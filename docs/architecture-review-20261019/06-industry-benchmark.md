# 行业顶级 Agent 框架对标研究与选型建议（2026-10-19）

> 本报告属于 `docs/architecture-review-20261019/` 架构评审序列，任务板编号 **task-6**。
> 评审对象：Knowledge Hub（qknow-server + qknow-module-* 控制面 / qknow-hermes 认知面 / qknow-mcp 工具生态 / Vue 3 前端）。
> 硬约束（由任务下发方给定，本报告不重复验证）：Java 21 单一运行时；生成侧唯一 DeepSeek API；向量侧唯一 Qwen Embedding（1536 维单位向量）；企业私有化部署；无 OpenAI、无本地 LLM。
> 所有外部事实均标注来源类型、URL 与抓取日期（统一为 **2026-10-19**）。本报告**只做外部对标与"能力宣称"层面的差距分析**，不推断任何代码实现事实；凡涉及本项目内部机制，一律标注"宣称未验证"。

---

## 0 研究方法与来源清单

### 0.1 方法

1. **先建立项目"能力宣称"基线**：只读 `README.md`、`backend/` 模块目录结构与关键包名（不读业务实现，不评价代码质量），把 README 与目录结构表达的"系统具备什么能力"记录为**宣称（claim）**，并在后文逐项标注"宣称未验证"。
2. **再定向检索外部一手来源**：对任务列出的每一类框架/规范，优先抓取**官方文档正文、官方规范正文、官方仓库 README**；官方工程博客降一级记为"中"；论文只在有明确一手页面时引用。所有 URL 均在本机实抓（HTTP 200），无法取正文者标 `NOT_VERIFIED`。
3. **最后做三件事**：七维对照矩阵 → 本项目同类机制 1–5 分评分与差距 → "抄/不抄/自研保留"裁决 + ≤12 项 P0/P1/P2 路线图。
4. **纪律**：不修改源码/配置；不执行 Maven/长测试/启动服务；不编造 API 名称或版本号；不确定即写"不确定"。

### 0.2 证据强度分级

| 标记 | 含义 | 典型来源 |
| --- | --- | --- |
| 【强】 | 官方规范或官方文档正文（本次实抓正文） | modelcontextprotocol.io 规范、learn.microsoft.com、docs.langchain.com、docs.temporal.io |
| 【强-仓库】 | 官方仓库 README / 官方文档站（一手，但含营销与导航信息，正文覆盖度有限） | GitHub 官方 README、docs.crewai.com、microsoft.github.io/autogen |
| 【中】 | 官方工程博客、官方公告、官方文档导航（未获得对应正文） | anthropic.com/engineering、a2a-protocol.org 首页与规范导航 |
| 【未验证】 | 页面无法渲染正文 / 未抓取 / 结论依赖二手推断 | 标 `NOT_VERIFIED` |

### 0.3 本次抓取的一手来源（摘要，完整清单见附录）

- 图执行与可观测：LangGraph Persistence【强】、LangSmith Observability【强】。
- Agent 原语：OpenAI Agents SDK Intro 与 Agent Orchestration【强】；Anthropic《How we built our multi-agent research system》【中】、《Building effective agents》【中】；Claude Agent SDK Overview【中，正文被导航截断，能力来自官方文档目录】。
- 多智能体框架：AutoGen AgentChat【强-仓库】、AG2 v1.0 README【强-仓库】、CrewAI Crews【强-仓库（导航）】、Google ADK（Python/Java README）【强-仓库】、Microsoft Semantic Kernel for Java README【强-仓库】、Microsoft Agent Framework Overview【强】、Dify README【强-仓库】。
- Java 直接候选：Spring AI Alibaba Graph README【强-仓库】、LangGraph4j 文档站【强-仓库】、langfuse-java README【强-仓库】。
- 协议与基础设施：MCP 规范 2026-07-28 及 Key Changes、Elicitation【强】；A2A 规范【强】（正文被导航截断，结论来自规范目录与章节标题）；Temporal《What is Temporal?》【强】；OpenTelemetry GenAI 语义约定（迁移后仓库）【强-仓库，字段细节未验证】；Langfuse README【强-仓库】。
- 未取得正文：LlamaIndex Workflows（开发者文档被重定向且为客户端渲染，仅得导航）【未验证】。

### 0.4 项目"能力宣称"基线（宣称未验证）

依据 `README.md` 与目录结构，本项目对外宣称的同类机制包括（**以下均为宣称，未做代码级验证**）：

| 维度 | README/目录表达的能力宣称 | 证据（只读） |
| --- | --- | --- |
| Agent 运行时 | 规划与 ReAct、可选反思与 AI Judge；目录另有 ToT+ReAct 规划器、PRM 步进奖励、MCTS、技能库、循环/周期卫兵 | `backend/qknow-hermes/qknow-hermes-core/.../agent/{planner,guard,skill}` |
| 记忆 | 短期与分层记忆；目录另有海马体双轨记忆、艾宾浩斯衰减、记忆分页、反思路 Episodic、记忆图、DST 对话状态 | `.../hermes/memory/*` |
| 多智能体 | 目录含 swarm / debate / supervisor-worker / blackboard / bidding / consensus / saga 补偿 / 动态拓扑 / 联盟博弈 | `.../hermes/{swarm,agent/debate,agent/blackboard,agent/bidding,consensus}` |
| 工作流/状态 | DAG 拓扑执行、条件分支、并行节点、状态快照与恢复；目录含 micro-checkpoint、receipt、trace 引擎 | `README.md` + `.../hermes/{agent/dag,agent/workflow,infra/checkpoint,trace}` |
| 工具与 MCP | HTTP/stdio MCP 适配；目录含 MCP server（注解式 tool/resource/prompt、OpenAPI→MCP 适配、KB/KG/DataAgent/CodeSandbox provider、QuadDefense 安全管线、租约管理）与 MCP client | `backend/qknow-mcp/{qknow-mcp-server,qknow-mcp-client,qknow-mcp-core}`；`.../module/kb/tool/mcp`；`.../module/kb/tool/permission` |
| 可观测与评测 | Langfuse 可选启用；目录含 LangFuseTracingService、分层 trace、Judge/Eval/Feedback/Evolution 服务 | `README.md`；`.../hermes/observability`、`.../module/kb/service/{judge,eval,feedback,evolution}` |
| RAG 知识引擎 | PgVector + 关键词检索 + 知识图谱路径 + GraphRAG；语义切块与图谱双路抽取 | `README.md`；`backend/qknow-module-kmc/`、`qknow-module-kg/` |

再次强调：上表是**能力面**，不是**质量面**。同一机制"存在"不等于"达到生产级语义"，本报告评分时统一按"可达上限 × 未验证折减"处理。

### 0.5 本次调研的五个时效性关键发现（直接影响路线图）

1. **MCP 已进入"无状态 + 多轮往返"时代**：最新规范版本为 **2026-07-28**，相对 2025-11-25 是破坏性变更——移除协议级 session 与 `initialize` 握手、移除 SSE 可恢复性、以 MRTR（Multi Round-Trip Requests）取代服务器主动请求、把 tasks 移入扩展。任何基于旧版 MCP 的实现都需要版本对齐（来源见附录 21/22/23）。
2. **微软已把 Semantic Kernel 与 AutoGen 合并为 Microsoft Agent Framework**，官方称其为"两者共同的直接后继"，且官方语言支持列表为 .NET / Python / Go（**抓取页未见 Java**）；Semantic Kernel for Java 仍在（MIT），但战略上已非主航道（附录 13/14）。
3. **Java 侧可直接依赖的候选已成熟到可用**：Spring AI Alibaba Graph（StateGraph/OverAllState/InterruptableAction/checkpoint）、LangGraph4j 1.9（checkpoint savers、breakpoints、Studio、MIT）、Google ADK for Java 1.12.0（Maven Central，Apache-2.0）、langfuse-java 0.3.0（OTel-only 追踪）（附录 19/20/16/29）。
4. **可观测性正在向 OTel GenAI 语义约定 + Langfuse v4 收敛**：OTel GenAI 语义约定已迁到独立仓库（spans/metrics/events、MCP 与 provider 专属约定），Langfuse v4 明确"Java 侧唯一受支持的追踪方式是 OpenTelemetry"，旧 v3 端点 Cloud 端保留至 **2026-11-16**（附录 26/27/28/29）。
5. **多智能体进入"工程化可靠性"阶段**：Anthropic 公开了 orchestrator-worker 的量化代价（多智能体约 **15× chat token**，研究型任务提升 **90.2%**）与可靠性实践（rainbow 部署、断点恢复、~20 条查询起步的评测）（附录 8）。

---

## 1 顶级框架逐一画像

> 每个画像给出：定位/核心抽象 → 状态与持久化 → 编排 → 工具与安全 → 可观测与评测 → HITL → 生产化。所有事实均来自该节末尾标注的来源，抓取日期 2026-10-19。

### 1.1 LangGraph + LangSmith（图执行与持久化的"语义基准"）

LangGraph 把 Agent 应用表达为图（Graph/Node/Edge），并提供两套持久化原语：**Checkpointer**（以 `thread_id` 为键的线程内图状态快照，用于会话连续、HITL、time travel、容错）与 **Store**（跨线程的应用自定义键值数据，用于用户偏好、事实与共享知识）。官方文档明确区分"短期线程内记忆"与"长期跨线程记忆"，并给出生产注意项（`PostgresSaver` 的 `thread_id` 长度限制、`MemorySaver` 不跨进程重启、checkpoint 无界增长需要保留策略）。LangGraph 文档站还暴露了 Interrupts（中断）、Time travel（时间旅行）、Subgraphs（子图）、Fault tolerance、Deployment、LangSmith Studio 等一等公民能力。LangSmith 侧提供 traces、dashboards、alerts、online evaluations、用户反馈、自动规则（rules/webhooks）以及 Cloud/Hybrid/Self-hosted 三种部署形态。

**对标价值**：本项目最缺的不是"能不能存快照"，而是 LangGraph 这样**把"线程内状态 / 跨线程记忆 / 中断恢复 / 时间旅行"拆成四个正交契约**。这是 §4"抄什么"的第一优先级。【强】

