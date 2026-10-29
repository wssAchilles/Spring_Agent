#!/usr/bin/env python3
"""Frozen, standard-library LongMemEval raw-session ranking proxy experiment."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import hashlib
import json
import math
import platform
import re
import statistics
import time
from collections import Counter
from pathlib import Path
from typing import Any


TOKEN_RE = re.compile(r"[a-z0-9]+")
DATE_FORMAT = "%Y/%m/%d (%a) %H:%M"

# @evidence[[data/external/longmemeval/longmemeval_s_cleaned.json]]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def tokenize(text: str) -> list[str]:
    return TOKEN_RE.findall(text.lower())


def session_text(session: Any) -> str:
    if isinstance(session, list):
        return "\n".join(
            str(message.get("content", ""))
            for message in session
            if isinstance(message, dict)
        )
    raise ValueError("Each haystack session must be a list of message objects")


def validate_candidate_alignment(question_id: str, ids: list[str], dates: list[str], sessions: list[Any], qrels: set[str]) -> None:
    if not ids or len(ids) != len(dates) or len(ids) != len(sessions):
        raise ValueError(f"Candidate identity/date/session alignment failed for question {question_id}")
    counts = Counter(ids)
    if not qrels or not qrels.issubset(counts) or any(counts[session_id] != 1 for session_id in qrels):
        raise ValueError(f"Qrel-to-candidate mapping failed for question {question_id}")


def bounded_relevance(raw_score: float) -> float:
    positive = max(0.0, raw_score)
    return positive / (1.0 + positive)


def bm25_relevance(query_terms: list[str], docs: list[list[str]], k1: float, b: float) -> list[float]:
    count = len(docs)
    if count == 0:
        return []
    lengths = [len(document) for document in docs]
    average_length = sum(lengths) / count
    document_frequency: Counter[str] = Counter()
    term_frequencies = [Counter(document) for document in docs]
    for frequencies in term_frequencies:
        document_frequency.update(frequencies.keys())

    scores = [0.0] * count
    if average_length == 0.0:
        return scores
    for term in sorted(set(query_terms)):
        df = document_frequency.get(term, 0)
        if df == 0:
            continue
        inverse_document_frequency = math.log((count - df + 0.5) / (df + 0.5))
        for index, frequencies in enumerate(term_frequencies):
            tf = frequencies.get(term, 0)
            if tf == 0:
                continue
            normalization = k1 * (1.0 - b + b * lengths[index] / average_length)
            scores[index] += inverse_document_frequency * (k1 + 1.0) * tf / (normalization + tf)
    return [bounded_relevance(score) for score in scores]


def retention(question_time: dt.datetime, session_time: dt.datetime, lambda0: float, eta: float) -> float:
    age_days = max(0.0, (question_time - session_time).total_seconds() / 86400.0)
    access_count = 0
    dynamic_lambda = lambda0 / (1.0 + eta * math.log1p(access_count))
    return math.exp(-dynamic_lambda * age_days)


def dominates(left: tuple[float, ...], right: tuple[float, ...]) -> bool:
    return all(a >= b for a, b in zip(left, right)) and any(a > b for a, b in zip(left, right))


def fronts(objectives: list[tuple[float, ...]]) -> list[list[int]]:
    remaining = set(range(len(objectives)))
    result: list[list[int]] = []
    while remaining:
        current = [
            index for index in sorted(remaining)
            if not any(dominates(objectives[other], objectives[index]) for other in remaining if other != index)
        ]
        result.append(current)
        remaining.difference_update(current)
    return result


def crowding(front: list[int], objectives: list[tuple[float, ...]]) -> dict[int, float]:
    distances = {index: 0.0 for index in front}
    if len(front) < 2:
        return distances
    dimensions = len(objectives[front[0]])
    for dimension in range(dimensions):
        ordered = sorted(front, key=lambda index: (objectives[index][dimension], index))
        minimum = objectives[ordered[0]][dimension]
        maximum = objectives[ordered[-1]][dimension]
        if maximum <= minimum:
            continue
        distances[ordered[0]] = math.inf
        distances[ordered[-1]] = math.inf
        for position in range(1, len(ordered) - 1):
            index = ordered[position]
            if not math.isinf(distances[index]):
                previous = objectives[ordered[position - 1]][dimension]
                following = objectives[ordered[position + 1]][dimension]
                distances[index] += (following - previous) / (maximum - minimum)
    return distances


def pareto_top_k(objectives: list[tuple[float, ...]], relevance: list[float], top_k: int) -> list[int]:
    if not objectives or top_k <= 0:
        return []
    ordered_result: list[int] = []
    for front in fronts(objectives):
        remaining = top_k - len(ordered_result)
        if remaining <= 0:
            break
        if len(front) <= remaining:
            ordered_result.extend(sorted(front, key=lambda index: (-relevance[index], index)))
        else:
            distances = crowding(front, objectives)
            ordered_result.extend(sorted(front, key=lambda index: (-distances[index], -relevance[index], index))[:remaining])
            break
    return ordered_result


def scalar_top_k(objectives: list[tuple[float, ...]], top_k: int) -> list[int]:
    recency_weight, importance_weight, relevance_weight = 0.25, 0.35, 0.40
    scores = [recency_weight * row[0] + importance_weight * row[2] + relevance_weight * row[1] for row in objectives]
    return sorted(range(len(scores)), key=lambda index: (-scores[index], index))[:top_k]


def query_metrics(ranked_ids: list[str], qrels: set[str], k: int) -> dict[str, float]:
    retrieved = ranked_ids[:k]
    hits = len(set(retrieved) & qrels)
    recall = hits / len(qrels) if qrels else 0.0
    precision = hits / k if k else 0.0
    ideal_count = min(len(qrels), k)
    seen: set[str] = set()
    dcg = 0.0
    for position, session_id in enumerate(retrieved):
        if session_id in qrels and session_id not in seen:
            dcg += 1.0 / math.log2(position + 2)
            seen.add(session_id)
    idcg = sum(1.0 / math.log2(position + 2) for position in range(ideal_count))
    return {"recall": recall, "precision": precision, "hit": float(hits > 0), "ndcg": dcg / idcg if idcg else 0.0}


def aggregate(rows: list[dict[str, Any]], method: str, cut: str, k: int) -> dict[str, Any]:
    recall_values = [row[f"{method}_recall_{cut}_{k}"] for row in rows]
    ndcg_values = [row[f"{method}_ndcg_{cut}_5"] for row in rows] if k == 5 else []
    precision_values = [row[f"{method}_precision_{cut}_{k}"] for row in rows]
    hit_values = [row[f"{method}_hit_{cut}_{k}"] for row in rows]
    retrieved_hits = sum(row[f"{method}_hits_{cut}_{k}"] for row in rows)
    # Keep unavailable future qrels in the sensitivity-analysis denominator.
    gold_count = sum(row["gold_count_full"] for row in rows)
    return {
        "macro_recall": statistics.fmean(recall_values) if recall_values else 0.0,
        "micro_recall": retrieved_hits / gold_count if gold_count else 0.0,
        "macro_ndcg": statistics.fmean(ndcg_values) if ndcg_values else None,
        "macro_precision": statistics.fmean(precision_values) if precision_values else 0.0,
        "hit_rate": statistics.fmean(hit_values) if hit_values else 0.0,
        "queries": len(rows),
        "qrel_labels": gold_count,
    }


def run(data_path: Path, contract_path: Path, output_dir: Path) -> dict[str, Any]:
    start = time.perf_counter()
    started_at = dt.datetime.now().astimezone().isoformat(timespec="seconds")
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    actual_data_hash = sha256(data_path)
    expected_data_hash = contract["data"]["sha256"]
    if actual_data_hash != expected_data_hash:
        raise ValueError(f"Input SHA-256 mismatch: expected {expected_data_hash}, got {actual_data_hash}")

    dataset = json.loads(data_path.read_text(encoding="utf-8"))
    if len(dataset) != contract["data"]["questions"]:
        raise ValueError(f"Expected {contract['data']['questions']} questions, found {len(dataset)}")

    feature_cfg = contract["candidate_features"]
    bm25_cfg = feature_cfg["lexical_relevance_proxy"]
    retention_cfg = feature_cfg["retention"]
    importance = float(feature_cfg["importance"]["value"])
    top_ks = contract["methods"]["pareto"]["top_k"]
    max_k = max(top_ks)
    records: list[dict[str, Any]] = []
    full_rows: list[dict[str, Any]] = []
    cutoff_rows: list[dict[str, Any]] = []
    qrel_count = 0
    future_qrel_count = 0
    future_qrel_question_count = 0
    shared_sessions: Counter[str] = Counter()
    candidate_duplicate_occurrences = 0
    questions_with_duplicate_candidates = 0
    candidate_position_occurrences = 0
    parent = list(range(len(dataset)))
    first_seen_question: dict[str, int] = {}

    def find_component(question_index: int) -> int:
        while parent[question_index] != question_index:
            parent[question_index] = parent[parent[question_index]]
            question_index = parent[question_index]
        return question_index

    def union_components(left: int, right: int) -> None:
        left_root = find_component(left)
        right_root = find_component(right)
        if left_root != right_root:
            parent[right_root] = left_root

    for question_index, question in enumerate(dataset):
        question_id = str(question["question_id"])
        question_time = dt.datetime.strptime(question["question_date"], DATE_FORMAT)
        ids = question["haystack_session_ids"]
        dates = question["haystack_dates"]
        sessions = question["haystack_sessions"]
        qrels = set(question["answer_session_ids"])
        validate_candidate_alignment(question_id, ids, dates, sessions, qrels)
        qrel_count += len(qrels)
        session_times = [dt.datetime.strptime(value, DATE_FORMAT) for value in dates]
        qrel_future = {session_id for session_id, timestamp in zip(ids, session_times) if session_id in qrels and timestamp > question_time}
        future_qrel_count += len(qrel_future)
        future_qrel_question_count += int(bool(qrel_future))
        unique_ids = set(ids)
        shared_sessions.update(unique_ids)
        candidate_position_occurrences += len(ids)
        candidate_duplicate_occurrences += len(ids) - len(unique_ids)
        questions_with_duplicate_candidates += int(len(ids) != len(unique_ids))
        for session_id in unique_ids:
            if session_id in first_seen_question:
                union_components(question_index, first_seen_question[session_id])
            else:
                first_seen_question[session_id] = question_index

        query_terms = tokenize(str(question["question"]))
        documents = [tokenize(session_text(session)) for session in sessions]

        for cut, candidate_indices, target in (
            ("full", list(range(len(ids))), full_rows),
            ("asof", [i for i, timestamp in enumerate(session_times) if timestamp <= question_time], cutoff_rows),
        ):
            selected_docs = [documents[i] for i in candidate_indices]
            relevance = bm25_relevance(query_terms, selected_docs, float(bm25_cfg["k1"]), float(bm25_cfg["b"]))
            recencies = [retention(question_time, session_times[i], float(retention_cfg["lambda0"]), float(retention_cfg["eta"])) for i in candidate_indices]
            objectives = [(recencies[i], relevance[i], importance) for i in range(len(candidate_indices))]
            pareto_indices = pareto_top_k(objectives, relevance, max_k)
            scalar_indices = scalar_top_k(objectives, max_k)
            selected_ids = [ids[i] for i in candidate_indices]
            row: dict[str, Any] = {
                "question_id": question_id,
                "question_type": question["question_type"],
                "candidate_count": len(candidate_indices),
                "gold_count_full": len(qrels),
                "gold_count_asof": sum(1 for session_id in qrels if session_id in set(selected_ids)),
                "future_gold_count": len(qrel_future),
            }
            for method, order in (("pareto", pareto_indices), ("weighted", scalar_indices)):
                ranked_ids = [selected_ids[index] for index in order]
                for k in top_ks:
                    metrics = query_metrics(ranked_ids, qrels, int(k))
                    row[f"{method}_recall_{cut}_{k}"] = metrics["recall"]
                    row[f"{method}_precision_{cut}_{k}"] = metrics["precision"]
                    row[f"{method}_hit_{cut}_{k}"] = metrics["hit"]
                    row[f"{method}_ndcg_{cut}_{k}"] = metrics["ndcg"]
                    row[f"{method}_hits_{cut}_{k}"] = len(set(ranked_ids[: int(k)]) & qrels)
            target.append(row)
        records.append({
            "question_id": question_id,
            "question_type": question["question_type"],
            "candidate_count": len(ids),
            "gold_count": len(qrels),
            "gold_count_asof": sum(1 for session_id in qrels if session_id in {ids[i] for i, timestamp in enumerate(session_times) if timestamp <= question_time}),
            "future_gold_count": len(qrel_future),
        })

    if qrel_count != contract["data"]["qrel_labels"]:
        raise ValueError(f"Expected {contract['data']['qrel_labels']} qrel labels, found {qrel_count}")
    all_session_occurrences = candidate_position_occurrences
    shared_id_count = sum(1 for count in shared_sessions.values() if count > 1)

    summaries: dict[str, Any] = {}
    for cut, rows in (("full", full_rows), ("asof", cutoff_rows)):
        summaries[cut] = {}
        for method in ("pareto", "weighted"):
            summaries[cut][method] = {str(k): aggregate(rows, method, cut, int(k)) for k in top_ks}
        summaries[cut]["paired_recall5"] = {
            "mean_delta_pareto_minus_weighted": statistics.fmean(
                row[f"pareto_recall_{cut}_5"] - row[f"weighted_recall_{cut}_5"] for row in rows
            ) if rows else 0.0,
            "pareto_wins": sum(row[f"pareto_recall_{cut}_5"] > row[f"weighted_recall_{cut}_5"] for row in rows),
            "ties": sum(row[f"pareto_recall_{cut}_5"] == row[f"weighted_recall_{cut}_5"] for row in rows),
            "weighted_wins": sum(row[f"pareto_recall_{cut}_5"] < row[f"weighted_recall_{cut}_5"] for row in rows),
        }
        by_type: dict[str, Any] = {}
        for question_type in sorted({row["question_type"] for row in rows}):
            subset = [row for row in rows if row["question_type"] == question_type]
            by_type[question_type] = {
                method: {
                    str(k): aggregate(subset, method, cut, int(k))["macro_recall"]
                    for k in top_ks
                }
                for method in ("pareto", "weighted")
            }
        summaries[cut]["macro_recall_by_question_type"] = by_type

    asof_gold_available = sum(row["gold_count_asof"] for row in records)
    metrics = {
        "dataset": {
            "questions": len(dataset),
            "qrel_labels": qrel_count,
            "unique_session_ids": len(shared_sessions),
            "session_occurrences": all_session_occurrences,
            "session_question_pairs": sum(shared_sessions.values()),
            "session_ids_shared_across_questions": shared_id_count,
            "max_questions_per_session": max(shared_sessions.values(), default=0),
            "candidate_duplicate_occurrences": candidate_duplicate_occurrences,
            "questions_with_duplicate_candidate_ids": questions_with_duplicate_candidates,
            "question_overlap_components": len({find_component(index) for index in range(len(dataset))}),
            "questions_with_future_dated_candidates": sum(
                any(dt.datetime.strptime(value, DATE_FORMAT) > dt.datetime.strptime(question["question_date"], DATE_FORMAT)
                    for value in question["haystack_dates"])
                for question in dataset
            ),
            "future_dated_qrel_labels": future_qrel_count,
            "questions_with_future_dated_qrel": future_qrel_question_count,
            "questions_with_no_asof_gold": sum(row["gold_count_asof"] == 0 for row in records),
            "asof_available_qrel_labels": asof_gold_available,
            "asof_micro_recall_ceiling_at_infinite_k": asof_gold_available / qrel_count if qrel_count else 0.0,
            "sha256": actual_data_hash,
        },
        "results": summaries,
        "interpretation": {
            "primary": "Full-haystack raw-session reranking using a BM25 lexical proxy; descriptive only.",
            "asof_sensitivity": "Strict question_date cutoff with original qrels retained; future-dated labels count as misses.",
            "prohibited_claims": ["Qwen/cosine retrieval gain", "production compressed-memory gain", "answer-quality gain", "forgetting benefit", "statistical significance", "production latency"],
        },
    }

    output_dir.mkdir(parents=True, exist_ok=True)
    query_path = output_dir / "per_question_metrics.csv"
    fieldnames = list(full_rows[0].keys())
    with query_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fieldnames)
        writer.writeheader()
        for row in full_rows:
            writer.writerow(row)
    sensitivity_path = output_dir / "per_question_asof_metrics.csv"
    fieldnames = list(cutoff_rows[0].keys())
    with sensitivity_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fieldnames)
        writer.writeheader()
        for row in cutoff_rows:
            writer.writerow(row)

    elapsed = time.perf_counter() - start
    ended_at = dt.datetime.now().astimezone().isoformat(timespec="seconds")
    script_path = Path(__file__).resolve()
    metadata = {
        "experiment_id": contract["experiment_id"],
        "started_at": started_at,
        "finished_at": ended_at,
        "elapsed_seconds": elapsed,
        "python_version": platform.python_version(),
        "platform": platform.platform(),
        "command": f"python3 {script_path.relative_to(Path.cwd()) if script_path.is_relative_to(Path.cwd()) else script_path} --data {data_path} --contract {contract_path} --output {output_dir}",
        "data_path": str(data_path),
        "data_sha256": actual_data_hash,
        "contract_sha256": sha256(contract_path),
        "script_path": str(script_path),
        "script_sha256": sha256(script_path),
        "output_files": [query_path.name, sensitivity_path.name, "summary.json"],
        "network_calls": 0,
        "paid_api_calls": 0,
        "source_data_modified": False,
    }
    result = {"metadata": metadata, **metrics}
    (output_dir / "summary.json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return result


def self_test() -> None:
    assert tokenize("Hello, 2024! HELLO.") == ["hello", "2024", "hello"]
    validate_candidate_alignment("duplicate-distractor", ["d", "d", "g"], ["t1", "t2", "t3"], [[], [], []], {"g"})
    try:
        validate_candidate_alignment("duplicate-qrel", ["g", "g"], ["t1", "t2"], [[], []], {"g"})
    except ValueError:
        pass
    else:
        raise AssertionError("Repeated qrel candidate identity must be rejected")
    values = bm25_relevance(["needle"], [["needle", "other"], ["other"], ["other"]], 2.0, 0.75)
    assert len(values) == 3 and all(0.0 <= value <= 1.0 for value in values)
    average_length = 4.0 / 3.0
    normalization = 2.0 * (1.0 - 0.75 + 0.75 * 2.0 / average_length)
    expected_score = bounded_relevance(math.log((3.0 - 1.0 + 0.5) / (1.0 + 0.5)) * (2.0 + 1.0) / (normalization + 1.0))
    assert math.isclose(values[0], expected_score, rel_tol=1e-12, abs_tol=1e-12)
    assert values[0] > values[1]
    objectives = [(1.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.5, 0.5, 0.0)]
    assert len(fronts(objectives)[0]) == 3
    assert pareto_top_k(objectives, [0.0, 1.0, 0.5], 2) == [1, 0]
    assert scalar_top_k(objectives, 2) == [1, 2]
    assert query_metrics(["g", "x"], {"g"}, 2)["recall"] == 1.0
    assert query_metrics(["g", "g", "x"], {"g"}, 3)["recall"] == 1.0
    assert query_metrics(["g", "g", "x"], {"g"}, 3)["precision"] == 1.0 / 3.0
    asof = aggregate([{
        "pareto_recall_asof_5": 0.5,
        "pareto_ndcg_asof_5": 1.0,
        "pareto_precision_asof_5": 0.2,
        "pareto_hit_asof_5": 1.0,
        "pareto_hits_asof_5": 1,
        "gold_count_full": 2,
        "gold_count_asof": 1,
    }], "pareto", "asof", 5)
    assert asof["micro_recall"] == 0.5
    print("SELF_TESTS_PASSED=10")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", required=True, type=Path)
    parser.add_argument("--contract", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        if not args.data.exists():
            return
    result = run(args.data, args.contract, args.output)
    print(json.dumps({"summary_path": str(args.output / "summary.json"), "dataset": result["dataset"], "primary_recall5": result["results"]["full"]["pareto"]["5"], "baseline_recall5": result["results"]["full"]["weighted"]["5"], "paired": result["results"]["full"]["paired_recall5"]}, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
