# Phase 140 实施方案与契约设计：多智能体认知协同博弈共识演化、动态反事实对抗审计与全链路可观测追踪中枢

## 一、方案核心目标与唯一算法假设

### 1.1 唯一待验证假设 (Hypothesis H-140)
在多智能体复杂业务认知博弈与协同决策场景下，构建“基于合谋信息熵与反事实魔鬼代言人注入的对抗演化调度器 + 基于 $\epsilon\text{-Nash}$ 与帕累托支配的多目标共识判定器 + 兼容 W3C/OpenTelemetry 纳秒级因果链路追踪与存证中枢 (`SwarmConsensusTraceReceipt`)”，能够实现：
1. 实时量化多智能体发言在阿里千问 1536 维超球面空间中的聚类分布与香农合谋信息熵（$H_{group}$），当谄媚趋同度（Sycophancy Score）$\ge 0.85$ 时在 $\le 2.0\text{ms}$ 内自动注入反事实魔鬼代言人（CRITIC）边界反例，使群体多样性回升 $\ge 30.0\%$，伪共识瓦解率 100.0%；
2. 基于超球面策略演化测地线欧氏位移差分（$\Delta \sigma \le 0.05$）精准判定达成 $\epsilon\text{-Nash}$ 纳什均衡稳态，结合质量、成本与风险三元目标进行帕累托前沿非支配优选，严格将博弈收敛控制在 $\le 5$ 轮内，单轮仲裁耗时 $\le 3.0\text{ms}$；
3. 全链路异步生成精确符合 W3C TraceContext 标准（32位 TraceId，16位 SpanId）的因果父子 Span 树，捕获思维演化、工具调用与决策置信度，签发纯 Java 21 Record 格式凭单，SHA-256 常量时间自验真率 100.0%。

---

## 二、架构设计与核心组件规划

### 2.1 后端核心组件设计 (`qknow-hermes-core`)

#### 1. `SwarmCollusionEntropyGuard.java`
- **代码路径**：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/consensus/SwarmCollusionEntropyGuard.java`；
- **定位**：多智能体发言语义合谋信息熵量化、谄媚趋同度实时监测与反事实对抗注入引擎；
- **核心机制**：
  * **超球面聚类分布计算**：接收各智能体阿里千问 1536 维超球面嵌入向量（$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$），计算均值中心向量与两两内积余弦相似度；
  * **香农合谋信息熵量化**：基于 Softmax 语义概率分布计算群体香农熵 $H_{group} = -\sum p_i \ln p_i$，量化计算谄媚趋同度 $S_{sycophancy} \in [0.0, 1.0]$；
  * **反事实魔鬼代言人 (Devil's Advocate / CRITIC) 注入**：当 $S_{sycophancy} \ge 0.85$ 且 $H_{group} < H_{crit}$ 时，触发合谋崩溃预警，自动生成对偶正交的极端边界反例扰动向量 $\mathbf{v}_{critic}$，迫使下一轮辩论探索空间重新发散至多峰态，多样性恢复 $\ge 30.0\%$；
  * **纳秒级纯算子执行**：单次判定与扰动生成耗时严格 $\le 2.0\text{ms}$。

#### 2. `EpsilonNashParetoArbitrator.java`
- **代码路径**：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/consensus/EpsilonNashParetoArbitrator.java`；
- **定位**：基于测地线位移差分的松弛纳什均衡 ($\epsilon\text{-Nash}$) 早停收敛与多目标帕累托前沿权衡仲裁器；
- **核心机制**：
  * **策略测地线差分早停**：追踪相邻轮次智能体策略向量的平均角位移 $\Delta \sigma^{(t)} = \frac{1}{N}\sum \arccos(\langle \mathbf{v}_i^{(t)}, \mathbf{v}_i^{(t+1)} \rangle)$。当 $\Delta \sigma \le 0.05$ 时，判定达到 $\epsilon\text{-Nash}$ 纳什均衡稳态，立即果断早停退出辩论循环，博弈轮次强制锁定在 $\le 5$ 轮；
  * **多目标帕累托前沿优选 (Pareto Dominance)**：针对各候选方案的三元效用向量 $\mathbf{U} = \langle u_{quality}, u_{cost}, u_{risk} \rangle$ 执行非支配排序（Non-dominated Sorting），彻底淘汰被支配的次优解，选出帕累托前沿上的全局最优综合折中方案；
  * **高性能微秒级仲裁**：单轮纳什与帕累托双重仲裁耗时严格 $\le 3.0\text{ms}$。

