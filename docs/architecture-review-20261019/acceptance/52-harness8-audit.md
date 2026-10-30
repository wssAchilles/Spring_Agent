# Hermes Harness² 阶段八（Stage 8-lite）：阶段 0 基线核验与阶段 1 只读法医审计报告

- **文档编号**：`52-harness8-audit.md`
- **审计日期**：`2026-10-30`
- **当前分支与提交**：`feat/hermes-harness-stage7` @ `362074a0`
- **Java 环境**：`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`（Java 21.0.5 Temurin）

---

## 一、阶段 0：Stage 7 归档检查与 Stage 8 基线核验

### 1.1 工作区未跟踪的 Stage 7 交付物核验
通过 `git status --short` 与 `shasum -a 256 -c` 核验，工作区当前存在 Stage 7 的 9 个归档文档且哈希校验完全通过：
- `docs/architecture-review-20261019/acceptance/49-harness7-agent-A.md`
- `docs/architecture-review-20261019/acceptance/49-harness7-agent-B.md`
- `docs/architecture-review-20261019/acceptance/49-harness7-agent-C.md`
- `docs/architecture-review-20261019/acceptance/49-harness7-bridge-design.md`
- `docs/architecture-review-20261019/acceptance/49-harness7-debate.md`
- `docs/architecture-review-20261019/acceptance/49-harness7-research.ledger.json`
- `docs/architecture-review-20261019/acceptance/49-harness7-research.md`
- `docs/architecture-review-20261019/acceptance/50-harness7-taskbook.md`
- `docs/architecture-review-20261019/acceptance/50-harness7-taskbook.md.sha256`（`50-harness7-taskbook.md: OK`）
- 已提交报告：`docs/architecture-review-20261019/acceptance/51-harness7-implementation.md`（已在 `362074a0` 提交中）。

> **阶段 0 归档指令**：实施 Agent 在开始 Stage 8 编码前，将在 `feat/hermes-harness-stage7` 分支把上述 Stage 7 遗留归档文件独立提交为 `docs(hermes): 归档阶段七桥接研究与实施报告`，随后切出新分支 `feat/hermes-harness-stage8`（严禁修改 `main`）。

### 1.2 核心基线不变量确认
1. **向量空间维度锁定 1024 维**：[`HypersphericalManifoldProjector.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/manifold/HypersphericalManifoldProjector.java) 行 19 锁定 `EMBEDDING_DIM = 1024`（对应千问 `text-embedding-v4` 单位超球面 $\mathbb{S}^{1023}$）。
2. **生产默认行为不变**：[`AgentProperties.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/config/AgentProperties.java) 默认 `kernelBridgeMode = KernelBridgeMode.OFF`，`hermes.orchestration.enabled = false`（`SINGLE` 模式）。

---

## 二、阶段 1：只读源码法医审计（逐项回答 7 大必答题）

### 问题 1：当前 token/cost 来自哪里？是否是真实 usage？

**审计结论：当前主路径的 token/cost 并非来自模型真实 usage，而是来自字符长度除以 2 的启发式估算（主路径）或工具 JSON 入参硬编码字段（Bridge 路径）。**

**源码证据**：
1. **主路径字符估算冒充真实 Token**：
   - 在 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java) 行 305–306 中声明了：
     ```java
     AtomicLong promptTokens = new AtomicLong(0L);
     AtomicLong completionTokens = new AtomicLong(0L);
     ```
   - 但在整个 `executeSingleAgentStream` / `runGraphStream`（行 721–850）中，**没有任何一处代码向 `promptTokens` 或 `completionTokens` 写入模型返回的值**（二者永远为 `0L`）。
   - 在流结束回调（行 832–841）中：
     ```java
     long pt = promptTokens.get() > 0 ? promptTokens.get() : estimateTokenCount(userQuery) + estimateTokenCount(ragContext);
     long ct = completionTokens.get() > 0 ? completionTokens.get() : estimateTokenCount(fullOutput.toString());
     long totalTokens = pt + ct;
     double costCny = (pt * 0.001 + ct * 0.002) / 1000.0;
     evidenceCollector.recordTokenUsage(pt, ct, totalTokens, costCny);
     ```
     由于 `promptTokens.get()` 恒为 `0`，100% 落入行 1315 的 `estimateTokenCount(String text)`：
     ```java
     private long estimateTokenCount(String text) {
         return (text == null || text.isEmpty()) ? 0L : Math.max(1L, Math.round(text.length() / 2.0));
     }
     ```
