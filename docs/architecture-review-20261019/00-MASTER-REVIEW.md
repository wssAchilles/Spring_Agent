# Knowledge Hub / Hermes 工业级 Agent 平台：架构评审与顶级框架对标总报告

> 评审日期：2026-10-19　|　代码基线：`backend/` 2734 个 Java 文件 / 390,721 行（未含前端）　|　评审方式：6 个独立智能体并行只读审计 + Lead 交叉验证
> 纪律声明：本报告为只读评审产物，**未修改任何源码、配置、SQL、fixture 或前端文件**，未运行 Maven 构建与长测试。所有事实性结论均附 `文件:行` 证据；标注【推断】者为静态代码推导、尚未运行验证；标注【未验证】者为本次未覆盖。按 AGENTS.md 要求，任何修复实施均需在方案获批后另立契约执行。

---

## 0. 执行摘要

### 0.1 一句话结论

> **这不是一个"玩具项目"，而是一个研究资产极其丰富、工程体量真实、但"宣称—实现—验证"三者严重断裂的平台：单 Agent 的 RAG 问答主链在理想条件下可运行；多智能体、HITL、检查点恢复、MCP 工具生态、GraphRAG 高级检索在生产路径上基本不可用；部署与安全存在阻断上线级缺口；对外评测数据来自代理仿真而非生产链路。**

### 0.2 成熟度评分卡（对标顶级 Agent 框架，1–5 分）

| 维度 | 得分 | 判断依据（摘要） |
|---|---:|---|
| 认知内核（ReAct / 上下文 / 记忆） | **2.5** | 单 Agent ReAct 主链存在且流式可用；但工具循环断裂、取消不级联、成本用量估算失真 |
| 多智能体协同（Swarm/Debate/Consensus/Supervisor） | **1.5** | 8 个子系统无任何生产调用，仅契约测试/基准探针可达 |
| 工作流 DAG / 检查点 / HITL | **2.0** | 控制面 DAG 调度可运行（虚拟线程+拓扑分层）；检查点 Bean 缺失致恢复必失败、HITL 跨 JVM 失联、恢复语义错误 |
| RAG 与知识图谱 | **2.5** | 检索主链完整、余弦度量正确、证据工程优秀；但 v2 因线程上下文丢失静默回退、跨库分数契约错位、1536/单位范数未强制、GraphRAG 未进主链 |
| MCP / 工具生态 | **1.5** | `qknow-mcp` 79 文件 7,091 行几乎未装配；Provider 返回硬编码假数据；语义路由恒 null 向量；沙箱非真隔离 |
| 平台工程 / 多租户 | **1.5** | 多租户为常量桩（`1001`）、无租户拦截器、Key 明文、fat jar 435MiB、CI 无打包门禁 |
| 可观测性与评测 | **2.0** | LangFuse/RAGAS/Micrometer 有骨架；默认关闭、无 OTel 标准、无真实链路回归基线；评测数据为代理仿真 |
| 安全 | **1.5** | gRPC 无 TLS/无认证、Key 明文、硬编码 HMAC、内部 API 越权、stdio 子进程继承全部凭据 |
| 工程化 / CI | **2.0** | 349 个测试文件、契约测试文化罕见；但无端到端装配测试、无打包/启动门禁、依赖冲突多 |
| 研究资产与文档 | **4.0** | AGENTS.md/设计系统/阶段报告/证据链远超行业平均，是最大差异化资产 |
| **综合** | **≈2.2 / 5** | 顶级框架（LangGraph/OpenAI Agents SDK/Temporal 级）为 4–5 分；本项目"能力面 3 分、证据面 2 分" |

### 0.3 十条最重要的判断

1. **生产主链其实是"单 Agent + 预检索 RAG"**：控制面 `KbAgentConfigServiceImpl.chatMessage()` → 多库并发召回 → gRPC → Hermes `AgentOrchestrator` → 单个 `ReactAgent` 流式生成。多智能体编排没有接进这条链。
2. **唯一的 DeepSeek 生成路径下，工具调用整体失效**：自研 `DeepSeekCompatibleChatModel` 只从 `DeepSeekChatOptions` 取 tools（该类型在生产聊天中从不构造），且从不构造 `AssistantMessage.ToolCall`，框架的 `hasToolCalls()` 永远为 false。
3. **取消链路三层全断**：浏览器断开 → SSE 控制器只置布尔位、不 dispose 上游 → gRPC 客户端 `onDispose` 不触发 → Hermes 内层 ReAct 订阅未被 dispose；客户端放弃后 LLM 仍跑到 120s deadline，Token 持续燃烧。
4. **多租户是常量桩**：`BaseController.getWorkSpaceId()` 恒返回 `1001L`，全仓 50 处使用；`DataScopeInterceptor` 未注册；会话查询无归属校验；`/internal/api/**` 任意 JWT 可跨库检索。
5. **工作流的"断点恢复 / HITL"当前不可用**：控制面 `@ComponentScan` 排除 `tech.qiantong.qknow.hermes.*`，而 `DagCheckpointManager` 只在 Hermes 包内注册 → 控制面注入恒 null → 检查点永不落库、`resumeFlow` 必抛"未配置检查点管理器"；审批状态又是另一个 JVM 的静态 Map。
6. **预置工作流与新建 Bot 模板在第一个 LLM 节点就会失败**：种子 SQL 与模板生成的 LLM 配置缺 `provider`，而 `LLMNodeBO` 要求 `provider` 非空；建好即坏。
7. **RAG v2 在生产聊天中很可能从未生效**：多库并发走无 `TaskDecorator` 的线程池，虚拟线程内 `SecurityUtils.getUserId()` 抛 401 → v2 异常 → 静默回退 legacy；legacy 捕获同一异常后**跳过权限过滤**（fail-open）。
8. **跨库融合分数契约错位**：上游 RRF 分数约 `1/61≈0.016`，下游 `scoreFloor=0.40`，v2 正常返回时上下文会被全部过滤为空。
9. **"企业 MCP 工具生态"尚未成立**：`qknow-mcp` 未装配进任何生产进程；四个 MCP Provider 返回硬编码假数据；`McpToolAdapter` 语义索引恒传 null 向量，工具"按意图路由"名存实亡；stdio 子进程继承全部宿主环境变量。
10. **对外可引用的评测数据不是生产链路数据**：归档证据的完整性审计明确写明"未执行 Qwen embedding、图遍历或后端调用"（THREE_PATH_PROXY/SEQUENTIAL_PROXY）；且其自身数据显示 BM25 部分指标优于 QuadPath。论文与对外材料必须重新标注或重跑。

