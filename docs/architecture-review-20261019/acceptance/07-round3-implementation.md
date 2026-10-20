# 第三轮实现修复报告（按第二轮验收报告落地）

> 日期：2026-10-20　|　基线：`1ff190d0` + 本轮未提交工作区改动　|　验证环境：Java 21 隔离环境（`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）
> 依据：`acceptance/00-ACCEPTANCE-MASTER.md` §5「第三轮必须完成的最小返工清单」
> 说明：本轮只改代码/测试/SQL/compose 配置，**未执行 git commit**，改动留在工作区待复核。

## 0. 修复项总览

| 编号 | 验收问题 | 本轮处置 | 主要文件 |
|---|---|---|---|
| N2 | `1ff190d0` 将 ToolNode 适配器缺失回退为伪 SUCCESS | **已修复**：恢复 fail-close；同步修正两个依赖旧语义的测试 | `ToolNodeBO.java`、`KbFlowServiceToolNodeTest.java`、`Phase154MasterContractTest.java`、`ToolNodeBOTest.java` |
| N3 | 删除 `resumeFlow` 唤醒守卫 + checkpoint 状态恒 SUSPENDED | **已修复**：按「挂起/崩溃恢复」双路径恢复守卫；保存检查点不再覆盖生命周期状态；新增 `markSuspended` 显式挂起 | `KbFlowServiceImpl.java`、`DagCheckpointManager.java`、`IKbFlowService.java` |
| N1 | RRF 死区：生产未接线 `scoreType`，0.40 门限误杀 v2 RRF | **已修复**：融合层标注 `RRF`，v2 转换默认 `RRF`，legacy 标注 `COSINE`，`RetrieveResultRespVO`/`RecallTest` 全链透传 | `RetrievalResult.java`、`CandidateFusionService.java`、`RagRerankService.java`、`KmcKnowledgeBaseServiceImpl.java`、`KmcApiServiceImpl.java`、`RetrieveResultRespVO.java` |
| R6 | `BaseController` 反射目标类不存在（死代码，多租户仍常量 1001） | **已修复（去死代码）**：改为同模块 `SecurityUtils` 真实判定超管；Header 仅超管可切换，普通用户强制基线 1001；不再虚假承诺工作区成员模型 | `BaseController.java` |
| P0-08 | 取消仍因 `subscriptionRef` 赋值晚于阻塞 `subscribe` 而不生效 | **已修复**：`AgentOrchestrator` 与 `GrpcReactorBridge` 均将订阅调度到 `boundedElastic`，使句柄在阻塞开始前可用，取消可级联 dispose | `AgentOrchestrator.java`、`GrpcReactorBridge.java` |
| P0-09 | 审批/恢复端点零鉴权、可伪造 `approvalApproved` 绕过、失败无补偿、reject 非终态 | **已修复**：审批端点加 `@PreAuthorize` + 运行实例租户校验；`resumeFlow` 清洗审批变量，审批放行只由新增 `resumeFlowWithApproval` 注入；恢复失败回滚 Redis 待办；reject 清理 checkpoint；`/dev/create` 增加开关 | `ApprovalProxyController.java`、`KbFlowController.java`、`KbFlowServiceImpl.java`、`IKbFlowService.java` |
| P0-12 | 种子的 `query.length` 条件恒真、prompt 键未对齐 | **已修复**：条件求值支持 `xxx.length` 与引号字面量、变量后缀匹配；种子 SQL `promptTemplate` 统一为 `prompt` | `ConditionNodeBO.java`、`backend/init_all_bot_workflows.sql` |
| P0-04 | gRPC 默认公开 token、可被缺省值绕过 | **已修复（token 面）**：服务端/客户端默认 token 置空并 fail-close（未配置仅回环可用）；`docker-compose` 的 `HERMES_GRPC_AUTH_TOKEN` 改为必填 `:?`。TLS/mTLS 与 proto 去 apiKey 仍为遗留项 | `HermesGrpcAuthInterceptor.java`、`HermesGrpcClient.java`、`docker-compose-qknow.yml` |
| N4 | plan-solve 复用 AgentRegistry 导致跨请求 prompt/tools/凭据污染 | **已修复**：每次任务独立构造 WorkerAgent，移除注册表复用 | `AgentOrchestrator.java` |

## 1. 新增回归测试（防再次回退）

| 测试 | 覆盖 |
|---|---|
| `tests/.../acceptance/ScoreTypeContractTest` | 融合产物必须标 `RRF`；RRF 0.066 不被 0.40 误杀；COSINE 0.35 被过滤、0.85 保留 |
| `tests/.../acceptance/ConditionExpressionContractTest` | `query.length > 20` 短/长问题分别走 fast/deep 分支，不再字典序恒真 |
| `tests/.../acceptance/WorkspaceHeaderContractTest` | 无 Header/伪造 Header 回退 1001；合法 1001 透传 |
| `tests/.../acceptance/CheckpointStatusContractTest` | 唤醒后保存不重置 SUSPENDED；二次挂起可再次 CAS 唤醒 |
| `ToolNodeBOTest#testNullAdapterMustFailClosed` | 适配器缺失必须 ERROR，禁止伪 SUCCESS |
| `KbFlowServiceToolNodeTest`（重写） | 工具节点注入 mock 适配器；熔断语义不变 |
| `Phase154MasterContractTest`（修正） | HITL 放行后使用 mock 适配器执行，不再依赖 null 适配器伪成功 |

