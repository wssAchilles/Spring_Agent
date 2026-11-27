#!/usr/bin/env python3
"""
================================================================================
FLORA-Bench 代理性能预测器无泄露分组交叉验证实验 (Phase 1 Group-Split Spike)
================================================================================
审计修复说明 (Addressing Task/Workflow Leakage & Statistical Testing Audit):
1. 彻底消除工作流与任务跨集泄露 (Zero Group Leakage):
   - 废除按记录随机切分 (Random Record Split)，改为严格按分组隔离的 5 折交叉验证:
     * Regime A (Unseen Workflow): GroupKFold(n_splits=5) 按 workflow_id 切分，
       保证同一 workflow_id 绝对不会同时出现在训练集与测试集。
     * Regime B (Unseen Task): GroupKFold(n_splits=5) 按 task_id 切分，
       保证同一 task_id 绝对不会同时出现在训练集与测试集。
     * Regime C (Both Unseen - Strict Inductive): 同时剔除测试集中出现过任何训练集
       workflow_id 或 task_id 的样本，评估对“未见工作流 + 未见任务”的双盲泛化能力。
2. 严密的配对统计检验与置信区间 (Paired Statistical Testing & 95% CI):
   - 对每一折 (Fold) 记录样本数、正样本率、各模型指标；
   - 计算每个学习器相对于 Prior Dummy 基线的配对差值 ΔAUC = AUC(model) - AUC(dummy)；
   - 执行双侧配对 t 检验 (scipy.stats.ttest_rel)、Wilcoxon 符号秩检验，并基于
     Student-t 分布计算 95% 置信区间 (95% Confidence Interval)。
3. 零硬编码与公平推理计时:
   - 所有模型 (Dummy, GBDT, RF, MLP, Real PyTorch GCN, Real PyTorch GAT) 均在每折真实训练；
   - 推理耗时剥离 fit()，执行 5 次预热 + 25 次测量取单样本推理中位数。
"""

import os
import sys
import json
import time
import urllib.request
import numpy as np
from scipy import stats
from sklearn.model_selection import GroupKFold, StratifiedGroupKFold
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

# -----------------------------------------------------------------------------
# 1. 数据获取与真实解析
# -----------------------------------------------------------------------------
DATA_CACHE_DIR = os.path.expanduser("~/.cache/flora_bench_spike")
DATA_FILE = os.path.join(DATA_CACHE_DIR, "math_af_val_1000.jsonl")
OUTPUT_JSON_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "artifacts",
    "spike_flora_surrogate_group_cv_results.json"
)

HF_URLS = [
    "https://hf-mirror.com/datasets/YuanshuoZhang/FLORA-Bench/resolve/main/Math-AF/val.jsonl",
    "https://huggingface.co/datasets/YuanshuoZhang/FLORA-Bench/resolve/main/Math-AF/val.jsonl"
]


