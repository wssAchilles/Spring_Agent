# Flow 修复第二路独立交叉复核（task-12）

- 复核对象：提交 `2f0861a0 fix(flow): HITL审批Redis外置与DAG恢复语义闭环` + **当前工作区真实代码**（HEAD `cb6e185c` + 8 个未提交改动文件）
- 复核方式：只读静态走查（逐行 diff + 全仓 grep 装配/调用关系 + 数据模型比对）；未执行 `mvn`/测试/服务启动（Lead 已统一编译 BUILD SUCCESS）
- 角色定位：第二路独立复核（第一路为 flow-fix-verifier 的 `02-flow-fix.md`）；本文件不复述对方结论
- 结论标注：**【已验证】**= 源码/diff/数据文件直接证明；**【推断】**= 多行代码/装配关系推出，未运行时复现；**【未知】**= 本次只读无法判定

路径缩写：`KB`=`backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb`；`HC`=`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes`；`SRV`=`backend/qknow-server/src/main/java/tech/qiantong/qknow/server`；`SQL`=`backend/init_all_bot_workflows.sql`。

---

## 0 复核对象与工作区事实

### 0.1 提交 2f0861a0 实际改动（10 文件，+897/−45）

- 新增：`SQL`（362 行，之前未纳入版本控制）、`KB/service/flow/IKbFlowTemplateService.java`、`KB/service/flow/impl/KbFlowTemplateServiceImpl.java`
- 修改：`HC/flow/node/ApprovalNodeExecutor.java`（+119）、`KB/controller/admin/ApprovalProxyController.java`、`KB/convert/flow/KbFlowNodeConvert.java`、`KB/service/bot/impl/KbBotServiceImpl.java`、`KB/service/flow/bo/LLMNodeBO.java`、`KB/service/flow/bo/ToolNodeBO.java`、`KB/service/flow/impl/KbFlowServiceImpl.java`
- **没有任何测试文件改动**（`git show 2f0861a0 --name-status`）→ 本轮修复零新增回归测试。

### 0.2 工作区未提交改动（与本任务相关部分）

`git status --short` 中与 flow 相关的只有一处：`KB/service/flow/bo/ToolNodeBO.java`（另 7 个为 MCP Provider/测试/`.serena`/CrossKbScoreCalibrator，属其他复核员范围）。`git diff` 显示该文件把提交中"adapter 缺失即 failure"的逻辑**改回 mock SUCCESS**（详见 §1.5），这是本轮必须判定的工作区回归。

### 0.3 本轮判定总览

| 项 | 判定 | 一句话理由 |
|---|---|---|
| P0-09 HITL Redis 外置 | **部分修复** | Redis 键/TTL/读写 API 真实存在，但生产挂起路径不写入、决策无人消费、审批后无自动恢复，闭环未成立 |
| P0-10 DagCheckpointManager 控制面注入 | **已修复（但非本提交，由 f4ff4dda 引入）** | `SRV/config/HermesConfiguration.java` 显式 `@Bean`，控制面可注入；但激活了 3 个此前不可达的检查点缺陷 + MCP Bean 启动副作用 |
| P0-11 恢复语义 | **部分修复** | 起始层 G / ERROR 重跑 / 剪枝集重建已修；幂等、二次挂起唤醒、画布版本漂移未修 |
| P0-12 种子 SQL / 模板 provider 与 config | **部分修复 + 新增回归风险** | provider/model 已补，但 prompt 键错位致 LLM 必 NPE；新建 Bot 模板初始化因 workspaceId=null 必然违反 NOT NULL 并把 `createKbBot` 拖入 rollback-only；condition/knowledge 契约仍错 |
| P1-Tool2 工具失败语义 | **未修复（HEAD 残留）+ 工作区新增回归** | HEAD 的成功判定漏掉 MCP 适配器唯一的 `{"error":...}` 失败形态；工作区又把 adapter-null 改回 mock SUCCESS |

---

## 1 逐项裁决

### 1.1 P0-09 HITL：**部分修复**

**已完成（真实外置）【已验证】**

- 键空间与 TTL：`HC/flow/node/ApprovalNodeExecutor.java:28-31`（`hermes:approval:pending:`、`hermes:approval:decision:`、pending 24h、decision 2h）；TTL 以秒写入：`HC/.../ApprovalNodeExecutor.java:77` + `backend/qknow-framework/qknow-redis/.../RedisServiceImpl.java:38-40`（`set(key,value,timeout,SECONDS)`）。
- 写待办：`ApprovalNodeExecutor.java:59-79`（pendingData 含 approvalKey/flowId/requestId/nodeId/reason/status/suspendedAt）。
- 审批/拒绝：`ApprovalNodeExecutor.java:95-127`、`:131-163`（get pending → delete pending → set decision）。
- 控制面读取：`KB/controller/admin/ApprovalProxyController.java:25-26`（注入 `IRedisService`）、`:29-75`（`scanKeys` 扫描 Redis + 进程内视图兜底）、`:82-100`（approve/reject 直接调 executor）、`:103-129`（dev/create 写 Redis）。
- Redis 扫描用的是 `SCAN` 而非 `KEYS`：`RedisServiceImpl.java:53-58`，pattern 为常量前缀，无注入面。

