---
report_id: P015-B
role: code-forensic
project_commit: b6516ee3
hypothesis_id: P015-MULTI-AGENT-PRODUCTION-WIRING
created_at: 2026-10-22T21:46:00+08:00
status: COMPLETE
verification_counts: {VERIFIED: 16, PARTIALLY_VERIFIED: 0, NOT_VERIFIED: 0}
---

# 本地代码法医审计报告：多智能体编排生产集成与最小接线架构分析

## 摘要与待验证假设

本法医审计报告由【本地代码法医审计员 (Code Forensic Auditor)】执行，基于本项目当前代码提交基线 `b6516ee3`（只读环境，未引入任何破坏性代码或配置变更），针对以下唯一待验证假设展开全面法医级代码审计：

> **唯一待验证假设 (P015-MULTI-AGENT-PRODUCTION-WIRING)**：
> “默认单 Agent 路径不变；SWARM（SupervisorAgent）与最小 DEBATE 作为按 Bot 配置、默认关闭的编排策略接入 AgentOrchestrator，可测量提升复杂问题质量，延迟 ≤3×、成本 ≤3×、默认零回归。”

经过对后端全量代码（包含 `qknow-module-kb`、`qknow-hermes-proto`、`qknow-hermes-core`、`qknow-framework/qknow-ai` 以及相关单元/契约/集成测试）的法医级代码追踪，得出核心定论：
1. **多智能体“零生产调用”事实成立**：`SupervisorAgent`、`StructuredDebateCoordinator`、`MoAExpertConsensusEngine`、`TripartiteDebateArbiterEngine` 等核心多智能体组件在当前生产调用链（Controller → Service → gRPC → AgentOrchestrator）中的**生产调用点数量确凿为 0**。
2. **控制面配置断流**：数据层与控制层（`KbAgentConfigDO.parameters` 与 `KbConversationController.sendMessage`）已具备 `parameters` 载体，但在业务服务层组装 gRPC `ChatRequest` 时被彻底丢弃，`ChatRequest` proto 亦缺少相应承载字段。
3. **组件成熟度严重分层**：代码库中存在大量“C 类”启发式/单测仿真代码（如关键词打分、循环计数假 Hash、手工 1536 向量等），必须严格设立红线禁止接入生产；同时存在完备可用的“A 类”真实 LLM 规划与 Worker 实例，以及需补齐生产治理缝隙的“B 类”蜂群架构组件。
4. **最小 Seam 具备可行性**：通过在 4 个核心文件内建立非侵入式按 Bot 策略开关，可严格保障既有单 Agent（SINGLE）路径 100% 字节级行为一致与基线零回归。

---

## 一、真实生产调用图与零生产调用核验

### 1.1 生产请求端到端执行链路图

当前系统生产环境下接收用户提问至大模型流式输出的完整调用路径如下：

```
[前端 Web / 移动端]
       │ HTTP POST /admin/kb/conversation/send (SSE 流式)
       ▼
[tech.qiantong.qknow.module.kb.controller.admin.conversation.KbConversationController#sendMessage] (行 82-126)
       │ 1. 查询历史会话与保存用户/助手消息 (行 84-88)
       │ 2. 读取配置：KbAgentConfigDO config = agentConfigService.getKbAgentConfigByBotId(botId) (行 91)
       │ 3. 构造请求：agentReq.setParameters(config.getParameters()) (行 108) [关键断点：后续被丢弃]
       │ 4. 调度服务：agentConfigService.chatMessage(agentReq) (行 125)
       ▼
[tech.qiantong.qknow.module.kb.service.agent.impl.KbAgentConfigServiceImpl#chatMessage] (行 226-442)
       │ 1. 解析模型、API Key 与 RAG 语义缓存 (行 228-261)
       │ 2. 多库协同并发召回与 20KB 预算硬截断 (行 267-365)
       │ 3. 解析工具白名单与构建历史列表 (行 368-388)
       │ 4. 组装 gRPC ChatRequest (行 393-415) [未传递 parameters / orchestrationMode]
       │ 5. 调用 Hermes 客户端：hermesGrpcClient.chat(request) (行 440-441)
       ▼
[gRPC 网络传输 (ChatRequest -> stream ChatEvent)]
       ▼
[tech.qiantong.qknow.hermes.grpc.HermesGrpcService#chat] (行 35-66)
       │ 1. 本地安全解析解密 API Key (行 41)
       │ 2. 调用编排器：agentOrchestrator.chat(effectiveRequest) (行 42)
       │ 3. 反应式流与 gRPC Observer 双向绑定：GrpcReactorBridge.bindStream (行 54)
       ▼
[tech.qiantong.qknow.hermes.agent.AgentOrchestrator#chat] (行 130-222)
       │ 1. 注册 LangFuse Trace 链路追踪 (行 141-143)
       │ 2. 标记 DeepSeek 成本开始 (行 137)
       │ 3. 执行单智能体执行循环：executeAgent(...) (行 154)
       ▼
[tech.qiantong.qknow.hermes.agent.AgentOrchestrator#executeAgent] (行 244-550)
       │ 1. 构建 ChatModel 实例 (行 250-268)
       │ 2. 挂载知识库动态工具与意图白名单 (行 285-329)
       │ 3. 挂载 ReActCycleGuard 振荡熔断守卫 (行 431-438)
       │ 4. 构建单智能体：ReactAgent.builder().name("hermes_agent")...build() (行 441-450)
       │ 5. 容量许可限流：enterCapacity(ACTIVE_REACT_RUNS) (行 452)
       │ 6. 异步流式订阅：StreamSubscriptionGuard.subscribeAsync(agent.stream(messages), ...) (行 464-478)
       │ 7. 逐 Token 组装并向 FluxSink 发射 StreamingChunk / ModelFinished (行 481-530)
```

