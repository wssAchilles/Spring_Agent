package tech.qiantong.qknow.mcp.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.mcp.client.model.McpExecutionReceipt;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpExecutionReceipt 不可变执行存证凭单测试")
class McpExecutionReceiptTest {

    @Test
    @DisplayName("正常创建签名的存证凭单，自验真成功")
    void receipt_signed_verifySignatureSuccess() {
        McpExecutionReceipt receipt = McpExecutionReceipt.createSigned(
                "RCP-MCP-20260918-001",
                "STDIO",
                "postgres-server",
                "query_user_table",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "READ_ONLY",
                "NOT_REQUIRED",
                "SYSTEM_AUTO",
                "SUCCESS",
                1250000L, // 1.25ms
                System.currentTimeMillis()
        );

        assertNotNull(receipt);
        assertNotNull(receipt.signature());
        assertEquals(64, receipt.signature().length(), "SHA-256 签名长度应为 64 位十六进制字符");
        assertTrue(receipt.verifySignature(), "密码学自验真必须通过");
    }

    @Test
    @DisplayName("反事实消融：单字段被恶意篡改后自验真失败")
    void receipt_tampered_verifySignatureFails() {
        long now = System.currentTimeMillis();
        McpExecutionReceipt original = McpExecutionReceipt.createSigned(
                "RCP-MCP-20260918-002",
                "STDIO",
                "db-gateway",
                "drop_table_users",
                "a1b2c3d4e5f67890",
                "HIGH_RISK_DESTRUCTIVE",
                "REJECTED",
                "sec-admin-01",
                "BLOCKED_HITL",
                3200000L,
                now
        );

        assertTrue(original.verifySignature(), "原始凭单签名验真必须通过");

        // 篡改风险级别为 READ_ONLY
        McpExecutionReceipt tamperedRisk = new McpExecutionReceipt(
                original.receiptId(),
                original.transportMode(),
                original.serverId(),
                original.toolName(),
                original.argumentHash(),
                "READ_ONLY", // 恶意篡改
                original.approvalStatus(),
                original.approverUserId(),
                original.executionStatus(),
                original.durationNanos(),
                original.timestampMillis(),
                original.signature()
        );
        assertFalse(tamperedRisk.verifySignature(), "篡改 riskLevel 必须验真失败");

        // 篡改执行状态为 SUCCESS
        McpExecutionReceipt tamperedStatus = new McpExecutionReceipt(
                original.receiptId(),
                original.transportMode(),
                original.serverId(),
                original.toolName(),
                original.argumentHash(),
                original.riskLevel(),
                original.approvalStatus(),
                original.approverUserId(),
                "SUCCESS", // 恶意篡改
                original.durationNanos(),
                original.timestampMillis(),
                original.signature()
        );
        assertFalse(tamperedStatus.verifySignature(), "篡改 executionStatus 必须验真失败");
    }

    @Test
    @DisplayName("空参数边界防御校验")
    void receipt_nullFieldProtection_handledSafely() {
        assertThrows(IllegalArgumentException.class, () ->
                McpExecutionReceipt.createSigned(
                        null, "STDIO", "srv", "tool", "hash", "READ_ONLY",
                        "NOT_REQUIRED", "SYSTEM", "SUCCESS", 100L, System.currentTimeMillis()
                )
        );

        assertThrows(IllegalArgumentException.class, () ->
                McpExecutionReceipt.createSigned(
                        "rcp-01", null, "srv", "tool", "hash", "READ_ONLY",
                        "NOT_REQUIRED", "SYSTEM", "SUCCESS", 100L, System.currentTimeMillis()
                )
        );
    }
}
