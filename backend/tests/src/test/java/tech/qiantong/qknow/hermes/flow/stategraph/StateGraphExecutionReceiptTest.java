package tech.qiantong.qknow.hermes.flow.stategraph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphExecutionReceipt;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StateGraphExecutionReceipt 不可变存证凭单测试")
class StateGraphExecutionReceiptTest {

    @Test
    @DisplayName("凭单自签名与完整性校验成功")
    void receipt_signedAndVerify_success() {
        StateGraphExecutionReceipt receipt = StateGraphExecutionReceipt.createSigned(
                "exec-101",
                "flow-001",
                12,
                Set.of("node-A", "node-B", "node-C"),
                Set.of(),
                Map.of("edge-loop-1", 3),
                false,
                true,
                1560L,
                System.currentTimeMillis()
        );

        assertNotNull(receipt);
        assertEquals("exec-101", receipt.executionId());
        assertEquals(12, receipt.totalSupersteps());
        assertTrue(receipt.hasSelfHealed());
        assertFalse(receipt.hasDegradedBreak());
        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length()); // SHA-256 hex string length is 64

        // 验证完整性
        assertTrue(receipt.verifyIntegrity(), "正向签名必须校验成功");
    }

    @Test
    @DisplayName("反事实消融：凭单数据遭篡改后验真必然失败")
    void receipt_tamperedData_verificationFails() {
        StateGraphExecutionReceipt original = StateGraphExecutionReceipt.createSigned(
                "exec-tamper",
                "flow-tamper",
                5,
                Set.of("node-1", "node-2"),
                Set.of(),
                Map.of(),
                false,
                false,
                880L,
                System.currentTimeMillis()
        );

        assertTrue(original.verifyIntegrity());

        // 构造被篡改了步数但保留原签名的凭单
        StateGraphExecutionReceipt tampered = new StateGraphExecutionReceipt(
                original.executionId(),
                original.flowId(),
                999, // 被恶意篡改
                original.completedNodeUuids(),
                original.activeNodeUuids(),
                original.loopCounterMap(),
                original.hasDegradedBreak(),
                original.hasSelfHealed(),
                original.executionLatencyUs(),
                original.sha256Signature(),
                original.timestamp()
        );

        assertFalse(tampered.verifyIntegrity(), "被篡改步数的凭单必须验真失败");
    }

    @Test
    @DisplayName("非法参数校验抛出异常")
    void receipt_invalidParameters_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new StateGraphExecutionReceipt(
                        null,
                        "flow-1",
                        1,
                        Set.of(),
                        Set.of(),
                        Map.of(),
                        false,
                        false,
                        100L,
                        "sig",
                        123L
                )
        );

        assertThrows(IllegalArgumentException.class, () ->
                new StateGraphExecutionReceipt(
                        "exec-1",
                        "flow-1",
                        -1, // 负数超步
                        Set.of(),
                        Set.of(),
                        Map.of(),
                        false,
                        false,
                        100L,
                        "sig",
                        123L
                )
        );
    }
}
