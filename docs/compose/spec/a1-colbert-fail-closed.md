---
feature: a1-colbert-fail-closed
status: delivered
updated: 2026-09-10
branch: feat/a1-colbert-fail-closed
commits: 5c84bb0..1a3c3d8
---

# A1 ColBERT Fail-Closed

## Report

**What was built** — 为 ColBERT 粗排增加 `hermes.rag.colbert.skip-when-no-embedding`（默认 `false`，A0 行为不变）。开启后：无真实 token embedding 配置时 identity 跳过粗排；配置齐全但编码异常或空响应时 fail-closed 抛错，禁止 `tokenVectorHash` 伪向量回落；`RagRerankService` 在 `enabled && skip && !configured` 时写入 `RagFallbackMonitor` 与 info 日志。未改 topK/RRF/CRAG/Router/golden/生产 yml 默认。

**Verification** — `mvn -pl tests -am -Dtest=ColbertScorerTest -Dsurefire.failIfNoSpecifiedTests=false test` → PASS，Tests run: 13, Failures: 0, Errors: 0。独立 review 首轮发现空响应 hash 旁路（critical），已修复并复审 PASS。机制消融 `ColbertFailClosedAblationEvaluationTest`（`-Dqknow.rag.colbert.ablation=true`）→ PASS：n=10，HASH_FALLBACK_IN_A1=0，A0 R@10/MRR=0.90 vs A1=1.00（Δ+0.10），A1 p95 4ms vs A0 52ms；关键 case `eval-006` A0 全 miss / A1 全 hit。证据级别 **MECHANISM_ABLATION**（非全链路 live ANN），**未改生产默认**。报告：`backend/tests/evidence/a1-colbert-fail-closed/a0-a1-ablation-report.json`。

**Journey log**
1. 首版只堵了「未配置」与「异常」路径，review 发现「空 embedding 响应」仍 hash —— fail-closed 必须覆盖所有编码失败入口。
2. `EmbeddingResponse.from` 在当前 Spring AI 版本不存在，应用 `new EmbeddingResponse(List)`。
3. Service 层短路会绕过 scorer 内日志，观测需在 service 补齐；monitor 仅在 `enabled=true` 时记录以避免噪声。
4. Surefire 工作目录是模块 `backend/tests`，证据路径需按 cwd 叶子名解析，否则写到嵌套 `backend/tests/backend/...`。
5. 机制消融证明「无 embedding 时 hash 截断会挤掉相关候选」；完整 live ANN E2E 仍缺 holdout，不得据此改生产默认。
4. Live A0/A1 消融需完整冻结旗标与 DB，单测通过不能替代 H1 运行证据。

## [S1] Problem

`RagRerankService` 在 RRF 融合后无条件调用 `ColbertScorer.rerank(..., topK*3)`。当 `hermes.rag.colbert.enabled=true` 但 embedding 四元组（platform/baseUrl/apiKey/model）不完整时，`encodeTokens` 回落到 `tokenVectorHash` 伪向量。伪向量 MaxSim 近似随机，却截断候选集，系统性丢弃 RRF 已排好的高相关文档（假设 H1）。

## [S2] Design

**契约**

- 配置：`hermes.rag.colbert.skip-when-no-embedding`（默认 `false`，保持 A0 行为）。
- `skip=true` 且缺少真实 embedding 配置 → **不粗排、不截断**，identity 返回输入候选（顺序保持）。
- `skip=true` 且配置齐全但编码 API 失败/空响应 → **禁止 hash 回落**；抛 ISE，上层 `colbertCoarseRerank` 既有 catch 返回原始候选。
- `skip=false` → 现行为不变（hash 粗排 + topK*3；空响应仍可 hash）。
- 观测：service 在 `enabled && skip && !configured` 时 `RagFallbackMonitor.record("colbert","skipped_no_embedding",...)` + info 日志。
- 探测 API：`ColbertScorer.isRealEmbeddingConfigured()` / `getConfig()`。

**不可变量**：topK、RRF、CRAG、Router、切块、golden/qrel、context 预算、生产 yml 业务默认。

## [S3] Out of Scope

- 真 ColBERT checkpoint / Jina / PLAID
- 修改 QueryRouter / CRAG / 切块 / RagContextBuilder
- 改生产默认开关为 true
- 调 topK 或截断倍数
- Live golden A0/A1 指标门禁（环境不足，另批）

## Tasks

- [x] T1: 记录冻结参数与 A0 基线可读性 — acceptance: 附录冻结表填完或环境失败码明确 (covers: S2)
- [x] T2: 实现 ColbertScorer skip-when-no-embedding — acceptance: 单测覆盖 skip=true 无配置→identity、skip=false→hash、配置齐全→真编码路径不触发 skip (covers: S2)
- [x] T3: RagRerankService 观测记录 — acceptance: skip 路径写入 RagFallbackMonitor (covers: S2)
- [x] T4: 模块编译 + 相关单测通过 — acceptance: mvn test 绿 (covers: S2)
- [x] T5: A0/A1 指标对比（若环境允许）— acceptance: 附录 B 有结果或 INSUFFICIENT 并说明 (covers: S2)
