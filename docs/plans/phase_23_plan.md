# Phase 23 决策完备实施方案：反应式工作流编排引擎、非阻塞人机协同审批 (HITL) 与 SAGA 事务状态补偿自愈体系

> **拟归档路径**：`docs/plans/phase_23_plan.md`  
> **依据规范**：`AGENTS.md` Research-to-Implementation Gate 规范  
> **前置依赖**：Phase 01 ~ Phase 22 全量圆满交付（已具备 Agentic 记忆图谱与蜂群协同调度）  
> **理论依据**：`docs/plans/phase_23_academic_report.md`（WF-Nets Soundness、剪枝标记代数 $\bot$、Delimited Continuation 状态等价性、SAGA 逆拓扑弱等价收敛定理）  
> **工业对标**：`docs/plans/phase_23_industrial_report.md`（Temporal, Camunda 8 Zeebe, LangGraph, Dify, Sagas 1987）  
> **唯一模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 / `deepseek-reasoner` 即 R1），唯一向量模型为 **阿里千问 (Qwen) Embedding (1536维)**，绝无本地大模型，彻底弃用 OpenAI/GPT API。  
> **环境隔离铁律**：统一且唯一使用 **Java 21** 编译与运行，绝对路径固定为 `/Users/achilles/.sdkman/candidates/java/21.0.5-tem`，Maven 局部传参 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`，禁止污染 Mac 全局 Java 17。

---

## 一、唯一待验证算法与工程假设 (Falsifiable Core Hypothesis)

> **核心假设 (H-Phase23)**：  
> 针对当前系统工作流模块中“`ApprovalNodeExecutor` 采用内存 Future 同步物理阻塞线程池 24 小时导致 8 个待办即全服死锁雪崩”、“静态入度分层无视条件分支导致未选中路径强制执行与汇聚网关永久死锁”、“完全缺失长事务 SAGA 补偿回滚导致失败时外部脏数据泄露”三大生产致命缺陷：  
> 1. 构建**非阻塞断点挂起与事件驱动恢复状态机**（`ApprovalNodeExecutor` 返回 `SUSPENDED`，快照写入 PostgreSQL `dag_checkpoints` 并注册 Redis 待办索引，立即归还物理线程；审批 API 基于分布式锁 + CAS 乐观锁安全反序列化恢复执行）；  
> 2. 构建**死锁安全的分支条件动态递归剪枝引擎**（未命中分支递归标记 `SKIPPED`，`AggregatorNodeBO` 自适应过滤无效输入，消除同步等待死锁）；  
> 3. 构建**基于转置图拓扑排序的 SAGA 事务补偿引擎**（定义 `CompensableNode` 契约，在流程失败或审批驳回时严格按逆拓扑序 LIFO 回滚已执行节点的外部副作用）；  
> 4. 构建**类型安全上下文访问器**（`TypeSafeContextAccessor`，杜绝快照反序列化类型擦除导致的 `ClassCastException`）。  
> 
> **能够证明**：  
> - **零物理线程阻塞**：在 100 个并发工作流挂起审批场景下，工作流线程池物理活跃线程占用保持为 0（降幅 $100\%$）；进程重启后审批唤醒恢复成功率达到 $100\%$；  
> - **条件分支零死锁**：条件分支未选中路径跳过率达到 $100\%$，汇聚网关自适应识别有效分支并正常执行，死锁率由基线 $\ge 60\%$ 降为严格的 $0.00\%$；  
> - **事务逆拓扑自愈**：在人工审批拒绝或节点执行异常时，已成功节点逆拓扑补偿触发率与语义对冲成功率达到 $100\%$；  
> - **并发防重防抖**：多端高并发审批请求下，基于 Redis 互斥锁与 DB 行级 CAS 乐观锁防重拦截率达 $100\%$，零重复恢复。

---

## 二、架构拓扑与交互数据流

```text
[用户/前端] 
     │ (提交工作流)
     ▼
[FlowExecutor] ──> [DagExecutor]
                        │ (按组推进)
                        ├──> [ConditionNodeBO] ──(未命中分支)──> [Dynamic Pruner: 递归打 SKIPPED 标签]
                        ├──> [Compensable Tasks] ──(成功执行)──> [入栈已完成执行链 A_done]
                        ├──> [ApprovalNodeExecutor] 
                                    │ (返回 SUSPENDED)
                                    ▼
                        [DagCheckpointManager] ──(保存上下文 Γ & 待办)──> [PostgreSQL / Redis]
                                    │
                         (立即释放物理工作线程, ActiveThreads = 0)
                                    │
                                    ... (数小时/数天后, 审批人决策) ...
                                    │
[ApprovalController] <──(POST /approve /reject)── [审批人]
     │
     ├──(分布式锁 Redis SETNX + DB CAS 校验 version)
     │
     ├── [若决策为 APPROVED] ──> [加载快照 Γ + 注入参数] ──> [重新提交 DagExecutor 推进后续波前]
     │                                                               │
     │                                                               ▼
     │                                                    [AggregatorNodeBO (过滤 SKIPPED)]
     │
     └── [若决策为 REJECTED] ──> [SagaCompensationEngine]
                                          │ (构建转置图 G^R)
                                          ▼
                               [逆拓扑序 LIFO 触发各节点的 compensate()]
                                          │
                               [更新状态为 REJECTED / COMPENSATED, 记录审计日志]