### 0.4 五个"今天就该做"的动作（24–72 小时，止血）

1. 给 compose 注入 `TOKEN_SECRET`，并把 Hermes 服务纳入编排（否则生产冷启动即失败 / 问答链路全断）。
2. 把 Hermes 镜像从 `eclipse-temurin:17-jre` 改为 21（当前是 Java 21 字节码跑 17 JRE，必然 `UnsupportedClassVersionError`）。
3. `AiApiKeyDO.apiKey` 挂 `EncryptTypeHandler`、响应 VO 去掉明文与 Excel 导出；gRPC 至少绑定 loopback 或上 mTLS，并把明文 apiKey 从 proto 请求中移除。
4. 把 `getWorkSpaceId()` 改为从 JWT/LoginUser 取值（或显式声明单租户并返回 501 拒绝多 workspace 写入），给会话/KB 查询补归属校验。
5. 修 DeepSeek 客户端工具链：映射标准 `ToolCallingChatOptions.toolCallbacks` → tools JSON，解析 tool_calls 构造 `AssistantMessage.ToolCall`；同时补 `onDispose` 级联与流内 `isCancelled` 检查。

---

## 1. 评审方法、范围与证据纪律

### 1.1 物理多智能体派发（6 路并行）

| 智能体 | 负责面 | 交付物 | 规模 |
|---|---|---|---|
| Hermes 认知内核审计员 | agent / planner / swarm / debate / memory / tool / gRPC | [01-hermes-cognitive-core.md](01-hermes-cognitive-core.md) | 7,409 中文字 |
| 流程引擎审计员 | flow / checkpoint / HITL / 自愈 / 控制面 DAG | [02-flow-dag-hitl.md](02-flow-dag-hitl.md) | 12,750 中文字 |
| RAG 引擎审计员 | 检索 / 切块 / 融合 / GraphRAG / 评测证据 | [03-rag-knowledge-engine.md](03-rag-knowledge-engine.md) | 9,863 中文字 |
| MCP 与工具安全审计员 | qknow-mcp / 工具路由 / 沙箱 / 协议一致性 | [04-mcp-tool-ecosystem.md](04-mcp-tool-ecosystem.md) | 8,359 中文字 |
| 平台与工程化审计员 | 控制面 / 安全 / 依赖 / CI / 测试 | [05-platform-infra-controlplane.md](05-platform-infra-controlplane.md) | 8,272 中文字 |
| 行业对标研究员 | 16 个框架/协议、七维矩阵、路线图 | [06-industry-benchmark.md](06-industry-benchmark.md) | 11,319 中文字，45 个一手 URL |

### 1.2 Lead 的独立核查（不是简单汇总）

Lead 独立阅读了控制面→gRPC→Hermes 主链、`AgentOrchestrator`、`DeepSeekCompatibleChatModel`、`KbFlowServiceImpl`、`KbConversationController`、`CrossKbScoreCalibrator`、`MultiKbRetrievalCoordinator`、`SecurityConfig`、`BaseController`、`McpToolAdapter` 等关键文件，并对队友结论做了交叉验证：

- **推翻 1 条**：平台审计员判定"SSE 语义缓存命中路径因 `Sinks.multicast()` 无订阅者丢帧（P0）"。Lead 用 Reactor 3.7.13 写了最小复现，`Sinks.many().multicast().onBackpressureBuffer()` 在无订阅者时 `tryEmitNext/tryEmitComplete` 会缓冲，首个订阅者到达后正常投递（received=1, done）——**该 P0 不成立**。真正的 SSE 问题是取消不传播（见 P0-08）与 `tryEmitNext` 返回值未检查（P2）。
- **部分修正 1 条**：流程审计员称控制面 `McpToolAdapter` 恒 null。实际 `qknow-server` 的 `HermesConfiguration` 显式注册了该 Bean（`HermesConfiguration.java:30-34`），所以非 null；但它只经无参构造、零 MCP Server 注册，`executeTool` 只会返回 "No connected MCP server found"。净效果（工具节点不可用）结论一致，机制需修正。
- **扩大 1 条**：租户隔离问题不止"stub"，凡是写 `workspaceId` 的 50 处业务写入都会落到 `1001`，多租户数据是同号写入。

### 1.3 局限

- 全部为静态审计，未启动服务、未跑真实请求；凡"运行时后果"均标注【推断】。
- 死代码统计为启发式（按简单类名 token 在全仓 main 源码中匹配），用于判断量级与趋势，不作为逐类精确判定。
- 评测数据真实性结论来自仓库内证据自述与数据文件，未重新执行评测。

---

## 2. 系统真实架构：宣称 vs 实际

### 2.1 生产主链路（已验证）

