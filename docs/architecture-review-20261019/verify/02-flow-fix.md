# Flow / HITL 修复复核报告：提交 2f0861a0 与当前工作区

> 复核人：Flow / HITL 修复复核员（独立子智能体）
> 复核对象：**当前工作区真实代码 = HEAD `cb6e185c` + 未提交改动**
> 被复核提交：`2f0861a0 fix(flow): HITL审批Redis外置与DAG恢复语义闭环`（10 文件，+897 / −45）
> 基线文档：`docs/architecture-review-20261019/00-MASTER-REVIEW.md` §3.4（P0-10~P0-12）、`02-flow-dag-hitl.md` §2.3/§2.4/§3、对方自证报告 `docs/ARCHITECTURE_DEFECTS_FORENSIC_VERIFICATION_REPORT.md`
> 纪律声明：本次复核**只读**，未修改任何源码/配置/SQL，未执行 `mvn`/`gradle`/测试/启动服务；唯一写入文件为本报告。

---

## 0 复核方法

1. **先读基线评审结论，再读代码**：逐条提取 `00-MASTER-REVIEW.md` §3.4 与 `02-flow-dag-hitl.md` 中 P0-09~P0-12（对应对方编号 F-P0-3/F-P0-4/F-P0-5、F-P0-1、F-P0-2）的**原始证据行号**，作为验收基线；不采信"自证报告"的结论，只把它的描述当作待核对的声明。
2. **通读提交 diff，再读最终文件**：`git show 2f0861a0` 逐文件阅读（ApprovalNodeExecutor、ApprovalProxyController、KbFlowNodeConvert、KbBotServiceImpl、IKbFlowTemplateService、LLMNodeBO、ToolNodeBO、KbFlowServiceImpl、KbFlowTemplateServiceImpl、init_all_bot_workflows.sql）；随后**重新打开工作区最终文件**核对，因为 diff 无法反映后续未提交改动。
3. **单独核对工作区未提交改动**：`git status` 显示 8 个已修改文件，其中与 Flow 相关的只有 `ToolNodeBO.java`；`git diff` 仅一个 hunk，且方向是"把失败改回伪 SUCCESS"，因此单独裁决。其余 7 个未提交文件（MCP Provider、CrossKbScoreCalibrator、契约测试、`.serena/project.yml`）不在本任务范围，本报告不复核。
4. **交叉验证装配事实**：对 P0-10 不只看 `KbFlowServiceImpl` 的注入点，还核对 `QKnowApplication` 扫描排除规则、`tech.qiantong.qknow.server.config.HermesConfiguration`、`DagCheckpointManager` 自身注解与 `JdbcTemplate` 可得性、`git blame` 确认 Bean 是哪个提交加入的。
5. **判定口径**：只有"目标缺陷的失败路径在最终工作区被消除"才判"已修复"；仅新增存储/工具/注释但生产空转的一律判"部分修复"或"未修复"；把已有缺陷重新引入或引入新破坏的判"新增回归"；依赖运行时环境/Bean 装配无法静态定论的标"无法静态判定（高置信推断）"。

---

## 1 逐项裁决表

| # | 复核项 | 判定 | 一句话结论 | 关键证据 |
|---|---|---|---|---|
| 1 | **P0-09 HITL 外置与跨 JVM 闭环** | **部分修复（核心闭环仍未修复）** | pending/decision 已写 Redis 并有 TTL、键前缀与 404 语义；但**生产路径没有任何代码调用 `ApprovalNodeExecutor.execute`**，审批结果没有任何消费者，控制面与 Hermes 的 Redis 实例/DB 不一致，恢复执行仍靠人工 `resumeFlow`，且可凭上下文变量绕过审批 | `ApprovalNodeExecutor.java:33,28-31,74-82,95-126,131-163,175-181`；`ReactiveWorkflowContractTest.java:104`；`ApprovalProxyController.java:25-26,37,102-129`；`FlowNodeTypeEnums.java:8-15`；`ToolNodeBO.java:102-108` |
| 2 | **P0-10 `DagCheckpointManager` Bean 可注入** | **已修复（当前工作区；由 `f4ff4dda` 提供，非本提交）** | 控制面新增 `@Bean dagCheckpointManager()` 且位于被扫描的 `tech.qiantong.qknow.server.config`，`@Autowired(required=false)` 现在能注入；`JdbcTemplate` 侧高置信可用 | `HermesConfiguration.java:35-39`；`QKnowApplication.java:21-26`；`KbFlowServiceImpl.java:74-75`；`DagCheckpointManager.java:25-27,35-36` |
| 3 | **P0-11 DAG 恢复语义** | **部分修复** | 起始层改为从 G 重跑（不再固定 G+1）、ERROR 节点会重试、剪枝集从 `nextNodeIds` 重建；但**层内崩溃整层重跑导致重复副作用**、无幂等键、`wakeSuspendedWithLock` 返回值被丢弃（并发重复恢复不受保护）、检查点不校验流程版本、`messageList` 不恢复 | `KbFlowServiceImpl.java:355-364,378,413,424,470-472,544,555-557,560-572,603-613`；`DagCheckpointManager.java:78-87,280-304,433-468` |
| 4 | **P0-12 种子 SQL / 模板 provider 与 config** | **部分修复** | `provider` 已在 10/10 个 LLM 节点补齐，模板配置已放进 `data.config`，SQL 语法/引号成对；但**LLM 运行时读取的是 `config.prompt`，SQL 与模板写的是 `promptTemplate`/完全缺 `prompt`，首个 LLM 节点仍 100% NPE 失败**；新建 Bot 的模板初始化因 `workspaceId=null` 触发 NOT NULL 约束必然失败；知识库节点 `knowledgeBaseId=null`、reply 输出映射为空、`valid_flag=0` 亦未修正 | `init_all_bot_workflows.sql:54,76,135,180,213,241,270,296,321,349`；`LLMNodeBO.java:100-104`；`KbFlowTemplateServiceImpl.java:49-50,166-179,181-198,201-211`；`KbFlowNodeConvert.java:37-53,75-84`；`deploy/sql/postgresql/01-schema.sql:834-854` |
| 5 | **P1-Tool2 工具节点失败语义** | **新增回归（工作区）；HEAD 亦属部分修复** | 工作区把 `mcpToolAdapter == null` 分支改回 `{"status":"SUCCESS",...}` 的本地 mock，明确伪造成功；即使按 HEAD 版本，生产环境 adapter 非空但零 MCP Server，`executeTool` 返回 `{"error":"No connected MCP server found ..."}`，不含 `isError/status`，仍被判 SUCCESS | 工作区 `ToolNodeBO.java:132-135`（diff 由 `NodeRunResultBO.failure` 改为 mock）；`ToolNodeBO.java:145-157,159-170`；`McpToolAdapter.java:353`；`HermesConfiguration.java:29-33` |
| 6 | **新增风险与回归** | **已发现多项** | Hermes 侧无 Redis 配置/无 REDIS 环境变量；审批端点无授权注解；`approve` 非原子；`dag_checkpoints` 无部署迁移且 DDL 为 PG 方言；新 SQL 未接入部署迁移且重跑全量 DELETE；`createKbBot` 新增失败吞噬的副作用；LLM provider/model 改为静默兜底掩盖配置错误 | 见 §3 |

