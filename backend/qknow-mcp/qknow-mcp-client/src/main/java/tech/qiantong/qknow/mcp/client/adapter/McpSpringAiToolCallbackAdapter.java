package tech.qiantong.qknow.mcp.client.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tech.qiantong.qknow.mcp.client.McpClientManager;
import tech.qiantong.qknow.mcp.client.security.McpSecurityFilterPipeline;
import tech.qiantong.qknow.mcp.core.model.CallToolResult;
import tech.qiantong.qknow.mcp.core.model.McpTool;
import tech.qiantong.qknow.mcp.core.util.McpNamingConvention;

import java.util.Map;

/**
 * 将标准 MCP 工具无缝适配为 Spring AI ToolCallback（支持 claw-code 双下划线命名空间隔离）
 */
public class McpSpringAiToolCallbackAdapter implements ToolCallback {

    private final String serverId;
    private final McpTool mcpTool;
    private final McpClientManager clientManager;
    private final McpSecurityFilterPipeline securityPipeline;
    private final boolean useQualifiedNamespace;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpSpringAiToolCallbackAdapter(String serverId, McpTool mcpTool,
                                          McpClientManager clientManager,
                                          McpSecurityFilterPipeline securityPipeline) {
        this(serverId, mcpTool, clientManager, securityPipeline, false);
    }

    public McpSpringAiToolCallbackAdapter(String serverId, McpTool mcpTool,
                                          McpClientManager clientManager,
                                          McpSecurityFilterPipeline securityPipeline,
                                          boolean useQualifiedNamespace) {
        this.serverId = serverId;
        this.mcpTool = mcpTool;
        this.clientManager = clientManager;
        this.securityPipeline = securityPipeline;
        this.useQualifiedNamespace = useQualifiedNamespace;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        String schemaJson = "{}";
        try {
            if (mcpTool.inputSchema() != null) {
                schemaJson = objectMapper.writeValueAsString(mcpTool.inputSchema());
            }
        } catch (Exception ignored) {}

        String toolName = useQualifiedNamespace
                ? McpNamingConvention.buildQualifiedToolName(serverId, mcpTool.name())
                : "mcp_" + serverId + "_" + mcpTool.name();

        return ToolDefinition.builder()
            .name(toolName)
            .description(mcpTool.description())
            .inputSchema(schemaJson)
            .build();
    }

    @Override
    public String call(String inputJson) {
        try {
            // 1. 前置安全门禁
            securityPipeline.preCheckCall(serverId, mcpTool.name(), inputJson);

            // 2. 反序列化入参
            Map<String, Object> args = Map.of();
            if (inputJson != null && !inputJson.isBlank()) {
                args = objectMapper.readValue(inputJson, new TypeReference<Map<String, Object>>() {});
            }

            // 3. 执行 MCP 调用
            CallToolResult result = clientManager.callTool(serverId, mcpTool.name(), args);
            String rawContent = result.getFirstText();

            // 4. 后置间接注入与主动免疫清洗
            return securityPipeline.postSanitizeResult(serverId, mcpTool.name(), rawContent);
        } catch (SecurityException se) {
            return "[安全阻断]: " + se.getMessage();
        } catch (Exception ex) {
            return "[调用异常]: " + ex.getMessage();
        }
    }
}
