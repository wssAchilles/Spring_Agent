# Knowledge Hub / Hermes 架构缺陷全量法医级核验决议报告

> **核验日期**：2026-10-19  
> **审计基准**：`docs/architecture-review-20261019/`（`00-MASTER-REVIEW.md` 及 6 份分项深度审计报告）  
> **审查对象**：`backend/` 真实代码资产（2734 个 Java 文件 / 390,721 行源码、配置文件、SQL 脚本、Maven 依赖字节码与评测证据资产）  
> **审查机制**：主 Agent 协调 + 5 位独立沙盒法医核验子智能体并行代码走查与实证求证（无单一进程角色扮演虚假自问自答）  
> **审查准则**：严格遵守《AGENTS.md》最高工程法典（Java 21 隔离环境、DeepSeek 官方规范、千问 1536 维超球面几何）

---

## 一、物理派发子智能体存证记录 (Subagent Dispatch Ledger)

| 智能体角色 (Role) | 物理 Conversation ID | 负责缺陷范围 | 运行 Transcript 存证链接 |
|---|---|---|---|
| **Hermes 认知内核与 Agent 编排缺陷法医核验员** | `7dbbbf7c-cc0b-482d-93f9-a96a2b06ff4e` | 01 报告全部缺陷：P0-07（DeepSeek 工具调用整体失效）、P0-08（三层取消断裂 Token 空烧）、P0-15（8 个多智能体子系统无生产调用）、双 CRAG 重复调用、记忆摘要与模型硬编码、gRPC 裸露等 | [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/7dbbbf7c-cc0b-482d-93f9-a96a2b06ff4e/.system_generated/logs/transcript.jsonl) |
| **工作流 DAG 与 HITL 检查点缺陷法医核验员** | `48a1e269-e86f-4f3a-802e-fc4a57960853` | 02 报告全部缺陷：双 DAG 引擎重复（控制面 vs Hermes）、P0-09（HITL 审批跨 JVM 静态 Map 失联）、P0-10（DagCheckpointManager 注入恒 null 导致恢复 100% 失败）、P0-11（恢复语义层号偏移与未重试）、P0-12（种子 SQL 与模板缺 provider）、工具节点静默成功、ReAct 循环滑窗推演等 | [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/48a1e269-e86f-4f3a-802e-fc4a57960853/.system_generated/logs/transcript.jsonl) |
| **RAG 知识引擎与超球面向量几何缺陷法医核验员** | `d676d17e-6a2f-4785-9ff0-321fd8ff38b2` | 03 报告全部缺陷：P0-13（异步线程丢失 SecurityContext 致 RAG v2 抛 401 回退且 legacy 权限 fail-open）、P0-14（跨库融合 RRF 分数量纲与 scoreFloor 错位致空召回）、千问 1536 维超球面单位向量守卫缺失、语义缓存失效机制、GraphRAG 与评测证据代理仿真声明等 | [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/d676d17e-6a2f-4785-9ff0-321fd8ff38b2/.system_generated/logs/transcript.jsonl) |
| **MCP 工具生态与沙箱安全缺陷法医核验员** | `94505e2a-d69f-4f34-91b9-d00de5c5e80f` | 04 报告全部缺陷：qknow-mcp 79 个类孤岛无装配、4 个 MCP Provider 硬编码伪造假数据、语义工具路由恒传 null 向量、启动期无超时拉起 GitHub MCP 子进程、沙箱非真隔离与环境变量透传泄露等 | [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/94505e2a-d69f-4f34-91b9-d00de5c5e80f/.system_generated/logs/transcript.jsonl) |
| **平台基础设施、控制面与安全多租户缺陷法医核验员** | `1e2897a9-f7e3-4f8a-b6f2-39c727347b69` | 05 报告与 00 报告全部缺陷：P0-01（TOKEN_SECRET 缺失致启动崩溃）、P0-02（Hermes 镜像 JDK17 跑 Java21 字节码必报版本异常）、P0-03（API Key 明文落库/传输/Excel 导出）、P0-04（gRPC 9090 无 TLS/认证）、P0-05（多租户 1001 常量桩与越权）、P0-06（硬编码 HMAC 密钥）、435MB fat jar、CI 门禁等 | [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/1e2897a9-f7e3-4f8a-b6f2-39c727347b69/.system_generated/logs/transcript.jsonl) |

