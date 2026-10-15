#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
QKnow 常驻 JVM 极速 IPC 守护服务冒烟验证脚本 (test_daemon_smoke.py)
验证 12 大探针全部成功调度、物理断言全通过与亚毫秒级 IPC 性能指标。
"""

import os
import sys
import time
import json
import socket
import subprocess

WORKSPACE_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, WORKSPACE_ROOT)

from experiment.runner import PROBE_BRIDGE, evaluate_logical_assertion, CONFIG

JAVA_BIN = "/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin/java"
UDS_SOCKET = "/tmp/qknow_daemon.sock"

def main():
    print("=" * 80)
    print("【里程碑一】QKnow 常驻 JVM 极速 IPC 守护服务端到端冒烟验证")
    print("=" * 80)

    # 1. 确保 classpath 文件就绪
    PROBE_BRIDGE._ensure_classpath()
    with open(PROBE_BRIDGE.cp_cache_file, "r", encoding="utf-8") as f:
        extra_cp = f.read().strip()
    core_target = os.path.join(WORKSPACE_ROOT, "backend/qknow-hermes/qknow-hermes-core/target")
    full_cp = f"{core_target}/test-classes:{core_target}/classes:{extra_cp}"

    # 2. 清理历史遗留 socket
    if os.path.exists(UDS_SOCKET):
        try:
            os.remove(UDS_SOCKET)
        except Exception:
            pass

    # 3. 后台启动 QKnowResidentDaemonServer
    daemon_cmd = [
        JAVA_BIN,
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED",
        "-cp", full_cp,
        "tech.qiantong.qknow.hermes.daemon.QKnowResidentDaemonServer",
        UDS_SOCKET
    ]
    print(f"[1/4] 正在拉起常驻守护进程: {' '.join(daemon_cmd[:4])} ...")
    daemon_proc = subprocess.Popen(
        daemon_cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )

    try:
        # 等待 socket 文件生成与就绪
        waited = 0.0
        while not os.path.exists(UDS_SOCKET) and waited < 10.0:
            time.sleep(0.1)
            waited += 0.1

        if not os.path.exists(UDS_SOCKET):
            print(f"[ERROR] 守护进程启动超时，未能生成 socket 文件: {UDS_SOCKET}")
            stdout, stderr = daemon_proc.communicate(timeout=2.0)
            print(f"STDOUT:\n{stdout}")
            print(f"STDERR:\n{stderr}")
            sys.exit(1)

        print(f"[2/4] 常驻守护进程就绪 (耗时: {waited:.2f}s)，Socket: {UDS_SOCKET}")

        # 4. 加载 12 个任务用例
        tasks_file = os.path.join(WORKSPACE_ROOT, "experiment/fixtures/agent_benchmark_tasks.jsonl")
        with open(tasks_file, "r", encoding="utf-8") as f:
            tasks = [json.loads(line.strip()) for line in f if line.strip()]

        print(f"[3/4] 启动 12 个物理探针端到端极速调度评测 (V1 完整方案)...")
        print("-" * 80)
        print(f"{'任务编号':<18} | {'探针类型':<22} | {'IPC 物理耗时':<12} | {'断言判定'}")
        print("-" * 80)

        variant_v1 = CONFIG["ablation_matrix"]["V1_full_hermes"]
        latencies = []
        all_passed = True

        for task in tasks:
            task_id = task["task_id"]
            probe_payload = task["probe_invocation"]
            probe_type = probe_payload.get("probe_type")
            assertions = task["ground_truth_criteria"].get("logical_assertions", [])

            t0 = time.perf_counter()
            probe_result = PROBE_BRIDGE.execute_probe(probe_payload, variant_v1)
            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            latencies.append(elapsed_ms)

            # 校验物理断言
            passed = True
            for assertion in assertions:
                ok = evaluate_logical_assertion(assertion, probe_result)
                if not ok:
                    passed = False
                    all_passed = False
                    print(f"  [ASSERTION FAILED] Task={task_id}, Assert='{assertion}', Result={probe_result}")

            status_str = "PASS (100% 绿色)" if passed else "FAIL"
            print(f"{task_id:<18} | {probe_type:<22} | {elapsed_ms:8.2f} ms | {status_str}")

        print("-" * 80)
        avg_latency = sum(latencies) / len(latencies)
        min_latency = min(latencies)
        p95_latency = sorted(latencies)[int(len(latencies) * 0.95)]
        print(f"[4/4] 性能指标统计汇总:")
        print(f"  - 探针总数: {len(tasks)}")
        print(f"  - 平均物理 IPC 延迟: {avg_latency:.2f} ms (相比原 1850ms 压降 {(1850.0 - avg_latency)/1850.0 * 100:.1f}%)")
        print(f"  - 最低单次调用耗时: {min_latency:.2f} ms")
        print(f"  - P95 物理调度延迟:  {p95_latency:.2f} ms")
        print(f"  - 全部断言验证结果: {'100% 成功达成' if all_passed else '存在断言失败'}")
        print("=" * 80)

        if not all_passed:
            sys.exit(2)

    finally:
        print("[清理] 正在安全优雅终止常驻守护进程...")
        daemon_proc.terminate()
        try:
            daemon_proc.wait(timeout=3.0)
        except Exception:
            daemon_proc.kill()
        if os.path.exists(UDS_SOCKET):
            try:
                os.remove(UDS_SOCKET)
            except Exception:
                pass
        print("[清理] 守护进程已安全关闭退出。")

if __name__ == "__main__":
    main()