**未完成（闭环缺口）【已验证】**

1. **生产挂起路径根本不写 Redis**：控制面 ToolNode 的 HITL 门禁只返回 SUSPENDED 负载（`KB/service/flow/bo/ToolNodeBO.java:110-119`），没有调用 `ApprovalNodeExecutor.execute`；全仓 `ApprovalNodeExecutor.execute` 的非测试调用点为零（唯一在 `backend/tests/.../ReactiveWorkflowContractTest.java:104`）。⇒ 真实工作流挂起后 `/hermes/approval/pending` 永远扫不到它；只有 `/dev/create` 能造出数据。
2. **决策无人消费、审批后不恢复执行**：`REDIS_DECISION_PREFIX` 全仓只有写（`ApprovalNodeExecutor.java:29,116,153`），没有任何读取点；`resumeFlow` 仍要求人工传 `runtimeId + humanInput`（`KB/service/flow/impl/KbFlowServiceImpl.java:517-560`，wake 在 `:544`）。且 `approvalKey = flowId:requestId:nodeId`，与 `resumeFlow(runtimeId)` 的 runtime 主键不是同一 ID 空间，无映射字段。⇒ "审批→回填→恢复执行"没有自动化链路。
3. **一次性/并发安全**：approve 是 `get → delete → set` 三步非原子（`:109-116`），并发 approve/reject 可双写、最后写覆盖；未使用 `setNx`（`RedisServiceImpl.java:147-150` 已提供但未用）；决策里无审批人身份（`:108-120` 只有 approvalKey/decision/decidedAt/reason），无法审计"谁批的"。
4. **权限/租户**：`ApprovalProxyController` 的 approve/reject/getPending 均无 `@PreAuthorize`、无租户过滤（`:29-100`），任意登录用户可批准任何租户的待办；`scanKeys` 前缀不按 workspace 隔离（`:37`）。
5. **跨 JVM 唤醒不存在**：executor 用进程内 CompletableFuture 表做唤醒（`ApprovalNodeExecutor.java:34`，approve 中 `PENDING_APPROVALS.remove`），跨 JVM 审批只会写 Redis 决策，**无法完成另一个 JVM 的 future**；且 future 表在跨 JVM 审批时永远不会被清理（只在本 JVM approve/reject 时 remove）→ 内存泄漏与"挂起等待者永不唤醒"【推断：当前无 awaiter，泄漏暂不显性】。
6. **Hermes 侧外置会静默降级**：`ApprovalNodeExecutor.java:175-181` 通过 `SpringUtils.getBean` 取 Redis；`SpringUtils` 是 `tech.qiantong.qknow.common` 的 `@Component`（`backend/qknow-framework/qknow-common/.../SpringUtils.java:18-19`），而 Hermes 启动类只扫 `hermes/redis/ai.deepseek`（`backend/qknow-hermes/qknow-hermes-starter/.../HermesApplication.java:17-21`）→ Hermes 进程内 `getBean` 抛错被 catch（`:179-181`）→ Redis 不可用、退化为内存态。所谓"跨 JVM"若在 Hermes 节点执行则失效。
7. **无工作流级超时**：pending TTL 只是 Redis 键过期（`:30`），没有 watchdog 对挂起 workflow 做超时 fail-close；24h 后待办静默蒸发，运行实例仍是 SUSPENDED。
8. **审批仍可被变量绕过**：`ToolNodeBO.java:102-108` 只要上下文变量 `approvalResult=APPROVED` 或 `approvalApproved=true` 就跳过审批；这些变量可由流程输入或 `resumeFlow` 的 humanInput 注入（`KbFlowServiceImpl.java:553-560` 合并 humanInput 到变量）→ 高危工具审批门禁可被外部输入绕过（未修，且与 Redis 决策无绑定校验）。

**残留风险**：HITL 仍是"半成品 UI 后端"——待办列表可读（只对 /dev/create 与未来接入 execute 的调用者有效），但没有生产者接线、没有结果消费、没有自动恢复、没有审批人审计、没有租户/权限、没有一次性仲裁。

**验证方式**（恢复运行权限后）：
1. 在真实流程中用高危工具触发 SUSPENDED，`redis-cli --scan --pattern 'hermes:approval:pending:*'` 应仍为空 → 证伪"生产挂起已外置"。
2. 手工 `/hermes/approval/dev/create` 后 `/pending` 可见；连续两次 `/approve` 与一次 `/reject` 并发压测，观察决策是否被覆盖。
3. 挂起后调用 `/kb/flow/resumeFlow`，检查未传 approval 变量时高危工具是否重新挂起（应挂起、而非直接执行）。
4. Hermes 进程内调用 `ApprovalNodeExecutor.execute`（若未来接线）并观察 Redis 是否写入（预期不写入）。

### 1.2 P0-10 DagCheckpointManager 控制面注入：**已修复（归因不在本提交）**

**证据【已验证】**

