package tech.qiantong.qknow.ai.belief;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 59 专属契约测试：多智能体跨层级信念状态对齐、分层贝叶斯意图推断与自反博弈网络
 */
public class Phase59BeliefAlignmentContractTest {

    private HierarchicalBayesianIntentInferer intentInferer;
    private CrossHierarchicalBeliefAligner beliefAligner;
    private ReflectiveGameEngine gameEngine;
    private BeliefConsensusBarrier consensusBarrier;
    private HierarchicalBeliefCoordinator coordinator;

    @BeforeEach
    void setUp() {
        intentInferer = new HierarchicalBayesianIntentInferer();
        beliefAligner = new CrossHierarchicalBeliefAligner();
        gameEngine = new ReflectiveGameEngine();
        consensusBarrier = new BeliefConsensusBarrier();
        coordinator = new HierarchicalBeliefCoordinator(
                intentInferer, beliefAligner, gameEngine, consensusBarrier
        );
    }

    @Test
    @DisplayName("契约 1: 信念对齐不可变存证凭单 SHA-256 自签名与防篡改测试")
    void testReceiptIntegrityAndSha256Verification() {
        BeliefAlignmentReceipt receipt = BeliefAlignmentReceipt.create(
                "REC-BLF-001", "ROUND-001", "AGENT_MACRO_LEADER", "AGENT_MICRO_WORKER",
                "QUERY_EXPLAIN", 0.92, 0.28, 0.45,
                2, "COORDINATED_FETCH", false, true, System.currentTimeMillis()
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "原始凭单 SHA-256 签名必须校验通过");

        // 篡改凭单动作与置信度
        BeliefAlignmentReceipt tampered = new BeliefAlignmentReceipt(
                receipt.receiptId(), receipt.coordinationRoundId(), receipt.macroAgentId(), receipt.microAgentId(),
                receipt.inferredIntent(), 0.99, // 篡改置信度
                receipt.beliefEntropy(), receipt.jeffreysDivergence(),
                receipt.cognitiveLevel(), "UNAUTHORIZED_ACTION", receipt.barrierTriggered(),
                receipt.alignmentSuccessful(), receipt.timestamp(), receipt.sha256Signature()
        );
        assertFalse(tampered.verifySignature(), "被篡改凭据的 SHA-256 签名自验必须失败");
    }

    @Test
    @DisplayName("契约 2: 分层贝叶斯意图推断多步观测后验概率单调提升与熵衰减测试 (定理 1.1)")
    void testHierarchicalBayesianIntentInferenceMonotonicConvergence() {
        float[] queryAnchor = HierarchicalBayesianIntentInferer.generateNormalizedAnchor(1);
        intentInferer.registerIntent("QUERY_EXPLAIN", queryAnchor);

        // 构造与 QUERY_EXPLAIN 语义高度对齐但带轻量扰动的观测序列
        List<float[]> observations = new ArrayList<>();
        Random rnd = new Random(42);
        for (int i = 0; i < 6; i++) {
            float[] obs = new float[1536];
            for (int j = 0; j < 1536; j++) {
                obs[j] = queryAnchor[j] + (float) (rnd.nextGaussian() * 0.05);
            }
            observations.add(obs);
        }

        // 1 步证据推断
        HierarchicalBayesianIntentInferer.InferenceResult r1 =
                intentInferer.inferIntent(observations.subList(0, 1), null);
        // 3 步证据推断
        HierarchicalBayesianIntentInferer.InferenceResult r3 =
                intentInferer.inferIntent(observations.subList(0, 3), null);
        // 6 步证据推断
        HierarchicalBayesianIntentInferer.InferenceResult r6 =
                intentInferer.inferIntent(observations, null);

        // 验证置信度单调提升与熵单调衰减
        assertEquals("QUERY_EXPLAIN", r6.bestIntent(), "最优推断意图应锁定 QUERY_EXPLAIN");
        assertTrue(r6.confidence() > r3.confidence(), "6步后验置信度应高于3步");
        assertTrue(r3.confidence() > r1.confidence(), "3步后验置信度应高于1步");
        assertTrue(r6.confidence() >= 0.85, "多步证据下置信度应突破 85%");

        assertTrue(r6.entropy() < r3.entropy(), "6步信念熵应低于3步");
        assertTrue(r3.entropy() < r1.entropy(), "3步信念熵应低于1步");
        assertTrue(r6.converged(), "多步收敛标志应为 true (熵 <= ln 2 且置信度 >= 85%)");
    }

