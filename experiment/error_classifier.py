#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
企业级 AI-Native RAG 与 Hermes 智能体编排平台
顶刊级细粒度三层 9 类失效模式归因与 Pareto 效率前沿分析器 (Error Classifier & Pareto Analyzer)
严格遵循 EXPERIMENT_SKILL.md 规程设计

核心职责：
1. 扫描所有消融组轨迹，提取三层 9 类归因特征；
2. 构造桑基图 (Sankey Diagram) 节点与边流向数据；
3. 计算 (Unit Cost, Pass@1) 空间上的非支配 Pareto 效率前沿。
"""

import os
import sys
import json
import numpy as np
from typing import Dict, Any, List, Tuple

WORKSPACE_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
EXP_DIR = os.path.join(WORKSPACE_ROOT, "experiment")
OUTPUTS_DIR = os.path.join(EXP_DIR, "outputs")

ERROR_TAXONOMY = {
    "Level 1: Planning & Reasoning": [
        "PR-01_GOAL_DRIFT",
        "PR-02_PREMATURE_HALTING",
        "PR-03_INFINITE_LOOP",
        "PR-04_PREFETCH_DESYNC"
    ],
    "Level 2: Tool & Grounding": [
        "TG-01_SCHEMA_HALLUCINATION",
        "TG-02_TOOL_MISSELECTION",
        "TG-03_TIMEOUT_DENIED",
        "TG-04_HOARE_INVARIANT_VIOLATION"
    ],
    "Level 3: Context & Attention": [
        "CA-01_CONTEXT_OVERFLOW",
        "CA-02_LOST_IN_THE_MIDDLE",
        "CA-03_HYPERSPHERICAL_COLLAPSE",
        "CA-04_HIPPOCAMPUS_FORGETTING",
        "CA-05_TOPOLOGY_BROADCAST_STORM"
    ]
}

def get_error_level(error_code: str) -> str:
    for lvl, codes in ERROR_TAXONOMY.items():
        if error_code in codes:
            return lvl
    return "Level 1: Planning & Reasoning"

def is_pareto_efficient(costs: np.ndarray, success_rates: np.ndarray) -> np.ndarray:
    """计算 Pareto 非支配前沿 (成本越低越好，成功率越高越好)"""
    is_efficient = np.ones(costs.shape[0], dtype=bool)
    for i in range(costs.shape[0]):
        if is_efficient[i]:
            # 如果存在另一个点 j，其成本更低且成功率更高（至少一项严格更优），则 i 被支配
            dominated = np.any((costs <= costs[i]) & (success_rates >= success_rates[i]) & ((costs < costs[i]) | (success_rates > success_rates[i])))
            if dominated:
                is_efficient[i] = False
    return is_efficient

def main():
    print("=" * 80)
    print("Hermes 细粒度失效模式归因与 Pareto 效率前沿分析器")
    print("=" * 80)

    variant_error_counts = {}
    points_costs = []
    points_success = []
    variant_names = []

    sankey_nodes = []
    sankey_node_map = {}
    sankey_links = []

    def get_or_create_node(name: str) -> int:
        if name not in sankey_node_map:
            idx = len(sankey_nodes)
            sankey_nodes.append({"id": idx, "name": name})
            sankey_node_map[name] = idx
        return sankey_node_map[name]

    traj_file = os.path.join(OUTPUTS_DIR, "trajectory.jsonl")
    if os.path.exists(traj_file):
        print(f"从统一轨迹文件读取评测数据: {traj_file}")
        records_by_variant = {}
        with open(traj_file, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip():
                    continue
                rec = json.loads(line.strip())
                v_code = rec.get("variant_code", "unknown")
                records_by_variant.setdefault(v_code, []).append(rec)

        for v_code, recs in records_by_variant.items():
            variant_error_counts[v_code] = {
                "total_failures": 0,
                "by_level": {lvl: 0 for lvl in ERROR_TAXONOMY.keys()},
                "by_code": {}
            }
            v_success = 0
            v_costs_usd = []
            for rec in recs:
                cost_usd = rec.get("cost_cny", 0.0) / 7.20
                v_costs_usd.append(cost_usd)
                if rec.get("success", False):
                    v_success += 1
                else:
                    err_code = rec.get("failure_reason") or "PR-01_GOAL_DRIFT"
                    variant_error_counts[v_code]["total_failures"] += 1
                    variant_error_counts[v_code]["by_code"][err_code] = variant_error_counts[v_code]["by_code"].get(err_code, 0) + 1
                    err_level = get_error_level(err_code)
                    variant_error_counts[v_code]["by_level"][err_level] += 1

            sr = v_success / max(1, len(recs))
            avg_c = np.mean(v_costs_usd) if v_costs_usd else 0.0
            points_costs.append(avg_c)
            points_success.append(sr)
            variant_names.append(v_code)
    else:
        summary_file = os.path.join(OUTPUTS_DIR, "all_experiments_summary.json")
        if not os.path.exists(summary_file):
            print(f"[Error] 未找到数据文件 {traj_file} 或 {summary_file}，请先执行 runner.py！")
            sys.exit(1)

        with open(summary_file, "r", encoding="utf-8") as f:
            summary_data = json.load(f)

        variant_summaries = summary_data.get("summary_by_variant", {})
        for v_code in variant_summaries.keys():
            variant_dir = os.path.join(OUTPUTS_DIR, v_code)
            if not os.path.exists(variant_dir):
                continue

            variant_error_counts[v_code] = {
                "total_failures": 0,
                "by_level": {lvl: 0 for lvl in ERROR_TAXONOMY.keys()},
                "by_code": {}
            }

            seed_dirs = [d for d in os.listdir(variant_dir) if d.startswith("seed_")]
            v_success_count = 0
            v_total_tasks = 0
            v_costs = []

            for s_dir in seed_dirs:
                s_traj = os.path.join(variant_dir, s_dir, "trajectory.jsonl")
                if not os.path.exists(s_traj):
                    continue
                with open(s_traj, "r", encoding="utf-8") as f:
                    for line in f:
                        if not line.strip():
                            continue
                        rec = json.loads(line.strip())
                        v_total_tasks += 1
                        v_costs.append(rec.get("total_cost_usd", 0.0))
                        if rec.get("task_success", False):
                            v_success_count += 1
                        else:
                            err_code = rec.get("error_type") or "PR-01_GOAL_DRIFT"
                            variant_error_counts[v_code]["total_failures"] += 1
                            variant_error_counts[v_code]["by_code"][err_code] = variant_error_counts[v_code]["by_code"].get(err_code, 0) + 1
                            err_level = get_error_level(err_code)
                            variant_error_counts[v_code]["by_level"][err_level] += 1

            sr = v_success_count / max(1, v_total_tasks)
            avg_c = np.mean(v_costs) if v_costs else 0.0

            points_costs.append(avg_c)
            points_success.append(sr)
            variant_names.append(v_code)

    # 构造桑基图 links: Variant -> Level -> Error Code
    level_counts = {}
    code_counts = {}

    for v_code, err_data in variant_error_counts.items():
        v_node_id = get_or_create_node(v_code)
        for lvl, cnt in err_data["by_level"].items():
            if cnt > 0:
                lvl_node_id = get_or_create_node(lvl)
                sankey_links.append({
                    "source": v_node_id,
                    "target": lvl_node_id,
                    "value": cnt
                })

        for err_code, cnt in err_data["by_code"].items():
            err_level = get_error_level(err_code)
            lvl_node_id = get_or_create_node(err_level)
            code_node_id = get_or_create_node(err_code)
            sankey_links.append({
                "source": lvl_node_id,
                "target": code_node_id,
                "value": cnt
            })

    # 合并重复的 links
    merged_links_dict = {}
    for link in sankey_links:
        key = (link["source"], link["target"])
        merged_links_dict[key] = merged_links_dict.get(key, 0) + link["value"]

    sankey_links_final = [{"source": k[0], "target": k[1], "value": v} for k, v in merged_links_dict.items()]

    # 计算 Pareto 非支配前沿
    costs_arr = np.array(points_costs)
    sr_arr = np.array(points_success)
    pareto_mask = is_pareto_efficient(costs_arr, sr_arr)

    pareto_frontier = []
    dominated_points = []
    for idx, v_name in enumerate(variant_names):
        pt = {
            "variant": v_name,
            "unit_cost_usd": round(float(costs_arr[idx]), 6),
            "unit_cost_cny": round(float(costs_arr[idx] * 7.20), 6),
            "pass_at_1": round(float(sr_arr[idx] * 100), 2),
            "is_pareto_optimal": bool(pareto_mask[idx])
        }
        if pareto_mask[idx]:
            pareto_frontier.append(pt)
        else:
            dominated_points.append(pt)

    report_result = {
        "error_distribution_by_variant": variant_error_counts,
        "sankey_diagram_data": {
            "nodes": sankey_nodes,
            "links": sankey_links_final
        },
        "pareto_frontier_analysis": {
            "description": "基于 (Unit Cost USD, Pass@1) 空间的最优非支配前沿分析",
            "pareto_optimal_variants": pareto_frontier,
            "dominated_variants": dominated_points
        }
    }

    report_path = os.path.join(OUTPUTS_DIR, "error_taxonomy_and_pareto_report.json")
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report_result, f, indent=2, ensure_ascii=False)

    print("\n失效模式归因与 Pareto 效率前沿分析完毕！")
    print(f"分析报告已落盘至: {report_path}")
    print("\n【Pareto 非支配前沿方案】:")
    for p in pareto_frontier:
        print(f"  * {p['variant']}: Pass@1 = {p['pass_at_1']}%, 单任务成本 = ${p['unit_cost_usd']} (¥{p['unit_cost_cny']} CNY)")
    print("\n【被支配的低效方案 (Dominated)】:")
    for p in dominated_points:
        print(f"  * {p['variant']}: Pass@1 = {p['pass_at_1']}%, 单任务成本 = ${p['unit_cost_usd']} (¥{p['unit_cost_cny']} CNY)")
    print("=" * 80)

if __name__ == "__main__":
    main()
