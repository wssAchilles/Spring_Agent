# Phase 05 — CRAG AMBIGUOUS 合并路（算法候选）

> **状态**：**Delivered**  
> **日期**：2026-09-13  
> **门禁**：AGENTS.md；文献 A2 CRAG 2401.15884（VERIFIED）
> **验证**：CragAmbiguousContractTest (3/3 passed)

## A. 失败机制

`CragRetrievalEvaluator.evaluate` 在 gateMode=sample 时跳过 LLM 返回 CORRECT；即便调用 LLM，实现将 AMBIGUOUS 当作「不纠正」路径处理，与论文「AMBIGUOUS = 内部 refine ∪ 扩展检索」不符。

## B. Ledger（已深读）

- CRAG arXiv:2401.15884：AMBIGUOUS 双路合并；去掉该路 PopQA −0.9pt。  
- Adaptive-RAG / Self-RAG：本轮不引入训练。

## C–E. 最小算法

`sample` 模式下跳过时行为不变；**当真正 evaluate 且 label=AMBIGUOUS 时**：

1. 不清空上下文  
2. 标记 `cragAmbiguous=true`（debugInfo）并下发 `clarificationOptions` 细分候选项列表  
3. 支持通过 `ambiguous-expand`（默认关）进行受控的二次扩展合并（Refine ∪ Expand）  

对比旧逻辑：AMBIGUOUS 未触发纠正——实现缺口为「至少不丢上下文 + 可观测 + 注入澄清反思」。完整「扩展检索∪」需二次检索，作为 **默认关** 的可选开关 `qknow.rag.crag.ambiguous-expand:false`，避免未批的延迟/成本。

## F. 契约

| 项 | 值 |
|---|---|
| Baseline | AMBIGUOUS 不纠正、可保持上下文（现状若已如此则为加固观测） |
| Candidate | AMBIGUOUS → keep first + 标记；可选 expand；提供 clarificationOptions |
| 指标 | 单测：AMBIGUOUS 不清空；注入 clarificationOptions；3/3 绿 |
| 禁止 | 改 golden、改 CRAG 默认 sample rate、外网搜索 |

## Tasks

- [x] T1 读/改 `RagRetrievalService` CRAG 分支与 `CragRetrievalEvaluation`  
- [x] T2 单测 AMBIGUOUS keep-first + flag + clarificationOptions  
- [x] T3 可选 expand 开关默认 false + 单测  

## 不采用

web search 扩展、训练 T5 evaluator、Self-RAG。
