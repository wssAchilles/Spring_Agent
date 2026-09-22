#!/usr/bin/env python3
"""Build the source-grounded v2 ESWA benchmark.

The public rows are selected from the supplied datasets.  Enterprise rows are
questions about declarations and control-flow edges that are verified against
the checked-out backend source.  The script never invents a source row: every
enterprise anchor must be present in its declared file before it is emitted.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))

from benchmark_builder import (  # noqa: E402
    LEAKAGE_THRESHOLD,
    answer_record,
    build_hotpot,
    build_multihop,
    build_split,
    canonical_json,
    char_ngrams,
    corpus_row,
    make_leakage_index,
    normalise_text,
    read_json,
    read_jsonl,
    sha256_bytes,
    sha256_file,
    stable_key,
    write_jsonl,
)
from v2_sources import CONFIG, RECOVERY, TOPOLOGY  # noqa: E402

SEED = 42
PUBLIC_COUNTS = {
    "inference": 50,
    "comparison": 50,
    "hotpot-hard-multi-hop": 50,
    "questanswer_2docs-multi-document": 15,
    "questanswer_3docs-multi-document": 15,
}
ENTERPRISE_COUNTS = {
    "enterprise-core-configuration": 40,
    "enterprise-component-topology": 40,
    "enterprise-failure-recovery": 40,
}

# The second hop for each topology question is an independently located source
# declaration for the referenced component.  Keeping this map explicit makes
# the two-hop contract auditable instead of treating two windows from one file
# as a dependency edge.
TOPOLOGY_SECONDARIES = [
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryIntentAnalyzer.java", "class QueryIntentAnalyzer"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/VectorRetriever.java", "class VectorRetriever"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/KeywordRetriever.java", "class KeywordRetriever"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/MetadataRetriever.java", "class MetadataRetriever"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/GraphRagRetriever.java", "class GraphRagRetriever"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/CandidateFusionService.java", "class CandidateFusionService"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRerankService.java", "class RagRerankService"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagContextBuilder.java", "class RagContextBuilder"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/dal/mapper/knowledgeBase/KmcKnowledgeBaseMapper.java", "interface KmcKnowledgeBaseMapper"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/PermissionFilter.java", "class PermissionFilter"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryEntityExtractionService.java", "class QueryEntityExtractionService"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/CragRetrievalEvaluator.java", "class CragRetrievalEvaluator"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryRouter.java", "class QueryRouter"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryTransformService.java", "class QueryTransformService"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/CragWebSearchClient.java", "class CragWebSearchClient"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/DynamicTopKConfig.java", "class DynamicTopKConfig"),
    ("backend/qknow-framework/qknow-config/src/main/java/tech/qiantong/qknow/config/ThreadPoolConfig.java", "class ThreadPoolConfig"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/SemanticCacheService.java", "class SemanticCacheService"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/GraphRagRetriever.java", "class GraphRagRetriever"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/knowledgeBase/IKmcKnowledgeBaseService.java", "interface IKmcKnowledgeBaseService"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/SemanticCacheService.java", "class SemanticCacheService"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagCacheService.java", "class RagCacheService"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java", "class RagRetrievalService"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagCacheService.java", "class RagCacheService"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java", "class RagRetrievalService"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java", "class AgentOrchestrator"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/FlowExecutor.java", "class FlowExecutor"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/grpc/GrpcReactorBridge.java", "class GrpcReactorBridge"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/FlowExecutor.java", "class FlowExecutor"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/grpc/HermesGrpcService.java", "class HermesGrpcService"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/grpc/HermesGrpcService.java", "class HermesGrpcService"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/grpc/HermesGrpcService.java", "class HermesGrpcService"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/dag/DagExecutor.java", "class DagExecutor"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/FlowStateStore.java", "class FlowStateStore"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/FlowStateStore.java", "class FlowStateStore"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/dag/DagExecutor.java", "class DagExecutor"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/config/PlanSolveConfig.java", "class PlanSolveConfig"),
    ("backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/config/ToolRoutingConfig.java", "class ToolRoutingConfig"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/SemanticCacheService.java", "class SemanticCacheService"),
    ("backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/SemanticCacheService.java", "class SemanticCacheService"),
]


def source_line(path: Path, anchor: str) -> tuple[int, list[str]]:
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if anchor in line:
            lo, hi = max(0, index - 4), min(len(lines), index + 5)
            return index + 1, [f"{n + 1}: {lines[n]}" for n in range(lo, hi)]
    # A few recovery guards are expressed as two adjacent source lines.
    offset = text.find(anchor)
    if offset >= 0:
        index = text.count("\n", 0, offset)
        lo, hi = max(0, index - 4), min(len(lines), index + 6)
        return index + 1, [f"{n + 1}: {lines[n]}" for n in range(lo, hi)]
    raise ValueError(f"anchor not found: {path}:{anchor}")


def first_class_anchor(path: Path) -> str:
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        stripped = line.strip()
        if " class " in f" {stripped} " or stripped.startswith("public class"):
            return stripped
    return path.stem


def source_segment(category: str, ordinal: int, path: Path, anchor: str, root: Path) -> tuple[dict[str, Any], int]:
    line_no, lines = source_line(path, anchor)
    sid = f"src-{category[:4]}-{sha256_bytes(f'{category}:{ordinal}:{path}:{anchor}'.encode())[:14]}"
    rel = str(path.relative_to(root))
    content = "\n".join(lines)
    return corpus_row(
        sid=sid,
        content=content,
        source_dataset="backend-source-v2",
        document_id=f"{rel}#L{line_no}",
        metadata={
            "sourcePath": rel,
            "sourceLine": line_no,
            "sourceAnchor": anchor,
            "sourceCategory": category,
            "enterpriseTrack": "repository-source",
        },
    ), line_no


def build_enterprise_rows(root: Path) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    benchmark: list[dict[str, Any]] = []
    corpus: list[dict[str, Any]] = []
    qrels: list[dict[str, Any]] = []
    categories = (
        ("configuration", CONFIG, "enterprise-core-configuration"),
        ("topology", TOPOLOGY, "enterprise-component-topology"),
        ("recovery", RECOVERY, "enterprise-failure-recovery"),
    )
    for category, specs, reasoning_type in categories:
        if len(specs) != 40:
            raise ValueError(f"{category} catalog must contain 40 rows, found {len(specs)}")
        for ordinal, spec in enumerate(specs):
            if category == "configuration":
                path_text, anchor, answer, query = spec
            else:
                path_text, anchor, query, answer = (spec[key] for key in ("path", "anchor", "query", "answer"))
            path = root / path_text
            if not path.is_file():
                raise FileNotFoundError(path)
            primary, line_no = source_segment(category, ordinal, path, anchor, root)
            corpus.append(primary)
            ids = [str(primary["segmentId"])]
            triples = [{
                "subject": str(path.relative_to(root)),
                "predicate": "contains-source-anchor",
                "object": anchor,
            }]
            if category == "topology":
                secondary_path_text, secondary_anchor = TOPOLOGY_SECONDARIES[ordinal]
                secondary_path = root / secondary_path_text
                if not secondary_path.is_file():
                    raise FileNotFoundError(secondary_path)
                secondary, _ = source_segment("topology-hop2", ordinal, secondary_path, secondary_anchor, root)
                corpus.append(secondary)
                ids.append(str(secondary["segmentId"]))
                triples.append({
                    "subject": str(path.relative_to(root)),
                    "predicate": "references-source-component",
                    "object": str(secondary_path.relative_to(root)),
                })
            elif category == "recovery":
                class_anchor = first_class_anchor(path)
                secondary, _ = source_segment("recovery-owner", ordinal, path, class_anchor, root)
                corpus.append(secondary)
                ids.append(str(secondary["segmentId"]))
                triples.append({
                    "subject": str(path.relative_to(root)),
                    "predicate": "owned-by-source-type",
                    "object": class_anchor,
                })
            qid = f"ent-{category[:4]}-{ordinal:03d}"
            extra = {
                "annotationStatus": "source_verified",
                "source_path": path_text,
                "source_anchor": anchor,
                "source_line": line_no,
                "selection_seed": SEED,
            }
            if category == "topology":
                extra.update({"secondary_source_path": secondary_path_text, "secondary_source_anchor": secondary_anchor})
            benchmark.append(answer_record(
                query_id=qid,
                query=query,
                answer=answer,
                hop_count=len(ids),
                reasoning_type=reasoning_type,
                track="track_2_enterprise",
                source_dataset="backend-source-v2",
                source_key=f"{path_text}::{anchor}",
                gold_ids=ids,
                triples=triples,
                source_index=line_no,
                extra=extra,
            ))
            qrels.extend({"queryId": qid, "segmentId": sid, "grade": 3 - i} for i, sid in enumerate(ids))
    return benchmark, corpus, qrels


def merge_corpus(*groups: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_id: dict[str, dict[str, Any]] = {}
    for group in groups:
        for row in group:
            sid = str(row.get("segmentId", ""))
            if sid:
                by_id.setdefault(sid, row)
    return [by_id[sid] for sid in sorted(by_id)]


def check_unique_queries(benchmark: list[dict[str, Any]]) -> list[dict[str, Any]]:
    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in benchmark:
        groups[normalise_text(row["query"])].append(row)
    return [
        {"query": query, "queryIds": [row["query_id"] for row in rows], "goldSets": [sorted(row["gold_chunk_ids"]) for row in rows]}
        for query, rows in sorted(groups.items()) if len(rows) > 1
    ]


def full_leakage(benchmark: list[dict[str, Any]], corpus: list[dict[str, Any]]) -> list[dict[str, Any]]:
    passages = [(str(row["segmentId"]), char_ngrams(row.get("content", ""))) for row in corpus]
    records: list[dict[str, Any]] = []
    for row in benchmark:
        qgrams = char_ngrams(row["query"])
        best, best_sid = 0.0, None
        for sid, pgrams in passages:
            union = len(qgrams | pgrams)
            score = len(qgrams & pgrams) / union if union else 1.0
            if score > best:
                best, best_sid = score, sid
        records.append({"queryId": row["query_id"], "maxJaccard": round(best, 8), "segmentId": best_sid})
    return records


def validate(benchmark: list[dict[str, Any]], corpus: list[dict[str, Any]], qrels: list[dict[str, Any]]) -> dict[str, Any]:
    duplicate_groups = check_unique_queries(benchmark)
    if len(benchmark) != 300 or len({row["query_id"] for row in benchmark}) != 300:
        raise ValueError("v2 benchmark must contain 300 unique query IDs")
    if duplicate_groups:
        raise ValueError(f"duplicate query texts remain: {duplicate_groups[:2]}")
    corpus_ids = {str(row["segmentId"]) for row in corpus}
    query_ids = {str(row["query_id"]) for row in benchmark}
    if len(corpus_ids) != len(corpus):
        raise ValueError("v2 corpus segment IDs are not unique")
    if len({str(row["queryId"]) for row in qrels}) != 300:
        raise ValueError("every query must have qrels")
    if any(str(row["queryId"]) not in query_ids or str(row["segmentId"]) not in corpus_ids for row in qrels):
        raise ValueError("qrels contain a dangling query or segment ID")
    by_query: dict[str, set[str]] = defaultdict(set)
    for row in qrels:
        by_query[str(row["queryId"])].add(str(row["segmentId"]))
    conflicts = []
    for row in benchmark:
        gold = {str(value) for value in row["gold_chunk_ids"]}
        if gold != by_query[str(row["query_id"])]:
            conflicts.append(row["query_id"])
    if conflicts:
        raise ValueError(f"gold/qrels mismatch: {conflicts[:3]}")
    counts = Counter(str(row["reasoning_type"]) for row in benchmark)
    for key, expected in {**PUBLIC_COUNTS, **ENTERPRISE_COUNTS}.items():
        if counts[key] != expected:
            raise ValueError(f"{key}: expected {expected}, found {counts[key]}")
    overlaps = full_leakage(benchmark, corpus)
    max_overlap = max((row["maxJaccard"] for row in overlaps), default=0.0)
    if max_overlap > LEAKAGE_THRESHOLD:
        offenders = [row for row in overlaps if row["maxJaccard"] > LEAKAGE_THRESHOLD]
        raise ValueError(f"3-gram leakage threshold exceeded: {offenders[:3]}")
    return {
        "duplicateQuestionGroups": duplicate_groups,
        "conflictingGoldGroups": [],
        "counts": {"benchmarkQueries": len(benchmark), "uniqueQuestionTexts": len(benchmark), "corpusSegments": len(corpus), "qrelPairs": len(qrels), "qrelQueries": len(by_query)},
        "reasoningTypeCounts": dict(sorted(counts.items())),
        "fullCorpusLeakage": {"threshold": LEAKAGE_THRESHOLD, "maxJaccard": max_overlap, "perQuery": overlaps, "exceedanceCount": 0},
    }


def build(args: argparse.Namespace) -> dict[str, Any]:
    root = args.root.resolve()
    data_dir = (args.data_dir or root / "data").resolve()
    eval_dir = (args.eval_dir or root / "backend/tests/src/test/resources/eval/v2").resolve()
    evidence_dir = (args.evidence_dir or root / "backend/tests/evidence").resolve()
    fixture_dir = (args.fixture_dir or root / "backend/tests/src/test/resources/rag-eval/candidate10-selection").resolve()
    public_paths = {name: data_dir / name for name in ("MultiHopRAG.json", "hotpot_dev_distractor_v1.json", "split_merged.json", "corpus.json")}
    for path in (*public_paths.values(), fixture_dir / "corpus.jsonl"):
        if not path.is_file():
            raise FileNotFoundError(path)
    multihop = read_json(public_paths["MultiHopRAG.json"])
    hotpot = read_json(public_paths["hotpot_dev_distractor_v1.json"])
    split = read_json(public_paths["split_merged.json"])
    public_corpus = read_json(public_paths["corpus.json"])
    fixture_rows = read_jsonl(fixture_dir / "corpus.jsonl")
    leakage_index = make_leakage_index(public_corpus, fixture_rows)
    benchmark: list[dict[str, Any]] = []
    public_docs: list[dict[str, Any]] = []
    qrels: list[dict[str, Any]] = []
    rejected: list[dict[str, Any]] = []
    for question_type, count in (("inference_query", 50), ("comparison_query", 50)):
        rows, docs, labels, blocked = build_multihop(multihop, question_type, count, leakage_index)
        benchmark.extend(rows); public_docs.extend(docs); qrels.extend(labels); rejected.extend(blocked)
    rows, docs, labels, blocked = build_hotpot(hotpot, 50, leakage_index)
    benchmark.extend(rows); public_docs.extend(docs); qrels.extend(labels); rejected.extend(blocked)
    for subset in ("questanswer_2docs", "questanswer_3docs"):
        rows, docs, labels, blocked = build_split(split, subset, 15, leakage_index)
        benchmark.extend(rows); public_docs.extend(docs); qrels.extend(labels); rejected.extend(blocked)
    enterprise_benchmark, enterprise_docs, enterprise_qrels = build_enterprise_rows(root)
    benchmark.extend(enterprise_benchmark)
    qrels.extend(enterprise_qrels)
    # Keep the previous 3,601-slice candidate pool as a fixed distractor pool;
    # add the source windows used by Track 2 and any newly selected public docs.
    prior_corpus_path = root / "backend/tests/src/test/resources/eval/corpus_300.jsonl"
    prior_corpus = read_jsonl(prior_corpus_path) if prior_corpus_path.is_file() else []
    corpus = merge_corpus(prior_corpus, public_docs, enterprise_docs)
    benchmark.sort(key=lambda row: str(row["query_id"]))
    qrels.sort(key=lambda row: (str(row["queryId"]), str(row["segmentId"])))
    corpus.sort(key=lambda row: str(row["segmentId"]))
    audit = validate(benchmark, corpus, qrels)
    eval_dir.mkdir(parents=True, exist_ok=True)
    evidence_dir.mkdir(parents=True, exist_ok=True)
    benchmark_path = eval_dir / "benchmark_300.jsonl"
    corpus_path = eval_dir / "corpus_300.jsonl"
    qrels_path = eval_dir / "qrels_300.tsv"
    write_jsonl(benchmark_path, benchmark)
    write_jsonl(corpus_path, corpus)
    with qrels_path.open("w", encoding="utf-8") as handle:
        handle.write("queryId\titeration\tsegmentId\tgrade\n")
        for row in qrels:
            handle.write(f"{row['queryId']}\t0\t{row['segmentId']}\t{row['grade']}\n")
    report = {
        "schemaVersion": "eswa.benchmark_report.v2",
        "selectionSeed": SEED,
        "status": "VALID",
        "track2Evidence": "repository-source annotations; not a claim of customer production incidents",
        "leakageCheck": {"algorithm": "casefolded character 3-gram Jaccard with whitespace removed", "threshold": LEAKAGE_THRESHOLD, "maxSelectedScore": audit["fullCorpusLeakage"]["maxJaccard"], "leakage_warnings_count": len(rejected), "rejectedPublicCandidates": rejected[:100]},
        "audit": audit,
        "counts": audit["counts"],
        "sourceFiles": {str(path.relative_to(root)): sha256_file(path) for path in (*public_paths.values(), fixture_dir / "corpus.jsonl")},
        "outputs": {str(path.relative_to(root)): sha256_file(path) for path in (benchmark_path, corpus_path, qrels_path)},
    }
    report_path = evidence_dir / "benchmark_report_v2.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"benchmark": benchmark_path, "corpus": corpus_path, "qrels": qrels_path, "report": report_path, "summary": report}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--data-dir", type=Path)
    parser.add_argument("--fixture-dir", type=Path)
    parser.add_argument("--eval-dir", type=Path)
    parser.add_argument("--evidence-dir", type=Path)
    args = parser.parse_args()
    result = build(args)
    for key in ("benchmark", "corpus", "qrels", "report"):
        print(f"{key}: {result[key]}")
    print(json.dumps(result["summary"]["counts"], ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
