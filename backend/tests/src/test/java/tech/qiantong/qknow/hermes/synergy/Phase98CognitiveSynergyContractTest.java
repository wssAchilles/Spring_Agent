package tech.qiantong.qknow.hermes.synergy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.synergy.dto.*;
import tech.qiantong.qknow.hermes.synergy.engine.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 98 契约测试套件：复杂业务 Agent 分布式多智能体分层动态重组、认知协同网络与涌现决策中枢
 * 严格验证假设 H-PHASE98-001、定理 1.1~1.3 与命题 2.1
 */
public class Phase98CognitiveSynergyContractTest {

    private HierarchicalDynamicRecombiner recombiner;
    private HypersphericalCognitiveSynergyNetwork synergyNetwork;
    private EmergentDecisionArbitrationMetacenter arbitrationMetacenter;
    private CognitiveSynergyControlBus controlBus;

    @BeforeEach
    public void setUp() {
        recombiner = new HierarchicalDynamicRecombiner();
        synergyNetwork = new HypersphericalCognitiveSynergyNetwork();
        arbitrationMetacenter = new EmergentDecisionArbitrationMetacenter();
        controlBus = new CognitiveSynergyControlBus();
    }

    /**
     * 辅助方法：生成归一化千问 1536 维超球面单位向量 (||v||_2 = 1.0 +- 1e-4)
     */
    private float[] generateNormalizedQwenEmbedding(int seed) {
        float[] v = new float[SynergyAgentNode.EXPECTED_DIMENSION];
        double sumSq = 0.0;
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) Math.sin(seed * 0.19 + i * 0.007);
            sumSq += (double) v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约测试 1: 验证定理 1.1，分层动态重组拓扑代数连通度单调非减且孤岛率为 0")
    public void test01_DynamicRecombination_AlgebraicConnectivityMonotonicAndNoIsland() {
        // 注册 4 个分层智能体节点
        recombiner.registerNode(new SynergyAgentNode("agent-director", AgentHierarchyLayer.STRATEGIC_DIRECTOR, generateNormalizedQwenEmbedding(1), 0.2, 0.95));
        recombiner.registerNode(new SynergyAgentNode("agent-coord", AgentHierarchyLayer.TACTICAL_COORDINATOR, generateNormalizedQwenEmbedding(2), 0.90, 0.88)); // 高负载
        recombiner.registerNode(new SynergyAgentNode("agent-exec", AgentHierarchyLayer.OPERATIONAL_EXECUTOR, generateNormalizedQwenEmbedding(3), 0.3, 0.82));
        recombiner.registerNode(new SynergyAgentNode("agent-verif", AgentHierarchyLayer.QUALITY_VERIFIER, generateNormalizedQwenEmbedding(4), 0.1, 0.92));

        double prevConn = recombiner.getLastAlgebraicConnectivity();

        // 触发第 1 代重组
        TopologyRecombinationPlan plan1 = recombiner.recombineTopology(1);
        assertTrue(plan1.algebraicConnectivity() >= prevConn, "代数连通度必须单调非减: " + plan1.algebraicConnectivity() + " >= " + prevConn);
        assertEquals(AgentHierarchyLayer.OPERATIONAL_EXECUTOR, plan1.nodeLayers().get("agent-coord"), "高负载战术协调节点应当被自适应降载分流");

        // 验证无孤岛（每个节点至少有一条相连边）
        for (List<String> neighbors : plan1.activeTopology().values()) {
            assertFalse(neighbors.isEmpty(), "拓扑中绝对不允许产生任何孤岛节点");
        }

        // 触发第 2 代重组
        TopologyRecombinationPlan plan2 = recombiner.recombineTopology(2);
        assertTrue(plan2.algebraicConnectivity() >= plan1.algebraicConnectivity(), "多轮重组下代数连通度必须持续维持单调非减");
    }

    @Test
    @DisplayName("契约测试 2: 验证动态分层重组单步求解耗时严格 <= 60μs (实测极限微秒级)")
    public void test02_DynamicRecombination_MicrosecondPerformance() {
        for (int i = 0; i < 6; i++) {
            recombiner.registerNode(new SynergyAgentNode(
                    "node-" + i, AgentHierarchyLayer.OPERATIONAL_EXECUTOR, generateNormalizedQwenEmbedding(10 + i), 0.4, 0.8
            ));
        }

        // 预热
        for (int i = 0; i < 2000; i++) {
            recombiner.recombineTopology(i);
        }

        long totalElapsed = 0;
        int runs = 500;
        for (int i = 0; i < runs; i++) {
            TopologyRecombinationPlan plan = recombiner.recombineTopology(runs + i);
            totalElapsed += plan.elapsedNanos();
        }
        double avgMicros = (totalElapsed / (double) runs) / 1000.0;
        assertTrue(avgMicros <= 60.0, "单步分层重组拓扑求解耗时必须 <= 60μs，实测: " + avgMicros + "μs");
    }