```
浏览器
  └─ HTTP SSE  POST /kb/conversation/send            KbConversationController:81
       ├─ 取历史 + 存 user/assistant 占位消息
       ├─ Agent 配置解析（模型 Key 明文来自 ai_api_key）
       └─ KbAgentConfigServiceImpl.chatMessage()     :226
            ├─ 语义缓存查询（workspace+bot+KB+model 为键）          :258
            ├─ 同步 RAG 预检索（阻塞首字）                          :263-365
            │    └─ MultiKbRetrievalCoordinator（threadPoolTaskExecutor，无 TaskDecorator）
            │         └─ kmc recallTest → RAG v2 / 静默回退 legacy（权限 fail-open）
            ├─ 工具 code 列表（kb_tool_method.code）                :368-374
            ├─ 构建 gRPC ChatRequest（含明文 apiKey / RAG 上下文 / 工具名 / 历史） :391-410
            └─ HermesGrpcClient.chat()（plaintext、120s deadline、取消级联）    :438
                 └─ Hermes HermesGrpcService.chat()             :38
                      └─ AgentOrchestrator.chat()               :130
                           ├─ RetrievalEvaluator.evaluate()  ← 每轮无条件额外 LLM 调用
                           ├─ RAG 结果被包装为「伪工具」knowledgeBase{id}/knowledgeQuery
                           ├─ plan-solve（默认关闭；开启则 4+ 次阻塞 LLM 且一次性返回、失去流式）
                           └─ ReactAgent（单 Agent）stream
                                └─ DeepSeekCompatibleChatModel（HTTP 直连 DeepSeek）
```

### 2.2 宣称能力 vs 生产可达（关键差异）

| 宣称能力 | 代码是否存在 | 生产是否可达 | 证据 |
|---|---|---|---|
| 多智能体 Swarm / Debate / Consensus | 是（52/20/10 类型） | **否**（外部 import 0） | `agent.swarm`/`agent.cognitive`/`agent.consensus` 全仓 main 外部引用 0；`AgentRegistry` 无 `@Component`、全仓无 `new AgentRegistry` |
| Supervisor/Worker 多智能体 | 是 | **否** | 仅 `AgentRegistry` 内部创建，而 Registry 从未实例化 |
| 意图澄清 / FSM / Saga 事务 / 自愈 | 是（intent 10、transaction 11、workflow 3） | **否** | 仅契约测试引用 |
| HITL 审批 | 是（`ApprovalNodeExecutor`） | **否** | 进程内静态 Map；控制面代理 import 的是另一 JVM 同类；组件扫描已排除 |
| 检查点 / 崩溃恢复 | 是（`DagCheckpointManager`） | **否** | 控制面注入 required=false 且无该 Bean → null |
| MCP 工具生态 | 是（qknow-mcp 79 文件） | **否** | 无自动装配；控制面不依赖 qknow-mcp-server；MCP Provider 为假数据 |
| GraphRAG / Steiner / PPR / 层次社区 | 是（hermes.search 16、rag 12） | **否** | 生产检索走 kmc；Hermes search/rag 仅 benchmark/test 引用 |
| 计划分解 Plan-and-Solve | 是 | 配置开关默认关闭 | `application.yml` `qknow.hermes.plan-solve.enabled: false` |
| 流式打字机 | 是 | **是**（ReAct 路径） | `AgentOrchestrator:461-546` + `KbConversationController` |
| 断点续传 | 是（后端环形窗口） | 后端有 / 前端未调用【推断】 | `KbConversationController:188-214`；前端未见 Last-Event-ID 调用 |

### 2.3 重复建设的四套"双轨"

| 领域 | 轨 A（生产） | 轨 B（未接线/半接线） | 后果 |
|---|---|---|---|
| 工作流引擎 | 控制面 `KbFlowServiceImpl`（虚拟线程 + 拓扑分层 + 检查点设计） | Hermes `FlowExecutor`（仅 gRPC `executeFlow` 与测试引用，控制面从不调用） | 两套状态语义、两套 HITL、维护双倍；`hermes/flow` 180 类型中 76 个仅测试引用 |
| RAG | kmc `service/rag/**`（生产预检索） | Hermes `hermes/rag` + `hermes/search`（GraphRAG 等） | 生产永不执行 Hermes 高级检索；论文/文档描述的算法与线上行为不一致 |
| 工具 | 控制面 flow `ToolNodeBO` + Hermes `ToolCallbackResolver` 内置 6 个 Bean | `qknow-mcp` 完整协议实现 + `McpToolAdapter` 语义路由 | 三层实现互不连通；语义路由 null 向量 |
| Embedding | 控制面 DB 配置模型（`AiModelApiServiceImpl.getEmbeddingModel`） | Hermes 记忆硬编码 `text-embedding-v2` + `TONGYI_API_KEY` | 维度/模型漂移无守卫；Hermes 容器缺变量即启动失败 |

### 2.4 死代码量化（启发式，用于判断量级）

对 `qknow-hermes-core/src/main/java` 中 793 个类型统计全仓引用（排除自身声明文件、`src/test`、`/benchmark/`、`/daemon/`）：

| 分类 | 数量 | 占比 |
|---|---:|---:|
| 有生产代码引用 | 374 | 47% |
| 仅 benchmark/daemon 引用 | 32 | 4% |
| **仅测试引用** | **292** | **37%** |
| **全仓零引用** | **95** | **12%** |

- 包级外部 import 计数为 0 的子系统：`agent.swarm`、`agent.cognitive`、`agent.consensus`、`agent.intent`、`cognitive(**)`、`transaction`、`workflow`、`flow.engine`。
- 冻结的 `tech.qiantong.qknow.ai.embodied.*` 资产：**167 文件 / 20,931 行**（按 AGENTS.md 属"永久冻结归档"，不是缺陷，但需计入维护与构建成本）。
- 另有审计员按顶层类型口径统计 Hermes 485 类型中 161 个 main 零引用、89 个"非 Bean 且零引用"，量级与上表一致。

---

## 3. 阻断级问题清单（P0，去重合并后 16 项）

> 级别：P0 = 阻断上线 / 核心能力不成立 / 安全高危 / 对外证据失真。修复成本 S<1 人日，M 1–5 人日，L 5–15 人日。

