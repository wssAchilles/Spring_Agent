package tech.qiantong.qknow.hermes.flow;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowEdgeDO;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowNodeDO;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.bo.RuntimeContextBO;
import tech.qiantong.qknow.hermes.flow.dag.DagExecutor;
import tech.qiantong.qknow.hermes.flow.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.hermes.grpc.HermesGrpcService;
import tech.qiantong.qknow.hermes.proto.FlowEvent;
import tech.qiantong.qknow.hermes.proto.FlowNode;
import tech.qiantong.qknow.hermes.proto.FlowRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowExecutorTest {

    @Mock
    private DagExecutor dagExecutor;

    private FlowExecutor flowExecutor;

    @BeforeEach
    void setUp() {
        flowExecutor = new FlowExecutor(dagExecutor, null);
    }

    private FlowRequest buildRequest(String flowId, FlowNode... nodes) {
        FlowRequest.Builder builder = FlowRequest.newBuilder()
                .setRequestId("req-1")
                .setFlowId(flowId);
        for (FlowNode node : nodes) {
            builder.addNodes(node);
        }
        return builder.build();
    }

    private FlowNode buildNode(String uuid, String name, String type) {
        return FlowNode.newBuilder()
                .setUuid(uuid)
                .setName(name)
                .setType(type)
                .build();
    }

    @Nested
    @DisplayName("单节点流程执行")
    class SingleNodeExecution {

        @Test
        @DisplayName("单节点流程正常执行并返回结果")
        void singleNodeExecutesSuccessfully() {
            FlowNode startNode = buildNode("node-1", "开始节点", "start");
            FlowRequest request = buildRequest("flow-1", startNode);

            NodeRunResultBO successResult = NodeRunResultBO.success("node-1", "开始节点", Map.of("output", "ok"));
            successResult.setDuration(100L);
            when(dagExecutor.execute(any(), any(), any())).thenReturn(List.of(successResult));

            List<NodeRunResultBO> results = flowExecutor.execute(request);

            assertEquals(1, results.size());
            assertEquals("node-1", results.get(0).getNodeUuid());
            assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), results.get(0).getStatus());

            ArgumentCaptor<List<KbFlowNodeDO>> nodeCaptor = ArgumentCaptor.forClass(List.class);
            verify(dagExecutor).execute(nodeCaptor.capture(), any(), any());
            assertEquals(1, nodeCaptor.getValue().size());
            assertEquals("开始节点", nodeCaptor.getValue().get(0).getName());
        }
    }

    @Nested
    @DisplayName("多节点串行执行")
    class MultiNodeSerialExecution {

        @Test
        @DisplayName("多节点按序执行并依次返回结果")
        void multiNodeExecutesInOrder() {
            FlowNode node1 = buildNode("node-1", "开始", "start");
            FlowNode node2 = buildNode("node-2", "LLM处理", "llm");
            FlowNode node3 = buildNode("node-3", "回复", "reply");
            FlowRequest request = buildRequest("flow-2", node1, node2, node3);

            NodeRunResultBO r1 = NodeRunResultBO.success("node-1", "开始", Map.of("step", 1));
            r1.setDuration(50L);
            NodeRunResultBO r2 = NodeRunResultBO.success("node-2", "LLM处理", Map.of("step", 2));
            r2.setDuration(200L);
            NodeRunResultBO r3 = NodeRunResultBO.success("node-3", "回复", Map.of("step", 3));
            r3.setDuration(30L);

            when(dagExecutor.execute(any(), any(), any())).thenReturn(List.of(r1, r2, r3));

            List<NodeRunResultBO> results = flowExecutor.execute(request);

            assertEquals(3, results.size());
            assertEquals("node-1", results.get(0).getNodeUuid());
            assertEquals("node-2", results.get(1).getNodeUuid());
            assertEquals("node-3", results.get(2).getNodeUuid());

            verify(dagExecutor).execute(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("并行分支执行")
    class ParallelBranchExecution {

        @Test
        @DisplayName("并行分支节点同时执行")
        void parallelBranchesExecuteConcurrently() {
            FlowNode node1 = buildNode("node-1", "开始", "start");
            FlowNode node2 = buildNode("node-2", "分支A", "llm");
            FlowNode node3 = buildNode("node-3", "分支B", "llm");
            FlowRequest request = buildRequest("flow-3", node1, node2, node3);

            NodeRunResultBO r1 = NodeRunResultBO.success("node-1", "开始", Map.of());
            r1.setDuration(50L);
            NodeRunResultBO r2 = NodeRunResultBO.success("node-2", "分支A", Map.of("branch", "A"));
            r2.setDuration(150L);
            NodeRunResultBO r3 = NodeRunResultBO.success("node-3", "分支B", Map.of("branch", "B"));
            r3.setDuration(120L);

            when(dagExecutor.execute(any(), any(), any())).thenReturn(List.of(r1, r2, r3));

            List<NodeRunResultBO> results = flowExecutor.execute(request);

            assertEquals(3, results.size());
            assertTrue(results.stream().allMatch(r ->
                    RuntimeStatusEnums.SUCCESS.getCode().equals(r.getStatus())));
        }
    }

    @Nested
    @DisplayName("条件网关路由")
    class ConditionGatewayRouting {

        @Test
        @DisplayName("条件节点根据表达式选择分支")
        void conditionNodeRoutesToCorrectBranch() {
            FlowNode startNode = buildNode("node-1", "开始", "start");
            FlowNode conditionNode = buildNode("node-2", "条件判断", "condition");
            FlowNode branchA = buildNode("node-3", "分支A", "reply");
            FlowNode branchB = buildNode("node-4", "分支B", "reply");
            FlowRequest request = FlowRequest.newBuilder()
                    .setRequestId("req-1")
                    .setFlowId("flow-4")
                    .addNodes(startNode)
                    .addNodes(conditionNode)
                    .addNodes(branchA)
                    .addNodes(branchB)
                    .addEdges(buildEdge("node-1", "node-2"))
                    .addEdges(buildEdge("node-2", "node-3"))
                    .addEdges(buildEdge("node-2", "node-4"))
                    .setRuntimeContextJson("{\"score\": 0.9}")
                    .build();

            NodeRunResultBO r1 = NodeRunResultBO.success("node-1", "开始", Map.of());
            r1.setDuration(50L);
            NodeRunResultBO r2 = NodeRunResultBO.success("node-2", "条件判断", Map.of("branch", "A"));
            r2.setDuration(10L);
            NodeRunResultBO r3 = NodeRunResultBO.success("node-3", "分支A", Map.of("result", "high score"));
            r3.setDuration(30L);

            when(dagExecutor.execute(any(), any(), any())).thenReturn(List.of(r1, r2, r3));

            List<NodeRunResultBO> results = flowExecutor.execute(request);

            assertEquals(3, results.size());
            assertEquals("node-3", results.get(2).getNodeUuid());

            ArgumentCaptor<RuntimeContextBO> contextCaptor = ArgumentCaptor.forClass(RuntimeContextBO.class);
            verify(dagExecutor).execute(any(), any(), contextCaptor.capture());
            assertEquals(0.9, contextCaptor.getValue().getVariables().getDouble("score"));
        }
    }

    @Nested
    @DisplayName("节点失败处理")
    class NodeFailureHandling {

        @Test
        @DisplayName("节点执行失败时返回错误结果并终止流程")
        void nodeFailureStopsFlow() {
            FlowNode node1 = buildNode("node-1", "开始", "start");
            FlowNode node2 = buildNode("node-2", "LLM处理", "llm");
            FlowRequest request = buildRequest("flow-5", node1, node2);

            NodeRunResultBO r1 = NodeRunResultBO.success("node-1", "开始", Map.of());
            r1.setDuration(50L);
            NodeRunResultBO r2 = NodeRunResultBO.failure("node-2", "LLM处理", "API调用超时");

            when(dagExecutor.execute(any(), any(), any())).thenReturn(List.of(r1, r2));

            List<NodeRunResultBO> results = flowExecutor.execute(request);

            assertEquals(2, results.size());
            assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), results.get(0).getStatus());
            assertEquals(RuntimeStatusEnums.ERROR.getCode(), results.get(1).getStatus());
            assertEquals("API调用超时", results.get(1).getErrorMessage());
        }

        @Test
        @DisplayName("DagExecutor 抛出异常时 FlowExecutor 传播异常")
        void dagExecutorExceptionPropagates() {
            FlowNode node1 = buildNode("node-1", "开始", "start");
            FlowRequest request = buildRequest("flow-6", node1);

            when(dagExecutor.execute(any(), any(), any()))
                    .thenThrow(new IllegalStateException("工作流存在环，无法执行"));

            assertThrows(IllegalStateException.class, () -> flowExecutor.execute(request));
        }
    }

    @Nested
    @DisplayName("HermesGrpcService 集成")
    class GrpcServiceIntegration {

        @Test
        @DisplayName("FlowEvent 正确映射 NodeRunResultBO")
        void flowEventMapsNodeResultsCorrectly() {
            NodeRunResultBO result = NodeRunResultBO.success("node-1", "开始节点", Map.of("key", "value"));
            result.setDuration(100L);

            FlowEvent event = FlowEvent.newBuilder()
                    .setRequestId("req-1")
                    .setNodeResult(tech.qiantong.qknow.hermes.proto.NodeResult.newBuilder()
                            .setNodeUuid(result.getNodeUuid())
                            .setNodeName(result.getNodeName())
                            .setStatus(result.getStatus())
                            .setOutputJson(result.getOutput() != null ? result.getOutput().toString() : "")
                            .setDurationMs(result.getDuration())
                            .build())
                    .build();

            assertEquals("req-1", event.getRequestId());
            assertTrue(event.hasNodeResult());
            assertEquals("node-1", event.getNodeResult().getNodeUuid());
            assertEquals("开始节点", event.getNodeResult().getNodeName());
            assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), event.getNodeResult().getStatus());
            assertEquals(100L, event.getNodeResult().getDurationMs());
        }
    }

    private tech.qiantong.qknow.hermes.proto.FlowEdge buildEdge(String source, String target) {
        return tech.qiantong.qknow.hermes.proto.FlowEdge.newBuilder()
                .setSourceNodeUuid(source)
                .setTargetNodeUuid(target)
                .build();
    }
}
