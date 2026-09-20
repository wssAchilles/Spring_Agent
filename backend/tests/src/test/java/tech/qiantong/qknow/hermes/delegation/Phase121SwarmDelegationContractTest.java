package tech.qiantong.qknow.hermes.delegation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.swarm.delegation.BoundedHandoverGuard;
import tech.qiantong.qknow.ai.swarm.delegation.HierarchicalSwarmDelegationMetacenter;
import tech.qiantong.qknow.ai.swarm.delegation.HypersphericalIntentMatcher;
import tech.qiantong.qknow.ai.swarm.delegation.SwarmDelegationReceipt;
import tech.qiantong.qknow.ai.swarm.delegation.ThinkingContextPropagator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 121: 多智能体自适应分层协同、意图委托网络与有界状态流转中枢 契约测试
 * 严格验证 8 大核心工程契约：
 * 1. 契约 1: 阿里千问 1536 维超球面单位向量流形几何约束校验
 * 2. 契约 2: 超球面测地意图匹配精度与 tau_intent >= 0.82 门禁拦截
 * 3. 契约 3: 李雅普诺夫单调递减与最大深度 D_max <= 4 硬熔断
 * 4. 契约 4: 双向即时乒乓交接 (A -> B -> A) 死锁拦截
 * 5. 契约 5: 多节点拓扑闭环 (A -> B -> C -> A) 死锁拦截
 * 6. 契约 6: DeepSeek 思考流因果脚手架提取与 API 格式无损保真
 * 7. 契约 7: 不可变委托执行凭单 SHA-256 自签名与单比特篡改拦截
 * 8. 契约 8: 端到端复合业务多智能体自适应分层委托流转全链路闭环
 */
@DisplayName("Phase 121: 多智能体自适应分层协同与意图委托网络契约测试")
class Phase121SwarmDelegationContractTest {

    private HypersphericalIntentMatcher intentMatcher;
    private BoundedHandoverGuard handoverGuard;
    private ThinkingContextPropagator contextPropagator;
    private HierarchicalSwarmDelegationMetacenter metacenter;

    @BeforeEach
    void setUp() {
        intentMatcher = new HypersphericalIntentMatcher();
        handoverGuard = new BoundedHandoverGuard();
        contextPropagator = new ThinkingContextPropagator();
        metacenter = new HierarchicalSwarmDelegationMetacenter(intentMatcher, handoverGuard, contextPropagator);
    }

