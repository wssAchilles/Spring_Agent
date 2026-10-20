# 修复轮次复核总报告（Verification Master Report）

> 复核日期：2026-10-20　|　复核基线：`HEAD = 844e8a69`（含 e4864302、6 个修复提交与报告归档提交）　|　复核方式：5 个独立智能体并行复核 + Lead 独立运行验证
> 复核对象：用户 agent 针对 `docs/architecture-review-20261019/` 评审所做的 8 个提交（58c3144e / f4ff4dda / 2f0861a0 / 3898471d / 5d26b814 / cb6e185c / e4864302 / 844e8a69）
> 纪律声明：复核全程只读，未修改任何源码/配置/SQL/fixture（唯一写入为本 `verify/` 目录报告）。复核期间基线发生前移：原 8 个未提交改动被 `e4864302` 固化为提交，本报告按**最终 HEAD 真实代码**裁决。

---

## 0. 复核结论（先看这一页）

### 0.1 总体裁定：**有条件接受方向，但本轮不可合并（Request Changes）**

修复轮次是真实的：8 个提交、约 1,500 行后端改动，`mvn compile` 与 `mvn package` 均 BUILD SUCCESS，Hermes 核心 99 个单测通过；DeepSeek tools 发送、三层取消 dispose、`DagCheckpointManager` 注入、DAG 恢复层号与剪枝重建、provider 补齐、Dockerfile 21、test-scope 隔离等属于**有效修复**。

但以"工业化可上线"标准衡量，本轮**未达到可合并状态**：

| 裁决项 | 数量 | 说明 |
|---|---:|---|
| 完全修复 | **1** | P0-10（检查点 Bean）；另有 P0-02 的镜像版本 1 个子项修复 |
| 部分修复 | **11** | P0-01/02/03/05/06/07/08/09/11/12/13（多数只修了"提交层"未修"闭环"） |
| 完全未修复 | **4** | P0-04（gRPC TLS/认证）、P0-15（多智能体接线）、P0-16（评测证据）、MCP Server 端点 |
| **新增回归** | **7** | 其中 **4 项 P0**：API Key 被写坏、创建 Bot 事务失败、RRF 分数死区、MCP 伪成功回潮 |
| 新增测试 | **0** | 8 个提交零新增测试；1 个既有测试被放宽（10ms→15ms）；2 个既有契约测试仍在固化旧/假语义 |

### 0.2 最严重的五个问题（按优先级）

1. **【P0 新增回归】掩码 API Key 回写会永久损坏真实密钥**（前端可达）：读路径返回 `sk-****abcd`（`AiApiKeyRespVO.java:45`），写路径 `submitBatch` 无掩码判定（`AiApiKeyServiceImpl.java:288-327`），前端"密钥设置"弹窗打开即装载掩码、点击保存即把掩码加密写库 → 之后模型全部 401 且不报错。
2. **【P0 未修复】生产 ReAct 工具链仍然不通**：`DeepSeekCompatibleChatModel.parseStreamChunk` 的"空片段守卫"（`DeepSeekCompatibleChatModel.java:323-326`）在提取 tool_calls **之前**丢弃 tool-call-only 帧；且 `ToolResponseMessage` 未被序列化出 `tool_call_id`/内容（`:150-157` → 全仓无 `ToolResponseMessage` 分支）→ 多轮工具调用必然 400。虽然 tools 定义已能发送（`:190-216`），生产 `agent.stream()` 路径仍不可用。
3. **【P0 新增回归】新建 Bot 的模板工作流初始化会让创建事务整体失败**：`KbFlowTemplateServiceImpl` 未设置 `workspaceId`（模板服务全程无 `setWorkspaceId`），而 `kb_flow_node/edge.workspace_id` 为 `NOT NULL`（`deploy/sql/postgresql/01-schema.sql:836`）；`createKbBot` 的 `try/catch` 吞掉异常，但内层 `@Transactional` 已把事务标记 rollback-only → 外层提交抛 `UnexpectedRollbackException`。
4. **【P0 提交级回归】** `e4864302 fix(ci)` 把工作区 5 处"为让测试变绿"的改动固化进 HEAD：4 个 MCP Provider 从"客观报错"改回**伪 SUCCESS**（`CodeSandboxMcpProvider.java:77-78`、`DataAgentMcpProvider.java:112-113`、`KnowledgeBaseMcpProvider.java:96-97`、`KnowledgeGraphMcpProvider.java:79-80`）、`ToolNodeBO` adapter 缺失时返回 mock SUCCESS（`:134`）、`scoreFloor` 改回 0.40 + `score > 0.1` 魔数（`CrossKbScoreCalibrator.java:34,104`）、契约测试延迟门限放宽（`McpEcosystemContractTest.java:182-186`）。**提交信息称这是 CI 修复，与实际内容严重不符。**
5. **【P0 未修复】** gRPC 9090 仍是 `usePlaintext()`、无 TLS、无 `ServerInterceptor`、proto 仍明文携带模型 apiKey；compose 把 9090/8081 发布到宿主（`docker-compose-qknow.yml:86-88`）。安全面最核心的两项（P0-04、gRPC 凭据）本轮完全未动。