### 3.1 部署与启动（P0-01 ~ P0-02）

| ID | 问题 | 证据 | 影响 | 成本 |
|---|---|---|---|---|
| P0-01 | 生产 compose 未注入 `TOKEN_SECRET`，控制面启动失败 | `deploy/docker/docker-compose-qknow.yml:12-40`；`application.yml:125`；**已由 `backend/logs/error.log:84` 实证** `Could not resolve placeholder 'TOKEN_SECRET'` | 生产冷启动 100% 失败 | S |
| P0-02 | Hermes 无任何 compose 服务；镜像用 JDK17 运行 Java21 产物 | `qknow-hermes-starter/docker/Dockerfile:1` `eclipse-temurin:17-jre`；父 POM `java.version=21`；compose 全文无 hermes | 部署后问答主链全断；即使启动也 `UnsupportedClassVersionError` | S |

### 3.2 安全与租户（P0-03 ~ P0-06）

| ID | 问题 | 证据 | 影响 | 成本 |
|---|---|---|---|---|
| P0-03 | LLM/Embedding API Key 明文落库、明文回读、明文 gRPC 传输 | `AiApiKeyDO.java:40` 无 TypeHandler；`EncryptTypeHandler` 全仓 0 引用；`AiApiKeyRespVO.java:37` 明文 + `@Excel`；`KbAgentConfigServiceImpl.java:407` + `HermesGrpcClient` `usePlaintext()` | DB/接口/内网抓包 = DeepSeek 与千问密钥泄漏，直接资金损失 | M |
| P0-04 | gRPC 9090 无 TLS、无认证、无拦截器 | `HermesGrpcService.java:23` 无 interceptor；`application.yml` 仅端口；`HermesGrpcClient.java:43-47` plaintext | 同网任意进程可调用 Agent、滥用模型、窃取明文 Key | M |
| P0-05 | 多租户为常量桩 + IDOR + 内部 API 越权 | `BaseController.java:215-217` `return 1001L`（全仓 50 处使用）；`DataScopeInterceptor` 仅测试引用；`MybatisPlusConfig.java:22-31` 无租户插件；`KbChatMessageServiceImpl.java:20-25` 无归属校验；`KbInternalApiController.java:37` 仅按 knowledgeId 检索 | 多租户上线即串号；任意用户可读他人会话/知识库 | M |
| P0-06 | 硬编码 HMAC 共享密钥 | `SecurityConfig.java:155` 字面量 `qknow-flyflow-secret-key-2026`；`FlyFlowHmacAuthenticationFilter.java:36` 同值兜底 | `/flyflow/**` 防伪形同虚设，密钥不可轮换 | S |

### 3.3 Agent 核心（P0-07 ~ P0-09）

| ID | 问题 | 证据 | 影响 | 成本 |
|---|---|---|---|---|
| P0-07 | DeepSeek 路径工具调用整体失效 | `DeepSeekCompatibleChatModel.java:166-188` 只认 `DeepSeekChatOptions`（生产聊天从不构造该类型）→ tools 不发送；`:222-229`/`:296-303` tool_calls 只塞 metadata，全仓 `.toolCalls(` 0 命中 → 框架 `hasToolCalls()` 恒 false | 知识库/天气/HTTP/自定义工具全部不执行，ReAct 退化为单次生成；与"唯一 DeepSeek"基线互斥（切 OpenAI 兼容路径可恢复工具但丢失 thinking） | M |
| P0-08 | 取消不级联，断连后 Token 空烧 | `KbConversationController.java:123-182` `doOnCancel` 只置位不 dispose；`AgentOrchestrator.java:461-497` 内层 `stream.subscribe` 无 Disposable；`DeepSeekCompatibleChatModel.streamResponse` 不检查 `sink.isCancelled()` | 每次放弃的对话持续计费至 120s；并发闸门不释放，容量泄漏 | S |
| P0-09 | HITL 审批跨 JVM 失联 | `ApprovalNodeExecutor.java:23` 进程内静态 Map；`ApprovalProxyController.java:20-22` 在另一 JVM import 同类；`QKnowApplication.java:21-26` 排除 hermes 扫描 | `/hermes/approval/pending` 恒空、approve/reject 恒 404；HITL 全链路不可用 | M |

### 3.4 工作流（P0-10 ~ P0-12）

| ID | 问题 | 证据 | 影响 | 成本 |
|---|---|---|---|---|
| P0-10 | 检查点 Bean 缺失 → 永不落库、恢复必失败 | `KbFlowServiceImpl.java:74-75` 注入 `@Autowired(required=false) DagCheckpointManager`；该类仅在 `hermes.flow.dag` 注册 `@Component`，被控制面扫描排除；`:511-513` 为 null 即抛"未配置检查点管理器" | 断点续跑、HITL 恢复、崩溃恢复 100% 不可用 | S |
| P0-11 | 恢复语义错误：失败层保存 G、恢复从 G+1；ERROR 节点不重试；剪枝集不重建 | `KbFlowServiceImpl.java:380,392,439,451,584,354` | "恢复"会输出错误结果而非重试，属静默数据错误 | M |
| P0-12 | 种子 SQL 与新建 Bot 模板的 LLM 节点缺 `provider` | `init_all_bot_workflows.sql:54` 等（全文件 `provider` 出现 0 次）；`LLMNodeBO.java:49-53` 要求非空；`KbFlowTemplateServiceImpl.java:100-243` 模板配置放在 `data` 而非 `data.config` | 所有预置/新建工作流在第一个 LLM 节点必失败 | S |

### 3.5 RAG（P0-13 ~ P0-14）

