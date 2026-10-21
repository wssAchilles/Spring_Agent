package tech.qiantong.qknow.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.mcp.server.transport.McpHttpAuthGuard;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P1 回归防护：原生 MCP Server HTTP 端点默认关闭，启用后必须令牌鉴权，
 * 且令牌比较使用常量时间实现。
 */
class McpServerEndpointAuthTest {

    private static final String TOKEN = "mcp-unit-test-token-2026";

    @Test
    @DisplayName("默认关闭：未启用时返回 DISABLED")
    void disabledByDefault() {
        assertEquals(McpHttpAuthGuard.Decision.DISABLED,
                new McpHttpAuthGuard(false, TOKEN).check("Bearer " + TOKEN));
    }

    @Test
    @DisplayName("启用但未配置令牌：拒绝访问")
    void enabledWithoutTokenMustFailClosed() {
        assertEquals(McpHttpAuthGuard.Decision.CONFIG_MISSING,
                new McpHttpAuthGuard(true, "").check("Bearer " + TOKEN));
    }

    @Test
    @DisplayName("合法 Bearer 令牌：允许访问")
    void validBearerTokenAllowed() {
        assertEquals(McpHttpAuthGuard.Decision.ALLOWED,
                new McpHttpAuthGuard(true, TOKEN).check("Bearer " + TOKEN));
    }

    @Test
    @DisplayName("缺失或错误令牌：拒绝访问")
    void missingOrWrongTokenRejected() {
        assertEquals(McpHttpAuthGuard.Decision.UNAUTHORIZED,
                new McpHttpAuthGuard(true, TOKEN).check(null));
        assertEquals(McpHttpAuthGuard.Decision.UNAUTHORIZED,
                new McpHttpAuthGuard(true, TOKEN).check("Bearer wrong-token"));
        assertEquals(McpHttpAuthGuard.Decision.UNAUTHORIZED,
                new McpHttpAuthGuard(true, TOKEN).check("Bearer "));
    }
}
