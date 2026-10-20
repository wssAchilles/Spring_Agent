# 第二轮验收总报告（Round-2 Acceptance Master Report）

> 验收日期：2026-10-20　|　验收基线：`HEAD = 1ff190d0`（`54fea59b fix(core)` + `1ff190d0 fix(ci)`，工作区 clean）
> 验收方式：5 路独立智能体复核（复用第一轮复核员，物理隔离）+ Lead 独立运行验证（编译 / 全量测试 / 目标契约测试 / 框架源码契约核对）
> 验收对象：用户 agent 针对第一轮复核报告 `verify/00-VERIFICATION-MASTER.md` 的返工提交，以及其自证报告 `verify/06-deep-fix-resolution.md`
> 纪律：只读验收，未修改任何源码/配置/SQL/fixture；唯一写入为本 `acceptance/` 目录报告。

---

## 0. 验收裁定：**不通过（Request Changes），但本轮修复质量显著优于前两轮**

### 0.1 一句话结论

> 本轮是三轮里第一次"真正按缺陷机理改代码"：R1 密钥覆盖、R4 Provider 假数据、P0-07 流式工具链、MCP 自动装配、HMAC fail-close、Bot 创建事务主因等**被真实修复**，并新增了 9 个 DeepSeek 契约测试、全量后端测试 1867 项 0 失败。但**仍不可合并**：R3 在移除 0.1 豁免后因生产未接线 `scoreType` 而恶化（多库 RAG 近乎必空）、`1ff190d0` 以 `fix(ci)` 把 ToolNode 伪成功回退并删除 resume 幂等守卫（重复执行风险）、R6 依赖一个不存在的类（多租户仍常量 1001）、P0-08 取消仍有 ref 赋值竞态、审批/恢复端点零鉴权。

### 0.2 统计

| 裁决项 | 数量 | 明细 |
|---|---:|---|
| **已修复** | **5** | R1 密钥掩码覆盖、R4a Provider 客观报错、R5 同名 Bean 双源、P0-07 流式工具链（静态闭环+单测）、P0-06 HMAC 硬编码 |
| **部分修复** | **12** | R2、R6、R7、P0-08、P0-09、P0-11、P0-12、P0-04、P0-01、MCP1（仅客户端/服务端未接）、MCP4、测试覆盖 |
| **未修复** | **5** | P0-15 多智能体、P0-16 评测证据、P0-13 RAG 权限 fail-open、MCP3 语义向量、依赖瘦身 |
| **新增回归** | **4** | N1 R3 生产 RRF 全量误杀（P0）、N2 ToolNode 伪成功回潮（P0）、N3 resume 守卫删除+status 恒 SUSPENDED（P0/P1）、N4 plan-solve worker 跨请求污染（P1） |
| **自证失实** | **2** | `06-deep-fix-resolution.md` 对 R3/R6/R4/P0-08/P0-11/P0-12 的"已彻底根除/已闭环"表述；Phase17 契约 2b 用测试内手工 `setScoreType` 自证 |

### 0.3 运行验证结果（Lead 实跑，Java 21 隔离环境）

| 验证 | 命令 | 结果 |
|---|---|---|
| 全仓编译 | `JAVA_HOME=…/21.0.5-tem mvn -B -ntp -DskipTests compile` | **BUILD SUCCESS**（35 模块，12.2s） |
| 全量后端测试 | `mvn -B -ntp -pl tests -am test` | **BUILD SUCCESS**：`qknow-tests` **1867 tests / 0 fail / 0 error / 8 skipped**；`qknow-hermes-core` **99 tests / 0 fail**（41s） |
| 本轮新增/修改契约 | `-Dtest=DeepSeekCompatibleChatModelContractTest,Phase46McpContractTest,Phase17MultiKbRetrievalAndTenantGateTest` | **9/9、10/10、6/6 全绿** |
| 框架契约核对 | sources jar：`AgentLlmNode`、`NodeExecutor.mergeToolCalls`、`ToolResponseMessage`、`Agent.doStream` 无调度器 | 证实 P0-07 静态链路成立；证实 P0-08 的 subscribe 同步阻塞推断 |

> ⚠️ 全量测试全绿不能作为"生产闭环"证据：Phase154 的 ToolNode 用例（null adapter 断言 SUCCESS）是 N2 回退的直接动因；Phase17 契约 2b 在测试内手工打 `scoreType`，绕过了生产转换链（N1）。

