# Hermes Harness² 阶段八（Stage 8-lite）：PPC 最小使能底座冻结任务书

- **文档编号**：`53-harness8-taskbook.md`
- **任务书状态**：`FROZEN`（已冻结）
- **基线分支与提交**：`feat/hermes-harness-stage7` (`HEAD: 362074a0`)
- **目标工作分支**：`feat/hermes-harness-stage8`（从完成阶段 0 归档后的 `feat/hermes-harness-stage7` 切出，严禁修改 `main`）
- **Java 编译与运行环境**：`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`
- **模型与向量基线**：DeepSeek 官方活跃模型；千问 `text-embedding-v4`（锁定 `1024` 维单位超球面 $\mathbb{S}^{1023}$）
- **生产默认配置**：`SINGLE`；`KernelBridgeMode.OFF`；`hermes.orchestration.enabled=false`
- **物理派发三智能体审计存证**：
  - **Agent A（可信计量审计员）**：[`fa4b2275-26a7-466a-aacc-c6eeecab9868`](conversation://fa4b2275-26a7-466a-aacc-c6eeecab9868) | [`52-harness8-agent-A.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/52-harness8-agent-A.md) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/fa4b2275-26a7-466a-aacc-c6eeecab9868/.system_generated/logs/transcript.jsonl)
  - **Agent B（能力与 Guard 语义审计员）**：[`aabfc864-b43f-458d-bfce-6fe994fed764`](conversation://aabfc864-b43f-458d-bfce-6fe994fed764) | [`52-harness8-agent-B.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/52-harness8-agent-B.md) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/aabfc864-b43f-458d-bfce-6fe994fed764/.system_generated/logs/transcript.jsonl)
  - **Agent C（PPC 使能审计员）**：[`670a26dd-c4fd-4aa9-a5a7-ffe2b803a93d`](conversation://670a26dd-c4fd-4aa9-a5a7-ffe2b803a93d) | [`52-harness8-agent-C.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/52-harness8-agent-C.md) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/670a26dd-c4fd-4aa9-a5a7-ffe2b803a93d/.system_generated/logs/transcript.jsonl)
- **前置调研与辩论文档**：[`52-harness8-research.ledger.json`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/52-harness8-research.ledger.json)、[`52-harness8-research.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/52-harness8-research.md)、[`52-harness8-audit.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/52-harness8-audit.md)、[`52-harness8-debate.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/52-harness8-debate.md)
- **正文哈希（`body_sha256`，从 `^## 一、` 起算至文件末尾）**：`6947491515fe12dfd8e969050cab4f580155e946c3cb36c5826faa8cb042dcf8`
- **全文件哈希（`full_file_sha256`）**：见伴生文件 [`53-harness8-taskbook.md.sha256`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/53-harness8-taskbook.md.sha256)

---

## 一、范围与定位（仅 PPC 最小使能，不做完整生产认证）

### 1.1 阶段八核心使命（5 项最小使能底座）
阶段八不是企业生产加固终点，而是**专门服务于 Stage 9 真实 PPC（Propose-Probe-Compare）所必需的最小使能底座**。本阶段严格收敛于以下 5 件事：
1. **可信 Usage 计量**：从底层 `ChatResponse.getMetadata().getUsage()` 读取真实 Token 消耗；当响应缺失 `Usage` 时显式标记为 `UsageStatus.UNKNOWN`，严禁用 `0` 或字符除 2 估算值（`estimateTokenCount`）冒充真实 `UsageStatus.KNOWN`。
2. **真实能力上下文注入**：`CapabilityContextAssembler.CapabilityContextRecord` 与 `HermesHarnessKernel.PreparedExecution` 必须通过 `applyTo` 真正进入 `ChatRequest` 与 `AgentOrchestrator` 实际执行上下文；未注入任何有效扩展能力时必须诚实降级或标记为 `CapabilityTag.PROMPT_ONLY`。
3. **Guard / Cycle 语义合并与修正**：消除 `HarnessKernelBridge.guard` 与 `AgentOrchestrator.wrapWithCycleGuard`（`ReActCycleGuard`）的双层重复包装与重复计数；被 Guard/Cycle 拒绝的调用不得执行底层工具，**严禁在 `HarnessEvidenceCollector` 中记为 `success = true`**，必须在证据中明确标记 `success = false, guardBlocked = true`；`ToolCallbackResolver` 仅允许解析当前请求声明的白名单工具。
4. **RAG 归因全链路打通**：消除 `AgentOrchestrator`、`HarnessKernelBridge`、`HermesHarnessKernel` 三处硬编码 `0`，使 `completeExecution` 接收并传递真实的 `ragProvided`、`ragUsed`、`ragScore` 至 `TrajectoryEvidence`；无 RAG 时明确标记 `RagAttributionStatus.NO_RAG`。
5. **可供 Stage 9 PPC 消费的最小执行记录**：在现有 `HarnessEvidenceRecord` 基础上以向后兼容方式新增 `usageStatus`、`capabilityTag`、`guardBlocked`、`guardBlockedCount`、`ragAttribution` 5 个最小必需字段。

### 1.2 明确不做的事项（硬性排除边界）
- **不做**完整生产认证（OAuth2 / JWT / 多租户 RBAC）；
- **不做**无限嵌套 JSON Schema 解析器（保持顶层必填字段与基础类型校验）；
- **不做**企业级 SLA、运维监控大盘或告警；
- **不做**付费 Live E2E 测试（默认 `mvn test` 零外网、零 API Key）；
- **不做** MCP stdio 或 HITL 人工审批改造；
- **不做**在线自修改（Online Self-Modification）；
- **不做** Rust / C++ / Go 跨语言重写；
- **不修改** `main` 分支，不修改 `experiment/outputs/harness4` 与 `experiment/outputs/harness5` 既有归档产物。

### 1.3 阶段 0：归档 Stage 7 并建立 Stage 8 分支操作契约
在开始 Stage 8 代码修改前，实施工程师必须按顺序执行：
1. 在 `feat/hermes-harness-stage7` 分支校验 `shasum -a 256 -c docs/architecture-review-20261019/acceptance/50-harness7-taskbook.md.sha256` 为 `OK`；
2. 将工作区遗留的 Stage 7 归档文件（`49-harness7-*` 与 `50-harness7-taskbook*`）独立提交：
   ```text
   docs(hermes): 归档阶段七桥接研究与实施报告

   - 归档阶段七三智能体独立审计报告、交叉辩论与桥接设计文档
   - 归档阶段七冻结任务书及双哈希校验文件
   ```
3. 从 `feat/hermes-harness-stage7` 切出新分支：
   ```bash
   git checkout -b feat/hermes-harness-stage8
   ```

---

## 二、模块 1：可信 Usage 计量契约（解决估算冒充真实）

### 2.1 `UsageStatus` 枚举与 `HarnessUsageSnapshot` 定义
在 `tech.qiantong.qknow.hermes.agent.harness` 包下新增 `UsageStatus.java` 与 `HarnessUsageSnapshot.java`：
```java
package tech.qiantong.qknow.hermes.agent.harness;

/**
 * 物理 Token 计量可信度状态枚举。
 * 铁律：严禁使用 0 或字符启发式估算值 (estimateTokenCount) 冒充 KNOWN。
 */
public enum UsageStatus {
    /** 所有 LLM 调用均从 ChatResponse.getMetadata().getUsage() 获取到真实非空正数 Token 统计 */
    KNOWN,
    /** 模型响应缺失 Usage（如 Mock/Fake 模型）、流中途中断/异常或未发生可验证的真实计量 */
    UNKNOWN
}
```
```java
package tech.qiantong.qknow.hermes.agent.harness;

import java.util.Objects;

public record HarnessUsageSnapshot(
        UsageStatus usageStatus,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long cacheHitTokens,
        long reasoningTokens,
        double actualCostCny,
        int meteredCallCount,
        int unmeteredCallCount
) {
    public HarnessUsageSnapshot {
        usageStatus = Objects.requireNonNull(usageStatus, "usageStatus");
        if (meteredCallCount <= 0 || unmeteredCallCount > 0 || totalTokens <= 0L) {
            usageStatus = UsageStatus.UNKNOWN;
        }
    }

    public static HarnessUsageSnapshot unknown() {
        return new HarnessUsageSnapshot(UsageStatus.UNKNOWN, 0L, 0L, 0L, 0L, 0L, 0.0D, 0, 0);
    }

    public boolean isKnown() {
        return usageStatus == UsageStatus.KNOWN && meteredCallCount > 0 && unmeteredCallCount == 0 && totalTokens > 0L;
    }
}
```

### 2.2 请求级 `UsageCapturingChatModel` 拦截器
在 `tech.qiantong.qknow.hermes.agent.harness` 包下新增 `UsageCapturingChatModel implements ChatModel`：
1. **包装挂载**：在 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java) 的单次请求执行入口中，用请求级 `UsageCapturingChatModel` 包装 `chatModel`（及工具路由 `agentModel`）。
2. **真实 Usage 提取规则**：
   - 从 `ChatResponse.getMetadata().getUsage()` 读取 `Usage`；
   - 当且仅当 `usage != null` 且 `(promptTokens > 0 || completionTokens > 0 || totalTokens > 0)` 时判定为有效真实 `Usage`；
   - 若 `usage.getNativeUsage()` 为 Jackson `JsonNode` 或 `Map`，同步提取 `prompt_cache_hit_tokens` 与 `completion_tokens_details.reasoning_tokens`（或 `reasoning_tokens`）。
3. **非流式与流式生命周期归并**：
   - `call(Prompt)`：若返回有效 `Usage`，累加 Token 并递增 `meteredCallCount`；否则递增 `unmeteredCallCount`。
   - `stream(Prompt)`：在 `Flux.defer` 内为单次流维护局部 `AtomicReference<Usage> streamUsageRef`；在 `doOnNext` 中若 chunk 携带有效 `Usage` 则更新 `streamUsageRef`（中间普通文本 delta chunk 无 `Usage` 时忽略，不做任何估算）；在 `doOnComplete` 时若 `streamUsageRef.get() != null` 则一次性计入请求级累加器并递增 `meteredCallCount`，若为 `null` 则递增 `unmeteredCallCount`；在 `doOnError` / `doOnCancel`（且未收到末帧 `Usage`）时递增 `unmeteredCallCount`。
4. **禁止用估算或 `0` 冒充真实**：
   - 在 `AgentOrchestrator` 结束回调中，从 `usageCapturingChatModel.snapshot()` 获取 `HarnessUsageSnapshot`：
     - 当 `snapshot.isKnown()` 为 `true` 时：向 `HarnessEvidenceCollector` 写入 `UsageStatus.KNOWN`、真实 `totalTokens` 与 `actualCostCny`；
     - 当 `snapshot.isKnown()` 为 `false` 时：向 `HarnessEvidenceCollector` 写入 `UsageStatus.UNKNOWN`，**严禁调用 `estimateTokenCount(text)` 写入 `HarnessEvidenceRecord` 冒充真实 Token**！

### 2.3 预算判断规则与 PPC 遇到 `UNKNOWN` 的处理契约
1. **运行期预算判断（`HarnessKernelBridge` & `HoareTripleToolGuard`）**：
   - `HarnessKernelBridge` 支持关联当前请求的实时 `Supplier<HarnessUsageSnapshot>`（同时兼容单测在工具参数中显式传入的 `_estimatedTokens` / `_estimatedCostCny`）；
   - 当 `UsageStatus == KNOWN`（或显式传入测试探针预算参数）时，严格执行 `GUARDRAIL_TOKEN_BUDGET` 与 `GUARDRAIL_COST_BUDGET` 校验；
   - 当 `UsageStatus == UNKNOWN` 时，不基于 `0` 伪造“预算合规通过”，在 `KernelBridgeReceipt` 与 `HarnessEvidenceRecord` 中诚实记录 `UsageStatus.UNKNOWN`，同时 100% 照常执行步数上限、循环窗口、工具白名单与必填参数守卫。
2. **离线 PPC 遇到 `UNKNOWN` 的比较语义（`ProbeExecutionDiff` / `EvidenceDiffExtractor` / `DualBlindSelectionEvaluator` / `PositionSwapBlindEvaluator`）**：
   - `ProbeExecutionDiff` 新增 `UsageStatus baselineUsageStatus`、`UsageStatus candidateUsageStatus` 与 `boolean usageComparable`（当且仅当两者均为 `UsageStatus.KNOWN` 时 `usageComparable == true`）；
   - **`ProbeExecutionDiff.satisfiesBudgetGate()` 硬门禁**：
     ```java
     public boolean satisfiesBudgetGate() {
         return usageComparable && deltaTokens <= 0 && deltaDurationMs <= 5;
     }
     ```
   - 当 Baseline 或 Candidate 任一侧为 `UsageStatus.UNKNOWN` 时，`usageComparable == false`，`satisfiesBudgetGate()` 与 `isEligibleForAdoption()` 强制返回 `false`，`DualBlindSelectionEvaluator` 与 `PositionSwapBlindEvaluator` 直接拒绝采纳 Candidate 并回退 Baseline。

---

## 三、模块 2：真实能力上下文注入契约（RAG / TOOL / HISTORY）

### 3.1 `CapabilityContextAssembler` 幂等注入与 `PROMPT_ONLY` 诚实降级
修改 [`CapabilityContextAssembler.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/CapabilityContextAssembler.java)：
1. **幂等 `applyTo(ChatRequest.Builder)`**：
   ```java
   public ChatRequest.Builder applyTo(ChatRequest.Builder builder) {
       Objects.requireNonNull(builder, "builder");
       return builder
               .clearRagContexts()
               .addAllRagContexts(ragContexts)
               .clearToolMethodIds()
               .addAllToolMethodIds(toolCallbacks.keySet())
               .clearHistory()
               .addAllHistory(history);
   }
   ```
2. **有效载荷清洗与 `PROMPT_ONLY` 判定**：
   - 过滤掉 `preRetrievedContent` 为 `null`、空白或 `"null"` 的无效 `RAGContext`；
   - 过滤掉未解析出非空 `ToolCallback` 的工具名；
   - 过滤掉内容为空白的 `ChatMessage`；
   - 当请求未显式强制声明扩展能力，且清洗后的有效 `ragContexts`、`toolCallbacks`、`history` 全为空时，实际生效的 `CapabilityTag` **必须标记或降级为 `CapabilityTag.PROMPT_ONLY`**；
   - 当请求显式强制声明了扩展能力（如 `RAG`、`TOOL`、`HISTORY`、`COMPOSITE`，兼容别名 `RAG_ENABLED`、`TOOL_AUGMENTED`），但对应有效载荷为空时：在 `ENFORCE` 模式下保持 Fail-Close 阻断，同时在回执与执行凭单中将实际生效能力记录为 `PROMPT_ONLY`。

### 3.2 `HarnessKernelBridge` 与 `AgentOrchestrator` 真实注入闭环
1. 在 [`HarnessKernelBridge.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HarnessKernelBridge.java) 的 `bridgeExecution` 中：
   - 合并知识库 `tools` 与 `resolver` 解析出的业务工具构建完整的 `mounted` 工具映射；
   - 调用 `PreparedExecution prepared = session.prepare(...)` 后，**真实调用 `ChatRequest effectiveRequest = prepared.applyTo(request.toBuilder()).build()`**；
   - 在 `BridgeExecutionHandle` 中返回 `effectiveRequest`、`prepared.context()`（`CapabilityContextRecord`）及实际生效的 `CapabilityTag`。
2. 在 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java) 的 `executeAgent` 中：
   - 当 `handle.capabilityContext() != null` 时，使用 `handle.capabilityContext()` 真实更新后续执行所需的 `effectiveRagContexts`、`enabledToolNames`（及 `toolNames` 数组）、`bridgeTools`（包含全部已挂载的 `mounted` 工具）与历史 `messages`；
   - 将实际生效的 `handle.effectiveCapabilityTag().name()` 写入 `evidenceCollector.recordCapabilityTag(...)`。

---

## 四、模块 3：Guard / Cycle 职责合并与拒绝语义修正契约

### 4.1 消除双层重复包装与重复计数
1. **`ENFORCE` 模式单层统一守卫**：
   - 在 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java) 中，当 `HarnessKernelBridge` 处于 `ENFORCE` 模式时，工具列表与 `ToolCallbackResolver` 由 `HarnessKernelBridge` 统一完成单层守卫包装（传入 `evidenceCollector`），**`AgentOrchestrator` 不再二次调用 `wrapWithCycleGuard` / `wrapResolverWithCycleGuard`**。
   - 单次工具调用在 `ENFORCE` 模式下只递增 1 次步数、只进入 1 次循环窗口自动机（`LocallyDenseCycleAutomaton`）、只执行 1 次 `HoareTripleToolGuard` 前后置校验。
2. **`OFF` 与 `SHADOW` 模式单层包装**：
   - `OFF` 模式仅由 `AgentOrchestrator` 的单层 `wrapWithCycleGuard` 包装；
   - `SHADOW` 模式由 `wrapWithCycleGuard` 承担实际熔断，同层旁路通知 `HarnessKernelBridge` 记录影子计数与违例，不套第二层 `ToolCallback`。
3. **全模式 `ToolCallbackResolver` 白名单强约束**：
   - 无论 `OFF`、`SHADOW` 还是 `ENFORCE`，`ToolCallbackResolver` 仅允许解析当前请求声明并启用（在 `ENFORCE` 下为 `prepared.context().toolCallbacks().keySet()`）的白名单工具；请求未声明的工具名一律拦截并返回 `null`（`ENFORCE` / `SHADOW` 下同步记录 `PERMISSION` 违例）。

### 4.2 拒绝结果严禁记为 `success`
1. **被拒绝调用不执行底层工具**：
   - 前置守卫（步数超限、循环窗口拦截、非白名单工具、缺失顶层必填参数、超出 Token/Cost 预算）一旦拒绝，**立即短路返回拒绝提示字符串 `"[HARNESS_GUARD_REJECTED:" + violation + "]"`，绝不调用底层 `delegate.call(...)`**。
2. **证据收集器强制记为 `success = false, guardBlocked = true`**：
   - 扩充 [`HarnessEvidenceRecord.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessEvidenceRecord.java) 中的 `ToolExecutionEntry`：
     ```java
     public record ToolExecutionEntry(
             String toolName,
             long durationMs,
             boolean success,
             boolean guardBlocked,
             String violationReason,
             String inputDigest,
             String outputDigest
     ) {
         public ToolExecutionEntry {
             toolName = Objects.requireNonNull(toolName, "toolName");
             violationReason = violationReason == null ? "" : violationReason;
             inputDigest = inputDigest == null ? "" : inputDigest;
             outputDigest = outputDigest == null ? "" : outputDigest;
             if (guardBlocked && success) {
                 throw new IllegalArgumentException("Guard-blocked tool execution must not be marked as success");
             }
         }

         public ToolExecutionEntry(String toolName, long durationMs, boolean success,
                                   String inputDigest, String outputDigest) {
             this(toolName, durationMs, success, false, "", inputDigest, outputDigest);
         }
     }
     ```
   - 在 [`HarnessEvidenceCollector.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessEvidenceCollector.java) 中：
     - 新增 `recordGuardBlockedToolExecution(String toolName, long durationMs, String violationReason, String inputRaw, String outputRaw)`，固定写入 `success = false, guardBlocked = true, violationReason = violationReason`；
     - 在 `recordToolExecution(...)` 中加入防御性检查：若 `outputRaw` 以 `"[HARNESS_GUARD_REJECTED:"`、`"CIRCUIT_BREAKER_"`、`"【系统熔断提示】"` 或 `"【系统纠偏提示】"` 开头，强制将 `success` 设为 `false`、`guardBlocked` 设为 `true`！
3. **修复 `HermesHarnessKernel` 请求级隔离隐患**：
   - 确保每次 `openSession` 创建独立的请求级 `LocallyDenseCycleAutomaton` 与 `ReversibleRingColdBuffer`，不在跨请求间共享可变窗口状态。

---

## 五、模块 4：RAG 归因全链路打通契约

### 5.1 `RagAttributionStatus` 与 `RagAttributionSummary`
在 [`HarnessEvidenceRecord.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessEvidenceRecord.java)（或同包）中定义：
```java
public enum RagAttributionStatus {
    /** 未启用 RAG 或有效注入文档数为 0（ragProvided == 0） */
    NO_RAG,
    /** 注入了有效文档但全部未被最终输出/知识库工具采纳（ragProvided > 0 && ragUsed == 0） */
    UNUSED,
    /** 部分有效文档被采纳（ragProvided > 0 && 0 < ragUsed < ragProvided） */
    PARTIALLY_USED,
    /** 全部有效文档被采纳（ragProvided > 0 && ragUsed >= ragProvided） */
    FULLY_USED;

    public static RagAttributionStatus resolve(int ragProvided, int ragUsed) {
        if (ragProvided <= 0) {
            return NO_RAG;
        }
        if (ragUsed <= 0) {
            return UNUSED;
        }
        return ragUsed >= ragProvided ? FULLY_USED : PARTIALLY_USED;
    }
}

public record RagAttributionSummary(
        RagAttributionStatus status,
        int ragProvided,
        int ragUsed,
        double ragScore,
        double ragUnderutilization
) {
    public RagAttributionSummary {
        status = status == null ? RagAttributionStatus.NO_RAG : status;
        ragProvided = Math.max(0, ragProvided);
        ragUsed = Math.max(0, Math.min(ragProvided, ragUsed));
        ragScore = Double.isFinite(ragScore) ? Math.max(0.0, ragScore) : 0.0;
        ragUnderutilization = Double.isFinite(ragUnderutilization)
                ? Math.max(0.0, Math.min(1.0, ragUnderutilization)) : 0.0;
    }

    public static RagAttributionSummary noRag() {
        return new RagAttributionSummary(RagAttributionStatus.NO_RAG, 0, 0, 0.0, 0.0);
    }

    public static RagAttributionSummary of(int ragProvided, int ragUsed, double ragScore) {
        if (ragProvided <= 0) {
            return noRag();
        }
        int clampedUsed = Math.max(0, Math.min(ragProvided, ragUsed));
        RagAttributionStatus st = RagAttributionStatus.resolve(ragProvided, clampedUsed);
        double underutilization = 1.0 - ((double) clampedUsed / ragProvided);
        return new RagAttributionSummary(st, ragProvided, clampedUsed, ragScore, underutilization);
    }

    public String canonical() {
        return status.name() + ":" + ragProvided + ":" + ragUsed + ":" + ragScore + ":" + ragUnderutilization;
    }
}
```

### 5.2 消除三处硬编码 `0` 并打通 `completeExecution`
1. **确定性计算 `ragProvided`、`ragUsed`、`ragScore`**：
   - `ragProvided`：实际注入 Prompt 的非空白且未被 CRAG `isIncorrect()` 阻断的有效 `RAGContext` 文档数量；
   - `ragUsed`：当 `ragProvided == 0` 或输出为空/错误兜底时为 `0`；当 `ragProvided > 0` 时，统计在最终输出文本中命中知识库名称/ID、成功执行对应知识库查询工具、或命中文档内容 $\ge 2$ 个有效核心词元（长度 $\ge 2$）的有效文档数（满足 $0 \le \text{ragUsed} \le \text{ragProvided}$）；
   - `ragScore`：当 `ragProvided == 0` 时为 `0.0D`；当 `ragProvided > 0` 时取 `retrievalEvaluation` 置信度或有效注入文档的最高余弦相似度（裁剪至 $[0.0, 1.0]$）。
2. **打通 `AgentOrchestrator -> HarnessKernelBridge -> HermesHarnessKernel -> TrajectoryAttributionAnalyzer`**：
   - `TrajectoryAttributionAnalyzer.TrajectoryEvidence` 增加 `double ragScore`（保留 5 参数兼容构造器），`AttributionVector` 增加 `RagAttributionStatus ragStatus` 与 `double ragScore`（保留 3 参数兼容构造器）；
   - `HermesHarnessKernel.ExecutionSession` 新增 `evaluateTrajectory(long reasoningTokens, long answerTokens, int ragProvided, int ragUsed, double ragScore, List<ParetoFrontierComposer.Candidate> candidates)`，不再硬编码 `0, 0, 0, 0`；
   - `HarnessKernelBridge` 新增 `completeExecution(String requestId, BridgeExecutionHandle handle, long reasoningTokens, long answerTokens, int ragProvided, int ragUsed, double ragScore)`，将真实指标传入 `session.evaluateTrajectory(...)` 并保存在 `KernelBridgeReceipt` 中；
   - `AgentOrchestrator` 在请求完成时计算真实的 `RagAttributionSummary`，同时写入 `evidenceCollector.recordRagAttribution(...)` 与 `harnessKernelBridge.completeExecution(...)`。

---

## 六、模块 5：可供 Stage 9 PPC 消费的最小执行记录契约

### 6.1 `HarnessEvidenceRecord` 向后兼容扩充（15 $\to$ 20 字段）
在 [`HarnessEvidenceRecord.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessEvidenceRecord.java) 中扩充主 Record 组件：
```java
public record HarnessEvidenceRecord(
        String evidenceId,
        String requestId,
        Long botId,
        String voucherFingerprint,
        String voucherVersion,
        Instant timestamp,
        String userInputSummary,
        int ragDocumentCount,
        double ragTopScore,
        List<ToolExecutionEntry> toolExecutions,
        long estimatedTokens,
        double estimatedCostCny,
        long executionDurationMs,
        String finalOutputSummary,
        ExecutionMode executionMode,
        // === Stage 8-lite 新增的 5 个 Stage 9 PPC 最小使能字段 ===
        UsageStatus usageStatus,
        String capabilityTag,
        boolean guardBlocked,
        int guardBlockedCount,
        RagAttributionSummary ragAttribution
)
```
- **向后兼容构造器**：
  - 保留原有 14 参数与 15 参数构造器，委托调用 20 参数主构造器：默认 `usageStatus = UsageStatus.UNKNOWN`，`capabilityTag = "PROMPT_ONLY"`，`guardBlocked` 与 `guardBlockedCount` 从 `toolExecutions` 自动派生（统计 `ToolExecutionEntry::guardBlocked`），`ragAttribution` 当 `ragDocumentCount <= 0` 时默认为 `RagAttributionSummary.noRag()`。
- **纳入 SHA-256 常量时间验签**：
  - 在 `computeDigest()` 中将 `usageStatus.name()`、`capabilityTag`、`guardBlocked`、`guardBlockedCount`、`ragAttribution.canonical()` 纳入摘要计算（或在保持旧构造兼容的前提下精确覆盖新增字段），确保 `verifyDigest` 使用 `MessageDigest.isEqual` 常量时间比对通过。

---

## 七、测试矩阵与门禁验证计划（Stage 5）

### 7.1 新增确定性测试类：`HermesHarnessStage8PpcReadinessTest`
在 `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HermesHarnessStage8PpcReadinessTest.java` 中编写 **6 个确定性单元测试**（默认无外网、无 API Key、不带 `@Tag("live")`）：
1. **`shouldCaptureRealUsageFromChatResponseAndMarkKnown`**：
   验证当底层 `ChatModel`（非流式或流式末帧）在 `ChatResponse.getMetadata().getUsage()` 中返回真实 `DefaultUsage(120, 40, 160)` 时，`HarnessEvidenceRecord.usageStatus()` 为 `UsageStatus.KNOWN`，且记录的 Token 数精确等于 `160L`（绝非字符估算值）。
2. **`shouldMarkUsageUnknownWhenModelOmitsUsageAndBlockPpcAdoption`**：
   验证当 Mock/Fake `ChatModel` 未返回 `Usage`（或流中断）时，`HarnessEvidenceRecord.usageStatus()` 必须为 `UsageStatus.UNKNOWN`（严禁用 `0` 或字符估算冒充 `KNOWN`）；且在 `EvidenceDiffExtractor` / `ProbeExecutionDiff` / `DualBlindSelectionEvaluator` 中，任一侧为 `UNKNOWN` 时 `usageComparable == false`、`satisfiesBudgetGate() == false`、拒绝采纳候选凭单。
3. **`shouldInjectCapabilityContextIntoChatRequestAndDowngradeEmptyToPromptOnly`**：
   验证 `CapabilityContextRecord.applyTo` 与 `HarnessKernelBridge.bridgeExecution` 真正将清洗后的 `ragContexts`、`toolMethodIds`、`history` 注入 `effectiveRequest` 与执行上下文；当未提供任何有效扩展载荷时，实际生效的 `capabilityTag` 诚实标记为 `PROMPT_ONLY`。
4. **`shouldUnifyGuardWithoutDoubleCountingAndMarkRejectedToolAsFailedAndBlocked`**：
   验证在 `ENFORCE` 模式下工具与 `resolver` 仅被单层守卫包装（单次调用步数只递增 1 次）；当触发非白名单工具、缺失必填字段或循环窗口拦截时，底层工具执行次数为 `0`，且 `HarnessEvidenceRecord` 中该次调用 `success == false`、`guardBlocked == true`、`guardBlockedCount >= 1`（彻底消除被误记为 `success = true` 的缺陷）。
5. **`shouldPropagateRealRagAttributionAndDistinguishNoRagFromUsed`**：
   验证 `completeExecution` 将真实的 `ragProvided`、`ragUsed`、`ragScore` 传入 `TrajectoryAttributionAnalyzer`：无 RAG 时状态为 `RagAttributionStatus.NO_RAG`；有 RAG 但未命中时为 `UNUSED`（`ragUnderutilization == 1.0`）；全部命中时为 `FULLY_USED`（`ragUnderutilization == 0.0`）。
6. **`shouldProduceCompleteStage9PpcReadyEvidenceRecordWithValidDigest`**：
   验证 `HarnessEvidenceRecord` 同时包含 `usageStatus`、`capabilityTag`、`guardBlocked`、`guardBlockedCount`、`ragAttribution` 五大新字段，且 `verifyDigest` 常量时间验签通过，旧 14/15 参数构造器完全向后兼容。

### 7.2 三轨验证命令与精确统计要求
1. **阶段八与历史 Harness 定向测试套件**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
   mvn -f backend/pom.xml test -pl qknow-hermes/qknow-hermes-core \
     -Dtest=HermesHarnessContractTest,HermesHarnessStage2RuntimeTest,HermesHarnessStage3OfflineLoopTest,HermesHarnessStage4ContractTest,HermesHarnessKernelArchitectureTest,HermesHarnessStage7BridgeTest,HermesHarnessStage8PpcReadinessTest
   ```
2. **默认全量测试门禁（无外网、无 API Key、自动排除 `live`）**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
   mvn -f backend/pom.xml test
   ```
3. **测试后工作区干净度与归档产物保护检查**：
   - 执行 `git status --short`，确认默认测试未重写 `experiment/outputs/harness4` 或 `experiment/outputs/harness5`；
   - 在实施报告 `54-harness8-implementation.md` 中分别报告三轨精确数字：
     - **A. Stage 8 新增测试数**（`HermesHarnessStage8PpcReadinessTest`）；
     - **B. `qknow-hermes-core` 模块测试总数**；
     - **C. 全仓默认 `mvn test` 总数**。

---

## 八、交付物清单与 Git 提交规范契约（Stage 6）

### 8.1 阶段八完整交付物清单
位于 `docs/architecture-review-20261019/acceptance/`：
1. `52-harness8-research.ledger.json`
2. `52-harness8-research.md`
3. `52-harness8-audit.md`
4. `52-harness8-agent-A.md`
5. `52-harness8-agent-B.md`
6. `52-harness8-agent-C.md`
7. `52-harness8-debate.md`
8. `53-harness8-taskbook.md`
9. `53-harness8-taskbook.md.sha256`
10. `54-harness8-implementation.md`（由实施工程师在完成代码与测试后生成）

### 8.2 Git 提交格式铁律（严格遵守 `AGENTS.md` 第八章）
- **提交 1（在 `feat/hermes-harness-stage7` 分支上完成 Stage 7 遗留归档）**：
  ```text
  docs(hermes): 归档阶段七桥接研究与实施报告

  - 归档阶段七三智能体独立审计报告、交叉辩论与桥接设计文档
  - 归档阶段七冻结任务书及双哈希校验文件
  ```
- **提交 2（在 `feat/hermes-harness-stage8` 分支上完成 Stage 8 最小使能实现与文档归档）**：
  ```text
  feat(hermes): 完成阶段八PPC最小使能底座与可信归因闭环

  - 引入 UsageStatus 与 UsageCapturingChatModel 实现真实 Token 计量并拦截 UNKNOWN 预算比较
  - 打通 CapabilityContextRecord 真实上下文注入并将空扩展能力诚实降级为 PROMPT_ONLY
  - 合并 Bridge Guard 与 ReActCycleGuard 消除重复计数并将被拒绝调用记为失败与 guardBlocked
  - 打通 RAG 归因全链路指标传递并向后兼容扩充 HarnessEvidenceRecord 与通过 Stage 8 单测
  ```

### 8.3 双 Hash 冻结校验规则
1. **正文 Hash（`body_sha256`）**：
   ```bash
   awk '/^## 一、/{flag=1} flag' docs/architecture-review-20261019/acceptance/53-harness8-taskbook.md | shasum -a 256
   ```
   必须与本文件头部声明的 `body_sha256` 完全一致。
2. **全文件 Hash（`full_file_sha256`）**：
   ```bash
   (cd docs/architecture-review-20261019/acceptance && shasum -a 256 -c 53-harness8-taskbook.md.sha256)
   ```
   必须输出 `53-harness8-taskbook.md: OK`。
