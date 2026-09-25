# Phase 141 实施方案与契约设计：多智能体认知状态回放、动态时空分叉沙盒与反事实交互调试中枢

## 一、方案核心目标与唯一算法假设

### 1.1 唯一待验证假设 (Hypothesis H-141)
在多智能体长程认知协作与博弈场景下，构建“基于写时复制与持久化结构共享的多智能体认知状态回放引擎 (`SwarmCognitiveStateReplayer`) + 动态时空分叉沙盒与反事实步进调度器 (`SpatiotemporalForkingSandboxGovernor`) + 纯 Java 21 Record 格式时空分叉审计存证凭单 (`CognitiveForkingSandboxReceipt`)”，能够实现：
1. 毫秒级捕获多智能体集群（含角色认知、黑板、千问 1536 维超球面观点向量与 W3C 因果 Span 树）的不可变全局认知状态快照，单次快照保存耗时 $\le 1.0\text{ms}$，内存增量结构共享空间节约率 $\ge 75.0\%$；
2. 支持任意历史时刻/轮次/Span 节点的无损双向步进回放（Forward-Step 与 Backward-Step），状态回滚重构耗时 $\le 2.0\text{ms}$，幽灵变量跨步污染率恒为 0.0%；
3. 在任意断点处派生平行推演分支（What-If Counterfactual Forking），允许注入 Prompt 补丁、RAG 上下文替换与参数覆写，分叉执行创建耗时 $\le 3.0\text{ms}$，且不同分支间沙箱隔离度 100.0%；
4. 签发纯 Java 21 Record 格式存证凭单，规范化记录分叉点、父子分支因果树与补丁哈希，SHA-256 常量时间自验真率 100.0%。

---

## 二、架构设计与核心组件规划

### 2.1 后端核心组件设计 (`qknow-hermes-core`)

#### 1. `SwarmCognitiveStateReplayer.java`
- **代码路径**：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/replay/SwarmCognitiveStateReplayer.java`；
- **定位**：多智能体全局认知状态捕获、写时复制结构共享与双向步进回放引擎；
- **核心数据结构**：
  * `SwarmAgentSnapshotState`：包含 `agentId`, `role`, `currentThought`, `workingMemory`, `qwenEmbedding` (1536维)；
  * `SwarmClusterSnapshot`：包含 `snapshotId`, `branchId`, `stepIndex`, `roundIndex`, `spanId`, `agentStates` (Map), `sharedBlackboard` (Map), `parentSnapshotId`, `timestamp`；
- **核心机制**：
  * **写时复制快照捕获 (`captureSnapshot`)**：以不可变 Map 记录发生变更的 Agent 状态与黑板增量，未变更部分沿用父快照引用，单次快照保存 $\le 1.0\text{ms}$，空间节约 $\ge 75.0\%$；
  * **双向步进回放 (`stepForward` / `stepBackward`)**：根据快照链表或时间戳精确定位历史任一时刻，完整还原集群全局状态，重构耗时 $\le 2.0\text{ms}$；
  * **零幽灵变量泄露保证**：对外返回的所有状态均由 `Collections.unmodifiableMap` 或不可变 Record 封装，彻底隔离外部修改。

#### 2. `SpatiotemporalForkingSandboxGovernor.java`
- **代码路径**：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/replay/SpatiotemporalForkingSandboxGovernor.java`；
- **定位**：动态时空分叉沙盒与反事实干预（What-If）调度器；
- **核心机制**：
  * **平行分支派生 (`forkBranch`)**：从指定基准快照点（`baseSnapshotId`）派生独立的执行分支 `newBranchId`，建立分支因果拓扑（ParentBranchId $\to$ ForkSpanId）；
  * **反事实热补丁注入 (`applyCounterfactualPatch`)**：允许开发者注入针对特定 Agent 的 Prompt 覆盖、修改特定黑板键值或注入外部边界事实；
  * **沙箱隔离推演 (`executeForkedStep`)**：在子分支沙盒中驱动智能体集群继续推演，所有生成的状态、观点向量与因果 Span 严格归属于新分支，主干与兄弟分支完全无感（隔离度 100.0%）；
  * **分支管理配额**：严格限制最大派生深度 $\le 5$ 级，最大并发分支数 $\le 20$ 个。

