#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P0-16 真实语料 qrel 检索评测（只读真实 API，不修改任何生产数据）。

用法:
  python3 experiment/scripts/eval_real_corpus_qrel.py \
      --base-url http://127.0.0.1:8098 \
      --qrel experiment/datasets/qrel_real_corpus_30.json \
      --out experiment/outputs/p016_real_qrel
"""
import argparse
import csv
import hashlib
import json
import math
import random
import statistics
import urllib.error
import urllib.request
from pathlib import Path


def http_json(url, payload, token=None, timeout=90):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def login(base_url, username, password):
    body = http_json(f"{base_url}/login", {"username": username, "password": password})
    token = body.get("token")
    if not token:
        raise SystemExit(f"login failed: {body}")
    return token


def canonical_hash(queries):
    payload = [{"query_id": q["query_id"], "kb_id": q["kb_id"], "query": q["query"]}
               for q in sorted(queries, key=lambda x: x["query_id"])]
    return hashlib.sha256(json.dumps(payload, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()


def dedupe(values):
    seen = set()
    out = []
    for v in values:
        if v is None:
            continue
        s = str(v)
        if s not in seen:
            seen.add(s)
            out.append(s)
    return out


def recall_at_k(ranked, gold, k):
    if not gold:
        return None
    return 1.0 if set(ranked[:k]) & set(gold) else 0.0


def mrr_at_k(ranked, gold, k):
    for i, v in enumerate(ranked[:k], start=1):
        if v in gold:
            return 1.0 / i
    return 0.0


def ndcg_at_k(ranked, gold, k):
    if not gold:
        return None
    dcg = sum(1.0 / math.log2(i + 1) for i, v in enumerate(ranked[:k], start=1) if v in gold)
    ideal_n = min(len(gold), k)
    idcg = sum(1.0 / math.log2(i + 1) for i in range(1, ideal_n + 1))
    return dcg / idcg if idcg > 0 else 0.0


def bootstrap_ci(values, n=1000, seed=42):
    values = [v for v in values if v is not None]
    if not values:
        return [None, None]
    rng = random.Random(seed)
    means = []
    for _ in range(n):
        sample = [values[rng.randrange(len(values))] for _ in range(len(values))]
        means.append(sum(sample) / len(sample))
    means.sort()
    return [round(means[int(0.025 * (n - 1))], 6), round(means[int(0.975 * (n - 1))], 6)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default="http://127.0.0.1:8098")
    ap.add_argument("--qrel", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--username", default="admin")
    ap.add_argument("--password", default="admin123")
    ap.add_argument("--top-k", type=int, default=10)
    args = ap.parse_args()

    qrel = json.loads(Path(args.qrel).read_text(encoding="utf-8"))
    queries = qrel["queries"]
    expected = canonical_hash(queries)
    if qrel.get("frozen_hash") != expected:
        raise SystemExit("INVALID_QREL_FROZEN_HASH: qrel content changed after freeze")

    token = login(args.base_url.rstrip("/"), args.username, args.password)
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    raw_path = out_dir / "raw_results.jsonl"
    rows = []
    with raw_path.open("w", encoding="utf-8") as f:
        for q in queries:
            url = f"{args.base_url.rstrip('/')}/kmc/knowledgeBase/recallTest?debug=true"
            try:
                body = http_json(url, {"id": q["kb_id"], "query": q["query"], "topK": args.top_k}, token)
            except Exception as e:  # noqa: BLE001
                body = {"code": 500, "msg": str(e), "data": []}
            data = body.get("data") or []
            doc_ranked = dedupe([r.get("documentId") for r in data])
            seg_ranked = dedupe([r.get("id") for r in data])
            row = {
                "query_id": q["query_id"],
                "kb_id": q["kb_id"],
                "query": q["query"],
                "gold_document_ids": q.get("gold_document_ids", []),
                "gold_segment_ids": q.get("gold_segment_ids", []),
                "api_code": body.get("code"),
                "result_count": len(data),
                "doc_ranked": doc_ranked,
                "seg_ranked": seg_ranked,
                "doc_recall@5": recall_at_k(doc_ranked, q.get("gold_document_ids", []), 5),
                "doc_recall@10": recall_at_k(doc_ranked, q.get("gold_document_ids", []), 10),
                "doc_mrr@10": mrr_at_k(doc_ranked, q.get("gold_document_ids", []), 10),
                "doc_ndcg@10": ndcg_at_k(doc_ranked, q.get("gold_document_ids", []), 10),
                "seg_recall@5": recall_at_k(seg_ranked, q.get("gold_segment_ids", []), 5),
                "seg_recall@10": recall_at_k(seg_ranked, q.get("gold_segment_ids", []), 10),
                "seg_mrr@10": mrr_at_k(seg_ranked, q.get("gold_segment_ids", []), 10),
                "top_docs": doc_ranked[:10],
            }
            rows.append(row)
            f.write(json.dumps(row, ensure_ascii=False) + "\n")

    metric_names = ["doc_recall@5", "doc_recall@10", "doc_mrr@10", "doc_ndcg@10",
                    "seg_recall@5", "seg_recall@10", "seg_mrr@10"]
    summary = {}
    for name in metric_names:
        vals = [r[name] for r in rows if r.get(name) is not None]
        summary[name] = {
            "mean": round(statistics.mean(vals), 6) if vals else None,
            "n": len(vals),
            "bootstrap_95ci": bootstrap_ci(vals),
        }
    errors = [r["query_id"] for r in rows if r.get("api_code") != 200 or r.get("result_count", 0) == 0]
    metrics = {
        "qrel": str(args.qrel),
        "frozen_hash": expected,
        "top_k": args.top_k,
        "query_count": len(rows),
        "failed_queries": errors,
        "metrics": summary,
    }
    (out_dir / "metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")
    with (out_dir / "metrics.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["metric", "mean", "n", "ci_low", "ci_high"])
        for name, m in summary.items():
            w.writerow([name, m["mean"], m["n"], m["bootstrap_95ci"][0], m["bootstrap_95ci"][1]])
    print(json.dumps(metrics, ensure_ascii=False, indent=2))
    if errors:
        raise SystemExit(f"RUN_API_INCOMPLETE: {errors}")


if __name__ == "__main__":
    main()
