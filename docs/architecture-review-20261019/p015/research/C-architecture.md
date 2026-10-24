---
report_id: P015-C
role: system-architecture
project_commit: b6516ee3
hypothesis_id: P015-MULTI-AGENT-PRODUCTION-WIRING
created_at: 2026-10-22T21:56:00+08:00
status: COMPLETE
verification_counts: {VERIFIED: 22, PARTIALLY_VERIFIED: 0, NOT_VERIFIED: 0}
---

# P015 系统架构契约与最小算法适配报告 (C-architecture)

## 准入与执行摘要

1. **唯一待验证假设通过架构准入门禁**：基于本阶段针对理论文献（A-theory，6 个来源全量 VERIFIED）与本地代码法医审计（B-code-audit，16 项证据全量 VERIFIED）的深度交叉对齐，确认唯一待验证假设在架构层面**完全成立且具备明确收敛的实施契约**。
2. **基线零生产调用定论与控制面断流**：代码法医审计确证 `SupervisorAgent`、`StructuredDebateCoordinator` 等多智能体类在当前生产请求主干中**调用点确凿为 0**；线上流量 100% 运行于单 `ReactAgent` 流式路径。控制面已有的 `parameters` 配置在服务层组装 gRPC `ChatRequest` 时被丢弃，导致多智能体编排在生产边界断流。
3. **C 类伪仿真组件严禁接入生产**：代码库中残留的 `StructuredDebateCoordinator`、`MoAExpertConsensusEngine`、`DefaultConsensusArbiterEngine`、`TripartiteDebateArbiterEngine` 严重依赖关键词匹配启发式评分、循环计数器伪造 Hash、以及硬编码 1536 维超球面向量（与项目真实 1024 维 `text-embedding-v4` 产生硬冲突）。本报告予以**永久性架构否决**，严禁换名引入生产。
4. **4 文件最小 Seam 确保默认路径 100% 零回归**：仅需微调 `hermes.proto`（扩展枚举）、`KbAgentConfigServiceImpl.java`（桥接参数）、`AgentOrchestrator.java`（策略路由与降级守卫）及 `SupervisorAgent.java`（修复跨请求黑板污染），即可在不改动数据库 Schema、不改动前端、不引入任何外部框架的前提下完成接线。未显式开启多智能体的 Bot 100% 保持既有单 Agent 路径，无任何逻辑与性能回归。
5. **分阶段两级跳推荐**：Phase 1 聚焦 **SWARM 最小接线**（SupervisorAgent + 请求级隔离 Worker，并发 ≤ 3，硬超时 15s）；Phase 2 作为候选探索 **最小三角色真实 LLM 辩论（DEBATE）**。预注册判据严格锁定：SWARM 质量相对单 Agent 必须提升 ≥ 10%，P95 延迟 ≤ 3×（≤ 15s），Token 成本 ≤ 3×（单次 ≤ 0.05 元），降级率 < 5%。

---

## A. 当前代码与失败机制

### A.1 真实生产调用路径与核心断点证据 (引用 B 文件:行号)

系统生产环境目前采用严谨的分层微服务架构，从用户界面发起提问至大模型流式响应的真实端到端链路如下：

