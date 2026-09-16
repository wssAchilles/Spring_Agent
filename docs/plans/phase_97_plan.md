# Phase 97 实施详案：复杂业务 Agent 跨模态因果意图预测、时序反事实推演沙盘与自主干预决策中枢

## 一、唯一核心待验证假设 (H-PHASE97-001)

本实施方案锁定唯一核心待验证假设 **`H-PHASE97-001`**：
1. **跨模态因果意图预测引擎 (`MultimodalCausalIntentPredictor`)**：基于 Pearl 结构因果模型 (SCM) 与后门准则，单步因果意图推断耗时严格 $\le 60\mu\text{s}$，跨模态虚假混淆消除率 $\ge 98.0\%$，因果意图预测准确率 $\ge 99.0\%$；
2. **时序反事实推演沙盘 (`TemporalCounterfactualSandbox`)**：基于千问 1536 维超球面单位流形 $\mathbb{S}^{1535}$ 进行深度 $H \le 5$ 的 What-If 假设分支树展开，单步推演耗时严格 $\le 100\mu\text{s}$，分支潜在状态保模归一化率 $100.0\%$，预测误差李普希茨有界收敛；
3. **自主干预决策中枢 (`AutonomousInterventionMetacenter`)**：基于相对阶 $r=2$ 离散时序控制屏障函数 (Temporal CBF) 与极速二次规划 (QP) 闭式正交超平面解析投影，单步干预决策耗时严格 $\le 30\mu\text{s}$，高危业务破坏与违规拦截率 $100.0\%$，原有意图推进保留率 $\ge 92.0\%$；
4. **1000Hz 4096 槽位 Disruptor 无锁控制总线 (`CausalInterventionControlBus`)**：非阻塞写入延迟 $\le 50\text{ns}$，JitterGuard 连续 3 帧时钟抖动（>2ms）瞬切 `STATUS_DEGRADED_BUFFERED` 缓冲软着陆，不可变存证凭单 (`CausalInterventionReceipt`) SHA-256 自签名验真通过率 $100.0\%$。

---

## 二、架构拓扑与核心 DTO 及类签名设计

核心代码将落地于 `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/causal/`：

### 2.1 数据传输对象 (DTO)
1. **`dto.CausalGraphNode`** (Java 21 Record):
   - 包含：`String nodeId`, `String nodeName`, `String nodeType` (OBSERVATION, CONFOUNDER, INTENT, ACTION, STATE), `double exogenousVariance`, `Map<String, Object> attributes`。
2. **`dto.CausalEdge`** (Java 21 Record):
   - 包含：`String sourceId`, `String targetId`, `double weight`, `boolean isConfounderEdge`。
3. **`dto.MultimodalCausalState`** (Java 21 Record):
   - 包含：`String sessionId`, `Map<String, String> multimodalInputs`, `float[] qwenEmbedding`, `long timestampNano`。
   - 校验：`qwenEmbedding.length == 1536` 且模长满足 $1.0 \pm 10^{-4}$。
4. **`dto.CounterfactualSandboxBranch`** (Java 21 Record):
   - 包含：`String branchId`, `String proposedAction`, `int depth`, `float[] predictedLatentState`, `double riskScore`, `double expectedUtility`。
5. **`dto.SandboxSimulationResult`** (Java 21 Record):
   - 包含：`String sessionId`, `int totalBranches`, `List<CounterfactualSandboxBranch> branches`, `CounterfactualSandboxBranch optimalBranch`, `long elapsedNanos`。
6. **`dto.AutonomousInterventionAction`** (Enum):
   - 包含：`PASS_DIRECT`, `INTERVENE_SOFT_PROJECT`, `INTERVENE_ALTERNATIVE_BRANCH`, `EMERGENCY_VETO_HALT`。
7. **`dto.AutonomousInterventionResult`** (Java 21 Record):
   - 包含：`AutonomousInterventionAction action`, `boolean intervened`, `String selectedBranchId`, `float[] modifiedActionVector`, `double barrierMargin`, `String rationale`, `long elapsedNanos`。
8. **`dto.CausalInterventionEventFrame`** (Java 21 Record):
   - 包含：`long sequence`, `String sessionId`, `String causalIntent`, `AutonomousInterventionAction action`, `double barrierMargin`, `boolean jitterFlag`, `long timestamp`。
