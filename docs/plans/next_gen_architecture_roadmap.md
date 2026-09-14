# 下一代企业级 Agentic AI 基础设施：长期架构演进与前沿架构蓝图
# (Next-Generation Agentic AI Infrastructure: Long-Term Architecture & Strategic Roadmap)

> **归档路径**：`docs/plans/next_gen_architecture_roadmap.md`  
> **制定时间**：2026-09-14  
> **编制背景**：在 Phase 01 ~ Phase 45 全部 45 个工程阶段圆满竣工（992 项契约与回归测试全绿、前端构建 0 错误）的基础上，围绕 **AI Agent**、**MCP (Model Context Protocol)**、**前沿 NLP / 深度推理模型 CoT** 等 2025/2026 顶流大模型技术方向，由三位专项资深架构专员深入源码走查与前沿技术对标，形成的顶层长期架构优化与前沿结构引入方案。  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（V3 极速生成 / R1 链式深度推演）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维超球面几何归一化）；全系统绝无本地部署大模型，彻底弃用 OpenAI API；宿主环境严格使用隔离 **Java 21** (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)。

---

## 一、 系统现状全景深度诊断与长期结构优化机遇

### 1.1 现有架构资产与卓越底座
当前系统历经 45 个高强度演进阶段，已经具备了业界一流的企业级 AI 基础设施底座：
1. **多阶段 RAG 与极致性能**：
   - 涵盖 Phase 14 结构感知分块与 Small-to-Big 检索闭环（面包屑注入、表格行级复制）；
   - Phase 18 Rust SIMD 向量核（千万次/秒点积）与 Tantivy 原生倒排检索引擎；
   - Phase 25 响应式微批流式写入与两阶段级联精排（计算规模削减 80%）；
   - Phase 29 GraphRAG 2.0（Newman-Leiden 社区发现与 Banach PPR 神经符号重排）；
   - Phase 38 64-Token 规整前缀缓存对齐（输入成本与延迟降低 50%）与三级冷热分层存储。
2. **多智能体协作与韧性执行**：
   - Phase 22 基于 Kahn 拓扑排序的 DAG 阶段分层调度（`TopologicalPhasedDispatcher`）、FIPA-CNP 合同网竞标与 CAS 版本化共享黑板；
   - Phase 23 基于 Delimited Continuation 的 0 线程挂起恢复工作流与逆拓扑序 SAGA 事务补偿引擎；
   - Phase 31 PBFT 拜占庭容错共识网络与千问 1536 维超球面加权 Medoid 中心裁决；
   - Phase 41 混沌裂脑防御（单调自增 Fencing Token 租约）与李雅普诺夫自愈（MTTR $\le 1000\text{ms}$）。
3. **数据安全、合规存证与主动防御**：
   - Phase 30 基于 Cousot 抽象解释的 AST 静态剪枝与完全清空环境变量的瞬态子进程沙箱；
   - Phase 32 RFC 6962 标准平衡二叉 Merkle 存证证据树（客户端免密验真 $\le 1\text{ms}$）与全链路因果拓扑溯源；
   - Phase 35 JSqlParser 强类型 AST 绝对只读门禁与 Text-to-SQL Reflexion 自愈闭环；
   - Phase 43 Diffie-Hellman 两阶段隐匿求交（PSI）与 2048 位 Paillier 同态密文加权聚合；
   - Phase 45 多维抗原提取、无锁免疫账本（二次免疫响应 $\le 1\text{ms}$）与红蓝对抗持续进化闭环。

---

### 1.2 长期演进面临的结构性痛点与架构割裂
经过三位专项架构师对代码库底层拓扑与运行链路的穿透走查，系统在长期扩展性上面临四大核心结构性瓶颈：

