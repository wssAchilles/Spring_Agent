#!/usr/bin/env python3
"""Run the frozen 300-query non-parametric statistical analysis.

The input is the per-query JSONL emitted by ``eval_runner_300.py``.  No
summary values are read or embedded in this script: all statistics are
computed from the paired rows so that the report remains auditable.
"""
from __future__ import annotations

import argparse
import hashlib
import itertools
import json
import math
import statistics
import warnings
from pathlib import Path
from typing import Any, Iterable

import numpy as np
from scipy import stats


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_RESULTS = ROOT / "backend/tests/evidence/eval_results_300.jsonl"
DEFAULT_OUTPUT = ROOT / "backend/tests/evidence/statistical_test_report.json"

METHODS = (
    "KnowledgeHub_QuadPath",
    "Dense_Only",
    "BM25_Only",
    "Graph_Only",
    "Hybrid_Dual",
)
METRICS = (
    "recall@5",
    "recall@10",
    "mrr@10",
    "map@10",
    "ndcg@10",
    "hopRecall@10",
)
TIMINGS = (
    "t_embed",
    "t_bm25",
    "t_dense",
    "t_graph",
    "t_rrf",
    "t_rerank",
    "t_total",
)
REQUIRED_FIELDS = {
    "schemaVersion",
    "evaluationMode",
    "method",
    "queryId",
    *METRICS,
    *TIMINGS,
}
EVALUATION_MODE = "offline_deterministic_retrieval"


def load_and_validate(path: Path) -> list[dict[str, Any]]:
    """Load JSONL and reject incomplete, duplicated, or mismatched results."""
    rows: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as handle:
        for line_number, raw in enumerate(handle, 1):
            if not raw.strip():
                continue
            try:
                row = json.loads(raw)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_number}: invalid JSON") from exc
            if not isinstance(row, dict):
                raise ValueError(f"{path}:{line_number}: result row is not an object")
            missing = REQUIRED_FIELDS - set(row)
            if missing:
                raise ValueError(f"{path}:{line_number}: missing fields {sorted(missing)}")
            if row["evaluationMode"] != EVALUATION_MODE:
                raise ValueError(
                    f"{path}:{line_number}: unsupported evaluationMode {row['evaluationMode']!r}"
                )
            if row["method"] not in METHODS:
                raise ValueError(f"{path}:{line_number}: unknown method {row['method']!r}")
            for metric in METRICS:
                try:
                    value = float(row[metric])
                except (TypeError, ValueError) as exc:
                    raise ValueError(f"{path}:{line_number}: {metric} is not numeric") from exc
                if not math.isfinite(value) or not 0.0 <= value <= 1.0:
                    raise ValueError(f"{path}:{line_number}: {metric} is outside [0, 1]")
            for timing in TIMINGS:
                try:
                    value = int(row[timing])
                except (TypeError, ValueError) as exc:
                    raise ValueError(f"{path}:{line_number}: {timing} is not an integer") from exc
                if value < 0:
                    raise ValueError(f"{path}:{line_number}: {timing} is negative")
            rows.append(row)

    if not rows:
        raise ValueError(f"{path}: no result rows")
    query_ids = {str(row["queryId"]) for row in rows}
    methods = {str(row["method"]) for row in rows}
    if methods != set(METHODS):
        raise ValueError(f"result method set is {sorted(methods)}, expected {list(METHODS)}")
    if len(query_ids) != 300:
        raise ValueError(f"result query count is {len(query_ids)}, expected 300")
    if len(rows) != len(query_ids) * len(METHODS):
        raise ValueError(f"result row count is {len(rows)}, expected {len(query_ids) * len(METHODS)}")
    seen: set[tuple[str, str]] = set()
    for row in rows:
        key = (str(row["queryId"]), str(row["method"]))
        if key in seen:
            raise ValueError(f"duplicate query/method row: {key}")
        seen.add(key)
    return rows


def grouped_rows(rows: Iterable[dict[str, Any]]) -> dict[str, dict[str, dict[str, Any]]]:
    grouped: dict[str, dict[str, dict[str, Any]]] = {}
    for row in rows:
        grouped.setdefault(str(row["queryId"]), {})[str(row["method"])] = row
    return grouped


