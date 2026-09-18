package tech.qiantong.qknow.hermes.agent.swarm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.swarm.dto.MultiAgentDebateReceipt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MultiAgentDebateReceipt 多智能体辩论存证凭单测试")
class MultiAgentDebateReceiptTest {

    @Test
    @DisplayName("凭单自签名生成与完整性验真成功")
    void receipt_signedAndVerify_success() {
        MultiAgentDebateReceipt receipt = MultiAgentDebateReceipt.createSigned(
                "debate-102-001",
                List.of("proponent-agent", "opponent-agent", "judge-agent"),
                2,
                "合规审查通过，已按反方意见修订竞业限制时间窗口",
                0.9450,
                List.of("proponent->opponent", "opponent->judge"),
                true,
                true,
                1420L,
                System.currentTimeMillis()
        );

        assertNotNull(receipt);
        assertEquals("debate-102-001", receipt.debateId());
        assertEquals(2, receipt.totalRounds());
        assertEquals(0.9450, receipt.confidenceScore(), 1e-4);
        assertTrue(receipt.convergedEarly());
        assertTrue(receipt.factVerified());
        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length());

        // 验证完整性
        assertTrue(receipt.verifyIntegrity(), "自签名防篡改校验必须成功");
    }

    @Test
    @DisplayName("反事实消融：被篡改的凭单验真必然失败")
    void receipt_tamperedData_verificationFails() {
        MultiAgentDebateReceipt original = MultiAgentDebateReceipt.createSigned(
                "debate-tamper",
                List.of("agent-A", "agent-B"),
                1,
                "原始结论",
                0.8800,
                List.of(),
                false,
                true,
                500L,
                System.currentTimeMillis()
        );

        assertTrue(original.verifyIntegrity());

        // 篡改置信度分数
        MultiAgentDebateReceipt tampered = new MultiAgentDebateReceipt(
                original.debateId(),
                original.participants(),
                original.totalRounds(),
                original.finalVerdict(),
                0.9999, // 恶意篡改置信度
                original.handoffChain(),
                original.convergedEarly(),
                original.factVerified(),
                original.latencyMs(),
                original.sha256Signature(),
                original.timestamp()
        );

        assertFalse(tampered.verifyIntegrity(), "被篡改置信度的凭单必须验真失败");
    }

    @Test
    @DisplayName("非法参数拦截：轮次超过 3 或置信度越界抛出异常")
    void receipt_invalidBounds_throwsException() {
        // 轮次超限 (> 3)
        assertThrows(IllegalArgumentException.class, () ->
                new MultiAgentDebateReceipt(
                        "deb-1",
                        List.of(),
                        4, // 超过硬上限 3
                        "verdict",
                        0.5,
                        List.of(),
                        false,
                        true,
                        100L,
                        "sig",
                        123L
                )
        );

        // 置信度越界 (< 0 或 > 1)
        assertThrows(IllegalArgumentException.class, () ->
                new MultiAgentDebateReceipt(
                        "deb-1",
                        List.of(),
                        2,
                        "verdict",
                        1.5, // 越界
                        List.of(),
                        false,
                        true,
                        100L,
                        "sig",
                        123L
                )
        );
    }
}
