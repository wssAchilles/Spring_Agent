package tech.qiantong.qknow.mcp.client.lifecycle;

import java.util.List;
import java.util.Map;

/**
 * 自省健康与发现报告（对标 claw mcp / claw doctor）
 */
public record McpDiscoveryReport(
        int totalServers,
        int activeServers,
        int totalTools,
        boolean degraded,
        List<McpServerState> serverStates,
        List<McpErrorSurface> failures,
        Map<String, String> summary
) {
}
