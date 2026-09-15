# Phase 55 实施方案：分层任务网络 (HTN) 递归规划、因果元反思自愈与分布式事务一致性仲裁

> **实施编号**：`PHASE-55-PLAN`  
> **学术依据**：`docs/plans/phase_55_academic_report.md`  
> **工业对标**：`docs/plans/phase_55_industrial_report.md`  
> **基线环境**：生成侧唯一 DeepSeek API（V3 / R1），向量侧唯一阿里千问 1536 维超球面，编译与执行唯一 Java 21 隔离环境。

---

## 一、唯一待验证科学假设 (Single Falsifiable Hypothesis)

> **唯一可证伪假设 `H-PHASE55-001`**：  
> 在多智能体长程异构业务流水线与复杂任务协同中，通过引入**基于良基序与 Kahn 拓扑排序的 HTN 递归任务分解器 (`HtnTaskDecomposer`)**、**基于 Pearl 结构因果诊断与 Reflexion 的因果元反思自愈引擎 (`CausalMetaReasoningEngine`)** 以及**基于 LIFO 逆向幂等补偿的分布式 SAGA 事务仲裁器 (`DistributedTransactionArbiter`)**：
> 1. 能够在最大展开深度 $D_{\max} \le 5$ 约束下完成多层复合任务到原子动作序列的合法展开，拓扑依赖图循环依赖检出率 100%，彻底消除自旋死锁与堆栈溢出（定理 1.1 无环有界展开定理）；
> 2. 针对原子动作可恢复执行异常，因果元反思引擎在 3 轮迭代内达成 $\ge 95\%$ 的自动化自愈成功率（定理 1.2 指数自愈收敛定理）；
> 3. 当遇到不可恢复故障时，SAGA 事务仲裁器严格按照依赖图逆序（LIFO）100% 触发已执行动作的幂等补偿回滚，脏状态残留率严格为零（定理 1.3 强最终一致性定理）；
> 4. 全链路端到端任务分解、拓扑排序、元反思诊断与事务协调代数开销严格满足 $\le 10\text{ms}$。

---

## 二、架构拓扑与核心组件设计

在 `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/htn/` 目录下落地五大组件：

```
[用户复杂长程业务目标 CompositeTask]
                 │
                 ▼
┌──────────────────────────────────────────────┐
│             HtnTaskDecomposer                │
│  (HTN 递归任务分解器; 良基序势能约束;        │
│   Kahn 拓扑排序检测环路; 深度硬截断 D_max=5;   │
│   将复合任务分解为原子动作序列 PrimitiveTasks)│
└──────────────────────┬───────────────────────┘
                       │ (拓扑有序的原子动作计划)
                       ▼
┌──────────────────────────────────────────────┐
│       DistributedTransactionArbiter          │
│  (分布式 SAGA 事务仲裁器; 正向执行与逆向补偿;│
│   维护 TransactionState 状态机; 幂等 LIFO 回滚)│
└──────────────────────┬───────────────────────┘
                       │ (多智能体协同分步执行)
                       ▼
┌──────────────────────────────────────────────┐
│        CausalMetaReasoningEngine             │
│  (Pearl 结构因果诊断与 Reflexion 自愈引擎;   │
│   构建失败因果图; 动态生成元补丁 Meta-Patch; │
│   上限 3 轮指数收敛自愈)                     │
└──────────────────────┬───────────────────────┘
                       │ (成功提交或完整回滚)
                       ▼
┌──────────────────────────────────────────────┐
│     MultiAgentHierarchicalCoordinator        │
│  (全链路总控协调中枢; 端到端耗时 <= 10ms)     │
└──────────────────────┬───────────────────────┘
                       │ (审计存证)
                       ▼
┌──────────────────────────────────────────────┐
│        HierarchicalExecutionReceipt          │
│  (不可变存证凭单 Record; SHA-256 密码学存证) │
└──────────────────────────────────────────────┘
```

### 2.1 组件清单
1. **`HierarchicalExecutionReceipt.java`**：不可变 Java 21 Record，记录 executionId, taskId, decomposedTaskTreeHash, executedActions, sagaStatus (COMMITTED / COMPENSATED), selfHealingRounds, executionDurationMs, tamperProofHash。
2. **`HtnTaskDecomposer.java`**：分层任务递归分解器，维护任务树节点模型，内置 Kahn 算法进行循环依赖排查，强制 $D_{\max} \le 5$ 势能截断。
3. **`CausalMetaReasoningEngine.java`**：因果元反思自愈引擎，根据执行异常堆栈与上下文特征构建因果关系图，区分瞬态/环境缺陷，生成元补丁并驱动 3 轮有限视界修复。
4. **`DistributedTransactionArbiter.java`**：分布式 SAGA 事务一致性仲裁器，管理正向执行栈与补偿栈，故障时触发 LIFO 逆向幂等补偿，确保强最终一致性。
5. **`MultiAgentHierarchicalCoordinator.java`**：端到端统筹调度中枢，闭环调度 HTN 分解 -> 拓扑执行 -> 因果元自愈 -> SAGA 事务一致性仲裁 -> 密码学存证签发。

---

## 三、测试与验证契约设计 (`Phase55HtnExecutionContractTest.java`)

在 `backend/tests/src/test/java/tech/qiantong/qknow/ai/htn/` 构建 8 项严苛契约测试：

1. **`test1_HtnTaskDecomposerRecursiveDecompositionAndDepthBound()`**：验证定理 1.1，多层嵌套复合任务正确递归分解为原子动作序列，深度严格 $\le 5$ 且无死锁；
2. **`test2_HtnTaskDecomposerCyclicDependencyDetection()`**：验证定理 1.1，输入包含循环前后置依赖的恶意分解任务，Kahn 拓扑排序 100% 捕获并抛出依赖环异常，阻断死锁；
3. **`test3_CausalMetaReasoningEngineSelfHealingConvergence()`**：验证定理 1.2，模拟原子动作可恢复参数错误，因果元反思在 2 轮内生成补丁并自愈恢复，状态转为 SUCCESS；
4. **`test4_CausalMetaReasoningEngineNonRecoverableFastFail()`**：验证不可恢复错误（如越权或资源物理不存在）直接判定为不可自愈，快速失败并触发后续回滚，拒绝盲目暴力重试；
5. **`test5_DistributedTransactionArbiterSagaRollbackConsistency()`**：验证定理 1.3，模拟多步流水线在第 3 步遭遇致命错误，SAGA 仲裁器 100% 触发已执行前置动作的逆向 LIFO 补偿，事务状态转为 COMPENSATED，脏数据残留为 0；
6. **`test6_HierarchicalExecutionReceiptImmutabilityAndSha256()`**：验证不可变存证 Record 的集合保护与 SHA-256 密码学自验；
7. **`test7_MultiAgentHierarchicalCoordinatorEndToEndLifecycle()`**：验证协调中枢全链路调度（分解 -> 拓扑执行 -> 自愈 -> 事务提交 -> 签发凭证），耗时严格 $\le 10	ext{ms}$；
8. **`test8_HighConcurrencyHtnExecutionThroughput()`**：验证 8 线程高并发下 HTN 分解与 SAGA 事务无死锁、线程安全与高吞吐。

---

## 四、全库防退化回归与前端构建目标

- **单测规模基线**：当前基线为 1074 项单测全绿；
- **交付目标规模**：全库单测突破 **1082 项 100% 全绿**（0 失败 0 错误）；
- **前端生产构建**：`npm run build:prod` 保持 0 错误 0 警告通过。
