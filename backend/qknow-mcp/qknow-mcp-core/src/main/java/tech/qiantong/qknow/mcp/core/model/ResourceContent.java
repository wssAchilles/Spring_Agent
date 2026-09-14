package tech.qiantong.qknow.mcp.core.model;

/**
 * 资源载荷内容
 */
public record ResourceContent(
    String uri,
    String mimeType,
    String text,
    String blob
) {}