### 1.2 多智能体类的生产调用方核验与“零生产调用”审计

对代码库中所有多智能体核心类进行全局符号追踪与调用链排查，证据如下：

| 多智能体类名 | 源码文件路径 | 生产代码调用方 (文件:行号) | 测试代码调用方 | 审计结论 |
| :--- | :--- | :--- | :--- | :--- |
| `SupervisorAgent` | `backend/qknow-hermes/.../SupervisorAgent.java:34` | **无 (生产调用点 = 0)**。<br>仅见于：<br>1. `AgentRegistry.java:127`（非生产活跃工厂方法）；<br>2. `QueryRouter.java:115`（仅在 JavaDoc 注释中提及）；<br>3. `reflect-config.json:15`（反射元数据）。 | `AgentE2ETest.java:365`<br>`MultiAgentTest.java:140`<br>`SwarmOrchestrationContractTest.java:382`<br>`DualLoopEvaluatorOptimizerContractTest.java:134` | **确凿零生产调用**。生产链路完全未实例化或执行该主管。 |
| `WorkerAgent` | `backend/qknow-hermes/.../WorkerAgent.java:27` | `AgentOrchestrator.java:732`（仅在 `planAndSolve` 分支开启且判定为复杂问题时，由线程池临时独立 `new WorkerAgent(...)` 并行执行）。<br>`AgentRegistry.java:68-76`（单例静态 Worker，生产未激活）。 | `AgentE2ETest.java:80`<br>`MultiAgentTest.java:53`<br>`AgentPerformanceTest.java:63`<br>`McpTwoPhaseFullLoopContractTest.java:174` | **主生产流未常态调用**。仅作为 Plan-and-Solve 的子任务临时执行体存在。 |
| `StructuredDebateCoordinator` | `backend/qknow-hermes/.../StructuredDebateCoordinator.java:17` | **无 (生产调用点 = 0)**。 | `MultiAgentDebateIntegrationTest.java:27`<br>`StructuredDebateCoordinatorTest.java:22` | **确凿零生产调用**。完全脱离生产链路。 |
| `MoAExpertConsensusEngine` | `backend/qknow-hermes/.../MoAExpertConsensusEngine.java:20` | **无 (生产调用点 = 0)**。 | `MultiAgentDebateIntegrationTest.java:28` | **确凿零生产调用**。完全脱离生产链路。 |
| `SwarmDynamicHandoffHub` | `backend/qknow-hermes/.../SwarmDynamicHandoffHub.java:20` | **无 (生产调用点 = 0)**。 | `MultiAgentDebateIntegrationTest.java:26` | **确凿零生产调用**。完全脱离生产链路。 |
| `DefaultConsensusArbiterEngine` | `backend/qknow-hermes/.../DefaultConsensusArbiterEngine.java:20` | **无 (生产调用点 = 0)**。 | `FivePillarsEvolutionContractTest.java:166` | **确凿零生产调用**。完全脱离生产链路。 |
| `TripartiteDebateArbiterEngine` | `backend/qknow-hermes/.../TripartiteDebateArbiterEngine.java:18` | **无 (生产调用点 = 0)**。 | `AgentCognitiveOrchestrationContractTest.java:85` | **确凿零生产调用**。完全脱离生产链路。 |
| `ConsensusArbiter` (qknow-ai) | `backend/qknow-framework/.../ConsensusArbiter.java:23` | **无 (生产调用点 = 0)**。 | `Phase31MultiAgentConsensusContractTest.java:46` | **确凿零生产调用**。完全脱离生产链路。 |

