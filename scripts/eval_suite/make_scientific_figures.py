#!/usr/bin/env python3
"""Create publication-ready figures directly from v2 per-query JSONL results."""
from __future__ import annotations

import argparse
import csv
from pathlib import Path
from typing import Any

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch
import numpy as np

from statistical_tests_300 import (
    EVALUATION_MODE,
    METHODS,
    METRICS,
    analyse,
    load_and_validate,
)


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_RESULTS = ROOT / "backend/tests/evidence/eval_results_300_v2.jsonl"
DEFAULT_OUTPUT_DIR = ROOT / "article/figures"

PALETTE = {
    "blue_main": "#0F4D92",
    "blue_secondary": "#3775BA",
    "green_3": "#8BCF8B",
    "red_1": "#F6CFCB",
    "red_strong": "#B64342",
    "neutral": "#CFCECE",
    "teal": "#42949E",
}
METHOD_COLORS = {
    "KnowledgeHub_QuadPath": PALETTE["blue_main"],
    "Dense_Only": "#767676",
    "BM25_Only": PALETTE["red_strong"],
    "Graph_Only": PALETTE["green_3"],
    "Hybrid_Dual": PALETTE["teal"],
}
METHOD_LABELS = {
    "KnowledgeHub_QuadPath": "KnowledgeHub QuadPath",
    "Dense_Only": "Dense-Only",
    "BM25_Only": "BM25-Only",
    "Graph_Only": "Graph-Only",
    "Hybrid_Dual": "Hybrid-Dual",
}
METRIC_LABELS = {
    "recall@5": "Recall@5",
    "recall@10": "Recall@10",
    "mrr@10": "MRR@10",
    "map@10": "MAP@10",
    "ndcg@10": "NDCG@10",
    "hopRecall@10": "HopRecall@10",
}
HATCHES = ("", "//", "..", "\\\\", "xx")


def publication_style() -> None:
    plt.rcParams.update(
        {
            "font.family": ["Arial", "Helvetica", "DejaVu Sans", "sans-serif"],
            "font.size": 15,
            "axes.spines.top": False,
            "axes.spines.right": False,
            "axes.linewidth": 1.2,
            "legend.frameon": False,
            "svg.fonttype": "none",
            "pdf.fonttype": 42,
            "savefig.facecolor": "white",
            "axes.facecolor": "white",
        }
    )


def aggregate(rows: list[dict[str, Any]]) -> tuple[dict[str, dict[str, np.ndarray]], dict[str, dict[str, float]]]:
    values: dict[str, dict[str, np.ndarray]] = {
        method: {
            metric: np.asarray([float(row[metric]) for row in rows if row["method"] == method], dtype=float)
            for metric in METRICS
        }
        for method in METHODS
    }
    means: dict[str, dict[str, float]] = {method: {} for method in METHODS}
    for method in METHODS:
        for metric in METRICS:
            means[method][metric] = float(np.mean(values[method][metric]))
    return values, means


