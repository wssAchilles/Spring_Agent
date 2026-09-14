package tech.qiantong.qknow.mcp.client.lifecycle;

import java.time.Instant;
import java.util.Map;

/**
 * 富错误上下文结构体（借鉴 claw-code McpErrorSurface 设计）
 */
public record McpErrorSurface(
        McpLifecyclePhase phase,
        String serverName,
        String message,
        Map<String, String> context,
        boolean recoverable,
        long timestamp
) {
    public static McpErrorSurface of(McpLifecyclePhase phase, String serverName, String message, Map<String, String> context, boolean recoverable) {
        return new McpErrorSurface(phase, serverName, message, context != null ? context : Map.of(), recoverable, Instant.now().toEpochMilli());
    }
}
