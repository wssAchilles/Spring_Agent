# Phase 110 实施详案与工程契约：声明式工作流 DSL 编译器、三阶静态安全门禁与零停机热重载引擎

**课题名称**：声明式工作流 DSL 编译器、三阶静态安全门禁与零停机热重载引擎 (Declarative Workflow DSL Compiler, Three-Stage Static Safety Gate & Zero-Downtime Hot-Reload Engine)  
**所属阶段**：企业级智能体第二演进阶段总路线图 Phase 110（`docs/plans/phase_107_to_112_master_roadmap.md`）  
**归档文件**：`docs/plans/phase_110_plan.md`  
**基线约束**：唯一生成模型 DeepSeek API（deepseek-flash）、唯一向量模型阿里千问 1536 维超球面向量（$\|\vec{C}\|_2 = 1.0 \pm 10^{-4}$）、无本地大模型、彻底弃用 OpenAI API、隔离 Java 21 环境（`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）。

---

## A. 当前代码与失败机制

### 1. 真实执行路径与组件调用关系
通过对当前代码库的完整审查，现有 DSL 编排与多智能体执行路径位于 `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/a2a/dsl/` 及周边模块：
1. **输入与数据边界**：
   - `WorkflowDefinition`：仅包含 `workflowId`, `name`, `version`, `description`, `List<WorkflowNode> nodes`, `List<WorkflowEdge> edges`；
   - `WorkflowNode`：仅包含 `nodeId`, `name`, `objective`, `requiredCapability`, `timeoutSeconds`, `required`；缺少节点类型语义（无法区分普通任务、循环开始/结束、Swarm 交接、Debate 竞技场、HITL 审批、MCP 工具调用）；
   - `WorkflowEdge`：仅包含 `fromNodeId`, `toNodeId`；无法承载流转条件（`condition`）、循环标记（`isLoopEdge`）、最大循环步数（`maxIterations`）与退出断言（`exitCondition`）；
2. **编译与门禁路径 (`DslWorkflowCompiler.compile`)**：
   - 门禁仅包含简单的非空校验与节点 ID 重复校验；
   - 依赖分析采用标准 Kahn 拓扑排序算法，利用入度为 0 节点入队消除边。若存在任何有向环（`visitedCount < nodeMap.size()`），一律视为死锁异常抛出 `DslCyclicDependencyException`；
   - 无法解析 YAML，仅提供单一的 Jackson JSON 反序列化；
   - 缺乏针对外部 MCP 工具名和能力标签的存活校验；
   - 缺乏针对 `AgentMeshRegistry` 中在线 `AgentCard` 及其阿里千问 1536 维超球面流形（$\|\vec{C}\|_2 = 1.0 \pm 10^{-4}$）的合法性校验；
   - 缺乏不可变编译凭单，无法溯源与审计校验记录；
3. **运行时热更与执行路径 (`DslWorkflowEngine`)**：
   - 仅包含单一 `AtomicReference<WorkflowDefinition> activeWorkflowRef`；
   - `deployWorkflow(WorkflowDefinition definition)` 执行 `compiler.compile(definition)` 后，直接 `activeWorkflowRef.set(definition)`；
   - **长事务撕裂缺陷**：在途会话（正在执行第 $N$ 阶段任务）若遭遇热更，若新定义删除了后续节点或变更了节点契约，在途会话在读取新引用或依赖上游黑板事实时将遭遇空指针或数据错乱；缺乏优雅下线（Graceful Drain）与版本租约分流机制；
   - 线程池固定使用虚拟线程（`Thread.ofVirtual()`）及 `CallerRunsPolicy`，但缺乏全局租约倒计时与超时熔断保护。

### 2. 失败机制与缺陷根因
1. **拓扑表达力缺陷（“环路即死锁”误判）**：
   - 业务真实场景中，大模型反思优化、状态机自愈、Debate 对抗等必须依赖有限循环（Bounded Cycle）。Kahn 算法将所有循环视为死锁，导致具有明确退出条件的循环业务流程无法被声明与执行；
2. **热重载指针覆写引发的在途会话状态撕裂**：
   - 单指针原子替换忽略了工作流执行的“长生命周期”特性。工作流实例跨越多个异步 Phase，执行周期可能长达数十秒甚至数分钟（含 HITL 人工审批）。新定义下发瞬间，旧实例后续步骤如果试图索引新定义的节点 ID，或新定义修改了共享黑板的 Key 命名契约，将直接导致在途业务崩溃；
3. **外部能力失活导致的运行时穿透报错**：
   - 编译期未校验 DSL 中指定的 MCP 工具是否已在网关注册、指定的 `requiredCapability` 是否有在线的 `AgentCard` 对应。这使得错误被延后至运行时异步调度阶段才爆发，严重损害系统可用性；
4. **缺乏编译防篡改凭单**：
   - 缺乏带 SHA-256 签名的编译凭单，审计追踪困难，无法在运行时校验内存中的执行计划是否真正通过了三阶安全门禁。

### 3. 本阶段唯一待验证假设 (Sole Verifiable Hypothesis)
> **假设 H-PHASE110-001**：在保持 Java 21 隔离运行环境与 DeepSeek API / 阿里千问 1536 维超球面流形基线不变的前提下，通过引入支持 YAML/JSON 统一双向互转的纯声明式 DSL 语法抽象、基于 Tarjan 强连通分量与 `max_iterations <= 10` 的三阶静态安全门禁、带有 SHA-256 签名的不可变编译凭单，以及基于 Java 21 `AtomicReference` 与版本注册表的多版本优雅下线（Graceful Drain）热重载引擎，能够实现多智能体循环/对抗/审批工作流静态编译通过率 100%、无界死锁环路静态拦截率 100%、热更期间在途长会话零中断与零状态撕裂（Graceful Drain 成功率 100%），且静态编译与门禁耗时 $\le 15\text{ms}$。

---

## B. Research Ledger

严格按照 `@AGENTS.md` 规范，对 6 个工业级开源项目/官方工程实践进行定向调研与精读，完整填满全部 14 项必填字段：

```text
id: RL-PHASE110-001
sourceType: production-implementation
titleOrRepository: temporalio/temporal & temporalio/sdk-java
authorsOrMaintainer: Temporal Technologies Inc. (Maxim Fateev, Samar Abbas et al.)
venueAndYear: Production Open Source / 2024-2026
doiOrArxiv: N/A
url: https://github.com/temporalio/sdk-java
commitOrTag: v1.26.0
license: MIT License
filesOrSectionsRead: io.temporal.workflow.Workflow (Workflow.getVersion, Workflow.patch), io.temporal.testing.WorkflowReplayer, io.temporal.worker.WorkerDeploymentOptions (BuildId versioning), Official Docs on Deterministic Workflow Definitions & Versioning Strategies
verificationStatus: VERIFIED
relevantFinding: Temporal 强调工作流定义的确定性（Determinism）。为避免热更新导致在途长事务非确定性崩溃（Non-Deterministic Errors, NDE），Temporal 提出了两大核心设计：1) 代码级 Patching/GetVersion 分支，让旧会话走旧逻辑，新会话走新分支；2) 基础设施级 Worker Versioning（通过 Build ID 标记版本），并在发布前必须使用 WorkflowReplayer 加载历史事件历史进行重放断言，确保新旧版本兼容。
projectApplicability: 本项目借鉴其版本隔离思想，在热更新时不直接修改在途会话的拓扑指针，而是为每个会话绑定不可变版本号，旧会话沿用旧拓扑执行直至结束（Graceful Drain），新会话原子路由到新拓扑。
limitations: Temporal 基于事件溯源（Event Sourcing）与重放机制，系统极其庞大，依赖底层 Temporal Server 状态存储集群，本项目 Hermes 属于轻量嵌入式微服务架构，不能直接照搬其完整的重放引擎，仅吸纳其版本分流与不可变执行逻辑。

