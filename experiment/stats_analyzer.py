#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
企业级 AI-Native RAG 与 Hermes 智能体编排平台
顶刊级统计显著性推断分析器与 LaTeX 三线表生成器 (Stats Analyzer & LaTeX Exporter)
严格遵循 AGENTS.md 规程与 ESWA / IEEE TKDE 标准设计

核心职责：
1. 聚合 trajectory.jsonl 全量高难度探针评测数据；
2. 执行 10,000 次 Bootstrap 重采样经验分位数计算 95% 置信区间；
3. 执行非参数 Wilcoxon 符号秩检验与配对统计推断；
4. 导出符合顶刊发表标准的 Booktabs LaTeX 三线表与 JSON 统计报告。
"""

import os
import sys
import json
import numpy as np
import math
from typing import Dict, Any, List, Tuple

try:
    from scipy import stats
    HAS_SCIPY = True
except ImportError:
    HAS_SCIPY = False

WORKSPACE_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
EXP_DIR = os.path.join(WORKSPACE_ROOT, "experiment")
OUTPUTS_DIR = os.path.join(EXP_DIR, "outputs")
REPORTS_DIR = os.path.join(EXP_DIR, "reports")

def bootstrap_ci(data: List[float], n_bootstraps: int = 10000, ci_level: float = 0.95) -> Tuple[float, float]:
    """执行 10,000 次 Bootstrap 重采样经验分位数置信区间计算"""
    if len(data) == 0:
        return 0.0, 0.0
    arr = np.array(data)
    boot_means = []
    rng = np.random.default_rng(42)
    for _ in range(n_bootstraps):
        sample = rng.choice(arr, size=len(arr), replace=True)
        boot_means.append(np.mean(sample))
    lower_pct = (1.0 - ci_level) / 2.0 * 100.0
    upper_pct = (1.0 + ci_level) / 2.0 * 100.0
    return float(np.percentile(boot_means, lower_pct)), float(np.percentile(boot_means, upper_pct))

def pure_math_wilcoxon_signed_rank(v1: List[float], v2: List[float]) -> Tuple[float, float]:
    """
    纯数学实现的 Wilcoxon 符号秩检验（针对非正态双峰样本分布）
    """
    diffs = np.array(v1) - np.array(v2)
    # 剔除平局
    diffs = diffs[diffs != 0]
    n = len(diffs)
    if n < 5:
        return 0.0, 1.0

    abs_diffs = np.abs(diffs)
    ranks = stats.rankdata(abs_diffs) if HAS_SCIPY else np.argsort(np.argsort(abs_diffs)) + 1

    w_plus = np.sum(ranks[diffs > 0])
    w_minus = np.sum(ranks[diffs < 0])
    t_stat = min(w_plus, w_minus)

    # 渐近正态近似
    mean_w = n * (n + 1) / 4.0
    var_w = n * (n + 1) * (2 * n + 1) / 24.0
    std_w = math.sqrt(var_w)

    z = (t_stat - mean_w + 0.5) / std_w
    p_val = 2.0 * (1.0 - 0.5 * (1.0 + math.erf(abs(z) / math.sqrt(2))))
    return float(t_stat), max(1e-12, min(1.0, float(p_val)))

def perform_hypothesis_test(baseline_data: List[float], candidate_data: List[float]) -> Tuple[str, float, str]:
    """执行非参数假设检验并生成学术显著性标记"""
    diffs = np.array(candidate_data) - np.array(baseline_data)
    if np.all(diffs == 0):
        return "Invariant", 1.0, ""

    if HAS_SCIPY:
        try:
            stat, p_val = stats.wilcoxon(candidate_data, baseline_data, zero_method="pratt")
            test_type = "Wilcoxon Signed-Rank (SciPy)"
        except Exception:
            stat, p_val = pure_math_wilcoxon_signed_rank(candidate_data, baseline_data)
            test_type = "Wilcoxon Signed-Rank (Analytical)"
    else:
        stat, p_val = pure_math_wilcoxon_signed_rank(candidate_data, baseline_data)
        test_type = "Wilcoxon Signed-Rank (Pure Math)"

    p_val = float(p_val)
    if p_val < 0.001:
        sig_mark = "^{\\dagger\\dagger\\dagger}"
    elif p_val < 0.01:
        sig_mark = "^{\\dagger\\dagger}"
    elif p_val < 0.05:
        sig_mark = "^{\\dagger}"
    else:
        sig_mark = ""

    return test_type, p_val, sig_mark

def load_trajectory_data(trajectory_path: str) -> Dict[str, Dict[str, Any]]:
    """读取全量 trajectory.jsonl 并按消融变体聚合度量"""
    if not os.path.exists(trajectory_path):
        return {}

    by_variant = {}
    with open(trajectory_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            rec = json.loads(line)
            v_code = rec["variant_code"]
            if v_code not in by_variant:
                by_variant[v_code] = {
                    "records": [],
                    "by_seed": {}
                }
            by_variant[v_code]["records"].append(rec)
            seed = rec["seed"]
            by_variant[v_code]["by_seed"].setdefault(seed, []).append(rec)

    return by_variant

def main():
    print("=" * 80)
    print("Hermes 顶刊级统计显著性推断分析器 (Stats Analyzer)")
    print("=" * 80)

    trajectory_path = os.path.join(OUTPUTS_DIR, "trajectory.jsonl")
    if not os.path.exists(trajectory_path):
        print(f"[Error] 未找到轨迹文件 {trajectory_path}，请先执行 runner.py！")
        sys.exit(1)

    variant_data = load_trajectory_data(trajectory_path)
    if not variant_data:
        print("[Error] 轨迹数据为空！")
        sys.exit(1)

    variants_order = [
        ("full_hermes", "V1: Full Architecture (完整生产上限)"),
        ("wo_colbert_simd", "V2: w/o Colbert SIMD (消融晚期交互)"),
        ("wo_speculative_rag", "V3: w/o Speculative RAG (消融推测性草稿)"),
        ("wo_graphrag_tree", "V4: w/o GraphRAG Tree (消融社区分层树)"),
        ("wo_mcts_dynamic_roles", "V5: w/o MCTS & DyLAN (消融树规划与角色流变)"),
        ("wo_sandbox_reflexion", "V6: w/o Sandbox & Reflexion (消融企业沙箱与反思记忆)"),
        ("baseline_vanilla_react", "V7: Vanilla ReAct Baseline (原生基线组)")
    ]

    baseline_code = "baseline_vanilla_react"
    if baseline_code not in variant_data:
        baseline_code = list(variant_data.keys())[-1]

    baseline_scores = [r["score"] for r in variant_data[baseline_code]["records"]]

    summary_rows = []
    latex_rows = []

    for code, label in variants_order:
        if code not in variant_data:
            continue

        v_info = variant_data[code]
        records = v_info["records"]
        scores = [r["score"] for r in records]
        success_list = [1.0 if r["success"] else 0.0 for r in records]
        durations = [r["duration_ms"] for r in records]
        costs = [r.get("cost_cny", 0.0) for r in records]

        sr_mean = np.mean(success_list) * 100.0
        sr_ci = bootstrap_ci(success_list, 10000, 0.95)
        score_mean = np.mean(scores)
        score_std = np.std(scores)
        p99_latency = np.percentile(durations, 99) if durations else 0.0
        avg_cost = np.mean(costs) if costs else 0.0

        # 假设检验 (相对于 baseline)
        if code == baseline_code:
            test_type, p_val, sig_mark = "Baseline", 1.0, "-"
        else:
            test_type, p_val, sig_mark = perform_hypothesis_test(baseline_scores, scores)

        summary_rows.append({
            "variant_code": code,
            "label": label,
            "pass_rate_mean": round(sr_mean, 2),
            "pass_rate_ci_95": [round(sr_ci[0] * 100.0, 2), round(sr_ci[1] * 100.0, 2)],
            "score_mean": round(score_mean, 4),
            "score_std": round(score_std, 4),
            "p99_latency_ms": round(p99_latency, 2),
            "avg_cost_cny": round(avg_cost, 5),
            "p_value_vs_baseline": round(p_val, 6),
            "significance_mark": sig_mark
        })

        p_str = f"{p_val:.4f}" if p_val >= 0.0001 else "<0.0001"
        latex_rows.append(
            f"{label} & {sr_mean:.1f}\\% [{sr_ci[0]*100.0:.1f}, {sr_ci[1]*100.0:.1f}] & "
            f"{score_mean:.3f} $\\pm$ {score_std:.3f}{sig_mark} & "
            f"{p99_latency:.1f} & ¥{avg_cost:.4f} & {p_str} \\\\"
        )

    # 导出 JSON 报告
    os.makedirs(REPORTS_DIR, exist_ok=True)
    report_json_path = os.path.join(OUTPUTS_DIR, "ablation_statistical_report.json")
    with open(report_json_path, "w", encoding="utf-8") as f:
        json.dump(summary_rows, f, ensure_ascii=False, indent=2)
    print(f"统计分析 JSON 报告已保存至: {report_json_path}")

    # 导出 LaTeX Booktabs 三线表
    latex_content = (
        "\\begin{table*}[t]\n"
        "\\centering\n"
        "\\small\n"
        "\\caption{Hermes 智能体架构在真实 Java 21 / Rust 物理探针下的正交消融评测结果 (10,000 次 Bootstrap 经验 95\\% CI 与 Wilcoxon 符号秩检验)}\n"
        "\\label{tab:hermes_orthogonal_ablation}\n"
        "\\begin{tabular}{lccccc}\n"
        "\\toprule\n"
        "\\textbf{消融实验变体} & \\textbf{Pass@1 (95\\% CI)} & \\textbf{Score (Mean $\\pm$ Std)} & \\textbf{P99 Latency (ms)} & \\textbf{Cost (¥)} & \\textbf{$p$-value vs V7} \\\\\n"
        "\\midrule\n"
        + "\n".join(latex_rows) + "\n"
        "\\bottomrule\n"
        "\\end{tabular}\n"
        "\\end{table*}\n"
    )

    latex_tex_path = os.path.join(REPORTS_DIR, "table_ablation_results.tex")
    with open(latex_tex_path, "w", encoding="utf-8") as f:
        f.write(latex_content)
    print(f"顶刊 LaTeX 三线表源码已保存至: {latex_tex_path}")

    # 打印控制台表格
    print("-" * 100)
    print(f"{'消融实验变体':<40} | {'Pass@1 (%)':<15} | {'Score (Mean±Std)':<18} | {'P99 Latency':<12} | {'p-value':<10}")
    print("-" * 100)
    for r in summary_rows:
        print(f"{r['label']:<40} | {r['pass_rate_mean']:<6} [{r['pass_rate_ci_95'][0]:.1f},{r['pass_rate_ci_95'][1]:.1f}] | {r['score_mean']:.3f}±{r['score_std']:.3f}{r['significance_mark']:<4} | {r['p99_latency_ms']:<8} ms | {r['p_value_vs_baseline']:<10}")
    print("-" * 100)

if __name__ == "__main__":
    main()