## 2. 验证结果（实跑）

| 验证 | 命令 | 结果 |
|---|---|---|
| 全仓编译 | `JAVA_HOME=…/21.0.5-tem mvn -B -ntp -DskipTests compile` | **BUILD SUCCESS** |
| 全量后端测试 | `mvn -B -ntp -pl tests -am test` | **BUILD SUCCESS**：`qknow-tests 1876 tests / 0 fail / 8 skipped`；`qknow-module-kb-biz 35 tests / 0 fail`；`qknow-hermes-core 99 tests / 0 fail` |
| 新增/修正测试 | 上述 9 个测试类 | 全部通过（`ScoreType 3`、`Condition 1`、`Workspace 3`、`Checkpoint 1`、`ToolNodeBO 4`、`Phase154 12`、`DeepSeek 9`、`Phase46 10`、`Phase17 6`） |

> 说明：期间出现过一次 `qknow-module-kg-biz` 找不到 `kg-api` 包的瞬时编译失败，串行重跑即恢复，判定为构建目录竞争/环境抖动，非本次改动引入。

## 3. 补充批（同日第二段继续）

在第一批 P0 修复后，继续完成以下遗留项：

| 项 | 处置 | 文件 |
|---|---|---|
| P0-09 审批强证明 | ToolNode 不再只信任变量：要求存在 `approvalKey` 且 Redis 中有服务端写入的 `APPROVED` 决策凭据；无 Spring 上下文（纯单测）才回退变量兼容路径 | `ToolNodeBO.java`、`SpringUtils.java`（新增 `getApplicationContext()`）、`Phase154MasterContractTest` |
| P0-04 TLS/mTLS | 服务端：`grpc.server.security.*` 配置（证书链/私钥/信任库/client-auth，默认关闭）；客户端：`NettyChannelBuilder + GrpcSslContexts` 支持信任证书与 mTLS 客户端证书，TLS 开启失败即 fail-fast；compose 增加 TLS 环境变量与 `./certs` 挂载说明 | `HermesGrpcClient.java`、`qknow-hermes-starter/application.yml`、`docker-compose-qknow.yml`、`deploy/docker/certs/` |
| P0-09 跨 JVM Redis 对齐 | Hermes 增加 `spring.data.redis.*`（默认 DB=3，与控制面一致），compose 传 `REDIS_HOST/PORT/PASSWORD/DATABASE` | `qknow-hermes-starter/application.yml`、compose |
| P0-02 Hermes 编排 | 修正 Hermes env 键名（`HERMES_OPENAI_API_KEY/BASE_URL`、`POSTGRESQL_*`、Redis），增加 `postgres` 健康依赖；数据源 URL 支持 `POSTGRESQL_URL` 覆盖 | compose、`application.yml` |
| 依赖瘦身 | 排除 `aliyun-java-sdk-core` 传递引入的 `org.jacoco.agent` runtime jar（生产 fat jar 不再包含） | `qknow-common/pom.xml`、`qknow-file/pom.xml` |
| CI 门禁 | 后端门禁新增 `mvn package` 与 `docker compose config`（校验必填环境变量与完整聚合 compose） | `.github/workflows/ci.yml` |

