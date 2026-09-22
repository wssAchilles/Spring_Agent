#!/usr/bin/env python3
"""Audit frozen evidence without changing queries, qrels, rankings, or scores."""
from __future__ import annotations

import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

from benchmark_builder import canonical_json, char_ngrams, normalise_text, sha256_file
from eval_runner_300 import METHODS, file_fingerprint, metrics, read_jsonl, read_qrels
from statistical_tests_300 import METRICS, load_and_validate

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "backend/tests/evidence"
DATA = ROOT / "backend/tests/src/test/resources/eval"


def duplicate_groups(queries):
    groups = defaultdict(list)
    for row in queries:
        groups[normalise_text(row["query"])].append(row)
    return [
        {
            "query": text,
            "ids": [row["query_id"] for row in group],
            "distinctGoldSets": len({tuple(sorted(row["gold_chunk_ids"])) for row in group}),
        }
        for text, group in sorted(groups.items()) if len(group) > 1
    ]


def full_corpus_overlap(queries, corpus):
    # This is a lexical-overlap screen, not proof of train/test independence.
    passages = [(str(row["segmentId"]), char_ngrams(row["content"])) for row in corpus]
    records = []
    for row in queries:
        grams = char_ngrams(row["query"])
        best, source = 0.0, None
        for sid, other in passages:
            intersection = len(grams & other)
            union = len(grams) + len(other) - intersection
            value = intersection / union if union else 1.0
            if value > best:
                best, source = value, sid
        records.append({"queryId": row["query_id"], "maxJaccard": best, "segmentId": source})
    return records


def contract_logs(paths):
    suites = []
    pattern = re.compile(
        r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+).*? -- in ([\w.]+)"
    )
    for path in paths:
        content = path.read_text(encoding="utf-8")
        matches = list(pattern.finditer(content))
        if not matches:
            raise ValueError(f"no suite summary in {path}")
        for match in matches:
            suites.append({
                "suite": match[5], "tests": int(match[1]), "failures": int(match[2]),
                "errors": int(match[3]), "skipped": int(match[4]),
                "buildSuccess": "BUILD SUCCESS" in content,
                "log": str(path.relative_to(ROOT)), "sha256": sha256_file(path),
            })
    return suites


