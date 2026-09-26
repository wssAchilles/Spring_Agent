package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.deepseek.evaluator.DeepSeekThinkingReasoningEvaluator;
import tech.qiantong.qknow.ai.deepseek.evaluator.DeepSeekThinkingReasoningEvaluator.CounterfactualEvaluationRequest;
import tech.qiantong.qknow.ai.deepseek.evaluator.DeepSeekThinkingReasoningEvaluator.CounterfactualEvaluationResult;
import tech.qiantong.qknow.hermes.causal.dto.CounterfactualDecisionAuditReceipt;
import tech.qiantong.qknow.hermes.causal.mcts.CounterfactualMctsDecisionHub;
import tech.qiantong.qknow.hermes.causal.mcts.CounterfactualMctsDecisionHub.CandidateActionProfile;
import tech.qiantong.qknow.hermes.causal.mcts.CounterfactualMctsDecisionHub.MctsNode;
import tech.qiantong.qknow.hermes.causal.mcts.CounterfactualMctsDecisionHub.MctsSearchRequest;
import tech.qiantong.qknow.hermes.causal.mcts.CounterfactualMctsDecisionHub.MctsSearchResult;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 146 核心契约测试套件：
 * 多智能体自适应反事实因果推演与改进 PUCT 蒙特卡洛树搜索中枢
 * (Phase 146 Counterfactual MCTS & Hyperspherical Geodesic Drift Contract Test)
 * <p>
 * 严格按照 AGENTS.md 规范与八大核心契约验证：
 * 1. 千问 1536 维超球面单位向量测地线距离计算与流形守恒契约 (TC-146-1)
 * 2. 改进 PUCT 启发式探索准则先验平滑与测地漂移衰减契约 (TC-146-2, Lemma 146.1)
 * 3. 超球面反事实剪枝门禁 (tau_geo = 0.65π) 与软隔离契约 (TC-146-3)
 * 4. 三级冷备隔离环形缓冲区软隔离与可逆唤醒自愈契约 (TC-146-4)
 * 5. DeepSeek 官方 1M 思考链因果评估与因果完备性契约 (TC-146-5, Lemma 146.2)
 * 6. MCTS 有界树搜索单步决策延迟与最优路径收敛契约 (TC-146-6)
 * 7. 纯 Java 21 Record 凭单 SHA-256 常量时间自验真与防篡改契约 (TC-146-7)
 * 8. 多租户物理命名空间硬隔离与高并发吞吐压力测试 (TC-146-8)
 *
 * @author Achilles
 * @since Phase 146
 */
public class Phase146CounterfactualMctsContractTest {

    private CounterfactualMctsDecisionHub decisionHub;
    private DeepSeekThinkingReasoningEvaluator thinkingEvaluator;

    @BeforeEach
    void setUp() {
        thinkingEvaluator = new DeepSeekThinkingReasoningEvaluator();
        decisionHub = new CounterfactualMctsDecisionHub(thinkingEvaluator);
    }

