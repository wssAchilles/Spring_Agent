# Lead 一手核查笔记（草稿，最终汇入 00-MASTER-REVIEW.md）

> 方法：只读静态阅读；所有结论标 `文件:行`。凡标「未验证」者为未运行验证。

## 1. 主执行链路（已核对）

- 控制面入口：`backend/qknow-module-kb/.../service/agent/impl/KbAgentConfigServiceImpl.java:226 chatMessage()`
  - `:258` 语义缓存查询；`:263-365` 同步 RAG 预检索（多库并发召回 + 跨库 RRF + 20KB 截断），**在返回 Flux 之前同步执行**；
  - `:368-374` 工具方法 ID → `code` 列表；
  - `:391-410` 构建 gRPC `ChatRequest`（含 modelConfig 明文 apiKey、ragContexts、toolMethodIds、history）；
  - `:438-439` 调 `hermesGrpcClient.chat`；`:449-456` 成功后异步写语义缓存。
- gRPC 客户端：`.../service/agent/HermesGrpcClient.java:60-96`（deadline 120s、取消级联 onDispose、plaintext channel 无 TLS/无鉴权）。
- Hermes 服务端：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/grpc/HermesGrpcService.java:38-62`。
- 编排器：`.../agent/AgentOrchestrator.java:130 chat()` → `:244 executeAgent()`：
  - `:267` `retrievalEvaluator.evaluate(...)` **无条件调用 LLM**（`RetrievalEvaluator.java:58`），而控制面已做过 CRAG（注释承认重复，见 `AgentOrchestrator.java:266-272`）；
  - `:284-312` 将预检索 RAG 结果包装为伪工具 `knowledgeBase{id}` / `knowledgeQuery`（`SearchKnowledgeTool` 仅回放预检索文本）；
  - `:385-388` `createPlannerSupervision`（plan-solve 开启时会额外 LLM 调用）；
  - `:398` `planAndSolve`（开启时 createPlan + N worker + aggregate + reflect 多次**阻塞** LLM 调用，且 `:403 emitSingleAnswer` 一次性返回，失去流式）；
  - `:438-446` 构建单 `ReactAgent`；`:461` `stream.subscribe(...)` 裸订阅；
  - `:428` `ReActCycleGuard(10,3)`；`:441` `ModelCallLimitHook.runLimit(10)`。
- `Flux.create` 内同步执行到 `agent.stream(...)`：`chat()` 在首次 token 前至少阻塞一次 CRAG LLM 完成；`plan-solve` 开启时可达 4+ 次。gRPC 线程（默认池）被占用。
- 取消：`GrpcReactorBridge.java:67-75` 客户端取消 → dispose 外层 Flux；但 `Flux.create` 未注册 `onDispose` 取消内层 `stream.subscribe` → 内层 ReAct 不中断（算力/Token 空跑），`active` 计数仅靠 onComplete/onError 释放。

## 2. 关键缺陷（Lead 已确认）

| ID | 级别 | 证据 | 说明 |
|---|---|---|---|
| L-P0-1 | P0 | `qknow-hermes-starter/docker/Dockerfile:1` | `eclipse-temurin:17-jre` 运行 Java 21 编译产物 → `UnsupportedClassVersionError`；与 AGENTS.md Java 21 铁律冲突 |
| L-P0-2 | P0 | `AgentOrchestrator.java:266-272` + `RetrievalEvaluator.java:33-67` | 控制面已 CRAG，Hermes 再无条件跑一次 LLM 评估；每轮 RAG 对话至少 +1 次完整 LLM 调用，抬升 TTFT 与成本；结果只用于日志 |
| L-P0-3 | P0 | `AgentOrchestrator.java:461-497` + `GrpcReactorBridge.java:67` | 外层 dispose 不级联内层 `agent.stream` 订阅 → 客户端断开后 ReAct/LLM 继续跑；无显式内层 Disposable |
| L-P0-4 | P0 | 见 §4 死代码扫描 | 多智能体/蜂群/辩论/共识/意图/事务/Saga/认知内核等 8 个子系统无生产调用，仅契约测试；平台"多智能体"宣称与生产路径不符 |
| L-P0-5 | P0 | `HermesGrpcService.java:23`（无 interceptor） | gRPC 9090 无 TLS/无认证；Hermes Dockerfile `EXPOSE 9090`；若同网可达=任意内网调用 |
| L-P1-1 | P1 | `KbAgentConfigServiceImpl.java:368-374` + `deploy/docker/database/mysql/init/init.sql:2020+` | DB 工具 code（`weather_query`…）与 Hermes bean 名（`weatherQuery`…）不一致；仅 `get_current_time` 能对上；用户配置工具大概率无法执行 |
| L-P1-2 | P1 | `ToolNodeBO.java:120-152` | 工具不存在/Aadapter 未连接时仍返回 `status=SUCCESS` + error JSON；静默失败，工作流误判成功 |
| L-P1-3 | P1 | `McpToolAdapter.java:80-86` | `@PostConstruct` 在存在 `GITHUB_PERSONAL_ACCESS_TOKEN` 时自动注册 GitHub MCP server（隐藏行为/凭据面） |
| L-P1-4 | P1 | `AgentOrchestrator.java:152` | `request.getModelConfig().getModelName()` 在无 modelConfig 时 NPE（后续 `:255` 才做空判断），credentials 分支不可达 |
| L-P1-5 | P1 | `AgentOrchestrator.java:401-402` + 全文件 grep | ReAct 正常路径不采集真实 usage，`doOnComplete` 用 `estimateTokenCount` 估算成本；成本治理数据失真 |
| L-P1-6 | P1 | `AgentOrchestrator.java:432-435` | `wrapWithCycleGuard(null)` 返回 null，ReactAgent toolNames 含不可解析名（行为取决于框架；需运行验证） |
| L-P1-7 | P1 | `backend/Dockerfile` vs `qknow-server/docker/Dockerfile:1` | 两套构建（maven:21 builder vs 预构建 jar），server Dockerfile `--enable-preview`（Java 21 preview 特性在生产开启，AOT/升级风险） |
| L-P2 | P2 | `HermesApplication.java:47-54` | 每次启动同步执行 `SleepTimeMemoryAgent.consolidateIdleConversations()`（CommandLineRunner），启动期副作用 |
| L-P2 | P2 | `application.yml`（server） | `spring.threads.virtual.enabled` 被注释，Tomcat 800 线程 + SSE 长连接；Java 21 虚拟线程未启用 |

## 3. 双引擎/双栈重复（已确认）

- 工作流：控制面 `KbFlowServiceImpl.executeFlow()`（`qknow-module-kb/.../flow/impl/KbFlowServiceImpl.java:295`，拓扑分层 + 虚拟线程 + checkpoint + HITL suspend/resume）**是生产引擎**；Hermes `FlowExecutor`（`hermes/flow/FlowExecutor.java:44`）仅被 gRPC `executeFlow`（控制面从不调用）与测试使用。`hermes/flow` 180 个类型中 76 个仅测试引用。
- RAG：控制面 `MultiKnowledgeConcurrentRetriever`/`CrossKbScoreCalibrator`/`kmc.service.rag.*` 与 Hermes `hermes/rag`、`hermes/search` 并存；Hermes `rag` 33 类型中 22 仅测试引用，生产主链未走 Hermes RAG。
- 工具：控制面 flow `ToolNodeBO` → Hermes `McpToolAdapter`；聊天路径 → Hermes `ToolCallbackResolver`（bean 名）；`qknow-mcp` 独立实现层（79 文件）未装配进两者。

## 4. 死代码扫描（启发式，脚本见下）

对 `qknow-hermes-core/src/main/java` 793 个类型，统计全仓 Java 引用（排除自身声明文件、排除 `src/test`、排除 `/benchmark/`、`/daemon/`）：
- 有生产引用的类型 374（47%）
- 仅 benchmark/daemon 引用 32
- 仅测试引用 292（37%）
- 全仓零引用 95（12%）
> 口径说明：Python 脚本按简单类名 token 匹配，属启发式；`src/test` 含 `tests/` 与各模块 `src/test`；`/benchmark/`、`/daemon/` 视为非生产。原始脚本输出见本次会话记录。

包级外部 import 计数（全仓 main，排除 tests）：
- `agent.swarm` 0、`agent.cognitive` 0、`agent.consensus` 0、`agent.intent` 0、`cognitive(**)` 0、`transaction` 0、`workflow` 0、`flow.engine` 0、`cost` 0、`trace` 0、`observability` 0（后者被全限定名引用，统计漏计）
- `planner` 3、`debate` 3、`hitl` 3、`checkpoint` 2、`guard` 5、`governor` 2、`blackboard` 2、`speculative` 10（其中多数来自 benchmark/daemon/同包）
- 结论：`AgentRegistry`/`SupervisorAgent`/`WorkerAgent`/`ReflectiveAgent` 无生产实例化（`AgentRegistry.java` 无 `@Component`，全仓无 `new AgentRegistry`）。

## 5. 待 Lead 继续核查
- [ ] 记忆子系统：短期 Redis 读写一致性、长期记忆 consolidation 调度、跨租户 scope
- [ ] SSE 控制面：`KbChatController` 流式资源释放、断线检测
- [ ] 控制面 KbFlowServiceImpl 的 HITL 恢复与 checkpoint 竞态（等 flow-engine-auditor 交叉）
- [ ] 依赖安全：Spring AI / Alibaba / fastjson2 / hutool 版本 CVE 面（等 platform-infra-auditor）
- [ ] 与行业对标报告交叉（等 industry-benchmarker 已交付）

## 6. 交叉验证记录（Lead 亲自复核）

| 队友结论 | Lead 复核 | 结论 |
|---|---|---|
| platform P0：SSE 缓存命中在无订阅者时丢帧（Sinks.multicast） | 用 Reactor 3.7.13 写最小复现：`Sinks.many().multicast().onBackpressureBuffer()` 在**无订阅者时 tryEmitNext/tryEmitComplete 均会缓冲**，首个订阅者到达后投递（received=1, done） | **否定**。缓存命中不丢帧；真正问题是取消不传播（我 L-P0-3）与 tryEmitNext 返回值未检查（P2） |
| platform P0：TOKEN_SECRET 缺失致启动失败 | `backend/logs/error.log:84` 实锤 `PlaceholderResolutionException: Could not resolve placeholder 'TOKEN_SECRET'`；`application.yml` token.secret 引用该变量 | **成立** |
| platform P0：API Key 明文 | `AiApiKeyDO.java:40` 无 TypeHandler；`EncryptTypeHandler` 全仓仅类定义 0 使用；`AiApiKeyRespVO.java:37` 含 apiKey | **成立**（需再看序列化是否脱敏，但 DO 无加密可确认） |
| platform P0：租户隔离为常量桩 | `BaseController.java:215-217` 硬编码 `return 1001L`；`getWorkSpaceId()` 全仓 50 处使用；`DataScopeInterceptor` 仅测试引用，未注册到 MybatisPlusConfig | **成立且范围更大**（所有写入 workspace 的多租户 API 全落到 1001） |
| platform P0：/internal/api 任意 JWT 可调 | `SecurityConfig.java:119-150` 无 /internal 规则 → 落到 `anyRequest().authenticated()`；`KbInternalApiController.java:37` recall 仅按 knowledgeId 检索无租户/归属校验 | **成立**（横向越权数据面） |
| platform P1：硬编码 HMAC 密钥 | `SecurityConfig.java:155` 字面量 `qknow-flyflow-secret-key-2026` | **成立** |
| mcp P0：语义工具路由恒 null 向量 | `McpToolAdapter.java:155` `indexTool(..., null)`；`McpToolSemanticRetriever` 空向量表返回全量 | **成立** |
| mcp P0：qknow-mcp 未装配 | 全仓无 `META-INF/spring/...AutoConfiguration.imports`；控制面/ Hermes 均未依赖 qknow-mcp-server | **成立**；但注意 `McpToolAdapter`（hermes.tool.mcp）由 `HermesConfiguration` 在控制面注册为 Bean，flow ToolNode 会调用它 |
| mcp P0：沙箱非真隔离 | `ZeroTrustSandboxRuntime` 为同 JVM 线程；stdio `putAll(environment)` | **成立** |
| mcp P1：HITL 审批可绕过（工作流凭变量 approvalApproved） | `ToolNodeBO.java:100-118` 仅凭 context 变量放行，审批结果未绑定一次性 token/工单 ID | **成立** |

## 7. 新增 Lead 发现（队友报告未覆盖）

1. **控制面启动即拉起 GitHub MCP 子进程**：`HermesConfiguration.java:30-34` 注册 `McpToolAdapter` → `McpToolAdapter.java:81-86` `@PostConstruct initializeGithubServer()`，在 shell 已 source `.env` 时执行 `npx -y @modelcontextprotocol/server-github`（`McpToolAdapter.java:181-187`），无超时、无版本固定、使用个人 PAT（`.env` 内 GITHUB_PERSONAL_ACCESS_TOKEN）。P1（启动稳定性+凭据暴露面）。
2. **Hermes Dockerfile 用 JDK17 运行 Java21 产物**：`qknow-hermes-starter/docker/Dockerfile:1` `eclipse-temurin:17-jre`，父 POM `java.version=21`；且 298MB `hermes.jar` 未跟踪地停留在 docker 目录。P0（部署即崩）。
3. **控制面 `KbFlowServiceImpl` 是生产 DAG 引擎，Hermes `FlowExecutor` 是无人调用的第二引擎**（`KbFlowServiceImpl.java:295`；`FlowExecutor` 仅 gRPC `executeFlow` 与测试引用，控制面从不调用该 gRPC）。
4. **工具 code 与 Bean 名不一致**：`deploy/docker/database/mysql/init/init.sql:2020-2032` seed `kb_tool_method.code` 为 `weather_query/weather_max_airTemperature/...`；Hermes Bean 为 `weatherQuery/httpRequest/webSearch/...`，仅 `get_current_time` 命中。聊天的 `toolMethodIds` → `ToolCallbackResolver`（`ToolCallbackResolverConfig.java:32-63`）按 bean 名解析 → 配置工具大概率静默不可用（需运行验证框架行为）。
5. **控制面工具节点静默失败**：`ToolNodeBO.java:120-152` 无论 adapter 是否为空/工具是否存在，均写 `status=SUCCESS`（错误以 JSON 字符串塞进 result）。工作流"成功"不可信。
6. **SSE 取消链三层断裂**：`KbConversationController.java:123-182` 手动 `subscribe` 后 `doOnCancel` 只置 `clientConnected=false`，不 dispose 上游 → `HermesGrpcClient.onDispose` 不触发 → Hermes `GrpcReactorBridge` 不 dispose → 内层 ReAct 即使 dispose 也不级联。客户端断开后 LLM 继续烧 token 至 120s deadline。
7. **`MemoryManager.onConversationEnd` 语义缺陷**：`MemoryManager.java:44-66` 在 `summarize(sessionId,5)` 未触发（消息≤5）时，`context.get(0)` 是**最旧一条原始消息**，被当作"会话摘要"写入长期向量库；空摘要被 `LongTermMemory.store` 以 blank 过滤。P2 记忆污染。
8. **记忆 embedding 硬编码且与 RAG 不同源**：`MemoryConfiguration.java:36-42` 使用 `text-embedding-v2` + `TONGYI_API_KEY`（无默认值），与控制面 DB 配置的 Embedding 模型（`AiModelApiServiceImpl.getEmbeddingModel`）双轨；Hermes Docker 部署缺该变量即启动失败。
