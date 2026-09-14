package tech.qiantong.qknow.mcp.core.model;

import java.util.List;

/**
 * MCP 提示词模版元数据
 */
public record McpPrompt(
    String name,
    String description,
    List<PromptArgument> arguments
) {}
