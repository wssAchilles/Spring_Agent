package tech.qiantong.qknow.hermes.agent.swarm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.swarm.dto.MultiAgentDebateReceipt;
import tech.qiantong.qknow.hermes.agent.swarm.engine.StructuredDebateCoordinator;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StructuredDebateCoordinator 结构化对抗辩论控制器测试")
class StructuredDebateCoordinatorTest {

    private StructuredDebateCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new StructuredDebateCoordinator();
    }

    private float[] createHypersphereVector(double angleRad) {
        float[] v = new float[1536];
        // 投影到前两维单位圆，模长为 1.0
        v[0] = (float) Math.cos(angleRad);
        v[1] = (float) Math.sin(angleRad);
        return v;
    }

    @Test
    @DisplayName("辩论在达到最大轮次（2轮）后由仲裁裁决并签署凭单")
    void debate_normalRounds_concludesSuccessfully() {
        AtomicInteger propRounds = new AtomicInteger(0);
        AtomicInteger oppRounds = new AtomicInteger(0);

        StructuredDebateCoordinator.AgentDebateParticipant proponent = new StructuredDebateCoordinator.AgentDebateParticipant() {
            @Override
            public String getAgentId() { return "proponent"; }
            @Override
            public String generateArgument(String topic, List<String> history, String oppLast) {
                propRounds.incrementAndGet();
                return "提案：该并购合同竞业禁止期限应为 24 个月。";
            }
            @Override
            public String generateRebuttal(String topic, List<String> history, String propLast) { return ""; }
            @Override
            public float[] getArgumentEmbedding(String text) {
                // 角度 0 弧度
                return createHypersphereVector(0.0);
            }
        };

        StructuredDebateCoordinator.AgentDebateParticipant opponent = new StructuredDebateCoordinator.AgentDebateParticipant() {
            @Override
            public String getAgentId() { return "opponent"; }
            @Override
            public String generateArgument(String topic, List<String> history, String oppLast) { return ""; }
            @Override
            public String generateRebuttal(String topic, List<String> history, String propLast) {
                oppRounds.incrementAndGet();
                return "质疑：法定最长不超过 24 个月，但本案行业特殊，建议缩减为 12 个月。";
            }
            @Override
            public float[] getArgumentEmbedding(String text) {
                // 角度 0.8 弧度 (散度约 0.25 > 0.15，未提前收敛)
                return createHypersphereVector(0.8);
            }
        };

        StructuredDebateCoordinator.AgentDebateJudge judge = new StructuredDebateCoordinator.AgentDebateJudge() {
            @Override
            public String getAgentId() { return "judge-senior"; }
            @Override
            public StructuredDebateCoordinator.JudgeVerdictBO arbitrate(String topic, List<String> transcript) {
                return new StructuredDebateCoordinator.JudgeVerdictBO(
                        "仲裁裁定：采纳折中方案，设定为 18 个月并附补偿金条款",
                        0.92,
                        true
                );
            }
        };

        MultiAgentDebateReceipt receipt = coordinator.coordinateDebate(
                "debate-case-001",
                "高管竞业限制期限审查",
                proponent,
                opponent,
                judge,
                2
        );

        assertNotNull(receipt);
        assertEquals(2, receipt.totalRounds());
        assertEquals(2, propRounds.get());
        assertEquals(2, oppRounds.get());
        assertEquals("仲裁裁定：采纳折中方案，设定为 18 个月并附补偿金条款", receipt.finalVerdict());
        assertEquals(0.92, receipt.confidenceScore(), 1e-4);
        assertFalse(receipt.convergedEarly());
        assertTrue(receipt.verifyIntegrity());
    }

    @Test
    @DisplayName("测地散度 <= 0.15 时触发提前收敛截断（第1轮即收敛退出）")
    void debate_geodesicConverged_earlyExit() {
        AtomicInteger propRounds = new AtomicInteger(0);

        StructuredDebateCoordinator.AgentDebateParticipant proponent = new StructuredDebateCoordinator.AgentDebateParticipant() {
            @Override
            public String getAgentId() { return "proponent"; }
            @Override
            public String generateArgument(String topic, List<String> history, String oppLast) {
                propRounds.incrementAndGet();
                return "一致同意采用 12 个月方案。";
            }
            @Override
            public String generateRebuttal(String topic, List<String> history, String propLast) { return ""; }
            @Override
            public float[] getArgumentEmbedding(String text) {
                // 0 弧度
                return createHypersphereVector(0.0);
            }
        };

        StructuredDebateCoordinator.AgentDebateParticipant opponent = new StructuredDebateCoordinator.AgentDebateParticipant() {
            @Override
            public String getAgentId() { return "opponent"; }
            @Override
            public String generateArgument(String topic, List<String> history, String oppLast) { return ""; }
            @Override
            public String generateRebuttal(String topic, List<String> history, String propLast) {
                return "我也同意 12 个月方案。";
            }
            @Override
            public float[] getArgumentEmbedding(String text) {
                // 极小夹角 0.1 弧度 (散度约 0.0318 <= 0.15，触发提前收敛)
                return createHypersphereVector(0.1);
            }
        };

        StructuredDebateCoordinator.AgentDebateJudge judge = (topic, transcript) ->
                new StructuredDebateCoordinator.JudgeVerdictBO("双方高度一致，直接采纳 12 个月", 0.98, true);

        MultiAgentDebateReceipt receipt = coordinator.coordinateDebate(
                "debate-early-exit",
                "快速一致性议题",
                proponent,
                opponent,
                judge,
                3
        );

        assertNotNull(receipt);
        assertEquals(1, receipt.totalRounds(), "第 1 轮即触发纳什测地收敛");
        assertTrue(receipt.convergedEarly(), "必须标记提前收敛");
        assertEquals(1, propRounds.get(), "仅执行 1 轮");
        assertTrue(receipt.verifyIntegrity());
    }

    @Test
    @DisplayName("超长论点被单轮 Token 配额（1500 Tokens）刚性截断")
    void debate_excessiveArgument_tokenQuotaTruncated() {
        // 构造超长文本 (> 6000 字符)
        String hugeText = "超长论点内容重复：".repeat(800);

        StructuredDebateCoordinator.AgentDebateParticipant proponent = new StructuredDebateCoordinator.AgentDebateParticipant() {
            @Override
            public String getAgentId() { return "p1"; }
            @Override
            public String generateArgument(String topic, List<String> history, String oppLast) {
                return hugeText;
            }
            @Override
            public String generateRebuttal(String topic, List<String> history, String propLast) { return ""; }
            @Override
            public float[] getArgumentEmbedding(String text) {
                // 验证传入 embedding 的文本已被截断
                assertTrue(text.contains("[TOKEN_QUOTA_EXCEEDED_TRUNCATED]"), "超出配额文本必须带截断标识");
                return createHypersphereVector(0.0);
            }
        };

        StructuredDebateCoordinator.AgentDebateParticipant opponent = new StructuredDebateCoordinator.AgentDebateParticipant() {
            @Override
            public String getAgentId() { return "o1"; }
            @Override
            public String generateArgument(String topic, List<String> history, String oppLast) { return ""; }
            @Override
            public String generateRebuttal(String topic, List<String> history, String propLast) {
                return "简短回应";
            }
            @Override
            public float[] getArgumentEmbedding(String text) {
                return createHypersphereVector(0.5);
            }
        };

        StructuredDebateCoordinator.AgentDebateJudge judge = (t, hist) ->
                new StructuredDebateCoordinator.JudgeVerdictBO("裁决完成", 0.85, true);

        MultiAgentDebateReceipt receipt = coordinator.coordinateDebate(
                "debate-quota-test",
                "配额测试",
                proponent,
                opponent,
                judge,
                1
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity());
    }
}