---

## 1. 上轮 7 项回归的验收

| ID | 上轮缺陷 | 本轮判定 | 关键证据 |
|---|---|---|---|
| R1 | API Key 掩码回写写坏真实密钥 | **已修复（核心）** | `isMaskedApiKey`（`AiApiKeyServiceImpl.java:151-153`，含 `*` 即视为掩码）；`updateAiApiKey:159-171` 与 `submitBatch:322-337` 均保留原 key；新增记录路径不守卫（正确）。残留 P2：真实 key 含 `*` → 静默不更新；清空 key → 覆盖为空 |
| R2 | 创建 Bot 模板 workspaceId NULL → 事务失败 | **部分修复** | `KbBotController:93` 注入 workspaceId；`KbFlowTemplateServiceImpl:33` 改 `REQUIRES_NEW` + `:74-76` 设 flowVO.workspaceId → 已切断 `UnexpectedRollbackException` 主因。残留：REQUIRES_NEW 看不到外层未提交 bot 行（`botMapper.selectById` 必空）、外层回滚留孤儿节点、`createKbBot` 仍吞异常、workspaceId 实际恒 1001（见 R6） |
| R3 | 跨库分数死区（0.1~0.4 误杀） | **未修复 + 新增回归（更严重）** | `RetrieveResult` 新增 `scoreType`（`RetrieveResult.java:89-92`），但**生产全仓无任何 `setScoreType`**（唯一命中 `Phase17...Test:203`）；`RetrieveResultRespVO` 无该字段、`retrievalResult2vo`（`KmcKnowledgeBaseServiceImpl.java:1047-1058`）与 `KmcApiServiceImpl.recallTest:147-175` 手工逐字段拷贝均漏标。移除 `score>0.1` 豁免后，`scoreFloor=0.40`（`CrossKbScoreCalibrator.java:33-34`）对 v2 RRF（典型 ≤4/61≈0.066）**全量误杀** → 多库装配近乎恒空 |
| R4a | 4 个 MCP Provider 伪 SUCCESS | **已修复** | 4/4 返回 `CallToolResult.error`（`CodeSandbox:78`、`DataAgent:113`、`KnowledgeBase:97`、`KnowledgeGraph:80`）；`Phase46` 断言改为 `isError=true` + KB delegate 适配器链路（10/10 实跑通过） |
| R4b | ToolNode 失败语义 | **新增回归（P0）** | `54fea59b` 曾改为 `failure("拒绝伪造成功执行")`；`1ff190d0` 53 分钟后改回 `"Executed in standalone mock mode"` 伪 SUCCESS（`ToolNodeBO.java:132-135`），`{"status":"SUCCESS"}` 无 error 键 → 绕过 `:146-148` 失败判定；动因是 `Phase154MasterContractTest:311-341`（null adapter 后审批放行断言 SUCCESS）——测试反向绑架复发 |
| R5 | 同名 Bean 双源启动风险 | **已修复** | `McpClientAutoConfiguration` 改 `@AutoConfiguration` + `@ConditionalOnMissingBean`（`:13-27`）；`SemanticToolRegistry` 移除 `@Component`（`:14`）；imports 齐备 |
| R6 | 客户端 header 伪造租户 | **部分修复 + 死代码** | Header 伪造被真防住（`BaseController.java:251-265`：仅 1001 或 admin 放行）；但反射目标 `tech.qiantong.qknow.security.utils.SecurityFrameworkUtils` **全仓不存在** → `Class.forName` 恒抛 → catch 后 `defaultWorkspaceId=1001`、`isAdmin=false`；多租户仍常量 1001，超管判定/用户工作区解析全是死代码 |
| R7 | 公开默认密钥 | **部分修复** | compose `TOKEN_SECRET: ${TOKEN_SECRET:?…}` fail-fast ✓；但 `application.yml:125` 公开默认仍在，`TokenService:58-71` 仅 warn 不阻断；HMAC 已 fail-close（见 P0-06） |

---

## 2. 上轮 P0 遗留项的验收