    /**
     * 生成严格满足 ||v||_2 = 1.0 的 1536 维超球面归一化测试向量
     */
    private float[] createNormalized1536Vector(float seed) {
        float[] v = new float[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            v[i] = (float) Math.sin(seed + i * 0.05);
            sumSq += v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约 1: 阿里千问 1536 维超球面单位向量流形几何约束校验")
    void testContract1_HypersphericalGeometryConstraint() {
        // 1. 合法超球面向量校验
        float[] validVec = createNormalized1536Vector(1.0f);
        assertDoesNotThrow(() -> intentMatcher.validateHypersphericalConstraint(validVec));

        // 2. 维度不匹配校验 (非 1536 维)
        float[] invalidDimVec = new float[768];
        IllegalArgumentException dimEx = assertThrows(IllegalArgumentException.class,
                () -> intentMatcher.validateHypersphericalConstraint(invalidDimVec));
        assertTrue(dimEx.getMessage().contains("1536"));

        // 3. 模长未归一化校验 (未投射至 S^1535)
        float[] unnormalizedVec = new float[1536];
        unnormalizedVec[0] = 5.0f; // 模长明显大于 1.0
        IllegalStateException normEx = assertThrows(IllegalStateException.class,
                () -> intentMatcher.validateHypersphericalConstraint(unnormalizedVec));
        assertTrue(normEx.getMessage().contains("hypersphere"));
    }

    @Test
    @DisplayName("契约 2: 超球面测地意图匹配精度与 tau_intent >= 0.82 门禁拦截")
    void testContract2_IntentMatchingConfidenceGate() {
        float[] queryVec = createNormalized1536Vector(10.0f);

        // 创建高亲和度候选智能体 (向量相近)
        float[] highAffinityVec = queryVec.clone();
        HypersphericalIntentMatcher.AgentCard procurementAgent = new HypersphericalIntentMatcher.AgentCard(
                "agent_procurement", "采购智能体", "负责供应商比价与采购合同生成", highAffinityVec);

        // 创建正交低亲和度候选智能体
        float[] lowAffinityVec = createNormalized1536Vector(500.0f);
        HypersphericalIntentMatcher.AgentCard legalAgent = new HypersphericalIntentMatcher.AgentCard(
                "agent_legal", "法务智能体", "负责合规审查与法务尽职调查", lowAffinityVec);

        // 1. 高置信度匹配成功委派
        HypersphericalIntentMatcher.IntentMatchResult highResult = intentMatcher.matchTargetAgent(
                queryVec, List.of(procurementAgent, legalAgent));
        assertTrue(highResult.delegated(), "亲和度 >= 0.82 必须成功委派");
        assertEquals("agent_procurement", highResult.targetAgentId());
        assertTrue(highResult.affinityScore() >= 0.82);

        // 2. 边缘模糊查询触发安全上浮
        float[] ambiguousQueryVec = createNormalized1536Vector(999.0f);
        HypersphericalIntentMatcher.IntentMatchResult lowResult = intentMatcher.matchTargetAgent(
                ambiguousQueryVec, List.of(procurementAgent, legalAgent));
        assertFalse(lowResult.delegated(), "亲和度 < 0.82 必须安全上浮，拒绝盲目委派");
        assertNotNull(lowResult.escalationReason());
        assertTrue(lowResult.escalationReason().contains("LOW_AFFINITY_CONFIDENCE"));
    }

    @Test
    @DisplayName("契约 3: 李雅普诺夫单调递减与最大深度 D_max <= 4 硬熔断")
    void testContract3_LyapunovMaxDepthExceededHardCutoff() {
        String sessionId = "sess_depth_test_001";
        handoverGuard.clearSession(sessionId);

        // 第 1 步: A0 -> A1 (深度 1)
        BoundedHandoverGuard.GuardDecision d1 = handoverGuard.evaluateHandover(sessionId, "A0", "A1");
        assertTrue(d1.authorized());
        assertEquals(1, d1.currentDepth());

        // 第 2 步: A1 -> A2 (深度 2)
        BoundedHandoverGuard.GuardDecision d2 = handoverGuard.evaluateHandover(sessionId, "A1", "A2");
        assertTrue(d2.authorized());
        assertEquals(2, d2.currentDepth());

        // 第 3 步: A2 -> A3 (深度 3)
        BoundedHandoverGuard.GuardDecision d3 = handoverGuard.evaluateHandover(sessionId, "A2", "A3");
        assertTrue(d3.authorized());
        assertEquals(3, d3.currentDepth());

        // 第 4 步: A3 -> A4 (深度 4, 达上限)
        BoundedHandoverGuard.GuardDecision d4 = handoverGuard.evaluateHandover(sessionId, "A3", "A4");
        assertTrue(d4.authorized());
        assertEquals(4, d4.currentDepth());

        // 第 5 步: A4 -> A5 (试图突破 D_max = 4, 必须绝对阻断)
        BoundedHandoverGuard.GuardDecision d5 = handoverGuard.evaluateHandover(sessionId, "A4", "A5");
        assertFalse(d5.authorized(), "深度超过 D_max=4 必须被硬熔断");
        assertEquals("MAX_DELEGATION_DEPTH_EXCEEDED", d5.abortCode());
        assertTrue(d5.abortReason().contains("4 层上限"));
    }

    @Test
    @DisplayName("契约 4: 双向即时乒乓交接 (A -> B -> A) 死锁拦截")
    void testContract4_PingPongHandoverDeadlockInterception() {
        String sessionId = "sess_pingpong_001";
        handoverGuard.clearSession(sessionId);

        // A -> B
        BoundedHandoverGuard.GuardDecision step1 = handoverGuard.evaluateHandover(sessionId, "Agent_Market", "Agent_Portfolio");
        assertTrue(step1.authorized());

        // B -> A (立即乒乓反弹，必须被状态机守卫秒级拦截)
        BoundedHandoverGuard.GuardDecision step2 = handoverGuard.evaluateHandover(sessionId, "Agent_Portfolio", "Agent_Market");
        assertFalse(step2.authorized(), "即时乒乓交接必须被 100% 拦截阻断");
        assertEquals("SWARM_PING_PONG_DETECTED", step2.abortCode());
        assertTrue(step2.abortReason().contains("乒乓移交死锁"));
    }

    @Test
    @DisplayName("契约 5: 多节点拓扑闭环 (A -> B -> C -> A) 死锁拦截")
    void testContract5_TopologicalCycleDeadlockInterception() {
        String sessionId = "sess_cycle_001";
        handoverGuard.clearSession(sessionId);

        // A -> B
        BoundedHandoverGuard.GuardDecision step1 = handoverGuard.evaluateHandover(sessionId, "Agent_A", "Agent_B");
        assertTrue(step1.authorized());

        // B -> C
        BoundedHandoverGuard.GuardDecision step2 = handoverGuard.evaluateHandover(sessionId, "Agent_B", "Agent_C");
        assertTrue(step2.authorized());

        // C -> A (形成拓扑环 A -> B -> C -> A，必须被拦截)
        BoundedHandoverGuard.GuardDecision step3 = handoverGuard.evaluateHandover(sessionId, "Agent_C", "Agent_A");
        assertFalse(step3.authorized(), "多节点拓扑闭环必须被 100% 拦截阻断");
        assertEquals("SWARM_TOPOLOGICAL_CYCLE_DETECTED", step3.abortCode());
        assertTrue(step3.abortReason().contains("拓扑闭环"));
    }

    @Test
    @DisplayName("契约 6: DeepSeek 思考流因果脚手架提取与 API 格式无损保真")
    void testContract6_ThinkingContextPropagationAndAPICompliance() {
        // 1. 构建符合 DeepSeek 官方规范的思考消息
        ThinkingContextPropagator.DeepSeekMessage assistantMsg = contextPropagator.buildAssistantMessageWithThinking(
                "这是最终业务回复", "这是参数化深度思考推演链路，排除方案甲并选择方案乙");
        assertEquals("assistant", assistantMsg.role());
        assertEquals("这是最终业务回复", assistantMsg.content());
        assertEquals("这是参数化深度思考推演链路，排除方案甲并选择方案乙", assistantMsg.reasoning_content());

        // 2. 跨 Agent 委托时因果脚手架蒸馏
        ThinkingContextPropagator.InheritedRationale rationale = contextPropagator.distillRationaleForHandoff(
                "agent_procurement", "采购专家", assistantMsg.reasoning_content(), "建议向供应商 B 采购");
        assertNotNull(rationale);
        assertEquals("agent_procurement", rationale.sourceAgentId());
        assertTrue(rationale.reasoningContent().contains("排除方案甲"));

        // 3. 格式化生成注入提示词切片
        String promptSection = contextPropagator.formatRationalePromptSection(List.of(rationale));
        assertTrue(promptSection.contains("INHERITED RATIONALE"));
        assertTrue(promptSection.contains("采购专家"));
    }

    @Test
    @DisplayName("契约 7: 不可变委托执行凭单 SHA-256 自签名与单比特篡改拦截")
    void testContract7_ImmutableReceiptCryptographicTamperResistance() {
        String receiptId = "REC-SWARM-PHASE121-001";
        String sessionId = "sess_audit_001";
        String rootAgent = "orchestrator_root";
        int depth = 2;
        double affinity = 0.9421;
        long start = 1726830000000000L;
        long end = 1726830000045000L;
        String status = "COMPLETED";

        String signature = SwarmDelegationReceipt.computeSignature(
                receiptId, sessionId, rootAgent, depth, affinity, start, end, status);
        assertNotNull(signature);
        assertEquals(64, signature.length(), "SHA-256 签名十六进制长度必须严格为 64 字符");

        SwarmDelegationReceipt receipt = new SwarmDelegationReceipt(
                receiptId, sessionId, rootAgent, depth, affinity,
                List.of("orchestrator_root", "agent_procurement", "agent_compliance"),
                start, end, status, signature);

        // 1. 合法凭单自验真
        assertTrue(receipt.verifySignature(), "合法凭单自验真必须 100% 通过");

        // 2. 深度被单比特篡改
        SwarmDelegationReceipt tamperedDepth = new SwarmDelegationReceipt(
                receiptId, sessionId, rootAgent, 3, affinity, // 篡改 depth
                receipt.delegationTopologyTree(), start, end, status, signature);
        assertFalse(tamperedDepth.verifySignature(), "深度被篡改后验真必须立即失败 (Fail-Close)");

        // 3. 状态被篡改
        SwarmDelegationReceipt tamperedStatus = new SwarmDelegationReceipt(
                receiptId, sessionId, rootAgent, depth, affinity,
                receipt.delegationTopologyTree(), start, end, "FAILED", signature);
        assertFalse(tamperedStatus.verifySignature(), "状态被篡改后验真必须立即失败 (Fail-Close)");
    }

    @Test
    @DisplayName("契约 8: 端到端复合业务多智能体自适应分层委托流转全链路闭环")
    void testContract8_EndToEndHierarchicalDelegationExecution() {
        String sessionId = "sess_e2e_procurement_001";
        float[] queryVec = createNormalized1536Vector(25.0f);

        // 注册专业子智能体
        HypersphericalIntentMatcher.AgentCard financeAgent = new HypersphericalIntentMatcher.AgentCard(
                "agent_finance", "财务核算智能体", "负责发票审计与付款审批", queryVec.clone());

        HierarchicalSwarmDelegationMetacenter.DelegationRequest request =
                new HierarchicalSwarmDelegationMetacenter.DelegationRequest(
                        sessionId,
                        "orchestrator_root",
                        "对本次 50 万元跨境采购进行资金核算与支付排期",
                        queryVec,
                        List.of(financeAgent),
                        "先期合规筛查已通过，预算充足",
                        "核算通过，准予支付"
                );

        // 触发自适应协同流转
        HierarchicalSwarmDelegationMetacenter.DelegationResult result = metacenter.dispatchDelegation(request);

        assertNotNull(result);
        assertTrue(result.success(), "合规意图委托必须执行成功");
        assertEquals("agent_finance", result.dispatchedAgentId());
        assertEquals(1, result.delegationDepth());
        assertNotNull(result.receipt());
        assertTrue(result.receipt().verifySignature(), "流转生成的不可变凭单必须具备合法密码学签名");
        assertTrue(result.formattedPromptForTarget().contains("INHERITED RATIONALE"));
    }
}
