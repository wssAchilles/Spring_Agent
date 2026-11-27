#!/usr/bin/env python3
"""
================================================================================
FLORA-Bench 大规模跨领域与 DeepSeek 专属子集严密分组交叉验证实验
(Large-Scale Multi-Domain & DeepSeek GroupKFold Surrogate Benchmark on Colab GPU)
================================================================================
实验设计说明：
1. 样本规模扩大 20~50 倍：
   - Benchmark 1 [Math-AF Full]: 合并 Math-AF/train.jsonl + val.jsonl + test.jsonl (全量约 4,000+ 条)
   - Benchmark 2 [Coding-AF Full]: 合并 Coding-AF/train.jsonl + val.jsonl + test.jsonl (全量约 6,000+ 条)
   - Benchmark 3 [ext_deepseek]: 专门針對 DeepSeek 模型执行轨迹的验证集 + 训练切片 (10,000 条)
2. 零数据泄露分组切分 (Zero-Leakage GroupKFold, 5 Folds):
   - Regime A (Unseen Workflow): 按 workflow_id 5 折 GroupKFold，训练/测试工作流交集严格为 0
   - Regime B (Unseen Task): 按 task_id 5 折 GroupKFold，训练/测试任务交集严格为 0
   - Regime C (Both Unseen - Strict Inductive): 双盲切分，训练/测试在 workflow_id 与 task_id 上同时零交集
3. 硬件加速与公平评测：
   - 自动检测并利用 Colab NVIDIA GPU (CUDA) 进行 PyTorch Batched GCN / Batched GAT 矩阵张量加速训练；
   - 统一公平测量单样本推理耗时 (剥离 fit()，执行 5 次预热 + 25 次实测取中位数与 P95)；
   - 输出 95% Student-t 置信区间与双侧配对 t 检验 (scipy.stats.ttest_rel)。
"""

import os
import sys
import json
import time
import urllib.request
import numpy as np
from scipy import stats
from sklearn.model_selection import GroupKFold
from sklearn.metrics import roc_auc_score, accuracy_score, f1_score
from sklearn.ensemble import HistGradientBoostingClassifier, RandomForestClassifier
from sklearn.neural_network import MLPClassifier
from sklearn.dummy import DummyClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import StandardScaler

import torch
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim
from torch.utils.data import TensorDataset, DataLoader

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
DATA_CACHE_DIR = os.path.expanduser("~/.cache/flora_bench_large")

HF_BASES = [
    "https://huggingface.co/datasets/YuanshuoZhang/FLORA-Bench/resolve/main",
    "https://hf-mirror.com/datasets/YuanshuoZhang/FLORA-Bench/resolve/main"
]