| 编号 | 判定 | 关键证据与残留 |
|---|---|---|
| P0-07 流式工具链 | **已修复（静态闭环 + 单测）** | 空片段守卫移到累计后（`DeepSeekCompatibleChatModel.java:381-397`）；`StreamToolCallAccumulator` 按 index 累积 id/name/args（`:486-559`）；`ToolResponseMessage` 展开出 `tool_call_id/content/name`（`:142-159`）；assistant 历史 `getToolCalls()` 兜底（`:172-194`）；thinking 时抑制 temperature（`:278-280`）。残留：无 `streamResponse`/SSE/graph 端到端测试、空参 `{}` vs `""` 不一致、工具 code 解析失败仍抛 `IllegalStateException` |
| P0-08 取消级联 | **部分修复（未闭环）** | `emitter.onDispose`/`setOnCancelHandler` 已提前注册，但 `streamSubscriptionRef`（`AgentOrchestrator.java:565`）与 `subscriptionRef`（`GrpcReactorBridge.java:82`）仍在**同步阻塞 subscribe 返回后**才 set；断连瞬间 ref=null → 内层不 dispose → DeepSeek `lines.close()` 永不触发 → Token 继续烧。机制本身有效（JDK `BodyHandlers.ofLines` close 能唤醒阻塞读），只差接通取消信号 |
| P0-09 HITL | **部分修复** | 生产挂起真正写 Redis（`KbFlowServiceImpl.java:397-407,773-813`）+ approve 后自动 `resumeFlow`（`ApprovalProxyController.java:99-119`）+ DEL 原子抢占。残留：`humanInput.approvalApproved` 可直接绕过；审批/恢复端点 **0 个 @PreAuthorize、无租户过滤**（`ApprovalProxyController` 与 `KbFlowController.resumeFlow`）；先消费后恢复失败无补偿；reject 不删 checkpoint 非终态 |
| P0-11 检查点 | **部分修复 + 语义回归** | DCL 建表有效（`DagCheckpointManager.java:497-528`）；但 `INSERT ... VALUES(...,'SUSPENDED')` 硬编码 + `ON CONFLICT status=EXCLUDED.status`（`:78-92`）→ **每次保存都把状态重置 SUSPENDED**，RUNNING 永不落库、CAS 屏障与租约自愈失效；`1ff190d0` 删除 `wakeSuspendedWithLock` 失败分支（`KbFlowServiceImpl.java:548-551`）→ 并发/重复 resume 可双跑 |
| P0-12 种子/模板 | **部分修复** | `LLMNodeBO.resolvePrompt` 三级回退（`prompt→promptTemplate→systemPrompt→默认`）+ `format` 空值守卫消除 NPE；`ConditionNodeBO` 兼容 `cases/condition/conditions/expression/兜底分支`。残留：种子 SQL 未同步（仍 `promptTemplate`/`cases`/provider=1 魔数）；条件缺变量时 `query.length > 20` 退化为字典序恒真 → 误走 deep；模板 `knowledgeBaseId=null`、start 无输出定义、tool 占位 |
| P0-04 gRPC 安全 | **部分修复** | 新增 `HermesGrpcAuthInterceptor`（`@GrpcGlobalServerInterceptor` 经 javap 证实含 @Component 元注解，Hermes 扫描包覆盖）；`chat()` 带 token；compose `ports→expose` 内网隔离 ✓。残留：默认 token 四处同一公开串（interceptor:26 / client:38 / compose:44,87）；loopback 白名单默认放行；**仍 usePlaintext、proto 仍明文 api_key**；`isHealthy()` 不带 token（无生产调用点，潜伏） |
| P0-06 HMAC | **已修复（安全）** | 字面量移除、独立配置键、空密钥 fail-close 401（`FlyFlowHmacAuthenticationFilter.java:50-58`）。运维缺口：默认空 → `/flyflow/**` 全 401 且该配置键未登记模板 |
| P0-01 TOKEN_SECRET | **部分修复** | compose `:?` fail-fast；application.yml 公开默认仍在、仅 warn |
| P0-15 多智能体 | **未修复 + 新风险** | AgentRegistry 仅被 plan-solve 复用；planner 不产出 worker 字段 → 全默认 `rag_worker` → 后续请求复用首个请求的 systemPrompt/tools/chatModel（旧会话 RAG 内容、旧用户模型凭据）→ 跨请求/跨租户污染；swarm/debate/consensus 等仍零生产调用 |
| P0-13 RAG 权限 | **未修复** | `RagRetrievalService:132/235` 与 `KmcKnowledgeBaseServiceImpl` 四处 catch-skip 仍 fail-open；userId 未显式贯穿；仅 CallerRunsPolicy 上下文恢复已修 |
| P0-16 评测证据 | **未修复** | 本轮无变化 |
| MCP1 | **部分修复** | 客户端自动装配已修复；服务端 `qknow-server` 仍无 `qknow-mcp-server` 依赖、`McpServerAutoConfiguration` 只造 registry 无 transport → 平台仍不是 MCP Server |
| MCP3 | **未修复** | 控制面无 `EmbeddingModel` Bean 注入（`McpToolAdapter.java:63`），索引向量仍 null；空索引仍返回全量 |
| MCP4 | **部分修复** | `StdioEnterpriseTransport:82-101` HOME 沙箱 + 代理白名单（实质改善）；但沙箱目录未创建、路径固定共享；GitHub MCP 无开关/超时/版本固定 |
| 依赖/CI | **未修复** | fat jar 418.2 MiB / 569 libs 不变，`embedded-redis`/`jacoco-agent` 仍在；CI 未补 `package`/compose 门禁 |