id: RL-PHASE110-002
sourceType: production-implementation
titleOrRepository: Netflix/conductor & conductor-oss/conductor
authorsOrMaintainer: Netflix / Orkes Inc.
venueAndYear: Production Open Source / 2023-2026
doiOrArxiv: N/A
url: https://github.com/conductor-oss/conductor
commitOrTag: v3.15.0
license: Apache License 2.0
filesOrSectionsRead: com.netflix.conductor.common.metadata.workflow.WorkflowDef, com.netflix.conductor.core.execution.tasks.DoWhile, com.netflix.conductor.core.execution.WorkflowExecutor, conductor-common/src/main/resources/schema.json
verificationStatus: VERIFIED
relevantFinding: Conductor 采用 JSON 声明式 DSL 定义工作流。其循环由 DO_WHILE 任务承载，通过 loopCondition（JavaScript 表达式）与 loopOver 任务列表实现循环；其工作流定义使用整数 version 字段管理版本，支持按版本启动实例。然而，其早期版本使用 Nashorn 脚本引擎动态计算 loopCondition，导致严重的脚本注入风险与未受限循环（无静态步数上限，依赖动态退出条件）。
projectApplicability: 吸收其声明式 JSON/YAML 结构化定义与版本整型标识模型；但坚决摒弃其 Nashorn 动态脚本计算机制，循环边必须为纯声明式静态布尔断言并强制绑定硬性步数上限（max_iterations <= 10）。
limitations: Conductor 依赖 Dynomite/Redis/Elasticsearch 等多重中间件支撑分布式状态，且其 DO_WHILE 的动态解析存在沙箱漏洞历史，不可直接用于高安全多智能体编排。

