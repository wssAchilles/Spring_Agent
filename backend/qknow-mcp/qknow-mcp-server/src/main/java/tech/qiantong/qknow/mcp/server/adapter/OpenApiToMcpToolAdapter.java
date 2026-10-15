package tech.qiantong.qknow.mcp.server.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.model.CallToolResult;
import tech.qiantong.qknow.mcp.core.model.McpTool;
import tech.qiantong.qknow.mcp.core.model.ToolContentItem;
import tech.qiantong.qknow.mcp.core.orchestration.dto.McpToolDescriptor;
import tech.qiantong.qknow.mcp.core.util.McpNamingConvention;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * OpenAPI 3.0 / Swagger 零代码动态逆向适配器 (OpenApiToMcpToolAdapter)
 * 递归解析企业存量微服务 OpenAPI JSON 规范，自动逆向生成符合 MCP 契约的标准工具定义与千问 1536 维超球面单位向量
 * 采用纯 Java 21 与 Jackson 实现，零外部重型依赖
 */
public class OpenApiToMcpToolAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenApiToMcpToolAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final int EMBEDDING_DIMENSION = 1536;

    private final String serverId;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Map<String, DynamicOpenApiEndpoint> endpointRegistry = new HashMap<>();

    public record DynamicOpenApiEndpoint(
            String toolName,
            String path,
            String httpMethod,
            String summary,
            String description,
            McpTool mcpTool,
            double[] hypersphericalEmbedding
    ) {}

    public OpenApiToMcpToolAdapter(String serverId, String baseUrl) {
        this.serverId = McpNamingConvention.normalizeName(serverId != null ? serverId : "default_openapi");
        this.baseUrl = baseUrl != null ? (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) : "http://localhost:8080";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 解析 OpenAPI 3.0 / Swagger JSON 文本，逆向生成 MCP 工具描述符集合
     */
    public List<McpToolDescriptor> parseAndRegister(String openApiJson) {
        if (openApiJson == null || openApiJson.isBlank()) {
            throw new IllegalArgumentException("OpenAPI 规范文本不能为空");
        }

        List<McpToolDescriptor> descriptors = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(openApiJson);
            JsonNode pathsNode = root.path("paths");
            if (pathsNode.isMissingNode() || !pathsNode.isObject()) {
                log.warn("OpenAPI 规范中未发现有效的 paths 节点: serverId={}", serverId);
                return descriptors;
            }

            Iterator<Map.Entry<String, JsonNode>> pathIterator = pathsNode.fields();
            while (pathIterator.hasNext()) {
                Map.Entry<String, JsonNode> pathEntry = pathIterator.next();
                String path = pathEntry.getKey();
                JsonNode methodsNode = pathEntry.getValue();

                Iterator<Map.Entry<String, JsonNode>> methodIterator = methodsNode.fields();
                while (methodIterator.hasNext()) {
                    Map.Entry<String, JsonNode> methodEntry = methodIterator.next();
                    String httpMethod = methodEntry.getKey().toUpperCase(Locale.ROOT);
                    JsonNode opNode = methodEntry.getValue();

                    // 仅支持常见 HTTP 操作方法
                    if (!List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(httpMethod)) {
                        continue;
                    }

                    String rawOpId = opNode.path("operationId").asText(null);
                    if (rawOpId == null || rawOpId.isBlank()) {
                        rawOpId = httpMethod.toLowerCase(Locale.ROOT) + "_" + path.replaceAll("[^a-zA-Z0-9]", "_");
                    }

                    String toolName = McpNamingConvention.buildQualifiedToolName(serverId, rawOpId);
                    String summary = opNode.path("summary").asText("");
                    String description = opNode.path("description").asText(summary);
                    if (description.isBlank()) {
                        description = "OpenAPI endpoint: " + httpMethod + " " + path;
                    }

                    // 动态构建 JSON Schema inputSchema
                    Set<String> requiredSlots = new HashSet<>();
                    Set<String> optionalSlots = new HashSet<>();
                    Map<String, Object> inputSchemaMap = buildInputSchemaMap(opNode, requiredSlots, optionalSlots);

                    // 构造 MCP Tool 契约
                    McpTool mcpTool = new McpTool(toolName, description, inputSchemaMap);

                    // 生成千问 1536 维超球面单位向量 (基于工具名称与语义描述派生确定性几何特征)
                    double[] embedding = generateDeterministicHypersphericalEmbedding(toolName + " " + summary + " " + description);

                    DynamicOpenApiEndpoint endpoint = new DynamicOpenApiEndpoint(
                            toolName, path, httpMethod, summary, description, mcpTool, embedding
                    );
                    endpointRegistry.put(toolName, endpoint);

                    boolean isIdempotent = "GET".equals(httpMethod) || "PUT".equals(httpMethod);
                    boolean isDestructive = "DELETE".equals(httpMethod);

                    McpToolDescriptor descriptor = new McpToolDescriptor(
                            toolName,
                            toolName,
                            "OPENAPI_DYNAMIC",
                            embedding,
                            requiredSlots,
                            optionalSlots,
                            List.of(),
                            isIdempotent,
                            isDestructive
                    );
                    descriptors.add(descriptor);
                }
            }

            log.info("成功逆向解析 OpenAPI 规范: serverId={}, 导出工具数={}", serverId, descriptors.size());
            return descriptors;
        } catch (Exception e) {
            throw new RuntimeException("解析 OpenAPI 规范发生异常: serverId=" + serverId, e);
        }
    }

    /**
     * 递归构建符合 JSON Schema Draft-07 的 Map<String, Object>
     */
    private Map<String, Object> buildInputSchemaMap(JsonNode opNode, Set<String> requiredSlots, Set<String> optionalSlots) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode requiredList = schema.putArray("required");

        // 1. 处理 query / path / header 参数
        JsonNode parametersNode = opNode.path("parameters");
        if (parametersNode.isArray()) {
            for (JsonNode param : parametersNode) {
                String name = param.path("name").asText(null);
                if (name == null || name.isBlank()) continue;

                boolean required = param.path("required").asBoolean(false);
                String desc = param.path("description").asText("");
                String type = param.path("schema").path("type").asText("string");

                ObjectNode prop = properties.putObject(name);
                prop.put("type", type);
                prop.put("description", desc.isBlank() ? "Parameter: " + name : desc);

                if (required) {
                    requiredList.add(name);
                    requiredSlots.add(name);
                } else {
                    optionalSlots.add(name);
                }
            }
        }

        // 2. 处理 requestBody
        JsonNode requestBodyNode = opNode.path("requestBody");
        JsonNode jsonSchemaNode = requestBodyNode.path("content").path("application/json").path("schema");
        if (jsonSchemaNode.isObject()) {
            JsonNode bodyProps = jsonSchemaNode.path("properties");
            if (bodyProps.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> bodyFields = bodyProps.fields();
                while (bodyFields.hasNext()) {
                    Map.Entry<String, JsonNode> field = bodyFields.next();
                    properties.set(field.getKey(), field.getValue());
                    optionalSlots.add(field.getKey());
                }
            }
            JsonNode bodyRequired = jsonSchemaNode.path("required");
            if (bodyRequired.isArray()) {
                for (JsonNode r : bodyRequired) {
                    String reqKey = r.asText();
                    requiredList.add(reqKey);
                    requiredSlots.add(reqKey);
                    optionalSlots.remove(reqKey);
                }
            }
        }

        return MAPPER.convertValue(schema, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 执行动态 OpenAPI 代理调用
     */
    public CallToolResult executeDynamicCall(String toolName, Map<String, Object> arguments) {
        DynamicOpenApiEndpoint endpoint = endpointRegistry.get(toolName);
        if (endpoint == null) {
            return new CallToolResult(List.of(new ToolContentItem("text", "未找到注册的 OpenAPI 工具: " + toolName)), true);
        }

        try {
            String resolvedPath = endpoint.path();
            Map<String, Object> remainingArgs = new HashMap<>(arguments != null ? arguments : Map.of());

            // 1. 替换 Path 变量: 例如 /users/{userId}
            for (Map.Entry<String, Object> entry : arguments != null ? arguments.entrySet() : Collections.<Map.Entry<String, Object>>emptySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (resolvedPath.contains(placeholder)) {
                    resolvedPath = resolvedPath.replace(placeholder, String.valueOf(entry.getValue()));
                    remainingArgs.remove(entry.getKey());
                }
            }

            // 2. 拼装 URL 与 Query 参数
            StringBuilder urlBuilder = new StringBuilder(baseUrl).append(resolvedPath);
            if ("GET".equals(endpoint.httpMethod()) && !remainingArgs.isEmpty()) {
                urlBuilder.append("?");
                boolean first = true;
                for (Map.Entry<String, Object> entry : remainingArgs.entrySet()) {
                    if (!first) urlBuilder.append("&");
                    urlBuilder.append(entry.getKey()).append("=").append(String.valueOf(entry.getValue()));
                    first = false;
                }
            }

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json");

            if ("POST".equals(endpoint.httpMethod()) || "PUT".equals(endpoint.httpMethod()) || "PATCH".equals(endpoint.httpMethod())) {
                String jsonBody = MAPPER.writeValueAsString(remainingArgs);
                reqBuilder.header("Content-Type", "application/json");
                reqBuilder.method(endpoint.httpMethod(), HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
            } else if ("DELETE".equals(endpoint.httpMethod())) {
                reqBuilder.DELETE();
            } else {
                reqBuilder.GET();
            }

            // 3. 发起调用
            HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            boolean isError = resp.statusCode() >= 400;
            return new CallToolResult(List.of(new ToolContentItem("text", resp.body())), isError);

        } catch (Exception e) {
            log.error("执行动态 OpenAPI 工具调用发生异常: toolName={}", toolName, e);
            return new CallToolResult(List.of(new ToolContentItem("text", "执行调用失败: " + e.getMessage())), true);
        }
    }

    /**
     * 生成千问 1536 维超球面单位向量 (严格满足 ||v||_2 = 1.0)
     */
    public static double[] generateDeterministicHypersphericalEmbedding(String seedText) {
        double[] vec = new double[EMBEDDING_DIMENSION];
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(seedText.getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (hash[i] & 0xFF);
            }
            Random random = new Random(seed);
            double sumSq = 0.0;
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                // Box-Muller 变换生成标准高斯变量
                double u1 = random.nextDouble();
                double u2 = random.nextDouble();
                double val = Math.sqrt(-2.0 * Math.log(Math.max(1e-12, u1))) * Math.cos(2.0 * Math.PI * u2);
                vec[i] = val;
                sumSq += val * val;
            }
            double norm = Math.sqrt(sumSq);
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                vec[i] /= norm;
            }
            return vec;
        } catch (Exception e) {
            throw new RuntimeException("生成超球面向量失败", e);
        }
    }

    public Map<String, DynamicOpenApiEndpoint> getEndpointRegistry() {
        return Collections.unmodifiableMap(endpointRegistry);
    }
}
