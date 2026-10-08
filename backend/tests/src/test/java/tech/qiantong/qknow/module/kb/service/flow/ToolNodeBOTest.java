package tech.qiantong.qknow.module.kb.service.flow;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.qiantong.qknow.hermes.agent.guard.ReActCycleGuard;
import tech.qiantong.qknow.hermes.tool.mcp.McpToolAdapter;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.ToolNodeBO;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ToolNodeBO 真实工具物理调度与循环卫士集成测试
 */
@ExtendWith(MockitoExtension.class)
class ToolNodeBOTest {

    @Mock
    private McpToolAdapter mcpToolAdapter;

    private RuntimeContextBO createContext() {
        RuntimeContextBO context = new RuntimeContextBO();
        context.setVariables(new JSONObject());
        return context;
    }

    @Test
    @DisplayName("测试真实 MCP 工具物理执行成功并回填结构化结果")
    void testExecuteToolSuccess() {
        KbFlowNodeDO nodeDO = new KbFlowNodeDO();
        nodeDO.setUuid("tool-node-1");
        nodeDO.setName("weather_service");
        nodeDO.setConfig("{\"toolCode\":\"mcp.weather.get_current\",\"description\":\"查询天气\"}");

        when(mcpToolAdapter.executeTool(eq("mcp.weather.get_current"), any()))
                .thenReturn("{\"temperature\":24.5,\"condition\":\"Sunny\"}");

        ToolNodeBO toolNode = new ToolNodeBO(nodeDO, Collections.emptyList(), mcpToolAdapter);

        Map<String, Object> input = new HashMap<>();
        input.put("city", "Beijing");

        NodeRunResultBO result = toolNode.execute(createContext());

        assertNotNull(result);
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), result.getStatus());
        assertEquals("tool-node-1", result.getNodeUuid());

        Map<String, Object> output = result.getOutput();
        assertNotNull(output);
        assertEquals("SUCCESS", output.get("status"));
        assertEquals("mcp.weather.get_current", output.get("tool"));
        assertTrue(output.get("result") instanceof JSONObject);
        JSONObject resultJson = (JSONObject) output.get("result");
        assertEquals(24.5, resultJson.getDouble("temperature"));

        verify(mcpToolAdapter, times(1)).executeTool(eq("mcp.weather.get_current"), any());
    }

    @Test
    @DisplayName("测试 ReActCycleGuard 循环卫士：连续完全重复调用在第2次立即触发短路拦截")
    void testCycleGuardCircuitBreaker() {
        KbFlowNodeDO nodeDO = new KbFlowNodeDO();
        nodeDO.setUuid("tool-node-loop");
        nodeDO.setName("search_tool");
        nodeDO.setConfig("{\"toolCode\":\"mcp.search.query\"}");

        // 设置保护卫士
        ReActCycleGuard cycleGuard = new ReActCycleGuard(10, 3);
        when(mcpToolAdapter.executeTool(eq("mcp.search.query"), any()))
                .thenReturn("{\"items\":[]}");

        ToolNodeBO toolNode = new ToolNodeBO(nodeDO, Collections.emptyList(), mcpToolAdapter, cycleGuard);

        // 第一次调用：正常通过，执行底层物理 MCP 工具
        NodeRunResultBO run1 = toolNode.execute(createContext());
        assertNotNull(run1);
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), run1.getStatus());
        assertEquals("SUCCESS", run1.getOutput().get("status"));

        // 第二次使用完全相同参数调用：触发连续完全重复调用短路拦截 (Consecutive Duplicate Check)
        NodeRunResultBO run2 = toolNode.execute(createContext());
        assertNotNull(run2);
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), run2.getStatus());
        assertEquals("CIRCUIT_TRIPPED", run2.getOutput().get("status"));
        assertTrue(run2.getOutput().get("result").toString().contains("严禁重复无效调用"));

        // 验证物理执行只发生了第 1 次，第 2 次被短路拦截未穿透到底层物理调用
        verify(mcpToolAdapter, times(1)).executeTool(eq("mcp.search.query"), any());
    }

    @Test
    @DisplayName("测试 MCP 适配器抛出异常时的优雅降级")
    void testExecuteToolExceptionFallback() {
        KbFlowNodeDO nodeDO = new KbFlowNodeDO();
        nodeDO.setUuid("tool-node-err");
        nodeDO.setName("db_query");

        when(mcpToolAdapter.executeTool(eq("db_query"), any()))
                .thenThrow(new RuntimeException("Connection timeout"));

        ToolNodeBO toolNode = new ToolNodeBO(nodeDO, Collections.emptyList(), mcpToolAdapter);
        NodeRunResultBO result = toolNode.execute(createContext());

        assertNotNull(result);
        assertEquals(RuntimeStatusEnums.ERROR.getCode(), result.getStatus());
        assertTrue(result.getErrorMessage().contains("Connection timeout"));
    }
}