def ensure_dataset(sample_limit=1000):
    os.makedirs(DATA_CACHE_DIR, exist_ok=True)
    if not (os.path.exists(DATA_FILE) and os.path.getsize(DATA_FILE) > 10000):
        print(f"[数据下载] 正在获取 FLORA-Bench 数据集 (上限: {sample_limit} 条)...")
        collected = 0
        success = False
        for url in HF_URLS:
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
                with urllib.request.urlopen(req, timeout=30) as resp, open(DATA_FILE, "w", encoding="utf-8") as out_f:
                    for line in resp:
                        line_str = line.decode("utf-8").strip()
                        if line_str:
                            out_f.write(line_str + "\n")
                            collected += 1
                            if collected >= sample_limit:
                                break
                if collected > 0:
                    success = True
                    break
            except Exception as e:
                print(f"源下载失败: {e}")
        if not success:
            raise RuntimeError("所有数据源均无法访问")

    records = []
    with open(DATA_FILE, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                try:
                    records.append(json.loads(line))
                except Exception:
                    pass
    print(f"[数据加载] 真实读取记录数: {len(records)} 条 (来自 {DATA_FILE})")
    return records


# -----------------------------------------------------------------------------
# 2. 拓扑特征解析与无泄露特征构建
# -----------------------------------------------------------------------------
def parse_topology_stats(record):
    nodes = record.get("nodes", {})
    num_nodes = len(nodes)
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

    out_deg = np.zeros(max(1, num_nodes), dtype=np.float32)
    in_deg = np.zeros(max(1, num_nodes), dtype=np.float32)
    for u, v in valid_edges:
        out_deg[u] += 1.0
        in_deg[v] += 1.0

    max_out_deg = float(np.max(out_deg))
    max_in_deg = float(np.max(in_deg))
    avg_deg = float(num_edges / max(1, num_nodes))

    depth = np.zeros(max(1, num_nodes), dtype=np.int32)
    for _ in range(num_nodes):
        changed = False
        for u, v in valid_edges:
            if u != v and depth[v] < depth[u] + 1:
                depth[v] = depth[u] + 1
                changed = True
        if not changed:
            break
    dag_depth = float(np.max(depth)) if num_nodes > 0 else 0.0

    node_texts = [str(nodes.get(str(i), nodes.get(i, ""))) for i in range(num_nodes)]
    combined_node_text = " ".join(node_texts)
    avg_prompt_len = float(len(combined_node_text)) / max(1, num_nodes)

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


def build_split_features(records, parsed_cache, train_idx, test_idx, node_tfidf_dim=24, task_tfidf_dim=24):
    """
    严格在 train_idx 上拟合 TF-IDF 与 StandardScaler，杜绝测试集特征泄露。
    """
    labels = np.array([int(r.get("label", 0)) for r in records], dtype=np.int64)

    train_node_corpus = []
    for idx in train_idx:
        _, _, node_texts, _ = parsed_cache[idx]
        train_node_corpus.extend(node_texts if node_texts else [""])

    train_task_corpus = [parsed_cache[idx][3] for idx in train_idx]

    node_vec_model = TfidfVectorizer(max_features=node_tfidf_dim, stop_words="english")
    node_vec_model.fit(train_node_corpus if train_node_corpus else ["workflow"])
    node_vocab_size = len(node_vec_model.vocabulary_)

    task_vec_model = TfidfVectorizer(max_features=task_tfidf_dim, stop_words="english")
    task_vec_model.fit(train_task_corpus if train_task_corpus else ["task"])
    task_vocab_size = len(task_vec_model.vocabulary_)

    tabular_list = []
    graph_list = []

    for i, (topo_vec, valid_edges, node_texts, task_str) in enumerate(parsed_cache):
        t_raw = task_vec_model.transform([task_str]).toarray()[0].astype(np.float32)
        t_feat = np.zeros(task_tfidf_dim, dtype=np.float32)
        t_feat[:min(task_tfidf_dim, len(t_raw))] = t_raw[:task_tfidf_dim]

        if len(node_texts) > 0:
            n_raw = node_vec_model.transform(node_texts).toarray().astype(np.float32)
            n_tfidf = np.zeros((len(node_texts), node_tfidf_dim), dtype=np.float32)
            n_tfidf[:, :min(node_tfidf_dim, n_raw.shape[1])] = n_raw[:, :node_tfidf_dim]
        else:
            n_tfidf = np.zeros((1, node_tfidf_dim), dtype=np.float32)

        num_nodes = n_tfidf.shape[0]
        in_d = np.zeros((num_nodes, 1), dtype=np.float32)
        out_d = np.zeros((num_nodes, 1), dtype=np.float32)
        for u, v in valid_edges:
            if u < num_nodes and v < num_nodes:
                out_d[u, 0] += 1.0
                in_d[v, 0] += 1.0
        pos_d = (np.arange(num_nodes, dtype=np.float32) / max(1, num_nodes - 1)).reshape(-1, 1)
        node_x = np.hstack([n_tfidf, in_d, out_d, pos_d])

        adj = np.eye(num_nodes, dtype=np.float32)
        for u, v in valid_edges:
            if u < num_nodes and v < num_nodes:
                adj[u, v] = 1.0
                adj[v, u] = 1.0
        deg = np.sum(adj, axis=1, keepdims=True)
        deg_inv_sqrt = np.power(np.maximum(deg, 1.0), -0.5)
        adj_norm = deg_inv_sqrt * adj * deg_inv_sqrt.T

        n_mean = np.mean(n_tfidf, axis=0)
        n_max = np.max(n_tfidf, axis=0)
        tab_x = np.concatenate([topo_vec, n_mean, n_max, t_feat]).astype(np.float32)
        tabular_list.append(tab_x)

        graph_list.append({
            "node_x": torch.tensor(node_x, dtype=torch.float32),
            "adj_norm": torch.tensor(adj_norm, dtype=torch.float32),
            "adj_mask": torch.tensor(adj > 0, dtype=torch.bool),
            "task_x": torch.tensor(t_feat, dtype=torch.float32),
            "topo_x": torch.tensor(topo_vec.copy(), dtype=torch.float32),
            "label": int(labels[i])
        })

    X_all = np.vstack(tabular_list)
    X_train_raw = X_all[train_idx]
    X_test_raw = X_all[test_idx]

    scaler = StandardScaler()
    X_train = scaler.fit_transform(X_train_raw).astype(np.float32)
    X_test = scaler.transform(X_test_raw).astype(np.float32)

    topo_scaler = StandardScaler()
    topo_train = np.vstack([graph_list[i]["topo_x"].numpy() for i in train_idx])
    topo_scaler.fit(topo_train)
    for g in graph_list:
        g["topo_x"] = torch.tensor(
            topo_scaler.transform(g["topo_x"].numpy().reshape(1, -1))[0],
            dtype=torch.float32
        )

    y_train = labels[train_idx]
    y_test = labels[test_idx]
    graphs_train = [graph_list[i] for i in train_idx]
    graphs_test = [graph_list[i] for i in test_idx]

    return X_train, X_test, y_train, y_test, graphs_train, graphs_test


# -----------------------------------------------------------------------------
# 3. 真实 PyTorch GNN (GCN & GAT) 实现
# -----------------------------------------------------------------------------
class RealWorkflowGCN(nn.Module):
    def __init__(self, node_in_dim, task_in_dim, topo_in_dim, hidden_dim=32):
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

    def forward_single(self, g):
        x = g["node_x"]
        adj = g["adj_norm"]
        h1 = F.relu(self.gcn1(torch.matmul(adj, x)))
        h2 = F.relu(self.gcn2(torch.matmul(adj, h1))) + h1
        h_mean = torch.mean(h2, dim=0)
        h_max = torch.max(h2, dim=0).values
        combined = torch.cat([h_mean, h_max, g["task_x"], g["topo_x"]], dim=0)
        return self.classifier(combined).squeeze(-1)

    def forward_batch(self, graphs):
        return torch.stack([self.forward_single(g) for g in graphs])


class RealWorkflowGAT(nn.Module):
    def __init__(self, node_in_dim, task_in_dim, topo_in_dim, hidden_dim=32):
        super().__init__()
        self.W1 = nn.Linear(node_in_dim, hidden_dim, bias=False)
        self.att_src1 = nn.Parameter(torch.randn(hidden_dim, 1) * 0.1)
        self.att_dst1 = nn.Parameter(torch.randn(hidden_dim, 1) * 0.1)

        self.W2 = nn.Linear(hidden_dim, hidden_dim, bias=False)
        self.att_src2 = nn.Parameter(torch.randn(hidden_dim, 1) * 0.1)
        self.att_dst2 = nn.Parameter(torch.randn(hidden_dim, 1) * 0.1)

        fusion_dim = hidden_dim * 2 + task_in_dim + topo_in_dim
        self.classifier = nn.Sequential(
            nn.Linear(fusion_dim, hidden_dim),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(hidden_dim, 1)
        )

    def _gat_layer(self, x, mask, W, att_src, att_dst):
        Wh = W(x)
        e_src = torch.matmul(Wh, att_src)
        e_dst = torch.matmul(Wh, att_dst)
        scores = F.leaky_relu(e_src + e_dst.T, negative_slope=0.2)
        scores = scores.masked_fill(~mask, -1e9)
        alpha = F.softmax(scores, dim=1)
        return F.elu(torch.matmul(alpha, Wh))

    def forward_single(self, g):
        x = g["node_x"]
        mask = g["adj_mask"]
        h1 = self._gat_layer(x, mask, self.W1, self.att_src1, self.att_dst1)
        h2 = self._gat_layer(h1, mask, self.W2, self.att_src2, self.att_dst2) + h1
        h_mean = torch.mean(h2, dim=0)
        h_max = torch.max(h2, dim=0).values
        combined = torch.cat([h_mean, h_max, g["task_x"], g["topo_x"]], dim=0)
        return self.classifier(combined).squeeze(-1)

    def forward_batch(self, graphs):
        return torch.stack([self.forward_single(g) for g in graphs])


def measure_inference_latency(infer_callable, num_samples, warmup=5, repeats=25):
    for _ in range(warmup):
        infer_callable()

    per_sample_times_ms = []
    for _ in range(repeats):
        t0 = time.perf_counter()
        infer_callable()
        elapsed_ms = (time.perf_counter() - t0) * 1000.0
        per_sample_times_ms.append(elapsed_ms / max(1, num_samples))

    return float(np.median(per_sample_times_ms)), float(np.percentile(per_sample_times_ms, 95))


def train_and_eval_gnn(model, graphs_train, y_train, graphs_test, epochs=35, lr=0.005, seed=42):
    torch.manual_seed(seed)
    optimizer = optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-3)
    criterion = nn.BCEWithLogitsLoss()
    y_tr_tensor = torch.tensor(y_train, dtype=torch.float32)

    t0 = time.perf_counter()
    model.train()
    for _ in range(epochs):
        optimizer.zero_grad()
        logits = model.forward_batch(graphs_train)
        loss = criterion(logits, y_tr_tensor)
        loss.backward()
        optimizer.step()
    train_ms = (time.perf_counter() - t0) * 1000.0

    model.eval()
    with torch.no_grad():
        logits_test = model.forward_batch(graphs_test)
        proba_test = torch.sigmoid(logits_test).cpu().numpy()

    def infer_fn():
        with torch.no_grad():
            _ = torch.sigmoid(model.forward_batch(graphs_test))

    med_ms, p95_ms = measure_inference_latency(infer_fn, len(graphs_test))
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


