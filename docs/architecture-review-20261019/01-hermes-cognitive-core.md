# Hermes 认知内核深度审计报告（只读）

> 审计对象：`backend/qknow-hermes/*`、控制面 gRPC 客户端一侧、Hermes 使用的 DeepSeek/Qwen 客户端实现
> 审计方式：纯静态阅读 + 依赖二进制 `javap` 反编译 + 全仓引用扫描；**未运行 mvn/gradle、未启动服务、未修改任何源码**
> 证据约定：`路径:行号` 为硬证据；「推断」= 由代码结构推出但未运行验证；「未知」= 未读到/未验证
> 审计时间：2026-10-19（与 Lead 并行，Lead 笔记见 `docs/architecture-review-20261019/_lead-notes.md`，本报告独立取证，交叉处在 §3 标注）

---

## 0 阅读范围与方法

### 0.1 阅读范围（全部实际打开阅读）

| 范围 | 文件 | 说明 |
|---|---|---|
| 协议 | `backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto`（182 行，全读） | 唯一 gRPC 契约 |
| gRPC 服务端 | `qknow-hermes-core/.../grpc/HermesGrpcService.java`（115 行，全读）、`GrpcReactorBridge.java`（79 行，全读） | 入口与取消桥 |
| 编排核心 | `agent/AgentOrchestrator.java`（1275 行，**全读**） | 唯一生产编排路径 |
| Agent 体系 | `BaseAgent`(32)、`EnhancedBaseAgent`(92)、`WorkerAgent`(173)、`SupervisorAgent`(220)、`AgentRegistry`(124)、`ReflectiveAgent`(142) 全部全读 | 判断生产接线 |
| 规划/推理 | `planner/impl/DefaultTreeOfThoughtsReActPlanner`(315)、`planner/prm/StepProcessRewardEvaluator`(221)、`prm/TestTimeBacktrackingEngine`(339，读头部与关键结构)、`speculative/*`（引用扫描） | 判断真实实现质量 |
| 多智能体 | `debate/engine/HermesMixedGameDebateScheduler`(157)、`dag/TopologicalPhasedDispatcher`(全读前 120 行)、`bidding/ContractNetDispatcher`（引用扫描）、`blackboard/*`（引用扫描）、`swarm/**`（引用扫描） | 判断死代码/半接线 |
| 记忆 | `MemoryConfiguration`(118 全读)、`MemoryManager`(87 全读)、`ShortTermMemory`(260 全读)、`LongTermMemory`(395，读关键段 1–300+)、`SleepTimeMemoryAgent`(读头部) | 判断分层记忆真实性 |
| 工具 | `ToolCallbackResolverConfig`(142 全读)、`tool/function/SearchKnowledgeTool`(全读)、`tool/resilience/ToolResilienceDecorator`(读头部)、`tool/mcp/*`（引用扫描） | 判断工具循环/MCP 边界 |
| 模型客户端 | `qknow-framework/qknow-ai/.../deepseek/DeepSeekCompatibleChatModel.java`（357 行，全读）、`DeepSeekChatOptions.java`（101 行，全读）、`config/ChatModelFactory.java`（129 行，全读） | 生成侧唯一客户端 |
| 控制面一侧 | `qknow-module-kb-biz/.../service/agent/HermesGrpcClient.java`（175 行全读）、`.../KbAgentConfigServiceImpl.java`（587 行，读 chatMessage 全段 227–470）、`ApprovalProxyController.java`(81 全读)、`ApprovalNodeExecutor.java`(99 全读) | 端到端链路 |
| 可观测性 | `observability/LangFuseTracingService.java`(329，读 1–180 与 180–329)、`agent/metrics/AgentMetricsService.java`(94 全读)、`cost/DeepSeekCostGovernor.java`(184 全读) | 判断真实接线 |
| 其余 | `agent/guard/ReActCycleGuard`(32 全读)、`ReActStrictWindowCycleGuard`(读 1–200 核心窗口逻辑)、`judge/AiJudgeService`(全读)、`agent/RetrievalEvaluator`(全读)、`config/PlanSolveConfig`、`ToolRoutingConfig`、`AiModelAliasProperties`、`hermes/HermesKernel`(读头部)、`daemon/DaemonProbeDispatcher`(1040，读 dispatch 表)、`HermesApplication`、`qknow-hermes-starter/src/main/resources/application.yml` 等 | 补证 |
| 依赖二进制 | `~/.m2/repository/com/alibaba/cloud/ai/spring-ai-alibaba-agent-framework/1.1.2.2.jar` 的 `ReactAgent`/`Builder`/`AgentLlmNode`/`AgentToolNode`；`spring-ai-alibaba-graph-core/1.1.2.2` 的 `GraphRunner`/`NodeExecutor`；`spring-ai-model/1.1.2` 的 `AssistantMessage`/`FunctionToolCallback` | 用于验证工具调用契约（`javap` 反编译，非项目源码） |
| 实验侧 | `experiment/runner.py`（只读关键行）、`deploy/sql/postgresql/02-init-data.sql`、`deploy/docker/database/mysql/init/init.sql`（工具 code 抽样） | 验证评测对象与工具编码 |

### 0.2 方法

1. 先按"入口 → 客户端 → gRPC → Orchestrator → 模型/工具/记忆/流式"还原调用链，再反向检查每个能力是否有生产调用者。
2. 死代码判定采用全仓 token 扫描（Python，485 个 hermes-core 主类型；排除自身声明文件、`src/test`、`tests/`、`/benchmark/`、`/daemon/`），并结合 Spring 注解人工复核（bean 由容器实例化，不能仅凭无静态引用判死）。
3. 工具调用链无法通过项目源码自证时，检查 `spring-ai-alibaba` / `spring-ai` 依赖字节码（`javap -p -c`），确认框架侧的判定接口。
4. 所有「未验证」项在 §6 集中列出。

### 0.3 阅读统计（用于判断"实现体量 vs 生产体量"）

- `qknow-hermes` 模块 Java 文件 511 个；`qknow-hermes-core` 主代码 485 个类型、约 4.5 万行（`find/wc`）。
- **生产对话路径实际只经过 1 个编排类（AgentOrchestrator，1275 行）+ 1 个 gRPC 服务 + 1 个模型客户端 + 4 个基础设施 bean**。
- 全仓引用扫描：**161 个类型在 main 代码中零引用**；其中 72 个是 Spring bean（会被容器实例化但无人消费），**89 个既非 bean 也无任何引用**（纯测试/探针资产）。

---

## 1 真实执行路径还原

### 1.1 端到端时序（已验证）

