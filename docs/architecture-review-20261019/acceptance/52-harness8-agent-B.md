# Hermes Harness² 阶段八（Stage 8-lite）：Agent B（能力与 Guard 语义审计员）独立审计报告

- **文档编号**：`52-harness8-agent-B.md`
- **子智能体角色**：`Capability & Guard Semantics Auditor`（Agent B：能力与 Guard 语义审计员）
- **物理 Conversation ID**：[`aabfc864-b43f-458d-bfce-6fe994fed764`](conversation://aabfc864-b43f-458d-bfce-6fe994fed764)
- **物理 Transcript 日志**：[`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/aabfc864-b43f-458d-bfce-6fe994fed764/.system_generated/logs/transcript.jsonl)
- **审计分支与提交基线**：`feat/hermes-harness-stage7` (`HEAD: 362074a0`)

---

## 一、RAG / TOOL / HISTORY 真实注入审计与修复方案

### 1.1 法医级源码根因剖析：为何 `CapabilityContextAssembler` 沦为“只校验、不注入”的空转组件？

通过逐行追踪 [`CapabilityContextAssembler.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/CapabilityContextAssembler.java)、[`HermesHarnessKernel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HermesHarnessKernel.java)、[`HarnessKernelBridge.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HarnessKernelBridge.java) 与 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java)，定位出以下 4 处断裂点：

1. **`applyTo(ChatRequest.Builder)` 全局零调用（Dead Assembly Method）**：
   - `CapabilityContextAssembler.java`（行 26-31）定义了 `CapabilityContextRecord.applyTo(ChatRequest.Builder)`，旨在将组装校验后的 `ragContexts`、`toolCallbacks.keySet()`（即 `toolMethodIds`）和 `history` 注入 `ChatRequest`。
   - `HermesHarnessKernel.java`（行 46-48）定义了 `PreparedExecution.applyTo(ChatRequest.Builder)`，调用 `context.applyTo(builder).setSystemPrompt(systemPrompt)`。
   - **法医结论**：在 `src/main/java` 生产主干中，上述两个 `applyTo` 方法的调用次数为 **0**。且 `CapabilityContextRecord.applyTo` 直接调用 `addAllRagContexts` / `addAllToolMethodIds` / `addAllHistory` 而未先执行 `clear*()`，若对非空 `request.toBuilder()` 调用还会产生重复追加。
2. **`HarnessKernelBridge.bridgeExecution` 丢弃了 `PreparedExecution.context()`**：
   - 在 `HarnessKernelBridge.java`（行 123-143）中，`session.prepare(tag, ragContexts, mounted, history)` 生成了 `prepared`（内含 `CapabilityContextRecord`）。
   - 然而 `BridgeExecutionHandle`（行 55-67）仅提取了 `prepared.systemPrompt()`，将 `prepared.context()` 彻底丢弃：
     - **工具挂载断层（行 109-133）**：行 115-122 通过 `resolver.resolve(name)` 将 `request.getToolMethodIdsList()` 解析进了 `mounted` 映射表，并传入 `session.prepare(...)` 写入了 `<tool_guidance>` 提示词；但行 132-133 构造 `wrapped`（即 `effectiveTools`）时，却只遍历了入参 `tools`（仅含知识库工具），**完全漏掉了 `mounted` 中由 `resolver` 解析出的业务工具**！
     - **RAG 与 History 断层**：`prepared.context().ragContexts()` 与 `prepared.context().history()` 未随 `BridgeExecutionHandle` 返回给 `AgentOrchestrator`。
3. **`AgentOrchestrator.executeAgent` 时序倒置与上下文脱节**：
   - 在 `AgentOrchestrator.java` 中，行 445-564 先从原始 `request` 构建了 `effectiveRagContexts`、`tools`、`enabledToolNames`（行 480-490）和 `messages`（行 509-564），直到行 579 才调用 `harnessKernelBridge.bridgeExecution(...)`。
   - 行 592-594 仅接收了 `systemPrompt`、`bridgeTools`、`bridgeResolver`，从未用组装后的 `CapabilityContextRecord` 回写更新 `effectiveRagContexts`、`enabledToolNames`（及行 701 `ReactAgent.builder().toolNames(toolNames)`）或 `messages`。