> 说明：任务口径中"P0-09"在基线里对应 F-P0-3（HITL 无持久化/无恢复闭环），"P0-10"对应 F-P0-1（检查点 Bean 缺失），"P0-11"对应 F-P0-2（恢复语义），"P0-12"对应 F-P0-4/F-P0-5（种子 SQL / 新建 Bot 模板）。

### 1.1 P0-09 裁决细节

**已做对的部分（不能否认的改动）**：

- `ApprovalNodeExecutor` 新增 `hermes:approval:pending:` / `hermes:approval:decision:` 键前缀，`DEFAULT_TIMEOUT_SECONDS=24h`、`DECISION_RETENTION_SECONDS=2h`（`ApprovalNodeExecutor.java:28-31`），并通过 `IRedisService.set(key,json,timeout)` 写入；`IRedisService` 的 timeout 实现为 `TimeUnit.SECONDS`（`RedisServiceImpl.java:43-45`），TTL 语义成立。
- `approve/reject` 对不存在/已处理审批单返回 `CommonResult.error(404, "审批请求不存在或已处理")`（`ApprovalProxyController.java:87-88,97-99`），满足"不存在的单子返回 404"。
- `getPending` 优先 `SCAN` Redis 前缀（`ApprovalProxyController.java:37`，实现为游标 SCAN 而非 `KEYS`，`RedisServiceImpl.java:53-70`），再合并进程内视图，未使用阻塞命令。

**未修复的核心缺陷（P0 仍不成立）**：

1. **没有生产者**：`ApprovalNodeExecutor.execute(...)` 是唯一会写 `PENDING` 的代码，但全仓生产代码零调用，唯一调用点是测试 `ReactiveWorkflowContractTest.java:104`；控制面 `FlowNodeTypeEnums` 只有 start/llm/reply/condition/knowledge/tool/loop（`FlowNodeTypeEnums.java:8-15`），**根本没有 APPROVAL 节点类型**，所以控制面 DAG 不可能生成审批节点。控制面唯一的 HITL 是 `ToolNodeBO` 关键字门禁（`ToolNodeBO.java:88-118`），它走的是 `NodeRunResultBO.suspended` → `kb_runtime.SUSPENDED` + checkpoint，与 `ApprovalNodeExecutor` 无任何调用关系。生产可达的 pending 写入只剩调试接口 `/hermes/approval/dev/create`（`ApprovalProxyController.java:102-129`）。
2. **静态 Map 仍在**：`PENDING_APPROVALS` 仍为进程内 `ConcurrentHashMap`（`ApprovalNodeExecutor.java:33`），`getPendingApprovals()` 返回其拷贝（`:168-170`）；Redis 只是"额外副本"，`approve/reject` 仍先改内存（`:98-102,134-138`）。
3. **控制面与 Hermes 不是同一存储**：两者键前缀相同，但控制面 Redis 配置为 `spring.data.redis.host=127.0.0.1 / database=3`（dev，`application-dev.yml:16-22`）与 `host=redis / database=3`（prod，`application-prod.yml:8-16`）；**Hermes 侧 `application.yml` 全文 0 处 redis 配置**（`grep -c redis` 命中 0），部署编排 `hermes` 服务的 environment 仅注入 TZ/JAVA_OPTS/DEEPSEEK/TONGYI，**没有 REDIS_HOST/REDIS_PORT**（`deploy/docker/docker-compose-qknow.yml:70-90`）。Hermes 若用 Spring Boot 默认值将连 `localhost:6379` 的 database 0，与控制面（docker 网络内 `redis:6379` / db 3）既不同实例也不同逻辑库，键空间不共享。
4. **Hermes 侧 `getRedisService()` 本身取不到 Bean**：`ApprovalNodeExecutor` 用 `tech.qiantong.qknow.common.utils.spring.SpringUtils.getBean(IRedisService.class)`（`ApprovalNodeExecutor.java:175-181`），而 `SpringUtils` 是 `@Component`（`SpringUtils.java:18-19`），Hermes 启动类只扫描 `tech.qiantong.qknow.hermes / redis / ai.deepseek`（`HermesApplication.java:17-21`），**不扫 `tech.qiantong.qknow.common`** → 静态 `beanFactory` 恒 null → 调用永远抛异常被 catch → 返回 null → 不写 Redis。
5. **审批结果没有消费者（恢复链路断开）**：`REDIS_DECISION_PREFIX` 只在 `approve/reject` 中写入（`ApprovalNodeExecutor.java:116,153`），**全仓无任何读取点**；控制面 `resumeFlow` 不读 Redis、不轮询、无事件/回调（`KbFlowServiceImpl.java:517-622`），恢复只能由人工 `POST /kb/flow/resumeFlow` 触发（`KbFlowController.java:109-116`）。前端聊天审批弹窗只调 `/hermes/approval/approve` 后 `emit`（`frontend/src/views/kb/agent/components/ApprovalDialog.vue:74-84`），《工作流调试面板》虽调 `resumeFlow`（`frontend/src/views/kb/bot/build/components/WorkflowDebugRunPanel.vue:686-700`），但其 `runtimeId` 取的是本地构造工单的回退值（基线 02 报告 §2.4.2 已证该路径大概率 undefined），且它并不调用 approve 接口——两条链互不相连。
6. **Hermes 的"唤醒检查点"映射错误**：Hermes `ApprovalController.approve` 用请求体里的 `requestId` 当作 `checkpointManager.wakeSuspendedWithLock(requestId, ...)` 的 runtimeId（`ApprovalController.java:51-56`）。控制面检查点主键是 `kb_runtime.id`，`requestId` 是审批单临时串，二者不同命名空间 → 永远找不到检查点；且 Hermes 的 `DagCheckpointManager` 是 Hermes JVM 内另一实例/另一数据源配置，看不到控制面写入的行。
7. **原子性/一次性消费不足**：`approve` 是 `get(pending) → delete(pending) → set(decision)` 三步（`:109-116`），`reject` 同构（`:145-153`），无 Lua/`setNx`/WATCH；两个并发 approve 可同时通过 `get != null` 并都返回成功；delete 成功而 set 失败时审批终态丢失。pending TTL 24h 与内存 Map 的"无过期清理"也不一致（Map 永不清理，`:33`）。
8. **可绕过（且是本任务点名要查的）**：`ToolNodeBO` 放行条件为 `context.variables.approvalResult == "APPROVED"` 或 `approvalApproved == true`（`ToolNodeBO.java:102-108`）；`resumeFlow` 把 HTTP 请求体 `humanInput` **原样 `putAll` 进上下文变量**（`KbFlowServiceImpl.java:555-557`，入口 `KbFlowController.java:113-115`）。因此任何已登录用户对任意 `runtimeId` 传 `{"approvalApproved": true}` 即可绕过高危工具审批，并顺带污染后续条件变量（条件节点为手写字符串求值，见 02 报告 F-P1-7）。

