# 第二轮验收报告：Flow / HITL / Checkpoint（基线 `1ff190d0`）

> 复核范围：第二轮修复提交 `54fea59b`（深度闭环，2026-10-20 19:56）与 `1ff190d0`（CI 修复，2026-10-20 20:49）中 Flow / HITL / 工作流侧改动
> 基线：`HEAD = 1ff190d0`，`git status` 工作区 clean（仅 `docs/architecture-review-20261019/acceptance/` 为其他复核员新建目录）
> 被核对的自证材料：`docs/architecture-review-20261019/verify/06-deep-fix-resolution.md`（该文档与 `54fea59b` 同一提交入库，早于 `1ff190d0`）
> 关联基线：`verify/00-VERIFICATION-MASTER.md`（R2/R4/R10 与合并门禁）、`verify/02-flow-fix.md`（第一轮 Flow 复核）
> 纪律声明：本次复核**纯只读**，未修改任何源码/配置/SQL/测试，未执行 `mvn`/`gradle`/测试/启动服务；唯一写入本报告。

---

## 0 复核方法

1. **先看两个提交的完整 diff**：`git show 54fea59b`（41 文件）与 `git show 1ff190d0`（4 文件），逐文件比对；重点核对 `KbFlowTemplateServiceImpl`、`KbBotServiceImpl`、`LLMNodeBO`、`ConditionNodeBO`、`BaseNodeBO`、`ApprovalProxyController`、`ApprovalNodeExecutor`、`KbFlowServiceImpl`、`DagCheckpointManager`、`ToolNodeBO`。
2. **再读最终文件**：所有结论以 `1ff190d0` 的最终工作区文件为准；对 `1ff190d0` 的删除/回退逐行核对，因为它会覆盖 `54fea59b` 的修复结论。
3. **装配与调用链核对**：`KbBotServiceImpl` 事务注解、`IKbFlowTemplateService` 注入代理、`BaseController.getWorkSpaceId()` 反射目标是否存在、`HermesConfiguration` 是否提供 `McpToolAdapter`/`DagCheckpointManager`、`SpringUtils` 在控制面是否可用。
4. **测试证据核对**：逐一确认 `54fea59b`/`1ff190d0` 是否新增/修改 Flow 测试；核对 `06-deep-fix-resolution.md` 引用的 `FlowExecutorTest` 是否与 R2/P0-12 相关。
5. **只做静态推导的地方显式标注**：所有需要真实 DB/Redis/容器才能确认的结论标注为"高置信静态推断"。

判定口径：目标失败路径在生产真实代码中消失才判"已修复"；只接通一段链路/只增加兼容层但主链仍断判"部分修复"；把已修复项改回旧行为判"新增回归"。

---

## 1 逐项裁决表

| # | 复核项 | 判定 | 一句话结论 | 关键证据 |
|---|---|---|---|---|
| 1 | **R2 Bot 创建事务/模板初始化** | **部分修复** | `workspaceId` 显式透传 + `REQUIRES_NEW` 已消除原 R2 的 `UnexpectedRollbackException` 主因（内层失败不再污染外层），HTTP 主路径可落库；但 `REQUIRES_NEW` 看不到外层未提交的 bot 行（`botMapper.selectById` 必空）、外层回滚会留孤儿节点、`createKbBot` 仍吞异常、`getWorkSpaceId()` 实际恒为 1001、模板默认流仍不可真正执行 | `KbFlowTemplateServiceImpl.java:32-34,38-61,74-77`；`KbBotServiceImpl.java:32-34,63-76`；`KbBotController.java:93`；`BaseController.java:220-271`；`01-schema.sql:834-854` |
| 2 | **P0-12 prompt / condition 契约** | **部分修复** | `resolvePrompt` 三级回退 + `format` null 守卫已消除 NPE；`ConditionNodeBO` 能解析种子 SQL 的 `cases`/`condition`/`query.length`/`兜底分支`；但**种子 SQL 本身仍未同步**（仍用 `promptTemplate`/`cases`、reply output 为空、knowledge 无 kbId），模板 start 节点无输出定义导致变量无法替换，条件在变量缺失时会误判 | `LLMNodeBO.java:89-106,116-143`；`BaseNodeBO.java:157-176`；`ConditionNodeBO.java:39-84,119-155`；`init_all_bot_workflows.sql:43,54,84-92,121`；`KbFlowTemplateServiceImpl.java:193-206,229-266` |
| 3 | **P0-09 HITL 闭环** | **部分修复** | 生产挂起路径已真正写 Redis 待办（`registerApprovalPendingIfSuspended`），approve 后能异步 `resumeFlow`，`DEL` 抢占阻断并发双批准；但审批仍可被 `humanInput.approvalApproved` 完全绕过，审批/恢复端点无 `@PreAuthorize`、无租户/归属校验，审批单"先消费后恢复"失败无补偿，并行层多挂起节点可触发多个 resume | `KbFlowServiceImpl.java:397-407,457-472,773-813`；`ApprovalProxyController.java:20-23,88-150`；`ApprovalNodeExecutor.java:95-126`；`ToolNodeBO.java:102-108`；`KbFlowController.java:109-116` |
| 4 | **P0-11 检查点 DCL / CAS** | **部分修复（新增语义回归）** | DCL 建表有效，消除了每层 8 条 DDL；但 `status = EXCLUDED.status` 因 INSERT 硬编码 `'SUSPENDED'`，导致**任何一次保存都把 status 重置为 SUSPENDED**，RUNNING 永不落库 → CAS 屏障与租约自愈（`findExpiredRunningCheckpoints`）实质失效；自证报告写的 `ON CONFLICT (flow_id, checkpoint_id)` 与实际代码不符 | `DagCheckpointManager.java:35,37-40,53-94,81,86,249-276,282-308,435-450,497-532` |
| 5 | **`1ff190d0` 回归 A：ToolNodeBO 伪成功** | **新增回归（确认）** | `54fea59b` 已把 `adapter==null` 改为 fail-close，`1ff190d0` 又改回 `{"status":"SUCCESS",...,"Executed in standalone mock mode"}` 伪成功；生产主链因 `HermesConfiguration` 供给非空 adapter 通常不走该分支，但这是**生产类中的 mock 成功**，且回退动机是为迁就两个旧测试（契约测试反向绑架复现） | `ToolNodeBO.java:126-135`；`git show 1ff190d0` ToolNodeBO hunk；`KbFlowServiceToolNodeTest.java:23-42,44-68`；`Phase154MasterContractTest.java:311-341`；`HermesConfiguration.java:29-33` |
| 6 | **`1ff190d0` 回归 B：resumeFlow 删除 wake 守卫** | **新增回归（确认）** | `1ff190d0` 删除了 `wakeSuspendedWithLock` 失败后的早退分支，当前 `resumeFlow` 无任何运行状态/并发检查、无锁、无幂等；配合 P0-11 的"status 恒 SUSPENDED"，并发/重复 resume 与并行审批会**重复执行工作流**（重复 LLM 计费/工具副作用） | `KbFlowServiceImpl.java:523-551,608-627`；`git show 1ff190d0` KbFlowServiceImpl hunk；`ApprovalProxyController.java:99-119`；`DagCheckpointManager.java:81,86,292-296` |
| 7 | **新增/修改测试覆盖** | **未满足合并门禁** | `54fea59b` 的测试改动只有 DeepSeek/Phase46/Phase17 三处，**Flow/HITL/Checkpoint/模板/Prompt/Condition 零新增测试**；自证报告用 2026-07-03 就存在的 Hermes `FlowExecutorTest` 冒充 R2/P0-12 的验证；`1ff190d0` 未新增任何测试，反而回退实现迁就旧测试 | `git show 54fea59b --name-only`；`FlowExecutorTest` 最后修改 `7ec7e243`(2026-07-03)；`06-deep-fix-resolution.md:19,27,143`；`00-VERIFICATION-MASTER.md:107,170` |

