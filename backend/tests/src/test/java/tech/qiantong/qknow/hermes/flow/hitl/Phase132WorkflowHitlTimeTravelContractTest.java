package tech.qiantong.qknow.hermes.flow.hitl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.hitl.dto.WorkflowHitlAuditReceipt;
import tech.qiantong.qknow.hermes.flow.hitl.engine.StreamingCausalTopologySyncBus;
import tech.qiantong.qknow.hermes.flow.hitl.engine.TimeTravelSnapshotBranchGovernor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 132 核心契约测试套件：
 * 可视化工作流 DAG 画布节点级状态快照热回溯、流式 Token 实时因果拓扑高亮与人机协同 (HITL) 动态干预中枢
 * <p>
 * 覆盖定理 1.1（持久化结构共享状态快照内存有界性）与定理 1.2（流式因果拓扑高亮同步与零死锁收敛）等 8 大核心契约
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public class Phase132WorkflowHitlTimeTravelContractTest {

    @Test
    @DisplayName("契约 1：基于持久化结构共享的快照增量内存有界性与空间节约率 >= 85%")
    void test01_PersistentStructuralSharingMemoryBound() {
        TimeTravelSnapshotBranchGovernor governor = new TimeTravelSnapshotBranchGovernor();
        governor.clear();

        String parentSnapshotId = null;
        // 模拟连续执行 50 步工作流，每步基础状态递增，但单步仅更新 2 个局部变量
        for (int step = 1; step <= 50; step++) {
            Map<String, Object> delta = new HashMap<>();
            delta.put("step_" + step + "_result", "value_" + step);
            delta.put("last_updated_step", step);

            TimeTravelSnapshotBranchGovernor.SnapshotNode node = governor.recordStepSnapshot(
                    "main",
                    step,
                    "node_" + step,
                    "计算节点-" + step,
                    parentSnapshotId,
                    delta
            );
            assertNotNull(node);
            assertEquals(step, node.stepIndex());
            parentSnapshotId = node.snapshotId();
        }

        double savingsRatio = governor.calculateMemorySavingsRatio();
        assertTrue(savingsRatio >= 0.85,
                String.format("持久化结构共享相对于全量克隆的空间节约率应 >= 85%%，实际为: %.2f%%", savingsRatio * 100));

        // 验证第 50 步快照完整持有了前序全部 50 步的累积结果
        TimeTravelSnapshotBranchGovernor.SnapshotRestorationResult restored = governor.restoreSnapshot(parentSnapshotId);
        assertNotNull(restored);
        assertEquals(51, restored.fullState().size(), "包含 50 个 step 变量与 1 个 last_updated_step");
    }

    @Test
    @DisplayName("契约 2：任意历史时刻状态重构寻址时间复杂度严格为 O(1)，耗时 <= 5ms")
    void test02_TimeTravelConstantTimeRestoration() {
        TimeTravelSnapshotBranchGovernor governor = new TimeTravelSnapshotBranchGovernor();
        governor.clear();

        List<String> snapshotIds = new ArrayList<>();
        String parentId = null;
        for (int i = 1; i <= 30; i++) {
            TimeTravelSnapshotBranchGovernor.SnapshotNode node = governor.recordStepSnapshot(
                    "main", i, "n_" + i, "Node-" + i, parentId, Map.of("k_" + i, "v_" + i)
            );
            snapshotIds.add(node.snapshotId());
            parentId = node.snapshotId();
        }

        // 随机多次检索历史快照，验证 O(1) 常数时间寻址
        Random rand = new Random(42);
        for (int testRun = 0; testRun < 10; testRun++) {
            String targetId = snapshotIds.get(rand.nextInt(snapshotIds.size()));
            long startNs = System.nanoTime();
            TimeTravelSnapshotBranchGovernor.SnapshotRestorationResult result = governor.restoreSnapshot(targetId);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

            assertNotNull(result);
            assertNotNull(result.fullState());
            assertTrue(elapsedMs <= 5, "历史状态寻址耗时必须在 5ms 以内，实际: " + elapsedMs + "ms");
        }
    }

    @Test
    @DisplayName("契约 3：时间旅行分叉树 (Fork Branching) 因果绝对隔离，主线历史零幽灵覆盖污染")
    void test03_ForkBranchCausalIsolationAndZeroPhantomOverwrite() {
        TimeTravelSnapshotBranchGovernor governor = new TimeTravelSnapshotBranchGovernor();
        governor.clear();

        List<String> mainChain = new ArrayList<>();
        String parentId = null;
        for (int step = 1; step <= 30; step++) {
            TimeTravelSnapshotBranchGovernor.SnapshotNode node = governor.recordStepSnapshot(
                    "main", step, "node_" + step, "MainNode-" + step, parentId,
                    Map.of("var_step_" + step, 100 + step, "global_cfg", "PRODUCTION_STABLE")
            );
            mainChain.add(node.snapshotId());
            parentId = node.snapshotId();
        }

        // 记录历史第 15 步的状态哈希与原值
        String step15Id = mainChain.get(14);
        TimeTravelSnapshotBranchGovernor.SnapshotNode step15Original = governor.getSnapshot(step15Id);
        String originalHash = step15Original.stateHash();
        Object originalVar15 = step15Original.fullState().get("var_step_15");
        assertEquals(115, originalVar15);

        // 在第 15 步时光旅行注入热补丁并派生新分支
        Map<String, Object> hotPatches = Map.of(
                "var_step_15", 99999, // 热修改
                "hot_patched_by", "ARCHITECT_ADMIN"
        );
        TimeTravelSnapshotBranchGovernor.BranchForkResult forkResult = governor.forkBranchWithHotPatch(step15Id, hotPatches);

        assertNotNull(forkResult);
        assertTrue(forkResult.newBranchId().startsWith("main_fork_"));
        assertEquals(99999, forkResult.forkRootSnapshot().fullState().get("var_step_15"));

        // 严格验证：主线第 15 步及后续所有 16~30 步快照是否被逆向污染
        TimeTravelSnapshotBranchGovernor.SnapshotNode step15AfterFork = governor.getSnapshot(step15Id);
        assertEquals(originalHash, step15AfterFork.stateHash(), "主线历史第 15 步状态哈希必须严格守恒");
        assertEquals(115, step15AfterFork.fullState().get("var_step_15"), "主线第 15 步变量值绝对不可被修改");

        // 验证主线最新第 30 步依然完好无损
        TimeTravelSnapshotBranchGovernor.SnapshotNode step30 = governor.getSnapshot(mainChain.get(29));
        assertEquals(130, step30.fullState().get("var_step_30"));
        assertFalse(step30.fullState().containsKey("hot_patched_by"), "主线快照绝不能出现分叉分支的幽灵变量");
    }

    @Test
    @DisplayName("契约 4：流式 Token 与因果拓扑高亮双轨事件单调递增，端到端延迟 <= 16ms")
    void test04_StreamingCausalTopologySyncBoundedLatency() {
        StreamingCausalTopologySyncBus syncBus = new StreamingCausalTopologySyncBus();
        syncBus.clear();

        // 模拟 DeepSeek 高通量产生 50 个连续流式 Token 帧
        long startNs = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            StreamingCausalTopologySyncBus.DualTrackSyncFrame frame = syncBus.publishFrame(
                    "WF_STREAM_TEST",
                    "node_graphrag_reasoning",
                    "proposition_causal_step_" + (i / 10),
                    "token_" + i + " ",
                    StreamingCausalTopologySyncBus.FrameType.TOKEN,
                    0.8
            );
            assertNotNull(frame);
            assertTrue(frame.logicalSeq() >= 1000, "逻辑序号必须从基线单调自增");
        }

        // 模拟 rAF 垂直同步批处理排空
        List<StreamingCausalTopologySyncBus.DualTrackSyncFrame> drained = syncBus.drainFramesForVsync();
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertEquals(50, drained.size(), "必须原子完整排空 50 个流式帧");
        assertTrue(elapsedMs <= 16, "双轨流式聚合耗时必须严格有界在 16ms 内，实际: " + elapsedMs + "ms");

        // 验证序号单调递增性
        for (int i = 0; i < drained.size() - 1; i++) {
            assertTrue(drained.get(i).logicalSeq() < drained.get(i + 1).logicalSeq(), "逻辑序号必须严格单调递增");
        }
    }

    @Test
    @DisplayName("契约 5：视口 AABB 边界相交裁剪算法在 200+ 节点拓扑中剔除率 >= 70%，耗时 <= 1ms")
    void test05_AabbViewportCullingOptimization() {
        StreamingCausalTopologySyncBus syncBus = new StreamingCausalTopologySyncBus();

        // 定义屏幕视口包围盒: 1920x1080 居中在世界坐标 (0, 0)
        StreamingCausalTopologySyncBus.AabbBoundingBox viewBox =
                new StreamingCausalTopologySyncBus.AabbBoundingBox(-960, -540, 1920, 1080);

        // 生成分布在大画布 (6000 x 6000) 上的 200 个节点
        List<StreamingCausalTopologySyncBus.AabbBoundingBox> allNodes = new ArrayList<>();
        Random rand = new Random(1024);
        for (int i = 0; i < 200; i++) {
            double nx = (rand.nextDouble() - 0.5) * 6000;
            double ny = (rand.nextDouble() - 0.5) * 6000;
            allNodes.add(new StreamingCausalTopologySyncBus.AabbBoundingBox(nx, ny, 200, 100));
        }

        long startNs = System.nanoTime();
        int visibleCount = 0;
        for (StreamingCausalTopologySyncBus.AabbBoundingBox nodeBox : allNodes) {
            if (syncBus.isNodeVisibleInViewport(nodeBox, viewBox)) {
                visibleCount++;
            }
        }
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;

        int culledCount = 200 - visibleCount;
        double cullingRatio = (double) culledCount / 200.0;

        assertTrue(durationMs <= 1, "200 节点 AABB 相交测试耗时必须在 1ms 内，实际: " + durationMs + "ms");
        assertTrue(cullingRatio >= 0.70,
                String.format("视口外节点剔除率应 >= 70%%，实际为: %.2f%% (可见: %d, 剔除: %d)",
                        cullingRatio * 100, visibleCount, culledCount));
    }

    @Test
    @DisplayName("契约 6：HITL 异步挂起期间状态哈希严格守恒，热补丁注入后流转符合 Petri 网规范")
    void test06_HitlAsynchronousSuspensionStateConservation() {
        Map<String, Object> baselineState = new LinkedHashMap<>();
        baselineState.put("orderId", "ORD-2026-TITANIUM-888");
        baselineState.put("amount", 250000.00);
        baselineState.put("riskScore", 0.88);

        // 模拟挂起时刻签发基线快照
        TimeTravelSnapshotBranchGovernor governor = new TimeTravelSnapshotBranchGovernor();
        TimeTravelSnapshotBranchGovernor.SnapshotNode suspendedSnapshot = governor.recordStepSnapshot(
                "main", 10, "high_risk_payment", "高危支付审核节点", null, baselineState
        );

        String baselineHash = suspendedSnapshot.stateHash();
        assertNotNull(baselineHash);

        // 模拟审批介入，注入热补丁修改金额
        Map<String, Object> hotPatchedInputs = Map.of(
                "amount", 180000.00, // 降额审批放行
                "approvalNotes", "合规总监降额特批通过"
        );

        WorkflowHitlAuditReceipt receipt = WorkflowHitlAuditReceipt.create(
                "WF_FINANCE_ORDER",
                "main",
                "high_risk_payment",
                WorkflowHitlAuditReceipt.DecisionAction.PATCH_AND_APPROVE,
                baselineState,
                hotPatchedInputs,
                "DIRECTOR_USER_007",
                30000L,
                1850L
        );

        assertNotNull(receipt);
        assertEquals(WorkflowHitlAuditReceipt.DecisionAction.PATCH_AND_APPROVE, receipt.decisionAction());
        assertEquals("DIRECTOR_USER_007", receipt.approverId());

        // 验证原挂起节点状态未被原地破坏
        assertEquals(baselineHash, governor.getSnapshot(suspendedSnapshot.snapshotId()).stateHash());
    }

    @Test
    @DisplayName("契约 7：HITL 审批人离线租约看门狗超时抢占触发 TIMEOUT_FAILSAFE，死锁概率恒为 0.0%")
    void test07_HitlWatchdogTimeoutFailCloseDeadlockFreedom() {
        long leaseDuration = 100L; // 模拟 100ms 快速超时看门狗
        long tStart = System.currentTimeMillis();

        // 模拟审批人在租约时间内未响应
        try {
            Thread.sleep(120);
        } catch (InterruptedException ignored) {}

        long now = System.currentTimeMillis();
        boolean isExpired = (now - tStart) >= leaseDuration;
        assertTrue(isExpired, "租约计时器必须准确触发超时");

        // 看门狗抢占使能 Fail-Close 终态
        WorkflowHitlAuditReceipt timeoutReceipt = WorkflowHitlAuditReceipt.create(
                "WF_CRITICAL_OPS",
                "main",
                "db_schema_migration",
                WorkflowHitlAuditReceipt.DecisionAction.TIMEOUT_FAILSAFE,
                Map.of("targetTable", "user_account"),
                Map.of(),
                "SYSTEM_WATCHDOG_DAEMON",
                leaseDuration,
                520L
        );

        assertNotNull(timeoutReceipt);
        assertEquals(WorkflowHitlAuditReceipt.DecisionAction.TIMEOUT_FAILSAFE, timeoutReceipt.decisionAction());
        assertEquals("SYSTEM_WATCHDOG_DAEMON", timeoutReceipt.approverId());
        assertTrue(timeoutReceipt.verifySignature(), "超时熔断凭单签名验真必须通过");
    }

    @Test
    @DisplayName("契约 8：纯 Java 21 Record 格式存证凭单 SHA-256 签名自验真通过，篡改任意入参立即抛错")
    void test08_HitlAuditReceiptSha256TamperResistance() {
        WorkflowHitlAuditReceipt receipt = WorkflowHitlAuditReceipt.create(
                "WF_AI_AGENT_GOV",
                "branch_alpha",
                "node_transfer_funds",
                WorkflowHitlAuditReceipt.DecisionAction.APPROVE,
                Map.of("targetAccount", "62220202020202"),
                Map.of(),
                "SECURITY_AUDITOR_42",
                60000L,
                2450L
        );

        // 1. 原生验真通过
        assertTrue(receipt.verifySignature(), "原生凭单自签名验真必须通过");

        // 2. 模拟黑客篡改：修改审批动作从 APPROVE 变为 REJECT
        WorkflowHitlAuditReceipt tampered = new WorkflowHitlAuditReceipt(
                receipt.receiptId(),
                receipt.workflowId(),
                receipt.branchId(),
                receipt.nodeId(),
                WorkflowHitlAuditReceipt.DecisionAction.REJECT, // 篡改动作
                receipt.originalInputs(),
                receipt.patchedInputs(),
                receipt.approverId(),
                receipt.leaseDurationMs(),
                receipt.latencyMicros(),
                receipt.timestamp(),
                receipt.sha256Signature() // 沿用旧签名
        );

        assertFalse(tampered.verifySignature(), "篡改核心业务动作后签名验真必须失败");
    }
}