**残留风险**：跨 JVM 部署下审批列表/终态两边不可见；审批与恢复两套机制并存导致"批了也不跑、跑了也不是因为批"；`/dev/create` 可生产注入假单；内存 Map 泄漏；审批端点无租户/角色校验（见 §3）。

**建议验证**：`POST /hermes/approval/dev/create` 后重启控制面，再 `GET /hermes/approval/pending` 仍应存在（证明读的是 Redis）；`POST approve` 两次，第二次应返回 404（并发场景用两个线程同时 POST 观察是否双成功）；在 Hermes 容器内执行 `redis-cli -h redis -n 3 KEYS 'hermes:approval:*'` 与控制面比对（预期 Hermes 侧空/连不上）。

### 1.2 P0-10 裁决细节

- 评审时结论"控制面排除 `tech.qiantong.qknow.hermes.*`，`DagCheckpointManager` 只在 Hermes 包内 `@Component`，注入恒 null"在当前工作区**已不成立**：`QKnowApplication` 的排除规则仍在（`QKnowApplication.java:21-26`），但 `tech.qiantong.qknow.server.config.HermesConfiguration` 已显式提供 `@Bean dagCheckpointManager()`（`HermesConfiguration.java:35-39`），该类位于 `tech.qiantong` 扫描范围内。`git blame` 显示该 Bean 由 **`f4ff4dda`（2026-10-20，"DeepSeek 工具调用协议对齐与取消链路级联治理"）** 添加，晚于 2026-10-19 的评审，早于 HEAD；**不是 `2f0861a0` 的改动**。
- `KbFlowServiceImpl` 的 `@Autowired(required = false)` 仍在（`KbFlowServiceImpl.java:74-75`），但 Bean 存在后非 null；`DagCheckpointManager` 的 `@Component` 在被排除的控制面不会重复注册，Hermes 侧则扫描 `hermes` 包得到组件版，不冲突。
- `JdbcTemplate` 注入（`DagCheckpointManager.java:35-36`，`required=false`）：控制面 `DashboardController` 以必需注入方式使用 `JdbcTemplate`（`DashboardController.java:33-34`），若 Bean 不存在应用无法启动，故可高置信推断控制面存在该 Bean，检查点走 JDBC 分支而非内存分支。【静态推断，未运行验证】
- **残留风险（原 P1 未修，仍应记录）**：① DDL 为 PostgreSQL 方言（`DagCheckpointManager.java:78-87` 的 `ON CONFLICT`、`:501-524` 的 `CREATE TABLE IF NOT EXISTS` + 7 条 `ADD COLUMN IF NOT EXISTS`），若部署切 MySQL 则检查点静默不落库（`saveCheckpointWithVariables` catch 后仅 `log.warn`，`:88-91`；`ensureTableExists` 失败仅 `log.debug`，`:525-527`）；② `save/load/wake/refresh/find` 每次都 `ensureTableExists`（8 条 DDL），存在锁竞争/性能风险；③ `UPDATE ... ON CONFLICT` 更新分支不写 `status`（`:78-87`），状态机语义仍不可靠；④ `wakeSuspendedWithLock` 的 CAS 只改 `version/status`，不写 lease 字段（`:280-304`），`findExpiredRunningCheckpoints`（`:433-468`）因 lease 恒 NULL 永不命中；⑤ 异常全部吞掉，检查点丢失静默。
- **建议验证**：启动控制面（本次不执行，交由 Lead 统一编译后验证），执行任一 `executeFlowStream` 后 `select * from dag_checkpoints where runtime_id=...` 应有行；再 `resumeFlow` 一次观察 `group_index` 推进；或给 `KbFlowResumeCheckpointTest` 注入 mock `JdbcTemplate` 断言 save/load 调用链。

### 1.3 P0-11 裁决细节

**已修复的三个点（与 diff 一致，代码已核对）**：

