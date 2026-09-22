#!/usr/bin/env python3
"""Reproducible lexical evaluation for the candidate10 frozen fixture."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
from collections import defaultdict
from pathlib import Path
from typing import Iterable

TOKEN_RE = re.compile(r"[\w]+", re.UNICODE)


def load_jsonl(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def load_qrels(path: Path) -> dict[str, dict[str, int]]:
    result: dict[str, dict[str, int]] = defaultdict(dict)
    with path.open(encoding="utf-8") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        for row in reader:
            result[row["queryId"]][row["segmentId"]] = int(row["grade"])
    return dict(result)


def tokens(text: str) -> list[str]:
    return [token.casefold() for token in TOKEN_RE.findall(text)]


def rank(query: str, corpus: Iterable[dict], method: str) -> list[tuple[str, float]]:
    query_tokens = tokens(query)
    query_set = set(query_tokens)
    scored = []
    for item in corpus:
        doc_tokens = tokens(item.get("content", ""))
        doc_set = set(doc_tokens)
        if method == "token_overlap":
            score = sum(doc_tokens.count(token) for token in query_set)
        elif method == "exact_token_overlap":
            score = float(len(query_set & doc_set))
        else:
            raise ValueError(f"unknown method: {method}")
        scored.append((item["segmentId"], score))
    return sorted(scored, key=lambda pair: (-pair[1], int(pair[0])))


def dcg(grades: list[int]) -> float:
    return sum((2**grade - 1) / math.log2(index + 2) for index, grade in enumerate(grades))


def metrics(ranked: list[tuple[str, float]], relevant: dict[str, int], k: int = 10) -> dict[str, float | int]:
    top = ranked[:k]
    retrieved = [segment_id for segment_id, _ in top]
    relevant_ids = {segment_id for segment_id, grade in relevant.items() if grade > 0}
    hits = [segment_id for segment_id in retrieved if segment_id in relevant_ids]
    recall = len(set(hits)) / len(relevant_ids) if relevant_ids else 0.0
    reciprocal = next((1.0 / (index + 1) for index, segment_id in enumerate(retrieved) if segment_id in relevant_ids), 0.0)
    grades = [relevant.get(segment_id, 0) for segment_id in retrieved]
    ideal = sorted(relevant.values(), reverse=True)[:k]
    ndcg = dcg(grades) / dcg(ideal) if ideal and dcg(ideal) else 0.0
    return {"recall": recall, "mrr": reciprocal, "ndcg": ndcg, "hits": len(hits)}


def hop_recall(query: dict, ranked: list[tuple[str, float]], corpus_by_id: dict[str, dict], relevant: dict[str, int], k: int) -> float:
    """Recall of distinct family IDs represented by retrieved relevant segments."""
    relevant_families = {
        corpus_by_id[sid].get("metadata", {}).get("familyId")
        for sid, grade in relevant.items()
        if grade > 0 and sid in corpus_by_id
    }
    relevant_families.discard(None)
    if not relevant_families:
        return 0.0
    retrieved_families = {
        corpus_by_id[sid].get("metadata", {}).get("familyId")
        for sid, _ in ranked[:k]
        if sid in relevant and relevant[sid] > 0 and sid in corpus_by_id
    }
    return len(relevant_families & retrieved_families) / len(relevant_families)


def evaluate(fixture: Path, output: Path, methods: list[str], k: int) -> tuple[Path, Path]:
    queries = load_jsonl(fixture / "queries.jsonl")
    corpus = load_jsonl(fixture / "corpus.jsonl")
    qrels = load_qrels(fixture / "qrels.tsv")
    corpus_by_id = {item["segmentId"]: item for item in corpus}
    output.mkdir(parents=True, exist_ok=True)
    fingerprint = hashlib.sha256(
        b"".join(path.read_bytes() for path in (fixture / "queries.jsonl", fixture / "corpus.jsonl", fixture / "qrels.tsv"))
    ).hexdigest()
    rows = []
    for method in methods:
        for query in queries:
            ranked = rank(query["retrievalQuery"], corpus, method)
            relevant = qrels.get(query["id"], {})
            m5 = metrics(ranked, relevant, 5)
            m10 = metrics(ranked, relevant, k)
            rows.append({
                "method": method,
                "queryId": query["id"],
                "language": query.get("language"),
                "answerable": query.get("answerable"),
                "familyId": query.get("familyId"),
                "rankedSegmentIds": [segment_id for segment_id, _ in ranked[:k]],
                "recall@5": m5["recall"],
                "recall@10": m10["recall"],
                "mrr@10": m10["mrr"],
                "ndcg@10": m10["ndcg"],
                "hopRecall@10": hop_recall(query, ranked, corpus_by_id, relevant, k),
                "relevantCount": len([grade for grade in relevant.values() if grade > 0]),
            })
    jsonl_path = output / "eval_results.jsonl"
    with jsonl_path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps({"fixtureSha256": fingerprint, **row}, ensure_ascii=False) + "\n")
    summary_path = output / "eval_summary.csv"
    fields = ["method", "queries", "recall@5", "recall@10", "mrr@10", "ndcg@10", "hopRecall@10"]
    with summary_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for method in methods:
            subset = [row for row in rows if row["method"] == method]
            writer.writerow({
                "method": method,
                "queries": len(subset),
                **{field: f"{sum(float(row[field]) for row in subset) / len(subset):.6f}" for field in fields[2:]},
            })
    judged_summary_path = output / "eval_summary_judged.csv"
    with judged_summary_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for method in methods:
            subset = [row for row in rows if row["method"] == method and row["relevantCount"] > 0]
            writer.writerow({
                "method": method,
                "queries": len(subset),
                **{field: f"{sum(float(row[field]) for row in subset) / len(subset):.6f}" for field in fields[2:]},
            })
    return jsonl_path, summary_path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, default=Path("backend/tests/src/test/resources/rag-eval/candidate10-selection"))
    parser.add_argument("--output", type=Path, default=Path("artifacts/eval_suite/candidate10-selection"))
    parser.add_argument("--k", type=int, default=10)
    parser.add_argument("--methods", nargs="+", default=["token_overlap", "exact_token_overlap"])
    args = parser.parse_args()
    jsonl_path, summary_path = evaluate(args.fixture, args.output, args.methods, args.k)
    print(jsonl_path)
    print(summary_path)


if __name__ == "__main__":
    main()
