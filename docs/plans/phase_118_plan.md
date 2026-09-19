# Phase 118 实施详案与工程契约文件

## 课题：支柱二：生产级企业 MCP 工具生态 —— 动态流水线与 Sagas 分布式事务补偿中枢 (Enterprise MCP Dynamic Pipeline & Sagas Distributed Compensation Engine)

> **归档路径**：`docs/plans/phase_118_plan.md`  
> **基线环境约束**：
> - 唯一生成模型：DeepSeek API（主干模型，参数化思考模式 `thinking: {"type": "enabled"}`, `reasoning_effort: "high"`, 绝无 r1）
> - 唯一向量模型：阿里千问 (Qwen) Embedding (1536 维超球面空间，$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$)
> - 隔离环境：Java 21 (`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)
> - 业务边界：100% 聚焦企业级知识库与智能体编排，严禁力学发散

---

### 一、唯一待验证假设 (Hypothesis H-PHASE118-001)

> 在保持 Java 21 隔离环境、DeepSeek API 参数化思考模式、阿里千问 1536 维超球面向量基线不变的前提下：
> 1. 构建**动态流水线 DAG 拓扑分发引擎 (`McpDynamicPipelineDispatcher`)**，基于 Kahn 算法进行依赖解析与关键路径并发调度，包含 $N$ 个 MCP 节点与 $M$ 条依赖边的动态流水线调度时间复杂度严格有界于 $\mathcal{O}(N + M)$，单步拓扑分发纯内存计算耗时 **$\le 5\text{ms}$**；
> 2. 构建**Sagas 逆序补偿事务中枢 (`McpSagaTransactionManager`)**，在长链路多 MCP 工具调用发生部分失败时，按因果逆序 LIFO 精确触发已注册的补偿动作，使系统状态最终一致性收敛概率为 **1.0**，逆序补偿步数严格有界于已执行步数 $K \le N$；
> 3. 构建**防悬挂与幂等租约注册表 (`McpCompensatingActionRegistry`)**，引入全局唯一 `LeaseToken` 与防悬挂墓碑标记（Tombstone），彻底根除网络乱序时“补偿动作先于正向动作到达”导致的数据覆盖与资产悬挂，幂等拦截率达 **100%**；
> 4. 构建**纯 Java 21 Record 格式的事务存证凭单 (`McpSagaReceipt`)**，内嵌 SHA-256 密码学自签名与运行时自验真，单次凭单生成与验真耗时 **$\le 50\mu\text{s}$**，实现 100% 防篡改可追溯。

---

### 二、拟实施文件清单与代码架构

#### 模块：`backend/qknow-hermes/qknow-hermes-core`

1. **[NEW] `tech.qiantong.qknow.hermes.tool.mcp.sagas.dto.McpSagaReceipt.java`**
   - 纯 Java 21 Record 不可变存证凭单；
   - 封装 `receiptId`, `transactionId`, `status`, `totalSteps`, `executedSteps`, `compensatedSteps`, `stepRecords`, `executionTimeMs`, `timestamp`, `signature`；
   - 内置 `McpStepRecord` Record 描述单步状态（`stepId`, `toolName`, `actionType`, `success`, `leaseToken`, `costMs`）；
   - 提供内置 SHA-256 自签名与 `verifySignature()` 运行时验真。

2. **[NEW] `tech.qiantong.qknow.hermes.tool.mcp.sagas.McpCompensatingActionRegistry.java`**
   - 补偿动作注册、LeaseToken 幂等生命周期管理与防悬挂墓碑拦截器；
   - 支持向正向工具绑定补偿动作函数 `BiFunction<String, Map<String, Object>, Boolean>`；
   - 维护 `tombstones` 集合，若补偿先于正向到达，记录墓碑标记，后续正向操作到达时自动拦截；
   - 纯 Java 21 堆内轻量级 ConcurrentHashMap 结构，零外部集群依赖。

3. **[NEW] `tech.qiantong.qknow.hermes.tool.mcp.sagas.McpDynamicPipelineDispatcher.java`**
   - 基于 Kahn 算法的 DAG 依赖拓扑分发与关键路径并发调度器；
   - 支持动态注册工具节点（`PipelineNode`）与前置依赖集合；
   - 自动检测并消除环路（若存在环路抛出明确异常）；
   - 使用 Java 21 虚拟线程池并发调度就绪节点，分层推进，纯内存拓扑解析 $\le 5\text{ms}$。

4. **[NEW] `tech.qiantong.qknow.hermes.tool.mcp.sagas.McpSagaTransactionManager.java`**
   - Sagas 事务管理器，统一协调动态流水线正向执行与异常逆序补偿；
   - 正向推进中将执行成功的步骤入栈（LIFO）；
   - 遇断路器触发或执行失败时，自动开启回滚模式，逆序出栈并执行对应补偿操作；
   - 生成最终 `McpSagaReceipt` 凭单并自签名。

#### 模块：`backend/tests`

5. **[NEW] `tech.qiantong.qknow.hermes.tool.mcp.Phase118McpSagaPipelineContractTest.java`**
   - 综合契约测试，精确覆盖 6 大契约：
     1. 契约 1：定理 1.1 Sagas 逆序补偿最终一致性与有界步数收敛验证（部分失败场景逆序回滚）；
     2. 契约 2：定理 1.2 Kahn DAG 拓扑分发 $\mathcal{O}(N+M)$ 复杂度与调度耗时 $\le 5\text{ms}$；
     3. 契约 3：防悬挂墓碑拦截与 LeaseToken 幂等性测试（先补偿后正向拦截）；
     4. 契约 4：Java 21 虚拟线程高并发多流水线并行隔离与零线程饥饿；
     5. 契约 5：纯 Java 21 Record 事务凭单 SHA-256 自签名与单比特篡改拦截；
     6. 契约 6：端到端跨系统业务场景（如 ERP 配额锁定 -> 支付扣款超时 -> 逆序释放配额）全流程验证。

---

### 三、验证命令与测试计数

在严格隔离的 Java 21 环境下执行：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests -Dtest="Phase118McpSagaPipelineContractTest" -Dsurefire.failIfNoSpecifiedTests=false
```
预期测试计数：**6 项契约测试 100% 绿灯通过**。

全量回归验证：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests -Dtest="Phase118McpSagaPipelineContractTest,Phase117NashDebateConsensusContractTest,CrossBorderProcurementEndToEndTest,Phase116GraphRagScaffoldContractTest" -Dsurefire.failIfNoSpecifiedTests=false
```
预期测试计数：**23 项跨模块测试 100% 绿灯全量通过**。