def _paired_values(
    grouped: dict[str, dict[str, dict[str, Any]]],
    left: str,
    right: str,
    metric: str,
) -> list[tuple[float, float]]:
    values: list[tuple[float, float]] = []
    for query_id in sorted(grouped):
        methods = grouped[query_id]
        if left not in methods or right not in methods:
            continue
        values.append((float(methods[left][metric]), float(methods[right][metric])))
    return values


def _rank_biserial(deltas: np.ndarray) -> tuple[float, float, float]:
    """Return rank-biserial r and positive/negative rank sums."""
    nonzero = deltas[deltas != 0.0]
    if nonzero.size == 0:
        return 0.0, 0.0, 0.0
    ranks = stats.rankdata(np.abs(nonzero), method="average")
    positive = float(np.sum(ranks[nonzero > 0.0]))
    negative = float(np.sum(ranks[nonzero < 0.0]))
    denominator = positive + negative
    effect = (positive - negative) / denominator if denominator else 0.0
    return effect, positive, negative


def wilcoxon_record(
    pairs: list[tuple[float, float]], left: str, right: str, metric: str
) -> dict[str, Any]:
    deltas = np.asarray([left_value - right_value for left_value, right_value in pairs], dtype=float)
    nonzero = deltas[deltas != 0.0]
    rank_biserial, positive_ranks, negative_ranks = _rank_biserial(deltas)
    if nonzero.size:
        # Continuity correction applies when SciPy selects its asymptotic method.
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            result = stats.wilcoxon(
                nonzero,
                zero_method="wilcox",
                correction=True,
                alternative="two-sided",
                method="auto",
            )
        statistic = float(result.statistic)
        p_value = float(result.pvalue)
    else:
        statistic = 0.0
        p_value = 1.0
    return {
        "left": left,
        "right": right,
        "metric": metric,
        "n": int(deltas.size),
        "nNonzero": int(nonzero.size),
        "W": statistic,
        "WPlus": positive_ranks,
        "WMinus": negative_ranks,
        "pTwoSided": p_value,
        "rankBiserial": rank_biserial,
        "medianDelta": float(statistics.median(deltas.tolist())) if deltas.size else 0.0,
        "meanLeft": float(np.mean([left_value for left_value, _ in pairs])) if pairs else 0.0,
        "meanRight": float(np.mean([right_value for _, right_value in pairs])) if pairs else 0.0,
    }


def _mean_ranks(matrix: np.ndarray) -> np.ndarray:
    # Higher retrieval scores are better; rank 1 is therefore the best score.
    return np.mean(np.vstack([stats.rankdata(-row, method="average") for row in matrix]), axis=0)


def _maximal_cliques(methods: list[str], mean_ranks: dict[str, float], cd: float) -> list[list[str]]:
    """Find maximal non-significant Nemenyi cliques (k is only five here)."""
    valid: list[tuple[str, ...]] = []
    for size in range(2, len(methods) + 1):
        for subset in itertools.combinations(methods, size):
            ranks = [mean_ranks[name] for name in subset]
            if max(ranks) - min(ranks) <= cd + 1e-12:
                valid.append(subset)
    maximal = [subset for subset in valid if not any(set(subset) < set(other) for other in valid)]
    return [list(subset) for subset in maximal]


def friedman_record(
    grouped: dict[str, dict[str, dict[str, Any]]], metric: str, alpha: float
) -> dict[str, Any]:
    complete = [
        [float(grouped[query_id][method][metric]) for method in METHODS]
        for query_id in sorted(grouped)
        if all(method in grouped[query_id] for method in METHODS)
    ]
    n, k = len(complete), len(METHODS)
    if n == 0:
        raise ValueError(f"no complete query blocks for {metric}")
    matrix = np.asarray(complete, dtype=float)
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", RuntimeWarning)
        test = stats.friedmanchisquare(*[matrix[:, index] for index in range(k)])
    chi_square = float(test.statistic)
    p_value = float(test.pvalue)
    denominator = n * (k - 1) - chi_square
    iman_f = ((n - 1) * chi_square / denominator) if denominator > 0.0 else float("inf")
    iman_p = float(stats.f.sf(iman_f, k - 1, (k - 1) * (n - 1))) if math.isfinite(iman_f) else 0.0
    mean_rank_values = _mean_ranks(matrix)
    mean_ranks = {method: float(rank) for method, rank in zip(METHODS, mean_rank_values)}
    q_critical = float(stats.studentized_range.ppf(1.0 - alpha, k, np.inf) / math.sqrt(2.0))
    critical_distance = q_critical * math.sqrt(k * (k + 1) / (6.0 * n))
    pairwise = []
    for left, right in itertools.combinations(METHODS, 2):
        difference = abs(mean_ranks[left] - mean_ranks[right])
        pairwise.append(
            {
                "left": left,
                "right": right,
                "meanRankDifference": difference,
                "significant": bool(difference > critical_distance),
            }
        )
    return {
        "metric": metric,
        "n": n,
        "k": k,
        "chiSquare": chi_square,
        "pTwoSided": p_value,
        "imanDavenportF": float(iman_f),
        "imanDavenportDf1": k - 1,
        "imanDavenportDf2": (k - 1) * (n - 1),
        "imanDavenportP": iman_p,
        "alpha": alpha,
        "meanRanks": mean_ranks,
        "qCritical": q_critical,
        "criticalDistance": float(critical_distance),
        "nemenyiPairs": pairwise,
        "nonSignificantCliques": _maximal_cliques(list(METHODS), mean_ranks, critical_distance),
    }