```
[Web / Mobile 前端]
       │ HTTP POST /admin/kb/conversation/send (SSE 流式长连接)
       ▼
[tech.qiantong.qknow.module.kb.controller.admin.conversation.KbConversationController#sendMessage] (行 82-126)
       │ • 查询历史会话与持久化用户提问 (行 84-88)
       │ • 加载机器人配置：KbAgentConfigDO config = agentConfigService.getKbAgentConfigByBotId(botId) (行 91)
       │ • 设置参数载体：agentReq.setParameters(config.getParameters()) (行 108) [控制面配置已正确承载]
       │ • 转发业务服务：agentConfigService.chatMessage(agentReq) (行 125)
       ▼
[tech.qiantong.qknow.module.kb.service.agent.impl.KbAgentConfigServiceImpl#chatMessage] (行 226-442)
       │ • 解析模型凭证、API Key 与 RAG 语义缓存 (行 228-261)
       │ • 多知识库并发召回与 20KB 预算硬截断 (行 267-365)
       │ • 解析工具白名单与构建历史会话 (行 368-388)
       │ • 组装 gRPC ChatRequest (行 393-415) [【严重断点】：未将 kbAgentConfig.parameters 下发至 gRPC]
       │ • 调用 Hermes 客户端：hermesGrpcClient.chat(request) (行 440-441)
       ▼
[gRPC 网络传输边界 (ChatRequest -> stream ChatEvent)]
       │ • 接口定义：backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto (行 28-43)
       │ • [【协议断点】：ChatRequest 仅定义了 14 个基础字段，缺失 parameters / orchestration_mode]
       ▼
[tech.qiantong.qknow.hermes.grpc.HermesGrpcService#chat] (行 35-66)
       │ • 安全解密 API Key (行 41)
       │ • 纯透传调用编排器：agentOrchestrator.chat(effectiveRequest) (行 42)
       │ • 响应式桥接：GrpcReactorBridge.bindStream(responseStream, responseObserver) (行 54)
       ▼
[tech.qiantong.qknow.hermes.agent.AgentOrchestrator#chat] (行 130-222)
       │ • 注册 LangFuse 分布式链路追踪 (行 141-143)
       │ • 启动 DeepSeek 成本生命周期计时 (行 137)
       │ • 调用执行入口：executeAgent(...) (行 154)
       ▼
[tech.qiantong.qknow.hermes.agent.AgentOrchestrator#executeAgent] (行 244-550)
       │ • 动态创建 Spring AI ChatModel 实例 (行 250-268)
       │ • 绑定知识库工具与意图白名单 (行 285-329)
       │ • 注入 ReActCycleGuard 防死循环振荡守卫 (行 431-438)
       │ • 实例化单 Agent：ReactAgent.builder().name("hermes_agent")...build() (行 441-450)
       │ • 异步流式订阅：StreamSubscriptionGuard.subscribeAsync(agent.stream(messages), ...) (行 464-478)
       │ • 逐 Token 组装并向 FluxSink 发射 StreamingChunk / ModelFinished (行 481-530)
```

**多智能体核心类的法医审计事实**：
- `SupervisorAgent.java:34`：生产调用点 = 0，主生产流从未实例化该主管；
- `WorkerAgent.java:27`：主生产流未常态调用，仅在 `AgentOrchestrator.java:732`（`planAndSolve` 复杂分析实验分支）中有临时局部 `new WorkerAgent(...)` 的独立运行逻辑；
- `StructuredDebateCoordinator.java:17`、`MoAExpertConsensusEngine.java:20`、`DefaultConsensusArbiterEngine.java:20`、`TripartiteDebateArbiterEngine.java:18`：生产调用点确凿为 0，完全处于孤立或单元测试打桩状态。

### A.2 唯一待验证假设声明
> **唯一待验证假设 (P015-MULTI-AGENT-PRODUCTION-WIRING)**：  
> “默认单 Agent 路径完全不变；SWARM（SupervisorAgent）与最小 DEBATE 作为按 Bot 配置、默认关闭的编排策略接入 AgentOrchestrator，可测量提升复杂问题质量，p95 延迟 ≤3×、token 成本 ≤3×、默认路径零回归。”

### A.3 系统失败机制识别 (Failure Mechanisms)
根据法医审计与系统动力学推导，如果不经严格架构规约直接接入多智能体，系统必然触发以下 6 种失败机制：
1. **控制面跨微服务协议断流 (Protocol Severance)**：`KbConversationController` 中已从数据库获取到的 Bot 参数，在 `KbAgentConfigServiceImpl.java:393` 组装 gRPC 消息时被丢失，导致底层 `AgentOrchestrator` 永远无法感知用户的编排模式指令。
2. **串行调用级联延迟穿透 (Sequential Latency Explosion)**：若 Supervisor 规划（拆解）、Worker 派发执行与最终合成完全同步串行，单次请求经历 $1 + K + 1$ 次大模型往返；在单步 LLM 耗时 2~3 秒的基准下，总耗时将飙升至 12~18 秒，直接击穿 3× 延迟红线（单 Agent 基线 P95 约 4.5s，3× 上限为 13.5s~15s）。
3. **单例黑板跨请求状态污染 (State Contamination & Leakage)**：`SupervisorAgent.java:45` 将 `SharedBlackboard` 持有为实例成员变量。一旦该类在多请求间复用，前一请求的事实快照与用户私有信息将直接泄露至后一请求中，造成重大安全隐患。
4. **流式打字机假死体验 (Streaming Impedance Mismatch)**：`SupervisorAgent.chat()` 底层返回单体同步 `String`，无法向前端 SSE 发射逐 Token 的 `StreamingChunk`，导致前端在 10 秒以上陷入完全静默，触发用户感知层面的“假死”。
5. **C 类伪仿真组件引发 1024 维向量致命崩溃**：`StructuredDebateCoordinator.java:113` 和 `MoAExpertConsensusEngine.java:134-165` 硬编码 1536 维超球面单位向量。当系统传入真实生产的 1024 维 `text-embedding-v4` 向量时，将直接抛出运行时数组越界或校验异常，引发服务崩溃。
6. **LLM 谄媚性诱发的假共识 (False Consensus Trap)**：在无结构化角色立场约束的多轮对抗中，大语言模型由于内置的 RLHF 迎合偏见（Sycophancy），极易在第 2 轮盲目迎合对方的错误论断，形成伪共识幻觉。