1. **起始层从 G 开始**：`resumeFlow` 中 `int startGroupIndex = checkpoint.getGroupIndex();`，仅当该层全部节点在 `initialResultMap` 中为 SUCCESS 才 `++`（`KbFlowServiceImpl.java:603-613`）。这修正了原 `G+1` 导致的"失败层被跳过、下游缺变量继续跑"的静默数据错误。
2. **ERROR 节点重试**：调度循环只对 `status == SUCCESS` 的已有结果短路（单节点 `:378`、并行 `:413`），ERROR/SUSPENDED 都会进入 `executeSingleDagNode` 重新执行（`:382,417-420`）。失败层保存时已把 ERROR 结果与其他层内结果一并写入 checkpoint（`:383,390,432,445-451`），因此恢复时错误节点会真正重跑。
3. **剪枝集重建**：恢复时遍历 `initialResultMap`，用 `DagUtils.computePrunedNodesUuids(key, nextNodeIds, edgePairs)` 反推剪枝集合（`:355-364`），`nextNodeIds` 在保存时已持久化（`hr.setNextNodeIds(r.getNextNodeIds())`，`:504`；恢复时回填 `:589`）；条件节点未选中的下游会被重新标记 SKIPPED（`executeSingleDagNode` 的 pruned 判定，`:640-651` 附近）而不再物理执行。这一条对应基线 F-P1-14，已实质修复。

**仍未修复的恢复语义缺陷**：

- **层内崩溃 = 整层重跑**：checkpoint 只在一层 `allOf.join()` 之后保存（`:424,470-472`），若进程在层执行中途崩溃，最后成功保存的是 G−1 层；恢复时 G 层所有节点（含已成功者）都会重跑 → LLM 计费、HTTP POST、MCP 写操作等**重复副作用**。全仓工作流执行路径仍无幂等键/per-node 提交记录，基线 F-P0-2 的后半段未消失。
- **并发重复恢复不受保护**：`resumeFlow` 调用 `wakeSuspendedWithLock` 但**丢弃返回值**（`:543-544`），CAS 失败（`updated==0`）时仍继续执行；两个并发 `resumeFlow` 可同时进入调度（`runtimeDO.setStatus(RUNNING)` 在 `:617-619`，无锁），造成双跑。
- **检查点无流程版本校验**：恢复用 `queryFlow(botId)` 重新读取"当前画布"（`:536`），但 `groupIndex` 来自旧图；画布在挂起期间增删节点/改边后会错层。checkpoint 只存 `flow_id=botId`，无定义摘要/版本号。
- **SKIPPED 结果被反复重写**：只有 SUCCESS 短路；SKIPPED 每次恢复都会再次生成并 `saveRuntimeNode`（`:369-384` 与 `executeSingleDagNode` 的 SKIPPED 分支），`kb_runtime_node` 重复 insert、`step` 冲突（基线 F-P2-22 未修），虽无业务副作用但污染回放。
- **上下文不完整**：恢复只重建 `variables/nodeMap/executeMode=BLOCK/botType`（`:560-572`），不恢复 `messageList`；chatflow 挂起恢复丢失多轮历史，且被强制 BLOCK 模式改变 LLM 流式行为。
- **状态字段不可靠**：如 1.2 所述 `status` 只在首次 INSERT 时写 `SUSPENDED`，之后 UPDATE 不更新；第一次恢复后 DB status 变 RUNNING，二次挂起不会写回 SUSPENDED → 再次 `wakeSuspendedWithLock` 的 `WHERE status='SUSPENDED'` 匹配 0 行；当前因返回值被忽略而"侥幸"继续，属语义债而非正确实现。

**建议验证**：在 `KbFlowResumeCheckpointTest`（已存在，`backend/qknow-module-kb/.../test/.../KbFlowResumeCheckpointTest.java`）基础上构造 3 层 DAG（层 2 含 1 成功 + 1 失败），断言恢复从第 2 层开始且只重跑失败节点；构造条件分支 + 故障恢复，断言被剪枝节点未调用 `nodeFactory.createNode`；双线程同时 `resumeFlow` 断言只有一个执行实例（当前预期会失败，暴露 CAS 被忽略问题）。

### 1.4 P0-12 裁决细节

**已落实的部分**：

- **`provider` 覆盖**：新 SQL 全部 10 个 LLM 节点的 `config` 与 `style.data.config` 均带 `"provider":1`（`init_all_bot_workflows.sql:54,76,135,180,213,241,270,296,321,349` 对应 config 行；style 行紧随其后），覆盖 bot 1~9（9 个 bot，bot 9 两个 LLM）。对比评审时"全文件 provider 0 次"已实质改善。
- **模板配置结构**：`KbFlowTemplateServiceImpl.createLlmData/createKnowledgeData/createReplyData` 把配置放进 `data.config`（`:181-198,201-211`），`KbFlowNodeConvert` 因此能取到 config；`KbFlowNodeConvert` 新增 LLM 兜底 `provider=1 / model=deepseek-flash`（`:46-53`）并在 `config==null` 时把 `data` 根字段回填进 config（`:37-45`）。
- **SQL 语法**：`BEGIN; ... COMMIT;`，28 条语句单引号全部成对、无未终止字符串、无 `$`/非 ASCII 引号、JSON 内 `\\n` 转义合法（按 PG `standard_conforming_strings` 与 MySQL 转义均可解析）；无字符串拼接 SQL，**未引入 SQL 注入面**（该文件为静态种子脚本）。
- **配套改动**：`KbBotServiceImpl.createKbBot` 在 bot 落库后调用模板初始化（`:63-75`）；`IKbFlowTemplateService` 新接口与实现可编译引用（`submitBatch` 使用与 `IKbFlowNodeService` 一致的签名）。

**仍导致"首个 LLM 节点必失败"的关键残留（P0 目标未达成）**：

