# Hermes Harness² 阶段七冻结任务书：Stage 6 架构语义收紧与 `HarnessKernelBridge` 生产桥接 (`50-harness7-taskbook.md`)

| 元数据字段 | 锁定值 |
|---|---|
| **任务书编号** | `50-harness7-taskbook` |
| **任务书版本** | `1.0.0-FROZEN` |
| **状态** | `FROZEN`（已完成 3 独立子智能体法医审计与主 Agent 交叉辩论裁决） |
| **Stage 0 修正分支** | `feat/hermes-harness-stage6`（基于 `4411fa2f` 提交 Stage 0 架构语义修正） |
| **Stage 4 实施分支** | `feat/hermes-harness-stage7`（从完成 Stage 0 后的 `feat/hermes-harness-stage6` 切出） |
| **唯一 JDK 环境** | `/Users/achilles/.sdkman/candidates/java/21.0.5-tem`（严禁污染宿主 Java 17） |
| **唯一生成与向量基线** | DeepSeek 官方活跃模型；阿里千问 `text-embedding-v4` **1024 维**单位超球面（$\mathbb{S}^{1023} \subset \mathbb{R}^{1024},\ \|\mathbf{v}\|_2 = 1$） |
| **生产默认安全开关** | `hermes.orchestration.enabled=false`；`hermes.harness.kernel.enabled=false`；`hermes.harness.kernel.mode=OFF` |
| **正文签名 (`body_sha256`)** | `ea1513045fdac949fc1997faea0de0c70bd54fa14aafc453b02268b0749e5f3e` |
| **全文件签名 (`full_file_sha256`)** | 独立存储于伴生文件 [`50-harness7-taskbook.md.sha256`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/50-harness7-taskbook.md.sha256)（严禁混写入本文件头） |