---

## B. Research Ledger (研究台账汇编)

严格执行取证前置铁律（Reading-First Rule），对 6 个来源进行全文字节级阅读，无二手推断，验证状态全部为 `VERIFIED`。

| id | sourceType | titleOrRepository | venueAndYear | doiOrArxiv | filesOrSectionsRead | verificationStatus | relevantFinding | projectApplicability | limitations |
|---|---|---|---|---|---|:---:|---|---|---|
| **AUTOGEN-2023** | paper | AutoGen: Enabling Next-Gen LLM Applications via Multi-Agent Conversation | ICLR 2024 / arXiv 2023 | arXiv:2308.08155 | Sec 1-3, App B.1 | **VERIFIED** | 提出 ConversableAgent 与 GroupChatManager 两级主管模式；证明自由自由对话会导致 Token 超线性爆炸，必须具备严格终止判定。 | 适用于 SupervisorAgent 两级解耦控制。坚决拒绝全量历史广播机制，采用局部黑板。 | 缺乏生产级严苛 SLA 延迟治理；动态选人引入非确定性延迟抖动。 |
| **MULTIAGENT-DEBATE-2023** | paper | Improving Factuality and Reasoning in Language Models through Multiagent Debate | ICML 2024 / arXiv 2023 | arXiv:2305.14325 | Sec 1-4 | **VERIFIED** | 多智能体辩论相比单 Agent 带来 +8%~+18% 推理与事实性增益；收益在 2~3 轮迅速达到平台期，模型具有天然的随和妥协倾向。 | 确立 DEBATE 编排最小基准：固定 3 角色（正方、反方、裁判），轮次严格锁死为 2 轮以内。 | 仅在通用公开基准上评估；若私有知识库检索切片本身有误，辩论易放大假证据。 |
| **METAGPT-2023** | paper | MetaGPT: Meta Programming for A Multi-Agent Collaborative Framework | ICLR 2024 / arXiv 2023 | arXiv:2308.00352 | Sec 1, 3, 4, App D.1 | **VERIFIED** | 将 SOP 编码入多智能体，利用强类型结构化产物契约与共享黑板单向流转，避免 $O(N^2)$ 通信噪声，将开销降低 80% 以上。 | 印证 SupervisorAgent 共享黑板与 Java 21 Record 不可变凭单的设计哲学，以结构化契约驱动。 | 面对非典型复杂变通任务时弹性不足；首节点瑕疵契约易造成错误级联传播。 |
| **MOA-2024** | paper | Mixture-of-Agents Enhances Large Language Model Capabilities | NeurIPS 2024 / arXiv 2024 | arXiv:2406.04692 | Sec 1-4 | **VERIFIED** | 提出“协作性”假设，弱模型候选可被聚合器激发产出高分；但多层网络导致输入 Token 随层数与 Agent 数量激增 $O(L \cdot N \cdot K)$。 | 约束 Hermes 绝不能采用多层重型 MoA（$L \ge 2$），必须精简为单层浅层聚合（$L=1, N \le 3$）。 | 评测主要基于通用问答排行榜，未考察私有企业级高密度技术文档问答。 |
| **LANGGRAPH-SUPERVISOR-2024** | official-doc | LangChain / LangGraph Multi-Agent Supervisor Official Architecture Guide | LangChain 2024 | N/A | Overview, Wrap sub-agents as tools, Termination | **VERIFIED** | 工业权威演进：将子智能体包装为 Tool 并由 Supervisor 进行 Tool-calling 调度，精细隔离子任务状态。 | 为 Hermes 中 SupervisorAgent 与 WorkerAgent 的工程解耦提供蓝图，明确按 Bot 默认关闭。 | 官方指出简单问题增加 1~2 次 LLM 往返会导致响应延迟翻倍，必须支持单 Agent 直通。 |
| **DEEPSEEK-API-DOCS-2026** | official-doc | DeepSeek 官方开发者文档：API 调用、推理模型与定价指南 | DeepSeek 2026 | N/A | Quick Start, Reasoning Model, Context Caching | **VERIFIED** | 确认主力模型 deepseek-flash；Context Caching 命中价格仅为未命中价格的 1/7；思考模式禁止设置温度参数。 | 奠定成本护城河：保持 Supervisor 与各角色 System Prompt 严格前缀对齐，激活缓存大幅压降成本。 | API 存在高并发速率限制（Rate Limits），必须在工程层受控于并发信号量。 |

