package tech.qiantong.qknow.ai.twin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 50: 全局多模态智能体数字孪生自省中枢、形式化反事实推演沙盘与自主策略自愈进化闭环 专属契约测试
 */
public class Phase50AgentDigitalTwinContractTest {

    private CausalInterventionOperator interventionOperator;
    private CounterfactualSimulationSandbox simulationSandbox;
    private MetacognitiveMonitor metacognitiveMonitor;
    private AgentDigitalTwinMetacenter metacenter;
    private AutonomicPolicyEvolutionEngine evolutionEngine;

    @BeforeEach
    void setUp() {
        interventionOperator = new CausalInterventionOperator();
        simulationSandbox = new CounterfactualSimulationSandbox(interventionOperator);
        metacognitiveMonitor = new MetacognitiveMonitor();
        metacenter = new AgentDigitalTwinMetacenter(simulationSandbox, metacognitiveMonitor);
        evolutionEngine = new AutonomicPolicyEvolutionEngine(simulationSandbox, metacenter);
    }

    @Test
    @DisplayName("契约 1: 验证数字孪生不可变快照与写时复制(COW)结构共享与物理强隔离")
    void test1_DigitalTwinSnapshotImmutabilityAndCOW() {
        Map<String, DigitalTwinSnapshot.AgentTwinState> agents = new HashMap<>();
        agents.put("agent_1", new DigitalTwinSnapshot.AgentTwinState("agent_1", "WORKER", "ONLINE", 0.95, 0.2, Map.of()));
        agents.put("agent_2", new DigitalTwinSnapshot.AgentTwinState("agent_2", "CRITIC", "ONLINE", 0.98, 0.1, Map.of()));

        DigitalTwinSnapshot original = DigitalTwinSnapshot.initialSnapshot(agents, Map.of("cpu", 0.3));
        String originalHash = original.snapshotHash();
        long originalVersion = original.snapshotVersion();

        // 产生推演分支快照
        DigitalTwinSnapshot branch = original.withIntervention("agent_1", "BUSY_INTERVENED", Map.of("riskFlag", true));

        // 校验原始快照完全不受任何修改 (物理状态强隔离不变量)
        assertEquals(originalVersion, original.snapshotVersion());
        assertEquals(originalHash, original.snapshotHash());
        assertEquals("ONLINE", original.getAgent("agent_1").orElseThrow().status());

        // 校验分支快照正常更新并产生新指纹
        assertEquals(originalVersion + 1, branch.snapshotVersion());
        assertNotEquals(originalHash, branch.snapshotHash());
        assertEquals("BUSY_INTERVENED", branch.getAgent("agent_1").orElseThrow().status());
        assertEquals(true, branch.getAgent("agent_1").orElseThrow().attributes().get("riskFlag"));

        // 结构共享校验：未被修改的 agent_2 在两个快照间保持状态一致
        assertEquals(original.getAgent("agent_2").orElseThrow().role(), branch.getAgent("agent_2").orElseThrow().role());
    }

    @Test
    @DisplayName("契约 2: 验证 Pearl do-演算三阶段算子(Abduction-Action-Prediction)因果完整性")
    void test2_CausalInterventionOperatorDoCalculus() {
        CausalInterventionOperator.CausalEvidence evidence = new CausalInterventionOperator.CausalEvidence(
                "EVT_101", "agent_worker", "HIGH_LATENCY", 3500.0, Map.of("networkJitter", "SEVERE")
        );

        // 阶段 1: 溯因 (Abduction)
        Map<String, Object> exogenous = interventionOperator.abductExogenousContext(evidence);
        assertNotNull(exogenous);
        assertEquals(0.8, (Double) exogenous.get("noiseLevel"), 0.01);

        // 阶段 2: 干预 (Action)
        DigitalTwinSnapshot base = metacenter.getActiveSnapshot();
        CausalInterventionOperator.IntervenedAction action = new CausalInterventionOperator.IntervenedAction(
                "ACT_001", "agent_coordinator", "SWITCH_FALLBACK_MODEL", Map.of("timeoutMs", 500), false
        );
        DigitalTwinSnapshot virtualSnapshot = interventionOperator.applyIntervention(base, action, exogenous);
        assertNotNull(virtualSnapshot);
        assertTrue(virtualSnapshot.getAgent("agent_coordinator").orElseThrow().status().contains("SWITCH_FALLBACK_MODEL"));

        // 阶段 3: 预测 (Prediction)
        CausalInterventionOperator.CounterfactualPrediction prediction = interventionOperator.predictOutcome(virtualSnapshot, action, 5);
        assertNotNull(prediction);
        assertTrue(prediction.approved());
        assertTrue(prediction.projectedUtility() >= 0.80);
        assertTrue(prediction.projectedRisk() <= 0.30);
    }