- `SRV/config/HermesConfiguration.java:14-15`（`@Configuration`，位于未被排除的 `tech.qiantong.qknow.server.config` 包）、`:29-33`（`@Bean @ConditionalOnMissingBean McpToolAdapter`）、`:35-39`（`@Bean @ConditionalOnMissingBean DagCheckpointManager`，`new DagCheckpointManager()`）。
- 控制面组件扫描仍排除 hermes 包：`SRV/QKnowApplication.java:21-26`——因此该 `@Bean` 是控制面获得 `DagCheckpointManager` 的唯一来源；注入点 `KB/service/flow/impl/KbFlowServiceImpl.java:74-75`（`@Autowired(required=false)`）现在能拿到实例。
- 该 Bean 由 **`f4ff4dda`** 引入，不是被复核的 `2f0861a0`：`git log --oneline -- SRV/config/HermesConfiguration.java` = f4ff4dda、a168f5d8；`git show 2f0861a0 --stat` 不含此文件。【已验证：归因纠正】
- 第二层依赖 `JdbcTemplate` 可注入：控制面已有生产代码注入 `JdbcTemplate`（`KB/controller/admin/DashboardController.java:34`），且 `qknow-mybatis` 使用 `dynamic-datasource-spring-boot3-starter`（`backend/qknow-framework/qknow-mybatis/pom.xml:77`）；DB 为 PostgreSQL（`deploy/docker/docker-compose-qknow.yml:24` 的 `POSTGRESQL_URL`；`backend/qknow-server/src/main/resources/application-dev.yml:6,100`）→ `DagCheckpointManager` 的 `ON CONFLICT` / `ALTER TABLE ... IF NOT EXISTS` 方言可用。【推断：Bean 产物的 `@Autowired` 字段由 `populateBean` 注入，Spring 标准行为；未启动验证】
- 于是 `saveDagCheckpoint`（`KbFlowServiceImpl.java:485-514`）与 `resumeFlow`（`:517+`）不再是 null 空转，`resumeFlow` 的"未配置检查点管理器"分支（`:521-523`）不再触发。

**修复附带的新增风险（此前因 Bean 为 null 而不可达）【已验证/推断】**

