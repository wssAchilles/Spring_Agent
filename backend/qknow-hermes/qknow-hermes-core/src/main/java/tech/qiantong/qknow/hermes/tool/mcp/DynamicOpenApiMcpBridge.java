package tech.qiantong.qknow.hermes.tool.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * 企业级 OpenAPI 3.0 / Swagger 动态 MCP 网关 (DynamicOpenApiMcpBridge)
 * <p>
 * 核心职责：
 * 1. 零代码解析企业已有 OpenAPI 3.0 / Swagger JSON 规范；
 * 2. 自动化将每个 HTTP API 映射并暴露为符合 Anthropic MCP 协议规范的标准 Tool (含完整 JSON Schema 入参校验)；
 * 3. 拦截 Agent 产出的工具调用入参，动态组装并物理分发至企业后端 RESTful 服务，实现即插即用的企业工具生态。
 * </p>
 */
@Slf4j
public class DynamicOpenApiMcpBridge {

    private final String serverBaseUrl;
    private final Map<String, OpenApiEndpointMeta> endpointRegistry = new HashMap<>();
    private final HttpClient httpClient;

    public record OpenApiEndpointMeta(
            String toolName,
            String description,
            String httpMethod,
            String pathTemplate,
            JSONObject inputSchema
    ) {}

    public DynamicOpenApiMcpBridge(String serverBaseUrl) {
        this.serverBaseUrl = serverBaseUrl.endsWith("/") ? serverBaseUrl.substring(0, serverBaseUrl.length() - 1) : serverBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 解析 OpenAPI 3.0 / Swagger JSON 文档并注册全部端点为 MCP 工具
     *
     * @param openApiJsonDoc OpenAPI 规范 JSON 字符串
     * @return 成功转换并注册的 MCP Tool 列表 (符合 tools/list 规范)
     */
    public List<JSONObject> registerOpenApiSpec(String openApiJsonDoc) {
        List<JSONObject> mcpTools = new ArrayList<>();
        if (openApiJsonDoc == null || openApiJsonDoc.isBlank()) {
            return mcpTools;
        }

        try {
            JSONObject root = JSON.parseObject(openApiJsonDoc);
            JSONObject paths = root.getJSONObject("paths");
            if (paths == null) {
                return mcpTools;
            }

            for (String path : paths.keySet()) {
                JSONObject pathItem = paths.getJSONObject(path);
                for (String httpMethod : pathItem.keySet()) {
                    String methodUpper = httpMethod.toUpperCase();
                    if (!List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(methodUpper)) {
                        continue;
                    }

                    JSONObject operation = pathItem.getJSONObject(httpMethod);
                    String operationId = operation.getString("operationId");
                    String summary = operation.getString("summary");
                    String description = operation.getString("description");
                    if (description == null || description.isBlank()) {
                        description = summary != null ? summary : ("调用 " + methodUpper + " " + path);
                    }

                    // 构建唯一的规范化 MCP 工具名称: mcp__openapi__{operationId 或 path}
                    String toolName = sanitizeToolName(operationId, methodUpper, path);

                    // 构建标准 JSON Schema 入参契约
                    JSONObject inputSchema = buildInputSchema(operation);

                    OpenApiEndpointMeta meta = new OpenApiEndpointMeta(
                            toolName,
                            description,
                            methodUpper,
                            path,
                            inputSchema
                    );
                    endpointRegistry.put(toolName, meta);

                    // 封装为 MCP 规范 Tool 对象
                    JSONObject toolDef = new JSONObject();
                    toolDef.put("name", toolName);
                    toolDef.put("description", description);
                    toolDef.put("inputSchema", inputSchema);
                    mcpTools.add(toolDef);
                }
            }
            log.info("成功从 OpenAPI 规范动态解析并注册 {} 个 MCP 工具", mcpTools.size());
        } catch (Exception e) {
            log.error("解析 OpenAPI 规范失败", e);
        }
        return mcpTools;
    }

    /**
     * 物理分发执行已注册的 OpenAPI MCP 工具
     */
    public String executeTool(String toolName, Map<String, Object> arguments) {
        OpenApiEndpointMeta meta = endpointRegistry.get(toolName);
        if (meta == null) {
            return "{\"status\":\"ERROR\",\"message\":\"未注册的 OpenAPI 工具: " + toolName + "\"}";
        }

        try {
            Map<String, Object> safeArgs = arguments != null ? new HashMap<>(arguments) : new HashMap<>();
            String targetPath = meta.pathTemplate();

            // 1. 替换路径参数 {paramName}
            for (Map.Entry<String, Object> entry : new HashMap<>(safeArgs).entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (targetPath.contains(placeholder)) {
                    targetPath = targetPath.replace(placeholder, String.valueOf(entry.getValue()));
                    safeArgs.remove(entry.getKey());
                }
            }

            String fullUrl = serverBaseUrl + targetPath;
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30));

            if ("GET".equalsIgnoreCase(meta.httpMethod())) {
                StringBuilder queryParams = new StringBuilder();
                for (Map.Entry<String, Object> entry : safeArgs.entrySet()) {
                    if (queryParams.isEmpty()) queryParams.append("?");
                    else queryParams.append("&");
                    queryParams.append(entry.getKey()).append("=").append(entry.getValue());
                }
                fullUrl += queryParams.toString();
                reqBuilder.uri(URI.create(fullUrl)).GET();
            } else {
                reqBuilder.uri(URI.create(fullUrl))
                        .header("Content-Type", "application/json")
                        .method(meta.httpMethod(), HttpRequest.BodyPublishers.ofString(JSON.toJSONString(safeArgs)));
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            log.error("物理调用企业 OpenAPI 失败: tool={}", toolName, e);
            return "{\"status\":\"ERROR\",\"message\":\"HTTP 调用失败: " + e.getMessage() + "\"}";
        }
    }

