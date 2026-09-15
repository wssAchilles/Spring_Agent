# Phase 58 架构实施方案：多智能体自适应强化学习探索策略、离线策略评估 (OPE) 与安全约束更新治理网络

## 一、方案背景与唯一可证伪假设

### 1. 业务背景
在多智能体自组织协作与调度演进中，各个业务场景（RAG 检索分流、工具调用链路、反思深度与 Prompt 模版选拔）的决策策略需要随着实际调用轨迹动态学习与调整。然而，由于线上直接探索存在巨大风险（越权、高延迟、API 超额），离线强化学习成为必然选择。现有架构缺乏离线评估的无偏性保证、缺少对分布外 (OOD) 动作的悲观惩罚，并且未将系统 SLA 约束显式纳入策略迭代中。

### 2. 唯一待验证假设 (H-PHASE58-001)
> **假设陈述 (H-PHASE58-001)**：
> 在多智能体离线轨迹日志评估与策略自适应更新治理网络中，通过引入双重稳健估计器 (Doubly Robust OPE) 配合截断重要性权重（$M \le 10.0$）、CQL 悲观价值下界惩罚（$\alpha \cdot D_{\text{CQL}}$）以及拉格朗日乘子对偶投影安全约束，相比无约束普通重要性采样 (IS) 与朴素贪心离线策略，能够将离线策略评估的估计方差降低 $\ge 70\%$，彻底阻断分布外 (OOD) 动作的盲目虚高冒进（OOD 价值高估率降低为 0%），并在策略更新全周期中 100% 保持 Token 预算、SLA 延迟与敏感操作的多维安全硬性约束，端到端单次治理调度耗时 $\le 10\text{ms}$ 并签发自校验通过率 100% 的不可变 SHA-256 存证凭单。

---

## 二、六大核心组件与领域契约设计

所有类落地在 `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/policy/`：

### 1. `OfflineTrajectoryReceipt.java`（不可变存证凭单 Record）
- **职责**：作为策略离线评估与更新治理的密码学可验证收据；
- **字段**：
  - `receiptId` (UUID 唯一编号)
  - `evaluationBatchId` (轨迹评估批次 ID)
  - `targetPolicyId` (待评估/更新目标策略 ID)
  - `baselinePolicyId` (线上行为基准策略 ID)
  - `doublyRobustValue` (DR 估计出的累积期望价值)
  - `directMethodValue` (基准回归模型估计价值)
  - `importanceSamplingValue` (普通重要性采样估计价值)
  - `cqlPessimisticPenalty` (CQL 施加的悲观价值惩罚值)
  - `conservativeValue` (保守校准后的最终有效价值 $V_{\text{DR}} - \text{Penalty}$)
  - `lagrangianMultipliers` (Map<String, Double>，包含 token_budget, latency_sla, permission_rate 等乘子状态)
  - `constraintViolated` (boolean，是否被安全约束阻断)
  - `policyAccepted` (boolean，是否通过安全门禁获批合入)
  - `timestamp` (创建时间戳)
  - `sha256Signature` (包含上述全部字段摘要的自签名 SHA-256 散列)
- **方法**：`verifySignature()` 密码学自验方法。

### 2. `AdaptiveExplorationScheduler.java`（自适应探索调度器）
- **职责**：基于汤普森采样 (Thompson Sampling) 与动态退火衰减，控制多智能体线上动作探索度；
- **特性**：
  - 维持各个策略动作的 Beta/Gaussian 先验参数（成功与惩罚次数）；
  - 动态退火温度衰减：$\epsilon(t) = \max(\epsilon_{\min}, \epsilon_0 \cdot \exp(-\lambda t))$，受控探索上限严格限制在 $\le 10\%$；
  - 保证高置信度动作主导利用，低曝光动作按贝叶斯后验概率被轻量采样。