#### 3. `CognitiveForkingSandboxReceipt.java`
- **代码路径**：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/replay/CognitiveForkingSandboxReceipt.java`；
- **定位**：纯 Java 21 Record 格式不可变时空分叉审计存证凭单；
- **核心字段**：
  * `receiptId` (String): 全局唯一存证编号 (`RCP-FORK-...`)；
  * `traceId` (String): W3C 32 位 TraceId；
  * `parentBranchId` (String): 父分支标识；
  * `forkedBranchId` (String): 新派生分支标识；
  * `baseSnapshotId` (String): 分叉基准快照编号；
  * `forkSpanId` (String): 分叉对应的因果 SpanId；
  * `patchedAgentId` (String): 注入反事实补丁的智能体标识；
  * `patchSummary` (String): 补丁正文摘要；
  * `patchHash` (String): 补丁内容的 SHA-256 哈希；
  * `forkLatencyMs` (double): 分叉与首步推演耗时；
  * `timestamp` (long): 时间戳；
  * `sha256Signature` (String): 规范化全字段 SHA-256 签名；
- **防篡改自验真**：提供 `verifySignature()` 常量时间校验方法。

---

### 2.2 前端可视化扩展 (`frontend`)

#### `CognitiveForkingDebugWidget.vue`
- **路径**：`frontend/src/views/kb/bot/build/components/swarm/CognitiveForkingDebugWidget.vue`；
- **视觉风格与规范**：
  * 深度遵循 `docs/design-system` 的顶级 Apple iOS 26 Liquid Glass & Vibrancy 设计系统；
  * **双层材质**：底层高光 + $50\text{px}$ 模糊层与 `color-dodge` 提亮混合，严格遵守**无层叠上下文铁律**（绝不在组件自身滥用 `transform`/`isolation`/容器自身 `z-index`）；
  * **Headline 590 字重铁律**：主标题字重 590，其余全部 400，依靠光学字距微调；
  * **零阴影系统**：零大投影，靠材质厚薄与柔光轮廓线（`rgba(255, 255, 255, 0.12)`）构建纵深；
  * **信号色彩**：Apple 系统蓝 `#0088ff`（当前选中与主操作）、系统绿 `#34c759`（验真通过）、系统橙 `#ff8d28`（补丁注入中）、系统紫 `#6155f5`（平行分叉分支）；
- **核心交互特性**：
  1. **时空分支 DAG 树 (Spatiotemporal Branch Tree)**：可视化呈现主线（main）与派生的平行分支（fork-1, fork-2），节点带角色徽标与快照状态；
  2. **双向步进控制器 (Bidirectional Stepper Control)**：支持单步后退（Step Back）、单步前进（Step Forward）、原位挂起（Pause at Span）；
  3. **反事实 Prompt 热补丁注入台 (Counterfactual Patch Deck)**：选中任意 Agent，直接在玻璃抽屉中覆盖其输入 Prompt 或黑板上下文，点击“立即派生平行宇宙”；
  4. **不可变存证凭单自验真卡片 (Receipt Card)**：点击“常量时间验真”，毫秒级显示 100% 验真通过高光。

---

## 三、8 项严苛契约测试定义 (Contract Tests)

| 测试编号 | 契约方法名 | 核心验证指标与断言标准 |
| :--- | :--- | :--- |
| **TC-141-1** | `testStateSnapshot_captureAndStructuralSharing()` | 多智能体全局认知快照捕获：增量结构共享空间节约率 $\ge 75.0\%$，单次快照耗时 $\le 1.0\text{ms}$ |
| **TC-141-2** | `testBidirectionalReplay_forwardAndBackwardStepping()` | 多智能体状态双向步进寻址与恢复：历史时刻无损还原，单步回滚耗时 $\le 2.0\text{ms}$，数据完全保真 |
| **TC-141-3** | `testSpatiotemporalForking_sandboxIsolation()` | 在指定 Span 派生平行分支，注入 Prompt 补丁，分叉创建耗时 $\le 3.0\text{ms}$，沙箱隔离度 100% |
| **TC-141-4** | `testGhostVariablePollution_zeroLeakageVerification()` | 验证在平行分支大量修改与执行后，主分支及其历史快照读数绝对恒定，幽灵变量污染率恒为 0.0% |
| **TC-141-5** | `testCounterfactualPatch_whatIfDivergenceExecution()` | 注入反事实干预补丁后，新分支推演出完全不同的博弈策略与结果，形成可解释差分 |
| **TC-141-6** | `testCognitiveForkingSandboxReceipt_immutableVerification()` | 纯 Java 21 Record 凭单签名自验真：验证全字段不可变性与 SHA-256 哈希常量时间自验真率 100.0% |
| **TC-141-7** | `testHighConcurrencyForking_multiBranchScalability()` | 高并发派生 20 个平行分支并发推演，平均分叉时延 $\le 3.0\text{ms}$，零死锁无泄漏 |
| **TC-141-8** | `testEndToEndCognitiveReplayAndForking_fullPipelineIntegration()` | 端到端：多智能体执行 $\to$ 发现分歧断点 $\to$ 原位挂起 $\to$ 状态单步回退 $\to$ 派生反事实分支 $\to$ 注入补丁 $\to$ 恢复执行 $\to$ 签发不可变凭单 |