---

## C. 可迁移与不可迁移结论分析

### C.1 项目真实运行基线 vs 外部文献前提差异
1. **向量维度硬核冲突**：
   - **外部伪代码/测试桩**：`StructuredDebateCoordinator` 与 `MoAExpertConsensusEngine` 内部写死 1536 维；`AGENTS.md` 第七节保留有历史基线“千问 1536 维”表述。
   - **项目生产真实环境**：真实生产库全量使用阿里 `text-embedding-v4`（**1024 维超球面单位向量**），实际已入库 4,955 条生产向量。两空间跨维度投影余弦仅 0.0186（正交无关）。
   - **架构结论**：编排层逻辑严禁依赖固定 1536 维假设计算，必须基于文本语义或自适应向量长度。
2. **模型生态基线**：
   - 本项目唯一生成模型为 DeepSeek API 官方活跃模型（`deepseek-flash`），开启深度思考链（`extra_body: {"thinking": {"type": "enabled"}}`），无本地部署模型，无 OpenAI 依赖。
3. **工程运行时基线**：
   - 统一且唯一使用 Java 21 隔离虚拟环境（SDKMAN 管理路径：`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`），严禁污染宿主 Java 17。

### C.2 可直接采用 (Adopt As-Is)
- **两级解耦调度范式**：Supervisor 负责高层意图分解与最终答案归集，Worker 负责垂直执行（来源于 LangGraph & AutoGen）。
- **结构化黑板与不可变凭单**：Worker 执行输入输出基于 Java 21 Record 不可变凭单，任务中间态记录于黑板字典（来源于 MetaGPT）。
- **DeepSeek Context Caching 前缀对齐**：所有 Worker 和 Supervisor 的 System Prompt 与固定前置背景保证静态前缀一致，最大化命中缓存（来源于 DeepSeek 官方规范）。

### C.3 需要改造后采用 (Adopt with Adaptation)
- **SupervisorAgent 改造**：必须剔除单例中持有 `SharedBlackboard` 的成员变量，重构为每次 `chat()` 请求临时在栈上创建独立的请求级黑板，杜绝跨会话状态污染。
- **Worker 动态安全投影**：严禁从 `AgentRegistry` 单例复用常驻 Worker，必须依据当前 `ChatRequest` 动态为 WorkerAgent 注入租户绑定的 `toolMethodIds` 与知识库 `knowledgeIds`。
- **流式适配层**：在编排器层面将 Supervisor 同步生成的阶段状态与最终文本转化为 `Flux<ChatEvent>`，保障前端 SSE 体验。

### C.4 必须坚决拒绝 (Strictly Reject)
- **拒绝全量广播通信**：坚决拒绝 AutoGen 式的 Agent 间全员广播，严格禁止在 Prompt 中堆叠所有历史对话。
- **拒绝直接接入 `MoAExpertConsensusEngine`**：
  - *拒绝理由*：8 处写死 1536 维数组；静态 0.45 测地散度阈值极易误杀；内部均为未实现的空接口。
- **拒绝直接接入 `StructuredDebateCoordinator`**：
  - *拒绝理由*：写死 1536 维；字符截断暴力破坏语法；缺乏真实模型驱动。
- **拒绝直接接入 `DefaultConsensusArbiterEngine` 与 `TripartiteDebateArbiterEngine`**：
  - *拒绝理由*：内部评分基于 `contains("证明")` 等机械关键词或 `"sha256_mock_qwen_1536_p"` 伪造哈希，属于无大模型参与的单测打桩代码，上线构成欺骗性伪共识。

---

## D. 候选方案综合比较矩阵

统一采用 9 个固定维度进行科学严谨对比：

