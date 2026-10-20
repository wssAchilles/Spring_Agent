# 工作流 DAG 引擎 / 检查点恢复 / HITL / 自愈事务审计报告

- 任务：task-2（DAG 流程引擎与 HITL 审计员 / flow-engine-auditor）
- 审计日期：2026-10-19（Asia/Shanghai）
- 代码基线：`backend/` 工作区当前未提交状态（**只读**；未执行 `mvn`/`gradle`、未启动任何服务、未修改源码/配置/SQL/fixture、未 `git commit`）
- 交付约束：唯一写入文件为本文件
- 结论标注：**【已验证】**= 由源码行、装配关系或数据文件直接证明；**【推断】**= 由多行源码/装配关系推出、但未运行时复现；**【未知】**= 本次只读审计无法判定，需运行或需 Lead 决策

---

## 0 阅读范围与方法

### 0.1 实际阅读范围

| 区域 | 规模 | 读法 |
|---|---|---|
| `backend/qknow-hermes/qknow-hermes-core/.../hermes/flow/**`（engine、dag、expression、factory、checkpoint（含 spi/impl）、hitl（engine/coordinator/governor/node/model/receipt/dto/scheduler/signal）、debug、audit、enums、rag、bo、stategraph、saga、pattern、mesh、node、util） | 120 文件 / 14,251 行 | 调度、节点、checkpoint、HITL、Saga、stategraph 共约 40 个核心文件逐行精读；其余按类签名 + 调用点核对 |
| `.../hermes/workflow/**`（timetravel） | 3 文件 / 996 行 | 头部与全部 public 方法签名、Merkle DAG/二进制提升/投机分支实现要点 |
| `.../hermes/transaction/**` | 11 文件 / 669 行 | 全部 engine 与 dto 逐个读 |
| `.../hermes/cognitive/selfhealing/**` | 8 文件 / 502 行 | state machine / kernel bus / memory compressor / graph aligner 逐个读 |
| `.../hermes/agent/workflow/healing/**`、`.../hermes/agent/dag/**` | 7 文件 / 966 行 | WaitForGraph、FiniteStateCheckpoint、TopologicalPhasedDispatcher、HierarchicalTaskPlanner 逐行 |
| 上述之外被本报告引用的 Hermes 类 | — | `agent/AgentOrchestrator`（guard 装配）、`agent/guard/*`（ReAct 滑窗）、`grpc/HermesGrpcService`、`streaming/engine/RealtimeDagEventStreamer`、`tool/mcp/sagas/McpSagasCompensationGovernor`、`controller/ApprovalController` |
| 控制面 `backend/qknow-module-kb/.../service/flow/**`、`controller/admin/flow`、`dal/dataobject/flow`、`convert/flow`、`service/runtime`、`service/bot`、`ApprovalProxyController` | 约 3,884 行（另加 controller/runtime/bot） | 生产 DAG 调度器、节点实现、装配、运行时持久化逐行精读 |
| `backend/init_all_bot_workflows.sql` | 362 行 | 全文精读（节点/边/配置/句柄/模型字段） |
| 前端只读契约 `frontend/src/views/kb/bot/build/**`（68 文件）与 `frontend/src/api/kb/bot/flow.js` | — | 仅读取调试面板、LLM/条件节点配置、SSE 消费、resumeFlow 调用，不深入 UI |
| 测试 `backend/tests`（flow/workflow/hitl/hermes 相关 40 文件 / 13,823 行）与 `qknow-module-kb-biz/src/test/.../flow`（4 文件 / 751 行） | — | 抽样精读主契约测试与 6 个 Phase 测试，统计装配真实度 |
| 装配关系 | — | 两个 Spring Boot 启动类、POM 依赖、`@ComponentScan` 过滤器、全仓 import 统计 |

### 0.2 方法

1. 先确定**进程与装配边界**（哪个类在哪个 Spring 上下文里是 Bean），再判断代码是否可达；这是本次审计最重要的分水岭。
2. 以「请求 → 控制面调度器 → 节点 → 运行时持久化 → SSE」为主线逐行还原；对 Hermes 侧以「gRPC → FlowExecutor → DagExecutor」与「stategraph / HITL / transaction」两条支线并行核对。
3. 用全仓 import 与类名交叉引用统计死代码面：脚本按简单类名 token 匹配（排除 `src/test`），并做从入口点（`@GrpcService`/`@RestController`/`@Scheduled`/`CommandLineRunner`）出发的可达性 BFS 启发式。
4. 对 AGENTS.md 提出的「窗口淘汰前判定阈值 → W+1 幽灵计数」做形式化手工推演（W=6、阈值=3、A,B,C,D,A,E,A 序列），逐阶段对照实现行号，给出证伪/证实结论。
5. 不把 README/注释当实现证据；注释与实现冲突处单独列出（本报告中此类冲突很多）。

### 0.3 路径缩写（全文统一）

| 缩写 | 实际路径 |
|---|---|
| `HC` | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes` |
| `KB` | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb` |
| `SQL` | `backend/init_all_bot_workflows.sql` |
| `FE` | `frontend/src/views/kb/bot/build` |
| `FEAPI` | `frontend/src/api/kb/bot/flow.js` |
| `TS` | `backend/tests/src/test/java/tech/qiantong/qknow` |
| `KBT` | `backend/qknow-module-kb/qknow-module-kb-biz/src/test/java/tech/qiantong/qknow/kb/biz/flow` |

### 0.4 范围之外（本次未审计）

`flow/mesh` 的 AIMD 背压只做了接口/实现抽查（不在必答项）；`flow/rag`、`flow/pattern`、`flow/audit` 的 DTO/凭单类只做签名级核对；`workflow/timetravel` 的数学正确性（Merkle/二进制提升/1536 维核度规）未逐公式验证；Hermes `AgentOrchestrator` 主聊天链路由 Lead 负责，本报告只在 ReAct 滑窗与 guard 装配处引用。

---

## 1 真实执行路径还原

### 1.1 决定性前置：两个 Spring Boot 上下文与 Bean 边界

本仓库有两个启动类：

- 控制面：`backend/qknow-server/src/main/java/tech/qiantong/qknow/server/QKnowApplication.java:21-26`，`@ComponentScan(basePackages = {"tech.qiantong"}, excludeFilters = @Filter(type = REGEX, pattern = {"tech\\.qiantong\\.qknow\\.hermes\\..*"}))`。
- Hermes 认知面：`backend/qknow-hermes/qknow-hermes-starter/src/main/java/tech/qiantong/qknow/hermes/HermesApplication.java:15-21`，扫描 `tech.qiantong.qknow.hermes`、`redis`、`ai.deepseek`。

`backend/qknow-module-kb/qknow-module-kb-biz/pom.xml:95-113` 依赖 `qknow-hermes-proto`、`qknow-hermes-core`、`grpc-stub`。也就是说：**Hermes 的类在控制面 classpath 上，但 Hermes 包下的 `@Component`/`@Service`/`@Configuration`/`@RestController` 不会被控制面的 Spring 上下文扫描**。全仓非测试代码中对 Hermes 的 import 只有 4 个与流相关：`DagCheckpointManager`（`KB/service/flow/impl/KbFlowServiceImpl.java:42`）、`DagUtils`（同文件 `:41`）、`ReActCycleGuard`（`KB/service/flow/bo/ToolNodeBO.java:5`）、`McpToolAdapter`（`KB/service/flow/factory/NodeFactory.java:21`、`KB/service/flow/bo/ToolNodeBO.java:10`）。其中前两者一个是静态工具、一个被 `@Autowired(required = false)` 注入：

- `KB/service/flow/impl/KbFlowServiceImpl.java:74-75`：`@Autowired(required = false) private DagCheckpointManager dagCheckpointManager;`
- `KB/service/flow/factory/NodeFactory.java:39-40`：`@Autowired(required = false) private McpToolAdapter mcpToolAdapter;`

而控制面没有任何别处注册这两个 Bean（全仓 `new DagCheckpointManager` 只出现在测试；Hermes 的 `@Configuration` 全部位于被排除的包）。**因此在实际运行的控制面进程里，`dagCheckpointManager == null`、`mcpToolAdapter == null`。** 由此直接推出三条生产事实（【已验证】（装配静态证明））：

1. 所有 `saveDagCheckpoint(...)` 都是空操作（`KbFlowServiceImpl.java:475-503` 的 `if (dagCheckpointManager != null ...)`），`dag_checkpoints` 表在生产控制面永远不会被写入；
2. `/kb/flow/resumeFlow` 必然抛 `ServiceException("未配置检查点管理器，无法恢复执行")`（`KbFlowServiceImpl.java:511-513`）——**HITL 恢复与崩溃恢复整体不可用**；
3. 工具节点永远走 `NO_ADAPTER` 兜底并返回 `status=SUCCESS`（`KB/service/flow/bo/ToolNodeBO.java:126-131`、`:148-153`）。

Hermes 侧唯一真实的流程入口是 gRPC：`HC/grpc/HermesGrpcService.java:63-82 executeFlow` → `HC/flow/FlowExecutor.java:44 execute`。但控制面的 gRPC 客户端 `KB/service/agent/HermesGrpcClient.java:63-96` **只有 `chat()`、`:103-107` 只有健康检查，没有 `executeFlow` 调用**。全仓也没有其他客户端调用 `HermesServiceGrpc.executeFlow`。【已验证】控制面从不通过 gRPC 执行 Hermes 侧 DAG；控制面执行的是自己的复制实现。

> 与 Lead 草稿的交叉核对：Lead 笔记「控制面 `KbFlowServiceImpl.executeFlow()` 是生产引擎、Hermes `FlowExecutor` 仅被 gRPC 与测试使用」**结论正确**。但需修正一处：控制面引擎的 checkpoint/HITL 持久化在当前装配下**不是"已具备"，而是"空转"**（详见 §2.3、§2.4）。

### 1.2 控制面生产 DAG 链（唯一真实生产引擎）

入口：`KB/controller/admin/flow/KbFlowController.java:51-59`（`/kb/flow/testExecuteFlow`，调试 SSE）、`:61-70`（`/kb/flow/executeFlow`）、`:72-81`（`/kb/flow/executeFlowStream`）、`:109-116`（`/kb/flow/resumeFlow`）。另有 `KB/api/BotApiServiceImpl.java:33-78` 供外部模块（如 `qknow-module-ext` 的非结构化任务）调用同一 service。

调度主体 `KB/service/flow/impl/KbFlowServiceImpl.java`：

1. **执行器**：`:77` `Executors.newVirtualThreadPerTaskExecutor()`——控制面节点并发用的是 Java 21 虚拟线程（与 Hermes 侧固定线程池形成对比）。
2. **执行管道** `executeDagPipelineWithResume`（`:322-473`）：`validateWorkflow`（`:648-685`，仅校验"恰好一个 START、uuid 唯一、边端点存在"，**不校验从 START 可达性**）→ `DagUtils.hasCycleUuids` 判环（`:340-343`，有环直接抛异常拒绝执行，不支持任何回边/循环）→ `DagUtils.getParallelGroupsUuids` Kahn BFS 分层（`:346`）→ 构建 `nodeMap`、`prunedNodes`、`resultMap`、`stepCounter`（`:349-357`）→ 逐层执行（`:359-463`）。
3. **层内执行**：单节点层直接 `executeSingleDagNode`（`:363-397`）；多节点层 `CompletableFuture.supplyAsync(..., dagExecutorService)`（`:398-412`）后 `allOf(...).join()`（`:414`），即**严格的层同步屏障**。
4. **节点执行** `executeSingleDagNode`（`:598-643`）：命中剪枝集合则生成 `SKIPPED` 结果（`:610-622`）；否则 `nodeFactory.createNode(currentNodeDef, flowEdges)` 现场 new 一个节点实例并 `node.execute(runtimeContext)`（`:625-626`）；`stepCounter.getAndIncrement()`（`:627`）；`runtimeService.saveRuntimeNode(...)` 落库（`:629-631`）；若结果带 `nextNodeIds`，用 `DagUtils.computePrunedNodesUuids(nodeUuid, nextNodeIds, edgePairs)` 计算未命中分支下游并加入剪枝集合（`:634-639`）。
5. **失败/挂起**：节点 ERROR → `saveRunError` + `saveDagCheckpoint(..., groupIndex, resultMap)` 后 `return`（`:376-385`）；节点 SUSPENDED → `runtimeDO.setStatus(SUSPENDED)` + `updateById` + `saveDagCheckpoint(..., groupIndex, ...)` 后 `return`（`:387-397`）；并行层同理（`:435-456`）。
6. **层成功**：`saveDagCheckpoint(..., groupIndex, resultMap)`（`:459-462`，注意传的是**当前层号**，不是下一层）；全部成功 → `saveRunSuccess` 并 `deleteCheckpoint`（`:465-470`）。
7. **检查点内容**：`saveDagCheckpoint`（`:475-504`）把运行时 `NodeRunResultBO` 转成 Hermes 的 `NodeRunResultBO`、连同 `runtimeContext.getVariables()` 一起调 `dagCheckpointManager.saveCheckpointWithVariables(runtimeId=runtimeId, flowId=botId, groupIndex, ...)`（`:482-498`）。
8. **恢复** `resumeFlow`（`:507-593`）：`loadCheckpoint`（`:515`）→ `runtimeService.getById`（`:520`）→ `queryFlow(botId)` **重新读取当前画布定义**（`:526`）→ `wakeSuspendedWithLock(runtimeId, humanInput)`（`:534`）→ 恢复变量并与 humanInput 合并（`:541-547`）→ 重建 `initialResultMap`（`:564-582`）→ `startGroupIndex = checkpoint.getGroupIndex() + 1`（`:584`）→ 以 `RUNNING` 状态重入 `executeDagPipelineWithResume`（`:589-592`）。

### 1.3 流式/SSE 链（控制面）