def analyse(rows: list[dict[str, Any]], alpha: float = 0.05) -> dict[str, Any]:
    grouped = grouped_rows(rows)
    texts = {" ".join(str(next(iter(methods.values())).get("query", "")).casefold().split())
             for methods in grouped.values()}
    wilcoxon: list[dict[str, Any]] = []
    baseline = METHODS[0]
    for comparator in METHODS[1:]:
        for metric in METRICS:
            pairs = _paired_values(grouped, baseline, comparator, metric)
            if len(pairs) != len(grouped):
                raise ValueError(f"incomplete pair for {baseline} vs {comparator} on {metric}")
            wilcoxon.append(wilcoxon_record(pairs, baseline, comparator, metric))
    friedman = [friedman_record(grouped, metric, alpha) for metric in METRICS]
    return {
        "schemaVersion": "eswa.statistical_test_report.v1",
        "source": "backend/tests/evidence/eval_results_300.jsonl",
        "evaluationMode": EVALUATION_MODE,
        "nRows": len(rows),
        "nQueries": len(grouped),
        "uniqueQuestionTexts": len(texts),
        "interpretation": "EXPLORATORY_ONLY_NONINDEPENDENT_RECORDS",
        "limitations": [
            "Query IDs are not independent question texts; identical texts may carry conflicting gold sets.",
            "P-values are unadjusted and do not establish production-pipeline superiority.",
            "NDCG uses frozen evidence-position grades, not independently judged relevance grades.",
            "The Nemenyi CD uses 300 row blocks nominally; Demsar's independent-dataset design is not met.",
        ],
        "nMethods": len(METHODS),
        "methods": list(METHODS),
        "metrics": list(METRICS),
        "alpha": alpha,
        "testDirection": "positive delta means KnowledgeHub_QuadPath is better",
        "inputSchemaVersions": sorted({str(row["schemaVersion"]) for row in rows}),
        "runnerVersions": sorted({str(row.get("runnerVersion", "")) for row in rows}),
        "inputFingerprints": sorted({str(row.get("inputFingerprint", "")) for row in rows}),
        "methodology": {
            "wilcoxon": {
                "zeroMethod": "wilcox",
                "continuityCorrection": True,
                "alternative": "two-sided",
                "delta": "KnowledgeHub_QuadPath - comparator",
                "rankBiserial": "(positive rank sum - negative rank sum) / (positive rank sum + negative rank sum)",
            },
            "friedman": {
                "rankDirection": "higher metric value receives better (lower) rank",
                "imanDavenportDf": [len(METHODS) - 1, (len(METHODS) - 1) * (len(grouped) - 1)],
            },
            "nemenyi": {
                "qCritical": "studentized_range.ppf(1-alpha, k, infinity) / sqrt(2)",
                "criticalDistance": "qCritical * sqrt(k*(k+1)/(6*N))",
            },
        },
        "wilcoxonSignedRank": wilcoxon,
        "friedmanImanDavenport": friedman,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--alpha", type=float, default=0.05)
    args = parser.parse_args()
    if not 0.0 < args.alpha < 1.0:
        parser.error("--alpha must be between 0 and 1")
    rows = load_and_validate(args.results)
    report = analyse(rows, args.alpha)
    report["sourceSha256"] = hashlib.sha256(args.results.read_bytes()).hexdigest()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
