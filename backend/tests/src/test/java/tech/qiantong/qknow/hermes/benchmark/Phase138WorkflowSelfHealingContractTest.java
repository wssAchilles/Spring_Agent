package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.workflow.healing.MultiAgentWaitForGraphDetector;
import tech.qiantong.qknow.hermes.agent.workflow.healing.MultiAgentWaitForGraphDetector.DeadlockCycle;
import tech.qiantong.qknow.hermes.agent.workflow.healing.MultiAgentWaitForGraphDetector.DeadlockResolutionResult;
import tech.qiantong.qknow.hermes.agent.workflow.healing.WorkflowFiniteStateCheckpointManager;
import tech.qiantong.qknow.hermes.agent.workflow.healing.WorkflowFiniteStateCheckpointManager.LeaderLease;
import tech.qiantong.qknow.hermes.agent.workflow.healing.WorkflowFiniteStateCheckpointManager.ResumptionResult;
import tech.qiantong.qknow.hermes.agent.workflow.healing.WorkflowFiniteStateCheckpointManager.StateSnapshot;
import tech.qiantong.qknow.hermes.agent.workflow.healing.WorkflowSelfHealingReceipt;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 138 核心契约测试套件：
 * 多智能体认知协同工作流状态机自愈、断点恢复与分布式死锁检测中枢
 * (Multi-Agent Cognitive Workflow Finite-State Self-Healing,
 * Breakpoint Resumption & Distributed Deadlock Detection Metacenter)
 *
 * 核心验证范围：
 * 1. 动态有向等待图 (WFG) 与 Tarjan 算法微秒级死锁环路检出 (TC-138-1)
 * 2. 最小代价外科手术式边解构与自愈破环 (TC-138-2)
 * 3. 单调自增防护令牌 (Fencing Token) 脑裂双写排他拦截 (TC-138-3)
 * 4. 工作流状态机不可变快照事件树存储 <= 1.0ms (TC-138-4)
 * 5. 断点恢复零冗余执行与秒级就地续跑 (TC-138-5)
 * 6. 租约超时看门狗与活性自愈判定 (TC-138-6)
 * 7. 纯 Java 21 Record 凭单签名不可变性与 SHA-256 自验真 (TC-138-7)
 * 8. 端到端全链路自愈闭环集成与防篡改签发 (TC-138-8)
 *
 * @author Achilles
 * @since 2026-09-25
 */
public class Phase138WorkflowSelfHealingContractTest {

    private final MultiAgentWaitForGraphDetector wfgDetector = new MultiAgentWaitForGraphDetector();
    private final WorkflowFiniteStateCheckpointManager checkpointManager = new WorkflowFiniteStateCheckpointManager();

    // =========================================================================
    // TC-138-1: 动态有向等待图 (WFG) 与 Tarjan 算法微秒级死锁环路检出
    // =========================================================================
    @Test
    @DisplayName("TC-138-1: 构造 A -> B -> C -> A 循环死锁等待，Tarjan 算法在 <= 5.0ms 内精准检出有向环")
    void testWaitForGraph_cycleDeadlockDetection() {
        wfgDetector.clear();

        // 注册 4 个业务智能体
        wfgDetector.registerNode("agent_crawler", "DATA_EXTRACTOR", "TASK_EXTRACT");
        wfgDetector.registerNode("agent_fraud", "FRAUD_DETECTOR", "TASK_FRAUD");
        wfgDetector.registerNode("agent_finance", "CREDIT_SCORER", "TASK_SCORE");
        wfgDetector.registerNode("agent_hitl", "HITL_REVIEWER", "TASK_APPROVAL");

        // 构造三元循环交织死锁: fraud -> finance -> hitl -> fraud
        wfgDetector.addWaitEdge("agent_fraud", "agent_finance", "RES_FINANCIAL_METRICS", 0.5);
        wfgDetector.addWaitEdge("agent_finance", "agent_hitl", "RES_APPROVAL_LEASE", 0.8);
        wfgDetector.addWaitEdge("agent_hitl", "agent_fraud", "RES_FRAUD_BASELINE", 0.2); // 形成环路

        // crawler 独立等待，不属于死锁环路
        wfgDetector.addWaitEdge("agent_crawler", "agent_fraud", "RES_READ_LOCK", 0.1);

        long start = System.nanoTime();
        List<DeadlockCycle> cycles = wfgDetector.detectDeadlockCycles();
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(cycles);
        assertEquals(1, cycles.size(), "必须精确检出 1 个死锁强连通环路");
        assertTrue(elapsedMs <= 5.0, "死锁环路检出耗时必须严格 <= 5.0ms，实测: " + elapsedMs + "ms");

        DeadlockCycle cycle = cycles.get(0);
        assertEquals(3, cycle.cycleNodes().size(), "环路中必须包含 3 个循环等待节点");
        assertTrue(cycle.cycleNodes().containsAll(List.of("agent_fraud", "agent_finance", "agent_hitl")));
        assertFalse(cycle.cycleNodes().contains("agent_crawler"), "非环路节点 agent_crawler 不得被误判为死锁");
    }