- 调试：`testExecuteFlow`（`:128-151`）在**返回 Flux 之前同步跑完整条 DAG**（`textExecuteFlow(...)`，`:133`），然后返回 `Flux.concat(Flux.just({event:"started"}), resultFlux)`（`:148-150`）。即"流式"只发生在 DAG 执行之后的事件重放阶段。
- 正式流式：`executeFlowStream`（`:218-242`）同样先同步 `executeFlow(...)`（`:225`），随后对 `resultFlux` 做 `Mono<CommonResult> last = resultFlux.last(); last.subscribe(...)`（`:234-238`）以保存输出，再把同一个冷 `resultFlux` 返回给 MVC 订阅（`:239-241`）。由于 `chatModel.stream(...)` 是冷 Flux（`KB/service/flow/bo/LLMNodeBO.java:73`），这里**存在两条独立订阅 → 同一次 LLM 生成被真实调用两次**（详见 §3 F-P1-9）。
- 事件内容只来自回复节点：`ReplyNodeBO.setWorkFlowFlux`（`KB/service/flow/bo/ReplyNodeBO.java:63-102`），每个输出定义发一条 `{text,name}`（`:80-88`），首帧固定为 `Flux.just(CommonResult.success("{}"))`（`:66-68`），每个输出后追加 `"\n"` 帧（`:169-174`）。**没有任何节点级事件、事件序号、event id、Last-Event-ID 或断线重放**（§2.6）。

### 1.4 数据模型与种子数据链

- 节点/边 DO：`KB/dal/dataobject/flow/KbFlowNodeDO.java:25-90`、`KbFlowEdgeDO.java:25-75`（`config/input/output/style/sourceHandle` 均为字符串 JSON）。
- 前端画布 JSON → DO：`KB/convert/flow/KbFlowNodeConvert.java:26-54`——`type` 取 `nodeJson.type` 字符串并按名字映射枚举，**未知类型静默降级为 START**（`:29-32`）；`config = data.config`（`:37-40`）；`input/output` 取 `data.input/output`（`:50-51`）；`style` 保存整个节点 JSON（`:52`）。边：`KB/convert/flow/KbFlowEdgeConvert.java:23-30`（`source/target/sourceHandle`）。
- 枚举：`KB/dal/enums/FlowNodeTypeEnums.java:8-16`（START=1、LLM=2、REPLY=3、CONDITION=4、KNOWLEDGE=5、TOOL=7、LOOP=8；`getByCode` 把 5/6 都当 KNOWLEDGE，`:61-63`）。注意 Hermes 侧枚举是 START=1、LLM=2、REPLY=3、CONDITION=4、HTTP=5、KNOWLEDGE=6、AGGREGATOR=7、APPROVAL=8、SUSPEND=9（`HC/flow/enums/FlowNodeTypeEnums.java:3-12`）——**两套枚举语义不同**，跨 gRPC 或跨模块共享节点数据会错位（Hermes `FlowExecutor` 用 `getByName` 映射，`tool` 在 Hermes 枚举不存在 → `typeCode=null` → `NodeFactory.createNode` 抛"节点类型不能为空"，`HC/flow/FlowExecutor.java:102-115` + `HC/flow/factory/NodeFactory.java:45-49`）。
- 种子 SQL：`SQL:10-104`（Bot 9）、`:108+`（Bot 3）等。种子里 `type` 与 KB 枚举一致（start=1、knowledge=5、condition=4、llm=2、tool=7、reply=3，`SQL:23,31,39,58,64,76,86`）；`style` 与配置同体。**但 LLM 节点配置只有 `model` 没有 `provider`**（全文件 `provider` 出现 0 次；`SQL:54,57,76,79,135,138,180,183,213,214,241,242`），而生产 LLM 节点要求 `provider` 非空（`KB/service/flow/bo/LLMNodeBO.java:49-53`）。⇒ 按此 SQL 初始化的任何工作流，**第一个 LLM 节点必然以"模型选择错误"置 ERROR，整条 DAG 终止**（`KbFlowServiceImpl.java:376-385`）。
- Bot 创建时自动生成模板：`KB/service/bot/impl/KbBotServiceImpl.java:64-75`（insert 后 `flowTemplateService.initBotWorkflowByTemplate(id, type, null)`）。模板 DSL 在 `KB/service/flow/impl/KbFlowTemplateServiceImpl.java:100-140`，节点 `data` 直接放 `label/model/prompt`（`:181-189`），**没有 `data.config` 嵌套**；而 `KbFlowNodeConvert` 只从 `data.config` 取配置（`:37-40`）。⇒ 模板生成的节点 `config={}`：LLM 无 `provider/model/prompt`、reply 无 `content`、tool 无 `toolCode`；knowledge 的 `config.knowledgeBaseId=null`（`:196`）会走到默认知识库 7L（`KB/service/flow/bo/KnowledgeNodeBO.java:74-76`）。**即"新建 Bot 自动初始化的工作流"整体不可执行**（LLM 首先失败）。【已验证】（静态数据契约比对）

### 1.5 Hermes 侧执行链（当前不可达/仅 gRPC 可达）

- `HC/flow/FlowExecutor.java:44-97`：gRPC `FlowRequest` → `KbFlowNodeDO/KbFlowEdgeDO` → `RuntimeContextBO` → `dagExecutor.executeWithCheckpoint(runtimeId=flowId+":"+requestId, ...)`（`:79-81`）；快照写入 `FlowStateStore`（`:83-93`）。
- `HC/flow/dag/DagExecutor.java:32-34`：**固定线程池 `min(availableProcessors, 8)`**（不是虚拟线程）；`:47-52` 判环 + Kahn 分组；`:64-125` 逐层执行，`:84-95` 层内 `CompletableFuture.allOf(...).join()`；`:134-265` 带检查点版本：从 `checkpointManager.loadCheckpoint` 恢复、`restoreCompletedResults/restoreVariables`（`:143-153`）、**从恢复结果反推剪枝集**（`:164-173`）、层失败/挂起时保存检查点后 break（`:197-210`、`:239-252`）、每层成功后保存下一层号（`:255-257`）、只有全成功才删检查点（`:260-263`）。
- 状态图执行器（另一套）：`HC/flow/stategraph/engine/DurableStateGraphEngine.java:114-341`（BSP 超步 + 每超步新建虚拟线程执行器 `:236-237` + 状态规约 + 快照）、`HC/flow/stategraph/engine/StateGraphScheduler.java:86-207`（Pregel 超步主循环，`checkpointer` 默认 null、`setCheckpointer` 无生产调用，`:43-77`）。
- 该链路的现状：控制面不调用；`HermesGrpcService.executeFlow` 无已知客户端；stategraph 调度器无任何生产调用点。**结论：Hermes 侧 DAG 在当前系统中不承担生产流量。**

---

## 2 架构与实现评估

### 2.1 DAG 调度的真实语义

**2.1.1 拓扑与并行**
两层实现都是"Kahn 分层 + 层同步屏障"，语义完全一致：同一层 = 当前所有入度为 0 的节点；层内并发；层间串行。区别只在并发载体：控制面虚拟线程（`KbFlowServiceImpl.java:77,407-410`）、Hermes 固定池 ≤8（`HC/flow/dag/DagExecutor.java:32-34,86-88`）。这意味着：

- 端到端时延 ≈ Σ(每层最慢节点时延)，而非关键路径时延。对"多路并行检索/多路 LLM"这类层内宽图尚可，对"长短分支混合"的大图最差情况退化明显（例：1 个 60s LLM 与 9 个 200ms 节点同层 → 整层 60s）。
- 控制面每层用 `CompletableFuture.allOf(...).join()` 阻塞调用线程（`KbFlowServiceImpl.java:414`）。`testExecuteFlow` 在 Web 请求线程内同步执行整条 DAG（`:133`）→ **SSE 请求在首个字节前可能被整条工作流阻塞**；若 Tomcat/Netty 线程池被打满，会放大排队。`application.yml` 未开虚拟线程（Lead 已记录 `spring.threads.virtual.enabled` 注释状态），该点由 Lead 报告覆盖，此处仅强调与流量模型叠加的风险。【推断】
- 并行组内共享同一个 `RuntimeContextBO`。控制面 `BaseNodeBO.processOutput` 对变量写入加了 `synchronized (contextVariables)`（`KB/service/flow/bo/BaseNodeBO.java:81-92`），比 Hermes 版本（`HC/flow/bo/BaseNodeBO.java:70-77`，无同步、直接 `putAll`）安全；但 **`context.setFluxMap()` 与 `setResultFlux()` 未加锁**（`KB/service/flow/bo/LLMNodeBO.java:68-75`、`ReplyNodeBO.java:101`），两个并行 LLM 节点同时首次写入时，后写覆盖前写，先写的 Flux 丢失；`fluxMap` 初始为 `null` 时才 `new HashMap<>()`（`:69-72`），在流式工作流里并行 LLM 分支可能触发丢失。`RuntimeContextBO.variables` 是 fastjson2 `JSONObject`（`HC/flow/bo/RuntimeContextBO.java:32`；KB 版 `KB/service/flow/bo/RuntimeContextBO.java:35`）——底层是 `LinkedHashMap`，非线程安全；控制面靠外部同步补了一处，但 `KbFlowServiceImpl` 自己也会并发写 `context.getVariables().put(...)`？控制面剪枝只写 `prunedNodes` 集合（`:638`），未写变量，暂未触发；但 `StepCounter` 与变量同步范围仍需要一次完整的并发审计。【已验证（单点）/推断（整体）】

**2.1.2 条件边与剪枝（含真实缺陷）**
条件边不是调度器语义，而是"节点先执行、返回 `nextNodeIds`、调度器据此把未选中分支的下游加入 `prunedNodes`"（`KbFlowServiceImpl.java:634-639` + `HC/flow/dag/DagUtils.java:242-275`）。剪枝算法从"未选中的直接后继"出发做 BFS，把可达节点全部标记为待剪枝。**缺陷**：如果两条分支在下方汇聚到一个 join 节点（典型 if/else → 合并），`computePrunedNodesUuids` 会从"未选中分支根"遍历到 join 节点并加入剪枝集（`HC/flow/dag/DagUtils.java:258-274`；只有"选中的直接后继"在 `selectedSet` 中被豁免，`:245,267`）。而 join 节点通常不在 `nextNodeIds`（`nextNodeIds` 只包含条件节点命中的直接边目标，`KB/service/flow/bo/ConditionNodeBO.java:64-88`）。因此**菱形汇聚图里 join 节点会被误判为 SKIPPED**，下游依赖它的输出缺失。现有测试只覆盖"分支无汇聚"（`KBT/KbFlowServiceDagTest.java:153-214`：true-branch/false-branch/false-sub，没有 join），**该缺陷未被测试覆盖**。【已验证（代码）/推断（运行时表现）】

**2.1.3 环检测与循环**
生产引擎对环的处置是"拒绝执行"（`KbFlowServiceImpl.java:340-343`；`DagUtils.topologicalSortUuids` 抛 `IllegalStateException`，`HC/flow/dag/DagUtils.java:84-89`）。前端却提供 `LoopNode.vue`（`FE/nodes/LoopNode.vue`）并在枚举里有 `LOOP(8)`（`KB/dal/enums/FlowNodeTypeEnums.java:15`），但控制面 `NodeFactory` 的 switch 没有 LOOP 分支（`KB/service/flow/factory/NodeFactory.java:61-69`）→ 用户画布上加的 Loop 节点可以通过保存（convert 不校验），执行时 `createNode` 抛"不支持的节点类型"（`:69`），并因 `BaseNodeBO.execute` 捕获异常返回 ERROR（`KB/service/flow/bo/BaseNodeBO.java:144-148`）→ 整条 DAG 终止。**前端支持循环、后端不支持，且错误信息含糊**（见 §3 F-P1-12）。【已验证】

**2.1.4 上下文变量传递**
变量是单一扁平 JSON：`BaseNodeBO.prepareInput` 只按输入定义的 `name` 直接 `get(name)`（`KB/service/flow/bo/BaseNodeBO.java:51-76`），`processOutput` 把节点输出 `putAll` 回全局（`:81-92`）。节点靠自己写 `uuid + ".xxx"` 形式的键来隔离（`LLMNodeBO:82`、`KnowledgeNodeBO:152-156`、`HttpNodeBO` 的 Hermes 版同构、`ReplyNodeBO:49` 读 `sourceNodeId + "." + variableKey`；`StartNodeBO:37-40` 同时写 `node_1.` 与 `uuid.` 前缀）。风险：
- 键空间无强隔离，节点未命中变量时静默得到 `null`（无"变量未定义"错误），LLM prompt 里会出现 `{{var}}` 原样残留或空串（见 2.2.2）；
- 恢复时变量走 JSON 序列化/反序列化（`DagCheckpointManager.restoreVariables`，`HC/flow/dag/DagCheckpointManager.java:226-237`），`JSON.parseObject(..., Map.class)` 得到的是通用 Map/JSON 类型，数字/日期/嵌套结构类型会漂移；节点若依赖强类型（如 `Number`）可能行为不同；
- 大对象（检索切片、LLM 全文）在每层检查点被整体 JSON 序列化（`KbFlowServiceImpl.java:480,498` → `DagCheckpointManager.java:57-58`），变量越大检查点成本越高，且 `dag_checkpoints.completed_results/variables_json` 为 TEXT，没有大小上限（`DagCheckpointManager.java:500-516`）。大图/大上下文下这是 P1 成本点。【推断】

### 2.2 条件表达式求值

**2.2.1 生产实际用的是"手写字符串比较器"，既不是 SpEL 也不是 JS**
`KB/service/flow/bo/ConditionNodeBO.java`（生产）与 `HC/flow/bo/ConditionNodeBO.java`（Hermes 副本）逻辑相同：