---

## 2 详细裁决

### 2.1 R2：Bot 创建事务与模板初始化

**已确认修好的部分**

- 接口新增 4 参重载并保留 3 参默认方法（`IKbFlowTemplateService.java:24-37`），`KbBotServiceImpl` 改为传入 `dictType.getWorkspaceId()`（`KbBotServiceImpl.java:70`），控制器在入口设置 `kbBot.setWorkspaceId(super.getWorkSpaceId())`（`KbBotController.java:93`）。
- 实现显式 `flowVO.setWorkspaceId(resolvedWorkspaceId)`（`KbFlowTemplateServiceImpl.java:74-77`），`KbFlowNodeConvert.toDOList` 会把该值写入节点/边实体，原先必现的 `kb_flow_node.workspace_id` NOT NULL 违例被消除（DDL：`deploy/sql/postgresql/01-schema.sql:836`）。
- 方法改为 `@Transactional(propagation = REQUIRES_NEW)`（`KbFlowTemplateServiceImpl.java:33`）。原 R2 的根因是"内层 `@Transactional` 失败把共享事务标记 rollback-only，外层提交抛 `UnexpectedRollbackException`"（`00-VERIFICATION-MASTER.md:29`）；`REQUIRES_NEW` 挂起外层事务后，内层失败只回滚内层，外层可正常提交，**该机制确认修复**。

**残留缺陷**

1. **`REQUIRES_NEW` 与未提交 bot 行的可见性**：`KbBotServiceImpl` 类级 `@Transactional`（`:32-34`），因此 `createKbBot` 的 `baseMapper.insert` 在外层未提交事务中（`:66`）。内层独立事务里的 `botMapper.selectById(botId)`（`KbFlowTemplateServiceImpl.java:40-49`）在 READ COMMITTED 下**看不到未提交行**，必然返回 null；该"多级安全解析"的查库分支在当前事务结构下是死代码。主路径靠显式入参规避，所以 NOT NULL 修复成立；但一旦 `workspaceId` 为 null（走 3 参默认重载或内部调用），会落到 `SecurityUtils.getDeptId()`（把 deptId 当 workspaceId，语义错误，`:52-55`）或硬编码 `1001L`（`:58-60`），产生跨租户/错误租户数据。
2. **孤儿节点风险**：内层 `REQUIRES_NEW` 先提交节点与边（`KbFlowTemplateServiceImpl.java:97-99`），外层事务随后才提交 bot 行。若外层在提交阶段回滚（或未来在 `createKbBot` 尾部追加任何可能抛错/标记回滚的逻辑），将留下 `bot_id` 指向不存在记录的 `kb_flow_node/kb_flow_edge` 孤儿数据；`kb_flow_node` 无外键、无清理/补偿逻辑，`deleteKbBot` 只按 botId 删节点，孤儿永远不会被回收。这是跨事务边界的原子性缺口，**没有任何测试覆盖**。
3. **`createKbBot` 仍吞异常**（`KbBotServiceImpl.java:69-73`）：模板初始化失败时仅 `log.warn`，外层照常提交 → Bot 存在但无流程（半态），API 返回成功；第一轮 F-P2-23 的缺陷保留。`REQUIRES_NEW` 只是让"失败"不再升级为创建失败，并不保证"有流程"。
4. **`dictType.getWorkspaceId()` 并不可靠（作为租户 ID）**：`BaseController.getWorkSpaceId()` 通过反射加载 `tech.qiantong.qknow.security.utils.SecurityFrameworkUtils`（`BaseController.java:227-229`）。全仓**不存在该类**（全仓 grep 仅命中该字符串本身），`LoginUser` 也没有 `workspaceId` 字段（`LoginUser.java:23-73`）。反射必抛 `ClassNotFoundException` → catch 后 `defaultWorkspaceId` 永远是 `1001L`；请求头只有在等于 1001（或 `isAdmin`，而 `isAdmin` 同样取不到登录用户恒 false）时才被接受。因此：**R2 的 NOT NULL 修复有效（值恒非空），但 R6 宣称的"租户校验"实际是无效实现**，新建 Bot 及其模板流程恒落 workspace=1001。
5. **模板默认流仍不可真正执行**（创建成功 ≠ 可用）：
   - `createStartData` 只写 `fields`（`KbFlowTemplateServiceImpl.java:193-206`），`KbFlowNodeConvert` 只读 `data.input/data.output`（`KbFlowNodeConvert.java:63-64`）→ start 节点 input/output 为空，`start_1.query` 永远不会写入上下文；而模板 LLM/知识 prompt 全用 `{{ start_1.query }}`（`:136,146,155-156`）→ `BaseNodeBO.format` 对空值直接 `continue`（`:165-176`），占位符原样留在提示词里。
   - 知识节点 `knowledgeBaseId` 显式写 null（`:234`）→ `kbIds=[null]` → 并发检索器过滤 null 后返回空（`MultiKnowledgeConcurrentRetriever.java:106-120`）→ 模板 RAG 恒"未检索到"。
   - Agent 模板的工具名是占位符"示例工具"（`:271`），控制面 `McpToolAdapter` 无任何 Server（`executeTool` 返回 `{"error":"No connected MCP server found..."}`），`ToolNodeBO` 已拦截 `error` 键判失败（`ToolNodeBO.java:145-157`）→ Agent 模板在工具节点必失败。
   - reply 节点只写 `content`，不写 output 定义（`:276-281`）；WORK_FLOW 模式 `ReplyNodeBO` 按 output 定义取值（`ReplyNodeBO.java:43-52`），空定义 → 运行 output 为 `{}`；仅 CHAT_FLOW 分支能借 `config.content` 工作。

