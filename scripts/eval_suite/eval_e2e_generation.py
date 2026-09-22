#!/usr/bin/env python3
"""Run the 100-question DeepSeek generation and judge experiment.

The runner uses only the official OpenAI-compatible DeepSeek endpoint.  It
loads credentials from the process environment or the local ``.env`` file and
never writes the key to an artifact.  A missing key produces an explicit
blocked report instead of synthetic scores.
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import random
import re
import statistics
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
MODEL = "deepseek-flash"
SEED = 42
GROUPS = ("No-RAG Baseline", "Dense-Only RAG", "BM25-Only RAG", "KnowledgeHub QuadPath RAG")
GROUP_METHOD = {"Dense-Only RAG": "Dense_Only", "BM25-Only RAG": "BM25_Only", "KnowledgeHub QuadPath RAG": "KnowledgeHub_QuadPath"}
TOKEN_RE = re.compile(r"[A-Za-z0-9]+(?:[-_][A-Za-z0-9]+)*|[\u4e00-\u9fff]")


def load_dotenv(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.is_file():
        return values
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values.setdefault(key.strip(), value)
    return values


def credentials(root: Path) -> tuple[str | None, str]:
    dotenv = load_dotenv(root / ".env")
    key = os.environ.get("DEEPSEEK_API_KEY") or os.environ.get("HERMES_OPENAI_API_KEY") or dotenv.get("DEEPSEEK_API_KEY") or dotenv.get("HERMES_OPENAI_API_KEY")
    base = os.environ.get("DEEPSEEK_BASE_URL") or os.environ.get("HERMES_OPENAI_BASE_URL") or dotenv.get("DEEPSEEK_BASE_URL") or dotenv.get("HERMES_OPENAI_BASE_URL") or "https://api.deepseek.com"
    return key, base.rstrip("/")


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def read_eval_rows(path: Path) -> dict[str, dict[str, dict[str, Any]]]:
    grouped: dict[str, dict[str, dict[str, Any]]] = {}
    for row in read_jsonl(path):
        grouped.setdefault(str(row["queryId"]), {})[str(row["method"])] = row
    return grouped


def sample_rows(benchmark: list[dict[str, Any]], limit: int = 100) -> list[dict[str, Any]]:
    rng = random.Random(SEED)

    def choose(predicate, count: int, label: str) -> list[dict[str, Any]]:
        candidates = sorted((row for row in benchmark if predicate(row)), key=lambda row: str(row["query_id"]))
        if len(candidates) < count:
            raise ValueError(f"{label}: need {count}, found {len(candidates)}")
        return rng.sample(candidates, count)

    selected: list[dict[str, Any]] = []
    selected.extend(choose(lambda r: r.get("source_dataset") == "MultiHopRAG" and r.get("reasoning_type") == "inference", 20, "MultiHop inference"))
    selected.extend(choose(lambda r: r.get("source_dataset") == "MultiHopRAG" and r.get("reasoning_type") == "comparison", 20, "MultiHop comparison"))
    selected.extend(choose(lambda r: r.get("source_dataset") == "hotpot_dev_distractor_v1" and r.get("reasoning_type") == "hotpot-hard-multi-hop", 10, "Hotpot hard"))
    selected.extend(choose(lambda r: r.get("source_dataset") == "split_merged", 10, "split multi-document"))
    selected.extend(choose(lambda r: r.get("reasoning_type") == "enterprise-core-configuration", 15, "enterprise configuration"))
    selected.extend(choose(lambda r: r.get("reasoning_type") == "enterprise-component-topology", 15, "enterprise topology"))
    selected.extend(choose(lambda r: r.get("reasoning_type") == "enterprise-failure-recovery", 10, "enterprise recovery"))
    if len(selected) != limit or len({row["query_id"] for row in selected}) != limit:
        raise AssertionError("the stratified sample is not 100 unique rows")
    return sorted(selected, key=lambda row: str(row["query_id"]))


def tokens(text: str) -> list[str]:
    return [value.casefold() for value in TOKEN_RE.findall(str(text or ""))]


def multiset_f1(reference: str, candidate: str) -> float:
    ref = Counter(tokens(reference))
    cand = Counter(tokens(candidate))
    overlap = sum((ref & cand).values())
    if not ref or not cand:
        return 0.0
    precision, recall = overlap / sum(cand.values()), overlap / sum(ref.values())
    return 2 * precision * recall / (precision + recall) if precision + recall else 0.0


def lcs_length(left: list[str], right: list[str]) -> int:
    previous = [0] * (len(right) + 1)
    for token_left in left:
        current = [0]
        for index, token_right in enumerate(right, 1):
            current.append(previous[index - 1] + 1 if token_left == token_right else max(previous[index], current[-1]))
        previous = current
    return previous[-1]


def rouge_l_f1(reference: str, candidate: str) -> float:
    ref, cand = tokens(reference), tokens(candidate)
    if not ref or not cand:
        return 0.0
    common = lcs_length(ref, cand)
    precision, recall = common / len(cand), common / len(ref)
    return 2 * precision * recall / (precision + recall) if precision + recall else 0.0


def request_json(url: str, key: str, payload: dict[str, Any], retries: int = 4) -> dict[str, Any]:
    # ``extra_body`` is the OpenAI SDK extension spelling.  This runner uses
    # stdlib HTTP, so it expands the extension into the official raw JSON
    # field before transmission (``thinking`` at the request top level).
    wire_payload = dict(payload)
    extra_body = wire_payload.pop("extra_body", None)
    if isinstance(extra_body, dict):
        wire_payload.update(extra_body)
    body = json.dumps(wire_payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(url, data=body, method="POST", headers={"Content-Type": "application/json", "Authorization": f"Bearer {key}", "User-Agent": "KnowledgeHub-ESWA-Eval/2"})
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=180) as response:
                parsed = json.loads(response.read().decode("utf-8"))
            if not isinstance(parsed, dict):
                raise ValueError("DeepSeek response is not a JSON object")
            return parsed
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, json.JSONDecodeError, ValueError) as exc:
            last_error = exc
            if attempt + 1 < retries:
                time.sleep(min(30.0, 2.0 ** attempt))
    raise RuntimeError(f"DeepSeek request failed after {retries} attempts: {type(last_error).__name__}: {last_error}")


def message_and_usage(response: dict[str, Any]) -> tuple[str, str, dict[str, Any]]:
    choices = response.get("choices") or []
    if not choices:
        raise ValueError("DeepSeek response contains no choices")
    message = choices[0].get("message") or {}
    content = str(message.get("content") or "")
    reasoning = str(message.get("reasoning_content") or "")
    usage = response.get("usage") or {}
    return content, reasoning, usage if isinstance(usage, dict) else {}


def context_text(ids: list[str], corpus: dict[str, dict[str, Any]]) -> str:
    if not ids:
        return "(No external context was supplied.)"
    chunks = []
    for index, sid in enumerate(ids, 1):
        row = corpus.get(sid)
        if row:
            chunks.append(f"[Context {index} | segment={sid}]\n{row.get('content', '')}")
    return "\n\n".join(chunks) or "(No external context was supplied.)"


def generation_payload(question: str, context: str) -> dict[str, Any]:
    return {
        "model": MODEL,
        "messages": [
            {"role": "system", "content": "You are a grounded enterprise knowledge assistant. Answer only with claims supported by the supplied context. If the context is empty or insufficient, say so explicitly. Keep the answer concise and cite segment identifiers when useful."},
            {"role": "user", "content": f"Question:\n{question}\n\nRetrieved context:\n{context}"},
        ],
        "temperature": 0.2,
        "extra_body": {"thinking": {"type": "disabled"}},
    }


def judge_payload(question: str, answer: str, reference: str, context: str) -> dict[str, Any]:
    rubric = {
        "faithfulness": "0 to 1: every material claim is supported by the supplied context; unsupported claims reduce the score.",
        "relevance": "0 to 1: the response directly answers the question without irrelevant content.",
        "factual_correctness": "0 to 1: the response agrees with the reference answer on the central conclusion.",
    }
    return {
        "model": MODEL,
        "messages": [
            {"role": "system", "content": "You are a strict RAG evaluation judge. Return only a JSON object with numeric scores in [0,1] and a short explanation. Do not reward plausible but unsupported claims."},
            {"role": "user", "content": f"Question:\n{question}\n\nReference answer:\n{reference}\n\nCandidate answer:\n{answer}\n\nEvidence context:\n{context}\n\nRubric:\n{json.dumps(rubric, ensure_ascii=False)}\n\nJSON schema: {{\"faithfulness\": 0.0, \"relevance\": 0.0, \"factual_correctness\": 0.0, \"explanation\": \"...\"}}"},
        ],
        "reasoning_effort": "high",
        "extra_body": {"thinking": {"type": "enabled"}},
    }


def parse_judge(content: str) -> dict[str, Any]:
    candidates = re.findall(r"\{.*?\}", content or "", flags=re.DOTALL)
    for candidate in reversed(candidates):
        try:
            value = json.loads(candidate)
        except json.JSONDecodeError:
            continue
        if isinstance(value, dict) and all(key in value for key in ("faithfulness", "relevance", "factual_correctness")):
            parsed = {key: max(0.0, min(1.0, float(value[key]))) for key in ("faithfulness", "relevance", "factual_correctness")}
            parsed["explanation"] = str(value.get("explanation", ""))
            return parsed
    raise ValueError("judge content did not contain the required JSON scores")


def usage_value(usage: dict[str, Any], key: str) -> int | None:
    value = usage.get(key)
    if isinstance(value, (int, float)):
        return int(value)
    return None


def nested_usage_value(usage: dict[str, Any], parent: str, key: str) -> int | None:
    value = usage.get(parent)
    if isinstance(value, dict) and isinstance(value.get(key), (int, float)):
        return int(value[key])
    return None


def run_case(row: dict[str, Any], group: str, eval_rows: dict[str, dict[str, dict[str, Any]]], corpus: dict[str, dict[str, Any]], url: str, key: str) -> dict[str, Any]:
    qid = str(row["query_id"])
    method = GROUP_METHOD.get(group)
    ids = [] if method is None else list((eval_rows.get(qid, {}).get(method) or {}).get("rankedSegmentIds", []))[:5]
    context = context_text(ids, corpus)
    started = time.perf_counter()
    try:
        generated = request_json(url, key, generation_payload(str(row["query"]), context))
        answer, generation_reasoning, generation_usage = message_and_usage(generated)
        judged = request_json(url, key, judge_payload(str(row["query"]), answer, str(row.get("gold_answer", "")), context))
        judge_content, judge_reasoning, judge_usage = message_and_usage(judged)
        judge_scores = parse_judge(judge_content)
        record = {
            "schemaVersion": "eswa.e2e_generation.v1",
            "status": "OK",
            "queryId": qid,
            "group": group,
            "track": row.get("track"),
            "sourceDataset": row.get("source_dataset"),
            "reasoningType": row.get("reasoning_type"),
            "question": row.get("query"),
            "contextSegmentIds": ids,
            "answer": answer,
            "referenceAnswer": row.get("gold_answer"),
            "judgeScores": judge_scores,
            "judgeExplanation": judge_scores.get("explanation", ""),
            "judgeReasoningContent": judge_reasoning,
            "generationReasoningContent": generation_reasoning,
            "rouge1": multiset_f1(str(row.get("gold_answer", "")), answer),
            "rougeL": rouge_l_f1(str(row.get("gold_answer", "")), answer),
            "tokenF1": multiset_f1(str(row.get("gold_answer", "")), answer),
            "promptTokens": usage_value(generation_usage, "prompt_tokens"),
            "completionTokens": usage_value(generation_usage, "completion_tokens"),
            "thinkingTokens": nested_usage_value(judge_usage, "completion_tokens_details", "reasoning_tokens"),
            "judgePromptTokens": usage_value(judge_usage, "prompt_tokens"),
            "judgeCompletionTokens": usage_value(judge_usage, "completion_tokens"),
            "latencySeconds": time.perf_counter() - started,
            "protocol": {"model": MODEL, "generationThinking": "disabled", "judgeThinking": "enabled", "judgeReasoningEffort": "high", "sdkThinkingParameter": "extra_body", "wireThinkingParameter": "thinking"},
        }
        return record
    except Exception as exc:
        return {"schemaVersion": "eswa.e2e_generation.v1", "status": "ERROR", "queryId": qid, "group": group, "track": row.get("track"), "reasoningType": row.get("reasoning_type"), "question": row.get("query"), "contextSegmentIds": ids, "error": f"{type(exc).__name__}: {exc}", "latencySeconds": time.perf_counter() - started, "protocol": {"model": MODEL, "generationThinking": "disabled", "judgeThinking": "enabled", "judgeReasoningEffort": "high", "sdkThinkingParameter": "extra_body", "wireThinkingParameter": "thinking"}}


def mean(records: list[dict[str, Any]], key: str) -> float | None:
    values = [float(row[key]) for row in records if isinstance(row.get(key), (int, float))]
    return statistics.fmean(values) if values else None


def render_latex(summary: list[dict[str, Any]], status: str) -> str:
    def cell(value: Any) -> str:
        return "--" if value is None else f"{float(value):.4f}" if isinstance(value, (float, int)) else str(value).replace("_", r"\_")
    lines = [
        "% Auto-generated by scripts/eval_suite/eval_e2e_generation.py.",
        "% Values are read from eval_e2e_results_100.jsonl; no metric is hand-entered.",
        r"\begin{table}[htbp]", r"\centering", r"\footnotesize",
        r"\caption{100-question end-to-end generation and LLM-as-a-judge evaluation.}",
        r"\label{tab:e2e-generation-100}", r"\resizebox{\linewidth}{!}{%", r"\begin{tabular}{lrrrrrr}", r"\toprule",
        "Pipeline & Faithfulness & Relevance & Factual correctness & ROUGE-L & Token F1 & Prompt tokens \\\\", r"\midrule",
    ]
    for row in summary:
        values = [row["group"].replace("&", r"\&"), cell(row["faithfulness"]), cell(row["relevance"]), cell(row["factual_correctness"]), cell(row["rougeL"]), cell(row["tokenF1"]), cell(row["promptTokens"])]
        lines.append(" & ".join(values) + r" \\")
    lines += [r"\bottomrule", r"\end{tabular}}", r"\begin{flushleft}\scriptsize", f"Run status: {status}. Generation uses {MODEL} with thinking disabled and temperature 0.2; the judge uses thinking enabled and reasoning effort high without temperature.", r"\end{flushleft}", r"\end{table}", "", r"\begin{table}[htbp]", r"\centering", r"\footnotesize", r"\caption{Token and latency measurements for the same 100-question end-to-end run.}", r"\label{tab:e2e-resources-100}", r"\resizebox{\linewidth}{!}{%", r"\begin{tabular}{lrrrr}", r"\toprule", r"Pipeline & Prompt tokens & Completion tokens & Thinking tokens & Latency (s) \\", r"\midrule"]
    for row in summary:
        values = [row["group"].replace("&", r"\&"), cell(row["promptTokens"]), cell(row["completionTokens"]), cell(row["thinkingTokens"]), cell(row["latencySeconds"])]
        lines.append(" & ".join(values) + r" \\")
    lines += [r"\bottomrule", r"\end{tabular}}", r"\end{table}", ""]
    return "\n".join(lines)


def run(args: argparse.Namespace) -> dict[str, Any]:
    key, base = credentials(args.root)
    args.results.parent.mkdir(parents=True, exist_ok=True)
    if not key:
        blocked = {"schemaVersion": "eswa.e2e_generation.v1", "status": "E2E_BLOCKED_MISSING_API_KEY", "model": MODEL, "requestedQueries": 100, "groups": list(GROUPS), "results": str(args.results)}
        args.results.write_text(json.dumps(blocked, ensure_ascii=False) + "\n", encoding="utf-8")
        args.summary.write_text("status\nE2E_BLOCKED_MISSING_API_KEY\n", encoding="utf-8")
        args.latex.write_text(render_latex([], "E2E_BLOCKED_MISSING_API_KEY"), encoding="utf-8")
        return blocked
    benchmark = read_jsonl(args.benchmark)
    sample = sample_rows(benchmark, 100)
    corpus = {str(row["segmentId"]): row for row in read_jsonl(args.corpus)}
    eval_rows = read_eval_rows(args.offline_results)
    url = base + "/chat/completions"
    records: list[dict[str, Any]] = []
    jobs = [(row, group) for row in sample for group in GROUPS]
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = [executor.submit(run_case, row, group, eval_rows, corpus, url, key) for row, group in jobs]
        for future in as_completed(futures):
            records.append(future.result())
    records.sort(key=lambda row: (str(row.get("queryId")), GROUPS.index(str(row.get("group")))))
    with args.results.open("w", encoding="utf-8") as handle:
        for row in records:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
    summary: list[dict[str, Any]] = []
    for group in GROUPS:
        rows = [row for row in records if row.get("group") == group]
        ok = [row for row in rows if row.get("status") == "OK"]
        summary.append({"group": group, "status": "OK" if len(ok) == 100 else "PARTIAL", "queries": len(rows), "success": len(ok), "failed": len(rows) - len(ok), "faithfulness": mean([{"v": row["judgeScores"]["faithfulness"]} for row in ok], "v"), "relevance": mean([{"v": row["judgeScores"]["relevance"]} for row in ok], "v"), "factual_correctness": mean([{"v": row["judgeScores"]["factual_correctness"]} for row in ok], "v"), "rouge1": mean(ok, "rouge1"), "rougeL": mean(ok, "rougeL"), "tokenF1": mean(ok, "tokenF1"), "promptTokens": mean(ok, "promptTokens"), "completionTokens": mean(ok, "completionTokens"), "thinkingTokens": mean(ok, "thinkingTokens"), "judgePromptTokens": mean(ok, "judgePromptTokens"), "judgeCompletionTokens": mean(ok, "judgeCompletionTokens"), "latencySeconds": mean(ok, "latencySeconds")})
    fields = list(summary[0]) if summary else ["status"]
    with args.summary.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader(); writer.writerows(summary)
    status = "OK" if all(row["status"] == "OK" for row in summary) else "PARTIAL"
    args.latex.parent.mkdir(parents=True, exist_ok=True)
    args.latex.write_text(render_latex(summary, status), encoding="utf-8")
    return {"status": status, "records": len(records), "successful": sum(row["status"] == "OK" for row in records), "results": str(args.results), "summary": str(args.summary), "latex": str(args.latex)}


def main() -> None:
    if sys.prefix == sys.base_prefix:
        raise RuntimeError("Use the project .venv Python interpreter")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--benchmark", type=Path, default=ROOT / "backend/tests/src/test/resources/eval/v2/benchmark_300.jsonl")
    parser.add_argument("--corpus", type=Path, default=ROOT / "backend/tests/src/test/resources/eval/v2/corpus_300.jsonl")
    parser.add_argument("--offline-results", type=Path, default=ROOT / "backend/tests/evidence/eval_results_300_v2.jsonl")
    parser.add_argument("--results", type=Path, default=ROOT / "backend/tests/evidence/eval_e2e_results_100.jsonl")
    parser.add_argument("--summary", type=Path, default=ROOT / "backend/tests/evidence/eval_e2e_summary_100.csv")
    parser.add_argument("--latex", type=Path, default=ROOT / "article/generated_e2e_tables.tex")
    parser.add_argument("--workers", type=int, default=4)
    args = parser.parse_args()
    if args.workers < 1 or args.workers > 16:
        parser.error("--workers must be between 1 and 16")
    print(json.dumps(run(args), ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
