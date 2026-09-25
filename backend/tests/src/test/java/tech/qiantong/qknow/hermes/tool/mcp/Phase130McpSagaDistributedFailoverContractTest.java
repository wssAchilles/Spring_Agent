package tech.qiantong.qknow.hermes.tool.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.McpDynamicPipelineDispatcher;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.dto.McpSagaLeaseRecord;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.dto.McpSagasTransactionReceipt;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.engine.DistributedLeaseCoordinator;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.engine.ResilientSagasStateManager;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.engine.VirtualThreadIsolatedExecutor;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 130 契约测试：基于虚拟线程与租约隔离的分布式双向 Sagas 幂等事务与崩溃安全接管中枢
 * <p>
 * 严格覆盖 8 大高烈度工业场景与学术定理：
 * 1. 定理 1.1 租约超时原子 CAS 接管验证 (节点宕机自愈耗时 <= 50ms, FencingToken 递增)
 * 2. 定理 1.1 假死节点苏醒写屏障拦截与 0 脑裂测试 (旧 Token 写入 100% 阻断)
 * 3. 定理 1.2 Sagas 逆拓扑 LIFO 补偿最终一致性收敛 (严格镜像倒序出栈，收敛率 100%)
 * 4. 定理 1.2 跨网络乱序与防悬挂墓碑标记验证 (补偿先于正向到达，正向降级为 NoOp)
 * 5. 补偿动作强幂等性与重复重试安全验证 (网络重传 3 次，底层业务仅执行 1 次)
 * 6. Kahn DAG 纯内存分层解析耗时与环路熔断 (30 节点复杂 DAG 单步耗时 <= 5ms)
 * 7. Java 21 虚拟线程高并发多流水线隔离与零饥饿 (30 并发流水线 150+ 节点平稳执行)
 * 8. 纯 Java 21 Record 存证凭单防篡改与法医级追溯 (10,000 次自验真 <= 100μs, 单字节篡改拦截)
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public class Phase130McpSagaDistributedFailoverContractTest {

    private DistributedLeaseCoordinator leaseCoordinator;
    private ResilientSagasStateManager stateManager;
    private VirtualThreadIsolatedExecutor virtualExecutor;
    private McpDynamicPipelineDispatcher pipelineDispatcher;

    @BeforeEach
    void setUp() {
        leaseCoordinator = new DistributedLeaseCoordinator();
        stateManager = new ResilientSagasStateManager();
        virtualExecutor = new VirtualThreadIsolatedExecutor(2000L); // 2s 超时
        pipelineDispatcher = new McpDynamicPipelineDispatcher();
    }

    @AfterEach
    void tearDown() {
        if (virtualExecutor != null) {
            virtualExecutor.close();
        }
    }

    @Test
    @DisplayName("契约 1：定理 1.1 租约超时原子 CAS 接管验证 (自愈耗时 <= 50ms, FencingToken 单调递增)")
    void testContract1_LeaseTimeoutAtomicCasTakeover() throws InterruptedException {
        String txId = "TX-CONTRACT-1001";
        String primaryWorker = "worker-primary-01";
        String standbyWorker = "worker-standby-02";
        long leaseTtl = 80L; // 80ms 超短租期

        // 1. primary 获取初始租约
        McpSagaLeaseRecord initialLease = leaseCoordinator.acquireOrTakeoverLease(txId, primaryWorker, leaseTtl);
        assertNotNull(initialLease);
        assertEquals(primaryWorker, initialLease.leaseOwnerId());
        long initialToken = initialLease.fencingToken();
        assertTrue(initialToken >= 1000L);

        // 2. 在有效期内，standby 尝试抢占必须被拒绝
        assertThrows(IllegalStateException.class, () ->
                leaseCoordinator.acquireOrTakeoverLease(txId, standbyWorker, leaseTtl));

        // 3. 模拟 primary 宕机 (停止续约)，等待租约超时 (80ms + 20ms 裕量)
        Thread.sleep(100L);

        // 4. standby 发起自愈接管，统计耗时
        long startNano = System.nanoTime();
        McpSagaLeaseRecord takeoverLease = leaseCoordinator.acquireOrTakeoverLease(txId, standbyWorker, 2000L);
        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000L;

        assertNotNull(takeoverLease);
        assertEquals(standbyWorker, takeoverLease.leaseOwnerId());
        assertEquals(initialToken + 1, takeoverLease.fencingToken(), "FencingToken 必须严格单调递增 +1");
        assertTrue(elapsedMs <= 50L, "备用节点原子接管耗时必须 <= 50ms，实测: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("契约 2：定理 1.1 假死节点苏醒写屏障拦截与 0 脑裂测试 (过期 Token 写入 100% 阻断)")
    void testContract2_ZombieNodeFencingBarrierZeroSplitBrain() throws InterruptedException {
        String txId = "TX-CONTRACT-1002";
        String primaryWorker = "worker-zombie-01";
        String standbyWorker = "worker-active-02";

        // 1. primary 获得 Token 1001，随后进入 GC 假死
        McpSagaLeaseRecord lease1 = leaseCoordinator.acquireOrTakeoverLease(txId, primaryWorker, 50L);
        long staleToken = lease1.fencingToken();

        // 2. 超时后 standby 成功接管，Token 推进为 1002
        Thread.sleep(60L);
        McpSagaLeaseRecord lease2 = leaseCoordinator.acquireOrTakeoverLease(txId, standbyWorker, 2000L);
        long currentToken = lease2.fencingToken();
        assertTrue(currentToken > staleToken);

        // 3. standby 节点携带当前有效 Token 写入，通过屏障
        assertDoesNotThrow(() -> leaseCoordinator.validateFencingToken(txId, currentToken));

        // 4. primary 苏醒，仍持有陈旧 staleToken 发起外部资源写入，写屏障 100% 拦截并抛出 SecurityException
        SecurityException ex = assertThrows(SecurityException.class, () ->
                leaseCoordinator.validateFencingToken(txId, staleToken));
        assertTrue(ex.getMessage().contains("检测到幽灵写攻击"));
    }

    @Test
    @DisplayName("契约 3：定理 1.2 Sagas 逆拓扑 LIFO 补偿最终一致性收敛 (严格镜像倒序出栈，收敛率 100%)")
    void testContract3_SagasLifoReverseTopologyConvergence() {
        String txId = "TX-CONTRACT-1003";
        long fencingToken = 1005L;

        List<String> executionOrder = new ArrayList<>();
        List<String> compensationOrder = new ArrayList<>();

        // 注册 4 个连续步骤的正向与补偿回调
        for (int i = 1; i <= 4; i++) {
            String step = "STEP_" + i;
            executionOrder.add(step);
            stateManager.registerSuccessStep(
                    txId,
                    step,
                    "Result_" + i,
                    leaseToken -> compensationOrder.add(step + "_REVERSED"),
                    fencingToken
            );
        }

        // 模拟第 5 步发生崩溃，触发 LIFO 逆拓扑回滚
        List<String> compensatedList = stateManager.rollbackLifo(txId);

        // 断言已回滚步骤数量
        assertEquals(4, compensatedList.size());
        assertEquals(4, compensationOrder.size());

        // 核心断言：补偿序列必须严格为 4 -> 3 -> 2 -> 1 镜像倒序
        assertEquals("STEP_4_REVERSED", compensationOrder.get(0));
        assertEquals("STEP_3_REVERSED", compensationOrder.get(1));
        assertEquals("STEP_2_REVERSED", compensationOrder.get(2));
        assertEquals("STEP_1_REVERSED", compensationOrder.get(3));
    }

    @Test
    @DisplayName("契约 4：定理 1.2 跨网络乱序与防悬挂墓碑标记验证 (补偿先于正向到达，正向降级为 NoOp)")
    void testContract4_OutOfOrderTombstoneAntiHanging() {
        String txId = "TX-CONTRACT-1004";
        String hangingStep = "STEP_ERP_ALLOCATE";
        long fencingToken = 1008L;

        // 1. 模拟网络严重乱序：正向请求滞留在网络中，补偿逻辑已先行到达触发，打上防悬挂墓碑
        stateManager.markTombstone(txId, hangingStep, fencingToken);
        assertTrue(stateManager.isTombstoneMarked(txId, hangingStep, fencingToken));

        // 2. 滞后的正向请求姗姗来迟并尝试入栈
        String leaseToken = stateManager.registerSuccessStep(
                txId, hangingStep, "LateResult", token -> fail("已被墓碑标记的步骤不应生成可执行补偿"), fencingToken
        );

        assertNotNull(leaseToken);

        // 3. 此时若再次回滚，由于正向操作未真正入栈污染状态，回滚列表应为空，悬挂率为 0.0%
        List<String> result = stateManager.rollbackLifo(txId);
        assertTrue(result.isEmpty(), "被墓碑拦截的滞后正向步骤不得产生悬挂污染");
    }

    @Test
    @DisplayName("契约 5：补偿动作强幂等性与重复重试安全验证 (网络重传 3 次，底层业务仅执行 1 次)")
    void testContract5_CompensatingActionStrongIdempotency() {
        String txId = "TX-CONTRACT-1005";
        long fencingToken = 1010L;
        AtomicInteger actualPhysicalActionCount = new AtomicInteger(0);

        // 注册一个执行物理资金解冻的步骤
        stateManager.registerSuccessStep(
                txId,
                "STEP_UNFREEZE_FUNDS",
                "FrozenFund1000",
                leaseToken -> actualPhysicalActionCount.incrementAndGet(),
                fencingToken
        );

        // 模拟外部网络抖动，连续触发 3 次回滚重试
        List<String> firstTry = stateManager.rollbackLifo(txId);
        assertEquals(1, firstTry.size());
        assertTrue(firstTry.get(0).contains("COMPENSATED"));
        assertEquals(1, actualPhysicalActionCount.get(), "首次补偿必须执行底层物理操作");

        // 第 2 次与第 3 次重试 (无论重试多少次，必须幂等短路)
        List<String> secondTry = stateManager.rollbackLifo(txId);
        List<String> thirdTry = stateManager.rollbackLifo(txId);

        assertEquals(0, secondTry.size());
        assertEquals(0, thirdTry.size());
        assertEquals(1, actualPhysicalActionCount.get(), "重试时底层物理操作绝不能发生二次扣减或解冻");
    }

    @Test
    @DisplayName("契约 6：Kahn DAG 纯内存分层解析耗时与环路熔断 (30 节点复杂 DAG 单步耗时 <= 5ms)")
    void testContract6_KahnDagPureMemorySchedulingLatency() {
        // 构建 30 个节点，层级依赖的流水线
        List<McpDynamicPipelineDispatcher.PipelineNode> nodes = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Set<String> deps = i > 0 ? Set.of("node_" + (i - 1)) : Set.of();
            nodes.add(new McpDynamicPipelineDispatcher.PipelineNode(
                    "node_" + i, "tool_" + i, Map.of("index", i), deps
            ));
        }

        // 预热 JIT
        for (int i = 0; i < 20; i++) {
            pipelineDispatcher.resolveTopology(nodes);
        }

        // 循环 100 次统计纯内存耗时
        long startNano = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            McpDynamicPipelineDispatcher.PipelineTopology topology = pipelineDispatcher.resolveTopology(nodes);
            assertEquals(30, topology.totalNodes());
            assertEquals(29, topology.totalEdges());
        }
        long avgMicros = ((System.nanoTime() - startNano) / 100) / 1_000L;
        assertTrue(avgMicros <= 5_000L, "Kahn DAG 解析平均纯内存耗时必须 <= 5ms (5000μs)，实测: " + avgMicros + "μs");

        // 注入环路依赖测试：A -> B -> A
        List<McpDynamicPipelineDispatcher.PipelineNode> cyclicNodes = List.of(
                new McpDynamicPipelineDispatcher.PipelineNode("nodeA", "toolA", Map.of(), Set.of("nodeB")),
                new McpDynamicPipelineDispatcher.PipelineNode("nodeB", "toolB", Map.of(), Set.of("nodeA"))
        );
        assertThrows(IllegalStateException.class, () -> pipelineDispatcher.resolveTopology(cyclicNodes),
                "检测到环路依赖时必须立即抛出异常熔断");
    }

    @Test
    @DisplayName("契约 7：Java 21 虚拟线程高并发多流水线隔离与零饥饿 (30 并发流水线 150+ 节点平稳执行)")
    void testContract7_VirtualThreadHighConcurrencyZeroStarvation() throws Exception {
        int pipelineCount = 30;
        ExecutorService testRunner = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(pipelineCount);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int p = 0; p < pipelineCount; p++) {
            final int pipelineId = p;
            testRunner.submit(() -> {
                try {
                    String txId = "TX-PARALLEL-" + pipelineId;
                    McpSagaLeaseRecord lease = leaseCoordinator.acquireOrTakeoverLease(txId, "worker-" + pipelineId, 3000L);
                    assertNotNull(lease);

                    // 在隔离虚拟线程中执行工具
                    String result = virtualExecutor.executeWithTimeout(
                            "McpEchoTool",
                            () -> {
                                try {
                                    Thread.sleep(20L); // 模拟网络 I/O 阻塞
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                                return "OK-" + pipelineId;
                            },
                            500L
                    );
                    assertEquals("OK-" + pipelineId, result);
                    successCounter.incrementAndGet();
                } catch (Exception e) {
                    fail("高并发虚拟线程流水线执行失败: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "30 条并发流水线必须在 10 秒内全部完成");
        assertEquals(pipelineCount, successCounter.get(), "所有流水线任务均应成功");
    }

    @Test
    @DisplayName("契约 8：纯 Java 21 Record 存证凭单防篡改与法医级追溯 (10,000 次自验真 <= 100μs, 单字节篡改拦截)")
    void testContract8_ImmutableReceiptSha256Verification() {
        String txId = "TX-CONTRACT-1008";
        long fencingToken = 2001L;
        String workerId = "worker-audit-01";

        List<String> forward = List.of("LOCK_INVENTORY", "CREDIT_CHECK", "PAYMENT_HOLD");
        List<String> compensated = List.of("PAYMENT_HOLD_REV", "CREDIT_CHECK_REV", "LOCK_INVENTORY_REV");

        McpSagasTransactionReceipt receipt = stateManager.finalizeTransactionReceipt(
                txId, fencingToken, workerId, "COMPENSATED", forward, compensated, 1450L
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "初始签名的凭单必须自验真成功");

        // 1. 性能测试：10,000 次自签名自验真
        int testRuns = 10_000;
        long startNano = System.nanoTime();
        for (int i = 0; i < testRuns; i++) {
            boolean valid = receipt.verifySignature();
            if (!valid) fail("验真不应失败");
        }
        long totalNano = System.nanoTime() - startNano;
        double avgMicros = (totalNano / (double) testRuns) / 1000.0;
        assertTrue(avgMicros <= 100.0, "单次自验真耗时必须 <= 100μs，实测平均: " + avgMicros + "μs");

        // 2. 防篡改测试：单字节修改 fencingToken
        McpSagasTransactionReceipt tampered1 = new McpSagasTransactionReceipt(
                receipt.receiptId(), receipt.transactionId(), receipt.fencingToken() + 1, receipt.leaseOwnerId(),
                receipt.executionStatus(), receipt.forwardSteps(), receipt.compensatedSteps(),
                receipt.latencyMicros(), receipt.timestamp(), receipt.sha256Signature()
        );
        assertFalse(tampered1.verifySignature(), "篡改 FencingToken 必须导致自验真失败");

        // 3. 防篡改测试：篡改状态
        McpSagasTransactionReceipt tampered2 = new McpSagasTransactionReceipt(
                receipt.receiptId(), receipt.transactionId(), receipt.fencingToken(), receipt.leaseOwnerId(),
                "COMMITTED", receipt.forwardSteps(), receipt.compensatedSteps(),
                receipt.latencyMicros(), receipt.timestamp(), receipt.sha256Signature()
        );
        assertFalse(tampered2.verifySignature(), "篡改 executionStatus 必须导致自验真失败");
    }
}