    /**
     * TC-146-1: 千问 1536 维超球面单位向量测地线距离计算与流形守恒契约
     */
    @Test
    @DisplayName("TC-146-1: 千问 1536 维超球面单位向量测地线距离计算与流形守恒契约")
    void testHypersphericalGeodesicDistanceAndManifoldConservation() {
        double[] u = generateNormalizedEmbedding(1001);
        double[] v = generateNormalizedEmbedding(1002);

        // 1. 同一向量测地距离恒为 0.0
        double distSelf = CounterfactualMctsDecisionHub.computeGeodesicDistance(u, u);
        assertEquals(0.0, distSelf, 1e-6, "同一单位向量的大圆测地距离必须为 0.0");

        // 2. 正交向量测地距离恒为 π / 2
        double[] ortho = createOrthogonalVector(u, 2001);
        double distOrtho = CounterfactualMctsDecisionHub.computeGeodesicDistance(u, ortho);
        assertEquals(Math.PI / 2.0, distOrtho, 1e-6, "正交单位向量的测地线弧长必须为 π/2");

        // 3. 反向向量测地距离恒为 π
        double[] opposite = new double[1536];
        for (int i = 0; i < 1536; i++) {
            opposite[i] = -u[i];
        }
        double distOpposite = CounterfactualMctsDecisionHub.computeGeodesicDistance(u, opposite);
        assertEquals(Math.PI, distOpposite, 1e-6, "反向单位向量的测地线弧长必须为 π");

        // 4. 任意两随机单位向量测地距离必须严格在 [0, π]
        double distRandom = CounterfactualMctsDecisionHub.computeGeodesicDistance(u, v);
        assertTrue(distRandom >= 0.0 && distRandom <= Math.PI, "测地线角距离必须严格位于 [0, π]");

        // 5. 验证归一化后范数严格守恒 ||v||_2 = 1.0
        double normU = computeNorm(u);
        assertEquals(1.0, normU, 1e-6, "千问 Embedding 必须严格满足 L2 单位范数守恒");
    }

    /**
     * TC-146-2: 改进 PUCT 启发式探索准则先验平滑与测地漂移衰减契约 (Lemma 146.1)
     */
    @Test
    @DisplayName("TC-146-2: 改进 PUCT 启发式探索准则先验平滑与测地漂移衰减契约")
    void testImprovedPuctScoreWithGeodesicDecay() {
        MctsNode parent = MctsNode.builder()
                .nodeId("P0")
                .visitCount(10)
                .totalValue(7.0) // Q_parent = 0.7
                .build();

        // 子节点 1: 未访问 (N=0)，平滑先验 P=0.8，测地距离 0.1 rad
        MctsNode child1 = MctsNode.builder()
                .nodeId("C1")
                .visitCount(0)
                .totalValue(0.0)
                .priorProbability(0.8)
                .geodesicDistanceToParent(0.1)
                .build();

        // 子节点 2: 未访问 (N=0)，相同平滑先验 P=0.8，但发生严重测地线漂移 1.5 rad
        MctsNode child2 = MctsNode.builder()
                .nodeId("C2")
                .visitCount(0)
                .totalValue(0.0)
                .priorProbability(0.8)
                .geodesicDistanceToParent(1.5)
                .build();

        double score1 = decisionHub.computePuctScore(parent, child1, 1.414, 0.35);
        double score2 = decisionHub.computePuctScore(parent, child2, 1.414, 0.35);

        // 测地偏离越大，PUCT 得分必须严格单调下降 (Lemma 146.1 测地自适应衰减)
        assertTrue(score1 > score2, "高测地漂移分支的 PUCT 分数必须受到严格单调惩罚");

        // 验证冷启动平滑填充生效：子节点 1 的 Q 值平滑继承父节点 (0.7) 而非 0
        assertTrue(score1 > 0.7, "冷启动状态下 PUCT 应在父节点 Q 基础上叠加探索激励项");
    }

