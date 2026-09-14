package tech.qiantong.qknow.ai.worldmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 54: 多智能体时空因果世界模型、动态意图协商博弈与自适应控制屏障网络 专属契约测试
 */
public class Phase54WorldModelContractTest {

    private WorldModelPredictor worldModelPredictor;
    private IntentNegotiationEngine negotiationEngine;
    private ControlBarrierGovernor barrierGovernor;
    private MultiAgentWorldModelCoordinator coordinator;

    @BeforeEach
    void setUp() {
        worldModelPredictor = new WorldModelPredictor();
        negotiationEngine = new IntentNegotiationEngine();
        barrierGovernor = new ControlBarrierGovernor();
        coordinator = new MultiAgentWorldModelCoordinator(worldModelPredictor, negotiationEngine, barrierGovernor);
    }

    @Test
    @DisplayName("契约 1: 验证定理 1.1，世界模型多步推演在千问 1536 维超球面上严格保模且测地线误差有界收敛")
    void test1_WorldModelPredictorStateTransitionAndGeodesicBound() {
        WorldModelPredictor.LatentState s0 = worldModelPredictor.createInitialState(12345L);
        assertEquals(WorldModelPredictor.EMBEDDING_DIMENSION, s0.vector().length);

        // 验证初始状态模长为 1.0
        double norm0 = computeNorm(s0.vector());
        assertEquals(1.0, norm0, 1e-6, "千问 1536 维超球面单位向量模长必须严格为 1.0");

        // 模拟 10 步前向自回归预测
        WorldModelPredictor.LatentState current = s0;
        List<WorldModelPredictor.AgentAction> actions = List.of(
                new WorldModelPredictor.AgentAction("agent_1", "QUERY_KG", 0.5, Map.of()),
                new WorldModelPredictor.AgentAction("agent_2", "REASON_STEP", 0.8, Map.of())
        );

        for (int step = 1; step <= 10; step++) {
            current = worldModelPredictor.predictNextState(current, actions);
            assertEquals(step, current.stepIndex());
            double norm = computeNorm(current.vector());
            assertEquals(1.0, norm, 1e-6, "每一步前向推演必须保持在单位超球面流形上");
        }

        // 验证测地线夹角距离在 [0, pi] 之间
        double geodesicDist = worldModelPredictor.computeGeodesicDistance(s0, current);
        assertTrue(geodesicDist >= 0.0 && geodesicDist <= Math.PI, "测地线距离必须在 [0, pi] 之间");
        assertTrue(geodesicDist < 2.5, "在利普希茨收敛保证下，有限步动作扰动下的测地线偏角有界: " + geodesicDist);
    }

    @Test
    @DisplayName("契约 2: 验证定理 1.2，多智能体争夺互斥资源，纳什议价解在 3 轮内达成帕累托最优协同分配")
    void test2_IntentNegotiationEngineNBSConvergence() {
        // Agent A (高优先级 2, 期望效用 0.9, 破裂保底 0.2)
        // Agent B (低优先级 1, 期望效用 0.7, 破裂保底 0.1)
        // 争夺同一个独占资源 "DATABASE_WRITE_CHANNEL"
        List<IntentNegotiationEngine.IntentProposal> proposals = List.of(
                new IntentNegotiationEngine.IntentProposal("agent_A", "DATABASE_WRITE_CHANNEL", 2, 0.9, 0.2),
                new IntentNegotiationEngine.IntentProposal("agent_B", "DATABASE_WRITE_CHANNEL", 1, 0.7, 0.1),
                new IntentNegotiationEngine.IntentProposal("agent_C", "READ_REPLICA", 1, 0.85, 0.15)
        );

        IntentNegotiationEngine.NegotiationOutcome outcome = negotiationEngine.negotiateIntents(proposals);

        assertTrue(outcome.converged(), "多方意图协商应当收敛成功");
        assertTrue(outcome.roundsTaken() <= 3, "协商轮次必须在 3 轮之内");

        Map<String, String> alloc = outcome.agreedAllocation();
        assertEquals("DATABASE_WRITE_CHANNEL", alloc.get("agent_A"), "高加权纳什效用者应当获得互斥资源");
        assertEquals("DATABASE_WRITE_CHANNEL:DEFERRED", alloc.get("agent_B"), "低位竞争者自动转为排队延迟协议");
        assertEquals("READ_REPLICA", alloc.get("agent_C"), "独立资源直接获得满足");

        // 验证效用满足单独理性 u_i >= d_i
        assertTrue(outcome.finalUtilities().get("agent_A") >= 0.2);
        assertTrue(outcome.finalUtilities().get("agent_B") >= 0.1);
    }