- 先在 `format()` 里把 `{{var}}` 替换成变量字符串值（`KB/service/flow/bo/BaseNodeBO.java:157-176`），再**按字符串包含关系**判断运算符（`ConditionNodeBO.java:102-123`）；两侧 `Double.parseDouble` 成功则数值比较，否则 `String.compareTo` 字典序（`:131-157`）。
- 已知问题：
  1. **字符串常量不剥离引号**：KB 版 `compare(parts[0].trim(), parts[1].trim(), ...)`（`:105,108,111,114,117,120`）没有 `stripQuotes`（Hermes 版有，`HC/flow/bo/ConditionNodeBO.java:105` 调 `stripQuotes`）。因此 `{{status}} == 'done'` 会被求值为 `done` vs `'done'` → 永假。【已验证】
  2. **不支持属性/方法访问**：种子 SQL 使用 `"condition":"query.length > 20"`（`SQL:43`），表达式替换后无法解析 `query.length`（除非变量名恰为 `query.length`），会走字符串比较且大概率恒真/恒假。【已验证（种子）→推断（结果）】
  3. **表达式注入/语义漂移**：变量值被直接拼进表达式后再切分运算符；若变量值含 `==`、`>`、引号，比较语义被值改变（例如用户输入 `a==b` 令 `{{x}} == 'z'` 变成 `a==b == 'z'`）。没有类型系统、没有词法隔离。【已验证（代码）/推断（攻击面）】
  4. **缺失变量残留占位符**：`format` 在替换值为空时 `continue` 跳过 `appendReplacement`（`BaseNodeBO.java:169-171`），`{{x}}` 原样留在结果里，布尔兜底 `Boolean.parseBoolean("...{{x}}...")` 恒 false（`ConditionNodeBO.java:124`）。【已验证】
  5. `appendReplacement(sb, replacement)` 未调用 `Matcher.quoteReplacement`（`BaseNodeBO.java:172`）：变量值含 `$`/`\` 时抛 `IllegalArgumentException`/`IndexOutOfBoundsException`，被 `execute` 捕获后节点 ERROR（`BaseNodeBO.java:144-148`）→ 工作流中止。任何用户输入带 `$` 即触发（例如价格 `$99`）。【已验证（代码）/推断（触发条件）】

**2.2.2 存在但未使用的 SpEL 引擎**
`HC/flow/expression/SpelExpressionEngine.java:27-187` 是完整 SpEL 实现（`SimpleEvaluationContext.forReadOnlyDataBinding`，`:87-89`；危险关键字黑名单 `:40-46`；`{{var}}` 替换 `:124-142`；`contains/startsWith` 预处理 `:155-167`）。但全仓非测试引用为 0（仅 `ExpressionEngine` 接口 + 自身）：**生产条件节点没有用它**。若启用，它自身也有缺陷：字符串转义只把 `'` 换成 `\'`（`:147-149`），而 SpEL 单引号字符串的正确转义是 `''`；`appendReplacement` 同样未 quote。【已验证】

**2.2.3 真正使用不安全 SpEL 的地方在 Hermes（不在生产流量里）**
- `HC/flow/stategraph/engine/StateGraphScheduler.java:247-264`：条件边用 `StandardEvaluationContext` + 用户侧 `edge.getConditionExpression()` 直接 `parseExpression`，`StandardEvaluationContext` 允许 `T(...)` 类型引用、构造器与任意方法调用 → 若图定义可被外部（画布/DSL）写入，等价于**表达式注入 RCE**。
- `HC/flow/stategraph/engine/ConvergenceLoopGuard.java:89-103`：同类 `StandardEvaluationContext`，且 `setVariable("iteration", context.getLoopCount(exprStr))`（`:95`）误用表达式字符串当 edgeId。
- 当前这两条链路在 Hermes 进程且无可达生产调用（§2.8），故定级 P1（条件性），但必须在未来把 Hermes 图引擎接入生产前修复。【已验证（代码）/未知（未来可达性）】

### 2.3 检查点与恢复

生产持久化只有 `DagCheckpointManager`（`HC/flow/dag/DagCheckpointManager.java`）。SPI 两实现（`InMemoryRingBufferCheckpointStorage`、`JdbcStateCheckpointStorage`）**在生产中零引用**，只被契约测试使用（全仓非测试 import=0），因此"可插拔云原生状态检查点 SPI"目前是纸面能力（§2.8）。