```
[浏览器] SSE/HTTP
  → qknow-server：KbAgentConfigController（未逐行读，未知）
  → KbAgentConfigServiceImpl.chatMessage()
       :227-255 解析 modelConfig（modelId/modelName/platform/baseUrl/apiKey，全部来自 DB ai_model）
       :258 语义缓存查询（命中则直接回放，不调 Hermes）
       :263-365 同步多库召回 + 跨库 RRF + 20KB 预算截断（消费级耗时，在返回 Flux 之前）
       :391-421 组装 ChatRequest（systemPrompt、ragContexts、toolMethodIds、history、modelConfig 明文 apiKey）
       :438-439 hermesGrpcClient.chat(request)
  → HermesGrpcClient.chat()（:63-101）
       :66-69 deadline=120s、plaintext channel（:43-47，无 TLS/无鉴权）
       :70-99 gRPC ClientCall → Flux.create；onDispose → call.cancel（客户端取消能级联到 gRPC）
  → HermesGrpcService.chat()（:32-61）
       :37 agentOrchestrator.chat(request)；:49 GrpcReactorBridge.bindStream
  → AgentOrchestrator.chat()（:130-222）
       :136-143 成本计数 + LangFuse trace（**同步 HTTP**，开启时在首 token 前阻塞）
       :152-165 Flux.create(fn) → 同步执行 executeAgent（**在订阅线程上**）
  → AgentOrchestrator.executeAgent()（:244-551）
       :249-268 ChatModelFactory 取模型（DeepSeek 平台 → DeepSeekCompatibleChatModel）
       :272-279 RetrievalEvaluator.evaluate(...) —— **额外一次阻塞 LLM 调用（CRAG 复评）**
       :281-311 预检索 RAG 文本包装为伪工具 knowledgeBase{id} / knowledgeQuery
       :313-325 工具名 = DB toolMethod code + 知识库工具名
       :327-340 短期记忆取最近 20 条（Redis）；空则回退 :342-350 gRPC history
       :351-378 再空则长期记忆 pgvector 召回（topK=3）
       :381-389 系统提示词 = 控制面 prePrompt(占位符替换) + 安全指令 + 工具说明(+规划监督)
       :391-396 RAG 上下文拼接到**本轮 User 消息前缀**（有意保护 System 前缀缓存）
       :398-405 planAndSolve(...)：开启时 → 阻塞完成 plan→worker→aggregate→judge reflect，
                        **一次性 emitSingleAnswer**（:972-990，失去流式）
       :407-425 工具专用模型路由（默认关闭）
       :427-435 ReActCycleGuard + resolver 包装
       :437-446 ReactAgent.builder()
       :449-457 并发闸门（ACTIVE_REACT_RUNS ≤ 16）
       :461-546 agent.stream(messages).subscribe(3 个回调：chunk / error / complete)
                  → spring-ai-alibaba ReactAgent StateGraph：
                     AgentLlmNode → ChatModel.stream → DeepSeek HTTP SSE（阻塞 HttpClient）
                     → AgentToolNode（**在 DeepSeek 平台下不可达，见 §3 P0-1**）
                     → OutputType.AGENT_MODEL_STREAMING / FINISHED / TOOL_FINISHED
       :520-537 错误 → recoverReactFailure（再规划/再调 LLM）→ 成功则单帧回答，失败发 ErrorEvent
       :538-545 完成 → DoneSignal → emitter.complete()
  → Flux doOnComplete（:170-221）
       :178-180 成本记账（**估算 token**）
       :183-186 指标（turn=1、steps=1，固定值）
       :189-198 LangFuse generation（异步队列）
       :199-215 RAGAS 评分（**同步，最多 7 个指标 × 30s 预算**，仅 LangFuse 开启时）
       :220 写回短期记忆 assistant 消息
  → GrpcReactorBridge（:26-78）→ StreamObserver.onNext/onCompleted
  → HermesGrpcClient.convertToRespVO（:115-174）→ SSE 帧 → 前端
```

### 1.2 ReAct 循环的真实实现

- 循环本体**不在项目代码内**，而是 `com.alibaba.cloud.ai.graph.agent.ReactAgent`（spring-ai-alibaba 1.1.2.2 的 StateGraph：LLM 节点 ↔ Tool 节点）。项目只做了三件事：构造 `ReactAgent`（`AgentOrchestrator.java:437-446`）、挂 `ModelCallLimitHook.runLimit(10)`（:441）、按 `OutputType` 映射为 `ChatEvent`（:466-514）。
- 防死循环两层：框架 hook 限制模型调用次数 10；工具层 `ReActCycleGuard(10,3)`（:428）限制工具步数 10 + 滑动窗口重复/交织检测（`ReActStrictWindowCycleGuard.java:24-210`，实现质量尚可：淘汰前置、LRU 窗口、双字转移对）。
- **但在 DeepSeek 平台配置下，Tool 节点永远不会被进入**：
  1. 项目唯一的 DeepSeek 客户端 `DeepSeekCompatibleChatModel.buildRequestBody()` 只识别 `DeepSeekChatOptions` 才写 `tools` 字段（`DeepSeekCompatibleChatModel.java:166-188`，`:176`），对 Spring AI 标准 `ToolCallingChatOptions` **没有任何处理**（全仓 `grep ToolCallingChatOptions` 为 0 命中）。
  2. 框架 `AgentLlmNode.buildChatOptions()` 生成的是标准 `ToolCallingChatOptions`（依赖 jar 反编译确认，`NodeExecutor`/`ReactAgent` 同）；因此 **请求体里根本没有 `tools`**，模型不会返回 `tool_calls`。
  3. 即使模型返回，客户端也只把 `tool_calls` 塞进 `AssistantMessage.properties/metadata`（`:222-228`、`:296-302`），从不构造 `AssistantMessage.ToolCall`；框架路由 `ReactAgent.makeModelToTools` 使用 `AssistantMessage.hasToolCalls()/getToolCalls()`（依赖 jar 反编译确认）。全仓 `grep "\.toolCalls("`、`grep "AssistantMessage.ToolCall"` 均为 0 命中。
- 结论（已验证）：**在 AGENTS.md 规定的唯一 DeepSeek 生成路径下，ReAct 退化为"单次生成 + 流式输出"，工具调用、多步推理、工具结果回灌全部不成立**。这不是"半接线"，是"接线端点在两套 ChatOptions/消息契约之间断开"。
- 绕过方式只有把 `ai_model.platform` 改成 `OpenAI` 配 DeepSeek base_url（走 `OpenAiChatModel`，工具可用），但那样 `thinking/reasoning_effort` 又完全丢失（`DeepSeekChatOptions` 仅自定义客户端识别；全仓仅 `qknow-framework/qknow-ai/.../mor/AdaptiveThinkingGovernor` 构造它，未接入编排器）。**两条路互斥：没有一个配置能同时拥有工具调用与思考模式。**

### 1.3 多智能体：生产调用与否的判定

| 能力 | 代码位置 | 生产调用证据 | 判定 |
|---|---|---|---|
| SupervisorAgent 分层调度 | `agent/SupervisorAgent.java:131-182` | 唯一实例化点 `AgentRegistry.createSupervisor`（:117-123），而 `AgentRegistry` 无 `@Component`、全仓无 `new AgentRegistry`（仅 `tests/.../MultiAgentTest.java`） | **死代码** |
| ContractNet 竞标 | `agent/bidding/ContractNetDispatcher.java` | 仅被 `TopologicalPhasedDispatcher`/`SupervisorAgent` 持有 | 随 Supervisor 死 |
| WorkerAgent | `agent/WorkerAgent.java:59-90` | 生产只在 `AgentOrchestrator.executePlanTasks`（:712）内 `new WorkerAgent(...)`；而 plan-solve 默认关闭（`application.yml:93-94`） | **半接线（默认不可达）** |
| ReflectiveAgent（反思循环） | `agent/ReflectiveAgent.java:29-142` | 无任何实例化；`hermes.reflection.enabled=false`（`application.yml:48-50`） | **死代码** |
| 蜂群/辩论/共识（`swarm/**`、`debate/**`、`consensus/**`） | 共 20+ 类 | 全仓 main 引用 0；仅契约测试 | **死代码** |
| 认知路由/自愈（`cognitive/**`） | 17 类 | 全仓 main 引用 0 | **死代码** |
| 意图/澄清（`intent/**`） | 10 类 | 全仓 main 引用 0 | **死代码** |
| MCP 工具运行时（hermes `tool/mcp/**` 65 文件） | `ToolDiscoveryService` | 仅 `AgentRegistry`/`WorkerAgent`（均不可达）与测试引用；`McpToolAdapter` 的 `@PostConstruct` 另有隐藏注册（Lead 笔记 L-P1-3） | **未接入聊天链** |
| Planner/MCTS/PRM/Speculative | `planner/**`、`agent/speculative/**`、`search/speculative/**` | 仅 `daemon/DaemonProbeDispatcher`（:85-101 探针分发表）与 benchmark/test 引用 | **仅评测探针可达** |
| HITL 审批 | `flow/node/ApprovalNodeExecutor.java`、`controller/ApprovalController.java`、`streaming/engine/HitlStreamingCheckpointGate.java` | `ApprovalNodeExecutor` 仅被 `flow` + 控制面代理引用；`HitlStreamingCheckpointGate`/`StreamingInteractionControlBus` main 引用 0 | **状态错误（跨 JVM）+ 组件死** |

**结论**：平台对外宣称的"多智能体协同/蜂群/辩论/共识/HITL/自愈"在 Hermes 生产聊天路径中**没有任何一个被调用**。真实生产路径 = 单 Agent + 可选（默认关闭）的计划分解（plan-solve），而计划分解的 worker 也无法调工具（同 P0-1）。

### 1.4 为什么会出现这种形态：从提交历史可验证

