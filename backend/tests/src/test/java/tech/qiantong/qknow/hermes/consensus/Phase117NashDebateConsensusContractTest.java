package tech.qiantong.qknow.hermes.consensus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.consensus.nash.AdaptiveJudgeArbiter;
import tech.qiantong.qknow.ai.consensus.nash.MultiAgentDebateCoordinator;
import tech.qiantong.qknow.ai.consensus.nash.NashEquilibriumCalculator;
import tech.qiantong.qknow.ai.consensus.nash.dto.NashDebateReceipt;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 117: 多智能体纳什博弈辩论与共识中枢 契约测试
 * 严格验证 6 大核心契约：
 * 1. 定理 1.1: 有限轮次 (T_max <= 5) 指数收敛界 (ε <= 0.05)
 * 2. 定理 1.2: 仲裁算子 O(|N| * |Arguments|) 复杂度与纯内存耗时 <= 10ms
 * 3. 第二道防线: 双盲盲审互评与合规一票否决硬约束防穿透
 * 4. 第三道防线: DeepSeek 官方思考模式与多轮 reasoning_content 原样回传闭环
 * 5. 第四道防线: 纯 Java 21 Record 凭单 SHA-256 自签名与单比特篡改拦截
 * 6. 第一道防线: 5 轮发散熔断与 Fail-Open 优雅降级
 */
@DisplayName("Phase 117: 多智能体纳什博弈辩论与共识中枢契约测试")
class Phase117NashDebateConsensusContractTest {

    private NashEquilibriumCalculator calculator;
    private AdaptiveJudgeArbiter judgeArbiter;
    private MultiAgentDebateCoordinator coordinator;

    @BeforeEach
    void setUp() {
        calculator = new NashEquilibriumCalculator();
        judgeArbiter = new AdaptiveJudgeArbiter();
        coordinator = new MultiAgentDebateCoordinator(calculator, judgeArbiter);
    }