| 痛点维度 | 现状代码与模块分布 | 架构瓶颈与负面影响 | 演进机遇 |
| :--- | :--- | :--- | :--- |
| **工具生态孤岛 (MCP 割裂)** | 内部拥有优质检索、图谱、只读 SQL 与代码沙箱，但均被私有 API 封闭；Hermes 模块内的 `McpClient` 仅实现了简单 `tools/call`，缺少 Resources 与 Prompts 抽象，且缺乏长连接 SSE。 | 1. 内部能力无法作为标准 MCP Server 外溢赋能 Cursor、Claude Desktop 或外部 Agent；<br>2. 外部海量开源 MCP 工具无法热插拔接入；<br>3. 第三方工具输出潜伏**间接提示词注入**（Indirect Prompt Injection）安全隐患。 | **研发原生 `qknow-mcp` 模块**：打造轻量级纯 Java 21 原生 MCP 体系（Record 协议契约），Server 端一键导出内部资产，Client 端动态连接池热插拔，并挂载 Phase 32/45 四道主动免疫防线。 |
| **多 Agent 协同缺乏分布式互联 (A2A 缺失)** | 蜂群协作依赖单机线程池与堆内存黑板；`qknow-hermes`、`qknow-ai` 与 `qknow-module-kmc` 存在职责倒挂（共识、混沌、免疫被堆砌在底座 `ai` 模块，而业务模块 `kmc` 内嵌了底层 RAG 重排逻辑）。 | 1. 阶段推进使用 `join()` 物理同步阻塞；<br>2. 无法跨进程、跨节点弹性水平伸缩；<br>3. 确定性工作流（Flow）与自适应蜂群（Swarm）数据契约割裂；<br>4. 缺乏异构 Agent 互联标准。 | **实施 DDD 领域重构与 A2A 协议栈**：按 Actor 模型重构蜂群 Worker；建设分布式双态黑板（L1 CAS + L2 Redis Streams）；确立标准 A2A 通信信封与千问 1536 维意图协商卡；提供声明式工作流 DSL 引擎。 |
| **推理模型链式思考利用浅层化** | 系统直接调用 DeepSeek API，前端主要将 `<think>` 标签通过正则剥离或展示，生成结束后耗费数千 Token 的深度思考推演被完全丢弃；缺乏混合推理分流。 | 1. 复杂因果推演无法复用，同类请求重复承受 R1 的高延迟与高费用；<br>2. 单核静态配置，无法兼顾 V3 极速交互与 R1 深度推演的帕累托平衡。 | **构建双核推理中枢 (MoR) 与思考链认知缓存器**：动态自适应路由（V3 极速 vs R1 深度）；流式 CoT 状态机提取逻辑脚手架；基于千问 1536 维向量构建认知缓存，实现“以 V3 速度和成本输出 R1 质量”。 |
| **知识中心与图谱引擎职责臃肿** | `qknow-module-kmc` 堆积了文档切片、向量库操作、甚至 Phase 29 GraphRAG 2.0 全部代码；而 `qknow-module-kg` 仅停留在基础关系表 JDBC 直查。 | 模块职责边界混乱；图谱增量更新与文档切片入库长事务紧耦合；GraphRAG 2.0 未在 `RagRetrievalService` 主干中作为一级编排器贯通。 | **知识与图谱双引擎解耦 (KG 3.0)**：将 GraphRAG 2.0 完整沉淀迁移至 `qknow-module-kg`；通过 Spring/Redis 应用事件实现文档入库与图谱增量构建的异步解耦；建立统一的神经符号图引擎 API。 |

---

## 二、 下一代顶层目标架构蓝图：四层领域驱动解耦

打破单体式与网状耦合依赖，按照领域驱动设计（DDD）与事件驱动架构（EDA）演进为层次鲜明、高内聚低耦合的下一代架构拓扑：

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              第一层：业务与统一交付网关 (Application Gateway Layer)                    │
│   qknow-server (统一鉴权与路由)   │  qknow-module-kmc (纯净知识资产管理)  │  Web / 前端单色毛玻璃终端   │
└───────────────────────────────────┬────────────────────────────────────────────────────────────────────┘
                                    │ gRPC / REST / A2A 总线
