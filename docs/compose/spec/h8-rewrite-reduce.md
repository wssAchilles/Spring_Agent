---
feature: h8-rewrite-reduce
status: delivered
updated: 2026-09-10
branch: feat/h8-rewrite-reduce
commits: 3c96f6e..HEAD
---

# H8 Query Transform LLM Reduce

## Report

**What was built** — `QueryTransformConfig.enabled` 默认改为 **false**；`application-dev.yml` 同步关闭。控制面 `rewriteQuery` / HyDE / multi-query 在默认配置下不再发起 LLM。

**Verification** — `QueryTransformH8GateTest` 4/4 PASS（默认关、strategy=none/hyde、compress 关闭）。

**Journey log**
1. 入口 rewrite 原先 enabled=true + strategy=rewrite，每次 recall 都多 1 次 LLM。
2. 与 H3 叠加后，MEDIUM 路径同步 LLM 进一步下降。
3. 如需 rewrite：yml 设 `hermes.rag.query-transform.enabled: true`。

## [S1] Problem

每次知识库召回额外一次 rewrite LLM。

## [S2] Design

默认关闭 query-transform；strategy=none/hyde 时入口 rewrite 亦跳过。

## [S3] Out of Scope

改 VectorStore 内部二次 embed、改 RRF/CRAG/H1–H3。

## Tasks

- [x] T1: 默认 enabled=false + yml — acceptance: 单测绿 (covers: S2)