**建议验证**：集成测试 `createKbBot`（mock mapper/事务）断言 bot 与 node 的 workspaceId 一致；模拟外层回滚断言孤儿节点；以 `tpl_rag_qa/tpl_chat/tpl_agent_tool` 建 Bot 后调用 `executeFlowStream` 断言各节点结果；构造 `workspaceId=null` 的 3 参调用断言实际写入值（预期暴露 deptId/1001 混用）。

### 2.2 P0-12：Prompt 与 Condition 契约

**已确认修好的部分**

- `LLMNodeBO` 新增 `resolvePrompt`：`prompt → promptTemplate → systemPrompt → 默认安全提示词`（`LLMNodeBO.java:89-106`），`getMessageArray` 改用该结果并在 `inputDefArray` 空/非空两路都先加 SystemMessage（`:116-143`）；`BaseNodeBO.format` 对 null 字符串与 null context 做了守卫（`BaseNodeBO.java:157-163`）。**第一轮"首个 LLM 节点必 NPE 失败"的直接原因已消除**。
- `ConditionNodeBO` 已兼容：`conditions` 与 `cases` 双数组（`:39-43`）、`expression` 与 `condition` 双字段（`:57-60`）、`targetHandle` 与 `id` 回退（`:61-64`）、`isElse/default/else/兜底分支`（`:66-74`）、`.length` 属性展开（`:123-134`），并保留 `==/!=/>=/<=/>/<` 比较与字符串回退（`:137-155,165-192`）。种子 SQL 的 `cases + condition:"query.length > 20" + 兜底分支 + targetHandle` 形态在代码层面**可被正确解析**。

**残留缺陷**

1. **种子 SQL 未同步**：`backend/init_all_bot_workflows.sql` 最后一次修改仍是 `2f0861a0`（`git log` 可证），文件仍是 `promptTemplate`（`:54,135,180` 等）、`cases`（`:43`）、reply `output='[]'`（`:84-92` 等）、knowledge 节点无 `knowledgeBaseId`（`:121,173`）。运行期兼容层让 LLM/条件"能跑"，但数据本身没有按 master 要求对齐；且 reply 无输出映射、knowledge 落入 `KnowledgeNodeBO` 硬编码 `7L`（`:74-76`），`{{chunks}}`/`{{chunks_rule}}` 等提示词变量与知识节点实际输出键 `knowledge_1.text/context`（`KnowledgeNodeBO.java:152-158`）不匹配 → 种子流仍只"不崩"，不会给出正确答复。
2. **条件变量缺失时误判**：`evaluateExpression` 的 `.length` 展开依赖 `context.variables` 中存在同名 key（`:123-134`）。若变量缺失（如 `executeFlow` 输入未带 `query`），表达式保持 `query.length > 20`，随后走 `>` 分支（`:149-151`），`Double.parseDouble` 失败后转字符串比较（`:179-191`）——`"query.length" > "20"` 比较首字符 `'q'(113) > '2'(50)` 恒真 → **误入 deep 分支而非兜底分支**。这是一个可复现的边界缺陷，兼容层没有覆盖"变量缺失"路径。
3. **模板 prompt 键补齐但变量仍不闭环**：`createLlmData` 已同时写 `config.prompt` 与 `config.promptTemplate`（`:217-225`），配合 `resolvePrompt` 可正常进入模型调用；但如 2.1 第 5 点所述 `{{ start_1.query }}` 无法解析，模板实际对话质量仍不可用。

**建议验证**：为 `ConditionNodeBO` 加单测：`cases + query.length`（变量存在/不存在两组）、`兜底分支`、`cases` 空、`targetHandle` 仅 id；为 `LLMNodeBO` 加单测：仅 `promptTemplate`、仅 `systemPrompt`、三者皆无（默认提示词）、`inputDefArray=null`；种子 SQL 建库后对 bot9 跑一次真实链路（需 PG+Redis+模型 Key，属受控集成）。

### 2.3 P0-09：HITL 闭环

**已确认接通的部分**