验证补充：`docker compose -f deploy/docker/docker-compose.yml config` **exit 0**；全量测试再次通过（`qknow-tests 1876 / 0 fail`、`kb-biz 35 / 0 fail`、`hermes-core 99 / 0 fail`）。注意：并行运行的外部 Maven 构建曾两次造成瞬时的"依赖包不存在"编译假失败，串行重跑即恢复；CI 单机串行不受影响。

## 4. 本轮未覆盖的遗留项（需后续迭代）

1. **P0-04 proto 去 apiKey**：TLS/鉴权已支持，但 `hermes.proto` 仍携带明文 `api_key`，最终应由控制面按别名解析、不随请求下发密钥。
2. **MCP3 控制面语义向量、MCP Server 端点**：未接线。
3. **P0-15 多智能体生产接线、P0-16 评测证据**：未处理。
4. **P0-12 模板数据质量**：`knowledgeBaseId=null`、provider=1 环境魔数仍依赖部署环境。
5. **依赖瘦身剩余项**：`embedded-redis` 仍为 compile 依赖（被 dev 配置使用），需改为 profile 隔离后再从生产包剔除。
6. 未启动控制面/Hermes 容器与真实 DeepSeek/Qwen 接口；N1/N3/P0-08/P0-09 的运行时效果尚需受控集成验证。

## 5. 建议提交信息（如确认提交）

```text
fix(core): 闭环修复验收遗留的流程、检索与安全缺陷

- 恢复 ToolNode 适配器缺失 fail-close，修正依赖伪成功的旧测试
- 接线 RRF/COSINE 得分量纲，修复跨库门禁误杀与检查点状态覆盖
- 审批与恢复端点补权限/租户校验，阻断 approvalApproved 变量绕过
- gRPC 支持 TLS/mTLS 与 token fail-close，取消链路改异步订阅以级联释放
- 修正 Hermes 编排环境变量与 Redis 对齐，排除生产包 JaCoCo，补 CI 打包/Compose 门禁
- 新增 9 条验收回归测试，全量 1876 项后端测试通过
```

## 6. 第三批（提交 b652f112 之后）

| 项 | 处置 | 文件 |
|---|---|---|
| 依赖瘦身（embedded-redis） | `EmbeddedRedisConfig` 增加 `@Profile("dev")`；`spring-boot-maven-plugin` 将 `embedded-redis` 从生产 fat jar 排除；dev/IDE 运行仍可用 | `EmbeddedRedisConfig.java`、`qknow-server/pom.xml` |
| P0-12 provider 魔数 | 移除 `provider=1` 硬编码：新增 `IAiModelApiService.getChatModelByPlatform(platform, model)`，按平台查当前环境可用 Key；LLM 节点未配置 provider 时动态解析；种子 SQL 20 处 `provider:1` 改为 `platform:"DeepSeek"`；模板/节点转换同步 | `IAiModelApiService.java`、`AiModelApiServiceImpl.java`、`LLMNodeBO.java`、`KbFlowTemplateServiceImpl.java`、`KbFlowNodeConvert.java`、`init_all_bot_workflows.sql` |

验证：全量测试再次通过（1876 / 0 fail）；生产 fat jar 重新打包验证 `embedded-redis` 已被排除。