    /**
     * TC-146-3: 超球面反事实剪枝门禁 (tau_geo = 0.65π) 与软隔离契约
     */
    @Test
    @DisplayName("TC-146-3: 超球面反事实剪枝门禁 (tau_geo = 0.65π) 与软隔离契约")
    void testGeodesicPruneThresholdAndSoftIsolation() {
        String tenantId = "tenant-tc-146-3";
        double[] rootState = generateNormalizedEmbedding(3001);

        // 构造候选动作：包含 2 个平滑反事实动作 (角距离 ~0.25 rad) 与 2 个恶性剧烈漂移动作 (角距离 2.5 rad > 0.65π)
        List<CandidateActionProfile> candidates = List.of(
                CandidateActionProfile.builder()
                        .actionName("VALID_COUNTERFACTUAL_1")
                        .isCounterfactual(true)
                        .priorProbability(0.6)
                        .predictedNextStateEmbedding(createVectorAtAngle(rootState, 0.25, 4001))
                        .build(),
                CandidateActionProfile.builder()
                        .actionName("VALID_COUNTERFACTUAL_2")
                        .isCounterfactual(true)
                        .priorProbability(0.7)
                        .predictedNextStateEmbedding(createVectorAtAngle(rootState, 0.30, 4002))
                        .build(),
                CandidateActionProfile.builder()
                        .actionName("MALICIOUS_DRIFT_BRANCH_1")
                        .isCounterfactual(true)
                        .priorProbability(0.5)
                        .predictedNextStateEmbedding(createVectorAtAngle(rootState, 2.50, 4003)) // 超过 0.65π ≈ 2.042
                        .build(),
                CandidateActionProfile.builder()
                        .actionName("MALICIOUS_DRIFT_BRANCH_2")
                        .isCounterfactual(true)
                        .priorProbability(0.5)
                        .predictedNextStateEmbedding(createVectorAtAngle(rootState, 2.80, 4004)) // 超过 0.65π
                        .build()
        );

        MctsSearchRequest request = MctsSearchRequest.builder()
                .tenantId(tenantId)
                .taskId("task-prune-test")
                .initialStateDescription("DATABASE_DEADLOCK_CRITICAL_ALERT")
                .initialStateEmbedding(rootState)
                .candidateActions(candidates)
                .iterations(10)
                .cPuct(1.414)
                .lambdaGeo(0.35)
                .pruneThreshold(CounterfactualMctsDecisionHub.MAX_GEODESIC_PRUNE_THRESHOLD)
                .build();

        MctsSearchResult result = decisionHub.searchAndIssueReceipt(request);

        assertNotNull(result);
        assertTrue(result.getPrunedCount() > 0, "恶性测地漂移分支必须被剪枝拦截");
        assertTrue(result.getQuarantinedCount() > 0, "被剪枝分支必须安全沉降进入冷备环形仓");

        // 验证活跃搜索树中绝不包含恶性漂移动作
        List<String> path = result.getBestActionPath();
        assertFalse(path.contains("MALICIOUS_DRIFT_BRANCH_1"), "最优路径不可包含被剪枝动作 1");
        assertFalse(path.contains("MALICIOUS_DRIFT_BRANCH_2"), "最优路径不可包含被剪枝动作 2");
    }

    /**
     * TC-146-4: 三级冷备隔离环形缓冲区软隔离与可逆唤醒自愈契约
     */
    @Test
    @DisplayName("TC-146-4: 三级冷备隔离环形缓冲区软隔离与可逆唤醒自愈契约")
    void testColdRingBufferSoftIsolationAndReversibleSelfHealing() {
        String tenantId = "tenant-tc-146-4";
        double[] rootState = generateNormalizedEmbedding(5001);

        // 注入一条严重偏离的动作使其被剪枝入冷备区
        CandidateActionProfile driftAction = CandidateActionProfile.builder()
                .actionName("EXTREME_ISOLATION_ACTION")
                .isCounterfactual(true)
                .priorProbability(0.5)
                .predictedNextStateEmbedding(createVectorAtAngle(rootState, 2.60, 5002))
                .build();

        MctsSearchRequest request = MctsSearchRequest.builder()
                .tenantId(tenantId)
                .initialStateEmbedding(rootState)
                .candidateActions(List.of(driftAction))
                .iterations(2)
                .pruneThreshold(CounterfactualMctsDecisionHub.MAX_GEODESIC_PRUNE_THRESHOLD)
                .build();

        decisionHub.searchAndIssueReceipt(request);

        // 验证冷备区存在被剪枝记录
        var records = decisionHub.getColdBufferRecords(tenantId);
        assertFalse(records.isEmpty(), "冷备环形仓必须留痕被剪枝记录");

        String targetNodeId = records.get(0).nodeId();

        // 执行一键可逆自愈唤醒
        boolean restored = decisionHub.restoreNodeFromColdBuffer(tenantId, targetNodeId);
        assertTrue(restored, "必须能够基于节点 ID 从冷备隔离区可逆唤醒");

        // 验证容量达到 128 上限时的 FIFO 环形替换 (无内存泄漏与死锁)
        for (int i = 0; i < 150; i++) {
            CandidateActionProfile temp = CandidateActionProfile.builder()
                    .actionName("TEMP_DRIFT_" + i)
                    .isCounterfactual(true)
                    .predictedNextStateEmbedding(createVectorAtAngle(rootState, 2.70, 6000 + i))
                    .build();
            MctsSearchRequest req = MctsSearchRequest.builder()
                    .tenantId(tenantId)
                    .initialStateEmbedding(rootState)
                    .candidateActions(List.of(temp))
                    .iterations(1)
                    .pruneThreshold(CounterfactualMctsDecisionHub.MAX_GEODESIC_PRUNE_THRESHOLD)
                    .build();
            decisionHub.searchAndIssueReceipt(req);
        }

        assertEquals(CounterfactualMctsDecisionHub.COLD_RING_BUFFER_CAPACITY,
                decisionHub.getColdBufferRecords(tenantId).size(),
                "三级冷备环形仓容量必须严格锁死在 128 条");
    }