    @Test
    @DisplayName("契约 3: 跨层级信念状态对称杰弗里斯散度计算精准度与对称性测试")
    void testCrossHierarchicalBeliefJeffreysDivergenceCalculation() {
        Map<String, Double> b1 = Map.of("STATE_A", 0.7, "STATE_B", 0.3);
        Map<String, Double> b2 = Map.of("STATE_A", 0.7, "STATE_B", 0.3);

        CrossHierarchicalBeliefAligner.AlignmentResult rSame = beliefAligner.alignBeliefs(b1, b2);
        assertEquals(0.0, rSame.jeffreysDivergence(), 1e-4, "相同信念分布散度应为 0");
        assertTrue(rSame.aligned(), "散度为 0 判定为对齐达成");

        // 构造有差异的分布
        Map<String, Double> bMacro = Map.of("STATE_A", 0.9, "STATE_B", 0.1);
        Map<String, Double> bMicro = Map.of("STATE_A", 0.2, "STATE_B", 0.8);

        CrossHierarchicalBeliefAligner.AlignmentResult rDiff1 = beliefAligner.alignBeliefs(bMacro, bMicro);
        CrossHierarchicalBeliefAligner.AlignmentResult rDiff2 = beliefAligner.alignBeliefs(bMicro, bMacro);

        assertTrue(rDiff1.jeffreysDivergence() > 0.5, "差异分布散度应显著大于 0");
        assertEquals(rDiff1.jeffreysDivergence(), rDiff2.jeffreysDivergence(), 1e-6,
                "杰弗里斯散度必须具备严格对称性 D_J(P||Q) == D_J(Q||P)");
    }