def save_pair(fig: plt.Figure, output_dir: Path, stem: str) -> tuple[Path, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    pdf = output_dir / f"{stem}.pdf"
    svg = output_dir / f"{stem}.svg"
    fig.tight_layout(pad=1.1)
    fig.savefig(pdf, format="pdf", dpi=300, bbox_inches="tight", pad_inches=0.08)
    fig.savefig(svg, format="svg", dpi=300, bbox_inches="tight", pad_inches=0.08)
    plt.close(fig)
    return pdf, svg


def performance_comparison(values: dict[str, dict[str, np.ndarray]], output_dir: Path) -> tuple[Path, Path]:
    """Plot grouped bars; metrics are categorical, so no connecting line is used."""
    figure, axis = plt.subplots(figsize=(7.4, 4.7))
    x = np.arange(len(METRICS), dtype=float)
    width = 0.15
    for method_index, method in enumerate(METHODS):
        means = [float(np.mean(values[method][metric])) for metric in METRICS]
        offset = (method_index - (len(METHODS) - 1) / 2.0) * width
        axis.bar(x + offset, means, width=width, color=METHOD_COLORS[method],
                 edgecolor="#333333", linewidth=0.55, label=METHOD_LABELS[method])
    axis.set_xticks(x, [METRIC_LABELS[metric] for metric in METRICS], rotation=18, ha="right", fontsize=11)
    axis.set_ylim(0.0, 1.0)
    axis.set_ylabel("Mean score")
    axis.set_xlabel("Retrieval metric")
    axis.grid(axis="y", color="#D9D9D9", linewidth=0.65, alpha=0.65)
    axis.set_axisbelow(True)
    axis.legend(loc="upper center", bbox_to_anchor=(0.5, 1.20), ncol=3, fontsize=11)
    return save_pair(figure, output_dir, "performance_comparison")


def ablation_comparison(values: dict[str, dict[str, np.ndarray]], output_dir: Path) -> tuple[Path, Path]:
    """Plot absolute per-metric scores for the full and reduced retrieval paths."""
    figure, axes = plt.subplots(3, 2, figsize=(7.2, 7.0), sharey=True)
    axes_flat = axes.ravel()
    x = np.arange(len(METHODS), dtype=float)
    for index, metric in enumerate(METRICS):
        axis = axes_flat[index]
        means: list[float] = []
        for method in METHODS:
            means.append(float(np.mean(values[method][metric])))
        bars = axis.bar(
            x,
            means,
            color=[METHOD_COLORS[method] for method in METHODS],
            edgecolor="#333333",
            linewidth=0.75,
            error_kw={"elinewidth": 0.8, "ecolor": "#333333"},
        )
        for bar, hatch, mean in zip(bars, HATCHES, means):
            bar.set_hatch(hatch)
            axis.text(
                bar.get_x() + bar.get_width() / 2.0,
                min(0.985, mean + 0.025),
                f"{mean:.3f}",
                ha="center",
                va="bottom",
                fontsize=9.5,
            )
        axis.set_title(METRIC_LABELS[metric], fontsize=12, pad=7)
        axis.set_xticks(x, ["KH", "Hash", "BM25", "Meta", "Dual"], fontsize=10)
        axis.set_ylim(0.0, 1.0)
        axis.grid(axis="y", color="#D9D9D9", linewidth=0.55, alpha=0.65)
        axis.set_axisbelow(True)
    axes_flat[0].set_ylabel("Mean score")
    axes_flat[2].set_ylabel("Mean score")
    axes_flat[4].set_ylabel("Mean score")
    return save_pair(figure, output_dir, "ablation_comparison")


def cd_diagram(report: dict[str, Any], output_dir: Path) -> tuple[Path, Path]:
    """Draw six Nemenyi critical-distance panels from the computed report."""
    figure, axes = plt.subplots(3, 2, figsize=(7.4, 7.4))
    axes_flat = axes.ravel()
    records = {record["metric"]: record for record in report["friedmanImanDavenport"]}
    for index, metric in enumerate(METRICS):
        axis = axes_flat[index]
        record = records[metric]
        mean_ranks = {str(method): float(value) for method, value in record["meanRanks"].items()}
        ordered = sorted(METHODS, key=lambda method: mean_ranks[method])
        ranks = {method: mean_ranks[method] for method in ordered}
        axis.plot([1, 5], [1.20, 1.20], color="#333333", linewidth=1)
        for tick in range(1, 6):
            axis.text(tick, 1.25, str(tick), ha="center", fontsize=10)
            axis.plot([tick, tick], [1.18, 1.22], color="#333333", linewidth=1)
        for method_index, method in enumerate(ordered):
            rank = ranks[method]
            y = 0.93 - method_index * 0.20
            axis.text(0.95, y, METHOD_LABELS[method], va="center", fontsize=11)
            axis.plot([2.65, rank, rank], [y, y, 1.17], color=METHOD_COLORS[method], linewidth=1.1)
            axis.scatter(rank, y, s=20, color=METHOD_COLORS[method], zorder=3)
            axis.text(5.04, y, f"{rank:.2f}", va="center", ha="right", fontsize=10)
        cliques = record.get("nonSignificantCliques", [])
        for level, clique in enumerate(cliques):
            positions = [ranks[method] for method in clique]
            y = 1.49 + level * 0.12
            axis.plot([min(positions), max(positions)], [y, y], color="#333333", linewidth=2.4)
        cd = float(record["criticalDistance"])
        axis.plot([1.1, 1.1 + cd], [1.78, 1.78], color="#333333", marker="|", linewidth=1.4)
        axis.text(1.1, 1.88, f"CD={cd:.3f}", fontsize=10)
        axis.set_xlim(0.85, 5.15)
        axis.set_ylim(0.0, 2.07)
        axis.set_title(METRIC_LABELS[metric], fontsize=12, pad=5)
        axis.axis("off")
    return save_pair(figure, output_dir, "cd_diagram")


def architecture(output_dir: Path) -> tuple[Path, Path]:
    """Industrial four-plane architecture with explicit evidence boundaries."""
    figure, axis = plt.subplots(figsize=(7.4, 5.8))
    axis.set(xlim=(0, 7.4), ylim=(0, 5.8))
    axis.axis("off")
    boxes = [
        (0.25, 4.55, 6.9, 0.85, "CONTROL PLANE", "Tenant scope | knowledge governance | HITL policy | DAG canvas", "#DDF3DE"),
        (0.25, 2.55, 3.25, 1.35, "RETRIEVAL ENGINE", "BM25 + dense + metadata + graph\nNeo4j 2-hop PPR | RRF | rerank\nJNI direct-buffer acceleration boundary", "#E3EDF7"),
        (3.90, 2.55, 3.25, 1.35, "AGENT PLANE", "DeepSeek generation | tool calls\nReActCycleGuard fingerprints\nstreaming backpressure and approval gate", "#E3EDF7"),
        (0.25, 0.70, 3.25, 1.25, "KNOWLEDGE STORE", "PostgreSQL / vector index\nlexical index | Neo4j graph\nworkspace and temporal predicates", "#F3F3F3"),
        (3.90, 0.70, 3.25, 1.25, "DURABLE WORKFLOW STORE", "JSON checkpoint snapshot\nstatus/version CAS update\ncompensation and resume state", "#F3F3F3"),
    ]
    for x, y, w, h, title, body, fill in boxes:
        axis.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.025", linewidth=1.3, edgecolor="#0F4D92", facecolor=fill))
        axis.text(x + w / 2, y + h - 0.23, title, ha="center", va="center", fontsize=12, weight="bold", color="#0F4D92")
        axis.text(x + w / 2, y + 0.39, body, ha="center", va="center", fontsize=10, linespacing=1.4)
    axis.annotate("", xy=(1.88, 3.92), xytext=(1.88, 4.55), arrowprops={"arrowstyle": "->", "lw": 1.5, "color": "#0F4D92"})
    axis.annotate("", xy=(5.53, 3.92), xytext=(5.53, 4.55), arrowprops={"arrowstyle": "->", "lw": 1.5, "color": "#0F4D92"})
    axis.annotate("", xy=(3.82, 3.22), xytext=(3.50, 3.22), arrowprops={"arrowstyle": "->", "lw": 1.5, "color": "#0F4D92"})
    axis.annotate("", xy=(1.88, 1.95), xytext=(1.88, 2.55), arrowprops={"arrowstyle": "->", "lw": 1.4, "color": "#0F4D92"})
    axis.annotate("", xy=(5.53, 1.95), xytext=(5.53, 2.55), arrowprops={"arrowstyle": "->", "lw": 1.4, "color": "#0F4D92"})
    axis.text(3.7, 0.25, "Offline benchmark: deterministic local proxies | production boundary: Java 21 / Rust / Neo4j / DeepSeek", ha="center", fontsize=8.7, color="#4D4D4D")
    return save_pair(figure, output_dir, "system_architecture")


def e2e_tradeoff(output_dir: Path, summary_path: Path) -> tuple[Path, Path]:
    """Latency--factuality bubble chart from the raw E2E summary CSV."""
    rows = list(csv.DictReader(summary_path.open(encoding="utf-8")))
    figure, axis = plt.subplots(figsize=(7.2, 4.8))
    short = {
        "No-RAG Baseline": "No-RAG",
        "Dense-Only RAG": "Dense",
        "BM25-Only RAG": "BM25",
        "KnowledgeHub QuadPath RAG": "QuadPath",
    }
    # 精确避让各气泡与帕累托前沿折线，杜绝标注文字与几何元素重叠
    offsets = {
        "No-RAG": ((0, 18), "center", "bottom"),
        "Dense": ((18, 0), "left", "center"),
        "QuadPath": ((-18, 0), "right", "center"),
        "BM25": ((0, 18), "center", "bottom"),
    }
    points = []
    for row in rows:
        group = row["group"]
        x = float(row["latencySeconds"])
        y = float(row["factual_correctness"])
        tokens = float(row["promptTokens"]) + float(row["completionTokens"]) + float(row["thinkingTokens"])
        points.append((x, y, group))
        size = 80.0 + tokens * 0.22
        axis.scatter(x, y, s=size, color=METHOD_COLORS.get({
            "No-RAG Baseline": "Dense_Only",
            "Dense-Only RAG": "Dense_Only",
            "BM25-Only RAG": "BM25_Only",
            "KnowledgeHub QuadPath RAG": "KnowledgeHub_QuadPath",
        }.get(group, "KnowledgeHub_QuadPath"), "#767676"), alpha=0.82, edgecolor="#333333", linewidth=0.8)
        lbl = short.get(group, group)
        xytext, ha, va = offsets.get(lbl, ((5, 5), "left", "bottom"))
        axis.annotate(lbl, (x, y), xytext=xytext, textcoords="offset points", fontsize=12, ha=ha, va=va)
    frontier = []
    best_y = -1.0
    for x, y, group in sorted(points, key=lambda item: (item[0], -item[1])):
        if y > best_y + 1e-12:
            frontier.append((x, y))
            best_y = y
    if len(frontier) > 1:
        axis.plot([p[0] for p in frontier], [p[1] for p in frontier], linestyle="--", linewidth=1.5, color="#B64342", marker="o", markersize=4, label="Pareto frontier")
    axis.set_xlabel("Mean end-to-end latency (s)")
    axis.set_ylabel("Judge factual correctness")
    axis.set_xlim(5.15, 7.65)
    axis.set_ylim(0.0, 0.68)
    axis.grid(color="#D9D9D9", linewidth=0.6, alpha=0.7)
    axis.set_axisbelow(True)
    axis.text(0.04, 0.94, "Bubble area ∝ prompt + completion + thinking tokens", transform=axis.transAxes, fontsize=10, color="#4D4D4D", va="top")
    axis.legend(loc="lower right", fontsize=11)
    return save_pair(figure, output_dir, "e2e_tradeoff")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--e2e-summary", type=Path, default=ROOT / "backend/tests/evidence/eval_e2e_summary_100.csv")
    args = parser.parse_args()
    publication_style()
    rows = load_and_validate(args.results)
    values, _means = aggregate(rows)
    report = analyse(rows)
    outputs = [
        architecture(args.output_dir),
        performance_comparison(values, args.output_dir),
        ablation_comparison(values, args.output_dir),
        cd_diagram(report, args.output_dir),
        e2e_tradeoff(args.output_dir, args.e2e_summary),
    ]
    for pdf, svg in outputs:
        print(pdf)
        print(svg)
    if any(row["evaluationMode"] != EVALUATION_MODE for row in rows):
        raise AssertionError("unexpected evaluation mode")


if __name__ == "__main__":
    main()
