package tech.qiantong.qknow.hermes.tool.mcp;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
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
 */
@Slf4j
@Component
public class McpToolAdapter {

    private final Map<String, McpServerConfig> serverConfigs = new ConcurrentHashMap<>();
    private final Map<String, FunctionToolCallback<?, ?>> mcpTools = new ConcurrentHashMap<>();
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeGithubServer() {
        String token = System.getenv("GITHUB_PERSONAL_ACCESS_TOKEN");
        if (token != null && !token.isBlank()) {
            registerServer(githubServerConfig(token));
        }
    }

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
     * 获取指定 server 的 MCP 客户端
     */
    public McpClient getClient(String serverName) {
        return clients.get(serverName);
    }

    /**
     * 注册 MCP Server 配置
     */
    public void registerServer(McpServerConfig config) {
        serverConfigs.put(config.getName(), config);
        log.info("注册 MCP Server: {} ({})", config.getName(), config.getUrl());

        // 未启用则跳过连接
        if (!config.isEnabled()) {
            return;
        }

        try {
            // 获取或创建客户端并初始化
            McpClient client = getClient(config.getName());
            if (client == null && config.getCommand() != null && !config.getCommand().isEmpty()) {
                client = createClient(config);
                clients.put(config.getName(), client);
            }
            if (client != null) {
                client.initialize();

                // 发现工具
                List<JSONObject> toolDefs = client.listTools();
                for (JSONObject toolDef : toolDefs) {
                    String toolName = toolDef.getString("name");
                    String description = toolDef.getString("description");
                    String fullKey = config.getName() + "." + toolName;
                    FunctionToolCallback<McpToolRequest, String> callback =
                            createMcpToolCallback(config.getName(), toolName, description);
                    mcpTools.put("mcp." + fullKey, callback);
                }

                log.info("MCP Server {} 注册成功，发现 {} 个工具", config.getName(), toolDefs.size());
            }
        } catch (McpException e) {
            log.warn("MCP Server {} 连接失败: {}", config.getName(), e.getMessage());
        } catch (Exception e) {
            log.error("MCP Server {} 注册异常", config.getName(), e);
        }
    }

    /**
     * 根据配置创建 stdio MCP 客户端。
     */
    protected McpClient createClient(McpServerConfig config) {
        return new StdioMcpClient(config.getCommand(), config.getEnvironment());
    }

    McpServerConfig githubServerConfig(String token) {
        McpServerConfig config = new McpServerConfig();
        config.setName("github");
        config.setCommand(List.of("npx", "-y", "@modelcontextprotocol/server-github"));
        config.setEnvironment(Map.of("GITHUB_PERSONAL_ACCESS_TOKEN", token));
        config.setEnabled(true);
        return config;
    }

    /**
     * 移除 MCP Server
     */
    public void unregisterServer(String name) {
        McpClient client = getClient(name);
        if (client != null) {
            client.disconnect();
        }

        clients.remove(name);
        serverConfigs.remove(name);

        // 移除该 server 的所有工具
        String prefix = "mcp." + name + ".";
        mcpTools.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));

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
     *
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
            McpClient client = adapter.getClient(serverName);
            if (client == null) {
                log.warn("MCP 客户端未连接: {}", serverName);
                return "{\"error\": \"MCP 工具 " + serverName + "." + toolName + " 未连接\"}";
            }

            try {
                Map<String, Object> arguments = request.getParams() != null ? request.getParams() : Map.of();
                JSONObject result = client.callTool(toolName, arguments);
                return result.toJSONString();
            } catch (McpException e) {
                log.error("MCP 工具调用失败: {}.{}", serverName, toolName, e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }
    }
}