def download_subset(rel_path, max_lines=None):
    os.makedirs(DATA_CACHE_DIR, exist_ok=True)
    safe_name = rel_path.replace("/", "_")
    if max_lines:
        safe_name = f"{safe_name}_limit{max_lines}.jsonl"
    local_path = os.path.join(DATA_CACHE_DIR, safe_name)

    if not (os.path.exists(local_path) and os.path.getsize(local_path) > 10000):
        print(f"[下载数据] 正在获取 {rel_path} (max_lines={max_lines})...")
        success = False
        for base in HF_BASES:
            url = f"{base}/{rel_path}"
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
                count = 0
                with urllib.request.urlopen(req, timeout=60) as resp, open(local_path, "w", encoding="utf-8") as out_f:
                    for raw_line in resp:
                        line_str = raw_line.decode("utf-8").strip()
                        if line_str:
                            out_f.write(line_str + "\n")
                            count += 1
                            if max_lines and count >= max_lines:
                                break
                if count > 0:
                    print(f"  -> 成功从 {base} 获取 {count} 条记录")
                    success = True
                    break
            except Exception as e:
                print(f"  -> 源 {base} 失败: {e}")
        if not success:
            raise RuntimeError(f"无法下载数据集: {rel_path}")

    records = []
    with open(local_path, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                try:
                    records.append(json.loads(line))
                except Exception:
                    pass
    return records


def load_domain_dataset(domain_name):
    if domain_name == "Math-AF-Full":
        r_tr = download_subset("Math-AF/train.jsonl")
        r_va = download_subset("Math-AF/val.jsonl")
        r_te = download_subset("Math-AF/test.jsonl")
        return r_tr + r_va + r_te
    elif domain_name == "Coding-AF-Full":
        r_tr = download_subset("Coding-AF/train.jsonl")
        r_va = download_subset("Coding-AF/val.jsonl")
        r_te = download_subset("Coding-AF/test.jsonl")
        return r_tr + r_va + r_te
    elif domain_name == "DeepSeek-Ext-10K":
        r_va = download_subset("ext_deepseek/val.jsonl")
        r_te = download_subset("ext_deepseek/test.jsonl")
        return r_va + r_te
    else:
        raise ValueError(f"未知数据集名称: {domain_name}")


# -----------------------------------------------------------------------------
# 2. 拓扑特征解析与张量批处理化 (Batched Graph Padding for Fast GPU Execution)
# -----------------------------------------------------------------------------
def parse_topology_stats(record):
    nodes = record.get("nodes", {})
    num_nodes = max(1, len(nodes))
    raw_edges = record.get("edge_index", [])

    valid_edges = []
    if isinstance(raw_edges, list):
        for e in raw_edges:
            if isinstance(e, (list, tuple)) and len(e) == 2:
                u, v = int(e[0]), int(e[1])
                if 0 <= u < num_nodes and 0 <= v < num_nodes:
                    valid_edges.append((u, v))

    num_edges = len(valid_edges)
    density = num_edges / (num_nodes * (num_nodes - 1)) if num_nodes > 1 else 0.0

    out_deg = np.zeros(num_nodes, dtype=np.float32)
    in_deg = np.zeros(num_nodes, dtype=np.float32)
    for u, v in valid_edges:
        out_deg[u] += 1.0
        in_deg[v] += 1.0

    max_out_deg = float(np.max(out_deg))
    max_in_deg = float(np.max(in_deg))
    avg_deg = float(num_edges / num_nodes)

    depth = np.zeros(num_nodes, dtype=np.int32)
    for _ in range(min(num_nodes, 16)):
        changed = False
        for u, v in valid_edges:
            if u != v and depth[v] < depth[u] + 1:
                depth[v] = depth[u] + 1
                changed = True
        if not changed:
            break
    dag_depth = float(np.max(depth))

    node_texts = [str(nodes.get(str(i), nodes.get(i, ""))) for i in range(num_nodes)]
    combined_node_text = " ".join(node_texts)
    avg_prompt_len = float(len(combined_node_text)) / num_nodes

    topo_vec = np.array([
        float(num_nodes),
        float(num_edges),
        density,
        max_out_deg,
        max_in_deg,
        avg_deg,
        dag_depth,
        float(num_edges == num_nodes - 1),
        float(num_edges >= num_nodes),
        avg_prompt_len
    ], dtype=np.float32)

    return topo_vec, valid_edges, node_texts, str(record.get("task", ""))


def build_split_tensors(records, parsed_cache, train_idx, test_idx, node_tfidf_dim=32, task_tfidf_dim=32, max_nodes=16):
    """
    严格仅在 train_idx 上 fit TF-IDF 与 StandardScaler。
    同时将可变长小图 padding 为固定阶张量 (B, max_nodes, F) 与 (B, max_nodes, max_nodes)，
    支持在 GPU 上通过批量矩阵乘法 (torch.bmm) 极速训练与推理，消除 Python for 循环开销。
    """
    labels = np.array([int(r.get("label", 0)) for r in records], dtype=np.int64)

    train_node_corpus = []
    for idx in train_idx:
        _, _, node_texts, _ = parsed_cache[idx]
        train_node_corpus.extend(node_texts if node_texts else [""])

    train_task_corpus = [parsed_cache[idx][3] for idx in train_idx]

    node_vec_model = TfidfVectorizer(max_features=node_tfidf_dim, stop_words="english")
    node_vec_model.fit(train_node_corpus if train_node_corpus else ["workflow"])

    task_vec_model = TfidfVectorizer(max_features=task_tfidf_dim, stop_words="english")
    task_vec_model.fit(train_task_corpus if train_task_corpus else ["task"])

    # 批量转换所有 task 文本
    all_tasks = [p[3] for p in parsed_cache]
    all_task_raw = task_vec_model.transform(all_tasks).toarray().astype(np.float32)
    all_task_feat = np.zeros((len(records), task_tfidf_dim), dtype=np.float32)
    all_task_feat[:, :all_task_raw.shape[1]] = all_task_raw[:, :task_tfidf_dim]

    # 批量转换所有 node 文本
    flat_node_texts = []
    node_offsets = [0]
    for p in parsed_cache:
        nt = p[2] if len(p[2]) > 0 else [""]
        flat_node_texts.extend(nt)
        node_offsets.append(len(flat_node_texts))

    flat_node_raw = node_vec_model.transform(flat_node_texts).toarray().astype(np.float32)
    flat_node_feat = np.zeros((len(flat_node_texts), node_tfidf_dim), dtype=np.float32)
    flat_node_feat[:, :flat_node_raw.shape[1]] = flat_node_raw[:, :node_tfidf_dim]

    node_feat_dim = node_tfidf_dim + 3
    N_total = len(records)

    tabular_all = np.zeros((N_total, 10 + node_tfidf_dim * 2 + task_tfidf_dim), dtype=np.float32)
    padded_node_x = np.zeros((N_total, max_nodes, node_feat_dim), dtype=np.float32)
    padded_adj_norm = np.zeros((N_total, max_nodes, max_nodes), dtype=np.float32)
    padded_adj_mask = np.zeros((N_total, max_nodes, max_nodes), dtype=bool)
    padded_node_mask = np.zeros((N_total, max_nodes), dtype=np.float32)
    topo_all = np.zeros((N_total, 10), dtype=np.float32)

    for i, (topo_vec, valid_edges, _, _) in enumerate(parsed_cache):
        st, ed = node_offsets[i], node_offsets[i + 1]
        n_tfidf = flat_node_feat[st:ed]
        n_mean = np.mean(n_tfidf, axis=0)
        n_max = np.max(n_tfidf, axis=0)
        t_feat = all_task_feat[i]

        tabular_all[i] = np.concatenate([topo_vec, n_mean, n_max, t_feat])
        topo_all[i] = topo_vec

        cur_n = min(n_tfidf.shape[0], max_nodes)
        padded_node_mask[i, :cur_n] = 1.0

        in_d = np.zeros((cur_n, 1), dtype=np.float32)
        out_d = np.zeros((cur_n, 1), dtype=np.float32)
        adj = np.eye(cur_n, dtype=np.float32)
        for u, v in valid_edges:
            if u < cur_n and v < cur_n:
                out_d[u, 0] += 1.0
                in_d[v, 0] += 1.0
                adj[u, v] = 1.0
                adj[v, u] = 1.0

        pos_d = (np.arange(cur_n, dtype=np.float32) / max(1, cur_n - 1)).reshape(-1, 1)
        padded_node_x[i, :cur_n, :] = np.hstack([n_tfidf[:cur_n], in_d, out_d, pos_d])

        deg = np.sum(adj, axis=1, keepdims=True)
        deg_inv_sqrt = np.power(np.maximum(deg, 1.0), -0.5)
        adj_norm = deg_inv_sqrt * adj * deg_inv_sqrt.T
        padded_adj_norm[i, :cur_n, :cur_n] = adj_norm
        padded_adj_mask[i, :cur_n, :cur_n] = (adj > 0)
        # 防止被 padding 的孤立行在 GAT softmax 中出现全 -inf NaN
        for r_idx in range(cur_n, max_nodes):
            padded_adj_mask[i, r_idx, r_idx] = True

    scaler = StandardScaler()
    X_train = scaler.fit_transform(tabular_all[train_idx]).astype(np.float32)
    X_test = scaler.transform(tabular_all[test_idx]).astype(np.float32)

    topo_scaler = StandardScaler()
    topo_train = topo_scaler.fit_transform(topo_all[train_idx]).astype(np.float32)
    topo_test = topo_scaler.transform(topo_all[test_idx]).astype(np.float32)

    def pack_graph_tensors(idx_arr, topo_scaled):
        return {
            "node_x": torch.tensor(padded_node_x[idx_arr], dtype=torch.float32, device=DEVICE),
            "adj_norm": torch.tensor(padded_adj_norm[idx_arr], dtype=torch.float32, device=DEVICE),
            "adj_mask": torch.tensor(padded_adj_mask[idx_arr], dtype=torch.bool, device=DEVICE),
            "node_mask": torch.tensor(padded_node_mask[idx_arr], dtype=torch.float32, device=DEVICE),
            "task_x": torch.tensor(all_task_feat[idx_arr], dtype=torch.float32, device=DEVICE),
            "topo_x": torch.tensor(topo_scaled, dtype=torch.float32, device=DEVICE),
            "y": torch.tensor(labels[idx_arr], dtype=torch.float32, device=DEVICE)
        }

    g_train = pack_graph_tensors(train_idx, topo_train)
    g_test = pack_graph_tensors(test_idx, topo_test)

    return X_train, X_test, labels[train_idx], labels[test_idx], g_train, g_test


# -----------------------------------------------------------------------------
# 3. 批量张量化 PyTorch GCN & GAT (支持 GPU 批量矩阵运算 torch.bmm)
# -----------------------------------------------------------------------------
class BatchedWorkflowGCN(nn.Module):
    def __init__(self, node_in_dim, task_in_dim, topo_in_dim, hidden_dim=48):
        super().__init__()
        self.gcn1 = nn.Linear(node_in_dim, hidden_dim)
        self.gcn2 = nn.Linear(hidden_dim, hidden_dim)
        fusion_dim = hidden_dim * 2 + task_in_dim + topo_in_dim
        self.classifier = nn.Sequential(
            nn.Linear(fusion_dim, hidden_dim),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(hidden_dim, 1)
        )

    def forward(self, node_x, adj_norm, node_mask, task_x, topo_x):
        # node_x: (B, N, F), adj_norm: (B, N, N), node_mask: (B, N)
        h1 = F.relu(self.gcn1(torch.bmm(adj_norm, node_x)))
        h2 = F.relu(self.gcn2(torch.bmm(adj_norm, h1))) + h1
        mask_exp = node_mask.unsqueeze(-1)  # (B, N, 1)
        h2_masked = h2 * mask_exp
        node_counts = torch.clamp(node_mask.sum(dim=1, keepdim=True), min=1.0)
        h_mean = h2_masked.sum(dim=1) / node_counts
        h_for_max = h2.masked_fill(mask_exp == 0, -1e9)
        h_max = torch.max(h_for_max, dim=1).values
        combined = torch.cat([h_mean, h_max, task_x, topo_x], dim=1)
        return self.classifier(combined).squeeze(-1)


class BatchedWorkflowGAT(nn.Module):
    def __init__(self, node_in_dim, task_in_dim, topo_in_dim, hidden_dim=48):
        super().__init__()
        self.W1 = nn.Linear(node_in_dim, hidden_dim, bias=False)
        self.att_src1 = nn.Parameter(torch.randn(1, hidden_dim, 1) * 0.1)
        self.att_dst1 = nn.Parameter(torch.randn(1, hidden_dim, 1) * 0.1)

        self.W2 = nn.Linear(hidden_dim, hidden_dim, bias=False)
        self.att_src2 = nn.Parameter(torch.randn(1, hidden_dim, 1) * 0.1)
        self.att_dst2 = nn.Parameter(torch.randn(1, hidden_dim, 1) * 0.1)

        fusion_dim = hidden_dim * 2 + task_in_dim + topo_in_dim
        self.classifier = nn.Sequential(
            nn.Linear(fusion_dim, hidden_dim),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(hidden_dim, 1)
        )

    def _gat_layer(self, x, adj_mask, W, att_src, att_dst):
        Wh = W(x)  # (B, N, H)
        e_src = torch.matmul(Wh, att_src)  # (B, N, 1)
        e_dst = torch.matmul(Wh, att_dst).transpose(1, 2)  # (B, 1, N)
        scores = F.leaky_relu(e_src + e_dst, negative_slope=0.2)  # (B, N, N)
        scores = scores.masked_fill(~adj_mask, -1e9)
        alpha = F.softmax(scores, dim=2)
        return F.elu(torch.bmm(alpha, Wh))

    def forward(self, node_x, adj_mask, node_mask, task_x, topo_x):
        h1 = self._gat_layer(node_x, adj_mask, self.W1, self.att_src1, self.att_dst1)
        h2 = self._gat_layer(h1, adj_mask, self.W2, self.att_src2, self.att_dst2) + h1
        mask_exp = node_mask.unsqueeze(-1)
        h2_masked = h2 * mask_exp
        node_counts = torch.clamp(node_mask.sum(dim=1, keepdim=True), min=1.0)
        h_mean = h2_masked.sum(dim=1) / node_counts
        h_for_max = h2.masked_fill(mask_exp == 0, -1e9)
        h_max = torch.max(h_for_max, dim=1).values
        combined = torch.cat([h_mean, h_max, task_x, topo_x], dim=1)
        return self.classifier(combined).squeeze(-1)


def measure_inference_latency(infer_callable, num_samples, warmup=5, repeats=25):
    for _ in range(warmup):
        infer_callable()
    if DEVICE.type == "cuda":
        torch.cuda.synchronize()

    per_sample_times_ms = []
    for _ in range(repeats):
        t0 = time.perf_counter()
        infer_callable()
        if DEVICE.type == "cuda":
            torch.cuda.synchronize()
        elapsed_ms = (time.perf_counter() - t0) * 1000.0
        per_sample_times_ms.append(elapsed_ms / max(1, num_samples))

    return float(np.median(per_sample_times_ms)), float(np.percentile(per_sample_times_ms, 95))


def train_and_eval_batched_gnn(model, is_gat, g_train, g_test, epochs=30, batch_size=256, lr=0.004, seed=42):
    torch.manual_seed(seed)
    model = model.to(DEVICE)
    optimizer = optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-3)
    criterion = nn.BCEWithLogitsLoss()

    n_train = g_train["y"].shape[0]
    if DEVICE.type == "cuda":
        torch.cuda.synchronize()
    t0 = time.perf_counter()

    model.train()
    for _ in range(epochs):
        perm = torch.randperm(n_train, device=DEVICE)
        for i in range(0, n_train, batch_size):
            b_idx = perm[i:i + batch_size]
            optimizer.zero_grad()
            if is_gat:
                logits = model(
                    g_train["node_x"][b_idx],
                    g_train["adj_mask"][b_idx],
                    g_train["node_mask"][b_idx],
                    g_train["task_x"][b_idx],
                    g_train["topo_x"][b_idx]
                )
            else:
                logits = model(
                    g_train["node_x"][b_idx],
                    g_train["adj_norm"][b_idx],
                    g_train["node_mask"][b_idx],
                    g_train["task_x"][b_idx],
                    g_train["topo_x"][b_idx]
                )
            loss = criterion(logits, g_train["y"][b_idx])
            loss.backward()
            optimizer.step()

    if DEVICE.type == "cuda":
        torch.cuda.synchronize()
    train_ms = (time.perf_counter() - t0) * 1000.0

    model.eval()
    def infer_fn():
        with torch.no_grad():
            if is_gat:
                out = model(
                    g_test["node_x"], g_test["adj_mask"], g_test["node_mask"],
                    g_test["task_x"], g_test["topo_x"]
                )
            else:
                out = model(
                    g_test["node_x"], g_test["adj_norm"], g_test["node_mask"],
                    g_test["task_x"], g_test["topo_x"]
                )
            return torch.sigmoid(out)

    with torch.no_grad():
        proba_test = infer_fn().detach().cpu().numpy()

    med_ms, p95_ms = measure_inference_latency(infer_fn, g_test["y"].shape[0])
    return proba_test, train_ms, med_ms, p95_ms