- 生产挂起分支真正写 Redis 待办：单节点挂起 `KbFlowServiceImpl.java:397-407`、并行层挂起 `:457-472`，均调用 `registerApprovalPendingIfSuspended`；该方法构造 `approvalKey = botId:runtimeId:nodeId`、`requestId = runtimeId`，以 24h TTL 写入 `hermes:approval:pending:` 前缀（`:773-813`）。这是第一轮"控制面无生产者"的实质修复。
- `ApprovalProxyController.approve` 审批成功后解析 `requestId` 为 runtimeId，构造 `approvalResult=APPROVED/approvalApproved=true`，用 `CompletableFuture.runAsync` 调 `flowService.resumeFlow`（`:99-119`）；`reject` 把 runtime 置 ERROR（`:135-148`）。
- `ApprovalNodeExecutor.approve/reject` 改为"`DEL` 成功即抢占"（`:104-118,145-160`），并发双批准第二个会拿到 `deleted=false` → 404，**一次性消费语义成立**（单机 Redis 原子 DEL）。`getPending` 扫描 Redis 前缀（`ApprovalProxyController.java:42-66`），前端可列出待办。

**残留缺陷（P0 仍未闭环）**

1. **审批绕过通道仍在**：`resumeFlow` 把请求体 `humanInput` 原样 `putAll` 进变量（`KbFlowServiceImpl.java:555-563`），而 `ToolNodeBO` 的放行判据仍是 `context.variables.approvalResult=="APPROVED" || approvalApproved==true`（`ToolNodeBO.java:102-108`）。任何已登录用户可**不经过 approve、不消费任何审批单**，直接 `POST /kb/flow/resumeFlow {runtimeId, humanInput:{approvalApproved:true}}` 跳过 HITL。且审批自动恢复写入的 `approvalApproved=true` 会被 `saveDagCheckpoint` 持久化进 variables（`:491-519`），该工作流实例后续**所有**高危工具节点（包括未单独审批的）在同一 run 与再次 resume 中都自动放行，审批语义被扩大为"整实例白名单"。
2. **审批/恢复端点无鉴权、无租户/归属校验**：`ApprovalProxyController` 全文件 0 个 `@PreAuthorize`（类注解仅 `@RestController/@RequestMapping`，`:20-23`），`/pending` 不过滤 workspace，任何登录用户可列出并批准他人工单；`/dev/create` 无 profile 守卫（`:152-179`）；`KbFlowController.resumeFlow` 同样 0 个 `@PreAuthorize`（`:109-116`），`resumeFlow` 只按 `runtimeId` 取 runtime，无归属/租户校验 → 跨租户 IDOR。`SecurityConfig` 仅 `anyRequest().authenticated()`（`:156`），不提供细粒度保护。这与 master 要求的"审批人审计、`@PreAuthorize`"（`00-VERIFICATION-MASTER.md:169`）直接冲突。
3. **"先消费、后恢复"无补偿**：approve 先原子删除 pending，再异步 resume；resume 失败只 `log.error`（`ApprovalProxyController.java:108-115`）。此时审批单已消失、运行仍 SUSPENDED、无重试/回滚/死信，工作流只能再人工 `resumeFlow`，且因 HITL 门禁仍需 `approvalApproved` 才能通过（见第 1 点）——审批链路的失败面没有闭环。
4. **并行层多挂起节点 → 多 pending → 多次 resume**：并行层若同时挂起 2 个高危工具节点，会写 2 条 pending（`:457-472`）；用户分别批准两条时，两次 `resumeFlow` 并发执行，而 `resumeFlow` 无锁/无幂等（见 2.5），会产生重复执行。成功恢复后其余 pending 键也不清理（24h TTL），后续误批准会对已完成/运行中 runtime 再次 resume。
5. **reject 非终态**：`reject` 只把 `kb_runtime.status` 置 ERROR（`:135-148`），**不删除也不标记 checkpoint**（仍为 SUSPENDED），任何人随后调 `resumeFlow` 仍可让被拒绝的流程继续执行；同时 checkpoint 行无 TTL，拒绝后永久残留。
6. **跨 JVM 范围变化**：本轮的 pending/decision 均在控制面 Redis（db=3）中，KB 流程本身也在控制面执行，因此**单控制面/多控制面副本**（共享同一 Redis）可以闭环；但 Hermes JVM 无 Redis 配置、`ApprovalController` 仍读进程内 Map 的结构未变，第一轮"两套审批实现"仍未统一（对 KB 流程影响已降低）。

**建议验证**：MockMvc/单测断言"未审批直接 resumeFlow + approvalApproved=true"应被拒绝（当前会成功，暴露绕过）；断言 `/hermes/approval/pending|approve` 无权限注解（静态）；构造并行层两挂起节点分别 approve，断言 resume 只执行一次（当前预期两次）；reject 后调用 resume 断言应拒绝（当前会继续）。

### 2.4 P0-11：检查点 DCL 与 CAS

**已确认修好的部分**

- `ensureTableExists` 改为 `AtomicBoolean tableInitialized` + `synchronized` 双重检查（`DagCheckpointManager.java:35,497-532`），首次成功后不再进入 DDL；`@PostConstruct`（`:37-40`）完成启动初始化。第一轮"每次 save/load 执行 8 条 DDL"的性能/锁竞争问题**实质修复**。
- `ON CONFLICT (runtime_id)` 的 `DO UPDATE` 增加 `status = EXCLUDED.status`（`:78-89`），使二次挂起后再 `wakeSuspendedWithLock` 的 CAS（`:292-296`）能命中 `status='SUSPENDED'`；从"CAS 恒失败"角度，表面上修复了"二次挂起 CAS 失败"。

**新增/残留缺陷**

