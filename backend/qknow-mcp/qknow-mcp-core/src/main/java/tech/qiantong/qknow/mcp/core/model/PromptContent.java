package tech.qiantong.qknow.mcp.core.model;

/**
 * 提示词内容对象
 */
public record PromptContent(
    String type,
    String text,
    ResourceContent resource
) {
    public static PromptContent text(String text) {
        return new PromptContent("text", text, null);
    }
}
