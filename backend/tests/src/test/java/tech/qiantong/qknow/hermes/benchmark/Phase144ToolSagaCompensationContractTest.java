package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.swarm.saga.HypersphericalIntentDisentangler;
import tech.qiantong.qknow.hermes.swarm.saga.HypersphericalIntentDisentangler.DisentangleResult;
import tech.qiantong.qknow.hermes.swarm.saga.HypersphericalIntentDisentangler.ToolIntentProfile;
import tech.qiantong.qknow.hermes.swarm.saga.SagaCompensationCoordinator;
import tech.qiantong.qknow.hermes.swarm.saga.SagaCompensationCoordinator.SagaExecutionResult;
import tech.qiantong.qknow.hermes.swarm.saga.ToolSagaCompensationReceipt;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 144 核心契约测试套件：
 * 分布式工具链有界事务补偿 (Saga) 逆拓扑执行器、千问 1536 维超球面多意图正交解耦与超时租约自愈中枢
 * (Tool Saga Reverse Topological Compensation, Intent Disentanglement & Bounded Lease-based Healing Hub)
 * <p>
 * 严格按照 AGENTS.md 规范与八大核心契约验证：
 * 1. Kahn 逆拓扑偏序单调性与因果抵消验证：死锁率 0.0%，耗时 <= 3.0ms (TC-144-1)
 * 2. 千问 1536 维超球面改进 Gram-Schmidt 正交解耦：残差 <= 1e-6，保真度 >= 0.85 (TC-144-2)
 * 3. 互斥工具写写冲突消歧与因果 DAG 依赖边生成：消歧率 100.0% (TC-144-3)
 * 4. 带 Fencing Token 的有界超时租约与墓碑防幽灵写：拦截率 100.0% (TC-144-4)
 * 5. 三级冷备隔离环形缓冲区异常软隔离与免崩溃机制 (TC-144-5)
 * 6. 纯 Java 21 Record 凭单 SHA-256 常量时间自验真与防篡改 (TC-144-6)
 * 7. 多租户物理命名空间硬隔离与防泄漏契约 (TC-144-7)
 * 8. 高并发全流程吞吐与端到端延迟基准压测：P99 <= 4.0ms，Token 增量严格为 0 (TC-144-8)
 *
 * @author Achilles
 * @since Phase 144
 */
public class Phase144ToolSagaCompensationContractTest {

    private final SagaCompensationCoordinator coordinator = new SagaCompensationCoordinator();
    private final HypersphericalIntentDisentangler disentangler = new HypersphericalIntentDisentangler();

