# Stage 21 架构研究与设计规划任务书：Cognitive Core v1

> **规划状态：CONDITIONAL / Stage 21A ONLY**。本文后续的并行 wave、复杂重规划、余弦死锁检测和量化 KPI 是路线图或待验证假设，不是 Stage 21A 实施契约。当前推荐顺序为 **Stage 21A 最小 PVER → Stage 23A 最小过程评测 → Stage 22A 最小经验选择**；扩展项需基于评测结果另行冻结。

**Stage 21A 范围冻结草案**：仅包含 `TaskAnalysis → Plan → PlanVerification → Sequential Execution → Reflection`。执行严格顺序，不做 wave/多 Agent 并发、不做复杂重规划或自动策略演化。Reflection 只产出结构化观察/结果，不隐含无界 retry、重规划或经验写入。baseline 当前为**待测**；成功率、失败误判率、自愈率、工具选择和延迟目标均为**待测/待预注册**，不得引用本文旧数值作为事实或验收门槛。

- **报告标识**：HERMES-STAGE21-COGNITIVE-CORE-V1
- **战略定位**：全面对标顶级期刊（ESWA / IEEE TKDE / ACM TOIS）水准的 Hermes 智能体认知编排核心架构
- **责任角色**：Hermes Agent 阶段二十一研究与架构规划员 (Cognitive Core Specialist)
- **规划基线**：Stage 20 CLOSED；88/89/90 旧计划仅作为 DEFERRED 历史记录保留；当前候选顺序如下，各阶段均需独立任务书与授权：
  - **Stage 21A: 最小 PVER（当前规划候选，尚未授权实施）**
  - **Stage 23A: 最小过程评测（21A 之后、22A 之前）**
  - **Stage 22A: 最小经验检索、策略模板与人工/规则选择**
  - Stage 21B/22B/23B 扩展项根据测量结果另行冻结。
- **运行环境基准**：
  - 运行时：Java 21 独立隔离虚拟环境 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)，严禁污染宿主 Java 17；
  - 生成模型基线：DeepSeek API 官方活跃模型（如 `deepseek-flash` 等），思维链解析基于 `reasoning_content`；
  - 向量几何基线：阿里千问 (Qwen) Embedding (`text-embedding-v4`)，1024 维超球面单位向量（$\mathbb{S}^{1023} \subset \mathbb{R}^{1024}$，$\|\mathbf{v}\|_2 = 1$），度量基准为余弦相似度。

---

