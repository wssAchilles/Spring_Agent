# Hermes Harness² 阶段八（Stage 8-lite）：三独立智能体交叉辩论与主 Agent 终局裁决书

- **文档编号**：`52-harness8-debate.md`
- **日期**：`2026-10-30`
- **当前分支与基线**：`feat/hermes-harness-stage7` (`HEAD: 362074a0`)

---

## 一、物理派发子智能体存证清单（遵循 `AGENTS.md` §2.7.1 铁律）

本阶段通过系统原生 `invoke_subagent` 工具物理派发了 3 个独立隔离沙盒 `research` 子智能体，严禁单进程自问自答。三方物理存证如下：

| 智能体代号 | 角色名称 (Role) | 物理 Conversation ID | 独立审计报告归档路径 | 物理运行 Transcript 审计链接 |
|---|---|---|---|---|
| **Agent A** | `Credible Usage Metering Auditor`（可信计量审计员） | [`fa4b2275-26a7-466a-aacc-c6eeecab9868`](conversation://fa4b2275-26a7-466a-aacc-c6eeecab9868) | [`52-harness8-agent-A.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/52-harness8-agent-A.md) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/fa4b2275-26a7-466a-aacc-c6eeecab9868/.system_generated/logs/transcript.jsonl) |
| **Agent B** | `Capability & Guard Semantics Auditor`（能力与 Guard 语义审计员） | [`aabfc864-b43f-458d-bfce-6fe994fed764`](conversation://aabfc864-b43f-458d-bfce-6fe994fed764) | [`52-harness8-agent-B.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/52-harness8-agent-B.md) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/aabfc864-b43f-458d-bfce-6fe994fed764/.system_generated/logs/transcript.jsonl) |
| **Agent C** | `PPC Readiness & Minimal Record Auditor`（PPC 使能审计员） | [`670a26dd-c4fd-4aa9-a5a7-ffe2b803a93d`](conversation://670a26dd-c4fd-4aa9-a5a7-ffe2b803a93d) | [`52-harness8-agent-C.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/52-harness8-agent-C.md) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/670a26dd-c4fd-4aa9-a5a7-ffe2b803a93d/.system_generated/logs/transcript.jsonl) |

---

## 二、五大核心架构议题交叉辩论与终局裁决

### 辩论焦点 1：`UsageStatus` 的语义定义与枚举取值（Agent A vs Agent C）

- **Agent A 观点**：
  `UsageStatus` 必须定义为物理 Token 计量的可信度状态枚举 `UsageStatus { KNOWN, UNKNOWN }`。只有当请求执行期间所有 LLM 调用均从 `ChatResponse.getMetadata().getUsage()` 中提取到真实正数 Token 统计时才为 `KNOWN`；若 Mock/Fake 模型未返回 `Usage`、流中途异常中断或未发生真实计量，则必须标记为 `UNKNOWN`，严禁用 `0` 或 `estimateTokenCount` 字符估算冒充 `KNOWN`。
- **Agent C 观点**：
  在设计 `HarnessEvidenceRecord` 扩充字段时，将 `UsageStatus` 写成了内核桥接模式状态（`KERNEL_OFF / SHADOW_BYPASS / KERNEL_ENFORCED / FAIL_CLOSED`）。
- **主 Agent 终局裁决（采纳 Agent A，驳回 Agent C 的命名混淆）**：
  1. **`UsageStatus` 严格锁定为 `{ KNOWN, UNKNOWN }`**，专门表达 Token/Cost 计量是否来自真实模型响应 `ChatResponse.getMetadata().getUsage()`。
  2. 内核桥接模式已有现成的 `KernelBridgeMode { OFF, SHADOW, ENFORCE }`，无需占用 `UsageStatus` 命名。
  3. **硬性门禁**：当 `UsageStatus == UNKNOWN` 时，`HarnessEvidenceRecord` 不得用 `estimateTokenCount(text)` 伪造 `KNOWN` Token；离线 PPC 的 `ProbeExecutionDiff` 必须将 `usageComparable` 设为 `false`，`satisfiesBudgetGate()` 与 `isEligibleForAdoption()` 强制返回 `false`，杜绝两个 Mock 返回 `0` Token 或短文本估算值被误判为“省 Token 胜出”。

---

### 辩论焦点 2：流式与多步 ReAct 下如何无损捕获 `ChatResponse.getMetadata().getUsage()`（Agent A 方案细化）

- **问题症结**：
  `DeepSeekCompatibleChatModel` 已经在非流式 `parseResponse`（行 323–335）和流式 `parseStreamChunk`（行 282 `include_usage: true`，行 422–434）中构造了 `DefaultUsage` 并放入 `ChatResponse.getMetadata().getUsage()`；但 `AgentOrchestrator` 调用 `ReactAgent.stream(messages)` 后只消费了 `NodeOutput` 文本块，导致 `Usage` 在中间层丢失。
- **Agent A 方案与主 Agent 裁决**：
  - 在 `tech.qiantong.qknow.hermes.agent.harness` 下引入请求级装饰器 `UsageCapturingChatModel implements ChatModel`，在 `AgentOrchestrator.executeAgent` 创建 `chatModel` 与 `agentModel` 时统一包装。
  - **流式防重与末帧归并**：流式 SSE 响应的中间 delta chunk 的 `usage` 为 `null`，只有末尾统计 chunk 携带有效 `Usage`。因此 `UsageCapturingChatModel.stream(Prompt)` 在单次流内用局部 `AtomicReference<Usage> lastValidUsage` 捕获有效 `Usage`，在 `doOnComplete` 时若 `lastValidUsage != null` 则记入 `meteredCallCount` 并累加真实 `promptTokens / completionTokens / totalTokens / cacheHitTokens / reasoningTokens`；若流结束仍无有效 `Usage` 或触发 `doOnError / doOnCancel`，则递增 `unmeteredCallCount`，使快照状态永久锁定为 `UsageStatus.UNKNOWN`。

---

### 辩论焦点 3：能力上下文注入与空扩展能力降级语义（Agent B 方案裁决）

- **问题症结**：
  1. `CapabilityContextRecord.applyTo(ChatRequest.Builder)` 与 `PreparedExecution.applyTo(ChatRequest.Builder)` 在生产代码中调用次数为 `0`；
  2. `HarnessKernelBridge.bridgeExecution` 解析了 `mounted` 工具传给 `session.prepare`，但在包装返回 `effectiveTools` 时只遍历了知识库 `tools`，漏掉了 `resolver` 解析出的业务工具；
  3. 当未注入任何实质 RAG 文档、工具或历史消息时，系统未诚实降级或标记为 `CapabilityTag.PROMPT_ONLY`。
- **主 Agent 终局裁决（全面采纳 Agent B）**：
  1. **幂等 `applyTo` 并真实注入 `ChatRequest`**：`CapabilityContextRecord.applyTo` 先执行 `clearRagContexts().clearToolMethodIds().clearHistory()` 再写入有效上下文；`HarnessKernelBridge.bridgeExecution` 真实调用 `prepared.applyTo(request.toBuilder()).build()` 并保存在 `BridgeExecutionHandle.effectiveRequest()` 与 `capabilityContext()` 中；`AgentOrchestrator` 在 `ENFORCE` 模式下使用 `handle.capabilityContext()` 同步驱动下游的 `effectiveRagContexts`、`enabledToolNames`、`bridgeTools` 与 `messages`。
  2. **诚实标记 `CapabilityTag.PROMPT_ONLY`**：
     - 若请求显式强制声明了扩展能力（如 `RAG` / `TOOL` / `HISTORY` / `COMPOSITE` 或别名 `RAG_ENABLED` / `TOOL_AUGMENTED`），但实际有效载荷为空：在 `ENFORCE` 模式下触发 Fail-Close 拒绝（防止无能力伪造），同时将实际生效能力 `effectiveCapabilityTag` 记录为 `CapabilityTag.PROMPT_ONLY`；
     - 若请求未显式强制声明扩展能力且清洗后的有效 `ragContexts`、`mountedTools`、`history` 全为空，则自动解析并标记为 `CapabilityTag.PROMPT_ONLY`。

---

### 辩论焦点 4：Bridge Guard 与 `ReActCycleGuard` 合并去重及拒绝结果严禁记为 `success`（Agent B vs Agent C）

- **问题症结**：
  1. `AgentOrchestrator` 先用 `HarnessKernelBridge` 包装了一次工具与 `resolver`，随后在行 655–665 又用 `wrapWithCycleGuard`（`ReActCycleGuard`）包装了第二次，造成每次调用触发两次步数与滑动窗口计数；
  2. `HarnessKernelBridge.guard` 拒绝工具调用时返回 `"[HARNESS_GUARD_REJECTED:" + violation + "]"` 字符串而不抛异常，外层 `wrapWithCycleGuard` 的 `finally` 块因未捕获到异常，保持 `toolSuccess = true`，把所有被 Guard 拦截的非法/循环调用在 `HarnessEvidenceCollector` 中全部记为了 **`success = true`**！
- **主 Agent 终局裁决（采纳 Agent B 单一守卫与硬防御契约）**：
  1. **消除重复包装**：
     - 当 `HarnessKernelBridge` 处于 `ENFORCE` 模式时，由 `HarnessKernelBridge` 统一完成单层工具守卫包装（内含步数上限、循环窗口检测、`HoareTripleToolGuard` 前后置校验与 `HarnessEvidenceCollector` 记录），`AgentOrchestrator` **不再二次套用 `wrapWithCycleGuard`**；
     - 当处于 `OFF` 或 `SHADOW` 模式时，由 `AgentOrchestrator` 的单层 `wrapWithCycleGuard` 负责实际熔断（`SHADOW` 模式下同层旁路通知 Bridge 记录影子观测，不嵌套第二层包装）；
     - 所有模式下的 `ToolCallbackResolver` 均强制校验当前请求声明的 `allowedToolNames` 白名单，非白名单工具一律拦截并返回 `null`。
  2. **拒绝结果严禁记为 `success`**：
     - 被 Guard/Cycle 拦截的调用**绝对不执行底层 `ToolCallback.call`**；
     - `HarnessEvidenceRecord.ToolExecutionEntry` 增加 `boolean guardBlocked` 与 `String violationReason`（强制不变量：`guardBlocked == true` 时 `success` 必须为 `false`）；
     - `HarnessEvidenceCollector` 新增 `recordGuardBlockedToolExecution(...)`，且在 `recordToolExecution(...)` 中对任何以 `"[HARNESS_GUARD_REJECTED:"`、`"CIRCUIT_BREAKER_"`、`"【系统熔断提示】"`、`"【系统纠偏提示】"` 开头的返回结果强制覆写为 `success = false, guardBlocked = true`；
     - `HarnessEvidenceRecord` 顶层暴露 `boolean guardBlocked` 与 `int guardBlockedCount`。

---

### 辩论焦点 5：RAG 归因全链路打通与 Stage 9 PPC 最小执行记录（采纳 Agent C + RAGChecker 理论）

- **问题症结**：
  `AgentOrchestrator` 行 326 硬编码 `completeExecution(..., 0.0D)`，`HarnessKernelBridge` 忽略 `ragScore`，`HermesHarnessKernel` 硬编码 `new TrajectoryEvidence(0, 0, 0, 0, toolExecutions)`，导致无 RAG 与 100% 充分利用 RAG 均输出 `ragUnderutilization = 0.0`。
- **主 Agent 终局裁决**：
  1. **引入 `RagAttributionStatus { NO_RAG, UNUSED, PARTIALLY_USED, FULLY_USED }` 与 `RagAttributionSummary`**：
     - 当有效注入文档数 `ragProvided == 0` 时，状态强制为 `NO_RAG`（严禁与 `FULLY_USED` 混淆）；
     - 当 `ragProvided > 0` 时，根据最终回答及知识库工具调用实际命中的有效文档数 `ragUsed`（$0 \le \text{ragUsed} \le \text{ragProvided}$）计算 `ragUnderutilization = 1.0 - (double) ragUsed / ragProvided`，并将真实的 `(reasoningTokens, answerTokens, ragProvided, ragUsed, ragScore)` 贯穿传入 `HarnessKernelBridge.completeExecution` $\to$ `ExecutionSession.evaluateTrajectory` $\to$ `TrajectoryAttributionAnalyzer.analyze`。
  2. **向后兼容扩充 `HarnessEvidenceRecord`（20 字段最小完备凭单）**：
     在保留原有 14/15 参数兼容构造器的基础上，新增 5 个最小使能字段：
     - `UsageStatus usageStatus`（`KNOWN | UNKNOWN`）
     - `String capabilityTag`（实际生效的能力标签，如 `"PROMPT_ONLY"`, `"RAG"`, `"TOOL"`, `"HISTORY"`, `"COMPOSITE"`）
     - `boolean guardBlocked`
     - `int guardBlockedCount`
     - `RagAttributionSummary ragAttribution`（含 `status`, `ragProvided`, `ragUsed`, `ragScore`, `ragUnderutilization`）
     并将上述 5 个字段全部纳入 `computeDigest()` 的 SHA-256 常量时间签名计算。
  3. **明确范围收敛（7 项坚决不做）**：
     阶段八坚决不做完整生产认证、不做无限嵌套 JSON Schema、不做企业级 SLA/运维、不做付费 live E2E、不做 MCP/HITL、不做在线自修改、不做跨语言重写。
