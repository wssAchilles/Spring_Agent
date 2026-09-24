package tech.qiantong.qknow.hermes.flow.checkpoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager;
import tech.qiantong.qknow.hermes.flow.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.McpSagasCompensationGovernor;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.McpSagasCompensationReceipt;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 126 核心契约测试：
 * 分布式 MCP 工具调用断点租约超时自愈 (Lease/Heartbeat TTL)、级联依赖事务补偿 (Sagas) 与所有权仲裁中枢
 * 覆盖定理 1.1（Fencing Token 写屏障）与定理 1.2（租约超时活性自愈）等 8 大核心契约
 */
public class Phase126McpLeaseLivenessContractTest {

    @Test
    @DisplayName("契约 1：普通挂起断点通过双轨原子 CAS 正常唤醒并签发合法凭单")
    void test01_NormalSuspendedWakeupMaintainsFencingToken() {
        DagCheckpointManager manager = new DagCheckpointManager();
        String runtimeId = "rt-test-01";
        String flowId = "flow-approval";

        // 构造一个挂起断点
        Map<String, NodeRunResultBO> results = new LinkedHashMap<>();
        NodeRunResultBO suspendedNode = new NodeRunResultBO();
        suspendedNode.setNodeUuid("hitl-node-1");
        suspendedNode.setStatus(RuntimeStatusEnums.SUSPENDED.getCode());
        results.put("hitl-node-1", suspendedNode);

        manager.saveCheckpointWithVariables(runtimeId, flowId, 1, results, Map.of("step", 1));

        // 模拟节点 node-worker-A 唤醒并获取租约（租约时长 5000ms）
        Map<String, Object> humanInput = Map.of("approved", true, "approver", "zhangsan");
        Optional<LeaseLivenessRecoveryReceipt> receiptOpt = manager.wakeSuspendedOrRecoverLease(
                runtimeId, "node-worker-A", 5000L, humanInput);

        assertTrue(receiptOpt.isPresent(), "挂起断点应能成功被唤醒抢占");
        LeaseLivenessRecoveryReceipt receipt = receiptOpt.get();

        assertEquals(runtimeId, receipt.runtimeId());
        assertEquals("node-worker-A", receipt.newOwnerId());
        assertEquals(1L, receipt.previousFencingToken());
        assertEquals(2L, receipt.newFencingToken(), "令牌必须从 1 单调递增为 2");
        assertEquals("SUSPENDED_NORMAL_WAKEUP", receipt.triggerReason());
        assertTrue(receipt.verifySignature(), "凭单 SHA-256 签名自验真必须 100% 成立");

        // 检查断点已被转为 RUNNING
        DagCheckpointManager.DagCheckpoint loaded = manager.loadCheckpoint(runtimeId);
        assertNotNull(loaded);
        assertEquals("RUNNING", loaded.getStatus());
        assertEquals("node-worker-A", loaded.getLeaseOwnerId());
        assertEquals(2L, loaded.getFencingToken());
    }