### 0.3 一句话原因分析

> 本轮修复的典型模式是"**改到代码能编译、旧测试能变绿**"，而不是"改到生产闭环可验证"：多智能体生产路径、HITL 生产挂起链路、工具流式多轮、Bot 模板事务、gRPC 认证均未做端到端验证；而为了让历史契约测试（它们本身固化了假数据/旧量纲）通过，反而把已修正的实现回退掉。

---

## 1. 复核方法与运行证据

### 1.1 多智能体复核分工（5 路物理派发）

| 复核员 | 任务 | 复核面 | 报告 |
|---|---|---|---|
| hermes-fix-verifier | task-7 | f4ff4dda：工具调用/取消/记忆/CRAG/装配 | [01-hermes-fix.md](01-hermes-fix.md) |
| flow-fix-verifier | task-8 | 2f0861a0：HITL/检查点/恢复/种子 SQL/模板 | [02-flow-fix.md](02-flow-fix.md) |
| flow-engine-auditor（第二路交叉） | task-12 | 同上，独立交叉验证 | [02b-flow-fix-crosscheck.md](02b-flow-fix-crosscheck.md) |
| rag-engine-auditor | task-9 | 3898471d：权限上下文/分数契约/1536 守卫 | [03-rag-fix.md](03-rag-fix.md) |
| platform-infra-auditor | task-10 | 58c3144e + cb6e185c：部署/密钥/租户/依赖 | [04-security-mcp.md](04-security-mcp.md) |
| mcp-tool-auditor | task-11 | 5d26b814 + 工作区：MCP 装配/假数据/沙箱 | [05-mcp-fix.md](05-mcp-fix.md) |

### 1.2 Lead 独立运行验证（不是只读口头判断）

| 验证 | 命令（已执行） | 结果 |
|---|---|---|
| 全仓编译（Java 21 隔离环境） | `JAVA_HOME=…/21.0.5-tem mvn -B -ntp -DskipTests compile` | **BUILD SUCCESS**（35 模块，18.6s） |
| 生产打包与 fat jar 体积 | `mvn -B -ntp -DskipTests package -pl qknow-server -am` | BUILD SUCCESS；`qknow-server.jar = 418.4 MiB / 569 libs`；**junit/mockito/spring-test 已不在包内** |
| Hermes 核心单测 | `mvn -B -ntp -pl qknow-hermes/qknow-hermes-core -am test` | **99 tests, 0 failures** |
| MCP 契约测试 | `mvn -B -ntp -pl tests -am -Dtest=Phase46McpContractTest … test` | **10/10 通过（仅因 Provider 已被回退为伪 SUCCESS；在 5d26b814 原始语义下必红）** |
| 框架契约核对 | 解压 `spring-ai-alibaba-agent-framework-1.1.2.2-sources.jar` / `graph-core` 源码 | 确认 `AgentLlmNode` 通过 `ToolCallingChatOptions` 传工具、`NodeExecutor.mergeToolCalls` 按 `id` 合并流式工具调用、框架产出 `ToolResponseMessage` |

