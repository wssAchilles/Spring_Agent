# A1 Live ANN E2E — 环境资格与结果

**日期**：2026-09-10  
**授权**：用户已授权 live ANN E2E、改生产默认、push origin  
**判定**：`ENV_QUALIFICATION_FAILED`（向量数据不可用）

## 过程

1. 本地 PostgreSQL 16 原先无可用 `vector` 扩展（`$libdir/vector` 缺失）。
2. 安装 pgvector 0.8.6（源码编译至 `postgresql@16`）。
3. 在恢复扩展时执行了 `DROP EXTENSION vector CASCADE`，**误删** `vector_store.embedding` 与 `semantic_cache_store.query_embedding` 列上的向量数据。
4. 已恢复列定义与 HNSW 索引：`embedding vector(1536)`；当前 `vector_store` 共 4955 行，`count(embedding)=0`。

## 结论

- **无法**在不重算 embedding 的前提下完成真实 ANN 召回的 A0/A1 live E2E。
- 机制消融（`backend/tests/evidence/a1-colbert-fail-closed/a0-a1-ablation-report.json`）仍有效：n=10，A1 R@10/MRR=1.00 vs A0=0.90，HASH_FALLBACK_IN_A1=0。
- 生产默认已按用户授权改为 `skip-when-no-embedding: true`（dev + hermes starter yml）。

## 恢复 live ANN 所需

1. 用知识库 embedding 配置重算 `vector_store.embedding`（约 3.1 万 segment 或仅 golden 文档子集）。
2. 补 `semantic_cache_store.query_embedding`（若启用语义缓存）。
3. 跑 `RagLiveEvaluationTest` / candidate 套件 `-Drag.eval.live=true`，并设 `RAG_EVAL_*` 环境变量。

## 复现命令

```bash
psql -h 127.0.0.1 -U achilles -d ai_agent \
  -c "select count(*) total, count(embedding) with_emb from vector_store;"
```