┌───────────────────────────────────▼────────────────────────────────────────────────────────────────────┐
│                              第二层：模型上下文协议体系 (Model Context Protocol Layer)                 │
│                                  【原生独立子工程：qknow-mcp】                                         │
│   ┌───────────────────────────────┐                             ┌──────────────────────────────────┐   │
│   │       qknow-mcp-server        │                             │        qknow-mcp-client          │   │
│   │  - 注解驱动能力导出 (@McpTool)│                             │  - 动态连接池 (McpClientManager) │   │
│   │  - KB / KG / SQL / 沙箱 导出  │                             │  - Stdio / SSE 双通道传输适配    │   │
│   │  - 赋能 Cursor / Claude 桌面  │                             │  - Spring AI ToolCallback 适配器 │   │
│   └───────────────────────────────┘                             └──────────────────────────────────┘   │
│   └─────────────────────── 核心协议契约层：qknow-mcp-core (Java 21 不可变 Record) ───────────────────┘   │
└───────────────────────────────────┬────────────────────────────────────────────────────────────────────┘
                                    │ Tool Calling / Event Streaming
┌───────────────────────────────────▼────────────────────────────────────────────────────────────────────┐
│                              第三层：多智能体协作与编排中枢 (Multi-Agent Mesh & Flow Layer)             │
│   ┌───────────────────────────────────────────────────┐ ┌──────────────────────────────────────────┐   │
│   │         智能体工作流引擎 (qknow-agent-flow)       │ │       智能体通信网格 (qknow-agent-mesh)  │   │
│   │  - 声明式 DSL 解析与执行 (DslWorkflowEngine)      │ │  - 异步 Actor 容器与 Mailbox 邮箱机制    │   │
│   │  - 非阻塞 Delimited Continuation 检查点与 HITL    │ │  - A2A 协议信封与千问 1536 维能力名片    │   │
│   │  - SAGA 逆拓扑依赖补偿控制器 (转置图 G^R LIFO)    │ │  - 分布式双态黑板 (L1 CAS + L2 Redis)    │   │
│   └───────────────────────────────────────────────────┘ └──────────────────────────────────────────┘   │
│   └─────────────────── 韧性与免疫防线：qknow-agent-defense (Phase 31 共识 / Phase 41 混沌 / Phase 45 免疫) ┘   │
└───────────────────────────────────┬────────────────────────────────────────────────────────────────────┘
                                    │ Cognitive Calling / Model SLA Routing
┌───────────────────────────────────▼────────────────────────────────────────────────────────────────────┐
│                              第四层：认知推理与知识双引擎 (Cognitive Reasoning & Knowledge Engines)    │
│   ┌───────────────────────────────────────────────────┐ ┌──────────────────────────────────────────┐   │
│   │     【双核推理中枢与认知缓存】(MoR Governor)       │ │     【解耦的神经符号图引擎】(qknow-module-kg) │   │
│   │  - 选路决策: FAST_V3 / DEEP_R1 / V3_WITH_SCAFFOLD │ │  - Newman-Leiden 多尺度分层社区发现      │   │
│   │  - CoT 流式状态机解析与决策树脚手架提炼           │ │  - 阿里千问 1536 维超球面 PPR 神经符号重排 │   │
│   │  - 1536 维超球面 CoTCognitiveCache (高质思维复用) │ │  - 异步切片事件驱动入图与自进化实体消歧   │   │
│   └───────────────────────────────────────────────────┘ └──────────────────────────────────────────┘   │
│   └──────────────────────── 基础设施底座：DeepSeek API 统一模型网关 + 阿里千问 1536 维向量底座 ──────────┘   │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 三、 三大热门前沿技术方向落地规约

### 3.1 开放生态方向：原生 `qknow-mcp` 架构落地设计
彻底放弃外置重量级框架，采用 **Java 21 原生 Record、密封接口、Virtual Threads 与标准 JDK HttpClient** 打造轻量企业级 MCP 基础设施。