1. **`status` 被永久重置为 SUSPENDED（核心语义错误）**：INSERT 的 `VALUES` 把 status 硬编码为 `'SUSPENDED'`（`:81`），因此 `EXCLUDED.status` 恒为 `'SUSPENDED'`；`:86` 等于"每次保存都把 status 改回 SUSPENDED"。后果：
   - 执行中的工作流在 DB 里永远显示 SUSPENDED；`wakeSuspendedWithLock` 的 CAS 可被任意并发 resume 命中，**CAS 不再是并发屏障**；
   - `wakeSuspended` 唤醒成功后调 `saveCheckpointWithVariables`（`:272`），立刻又把状态写回 SUSPENDED → 唤醒完即"重新挂起"；
   - `findExpiredRunningCheckpoints` 查 `WHERE status='RUNNING' AND lease_expire_at < ...`（`:435-450`），因 status 永不等于 RUNNING 而**永不命中**，租约活性自愈彻底失效；`wakeSuspendedOrRecoverLease` 的 expired-RUNNING 分支（`:378`）同样失效。
   自证报告称"补齐 `DO UPDATE SET status = EXCLUDED.status` 解决二次挂起 CAS 失败"，实际是**以牺牲 RUNNING 状态换取 CAS 通过率**，代价大于收益。
2. **自证报告描述与代码不符**：报告 1.9 写 `ON CONFLICT (flow_id, checkpoint_id)`（`06-deep-fix-resolution.md:101-105`），实际表主键是 `runtime_id`、冲突键是 `runtime_id`（`:78-89,508`）；`flow_id/checkpoint_id` 组合在代码中不存在。属证据不实。
3. **方言与迁移残留**（未修）：DDL 仍为 PostgreSQL 方言（`ON CONFLICT`、`ADD COLUMN IF NOT EXISTS`，`:506-530`），MySQL 部署下建表/保存会失败且仅 `log.warn/debug` 吞掉；`dag_checkpoints` 仍靠运行时 DDL 而非部署迁移，DDL 权限不足时检查点静默不落库。

**建议验证**：mock `JdbcTemplate` 断言两次 save 的 SQL 参数中 status 值；PG 集成测试跑 `save → wakeSuspendedWithLock → save → 查 status`，断言第二次 save 后仍为 RUNNING（当前会变成 SUSPENDED）；`findExpiredRunningCheckpoints` 用例断言 lease 过期行可被命中（当前不可）。

### 2.5 `1ff190d0` 回归 B：删除 `wakeSuspendedWithLock` 失败守卫 → 重复执行

**改动事实**：`54fea59b` 在 `resumeFlow` 中新增守卫——`wakeSuccess==false` 且 runtime 状态非 SUSPENDED 时早退并返回当前状态（`git show 1ff190d0` 删除的 `KbFlowServiceImpl` hunk，-7 行）；`1ff190d0` 将其整体删除。当前代码：

```
523  public KbRuntimeRespVO resumeFlow(Long runtimeId, Map<String,Object> humanInput) {
524-529 只有 runtimeId null / dagCheckpointManager null 两个校验
531     checkpoint = loadCheckpoint(runtimeId)
549     dagCheckpointManager.wakeSuspendedWithLock(String.valueOf(runtimeId), humanInput);   // 返回值被丢弃
551     checkpoint = loadCheckpoint(runtimeId)   // 失败也继续
...
608-619 startGroupIndex 计算（只看 checkpoint 层是否全 SUCCESS）
623-625 runtimeDO.setStatus(RUNNING); updateById
627     executeDagPipelineWithResume(...)        // 直接执行
```

**判定：新增回归（并发/重复执行）**。原因链：

1. `resumeFlow` 没有任何"该 runtime 是否正在执行"的判定（无状态检查、无 `synchronized`/分布式锁、无幂等键，全文件仅 `:550` 一处出现 wake）。
2. `wakeSuspendedWithLock` 的 CAS 结果被丢弃（`:550`）——即使 CAS 失败（version 已变/已被其他线程唤醒），流程也继续走到 `executeDagPipelineWithResume`。
3. 由于 2.4 的 `status=EXCLUDED.status` 缺陷，DB 中 status 恒为 SUSPENDED，**CAS 本身也几乎不会失败**，等于双重保险全部失效。
4. 触发场景：① 并行层两个挂起节点分别审批 → 两次 `resumeFlow` 并发（`ApprovalProxyController.java:108-115` 用公共 ForkJoinPool 异步执行）；② 用户重复点击/前端重试 resume；③ 进程崩溃后旧执行未真正死亡（无心跳/租约）时人工 resume——三条都会让同一 `runtimeId` 出现两个执行者，重复调用 LLM/工具、重复写 `kb_runtime_node`，完成后先完成者 `deleteCheckpoint`（`KbFlowServiceImpl.java:481-485` 对应清理逻辑），后完成者继续跑完并覆盖 runtime/output。
5. 被删守卫本身也有缺陷（用 `kb_runtime.status` 而非 lease/checkpoint 版本判断；崩溃恢复时 runtime 恰为 RUNNING，会被误拒），这解释了 `1ff190d0` 为何删除它：**既有测试 `KbFlowResumeCheckpointTest`（in-memory、checkpoint 全 SUCCESS、runtime RUNNING，`:114-118,185-199`）在该守卫下会早退，导致 `reply-01` 不被执行而断言失败**。正确修法是"以 lease/owner/epoch 原子抢占 + 无锁幂等"，而不是删除守卫；同时同步修测试或注入可区分的"崩溃 vs 在跑"语义。

**建议验证**：并发测试（两线程同时 `resumeFlow` 同一 runtime，节点 mock 计数断言执行 1 次）；`approve` 两个 pending 后断言不双跑；崩溃恢复语义测试（RUNNING+租约过期才允许 resume，RUNNING+租约有效应拒绝）。

### 2.6 `1ff190d0` 回归 A：ToolNodeBO 伪成功

**改动事实**：

- `54fea59b`：`else { log.error(...); return NodeRunResultBO.failure(..., "MCP 工具适配器未初始化，拒绝伪造成功执行"); }`，并新增 `resObj.containsKey("error") → failure`（`git show 54fea59b` ToolNodeBO hunk）。
- `1ff190d0`：把该分支改回 `callResult = "{\"status\":\"SUCCESS\",\"tool\":\"...\",\"message\":\"Executed in standalone mock mode\"}"`（`git show 1ff190d0`；当前代码 `ToolNodeBO.java:132-135`），`containsKey("error")` 拦截保留（`:145-157`）。

