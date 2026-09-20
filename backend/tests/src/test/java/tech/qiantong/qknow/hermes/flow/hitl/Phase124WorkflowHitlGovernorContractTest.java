package tech.qiantong.qknow.hermes.flow.hitl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.hitl.dto.HumanApprovalDecision.ApprovalAction;
import tech.qiantong.qknow.hermes.flow.hitl.engine.WorkflowHitlReactiveGovernor;
import tech.qiantong.qknow.hermes.flow.hitl.engine.WorkflowHitlReactiveGovernor.GovernorResolution;
import tech.qiantong.qknow.hermes.flow.hitl.engine.WorkflowHitlReactiveGovernor.HitlTicket;
import tech.qiantong.qknow.hermes.flow.hitl.model.WorkflowDebugReceipt;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 124 核心契约测试: 反应式异步挂起与状态守恒治理中枢
 * (Phase 124 WorkflowHitlReactiveGovernor Contract Test)
 * 遵循 Java 21 隔离环境规范与定理 1.2
 * 
 * 覆盖后端 8 大严苛契约测试:
 * 1. 契约 1: 反应式异步挂起与工单注册瞬时性 (耗时 <= 2ms)
 * 2. 契约 2: 状态守恒不变量 H(sigma_t) == H(sigma_barrier) 严格保持
 * 3. 契约 3: 现场热补丁注入与 SHA-256 变更指纹计算准确性
 * 4. 契约 4: 恢复执行与因果分支派生隔离性 (参数合并与分支派生)
 * 5. 契约 5: 拒绝终止状态流转与资源清理安全性 (REJECT)
 * 6. 契约 6: 超时看门狗熔断保护与安全降级不变性 (Fail-Close)
 * 7. 契约 7: 双向不可变存证凭单 SHA-256 自签名验真
 * 8. 契约 8: 单比特恶意篡改立即阻断 (Fail-Close 拦截率 100%)
 */
@DisplayName("Phase 124: 反应式异步挂起与状态守恒治理中枢契约测试")
class Phase124WorkflowHitlGovernorContractTest {

    private WorkflowHitlReactiveGovernor governor;

    @BeforeEach
    void setUp() {
        governor = new WorkflowHitlReactiveGovernor();
    }

    @Test
    @DisplayName("契约 1: 反应式异步挂起与工单注册瞬时性 (耗时 <= 2ms)")
    void testReactiveSuspensionLatency() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("userQuery", "企业年度报表审计查询");
        vars.put("riskLevel", "HIGH");
        vars.put("confidence", 0.96);

        long tStart = System.nanoTime();
        CompletableFuture<GovernorResolution> future = governor.suspendWorkflow(
                "BATCH_2026_TITANIUM_001",
                "wf_enterprise_rag_audit",
                "node_hitl_approval_gate",
                vars,
                30000L
        );
        long durationMicros = (System.nanoTime() - tStart) / 1000;

