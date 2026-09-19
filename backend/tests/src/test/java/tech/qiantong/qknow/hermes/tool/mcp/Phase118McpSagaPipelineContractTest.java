package tech.qiantong.qknow.hermes.tool.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.tool.mcp.governance.McpVirtualThreadCircuitBreaker;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.McpCompensatingActionRegistry;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.McpDynamicPipelineDispatcher;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.McpSagaTransactionManager;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.dto.McpSagaReceipt;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 118 综合契约测试：生产级企业 MCP 工具生态 —— 动态流水线与 Sagas 分布式事务补偿中枢
 * 覆盖 6 大核心工程契约与定理 1.1 / 定理 1.2
 */
class Phase118McpSagaPipelineContractTest {

    private McpDynamicPipelineDispatcher pipelineDispatcher;
    private McpCompensatingActionRegistry compensationRegistry;
    private McpVirtualThreadCircuitBreaker circuitBreaker;
    private McpSagaTransactionManager sagaManager;

    @BeforeEach
    void setUp() {
        pipelineDispatcher = new McpDynamicPipelineDispatcher();
        compensationRegistry = new McpCompensatingActionRegistry();
        circuitBreaker = new McpVirtualThreadCircuitBreaker();
        sagaManager = new McpSagaTransactionManager(pipelineDispatcher, compensationRegistry, circuitBreaker);
    }

    @Test
    @DisplayName("契约 1：定理 1.1 Sagas 逆序补偿最终一致性与有界步数收敛验证")
    void testSagaReverseCompensationConvergence() {
        // 注册补偿动作与追踪列表
        List<String> compensationOrder = new CopyOnWriteArrayList<>();
        compensationRegistry.registerCompensation("erp.lockQuota", (token, params) -> {
            compensationOrder.add("erp.lockQuota");
            return true;
        });
        compensationRegistry.registerCompensation("bank.freezeFunds", (token, params) -> {
            compensationOrder.add("bank.freezeFunds");
            return true;
        });

        // 构造三步流水线：Step 1 (锁定配额) -> Step 2 (冻结资金) -> Step 3 (跨境支付超时失败)
        List<McpDynamicPipelineDispatcher.PipelineNode> nodes = List.of(
                new McpDynamicPipelineDispatcher.PipelineNode("node-1", "erp.lockQuota", Map.of("quota", 100), Set.of()),
                new McpDynamicPipelineDispatcher.PipelineNode("node-2", "bank.freezeFunds", Map.of("amount", 5000), Set.of("node-1")),
                new McpDynamicPipelineDispatcher.PipelineNode("node-3", "pay.crossBorderPay", Map.of("currency", "USD"), Set.of("node-2"))
        );

        Map<String, Function<Map<String, Object>, String>> executors = Map.of(
                "erp.lockQuota", p -> "{\"success\":true,\"quota\":100}",
                "bank.freezeFunds", p -> "{\"success\":true,\"frozen\":5000}",
                "pay.crossBorderPay", p -> "{\"error\":\"DOWNSTREAM_PAYMENT_TIMEOUT\"}" // 模拟失败
        );

        McpSagaReceipt receipt = sagaManager.executePipeline("TX-TEST-001", nodes, executors, 2000);

        assertNotNull(receipt);
        assertEquals("COMPENSATED", receipt.status(), "部分步骤失败应触发完整逆序补偿");
        assertEquals(3, receipt.totalSteps());
        assertEquals(2, receipt.executedSteps(), "前两步正向执行成功");
        assertEquals(2, receipt.compensatedSteps(), "前两步逆序补偿成功");

        // 验证因果逆序执行 (LIFO)：先补偿 Step 2，再补偿 Step 1
        assertEquals(2, compensationOrder.size());
        assertEquals("bank.freezeFunds", compensationOrder.get(0), "首先补偿第二步 (冻结资金)");
        assertEquals("erp.lockQuota", compensationOrder.get(1), "随后补偿第一步 (锁定配额)");

        assertTrue(receipt.verifySignature(), "凭单密码学签名必须验真通过");
    }

