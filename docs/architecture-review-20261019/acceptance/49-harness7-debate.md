# Hermes Harness² 阶段七：三独立智能体交叉辩论与主 Agent 终局裁决书 (`49-harness7-debate.md`)

> **文档状态**：`FINAL_ADJUDICATED`  
> **辩论日期**：`2026-10-30`  
> **基准分支与提交**：`feat/hermes-harness-stage6` (`4411fa2f`) $\to$ `feat/hermes-harness-stage7`  
> **遵守法典**：[`AGENTS.md`](file:///Users/achilles/Documents/许子祺/Agent/AGENTS.md) 第 2.7 节多智能体物理派发与主 Agent 对齐裁决协议

---

## 零、物理派发子智能体存证清单（Physical Subagent Evidence Registry）

本阶段严格遵循《AGENTS.md》第 2.7.1 节“真实物理派发”与“存证留痕要求”，通过系统原生 `invoke_subagent` 工具物理派发了 3 个独立隔离沙盒智能体，严禁单进程角色扮演：

| 智能体代号 | 角色定位 (`Role`) | 物理 `conversationId` | 独立运行 Transcript 物理存证链接 | 独立审计报告文件 |
|---|---|---|---|---|
| **Agent A** | 架构与形式化审计员 (`Architecture & Formal Verification Auditor`) | [`04f92fd9-7dc1-41a9-acdd-ab18f7724bf6`](conversation://04f92fd9-7dc1-41a9-acdd-ab18f7724bf6) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/04f92fd9-7dc1-41a9-acdd-ab18f7724bf6/.system_generated/logs/transcript.jsonl) | [`49-harness7-agent-A.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/49-harness7-agent-A.md) |
| **Agent B** | Java 生产桥接审计员 (`Java Production Bridge Auditor`) | [`e1765a6e-1d3e-4dac-8a06-eeb257c055e8`](conversation://e1765a6e-1d3e-4dac-8a06-eeb257c055e8) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/e1765a6e-1d3e-4dac-8a06-eeb257c055e8/.system_generated/logs/transcript.jsonl) | [`49-harness7-agent-B.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/49-harness7-agent-B.md) |
| **Agent C** | 测试、安全与门禁审计员 (`Test, Security & Gate Auditor`) | [`a2493cbd-8d5c-4f32-b955-b8ce0d1f21f3`](conversation://a2493cbd-8d5c-4f32-b955-b8ce0d1f21f3) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/a2493cbd-8d5c-4f32-b955-b8ce0d1f21f3/.system_generated/logs/transcript.jsonl) | [`49-harness7-agent-C.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/49-harness7-agent-C.md) |

---

## 一、五大核心议题三方交叉辩论与终局裁决

### 议题一：1024 维单位超球面质心命名收窄（`extrinsicCentroid` vs `frechetMean`）与测地线失真夹逼定理

- **Agent A（架构与形式化审计员）立场**：
  - 在 1024 维千问 `text-embedding-v4` 单位超球面 $\mathbb{S}^{1023}$ 上，当前 [`HypersphericalManifoldProjector.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/manifold/HypersphericalManifoldProjector.java) 行 45–63 计算的是算术均值归一化投影 $\boldsymbol{\mu}_{\mathrm{ext}} = \bar{\mathbf{v}}/\|\bar{\mathbf{v}}\|_2$ 与平均平方弦距 $2(1 - \|\bar{\mathbf{v}}\|_2)$。根据 Bhattacharya & Patrangenaru (*Annals of Statistics*, 2005) 与 Ferguson et al. (`arXiv:2511.04301`)，这在微分几何上严格属于**外在均值 / 外在弦质心（Extrinsic / Chordal Centroid）**，仅当样本服从各向同性 von Mises-Fisher 分布或大圆对称分布时才与内蕴黎曼测地线 Fréchet 均值重合。
  - 主张：必须将主方法重命名为 `extrinsicCentroid(List<double[]> vectors)` 并提供等价别名 `chordalCentroid(List<double[]> vectors)`，同时利用 Jordan 不等式 $\frac{2}{\pi}x \le \sin x \le x$ 补充局部凸球冠测地线失真上界方法 `geodesicDistortionUpperBound(Centroid centroid, double maxGeodesicRadians)`：
    $$d_{\mathrm{chord}}^2(\mathbf{u},\mathbf{v}) \le d_{\mathrm{geo}}^2(\mathbf{u},\mathbf{v}) \le \left(\frac{\theta_{\max}/2}{\sin(\theta_{\max}/2)}\right)^2 d_{\mathrm{chord}}^2(\mathbf{u},\mathbf{v}) \le \frac{\pi^2}{4} d_{\mathrm{chord}}^2(\mathbf{u},\mathbf{v})$$
- **Agent B（Java 生产桥接审计员）质询与补充**：
  - 完全赞同重命名，但指出 [`ReversibleRingColdBuffer.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/manifold/ReversibleRingColdBuffer.java) 行 114 的 `warmCentroid()` 以及 [`HermesHarnessKernelArchitectureTest.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/HermesHarnessKernelArchitectureTest.java) 正在调用 `frechetMean`。若直接删除 `frechetMean` 会破坏向后兼容。
  - 主张：保留 `@Deprecated(since = "stage7", forRemoval = false) public Centroid frechetMean(List<double[]> vectors)` 委托给 `extrinsicCentroid(vectors)`，同时把 `ReversibleRingColdBuffer.warmCentroid()` 内部调用迁移至 `projector.extrinsicCentroid(vectors)`。
- **Agent C（测试、安全与门禁审计员）质询与补充**：
  - 指出 `geodesicDistortionUpperBound(Centroid centroid, double maxGeodesicRadians)` 必须对输入边界做严格防御：若 `centroid == null` 抛出 `NullPointerException`；若 `maxGeodesicRadians < 0.0 || maxGeodesicRadians > Math.PI || Double.isNaN(maxGeodesicRadians)` 抛出 `IllegalArgumentException`；当 `maxGeodesicRadians == 0.0` 时比值 $\lim_{x \to 0} (x / \sin x)^2 = 1.0$，必须规避 `0.0 / 0.0` 产生 `NaN`。
- **主 Agent 终局裁决（Binding Adjudication 1）**：
  - **采纳三方合议**：
    1. 在 Stage 0（`feat/hermes-harness-stage6` 独立修正提交）中，将 `HypersphericalManifoldProjector` 主计算方法定名为 `extrinsicCentroid(List<double[]> vectors)`，新增等价方法 `chordalCentroid(List<double[]> vectors)`；
    2. 保留 `@Deprecated(since = "stage7", forRemoval = false) frechetMean(List<double[]> vectors)` 委托调用，并在 Javadoc 中明确标注外在弦质心语义与 $O(Nd)$ 闭式复杂度优势；
    3. 新增 `geodesicDistortionUpperBound(Centroid centroid, double maxGeodesicRadians)`，对 `maxGeodesicRadians < 1e-9` 采用极限值 `1.0` 防止 `0/0` `NaN`，对 $(0, \pi]$ 精确计算 $\left(\frac{\theta_{\max}/2}{\sin(\theta_{\max}/2)}\right)^2 \cdot \mathrm{meanSquaredChordalLoss}$；
    4. `ReversibleRingColdBuffer.warmCentroid()` 内部迁移为调用 `projector.extrinsicCentroid(vectors)`。

---

### 议题二：`ReversibleRingColdBuffer` 冷备档案容量上限（`maxArchiveCapacity`）与 `softDelete` 满额语义

- **Agent A（架构与形式化审计员）立场**：
  - 当前 [`ReversibleRingColdBuffer.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/manifold/ReversibleRingColdBuffer.java) 行 38 的 `archive` 是无界 `LinkedHashMap`，违反四元组有界状态空间不变量 $|\mathcal{H}| + |\mathcal{W}| + |\mathcal{C}| + |\mathcal{A}| \le C_{\mathrm{hot}} + C_{\mathrm{warm}} + C_{\mathrm{cold}} + C_{\mathrm{archive}}$。
  - 主张：引入 `DEFAULT_MAX_ARCHIVE_CAPACITY = 256` 与 5 参数构造器 `ReversibleRingColdBuffer(int hotCapacity, int warmCapacity, int coldCapacity, int maxArchiveCapacity, HypersphericalManifoldProjector projector)`，扩展 `Statistics` Record 为 8 字段：`(int hot, int warm, int cold, int archived, int archivedCapacity, long softDeleted, long restored, long rejectedArchive)`。
- **Agent C（测试、安全与门禁审计员）关键安全挑战**：
  - **重点质疑 `softDelete(RestorationToken)` 在 `archive` 满额时的行为**：
    在现有代码（行 124）中，`softDelete` 先执行 `Stored<T> removed = removeById(cold, token.episodeId())`。如果在 `archive.size() >= maxArchiveCapacity` 时仍然先从 `cold` 移除、然后又拒绝放入 `archive`，那么 `softDelete` 就会退化为**不可逆的静默物理硬删除**，直接违反《AGENTS.md》第七章第 5 条“环形冷备可逆隔离：严禁发生不可逆的物理硬删除”！
  - 主张：在 `softDelete(RestorationToken)` 中，必须**先检查 `archive.size() >= maxArchiveCapacity`**（或在定位到 `cold` 中存在该条目且 HMAC 验签通过后检查容量）：若 `archive` 已满，**不得从 `cold` 移除该条目**，而是保持该条目继续留在 `cold` 中，递增 `rejectedArchive++` 并返回 `false`；同时提供 `canArchive()` 预检方法。
- **Agent B（Java 生产桥接审计员）补充**：
  - 对于 `add(Episode<T>)` 引起的自然级联降级（`hot -> warm -> cold -> archive`），当 `cold` 溢出最老条目尝试进入 `archive` 而 `archive.size() >= maxArchiveCapacity` 时，由于这是固定容量环形缓冲区的自然容量边界（而非显式 `softDelete` 操作），拒绝写入 `archive` 并递增 `rejectedArchive++`，确保 `archive.size() <= maxArchiveCapacity` 严格成立。同时保留原 4 参数构造器向后兼容（委托给 5 参数构造器并传入 `DEFAULT_MAX_ARCHIVE_CAPACITY`）。
- **主 Agent 终局裁决（Binding Adjudication 2）**：
  - **完全采纳 Agent C 的非破坏性拒绝安全修正与 Agent A/B 的容量契约**：
    1. 新增 `DEFAULT_MAX_ARCHIVE_CAPACITY = 256`、5 参数构造器、`maxArchiveCapacity()` 访问器与 `canArchive()` 预检方法；
    2. `Statistics` 扩展为 `(int hot, int warm, int cold, int archived, int archivedCapacity, long softDeleted, long restored, long rejectedArchive)`；
    3. `add()` 触发 `cold` 溢出时：若 `archive.size() < maxArchiveCapacity` 则归档并 `softDeletedCount++`；若已满则拒绝写入 `archive` 并 `rejectedArchiveCount++`；
    4. `softDelete(token)` 执行时：先常量时间校验 `MessageDigest.isEqual` 验签，若 `archive.size() >= maxArchiveCapacity`，则**原条目保留在 `cold` 环中绝不移除**，递增 `rejectedArchiveCount++` 并返回 `false`。

---

### 议题三：`HoareTripleToolGuard` 九步短路合取序与 `HermesHarnessKernel.ExecutionSession` 真实四阶段编排

- **Agent A（架构与形式化审计员）立场**：
  - 当前 [`HoareTripleToolGuard.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/neurosymbolic/HoareTripleToolGuard.java) 行 45 仅执行 `Objects.requireNonNull(guardrails, "guardrails")`，完全未读取 `GuardrailsSpecificationRecord` 的 `maxCycleSteps`、`loopThreshold`、`maxTokenBudget`、`maxCostCny`。
  - 主张：新增 `ExecutionState(int currentStep, int loopFrequency, long estimatedTokens, double estimatedCostCny)` 与 4 个枚举值 `GUARDRAIL_STEP_LIMIT`, `GUARDRAIL_LOOP_THRESHOLD`, `GUARDRAIL_TOKEN_BUDGET`, `GUARDRAIL_COST_BUDGET`，按 9 步严格短路序校验前置条件 $P(s, a)$。
  - 同时，当前 [`HermesHarnessKernel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HermesHarnessKernel.java) 的 `prepare()` 与 `execute()` 未调用 6 个子组件。必须引入请求级会话 `ExecutionSession`，按四阶段状态机 $\mathsf{INIT} \to \mathsf{PREPARED} \to \mathsf{STEP\_GUARDED} \to \mathsf{EVALUATED}$ 真实串联全部 8 个组件。
- **Agent B（Java 生产桥接审计员）质询与补充**：
  - 指出在工具拦截时，`LocallyDenseCycleAutomaton.inspect(toolName, canonicalArgs)` 会返回 `CycleInspection(boolean rejected, int frequency, int stepIndex, ...)`。如果先调用 `cycleAutomaton.inspect`，其返回的 `inspection.frequency()` 恰好可以作为 `ExecutionState.loopFrequency` 传给 `HoareTripleToolGuard.verifyBefore(toolName, args, guardrails, contract, state)`！
  - 这样既让 `LocallyDenseCycleAutomaton` 完成了滑窗局部致密循环计数，又让 `HoareTripleToolGuard` 基于 `guardrails.loopThreshold()` 和 `guardrails.maxCycleSteps()` 给出统一的霍尔逻辑 `Decision` 与反例提示（`counterexampleHint`）。
- **Agent C（测试、安全与门禁审计员）补充**：
  - 强调当 `verifyBefore` 拒绝时，底层工具回调 `ToolCallback.call(...)` 的实际执行次数必须严格为 `0`（零副作用），且 `ExecutionSession` 必须记录被拒轨迹步（`TrajectoryStep` with `toolSuccess = false`），以便后续 `evaluateTrajectory` 中的 `TrajectoryAttributionAnalyzer` 能够精确归因到该失败步并由 `CounterexampleHarnessMutator` 生成变异提案。
- **主 Agent 终局裁决（Binding Adjudication 3）**：
  - **采纳三方合议**：
    1. `HoareTripleToolGuard` 新增 `ExecutionState` Record、4 个 `GUARDRAIL_*` 违约枚举及 5 参数 `verifyBefore(toolName, arguments, guardrails, contract, state)`；原 4 参数 `verifyBefore` 通过 `ExecutionState.fromArguments(arguments)` 提取或默认初始化后委托调用 5 参数版本；
    2. `HermesHarnessKernel` 新增 `openSession(String requestId, HermesHarnessVoucher voucher)` 返回请求级隔离的 `ExecutionSession`（内部持有该会话专属的 `LocallyDenseCycleAutomaton` 与 `ReversibleRingColdBuffer`）；
    3. `prepare(...)` 真实调用 `capabilityAssembler.assemble(...)`、向会话 `coldBuffer` 写入历史消息归一化特征快照并读取 `coldBuffer.statistics()`、调用 `harnessAssembler.assembleSystemPrompt(...)`；
    4. `ExecutionSession.invokeTool(...)` / `wrapToolCallback(...)` 真实按序执行：`cycleAutomaton.inspect()` $\to$ 构造 `ExecutionState` $\to$ `toolGuard.verifyBefore()`（拒绝则阻断底层工具调用并记录失败步）$\to$ 底层工具执行 $\to$ `toolGuard.verifyAfter()`；
    5. `ExecutionSession.evaluateTrajectory(...)`（及 `HermesHarnessKernel.evaluateTrajectory(...)`）真实串联 `attributionAnalyzer.analyze()` $\to$ `mutator.mutate()` $\to$ `paretoComposer.frontier()`，产出不可变的 `TrajectoryEvaluationResult`。

---

### 议题四：`HarnessKernelBridge` 三态模式与 `AgentOrchestrator` 无侵入接入位置

- **Agent B（Java 生产桥接审计员）法医级发现**：
  1. **发现 1（`ChatRequest.getInputParams()` 是 `String` 而非 `Map`）**：审查 [`ChatRequest.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/domain/ChatRequest.java) 行 114–120 证实，`inputParams` 和 `parameters` 均为 JSON `String`。因此 `HarnessKernelBridge.resolveCapabilityTag(...)` 必须用 `JSONObject.parseObject` 解析 JSON 字符串中的 `"harness.capabilityTag"` 或 `"capabilityTag"`，并在未显式指定时按实际非空载荷自动推断。
  2. **发现 2（`MemoryManager` 短期记忆与 `request.getHistoryList()` 双源对齐）**：在 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/orchestrator/AgentOrchestrator.java) 行 497–516 中，短期记忆通过 `memoryManager.getMessages(conversationId)` 加载为 `List<Message>`，而 `request.getHistoryList()` 可能为空。桥接器在组装 `effectiveHistory` 时，若 `request.getHistoryList()` 为空且短期记忆存在历史消息（排除当前轮刚追加的 UserMessage 或提取历史 User/Assistant 消息），应将其转换为 `List<ChatMessage>` 传入 `CapabilityContextAssembler`。
  3. **发现 3（切入点必须在 `planAndSolve` 之前）**：在 `AgentOrchestrator.java` 中，`if (planAndSolve)` 位于行 571，而 `ReActCycleGuard` 包装位于行 596。若在行 596 之后才切入 `HarnessKernelBridge`，则启用 `planAndSolve` 时会绕过 `ENFORCE` 模式的 Fail-Close 能力门禁。因此必须将 `ReActCycleGuard` 包装与 `harnessKernelBridge.bridgeExecution(...)` 统一提至 `if (planAndSolve)`（行 571）之前！
- **Agent A（架构与形式化审计员）与 Agent C（测试、安全与门禁审计员）复核意见**：
  - 一致认同 Agent B 的 3 项源码法医发现。
  - Agent C 进一步要求：当 `ENFORCE` 模式因 `CapabilityContextAssembler` 校验失败触发 `failClosed == true` 时，必须在 `if (planAndSolve)` 之前立即通过 `HarnessEvidenceCollector` 12 参数重载记录 `ExecutionMode.FAIL_SAFE` 证据，再调用 `emitter.next(new ChatEvent.ErrorEvent(...))` 和 `emitter.complete()` `return`，确保此时大模型 `chatClient` 的调用次数严格为 `0`。
- **主 Agent 终局裁决（Binding Adjudication 4）**：
  - **全盘采纳 Agent B 的 3 项源码法医级修正与 Agent C 的 Fail-Close 证据闭环设计**：
    1. 新增 `KernelBridgeMode`（`OFF | SHADOW | ENFORCE`）与 `HarnessKernelBridge`（位于 `tech.qiantong.qknow.hermes.agent.harness.kernel`），配置项 `hermes.harness.kernel.enabled=false`、`hermes.harness.kernel.mode=OFF`；
    2. `AgentOrchestrator` 保持全部现有构造器与公共方法签名 100% 不变，仅新增 `@Autowired(required = false) setHarnessKernelBridge` / `getHarnessKernelBridge`，默认字段初始化为 `HarnessKernelBridge.disabledInstance()`；
    3. 在 `AgentOrchestrator` 行 571 `if (planAndSolve)` 之前完成 `ReActCycleGuard` 与 `harnessKernelBridge.bridgeExecution(...)` 接入；
    4. 为 `HarnessEvidenceCollector` 新增 12 参数重载（显式接收 `ExecutionMode`），使 `ENFORCE` Fail-Close 阻断时能够准确落盘 `ExecutionMode.FAIL_SAFE` 证据。

---

### 议题五：18 项 Fail-Close 负面测试矩阵、跨请求状态隔离、全量测试计数口径与任务书双 Hash

- **Agent C（测试、安全与门禁审计员）立场**：
  1. 制定了覆盖 `CapabilityContextAssembler`（5 项）、`HoareTripleToolGuard`（8 项）、`LocallyDenseCycleAutomaton`（2 项）、`ReversibleRingColdBuffer`（2 项）、`HarnessKernelBridge` `ENFORCE` 端到端阻断（1 项）的 **18 项确定性负面与边界测试矩阵**，全部使用本地内存替身，零外网调用；
  2. 设计了跨请求并发/顺序隔离测试：在 `ENFORCE` 模式下，Request 1 连续调用同一工具触发局部致密循环告警，随后发起 Request 2 调用同一工具 1 次，断言 Request 2 的 `loopFrequency == 1` 且正常放行，证明请求级 `ExecutionSession` 零跨请求污染；
  3. 查明了 Stage 6 验收指出的测试计数差异根因：`-pl tests -am` 共执行 43 个模块（`qknow-tests: 1942` + `qknow-hermes-core: 133` + `其余依赖模块: 41` = `2116`），而 `qknow-server` 不在 `-pl tests -am` 子图中，其 `target/surefire-reports` 存有历史残留。阶段七在跑全量测试前必须先清理残留 `surefire-reports` 并按三轨口径清晰列报；
  4. 制定了 `50-harness7-taskbook.md` 双 Hash（`body_sha256` 从 `^## 一、` 起算 + `full_file_sha256` 全文件校验）防篡改锁定流程。
- **Agent A 与 Agent B 复核意见**：
  - 无异议，完全赞同。
- **主 Agent 终局裁决（Binding Adjudication 5）**：
  - **采纳 Agent C 的全部测试、安全与门禁规范**，直接写入 `50-harness7-taskbook.md` 第四、五、六章。

---

## 二、三方辩论终局裁决汇总表（映射至 `50-harness7-taskbook.md`）

| 裁决编号 | 核心事项 | 最终裁决方案 | 对应阶段与目标分支 | 落地文件 |
|---|---|---|---|---|
| **ADJ-01** | 超球面质心重命名与测地线失真界 | 主方法定名 `extrinsicCentroid` + 别名 `chordalCentroid`；保留 `@Deprecated frechetMean`；新增 `geodesicDistortionUpperBound` | Stage 0 (`feat/hermes-harness-stage6`) | `HypersphericalManifoldProjector.java` |
| **ADJ-02** | 冷备档案容量上限与非破坏性软删除拒绝 | 新增 `maxArchiveCapacity`（默认 256）与 8 字段 `Statistics`；满额时 `softDelete` 保留条目在 `cold` 中并返回 `false` | Stage 0 (`feat/hermes-harness-stage6`) | `ReversibleRingColdBuffer.java` |
| **ADJ-03** | 霍尔守卫接入 Guardrails 四维阈值 | 新增 `ExecutionState` 与 4 个 `GUARDRAIL_*` 违约枚举，按 9 步短路序强制检验 `maxCycleSteps / loopThreshold / maxTokenBudget / maxCostCny` | Stage 0 (`feat/hermes-harness-stage6`) | `HoareTripleToolGuard.java` |
| **ADJ-04** | Kernel 请求级会话与 8 组件真实编排 | 新增 `ExecutionSession`，真实串联 `CapabilityAssembler`、`ColdBuffer`、`HarnessAssembler`、`CycleAutomaton`、`ToolGuard`、`AttributionAnalyzer`、`Mutator`、`ParetoComposer` | Stage 0 (`feat/hermes-harness-stage6`) | `HermesHarnessKernel.java` |
| **ADJ-05** | `HarnessKernelBridge` 三态模式与 `AgentOrchestrator` 接入 | 新增 `KernelBridgeMode (OFF/SHADOW/ENFORCE)` 与 `HarnessKernelBridge`；在 `AgentOrchestrator` 行 571 `planAndSolve` 之前无侵入接入；默认 `enabled=false, mode=OFF` | Stage 4 (`feat/hermes-harness-stage7`) | `KernelBridgeMode.java`, `HarnessKernelBridge.java`, `HarnessEvidenceCollector.java`, `AgentOrchestrator.java` |
| **ADJ-06** | 确定性测试矩阵与三轨测试计数口径 | 更新 `HermesHarnessKernelArchitectureTest`（Stage 0），新增 `HermesHarnessStage7BridgeTest`（Stage 4，含三态模式、18 项 Fail-Close 与跨请求隔离），统一三轨 Surefire 报告口径 | Stage 0 & Stage 4 | `HermesHarnessKernelArchitectureTest.java`, `HermesHarnessStage7BridgeTest.java` |
