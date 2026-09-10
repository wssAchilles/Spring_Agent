---
feature: h6-h10-h11-h12-launch
status: delivered
updated: 2026-09-10
branch: feat/h6-h11-chunk-memory
commits: 63b4c51..HEAD
---

# H6/H10/H11/H12 Launch Slice

## Report

**What was built**
- **H6**: `qknow.rag.chunk.child-tokens`（默认 **128**，历史行为不变）；调大需新文档重嵌才能 A/B。
- **H10**: GraphRAG 默认 **false** 保持；测试锁定默认。
- **H11**: 短期与关系库皆空时 `recallByScope` 注入长期记忆摘要（`hermes.memory.long-term.recall-on-empty` 默认 true，top-k=3）。
- **H12**: 完整 late-interaction 模型仍 **Deferred**（H1 fail-closed 已挡住伪向量；真 checkpoint/索引需独立预算）。

**Verification** — `DeferredDefaultsH10H6Test` + `LongTermRecallH11GateTest` PASS；hermes/kmc 编译 SUCCESS。

**Journey log**
1. H6 未重嵌前只能交配置，不能宣称 Recall 提升。
2. H11 不覆盖已有 short-term/DB history，避免双份上下文。
3. H12 依赖模型与索引，不在本轮假装交付。

## [S1] Problem

长期记忆只写不读；child chunk 不可配；Graph/真 ColBERT 边界不清。

## [S2] Design

见 Report。

## [S3] Out of Scope

重嵌全库、启用 GraphRAG 默认、下载 ColBERT checkpoint。

## Tasks

- [x] T1: H6 child-tokens 配置 (covers: S2)
- [x] T2: H11 long-term recall (covers: S2)
- [x] T3: H10 默认锁定测试 (covers: S2)