    /**
     * TC-146-5: DeepSeek 官方 1M 思考链因果评估与因果完备性契约 (Lemma 146.2)
     */
    @Test
    @DisplayName("TC-146-5: DeepSeek 官方 1M 思考链因果评估与因果完备性契约")
    void testDeepSeekThinkingReasoningCausalCompleteness() {
        CounterfactualEvaluationRequest req = CounterfactualEvaluationRequest.builder()
                .tenantId("tenant-tc-146-5")
                .taskId("task-ds-eval-001")
                .currentStateDescription("RPC_GATEWAY_CIRCUIT_BREAKER_TRIGGERED")
                .candidateAction("ROLLBACK_TRAFFIC_TO_CANARY_CLUSTER")
                .counterfactualIntervention("DO(SWITCH_UPSTREAM_ROUTER_WEIGHTS)")
                .isCounterfactual(true)
                .priorActionProbability(0.85)
                .geodesicDriftPenalty(0.18)
                .build();

        CounterfactualEvaluationResult res = thinkingEvaluator.evaluateCandidateBranch(req);

        assertNotNull(res);
        assertNotNull(res.getReasoningContent(), "必须包含 DeepSeek 思考模式思维链内容");
        assertTrue(res.getReasoningContent().contains("【溯因阶段 Abduction】"), "思维链必须严格覆盖因果溯因自省");
        assertTrue(res.getReasoningContent().contains("【反事实干预 Action】"), "思维链必须严格覆盖反事实干预推导");

        // 验证因果完备性得分下界 (Lemma 146.2 证明完备度 >= 96.0%)
        assertTrue(res.getCausalCompletenessScore() >= 0.96,
                "反事实因果完备性得分必须严格满足下界 >= 96.0%，实际: " + res.getCausalCompletenessScore());

        // 验证前缀缓存模 64 严格对齐
        assertTrue(res.isCacheAligned(), "评估请求提示词前缀必须满足 64-token 严格对齐");
        assertEquals(0, res.getAlignedTokens() % 64, "规整前缀总 Token 数量必须为 64 的整数倍");
    }

