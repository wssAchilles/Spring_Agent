package tech.qiantong.qknow.mcp.server.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.model.*;
import tech.qiantong.qknow.mcp.core.protocol.*;
import tech.qiantong.qknow.mcp.server.annotation.McpTool.RiskLevel;
import tech.qiantong.qknow.mcp.server.model.McpServerExportReceipt;
import tech.qiantong.qknow.mcp.server.security.QuadDefenseSecurityPipeline;
import tech.qiantong.qknow.mcp.server.security.QuadDefenseSecurityPipeline.SecurityPipelineException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * MCP 服务端统一注册与请求分发路由中心 (强化版：内嵌四级纵深防御与数字存证)
 */
public class McpServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpServerRegistry.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String serverName;
    private final String serverVersion;
    private final QuadDefenseSecurityPipeline securityPipeline;

    private final Map<String, ToolHandler> tools = new ConcurrentHashMap<>();
    private final Map<String, ResourceHandler> resources = new ConcurrentHashMap<>();
    private final Map<String, PromptHandler> prompts = new ConcurrentHashMap<>();

    public record ToolHandler(
            McpTool metadata,
            RiskLevel riskLevel,
            boolean requiresLease,
            Function<Map<String, Object>, CallToolResult> executor
    ) {}

    public record ResourceHandler(McpResource metadata, Function<String, ResourceContent> executor) {}
    public record PromptHandler(McpPrompt metadata, Function<Map<String, Object>, List<PromptMessage>> executor) {}

    public McpServerRegistry(String serverName, String serverVersion) {
        this(serverName, serverVersion, null);
    }

    public McpServerRegistry(String serverName, String serverVersion, QuadDefenseSecurityPipeline securityPipeline) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.securityPipeline = securityPipeline;
    }

    public void registerTool(String name, String description, Map<String, Object> inputSchema,
                             Function<Map<String, Object>, CallToolResult> executor) {
        RiskLevel risk = RiskLevel.SAFE;
        boolean reqLease = false;
        if (inputSchema != null) {
            if (Boolean.TRUE.equals(inputSchema.get("requiresLease"))) {
                reqLease = true;
                risk = RiskLevel.HIGH_RISK;
            }
            if (inputSchema.containsKey("riskLevel")) {
                Object r = inputSchema.get("riskLevel");
                if (r instanceof RiskLevel rl) {
                    risk = rl;
                } else if ("HIGH_RISK".equalsIgnoreCase(String.valueOf(r))) {
                    risk = RiskLevel.HIGH_RISK;
                }
            }
        }
        registerTool(name, description, inputSchema, risk, reqLease, executor);
    }

    public void registerTool(String name, String description, Map<String, Object> inputSchema,
                             RiskLevel riskLevel, boolean requiresLease,
                             Function<Map<String, Object>, CallToolResult> executor) {
        Map<String, Object> schemaWithMeta = new HashMap<>(inputSchema != null ? inputSchema : Map.of());
        schemaWithMeta.put("requiresLease", requiresLease);
        schemaWithMeta.put("riskLevel", riskLevel.name());

        McpTool tool = new McpTool(name, description, schemaWithMeta);
        tools.put(name, new ToolHandler(tool, riskLevel, requiresLease, executor));
        log.info("[MCP Server] 成功注册工具: {} (风险等级: {}, 需租约: {})", name, riskLevel, requiresLease);
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
     * 签发当前服务导出不可变存证凭单
     */
    public McpServerExportReceipt issueExportReceipt(String transportChannel) {
        List<McpTool> toolList = tools.values().stream().map(ToolHandler::metadata).toList();
        return McpServerExportReceipt.create(
                serverName,
                serverVersion,
                transportChannel,
                toolList,
                resources.size(),
                prompts.size(),
                securityPipeline != null
        );
    }

    /**
     * 核心 JSON-RPC 2.0 请求分发与四道防线拦截处理器
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

                    // 四道防线护航审查
                    if (securityPipeline != null) {
                        // 防线二：租约校验
                        securityPipeline.verifyLeaseIfRequired(toolName, handler.riskLevel(), handler.requiresLease(), arguments);
                        // 防线三：入参提示词注入审查
                        securityPipeline.sanitizeInboundArguments(toolName, arguments);
                    }

                    // 执行真实底层工具调用
                    CallToolResult result = handler.executor().apply(arguments);

                    // 防线三：出参脱敏与间接提示词注入净化
                    if (securityPipeline != null) {
                        result = securityPipeline.sanitizeOutboundResult(toolName, result);
                    }

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
        } catch (SecurityPipelineException spe) {
            log.warn("[MCP Server] 安全流水线强力拦截: code={}, msg={}", spe.getErrorCode(), spe.getMessage());
            return JsonRpcResponse.error(reqId, spe.getErrorCode(), spe.getMessage(), null);
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