id: RL-PHASE110-003
sourceType: production-implementation
titleOrRepository: apache/airflow
authorsOrMaintainer: Apache Software Foundation (Airflow Project Management Committee)
venueAndYear: Apache Top-Level Project / 2024-2026
doiOrArxiv: N/A
url: https://github.com/apache/airflow
commitOrTag: 3.3.0
license: Apache License 2.0
filesOrSectionsRead: airflow.models.dag.DAG, airflow.serialization.serialized_objects.SerializedDAG, airflow.dag_processing.processor.DagFileProcessor, CVE-2026-33264 Security Advisory
verificationStatus: VERIFIED
relevantFinding: Airflow 在 2.x/3.x 中全面引入 SerializedDAG，将 Python 代码解析为结构化 JSON 存入元数据库。CVE-2026-33264 漏洞揭示：BaseSerialization.deserialize() 中由于未严格限制类路径反序列化（import_string 动态加载），导致攻击者能够构造恶意 DAG 触发远程代码执行 (RCE)。Airflow 官方随后强制引入类白名单 allowed_deserialization_classes 与独立的 DagFileProcessor 进程隔离。
projectApplicability: 强化了本项目的纯声明式 AST 设计原则：DSL 编译器绝不引入任何类加载器（ClassLoader）、绝不使用动态脚本求值器（如 Groovy、MVEL、SpEL、Nashorn），仅使用 Jackson 严格解析为无执行能力的 Java Record/POJO，并在门禁一中执行严格的 JSON Schema 白名单校验。
limitations: Airflow 主要面向批处理和离线数据流 DAG，缺少交互式多智能体状态机（StateGraph）以及动态竞标（A2A CFP）的实时微服务支持。

id: RL-PHASE110-004
sourceType: production-implementation
titleOrRepository: langchain-ai/langgraph
authorsOrMaintainer: LangChain AI (Harrison Chase et al.)
venueAndYear: Production Open Source / 2024-2026
doiOrArxiv: N/A
url: https://github.com/langchain-ai/langgraph
commitOrTag: v0.2.14
license: MIT License
filesOrSectionsRead: langgraph.graph.state.StateGraph, langgraph.pregel.runner, langgraph.checkpoint.base.BaseCheckpointSaver, langgraph.types.interrupt, Documentation on recursion_limit & HITL
verificationStatus: VERIFIED
relevantFinding: LangGraph 专门针对大模型多智能体设计了 StateGraph。其核心安全与长事务机制包括：1) recursion_limit（默认 25 super-steps）：任何图执行达到步数阈值立即抛出 GraphRecursionError，防止死循环导致 LLM Token 爆额；2) checkpointing：在每个 super-step 持久化完整状态快照（State Snapshot），使得挂起长事务具备断点续传能力；3) interrupt(value) 原语：支持在审批节点主动挂起图执行，等待人工审批通过后以 Command 恢复。
projectApplicability: 直接对标并迁移其思想：在本项目 DSL 中原生引入 STATE_GRAPH_LOOP 节点与 max_iterations <= 10 的硬门禁；原生引入 HITL_APPROVAL 节点并与现有的 ApprovalProxyController 及 SharedBlackboard 对齐；在门禁二中对循环实施步数静态限制。
limitations: LangGraph 为 Python 生态设计，其类型系统较为动态，且缺少分布式多版本原子热重载注册表与强类型签名凭单。本项目需在 Java 21 强类型环境中提供更高等级的编译安全保障。

