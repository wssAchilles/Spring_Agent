#!/usr/bin/env python3
"""Build the reproducible 300-query ESWA retrieval benchmark.

The public track keeps source questions and evidence intact. The enterprise
track is generated from the frozen candidate10 fixture and is labelled as a
synthetic diagnostic track; it is not evidence of a production deployment.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
from collections import Counter
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[2]
SELECTION_SEED = 20260921
LEAKAGE_THRESHOLD = 0.85
_WS_RE = re.compile(r"\s+", re.UNICODE)


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def read_json(path: Path) -> Any:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    with path.open(encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(canonical_json(row) + "\n")


def normalise_text(value: Any) -> str:
    return _WS_RE.sub(" ", str(value or "")).strip().casefold()


def char_ngrams(value: Any, n: int = 3) -> set[str]:
    """Return whitespace-normalised character n-grams for an auditable check."""
    text = normalise_text(value).replace(" ", "")
    return {text[i : i + n] for i in range(max(0, len(text) - n + 1))}


def trigram_jaccard(left: Any, right: Any) -> float:
    a, b = char_ngrams(left), char_ngrams(right)
    if not a and not b:
        return 1.0
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def stable_key(value: Any) -> str:
    return sha256_bytes(canonical_json(value).encode("utf-8"))


def segment_id(prefix: str, source_key: str, ordinal: int) -> str:
    digest = sha256_bytes(f"{prefix}:{source_key}:{ordinal}".encode("utf-8"))[:12]
    return f"{prefix}-{digest}-{ordinal:02d}"


def evidence_triple(subject: str, predicate: str, obj: str) -> dict[str, str]:
    return {"subject": subject, "predicate": predicate, "object": obj}


def answer_record(
    *, query_id: str, query: str, answer: str, hop_count: int,
    reasoning_type: str, track: str, source_dataset: str, source_key: str,
    gold_ids: list[str], triples: list[dict[str, str]],
    source_index: int | None = None, extra: dict[str, Any] | None = None,
) -> dict[str, Any]:
    row: dict[str, Any] = {
        "query_id": query_id, "query": query, "retrievalQuery": query,
        "hop_count": hop_count, "reasoning_type": reasoning_type,
        "track": track, "source_dataset": source_dataset,
        "source_key": source_key, "gold_chunk_ids": gold_ids,
        "gold_answer": answer, "evidence_triples": triples,
    }
    if source_index is not None:
        row["source_index"] = source_index
    if extra:
        row.update(extra)
    row["sha256_checksum"] = sha256_bytes(canonical_json(row).encode("utf-8"))
    return row


def corpus_row(
    *, sid: str, content: str, source_dataset: str, query_id: str | None = None,
    metadata: dict[str, Any] | None = None, document_id: str | None = None,
) -> dict[str, Any]:
    item: dict[str, Any] = {
        "segmentId": sid, "documentId": document_id or sid,
        "content": content,
        "metadata": {"sourceDataset": source_dataset, **(metadata or {})},
        "parentSegmentId": None,
    }
    if query_id:
        item["metadata"]["queryId"] = query_id
    return item


def sort_records(rows: list[dict[str, Any]], preferred: str | None = None) -> list[dict[str, Any]]:
    if preferred:
        return sorted(rows, key=lambda row: (str(row.get(preferred, "")), stable_key(row)))
    return sorted(rows, key=stable_key)


def make_leakage_index(data_corpus: list[dict[str, Any]], fixture_corpus: list[dict[str, Any]]) -> list[tuple[str, set[str]]]:
    index: list[tuple[str, set[str]]] = []
    for i, item in enumerate(data_corpus):
        text = " ".join(str(item.get(key, "")) for key in ("title", "body"))
        index.append((f"data/corpus.json#{i}", char_ngrams(text)))
    for item in fixture_corpus:
        sid = str(item.get("segmentId", ""))
        index.append((f"candidate10-selection/corpus.jsonl#{sid}", char_ngrams(item.get("content", ""))))
    return index


def leakage_score(query: str, leakage_index: list[tuple[str, set[str]]]) -> tuple[float, str | None]:
    query_grams = char_ngrams(query)
    if not query_grams:
        return 0.0, None
    best_score, best_source = 0.0, None
    for source, grams in leakage_index:
        if not grams:
            continue
        score = len(query_grams & grams) / len(query_grams | grams)
        if score > best_score:
            best_score, best_source = score, source
    return best_score, best_source


def select_with_leakage(
    candidates: list[dict[str, Any]], count: int,
    leakage_index: list[tuple[str, set[str]]], threshold: float,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    selected: list[dict[str, Any]] = []
    rejected: list[dict[str, Any]] = []
    for row in candidates:
        score, source = leakage_score(str(row["query"]), leakage_index)
        row["leakage_score"], row["leakage_source"] = round(score, 8), source
        if score > threshold:
            rejected.append({"source_key": row["source_key"], "score": round(score, 8), "source": source})
            continue
        selected.append(row)
        if len(selected) == count:
            return selected, rejected
    raise RuntimeError(f"only {len(selected)} candidates passed 3-gram threshold {threshold}; needed {count}")


def build_multihop(records: list[dict[str, Any]], question_type: str, count: int, leakage_index: list[tuple[str, set[str]]]):
    candidates: list[dict[str, Any]] = []
    for index, item in enumerate(sort_records([r for r in records if r.get("question_type") == question_type])):
        evidence = [e for e in item.get("evidence_list", []) if str(e.get("fact", "")).strip()]
        if evidence:
            candidates.append({"query": str(item.get("query", "")).strip(), "answer": str(item.get("answer", "")), "source_key": f"mh-{question_type.removesuffix('_query')}-{index:03d}", "source_index": index, "item": item, "evidence": evidence})
    selected, rejected = select_with_leakage(candidates, count, leakage_index, LEAKAGE_THRESHOLD)
    benchmark, corpus, qrels = [], [], []
    for selected_index, candidate in enumerate(selected):
        qid, ids, triples = candidate["source_key"], [], []
        for ordinal, evidence in enumerate(candidate["evidence"]):
            sid, fact, title = segment_id("mh", qid, ordinal), str(evidence.get("fact", "")).strip(), str(evidence.get("title", "")).strip()
            ids.append(sid)
            corpus.append(corpus_row(sid=sid, content=fact, source_dataset="MultiHopRAG", query_id=qid, document_id=str(evidence.get("url") or title or sid), metadata={"title": title, "source": evidence.get("source"), "publishedAt": evidence.get("published_at")}))
            triples.append(evidence_triple(title or "source", "supports", fact))
            qrels.append({"queryId": qid, "segmentId": sid, "grade": max(1, 3 - min(2, ordinal))})
        benchmark.append(answer_record(query_id=qid, query=candidate["query"], answer=candidate["answer"], hop_count=len(ids), reasoning_type=question_type.removesuffix("_query"), track="track_1_public", source_dataset="MultiHopRAG", source_key=qid, gold_ids=ids, triples=triples, source_index=candidate["source_index"], extra={"leakage_score": candidate["leakage_score"], "selection_index": selected_index}))
    return benchmark, corpus, qrels, rejected


def build_hotpot(records: list[dict[str, Any]], count: int, leakage_index: list[tuple[str, set[str]]]):
    hard = sort_records([r for r in records if r.get("level") == "hard"], "_id")
    candidates = [{"query": str(item.get("question", "")), "answer": str(item.get("answer", "")), "source_key": f"hp-{item.get('_id')}", "source_index": i, "item": item} for i, item in enumerate(hard)]
    selected, rejected = select_with_leakage(candidates, count, leakage_index, LEAKAGE_THRESHOLD)
    benchmark, corpus, qrels = [], [], []
    for selected_index, candidate in enumerate(selected):
        item, qid, contexts = candidate["item"], candidate["source_key"], candidate["item"].get("context", [])
        context_ids: dict[tuple[str, int], str] = {}
        for context_index, pair in enumerate(contexts):
            if not isinstance(pair, list) or len(pair) != 2:
                continue
            title, sentences = str(pair[0]), pair[1] if isinstance(pair[1], list) else []
            for sentence_index, sentence in enumerate(sentences):
                sid = segment_id("hp", qid, context_index * 1000 + sentence_index)
                context_ids[(title, sentence_index)] = sid
                corpus.append(corpus_row(sid=sid, content=str(sentence).strip(), source_dataset="hotpot_dev_distractor_v1", query_id=qid, document_id=title, metadata={"title": title, "contextIndex": context_index, "sentenceIndex": sentence_index}))
        ids, triples = [], []
        for fact_index, pair in enumerate(item.get("supporting_facts", [])):
            if not isinstance(pair, list) or len(pair) != 2:
                continue
            title, sentence_index = str(pair[0]), int(pair[1])
            sid = context_ids.get((title, sentence_index))
            if not sid:
                continue
            ids.append(sid)
            sentence = next((str(c[1][sentence_index]) for c in contexts if isinstance(c, list) and len(c) == 2 and str(c[0]) == title and isinstance(c[1], list) and sentence_index < len(c[1])), "")
            triples.append(evidence_triple(title, "supports", sentence))
            qrels.append({"queryId": qid, "segmentId": sid, "grade": max(1, 3 - min(2, fact_index))})
        if not ids:
            raise ValueError(f"Hotpot query {qid} has no resolvable supporting facts")
        benchmark.append(answer_record(query_id=qid, query=candidate["query"], answer=candidate["answer"], hop_count=len(ids), reasoning_type="hotpot-hard-multi-hop", track="track_1_public", source_dataset="hotpot_dev_distractor_v1", source_key=qid, gold_ids=ids, triples=triples, source_index=candidate["source_index"], extra={"level": item.get("level"), "leakage_score": candidate["leakage_score"], "selection_index": selected_index}))
    return benchmark, corpus, qrels, rejected


def build_split(records: dict[str, list[dict[str, Any]]], subset: str, count: int, leakage_index: list[tuple[str, set[str]]]):
    source_rows = sort_records(records.get(subset, []), "ID")
    candidates = [{"query": str(item.get("questions", "")), "answer": str(item.get("answers", "")), "source_key": f"split-{subset}-{item.get('ID')}", "source_index": i, "item": item} for i, item in enumerate(source_rows) if str(item.get("questions", "")).strip()]
    selected, rejected = select_with_leakage(candidates, count, leakage_index, LEAKAGE_THRESHOLD)
    benchmark, corpus, qrels = [], [], []
    for selected_index, candidate in enumerate(selected):
        item, qid = candidate["item"], candidate["source_key"]
        news_keys = [key for key in sorted(item) if key.startswith("news") and str(item.get(key, "")).strip()]
        ids, triples = [], []
        for ordinal, news_key in enumerate(news_keys):
            sid, content = segment_id("split", qid, ordinal), str(item[news_key]).strip()
            ids.append(sid)
            corpus.append(corpus_row(sid=sid, content=content, source_dataset="split_merged", query_id=qid, document_id=f"{item.get('ID')}-{news_key}", metadata={"subset": subset, "newsKey": news_key, "recordId": item.get("ID")}))
            triples.append(evidence_triple(news_key, "reports", content[:500]))
            qrels.append({"queryId": qid, "segmentId": sid, "grade": max(1, 3 - min(2, ordinal))})
        benchmark.append(answer_record(query_id=qid, query=candidate["query"], answer=candidate["answer"], hop_count=len(ids), reasoning_type=f"{subset}-multi-document", track="track_1_public", source_dataset="split_merged", source_key=qid, gold_ids=ids, triples=triples, source_index=candidate["source_index"], extra={"subset": subset, "leakage_score": candidate["leakage_score"], "selection_index": selected_index}))
    return benchmark, corpus, qrels, rejected


def build_enterprise(fixture_rows: list[dict[str, Any]]):
    rows = sorted(fixture_rows, key=lambda row: (str(row.get("segmentId", "")), stable_key(row)))
    corpus = []
    for original in rows:
        item = copy.deepcopy(original)
        metadata = dict(item.get("metadata") or {})
        metadata["sourceDataset"], metadata["enterpriseTrack"] = "candidate10-selection", "synthetic_diagnostic_fixture"
        item["metadata"] = metadata
        corpus.append(item)
    by_family: dict[str, list[dict[str, Any]]] = {}
    for row in rows:
        by_family.setdefault(str(row.get("metadata", {}).get("familyId", "unknown")), []).append(row)
    core = [row for row in rows if str(row.get("metadata", {}).get("candidate10Role", "")).endswith("-core")]
    pressure = [row for row in rows if row.get("metadata", {}).get("candidate10Role") == "pressure-distractor"]
    if len(core) < 40 or len(pressure) < 40:
        raise ValueError("candidate10 fixture does not contain enough core/pressure rows")
    benchmark, qrels = [], []
    for index, row in enumerate(core[:40]):
        sid, family, qid = str(row["segmentId"]), str(row.get("metadata", {}).get("familyId", "unknown")), f"ent-single-{index:03d}"
        benchmark.append(answer_record(query_id=qid, query=f"Which single configuration evidence is associated with candidate10 family {family}?", answer=f"{sid} ({row.get('metadata', {}).get('documentName', sid)})", hop_count=1, reasoning_type="single-hop-configuration", track="track_2_enterprise", source_dataset="candidate10-selection", source_key=sid, gold_ids=[sid], triples=[evidence_triple(family, "contains", sid)], extra={"synthetic": True, "fixtureRole": row.get("metadata", {}).get("candidate10Role")}))
        qrels.append({"queryId": qid, "segmentId": sid, "grade": 3})
    for family_index, family in enumerate(sorted(by_family)):
        family_rows = [row for row in by_family[family] if str(row.get("metadata", {}).get("candidate10Role", "")).endswith("-core")]
        if len(family_rows) < 2:
            continue
        for copy_index in range(2):
            index, qid, pair = family_index * 2 + copy_index, f"ent-cross-{family_index * 2 + copy_index:03d}", family_rows[:2]
            ids = [str(row["segmentId"]) for row in pair]
            benchmark.append(answer_record(query_id=qid, query=f"Which two component segments jointly describe candidate10 family {family}?", answer="; ".join(ids), hop_count=2, reasoning_type="cross-component-dependency", track="track_2_enterprise", source_dataset="candidate10-selection", source_key=family, gold_ids=ids, triples=[evidence_triple(family, "depends-on", sid) for sid in ids], extra={"synthetic": True, "familyId": family, "replicate": copy_index}))
            qrels.extend({"queryId": qid, "segmentId": sid, "grade": 3 - i} for i, sid in enumerate(ids))
    for index, row in enumerate(pressure[:40]):
        sid, family, qid = str(row["segmentId"]), str(row.get("metadata", {}).get("familyId", "unknown")), f"ent-troubleshoot-{index:03d}"
        benchmark.append(answer_record(query_id=qid, query=f"Which diagnostic segment should be inspected for a recovery issue in candidate10 family {family}?", answer=f"{sid} ({row.get('metadata', {}).get('documentName', sid)})", hop_count=1, reasoning_type="troubleshooting", track="track_2_enterprise", source_dataset="candidate10-selection", source_key=sid, gold_ids=[sid], triples=[evidence_triple(family, "diagnoses", sid)], extra={"synthetic": True, "fixtureRole": "pressure-distractor"}))
        qrels.append({"queryId": qid, "segmentId": sid, "grade": 3})
    if sum(row["reasoning_type"] == "cross-component-dependency" for row in benchmark) != 40:
        raise ValueError("expected 40 cross-component enterprise questions")
    return benchmark, corpus, qrels


def parse_qrels(path: Path) -> dict[str, dict[str, int]]:
    result: dict[str, dict[str, int]] = {}
    with path.open(encoding="utf-8") as handle:
        header = handle.readline().strip().split("\t")
        if header != ["queryId", "segmentId", "grade"]:
            raise ValueError(f"unexpected qrels header: {header}")
        for line in handle:
            if line.strip():
                query_id, segment_id, grade = line.rstrip("\n").split("\t")
                result.setdefault(query_id, {})[segment_id] = int(grade)
    return result


def build_manifest(fixture: Path) -> dict[str, Any]:
    """Keep the earlier candidate10 manifest API usable by diagnostic tools."""
    required = [fixture / name for name in ("queries.jsonl", "corpus.jsonl", "qrels.tsv")]
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise FileNotFoundError("missing fixture files: " + ", ".join(missing))
    queries, corpus, qrels = read_jsonl(required[0]), read_jsonl(required[1]), parse_qrels(required[2])
    segment_ids, query_ids = [str(item.get("segmentId")) for item in corpus], [str(item.get("id")) for item in queries]
    if len(set(segment_ids)) != len(segment_ids) or len(set(query_ids)) != len(query_ids):
        raise ValueError("fixture identifiers must be unique")
    return {"fixture": str(fixture), "fixtureSha256": sha256_bytes(b"".join(path.read_bytes() for path in required)), "files": {path.name: sha256_file(path) for path in required}, "counts": {"queries": len(queries), "corpusSegments": len(corpus), "qrelQueries": len(qrels), "positiveQrelPairs": sum(sum(g > 0 for g in rel.values()) for rel in qrels.values())}, "status": "VALID"}


def validate(benchmark: list[dict[str, Any]], corpus: list[dict[str, Any]], qrels: list[dict[str, int]], expected_counts: dict[str, int], max_leakage: float) -> None:
    if len(benchmark) != 300 or len({row["query_id"] for row in benchmark}) != 300:
        raise ValueError(f"expected 300 unique benchmark queries, got {len(benchmark)}")
    corpus_ids, benchmark_ids = {str(row["segmentId"]) for row in corpus}, {row["query_id"] for row in benchmark}
    if len(corpus_ids) != len(corpus):
        raise ValueError("corpus segmentId values must be unique")
    if any(row["queryId"] not in benchmark_ids or row["segmentId"] not in corpus_ids for row in qrels):
        raise ValueError("qrels reference unknown query or segment")
    if any(not row["gold_chunk_ids"] for row in benchmark):
        raise ValueError("every query must have at least one gold chunk")
    actual_counts = Counter(row["reasoning_type"] for row in benchmark)
    for name, count in expected_counts.items():
        if actual_counts[name] != count:
            raise ValueError(f"expected {count} rows for {name}, got {actual_counts[name]}")
    if max((float(row.get("leakage_score", 0.0)) for row in benchmark), default=0.0) > max_leakage:
        raise ValueError("selected benchmark contains a leakage score above threshold")


def build(args: argparse.Namespace) -> dict[str, Any]:
    data_dir, fixture_dir = args.data_dir, args.fixture_dir
    paths = {name: data_dir / name for name in ("MultiHopRAG.json", "hotpot_dev_distractor_v1.json", "split_merged.json", "corpus.json")}
    fixture_path = fixture_dir / "corpus.jsonl"
    for path in (*paths.values(), fixture_path):
        if not path.is_file():
            raise FileNotFoundError(path)
    multihop, hotpot, split, public_corpus = [read_json(paths[name]) for name in ("MultiHopRAG.json", "hotpot_dev_distractor_v1.json", "split_merged.json", "corpus.json")]
    fixture_rows = read_jsonl(fixture_path)
    leakage_index = make_leakage_index(public_corpus, fixture_rows)
    benchmark: list[dict[str, Any]] = []
    corpus: list[dict[str, Any]] = []
    qrels: list[dict[str, int]] = []
    rejected: list[dict[str, Any]] = []
    selections: dict[str, int] = {}
    for question_type, count in (("inference_query", 50), ("comparison_query", 50)):
        rows, docs, labels, blocked = build_multihop(multihop, question_type, count, leakage_index)
        benchmark.extend(rows); corpus.extend(docs); qrels.extend(labels); rejected.extend(blocked); selections[f"MultiHopRAG.{question_type}"] = len(rows)
    rows, docs, labels, blocked = build_hotpot(hotpot, 50, leakage_index)
    benchmark.extend(rows); corpus.extend(docs); qrels.extend(labels); rejected.extend(blocked); selections["hotpot_dev_distractor_v1.hard"] = len(rows)
    for subset in ("questanswer_2docs", "questanswer_3docs"):
        rows, docs, labels, blocked = build_split(split, subset, 15, leakage_index)
        benchmark.extend(rows); corpus.extend(docs); qrels.extend(labels); rejected.extend(blocked); selections[f"split_merged.{subset}"] = len(rows)
    enterprise_rows, enterprise_corpus, enterprise_qrels = build_enterprise(fixture_rows)
    benchmark.extend(enterprise_rows); corpus.extend(enterprise_corpus); qrels.extend(enterprise_qrels)
    selections.update({"candidate10.single-hop": 40, "candidate10.cross-component": 40, "candidate10.troubleshooting": 40})
    benchmark.sort(key=lambda row: row["query_id"]); corpus.sort(key=lambda row: str(row["segmentId"])); qrels.sort(key=lambda row: (row["queryId"], row["segmentId"]))
    expected = {"inference": 50, "comparison": 50, "hotpot-hard-multi-hop": 50, "questanswer_2docs-multi-document": 15, "questanswer_3docs-multi-document": 15, "single-hop-configuration": 40, "cross-component-dependency": 40, "troubleshooting": 40}
    validate(benchmark, corpus, qrels, expected, LEAKAGE_THRESHOLD)
    eval_dir, evidence_dir = args.eval_dir, args.evidence_dir
    benchmark_path, qrels_path, corpus_path, report_path = eval_dir / "benchmark_300.jsonl", eval_dir / "qrels_300.tsv", eval_dir / "corpus_300.jsonl", evidence_dir / "benchmark_report.json"
    write_jsonl(benchmark_path, benchmark); write_jsonl(corpus_path, corpus)
    qrels_path.parent.mkdir(parents=True, exist_ok=True)
    with qrels_path.open("w", encoding="utf-8") as handle:
        handle.write("queryId\tsegmentId\tgrade\n")
        for row in qrels:
            handle.write(f"{row['queryId']}\t{row['segmentId']}\t{row['grade']}\n")
    max_leakage = max((float(row.get("leakage_score", 0.0)) for row in benchmark), default=0.0)
    report = {
        "builderVersion": "eswa-benchmark-builder-v2", "selectionSeed": SELECTION_SEED,
        "selectionRule": "stable SHA-256 ordering, first candidates under character 3-gram Jaccard threshold",
        "leakageCheck": {"algorithm": "character 3-gram Jaccard after whitespace normalisation", "threshold": LEAKAGE_THRESHOLD, "maxSelectedScore": max_leakage, "leakage_warnings_count": len(rejected), "rejectedCandidates": rejected[:100]},
        "counts": {"benchmarkQueries": len(benchmark), "corpusSegments": len(corpus), "qrelPairs": len(qrels), "qrelQueries": len({r['queryId'] for r in qrels})},
        "selectionCounts": selections,
        "sourceAvailability": {"MultiHopRAG.total": len(multihop), "MultiHopRAG.inference_query": sum(r.get("question_type") == "inference_query" for r in multihop), "MultiHopRAG.comparison_query": sum(r.get("question_type") == "comparison_query" for r in multihop), "hotpot.total": len(hotpot), "hotpot.hard": sum(r.get("level") == "hard" for r in hotpot), "split.questanswer_2docs": len(split.get("questanswer_2docs", [])), "split.questanswer_3docs": len(split.get("questanswer_3docs", [])), "candidate10.corpusSegments": len(fixture_rows)},
        "sourceFiles": {str(path.relative_to(ROOT)): sha256_file(path) for path in (*paths.values(), fixture_path)},
        "outputs": {path.name: {"path": str(path), "sha256": sha256_file(path)} for path in (benchmark_path, qrels_path, corpus_path)},
        "enterpriseTrackDisclaimer": "Track 2 is generated from the frozen candidate10-selection fixture. It is a synthetic diagnostic benchmark and must not be presented as a production private-enterprise case study.",
        "qrelsFormat": "project-compatible TREC-style qrels: queryId, segmentId, grade; iteration is implicitly 0", "status": "VALID",
    }
    evidence_dir.mkdir(parents=True, exist_ok=True); report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"benchmark": benchmark_path, "qrels": qrels_path, "corpus": corpus_path, "report": report_path, "summary": report}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--data-dir", type=Path); parser.add_argument("--fixture-dir", type=Path)
    parser.add_argument("--eval-dir", type=Path); parser.add_argument("--evidence-dir", type=Path)
    args = parser.parse_args(); root = args.root.resolve()
    args.data_dir = (args.data_dir or root / "data").resolve(); args.fixture_dir = (args.fixture_dir or root / "backend/tests/src/test/resources/rag-eval/candidate10-selection").resolve(); args.eval_dir = (args.eval_dir or root / "backend/tests/src/test/resources/eval").resolve(); args.evidence_dir = (args.evidence_dir or root / "backend/tests/evidence").resolve()
    result = build(args)
    for key in ("benchmark", "qrels", "corpus", "report"):
        print(f"{key}: {result[key]}")
    print(json.dumps({"counts": result["summary"]["counts"], "selectionCounts": result["summary"]["selectionCounts"], "leakage": result["summary"]["leakageCheck"]}, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
