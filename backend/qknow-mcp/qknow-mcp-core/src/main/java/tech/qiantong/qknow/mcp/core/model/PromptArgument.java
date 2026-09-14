package tech.qiantong.qknow.mcp.core.model;

/**
 * 提示词模版入参定义
 */
public record PromptArgument(
    String name,
    String description,
    boolean required
) {}