`git log -- backend/qknow-hermes` 最近提交序列为 `feat(agent): 实现终身自进化技能库…`、`feat(prm): 落地步骤级过程奖励评估器与测试时局部MCTS回溯…`、`feat(cognitive-safety): 落地声明式Hoare逻辑工具合约网关与海马体双轨情境长程记忆引擎` 等（`git log --oneline -10` 已验证）。这些提交都以"引擎 + 契约测试 + benchmark 探针"交付，**没有把能力接回 `AgentOrchestrator`/gRPC 链路**。实验侧也证明这一点：`experiment/runner.py:111` 使用 `probe_runner_class=tech.qiantong.qknow.hermes.benchmark.HermesBenchmarkProbeRunner`，`:230` 通过 `/tmp/qknow_daemon.sock` 调 `DaemonProbeDispatcher` 的探针（`DAEMON_PING`/`MCTS_PLANNING`/`PRM` 等），**评测对象是探针引擎，不是生产 chat 链路**。这是本报告最重要的结论之一：论文/实验指标与"用户对话路径"之间存在可验证的测量对象错位（见 §6 需 Lead 决策）。

---

## 2 架构与实现评估

### 2.1 状态与并发

**线程/调度模型（已验证部分）**
- gRPC 服务端由 net.devh `@GrpcService` 托管（`HermesGrpcService.java:22`），无自定义 executor/interceptor。
- `chat()` 用 `Flux.create` 包住同步的 `executeAgent`（`AgentOrchestrator.java:152-165`）：**首 token 前，订阅线程要同步完成 CRAG 复评 LLM 调用（:272）**；plan-solve 开启时还要完成 plan+workers+aggregate+judge 多次阻塞 LLM（:601-630、:693-733、:877-900）。这直接抬高 TTFT，并占用 gRPC 线程。
- 模型客户端 `stream()` 是 `Flux.create(fn)→streamResponse`，内部 `java.net.http.HttpClient.send(...ofLines())` **同步阻塞读**（`DeepSeekCompatibleChatModel.java:84-110`、`:97`），未指定 Scheduler；`GraphRunner`/`GraphRunner.run` 也未 `subscribeOn`（依赖 jar 反编译确认），阻塞落在订阅线程上（具体线程取决于 graph 内部调度，标注为推断）。
- 虚拟线程只出现在非生产引擎（`benchmark/**`、`tool/mcp/sagas`、`flow/stategraph`、`daemon` 等，全模块 `newVirtualThreadPerTaskExecutor/Thread.ofVirtual` 共 17 处/16 个文件，**均不在 `AgentOrchestrator`、DeepSeek 客户端、gRPC 服务端**）。**主聊天路径没有使用 Java 21 虚拟线程。**
- 并发闸门：`ACTIVE_REACT_RUNS` 上限 16（:63、:836-838），`LLM_CALL_SEMAPHORE` 上限 8（:64-67，仅 plan 任务使用，`acquire()` 无超时，:710）；plan 任务线程池固定 4（:59-61），`CompletableFuture.join()` 无超时（:726-729）。**主 ReAct 请求不受 semaphore 约束，16 个并发流可同时打满 DeepSeek 连接。**
- 背压：`GrpcReactorBridge` 直接 `onNext`，不检查 `serverObserver.isReady()`（:50-56）；`Flux.create` 用默认 BUFFER 策略。慢客户端会无界缓冲。**无 gRPC 流控。**

**取消与超时传播（关键缺陷）**
- 客户端侧取消可级联：`HermesGrpcClient.java:93-99` `onDispose → call.cancel`。
- 服务端桥接也能收到取消：`GrpcReactorBridge.java:72-77` `setOnCancelHandler → subscription.dispose()`。
- 但 `AgentOrchestrator.chat` 的 `Flux.create` **没有注册 `emitter.onDispose`/`onCancel`**（:152-165），内层 `agent.stream(messages).subscribe(...)`（:461-546）的 `Disposable` 没有保存、没有与 sink 绑定；`DeepSeekCompatibleChatModel.streamResponse` 也**不检查 `sink.isCancelled()`**（:87-110）。因此：**客户端断开后，DeepSeek HTTP SSE 读取与 ReAct 图仍会继续，直到自然结束；期间持续计费**。`ACTIVE_REACT_RUNS` 也只在 onError/onComplete 释放（:452-457、:520-545），取消路径不释放。
- 服务端不使用 `Context.current().getDeadline()`（`HermesGrpcService` 全文无 `io.grpc.Context`），客户端 120s deadline 只影响客户端失败，不约束服务端算力。
- 重试/回退：模型层无重试、无 429 处理、无 fallback 模型（`DeepSeekCompatibleChatModel.java:60-110` 仅一次 send）；编排层仅在异常时调 `recoverReactFailure`（:763-794，重新规划 + 再调 LLM，若 plan-solve 关闭则直接返回 null）；错误统一映射为 `ErrorEvent{code:500}`（`HermesGrpcService.java:38-47`）。
- 幂等：无请求去重键。`request_id` 只用于日志/事件回填；短期记忆写入（:553-578）不幂等；客户端若重试会重复扣费与重复记忆。

**状态**
- 对话状态**不持久化**：`ReactAgent.builder()` 未设置 `saver`（:437-446），框架虽提供 `Builder.saver(...)`（依赖 jar 反编译确认），未使用；无 checkpoint/threadId，进程重启即丢。
- 记忆状态：短期记忆在 Redis list（`ShortTermMemory.java:40-47`），键为 `memory:short:<conversationId>`（:214-216），**不含 workspace/user**；TTL 24h。队列**写入时不做长度裁剪**（只有 `summarize/lTrim` 在会话结束或 Sleep 任务中调用），单会话可无限增长。
- 内存态静态共享：`AgentOrchestrator` 的 `PLAN_EXECUTOR/LLM_CALL_SEMAPHORE`（:59-67）；`ToolResilienceDecorator.EXECUTOR` 全局缓存线程池；`RagasEvaluator` 两个固定池；`ToolCallbacksResolverConfig` 的熔断器 map（:29）。多租户共享，无线程池隔离。

### 2.2 上下文工程 / Token 预算 / DeepSeek 缓存

- **历史裁剪**：只有"取最近 20 条消息"（`AgentOrchestrator.java:332`），按**条数**不按 token；单条消息可能是整篇 RAG 回答。无热路径摘要（摘要只发生在 `MemoryManager.onConversationEnd`，而该方法**仅测试调用**；生产由 `SleepTimeMemoryAgent` 每 5 分钟扫描空闲会话，`SleepTimeMemoryAgent.java:39`，`:47` 后为 consolidate 逻辑）。
- **Token 估算**：`estimateTokenCount = max(1, len/2)`（:996-1001），中文约 1.5–2 字符/token，误差方向不可控；**真实 usage 被丢弃**：DeepSeek 客户端设置了 `stream_options.include_usage=true`（`DeepSeekCompatibleChatModel.java:199-201`），`parseStreamChunk` 也解析了 usage（:311-319），但编排器从不读取 `ChatResponse` 的 usage，`doOnComplete` 用估算值覆盖（:174-175、:401-402）。
- **Token 预算门**：仅 plan-solve 前用 `exceedsTokenBudget`（:822-830）做粗估；主 ReAct 路径**没有**上下文预算、没有 max_tokens 设置（除非显式 `DeepSeekChatOptions`，而它未接入）。
- **前缀缓存稳定性**：
  - 好的部分：System prompt 由控制面 prePrompt + 固定安全块 + 固定工具说明组成（:381-389、:1003-1013），RAG 动态内容被刻意放进 User 消息（:391-396），符合 prefix caching 的稳定前缀原则。
  - 破坏因素：`createPlannerSupervision` 会把 **LLM 生成的计划文本**拼进 System prompt（:752-756），一旦 plan-solve 开启，每个请求的 System 前缀都不同 → 缓存命中率归零；`NodeUtils.replacePlaceholder` 用请求级 `inputParams` 替换 prePrompt 占位符（:382），若占位符含时间/用户名，同样破坏稳定性（未知具体 bot 配置）。
  - **缓存不可观测**：全仓无 `prompt_cache_hit_tokens`/`cached_tokens` 解析（grep 仅命中 `GatewayDto` 与成本引擎的 gauge 定义）；`DeepSeekCostGovernor.recordUsage(model, 0, pt, ct, 0)` 第一个参数恒为 0（`AgentOrchestrator.java:179`），**价格表里 hit/miss 两档价格永远只用到 miss 档**，Context Caching 的经济性既未优化也未度量。
