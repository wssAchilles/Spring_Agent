package tech.qiantong.qknow.mcp.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.mcp.server.security.McpTransientLeaseManager;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 107 McpTransientLeaseManager 瞬态租约管理器契约测试")
class McpTransientLeaseManagerTest {

    @Test
    @DisplayName("测试租约正常签发与单次核销成功")
    void testIssueAndConsumeLeaseSuccess() {
        McpTransientLeaseManager leaseManager = new McpTransientLeaseManager();
        String token = leaseManager.issueLease("qknow_sandbox_run");

        assertNotNull(token, "签发的租约 Token 不得为空");
        assertTrue(token.startsWith("lease_"), "租约 Token 必须以 lease_ 开头");

        // 首次核销必须成功
        boolean consumed = leaseManager.consumeLease("qknow_sandbox_run", token);
        assertTrue(consumed, "首次合法消费租约必须成功");

        // 再次核销必须失败 (CAS 一次性消费防重放)
        boolean replayed = leaseManager.consumeLease("qknow_sandbox_run", token);
        assertFalse(replayed, "已被消费的租约再次核销必须失败 (防重放)");
    }

    @Test
    @DisplayName("测试工具名称不匹配导致租约核销失败")
    void testMismatchedToolNameFails() {
        McpTransientLeaseManager leaseManager = new McpTransientLeaseManager();
        String token = leaseManager.issueLease("qknow_sandbox_run");

        // 试图用于其他高危工具
        boolean consumed = leaseManager.consumeLease("qknow_sql_update", token);
        assertFalse(consumed, "租约目标工具不匹配必须拒绝核销");
    }

    @Test
    @DisplayName("测试不存在或伪造的租约核销直接拒绝")
    void testFakeLeaseRejected() {
        McpTransientLeaseManager leaseManager = new McpTransientLeaseManager();
        boolean consumed = leaseManager.consumeLease("qknow_sandbox_run", "lease_fake_token_12345");
        assertFalse(consumed, "伪造的不存在租约必须拒绝核销");
    }
}