---

## 二、全量缺陷法医核验裁决矩阵 (Master Verification Matrix)

| 缺陷编号 | 缺陷核心事实 | 法医核验判定 | 严重级别 | 关键物理代码与实证位置 | 核心机制与危害简述 |
|---|---|:---:|:---:|---|---|
| **P0-01** | Compose 缺 `TOKEN_SECRET` 致冷启动崩溃 | **【完全属实】** | P0 (阻断) | `docker-compose-qknow.yml:12-40`<br>`application.yml:125`<br>`backend/logs/error.log:84` | `${TOKEN_SECRET}` 强制占位无默认值，冷启动时 Spring 装配 `tokenService` 100% 崩溃。 |
| **P0-02** | Hermes 缺 Compose 服务且 Dockerfile 错用 JRE 17 | **【完全属实】** | P0 (阻断) | `Dockerfile:1` (`eclipse-temurin:17-jre`)<br>父 `pom.xml:18` (`java.version=21`) | Java 21 编译的主版本 65.0 字节码在 JRE 17 下抛 `UnsupportedClassVersionError`；Compose 无服务导致控制面 DNS 解析失败。 |
| **P0-03** | API Key 明文落库、响应回显、Excel 导出与 gRPC 裸传 | **【完全属实】** | P0 (高危) | `AiApiKeyDO.java:40`<br>`EncryptTypeHandler.java` (0引用)<br>`AiApiKeyRespVO.java:37` (`@Excel`)<br>`HermesGrpcClient.java:44` (`.usePlaintext()`) | DeepSeek 与千问凭证全链路明文无防御，数据库备份、HTTP 响应、Excel 导出与内网抓包均可直接窃取。 |
| **P0-04** | Hermes gRPC 9090 端口无 TLS 与认证拦截器 | **【完全属实】** | P0 (高危) | `HermesGrpcService.java:23`<br>`application.yml:4-8`<br>全仓 `ServerInterceptor` 引用数为 0 | 0.0.0.0 裸端口监听，同网任意进程无需凭据可调用 Agent、盗用模型额度。 |
| **P0-05** | 多租户为常量桩（`1001L`）、会话越权与内部 API 越权 | **【完全属实】** | P0 (高危) | `BaseController.java:217` (`return 1001L;`)<br>`DataScopeInterceptor.java` (未注册)<br>`KbChatMessageServiceImpl.java:20-25`<br>`KbInternalApiController.java:37` | 33 个控制器 44 处调用 `getWorkSpaceId()` 全落到 1001；数据范围拦截器未注册；会话与内部检索无归属校验，任意有效 JWT 可水平越权检索他人知识库。 |
| **P0-06** | 源码硬编码 HMAC 共享密钥 | **【完全属实】** | P0 (高危) | `SecurityConfig.java:155`<br>`FlyFlowHmacAuthenticationFilter.java:36` | 字面量 `"qknow-flyflow-secret-key-2026"` 硬编码，无法轮换，内网或源码泄露后签名防御失效。 |
| **P0-07** | DeepSeek 路径工具调用整体失效 | **【完全属实】** | P0 (核心) | `DeepSeekCompatibleChatModel.java:168,222,296`<br>`AssistantMessage.class` 反编译 | 仅识别 `DeepSeekChatOptions`，生产 `ToolCallingChatOptions` 被忽略；未调用 `.toolCalls()` 构造对象，框架 `hasToolCalls()` 恒为 false，ReAct 循环彻底退化为单次文本生成。 |
| **P0-08** | 取消链路三层全断，客户端断开后 Token 空烧 | **【完全属实】** | P0 (成本) | `KbConversationController.java:181`<br>`AgentOrchestrator.java:463`<br>`DeepSeekCompatibleChatModel.java:103` | 控制面 `doOnCancel` 仅置布尔位不 dispose 上游；编排层裸订阅且并发闸门不释放；传输层无 `isCancelled()` 校验，断开后持续调用 DeepSeek 跑满 120s 超时。 |
| **P0-09** | HITL 审批跨 JVM 静态 Map 失联 | **【完全属实】** | P0 (阻断) | `ApprovalNodeExecutor.java:23`<br>`ApprovalProxyController.java:20-22` | Hermes 审批单保存在其本地 JVM 静态 Map；控制面代理控制器跨进程访问其自身内存，永远查不到该单据，审批 100% 返回 404。 |
| **P0-10** | 检查点 Bean 缺失致断点恢复 100% 失败 | **【完全属实】** | P0 (阻断) | `KbFlowServiceImpl.java:74-75,511-513`<br>`DagCheckpointManager.java:1` | `DagCheckpointManager` 在 hermes 包下，被控制面 `@ComponentScan` 排除；控制面注入为 null，保存检查点空转，触发恢复时抛出 `未配置检查点管理器` 异常。 |
| **P0-11** | 恢复语义错误（跳过失败层、剪枝丢失） | **【完全属实】** | P0 (正确性) | `KbFlowServiceImpl.java:380,392,439,451,584,354` | 失败层保存为 G，恢复却从 G+1 启动，第 G 层失败节点被跳过且从未重试；剪枝集未重建导致历史剪枝分支被误执行。 |
| **P0-12** | 种子 SQL 与新建 Bot 模板的 LLM 节点缺 `provider` | **【完全属实】** | P0 (阻断) | `init_all_bot_workflows.sql:54,76`<br>`LLMNodeBO.java:49-53`<br>`KbFlowTemplateServiceImpl.java:181-189` | `LLMNodeBO` 强制校验 `provider` 非空；种子 SQL 中 `provider` 出现 0 次，模板数据未嵌入 `config` 导致解析出空对象，执行到首个 LLM 节点必报模型配置错误。 |
| **P0-13** | 异步线程丢失 SecurityContext 致 RAG v2 回退，且 legacy 权限 fail-open | **【完全属实】** | P0 (高危) | `ThreadPoolConfig.java:32-43`<br>`MultiKbRetrievalCoordinator.java:49,89,138`<br>`SecurityUtils.java:27-37`<br>`KmcKnowledgeBaseServiceImpl.java:411-413,725-735` | 线程池无 `TaskDecorator`，异步调用抛 401，被静默 catch 回退到旧管线；旧管线在 catch 后把权限过滤置空直接穿透，造成数据越权。 |
| **P0-14** | 跨库融合分数契约错位（RRF ≈0.016 撞 scoreFloor=0.40） | **【完全属实】** | P0 (阻断) | `CandidateFusionService.java:84,104`<br>`CrossKbScoreCalibrator.java:33-34,94-98` | RRF 分数上限仅约 0.016~0.065，被下游 `scoreFloor=0.40` 全量过滤丢弃，返回空上下文。算法越正常上下文越为空，底层崩溃回退反而有数据。 |
| **P0-15** | 8 个多智能体/认知/事务子系统无生产调用 | **【完全属实】** | P0 (战略) | `agent.swarm`/`agent.cognitive`/`agent.consensus`/`agent.intent`/`transaction` 等<br>`AgentRegistry.java:17` | 全仓 2296 个 main 文件中外部引用全为 0（仅 1 处探针）；`AgentRegistry` 无 `@Component` 且全仓 main 零实例化，多智能体宣称与生产严重脱节。 |
| **P0-16** | 对外评测数据来自代理仿真而非生产链路 | **【完全属实】** | P0 (证据) | `evidence_integrity_audit.json:2061-2067`<br>`eval_summary_300_v2.csv`<br>`live-ann-environment.md:1-13` | 审计自证为 `THREE_PATH_PROXY`（128-bucket 哈希模拟），无 Qwen embedding、无图遍历；评测数据显示 BM25 优于 QuadPath；真实 Live ANN 环境因误删扩展向量瘫痪。 |
| **P0-MCP1** | `qknow-mcp` 模块 79 类完全孤岛未装配 | **【完全属实】** | P0 (孤岛) | `backend/qknow-mcp/` 全 79 个 Java 类<br>全仓 `spring.factories` 命中数为 0 | 无自动装配文件，控制面未引入 `qknow-mcp-server`，核心类全仓生产引用数为 0。 |
| **P0-MCP2** | 4 个 MCP Provider 硬编码假数据 | **【完全属实】** | P0 (造假) | `KnowledgeBaseMcpProvider.java:66`<br>`KnowledgeGraphMcpProvider.java:53`<br>`DataAgentMcpProvider.java:86`<br>`CodeSandboxMcpProvider.java:51-52` | 算术伪造余弦得分 `0.9825`；硬编码固定三元组 `[实体A]->[导致]->[实体B]`；硬编码订单数据；自证“模拟执行”固定返回 `42ms/42`。 |
| **P0-MCP3** | 语义工具路由恒传 null 向量与全量扫表退化 | **【完全属实】** | P0 (核心) | `McpToolAdapter.java:155`<br>`McpToolSemanticRetriever.java:73-78,115-124` | 注册工具时向量参数硬编码为 `null`，导致向量表为空，检索时跳过余弦计算与 TopK，直接退化为全量扫表无序返回。 |
| **P0-MCP4** | 沙箱伪隔离与宿主凭据全量透传泄露 | **【完全属实】** | P0 (高危) | `ZeroTrustSandboxRuntime.java:116-125`<br>`StdioEnterpriseTransport.java:81-84` | 零信任沙箱仅为普通平台线程；Stdio 启动未执行 `pb.environment().clear()`，宿主包含 `DEEPSEEK_API_KEY` 在内的所有机密环境变量 100% 透传给子进程。 |
| **P1-Tool1** | 启动期无超时同步拉起 GitHub MCP 子进程 | **【完全属实】** | P1 (启动) | `McpToolAdapter.java:80-86,181-188` | `@PostConstruct` 同步执行未锁定版本的 `npx -y @modelcontextprotocol/server-github`，无超时熔断，阻塞 Spring 容器启动。 |
| **P1-Tool2** | 工具节点调用失败强行标记为 SUCCESS | **【完全属实】** | P1 (正确性) | `ToolNodeBO.java:126-153` | 当 adapter 为空时拼装 `NO_ADAPTER`，但随后强行将节点状态覆盖为 `SUCCESS`，伪造成功凭单。 |
| **P1-RAG1** | 双 CRAG 重复无条件调用 LLM | **【完全属实】** | P1 (性能) | `AgentOrchestrator.java:270-279`<br>`RetrievalEvaluator.java:58` | 控制面做完 CRAG 后 Hermes 每轮再无条件发起同步阻塞 LLM 评估，结果仅记日志，白白消耗 1~3s TTFT 与 Token。 |
| **P1-RAG2** | 语义缓存正则黑名单粗暴且键缺权限维度 | **【完全属实】** | P1 (质量) | `KbAgentConfigServiceImpl.java:459-471`<br>`SemanticCacheService.java:114-125` | 粗暴正则命中“抱歉”即拒绝入缓存；缓存查找缺权限哈希与文档版本号，存在越权读取风险。 |
| **P1-Mem** | 记忆将最旧原始消息冒充摘要且环境变量阻断启动 | **【完全属实】** | P1 (数据) | `MemoryManager.java:54`<br>`MemoryConfiguration.java:27,31` | 消息 `<=5` 条时取最旧一条原始消息（如“你好”）存入长期向量库；`TONGYI_API_KEY` 无默认值缺环境变量阻断启动。 |
| **P1-Build** | Fat Jar 体积达到 434MB 且 CI 缺打包冒烟门禁 | **【完全属实】** | P1 (工程) | `qknow-server.jar` 物理体积 434 MiB<br>`.github/workflows/ci.yml:105-111` | CI 只跑 `tests -am test`，缺 `mvn package` 与容器启动冒烟探测，导致部署级缺陷无法被拦截。 |
| **SSE证伪** | SSE 语义缓存命中丢帧判定被推翻 | **【证伪属实】<br>(原P0不成立)** | `KbConversationController.java:123-181`<br>Reactor `Sinks.many().multicast().onBackpressureBuffer()` | 经单测实证，背压缓冲队列在首个订阅者前自动缓存帧，不会丢帧；真实缺陷为取消不级联（P0-08）与 `tryEmitNext` 返回值未检查。 |
| **滑窗核验** | ReAct 循环滑窗 W+1 判定分析 | **【部分属实】<br>(严格守卫无误)** | `ReActStrictWindowCycleGuard.java:131-150`<br>`SwarmLoopGuard.java:71-83`<br>`ToolNodeBO.java:32` | `ReActStrictWindowCycleGuard` 严格实现了先淘汰后判定，不存在 W+1 误杀；但先入队后淘汰反模式残留在 `SwarmLoopGuard` 中，且 `ToolNodeBO` 每次新建实例导致工作流守卫无状态。 |