    @Test
    @DisplayName("契约 2：定理 1.2 Kahn DAG 拓扑分发 O(N+M) 复杂度与调度耗时 <= 5ms")
    void testKahnDagDispatcherComplexityAndLatency() {
        // 构造包含 10 个节点与 12 条边的典型流水线 DAG
        // Layer 0: n1, n2
        // Layer 1: n3(dep n1), n4(dep n1, n2), n5(dep n2)
        // Layer 2: n6(dep n3, n4), n7(dep n4, n5)
        // Layer 3: n8(dep n6), n9(dep n6, n7)
        // Layer 4: n10(dep n8, n9)
        List<McpDynamicPipelineDispatcher.PipelineNode> nodes = List.of(
                new McpDynamicPipelineDispatcher.PipelineNode("n1", "t1", Map.of(), Set.of()),
                new McpDynamicPipelineDispatcher.PipelineNode("n2", "t2", Map.of(), Set.of()),
                new McpDynamicPipelineDispatcher.PipelineNode("n3", "t3", Map.of(), Set.of("n1")),
                new McpDynamicPipelineDispatcher.PipelineNode("n4", "t4", Map.of(), Set.of("n1", "n2")),
                new McpDynamicPipelineDispatcher.PipelineNode("n5", "t5", Map.of(), Set.of("n2")),
                new McpDynamicPipelineDispatcher.PipelineNode("n6", "t6", Map.of(), Set.of("n3", "n4")),
                new McpDynamicPipelineDispatcher.PipelineNode("n7", "t7", Map.of(), Set.of("n4", "n5")),
                new McpDynamicPipelineDispatcher.PipelineNode("n8", "t8", Map.of(), Set.of("n6")),
                new McpDynamicPipelineDispatcher.PipelineNode("n9", "t9", Map.of(), Set.of("n6", "n7")),
                new McpDynamicPipelineDispatcher.PipelineNode("n10", "t10", Map.of(), Set.of("n8", "n9"))
        );

        // 预热并测试调度
        McpDynamicPipelineDispatcher.PipelineTopology topology = pipelineDispatcher.resolveTopology(nodes);
        assertEquals(10, topology.totalNodes());
        assertEquals(13, topology.totalEdges());
        assertEquals(5, topology.executionLayers().size(), "应严格分为 5 个执行层");

        // 验证单步拓扑分发内存耗时 <= 5ms
        long maxCostNanos = 0;
        for (int i = 0; i < 50; i++) {
            long t0 = System.nanoTime();
            pipelineDispatcher.resolveTopology(nodes);
            long cost = System.nanoTime() - t0;
            if (cost > maxCostNanos) {
                maxCostNanos = cost;
            }
        }
        double maxCostMs = maxCostNanos / 1_000_000.0;
        assertTrue(maxCostMs <= 5.0, "单步拓扑解析耗时必须 <= 5ms，实测: " + maxCostMs + "ms");

        // 环路检测测试：构造有向环 nA -> nB -> nC -> nA
        List<McpDynamicPipelineDispatcher.PipelineNode> cyclicNodes = List.of(
                new McpDynamicPipelineDispatcher.PipelineNode("nA", "tA", Map.of(), Set.of("nC")),
                new McpDynamicPipelineDispatcher.PipelineNode("nB", "tB", Map.of(), Set.of("nA")),
                new McpDynamicPipelineDispatcher.PipelineNode("nC", "tC", Map.of(), Set.of("nB"))
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                pipelineDispatcher.resolveTopology(cyclicNodes));
        assertTrue(ex.getMessage().contains("循环环路"), "检测到环路必须抛出明确异常");
    }