### 1.2 OpenAI Agents SDK（含 Responses API）

官方定位是"few abstractions, production-ready"：核心原语只有 **Agents**（指令+工具）、**Handoffs / Agents-as-tools**（委派或接管）、**Guardrails**（输入输出校验，可与执行并行、快速失败）。运行时有内建循环、**Sessions**（跨轮记忆层）、**Human-in-the-loop**、**Tracing**（可接评测/微调/蒸馏）；工具侧支持函数工具、托管工具与 **MCP server**；工程侧新增 Sandbox agents（隔离工作区、可恢复沙箱会话）。官方明确 SDK 默认使用 **Responses API**，并给出"何时直接用 Responses API、何时用 SDK"的判据（自管循环/短流程 vs 托管轮次/工具/guardrail/handoff/session）。编排文档给出清晰的双模：**Agents as tools**（经理保留控制权、统一 guardrail）与 **Handoffs**（专家接管本轮会话），并允许混用。

**对标价值**：两层抽象（Agents-as-tools 与 Handoffs）的取舍标准可直接移植为 Hermes 的"委派语义"；但 Responses API 的托管状态与托管工具是**厂商绑定**，与 DeepSeek-only 冲突，不可抄。【强】

### 1.3 Anthropic：multi-agent research system + Building effective agents + Claude Agent SDK

《How we built our multi-agent research system》（2025-06-13）是一手工程记录：lead agent 规划→并行 spawn 3–5 个（复杂任务 10+）subagent→聚合→CitationAgent 补引用；subagent 拥有独立上下文窗口与工具/提示词，用于"压缩"与"关注点分离"。给出的量化事实包括：lead=Opus 4、sub=Sonnet 4 的多智能体在内部 research eval 上比单智能体 Opus 4 **高 90.2%**；多智能体约 **15× chat token**，普通 agent 约 4×；BrowseComp 上 **token 用量单独解释 80% 方差**；并行化把复杂查询耗时降低最多 **90%**；prompt 层要显式写"投入规模规则"；评测从 **~20 条真实查询**起步，LLM-as-judge 按 factual accuracy / citation accuracy / completeness / source quality / tool efficiency 五项 rubric 打分；可靠性上需要**断点恢复 + 确定性重试 + 常规 checkpoint**，部署用 **rainbow deployment** 灰度；同步执行 subagent 是当前瓶颈。

《Building effective agents》（2024-12-19）给出被全行业复用的工作流模式：augmented LLM、prompt chaining、routing、parallelization（sectioning/voting）、orchestrator-workers、evaluator-optimizer，以及"能用简单方案就不要上 agent"的取舍原则与 ACI（agent-computer interface）工具设计原则（poka-yoke、清晰描述、格式贴近自然语言）。

Claude Agent SDK 文档目录显示其工程面：sessions 与外部存储、subagents、hooks、permissions、MCP、skills、plugins、tool search、checkpointing（文件回滚）、cost tracking、OpenTelemetry 可观测、hosting/secure deployment。本次未取得正文，仅以官方文档目录为证，故记【中】。

**对标价值**：这是"多智能体的收益/代价/可靠性"最诚实的公开材料，适合直接作为本项目 swarm/debate 的**成本闸门与评测口径**依据。【中】

### 1.4 AutoGen（AgentChat）

AutoGen 分两层：`autogen-core` 事件驱动编程模型，`autogen-agentchat` 高层多智能体 API（Agents + Teams）。文档目录显示的团队模式包括 Selector Group Chat、Swarm、Magentic-One、GraphFlow（有向图工作流），并单独覆盖 HITL、Termination、Managing State、Memory and RAG、Logging、Serializing Components、Tracing and Observability、Studio。需要注意：微软已宣布 **Microsoft Agent Framework 是 AutoGen 与 Semantic Kernel 的直接后继**（见 1.6），AutoGen 处于"概念被吸收、维护趋缓"的状态。

**对标价值**：Team 设计模式（selector/swarm/magentic/graph）可作为"多智能体拓扑目录"参照；状态管理与序列化组件是"可回放"的最小前提。【强-仓库】

### 1.5 AG2

AG2 从 AutoGen 分叉后于 **v1.0** 重构为"protocol-driven AgentOS"：`Agent` + `ask()` 轮次模型、`@tool`、HITL 用 `context.input()` 在工具内部暂停、多智能体用 **Network（Hub + typed channels + 预写日志 WAL + 审计轨迹）**，channel 类型含 conversation / consulting / discussion / workflow（TransitionGraph）。还提供 agent harness：`knowledge=`（KnowledgeStore 持久记忆）、`compact=`（SummarizeCompact 压缩历史）、middleware、observers、telemetry、evaluation、testing。经典的 `ConversableAgent/GroupChat` 已拆到 `ag2-classic` 独立维护。许可证 Apache-2.0。

**对标价值**：Hub + WAL + 审计 的"协议化多智能体"与 Hermes 的 swarm/consensus 设计高度同构，是本项目**最值得对照的 Python 参考实现之一**（尤其审计与回放）。【强-仓库】

### 1.6 CrewAI

CrewAI 的核心概念是 Agent / Task / Crew / Flow / Process，并围绕其扩展 Memory、Planning、Reasoning、Knowledge、Skills、Collaboration、Training、Testing、Production Architecture、Streaming、CLI；文档站版本 v1.15.28，另有 CrewAI Cloud / AMP 平台。它强调"角色化团队 + 流程"，并给出生产架构章节。

**对标价值**：角色/任务/流程的建模方式适合非工程用户；但对"状态机级持久化与恢复"的公开证据弱于 LangGraph，本项目不应把它当状态层参照。【强-仓库】

### 1.7 Microsoft：Semantic Kernel / AutoGen → Microsoft Agent Framework

Microsoft Agent Framework 官方定义为四块：**Agents**（含工具与 hosted MCP）、**Harness Agent**（面向长任务的规划/待办、上下文压缩、文件与记忆、工具审批"don't-ask-again"、可观测）、**Workflows**（函数式与图式工作流）、**Integrations**（模型、工具、上下文、middleware、评测、UI）；并提供 model clients、agent session 状态、context providers、middleware、MCP clients 等基础件。官方原文："Agent Framework combines AutoGen's simple agent abstractions with Semantic Kernel's enterprise features... is the direct successor"。语言支持为 .NET / Python / Go（Go 为 public preview，且明确部分能力未提供）；**抓取页未见 Java**。Semantic Kernel for Java 仍然存在（MIT，plugins/planner/chaining，指向 Microsoft Learn 的 Java 文档），属于"可用但非主航道"。

**对标价值**：Harness Agent 的"上下文压缩 + 工具审批 + 可观测"三件套，正是本项目 Hermes 的对照面；但 **不要把新核心押在 SK Java 上**（后继框架无 Java 官方实现）。【强】

### 1.8 Google ADK（ADK 2.0 / ADK for Java）

Google ADK 2.0 是 code-first 框架，核心是 `Agent` + `Workflow`（图式执行引擎：routing、fan-out/fan-in、loops、retry、state、dynamic nodes、HITL、嵌套工作流），新增 **Task API**（agent-to-agent 结构化委派、多轮任务、单轮受控输出、任务作为工作流节点）。工具生态含 prebuilt tools、custom functions、OpenAPI、**MCP tools**，并有 **tool confirmation**（HITL 守卫）；自带 Dev UI 与 `adk eval` 评测；部署支持 Docker / Cloud Run / Vertex AI Agent Engine。多语言：Python 2.0、**Java 1.12.0（Apache-2.0，Maven Central `com.google.adk:google-adk`）**、Kotlin/Go/TS；ADK Java 明确集成 **A2A 协议**，但 README 中评测能力标注为 "Coming soon..."。

**对标价值**：ADK 2.0 的"Agent + Workflow + Task API"三层，是本项目**控制面/认知面分层**最接近的外部参照；ADK Java 是少数"Java 原生 + 图执行 + A2A"的可用库。【强-仓库】

### 1.9 Dify

Dify 是平台型产品：可视化 Workflow 画布、Prompt IDE、RAG Pipeline、Agent（自带沙箱，可接插件市场与 MCP server）、LLMOps 日志与性能、观测集成（Opik / Langfuse / Arize Phoenix）、Backend-as-a-Service API；企业版提供自托管、SSO、RBAC 与支持 SLA。许可证是 **Dify Open Source License（基于 Apache 2.0 附加条件）**，不是纯 Apache-2.0。

**对标价值**：Dify 是"产品形态对照物"（画布 + RAG + 插件 + 观测），不是"内核代码参照物"；其可视化与 BaaS 思路可参考，平台级替换不建议。【强-仓库】

### 1.10 LlamaIndex Workflows

`docs.llamaindex.ai` 已重定向到 `developers.llamaindex.ai`，且 Workflows 现落在 **LlamaAgents/Workflows** 章节；该文档站为客户端渲染，本次抓取仅得导航，**正文 NOT_VERIFIED**。因此本报告不对 LlamaIndex Workflows 的事件驱动/step 语义做任何断言，只在矩阵中标注"官方文档导航存在、正文未验证"。可确认的旁证：Langfuse README 列出 LlamaIndex 为受支持集成（Python 回调方式）。【未验证】

### 1.11 MCP 官方规范（2026-07-28）：协议层"硬约束"

MCP 是 JSON-RPC 2.0 的开放协议，定义 Hosts / Clients / Servers，Server 提供 Resources / Prompts / Tools，Client 提供 Elicitation 等能力；规范以 TypeScript schema 为权威。**2026-07-28 版的核心变化**（相对 2025-11-25）：

