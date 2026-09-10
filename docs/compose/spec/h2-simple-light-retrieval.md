---
feature: h2-simple-light-retrieval
status: designed
updated: 2026-09-10
branch: feat/h2-simple-light-retrieval
commits: 
---

# H2 SIMPLE Lightweight Retrieval

## Report

## [S1] Problem

`QueryRouter` 将 `length < 10` 的查询判为 SIMPLE；`RagRetrievalService` 在非 debug 下直接返回空上下文。组织库短实体/制度名查询因此完全不走 RAG。

## [S2] Design

- 配置：`qknow.rag.simple.light-retrieval`（默认 false）+ `qknow.rag.simple.light-top-k`（默认 5）。
- SIMPLE 且开关开启：仅 keyword（复用现有 KeywordRetriever 路径或等价 SQL）轻检索 topK，**零 LLM**；仍做权限过滤。
- 跳过：rewrite、Router LLM（已在 classify 内）、实体抽取、CRAG、web、重型 rerank、ColBERT。
- SIMPLE 且开关关闭：现行为（空）。
- 观测：debugInfo / 日志记录 `simpleLightRetrieval=true` 与命中数。

**不可变量**：既有 golden qrel、CRAG、RRF 全局默认、ColBERT、切块、生产 yml 默认。

## [S3] Out of Scope

- 修改 Router 长度阈值语义
- 训练复杂度分类器
- 改生产默认为 true
- 修 recallTest 丢 context 接线（另票）

## Tasks

- [ ] T1: 冻结短查询切片 fixture — acceptance: 新 jsonl 文件，不改旧 golden (covers: S2)
- [ ] T2: A0 空召回基线指标 — acceptance: 报告含 Hit@10≈0 (covers: S2)
- [ ] T3: 实现 SIMPLE 轻检索分支 + 单测 — acceptance: 开关 false 行为不变；true 零 LLM 返回 keyword 命中 (covers: S2)
- [ ] T4: A0/A1 消融报告 — acceptance: evidence JSON + 判据 (covers: S2)
- [ ] T5: 停 — 不改生产默认 (covers: S3)