    @Test
    @DisplayName("契约 3: 验证反事实推演沙盘对高危越界操作的阻断性与物理零破坏")
    void test3_CounterfactualSandboxHighRiskActionBlocking() {
        DigitalTwinSnapshot base = metacenter.getActiveSnapshot();
        String originHash = base.snapshotHash();

        // 构造高危破坏性动作：未经授权删除核心数据表
        CausalInterventionOperator.IntervenedAction dangerousAction = new CausalInterventionOperator.IntervenedAction(
                "ACT_MALICIOUS",
                "agent_coordinator",
                "DROP_TABLE_KNOWLEDGE_DOCUMENTS",
                Map.of("sql", "DROP TABLE qknow_documents;"),
                true
        );

        CounterfactualSimulationSandbox.SimulationResult result = simulationSandbox.simulate(base, dangerousAction, 10);

        // 验证沙盘给出的严重阻断判决
        assertEquals(CounterfactualSimulationSandbox.SafetyVerdict.CRITICAL_BLOCKED, result.verdict());
        assertTrue(result.riskScore() >= 0.80);
        assertTrue(result.utilityScore() <= 0.20);

        // 关键不变量断言：物理生产快照指纹未被修改
        assertEquals(originHash, base.snapshotHash(), "物理状态指纹必须完全恒等，零状态渗漏");
    }

    @Test
    @DisplayName("契约 4: 验证反事实推演沙盘对合规安全操作的纳秒级放行与高效推演")
    void test4_CounterfactualSandboxSafeActionApproval() {
        DigitalTwinSnapshot base = metacenter.getActiveSnapshot();

        CausalInterventionOperator.IntervenedAction safeAction = new CausalInterventionOperator.IntervenedAction(
                "ACT_SAFE",
                "agent_retriever",
                "REFRESH_EMBEDDING_CACHE",
                Map.of("ttl", 3600),
                false
        );

        CounterfactualSimulationSandbox.SimulationResult result = simulationSandbox.simulate(base, safeAction, 10);

        assertEquals(CounterfactualSimulationSandbox.SafetyVerdict.SAFE, result.verdict());
        assertTrue(result.riskScore() <= 0.25);
        assertTrue(result.utilityScore() >= 0.80);
        // 推演总耗时满足毫秒级预算
        assertTrue(result.executionTimeMs() <= 20, "沙盘推演耗时应在 20ms 以内");
    }

    @Test
    @DisplayName("契约 5: 验证李雅普诺夫指数同步动力学跟踪收敛性与保真度 >= 99%")
    void test5_LyapunovStateSynchronizationConvergence() {
        // 模拟物理环境状态发生微小变更
        metacenter.registerOrUpdateAgent("agent_retriever", "RETRIEVER", "BUSY", 0.99, 0.65);

        // 触发李雅普诺夫同步观测更新
        metacenter.syncLyapunovObserver(50);

        // 验证保真度指标
        double fidelity = metacenter.getSyncFidelity();
        double trackingError = metacenter.getTrackingErrorNorm();

        assertTrue(fidelity >= 0.99, "数字孪生保真度必须保持在 99% 以上, actual: " + fidelity);
        assertTrue(trackingError <= 0.01, "跟踪误差模长 ||e(t)|| 必须小于等于 0.01, actual: " + trackingError);

        // 验证更新后的快照包含最新智能体状态
        DigitalTwinSnapshot active = metacenter.getActiveSnapshot();
        assertEquals("BUSY", active.getAgent("agent_retriever").orElseThrow().status());
    }