- **无状态化**：移除协议级 session 与 `Mcp-Session-Id`，移除 `initialize`/`notifications/initialized` 握手；每个请求在 `_meta` 中携带 protocolVersion 与 clientCapabilities，版本不匹配返回 `UnsupportedProtocolVersionError`；新增 `server/discover` 用于能力/版本探测。
- **MRTR（多轮往返）**：以 `InputRequiredResult`（`resultType: "input_required"` + `inputRequests`）取代服务器主动请求模式；所有结果必须携带 `resultType`（complete / input_required）。elicitation 支持 **form mode**（结构化表单）与 **URL mode**（敏感交互不经 client）。
- **订阅模型**：以 `subscriptions/listen` 单一长连接取代 HTTP GET 与 `resources/subscribe`；移除 ping、logging/setLevel 等；tasks 移出核心成为官方扩展（轮询 + 多轮输入 + 持久 handle）。
- **可缓存性**：`tools/list` 等结果要求 `ttlMs` 与 `cacheScope`；建议 `tools/list` 确定性排序以提升 prompt cache 命中率（与 DeepSeek Context Caching 直接相关）。
- **授权**：Authorization 章节仍以 OAuth 系为主，2026-07-28 起要求校验 `iss`（RFC 9207）、弃用 DCR（RFC 7591）转向 **Client ID Metadata Documents**、客户端凭据必须按 issuer 绑定；并新增 MCP 请求头（`Mcp-Method`/`Mcp-Name`）与自定义头机制。
- **可观测**：文档化 OTel trace context 在 `_meta` 的传播约定（`traceparent`/`tracestate`/`baggage`）。
- **弃用**：Roots、Sampling、Logging 进入弃用窗口；HTTP+SSE 传输正式弃用（用 Streamable HTTP）。

**对标价值**：这是本项目 `qknow-mcp` 的**外部硬约束**。目录显示自研 server/client 同时支持 stdio/HTTP 与注解式工具，但版本对齐情况不明——这是本报告最重要的外部风险项（见 §3、§6）。【强】

### 1.12 A2A 协议（v1.0）

A2A 是"agent 与 agent 之间"的互操作协议：核心对象 Task / TaskStatus / TaskState / Message / Part / Artifact；操作为 SendMessage、SendStreamingMessage、GetTask、ListTasks、CancelTask、SubscribeToTask、推送通知配置、Extended Agent Card；发现机制是 **Agent Card**（含 provider、capabilities、skills、interfaces、签名）；传输绑定覆盖 **JSON-RPC、gRPC、HTTP+JSON/REST**；安全对象含 APIKey / HTTPAuth / OAuth2 / OpenID Connect / mTLS，并有 Agent Card 签名与 in-task authorization 语义；文档另有 streaming/async、multi-tenancy、enterprise features、extensions。规范含 "What's New in v1.0" 页面；2026-08-27 官方公告 A2A 加入 **Agentic AI Foundation**。

**对标价值**：本项目已有 gRPC 控制面，A2A 的 gRPC binding 与 Agent Card/Task 生命周期可直接映射为"跨团队/跨系统 agent 互操作"契约；这是从"单体平台"走向"企业 agent 网络"的低成本增量。【强】

### 1.13 Temporal（durable execution 参照）

Temporal 用 **Event History** 记录 Workflow Execution 每一步，失败后从最后事件恢复；Workflow Execution 对本地状态独占，通过消息传递与其他执行/环境通信，可"恰好一次地执行到完成"，时长可秒级到年级；平台由 Temporal Service（Go server + DB）与客户侧 Worker Processes（多语言 SDK）组成，支持自托管或 Temporal Cloud。文档还包括 Event History、Workflow Message Passing、Child Workflows、Failures and error handling、Visibility、Nexus 等章节。

**对标价值**：本项目不需要引入 Temporal（Java 重依赖 + 运维成本），但需要**抄它的语义**：事件历史、确定性重放、activity 重试/超时、消息传递、版本兼容。【强】

### 1.14 OpenTelemetry GenAI 语义约定

原 `opentelemetry.io/docs/specs/semconv/gen-ai/` 页面已标注 "Moved"，GenAI 约定迁至独立仓库 `open-telemetry/semantic-conventions-genai`，覆盖 **GenAI clients 的 spans / metrics / events、MCP 约定、provider-specific 约定（如 OpenAI）**，并用 Weaver 管理对核心约定的依赖。仓库 README 中 Schema URL 仍是 TODO，说明该规范仍在快速演进——**采纳其命名空间与结构，但不要把字段当冻结标准**。旁证：OpenTelemetry 语义约定主站版本为 1.44.0。【强-仓库（字段细节未逐条验证）】

### 1.15 Langfuse（v4）

Langfuse 是开源 LLM 工程平台（MIT，`ee` 目录除外；2026 年起归属 ClickHouse）：Tracing/Observability、Prompt Management、Evaluations（LLM-as-judge、code evaluator、用户反馈、人工标注、自定义管线）、Datasets/Dataset runs、Playground、完整 API；自托管支持 Docker Compose / VM / Kubernetes Helm / Terraform。**v4 采用 observations-first 数据模型**，官方明确 Java 侧"推荐且未来唯一受支持的追踪方式是 OpenTelemetry"，`langfuse-java` 0.3.0 只保留非追踪类 API（prompts/datasets/scores/models 等），并给出 Spring AI 集成示例；v3 追踪端点在 Cloud 保留至 **2026-11-16**。

**对标价值**：本项目 README 已写"Langfuse 可选"，但若仍走旧 ingestion API 或私有 trace 上报，将在 v4 切换时被动。正确姿势是 **OTel 先行、Langfuse 作为后端**。【强】

### 1.16 Java 可直接依赖候选（本报告的重点推荐池）

| 候选 | 关键能力（抓取页可证） | 许可/版本 | 与本项目契合度 |
| --- | --- | --- | --- |
| **Spring AI Alibaba Graph** | StateGraph / Node / Edge / **OverAllState（可序列化、可 checkpoint）** / CompiledGraph（**中断、并行、checkpoint、流式**）/ **InterruptableAction**（中断前/后钩子，HITL）；其上是 Agent Framework（ReactAgent、SequentialAgent） | 版本与许可证未在抓取页声明，需核验；官网 java2ai.com | **最高**：项目已依赖 spring-ai-alibaba（graph-core 是否已在类路径需核验），若可用则无需新运行时；可作为统一图执行内核首选 |
| **LangGraph4j 1.9** | StateGraph、异步与流式（CompletableFuture）、**checkpoints（save/replay）+ breakpoints（pause/resume）**、Studio、PlantUML/Mermaid、多智能体/子图/并行、Spring AI 与 LangChain4j 集成、checkpoint saver 支持 SQLite/Postgres/MySQL/Oracle | MIT（徽章），Maven Central `org.bsc.langgraph4j` | 高：LangGraph 语义的 Java 镜像，适合"学语义 + 借实现"；但为社区项目，需评估维护与生产证据 |
| **Google ADK for Java 1.12.0** | LlmAgent、工具生态、A2A 集成、Dev UI；评测 "Coming soon" | Apache-2.0，`com.google.adk:google-adk` | 中高：A2A/多代理抽象可借鉴；评测缺口明显 |
| **langfuse-java 0.3.0** | 非追踪 API 客户端；追踪走 OTel | 版本 0.3.0；许可证未在抓取页声明，需核验 | 高：与"OTel 先行"路线一致 |

---

## 2 七维对照矩阵

**图例**：每格末尾标证据强度（【强】官方文档正文 / 【强-仓库】官方仓库或文档站 / 【中】官方博客或导航 / 【未验证】）。"生产部署成熟度"综合官方部署文档、托管产品与公开可靠性实践，不代表第三方评测。

### 2.1 通用框架