**法医定论**：
生产环境中不存在任何多智能体协作或辩论的实际流量，“零生产调用”百分之百成立。现有线上所有对话请求均由 `AgentOrchestrator.executeAgent` 内部单实例 `ReactAgent` 处理。

---

## 二、多智能体组件法医级分类矩阵

根据组件是否包含真实大模型交互、架构成熟度以及是否存在伪造仿真逻辑，将代码库中涉及的多智能体组件分为三类：

### 2.1 组件分类定义
- **A 类（真实 LLM 编排组件，可直接复用）**：包含完整提示词注入、真实 Spring AI `ChatModel` 调用或包装成熟 `ReactAgent`，逻辑无伪造，可作为生产最小 Seam 的基础构件。
- **B 类（结构真实但未接生产，需要接线与工程加固）**：具备真实多智能体协同协议（如 DAG 分层、合同网竞标、黑板同步等），但在流式语义、状态隔离、租户隔离与容量治理上存在缺口，需加固后接入。
- **C 类（启发式/仿真/固定 1536，严禁直接生产使用）**：内部采用关键词规则伪造置信度、循环计数伪造 Hash 凭单、硬编码 1536 向量或空接口 Mock，缺乏大模型认知能力，接入生产将直接导致幻觉与系统崩溃。

### 2.2 详细分类清单表

