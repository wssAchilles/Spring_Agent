package tech.qiantong.qknow.hermes.tool.mcp;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具适配器
 * 将外部 MCP Server 的工具转换为 Spring AI FunctionToolCallback
 *
 * MCP (Model Context Protocol) 是一种标准协议，用于连接 LLM 与外部工具/数据源
 *
 * TODO: 原始 import tech.qiantong.qknow.module.kb.dal.dataobject.tool.KbToolMethodDO 已移除（未实际使用）
 */
@Slf4j
@Component
public class McpToolAdapter {

    private final Map<String, McpServerConfig> serverConfigs = new ConcurrentHashMap<>();
    private final Map<String, FunctionToolCallback<?, ?>> mcpTools = new ConcurrentHashMap<>();

    @Data
    public static class McpServerConfig {
        private String name;
        private String url;
        private String apiKey;
        private Map<String, String> headers;
        private boolean enabled;
    }

    /**
     * 注册 MCP Server 配置
     */
    public void registerServer(McpServerConfig config) {
        serverConfigs.put(config.getName(), config);
        log.info("注册 MCP Server: {} ({})", config.getName(), config.getUrl());
    }

    /**
     * 移除 MCP Server
     */
    public void unregisterServer(String name) {
        serverConfigs.remove(name);
        // 移除该 server 的所有工具
        mcpTools.entrySet().removeIf(entry -> entry.getKey().startsWith(name + "."));
        log.info("移除 MCP Server: {}", name);
    }

    /**
     * 获取所有已注册的 MCP Server 配置
     */
    public Map<String, McpServerConfig> getServerConfigs() {
        return Collections.unmodifiableMap(serverConfigs);
    }

    /**
     * 获取所有 MCP 工具
     */
    public Map<String, FunctionToolCallback<?, ?>> getMcpTools() {
        return Collections.unmodifiableMap(mcpTools);
    }

    /**
     * 检查工具方法是否为 MCP 工具
     * MCP 工具的 code 格式为: mcp.<serverName>.<toolName>
     */
    public boolean isMcpTool(String code) {
        return code != null && code.startsWith("mcp.");
    }

    /**
     * 解析 MCP 工具名称
     * @return [serverName, toolName]
     */
    public String[] parseMcpToolName(String code) {
        if (!isMcpTool(code)) return null;
        String[] parts = code.split("\\.", 3);
        if (parts.length < 3) return null;
        return new String[]{parts[1], parts[2]};
    }

    /**
     * 创建 MCP 工具的 FunctionToolCallback
     * 这是一个占位实现，实际的 MCP 协议通信需要根据具体 server 实现
     */
    public FunctionToolCallback<McpToolRequest, String> createMcpToolCallback(
            String serverName, String toolName, String description) {

        return FunctionToolCallback.builder(
                "mcp." + serverName + "." + toolName,
                new McpToolFunction(serverName, toolName))
                .inputType(McpToolRequest.class)
                .description(description != null ? description : "MCP 工具: " + toolName)
                .build();
    }

    /**
     * MCP 工具请求对象
     */
    @Data
    public static class McpToolRequest {
        private Map<String, Object> params;
    }

    /**
     * MCP 工具执行函数
     */
    @lombok.AllArgsConstructor
    private static class McpToolFunction implements java.util.function.Function<McpToolRequest, String> {
        private final String serverName;
        private final String toolName;

        @Override
        public String apply(McpToolRequest request) {
            // TODO: 实现实际的 MCP 协议通信
            // 这里是占位实现，实际需要:
            // 1. 连接到 MCP Server (HTTP/SSE)
            // 2. 发送 tools/call 请求
            // 3. 解析响应
            log.info("调用 MCP 工具: {}.{}, 参数: {}", serverName, toolName, request.getParams());
            return "MCP 工具 " + serverName + "." + toolName + " 执行成功 (占位响应)";
        }
    }
}