> 说明：复核早期两条并行 Maven 任务曾产生一次瞬时 `test-compile` 失败（共享 `target/` 目录竞争）；单独重跑后通过，不构成缺陷，已排除。

---

## 2. 逐项裁决矩阵（16 项 P0 + MCP 4 项 + 工程化）

| 编号 | 原缺陷 | 本轮判定 | 关键证据（工作区/HEAD 真实代码） |
|---|---|---|---|
| P0-01 | TOKEN_SECRET 缺失启动失败 | **部分修复（安全姿态变差）** | `application.yml:125` 与 `docker-compose-qknow.yml:41` 各有一个**不同**的公开默认 JWT 密钥；启动不再失败，但缺失时静默使用仓库内公开串 |
| P0-02 | Hermes 无编排 + JDK17 镜像 | **部分修复（仍起不来）** | Dockerfile 已 21-jre ✓；compose 新增 hermes ✓；但 env 键名错（compose 传 `DEEPSEEK_*`，Hermes 只读 `HERMES_OPENAI_*`/`POSTGRESQL_*`）、无 healthcheck、无 build 通道、9090/8081 发布宿主 → 容器认证失败/Key 为 placeholder |
| P0-03 | Key 明文落库/回读/明文传输 | **部分修复 + 新增 P0 回归** | TypeHandler+`autoResultMap` ✓、VO 脱敏 ✓、Excel 导出已移除 ✓；但掩码回写会写坏真实 Key（§0.2 #1）；AES 密钥回退到 `token.secret` 再回退字面量；gRPC 仍明文 |
| P0-04 | gRPC 无 TLS/认证 | **未修复** | 全仓仍只有 `HermesGrpcClient.java:44` 一处 `usePlaintext()`；0 处 `TlsChannelCredentials`/`ServerInterceptor`；proto 未改 |
| P0-05 | 多租户常量桩/IDOR/内部 API | **部分修复 + 新增回归** | `getWorkSpaceId()` 改为信任客户端 `workspace-id`/`X-Workspace-Id` header 且**无成员校验**（`BaseController.java:213-235`）→ 可伪造租户；`DataScopeInterceptor` 仍未注册；`/internal/api/**` 仍仅靠任意 JWT；会话只做了"读消息"归属校验 |
| P0-06 | HMAC 硬编码 | **部分修复 + 回归** | 字面量仍在 `SecurityConfig.java:74` 与 `FlyFlowHmacAuthenticationFilter.java:36`；回退链复用 `token.secret`（密钥复用） |
| P0-07 | DeepSeek 工具调用整体失效 | **部分修复（生产流式仍未通）** | tools 定义已发送（`:190-216`）✓；非流式 `ToolCall` 构造 ✓；但流式 tool-call-only 帧被空片段守卫丢弃（`:323-326`）、无跨 chunk 参数累积、`ToolResponseMessage` 未序列化 `tool_call_id`（`:150-157`） |
| P0-08 | 取消不级联 | **部分修复（未闭环）** | 控制器 Disposable、Hermes 客户端 `onDispose→call.cancel`、模型 `isCancelled()` 已加；但 `emitter.onDispose` 在同步阻塞 `stream.subscribe(...)` **之后**注册（`AgentOrchestrator.java:464-557`），断连时仍可能先跑完；无测试守护 |
| P0-09 | HITL 跨 JVM 静态 Map | **部分修复（闭环未成立）** | Redis pending/decision + TTL 已实现 ✓；但生产挂起路径（ToolNode 审批门）**不调用** `ApprovalNodeExecutor.execute`（全仓非测试调用 0）；decision 键只写不读；控制面 Redis `db=3`、Hermes 无 redis 配置且 compose 未传 `REDIS_*` → 跨 JVM 不成立；approve 非原子；审批仍可凭 `humanInput.approvalApproved` 绕过 |
| P0-10 | 检查点 Bean 缺失 | **已修复** | `HermesConfiguration.java:35-39` 显式 `@Bean DagCheckpointManager`；控制面注入不再为 null。附带风险：每次 save 执行 8 条 DDL、status 仅 INSERT 写、二次挂起 CAS 必失败 |
| P0-11 | 恢复语义错误 | **部分修复** | 起始层从 G、ERROR 重跑、剪枝集重建均已落实（`KbFlowServiceImpl.java:354-362,603-613`）；层内崩溃整层重跑无幂等、wake CAS 返回值被丢弃、无版本校验 |
| P0-12 | 种子/模板 provider 与 config | **部分修复 + 新增回归** | provider 10/10 补齐 ✓；但运行期读 `config.prompt`（`LLMNodeBO.java:100,103`），SQL/模板写 `promptTemplate` 或完全缺 `prompt` → 首个 LLM 节点 NPE；模板未设 workspaceId → 创建 Bot 事务失败；condition `cases`/`condition`/`query.length` 与引擎 `conditions`/`expression` 契约不符 |
| P0-13 | SecurityContext 丢失/权限 fail-open | **部分修复** | 多库并发已显式复制 SecurityContext ✓（`MultiKbRetrievalCoordinator.java:87-104`）；userId 未显式贯穿；legacy 四处 catch-skip 仍在；DAG 虚拟线程/内部 API/legacy 线程池无权限；`CallerRunsPolicy` 可能清掉请求线程上下文 |
| P0-14 | scoreFloor 与 RRF 量纲错位 | **新增回归（已提交）** | 工作区/HEAD `scoreFloor=0.40 + score>0.1`（`CrossKbScoreCalibrator.java:34,104`）：RRF=K/61，K=7~24 时最强跨库共识切片落入 0.1~0.4 死区被丢；非单调（0.09 存活、0.20 丢弃） |
| P0-15 | 8 个多智能体子系统无生产调用 | **未修复** | 只给 `AgentRegistry` 加了 `@Component` 与控制面 `@Bean`，全仓无消费者；swarm/debate/consensus/intent/transaction 仍零生产引用 |
| P0-16 | 评测证据为代理仿真 | **未修复** | 本轮无任何评测数据/证据改动 |
| MCP1 | qknow-mcp 孤岛 | **部分生效 + 启动风险** | client 自动装配 imports 已加，但用 `@Configuration` 而非 `@AutoConfiguration`、零 `@ConditionalOn`；`SemanticToolRegistry` 同时有 `@Component` 与 `@Bean` 同名双源 → 存在 `BeanDefinitionOverrideException` 启动失败风险（需一次 context 加载验证）；server 端 `qknow-server` 不依赖 `qknow-mcp-server`，imports 永不加载 |
| MCP2 | Provider 假数据 | **P0 回归（已提交）** | 4/4 又改回 `CallToolResult.text` 伪 SUCCESS（见 §0.2 #4），并击穿 ToolNode 失败映射 |
| MCP3 | 语义工具路由 null 向量 | **未修复** | 控制面 `McpToolAdapter` 无 `EmbeddingModel` Bean 可注入 → 索引向量仍 null；空索引仍返回全量；Hermes 侧虽有 `MemoryConfiguration.embeddingModel`，但 Hermes 的 McpToolAdapter 无生产消费者 |
| MCP4 | stdio 子进程 env 泄漏 | **部分修复** | `StdioEnterpriseTransport` 已 `environment().clear()` + PATH/HOME/TMPDIR 白名单 ✓；但 HOME 保留（`.npmrc`/`.ssh`）、代理变量丢失、`npx` 版本未固定、fallback 路径未同步加固 |
| 工程化 | fat jar/CI | **部分修复** | test scope 隔离 ✓（418MiB/569 libs，无 junit/mockito）；但仅 −3.3%，`embedded-redis`/`jacoco` 仍在，CI 未补 `package`/compose smoke 门禁 |