| ID | 问题 | 证据 | 影响 | 成本 |
|---|---|---|---|---|
| P0-13 | 异步线程丢失 SecurityContext → RAG v2 静默回退 legacy，且 legacy 权限过滤 fail-open | `ThreadPoolConfig.java:32-43` 无 TaskDecorator；`MultiKbRetrievalCoordinator.java:100-138` 走该池；`SecurityUtils.java:29-39` 无上下文抛 401；`RagRetrievalService.java:226`；`KmcKnowledgeBaseServiceImpl.java:388-413`（v2 catch）与 `:725-735`/`:780-786`（legacy catch 后跳过权限） | 生产很可能从未跑过 v2；知识库权限静默失效；行为随线程调度漂移【运行时需日志验证】 | M |
| P0-14 | 跨库融合 `scoreFloor=0.40` 与上游 RRF 分数量纲（≈1/61≈0.016）不匹配 | `CandidateFusionService.java:94-102`（score=rrf）；`KmcKnowledgeBaseServiceImpl.java:1047-1057`（VO 原样透传）；`CrossKbScoreCalibrator.java:33-34,94-98,113-119` | v2 正常返回时上下文被全部过滤为空；与 P0-13 叠加造成双路漂移 | S |

### 3.6 能力宣称与证据（P0-15 ~ P0-16，战略级）

| ID | 问题 | 证据 | 影响 | 成本 |
|---|---|---|---|---|
| P0-15 | 8 个"多智能体/认知/事务"子系统无生产接线，仅契约测试可达 | `agent.swarm`/`agent.cognitive`/`agent.consensus`/`agent.intent`/`transaction`/`workflow`/`flow.engine` 外部 import 0；`AgentRegistry` 无注解无实例化；`hermes/flow` 仅 27/149 文件从入口可达 | 平台"多智能体协同/自愈/人机协同"宣称与生产不符；维护者误判能力边界；对外演示有声誉风险 | L（接线或删除） |
| P0-16 | 对外评测数据来自代理仿真，被当作生产管线效果 | `tests/evidence/v1_archive/evidence_integrity_audit.json` `THREE_PATH_PROXY`：*"No fourth metadata ranking, Qwen embedding, graph traversal or backend call is executed"*；同文件 `SEQUENTIAL_PROXY`；且自身数据 BM25 relevance 0.816 vs QuadPath 0.590 | 论文/对外材料存在"证据失真"风险，评审可被直接质疑 | M（重跑或降级表述） |

> **另有两项"能力名存实亡"虽非上线阻断，但属战略级 P0，见 §5 P1 清单顶部**：MCP Provider 硬编码假数据（`KnowledgeBaseMcpProvider.java:59-69` 伪造余弦分；`KnowledgeGraphMcpProvider.java:39-54` 伪造三元组；`DataAgentMcpProvider.java:60-87` 伪造 SQL 结果；`CodeSandboxMcpProvider.java:41-53` 未执行代码返回"耗时 42ms/结果 42"）；工具语义路由恒 null 向量（`McpToolAdapter.java:155`）。

---

## 4. P1 清单（显著损失，按主题合并）

### 4.1 接口与协议

1. `qknow-mcp` 79 文件 / 7,091 行无自动装配、无 server 端点、无任何 MCP 配置；控制面不依赖 `qknow-mcp-server`。唯一"自动注册"是 `McpToolAdapter.initializeGithubServer()` 在环境存在 `GITHUB_PERSONAL_ACCESS_TOKEN` 时拉起 `npx -y @modelcontextprotocol/server-github`（无超时/无版本固定，使用个人 PAT），控制面启动即受此影响（`McpToolAdapter.java:81-86,181-187`）。
2. MCP 协议版本双轨且落后：实现基于 `2024-11-05`/`2025-03-26`，最新规范已演进（研究报告记录为 2026-07-28：无状态化、MRTR、tasks 扩展、`tools/list` 确定性排序）；无分页/进度/取消。
3. HTTP MCP 传输可被恶意 server 通过 SSE `endpoint` 事件把 Bearer 凭据外发（SSRF/凭据外泄面）。
4. 幂等工具网关命中缓存时不比对 `payloadHash`；HITL 审批可凭上下文变量 `approvalApproved` 绕过、无一次性消费。

### 4.2 RAG 与知识

5. 双 CRAG：控制面已做 CRAG，Hermes 每轮再无条件调 LLM 评估且结果只记日志/提示（`AgentOrchestrator.java:266-272`；`RetrievalEvaluator.java:41-70`）；每轮 RAG 对话至少 +1 次 LLM，TTFT 显著抬升。
6. "Qwen 1536 维单位向量"未代码强制：embedding 请求未传 `dimensions`（`EmbeddingServiceImpl.java:97`）；`EmbeddingDimensionGuard` 零生产引用且不校验范数；证据文件显示曾用 1024 维模型。维度漂移时表现为静默空召回。
7. 语义缓存键含 workspace/bot/KB/model，方向正确；但"失败回答"用正则黑名单判断（含"抱歉"即不缓存），且缓存失效/版本更新策略缺失。
8. `MemoryManager.onConversationEnd` 在消息 ≤5 时把"最旧一条原始消息"当摘要写长期记忆；Hermes 记忆 embedding 硬编码 `text-embedding-v2` + 无默认值的 `TONGYI_API_KEY`，与控制面 DB 模型双轨。
9. GraphRAG/Steiner/PPR/层次社区/MultimodalPyramidChunker 未进主链；Neo4j 多跳与因果排名实现多为简化版（`MultiHopCausalPathExtractorImpl`、`NeuroSymbolicGraphRankerImpl`）。

### 4.3 流程与运行时

10. 工具节点静默成功：adapter 为空/工具不存在时仍写 `status=SUCCESS` 并塞入错误 JSON（`ToolNodeBO.java:120-152`）；配合控制面默认零 MCP Server，工作流"成功"不可信。
11. 条件表达式为手写字符串比较器（不剥引号、不支持 `query.length`、`appendReplacement` 未转义），SpEL 未使用；菱形汇聚剪枝可能误杀 join 节点。
12. `executeFlowStream` 冷 Flux 双订阅 → LLM 可能被调用 2 次、落库为 `"\n"`。
13. 无节点级 SSE、无 Last-Event-ID 回放（前端未调用后端 replay）；"非模态审批岛屿/时间旅行"为前端本地模拟。
14. 失败/挂起层的并行兄弟节点不取消；`CompletableFuture.allOf().join()` 无单节点超时。

