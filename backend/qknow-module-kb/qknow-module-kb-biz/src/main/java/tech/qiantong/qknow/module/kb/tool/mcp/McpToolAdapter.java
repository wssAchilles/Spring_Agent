package tech.qiantong.qknow.module.kb.tool.mcp;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.tool.mcp.McpClient;
import tech.qiantong.qknow.hermes.tool.mcp.McpException;
import tech.qiantong.qknow.hermes.tool.mcp.StdioMcpClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具适配器
 * 将外部 MCP Server 的工具转换为 Spring AI FunctionToolCallback
 *
 * MCP (Model Context Protocol) 是一种标准协议，用于连接 LLM 与外部工具/数据源
 */
@Slf4j
@Component
public class McpToolAdapter {

    private final Map<String, McpServerConfig> serverConfigs = new ConcurrentHashMap<>();
    private final Map<String, FunctionToolCallback<?, ?>> mcpTools = new ConcurrentHashMap<>();
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();

    @Data
    public static class McpServerConfig {
        private String name;
        private String url;
        private String apiKey;
        private Map<String, String> headers;
        private List<String> command;
        private Map<String, String> environment;
        private boolean enabled;
    }

    /**
     * 注册 MCP Server 配置
     */
    public void registerServer(McpServerConfig config) {
        serverConfigs.put(config.getName(), config);
        log.info("注册 MCP Server: {} ({})", config.getName(), config.getUrl());

        if (!config.isEnabled()) {
            return;
        }

        try {
            McpClient client = getClient(config.getName());
            if (client == null && config.getCommand() != null && !config.getCommand().isEmpty()) {
                client = createClient(config);
                clients.put(config.getName(), client);
            }
            if (client == null) {
                return;
            }

            client.initialize();
            for (JSONObject toolDef : client.listTools()) {
                String toolName = toolDef.getString("name");
                String description = toolDef.getString("description");
                mcpTools.put("mcp." + config.getName() + "." + toolName,
                        createMcpToolCallback(config.getName(), toolName, description));
            }
        } catch (McpException e) {
            log.warn("MCP Server {} 连接失败: {}", config.getName(), e.getMessage());
        } catch (Exception e) {
            log.error("MCP Server {} 注册异常", config.getName(), e);
        }
    }

    public McpClient getClient(String serverName) {
        return clients.get(serverName);
    }

    protected McpClient createClient(McpServerConfig config) {
        return new StdioMcpClient(config.getCommand(), config.getEnvironment());
    }

    /**
     * 移除 MCP Server
     */
    public void unregisterServer(String name) {
        McpClient client = clients.remove(name);
        if (client != null) {
            client.disconnect();
        }
        serverConfigs.remove(name);
        // 移除该 server 的所有工具
        mcpTools.entrySet().removeIf(entry -> entry.getKey().startsWith("mcp." + name + "."));
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
                new McpToolFunction(this, serverName, toolName))
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
    private static class McpToolFunction implements java.util.function.Function<McpToolRequest, String> {
        private final McpToolAdapter adapter;
        private final String serverName;
        private final String toolName;

        McpToolFunction(McpToolAdapter adapter, String serverName, String toolName) {
            this.adapter = adapter;
            this.serverName = serverName;
            this.toolName = toolName;
        }

        @Override
        public String apply(McpToolRequest request) {
            log.info("调用 MCP 工具: {}.{}, 参数: {}", serverName, toolName, request.getParams());
            McpClient client = adapter.getClient(serverName);
            if (client == null) {
                return "{\"error\": \"MCP 工具 " + serverName + "." + toolName + " 未连接\"}";
            }
            try {
                Map<String, Object> arguments = request.getParams() != null ? request.getParams() : Map.of();
                return client.callTool(toolName, arguments).toJSONString();
            } catch (McpException e) {
                log.error("MCP 工具调用失败: {}.{}", serverName, toolName, e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }
    }
}