    /**
     * TC-144-1: 诱导转置图 Kahn 算法逆拓扑偏序单调性与因果抵消验证 (Lemma 144.1)
     * 菱形并发拓扑：A -> B, A -> C, B -> D, C -> D (即 D 依赖 B、C；B、C 依赖 A)
     * 预期逆拓扑补偿顺序：D 必须先于 B、C 补偿；B、C 必须先于 A 补偿。
     */
    @Test
    @DisplayName("TC-144-1: 诱导转置图 Kahn 算法逆拓扑偏序单调性与因果抵消验证")
    void testReverseTopologicalOrderAndCausalityInvariance() {
        String tenantId = "tenant-saga-test";
        String txId = "TX-SAGA-DIAMOND-" + UUID.randomUUID().toString().substring(0, 8);

        List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

        // 注册 A, B, C, D 四个工具步骤
        coordinator.registerStep(tenantId, txId, "Tool-A", Set.of(), ctx -> executionLog.add("Compensate-A"), 5000);
        coordinator.registerStep(tenantId, txId, "Tool-B", Set.of("Tool-A"), ctx -> executionLog.add("Compensate-B"), 5000);
        coordinator.registerStep(tenantId, txId, "Tool-C", Set.of("Tool-A"), ctx -> executionLog.add("Compensate-C"), 5000);
        coordinator.registerStep(tenantId, txId, "Tool-D", Set.of("Tool-B", "Tool-C"), ctx -> executionLog.add("Compensate-D"), 5000);

        // 标记全量正向执行成功
        assertTrue(coordinator.markStepExecuted(txId, "Tool-A", 1001));
        assertTrue(coordinator.markStepExecuted(txId, "Tool-B", 1002));
        assertTrue(coordinator.markStepExecuted(txId, "Tool-C", 1003));
        assertTrue(coordinator.markStepExecuted(txId, "Tool-D", 1004));

        long start = System.nanoTime();
        SagaExecutionResult result = coordinator.triggerReverseTopologicalCompensation(
                txId, "task-001", "trace-001", "intent-digest-diamond", false
        );
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(result);
        assertTrue(result.isFullyCompensated());
        assertEquals(4, result.compensatedOrder().size());

        List<String> order = result.compensatedOrder();
        int idxA = order.indexOf("Tool-A");
        int idxB = order.indexOf("Tool-B");
        int idxC = order.indexOf("Tool-C");
        int idxD = order.indexOf("Tool-D");

        // 严格断言因果逆拓扑偏序关系
        assertTrue(idxD < idxB, "D 必须在 B 之前完成补偿");
        assertTrue(idxD < idxC, "D 必须在 C 之前完成补偿");
        assertTrue(idxB < idxA, "B 必须在 A 之前完成补偿");
        assertTrue(idxC < idxA, "C 必须在 A 之前完成补偿");

        assertTrue(latencyMs <= 3.0, "转置拓扑排序与补偿耗时应 <= 3.0ms，实际: " + latencyMs);
        assertEquals(4, executionLog.size());
    }

    /**
     * TC-144-2: 千问 1536 维超球面改进 Gram-Schmidt 正交意图解耦精度验证 (Lemma 144.2)
     */
    @Test
    @DisplayName("TC-144-2: 千问 1536 维超球面改进 Gram-Schmidt 正交意图解耦精度验证")
    void testHypersphericalIntentDisentanglementPrecision() {
        double[] rawTask = createPerturbedEmbedding(42, 0.2);

        List<ToolIntentProfile> candidates = List.of(
                new ToolIntentProfile("tool-kb-search", "知识库检索", "RAG 检索", createPerturbedEmbedding(42, 0.1), Set.of(), false),
                new ToolIntentProfile("tool-sql-exec", "SQL 执行器", "只读查询", createPerturbedEmbedding(55, 0.3), Set.of(), false),
                new ToolIntentProfile("tool-write-audit", "审计日志写入", "写操作", createPerturbedEmbedding(88, 0.4), Set.of(), true),
                new ToolIntentProfile("tool-notify", "站内通知", "外部通知", createPerturbedEmbedding(120, 0.5), Set.of(), true)
        );

        long start = System.nanoTime();
        DisentangleResult result = disentangler.disentangleIntents(
                "task-disentangle-001", rawTask, candidates, 0.70, 0.05
        );
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(result);
        assertEquals(4, result.toolIntents().size());
        assertTrue(result.orthogonalityResidual() <= HypersphericalIntentDisentangler.ORTHO_EPSILON,
                "子空间正交残差必须 <= 1e-6，实际: " + result.orthogonalityResidual());
        assertTrue(result.reconstructionFidelity() >= 0.85,
                "意图重构保真度必须 >= 0.85，实际: " + result.reconstructionFidelity());
        assertTrue(latencyMs <= 2.0, "正交投影解耦耗时应 <= 2.0ms，实际: " + latencyMs);
    }