### 4.4 工程化、依赖与可观测

15. fat jar 589 个 jar / 435 MiB，`spring-boot-starter-test`/`embedded-redis` 编译进产物；`qknow-server` 依赖 `junit`、`embedded-redis` 未限定 scope。
16. 版本冲突面：Spring AI 1.1.0 vs Alibaba 1.1.2.2、`jackson-annotations 2.21` vs databind 2.19.x、protobuf 3.25.1 强降、fastjson2 2.0.60、hutool 5.8.31、jjwt 0.9.1（老旧）；CI 无依赖收敛与 license 门禁（仓库存在 iText 5 AGPL 依赖待决策）。
17. CI 只跑 `-pl tests -am test` 与 `-pl qknow-hermes-core -am test`，**没有** `mvn package`、没有 compose 启动 smoke、没有镜像构建；因此 P0-01/02 这类编排断裂永远不会被 CI 发现。`backend/tests` 全模块 0 个 `@SpringBootTest`。
18. 可观测性：LangFuse 默认关闭、无 OTel GenAI 标准；ReAct 正常路径不采集真实 usage，成本指标用 `estimateTokenCount` 估算；`trace`/`observability` 大量类无外部 import。
19. 生产日志继承 dev 级别 DEBUG（`application.yml`），限流/熔断注册 0 使用；Redis 反序列化开启 `WriteClassName` + `autoTypeFilter` 白名单（可接受但危险前提），dev Redis 无密码。
20. SSE `Sinks` 的 `tryEmitNext` 返回值未检查；assistant 占位消息"思考中"在进程崩溃时永久残留（无超时清理任务）。

---

## 5. 与行业顶级 Agent 框架的对标

### 5.1 七维对照（摘要；完整矩阵见 [06-industry-benchmark.md](06-industry-benchmark.md)）

| 维度 | 顶级框架的共识基线 | 本项目现状 | 差距 |
|---|---|---|---|
| 核心抽象 | Graph/State + Node/Edge（LangGraph、Spring AI Alibaba Graph）、Agent+Handoff（OpenAI Agents SDK）、Actor（AutoGen） | 单 `ReactAgent` + 两套 DAG 引擎 | **大**：抽象不统一，状态与执行语义分裂 |
| 状态与持久化 | Checkpointer + thread_id + 时间旅行/断点续跑（LangGraph）；Temporal 确定性重放；Step 级 event sourcing | 控制面有设计但 Bean 缺失；无确定性重放；恢复语义错误 | **大** |
| 多智能体编排 | Supervisor/Swarm/Handoff + 显式拓扑与终止条件 | 有大量实现，零生产接线 | **大** |
| 工具与安全 | MCP 标准 + 审批/沙箱 + 幂等 + 可观测；OpenAI/Anthropic 原生 tool-use 契约 | 工具循环断裂、MCP 孤岛、假数据、沙箱非隔离 | **大** |
| 可观测与评测 | OTel GenAI 语义约定 + trace/span + 数据集回归 + LLM judge 校准 | LangFuse/RAGAS 骨架、默认关闭、评测为代理仿真 | **大** |
| 人机协同 | `interrupt()`/`Command(resume)`（LangGraph）、Resume with state（Temporal）、审批 UI 闭环 | 静态 Map 跨 JVM、前端本地模拟 | **大** |
| 生产部署 | 容器编排 + mTLS + 多租户 + 配额/计费 + 灰度/回滚 | 编排断裂、租户常量、Key 明文 | **大** |

### 5.2 Java 生态可移植性（关键结论）

- 顶级框架多为 Python/TS，**不能直接依赖**；本项目只能移植"概念契约"，不能照搬 SDK。
- 可直接评估的 Java 候选（研究报告给出官方来源与版本）：**Spring AI Alibaba Graph**（已依赖，StateGraph/OverAllState/InterruptableAction 语义最接近 LangGraph）、**LangGraph4j**（MIT，checkpoint saver/breadpoints/Studio）、**ADK Java**、**langfuse-java**、**Spring AI MCP**。
- **不建议**：引入 Python LangGraph 作为核心运行时（破坏 Java 21 单栈与私有化部署）；引入 Temporal 全量（除非 HITL/长流程成为一等业务，且能接受运维复杂度）。
- **建议保留自研**（真正差异化，不应推倒重来）：DeepSeek 思考链/成本治理、Qwen 超球面几何工具链、Hermes prompt/上下文工程、RAG 证据工程与 contract-test 文化、Java 21 虚拟线程与 Record 凭据、流式打字机与前端 Liquid Glass 设计系统。

### 5.3 "抄什么 / 不抄什么 / 保留什么"裁决（摘要）

| 决策 | 对象 | 理由 |
|---|---|---|
| 抄（概念） | LangGraph 的 checkpointer + interrupt/resume；OpenAI Agents SDK 的 handoff/guardrail/tracing 三层；Temporal 的 activity 幂等与超时；OTel GenAI 语义约定 | 都是"可验证的工程契约"，与语言无关 |
| 抄（Java 实现） | Spring AI Alibaba Graph 的 StateGraph/OverAllState；LangGraph4j 的 checkpoint 模型 | 已在 Java 生态，落地成本最低 |
| 收敛（去重） | 双 DAG 引擎 → 控制面唯一；双 RAG → kmc 唯一 + Hermes 只做生成；双工具层 → MCP 客户端唯一入口；双 embedding → 统一模型注册表 | 消除行为漂移与"看起来都有"的假象 |
| 保留（差异化） | Hermes 认知内核、qknow-mcp 安全管线（修好后）、Record 凭据/冷备、DeepSeek 成本工程、RAG 契约测试与证据链、前端设计系统 | 这是项目最值钱的部分 |
| 拒绝 | 大规模引入 Python 运行时、为"研究阶段"继续堆子系统、用 mock/假数据 Provider 充数 | 与工业化目标背道而驰 |

