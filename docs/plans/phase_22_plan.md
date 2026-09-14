# Phase 22 实施方案：多智能体动态分工协作、蜂群通信协议与分层任务意图分解 (Multi-Agent Swarm Orchestration & Hierarchical Task Intent Decomposition)

> **文档位置**：`docs/plans/phase_22_plan.md`  
> **前置理论与工业支撑**：`docs/plans/phase_22_academic_report.md` & `docs/plans/phase_22_industrial_report.md`  
> **门禁状态**：**GATE_PASSED_WAITING_APPROVAL**（第一回合调研与方案编写完成，待用户批准实施契约后启动 TDD 编码）  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` / `deepseek-reasoner`）；唯一向量模型为 **阿里千问 (Qwen) Embedding（1536维）**；全链路无本地大模型，彻底弃用 OpenAI/GPT API。

---

## 一、方案背景与改造目标

### 1.1 背景与现状诊断
深入走查 `backend/qknow-hermes` 核心代码库（重点分析 `tech.qiantong.qknow.hermes.agent.SupervisorAgent`、`AgentOrchestrator`、`BaseAgent` 及 `guard/ReActCycleGuard`），当前系统的多智能体协同存在四大根本性工程缺陷：

1. **任务分解过于平坦，缺乏标准 DAG 拓扑排序与阶段执行引擎**：
   - 当前 `SupervisorAgent.chat` 仅能通过单次简陋的 Prompt 将用户问题拆解为平坦的 JSON 数组 `[{"worker":"...", "subtask":"..."}]`；
   - 无法表达复杂的时序依赖（如“任务 B 必须等待任务 A 的数据检索输出，任务 C 必须同时等待 A 与 B 完成后再做分析汇总”）；
   - 缺乏阶段执行（Phased Execution）隔离屏障与自动数据流注入（Context Injection）。
2. **任务分发硬编码，缺乏动态合同网竞标（Contract Net）与自适应降级**：
   - 子任务分配直接在 Prompt 中填入固定的 Worker 字符串名称（如 `RAGAgent`, `SearchAgent`）；
   - 未建立 Worker 能力清单（Capabilities Schema）、实时负载（Load Factor）、历史履约置信度与动态竞标机制；
   - 一旦目标 Worker 繁忙或名称不存在，直接抛弃跳过，缺少动态能力竞标与安全兜底降级（Fallback Default Worker）。
3. **数据竞争与脏写覆盖，缺乏版本控制与全局共享黑板（Shared Blackboard）**：
   - `SupervisorAgent` 使用 `ConcurrentHashMap<String, String>` 存储结果，以 `subtask.worker` 作为 Key；
   - 若两个子任务分配给同一个 Worker，后执行的任务直接覆写先执行的任务，引发严重事实丢失；
   - 缺乏全局事实、假设、产物分区，缺乏 CAS 乐观锁版本控制，没有响应式状态广播通知，也没有执行轨迹审计（Trace Log）。
4. **并发调度使用公共线程池与无超时永久阻塞，极易引发服务雪崩与死锁**：
   - `SupervisorAgent.dispatchToWorkers` 直接调用 `CompletableFuture.runAsync()`，默认绑定 JVM 全局共享的 `ForkJoinPool.commonPool()`；
   - 末端直接调用 `CompletableFuture.allOf(...).join()`，没有任何超时设置（Timeout）！一旦外部检索超时或 Worker 卡死，公共线程池瞬间耗尽，拖垮宿主进程内全部并行流与定时任务；
   - 缺乏调用链深度计数（Swarm Hop Depth）、环路委托检测（Cycle Detection）和跨智能体语义振荡熔断器。

### 1.2 改造目标与量化指标
1. **DAG 拓扑阶段推进**：实现基于 Kahn 拓扑排序算法的 `TopologicalPhasedDispatcher`，结合自定义隔离线程池（`AgentTaskThreadPool`）与显式依赖上下文流式注入，保证多阶段时序依赖无环执行成功率 $100\%$；
2. **动态合同网竞标**：实现标准 FIPA-CNP 扩展的 `ContractNetDispatcher`，基于阿里千问 1536 维能力匹配、动态负载率与贝叶斯历史置信度加权选标，无标或低分时自适应软降级兜底率 $100\%$；
3. **版本化响应式共享黑板**：构建具备版本号 CAS 乐观锁、状态分区（Facts / Hypotheses / Artifacts）与 Project Reactor `Sinks.Many` 广播总线的 `SharedBlackboard`，实现并发写入数据冲突丢失率严格为 $0\%$；
4. **蜂群环路防死锁熔断**：建立限制协同跳转深度（$\le 5$）、环路路径哈希检测、语义指纹振荡熔断与硬超时（30s）Fail-Open 的 `SwarmLoopGuard`，保证蜂群死循环震荡熔断拦截率 $100\%$，主线程与公共线程池零卡死、`commonPool` 污染率为 $0$。

---

## 二、系统架构与核心组件设计

```
+----------------------------------------------------------------------------------------------------+
|                                    Client / Chat Application                                       |
+-------------------------------------------------+--------------------------------------------------+
                                                  | gRPC ChatRequest
                                                  v