| 框架 | 核心抽象 | 状态与持久化 | 多智能体编排 | 工具与安全 | 可观测与评测 | 人机协同 | 生产部署成熟度 | 总证据 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **LangGraph + LangSmith** | Graph/Node/Edge、Pregel runtime、Functional API【强】 | **Checkpointer（线程内）+ Store（跨线程）**、time travel、durability 模式；需保留策略防 checkpoint 膨胀【强】 | supervisor/swarm/子图/并行，图即编排【强】 | 工具节点与生态集成；安全主要靠外围与 interrupt【强】 | LangSmith traces/dashboards/alerts/online eval/datasets/feedback【强】 | **Interrupt/Resume 与 time travel 一等公民**，HITL 是最成熟的一家【强】 | Agent Server/托管部署、Cloud/Hybrid/Self-hosted【强】 | 强 |
| **OpenAI Agents SDK** | Agent/Handoff/Guardrail/Runner、Sessions、Sandbox agents【强】 | Sessions（会话记忆）+ RunState（中断恢复）；默认 Responses API 托管状态【强】 | **Agents-as-tools 与 Handoffs 双模**，可混用；code orchestration【强】 | function tools、hosted tools、MCP server、tool guardrails【强】 | 内建 tracing，接评测/微调/蒸馏【强】 | 内建 HITL 与中断审批【强】 | 生产级 SDK；但模型/工具托管绑定 OpenAI，非 OpenAI provider 支持有限【强】 | 强 |
| **Anthropic Research System + Claude Agent SDK** | lead agent + subagents + tools + memory + thinking【中】 | 上下文压缩到外部 memory、断点恢复、checkpoint + 重试【中】 | **orchestrator-worker**，3–5（复杂 10+）并行子代理，effort scaling 规则【中】 | MCP、工具描述工程化（ACI）、guardrails 防失控【中】 | 全链路 tracing + 决策模式监控；LLM-judge 五项 rubric；~20 查询起步【中】 | agent 可在 checkpoint 暂停等人类；SDK 有 approvals/user-input【中】 | rainbow 部署、同步子代理瓶颈、15× token 经济性门槛【中】 | 中 |
| **AutoGen（AgentChat）** | Agent/Team；Core 事件驱动 actor 模型【强-仓库】 | Managing State、Serializing Components；无内建 durable checkpoint 宣称【强-仓库】 | Selector Group Chat、Swarm、Magentic-One、GraphFlow【强-仓库】 | 函数工具；安全依赖外围【强-仓库】 | Logging、Tracing and Observability 章节【强-仓库】 | HITL 教程与 termination 控制【强-仓库】 | Studio；微软已转向 Agent Framework，维护趋缓【强】 | 强-仓库 |
| **AG2 v1.0** | Agent/ask、@tool、Hub+typed channels（AgentOS）【强-仓库】 | **Hub WAL + 审计轨迹**；KnowledgeStore + SummarizeCompact【强-仓库】 | conversation / consulting / discussion / workflow(TransitionGraph)【强-仓库】 | @tool、middleware、observers；HITL hook【强-仓库】 | telemetry / evaluation / testing 文档【强-仓库】 | `context.input()` 工具内暂停 + hitl_hook【强-仓库】 | Apache-2.0；Classic 分叉维护；企业级证据有限【强-仓库】 | 强-仓库 |
| **CrewAI** | Agent/Task/Crew/Flow/Process【强-仓库】 | Memory/Planning/Knowledge 概念；持久化细节未验证【未验证】 | 角色化团队 + 顺序/层级流程；协作与训练【强-仓库】 | 工具库与 skills；安全未验证【未验证】 | testing 概念；与 Langfuse 等观测集成未在抓取页确认【未验证】 | 抓取页未见独立 HITL 概念【未验证】 | Production Architecture 章节、Cloud/AMP 平台【强-仓库】 | 强-仓库 |
| **Semantic Kernel / Microsoft Agent Framework** | SK：Kernel/plugins/functions/planner；AF：Agents / **Harness Agent** / Workflows / Integrations【强】 | AF：session-based state、workflows 状态、长任务与 HITL 状态；SK 持久化弱【强】 | AF 图式与函数式工作流 + harness；SK planner/group chat【强】 | plugins、OpenAPI、hosted MCP、middleware、工具审批【强】 | SK filters/telemetry；AF 有 observability/evaluation 集成【强】 | AF Harness 含工具审批与长任务人工介入【强】 | **无 Java 官方实现**（.NET/Python/Go）；SK Java 仍在但非主航道【强】 | 强 |
| **Google ADK 2.0** | Agent / Workflow（图执行）/ **Task API** / Tools【强-仓库】 | workflow state、routing、retry、嵌套工作流、HITL 状态【强-仓库】 | 层级多代理、任务委派、fan-out/fan-in、循环【强-仓库】 | prebuilt/自定义/OpenAPI/MCP + **tool confirmation**【强-仓库】 | Dev UI + `adk eval` 评测集【强-仓库】 | tool confirmation + Task HITL【强-仓库】 | Docker/Cloud Run/Vertex Agent Engine；ADK Java 评测 "coming soon"【强-仓库】 | 强-仓库 |
| **Dify** | Workflow/Node、Agent、RAG Pipeline、Prompt IDE【强-仓库】 | 平台内会话与日志；durable execution 未验证【未验证】 | 以单 Agent/工作流为主，未见独立多智能体编排章节【未验证】 | 插件市场、MCP server 接入、BaaS API【强-仓库】 | LLMOps 日志 + Opik/Langfuse/Phoenix 集成【强-仓库】 | 工作流人工节点（抓取页未确认）【未验证】 | 自托管 Docker、企业版 SSO/RBAC/SLA；**许可为 Apache-2.0+附加条件**【强-仓库】 | 强-仓库 |
| **LlamaIndex Workflows** | 官方文档导航存在（LlamaAgents/Workflows），正文未取得【未验证】 | 未验证 | 未验证 | 未验证 | 仅旁证：Langfuse 支持 LlamaIndex（Python 回调）【强-仓库】 | 未验证 | 未验证 | 未验证 |

### 2.2 协议与基础设施（"层"而非"框架"）

| 规范/设施 | 核心抽象 | 状态与持久化 | 多智能体编排 | 工具与安全 | 可观测与评测 | 人机协同 | 生产部署成熟度 | 总证据 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **MCP 2026-07-28** | JSON-RPC 2.0；Host/Client/Server；Tools/Resources/Prompts；扩展（Tasks/Skills/MCP Apps）【强】 | **无状态**，per-request capabilities；tasks 扩展提供 durable handle【强】 | 不负责（工具层）【强】 | capability negotiation、**MRTR**、elicitation form/URL、OAuth（iss 校验、DCR 弃用）【强】 | OTel trace context 在 `_meta` 传播；缓存提示 `ttlMs`/`cacheScope`【强】 | **Elicitation + MRTR** 是一等公民的"向用户要信息"机制【强】 | 规范+Registry+SDK 生态；版本化与弃用策略明确【强】 | 强 |
| **A2A v1.0** | Task/Message/Part/Artifact/AgentCard【强】 | Task 生命周期、streaming、push notification【强】 | **agent-to-agent 委派**与发现【强】 | OAuth2/OIDC/APIKey/HTTPAuth/mTLS、AgentCard 签名、in-task authorization【强】 | 未在规范目录确认【未验证】 | 多轮交互章节存在，HITL 语义未验证【未验证】 | JSON-RPC/gRPC/REST 三绑定、multi-tenancy、企业特性；加入 Agentic AI Foundation【强】 | 强 |
| **Temporal** | Workflow/Activity/Worker/**Event History**【强】 | **durable execution 标杆**：事件历史 + 恢复 + 恰好一次到完成【强】 | 非 agent 专用，但可承载 agent 长流程【强】 | activity 重试/超时/错误处理；消息传递【强】 | Visibility、Web UI、tracing 生态【强】 | signals/updates 支持人工介入【强】 | 自托管/Cloud 成熟；Go server + 多语言 SDK【强】 | 强 |
| **OTel GenAI semconv** | GenAI spans/metrics/events + MCP + provider-specific【强-仓库】 | n/a | agent spans（规范内）【强-仓库】 | MCP 约定【强-仓库】 | **本行即能力**；Schema URL 仍 TODO，演进中【强-仓库】 | n/a | 与主流厂商/观测后端生态绑定，处于快速演进期【强-仓库】 | 强-仓库 |
| **Langfuse v4** | Observation/Trace/Session/Dataset/Score/Prompt【强-仓库】 | ClickHouse 存储；observations-first 模型【强-仓库】 | 无（观测层）【强-仓库】 | 无（观测层）【强-仓库】 | tracing/prompt 管理/评测/dataset/playground/API【强-仓库】 | 标注队列/人工反馈【强-仓库】 | 自托管 Docker/VM/K8s Helm/云；MIT（ee 除外）【强-仓库】 | 强-仓库 |

### 2.3 Java 直接候选

| 候选 | 核心抽象 | 状态与持久化 | 多智能体编排 | 工具与安全 | 可观测与评测 | 人机协同 | 生产部署成熟度 | 总证据 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **Spring AI Alibaba Graph** | StateGraph/Node/Edge/**OverAllState**/CompiledGraph/InterruptableAction【强-仓库】 | OverAllState 可序列化并支持 **checkpoint/resume**；并行节点【强-仓库】 | 图式多智能体 + Agent Framework（ReactAgent/SequentialAgent）【强-仓库】 | 依赖 Spring AI 工具/MCP 生态（版本能力未验证）【未验证】 | 未在抓取页声明，需核验【未验证】 | **InterruptableAction**（前后钩子）适配审批/多轮【强-仓库】 | 阿里开源、Spring Boot 深度集成；许可证未在抓取页声明【未验证】 | 强-仓库 |
| **LangGraph4j 1.9** | StateGraph、async/streaming、hooks、子图【强-仓库】 | **checkpoints（save/replay）+ breakpoints（pause/resume）**；SQLite/Postgres/MySQL/Oracle saver【强-仓库】 | 多智能体、threads、子图、并行分支【强-仓库】 | 集成 LangChain4j/Spring AI；安全靠外围【强-仓库】 | Studio（Spring Boot/Quarkus/Jetty）、PlantUML/Mermaid【强-仓库】 | wait-user-input、time travel【强-仓库】 | 社区项目（MIT）；生产证据需自证【强-仓库】 | 强-仓库 |
| **Google ADK for Java 1.12.0** | LlmAgent、tools、A2A、Dev UI【强-仓库】 | 依赖 ADK 运行时（未验证细节）【未验证】 | 层级多代理 + A2A【强-仓库】 | 工具生态；安全未在 README 展开【未验证】 | 评测 "Coming soon"【强-仓库】 | HITL 能力未在 Java README 展开【未验证】 | Apache-2.0、Maven Central；Pre-GA 提示（README "Preview"）【强-仓库】 | 强-仓库 |

### 2.4 横向解读（矩阵读数）

1. **状态层已经收敛为三种契约**：线程内 checkpointer（LangGraph/LangGraph4j/Spring AI Alibaba）、跨线程 store（LangGraph）、事件历史 durable execution（Temporal）。本项目若只有"快照+receipt"，缺的是**契约分层**与**保留/清理策略**。
2. **编排层只有两种被验证的形态**：图/工作流（显式）与 orchestrator-worker（动态）；"swarm/debate"属于研究型拓扑，公开生产证据少，必须自带预算与终止条件。
3. **工具与安全已经不靠框架，而靠协议**：MCP（工具）+ A2A（agent 互操作）把互操作与授权从框架里抽走；框架只提供 guardrail/confirmation。
4. **可观测层的事实标准是 OTel GenAI + 一家观测后端**（LangSmith/Langfuse/Phoenix/Opik）；"自研 trace 格式"在 2026 年已无生态收益。
5. **HITL 的正确语义是"可中断 + 可恢复 + 可时间旅行"**，而不是"弹窗审批"；LangGraph/LangGraph4j/Spring AI Alibaba 都提供 interrupt 原语。
6. **生产成熟度的分水岭是部署语义**：托管 runtime + 灰度/rainbow + 断点恢复 + 评测闭环；模型/框架本身不是分水岭。

---

## 3 本项目机制成熟度评分与差距

### 3.1 评分口径

- **1**：缺失或玩具级；**2**：有局部实现/宣称，但无契约、无评测；**3**：能力面完整、可跑通主路径，但缺标准化契约或生产证据；**4**：接近顶级框架同级（契约清晰、可恢复、可观测、有评测）；**5**：达到或超过当前顶级开源框架的公开成熟度。
- 因本项目代码事实不在本报告职责内，评分全部基于 **README/目录"能力宣称" + 未验证折减**：若一项能力只有目录名而无端到端证据，最高不超过 3；若连宣称都没有，给 1–2。
- 评分不是审计结论，**不能替代其他审计员的代码事实**。

### 3.2 评分与差距