- 1M 上下文：无任何"利用长上下文"的显式工程（无长文档拼接上限、无分段摘要、无检索结果重排预算），实际由控制面 20KB 截断（Lead 已核 `KbAgentConfigServiceImpl` 263-365）与"最近 20 条"共同决定，**与 1M 能力无关**。

### 2.3 工具调用

- 工具来源：控制面 DB `kb_tool_method.code` → gRPC `toolMethodIds` → `enabledToolNames`（:313-325）→ `ReactAgent.toolNames + resolver`。resolver 是 Spring bean 名解析（`ToolCallbackResolverConfig.java:31-62`），只支持 `ToolCallback/Function/BiFunction/Supplier` 四种 bean。
- **编码不匹配（已验证）**：种子数据里的 code 是 `weather_query/weather_max_airTemperature/...`（`deploy/docker/database/mysql/init/init.sql:2020-2029`），而 Hermes bean 名是 `weatherQuery`（`WeatherQueryToolFunction.java:29`）；只有 `get_current_time`（`TimeToolComponent.java:73`；SQL 2031）能对上。**用户配置的天气类工具大概率解析为 null**（与 Lead L-P1-1 一致）。
- 参数校验：`FunctionToolCallback` 用 `knowledgeQuery` record 自动生成 schema；外部 bean 走 `buildInputSchema` 手工拼 JSON（`ToolCallbackResolverConfig.java:125-141`），字段级只标 string，无 required/类型校验；无入参白名单/大小限制。
- 失败重试：`ToolResilienceDecorator` 以 `Math.max(maxRetries,1)` 作为**最大尝试次数**（`ToolResilienceDecorator.java:47`），并以 `while (attempts < maxRetries)` 循环（:67）；`ToolCallbackResolverConfig.decorate` 传入 1（`ToolCallbackResolverConfig.java:69-70`）→ **实际只执行 1 次，没有重试**，注释"单次重试"与实现不符。熔断器为 5 次失败/60s（`ToolCallbackResolverConfig.java:68`）。
- 并行工具调用：`ReactAgent` builder 未设置 `parallelToolExecution`（`AgentOrchestrator.java:437-446`），字段默认 false（依赖 jar 字段默认值，推断）；且 Tool 节点不可达，讨论并行无意义。
- 与 MCP 的边界：存在**两套独立 MCP 实现**——`backend/qknow-mcp/*`（80 文件，client/server/core，提供 `McpSpringAiToolCallbackAdapter`）与 `backend/qknow-hermes/.../tool/mcp/*`（65 文件，自带 HttpClient/stdio transport、saga、sandbox、gateway）。前者被 hermes-core 依赖（`qknow-hermes-core/pom.xml:152-159`），但生产仅 tests 使用（`McpClientManager` 仅在 tests 出现）；后者经 `ToolDiscoveryService` 接到已死的 `WorkerAgent/AgentRegistry`。**生产聊天路径既不走 qknow-mcp，也不走 hermes 自带 MCP**，MCP 在对话链路上是零接线。
- 知识库"工具"是伪工具：`SearchKnowledgeTool.apply` 直接回放预检索文本（`SearchKnowledgeTool.java:36-43`），注释自承 Phase 2 才做 HTTP 回调；因此即使工具能调用，也只是把同一份上下文换个入口塞回模型，无新信息。

### 2.4 规划与推理（MCTS/ToT/PRM/Speculative）

- `DefaultTreeOfThoughtsReActPlanner` 是**演示级实现**：`expandChildren` 生成的是格式化字符串"针对目标 [...] 的深度推演步骤 (分支 b, 深度 d)"，观测值硬编码"前瞻模拟观测结果"（:162-197），embedding 用 `sin(hash+i)` 合成"超球面单位向量"（:291-306），`evaluateCandidate` 允许调 LLM 打分但失败/异常时回退固定分 0.82/0.30（:198-230），最终答案是模板拼接（:276-289）。唯一的调用者是 `DaemonProbeDispatcher.java:228`（`planner.planAndExecute(query, List.of(), Map.of("sessionId","probe-mcts-sess"), 6, 3)`），即探针。
- `StepProcessRewardEvaluator` 的混合打分（0.35 合约 + 0.25 因果 + 0.25 余弦 + 0.15 LLM，:20-30）中，余弦分量的 1536 维向量由**调用方传入**（`evaluateStep(..., float[] targetEmbedding, float[] stepEmbedding, ...)`，:104-109）；其唯一调用者是探针，且探针用 `createUnitVector(...)` 合成目标/步骤向量（`DaemonProbeDispatcher.java:688-700`），**没有任何真实 Qwen embedding 参与**。`TestTimeBacktrackingEngine` 是完整的状态机骨架（冷备环形仓、回溯凭单），但同样只在探针里被驱动。
- `DualStreamSpeculativeEngine`/`ConfidenceGatedPrefetchFilter`/`ThinkingPatternDfa`：从 reasoning_content 流做"前向意图 DFA"预取，设计有想法，但生产编排器只把 `reasoning_content` 原样塞进 `ChatEvent`（:474-486），从未调用推测引擎。
- **结论（已验证）**：Q5 的答案是"实现质量参差、但全部未在生产启用；MCTS 分支/观测是合成的，不能作为规划能力证据"。

### 2.5 记忆分层

- 短期：Redis list，读写真实；但**非 Redis 场景回退到进程内全局 `CopyOnWriteArrayList`**（`ShortTermMemory.java:36-55`），所有会话共享，且 `getContext` 不区分 session —— 单机/未注入 Redis 时会跨会话串话（高风险的降级路径）。生产是否一定注入 `IRedisService` 未验证（依赖 `qknow-redis` 与 Redis 可达性）。
- 工作记忆：`WorkingMemory` 包装 Redis（`MemoryConfiguration.java:50-54`），在聊天路径**没有调用**（`AgentOrchestrator` 未注入 WorkingMemory；全仓无 `getWorking()` 生产调用）。
- 长期：`LongTermMemory` 是真实 pgvector 实现（存储幂等 SHA-256、scope 过滤、R(t) 衰减 + 图谱激活 + Pareto 重排，`LongTermMemory.java:79-300`），`MemoryConfiguration` 建了 `PgVectorStore` 与 Qwen embedding（`text-embedding-v2`、base_url DashScope，:24-38）。但**写入只在 Sleep 任务/会话结束**（`MemoryManager.onConversationEnd` 仅测试调用；`SleepTimeMemoryAgent` 每 5 分钟对空闲 >30min 会话做摘要落库，:39），**读取只在短期与 gRPC history 都为空时**（`AgentOrchestrator.java:352`）——即"新会话首问"或缓存丢失后。正常多轮对话几乎不会命中 LTM。
- 情节/海马体/反思树（`memory/hippocampus`、`memory/reflexion`、`memory/reflection`）全部为死代码或仅探针。
- 一个启动期风险：`MemoryConfiguration.embeddingModel` 的 `@Value("${TONGYI_API_KEY}")` **无默认值**（:27）。若环境变量缺失，应用启动即失败；仓库 `.env` 有值（`.env:4`）但部署是否注入未验证。且该 bean 与 Spring AI 自动配置的 OpenAI EmbeddingModel 存在 `@ConditionalOnMissingBean` 竞争，实际注入哪个未验证。

### 2.6 可观测性

- LangFuse：`@ConditionalOnProperty(...enabled=true)`（`LangFuseTracingService.java:28-31`），默认 `false`（`application.yml:84-89`）。开启后：
  - `trace()` 是**同步 HTTP**，3 次重试、每次 5s（:104-135），在 `chat()` 最前面调用（`AgentOrchestrator.java:140-143`）→ 首 token 前额外阻塞；
  - `recordGeneration/recordScore/recordRetrieval` 走后台单线程有界队列（1024/2048，:33、:47、:62-99）；
  - 手写 JSON 字符串拼接（:152-157 等），非 SDK（`langfuse-java` 依赖在 pom 中但未使用，`qknow-hermes-core/pom.xml:136-140`）；
  - 没有 OTel/Micrometer Tracing 上下文，没有 traceId 跨 gRPC 传播（`request_id` 仅业务字段）。