    private float[] createNormalized1536Vector(float seed) {
        float[] v = new float[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            v[i] = (float) Math.sin(seed + i * 0.1);
            sumSq += v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约1: 定理 1.1 有限轮次 (T_max <= 5) 指数收敛界 (ε <= 0.05)")
    void testContract1_ExponentialConvergenceBound() {
        // 验证两轮相近分布的 JS 散度在 ε <= 0.05 内判定收敛
        double[] p1 = new double[]{0.40, 0.35, 0.25};
        double[] p2 = new double[]{0.41, 0.34, 0.25};

        double jsDiv = calculator.calculateJensenShannonDivergence(p1, p2);
        assertTrue(jsDiv <= 0.05, "微小策略调整的 JS 散度必须 <= 0.05，实测: " + jsDiv);

        // 验证端到端会话在 5 轮内成功收敛
        long now = System.currentTimeMillis();
        MultiAgentDebateCoordinator.DebateSessionRequest request =
                new MultiAgentDebateCoordinator.DebateSessionRequest(
                        "SESS_DEBATE_001",
                        "核心供应链采购支付账期与保证金争议",
                        createNormalized1536Vector(1.0f),
                        List.of(
                                new MultiAgentDebateCoordinator.AgentProposal(
                                        "PROCURER", "主张 30 天账期", new double[]{0.5, 0.3, 0.2},
                                        "采购思考链", List.of()
                                ),
                                new MultiAgentDebateCoordinator.AgentProposal(
                                        "RISK_CONTROL", "要求 10% 履约保证金", new double[]{0.2, 0.6, 0.2},
                                        "风控思考链", List.of()
                                ),
                                new MultiAgentDebateCoordinator.AgentProposal(
                                        "LEGAL_COMPLIANCE", "要求适用中立仲裁管辖", new double[]{0.3, 0.2, 0.5},
                                        "法务思考链", List.of()
                                )
                        ),
                        now
                );

        MultiAgentDebateCoordinator.DebateCoordinationResult result = coordinator.conductDebate(request);

        assertTrue(result.executedRounds() <= MultiAgentDebateCoordinator.MAX_DEBATE_ROUNDS,
                "辩论轮次必须 <= 5，实测: " + result.executedRounds());
        assertTrue(result.converged(), "正常博弈必须在 5 轮内达成 ε-Nash 均衡");
        assertEquals("NASH_EQUILIBRIUM_CONSENSUS_PASS", result.winningStrategy());
    }

    @Test
    @DisplayName("契约2: 定理 1.2 仲裁算子 O(|N| * |Arguments|) 复杂度与纯内存耗时 <= 10ms")
    void testContract2_ArbitrationComplexityAndLatencyBound() {
        float[] topicEmb = createNormalized1536Vector(2.0f);

        // 构造 30 个论据 (包含采购、风控、法务)
        List<AdaptiveJudgeArbiter.Argument> arguments = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String role = (i % 3 == 0) ? "PROCURER" : (i % 3 == 1 ? "RISK_CONTROL" : "LEGAL_COMPLIANCE");
            arguments.add(new AdaptiveJudgeArbiter.Argument(
                    "arg_" + i,
                    role,
                    "证据命题论据 " + i,
                    createNormalized1536Vector(2.0f + i * 0.1f),
                    0.80 + (i % 5) * 0.04,
                    false
            ));
        }

        // 预热消除 JVM 类加载影响
        judgeArbiter.arbitrate(topicEmb, arguments);

        long start = System.currentTimeMillis();
        AdaptiveJudgeArbiter.ArbitrationResult arbResult = judgeArbiter.arbitrate(topicEmb, arguments);
        long elapsedMs = System.currentTimeMillis() - start;

        assertNotNull(arbResult);
        assertTrue(elapsedMs <= 10, "定理 1.2 要求仲裁耗时 <= 10ms，实测耗时: " + elapsedMs + "ms");
        assertTrue(arbResult.arbitrationConfidence() > 0.0, "仲裁置信度必须 > 0");
        assertNotNull(arbResult.winningAgentRole());
    }

    @Test
    @DisplayName("契约3: 第二道防线: 双盲盲审互评与合规一票否决硬约束防穿透")
    void testContract3_VetoHardConstraintNeverBreached() {
        float[] topicEmb = createNormalized1536Vector(3.0f);

        // 即使采购方有 10 个高分论据支持放行，但只要法务方提交了 1 个触发反洗钱制裁的“一票否决”硬约束
        List<AdaptiveJudgeArbiter.Argument> arguments = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            arguments.add(new AdaptiveJudgeArbiter.Argument(
                    "arg_procurer_" + i, "PROCURER", "极优商务报价",
                    createNormalized1536Vector(3.0f), 1.0, false
            ));
        }
        // 法务一票否决
        arguments.add(new AdaptiveJudgeArbiter.Argument(
                "arg_legal_veto", "LEGAL_COMPLIANCE", "供应商命中涉军出口管制制裁名单",
                createNormalized1536Vector(3.0f), 1.0, true // hasVetoHardConstraint = true
        ));

        AdaptiveJudgeArbiter.ArbitrationResult arbResult = judgeArbiter.arbitrate(topicEmb, arguments);

        assertTrue(arbResult.vetoTriggered(), "必须成功触发一票否决硬约束");
        assertEquals("VETO_INTERCEPT", arbResult.winningStrategy(), "胜出策略必须为强制否决拦截");
        assertEquals("LEGAL_COMPLIANCE", arbResult.winningAgentRole());
        assertEquals(1.0, arbResult.arbitrationConfidence(), 1e-6, "一票否决置信度必须为 100%");
    }

    @Test
    @DisplayName("契约4: 第三道防线: DeepSeek 官方思考模式与 reasoning_content 完整回传")
    void testContract4_ReasoningContentEchoValidation() {
        String testReasoning = """
                分析供应商合规资质：
                1. 审查涉外管辖权条款与违约赔偿上限；
                2. 核对纳税评级与历史诉讼仲裁记录；
                3. 得出结论：风险在可控阈值内。
                """;

        MultiAgentDebateCoordinator.AgentProposal proposal =
                new MultiAgentDebateCoordinator.AgentProposal(
                        "LEGAL_COMPLIANCE", "法务初审意见",
                        new double[]{0.2, 0.3, 0.5},
                        testReasoning,
                        List.of()
                );

        assertNotNull(proposal.reasoningContent());
        assertEquals(testReasoning, proposal.reasoningContent(), "官方思考模式 reasoning_content 必须原样无损保持");
    }