1. **prompt 键不匹配（致命）**：运行时 `LLMNodeBO` 读 `configJson.getString("prompt")`（`LLMNodeBO.java:100,103`），随后 `super.format(promptFormat, context)` 直接对 null 做 `Pattern.matcher` → NPE，被 `BaseNodeBO.execute` catch 成 `NodeRunResultBO.failure`。而新 SQL 写的是 `promptTemplate`（`:54` 等），模板服务写的也是 `config.promptTemplate`（`KbFlowTemplateServiceImpl.java:194`）；bot 1/2/5/6/7/8 的 LLM config 甚至**完全没有 prompt 类字段**（`init_all_bot_workflows.sql:213,241,270,296,321,349`）。既有数据库种子行（`deploy/sql/mysql/latest初始化.sql:1731`）证明真正的运行时键是 `"prompt"`。因此"provider 修好"只是把失败点从 provider 校验后移到 NPE，**预置/模板工作流仍在第一个 LLM 节点失败**。
2. **新建 Bot 模板初始化 DB 写入必然失败（新问题）**：`initBotWorkflowByTemplate` 只设置 `flowVO.setBotId(botId)`，从未设置 `workspaceId`（`KbFlowTemplateServiceImpl.java:49-50`）；`KbFlowNodeConvert.toDOList` 把 `workspaceId=null` 赋给 DO（`:75-84`），而 MyBatis-Plus 全局 `insertStrategy: NOT_NULL`（`application.yml:175`）会省略 null 列；`kb_flow_node.workspace_id` / `kb_flow_edge.workspace_id` 为 `NOT NULL`（`deploy/sql/postgresql/01-schema.sql:834-854`、`deploy/sql/mysql/latest初始化.sql:1704-1725`）→ INSERT 必然报 NOT NULL 违反，异常被 `createKbBot` catch 成 `log.warn`（`KbBotServiceImpl.java:68-73`）。**净效果：新建 Bot 仍无默认流程（半态），且每次创建都产生一次失败事务与告警日志**（基线 F-P2-23 的缺陷被新路径放大）。【静态推断，置信度高；需一次真实建 Bot 验证】
3. **模板知识节点必错**：`createKnowledgeData` 写 `knowledgeBaseId: null`（`:206`），`KnowledgeNodeBO` 用 `containsKey("knowledgeBaseId")` 判断后直接把 `getLong` 的 null 放进 `kbIds`（`KnowledgeNodeBO.java:69-73`），`kbIds` 非空（含 null）→ 走并发检索 null 主键（异常或空结果）；模板 query 为 `{{ start_1.query }}`（`:208`），而 start 节点只写了 `fields`（`:166-179`）没有 `output` 定义，运行时 start 节点的 `input` 为空 → 上下文里没有 `start_1.query`，`format` 不替换 → 字面量 `{{ start_1.query }}` 被当检索词。
4. **reply 输出映射仍为空**：全部 reply 节点 DO 列 `output='[]'`（如 `:89,144,189`），style 内 `data.output: []`（`:90,145,190,217,245,274,300,325,353`）。`ReplyNodeBO.executeLogic` 在 BLOCK 模式下按 output 定义取值（`ReplyNodeBO.java:37-60`），空定义 → 运行结果输出 `{}`；流式 `setWorkFlowFlux` 亦无内容。**即使 LLM 修好，预置工作流也不会返回答案**（基线 F-P0-4 提到的 reply 空 output 未修）。
5. **知识库 id 全缺 → 硬编码兜底**：SQL 所有 knowledge 节点 config 无 `knowledgeBaseId/knowledgeIds`（如 `:24,28,120` 等），`KnowledgeNodeBO` 在 `kbIds.isEmpty()` 时**硬编码 `kbIds.add(7L)`**（`KnowledgeNodeBO.java:85-87`），即 9 个 bot 的知识检索全部命中 id=7 的"常州工学院总评方案知识库"，与节点 label 宣称的知识库不符（数据正确性缺陷）。
6. **`valid_flag=0`**：新 SQL 每条节点插入的 `valid_flag` 为 0（如 `:30,49,...`），而 DDL 注释与既有种子数据均为 1 表示有效（`deploy/sql/mysql/latest初始化.sql:1715,1730-1732`）。当前 `flowVOByBotId/listByBotId` 不按 `valid_flag` 过滤，故暂不阻断；但与"有效"语义相反，任何未来按 valid_flag 过滤的查询都会让种子流整体失效。

**建议验证**：`psql -f backend/init_all_bot_workflows.sql` 后 `select bot_id,count(*),sum(case when valid_flag=1 then 1 else 0 end) from kb_flow_node group by bot_id`（预期 count 满、valid=0）；对 bot 9 调 `POST /kb/flow/executeFlow`，观察日志是否出现 `LLM 节点执行` 前的 NPE/`节点执行失败`；`POST /kb/bot`（type=1）后 `select count(*) from kb_flow_node where bot_id=新id`（预期 0 + 日志含 workspace_id 非空约束错误）；修复后把 prompt 键统一为 `prompt` 并补充 reply output 映射再回归。

### 1.5 P1-Tool2 裁决细节（工作区回归）

- **HEAD 版本（提交内）**：`mcpToolAdapter == null` → `NodeRunResultBO.failure("MCP 工具适配器未初始化")`；`callResult == null` → failure；结果 JSON 带 `isError=true` 或 `status=ERROR/FAILURE` → failure（`ToolNodeBO.java:124-157`）。这修复了基线 F-P1-9 描述的"adapter 为 null 仍标 SUCCESS"的具体表现。
- **工作区未提交改动（`git diff` 唯一 hunk）**：把 null-adapter 分支改回
  `callResult = "{\"status\":\"SUCCESS\",\"tool\":\"...\",\"message\":\"Executed in standalone mock mode\"}"`（`ToolNodeBO.java:132-135`），并保留非空 adapter 的 null/错误形状检查。这是**明确的回归**：适配器缺失时伪造 SUCCESS，等于把 F-P1-9 的病灶重新植入，且用 "standalone mock mode" 掩盖了未执行事实。
- **即使按 HEAD，生产路径仍静默成功**：控制面的 `McpToolAdapter` Bean 由 `HermesConfiguration` 无参构造提供（`HermesConfiguration.java:29-33`，零 MCP Server 注册），`executeTool` 对未知工具返回 `{"error": "No connected MCP server found for tool: XXX"}`（`McpToolAdapter.java:353`）；该 JSON 只有 `error` 键，不含 `isError/status`，`ToolNodeBO` 的失败判定不命中（`:145-157`），最终仍写 `status=SUCCESS`（`:159-170`）。也就是说：**"工具没执行成功却标成功"这一可观测行为在生产仍然存在**，本提交只覆盖了"adapter 为 null"这一非生产分支。
- **建议验证**：为 `KbFlowServiceToolNodeTest`（已存在）添加用例：mock `McpToolAdapter` 返回 `{"error":"..."}`，断言节点 `status=ERROR`（当前预期失败）；再以 `adapter=null` 构造 `ToolNodeBO`，断言 `status=ERROR`（工作区当前预期为 SUCCESS，暴露回归）；修复方向：把 `error` 键/非 JSON 文本/MCP `isError` 统一映射为失败，并对空 adapter 一律 fail-close。