> **物理派发 3 独立子智能体审计存证**：
> - **Agent A（架构与形式化审计员）**：[`04f92fd9-7dc1-41a9-acdd-ab18f7724bf6`](conversation://04f92fd9-7dc1-41a9-acdd-ab18f7724bf6) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/04f92fd9-7dc1-41a9-acdd-ab18f7724bf6/.system_generated/logs/transcript.jsonl) | [`49-harness7-agent-A.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/49-harness7-agent-A.md)
> - **Agent B（Java 生产桥接审计员）**：[`e1765a6e-1d3e-4dac-8a06-eeb257c055e8`](conversation://e1765a6e-1d3e-4dac-8a06-eeb257c055e8) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/e1765a6e-1d3e-4dac-8a06-eeb257c055e8/.system_generated/logs/transcript.jsonl) | [`49-harness7-agent-B.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/49-harness7-agent-B.md)
> - **Agent C（测试、安全与门禁审计员）**：[`a2493cbd-8d5c-4f32-b955-b8ce0d1f21f3`](conversation://a2493cbd-8d5c-4f32-b955-b8ce0d1f21f3) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/a2493cbd-8d5c-4f32-b955-b8ce0d1f21f3/.system_generated/logs/transcript.jsonl) | [`49-harness7-agent-C.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/49-harness7-agent-C.md)
> - **前置研究与辩论文档**：[`49-harness7-research.ledger.json`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/49-harness7-research.ledger.json) | [`49-harness7-research.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/49-harness7-research.md) | [`49-harness7-bridge-design.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/49-harness7-bridge-design.md) | [`49-harness7-debate.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/49-harness7-debate.md)

---

## 一、阶段七唯一待验证架构命题与硬性红线边界

### 1.1 唯一待验证核心架构命题

> 在不改变 [`AgentOrchestrator`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestrator/AgentOrchestrator.java) 任何既有公共方法签名、且保持生产默认开关 `hermes.harness.kernel.enabled=false`（`mode=OFF`）对现有执行路径 **100% 零影响** 的前提下：
> 1. 先在 `feat/hermes-harness-stage6` 完成 **Stage 0 架构语义收紧**（超球面外在弦质心重命名与测地线失真夹逼界、`ReversibleRingColdBuffer` 四元组容量上限与非破坏性软删除拒绝、`HoareTripleToolGuard` 接入 `GuardrailsSpecificationRecord` 四维阈值、`HermesHarnessKernel` 真实串联 8 大核心组件）；
> 2. 再切出 `feat/hermes-harness-stage7` 构建受控生产桥接器 **`HarnessKernelBridge`**（支持 `OFF / SHADOW / ENFORCE` 三态模式），以单次请求级隔离会话（`ExecutionSession`）无侵入接入 `AgentOrchestrator`，使 `SHADOW` 模式仅旁路生成可审计凭单回执（Fail-Open），`ENFORCE` 模式真实接管能力载荷门禁、工具霍尔前后置守卫与局部致密循环阻断（Fail-Close），并通过零外网确定性契约测试与全量回归门禁。

### 1.2 硬性红线边界（Iron Boundaries）

1. **分支与提交纪律**：
   - 严禁修改 `main` 分支；
   - **Stage 0** 必须先在 `feat/hermes-harness-stage6` 上独立提交 1 个 `fix(hermes)` commit；
   - **Stage 4** 必须从完成 Stage 0 后的 `feat/hermes-harness-stage6` 切出新分支 `feat/hermes-harness-stage7` 并提交实现与文档。
2. **零侵入与零新增依赖**：
   - 不修改 MCP stdio、HITL/审批、SWARM/DEBATE；
   - 不引入任何新 Maven 依赖、新模型或新向量库；
   - 不改变 `AgentOrchestrator` 的任何既有构造器或公共方法签名；
   - 默认 `mvn test` 严禁依赖外网、严禁需要真实 DeepSeek API Key、严禁改写任何已提交的 `experiment/outputs/harness*` 历史证据文件。
3. **跨请求零污染铁律**：
   - `LocallyDenseCycleAutomaton` 与 `ReversibleRingColdBuffer` 必须按**单次请求级会话（Request-Scoped `ExecutionSession`）**隔离创建，严禁跨请求共享可变状态。

---

## 二、Stage 0：Stage 6 架构语义收紧实施契约（在 `feat/hermes-harness-stage6` 独立提交）

实施工程师必须首先确认当前分支为 `feat/hermes-harness-stage6`（HEAD 为 `4411fa2f`），按以下精确规约修改 4 个核心主干类与 1 个测试类，并在 `feat/hermes-harness-stage6` 上完成独立提交。

### 2.1 修改文件 1：`HypersphericalManifoldProjector.java`

- **文件路径**：[`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/manifold/HypersphericalManifoldProjector.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/manifold/HypersphericalManifoldProjector.java)
- **精确改动要求**：
  1. **收紧类级与方法级 Javadoc 学术表述**：明确声明本类计算的是 1024 维千问单位超球面 $\mathbb{S}^{1023}$ 上的**外在弦质心（Extrinsic / Chordal Centroid）** $\boldsymbol{\mu}_{\mathrm{ext}} = \bar{\mathbf{v}} / \|\bar{\mathbf{v}}\|_2$，其精确最小化经验平方弦距风险 $\mathcal{L}_{\mathrm{chord}}(\boldsymbol{\mu}) = \frac{1}{N}\sum_{i=1}^N \|\mathbf{v}_i - \boldsymbol{\mu}\|_2^2 = 2(1 - \|\bar{\mathbf{v}}\|_2)$，并在局部凸球冠内为内蕴黎曼测地线 Fréchet 均值提供 $O(Nd)$ 闭式二阶近似。
  2. **重命名主计算方法为 `extrinsicCentroid` 并新增等价别名 `chordalCentroid`**：
     - 将原 `frechetMean(List<double[]> vectors)` 的实现体移入 `public Centroid extrinsicCentroid(List<double[]> vectors)`；
     - 新增 `public Centroid chordalCentroid(List<double[]> vectors)`，直接返回 `extrinsicCentroid(vectors)`；
     - 保留向后兼容方法：
       ```java
       /**
        * 兼容旧调用方的委托方法：返回单位超球面上的外在弦质心（Extrinsic / Chordal Centroid）。
        * 请优先使用 {@link #extrinsicCentroid(List)} 或 {@link #chordalCentroid(List)}。
        */
       @Deprecated(since = "stage7", forRemoval = false)
       public Centroid frechetMean(List<double[]> vectors) {
           return extrinsicCentroid(vectors);
       }
       ```
  3. **新增局部凸球冠测地线失真上界计算方法 `geodesicDistortionUpperBound`**：
     ```java
     /**
      * 基于 Jordan 不等式计算局部球冠（最大测地线半径 maxGeodesicRadians ∈ [0, π]）内
      * 外在平方弦距损失对应的内蕴平均平方测地线失真上界：
      * d_geo^2 <= ((theta_max / 2) / sin(theta_max / 2))^2 * d_chord^2 <= (pi^2 / 4) * d_chord^2
      */
     public double geodesicDistortionUpperBound(Centroid centroid, double maxGeodesicRadians) {
         Objects.requireNonNull(centroid, "centroid must not be null");
         if (Double.isNaN(maxGeodesicRadians) || maxGeodesicRadians < 0.0D || maxGeodesicRadians > Math.PI) {
             throw new IllegalArgumentException("maxGeodesicRadians must be in [0, pi], got: " + maxGeodesicRadians);
         }
         double halfTheta = maxGeodesicRadians * 0.5D;
         double factor;
         if (halfTheta < 1e-9D) {
             factor = 1.0D;
         } else {
             double ratio = halfTheta / Math.sin(halfTheta);
             factor = ratio * ratio;
         }
         return factor * centroid.meanSquaredChordalLoss();
     }
     ```

### 2.2 修改文件 2：`ReversibleRingColdBuffer.java`

- **文件路径**：[`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/manifold/ReversibleRingColdBuffer.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/manifold/ReversibleRingColdBuffer.java)
- **精确改动要求**：
  1. **引入冷备档案最大容量上限 `maxArchiveCapacity`**：
     - 新增常量 `public static final int DEFAULT_MAX_ARCHIVE_CAPACITY = 256;`；
     - 新增成员变量 `private final int maxArchiveCapacity;` 与 `private long rejectedArchiveCount;`；
     - 新增 5 参数主构造器：
       ```java
       public ReversibleRingColdBuffer(
               int hotCapacity,
               int warmCapacity,
               int coldCapacity,
               int maxArchiveCapacity,
               HypersphericalManifoldProjector projector)
       ```
       校验 `maxArchiveCapacity < 1` 时抛出 `IllegalArgumentException("maxArchiveCapacity must be >= 1")`；
     - 原 4 参数构造器委托调用 `this(hotCapacity, warmCapacity, coldCapacity, DEFAULT_MAX_ARCHIVE_CAPACITY, projector)`；
     - 新增访问器 `public int maxArchiveCapacity() { return maxArchiveCapacity; }` 与预检方法 `public synchronized boolean canArchive() { return archive.size() < maxArchiveCapacity; }`。
  2. **扩展 `Statistics` Record（保留旧签名兼容性的同时提供 8 字段完整视图）**：
     ```java
     public record Statistics(
             int hot,
             int warm,
             int cold,
             int archived,
             int archivedCapacity,
             long softDeleted,
             long restored,
             long rejectedArchive) {
         public Statistics(int hot, int warm, int cold, int archived, long softDeleted, long restored) {
             this(hot, warm, cold, archived, DEFAULT_MAX_ARCHIVE_CAPACITY, softDeleted, restored, 0L);
         }
     }
     ```
     在 `statistics()` 方法中返回 `new Statistics(hot.size(), warm.size(), cold.size(), archive.size(), maxArchiveCapacity, softDeletedCount, restoredCount, rejectedArchiveCount)`。
  3. **有界归档与非破坏性软删除拒绝语义（严禁静默硬删除）**：
     - 在 `add(Episode<T> episode)` 中，当 `cold.size() > coldCapacity` 弹出 `cold.pollFirst()`（记为 `evictedCold`）时：
       - 若 `archive.size() < maxArchiveCapacity`，则 `archive.put(evictedCold.episode().episodeId(), evictedCold); softDeletedCount++;`；
       - 若 `archive.size() >= maxArchiveCapacity`，则**拒绝写入 `archive`**，递增 `rejectedArchiveCount++`（确保 `archive.size() <= maxArchiveCapacity` 恒成立）；
     - 在 `softDelete(RestorationToken token)` 中：
       - 先在 `cold` 中查找目标条目并完成常量时间 `MessageDigest.isEqual` 验签；
       - 若验签通过但 `archive.size() >= maxArchiveCapacity`，**严禁从 `cold` 中移除该条目**（保留原条目在 `cold` 中不丢失），递增 `rejectedArchiveCount++` 并返回 `false`；
       - 仅当 `archive.size() < maxArchiveCapacity` 时，才从 `cold` 移除并放入 `archive`，递增 `softDeletedCount++` 并返回 `true`。
  4. **迁移质心调用**：将 `warmCentroid()` 中的 `projector.frechetMean(vectors)` 改为 `projector.extrinsicCentroid(vectors)`。

### 2.3 修改文件 3：`HoareTripleToolGuard.java`

- **文件路径**：[`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/neurosymbolic/HoareTripleToolGuard.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/neurosymbolic/HoareTripleToolGuard.java)
- **精确改动要求**：
  1. **扩展 `Violation` 枚举**：
     ```java
     public enum Violation {
         MISSING_CONTRACT,
         PERMISSION,
         SCHEMA,
         BOUNDARY,
         GUARDRAIL_STEP_LIMIT,
         GUARDRAIL_LOOP_THRESHOLD,
         GUARDRAIL_TOKEN_BUDGET,
         GUARDRAIL_COST_BUDGET,
         RESULT_INTEGRITY,
         SENSITIVE_OUTPUT
     }
     ```
  2. **新增运行期护栏状态 `ExecutionState` Record**：
     ```java
     public record ExecutionState(
             int currentStep,
             int loopFrequency,
             long estimatedTokens,
             double estimatedCostCny) {
         public ExecutionState {
             if (currentStep < 0 || loopFrequency < 0 || estimatedTokens < 0L || estimatedCostCny < 0.0D || Double.isNaN(estimatedCostCny)) {
                 throw new IllegalArgumentException("ExecutionState metrics must be non-negative and finite");
             }
         }

         public static ExecutionState initial() {
             return new ExecutionState(1, 1, 0L, 0.0D);
         }

         public static ExecutionState fromArguments(Map<String, Object> arguments) {
             if (arguments == null || arguments.isEmpty()) {
                 return initial();
             }
             int step = toInt(arguments.get("_step"), 1);
             int freq = toInt(arguments.get("_loopFrequency"), 1);
             long tokens = toLong(arguments.get("_estimatedTokens"), 0L);
             double cost = toDouble(arguments.get("_estimatedCostCny"), 0.0D);
             return new ExecutionState(Math.max(0, step), Math.max(0, freq), Math.max(0L, tokens), Math.max(0.0D, cost));
         }
     }
     ```
  3. **实现 5 参数 `verifyBefore` 并由 4 参数重载委托调用**：
     - 4 参数 `verifyBefore(String toolName, Map<String, Object> arguments, GuardrailsSpecificationRecord guardrails, ToolContract contract)` 调用 `verifyBefore(toolName, arguments, guardrails, contract, ExecutionState.fromArguments(arguments))`；
     - 5 参数 `verifyBefore(String toolName, Map<String, Object> arguments, GuardrailsSpecificationRecord guardrails, ToolContract contract, ExecutionState state)` 严格按以下 9 步顺序执行短路校验：
       1. `guardrails` 与 `state` 非空校验（`Objects.requireNonNull`）；
       2. `contract == null || toolName == null || toolName.isBlank()` $\to$ `Decision.reject(Violation.MISSING_CONTRACT, ...)`；
       3. `state.currentStep() > guardrails.maxCycleSteps()` $\to$ `Decision.reject(Violation.GUARDRAIL_STEP_LIMIT, ...)`；
       4. `state.loopFrequency() >= guardrails.loopThreshold()` $\to$ `Decision.reject(Violation.GUARDRAIL_LOOP_THRESHOLD, ...)`；
       5. `state.estimatedTokens() > guardrails.maxTokenBudget()` $\to$ `Decision.reject(Violation.GUARDRAIL_TOKEN_BUDGET, ...)`；
       6. `state.estimatedCostCny() > guardrails.maxCostCny()` $\to$ `Decision.reject(Violation.GUARDRAIL_COST_BUDGET, ...)`；
       7. `!contract.permittedTools().contains(normalizedTool)` $\to$ `Decision.reject(Violation.PERMISSION, ...)`；
       8. 遍历 `contract.requiredArguments()`，缺失或空白 $\to$ `Decision.reject(Violation.SCHEMA, ...)`；
       9. `!contract.boundary().test(safeArgs)` $\to$ `Decision.reject(Violation.BOUNDARY, ...)`。

### 2.4 修改文件 4：`HermesHarnessKernel.java`

- **文件路径**：[`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HermesHarnessKernel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HermesHarnessKernel.java)
- **精确改动要求**：
  1. **扩展 `PreparedExecution` Record**：
     ```java
     public record PreparedExecution(
             HermesHarnessVoucher voucher,
             CapabilityContext context,
             String systemPrompt,
             String voucherFingerprint,
             ReversibleRingColdBuffer.Statistics coldBufferSnapshot) {
         public PreparedExecution(
                 HermesHarnessVoucher voucher,
                 CapabilityContext context,
                 String systemPrompt,
                 String voucherFingerprint) {
             this(voucher, context, systemPrompt, voucherFingerprint,
                     new ReversibleRingColdBuffer.Statistics(0, 0, 0, 0,
                             ReversibleRingColdBuffer.DEFAULT_MAX_ARCHIVE_CAPACITY, 0L, 0L, 0L));
         }
     }
     ```
  2. **新增工具调用结果 Record `GuardedToolOutcome` 与轨迹评估结果 Record `TrajectoryEvaluationResult`**：
     - `GuardedToolOutcome(String toolName, boolean executed, String output, HoareTripleToolGuard.Decision preDecision, HoareTripleToolGuard.Decision postDecision, LocallyDenseCycleAutomaton.CycleInspection cycleInspection, int stepIndex)`；
     - `TrajectoryEvaluationResult(TrajectoryAttributionAnalyzer.AttributionResult attribution, Optional<HarnessCandidateProposal> mutationProposal, List<ParetoHarnessComposer.EvaluatedCandidate> paretoFrontier)`。
  3. **新增请求级隔离会话 `ExecutionSession`（通过 `openSession(String requestId, HermesHarnessVoucher voucher)` 创建）**：
     - 每个 `ExecutionSession` 内部根据 `voucher` 独立创建专属的 `LocallyDenseCycleAutomaton(voucher.guardrails().maxCycleSteps(), voucher.guardrails().loopThreshold())` 与 `ReversibleRingColdBuffer<String>(Math.max(1, voucher.memoryContext().shortTermWindowSize()), 4, 8, ReversibleRingColdBuffer.DEFAULT_MAX_ARCHIVE_CAPACITY, manifoldProjector)`；
     - `prepare(...)` 方法真实调用：
       1. `capabilityAssembler.assemble(tag, ragContexts, tools, history)`；
       2. 将 `history` 消息文本确定性映射为 1024 维单位向量（通过 SHA-256 派生确定性正交基投影并归一化到 $\mathbb{S}^{1023}$）写入会话专属 `coldBuffer`（或 kernel `coldBuffer`），采集 `coldBuffer.statistics()`；
       3. `harnessAssembler.assembleSystemPrompt(basePrompt, voucher)`；
     - `invokeTool(String toolName, Map<String, Object> arguments, String canonicalArgs, HoareTripleToolGuard.ToolContract contract, long estimatedTokens, double estimatedCostCny, Supplier<String> rawToolInvoker)` 方法真实按序调用：
       1. `cycleAutomaton.inspect(toolName, canonicalArgs)` 获取 `CycleInspection`；
       2. 构造 `HoareTripleToolGuard.ExecutionState(stepIndex, inspection.frequency(), estimatedTokens, estimatedCostCny)`；
       3. 调用 `toolGuard.verifyBefore(toolName, arguments, voucher.guardrails(), contract, state)`；
       4. 若 `inspection.rejected()` 或 `!preDecision.allowed()`，**不执行 `rawToolInvoker.get()`**，向会话轨迹列表追加失败 `TrajectoryStep` 并返回 `GuardedToolOutcome`（`executed = false`）；
       5. 若前置通过，执行 `String result = rawToolInvoker.get()`，再调用 `toolGuard.verifyAfter(result, contract)`，记录 `TrajectoryStep` 并返回 `GuardedToolOutcome`（`executed = true`）；
     - `evaluateTrajectory(...)` 方法真实按序调用：
       1. `attributionAnalyzer.analyze(steps, ragGroundingScore)`；
       2. 若存在归因失败步或护栏违约，调用 `mutator.mutate(...)` 生成 `Optional<HarnessCandidateProposal>`；
       3. 若存在候选分数列表，调用 `paretoComposer.frontier(candidates)` 计算非支配 Pareto 前沿；
     - 在 `HermesHarnessKernel.execute(PreparedExecution, Function<PreparedExecution, T>)` 中，同步更新执行计数并确保会话与内核组件的真实调用链完整闭环。

### 2.5 修改文件 5：`HermesHarnessKernelArchitectureTest.java`

- **文件路径**：[`backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/HermesHarnessKernelArchitectureTest.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/HermesHarnessKernelArchitectureTest.java)
- **精确改动要求**：
  - 更新测试以覆盖：
    1. `extrinsicCentroid` / `chordalCentroid` 与 `geodesicDistortionUpperBound` 的夹逼不等式验证；
    2. `ReversibleRingColdBuffer` 的 `maxArchiveCapacity` 上限强制截断与 `softDelete` 满额时保留在 `cold` 中并返回 `false` 的非破坏性拒绝验证；
    3. `HoareTripleToolGuard` 对 `GuardrailsSpecificationRecord` 四维阈值（`maxCycleSteps`, `loopThreshold`, `maxTokenBudget`, `maxCostCny`）的真实阻断验证；
    4. `HermesHarnessKernel` 的 `openSession`、`prepare`、`invokeTool`、`evaluateTrajectory` 对 8 大组件的真实串联验证。

### 2.6 Stage 0 提交规范（在 `feat/hermes-harness-stage6` 上提交）

运行 `HermesHarnessKernelArchitectureTest` 全绿后，在 `feat/hermes-harness-stage6` 提交：

```text
fix(hermes): 收紧Stage6流形质心命名与冷备容量上限并补齐Kernel真实组件编排

- 将HypersphericalManifoldProjector主方法定名为extrinsicCentroid并补充局部球冠测地线失真上界
- 为ReversibleRingColdBuffer引入maxArchiveCapacity容量上限与非破坏性软删除拒绝机制
- 在HoareTripleToolGuard中接入GuardrailsSpecificationRecord四维阈值并在HermesHarnessKernel中实现真实组件编排
```

---

## 三、Stage 4：`HarnessKernelBridge` 生产桥接与 `AgentOrchestrator` 接入契约（切出 `feat/hermes-harness-stage7`）

在 `feat/hermes-harness-stage6` 完成 Stage 0 提交后，立即切出新分支：
```bash
git checkout -b feat/hermes-harness-stage7
```

### 3.1 新增文件 1：`KernelBridgeMode.java`

- **文件路径**：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/KernelBridgeMode.java`
- **完整规约**：
  ```java
  package tech.qiantong.qknow.hermes.agent.harness.kernel;

  import java.util.Locale;

  /**
   * Hermes Harness Kernel 生产桥接三态运行模式。
   */
  public enum KernelBridgeMode {
      /**
       * 完全关闭（默认）：不创建会话、不改写提示词、不包装工具，对现有路径 100% 零影响。
       */
      OFF,

      /**
       * 影子观测模式：旁路执行 Capability 预检与凭单指纹计算并记录回执，Fail-Open，不改变实际提示词与工具调用。
       */
      SHADOW,

      /**
       * 强制门禁模式：真实执行 Capability 门禁（缺失必需载荷立即 Fail-Close 阻断）、受控提示词装配与工具级霍尔/循环守卫包装。
       */
      ENFORCE;

      public static KernelBridgeMode resolveEffective(boolean enabled, String configuredMode) {
          if (!enabled || configuredMode == null || configuredMode.isBlank()) {
              return OFF;
          }
          return switch (configuredMode.trim().toUpperCase(Locale.ROOT)) {
              case "SHADOW" -> SHADOW;
              case "ENFORCE" -> ENFORCE;
              default -> OFF;
          };
      }
  }
  ```

### 3.2 新增文件 2：`HarnessKernelBridge.java`

- **文件路径**：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HarnessKernelBridge.java`
- **核心职责与规约**：
  1. **Spring Bean 与配置绑定**：
     - `@Component`；
     - `@Value("${hermes.harness.kernel.enabled:false}") private boolean enabled;`
     - `@Value("${hermes.harness.kernel.mode:OFF}") private String configuredMode;`
     - 提供静态工厂 `public static HarnessKernelBridge disabledInstance()`（`enabled = false, mode = OFF`）以及编程式构造器 `public HarnessKernelBridge(HermesHarnessKernel kernel, HermesHarnessAssembler assembler, boolean enabled, KernelBridgeMode mode)` 供确定性单测直接构造。
  2. **不可变桥接回执 `KernelBridgeReceipt` Record**：
     ```java
     public record KernelBridgeReceipt(
             String requestId,
             KernelBridgeMode effectiveMode,
             CapabilityTag capabilityTag,
             boolean preparedSuccess,
             boolean failClosed,
             String errorReason,
             String voucherFingerprint,
             int guardedToolInvocationCount,
             int blockedToolInvocationCount,
             List<String> guardViolations,
             ReversibleRingColdBuffer.Statistics coldBufferSnapshot,
             Optional<HermesHarnessKernel.TrajectoryEvaluationResult> trajectoryEvaluation) {}
     ```
  3. **不可变桥接执行句柄 `BridgeExecutionHandle`**：
     - 包含：`KernelBridgeMode effectiveMode`、`boolean failClosed`、`String failCloseReason`、`String effectiveSystemPrompt`、`List<ToolCallback> effectiveTools`、`ToolCallbackResolver effectiveResolver`、`HermesHarnessKernel.ExecutionSession session`；
     - 当 `effectiveMode == OFF` 时，直接返回常量单例 `BridgeExecutionHandle.off(systemPrompt, tools, resolver)`（零额外分配）。
  4. **JSON 字符串解析 `CapabilityTag`（修复 `ChatRequest.getInputParams()` 类型问题）**：
     - `ChatRequest.getInputParams()` 与 `getParameters()` 均为 JSON `String`；
     - `resolveCapabilityTag(ChatRequest request, List<String> ragContexts, List<ToolCallback> tools, ToolCallbackResolver resolver, List<ChatMessage> history)`：
       - 先尝试使用 `com.alibaba.fastjson2.JSONObject.parseObject` 解析 `request.getInputParams()` 与 `request.getParameters()` 中的 `"harness.capabilityTag"` 或 `"capabilityTag"`（如 `"RAG"`, `"TOOL"`, `"HISTORY"`, `"COMPOSITE"`, `"PROMPT_ONLY"`）；
       - 若未显式指定，则按实际非空载荷自动推断：
         - `hasRag && (hasTools || hasHistory)` $\to$ `COMPOSITE`；
         - `hasRag` $\to$ `RAG`；
         - `hasTools` $\to$ `TOOL`；
         - `hasHistory` $\to$ `HISTORY`；
         - 其余 $\to$ `PROMPT_ONLY`。
  5. **双源历史消息对齐辅助方法 `resolveEffectiveHistory(ChatRequest request, List<Message> memoryMessages)`**：
     - 若 `request.getHistoryList()` 非空，直接使用 `request.getHistoryList()`；
     - 若 `request.getHistoryList()` 为空且 `memoryMessages` 包含除当前轮提问之外的历史消息，将其映射为 `List<ChatMessage>`，避免短期记忆多轮对话在 `HISTORY` 标签下被误判为空。
  6. **三态模式核心编排逻辑 `bridgeExecution(...)` 与 `completeExecution(...)`**：
     - **`OFF` 模式**：立即原样返回传入的 `systemPrompt`、`tools`、`resolver`；
     - **`SHADOW` 模式**：
       - 创建请求级隔离 `ExecutionSession`；
       - 尝试执行 `session.prepare(tag, baseSystemPrompt, ragContexts, toolNames, effectiveHistory)`；
       - 无论成功或抛异常，捕获并记录到 `KernelBridgeReceipt`（`failClosed = false`），**原样返回未修改的 `systemPrompt`、`tools`、`resolver`**；
     - **`ENFORCE` 模式**：
       - 创建请求级隔离 `ExecutionSession`；
       - 执行 `session.prepare(tag, baseSystemPrompt, ragContexts, toolNames, effectiveHistory)`：
         - 若抛出 `IllegalStateException` 或 `IllegalArgumentException`（如显式声明了 `"capabilityTag": "RAG"` 但 `ragContexts` 为空），捕获异常并生成 `BridgeExecutionHandle.failClosed(...)`（`failClosed = true`），同時写入 `KernelBridgeReceipt`；
         - 若 `prepare` 成功，采用 `prepared.systemPrompt()`，并将 `tools` 与 `resolver` 包装为 `GuardedToolCallback`：每次工具调用前自动经过会话专属 `LocallyDenseCycleAutomaton.inspect()` + `HoareTripleToolGuard.verifyBefore()`（含 `GuardrailsSpecificationRecord` 四维预算校验），调用后经过 `HoareTripleToolGuard.verifyAfter()`；若被守卫拒绝，**不调用底层工具**，递增 `blockedToolInvocationCount` 并返回结构化守卫拦截消息 `"[HARNESS_GUARD_REJECTED:" + violation + "] " + counterexampleHint`；
     - **`completeExecution(String requestId, BridgeExecutionHandle handle, double ragScore)`**：
       - 在 `finally` 块中调用：当处于 `SHADOW` 或 `ENFORCE` 时，汇总会话轨迹并调用 `session.evaluateTrajectory(ragScore)`，将最终 `KernelBridgeReceipt` 存入容量有界（默认 `256`）的线程安全 LRU 映射 `receiptsByRequestId`，可通过 `getLatestReceipt(String requestId)` 查询。

### 3.3 修改文件 3：`HarnessEvidenceCollector.java`

- **文件路径**：[`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/evidence/HarnessEvidenceCollector.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/evidence/HarnessEvidenceCollector.java)
- **精确改动要求**：
  - 保留现有 11 参数 `collect(...)` 方法签名不变（内部委托给 12 参数重载并默认传入 `HarnessEvidenceRecord.ExecutionMode.REAL_PRODUCTION`）；
  - 新增 12 参数重载（显式接收 `HarnessEvidenceRecord.ExecutionMode executionMode`），以便当 `AgentOrchestrator` 在 `ENFORCE` 模式触发 Fail-Close 阻断时，能够记录 `ExecutionMode.FAIL_SAFE` 证据。

### 3.4 修改文件 4：`AgentOrchestrator.java`

- **文件路径**：[`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestrator/AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestrator/AgentOrchestrator.java)
- **精确改动要求**：
  1. **所有现有构造器与公共方法签名 100% 不变**；
  2. 新增字段与可选 Setter/Getter（与 `setHarnessAssembler` 同构）：
     ```java
     private HarnessKernelBridge harnessKernelBridge = HarnessKernelBridge.disabledInstance();

     @org.springframework.beans.factory.annotation.Autowired(required = false)
     public void setHarnessKernelBridge(HarnessKernelBridge harnessKernelBridge) {
         if (harnessKernelBridge != null) {
             this.harnessKernelBridge = harnessKernelBridge;
         }
     }

     public HarnessKernelBridge getHarnessKernelBridge() {
         return this.harnessKernelBridge;
     }
     ```
  3. **在 `if (planAndSolve)`（约行 571）之前切入 `ReActCycleGuard` 与 `HarnessKernelBridge`**：
     - 将原行 596–605 的 `ReActCycleGuard` 包装提前至 `if (planAndSolve)` 之前；
     - 紧接着调用 `harnessKernelBridge.bridgeExecution(...)`：
       - 当 `harnessKernelBridge.isOff()`（默认 `true`）时，零开销跳过；
       - 当 `bridgeHandle.failClosed()` 为 `true`（仅在 `ENFORCE` 模式且能力门禁校验失败时触发）：
         1. 调用 `harnessEvidenceCollector.collect(..., HarnessEvidenceRecord.ExecutionMode.FAIL_SAFE)` 记录 `FAIL_SAFE` 证据并存入 `latestEvidenceByRequest`；
         2. 调用 `harnessKernelBridge.completeExecution(effectiveEvidenceRequestId, bridgeHandle, 0.0D)`；
         3. 调用 `emitter.next(new ChatEvent.ErrorEvent(bridgeHandle.failCloseReason()))` 与 `emitter.complete()`，然后**立即 `return`**（确保 `planAndSolve` 与主循环中的大模型 `chatClient` 调用次数严格为 `0`！）；
       - 当未触发 `failClosed` 时，使用 `bridgeHandle.effectiveSystemPrompt()`、`bridgeHandle.effectiveTools()`、`bridgeHandle.effectiveResolver()` 继续后续流程，并在 `finally` 块中调用 `harnessKernelBridge.completeExecution(...)`。

### 3.5 配置文件默认属性（可选显式声明，保持默认关闭）

- 在 [`backend/qknow-server/src/main/resources/application.yml`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-server/src/main/resources/application.yml) 的 `hermes:` 节点下可补充显式默认注释或配置：
  ```yaml
  hermes:
    orchestration:
      enabled: false
    harness:
      kernel:
        enabled: false
        mode: OFF
  ```

---

## 四、阶段七确定性契约测试矩阵（`HermesHarnessStage7BridgeTest.java`，零外网调用）

- **新增测试文件**：`backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/HermesHarnessStage7BridgeTest.java`
- **硬性约束**：不得带有 `@Tag("live")`，不得发起任何真实网络请求，不得依赖 `DEEPSEEK_API_KEY`，必须作为默认 `mvn test` 的一部分在毫秒级内确定性通过。

### 4.1 六大核心端到端桥接测试场景

| 测试方法名 | 模式 | 核心验证断言 |
|---|---|---|
| `shouldBeCompletelyNoOpWhenBridgeModeIsOff` | `OFF` (默认) | 验证默认 `AgentOrchestrator` 的 `harnessKernelBridge.isOff() == true`；执行 `chat()` 时不产生 `KernelBridgeReceipt`，不改变 `systemPrompt` 与工具回调，大模型替身正常调用 1 次 |
| `shouldRecordShadowReceiptAndFailOpenWhenCapabilityMissingInShadowMode` | `SHADOW` | 显式在 `ChatRequest.inputParams` 传入 `{"capabilityTag":"RAG"}` 但不提供 `ragContexts`；断言 `SHADOW` 模式捕获能力缺失并记录 `KernelBridgeReceipt(preparedSuccess=false, failClosed=false)`，但请求**不中断（Fail-Open）**，大模型替身正常返回回答 |
| `shouldFailCloseBeforeLlmInvocationWhenCapabilityMissingInEnforceMode` | `ENFORCE` | 显式传入 `{"capabilityTag":"RAG"}` 但 `ragContexts` 为空；断言 `ENFORCE` 模式在调用 `chatClient` 之前**立即 Fail-Close**，`ErrorEvent` 被发射，`chatClient` 调用次数严格为 `0`，且 `getLatestEvidence(requestId)` 的 `executionMode == FAIL_SAFE` |
| `shouldEnforceHoareToolGuardAndLocallyDenseCycleAutomatonInEnforceMode` | `ENFORCE` | 在 `ENFORCE` 模式下注入真实 `ToolCallback`：第 1 次合法调用通过 `verifyBefore` 与 `verifyAfter`；当连续重复相同调用触发 `loopThreshold` 或违反参数边界时，底层 `ToolCallback` **不被执行**，返回 `[HARNESS_GUARD_REJECTED:...]`，且 `KernelBridgeReceipt.blockedToolInvocationCount() >= 1`、`trajectoryEvaluation` 非空 |
| `shouldIsolateCycleAutomatonAndColdBufferAcrossRequestsInEnforceMode` | `ENFORCE` | 连续发起 Request A 与 Request B：Request A 将某工具调用推至循环阈值边缘或触发拦截；断言 Request B 调用同一工具时其专属 `ExecutionSession` 的 `loopFrequency == 1` 且正常放行，证明**跨请求零状态污染** |
| `shouldResolveHistoryFromMemoryManagerWhenRequestHistoryListIsEmpty` | `ENFORCE` | 显式传入 `{"capabilityTag":"HISTORY"}`，`request.getHistoryList()` 为空但 `MemoryManager` 短期记忆存有历史对话；断言双源历史对齐生效，`ENFORCE` 模式校验通过并正常完成回答 |

### 4.2 18 项 Fail-Close 负面与边界确定性测试覆盖表

在 `HermesHarnessKernelArchitectureTest` 与 `HermesHarnessStage7BridgeTest` 中必须完整覆盖以下 18 项边界：
1. `CapabilityTag.RAG` 缺失 `ragContexts` 抛异常 / Fail-Close；
2. `CapabilityTag.TOOL` 缺失 `tools` 抛异常 / Fail-Close；
3. `CapabilityTag.HISTORY` 缺失 `history` 抛异常 / Fail-Close；
4. `CapabilityTag.COMPOSITE` 任一子能力缺失抛异常 / Fail-Close；
5. `CapabilityContext` 构造器传入未对齐载荷直接抛异常；
6. `HoareTripleToolGuard` 缺失契约或空白工具名返回 `MISSING_CONTRACT`；
7. `HoareTripleToolGuard` `currentStep > maxCycleSteps` 返回 `GUARDRAIL_STEP_LIMIT`；
8. `HoareTripleToolGuard` `loopFrequency >= loopThreshold` 返回 `GUARDRAIL_LOOP_THRESHOLD`；
9. `HoareTripleToolGuard` `estimatedTokens > maxTokenBudget` 返回 `GUARDRAIL_TOKEN_BUDGET`；
10. `HoareTripleToolGuard` `estimatedCostCny > maxCostCny` 返回 `GUARDRAIL_COST_BUDGET`；
11. `HoareTripleToolGuard` 工具不在白名单返回 `PERMISSION`；
12. `HoareTripleToolGuard` 缺失必填参数返回 `SCHEMA`；
13. `HoareTripleToolGuard` 违反数值/路径边界谓词返回 `BOUNDARY`；
14. `HoareTripleToolGuard.verifyAfter` 输出为空或超过 `maxOutputLength` 返回 `RESULT_INTEGRITY`；
15. `HoareTripleToolGuard.verifyAfter` 输出包含敏感凭证片段返回 `SENSITIVE_OUTPUT`；
16. `LocallyDenseCycleAutomaton` 滑窗内同签名调用频次 $\ge \tau_{\mathrm{loop}}$ 触发 `rejected=true`；
17. `LocallyDenseCycleAutomaton` 总步数 $> K_{\max}$ 触发 `rejected=true`；
18. `ReversibleRingColdBuffer` 当 `archive.size() >= maxArchiveCapacity` 时，`softDelete` 拒绝归档、保留条目在 `cold` 中并返回 `false`（`rejectedArchive++`）。

---

## 五、验证命令、全量测试三轨计数口径与阶段七交付报告规范

### 5.1 强制验证命令清单

1. **清理历史残留 Surefire 报告（消除跨模块汇总计数偏差）**：
   ```bash
   rm -rf backend/qknow-server/target/surefire-reports
   ```
2. **Hermes Core 定向测试**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
   mvn -B -ntp -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core test
   ```
3. **后端全量 Reactor 测试（`-pl tests -am clean test`）**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
   mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
   ```
4. **阶段四/五/六历史实验产物零污染与工作区洁净检查**：
   ```bash
   git status --short
   shasum -a 256 -c experiment/outputs/harness4/run-20261030-stage4/checksums.sha256
   shasum -a 256 -c experiment/outputs/harness5/run-20261030-stage5/checksums.sha256
   ```

### 5.2 全量测试三轨计数统一报告口径

在阶段七实施与验收报告（`docs/architecture-review-20261019/acceptance/51-harness7-implementation.md`）中，**严禁只写一个笼统总数**，必须使用以下三轨拆解明细表：

| 统计轨道 | 模块范围 | 计数提取命令 | 说明 |
|---|---|---|---|
| **轨道 1：`qknow-hermes-core`** | `backend/qknow-hermes/qknow-hermes-core/target/surefire-reports` | 解析该目录下 `TEST-*.xml` 的 `tests / failures / errors / skipped` | 包含 Stage 1–7 全部 Harness 契约与桥接单测 |
| **轨道 2：`qknow-tests`** | `backend/tests/target/surefire-reports` | 解析该目录下 `TEST-*.xml` 的 `tests / failures / errors / skipped` | 平台主集成与回归测试套件（基线 `1942` 个，`8` skipped） |
| **轨道 3：Reactor 其余依赖模块** | `-pl tests -am` 中除上述两模块外的上游模块（如 `qknow-ai` 等） | 解析其余参与本次构建模块的 `TEST-*.xml`（基线 `41` 个） | 三轨之和精确等于 `-pl tests -am clean test` 总运行数 |

---

## 六、任务书双 Hash 校验与防篡改复核命令

本任务书采用双 Hash 防篡改机制（彻底杜绝自引用死循环）：
1. **正文签名 (`body_sha256`)**：对本文件从 `## 一、` 起始行至文件末尾的全部字节计算 SHA-256，并写入本文件顶部元数据表；
2. **全文件签名 (`full_file_sha256`)**：对写入 `body_sha256` 后的完整 `50-harness7-taskbook.md` 计算 SHA-256，写入伴生文件 `50-harness7-taskbook.md.sha256`。

**自动化复核命令**：
```bash
# 1. 复核正文签名 body_sha256
sed -n '/^## 一、/,$p' docs/architecture-review-20261019/acceptance/50-harness7-taskbook.md | shasum -a 256

# 2. 复核全文件签名 full_file_sha256
shasum -a 256 -c docs/architecture-review-20261019/acceptance/50-harness7-taskbook.md.sha256
```
