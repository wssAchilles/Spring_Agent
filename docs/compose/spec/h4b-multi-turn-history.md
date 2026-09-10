---
feature: h4b-multi-turn-history
status: delivered
updated: 2026-09-10
branch: feat/h4b-multi-turn-history
commits: 00dd091..HEAD
---

# H4b Multi-turn History into Retrieval

## Report

**What was built** — `IKmcApiService.recallTest(kbId, query, history)`；`search` 重载将 history 写入 `RetrieveResultReqVO`；Agent 将 `historyMessages` 传入预检索。有 history 且 `query-transform.enabled=true` 时才会走 `compressQuery`（H8 默认关，避免每轮 LLM）。

**Verification** — `MultiTurnHistoryCompressionTest` PASS；全模块编译 SUCCESS。

**Journey log**
1. history 字段早已在 ReqVO/`compressQuery` 中存在，缺的是 API/Agent 接线。
2. 与 H8 组合：默认不压缩；打开 transform 后才产生多轮 LLM 成本。

## [S1] Problem

Agent 预检索只传当前问题，指代消解失效。

## [S2] Design

history 经 KmcChatTurnDTO → ReqVO.history → 现有 compress 逻辑。

## [S3] Out of Scope

改 compress prompt、强制开启 transform、H5 关键词。

## Tasks

- [x] T1: API 重载 + search history — acceptance: 编译绿 (covers: S2)
- [x] T2: Agent 传 history — acceptance: 单测/编译绿 (covers: S2)