| 分类 | 组件类名 | 文件位置及关键行号 | 法医证据特征 | 评估结论与处置措施 |
| :---: | :--- | :--- | :--- | :--- |
| **A 类** | `HierarchicalTaskPlanner` | `tech.qiantong.qknow.hermes.agent.dag.HierarchicalTaskPlanner:25-95` | 通过 `chatModel.call(Prompt)` 动态向大模型发起子任务拆解 Prompt，解析 JSON 数组并运用标准 Kahn 拓扑排序算法提取分层阶段。 | **直接复用**。作为 SWARM 意图拆解的核心引擎，代码纯正无伪造。 |
| **A 类** | `WorkerAgent` | `tech.qiantong.qknow.hermes.agent.WorkerAgent:27-173` | 包装真实 `ReactAgent`，支持工具挂载与系统提示词动态绑定，在 `AgentOrchestrator.java:732` 已有生产临时构造成功先例。 | **直接复用**。作为子任务独立执行的工蜂载体，每次请求独立 `new`。 |
| **A 类** | `SwarmLoopGuard` | `tech.qiantong.qknow.hermes.agent.guard.SwarmLoopGuard:20-80` | 基于请求级 `sessionId` 维护指令滑动窗口哈希与编辑距离判定，可有效拦截智能体死循环。 | **直接复用**。作为蜂群执行的死循环与发散防护层。 |
| **A 类** | `BaseAgent` | `tech.qiantong.qknow.hermes.agent.BaseAgent:6-32` | 核心抽象基类，规范了 `chat(String question, Map<String, Object> context)` 标准契约。 | **直接复用**。作为通用 Agent 类型约束。 |
| **B 类** | `SupervisorAgent` | `tech.qiantong.qknow.hermes.agent.SupervisorAgent:34-220` | 完整编排了任务规划、拓扑分派、黑板归集与双环质检；但内部成员变量持有单例 `blackboard` 存在**跨请求状态污染**；返回单体 `String` 未支持 `Flux<ChatEvent>`。 | **加固接线**。消除黑板成员变量共享，改造为请求级上下文创建，由编排器包装流式事件。 |
| **B 类** | `TopologicalPhasedDispatcher` | `tech.qiantong.qknow.hermes.agent.dag.TopologicalPhasedDispatcher:20-75` | 遍历 `PhasedExecutionPlan` 阶段，发起合同网 CFP 竞标并分派给最佳 Worker，结果写入黑板。 | **加固接线**。需将任务超时与线程池并发限流融入调度主循环。 |
| **B 类** | `ContractNetDispatcher` | `tech.qiantong.qknow.hermes.agent.bidding.ContractNetDispatcher:17-60` | 实现标准 FIPA 合同网协议，根据能力匹配度、负载率与可靠性进行综合算力投标评估。 | **加固接线**。逻辑真实可靠，需在请求结束时主动重置活跃 Worker 列表。 |
| **B 类** | `SharedBlackboard` | `tech.qiantong.qknow.hermes.agent.blackboard.SharedBlackboard:21-95` | 并发线程安全字典，维护事实快照、历史追溯与版本树。 | **生命周期改造**。必须声明为请求级（Request-Scoped）局部对象，严禁跨请求复用。 |
| **B 类** | `AgentRegistry` | `tech.qiantong.qknow.hermes.agent.AgentRegistry:19-140` | 单例 Spring 组件，初始化内置静态 WorkerAgent（`initDefaultAgents` 行 58-81）。 | **限制使用**。严禁生产直接使用单例内的静态 Worker（存在跨请求凭据污染 N4 风险）。 |
| **B 类** | `SwarmDynamicHandoffHub` | `tech.qiantong.qknow.hermes.agent.swarm.engine.SwarmDynamicHandoffHub:20-134` | 维护移交栈与深度熔断（max 5 次），拦截即时 A->B->A 振荡与拓扑闭环。 | **暂不接入**。适合开放漫游式群智架构，与当前最小有界收敛假设不匹配，暂缓接线。 |
| **C 类** | `DefaultConsensusArbiterEngine` | `tech.qiantong.qknow.hermes.agent.debate.impl.DefaultConsensusArbiterEngine:20-120` | 行 101-108 仲裁评分采用硬编码关键词匹配：`prop.contains("证明") ? +0.15 : 0`，`prop.length() > 50 ? +0.10 : 0`。完全无大模型参与。 | **坚决否决**。纯伪仿真启发式打分，接入生产将产生虚假共识。 |
| **C 类** | `TripartiteDebateArbiterEngine` | `tech.qiantong.qknow.hermes.agent.debate.tripartite.TripartiteDebateArbiterEngine:18-133` | 行 61, 78, 95 使用 `"sha256_mock_qwen_1536_p" + round` 伪造哈希；行 67 使用 `(activeRisks.size() - round) / size` 算术循环模拟分歧度。无 LLM 调用。 | **坚决否决**。属于典型的单测模拟桩，绝不可作为生产辩论仲裁引擎。 |
| **C 类** | `StructuredDebateCoordinator` | `tech.qiantong.qknow.hermes.agent.swarm.engine.StructuredDebateCoordinator:17-143` | 行 113 硬编码 `u.length != 1536 || v.length != 1536` 返回 1.0；行 100 暴力按 3.5 字符硬截断；Participant 和 Judge 均为无真实实现的抽象接口。 | **坚决否决**。测地距离计算缺乏通用维度自适应，缺乏真实模型驱动。 |
| **C 类** | `MoAExpertConsensusEngine` | `tech.qiantong.qknow.hermes.agent.swarm.engine.MoAExpertConsensusEngine:20-214` | 行 134-165 存在 8 处硬编码 `float[1536]`；行 100 一票否决阈值（0.45）缺乏理论泛化证明；专家接口均为抽象方法。 | **坚决否决**。维度锁死、缺乏真实模型与知识库绑定，禁止上线。 |

---

## 三、生产就绪缺口清单 (Production Gaps)

将多智能体架构从“测试/离线状态”接入“高并发、多租户生产环境”，必须清偿以下 6 项核心缺口：

### 3.1 流式语义断层 (String vs Flux<ChatEvent>)
- **生产基线**：`HermesGrpcService` 与 `AgentOrchestrator` 严格要求返回 `Flux<ChatEvent>`，由 `StreamingChunk` 逐 Token 向前端推送打字机流，并通过 `status="thinking"` 或 `tool_call` 事件更新前端交互状态。
- **现状缺口**：`SupervisorAgent.chat()`、`WorkerAgent.chat()` 底层均返回同步阻塞的单体 `String`。若直接以同步阻塞方式运行，会导致用户端在多智能体交互的 5–15 秒内陷入“白屏/假死”，既无法展示中间思维链，也违背前端 SSE 交互体验。
- **规约方案**：
  在 `AgentOrchestrator` 中实现适配层，通过 `emitter.next(...)` 在任务拆解、Worker 派发及结果聚合等关键阶段主动发射结构化流式事件（如 `ChatEvent.newBuilder().setChunk(StreamingChunk.newBuilder().setText("【蜂群协同】正在执行意图分解..."))`），并在最终聚合收敛后发射完整的回答与 `DoneSignal`。