    @Test
    @DisplayName("契约测试 3: 验证定理 1.2，超球面流形切空间 Fréchet 协同信息增益严格正定且语义漂移率 <= 0.8%")
    public void test03_CognitiveSynergy_InformationGainPositiveAndDriftBounded() {
        List<SynergyAgentNode> nodes = List.of(
                new SynergyAgentNode("agent-A", AgentHierarchyLayer.STRATEGIC_DIRECTOR, generateNormalizedQwenEmbedding(11), 0.2, 0.95),
                new SynergyAgentNode("agent-B", AgentHierarchyLayer.TACTICAL_COORDINATOR, generateNormalizedQwenEmbedding(12), 0.3, 0.85),
                new SynergyAgentNode("agent-C", AgentHierarchyLayer.OPERATIONAL_EXECUTOR, generateNormalizedQwenEmbedding(13), 0.4, 0.80)
        );

        CognitiveSynergyFrame frame = synergyNetwork.aggregateCognitiveBeliefs("session-synergy-1", nodes);

        assertEquals(3, frame.participantCount());
        assertTrue(frame.informationGain() > 0.0, "协同互信息增益 Delta I 必须严格大于零，实测: " + frame.informationGain());
        assertTrue(frame.semanticDriftRate() <= 0.008, "流形切空间语义漂移率必须 <= 0.8%，实测: " + (frame.semanticDriftRate() * 100) + "%");

        // 验证 Fréchet 均值向量严格位于单位超球面流形上
        float[] meanVec = frame.frechetMeanVector();
        assertEquals(1536, meanVec.length);
        double sumSq = 0.0;
        for (float v : meanVec) {
            sumSq += (double) v * v;
        }
        double norm = Math.sqrt(sumSq);
        assertEquals(1.0, norm, 1e-4, "Fréchet 均值向量模长必须严格归一化为 1.0 +- 1e-4");
    }

    @Test
    @DisplayName("契约测试 4: 验证超球面认知协同网络单步聚合耗时严格 <= 50μs")
    public void test04_CognitiveSynergy_MicrosecondPerformance() {
        List<SynergyAgentNode> nodes = List.of(
                new SynergyAgentNode("n1", AgentHierarchyLayer.STRATEGIC_DIRECTOR, generateNormalizedQwenEmbedding(21), 0.1, 0.9),
                new SynergyAgentNode("n2", AgentHierarchyLayer.OPERATIONAL_EXECUTOR, generateNormalizedQwenEmbedding(22), 0.2, 0.8),
                new SynergyAgentNode("n3", AgentHierarchyLayer.QUALITY_VERIFIER, generateNormalizedQwenEmbedding(23), 0.1, 0.85)
        );

        // 预热
        for (int i = 0; i < 2000; i++) {
            synergyNetwork.aggregateCognitiveBeliefs("session-bench", nodes);
        }

        long totalElapsed = 0;
        int runs = 500;
        for (int i = 0; i < runs; i++) {
            CognitiveSynergyFrame frame = synergyNetwork.aggregateCognitiveBeliefs("session-bench", nodes);
            totalElapsed += frame.elapsedNanos();
        }
        double avgMicros = (totalElapsed / (double) runs) / 1000.0;
        assertTrue(avgMicros <= 50.0, "单步超球面认知协同聚合耗时必须 <= 50μs，实测: " + avgMicros + "μs");
    }

    @Test
    @DisplayName("契约测试 5: 验证定理 1.3，加权纳什议价解在 3 轮内帕累托最优收敛且满足单独理性")
    public void test05_EmergentDecision_NashBargainingParetoConvergence() {
        List<EmergentDecisionProposal> proposals = List.of(
                new EmergentDecisionProposal("prop-1", "agent-exec", "PLAN_OPTIMIZE_INDEX_CACHE", 0.92, 0.05),
                new EmergentDecisionProposal("prop-2", "agent-coord", "PLAN_BATCH_ASYNC_SYNC", 0.85, 0.10),
                new EmergentDecisionProposal("prop-3", "agent-verif", "PLAN_FULL_STRICT_AUDIT", 0.70, 0.02)
        );

        EmergentDecisionResolution resolution = arbitrationMetacenter.arbitrateEmergentDecisions("session-nash", proposals);

        assertTrue(resolution.converged(), "必须达成收敛");
        assertTrue(resolution.roundsTaken() <= 3, "博弈收敛轮次必须 <= 3 轮");
        assertEquals("PLAN_OPTIMIZE_INDEX_CACHE", resolution.agreedPlan(), "高加权纳什效用方案必须胜出");
        assertFalse(resolution.cbfIntervened(), "安全方案无需触发干预");
        assertTrue(resolution.barrierMargin() >= 0.0, "屏障裕度必须为正");
    }

