package tech.qiantong.qknow.ai.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 58 专属契约测试：多智能体自适应强化学习探索策略、离线策略评估 (OPE) 与安全约束更新治理网络
 */
public class Phase58PolicyGovernanceContractTest {

    private AdaptiveExplorationScheduler explorationScheduler;
    private DoublyRobustOpeEvaluator opeEvaluator;
    private ConservativePolicyGovernor cqlGovernor;
    private ConstrainedPolicyOptimizer policyOptimizer;
    private SafePolicyGovernanceCoordinator coordinator;

    @BeforeEach
    void setUp() {
        explorationScheduler = new AdaptiveExplorationScheduler();
        opeEvaluator = new DoublyRobustOpeEvaluator();
        cqlGovernor = new ConservativePolicyGovernor(1.0);
        policyOptimizer = new ConstrainedPolicyOptimizer();
        coordinator = new SafePolicyGovernanceCoordinator(
                explorationScheduler, opeEvaluator, cqlGovernor, policyOptimizer
        );
    }

    @Test
    @DisplayName("契约 1: 离线轨迹存证凭单 SHA-256 自签名与篡改防御测试")
    void testReceiptIntegrityAndSha256Verification() {
        Map<String, Double> multipliers = Map.of(
                ConstrainedPolicyOptimizer.COST_TOKEN_BUDGET, 0.5,
                ConstrainedPolicyOptimizer.COST_LATENCY_MS, 0.0,
                ConstrainedPolicyOptimizer.COST_SECURITY_VIOLATION, 0.0
        );

        OfflineTrajectoryReceipt receipt = OfflineTrajectoryReceipt.create(
                "REC-001", "BATCH-101", "POLICY-V2", "POLICY-V1",
                0.85, 0.82, 0.88, 0.05, 0.80,
                multipliers, false, true, System.currentTimeMillis()
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "原始凭单 SHA-256 签名必须校验成功");

        // 构造篡改凭单
        OfflineTrajectoryReceipt tampered = new OfflineTrajectoryReceipt(
                receipt.receiptId(), receipt.evaluationBatchId(), receipt.targetPolicyId(), receipt.baselinePolicyId(),
                0.99, // 篡改评估收益
                receipt.directMethodValue(), receipt.importanceSamplingValue(),
                receipt.cqlPessimisticPenalty(), receipt.conservativeValue(),
                receipt.lagrangianMultipliers(), receipt.constraintViolated(), receipt.policyAccepted(),
                receipt.timestamp(), receipt.sha256Signature()
        );
        assertFalse(tampered.verifySignature(), "被篡改评估收益的凭单签名校验必须失败");
    }

    @Test
    @DisplayName("契约 2: 自适应探索调度器汤普森采样与退火衰减特性测试")
    void testAdaptiveExplorationSchedulerDecayAndThompsonSampling() {
        explorationScheduler.reset();
        assertEquals(0.10, explorationScheduler.getCurrentExplorationRate(), 1e-4, "初始探索率应为 10%");

        List<String> actions = List.of("ACT_FAST", "ACT_ROBUST", "ACT_DEEP");

        // 模拟多轮反馈，赋予 ACT_ROBUST 极高奖励，赋予 ACT_FAST 极低奖励
        for (int i = 0; i < 50; i++) {
            explorationScheduler.updateFeedback("ACT_ROBUST", 1.0);
            explorationScheduler.updateFeedback("ACT_FAST", 0.0);
        }

        // 模拟 100 次动作选择，由于 ACT_ROBUST 期望远高且探索率退火，ACT_ROBUST 应占主导利用
        int robustCount = 0;
        for (int i = 0; i < 100; i++) {
            String chosen = explorationScheduler.selectAction(actions);
            if ("ACT_ROBUST".equals(chosen)) {
                robustCount++;
            }
        }

        assertTrue(robustCount >= 70, "后验优势动作 ACT_ROBUST 被利用比例应大于等于 70%，当前: " + robustCount);
        assertTrue(explorationScheduler.getCurrentExplorationRate() < 0.10, "经历多步后探索率应当发生退火衰减");
    }