2. **Bridge 守卫路径从工具入参读取虚拟预算**：
   - 在 [`HarnessKernelBridge.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HarnessKernelBridge.java) 行 232–242 中，`guard()` 调用 `session.invokeTool(toolName, arguments, estimatedTokens, estimatedCostCny)` 时，其 `estimatedTokens` 和 `estimatedCostCny` 是通过 `longArg(arguments, "_estimatedTokens")` 和 `doubleArg(arguments, "_estimatedCostCny")` 从工具调用 JSON 参数中读取的。正常 LLM 工具调用根本不会传入 `_estimatedTokens` 参数，因此恒为 `0L` 和 `0.0D`。

---

### 问题 2：DeepSeek 响应中真实 usage 在哪个对象/字段？

**审计结论：真实 usage 已经由 [`DeepSeekCompatibleChatModel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/deepseek/DeepSeekCompatibleChatModel.java) 完整解析并封装在 Spring AI 标准对象 `ChatResponse.getMetadata().getUsage()` 中。**

**源码证据**：
1. **非流式响应（`call(Prompt)`）**：
   - [`DeepSeekCompatibleChatModel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/deepseek/DeepSeekCompatibleChatModel.java) 行 323–335（`parseResponse`）：
     ```java
     ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder();
     JsonNode usageNode = root.get("usage");
     if (usageNode != null && !usageNode.isNull()) {
         int promptTokens = usageNode.path("prompt_tokens").asInt(0);
         int completionTokens = usageNode.path("completion_tokens").asInt(0);
         int totalTokens = usageNode.path("total_tokens").asInt(promptTokens + completionTokens);
         metadataBuilder.usage(new DefaultUsage(promptTokens, completionTokens, totalTokens, usageNode));
     }
     ```
2. **流式响应（`stream(Prompt)`）**：
   - 行 282 在构造流式请求体时自动注入了 `stream_options: {"include_usage": true}`；
   - 行 422–434（`parseStreamChunk`）在收到流式末帧的 `usage` JSON 节点时，同样构造了 `new DefaultUsage(promptTokens, completionTokens, totalTokens, usageNode)` 并挂载到 `ChatResponse.getMetadata().getUsage()` 上。
   - 其中 `usage.getNativeUsage()` 保存了原始 `JsonNode usageNode`，可进一步提取 `prompt_cache_hit_tokens`、`prompt_cache_miss_tokens` 及 `completion_tokens_details.reasoning_tokens`。
3. **为什么 `AgentOrchestrator` 没拿到**：
   - `AgentOrchestrator` 将 `ChatModel` 传给 Spring AI Alibaba 的 `ReactAgent.builder().model(chatModel)`，随后消费 `reactAgent.stream(messages)` 产出的 `NodeOutput`（`StreamingOutput`）。`StreamingOutput` 仅暴露了文本块 `chunk()`，屏蔽了底层 `ChatResponse.getMetadata().getUsage()`。
   - **最小使能修复点**：在单次请求执行时，用轻量的请求级 `UsageCapturingChatModel` 装饰器包装传入的 `ChatModel`，在 `call(Prompt)` 返回值和 `stream(Prompt)` 的 `doOnNext(ChatResponse)` 中直接拦截 `response.getMetadata().getUsage()`，累加真实 `promptTokens`、`completionTokens`、`totalTokens`；若整个请求生命周期内未捕获到任何非空有效 `Usage`（例如单元测试中的简单 stub 模型未设置 `DefaultUsage`），则将状态诚实标记为 `UsageStatus.UNKNOWN`（严禁回退到 `estimateTokenCount` 冒充 `KNOWN`）。

---

### 问题 3：RAG score 当前为什么没有进入归因？

**审计结论：从 `AgentOrchestrator` 到 `HarnessKernelBridge` 再到 `HermesHarnessKernel` 存在三处硬编码断链，导致 `TrajectoryAttributionAnalyzer` 收到的永远是全 `0`。**

**源码证据**：
1. **断链点 1（`AgentOrchestrator` 硬编码 `0.0D`）**：
   - [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java) 行 432–476 在 `retrieveKnowledgeContext` 中明明已经拿到了检索结果列表 `docs`、文档数量与最高相似度分数，但在行 326 的 `doFinally` 中却写死：
     ```java
     harnessKernelBridge.completeExecution(request.getRequestId(), handle, 0.0D);
     ```
2. **断链点 2（`HarnessKernelBridge` 丢弃 `ragScore` 参数）**：
   - [`HarnessKernelBridge.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HarnessKernelBridge.java) 行 154–161：
     ```java
     public Optional<TrajectoryAttributionAnalyzer.AttributionReport> completeExecution(
             String requestId, PreparedSessionHandle handle, double ragScore) {
         // ...
         return Optional.of(handle.session().evaluateTrajectory(List.of()));
     }
     ```
     这里的 `ragScore` 形参完全未被使用，且连 `toolExecutions` 都传了空列表 `List.of()`！
