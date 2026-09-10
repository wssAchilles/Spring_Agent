---
feature: a1-colbert-fail-closed
status: in-progress
updated: 2026-09-10
branch: feat/a1-colbert-fail-closed
commits: 
---

# A1 ColBERT Fail-Closed

## Report

## [S1] Problem

`RagRerankService` 在 RRF 融合后无条件调用 `ColbertScorer.rerank(..., topK*3)`。当 `hermes.rag.colbert.enabled=true` 但 embedding 四元组（platform/baseUrl/apiKey/model）不完整时，`encodeTokens` 回落到 `tokenVectorHash` 伪向量。伪向量 MaxSim 近似随机，却截断候选集，系统性丢弃 RRF 已排好的高相关文档（假设 H1）。

## [S2] Design

**契约**

- 配置：`hermes.rag.colbert.skip-when-no-embedding`（默认 `false`，保持 A0 行为）。
- `skip=true` 且缺少真实 embedding 配置 → **不粗排、不截断**，identity 返回输入候选（顺序保持）。
- `skip=true` 且配置齐全但编码 API 失败 → **禁止 hash 回落**；抛出/让上层 `colbertCoarseRerank` 既有 catch 返回原始候选。
- `skip=false` → 现行为不变（hash 粗排 + topK*3）。
- 观测：跳过时 `RagFallbackMonitor.record("colbert","skipped_no_embedding",...)`；日志 info。
- 探测 API：`ColbertScorer.isRealEmbeddingConfigured()` 供调用方与测试使用。

**不可变量**：topK、RRF、CRAG、Router、切块、golden/qrel、context 预算、生产 yml 业务默认。

## [S3] Out of Scope

- 真 ColBERT checkpoint / Jina / PLAID
- 修改 QueryRouter / CRAG / 切块 / RagContextBuilder
- 改生产默认开关为 true
- 调 topK 或截断倍数

## Tasks

- [ ] T1: 记录冻结参数与 A0 基线可读性 — acceptance: 附录冻结表填完或环境失败码明确 (covers: S2)
- [ ] T2: 实现 ColbertScorer skip-when-no-embedding — acceptance: 单测覆盖 skip=true 无配置→identity、skip=false→hash、配置齐全→真编码路径不触发 skip (covers: S2)
- [ ] T3: RagRerankService 观测记录 — acceptance: skip 路径写入 RagFallbackMonitor (covers: S2)
- [ ] T4: 模块编译 + 相关单测通过 — acceptance: mvn test 绿 (covers: S2)
- [ ] T5: A0/A1 指标对比（若环境允许）— acceptance: 附录 B 有结果或 INSUFFICIENT 并说明 (covers: S2)