---

## 3. 本轮新增回归（必须在第三轮前处置）

| ID | 级别 | 回归 | 触发条件 | 证据 |
|---|---|---|---|---|
| N1 | **P0** | 多库 RAG 近乎恒空 | Bot 绑定 ≥2 知识库走跨库融合 | `CrossKbScoreCalibrator.java:33-34,103-107`；生产无 `setScoreType`；`Phase17...Test:203` 手工打标 |
| N2 | **P0** | 工具节点"未执行却成功" | `mcpToolAdapter == null`（降级/独立部署/测试） | `ToolNodeBO.java:132-135`；`Phase154MasterContractTest:311-341` |
| N3 | **P0/P1** | 工作流重复执行/非幂等 | 重复/并发 `/kb/flow/resumeFlow`；并行层多挂起节点分别审批 | `KbFlowServiceImpl.java:548-551`（wake 返回值丢弃、无状态/锁）；`DagCheckpointManager.java:78-92`（status 恒 SUSPENDED） |
| N4 | **P1** | plan-solve 跨请求状态/凭据污染 | plan-solve 开启且多请求 | `AgentOrchestrator.java:733-747` + `AgentRegistry`（worker 名默认 `rag_worker`，复用旧 systemPrompt/tools/chatModel） |
| N5 | P1 | 取消时并发闸门计数漂移 | 断连取消 | `AgentOrchestrator.java:477` 提前 `releaseReactCapacity` |
| N6 | P1 | gRPC 默认公开 token + loopback 放行 | 默认部署 | `HermesGrpcAuthInterceptor.java:26-30`、`HermesGrpcClient.java:38-39`、compose `:44,87` |

---

## 4. 与自证报告 `06-deep-fix-resolution.md` 的对账（要求更正）

| 06 报告声明 | 实际 | 证据 |
|---|---|---|
| R3「已彻底根除」 | **未修复且恶化**（生产无 scoreType → 0.40 误杀全部 v2 RRF） | §1 R3 |
| R6「结合 Spring Security…已彻底根除」 | **死代码**：反射类不存在，多租户仍常量 1001；仅 Header 伪造被防 | `BaseController.java:227`；全仓无 `SecurityFrameworkUtils` |
| R4「已彻底根除」 | Provider 已修，但 **ToolNode 伪成功被 1ff190d0 回退** | `ToolNodeBO.java:132-135` |
| P0-08「已闭环」 | 取消仍因 ref 赋值竞态不生效 | §2 P0-08 |
| P0-11「已闭环」 | status 恒 SUSPENDED；1ff190d0 删 wake 守卫 | §2 P0-11 |
| P0-12「已闭环」 | NPE 消除，但种子/模板/条件语义仍错 | §2 P0-12 |
| 「可信度与安全性 100%」 | 存在 4 项 P0 回归与多处 P1 | §3 |
| 用 `FlowExecutorTest`(2026-07 旧测试) 验证 R2/P0-12；用未改动的 `GrpcCancelPropagationTest`(异步源) 验证 P0-08 | **证据与结论不匹配**；Phase17 2b 为测试内自证 | 02/01 报告 §2.x |

---

## 5. 第三轮必须完成的最小返工清单（按优先级）