def calc_top_k_precision(scores, labels, ratio=0.2, rng_seed=42):
    k = max(1, int(len(labels) * ratio))
    rng = np.random.RandomState(rng_seed)
    jittered_scores = scores + rng.uniform(-1e-9, 1e-9, size=len(scores))
    idx = np.argsort(jittered_scores)[::-1][:k]
    return float(np.mean(labels[idx]) * 100.0)


def evaluate_predictions(y_true, proba, seed):
    preds = (proba >= 0.5).astype(np.int64)
    try:
        auc = float(roc_auc_score(y_true, proba))
    except ValueError:
        auc = 0.5
    acc = float(accuracy_score(y_true, preds))
    f1 = float(f1_score(y_true, preds, zero_division=0))
    top20 = calc_top_k_precision(proba, y_true, ratio=0.2, rng_seed=seed)
    return auc, acc, f1, top20


def compute_paired_stats(model_vals, baseline_vals):
    m_arr = np.array(model_vals, dtype=np.float64)
    b_arr = np.array(baseline_vals, dtype=np.float64)
    diffs = m_arr - b_arr
    n = len(diffs)
    mean_val = float(np.mean(m_arr))
    std_val = float(np.std(m_arr, ddof=1)) if n > 1 else 0.0

    if n > 1 and std_val > 1e-12:
        se = std_val / np.sqrt(n)
        t_crit = float(stats.t.ppf(0.975, df=n - 1))
        ci_low, ci_high = mean_val - t_crit * se, mean_val + t_crit * se
    else:
        ci_low, ci_high = mean_val, mean_val

    diff_mean = float(np.mean(diffs))
    diff_std = float(np.std(diffs, ddof=1)) if n > 1 else 0.0
    if n > 1 and diff_std > 1e-12:
        diff_se = diff_std / np.sqrt(n)
        t_crit = float(stats.t.ppf(0.975, df=n - 1))
        diff_ci_low = diff_mean - t_crit * diff_se
        diff_ci_high = diff_mean + t_crit * diff_se
        _, p_val = stats.ttest_rel(m_arr, b_arr)
        p_val = float(p_val)
    else:
        diff_ci_low, diff_ci_high = diff_mean, diff_mean
        p_val = 1.0

    return {
        "mean": mean_val,
        "std": std_val,
        "ci95": [float(ci_low), float(ci_high)],
        "paired_diff_mean": diff_mean,
        "paired_diff_std": diff_std,
        "paired_diff_ci95": [float(diff_ci_low), float(diff_ci_high)],
        "paired_ttest_p": p_val
    }