### 3.2 租户空间、数据权限与工具白名单隔离
- **生产基线**：`ChatRequest` 严格携带 `botId`、`workspaceId`、`userId`、`knowledgeIds` 以及 `toolMethodIds`，所有外部工具和 RAG 上下文均遵循租户强隔离。
- **现状缺口**：`AgentRegistry` 单例中预初始化的 `RAGAgent`、`SearchAgent`、`CodeAgent`（行 68-76）未绑定任何租户上下文与工具白名单。若多租户直接共享这些静态 Worker，会发生越权调用与知识库穿透漏洞。
- **规约方案**：
  严禁从 `AgentRegistry` 获取常驻 Worker；每次多智能体协同请求到达时，必须依据当前 `ChatRequest` 动态为子任务 Worker 投影有效的 `ToolCallback` 白名单与知识库绑定。

### 3.3 超时控制、并发信号量与 Token 成本硬配额
- **生产基线**：`AgentOrchestrator` 配备了 `LLM_CALL_SEMAPHORE`（最大并发 8）、`ACTIVE_PLAN_TASKS` 容量闸门以及 `DeepSeekCostGovernor`（记录 Token 用量与请求生命周期）。
- **现状缺口**：`SupervisorAgent` 内部直接调用 `chatModel.call()`，其子任务调度未挂载任何超时熔断（例如 10 秒超时门禁），亦未接入全局 `LLM_CALL_SEMAPHORE`，在复杂问题引发并发子任务激增时，将迅速耗尽后端线程与 API 连接池配额。
- **规约方案**：
  为多智能体调度主流程设定硬超时门禁（单请求整体超时 ≤ 15s），限制最大并发 Worker 数 ≤ 3，并在每次调用 `ChatModel` 前后向 `DeepSeekCostGovernor` 登记 Token 消耗。

### 3.4 异常降级与部分结果自愈 (Fallback & Fail-Open)
- **生产基线**：企业级高可用规范要求：编排层异常不得直接向前端抛出 500 堆栈，必须有稳态降级策略。
- **现状缺口**：`SupervisorAgent.chat()` 发生异常时，直接返回字符串 `"执行失败: " + getName() + " - " + e.getMessage()`（行 178），前端将直接展示错误文本。
- **规约方案**：
  在 `AgentOrchestrator` 接线 Seam 处设置安全防护伞：一旦多智能体规划失败、依赖死锁、子任务超时或抛出任何未受检异常，**立即自动降级回退到基线 SINGLE (ReactAgent) 路径**，确保用户始终能获得基础单 Agent 的兜底回复。

### 3.5 请求级 Worker 与黑板隔离（杜绝跨请求污染）
- **生产基线**：系统运行于高并发环境，多用户/多会话并行交织。
- **现状缺口**：`SupervisorAgent` 将 `SharedBlackboard` 作为成员变量（行 45），多个连续请求调用同个 Supervisor 实例时，上一轮遗留的 `BlackboardEntry` 事实将被下一轮直接读取（行 155），造成致命的数据泄漏；`AgentOrchestrator:730` 处的历史注释亦明确指出了该污染风险。
- **规约方案**：
  将 `SharedBlackboard` 改为单次 `chat()` 调用范围内的局部变量，或每次请求显式创建独立的黑板与 Worker 实例，请求结束立即丢弃并释放。

### 3.6 审计事件与存证凭单
- **生产基线**：所有关键决策（意图分解、竞标分配、质检优化）必须生成纯 Java 21 Record 不可变凭单，并通过常量时间比对验签。
- **现状缺口**：目前虽然定义了 `DualLoopQualityReceipt`，但仅缓存在内存 `Map<String, DualLoopQualityReceipt>` 中，未打通持久化与全局 Trace。
- **规约方案**：
  在多智能体完成收敛后，将最终凭单关键摘要注入 `LangFuseTracingService` 的元数据与输出元信息中，实现全链路透明存证。

---

## 四、硬编码 1536 维度法医级审计与模型生态对照

### 4.1 全局搜索统计与类清单
在 `backend/` 下对 `1536` 及 `EMBEDDING_DIMENSION` 进行全面审计，共定位到包含硬编码的类数量如下：

- **涉及硬编码 1536 的总文件数**：**48 个文件**（含算法核心类、JNI Rust 加速模块、测试用例与历史具身力学沙箱）。
- **生产核心业务模块中硬编码 1536 的核心类**：**12 个**。

核心受影响类清单如下：

