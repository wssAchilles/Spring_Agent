package tech.qiantong.qknow.mcp.core.model;

import java.util.Map;

/**
 * MCP 工具定义元数据
 */
public record McpTool(
    String name,
    String description,
    Map<String, Object> inputSchema
) {}
