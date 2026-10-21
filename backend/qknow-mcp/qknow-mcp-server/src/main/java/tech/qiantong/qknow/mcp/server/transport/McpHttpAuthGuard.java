package tech.qiantong.qknow.mcp.server.transport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 原生 MCP Server HTTP 端点鉴权守卫。
 * 纯逻辑实现，便于单元测试；控制器仅负责 HTTP 状态映射。
 */
public final class McpHttpAuthGuard {

    public enum Decision {
        /** 允许访问 */
        ALLOWED,
        /** 端点未启用 */
        DISABLED,
        /** 已启用但未配置访问令牌 */
        CONFIG_MISSING,
        /** 令牌缺失或不匹配 */
        UNAUTHORIZED
    }

    private final boolean enabled;
    private final String authToken;

    public McpHttpAuthGuard(boolean enabled, String authToken) {
        this.enabled = enabled;
        this.authToken = authToken;
    }

    public Decision check(String authorizationHeader) {
        if (!enabled) {
            return Decision.DISABLED;
        }
        if (authToken == null || authToken.isBlank()) {
            return Decision.CONFIG_MISSING;
        }
        String provided = extractBearerToken(authorizationHeader);
        // 常量时间比较，避免令牌比较的时序侧信道
        boolean matched = provided != null && MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                authToken.getBytes(StandardCharsets.UTF_8));
        return matched ? Decision.ALLOWED : Decision.UNAUTHORIZED;
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }
}