    @Test
    @DisplayName("契约 3：防悬挂墓碑拦截与 LeaseToken 幂等性测试")
    void testAntiHangingTombstoneAndIdempotency() {
        String toolName = "inventory.deduct";
        String txId = "TX-RACE-001";
        String leaseToken = compensationRegistry.generateLeaseToken(txId, toolName);

        AtomicBoolean compExecuted = new AtomicBoolean(false);
        compensationRegistry.registerCompensation(toolName, (token, params) -> {
            compExecuted.set(true);
            return true;
        });

        // 模拟网络乱序：补偿先于正向动作到达！
        boolean compRes = compensationRegistry.executeCompensation(toolName, leaseToken, Map.of("sku", "SKU-999"));
        assertTrue(compRes, "先行到达的补偿操作应返回成功");
        assertTrue(compensationRegistry.isTombstoned(leaseToken), "该租约必须被打上墓碑标记 (Tombstone)");

        // 随后姗姗来迟的正向操作尝试执行
        boolean canForward = compensationRegistry.canExecuteForward(leaseToken);
        assertFalse(canForward, "命中墓碑标记的正向操作必须被坚决拦截，防止资产悬挂与覆盖");

        // 测试正常流程下的幂等性
        String normalToken = compensationRegistry.generateLeaseToken("TX-NORM-001", toolName);
        assertTrue(compensationRegistry.canExecuteForward(normalToken));
        compensationRegistry.markForwardExecuted(normalToken);

        // 重复正向执行拦截
        assertFalse(compensationRegistry.canExecuteForward(normalToken), "同一租约禁止重复正向执行");
    }