+-------------------------------------------------+--------------------------------------------------+
|                            qknow-hermes: SupervisorAgent                                           |
|                                                                                                    |
|  1. 意图分解与 DAG 构建 (HierarchicalTaskPlanner - DeepSeek API)                                  |
|     +--> Kahn 算法拓扑排序 -> 检测环路 (有环立即防御性降级) -> 生成分层阶段计划 PhasedExecutionPlan     |
|                                                                                                    |
|  2. 拓扑阶段调度器 (TopologicalPhasedDispatcher)                                                  |
|     +--> 物理隔离独立线程池 (agentTaskExecutor, 有界队列 200, 拒绝策略 CallerRuns)                  |
|     +--> 按 Phase 顺序推进 (Phase 0 -> Phase 1 -> ... -> Phase N)                                  |
|     +--> Phase 内部并发执行，带 30s 硬超时 (orTimeout) + 软降级 (Fail-Open)                        |
|                                                                                                    |
|  3. 动态合同网竞标引擎 (ContractNetDispatcher)                                                     |
|     +--> 发布标书 TaskCfp (含 Qwen 1536维能力需求描述与上游上下文)                                   |
|     +--> Worker 动态评估投标 (WorkerBid: 0.5*能力匹配 + 0.3*(1-负载) + 0.2*历史信誉)                 |
|     +--> 选标授标 (>=0.60) / 软降级至 DefaultFallbackWorker                                        |
|                                                                                                    |
|  4. 响应式共享黑板状态总线 (SharedBlackboard)                                                      |
|     +--> CAS 乐观锁原子版本控制 (AtomicLong version)                                               |
|     +--> 状态分区存储 (Facts, Hypotheses, TraceLogs)                                               |
|     +--> Project Reactor Sinks.Many 响应式事件广播总线                                             |
|                                                                                                    |
|  5. 蜂群防死锁与振荡熔断器 (SwarmLoopGuard)                                                       |
|     +--> 前置拦截：调用链深度 MaxDepth <= 5 拦截 + 拓扑闭环检测                                      |
|     +--> 后置拦截：SHA-256 语义指纹滑动窗口检测 (重复 >= 3 次强制熔断)                             |
|                                                                                                    |
|  6. 结果收敛聚合 (DeepSeek API)                                                                    |
|     +--> 从黑板汇总全阶段事实产出，生成结构化最终综合答复                                            |
+----------------------------------------------------------------------------------------------------+
```

---

## 三、数学定理与算法收敛保障

1. **波前分层单调终止性定理 (Theorem 1.1)**：
   系统通过入度为零的节点集合逐层抽取波前集合 $W_k$。Lyapunov 势函数 $\Phi(t) = |V_T \setminus \mathcal{C}_t|$ 在每个阶段以 $|W_t| \ge 1$ 严格离散单调递减，保证系统必在有限步 $K \le |V_T|$ 阶段内严格收敛终止。
2. **多智能体最优分解粒度定理 (Theorem 1.2)**：
   在通信协调与 Prompt 拼接开销模型 $C(k) = c_0 + \alpha k$ 下，端到端执行延迟最小化的最优子任务分解数满足解析解 $K^* = \sqrt{\frac{(1-f)T_0}{\alpha}}$。系统硬性限制最大子任务数 $K \le 5$，杜绝细碎过度分解引发的延迟反弹与 Token 暴击。
3. **在线胜标分配贪心近似比与纳什均衡 (Theorem 2.1 & 2.2)**：
   基于次模目标函数 $F(\mathcal{S})$，在线流式最高分贪心选标算法保证全局收益不低于 $(1 - 1/e) \approx 0.632$；结合超时惩罚与贝叶斯信誉衰减模型，真实申报算力负载（Truth-telling）构成全体 Worker 的弱占优纳什均衡。
4. **黑板语义熵衰减与早停下界 (Theorem 3.1 & 3.2)**：
   有效证据追加使得黑板假设后验分布的语义熵 $H(\mathcal{S}_t)$ 条件期望单调递减，衰减差额等于期望互信息 $I(\mathcal{H}; e_{t+1} \mid \mathcal{E}_t)$。当语义熵压降至阈值 $\epsilon_{\text{entropy}}$ 时提前截断收敛，节约无效调用。
5. **蜂群网络死锁消除不变量 (Theorem 4.1)**：
   由 Tarjan SCC 拓扑回环检测、祖先签名包含性判定与硬上限深度门控 $D_{\max} \le 5$ 共同保障，系统状态转移概率矩阵满足幂零性 $\mathbf{P}^{D_{\max}} = \mathbf{0}$，谱半径 $\rho(\mathbf{P}) = 0$，死锁消除不变量（Deadlock-Free Invariant）全局恒成立。

---

## 四、工业级踩坑规避与防御机制

1. **规避事故 1：多 Agent 循环委托与死锁震荡**：
   - 严禁 Worker 之间私下无序相互委托，所有跨阶段流转必须由 Supervisor 编排或通过黑板发布；
   - `SwarmLoopGuard` 强制校验调用链深度 $\le 5$；检测到调用链中重复出现相同任务 ID 立即抛出 `SWARM_TOPOLOGY_CYCLE_DETECTED`；
   - 输出内容计算 SHA-256 语义指纹，滑动窗口内连续出现 3 次相同语义输出触发 `SWARM_SEMANTIC_OSCILLATION_BREAKER` 强制收敛。
2. **规避事故 2：共享黑板并发状态竞争与数据污染**：
   - 彻底摒弃以 `worker` 名称为 Key 的扁平 Map，改为以强类型 `taskId` 为 Key；
   - 黑板写入采用不可变 Record 与 CAS 乐观锁版本递增（`AtomicLong globalVersion`）；
   - 下游任务仅能以只读视图消费已在黑板上 Commit 的上游阶段产出，杜绝读取中间草稿。
3. **规避事故 3：单点 Worker 挂死拖垮全局线程池**：
   - 创建独立的 `agentTaskExecutor` 线程池（核心 8，最大 32，队列 200，CallerRunsPolicy），杜绝借用 JVM 公共 `ForkJoinPool.commonPool()`；
   - 任务 Future 显式配置 `.orTimeout(task.timeoutSeconds(), TimeUnit.SECONDS)`，超时自动触发 `exceptionally` 软降级写入降级事实（Fail-Open），保证后续阶段不卡死。

---

## 五、核心类与接口设计

### 5.1 目录结构规划
所有改动与新增均归属于 `backend/qknow-hermes/qknow-hermes-core` 模块：
```
backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/
├── EnhancedBaseAgent.java             // [NEW] 具备能力清单、负载感知与竞标接口的 Agent 基类
├── SupervisorAgent.java                 // [MODIFY] 重构接入 DAG 调度、合同网、黑板与蜂群守卫
├── dag/
│   ├── DagTaskNode.java                // [NEW] 强类型 DAG 任务节点 Record
│   ├── PhasedExecutionPlan.java        // [NEW] 拓扑排序后的分层阶段计划 Record
│   ├── HierarchicalTaskPlanner.java    // [NEW] 分层任务意图分解器 (LLM + Kahn 拓扑排序)
│   └── TopologicalPhasedDispatcher.java// [NEW] 拓扑分层调度引擎 (隔离线程池 + 阶段并发 + 超时软降级)
├── bidding/
│   ├── TaskCfp.java                    // [NEW] 招标标书 Record
│   ├── WorkerBid.java                  // [NEW] 投标单 Record
│   └── ContractNetDispatcher.java      // [NEW] 工业级合同网竞标与动态路由引擎
├── blackboard/
│   ├── BlackboardEntry.java            // [NEW] 版本化黑板条目 Record
│   ├── BlackboardEvent.java            // [NEW] 响应式黑板事件 Record
│   └── SharedBlackboard.java           // [NEW] 线程安全响应式共享黑板状态总线
└── guard/
    ├── SwarmLoopGuard.java             // [NEW] 蜂群环路防死锁与振荡熔断器
    └── SwarmLoopException.java         // [NEW] 蜂群熔断专有异常