1. **回退 `1ff190d0` 的两处危险改动**：ToolNode `adapter==null` 恢复 fail-close；`resumeFlow` 恢复 wake CAS 失败守卫（并改为 lease/幂等语义）。同步修改 `Phase154MasterContractTest`（注入 stub adapter 或断言 adapter-null → FAILED）。
2. **R3 生产接线**：v2 RRF 结果标记 `scoreType=RRF`（在 `retrievalResult2vo`、`RetrieveResultRespVO`、`KmcApiServiceImpl.recallTest` 全链透传），legacy 标 `COSINE`；或撤回 `scoreFloor` 默认 0.0 并将门禁下沉到原始分。新增一条经过 `recallTest → calibrateAndAssemble` 的端到端契约。
3. **R6 真实现**：同模块直接用 `SecurityUtils`（或补 `LoginUser` 工作区字段与成员校验），删反射死代码；补租户单测。
4. **P0-08 真闭环**：`stream.subscribeOn(Schedulers.boundedElastic())` 或 `Disposable.Swap` 消除 null 窗口；补"阻塞源"取消集成测试（fake SSE server）。
5. **P0-09 鉴权与幂等**：审批/恢复端点 `@PreAuthorize` + 租户校验 + 审批人审计；禁止 `humanInput.approvalApproved` 绕过（服务端不可信变量）；resume 加分布式锁/幂等键与失败补偿；reject 清理 checkpoint。
6. **P0-11 状态语义**：checkpoint status 由调用方显式传入，`ON CONFLICT` 不再恒写 SUSPENDED；恢复 RUNNING 落库与租约自愈。
7. **P0-04**：默认 token 改 `:?` 必填；`qknownet` 加 `internal: true`；至少 mTLS 或移除 proto `api_key` 字段（控制面按别名解析）。
8. **P0-12**：同步种子 SQL（prompt/cases/provider 映射）；条件表达式支持 `query.length` / 变量取值，不可退化为字典序恒真；模板补 start 输出与 kbId 绑定。
9. **P0-15/N4**：AgentRegistry 要么真接线（按 worker 名带定义模板，禁止复用请求级参数），要么回退该复用逻辑。
10. **测试与 CI**：为 N1~N4 各补 1 个失败→修复回归测试；CI 增加 `mvn package` 与 compose `config` 门禁；禁止以放宽/自证断言让测试变绿。

**合并门禁（全部满足才可接受）**：① N1~N4 清零且各有回归测试；② 每条 P0 修复附带行为级测试（非 helper 级自证）；③ 全量 `mvn -pl tests -am test` + `mvn package` 绿；④ 一次真实 smoke（建 Bot→模板执行→多库对话→工具调用→断点恢复→取消）；⑤ 06 报告按事实更正。

---

## 6. 局限

- 未启动控制面/Hermes 容器与真实 DeepSeek/Qwen 接口；N1 的"多库 RAG 恒空"、P0-08 取消、P0-09 自动恢复、N3 重复执行均为**高置信静态推导**，需受控集成验证。
- 未执行 fat jar 打包（本轮依赖仅 `mcp-server` 加 `spring-boot-autoconfigure`、tests 加 `qknow-redis`，不改变上轮 418.2MiB 结论）。
- 本轮验收期间基线稳定在 `1ff190d0`，工作区无未提交源码改动。

---

## 附录：证据索引

- 子报告：[01-hermes-round2.md](01-hermes-round2.md)、[02-flow-round2.md](02-flow-round2.md)、[03-security-round2.md](03-security-round2.md)、[04-rag-round2.md](04-rag-round2.md)、[05-mcp-round2.md](05-mcp-round2.md)
- 运行证据：`backend/tests/target/surefire-reports/`（1867 tests，含 `DeepSeekCompatibleChatModelContractTest` 9、`Phase46McpContractTest` 10、`Phase17MultiKbRetrievalAndTenantGateTest` 6）；`backend/qknow-hermes/qknow-hermes-core/target/surefire-reports/`（99 tests）
- 提交：`54fea59b`（41 文件 +1255/-170）、`1ff190d0`（4 文件 +13/-11）；自证报告 `verify/06-deep-fix-resolution.md`
- 框架源码契约：`spring-ai-alibaba-agent-framework-1.1.2.2`（`AgentLlmNode`、`Agent.doStream`）、`spring-ai-alibaba-graph-core-1.1.2.2`（`NodeExecutor.mergeToolCalls`）、`spring-ai-deepseek-1.1.0`（官方 tool 消息映射）、JDK21 `ResponseSubscribers`