#### 3. `SwarmConsensusTraceReceipt.java`
- **代码路径**：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/consensus/SwarmConsensusTraceReceipt.java`；
- **定位**：纯 Java 21 Record 格式不可变 W3C 规范全链路追踪与博弈共识存证凭单；
- **包含内部 Record/数据结构**：
  * `SwarmTraceSpan`：包含 `traceId` (32位十六进制), `spanId` (16位十六进制), `parentSpanId`, `agentRole`, `operationType`, `startTimeNs`, `endTimeNs`, `attributes`；
  * `SwarmConsensusTraceReceipt` 凭单主结构：
    - `receiptId` (String): 全局唯一存证流水号 (`RCP-SWARM-...`)；
    - `traceId` (String): W3C 规范 32 位十六进制全局链路追踪标识；
    - `rootSpanId` (String): 根 Span 16 位十六进制标识；
    - `totalSpans` (int): 捕获的因果 Span 节点总数；
    - `spans` (List<SwarmTraceSpan>): 拓扑有序的因果链路列表；
    - `rounds` (int): 经历的博弈收敛轮数；
    - `sycophancyScore` (double): 最终辩论收敛时的谄媚趋同度；
    - `entropyValue` (double): 群体香农信息熵；
    - `isCollusionDetected` (boolean): 是否曾触发谄媚合谋预警；
    - `isDevilAdvocateInjected` (boolean): 是否执行过反事实魔鬼代言人对抗注入；
    - `isEpsilonNashConverged` (boolean): 是否因 $\epsilon\text{-Nash}$ 早停稳态收敛；
    - `winningProposalId` (String): 最终帕累托胜选方案标识；
    - `winningProposalContent` (String): 胜选方案摘要/内容；
    - `latencyMs` (double): 全程决策与仲裁耗时；
    - `timestamp` (long): 毫秒时间戳；
    - `sha256Signature` (String): 全字段规范化密码学 SHA-256 防篡改签名；
  * **防篡改与自验真**：提供常量时间自验真方法 `verifySignature()`。

---

### 2.2 前端可视化扩展 (`frontend`)

#### `SwarmConsensusTraceWidget.vue`
- **路径**：`frontend/src/views/kb/bot/build/components/swarm/SwarmConsensusTraceWidget.vue`；
- **视觉风格与规范**：
  * 严格遵循 Rule 2 UI/UX Pro Max 规范与单色现代暗黑钛金毛玻璃设计 Token；
  * 极致现代单色黑底：材质底色 `#0a0a0c`、毛玻璃 `backdrop-filter: blur(20px)`、发丝边框 `rgba(255,255,255,0.08)`、强调色单色白/钛金金银阶；
- **核心交互特性**：
  1. **合谋信息熵与谄媚度仪表盘 (Collusion Entropy Gauge)**：动态展示群体香农熵曲线、谄媚趋同度滑动条；当触发魔鬼代言人注入时显示钛金警示脉冲波；
  2. **$\epsilon\text{-Nash}$ 测地线收敛与多目标帕累托雷达图**：展示多轮次角位移收敛速率（$\le 5$ 轮）及最终胜选方案在质量、成本、风险三维度的帕累托包络；
  3. **W3C 因果拓扑 Span 树调用时序甘特图**：以层级树与甘特时序形式直观还原每位 Agent 的思考、工具调用与投票仲裁纳秒级因果拓扑；
  4. **流畅度保证**：前端渲染时延 $\le 16.6\text{ms}$，满足 60 FPS 锁步标准。

---

## 三、8 项严苛契约测试定义 (Contract Tests)

| 测试编号 | 契约方法名 | 核心验证指标与断言标准 |
| :--- | :--- | :--- |
| **TC-140-1** | `testCollusionEntropy_sycophancyCollapseDetection()` | 构造高度趋同策略向量，检测谄媚度 $\ge 0.85$ 时精准触发合谋崩塌预警，香农信息熵量化耗时 $\le 2.0\text{ms}$ |
| **TC-140-2** | `testCounterfactualDevilAdvocate_adversarialInjection()` | 触发反事实魔鬼代言人对抗注入后，群体超球面分布重新发散，信息熵恢复提升 $\ge 30.0\%$，伪共识瓦解率 100.0% |
| **TC-140-3** | `testEpsilonNash_geodesicConvergenceEarlyStop()` | 基于千问 1536 维超球面策略欧氏角位移差分 $\Delta \sigma \le 0.05$ 判定纳什均衡早停，收敛轮数 $\le 5$ 轮，单轮耗时 $\le 3.0\text{ms}$ |
| **TC-140-4** | `testParetoDominance_multiObjectiveArbitration()` | 质量、成本、风险三元目标非支配排序：确保严格淘汰被绝对支配的劣解，选出帕累托前沿上的全局最优折中解 |
| **TC-140-5** | `testW3cTraceContext_causalSpanHierarchyIntegrity()` | 严格遵循 W3C 规范生成 32 位 TraceId 与 16 位 SpanId，因果父子链路 100% 连通无孤儿 Span，Span 创建入栈耗时 $\le 50\mu s$ |
| **TC-140-6** | `testSwarmConsensusTraceReceipt_immutableVerification()` | 纯 Java 21 Record 凭单签名自验真：验证全字段不可变性与 SHA-256 哈希常量时间自验真率 100.0% |
| **TC-140-7** | `testHighConcurrencySwarmArbitration_performance()` | 高并发 100 次博弈协同模拟，单次决策仲裁平均耗时 $\le 3.0\text{ms}$，零死锁无内存泄露 |
| **TC-140-8** | `testEndToEndSwarmConsensus_fullPipelineIntegration()` | 端到端全链路闭环：多智能体发言 $\to$ 谄媚熵检测 $\to$ 反事实注入 $\to$ 策略收敛早停 $\to$ 帕累托仲裁 $\to$ W3C Span 追踪 $\to$ 凭单签发与自验真 |