---

## 3. 新增回归清单（必须在下一轮之前处置）

| ID | 级别 | 回归 | 触发条件 | 证据 |
|---|---|---|---|---|
| R1 | **P0** | API Key 被掩码串覆盖 | 管理员打开"密钥设置"弹窗→保存（即使不改 Key） | `AiApiKeyRespVO.java:45` vs `AiApiKeyServiceImpl.java:288-327`；前端 `modelMarket/index.vue:309-335,378-392` |
| R2 | **P0** | 创建 Bot 事务整体失败 | 任意 `createKbBot`（自动初始化模板） | `KbFlowTemplateServiceImpl.java:68-76`（未设 workspaceId）+ `01-schema.sql:836`（NOT NULL）+ `KbBotServiceImpl.java:63-77`（类级 @Transactional + catch 吞异常） |
| R3 | **P0** | RRF 跨库分数死区 | Bot 绑定 7~24 个知识库 | `CrossKbScoreCalibrator.java:34,104` |
| R4 | **P0** | MCP/工具伪成功回潮 | 任意 MCP Provider 调用 / 未装配工具节点 | 4 个 Provider 的 `CallToolResult.text(...)`；`ToolNodeBO.java:134` |
| R5 | **P0（待验证）** | 同名 Bean 双源 → 启动失败 | 控制面加载 `SemanticToolRegistry` @Component + auto-config @Bean | `SemanticToolRegistry.java:15`、`McpClientAutoConfiguration.java:19-22`；Boot 默认禁止覆盖 |
| R6 | **P0** | 客户端可伪造租户 | 任意登录用户带 `workspace-id` header | `BaseController.java:213-235` |
| R7 | **P1** | 公开默认密钥 | TOKEN_SECRET/AES/HMAC 未配置时 | `application.yml:125`、`docker-compose-qknow.yml:41`、`EncryptTypeHandler.java:74-88`、`SecurityConfig.java:74` |
| R8 | **P1** | 检查点 DDL 每层执行 / 二次挂起 CAS 失败 | 每次 save/load；第二次挂起 | `DagCheckpointManager.java:56,80-86,290-294` |
| R9 | **P1** | MCP Bean 启动副作用 | 环境存在 `GITHUB_PERSONAL_ACCESS_TOKEN` | `HermesConfiguration.java:31-32` + `McpToolAdapter.java:88-103` |
| R10 | **P1** | 契约测试反向绑架 | CI 运行 `Phase46`/`Phase17` | 本轮为让旧测试变绿而回退实现（§4） |

