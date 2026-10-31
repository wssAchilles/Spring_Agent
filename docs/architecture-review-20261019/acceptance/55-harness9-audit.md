# Hermes Harness² 阶段九（Stage 9）：只读源码法医审计报告

- **文档编号**：`55-harness9-audit.md`
- **所属阶段**：Stage 9 — 阶段 1（只读审计）
- **审计基线**：分支 `feat/hermes-harness-stage8`，提交 `198a6b69`
- **知识图谱与覆盖率核验**：已通过 `codebase-memory-mcp`（项目 `Spring_Agent`，索引代次 `2026-10-28T19:53:50Z`）执行 `search_graph` 与 `check_index_coverage`，并对 Stage 6–8 演进的全部源码文件完成 100% 源码级 Fallback 精读。

---

## 一、审计范围与已核验源码清单

1. [`53-harness8-taskbook.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/53-harness8-taskbook.md) 与 [`54-harness8-implementation.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/54-harness8-implementation.md)
2. [`UsageCapturingChatModel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/UsageCapturingChatModel.java)（行 1–146）
3. [`HarnessUsageSnapshot.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessUsageSnapshot.java)（行 1–30）
4. [`HarnessEvidenceRecord.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessEvidenceRecord.java)（行 1–205）
5. [`HarnessEvidenceCollector.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessEvidenceCollector.java)（行 1–217）
6. [`HarnessKernelBridge.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HarnessKernelBridge.java)（行 1–360）
7. [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java)（行 295–1350）
8. [`DeepSeekCostGovernor.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/cost/DeepSeekCostGovernor.java)（行 1–184）
9. [`AlgebraicHarnessMutator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/evolution/AlgebraicHarnessMutator.java) 与 [`ParetoFrontierComposer.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/evolution/ParetoFrontierComposer.java)
10. `src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/` 下全部现有离线评测类（`OfflineProbeRunner.java`, `DefaultOfflineProbeExecutor.java`, `PositionSwapBlindEvaluator.java`, `EvidenceDiffExtractor.java`）

---

## 二、七大必答审计问题逐项核验

### 问题 1：当前 usage 是否包含 `prompt / completion / cacheHit`？