| # | 机制 | 本项目宣称（未验证） | 评分 | 顶级参照 | 关键差距（要拿到 4 分需要什么） |
| --- | --- | --- | --- | --- | --- |
| 1 | Agent 运行时与规划 | 规划/ReAct、反思、ToT、PRM、MCTS、技能库、循环卫兵 | **3** | LangGraph【强】、ADK 2.0 Task API【强-仓库】、Agents SDK【强】 | 缺"统一原语边界"：规划/执行/工具/记忆/守卫应有稳定的接口与事件模型；缺可复现的运行时语义说明与公开基准 |
| 2 | 状态与持久化 | DAG 快照恢复、micro-checkpoint、receipt、冷备隔离 | **3** | LangGraph checkpointer+store【强】、Temporal event history【强】 | 缺三层契约（线程内 / 跨线程 / 事件历史）与**保留策略**；缺"时间旅行/回放"的对外语义；checkpoint 增长与清理未见策略 |
| 3 | 多智能体编排 | swarm、debate、supervisor-worker、blackboard、bidding、consensus、saga、动态拓扑 | **3** | Anthropic orchestrator-worker【中】、AutoGen Teams【强-仓库】、AG2 Network【强-仓库】 | 拓扑丰富但缺**成本闸门/终止条件/评测**三件套；缺任务级互操作协议（A2A）；生产证据薄弱 |
| 4 | 工具与 MCP 安全 | MCP server+client（stdio/HTTP）、注解式 tool/resource/prompt、OpenAPI 适配、QuadDefense、租约、工具权限 | **3**（覆盖面接近 4） | MCP 2026-07-28【强】、ADK MCP+confirmation【强-仓库】 | **规范版本漂移风险**（无状态化/MRTR/tasks 扩展/OTel 传播）；缺对外一致性测试与版本矩阵；安全管线需与规范授权语义对齐 |
| 5 | 可观测与评测 | Langfuse 可选、LangFuseTracingService、分层 trace、Judge/Eval/Feedback/Evolution | **2** | OTel GenAI+Langfuse【强-仓库】、LangSmith【强】 | 缺 OTel GenAI 语义约定与跨进程 trace 传播；缺 trace→dataset→experiment→online eval 闭环；私有 trace 格式无生态 |
| 6 | 人机协同（HITL） | Flow 审批、前端画布、可选中断（目录存在但未验证） | **3** | LangGraph interrupts/time travel【强】、Spring AI Alibaba InterruptableAction【强-仓库】 | 缺统一"中断点枚举 + 恢复幂等 + 时间旅行"语义；前后端契约需统一 resume token |
| 7 | 生产部署成熟度 | Java 21/Spring Boot/gRPC、脚本化启动、bare-metal PG/Redis、Langfuse 可选 | **2** | Temporal【强】、LangGraph deploy【强】、ADK Cloud Run/Vertex【强-仓库】 | 缺 durable execution 语义、灰度/rainbow、checkpoint 保留与容量治理、可重复的发布/回滚演练 |
| 8 | RAG 与记忆（差异化） | PgVector+关键词+图谱、GraphRAG、海马体双轨记忆、艾宾浩斯衰减、reflexion、记忆分页 | **3**（差异化潜力 4–5） | LlamaIndex/LangChain 生态（未逐项验证） | 缺公开评测口径（召回/延迟/成本/幻觉）与消融；记忆淘汰需与"冷备可逆隔离"证据闭环 |

**加权判断**：本项目在"能力广度"上明显超过同规模团队（目录已出现大量顶级论文机制），但在**契约层、标准层、评测层**三处低于顶级框架，整体处于"**3 分能力面、2 分证据面**"。因此路线图不应再增加机制，而应**把已有机制收敛成可验证契约**。

### 3.3 五个关键差距（按影响排序）

1. **抽象未收敛**：同一"图执行/状态恢复"能力可能同时存在于 Hermes DAG、flow 引擎、checkpoint 引擎与 Spring AI Alibaba Graph 中；这会造成语义分叉与双维护。顶级框架都是"一个运行时 + 多个上层模式"。
2. **无标准可观测**：OTel GenAI + Langfuse v4 已是事实标准；自研 trace 会失去所有后端与生态工具（且 Langfuse v4 只认 OTel）。
3. **无评测闭环**：没有 trace→dataset→experiment→online eval 的闭环，就没有"机制是否有效"的判据。Anthropic 的经验是 ~20 条真实查询即可起步。
4. **MCP 规范漂移**：2026-07-28 是无状态化的破坏性版本；如果实现基于旧版，会在企业对接、SDK 兼容、缓存/成本优化上吃亏（尤其 `tools/list` 确定性排序与 prompt cache）。
5. **HITL 语义不统一**：审批、暂停、恢复、回滚若分散在 flow/前端/Judge 多处，就无法提供"时间旅行"和幂等恢复，生产排障成本高。

### 3.4 不应自我贬低之处（差异化必须承认）

- **Hermes 的算法密度**（ToT/ReAct/PRM/MCTS、swarm consensus、海马体记忆、循环卫兵、SAGA 补偿）远超一般企业平台，属于**论文级差异化**；
- **qknow-mcp 的企业安全管线**（QuadDefense、租约、注解式 provider）在开源生态里少见，是**企业私有化的卖点**；
- **Java 21 Record 不可变凭单 + 常量时间验签 + 三级冷备**是合规向差异化，不应为了"对齐框架"而丢弃；
- **Vue3 DAG 画布 + HITL + Apple Liquid Glass 设计系统**是产品体验差异化，顶级开源框架普遍没有；
- **DeepSeek Context Caching 成本工程**是约束下的独特优势（MCP `tools/list` 确定性排序、固定前缀、缓存友好提示词都能放大它）。

---

## 4 抄与不抄的裁决

### 4.1 抄（8 项，全部给了 Java 侧移植路径）

| # | 抄什么 | 来源（URL 见附录） | Java 侧怎么落 |
| --- | --- | --- | --- |
| C1 | **checkpointer / store 分层**：线程内状态、跨线程记忆、事件历史各自独立 | LangGraph Persistence【强】 | 定义 `ThreadStateStore` / `LongTermStore` / `EventHistory` 三个接口；Hermes 现有快照作为 `ThreadStateStore` 适配实现；后端用 PostgreSQL（项目已有） |
| C2 | **interrupt / resume / time travel 语义** | LangGraph Interrupts/Time travel（文档导航）【强】；Spring AI Alibaba InterruptableAction【强-仓库】 | 统一 `InterruptPoint` 枚举 + `ResumeToken`（Java Record，不可变）；复用 Spring AI Alibaba `InterruptableAction` 风格的前后钩子 |
| C3 | **agents-as-tools 与 handoffs 双模** | OpenAI Agents SDK Orchestration【强】 | 在 Hermes `AgentOrchestrator` 上抽象 `delegate()` 与 `handoff()` 两种语义，禁止混用同一条链路；配预算与终止条件 |
| C4 | **orchestrator-worker + effort scaling** | Anthropic Research System【中】 | 在 planner 输出中强制包含 `subagent_count/tool_call_budget/output_format` 四元组；按查询复杂度分级（简单 1 子代理 3–10 次调用；复杂 10+ 子代理） |
| C5 | **评测三项 rubric + ~20 条种子集** | Anthropic Research System【中】 | 自研轻量 Eval 服务：factual/citation/completeness/tool-efficiency 四维，DeepSeek 作 judge；数据落 Langfuse datasets |
| C6 | **MCP 2026-07-28 语义**：无状态、per-request capabilities、MRTR、tasks 扩展、`ttlMs/cacheScope`、OTel `_meta` 传播 | MCP 规范与 Key Changes【强】 | `qknow-mcp` 增加协议版本适配层；stdio 与 Streamable HTTP 双实现；先做"版本探测 + 兼容拒绝"再做全量迁移 |
| C7 | **A2A Agent Card + Task 生命周期** | A2A 规范【强】 | 用现有 gRPC 定义 `AgentCard` 注册表与 Task 状态机；先做内部 agent 目录（发现），再做跨系统委派 |
| C8 | **OTel GenAI + Langfuse v4 追踪** | OTel GenAI 仓库【强-仓库】、langfuse-java【强-仓库】 | Spring Boot 引入 OTel Java SDK，span 命名遵循 GenAI 约定；gRPC 透传 `traceparent`；Langfuse 只作后端 |

### 4.2 不抄（8 项）

| # | 不抄什么 | 理由 |
| --- | --- | --- |
| N1 | **引入 Python/Node 运行时**（LangGraph、Agents SDK、AutoGen、CrewAI、Dify、LlamaIndex） | 违反 Java 21 单一运行时硬约束；跨语言进程边界会把"状态/中断/恢复"复杂度放大一个量级 |
| N2 | **Responses API 托管状态与托管工具** | 生成侧唯一 DeepSeek；托管状态会绑定 OpenAI 生态，且与"私有化 + 数据不出域"冲突 |
| N3 | **托管的 Deep Agents / 云评测** | 企业私有化要求；评测数据即知识资产，不能出域 |
| N4 | **Dify/Coze/Flowise 平台级替换** | 项目控制面已有 RBAC/会话/RAG/工具能力，替换成本远大于收益；且 Dify 为 Apache-2.0 + 附加条件许可 |
| N5 | **Anthropic 的"默认多智能体"** | 15× token 成本在本项目不可接受；只在"高价值、可并行"任务上启用，并强制预算 |
| N6 | **AutoGen Core 的 actor 事件模型作为新核心** | 与现有 Hermes/flow 抽象冲突；AutoGen 本身已被 Agent Framework 取代 |
| N7 | **Semantic Kernel Java 作为新核心** | 官方后继 Agent Framework 无 Java 实现；SK Java 处于非主航道，长期维护风险高 |
| N8 | **自研 MCP 传输/协议语义的无边界扩张** | 协议层应由规范驱动；自研只保留安全增强（QuadDefense/租约）作为扩展点，不做协议分叉 |

### 4.3 自研保留（7 项）