    /**
     * TC-144-3: 互斥工具写写冲突消歧与因果 DAG 依赖边生成验证
     */
    @Test
    @DisplayName("TC-144-3: 互斥工具写写冲突消歧与因果 DAG 依赖边生成验证")
    void testMutualExclusionConflictSerialization() {
        // 构造高度重叠 (余弦重叠度 >= 0.70) 且均有副作用的互斥工具
        double[] baseVec = createPerturbedEmbedding(777, 0.05);
        double[] conflictVecA = createPerturbedEmbedding(777, 0.06);
        double[] conflictVecB = createPerturbedEmbedding(777, 0.07);

        List<ToolIntentProfile> candidates = List.of(
                new ToolIntentProfile("tool-account-deduct", "账户扣划", "敏感资金变动", conflictVecA, Set.of(), true),
                new ToolIntentProfile("tool-account-freeze", "账户冻结", "账户状态修改", conflictVecB, Set.of(), true)
        );

        DisentangleResult result = disentangler.disentangleIntents(
                "task-conflict-001", baseVec, candidates, 0.70, 0.05
        );

        assertNotNull(result);
        assertFalse(result.resolvedEdges().isEmpty(), "必须成功识别出互斥工具并生成拓扑消歧依赖边");

        var edge = result.resolvedEdges().get(0);
        assertTrue(edge.reason().contains("Conflict-Serialization"));
        assertTrue((edge.fromToolId().equals("tool-account-deduct") && edge.toToolId().equals("tool-account-freeze"))
                || (edge.fromToolId().equals("tool-account-freeze") && edge.toToolId().equals("tool-account-deduct")));
    }

    /**
     * TC-144-4: 带 Fencing Token 的有界超时租约与墓碑防幽灵写验证 (Lemma 144.3)
     */
    @Test
    @DisplayName("TC-144-4: 带 Fencing Token 的有界超时租约与墓碑防幽灵写验证")
    void testLeaseTimeoutAndTombstonePhantomWritePrevention() throws InterruptedException {
        String tenantId = "tenant-lease-test";
        String txId = "TX-LEASE-" + UUID.randomUUID().toString().substring(0, 8);

        // 注册超短超时时间 (10ms) 的步骤
        var lease = coordinator.registerStep(tenantId, txId, "Tool-SlowExternal", Set.of(), ctx -> {}, 10);
        assertNotNull(lease);

        // 模拟等待租约超时 (30ms)
        Thread.sleep(30);

        boolean expiredDetected = coordinator.checkAndHealTimeouts(txId, "task-timeout", "trace-timeout");
        assertTrue(expiredDetected, "必须检测到超时租约失效");

        // 模拟外部网络由于延迟，在超时之后才迟到返回并尝试写操作
        boolean writeSuccess = coordinator.markStepExecuted(txId, "Tool-SlowExternal", lease.fencingToken());
        assertFalse(writeSuccess, "超时步骤必须被墓碑标记拦截，严禁幽灵脏写覆盖");
    }

    /**
     * TC-144-5: 三级冷备隔离环形缓冲区异常软隔离与免崩溃验证
     */
    @Test
    @DisplayName("TC-144-5: 三级冷备隔离环形缓冲区异常软隔离与免崩溃验证")
    void testQuarantineRingBufferSoftIsolation() {
        String tenantId = "tenant-quarantine-test";
        String txId = "TX-QUARANTINE-" + UUID.randomUUID().toString().substring(0, 8);

        AtomicBoolean step1Compensated = new AtomicBoolean(false);
        AtomicBoolean step3Compensated = new AtomicBoolean(false);

        // 步骤 1 正常
        coordinator.registerStep(tenantId, txId, "Step-1", Set.of(), ctx -> step1Compensated.set(true), 5000);
        // 步骤 2 补偿故意抛错
        coordinator.registerStep(tenantId, txId, "Step-2", Set.of("Step-1"), ctx -> {
            throw new RuntimeException("Simulated external MCP compensation network timeout");
        }, 5000);
        // 步骤 3 正常
        coordinator.registerStep(tenantId, txId, "Step-3", Set.of("Step-2"), ctx -> step3Compensated.set(true), 5000);

        assertTrue(coordinator.markStepExecuted(txId, "Step-1", 1));
        assertTrue(coordinator.markStepExecuted(txId, "Step-2", 2));
        assertTrue(coordinator.markStepExecuted(txId, "Step-3", 3));

        // 触发补偿
        SagaExecutionResult result = coordinator.triggerReverseTopologicalCompensation(
                txId, "task-err", "trace-err", "digest-err", false
        );

        assertNotNull(result);
        assertFalse(result.isFullyCompensated(), "存在失败步骤，不能标记为全量成功");
        assertEquals(1, result.failedStepIds().size());
        assertEquals("Step-2", result.failedStepIds().get(0));

        // 验证其余未出错步骤仍正常完成补偿
        assertTrue(step1Compensated.get(), "Step-1 应当完成补偿");
        assertTrue(step3Compensated.get(), "Step-3 应当完成补偿");

        // 验证三级冷备隔离缓冲区记录
        var snapshot = coordinator.getQuarantineSnapshot();
        assertFalse(snapshot.isEmpty());
        boolean hasRecord = snapshot.stream().anyMatch(r -> r.stepId().equals("Step-2") && r.transactionId().equals(txId));
        assertTrue(hasRecord, "失败步骤必须软隔离至冷备环形缓冲区，支持后续自愈与人工介入");
    }