id: RL-PHASE110-005
sourceType: production-implementation
titleOrRepository: camunda/camunda (Zeebe Engine)
authorsOrMaintainer: Camunda Services GmbH
venueAndYear: Production Open Source / 2024-2026
doiOrArxiv: N/A
url: https://github.com/camunda/camunda
commitOrTag: 8.5.0
license: Camunda License / Zeebe Community License
filesOrSectionsRead: io.camunda.zeebe.engine.processing.deployment.DeploymentCreateProcessor, io.camunda.zeebe.engine.state.deployment.DbProcessState, io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors, BPMN 2.0 Multi-Instance Specification
verificationStatus: VERIFIED
relevantFinding: Zeebe 引擎在部署流程定义时执行严格的版本控制：每次部署新流程定义，若 BPMN 发生变化则生成新的自增版本号，不可变持久化于 RocksDB。在途流程实例严格锁定启动时的 processDefinitionKey，绝不因新部署而发生版本迁移；新创建实例自动路由至最新版本。在循环（Multi-Instance）处理上，通过 chunking 机制与异步事务边界（asyncBefore）防止内存撑爆与超长事务锁。
projectApplicability: 本项目零停机热重载引擎的设计直接吸收 Zeebe 的“不可变版本注册表”与“在途实例版本锁定”理念：WorkflowVersionRegistry 维护当前活动版本与历史存活版本，在途会话持有旧版本租约直到终止，杜绝内存指针强更导致的会话撕裂。
limitations: Zeebe 深度依赖 Raft 共识协议与 RocksDB 存储流，系统部署复杂度高。本项目作为 Spring Boot 微服务应用，需在单机/集群内存中利用 Java 21 的 AtomicReference 和并发 Map 实现轻量级等价保证。

