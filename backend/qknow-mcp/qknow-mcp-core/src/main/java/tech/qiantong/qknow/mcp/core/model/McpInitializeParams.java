package tech.qiantong.qknow.mcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * MCP initialize 握手请求参数（遵循 Anthropic MCP 协议规范与 claw-code 设计）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpInitializeParams(
        String protocolVersion,
        Map<String, Object> capabilities,
        McpClientInfo clientInfo
) {
}