---

## 三、四大核心根因溯源分析 (Root Cause Synthesis)

经过 5 个维度的代码取证，整个系统之所以呈现出“理论资产极为宏大、生产路径处处阻断”的反差，其根因高度收敛于以下四个工程与管理失误：

1. **“阶段式自证”与真实装配脱节**：
   在历史研发过程中，多个研发阶段以“在 `backend/tests/` 下编写独立的单测并通过”作为交付验收标准。然而测试由于未启动完整的 Spring Boot 上下文，未能发现 `QKnowApplication` 显式排除了 `hermes.*` 扫描，导致依赖 Hermes 的检查点管理器、MCP 适配器、HITL 审批单在控制面全部为 `null` 或失效。
2. **双轨架构与边界撕裂**：
   控制面（`qknow-module-kb`）与认知面（`qknow-hermes`）各长出了一套 DAG 引擎（`KbFlowServiceImpl` vs `FlowExecutor`）、一套 RAG 检索（`kmc` vs `hermes/search`）、一套工具适配层。控制面实际上从未调用 Hermes 的图执行引擎，导致 Hermes 内部大量高级算法沦为仅单测可达的死代码。
3. **协议契约与数据量纲断层**：
   系统各个模块之间缺乏严格的类型契约与量纲规范：
   - 生成侧：DeepSeek 客户端只认私有 `DeepSeekChatOptions`，框架的标准 `ToolCallingChatOptions` 无法通信，工具调用全部断裂；
   - 检索侧：上游单库融合输出无量纲的 RRF 分数（约 0.016），下游跨库校准器却施加了绝对余弦相似度门禁（`0.40`），形成致命数据断崖；
   - 传输侧：Stido 进程启动直接 `putAll(environment)`，忽略了宿主凭证隔离。
