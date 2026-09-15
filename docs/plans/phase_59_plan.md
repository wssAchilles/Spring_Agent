# Phase 59 架构实施方案：多智能体跨层级信念状态对齐、分层贝叶斯意图推断与自反博弈网络

## 一、方案背景与唯一可证伪假设

### 1. 业务背景
在复杂的企业级多智能体协同应用中（如高层业务战略分解 -> 中层工作流编排 -> 底层数据库/工具执行 -> 旁路质检审计），不同层级智能体掌握的局部上下文存在严重的非对称性。缺乏概率信念对齐会导致“宏观战略意图被底层机械扭曲执行”；而若无限制地引入高阶心智推测（Theory of Mind），又会导致智能体间陷入“推测对方决策”的自反死锁与推理震荡。

### 2. 唯一待验证假设 (H-PHASE59-001)
> **假设陈述 (H-PHASE59-001)**：
> 在多智能体跨层级协同网络中，通过引入基于阿里千问 1536 维超球面流形投影的分层贝叶斯意图推断器、信息几何几何平均对称信念对齐器、有界 $k \le 2$ 自反认知博弈求解器以及信念散度安全共识屏障，相比无信念校准与无界递归推断，能够使多轮交互下的高阶意图识别置信度提升至 $\ge 90\%$ 且信念熵指数单调衰减，彻底杜绝高阶自反死锁（死锁率严格为 0%），当高低层信念分歧超过安全限额 $D^*_{\max} = 1.5$ 时 100% 物理硬拦截并完成信息几何无偏自愈，端到端单次治理协调耗时 $\le 10\text{ms}$ 并签发自签名自校验通过率 100% 的不可变 SHA-256 存证凭单。

---

## 二、六大核心组件与领域契约设计

所有类落地在 `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/belief/`：

### 1. `BeliefAlignmentReceipt.java`（不可变存证凭单 Record）
- **职责**：跨层级信念状态对齐与自反博弈执行的密码学不可篡改存证凭据；
- **字段**：
  - `receiptId` (UUID 编号)
  - `coordinationRoundId` (协同轮次 ID)
  - `macroAgentId` (高层宏观智能体 ID)
  - `microAgentId` (底层微观智能体 ID)
  - `inferredIntent` (推断出的主导意图标签)
  - `intentConfidence` (意图后验概率置信度)
  - `beliefEntropy` (最终信念香农熵)
  - `jeffreysDivergence` (高低层信念对称杰弗里斯散度)
  - `cognitiveLevel` (自反博弈求解层级 $k \in \{0, 1, 2\}$)
  - `selectedAction` (博弈均衡收敛的最优执行动作)
  - `barrierTriggered` (是否触发散度安全屏障拦截)
  - `alignmentSuccessful` (是否成功达成对齐共识)
  - `timestamp` (创建时间戳)
  - `sha256Signature` (包含上述全部字段摘要的 SHA-256 自签名)
- **方法**：`verifySignature()` 密码学自验方法。

### 2. `HierarchicalBayesianIntentInferer.java`（分层贝叶斯意图推断器）
- **职责**：基于定理 1.1，计算意图后验分布 $P(I \mid \mathbf{o}_{1:t})$；
- **特性**：
  - 维护预定义意图假设空间 $\mathcal{I}$，及各意图在千问 1536 维超球面上的中心锚点特征向量；
  - 基于 vMF 分布与观测证据语义余弦相似度计算条件似然比；
  - 贝叶斯后验递推平滑更新，计算信念香农熵 $H(t)$；
  - 判定后验熵门槛：当 $H(t) > \ln 2$ 时标记为模糊未收敛，当 $H(t) \le \ln 2$ 且概率 $\ge 0.90$ 时标记为置信收敛。

