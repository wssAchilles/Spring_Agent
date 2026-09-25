# Phase 138 实施方案与契约设计：多智能体认知协同工作流状态机自愈、断点恢复与分布式死锁检测中枢

## 一、方案核心目标与唯一算法假设

### 1.1 唯一待验证假设 (Hypothesis H-138)
在多智能体异步协同工作流与 DAG 编排场景下，通过构建“基于动态有向等待图 (Wait-For Graph) 的微秒级死锁环路检测与强连通分量熔断 + 基于快照状态树与防护令牌 (Fencing Token) 的无损断点恢复 + 纯 Java 21 Record 格式不可变自愈存证凭单 (`WorkflowSelfHealingReceipt`)”，能够实现：
1. 包含 5~20 个智能体复杂交织等待的拓扑死锁在 $\le 5.0\text{ms}$ 内完成环路闭环检出，并根据事务开销与拓扑层级自适应裁决牺牲者（Victim Preemption），将死锁自愈成功率提升至 100.0%；
2. 长流程任务在任意状态机跃迁节点崩溃后，基于快照存证与租约心跳（Lease TTL $\le 3000\text{ms}$）在 $\le 50\text{ms}$ 内完成确定性接管与断点续跑，重复执行开销降为 0.0%，状态机脑裂双写拦截率 100.0%；
3. 状态回溯与自愈决策全过程签发不可变密码学存证凭单，支持 SHA-256 常量时间验真率 100.0%。

---

## 二、架构设计与核心组件规划

### 2.1 后端核心组件设计 (`qknow-hermes-core`)

#### 1. `MultiAgentWaitForGraphDetector.java`
- **定位**：多智能体动态因果等待图 (WFG) 拓扑维护与 Tarjan 强连通分量微秒级死锁检出器；
- **算法机制**：
  * **动态等待图建模**：维护有向图 $\mathcal{G} = (\mathcal{V}_{agent}, \mathcal{E}_{wait})$，边权重表示事务回滚代价；
  * **Tarjan 强连通分量 (SCC) 算法**：深度优先遍历在 $O(|V| + |E|)$ 内计算 `dfn` 与 `lowlink`，精准识别基数 $\ge 2$ 的死锁强连通环路；
  * **外科手术式最小代价破环 (Surgical Preemption)**：遍历环路中各条依赖边，选定回滚代价最小的边断开并注入预置降级结果，使拓扑解构为有向无环图（DAG），其余已完成步骤 100% 保持正常。

#### 2. `WorkflowFiniteStateCheckpointManager.java`
- **定位**：工作流有限状态机不可变快照事件树、单调防护令牌 (Fencing Token) 与断点恢复引擎；
- **核心机制**：
  * **状态机快照树 (Snapshot Tree)**：每次状态机成功跃迁原子创建包含步骤序号、执行上下文变量、已生成 Token 和时间戳的不可变记录；
  * **单调自增防护令牌 (Fencing Token)**：每次协调者主备倒换严格递增令牌号；所有写操作必须携带 $\Phi \ge \Phi_{current}$，陈旧假死节点的写操作 100% 被原子拦截；
  * **断点无损续跑 (Breakpoint Resumption)**：接管者从最近快照 $s_k$ 顺滑续跑，前序步骤 $1 \sim k$ 重复执行率为 0；
  * **租约超时看门狗 (Lease TTL Guard)**：租约超时判定门限严格锁定于 $\le 3000\text{ms}$，超时后自动触发主备切换与断点自愈。

#### 3. `WorkflowSelfHealingReceipt.java`
- **定位**：纯 Java 21 Record 格式不可变密码学工作流自愈审计存证凭单；
- **核心字段**：
  * `receiptId` (String): 全局唯一自愈凭单编号 (`RCP-SELF-HEAL-...`)；
  * `workflowId` (String): 工作流实例编号；
  * `actionType` (String): 自愈类型 (`DEADLOCK_BROKEN`, `BREAKPOINT_RESUMED`, `SPLIT_BRAIN_FENCED`)；
  * `fencingToken` (long): 当前有效的单调防护令牌版本号；
  * `deadlockCycleNodes` (List<String>): 死锁环路中涉及的智能体节点标识；
  * `resumedStepIndex` (int): 断点恢复起始步骤序号；
  * `latencyMs` (double): 自愈或断点恢复端到端耗时；
  * `timestamp` (long): 毫秒时间戳；
  * `sha256Signature` (String): 全字段规范化 SHA-256 防篡改签名；
- **特性**：提供常量时间自验真方法 `verifySignature()`。

---

### 2.2 前端可视化扩展 (`frontend`)

#### `WorkflowStateRecoveryWidget.vue`
- **路径**：`frontend/src/views/kb/bot/build/components/flow/WorkflowStateRecoveryWidget.vue`；
- **视觉风格与规范**：
  * 严格遵循 Rule 2 UI/UX Pro Max 规范与单色现代暗黑钛金毛玻璃设计 Token；
  * 材质底色 `#0a0a0c`、毛玻璃 `backdrop-filter: blur(20px)`、发丝边框 `rgba(255,255,255,0.08)`；
