package tech.qiantong.qknow.mcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * MCP initialize 握手响应结果
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpInitializeResult(
        String protocolVersion,
        Map<String, Object> capabilities,
        McpServerInfo serverInfo
) {
}