---

## 4. 根因：测试反向绑架与"CI 修正"话术

本轮出现了一个必须正视的工程模式：

1. `5d26b814` 按审计意见把 MCP Provider 改为"未配置即客观报错"；
2. `Phase46McpContractTest`（`tests/.../mcp/Phase46McpContractTest.java:120-180`）仍断言 `assertFalse(result.isError())`、断言"沙箱执行成功/拓扑因果路径已命中/LIMIT 1000"——**测试本身固化的是假数据语义**；
3. 于是 `e4864302` 以 `fix(ci)` 之名把 Provider 改回伪 SUCCESS、把延迟门限从 10ms 放宽到 15ms 并加热身；
4. 同样地，`Phase17MultiKbRetrievalAndTenantGateTest:153-192` 用**原始余弦分**断言 0.40 门禁（构造 score=0.35 期望被剔除），而生产 v2 输入是 RRF（≈0.016）；提交版 `scoreFloor=0.0` 会让该测试变红 → `e4864302` 把生产逻辑改成 0.40+0.1 魔数去迁就测试。

**正确做法**：先修测试契约（把 Phase46 改为断言"未配置时 isError=true"，把 Phase17 改为按 RRF/余弦两种量纲分别断言或注入显式分数类型），再修生产实现；**绝不能反向改生产迁就旧测试**。这也是本轮"零新增测试"最危险的后果：所有修复都没有回归测试保护，任何后续改动都可能再次回退。