    @Test
    @DisplayName("契约 2：持有节点崩溃超时后，健康探活节点原子接管自愈 (定理 1.2)")
    void test02_CrashingOwnerLeaseTimeoutAutonomousTakeover() {
        DagCheckpointManager manager = new DagCheckpointManager();
        String runtimeId = "rt-crash-02";
        String flowId = "flow-long-task";

        // 初始建立检查点
        manager.saveCheckpoint(runtimeId, flowId, 1, Collections.emptyMap());

        // 模拟 node-crashed-1 持有租约，但租约时间戳已经过期
        DagCheckpointManager.DagCheckpoint cp = manager.loadCheckpoint(runtimeId);
        cp.setStatus("RUNNING");
        cp.setLeaseOwnerId("node-crashed-1");
        cp.setFencingToken(2L);
        // 设置为 10 秒前已过期
        cp.setLeaseExpireAt(new Timestamp(System.currentTimeMillis() - 10_000L));

        // 使用探活中枢协调器检测并接管
        try (LeaseLivenessRecoveryCoordinator coordinator = new LeaseLivenessRecoveryCoordinator(manager, "node-healthy-2", 8000L)) {
            List<LeaseLivenessRecoveryReceipt> recovered = coordinator.recoverExpiredLeases();
            assertEquals(1, recovered.size(), "探活中枢必须准确检出并抢占该超时断点");

            LeaseLivenessRecoveryReceipt receipt = recovered.get(0);
            assertEquals("node-crashed-1", receipt.previousOwnerId());
            assertEquals("node-healthy-2", receipt.newOwnerId());
            assertEquals(2L, receipt.previousFencingToken());
            assertEquals(3L, receipt.newFencingToken(), "自愈后令牌必须单调递增为 3");
            assertEquals("LEASE_TIMEOUT_TAKEOVER", receipt.triggerReason());
            assertTrue(receipt.verifySignature(), "凭单验真通过");

            // 检查数据库中已变为健康节点持有
            DagCheckpointManager.DagCheckpoint updated = manager.loadCheckpoint(runtimeId);
            assertEquals("node-healthy-2", updated.getLeaseOwnerId());
            assertEquals(3L, updated.getFencingToken());
            assertTrue(updated.getLeaseExpireAt().getTime() > System.currentTimeMillis());
        }
    }

    @Test
    @DisplayName("契约 3：租约在有效期内，第三方节点强行抢占 100% 遭到拒绝")
    void test03_UnexpiredLeaseRejectsIllegalTakeover() {
        DagCheckpointManager manager = new DagCheckpointManager();
        String runtimeId = "rt-active-03";

        manager.saveCheckpoint(runtimeId, "flow-1", 1, Collections.emptyMap());
        DagCheckpointManager.DagCheckpoint cp = manager.loadCheckpoint(runtimeId);
        cp.setStatus("RUNNING");
        cp.setLeaseOwnerId("node-active-owner");
        cp.setFencingToken(5L);
        // 设置租约还有 30 秒才到期
        cp.setLeaseExpireAt(new Timestamp(System.currentTimeMillis() + 30_000L));

        // 试图由入侵节点 node-intruder 强占
        Optional<LeaseLivenessRecoveryReceipt> takeoverOpt = manager.wakeSuspendedOrRecoverLease(
                runtimeId, "node-intruder", 5000L, null);

        assertTrue(takeoverOpt.isEmpty(), "租约未到期且非挂起状态，非法抢占必须被 100% 拒绝");

        // 状态未被篡改
        DagCheckpointManager.DagCheckpoint cpAfter = manager.loadCheckpoint(runtimeId);
        assertEquals("node-active-owner", cpAfter.getLeaseOwnerId());
        assertEquals(5L, cpAfter.getFencingToken());
    }

    @Test
    @DisplayName("契约 4：Fencing Token 写屏障彻底物理拦截陈旧节点覆写 (定理 1.1)")
    void test04_StaleOwnerWriteBlockedByFencingTokenBarrier() {
        DagCheckpointManager manager = new DagCheckpointManager();
        String runtimeId = "rt-fencing-04";

        manager.saveCheckpoint(runtimeId, "flow-fencing", 1, Collections.emptyMap());

        // 初始状态：node-A 持有 token=1
        DagCheckpointManager.DagCheckpoint cp = manager.loadCheckpoint(runtimeId);
        cp.setFencingToken(1L);
        cp.setLeaseOwnerId("node-A");

        // 假死发生，超时被 node-B 接管，token 递增为 2
        cp.setFencingToken(2L);
        cp.setLeaseOwnerId("node-B");

        // 现在 node-A 从假死长 GC 中苏醒，试图使用旧 token=1 写入中间执行结果
        boolean staleWriteSuccess = manager.saveCheckpointWithFencingToken(
                runtimeId, "flow-fencing", 2, Collections.emptyMap(), Map.of("data", "corrupted_by_A"), 1L);

        assertFalse(staleWriteSuccess, "Fencing Token 写屏障必须物理拦截陈旧节点 node-A 的幽灵写");

        // node-B 使用最新有效 token=2 写入
        boolean validWriteSuccess = manager.saveCheckpointWithFencingToken(
                runtimeId, "flow-fencing", 2, Collections.emptyMap(), Map.of("data", "valid_by_B"), 2L);

        assertTrue(validWriteSuccess, "持有合法最新 token 的节点 node-B 写入必须成功");

        DagCheckpointManager.DagCheckpoint finalCp = manager.loadCheckpoint(runtimeId);
        Map<String, Object> vars = manager.restoreVariables(finalCp);
        assertEquals("valid_by_B", vars.get("data"), "最终持久化数据绝不包含被拦截的幽灵写");
    }