def run_cv_regime(regime_name, splits, records, parsed_cache):
    from tabulate import tabulate

    model_names = [
        "0. Prior Dummy",
        "1. GBDT (HistGradientBoosting)",
        "2. Random Forest",
        "3. MLP (Neural Net)",
        "4. Real GCN (PyTorch GPU)",
        "5. Real GAT (PyTorch GPU)"
    ]

    fold_metadata = []
    results_by_model = {m: [] for m in model_names}

    for fold_idx, (train_idx, test_idx) in enumerate(splits):
        seed = 42 + fold_idx * 100
        train_wfs = set(records[i]["workflow_id"] for i in train_idx)
        test_wfs = set(records[i]["workflow_id"] for i in test_idx)
        train_tasks = set(records[i]["task_id"] for i in train_idx)
        test_tasks = set(records[i]["task_id"] for i in test_idx)

        wf_overlap = len(train_wfs.intersection(test_wfs))
        task_overlap = len(train_tasks.intersection(test_tasks))

        X_train, X_test, y_train, y_test, g_train, g_test = build_split_tensors(
            records, parsed_cache, train_idx, test_idx
        )
        n_test = len(y_test)

        fold_metadata.append({
            "fold": fold_idx + 1,
            "n_train": int(len(y_train)),
            "n_test": int(len(y_test)),
            "train_pos_rate": float(np.mean(y_train)),
            "test_pos_rate": float(np.mean(y_test)),
            "train_unique_workflows": len(train_wfs),
            "test_unique_workflows": len(test_wfs),
            "workflow_overlap_count": wf_overlap,
            "train_unique_tasks": len(train_tasks),
            "test_unique_tasks": len(test_tasks),
            "task_overlap_count": task_overlap
        })

        # 0. Dummy
        t0 = time.perf_counter()
        dummy = DummyClassifier(strategy="prior", random_state=seed)
        dummy.fit(X_train, y_train)
        train_ms = (time.perf_counter() - t0) * 1000.0
        proba = dummy.predict_proba(X_test)[:, 1]
        med_lat, p95_lat = measure_inference_latency(lambda: dummy.predict_proba(X_test), n_test)
        auc, acc, f1, top20 = evaluate_predictions(y_test, proba, seed)
        results_by_model[model_names[0]].append({
            "fold": fold_idx + 1, "auc": auc, "acc": acc, "f1": f1, "top20": top20,
            "train_ms": train_ms, "infer_median_ms": med_lat, "infer_p95_ms": p95_lat
        })

        # 1. GBDT
        t0 = time.perf_counter()
        gbdt = HistGradientBoostingClassifier(max_iter=100, max_depth=6, random_state=seed)
        gbdt.fit(X_train, y_train)
        train_ms = (time.perf_counter() - t0) * 1000.0
        proba = gbdt.predict_proba(X_test)[:, 1]
        med_lat, p95_lat = measure_inference_latency(lambda: gbdt.predict_proba(X_test), n_test)
        auc, acc, f1, top20 = evaluate_predictions(y_test, proba, seed)
        results_by_model[model_names[1]].append({
            "fold": fold_idx + 1, "auc": auc, "acc": acc, "f1": f1, "top20": top20,
            "train_ms": train_ms, "infer_median_ms": med_lat, "infer_p95_ms": p95_lat
        })

        # 2. Random Forest
        t0 = time.perf_counter()
        rf = RandomForestClassifier(n_estimators=100, max_depth=8, n_jobs=-1, random_state=seed)
        rf.fit(X_train, y_train)
        train_ms = (time.perf_counter() - t0) * 1000.0
        proba = rf.predict_proba(X_test)[:, 1]
        med_lat, p95_lat = measure_inference_latency(lambda: rf.predict_proba(X_test), n_test)
        auc, acc, f1, top20 = evaluate_predictions(y_test, proba, seed)
        results_by_model[model_names[2]].append({
            "fold": fold_idx + 1, "auc": auc, "acc": acc, "f1": f1, "top20": top20,
            "train_ms": train_ms, "infer_median_ms": med_lat, "infer_p95_ms": p95_lat
        })

        # 3. MLP
        t0 = time.perf_counter()
        mlp = MLPClassifier(hidden_layer_sizes=(64, 32), max_iter=150, random_state=seed, early_stopping=True)
        mlp.fit(X_train, y_train)
        train_ms = (time.perf_counter() - t0) * 1000.0
        proba = mlp.predict_proba(X_test)[:, 1]
        med_lat, p95_lat = measure_inference_latency(lambda: mlp.predict_proba(X_test), n_test)
        auc, acc, f1, top20 = evaluate_predictions(y_test, proba, seed)
        results_by_model[model_names[3]].append({
            "fold": fold_idx + 1, "auc": auc, "acc": acc, "f1": f1, "top20": top20,
            "train_ms": train_ms, "infer_median_ms": med_lat, "infer_p95_ms": p95_lat
        })

        # 4. Real PyTorch GCN
        node_dim = g_train["node_x"].shape[2]
        task_dim = g_train["task_x"].shape[1]
        topo_dim = g_train["topo_x"].shape[1]
        gcn = BatchedWorkflowGCN(node_dim, task_dim, topo_dim, hidden_dim=48)
        proba, train_ms, med_lat, p95_lat = train_and_eval_batched_gnn(
            gcn, False, g_train, g_test, epochs=30, batch_size=256, lr=0.004, seed=seed
        )
        auc, acc, f1, top20 = evaluate_predictions(y_test, proba, seed)
        results_by_model[model_names[4]].append({
            "fold": fold_idx + 1, "auc": auc, "acc": acc, "f1": f1, "top20": top20,
            "train_ms": train_ms, "infer_median_ms": med_lat, "infer_p95_ms": p95_lat
        })

        # 5. Real PyTorch GAT
        gat = BatchedWorkflowGAT(node_dim, task_dim, topo_dim, hidden_dim=48)
        proba, train_ms, med_lat, p95_lat = train_and_eval_batched_gnn(
            gat, True, g_train, g_test, epochs=30, batch_size=256, lr=0.004, seed=seed
        )
        auc, acc, f1, top20 = evaluate_predictions(y_test, proba, seed)
        results_by_model[model_names[5]].append({
            "fold": fold_idx + 1, "auc": auc, "acc": acc, "f1": f1, "top20": top20,
            "train_ms": train_ms, "infer_median_ms": med_lat, "infer_p95_ms": p95_lat
        })

    dummy_aucs = [r["auc"] for r in results_by_model[model_names[0]]]
    fold_prior_rates = [fm["test_pos_rate"] * 100.0 for fm in fold_metadata]

    regime_summary = {
        "folds": fold_metadata,
        "models": {}
    }

    perf_rows = []
    for m in model_names:
        runs = results_by_model[m]
        aucs = [r["auc"] for r in runs]
        accs = [r["acc"] for r in runs]
        f1s = [r["f1"] for r in runs]
        top20s = [r["top20"] for r in runs]
        trains = [r["train_ms"] for r in runs]
        inf_meds = [r["infer_median_ms"] for r in runs]

        auc_stats = compute_paired_stats(aucs, dummy_aucs)
        top20_vs_prior = compute_paired_stats(top20s, fold_prior_rates)

        regime_summary["models"][m] = {
            "runs": runs,
            "auc": auc_stats,
            "acc_mean": float(np.mean(accs)),
            "acc_std": float(np.std(accs, ddof=1)),
            "f1_mean": float(np.mean(f1s)),
            "f1_std": float(np.std(f1s, ddof=1)),
            "top20_vs_prior": top20_vs_prior,
            "train_ms_mean": float(np.mean(trains)),
            "infer_ms_median": float(np.median(inf_meds))
        }

        perf_rows.append([
            m,
            f"{auc_stats['mean']:.4f} ± {auc_stats['std']:.4f}",
            f"[{auc_stats['ci95'][0]:.4f}, {auc_stats['ci95'][1]:.4f}]",
            f"{auc_stats['paired_diff_mean']:+.4f} (p={auc_stats['paired_ttest_p']:.4f})",
            f"{np.mean(accs):.4f} ± {np.std(accs, ddof=1):.4f}",
            f"{top20_vs_prior['mean']:.1f}% ({top20_vs_prior['paired_diff_mean']:+.1f}%, p={top20_vs_prior['paired_ttest_p']:.4f})",
            f"{np.mean(trains):.1f} ms",
            f"{np.median(inf_meds):.4f} ms"
        ])

    avg_train_n = int(np.mean([f["n_train"] for f in fold_metadata]))
    avg_test_n = int(np.mean([f["n_test"] for f in fold_metadata]))
    max_wf_ov = max(f["workflow_overlap_count"] for f in fold_metadata)
    max_tk_ov = max(f["task_overlap_count"] for f in fold_metadata)

    print(f"\n  [{regime_name}] (Avg Train={avg_train_n}, Avg Test={avg_test_n}, WF_Overlap={max_wf_ov}, Task_Overlap={max_tk_ov})")
    print(tabulate(
        perf_rows,
        headers=[
            "模型",
            "ROC-AUC (Mean±SD)",
            "AUC 95% CI",
            "ΔAUC vs Dummy (p-val)",
            "Accuracy (Mean±SD)",
            "Top-20% 胜率 (Δ vs Prior, p)",
            "训练耗时",
            "单样本推理"
        ],
        tablefmt="fancy_grid"
    ))

    gcn_aucs = [r["auc"] for r in results_by_model[model_names[4]]]
    gat_aucs = [r["auc"] for r in results_by_model[model_names[5]]]
    gbdt_aucs = [r["auc"] for r in results_by_model[model_names[1]]]
    rf_aucs = [r["auc"] for r in results_by_model[model_names[2]]]

    gcn_vs_gbdt = compute_paired_stats(gcn_aucs, gbdt_aucs)
    gcn_vs_rf = compute_paired_stats(gcn_aucs, rf_aucs)
    gat_vs_gbdt = compute_paired_stats(gat_aucs, gbdt_aucs)

    regime_summary["head_to_head"] = {
        "gcn_vs_gbdt_auc": gcn_vs_gbdt,
        "gcn_vs_rf_auc": gcn_vs_rf,
        "gat_vs_gbdt_auc": gat_vs_gbdt
    }
    print(
        f"  👉 [Head-to-Head] "
        f"GCN vs GBDT: ΔAUC={gcn_vs_gbdt['paired_diff_mean']:+.4f} (CI [{gcn_vs_gbdt['paired_diff_ci95'][0]:+.4f}, {gcn_vs_gbdt['paired_diff_ci95'][1]:+.4f}], p={gcn_vs_gbdt['paired_ttest_p']:.4f}) | "
        f"GCN vs RF: ΔAUC={gcn_vs_rf['paired_diff_mean']:+.4f} (p={gcn_vs_rf['paired_ttest_p']:.4f}) | "
        f"GAT vs GBDT: ΔAUC={gat_vs_gbdt['paired_diff_mean']:+.4f} (p={gat_vs_gbdt['paired_ttest_p']:.4f})"
    )

    return regime_summary