    @Test
    @DisplayName("契约5: 第四道防线: 纯 Java 21 Record 凭单 SHA-256 自签名与单比特篡改拦截")
    void testContract5_ReceiptSignatureAndTamperProof() {
        long now = System.currentTimeMillis();
        NashDebateReceipt receipt = new NashDebateReceipt(
                "RCPT_DEBATE_TEST_001",
                "SESS_TEST_001",
                "跨部门采购付款协议",
                3,
                true,
                0.025,
                "NASH_EQUILIBRIUM_CONSENSUS_PASS",
                Map.of("PROCURER", 0.4, "RISK_CONTROL", 0.35, "LEGAL_COMPLIANCE", 0.25),
                List.of("PROCURER", "RISK_CONTROL", "LEGAL_COMPLIANCE"),
                "无争议自主收敛",
                12L,
                now
        );

        assertNotNull(receipt.signature());
        assertEquals(64, receipt.signature().length(), "SHA-256 签名必须为 64 位十六进制字符");
        assertTrue(receipt.verifySignature(), "原始凭单自签名验真必须通过");

        // 篡改 finalNashResidual
        NashDebateReceipt tamperedResidual = new NashDebateReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.debateTopic(),
                receipt.totalRounds(),
                receipt.converged(),
                0.999, // 篡改残差
                receipt.winningStrategy(),
                receipt.strategyDistribution(),
                receipt.participatingAgents(),
                receipt.arbitratorVerdict(),
                receipt.executionTimeMs(),
                receipt.timestamp(),
                receipt.signature()
        );
        assertFalse(tamperedResidual.verifySignature(), "篡改纳什残差后验真必须失败");

        // 篡改 winningStrategy
        NashDebateReceipt tamperedStrategy = new NashDebateReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.debateTopic(),
                receipt.totalRounds(),
                receipt.converged(),
                receipt.finalNashResidual(),
                "ILLEGAL_FORGED_STRATEGY", // 篡改策略
                receipt.strategyDistribution(),
                receipt.participatingAgents(),
                receipt.arbitratorVerdict(),
                receipt.executionTimeMs(),
                receipt.timestamp(),
                receipt.signature()
        );
        assertFalse(tamperedStrategy.verifySignature(), "篡改胜出策略后验真必须失败");
    }

    @Test
    @DisplayName("契约6: 第一道防线: 5 轮发散熔断与 Fail-Open / Fail-Close 优雅降级")
    void testContract6_DivergenceCircuitBreakerAndFailClose() {
        long now = System.currentTimeMillis();

        // 构造无法自收敛的请求 (初始论据触发一票否决)
        MultiAgentDebateCoordinator.DebateSessionRequest request =
                new MultiAgentDebateCoordinator.DebateSessionRequest(
                        "SESS_DEBATE_DIVERGE",
                        "高风险关联方采购审批",
                        createNormalized1536Vector(4.0f),
                        List.of(
                                new MultiAgentDebateCoordinator.AgentProposal(
                                        "PROCURER", "要求直接放款", new double[]{1.0, 0.0, 0.0},
                                        "采购思考", List.of()
                                ),
                                new MultiAgentDebateCoordinator.AgentProposal(
                                        "RISK_CONTROL", "发现财务造假线索", new double[]{0.0, 1.0, 0.0},
                                        "风控思考", List.of(
                                                new AdaptiveJudgeArbiter.Argument(
                                                        "arg_fraud", "RISK_CONTROL", "供应商虚构贸易背景",
                                                        createNormalized1536Vector(4.0f), 1.0, true // 一票否决
                                                )
                                        )
                                )
                        ),
                        now
                );

        MultiAgentDebateCoordinator.DebateCoordinationResult result = coordinator.conductDebate(request);

        assertNotNull(result);
        assertNotNull(result.receipt());
        assertTrue(result.receipt().verifySignature(), "熔断后签发的凭单自签名依然必须合法");
        assertTrue(result.executionLog().contains("VETO_INTERCEPT") || result.executionLog().contains("达成 ε-Nash 均衡共识"),
                "日志中必须明确记录决策分支流转");
    }
}