```

### 5.2 核心组件规格

1. **`DagTaskNode` & `PhasedExecutionPlan`**：
   ```java
   public record DagTaskNode(
           String taskId,
           String objective,
           String requiredCapability,
           List<String> dependencies,
           int timeoutSeconds,
           Map<String, Object> metadata
   ) {}
   
   public record PhasedExecutionPlan(
           List<List<DagTaskNode>> phases,
           int totalTasks
   ) {}
   ```
2. **`HierarchicalTaskPlanner`**：
   - 提示词驱动 DeepSeek 生成带 `dependencies` 的子任务 JSON 数组；
   - 内置 Kahn 算法：统计入度、队列循环提取入度为 0 的波前批次、拓扑分层，若存在环路则抛出异常或降级。
3. **`TopologicalPhasedDispatcher`**：
   - 专用线程池 `agentTaskExecutor`（8/32/200/CallerRuns）；
   - 外层循环遍历 `phases`，内层使用 `CompletableFuture.runAsync()` 并发执行当前阶段任务；
   - 链式调用 `.orTimeout(task.timeoutSeconds(), TimeUnit.SECONDS)` 与 `.exceptionally()` 软降级；
   - 使用 `CompletableFuture.allOf().join()` 作为阶段同步栅栏。
4. **`ContractNetDispatcher`**：
   - 维护 `Map<String, EnhancedBaseAgent> registeredWorkers`；
   - 组装 `TaskCfp`，调用各 Worker 的 `evaluateAndBid(cfp)`；
   - 按 `bidScore` 降序排列，最高分 $\ge 0.60$ 授标；无标或低分时自适应路由至 `defaultFallbackWorker`。
5. **`SharedBlackboard`**：
   - `AtomicLong globalVersion` 全局递增；
   - `ConcurrentHashMap<String, BlackboardEntry> factsTable`；
   - `commitFact(key, value, sourceTag)` 原子写入并触发 `Sinks.Many` 广播；
   - `getFactsByKeys(List<String> keys)` 提取只读快照。
6. **`SwarmLoopGuard`**：
   - `preCheck(sessionId, taskId)`：深度检查（$\ge 5$ 熔断）、调用链环路检查；
   - `inspectOutput(sessionId, output)`：文本规整化后计算 SHA-256，滑动窗口重复 $\ge 3$ 次触发振荡熔断；
   - `cleanSession(sessionId)`。
7. **`EnhancedBaseAgent`**：
   - 继承自现有 `BaseAgent`，扩展 `capabilities` 集合；
   - 提供 `evaluateAndBid(TaskCfp cfp)`：计算能力匹配（字符串或向量余弦）、当前负载与置信度加权得分；
   - 提供 `executeTask(String objective, String context)`。

---

## 六、TDD 测试驱动开发计划 (Red-Green Verification)

在 `backend/tests/src/test/java/tech/qiantong/qknow/hermes/agent/SwarmOrchestrationContractTest.java` 中构建不少于 10 项严密测试用例：

1. **`testHierarchicalTaskPlannerKahnTopologicalSorting`**：验证 Kahn 算法对多依赖任务图进行正确分层（无依赖在 Phase 0，有依赖在 Phase 1，最后汇聚在 Phase 2）；
2. **`testHierarchicalTaskPlannerCycleDetection`**：构造包含循环依赖的任务图（$A \to B \to A$），断言规划器准确捕获并抛出依赖循环异常；
3. **`testSharedBlackboardCasVersionAndPartition`**：多线程并发调用 `commitFact`，断言全局版本号严格单调递增，无任何写入覆盖丢失，快照状态完备；
4. **`testSharedBlackboardReactiveEventStream`**：使用 StepVerifier 订阅黑板 `eventStream`，提交事实时能够精确接收到广播事件与版本号；
5. **`testContractNetBiddingWeightedOptimalSelection`**：构造两个 Worker（一个能力高度匹配但轻微负载，另一个不匹配），断言合同网竞标器准确将任务授标给得分最高的 Worker；
6. **`testContractNetBiddingFallbackOnLowScore`**：构造所有 Worker 得分均低于 0.60 或无可用 Worker，断言竞标器自动降级至 `DefaultFallbackWorker` 履约；
7. **`testTopologicalPhasedDispatcherPhasedContextInjection`**：模拟多阶段执行，断言下游任务执行时其输入上下文准确注入了上游任务的黑板产出；
8. **`testTopologicalPhasedDispatcherTimeoutAndFailOpen`**：模拟某个 Worker 执行超时（卡顿 10s，任务超时设置为 1s），断言触发硬超时切断并写入降级上下文（Fail-Open），后续任务未被阻塞；
9. **`testSwarmLoopGuardMaxDepthExceededBreaker`**：模拟协同跳转深度连续递增，当达到第 5 层时断言触发 `SWARM_MAX_DEPTH_EXCEEDED` 强制熔断；
10. **`testSwarmLoopGuardTopologyCycleBreaker`**：模拟同一任务在当前调用链中二次出现，断言触发 `SWARM_TOPOLOGY_CYCLE_DETECTED` 熔断阻断；
11. **`testSwarmLoopGuardSemanticOscillationBreaker`**：模拟智能体连续产生 3 次相同语义输出，断言触发 `SWARM_SEMANTIC_OSCILLATION_BREAKER` 强制收敛；
12. **`testSupervisorAgentEndToEndSwarmOrchestration`**：端到端验证 `SupervisorAgent` 在新架构下完整跑通分解、阶段分派、黑板共享、结果收敛聚合。

---

## 七、实施步骤与文件清单

### 7.1 文件清单
- **新增核心业务文件**：
  1. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/EnhancedBaseAgent.java`
  2. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/dag/DagTaskNode.java`
  3. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/dag/PhasedExecutionPlan.java`
  4. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/dag/HierarchicalTaskPlanner.java`
  5. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/dag/TopologicalPhasedDispatcher.java`
  6. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/bidding/TaskCfp.java`
  7. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/bidding/WorkerBid.java`
  8. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/bidding/ContractNetDispatcher.java`
  9. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/blackboard/BlackboardEntry.java`
  10. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/blackboard/BlackboardEvent.java`
  11. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/blackboard/SharedBlackboard.java`
  12. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/guard/SwarmLoopGuard.java`
  13. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/guard/SwarmLoopException.java`
- **重构现有业务文件**：
  14. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/SupervisorAgent.java`
- **新增测试文件**：
  15. `backend/tests/src/test/java/tech/qiantong/qknow/hermes/agent/SwarmOrchestrationContractTest.java`

### 7.2 禁止触碰边界
- 严禁修改外部向量模型接口（保持阿里千问 1536 维标准不变）；
- 严禁修改生成模型基线（保持 DeepSeek API 标准不变）；
- 严禁修改 Java 21 全局环境变量，所有构建必须指定 SDKMAN 局部路径；
- 严禁借用系统公共 `ForkJoinPool.commonPool()` 执行 Agent 任务。

---

## 八、复现命令与验证门禁

在执行前、中、后严格通过指定 Java 21 隔离环境执行回归验证：
```bash
# 1. 执行 Phase 22 专属契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn -pl tests -Dtest=SwarmOrchestrationContractTest test

# 2. 执行 Hermes 模块全量单测
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn -pl tests -Dtest=*Agent* test

# 3. 后端全量防退化回归测试 (750+ 项测试全量绿灯)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn clean test -pl tests

# 4. 前端构建校验 (确保零破坏)
npm run build --prefix frontend
```
