#!/usr/bin/env python3
"""Run the frozen 300-query benchmark with five offline retrieval pipelines.

The runner deliberately has no network, model, or third-party dependency.  Its
``Dense_Only`` path is a deterministic hashed-vector proxy; it must not be
reported as an embedding-model (Qwen) result.  Likewise, no generation model
is used.  Rankings and tie breaking are deterministic; wall-clock stage times
are recorded with ``perf_counter_ns`` for diagnosis only.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
import time
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from statistics import fmean
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_BENCHMARK = ROOT / "backend/tests/src/test/resources/eval/benchmark_300.jsonl"
DEFAULT_CORPUS = ROOT / "backend/tests/src/test/resources/eval/corpus_300.jsonl"
DEFAULT_QRELS = ROOT / "backend/tests/src/test/resources/eval/qrels_300.tsv"
DEFAULT_RESULTS = ROOT / "backend/tests/evidence/eval_results_300.jsonl"
DEFAULT_SUMMARY = ROOT / "backend/tests/evidence/eval_summary_300.csv"

EVALUATION_MODE = "offline_deterministic_retrieval"
RUNNER_VERSION = "2026-09-21"
METHODS = (
    "KnowledgeHub_QuadPath",
    "Dense_Only",
    "BM25_Only",
    "Graph_Only",
    "Hybrid_Dual",
)
TOP_K = 10
RRF_K = 60.0
HASH_DIMENSIONS = 128
TOKEN_RE = re.compile(r"[a-z0-9]+(?:[-_][a-z0-9]+)*|[\u4e00-\u9fff]", re.IGNORECASE)
FAMILY_RE = re.compile(r"c10s-f[0-9]+", re.IGNORECASE)


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    with path.open(encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def read_qrels(path: Path) -> dict[str, dict[str, int]]:
    """Read project qrels or standard four-column TREC qrels.

    Legacy v1 files contain ``queryId, segmentId, grade``.  v2 uses the
    standard ``queryId, iteration, segmentId, grade`` shape; accepting both
    keeps the frozen v1 diagnostic run reproducible while allowing the v2
    artifact to be consumed by ordinary TREC tooling.
    """
    qrels: dict[str, dict[str, int]] = defaultdict(dict)
    with path.open(encoding="utf-8") as handle:
        for line_number, raw in enumerate(handle, 1):
            line = raw.strip()
            if not line:
                continue
            parts = line.split("\t")
            if parts in (["queryId", "segmentId", "grade"], ["queryId", "iteration", "segmentId", "grade"]):
                continue
            if len(parts) == 4:
                query_id, _iteration, segment_id, grade = parts
            elif len(parts) == 3:
                query_id, segment_id, grade = parts
            else:
                raise ValueError(f"{path}:{line_number}: expected 3 or 4 TSV columns")
            try:
                qrels[query_id][segment_id] = int(grade)
            except ValueError as exc:
                raise ValueError(f"{path}:{line_number}: grade is not an integer") from exc
    return dict(qrels)


def file_fingerprint(paths: Iterable[Path]) -> str:
    digest = hashlib.sha256()
    for path in paths:
        digest.update(str(path.name).encode("utf-8"))
        digest.update(path.read_bytes())
    return digest.hexdigest()


def tokens(text: str) -> tuple[str, ...]:
    return tuple(match.casefold() for match in TOKEN_RE.findall(str(text or "")))


def field_text(item: dict[str, Any]) -> str:
    """Build the lexical index text without query-specific metadata."""
    metadata = item.get("metadata") or {}
    values = [item.get("content", ""), item.get("documentId", "")]
    for key in ("title", "documentName", "candidate10Role", "identifierShape", "subset", "newsKey"):
        if metadata.get(key):
            values.append(metadata[key])
    return " ".join(str(value) for value in values if value is not None)


def stable_bucket(value: str) -> int:
    return int.from_bytes(hashlib.sha256(value.encode("utf-8")).digest()[:4], "big") % HASH_DIMENSIONS


def hashed_vector(parts: Iterable[str]) -> tuple[dict[int, float], float]:
    vector: dict[int, float] = defaultdict(float)
    for token in parts:
        vector[stable_bucket("token:" + token)] += 1.0
    norm = math.sqrt(sum(value * value for value in vector.values()))
    return dict(vector), norm


@dataclass(frozen=True)
class Document:
    index: int
    segment_id: str
    text_tokens: tuple[str, ...]
    term_counts: dict[str, int]
    length: int
    vector: dict[int, float]
    vector_norm: float
    graph_terms: frozenset[str]
    family_id: str | None


class LocalIndex:
    """Small in-memory index shared by all five deterministic pipelines."""

    def __init__(self, corpus: list[dict[str, Any]]) -> None:
        self.documents: list[Document] = []
        self.by_segment: dict[str, Document] = {}
        self.postings: dict[str, list[tuple[int, int]]] = defaultdict(list)
        self.family_index: dict[str, list[int]] = defaultdict(list)
        self.document_frequency: dict[str, int] = {}
        self.average_length = 0.0
        for index, item in enumerate(corpus):
            segment_id = str(item.get("segmentId", ""))
            if not segment_id:
                raise ValueError(f"corpus row {index} has no segmentId")
            if segment_id in self.by_segment:
                raise ValueError(f"duplicate corpus segmentId: {segment_id}")
            text = field_text(item)
            text_tokens = tokens(text)
            term_counts = Counter(text_tokens)
            vector, vector_norm = hashed_vector(text_tokens)
            metadata = item.get("metadata") or {}
            family = str(metadata.get("familyId", "")).casefold() or None
            graph_terms = frozenset(tokens(" ".join(
                str(metadata.get(key, ""))
                for key in ("familyId", "title", "documentName", "candidate10Role", "identifierShape", "subset", "newsKey")
            )))
            document = Document(
                index=index,
                segment_id=segment_id,
                text_tokens=text_tokens,
                term_counts=dict(term_counts),
                length=max(1, len(text_tokens)),
                vector=vector,
                vector_norm=vector_norm,
                graph_terms=graph_terms,
                family_id=family,
            )
            self.documents.append(document)
            self.by_segment[segment_id] = document
            for term, frequency in sorted(term_counts.items()):
                self.postings[term].append((index, frequency))
            if family:
                self.family_index[family].append(index)
        self.document_frequency = {term: len(posting) for term, posting in self.postings.items()}
        self.average_length = sum(document.length for document in self.documents) / max(1, len(self.documents))

    def bm25(self, query_tokens: tuple[str, ...]) -> list[tuple[int, float]]:
        scores = [0.0] * len(self.documents)
        query_terms = sorted(set(query_tokens))
        k1, b = 1.2, 0.75
        total = len(self.documents)
        for term in query_terms:
            posting = self.postings.get(term)
            if not posting:
                continue
            df = self.document_frequency[term]
            idf = math.log1p((total - df + 0.5) / (df + 0.5))
            for index, frequency in posting:
                document = self.documents[index]
                denominator = frequency + k1 * (1.0 - b + b * document.length / self.average_length)
                scores[index] += idf * frequency * (k1 + 1.0) / denominator
        return self._ordered(scores)

    def dense(self, query_vector: dict[int, float], query_norm: float) -> list[tuple[int, float]]:
        if not query_vector or query_norm == 0.0:
            return self._ordered([0.0] * len(self.documents))
        scores: list[float] = []
        for document in self.documents:
            if document.vector_norm == 0.0:
                scores.append(0.0)
                continue
            dot = sum(query_vector.get(bucket, 0.0) * value for bucket, value in document.vector.items())
            scores.append(dot / (query_norm * document.vector_norm))
        return self._ordered(scores)

    def graph(self, query: str, query_tokens: tuple[str, ...]) -> list[tuple[int, float]]:
        query_terms = set(query_tokens)
        query_families = {value.casefold() for value in FAMILY_RE.findall(query)}
        scores: list[float] = []
        for document in self.documents:
            family_match = 1.0 if document.family_id and document.family_id in query_families else 0.0
            term_overlap = len(query_terms & document.graph_terms)
            # Family membership is the graph edge; title/role overlap is a
            # weak deterministic fallback for public documents without IDs.
            scores.append(100.0 * family_match + float(term_overlap))
        return self._ordered(scores)

    def _ordered(self, scores: list[float]) -> list[tuple[int, float]]:
        return sorted(
            ((index, float(score)) for index, score in enumerate(scores)),
            key=lambda pair: (-pair[1], self.documents[pair[0]].segment_id),
        )


def reciprocal_rank_fusion(*rankings: list[tuple[int, float]], limit: int = 200) -> list[tuple[int, float]]:
    fused: dict[int, float] = defaultdict(float)
    for ranking in rankings:
        for rank, (index, _score) in enumerate(ranking[:limit], 1):
            fused[index] += 1.0 / (RRF_K + rank)
    return sorted(fused.items(), key=lambda pair: (-pair[1], pair[0]))


def rerank(
    ranking: list[tuple[int, float]],
    index: LocalIndex,
    query_tokens: tuple[str, ...],
    query: str,
) -> list[tuple[int, float]]:
    query_set = set(query_tokens)
    query_families = {value.casefold() for value in FAMILY_RE.findall(query)}
    reranked: list[tuple[int, float]] = []
    for position, (document_index, base_score) in enumerate(ranking):
        document = index.documents[document_index]
        overlap = len(query_set & set(document.text_tokens))
        family_bonus = 1.0 if document.family_id and document.family_id in query_families else 0.0
        # Base RRF score dominates; lexical and family signals only break ties
        # among candidates from the two/four retrieval paths.
        score = base_score * 1000.0 + overlap * 1e-3 + family_bonus * 1e-4 - position * 1e-9
        reranked.append((document_index, score))
    return sorted(reranked, key=lambda pair: (-pair[1], index.documents[pair[0]].segment_id))


def _elapsed(start: int) -> int:
    return max(0, time.perf_counter_ns() - start)


def metrics(ranked_ids: list[str], relevant: dict[str, int], k: int = TOP_K) -> dict[str, float]:
    relevant_ids = {segment_id for segment_id, grade in relevant.items() if grade > 0}
    if not relevant_ids:
        return {"recall@5": 0.0, "recall@10": 0.0, "mrr@10": 0.0, "map@10": 0.0, "ndcg@10": 0.0, "hopRecall@10": 0.0}

    def recall(limit: int) -> float:
        return len(set(ranked_ids[:limit]) & relevant_ids) / len(relevant_ids)

    top = ranked_ids[:k]
    reciprocal = next((1.0 / (position + 1) for position, segment_id in enumerate(top) if segment_id in relevant_ids), 0.0)
    average_precision = 0.0
    hit_count = 0
    for position, segment_id in enumerate(top, 1):
        if segment_id in relevant_ids:
            hit_count += 1
            average_precision += hit_count / position
    average_precision /= max(1, min(len(relevant_ids), k))

    def dcg(grades: list[int]) -> float:
        return sum((2**grade - 1) / math.log2(position + 2) for position, grade in enumerate(grades))

    observed = [relevant.get(segment_id, 0) for segment_id in top]
    ideal = sorted((grade for grade in relevant.values() if grade > 0), reverse=True)[:k]
    ideal_dcg = dcg(ideal)
    ndcg = dcg(observed) / ideal_dcg if ideal_dcg else 0.0
    return {
        "recall@5": recall(5),
        "recall@10": recall(k),
        "mrr@10": reciprocal,
        "map@10": average_precision,
        "ndcg@10": ndcg,
        # Strict multi-hop recall: all judged gold chunks must be present.
        "hopRecall@10": float(relevant_ids.issubset(set(top))),
    }


def run_method(method: str, query: str, index: LocalIndex) -> tuple[list[tuple[int, float]], dict[str, int], list[str]]:
    if method not in METHODS:
        raise ValueError(f"unknown method: {method}")
    total_start = time.perf_counter_ns()
    timings = {name: 0 for name in ("t_embed", "t_bm25", "t_dense", "t_graph", "t_rrf", "t_rerank")}
    query_tokens: tuple[str, ...] = ()
    query_vector: dict[int, float] = {}
    query_norm = 0.0
    bm25_ranking: list[tuple[int, float]] = []
    dense_ranking: list[tuple[int, float]] = []
    graph_ranking: list[tuple[int, float]] = []

    if method in {"KnowledgeHub_QuadPath", "Dense_Only", "Hybrid_Dual"}:
        start = time.perf_counter_ns()
        query_tokens = tokens(query)
        query_vector, query_norm = hashed_vector(query_tokens)
        timings["t_embed"] = _elapsed(start)

    if method in {"KnowledgeHub_QuadPath", "BM25_Only", "Hybrid_Dual"}:
        start = time.perf_counter_ns()
        if not query_tokens:
            query_tokens = tokens(query)
        bm25_ranking = index.bm25(query_tokens)
        timings["t_bm25"] = _elapsed(start)

    if method in {"KnowledgeHub_QuadPath", "Dense_Only", "Hybrid_Dual"}:
        start = time.perf_counter_ns()
        dense_ranking = index.dense(query_vector, query_norm)
        timings["t_dense"] = _elapsed(start)

    if method in {"KnowledgeHub_QuadPath", "Graph_Only"}:
        start = time.perf_counter_ns()
        if not query_tokens:
            query_tokens = tokens(query)
        graph_ranking = index.graph(query, query_tokens)
        timings["t_graph"] = _elapsed(start)

    if method == "KnowledgeHub_QuadPath":
        start = time.perf_counter_ns()
        fused = reciprocal_rank_fusion(bm25_ranking, dense_ranking, graph_ranking)
        timings["t_rrf"] = _elapsed(start)
        start = time.perf_counter_ns()
        final = rerank(fused, index, query_tokens, query)
        timings["t_rerank"] = _elapsed(start)
    elif method == "Hybrid_Dual":
        start = time.perf_counter_ns()
        fused = reciprocal_rank_fusion(bm25_ranking, dense_ranking)
        timings["t_rrf"] = _elapsed(start)
        start = time.perf_counter_ns()
        final = rerank(fused, index, query_tokens, query)
        timings["t_rerank"] = _elapsed(start)
    elif method == "Dense_Only":
        final = dense_ranking
    elif method == "BM25_Only":
        final = bm25_ranking
    else:
        final = graph_ranking

    timings["t_total"] = max(_elapsed(total_start), sum(timings.values()))
    ranked_ids = [index.documents[document_index].segment_id for document_index, _score in final[:TOP_K]]
    return final, timings, ranked_ids


def evaluate(
    benchmark_path: Path = DEFAULT_BENCHMARK,
    corpus_path: Path = DEFAULT_CORPUS,
    qrels_path: Path = DEFAULT_QRELS,
    results_path: Path = DEFAULT_RESULTS,
    summary_path: Path = DEFAULT_SUMMARY,
) -> tuple[Path, Path]:
    benchmark = read_jsonl(benchmark_path)
    corpus = read_jsonl(corpus_path)
    qrels = read_qrels(qrels_path)
    if not benchmark or not corpus:
        raise ValueError("benchmark and corpus must not be empty")
    query_ids = [str(row.get("query_id", "")) for row in benchmark]
    if any(not query_id for query_id in query_ids):
        raise ValueError("every benchmark row must have query_id")
    if len(set(query_ids)) != len(query_ids):
        raise ValueError("benchmark query_id values must be unique")
    unknown_qrels = sorted(set(qrels) - set(query_ids))
    if unknown_qrels:
        raise ValueError(f"qrels contain unknown query IDs: {unknown_qrels[:3]}")

    index = LocalIndex(corpus)
    fingerprint = file_fingerprint((benchmark_path, corpus_path, qrels_path))
    result_rows: list[dict[str, Any]] = []
    for benchmark_row in benchmark:
        query_id = str(benchmark_row["query_id"])
        query = str(benchmark_row.get("retrievalQuery") or benchmark_row.get("query") or "")
        relevant = qrels.get(query_id, {})
        for method in METHODS:
            _final, timings, ranked_ids = run_method(method, query, index)
            row_metrics = metrics(ranked_ids, relevant)
            result_rows.append({
                "schemaVersion": "eswa.eval_results.v1",
                "evaluationMode": EVALUATION_MODE,
                "runnerVersion": RUNNER_VERSION,
                "inputFingerprint": fingerprint,
                "method": method,
                "queryId": query_id,
                "track": benchmark_row.get("track"),
                "sourceDataset": benchmark_row.get("source_dataset"),
                "reasoningType": benchmark_row.get("reasoning_type"),
                "hopCount": benchmark_row.get("hop_count"),
                "query": query,
                "goldSegmentIds": sorted(segment_id for segment_id, grade in relevant.items() if grade > 0),
                "rankedSegmentIds": ranked_ids,
                "relevantCount": len([grade for grade in relevant.values() if grade > 0]),
                **row_metrics,
                **timings,
            })

    results_path.parent.mkdir(parents=True, exist_ok=True)
    with results_path.open("w", encoding="utf-8") as handle:
        for row in result_rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")

    summary_fields = [
        "method", "evaluationMode", "queries", "recall@5", "recall@10", "mrr@10", "map@10", "ndcg@10", "hopRecall@10",
        "t_embed", "t_bm25", "t_dense", "t_graph", "t_rrf", "t_rerank", "t_total",
    ]
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    with summary_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=summary_fields)
        writer.writeheader()
        for method in METHODS:
            subset = [row for row in result_rows if row["method"] == method]
            row: dict[str, Any] = {
                "method": method,
                "evaluationMode": EVALUATION_MODE,
                "queries": len(subset),
            }
            for metric_name in ("recall@5", "recall@10", "mrr@10", "map@10", "ndcg@10", "hopRecall@10"):
                row[metric_name] = f"{fmean(float(item[metric_name]) for item in subset):.9f}"
            for timing_name in ("t_embed", "t_bm25", "t_dense", "t_graph", "t_rrf", "t_rerank", "t_total"):
                row[timing_name] = str(round(fmean(int(item[timing_name]) for item in subset)))
            writer.writerow(row)

    self_check(result_rows, results_path, summary_path)
    return results_path, summary_path


def self_check(rows: list[dict[str, Any]], results_path: Path, summary_path: Path) -> None:
    expected = {row["queryId"] for row in rows}
    if len(rows) != len(expected) * len(METHODS):
        raise AssertionError("result row count is not queries x methods")
    if {row["method"] for row in rows} != set(METHODS):
        raise AssertionError("result methods do not match the frozen method set")
    seen: set[tuple[str, str]] = set()
    for row in rows:
        key = (str(row["queryId"]), str(row["method"]))
        if key in seen:
            raise AssertionError(f"duplicate result row: {key}")
        seen.add(key)
        if row["evaluationMode"] != EVALUATION_MODE:
            raise AssertionError("evaluation mode is not explicit")
        if len(row["rankedSegmentIds"]) > TOP_K or len(set(row["rankedSegmentIds"])) != len(row["rankedSegmentIds"]):
            raise AssertionError("ranked segment IDs are malformed")
        for metric_name in ("recall@5", "recall@10", "mrr@10", "map@10", "ndcg@10", "hopRecall@10"):
            if not 0.0 <= float(row[metric_name]) <= 1.0:
                raise AssertionError(f"metric outside [0,1]: {metric_name}")
        for timing_name in ("t_embed", "t_bm25", "t_dense", "t_graph", "t_rrf", "t_rerank", "t_total"):
            if int(row[timing_name]) < 0:
                raise AssertionError(f"negative timing: {timing_name}")
    if sum(1 for _ in results_path.open(encoding="utf-8")) != len(rows):
        raise AssertionError("written JSONL line count differs from in-memory rows")
    with summary_path.open(encoding="utf-8", newline="") as handle:
        summary_rows = list(csv.DictReader(handle))
    if [row["method"] for row in summary_rows] != list(METHODS):
        raise AssertionError("summary method order is not fixed")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--benchmark", type=Path, default=DEFAULT_BENCHMARK)
    parser.add_argument("--corpus", type=Path, default=DEFAULT_CORPUS)
    parser.add_argument("--qrels", type=Path, default=DEFAULT_QRELS)
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS)
    parser.add_argument("--summary", type=Path, default=DEFAULT_SUMMARY)
    args = parser.parse_args()
    results, summary = evaluate(args.benchmark, args.corpus, args.qrels, args.results, args.summary)
    print(results)
    print(summary)


if __name__ == "__main__":
    main()
