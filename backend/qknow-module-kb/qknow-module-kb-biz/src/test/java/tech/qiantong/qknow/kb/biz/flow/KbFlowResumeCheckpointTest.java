package tech.qiantong.qknow.kb.biz.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.common.exception.ServiceException;
import tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager;
import tech.qiantong.qknow.module.kb.controller.admin.runtime.vo.KbRuntimeRespVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.bot.KbBotDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.runtime.KbRuntimeDO;
import tech.qiantong.qknow.module.kb.dal.enums.BotTypeEnums;
import tech.qiantong.qknow.module.kb.dal.enums.FlowNodeTypeEnums;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.module.kb.service.bot.IKbBotService;
import tech.qiantong.qknow.module.kb.service.flow.IKbFlowEdgeService;
import tech.qiantong.qknow.module.kb.service.flow.IKbFlowNodeService;
import tech.qiantong.qknow.module.kb.service.flow.bo.BaseNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.module.kb.service.flow.factory.NodeFactory;
import tech.qiantong.qknow.module.kb.service.flow.impl.KbFlowServiceImpl;
import tech.qiantong.qknow.module.kb.service.runtime.IKbRuntimeService;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * 工作流 Checkpointing 断点恢复持久化与自愈续跑契约测试
 * 验证：节点级崩溃后断点恢复、已完成节点跳过幂等执行、人工干预变量合并与最终全量结果汇聚
 */
public class KbFlowResumeCheckpointTest {

    private KbFlowServiceImpl flowService;
    private IKbFlowNodeService flowNodeService;
    private IKbFlowEdgeService flowEdgeService;
    private IKbRuntimeService runtimeService;
    private IKbBotService botService;
    private NodeFactory nodeFactory;
    private DagCheckpointManager checkpointManager;

    @BeforeEach
    public void setup() {
        flowService = new KbFlowServiceImpl();
        flowNodeService = mock(IKbFlowNodeService.class);
        flowEdgeService = mock(IKbFlowEdgeService.class);
        runtimeService = mock(IKbRuntimeService.class);
        botService = mock(IKbBotService.class);
        nodeFactory = mock(NodeFactory.class);
        // 使用纯内存双轨降级实例验证
        checkpointManager = new DagCheckpointManager();

        ReflectionTestUtils.setField(flowService, "flowNodeService", flowNodeService);
        ReflectionTestUtils.setField(flowService, "flowEdgeService", flowEdgeService);
        ReflectionTestUtils.setField(flowService, "runtimeService", runtimeService);
        ReflectionTestUtils.setField(flowService, "botService", botService);
        ReflectionTestUtils.setField(flowService, "nodeFactory", nodeFactory);
        ReflectionTestUtils.setField(flowService, "dagCheckpointManager", checkpointManager);
    }

    private JSONObject createNodeJson(String id, String label, String typeName) {
        JSONObject node = new JSONObject();
        node.put("id", id);
        node.put("type", typeName);

        JSONObject data = new JSONObject();
        JSONObject config = new JSONObject();
        config.put("label", label);
        data.put("config", config);
        data.put("input", new JSONArray());
        data.put("output", new JSONArray());
        node.put("data", data);
        return node;
    }

    private JSONObject createEdgeJson(String source, String target) {
        JSONObject edge = new JSONObject();
        edge.put("source", source);
        edge.put("target", target);
        return edge;
    }