    // =========================================================================
    // TC-138-2: 最小代价外科手术式边解构与自愈破环
    // =========================================================================
    @Test
    @DisplayName("TC-138-2: 拓扑破环自适应选择最小回滚代价依赖边剪断，死锁自愈成功率 100%，非环节点不受干扰")
    void testDeadlockPreemption_minimumRollbackCost() {
        wfgDetector.clear();

        // 构造三元环路并赋予明确代价:
        // edge1: A -> B, 代价 0.7
        // edge2: B -> C, 代价 0.9
        // edge3: C -> A, 代价 0.15 (最小代价，应被裁决为牺牲者)
        wfgDetector.addWaitEdge("node_A", "node_B", "LOCK_1", 0.7);
        wfgDetector.addWaitEdge("node_B", "node_C", "LOCK_2", 0.9);
        wfgDetector.addWaitEdge("node_C", "node_A", "LOCK_3", 0.15);

        List<DeadlockCycle> cycles = wfgDetector.detectDeadlockCycles();
        assertEquals(1, cycles.size());
        DeadlockCycle cycle = cycles.get(0);

        // 验证候选牺牲者边为代价 0.15 的边 (C -> A)
        assertNotNull(cycle.candidateVictimEdge());
        assertEquals("node_C", cycle.candidateVictimEdge().waitingAgentId());
        assertEquals("node_A", cycle.candidateVictimEdge().awaitedAgentId());
        assertEquals(0.15, cycle.candidateVictimEdge().rollbackCost());

        // 执行外科手术式破环
        DeadlockResolutionResult resolution = wfgDetector.resolveDeadlock(cycle, "{\"degraded\": true, \"defaultScore\": 60.0}");
        assertTrue(resolution.resolved(), "破环必须执行成功");
        assertNotNull(resolution.injectedFallbackValue());
        assertTrue(resolution.latencyMs() <= 2.0, "破环耗时必须 <= 2.0ms");

        // 再次检测，死锁环路已完全消除
        List<DeadlockCycle> remainingCycles = wfgDetector.detectDeadlockCycles();
        assertTrue(remainingCycles.isEmpty(), "破环后死锁环路数必须严格为 0");
    }

