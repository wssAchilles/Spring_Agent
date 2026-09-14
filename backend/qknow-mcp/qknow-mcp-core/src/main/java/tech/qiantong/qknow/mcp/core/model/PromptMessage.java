package tech.qiantong.qknow.mcp.core.model;

/**
 * 结构化提示词消息
 */
public record PromptMessage(
    String role,
    PromptContent content
) {}
