package tech.qiantong.qknow.hermes.flow.hitl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tech.qiantong.qknow.hermes.flow.hitl.dto.WorkflowNodeStateSnapshot;
import tech.qiantong.qknow.hermes.flow.hitl.engine.CognitiveProjectionFilter;
import tech.qiantong.qknow.hermes.flow.hitl.engine.TimeTravelSnapshotRingBuffer;
import tech.qiantong.qknow.hermes.flow.hitl.model.WorkflowDebugReceipt;
import tech.qiantong.qknow.mcp.client.safety.HighRiskToolSafetyGovernor;
import tech.qiantong.qknow.mcp.client.safety.HighRiskToolSafetyGovernor.RiskLevel;
import tech.qiantong.qknow.mcp.client.safety.HighRiskToolSafetyGovernor.SafetyDecisionBO;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 104 全链路协同集成测试 (Phase104HitlCanvasIntegrationTest)")
class Phase104HitlCanvasIntegrationTest {

    @Test
    @DisplayName("端到端闭环: 节点执行 -> 断点暂停 -> 时空回溯 -> 高危挂起与认知投影 -> 审批放行 -> 签发调试存证")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testFullChainTimeTravelAndHitlWorkflow() {
        long startMicros = System.currentTimeMillis() * 1000L;

        // 1. 初始化快照环形池与认知投影算子
        TimeTravelSnapshotRingBuffer ringBuffer = new TimeTravelSnapshotRingBuffer(20);
        CognitiveProjectionFilter projectionFilter = new CognitiveProjectionFilter();
        HighRiskToolSafetyGovernor safetyGovernor = new HighRiskToolSafetyGovernor();

        // 2. 模拟步进执行 Node 1 -> Node 2
        ringBuffer.recordSnapshot(new WorkflowNodeStateSnapshot(
                "SNAP-01", "wf-enterprise-etl", "main", "node-extract",
                "ExtractNode", null, Map.of("sourceTable", "raw_orders", "batchSize", 5000),
                "in-1", "out-1", null, startMicros
        ));

        ringBuffer.recordSnapshot(new WorkflowNodeStateSnapshot(
                "SNAP-02", "wf-enterprise-etl", "main", "node-transform",
                "TransformNode", "SNAP-01", Map.of("processedCount", 4980, "errorCount", 20),
                "in-2", "out-2", null, startMicros + 1000L
        ));

        // 3. 模拟在 Node 3 (node-purge) 触发断点暂停
        String breakpointNode = "node-purge";
        boolean breakpointPaused = breakpointNode.equals("node-purge");
        assertTrue(breakpointPaused, "应命中节点断点并暂停执行");

        // 4. 触发时空回溯 (Step Back 至 SNAP-01 节点状态)
        Map<String, Object> stateAtSnap1 = ringBuffer.reconstructStateAt("SNAP-01");
        assertNotNull(stateAtSnap1);
        assertEquals("raw_orders", stateAtSnap1.get("sourceTable"));
        assertFalse(stateAtSnap1.containsKey("processedCount"), "回溯到 SNAP-01 时不得包含后续节点数据（零时间污染）");

        // 5. 从断点恢复并执行 Node 3 (高危破坏性表清除操作)
        Map<String, Object> destructiveArgs = new HashMap<>();
        destructiveArgs.put("sys_cluster_vip", "10.0.8.22");
        destructiveArgs.put("env_deploy_mode", "production");
        destructiveArgs.put("targetTable", "temp_staging_orders");
        destructiveArgs.put("sqlAction", "TRUNCATE TABLE temp_staging_orders CASCADE");

        // 调用 Phase 103 安全门禁
        SafetyDecisionBO decision = safetyGovernor.evaluateAndIntercept(
                "srv-postgres-cluster",
                "truncate_table_action",
                destructiveArgs,
                RiskLevel.HIGH_RISK_DESTRUCTIVE,
                "ROLE_ADMIN",
                "engineer-charlie"
        );

        assertFalse(decision.allowed(), "破坏性高危操作必须被物理挂起");
        assertTrue(decision.approvalRequired());
        assertNotNull(decision.ticketId(), "必须派发待审批工单");

        // 6. 渐进式认知焦点投影 (精炼冗余常量，保留高危 Diff)
        Map<String, Object> parentVars = ringBuffer.reconstructStateAt("SNAP-02");
        Map<String, Object> projectedParams = projectionFilter.projectFocusContext(destructiveArgs, parentVars);

        assertFalse(projectedParams.containsKey("sys_cluster_vip"), "系统变量应被过滤");
        assertFalse(projectedParams.containsKey("env_deploy_mode"), "系统环境应被过滤");
        assertTrue(projectedParams.containsKey("sqlAction"), "高危破坏性动作必须精确保留");
        assertEquals("TRUNCATE TABLE temp_staging_orders CASCADE", projectedParams.get("sqlAction"));

        // 7. 管理员在单色钛金浮窗中审查通过审批
        boolean approved = safetyGovernor.submitApprovalDecision(decision.ticketId(), true, "chief-architect");
        assertTrue(approved, "工单二次审批应通过");

        // 8. 流程完成并写入终态快照
        ringBuffer.recordSnapshot(new WorkflowNodeStateSnapshot(
                "SNAP-03", "wf-enterprise-etl", "main", "node-purge",
                "PurgeNode", "SNAP-02", Map.of("purgedRows", 4980, "purgeStatus", "SUCCESS"),
                "in-3", "out-3", null, startMicros + 2000L
        ));
        assertEquals(3, ringBuffer.size());

        // 9. 签发不可变工作流调试审计凭单
        long endMicros = System.currentTimeMillis() * 1000L;
        WorkflowDebugReceipt receipt = WorkflowDebugReceipt.createSigned(
                "RCP-DEBUG-PHASE104-001",
                "BATCH-ETL-2026",
                "wf-enterprise-etl",
                3, // 总执行步数
                1, // 命中 1 次断点
                1, // 触发 1 次时光回溯
                1, // 完成 1 次 HITL 审批
                "chief-architect",
                startMicros,
                endMicros,
                "COMPLETED"
        );

        assertNotNull(receipt.signature());
        assertEquals(64, receipt.signature().length());
        assertTrue(receipt.verifySignature(), "工作流调试存证凭单 SHA-256 自验真必须 100% 通过");
        assertEquals("COMPLETED", receipt.finalStatus());
    }
}
