package tech.qiantong.qknow.mcp.core.model;

import java.util.List;

/**
 * 工具调用执行结果
 */
public record CallToolResult(
    List<ToolContentItem> content,
    boolean isError
) {
    public static CallToolResult text(String text) {
        return new CallToolResult(List.of(new ToolContentItem("text", text)), false);
    }

    public static CallToolResult error(String errorMsg) {
        return new CallToolResult(List.of(new ToolContentItem("text", errorMsg)), true);
    }

    public String getFirstText() {
        if (content != null && !content.isEmpty()) {
            return content.get(0).text();
        }
        return "";
    }
}