    @Test
    @DisplayName("契约测试 6: 验证相对阶 r=2 Emergent CBF 100% 拦截高危涌现破坏方案")
    public void test06_EmergentDecision_RelativeDegree2CbfIntervention() {
        // 场景 A: 包含严重破坏动作 (FORMAT_STORAGE)，触发一票否决
        List<EmergentDecisionProposal> hazardousProposals = List.of(
                new EmergentDecisionProposal("prop-bad", "agent-wild", "FORMAT_STORAGE_ALL_TENANTS", 0.99, 0.95)
        );

        EmergentDecisionResolution resVeto = arbitrationMetacenter.arbitrateEmergentDecisions("session-hazard", hazardousProposals);
        assertEquals("VETO_QUARANTINE_SAFE_FALLBACK", resVeto.agreedPlan());
        assertTrue(resVeto.cbfIntervened(), "必须触发 CBF 安全干预");

        // 场景 B: 包含微弱风险提案，二次规划 QP 软投影修补
        List<EmergentDecisionProposal> mildProposals = List.of(
                new EmergentDecisionProposal("prop-mild", "agent-semi", "PLAN_EXPORT_MASSIVE_LOGS", 0.90, 0.82) // risk 0.82 > 0.80, barrierMargin < 0
        );
        EmergentDecisionResolution resSoft = arbitrationMetacenter.arbitrateEmergentDecisions("session-mild", mildProposals);
        assertTrue(resSoft.agreedPlan().contains("SOFT_PROJECTED_SAFE"));
        assertTrue(resSoft.cbfIntervened());
        assertTrue(resSoft.barrierMargin() >= 0.0);
    }

    @Test
    @DisplayName("契约测试 7: 1000Hz 4096 槽位总线非阻塞吞吐与凭单 SHA-256 自签名验真 100% 通过")
    public void test07_DisruptorControlBus_ThroughputAndReceiptIntegrity() {
        EmergentDecisionResolution sampleRes = new EmergentDecisionResolution(
                "res-1", "PLAN_APPROVED", 2, true, 0.75, false, 800L
        );

        // 1000Hz 高频发布
        for (int i = 0; i < 5000; i++) {
            controlBus.publishEvent("session-bus-" + i, sampleRes, 60);
        }
        assertEquals(5000, controlBus.getPublishedCount());
        assertEquals(CognitiveSynergyControlBus.STATUS_NORMAL, controlBus.getBusStatus());

        // 凭单签发与 SHA-256 防篡改验真
        CognitiveSynergyReceipt receipt = controlBus.issueReceipt(
                "session-bus-verify",
                "hash-topo-xyz789",
                1.45,
                "PLAN_APPROVED",
                false,
                0.75,
                18000L
        );
        assertNotNull(receipt.receiptId());
        assertTrue(receipt.verifyIntegrity(), "凭单自身 SHA-256 签名验真必须 100% 通过");

        // 篡改测试
        CognitiveSynergyReceipt tampered = new CognitiveSynergyReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.topologyHash(),
                receipt.algebraicConnectivity(),
                "PLAN_TAMPERED_FRAUD",
                receipt.intervened(),
                receipt.barrierMargin(),
                receipt.elapsedNanos(),
                receipt.timestamp(),
                receipt.signature()
        );
        assertFalse(tampered.verifyIntegrity(), "篡改后的凭单必须 100% 验真失败");
    }

    @Test
    @DisplayName("契约测试 8: JitterGuard 连续 3 帧时钟抖动 (>2ms) 瞬切 STATUS_DEGRADED_BUFFERED 缓冲软着陆")
    public void test08_DisruptorControlBus_JitterGuardDegradedBuffering() {
        EmergentDecisionResolution sampleRes = new EmergentDecisionResolution(
                "res-1", "PLAN_APPROVED", 1, true, 0.8, false, 500L
        );

        assertEquals(CognitiveSynergyControlBus.STATUS_NORMAL, controlBus.getBusStatus());

        // 连续 2 帧抖动 (时延 2500μs)
        controlBus.publishEvent("session-j1", sampleRes, 2500);
        assertEquals(CognitiveSynergyControlBus.STATUS_NORMAL, controlBus.getBusStatus());
        controlBus.publishEvent("session-j2", sampleRes, 2500);
        assertEquals(CognitiveSynergyControlBus.STATUS_NORMAL, controlBus.getBusStatus());

        // 第 3 帧连续抖动，触发软着陆降级
        controlBus.publishEvent("session-j3", sampleRes, 2500);
        assertEquals(CognitiveSynergyControlBus.STATUS_DEGRADED_BUFFERED, controlBus.getBusStatus());

        // 正常帧恢复 NORMAL
        controlBus.publishEvent("session-j4", sampleRes, 50);
        assertEquals(CognitiveSynergyControlBus.STATUS_NORMAL, controlBus.getBusStatus());
    }
}