---

## 6. 根因分析：为什么会变成这样

1. **阶段式交付以"新增子系统 + 契约测试"为验收，而非"接入生产路径并端到端验证"**：Phase 84–156 每一阶段都产出独立引擎与同名 ContractTest，但 `QKnowApplication` 一排除 `hermes.*` 扫描，所有依赖它的控制面能力就静默失效；测试因为不启 Spring 上下文而毫无察觉。
2. **契约测试自证（self-fulfilling）**：工具路由测试用同一个函数生成"工具向量"和"查询向量"，因此恒过；RAG 评测用代理仿真生成向量，因此"跑通"但不能证明生产效果；全仓 `backend/tests` 0 个 `@SpringBootTest`。
3. **双轨并行 + 文档漂移**：控制面与 Hermes 各自长出一套 flow/RAG/tool/embedding；README/论文描述的是理想架构，代码里生产可达的是另一套。评审若不逐行追踪装配，就会被类名与注释误导。
4. **缺少"最小生产门禁"**：CI 不打包、不启动、不跑 compose；没有"生产 smoke 测试"（一次真实登录→建 Bot→上传文档→提问→流式响应→落库）。P0-01/02/12 这类"建好即坏"的问题本应由一次端到端 smoke 抓住。
5. **演示/研究代码进入生产 jar**：298MB 未跟踪 `hermes.jar`、589 个依赖、benchmark/daemon 类与 `@Component` 混在 main、MCP Provider 假数据——"可演示"优先于"可运维"。

---

## 7. 优化路线图（按波次，含验收标准）

> 所有工作必须先出契约（baseline/candidate/验收命令/回滚），获批后实施。P0 修复期间禁止顺手重构。

### Wave 0：止血（24–72 小时，约 6–8 人日）

| # | 动作 | 验收 |
|---|---|---|
| 1 | compose 注入 `TOKEN_SECRET`；Hermes 镜像改 21-jre；补 Hermes compose 服务（9090 仅内网） | `docker compose up` 后 `/actuator/health` = UP，Hermes healthCheck = UP |
| 2 | Key 加密：`@TableField(typeHandler=EncryptTypeHandler.class)`，迁移脚本加密存量；VO 去明文/去 Excel；proto 移除 apiKey，改模型别名+服务端解析 | 库表密文；接口响应无 key；gRPC 抓包无 key |
| 3 | gRPC：至少绑定 127.0.0.1 + 网络策略；目标 mTLS + 调用方白名单 | 外部机器 `grpcurl` 不可达 |
| 4 | 租户：`getWorkSpaceId()` 从 LoginUser 取；会话/KB 查询补 `workspace_id` 归属；`/internal/api` 加服务间鉴权 | 跨租户 ID 访问返回 403/404 |
| 5 | 工具链：DeepSeek 客户端映射 `ToolCallingChatOptions` + 构造 `ToolCall.toolCalls`；补 fake-server 契约测试 | 单测覆盖 tools 请求体与 tool_call 解析；真实天气/时间工具可执行 |
| 6 | 取消：控制器保存上游 `Disposable` 并在 `doOnCancel` dispose；`Flux.create` 注册 `onDispose`；流循环查 `isCancelled` | 断连后 1s 内上游日志显示 cancel，无后续 token |
| 7 | 检查点：在控制面显式 `@Bean DagCheckpointManager`（或移出 hermes 包）；修恢复层号（从 G 开始、ERROR 重试、重建剪枝集） | 失败工作流可 resume 且只重复未完成节点 |
| 8 | 模板/种子：LLM 节点补 `provider`，统一 `data.config`；ToolNode 失败返回 FAILED 而非 SUCCESS | 预置工作流首个 LLM 节点成功；不存在工具 → 节点 FAILED |

### Wave 1：收敛与正确性（2 周，约 20–25 人日）

- **单一执行内核**：控制面 DAG 为唯一生产内核；Hermes `flow` 标 `@Deprecated` 并移出生产 jar（保留契约测试归档）；删除或隔离 `flow.engine`/`transaction`/`workflow`/`intent`/`consensus`/`cognitive` 中零引用包。
- **统一 RAG 契约**：显式传递 `userId/workspaceId`（不依赖 SecurityContext）；统一 `score` 语义（RRF 与 cosine 分层，scoreFloor 只作用于 cosine）；去掉 Hermes 重复 CRAG；embedding 强制 dimensions=1536 + 单位范数断言 + 启动自检。
- **HITL 外置**：审批落 Redis/DB（`approval:{flowId}:{requestId}:{nodeId}`，TTL + 一次性消费），控制面经 HTTP/gRPC 访问 Hermes；前端 Last-Event-ID 回放接通。
- **工程门禁**：CI 加 `mvn -DskipTests package` + compose smoke；依赖收敛（failed build on duplicate classes）；移除 test scope 泄漏；fat jar 体积目标 < 200MiB。
- **死代码处置**：8 个子系统"接线或删除"二选一，写 ADR；文档/README 能力表按真实生产可达性重写。

### Wave 2：可观测、工具与评测（6 周，约 40 人日）