- Micrometer：`AgentMetricsService` 是 `@Component`（:15-23），但 Hermes 只注册了 `SimpleMeterRegistry`（`HermesApplication.java:31-34`），**没有 prometheus registry**（全仓仅 `qknow-server/pom.xml:110` 有 `micrometer-registry-prometheus`），Hermes 的 `application.yml` 也无 `management.endpoints` 暴露配置 → `/actuator/prometheus` 不存在，**指标只存在于 JVM 内存，外部不可抓取**。
- 指标语义失真：`recordReactSteps(1)` 与 `recordTurnDuration("hermes_agent", 1, ...)` 传的是常量（`AgentOrchestrator.java:184-185`），不是真实步数/轮次。
- 自定义 trace 栈（`trace/TraceCollector`、`HierarchicalExecutionTraceEngine`）main 引用 0；`infra/prompt/CanonicalPromptCompiler` 0 引用。
- 健康检查硬编码版本：`HermesGrpcService.java:111` 返回 `"2.2.1"`，与 `pom` 版本不同步的风险（当前版本恰好一致，pom 为 2.2.1）。

### 2.7 安全与多租户

- gRPC 9090：无 TLS（客户端 `usePlaintext`，`HermesGrpcClient.java:43-47`）、无认证/无拦截器（`HermesGrpcService` 无 `ServerInterceptor`，全仓 grep 0）、未绑定回环地址（`application.yml:4-8` 只给 port）。内网可达即任意调用。
- 凭据透过 gRPC 明文传输：`ChatRequest.ModelConfig.api_key`/`ModelCredentials.api_key`（proto:45-57）；控制面把 DB 解密后的 apiKey 放入请求（`KbAgentConfigServiceImpl.java:391-421`）。
- 租户隔离：短期记忆键只有 conversationId（`AgentOrchestrator.java:580-582` + `ShortTermMemory.java:214-216`）；LTM 的 scope 是 `workspace:<id>:bot:<id>`（:588-590）但只在 LTM 过滤用。conversationId 若全局唯一尚可，若各 workspace 独立发号则存在跨租户串话风险（推断，需确认 ID 生成策略）。
- Prompt 注入：有 System 层安全指令与 RAG 文档 `<security_notice>`（:1003-1041），属提示词级防御；无输入/输出内容审核、无工具权限校验（`ToolCallbackResolverConfig` 只按 bean 名解析，不校验调用者/租户；`tool/permission/PermissionCheckedToolCallback` 存在但 0 引用）。
- HITL 状态错误（跨 JVM）：`ApprovalNodeExecutor` 用进程内静态 `ConcurrentHashMap` 存 `CompletableFuture`（:23、:38、:70-97），注释声称"审批状态存储在 Redis"（:19）但类中无 Redis；控制面 `ApprovalProxyController` 直接 import 该静态 map（`ApprovalProxyController.java:20-22`），而控制面按 `QKnowApplication.java:21-27` 排除了 `tech.qiantong.qknow.hermes.*` 的组件扫描——**两个 JVM 各有一份空 map**，控制面 `/hermes/approval/pending` 永远看不到 Hermes 内挂起的审批，approve 也永远返回 404。前端也未发现对 `/hermes/approval` 或 8081 的调用（`grep frontend/src` 无命中，未知是否有其他入口）。

---

## 3 缺陷与风险清单

> 级别定义：P0 = 核心能力不成立/安全高危；P1 = 正确性、成本、时延显著受损；P2 = 可维护性/一致性问题。
> 「修复成本」按最小改动估算：S ≤ 半天，M ≤ 3 天，L > 3 天。