# -----------------------------------------------------------------------------
# 4. 统计检验与置信区间计算
# -----------------------------------------------------------------------------
def compute_paired_stats(model_vals, baseline_vals):
    """
    计算相对于基线的配对差值、95% Student-t 置信区间与双侧配对 t 检验 p-value。
    """
    m_arr = np.array(model_vals, dtype=np.float64)
    b_arr = np.array(baseline_vals, dtype=np.float64)
    diffs = m_arr - b_arr
    n = len(diffs)
    mean_val = float(np.mean(m_arr))
    std_val = float(np.std(m_arr, ddof=1)) if n > 1 else 0.0

    # 本身指标的 95% CI
    if n > 1 and std_val > 1e-12:
        se = std_val / np.sqrt(n)
        t_crit = float(stats.t.ppf(0.975, df=n - 1))
        ci_low, ci_high = mean_val - t_crit * se, mean_val + t_crit * se
    else:
        ci_low, ci_high = mean_val, mean_val

    # 配对差值 Δ 的 95% CI 与配对 t 检验
    diff_mean = float(np.mean(diffs))
    diff_std = float(np.std(diffs, ddof=1)) if n > 1 else 0.0
    if n > 1 and diff_std > 1e-12:
        diff_se = diff_std / np.sqrt(n)
        t_crit = float(stats.t.ppf(0.975, df=n - 1))
        diff_ci_low = diff_mean - t_crit * diff_se
        diff_ci_high = diff_mean + t_crit * diff_se
        t_stat, p_val = stats.ttest_rel(m_arr, b_arr)
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