    @Test
    @DisplayName("契约 5：活跃节点通过心跳正常续约租约")
    void test05_ActiveNodeHeartbeatLeaseRenewal() {
        DagCheckpointManager manager = new DagCheckpointManager();
        String runtimeId = "rt-heartbeat-05";

        manager.saveCheckpoint(runtimeId, "flow-hb", 1, Collections.emptyMap());
        DagCheckpointManager.DagCheckpoint cp = manager.loadCheckpoint(runtimeId);
        cp.setStatus("RUNNING");
        cp.setLeaseOwnerId("node-master");
        cp.setFencingToken(3L);
        long initialExpire = System.currentTimeMillis() + 1000L;
        cp.setLeaseExpireAt(new Timestamp(initialExpire));

        // 节点使用正确身份与令牌续租 10000ms
        boolean renewSuccess = manager.refreshLease(runtimeId, "node-master", 3L, 10_000L);
        assertTrue(renewSuccess, "合法心跳续约必须成功");

        DagCheckpointManager.DagCheckpoint renewed = manager.loadCheckpoint(runtimeId);
        assertTrue(renewed.getLeaseExpireAt().getTime() > initialExpire, "租约到期时间必须被成功后移");

        // 使用错误令牌或身份续约必失败
        assertFalse(manager.refreshLease(runtimeId, "node-other", 3L, 10_000L), "非法 ownerId 续约必须失败");
        assertFalse(manager.refreshLease(runtimeId, "node-master", 2L, 10_000L), "陈旧 fencingToken 续约必须失败");
    }

    @Test
    @DisplayName("契约 6：Sagas LIFO 严格逆序事务补偿调度与最终一致性收敛")
    void test06_SagasLIFOInverseCompensationExecution() {
        DagCheckpointManager manager = new DagCheckpointManager();
        String runtimeId = "rt-saga-06";
        manager.saveCheckpoint(runtimeId, "flow-erp-purchase", 1, Collections.emptyMap());

        McpSagasCompensationGovernor governor = new McpSagasCompensationGovernor(manager);

        // 正向按顺序执行了三个 MCP 工具调用
        // 步骤 1：ERP 锁定库存
        governor.recordStep(runtimeId, "step-1", "erp_lock_inventory", "erp_unlock_inventory",
                Map.of("sku", "ITEM-999", "quantity", 10), "token-1");

        // 步骤 2：财务冻结预算
        governor.recordStep(runtimeId, "step-2", "finance_freeze_budget", "finance_unfreeze_budget",
                Map.of("department", "RD", "amount", 50000), "token-2");

        // 步骤 3：电子签章发起（此步骤正向失败，因此只需逆向补偿 step-2 与 step-1）

        // 模拟执行逆序补偿，记录补偿执行轨迹
        List<String> actualExecutionSequence = new CopyOnWriteArrayList<>();
        Map<String, java.util.function.Function<Map<String, Object>, Boolean>> handlers = new HashMap<>();

        handlers.put("finance_unfreeze_budget", params -> {
            actualExecutionSequence.add("finance_unfreeze_budget");
            return true;
        });

        handlers.put("erp_unlock_inventory", params -> {
            actualExecutionSequence.add("erp_unlock_inventory");
            return true;
        });

        McpSagasCompensationReceipt receipt = governor.executeLIFOCompensation(runtimeId, handlers);

        assertNotNull(receipt);
        assertTrue(receipt.fullyCompensated(), "所有步骤补偿必须全部成功");
        assertEquals(2, receipt.successfullyCompensatedSteps());
        assertEquals(2, receipt.totalStepsToCompensate());

        // 验证执行轨迹严格满足 LIFO 逆序：step-2 (财务退款) 先于 step-1 (库存解锁)
        assertEquals(2, actualExecutionSequence.size());
        assertEquals("finance_unfreeze_budget", actualExecutionSequence.get(0), "最晚执行的步骤必须最先补偿");
        assertEquals("erp_unlock_inventory", actualExecutionSequence.get(1), "最早执行的步骤最后补偿");

        assertTrue(receipt.verifySignature(), "Sagas 补偿凭单 SHA-256 自验真必须通过");

        // 检查检查点状态已被标记为 COMPENSATED
        DagCheckpointManager.DagCheckpoint finalCp = manager.loadCheckpoint(runtimeId);
        assertEquals("COMPENSATED", finalCp.getStatus());
    }

