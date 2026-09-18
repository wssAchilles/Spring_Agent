package tech.qiantong.qknow.hermes.agent.swarm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.swarm.dto.ContextSliceBO;
import tech.qiantong.qknow.hermes.agent.swarm.engine.SwarmDynamicHandoffHub;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SwarmDynamicHandoffHub 去中心化交接中枢测试")
class SwarmDynamicHandoffHubTest {

    private SwarmDynamicHandoffHub handoffHub;

    @BeforeEach
    void setUp() {
        handoffHub = new SwarmDynamicHandoffHub();
    }

    private ContextSliceBO createSlice(String instruction) {
        return new ContextSliceBO(
                "处理跨境退款争议",
                Map.of("orderId", "ORD-888", "amount", 199.0),
                instruction,
                System.currentTimeMillis()
        );
    }

    @Test
    @DisplayName("正常单向无环交接流转顺利通过并记录深度")
    void handoff_normalChain_authorized() {
        String session = "sess-normal";

        // A -> B
        var d1 = handoffHub.transferToAgent(session, "CustomerCareAgent", "FinanceAgent", createSlice("移交财务审查"));
        assertTrue(d1.authorized());
        assertEquals(1, d1.currentDepth());
        assertEquals("FinanceAgent", d1.targetAgentId());

        // B -> C
        var d2 = handoffHub.transferToAgent(session, "FinanceAgent", "TaxAuditAgent", createSlice("移交税务复核"));
        assertTrue(d2.authorized());
        assertEquals(2, d2.currentDepth());
        assertEquals("TaxAuditAgent", d2.targetAgentId());

        // C -> D
        var d3 = handoffHub.transferToAgent(session, "TaxAuditAgent", "RiskControlAgent", createSlice("移交风控审批"));
        assertTrue(d3.authorized());
        assertEquals(3, d3.currentDepth());
        assertEquals("RiskControlAgent", d3.targetAgentId());
    }

    @Test
    @DisplayName("反事实消融：A -> B -> A 即时乒乓振荡被毫秒级就地拦截")
    void handoff_pingPongOscillation_interceptedSafely() {
        String session = "sess-pingpong";

        // 1. 客服 A -> 财务 B
        var d1 = handoffHub.transferToAgent(session, "CustomerCareAgent", "FinanceAgent", createSlice("移交财务"));
        assertTrue(d1.authorized());
        assertEquals(1, d1.currentDepth());

        // 2. 财务 B -> 客服 A (试图踢回皮球)
        var d2 = handoffHub.transferToAgent(session, "FinanceAgent", "CustomerCareAgent", createSlice("缺发票退回"));
        assertFalse(d2.authorized(), "必须拦截 A->B->A 乒乓交接");
        assertEquals("SWARM_PING_PONG_CYCLE_DETECTED", d2.rejectionCode());
        assertTrue(d2.rejectionReason().contains("乒乓"));
    }

    @Test
    @DisplayName("深层拓扑闭环 (A -> B -> C -> A) 检出并拦截")
    void handoff_topologicalCycle_interceptedSafely() {
        String session = "sess-cycle";

        // A -> B -> C
        assertTrue(handoffHub.transferToAgent(session, "AgentA", "AgentB", createSlice("A->B")).authorized());
        assertTrue(handoffHub.transferToAgent(session, "AgentB", "AgentC", createSlice("B->C")).authorized());

        // C -> A (形成闭环)
        var d3 = handoffHub.transferToAgent(session, "AgentC", "AgentA", createSlice("C->A 形成循环"));
        assertFalse(d3.authorized(), "必须拦截深层拓扑闭环");
        assertEquals("SWARM_TOPOLOGICAL_CYCLE_DETECTED", d3.rejectionCode());
    }

    @Test
    @DisplayName("交接深度达到硬上限（5次）后触发熔断拦截")
    void handoff_maxDepthExceeded_meltdownTriggered() {
        String session = "sess-max-depth";

        // 连续进行 5 次合法无环交接：A -> B -> C -> D -> E -> F
        assertTrue(handoffHub.transferToAgent(session, "Node1", "Node2", createSlice("1->2")).authorized());
        assertTrue(handoffHub.transferToAgent(session, "Node2", "Node3", createSlice("2->3")).authorized());
        assertTrue(handoffHub.transferToAgent(session, "Node3", "Node4", createSlice("3->4")).authorized());
        assertTrue(handoffHub.transferToAgent(session, "Node4", "Node5", createSlice("4->5")).authorized());
        var d5 = handoffHub.transferToAgent(session, "Node5", "Node6", createSlice("5->6"));
        assertTrue(d5.authorized());
        assertEquals(5, d5.currentDepth(), "第 5 次达到最大允许深度");

        // 第 6 次交接必须被硬熔断拦截
        var d6 = handoffHub.transferToAgent(session, "Node6", "Node7", createSlice("6->7 超限"));
        assertFalse(d6.authorized(), "达到深度 5 必须熔断拦截");
        assertEquals("MAX_HANDOFF_DEPTH_EXCEEDED", d6.rejectionCode());
    }
}