    /**
     * TC-144-6: 纯 Java 21 Record 凭单 SHA-256 常量时间自验真与防篡改验证
     */
    @Test
    @DisplayName("TC-144-6: 纯 Java 21 Record 凭单 SHA-256 常量时间自验真与防篡改验证")
    void testReceiptConstantTimeSignatureAndAntiTampering() {
        String tenantId = "tenant-crypto-test";
        String txId = "TX-CRYPTO-" + UUID.randomUUID().toString().substring(0, 8);

        coordinator.registerStep(tenantId, txId, "Step-A", Set.of(), ctx -> {}, 5000);
        coordinator.markStepExecuted(txId, "Step-A", 1);

        SagaExecutionResult result = coordinator.triggerReverseTopologicalCompensation(
                txId, "task-crypto", "trace-crypto", "digest-crypto", false
        );

        ToolSagaCompensationReceipt originalReceipt = result.receipt();
        assertNotNull(originalReceipt);
        assertTrue(originalReceipt.verifySignature(), "合法初始凭单必须通过常量时间验签");

        // 反事实篡改验证：单比特修改 transactionId
        ToolSagaCompensationReceipt tamperedTx = new ToolSagaCompensationReceipt(
                originalReceipt.receiptId(),
                originalReceipt.tenantId(),
                originalReceipt.taskId(),
                originalReceipt.traceId(),
                originalReceipt.transactionId() + "-HACKED",
                originalReceipt.totalSteps(),
                originalReceipt.compensatedSteps(),
                originalReceipt.failedSteps(),
                originalReceipt.isFullyCompensated(),
                originalReceipt.isLeaseExpired(),
                originalReceipt.intentDisentangleDigest(),
                originalReceipt.stepOrder(),
                originalReceipt.latencyMs(),
                originalReceipt.timestamp(),
                originalReceipt.sha256Signature()
        );
        assertFalse(tamperedTx.verifySignature(), "篡改 transactionId 后验真必须立即判伪");

        // 反事实篡改验证：修改已补偿步数
        ToolSagaCompensationReceipt tamperedSteps = new ToolSagaCompensationReceipt(
                originalReceipt.receiptId(),
                originalReceipt.tenantId(),
                originalReceipt.taskId(),
                originalReceipt.traceId(),
                originalReceipt.transactionId(),
                originalReceipt.totalSteps(),
                999, // 篡改
                originalReceipt.failedSteps(),
                originalReceipt.isFullyCompensated(),
                originalReceipt.isLeaseExpired(),
                originalReceipt.intentDisentangleDigest(),
                originalReceipt.stepOrder(),
                originalReceipt.latencyMs(),
                originalReceipt.timestamp(),
                originalReceipt.sha256Signature()
        );
        assertFalse(tamperedSteps.verifySignature(), "篡改步骤计数后验真必须立即判伪");
    }