    @Test
    @DisplayName("契约 6: 验证旁路无锁监控器的高并发事件遥测吞吐量与振荡检测")
    void test6_MetacognitiveMonitorLockFreeTelemetry() throws InterruptedException {
        int threadCount = 8;
        int eventsPerThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < eventsPerThread; i++) {
                        metacognitiveMonitor.recordAgentEvent(new MetacognitiveMonitor.PhysicalAgentEvent(
                                "EVT_" + threadId + "_" + i,
                                "agent_" + (threadId % 3),
                                "A2A_CALL",
                                15.0 + (i % 5),
                                false,
                                Map.of(),
                                System.currentTimeMillis()
                        ));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        // 验证吞吐量与总摄取数
        assertEquals(threadCount * eventsPerThread, metacognitiveMonitor.getTotalIngestedEvents());

        // 执行批量排空与指标更新
        int drained = metacognitiveMonitor.drainAndProcessEvents();
        assertEquals(threadCount * eventsPerThread, drained);
        assertTrue(metacognitiveMonitor.getSystemHealthScore() >= 0.90);
        assertFalse(metacognitiveMonitor.isOscillationDetected());
    }

    @Test
    @DisplayName("契约 7: 验证自主策略自愈进化闭环的单调提升与热装载 (Delta J >= 0)")
    void test7_AutonomicPolicyEvolutionMonotonicImprovement() {
        // 提出有益策略补丁：估计收益增益 Delta J = +0.20
        AutonomicPolicyEvolutionEngine.PolicyPatch goodPatch = evolutionEngine.proposeSelfHealingPatch(
                "agent_retriever",
                "HIGH_CONCURRENCY",
                "DeepSeek-R1 causal analysis indicates caching is sufficient, reducing timeout window",
                0.85,
                0.20
        );

        AutonomicPolicyEvolutionEngine.EvolutionResult result = evolutionEngine.evaluateAndApplyPatch(goodPatch);

        assertTrue(result.accepted());
        assertEquals("ACCEPTED_MONOTONIC_IMPROVEMENT", result.statusMessage());
        assertTrue(result.deltaJ() > 0);

        // 验证策略已热加载到引擎
        var activePolicy = evolutionEngine.getActivePolicy("agent_retriever");
        assertTrue(activePolicy.isPresent());
        assertEquals(0.85, activePolicy.get().threshold());
    }

    @Test
    @DisplayName("契约 8: 验证自主策略自愈对性能退化补丁(Delta J < 0)的单调性拒绝与冷却窗口保护")
    void test8_AutonomicPolicyEvolutionNegativePatchRejection() {
        // 提出导致性能退化的劣质补丁：Delta J = -0.35
        AutonomicPolicyEvolutionEngine.PolicyPatch badPatch = evolutionEngine.proposeSelfHealingPatch(
                "agent_retriever",
                "TRANSIENT_TIMEOUT",
                "Incorrect reasoning causing aggressive throttling",
                0.10,
                -0.35
        );

        AutonomicPolicyEvolutionEngine.EvolutionResult result = evolutionEngine.evaluateAndApplyPatch(badPatch);

        // 验证 Kakade-Langford 门禁严厉拒绝
        assertFalse(result.accepted());
        assertTrue(result.statusMessage().contains("REJECTED_MONOTONIC_DEGRADATION"));

        // 验证冷却时间窗口保护：短时间内连续提交策略被限流
        AutonomicPolicyEvolutionEngine.PolicyPatch rapidPatch = evolutionEngine.proposeSelfHealingPatch(
                "agent_retriever",
                "RAPID_FLAPPING",
                "Immediate second patch attempt",
                0.90,
                0.05
        );
        // 先成功合入一个
        evolutionEngine.evaluateAndApplyPatch(new AutonomicPolicyEvolutionEngine.PolicyPatch(
                "P_COOL", "agent_retriever", rapidPatch.candidateRule(), "Reason", 0.05
        ));
        // 紧接着再次合入同一 Agent 的补丁
        var throttledResult = evolutionEngine.evaluateAndApplyPatch(rapidPatch);
        assertFalse(throttledResult.accepted());
        assertTrue(throttledResult.statusMessage().contains("REJECTED_COOLDOWN_ACTIVE"));
    }
}