| # | 保留什么 | 理由 |
| --- | --- | --- |
| K1 | **Hermes 认知内核**（ToT/ReAct/PRM/MCTS、swarm consensus、海马体记忆、循环卫兵、SAGA 补偿） | 论文级差异化，替换会丢掉核心资产；只需补契约与评测 |
| K2 | **qknow-mcp 企业安全管线**（QuadDefense、租约、注解式 provider、OpenAPI 适配） | 企业私有化卖点；应作为 MCP 规范的"安全扩展层"而非替代协议 |
| K3 | **Java 21 Record 不可变凭单 + `MessageDigest.isEqual` 常量时间 + 三级冷备隔离** | 合规/安全差异化，外部框架不提供 |
| K4 | **Vue3 DAG 画布 + HITL + Apple Liquid Glass 设计系统** | 产品体验差异化；外部框架没有对应资产 |
| K5 | **PgVector + Neo4j GraphRAG 双引擎知识路径** | 知识引擎差异化；只补评测与消融 |
| K6 | **DeepSeek Context Caching 成本工程** | 约束下的独有优势；用 MCP 缓存提示与固定前缀放大 |
| K7 | **Hermes/控制面 gRPC 分层** | 与 A2A 的 gRPC binding 天然契合，是未来 agent 网络的底座 |

### 4.4 应替换/收敛的"重复造轮子"清单

| 自研/宣称模块 | 重复对象 | 裁决 | 理由 |
| --- | --- | --- | --- |
| Hermes DAG / workflow 引擎（若与 flow 引擎双轨） | Spring AI Alibaba Graph（依赖已引入，graph-core 可用性待核验）、LangGraph4j | **收敛为单一图内核**，Hermes 只保留算法节点 | 双运行时会导致中断/恢复语义分叉；Spring AI Alibaba Graph 原生 `OverAllState/CompiledGraph/InterruptableAction` 已覆盖 |
| 私有 checkpoint/快照格式 | LangGraph checkpointer 语义、Spring AI Alibaba checkpoint、LangGraph4j savers | **抽象收敛、存储保留**（PostgreSQL） | 保留 receipt 审计与冷备；但对外契约用标准三层模型 |
| 私有 trace 采集与上报 | OTel GenAI semconv + Langfuse v4 | **替换为标准 OTel**，本地 receipt 保留 | v4 后 Langfuse 只认 OTel；自研格式无生态 |
| 自研 eval/Judge 数据格式 | Langfuse datasets/experiments/scores | **数据模型对齐 Langfuse**，Judge 逻辑自研 | 保留 AI Judge 差异化，但数据集与实验必须可复用 |
| 自研 MCP server/client 协议层 | MCP 官方规范/SDK（含 Spring AI MCP 能力，需核验版本） | **协议层对齐规范**，安全层自研 | 规范已无状态化，自研协议分叉会失去企业互联能力 |
| 自研 agent 间消息协议 | A2A（gRPC binding） | **对外用 A2A，对内保留 Hermes 消息模型** | 内外分层，避免对外私有协议锁定 |

### 4.5 Java 生态可移植性总表

| Python 框架 | 可移植的"概念" | 不可移植/不建议 | Java 替代 |
| --- | --- | --- | --- |
| LangGraph | checkpointer/store 分层、interrupt、time travel、subgraph | 运行时与 SDK | Spring AI Alibaba Graph / LangGraph4j |
| OpenAI Agents SDK | agents-as-tools vs handoffs、guardrails、sessions | Responses API 托管、tracing 后端 | 自研抽象 + OTel |
| Anthropic Research | orchestrator-worker、effort scaling、rubric 评测、rainbow 部署 | 15× token 默认、Anthropic 模型 | 自研 planner/swarm + 预算闸门 |
| AutoGen / AG2 | Team 模式、state/serialize、Hub+WAL | actor 运行时、Python 包 | 自研（Hermes 已有类似结构） |
| CrewAI | 角色/任务/流程建模 | 运行时与云平台 | 前端画布 + Flow 引擎 |
| Microsoft AF | Harness Agent 三件套（压缩/审批/可观测）、workflows | .NET/Python 运行时 | 自研或 Spring AI Alibaba |
| Google ADK | Agent/Workflow/Task API、tool confirmation、A2A | Python 运行时 | ADK for Java 1.12.0（谨慎） |
| Dify | 画布/BaaS/观测集成产品形态 | 平台替换 | Vue3 画布自研 |
| LlamaIndex Workflows | 事件驱动 step 语义（**未验证**） | 运行时 | 不建议在未验证前投入 |

---

## 5 优化路线图（P0–P2，共 12 项）

> 约定：人日为**粗略量级**（1 人日 ≈ 6 小时有效工程），不含跨团队评审与灰度窗口。每项都必须有 feature flag 与回滚路径；P0 建议在 4–6 周内完成，P1 在 1 个季度内，P2 视资源。
> 原则：**不增加新机制，只收敛已有机制并补契约/标准/评测**。

### 5.1 P0（先做，否则后续都会返工）

#### P0-1 统一图执行内核（收敛双轨）
- **目标**：把"flow 引擎 / Hermes DAG / 状态恢复"收敛到单一执行语义与单一 checkpoint 契约。
- **借鉴来源**：Spring AI Alibaba Graph（StateGraph/OverAllState/CompiledGraph/InterruptableAction，https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-graph-core ）【强-仓库】；LangGraph Persistence（https://docs.langchain.com/oss/python/langgraph/persistence ）【强】。
- **最小落地方案（Java 侧）**：定义 `ExecutionKernel` 接口（`start/resume/interrupt/snapshot/replay`），以 Spring AI Alibaba Graph 为默认实现；Hermes 算法节点通过 `NodeAction` 适配器接入，不改算法实现；对外只暴露一套 DAG JSON schema。
- **预估工作量**：10 人日（2 人 × 1 周）。
- **风险与回滚**：语义差异导致旧流程回归失败——用双跑对比（同输入比对节点序列/输出）+ feature flag 切换旧引擎；保留旧引擎 1 个版本周期。
- **验收指标**：同一 DAG 在新旧引擎上的输出一致率 ≥99%；中断/恢复成功率 100%（100 次抽样）；checkpoint 体积与恢复时延写入基线。

#### P0-2 可观测性标准化（OTel GenAI + Langfuse v4）
- **目标**：以 OTel 为唯一 trace 出口，打通控制面→gRPC→Hermes→工具的全链路。
- **借鉴来源**：OTel GenAI semconv（https://github.com/open-telemetry/semantic-conventions-genai ）【强-仓库】；Langfuse v4 + langfuse-java（https://github.com/langfuse/langfuse-java ）【强-仓库】。
- **最小落地方案（Java 侧）**：Spring Boot 引入 OTel Java SDK 与 OTLP exporter；span 命名遵循 `gen_ai.*` 语义；gRPC 透传 `traceparent`；Langfuse 走 OTel endpoint（`x-langfuse-ingestion-version: 4`）；保留 Java Record receipt 作为本地审计。
- **预估工作量**：6 人日。
- **风险与回滚**：Langfuse v4 数据模型切换导致历史面板不可用——先在 staging 并行双写 2 周，再切主；保留旧 v3 读侧只读至 2026-11-16。
- **验收指标**：跨 3 个服务（server/hermes/mcp）的 trace 关联率 ≥95%；token/成本/延迟面板可查；v4 兼容检查清单通过。

#### P0-3 MCP 规范版本对齐（2026-07-28）
- **目标**：把 `qknow-mcp` 从"能连"升级为"规范一致 + 可互操作 + 缓存友好"。
- **借鉴来源**：MCP 2026-07-28（https://modelcontextprotocol.io/specification/2026-07-28 ）与 Key Changes（https://modelcontextprotocol.io/specification/2026-07-28/changelog ）【强】。
- **最小落地方案（Java 侧）**：增加协议适配层：`_meta` 携带 protocolVersion/capabilities；实现 `server/discover`；MRTR（`InputRequiredResult`/`inputRequests`/`inputResponses`）；`tools/list` 确定性排序 + `ttlMs/cacheScope`；stdio 与 Streamable HTTP 双传输；`traceparent` 透传。
- **预估工作量**：12 人日。
- **风险与回滚**：破坏性变更影响存量客户端——保留旧版协商分支（按版本降级），灰度按 server 维度切；提供"兼容矩阵"文档。
- **验收指标**：官方/社区 MCP 客户端互通测试通过率 ≥90%；旧客户端降级可用率 100%；`tools/list` 缓存命中率提升（以 DeepSeek prompt cache 命中率验证）。

#### P0-4 HITL 中断/恢复语义统一
- **目标**：把"审批"升级为"可中断、可恢复、可时间旅行"的统一语义。
- **借鉴来源**：LangGraph Interrupts/Time travel（文档导航，https://docs.langchain.com/oss/python/langgraph/persistence 同站）【强】；Spring AI Alibaba `InterruptableAction`（同上）【强-仓库】。
- **最小落地方案（Java 侧）**：`InterruptPoint` 枚举（before/after/外部事件）+ `ResumeToken`（Record，含 checkpoint id、节点 id、状态摘要、签名）；前端画布以 token 恢复；恢复操作幂等。
- **预估工作量**：6 人日。
- **风险与回滚**：状态摘要与真实状态不一致——token 只做"指针"不做"数据"，恢复时校验 checkpoint 哈希；旧审批流程保留兼容。
- **验收指标**：100 次随机中断/恢复无状态漂移；恢复耗时 P99 < 500ms；前端可回放最近 10 个检查点。

#### P0-5 评测基线（20 条种子 + 四维 rubric）
- **目标**：让"机制是否有效"有判据，先建最小评测闭环。
- **借鉴来源**：Anthropic Research System 评测方法（https://www.anthropic.com/engineering/multi-agent-research-system ）【中】；Langfuse Evaluations/Datasets（https://langfuse.com/docs/evaluation/overview ，README 引用）【强-仓库】。
- **最小落地方案（Java 侧）**：从日志取 20 条真实查询入 Langfuse dataset；DeepSeek 作 judge，按 factual/citation/completeness/tool-efficiency 打分；每次发布跑一次回归。
- **预估工作量**：5 人日。
- **风险与回滚**：judge 偏差导致误判——保留人工复核 10% 抽样；评测结果只作发布门禁的非阻断项，先观察 2 个版本。
- **验收指标**：20 条集合可一键回归；四维分数与人工评分 Spearman ≥0.6；发布回归耗时 <10 分钟。

