#!/usr/bin/env python3
"""Generate editable LaTeX tables from the frozen statistical report."""
from __future__ import annotations

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_INPUT = ROOT / "backend/tests/evidence/statistical_test_report.json"
DEFAULT_OUTPUT = ROOT / "article/generated_statistics_tables.tex"

METHOD_LABELS = {
    "Dense_Only": "Dense-Only",
    "BM25_Only": "BM25-Only",
    "Graph_Only": "Graph-Only",
    "Hybrid_Dual": "Hybrid-Dual",
}
METRIC_LABELS = {
    "recall@5": "R@5",
    "recall@10": "R@10",
    "mrr@10": "MRR@10",
    "map@10": "MAP@10",
    "ndcg@10": "NDCG@10",
    "hopRecall@10": "HopR@10",
}


def build(report: dict) -> str:
    wilcoxon = report["wilcoxonSignedRank"]
    friedman = {record["metric"]: record for record in report["friedmanImanDavenport"]}
    lines = [
        "% AUTO-GENERATED from the selected statistical_test_report JSON; do not edit manually.",
        r"\begin{table}[htbp]",
        r"\centering",
        r"\scriptsize",
        r"\caption{Exploratory Wilcoxon calculations on the v2 retrieval proxy run: KH proxy minus Dense/BM25.}",
        r"\label{tab:wilcoxon-results}",
        r"\begin{tabular}{@{}llrrrr@{}}",
        r"\toprule",
        r"Comparator & Metric & $W$ & $p$ & $r_{\mathrm{rb}}$ & $\widetilde{\Delta}$ \\",
        r"\midrule",
    ]
    for comparator in ("Dense_Only", "BM25_Only", "Graph_Only", "Hybrid_Dual"):
        if comparator == "Graph_Only":
            lines.extend([
                r"\bottomrule", r"\end{tabular}",
                r"\begin{flushleft}\footnotesize These unadjusted $p$ values describe the declared v2 offline proxy experiment and are not production-pipeline evidence.\end{flushleft}",
                r"\end{table}", r"\begin{table}[htbp]", r"\centering", r"\scriptsize",
                r"\caption{Exploratory Wilcoxon calculations on the v2 retrieval proxy run: KH proxy minus Graph/Hybrid.}",
                r"\label{tab:wilcoxon-results-2}",
                r"\begin{tabular}{@{}llrrrr@{}}", r"\toprule",
                r"Comparator & Metric & $W$ & $p$ & $r_{\mathrm{rb}}$ & $\widetilde{\Delta}$ \\",
                r"\midrule",
            ])
        records = [
            record
            for record in wilcoxon
            if record["right"] == comparator
        ]
        for record in records:
            p_value = record["pTwoSided"]
            p_text = f"{p_value:.3g}" if p_value >= 0.001 else f"{p_value:.2e}"
            lines.append(
                f"{METHOD_LABELS[comparator]} & {METRIC_LABELS[record['metric']]} & "
                f"{record['W']:.1f} & {p_text} & {record['rankBiserial']:.3f} & "
                f"{record['medianDelta']:.3f} \\\\" 
            )
    lines.extend(
        [
            r"\bottomrule",
            r"\end{tabular}",
            r"\begin{flushleft}\footnotesize\emph{Note.} $p$ values are two-sided and unadjusted; $r_{\mathrm{rb}}$ is rank-biserial effect size. Positive differences favor KH proxy. Calculations retain all v2 query blocks for audit.\end{flushleft}",
            r"\end{table}",
            r"\begin{table}[htbp]",
            r"\centering",
            r"\scriptsize",
            r"\caption{Exploratory Friedman and Iman--Davenport calculations over the five v2 retrieval proxies.}",
            r"\label{tab:friedman-results}",
            r"\resizebox{\linewidth}{!}{%",
            r"\begin{tabular}{@{}lrrrrrr@{}}",
            r"\toprule",
            r"Metric & $\chi_F^2$ & $p_F$ & $F_F$ & $p_{ID}$ & CD & Mean ranks (KH, D, B, G, H) \\",
            r"\midrule",
        ]
    )
    for metric, record in friedman.items():
        ranks = record["meanRanks"]
        rank_text = ", ".join(
            f"{ranks[name]:.2f}" for name in (
                "KnowledgeHub_QuadPath",
                "Dense_Only",
                "BM25_Only",
                "Graph_Only",
                "Hybrid_Dual",
            )
        )
        p_f = record["pTwoSided"]
        p_id = record["imanDavenportP"]
        p_f_text = f"{p_f:.2e}" if p_f < 0.001 else f"{p_f:.3g}"
        p_id_text = f"{p_id:.2e}" if p_id < 0.001 else f"{p_id:.3g}"
        lines.append(
            f"{METRIC_LABELS[metric]} & {record['chiSquare']:.2f} & {p_f_text} & "
            f"{record['imanDavenportF']:.2f} & {p_id_text} & {record['criticalDistance']:.3f} & "
            f"{rank_text} \\\\" 
        )
    lines.extend(
        [
            r"\bottomrule",
            r"\end{tabular}}",
            r"\begin{flushleft}\footnotesize\emph{Note.} $D$, $B$, $G$, and $H$ denote Dense-Only, BM25-Only, Graph-Only, and Hybrid-Dual. Lower rank is better. The nominal Nemenyi CD uses $\alpha=0.05$ and 300 query blocks; the inference remains limited to the declared offline protocol.\end{flushleft}",
            r"\end{table}",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    report = json.loads(args.input.read_text(encoding="utf-8"))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(build(report), encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