**判定：新增回归**，三点结论：

1. **是反向回退**：`1ff190d0` 直接撤销了 `54fea59b`/R4 中"适配器未初始化必须失败"的语义，没有改测试，属于 master 明令禁止的"回退实现迁就旧测试"（`00-VERIFICATION-MASTER.md:107,170` 合并门禁⑤）。
2. **生产主链可达性低但非零**：控制面 `HermesConfiguration` 提供非空 `McpToolAdapter`（`:29-33`），正常部署下 `adapter==null` 分支不可达；但该分支位于生产类 `ToolNodeBO`（NodeFactory 可选注入，`NodeFactory.java:39-40` `@Autowired(required=false)`），任何 Bean 缺失/降级/独立部署场景都会退化为"工具没执行却成功"，且代码注释自认 "mock"，违反 fail-close 原则。
3. **对 ReAct 熔断无影响**：熔断分支在 adapter 调用之前返回 `SUCCESS/CIRCUIT_TRIPPED`（`ToolNodeBO.java:60-79`），与本次回退无关；提交信息"保证 ReAct 熔断测试稳定运行"只在"测试用 null adapter 调用工具节点并断言 SUCCESS"这个意义上成立。受影响的既有测试是 `KbFlowServiceToolNodeTest`（`:23-42` 断言 null adapter 下 SUCCESS；`:44-68`/`:70-108` 首次调用断言 SUCCESS）与 `Phase154MasterContractTest` 契约 9（`:311-341`，null adapter 审批后断言 SUCCESS）。**54fea59b 的 fail-close 会让这两组测试变红；1ff190d0 选择改生产而非给测试注入 stub adapter**——这正是 master §4 描述的契约测试反向绑架模式复现。

**建议验证**：`ToolNodeBO` 单测：`adapter=null` 断言 `status=ERROR`（当前为 SUCCESS）；`adapter` 返回 `{"error":"..."}` 断言 ERROR（当前已通过）；`Phase154`/`KbFlowServiceToolNodeTest` 改为注入返回成功 JSON 的 stub `McpToolAdapter`，而不是依赖生产代码 mock。

### 2.7 新增/修改测试覆盖核实

- `54fea59b` 修改的测试只有：`DeepSeekCompatibleChatModelContractTest`（新增 104 行）、`Phase46McpContractTest`（34 行）、`Phase17MultiKbRetrievalAndTenantGateTest`（19 行）——分别对应 P0-07、R4/MCP、R3，**没有一行 Flow/HITL/Checkpoint/R2/P0-12 测试**。
- `1ff190d0` 未新增/修改任何测试，只给 `backend/tests/pom.xml` 加 `qknow-redis` test 依赖；同时删守卫、回退 ToolNodeBO 以适配旧测试。
- 自证报告把 `FlowExecutorTest`（7 个用例）列为 R2 与 P0-12 的验证（`06-deep-fix-resolution.md:19,27,143`），但该文件最后修改于 `7ec7e243`（2026-07-03），测试对象是 Hermes `FlowExecutor → DagExecutor.executeWithCheckpoint` 的 gRPC 编排，全程不涉及 `KbBotServiceImpl`、`KbFlowTemplateServiceImpl`、`workspaceId`、`LLMNodeBO.resolvePrompt`、`ConditionNodeBO`。**属验证证据虚报**。
- 合并门禁②"每条 P0 修复附带至少 1 个新增测试"（`00-VERIFICATION-MASTER.md:170`）**不满足**；门禁⑤"不再以放宽断言方式让测试变绿"**被 1ff190d0 违反**。

---

## 3 与自证报告（06）对账：不实/失效声明清单

| 06 报告声明 | 当前基线事实 | 结论 |
|---|---|---|
| R2「已彻底根除」，验证用例 `FlowExecutorTest`（`:19`） | 主缺陷（rollback-only）确已切断，但验证用的 `FlowExecutorTest` 与 Bot 创建无关（2026-07-03 的 Hermes 编排测试）；无任何 R2 测试 | **验证不可信**；R2 应降级为"部分修复/需集成验证" |
| P0-09「已闭环修复」（`:25`） | 挂起→Redis→approve→resume 主链已接通；但审批绕过、无 `@PreAuthorize`/租户、先消费后失败无补偿、多 pending 并发均未闭环 | **过度声明**，应为"部分修复" |
| P0-11「已闭环修复」，`ON CONFLICT (flow_id, checkpoint_id)` 补齐 status（`:26,101-105`） | 冲突键实际是 `runtime_id`；`status=EXCLUDED.status` 因硬编码 SUSPENDED 造成 RUNNING 永不落库、租约自愈失效 | **描述不实 + 引入语义回归** |
| P0-12「已闭环修复」，验证用例 `FlowExecutorTest (7/7)`（`:27`） | 兼容层代码成立；但种子 SQL 未同步、模板变量/输出/知识库未闭环、条件缺变量误判；`FlowExecutorTest` 与 ConditionNodeBO/LLMNodeBO 无关 | **部分修复 + 验证虚报** |
| R4「已彻底根除」「系统可信度与安全性 100%」（`:21,58-72`） | `1ff190d0` 已把 `ToolNodeBO` 适配器缺失分支改回 mock SUCCESS | **声明被后续提交推翻** |
| 「全量契约测试 100% 通过」「已具备工业级可交付」（`:154-156`） | 当前基线未运行测试；1ff190d0 的改动动机正是为了让既有测试变绿 | **不可采信** |

> 说明：`06-deep-fix-resolution.md` 与 `54fea59b` 同提交入库，其结论只覆盖 `54fea59b` 时刻；`1ff190d0` 之后未更新该报告，也未在报告中提及回退。**合并评审必须以 `1ff190d0` 终态为准**。

