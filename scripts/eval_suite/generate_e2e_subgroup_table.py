#!/usr/bin/env python3
"""Generate subgroup answer-quality tables from the 100-question raw trace."""
from __future__ import annotations

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path
from statistics import fmean

ROOT = Path(__file__).resolve().parents[2]
GROUPS = ("No-RAG Baseline", "Dense-Only RAG", "BM25-Only RAG", "KnowledgeHub QuadPath RAG")
ORDER = (
    ("inference", "MultiHop inference"),
    ("comparison", "MultiHop comparison"),
    ("hotpot-hard-multi-hop", "Hotpot hard distractor"),
    ("questanswer_2docs-multi-document", "Two-document"),
    ("questanswer_3docs-multi-document", "Three-document"),
    ("enterprise-core-configuration", "Enterprise configuration"),
    ("enterprise-component-topology", "Enterprise topology"),
    ("enterprise-failure-recovery", "Enterprise recovery"),
)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=ROOT / "backend/tests/evidence/eval_e2e_results_100.jsonl")
    parser.add_argument("--output-csv", type=Path, default=ROOT / "backend/tests/evidence/eval_e2e_subgroup_100.csv")
    parser.add_argument("--output-tex", type=Path, default=ROOT / "article/generated_e2e_subgroup_table.tex")
    args = parser.parse_args()
    rows = [json.loads(line) for line in args.input.read_text(encoding="utf-8").splitlines() if line.strip()]
    grouped: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for row in rows:
        if row.get("status") == "OK":
            grouped[(str(row["reasoningType"]), str(row["group"]))].append(row)
    records = []
    for typ, label in ORDER:
        for group in GROUPS:
            values = grouped[(typ, group)]
            if not values:
                continue
            records.append({
                "reasoning_type": typ,
                "label": label,
                "group": group,
                "n": len(values),
                "faithfulness": fmean(v["judgeScores"]["faithfulness"] for v in values),
                "relevance": fmean(v["judgeScores"]["relevance"] for v in values),
                "factual_correctness": fmean(v["judgeScores"]["factual_correctness"] for v in values),
                "rougeL": fmean(v["rougeL"] for v in values),
                "tokenF1": fmean(v["tokenF1"] for v in values),
                "latencySeconds": fmean(v["latencySeconds"] for v in values),
            })
    args.output_csv.parent.mkdir(parents=True, exist_ok=True)
    with args.output_csv.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(records[0]))
        writer.writeheader(); writer.writerows(records)
    by_type = {(r["reasoning_type"], r["group"]): r for r in records}
    lines = [
        "% Auto-generated from eval_e2e_results_100.jsonl; no subgroup metric is entered manually.",
        r"\begin{table}[htbp]", r"\centering", r"\scriptsize",
        r"\caption{End-to-end factual correctness by reasoning type (100-question stratified sample).}",
        r"\label{tab:e2e-subgroups}", r"\resizebox{\linewidth}{!}{%", r"\begin{tabular}{lrrrrrr}", r"\toprule",
        r"Reasoning type & $n$ & No-RAG & Dense & BM25 & QuadPath & QuadPath--BM25 \\", r"\midrule",
    ]
    for typ, label in ORDER:
        vals = [by_type[(typ, group)]["factual_correctness"] for group in GROUPS]
        delta = vals[3] - vals[2]
        lines.append(f"{label} & {by_type[(typ, GROUPS[0])]['n']} & " + " & ".join(f"{v:.3f}" for v in vals) + f" & {delta:+.3f} " + r"\\")
    lines += [r"\bottomrule", r"\end{tabular}}", r"\begin{flushleft}\scriptsize", r"The subgroup table is descriptive and uses the same judge protocol as Table~\ref{tab:e2e-generation-100}. Positive differences in the final column favor QuadPath. The enterprise rows are repository-source questions, while the public rows are dataset-derived.", r"\end{flushleft}", r"\end{table}", ""]
    args.output_tex.parent.mkdir(parents=True, exist_ok=True)
    args.output_tex.write_text("\n".join(lines), encoding="utf-8")
    print(args.output_csv)
    print(args.output_tex)


if __name__ == "__main__":
    main()
