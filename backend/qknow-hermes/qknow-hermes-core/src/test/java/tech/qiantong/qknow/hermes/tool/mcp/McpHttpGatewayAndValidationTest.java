package tech.qiantong.qknow.hermes.tool.mcp;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 企业级网络化 MCP 网关与 JSON Schema 参数校验契约测试
 * 验证：HttpMcpClient 认证与 Header 注入、McpSchemaValidator 前置校验拦截、McpToolAdapter 双模接入与防幻觉防护
 */
public class McpHttpGatewayAndValidationTest {

    @Test
    @DisplayName("验证 HttpMcpClient：支持 API 认证令牌与自定义 Header 注入，端点自适应解析")
    public void testHttpMcpClientAuthAndHeaders() {
        String baseUrl = "https://mcp.enterprise.internal/api/v1";
        String apiKey = "sk-test-enterprise-token-9988";
        Map<String, String> headers = Map.of("X-Tenant-Id", "tenant-alpha", "X-Trace-Id", "trace-xyz");

        HttpMcpClient client = new HttpMcpClient(baseUrl, apiKey, headers);

        assertEquals("https://mcp.enterprise.internal/api/v1", client.getBaseUrl());
        assertEquals(apiKey, client.getApiKey());
        assertEquals("tenant-alpha", client.getCustomHeaders().get("X-Tenant-Id"));
        assertEquals("trace-xyz", client.getCustomHeaders().get("X-Trace-Id"));
    }

    @Test
    @DisplayName("验证 McpSchemaValidator：缺少必需字段时被精确拦截")
    public void testSchemaValidationMissingRequired() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONArray required = new JSONArray();
        required.add("repoName");
        required.add("issueNumber");
        schema.put("required", required);

        JSONObject properties = new JSONObject();
        JSONObject repoProp = new JSONObject();
        repoProp.put("type", "string");
        JSONObject issueProp = new JSONObject();
        issueProp.put("type", "integer");
        properties.put("repoName", repoProp);
        properties.put("issueNumber", issueProp);
        schema.put("properties", properties);

        // 缺少 issueNumber
        Map<String, Object> invalidArgs = Map.of("repoName", "qknow-enterprise");
        McpSchemaValidator.ValidationResult result = McpSchemaValidator.validate(schema, invalidArgs);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("缺少必需参数: 'issueNumber'"));
    }

    @Test
    @DisplayName("验证 McpSchemaValidator：类型不匹配时被精确拦截")
    public void testSchemaValidationTypeMismatch() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");

        JSONObject properties = new JSONObject();
        JSONObject timeoutProp = new JSONObject();
        timeoutProp.put("type", "integer");
        properties.put("timeoutSeconds", timeoutProp);
        schema.put("properties", properties);

        // 传入了字符串类型的 timeoutSeconds
        Map<String, Object> invalidArgs = Map.of("timeoutSeconds", "not_a_number");
        McpSchemaValidator.ValidationResult result = McpSchemaValidator.validate(schema, invalidArgs);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("参数 'timeoutSeconds' 类型不匹配: 期望 integer"));
    }

    @Test
    @DisplayName("验证 McpSchemaValidator：合规参数顺利通过")
    public void testSchemaValidationSuccess() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("required", List.of("query", "topK"));

        JSONObject properties = new JSONObject();
        properties.put("query", JSONObject.of("type", "string"));
        properties.put("topK", JSONObject.of("type", "integer"));
        properties.put("enableFilter", JSONObject.of("type", "boolean"));
        schema.put("properties", properties);

        Map<String, Object> validArgs = Map.of(
                "query", "企业级混合检索方案",
                "topK", 10,
                "enableFilter", true
        );

        McpSchemaValidator.ValidationResult result = McpSchemaValidator.validate(schema, validArgs);
        assertTrue(result.valid());
        assertNull(result.errorMessage());
    }

    @Test
    @DisplayName("验证 McpToolAdapter：前置拦截不合规参数，合规参数正常穿透调用客户端")
    public void testMcpToolAdapterSchemaValidationGate() throws Exception {
        McpToolAdapter adapter = new McpToolAdapter();

        String serverName = "github-remote";
        String toolName = "create_issue";
        String fullToolCode = "mcp." + serverName + "." + toolName;

        // Mock 远程客户端
        McpClient mockClient = mock(McpClient.class);
        when(mockClient.isConnected()).thenReturn(true);

        JSONObject toolDef = new JSONObject();
        toolDef.put("name", toolName);
        toolDef.put("description", "创建 GitHub Issue");

        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("required", List.of("title", "body"));
        toolDef.put("inputSchema", schema);

        when(mockClient.listTools()).thenReturn(List.of(toolDef));

        // 注入 Mock 客户端与配置
        McpToolAdapter.McpServerConfig config = new McpToolAdapter.McpServerConfig();
        config.setName(serverName);
        config.setUrl("https://api.github-mcp.internal/mcp");
        config.setApiKey("ghp_fake_token");
        config.setEnabled(true);

        Map<String, McpClient> clientsMap = (Map<String, McpClient>) ReflectionTestUtils.getField(adapter, "clients");
        clientsMap.put(serverName, mockClient);

        adapter.registerServer(config);

        // 1. 验证工具已发现且 Schema 已缓存
        assertNotNull(adapter.getToolSchema(fullToolCode));

        // 2. 模拟大模型幻觉：只传入了 title，缺少必需参数 body
        Map<String, Object> hallucinatedArgs = Map.of("title", "修复工作流死锁缺陷");
        String errorOutput = adapter.executeTool(fullToolCode, hallucinatedArgs);

        // 校验：被前置拦截，返回错误信息，且底层 mockClient.callTool 绝对没有被调用！
        assertTrue(errorOutput.contains("参数校验失败"));
        assertTrue(errorOutput.contains("缺少必需参数: 'body'"));
        verify(mockClient, never()).callTool(any(), any());

        // 3. 传入合规参数：正常执行并返回结果
        when(mockClient.callTool(eq(toolName), any())).thenReturn(JSONObject.of("issueUrl", "https://github.com/issues/101"));
        Map<String, Object> validArgs = Map.of("title", "修复工作流死锁缺陷", "body", "通过 DagCheckpointManager 断点恢复持久化自愈。");

        String successOutput = adapter.executeTool(fullToolCode, validArgs);
        assertTrue(successOutput.contains("https://github.com/issues/101"));
        verify(mockClient, times(1)).callTool(eq(toolName), any());
    }
}
