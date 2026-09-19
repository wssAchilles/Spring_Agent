package tech.qiantong.qknow.hermes.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.trace.model.TraceExecutionReceipt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 106 契约测试集三：全链路不可变追踪存证凭单密码学验真测试
 *
 * @author Achilles
 * @version 1.0
 */
public class TraceExecutionReceiptTest {

    @Test
    @DisplayName("契约 7：密码学存证凭单正常构建与自签名验真通过 (100% 通过)")
    void testReceiptCreationAndVerification() {
        TraceExecutionReceipt receipt = TraceExecutionReceipt.create(
                "RCPT-TRACE-106001",
                "trace-alpha-001",
                "wf-finance-audit",
                12,
                152000L,
                98000L,
                1450,
                0,
                System.currentTimeMillis()
        );

        assertNotNull(receipt);
        assertEquals("RCPT-TRACE-106001", receipt.receiptId());
        assertEquals("trace-alpha-001", receipt.traceId());
        assertEquals("wf-finance-audit", receipt.workflowId());
        assertEquals(12, receipt.totalSpans());
        assertEquals(152000L, receipt.rootDurationUs());
        assertEquals(98000L, receipt.criticalPathDurationUs());
        assertEquals(1450, receipt.totalTokens());
        assertEquals(0, receipt.errorCount());

        assertTrue(receipt.verifySignature(), "原始签发的存证凭单自验真必须 100% 成功");
    }

    @Test
    @DisplayName("契约 8：单字段恶意篡改 (修改 durationUs / totalTokens) 100% 拦截失败")
    void testTamperProofVerification() {
        TraceExecutionReceipt original = TraceExecutionReceipt.create(
                "RCPT-TRACE-106002",
                "trace-beta-002",
                "wf-order-dispatch",
                5,
                45000L,
                32000L,
                680,
                1,
                System.currentTimeMillis()
        );

        assertTrue(original.verifySignature());

        // 恶意篡改 1：伪造关键路径耗时 (缩小耗时掩盖性能瓶颈)
        TraceExecutionReceipt tamperedCriticalPath = new TraceExecutionReceipt(
                original.receiptId(),
                original.traceId(),
                original.workflowId(),
                original.totalSpans(),
                original.rootDurationUs(),
                1000L, // 篡改
                original.totalTokens(),
                original.errorCount(),
                original.timestampMs(),
                original.signature()
        );
        assertFalse(tamperedCriticalPath.verifySignature(), "篡改 criticalPathDurationUs 必须导致验真失败");

        // 恶意篡改 2：伪造 Token 消耗数 (少报扣费)
        TraceExecutionReceipt tamperedTokens = new TraceExecutionReceipt(
                original.receiptId(),
                original.traceId(),
                original.workflowId(),
                original.totalSpans(),
                original.rootDurationUs(),
                original.criticalPathDurationUs(),
                10, // 篡改
                original.errorCount(),
                original.timestampMs(),
                original.signature()
        );
        assertFalse(tamperedTokens.verifySignature(), "篡改 totalTokens 必须导致验真失败");

        // 恶意篡改 3：伪造工作流 ID
        TraceExecutionReceipt tamperedWorkflow = new TraceExecutionReceipt(
                original.receiptId(),
                original.traceId(),
                "wf-hacked", // 篡改
                original.totalSpans(),
                original.rootDurationUs(),
                original.criticalPathDurationUs(),
                original.totalTokens(),
                original.errorCount(),
                original.timestampMs(),
                original.signature()
        );
        assertFalse(tamperedWorkflow.verifySignature(), "篡改 workflowId 必须导致验真失败");
    }
}