**2.3.1 JDBC 路径的语义**
- 保存：`INSERT ... VALUES (...,1,...,'SUSPENDED',1,...) ON CONFLICT (runtime_id) DO UPDATE SET group_index=..., completed_results=..., variables_json=..., version=version+1`（`DagCheckpointManager.java:77-87`）。问题：
  1. **status 只在首次 INSERT 时写 `'SUSPENDED'`，更新时不写**（`:80-86`）。因此一个正在 `RUNNING` 中被恢复过的工作流，其 DB status 会永久停在 `RUNNING`（`wakeSuspendedWithLock` 把它改成 RUNNING 后，后续任何 save 都不再改回 SUSPENDED，`:290-294`）；
  2. **正常保存路径不带 fencing token**（fencing 保存方法是 `saveCheckpointWithFencingToken`，`:98-148`，但执行器只调 `saveCheckpointWithVariables`）：并发/多实例写时后写覆盖先写，写屏障只有事务性意义、不在主路径生效；
  3. **每次保存/加载都执行 `ensureTableExists()`**（save `:56`、load `:155`、wake `:322`、refresh `:402`、find `:434`），后者是 `CREATE TABLE IF NOT EXISTS` + 7 条 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`（`:495-528`）。PostgreSQL 下即使 no-op 也会走 DDL 解析与短时锁；每层每个工作流重复 8 条 DDL 是明确的 P1 性能/锁竞争问题；
  4. 异常全部 `log.warn` 吞掉（`:89-91`）→ 检查点丢失静默，上层认为"已持久化"继续执行。
- 内存降级路径（`jdbcTemplate == null`，`:60-75`）：`computeIfAbsent` 创建 status=SUSPENDED、fencingToken=1、version 自增；与 JDBC 语义不同（JDBC 路径不维护内存副本；测试里用内存路径，生产走 JDBC）。

**2.3.2 恢复语义（最严重的一类缺陷）**
设失败/挂起发生在第 G 层。执行器保存的 `groupIndex = G`（`KbFlowServiceImpl.java:380,392,439,451`），恢复时 `startGroupIndex = G + 1`（`:584`）：

- **挂起场景**：本层所有节点结果都已存在（并行层 `allOf` 后收集，`:419-433`；单节点层直接保存），`wakeSuspendedWithLock` 把 SUSPENDED 结果改成 SUCCESS（`DagCheckpointManager.java:253-264`），从 G+1 继续 → 语义上正确（前提是 checkpoint 真被持久化，见 §1.1 的 Bean 缺失）。
- **失败场景（P0 级）**：本层失败节点以 `ERROR` 状态进入 `resultMap` 并保存；恢复从 G+1 开始，**失败节点既不在 G+1 层也不会被重试** → 下游在缺少该节点输出（变量里无 `uuid.text`）的情况下继续执行，直到某节点因空值失败或产出错误结果。执行器从未把 ERROR 结果重新入队，也没有"重试该层/该节点"的任何分支。【已验证（代码）→推断（运行后果）】
- **层内崩溃/进程重启**：崩溃发生在第 G 层执行中时，最后成功保存的是 G-1 层（`:459-462` 在整层完成后才保存）。恢复时 G 层整层重跑 → 已成功节点的副作用（LLM 计费、HTTP POST、MCP 工具写操作）**重复执行**；全仓不存在幂等键（非测试代码 grep `idempoten` 命中为 0 于工作流执行路径），检查点也没有 per-node 提交记录。
- **剪枝状态不恢复（P1）**：控制面恢复入口只传 `initialResultMap`，`prunedNodes` 重新初始化为空集（`:354`），并没有像 Hermes `DagExecutor.executeWithCheckpoint` 那样用恢复结果的 `nextNodeIds` 反推剪枝集（`HC/flow/dag/DagExecutor.java:164-173`）。若崩溃发生在"条件节点所在层已完成、被剪枝节点所在层未完成"之间，恢复后**原先 SKIPPED 的节点会被物理执行**（因为循环里只对 `status == SUCCESS` 的已有结果跳过，`:368,403`；SKIPPED 不在缓存判定内）。
- **恢复使用最新画布定义**：`resumeFlow` 通过 `queryFlow(botId)` 重新读取当前节点/边（`:526`），但检查点里的 `groupIndex` 是旧图的分层序号；画布在挂起期间被编辑（增删节点/改边）后恢复会用旧层号对新图，可能跳层/错层。没有任何"流程版本号/定义摘要"校验（checkpoint 只有 `flow_id=botId`，`KbFlowServiceImpl.java:479`、`DagCheckpointManager.java:78-80`）。【已验证（代码）】
- **恢复上下文不完整**：恢复只重建 `RuntimeContextBO.variables`、`nodeMap`、`executeMode=BLOCK`、`botType`（`:550-562`），`messageList`（CHAT_FLOW 的对话历史）不恢复 → 多轮 chatflow 挂起恢复后丢失历史（`KB/service/flow/bo/LLMNodeBO.java:113-115` 依赖 `context.getMessageList()`）。

**2.3.3 租约/心跳 TTL 与崩溃恢复活性**
`DagCheckpointManager` 设计了完整租约 API：`refreshLease`（`:400-428`）、`wakeSuspendedOrRecoverLease`（双轨 CAS，`:316-394`）、`findExpiredRunningCheckpoints`（`:433-468`），以及 `LeaseLivenessRecoveryCoordinator`（`HC/flow/checkpoint/LeaseLivenessRecoveryCoordinator.java`）。实际状态：

1. **控制面恢复/挂起从不写 lease 字段**：`wakeSuspendedWithLock` 只改 `version/status`（`DagCheckpointManager.java:290-294`），不写 `lease_owner_id/lease_expire_at`；因此该表所有行的 lease 字段恒为 NULL，`findExpiredRunningCheckpoints` 的 `WHERE status='RUNNING' AND lease_expire_at < CURRENT_TIMESTAMP`（`:448`）永远匹配不到（NULL 比较不成立）。
2. **Coordinator 从未启动**：`startWatchdog` 只被测试调用（全仓非测试调用点 0；`HC/flow/checkpoint/LeaseLivenessRecoveryCoordinator.java:55-67`）。即便启动，它的 `recoverExpiredLeases`（`:73-97`）只做"抢租约 + 签凭单"，**不会触发工作流继续执行**（没有注入 `KbFlowServiceImpl`/任何执行回调），恢复后的流程会永远停在"status=RUNNING 但无人执行"的僵尸态。
3. **凭单"签名"无密钥**：`LeaseLivenessRecoveryReceipt.create` 用 `SHA-256(payload)` 当签名（`HC/flow/checkpoint/LeaseLivenessRecoveryReceipt.java:107-123`），`verifySignature` 用同一 payload 重算（`:80-88`）——任何能改写字段的人都能重算签名。`MessageDigest.isEqual` 的常量时间比对是正确的，但**没有 authenticity**，"彻底杜绝伪造"的注释（`:11`）不成立。同类问题见 `StateCheckpointRecord.verifyIntegrity`（`HC/flow/checkpoint/spi/StateCheckpointRecord.java:45-50`）、`WorkflowDebugReceipt`/`WorkflowExecutionReceipt`/`StateGraphExecutionReceipt`/`HitlInterventionAuditReceipt` 等一整套凭单（全部是无密钥 SHA-256 自签名）。【已验证】

**2.3.4 两套 SPI 实现的语义不等价**
`StateCheckpointStorageSpi`（`HC/flow/checkpoint/spi/StateCheckpointStorageSpi.java:9-41`）承诺"现存 token >= proposed token 则拒绝"：

- 内存环形实现：`saveCheckpointWithFencing` 的 `if (current.fencingToken() >= fencingToken && !current.runtimeId().equals(record.runtimeId()))`（`InMemoryRingBufferCheckpointStorage.java:37`）恒为 false（同一 runtimeId），真实判定只有 `:40` 的 `>`，**允许同 token 覆盖**，与接口契约不符；环形缓冲满时 `(writeCursor.getAndIncrement() % bufferCapacity)` 覆盖的槽位可能是**另一个 runtimeId** 的记录（`:45-48`），而 `indexMap` 中旧 runtimeId → 该槽位的映射没有清除 → 之后 `loadCheckpoint(旧Id)` 返回**别的运行**的快照（跨运行状态泄漏）；`archiveCheckpointToColdBuffer` 只移除索引不平移记录，注释称"保留供回滚"，但后续写入会覆盖（`:104-119`）。
- JDBC 实现：`saveCheckpointWithFencing` 是"先 SELECT fencing_token，再 UPDATE ... WHERE runtime_id=? AND fencing_token <= ?"（`JdbcStateCheckpointStorage.java:37-42,48-68`），且 UPDATE 写入的是 `record.fencingToken()` 而不是"库内 token+1"（`:99` 绑定 `record.fencingToken()`）。两个并发写者都能通过检查并成功 UPDATE（README 级 CAS 不成立）；`<=` 允许同 token；`tryAcquireLease` 也是 load→save（`:145-167`），非原子 → 双主。此外 `saveCheckpointWithFencing` 未创建 `hermes_flow_checkpoints` 表（全仓无 DDL），首跑会因表不存在直接失败（`:84-87` 只记日志）。**因此两种实现不等价，且都未达到其注释声明的 fencing 语义。**【已验证】

**2.3.5 stategraph/WAL 检查点（未接线）**
`WalDurableStateGraphCheckpointer`（`HC/flow/stategraph/checkpoint/WalDurableStateGraphCheckpointer.java`）是唯一"真写文件"的实现：`Files.writeString(..., APPEND)`（`:203-212`）。缺陷：

- 默认目录是 `java.io.tmpdir/qknow_stategraph_wal`（`:37-39`）——**容器/主机重启即丢**，与"冷启动事件重放、100% 零状态丢失"（`:23`）直接矛盾；
- `appendWalRecord` 捕获 IOException 只记日志（`:209-211`），`saveSnapshot` 仍更新内存索引并 `return true`（`:75-84`）→ 静默丢失；
- fencing 只存在内存 `fencingTokens`（`:35`、`:62-73`），重启后归零，未先 replay 就写入可以接受任意 token；
- 校验和覆盖 `String.valueOf(v)` 的 TreeMap（`:225-238`），嵌套 Map/List 的 `toString` 顺序不稳定 → 重放时可能校验失败被当作"损坏"跳过（`:184-188`）；同样是无密钥 SHA-256（无法防篡改）；
- `StateGraphScheduler` 默认 `checkpointer=null`（`HC/flow/stategraph/engine/StateGraphScheduler.java:43-77`），`setCheckpointer` 无生产调用 → 这套 WAL 从未启用。

### 2.4 HITL（人机协同）

**2.4.1 生产路径上 HITL 的真实动作**
控制面没有"审批节点类型"（KB 枚举里没有 APPROVAL/SUSPEND；只有 start/llm/reply/condition/knowledge/tool/loop）。生产 HITL 只有一种：**Tool 节点的高危工具门禁**——`ToolNodeBO` 依据工具名关键字（delete/drop/rm_rf/shutdown/transfer/execute_command）或 `config.requireApproval` 决定是否挂起（`KB/service/flow/bo/ToolNodeBO.java:81-119`），挂起返回 SUSPENDED（`:110-119`）。调度器收到 SUSPENDED 后：`runtimeDO.setStatus(SUSPENDED)` + `updateById` + `saveDagCheckpoint`（空操作）→ 返回（`KbFlowServiceImpl.java:387-397`）。**审批结果没有任何落库结构**：没有 approval 表、没有 decision 字段、没有 expiry；`humanInput` 只通过恢复请求体临时传入（`:112-115`）。

**2.4.2 前端/后端契约断裂**
- 前端调试面板订阅的是 `/kb/flow/testExecuteFlow`（`FEAPI` 的 `ProcessFlow.executeFlowStream` 固定 URL，`frontend/src/api/kb/bot/flow.js:60-88`），而后端调试模式挂起时**不向 SSE 发 SUSPENDED 事件**：`testExecuteFlow` 在返回 Flux 前同步执行，挂起路径把消息写进 `runtimeDO.output` 后直接返回（`KbFlowServiceImpl.java:393-396`），`resultFlux` 可能为 null → 返回 `CommonResult.error(...)`（`:143-147`）。前端等待的 `inner.status === 'SUSPENDED'`（`FE/components/WorkflowDebugRunPanel.vue:533-545`）在真实链路上不可达；面板里展示的审批工单是**前端按默认值本地构造的伪造对象**（`ticketId: TICKET-...`、`riskLevel: CRITICAL`、`nodeId: hitl_gate`，`:535-545`）。
- 前端点"批准"后调 `resumeFlow({runtimeId, humanInput})`（`FE/components/WorkflowDebugRunPanel.vue:686-700`），`runtimeId` 回退取 `payload.flow?.id`（`:537`）——`flow` 是画布数据（`KbFlowVO` 没有 `id` 字段，只有 `botId`，`KB/controller/admin/flow/vo/KbFlowVO.java:11-34`）→ 大概率 `undefined`；即使有值，调试路径使用 `createRuntimeContext`（不 insert，无 id，`KB/service/runtime/impl/KbRuntimeServiceImpl.java:59-67`），也不会有对应 checkpoint。⇒ **画布调试态 HITL 无论如何都无法恢复**。
- 正式流式 `/kb/flow/executeFlowStream` 用 `createSaveRuntimeContext`（有 runtime id，`KbFlowServiceImpl.java:222` + `KbRuntimeServiceImpl.java:77-86`），但 SSE 只回传回复文本（§1.3），**不把 runtimeId/挂起状态告知前端**；前端无从流中获取 runtimeId 的通道，只能去 `/kb/runtime/list` 人工翻（`FE/api/kb/bot/botLog.js:6`）。

**2.4.3 审批代理端点与内存态**
- `KB/controller/admin/ApprovalProxyController.java` 暴露 `/hermes/approval/pending|approve|reject|dev/create`，实现是直接读写 Hermes 的静态 `ConcurrentHashMap`：`PENDING = ApprovalNodeExecutor.getPendingApprovals()`（`:20-22`，返回的是**拷贝**，approve/reject 走静态方法改原表）；`/dev/create` 无条件注册测试审批（`:65-80`，无 profile 守卫）。
- `ApprovalNodeExecutor` 本身：`PENDING_APPROVALS` 进程内静态表（`HC/flow/node/ApprovalNodeExecutor.java:23`），`DEFAULT_TIMEOUT_HOURS=24` **声明未使用**（`:24`，全仓无引用），无 TTL、无持久化、无过期清理；`approve/reject` 只 `future.complete`（`:70-91`）。生产调度器从不调用 `ApprovalNodeExecutor.execute`（全仓非测试调用仅 `ReactiveWorkflowContractTest:104`）→ 代理端点在真实系统里**永远返回空/pending=[]**，approve 返回 404。前端 `frontend/src/views/kb/agent/components/ApprovalDialog.vue:75,94` 仍调用它们（agent 模块的对话审批场景），实际是空转。
- 另有 `HC/controller/ApprovalController.java` 与 KB 的 `ApprovalProxyController` **相同 `@RequestMapping("/hermes/approval")`**（`:14-17`）；因为控制面排除 `hermes.*` 扫描、Hermes 进程不扫描 KB，两个上下文各自只有一个映射，当前不会冲突；但若未来合并上下文/加 `@Import` 会立刻 `Ambiguous mapping` 启动失败。审计中作为 P2 记录。
- Hermes 里的 HITL"完整实现"（`HumanInTheLoopApprovalGate`、`WorkflowHitlReactiveGovernor`、`WorkflowSignalBus`、`TimeTravelSnapshot*`、`MultiAgentHandoff*`）全部只被测试/benchmark 引用（§2.8）。其中：
  - `HumanInTheLoopApprovalGate`：`pendingFutures/pendingTasks/completedApprovals` 三个无界内存 Map（`HC/flow/hitl/engine/HumanInTheLoopApprovalGate.java:20-22`），watchdog 超时 fail-close 实现正确（`:73-89`），但**决策只存内存**，进程重启即丢；`completedApprovals` 永不清理（`:108`）。
  - `WorkflowHitlReactiveGovernor`：工单表 `activeTickets` 无过期清理（`:61`）；`timeoutMicros` 只记录不生效（`:75`，全类没有 scheduler，`triggerWatchdogTimeout` 需外部调用，`:261-296`）；所谓"状态守恒检验"是把 `suspendedStateHash` 与**同一份不可变 baseline 变量**再哈希比较（`:80`、`:124-128`、`:315-332`），恒为 true，是装饰性检查；`computeStateHash` 用 `k=v;` 字符串拼接，无转义（`:320-323`），存在拼接歧义（`{"a":"1;b=2"}` 与 `{"a":"1","b":"2"}` 同哈希）。
  - `WorkflowSignalBus`：非 `@Component`（`HC/flow/hitl/signal/WorkflowSignalBus.java:16`），信号先到会因"无等待者"直接丢弃（`:35-56`），`registerPendingWait` 的 `orTimeout(...).exceptionally(...)` 作用在派生 stage 上（`:67-73`），等待方拿到的是 `CompletionException` 而非注释承诺的 `{error:SIGNAL_TIMEOUT}` 结果 → 超时语义断裂。可作为"Temporal signal"反面对照。
  - 结论：**HITL 的所有持久化/推送能力都不存在；控制面唯一的挂起结果是"runtime 行标 SUSPENDED + 返回"；核对结论是"内存态必丢"（进程重启、重启幂等、多实例部署三种场景全部丢）。**【已验证（代码/装配）】

### 2.5 自愈与补偿事务：真实 Saga 还是日志装饰？

把四层分开看：

1. **生产控制面**：无补偿、无重试。节点失败即 `saveRunError` + 终止（`KbFlowServiceImpl.java:376-385,435-444`）；节点内部也没有重试：`HttpNodeBO` 一次 `send` 失败即返回 failure；LLM/Knowledge/Tool 同理（`KB/service/flow/bo/LLMNodeBO.java:61-79`、`KnowledgeNodeBO.java:54-67`、`ToolNodeBO.java:121-157`）。"resume" 是唯一的重跑手段，但如上所述会跳过失败层。HTTP 节点在 Hermes 副本里同样无重试（`HC/flow/bo/HttpNodeBO.java:59-120`）。
2. **Saga 补偿引擎**：`SagaCompensationEngine`（`HC/flow/saga/SagaCompensationEngine.java`）算法是真实实现——按成功节点集构建转置图并 Kahn 逆拓扑（`:159-213`）、对实现 `CompensableNode` 的节点按 LIFO 补偿、3 次指数退避重试（`:125-154`）。但：
   - **没有任何生产节点实现 `CompensableNode`**：全仓唯一实现是测试内部类（`TS/flow/ReactiveWorkflowContractTest.java:557`）；
   - **没有任何生产调用**：`compensateOnRejection/compensateOnError` 调用点全在 `ReactiveWorkflowContractTest`（`:358,400,438,489`）；
   - 调度器没有 `nodeInstanceMap`（节点每层现场 new，`KbFlowServiceImpl.java:625`），即便调用也没有补偿实现可路由。
   ⇒ Saga 是"可运行但未接线的算法骨架"，实际效果为 0。
3. **transaction 包**：`SagaDistributedTransactionHealer`（`HC/transaction/engine/SagaDistributedTransactionHealer.java:27-134`）是通用逆拓扑 + 补偿回调 Map，**无持久化、无幂等键、无调用方**（生产引用 0）；`WorkflowTransactionControlBus`（`:19-108`）是"1000Hz/4096 槽位 Disruptor"名义的数组环形缓冲，无消费者与序列栅栏（`:48-56` 直接覆盖槽位），**不是 Disruptor**；`ContractEvolutionGovernor`/`WorkflowElasticScaler` 同样零生产引用。MCP 侧唯一"真实"补偿设计 `McpSagasCompensationGovernor`（`HC/tool/mcp/sagas/McpSagasCompensationGovernor.java:52-79`）把补偿项写进 `dag_checkpoints.compensation_log`，但只被测试使用（`TS/flow/checkpoint/Phase126McpLeaseLivenessContractTest.java:196`），且控制面 checkpoint manager 为 null（§1.1）→ 即使接线也写不进去。
4. **selfhealing 包**：`CognitiveSelfHealingStateMachine`（`HC/cognitive/selfhealing/engine/CognitiveSelfHealingStateMachine.java`）的"自愈"= 状态机 + 相似度死锁检测 + **向 prompt 追加一段"反事实负向约束"文本**（`:78-86`）；不是状态修复、不是补偿、不是重规划。`attemptEmbeddings` 无界增长（`:23,100-105`），Bean 为单例且持有可变状态（`:22-25`），多会话共享 → 会话间状态污染。生产引用 0。`CognitiveKernelControlBus`/`HierarchicalMemoryCompressor`/`UnifiedGraphCognitiveAligner` 同为零引用。
5. **唯一"半真实"的重试实现**在未接线的 stategraph 里：`NodeSelfHealingRouter`（`HC/flow/stategraph/engine/NodeSelfHealingRouter.java:51-104`）有类 Decorrelated Full Jitter 重试、致命异常甄别（`:130-150`）与 Fallback 路由；但 ① 只对**抛异常**的节点重试，节点返回 `status=2` 的失败结果不重试（`:58-71`）；② Fallback 递归调用自身（`:116`）没有 visited 集，fallback 链成环会无限递归；③ 该路由器只被 `VirtualThreadSuperstepExecutor`/`StateGraphScheduler` 使用，而这两者无生产入口。

**结论（对应必答问题 5）**：transaction/selfhealing 两包都不是生产 Saga，是"日志/凭单装饰 + 未接线的算法骨架"；生产失败节点既无补偿也无重试，唯一兜底是人工 `resumeFlow`（且语义有缺陷）。

### 2.6 流式与节点级回溯

| 能力 | 生产实现 | 结论 |
|---|---|---|
| SSE 协议 | Spring MVC `Flux<CommonResult<String>>`（`KbFlowController.java:53,74,100`） | 有 |
| 事件模型 | 仅 `{text,name}` 帧 + 固定 `"{}"` 首帧 + `"\n"` 分隔帧（`KB/service/flow/bo/ReplyNodeBO.java:66-101,169-174`）；另有 `{"event":"started"}`（`KbFlowServiceImpl.java:148`） | 无节点生命周期/无 status/无耗时 |
| 事件顺序 | Reactor `Flux.concat` 串行拼接（`ReplyNodeBO.java:86-101`）→ 单订阅内有序 | 【推断】单连接内有序 |
| 事件 id / seq | 无 | — |
| 断线重连 | 无 `Last-Event-ID`、无服务端游标；前端 `fetchEventSource`（`FEAPI:60-88`）断线后若重试会**重新 POST 同一个 body → 重跑整条工作流**（LLM 重复计费、副作用重复），且 `onerror` 直接 `throw err`（`:74-77`）通常直接终止 | 不可恢复 |
| 回放 | 控制面无；Hermes 侧 `RealtimeDagEventStreamer` 有 500 帧/会话环形历史（`HC/streaming/engine/RealtimeDagEventStreamer.java:25,77-83,138-144`），但 SSE 端点只在订阅瞬间推一次 snapshot（`qknow-hermes-starter/.../controller/HermesDagStreamController.java:39-77`），**不按 Last-Event-ID 补发**；且该 streamer 的唯一事件生产者是调试注入端点 `/api/hermes/stream/dag/inject`（`:79-86`），真实执行器 `FlowExecutor`/`DagExecutor` 从不 `publishEvent`（全仓 `publishEvent(` 调用点核对） | 名义上有、实际空转 |
| 节点级回溯 | 控制面写 `kb_runtime_node`（`KB/service/runtime/impl/KbRuntimeServiceImpl.java:123-133`，每节点一行），但**没有任何 Controller 暴露节点运行明细**（`KB/controller/admin/runtime/KbRuntimeController.java:36-59` 只有 list/get/delete；全仓无 RuntimeNodeController）；前端 `TraceWaterfall.vue`/`WorkflowTimeTravelWidget.vue` 的输入是本地 `PersistentSnapshotManager`（`FE/components/WorkflowDebugRunPanel.vue:420,500-578`），节点事件为前端本地合成（`evt_start`、`evt_stream`、`latencyMicros` 为 0），HITL 工单为本地伪造（§2.4.2） | 后端数据未暴露、前端展示为模拟 |
| 断点/时间旅行 | Hermes `DefaultWorkflowTimeTravelDebugger`（断点 + 暂停 Future + 步骤历史，`HC/flow/debug/impl/DefaultWorkflowTimeTravelDebugger.java:21-60`）、`WorkflowTimeTravelStateHub`（Merkle DAG/二进制提升/投机分支，`HC/workflow/timetravel/WorkflowTimeTravelStateHub.java:25-228`）均为**零生产引用**，也无 Controller 暴露 | 未接线 |

### 2.7 测试覆盖真实度

- 规模：`backend/tests` 中 flow/workflow/hitl/checkpoint/workflow 相关测试文件约 40 个、13,823 行；`qknow-module-kb-biz` 另有 4 个流程测试 751 行。但：
  - **`backend/tests` 全模块 0 个 `@SpringBootTest`**（全仓仅 6 个 `@SpringBootTest` 文件（分布在 qknow-server/qknow-module-dm/qknow-module-app/qknow-module-kmc），均不在流程测试中）——**没有任何测试启动过真实 Spring 上下文**，因此 §1.1 的 Bean 缺失（P0）不可能被现有测试发现；
  - 主 DAG 契约测试用测试替身替换真实检查点：`ReactiveWorkflowContractTest` 内部类 `InMemoryDagCheckpointManager extends DagCheckpointManager`（`TS/flow/ReactiveWorkflowContractTest.java:500`）并直接 `new DagExecutor(nodeFactory, checkpointManager)`（`:61-64`），绕过了真实 `saveCheckpointWithVariables` 的 JSON/DB 路径；
  - E2E 测试把被测对象整体 mock 掉：`DagE2ETest` 用 `mock(DagExecutor)`/`mock(FlowExecutor)`（`TS/integration/DagE2ETest.java:385-393`），验证的是 gRPC 到 Service 的字段映射与 mock 返回值；
  - 控制面 resume 测试用真实 `new DagCheckpointManager()`（无 JDBC，走内存降级）+ mock `NodeFactory`（`KBT/KbFlowResumeCheckpointTest.java:50-64`），覆盖了"跳过已完成层"的正向路径，但**没有覆盖失败层跳过、SQL 分支、Bean 缺失、二次挂起**；
  - "前端集成"契约测试实际是**纯 Java 本地模拟**：`Phase134FrontendDagHitlIntegrationContractTest` 用 `ConcurrentLinkedQueue` 模拟前端双缓冲并断言 `<=16.6ms`（`TS/hermes/benchmark/Phase134FrontendDagHitlIntegrationContractTest.java:40-73`），用 `Math.random()` 模拟网络抖动（`:90`）——不读前端源码、不启动浏览器，不能证明任何前后端契约；
  - Phase101/104/124/126/132/138/148/154/155/156 等"PhaseNNN 契约测试"整体模式是：对未接线的组件 `new` 出来后断言其内部行为（例如 `Phase126McpLeaseLivenessContractTest` 直接 `new DagCheckpointManager()` 的内存降级路径；该类在 `:28,:67,:106,:132,:166,:192,:278` 处均直接 `new DagCheckpointManager()`），因此**大量测试在证明死代码"自身正确"**，与生产装配无关；这类测试给出的"通过"不能作为系统性证据（Lead 的 `_lead-notes.md` §4 亦记录同类模式）。
- 覆盖正面：`DagUtilsTest`/`DagCheckpointManagerTest`/`ExpressionEngineTest`/`NodeSelfHealingRouterTest` 等单元测试对各自算法的边界覆盖尚可（如 `Phase125` 对剪枝/滑窗的逐阶段断言是有效算法测试）；但**没有任何测试覆盖两个 Spring 上下文的装配、真实 JDBC 检查点、控制面到 Hermes 的流执行**。

### 2.8 死代码与装配面统计（决定"真实体量"）

对被审计目录做两类统计（脚本启发式；排除 `src/test`）：

- 149 个被审 Java 文件（`flow/**` 120 + `workflow/**` 3 + `transaction/**` 11 + `cognitive/selfhealing/**` 8 + `agent/workflow/**` 3 + `agent/dag/**` 4，共 17,384 行）中：**53 个文件在非测试生产代码中零外部引用**；39 个文件仅被 1 个生产文件引用，且其中多数引用方本身零外部引用（如 `FlowStateStore` 只被 `FlowExecutor` 引用，而 `FlowExecutor` 只被 gRPC service 引用，gRPC `executeFlow` 无客户端）。典型零引用清单：`JdbcStateCheckpointStorage`、`InMemoryRingBufferCheckpointStorage`、`LeaseLivenessRecoveryCoordinator`、`HumanInTheLoopApprovalController`、`WorkflowOrchestrationControlBus`、`WorkflowSnapshotBranchManager`、`MultiAgentHandoff*`、`TenantMcpQuotaGovernor`、`CanonicalPrefixCacheTreeOptimizer`、`DynamicPrefixCacheTreeScheduler`、`WorkflowStudioReceipt/WorkflowForkReceipt/Phase15xMasterAuditReceipt`、`SagaCompensationEngine`、`SagaDistributedTransactionHealer`、`WorkflowTransactionControlBus`、`CognitiveSelfHealingStateMachine`、`WorkflowTimeTravelStateHub`、`DefaultWorkflowTimeTravelDebugger`、`HybridWorkflowEngine`、`SpelExpressionEngine`、`DurableStateGraphEngine`/`StateGraphScheduler` 等。
- 从 Hermes 进程入口（5 个文件：`HermesGrpcService`、`ApprovalController`、`HermesDagStreamController`、`SleepTimeMemoryAgent`、`HermesApplication`）出发做引用可达性 BFS：149 个被审文件中仅 **27 个（18%）可达**，122 个不可达（其中包含本报告 §2.3–§2.5 讨论的全部 checkpoint SPI/HITL/Saga/selfhealing/stategraph 组件）。
- 结论：本项目"工作流/HITL/durable execution"方向的代码体量（1.7 万行 + 3.9 千行控制面）中，**真正参与生产执行的只是控制面那 3.9 千行的一个子集**；其余绝大多数是测试自证、benchmark 展示或未来预留。这不必然是错误（研发储备可以存在），但任何"系统已具备 durable execution / Saga / 非模态 HITL"的对外表述都与装配事实不符，必须在文档与投标/验收材料中纠正。

---

## 3 缺陷与风险清单（P0/P1/P2）

> 定级原则：P0 = 生产功能不可用/数据错误/恢复语义错误；P1 = 正确性或安全风险显著、影响可信度/成本；P2 = 可维护性、装饰性风险、未来隐患。每条均给 `路径:行`；【已验证】或【推断】按 §0 定义。

| ID | 级别 | 证据（路径:行） | 问题 | 影响 | 验证状态 |
|---|---|---|---|---|---|
| F-P0-1 | P0 | `QKnowApplication.java:21-26`；`KbFlowServiceImpl.java:74-75,475-476`；`NodeFactory.java:39-40`；`HermesApplication.java:15-21` | 控制面 `@ComponentScan` 排除 `tech.qiantong.qknow.hermes.*`，但 `DagCheckpointManager`/`McpToolAdapter` 只在该包下注册 Bean，且注入为 `required=false` → 生产环境两依赖恒为 null | 检查点永不落库；`resumeFlow` 必抛异常（HITL/崩溃恢复 100% 不可用）；工具节点恒 NO_ADAPTER | 已验证（静态装配） |
| F-P0-2 | P0 | `KbFlowServiceImpl.java:380,392,439,451,584` | 失败/挂起层以 `groupIndex=G` 保存，恢复从 `G+1` 开始；ERROR 节点不重试、下游缺输入继续执行 | 失败后"恢复"会产出**错误结果**而非重试；不可接受的静默数据错误 | 已验证（代码）/推断（后果） |
| F-P0-3 | P0 | `KbFlowServiceImpl.java:507-593`；`KbRuntimeServiceImpl.java:123-133`；`KbRuntimeController.java:36-59`；`HC/flow/node/ApprovalNodeExecutor.java:23-24` | HITL 审批无持久化（无 approval 表/decision/expiry），`kb_runtime_node` 有写入但无 API 暴露；审批代理端点依赖从未被填充的静态内存表 | 审批结果只能靠"恢复请求临时变量"；重启/多实例即丢；前端审批 UI 后端支撑为空 | 已验证 |
| F-P0-4 | P0 | `SQL:54,57,76,79,135,138,180,...`（全文件 `provider` 0 次）；`KB/service/flow/bo/LLMNodeBO.java:49-53` | 种子工作流 LLM 配置只有 `model` 无 `provider`，而生产节点要求 `provider` 非空，否则抛"模型选择错误" | 所有由 `init_all_bot_workflows.sql` 初始化的工作流在第一个 LLM 节点必然失败 | 已验证（数据/代码比对） |
| F-P0-5 | P0 | `KbFlowTemplateServiceImpl.java:100-140,181-243`；`KbFlowNodeConvert.java:37-40` | 新建 Bot 自动生成模板时，节点配置放在 `data` 根（`data.model/prompt/content/toolName`），但运行时只读 `data.config` | 新建 Bot 的默认工作流不可执行（LLM 无 provider/model、reply 无 content、tool 无 toolCode） | 已验证 |
| F-P1-6 | P1 | `HC/flow/dag/DagUtils.java:242-275`；`KbFlowServiceImpl.java:634-639` | 条件分支剪枝会把"未选中分支下游可达"的所有节点剪掉，包括两条分支汇聚的 join 节点 | 菱形汇聚图 join 节点被误 SKIPPED，下游缺输出；现有测试未覆盖 | 已验证（代码）/推断（触发条件） |
| F-P1-7 | P1 | `KB/service/flow/bo/ConditionNodeBO.java:102-157`；`BaseNodeBO.java:157-176` | 条件求值是手写字符串比较：字符串常量不剥引号（`done` vs `'done'` 永假）、不支持属性访问（种子用 `query.length`）、`appendReplacement` 未 quote、变量值可改变运算符语义 | 条件路由大面积失效或误判；含 `$`/`\` 的用户输入令节点异常；表达式注入面 | 已验证 |
| F-P1-8 | P1 | `KbFlowServiceImpl.java:234-241`；`KB/service/flow/bo/LLMNodeBO.java:68-79`；`KB/service/flow/bo/ReplyNodeBO.java:66-68` | `executeFlowStream` 对同一个冷 `resultFlux` 做 `last.subscribe(...)` 再返回给 MVC 订阅 | 每次正式流式执行**重复调用 DeepSeek 至少 2 次**（成本/限流/一致性）；且保存的 output 是 `last()` 的 `"\n"` 帧而非答案 | 已验证（代码）/推断（成本后果） |
| F-P1-9 | P1 | `KB/service/flow/bo/ToolNodeBO.java:126-153`；`KB/service/flow/factory/NodeFactory.java:39-40` | MCP adapter 为 null 时返回 `{"status":"NO_ADAPTER"}` 但节点结果 `status=SUCCESS` | 工具调用静默失败，工作流误判成功（Lead 已独立确认同类问题） | 已验证 |
| F-P1-10 | P1 | `HC/flow/dag/DagCheckpointManager.java:56,77-91,155,290-294,448,495-528` | 检查点：每次操作执行 8 条 DDL；正常路径无 fencing；status 只 INSERT 时写 SUSPENDED；lease 字段从不写；DDL/异常吞掉；二次挂起 CAS 因 status=RUNNING 失败（`WHERE status='SUSPENDED'` 0 行） | DB 锁竞争/性能；恢复状态机不可靠；二次挂起后醒不来；失败静默 | 已验证 |
| F-P1-11 | P1 | `HC/flow/checkpoint/spi/impl/InMemoryRingBufferCheckpointStorage.java:37-48,104-119`；`JdbcStateCheckpointStorage.java:37-42,99,145-167` | 两 SPI 实现语义不等价：内存版允许同 token 覆盖、环形淘汰不清索引（跨 runtime 泄漏）；JDBC 版 SELECT→UPDATE 非原子、`<=` 允许同 token、token 不自增、表无 DDL | "云原生 checkpoint SPI"不可信；多实例会双主/覆盖 | 已验证 |
| F-P1-12 | P1 | `HC/streaming/engine/RealtimeDagEventStreamer.java:60-100,138-144`；`qknow-hermes-starter/.../controller/HermesDagStreamController.java:39-86`；`KB/service/flow/bo/ReplyNodeBO.java:63-102` | 节点级 SSE 唯一生产者是 `/inject` 调试端点；真实执行器不发布事件；重连不补发（无 Last-Event-ID/游标）；控制面 SSE 无事件 id/无节点事件/无重连语义 | 画布"实时调试/节点回溯"无后端支撑；断线重连会重跑工作流 | 已验证 |
| F-P1-13 | P1 | `KbFlowServiceImpl.java:340-343,584`；`KB/dal/enums/FlowNodeTypeEnums.java:15`；`KB/service/flow/factory/NodeFactory.java:61-69`；`FE/nodes/LoopNode.vue` | 后端拒绝任何环、无 LOOP 执行器，但前端提供 Loop 节点且保存不校验 | 用户可保存"带循环"的工作流，执行期才报"不支持的节点类型"；前后端契约不一致 | 已验证 |
| F-P1-14 | P1 | `HC/flow/dag/DagExecutor.java:164-173` vs `KbFlowServiceImpl.java:354,368,403` | 恢复时控制面不重建剪枝集（Hermes 版本有）；SKIPPED 不被视为已完成缓存 | 崩溃恢复后原剪枝分支被物理执行（副作用/成本）；两层实现语义漂移 | 已验证 |
| F-P1-15 | P1 | `HC/flow/checkpoint/LeaseLivenessRecoveryCoordinator.java:55-97`；`HC/flow/dag/DagCheckpointManager.java:400-468` | 租约 Coordinator 从未启动；即使启动也只抢租约不恢复执行；`findExpiredRunningCheckpoints` 因 lease 字段恒 NULL 永不命中 | "断点租约活性自愈"完全空转；崩溃工作流永久悬挂 | 已验证 |
| F-P1-16 | P1 | `InMemoryRingBufferCheckpointStorage.java:37-40`；`JdbcStateCheckpointStorage.java:42,55-67`；`LeaseLivenessRecoveryReceipt.java:80-88,107-123`；`StateCheckpointRecord.java:45-50` | 多个"密码学存证/自验真"实现是无密钥 SHA-256（可任意重算伪造）；fencing 比较用 `>=`/`<=` 允许同 token | 审计凭单不具备防伪性；跨实例写屏障失效（与 AGENTS.md §5 的存证铁律不符） | 已验证 |
| F-P1-17 | P1 | `HC/flow/stategraph/engine/StateGraphScheduler.java:247-264`；`ConvergenceLoopGuard.java:89-103` | 条件边/循环边用 `StandardEvaluationContext` 执行用户侧表达式（允许 `T()`/任意方法） | 一旦该引擎接入生产/图定义可写，即为 SpEL RCE | 已验证（代码）/未知（可达性） |
| F-P1-18 | P1 | `KB/service/flow/bo/LLMNodeBO.java:96-99`；`KB/service/flow/bo/ReplyNodeBO.java:35,78,124`；`KB/service/flow/bo/BaseNodeBO.java:51-76` | 空指针/None 处理：LLM 的 `inputDefArray`（`:45,96`）、Reply 的 `parseArray(output)`、`getFluxMap().get`、`nodeMap.get(...)`、LLM prompt 为 null 时 `format(null)` 都会 NPE/异常 | 任一配置缺失导致整条 DAG ERROR；错误信息仅"节点执行失败" | 已验证（代码） |
| F-P1-19 | P1 | `HC/flow/hitl/engine/WorkflowHitlReactiveGovernor.java:80,124-128,315-332`；`HC/cognitive/selfhealing/engine/CognitiveSelfHealingStateMachine.java:22-25,78-86,100-105` | 装饰性正确性：状态守恒检验是同一变量重哈希恒真；"自愈"是 prompt 追加文本；单例持有可变会话状态；集合无界 | 评估/审计结论被这些"通过项"误导；内存泄漏 | 已验证 |
| F-P1-20 | P1 | `HC/flow/stategraph/engine/VirtualThreadSuperstepExecutor.java:107-153` | 超步屏障 `allOf.get(timeout)` 超时后，try-with-resources 的 `ExecutorService.close()` 会等待全部任务结束；超时节点仍会写 `resultsMap/putNodeResult` | "超时熔断"可能被挂死节点无限拖住；超时后状态被污染 | 已验证（JDK 语义 + 代码）/推断 |
| F-P2-21 | P2 | `KbFlowServiceImpl.java:648-685` | 只校验 1 个 START/唯一/边端点；不校验可达性、不校验孤立节点 | 与 START 无关的孤立节点也会执行（入度 0 自成一层） | 已验证 |
| F-P2-22 | P2 | `KbFlowServiceImpl.java:357,627`；`KbRuntimeServiceImpl.java:123-133` | step 计数从 `initialResultMap.size()+1` 起；`kb_runtime_node` 每次执行都 insert，无 upsert | 恢复/重跑后 step 冲突、节点历史重复、无法做节点级回放 | 已验证 |
| F-P2-23 | P2 | `KbBotServiceImpl.java:64-75`；`KbFlowTemplateServiceImpl.java:52-76` | 模板初始化先 insert bot（事务内），失败 catch 仅 warn；`submitBatch` 先删后插（`KbFlowNodeServiceImpl.java:59-64`） | 模板失败后 Bot 处于"无流程"半态；画布保存失败会先删旧数据 | 已验证 |
| F-P2-24 | P2 | `HC/grpc/HermesGrpcService.java:31-61`；`KB/service/agent/HermesGrpcClient.java:63-96` | 控制面 gRPC 客户端只有 chat；`executeFlow` 无客户端；Hermes 进程的 `executeFlow` 成为不可达 API | 双栈维护成本；易误认为 Hermes 工作流在生产 | 已验证 |
| F-P2-25 | P2 | `ApprovalProxyController.java:65-80` | `/hermes/approval/dev/create` 无环境守卫、无鉴权注解 | 生产可被调用注入假审批（当前无害因为表是空的；接线后是风险） | 已验证 |
| F-P2-26 | P2 | `HC/controller/ApprovalController.java:14-17` vs `KB/controller/admin/ApprovalProxyController.java:16-17` | 两个上下文各有一个 `/hermes/approval` 映射 | 当前不冲突；未来合并上下文/Import 即启动失败 | 已验证 |
| F-P2-27 | P2 | `HC/workflow/timetravel/WorkflowTimeTravelStateHub.java`、`HC/flow/debug/**`、`HC/flow/audit/**`；`FE/components/hitl/**`、`FE/components/trace/**` | 时间旅行/断点/审计凭单/前端 HITL 全套 UI 与 Hermes 引擎均零生产引用，且无 Controller 暴露；前端展示为本地模拟数据 | 功能宣称与实现脱节；验收演示存在"假功能"风险 | 已验证 |

### 3.1 关于「窗口淘汰前判定阈值 → W+1 幽灵计数」的专项核查（必答问题 8）

AGENTS.md §11.2 与论文 Lemma/Algorithm 3 指控的是："阈值判定发生在滑动窗口淘汰之前，导致瞬时计数可达 W+1，产生幽灵计数误杀"。逐行核查结论：

1. **ReAct 工具调用守卫（当前实际生效的实现）：该缺陷不存在（证伪）。**
   `ReActStrictWindowCycleGuard.inspectToolCallWithReceipt`（`HC/agent/guard/ReActStrictWindowCycleGuard.java:114-196`）的时序是：连续重复短路（`:120-129`）→ **先淘汰**（窗口满 `>= windowCapacity` 时 `removeFirst` 并递减/删除计数，`:131-142`）→ 计算 proposedCount（`:144-145`）→ 阈值判定（`:148-156`）→ 交错转移判定（`:158-173`）→ 原子提交（`:175-185`）。淘汰发生在判定之前，因此"判定时窗口元素数 <= W"，不存在 W+1。
   手工推演（W=6, B=3，序列 A,B,C,D,A,E,A）：
   - 前 6 步窗口 `[A,B,C,D,A,E]`，A 计数=2；
   - 第 7 步 A：先淘汰最老 A → 计数=1，proposed=2 <3 放行，窗口 `[B,C,D,A,E,A]`；
   - 第 9 步 A 前窗口 `[C,D,A,E,A,B]`，先淘汰 C，proposed=2+1=3 → 熔断。
   与 `TS/hermes/agent/Phase125ReActStrictWindowContractTest.java:48-85` 的契约 2 断言一致（该测试正是"严格先淘汰后计数、消除 W+1"的设计用例，且实现与断言自洽）。**【已验证：当前实现无 W+1 幽灵计数】**
2. **同仓存在"先计数后淘汰"的实现，但被阈值/窗口比掩盖：`SwarmLoopGuard`。**
   `SwarmLoopGuard.inspectOutput`（`HC/agent/guard/SwarmLoopGuard.java:61-84`）顺序是：`window.addLast(fp)`（`:72`）→ 在**含当前元素的窗口**上统计 duplicates（`:73`）→ 阈值判定（`:74`）→ 最后才 `if (window.size()>6) removeFirst()`（`:80-82`）。这是标准的"判定前不淘汰"时序，窗口瞬时为 W+1=7。由于阈值 3 远小于窗口 6，该时序在当前参数下**不会改变判定结果**（只在 `MAX_SEMANTIC_REPETITIONS >= W` 时才会造成 1 次误杀），属于**潜在缺陷而非现行误杀**；此外 `sessionCallChains` 只在 `SupervisorAgent` 收尾时清理（`HC/agent/SupervisorAgent.java:180`），异常路径不清理会导致会话级深度累积。**【已验证：存在该反模式；当前参数下不影响判定 → 定级 P2，不作为"已存在 W+1 误杀"证据】**
3. **工作流工具节点里的 ReAct 守卫实际上没有记忆。**
   `ToolNodeBO` 在构造时 `new ReActCycleGuard(10,3)`（`KB/service/flow/bo/ToolNodeBO.java:32`），而控制面每次执行节点都 `nodeFactory.createNode(...)` 新建实例（`KbFlowServiceImpl.java:625`）→ 每个节点实例只被调用一次 `inspectToolCall`，滑动窗口永远只有 1 条记录。**因此在工作流引擎里，"重复工具调用有界拒绝"能力是不可能生效的**（窗口无历史）；该能力只在聊天 ReAct 循环中生效：`AgentOrchestrator` 每次 `chat()` 建一个 guard 并在多步工具调用间共享（`HC/agent/AgentOrchestrator.java:428-434`），并在 `wrapWithCycleGuard` 中同时调用 `checkStepLimit()` 与 `inspectToolCall()`（`HC/agent/AgentOrchestrator.java:1193-1230`），这才是真实的防循环层。【已验证】
4. 结论：**AGENTS.md 所指的 W+1 时序缺陷在 ReAct 守卫中已被修复（有实现 + 有契约测试）；它仍以反模式形式残留于 `SwarmLoopGuard`（P2）；而工作流 ToolNode 的"防重复"宣传与实现不符（守卫无状态，P1，已并入 F-P1-9 的证据链）。**

---

## 4 优化建议（最小改动）

原则：不引入 Temporal/LangGraph 等新框架；优先修装配与语义，其次补持久化与推送，最后清理装饰性代码。改动集合按 P0→P2 排序，每项给出最小文件集。

### 4.1 P0（先让"恢复/HITL"从"不可用"变成"可用"）

1. **恢复 Bean 装配（F-P0-1）**
   - 最小方案：在控制面新建一个显式配置类（例如 `qknow-server` 的 `config` 包）用 `@Bean` 显式注册 `DagCheckpointManager` + Hermes `McpToolAdapter`（或把它们移到未排除的共享包，但迁移成本更大）。校验方式：写一个 `@SpringBootTest` 断言 `applicationContext.getBean(DagCheckpointManager.class) != null`（当前测试体系没有 Spring 测试，这是必要的补齐）。
   - 同步：把 `KbFlowServiceImpl` 的 `@Autowired(required=false)` 改为必需注入，让"缺 Bean"在启动期 fail-fast，而不是运行期空转。最小改动：`KbFlowServiceImpl.java:74-75` + 新增 1 个 `@Configuration` 文件。
2. **修正恢复层号语义（F-P0-2 / F-P1-14）**
   - 不改检查点写入格式，只改恢复侧：`startGroupIndex = checkpoint.getGroupIndex()`（`KbFlowServiceImpl.java:584`），并把 `resultMap` 中状态为 `SUCCESS` 的结果视为"已完成缓存"（`:368,403`），`ERROR`/`SUSPENDED` 结果从 `resultMap` 移除后重跑该层；同时恢复时按已恢复结果的 `nextNodeIds` 重建 `prunedNodes`（照搬 `HC/flow/dag/DagExecutor.java:164-173` 的逻辑，约 10 行）。
   - 恢复时对 `queryFlow` 的图定义做一次一致性校验：把 flow 定义（节点 uuid 集合 + 边数）的 SHA-256 存进 `dag_checkpoints`（新增 1 列）并在恢复时比对，不一致则拒绝并提示"画布已变更，需重新执行"（防止新图套旧层号）。
3. **修正 HITL 契约（F-P0-3）**
   - 正式流式 `/kb/flow/executeFlowStream` 在 SSE 首帧（`started`）里带上 `runtimeId`；挂起时追加一条 `{"event":"SUSPENDED","runtimeId":...,"nodeId":...,"reason":...}` 事件（改 `ReplyNodeBO`/`KbFlowServiceImpl`，约 15 行），前端 `WorkflowDebugRunPanel` 不再使用伪造 ticket，而是用该事件驱动审批 UI。
   - 新增审批持久化最小表（`kb_flow_approval`：runtime_id、node_uuid、tool、reason、status、decided_by、decided_at、expire_at），由挂起路径写入、由 `/kb/flow/approval/decide` 写入决策、由恢复路径读取；若不想建表，最小可行替代是把 decision/expiry 写进 `kb_runtime` 的 output/扩展 JSON 列并新增 decide 接口（不推荐，难以审计）。同时给审批加 TTL（默认 24h，与 `ApprovalNodeExecutor.DEFAULT_TIMEOUT_HOURS` 的意图对齐），超时按 fail-close 置 ERROR。
4. **修正种子/模板数据契约（F-P0-4/5）**
   - `SQL` 中给所有 LLM 节点补 `"provider": <ai_api_key.id>`（或改为后端按唯一 DeepSeek key 自动解析）；`KbFlowTemplateServiceImpl.buildTemplateDsl` 的节点统一输出 `{data:{label, config:{...}, input:[], output:[]}}`（把 model/prompt/content/toolName 移入 `config`）。最小改动：SQL 12 处 LLM 配置 + 模板 4 个 `create*Data` 方法。
   - 在 `KbFlowNodeConvert.toDO` 增加"未知 type 不静默降级 START"的显式失败（当前 `:29-32` 会把任何未知类型当开始节点，掩盖配置错误）。

### 4.2 P1（正确性/安全/成本）

5. **条件求值统一与安全化（F-P1-7）**
   - 最小改动：`ConditionNodeBO.evaluateExpression` 改用 `SpelExpressionEngine`（已存在），并把其字符串替换改为"变量以 `#vars['name']` 形式注入 + `SimpleEvaluationContext`"而非拼接字符串（约 40 行），这样同时解决引号、类型、注入问题；给用户语法做白名单（`==,!=,>,<,>=,<=,&&,||,!` + 字符串/数字字面量），并用 `Matcher.quoteReplacement`。
   - 前后端 schema 统一：`FE/index.vue:517-523` 与 `useWorkflowAddNode.js:49-50` 保存的是 `cases:[{conditions:[]}]`，后端 `ConditionNodeBO:40` 只认 `config.conditions`。最小方案：后端兼容 `cases` 结构（按 `cases[].conditions[]` 逐条求值，全部满足即命中该 case），或前端序列化时归一化为 `conditions`（推荐后端兼容 + 前端迁移，避免存量数据失效）。
6. **修复菱形汇聚剪枝（F-P1-6）**
   - 最小改动：`DagUtils.computePrunedNodesUuids` 在 BFS 中排除"仍可从已选中分支到达"的节点（对选中的后继做一次可达集，求差集），约 20 行；新增 1 个菱形汇聚单元测试。
7. **修复流式双订阅与输出落库（F-P1-8）**
   - `executeFlowStream` 删除 `last.subscribe(...)`（`:234-238`），改在返回的 Flux 上 `doOnNext` 累积文本、`doOnComplete` 落库（模式照抄同文件 `executeChatFlow:268-277`）；同时 `ReplyNodeBO` 不再注入 `"{}"` 首帧或由控制器过滤。
8. **工具节点失败语义（F-P1-9）**
   - `ToolNodeBO`：adapter 为 null 或返回 NO_ADAPTER 时返回 `NodeRunResultBO.failure`（并在消息中给出"工具网关未配置"），而不是 SUCCESS；若确认需要"无工具时降级"，则必须使用独立状态（如 `SKIPPED`）并在 UI 明确提示。
   - 若短期内让工具真正工作：在控制面显式注册 Hermes `McpToolAdapter` Bean（与 4.1.1 同一配置类），并核实其 `@PostConstruct` 隐式注册行为（Lead 的 L-P1-3）不会引入凭据面。
9. **检查点/租约语义统一（F-P1-10/11/15/16）**
   - 表 DDL 只在启动期建（`@PostConstruct` 一次），从 `save/load/wake` 路径移除 `ensureTableExists()`；恢复路径使用 `saveCheckpointWithFencingToken`（先 `wakeSuspendedOrRecoverLease` 拿 token，再写）；`wakeSuspendedWithLock` 增加 `WHERE` 同时更新 `status='RUNNING'` 后，恢复成功时把 status 写回 `COMPLETED` 或删除行；控制面挂起时写入 `lease_owner_id/lease_expire_at` 并由执行线程周期 `refreshLease`（最小实现：在 `executeDagPipelineWithResume` 每层成功后 `refreshLease`，TTL=2×层超时）。
   - 若要保留 Coordinator：为它加"恢复回调"注入点（调用 `KbFlowServiceImpl.resumeFlow` 或发内部事件），并把 `startWatchdog` 挂到 `@Scheduled`/`@PostConstruct`；否则应在文档中删除"自愈"宣称。
   - 两个 SPI 实现二选一并修正：oracle 建议保留 JDBC 版、删除内存环形版（或将其降级为"仅单测"并标注）；JDBC 的 UPDATE 改为 `SET fencing_token = fencing_token + 1 WHERE runtime_id=? AND fencing_token = ?`，租约获取改为单条条件 UPDATE ... RETURNING。
   - 凭单签名改为 `HmacSHA256(payload, serverKey)` 或改为"不声称防伪、仅做一致性校验"并在 UI/文档纠正措辞（成本最小的是改注释与命名）。
10. **节点级事件流与断线语义（F-P1-12）**
    - 最小可用方案：控制面在 `executeSingleDagNode` 前后发事件（Reactor `Sinks.Many` 或每 runtime 一个 `LinkedBlockingQueue`），SSE 端点改为 `Flux` 流并支持 `Last-Event-ID`：事件持久化到 `kb_runtime_node`（已有表），重连时先按 `id > lastId` 补发再续流；事件 id 用 `runtimeId:step` 单调序号。这样同时补齐"节点级回溯"（新增只读 Controller `GET /kb/runtime/{id}/nodes`，约 30 行）。
    - Hermes 侧 `RealtimeDagEventStreamer` 若要保留，必须让 `DagExecutor` 在节点前后 `publishEvent`，否则删掉以免误导。
11. **循环能力对齐（F-P1-13）**
    - 短期：`KbFlowNodeConvert.toDO` 对 LOOP 类型显式拒绝保存/执行（"当前版本不支持循环节点"），前端在 LoopNode 上加"未启用"标记；不要把用户数据静默保存成不可执行图。
    - 中期（需产品决策）：若产品必须支持循环，接入 Hermes `StateGraphScheduler` 的 LOOP_BACK 能力但必须先解决 F-P1-17 的 SpEL RCE 与 F-P1-20 的超时问题。
12. **空值防御（F-P1-18）**：`LLMNodeBO` 对 `input`/`prompt` 为 null 做默认值（`"[]"`/空串）；`ReplyNodeBO` 对 output 为 null 用空数组、对 `getFluxMap()`/`nodeMap` 做判空；`BaseNodeBO.format` 对 null 入参返回 null 而非 NPE。约 20 行。

### 4.3 P2（可维护性/清理）

13. 启动期做一次"工作流引擎自检"：校验唯一 START、可达性、LOOP/未知类型、条件 schema、LLM provider 是否可解析，把错误前移到保存/发布阶段（新增 `validateWorkflow` 规则，`KbFlowServiceImpl.java:648-685`）。
14. `kb_runtime_node` 改为 `(runtime_id, node_uuid, group_index)` upsert + 自增 seq，避免恢复后 step 冲突（`KbRuntimeServiceImpl.java:123-133`）。
15. 删除或标注 `HC/flow/hitl/**`、`HC/flow/checkpoint/spi/**`、`HC/transaction/**`、`HC/cognitive/selfhealing/**`、`HC/workflow/timetravel/**` 中零引用组件的"生产级"措辞（改为 experimental/spike），并对其中的"1000Hz/50ns/100%/0.0%"等注释做降级（当前无任何 benchmark 证据支持）。
16. 为 `SwarmLoopGuard` 修正"先淘汰后判定"顺序（把 `removeFirst` 移到 `addLast` 之前），并给 `sessionCallChains` 加 TTL/异常清理（`HC/agent/guard/SwarmLoopGuard.java:61-84`）；成本约 10 行，消除潜在 W+1 反模式。
17. 前端：模型字典结构对齐（后端 `getModelDict` 返回分组 `{provider, models[]}`，`useWorkflowMeta.js:130-137` 按扁平 `item.model` 映射 → 前端拿不到模型项且 `normalizeProviderValue` 会把 provider 转 `""`，`LlmNodeConfigForm.vue:318-333`），这是"画布选不了模型"的直接原因；调整为遍历 `group.models` 并保存 `provider=group.provider`（约 15 行）。
   证据：`backend/qknow-module-ai/qknow-module-ai-biz/src/main/java/tech/qiantong/qknow/module/ai/service/modelMarket/impl/AiModelServiceImpl.java:149-185`（分组返回）vs `FE/composables/useWorkflowMeta.js:130-156`（扁平映射 + 默认兜底）、`FE/components/nodeConfig/LlmNodeConfigForm.vue:318-333,380-386`（分组 `group.options` 消费）。【推断：需浏览器验证 UI 表现】

---

## 5 与 Temporal / LangGraph 的差距

先给本项目"durable execution"能力定位：以 Temporal 的语义维度衡量，**当前生产工作流引擎是"有检查点表的进程内调度器"，不是 durable execution**。差距按维度对照：

| 维度 | Temporal | LangGraph | 本项目（生产控制面） | 差距 |
|---|---|---|---|---|
| 持久化模型 | Event History（每命令/事件全量持久化） | Checkpointer（thread_id + superstep 快照，可 Postgres/SQLite/内存） | `dag_checkpoints` 单行覆盖式快照（层粒度、`completed_results`+`variables` JSON） | 无事件历史、无版本化；覆盖写导致并发/重放不可审计 |
| 重放确定性 | 确定性重放（Workflow 代码限制、SideEffect/Activity 分离） | 无强制确定性（图节点即函数），但 checkpointer 可按 superstep 恢复 | 无重放概念；恢复=从层号继续，节点内外部副作用不隔离 | 崩溃重放会重复副作用；无法保证确定性 |
| 活动/节点重试 | Activity RetryPolicy（指数退避、最大次数、非重试错误） | 节点内自行实现（无内建重试） | **无任何节点级重试**；失败终止（§2.5） | 需自建，且当前无补偿实现 |
| 幂等 | Workflow ID + 活动幂等键、去重 | 取决于用户实现 | 无幂等键；恢复/重试必然重复副作用 | 缺失 |
| Signal / Update | 持久化 Signal，可在 Workflow 任意点等待/查询 | `interrupt()` + 外部恢复（Command(resume=...)） | 无持久 signal；内存 `WorkflowSignalBus` 未接线且先到信号丢失 | HITL"审批等待"没有等价原语 |
| Timer | 持久化 Timer（可跨月） | 无内建 | 无持久 timer；审批超时未实现（`DEFAULT_TIMEOUT_HOURS` 未使用） | 超时/延迟节点不可用 |
| Query | Workflow Query 可读运行中状态 | `get_state()`/`get_state_history()` | 只能查 `kb_runtime`/`kb_runtime_node` 表快照（且无节点 API） | 可观测性缺失 |
| 版本/兼容 | Workflow Versioning / 兼容性策略 | 无强版本策略 | 无版本号；画布变更后恢复用新图套旧层号（F-P0-2 补充） | 高风险 |
| 中断恢复 | 进程重启后由服务自动恢复（workflow task 重新调度） | Checkpointer 恢复（应用侧调用） | 依赖人工 `resumeFlow`；且 Bean 缺失时直接不可用；无自动恢复扫描（Coordinator 未启动且不执行） | 活性缺失 |
| 并发/吞吐 | Task Queue + Worker 池，线性扩展 | 取决于实现 | 单进程虚拟线程、层同步屏障、每层 DDL、全量变量重写 | 大图/多实例扩展性弱 |
| 可观测 | Event History + Web UI + 指标 | 依赖 LangSmith/自建 | 仅日志；无事件流（§2.6） | 缺 |

**结论**：
1. 当前生产的"检查点+HITL"在语义上更接近"LangGraph checkpointer + interrupt 的早期半成品"，但缺少 LangGraph 最关键的两个约定：checkpoint 与执行线程/状态图绑定（thread_id、superstep、状态通道），以及 `interrupt` 的"持久化中断 + 携带恢复值"契约。Hermes 的 `stategraph` 包（BSP 超步 + WAL 快照 + `resumeFromLatestCheckpoint`）在结构上更接近 LangGraph，但**未接线**且 WAL 落在 `/tmp`。
2. 不建议引入 Temporal：本项目的流程是"单租户/单集群内的 AI 工作流"，节点数少、状态以 JSON 变量为主，Temporal 的确定性限制（时间/随机/网络必须在 Activity/副作用 API 内）会与现有 LLM/HTTP 节点实现冲突，改造成本远超收益。**更小的路径是：保留现有调度器，补齐 (a) 事件历史表 + 层/节点幂等键、(b) 审批持久化表 + TTL、(c) 节点级 SSE + Last-Event-ID、(d) Bean 装配与恢复语义修正**——这四项即可让系统在"单集群可恢复"意义上接近 LangGraph 的 checkpointer 能力，而不必承担 Temporal 的运维复杂度。
3. 若未来要对外宣称"durable execution / 自愈 Saga / 非模态 HITL"，必须先完成 §4.1/4.2 的 P0/P1 项并提供 `@SpringBootTest` 级别的端到端验证（当前 0 个 Spring 集成测试无法支撑任何此类宣称）。

---

## 6 未验证与需 Lead 决策事项

### 6.1 未验证（需要运行/环境）

1. **生产环境是否真的不启动 Hermes 进程**：代码上控制面不含 Hermes Bean，但部署是否总是同时运行 `qknow-hermes-starter`（独立 gRPC 服务）未知；若某些部署只跑控制面，则 §1.1 的 P0 影响面为 100%；若同时跑 Hermes，也改变不了"控制面工作流不使用 Hermes 执行器"的结论。
2. **前端画布 LLM 模型选择的实际 UI 表现**：§4.3 第 17 条的字典结构错配为静态推断，需在浏览器中确认（本审计只读，不启动前端）。
3. **DeepSeek 实际调用次数与成本**：`executeFlowStream` 双订阅导致 2× 调用是代码事实，但实际是否两次都完成取决于客户端断开时机与 Reactor 取消，需要运行时 APM/token 统计验证。
4. **JDBC 检查点在高并发下的行为**：`ON CONFLICT` 覆盖写与多实例并发、`ALTER TABLE` 的锁竞争属推断，需要 PostgreSQL 环境压测。
5. **`kb_runtime_node`/`kb_runtime` 的实际数据形态**：是否存在历史脏数据（step 冲突、重复行）未查库；数据库快照不在只读审计范围内。
6. **Hermes `stategraph`/WAL 若被启用的正确性**：checksum、replay、fencing 的运行时行为未验证（本报告只做静态分析）。

### 6.2 需 Lead 决策

1. **产品口径**：是否继续对外宣称"工作流 durable execution / 自动自愈 / 非模态 HITL 审批岛屿"？在本报告证据下建议统一改为"工作流编排（进程内）+ 人工恢复"或先把 §4.1 完成再宣称。这是影响验收/投标材料的第一决策。
2. **双引擎治理**：控制面 `KbFlowServiceImpl` 与 Hermes `FlowExecutor`/`stategraph` 两套执行器长期并存（`flow/**` 149 文件中 82% 从 Hermes 入口不可达）。建议明确"控制面引擎为唯一生产引擎，Hermes 侧 flow 降级为实验/待迁移"，或反向迁回 Hermes 并补 gRPC `executeFlow` 客户端；**不建议继续双向维护**。
3. **HITL 落地范围**：审批模型是 (a) 只做"工具高危门禁 + resume"，还是 (b) 做完整审批中心（多级审批、驳回补偿、TTL、审计）？前者工作量约 1-2 人日（§4.1.3 最小表 + SSE 事件），后者需要新表/新前端流程。
4. **是否接线 Saga 补偿**：若产品需要"驳回后回滚已执行副作用"，必须为 HTTP/MCP/写库类节点定义补偿动作与幂等键；若不需要，应删除/封存 `flow/saga`、`transaction` 并停止对其的契约测试投入（当前在测试死代码）。
5. **Java 21 隔离环境**：本审计严格遵守只读约束未运行任何 Maven；最终修复验证必须在 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem` 下进行（Lead 的 L-P0-1 Dockerfile JDK17 问题会阻断所有修复验证，需先解决）。
6. **测试策略**：是否允许新增 2 个 `@SpringBootTest` 级集成测试（H2/Testcontainers PostgreSQL）作为 P0 修复的验收门槛？当前测试体系（0 Spring 测试、大量自证型 Phase 契约测试）无法防止 F-P0-1 类问题再次发生。

---

## 附录 A：证据索引（按主题，路径:行）

### A.1 装配与进程边界
- `backend/qknow-server/src/main/java/tech/qiantong/qknow/server/QKnowApplication.java:19-29`（`@EnableScheduling`、排除 hermes 包、排除 DataSource 自动配置）
- `backend/qknow-hermes/qknow-hermes-starter/src/main/java/tech/qiantong/qknow/hermes/HermesApplication.java:15-21`（Hermes 扫描范围）
- `backend/qknow-module-kb/qknow-module-kb-biz/pom.xml:95-113`（依赖 hermes-core/proto/grpc-stub）
- `KB/service/flow/impl/KbFlowServiceImpl.java:74-75,475-476,511-513`（`required=false` 注入 + null 空转 + resume 抛错）
- `KB/service/flow/factory/NodeFactory.java:39-40`；`KB/service/flow/bo/ToolNodeBO.java:10,126-131`（MCP adapter 注入与 NO_ADAPTER）
- `KB/service/agent/HermesGrpcClient.java:63-107`（只有 chat/健康检查）
- `HC/grpc/HermesGrpcService.java:63-82`（executeFlow 实现，无客户端）

### A.2 控制面调度器
- `KB/service/flow/impl/KbFlowServiceImpl.java:77`（虚拟线程执行器）
- `:128-151`（testExecuteFlow 同步执行 + started）
- `:218-242`（executeFlowStream + 双订阅）
- `:252-281`（executeChatFlow 正确累积模式）
- `:295-307`（executeFlow/textExecuteFlow）
- `:318-473`（DAG 管道主循环：340 判环、346 分层、354 剪枝集、359-463 逐层、363-397 单节点、398-456 并行、459-462 层检查点、465-470 成功清理）
- `:475-504`（saveDagCheckpoint）
- `:507-593`（resumeFlow：515 加载、526 新图、534 唤醒、541-547 变量、584 层号+1）
- `:598-643`（executeSingleDagNode：610 剪枝、625-631 执行+落库、634-639 剪枝计算）
- `:648-685`（validateWorkflow）

### A.3 检查点/恢复/租约
- `HC/flow/dag/DagCheckpointManager.java:52-92`（save）、`:77-87`（upsert/status）、`:98-148`（fencing，主路径不用）、`:153-185`（load）、`:206-242`（restore/hasSuspended）、`:247-307`（wake/CAS）、`:316-394`（双轨 CAS）、`:400-428`（refreshLease）、`:433-468`（findExpired）、`:473-493`（compensation）、`:495-528`（DDL）
- `HC/flow/dag/DagExecutor.java:32-34,47-52,64-125,134-265`（Hermes 版调度/恢复/剪枝反推）
- `HC/flow/checkpoint/spi/StateCheckpointStorageSpi.java:9-41`
- `HC/flow/checkpoint/spi/StateCheckpointRecord.java:28-50`
- `HC/flow/checkpoint/spi/impl/InMemoryRingBufferCheckpointStorage.java:32-49,61-101,104-119`
- `HC/flow/checkpoint/spi/impl/JdbcStateCheckpointStorage.java:31-88,90-103,145-197`
- `HC/flow/checkpoint/LeaseLivenessRecoveryCoordinator.java:19-46,55-97,102-117`
- `HC/flow/checkpoint/LeaseLivenessRecoveryReceipt.java:13-38,43-88,90-123`
- `HC/flow/stategraph/checkpoint/WalDurableStateGraphCheckpointer.java:37-49,56-85,164-212,225-246`
- `HC/flow/stategraph/checkpoint/StateCheckpointRepository.java:21-90,115-195`
- `HC/flow/stategraph/checkpoint/MemoryDurableCheckpointer.java:17-51`
- `HC/flow/stategraph/engine/DurableStateGraphEngine.java:114-341`
- `HC/flow/stategraph/engine/StateGraphScheduler.java:43-77,86-207,247-264`
- `HC/flow/stategraph/engine/VirtualThreadSuperstepExecutor.java:69-171`
- `HC/flow/stategraph/engine/NodeSelfHealingRouter.java:51-158`

### A.4 HITL
- `KB/service/flow/bo/ToolNodeBO.java:81-119`（高危门禁/挂起）
- `KB/service/flow/impl/KbFlowServiceImpl.java:387-397,446-456`（挂起落库路径）
- `KB/controller/admin/ApprovalProxyController.java:20-22,42-63,65-80`
- `HC/flow/node/ApprovalNodeExecutor.java:21-24,36-64,70-98`
- `HC/controller/ApprovalController.java:14-20,43-75`
- `HC/flow/hitl/engine/HumanInTheLoopApprovalGate.java:18-27,49-57,62-115,133-136`
- `HC/flow/hitl/engine/WorkflowHitlReactiveGovernor.java:28-99,117-216,261-332`
- `HC/flow/hitl/signal/WorkflowSignalBus.java:16-90`
- `HC/flow/hitl/engine/WorkflowOrchestrationControlBus.java:20-108`
- `HC/flow/hitl/engine/CanvasStreamEventAggregator.java:16-109`
- `HC/flow/hitl/HumanInTheLoopApprovalController.java:21-39`（非 Controller）
- `KB/service/flow/bo/ReplyNodeBO.java:66-101`（流式事件模型）
- `HC/streaming/engine/RealtimeDagEventStreamer.java:22-163`
- `qknow-hermes/qknow-hermes-starter/.../controller/HermesDagStreamController.java:39-86`

### A.5 条件表达式/剪枝/节点
- `KB/service/flow/bo/ConditionNodeBO.java:33-158`
- `KB/service/flow/bo/BaseNodeBO.java:51-92,114-176`
- `HC/flow/bo/ConditionNodeBO.java:97-169`
- `HC/flow/dag/DagUtils.java:39-92,126-191,232-275`
- `HC/flow/expression/SpelExpressionEngine.java:27-187`
- `HC/flow/stategraph/engine/StateGraphScheduler.java:247-264`；`ConvergenceLoopGuard.java:89-103`
- `KB/service/flow/bo/LLMNodeBO.java:42-139`
- `KB/service/flow/bo/KnowledgeNodeBO.java:41-165`
- `KB/service/flow/bo/ToolNodeBO.java:29-158`
- `KB/service/flow/bo/ReplyNodeBO.java:31-161`
- `HC/flow/bo/LLMNodeBO.java:40-127`；`HC/flow/bo/HttpNodeBO.java:41-120`；`HC/flow/bo/AggregatorNodeBO.java:34-160`

### A.6 数据模型/种子/模板/工作流
- `SQL:10-104`（Bot 9 全量节点/边，含 43 cases、54/57 LLM 缺 provider、64-70 tool、86-92 reply 空 output）
- `SQL:108-330`（Bot 3/2/5 等，LLM 均缺 provider）
- `KB/dal/enums/FlowNodeTypeEnums.java:8-70`
- `KB/convert/flow/KbFlowNodeConvert.java:26-71`；`KbFlowEdgeConvert.java:23-47`
- `KB/service/flow/impl/KbFlowNodeServiceImpl.java:59-64`（先删后插）；`IKbFlowNodeService` 等
- `KB/service/flow/impl/KbFlowTemplateServiceImpl.java:31-77,97-243`
- `KB/service/bot/impl/KbBotServiceImpl.java:64-76`
- `KB/service/runtime/impl/KbRuntimeServiceImpl.java:59-133`
- `KB/controller/admin/runtime/KbRuntimeController.java:36-59`
- `KB/controller/admin/flow/KbFlowController.java:51-116`
- `KB/controller/admin/flow/vo/KbFlowVO.java:11-34`
- `KB/api/BotApiServiceImpl.java:33-78`
- `qknow-module-ai/.../api/modelMarket/AiModelApiServiceImpl.java:52-62`；`.../service/modelMarket/impl/AiModelServiceImpl.java:149-185`（模型字典分组结构）

### A.7 前端（只读参考）
- `frontend/src/api/kb/bot/flow.js:1-123`（SSE 端点、ProcessFlow、resumeFlow）
- `FE/components/WorkflowDebugRunPanel.vue:420,480-600,640-710`（本地合成事件/伪造 HITL 工单/runtimeId 回退/resume 调用）
- `FE/index.vue:517-541`（condition cases 结构）
- `FE/composables/useWorkflowMeta.js:130-156`（模型字典扁平映射 + DeepSeek 兜底）
- `FE/components/nodeConfig/LlmNodeConfigForm.vue:16-19,318-333,380-386`
- `FE/nodes/LoopNode.vue`；`FE/components/hitl/HitlApprovalModal.vue:210-295`（本地凭单）
- `FE/components/WorkflowDebugRunPanel.vue:500-512`（`evt_start` 假事件、latency 0）

### A.8 自愈/Saga/事务/认知
- `HC/flow/saga/SagaCompensationEngine.java:19-213`；`CompensableNode.java:10-26`
- `HC/transaction/engine/SagaDistributedTransactionHealer.java:27-134`
- `HC/transaction/engine/WorkflowTransactionControlBus.java:19-108`
- `HC/transaction/engine/ContractEvolutionGovernor.java`；`WorkflowElasticScaler.java`（零生产引用）
- `HC/tool/mcp/sagas/McpSagasCompensationGovernor.java:20-79`
- `HC/cognitive/selfhealing/engine/CognitiveSelfHealingStateMachine.java:18-127`
- `HC/cognitive/selfhealing/engine/CognitiveKernelControlBus.java`；`HierarchicalMemoryCompressor.java`；`UnifiedGraphCognitiveAligner.java`
- `HC/agent/workflow/healing/WorkflowFiniteStateCheckpointManager.java:81-229`（租约/快照/resume，内存态、租约过期未校验、零生产引用）
- `HC/agent/workflow/healing/MultiAgentWaitForGraphDetector.java:24-255`（Tarjan 死锁检测，零生产引用）
- `HC/agent/dag/TopologicalPhasedDispatcher.java:20-107`；`HierarchicalTaskPlanner.java:21-90`（被 SupervisorAgent 使用，但 SupervisorAgent 只被测试实例化）
- `HC/agent/guard/ReActStrictWindowCycleGuard.java:114-196`；`ReActCycleGuard.java:10-18`；`SwarmLoopGuard.java:30-107`
- `HC/agent/AgentOrchestrator.java:428-434,1193-1230`（聊天 ReAct guard 的真实使用）
- `HC/workflow/timetravel/WorkflowTimeTravelStateHub.java:25-228`
- `HC/flow/debug/impl/DefaultWorkflowTimeTravelDebugger.java:21-60`

### A.9 测试
- `TS/flow/ReactiveWorkflowContractTest.java:35-64,500`（测试替身 checkpoint）
- `TS/integration/DagE2ETest.java:38-53,385-393`（mock 化 E2E）
- `TS/hermes/benchmark/Phase134FrontendDagHitlIntegrationContractTest.java:33-120`（纯 Java 模拟前端 + `Math.random`）
- `TS/hermes/flow/checkpoint/Phase126McpLeaseLivenessContractTest.java:28,67,106,132,166,192,278`（均 `new DagCheckpointManager()` 走内存降级）与 `:196`（MCP saga 测试）
- `TS/hermes/agent/Phase125ReActStrictWindowContractTest.java:26,48-85`（W+1 幽灵计数契约）
- `TS/hermes/flow/hitl/Phase154MasterContractTest.java`、`Phase155`、`Phase156`、`Phase124/132`（flow/hitl）与 `Phase138/148`（benchmark）（自证型 Phase 契约测试）
- `KBT/KbFlowResumeCheckpointTest.java:48-65,89-207`（内存降级 + mock NodeFactory 的 resume 测试）
- `KBT/KbFlowServiceDagTest.java:75-214`（判环/菱形并行/剪枝，无 join/无失败恢复）
- `KBT/EndToEndRagWorkflowIntegrationTest.java`（RAG 端到端，非 DAG 装配测试）
- `KBT/KbFlowServiceToolNodeTest.java`、`TS/module/kb/service/flow/ToolNodeBOTest.java`（工具节点单元测试）

### A.10 统计口径
- 被审代码行数：`flow` 120 文件/14,251 行、`workflow` 3/996、`transaction` 11/669、`cognitive/selfhealing` 8/502、`agent/workflow` 3/644、`agent/dag` 4/322，合计 149 文件/17,384 行；控制面 flow 相关（service/flow+controller/admin/flow+dal/dataobject/flow+convert/flow）3,884 行。
- 引用统计：非测试代码零外部引用 53 文件；仅 1 个生产引用 39 文件；从 Hermes 入口 BFS 可达 27/149（18%）；控制面对 Hermes 的 flow 相关 import 仅 4 个类。
- 测试：`backend/tests` 中 flow/workflow/hitl/checkpoint 相关 40 文件/13,823 行；该模块 `@SpringBootTest` 数量 = 0；`qknow-module-kb-biz` 流程测试 4 文件/751 行。

---

## 附录 B：本报告未采用的表述与理由（防止误读）

1. 未将 `flow/**` 注释中的 "1000Hz Disruptor / 50ns / 100% 零丢失 / 死锁概率 0.0% / 重复执行数 0" 当作事实：这些类无生产调用、无基准测试产物，且实现与描述不符（如 `WorkflowTransactionControlBus.publishFrame` 是普通数组覆盖写，`:48-56`）。
2. 未将 `PhaseNNN` 契约测试的"通过"当作系统能力证据：它们不启动 Spring 上下文、大量在测试未接线的类（§2.7、§0.4）。
3. 未将 `HC/flow/dag/DagExecutor` 与 `KB/service/flow/dag/DagExecutor` 视为同一实现：两者是复制关系，且控制面走的是 `KbFlowServiceImpl` 内联调度器（`KB/service/flow/dag/DagExecutor` 本身也是零生产引用）。
4. 未声称"前端 HITL 完全不可用"以外的 UI 细节：前端契约只读判断，LLM 模型选择等 UI 行为属推断，需浏览器验证（§6.1）。
