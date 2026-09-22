#!/usr/bin/env python3
"""Run bounded-K sensitivity and parameterized Green-AI accounting.

The frozen runner stores only the top ten IDs, so K sensitivity is limited to
K in {1, 3, 5, 10}. Energy is a scenario estimate from recorded elapsed time; it is
not a power-meter measurement and contains no hosted-model or API cost.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
from pathlib import Path
from typing import Any

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_RESULTS = ROOT / "backend/tests/evidence/eval_results_300.jsonl"
DEFAULT_JSON = ROOT / "backend/tests/evidence/sensitivity_green_ai.json"
DEFAULT_CSV = ROOT / "backend/tests/evidence/sensitivity_green_ai.csv"
DEFAULT_FIGURE = ROOT / "article/figures/sensitivity_analysis"
DEFAULT_TABLE = ROOT / "article/generated_sensitivity_table.tex"
METHODS = (
    "KnowledgeHub_QuadPath",
    "Dense_Only",
    "BM25_Only",
    "Graph_Only",
    "Hybrid_Dual",
)
METHOD_LABELS = {
    "KnowledgeHub_QuadPath": "KH-QuadPath",
    "Dense_Only": "Dense-Only",
    "BM25_Only": "BM25-Only",
    "Graph_Only": "Graph-Only",
    "Hybrid_Dual": "Hybrid-Dual",
}
METHOD_COLORS = {
    "KnowledgeHub_QuadPath": "#0F4D92",
    "Dense_Only": "#777777",
    "BM25_Only": "#B64342",
    "Graph_Only": "#5A9E5A",
    "Hybrid_Dual": "#42949E",
}
K_VALUES = (1, 3, 5, 10)
POWER_W = 35.0
PUE = 1.20
CARBON_INTENSITY_G_PER_KWH = 442.0


def load_rows(path: Path) -> list[dict[str, Any]]:
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if len(rows) != 1500:
        raise ValueError(f"expected 1500 rows, found {len(rows)}")
    seen: set[tuple[str, str]] = set()
    for row in rows:
        if row.get("evaluationMode") != "offline_deterministic_retrieval":
            raise ValueError("sensitivity input contains a non-offline row")
        key = (str(row["queryId"]), str(row["method"]))
        if key in seen:
            raise ValueError(f"duplicate query/method row: {key}")
        seen.add(key)
        if len(row.get("rankedSegmentIds", [])) < max(K_VALUES):
            raise ValueError("top-ten rankedSegmentIds are required for K sensitivity")
    if {row["method"] for row in rows} != set(METHODS):
        raise ValueError("method set does not match frozen five-pipeline protocol")
    if len({row["queryId"] for row in rows}) != 300:
        raise ValueError("query count is not 300")
    return rows


def binary_metrics(row: dict[str, Any], k: int) -> dict[str, float]:
    ranked = [str(value) for value in row["rankedSegmentIds"][:k]]
    gold = {str(value) for value in row.get("goldSegmentIds", [])}
    if not gold:
        return {"recall": 0.0, "mrr": 0.0, "map": 0.0, "ndcg": 0.0, "hopRecall": 0.0}
    hits = [segment_id in gold for segment_id in ranked]
    hit_positions = [index + 1 for index, hit in enumerate(hits) if hit]
    recall = len(set(ranked) & gold) / len(gold)
    mrr = 1.0 / hit_positions[0] if hit_positions else 0.0
    ap = sum(sum(hits[:position]) / position for position in hit_positions) / min(len(gold), k)
    dcg = sum((1.0 if hit else 0.0) / math.log2(index + 2) for index, hit in enumerate(hits))
    ideal_hits = min(len(gold), k)
    ideal = sum(1.0 / math.log2(index + 2) for index in range(ideal_hits))
    ndcg = dcg / ideal if ideal else 0.0
    hop = float(gold.issubset(set(ranked)))
    return {"recall": recall, "mrr": mrr, "map": ap, "ndcg": ndcg, "hopRecall": hop}


def aggregate(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    output: list[dict[str, Any]] = []
    for method in METHODS:
        method_rows = [row for row in rows if row["method"] == method]
        for k in K_VALUES:
            values = [binary_metrics(row, k) for row in method_rows]
            output.append(
                {
                    "method": method,
                    "k": k,
                    **{
                        metric: float(np.mean([value[metric] for value in values]))
                        for metric in ("recall", "mrr", "map", "ndcg", "hopRecall")
                    },
                }
            )
    return output


def green_ai(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for method in METHODS:
        method_rows = [row for row in rows if row["method"] == method]
        total_seconds = sum(int(row["t_total"]) for row in method_rows) / 1e9
        energy_kwh = total_seconds * POWER_W * PUE / 3.6e6
        results.append(
            {
                "method": method,
                "queries": len(method_rows),
                "totalRecordedSeconds": total_seconds,
                "energyKWhScenario": energy_kwh,
                "co2eGramsScenario": energy_kwh * CARBON_INTENSITY_G_PER_KWH,
                "externalApiCalls": 0,
                "observedHostedApiCostUsd": 0.0,
            }
        )
    return results


def save_figure(records: list[dict[str, Any]], energy: list[dict[str, Any]], stem: Path) -> None:
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
        }
    )
    figure, axes = plt.subplots(2, 2, figsize=(7.4, 6.4))
    axes = axes.ravel()
    for axis, metric, label in ((axes[0], "recall", "Mean Recall@K"), (axes[1], "mrr", "Mean MRR@K")):
        for method in METHODS:
            subset = [record for record in records if record["method"] == method]
            axis.plot(
                [record["k"] for record in subset],
                [record[metric] for record in subset],
                marker="o",
                linewidth=2.0,
                markersize=4.8,
                color=METHOD_COLORS[method],
                label=METHOD_LABELS[method],
            )
        axis.set_xticks(K_VALUES)
        axis.set_ylim(0.0, 1.0)
        axis.set_xlabel("Cutoff K")
        axis.set_ylabel(label)
        axis.grid(axis="y", color="#D9D9D9", linewidth=0.6, alpha=0.75)
        axis.set_axisbelow(True)
    handles, labels = axes[0].get_legend_handles_labels()
    axes[3].axis("off")
    axes[3].legend(handles, labels, loc="upper left", fontsize=11)
    axes[3].text(0, 0.12, "Power: assumed 35 W\nPUE: assumed 1.20\nGrid: assumed 442 g/kWh\nNot metered energy", transform=axes[3].transAxes, fontsize=11)
    methods = [METHOD_LABELS[method] for method in METHODS]
    energy_values = [1000 * next(item["co2eGramsScenario"] for item in energy if item["method"] == method) for method in METHODS]
    bars = axes[2].bar(np.arange(len(METHODS)), energy_values, color=[METHOD_COLORS[method] for method in METHODS], edgecolor="#333333", linewidth=0.7)
    axes[2].set_xticks(np.arange(len(METHODS)), ["KH", "Hash", "BM25", "Meta", "Dual"], fontsize=10)
    axes[2].set_ylabel("Scenario CO$_2$e (mg / run)", fontsize=12)
    axes[2].grid(axis="y", color="#D9D9D9", linewidth=0.6, alpha=0.75)
    axes[2].set_axisbelow(True)
    for bar, value in zip(bars, energy_values):
        axes[2].text(bar.get_x() + bar.get_width() / 2.0, bar.get_height(), f"{value:.2f}", ha="center", va="bottom", fontsize=10)
    figure.tight_layout(pad=1.0)
    stem.parent.mkdir(parents=True, exist_ok=True)
    figure.savefig(stem.with_suffix(".pdf"), format="pdf", dpi=300, bbox_inches="tight", pad_inches=0.08)
    figure.savefig(stem.with_suffix(".svg"), format="svg", dpi=300, bbox_inches="tight", pad_inches=0.08)
    plt.close(figure)


def write_latex_table(records: list[dict[str, Any]], energy: list[dict[str, Any]], path: Path) -> None:
    lines = [
        "% AUTO-GENERATED from backend/tests/evidence/sensitivity_green_ai.json; do not edit manually.",
        r"\begin{table}[htbp]",
        r"\centering",
        r"\footnotesize",
        r"\caption{Cutoff sensitivity and scenario resource accounting.}",
        r"\label{tab:sensitivity-green-ai}",
        r"\begin{tabular}{@{}llrrrrr@{}}",
        r"\toprule",
        r"$K$ & Method & Recall & MRR & MAP & NDCG & HopRecall \\",
        r"\midrule",
    ]
    for record in records:
        lines.append(
            f"{record['k']} & {METHOD_LABELS[record['method']]} & {record['recall']:.3f} & "
            f"{record['mrr']:.3f} & {record['map']:.3f} & {record['ndcg']:.3f} & "
            f"{record['hopRecall']:.3f} \\\\" 
        )
    lines.extend(
        [
            r"\bottomrule",
            r"\end{tabular}",
            r"\begin{flushleft}\footnotesize\emph{Note.} Cutoffs use the stored rankings and binary relevance. These are descriptive scores over non-independent records. No alpha, graph-depth, chunk-size or RRF sweep is represented.\end{flushleft}",
            r"\end{table}",
            "",
            r"\begin{table}[htbp]",
            r"\centering",
            r"\scriptsize",
            r"\caption{Parameterized Green-AI accounting over the 300-query offline run.}",
            r"\label{tab:green-ai}",
            r"\begin{tabular}{@{}lrrr@{}}",
            r"\toprule",
            r"Method & Elapsed (s) & Model energy (mWh) & Model CO$_2$e (mg) \\",
            r"\midrule",
        ]
    )
    for item in energy:
        lines.append(
            f"{METHOD_LABELS[item['method']]} & {item['totalRecordedSeconds']:.3f} & "
            f"{item['energyKWhScenario'] * 1e6:.3f} & {item['co2eGramsScenario'] * 1000:.3f} \\\\" 
        )
    lines.extend(
        [
            r"\bottomrule",
            r"\end{tabular}",
            r"\end{table}",
            "",
        ]
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS)
    parser.add_argument("--json", type=Path, default=DEFAULT_JSON)
    parser.add_argument("--csv", type=Path, default=DEFAULT_CSV)
    parser.add_argument("--figure", type=Path, default=DEFAULT_FIGURE)
    parser.add_argument("--table", type=Path, default=DEFAULT_TABLE)
    args = parser.parse_args()
    rows = load_rows(args.results)
    sensitivity = aggregate(rows)
    energy = green_ai(rows)
    report = {
        "schemaVersion": "eswa.sensitivity_green_ai.v1",
        "source": str(args.results),
        "evaluationMode": "offline_deterministic_retrieval",
        "kValues": list(K_VALUES),
        "sensitivityMetricDefinition": "binary gold-segment metrics recomputed from stored top-ten rankings; NDCG is binary because qrel grades are not stored per result row",
        "sensitivity": sensitivity,
        "greenAiScenario": {
            "powerW": POWER_W,
            "pue": PUE,
            "carbonIntensityGPerKWh": CARBON_INTENSITY_G_PER_KWH,
            "formula": "energy_kWh = recorded_total_seconds * power_W * PUE / 3.6e6",
            "limitation": "scenario based on wall-clock elapsed time, not CPU time or measured power; excludes indexing and hosted services; not evidence of carbon or cost superiority",
            "externalApiCalls": 0,
            "hostedApiCost": "not estimated; no hosted calls were made",
            "methods": energy,
        },
    }
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.csv.parent.mkdir(parents=True, exist_ok=True)
    with args.csv.open("w", newline="", encoding="utf-8") as handle:
        fields = ["method", "k", "recall", "mrr", "map", "ndcg", "hopRecall"]
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(sensitivity)
    write_latex_table(sensitivity, energy, args.table)
    save_figure(sensitivity, energy, args.figure)
    print(args.json)
    print(args.csv)
    print(args.figure.with_suffix(".pdf"))
    print(args.table)


if __name__ == "__main__":
    main()