def evaluate_domain(domain_name):
    records = load_domain_dataset(domain_name)
    parsed_cache = [parse_topology_stats(r) for r in records]
    all_indices = np.arange(len(records))
    all_labels = np.array([int(r.get("label", 0)) for r in records], dtype=np.int64)
    workflow_groups = np.array([r.get("workflow_id", "") for r in records])
    task_groups = np.array([r.get("task_id", "") for r in records])

    n_wf = len(np.unique(workflow_groups))
    n_tk = len(np.unique(task_groups))
    pos_rate = float(np.mean(all_labels))

    print("\n" + "#" * 115)
    print(f"### 领域数据集: {domain_name} | 总样本数: {len(records)} | 独立工作流: {n_wf} | 独立任务: {n_tk} | 正例率: {pos_rate*100:.1f}% | 设备: {DEVICE}")
    print("#" * 115)

    gkf_wf = GroupKFold(n_splits=5)
    wf_splits = list(gkf_wf.split(all_indices, all_labels, groups=workflow_groups))
    res_wf = run_cv_regime(
        f"{domain_name} - Regime A: Unseen Workflow (按 workflow_id 5-Fold)",
        wf_splits, records, parsed_cache
    )

    gkf_task = GroupKFold(n_splits=5)
    task_splits = list(gkf_task.split(all_indices, all_labels, groups=task_groups))
    res_task = run_cv_regime(
        f"{domain_name} - Regime B: Unseen Task (按 task_id 5-Fold)",
        task_splits, records, parsed_cache
    )

    wf_fold_id = np.zeros(len(records), dtype=np.int32)
    for f_i, (_, te_i) in enumerate(wf_splits):
        wf_fold_id[te_i] = f_i
    task_fold_id = np.zeros(len(records), dtype=np.int32)
    for f_i, (_, te_i) in enumerate(task_splits):
        task_fold_id[te_i] = f_i

    strict_both_splits = []
    for f_i in range(5):
        tr_mask = (wf_fold_id != f_i) & (task_fold_id != f_i)
        te_mask = (wf_fold_id == f_i) & (task_fold_id == f_i)
        tr_idx = np.where(tr_mask)[0]
        te_idx = np.where(te_mask)[0]
        if len(te_idx) >= 10 and len(np.unique(all_labels[te_idx])) == 2:
            strict_both_splits.append((tr_idx, te_idx))

    res_both = None
    if len(strict_both_splits) >= 3:
        res_both = run_cv_regime(
            f"{domain_name} - Regime C: Both Unseen (双盲切分: WF与Task同时零交集, {len(strict_both_splits)} Folds)",
            strict_both_splits, records, parsed_cache
        )

    return {
        "domain": domain_name,
        "total_records": len(records),
        "unique_workflows": n_wf,
        "unique_tasks": n_tk,
        "overall_positive_rate": pos_rate,
        "device": str(DEVICE),
        "regimes": {
            "unseen_workflow": res_wf,
            "unseen_task": res_task,
            "both_unseen": res_both
        }
    }