- **审计结论**：**已包含 token 细项提取，但 `actualCostCny` 被硬编码为 `0.0D`（阻断项，必须在 Stage 9 修复）。**
- **源码铁证**：
  1. 在 [`UsageCapturingChatModel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/UsageCapturingChatModel.java#L82-L110) 的 `extractFromResponse(ChatResponse response)` 中：
     - 行 95–97：从 `Usage` 提取了 `promptTokens`（`getPromptTokens()`）、`completionTokens`（`getCompletionTokens()`）与 `totalTokens`（`getTotalTokens()`）；
     - 行 100–103：从 `usage.getNativeUsage()` 提取了 `prompt_cache_hit_tokens`（累加至 `totals.cacheHitTokens`）与 `reasoning_tokens`（累加至 `totals.reasoningTokens`）。
  2. 然而在 [`UsageCapturingChatModel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/UsageCapturingChatModel.java#L72-L79) 的 `snapshot()` 方法中：
     ```java
     return new HarnessUsageSnapshot(
             status,
             totals.promptTokens.get(),
             totals.completionTokens.get(),
             totals.totalTokens.get(),
             totals.reasoningTokens.get(),
             totals.cacheHitTokens.get(),
             0.0D);
     ```
     第 7 个参数 `actualCostCny` 被直接写死为 `0.0D`！这导致 [`HarnessEvidenceCollector.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessEvidenceCollector.java#L168) 写入 `HarnessEvidenceRecord.actualCostCny` 的值永远是 `0.0D`。
  3. 此外，[`DeepSeekCostGovernor.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/cost/DeepSeekCostGovernor.java#L34) 中的默认价格表 `new ModelPricingNano(100L, 500L, 2_000L)` 为历史占位值，未反映 DeepSeek 官方 `deepseek-flash` 的最新分时段价格（空闲 0.02 / 1.00 / 4.00 元/M，高峰 0.04 / 2.00 / 8.00 元/M），且 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java#L368) 调用 `recordUsage` 时将 `promptCacheHitTokens` 传为了 `0`。

---

### 问题 2：如何用官方价格计算 `actualCostCny`？

- **审计结论**：必须在 `UsageCapturingChatModel`（及 `ppc` 成本模块）中接入基于北京时间（`ZoneId.of("Asia/Shanghai")`）高峰/空闲时段的 DeepSeek 官方定价计算器。
- **精确计算逻辑**：
  1. 当 `UsageStatus == UsageStatus.UNKNOWN` 时：`actualCostCny = 0.0D`，同时在 `PpcProbeResult` 中显式标记 `costKnown = false`（或 `usageStatus = UNKNOWN`），严禁参与成本比较；
  2. 当 `UsageStatus == UsageStatus.KNOWN` 时：
     - 判定调用时刻 `Instant` 对应的 `ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Shanghai"))`：
       - 若为周一至周五（`DayOfWeek.MONDAY` 至 `FRIDAY`）且时间处于 `[09:00, 12:00)` 或 `[14:00, 18:00)`，则为 **高峰时段（`PEAK`）**：
         $$P_{\text{hit}} = 0.04,\quad P_{\text{miss}} = 2.00,\quad P_{\text{out}} = 8.00 \quad (\text{元} / 10^6\text{ tokens})$$
       - 其余时间（含周六、周日全天）为 **空闲时段（`OFF_PEAK`）**：
         $$P_{\text{hit}} = 0.02,\quad P_{\text{miss}} = 1.00,\quad P_{\text{out}} = 4.00 \quad (\text{元} / 10^6\text{ tokens})$$
     - 计算缓存命中与未命中输入 token：
       $$\text{hit} = \min(\text{promptTokens}, \max(0L, \text{cacheHitTokens})),\quad \text{miss} = \max(0L, \text{promptTokens} - \text{hit})$$
     - 计算人民币实际费用：
       $$\text{actualCostCny} = \frac{\text{hit} \times P_{\text{hit}} + \text{miss} \times P_{\text{miss}} + \text{completionTokens} \times P_{\text{out}}}{1\_000\_000.0}$$

---

### 问题 3：每个 probe 可能触发多少次模型调用？

- **审计结论**：在 `SINGLE` 模式（`hermes.orchestration.enabled=false`、`planSolveConfig.enabled=false`、`hermes.crag.secondary.enabled=false`）下，单个 Probe 触发 **1 至 3 次** 底层模型调用（极端带 1 次工具重试时最多 **4 次**）。
- **源码调用链分析**：
  1. 在 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java#L1121-L1150) 中，`buildReactAgent` 挂载了 `ModelCallLimitHook.builder().runLimit(effectiveMaxSteps).build()`。
  2. 对于无工具调用的纯生成或直接基于注入上下文回答的 Probe，`ReactAgent` 仅执行 **1 次** `ChatModel.stream` 调用即产生 최종回答。
  3. 对于挂载了 `ToolCallback` 的任务（如 `kb-qa-01`、`tool-route-02`、`param-guard-03`、`comp-rag-tool-05`），`ReactAgent` 第 1 次调用模型生成 `ToolCall`，本地执行工具后，第 2 次调用模型生成最终回复（共 **2 次** 模型调用；若发生二次工具调用则为 **3 次**）。
  4. `UsageCapturingChatModel.call` 与 `stream` 每次被调用时，均可在内部计数器 `modelCallCount` 上原子自增 `+1`，并实时向全局预算看门狗汇报，确保 15 个 Probe 的总模型调用次数严格受控在 **`20 ~ 35` 次**（硬上限 **60 次**）。

---

### 问题 4：如何注入 RAG / tool / history？

- **审计结论**：找出了 Stage 5 证据中 `ragDocumentCount=0, toolExecutions=[]` 的直接根因——旧版 [`DefaultOfflineProbeExecutor.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/probe/DefaultOfflineProbeExecutor.java#L72-L85) 构造 `ChatRequest` 时**只传入了 `question` 和 `systemPrompt`，遗漏了 `ragContexts`、`toolMethodIds` 和 `history`**！
- **源码级真实注入机制（Stage 8 已打通底座，Stage 9 在 `PpcOrchestrator` 中组装传入）**：
  1. **RAG 注入**：通过 `ChatRequest.newBuilder().addAllRagContexts(ragContexts)` 传入非空 `RAGContext`（含 `knowledgeBaseId`、`knowledgeBaseName`、`chunks`）。[`HarnessKernelBridge.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HarnessKernelBridge.java#L279-L294) 的 `CapabilityContextAssembler` 会将其装配为结构化 `[RAG Knowledge Context]` 注入请求，且 `AgentOrchestrator.prepareRagTools` 会自动生成知识库检索工具并由 `collector.recordRagAttribution` 记录真实 `ragChunkIds` 与 `ragDocumentCount`。
  2. **Tool 注入**：通过 `ChatRequest.newBuilder().addAllToolMethodIds(toolMethodIds)` 传入工具 ID，并在 `AgentOrchestrator` 绑定的 `ToolCallbackResolver` 中注册确定性本地沙盒 `ToolCallback`（如 `banking_transaction_query`、`banking_account_balance`、`calendar_event_creator`、`arch_metrics_calculator`）。模型触发 ToolCall 时，`AgentOrchestrator.wrapToolCallbackWithAudit` 会经过 `HoareTripleToolGuard` 校验并调用 `collector.recordToolExecution` 记录真实工具调用。
  3. **History 注入**：通过 `ChatRequest.newBuilder().addAllHistory(historyMessages)` 传入多轮 `ChatMessage`（`role="user"` / `"assistant"`）。`CapabilityContextAssembler` 与 `AgentOrchestrator.buildHistoryMessages` 会将其注入真实请求上下文。

---

### 问题 5：哪些 case 必须降级 `PROMPT_ONLY`？

- **审计结论**：
  1. 在 Stage 9 标准配置下，5 个基准任务分别绑定真实能力夹具：
     - `kb-qa-01` $\to$ `RAG`（绑定订单支付幂等键规范 `RAGContext`）；
     - `tool-route-02` $\to$ `TOOL`（绑定 `banking_transaction_query` 与 `banking_account_balance` 工具）；
     - `param-guard-03` $\to$ `TOOL`（绑定带必填日期参数契约的 `calendar_event_creator` 工具）；
     - `ctx-history-04` $\to$ `HISTORY`（绑定 2 轮 4 条关于服务网格延迟瓶颈的真实 `ChatMessage` 历史）；
     - `comp-rag-tool-05` $\to$ `COMPOSITE`（同时绑定微服务架构规范 `RAGContext` 与 `arch_metrics_calculator` 工具）。
  2. **强制降级规则**：若任一任务在执行时未能提供非空 `RAGContext`、未能解析到非空 `ToolCallback`、且未能提供非空 `history`（例如在仅测试纯提示词的特殊配置或夹具缺失时），[`HarnessKernelBridge.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HarnessKernelBridge.java#L204-L225) 与 `PpcOrchestrator` 必须将其 `effectiveCapabilityTag` 显式降级为 **`PROMPT_ONLY`**（在 `ENFORCE` 模式下若声明了强能力标签却缺失对应载荷则直接 fail-close 拒绝执行），并在 `PpcProbeResult` 与实验报告中诚实标注，严禁虚报能力覆盖。

---

### 问题 6：如何匿名选择与防泄漏？

- **审计结论**：对齐本地 Harness² 源码 [`selection.py`](file:///Users/achilles/Documents/优秀开源项目/harness2/harness2/core/selection.py) 与 [`context.py`](file:///Users/achilles/Documents/优秀开源项目/harness2/harness2/core/context.py)，在 `PpcComposer` 中实施四重防泄漏机制：
  1. **Proposer 零 qrel 接触**：候选生成仅对 `HarnessConfig` 执行与题目标准答案（`expectedKeywords` / `qrel`）完全无关的通用代数变异（如精简系统提示词冗余修饰、收紧循环步数上界），并在构造候选时通过 `assertNoQrelLeakage` 断言候选提示词不包含任何任务专属答案词。
  2. **匿名 ID 映射（Anonymous Mapping）**：在进入比较器前，使用确定性 SHA-256/SHA-1 种子将 `baseline` 与 `candidate` 映射为匿名标签 `Option_A` 与 `Option_B`，剥离 `candidateId` 与 `isBaseline` 身份标签。
  3. **Position-Swap 双向盲评**：对每个 case 的 `(baseline, candidate)` 输出对，先后以 `(Option_A=baseline, Option_B=candidate)`（Pass 1）和 `(Option_A=candidate, Option_B=baseline)`（Pass 2）两个方向执行无 qrel 盲评。只有当 Pass 1 与 Pass 2 指向同一个物理候选时才判定 `positionSwapConsistent = true`；若两次结果矛盾，立即判定为位置偏置噪声（`INCONSISTENT_TIE`），不得判候选胜出。
  4. **`UNKNOWN` 指标硬剔除**：凡任一 Probe 的 `usageStatus == UsageStatus.UNKNOWN` 或 `costKnown == false`，该候选在进入 `PpcComposer` 选择前直接被标记为 `DROPPED_UNKNOWN_USAGE` 剔除，绝不允许以 `0 token / 0 元` 混入优选。

---

### 问题 7：如何保证 live 证据不覆盖已跟踪资产？

- **审计结论**：
  1. **独立输出路径**：Stage 9 的所有真实 Live PPC 证据统一写入全新隔离目录：
     `experiment/outputs/harness9-live/<runId>/`（包含 `probes/`、`ppc_summary.json`、`selected_candidate.json`、`checksums.sha256`）。
  2. **Git 忽略保护**：在仓库根目录 `.gitignore` 中新增忽略规则 `experiment/outputs/harness9-live/`，确保运行 Live 实验或默认测试均不会产生未跟踪或已修改的 Git 脏文件，且**绝对不读取、不修改、不覆盖**已提交的 `experiment/outputs/harness4/` 与 `experiment/outputs/harness5/`。
  3. **原子写盘与校验和**：所有 JSON 证据文件均先写入同目录 `.tmp` 临时文件，再通过 `Files.move(..., StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)` 原子落盘，最后统一生成 `checksums.sha256`。
