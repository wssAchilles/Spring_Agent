package tech.qiantong.qknow.hermes.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.trace.engine.HierarchicalExecutionTraceEngine;
import tech.qiantong.qknow.hermes.trace.model.HierarchicalTraceSpan;
import tech.qiantong.qknow.hermes.trace.model.TraceExecutionReceipt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 106 契约测试集四：全链路多 Agent 协同可观测性端到端集成测试
 * <p>
 * 契约 9：主 Agent 编排 -> Swarm 对抗辩论 -> MCP 工具调用 -> GraphRAG 子图推理全链路树状因果拓扑闭环
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public class Phase106TraceObservabilityIntegrationTest {

    @Test
    @DisplayName("契约 9：多 Agent、MCP 工具与 GraphRAG 全链路树状追踪与凭单签发端到端闭环验证")
    void testEndToEndMultiAgentTracePipeline() throws InterruptedException {
        HierarchicalExecutionTraceEngine traceEngine = new HierarchicalExecutionTraceEngine();

        String traceId = "trace-e2e-106";
        String workflowId = "wf-autonomous-agent-001";

        // 1. 根编排节点：主 Agent 意图分解与调度
        HierarchicalTraceSpan root = traceEngine.startTrace(
                traceId,
                workflowId,
                "MasterOrchestrator-DeepSeekV3",
                "AGENT_REASONING",
                "用户请求：全面审计某上市公司 2025 年报财务异常并调用外部风控 API 核验"
        );
        assertNotNull(root);

        // 2. 一级子节点：Phase 105 高保真 GraphRAG 知识中枢子图推理
        HierarchicalTraceSpan ragSpan = traceEngine.startChildSpan(
                traceId,
                root.spanId(),
                "GraphRAG-SpatiotemporalMetacenter",
                "GRAPH_RAG",
                "检索年报相关章节并执行 2-跳局部 PPR 推理"
        );
        Thread.sleep(8);
        traceEngine.endSpan(traceId, ragSpan.spanId(), "SUCCESS", 260, "召回 3 个父级富上下文与核心实体");

        // 3. 一级子节点：Phase 102 多智能体对抗辩论网络 (Swarm Debate)
        HierarchicalTraceSpan debateSpan = traceEngine.startChildSpan(
                traceId,
                root.spanId(),
                "SwarmDebateCoordinator",
                "SWARM_DEBATE",
                "正反方智能体针对审计风险评级展开辩论"
        );

        // 辩论网络派生二级子节点：Proponent 与 Opponent
        HierarchicalTraceSpan propSpan = traceEngine.startChildSpan(
                traceId,
                debateSpan.spanId(),
                "ProponentAgent-DeepSeekR1",
                "AGENT_REASONING",
                "主张存在重大财报造假疑点"
        );
        Thread.sleep(6);
        traceEngine.endSpan(traceId, propSpan.spanId(), "SUCCESS", 180, "给出 3 项财务异常逻辑链");

        HierarchicalTraceSpan oppSpan = traceEngine.startChildSpan(
                traceId,
                debateSpan.spanId(),
                "OpponentAgent-DeepSeekR1",
                "AGENT_REASONING",
                "主张属于合理行业周期波动"
        );
        Thread.sleep(5);
        traceEngine.endSpan(traceId, oppSpan.spanId(), "SUCCESS", 150, "给出 2 项对冲证据");

        traceEngine.endSpan(traceId, debateSpan.spanId(), "SUCCESS", 330, "裁判员裁定正方论点更充分");

        // 4. 一级子节点：Phase 103 企业级 MCP 工具调用 (高危 API 核验)
        HierarchicalTraceSpan mcpSpan = traceEngine.startChildSpan(
                traceId,
                root.spanId(),
                "EnterpriseMcpTool-RiskQuery",
                "TOOL_MCP",
                "调用外部金融风控沙箱接口 verify_enterprise_risk"
        );
        Thread.sleep(10);
        traceEngine.endSpan(traceId, mcpSpan.spanId(), "SUCCESS", 90, "外部风控系统返回高风险警告");

        // 5. 结算根节点并签发凭单
        traceEngine.endSpan(traceId, root.spanId(), "SUCCESS", 860, "最终审计报告合成完毕");

        TraceExecutionReceipt receipt = traceEngine.endTrace(traceId);
        assertNotNull(receipt);

        // 6. 核心契约断言验证
        assertEquals(traceId, receipt.traceId());
        assertEquals(workflowId, receipt.workflowId());
        assertEquals(6, receipt.totalSpans(), "包含 root, rag, debate, proponent, opponent, mcp 共计 6 个 Span 节点");
        assertEquals(0, receipt.errorCount());
        // 全链路累计 Token: 260 + 180 + 150 + 330 + 90 + 860 = 1870
        assertEquals(1870, receipt.totalTokens());
        assertTrue(receipt.rootDurationUs() > 0);
        assertTrue(receipt.criticalPathDurationUs() > 0);
        assertTrue(receipt.verifySignature(), "全链路追踪密码学防篡改凭单必须 100% 自验真通过");

        // 7. 验证拓扑与 Lamport 单调性
        List<HierarchicalTraceSpan> allSpans = traceEngine.getTraceSpans(traceId);
        assertEquals(6, allSpans.size());
        for (HierarchicalTraceSpan s : allSpans) {
            if (s.parentSpanId() != null) {
                // 子节点起始时间必须 >= 根节点起始时间
                assertTrue(s.startNano() >= root.startNano(), "全链路任何子节点起始纳秒必须满足 Lamport 单调保序");
            }
        }
    }
}