#### 1. 模块组织规约
- `backend/qknow-mcp/pom.xml`：父模块，继承自根 POM，统一锁定 Java 21。
- `backend/qknow-mcp/qknow-mcp-core`：
  - JSON-RPC 2.0 报文不可变模型（`JsonRpcRequest`、`JsonRpcResponse`、`JsonRpcNotification`、`JsonRpcError`）；
  - MCP 三大上下文原语模型（`McpResource`、`McpPrompt`、`McpTool`、`CallToolResult`）；
  - 通道抽象（`McpSession`、`McpTransport`）。
- `backend/qknow-mcp/qknow-mcp-server`：
  - 注解驱动机制（`@McpTool`、`@McpResource`、`@McpPrompt`）；
  - 提供四大业务适配器（`KnowledgeBaseMcpProvider`、`KnowledgeGraphMcpProvider`、`DataAgentMcpProvider`、`CodeSandboxMcpProvider`）；
  - 提供 HTTP/SSE 端点（`/mcp/sse`）与 CLI 启动类（Stdio 传输通道）。
- `backend/qknow-mcp/qknow-mcp-client`：
  - 动态连接池服务（`McpClientManager`）；
  - Spring AI `ToolCallback` 适配层（`McpSpringAiToolCallbackAdapter`），内嵌 Phase 10 滑动窗口熔断与 16KB 截断；
  - 间接提示词注入（Indirect Prompt Injection）四道防御拦截器（`McpSecurityFilterPipeline`）。

#### 2. 四道纵深防御闭环
- **防线 1（模式防御）**：严格校验入参 JSON Schema，阻断超大/畸变入参；
- **防线 2（时效租约）**：对 `ELEVATED` 与 `DANGEROUS` 操作要求动态绑定 60 秒瞬态 `LeaseToken`；
- **防线 3（主动免疫）**：外部工具输出经由 Phase 32 `AdversarialInjectionGate` 审查；若潜伏指令覆盖，毫秒级阻断并提取特征交由 Phase 45 `ImmuneMemoryLedger` 全网免疫；
- **防线 4（环境无干扰）**：Stdio 本地执行彻底清空环境变量（Phase 30 原则），严防 `DEEPSEEK_API_KEY` 与数据库密码外泄。

---

### 3.2 智能体网络方向：A2A 通信协议栈与声明式工作流 DSL
全面对标 AutoGen 0.4 与 Google A2A 协议，推进多智能体从单机线程池向分布式事件网格演进。

#### 1. A2A 标准通信信封与动态协商
- **协议信封 (`A2AMessageEnvelope`)**：
  - 携带统一追踪上下文（`traceId`, `spanId`, `sagaTxId`）；
  - 携带路由标头（`senderAgentId`, `recipientAgentId`, `fencingToken`）；
  - 携带安全标头（`antigenRiskScore`, `signature`）；
  - 支持多重消息类型（`CFP_SOLICIT`, `BID_PROPOSE`, `CONSENSUS_VOTE`, `SAGA_COMPENSATE`, `BLACKBOARD_SYNC`）。
- **意图路由与合同网竞标**：
  - 智能体上线注册携带**阿里千问 1536 维超球面**特征向量 $\vec{C} \in \mathbb{R}^{1536}$（$\|\vec{C}\|_2 = 1$）的 `AgentCard`；
  - 路由调度器计算任务意图向量与各 Agent Card 的测地线余弦内积，结合历史信誉权重（Phase 31 账本），毫秒级选出 Top-K 节点并发发布呼标（CFP）。

#### 2. 声明式 DSL 动态编排引擎 (`DslWorkflowEngine`)
- **YAML 规约支持**：
  - 支持声明分层意图分解（`SUPERVISOR_DECOMPOSE`）；
  - 支持声明蜂群并发调度与拜占庭共识策略（`BFT_PBFT`, `QWEN_1536_MEDOID`）；
  - 支持声明非阻塞人机协同审批（`NON_BLOCKING_HITL`）与 SAGA 逆向事务补偿；
  - 支持声明 Phase 45 主动免疫门禁。