    public List<JSONObject> listRegisteredTools() {
        List<JSONObject> list = new ArrayList<>();
        for (OpenApiEndpointMeta meta : endpointRegistry.values()) {
            JSONObject tool = new JSONObject();
            tool.put("name", meta.toolName());
            tool.put("description", meta.description());
            tool.put("inputSchema", meta.inputSchema());
            list.add(tool);
        }
        return list;
    }

    public Map<String, OpenApiEndpointMeta> getEndpointRegistry() {
        return Collections.unmodifiableMap(endpointRegistry);
    }

    private String sanitizeToolName(String operationId, String method, String path) {
        if (operationId != null && !operationId.isBlank()) {
            return "openapi__" + operationId.replaceAll("[^a-zA-Z0-9_]", "_");
        }
        String cleanPath = path.replaceAll("[{}]", "").replaceAll("[^a-zA-Z0-9_]", "_");
        return "openapi__" + method.toLowerCase() + "_" + cleanPath;
    }

    private JSONObject buildInputSchema(JSONObject operation) {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        JSONArray required = new JSONArray();

        // 提取 parameters (path / query)
        JSONArray params = operation.getJSONArray("parameters");
        if (params != null) {
            for (int i = 0; i < params.size(); i++) {
                JSONObject p = params.getJSONObject(i);
                String name = p.getString("name");
                boolean req = p.getBooleanValue("required", false);
                JSONObject pSchema = p.getJSONObject("schema");
                if (pSchema == null) {
                    pSchema = new JSONObject();
                    pSchema.put("type", "string");
                }
                properties.put(name, pSchema);
                if (req) {
                    required.add(name);
                }
            }
        }

        // 提取 requestBody
        JSONObject reqBody = operation.getJSONObject("requestBody");
        if (reqBody != null && reqBody.containsKey("content")) {
            JSONObject content = reqBody.getJSONObject("content");
            if (content.containsKey("application/json")) {
                JSONObject appJson = content.getJSONObject("application/json");
                JSONObject bodySchema = appJson.getJSONObject("schema");
                if (bodySchema != null && bodySchema.containsKey("properties")) {
                    JSONObject bodyProps = bodySchema.getJSONObject("properties");
                    properties.putAll(bodyProps);
                    if (bodySchema.containsKey("required")) {
                        required.addAll(bodySchema.getJSONArray("required"));
                    }
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }
}