    // =========================================================================
    // TC-138-3: 单调自增防护令牌 (Fencing Token) 脑裂双写排他拦截
    // =========================================================================
    @Test
    @DisplayName("TC-138-3: 单调防护令牌严格排他：假死旧节点携带过期令牌写操作 100% 被原子拦截")
    void testFencingToken_splitBrainRejection() {
        String workflowId = "WF-FINANCIAL-LOAN-001";

        // 1. 初始主管节点 agent_leader_01 申请租约
        LeaderLease lease1 = checkpointManager.acquireNewLeaderLease(workflowId, "agent_leader_01", 3000L);
        long token1 = lease1.fencingToken();
        assertTrue(token1 >= 1000L);

        // 主管节点使用合法 token 成功写入步骤 1 快照
        StateSnapshot snap1 = checkpointManager.saveCheckpoint(
                workflowId, 1, "EXTRACT_IDENTITY", "DATA_PAYLOAD_1", Map.of("userId", "U123"), token1
        );
        assertNotNull(snap1);

        // 2. 模拟 agent_leader_01 发生长 GC 假死，备用节点 agent_standby_02 触发接管
        LeaderLease lease2 = checkpointManager.acquireNewLeaderLease(workflowId, "agent_standby_02", 3000L);
        long token2 = lease2.fencingToken();
        assertTrue(token2 > token1, "备用节点获得的 Fencing Token 必须严格大于旧 Token");

        // 备用节点使用新 token 成功写入步骤 2 快照
        StateSnapshot snap2 = checkpointManager.saveCheckpoint(
                workflowId, 2, "RUN_MODEL_INFERENCE", "DATA_PAYLOAD_2", Map.of("score", 85.5), token2
        );
        assertNotNull(snap2);

        // 3. 模拟旧主管 agent_leader_01 从假死中苏醒，尝试携带旧 token1 提交写入
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            checkpointManager.saveCheckpoint(
                    workflowId, 2, "DUPLICATE_WRITE_ATTEMPT", "MALICIOUS_PAYLOAD", Map.of(), token1
            );
        }, "陈旧令牌写入必须被原子拒绝抛出异常");

        assertTrue(ex.getMessage().contains("FENCING_REJECTED"), "错误信息必须标明 FENCING_REJECTED");
        assertFalse(checkpointManager.verifyFencingToken(workflowId, token1), "旧令牌校验必须返回 false");
        assertTrue(checkpointManager.verifyFencingToken(workflowId, token2), "当前有效新令牌校验必须返回 true");
    }

    // =========================================================================
    // TC-138-4: 工作流状态机不可变快照事件树存储 <= 1.0ms
    // =========================================================================
    @Test
    @DisplayName("TC-138-4: 状态机跃迁原子创建不可变快照事件，单次快照保存耗时 <= 1.0ms")
    void testWorkflowCheckpoint_stateSnapshotTree() {
        String workflowId = "WF-CROSS-BORDER-AUDIT-002";
        LeaderLease lease = checkpointManager.acquireNewLeaderLease(workflowId, "worker_primary", 5000L);

        long start = System.nanoTime();
        for (int i = 1; i <= 5; i++) {
            checkpointManager.saveCheckpoint(
                    workflowId,
                    i,
                    "STEP_" + i,
                    "PAYLOAD_STEP_" + i,
                    Map.of("stepIndex", i, "status", "OK"),
                    lease.fencingToken()
            );
        }
        long elapsedNanos = System.nanoTime() - start;
        double avgMs = (elapsedNanos / 5.0) / 1_000_000.0;

        assertTrue(avgMs <= 1.0, "平均单次快照保存耗时必须 <= 1.0ms，实测: " + avgMs + "ms");

        List<StateSnapshot> allSnapshots = checkpointManager.getAllCheckpoints(workflowId);
        assertEquals(5, allSnapshots.size(), "必须完整保存 5 步快照历史");
        assertEquals("STEP_5", checkpointManager.getLatestCheckpoint(workflowId).orElseThrow().stepName());
    }

    // =========================================================================
    // TC-138-5: 断点恢复零冗余执行与秒级就地续跑
    // =========================================================================
    @Test
    @DisplayName("TC-138-5: 协调节点在第 4 步崩溃后由备用节点无损接管，前序步骤重复执行数为 0，续跑总延迟 <= 50ms")
    void testBreakpointResumption_zeroRedundantExecution() {
        String workflowId = "WF-HEAVY-RAG-INFERENCE-003";
        LeaderLease lease = checkpointManager.acquireNewLeaderLease(workflowId, "worker_lead", 5000L);

        // 模拟顺序执行完成 1~4 步并落盘快照
        for (int i = 1; i <= 4; i++) {
            checkpointManager.saveCheckpoint(
                    workflowId, i, "STAGE_" + i, "RESULT_" + i, Map.of("stage", i), lease.fencingToken()
            );
        }

        // 模拟 worker_lead 节点崩溃，备用节点 worker_standby 触发断点恢复
        long start = System.nanoTime();
        ResumptionResult res = checkpointManager.resumeFromCheckpoint(workflowId, "worker_standby");
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(res);
        assertTrue(res.success(), "断点恢复必须成功");
        assertEquals(5, res.resumedStepIndex(), "恢复起始步骤必须为步骤 5 (第 4 步的下一步)");
        assertEquals(4, res.skippedStepCount(), "跳过前序已完成步骤数必须严格为 4 (0 冗余)");
        assertTrue(res.newFencingToken() > lease.fencingToken(), "新主管必须获分配更高版本的 Fencing Token");
        assertTrue(elapsedMs <= 50.0, "断点恢复耗时必须严格 <= 50ms，实测: " + elapsedMs + "ms");
    }

    // =========================================================================
    // TC-138-6: 租约超时看门狗与活性自愈判定
    // =========================================================================
    @Test
    @DisplayName("TC-138-6: 依据 Lemma 3.1，当租约超过 TTL 时，看门狗判定租约超时触发自愈")
    void testLeaseTimeout_watchdogActiveHealing() throws InterruptedException {
        String workflowId = "WF-LEASE-TIMEOUT-TEST";
        // 分配极短 TTL (20ms) 模拟网络失联租约超时
        checkpointManager.acquireNewLeaderLease(workflowId, "worker_flaky", 20L);

        // 立即检查，未超时
        assertFalse(checkpointManager.isLeaseExpiredOrUnassigned(workflowId), "刚获取租约时不应超时");

        // 睡眠 25ms 跨过 TTL
        Thread.sleep(25L);

        // 再次检查，已超时触发看门狗
        assertTrue(checkpointManager.isLeaseExpiredOrUnassigned(workflowId), "租约超过 TTL 后必须判定为超时已失效");
    }

    // =========================================================================
    // TC-138-7: 纯 Java 21 Record 凭单签名不可变性与 SHA-256 自验真
    // =========================================================================
    @Test
    @DisplayName("TC-138-7: 纯 Java 21 Record 凭单全字段不可变与 SHA-256 哈希常量时间自验真")
    void testWorkflowSelfHealingReceipt_immutableVerification() {
        String workflowId = "WF-RECEIPT-AUDIT-007";
        String actionType = "DEADLOCK_BROKEN";
        long fencingToken = 1050L;
        List<String> cycleNodes = List.of("agent_fraud", "agent_finance", "agent_hitl");
        int resumedStep = 3;
        double latencyMs = 1.25;
        String summary = "成功剪断最小代价边 (hitl -> fraud)，注入安全降级默认值";

        WorkflowSelfHealingReceipt receipt = WorkflowSelfHealingReceipt.create(
                workflowId, actionType, fencingToken, cycleNodes, resumedStep, latencyMs, summary
        );

        assertNotNull(receipt);
        assertTrue(receipt.receiptId().startsWith("RCP-SELF-HEAL-"));
        assertEquals(64, receipt.sha256Signature().length(), "SHA-256 签名必须为 64 位字符");
        assertTrue(receipt.verifySignature(), "原始不可变凭单签名自验真必须 100% 成功");

        // 篡改测试
        WorkflowSelfHealingReceipt tamperedReceipt = new WorkflowSelfHealingReceipt(
                receipt.receiptId(),
                "TAMPERED_WORKFLOW_ID",
                receipt.actionType(),
                receipt.fencingToken(),
                receipt.deadlockCycleNodes(),
                receipt.resumedStepIndex(),
                receipt.latencyMs(),
                receipt.diagnosticSummary(),
                receipt.timestamp(),
                receipt.sha256Signature()
        );
        assertFalse(tamperedReceipt.verifySignature(), "篡改字段后自验真必须严格返回 false");
    }

    // =========================================================================
    // TC-138-8: 端到端全链路自愈闭环集成与防篡改签发
    // =========================================================================
    @Test
    @DisplayName("TC-138-8: 端到端闭环: 网状协同 -> 死锁注入 -> WFG 检出与破环 -> 模拟主管假死 -> Fencing 接管断点续跑 -> 凭单签发")
    void testEndToEndWorkflowSelfHealing_fullPipelineIntegration() {
        String workflowId = "WF-E2E-FULL-PIPELINE-008";
        wfgDetector.clear();

        // 1. 注册多智能体网状协作并注入死锁环路
        wfgDetector.registerNode("agent_A", "ANALYSIS", "TASK_A");
        wfgDetector.registerNode("agent_B", "EXECUTION", "TASK_B");
        wfgDetector.addWaitEdge("agent_A", "agent_B", "DATA_PIPE", 0.4);
        wfgDetector.addWaitEdge("agent_B", "agent_A", "ACK_SIGNAL", 0.1); // 死锁

        // 2. WFG 微秒级死锁检出
        List<DeadlockCycle> cycles = wfgDetector.detectDeadlockCycles();
        assertFalse(cycles.isEmpty());
        DeadlockResolutionResult breakRes = wfgDetector.resolveDeadlock(cycles.get(0), "{\"ack\": true}");
        assertTrue(breakRes.resolved());

        // 3. 工作流正常推进前两步
        LeaderLease initialLease = checkpointManager.acquireNewLeaderLease(workflowId, "primary_worker", 3000L);
        checkpointManager.saveCheckpoint(workflowId, 1, "INIT_DATA", "OK", Map.of(), initialLease.fencingToken());
        checkpointManager.saveCheckpoint(workflowId, 2, "PROCESS_DATA", "OK", Map.of(), initialLease.fencingToken());

        // 4. 模拟 primary_worker 假死，备用节点 standby_worker 接管续跑
        ResumptionResult resumption = checkpointManager.resumeFromCheckpoint(workflowId, "standby_worker");
        assertTrue(resumption.success());
        assertEquals(3, resumption.resumedStepIndex());
        assertEquals(2, resumption.skippedStepCount());

        // 5. 备用节点完成后续步骤
        checkpointManager.saveCheckpoint(
                workflowId, 3, "FINALIZE_REPORT", "FINAL_OUTPUT", Map.of(), resumption.newFencingToken()
        );

        // 6. 签发不可变自愈存证凭单
        WorkflowSelfHealingReceipt receipt = WorkflowSelfHealingReceipt.create(
                workflowId,
                "E2E_DEADLOCK_HEALED_AND_RESUMED",
                resumption.newFencingToken(),
                List.of("agent_A", "agent_B"),
                resumption.resumedStepIndex(),
                resumption.resumptionLatencyMs(),
                "端到端死锁破环自愈与断点就地续跑成功"
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "端到端自愈凭单自验真必须 100% 成功");
    }
}