9. **`dto.CausalInterventionReceipt`** (Java 21 Record):
   - 包含：`String receiptId`, `String sessionId`, `String causalGraphHash`, `int totalBranches`, `String finalAction`, `boolean intervened`, `double barrierMargin`, `long elapsedNanos`, `long timestamp`, `String signature`。
   - 方法：`boolean verifyIntegrity()`，基于 SHA-256 验证签名与字段完整性。

### 2.2 核心执行引擎与控制器
1. **`engine.MultimodalCausalIntentPredictor`**：
   - 维护结构因果拓扑图，计算基于 Pearl 后门准则的调节权重；
   - 8 路循环展开计算千问 1536 维超球面测地大圆弧距离；
   - 滤除环境与操作混淆特征，输出核心因果意图与置信度。
2. **`engine.TemporalCounterfactualSandbox`**：
   - 维护状态转移张量算子，对候选假设动作展开有限深度 ($H \le 5$) 前向推演树；
   - 严格在千问 1536 维超球面流形进行超球面归一化投影；
   - 评估未来各分支的风险得分与预期效用。
3. **`engine.AutonomousInterventionMetacenter`**：
   - 维护离散时序控制屏障函数 (Temporal CBF)；
   - 闭式解析二次规划 (QP) 正交超平面最小干预投影；
   - 确定执行动作（直通、软投影修补、安全分支切换、紧急熔断）。
4. **`engine.CausalInterventionControlBus`**：
   - 定长 4096 槽位 Disruptor 环形并发总线；
   - JitterGuard 滑动监控连续 3 帧时钟抖动（>2ms）瞬切缓冲软着陆；
   - 闭环协调全链路并签发不可变密码学存证凭单。

---

## 三、专属契约测试规划 (`Phase97CausalSandboxContractTest.java`)

测试类位置：`backend/tests/src/test/java/tech/qiantong/qknow/hermes/causal/Phase97CausalSandboxContractTest.java`

8 项专属严苛契约测试：
1. `test01_CausalIntentPrediction_AccuracyAndConfounderElimination`：验证因果意图预测准确率 $\ge 99.0\%$ 且混淆变量消除率 $\ge 98.0\%$ (定理 1.1)；
2. `test02_CausalIntentPrediction_MicrosecondPerformance`：单步因果意图推断耗时严格 $\le 60\mu\text{s}$；
3. `test03_TemporalSandbox_HypersphereBranchingAndLipschitzConvergence`：时序沙盘多分支展开保模归一化率 $100.0\%$ 且李普希茨有界收敛 (定理 1.2)；
4. `test04_TemporalSandbox_MicrosecondPerformance`：单步沙盘多分支树展开耗时严格 $\le 100\mu\text{s}$；
5. `test05_AutonomousIntervention_MinimalInterventionAndSafety`：相对阶 $r=2$ Temporal CBF 最小干预软投影修补，高危违规拦截率 $100.0\%$ 且意图保留率 $\ge 92.0\%$ (定理 1.3)；
6. `test06_AutonomousIntervention_NormalActionDirectPass`：正常合规业务动作直通放行，单步干预审查耗时 $\le 30\mu\text{s}$；
7. `test07_DisruptorControlBus_ThroughputAndReceiptIntegrity`：1000Hz 无锁总线非阻塞流转与不可变凭单 SHA-256 自签名验真通过率 $100.0\%$；
8. `test08_DisruptorControlBus_JitterGuardDegradedBuffering`：JitterGuard 连续 3 帧时钟抖动瞬切缓冲软着陆保护。

---

## 四、验证计划与实施纪律

1. **第一回合只读检查与计划审批**：
   - 产出学术研学报告 `phase_97_academic_report.md`、工业对标报告 `phase_97_industrial_report.md`、实施详案 `phase_97_plan.md` 与 `implementation_plan.md`；
   - 等待用户审批，绝不擅自提前编码。
2. **获批后 TDD 实施步骤**：
   - 编写 `Phase97CausalSandboxContractTest.java`；
   - 落地 9 个 DTO 与 4 个核心引擎组件；
   - 运行专属契约测试确保 8/8 项 100% 全绿；
   - 运行全库全量回归确保突破 **1432 项大关**（1432/1432 项 100% 全绿，0 失败 0 错误）；
   - 执行前端生产构建打包校验（`npm --prefix frontend run build:prod` 0 错误通过）；
   - 更新主索引并完成 Git 规范提交。