4. **CI 门禁与环境烟雾测试缺失**：
   CI 流水线仅执行了部分单元测试，从未执行 `mvn package`，也未拉起 Docker Compose 验证微服务联通性。导致 `TOKEN_SECRET` 缺失、Dockerfile 错用 JRE 17 跑 Java 21 字节码、种子 SQL 缺少 `provider` 等“起机即崩”的低级缺陷长期潜伏未被拦截。

---

## 四、止血与整改四波次路线图 (Implementation Roadmap)

针对上述核验属实的全部缺陷，严格遵循 AGENTS.md 规定的最小工程修改原则，建议分为四个波次进行推进：

### Wave 0：紧急止血（24–72 小时，约 6–8 人日，阻断项归零）
1. **容器编排脱险**：
   - 在 `docker-compose-qknow.yml` 注入 `TOKEN_SECRET` 并给 `application.yml` 设置安全保底默认值；
   - 将 Hermes Dockerfile 基础镜像改为 `eclipse-temurin:21-jre-jammy`，并在 Compose 中加入 `hermes` 容器服务编排。
2. **凭据安全加固**：
   - 给 `AiApiKeyDO.apiKey` 挂载 `@TableField(typeHandler = EncryptTypeHandler.class)`；
   - 在 `AiApiKeyRespVO` 中移除 `@Excel` 导出，返回前端时做脱敏掩码；
   - 将 Hermes gRPC 限制为内网通信，并移除 RPC 请求体中的明文 API Key。
