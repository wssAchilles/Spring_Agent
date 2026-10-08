package tech.qiantong.qknow.kb.biz.flow;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.common.exception.ServiceException;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.runtime.KbRuntimeDO;
import tech.qiantong.qknow.module.kb.dal.enums.FlowNodeTypeEnums;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.module.kb.service.flow.IKbFlowEdgeService;
import tech.qiantong.qknow.module.kb.service.flow.IKbFlowNodeService;
import tech.qiantong.qknow.module.kb.service.flow.bo.BaseNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.KbFlowBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO;
import tech.qiantong.qknow.module.kb.service.flow.factory.NodeFactory;
import tech.qiantong.qknow.module.kb.service.flow.impl.KbFlowServiceImpl;
import tech.qiantong.qknow.module.kb.service.runtime.IKbRuntimeService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 现代 DAG 工作流引擎契约测试
 * 验证：环路死锁快速检测、拓扑分层并行执行、条件分支动态递归剪枝与状态标记
 */
public class KbFlowServiceDagTest {

    private KbFlowServiceImpl flowService;
    private IKbFlowNodeService flowNodeService;
    private IKbFlowEdgeService flowEdgeService;
    private IKbRuntimeService runtimeService;
    private NodeFactory nodeFactory;

    @BeforeEach
    public void setup() {
        flowService = new KbFlowServiceImpl();
        flowNodeService = mock(IKbFlowNodeService.class);
        flowEdgeService = mock(IKbFlowEdgeService.class);
        runtimeService = mock(IKbRuntimeService.class);
        nodeFactory = mock(NodeFactory.class);

        ReflectionTestUtils.setField(flowService, "flowNodeService", flowNodeService);
        ReflectionTestUtils.setField(flowService, "flowEdgeService", flowEdgeService);
        ReflectionTestUtils.setField(flowService, "runtimeService", runtimeService);
        ReflectionTestUtils.setField(flowService, "nodeFactory", nodeFactory);
    }

    private KbFlowNodeDO createNode(String uuid, String name, FlowNodeTypeEnums type) {
        KbFlowNodeDO node = new KbFlowNodeDO();
        node.setUuid(uuid);
        node.setName(name);
        node.setType(type.getCode());
        node.setConfig("{}");
        node.setInput("[]");
        node.setOutput("[]");
        return node;
    }

    private KbFlowEdgeDO createEdge(String source, String target) {
        KbFlowEdgeDO edge = new KbFlowEdgeDO();
        edge.setSourceNodeUuid(source);
        edge.setTargetNodeUuid(target);
        return edge;
    }

    @Test
    @DisplayName("测试环路检测：遇到环路流程应立即抛出 ServiceException 避免死循环")
    public void testCycleDetection() {
        KbFlowNodeDO start = createNode("start-1", "开始", FlowNodeTypeEnums.START);
        KbFlowNodeDO nodeA = createNode("node-a", "节点A", FlowNodeTypeEnums.LLM);
        KbFlowNodeDO nodeB = createNode("node-b", "节点B", FlowNodeTypeEnums.LLM);

        List<KbFlowNodeDO> nodes = List.of(start, nodeA, nodeB);
        // start -> A -> B -> A 环路
        List<KbFlowEdgeDO> edges = List.of(
                createEdge("start-1", "node-a"),
                createEdge("node-a", "node-b"),
                createEdge("node-b", "node-a")
        );

        KbFlowBO flowBO = new KbFlowBO();
        flowBO.setBotId(100L);
        flowBO.setNodeList(nodes);
        flowBO.setEdgeList(edges);

        RuntimeContextBO context = new RuntimeContextBO(new KbRuntimeDO(), new JSONObject());

        ServiceException ex = assertThrows(ServiceException.class, () -> flowService.executeFlow(flowBO, context));
        assertTrue(ex.getMessage().contains("环路死锁"));
    }