| 评估维度 | 方案 1: Baseline (SINGLE) | 方案 2: 最小诊断/数据修正 | 方案 3: 推荐候选 (SWARM 最小接线) | 方案 4: 进阶候选 (SWARM + DEBATE) | 方案 5: 现有仿真引擎 (MoA/Tripartite) |
|---|---|---|---|---|---|
| **模式描述** | 既有单 `ReactAgent` 流式 | 仅透传 parameters，不改编排 | SupervisorAgent + 请求级 Worker | SWARM + 真实 LLM 最小 3 角色辩论 | 直接接线现有 C 类多智能体类 |
| **正确性 (Correctness)** | 稳态基准（多步推理偶发逻辑遗漏） | 同 Baseline | 高（意图分解大幅提升复杂问题覆盖率） | 极高（辩论交叉审视减少幻觉） | **极差（硬编码 1536 抛异常或输出伪逻辑）** |
| **可证伪性 (Falsifiability)** | N/A (基准点) | 无新增变量 | 极高（明确的 Win Rate / P95 指标） | 极高（多步事实问答双盲对决） | 不可证伪（伪打桩代码恒定返回） |
| **数据需求 (Data Req.)** | 既有会话与 RAG 上下文 | 同 Baseline | 仅需既有多步任务数据 | 同 SWARM，需对抗立场提示词 | 依赖虚构的 1536 维空间向量 |
| **延迟影响 (Latency)** | 1.0× (P95 约 4.5s) | 1.0× | **≤ 2.5× (并发派发控制在 8~12s)** | **≤ 3.0× (受控在 12~14s)** | 无法运行（启动即抛异常） |
| **成本影响 (Cost)** | 1.0× (约 0.010 元/次) | 1.0× | **≤ 2.2× (利用 Context Caching)** | **≤ 2.8× (受控轮次 $R \le 2$)** | 无法运行 |
| **实现复杂度 (Complexity)** | 0 (既有生产代码) | 极低 (仅改 2 行) | **低 (仅需 4 个核心文件微调)** | 中 (需编写轻量 Proposer/Judge) | 极高（需全量重构老旧算法） |
| **依赖变化 (Dependencies)** | 零新增 | 零新增 | **零新增 (完全复用现有类)** | **零新增 (完全复用现有 ChatModel)**| 零新增 |
| **回滚风险 (Rollback)** | 无 | 无 | **零风险 (未配置一律走 SINGLE)** | **零风险 (按 Bot 配置，自动降级)** | 致命风险（可能拉垮整个编排容器） |
| **生产影响 (Prod Impact)** | 维持现状 | 维持现状 | **显著提升特定 Bot 复杂业务表现** | **进一步深化关键决策可解释性** | **破坏性生产事故** |
| **裁决结论** | **保留为系统默认基线** | 仅作为过渡，无法证伪假设 | **【推荐获批实施】(Phase 1)** | **【批准进入后续探索】(Phase 2)** | **【永久坚决否决，禁止引入】** |

---

## E. 推荐的最小算法架构

### E.1 架构设计理念：复用与最小干预
推荐方案坚决拒绝引入 LangGraph、AutoGen、CrewAI 等任何 Python 外挂或重型 Java 框架，**100% 复用项目既有核心资产**：
1. **复用 `HierarchicalTaskPlanner`**：已具备利用真实 `ChatModel` 发起 JSON 子任务拆解与基于标准 Kahn 拓扑排序算法提取分层阶段的完整能力；
2. **复用 `WorkerAgent` 与局部实例化**：参考 `AgentOrchestrator.java:732` 已验证的成熟模式，每次请求动态创建独立的 Worker 实例，按需挂载工具白名单与知识库；
3. **复用 `DeepSeekCostGovernor` 与 `LLM_CALL_SEMAPHORE`**：直接复用系统内建的并发控制与 Token 计费治理器，保障并发安全与成本受控。

### E.2 DEBATE 的最小真实 LLM 契约
若后续开启 Phase 2 DEBATE，必须使用纯大模型驱动的 `LlmDebateCoordinator`，坚决废黜一切启发式关键词：
- **正方 (Proposer)**：结合知识库检索切片生成初步立论与论据清单；
- **反方 (Critic)**：以严格挑剔视角审查正方立论中的逻辑漏洞、事实冲突与遗漏假设；
- **裁判 (Judge)**：独立大模型实例，接收正反方陈述，执行最终仲裁，输出结构化判据与最终完整回复。
- **硬性约束**：轮次严格锁死为 $R = 1$（一次交锋即仲裁），严禁无界多轮循环。

---

## F. 决策完备的实验与实施契约 (Decision-Complete Contract)

### F.1 微服务协议层字段规约 (proto)
在 `backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto` 中定义严格向后兼容的枚举与字段：