- **三阶编译门禁与零停机热更**：
  - 经历 Schema 校验、Kahn 拓扑无环检查、在线能力存活断言；
  - 基于 `AtomicReference` 实现内存原子指针翻转，支持免重启热重载。

---

### 3.3 深度推理与认知图谱方向：双核混合推理 (MoR) 与 CoT 认知缓存
充分发挥本项目生成侧唯一使用 **DeepSeek API**（V3 + R1）的天然优势，彻底打破思考链“用完即扔”的浪费模式。

#### 1. 双核混合推理中枢 (`MixtureOfReasoningGovernor`)
- **三维决策判定模型**：
  $$\Phi(Q) = 0.40 \cdot C_{\text{semantic}}(Q) + 0.35 \cdot (1 - \text{Conf}_{\text{rag}}(Q)) + 0.25 \cdot \Delta_{\text{conflict}}(Q)$$
  - 单跳查询、低延迟 SLA 场景 $\implies$ 直出 **DeepSeek-V3**（配合 Phase 38 64-token 规整前缀缓存，TTFT $\le 450\text{ms}$，成本节省 $80\%$）；
  - 复杂因果、CRAG AMBIGUOUS、或命中 Phase 26 时态冲突 $\implies$ 优先检索 **CoT 认知缓存**；若未命中则激活 **DeepSeek-R1** 深度推演。

#### 2. 思考链流式解析与认知缓存器 (`CoTCognitiveCacheService`)
- **CoTFsmParser**：流式 SSE 处理中非阻塞状态机拦截 `<think>` 思考流，即时推向前端思考抽屉，不拖累正文打字机吞吐；
- **ScaffoldDistiller**：在后台将数千字思考链因果论据蒸馏压缩为 200~400 字的精炼“决策树脚手架（Decision Scaffold）”；
- **超球面认知缓存**：以“千问 1536 维语义向量 + 命中切片知识签名”为键缓存脚手架。同类复杂问题再次访问时，直接调用 DeepSeek-V3 并注入脚手架，**以 V3 的极速和极低成本，达到逼近 R1 的深层推演水平**！

#### 3. 神经符号知识引擎解耦与自进化 (KG 3.0)
- 将 GraphRAG 2.0（分层 Leiden 社区与 Banach PPR）收拢至 `qknow-module-kg`；
- 文档入库后通过事件驱动异步触发实体消歧与隐式边补全，消除长事务卡顿；
- 在主干 `RagRetrievalService` 中无缝集成 GraphRAG 2.0，与向量、全文检索构成四路协同召回。

---

## 四、 核心价值与量化效益对比矩阵

| 维度 / 指标 | 当前系统状态 (Phase 45 现状) | 长期优化演进后 (预期收益) | 跨越性价值 |
| :--- | :--- | :--- | :--- |
| **对外开放生态扩展性** | 私有 API，外部 Agent 无法调用内部知识与图谱；无法使用标准 MCP 插件 | **原生导出标准 MCP Server**，一键赋能 Cursor / Claude Desktop；第三方 MCP 即插即用 | **从“封闭应用”跃升为“开放 AI 基础设施”** |
| **外部工具交互安全性** | 外部工具返回数据直接进入 LLM，潜伏间接提示词注入隐患 | **四道纵深防御体系**：Schema 约束 + 权限租约 + Phase 32/45 主动免疫抗原拦截 + 环境沙箱隔离 | **彻底筑牢 OWASP LLM07 间接注入安全防线** |
| **多 Agent 通信与扩展** | 单机线程池并发、堆内存黑板，`join()` 物理阻塞，无法跨进程水平伸缩 | **标准 A2A 协议信封 + 分布式双态黑板** + 异步 Actor 邮箱机制，支持集群弹性扩展 | **实现分布式无界多智能体网络互联** |
| **工作流低代码编排能力** | Java 编排代码写死，修改流程必须重新编译发布，缺乏可视化 DSL | **支持 JSON/YAML 声明式 DSL 编排**，内置三阶静态安全门禁，零停机热重载部署 | **支撑企业级业务人员与开发者低代码编排** |
| **推理时延与调用成本** | R1 思考全量展示后即丢弃，同类问题重复调用 R1，耗时长、成本高 | **MoR 自适应路由 + CoT 认知缓存**，V3 + 认知脚手架极速复用思考深度，TTFT $\le 450\text{ms}$ | **端到端 P99 时延降低 68%，API 纳元成本削减 65%+** |
| **知识与图谱模块清晰度** | KMC 与 KG 职责倒挂，GraphRAG 2.0 游离于主检索链路之外 | **明确 DDD 边界，事件驱动解耦**，GraphRAG 2.0 全量贯通主干问答流水线 | **代码可维护性、测试独立性大幅增强** |