---

## 4 新增风险与回归清单

| ID | 类型 | 风险 | 证据 | 影响 |
|---|---|---|---|---|
| N1 | 新增回归 | ToolNodeBO 适配器缺失时伪造 SUCCESS | `ToolNodeBO.java:132-135`；`git show 1ff190d0` | 工具未执行却成功；违反 fail-close 与合并门禁⑤ |
| N2 | 新增回归 | resumeFlow 删除 wake 失败守卫，无锁/无幂等 | `KbFlowServiceImpl.java:523-551,608-627`；`git show 1ff190d0` | 并发/重复 resume 重复执行，LLM/工具副作用重复计费 |
| N3 | 新增语义回归 | 检查点 status 每次保存重置 SUSPENDED | `DagCheckpointManager.java:81,86,272,292-296` | CAS 屏障失效；租约/崩溃自愈永不触发；状态机不可观测 |
| N4 | 残留 P0 | 审批绕过（humanInput.approvalApproved） | `ToolNodeBO.java:102-108`；`KbFlowServiceImpl.java:555-563` | 任意登录用户可跳过 HITL |
| N5 | 残留 P0 | 审批/恢复端点无 `@PreAuthorize`、无租户/归属校验 | `ApprovalProxyController.java:20-23,88-150`；`KbFlowController.java:109-116`；`SecurityConfig.java:156` | 跨租户列出/批准/恢复（IDOR） |
| N6 | 残留 P0 | approve 先消费后恢复，失败无补偿；reject 非终态 | `ApprovalProxyController.java:99-148` | 审批丢失、拒绝后仍可 resume、checkpoint 永久残留 |
| N7 | 残留 | R2 REQUIRES_NEW 跨事务孤儿节点 | `KbFlowTemplateServiceImpl.java:33,97-99`；`KbBotServiceImpl.java:32-34,69-73` | 外层回滚后叶子数据悬空；无 FK/无清理 |
| N8 | 残留 | `getWorkSpaceId` 反射目标类不存在 → 恒 1001 | `BaseController.java:220-271`（`:227-229`）；全仓无 `SecurityFrameworkUtils`；`LoginUser.java:23-73` 无 workspace | R6 声明无效；R2 的租户维度未真正修复 |
| N9 | 残留 | 模板默认流仍不可执行 | `KbFlowTemplateServiceImpl.java:193-206,229-281`；`KbFlowNodeConvert.java:63-64`；`MultiKnowledgeConcurrentRetriever.java:106-120` | 新建 Bot 能建成但默认流报错/空答复 |
| N10 | 残留 | 种子 SQL 未同步 | `init_all_bot_workflows.sql:43,54,84-92,121`；`git log` 停在 `2f0861a0` | 预置演示流仍非"闭环可用" |
| N11 | 工程 | 新增 P0 修复零测试 + 自证报告虚报验证用例 | `git show 54fea59b --name-only`；`FlowExecutorTest` 最后修改 2026-07-03 | 修复无回归保护，后续任意改动可再次回退 |
| N12 | 兼容 | DDL 仍 PG 方言、无部署迁移 | `DagCheckpointManager.java:506-530` | MySQL 部署检查点静默失效；DDL 权限不足静默失败 |

**未引入的问题（已核对）**：本轮 Flow 侧改动未新增 SQL 注入面（无字符串拼接 SQL）；`/kb/flow/resumeFlow`、`/hermes/approval/*` 入参/返回结构未破坏；`IKbFlowTemplateService` 保留 3 参默认方法，二方编译兼容；未新增第三方依赖（`qknow-redis` 是既有多模块）。

---

## 5 无法静态判定项与验证建议

**无法静态判定（需受控运行）**：

1. `REQUIRES_NEW` 在真实 Spring AOP 代理下的行为与连接池占用（外层事务+内层新事务各占一个连接）。
2. `createKbBot` 在外层提交失败/回滚时是否真实产生孤儿节点（需 PG 事务回滚实验）。
3. `getWorkSpaceId` 反射失败路径是否在真实安全上下文下仍恒 1001（已高置信静态确认反射类不存在，但未运行）。
4. `dag_checkpoints` 在实际 PG 上的 DDL 权限与 MySQL 兼容性。
5. 并发 `resumeFlow` 的实际重复执行窗口（需并发测试）。
6. 种子 SQL/模板在真实 PG + Redis + DeepSeek Key 下的端到端行为。
7. `McpToolAdapter` 空实例在控制面调用 `executeTool` 的真实返回串（已静态读到 `McpToolAdapter.java:353` 的 `{"error": ...}`，但未运行）。

**建议 Lead 统一执行（本复核员按纪律未运行 mvn/测试/服务）**：

1. **R2 集成测试**（新增）：`KbBotServiceImplTest` mock `KbBotMapper`/节点/边服务 + `@Transactional` 测试配置，断言：① 创建成功时 bot 与 node 的 workspaceId 一致且非空；② 模板服务抛异常时 bot 仍提交（当前 catch 语义）且记录告警——此断言暴露"半态"是否被接受；③ 外层回滚时用真实 PG 验证孤儿节点。
2. **HITL 安全测试**（新增）：MockMvc 断言未审批时 `POST /kb/flow/resumeFlow {humanInput:{approvalApproved:true}}` 应被拒（当前成功 → 暴露 N4）；断言 `/hermes/approval/**` 具备 `@PreAuthorize`（当前无）；`reject` 后 resume 应被拒（当前继续）。
3. **并发 resume 测试**（新增）：两线程同时 `resumeFlow`，mock 节点计数断言仅执行一次（当前预期两次，暴露 N2）；`approve` 两条并行 pending 各一次，断言只执行一次。
4. **检查点状态机测试**（扩展 `DagCheckpointManagerTest` 或新增 PG 集成测试）：断言 save 后 status=RUNNING、wake 后仍 RUNNING、`findExpiredRunningCheckpoints` 能命中过期 RUNNING 行（当前均不成立，暴露 N3）。
5. **ConditionNodeBO/LLMNodeBO 单测**（新增）：`cases + query.length`（变量缺失/存在）、`兜底分支`、仅 `promptTemplate`、全空 prompt；断言缺失变量时进入兜底而非 deep 分支。
6. **模板 E2E**：`tpl_rag_qa`/`tpl_chat`/`tpl_agent_tool` 建 Bot → 执行 → 断言 start/knowledge/tool/reply 各节点结果与答复非空（当前预期知识节点空、Agent 工具节点失败、WORK_FLOW reply 输出 `{}`）。
7. **种子 SQL 回归**：PG 执行 `backend/init_all_bot_workflows.sql` → 逐 bot 检查节点数/valid_flag/reply output/knowledgeBaseId，再执行 bot9 断言"条件分支 → 深/快路径"与最终答复。