    @Test
    @DisplayName("契约 3: 双重稳健离线策略评估 (DR-OPE) 方差削减达标测试 (>= 70%)")
    void testDoublyRobustOpeUnbiasednessAndVarianceReduction() {
        // 构造具有长尾行为倾向评分的轨迹数据 (模拟重要性权重波动)
        List<DoublyRobustOpeEvaluator.TrajectoryStep> trajectories = new ArrayList<>();
        Random rng = new Random(123);

        for (int i = 0; i < 200; i++) {
            String state = "STATE_" + (i % 5);
            String action = (rng.nextDouble() < 0.5) ? "ACT_A" : "ACT_B";
            // 构造部分长尾低概率行为倾向 (易引发纯 IS 权重爆炸)
            double mu = (i % 20 == 0) ? 0.005 : 0.40;
            double trueReward = "ACT_A".equals(action) ? 0.8 + rng.nextGaussian() * 0.1 : 0.3 + rng.nextGaussian() * 0.1;
            trajectories.add(new DoublyRobustOpeEvaluator.TrajectoryStep(
                    state, action, trueReward, "NEXT_" + state, mu, Map.of()
            ));
        }

        // 目标策略：倾向于动作 ACT_A
        DoublyRobustOpeEvaluator.Policy targetPolicy = new DoublyRobustOpeEvaluator.Policy() {
            @Override
            public double getActionProb(String state, String action) {
                return "ACT_A".equals(action) ? 0.8 : 0.2;
            }

            @Override
            public List<String> getAvailableActions(String state) {
                return List.of("ACT_A", "ACT_B");
            }
        };

        // 基准回归模型
        DoublyRobustOpeEvaluator.BaselineValueModel baselineModel = new DoublyRobustOpeEvaluator.BaselineValueModel() {
            @Override
            public double predictQ(String state, String action) {
                return "ACT_A".equals(action) ? 0.8 : 0.3;
            }

            @Override
            public double predictV(String state) {
                return 0.8 * 0.8 + 0.2 * 0.3; // 0.70
            }
        };

        DoublyRobustOpeEvaluator.OpeEvaluationResult result =
                opeEvaluator.evaluatePolicy(trajectories, targetPolicy, baselineModel);

        assertNotNull(result);
        assertTrue(result.doublyRobustValue() > 0.60, "DR 评估收益应合理反映 ACT_A 优势");
        // 关键断言：DR-OPE 方差相比纯 IS 方差削减 >= 70% (即 varDR < 0.30 * varIS)
        assertTrue(result.doublyRobustVariance() < result.importanceSamplingVariance() * 0.30,
                String.format("DR 方差 (%.4f) 相比纯 IS 方差 (%.4f) 削减必须 >= 70%%",
                        result.doublyRobustVariance(), result.importanceSamplingVariance()));
    }

    @Test
    @DisplayName("契约 4: 保守性价值惩罚治理器 (CQL) 分布外 OOD 悲观惩罚测试")
    void testConservativePolicyGovernorOodPessimisticPenalty() {
        cqlGovernor.clear();
        String state = "STATE_ORDER_DISPATCH";

        // 录入只包含 ACT_NORMAL 的历史轨迹
        List<DoublyRobustOpeEvaluator.TrajectoryStep> trajectories = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            trajectories.add(new DoublyRobustOpeEvaluator.TrajectoryStep(
                    state, "ACT_NORMAL", 1.0, "NEXT_STATE", 1.0, Map.of()
            ));
        }
        cqlGovernor.recordTrajectories(trajectories);

        assertFalse(cqlGovernor.isOodAction(state, "ACT_NORMAL"), "历史出现的动作不是 OOD");
        assertTrue(cqlGovernor.isOodAction(state, "ACT_UNKNOWN_RISKY"), "未出现的未知动作判定为 OOD");

        // 策略 1: 仅包含已知动作
        DoublyRobustOpeEvaluator.Policy knownPolicy = new DoublyRobustOpeEvaluator.Policy() {
            @Override
            public double getActionProb(String s, String a) {
                return "ACT_NORMAL".equals(a) ? 1.0 : 0.0;
            }

            @Override
            public List<String> getAvailableActions(String s) {
                return List.of("ACT_NORMAL");
            }
        };