| 模块位置 | 核心类名 | 关键代码行号 | 硬编码形式与影响路径 |
| :--- | :--- | :--- | :--- |
| **Hermes 多智能体** | `StructuredDebateCoordinator` | 行 113, 117 | `if (u.length != 1536 \|\| v.length != 1536) return 1.0;`。维度不符直接使测地散度恒为 1.0，导致提前退出逻辑永久失效。 |
| **Hermes 多智能体** | `MoAExpertConsensusEngine` | 行 134, 137, 139, 148, 153, 158, 172, 176 | `float[] centroid = new float[1536];`。质心计算与内积计算强行绑定 1536 维数组长度。 |
| **Hermes 多智能体** | `DefaultSphericalDyLANRoleGovernor` | 行 45, 112 | 角色动态修剪算法中硬编码 1536 维超球面投影。 |
| **Hermes 多智能体** | `DynamicSwarmEdgeRewiringGovernor` | 行 58, 120 | 蜂群动态拓扑重连权重计算强行要求 1536 维向量。 |
| **Hermes 多智能体** | `LiveAgentHotPluggingManager` | 行 76, 142 | 智能体热插拔能力评估强依赖 1536 维语义空间。 |
| **Hermes 多智能体** | `BlackboardDebateArbitrator` | 行 88, 134 | 黑板对抗仲裁器中硬编码 1536 维内积。 |
| **KMC 检索精排** | `HypersphericalRrfReranker` | 行 27, 249 | `public static final int EMBEDDING_DIM = 1536;`<br>`if (u.length != 1536) throw new IllegalArgumentException(...)`。 |
| **KMC 检索精排** | `AdaptiveHypersphericalRrfGovernor` | 行 30, 125 | `public static final int EMBEDDING_DIM = 1536;`。校验超球面约束时硬编码 1536 维。 |
| **底层向量加速** | `backend/tools/vecsim-jni/src/lib.rs` | 行 18, 50, 89 | `dot_product_1536(...)`。Rust 编写的 JNI 底层 AVX2/NEON SIMD 指令专门针对 1536 维对齐循环展开。 |
| **AI 框架共识** | `tech.qiantong.qknow.ai.consensus.core.ConsensusArbiter` | 行 18, 139, 183 | Medoid 中心点计算与诚实节点筛选强依赖 1536 维向量。 |
| **AI 框架共识** | `tech.qiantong.qknow.ai.consensus.core.ByzantineWorkerFilter` | 行 21, 28 | 拜占庭节点过滤算法使用 1536 维归一化约束。 |
| **AI 框架共识** | `tech.qiantong.qknow.ai.consensus.nash.AdaptiveJudgeArbiter` | 行 35, 92 | 纳什均衡仲裁中强行校验 1536 维。 |

### 4.2 对照真实 `text-embedding-v4` (1024 维) 生产冲突法医核实
根据阿里官方规范，通用文本嵌入模型 `text-embedding-v4` 默认输出维度为 **1024 维**（支持 768/1024/1536 可变维）。
法医核查表明：
- 若系统接入默认 1024 维度的嵌入向量，上述所有 C 类多智能体组件（`StructuredDebateCoordinator`、`MoAExpertConsensusEngine` 等）在执行到 `u.length != 1536` 时，将**直接触发致命异常或降级失效**（抛出 `IllegalArgumentException` 或使测地散度恒等为 1.0）。
- 这进一步从数学和底层向量维度证明：**绝不能直接接线上述 C 类多智能体算法**，最小接线方案必须做到与特定向量维度完全解耦（纯基于文本级或在需要向量时支持动态自适应长度 `v.length`）。

---

## 五、最小接线 Seam 与零回归架构设计

### 5.1 最小接线修改文件清单 (共 4 个文件)

为了以最小代价、最低风险且完全符合 Java 21 隔离运行环境的要求实现本阶段假设，建议且仅建议修改以下 **4 个文件**：

```
1. backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto (契约层)
2. backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/impl/KbAgentConfigServiceImpl.java (控制面桥梁)
3. backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java (核心编排中枢)
4. backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/SupervisorAgent.java (蜂群主管隔离修复)
```

### 5.2 为什么修改这 4 个文件是全局最小 Seam？
1. **修改 `hermes.proto` 的必要性**：
   在 `message ChatRequest` 中增加 `string parameters = 15;`。这是打通控制面与 Hermes 运行时参数的正式微服务契约。避免借用含义模糊的 `input_params`，保证数据流清晰明确。
2. **修改 `KbAgentConfigServiceImpl.java` 的必要性**：
   这是控制面与 Hermes 交互的唯一业务代码桥梁。在行 393 构建 `ChatRequest` 时，补充 `.setParameters(kbAgentConfig.getParameters() != null ? kbAgentConfig.getParameters() : "")`，仅需增加 1 行代码即可清偿控制面断流。