    @Test
    @DisplayName("契约 4: 跨层级几何平均测地流形投影最优性与无偏归一化测试 (定理 1.3)")
    void testCrossHierarchicalBeliefGeometricMeanProjection() {
        // 对称相反分布
        Map<String, Double> p = Map.of("S1", 0.8, "S2", 0.2);
        Map<String, Double> q = Map.of("S1", 0.2, "S2", 0.8);

        CrossHierarchicalBeliefAligner.AlignmentResult res = beliefAligner.alignBeliefs(p, q);
        Map<String, Double> aligned = res.alignedDistribution();

        // sqrt(0.8 * 0.2) = 0.4 两项相等，归一化后必须均为 0.5
        assertEquals(0.5, aligned.get("S1"), 1e-4, "对称分布对齐后 S1 概率应为 0.5");
        assertEquals(0.5, aligned.get("S2"), 1e-4, "对称分布对齐后 S2 概率应为 0.5");

        double totalSum = aligned.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, totalSum, 1e-6, "对齐后概率分布之和必须严格归一化为 1.0");
    }

    @Test
    @DisplayName("契约 5: 自反博弈引擎 k<=2 有界层级求解与防死锁截断测试 (定理 1.2)")
    void testReflectiveGameEngineCognitiveLevelBoundedExecution() {
        List<String> selfActions = List.of("ACT_COOP", "ACT_DEFECT");
        List<String> oppActions = List.of("ACT_COOP", "ACT_DEFECT");

        // 囚徒困境 / 协作博弈收益矩阵 (self -> (opp -> payoff))
        Map<String, Map<String, Double>> payoff = Map.of(
                "ACT_COOP", Map.of("ACT_COOP", 3.0, "ACT_DEFECT", 0.0),
                "ACT_DEFECT", Map.of("ACT_COOP", 5.0, "ACT_DEFECT", 1.0)
        );

        // Level-0: 基线决策
        ReflectiveGameEngine.GameDecision d0 = gameEngine.solveReflectiveDecision(0, selfActions, oppActions, payoff);
        assertEquals(0, d0.cognitiveLevel());
        assertEquals("ACT_COOP", d0.selectedAction());

        // Level-1: 假设对手等概率，应对最优 (ACT_COOP 均值 1.5, ACT_DEFECT 均值 3.0 -> 选择 ACT_DEFECT)
        ReflectiveGameEngine.GameDecision d1 = gameEngine.solveReflectiveDecision(1, selfActions, oppActions, payoff);
        assertEquals(1, d1.cognitiveLevel());
        assertEquals("ACT_DEFECT", d1.selectedAction());

        // Level-2: 二阶综合博弈
        ReflectiveGameEngine.GameDecision d2 = gameEngine.solveReflectiveDecision(2, selfActions, oppActions, payoff);
        assertEquals(2, d2.cognitiveLevel());
        assertNotNull(d2.selectedAction());

        // 防死锁测试：传入非法深度 10，必须硬截断为 2 (定理 1.2)
        ReflectiveGameEngine.GameDecision dHigh = gameEngine.solveReflectiveDecision(10, selfActions, oppActions, payoff);
        assertEquals(2, dHigh.cognitiveLevel(), "任何深度请求必须截断为 2，杜绝自反无限死锁");
    }

    @Test
    @DisplayName("契约 6: 信念共识安全屏障高散度分歧 100% 物理硬拦截测试 (定理 1.3)")
    void testBeliefConsensusBarrierBlocksHighDivergence() {
        // 安全范围散度
        BeliefConsensusBarrier.BarrierCheckResult safeRes = consensusBarrier.checkBarrier(0.8);
        assertFalse(safeRes.barrierTriggered(), "散度 0.8 <= 1.5 不触发拦截");
        String safeAct = consensusBarrier.filterAction("EXECUTE_PAYMENT", safeRes.barrierTriggered());
        assertEquals("EXECUTE_PAYMENT", safeAct, "安全态下原动作放行");

        // 超标散度 (认知冲突严重)
        BeliefConsensusBarrier.BarrierCheckResult dangerRes = consensusBarrier.checkBarrier(2.1);
        assertTrue(dangerRes.barrierTriggered(), "散度 2.1 > 1.5 必须触发硬拦截");
        String fallbackAct = consensusBarrier.filterAction("EXECUTE_PAYMENT", dangerRes.barrierTriggered());
        assertEquals(BeliefConsensusBarrier.ALIGNMENT_FALLBACK_ACTION, fallbackAct,
                "触发屏障时动作必须强制替换为安全自愈动作 ALIGNMENT_SYNC_FALLBACK");
    }

    @Test
    @DisplayName("契约 7: 端到端跨层级信念协调中枢闭环调度成功测试")
    void testEndToEndBeliefCoordinationSuccess() {
        float[] anchor = HierarchicalBayesianIntentInferer.generateNormalizedAnchor(1);
        List<float[]> obs = List.of(anchor, anchor);

        Map<String, Double> macroB = Map.of("S1", 0.6, "S2", 0.4);
        Map<String, Double> microB = Map.of("S1", 0.55, "S2", 0.45);

        List<String> selfActs = List.of("PLAN_A", "PLAN_B");
        List<String> oppActs = List.of("PLAN_A", "PLAN_B");
        Map<String, Map<String, Double>> payoff = Map.of(
                "PLAN_A", Map.of("PLAN_A", 4.0, "PLAN_B", 1.0),
                "PLAN_B", Map.of("PLAN_A", 1.0, "PLAN_B", 3.0)
        );

        BeliefAlignmentReceipt receipt = coordinator.coordinateBeliefAndAction(
                "ROUND-20260915", "MACRO_SUPERVISOR", "MICRO_EXECUTOR",
                obs, macroB, microB, 2, selfActs, oppActs, payoff
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "端到端生成凭单的 SHA-256 签名自验必须通过");
        assertTrue(receipt.alignmentSuccessful(), "相近信念状态下对齐应宣告成功");
        assertFalse(receipt.barrierTriggered(), "安全屏障不应被触发");
        assertNotNull(receipt.selectedAction(), "必须输出确定性动作");
        assertTrue(receipt.cognitiveLevel() <= 2, "认知博弈层级必须有界 <= 2");
    }

    @Test
    @DisplayName("契约 8: 统筹中枢边界异常防御与空参数拦截测试")
    void testCoordinatorRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> coordinator.coordinateBeliefAndAction(
                null, "MACRO", "MICRO", List.of(new float[1536]), Map.of("S", 1.0), Map.of("S", 1.0),
                1, List.of("A"), List.of("A"), Map.of()
        ), "空 roundId 必须抛出 IllegalArgumentException");

        assertThrows(IllegalArgumentException.class, () -> coordinator.coordinateBeliefAndAction(
                "R1", "MACRO", "MICRO", Collections.emptyList(), Map.of("S", 1.0), Map.of("S", 1.0),
                1, List.of("A"), List.of("A"), Map.of()
        ), "空观测列表必须抛出 IllegalArgumentException");
    }
}