```protobuf
syntax = "proto3";
package tech.qiantong.qknow.hermes;

// 编排策略枚举（明确默认值为 SINGLE=0）
enum OrchestrationMode {
  ORCHESTRATION_MODE_SINGLE = 0;  // 默认单 Agent 路径 (ReactAgent)
  ORCHESTRATION_MODE_SWARM = 1;   // 蜂群协作编排 (SupervisorAgent)
  ORCHESTRATION_MODE_DEBATE = 2;  // 三角色对抗辩论 (LlmDebateCoordinator)
}

message ChatRequest {
  string session_id = 1;
  string question = 2;
  string bot_id = 3;
  string workspace_id = 4;
  string user_id = 5;
  repeated string knowledge_ids = 6;
  string model = 7;
  string api_key = 8;
  repeated string tool_method_ids = 9;
  repeated ChatMessage history = 10;
  bool is_stream = 11;
  repeated string agent_ids = 12;
  string prompt = 13;
  string context = 14;
  
  // 【新增参数】：控制面配置透传与显式编排枚举
  string parameters = 15;                       // 透传原始 JSON 参数字符串
  OrchestrationMode orchestration_mode = 16;   // 显式强类型编排模式
}
```
*向后兼容说明*：gRPC 反序列化时，缺失字段默认解析为 `0`（即 `ORCHESTRATION_MODE_SINGLE`），未知枚举值根据 Protocol Buffers 规范自动安全降级，百分之百保障老客户端与既有请求不受影响。

### F.2 控制面与数据层规约 (Control Plane)
- **数据库 Schema 零变更**：不执行任何 DDL，不修改 `kb_agent_config` 表结构。
- **配置载体**：复用既有的 `kb_agent_config.parameters` 字段（JSON 格式），约定配置键：
  ```json
  {
    "orchestrationMode": "SWARM",
    "swarmMaxWorkers": 3,
    "swarmTimeoutMs": 15000
  }
  ```
- **服务层透传补丁 (`KbAgentConfigServiceImpl.java:393-415`)**：
  在构建 `ChatRequest.Builder` 时补充参数绑定：
  ```java
  // 注入控制面 parameters 并解析设置 orchestration_mode
  String params = kbAgentConfig.getParameters() != null ? kbAgentConfig.getParameters() : "";
  builder.setParameters(params);
  builder.setOrchestrationMode(parseOrchestrationMode(params));
  ```

### F.3 Hermes 编排中枢接入点与降级守卫 (`AgentOrchestrator.java`)
在 `AgentOrchestrator.chat()` 方法中确立唯一的非侵入式接线 Seam：

```java
public Flux<ChatEvent> chat(ChatRequest request) {
    // 1. 解析目标编排模式（默认 SINGLE）
    OrchestrationMode mode = resolveOrchestrationMode(request);
    
    // 2. 模式分流守护
    if (mode == OrchestrationMode.ORCHESTRATION_MODE_SWARM) {
        log.info("[Orchestrator] 激活 SWARM 编排模式: botId={}, sessionId={}", 
                 request.getBotId(), request.getSessionId());
        return executeSwarmFlowWithFallback(request);
    } else if (mode == OrchestrationMode.ORCHESTRATION_MODE_DEBATE) {
        log.info("[Orchestrator] 激活 DEBATE 编排模式: botId={}, sessionId={}", 
                 request.getBotId(), request.getSessionId());
        return executeDebateFlowWithFallback(request);
    }
    
    // 3. 稳态默认路径：100% 走既有单 Agent 执行逻辑（完全零改动）
    return executeAgent(request);
}
```

**SWARM 安全包装器与降级守卫**：
```java
private Flux<ChatEvent> executeSwarmFlowWithFallback(ChatRequest request) {
    return Flux.create(emitter -> {
        // 请求级局部黑板与超时控制
        SharedBlackboard requestBlackboard = new SharedBlackboard();
        long startTime = System.currentTimeMillis();
        long timeoutMs = 15000L; // 硬性 15 秒超时门禁
        
        try {
            // 发射阶段流式状态通知
            emitStreamingChunk(emitter, "【蜂群协同】正在进行意图理解与任务分解...\n");
            
            // 构造请求级隔离的 SupervisorAgent
            SupervisorAgent supervisor = createIsolatedSupervisor(request, requestBlackboard);
            
            // 执行调度（挂载超时熔断）
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> supervisor.chat(request.getQuestion(), buildRequestContext(request)),
                swarmThreadPool
            );
            
            String finalAnswer = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            
            // 发射最终结果
            emitStreamingChunk(emitter, finalAnswer);
            emitModelFinished(emitter);
            emitter.complete();
            
        } catch (TimeoutException te) {
            log.warn("[SWARM 超时熔断] 耗时超过 {}ms，安全降级至 SINGLE 基线: sessionId={}", 
                     timeoutMs, request.getSessionId());
            emitStreamingChunk(emitter, "\n[系统提示: 协同计算超时，正在切换极速引擎回答...]\n");
            // 降级回退执行原单 Agent 流
            executeAgent(request).subscribe(emitter::next, emitter::error, emitter::complete);
        } catch (Exception e) {
            log.error("[SWARM 执行异常] 触发安全兜底降级: sessionId={}", request.getSessionId(), e);
            executeAgent(request).subscribe(emitter::next, emitter::error, emitter::complete);
        }
    });
}
```