def main():
    if torch.cuda.is_available():
        print(f"[GPU 加速已启用] 当前 CUDA 设备: {torch.cuda.get_device_name(0)}")
    else:
        print(f"[运行设备] CPU ({os.cpu_count()} cores)")

    domains = ["Math-AF-Full", "Coding-AF-Full", "DeepSeek-Ext-10K"]
    all_domain_results = {}
    for d in domains:
        all_domain_results[d] = evaluate_domain(d)

    out_path = os.environ.get("SPIKE_OUTPUT_JSON", "flora_large_scale_group_cv_results.json")
    if os.path.dirname(out_path):
        os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(all_domain_results, f, indent=2, ensure_ascii=False)
    print(f"\n[全部实验完成] JSON 结果已保存至: {out_path}")
    print("___JSON_SUMMARY_START___")
    # 打印精简版汇总 JSON 以便从 stdout 直接提取
    compact = {}
    for d, d_val in all_domain_results.items():
        compact[d] = {
            "total_records": d_val["total_records"],
            "unique_workflows": d_val["unique_workflows"],
            "unique_tasks": d_val["unique_tasks"],
            "overall_positive_rate": d_val["overall_positive_rate"],
            "device": d_val["device"],
            "regimes": {}
        }
        for r_k, r_v in d_val["regimes"].items():
            if r_v is None:
                continue
            compact[d]["regimes"][r_k] = {
                "avg_n_train": int(np.mean([f["n_train"] for f in r_v["folds"]])),
                "avg_n_test": int(np.mean([f["n_test"] for f in r_v["folds"]])),
                "max_wf_overlap": max(f["workflow_overlap_count"] for f in r_v["folds"]),
                "max_task_overlap": max(f["task_overlap_count"] for f in r_v["folds"]),
                "head_to_head": r_v["head_to_head"],
                "models": {
                    m_k: {
                        "auc_mean": m_v["auc"]["mean"],
                        "auc_std": m_v["auc"]["std"],
                        "auc_ci95": m_v["auc"]["ci95"],
                        "auc_diff_vs_dummy": m_v["auc"]["paired_diff_mean"],
                        "auc_p_vs_dummy": m_v["auc"]["paired_ttest_p"],
                        "acc_mean": m_v["acc_mean"],
                        "acc_std": m_v["acc_std"],
                        "top20_mean": m_v["top20_vs_prior"]["mean"],
                        "top20_diff_vs_prior": m_v["top20_vs_prior"]["paired_diff_mean"],
                        "top20_p_vs_prior": m_v["top20_vs_prior"]["paired_ttest_p"],
                        "train_ms_mean": m_v["train_ms_mean"],
                        "infer_ms_median": m_v["infer_ms_median"]
                    }
                    for m_k, m_v in r_v["models"].items()
                }
            }
    print(json.dumps(compact, ensure_ascii=False))
    print("___JSON_SUMMARY_END___")


if __name__ == "__main__":
    main()
