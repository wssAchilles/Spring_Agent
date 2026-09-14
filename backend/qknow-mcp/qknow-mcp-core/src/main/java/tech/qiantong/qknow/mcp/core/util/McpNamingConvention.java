package tech.qiantong.qknow.mcp.core.util;

import java.util.regex.Pattern;

/**
 * MCP 命名空间与工具名称清洗规整引擎（借鉴 claw-code mcp.rs 双下划线命名空间设计）
 */
public final class McpNamingConvention {

    public static final String MCP_PREFIX = "mcp__";
    public static final String SEPARATOR = "__";
    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-zA-Z0-9_-]");

    private McpNamingConvention() {}

    /**
     * 清洗名称中的非法字符，仅保留合规字符 [a-zA-Z0-9_-]
     */
    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        String normalized = INVALID_CHARS.matcher(name).replaceAll("_");
        // 折叠连续下划线
        normalized = normalized.replaceAll("_{2,}", "_");
        // 去除首尾可能因替换产生的下划线
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "unnamed" : normalized;
    }

    /**
     * 构建合格工具名称：mcp__{serverName}__{rawToolName}
     */
    public static String buildQualifiedToolName(String serverName, String rawToolName) {
        String normalizedServer = normalizeName(serverName);
        String normalizedTool = normalizeName(rawToolName);
        return MCP_PREFIX + normalizedServer + SEPARATOR + normalizedTool;
    }

    /**
     * 从合格工具名称中反解出目标 Server 与原始 ToolRoute
     */
    public static ToolRoute parseToolRoute(String qualifiedToolName) {
        if (qualifiedToolName == null || !qualifiedToolName.startsWith(MCP_PREFIX)) {
            return new ToolRoute("default", qualifiedToolName != null ? qualifiedToolName : "");
        }
        String body = qualifiedToolName.substring(MCP_PREFIX.length());
        int sepIndex = body.indexOf(SEPARATOR);
        if (sepIndex < 0) {
            return new ToolRoute("default", body);
        }
        String server = body.substring(0, sepIndex);
        String tool = body.substring(sepIndex + SEPARATOR.length());
        return new ToolRoute(server, tool);
    }

    /**
     * 工具路由结构体
     */
    public record ToolRoute(String serverName, String rawToolName) {}
}
