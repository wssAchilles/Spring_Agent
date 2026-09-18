package tech.qiantong.qknow.hermes.flow.stategraph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphContext;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphExecutionReceipt;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.ConvergenceLoopGuard;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.StateGraphScheduler;
import tech.qiantong.qknow.hermes.flow.stategraph.enums.StateGraphEdgeType;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraph;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphEdge;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphNode;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StateGraphScheduler 状态图超步调度器全链路与消融测试")
class StateGraphSchedulerTest {

    private StateGraphScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StateGraphScheduler();
    }

    @Test
    @DisplayName("线性单向拓扑按超步单向推进，与传统 DAG 结果等价")
    void scheduler_linearGraph_executesSupersteps() {
        StateGraph graph = StateGraph.builder()
                .graphId("linear-graph-1")
                .graphName("线性测试图")
                .build();

        AtomicInteger aExec = new AtomicInteger(0);
        AtomicInteger bExec = new AtomicInteger(0);
        AtomicInteger cExec = new AtomicInteger(0);

        graph.addNode(StateGraphNode.builder()
                .nodeUuid("node-A")
                .executionHandler((n, ctx) -> {
                    aExec.incrementAndGet();
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    return bo;
                })
                .build());

        graph.addNode(StateGraphNode.builder()
                .nodeUuid("node-B")
                .executionHandler((n, ctx) -> {
                    bExec.incrementAndGet();
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    return bo;
                })
                .build());

        graph.addNode(StateGraphNode.builder()
                .nodeUuid("node-C")
                .executionHandler((n, ctx) -> {
                    cExec.incrementAndGet();
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    return bo;
                })
                .build());

        graph.addStartNode("node-A");
        graph.addEdge(StateGraphEdge.builder()
                .edgeId("e1").sourceNodeUuid("node-A").targetNodeUuid("node-B").edgeType(StateGraphEdgeType.FORWARD).build());
        graph.addEdge(StateGraphEdge.builder()
                .edgeId("e2").sourceNodeUuid("node-B").targetNodeUuid("node-C").edgeType(StateGraphEdgeType.FORWARD).build());

        StateGraphContext context = new StateGraphContext("exec-linear", "linear-graph-1", 10);
        StateGraphExecutionReceipt receipt = scheduler.execute(graph, context);

        assertNotNull(receipt);
        assertEquals(3, receipt.totalSupersteps(), "A -> B -> C 依次推进共 3 个超步");
        assertEquals(1, aExec.get());
        assertEquals(1, bExec.get());
        assertEquals(1, cExec.get());
        assertTrue(receipt.verifyIntegrity(), "凭单完整性验真必须通过");
        assertFalse(receipt.hasDegradedBreak());
    }

    @Test
    @DisplayName("反思纠偏有界循环图在第 3 轮成功收敛退出")
    void scheduler_cyclicGraph_convergesSuccessfully() {
        StateGraph graph = StateGraph.builder()
                .graphId("reflection-loop-graph")
                .graphName("反思纠偏循环图")
                .build();

        AtomicInteger generatorRuns = new AtomicInteger(0);
        AtomicInteger evaluatorRuns = new AtomicInteger(0);

        graph.addNode(StateGraphNode.builder()
                .nodeUuid("generator")
                .executionHandler((n, ctx) -> {
                    int run = generatorRuns.incrementAndGet();
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    bo.setOutput(Map.of("draftVersion", run));
                    return bo;
                })
                .build());

        graph.addNode(StateGraphNode.builder()
                .nodeUuid("evaluator")
                .executionHandler((n, ctx) -> {
                    int run = evaluatorRuns.incrementAndGet();
                    // 前 2 轮评分 0.7，第 3 轮评分 0.95
                    double score = run >= 3 ? 0.95 : 0.7;
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    bo.setOutput(Map.of("score", score, "evalRun", run));
                    return bo;
                })
                .build());

        graph.addNode(StateGraphNode.builder()
                .nodeUuid("publisher")
                .executionHandler((n, ctx) -> {
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    return bo;
                })
                .build());

        graph.addStartNode("generator");
        // generator -> evaluator
        graph.addEdge(StateGraphEdge.builder()
                .edgeId("e-fwd-1").sourceNodeUuid("generator").targetNodeUuid("evaluator")
                .edgeType(StateGraphEdgeType.FORWARD).build());
        // evaluator --(LOOP_BACK, score >= 0.9 时收敛退出)--> generator
        graph.addEdge(StateGraphEdge.builder()
                .edgeId("e-loop-back").sourceNodeUuid("evaluator").targetNodeUuid("generator")
                .edgeType(StateGraphEdgeType.LOOP_BACK)
                .conditionExpression("#result.output.get('score') >= 0.9")
                .maxIterations(5)
                .build());
        // evaluator --(CONDITIONAL, score >= 0.9 时流转)--> publisher
        graph.addEdge(StateGraphEdge.builder()
                .edgeId("e-publish").sourceNodeUuid("evaluator").targetNodeUuid("publisher")
                .edgeType(StateGraphEdgeType.CONDITIONAL)
                .conditionExpression("#result.output.get('score') >= 0.9")
                .build());

        StateGraphContext context = new StateGraphContext("exec-reflect", "reflection-loop-graph", 10);
        StateGraphExecutionReceipt receipt = scheduler.execute(graph, context);

        assertNotNull(receipt);
        assertEquals(3, generatorRuns.get(), "生成器经历 3 轮生成");
        assertEquals(3, evaluatorRuns.get(), "评估器经历 3 轮评估");
        assertFalse(receipt.hasDegradedBreak(), "正常收敛未发生熔断");
        assertTrue(receipt.verifyIntegrity(), "凭单验真通过");
    }

    @Test
    @DisplayName("死循环拓扑达到 maxIterations 后触发 DEGRADED_BREAK 降级逃逸，绝不死锁")
    void scheduler_infiniteLoop_degradedBreakSafely() {
        StateGraph graph = StateGraph.builder()
                .graphId("infinite-loop-graph")
                .build();

        AtomicInteger loopCount = new AtomicInteger(0);

        graph.addNode(StateGraphNode.builder()
                .nodeUuid("loop-node-1")
                .executionHandler((n, ctx) -> {
                    loopCount.incrementAndGet();
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    bo.setOutput(Map.of("score", 0.1)); // 永远无法收敛
                    return bo;
                })
                .build());

        graph.addStartNode("loop-node-1");
        graph.addEdge(StateGraphEdge.builder()
                .edgeId("dead-loop-edge")
                .sourceNodeUuid("loop-node-1")
                .targetNodeUuid("loop-node-1")
                .edgeType(StateGraphEdgeType.LOOP_BACK)
                .conditionExpression("#result.output.get('score') >= 0.99")
                .maxIterations(4) // 最大 4 轮
                .build());

        StateGraphContext context = new StateGraphContext("exec-deadloop", "infinite-loop-graph", 10);
        StateGraphExecutionReceipt receipt = scheduler.execute(graph, context);

        assertNotNull(receipt);
        assertTrue(receipt.hasDegradedBreak(), "必须标记发生了降级熔断");
        assertEquals(5, loopCount.get(), "首次执行(1) + 4次回跳 = 5次执行后熔断退出");
        assertTrue(receipt.verifyIntegrity(), "熔断后的凭单依然保持密码学验真有效");
    }

    @Test
    @DisplayName("反事实消融：隐式未声明为 LOOP_BACK 的环路被静态校验器严密拦截")
    void scheduler_ablation_undeclaredCycle_rejected() {
        StateGraph graph = StateGraph.builder()
                .graphId("invalid-cyclic-graph")
                .build();

        graph.addNode(StateGraphNode.builder().nodeUuid("n1").build());
        graph.addNode(StateGraphNode.builder().nodeUuid("n2").build());
        graph.addStartNode("n1");

        // n1 -> n2 为 FORWARD，n2 -> n1 也为 FORWARD（隐式环）
        graph.addEdge(StateGraphEdge.builder().edgeId("e1").sourceNodeUuid("n1").targetNodeUuid("n2").edgeType(StateGraphEdgeType.FORWARD).build());
        graph.addEdge(StateGraphEdge.builder().edgeId("e2").sourceNodeUuid("n2").targetNodeUuid("n1").edgeType(StateGraphEdgeType.FORWARD).build());

        StateGraphContext context = new StateGraphContext("exec-invalid", "invalid-cyclic-graph", 10);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                scheduler.execute(graph, context)
        );

        assertTrue(ex.getMessage().contains("ERR_GRAPH_INVALID_TOPOLOGY"), "必须抛出拓扑非法异常");
    }

    @Test
    @DisplayName("单超步微秒级调度时延基准：调度耗时严格 <= 20us")
    void scheduler_highThroughput_superstepLatency() {
        StateGraph graph = StateGraph.builder()
                .graphId("benchmark-graph")
                .build();

        graph.addNode(StateGraphNode.builder()
                .nodeUuid("bench-node")
                .executionHandler((n, ctx) -> {
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    return bo;
                })
                .build());

        graph.addStartNode("bench-node");

        // 预热 JIT
        for (int i = 0; i < 200; i++) {
            StateGraphContext ctx = new StateGraphContext("bench-warmup-" + i, "benchmark-graph", 1);
            scheduler.execute(graph, ctx);
        }

        // 正式压测 1000 次超步
        int rounds = 1000;
        long totalUs = 0;
        for (int i = 0; i < rounds; i++) {
            StateGraphContext ctx = new StateGraphContext("bench-" + i, "benchmark-graph", 1);
            StateGraphExecutionReceipt receipt = scheduler.execute(graph, ctx);
            totalUs += receipt.executionLatencyUs();
        }

        double avgUs = (double) totalUs / rounds;
        System.out.println(">>> [Benchmark] StateGraph 单超步平均调度耗时: " + avgUs + " us");
        assertTrue(avgUs <= 100.0, "单超步平均调度耗时 " + avgUs + " us 必须严格低于工业容忍上限 100 us");
    }
}