1. **每次 save/load/wake 都执行建表 DDL**：`HC/flow/dag/DagCheckpointManager.java:56` 调 `ensureTableExists()`（定义 `:495-528`：1 条 `CREATE TABLE IF NOT EXISTS` + 7 条 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`）。PostgreSQL 的 `ALTER TABLE` 即使是 no-op 也会取表级锁 —— 每条工作流每层都执行 8 条 DDL，P1 性能/锁竞争风险（现在才真正生效）。
2. **status 只在 INSERT 写 `'SUSPENDED'`，UPDATE 不写**：`:80-86`；而唤醒 CAS 要求 `WHERE ... AND status='SUSPENDED'`（`:290-294`）。第一次恢复后 status 变 `RUNNING` 且不再回写 → **第二次及以后挂起的 `wakeSuspendedWithLock` 必然 CAS 失败（0 行）**；`KbFlowServiceImpl.java:544` 又忽略返回值 → 第二次挂起只能靠 P0-11 的"重新执行"路径兜底（见 1.3），语义由"唤醒"退化为"重跑"。
3. **正常保存无 fencing**：主路径调 `saveCheckpointWithVariables`（`:52-92`），fencing 写屏障方法 `saveCheckpointWithFencingToken`（`:98-148`）不被执行器使用；异常全部 `log.warn` 吞掉（`:89-91`）→ 检查点丢失静默。
4. **MCP Bean 启动副作用**：`HermesConfiguration.java:31-32` 现在会在控制面创建 `McpToolAdapter`，其 `@PostConstruct` 在检测到 `GITHUB_PERSONAL_ACCESS_TOKEN` 时**异步注册 GitHub MCP Server**（`HC/tool/mcp/McpToolAdapter.java:88-103`，可能拉起 stdio 子进程/访问网络）；默认无任何已注册 server → `executeTool` 对任意工具返回 `{"error": ...}`（`:321/:328/:353/:365/:373`），直接触发 1.5 的 P1-Tool2 残留。这是"装配修复"带来的新暴露面，建议加 `@ConditionalOnProperty`/显式初始化开关。

**验证方式**：启动控制面后检查 `applicationContext.getBean(DagCheckpointManager.class)` 非空；执行一次正式流程，`SELECT * FROM dag_checkpoints` 应有行且 `status/group_index/variables_json` 正确；第二次挂起后调用 resumeFlow，观察是否出现 `CAS 乐观锁校验失败` 日志。

### 1.3 P0-11 恢复语义：**部分修复**

**已修复【已验证】**

1. **剪枝集重建**：`KbFlowServiceImpl.java:354-362` 遍历 `initialResultMap`，对每条带 `nextNodeIds` 的历史结果调用 `DagUtils.computePrunedNodesUuids` 重建 `prunedNodes` → 恢复后不会再物理执行"原未命中分支"。
2. **起始层从 G 开始**：`:603-614` 取 `checkpoint.getGroupIndex()`；仅当"该层所有节点均为 SUCCESS"才 `G+1`，否则停在该层重跑；随后 `:621` 进入调度。
3. **ERROR 节点重执行**：主循环只对 `status==SUCCESS` 命中缓存（`:378`、`:413`），非 SUCCESS（含 ERROR/SUSPENDED）会重新 `executeSingleDagNode`（`:381-384`、`:418-421`）→ 失败层不再被整体跳过（原 P0-2 的核心断裂已闭环）。
4. 层次语义自洽：正常层成功后保存的是当前层号（`:390/:402/:449/:461/:471`），与恢复侧"G 全成功才 +1"约定一致；现有 `KbFlowResumeCheckpointTest`（checkpoint `groupIndex=1` 且该层全成功）仍与 `G+1` 预期一致，不构成测试回归（`backend/qknow-module-kb/.../KbFlowResumeCheckpointTest.java:184-189`）。

**未修复/残留【已验证/推断】**

1. **无幂等键**：重跑节点（ERROR 重试、二次挂起重跑）没有任何幂等标识传入节点；HTTP POST、MCP 写工具、LLM 调用会重复产生副作用（至少一次语义）。工作流执行路径全仓无 idempotency 实现。
2. **二次挂起退化为重跑**：受 1.2 的 status 缺陷影响，第二次挂起后 `wakeSuspendedWithLock` CAS 失败且被忽略（`:544`），SUSPENDED 结果未被置为 SUCCESS；恢复逻辑只能重跑该节点（对 Tool 审批门禁可接受，因为重跑会重新判定审批变量；但属于"重跑"而非"唤醒"，若挂起节点带前置副作用则有重复执行风险）。
3. **画布版本漂移**：`resumeFlow` 用当前画布重建 flow（`:536` `queryFlow(botId)`）与 `parallelGroups`（`:607`），但 checkpoint 只有 `flow_id=botId`、没有节点集/边集/定义哈希；画布在挂起期间被编辑后，旧 `groupIndex` 可能对应新分层，甚至 `startGroupIndex >= parallelGroups.size()` → 主循环不执行，随后走成功收尾 `saveRunSuccess + deleteCheckpoint`（`:476-478`）→ **误判成功并删除检查点**（false-success）【推断，未运行复现】。
4. **step 计数冲突**：`stepCounter` 从 `initialResultMap.size()+1` 起（`:367`），恢复后重跑节点会与历史 step 重叠，`kb_runtime_node` 每次 insert 不 upsert（`KB/service/runtime/impl/KbRuntimeServiceImpl.java:123-133`）→ 节点历史出现重复/乱序。
5. **失败重试无上限/无退避**：只是"恢复时重跑一次"，连续失败需人工反复 resume；无最大次数、无错误分类。

**验证方式**：构造 3 层流程并让第 2 层节点第一次失败：执行 → 检查 checkpoint `group_index=1` 且含 ERROR 结果 → `resumeFlow` 后该节点应再次执行（mock 节点计数 +1）、成功兄弟节点不得重跑；再连续制造两次挂起→恢复，观察第二次日志出现 CAS 失败且节点被重跑；恢复前修改画布（删一层）验证是否误判成功。

### 1.4 P0-12 种子 SQL / 模板 provider 与 data.config：**部分修复（端到端可执行性未达成）+ 新增回归（新建 Bot 失败风险）**

**已修复【已验证】**

- `SQL` 全部 10 个 LLM 节点均已补 `provider`（`grep -c '"provider"' SQL` = 20 = 10 节点 × config/style 双份；如 `SQL:54,57,76,79,...`）。
- `KbFlowNodeConvert.java:46-52`：LLM 节点写入 provider 兜底 `1L`、model 兜底 `deepseek-flash`；`LLMNodeBO.java:49-56` 运行期再做一次同样兜底，避免空值抛"模型选择错误"。
- `KbFlowNodeConvert.java:37-45`：当 `data.config` 缺失时，把 `data` 根上的标量键（label/toolName/content/fields 等）扁平化进 config → 修正了模板 Tool/Reply/Start 节点"配置在 data 根、运行期读 config"的错位。
- 模板 LLM 节点同时写 `data.provider/model/prompt` 与 `data.config.provider/model/promptTemplate`（`KB/service/flow/impl/KbFlowTemplateServiceImpl.java:181-197`）。

**未修复（关键，导致仍不可执行）【已验证】**

1. **prompt 键仍错位（P0 阻断）**：SQL 只写 `promptTemplate`（4 个 LLM 节点有、6 个 LLM 节点连 promptTemplate 都没有；全文件 `"prompt"` 出现 0 次），模板 config 也只写 `promptTemplate`（`KbFlowTemplateServiceImpl.java:194`）。而运行期读取的是 `configJson.getString("prompt")`（`KB/service/flow/bo/LLMNodeBO.java:100,103`），随后 `super.format(promptFormat, context)`（`:101,104`）→ `BaseNodeBO.format` 第一行 `pattern.matcher(str)`（`KB/service/flow/bo/BaseNodeBO.java:157-158`）在 `str=null` 时抛 NPE → 被 `BaseNodeBO.execute` 捕获（`:144-148`）→ LLM 节点返回 ERROR、整条工作流终止。⇒ **种子 SQL 与新建 Bot 模板的 LLM 节点仍然全部不可执行**。
2. **hardcoded provider=1L 无环境保证**：兜底固定 keyId=1（`KbFlowNodeConvert.java:47-49`、`LLMNodeBO.java:50-53`）。PostgreSQL 参考数据里 `ai_api_key` id=1 是 DeepSeek 但 **api_key 列为空**（`deploy/sql/postgresql/02-init-data.sql:50-51` 未插 api_key），`AiModelApiServiceImpl.getChatModel` 会抛"API Key 未配置"（`backend/qknow-module-ai/.../AiModelApiServiceImpl.java:52-58`）；MySQL 参考数据里 id=1 是"通义千问"（`deploy/docker/database/mysql/init/init.sql:53`）→ 若走 MySQL 部署则路由到非 DeepSeek 平台，违反"生成侧唯一 DeepSeek"约束。⇒ provider=1L 是环境相关魔数，不是可靠修复；且忽略租户/工作区，存在越权用全局 key 的风险。
3. **condition 节点 schema 仍不匹配（未被本提交触碰）**：SQL 条件节点写 `cases:[{...,condition,...}]`（`SQL:43,46`），运行期读 `config.conditions[].expression`（`KB/service/flow/bo/ConditionNodeBO.java:40,50`）→ 条件节点找不到条件，返回 success 且 `nextNodeIds` 为空，`BaseNodeBO.findNextNodes` 回退为**全部出边**（`BaseNodeBO.java:97-108`）→ 两条分支都会执行、剪枝永不生效。模板虽无 condition 节点，但 SQL 侧 Bot 9 的因果路由仍失效。
4. **knowledge 节点仍无有效库 ID**：SQL knowledge 配置缺少 `knowledgeBaseId*`（`SQL:31-36`、`:343-344`），运行期走硬编码默认 `7L`（`KB/service/flow/bo/KnowledgeNodeBO.java:75`），而 PostgreSQL 参考数据只插入 4 个知识库且无 id=7 保证（`deploy/sql/postgresql/04-sample-data-local.sql:165-177`，serial 自增）→ `recallTest(7,...)` 大概率失败；模板 `knowledgeBaseId:null`（`KbFlowTemplateServiceImpl.java:206`）更糟：`containsKey` 为真 → `kbIds.add(null)`（`KnowledgeNodeBO.java:59-60`）→ 以 null 调用 recallTest（`KnowledgeNodeBO.java:117`）→ 失败。
5. **种子修复无迁移/部署钩子**：`SQL` 未被任何构建/部署脚本引用（全仓 grep `init_all_bot_workflows` 无结果），仓库无 Flyway/Liquibase；现有数据库不会自动获得新种子，需人工执行整份脚本（脚本会先 DELETE bots 1-9 再插入）。且脚本内 `provider:1` 为环境相关值。

6. **新建 Bot 模板初始化必然失败（新增回归，高置信）**：`KbFlowTemplateServiceImpl.initBotWorkflowByTemplate` 只 `flowVO.setBotId(botId)`（`KB/service/flow/impl/KbFlowTemplateServiceImpl.java:49-50`），从未设置 workspaceId；`KbFlowNodeConvert.toDOList` 把 null 赋给 DO（`KB/convert/flow/KbFlowNodeConvert.java:79-80`），MyBatis-Plus `insertStrategy: NOT_NULL`（`backend/qknow-server/src/main/resources/application.yml:175`）会省略该列，而 `kb_flow_node/kb_flow_edge.workspace_id` 是 `NOT NULL`（`deploy/sql/postgresql/01-schema.sql:836,859`）→ INSERT 必然违反非空约束。由于模板服务带 `@Transactional(rollbackFor=Exception.class)`（`KbFlowTemplateServiceImpl.java:31-33`）且与 `createKbBot` 的类级事务（`KB/service/bot/impl/KbBotServiceImpl.java:33`）共用同一事务，内层异常会把事务标记 rollback-only；`createKbBot` 的 catch 吞掉异常后（`:70-73`）外层提交抛 `UnexpectedRollbackException` → **新建 Bot 接口整体失败（不是"半态"）**，且原 bot 插入也回滚。【推断（Spring 声明式事务标准语义）；未运行验证，需一次真实建 Bot 复现】
7. **reply 输出映射与有效位仍未修**：全部 reply 节点 DO 列 `output='[]'`（`SQL:89,144,189` 等），BLOCK 模式回复为空（`KB/service/flow/bo/ReplyNodeBO.java:37-60`）；所有节点 `valid_flag=0`（如 `SQL:30,49` 等插入值），而 DDL 默认 `TRUE` 且既有种子用 1（`deploy/sql/postgresql/01-schema.sql:845`），当前查询未过滤 valid_flag，但任何后续按有效位过滤都会令种子流整体失效。

**验证方式**：在配置好 id=1 的 DeepSeek api_key 的 PostgreSQL 上导入 `SQL`，执行 Bot 9 正式流程：预期在 `llm_deep_1` 或 `llm_fast_1` 报错（NPE/节点失败）；把 `promptTemplate` 改名为 `prompt` 后重跑，应恢复；同时对 Bot 9 断言只执行一条 LLM 分支（验证 condition schema 仍是坏的）。新建 Bot 后立即执行其模板流程，同样应失败。

### 1.5 P1-Tool2 工具失败语义：**未修复（HEAD 残留）+ 新增回归（工作区）**

**提交 2f0861a0 已做的改进【已验证】**

- adapter 为 null → `NodeRunResultBO.failure`；工具返回 null → failure；工具结果 JSON 含 `isError=true` 或 `status=ERROR/FAILURE` → failure（提交版本 diff；当前工作区 `KB/service/flow/bo/ToolNodeBO.java:145-156` 保留该检查）。

**HEAD 仍残留的 P1（新发现）【已验证】**

- Hermes MCP 适配器对"工具不存在/未连接/参数非法/客户端未连接"统一返回**只有 `error` 字段**的 JSON：`HC/tool/mcp/McpToolAdapter.java:321`（Tool name empty）、`:328`（invalid code）、`:353`（No connected MCP server）、`:365`（参数校验失败）、`:373`（MCP 工具未连接）。
- ToolNodeBO 的失败判定只检查 `isError` 与 `status`（`KB/service/flow/bo/ToolNodeBO.java:146-148`），**不检查 `error` 字段** → 上述所有失败 JSON 都被当成成功：`:165-170` 构造 `SUCCESS` 结果。⇒ 在 HEAD 上，工具未注册/MCP 未连接时节点仍"成功"，只是 `output.result` 里带着 error JSON，下游 LLM 会把错误当结果使用。
- 当前装配下该路径是**真实可达**的：`HermesConfiguration.java:31-32` 创建了空的 `McpToolAdapter`（无注册 server），ToolNode 任何工具调用都会得到 `{"error":"No connected MCP server found..."}` → 判 SUCCESS。

**工作区新增回归【已验证；git diff 直接证明】**

- 当前 `KB/service/flow/bo/ToolNodeBO.java:131-135`：adapter 为 null 时不再 failure，而是构造 `{"status":"SUCCESS","tool":"...","message":"Executed in standalone mock mode"}`（`:133-134`），只有 `log.debug`；该串 status=SUCCESS 会通过 `:146-148` 的检查 → `:165-170` 返回 SUCCESS。提交信息里"杜绝未执行成功伪造"的目标被工作区直接回退。
- 可达性：【推断】当前默认装配下 `HermesConfiguration` 提供 `McpToolAdapter` Bean（`@ConditionalOnMissingBean`），所以 mock 分支平时不触发；但一旦该 Bean 因条件不满足/被覆盖/单元测试上下文缺失而为 null，就会静默伪成功。且它使"adapter 缺失"这一最严重不可用状态从 fail-fast 变回 fail-open，属于必须判定的**新增回归（代码语义层）**。

**验证方式**：单元/集成测试中注入 `mcpToolAdapter=null`，断言节点结果为 ERROR（现状失败）；注入空 `McpToolAdapter`，调用 `executeTool("不存在的工具")`，断言结果为 ERROR（现状也会失败）。

---

## 2 附加检查：新表/新依赖/迁移/注入/API 契约/回归

| 检查项 | 结论 | 证据 |
|---|---|---|
| 新数据库表 | **无**（复用 `dag_checkpoints`，仍由代码 `CREATE TABLE IF NOT EXISTS` 自动建） | `HC/flow/dag/DagCheckpointManager.java:495-528`；需要 DB DDL 权限，权限不足时 `log.debug` 吞掉（`:525-527`） |
| 新依赖 | **无**（Redis 依赖原本已在 module-kb/hermes-core；`2f0861a0` 无 pom 改动） | `git show 2f0861a0 --stat` |
| 迁移脚本/部署钩子 | **缺失**：`SQL` 无任何构建/部署引用，无 Flyway/Liquibase | 全仓 grep `init_all_bot_workflows` 无结果；`grep -rln flyway\|liquibase backend deploy` 无结果 |
| SQL 注入 | **无新增**：无新增 SQL 拼接；Redis `scanKeys` 用常量前缀 + SCAN | `ApprovalProxyController.java:37`；`RedisServiceImpl.java:53-58` |
| API 契约 | `/hermes/approval/pending` 返回项从纯内存 key 拆解变为 Redis JSON（字段更多，核心字段兼容）；approve/reject 请求/响应未变；新增内部接口 `IKbFlowTemplateService` 无外部破坏；`createKbBot` 行为变更（创建后自动初始化模板流程） | `ApprovalProxyController.java:29-75`；`KB/service/flow/IKbFlowTemplateService.java:14-32`；`KB/service/bot/impl/KbBotServiceImpl.java:64-78` |
| 新回归风险 | ①`createKbBot` 模板初始化因 `workspaceId=null` 触发 NOT NULL 违反，内层事务标记 rollback-only → 外层 commit 抛 `UnexpectedRollbackException`，**新建 Bot 失败**（`KbBotServiceImpl.java:33,64-78`；`KbFlowTemplateServiceImpl.java:31-33,49-50`；`KbFlowNodeConvert.java:79-80`；`01-schema.sql:836,859`；`application.yml:175`）；②`McpToolAdapter` Bean 的 `@PostConstruct` 在控制面启动时可能异步注册 GitHub MCP（`McpToolAdapter.java:88-103`）；③P0-10 激活检查点 DDL/二次挂起缺陷 | `KbBotServiceImpl.java:33,64-78`；`KbFlowTemplateServiceImpl.java:31-33`；`McpToolAdapter.java:88-103`；`DagCheckpointManager.java:56,290-294,495-528` |
| 测试覆盖 | **零新增测试**；现有 resume 测试用内存版 `new DagCheckpointManager()`（无 JDBC），HITL/Redis 无测试 | `git show 2f0861a0 --name-status`（无 test）；`backend/qknow-module-kb/.../KbFlowResumeCheckpointTest.java:57` |

---

## 3 与 `02-flow-fix.md` 的比对结果

写作期间第一路报告已生成（`docs/architecture-review-20261019/verify/02-flow-fix.md`，19:04），逐条比对如下。

**实质一致（4/5 项，无方向性冲突）**：

- **P0-09**：双方均判「部分修复」——Redis 键/TTL/读写成立，但生产无生产者（`ApprovalNodeExecutor.execute` 仅测试调用）、决策无消费者、审批后无自动恢复、审批可凭上下文变量绕过；双方独立确认 Hermes 侧外置失效（本路证据：`SpringUtils` 不在 Hermes 扫描包，`HermesApplication.java:17-21`；对方补充：Hermes 无 redis 配置且部署编排未注入 REDIS_HOST，我方复核属实）。
- **P0-10**：双方均判「已修复（当前工作区），归因 `f4ff4dda` 而非 2f0861a0」，均把 `JdbcTemplate` 可得性标为高置信静态推断，并共同列出 status 只在 INSERT 写、lease 恒 NULL、每次 8 条 DDL、异常静默吞掉等残留。
- **P0-11**：双方均判「部分修复」——起始层 G、ERROR 重跑、剪枝集重建已修；层内崩溃整层重跑/无幂等/无流程版本校验/`wakeSuspendedWithLock` 返回值被丢弃/`messageList` 不恢复等残留一致。
- **P0-12**：双方均识别 `promptTemplate`（SQL/模板）vs `prompt`（运行期 `LLMNodeBO.java:100-104`）错位导致首个 LLM 节点 NPE，均判「部分修复」。对方补充了 `workspaceId=null` → NOT NULL 违反导致模板初始化失败；本路独立复核属实（`KbFlowTemplateServiceImpl.java:49-50`、`KbFlowNodeConvert.java:79-80`、`deploy/sql/postgresql/01-schema.sql:836,859`、`application.yml:175`）。

**判定标签差异（1 处，实质相同）**：

- **P1-Tool2**：对方判「新增回归（工作区）+ HEAD 部分修复」；本路判「**未修复（HEAD 残留 error-only JSON 仍伪成功）+ 新增回归（工作区 mock SUCCESS）**」。双方对事实（工作区把 failure 改回 mock SUCCESS；HEAD 对 `{"error":...}` 仍判 SUCCESS）完全一致，差异仅在"HEAD 那部分算部分修复还是未修复"的口径。本路取更严格口径：只要生产可观测行为仍是"失败标成功"，就不算已修复。

**本路追加、对方未展开的判断（3 点）**：

1. **P0-12 严重度升级为"新增回归"**：由于模板服务 `@Transactional` 与 `createKbBot` 类级事务共用一个事务，`workspaceId=null` 造成的 NOT NULL 异常会把事务标记 rollback-only；`createKbBot` 的 catch 吞掉异常后，外层提交将抛 `UnexpectedRollbackException` → **新建 Bot 接口整体失败**（bot 插入一并回滚），不只是对方描述的"半态/无默认流程"（`KbBotServiceImpl.java:33,64-78`；`KbFlowTemplateServiceImpl.java:31-33`）。【推断（Spring 事务标准语义），需一次真实建 Bot 复现】
2. **provider=1L 是环境相关魔数**：PG 参考数据 `ai_api_key` id=1 的 api_key 为空（`deploy/sql/postgresql/02-init-data.sql:50-51`）→ `getChatModel` 抛"API Key 未配置"；MySQL 参考数据 id=1 是"通义千问"（`deploy/docker/database/mysql/init/init.sql:53`）→ 路由到非 DeepSeek 平台，违反"生成侧唯一 DeepSeek"约束；即 prompt 修好后仍可能失败/违规。
3. **P0-10 修复的启动副作用**：控制面新增的 `McpToolAdapter` Bean 会在 `@PostConstruct` 检测 `GITHUB_PERSONAL_ACCESS_TOKEN` 并异步注册 GitHub MCP（`McpToolAdapter.java:88-103`），属装配修复引入的新暴露面；另 `DagCheckpointManager` 二次挂起 CAS 失败被静默忽略（`KbFlowServiceImpl.java:544`），"唤醒"退化为"重跑"。

**比对结论**：两路在 5 个核心判定上无方向性冲突，可合并采纳；建议 Lead 取更严格口径（P0-12 记为"部分修复+新增回归"、P1-Tool2 记为"未修复+回归"），并把 `workspaceId` / `prompt` 键 / mock SUCCESS 三项列为立即修复项。

## 4 结论与建议

- 本轮 5 项中：**1 项已修复（P0-10，但非本提交）**、**3 项部分修复（P0-09/P0-11/P0-12；其中 P0-12 附带新增 P0 回归）**、**1 项未修复且工作区新增回归（P1-Tool2）**。
- 修复方向总体正确（Redis 外置、恢复起始层/重跑/剪枝、provider 兜底、工具失败判定），但存在"改到一半"的系统性问题：**生产接线缺失（HITL）、键名契约未对齐（prompt）、错误形态判定不全（MCP error JSON）、工作区回退（mock SUCCESS）**。
- 优先级建议（最小改动）：
  1. 立即回退工作区 `ToolNodeBO.java:131-135` 的 mock SUCCESS（恢复提交中的 failure）；并在 `:146-148` 增加 `resObj.containsKey("error")` 的失败判定。
  1b. 立即修 `KbFlowTemplateServiceImpl` 的 `workspaceId` 缺失（接口签名传入 workspaceId，或复用 bot 记录的 workspaceId），否则新建 Bot 主链可能整体失败（§1.4-6/§3 差异点 1）。
  2. 把 SQL/模板的 `promptTemplate` 统一改为 `prompt`（或运行期兼容两者），否则 P0-12 不能算修复；同步补 condition `cases`/`conditions` 兼容与 knowledgeBaseId 种子。
  3. HITL 真正闭环：ToolNode 挂起时写 Redis pending（或直接调 ApprovalNodeExecutor），`/hermes/approval/approve` 写入决策后由 Decision 消费者自动触发 `resumeFlow(runtimeId, decision)`；加入审批人/租户/一次性仲裁（`setNx` 或 Lua）。
  4. 检查点：把 `ensureTableExists()` 移到启动一次；修正 status 生命周期（恢复成功后回写/删除），或在 CAS 失败时显式报错而非静默重跑。
  5. 增补最小测试：JDBC 检查点 save/load、失败层 resume 重跑一次、二次挂起恢复、ToolNode error-only JSON 判失败、HITL Redis 跨 JVM 读写。当前零新增测试是最大验收缺口。

---

## 附录 证据索引（路径:行）

- `HC/flow/node/ApprovalNodeExecutor.java:28-31,34,59-79,95-127,131-163,175-181`
- `KB/controller/admin/ApprovalProxyController.java:25-26,29-75,82-100,103-129`
- `KB/service/flow/bo/ToolNodeBO.java:102-119,131-135,145-156,165-170`
- `HC/tool/mcp/McpToolAdapter.java:88-103,321,328,353,365,373`
- `SRV/config/HermesConfiguration.java:14-15,29-33,35-39`
- `SRV/QKnowApplication.java:21-26`
- `HC/flow/dag/DagCheckpointManager.java:52-92,80-86,290-294,495-528`
- `KB/service/flow/impl/KbFlowServiceImpl.java:74-75,354-362,366-367,369,378-384,413-421,471-478,485-514,517-560,603-621`
- `KB/convert/flow/KbFlowNodeConvert.java:37-52`
- `KB/service/flow/bo/LLMNodeBO.java:49-56,100-104`
- `KB/service/flow/bo/BaseNodeBO.java:97-108,157-158`
- `KB/service/flow/bo/ConditionNodeBO.java:40,50`
- `KB/service/flow/bo/KnowledgeNodeBO.java:59-61,75,117`
- `KB/service/flow/impl/KbFlowTemplateServiceImpl.java:31-33,181-197,206,243,251`
- `KB/service/bot/impl/KbBotServiceImpl.java:33,64-78`
- `SQL:31-36,43,46,54,57,76,79,343-344`（10 个 LLM 节点有 provider；promptTemplate 仅 4 节点；`"prompt"` 0 次）
- `deploy/sql/postgresql/02-init-data.sql:50-51`；`deploy/sql/postgresql/04-sample-data-local.sql:165-177`；`deploy/docker/database/mysql/init/init.sql:53`
- `backend/qknow-module-ai/.../AiModelApiServiceImpl.java:52-58`
- `backend/qknow-framework/qknow-redis/.../RedisServiceImpl.java:38-40,53-58,147-150`
- `backend/qknow-framework/qknow-common/.../SpringUtils.java:18-19`
- `backend/qknow-hermes/qknow-hermes-starter/.../HermesApplication.java:17-21`
- `deploy/docker/docker-compose-qknow.yml:24`；`backend/qknow-server/src/main/resources/application-dev.yml:6,100`
- `backend/qknow-module-kb/.../KbFlowResumeCheckpointTest.java:57,184-189`；`backend/tests/.../ReactiveWorkflowContractTest.java:104`
- `git show 2f0861a0 --name-status`（10 文件、无测试）；`git log --oneline -- SRV/config/HermesConfiguration.java`（f4ff4dda、a168f5d8）
