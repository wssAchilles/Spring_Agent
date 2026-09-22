#!/usr/bin/env python3
"""Audit the v2 benchmark, qrels and full candidate corpus."""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))

from benchmark_builder import LEAKAGE_THRESHOLD, canonical_json, char_ngrams, normalise_text, sha256_file  # noqa: E402


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def read_qrels(path: Path) -> tuple[list[dict[str, Any]], str]:
    rows: list[dict[str, Any]] = []
    lines = [line for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if not lines:
        raise ValueError("empty qrels")
    header = lines[0].split("\t")
    if header == ["queryId", "iteration", "segmentId", "grade"]:
        width = 4
    elif header == ["queryId", "segmentId", "grade"]:
        width = 3
    else:
        raise ValueError(f"unsupported qrels header: {header}")
    for line in lines[1:]:
        parts = line.split("\t")
        if len(parts) != width:
            raise ValueError(f"qrels row has {len(parts)} columns, expected {width}")
        if width == 4:
            query_id, iteration, segment_id, grade = parts
        else:
            query_id, segment_id, grade = parts
            iteration = "0"
        rows.append({"queryId": query_id, "iteration": iteration, "segmentId": segment_id, "grade": int(grade)})
    return rows, "trec-4-column" if width == 4 else "legacy-3-column"


def overlap_records(benchmark: list[dict[str, Any]], corpus: list[dict[str, Any]]) -> list[dict[str, Any]]:
    passages = [(str(row["segmentId"]), char_ngrams(row.get("content", ""))) for row in corpus]
    records = []
    for row in benchmark:
        grams = char_ngrams(row.get("query", ""))
        best, best_sid = 0.0, None
        for sid, pgrams in passages:
            union = len(grams | pgrams)
            score = len(grams & pgrams) / union if union else 1.0
            if score > best:
                best, best_sid = score, sid
        records.append({"queryId": row["query_id"], "maxJaccard": round(best, 8), "segmentId": best_sid})
    return records


def source_checks(benchmark: list[dict[str, Any]]) -> list[dict[str, Any]]:
    issues = []
    for row in benchmark:
        if row.get("track") != "track_2_enterprise":
            continue
        path = ROOT / str(row.get("source_path", ""))
        anchor = str(row.get("source_anchor", ""))
        if not path.is_file():
            issues.append({"queryId": row["query_id"], "error": "SOURCE_FILE_MISSING", "path": str(path)})
        elif not anchor or anchor not in path.read_text(encoding="utf-8", errors="replace"):
            issues.append({"queryId": row["query_id"], "error": "SOURCE_ANCHOR_MISSING", "path": str(path), "anchor": anchor})
        if row.get("reasoning_type") == "enterprise-component-topology":
            secondary = ROOT / str(row.get("secondary_source_path", ""))
            secondary_anchor = str(row.get("secondary_source_anchor", ""))
            if not secondary.is_file():
                issues.append({"queryId": row["query_id"], "error": "SECONDARY_SOURCE_FILE_MISSING", "path": str(secondary)})
            elif not secondary_anchor or secondary_anchor not in secondary.read_text(encoding="utf-8", errors="replace"):
                issues.append({"queryId": row["query_id"], "error": "SECONDARY_SOURCE_ANCHOR_MISSING", "path": str(secondary), "anchor": secondary_anchor})
    return issues


def audit(args: argparse.Namespace) -> dict[str, Any]:
    benchmark = read_jsonl(args.benchmark)
    corpus = read_jsonl(args.corpus)
    qrels, qrels_format = read_qrels(args.qrels)
    query_ids = {str(row.get("query_id", "")) for row in benchmark}
    corpus_ids = {str(row.get("segmentId", "")) for row in corpus}
    duplicate_groups: dict[str, list[str]] = defaultdict(list)
    for row in benchmark:
        duplicate_groups[normalise_text(row.get("query", ""))].append(str(row.get("query_id", "")))
    duplicate_rows = [{"query": key, "queryIds": ids} for key, ids in sorted(duplicate_groups.items()) if len(ids) > 1]
    qrel_ids: dict[str, set[str]] = defaultdict(set)
    qrel_grades: dict[tuple[str, str], set[int]] = defaultdict(set)
    dangling_queries, dangling_segments = [], []
    for row in qrels:
        qid, sid = str(row["queryId"]), str(row["segmentId"])
        qrel_ids[qid].add(sid)
        qrel_grades[(qid, sid)].add(int(row["grade"]))
        if qid not in query_ids:
            dangling_queries.append(qid)
        if sid not in corpus_ids:
            dangling_segments.append(sid)
    by_qid = {str(row["query_id"]): row for row in benchmark}
    gold_mismatches = [qid for qid, row in by_qid.items() if set(map(str, row.get("gold_chunk_ids", []))) != qrel_ids.get(qid, set())]
    grade_conflicts = [{"queryId": qid, "segmentId": sid, "grades": sorted(grades)} for (qid, sid), grades in qrel_grades.items() if len(grades) > 1]
    checksum_mismatches = []
    for row in benchmark:
        payload = {key: value for key, value in row.items() if key != "sha256_checksum"}
        expected = hashlib.sha256(canonical_json(payload).encode("utf-8")).hexdigest()
        if expected != row.get("sha256_checksum"):
            checksum_mismatches.append(str(row.get("query_id")))
    overlaps = overlap_records(benchmark, corpus)
    max_jaccard = max((float(row["maxJaccard"]) for row in overlaps), default=0.0)
    arithmetic_errors = []
    if len(benchmark) != 300:
        arithmetic_errors.append(f"benchmark_rows={len(benchmark)}")
    if len(query_ids) != 300:
        arithmetic_errors.append(f"unique_query_ids={len(query_ids)}")
    if len(corpus) < 3601:
        arithmetic_errors.append(f"corpus_segments={len(corpus)} (<3601)")
    if len(qrel_ids) != 300:
        arithmetic_errors.append(f"qrel_queries={len(qrel_ids)}")
    report = {
        "schemaVersion": "eswa.evidence-integrity.v2",
        "status": "VALID" if not any((duplicate_rows, dangling_queries, dangling_segments, grade_conflicts, gold_mismatches, checksum_mismatches, source_checks(benchmark), arithmetic_errors, max_jaccard > LEAKAGE_THRESHOLD)) else "NOT_SUBMISSION_READY",
        "inputs": {str(path.relative_to(ROOT)): {"sha256": sha256_file(path), "bytes": path.stat().st_size} for path in (args.benchmark, args.corpus, args.qrels)},
        "counts": {"queryRows": len(benchmark), "uniqueQueryIds": len(query_ids), "uniqueQuestionTexts": len(query_ids) - sum(len(ids) - 1 for ids in duplicate_groups.values() if len(ids) > 1), "corpusSegments": len(corpus), "qrelRows": len(qrels), "qrelQueries": len(qrel_ids)},
        "qrelsFormat": qrels_format,
        "duplicateQuestionGroups": duplicate_rows,
        "danglingQueryIds": sorted(set(dangling_queries)),
        "danglingSegmentIds": sorted(set(dangling_segments)),
        "conflictingGoldAnnotations": grade_conflicts,
        "goldQrelMismatches": gold_mismatches,
        "rowChecksumMismatches": checksum_mismatches,
        "sourceAnchorIssues": source_checks(benchmark),
        "threeGramJaccard": {"definition": "casefolded character trigrams after whitespace removal", "threshold": LEAKAGE_THRESHOLD, "maxJaccard": max_jaccard, "exceedances": [row for row in overlaps if row["maxJaccard"] > LEAKAGE_THRESHOLD], "perQuery": overlaps},
        "arithmeticConsistency": {"passed": not arithmetic_errors, "errors": arithmetic_errors},
        "environment": {"python": sys.version, "interpreter": sys.executable},
    }
    return report


def main() -> None:
    if sys.prefix == sys.base_prefix:
        raise RuntimeError("Use the project .venv Python interpreter")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--benchmark", type=Path, default=ROOT / "backend/tests/src/test/resources/eval/v2/benchmark_300.jsonl")
    parser.add_argument("--corpus", type=Path, default=ROOT / "backend/tests/src/test/resources/eval/v2/corpus_300.jsonl")
    parser.add_argument("--qrels", type=Path, default=ROOT / "backend/tests/src/test/resources/eval/v2/qrels_300.tsv")
    parser.add_argument("--output", type=Path, default=ROOT / "backend/tests/evidence/evidence_integrity_audit_v2.json")
    args = parser.parse_args()
    args.benchmark = args.benchmark.resolve()
    args.corpus = args.corpus.resolve()
    args.qrels = args.qrels.resolve()
    args.output = args.output.resolve()
    report = audit(args)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "counts": report["counts"], "maxJaccard": report["threeGramJaccard"]["maxJaccard"], "duplicateGroups": len(report["duplicateQuestionGroups"]), "danglingIds": len(report["danglingQueryIds"]) + len(report["danglingSegmentIds"]), "checksumMismatches": len(report["rowChecksumMismatches"])}, ensure_ascii=False))
    print(args.output)
    if report["status"] != "VALID":
        raise SystemExit(2)


if __name__ == "__main__":
    main()