---

## 四、最小实现文件集合与禁止修改边界

### 4.1 最小修改文件集合
1. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/consensus/SwarmCollusionEntropyGuard.java` (新建)
2. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/consensus/EpsilonNashParetoArbitrator.java` (新建)
3. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/consensus/SwarmConsensusTraceReceipt.java` (新建)
4. `frontend/src/views/kb/bot/build/components/swarm/SwarmConsensusTraceWidget.vue` (新建)
5. `backend/tests/src/test/java/tech/qiantong/qknow/hermes/benchmark/Phase140SwarmConsensusTraceContractTest.java` (新建)

### 4.2 严格禁止修改的边界
- 严禁修改已归档封存的力学物理沙箱目录：`tech.qiantong.qknow.ai.embodied.*`；
- 严禁修改全局系统默认 JDK 17，所有编译与测试必须严格使用 Java 21 隔离环境变量：
  `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH`；
- 严禁引入任何未经许可的外部第三方重型分布式追踪服务端（如 Jaeger/Zipkin 独立微服务），必须使用纯原生 Java 21 Record 内存因果树模型；
- 严禁在测试中修改断言期望值以掩盖失败；
- 严禁使用 OpenAI API 或本地小模型，唯一生成模型为 DeepSeek API，唯一向量模型为阿里千问 1536 维超球面空间。

---

## 五、完整复现与全量回归验证命令

### 5.1 编译与契约测试验证命令
```bash
# 1. 编译 Hermes 核心子模块
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn clean install -pl qknow-hermes/qknow-hermes-core -DskipTests

# 2. 执行 Phase 140 专属契约基准测试 (8项严苛测试)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase140SwarmConsensusTraceContractTest

# 3. 执行全量 16 个阶段跨阶段基准回归测试套件 (Phase 125 ~ Phase 140 共 64 项契约基准测试)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase125DynamicGraphPartitionContractTest,Phase126DecoupledRouterContractTest,Phase127DifferentiableSearchContractTest,Phase128RealtimeSublinearRankerContractTest,Phase129GraphHypergraphRerankerContractTest,Phase130StreamingRagTypewriterContractTest,Phase131MultimodalLayoutRagContractTest,Phase132HierarchicalContextEngineContractTest,Phase133ActiveLearningFeedbackContractTest,Phase134StreamingSemanticFlowContractTest,Phase135EnterpriseMcpSecurityContractTest,Phase136AdaptiveMultiAgentDebateContractTest,Phase137SteinerCausalPyramidContractTest,Phase138MultiAgentDeadlockWfgContractTest,Phase139DatabaseMcpSandboxContractTest,Phase140SwarmConsensusTraceContractTest
```

### 5.2 前端构建验证命令
```bash
# 4. 执行前端生产打包构建验证
cd frontend && npm run build:prod
```

---

## 六、风险评估、停止条件与后续授权边界

### 6.1 残余风险评估
1. **千问向量极端高维数值下溢**：当 1536 维超球面向量计算 Softmax 指数分布时，若未做最大值平移归一化，可能引起浮点数溢出，需采用稳定版 Log-Sum-Exp 变换；
2. **多线程并发记录 Span 时的时钟抖动**：系统使用 `System.nanoTime()` 计算相对时延，`System.currentTimeMillis()` 记录绝对时间戳，避免跨虚拟线程的时钟不同步问题。

### 6.2 立即停止条件 (Abort Criteria)
- 若合谋信息熵量化耗时在基准测试中超过 $2.0\text{ms}$，立即停止实现并排查向量算子循环；
- 若多目标帕累托前沿排序引入死循环或未在 $5$ 轮内收敛，立即停止；
- 若 SHA-256 签名自验真失败率 $> 0.0\%$，立即停止。

### 6.3 后续授权边界
本实施方案获批后，授权仅限于上述最小修改文件集合的代码编写与测试验证。任何参数阈值（如 $\epsilon = 0.05$、$S_{sycophancy} = 0.85$）的修改或生产环境配置变更，均须单独报批。