id: RL-PHASE110-006
sourceType: official-doc
titleOrRepository: Amazon States Language (ASL) Specification & AWS Step Functions
authorsOrMaintainer: Amazon Web Services, Inc.
venueAndYear: Official Specification & Cloud Service / 2023-2026
doiOrArxiv: N/A
url: https://states-language.net/ & https://docs.aws.amazon.com/step-functions/
commitOrTag: ASL Spec 2024 Revision
license: Proprietary AWS Specification
filesOrSectionsRead: ASL State Machine Definition (Task, Choice, Parallel, Map, Pass), ValidateStateMachineDefinition API, Versions and Aliases (Traffic Routing), ASL Linter & Error Handling (TimeoutSeconds, Catch, Retry)
verificationStatus: VERIFIED
relevantFinding: ASL 是一种纯 JSON 结构的声明式工作流语言，严禁嵌入可执行代码。AWS 提供了 ValidateStateMachineDefinition 静态分析 API，在部署前进行全面的语法与拓扑校验（如孤立状态、死循环、缺失终态等）。其版本与别名（Versions & Aliases）机制允许给不可变版本创建别名并支持流量灰度切流（Canary）。ASL 中的循环依靠 Choice 状态跳回前置状态实现，官方强制要求必须显式声明超时（TimeoutSeconds）与退出分支以防止死循环。
projectApplicability: 本项目 DSL 语法规范直接借鉴 ASL 的声明式哲学：纯数据结构、无代码嵌入、强类型校验，并在编译期通过 ThreeStageStaticSafetyGate 提供等价于 ValidateStateMachineDefinition 的静态合规断言。
limitations: ASL 的状态转移过于冗长繁琐，且对大模型语义匹配、动态 Agent 竞标与 MCP 工具生态无原生支持。本项目在此基础上针对 AI-Native 场景进行了高度专门化抽象。
```

---

## C. 可迁移与不可迁移结论

### 1. 可直接采用与迁移的结论 (Adopted)
1. **纯声明式无执行能力 AST (from ASL & Conductor)**：
   - 语法模型采用静态 POJO/Record 数据结构，绝不引入 Groovy、SpEL、Nashorn 等动态脚本求值器，彻底免疫远程代码执行与表达式注入。
2. **多版本共存与在途会话版本锁定 (from Zeebe & Temporal)**：
   - 引入不可变 `WorkflowVersionRegistry`。在途会话在初始化时锁定特定版本引用，平滑执行至结束（Graceful Drain）；新会话原子路由至新版本。
3. **循环步数硬门禁与超时熔断 (from LangGraph & ASL)**：
   - 借鉴 LangGraph 的 `recursion_limit`，在编译门禁中强制要求循环边显式声明 `max_iterations <= 10` 与明确的退出条件，防止大模型陷入死循环消耗 Token 额度。
4. **编译期静态安全门禁与结构自签名 (from ASL & Airflow SerializedDAG)**：
   - 编译必须产出带有 SHA-256 自签名的不可变凭单 `DslWorkflowCompilationReceipt`，运行时调度前强校验凭单签名，防止未编译或被篡改的定义进入执行引擎。

### 2. 需要深度改造与增强的部分 (Adapted)
1. **拓扑环路校验算法重构 (Kahn -> Tarjan SCC)**：
   - 现存 Kahn 算法将所有环路视为死锁。改造为基于 **Tarjan 强连通分量 (SCC)** 算法：将拓扑中的子图划分为 DAG 主干与紧耦合循环环路；对于大小 $>1$ 的 SCC 或存在自环的节点，判定其是否为声明了 `max_iterations` 的合法 `STATE_GRAPH_LOOP` 或 `DEBATE_ARENA`，既允许业务有界循环，又 100% 拦截无界死锁环路。
2. **外部依赖存活断言（三阶门禁创新）**：
   - 在编译期静态断言：节点所引用的 MCP 工具必须已在本地或网关注册并在线；节点所声明的 `requiredCapability` 必须在 `AgentMeshRegistry` 中存在匹配的 `AgentCard`；对应的 `AgentCard` 必须满足阿里千问 1536 维超球面流形几何约束（$\|\vec{C}\|_2 = 1.0 \pm 10^{-4}$）。

### 3. 必须坚决拒绝的方案 (Rejected)
1. **拒绝动态脚本引擎（Nashorn / Groovy / Python Exec）**：严禁在 DSL 中编写任意代码，所有条件均为声明式预定义操作符。
2. **拒绝基于事件溯源的全量重放引擎（Temporal Replayer）**：系统架构定位于轻量嵌入式微服务，避免引入沉重的外部重放依赖。
3. **拒绝动态热更强杀在途事务（Forced Abort）**：严禁在热更时强行终止正在运行的会长，必须通过 Graceful Drain 机制平滑下线。

---

## D. 候选方案比较

| 比较维度 | baseline (Phase 47 现有) | 最小诊断修补方案 | 候选方案 (Phase 110 推荐) | 保持现状选项 |
| :--- | :--- | :--- | :--- | :--- |
| **拓扑表达力** | 仅支持简单 DAG，环路一律报死锁 | 仅对指定节点忽略环路检测 | **支持 DAG、StateGraph 有界循环、Swarm 交接、Debate、HITL、MCP** | 仅支持简单 DAG |
| **安全沙箱防护** | 仅非空和重复 ID 校验 | 增加字符串黑名单 | **纯声明式无执行 AST + 三阶静态安全门禁 (Schema/SCC/存活断言)** | 脆弱无防线 |
| **死循环与死锁防护** | 粗暴拦截所有环，无法支持循环业务 | 运行时动态计数器 | **静态 Tarjan SCC 拓扑分析 + `max_iterations <= 10` 硬门禁** | 无法支持循环业务 |
| **热重载可靠性** | 单指针覆写，在途会话状态撕裂 | 加读写锁（造成请求阻塞） | **Java 21 原子指针翻转 + 多版本共存 + Graceful Drain 优雅下线** | 状态撕裂风险极高 |
| **外部依赖校验** | 无校验，运行时延后穿透报错 | 运行时 try-catch 降级 | **编译期断言 MCP 可用性 + 千问 1536 维 AgentCard 在线与流形合法性** | 延迟至运行时爆发 |
| **审计与凭单机制** | 无凭单，无法追溯编译状态 | 仅记录日志 | **不可变编译凭单 `DslWorkflowCompilationReceipt` + SHA-256 自签名** | 无审计能力 |
| **编译门禁耗时** | $\le 2\text{ms}$ | $\le 3\text{ms}$ | **$\le 10\text{ms}$（完全满足预算 $\le 15\text{ms}$）** | $\le 2\text{ms}$ |
| **实现复杂度** | 低（遗留代码缺陷明显） | 低（治标不治本） | **中等（模块边界清晰，高内聚低耦合）** | 零（技术债务积压） |
| **拒绝理由 / 裁决** | 无法支撑第二演进阶段复杂编排 | 无法根治在途撕裂与死锁风险，拒绝 | **采纳推荐** | 严重阻碍后续 Phase 研发，拒绝 |

---

## E. 推荐的最小算法

只推荐能直接验证唯一假设 `H-PHASE110-001` 的最小机制集合：
1. **纯声明式 AST 模型 (`DslWorkflowDefinition`, `DslWorkflowNode`, `DslWorkflowEdge`, `DslNodeType`)**：
   - 采用纯 Java Record/POJO，原生支持 YAML 与 JSON 双向互转；
   - 节点类型涵盖 `TASK`, `STATE_GRAPH_LOOP`, `SWARM_HANDOFF`, `DEBATE_ARENA`, `HITL_APPROVAL`, `MCP_TOOL_CALL`；
   - 边模型支持 `isLoopEdge`, `maxIterations` 与声明式 `exitCondition`。
2. **三阶静态编译安全门禁 (`ThreeStageStaticSafetyGate`)**：
   - **门禁一（语法结构门禁）**：JSON Schema 契约校验、节点 ID 唯一性与命名规范（`^[a-zA-Z0-9_-]{1,64}$`）、边端点存在性；
   - **门禁二（有界拓扑门禁）**：基于 Tarjan SCC 算法识别强连通分量，静态强制要求环路边标记 `isLoopEdge` 且 `1 <= maxIterations <= 10`，无界环路 100% 熔断并抛出 `UnboundedCycleException`；
   - **门禁三（外部依赖存活门禁）**：静态断言 MCP 工具在线、AgentMesh 对应 `AgentCard` 在线，以及千问 1536 维向量流形单位范数约束（$\left| \|\vec{C}\|_2 - 1.0 \right| \le 10^{-4}$）。
3. **不可变编译凭单 (`DslWorkflowCompilationReceipt`)**：
   - 封装 `receiptId`, `workflowId`, `version`, `astDigest`, `topologyHash`, `gateReports`, `sha256Signature`, `compiledTimestamp`；
   - 提供 `verifySignature()` 判定逻辑，未验签通过的执行计划拒绝载入引擎。
4. **零停机热重载引擎 (`ZeroDowntimeHotReloadEngine`) 与版本注册表 (`WorkflowVersionRegistry`)**：
   - 基于 Java 21 `AtomicReference.compareAndSet()` 实现无锁秒级原子翻转；
   - 维护多版本共存与 `LongAdder` 活跃租约计数器，旧版本执行 Graceful Drain 优雅下线；
   - 提供 `rollbackToVersion(int targetVersion)` 支持秒级一键原子回滚。

不需要引入任何重型外部工作流引擎、分布式事件溯源中间件或动态脚本执行器，保持系统极轻量、高内聚。

---

## F. 实验与实现计划

### 1. 契约测试清单 (Contract Tests)
在 `backend/tests/src/test/java/tech/qiantong/qknow/hermes/a2a/Phase110DslCompilerAndHotReloadContractTest.java` 中构建 6 项严格契约：
- **Contract 1: YAML/JSON 双向无损序列化与 Schema 合法性校验契约**：验证 YAML 与 JSON 解析等价性，拦截非法字段与重复 ID；
- **Contract 2: Tarjan SCC 拓扑分析与有界循环硬熔断契约**：验证 DAG 正常放行、有界循环（`max_iterations <= 10`）放行、无界死锁环路及 `max_iterations > 10` 100% 拦截；
- **Contract 3: 外部 MCP 工具存活与 AgentCard 千问 1536 维流形断言契约**：验证未注册 MCP 工具、离线 AgentCard、以及千问向量范数超标时编译熔断；
- **Contract 4: 不可变编译凭单生成与 SHA-256 防篡改核验契约**：验证凭单生成与签名校验，任何字段篡改均导致验签失败；
- **Contract 5: 零停机热重载与在途长事务 Graceful Drain 隔离契约**：验证高并发下新旧版本共存，在途长事务持旧版本执行完毕无撕裂，新会话走新版本；
- **Contract 6: 紧急一键原子回滚契约**：验证 `rollbackToVersion` 实现指针瞬间回滚，新流量立即重定向至回滚版本。

### 2. 反事实与消融设计
- **消融 1（关闭 Tarjan SCC 校验）**：验证无界死锁环路能够进入运行时引发无限循环，反向证明门禁二的必要性；
- **消融 2（关闭 Graceful Drain 租约隔离）**：验证单指针覆写直接引发在途长事务空指针与状态撕裂，反向证明多版本隔离的必要性。

### 3. 数据泄漏防护与资源预算
- **数据隔离**：黑板 Key 增加会话与版本命名空间前缀，杜绝跨版本跨会话数据污染；
- **性能预算**：单次 DSL 编译与三阶门禁耗时 $\le 15\text{ms}$，CAS 翻转耗时 $\le 1\mu\text{s}$，凭单验签耗时 $\le 1\text{ms}$。

### 4. 最小修改文件集合 (Minimal Modification Set)
```text
backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/a2a/dsl/
├── DslNodeType.java                                   # 节点类型枚举
├── DslWorkflowDefinition.java                         # 声明式工作流增强规约 (支持 YAML/JSON)
├── DslWorkflowNode.java                               # 增强节点模型
├── DslWorkflowEdge.java                               # 增强边模型 (含 isLoopEdge, maxIterations, exitCondition)
├── DslWorkflowCompilationReceipt.java                 # 不可变编译凭单 Record (含 SHA-256 签名)
├── ThreeStageStaticSafetyGate.java                    # 三阶静态编译安全门禁 (Schema / Tarjan SCC / 存活断言)
├── WorkflowVersionRegistry.java                       # 多版本共存与 Graceful Drain 注册表
├── ZeroDowntimeHotReloadEngine.java                   # 基于 Java 21 AtomicReference 的零停机热重载引擎
├── exception/
│   ├── DslSyntaxValidationException.java              # 语法门禁异常
│   ├── UnboundedCycleException.java                   # 无界循环/死锁异常
│   └── DependencyUnsatisfiedException.java            # 外部依赖/流形断言异常
backend/tests/src/test/java/tech/qiantong/qknow/hermes/a2a/
└── Phase110DslCompilerAndHotReloadContractTest.java   # Phase 110 专属 6 项契约测试套件
```

### 5. 完整验证命令
```bash
# 专属契约测试执行命令 (严格隔离 Java 21)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn clean test -pl backend/tests -Dtest=tech.qiantong.qknow.hermes.a2a.Phase110DslCompilerAndHotReloadContractTest

# 后端全量防退化回归测试 (保持 1000+ 测试全绿)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl backend/tests
```

---

## G. 风险、停止条件和后续授权边界

### 1. 残余风险评估
- **多版本内存堆积**：频繁发布可能导致 DRAINING 版本占用内存，设置最多保留 5 个历史版本；
- **MCP 外部网络抖动**：门禁三校验 MCP 存活时可能受网络延迟影响，设置 500ms 短超时与 1 次轻量重试。

### 2. 立即停止条件 (Immediate Stop Conditions)
- 任何门禁未能拦截无界死锁环路；
- 热更测试中出现旧会话中断或抛出空指针（Graceful Drain 失败）；
- 静态编译与三阶门禁耗时超过 $50\text{ms}$ 性能红线；
- 破坏了全库既有 1018 项测试中的任何一项。

### 3. 后续授权边界
- **Phase 110 仅获准实施**：上述最小修改文件集合与契约测试；
- **严禁越界修改**：严禁修改任何具身力学封存代码（`tech.qiantong.qknow.ai.embodied.*`）；严禁修改线上数据库表结构；严禁修改既有 A2A 信封基础通信语义。