3. **DeepSeek 工具调用链修复**：
   - 改造 `DeepSeekCompatibleChatModel.java`：模式匹配 `ToolCallingChatOptions` 提取 tools schema；解析响应体 `tool_calls` 并构造 `AssistantMessage.ToolCall` 列表注入 `AssistantMessage`。
4. **流式取消级联**：
   - 控制面保存 `Disposable` 并在 `doOnCancel` 中执行 `dispose()`；
   - 编排层向 `emitter.onDispose` 注册 Disposable 并释放并发闸门；
   - 传输层循环增加 `sink.isCancelled()` 校验，断开后立即关闭连接。
5. **恢复 DAG 检查点与模板可用性**：
   - 在控制面配置类中显式以 `@Bean` 注册 `DagCheckpointManager`；
   - 修改 `KbFlowServiceImpl.java:584` 恢复起始层号为当前层 G，重试未成功节点，反向重建剪枝集；
   - 批量更新 `init_all_bot_workflows.sql` 补充 `"provider": 1`；修复模板生成逻辑，消除新建 Bot 首节点报错。
6. **解除 RAG 误杀与权限穿透**：
   - `CrossKbScoreCalibrator.java:34` 将 `scoreFloor` 默认值降为 `0.0D`，解除对 RRF 分数的误杀；
   - `recallTest` 与 `RagRetrievalService` 显式传递 `Long userId`，杜绝异步 ThreadLocal 丢失；删除 legacy 路径跳过权限的 fail-open 逻辑。