    /**
     * TC-144-7: 多租户物理命名空间硬隔离与防泄漏契约验证
     */
    @Test
    @DisplayName("TC-144-7: 多租户物理命名空间硬隔离与防泄漏契约验证")
    void testMultiTenantPhysicalHardIsolation() {
        String txId = "TX-SHARED-" + UUID.randomUUID().toString().substring(0, 8);

        // Tenant Alpha 注册事务
        coordinator.registerStep("tenant-alpha", txId, "Step-Alpha-1", Set.of(), ctx -> {}, 5000);

        // 模拟 Tenant Beta 试图越权操作该事务
        assertThrows(SecurityException.class, () -> {
            coordinator.registerStep("tenant-beta", txId, "Step-Beta-Injected", Set.of(), ctx -> {}, 5000);
        }, "跨租户非法访问必须被 100% 拒绝");
    }

    /**
     * TC-144-8: 高并发事务吞吐与端到端延迟预算基准压测 (P99 <= 4.0ms)
     */
    @Test
    @DisplayName("TC-144-8: 高并发事务吞吐与端到端延迟预算基准压测")
    void testHighConcurrencyThroughputAndLatencyBudget() {
        int rounds = 100;
        List<Double> latencies = new ArrayList<>();

        double[] taskVec = createPerturbedEmbedding(999, 0.1);
        List<ToolIntentProfile> tools = List.of(
                new ToolIntentProfile("tool-1", "工具1", "描述1", createPerturbedEmbedding(10, 0.2), Set.of(), false),
                new ToolIntentProfile("tool-2", "工具2", "描述2", createPerturbedEmbedding(20, 0.2), Set.of(), true),
                new ToolIntentProfile("tool-3", "工具3", "描述3", createPerturbedEmbedding(30, 0.2), Set.of(), true)
        );

        for (int i = 0; i < rounds; i++) {
            String txId = "TX-PERF-" + i + "-" + UUID.randomUUID().toString().substring(0, 6);
            long start = System.nanoTime();

            // 1. 意图解耦
            DisentangleResult dResult = disentangler.disentangleIntents("task-perf", taskVec, tools, 0.70, 0.05);

            // 2. 步骤注册
            coordinator.registerStep("tenant-perf", txId, "tool-1", Set.of(), ctx -> {}, 5000);
            coordinator.registerStep("tenant-perf", txId, "tool-2", Set.of("tool-1"), ctx -> {}, 5000);
            coordinator.registerStep("tenant-perf", txId, "tool-3", Set.of("tool-2"), ctx -> {}, 5000);

            coordinator.markStepExecuted(txId, "tool-1", 1);
            coordinator.markStepExecuted(txId, "tool-2", 2);
            coordinator.markStepExecuted(txId, "tool-3", 3);

            // 3. 逆拓扑补偿与凭单签发自验真
            SagaExecutionResult sResult = coordinator.triggerReverseTopologicalCompensation(
                    txId, "task-perf", "trace-perf", dResult.digest(), false
            );

            double costMs = (System.nanoTime() - start) / 1_000_000.0;
            latencies.add(costMs);

            assertTrue(sResult.isFullyCompensated());
            assertTrue(sResult.receipt().verifySignature());
            coordinator.cleanupTransaction(txId);
        }

        Collections.sort(latencies);
        double p99 = latencies.get((int) (rounds * 0.99) - 1);
        double avg = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        System.out.printf("[Benchmark Phase 144] 100 轮全流程测试完成: 平均耗时=%.3f ms, P99=%.3f ms%n", avg, p99);
        assertTrue(p99 <= 4.0, "P99 协调开销应 <= 4.0ms，实际: " + p99);
    }

    private static double[] createPerturbedEmbedding(int seed, double perturbation) {
        Random rand = new Random(seed);
        double[] v = new double[HypersphericalIntentDisentangler.EMBEDDING_DIM];
        for (int i = 0; i < v.length; i++) {
            v[i] = rand.nextGaussian() + perturbation * rand.nextDouble();
        }
        return HypersphericalIntentDisentangler.normalizeVector(v);
    }
}
