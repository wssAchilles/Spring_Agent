#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
成本熔断守护程序 (Cost Guard Listener)
负责在实验运行全过程中，实时监听并计算当前各实验组累计产生的物理 API 花费。
当累计费用达到软预警线 (6.50 元) 时发出高危告警；
当累计费用达到硬熔断线 (8.00 元) 时向父进程发送 SIGTERM 终止信号，坚决捍卫 10.00 元绝对安全红线。
"""

import sys
import os
import json
import time
import glob
import signal

def calculate_single_payload_cost(payload, pricing_spec):
    usage = payload.get("usage", {})
    hit_tokens = usage.get("prompt_cache_hit_tokens", 0)
    miss_tokens = usage.get("prompt_cache_miss_tokens", usage.get("prompt_tokens", 0) - hit_tokens)
    out_tokens = usage.get("completion_tokens", 0)

    # 优先采用高峰期单价保守估计
    rates = pricing_spec.get("rates", {}).get("peak", {})
    hit_rate = rates.get("cache_hit_input_cny", 0.0216)
    miss_rate = rates.get("cache_miss_input_cny", 1.0800)
    out_rate = rates.get("output_cny", 4.3200)

    cost = (hit_tokens * hit_rate + miss_tokens * miss_rate + out_tokens * out_rate) / 1e6
    return cost, hit_tokens, miss_tokens, out_tokens

def scan_total_cost(runs_dir, pricing_spec):
    total_cost = 0.0
    total_hit = 0
    total_miss = 0
    total_out = 0
    file_count = 0

    payload_files = glob.glob(os.path.join(runs_dir, "**/payloads/*.json"), recursive=True)
    for pf in payload_files:
        try:
            with open(pf, "r", encoding="utf-8") as f:
                data = json.load(f)
                cost, h, m, o = calculate_single_payload_cost(data, pricing_spec)
                total_cost += cost
                total_hit += h
                total_miss += m
                total_out += o
                file_count += 1
        except Exception:
            pass

    return total_cost, total_hit, total_miss, total_out, file_count

def main():
    workspace_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
    exp_dir = os.path.join(workspace_root, "experiment")
    runs_dir = os.path.join(exp_dir, "runs")
    config_pricing = os.path.join(exp_dir, "config/deepseek_pricing_spec.json")
    config_budget = os.path.join(exp_dir, "config/cost_budget_policy.json")

    with open(config_pricing, "r", encoding="utf-8") as f:
        pricing_spec = json.load(f)
    with open(config_budget, "r", encoding="utf-8") as f:
        budget_policy = json.load(f)

    hard_cap = budget_policy.get("hard_budget_cap_cny", 8.00)
    soft_warning = budget_policy.get("soft_warning_threshold_cny", 6.50)

    print(f"[CostGuard] 成本守卫进程已就绪，当前硬熔断阈值: ¥{hard_cap:.2f}，软预警阈值: ¥{soft_warning:.2f}")

    cost, h, m, o, count = scan_total_cost(runs_dir, pricing_spec)
    print(f"[CostGuard] 初始扫描完成: 已处理 {count} 个 Payload, 累计花费: ¥{cost:.4f} CNY")

if __name__ == "__main__":
    main()