3. **断链点 3（`HermesHarnessKernel` 硬编码 4 个 `0`）**：
   - [`HermesHarnessKernel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HermesHarnessKernel.java) 行 166–171：
     ```java
     public TrajectoryAttributionAnalyzer.AttributionReport evaluateTrajectory(
             List<TrajectoryAttributionAnalyzer.ToolExecutionEvidence> toolExecutions) {
         return kernel.attributionAnalyzer.analyze(
                 new TrajectoryAttributionAnalyzer.TrajectoryEvidence(0, 0, 0, 0, toolExecutions));
     }
     ```
     此处将 `reasoningTokens`、`baselineReasoningTokens`、`ragProvided`、`ragUsed` 全部硬编码为 `0`，导致 [`TrajectoryAttributionAnalyzer.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/evolution/TrajectoryAttributionAnalyzer.java) 行 41–44 计算出的 `ragUnderutilization` 恒为 `0.0D`。

---

### 问题 4：Bridge guard 与 `ReActCycleGuard` 是否重复包装？

**审计结论：是，存在双层重复包装与重复计数。**

**源码证据**：
- **第一层包装（`HarnessKernelBridge`）**：
  在 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java) 行 579–594 中：
  ```java
  List<ToolCallback> bridgeTools = harnessKernelBridge.wrapToolCallbacks(
          request.getRequestId(), rawTools, preparedHandle);
  ToolCallbackResolver bridgeResolver = harnessKernelBridge.wrapToolResolver(
          request.getRequestId(), rawResolver, rawTools, preparedHandle);
  ```
  当模式为 `ENFORCE` 时，`HarnessKernelBridge.guard()` 会对每次工具调用执行 `session.invokeTool(...)`，触发内核的 `LocallyDenseCycleAutomaton.step(toolName)` 与 `HoareTripleToolGuard.verifyPrecondition(...)`。
- **第二层包装（`AgentOrchestrator.wrapWithCycleGuard`）**：
  紧接着在 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java) 行 655–665 中：
  ```java
  List<ToolCallback> guardedTools = bridgeTools.stream()
          .map(t -> wrapWithCycleGuard(t, cycleGuard, evidenceCollector))
          .toList();
  ToolCallbackResolver guardedResolver = wrapResolverWithCycleGuard(
          bridgeResolver, cycleGuard, evidenceCollector);
  ```
  外层又无条件套了一层 `ReActCycleGuard`。一次工具调用既被外层 `ReActCycleGuard` 检查并计数，又被内层 `LocallyDenseCycleAutomaton` 检查并计数，职责重叠且行为不一致。

---

### 问题 5：被 guard 拒绝的工具结果是否会被记录为 `success`？

**审计结论：是！所有被 `HarnessKernelBridge.guard` 拦截的非法或死循环工具调用，在 `HarnessEvidenceCollector` 中全部被错误记录为 `success = true`。**

