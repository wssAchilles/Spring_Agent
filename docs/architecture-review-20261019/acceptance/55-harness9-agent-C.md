# 智能体 C：阶段九（Stage 9）候选匿名选择、Position-Swap 一致性与零泄漏法医审计报告

- **物理子智能体 `conversationId`**：[`431fe51b-d0fc-46dc-a6b0-91f6395a4d1c`](conversation://431fe51b-d0fc-46dc-a6b0-91f6395a4d1c)
- **物理运行 Transcript 存证**：[transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/431fe51b-d0fc-46dc-a6b0-91f6395a4d1c/.system_generated/logs/transcript.jsonl)
- **审计角色**：智能体 C — 选择与防泄漏审计员 (Selection & Anti-Leakage Auditor)
- **证据等级**：Auditor (Tier 3)
- **审计源码基线**：
  1. `/Users/achilles/Documents/优秀开源项目/harness2/harness2/core/selection.py`（行 1–506，重点审计 `assign_ids`、`_render_pool`、`_write_candidates`、`_fallback`、`run_selection`）
  2. `/Users/achilles/Documents/优秀开源项目/harness2/harness2/core/context.py`（行 1–547，重点审计 `copy_source`、`assert_no_leak_paths`、`GRADER_VERDICT_MARKERS`、`assert_no_grader_verdicts`、`assert_tree_has_no_grader_verdicts`）
  3. `/Users/achilles/Documents/优秀开源项目/harness2/harness2/core/recursion.py`（行 1–579，重点审计 `_accept_tree`、`_run_stage` 失败回退与零泄漏拦截）
  4. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/evolution/ParetoFrontierComposer.java`（行 1–47）
  5. `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/PositionSwapBlindEvaluator.java`（行 1–163）与 `DualBlindSelectionEvaluator.java`（行 1–165）
  6. `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/EvidenceDiffExtractor.java`（行 1–60）与 `SelectionDataLeakageTripwire.java`（行 1–93）
  7. `backend/pom.xml`（行 19, 260–279，`maven-surefire-plugin` 与 `-Plive` Profile 隔离配置）

---

## 问题 1：匿名候选映射（Anonymous Candidate Mapping）协议设计

### 1.1 源码法医对比与设计动机
在 Google Research `harness2/core/selection.py`（行 71–87）中，`assign_ids(labels, *, task_id, k, mode)` 使用 `SHA-1(f"{task_id}|{k}|{mode}")` 前 12 位十六进制作为确定性伪随机种子，对排序后的候选标签洗牌并映射为匿名代号 `A, B, C...`；并在 `_write_candidates`（行 183–219）中将候选产物写入以匿名 ID 命名的独立文件，彻底剥离 `base` 或 `c1..ck` 原始身份标识。

在 Stage 9 主干实现中，必须将原本散落在 `src/test` 下的匿名映射逻辑正式收敛提升至 `src/main/java/tech/qiantong/qknow/hermes/agent/harness/ppc/PpcComposer.java`，确保选择器在比较 `baseline` 与 `candidate_X`（如 `candidate_A`、`candidate_B`）时处于**绝对盲态（Identity-Blind State）**，彻底消除对“基线身份（Status-Quo Bias）”或“优化变异体命名（Name-Halo Bias）”的偏袒。

### 1.2 确定性哈希种子匿名映射数学定义与代码契约
1. **确定性置换位生成（Deterministic Swap-Bit Generation）**：
   对于给定的基准任务标识 $\text{taskId}$、待比较的候选标识 $\text{candidateId} \in \{\texttt{"candidate\_A"}, \texttt{"candidate\_B"}, \texttt{"candidate\_C"}\}$ 以及固定协议盐值 $\text{salt} = \texttt{"STAGE9\_PPC\_ANONYMOUS\_SALT\_20261031"}$，构造规范化种子字符串：
   $$s(\text{taskId}, \text{candidateId}) = \text{taskId} \mathbin{\|} \texttt{"|"} \mathbin{\|} \text{candidateId} \mathbin{\|} \texttt{"|"} \mathbin{\|} \text{salt}$$
   计算 SHA-256 摘要 $\mathbf{h} = \text{SHA-256}(\text{UTF-8}(s))$，取首字节最低有效位（LSB）作为确定性初始置换位：
   $$b_{\text{swap}} = (\mathbf{h}[0] \mathbin{\&} \texttt{0x01}) == 1$$
2. **双射匿名映射算子 $\pi_{b_{\text{swap}}}$**：
   $$\pi_{b_{\text{swap}}}(\texttt{Option\_A}) = \begin{cases} \text{candidate}_X, & \text{if } b_{\text{swap}} = \text{true} \\ \text{baseline}, & \text{if } b_{\text{swap}} = \text{false} \end{cases}, \qquad \pi_{b_{\text{swap}}}(\texttt{Option\_B}) = \begin{cases} \text{baseline}, & \text{if } b_{\text{swap}} = \text{true} \\ \text{candidate}_X, & \text{if } b_{\text{swap}} = \text{false} \end{cases}$$
3. **身份剥离的盲态视图数据结构（`AnonymousOptionView`）**：
   传入盲态比较器的唯一数据结构必须为不可变 Record `AnonymousOptionView`，**严禁包含 `candidateId`、`isBaseline`、`voucherFingerprint`、`systemPrompt` 或 `evidenceId`**：
   ```java
   public record AnonymousOptionView(
           String anonymousOptionId,      // 仅允许 "Option_A" 或 "Option_B"
           String sanitizedOutputSummary, // 脱敏后的模型最终回答摘要
           boolean qualityPass,           // 离线质量门槛是否通过
           long promptTokens,
           long completionTokens,
           long cacheHitTokens,
           long totalTokens,
           double actualCostCny,
           long durationMs,
           boolean toolTrajectoryValid,
           boolean guardClean,
           boolean cycleFree
   ) {}
   ```
   匿名映射表 `Map<String, String> anonToRealId` 仅保存在 `PpcComposer` 的闭合作用域内，待盲态比较器返回 `"Option_A"`、`"Option_B"` 或 `"TIE"` 后，再由 `PpcComposer` 执行逆映射还原出物理胜出者。

---

## 问题 2：Position-Swap 双向对调一致性协议（Position-Swap Consistency Protocol）

### 2.1 为什么要执行 Pass 1 `(Option_A, Option_B)` 与 Pass 2 `(Option_B, Option_A)` 双向对调评估？
1. **消除大模型与启发式裁判的位置偏置（Primacy & Recency Bias）**：无论使用 LLM-as-a-Judge 还是带平局破除规则的算法比较器，成对比较（Pairwise Comparison）普遍存在非对称位置偏置（例如倾向于选择第一个出现的选项 `Option_A`，或在指标相近时默认维持左侧参数胜出）。
2. **逼出非对称噪声与脆弱边界（Fragile Boundary Detection）**：如果某候选仅因处于特定展示位置（如第一个位置）才侥幸获胜，一旦对调顺序就反转结果，说明其优势低于位置噪声阈值，绝不能被采纳。

### 2.2 严格的数学对称性公理与四象限裁决状态机
设盲态成对比较算子为：
$$\mathcal{J}: \mathcal{V}_{\text{anon}} \times \mathcal{V}_{\text{anon}} \to \{\texttt{FIRST}, \texttt{SECOND}, \texttt{TIE}\}$$
其中 $\texttt{FIRST}$ 表示排在第一个入参位置的选项胜出，$\texttt{SECOND}$ 表示排在第二个入参位置的选项胜出。

对于任务 $t$，令 Pass 1 输入序列为 $(V_A, V_B)$，其中 $V_A$ 对应物理实体 $E_A = \pi_{b_{\text{swap}}}(\texttt{Option\_A})$，$V_B$ 对应物理实体 $E_B = \pi_{b_{\text{swap}}}(\texttt{Option\_B})$。
- **Pass 1（正向评估）**：调用 $r_1 = \mathcal{J}(V_A, V_B)$，物理胜出者映射函数为：
  $$W_1 = \phi_1(r_1) = \begin{cases} E_A, & \text{if } r_1 = \texttt{FIRST} \\ E_B, & \text{if } r_1 = \texttt{SECOND} \\ \texttt{TIE}, & \text{if } r_1 = \texttt{TIE} \end{cases}$$
- **Pass 2（对称位置对调评估）**：物理交换入参顺序为 $(V_B, V_A)$，调用 $r_2 = \mathcal{J}(V_B, V_A)$，物理胜出者映射函数为：
  $$W_2 = \phi_2(r_2) = \begin{cases} E_B, & \text{if } r_2 = \texttt{FIRST} \\ E_A, & \text{if } r_2 = \texttt{SECOND} \\ \texttt{TIE}, & \text{if } r_2 = \texttt{TIE} \end{cases}$$

**对称一致性公理（Axiom of Position-Swap Consistency）**：
$$\text{positionSwapConsistent} \iff (W_1 == W_2) \land (W_1 \in \{\text{baseline}, \text{candidate}_X\})$$

| 象限 | Pass 1 物理指向 ($W_1$) | Pass 2 物理指向 ($W_2$) | `positionSwapConsistent` | 最终裁决结果 (`Verdict`) | 处置动作 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Quadrant I** | `candidate_X` | `candidate_X` | **`true`** | `ROBUST_CANDIDATE_WIN` | 承认该候选在该 case 上稳定优于 `baseline` |
| **Quadrant II** | `baseline` | `baseline` | **`true`** | `ROBUST_BASELINE_WIN` | 判定 `baseline` 胜出，拒绝候选 |
| **Quadrant III** | `candidate_X` (或 `baseline`) | `baseline` (或 `candidate_X`) | **`false`** | `INCONSISTENT_POSITION_BIAS` | 两次评估指向不同物理实体（位置偏置冲突），强制判为 `INCONSISTENT_TIE` 并**保守回退保留 `baseline`** |
| **Quadrant IV** | 任一为 `TIE` | 任一为 `TIE` | **`false`** | `TIE_KEEP_BASELINE` | 优势不显著，奥卡姆剃刀保守回退保留 `baseline` |

> **全局候选采纳门禁（Candidate Adoption Gate）**：
> 候选 `candidate_X` 只有在满足：(1) 5 个基准任务 **100% 满足 `qualityPass == true`**（零质量退化）、(2) 零触发 `cycleDetected` 与非预期 `guardBlocked`、(3) 跨任务的 `Position-Swap` 双向评估全部满足 `positionSwapConsistent == true`（无位置偏置冲突）、且 (4) 聚合总成本 $\sum \text{actualCostCny}(\text{cand}) < \sum \text{actualCostCny}(\text{base})$（或总 Token 严格下降且总成本不增）时，才允许被 `PpcComposer` 采纳；否则一律保守保留 `baseline`，诚实输出 `REJECTED_AFTER_REAL_PPC`。

---

## 问题 3：零 qrel 泄漏铁律（Zero-qrel Leakage Invariant）

### 3.1 Proposer、Probe 与 Selector/Judge 三阶段输入边界法医审计
参照 `harness2/core/context.py` 的 `assert_no_leak_paths`（行 399–416）与 `assert_no_grader_verdicts`（行 454–518），Stage 9 对三个阶段的输入边界设立严格的信息流隔离墙（Information Flow Firewall）：

| 阶段 | 允许流入的合法输入（Allowed Inputs） | 严禁流入的禁区数据（Forbidden / Leakage Inputs） | 为什么严禁流入（理论与法医依据） |
| :--- | :--- | :--- | :--- |
| **1. Proposer（候选生成阶段）** | 基线 `HarnessConfig`（通用系统提示词契约、默认 `maxCycleSteps`、`guardPolicy`）及通用单变量代数变异算子标识（`CONCISE_SYSTEM_PROMPT`、`TIGHT_CYCLE_STEPS`） | 任何 `PpcBenchmarkCase` 的 `expectedKeywords`（qrel 黄金答案词）、`expectedToolNames`、测试集具体题目文本或历史评分判词 | 若 `expectedKeywords` 进入 Proposer，变异器会将答案词（如 `"幂等键"`、`"TX-20261029-8891"`）直接写入候选 `systemPrompt`，构成**答案塞入作弊（Answer-Key Stuffing）**，使实验完全丧失外部有效性 |
| **2. Probe（真实运行阶段）** | 单题用户 `question`、候选 `HarnessConfig`、真实能力夹具（`RAGContext` 知识切片、可用 `ToolCallback` 定义、多轮 `ChatMessage` 历史） | `expectedKeywords`（qrel）、`expectedToolNames`（期望命中的答案工具名）、离线评分器（Grader）规则 | 探针运行必须 100% 模拟真实在线推理环境；真实生产环境中系统绝不可能预先知道用户提问的标准答案关键词 |
| **3. Selector / Judge（盲评选择阶段）** | 匿名化的 `AnonymousOptionView`（脱敏回答摘要、布尔 `qualityPass` 门禁标志、真实 Token 细项、真实 `actualCostCny`、耗时与符号守卫状态） | `expectedKeywords` 原文、离线 Grader 的关键词命中明细、任何包含 `GRADER_VERDICT_MARKERS` 的判词文本 | `expectedKeywords` / `qrel` 只能用于 Probe 运行完成后的**独立离线质量门槛校验器（Isolated Post-Run Quality Gate）**以生成布尔准入标志 `qualityPass`；绝不能进入盲评 Judge 提示词，否则会诱导按关键词频次作弊选择 |

### 3.2 代码级 `assertNoQrelLeakage` 强制拦截契约
在 `PpcComposer`（及 `PpcProposer`）中必须内置静态硬断言方法 `assertNoQrelLeakage`，在**候选生成后**、**Probe 执行前**以及**进入 Selector 比较前**三次强制调用：

```java
public static void assertNoQrelLeakage(
        HarnessConfig config,
        List<PpcBenchmarkCase> suite,
        String additionalSelectorContext) {
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(suite, "suite");
    String promptLower = (config.systemPrompt() == null ? "" : config.systemPrompt()).toLowerCase(Locale.ROOT);
    String ctxLower = (additionalSelectorContext == null ? "" : additionalSelectorContext).toLowerCase(Locale.ROOT);

    // 1. 严查任何基准任务的 qrel (expectedKeywords) 是否泄漏进候选 System Prompt 或 Selector Prompt
    for (PpcBenchmarkCase tc : suite) {
        for (String qrelKeyword : tc.expectedKeywords()) {
            if (qrelKeyword == null || qrelKeyword.isBlank()) continue;
            String kwLower = qrelKeyword.trim().toLowerCase(Locale.ROOT);
            if (promptLower.contains(kwLower)) {
                throw new IllegalStateException(
                        "QREL_LEAKAGE_DETECTED: Candidate [" + config.candidateId()
                        + "] systemPrompt leaked qrel keyword [" + qrelKeyword + "] from case [" + tc.taskId() + "]");
            }
            if (ctxLower.contains("qrel:" + kwLower) || ctxLower.contains("expected_keyword:" + kwLower)) {
                throw new IllegalStateException(
                        "QREL_LEAKAGE_DETECTED: Selector context leaked qrel keyword [" + qrelKeyword + "]");
            }
        }
    }

    // 2. 严查 Grader Verdict 标记词（对齐 harness2/core/context.py GRADER_VERDICT_MARKERS）
    for (String marker : GRADER_VERDICT_MARKERS) {
        String markerLower = marker.toLowerCase(Locale.ROOT);
        if (promptLower.contains(markerLower) || ctxLower.contains(markerLower)) {
            throw new IllegalStateException(
                    "GRADER_VERDICT_LEAKAGE_DETECTED: Context contains forbidden grader marker [" + marker + "]");
        }
    }
}
```

---

## 问题 4：不可信指标（`UNKNOWN` Usage / Cost 与虚假 `ragScore`）的强制剔除规则

### 4.1 为什么 `UNKNOWN` Usage / `costKnown == false` 绝不能计为 `tokens = 0, cost = 0.0` 参与 Pareto 选择？
法医审计 `ParetoFrontierComposer.java`（行 19–28）可见：
```java
public boolean dominates(Candidate left, Candidate right) {
    return left.fidelity() >= right.fidelity()
            && left.tokens() <= right.tokens()
            && left.latencyMs() <= right.latencyMs()
            && (left.fidelity() > right.fidelity()
                || left.tokens() < right.tokens()
                || left.latencyMs() < right.latencyMs());
}
```
在多目标帕累托优化中，$\text{tokens}$ 与 $\text{actualCostCny}$ 均为**越小越优（Minimization）**的非负物理量（真实值满足 $\text{tokens} > 0, \text{actualCostCny} > 0$）。
- **零成本中毒定理（Zero-Cost Poisoning Vulnerability）**：若某个 Probe 因流式中断、网络超时或 Provider 未返回 `usage` 而处于 `usageStatus == UsageStatus.UNKNOWN`（或 `costKnown == false`），此时其数值字段默认存放为 `0L` 和 `0.0D`。一旦允许该记录进入 `ParetoFrontierComposer` 或成本均值计算，这个缺失 Usage 的候选就会在数学上取得 $\text{tokens} = 0 < \text{tokens}_{\text{real}}$ 与 $\text{cost} = 0.0 < \text{cost}_{\text{real}}$ 的**虚假全局下确界**，从而在帕累托前沿上错误地“严格支配”所有真实返回了合法 Token 与成本的候选！

### 4.2 `PpcComposer` 入口硬剔除规则（Hard Drop Gate）
在 `PpcComposer.selectBest` 入口处，必须在任何指标汇总和比较之前执行**一票否决硬剔除**：
1. 对候选 $c$ 的所有 Probe 结果 $r \in \text{probes}(c)$，检查以下不变式：
   $$\text{isProbeMetricCredible}(r) \iff r.\text{usageStatus}() == \texttt{UsageStatus.KNOWN} \land r.\text{costKnown}() \land r.\text{totalTokens}() > 0 \land r.\text{promptTokens}() > 0 \land r.\text{actualCostCny}() > 0.0 \land r.\text{durationMs}() > 0$$
2. 若候选 $c$ 缺失任何一个基准任务的 Probe（$\text{probes}(c).\text{size}() < 5$），或存在**哪怕 1 个** Probe 不满足 $\text{isProbeMetricCredible}(r)$：
   - 立即将候选 $c$ 移入 `droppedCandidates` 集合，记录剔除原因 `DROPPED_UNKNOWN_USAGE_OR_COST`；
   - **严禁**将其映射到 `Option_A / Option_B`，**严禁**将其传入 `ParetoFrontierComposer`。
3. 若 `baseline` 自身的任一 Probe 不满足 $\text{isProbeMetricCredible}(r)$：
   - 整个 `PpcComposer` 立即中止选择，标记实验状态为 `ABORTED_UNCREDIBLE_BASELINE_USAGE`，严禁在基线失真的情况下选出任何变异候选。

### 4.3 为什么在接通真实 Qwen 1024 维向量余弦打分前，必须显式禁用 `ragScore`？
1. **超球面余弦几何保真铁律**：根据《AGENTS.md》第七章第 2 条，本系统唯一合法的向量相似度分必须来自**阿里千问 `text-embedding-v4` 在 1024 维单位超球面 $\mathbb{S}^{1023} \subset \mathbb{R}^{1024}$（$\|\mathbf{v}\|_2 = 1$）上的真实余弦内积 $\cos\theta = \mathbf{u} \cdot \mathbf{v}$**。
2. **杜绝常数/启发式伪打分污染**：在 Stage 9 的 DeepSeek PPC 探针中，`kb-qa-01` 与 `comp-rag-tool-05` 通过 `RAGContext` 注入确定性知识切片以验证 RAG 能力链路，并未在本地实时调用千问 Embedding API 计算每个切片的 1024 维余弦内积。如果沿用占位常数（如 `0.85`）作为 `ragScore` 参与候选质量加权，就是典型的**伪造指标（Fake Metric）**。
3. **Stage 9 硬性冻结规则**：
   - 在 `PpcProbeResult` 中显式设置字段 `boolean ragScoreEnabled = false`，并将 `ragScore` 锁定为不可用于比较的禁用状态；
   - `PpcComposer` 在评估 `RAG` 与 `COMPOSITE` 任务时，**仅允许核验确定性可证伪指标**：布尔值 `ragRetrieved`（是否成功注入并检索非空知识切片）、`ragChunkIds`（真实归因的切片 ID 列表，如 `["chunk-pay-idem-01", "chunk-pay-retry-02"]`）以及回答是否通过离线 `qualityPass` 校验。

---

## 问题 5：默认测试门禁隔离与可复现性契约

### 5.1 默认离线契约测试 vs. 付费真实测试（`@Tag("live")`）双轨物理隔离
经核验 `backend/pom.xml`（行 19, 263–279），项目父 POM 已建立完备的 Maven Surefire 标签隔离机制：
- 默认属性：`<hermes.live.excludedGroups>live</hermes.live.excludedGroups>`；
- `maven-surefire-plugin` 配置：`<excludedGroups>${hermes.live.excludedGroups}</excludedGroups>`；
- `live` Profile：当且仅当命令行传入 `-Plive` 时，覆盖 `<hermes.live.excludedGroups></hermes.live.excludedGroups>`。

基于此机制，Stage 9 的测试套件严格实施双轨设计：

| 维度 | 轨道一：离线契约测试 (`PpcCostAndSelectionContractTest`, `PpcOrchestratorOfflineTest`) | 轨道二：付费真实 Live PPC 测试 (`HermesHarnessStage9LivePpcTest`) |
| :--- | :--- | :--- |
| **JUnit 5 标签** | **无 `@Tag("live")` 标签** | **类级别强制标注 `@Tag("live")`** |
| **触发命令** | 默认 `mvn test -pl qknow-hermes/qknow-hermes-core` 自动执行 | 仅通过 `mvn test -pl qknow-hermes/qknow-hermes-core -Plive -Dtest=HermesHarnessStage9LivePpcTest` 显式触发 |
| **网络与 API Key 依赖** | **0 外网请求、0 API Key 依赖、0 人民币消耗** | 依赖外网 `api.deepseek.com` 与真实 `DEEPSEEK_API_KEY`，受 `<= ¥10.00` 硬预算熔断保护 |
| **执行模式标记** | 显式标记 `ExecutionMode.DETERMINISTIC_TEST_DOUBLE` | 显式标记 `ExecutionMode.REAL_PRODUCTION` |
| **落盘目录隔离** | 仅允许使用 JUnit `@TempDir` 临时目录，测试结束自动清理 | 写入 `.gitignore` 保护的 `experiment/outputs/harness9-live/<runId>/`，绝不触碰 `harness4` / `harness5` 已跟踪资产 |
| **核心验证职责** | 1. 北京时间 Peak/Off-Peak 官方价格公式精确计算；<br>2. `assertNoQrelLeakage` 拦截关键词与判词泄漏；<br>3. `UNKNOWN` usage / `costKnown=false` 候选 100% 硬剔除；<br>4. Position-Swap 对调冲突时 100% 判 `INCONSISTENT_TIE` 并回退 `baseline`；<br>5. 预算看门狗（Probes/Calls/Tokens/Cost）超限立即熔断为 `BUDGET_EXHAUSTED`。 | 1. 真实 `deepseek-flash` + 真实 `AgentOrchestrator` 跑完 5 cases × (baseline + candidate_A + candidate_B) = 15 probes；<br>2. 校验 5 类能力标签（`RAG`, `TOOL`, `HISTORY`, `COMPOSITE`）真实生效；<br>3. 产出含 SHA-256 校验和的真实 `ppc_summary.json` 与 `selected_candidate.json`。 |

### 5.2 缺少 `DEEPSEEK_API_KEY` 时的诚实 `EMPIRICAL_BLOCKED` 协议（严禁 Mock 伪造）
为彻底杜绝“在缺少 API Key 时用 Mock 替身伪造 `REAL_PRODUCTION` 证据”的学术不端风险，`PpcOrchestrator` 与 `HermesHarnessStage9LivePpcTest` 必须严格执行以下诚实阻断状态机：

1. **真实密钥前置核验函数（`resolveValidLiveApiKey`）**：
   - 按顺序读取环境变量 `DEEPSEEK_API_KEY` 与系统属性 `deepseek.api.key`；
   - 若值为 `null`、空字符串、长度 `< 16`，或包含任何伪造/占位特征词（`"placeholder"`, `"your-api-key"`, `"mock"`, `"dummy"`, `"test-key"`, `"sk-xxx"`），判定为**无有效真实密钥（`apiKeyValid = false`）**。
2. **无密钥时的强制诚实阻断行为（Fail-Honest as `EMPIRICAL_BLOCKED`）**：
   - 当 `PpcOrchestrator` 检测到 `apiKeyValid == false` 时：
     - **绝对禁止**初始化或回退到 `DeterministicHarnessTestDouble` 或任何本地 Mock `ChatModel`；
     - **绝对禁止**生成任何带有 `"executionMode": "REAL_PRODUCTION"` 的伪造 Probe JSON 文件；
     - 立即返回模式为 `PpcMode.EMPIRICAL_BLOCKED` 的 `PpcRun`（其中 `completedProbes = 0`、`totalModelCalls = 0`、`totalCostCny = 0.0`），并诚实声明阻断原因。