### 3. `DoublyRobustOpeEvaluator.java`（双重稳健离线策略评估器）
- **职责**：基于定理 1.1，在离线轨迹数据集上计算双重稳健估计量 $\hat{V}_{\text{DR}}(\pi)$；
- **特性**：
  - 支持从轨迹列表中提取状态、动作、即时奖励、下一状态与行为策略倾向得分 $\mu(a \mid s)$；
  - 重要性权重硬截断：$\bar{\rho}(s, a) = \min\left( \frac{\pi(a \mid s)}{\mu(a \mid s)}, 10.0 \right)$，消除方差爆炸；
  - 融合直接模型基准预测 $\hat{Q}(s, a)$，消除单一估计的系统偏差。

### 4. `ConservativePolicyGovernor.java`（保守性价值惩罚治理器）
- **职责**：基于定理 1.2，实施 CQL 悲观价值下界校准，杜绝 OOD 动作虚高；
- **特性**：
  - 统计轨迹数据集中各动作的经验分布频率 $\hat{\pi}_\beta(a \mid s)$；
  - 计算对数配分散度惩罚：$D_{\text{CQL}}(\pi, \hat{\pi}_\beta) = \sum_a \pi(a \mid s) \left( \frac{\pi(a \mid s)}{\hat{\pi}_\beta(a \mid s)} - 1 \right)$；
  - 对 OOD（未在日志中出现过）的动作，自动施加大额惩罚，使其保守评估价值远低于安全基线。

### 5. `ConstrainedPolicyOptimizer.java`（安全约束策略优化器）
- **职责**：基于定理 1.3，对受约束 MDP (CMDP) 实施对偶更新与安全屏障拦截；
- **特性**：
  - 管理多维安全约束配额：`token_cost` (上限 2000), `latency_ms` (上限 1500ms), `security_violation` (上限 0.0)；
  - 动态拉格朗日乘子更新：$\lambda_k^{(t+1)} = \min\left( \lambda_{\max}, \max\left(0, \lambda_k^{(t)} + \eta_\lambda (c_k - d_k)\right) \right)$；
  - 设置阻尼上限 $\lambda_{\max} = 100.0$，防止乘子死锁；
  - 结合离散控制屏障函数 (CBF)，对严重超标动作实施硬拦截并回滚至安全基准。

### 6. `SafePolicyGovernanceCoordinator.java`（端到端策略治理统筹中枢）
- **职责**：闭环统筹调度轨迹输入 -> 自适应探索 -> DR 离线评估 -> CQL 保守校准 -> 安全约束优化 -> 存证凭单签发；
- **特性**：
  - 线程安全并发执行，单次治理判定代数耗时 $\le 10\text{ms}$；
  - 完备防御：当轨迹为空、策略为空或参数非法时抛出清晰异常；
  - 完整输出不可变 `OfflineTrajectoryReceipt`。

---

## 三、验证计划与测试矩阵

在 `backend/tests/src/test/java/tech/qiantong/qknow/ai/policy/Phase58PolicyGovernanceContractTest.java` 中建立 8 项严苛契约单测：
1. `testReceiptIntegrityAndSha256Verification`：凭单防篡改性与自验完整性测试；
2. `testAdaptiveExplorationSchedulerDecayAndThompsonSampling`：自适应探索调度器退火与采样正确性测试；
3. `testDoublyRobustOpeUnbiasednessAndVarianceReduction`：DR-OPE 相对于纯 IS 的方差削减达标测试（$\ge 70\%$）；
4. `testConservativePolicyGovernorOodPessimisticPenalty`：CQL 对 OOD 动作施加悲观惩罚、杜绝虚高冒险测试；
5. `testConstrainedPolicyOptimizerLagrangianDualUpdate`：CMDP 拉格朗日乘子随违规自动膨胀与恢复测试；
6. `testConstrainedPolicyOptimizerBlocksUnsafeActions`：严重违规动作被控制屏障 100% 物理硬拦截测试；
7. `testEndToEndPolicyGovernanceSuccess`：端到端统筹调度器成功评估并获批合入安全增益策略；
8. `testCoordinatorRejectsEmptyTrajectories`：边界异常防御与零轨迹合规拦截测试。

全量验证：
- `mvn test -pl tests` 回归验证全库单测（确保冲刺突破 1106 项 100% 全绿）；
- 前端 `npm run build:prod` 验证生产打包 0 错误通过。
