---
feature: h7-h9-rerank-grpc
status: delivered
updated: 2026-09-10
branch: feat/h7-h9-rerank-grpc
commits: 849e190..HEAD
---

# H7 Post-fusion Filter + H9 gRPC Cancel

## Report

**What was built** — H7: `qknow.rag.rerank.post-fusion-filter-enabled` 默认 **false**，不再把 RRF 分数当向量分过滤。H9: `HermesGrpcClient.chat` 使用 `ClientCall` + deadline（默认 120s），`Flux.onDispose` 时 `call.cancel`，客户端断连可取消上游。

**Verification** — `PostFusionFilterH7Test` PASS；kb/kmc 全量编译 SUCCESS。

**Journey log**
1. gRPC Listener 回调是 `onMessage` 不是 `onNext`。
2. Server-streaming Chat 仍可 `ClientCall.cancel`。
3. H6/H10/H11/H12 需重嵌/图谱证据/专项评测，本轮记为 Deferred。

## [S1] Problem

误过滤 RRF 候选；断连后 ReAct 继续跑。

## [S2] Design

见 Report。

## [S3] Out of Scope

H6 重嵌 A/B、H10 开启 GraphRAG、H11 记忆读、H12 真 ColBERT。

## Tasks

- [x] T1: H7 默认关 + 测试 (covers: S2)
- [x] T2: H9 deadline + cancel (covers: S2)
