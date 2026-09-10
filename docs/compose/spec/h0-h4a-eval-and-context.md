---
feature: h0-h4a-eval-and-context
status: delivered
updated: 2026-09-10
branch: feat/h0-h4a-eval-and-context
commits: 45ea46e..HEAD
---

# H0 Eval Gate + H4a Context Wiring

## Report

**What was built** — H0: frozen `rag-eval-holdout-v1.jsonl` (41 cases, selection≥20 / holdout≥12, medium+short) and `LiveRetrievalMetrics` (Hit@5/@10, MRR@10, NDCG@10). H4a: `RetrieveResultRespVO`/`RetrieveResult.ragContext` 由 `RagResult.getContext()` 写入首条结果；Agent 优先注入预算化 context，否则回退裸拼。

**Verification** — `EvalDatasetSplitTest` 2/2 PASS；`AgentRagContextPreferenceTest` 2/2 PASS。

**Journey log**
1. 指标需支持 `Day01` ↔ `Day01.md` 前缀匹配，不能只做全等。
2. context 只挂在列表首条，避免缓存 JSON 膨胀。
3. 未改 golden 旧文件；未改预算默认。

## [S1] Problem

评估缺 holdout；生产 Agent 丢弃 RagContextBuilder 输出。

## [S2] Design

见 Report；默认行为兼容：无 ragContext 时与旧行为一致。

## [S3] Out of Scope

改 max-bytes 默认、live 全量 ANN 门禁 CI、多轮 history。

## Tasks

- [x] T1: 冻结 eval 数据集 split — acceptance: selection/holdout 校验测试绿 (covers: S2)
- [x] T2: LiveRetrievalMetrics — acceptance: 人工分值一致 (covers: S2)
- [x] T3: VO/DTO/ServiceImpl/Agent 接线 — acceptance: 单测优先 budgeted context (covers: S2)