        // 策略 2: 试图冒进尝试 OOD 动作
        DoublyRobustOpeEvaluator.Policy oodPolicy = new DoublyRobustOpeEvaluator.Policy() {
            @Override
            public double getActionProb(String s, String a) {
                return "ACT_UNKNOWN_RISKY".equals(a) ? 0.9 : 0.1;
            }

            @Override
            public List<String> getAvailableActions(String s) {
                return List.of("ACT_NORMAL", "ACT_UNKNOWN_RISKY");
            }
        };

        double knownPenalty = cqlGovernor.computeCqlPenalty(state, knownPolicy);
        double oodPenalty = cqlGovernor.computeCqlPenalty(state, oodPolicy);

        assertEquals(0.0, knownPenalty, 1e-4, "已知动作分布散度惩罚应为 0");
        assertTrue(oodPenalty >= 5.0, "包含 OOD 动作的策略应被施加高额悲观惩罚: " + oodPenalty);

        double rawGain = 0.95;
        double conservativeValue = cqlGovernor.calibrateConservativeValue(rawGain, oodPenalty);
        assertTrue(conservativeValue < 0.0, "悲观校准后价值应远低于零，阻断冒进选择");
    }

    @Test
    @DisplayName("契约 5: 安全约束策略优化器拉格朗日对偶乘子更新与阻尼截断测试")
    void testConstrainedPolicyOptimizerLagrangianDualUpdate() {
        policyOptimizer.reset();
        Map<String, Double> initialMultipliers = policyOptimizer.getMultipliers();
        assertEquals(0.0, initialMultipliers.get(ConstrainedPolicyOptimizer.COST_TOKEN_BUDGET));
        assertEquals(0.0, initialMultipliers.get(ConstrainedPolicyOptimizer.COST_LATENCY_MS));

        // 注入超标成本: token 2500 (超 2000), 延迟 2000ms (超 1500ms)
        Map<String, Double> violatedCosts = Map.of(
                ConstrainedPolicyOptimizer.COST_TOKEN_BUDGET, 2500.0,
                ConstrainedPolicyOptimizer.COST_LATENCY_MS, 2000.0,
                ConstrainedPolicyOptimizer.COST_SECURITY_VIOLATION, 0.0
        );

        policyOptimizer.updateMultipliers(violatedCosts);
        Map<String, Double> updatedMultipliers = policyOptimizer.getMultipliers();

        assertTrue(updatedMultipliers.get(ConstrainedPolicyOptimizer.COST_TOKEN_BUDGET) > 0.0, "Token 乘子应正向膨胀");
        assertTrue(updatedMultipliers.get(ConstrainedPolicyOptimizer.COST_LATENCY_MS) > 0.0, "延迟乘子应正向膨胀");

        // 模拟极端连续超标，测试 100.0 阻尼截断防死锁
        for (int i = 0; i < 200; i++) {
            policyOptimizer.updateMultipliers(violatedCosts);
        }
        Map<String, Double> clampedMultipliers = policyOptimizer.getMultipliers();
        assertEquals(100.0, clampedMultipliers.get(ConstrainedPolicyOptimizer.COST_TOKEN_BUDGET), 1e-4, "乘子应严格阻尼在 100.0 上限");

        // 注入良好合规成本，验证乘子回落
        Map<String, Double> compliantCosts = Map.of(
                ConstrainedPolicyOptimizer.COST_TOKEN_BUDGET, 1000.0,
                ConstrainedPolicyOptimizer.COST_LATENCY_MS, 1000.0,
                ConstrainedPolicyOptimizer.COST_SECURITY_VIOLATION, 0.0
        );
        policyOptimizer.updateMultipliers(compliantCosts);
        assertTrue(policyOptimizer.getMultipliers().get(ConstrainedPolicyOptimizer.COST_TOKEN_BUDGET) < 100.0, "合规运行后乘子应平滑回落");
    }

    @Test
    @DisplayName("契约 6: 安全控制屏障 (CBF) 对高危违规动作 100% 物理硬拦截测试")
    void testConstrainedPolicyOptimizerBlocksUnsafeActions() {
        policyOptimizer.reset();

        // 场景 1: 合规动作放行
        Map<String, Double> safeCosts = Map.of(
                ConstrainedPolicyOptimizer.COST_SECURITY_VIOLATION, 0.0,
                ConstrainedPolicyOptimizer.COST_TOKEN_BUDGET, 1200.0
        );
        String action1 = policyOptimizer.filterSafeAction("QUERY_DOCS", safeCosts);
        assertEquals("QUERY_DOCS", action1, "安全合规动作应当放行");
        assertTrue(policyOptimizer.isPolicySafe(safeCosts));

        // 场景 2: 敏感安全违规触发 CBF 硬拦截
        Map<String, Double> dangerousCosts = Map.of(
                ConstrainedPolicyOptimizer.COST_SECURITY_VIOLATION, 1.0,
                ConstrainedPolicyOptimizer.COST_TOKEN_BUDGET, 1200.0
        );
        String action2 = policyOptimizer.filterSafeAction("DELETE_DATABASE", dangerousCosts);
        assertEquals(ConstrainedPolicyOptimizer.SAFE_FALLBACK_ACTION, action2, "高危动作必须被 CBF 硬拦截为安全兜底动作");
        assertFalse(policyOptimizer.isPolicySafe(dangerousCosts));
    }

    @Test
    @DisplayName("契约 7: 端到端安全策略治理总控中枢闭环调度成功测试")
    void testEndToEndPolicyGovernanceSuccess() {
        String batchId = "BATCH-20260915-01";
        String targetPolicyId = "POLICY_OPTIMIZED_V2";
        String baselinePolicyId = "POLICY_BASELINE_V1";

        List<DoublyRobustOpeEvaluator.TrajectoryStep> trajectories = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            trajectories.add(new DoublyRobustOpeEvaluator.TrajectoryStep(
                    "STATE_DEFAULT", "ACT_OPTIMIZED", 0.90, "STATE_END", 0.5, Map.of()
            ));
        }

        DoublyRobustOpeEvaluator.Policy targetPolicy = new DoublyRobustOpeEvaluator.Policy() {
            @Override
            public double getActionProb(String s, String a) {
                return "ACT_OPTIMIZED".equals(a) ? 1.0 : 0.0;
            }

            @Override
            public List<String> getAvailableActions(String s) {
                return List.of("ACT_OPTIMIZED");
            }
        };

        DoublyRobustOpeEvaluator.BaselineValueModel baselineModel = new DoublyRobustOpeEvaluator.BaselineValueModel() {
            @Override
            public double predictQ(String s, String a) {
                return 0.80;
            }

            @Override
            public double predictV(String s) {
                return 0.70;
            }
        };

        Map<String, Double> costs = Map.of(
                ConstrainedPolicyOptimizer.COST_TOKEN_BUDGET, 1500.0,
                ConstrainedPolicyOptimizer.COST_LATENCY_MS, 800.0,
                ConstrainedPolicyOptimizer.COST_SECURITY_VIOLATION, 0.0
        );

        OfflineTrajectoryReceipt receipt = coordinator.evaluateAndGovernPolicy(
                batchId, targetPolicyId, baselinePolicyId,
                trajectories, targetPolicy, baselineModel, costs
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "端到端产出的存证凭单签名自验必须通过");
        assertTrue(receipt.policyAccepted(), "收益达标且无违规的策略应当被获批合入");
        assertFalse(receipt.constraintViolated(), "不应产生安全违规");
        assertTrue(receipt.conservativeValue() >= 0.70, "保守评估收益应大于等于基线预期收益");
    }

    @Test
    @DisplayName("契约 8: 协调中枢边界异常防御与零轨迹合规拦截测试")
    void testCoordinatorRejectsEmptyTrajectories() {
        assertThrows(IllegalArgumentException.class, () -> coordinator.evaluateAndGovernPolicy(
                "BATCH-00", "P1", "P0",
                Collections.emptyList(), null, null, Map.of()
        ), "空轨迹列表必须抛出 IllegalArgumentException");

        assertThrows(IllegalArgumentException.class, () -> coordinator.evaluateAndGovernPolicy(
                "BATCH-00", "P1", "P0",
                null, null, null, Map.of()
        ), "null 轨迹数据集必须抛出 IllegalArgumentException");
    }
}