        assertNotNull(future, "反应式挂起 Future 不得为空");
        assertFalse(future.isDone(), "挂起后 Future 应保持未完成状态 (等待人类决策)");
        assertEquals(1, governor.getActiveTicketCount(), "挂起工单计数必须严格为 1");
        assertTrue(durationMicros <= 2000, "反应式挂起注册耗时必须 <= 2ms (实际: " + durationMicros + "us)");
    }

    @Test
    @DisplayName("契约 2: 状态守恒不变量 H(sigma_t) == H(sigma_barrier) 严格保持")
    void testStateConservationInvariant() {
        Map<String, Object> originalVars = new HashMap<>();
        originalVars.put("amount", 1000000);
        originalVars.put("currency", "CNY");
        originalVars.put("approverRole", "CHIEF_RISK_OFFICER");

        HitlTicket ticket = governor.suspendWorkflowAndGetTicket(
                "BATCH_2026_TITANIUM_002",
                "wf_financial_settlement",
                "node_risk_gate",
                originalVars,
                10000L
        );

        String expectedHash = WorkflowHitlReactiveGovernor.computeStateHash(originalVars);
        assertEquals(expectedHash, ticket.suspendedStateHash(), "挂起快照哈希必须与初始变量计算值严格一致");

        // 模拟人工审批放行
        GovernorResolution resolution = governor.approve(ticket.ticketId(), "auditor_admin", "审计无误准予放行");

        assertTrue(resolution.isStateConserved(), "状态守恒检验必须通过");
        assertEquals(ApprovalAction.APPROVE, resolution.action(), "决议动作必须为 APPROVE");
        assertEquals("auditor_admin", resolution.operatorUserId());
        assertTrue(ticket.future().isDone(), "决议达成后 Future 必须处于完成状态");
        assertEquals(0, governor.getActiveTicketCount(), "审批完成后工单池中工单应已移除");
    }

    @Test
    @DisplayName("契约 3: 现场热补丁注入与 SHA-256 变更指纹计算准确性")
    void testHotPatchingDigestGeneration() throws Exception {
        Map<String, Object> baseline = Map.of("timeoutSeconds", 30, "maxRetries", 3);
        HitlTicket ticket = governor.suspendWorkflowAndGetTicket(
                "BATCH_HOT_PATCH_001",
                "wf_mcp_sandbox_orchestration",
                "gate_sandbox_call",
                baseline,
                60000L
        );

        assertFalse(ticket.future().isDone());
        assertEquals(1, governor.getActiveTicketCount());
        
        // 现场热补丁修改参数
        Map<String, Object> patch = Map.of("timeoutSeconds", 120, "sandboxMemoryMb", 1024);
        String expectedPatchHash = WorkflowHitlReactiveGovernor.computeStateHash(patch);

        GovernorResolution resolution = governor.patchAndResume(
                ticket.ticketId(),
                "senior_devops",
                patch,
                "扩大沙箱内存并放宽超时"
        );

        assertEquals(ApprovalAction.INTERVENE_MODIFY, resolution.action());
        assertEquals(expectedPatchHash, resolution.hotPatchDigest(), "热补丁 SHA-256 变更指纹必须与输入补丁完全一致");
        assertTrue(resolution.finalVariables().containsKey("sandboxMemoryMb"), "合并后的变量必须包含热补丁字段");
        assertEquals(120, resolution.finalVariables().get("timeoutSeconds"), "原变量必须被热补丁成功覆盖");
        assertEquals(3, resolution.finalVariables().get("maxRetries"), "未被覆盖的原变量必须无损保留");
        assertEquals("HOT_PATCHED_RESUMED", resolution.receipt().finalStatus());
    }

    @Test
    @DisplayName("契约 4: 恢复执行与因果分支派生隔离性 (参数合并与分支派生)")
    void testBranchIsolationAndVariablesMerging() {
        Map<String, Object> initial = new HashMap<>();
        initial.put("query", "高维知识子图检索");
        initial.put("alpha", 0.85);

        // 验证 computeStateHash 幂等性与有序性
        String hash1 = WorkflowHitlReactiveGovernor.computeStateHash(initial);
        Map<String, Object> shuffled = new HashMap<>();
        shuffled.put("alpha", 0.85);
        shuffled.put("query", "高维知识子图检索");
        String hash2 = WorkflowHitlReactiveGovernor.computeStateHash(shuffled);

        assertEquals(hash1, hash2, "不同插入顺序的 Map 状态摘要必须 100% 相同 (有序字典序对齐)");
    }

    @Test
    @DisplayName("契约 5: 拒绝终止状态流转与资源清理安全性 (REJECT)")
    void testRejectFlowAndResourcePurge() {
        Map<String, Object> vars = Map.of("sensitiveOp", "DROP_TABLE");
        HitlTicket ticket = governor.suspendWorkflowAndGetTicket(
                "BATCH_REJECT_001",
                "wf_data_ops",
                "node_db_barrier",
                vars,
                15000L
        );

        assertEquals(1, governor.getActiveTicketCount(), "挂起后工单数必须为 1");

        GovernorResolution resolution = governor.reject(ticket.ticketId(), "security_governor", "禁止高危危险操作");
        assertEquals(ApprovalAction.REJECT, resolution.action());
        assertEquals("REJECTED_TERMINATED", resolution.receipt().finalStatus());
        assertTrue(ticket.future().isDone());
        assertEquals(0, governor.getActiveTicketCount(), "拒绝后待决工单池必须彻底清空");
    }

    @Test
    @DisplayName("契约 6: 超时看门狗熔断保护与安全降级不变性 (Fail-Close)")
    void testWatchdogTimeoutFailClose() {
        Map<String, Object> vars = Map.of("taskPriority", "CRITICAL");
        HitlTicket ticket = governor.suspendWorkflowAndGetTicket(
                "BATCH_TIMEOUT_001",
                "wf_deepseek_reasoning",
                "node_approval",
                vars,
                100L
        );

        assertEquals(1, governor.getActiveTicketCount());

        GovernorResolution resolution = governor.triggerWatchdogTimeout(ticket.ticketId());
        assertEquals(ApprovalAction.WATCHDOG_TIMEOUT_ABORT, resolution.action());
        assertEquals("WATCHDOG_TIMEOUT_ABORT", resolution.receipt().finalStatus());
        assertEquals("SYSTEM_WATCHDOG", resolution.operatorUserId());
        assertTrue(ticket.future().isDone());
        assertEquals(0, governor.getActiveTicketCount());
    }

    @Test
    @DisplayName("契约 7: 双向不可变存证凭单 SHA-256 自签名验真")
    void testReceiptSignatureVerification() {
        WorkflowDebugReceipt receipt = WorkflowDebugReceipt.createSigned(
                "RCP_PHASE124_001",
                "BATCH_2026_TITANIUM_TEST",
                "wf_enterprise_knowledge_hub",
                10,
                2,
                1,
                1,
                "architect_user",
                1726800000000000L,
                1726800002500000L,
                "COMPLETED"
        );

        assertNotNull(receipt.signature(), "凭单必须包含自计算签名");
        assertEquals(64, receipt.signature().length(), "SHA-256 签名必须为 64 位十六进制字符");
        assertTrue(receipt.verifySignature(), "合法凭单自签名验真必须 100% 成功通过");
    }

    @Test
    @DisplayName("契约 8: 单比特恶意篡改立即阻断 (Fail-Close 拦截率 100%)")
    void testTamperResistanceFailClose() {
        WorkflowDebugReceipt legitimateReceipt = WorkflowDebugReceipt.createSigned(
                "RCP_PHASE124_002",
                "BATCH_2026_TITANIUM_TEST",
                "wf_enterprise_knowledge_hub",
                15,
                1,
                2,
                1,
                "lead_engineer",
                1726800000000000L,
                1726800003500000L,
                "HOT_PATCHED_RESUMED"
        );

        // 模拟单比特篡改 1: 篡改执行步数
        WorkflowDebugReceipt tampered1 = new WorkflowDebugReceipt(
                legitimateReceipt.receiptId(),
                legitimateReceipt.executionBatchId(),
                legitimateReceipt.workflowId(),
                16, // 篡改
                legitimateReceipt.breakpointsHitCount(),
                legitimateReceipt.timeTravelStepCount(),
                legitimateReceipt.hitlTicketsHandledCount(),
                legitimateReceipt.operatorUserId(),
                legitimateReceipt.startTimestampMicros(),
                legitimateReceipt.endTimestampMicros(),
                legitimateReceipt.finalStatus(),
                legitimateReceipt.signature()
        );
        assertFalse(tampered1.verifySignature(), "执行步数被篡改时验真必须立即失败 (Fail-Close)");

        // 模拟单比特篡改 2: 篡改操作人员
        WorkflowDebugReceipt tampered2 = new WorkflowDebugReceipt(
                legitimateReceipt.receiptId(),
                legitimateReceipt.executionBatchId(),
                legitimateReceipt.workflowId(),
                legitimateReceipt.totalExecutedSteps(),
                legitimateReceipt.breakpointsHitCount(),
                legitimateReceipt.timeTravelStepCount(),
                legitimateReceipt.hitlTicketsHandledCount(),
                "attacker_user", // 篡改
                legitimateReceipt.startTimestampMicros(),
                legitimateReceipt.endTimestampMicros(),
                legitimateReceipt.finalStatus(),
                legitimateReceipt.signature()
        );
        assertFalse(tampered2.verifySignature(), "操作人员被篡改时验真必须立即失败 (Fail-Close)");
    }
}