**源码证据**：
1. 内层 [`HarnessKernelBridge.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HarnessKernelBridge.java) 行 235–251 在拦截工具调用时**不抛异常，而是正常返回字符串**：
   ```java
   HoareTripleToolGuard.VerificationResult verification = session.invokeTool(...);
   if (!verification.permitted()) {
       counters.recordBlocked(verification.violationMessage());
       return "[HARNESS_GUARD_REJECTED:" + verification.violationMessage() + "]";
   }
   ```
2. 外层 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java) 行 1554–1570 的 `wrapWithCycleGuard` 逻辑为：
   ```java
   boolean toolSuccess = true;
   String result = null;
   try {
       // ...
       result = delegate.call(toolInput, toolContext);
       return result;
   } catch (Exception ex) {
       toolSuccess = false;
       throw ex;
   } finally {
       if (evidenceCollector != null) {
           long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
           evidenceCollector.recordToolExecution(toolName, durationMs, toolSuccess, toolInput, result);
       }
   }
   ```
   因为 `delegate.call(...)` 正常返回了 `"[HARNESS_GUARD_REJECTED:...]"` 字符串而未抛出异常，`toolSuccess` 保持为 `true`！最终 `HarnessEvidenceRecord.toolExecutions()` 中该次违规调用的 `success()` 为 `true`，导致离线 PPC 评测器误以为工具成功执行。

---

### 问题 6：哪些状态属于 request，哪些属于 session？以及能力上下文注入现状如何？

**审计结论**：
1. **能力上下文“只校验不注入”**：
   - [`CapabilityContextAssembler.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/CapabilityContextAssembler.java) 行 26–31 定义了 `CapabilityContextRecord.applyTo(ChatRequest.Builder)`，[`HermesHarnessKernel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HermesHarnessKernel.java) 行 46–48 定义了 `PreparedExecution.applyTo(ChatRequest.Builder)`；
   - 但在 `HarnessKernelBridge`、`AgentOrchestrator` 和 `DefaultOfflineProbeExecutor` 中，**从未调用过 `applyTo`**！
   - 此外，当调用方请求了 `RAG_ENABLED` 或 `TOOL_AUGMENTED`，但实际组装出的 `ragContexts`、`toolMethodIds`、`history` 全部为空时，当前实现未将其有效能力标签降级为 `CapabilityTag.PROMPT_ONLY`。
2. **Request 级状态 vs Conversation/Session 级状态边界**：
   - **Request 级状态（生命周期严格绑定单次 `requestId`，请求结束即归档销毁）**：
     - `PreparedExecution` / `PreparedSessionHandle`（虽命名为 `ExecutionSession`，实为单次请求的执行上下文）；
     - 实际生效的 `CapabilityContextRecord` 与有效 `CapabilityTag`；
     - `UsageSnapshot`（`UsageStatus`、`promptTokens`、`completionTokens`、`totalTokens`、`reasoningTokens`、`cacheHitTokens`）；
     - `LocallyDenseCycleAutomaton` 的滑动窗口步数与工具调用频次；
     - `HoareTripleToolGuard` 的单次请求工具调用计数与阻断记录（`guardBlocked`、`guardBlockedCount`、`blockedViolations`）；
     - 单次请求的 RAG 归因统计（`ragProvided`、`ragUsed`、`ragScore`、`RagAttributionStatus`）；
     - `HarnessEvidenceCollector` 与最终生成的 `HarnessEvidenceRecord`。
   - **Conversation/Session 级状态（生命周期跨越多轮对话，绑定 `conversationId`）**：
     - `MemoryManager` 管理的对话历史与长期记忆快照；在每轮请求开始时提取历史片段注入当前请求的 `CapabilityContextRecord`，并在请求级 `ReversibleRingColdBuffer` 中维护可逆冷备。

---

### 问题 7：Stage 9 PPC 需要的最小执行记录是什么？

**审计结论**：
对照本地 Harness² 的 `Runmark` / `LedgerEntry` 以及本项目 `EvidenceDiffExtractor`、`ProbeExecutionDiff`、`DualBlindSelectionEvaluator`，Stage 9 真实 PPC 在对比 Baseline 与 Candidate 时，单次执行的 `HarnessEvidenceRecord` 必须至少提供以下**最小可审计字段集**：

| 字段名 | 类型 | 作用与 Stage 9 PPC 消费语义 | 当前 `HarnessEvidenceRecord` 是否已具备 |
|---|---|---|---|
| `requestId` / `evidenceId` | `String` | 唯一标识单次执行凭单 | 已具备 |
| `voucherFingerprint` | `String` | 绑定 `HarnessConfigVoucher` 的 SHA-256 指纹（即 `harnessFingerprint`） | 已具备 |
| `capabilityTag` | `CapabilityTag` | 标记本次执行**真实注入并生效**的能力（若未注入任何扩展能力则诚实记为 `PROMPT_ONLY`） | **缺失（需新增）** |
| `usageStatus` | `UsageStatus` (`KNOWN` \| `UNKNOWN`) | 标记 `tokenUsage` 是否来自真实模型响应；若为 `UNKNOWN`，PPC 预算对比必须标记 `usageComparable = false`，严禁把 `UNKNOWN` 当 `0` 或用估算值伪造省 Token | **缺失（需新增）** |
| `tokenUsage` | `TokenUsage` | 当 `usageStatus == KNOWN` 时存放真实 `promptTokens`、`completionTokens`、`totalTokens`、`estimatedCostCny` | 已具备结构，但数据系估算（需改为真实 Usage） |
| `toolExecutions` | `List<ToolExecution>` | 每次工具调用的耗时与真实成败；**被 Guard/Cycle 阻断的调用必须 `success = false`** | 已具备结构，但 Guard 阻断被误记为 `true`（需修正） |
| `guardBlocked` / `guardBlockedCount` | `boolean` / `int` | 明确标识本次执行是否触发了 Guard/Cycle 拦截及拦截次数，供 PPC 安全门禁直接判读 | **缺失（需新增）** |
| `ragAttribution` | `RagAttributionRecord` | 含 `RagAttributionStatus` (`NO_RAG` \| `PROVIDED_UNUSED` \| `PROVIDED_USED`)、`ragProvided`、`ragUsed`、`ragScore`、`ragUnderutilization` | **缺失（需新增）** |
| `finalOutputSummary` | `String` | 最终回答摘要，用于质量评分与归因核对 | 已具备 |
| `executionMode` | `ExecutionMode` | 区分 `DETERMINISTIC_REPLAY` 与 `REAL_PRODUCTION` | 已具备 |