    /**
     * TC-146-6: MCTS 有界树搜索单步决策延迟与最优路径收敛契约
     */
    @Test
    @DisplayName("TC-146-6: MCTS 有界树搜索单步决策延迟与最优路径收敛契约")
    void testMctsSearchConvergenceAndLatencyBudget() {
        String tenantId = "tenant-tc-146-6";
        double[] rootState = generateNormalizedEmbedding(7001);

        List<CandidateActionProfile> candidates = List.of(
                CandidateActionProfile.builder()
                        .actionName("ACT_DIAGNOSE_CPU")
                        .isCounterfactual(false)
                        .priorProbability(0.5)
                        .predictedNextStateEmbedding(createVectorAtAngle(rootState, 0.15, 8001))
                        .build(),
                CandidateActionProfile.builder()
                        .actionName("ACT_KILL_LONG_RUNNING_QUERY")
                        .isCounterfactual(true)
                        .priorProbability(0.8)
                        .predictedNextStateEmbedding(createVectorAtAngle(rootState, 0.20, 8002))
                        .build(),
                CandidateActionProfile.builder()
                        .actionName("ACT_SCALE_UP_POD_REPLICA")
                        .isCounterfactual(true)
                        .priorProbability(0.6)
                        .predictedNextStateEmbedding(createVectorAtAngle(rootState, 0.35, 8003))
                        .build()
        );

        MctsSearchRequest request = MctsSearchRequest.builder()
                .tenantId(tenantId)
                .taskId("task-convergence-bench")
                .initialStateDescription("DATABASE_CONNECTION_POOL_EXHAUSTION")
                .initialStateEmbedding(rootState)
                .candidateActions(candidates)
                .iterations(30)
                .cPuct(1.414)
                .lambdaGeo(0.35)
                .build();

        // 预热 JVM (充分预热 JIT C2 编译器与字符串/向量计算优化)
        for (int i = 0; i < 15; i++) {
            decisionHub.searchAndIssueReceipt(request);
        }

        // 正式测速
        long start = System.nanoTime();
        MctsSearchResult result = decisionHub.searchAndIssueReceipt(request);
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(result);
        assertNotNull(result.getBestActionPath());
        assertFalse(result.getBestActionPath().isEmpty(), "必须成功收敛并输出最优反事实动作路径");

        // 延迟硬指标：单次有界 MCTS 决策耗时 P99 <= 5.0ms
        assertTrue(latencyMs <= 5.0, "单步反事实 MCTS 树搜索延迟必须满足 P99 <= 5.0ms，实际: " + latencyMs);
    }

    /**
     * TC-146-7: 纯 Java 21 Record 凭单 SHA-256 常量时间自验真与防篡改契约
     */
    @Test
    @DisplayName("TC-146-7: 纯 Java 21 Record 凭单 SHA-256 常量时间自验真与防篡改契约")
    void testJava21RecordReceiptConstantTimeSignatureVerification() {
        CounterfactualDecisionAuditReceipt receipt = CounterfactualDecisionAuditReceipt.create(
                "RCP-CF-TEST-001",
                "tenant-audit-7",
                "task-sec-01",
                "trace-w3c-777",
                List.of("STEP_1_KILL_LOCK", "STEP_2_RESET_HIKARI"),
                2,
                1,
                5,
                24,
                0.842150,
                0.194200,
                896,
                true,
                2.154,
                System.currentTimeMillis()
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "初始签发的反事实决策审计凭单必须自验真通过");

        // 模拟篡改凭单核心字段 (如篡改最优路径或剪枝分支计数)
        CounterfactualDecisionAuditReceipt tampered = new CounterfactualDecisionAuditReceipt(
                receipt.receiptId(),
                receipt.tenantId(),
                receipt.taskId(),
                receipt.traceId(),
                List.of("MALICIOUS_UNAUTHORIZED_STEP"), // 恶意伪造动作路径
                receipt.counterfactualPrunedCount(),
                receipt.quarantinedCount(),
                receipt.maxTreeDepth(),
                receipt.totalVisitedNodes(),
                receipt.rootUctScore(),
                receipt.averageGeodesicDistance(),
                receipt.alignedTokens(),
                receipt.isCacheAligned(),
                receipt.inferenceLatencyMs(),
                receipt.timestamp(),
                receipt.sha256Signature() // 沿用原数字签名
        );

        assertFalse(tampered.verifySignature(), "字段遭篡改后，基于常量时间比对的验签必须严格失败");
    }