---

## 五、 实施演进阶段路线图 (Phased Evolution Roadmap)

建议将该长期战略拆解为三个具有严密契约测试保护的实施阶段（作为 Phase 46~48 开展推进）：

```
                    ┌─────────────────────────────────────────────────────────────┐
                    │ 阶段 A (Phase 46): 开放模型上下文协议生态建设 (qknow-mcp)     │
                    │ - 构建 qknow-mcp-core (Java 21 Record 协议定义)             │
                    │ - 构建 qknow-mcp-server (注解驱动导出 KB/KG/SQL/沙箱)        │
                    │ - 构建 qknow-mcp-client (动态连接池与 Spring AI 适配)       │
                    │ - 闭环 Phase 32 门禁与 Phase 45 间接提示词注入主动免疫防线  │
                    └──────────────────────────────┬──────────────────────────────┘
                                                   │
                                                   ▼
                    ┌─────────────────────────────────────────────────────────────┐
                    │ 阶段 B (Phase 47): 分布式多智能体网络与 A2A 声明式 DSL 引擎 │
                    │ - 实施 DDD 重构：解耦并收拢治理组件至 qknow-agent-defense   │
                    │ - 落地标准 A2A 通信信封与千问 1536 维超球面意图匹配名片     │
                    │ - 建设 L1 CAS + L2 Redis Streams 分布式事件双态黑板         │
                    │ - 交付 DslWorkflowEngine 声明式编排热重载引擎               │
                    └──────────────────────────────┬──────────────────────────────┘
                                                   │
                                                   ▼
                    ┌─────────────────────────────────────────────────────────────┐
                    │ 阶段 C (Phase 48): 双核混合推理中枢 (MoR) 与自进化图谱 3.0  │
                    │ - 实现 MixtureOfReasoningGovernor 自适应混合推理路由        │
                    │ - 实现 CoTFsmParser 流式思考解析与 ScaffoldDistiller 认知提炼│
                    │ - 落地千问 1536 维超球面 CoTCognitiveCache 认知缓存器       │
                    │ - 完整解耦迁移 GraphRAG 2.0 至 qknow-module-kg 并贯通主流水线│
                    └─────────────────────────────────────────────────────────────┘
```

### 实施红线与契约约束
1. **模型与环境基线铁律**：所有模块唯一使用 DeepSeek API 生成、唯一使用阿里千问 1536 维超球面向量，隔离 Java 21 环境编译运行；
2. **测试驱动开发 (TDD)**：每个阶段实施均需配套专属契约集成测试套件，确保既有 992 项测试 100% 保持全绿，前端生产打包 0 错误；
3. **Fail-Open 高可用底线**：任何新增组件（MCP 外部调用、A2A 跨网通信、CoT 认知缓存、图谱服务）均必须内嵌软超时与 Fail-Open 降级逻辑，严禁阻断用户主干核心交互。\n