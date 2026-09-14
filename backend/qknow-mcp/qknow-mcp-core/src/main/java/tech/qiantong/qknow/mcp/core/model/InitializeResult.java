package tech.qiantong.qknow.mcp.core.model;

import java.util.Map;

/**
 * MCP 初始化握手协商结果
 */
public record InitializeResult(
    String protocolVersion,
    Map<String, Object> capabilities,
    Map<String, Object> serverInfo
) {
    public static InitializeResult standard(String serverName, String serverVersion) {
        return new InitializeResult(
            "2024-11-05",
            Map.of(
                "tools", Map.of("listChanged", true),
                "resources", Map.of("subscribe", false, "listChanged", true),
                "prompts", Map.of("listChanged", true)
            ),
            Map.of("name", serverName, "version", serverVersion)
        );
    }
}