def audit():
    paths = [DATA / name for name in ("benchmark_300.jsonl", "corpus_300.jsonl", "qrels_300.tsv")]
    result_path = EVIDENCE / "eval_results_300.jsonl"
    queries, corpus, qrels = read_jsonl(paths[0]), read_jsonl(paths[1]), read_qrels(paths[2])
    rows = load_and_validate(result_path)
    by_id = {row["query_id"]: row for row in queries}
    corpus_ids = {str(row["segmentId"]) for row in corpus}
    fingerprint = file_fingerprint(paths)
    errors = []
    for row in rows:
        qid = row["queryId"]
        if qid not in by_id or qid not in qrels:
            errors.append({"queryId": qid, "error": "UNKNOWN_QUERY"})
            continue
        expected = metrics(row["rankedSegmentIds"], qrels[qid])
        for metric in METRICS:
            if abs(float(row[metric]) - expected[metric]) > 1e-12:
                errors.append({"queryId": qid, "method": row["method"], "metric": metric})
        if row["inputFingerprint"] != fingerprint:
            errors.append({"queryId": qid, "error": "INPUT_FINGERPRINT_MISMATCH"})
        if set(row["goldSegmentIds"]) != set(by_id[qid]["gold_chunk_ids"]):
            errors.append({"queryId": qid, "error": "GOLD_IDS_MISMATCH"})
        if set(row["rankedSegmentIds"]) - corpus_ids:
            errors.append({"queryId": qid, "error": "UNKNOWN_RANKED_ID"})
    checksum_errors = []
    for row in queries:
        payload = {k: v for k, v in row.items() if k != "sha256_checksum"}
        actual = hashlib.sha256(canonical_json(payload).encode("utf-8")).hexdigest()
        if actual != row["sha256_checksum"]:
            checksum_errors.append(row["query_id"])
    duplicates = duplicate_groups(queries)
    overlaps = full_corpus_overlap(queries, corpus)
    initial_suites = contract_logs(sorted((EVIDENCE / "phase1_logs").glob("[0-9][0-9]_*.log")))
    recheck = EVIDENCE / "phase1_recheck.log"
    suites = contract_logs([recheck]) if recheck.exists() else initial_suites
    if {s["suite"] for s in suites} != {s["suite"] for s in initial_suites}:
        raise ValueError("recheck does not cover the original contract suite set")
    counts = {key: sum(suite[key] for suite in suites) for key in ("tests", "failures", "errors", "skipped")}
    counts["passed"] = counts["tests"] - counts["failures"] - counts["errors"] - counts["skipped"]
    issues = [
        {"code": "NONINDEPENDENT_QUERY_ROWS", "detail": "Repeated question texts, including contradictory gold sets, invalidate treating all 300 rows as independent observations."},
        {"code": "UNSUPPORTED_RELEVANCE_GRADES", "detail": "benchmark_builder.py assigns public grades by evidence position (3,2,1), not source relevance judgments."},
        {"code": "SYNTHETIC_ENTERPRISE_CONTENT", "detail": "candidate10 content comprises identifier/pressure fixtures; family membership is not a software dependency or incident label."},
        {"code": "QRELS_FORMAT_NOT_STANDARD_TREC", "detail": "Frozen qrels have a header and three fields; standard TREC requires query, iteration, document, relevance. No labels were changed."},
        {"code": "PARTIAL_ORIGINAL_OVERLAP_SCREEN", "detail": "Original builder screens 180 public questions against corpus.json and candidate10 only; enterprise rows have no leakage_score; selected Hotpot/split passages were outside that screen."},
        {"code": "THREE_PATH_PROXY", "detail": "QuadPath ID invokes BM25, 128-bucket hashed vectors and metadata-family scores, followed by RRF and tie breaking. No fourth metadata ranking, Qwen embedding, graph traversal or backend call is executed."},
        {"code": "SEQUENTIAL_PROXY", "detail": "eval_runner_300.py loops over queries and methods synchronously; stage elapsed times do not measure production concurrency."},
        {"code": "SENSITIVITY_SCOPE_INCOMPLETE", "detail": "K=1,3,5,10 cutoff analysis does not implement alpha/h/chunk-size/RRF grids."},
        {"code": "NO_MEASURED_ROI_OR_ENERGY", "detail": "No incident, annotation, metered-energy or finance records establish MTTR, hallucination, kappa, 312% ROI, or cost superiority."},
    ]
    if counts["failures"] or counts["errors"]:
        issues.append({"code": "CONTRACT_FAILURE", "detail": "Phase18 DirectByteBuffer contract fails at assertNotNull despite native library availability; original failure is retained."})
    report = {
        "schemaVersion": "eswa.evidence-integrity.v1", "status": "NOT_SUBMISSION_READY",
        "inputSha256": {str(p.relative_to(ROOT)): sha256_file(p) for p in [*paths, result_path]},
        "counts": {"queryRows": len(queries), "uniqueIds": len(by_id),
                   "uniqueQuestionTexts": len(queries) - sum(len(d["ids"]) - 1 for d in duplicates),
                   "duplicateTextGroups": len(duplicates),
                   "conflictingGoldGroups": sum(d["distinctGoldSets"] > 1 for d in duplicates),
                   "corpusSegments": len(corpus), "qrelPairs": sum(len(v) for v in qrels.values()),
                   "resultRows": len(rows), "missingOriginalLeakageScores": sum("leakage_score" not in q for q in queries)},
        "arithmeticConsistency": {"passed": not errors, "errors": errors},
        "rowChecksumMismatches": checksum_errors, "duplicateQuestionGroups": duplicates,
        "fullSelectedCorpusOverlap": {"definition": "casefolded character trigrams with whitespace removed; full selected corpus only",
            "threshold": 0.85, "maxJaccard": max(x["maxJaccard"] for x in overlaps),
            "exceedances": [x for x in overlaps if x["maxJaccard"] > 0.85], "perQuery": overlaps,
            "limitation": "No inference about training contamination, independence or annotation correctness."},
        "phase1InitialAttempts": initial_suites,
        "phase1": {"counts": counts, "suites": suites,
                   "localNativeBuild": "cargo build --manifest-path backend/tools/vecsim-jni/Cargo.toml --release --locked",
                   "nativeOverride": "-Dqknow.native.lib.dir=<project>/backend/tools/vecsim-jni/target/release",
                   "javaVersionLog": "backend/tests/evidence/phase1_logs/java_version.txt",
                   "limitation": "Maven logs include component mocks. They are not production-load or real-database concurrency evidence. Initial stale-JNI failure remains archived."},
        "issues": issues,
    }
    return report


def main():
    if sys.prefix == sys.base_prefix:
        raise RuntimeError("Use the project .venv Python interpreter")
    probe = [{"query_id": str(i), "query": "same text", "gold_chunk_ids": [str(i)]} for i in range(2)]
    assert duplicate_groups(probe)[0]["distinctGoldSets"] == 2
    report = audit()
    output = EVIDENCE / "evidence_integrity_audit.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    counts = report["counts"]
    macros = {"AuditRows": counts["queryRows"], "AuditUniqueTexts": counts["uniqueQuestionTexts"],
              "AuditDuplicateGroups": counts["duplicateTextGroups"], "AuditConflictingGroups": counts["conflictingGoldGroups"],
              "AuditContracts": report["phase1"]["counts"]["tests"], "AuditContractPasses": report["phase1"]["counts"]["passed"],
              "AuditContractFailures": report["phase1"]["counts"]["failures"]}
    (ROOT / "article/generated_audit_macros.tex").write_text(
        "% Generated by scripts/eval_suite/audit_evidence.py\n" +
        "".join(f"\\newcommand{{\\{key}}}{{{value}}}\n" for key, value in macros.items()), encoding="utf-8")
    print(json.dumps({"status": report["status"], "counts": counts, "contracts": report["phase1"]["counts"],
                      "arithmetic": report["arithmeticConsistency"]["passed"], "maxFullCorpusJaccard": report["fullSelectedCorpusOverlap"]["maxJaccard"],
                      "checksumFailures": len(report["rowChecksumMismatches"])}, indent=2))
    print(output)


if __name__ == "__main__":
    main()