    @Test
    @DisplayName("契约 4：Java 21 虚拟线程高并发多流水线并行隔离与零线程饥饿")
    void testVirtualThreadHighConcurrencyIsolation() throws Exception {
        int concurrentPipelines = 20;
        CountDownLatch latch = new CountDownLatch(concurrentPipelines);
        List<McpSagaReceipt> receipts = new CopyOnWriteArrayList<>();

        // 注册快速补偿
        compensationRegistry.registerCompensation("quick.tool", (token, params) -> true);

        for (int i = 0; i < concurrentPipelines; i++) {
            final int index = i;
            pipelineDispatcher.getVirtualThreadExecutor().submit(() -> {
                try {
                    List<McpDynamicPipelineDispatcher.PipelineNode> nodes = List.of(
                            new McpDynamicPipelineDispatcher.PipelineNode("p" + index + "-1", "quick.tool", Map.of("i", index), Set.of()),
                            new McpDynamicPipelineDispatcher.PipelineNode("p" + index + "-2", "quick.tool", Map.of("i", index), Set.of("p" + index + "-1"))
                    );
                    Map<String, Function<Map<String, Object>, String>> executors = Map.of(
                            "quick.tool", p -> "{\"success\":true,\"val\":" + p.get("i") + "}"
                    );
                    McpSagaReceipt receipt = sagaManager.executePipeline("TX-CONCURRENT-" + index, nodes, executors, 1000);
                    receipts.add(receipt);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "20 条并发虚拟线程流水线必须在 10s 内全部平稳完成");
        assertEquals(concurrentPipelines, receipts.size());

        for (McpSagaReceipt receipt : receipts) {
            assertEquals("COMMITTED", receipt.status());
            assertEquals(2, receipt.executedSteps());
            assertTrue(receipt.verifySignature());
        }
    }

    @Test
    @DisplayName("契约 5：纯 Java 21 Record 事务凭单 SHA-256 自签名与单比特篡改拦截")
    void testReceiptSignatureAndTamperProof() {
        List<McpSagaReceipt.McpStepRecord> records = List.of(
                new McpSagaReceipt.McpStepRecord("s1", "tool.a", "EXECUTE", true, "L1", 10, null),
                new McpSagaReceipt.McpStepRecord("s2", "tool.b", "EXECUTE", true, "L2", 15, null)
        );

        McpSagaReceipt receipt = McpSagaReceipt.create(
                "RCP-SEC-001",
                "TX-SEC-001",
                "COMMITTED",
                2, 2, 0,
                records,
                25,
                Instant.now()
        );

        assertNotNull(receipt.signature());
        assertTrue(receipt.verifySignature(), "原始凭单签名验证必须为 true");

        // 模拟篡改 1：篡改状态
        McpSagaReceipt tamperedStatus = new McpSagaReceipt(
                receipt.receiptId(),
                receipt.transactionId(),
                "COMPENSATED", // 恶意篡改状态
                receipt.totalSteps(),
                receipt.executedSteps(),
                receipt.compensatedSteps(),
                receipt.stepRecords(),
                receipt.executionTimeMs(),
                receipt.timestamp(),
                receipt.signature()
        );
        assertFalse(tamperedStatus.verifySignature(), "篡改状态后验真必须失败");

        // 模拟篡改 2：篡改执行步数
        McpSagaReceipt tamperedSteps = new McpSagaReceipt(
                receipt.receiptId(),
                receipt.transactionId(),
                receipt.status(),
                receipt.totalSteps(),
                999, // 恶意篡改执行步数
                receipt.compensatedSteps(),
                receipt.stepRecords(),
                receipt.executionTimeMs(),
                receipt.timestamp(),
                receipt.signature()
        );
        assertFalse(tamperedSteps.verifySignature(), "篡改执行步数后验真必须失败");

        // 模拟篡改 3：伪造签名
        McpSagaReceipt tamperedSig = new McpSagaReceipt(
                receipt.receiptId(),
                receipt.transactionId(),
                receipt.status(),
                receipt.totalSteps(),
                receipt.executedSteps(),
                receipt.compensatedSteps(),
                receipt.stepRecords(),
                receipt.executionTimeMs(),
                receipt.timestamp(),
                "deadbeef12345678" // 伪造签名
        );
        assertFalse(tamperedSig.verifySignature(), "伪造签名验真必须失败");
    }

    @Test
    @DisplayName("契约 6：端到端跨系统业务场景全流程验证 (ERP 锁定 -> 支付超时 -> 逆序释放)")
    void testEndToEndCrossBorderScenarioWithRollback() {
        AtomicInteger erpLockCount = new AtomicInteger(0);
        AtomicInteger erpReleaseCount = new AtomicInteger(0);

        // 注册 ERP 补偿动作
        compensationRegistry.registerCompensation("erp.procurementQuota", (token, params) -> {
            erpReleaseCount.incrementAndGet();
            return true;
        });

        // 编排节点：ERP 采购配额锁定 -> 跨境海关申报 -> 境外资金清算 (触发熔断超时)
        List<McpDynamicPipelineDispatcher.PipelineNode> nodes = List.of(
                new McpDynamicPipelineDispatcher.PipelineNode("n-erp", "erp.procurementQuota", Map.of("batchId", "B2026"), Set.of()),
                new McpDynamicPipelineDispatcher.PipelineNode("n-customs", "customs.declare", Map.of("goods", "medical"), Set.of("n-erp")),
                new McpDynamicPipelineDispatcher.PipelineNode("n-pay", "swift.settlement", Map.of("amount", 200000), Set.of("n-customs"))
        );

        // 注册海关无损查询补偿 (空操作)
        compensationRegistry.registerCompensation("customs.declare", (token, params) -> true);

        Map<String, Function<Map<String, Object>, String>> executors = Map.of(
                "erp.procurementQuota", p -> {
                    erpLockCount.incrementAndGet();
                    return "{\"success\":true,\"locked\":true}";
                },
                "customs.declare", p -> "{\"success\":true,\"declarationNo\":\"DEC-888\"}",
                "swift.settlement", p -> {
                    // 模拟外部网络中断导致超时
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {}
                    return "{\"error\":\"SWIFT_NETWORK_TIMEOUT\"}";
                }
        );

        McpSagaReceipt receipt = sagaManager.executePipeline("TX-PROCUREMENT-888", nodes, executors, 200);

        assertEquals("COMPENSATED", receipt.status());
        assertEquals(1, erpLockCount.get(), "ERP 配额锁定执行了 1 次");
        assertEquals(1, erpReleaseCount.get(), "由于后续结算失败，ERP 配额逆序释放执行了 1 次");
        assertTrue(receipt.verifySignature());
    }
}