4. **空扩展能力未诚实降级为 `CapabilityTag.PROMPT_ONLY`**：
   - 在 `HarnessKernelBridge.resolveCapabilityTag`（行 176-200）中：
     - 行 191：`boolean rag = ragContexts != null && !ragContexts.isEmpty();` 未过滤 `preRetrievedContent` 为空白或 `"null"` 的无效条目。
     - 行 192-193：`resolver != null && !request.getToolMethodIdsList().isEmpty()` 仅凭请求带有 `toolMethodIds` 字符串即推断为 `CapabilityTag.TOOL`；当 `resolver.resolve(name)` 实际返回 `null`（导致 `mounted` 为空）时，未诚实降级为 `CapabilityTag.PROMPT_ONLY`。

### 1.2 最小修复契约方案（RAG/TOOL/HISTORY 真实注入与诚实降级）

1. **`CapabilityContextAssembler` 幂等注入与降级契约**：
   - 在 `CapabilityContextRecord.applyTo(ChatRequest.Builder)` 中，先执行 `builder.clearRagContexts().clearToolMethodIds().clearHistory()`，再写入组装后的有效 `ragContexts`、`toolCallbacks.keySet()` 与 `history`。
   - 新增有效载荷过滤与 `resolveEffectiveTag`：当有效 `validRag`、`mountedTools`、`validHistory` 全部为空时，实际生效标签 `effectiveCapabilityTag` **强制诚实降级或标记为 `CapabilityTag.PROMPT_ONLY`**；若请求在 `inputParams` / `parameters` 中显式强制声明了非 `PROMPT_ONLY` 标签但有效载荷为空，则在 `ENFORCE` 下触发 Fail-Close 阻断，同时在回执与凭单中将实际生效能力记录为 `PROMPT_ONLY`。
2. **`HarnessKernelBridge` 与 `AgentOrchestrator` 真实上下文闭环**：
   - `BridgeExecutionHandle` 携带 `ChatRequest effectiveRequest`、`CapabilityContextRecord capabilityContext` 与 `CapabilityTag effectiveCapabilityTag`。
   - 在 `HarnessKernelBridge.bridgeExecution` 中真实调用 `ChatRequest effectiveRequest = prepared.applyTo(request.toBuilder()).build()`，并在 `AgentOrchestrator.executeAgent` 中使用 `handle.capabilityContext()` 驱动下游 `effectiveRagContexts`、`enabledToolNames` 与 `messages`。

---

## 二、Bridge Guard 与 `ReActCycleGuard` 合并去重方案

### 2.1 双层包装（Double Wrapping）与双倍计数根因剖析

在当前 `AgentOrchestrator.java` 与 `HarnessKernelBridge.java` 中，同一个 `ToolCallback` 和 `ToolCallbackResolver` 被先后包装了两次：
1. **第一层包装（`HarnessKernelBridge.java` 行 132-141 & 行 214-255）**：`ENFORCE` 模式下递增 `ExecutionSession.stepIndex` 并在 `LocallyDenseCycleAutomaton` 中执行第 1 次窗口淘汰与频次累加，随后执行 `HoareTripleToolGuard.verifyBefore`。
2. **第二层包装（`AgentOrchestrator.java` 行 655-665 & 行 1521-1574）**：`AgentOrchestrator` 拿到 `bridgeTools` 和 `bridgeResolver` 后，**无条件再次调用 `wrapWithCycleGuard(t, cycleGuard, evidenceCollector)`**，在 `ReActStrictWindowCycleGuard` 中执行第 2 套步数递增与滑动窗口计数！
3. **跨模式 `ToolCallbackResolver` 白名单漏洞**：`OFF` 与 `SHADOW` 模式下 `guardedResolver` 未校验工具名是否属于当前请求的 `enabledToolNames` 白名单；且 `executePlanTasks`（行 1022）直接将未包装的裸 `this.resolver` 传给了 `WorkerAgent`。

### 2.2 三模式（`OFF` / `SHADOW` / `ENFORCE`）统一合并去重契约

- **`OFF` 模式**：仅由 `AgentOrchestrator` 单层守卫包装（执行 `ReActCycleGuard` 与 `HarnessEvidenceCollector`），`ToolCallbackResolver` 强制校验当前请求的 `allowedToolNames` 白名单。
- **`SHADOW` 模式**：单层包装，由 `ReActCycleGuard` 承担熔断控制，同层旁路通知 `HarnessKernelBridge` 记录影子计数与违例，**严禁嵌套第二层 `ToolCallback`**。
- **`ENFORCE` 模式**：**单层合并守卫（禁止 `AgentOrchestrator` 二次包装 `wrapWithCycleGuard`）**。在单一拦截器内按顺序执行：（1）递增唯一步数计数器 1 次；（2）执行统一循环窗口检测；（3）执行 `HoareTripleToolGuard.verifyBefore`；（4）仅当全部通过时调用底层 `original.call(toolInput)` 1 次；（5）执行 `HoareTripleToolGuard.verifyAfter`；（6）直接向 `HarnessEvidenceCollector` 写入真实单次执行/阻断证据。`ToolCallbackResolver` 严格锁定为 `prepared.context().toolCallbacks().keySet()` 白名单。