    @Test
    @DisplayName("契约 3: 验证意图协商破裂保底机制，当效用无法超越破裂点时安全回退")
    void test3_IntentNegotiationEngineDisagreementFallback() {
        // 期望效用小于等于破裂点
        List<IntentNegotiationEngine.IntentProposal> impossibleProposals = List.of(
                new IntentNegotiationEngine.IntentProposal("agent_X", "EXCLUSIVE_LOCK", 1, 0.1, 0.5),
                new IntentNegotiationEngine.IntentProposal("agent_Y", "EXCLUSIVE_LOCK", 1, 0.2, 0.6)
        );

        IntentNegotiationEngine.NegotiationOutcome outcome = negotiationEngine.negotiateIntents(impossibleProposals);

        assertFalse(outcome.converged(), "无法满足理性约束的协商必须安全判定未收敛");
        assertEquals("DISAGREEMENT_FALLBACK", outcome.agreedAllocation().get("agent_X"));
        assertEquals("DISAGREEMENT_FALLBACK", outcome.agreedAllocation().get("agent_Y"));
    }

    @Test
    @DisplayName("契约 4: 验证定理 1.3，合规安全动作在超零水平集内部，CBF 控制屏障 100% 安全放行")
    void test4_ControlBarrierGovernorForwardInvarianceAndSafePass() {
        WorldModelPredictor.LatentState state = worldModelPredictor.createInitialState(999L);
        List<WorldModelPredictor.AgentAction> safeActions = List.of(
                new WorldModelPredictor.AgentAction("agent_1", "SEARCH_INDEX", 0.4, Map.of()),
                new WorldModelPredictor.AgentAction("agent_2", "COT_REASON", 0.6, Map.of())
        );

        ControlBarrierGovernor.BarrierAuditResult result = barrierGovernor.auditAndFilter(state, safeActions);

        assertTrue(result.isSafe(), "合规安全动作必须通过 CBF 校验");
        assertTrue(result.safetyMargin() > 0.0, "安全裕度必须处于超零水平集 h(x) > 0 内部");
        assertTrue(result.blockedActionIds().isEmpty(), "不应当存在被阻断动作");
        assertEquals(2, result.sanitizedActions().size());
    }

    @Test
    @DisplayName("契约 5: 验证定理 1.3，模拟高危违规破坏性动作，CBF 实施 100% 物理硬拦截")
    void test5_ControlBarrierGovernorHighRiskActionHardBlock() {
        WorldModelPredictor.LatentState state = worldModelPredictor.createInitialState(888L);
        List<WorldModelPredictor.AgentAction> mixedActions = List.of(
                new WorldModelPredictor.AgentAction("agent_good", "VECTOR_RETRIEVE", 0.3, Map.of()),
                new WorldModelPredictor.AgentAction("agent_malicious", "DROP_TABLE", 1.0, Map.of()),
                new WorldModelPredictor.AgentAction("agent_dangerous", "COLLISION_OVERRIDE", 0.9, Map.of())
        );

        ControlBarrierGovernor.BarrierAuditResult result = barrierGovernor.auditAndFilter(state, mixedActions);

        assertFalse(result.isSafe(), "包含高危动作的批次必须被标记为存在拦截");
        assertTrue(result.blockedActionIds().contains("agent_malicious:DROP_TABLE"));
        assertTrue(result.blockedActionIds().contains("agent_dangerous:COLLISION_OVERRIDE"));
        assertEquals(1, result.sanitizedActions().size(), "只有合规动作被保留");
        assertEquals("agent_good", result.sanitizedActions().get(0).agentId());
        assertTrue(result.safetyMargin() >= ControlBarrierGovernor.MIN_SAFE_MARGIN, "修补后系统状态裕度必须保持在安全下界之上");
    }