---

## 2 关键机制走查

### 2.1 HITL 数据流（当前工作区真实链路）

```
控制面 → KbFlowServiceImpl.executeFlow* → ToolNodeBO（仅有 HITL 门禁）
   ├─ 未批准 → NodeRunResultBO.suspended() → runtime.SUSPENDED
   │            → saveDagCheckpoint(G, results) → 返回
   └─ 已批准（变量 approvalApproved/approvalResult 由客户端 resumeFlow 注入）→ 继续执行
人工恢复：POST /kb/flow/resumeFlow {runtimeId, humanInput}
   → KbFlowServiceImpl.resumeFlow → wakeSuspendedWithLock(忽略返回值) → merge humanInput
   → 从 G/ G+1 重跑（成功节点跳过，ERROR/SUSPENDED 重跑）

另一条“审批”线（与上面不相连）：
ApprovalNodeExecutor.execute（无生产调用者）
   → Redis pending（SpringUtils 取 Bean）
ApprovalProxyController(控制面) / ApprovalController(Hermes)
   → approve/reject → Redis decision（无读取者）→ 无任何恢复回调
```

结论：**HITL 的"审批"与"恢复"是两条互不相连的线**；Redis 外置解决的是"审批单能不能被另一个 JVM 看到"，但既不解决"谁产生审批单"，也不解决"批准后谁来继续跑"。跨 JVM 的配置（Redis 实例/DB、Hermes 扫描包、requestId→runtimeId 映射）三处均断。

### 2.2 检查点注入走查

- `QKnowApplication` 仍排除 `tech.qiantong.qknow.hermes.*`（`QKnowApplication.java:21-26`），评分项上"排除规则未改"。
- 但 Bean 由 `HermesConfiguration.java:35-39` 在**未被排除的包**中提供，`DagCheckpointManager` 的 `@Component` 不参与控制面装配，因此控制面拿到的是 `@Bean` 实例，`@Autowired(required=false)` 注入成功。
- `DagCheckpointManager` 依赖 `JdbcTemplate`（`required=false`，`DagCheckpointManager.java:35-36`）；控制面 `DashboardController` 必需注入 `JdbcTemplate`（`DashboardController.java:33-34`），说明控制面有该 Bean → 检查点走 `INSERT ... ON CONFLICT` 持久化（`:78-87`），并在 `@PostConstruct` 时 `ensureTableExists`（`:36-39`）。
- 但：表不是部署迁移的一部分，是应用运行时 DDL；DDL 失败只 `log.debug`，保存失败只 `log.warn`（`:88-91,525-527`），因此"检查点管理器非 null"不等于"检查点一定落库"。这是从"Bean 可注入"到"恢复可用"之间仍未被任何测试/探针验证的灰区。

### 2.3 恢复语义状态机走查

1. 失败/挂起发生在第 G 层 → `saveDagCheckpoint(G, resultMap)`（`:390,402,451,471`），ERROR/SUSPENDED 结果在 resultMap 中。
2. `resumeFlow` 载入 checkpoint → merge humanInput → 重建 pruned 集合 → 计算 `startGroupIndex`：G 层全 SUCCESS 才 G+1，否则从 G 重跑（`:603-613`）。
3. 调度循环逐层：SUCCESS 命中缓存跳过；ERROR 重跑；SKIPPED 若被剪枝集覆盖则重新标记跳过；本层全部完成后保存 G+1（或清 checkpoint，`:475-479`）。
4. 由于 checkpoint 只在"整层完成后"写，**半层崩溃回滚到上一层的完成点**，恢复即整层重跑；没有 node-level commit、没有幂等键、没有"已执行但未提交"的补偿/去重，因此"至少一次"语义下的重复副作用仍成立。
5. `wakeSuspendedWithLock` 的 CAS 返回值未参与控制流（`:544`），并发恢复去重事实上不存在；`status` 字段不随保存更新（`:78-87`），二次挂起/恢复的 CAS 语义不可靠。

### 2.4 种子 SQL / 模板执行链走查

- 正式执行入口 `executeFlow*` 使用 `flowNodeService.listByBotId(botId)` 直接从 DB 取 DO（`KbFlowServiceImpl.java:202-203,220-221,255-256`），**不经过** `KbFlowNodeConvert`，因此 SQL 行里的 `config` 原样进入 `NodeFactory` → `LLMNodeBO`。
- `LLMNodeBO` 读 `config.provider/model/prompt`，provider/model 已由 SQL 补齐，prompt 缺失/键错 → NPE（见 1.4）。这解释了"provider 修复为何不足以让预置流跑起来"。
- 模板创建路径走 `KbFlowNodeConvert.toDOList`，但只拿到 botId/type，`workspaceId` 为 null → DB NOT NULL 失败；即便绕过，模板 config 的 `promptTemplate`/`knowledgeBaseId:null`/`start` 无输出定义仍会让 LLM/知识/检索链失败。
- `KbFlowNodeConvert` 的"provider 默认 1 / model 默认 deepseek-flash"属于**静默兜底**：它把配置缺失从"快速失败"改成"猜一个模型"，与基线"fail-fast"期望相反，也会掩盖前端漏传 provider 的真实问题（见 §3）。

---

## 3 新增风险与回归