---

## 三、拒绝结果严禁记为 `success` 的语义修正方案

### 3.1 漏洞取证：为什么被 Guard 拦截的调用全部在证据中记为了 `success = true`？
- `HarnessKernelBridge.java` 行 239 与行 250 在拦截工具调用时**未抛异常，而是正常返回字符串 `"[HARNESS_GUARD_REJECTED:" + violation + "]"`**。
- `AgentOrchestrator.java` 行 1554-1571 的 `wrapWithCycleGuard` 仅在捕获到 `Exception` 时才将 `toolSuccess` 设为 `false`；收到拒绝字符串后 `toolSuccess` 保持为 `true`，在 `finally` 中调用 `evidenceCollector.recordToolExecution(toolName, durationMs, true, toolInput, result)`！

### 3.2 拒绝语义修正的具体代码契约
1. **升级 `HarnessEvidenceRecord.ToolExecutionEntry`**：新增 `boolean guardBlocked` 与 `String violationReason`（强制不变量：`if (guardBlocked && success) throw new IllegalArgumentException(...)`），保留 5 参数向后兼容构造器。
2. **升级 `HarnessEvidenceCollector`**：
   - 新增 `recordGuardBlockedToolExecution(String toolName, long durationMs, String violationReason, String inputRaw, String outputRaw)`，固定写入 `success = false, guardBlocked = true`；
   - 在 `recordToolExecution(...)` 中增加硬防御：凡返回字符串以 `"[HARNESS_GUARD_REJECTED:"`、`"CIRCUIT_BREAKER_"`、`"【系统熔断提示】"` 或 `"【系统纠偏提示】"` 开头，一律强制设为 `success = false, guardBlocked = true`。
3. **升级 `HarnessEvidenceRecord`**：暴露 `boolean guardBlocked()` 与 `int guardBlockedCount()`。

---

## 四、请求级 vs 会话级状态隔离与 Research Ledger

### 4.1 状态隔离边界
- **单次请求级（`requestId`）**：`ExecutionSession`、`CapabilityContextRecord`、`LocallyDenseCycleAutomaton`、`HoareTripleToolGuard` 计数与违例、`UsageCapturingChatModel` 快照、`HarnessEvidenceCollector`、`ScopedVoucherLease`。同时修复 `HermesHarnessKernel` 顶层 `coldBuffer` 与 `cycleAutomaton` 共享可变字段隐患，确保每次会话独立实例化，并将 `AgentOrchestrator.latestEvidenceMap` 改为有界 FIFO/LRU `LinkedHashMap`。
- **跨轮会话级（`conversationId`）**：`MemoryManager` 短期记忆与按会话隔离的 `ReversibleRingColdBuffer`。同时修复 `DefaultOfflineProbeExecutor` 硬编码 `conversationId = 7777L` 导致的跨探针污染，改为按 `probeRequestId` 隔离。

### 4.2 Research Ledger（14 字段规范）
1. **`RL-STAGE8-B-01`（`paper`）**：Shunyu Yao et al., *"ReAct: Synergizing Reasoning and Acting in Language Models"*, ICLR 2023 (`arXiv:2210.03629v3`, 已读 Abstract, Sec 2, Sec 3.2, Sec 3.3 & Table 2, `VERIFIED`)。
2. **`RL-STAGE8-B-02`（`paper`）**：Shi-Qi Yan et al., *"Corrective Retrieval Augmented Generation"*, 2024 (`arXiv:2401.15884v2`, 已读 Abstract, Sec 4.1-4.3, Sec 5.4, `VERIFIED`)。
3. **`RL-STAGE8-B-03`（`production-implementation`）**：`Spring_Agent (qknow-hermes-core)` (`commit: 362074a0`, 已读 `CapabilityContextAssembler.java` L1-62, `HermesHarnessKernel.java` L1-198, `HarnessKernelBridge.java` L1-338, `AgentOrchestrator.java` L1-1659, `ReActStrictWindowCycleGuard.java` L1-240, `HarnessEvidenceCollector.java` L1-171, `HarnessEvidenceRecord.java` L1-160, `VERIFIED`)。
