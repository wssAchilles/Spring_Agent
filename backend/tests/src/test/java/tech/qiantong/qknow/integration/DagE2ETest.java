package tech.qiantong.qknow.integration;

import com.alibaba.fastjson2.JSONObject;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.flow.FlowExecutor;
import tech.qiantong.qknow.hermes.flow.bo.*;
import tech.qiantong.qknow.hermes.flow.dag.DagExecutor;
import tech.qiantong.qknow.hermes.flow.dag.DagUtils;
import tech.qiantong.qknow.hermes.flow.enums.FlowNodeTypeEnums;
import tech.qiantong.qknow.hermes.flow.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.hermes.flow.expression.ExpressionEngine;
import tech.qiantong.qknow.hermes.flow.factory.NodeFactory;
import tech.qiantong.qknow.hermes.flow.rag.RagRetrievalService;
import tech.qiantong.qknow.hermes.grpc.HermesGrpcService;
import tech.qiantong.qknow.hermes.proto.FlowEvent;
import tech.qiantong.qknow.hermes.proto.FlowNode;
import tech.qiantong.qknow.hermes.proto.FlowRequest;
import tech.qiantong.qknow.hermes.proto.NodeResult;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DagE2ETest {

    @Mock
    private DagExecutor dagExecutor;

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ExpressionEngine expressionEngine;

    @Mock
    private RagRetrievalService ragRetrievalService;

    private FlowExecutor flowExecutor;

    @BeforeEach
    void setUp() {
        flowExecutor = new FlowExecutor(dagExecutor, null);
    }

    private FlowNode protoNode(String uuid, String name, String type) {
        return FlowNode.newBuilder()
                .setUuid(uuid)
                .setName(name)
                .setType(type)
                .build();
    }

    private FlowRequest buildProtoRequest(String flowId, FlowNode... nodes) {
        FlowRequest.Builder builder = FlowRequest.newBuilder()
                .setRequestId("req-" + UUID.randomUUID().toString().substring(0, 8))
                .setFlowId(flowId);
        for (FlowNode n : nodes) {
            builder.addNodes(n);
        }
        return builder.build();
    }

    private KbFlowNodeDO buildNode(String uuid, String name, Integer type, String config) {
        KbFlowNodeDO node = new KbFlowNodeDO();
        node.setUuid(uuid);
        node.setName(name);
        node.setType(type);
        node.setConfig(config);
        node.setInput("[]");
        return node;
    }

    private KbFlowEdgeDO buildEdge(String source, String target) {
        KbFlowEdgeDO edge = new KbFlowEdgeDO();
        edge.setSourceNodeUuid(source);
        edge.setTargetNodeUuid(target);
        return edge;
    }

    private RuntimeContextBO buildContext() {
        KbRuntimeDO runtime = new KbRuntimeDO();
        return new RuntimeContextBO(runtime, new JSONObject());
    }

    private void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("串行执行 E2E")
    class SerialExecution {

        @Test
        @DisplayName("START → LLM → REPLY 串行执行全部成功")
        void startLlmReplySerialExecution() {
            FlowNode start = protoNode("n1", "开始", "start");
            FlowNode llm = protoNode("n2", "LLM处理", "llm");
            FlowNode reply = protoNode("n3", "回复", "reply");
            FlowRequest request = buildProtoRequest("flow-serial", start, llm, reply);

            NodeRunResultBO r1 = NodeRunResultBO.success("n1", "开始", Map.of("step", 1));
            r1.setDuration(10L);
            NodeRunResultBO r2 = NodeRunResultBO.success("n2", "LLM处理", Map.of("answer", "AI是人工智能"));
            r2.setDuration(500L);
            NodeRunResultBO r3 = NodeRunResultBO.success("n3", "回复", Map.of("output", "最终回复"));
            r3.setDuration(20L);

            when(dagExecutor.execute(any(), any(), any())).thenReturn(List.of(r1, r2, r3));

            List<NodeRunResultBO> results = flowExecutor.execute(request);

            assertEquals(3, results.size());
            assertEquals("n1", results.get(0).getNodeUuid());
            assertEquals("n2", results.get(1).getNodeUuid());
            assertEquals("n3", results.get(2).getNodeUuid());
            assertTrue(results.stream().allMatch(r ->
                    RuntimeStatusEnums.SUCCESS.getCode().equals(r.getStatus())));
            assertNotNull(results.get(1).getOutput().get("answer"));
        }

        @Test
        @DisplayName("串行执行总耗时等于各节点耗时之和")
        void serialTotalDurationIsSum() {
            FlowNode start = protoNode("n1", "开始", "start");
            FlowNode llm = protoNode("n2", "LLM", "llm");
            FlowRequest request = buildProtoRequest("flow-time", start, llm);

            NodeRunResultBO r1 = NodeRunResultBO.success("n1", "开始", Map.of());
            r1.setDuration(100L);
            NodeRunResultBO r2 = NodeRunResultBO.success("n2", "LLM", Map.of());
            r2.setDuration(300L);

            when(dagExecutor.execute(any(), any(), any())).thenReturn(List.of(r1, r2));

            List<NodeRunResultBO> results = flowExecutor.execute(request);

            long total = results.stream().mapToLong(r -> r.getDuration() != null ? r.getDuration() : 0).sum();
            assertEquals(400L, total);
        }
    }

    @Nested
    @DisplayName("并行分支 E2E")
    class ParallelBranchExecution {

        @Test
        @DisplayName("START 分叉到两个 LLM 并行执行后汇聚 REPLY")
        void parallelBranchesExecuteAndMerge() {
            FlowNode start = protoNode("n1", "开始", "start");
            FlowNode branchA = protoNode("n2", "分支A", "llm");
            FlowNode branchB = protoNode("n3", "分支B", "llm");
            FlowNode reply = protoNode("n4", "汇聚回复", "reply");
            FlowRequest request = buildProtoRequest("flow-parallel", start, branchA, branchB, reply);

            NodeRunResultBO r1 = NodeRunResultBO.success("n1", "开始", Map.of());
            r1.setDuration(10L);
            NodeRunResultBO r2 = NodeRunResultBO.success("n2", "分支A", Map.of("branch", "A"));
            r2.setDuration(200L);
            NodeRunResultBO r3 = NodeRunResultBO.success("n3", "分支B", Map.of("branch", "B"));
            r3.setDuration(180L);
            NodeRunResultBO r4 = NodeRunResultBO.success("n4", "汇聚回复", Map.of("merged", "A+B"));
            r4.setDuration(30L);

            when(dagExecutor.execute(any(), any(), any())).thenReturn(List.of(r1, r2, r3, r4));

            List<NodeRunResultBO> results = flowExecutor.execute(request);

            assertEquals(4, results.size());
            assertTrue(results.stream().allMatch(r ->
                    RuntimeStatusEnums.SUCCESS.getCode().equals(r.getStatus())));
            assertEquals("A+B", results.get(3).getOutput().get("merged"));
        }

        @Test
        @DisplayName("并行分支 DAG 拓扑正确 - 无环")
        void parallelDagIsAcyclic() {
            List<KbFlowNodeDO> nodes = List.of(
                    buildNode("n1", "开始", FlowNodeTypeEnums.START.getCode(), "{}"),
                    buildNode("n2", "分支A", FlowNodeTypeEnums.LLM.getCode(), "{}"),
                    buildNode("n3", "分支B", FlowNodeTypeEnums.LLM.getCode(), "{}"),
                    buildNode("n4", "回复", FlowNodeTypeEnums.REPLY.getCode(), "{}")
            );
            List<KbFlowEdgeDO> edges = List.of(
                    buildEdge("n1", "n2"),
                    buildEdge("n1", "n3"),
                    buildEdge("n2", "n4"),
                    buildEdge("n3", "n4")
            );

            assertFalse(DagUtils.hasCycle(nodes, edges), "并行分支 DAG 不应有环");
        }
    }

    @Nested
    @DisplayName("条件网关 E2E")
    class ConditionGateway {

        @Test
        @DisplayName("条件节点根据表达式选择正确分支")
        void conditionNodeRoutesCorrectly() {
            FlowNode start = protoNode("n1", "开始", "start");
            FlowNode condition = protoNode("n2", "条件判断", "condition");
            FlowNode branchA = protoNode("n3", "高分分支", "reply");
            FlowNode branchB = protoNode("n4", "低分分支", "reply");
            FlowRequest request = FlowRequest.newBuilder()
                    .setRequestId("req-cond-1")
                    .setFlowId("flow-condition")
                    .addNodes(start)
                    .addNodes(condition)
                    .addNodes(branchA)
                    .addNodes(branchB)
                    .addEdges(tech.qiantong.qknow.hermes.proto.FlowEdge.newBuilder()
                            .setSourceNodeUuid("n1").setTargetNodeUuid("n2").build())
                    .addEdges(tech.qiantong.qknow.hermes.proto.FlowEdge.newBuilder()
                            .setSourceNodeUuid("n2").setTargetNodeUuid("n3").build())
                    .addEdges(tech.qiantong.qknow.hermes.proto.FlowEdge.newBuilder()
                            .setSourceNodeUuid("n2").setTargetNodeUuid("n4").build())
                    .setRuntimeContextJson("{\"score\": 0.95}")
                    .build();

            NodeRunResultBO r1 = NodeRunResultBO.success("n1", "开始", Map.of());
            r1.setDuration(10L);
            NodeRunResultBO r2 = NodeRunResultBO.success("n2", "条件判断", Map.of("branch", "high"));
            r2.setDuration(5L);
            r2.setNextNodeIds(List.of("n3"));
            NodeRunResultBO r3 = NodeRunResultBO.success("n3", "高分分支", Map.of("result", "优秀"));
            r3.setDuration(20L);

            when(dagExecutor.execute(any(), any(), any())).thenReturn(List.of(r1, r2, r3));

            List<NodeRunResultBO> results = flowExecutor.execute(request);

            assertEquals(3, results.size());
            assertEquals("n3", results.get(2).getNodeUuid());
            assertEquals("优秀", results.get(2).getOutput().get("result"));
        }

        @Test
        @DisplayName("条件节点将变量传递到运行时上下文")
        void conditionNodePassesVariablesToContext() {
            List<KbFlowNodeDO> nodes = List.of(
                    buildNode("n1", "条件", FlowNodeTypeEnums.CONDITION.getCode(),
                            "{\"expression\":\"score > 0.5\"}")
            );

            RuntimeContextBO context = buildContext();
            context.getVariables().put("score", 0.8);

            when(expressionEngine.evaluate("score > 0.5", context.getVariables()))
                    .thenReturn(true);

            assertTrue(expressionEngine.evaluate("score > 0.5", context.getVariables()));
        }
    }

    @Nested
    @DisplayName("节点失败 E2E")
    class NodeFailure {

        @Test
        @DisplayName("LLM 节点失败时终止流程并返回错误")
        void llmNodeFailureStopsFlow() {
            FlowNode start = protoNode("n1", "开始", "start");
            FlowNode llm = protoNode("n2", "LLM处理", "llm");
            FlowRequest request = buildProtoRequest("flow-fail", start, llm);

            NodeRunResultBO r1 = NodeRunResultBO.success("n1", "开始", Map.of());
            r1.setDuration(10L);
            NodeRunResultBO r2 = NodeRunResultBO.failure("n2", "LLM处理", "API调用超时");

            when(dagExecutor.execute(any(), any(), any())).thenReturn(List.of(r1, r2));

            List<NodeRunResultBO> results = flowExecutor.execute(request);

            assertEquals(2, results.size());
            assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), results.get(0).getStatus());
            assertEquals(RuntimeStatusEnums.ERROR.getCode(), results.get(1).getStatus());
            assertEquals("API调用超时", results.get(1).getErrorMessage());
        }

        @Test
        @DisplayName("DagExecutor 异常时 FlowExecutor 传播异常")
        void dagExecutorExceptionPropagates() {
            FlowNode start = protoNode("n1", "开始", "start");
            FlowRequest request = buildProtoRequest("flow-except", start);

            when(dagExecutor.execute(any(), any(), any()))
                    .thenThrow(new IllegalStateException("工作流存在环"));

            assertThrows(IllegalStateException.class, () -> flowExecutor.execute(request));
        }

        @Test
        @DisplayName("并行分支中一个失败不影响另一个完成")
        void parallelBranchPartialFailure() {
            FlowNode start = protoNode("n1", "开始", "start");
            FlowNode branchA = protoNode("n2", "分支A", "llm");
            FlowNode branchB = protoNode("n3", "分支B", "llm");
            FlowRequest request = buildProtoRequest("flow-partial", start, branchA, branchB);

            NodeRunResultBO r1 = NodeRunResultBO.success("n1", "开始", Map.of());
            r1.setDuration(10L);
            NodeRunResultBO r2 = NodeRunResultBO.failure("n2", "分支A", "超时");
            NodeRunResultBO r3 = NodeRunResultBO.success("n3", "分支B", Map.of("ok", true));
            r3.setDuration(100L);

            when(dagExecutor.execute(any(), any(), any())).thenReturn(List.of(r1, r2, r3));

            List<NodeRunResultBO> results = flowExecutor.execute(request);

            assertEquals(3, results.size());
            assertEquals(RuntimeStatusEnums.ERROR.getCode(), results.get(1).getStatus());
            assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), results.get(2).getStatus());
        }
    }

    @Nested
    @DisplayName("gRPC → FlowExecutor 集成")
    class GrpcFlowIntegration {

        @Test
        @DisplayName("FlowRequest gRPC 对象正确映射到 FlowExecutor")
        void grpcRequestMapsToFlowExecutor() {
            FlowNode n1 = protoNode("n1", "开始", "start");
            FlowNode n2 = protoNode("n2", "回复", "reply");
            FlowRequest request = buildProtoRequest("flow-grpc", n1, n2);

            NodeRunResultBO r1 = NodeRunResultBO.success("n1", "开始", Map.of());
            r1.setDuration(10L);
            NodeRunResultBO r2 = NodeRunResultBO.success("n2", "回复", Map.of("text", "你好"));
            r2.setDuration(5L);

            when(dagExecutor.execute(any(), any(), any())).thenReturn(List.of(r1, r2));

            List<NodeRunResultBO> results = flowExecutor.execute(request);

            assertEquals(2, results.size());
            verify(dagExecutor).execute(any(), any(), any());
        }

        @Test
        @DisplayName("FlowEvent 正确映射 NodeRunResultBO")
        void flowEventMapsNodeResults() {
            NodeRunResultBO result = NodeRunResultBO.success("n1", "开始", Map.of("key", "val"));
            result.setDuration(100L);

            FlowEvent event = FlowEvent.newBuilder()
                    .setRequestId("req-1")
                    .setNodeResult(NodeResult.newBuilder()
                            .setNodeUuid(result.getNodeUuid())
                            .setNodeName(result.getNodeName())
                            .setStatus(result.getStatus())
                            .setOutputJson(result.getOutput().toString())
                            .setDurationMs(result.getDuration())
                            .build())
                    .build();

            assertEquals("req-1", event.getRequestId());
            assertTrue(event.hasNodeResult());
            assertEquals("n1", event.getNodeResult().getNodeUuid());
            assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), event.getNodeResult().getStatus());
            assertEquals(100L, event.getNodeResult().getDurationMs());
        }

        @Test
        @DisplayName("HermesGrpcService.executeFlow 推送节点结果和完成事件")
        void grpcServiceExecuteFlowStreamsResults() {
            FlowExecutor mockFlowExecutor = mock(FlowExecutor.class);
            HermesGrpcService service = new HermesGrpcService();
            setField(service, "flowExecutor", mockFlowExecutor);

            FlowRequest request = buildProtoRequest("flow-grpc-service",
                    protoNode("n1", "开始", "start"));
            NodeRunResultBO result = NodeRunResultBO.success("n1", "开始", Map.of("ok", true));
            result.setDuration(12L);
            when(mockFlowExecutor.execute(request)).thenReturn(List.of(result));

            List<FlowEvent> events = new ArrayList<>();
            service.executeFlow(request, new StreamObserver<>() {
                @Override
                public void onNext(FlowEvent value) {
                    events.add(value);
                }

                @Override
                public void onError(Throwable t) {
                    fail(t);
                }

                @Override
                public void onCompleted() {
                    events.add(FlowEvent.newBuilder().setRequestId("completed").build());
                }
            });

            assertEquals(3, events.size());
            assertTrue(events.get(0).hasNodeResult());
            assertEquals("n1", events.get(0).getNodeResult().getNodeUuid());
            assertTrue(events.get(1).hasFlowCompleted());
            assertFalse(events.stream().anyMatch(FlowEvent::hasError));
        }
    }

    @Nested
    @DisplayName("DAG 拓扑验证")
    class DagTopologyValidation {

        @Test
        @DisplayName("线性 DAG 无并行组")
        void linearDagHasNoParallelGroups() {
            List<KbFlowNodeDO> nodes = List.of(
                    buildNode("n1", "开始", FlowNodeTypeEnums.START.getCode(), "{}"),
                    buildNode("n2", "LLM", FlowNodeTypeEnums.LLM.getCode(), "{}"),
                    buildNode("n3", "回复", FlowNodeTypeEnums.REPLY.getCode(), "{}")
            );
            List<KbFlowEdgeDO> edges = List.of(
                    buildEdge("n1", "n2"),
                    buildEdge("n2", "n3")
            );

            assertFalse(DagUtils.hasCycle(nodes, edges));
            List<List<String>> groups = DagUtils.getParallelGroups(nodes, edges);
            assertEquals(3, groups.size());
            assertEquals(1, groups.get(0).size());
            assertEquals(1, groups.get(1).size());
            assertEquals(1, groups.get(2).size());
        }

        @Test
        @DisplayName("分叉-汇聚 DAG 有两个并行组")
        void forkJoinDagHasParallelGroup() {
            List<KbFlowNodeDO> nodes = List.of(
                    buildNode("n1", "开始", FlowNodeTypeEnums.START.getCode(), "{}"),
                    buildNode("n2", "A", FlowNodeTypeEnums.LLM.getCode(), "{}"),
                    buildNode("n3", "B", FlowNodeTypeEnums.LLM.getCode(), "{}"),
                    buildNode("n4", "回复", FlowNodeTypeEnums.REPLY.getCode(), "{}")
            );
            List<KbFlowEdgeDO> edges = List.of(
                    buildEdge("n1", "n2"),
                    buildEdge("n1", "n3"),
                    buildEdge("n2", "n4"),
                    buildEdge("n3", "n4")
            );

            assertFalse(DagUtils.hasCycle(nodes, edges));
            List<List<String>> groups = DagUtils.getParallelGroups(nodes, edges);
            assertEquals(3, groups.size());
            assertEquals(1, groups.get(0).size());
            assertEquals(2, groups.get(1).size());
            assertTrue(groups.get(1).contains("n2"));
            assertTrue(groups.get(1).contains("n3"));
        }

        @Test
        @DisplayName("有环 DAG 检测正确")
        void cyclicDagDetected() {
            List<KbFlowNodeDO> nodes = List.of(
                    buildNode("n1", "A", FlowNodeTypeEnums.LLM.getCode(), "{}"),
                    buildNode("n2", "B", FlowNodeTypeEnums.LLM.getCode(), "{}")
            );
            List<KbFlowEdgeDO> edges = List.of(
                    buildEdge("n1", "n2"),
                    buildEdge("n2", "n1")
            );

            assertTrue(DagUtils.hasCycle(nodes, edges));
        }
    }
}