## 目录
- [一、现有系统代码深度只读审计报告](#一现有系统代码深度只读审计报告)
  - [1.1 审计覆盖的核心源码路径清单](#11-审计覆盖的核心源码路径清单)
  - [1.2 十大核心问题法医级诊断回答 (事实 vs 推断)](#12-十大核心问题法医级诊断回答-事实-vs-推断)
  - [1.3 现有认知与编排代码拓扑断层剖析](#13-现有认知与编排代码拓扑断层剖析)
- [二、学术前沿与工业界架构深度调研 (Research Ledger)](#二学术前沿与工业界架构深度调研-research-ledger)
  - [2.1 六大核心范式对比与适用性定性](#21-六大核心范式对比与适用性定性)
  - [2.2 严格遵守 14 字段规范的 Research Ledger](#22-严格遵守-14-字段规范的-research-ledger)
- [三、候选架构方案深度比较与裁决](#三候选架构方案深度比较与裁决)
  - [3.1 候选架构方案定义](#31-候选架构方案定义)
  - [3.2 九维全景对比矩阵](#32-九维全景对比矩阵)
  - [3.3 Stage 21A 范围裁决](#33-stage-21a-范围裁决非实证排名)
- [四、Stage 21: Cognitive Core v1 架构设计与数据契约草案](#四stage-21-cognitive-core-v1-架构设计与数据契约草案)
  - [4.1 认知状态机 (FSM) 形式化建模与数学定义](#41-认知状态机-fsm-形式化建模与数学定义)
  - [4.2 核心数据结构契约草案 (纯 Java 21 Record)](#42-核心数据结构契约草案-纯-java-21-record)
  - [4.3 核心组件架构与契约接口设计](#43-核心组件架构与契约接口设计)
  - [4.4 失败自愈、重规划防环与资源熔断机制](#44-失败自愈重规划防环与资源熔断机制)
  - [4.5 与 AgentOrchestrator 的无缝集成方案](#45-与-agentorchestrator-的无缝集成方案)
- [五、核心契约验证计划与指标评估体系](#五核心契约验证计划与指标评估体系)
  - [5.1 纯本地确定性测试验证矩阵](#51-纯本地确定性测试验证矩阵)
  - [5.2 阶段验收基准与关键评价指标 (KPI)](#52-阶段验收基准与关键评价指标-kpi)
- [六、规划总结与六项核心返回结论](#六规划总结与六项核心返回结论)

---

## 一、现有系统代码深度只读审计报告

### 1.1 审计覆盖的核心源码路径清单

依据任务书指令，规划员对 Hermes 系统内核进行了全量只读法医级代码审查，覆盖以下 10 个关键类与路径：

1. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java`（特别是 `executeAgent` 流调度、`planAndSolve` 分支、ReAct 循环、多智能体协同及容量门禁控制）；
2. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/config/PlanSolveConfig.java`（配置项前缀 `qknow.hermes.plan-solve`，默认开关、重试与并发参数）；
3. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/dag/HierarchicalTaskPlanner.java` 与 `PhasedExecutionPlan.java`、`DagTaskNode.java`（分层任务分解与 Kahn 拓扑排序算法）；
4. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/planner/TreeOfThoughtsReActPlanner.java` 与 `impl/DefaultTreeOfThoughtsReActPlanner.java`（MCTS/ToT 思维树搜索规划器）；
5. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/WorkerAgent.java`（包装 `ReactAgent`，支持工具拦截器与千问超球面向量工具动态意图投影）；
6. 现有 Plan / Task / Step 数据结构（包括私有内部 `AgentOrchestrator.PlanTask`、`DagTaskNode`、`PhasedExecutionPlan`、`StepProcessRewardReceipt` 等）；
7. 现有工具选择与 ToolCallback 路径（`ToolCallDispatch` DFA 状态机拦截、`ToolDiscoveryService` 向量投影、`ReActCycleGuard` 死循环保护）；
8. 现有 Reflection / retry / fallback 逻辑（`EvaluatorOptimizerGovernor` 双环评估优化器、`CognitiveSelfHealingStateMachine` 认知自愈状态机）；
9. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessEvidenceCollector.java` 与 `HarnessEvidenceRecord.java`（轻量级执行物理摘要凭单收集器）；
10. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/experience/ExperienceLibrary.java` 与 `DefaultExperienceLibrary.java`（经验沉淀与少样本召回引擎）。

---

### 1.2 十大核心问题法医级诊断回答 (事实 vs 推断)

#### 【问题 1：当前计划在哪里生成？】
- **事实（Fact）**：
  在当前主干生产代码中，存在两处互不连通的计划生成路径：
  1. `AgentOrchestrator.planAndSolve`（第 1147 行）：当且仅当 `planSolveConfig.isEnabled() == true` 且 `isComplexQuestion(question) == true` 且未超 Token 预算时，调用私有方法 `createPlan(question, systemPrompt, chatModel)`。通过一次性请求 ChatModel 生成包含 `taskId, objective, dependencies` 的 JSON 数组；
  2. `HierarchicalTaskPlanner.plan(userQuery)`（第 54 行）：位于 `tech.qiantong.qknow.hermes.agent.dag` 包，利用专门的 `PLANNER_SYSTEM_PROMPT` 将复合任务分解为 `List<DagTaskNode>`，并利用 Kahn 算法按入度为 0 逐层分阶段输出 `PhasedExecutionPlan`。
  3. `DefaultTreeOfThoughtsReActPlanner` 虽然实现了树搜索规划逻辑，但仅存在于测试用例及离线探针 `DaemonProbeDispatcher`、`HermesBenchmarkProbeRunner` 中。
- **推断（Inference）**：
  `AgentOrchestrator` 与 `HierarchicalTaskPlanner` 存在严重的工程割裂。`AgentOrchestrator` 未复用 `dag` 包成熟的分层 DAG 调度器，而是私自维护了一套极度简化、缺乏前置验证的私有 `createPlan` 方法。

#### 【问题 2：计划是否有显式结构？】
- **事实（Fact）**：
  1. `AgentOrchestrator` 内部采用私有结构：
     `private record PlanTask(String taskId, String objective, String worker, List<String> dependencies) {}`（只有 4 个基础字段，缺乏工具约束、预期输入输出契约、重试策略、状态机标记等）；
  2. `dag` 包中采用结构：
     `record DagTaskNode(String taskId, String objective, String requiredCapability, List<String> dependencies, int timeoutSeconds, Map<String, Object> metadata)`；
  3. 两者均**缺乏显式的步骤执行状态机**（如 READY, RUNNING, COMPLETED, FAILED, SKIPPED）、**前置条件校验（Pre-conditions）**与**后置可检验断言（Post-conditions / ExpectedArtifacts）**。

#### 【问题 3：计划是否被验证？】
- **事实（Fact）**：
  现有验证机制极其薄弱且单一：
  1. `AgentOrchestrator.createPlan` 中唯一的验证是 `hasDependencyCycle(tasks)`，若拓扑成环则直接清空并返回 `List.of()`；
  2. `HierarchicalTaskPlanner.buildPhasedPlan` 中唯一的验证是 Kahn 算法统计处理节点数，成环则抛出 `IllegalStateException`；
  3. **缺失关键验证能力**：
     - **工具与能力可用性验证（Capability Feasibility Check）**：完全不校验子任务所需的工具或能力在当前运行时环境中是否存在，若模型幻觉生成不存在的 worker/tool，后续调用必定报空或静默失败；
     - **语义覆盖率与脱靶验证（Semantic Coverage Validation）**：不校验拆解后的子任务是否完备覆盖原始问题意图，是否存在冗余或幻觉任务；
     - **资源与参数边界验证（Parameter & Context Budget Gate）**：未对子任务参数做模式校验，未对依赖上下文拼接膨胀做预计算。

#### 【问题 4：失败后是重试、重规划还是终止？】
- **事实（Fact）**：
  1. 在 `AgentOrchestrator.planAndSolve`（第 1156-1161 行）中：
     - 若 `hasFailedWorkerResult(workerResults)` 为 true，触发 `repairFailedPlan`；
     - **关键事实**：`repairFailedPlan` **既不重试失败步骤，也不重新生成计划图重新执行工具**，而是将所有失败信息连同已成功的文本直接喂给 ChatModel 进行一次**纯文本降级总结（Skip & Patch LLM Call）**！
     - 若 `planAndSolve` 抛出未捕获异常（第 1164-1167 行），直接返回 `null`，外层**无缝静默回退（Fallback）到普通的单智能体 ReAct 循环**；
  2. 在 `AgentOrchestrator.recoverReactFailure` 中，仅在 ReAct 彻底抛出异常时尝试一次性重新生成静态计划，若再次失败则输出安全提示词；
  3. 系统**完全缺乏带状态追踪的局域重规划（Local Replanning）**、**受控重试（Retry with mutated prompt）**与**有界自愈状态机**。

#### 【问题 5：工具选择是模型隐式决定，还是有显式约束？】
- **事实（Fact）**：
  系统在执行层具备较强的显式约束，但在规划层完全失控：
  1. **执行层显式约束**：
     - `ToolCallDispatch`（DFA 拦截器）：`ENFORCE` 模式下依据状态机严格拦截非法工具跃迁；
     - `ToolDiscoveryService`：基于阿里千问 1024 维超球面向量投影 Top-5 工具（动态 Safe Schema 绑定）；
     - `ReActCycleGuard`：步数与死循环限制；
  2. **规划层隐式放任**：
     - 在计划生成阶段（`createPlan` 与 `HierarchicalTaskPlanner`），模型生成 `worker` 或 `requiredCapability` 纯靠 Prompt 文本要求，没有任何 Schema 白名单与枚举约束，规划与执行底座的工具字典脱节。

#### 【问题 6：计划与执行结果是否可追踪？】
- **事实（Fact）**：
  1. 宏观层：`HarnessEvidenceRecord` 精确记录了请求级别的输入摘要、RAG 命中数、Token/Cost、工具耗时列表及 SHA-256 签名凭单；
  2. 微观层：**计划步骤粒度的认知痕迹严重缺失**。中间每个 `PlanTask` 的入参、下游依赖传递、执行耗时、失败真实异常、反思打分与重规划对比，全部存放在方法内部的局部变量 `Map<String, String> workerResults` 中，请求结束立即被 GC 销毁，未沉淀为持久化或可追溯的 `CognitiveTrace` 凭单。

#### 【问题 7：失败是否会被误判为成功？】
- **事实（Fact）**：
  **存在重大的结构性误判漏洞**：
  1. `AgentOrchestrator.hasFailedWorkerResult` 的判断逻辑为：
     `result.startsWith("执行失败:")`；
     - 若 WorkerAgent 中的大模型自行礼貌道歉（如“抱歉，数据库中没有查到该记录”），或工具返回了带有业务错误的 JSON（如 `{"code": 500, "msg": "API rate limit"}`），只要文本未以 `"执行失败:"` 开头，系统一律将其判定为**执行成功**！
     - 这些伪成功数据会直接流入 `aggregatePlanResults`，导致严重的错误级联放大与模型事实性幻觉；
  2. 计划后置反思 `reflectOnce` 中，若 `aiJudgeService == null`（生产默认未配时），直接透传答案，没有任何输出质量守卫。

#### 【问题 8：现有 Plan-and-Solve 与 ReAct 的边界？】
- **事实（Fact）**：
  1. 边界是**互斥且割裂的双轨制门禁**：在 `AgentOrchestrator.executeAgent` 内部，先根据 `planSolveConfig.isEnabled()` 与 `isComplexQuestion` 决定是否进入 `planAndSolve`；
  2. 若 `planAndSolve` 成功，直接通过 `emitSingleAnswer` 发送响应并 `return`，完全不流经后续的 `ReactAgent`；
  3. 只有当 `planSolveConfig.isEnabled() == false`、问题不满足复杂判定条件，或 `planAndSolve` 运行抛异常/返回 null 时，才会进入单智能体 `ReactAgent` 流式循环；
  4. 生产默认状态：`planSolveConfig.enabled = false`，意味着当前生产主路径 100% 运行在单线 ReAct 之上。

#### 【问题 9：哪些逻辑可以复用，哪些必须新增？】
- **事实与推断（Fact & Inference）**：
  - **核心复用资产**：
    1. `HierarchicalTaskPlanner` 中的 Kahn 拓扑分层分阶段调度算法；
    2. `WorkerAgent` 的多拦截器、串行/并发执行和向量意图投影机制；
    3. `ToolCallDispatch` 的 DFA 拦截与状态机防时序侧信道签名；
    4. `EvaluatorOptimizerGovernor` 的双环质检打分范式；
    5. `CognitiveSelfHealingStateMachine` 的反思死锁检测与反事实变异纠偏算法（需将维度从历史 1536 统一为千问 1024 维）；
    6. `HarnessEvidenceRecord` / `HarnessEvidenceCollector` 不可变存证流水线；
    7. `ExperienceLibrary` 的经验召回与沉淀协议（为 Stage 22 预留插槽）。
  - **必须新增组件**：
    1. 强类型纯 Java 21 Record 认知核心契约（`TaskAnalysis`, `Plan`, `PlanStep`, `PlanVerificationResult`, `StepResult`, `ReflectionResult`, `CognitiveTrace`）；
    2. 形式化计划前置验证器（`PlanValidator`），提供工具契约、图拓扑、语义覆盖多重门禁；
    3. 步进式波次执行引擎（`CognitiveWaveExecutor`），精准捕获结构化失败并阻断级联错误；
    4. 自适应反思与局域重规划调度器（`AdaptiveCognitiveReplanner`），支持有界反事实纠偏与动态插拔重规划；
    5. 细粒度认知轨迹记录器（`CognitiveTraceRecorder`）。

#### 【问题 10：最小认知循环应该接在哪一层？】
- **事实与推断（Fact & Inference）**：
  - **接入层定位**：必须作为 `AgentOrchestrator` 与底层执行器（`ReactAgent` / `WorkerAgent`）之间的**认知编排核心层（Cognitive Core Engine）**；
  - **无缝集成接口**：在 `AgentOrchestrator.executeAgent` 内部，通过抽象接口 `CognitiveOrchestratorEngine` 替代原有脆弱私有的 `planAndSolve` 分支；
  - **契约设计**：当任务满足认知处理条件时由引擎接管，统一流式产出思考/执行/反思状态事件，并向 `HarnessEvidenceCollector` 注入完整执行痕迹；当任务为简单单步检索或发生未决熔断时，干净降级回退到单步轻量 ReAct。

---

### 1.3 现有认知与编排代码拓扑断层剖析

综合上述法医审计，现有系统在认知编排上存在三大致命断层：
1. **规划与验证脱节（Plan-without-Verification Gap）**：计划由大模型自由发散生成，缺乏环境工具强校验与预期契约，导致错误在执行早期无法发现；
2. **执行与判定脱节（Execution-without-Semantic-Gate Gap）**：将字符串 `startsWith("执行失败:")` 作为唯一判定标准，致使真实语义错误与工具异常被当作正确事实级联传递；
3. **反思与重规划脱节（Reflection-without-Replanning Gap）**：现存反思仅在聚合后做一次问答修饰（`reflectOnce`），执行失败时仅做跳过摘要（`repairFailedPlan`），缺失步骤级的语言化反思与拓扑动态修正。

---

## 二、学术前沿与工业界架构深度调研 (Research Ledger)

### 2.1 六大核心范式对比与适用性定性

依据 AGENTS.md §2.2 铁律，规划员对 6 大代表性学术成果与工业架构进行了逐项深度研读与法医级核验，其对本项目的适用性定性如下：

| 范式 / 体系 | 核心代表文献 / 仓库 | 核心机制与理论贡献 | 本项目适用性定性 | 采用/改造/拒绝理由 |
| :--- | :--- | :--- | :--- | :--- |
| **ReAct** | Yao et al., ICLR 2023 (arXiv:2210.03629) | Thought-Action-Observation 紧密交织的流式单步推理 | **可直接复用** | 作为底层子任务执行器（WorkerAgent）与简单原子任务的兜底 Baseline，长程任务拒绝单线使用 |
| **Plan-and-Solve** | Wang et al., ACL 2023 (arXiv:2305.04091) | 先全局拆解多步子任务，再顺序执行子任务 | **需要改造** | 静态一次性拆解容易在执行过程中脱靶，需改造为有向无环图拓扑波次执行与动态状态追踪 |
| **Reflexion** | Shinn et al., NeurIPS 2023 (arXiv:2303.11366) | 基于语言反馈（Verbal Feedback）的语言强化学习与反思记忆 | **需要改造** | 核心借鉴其语言化反思、短期情景记忆与自愈纠偏，需与本项目的超球面死锁检测深度结合 |
| **LLM Compiler** | Kim et al., ICML 2024 (arXiv:2312.04511) | 借鉴经典编译器思想，生成中间表示 (IR)，并行调度独立工具调用 | **需要改造** | 借鉴其有向图依赖流转、波次并行调度与跨任务变量传递，改造为 Java 21 CompletableFuture 并发流 |
| **Tree of Thoughts (ToT)** | Yao et al., NeurIPS 2023 (arXiv:2305.10601) | 维护思维树，通过广度/深度/MCTS 搜索与回溯剪枝解决复杂规划 | Stage 21A 不纳入 | 多分支的实际 Token、延迟与工具副作用风险尚未在本项目测量；不作为当前候选或事实结论 |
| **LangGraph / AutoGen** | LangGraph Plan-and-Execute Architecture (2024) | 基于显式状态图 (StateGraph) 的 Plan-Execute-Replan 动态循环 | **需要改造** | 借鉴其显式状态机、不可变 State 与条件边缘路由设计，采用 Java 21 Record 强类型重构 |

---

### 2.2 严格遵守 14 字段规范的 Research Ledger

```text
id: RL-2026-STAGE21-001
sourceType: paper
titleOrRepository: ReAct: Synergizing Reasoning and Acting in Language Models
authorsOrMaintainer: Shunyu Yao, Jeffrey Zhao, Dian Yu, Nan Du, Izhak Shafran, Karthik Narasimhan, Yuan Cao
venueAndYear: ICLR 2023
doiOrArxiv: arXiv:2210.03629
url: https://arxiv.org/abs/2210.03629
commitOrTag: N/A
license: CC BY 4.0
filesOrSectionsRead: Sections 1-4, Section 5 (Knowledge-intensive reasoning), Section 6 (Interactive decision making)
verificationStatus: VERIFIED
relevantFinding: 论文证明将大模型的离散推理痕迹（Thought）与外部环境交互动作（Action/Observation）交织，可显著降低幻觉并增强决策解释性；在 HotpotQA 和 ALFWorld 上相比纯 CoT 具备明显优势。但在复杂长程任务中，ReAct 容易陷入局部贪心、死循环和上下文膨胀。
projectApplicability: 可直接复用作为底层单个子任务节点（PlanStep）的执行引擎（即 WorkerAgent 内嵌的 ReactAgent）。但在顶层编排上必须受到全局规划器与验证器的显式状态约束。
limitations: 论文未涉及多步任务的全局依赖解耦、并行波次执行以及结构化重规划机制。
```

```text
id: RL-2026-STAGE21-002
sourceType: paper
titleOrRepository: Plan-and-Solve Prompting: Improving Zero-Shot Chain-of-Thought Reasoning by Large Language Models
authorsOrMaintainer: Lei Wang, Wanyu Xu, Yihuai Lan, Zhiqiang Hu, Yunshi Lan, Roy Ka-Wei Lee, Ee-Peng Lim
venueAndYear: ACL 2023
doiOrArxiv: arXiv:2305.04091
url: https://arxiv.org/abs/2305.04091
commitOrTag: N/A
license: CC BY-NC-SA 4.0
filesOrSectionsRead: Sections 1-3 (Zero-shot PS & PS+), Section 4 (Experiments on Math and Multi-step Reasoning)
verificationStatus: VERIFIED
relevantFinding: 提出 Plan-and-Solve (PS) 范式，通过引导大模型在执行前先生成全局计划清单，克服了传统零样本 CoT 容易漏步、计算跳跃和语义脱靶的缺陷；扩展版 PS+ 进一步引入了抽取变量与中间计算验证。
projectApplicability: 需要改造。本项目现有 AgentOrchestrator 借鉴了其朴素思想，但原生 PS 为静态一次性执行，缺乏中途失败自愈与依赖图并发调度。需将其升级为具备验证与反思闭环的动态拓扑执行机制。
limitations: 原论文侧重于数学与问答数据集的单轮提示词，未涉及带有外部 Tool Calling 环境的异常反馈与自适应局部修正。
```

```text
id: RL-2026-STAGE21-003
sourceType: paper
titleOrRepository: Reflexion: Language Agents with Verbal Reinforcement Learning
authorsOrMaintainer: Noah Shinn, Federico Cassano, Edward Berman, Ashwin Gopinath, Karthik Narasimhan, Shunyu Yao
venueAndYear: NeurIPS 2023
doiOrArxiv: arXiv:2303.11366
url: https://arxiv.org/abs/2303.11366
commitOrTag: N/A
license: MIT
filesOrSectionsRead: Section 2 (Reflexion Framework: Actor, Evaluator, Self-Reflection), Section 3 (Memory), Section 4 (Experiments)
verificationStatus: VERIFIED
relevantFinding: 提出了基于语言强化学习的反思智能体架构，通过 Evaluator 评估任务反馈并生成语言化批评（Verbal Reflection），缓存在短期情景记忆中指导下一轮试错，在 HumanEval 上将 Pass@1 从 80% 提升至 91%。
projectApplicability: 需要改造。本项目必须引入其 Evaluator 与 Self-Reflection 机制，对每个子步骤的产物和全图产物进行双环质检；同时需结合本项目独有的超球面向量余弦死锁检测，杜绝重复无效反思。
limitations: 若缺乏反思终止上限或反事实变异算子，连续多轮语言反思极易陷入语义固着（Semantic Fixation）循环。
```

```text
id: RL-2026-STAGE21-004
sourceType: paper
titleOrRepository: An LLM Compiler for Parallel Function Calling
authorsOrMaintainer: Sehoon Kim, Suhong Moon, Ryan Tabrizi, Nicholas Lee, Michael W. Mahoney, Kurt Keutzer, Amir Gholami
venueAndYear: ICML 2024
doiOrArxiv: arXiv:2312.04511
url: https://arxiv.org/abs/2312.04511
commitOrTag: N/A
license: Apache-2.0
filesOrSectionsRead: Section 1-3 (LLMCompiler Architecture: Planner, Task Fetching Unit, Executor), Section 4 (Evaluation)
verificationStatus: VERIFIED
relevantFinding: 受经典编译优化启发，LLMCompiler 通过解析子任务依赖生成有向无环图（DAG），利用任务获取单元（Task Fetching Unit）识别入度为 0 的任务波次，实现工具调用最大并行化，取得高达 3.7x 的延迟加速比和 6.7x 的成本缩减。
projectApplicability: 需要改造。直接适配本项目 `HierarchicalTaskPlanner` 的 Kahn 拓扑分层思想，在 Java 21 中构建以不可变 Record 为数据契约的波次调度器，提升无依赖工具任务的并发执行吞吐。
limitations: 编译器模式偏向确定性 DAG 预编译，在任务执行中途因工具输出未知而产生动态分支重写时，其处理机制较重。
```

```text
id: RL-2026-STAGE21-005
sourceType: paper
titleOrRepository: Tree of Thoughts: Deliberate Problem Solving with Large Language Models
authorsOrMaintainer: Shunyu Yao, Dian Yu, Jeffrey Zhao, Izhak Shafran, Thomas L. Griffiths, Yuan Cao, Karthik Narasimhan
venueAndYear: NeurIPS 2023
doiOrArxiv: arXiv:2305.10601
url: https://arxiv.org/abs/2305.10601
commitOrTag: N/A
license: MIT
filesOrSectionsRead: Section 2 (Tree of Thoughts Framework), Section 3 (Experiments: Game of 24, Creative Writing, Mini Crosswords)
verificationStatus: VERIFIED
relevantFinding: 突破线性思维链限制，维护多分支思维树，通过启发式评估函数与前瞻搜索（BFS / DFS / MCTS）实现主动探索与剪枝回溯，在 24 点等复杂探索性难题上表现优异。
projectApplicability: 应拒绝（作为通用在线推理主架构拒绝）。本项目实测与论文均表明，ToT 每次决策需生成 $k$ 个候选分支并打分，多步任务导致 LLM 调用量暴增数十倍，通用交互场景延迟超 10 秒，生产不可接受。仅保留离线探针用途。
limitations: 搜索空间庞大时分支爆炸，极度依赖高质量价值评估函数，在确定性工程工具调用中性价比极低。
```

```text
id: RL-2026-STAGE21-006
sourceType: production-implementation
titleOrRepository: langchain-ai/langgraph (Plan-and-Execute Architecture Pattern)
authorsOrMaintainer: LangChain AI
venueAndYear: Production Documentation 2024
doiOrArxiv: N/A
url: https://github.com/langchain-ai/langgraph
commitOrTag: commit:latest-stable
license: MIT
filesOrSectionsRead: docs/how-tos/plan-and-execute.ipynb, core state graph scheduling, replanner conditional edges
verificationStatus: VERIFIED
relevantFinding: 工业界最成熟的计划执行模式：利用显式 StateGraph 将状态划分为 input, plan, past_steps, response；Planner 节点生成计划，Executor 节点取出队首执行，Replanner 节点根据 past_steps 动态决定是否重构剩余 plan 或终止。
projectApplicability: 需要改造。吸收其“显式状态机、不可变全局状态驱动、步骤级条件边缘流转”的思想，在本项目 Java 21 体系下用纯强类型 Record、函数式接口与无锁并发状态转移实现。
limitations: Python 动态语言实现缺乏编译期强类型安全，缺乏形式化验证器门禁，易受下游工具脏数据污染。
```

---

## 三、候选架构方案深度比较与裁决

### 3.1 候选架构方案定义

- **方案 A：保持 ReAct Baseline**
  不引入显式任务分解与计划图，所有问题全量依赖单 Agent 通过系统提示词与 Function Calling 逐步试错、局部决策。
- **方案 B：现有 Plan-and-Solve 局部增强**
  在现有 `AgentOrchestrator.planAndSolve` 私有逻辑上做修修补补，仅调整 Prompt 提示词或增加关键词判定，维持原有一次性全量分发与 `repairFailedPlan` 纯文本修补逻辑。
- **方案 C：完整 PVER 路线图候选（非 Stage 21A 实施基线）**
  作为后续研究方向，可能包含分析、计划校验、执行、反思，以及经单独验证后的并发或重规划扩展。Stage 21A 仅取其中的顺序最小子集；本方案不是已验证有效的整体架构。
- **方案 D：树搜索 / 多计划搜索 (MCTS + ToT 全面替换)**
  全面废弃线性执行，将主生产路径完全切换为 `DefaultTreeOfThoughtsReActPlanner` 的蒙特卡洛树搜索与前瞻状态模拟。

---

### 3.2 九维全景对比矩阵

> 下表是未测量的设计假设，不是实测排名或效果结论。Stage 21A 只验证顺序执行与结构契约；成功率、恢复率、延迟、兼容性和回退行为均待测。

| 评估维度 | 方案 A：ReAct Baseline | 方案 B：现有 Plan-and-Solve 增强 | 方案 C：完整 PVER 路线图候选 | 方案 D：ToT / MCTS 树搜索 |
| :--- | :--- | :--- | :--- | :--- |
| **1. 多步任务成功率** | baseline，待测 | 待测 | 待测；21A 不做效果优越性判断 | 待测 |
| **2. 计划合法性** | 不适用或按现有路径记录 | 按现有实现复核 | 可测试规则拒绝行为；总体合法率待测 | 待测 |
| **3. 工具选择正确率** | 待测 | 待测 | 待测；Capability 约束仍属设计假设 | 待测 |
| **4. 反思修复率** | 记录现状 | 待测 | 21A 只记录反思，不承诺自动恢复 | 待测 |
| **5. Token / 延迟** | 待测 | 待测 | 待测；21A 不含波次并发收益主张 | 待测 |
| **6. 实现复杂度** | 现有路径 | 需按当前代码复核 | 设计估计，实施前复核 | 设计估计 |
| **7. 可解释性与可审计性** | 现有 trace 能力待核验 | 现有 trace 能力待核验 | trace 契约可测试，审计效用待评估 | 待评估 |
| **8. 容灾回退能力** | 按现有行为记录 | 按现有行为记录 | 回退契约尚待设计与验证，不作保证 | 待评估 |
| **9. 现有架构兼容性** | 当前对照路径 | 待源码复核 | opt-in 边界候选，影响范围待实施前复核 | 待评估 |

---

### 3.3 Stage 21A 范围裁决（非实证排名）

1. Stage 21A 仅规划顺序的 `TaskAnalysis → Plan → PlanVerification → Sequential Execution → Reflection` 子集；它是待实施、待验证的架构候选，不代表相对 ReAct 或其他方案已取得效果优势。
2. 方案 A（ReAct）继续作为 baseline/对照与既有回退路径，不予否决。方案 B、并发/重规划扩展及 ToT/MCTS 均不纳入 Stage 21A；未来是否研究须结合 Stage 23A 的测量和独立任务书决定。
3. 本文没有验证“第 3 步后失焦”“误判率不可接受”“15–30s”或方案 C 的“顶刊原创性”等效果性说法；此类旧表述不作为事实、验收标准或实施理由。

---

## 四、Stage 21: Cognitive Core v1 架构设计与数据契约草案

### 4.1 认知状态机 (FSM) 形式化建模与数学定义

> **Stage 21A 收敛规则**：本节完整 FSM 中的 `REPLANNING`、重规划计数与复杂 fallback 属于后续路线图。Stage 21A 只实现分析、规划、验证、顺序执行、反思和终止/失败状态；任何自动重规划循环须另行冻结预算、停止条件和验收契约。

定义认知核心引擎为八元状态机元组：
$$\mathcal{M}_{cog} = \langle \mathcal{S}, \Sigma, \delta, s_0, \mathcal{F}, \mathcal{C}, \mathcal{K}, \mathcal{B} \rangle$$

其中：
1. **状态集合 $\mathcal{S}$**：
   $$\mathcal{S} = \{ \text{ANALYZING}, \text{PLANNING}, \text{VERIFYING}, \text{EXECUTING}, \text{REFLECTING}, \text{REPLANNING}, \text{COMPLETED}, \text{FAILED\_FALLBACK} \}$$
2. **输入事件集 $\Sigma$**：
   $$\Sigma = \{ e_{req}, e_{analyzed}, e_{plan\_gen}, e_{verify\_ok}, e_{verify\_fail}, e_{step\_ok}, e_{step\_fail}, e_{eval\_pass}, e_{eval\_fail}, e_{replan\_ok}, e_{replan\_exceeded}, e_{budget\_exhausted} \}$$
3. **初态 $s_0 = \text{ANALYZING}$**；
4. **终态集合 $\mathcal{F} = \{ \text{COMPLETED}, \text{FAILED\_FALLBACK} \}$**；
5. **上下文配置 $\mathcal{C}$**：包含输入请求、可用工具白名单、会话凭单等不可变环境；
6. **重规划计数器 $\mathcal{K} \in [0, K_{max}]$**（系统硬性上界 $K_{max} = 2$）；
7. **预算控制器 $\mathcal{B}$**：监控累计消耗 Token 与执行时延，超出阈值立即强转移至 $\text{FAILED\_FALLBACK}$。

#### 状态转移函数 $\delta: \mathcal{S} \times \Sigma \to \mathcal{S}$ 规范表：
- $(\text{ANALYZING}, e_{analyzed}) \to \text{PLANNING}$
- $(\text{PLANNING}, e_{plan\_gen}) \to \text{VERIFYING}$
- $(\text{VERIFYING}, e_{verify\_ok}) \to \text{EXECUTING}$
- $(\text{VERIFYING}, e_{verify\_fail}) \to \begin{cases} \text{PLANNING}, & \text{若 } \mathcal{K} < K_{max} \\ \text{FAILED\_FALLBACK}, & \text{若 } \mathcal{K} \ge K_{max} \end{cases}$
- $(\text{EXECUTING}, e_{step\_ok}) \to \text{REFLECTING}$
- $(\text{EXECUTING}, e_{step\_fail}) \to \begin{cases} \text{REPLANNING}, & \text{若 } \mathcal{K} < K_{max} \\ \text{FAILED\_FALLBACK}, & \text{若 } \mathcal{K} \ge K_{max} \end{cases}$
- $(\text{REFLECTING}, e_{eval\_pass}) \to \begin{cases} \text{EXECUTING}, & \text{若存在待执行剩余步骤} \\ \text{COMPLETED}, & \text{全图步骤均已完成} \end{cases}$
- $(\text{REFLECTING}, e_{eval\_fail}) \to \begin{cases} \text{REPLANNING}, & \text{若 } \mathcal{K} < K_{max} \\ \text{FAILED\_FALLBACK}, & \text{若 } \mathcal{K} \ge K_{max} \end{cases}$
- $(\text{REPLANNING}, e_{replan\_ok}) \to \text{VERIFYING}$ (重规划计数器自增 $\mathcal{K} \leftarrow \mathcal{K} + 1$)
- $(\forall s \in \mathcal{S}, e_{budget\_exhausted}) \to \text{FAILED\_FALLBACK}$ (预算强行熔断)

---

### 4.2 核心数据结构契约草案 (纯 Java 21 Record)

所有核心契约类统一置于新建包路径：
`tech.qiantong.qknow.hermes.cognitive.core.model.*`
全面采用 Java 21 Record、强类型枚举与不可变容器实现零时序侧信道防泄漏。

```java
package tech.qiantong.qknow.hermes.cognitive.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 1. 任务深度意图分析契约 (TaskAnalysis)
 */
public record TaskAnalysis(
        String analysisId,
        String originalQuestion,
        boolean requiresMultiStep,
        TaskComplexity complexity,
        Set<String> detectedIntents,
        List<String> requiredCapabilities,
        Map<String, Object> contextualConstraints,
        Instant analyzedAt
) {
    public enum TaskComplexity {
        SIMPLE_DIRECT,
        MULTI_HOP_REASONING,
        PARALLEL_COMPARATIVE,
        LONG_HORIZON_SYNTHESIS
    }

    public TaskAnalysis {
        Objects.requireNonNull(analysisId, "analysisId");
        Objects.requireNonNull(originalQuestion, "originalQuestion");
        detectedIntents = detectedIntents != null ? Set.copyOf(detectedIntents) : Set.of();
        requiredCapabilities = requiredCapabilities != null ? List.copyOf(requiredCapabilities) : List.of();
        contextualConstraints = contextualConstraints != null ? Map.copyOf(contextualConstraints) : Map.of();
        analyzedAt = analyzedAt != null ? analyzedAt : Instant.now();
    }
}
```

```java
package tech.qiantong.qknow.hermes.cognitive.core.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 2. 结构化子任务计划步骤契约 (PlanStep)
 */
public record PlanStep(
        String stepId,
        int sequenceIndex,
        String objective,
        String requiredCapability,
        String designatedToolName,
        List<String> dependencies,
        Map<String, String> inputMapping,
        String expectedArtifactName,
        int timeoutSeconds,
        StepStatus status
) {
    public enum StepStatus {
        PENDING,
        READY,
        RUNNING,
        COMPLETED,
        FAILED,
        SKIPPED
    }

    public PlanStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(objective, "objective");
        requiredCapability = requiredCapability != null ? requiredCapability : "GENERAL";
        dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
        inputMapping = inputMapping != null ? Map.copyOf(inputMapping) : Map.of();
        expectedArtifactName = expectedArtifactName != null ? expectedArtifactName : "";
        timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        status = status != null ? status : StepStatus.PENDING;
    }

    public PlanStep withStatus(StepStatus newStatus) {
        return new PlanStep(stepId, sequenceIndex, objective, requiredCapability, designatedToolName,
                dependencies, inputMapping, expectedArtifactName, timeoutSeconds, newStatus);
    }
}
```

```java
package tech.qiantong.qknow.hermes.cognitive.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 3. 完整拓扑分层执行计划契约 (Plan)
 */
public record Plan(
        String planId,
        String analysisId,
        String rootObjective,
        int revisionNumber,
        List<PlanStep> allSteps,
        List<List<String>> phasedWaveStepIds, // 按 Kahn 算法拓扑排序的并发波次
        Map<String, Object> metadata,
        Instant createdAt
) {
    public Plan {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(rootObjective, "rootObjective");
        allSteps = allSteps != null ? List.copyOf(allSteps) : List.of();
        phasedWaveStepIds = phasedWaveStepIds != null ? List.copyOf(phasedWaveStepIds) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
        revisionNumber = Math.max(0, revisionNumber);
    }

    public boolean isEmpty() {
        return allSteps.isEmpty();
    }
}
```

```java
package tech.qiantong.qknow.hermes.cognitive.core.model;

import java.util.List;
import java.util.Objects;

/**
 * 4. 计划前置形式化验证结果契约 (PlanVerificationResult)
 */
public record PlanVerificationResult(
        boolean valid,
        String planId,
        List<VerificationIssue> issues,
        String verificationFingerprint
) {
    public record VerificationIssue(
            IssueSeverity severity,
            String stepId,
            String ruleCode,
            String message
    ) {
        public enum IssueSeverity {
            WARNING,
            FATAL_ERROR
        }
    }

    public PlanVerificationResult {
        Objects.requireNonNull(planId, "planId");
        issues = issues != null ? List.copyOf(issues) : List.of();
        verificationFingerprint = verificationFingerprint != null ? verificationFingerprint : "";
    }

    public static PlanVerificationResult pass(String planId) {
        return new PlanVerificationResult(true, planId, List.of(), "PASS");
    }

    public static PlanVerificationResult reject(String planId, List<VerificationIssue> issues) {
        return new PlanVerificationResult(false, planId, issues, "REJECT");
    }
}
```

```java
package tech.qiantong.qknow.hermes.cognitive.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 5. 单个步骤物理执行产物与度量契约 (StepResult)
 */
public record StepResult(
        String stepId,
        boolean success,
        String rawOutput,
        String structuredArtifactJson,
        long durationMs,
        long promptTokens,
        long completionTokens,
        String failureReason,
        Map<String, String> exportedVariables,
        Instant finishedAt
) {
    public StepResult {
        Objects.requireNonNull(stepId, "stepId");
        rawOutput = rawOutput != null ? rawOutput : "";
        structuredArtifactJson = structuredArtifactJson != null ? structuredArtifactJson : "{}";
        failureReason = failureReason != null ? failureReason : "";
        exportedVariables = exportedVariables != null ? Map.copyOf(exportedVariables) : Map.of();
        finishedAt = finishedAt != null ? finishedAt : Instant.now();
    }

    public static StepResult failed(String stepId, String reason, long durationMs) {
        return new StepResult(stepId, false, "", "{}", durationMs, 0L, 0L, reason, Map.of(), Instant.now());
    }
}
```

```java
package tech.qiantong.qknow.hermes.cognitive.core.model;

import java.util.List;
import java.util.Objects;

/**
 * 6. 双环质量评估与反思结果契约 (ReflectionResult)
 */
public record ReflectionResult(
        String reflectionId,
        String stepId,
        boolean passed,
        double qualityScore,
        List<String> critiquePoints,
        String counterfactualGuidance, // 反事实变异引导指令
        boolean semanticDeadlockDetected, // 是否命中 1024 维超球面死锁
        ActionDecision recommendedAction
) {
    public enum ActionDecision {
        PROCEED_NEXT,
        RETRY_LOCAL_STEP,
        REPLAN_REMAINING_GRAPH,
        ABORT_TO_FALLBACK
    }

    public ReflectionResult {
        Objects.requireNonNull(reflectionId, "reflectionId");
        critiquePoints = critiquePoints != null ? List.copyOf(critiquePoints) : List.of();
        counterfactualGuidance = counterfactualGuidance != null ? counterfactualGuidance : "";
        recommendedAction = recommendedAction != null ? recommendedAction : ActionDecision.PROCEED_NEXT;
    }
}
```

```java
package tech.qiantong.qknow.hermes.cognitive.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 7. 全流程认知执行不可变痕迹凭单契约 (CognitiveTrace)
 */
public record CognitiveTrace(
        String traceId,
        String requestId,
        TaskAnalysis taskAnalysis,
        List<Plan> planHistory, // 包含初始规划与历次重规划版本
        Map<String, StepResult> stepExecutionReceipts,
        List<ReflectionResult> reflectionReceipts,
        int totalReplans,
        boolean completedSuccessfully,
        String finalAggregatedOutput,
        long totalDurationMs,
        long totalTokensConsumed,
        double totalCostCny,
        Instant timestamp,
        String cryptographicSignature // SHA-256 签名，防篡改
) {
    public CognitiveTrace {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(requestId, "requestId");
        planHistory = planHistory != null ? List.copyOf(planHistory) : List.of();
        stepExecutionReceipts = stepExecutionReceipts != null ? Map.copyOf(stepExecutionReceipts) : Map.of();
        reflectionReceipts = reflectionReceipts != null ? List.copyOf(reflectionReceipts) : List.of();
        timestamp = timestamp != null ? timestamp : Instant.now();
        cryptographicSignature = cryptographicSignature != null ? cryptographicSignature : "";
    }
}
```

---

### 4.3 核心组件架构与契约接口设计

构建三大核心引擎接口，职责高度解耦：

```java
package tech.qiantong.qknow.hermes.cognitive.core.engine;

import tech.qiantong.qknow.hermes.cognitive.core.model.*;
import java.util.List;
import java.util.Set;

/**
 * 认知规划与验证契约
 */
public interface CognitivePlanner {
    TaskAnalysis analyzeTask(String userPrompt, Set<String> availableToolNames);
    Plan generatePlan(TaskAnalysis analysis, Set<String> availableToolNames);
    PlanVerificationResult verifyPlan(Plan plan, Set<String> availableToolNames);
    Plan replan(Plan currentPlan, StepResult failedStep, ReflectionResult reflection, Set<String> availableToolNames);
}
```

```java
package tech.qiantong.qknow.hermes.cognitive.core.engine;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction.ToolCallDispatch;
import tech.qiantong.qknow.hermes.cognitive.core.model.PlanStep;
import tech.qiantong.qknow.hermes.cognitive.core.model.StepResult;
import java.util.List;
import java.util.Map;

/**
 * 步骤波次执行契约
 */
public interface CognitiveStepExecutor {
    StepResult executeStep(
            PlanStep step,
            Map<String, String> accumulatedContext,
            List<ToolCallback> tools,
            ToolCallbackResolver resolver,
            ToolCallDispatch toolCallDispatch
    );
}
```

```java
package tech.qiantong.qknow.hermes.cognitive.core.engine;

import tech.qiantong.qknow.hermes.cognitive.core.model.PlanStep;
import tech.qiantong.qknow.hermes.cognitive.core.model.ReflectionResult;
import tech.qiantong.qknow.hermes.cognitive.core.model.StepResult;

/**
 * 认知自省与双环质量裁决契约
 */
public interface CognitiveReflector {
    ReflectionResult reflectOnStep(
            PlanStep step,
            StepResult result,
            String rootObjective,
            float[] stepOutputEmbedding, // 1024 维超球面向量
            float[] previousStepEmbedding
    );
}
```

---

### 4.4 失败自愈、重规划防环与资源熔断机制

1. **形式化前置验证四重门禁 (PlanValidator Guardrails)**：
   - **门禁 1（DAG 拓扑无环与连通性）**：严格使用 Kahn 算法计算入度，出现循环依赖或孤立悬空依赖立即标记 `FATAL_ERROR`；
   - **门禁 2（Capability/Tool 显式映射）**：每个 Step 声明的 `requiredCapability` 或 `designatedToolName` 必须在当前已启用的 `availableToolNames` 集合中存在映射，严禁未定义工具通过；
   - **门禁 3（预期产物声明）**：非终止步骤必须显式声明非空的 `expectedArtifactName`，杜绝无产物盲目流转；
   - **门禁 4（步数与复杂度限制）**：子任务数必须满足 $2 \le N \le 6$。

2. **自适应失败处理与防环算法 (Anti-Loop Replanning Guard)**：
   - 设定最大重规划次数 $K_{max} = 2$；
   - **状态指纹去重（Plan State Fingerprint）**：为每个重规划生成的 Plan 计算规范化 SHA-256 签名，若新生成的 Plan 与历史 `planHistory` 中的任意版本签名一致，判定为重规划停滞死循环，立即中止并触发 $e_{replan\_exceeded}$，强制平滑降级；
   - **1024 维超球面反思死锁打破（Hyperspherical Deadlock Breaking）**：
     当连续两轮反思失败且 step 输出向量在 $\mathbb{S}^{1023}$ 上的内积 $\cos\theta = \mathbf{u} \cdot \mathbf{v} \ge 0.90$ 时，触发反事实变异指令注入（强制颠覆上一轮前置假设，施加负向惩罚）。

3. **三层预算熔断守卫 (Budget Guardrails)**：
   - **Token 预算门禁**：在每波执行前预估累计 Token，超过 `planSolveConfig.getMaxTokenBudget()` 立即熔断；
   - **单步超时门禁**：每个 Step 独立倒计时（默认 30s），超时使用 `CompletableFuture.orTimeout` 优雅中断；
   - **全局执行时延门禁**：单次请求认知全流程总耗时不得超过 60s。

---

### 4.5 与 AgentOrchestrator 的无缝集成方案

为实现零破坏向后兼容，在 `AgentOrchestrator` 中引入 `CognitiveOrchestratorEngine` 契约，并通过依赖注入或默认构造接入：

```java
// 架构改造点定位：AgentOrchestrator.java
// 彻底废除原有的私有 planAndSolve(...) 方法，重构为结构化认知核心委托：

private String executeCognitiveOrchestration(
        ChatRequest request,
        String systemPrompt,
        List<ToolCallback> bridgeTools,
        ToolCallbackResolver effectiveResolver,
        ChatModel chatModel,
        ToolCallDispatch toolCallDispatch,
        HarnessEvidenceCollector evidenceCollector
) {
    if (cognitiveEngine == null || !cognitiveEngine.shouldHandle(request.getQuestion())) {
        return null; // 平滑透传给原 ReAct 主干
    }

    // 执行 PVER 认知闭环
    CognitiveExecutionReceipt receipt = cognitiveEngine.execute(
            request, systemPrompt, bridgeTools, effectiveResolver, chatModel, toolCallDispatch
    );

    // 将细粒度认知痕迹注入全局 HarnessEvidenceRecord
    evidenceCollector.recordCognitiveTrace(receipt.cognitiveTrace());

    if (receipt.isSuccessful()) {
        return receipt.finalAnswer();
    }

    // 若认知引擎最终判定无法完成或达到重规划上限，返回 null，由外层无缝回退到 ReAct 单智能体
    log.warn("Cognitive engine degraded to ReAct fallback: requestId={}, reason={}",
            request.getRequestId(), receipt.failureCode());
    return null;
}
```

---

## 五、核心契约验证计划与指标评估体系

### 5.1 纯本地确定性测试验证矩阵

全面落实 AGENTS.md §2.1 与 Java 21 隔离环境约束，全部验证通过本地隔离 JDK (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`) 运行，严禁调用真实外网付费 API：

1. **测试用例 1：`TaskAnalysisContractTest`**
   - 验证目标：复杂对比任务（带“对比/区别”关键词与多问号）准确识别为 `PARALLEL_COMPARATIVE`，简单问答识别为 `SIMPLE_DIRECT`。
2. **测试用例 2：`PlanValidatorRuleEngineTest`**
   - 验证目标：构造的成环依赖图、未注册工具调用和缺失预期产物样例均被 `PlanValidator` 拦截并产出格式化 `FATAL_ERROR` 议题；不将这些样例外推为总体拦截率。
3. **测试用例 3：`SequentialPlanExecutionTest`**
   - 验证目标：按合法计划顺序执行依赖步骤，前一步失败时不启动后续依赖步骤；不启动并行 wave。
4. **测试用例 4：`CognitiveReflectionContractTest`**
   - 验证目标：Reflection 根据结构化 StepResult 生成有界、可复算的反思结果；本阶段不触发自动重规划或余弦死锁检测。
5. **测试用例 5：`CognitiveTraceSerializationAndSignatureTest`**
   - 验证目标：验证不可变 Record `CognitiveTrace` 经过 SHA-256 签名，且在修改任何字段后验签均返回 false。

---

### 5.2 阶段验收基准与关键评价指标 (KPI)

| 评价指标 | Baseline | Stage 21A 目标/门禁 | 验证方法 / 口径 |
| :--- | :--- | :--- | :--- |
| **多步复合任务成功率 (Pass@1)** | 待测 | 待测；Stage 21A 不设提升目标 | Stage 23A 冻结的小规模金标任务集 |
| **计划合法性** | 待测 | 结构契约：非法计划按规则拒绝；总体合法率待测 | 确定性 PlanValidator 合约用例 + Stage 23A 过程 trace |
| **工具选择准确率** | 待测 | 待测/待预注册 | Stage 23A 对冻结任务和工具 oracle 逐题复核 |
| **失败自愈/反思修复率** | 待测 | 待测/待预注册；Stage 21A 不要求自动重规划 | 仅报告反思触发与结果，不将 mock 流程通过率等同真实恢复率 |
| **误判率 (False Success Rate)** | 待测 | 待测/待预注册；不沿用旧 `~30% → ≤2%` 数值 | 结构化 StepResult 与 evaluator 的离线复算 |
| **Token 与延迟** | 待测 | 待测；Stage 21A 无 wave 并发改善目标 | 记录同一任务协议下的计数和耗时，后续阶段再比较 |

---

## 六、规划总结与六项核心返回结论

本研究与架构规划严格遵守 AGENTS.md 顶刊论文最高战略导向与只读约束，完成了深度代码法医级审计与前沿文献调研，形成如下 6 项关键裁决结论：

1. **推荐架构**：
   Stage 21A 仅采用 **Plan → Verify → Sequential Execute → Reflect (PVER)** 最小闭环；Kahn 波次并发、复杂重规划和策略自演化保留为后续候选，不属于本阶段实现基线。
2. **认知循环**：
   Stage 21A 生命周期为分析、规划、验证、顺序执行、反思和终止/失败；自动重规划及基于向量相似度的死锁算子不在当前范围。
3. **最小数据结构**：
   定义了全量不可变、强类型、零时序泄漏的纯 Java 21 Record 体系：`TaskAnalysis`、`Plan`、`PlanStep`、`PlanVerificationResult`、`StepResult`、`ReflectionResult`、`CognitiveTrace`，为顶刊伪代码与架构图提供严格代数同构映射。
4. **与现有路径的兼容边界**：
   Stage 21A 候选边界是在 `AgentOrchestrator` 与既有执行器间建立 opt-in 接缝；具体接入点、回退契约和影响范围须在实施任务书中按当前源码复核，不预先承诺“100% 干净回退”或“零风险”。
5. **测试与评测指标**：
   Stage 21A 用确定性契约测试验证分析、计划校验、顺序执行、结构化反思和 trace；真实 baseline 与 KPI 均待 Stage 23A 测量后预注册，不沿用旧目标值。
6. **是否允许进入实现**：
   **准入裁决：仅 Stage 21A 范围收敛通过；本文不是源码实施授权。** Stage 21A 任务书需单独冻结并获得实施授权后方可修改源码；不得按本文完整路线图一次性实现 Stage 21–23。