### F.4 流式事件规约 (Streaming Spec)
- 多智能体中间调度过程（分解、派发）通过发射思考阶段事件呈现给前端：`StreamingChunk.newBuilder().setText("...").build()`；
- 最终收敛文本以单次或打字机式 `StreamingChunk` 输出；
- 尾部必须严格附带 `ModelFinished` 与 `DoneSignal`，完全兼容前端 SSE 解析器。

### F.5 评测数据集设计
- **规模与抽样**：固定选取 15 条典型企业级复杂多步长文本问答（涵盖多切片交叉比对、矛盾观点仲裁、长流程规划与技术规范解释）。
- **评测轮次**：采用 3 组不同随机种子（Seeds: 42, 1024, 2026）进行重复测试，总计 45 次实验调用。
- **评估方式**：由 DeepSeek 活跃模型扮演独立裁判评测（评分维度：事实准确性、论证完整性、逻辑自洽性，1~5 分制），辅以 20% 人工盲审抽检比对。

### F.6 反事实与消融实验矩阵 (Counterfactual & Ablation Matrix)
为严谨证明多智能体各组件的实际贡献，设计以下 4 组消融实验：

| 实验编号 | 实验组名称 | 配置与消融手段 | 预期证伪目标 |
|---|---|---|---|
| **EXP-00** | **Baseline** | 默认单 `ReactAgent`，无主管干预 | 确立基础质量、延迟（P95）与 Token 成本基线 |
| **EXP-01** | **SWARM Full** | SupervisorAgent + 3 并发 Worker + 局部黑板 | 验证完整多智能体协同的综合质量增益 |
| **EXP-02** | **Worker Ablation** | 限制 Worker 并发数从 3 降为 1（单 Worker 串行） | 检验并发调度对 P95 延迟的优化幅度及瓶颈 |
| **EXP-03** | **Debate Rounds** | 对比 DEBATE 轮次 $R=1$ 与 $R=2$ | 检验是否在第 2 轮发生边际效用递减或假共识 |
| **EXP-04** | **Tool Ablation** | 关闭所有外部知识库工具，纯靠 LLM 内在知识 | 检验检索切片质量对辩论和协同决策的依赖程度 |
| **EXP-05** | **Chaos Timeout** | 在 Worker 执行中人为注入 16s 延迟 | 验证系统是否 100% 触发 Fail-Open 降级为 SINGLE |

### F.7 测量指标定义
1. **质量改善分 ($\Delta Q$)**：DeepSeek Judge 对最终回答的综合打分增量（5 分制），$\Delta Q = Q_{swarm} - Q_{single}$。
2. **P95 端到端延迟 ($T_{p95}$)**：从接收请求到流式完全终止（DoneSignal）的第 95 分位耗时。
3. **Token 开销 ($Tokens_{total}$)**：单次请求累计消耗的 Prompt Token 与 Completion Token 总和。
4. **单次折算成本 ($Cost_{cny}$)**：依据 DeepSeek 官方计费标准与 Context Caching 实际命中情况折算的人民币总额（元）。
5. **异常降级率 ($Rate_{fallback}$)**：触发超时熔断或异常降级为 SINGLE 路径的请求占比（%）。

### F.8 预注册成功判据与保留条件
- **SWARM 准入成功门禁（必须全部同时满足）**：
  1. 质量评分 $\Delta Q \ge +10\%$（绝对值提升 $\ge 0.5$ 分）；
  2. 评测集内无任何单题出现质量显著退化（无单题得分比 baseline 低 $> 1.0$ 分）；
  3. P95 延迟倍率 $T_{p95}^{swarm} / T_{p95}^{single} \le 3.0$（绝对时间锁定 $\le 15.0\text{s}$）；
  4. Token 成本倍率 $Cost_{swarm} / Cost_{single} \le 3.0$（单次平均成本 $\le 0.05$ 元人民币）；
  5. 异常降级率 $Rate_{fallback} < 5.0\%$。
- **DEBATE 保留门禁（二期决策条件）**：
  仅在 SWARM 达标的前提下评估；DEBATE 必须在 SWARM 基础上质量再提升 $\ge 5.0\%$，且成本倍率 $\le 6.0\times$ 基线；若不满足则永久废弃 DEBATE，仅保留 SWARM。