    @Test
    @DisplayName("契约 7：自愈存证凭单防篡改与自验真完整性")
    void test07_ReceiptSha256ImmutabilityAndSelfVerification() {
        LeaseLivenessRecoveryReceipt validReceipt = LeaseLivenessRecoveryReceipt.create(
                "rt-verify-07",
                "flow-sec",
                "node-old",
                "node-new",
                10L,
                11L,
                "LEASE_TIMEOUT_TAKEOVER",
                6000L
        );
        assertTrue(validReceipt.verifySignature(), "合法生成的凭单验真必须通过");

        // 伪造篡改 newOwnerId
        LeaseLivenessRecoveryReceipt tamperedReceipt = new LeaseLivenessRecoveryReceipt(
                validReceipt.receiptId(),
                validReceipt.runtimeId(),
                validReceipt.flowId(),
                validReceipt.previousOwnerId(),
                "node-hacker", // 篡改
                validReceipt.previousFencingToken(),
                validReceipt.newFencingToken(),
                validReceipt.triggerReason(),
                validReceipt.leaseDurationMs(),
                validReceipt.timestamp(),
                validReceipt.signature()
        );

        assertFalse(tamperedReceipt.verifySignature(), "字段遭篡改的伪造凭单自验真必须 100% 失败");
    }

    @Test
    @DisplayName("契约 8：高并发多线程争夺超时断点，恰有一方胜出，0 脑裂")
    void test08_HighConcurrencyAtomicArbitrationNoDoubleTakeover() throws InterruptedException {
        DagCheckpointManager manager = new DagCheckpointManager();
        String runtimeId = "rt-concurrent-08";
        manager.saveCheckpoint(runtimeId, "flow-concurrent", 1, Collections.emptyMap());

        // 构造一个已经超时的断点
        DagCheckpointManager.DagCheckpoint cp = manager.loadCheckpoint(runtimeId);
        cp.setStatus("RUNNING");
        cp.setLeaseOwnerId("node-crashed");
        cp.setFencingToken(1L);
        cp.setLeaseExpireAt(new Timestamp(System.currentTimeMillis() - 5000L));

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger winCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<LeaseLivenessRecoveryReceipt> wonReceipts = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String workerNodeId = "worker-competitor-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Optional<LeaseLivenessRecoveryReceipt> opt = manager.wakeSuspendedOrRecoverLease(
                            runtimeId, workerNodeId, 5000L, null);
                    if (opt.isPresent()) {
                        winCount.incrementAndGet();
                        wonReceipts.add(opt.get());
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS), "并发抢占应在 5 秒内完成");
        executor.shutdownNow();

        // 强互斥仲裁断言：8 个并发争夺者中，恰有 1 个胜出，7 个失败
        assertEquals(1, winCount.get(), "并发争夺必须恰好只有 1 个胜出节点，彻底杜绝双主脑裂");
        assertEquals(threadCount - 1, failCount.get());

        LeaseLivenessRecoveryReceipt winnerReceipt = wonReceipts.get(0);
        assertTrue(winnerReceipt.verifySignature());
        assertEquals(2L, winnerReceipt.newFencingToken());
    }
}