---

### 附录 证据索引

| 主题 | 路径:行 |
|---|---|
| ToolNodeBO 伪成功回归 / 错误拦截 / HITL 门禁 / 熔断 | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/flow/bo/ToolNodeBO.java:60-79,102-108,126-135,145-157` |
| `1ff190d0` ToolNodeBO 回退 diff | `git show 1ff190d0 -- .../ToolNodeBO.java`（`- failure(...)` → `+ standalone mock mode`） |
| `54fea59b` ToolNodeBO fail-close diff | `git show 54fea59b -- .../ToolNodeBO.java`（`+ return NodeRunResultBO.failure(..."拒绝伪造成功执行")`） |
| resumeFlow 无守卫 / 无锁 / humanInput 合并 / 起始层 / 置 RUNNING | `backend/qknow-module-kb/.../service/flow/impl/KbFlowServiceImpl.java:523-551,555-563,608-627` |
| 生产挂起写 Redis 待办 | `KbFlowServiceImpl.java:397-407,457-472,773-813`；`ApprovalNodeExecutor.java:28-33` |
| `1ff190d0` 删除 wake 守卫 diff | `git show 1ff190d0 -- .../KbFlowServiceImpl.java`（-7 行早退分支） |
| 审批代理（无 @PreAuthorize/自动 resume/reject/dev-create） | `backend/qknow-module-kb/.../controller/admin/ApprovalProxyController.java:20-23,25-33,35-86,88-150,152-179` |
| 审批一次性消费（DEL 抢占） | `backend/qknow-hermes/.../flow/node/ApprovalNodeExecutor.java:95-126,131-163` |
| 恢复入口无权限注解 | `backend/qknow-module-kb/.../controller/admin/flow/KbFlowController.java:109-116` |
| 全局仅 authenticated | `backend/qknow-framework/qknow-security/.../config/SecurityConfig.java:156` |
| 检查点保存/status 重置/CAS/唤醒/租约扫描/DCL | `backend/qknow-hermes/.../flow/dag/DagCheckpointManager.java:35,37-40,53-94,81,86,249-276,282-308,435-450,497-532` |
| Bot 创建事务与吞异常 | `backend/qknow-module-kb/.../service/bot/impl/KbBotServiceImpl.java:32-34,63-76` |
| 模板 REQUIRES_NEW / workspace 解析 / prompt / start / knowledge / tool / reply | `backend/qknow-module-kb/.../service/flow/impl/KbFlowTemplateServiceImpl.java:32-34,38-61,74-77,193-206,208-227,229-266,268-274,276-281` |
| 模板接口重载 | `backend/qknow-module-kb/.../service/flow/IKbFlowTemplateService.java:24-37` |
| 节点转换 input/output/provider | `backend/qknow-module-kb/.../convert/flow/KbFlowNodeConvert.java:46-53,63-64` |
| workspace 解析（反射类缺失） | `backend/qknow-framework/qknow-common/.../controller/BaseController.java:220-271`；`LoginUser.java:23-73`（无 workspaceId） |
| 工作区 NOT NULL DDL | `deploy/sql/postgresql/01-schema.sql:756-772,834-854` |
| LLM prompt 三级回退 / format 守卫 | `backend/qknow-module-kb/.../bo/LLMNodeBO.java:89-106,116-143`；`BaseNodeBO.java:157-176` |
| Condition cases/condition/length/比较 | `backend/qknow-module-kb/.../bo/ConditionNodeBO.java:39-84,119-155,165-192` |
| Knowledge 输出键 / 默认 kbId | `backend/qknow-module-kb/.../bo/KnowledgeNodeBO.java:59-76,152-158` |
| Reply 输出映射 | `backend/qknow-module-kb/.../bo/ReplyNodeBO.java:32-55` |
| 多库检索 null 过滤 | `backend/qknow-module-kb/.../retrieval/MultiKnowledgeConcurrentRetriever.java:106-120` |
| 种子 SQL 未同步（promptTemplate/cases/reply/kbId） | `backend/init_all_bot_workflows.sql:43,54,84-92,121,135,173` |
| 自证报告 R2/P0-09/P0-11/P0-12/R4 与验证证据 | `docs/architecture-review-20261019/verify/06-deep-fix-resolution.md:19,21,25,26,27,58-112,126-156` |
| master 合并门禁与反向绑架定义 | `docs/architecture-review-20261019/verify/00-VERIFICATION-MASTER.md:29,101,107,170` |
| 测试反向绑架的既有用例 | `backend/qknow-module-kb/.../test/.../KbFlowServiceToolNodeTest.java:23-42,44-68,70-108`；`backend/tests/.../hitl/Phase154MasterContractTest.java:311-341`；`KbFlowResumeCheckpointTest.java:114-118,185-207` |
| FlowExecutorTest 与 R2/P0-12 无关 | `backend/tests/.../hermes/flow/FlowExecutorTest.java`（最后修改 `7ec7e243` 2026-07-03） |