| 风险 | 证据 | 影响 |
|---|---|---|
| **工作区 ToolNodeBO 回归：适配器缺失时伪 SUCCESS** | `ToolNodeBO.java:132-135`（工作区 diff 把 HEAD 的 `NodeRunResultBO.failure` 改回 mock JSON） | 工具未执行却标成功，工作流产出不可信；P1-Tool2 复现 |
| Hermes 侧无 Redis 配置，跨 JVM 审批不可用 | `qknow-hermes-starter/src/main/resources/application.yml`（0 处 redis）；`docker-compose-qknow.yml:70-90` 无 REDIS_HOST；控制面 db=3（`application-prod.yml:16`） | 新引入的"You can approve from control plane"在生产不成立 |
| 审批/恢复端点无细粒度鉴权 | `ApprovalProxyController.java:20-23`、`KbFlowController.java:109-116` 无 `@PreAuthorize`；`SecurityConfig.java:156` 仅要求登录；`/dev/create` 无 profile 守卫（`ApprovalProxyController.java:102-129`） | 任意已登录用户可批准/拒绝/注入审批单；`resumeFlow` 不校验 runtime 归属，可恢复他人工作流 |
| `approve` 非原子、decision 只写不读 | `ApprovalNodeExecutor.java:109-116,145-153`（get→delete→set）；`REDIS_DECISION_PREFIX` 仅在 `:29,116,153` 出现 | 并发双成功；decision 键成为 2h TTL 的写放大垃圾 |
| 新表无部署迁移、DDL 为 PG 方言 | `DagCheckpointManager.java:78-87,501-524`；`deploy/sql/*` 无 `dag_checkpoints` 迁移 | MySQL/无 DDL 权限部署下恢复 100% 静默不可用 |
| 种子 SQL 未接入部署迁移且全量 DELETE | `backend/init_all_bot_workflows.sql` 为本次新增文件（`git show --numstat` 362/0），`deploy/` 无引用；文件头 `DELETE FROM kb_flow_node/edge WHERE bot_id IN (1..9)` | 依赖人工执行；重跑会覆盖用户对 1~9 号 bot 的画布编辑；`valid_flag=0` 语义错误 |
| `createKbBot` 新增自动初始化但失败吞噬 | `KbBotServiceImpl.java:63-75`；模板服务 `KbFlowTemplateServiceImpl.java:49-50`（无 workspaceId） | 每个新 Bot 触发一次注定失败的事务 + 告警；Bot 保持"无流程"半态（基线 F-P2-23 放大） |
| LLM provider/model 静默兜底掩盖配置错误 | `KbFlowNodeConvert.java:46-53`、`LLMNodeBO.java:49-56` | 配置缺失不再报错而是默认 DeepSeek；错误模型/供应商风险，审计困难 |
| `ApprovalProxyController`/`ApprovalController` 双 `/hermes/approval` 映射仍在 | `ApprovalProxyController.java:22` vs `ApprovalController.java:16` | 当前因两个上下文隔离不冲突；未来合并/导入即 `Ambiguous mapping` 启动失败（基线 F-P2-26 未修） |
| 其他未提交改动 | `git status` 另有 7 个文件（MCP Provider/CrossKbScoreCalibrator/契约测试/.serena） | 不在本任务范围；请 Lead 在最终 diff 中另行裁决 |

**未引入的风险（已核对）**：

- **未发现新增 SQL 注入**：新代码无 SQL 字符串拼接，种子文件为静态 SQL，单引号配对检查通过。
- **未破坏既有 API 契约**：`/hermes/approval/pending|approve|reject` 路径与返回结构不变（仅数据源换成 Redis+内存合并）；`/kb/flow/resumeFlow` 入参出参不变；`KbBotServiceImpl.createKbBot` 返回仍为 botId（新增副作用）。
- **未新增第三方 Redis 依赖声明**：`qknow-redis` 早已是 Hermes core 依赖（`qknow-hermes-core/pom.xml:128-131`）；新问题是**配置**缺失而非依赖缺失。
- **未引入 `KEYS` 阻塞命令**：`scanKeys` 用游标 SCAN（`RedisServiceImpl.java:53-70`）。

---

## 4 无法静态判定项与验证建议

### 4.1 无法静态判定（需运行证据）

1. 控制面 `JdbcTemplate` 是否真实装配、`dag_checkpoints` 是否有 DDL 权限（本报告为高置信推断，源于 `DashboardController` 的必需注入）。
2. Hermes 容器在缺 Redis 配置时是否启动失败/仅连接 localhost，以及其在 compose 中缺 `POSTGRESQL_*` 环境变量是否导致数据源不可用（`application.yml` 默认 127.0.0.1）。
3. 新建 Bot 模板初始化是否真的在 `workspace_id` NOT NULL 处失败（静态推断，需一次真实 `POST /kb/bot`）。
4. 并发 `approve` 是否双成功（get→delete 竞态需并发实测）。
5. 并发 `resumeFlow` 是否双跑（CAS 返回值被忽略，需并发实测）。
6. 种子 SQL 在真实 PostgreSQL 上执行结果（本报告只做静态语法/引号配对检查，未执行）。
7. 前端 HITL 工单是否仍为本地伪造对象（基线 02 §2.4.2 的结论，未在本次重新做浏览器验证）。

### 4.2 建议验证清单（交 Lead 统一编译后执行；本复核员未运行）

1. **检查点装配**：启动控制面后 `select count(*) from dag_checkpoints`（表应存在）；调用一次 `POST /kb/flow/executeFlowStream`，再查该 runtime 的 checkpoint 行；随后 `POST /kb/flow/resumeFlow` 观察 `group_index` 与 `kb_runtime_node` 记录数（验证层内重复执行）。
2. **恢复语义单测**：扩展 `backend/qknow-module-kb/qknow-module-kb-biz/src/test/java/tech/qiantong/qknow/kb/biz/flow/KbFlowResumeCheckpointTest.java`：3 层 DAG、层 2 一成功一 ERROR，断言 resume 从层 2 开始、只重跑 ERROR；条件分支 + 崩溃恢复断言 SKIPPED 不物理执行。
3. **HITL 外置**：`POST /hermes/approval/dev/create` → 重启控制面 → `GET /hermes/approval/pending` 应仍在（证明 Redis 非内存）；双线程并发 `POST approve` 断言仅一次 200、一次 404。
4. **跨 JVM**：进入 `agent-hermes` 容器执行 `redis-cli -h redis -n 3 KEYS 'hermes:approval:*'` 与 `redis-cli -h redis -n 0 ...` 对照；观察 `ApprovalProxyController` 与 Hermes `ApprovalController` 是否看到同一批单据（预期否）。
5. **种子 SQL**：在 PG 上执行 `backend/init_all_bot_workflows.sql`，核对 `select bot_id,count(*) from kb_flow_node group by bot_id`（1~9 全覆盖）与 `valid_flag` 分布；随后触发 bot 9 执行，日志应出现"节点执行失败"（当前预期，证明 prompt 缺陷）。
6. **新建 Bot 模板**：`POST /kb/bot`（type=1/2）后查 `kb_flow_node`（预期 0 行），日志应出现 `null value in column "workspace_id"` 类错误。
7. **工具节点失败语义**：用不存在的工具名构造 Tool 节点执行，断言 `kb_runtime_node.status`（当前预期 SUCCESS，应为 ERROR）；分别覆盖 `adapter=null`（工作区回归）与 `adapter={"error":...}`（HEAD 残留）。
8. **修复验收测试建议新增**：`FlowHitlResumeContractTest`（Mock RedisService + Mock JdbcTemplate），断言：① pending 写 Redis 且 TTL=86400；② 第二个 approve 返回 404；③ approve 后存在回调/轮询能触发 `resumeFlow`（当前不存在，应作为新增需求用例）；④ `humanInput` 含 `approvalApproved` 不得绕过门禁（需改为审批单凭据校验）。