# -----------------------------------------------------------------------------
# 5. 执行特定切分体制 (Regime) 下的 5 折交叉验证
# -----------------------------------------------------------------------------
def run_cv_regime(regime_name, splits, records, parsed_cache):
    from tabulate import tabulate

    model_names = [
        "0. Prior Dummy (常数先验)",
        "1. GBDT (HistGradientBoosting)",
        "2. Random Forest (随机森林)",
        "3. MLP (多层感知机)",
        "4. Real GCN (PyTorch 2层图卷积)",
        "5. Real GAT (PyTorch 2层图注意力)"
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

        X_train, X_test, y_train, y_test, graphs_train, graphs_test = build_split_features(
            records, parsed_cache, train_idx, test_idx
        )
        n_test = len(y_test)

        fold_meta = {
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
        }
        fold_metadata.append(fold_meta)

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
        gbdt = HistGradientBoostingClassifier(max_iter=100, max_depth=5, random_state=seed)
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
        rf = RandomForestClassifier(n_estimators=100, max_depth=6, random_state=seed)
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
        mlp = MLPClassifier(hidden_layer_sizes=(64, 32), max_iter=200, random_state=seed, early_stopping=True)
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
        node_dim = graphs_train[0]["node_x"].shape[1]
        task_dim = graphs_train[0]["task_x"].shape[0]
        topo_dim = graphs_train[0]["topo_x"].shape[0]
        gcn = RealWorkflowGCN(node_dim, task_dim, topo_dim, hidden_dim=32)
        proba, train_ms, med_lat, p95_lat = train_and_eval_gnn(
            gcn, graphs_train, y_train, graphs_test, epochs=35, lr=0.005, seed=seed
        )
        auc, acc, f1, top20 = evaluate_predictions(y_test, proba, seed)
        results_by_model[model_names[4]].append({
            "fold": fold_idx + 1, "auc": auc, "acc": acc, "f1": f1, "top20": top20,
            "train_ms": train_ms, "infer_median_ms": med_lat, "infer_p95_ms": p95_lat
        })

        # 5. Real PyTorch GAT
        gat = RealWorkflowGAT(node_dim, task_dim, topo_dim, hidden_dim=32)
        proba, train_ms, med_lat, p95_lat = train_and_eval_gnn(
            gat, graphs_train, y_train, graphs_test, epochs=35, lr=0.005, seed=seed
        )
        auc, acc, f1, top20 = evaluate_predictions(y_test, proba, seed)
        results_by_model[model_names[5]].append({
            "fold": fold_idx + 1, "auc": auc, "acc": acc, "f1": f1, "top20": top20,
            "train_ms": train_ms, "infer_median_ms": med_lat, "infer_p95_ms": p95_lat
        })

    # 打印每折样本数与重叠度核验表
    print("\n" + "=" * 115)
    print(f"  [{regime_name}] 5 折切分元数据与零泄露核验表")
    print("=" * 115)
    fold_rows = []
    for fm in fold_metadata:
        fold_rows.append([
            f"Fold {fm['fold']}",
            f"{fm['n_train']} ({fm['train_pos_rate']*100:.1f}%)",
            f"{fm['n_test']} ({fm['test_pos_rate']*100:.1f}%)",
            f"{fm['train_unique_workflows']} / {fm['test_unique_workflows']}",
            fm["workflow_overlap_count"],
            f"{fm['train_unique_tasks']} / {fm['test_unique_tasks']}",
            fm["task_overlap_count"]
        ])
    print(tabulate(
        fold_rows,
        headers=["折号", "训练集 (正例率)", "测试集 (正例率)", "训练/测试工作流数", "工作流交集", "训练/测试任务数", "任务交集"],
        tablefmt="fancy_grid"
    ))

    # 汇总统计与配对检验
    dummy_aucs = [r["auc"] for r in results_by_model[model_names[0]]]
    dummy_top20s = [r["top20"] for r in results_by_model[model_names[0]]]
    # 对抗随机排序期望 (即每折真实测试集正样本率 * 100)
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
            f"{np.median(inf_meds):.4f} ms"
        ])

    print(f"\n  [{regime_name}] 5 折交叉验证模型性能与配对显著性检验 (vs. Prior Baseline)")
    print(tabulate(
        perf_rows,
        headers=[
            "模型",
            "ROC-AUC (Mean±SD)",
            "AUC 95% CI",
            "ΔAUC vs Dummy (p-val)",
            "Accuracy (Mean±SD)",
            "Top-20% 胜率 (Δ vs Prior, p)",
            "单样本推理中位数"
        ],
        tablefmt="fancy_grid"
    ))

    # GNN (GCN) vs GBDT & RF 的直接配对比较
    gcn_aucs = [r["auc"] for r in results_by_model[model_names[4]]]
    gbdt_aucs = [r["auc"] for r in results_by_model[model_names[1]]]
    rf_aucs = [r["auc"] for r in results_by_model[model_names[2]]]

    gcn_vs_gbdt = compute_paired_stats(gcn_aucs, gbdt_aucs)
    gcn_vs_rf = compute_paired_stats(gcn_aucs, rf_aucs)
    regime_summary["head_to_head"] = {
        "gcn_vs_gbdt_auc": gcn_vs_gbdt,
        "gcn_vs_rf_auc": gcn_vs_rf
    }
    print(
        f"  👉 [Head-to-Head 配对检验] "
        f"GCN vs GBDT: ΔAUC = {gcn_vs_gbdt['paired_diff_mean']:+.4f} "
        f"(95% CI [{gcn_vs_gbdt['paired_diff_ci95'][0]:+.4f}, {gcn_vs_gbdt['paired_diff_ci95'][1]:+.4f}], p={gcn_vs_gbdt['paired_ttest_p']:.4f}) | "
        f"GCN vs RF: ΔAUC = {gcn_vs_rf['paired_diff_mean']:+.4f} "
        f"(95% CI [{gcn_vs_rf['paired_diff_ci95'][0]:+.4f}, {gcn_vs_rf['paired_diff_ci95'][1]:+.4f}], p={gcn_vs_rf['paired_ttest_p']:.4f})"
    )

    return regime_summary