```

---

## 三、实施范围与最小修改文件集合

严格遵循最小修改原则，仅触碰与本阶段核心假设直接相关的组件：

### 3.1 核心枚举与工具类
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/enums/RuntimeStatusEnums.java`（增加 `SKIPPED`, `COMPENSATING`, `COMPENSATED`, `REJECTED` 状态枚举）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/util/TypeSafeContextAccessor.java` [NEW]（解决反序列化类型擦除工具类）

### 3.2 调度器与断点持久化
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/dag/DagCheckpointManager.java`（扩展全量上下文变量持久化、CAS 乐观锁版本更新与 Redis 待办索引支持）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/dag/DagExecutor.java`（深度重构：动态递归剪枝、非阻塞挂起退出、逆拓扑 SAGA 补偿触发）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/dag/DagUtils.java`（增强前驱边可达性与有效性判断）

### 3.3 节点执行器与汇聚网关
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/node/ApprovalNodeExecutor.java`（彻底移除阻塞 Future，重构为轻量级元数据非阻塞挂起）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/bo/AggregatorNodeBO.java`（改造汇聚策略：自适应跳过已被标记为 `SKIPPED` 的无效前置输入）

### 3.4 SAGA 事务状态补偿引擎 [NEW]
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/saga/CompensableNode.java` [NEW]（补偿节点标准接口定义）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/saga/SagaCompensationEngine.java` [NEW]（转置图逆拓扑调度、重试退避与补偿审计记录）

### 3.5 审批控制层与外部交互
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/controller/ApprovalController.java`（重构接入分布式锁防重、数据库 CAS 状态流转与异步唤醒调度）

### 3.6 数据库迁移脚本与自动化测试
- `deploy/sql/postgresql/migrations/V016__dag_checkpoint_saga_and_suspension.sql` [NEW]（扩展 `dag_checkpoints` 表支持 `version`, `variables_json`, `status`, `compensation_log`）
- `backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/ReactiveWorkflowContractTest.java` [NEW]（专属契约测试用例，覆盖非阻塞挂起、动态剪枝、SAGA 逆拓扑补偿、并发防重等核心断言）

---

## 四、测试驱动开发 (TDD) 契约规划

在编写任何业务实现代码前，首先在 `tech.qiantong.qknow.hermes.flow.ReactiveWorkflowContractTest.java` 中构建 10 大核心契约用例：

1. **`testApprovalNode_NonBlockingSuspension_ReleasesThread`**：执行含审批节点工作流，验证毫秒级返回 `SUSPENDED`，物理工作线程不阻塞且立即被释放；
2. **`testApprovalCheckpoint_SaveAndRestoreContextVariables`**：验证挂起快照中完整保留数字、字符串、Map 等环境变量，恢复时通过 `TypeSafeContextAccessor` 零类型强转异常；
3. **`testApprovalApprove_ResumesWorkflowSuccessfully`**：验证调用审批通过并注入变量后，工作流从断点位置准确继续执行直至后续节点正常完成；
4. **`testApprovalApprove_ConcurrentRaceCondition_ProtectedByDistributedLock`**：20 个并发线程同时发起审批唤醒，断言仅有 1 个请求获得锁并执行成功，其余 19 个被幂等拦截；
5. **`testConditionNode_PrunesUnselectedBranchRecursively`**：条件节点选择 Branch A，验证 Branch B 以及挂载在 Branch B 下的所有孙子节点 100% 被标记为 `SKIPPED`；
6. **`testAggregatorNode_AdaptivePrunedInputs_ExecutesWithoutDeadlock`**：验证汇聚网关自适应过滤被 `SKIPPED` 的前驱分支，仅聚合有效分支输入，彻底消除死锁；
7. **`testSagaCompensation_TriggeredOnApprovalRejection`**：当审批节点被硬性驳回（`REJECTED`）时，验证上游已完成的 `CompensableNode` 严格以逆拓扑序执行 `compensate`；
8. **`testSagaCompensation_TriggeredOnDownstreamExecutionError`**：当下游节点抛出不可恢复异常时，验证全链路自动触发 SAGA 逆序回滚，并保存补偿审计日志；
9. **`testSagaCompensation_NonCompensableNodesSkippedGracefully`**：纯 LLM 或只读节点不实现 `CompensableNode`，补偿引擎优雅跳过且不报错；
10. **`testEndToEnd_ComplexWorkflowWithConditionApprovalAndCompensation`**：端到端复合长链路测试，验证条件分支分流、非阻塞挂起、人机介入以及异常补偿自愈无缝协同。

---

## 五、实施纪律与回滚防线

1. **零外部重型依赖引入**：严禁引入 Temporal / Camunda 外部中间件，所有逻辑基于原生 Java 21 + 现有 PostgreSQL/Redis 闭环；
2. **回归防退化红线**：修改完成后，除本阶段 10 个专属契约单测全绿外，现有 `DagE2ETest`、`FlowExecutorTest` 及 Hermes 模块既有 57+ 项单元测试必须 100% 绿灯通过；
3. **全量防退化回归**：运行全系统 760+ 项全量单测与前端 Vite 生产构建，确保零编译破坏与零行为退化。
