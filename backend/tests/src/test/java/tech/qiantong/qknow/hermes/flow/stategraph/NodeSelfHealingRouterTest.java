package tech.qiantong.qknow.hermes.flow.stategraph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphContext;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.NodeSelfHealingRouter;
import tech.qiantong.qknow.hermes.flow.stategraph.enums.SelfHealingActionType;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphNode;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NodeSelfHealingRouter 节点局部自愈路由器测试")
class NodeSelfHealingRouterTest {

    private NodeSelfHealingRouter router;
    private StateGraphContext context;

    @BeforeEach
    void setUp() {
        // baseBackoffMs 设为 1ms 以加速单元测试
        router = new NodeSelfHealingRouter(3, 1, 10);
        context = new StateGraphContext("exec-heal-1", "flow-heal-1", 10);
    }

    @Test
    @DisplayName("瞬时非致命异常在局部重试后自愈成功")
    void selfHealing_transientError_recoversWithJitter() {
        AtomicInteger attemptCounter = new AtomicInteger(0);

        StateGraphNode node = StateGraphNode.builder()
                .nodeUuid("node-transient")
                .nodeName("瞬时抖动节点")
                .maxLocalRetries(3)
                .executionHandler((n, ctx) -> {
                    int att = attemptCounter.incrementAndGet();
                    if (att < 3) {
                        // 模拟前 2 次抛出网络超时瞬时异常
                        throw new RuntimeException(new IOException("Connection timed out (503)"));
                    }
                    // 第 3 次成功
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1); // 成功
                    bo.setOutput(Map.of("data", "repaired_success"));
                    return bo;
                })
                .build();

        NodeRunResultBO finalResult = router.executeWithHealing(node, context);

        assertNotNull(finalResult);
        assertEquals(1, finalResult.getStatus());
        assertEquals("repaired_success", finalResult.getOutput().get("data"));
        assertEquals(3, attemptCounter.get(), "经历 2 次失败重试，第 3 次成功自愈");
        assertTrue(context.getSelfHealedOccurred().get(), "上下文必须标记发生自愈");
    }

    @Test
    @DisplayName("致命不可恢复异常直接路由至 Fallback 旁路降级分支")
    void selfHealing_fatalError_routesToFallback() {
        AtomicInteger mainExecuted = new AtomicInteger(0);
        AtomicInteger fallbackExecuted = new AtomicInteger(0);

        StateGraphNode fallbackNode = StateGraphNode.builder()
                .nodeUuid("node-fallback-branch")
                .nodeName("降级旁路节点")
                .executionHandler((n, ctx) -> {
                    fallbackExecuted.incrementAndGet();
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    bo.setOutput(Map.of("fallback_data", "default_safe_payload"));
                    return bo;
                })
                .build();

        StateGraphNode mainNode = StateGraphNode.builder()
                .nodeUuid("node-fatal")
                .nodeName("致命异常主节点")
                .fallbackNodeUuid("node-fallback-branch")
                .executionHandler((n, ctx) -> {
                    mainExecuted.incrementAndGet();
                    // 抛出不可恢复的鉴权异常
                    throw new SecurityException("401 Unauthorized: Invalid API Key");
                })
                .build();

        // 注入 fallbackNode 供路由器查询
        router.registerNode(fallbackNode);

        NodeRunResultBO finalResult = router.executeWithHealing(mainNode, context);

        assertNotNull(finalResult);
        assertEquals(1, mainExecuted.get(), "致命异常禁止重复重试");
        assertEquals(1, fallbackExecuted.get(), "必须路由至 Fallback 旁路执行");
        assertEquals("default_safe_payload", finalResult.getOutput().get("fallback_data"));
    }

    @Test
    @DisplayName("局部重试配额耗尽后优雅流转至 Fallback 旁路")
    void selfHealing_exhaustedRetries_fallbackGraceful() {
        AtomicInteger attempts = new AtomicInteger(0);

        StateGraphNode fallbackNode = StateGraphNode.builder()
                .nodeUuid("fb-quota-node")
                .nodeName("配额耗尽旁路")
                .executionHandler((n, ctx) -> {
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    bo.setOutput(Map.of("quota_exceeded", true));
                    return bo;
                })
                .build();

        StateGraphNode node = StateGraphNode.builder()
                .nodeUuid("node-always-fail")
                .nodeName("持续报错节点")
                .fallbackNodeUuid("fb-quota-node")
                .maxLocalRetries(2)
                .executionHandler((n, ctx) -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("503 Service Unavailable");
                })
                .build();

        router.registerNode(fallbackNode);

        NodeRunResultBO result = router.executeWithHealing(node, context);

        assertNotNull(result);
        assertEquals(3, attempts.get(), "首次执行(1) + 局部重试(2) = 3次尝试");
        assertEquals(true, result.getOutput().get("quota_exceeded"));
    }
}