---

### 附录 证据索引

| 主题 | 路径:行 |
|---|---|
| HITL 键空间/TTL/静态 Map | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/node/ApprovalNodeExecutor.java:28-31,33,41,74-82,95-126,131-163,168-170,175-181` |
| HITL 唯一调用点为测试 | `backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/ReactiveWorkflowContractTest.java:104` |
| 控制面无 APPROVAL 节点类型 | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/dal/enums/FlowNodeTypeEnums.java:8-15` |
| 审批代理控制器（Redis 读取/404/dev-create/无授权） | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/controller/admin/ApprovalProxyController.java:20-30,37,61-77,83-100,102-129` |
| Hermes 审批控制器（requestId→runtimeId、只读内存、无 Redis） | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/controller/ApprovalController.java:19-20,24-38,48-59,71-75` |
| 控制面 Redis 配置（db=3） | `backend/qknow-server/src/main/resources/application-dev.yml:16-22`；`application-prod.yml:8-16` |
| Hermes 无 Redis 配置 | `backend/qknow-hermes/qknow-hermes-starter/src/main/resources/application.yml`（grep `redis`=0） |
| Hermes 部署环境无 REDIS_* | `deploy/docker/docker-compose-qknow.yml:70-90` |
| Hermes 扫描包不含 common（SpringUtils 不可用） | `backend/qknow-hermes/qknow-hermes-starter/src/main/java/tech/qiantong/qknow/hermes/HermesApplication.java:17-21`；`backend/qknow-framework/qknow-common/src/main/java/tech/qiantong/qknow/common/utils/spring/SpringUtils.java:18-19` |
| 审批绕过（上下文变量） | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/flow/bo/ToolNodeBO.java:102-108`；`.../service/flow/impl/KbFlowServiceImpl.java:555-557`；`.../controller/admin/flow/KbFlowController.java:113-115` |
| 检查点 Bean 供给 | `backend/qknow-server/src/main/java/tech/qiantong/qknow/server/config/HermesConfiguration.java:35-39`；`backend/qknow-server/src/main/java/tech/qiantong/qknow/server/QKnowApplication.java:21-26`；`KbFlowServiceImpl.java:74-75` |
| 检查点实现/DDL/吞异常 | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/dag/DagCheckpointManager.java:25-39,77-91,280-304,433-468,501-527` |
| 控制面 JdbcTemplate 必需注入 | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/controller/admin/DashboardController.java:33-34` |
| 恢复语义（起始层/剪枝/跳过/重试） | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/flow/impl/KbFlowServiceImpl.java:355-364,378,413,424,470-472,485-510,543-544,555-557,560-572,603-613` |
| 种子 SQL provider / promptTemplate / reply output | `backend/init_all_bot_workflows.sql:54,76,88,135,143,180,188,213,241,270,296,321,349` |
| LLM 运行时读 `prompt` | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/flow/bo/LLMNodeBO.java:100-104`；`BaseNodeBO.java:157-176`（format NPE） |
| 既有种子用 `prompt`（反证键名） | `deploy/sql/mysql/latest初始化.sql:1731` |
| 模板生成（data.config/promptTemplate/workspaceId 缺失/knowledgeBaseId null/start fields） | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/flow/impl/KbFlowTemplateServiceImpl.java:49-50,71-72,166-179,181-198,201-211` |
| Bot 创建自动初始化 + 失败吞噬 | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/bot/impl/KbBotServiceImpl.java:63-75` |
| 节点转换（provider 兜底/workspaceId 赋值） | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/convert/flow/KbFlowNodeConvert.java:37-53,75-84` |
| 表结构 NOT NULL workspace_id | `deploy/sql/postgresql/01-schema.sql:834-854`；`deploy/sql/mysql/latest初始化.sql:1704-1725` |
| MyBatis-Plus insertStrategy NOT_NULL | `backend/qknow-server/src/main/resources/application.yml:175` |
| 工具节点 HEAD 失败判定 / 工作区 mock 回归 | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/flow/bo/ToolNodeBO.java:124-157`（HEAD）、`:132-135`（工作区 mock SUCCESS）、`:159-170`（无错误键仍 SUCCESS） |
| MCP 空适配器错误形状 | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/mcp/McpToolAdapter.java:319-355` |
| MCP 空适配器 Bean | `backend/qknow-server/src/main/java/tech/qiantong/qknow/server/config/HermesConfiguration.java:29-33` |
| Redis set 单位秒 / SCAN | `backend/qknow-framework/qknow-redis/src/main/java/tech/qiantong/qknow/redis/service/impl/RedisServiceImpl.java:43-45,53-70` |
| 安全策略仅 anyRequest authenticated | `backend/qknow-framework/qknow-security/src/main/java/tech/qiantong/qknow/security/config/SecurityConfig.java:156` |
| 知识库兜底硬编码 id=7 | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/flow/bo/KnowledgeNodeBO.java:69-73,85-87` |
| Reply 输出映射为空 | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/flow/bo/ReplyNodeBO.java:37-60` |
| 前端审批弹窗/恢复入口 | `frontend/src/views/kb/agent/components/ApprovalDialog.vue:74-84`；`frontend/src/views/kb/bot/build/components/WorkflowDebugRunPanel.vue:686-700`；`frontend/src/api/kb/bot/flow.js:119-126` |
| 既有恢复测试可扩展 | `backend/qknow-module-kb/qknow-module-kb-biz/src/test/java/tech/qiantong/qknow/kb/biz/flow/KbFlowResumeCheckpointTest.java`；`KbFlowServiceToolNodeTest.java` |