### 5.2 P1（一个季度内）

#### P1-1 durable execution 语义（不引入 Temporal）
- **目标**：用事件历史 + 确定性重放 + activity 重试 + 版本兼容，补齐"长任务可靠性"。
- **借鉴来源**：Temporal《What is Temporal?》（https://docs.temporal.io/temporal ）【强】。
- **最小落地方案（Java 侧）**：为每次 agent run 落 `EventHistory`（PostgreSQL 表，append-only）；工具调用封装为 `Activity`（超时/重试/幂等键）；版本号写入 checkpoint，支持"旧 run 用旧逻辑"。
- **预估工作量**：15 人日。
- **风险与回滚**：事件表膨胀——分区 + TTL + 冷备归档；语义迁移风险用双写验证。
- **验收指标**：kill -9 后恢复成功率 ≥99%；重复副作用 0（幂等键校验）；历史回放一致率 100%。

#### P1-2 多智能体编排规约（orchestrator-worker + 双模委派）
- **目标**：把"拓扑丰富"变成"可预算、可终止、可评测"的编排。
- **借鉴来源**：Anthropic Research System【中】；OpenAI Agents SDK Orchestration【强】。
- **最小落地方案（Java 侧）**：planner 输出强制含 `delegationMode(handoff|as_tool)`、`subagentCount`、`toolBudget`、`stopCondition`；超预算熔断；每次委派落 receipt。
- **预估工作量**：8 人日。
- **风险与回滚**：熔断误杀——预算分级 + 只对"高价值任务"启用；保留全量开关。
- **验收指标**：多智能体任务 token 成本相对基线可控（≤1.5× 单 agent 预算内完成率 ≥80%）；死循环 0；任务成功率提升有统计口径。

#### P1-3 A2A Agent Card 与任务生命周期
- **目标**：为"企业 agent 网络"预留互操作契约。
- **借鉴来源**：A2A 规范（https://a2a-protocol.org/latest/specification/ ）【强】。
- **最小落地方案（Java 侧）**：用现有 gRPC proto 定义 `AgentCard`/`Task`/`TaskStatus`/`Artifact`；先做内部 agent 注册与发现（只读），再做跨系统 SendMessage 的受控试点。
- **预估工作量**：8 人日。
- **风险与回滚**：协议实现不完整导致对外承诺——先只做"发现 + 只读 Card"，委派能力后置。
- **验收指标**：内部 agent 目录可列出 ≥N 个 agent 的 Card；跨系统试点任务成功率 ≥90%；Card 签名校验通过。

#### P1-4 工具 ACI 与安全治理
- **目标**：把工具描述/权限/成本纳入治理，降低"工具选错"与越权风险。
- **借鉴来源**：Anthropic《Building effective agents》工具设计原则（https://www.anthropic.com/engineering/building-effective-agents ）【中】；MCP tools/授权（规范）【强】。
- **最小落地方案（Java 侧）**：工具描述模板（用途/边界/示例/失败模式）；工具级 RBAC 与配额；工具调用审计（已有 trace 基础）。
- **预估工作量**：6 人日。
- **风险与回滚**：描述变更影响模型行为——版本化工具描述，A/B 对比后切换。
- **验收指标**：工具误选率下降（以评测集统计）；越权调用 0；每个工具都有配额与超时。

#### P1-5 Java 图/状态候选 PoC（只评估不替换）
- **目标**：确认 Spring AI Alibaba Graph vs LangGraph4j vs ADK Java 的取舍，形成决策记录（ADR）。
- **借鉴来源**：LangGraph4j（https://langgraph4j.github.io/langgraph4j/main/ ）【强-仓库】；ADK Java（https://github.com/google/adk-java ）【强-仓库】。
- **最小落地方案（Java 侧）**：同一最小场景（3 节点 + 1 中断 + 1 恢复）各写 1 个 PoC；比较 API 稳定性、许可、维护活跃度、checkpoint 后端。
- **预估工作量**：5 人日。
- **风险与回滚**：PoC 代码污染主仓——放 `experiment/` 沙箱，不合并。
- **验收指标**：ADR 一页纸结论 + 代码对比矩阵；主选与备选明确。

### 5.3 P2（视资源）

#### P2-1 回归/在线评估管线（LangSmith 式闭环）
- **目标**：trace→dataset→experiment→online eval 的持续质量闭环。
- **借鉴来源**：LangSmith Observability（https://docs.langchain.com/langsmith/observability ）【强】；Langfuse（同上）【强-仓库】。
- **最小落地方案（Java 侧）**：定时从 trace 采样入 dataset；发布前跑 experiment；线上用规则 + 抽样 judge 做 online eval。
- **预估工作量**：10 人日。
- **风险与回滚**：评测成本上升——采样率与预算上限硬编码；可随时关。
- **验收指标**：每周自动产出质量报告；回归拦截率与误报率可度量。

#### P2-2 生产部署成熟度（灰度/rainbow + 容量治理）
- **目标**：把"能跑"升级为"可灰度、可回滚、可容量规划"。
- **借鉴来源**：Anthropic rainbow deployment 实践【中】；LangGraph deploy 与 checkpoint 保留策略【强】。
- **最小落地方案（Java 侧）**：K8s/Helm（或现有部署脚本）双版本并行 + 流量按会话/租户切；checkpoint/事件表分区与 TTL；发布前检查"运行中 agent 是否可继续"。
- **预估工作量**：10 人日。
- **风险与回滚**：双版本状态不兼容——版本号写入 checkpoint，路由层按版本亲和；保留一键回退。
- **验收指标**：灰度发布中断会话率 <1%；回滚 <5 分钟；checkpoint 存储增长率可控（月度 <20%）。

### 5.4 依赖关系与预算

- 依赖：P0-1 → P0-4（中断语义依赖统一内核）；P0-2 → P1-4（工具审计依赖 trace）；P0-3 → P1-3（A2A 试点依赖 MCP 工具层稳定）；P0-5 → P2-1。
- 预算合计：P0 = 39 人日；P1 = 42 人日；P2 = 20 人日；**合计 ≈101 人日**（不含评审、灰度与外部合规审批）。
- 建议节奏：第 1–2 周 P0-1/P0-2 并行；第 3–4 周 P0-3/P0-4；第 5 周 P0-5；P1 按季度排期。

---

## 6 未验证与风险

### 6.1 NOT_VERIFIED 清单

| 项 | 状态 | 影响 |
| --- | --- | --- |
| LlamaIndex Workflows 正文（事件驱动/step 语义） | NOT_VERIFIED（文档站重定向 + 客户端渲染，仅得导航） | 本报告不将其纳入可抄清单；如需要，须由后续任务用可渲染来源补证 |
| OpenAI Responses API 的字段与状态语义 | PARTIALLY_VERIFIED（仅 Agents SDK 官方页"默认使用 Responses API"的表述） | 不影响"不抄托管状态"的裁决 |
| MCP Authorization 章节的逐条 MUST/SHOULD | PARTIALLY_VERIFIED（仅到 2026-07-28 Key Changes 的摘要级：iss 校验、DCR 弃用、issuer 绑定） | 落地前必须读 Authorization 正文并做一致性测试 |
| OTel GenAI 具体属性/指标名与稳定性 | PARTIALLY_VERIFIED（仓库 README 明确覆盖 spans/metrics/events、MCP、provider-specific；Schema URL 仍 TODO） | 不能把字段当冻结标准；实现需版本化 |
| A2A 规范版本号与全部 MUST 条款 | PARTIALLY_VERIFIED（规范目录 + "What's New in v1.0" 导航；正文被截断） | 试点前需完整读规范 |
| CrewAI 的持久化/安全/HITL 细节 | NOT_VERIFIED（仅文档导航） | 不作为状态层参照 |
| Dify 工作流人工节点与多智能体能力 | NOT_VERIFIED（README 未提，导航未确认） | 仅作产品形态参照 |
| Semantic Kernel for Java 的维护状态/版本 | NOT_VERIFIED（README 未声明弃用；官方后继 Agent Framework 无 Java） | 不建议押注为新核心 |
| Spring AI Alibaba Graph / ADK Java 的许可证与生产证据 | PARTIALLY_VERIFIED（Java README 声明 Apache-2.0；Spring AI Alibaba Graph 抓取页未声明许可） | 引入前须做许可证与 CVE 审查 |
| 本项目全部机制的实际质量 | 宣称未验证（仅 README/目录） | 评分只代表"能力上限"，代码事实以其他审计员结论为准 |

### 6.2 外部时效性风险

1. **MCP 2026-07-28 破坏性变更**：无状态化、MRTR、tasks 扩展、SSE 可恢复性移除。若本项目依赖旧版（例如假定 `initialize` 握手/会话），企业对接与 SDK 兼容会出问题。**处置：P0-3 优先，先做版本协商与降级。**
2. **Langfuse v4 切换窗口（2026-11-16）**：v3 追踪端点 Cloud 到期；自托管升级 v4 后旧 API 立即不可用。**处置：P0-2 必须在窗口前完成 OTel 迁移。**
3. **微软 Agent Framework 演进**：SK/AutoGen 已被官方定义为"前代"，Java 空缺可能长期存在。**处置：不新增 SK Java 依赖；对 ADK Java 保持"PoC 级"投入。**
4. **A2A 治理变化**：2026-08-27 加入 Agentic AI Foundation，后续规范节奏可能变化。**处置：只做 Card/Task 最小子集，避免深绑定。**
5. **OTel GenAI 仍在演进**（Schema URL TODO）：属性名可能变化。**处置：版本化常量 + 语义映射层。**

### 6.3 内部风险