    @Test
    @DisplayName("验证断点恢复：第1层执行完毕后恢复执行，第1层节点不重复运行，第2层继续执行并成功汇聚")
    public void testResumeFlowFromCheckpoint() {
        // 构建 3 层流程: start -> (node1, node2) -> node3
        JSONObject startNode = createNodeJson("start-01", "开始", "start");
        JSONObject node1 = createNodeJson("llm-01", "大模型分析", "llm");
        JSONObject node2 = createNodeJson("tool-01", "工具调用", "tool");
        JSONObject node3 = createNodeJson("reply-01", "聚合回复", "reply");

        List<JSONObject> nodes = List.of(startNode, node1, node2, node3);
        List<JSONObject> edges = List.of(
                createEdgeJson("start-01", "llm-01"),
                createEdgeJson("start-01", "tool-01"),
                createEdgeJson("llm-01", "reply-01"),
                createEdgeJson("tool-01", "reply-01")
        );

        Long botId = 888L;
        Long runtimeId = 9999L;

        KbBotDO botDO = new KbBotDO();
        botDO.setId(botId);
        botDO.setType(BotTypeEnums.WORK_FLOW.getCode());
        when(botService.getById(botId)).thenReturn(botDO);

        KbRuntimeDO runtimeDO = new KbRuntimeDO();
        runtimeDO.setId(runtimeId);
        runtimeDO.setBotId(botId);
        runtimeDO.setStatus(RuntimeStatusEnums.RUNNING.getCode());
        when(runtimeService.getById(runtimeId)).thenReturn(runtimeDO);

        when(flowNodeService.flowVOByBotId(botId)).thenReturn(nodes);
        when(flowEdgeService.flowVOByBotId(botId)).thenReturn(edges);

        // 模拟执行计数器
        AtomicInteger startExecCount = new AtomicInteger(0);
        AtomicInteger node1ExecCount = new AtomicInteger(0);
        AtomicInteger node2ExecCount = new AtomicInteger(0);
        AtomicInteger node3ExecCount = new AtomicInteger(0);

        BaseNodeBO startBO = mock(BaseNodeBO.class);
        when(startBO.execute(any())).thenAnswer(invocation -> {
            startExecCount.incrementAndGet();
            return NodeRunResultBO.success("start-01", "开始", Map.of("query", "企业知识库测试"));
        });

        BaseNodeBO node1BO = mock(BaseNodeBO.class);
        when(node1BO.execute(any())).thenAnswer(invocation -> {
            node1ExecCount.incrementAndGet();
            return NodeRunResultBO.success("llm-01", "大模型分析", Map.of("answer", "大模型产出分析"));
        });

        BaseNodeBO node2BO = mock(BaseNodeBO.class);
        when(node2BO.execute(any())).thenAnswer(invocation -> {
            node2ExecCount.incrementAndGet();
            return NodeRunResultBO.success("tool-01", "工具调用", Map.of("toolResult", "工具搜索成功"));
        });

        BaseNodeBO node3BO = mock(BaseNodeBO.class);
        when(node3BO.execute(any())).thenAnswer(invocation -> {
            node3ExecCount.incrementAndGet();
            return NodeRunResultBO.success("reply-01", "聚合回复", Map.of("finalText", "综合结论回复"));
        });

        when(nodeFactory.createNode(argThat(n -> n != null && "start-01".equals(n.getUuid())), any())).thenReturn(startBO);
        when(nodeFactory.createNode(argThat(n -> n != null && "llm-01".equals(n.getUuid())), any())).thenReturn(node1BO);
        when(nodeFactory.createNode(argThat(n -> n != null && "tool-01".equals(n.getUuid())), any())).thenReturn(node2BO);
        when(nodeFactory.createNode(argThat(n -> n != null && "reply-01".equals(n.getUuid())), any())).thenReturn(node3BO);

        // 模拟外部系统在执行完第 0 层 (start) 和第 1 层 (node1, node2) 之后发生挂起，已在 Checkpoint 中保存
        Map<String, tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO> completedResults = new HashMap<>();
        tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO startRes = new tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO();
        startRes.setNodeUuid("start-01");
        startRes.setNodeName("开始");
        startRes.setStatus(RuntimeStatusEnums.SUCCESS.getCode());
        startRes.setOutput(Map.of("query", "企业知识库测试"));
        completedResults.put("start-01", startRes);

        tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO n1Res = new tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO();
        n1Res.setNodeUuid("llm-01");
        n1Res.setNodeName("大模型分析");
        n1Res.setStatus(RuntimeStatusEnums.SUCCESS.getCode());
        n1Res.setOutput(Map.of("answer", "大模型产出分析"));
        completedResults.put("llm-01", n1Res);

        tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO n2Res = new tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO();
        n2Res.setNodeUuid("tool-01");
        n2Res.setNodeName("工具调用");
        n2Res.setStatus(RuntimeStatusEnums.SUCCESS.getCode());
        n2Res.setOutput(Map.of("toolResult", "工具搜索成功"));
        completedResults.put("tool-01", n2Res);

        Map<String, Object> savedVars = new HashMap<>();
        savedVars.put("systemPrompt", "企业级助手");

        // 存入检查点：第 1 层（groupIndex=1）已完成，flowId="888"
        checkpointManager.saveCheckpointWithVariables(String.valueOf(runtimeId), String.valueOf(botId), 1, completedResults, savedVars);

        // 此时触发断点恢复执行，并传入人工审批/干预补充参数
        Map<String, Object> humanInput = Map.of("approvedBy", "AdminUser");
        KbRuntimeRespVO response = flowService.resumeFlow(runtimeId, humanInput);

        // 验证：
        // 1. 已在检查点中的节点 (start-01, llm-01, tool-01) 绝对不应该被重新 execute（幂等保护）
        assertEquals(0, startExecCount.get(), "已完成的 start-01 不得重复执行");
        assertEquals(0, node1ExecCount.get(), "已完成的 llm-01 不得重复执行");
        assertEquals(0, node2ExecCount.get(), "已完成的 tool-01 不得重复执行");

        // 2. 属于第 2 层的未执行节点 (reply-01) 必须正常调度执行 1 次
        assertEquals(1, node3ExecCount.get(), "断点续跑后，后续层节点 reply-01 必须被执行");

        // 3. 最终返回的结果不为空且已成功保存
        assertNotNull(response);
        verify(runtimeService, atLeastOnce()).saveRunSuccess(runtimeDO);

        // 4. 工作流正常顺利完结后，检查点应已被安全清理
        DagCheckpointManager.DagCheckpoint remainingCheckpoint = checkpointManager.loadCheckpoint(String.valueOf(runtimeId));
        assertNull(remainingCheckpoint, "全流程正常完成后，检查点应已自动清理");
    }

    @Test
    @DisplayName("验证异常情况：未找到检查点时抛出异常")
    public void testResumeFlowNotFoundCheckpoint() {
        Long runtimeId = 7777L;
        KbRuntimeDO runtimeDO = new KbRuntimeDO();
        runtimeDO.setId(runtimeId);
        when(runtimeService.getById(runtimeId)).thenReturn(runtimeDO);

        ServiceException ex = assertThrows(ServiceException.class, () -> flowService.resumeFlow(runtimeId, null));
        assertTrue(ex.getMessage().contains("未找到可恢复的工作流检查点"));
    }
}