### 3. `CrossHierarchicalBeliefAligner.java`（跨层级信念状态对齐器）
- **职责**：基于定理 1.3，对高低层离散信念分布实施信息几何对称对齐；
- **特性**：
  - 计算对称杰弗里斯散度 $D_{\text{J}}(P_{\text{macro}} \parallel P_{\text{micro}}) = \frac{1}{2}(D_{\text{KL}} + D_{\text{KL}})$；
  - 计算几何平均无偏最优投影：$P_{\text{aligned}}(s) = \frac{\sqrt{P_{\text{macro}}(s) P_{\text{micro}}(s)}}{\sum_{s'} \sqrt{P_{\text{macro}}(s') P_{\text{micro}}(s')}}$；
  - 保证对齐后分布到两者的相对熵和最小化，消除非对称认知断层。

### 4. `ReflectiveGameEngine.java`（有限视界自反博弈引擎）
- **职责**：基于定理 1.2，实施 $k$-level 认知层级博弈求解；
- **特性**：
  - 明确限制层级 $k \in \{0, 1, 2\}$，硬拦截任何 $k \ge 3$ 的递归调用，从根本上杜绝自反死锁；
  - Level-0：均匀探索基线；
  - Level-1：针对 Level-0 的单步最佳应对 (Best Response)；
  - Level-2：按泊松分布（$\tau=1.5$）综合 Level-0 与 Level-1 的加权应对；
  - 在有限离散动作空间中快速求解协同均衡动作，耗时 $\le 5\text{ms}$。

### 5. `BeliefConsensusBarrier.java`（信念共识安全屏障）
- **职责**：基于定理 1.3，充当控制屏障函数 (CBF)，保障前向安全不变性；
- **特性**：
  - 最大容许信念散度门限 $D^*_{\max} = 1.5$；
  - 当 $D_{\text{J}} > D^*_{\max}$ 时，判断为认知分歧失控，100% 物理拦截后续动作派发，强制触发跨层级几何投影对齐自愈；
  - 自愈后重新复验散度，确保动作仅在共识达成态下放行。

### 6. `HierarchicalBeliefCoordinator.java`（端到端统筹调度协调中枢）
- **职责**：闭环统筹调度观测摄取 -> 贝叶斯意图推断 -> 跨层级信念对齐 -> 自反博弈求解 -> 安全屏障核验 -> 签发不可变凭单；
- **特性**：
  - 纯 Java 21 高并发安全无死锁执行；
  - 边界完备防御：对空观测、空先验或非法输入抛出清晰明确的异常；
  - 签发不可变 `BeliefAlignmentReceipt`，自签名校验通过率 100%。

---

## 三、验证计划与测试矩阵

在 `backend/tests/src/test/java/tech/qiantong/qknow/ai/belief/Phase59BeliefAlignmentContractTest.java` 中建立 8 项严苛契约单测：
1. `testReceiptIntegrityAndSha256Verification`：不可变存证凭单防篡改与自验完整性测试；
2. `testHierarchicalBayesianIntentInferenceMonotonicConvergence`：多步证据观测下真实意图后验概率单调提升且香农熵衰减测试；
3. `testCrossHierarchicalBeliefJeffreysDivergenceCalculation`：高低层信念对称杰弗里斯散度代数计算精准性测试；
4. `testCrossHierarchicalBeliefGeometricMeanProjection`：几何平均测地投影最优性与无偏归一化测试；
5. `testReflectiveGameEngineCognitiveLevelBoundedExecution`：自反博弈引擎 $k \le 2$ 有界层级求解与防死锁测试；
6. `testBeliefConsensusBarrierBlocksHighDivergence`：高散度认知分歧时安全屏障 100% 物理拦截并驱动对齐自愈测试；
7. `testEndToEndBeliefCoordinationSuccess`：端到端统筹中枢闭环调度成功并签发合规凭据测试；
8. `testCoordinatorRejectsInvalidInputs`：空输入与边界异常合规拦截测试。

全量验证：
- `mvn test -pl tests` 回归验证全库单测（冲刺突破 1114 项 100% 全绿）；
- 前端 `npm run build:prod` 验证生产打包 0 错误通过。