1. **"宣称 ≠ 实现"**：本报告全部项目侧判断基于 README/目录；若实际实现与宣称有差距，评分需下调，路线图工期需上调。
2. **双轨收敛风险**：P0-1 若处理不当，会出现"新旧引擎同时活着"的长期维护成本；必须设明确退役日期。
3. **自研协议层兼容性**：`qknow-mcp` 若已深度定制旧版语义，规范迁移工作量可能超过 12 人日；建议先做差异盘点（1 人日）再承诺。
4. **评测引入的"指标工程"风险**：若一开始就追求大而全评测集，会重蹈"迟迟不建 eval"的覆辙；坚持 20 条起步。
5. **成本风险**：多智能体 15× token 是公开事实；任何 swarm/debate 默认开启都可能吞噬预算。必须默认关闭、按任务开启。

### 6.4 合规与许可风险

| 对象 | 许可（抓取页证据） | 风险提示 |
| --- | --- | --- |
| LangGraph4j | MIT（README 徽章） | 低；但社区项目需评估维护活跃度 |
| Google ADK for Java | Apache-2.0（README） | 低；注意 "Preview" 声明与评测缺失 |
| Semantic Kernel for Java | MIT（README） | 低；但非主航道 |
| AG2 | Apache-2.0（README） | 低（仅作参考，不引入） |
| Langfuse | MIT，`ee` 目录除外（README） | 企业功能可能落在 ee；需确认自托管所需功能许可 |
| Dify | Dify Open Source License（Apache-2.0 + 附加条件） | 不可当纯 Apache-2.0 处理；不引入其代码 |
| Spring AI Alibaba Graph | 抓取页未声明 | 引入前必须核验 LICENSE 与 NOTICE |
| 本项目自身 | README 声明"无统一根级许可证，需按模块核对" | 对外交付/再分发前必须补齐许可审计 |

### 6.5 移交给 Lead 与其他审计员的核验清单

1. `qknow-mcp` 实际支持的 MCP 协议版本、是否实现 `initialize`/session、是否支持 Streamable HTTP、`tools/list` 顺序是否确定（影响 DeepSeek 缓存）。
2. `qknow-hermes` 的 checkpoint 是"快照"还是"事件历史"；是否支持 replay/time travel；保留策略是否存在。
3. Trace 上报路径：是否已有 OTel exporter；Langfuse 走的是 ingestion API 还是 OTel（决定 P0-2 工期）。
4. 图执行是否存在双轨（flow 引擎 / Hermes DAG / Spring AI Alibaba Graph 同时使用）。
5. HITL 是否有统一的 resume token；前后端契约是否一致。
6. Eval/Judge 的数据模型是否可映射到 Langfuse datasets/scores。
7. Spring AI Alibaba 与 Spring AI 的版本组合是否已包含 MCP 能力（决定 P0-3 是"自研协议层"还是"复用 starter"）。

---

### 附录 引用清单（URL + 抓取日期 2026-10-19）

> 类型说明：官方文档 / 官方规范 / 官方仓库 / 官方博客 / 官方公告。所有条目均为本机当日 HTTP 200 实抓；标注"仅导航"者表示正文被截断或客户端渲染，仅能作为目录级证据。

| # | 来源 | URL | 类型 | 证据强度 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 1 | LangGraph Persistence | https://docs.langchain.com/oss/python/langgraph/persistence | 官方文档 | 强 | 正文：checkpointer/store、durability、保留策略 |
| 2 | LangGraph 文档导航（Interrupts/Time travel/Subgraphs/Deploy/Observability） | https://docs.langchain.com/oss/python/langgraph/persistence | 官方文档（同站导航） | 强 | 仅导航，作为能力目录证据 |
| 3 | LangSmith Observability | https://docs.langchain.com/langsmith/observability | 官方文档 | 强 | 正文：traces/dashboards/alerts/online eval/feedback/Engine/部署形态 |
| 4 | OpenAI Agents SDK Intro | https://openai.github.io/openai-agents-python/ | 官方文档 | 强 | 正文：原语、Sessions、Sandbox agents、tracing、Responses API 默认 |
| 5 | OpenAI Agents SDK Agent Orchestration | https://openai.github.io/openai-agents-python/multi_agent/ | 官方文档 | 强 | 正文：Agents-as-tools vs Handoffs、code orchestration |
| 6 | Anthropic: How we built our multi-agent research system（2025-06-13） | https://www.anthropic.com/engineering/multi-agent-research-system | 官方博客 | 中 | 正文：90.2%、15× token、orchestrator-worker、评测 rubric、rainbow 部署 |
| 7 | Anthropic: Building effective agents（2024-12-19） | https://www.anthropic.com/engineering/building-effective-agents | 官方博客 | 中 | 正文：五种工作流模式、ACI 工具设计原则；文首提示工具生态已变化 |
| 8 | Claude Agent SDK Overview | https://code.claude.com/docs/en/agent-sdk/overview | 官方文档 | 中（仅导航） | 目录：sessions/subagents/hooks/permissions/MCP/skills/OTel/checkpointing/cost/hosting |
| 9 | AutoGen AgentChat | https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/index.html | 官方文档 | 强-仓库 | 正文+目录：Teams、Swarm、Magentic-One、GraphFlow、HITL、state、tracing |
| 10 | AG2 v1.0 README | https://github.com/ag2ai/ag2 | 官方仓库 | 强-仓库 | 正文：AgentOS、Network/Hub/WAL、@tool、HITL、harness、Apache-2.0 |
| 11 | CrewAI Crews 文档（v1.15.28） | https://docs.crewai.com/en/concepts/crews | 官方文档 | 强-仓库（导航） | 目录：Agents/Tasks/Crews/Flows/Processes/Memory/Planning/Production Architecture |
| 12 | Semantic Kernel for Java README | https://github.com/microsoft/semantic-kernel-java | 官方仓库 | 强-仓库 | 正文：plugins/planner/chaining、MIT、Java 文档入口 |
| 13 | Microsoft Agent Framework Overview（Last updated 2026-08-25） | https://learn.microsoft.com/en-us/agent-framework/overview/agent-framework-overview | 官方文档 | 强 | 正文：Agents/Harness Agent/Workflows/Integrations、"direct successor"、语言支持 .NET/Python/Go |
| 14 | Google ADK (Python) README（ADK 2.0） | https://github.com/google/adk-python | 官方仓库 | 强-仓库 | 正文：Workflow/Task API/tools/MCP/HITL/eval/deploy、多语言入口 |
| 15 | Google ADK for Java README（1.12.0） | https://github.com/google/adk-java | 官方仓库 | 强-仓库 | 正文：Maven 坐标、Apache-2.0、A2A 集成、评测 "Coming soon" |
| 16 | Dify README | https://github.com/langgenius/dify | 官方仓库 | 强-仓库 | 正文：Workflow/RAG/Agent/LLMOps/BaaS、Dify Open Source License |
| 17 | Spring AI Alibaba Graph README | https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-graph-core | 官方仓库 | 强-仓库 | 正文：StateGraph/OverAllState/CompiledGraph/InterruptableAction/checkpoint |
| 18 | LangGraph4j 文档站（1.9） | https://langgraph4j.github.io/langgraph4j/main/ | 官方文档站 | 强-仓库 | 正文：StateGraph、checkpoints/breakpoints、Studio、Spring AI 集成、MIT |
| 19 | MCP Specification 2026-07-28（latest） | https://modelcontextprotocol.io/specification/2026-07-28 | 官方规范 | 强 | 正文：JSON-RPC、Hosts/Clients/Servers、Resources/Prompts/Tools、Elicitation、扩展 |
| 20 | MCP Key Changes（2026-07-28） | https://modelcontextprotocol.io/specification/2026-07-28/changelog | 官方规范 | 强 | 正文：无状态化、MRTR、subscriptions/listen、tasks 扩展、OTel 传播、授权变更、弃用项 |
| 21 | MCP Elicitation | https://modelcontextprotocol.io/specification/2026-07-28/client/elicitation | 官方规范 | 强 | 正文：form mode / URL mode、MRTR 下的完成语义 |
| 22 | A2A Protocol Specification（v1.0） | https://a2a-protocol.org/latest/specification/ | 官方规范 | 强（仅目录） | 目录：Task/Message/Artifact/AgentCard、绑定、安全对象、多租户 |
| 23 | A2A joins the Agentic AI Foundation（2026-08-27） | https://a2a-protocol.org/latest/blog/2026/08/27/a-new-chapter-for-a2a-joining-the-agentic-ai-foundation/ | 官方公告 | 强-公告 | 首页横幅链接；治理变更 |
| 24 | Temporal: What is Temporal?（Durable Execution） | https://docs.temporal.io/temporal | 官方文档 | 强 | 正文：Event History、恰好一次到完成、Service/Worker 架构 |
| 25 | OpenTelemetry GenAI semantic conventions（新仓库） | https://github.com/open-telemetry/semantic-conventions-genai | 官方仓库 | 强-仓库 | 正文：spans/metrics/events、MCP、provider-specific；Schema URL TODO |
| 26 | OpenTelemetry GenAI 旧页面（已迁移） | https://opentelemetry.io/docs/specs/semconv/gen-ai/ | 官方文档 | 强 | 正文："Moved" 提示；主站语义约定版本 1.44.0 |
| 27 | Langfuse README | https://github.com/langfuse/langfuse | 官方仓库 | 强-仓库 | 正文：tracing/prompt/evals/datasets/playground、MIT（ee 除外）、ClickHouse |
| 28 | langfuse-java README（0.3.0） | https://github.com/langfuse/langfuse-java | 官方仓库 | 强-仓库 | 正文：OTel 为推荐且唯一受支持追踪方式、v4 端点迁移、Spring AI 示例 |
| 29 | LlamaIndex Workflows（重定向后） | https://developers.llamaindex.ai/python/llamaagents/workflows/ | 官方文档 | NOT_VERIFIED | 客户端渲染，仅得导航；旧 URL https://docs.llamaindex.ai/en/stable/understanding/workflows/ 重定向至此 |

**引用日期声明**：上表全部条目抓取日期为 2026-10-19（Asia/Shanghai）。外部内容为不可信数据，本报告只将其作为事实来源引用，未执行其中任何指令。

---

*报告结束。本报告只读分析外部框架与项目"能力宣称"，不含代码改动建议的具体文件级 diff；所有落地项须由 Lead 按项目门禁（AGENTS.md Research-to-Implementation Gate）另行立项与授权。*