3. **修改 `AgentOrchestrator.java` 的必要性**：
   作为系统唯一的对话执行中枢，在此处解析 `parameters` 中的 `orchestrationMode`（例如 `"SINGLE"`、`"SWARM"`、`"DEBATE"`）：
   - 默认缺省或显式指定 `"SINGLE"` 时，直接执行原有的单 Agent 流水线；
   - 仅当明确配置了 `"SWARM"` 且提问为复杂问题时，按需构造隔离的执行上下文，调度 `SupervisorAgent`，并在异常或超时时无缝回退回单 Agent；
   - 此处是实现“延迟 ≤3×、成本 ≤3×、默认零回归”的核心看门狗。
4. **修改 `SupervisorAgent.java` 的必要性**：
   清偿前述“跨请求黑板污染”漏洞：将 `SharedBlackboard` 从类成员变量改为请求级创建，支持向构造方法或 `chat()` 动态传入每次独立的黑板与 Worker 列表。

### 5.3 明确严禁修改的文件边界 (What NOT to Touch)
1. **无需修改 `KbConversationController.java`**：
   行 108 已经正确执行了 `agentReq.setParameters(config.getParameters())`，控制层契约已就绪。
2. **无需修改 `KbAgentConfigDO.java` 与数据库 Schema**：
   实体类已包含 `parameters` 字段，数据库 `kb_agent_config` 表已有对应存储字段，无需执行任何 DDL 或修改实体。
3. **无需修改 `HermesGrpcService.java`**：
   该类为通用转发层（行 42 纯透传调用 `agentOrchestrator.chat(effectiveRequest)`），不需要任何改动。
4. **严禁修改和接入任何 C 类组件**：
   严禁触碰 `StructuredDebateCoordinator.java`、`MoAExpertConsensusEngine.java`、`DefaultConsensusArbiterEngine.java`、`TripartiteDebateArbiterEngine.java`。

### 5.4 Baseline SINGLE 零回归的完整论证
```java
// AgentOrchestrator.java 内部的最小接线分支逻辑设计原型：
String orchestrationMode = parseOrchestrationMode(request.getParameters());
if ("SWARM".equalsIgnoreCase(orchestrationMode) && isComplexQuestion(request.getQuestion())) {
    try {
        return executeSwarmFlow(request, emitter, ...);
    } catch (Exception e) {
        log.warn("SWARM 编排失败，无缝回退至稳态 SINGLE 路径", e);
        // Fallback 到原有单 Agent 逻辑
    }
}
// 默认 / 降级基线执行路径（既有 100% 原始代码逻辑完全不变）
executeSingleReactAgent(request, emitter, ...);
```
- **参数控制与默认关闭**：数据库中现存所有 Bot 的 `parameters` 默认均为空或未配置 `orchestrationMode`，解析结果一律判定为 `"SINGLE"`。
- **纯粹向后兼容**：在未命中 `"SWARM"` 或出现任何执行异常时，控制流直接无条件穿透至原有单 Agent 逻辑，原有的 `ReactAgent`、`ReActCycleGuard`、Token 统计与 LangFuse 链路毫厘不差，100% 满足**基线零回归 (Zero-Regression)**。

---

## 六、集成与回归测试验证清单

为了科学、可复现地验证接线效果并保障系统稳健性，必须建立以下 7 项契约测试：

| 测试用例 ID | 测试项名称 | 验证目标与输入条件 | 预期断言与判定标准 |
| :---: | :--- | :--- | :--- |
| **TEST-01** | **默认 SINGLE 基线测试** | `parameters` 为空、未配置或显式配置 `{"orchestrationMode":"SINGLE"}`。 | 系统严格走原有单 ReactAgent 路径，执行耗时与 Token 开销与历史基线 100% 对齐。 |
| **TEST-02** | **未知模式安全降级测试** | `parameters` 配置未知非法模式（如 `{"orchestrationMode":"UNKNOWN_MODE_XYZ"}`）。 | 系统静默识别并记录告警日志，自动降级为默认 SINGLE 模式，回答成功且无报错。 |
| **TEST-03** | **SWARM 复杂问题正常协同测试** | `parameters` 配置 `{"orchestrationMode":"SWARM"}`，输入包含多子步骤的复杂分析问题。 | `HierarchicalTaskPlanner` 成功拆解为多阶段 DAG，Worker 独立执行并由 Supervisor 汇总，产出高质量结构化答复。 |
| **TEST-04** | **超时与异常无缝回退测试** | 在 SWARM 阶段注入模拟延迟（超时 > 10s）或规划抛错。 | 系统触发 Fail-Open 熔断机制，在规定时间内无缝回退至 SINGLE 模式生成保底回答，无 500 异常暴露。 |
| **TEST-05** | **租户与工具白名单穿透防护测试** | 配置 SWARM 模式并指定特定的 `toolMethodIds` 与 `knowledgeIds`。 | 验证派生的所有 WorkerAgent 仅能访问该白名单内的工具与切片，严禁越权调用未授权工具。 |
| **TEST-06** | **跨请求黑板状态隔离测试** | 连续并发发起两个不同会话的 SWARM 请求（Session A 与 Session B）。 | 校验 Session A 写入黑板的事实在 Session B 执行期间不可见，黑板生命周期与请求严格对齐。 |
| **TEST-07** | **流式事件顺序与完整性测试** | 监控 SWARM 模式下的 gRPC `stream ChatEvent` 输出。 | 检验事件流必须按照 `StreamingChunk(思考/步骤状态) -> StreamingChunk(最终文本) -> ModelFinished -> DoneSignal` 严格有序发射，前端打字机无假死。 |