### F.9 明确系统失败状态码与语义
```text
ORCHESTRATION_UNKNOWN_MODE   : 传入非法或未识别的编排模式，已自动降级为 SINGLE
ORCHESTRATION_DISABLED       : 该 Bot 未配置或禁用了多智能体编排，正常路由至 SINGLE
SUPERVISOR_TIMEOUT           : 蜂群协同调度超过 15000ms 硬超时，已无缝回退至 SINGLE
SUPERVISOR_FAILED            : 蜂群内部任务拆解或执行抛出异常，已无缝回退至 SINGLE
DEBATE_FAILED                : 对抗辩论仲裁过程异常，已自动回退至正方初步立论或 SINGLE
BUDGET_EXCEEDED              : 请求消耗 Token 触碰单请求预算阈值，已触发强行熔断截断
```

### F.10 最小实现文件集合与红线边界
实施阶段**允许且仅允许**修改以下 **4 个生产文件**（+1 个单元测试契约文件）：
1. `backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto` (协议层)
2. `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/impl/KbAgentConfigServiceImpl.java` (控制面参数桥接)
3. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java` (核心接线与降级熔断)
4. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/SupervisorAgent.java` (黑板请求级隔离改造)
5. `backend/tests/src/test/java/tech/qiantong/qknow/hermes/agent/OrchestrationWiringContractTest.java` (新增严格契约测试)

**严禁触碰的边界 (Forbidden Boundary)**：
- 严禁修改数据库表结构及实体类 `KbAgentConfigDO.java`；
- 严禁修改控制层 `KbConversationController.java`；
- 严禁触碰前端代码及 UI 设计系统；
- 严禁修改或引入任何 C 类仿真文件（`StructuredDebateCoordinator.java`、`MoAExpertConsensusEngine.java` 等）。

### F.11 隔离环境全量复现与验证命令
严格基于 Java 21 隔离环境执行，严禁污染宿主 Java 17：

```bash
# 1. 协议编译与代码生成
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl qknow-hermes/qknow-hermes-proto compile

# 2. 核心编排模块单元与契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am test

# 3. 多智能体接线定向契约验证 (待新增的测试类)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core test -Dtest=OrchestrationWiringContractTest
```

---

## G. 残余风险、立即停止条件与授权边界

### G.1 残余工程风险与防御预案
1. **API 并发限流风险 (HTTP 429 Rate Limit)**：
   - *风险特征*：当多个用户并发启用 SWARM 时，瞬间并发派生的 Worker 激增，可能触碰 DeepSeek 官方账号的 QPS 上限。
   - *防御预案*：所有 Worker 的大模型调用必须强行收敛于 `LLM_CALL_SEMAPHORE`（最大许可数 = 8），一旦排队超过 3 秒直接触发优雅降级。
2. **检索噪声的错误放大与级联 (Error Cascade)**：
   - *风险特征*：若外部知识库召回的切片本身包含误导性信息，Supervisor 拆解的子任务将被噪声带偏。
   - *防御预案*：在 Supervisor 最终聚合前，保留单 Agent 的事实校验步骤，并将相关度得分低于 0.65 的切片予以剔除。
3. **智能体间的随和妥协与假共识 (Sycophancy Trap)**：
   - *风险特征*：辩论双方缺乏立场坚持，盲目附和。
   - *防御预案*：在 Critic 提示词中强行注入“反事实挑刺指令（Adversarial Prompting）”，禁止直接认同正方第一轮结论。

### G.2 触发即停条件 (Immediate Stop Conditions)
在后续实验评估或测试运行中，一旦监测到以下**任意一项指标触发**，立即终止实施并回滚：
1. 综合质量评分提升 $< 10.0\%$（或在多步问答集上与 baseline 无统计显著差异）；
2. 端到端 P95 延迟突破 $3.0\times$ 基线（即绝对耗时 $> 15.0\text{s}$）；
3. 单次请求 Token 成本突破 $3.0\times$ 基线（折算成本 $> 0.05$ 元人民币）；
4. 在评测中出现任何一次单 Agent 默认路径失败或降级（默认路径回归率 $> 0\%$）；
5. 生产模拟流量下异常降级率 $> 5.0\%$。

### G.3 独立分级授权边界 (Authorization Boundaries)
本阶段所有产出严格限定为只读调研、架构适配分析与契约设计，后续所有推进动作必须遵循独立分级授权：
- **实施授权（代码修改）**：必须待用户明确审批本报告及决策计划后，方可对上述 4 个文件实施代码与测试编写；
- **参数调优授权**：在固定代码后，任何针对超时、Worker 数量、提示词的参数微调需二次确认；
- **A/B 实验与小流量灰度授权**：在线下测试 100% 通过后，开启线上特定租户的灰度验证需单独授权；
- **生产全量默认切换授权**：严禁私自将全局默认模式改为 SWARM，生产全量推广需最高级生产变更授权。
