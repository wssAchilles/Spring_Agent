package tech.qiantong.qknow.hermes.flow.hitl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.hitl.model.WorkflowDebugReceipt;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkflowDebugReceipt 调试存证凭据测试")
class WorkflowDebugReceiptTest {

    @Test
    @DisplayName("测试1: 正常生成自签名存证凭据并验真成功 (100% 通过)")
    void testCreateSignedReceiptAndVerifySuccess() {
        long start = System.currentTimeMillis() * 1000L;
        long end = start + 35000L;

        WorkflowDebugReceipt receipt = WorkflowDebugReceipt.createSigned(
                "RCP-DEBUG-20260918-001",
                "BATCH-RUN-9981",
                "workflow-finance-approval",
                12, // 12步
                2,  // 命中2次断点
                3,  // 3次回溯
                1,  // 1次人工审批
                "developer-alice",
                start,
                end,
                "COMPLETED"
        );

        assertNotNull(receipt);
        assertNotNull(receipt.signature());
        assertEquals(64, receipt.signature().length(), "SHA-256 哈希应为 64 位十六进制字符");
        assertTrue(receipt.verifySignature(), "原始凭据自验真必须 100% 成功");
    }

    @Test
    @DisplayName("测试2: 反事实消融——单字段篡改即触发自验真失败")
    void testTamperedReceiptVerifyFails() {
        long start = System.currentTimeMillis() * 1000L;
        long end = start + 35000L;

        WorkflowDebugReceipt original = WorkflowDebugReceipt.createSigned(
                "RCP-DEBUG-20260918-002",
                "BATCH-RUN-9982",
                "workflow-finance-approval",
                8, 1, 0, 0,
                "developer-bob",
                start, end, "COMPLETED"
        );
        assertTrue(original.verifySignature());

        // 模拟篡改步数字段（从 8 篡改为 9）
        WorkflowDebugReceipt tampered = new WorkflowDebugReceipt(
                original.receiptId(),
                original.executionBatchId(),
                original.workflowId(),
                9, // 恶意篡改
                original.breakpointsHitCount(),
                original.timeTravelStepCount(),
                original.hitlTicketsHandledCount(),
                original.operatorUserId(),
                original.startTimestampMicros(),
                original.endTimestampMicros(),
                original.finalStatus(),
                original.signature() // 沿用原签名
        );

        assertFalse(tampered.verifySignature(), "被篡改字段的凭据验真必须失败");
    }

    @Test
    @DisplayName("测试3: 边界参数与空字段鲁棒性测试")
    void testBoundaryAndNullSafety() {
        WorkflowDebugReceipt receipt = WorkflowDebugReceipt.createSigned(
                null, null, null, 0, 0, 0, 0, null, 0L, 0L, null
        );
        assertNotNull(receipt.signature());
        assertTrue(receipt.verifySignature(), "空值字段下签名与验真应依然鲁棒成立");
    }
}
