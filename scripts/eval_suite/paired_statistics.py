#!/usr/bin/env python3
"""Non-parametric paired tests for the frozen 300-query evaluation."""
from __future__ import annotations

import argparse
import csv
import itertools
import json
import math
import statistics
from pathlib import Path
from typing import Any

import numpy as np
from scipy import stats


METRICS = (
    "recall@5",
    "recall@10",
    "mrr@10",
    "map@10",
    "ndcg@10",
    "hopRecall@10",
)


def load_rows(path: Path) -> list[dict[str, Any]]:
    with path.open(encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def grouped_rows(rows: list[dict[str, Any]]) -> dict[str, dict[str, dict[str, Any]]]:
    grouped: dict[str, dict[str, dict[str, Any]]] = {}
    for row in rows:
        query_id = str(row.get("queryId", row.get("query_id", "")))
        method = str(row["method"])
        if not query_id:
            raise ValueError("each result row must contain queryId")
        grouped.setdefault(query_id, {})[method] = row
    return grouped


def paired_values(grouped: dict[str, dict[str, dict[str, Any]]], left: str, right: str, metric: str) -> list[tuple[float, float]]:
    return [
        (float(methods[left][metric]), float(methods[right][metric]))
        for methods in grouped.values()
        if left in methods and right in methods
    ]


def rank_biserial(deltas: list[float]) -> float:
    nonzero = [delta for delta in deltas if delta != 0.0]
    if not nonzero:
        return 0.0
    ranks = stats.rankdata(np.abs(nonzero), method="average")
    positive = float(sum(rank for rank, delta in zip(ranks, nonzero) if delta > 0))
    negative = float(sum(rank for rank, delta in zip(ranks, nonzero) if delta < 0))
    return (positive - negative) / (positive + negative)


def wilcoxon_record(pairs: list[tuple[float, float]], left: str, right: str, metric: str) -> dict[str, Any]:
    deltas = [a - b for a, b in pairs]
    nonzero = [delta for delta in deltas if delta != 0.0]
    if nonzero:
        result = stats.wilcoxon(nonzero, zero_method="wilcox", alternative="two-sided", method="auto")
        statistic, pvalue = float(result.statistic), float(result.pvalue)
    else:
        statistic, pvalue = 0.0, 1.0
    return {
        "left": left,
        "right": right,
        "metric": metric,
        "n": len(deltas),
        "nNonzero": len(nonzero),
        "W": statistic,
        "pTwoSided": pvalue,
        "rankBiserial": rank_biserial(deltas),
        "medianDelta": statistics.median(deltas) if deltas else 0.0,
        "meanLeft": statistics.fmean(a for a, _ in pairs) if pairs else 0.0,
        "meanRight": statistics.fmean(b for _, b in pairs) if pairs else 0.0,
    }


def _mean_ranks(matrix: np.ndarray) -> list[float]:
    # Higher metric values are better, hence rank the negated values.
    ranked = [stats.rankdata(-row, method="average") for row in matrix]
    return [float(value) for value in np.mean(np.vstack(ranked), axis=0)]


def friedman_record(grouped: dict[str, dict[str, dict[str, Any]]], methods: list[str], metric: str, alpha: float) -> dict[str, Any]:
    complete = []
    for query_id in sorted(grouped):
        rows = grouped[query_id]
        if all(method in rows for method in methods):
            complete.append([float(rows[method][metric]) for method in methods])
    n, k = len(complete), len(methods)
    if n == 0 or k < 3:
        return {
            "metric": metric,
            "methods": methods,
            "n": n,
            "k": k,
            "chiSquare": None,
            "pTwoSided": None,
            "imanDavenportF": None,
            "imanDavenportP": None,
            "meanRanks": {},
            "criticalDistance": None,
            "nemenyiPairs": [],
        }
    matrix = np.asarray(complete, dtype=float)
    test = stats.friedmanchisquare(*[matrix[:, index] for index in range(k)])
    chi_square = float(test.statistic)
    p_value = float(test.pvalue)
    denominator = n * (k - 1) - chi_square
    iman_f = ((n - 1) * chi_square / denominator) if denominator > 0 else float("inf")
    iman_p = float(stats.f.sf(iman_f, k - 1, (k - 1) * (n - 1))) if math.isfinite(iman_f) else 0.0
    mean_ranks = dict(zip(methods, _mean_ranks(matrix)))
    q_alpha = float(stats.studentized_range.ppf(1.0 - alpha, k, np.inf) / math.sqrt(2.0))
    critical_distance = q_alpha * math.sqrt(k * (k + 1) / (6.0 * n))
    nemenyi_pairs = []
    for left, right in itertools.combinations(methods, 2):
        difference = abs(mean_ranks[left] - mean_ranks[right])
        nemenyi_pairs.append({
            "left": left,
            "right": right,
            "meanRankDifference": difference,
            "significant": difference > critical_distance,
        })
    return {
        "metric": metric,
        "methods": methods,
        "n": n,
        "k": k,
        "chiSquare": chi_square,
        "pTwoSided": p_value,
        "imanDavenportF": float(iman_f),
        "imanDavenportP": iman_p,
        "meanRanks": mean_ranks,
        "alpha": alpha,
        "criticalDistance": float(critical_distance),
        "nemenyiPairs": nemenyi_pairs,
    }


def analyse(rows: list[dict[str, Any]], alpha: float = 0.05, left: str | None = None, right: str | None = None) -> dict[str, Any]:
    grouped = grouped_rows(rows)
    methods = sorted({str(row["method"]) for row in rows})
    comparisons = list(itertools.combinations(methods, 2)) if left is None or right is None else [(left, right)]
    wilcoxon = []
    for comparison_left, comparison_right in comparisons:
        for metric in METRICS:
            pairs = paired_values(grouped, comparison_left, comparison_right, metric)
            if pairs:
                wilcoxon.append(wilcoxon_record(pairs, comparison_left, comparison_right, metric))
    friedman = [friedman_record(grouped, methods, metric, alpha) for metric in METRICS]
    return {
        "analysis": {
            "results": "eval_results_300.jsonl",
            "methods": methods,
            "metrics": list(METRICS),
            "alpha": alpha,
            "testDirection": "positive delta means left method is better",
        },
        "wilcoxonSignedRank": wilcoxon,
        "friedmanImanDavenport": friedman,
    }


def write_output(report: dict[str, Any], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.suffix.lower() == ".csv":
        rows = report["wilcoxonSignedRank"]
        fields = ["left", "right", "metric", "n", "nNonzero", "W", "pTwoSided", "rankBiserial", "medianDelta", "meanLeft", "meanRight"]
        with output.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fields)
            writer.writeheader()
            writer.writerows(rows)
    else:
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--alpha", type=float, default=0.05)
    parser.add_argument("--left")
    parser.add_argument("--right")
    args = parser.parse_args()
    if (args.left is None) != (args.right is None):
        parser.error("--left and --right must be supplied together")
    report = analyse(load_rows(args.results), args.alpha, args.left, args.right)
    write_output(report, args.output)
    print(args.output)


if __name__ == "__main__":
    main()
