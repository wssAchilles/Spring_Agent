package tech.qiantong.qknow.mcp.server.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.model.*;
import tech.qiantong.qknow.mcp.core.protocol.*;
import tech.qiantong.qknow.mcp.server.annotation.McpPromptMapping;
import tech.qiantong.qknow.mcp.server.annotation.McpResourceMapping;
import tech.qiantong.qknow.mcp.server.annotation.McpToolMapping;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * MCP 服务端统一注册与请求分发路由中心
 */
public class McpServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpServerRegistry.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String serverName;
    private final String serverVersion;

    private final Map<String, ToolHandler> tools = new ConcurrentHashMap<>();
    private final Map<String, ResourceHandler> resources = new ConcurrentHashMap<>();
    private final Map<String, PromptHandler> prompts = new ConcurrentHashMap<>();

    public record ToolHandler(McpTool metadata, Function<Map<String, Object>, CallToolResult> executor) {}
    public record ResourceHandler(McpResource metadata, Function<String, ResourceContent> executor) {}
    public record PromptHandler(McpPrompt metadata, Function<Map<String, Object>, List<PromptMessage>> executor) {}

    public McpServerRegistry(String serverName, String serverVersion) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
    }

    public void registerTool(String name, String description, Map<String, Object> inputSchema,
                             Function<Map<String, Object>, CallToolResult> executor) {
        McpTool tool = new McpTool(name, description, inputSchema);
        tools.put(name, new ToolHandler(tool, executor));
        log.info("[MCP Server] 成功注册工具: {}", name);
    }

    public void registerResource(String uriPattern, String name, String description, String mimeType,
                                 Function<String, ResourceContent> executor) {
        McpResource res = new McpResource(uriPattern, name, description, mimeType, null);
        resources.put(uriPattern, new ResourceHandler(res, executor));
        log.info("[MCP Server] 成功注册资源: {}", uriPattern);
    }

    public void registerPrompt(String name, String description, List<PromptArgument> arguments,
                               Function<Map<String, Object>, List<PromptMessage>> executor) {
        McpPrompt prompt = new McpPrompt(name, description, arguments);
        prompts.put(name, new PromptHandler(prompt, executor));
        log.info("[MCP Server] 成功注册提示词模版: {}", name);
    }

    /**
     * 核心 JSON-RPC 2.0 请求分发处理器
     */
    public JsonRpcResponse handleRequest(JsonRpcRequest request) {
        if (request == null || request.method() == null) {
            return JsonRpcResponse.error(null, JsonRpcError.INVALID_REQUEST, "请求无效或方法为空", null);
        }

        Object reqId = request.id();
        String method = request.method();
        Map<String, Object> params = request.params() != null ? request.params() : Map.of();

        try {
            switch (method) {
                case "initialize" -> {
                    InitializeResult initResult = InitializeResult.standard(serverName, serverVersion);
                    return JsonRpcResponse.success(reqId, initResult);
                }
                case "tools/list" -> {
                    List<McpTool> toolList = tools.values().stream().map(ToolHandler::metadata).toList();
                    return JsonRpcResponse.success(reqId, Map.of("tools", toolList));
                }
                case "tools/call" -> {
                    String toolName = (String) params.get("name");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
                    if (arguments == null) {
                        arguments = Map.of();
                    }

                    ToolHandler handler = tools.get(toolName);
                    if (handler == null) {
                        return JsonRpcResponse.error(reqId, JsonRpcError.METHOD_NOT_FOUND, "未找到目标工具: " + toolName, null);
                    }

                    CallToolResult result = handler.executor().apply(arguments);
                    return JsonRpcResponse.success(reqId, result);
                }
                case "resources/list" -> {
                    List<McpResource> resList = resources.values().stream().map(ResourceHandler::metadata).toList();
                    return JsonRpcResponse.success(reqId, Map.of("resources", resList));
                }
                case "resources/read" -> {
                    String uri = (String) params.get("uri");
                    ResourceHandler handler = findResourceHandler(uri);
                    if (handler == null) {
                        return JsonRpcResponse.error(reqId, JsonRpcError.INVALID_PARAMS, "未找到目标资源 URI: " + uri, null);
                    }

                    ResourceContent content = handler.executor().apply(uri);
                    return JsonRpcResponse.success(reqId, Map.of("contents", List.of(content)));
                }
                case "prompts/list" -> {
                    List<McpPrompt> promptList = prompts.values().stream().map(PromptHandler::metadata).toList();
                    return JsonRpcResponse.success(reqId, Map.of("prompts", promptList));
                }
                case "prompts/get" -> {
                    String promptName = (String) params.get("name");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> promptArgs = (Map<String, Object>) params.get("arguments");
                    if (promptArgs == null) {
                        promptArgs = Map.of();
                    }

                    PromptHandler handler = prompts.get(promptName);
                    if (handler == null) {
                        return JsonRpcResponse.error(reqId, JsonRpcError.METHOD_NOT_FOUND, "未找到目标提示词: " + promptName, null);
                    }

                    List<PromptMessage> messages = handler.executor().apply(promptArgs);
                    return JsonRpcResponse.success(reqId, Map.of("messages", messages));
                }
                default -> {
                    return JsonRpcResponse.error(reqId, JsonRpcError.METHOD_NOT_FOUND, "不支持的方法: " + method, null);
                }
            }
        } catch (SecurityException se) {
            log.warn("[MCP Server] 安全审查拦截: {}", se.getMessage());
            return JsonRpcResponse.error(reqId, JsonRpcError.SECURITY_VIOLATION, "安全拦截: " + se.getMessage(), null);
        } catch (Exception ex) {
            log.error("[MCP Server] 执行异常: {}", ex.getMessage(), ex);
            return JsonRpcResponse.error(reqId, JsonRpcError.INTERNAL_ERROR, "内部服务异常: " + ex.getMessage(), null);
        }
    }

    private ResourceHandler findResourceHandler(String uri) {
        if (uri == null) return null;
        if (resources.containsKey(uri)) {
            return resources.get(uri);
        }
        for (Map.Entry<String, ResourceHandler> entry : resources.entrySet()) {
            String pattern = entry.getKey();
            if (matchesUriTemplate(pattern, uri)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean matchesUriTemplate(String template, String uri) {
        String regex = template.replaceAll("\\{[^/]+?\\}", "[^/]+");
        return uri.matches(regex);
    }

    public List<McpTool> listTools() {
        return tools.values().stream().map(ToolHandler::metadata).toList();
    }

    public List<McpResource> listResources() {
        return resources.values().stream().map(ResourceHandler::metadata).toList();
    }

    public List<McpPrompt> listPrompts() {
        return prompts.values().stream().map(PromptHandler::metadata).toList();
    }
}
