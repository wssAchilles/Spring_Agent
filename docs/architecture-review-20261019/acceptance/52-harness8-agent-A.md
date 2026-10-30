# Hermes Harness² 阶段八（Stage 8-lite）：Agent A（可信计量审计员）独立审计报告

- **文档编号**：`52-harness8-agent-A.md`
- **子智能体角色**：`Credible Usage Metering Auditor`（Agent A：可信计量审计员）
- **物理 Conversation ID**：[`fa4b2275-26a7-466a-aacc-c6eeecab9868`](conversation://fa4b2275-26a7-466a-aacc-c6eeecab9868)
- **物理 Transcript 日志**：[`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/fa4b2275-26a7-466a-aacc-c6eeecab9868/.system_generated/logs/transcript.jsonl)
- **审计分支与提交基线**：`feat/hermes-harness-stage7` (`HEAD: 362074a0`)
- **审计范围**：`backend/qknow-framework/qknow-ai` 与 `backend/qknow-hermes/qknow-hermes-core`

---

## 一、真实 Usage 来源与断链根因审计 (Forensic Root-Cause Audit)

通过对上述核心源码的逐行核验，现已完整锁定真实 Token 计量从底层生成到上层丢失、再到被启发式估算或 `0` 冒充的全链路断链根因：

### 1.1 真实 Usage 的合法物理源头：`DeepSeekCompatibleChatModel`
文件：[`DeepSeekCompatibleChatModel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/deepseek/DeepSeekCompatibleChatModel.java)
1. **流式 Usage 开关已显式开启（行 281–283）**：
   在 `buildRequestBody(Prompt prompt, boolean stream)` 中，当 `stream == true` 时，第 **282** 行显式注入 `body.put("stream_options", Map.of("include_usage", true))`，确保 DeepSeek 官方 SSE 流在结束前下发携带 `usage` 统计对象的 chunk。
2. **非流式响应真实封装（行 323–335）**：
   在 `parseResponse(String responseBody)` 中，第 **323–329** 行解析 `root.path("usage")`，构造 Spring AI 标准 `DefaultUsage(promptTokens, completionTokens, totalTokens, usageNode)`，其中第 4 个参数 `nativeUsage` 原样保留了 Jackson `JsonNode`（含 `prompt_cache_hit_tokens`、`prompt_cache_miss_tokens` 及 `completion_tokens_details.reasoning_tokens`），并在第 **330–335** 行通过 `ChatResponseMetadata.builder().usage(usage).build()` 封装进 `ChatResponse.getMetadata().getUsage()`。
3. **流式响应真实封装（行 377–395, 422–434）**：
   在 `parseStreamChunk(String payload, StreamToolCallAccumulator accumulator)` 中，第 **377–378** 行提取 `JsonNode usageNode = root.path("usage"); boolean hasUsage = !usageNode.isMissingNode();`；第 **393–395** 行的空片段守卫显式放行 `hasUsage == true` 的末尾统计块；第 **422–427** 行当 `hasUsage == true` 时构造 `DefaultUsage` 并挂载至 `ChatResponse.getMetadata().getUsage()`（其余中间文本 delta 块的 `usage` 为 `null`）。

### 1.2 断链点一：`AgentOrchestrator` 主 ReAct 流 100% 丢弃 `Usage` 并用 `estimateTokenCount` 冒充
文件：[`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java)
1. **计数器初始为 0（行 305–306）**：
   `chat(ChatRequest request)` 在第 **305–306** 行创建了 `AtomicLong promptTokens = new AtomicLong(0)` 与 `AtomicLong completionTokens = new AtomicLong(0)`。
2. **ReAct 流式消费全程未读取 `Usage`（行 721, 735–793）**：
   第 **721** 行启动 `Flux<NodeOutput> stream = agent.stream(messages)`。在第 **735–793** 行的流订阅回调 `output -> { ... }` 中，代码仅处理了 `StreamingOutput` 的 `AGENT_MODEL_STREAMING`（提取 `message().getText()` 与 `reasoning_content`）、`AGENT_MODEL_FINISHED` 和 `AGENT_TOOL_FINISHED`。**整段流式消费逻辑没有任何一行代码读取底层 `ChatResponse.getMetadata().getUsage()`，也从未调用 `promptTokens.set(...)` 或 `completionTokens.set(...)`**。导致主 ReAct 路径走完时，`promptTokens.get()` 与 `completionTokens.get()` **100% 恒为 `0L`**。
3. **流完成回调与 `doOnComplete` 100% 退化为 `text.length() / 2.0` 启发式估算（行 830–842, 335–341, 1315–1320）**：
   - 在流完成回调（行 **832–841**）中，因 `pt <= 0L` 与 `ct <= 0L` 恒成立，第 **835** 行强制执行 `pt = estimateTokenCount(request.getQuestion())`，第 **838** 行强制执行 `ct = estimateTokenCount(fullAnswer.toString())`，其中 `estimateTokenCount`（行 **1315–1320**）仅为 `Math.max(1, Math.round(text.length() / 2.0))`。这不仅用字符除 2 冒充了真实 Token，而且输入侧仅计算了 `request.getQuestion()`，**完全漏算了 `systemPrompt`、历史上下文、预检索 `<knowledge_base>` 文档块以及多步工具调用的上下文累积 Token**！随后第 **841** 行通过 `evidenceCollector.recordCostAndTokens(pt + ct, calculateEstimatedCostCny(pt, ct))` 将该失真估算值直接写入 `HarnessEvidenceRecord`。
   - 在外层 `doOnComplete`（行 **335–341**）中，由于第 835/838 行只修改了局部变量 `pt`/`ct` 而未写回 `AtomicLong`，第 **335–336** 行再次退化调用 `estimateTokenCount`，并将估算值写入 `deepSeekCostGovernor.recordUsage(modelName, 0, pt, ct, 0)`（行 **340**）。

### 1.3 断链点二：`HarnessKernelBridge` 从工具业务入参读取 `_estimatedTokens` 导致恒为 `0`
文件：[`HarnessKernelBridge.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HarnessKernelBridge.java) 与 [`HoareTripleToolGuard.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/neurosymbolic/HoareTripleToolGuard.java)
1. 在 `HarnessKernelBridge.guard(...)`（行 **232–243**）中，第 **234–235** 行通过 `HoareTripleToolGuard.ExecutionState.fromArguments(arguments)` 提取预算状态，并在第 **241–242** 行将 `estimates.estimatedTokens()` 与 `estimates.estimatedCostCny()` 传给 `session.invokeTool(...)`。
2. 在 `HoareTripleToolGuard.java`（行 **33–43**）中，`fromArguments` 试图从 LLM 生成的工具调用业务参数 `arguments`（例如 `{"city":"北京"}`）中读取名为 `"_estimatedTokens"`（行 **39**）与 `"_estimatedCostCny"`（行 **40**）的字段，缺失时默认返回 `0L` 与 `0.0D`。
3. 生产运行中 LLM 调用工具绝不会在业务 JSON 中携带 `_estimatedTokens`，导致运行期传入 `HoareTripleToolGuard.verifyBefore(...)`（行 **103–108**）的 Token 与成本在正常调用下**恒为 `0L` 和 `0.0D`**，`GUARDRAIL_TOKEN_BUDGET` 与 `GUARDRAIL_COST_BUDGET` 形同虚设。

---

## 二、`UsageStatus` 诚实表达与请求级拦截累加机制设计

为彻底根除“用 `0` 或字符估算冒充真实计量”的学术与工程失真，设计如下最小诚实计量架构：

### 2.1 `UsageStatus` 与不可变快照 `HarnessUsageSnapshot`
在 `tech.qiantong.qknow.hermes.agent.harness` 包下定义计量状态枚举与快照凭单（纯 Java 21 Record）：

```java
package tech.qiantong.qknow.hermes.agent.harness;

/**
 * 物理 Token 计量可信度状态枚举。
 * 铁律：严禁使用 0 或字符启发式估算值 (estimateTokenCount) 冒充 KNOWN。
 */
public enum UsageStatus {
    /**
     * 本次执行链路中发生的所有 LLM 调用（单次或 ReAct 多步）均从底层 ChatResponse.getMetadata().getUsage()
     * 捕获到真实、非空、正数的服务端 Token 用量统计，且无任何未计量调用或流中断缺失。
     */
    KNOWN,

    /**
     * 存在以下任一情形：
     * 1. Mock/Fake 模型未返回 Usage（或返回 null / 全 0 Usage）；
     * 2. 流式调用在收到末尾 Usage chunk 前发生中断、取消或异常；
     * 3. 多步调用中存在部分调用缺失真实 Usage；
     * 4. 请求在调用 LLM 前被短路拦截（如 FAIL_SAFE）或未发生可验证的真实计量。
     */
    UNKNOWN
}
```

### 2.2 请求级 `UsageCapturingChatModel` 拦截与流式防重累加机制
在 `tech.qiantong.qknow.hermes.agent.harness`（或 `kernel`）包下新增 `UsageCapturingChatModel implements ChatModel`：
1. **挂载位置**：在 `AgentOrchestrator.executeAgent(...)` 创建 `chatModel`（行 413–431）及工具路由模型 `agentModel`（行 635–652）后，统一用绑定于当前请求的 `UsageCapturingChatModel` 进行装饰。
2. **有效 `Usage` 判定准则 (`hasValidUsage`)**：
   从 `ChatResponse.getMetadata().getUsage()` 提取 `Usage`：仅当 `usage != null` 且 `(safeLong(usage.getPromptTokens()) > 0 || safeLong(usage.getCompletionTokens()) > 0 || safeLong(usage.getTotalTokens()) > 0)` 时判定为有效真实 `Usage`。
3. **非流式 `call(Prompt)` 拦截**：
   - 调用 `delegate.call(prompt)` 获取 `ChatResponse`；
   - 若满足 `hasValidUsage(response)`，原子累加 `promptTokens`、`completionTokens`、`totalTokens`、`cacheHitTokens`、`reasoningTokens` 与真实成本，并执行 `meteredCallCount.incrementAndGet()`；
   - 若不满足（如 Mock/Fake 模型返回无 `Usage` 的 `ChatResponse`）或调用抛出异常，执行 `unmeteredCallCount.incrementAndGet()`（一旦 `unmeteredCallCount > 0`，快照状态永久锁定为 `UsageStatus.UNKNOWN`）。
4. **流式 `stream(Prompt)` 拦截（单次流生命周期归并，严防逐 chunk 误判）**：
   - 在 `Flux.defer(...)` 内部为单次 `stream(prompt)` 调用创建局部 `AtomicReference<Usage> lastValidUsage = new AtomicReference<>()`；
   - 在 `doOnNext(chunk -> ...)` 中：若该 chunk 满足 `hasValidUsage(chunk)`，更新 `lastValidUsage.set(chunk.getMetadata().getUsage())`（中间普通文本 chunk 无 `Usage` 时忽略，绝不在中间 chunk 递增 `unmeteredCallCount` 或做字符估算）；
   - 在 `doOnError(err -> unmeteredCallCount.incrementAndGet())` 与 `doOnCancel(() -> { if (lastValidUsage.get() == null) unmeteredCallCount.incrementAndGet(); })` 中捕获流异常与中断；
   - 在 `doOnComplete(() -> ...)` 中：若 `lastValidUsage.get() != null`，将该次流调用末尾捕获到的真实 `Usage` 一次性计入请求级累加器并递增 `meteredCallCount`；若流正常结束但 `lastValidUsage.get() == null`（例如测试中的 Fake 流式模型未下发 `Usage` 块），则递增 `unmeteredCallCount`，诚实标记为 `UsageStatus.UNKNOWN`！
5. **彻底剥离启发式冒充**：
   - 在 `AgentOrchestrator` 流结束回调（行 832–842）中，读取 `HarnessUsageSnapshot snapshot = usageCapturingChatModel.snapshot()`：
     - 若 `snapshot.isKnown()`：向 `evidenceCollector` 写入真实 Token 与真实成本，标记 `UsageStatus.KNOWN`；
     - 若 `!snapshot.isKnown()`（即 `UsageStatus.UNKNOWN`）：将 `HarnessEvidenceRecord.usageStatus` 设为 `UsageStatus.UNKNOWN`，**严禁再用 `estimateTokenCount` 写入凭单冒充真实 Token**！

---

## 三、运行期预算判断与 PPC 比较语义设计

1. **运行期内核预算判断（`HarnessKernelBridge` & `HoareTripleToolGuard`）**：
   - 当 `usageStatus == UsageStatus.KNOWN`（或工具入参显式注入测试探针 `_estimatedTokens`）时，严格执行 `GUARDRAIL_TOKEN_BUDGET` 与 `GUARDRAIL_COST_BUDGET` 检查；
   - 当 `usageStatus == UNKNOWN` 时，不把 `0` 当作“已通过预算认证”，而是在凭单中诚实记录 `UsageStatus.UNKNOWN`，同时照常严格执行 Step 上限、循环窗口、工具白名单与必填参数守卫。
2. **离线 PPC 比较语义（`EvidenceDiffExtractor` / `ProbeExecutionDiff` / `DualBlindSelectionEvaluator`）**：
   - `ProbeExecutionDiff` 增加 `boolean usageComparable`（当且仅当 Baseline 与 Candidate 均为 `UsageStatus.KNOWN` 时为 `true`）；
   - 当任一侧为 `UsageStatus.UNKNOWN` 时，`usageComparable == false`，`satisfiesBudgetGate()` 与 `isEligibleForAdoption()` 强制返回 `false`，严禁因两个 Mock 均为 `0` Token 或字符估算更短而伪造“省 Token 通过”！

---

## 四、Research Ledger（14 字段规范）

1. **`RL-STAGE8-A-01`（`production-implementation`）**：`Spring_Agent` (`commit: 362074a0`, 已读 `DeepSeekCompatibleChatModel.java` L1-480, `AgentOrchestrator.java` L1-1659, `HarnessKernelBridge.java` L1-338, `HermesHarnessKernel.java` L1-198, `HoareTripleToolGuard.java` L1-165, `VERIFIED`)。
2. **`RL-STAGE8-A-02`（`production-implementation`）**：`Spring_Agent` 证据与离线 PPC 组件 (`commit: 362074a0`, 已读 `HarnessEvidenceCollector.java` L1-171, `HarnessEvidenceRecord.java` L1-160, `EvidenceDiffExtractor.java` L1-57, `ProbeExecutionDiff.java` L1-45, `DualBlindSelectionEvaluator.java` L1-165, `PositionSwapBlindEvaluator.java` L1-156, `VERIFIED`)。