---

## 四、最小实现文件集合与禁止修改边界

### 4.1 最小修改文件集合
1. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/replay/SwarmCognitiveStateReplayer.java` (新建)
2. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/replay/SpatiotemporalForkingSandboxGovernor.java` (新建)
3. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/replay/CognitiveForkingSandboxReceipt.java` (新建)
4. `frontend/src/views/kb/bot/build/components/swarm/CognitiveForkingDebugWidget.vue` (新建)
5. `backend/tests/src/test/java/tech/qiantong/qknow/hermes/benchmark/Phase141CognitiveStateReplayContractTest.java` (新建)

### 4.2 严格禁止修改的边界
- 严禁修改已归档封存的力学物理沙箱目录：`tech.qiantong.qknow.ai.embodied.*`；
- 严禁修改全局系统默认 JDK 17，所有编译与测试必须严格使用 Java 21 隔离环境变量：
  `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH`；
- 严禁引入任何未经许可的外部第三方内存虚拟化或重型调试服务，必须使用纯原生 Java 21 不可变 Record 与结构共享内存模型；
- 严禁在测试中修改断言期望值以掩盖失败；
- 严禁使用 OpenAI API 或本地小模型，唯一生成模型为 DeepSeek API，唯一向量模型为阿里千问 1536 维超球面空间。

---

## 五、完整复现与全量回归验证命令

### 5.1 编译与契约测试验证命令
```bash
# 1. 编译 Hermes 核心子模块
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn clean install -pl qknow-hermes/qknow-hermes-core -DskipTests

# 2. 执行 Phase 141 专属契约基准测试 (8项严苛测试)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase141CognitiveStateReplayContractTest

# 3. 执行全量 17 个阶段跨阶段基准回归测试套件 (Phase 125 ~ Phase 141 共 72 项契约基准测试)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase125DynamicGraphPartitionContractTest,Phase126DecoupledRouterContractTest,Phase127DifferentiableSearchContractTest,Phase128RealtimeSublinearRankerContractTest,Phase129GraphHypergraphRerankerContractTest,Phase130StreamingRagTypewriterContractTest,Phase131MultimodalLayoutRagContractTest,Phase132HierarchicalContextEngineContractTest,Phase133ActiveLearningFeedbackContractTest,Phase134StreamingSemanticFlowContractTest,Phase135EnterpriseMcpSecurityContractTest,Phase136AdaptiveMultiAgentDebateContractTest,Phase137SteinerCausalPyramidContractTest,Phase138MultiAgentDeadlockWfgContractTest,Phase139DatabaseMcpSandboxContractTest,Phase140SwarmConsensusTraceContractTest,Phase141CognitiveStateReplayContractTest
```

### 5.2 前端构建验证命令
```bash
# 4. 执行前端生产打包构建验证
cd frontend && npm run build:prod
```

---

## 六、风险评估、停止条件与后续授权边界

### 6.1 残余风险评估
1. **多分支深度嵌套内存积累**：若分支无节制派生，可能造成堆内存增量，需限制最大嵌套深度 $\le 5$ 级并自动淘汰空闲分支；
2. **多线程并发回放与分叉时的时序锁竞争**：在快照存储与分支树管理中，全部使用 `ConcurrentHashMap` 与 `Collections.unmodifiableMap` 无锁读取。

### 6.2 立即停止条件 (Abort Criteria)
- 若快照保存耗时在基准测试中超过 $1.0\text{ms}$，立即停止；
- 若出现任何跨分支幽灵变量污染（主分支数据被污染），立即停止并检查不可变封装；
- 若 SHA-256 签名自验真失败率 $> 0.0\%$，立即停止。

### 6.3 后续授权边界
本实施方案获批后，授权仅限于上述最小修改文件集合的代码编写与测试验证。任何参数阈值与生产环境配置变更，均须单独报批。