### Wave 1：收敛与正确性（2 周，约 20–25 人日）
1. **单一 DAG 引擎收敛**：明确控制面 `KbFlowServiceImpl` 为唯一生产工作流内核，Hermes 的 `FlowExecutor` 标记弃用；
2. **HITL 审批外置落库**：新增 `kb_flow_approval` 数据库表，替代内存静态 Map，实现审批状态跨进程持久化；
3. **多租户基线治理**：改造 `BaseController.java`，从登录用户动态解析 `workspaceId`，移除 `1001L` 硬编码；在 `MybatisPlusConfig` 中正式注册 `DataScopeInterceptor`；
4. **死代码物理隔离**：将 8 个无生产调用的多智能体、事务子系统剥离至实验性模块，精简生产构建产物；
5. **CI 门禁补齐**：CI 增加 `mvn package` 与 Docker Compose 配置语法烟雾测试。

### Wave 2：工具生态、向量几何与真实评测（6 周，约 40 人日）
1. **MCP 真实向量索引**：在 `McpToolAdapter` 发现工具时，调用千问 1536 维 Embedding 生成工具向量，恢复真正的超球面语义检索；
2. **沙箱进程真实隔离**：Stdio 进程启动前显式清空环境变量（`pb.environment().clear()`），仅透传安全白名单；
3. **超球面几何强制约束**：千问客户端显式传入 `.dimensions(1536)`，并在检索与写入两端建立 $L_2$ 范数等于 1.0 的硬校验守卫；
4. **评测基准数据重构**：撤回离线代理仿真数据，在修复后的 PgVector 环境中基于真实 Qwen Embedding 与真实大模型重跑 300 题基准评测。

### Wave 3：平台化与企业对标（1 个季度）
1. 工作流状态机向 Spring AI Alibaba Graph / LangGraph4j 规范收敛；
2. 建立 OpenTelemetry GenAI 统一可观测链路与真实 Token 成本治理看板。

---
本决议报告基于 5 个独立子智能体的源码级法医核验产物整合而成，各项事实均有确凿的代码切片与运行证据支撑，决策完备，待用户明确批准后即可启动 Wave 0 实施。