- **核心交互特性**：
  1. **等待图拓扑与死锁环路红色脉冲高亮**：直观展示智能体间的等待有向边，在检出环路时呈现高亮红色呼吸闪烁；
  2. **状态快照时间轴回溯 (Snapshot Timeline)**：可视化展示工作流各步骤执行快照，支持点击回溯查看各阶段上下文；
  3. **断点接管流水线动效**：呈现主节点假死与备用节点持自增 Fencing Token 接管的流式动画；
  4. **流畅度达标**：渲染响应时延 $\le 16.6\text{ms}$，满足 60 FPS 锁步标准。

---

## 三、8 项严苛契约测试定义 (Contract Tests)

| 测试编号 | 契约方法名 | 核心验证指标与断言标准 |
| :--- | :--- | :--- |
| **TC-138-1** | `testWaitForGraph_cycleDeadlockDetection()` | 构造 A $\to$ B $\to$ C $\to$ A 循环死锁等待，Tarjan 算法在 $\le 5.0\text{ms}$ 内精准检出有向环并识别环上全量节点 |
| **TC-138-2** | `testDeadlockPreemption_minimumRollbackCost()` | 拓扑破环自适应选择最小回滚代价依赖边剪断，死锁自愈成功率 100%，非环节点不受干扰 |
| **TC-138-3** | `testFencingToken_splitBrainRejection()` | 单调递增防护令牌排他性：持有过期旧令牌的假死智能体写请求 100% 被原子拦截，拦截率 100.0% |
| **TC-138-4** | `testWorkflowCheckpoint_stateSnapshotTree()` | 状态机跃迁时原子创建不可变快照，包含中间 Token、变量与元数据，单次快照耗时 $\le 1.0\text{ms}$ |
| **TC-138-5** | `testBreakpointResumption_zeroRedundantExecution()` | 模拟协调节点在第 4 步崩溃后由备用节点无损接管，前序步骤重复执行数为 0，断点续跑总延迟 $\le 50\text{ms}$ |
| **TC-138-6** | `testLeaseTimeout_watchdogActiveHealing()` | 依据 Lemma 3.1，当租约超过 TTL (3000ms) 时，看门狗自动触发主备倒换与断点恢复，自愈时效有界 |
| **TC-138-7** | `testWorkflowSelfHealingReceipt_immutableVerification()` | 纯 Java 21 Record 凭单签名自验真：验证全字段不可变性与 SHA-256 哈希常量时间自验真率 100.0% |
| **TC-138-8** | `testEndToEndWorkflowSelfHealing_fullPipelineIntegration()` | 端到端闭环：复杂网状协同 $\to$ 注入死锁 $\to$ 环路检出与自愈 $\to$ 模拟主节点假死 $\to$ Fencing 接管断点续跑 $\to$ 凭单签发 |

---

## 四、最小实现文件集合与禁止修改边界

### 4.1 最小修改文件集合
1. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/workflow/healing/MultiAgentWaitForGraphDetector.java` (新建)
2. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/workflow/healing/WorkflowFiniteStateCheckpointManager.java` (新建)
3. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/workflow/healing/WorkflowSelfHealingReceipt.java` (新建)
4. `frontend/src/views/kb/bot/build/components/flow/WorkflowStateRecoveryWidget.vue` (新建)
5. `backend/tests/src/test/java/tech/qiantong/qknow/hermes/benchmark/Phase138WorkflowSelfHealingContractTest.java` (新建)

### 4.2 严格禁止修改的边界
- 严禁修改已归档封存的力学物理沙箱目录：`tech.qiantong.qknow.ai.embodied.*`；
- 严禁修改全局系统默认 JDK 17，所有编译与测试必须严格使用 Java 21 隔离环境变量；
- 严禁引入任何外部重型工作流/分布式锁中间件依赖（如 Temporal SDK, ZooKeeper Driver 等）；
- 严禁在测试中修改断言期望值以掩盖失败；
- 严禁使用 OpenAI API 或本地小模型，唯一生成模型为 DeepSeek API。

---

## 五、完整复现与全量回归验证命令

```bash
# 1. 编译安装 qknow-hermes-core 模块
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn clean install -pl qknow-hermes/qknow-hermes-core -DskipTests

# 2. 运行 Phase 138 专项严苛契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase138WorkflowSelfHealingContractTest

# 3. 运行 Phase 125 ~ Phase 138 跨阶段全量基准回归测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase125SwarmConsensusContractTest,Phase126DistributedSwarmContractTest,Phase127E2ESwarmContractTest,Phase128AutonomousSwarmContractTest,Phase129UnifiedE2ESwarmContractTest,Phase130SwarmGovernanceContractTest,Phase131EnterpriseMcpProductionBenchmarkContractTest,Phase132HierarchicalGraphRagContractTest,Phase133E2EChaosBenchmarkContractTest,Phase134FrontendDagHitlIntegrationContractTest,Phase135SwarmDynamicTopologyContractTest,Phase136McpGatewayContractTest,Phase137GraphRagCognitiveContractTest,Phase138WorkflowSelfHealingContractTest

# 4. 验证前端 Vite 生产环境全量编译构建
cd frontend && npm run build:prod
```
