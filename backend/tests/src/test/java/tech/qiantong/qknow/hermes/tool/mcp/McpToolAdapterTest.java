package tech.qiantong.qknow.hermes.tool.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpToolAdapterTest {

    @Mock
    private McpClient mockClient;

    private McpToolAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new McpToolAdapter();
    }

    @Test
    void registerServerDiscoversTools() {
        JSONObject toolDef = new JSONObject();
        toolDef.put("name", "read_file");
        toolDef.put("description", "读取文件内容");
        JSONArray tools = new JSONArray();
        tools.add(toolDef);
        JSONObject listResult = new JSONObject();
        listResult.put("tools", tools);

        when(mockClient.listTools()).thenReturn(List.of(toolDef));

        McpToolAdapter.McpServerConfig config = new McpToolAdapter.McpServerConfig();
        config.setName("test-server");
        config.setUrl("http://localhost:3000");
        config.setEnabled(true);

        McpToolAdapter spyAdapter = spy(adapter);
        doReturn(mockClient).when(spyAdapter).getClient("test-server");

        spyAdapter.registerServer(config);

        verify(mockClient).initialize();
        verify(mockClient).listTools();
    }

    @Test
    void registerServerCreatesConfiguredStdioClient() {
        JSONObject toolDef = new JSONObject();
        toolDef.put("name", "create_issue");
        when(mockClient.listTools()).thenReturn(List.of(toolDef));

        McpToolAdapter.McpServerConfig config = new McpToolAdapter.McpServerConfig();
        config.setName("github");
        config.setCommand(List.of("npx", "-y", "@modelcontextprotocol/server-github"));
        config.setEnvironment(Map.of("GITHUB_PERSONAL_ACCESS_TOKEN", "test-token"));
        config.setEnabled(true);

        McpToolAdapter spyAdapter = spy(adapter);
        doReturn(mockClient).when(spyAdapter).createClient(config);

        spyAdapter.registerServer(config);

        verify(mockClient).initialize();
        verify(mockClient).listTools();
        assertSame(mockClient, spyAdapter.getClient("github"));
        assertTrue(spyAdapter.getMcpTools().containsKey("mcp.github.create_issue"));
    }

    @Test
    void githubServerConfigUsesOfficialStdioCommandAndToken() {
        McpToolAdapter.McpServerConfig config = adapter.githubServerConfig("test-token");

        assertEquals("github", config.getName());
        assertEquals(List.of("npx", "-y", "@modelcontextprotocol/server-github"), config.getCommand());
        assertEquals("test-token", config.getEnvironment().get("GITHUB_PERSONAL_ACCESS_TOKEN"));
        assertTrue(config.isEnabled());
    }

    @Test
    void callToolReturnsResult() {
        McpToolAdapter.McpServerConfig config = new McpToolAdapter.McpServerConfig();
        config.setName("srv");
        config.setUrl("http://localhost:3000");
        config.setEnabled(true);

        JSONObject toolDef = new JSONObject();
        toolDef.put("name", "echo");
        toolDef.put("description", "Echo tool");

        McpToolAdapter spyAdapter = spy(adapter);
        doReturn(mockClient).when(spyAdapter).getClient("srv");
        when(mockClient.listTools()).thenReturn(List.of(toolDef));

        spyAdapter.registerServer(config);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("input", "hello");
        JSONObject callResult = new JSONObject();
        callResult.put("content", "hello response");
        when(mockClient.callTool(eq("echo"), any())).thenReturn(callResult);

        McpToolAdapter.McpToolRequest request = new McpToolAdapter.McpToolRequest();
        request.setParams(arguments);

        FunctionToolCallback<McpToolAdapter.McpToolRequest, String> callback =
                spyAdapter.createMcpToolCallback("srv", "echo", "Echo tool");
        String result = callback.call(JSON.toJSONString(request.getParams()));

        assertTrue(result.contains("hello response"));
        verify(mockClient).callTool(eq("echo"), any());
    }

    @Test
    void connectionFailureHandledGracefully() {
        doThrow(new McpException("Connection refused")).when(mockClient).initialize();

        McpToolAdapter.McpServerConfig config = new McpToolAdapter.McpServerConfig();
        config.setName("broken-server");
        config.setUrl("http://localhost:9999");
        config.setEnabled(true);

        McpToolAdapter spyAdapter = spy(adapter);
        doReturn(mockClient).when(spyAdapter).getClient("broken-server");

        spyAdapter.registerServer(config);

        assertTrue(spyAdapter.getMcpTools().isEmpty());
    }

    @Test
    void timeoutHandledGracefully() {
        Map<String, Object> arguments = Map.of("key", "value");
        when(mockClient.callTool(eq("slow_tool"), any()))
                .thenThrow(new McpException("MCP 请求失败: timeout"));

        McpToolAdapter.McpServerConfig config = new McpToolAdapter.McpServerConfig();
        config.setName("timeout-server");
        config.setUrl("http://localhost:3000");
        config.setEnabled(true);

        JSONObject toolDef = new JSONObject();
        toolDef.put("name", "slow_tool");
        toolDef.put("description", "Slow tool");

        McpToolAdapter spyAdapter = spy(adapter);
        doReturn(mockClient).when(spyAdapter).getClient("timeout-server");
        when(mockClient.listTools()).thenReturn(List.of(toolDef));

        spyAdapter.registerServer(config);

        McpToolAdapter.McpToolRequest request = new McpToolAdapter.McpToolRequest();
        request.setParams(arguments);

        FunctionToolCallback<McpToolAdapter.McpToolRequest, String> callback =
                spyAdapter.createMcpToolCallback("timeout-server", "slow_tool", "Slow tool");
        String result = callback.call(JSON.toJSONString(request.getParams()));

        assertTrue(result.contains("error"));
        assertTrue(result.contains("timeout"));
    }

    @Test
    void disabledServerSkipsConnection() {
        McpToolAdapter.McpServerConfig config = new McpToolAdapter.McpServerConfig();
        config.setName("disabled");
        config.setUrl("http://localhost:3000");
        config.setEnabled(false);

        adapter.registerServer(config);

        assertNull(adapter.getClient("disabled"));
        assertTrue(adapter.getMcpTools().isEmpty());
    }

    @Test
    void unregisterServerDisconnectsAndRemovesTools() {
        McpToolAdapter.McpServerConfig config = new McpToolAdapter.McpServerConfig();
        config.setName("removable");
        config.setUrl("http://localhost:3000");
        config.setEnabled(true);

        JSONObject toolDef = new JSONObject();
        toolDef.put("name", "tool1");
        toolDef.put("description", "Tool 1");

        McpToolAdapter spyAdapter = spy(adapter);
        doReturn(mockClient).when(spyAdapter).getClient("removable");
        when(mockClient.listTools()).thenReturn(List.of(toolDef));

        spyAdapter.registerServer(config);
        spyAdapter.unregisterServer("removable");

        verify(mockClient).disconnect();
        assertTrue(spyAdapter.getMcpTools().isEmpty());
    }

    @Test
    void isMcpToolDetectsPrefix() {
        assertTrue(adapter.isMcpTool("mcp.server.tool"));
        assertFalse(adapter.isMcpTool("native_tool"));
        assertFalse(adapter.isMcpTool(null));
    }

    @Test
    void parseMcpToolNameExtractsParts() {
        String[] parts = adapter.parseMcpToolName("mcp.myserver.mytool");
        assertNotNull(parts);
        assertEquals("myserver", parts[0]);
        assertEquals("mytool", parts[1]);

        assertNull(adapter.parseMcpToolName("not_mcp"));
        assertNull(adapter.parseMcpToolName("mcp.incomplete"));
    }

    @Test
    void toolCallbackReturnsErrorWhenClientNull() {
        McpToolAdapter.McpToolRequest request = new McpToolAdapter.McpToolRequest();
        request.setParams(Map.of());

        FunctionToolCallback<McpToolAdapter.McpToolRequest, String> callback =
                adapter.createMcpToolCallback("no-such-server", "tool", "desc");
        String result = callback.call(JSON.toJSONString(request.getParams()));

        assertTrue(result.contains("error"));
        assertTrue(result.contains("未连接"));
    }
}