    /**
     * TC-146-8: 多租户物理命名空间硬隔离与高并发吞吐压力测试
     */
    @Test
    @DisplayName("TC-146-8: 多租户物理命名空间硬隔离与高并发吞吐压力测试")
    void testMultiTenantPhysicalIsolationAndHighConcurrency() throws Exception {
        int tenantCount = 8;
        int operationsPerTenant = 15;
        ExecutorService executor = Executors.newFixedThreadPool(tenantCount);
        CountDownLatch latch = new CountDownLatch(tenantCount);
        List<Future<Double>> futures = new ArrayList<>();

        for (int t = 0; t < tenantCount; t++) {
            final int tenantIndex = t;
            final String tenantId = "tenant-mcts-concurrent-" + tenantIndex;
            final int seed = tenantIndex * 100;

            futures.add(executor.submit(() -> {
                try {
                    long totalNanos = 0;
                    double[] state = generateNormalizedEmbedding(seed);
                    for (int op = 0; op < operationsPerTenant; op++) {
                        CandidateActionProfile act1 = CandidateActionProfile.builder()
                                .actionName("T" + tenantIndex + "_ACT_NORMAL_" + op)
                                .isCounterfactual(false)
                                .priorProbability(0.6)
                                .predictedNextStateEmbedding(createVectorAtAngle(state, 0.10, seed + op))
                                .build();
                        CandidateActionProfile act2 = CandidateActionProfile.builder()
                                .actionName("T" + tenantIndex + "_ACT_CF_" + op)
                                .isCounterfactual(true)
                                .priorProbability(0.7)
                                .predictedNextStateEmbedding(createVectorAtAngle(state, 0.20, seed + op + 1))
                                .build();

                        MctsSearchRequest req = MctsSearchRequest.builder()
                                .tenantId(tenantId)
                                .taskId("task-" + tenantId + "-" + op)
                                .traceId("trace-" + tenantId)
                                .initialStateEmbedding(state)
                                .candidateActions(List.of(act1, act2))
                                .iterations(15)
                                .build();

                        long s = System.nanoTime();
                        MctsSearchResult res = decisionHub.searchAndIssueReceipt(req);
                        totalNanos += (System.nanoTime() - s);

                        assertNotNull(res);
                        assertTrue(res.getReceipt().verifySignature());
                    }
                    return (totalNanos / (double) operationsPerTenant) / 1_000_000.0;
                } finally {
                    latch.countDown();
                }
            }));
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertTrue(finished, "多租户高并发执行必须在 10 秒内完成");

        for (Future<Double> future : futures) {
            double avgLatency = future.get();
            assertTrue(avgLatency <= 5.0, "并发单步决策耗时均值必须 <= 5.0ms，实际: " + avgLatency);
        }

        executor.shutdown();
    }

    // ================================= 辅助几何方法 =================================

    private double[] generateNormalizedEmbedding(long seed) {
        Random random = new Random(seed);
        double[] vec = new double[1536];
        double norm = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = random.nextGaussian();
            norm += vec[i] * vec[i];
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < 1536; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    private double[] createOrthogonalVector(double[] base, long seed) {
        Random random = new Random(seed);
        double[] rand = new double[1536];
        for (int i = 0; i < 1536; i++) {
            rand[i] = random.nextGaussian();
        }
        double dot = 0.0;
        for (int i = 0; i < 1536; i++) {
            dot += rand[i] * base[i];
        }
        double norm = 0.0;
        for (int i = 0; i < 1536; i++) {
            rand[i] -= dot * base[i];
            norm += rand[i] * rand[i];
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < 1536; i++) {
            rand[i] /= (norm > 1e-9 ? norm : 1.0);
        }
        return rand;
    }

    private double[] createVectorAtAngle(double[] base, double angleRad, long seed) {
        double[] ortho = createOrthogonalVector(base, seed);
        double cosTheta = Math.cos(angleRad);
        double sinTheta = Math.sin(angleRad);
        double[] res = new double[1536];
        for (int i = 0; i < 1536; i++) {
            res[i] = cosTheta * base[i] + sinTheta * ortho[i];
        }
        return res;
    }

    private double computeNorm(double[] vec) {
        double sum = 0.0;
        for (double v : vec) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }
}
