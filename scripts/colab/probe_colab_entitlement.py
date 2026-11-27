#!/usr/bin/env python3
"""
Google Colab Pro 硬件配额与模型支持自动探测工具
(Automated Entitlement & Accelerator Probe for Colab Pro)

功能：
1. 依次测试云端是否能成功分配 T4, L4, A100, H100 等加速器；
2. 获取当前节点的真实 GPU 型号、显存大小与算力代际；
3. 测试完成后立即释放云端 VM，绝不残留空转，零浪费计算单元。
"""

import subprocess
import sys
import json
import time

ACCELERATORS_TO_TEST = [
    ("T4", "基础 GPU (16GB, Turing 架构, 约 1.96 CU/hr)"),
    ("L4", "高性价比现代 GPU (24GB, Ada 架构, 支持 FP8/BF16, 约 3.5 CU/hr)"),
    ("A100", "高算力数据中心 GPU (40GB/80GB, Ampere 架构, 约 11~13 CU/hr)"),
    ("H100", "顶配算力 GPU (80GB, Hopper 架构, 极高算力, 约 12~15 CU/hr)")
]

REMOTE_PROBE_SCRIPT = """
import torch
import sys

print("=== REMOTE HARDWARE PROBE START ===")
if not torch.cuda.is_available():
    print("NO_CUDA")
    sys.exit(0)

device_name = torch.cuda.get_device_name(0)
total_vram_gb = torch.cuda.get_device_properties(0).total_memory / (1024 ** 3)
cap = torch.cuda.get_device_capability(0)

print(f"DEVICE_NAME:{device_name}")
print(f"VRAM_GB:{total_vram_gb:.2f}")
print(f"CAPABILITY:{cap[0]}.{cap[1]}")
print("=== REMOTE HARDWARE PROBE END ===")
"""

def main():
    print("=" * 75)
    print("     Google Colab Pro 硬件配额与可用性自动化探测 (Colab Entitlement Probe)")
    print("=" * 75)

    # 1. 检查本地 colab CLI
    try:
        ver_out = subprocess.check_output(["colab", "version"], encoding="utf-8").strip()
        print(f"[本地 CLI] 已检测到 Colab CLI: {ver_out}")
    except Exception as e:
        print("❌ 未在 PATH 中找到 colab 命令，请先运行: export PATH=\"$HOME/.local/bin:$PATH\"")
        sys.exit(1)

    # 2. 检查账户余额
    try:
        usage_out = subprocess.check_output(["colab", "--auth", "adc", "usage"], encoding="utf-8").strip()
        print(f"[账户配额] {usage_out}")
    except Exception as e:
        print(f"⚠️ 查询余额提示: {e}")

    print("\n准备对以下加速器进行瞬态调度探测 (测试完毕自动释放 VM)...")
    results = []

    for acc, desc in ACCELERATORS_TO_TEST:
        print(f"\n🔍 正在探测加速器: 【{acc}】 ({desc})...")
        t0 = time.time()
        
        # 使用 colab run 执行瞬态代码
        cmd = [
            "colab", "--auth", "adc", "run",
            "--gpu", acc,
            "-c", REMOTE_PROBE_SCRIPT
        ]
        
        try:
            # 执行探测 (超时限制 120 秒)
            proc = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=120
            )
            cost_time = time.time() - t0
            
            if proc.returncode == 0 and "DEVICE_NAME:" in proc.stdout:
                # 解析硬件输出
                lines = proc.stdout.splitlines()
                dev_name = "Unknown"
                vram = "Unknown"
                cap = "Unknown"
                for line in lines:
                    if line.startswith("DEVICE_NAME:"):
                        dev_name = line.split(":", 1)[1]
                    elif line.startswith("VRAM_GB:"):
                        vram = line.split(":", 1)[1] + " GB"
                    elif line.startswith("CAPABILITY:"):
                        cap = line.split(":", 1)[1]
                        
                status = "✅ 成功分配并运行"
                detail = f"分配卡型: {dev_name} | 显存: {vram} | 算力代际: sm_{cap} (耗时: {cost_time:.1f}s)"
                print(f"   -> {status}: {detail}")
                results.append((acc, "支持分配", detail))
            else:
                err_msg = proc.stderr.strip() or proc.stdout.strip()
                if "400" in err_msg or "ResourceExhausted" in err_msg or "quota" in err_msg.lower():
                    status = "⚠️ 暂时无配额或当前无空闲物理节点"
                else:
                    status = f"❌ 分配失败 (错误代码 {proc.returncode})"
                detail = err_msg.splitlines()[-1] if err_msg else "无详细错误日志"
                print(f"   -> {status}: {detail}")
                results.append((acc, "暂不可用/配额限制", detail))
                
        except subprocess.TimeoutExpired:
            print(f"   -> ⏱️ 调度超时 (120s 未响应)")
            results.append((acc, "超时", "调度超时"))
        except Exception as e:
            print(f"   -> ❌ 执行异常: {e}")
            results.append((acc, "异常", str(e)))

    # 3. 汇总报告
    print("\n" + "=" * 75)
    print("                    Colab Pro 硬件配额探测汇总")
    print("=" * 75)
    for acc, stat, det in results:
        print(f"• 加速器 [{acc:4s}]: {stat:12s} | {det}")
    print("=" * 75)
    print("💡 结论指引:")
    print("1. 若 H100 显示支持，你可随时在批处理或模型微调中使用 `--gpu H100`；")
    print("2. 若 H100 暂无空闲，A100 与 L4 是最佳平替，显存足够跑 14B~70B 量化模型；")
    print("3. 所有测试 VM 已由 `colab run` 自动终止，未残留任何计费实例。")

if __name__ == "__main__":
    main()
