package tech.qiantong.qknow.module.kb.tool.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.function.FunctionToolCallback;
import tech.qiantong.qknow.hermes.tool.mcp.McpClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KbMcpToolAdapterTest {

    @Test
    void callbackCallsConnectedMcpClient() {
        McpClient client = mock(McpClient.class);
        JSONObject toolDef = new JSONObject();
        toolDef.put("name", "echo");
        toolDef.put("description", "Echo tool");
        when(client.listTools()).thenReturn(List.of(toolDef));
        JSONObject callResult = new JSONObject();
        callResult.put("content", "real response");
        when(client.callTool(eq("echo"), any())).thenReturn(callResult);

        McpToolAdapter adapter = spy(new McpToolAdapter());
        doReturn(client).when(adapter).getClient("srv");

        McpToolAdapter.McpServerConfig config = new McpToolAdapter.McpServerConfig();
        config.setName("srv");
        config.setEnabled(true);
        adapter.registerServer(config);

        McpToolAdapter.McpToolRequest request = new McpToolAdapter.McpToolRequest();
        request.setParams(Map.of("input", "hello"));
        FunctionToolCallback<McpToolAdapter.McpToolRequest, String> callback =
                adapter.createMcpToolCallback("srv", "echo", "Echo tool");

        String result = callback.call(JSON.toJSONString(request.getParams()));

        assertTrue(result.contains("real response"));
        assertFalse(result.contains("占位" + "响应"));
        verify(client).callTool(eq("echo"), any());
    }
}