- 可观测：OTel GenAI 语义约定 + LangFuse v4 迁移；真实 usage 采集（替换估算）；每次对话 trace 关联 requestId/conversationId/tool 调用/成本；P99/TTFT/成本看板。
- 工具：MCP 客户端统一入口（对齐最新规范：无状态化/MRTR/tasks；分页/取消/进度）；语义工具索引真实 embedding（1536 单位向量）；沙箱真隔离（容器或 `sandbox-exec`/`bwrap`，env 白名单）；审批/幂等/高危工具闭环。
- 评测：20–50 条真实链路回归集（真实 Qwen + pgvector + 后端 + 图），四维 rubric（正确性/引用/延迟/成本）；LLM judge 校准；公开数据必须标注"代理仿真"或重跑。
- 多智能体最小闭环：若产品承诺，则只接 `Supervisor + 2 Worker + RAG 工具` 一条路径，有终止条件与预算；否则删除宣称。

### Wave 3：对标与平台化（1 个季度，约 30–40 人日）

- 图内核语义向 Spring AI Alibaba Graph 收敛（StateGraph/OverAllState/InterruptableAction），评估 LangGraph4j 作为 checkpoint 参考。
- 企业平台面：配额/计费/审计/灰度/版本化/回滚；多租户行级隔离；A2A/MCP 互操作试点。
- 研究资产产品化：把 Hermes 的 prompt/上下文/成本工程沉淀为可复用模块，把 embodied 冻结区彻底移出构建路径。

> 总量级：P0 约 39 人日、P1 约 42 人日、P2 约 20 人日（对标报告口径 ≈101 人日，不含评审/灰度/合规）。

---

## 8. 值得保留的差异化资产（不要推倒重来）

1. **DeepSeek 官方契约与成本工程**：thinking/`reasoning_content` 解析、静态 system prompt 以命中 Context Caching、`DeepSeekCostGovernor` 骨架——方向正确，需补真实 usage 与缓存命中率。
2. **Qwen 超球面几何工具链**：1536 余弦度量、幂等内容签名、记忆衰减/图激活打分；数学基础扎实，缺的是"强制校验"与"单一模型源"。
3. **契约测试与证据工程文化**：349 个测试文件、checksum/泄漏检查/哨兵——这是很多团队没有的，问题在"自证"与"不启上下文"，不是文化本身。
4. **Java 21 现代化使用**：虚拟线程池、Record 不可变凭单、结构化并发思路；方向正确，需统一线程模型与上下文传播。
5. **流式与前端体验**：SSE 打字机、思考链分离、Liquid Glass 设计系统——产品层竞争力所在。
6. **RAG 证据链与回退监控**：`RagFallbackMonitor`、缓存维度、budget 截断等，补上真实链路验证即可成为卖点。

---

## 9. 需要你拍板的问题（决策清单）

1. **"多智能体"是产品承诺还是研究资产？** 若产品承诺，先做最小闭环（Supervisor+2 Worker+RAG）并写入验收；若研究资产，README/对外材料降级表述，代码移出生产 jar。
2. **是否接受"控制面 = 唯一执行内核，Hermes = 推理/模型网关"？**（推荐；能一次性消掉双 flow/双 RAG/双 tool 的大部分问题）
3. **Hermes 部署形态**：独立服务（需 compose/k8s + mTLS + 模型凭据隔离）还是嵌入控制面（简单但失去隔离与独立扩容）？
4. **评测资产处置**：重跑真实链路（需要 Qwen/DeepSeek 配额与时间），还是公开标注"离线代理实验"并撤回生产指标表述？
5. **多租户路线**：现在实现行级隔离，还是明确"单租户产品化"并移除 workspace 语义（避免 1001 假象）？
6. **死代码处置**：swarm/debate/consensus/intent/transaction/workflow 是"接线"还是"删除/归档"？embodied 是否移出构建？
7. **图内核选型**：继续自研 `KbFlowServiceImpl`，还是向 Spring AI Alibaba Graph / LangGraph4j 收敛（影响 6 周以上工期）？
8. **API Key 安全方案**：Jasypt、MyBatis TypeHandler + 环境密钥、还是外部 KMS？轮换周期与应急流程？
9. **是否启动 Wave 0 的 8 项止血修复？** 按 AGENTS.md，需要你明确批准后我们才进入实施（本轮不修改任何代码）。

---

## 10. 附录

### 10.1 分报告索引

| 文件 | 内容 |
|---|---|
| [01-hermes-cognitive-core.md](01-hermes-cognitive-core.md) | 执行路径、ReAct/规划/记忆/工具、6 P0 + 11 P1 + 10 P2、与四大框架差距 |
| [02-flow-dag-hitl.md](02-flow-dag-hitl.md) | 双引擎对比、checkpoint/HITL/恢复语义、W+1 幽灵计数证伪、条件表达式与剪枝缺陷 |
| [03-rag-knowledge-engine.md](03-rag-knowledge-engine.md) | 检索全链路、分数契约、embedding 维度、语义缓存、GraphRAG 现状、评测真实性 |
| [04-mcp-tool-ecosystem.md](04-mcp-tool-ecosystem.md) | MCP 协议一致性、语义路由、Provider 假数据、沙箱/凭据、30 条缺陷 |
| [05-platform-infra-controlplane.md](05-platform-infra-controlplane.md) | 控制面链路、安全/租户/密钥、依赖冲突、CI、测试金字塔 |
| [06-industry-benchmark.md](06-industry-benchmark.md) | 16 框架/规范画像、七维矩阵、抄/不抄裁决、12 项路线图（45 个一手 URL） |

### 10.2 方法与证据说明

- 证据格式：`路径:行号`；关键结论在分报告中标注【已验证】/【推断】/【未验证】。
- 本报告推翻/修正的分报告结论已在 §1.2 明示，分报告原文保留作为审计过程记录。
- 死代码统计脚本口径见 §2.4；不排除反射、Spring XML、动态注册等静态分析盲区。
- 所有外部框架信息来自 2026-10-19 抓取的官方文档/规范/仓库，URL 清单见 06 报告附录。
- 本报告与分报告均未修改项目源码；`git status` 中的既有修改属于评审开始前的工作区状态。
