package tech.qiantong.qknow.mcp.core.model;

/**
 * MCP 资源元数据契约
 */
public record McpResource(
    String uri,
    String name,
    String description,
    String mimeType,
    Long size
) {}