| ID | 级别 | 文件:行 | 证据（已验证） | 影响 | 修复成本 |
|---|---|---|---|---|---|
| H-P0-1 | P0 | `qknow-framework/qknow-ai/.../DeepSeekCompatibleChatModel.java:166-188, 222-229, 296-303` | 只识别 `DeepSeekChatOptions` 才写 `tools`（:168、:176）；对标准 `ToolCallingChatOptions` 无处理（全仓 grep 0）；`tool_calls` 只进 metadata，从不构造 `AssistantMessage.ToolCall`（全仓 grep `.toolCalls(`=0） | DeepSeek 平台下 **工具定义不发送、tool_calls 不执行**，ReAct 退化为单次生成；知识库/天气/HTTP 工具全部失效 | M（约 80–120 行：映射 `ToolCallingChatOptions.toolCallbacks`→OpenAI tools JSON；解析并构造 `AssistantMessage.ToolCall`；补 fake-server 契约测试） |
| H-P0-2 | P0 | `AgentOrchestrator.java:152-165, 461-546`；`DeepSeekCompatibleChatModel.java:84-110` | `Flux.create` 无 `onDispose`；内层 `subscribe` 的 Disposable 未保存；流循环不检查 `sink.isCancelled()` | 客户端断开后 LLM 继续跑完，Token 空烧；并发闸门不释放，容量泄漏 | S（约 20 行：保存 Disposable + `emitter.onDispose(dispose)`；流循环加 `isCancelled` 并关闭 response body） |
| H-P0-3 | P0 | `qknow-hermes-core/src/main/java/.../grpc/HermesGrpcService.java:22-61`；`HermesGrpcClient.java:43-47`；`application.yml:4-8` | 无 interceptor/无 TLS/无认证；proto 明文 api_key（`hermes.proto:45-57`） | 同网任意进程可调用并窃取/滥用模型凭据 | M（TLS/mTLS + 凭据移出请求，改由 Hermes 侧按别名读取；或至少绑定 127.0.0.1 + 网络策略） |
| H-P0-4 | P0 | 全仓引用扫描（本报告 §0.3） | 161 类型 main 零引用；89 个非 bean 零引用；`AgentRegistry` 无注解无实例化 | "多智能体/蜂群/辩论/HITL/自愈"宣称与生产不符；维护者误判能力边界 | L（二选一：接线或删除；最小是文档降级 + 删除/隔离死代码包） |
| H-P0-5 | P0 | `ApprovalNodeExecutor.java:23,29-97`；`ApprovalProxyController.java:20-22`；`QKnowApplication.java:21-27` | 审批状态是进程内静态 map，控制面 import 的是另一 JVM 的同名类；组件扫描已排除 hermes 包 | HITL 全链路不可用（approve/reject 恒 404）；与论文/演示宣称的"人机协同"冲突 | M（把 pending 状态落 Redis/DB，键 `approval:{flowId}:{requestId}:{nodeId}`，控制器走 HTTP/gRPC 到 Hermes） |
| H-P1-1 | P1 | `AgentOrchestrator.java:148`（对照 :251） | `final String modelName = request.getModelConfig().getModelName();` 先解引用，无 modelConfig 时 NPE；credentials 分支不可达 | 仅传 ModelCredentials 的调用方 100% 失败（控制面当前总传 modelConfig，故线上未暴露） | S（改为空安全 + 先判 hasModelCredentials） |
| H-P1-2 | P1 | `AgentOrchestrator.java:272-279`；`RetrievalEvaluator.java:44-99` | 每轮有 RAG 上下文即**无条件**再跑一次阻塞 LLM 复评；控制面已做过 CRAG（编排器注释 :270-271 自认） | 每轮 +1 次完整 LLM 调用，TTFT 与成本上升；结果只用于日志与提示词标注 | S（门控：仅当 `hermes.crag.enabled=true` 且控制面未标记已评估时执行；或改为异步） |
| H-P1-3 | P1 | `AgentOrchestrator.java:174-180, 401-402, 996-1001`；`DeepSeekCompatibleChatModel.java:199-201, 311-319` | 真实 usage 已解析但被丢弃；成本用 `len/2` 估算；缓存命中 token 恒传 0（:179） | 成本大盘失真、无法度量 Context Caching 收益；容量规划与计费不可信 | S（从 ChatResponse metadata/流末帧取 usage，记录 4 个真实计数；estimate 仅作 fallback） |
| H-P1-4 | P1 | `AgentOrchestrator.java:327-350, 391-405, 601-630, 972-990` | 历史按"20 条"截断；plan 路径把整段答案单帧返回；plan 任务 `join()` 无超时、semaphore 无超时 | 长上下文溢出（DeepSeek 400）无保护；plan 路径失去打字机体验；任务可无限等待 | M（引入真实 token 预算与裁剪/摘要；plan 结果改为流式；加超时） |
| H-P1-5 | P1 | `AgentOrchestrator.java:60-67, 710, 726-729`；`application.yml:98-99` | 主 ReAct 流不受 LLM semaphore 约束；plan 池 4 线程 + 无超时 | 突发流量下并发对 DeepSeek 无节制；plan 任务阻塞线程直至模型返回 | S（ReAct 入口纳入 semaphore 或信号量化 + 超时） |
| H-P1-6 | P1 | `ShortTermMemory.java:36-55, 40-47`；`AgentOrchestrator.java:580-599` | 无 Redis 时回退进程内全局 list、跨会话共享；Redis 键无 workspace/user；写入无长度裁剪 | 单机/Redis 故障时跨会话串话（隐私事故）；单会话内存/Redis 无限增长 | S（无 Redis 时按 sessionId 分 Map；键加 workspace:user；写后 LTRIM 至 N） |
| H-P1-7 | P1 | `MemoryConfiguration.java:24-32` | `@Value("${TONGYI_API_KEY}")` 无默认值，启动硬依赖；与 Spring AI 自动配置 embedding 存在条件竞争 | 新环境启动失败（变量缺失）；实际 embedding 来源不确定 | S（给默认值/启动校验；显式 `@Primary` 或排除自动配置） |
| H-P1-8 | P1 | `deploy/docker/database/mysql/init/init.sql:2020-2029` vs `WeatherQueryToolFunction.java:29` | DB code=`weather_query...`，bean=``weatherQuery`；仅 `get_current_time` 匹配（SQL:2031） | 工具配置静默失效（解析 null），用户以为已启用 | S（加命名映射表或统一 code 约定；resolver 解析失败显式告警） |
| H-P1-9 | P1 | `observability/LangFuseTracingService.java:104-135`；`AgentOrchestrator.java:140-143` | trace 创建是同步 HTTP、3×5s 重试，位于流开始前 | 开启 LangFuse 时首 token 延迟可增加 5–15s+ | S（trace 改为异步/首次 enqueue；或本地生成 traceId 后异步投递） |
| H-P1-10 | P1 | `AgentMetricsService.java:15-48`；`HermesApplication.java:31-34`；`qknow-hermes-core/pom.xml`（无 prometheus） | 只有 SimpleMeterRegistry；无 prometheus registry、无 management 暴露 | 指标不可抓取 = 实质没有可观测性 | S（starter 加 `micrometer-registry-prometheus`，暴露 `/actuator/prometheus`） |
| H-P1-11 | P1 | `AgentOrchestrator.java:385-389, 398-405`；`PlanSolveConfig.java:13-14` | plan-solve 开启时 `createPlannerSupervision` 与 `planAndSolve` **各调一次 `createPlan`**（双份规划 LLM）；计划文本拼进 System prompt | 每轮 +1 次规划调用；前缀缓存被破坏 | S（一次规划、两处复用；supervision 默认关闭） |
| H-P2-1 | P2 | `tool/resilience/ToolResilienceDecorator.java:47, 67`；`config/ToolCallbackResolverConfig.java:69-70` | `Math.max(maxRetries,1)` 语义是"最大尝试次数"，传 1 即不重试；注释"单次重试"不实 | 工具瞬时失败不重试 | S（重命名为 maxAttempts 或 `+1`） |
| H-P2-2 | P2 | `ChatModelFactory.java:39, 64-81` | 按 apiKey.hashCode 缓存 ChatModel，无淘汰；每个实例自带 HttpClient/连接池 | 多租户/轮换 key 时对象与连接泄漏 | S（LRU/大小上限） |
| H-P2-3 | P2 | `HermesGrpcService.java:111` | 版本硬编码 "2.2.1" | 版本漂移后健康检查误导 | S |
| H-P2-4 | P2 | `AgentOrchestrator.java:104-113, 1235-1274` | `needsToolCalling` 关键词路由仅在工具模型路由启用时使用，而该路由默认关闭（`application.yml:37-39`） | 死分支；维护误导 | S（删除或接回） |
| H-P2-5 | P2 | `AgentOrchestrator.java:1073-1143` | `appendRagContext` 无调用者（全文件 grep） | 死代码，两套 RAG 注入逻辑并存易误改 | S |
| H-P2-6 | P2 | `qknow-module-kb-biz/.../tool/function/WebSearchToolFunction.java:21` 等 4 个类 vs hermes 同名类 | 控制面与 Hermes 各有一份工具实现（同名 bean），逻辑重复 | 双维护、行为漂移 | S/M（下沉到共享模块或删除一侧） |
| H-P2-7 | P2 | `HermesGrpcClient.java:63-101`；`application-bot-dev.yml`/`prod.yml`（空文件） | gRPC host/port 全靠默认值；无配置项 | 部署不可配置/易错连 localhost | S |
| H-P2-8 | P2 | `proto/hermes.proto:70-107` | 事件只有 chunk/finished/tool_invoked/error/done/reflection；无 usage、无 tool_call 参数、无 step/handoff/interrupt | 前端/评测无法获知成本、工具参数、步骤；协议扩展受限 | M |
| H-P2-9 | P2 | `AgentOrchestrator.java:553-578` | 用户消息在模型调用前落记忆；请求失败也落库；assistant 在 doOnComplete 落库 | 失败对话污染记忆 | S |
| H-P2-10 | P2 | `experiment/runner.py:111, 230`；`daemon/DaemonProbeDispatcher.java:85-101` | 实验评测走 `HermesBenchmarkProbeRunner`+UDS 探针，而非 chat gRPC | 评测结论不能直接外推生产对话质量（方法学风险） | L（需 Lead/论文作者决策：补在线路径评测或显式声明） |

---

## 4 优化建议（优先级 / 工作量 / 风险 / 最小改动）

### P0（立即，1–3 天）

1. **修复 DeepSeek 工具调用链（H-P0-1）**
   - 最小改动：在 `DeepSeekCompatibleChatModel.buildRequestBody` 增加 `options instanceof ToolCallingChatOptions t` 分支，把 `t.getToolCallbacks()` 序列化为 OpenAI tools schema；在 `parseResponse/parseStreamChunk` 把 `tool_calls` 构造为 `AssistantMessage.ToolCall`（id/type/name/arguments），并设置 `toolCalls(...)`。
   - 验证：写一个本地 fake HTTP server 契约测试（非 mvn 长测；单测即可），断言请求含 tools、响应被框架 `hasToolCalls()` 识别；再跑一条真实 DeepSeek 冒烟（需 Lead 授权）。
   - 风险：低（新增分支，不动现有文本路径）；注意 DeepSeek 流式 `tool_calls` 是**增量拼接**（index/arguments 分片），需按 index 聚合，不能直接整体覆盖。
2. **取消级联（H-P0-2）**
   - 最小改动：`AgentOrchestrator.chat` 里 `emitter.onDispose(() -> innerDisposable.get().dispose())`；`executeAgent` 保存 `stream.subscribe(...)` 返回值；`DeepSeekCompatibleChatModel.streamResponse` 的 `lines.forEach` 改为带 `sink.isCancelled()` 检查的循环，并在取消时关闭 `Stream`（try-with-resources 已保证关闭，但需提前退出）。
   - 风险：低；注意 `Flux.create` 的 `onDispose` 与 `doFinally` 顺序。
3. **HITL 状态外置（H-P0-5）**
   - 最小改动：`ApprovalNodeExecutor` 的 pending 状态迁移到 Redis（复用 `IRedisService`，键加 TTL 24h），`approve/reject` 通过 Redis 读改写 + 事件通知；控制面代理改调 Hermes HTTP（8081）而不是引用静态类。
   - 风险：中（涉及控制面与 Hermes 两侧接口）；若短期无法做，至少在 `ApprovalProxyController` 明确返回 501 并在前端隐藏入口，避免"假功能"。
4. **gRPC 安全（H-P0-3）**
   - 最小改动：Hermes 侧删除 `ModelConfig.api_key` 使用路径，改由 `ModelNameResolver`+本地密钥配置；通道改 TLS 或至少绑定 127.0.0.1 + 共享密钥 metadata 校验（一个 `ServerInterceptor` 20 行即可）。
   - 风险：中；涉及控制面传参改造。

### P1（1–2 周内）

5. **去掉首 token 前的同步 LLM 复评（H-P1-2）**：加配置开关默认关闭；或并行为 Future，在模型首 token 后再合并（结果只影响提示词时不可行，需产品决策）。
6. **真实 usage/cost/缓存命中（H-P1-3）**：从流末帧 usage 记账；`DeepSeekCostGovernor.recordUsage` 拆分 hit/miss；LangFuse generation 记录真实 token。这一步是后续所有成本优化的度量前提。
7. **上下文预算（H-P1-4）**：引入 `hermes.context.max-prompt-tokens`，用字符/token 混合估算在装配 messages 时裁剪（优先丢最旧 user/assistant，保留 System 与最后 N 轮），超限时先摘要后裁剪；plan 路径输出改流式。
8. **并发与超时（H-P1-5）**：ReAct 入口纳入 semaphore（带 `tryAcquire(timeout)`），plan 任务 `orTimeout`，`join` 改 `get(timeout)`。
9. **记忆隔离与裁剪（H-P1-6）**：Redis 键 `memory:short:{workspace}:{user}:{conversation}`；写入后 `lTrim(-N,-1)`；无 Redis 时按 session 分 Map。
10. **可观测性真正落地（H-P1-9/10）**：starter 加 prometheus registry + `management.endpoints.web.exposure.include=health,metrics,prometheus`；LangFuse trace 改异步；把 `recordReactSteps(1)` 改成真实模型调用次数/工具次数（可从 hook 回调取）。
11. **工具编码对齐（H-P1-8）**：短期加 `code → bean` 映射表（DB 可配），resolver 解析失败写 ERROR 日志并返回结构化错误，不再静默。

### P2（清理与一致性）

12. 删除/归档死代码包（建议按 §0.3 清单分批）：`agent/swarm`、`agent/cognitive`、`agent/consensus`、`agent/intent`、`cognitive/**`、`transaction/**`、`workflow/**`、`flow/hitl/**`、`flow/stategraph/**`、`tool/mcp/**` 中未接线部分。保留 benchmark/daemon 可独立成 `hermes-probe` 模块（避免生产 jar 体积与启动 bean 数膨胀）。
13. 统一工具实现（删除控制面或 Hermes 一侧重复的 webSearch/httpRequest/textTransform/weatherQuery）。
14. 修 `ToolResilienceDecorator` 重试语义、`ChatModelFactory` 缓存上限、`appendRagContext` 死代码、健康检查版本号。
15. 协议扩展：`ChatEvent` 增 `Usage`、`ToolCall`（name+args 摘要）、`StepStarted/Finished`、`Interrupt`；`ChatRequest` 增 `traceparent`/`tenant_id` 与 `capabilities` 协商字段（向后兼容用 reserved 字段）。

---

## 5 与顶级框架的能力差距

> 对标依据：LangGraph（StateGraph/checkpoint/interrupt）、OpenAI Agents SDK（handoff/guardrails/tracing/sessions）、AutoGen（group chat/actor 事件驱动/human proxy）、Anthropic 多智能体研究系统（orchestrator-worker、上下文隔离、子代理并行）的公开能力模型。以下只比较"能力是否存在/是否接线"，不比较性能数字。

| 能力维度 | 本项目现状（带证据） | LangGraph | OpenAI Agents SDK | AutoGen | Anthropic 多智能体 |
|---|---|---|---|---|---|
| 图/状态机抽象 | 依赖 alibaba graph 的 ReactAgent（固定 LLM↔Tool 两节点），未自建 DAG 编排用于 chat（`AgentOrchestrator.java:437-446`） | ✅ 任意 StateGraph + 条件边 | ⚠️ 轻量 handoff 链 | ✅ 对话/事件图 | ⚠️ 编排器-工作者脚本 |
| 状态持久化/断点恢复 | ❌ 无 saver/threadId（builder 未设；`ReactAgent` 支持但未用）；记忆在外部 Redis/pgvector，与图状态脱节 | ✅ checkpointer（内存/PG/Redis）+ 时间旅行 | ⚠️ Sessions 持久化会话，非图状态 | ⚠️ 需自建 | ⚠️ 靠外部存储 |
| Handoff / 子代理协议 | ❌ `HandoffStack/HandoffFrame` 存在但无生产调用；Supervisor/Worker 死 | ✅ Command/handoff + 子图 | ✅ handoffs 一等公民 | ✅ group chat speaker selection | ✅ orchestrator-worker 委派 |
| Guardrails | ⚠️ 仅提示词级安全块 + ReAct 防循环（`AgentOrchestrator.java:1003-1013`、`ReActStrictWindowCycleGuard`）；无输出审核/工具权限 | ⚠️ 需自建/中间件 | ✅ input/output guardrails | ⚠️ 需自建 | ⚠️ 策略层 |
| Human-in-the-loop | ❌ 状态跨 JVM 错误（H-P0-5）；`ReactAgent.interrupt()` 存在但未用 | ✅ interrupt + resume | ⚠️ 需自建 | ✅ UserProxyAgent | ✅ 审批点 |
| 工具生态/MCP | ❌ 聊天链零接线；两套 MCP 实现；工具 code 不匹配（H-P0-1/8） | ✅ ToolNode + MCP 适配 | ✅ hosted/local tools + MCP | ✅ 函数工具 | ✅ 内部工具 |
| 可观测性 | ❌ 指标不可抓取；LangFuse 可选且手写、默认关；无 OTel trace 传播 | ✅ LangSmith 集成 | ✅ 内置 tracing（spans） | ⚠️ 需接入 | ✅ 内部 tracing |
| 上下文工程 | ⚠️ 固定 20 条历史 + 无 token 预算 + 无热路径摘要；RAG 在 User 消息（利于缓存） | ✅ 可编程 context 压缩/裁剪 | ✅ Sessions + 自动裁剪 | ⚠️ 需自建 | ✅ 子代理上下文隔离（本项目完全没有隔离式子上下文） |
| 评测闭环 | ⚠️ 离线探针/benchmark 有（`DaemonProbeDispatcher`），但与生产 chat 路径不是同一对象（H-P2-10） | ✅ LangSmith 数据集 | ✅ 内置 evals | ⚠️ 需自建 | ✅ 内部评测 |
| 并发/背压 | ⚠️ Reactor 外壳 + 阻塞模型客户端；无 gRPC flow control；取消不级联 | ✅ 异步优先 | ✅ async | ✅ actor | ✅ 并行子代理 |

**判断**：本项目的 Hermes 在"Agent 运行时"维度是**单 Agent + 单次生成**水平；框架侧依赖 alibaba graph 提供了图执行与 hook 的可能性，但项目未使用 checkpoint/interrupt/handoff 等关键能力；多智能体资产停留在"引擎 + 契约测试"层，与上述四个框架的差距是**结构性的（状态/协议/可观测/评测），不是调参级的**。

---

## 6 未验证与需 Lead 决策事项

### 6.1 未验证（明确列出，避免误当结论）

1. **生产 `ai_model.platform` 实际取值**：代码与种子数据（`deploy/sql/postgresql/02-init-data.sql:54-55`）为 `DeepSeek`，若实际运营改为 `OpenAI`+DeepSeek base_url，则 H-P0-1 不成立（工具可用、思考模式不可用）。需有权限的同学查生产库。
2. **gRPC 线程池与 graph 调度线程的精确归属**：`java.net.http` 阻塞读最终落在 gRPC 线程、Netty 线程还是 graph 内部 executor，未运行验证；「阻塞订阅线程」为结构性推断。
3. **`ReactAgent` 的 `parallelToolExecution` 默认值**：仅凭字节码字段默认值推断为 false，未做行为验证（且 Tool 节点当前不可达）。
4. **Redis 是否在生产 Hermes 中注入**（决定短期记忆是否走全局内存回退）：`qknow-redis` 依赖存在，部署是否配置 `spring.data.redis` 未验证。
5. **`embeddingModel` bean 与 Spring AI 自动配置的竞争结果**：`@ConditionalOnMissingBean` 的实际选中者未验证；若选中 OpenAI auto-config，会违反"唯一 Qwen Embedding"基线。
6. **前端是否从其他端口/网关调用 `/hermes/approval` 或 8081**：仅 grep 了 `frontend/src`，未覆盖部署网关/nginx 配置。
7. **LangFuse/RAGAS 是否在生产开启**：`LANGFUSE_ENABLED` 环境变量配置未知。
8. **控制面 `history` 的实际上限**：`KbAgentConfigReqVO.historyMessages` 的截断逻辑未读，无法判断 DeepSeek 长上下文是否会被撑爆。

### 6.2 需 Lead 决策

1. **"多智能体"叙事与实现对齐**：是补接线（把 Supervisor/swarm 接进 chat 或独立 API），还是删除死代码并在架构文档降级表述？两种都要动 100+ 文件，建议先做"文档 + 模块拆分"再排期。
2. **DeepSeek 平台与工具调用的取舍**：修复 H-P0-1 是唯一正确路径（同时保留 DeepSeekChatOptions 的 thinking），还是短期把工具链切到 OpenAI-compatible 平台并放弃 thinking？建议前者。
3. **HITL 的宿主归属**：审批状态放在控制面（有 DB/Redis 与前端）还是 Hermes？若保留 Hermes，必须通过 HTTP/gRPC 暴露；若控制面已有 `KbFlowServiceImpl` 的 HITL（Lead 笔记核为生产引擎），建议直接删除 Hermes 侧 HITL，避免双实现。
4. **评测口径**：论文/实验使用探针引擎指标（`experiment/runner.py:111,230`）而非 chat gRPC 路径，需在文档/论文中明确声明，或补一条在线路径评测。
5. **死代码清理的边界**：`qknow-hermes-core` 里 89 个非 bean 死类 + 72 个无人消费 bean，是否允许在本次评审后集中清理（会改动大量测试）？

---

### 附录 证据索引（按主题）

| 结论 | 证据 |
|---|---|
| gRPC 契约/事件字段/明文凭据 | `qknow-hermes-proto/src/main/proto/hermes.proto:13-23, 28-43, 45-57, 70-107` |
| 服务端入口/错误映射/健康版本 | `grpc/HermesGrpcService.java:22-23, 32-61, 96-114` |
| 取消桥接/无流控 | `grpc/GrpcReactorBridge.java:26-77`（:50-56 订阅无 isReady；:72-77 取消回调） |
| 编排器全链路 | `agent/AgentOrchestrator.java:130-222, 244-551` |
| 首 token 前阻塞复评 | `AgentOrchestrator.java:272-279`；`agent/RetrievalEvaluator.java:44-99` |
| 系统提示词/安全/RAG 注入位置 | `AgentOrchestrator.java:381-396, 1003-1041, 1073-1143(死), 1145-1174` |
| plan-solve 早返回/单帧输出 | `AgentOrchestrator.java:398-405, 601-630, 860-900, 972-990` |
| 双份规划 | `AgentOrchestrator.java:385, 610, 735-761` |
| 并发闸门/线程池/信号量 | `AgentOrchestrator.java:59-67, 449-457, 693-733, 836-858` |
| token 估算/成本记账 | `AgentOrchestrator.java:174-180, 401-402, 996-1001`；`cost/DeepSeekCostGovernor.java:96-184` |
| LangFuse 同步 trace/异步队列/条件 bean | `observability/LangFuseTracingService.java:28-35, 47-99, 104-135, 140-180` |
| 指标不可导出 | `agent/metrics/AgentMetricsService.java:15-48`；`HermesApplication.java:31-34`；`qknow-server/pom.xml:110`（仅控制面有 prometheus） |
| DeepSeek 客户端（阻塞流/tools 处理/usage/typed toolcall） | `qknow-ai/.../deepseek/DeepSeekCompatibleChatModel.java:60-110, 120-206, 208-247, 285-330` |
| DeepSeekChatOptions 五参数仅部分透传 | `qknow-ai/.../deepseek/DeepSeekChatOptions.java:31-51` vs `DeepSeekCompatibleChatModel.java:166-188`（无 topP/presence/freq 写入） |
| 平台路由/缓存/DeepSeek 分支 | `config/ChatModelFactory.java:57-74, 123-128`；`config/AiPlatformEnum.java:21, 72-79` |
| 模型别名/退役升级 | `config/ModelNameResolver.java:60-80`；`config/AiModelAliasProperties.java:40-47` |
| 原生工具调用框架契约 | 依赖 jar：`spring-ai-alibaba-agent-framework-1.1.2.2` 的 `ReactAgent.makeModelToTools`（`AssistantMessage.hasToolCalls()`）、`AgentLlmNode.buildChatOptions`（`ToolCallingChatOptions`）；`spring-ai-model-1.1.2` 的 `AssistantMessage.Builder`（properties 与 toolCalls 分离） |
| 工具 bean 名/DB code 不匹配 | `tool/function/WeatherQueryToolFunction.java:29`、`TimeToolComponent.java:73/79`；`deploy/docker/database/mysql/init/init.sql:2020-2029, 2031` |
| 工具弹性包装/重试语义 | `config/ToolCallbackResolverConfig.java:31-71, 68-70`；`tool/resilience/ToolResilienceDecorator.java:40-52, 67` |
| 伪知识库工具 | `tool/function/SearchKnowledgeTool.java:15-45` |
| 短期记忆键/TTL/无裁剪/内存回退 | `memory/ShortTermMemory.java:36-68, 149-164, 204-233` |
| 长期记忆/向量库/Qwen embedding | `memory/MemoryConfiguration.java:17-62`；`memory/LongTermMemory.java:79-300` |
| 记忆生命周期 | `memory/MemoryManager.java:43-66`；`memory/SleepTimeMemoryAgent.java:39`；`AgentOrchestrator.java:351-378, 553-599` |
| HITL 静态 map/跨 JVM 代理 | `flow/node/ApprovalNodeExecutor.java:19-97`；`controller/ApprovalController.java:16-77`；`qknow-module-kb/.../ApprovalProxyController.java:20-22`；`QKnowApplication.java:21-27` |
| 控制面 gRPC 客户端 | `qknow-module-kb/.../HermesGrpcClient.java:27-113, 115-174` |
| 控制面前置缓存/检索/请求组装 | `KbAgentConfigServiceImpl.java:227-255, 258, 263-365, 391-421, 438-439` |
| 控制面依赖 hermes-core | `qknow-module-kb/qknow-module-kb-biz/pom.xml:96-111` |
| 死代码统计 | 本报告 §0.3（脚本：全仓 token 扫描，485 类型/161 零引用/89 非 bean 零引用） |
| 实验走探针而非 chat 路径 | `experiment/runner.py:111, 230`；`daemon/DaemonProbeDispatcher.java:85-101, 228` |
| MCTS 合成观测/合成向量 | `agent/planner/impl/DefaultTreeOfThoughtsReActPlanner.java:162-197, 198-230, 276-306` |
| PRM/回溯/推测仅探针 | `daemon/DaemonProbeDispatcher.java:228`（唯一生产外调用点）；`planner/prm/*`、`agent/speculative/*` 全仓 main 引用 0 |
| 多智能体死代码 | `agent/SupervisorAgent.java:131-182`；`agent/AgentRegistry.java:117-123`；`agent/ReflectiveAgent.java:29-142`；全仓 `grep new AgentRegistry` 仅测试 |
| 配置默认值 | `qknow-hermes-starter/src/main/resources/application.yml:4-8, 36-50, 84-111, 124-127` |
| 应用启动/扫描/默认模型 bean | `qknow-hermes-starter/.../HermesApplication.java:17-54` |
| 工具重复实现 | `qknow-hermes/.../tool/function/WebSearchToolFunction.java:21` vs `qknow-module-kb/.../tool/function/WebSearchToolFunction.java:21`（及 HttpRequest/TextTransform/Time 同名类） |

---

**审计结论摘要**：Hermes 的真实生产能力集中在一条 1275 行的编排器上，它完成了"预检索 RAG → 单 Agent 流式生成 → 成本/日志/记忆"这条可用但脆弱的链路；而平台宣称的 ReAct 工具循环、多智能体、辩论/共识、HITL、Planner/MCTS/PRM、自愈与遥测，绝大多数要么在 DeepSeek 客户端契约处断链（P0-1），要么只有探针/契约测试可达（P0-4），要么状态放错 JVM（P0-5）。修复顺序应是：先恢复工具循环与取消传播（两项都在同一客户端/编排器文件内，改动小、收益大），再补真实 usage/缓存与 token 预算（成本与上下文工程的前提），最后决定多智能体与 HITL 的取舍。
