#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Q025 真实检索与端到端问答逐请求证据采集脚本"""
import json
import time
import urllib.request
import uuid

BASE_URL = "http://127.0.0.1:8098"

def http_post(url, payload, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers=headers)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8")), resp.getheaders()

def main():
    # 1. 登录
    login_res, _ = http_post(f"{BASE_URL}/login", {"username": "admin", "password": "admin123"})
    token = login_res.get("token")
    assert token, f"登录失败: {login_res}"
    print(f"[AUTH] 登录成功，获取 token 长度: {len(token)}")

    # 2. Q025 真实召回测试
    request_id = f"req-{uuid.uuid4()}"
    timestamp = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
    q025_payload = {
        "id": 1,
        "query": "Day 12 的日志记录如何区分已确认内容与待补充内容？",
        "topK": 10
    }
    print(f"\n[Q025 REQUEST] requestId={request_id}, timestamp={timestamp}")
    print(f"[Q025 PAYLOAD] {json.dumps(q025_payload, ensure_ascii=False)}")

    t0 = time.time()
    recall_res, headers = http_post(f"{BASE_URL}/kmc/knowledgeBase/recallTest?debug=true", q025_payload, token)
    cost_ms = (time.time() - t0) * 1000

    print(f"[Q025 RESPONSE] code={recall_res.get('code')}, msg={recall_res.get('msg')}, cost={cost_ms:.2f}ms")
    data = recall_res.get("data", [])
    print(f"[Q025 RESULT COUNT] {len(data)}")

    evidence = {
        "requestId": request_id,
        "timestamp": timestamp,
        "costMs": cost_ms,
        "query": q025_payload["query"],
        "kbId": q025_payload["id"],
        "topK": len(data),
        "results": []
    }

    print("\n--- 最终 Top-K 逐条切片来源与元数据明细 ---")
    vector_participated_count = 0
    for idx, item in enumerate(data, start=1):
        meta = item.get("metadata") or {}
        matched_paths = meta.get("matchedPaths", [])
        path_ranks = meta.get("pathRanks", {})
        path_scores = meta.get("pathScores", {})
        has_vector = "vector" in matched_paths or item.get("source") == "vector"
        if has_vector:
            vector_participated_count += 1

        rec = {
            "rank": idx,
            "segmentId": item.get("id"),
            "documentId": item.get("documentId"),
            "documentName": item.get("documentName"),
            "score": item.get("score"),
            "scoreType": item.get("scoreType"),
            "source": item.get("source"),
            "matchedPaths": matched_paths,
            "pathRanks": path_ranks,
            "pathScores": path_scores,
            "hasVectorContribution": has_vector,
            "contentSnippet": (item.get("content") or "")[:80].replace("\n", " ")
        }
        evidence["results"].append(rec)
        print(f"Rank #{idx:02d} | SegID: {rec['segmentId']} | Score: {rec['score']} ({rec['scoreType']}) | Source: {rec['source']} | MatchedPaths: {matched_paths} | PathRanks: {path_ranks}")
        print(f"         Snippet: {rec['contentSnippet']}")

    evidence["vectorParticipatedCount"] = vector_participated_count
    print(f"\n[VECTOR CONTRIBUTION AUDIT] 最终 Top-K 包含向量路贡献的切片数: {vector_participated_count}/{len(data)}")

    # 3. 输出完整证据 JSON
    out_file = "experiment/outputs/q025_e2e_evidence.json"
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(evidence, f, ensure_ascii=False, indent=2)
    print(f"[SAVED] 完整证据已落盘: {out_file}")

if __name__ == "__main__":
    main()
