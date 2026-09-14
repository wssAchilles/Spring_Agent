package tech.qiantong.qknow.mcp.client.lifecycle;

import tech.qiantong.qknow.mcp.core.model.McpResource;
import tech.qiantong.qknow.mcp.core.model.McpTool;

import java.util.List;

/**
 * 追踪单个 MCP Server 节点的连接与健康状态
 */
public record McpServerState(
        String serverName,
        McpLifecyclePhase currentPhase,
        boolean connected,
        boolean required,
        List<McpTool> tools,
        List<McpResource> resources,
        McpErrorSurface lastError
) {
}
