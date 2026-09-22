#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Update Carrier Pinning Analysis Figure (Figure 7) to eliminate all box-line intersections.
Ensures annotation text bounding boxes and arrows strictly avoid intersecting with lines, legends, or axes.
"""

import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

PALETTE = {
    "blue_main": "#0F4D92",
    "blue_secondary": "#3775BA",
    "green_1": "#DDF3DE",
    "green_2": "#AADCA9",
    "green_3": "#8BCF8B",
    "red_1": "#F6CFCB",
    "red_2": "#E9A6A1",
    "red_strong": "#B64342",
    "neutral": "#767676",
    "neutral_light": "#CFCECE",
    "highlight": "#FFD700",
    "teal": "#42949E",
    "violet": "#9A4D8E",
}

def apply_publication_style(font_size=12, axes_linewidth=2.0):
    plt.rcParams.update({
        "font.family": "sans-serif",
        "font.sans-serif": ["DejaVu Sans", "Helvetica", "Arial", "Lucida Grande"],
        "font.size": font_size,
        "axes.spines.right": False,
        "axes.spines.top": False,
        "axes.linewidth": axes_linewidth,
        "axes.labelsize": font_size + 2,
        "axes.titlesize": font_size + 3,
        "xtick.labelsize": font_size,
        "ytick.labelsize": font_size,
        "legend.fontsize": font_size - 1.5,
        "legend.frameon": False,
        "figure.titlesize": font_size + 4,
        "axes.grid": False,
        "pdf.fonttype": 42,
        "ps.fonttype": 42,
    })

def plot_carrier_pinning():
    apply_publication_style(font_size=12, axes_linewidth=2.0)
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5.2))

    concurrency = np.array([50, 100, 150, 200, 250, 300, 350, 400, 450, 500])
    
    # -------------------------------------------------------------
    # Panel (a): 载体线程 (ForkJoinWorkerThread) 数量
    # -------------------------------------------------------------
    isolated_threads = np.array([16, 16, 16, 16, 16, 16, 16, 16, 16, 16])
    pinned_threads = np.array([16, 18, 24, 32, 45, 62, 85, 110, 138, 165])

    ax1.plot(concurrency, pinned_threads, marker="^", markersize=7, linewidth=2.5,
             color=PALETTE["red_strong"], label="Direct JNI SIMD (Pinned)")
    ax1.plot(concurrency, isolated_threads, marker="o", markersize=7, linewidth=2.5,
             color=PALETTE["blue_main"], label="Dedicated Pool Isolated (Ours)")
    ax1.axhline(16, color=PALETTE["neutral"], linestyle=":", linewidth=1.5, alpha=0.7, label="Default Core Pool Size (16)")
    ax1.fill_between(concurrency, isolated_threads, pinned_threads, color=PALETTE["red_strong"], alpha=0.10)
    ax1.grid(axis="y", linestyle="--", alpha=0.3)

    # 红色数值框：放置在右上开阔区 (x=330, y=188)，底边在 y=178，完全高于红线最高点 165，且远离左侧图例
    ax1.annotate("Thread Compensation Spike\n(165 Carrier Threads)", xy=(500, 165), xytext=(325, 186),
                 arrowprops=dict(facecolor=PALETTE["red_strong"], shrink=0.08, width=1.5, headwidth=6),
                 fontsize=9.5, fontweight="bold", color=PALETTE["red_strong"],
                 bbox=dict(boxstyle="round,pad=0.25", facecolor="white", edgecolor=PALETTE["red_strong"], alpha=0.95))

    # 蓝色数值框：放置在红线 (71-110) 与蓝线 (16) 中间的开阔带 (x=310, y=48)
    ax1.annotate("Zero Carrier Pinning (Fixed 16)", xy=(500, 16), xytext=(310, 48),
                 arrowprops=dict(facecolor=PALETTE["blue_main"], shrink=0.08, width=1.5, headwidth=6),
                 fontsize=9.5, fontweight="bold", color=PALETTE["blue_main"],
                 bbox=dict(boxstyle="round,pad=0.25", facecolor="white", edgecolor=PALETTE["blue_main"], alpha=0.95))

    ax1.set_xlabel("Concurrent Client Requests")
    ax1.set_ylabel("ForkJoinPool Carrier Threads")
    ax1.set_title("(a) Carrier Thread Pool Growth")
    ax1.set_ylim(0, 220)
    ax1.legend(loc="upper left")

    # -------------------------------------------------------------
    # Panel (b): CPU 上下文切换速率 (k/sec)
    # -------------------------------------------------------------
    pinned_cs = np.array([4.2, 8.5, 15.2, 26.8, 41.5, 62.4, 88.0, 115.6, 142.3, 168.0])
    isolated_cs = np.array([3.8, 6.2, 9.1, 12.4, 15.8, 19.5, 23.2, 27.0, 30.5, 34.2])

    ax2.plot(concurrency, pinned_cs, marker="s", markersize=7, linewidth=2.5,
             color=PALETTE["red_strong"], label="Direct JNI SIMD (High Contention)")
    ax2.plot(concurrency, isolated_cs, marker="o", markersize=7, linewidth=2.5,
             color=PALETTE["teal"], label="Dedicated Pool Isolated (Bounded)")
    ax2.grid(axis="y", linestyle="--", alpha=0.3)

    # 红色数值框：放置在右上开阔区 (x=325, y=186)，底边在 y=178，完全高于红线最高点 168，且远离左侧图例
    ax2.annotate("168.0k Context Switches/s\n(OS Thrashing)", xy=(500, 168.0), xytext=(325, 186),
                 arrowprops=dict(facecolor=PALETTE["red_strong"], shrink=0.08, width=1.5, headwidth=6),
                 fontsize=9.5, fontweight="bold", color=PALETTE["red_strong"],
                 bbox=dict(boxstyle="round,pad=0.25", facecolor="white", edgecolor=PALETTE["red_strong"], alpha=0.95))

    # 蓝绿色数值框：放置在红线 (88-142) 与蓝绿线 (23-34) 之间最宽阔真空区 (x=355, y=55)
    ax2.annotate("34.2k Context Switches/s\n(Bounded)", xy=(500, 34.2), xytext=(355, 55),
                 arrowprops=dict(facecolor=PALETTE["teal"], shrink=0.08, width=1.5, headwidth=6),
                 fontsize=9.5, fontweight="bold", color=PALETTE["teal"],
                 bbox=dict(boxstyle="round,pad=0.25", facecolor="white", edgecolor=PALETTE["teal"], alpha=0.95))

    ax2.set_xlabel("Concurrent Client Requests")
    ax2.set_ylabel("Context Switches ($10^3$ / sec)")
    ax2.set_title("(b) OS Context Switching Frequency")
    ax2.set_ylim(0, 220)
    ax2.legend(loc="upper left")

    fig.tight_layout(pad=1.5)

    # 导出到各个目标位置
    targets = [
        "/Users/achilles/Documents/许子祺/Agent/article/carrier_pinning_analysis.pdf",
        "/Users/achilles/Documents/许子祺/Agent/article/figures/carrier_pinning_analysis.pdf",
        "/Users/achilles/Documents/许子祺/Agent/docs/报告/figures/carrier_pinning_analysis.pdf",
        "/Users/achilles/Documents/许子祺/Agent/docs/报告/figures/carrier_pinning_analysis.png",
        "/Users/achilles/.gemini/antigravity/brain/5df56cc3-ce12-40c4-b191-0fb5383c4b77/scratch/carrier_pinning_preview.png"
    ]
    for t in targets:
        os.makedirs(os.path.dirname(t), exist_ok=True)
        if t.endswith(".pdf"):
            fig.savefig(t, format="pdf", dpi=300, bbox_inches="tight")
        else:
            fig.savefig(t, format="png", dpi=300, bbox_inches="tight")
        print(f"Exported: {t}")
    plt.close(fig)

if __name__ == "__main__":
    plot_carrier_pinning()