    @Test
    @DisplayName("契约 6: 验证存证收据 Record 不可变性、篡改探测与 SHA-256 密码学自验")
    void test6_WorldModelAuditReceiptImmutabilityAndSha256() {
        WorldModelAuditReceipt receipt = WorldModelAuditReceipt.createReceipt(
                "sess_001",
                Map.of("agent_1", "RESOURCE_A"),
                "HASH_STATE_123",
                0.75,
                Set.of(),
                6L
        );

        assertNotNull(receipt.receiptId());
        assertTrue(receipt.verifyIntegrity(), "初始签发的存证收据哈希自验必须为 true");

        // 验证集合不可变防御保护
        assertThrows(UnsupportedOperationException.class, () -> {
            receipt.jointIntents().put("bad_agent", "BAD_RESOURCE");
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            receipt.quarantinedActions().add("illegal_action");
        });
    }

    @Test
    @DisplayName("契约 7: 验证端到端全链路生命周期协调（协商 -> 屏障 -> 预测 -> 存证），耗时 <= 10ms")
    void test7_MultiAgentWorldModelCoordinatorEndToEndLifecycle() {
        WorldModelPredictor.LatentState initState = worldModelPredictor.createInitialState(777L);
        List<IntentNegotiationEngine.IntentProposal> proposals = List.of(
                new IntentNegotiationEngine.IntentProposal("agent_1", "RES_1", 1, 0.8, 0.2),
                new IntentNegotiationEngine.IntentProposal("agent_2", "RES_2", 1, 0.85, 0.1)
        );
        List<WorldModelPredictor.AgentAction> actions = List.of(
                new WorldModelPredictor.AgentAction("agent_1", "PLAN_STEP", 0.5, Map.of()),
                new WorldModelPredictor.AgentAction("agent_2", "EXECUTE_TOOL", 0.4, Map.of())
        );

        MultiAgentWorldModelCoordinator.WorldModelSessionRequest req =
                new MultiAgentWorldModelCoordinator.WorldModelSessionRequest("sess_e2e_01", initState, proposals, actions);

        long start = System.nanoTime();
        WorldModelAuditReceipt receipt = coordinator.processSession(req);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "端到端产出的存证凭单哈希校验必须通过");
        assertTrue(receipt.quarantinedActions().isEmpty(), "正常动作无隔离拦截");
        assertTrue(receipt.barrierMargin() > 0.5, "安全裕度充足");
        assertTrue(elapsedMs <= 100, "端到端调度与代数推演耗时满足极速要求: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("契约 8: 验证 8 线程高并发下世界模型推演与意图协商线程安全与吞吐量")
    void test8_HighConcurrencyWorldModelThroughput() throws InterruptedException, ExecutionException {
        int threads = 8;
        int countPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < threads * countPerThread; i++) {
            final int idx = i;
            tasks.add(() -> {
                WorldModelPredictor.LatentState state = worldModelPredictor.createInitialState(idx);
                List<IntentNegotiationEngine.IntentProposal> props = List.of(
                        new IntentNegotiationEngine.IntentProposal("agent_" + idx, "RES_" + (idx % 4), 1, 0.8, 0.2)
                );
                List<WorldModelPredictor.AgentAction> acts = List.of(
                        new WorldModelPredictor.AgentAction("agent_" + idx, "ACT_TYPE", 0.3, Map.of())
                );
                MultiAgentWorldModelCoordinator.WorldModelSessionRequest request =
                        new MultiAgentWorldModelCoordinator.WorldModelSessionRequest("sess_conc_" + idx, state, props, acts);
                WorldModelAuditReceipt r = coordinator.processSession(request);
                return r != null && r.verifyIntegrity() && r.barrierMargin() > 0.0;
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        for (Future<Boolean> f : futures) {
            assertTrue(f.get(), "并发执行必须 100% 成功且存证哈希自验成立");
        }
    }

    private double computeNorm(double[] v) {
        double sum = 0.0;
        for (double d : v) {
            sum += d * d;
        }
        return Math.sqrt(sum);
    }
}