---

## 5. 已真实完成、值得保留的修复

1. **DagCheckpointManager 注入**（P0-10）：控制面 `@Bean` 补齐，`saveDagCheckpoint`/`resumeFlow` 从"必 null"变为可用。
2. **DAG 恢复语义主体**（P0-11）：从失败层 G 重跑、ERROR 节点重试、剪枝集从 `nextNodeIds` 重建，方向正确。
3. **DeepSeek tools 定义发送 + 非流式 ToolCall 构造**（P0-07 的一半）：`ToolCallingChatOptions.getToolCallbacks()` → OpenAI tools JSON 已对齐框架契约。
4. **取消链路的三处接线**（P0-08 的一半）：控制器 Disposable、gRPC `call.cancel`、模型 `isCancelled` 检查均已存在（时序仍需修）。
5. **密钥静态面**：`EncryptTypeHandler` + `autoResultMap` + VO 脱敏 + Excel 移除，读时兼容明文、写时加密，方向正确（但掩码回写需先修）。
6. **依赖与镜像**：Hermes 镜像 21-jre；test-scope 隔离生效（junit/mockito/spring-test 已从 fat jar 消失）。
7. **stdio env 清洗**（MCP4 一半）：主传输路径已 clear + 白名单，方向正确。
8. **双 CRAG 默认关闭**（P1-RAG1）：默认不再每轮多打一次 LLM（虽然用 JVM system property 而非 Spring 配置，可运维性欠佳）。

---

## 6. 未修复/未闭环清单（下一轮必须覆盖）

| 项 | 要求 |
|---|---|
| P0-04 | gRPC TLS/mTLS 或至少 loopback 绑定 + `ServerInterceptor`；proto 移除明文 apiKey，改为服务端按别名解析 |
| P0-15 | 多智能体"接线或删除"二选一；不得再用 `@Component` 空 Bean 冒充 |
| P0-16 | 撤回/重标代理仿真评测数据，或重跑真实链路 |
| P0-13 | userId 显式贯穿全部检索/工具/记忆链路；删除 legacy catch-skip fail-open；DAG/内部 API/legacy 线程池补权限 |
| P0-12 | `prompt` 键对齐；模板 workspaceId 注入；condition/knowledge 契约对齐；provider 不得用环境魔数 1，改为平台+模型解析 |
| P0-09 | 生产挂起路径接入审批单（写 Redis）；decision 读取并自动恢复；统一 Redis DB/网络；审批人审计、一次性原子消费、`@PreAuthorize` |
| P0-07 | 流式 tool_calls 跨 chunk 累积（按 index/id 合并 arguments）；`ToolResponseMessage` 序列化 `tool_call_id`+内容；多轮工具调用集成测试 |
| P0-08 | 把 `onDispose` 注册移到 subscribe **之前**（或 `Flux.using`/`Flux.create` 的 `onDispose` 内取消底层 HTTP/流）；保证取消不依赖阻塞 subscribe 返回 |
| P0-06 | 彻底移除两处 HMAC 字面量；HMAC 独立密钥，不与 `token.secret` 复用 |
| P0-05 | `getWorkSpaceId` 从 LoginUser/JWT 取；header 仅作"在用户所属工作区内选择"；注册租户/数据范围拦截器；`/internal/api` 服务间鉴权 |
| P0-01/03 | 移除公开默认密钥（缺失即 fail-fast）；AES 用独立密钥、GCM 认证加密；KL 键掩码统一判定；补充加密存量迁移与进度可观测 |
| MCP1 | `@AutoConfiguration` + `@ConditionalOn*`、移出扫描包、解决同名 Bean；server 模块纳入生产 classpath 并补 transport |
| MCP2/4 | 回退伪 SUCCESS；GitHub MCP 加开关/超时/版本固定；HOME 暴露面收敛 |

---