---

## 七、被否决方案与理由（防范换名重提）

在多智能体系统演进中，针对以下曾经探讨或代码库中残留的方案，本审计予以**永久性否决**，严禁后续以“重构”或“别名包装”重新引入：

### 7.1 坚决否决：直接接入 `MoAExpertConsensusEngine`
- **被否决原因**：
  1. 内部严重锁死 1536 维超球面单位向量（行 134-165 共 8 处硬编码），无法兼容项目通用 1024 维 `text-embedding-v4`；
  2. 其“一票否决”测地散度门限（0.45）为纯经验静态值，极易引发专家委员会与独立事实的高频误杀拒答；
  3. 内部 `MoAExpert` 和 `FactRetrievalService` 为空接口，缺乏真实的知识库 RAG 与 DeepSeek API 支撑。

### 7.2 坚决否决：直接接入 `StructuredDebateCoordinator`
- **被否决原因**：
  1. 行 113 硬编码 `u.length != 1536` 则直接返回散度 1.0，脱离千问 1536 空间后提前收敛研判永久失效；
  2. 行 100 采用 `maxTokenQuota * 3.5` 暴力截断字符串，会破坏大模型输出的 JSON 结构与 Markdown 语法；
  3. 缺乏真实模型接入与动态租户安全上下文。

### 7.3 坚决否决：直接接入 `DefaultConsensusArbiterEngine`
- **被否决原因**：
  1. 置信度评分函数 `evaluatePropositionConfidence`（行 96-109）完全基于 `contains("证明")` 等机械关键词与长度加分，不存在任何大语言模型的语义理解与逻辑裁决；
  2. 属于典型的“形式化共识”与伪算法，上线将彻底违背真实科研与工程法典。

### 7.4 坚决否决：直接接入 `TripartiteDebateArbiterEngine`
- **被否决原因**：
  1. 行 61, 78, 95 采用 `"sha256_mock_qwen_1536_p" + round` 字符串拼接生成伪造 Hash 凭单；
  2. 行 67 采用循环计数器 `unaddressedRisks / activeRisks.size()` 模拟所谓博弈收敛，完全是单测打桩代码；
  3. 接入生产将输出固定的测试模板文本，造成重大业务生产事故。

---

## 八、法医审计结论与实施前置判定

1. **报告与存证凭据绝对路径**：
   - 报告文件：`/Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/p015/research/B-code-audit.md`
   - 凭单文件：`/Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/p015/research/B-code-audit.evidence.jsonl`
2. **生产调用点审计结果**：
   - 现有多智能体核心类（`SupervisorAgent`、`StructuredDebateCoordinator`、`MoAExpertConsensusEngine` 等）生产调用点数量为 **0**；系统主干百分之百运行于单 Agent（`ReactAgent`）流式链路。
3. **硬编码 1536 审计结果**：
   - 全局共发现 48 个涉及 1536 的源码文件，其中生产核心相关类 **12 个**，必须在接线设计中严格解耦，杜绝维度锁死。
4. **最小 Seam 建议清单**：
   - 仅建议修改 **4 个文件**：`hermes.proto`、`KbAgentConfigServiceImpl.java`、`AgentOrchestrator.java`、`SupervisorAgent.java`。
5. **实施阻塞判定**：
   - **不存在代码结构层面的死锁阻塞**；只要严格遵循最小 Seam、设立自动降级为 SINGLE 的兜底守卫、并完全隔离 C 类伪仿真组件，即可安全获准进入后续实施契约设计阶段。