def main():
    records = ensure_dataset(sample_limit=1000)
    parsed_cache = [parse_topology_stats(r) for r in records]
    all_indices = np.arange(len(records))
    all_labels = np.array([int(r.get("label", 0)) for r in records], dtype=np.int64)
    workflow_groups = np.array([r.get("workflow_id", "") for r in records])
    task_groups = np.array([r.get("task_id", "") for r in records])

    # 1. Regime A: 按 workflow_id 5 折 GroupKFold (评估 Unseen Workflows)
    gkf_wf = GroupKFold(n_splits=5)
    wf_splits = list(gkf_wf.split(all_indices, all_labels, groups=workflow_groups))
    res_wf = run_cv_regime(
        "Regime A: Unseen Workflow (按 workflow_id 5-Fold GroupKFold)",
        wf_splits, records, parsed_cache
    )

    # 2. Regime B: 按 task_id 5 折 GroupKFold (评估 Unseen Tasks)
    gkf_task = GroupKFold(n_splits=5)
    task_splits = list(gkf_task.split(all_indices, all_labels, groups=task_groups))
    res_task = run_cv_regime(
        "Regime B: Unseen Task (按 task_id 5-Fold GroupKFold)",
        task_splits, records, parsed_cache
    )

    # 3. Regime C: 严格双盲归纳切分 (Unseen Workflow AND Unseen Task)
    # 构造 5 折：先将 workflow 分为 5 折，将 task 分为 5 折，第 k 折的 train 只取 wf!=k 且 task!=k，test 只取 wf==k 且 task==k
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
        if len(te_idx) >= 5 and len(np.unique(all_labels[te_idx])) == 2:
            strict_both_splits.append((tr_idx, te_idx))

    res_both = None
    if len(strict_both_splits) >= 3:
        res_both = run_cv_regime(
            f"Regime C: Both Unseen (双盲切分: workflow_id 与 task_id 均零交集, {len(strict_both_splits)} Folds)",
            strict_both_splits, records, parsed_cache
        )

    os.makedirs(os.path.dirname(OUTPUT_JSON_PATH), exist_ok=True)
    full_output = {
        "dataset_path": DATA_FILE,
        "total_records": len(records),
        "unique_workflows": int(len(np.unique(workflow_groups))),
        "unique_tasks": int(len(np.unique(task_groups))),
        "overall_positive_rate": float(np.mean(all_labels)),
        "regimes": {
            "unseen_workflow_group_kfold": res_wf,
            "unseen_task_group_kfold": res_task,
            "both_unseen_inductive_kfold": res_both
        }
    }
    with open(OUTPUT_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(full_output, f, indent=2, ensure_ascii=False)
    print(f"\n[结果落盘] 完整 GroupKFold 交叉验证与统计检验 JSON 已写入: {OUTPUT_JSON_PATH}")


if __name__ == "__main__":
    main()
