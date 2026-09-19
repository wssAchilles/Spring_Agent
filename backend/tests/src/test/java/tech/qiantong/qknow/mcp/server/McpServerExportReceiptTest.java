package tech.qiantong.qknow.mcp.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.mcp.core.model.McpTool;
import tech.qiantong.qknow.mcp.server.model.McpServerExportReceipt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 107 McpServerExportReceipt 存证凭单契约测试")
class McpServerExportReceiptTest {

    @Test
    @DisplayName("测试凭单正常签发与密码学 SHA-256 自验真")
    void testReceiptIssuanceAndVerification() {
        McpTool tool1 = new McpTool("qknow_kb_search", "知识库超球面检索", Map.of("requiresLease", false));
        McpTool tool2 = new McpTool("qknow_sandbox_run", "代码沙箱执行", Map.of("requiresLease", true));

        McpServerExportReceipt receipt = McpServerExportReceipt.create(
                "qknow-mcp-server",
                "2.2.1",
                "stdio",
                List.of(tool1, tool2),
                3,
                2,
                true
        );

        assertNotNull(receipt.receiptId(), "收据ID不得为空");
        assertTrue(receipt.receiptId().startsWith("rcpt_mcp_"), "收据ID前缀必须为 rcpt_mcp_");
        assertEquals("qknow-mcp-server", receipt.serverName());
        assertEquals("2.2.1", receipt.serverVersion());
        assertEquals("stdio", receipt.transportChannel());
        assertEquals(2, receipt.exportedToolsCount());
        assertEquals(3, receipt.exportedResourcesCount());
        assertEquals(2, receipt.exportedPromptsCount());
        assertTrue(receipt.quadDefenseEnabled());
        assertEquals(List.of("qknow_sandbox_run"), receipt.highRiskToolNames());
        assertNotNull(receipt.signature(), "数字签名不得为空");

        // 验证自验真函数
        assertTrue(receipt.verify(), "标准合规凭单自验真必须通过");
    }

    @Test
    @DisplayName("测试篡改数据导致签名自验真失败")
    void testReceiptTamperVerificationFailure() {
        McpTool tool1 = new McpTool("qknow_kb_search", "知识库超球面检索", Map.of());
        McpServerExportReceipt receipt = McpServerExportReceipt.create(
                "qknow-mcp-server",
                "2.2.1",
                "sse",
                List.of(tool1),
                1,
                1,
                true
        );

        assertTrue(receipt.verify());

        // 构造被篡改的收据 (伪造工具数量)
        McpServerExportReceipt tampered = new McpServerExportReceipt(
                receipt.receiptId(),
                receipt.serverName(),
                receipt.serverVersion(),
                receipt.transportChannel(),
                999, // 篡改工具数
                receipt.exportedResourcesCount(),
                receipt.exportedPromptsCount(),
                receipt.highRiskToolNames(),
                receipt.quadDefenseEnabled(),
                receipt.exportTimestampMs(),
                receipt.signature()
        );

        assertFalse(tampered.verify(), "被篡改数据的凭单自验真必须判定为失败");
    }
}
