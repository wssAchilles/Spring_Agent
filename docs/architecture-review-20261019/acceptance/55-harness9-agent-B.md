# 智能体 B：阶段九（Stage 9）真实 Probe 与成本工程设计规格报告

- **物理子智能体 `conversationId`**：[`882b1226-330c-42aa-aa8d-b19b25567031`](conversation://882b1226-330c-42aa-aa8d-b19b25567031)
- **物理运行 Transcript 存证**：[transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/882b1226-330c-42aa-aa8d-b19b25567031/.system_generated/logs/transcript.jsonl)
- **呈报角色**：智能体 B — 真实 Probe 与成本工程师 (Real Probe & Cost Engineer)
- **证据等级**：Auditor (Tier 3) —— 已完成对 `UsageCapturingChatModel`、`HarnessUsageSnapshot`、`HarnessEvidenceRecord`、`HarnessEvidenceCollector`、`HarnessKernelBridge`、`AgentOrchestrator`、`DeepSeekCostGovernor`、`DeepSeekCompatibleChatModel` 及 `.gitignore` 的 100% 源码法医级审计。

---

## 一、源码法医审计发现（Key Forensic Findings）

1. **`UsageCapturingChatModel.java`（行 74–81）硬编码零成本缺陷**：
   - `record(Usage usage)`（行 89–106）已能从 Spring AI `Usage` 及其 `nativeUsage`（`JsonNode` 或 `Map`）中提取 `promptTokens`、`completionTokens`、`totalTokens`、`prompt_cache_hit_tokens` 与 `completion_tokens_details.reasoning_tokens`。
   - 但 `snapshot()`（行 78）将 `actualCostCny` 写死为 `0.0D`，导致 `HarnessEvidenceCollector.finalizeEvidence()`（行 144）在 `usageKnown == true` 时写入的 `estimatedCostCny` 恒为 `0.0D`。
2. **`HarnessUsageSnapshot.java`（行 16–29）成本已知性（`costKnown`）判定缺失**：
   - 当前 `isKnown()` 仅检查 `usageStatus == UsageStatus.KNOWN && meteredCallCount > 0 && unmeteredCallCount == 0 && totalTokens > 0L`，未对 `actualCostCny` 是否经过真实计费做显式契约约束。若 `UsageStatus == UNKNOWN` 时返回 `actualCostCny = 0.0D`，下游若直接按数值比较成本，会把 `0.0D` 误判为“免费零成本”。
3. **`DeepSeekCostGovernor.java`（行 33–38）定价表过时且缺乏分时段感知**：
   - 行 34 将 `deepseek-flash` 定价硬编码为 `new ModelPricingNano(100L, 500L, 2_000L)`（即命中 0.1 元/M、未命中 0.5 元/M、输出 2.0 元/M），未对齐 DeepSeek 官方最新 `deepseek-flash`（指向 `DeepSeek-V4.1-Flash-1017`）的北京时间高峰/空闲分时段价格体系。
4. **`DeepSeekCompatibleChatModel.java`（行 214–238）单次输出上限传递缺口**：
   - `buildRequestBody(Prompt prompt, boolean stream)` 仅在 `options instanceof DeepSeekChatOptions` 时读取 `maxTokens`（行 235–237），若通过通用 `ChatOptions` 或 `ToolCallingChatOptions` 传入 `maxTokens`，请求体不会携带 `"max_tokens"` 字段。
5. **`AgentOrchestrator.java`（行 469、649、680、763）单次 Probe 调用放大风险点**：
   - 默认路径下若未显式关闭 `PlanSolveConfig`（`createPlannerSupervision` + `planAndSolve` + `recoverReactFailure`）或未收紧 `GuardrailsSpecificationRecord.maxCycleSteps`（默认 10 步），单个复杂问题 Probe 可能触发 4～12 次底层模型调用。

---

## 二、职责 1：官方分时段 `actualCostCny` 计算器与 `UsageCapturingChatModel` 闭环设计

为严格遵守阶段九主干 `ppc` 包 **“控制在 6 个核心类以内”** 的架构约束，不额外新增顶层计算器类，而是将官方分时段定价算法内聚为 `UsageCapturingChatModel` 的静态纯函数与实例计费闭环，并同步对齐 `DeepSeekCostGovernor`。

### 2.1 DeepSeek 官方 `deepseek-flash` 分时段纳元定价数学模型

- **时区常量**：`public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");`
- **时段判定函数 `isPeakBeijingTime(Instant timestamp)`**：
  令 $t = \text{timestamp}.\text{atZone}(\text{Asia/Shanghai})$，星期 $d = t.\text{getDayOfWeek}()$，本地时间 $\tau = t.\text{toLocalTime}()$：
  $$\text{isPeak}(t) = \mathbb{I}\big(d \notin \{\text{SATURDAY}, \text{SUNDAY}\}\big) \wedge \Big(\mathbb{I}\big(09:00 \le \tau < 12:00\big) \vee \mathbb{I}\big(14:00 \le \tau < 18:00\big)\Big)$$
  - 周一至周五北京时间 `[09:00, 12:00)` 与 `[14:00, 18:00)` 为 **Peak（高峰时段）**；
  - 其余时段及周六、周日全天（如 `2026-10-31` 周六全天）均为 **Off-Peak（空闲时段）**。
- **纳元（Nano-CNY, $10^{-9}$ 元）定点单价常量**：
  因为 $\frac{1 \text{ CNY}}{10^6 \text{ tokens}} = 10^{-6} \text{ CNY/token} = 1,000 \text{ Nano-CNY/token}$：
  - **高峰时段（Peak）**：
    - `PEAK_HIT_NANO_PER_TOKEN = 40L`（对应 $0.04 \text{ 元} / 10^6 \text{ tokens}$）
    - `PEAK_MISS_NANO_PER_TOKEN = 2_000L`（对应 $2.00 \text{ 元} / 10^6 \text{ tokens}$）
    - `PEAK_COMPLETION_NANO_PER_TOKEN = 8_000L`（对应 $8.00 \text{ 元} / 10^6 \text{ tokens}$）
  - **空闲时段（Off-Peak）**：
    - `OFF_PEAK_HIT_NANO_PER_TOKEN = 20L`（对应 $0.02 \text{ 元} / 10^6 \text{ tokens}$）
    - `OFF_PEAK_MISS_NANO_PER_TOKEN = 1_000L`（对应 $1.00 \text{ 元} / 10^6 \text{ tokens}$）
    - `OFF_PEAK_COMPLETION_NANO_PER_TOKEN = 4_000L`（对应 $4.00 \text{ 元} / 10^6 \text{ tokens}$）
- **单次调用精确计费公式**：
  给定单次模型响应的 `promptTokens`、`cacheHitTokens`、`completionTokens`（注：DeepSeek 官方 API 的 `completion_tokens` 已包含思维链 `reasoning_tokens`，严禁重复累加）：
  $$\begin{aligned}
  T_{\text{prompt}} &= \max(0, \text{promptTokens}) \\
  T_{\text{hit}} &= \min\big(T_{\text{prompt}}, \max(0, \text{cacheHitTokens})\big) \\
  T_{\text{miss}} &= T_{\text{prompt}} - T_{\text{hit}} \\
  T_{\text{out}} &= \max(0, \text{completionTokens}) \\
  C_{\text{nano}}(t) &= T_{\text{hit}} \cdot p_{\text{hit}}(t) + T_{\text{miss}} \cdot p_{\text{miss}}(t) + T_{\text{out}} \cdot p_{\text{out}}(t) \\
  \text{actualCostCny} &= \frac{C_{\text{nano}}(t)}{1\_000\_000\_000.0\text{D}}
  \end{aligned}$$

### 2.2 `HarnessUsageSnapshot` 与 `UsageCapturingChatModel` 改造规格

#### 1. `HarnessUsageSnapshot.java` 增强契约
```java
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
        if (meteredCallCount <= 0 || unmeteredCallCount > 0 || totalTokens <= 0L
                || !Double.isFinite(actualCostCny) || actualCostCny < 0.0D) {
            usageStatus = UsageStatus.UNKNOWN;
            actualCostCny = 0.0D;
        }
    }

    public static HarnessUsageSnapshot unknown() {
        return new HarnessUsageSnapshot(UsageStatus.UNKNOWN, 0L, 0L, 0L, 0L, 0L, 0.0D, 0, 0);
    }

    public boolean isKnown() {
        return usageStatus == UsageStatus.KNOWN
                && meteredCallCount > 0
                && unmeteredCallCount == 0
                && totalTokens > 0L;
    }

    /**
     * 严格判定真实成本是否已知：仅当 UsageStatus.KNOWN 且 actualCostCny > 0.0D 时返回 true，
     * 彻底杜绝将 UNKNOWN 状态下的 0.0D 误判为“免费零成本”。
     */
    public boolean isCostKnown() {
        return isKnown() && Double.isFinite(actualCostCny) && actualCostCny > 0.0D;
    }
}
```

#### 2. `UsageCapturingChatModel.java` 精确方法签名与闭环逻辑
- 新增可注入 `Clock` 与实时调用拦截钩子（默认使用 `Clock.system(SHANGHAI_ZONE)` 与无操作钩子，保持无参/单参构造器 100% 向后兼容）：
```java
public final class UsageCapturingChatModel implements ChatModel {

    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    public static final long NANO_PER_CNY = 1_000_000_000L;

    public static final long PEAK_HIT_NANO = 40L;
    public static final long PEAK_MISS_NANO = 2_000L;
    public static final long PEAK_COMPLETION_NANO = 8_000L;

    public static final long OFF_PEAK_HIT_NANO = 20L;
    public static final long OFF_PEAK_MISS_NANO = 1_000L;
    public static final long OFF_PEAK_COMPLETION_NANO = 4_000L;

    @FunctionalInterface
    public interface CallBudgetListener {
        void onModelCallCompleted(long callPromptTokens, long callCompletionTokens,
                                  long callTotalTokens, long callCacheHitTokens,
                                  long callReasoningTokens, double callCostCny);
    }

    public static boolean isPeakBeijingTime(Instant timestamp);
    public static long computeOfficialCostNano(long promptTokens, long cacheHitTokens,
                                               long completionTokens, Instant timestamp);
    public static double computeOfficialCostCny(long promptTokens, long cacheHitTokens,
                                                long completionTokens, Instant timestamp);
```
- 在 `record(Usage usage)` 中，按 `Instant callInstant = clock.instant()` 实时计算本次调用的 `callCostNano = computeOfficialCostNano(callPrompt, callCacheHit, callCompletion, callInstant)` 并累加至 `totals.costNanoYuan`；
- 在 `snapshot()` 中：
  - 若 `totals.meteredCallCount <= 0 || totals.unmeteredCallCount > 0 || totals.totalTokens <= 0L`，返回 `UsageStatus.UNKNOWN` 且 `actualCostCny = 0.0D`；
  - 否则返回 `UsageStatus.KNOWN` 且 `actualCostCny = (double) totals.costNanoYuan / NANO_PER_CNY`。
- 在 `HarnessEvidenceCollector.finalizeEvidence()`（行 142–145）中：
  ```java
  boolean usageKnown = usage != null && usage.isKnown() && usage.isCostKnown();
  long tokens = currentUsageSupplier == null ? estimatedTokens : usageKnown ? usage.totalTokens() : 0L;
  double cost = currentUsageSupplier == null ? estimatedCostCny : usageKnown ? usage.actualCostCny() : 0.0D;
  ```
  确保一旦 `UsageStatus == UNKNOWN`，`HarnessEvidenceRecord` 的 `usageStatus` 必为 `UsageStatus.UNKNOWN`，`estimatedTokens = 0L`，`estimatedCostCny = 0.0D`。

---

## 三、职责 2：主干 `tech.qiantong.qknow.hermes.agent.harness.ppc` 最小 6 类架构设计

在 `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/ppc/` 包下**严格只定义以下 6 个核心类**：

1. **`PpcMode.java`**（执行终态枚举：`REAL_PPC`, `EMPIRICAL_BLOCKED`, `BUDGET_EXHAUSTED`）
2. **`PpcCandidate.java`**（不可变候选契约与聚合指标 Record）
3. **`PpcProbeResult.java`**（单次 Probe 物理证据与成本凭单 Record）
4. **`PpcRun.java`**（Stage 9 实验总账与预算审计快照 Record）
5. **`PpcComposer.java`**（代数变异提议、匿名 Position-Swap 校验与可信 Pareto 选择器）
6. **`PpcOrchestrator.java`**（真实 Probe 驱动、硬预算看门狗与原子落盘主引擎）

---

## 四、职责 3：硬预算看门狗与实时拦截机制（Hard Budget Watchdog）

在 `PpcOrchestrator` 内部实现线程安全的双层原子预算看门狗（`BudgetWatchdog`）：

1. **第一层：Probe 启动前拦截（`tryAcquireProbeSlot()`）**：
   - 在每次执行 `executeSingleProbe` 发起网络调用之前，检查 `totalProbes`（默认上限 `15`，启用 Candidate C 时绝对上限 `20`）、`totalModelCalls`（上限 `60`）、`totalTokens`（上限 `200,000`）、`totalCostCny`（上限 `¥10.00`）；任一达标立即中止后续所有 Probe 并返回 `PpcMode.BUDGET_EXHAUSTED`。
2. **第二层：底层 `ChatModel.call` / `stream` 级实时拦截（`beforeCallGuard` & `afterCallListener`）**：
   - 在每次底层模型调用前检查单次 Probe 调用次数（$\le 3$）及全局预算上限，超限则在 HTTP 请求发出前物理阻断；
   - 在每次底层模型返回 `Usage` 后立即原子累加 `totalModelCalls`、`totalTokens` 与 `totalCostNano`，并校验 `callCompletionTokens <= 2000` 及全局上限，一旦突破立即置位 `budgetExhausted = true`，优雅收敛并标记 `PpcRun.mode = PpcMode.BUDGET_EXHAUSTED`。

---

## 五、职责 4：真实 `AgentOrchestrator` 驱动与单次 Probe 模型调用上界控制

1. **真实 `AgentOrchestrator` + `HarnessKernelBridge(ENFORCE)` 组装**：
   - 显式关闭 `PlanSolveConfig`（`enabled=false`、`rpReactEnabled=false`、`maxReflectionRetries=0`）以及旁路 `RetrievalEvaluator`、`AiJudgeService`、`RagasEvaluator`，消除一切旁路 LLM 调用；
   - 强制开启 `HarnessKernelBridge(..., true, KernelBridgeMode.ENFORCE)`，并通过 `ChatRequest` 真实注入 `ragContexts`、`toolMethodIds`（配对沙盒确定性 `ToolCallbackResolver`）与 `history`；
   - 将单次请求的 `max_tokens` 硬限制在 `<= 2000`，将 `maxCycleSteps` 设为 `3`（Candidate B 紧缩为 `2`），确保单次 Probe 模型调用严格控制在 **1～3 次**以内。

---

## 六、职责 5：Live 证据落盘与零污染隔离规格

1. **物理落盘目录结构**：
   所有 Stage 9 实时实验产物唯一落盘至 `experiment/outputs/harness9-live/<runId>/`（包含 `probes/<candidateId>-<caseId>.json`、`ppc_summary.json`、`selected_candidate.json`、`checksums.sha256`）。
2. **`ATOMIC_MOVE` 写入与 `checksums.sha256` 规范**：
   - 每完成一个 Probe 立即写入同目录 `.tmp` 文件并执行 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`；
   - 运行结束时按相对路径字典序计算所有 JSON 产物的 SHA-256 并原子写入 `checksums.sha256`。
3. **历史实验目录零污染保护与 `.gitignore` 核验**：
   - `PpcOrchestrator` 强制校验输出路径严禁指向 `experiment/outputs/harness4` 或 `experiment/outputs/harness5`；
   - 仓库根目录 `.gitignore` 第 113 行已包含 `experiment/outputs/*-live/`，原生覆盖 `experiment/outputs/harness9-live/`，确保工作区 `git status --short` 100% 保持 clean。