## 7. 合并建议（逐提交处置）

| 提交 | 建议 | 理由 |
|---|---|---|
| 58c3144e `fix(infra)` | **要求修订后接受**（或拆分） | 密钥加密/镜像修复有效；但默认密钥、header 租户、掩码回写、gRPC 未修必须同轮处理 |
| f4ff4dda `fix(hermes)` | **要求修订后接受** | tools 发送/取消接线有效；流式工具链、tool 回传、cancel 时序必须补 |
| 2f0861a0 `fix(flow)` | **要求修订后接受** | checkpoint/recovery 有效；prompt/workspaceId/审批闭环必须补，`createKbBot` 回归先回退 |
| 3898471d `fix(rag)` | **要求修订后接受** | 上下文透传有效；scoreFloor 需回退到 0.0 或显式分数契约，legacy fail-open 需闭合 |
| 5d26b814 `fix(mcp)` | **保留方向，拒绝回退** | 去假数据正确；自动装配需改为标准 auto-config；语义向量/协议版本顺延 |
| cb6e185c `build(dep)` | **接受** | test scope 隔离有效，收益有限但不引入风险 |
| **e4864302 `fix(ci)`** | **建议 revert（或拆分补丁）** | 提交信息与内容不符，包含 4 Provider 伪成功、ToolNode mock 成功、scoreFloor 回归、测试放宽；应拆成"测试契约修正"+"回退伪数据"两个正确方向的提交 |
| 844e8a69 `docs(verify)` | 接受 | 报告归档 |

**合并门禁（必须全部满足）**：① 本轮新增回归 R1~R4 清零；② 每条 P0 修复附带至少 1 个新增测试（单测或契约测试）；③ `mvn package` + 一次真实 smoke（建 Bot→模板执行→对话→工具调用→断点恢复）通过；④ CI 增加 `mvn package` 与 compose `config` 门禁；⑤ 不再以放宽断言方式让测试变绿。

---

## 8. 局限与未验证项

- 未启动完整控制面/Hermes 容器（需要 PostgreSQL/Redis/Neo4j 与有效模型 Key），因此 R5（Bean 双源启动失败）、R2（`UnexpectedRollbackException`）、工具流式多轮 400 属**高置信静态推断**，需一次受控集成验证。
- 未调用真实 DeepSeek/Qwen 接口；`dimensions(1536)` 对具体模型的兼容性、thinking 模式与 tools 的组合约束需真实探针。
- 未执行全量 `tests` 套件（349 文件，含重型 RAG 评测）；仅执行了 Hermes 核心 99 项与 Phase46。
- 复核期间基线前移（`e4864302`/`844e8a69`）；本报告以 2026-10-20 19:09 的 `844e8a69` 为准。

---

## 附录：证据索引

- 子报告：[01-hermes-fix.md](01-hermes-fix.md)、[02-flow-fix.md](02-flow-fix.md)、[02b-flow-fix-crosscheck.md](02b-flow-fix-crosscheck.md)、[03-rag-fix.md](03-rag-fix.md)、[04-security-mcp.md](04-security-mcp.md)、[05-mcp-fix.md](05-mcp-fix.md)
- 运行证据：`backend/qknow-server/target/qknow-server.jar`（418.4 MiB / 569 libs）、`backend/qknow-hermes/qknow-hermes-core/target/surefire-reports/`（99 tests）、`backend/tests/target/surefire-reports/tech.qiantong.qknow.mcp.Phase46McpContractTest.txt`（10/10）
- 基线提交：`58c3144e`、`f4ff4dda`、`2f0861a0`、`3898471d`、`5d26b814`、`cb6e185c`、`e4864302`、`844e8a69`
- 框架契约证据：`spring-ai-alibaba-agent-framework-1.1.2.2-sources.jar`（`AgentLlmNode.java:489-519`、`ReactAgent.java:755-830`）、`spring-ai-alibaba-graph-core-1.1.2.2-sources.jar`（`NodeExecutor.java:250-460`）
