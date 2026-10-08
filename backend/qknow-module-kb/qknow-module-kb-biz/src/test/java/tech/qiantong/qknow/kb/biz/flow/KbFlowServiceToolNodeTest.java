package tech.qiantong.qknow.kb.biz.flow;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.guard.ReActCycleGuard;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.ToolNodeBO;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tool 节点与 ReActCycleGuard 循环卫士熔断机制契约测试
 */
public class KbFlowServiceToolNodeTest {

    @Test
    @DisplayName("测试工具节点正常执行返回 SUCCESS")
    void testToolNode_NormalExecution() {
        KbFlowNodeDO nodeDef = new KbFlowNodeDO();
        nodeDef.setUuid("tool-uuid-1");
        nodeDef.setName("weather_query");
        nodeDef.setInput("[]");

        ToolNodeBO toolNode = new ToolNodeBO(nodeDef, Collections.emptyList(), null);

        RuntimeContextBO context = new RuntimeContextBO();
        context.setVariables(new JSONObject());

        NodeRunResultBO result = toolNode.execute(context);

        assertNotNull(result);
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), result.getStatus());
        assertEquals("SUCCESS", result.getOutput().get("status"));
        assertTrue(result.getOutput().get("result").toString().contains("weather_query"));
    }

    @Test
    @DisplayName("测试连续完全相同入参调用工具被 ReAct 卫士短路拦截")
    void testToolNode_DuplicateCallTripped() {
        KbFlowNodeDO nodeDef = new KbFlowNodeDO();
        nodeDef.setUuid("tool-uuid-2");
        nodeDef.setName("calculator");
        nodeDef.setInput("[]");

        // 共享同一个 ReActCycleGuard 实例
        ReActCycleGuard guard = new ReActCycleGuard(10, 3);
        ToolNodeBO toolNode = new ToolNodeBO(nodeDef, Collections.emptyList(), null, guard);

        RuntimeContextBO context = new RuntimeContextBO();
        context.setVariables(new JSONObject());

        // 第 1 次调用正常
        NodeRunResultBO res1 = toolNode.execute(context);
        assertEquals("SUCCESS", res1.getOutput().get("status"));

        // 第 2 次相同入参立即被连续重复调用短路拦截
        NodeRunResultBO res2 = toolNode.execute(context);
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), res2.getStatus());
        assertEquals("CIRCUIT_TRIPPED", res2.getOutput().get("status"));
        assertTrue(res2.getOutput().get("result").toString().contains("系统纠偏提示"));
    }

    @Test
    @DisplayName("测试滑动窗口内高频循环调用触发震荡熔断")
    void testToolNode_WindowOscillationTripped() {
        KbFlowNodeDO nodeDef = new KbFlowNodeDO();
        nodeDef.setUuid("tool-uuid-3");
        nodeDef.setName("stock_fetcher");
        nodeDef.setInput("[{\"name\":\"arg\"}]");

        // 窗口容量 6，单工具频次上限 3
        ReActCycleGuard guard = new ReActCycleGuard(15, 3);

        ToolNodeBO toolNode = new ToolNodeBO(nodeDef, Collections.emptyList(), null, guard);

        RuntimeContextBO context = new RuntimeContextBO();

        // 交替不同参数避免连续完全重复，但在窗口内累计达到 3 次
        context.setVariables(JSONObject.parseObject("{\"arg\": 1}"));
        NodeRunResultBO r1 = toolNode.execute(context);
        assertEquals("SUCCESS", r1.getOutput().get("status"));

        context.setVariables(JSONObject.parseObject("{\"arg\": 2}"));
        NodeRunResultBO r2 = toolNode.execute(context);
        assertEquals("SUCCESS", r2.getOutput().get("status"));

        context.setVariables(JSONObject.parseObject("{\"arg\": 1}"));
        NodeRunResultBO r3 = toolNode.execute(context);
        // 此处第二次调用 arg=1 成功
        assertEquals("SUCCESS", r3.getOutput().get("status"));

        context.setVariables(JSONObject.parseObject("{\"arg\": 2}"));
        NodeRunResultBO r4 = toolNode.execute(context);
        assertEquals("SUCCESS", r4.getOutput().get("status"));

        // 累计达到上限，触发熔断
        context.setVariables(JSONObject.parseObject("{\"arg\": 1}"));
        NodeRunResultBO r5 = toolNode.execute(context);
        assertEquals("CIRCUIT_TRIPPED", r5.getOutput().get("status"));
        assertTrue(r5.getOutput().get("result").toString().contains("系统纠偏提示"));
    }
}