    @Test
    @DisplayName("测试拓扑分层与并行执行：经典菱形分支 Start -> [A, B] -> Reply 正确调度")
    public void testParallelExecution() {
        KbFlowNodeDO start = createNode("start-1", "开始", FlowNodeTypeEnums.START);
        KbFlowNodeDO branchA = createNode("branch-a", "分支A", FlowNodeTypeEnums.LLM);
        KbFlowNodeDO branchB = createNode("branch-b", "分支B", FlowNodeTypeEnums.LLM);
        KbFlowNodeDO reply = createNode("reply-1", "回复", FlowNodeTypeEnums.REPLY);

        List<KbFlowNodeDO> nodes = List.of(start, branchA, branchB, reply);
        List<KbFlowEdgeDO> edges = List.of(
                createEdge("start-1", "branch-a"),
                createEdge("start-1", "branch-b"),
                createEdge("branch-a", "reply-1"),
                createEdge("branch-b", "reply-1")
        );

        KbFlowBO flowBO = new KbFlowBO();
        flowBO.setBotId(200L);
        flowBO.setNodeList(nodes);
        flowBO.setEdgeList(edges);

        KbRuntimeDO runtimeDO = new KbRuntimeDO();
        runtimeDO.setId(1L);
        RuntimeContextBO context = new RuntimeContextBO(runtimeDO, new JSONObject());

        // Mock node executions
        BaseNodeBO startMock = mock(BaseNodeBO.class);
        when(startMock.execute(any())).thenReturn(NodeRunResultBO.success("start-1", "开始", Map.of()));

        BaseNodeBO branchAMock = mock(BaseNodeBO.class);
        when(branchAMock.execute(any())).thenReturn(NodeRunResultBO.success("branch-a", "分支A", Map.of("a.text", "resA")));

        BaseNodeBO branchBMock = mock(BaseNodeBO.class);
        when(branchBMock.execute(any())).thenReturn(NodeRunResultBO.success("branch-b", "分支B", Map.of("b.text", "resB")));

        BaseNodeBO replyMock = mock(BaseNodeBO.class);
        when(replyMock.execute(any())).thenReturn(NodeRunResultBO.success("reply-1", "回复", Map.of("output", "done")));

        when(nodeFactory.createNode(eq(start), any())).thenReturn(startMock);
        when(nodeFactory.createNode(eq(branchA), any())).thenReturn(branchAMock);
        when(nodeFactory.createNode(eq(branchB), any())).thenReturn(branchBMock);
        when(nodeFactory.createNode(eq(reply), any())).thenReturn(replyMock);

        flowService.executeFlow(flowBO, context);

        // 验证每个节点都已被调度执行
        verify(startMock, times(1)).execute(any());
        verify(branchAMock, times(1)).execute(any());
        verify(branchBMock, times(1)).execute(any());
        verify(replyMock, times(1)).execute(any());
        verify(runtimeService, times(1)).saveRunSuccess(runtimeDO);
    }

    @Test
    @DisplayName("测试动态递归剪枝：条件节点未命中分支应被标记为 SKIPPED 且不提交物理计算")
    public void testDynamicConditionPruning() {
        KbFlowNodeDO start = createNode("start-1", "开始", FlowNodeTypeEnums.START);
        KbFlowNodeDO cond = createNode("cond-1", "条件判断", FlowNodeTypeEnums.CONDITION);
        KbFlowNodeDO trueBranch = createNode("true-branch", "命中分支", FlowNodeTypeEnums.LLM);
        KbFlowNodeDO falseBranch = createNode("false-branch", "未命中分支", FlowNodeTypeEnums.LLM);
        KbFlowNodeDO falseSub = createNode("false-sub", "未命中子节点", FlowNodeTypeEnums.LLM);

        List<KbFlowNodeDO> nodes = List.of(start, cond, trueBranch, falseBranch, falseSub);
        List<KbFlowEdgeDO> edges = List.of(
                createEdge("start-1", "cond-1"),
                createEdge("cond-1", "true-branch"),
                createEdge("cond-1", "false-branch"),
                createEdge("false-branch", "false-sub")
        );

        KbFlowBO flowBO = new KbFlowBO();
        flowBO.setBotId(300L);
        flowBO.setNodeList(nodes);
        flowBO.setEdgeList(edges);

        KbRuntimeDO runtimeDO = new KbRuntimeDO();
        runtimeDO.setId(2L);
        RuntimeContextBO context = new RuntimeContextBO(runtimeDO, new JSONObject());

        BaseNodeBO startMock = mock(BaseNodeBO.class);
        when(startMock.execute(any())).thenReturn(NodeRunResultBO.success("start-1", "开始", Map.of()));

        // 条件节点只选择了 "true-branch"，未选择 "false-branch"
        BaseNodeBO condMock = mock(BaseNodeBO.class);
        NodeRunResultBO condResult = NodeRunResultBO.success("cond-1", "条件判断", Map.of());
        condResult.setNextNodeIds(List.of("true-branch"));
        when(condMock.execute(any())).thenReturn(condResult);

        BaseNodeBO trueBranchMock = mock(BaseNodeBO.class);
        when(trueBranchMock.execute(any())).thenReturn(NodeRunResultBO.success("true-branch", "命中分支", Map.of()));

        BaseNodeBO falseBranchMock = mock(BaseNodeBO.class);
        BaseNodeBO falseSubMock = mock(BaseNodeBO.class);

        when(nodeFactory.createNode(eq(start), any())).thenReturn(startMock);
        when(nodeFactory.createNode(eq(cond), any())).thenReturn(condMock);
        when(nodeFactory.createNode(eq(trueBranch), any())).thenReturn(trueBranchMock);
        when(nodeFactory.createNode(eq(falseBranch), any())).thenReturn(falseBranchMock);
        when(nodeFactory.createNode(eq(falseSub), any())).thenReturn(falseSubMock);

        flowService.executeFlow(flowBO, context);

        // 验证命中分支被物理执行
        verify(trueBranchMock, times(1)).execute(any());

        // 验证未命中的下游子树完全被跳过，绝无物理执行！
        verify(falseBranchMock, never()).execute(any());
        verify(falseSubMock, never()).execute(any());

        // 验证保存的节点中包含了 SKIPPED 状态的节点
        verify(runtimeService, atLeastOnce()).saveRuntimeNode(argThat(res ->
                RuntimeStatusEnums.SKIPPED.getCode().equals(res.getStatus()) &&
                        ("false-branch".equals(res.getNodeUuid()) || "false-sub".equals(res.getNodeUuid()))
        ), any());
    }
}
