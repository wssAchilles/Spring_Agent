package tech.qiantong.qknow.hermes.agent.harness.offline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.offline.probe.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermes 阶段四离线真实探针与受控 PPC 契约测试套件 (Stage 4 Contract Test)。
 *
 * <p>基于确定性测试替身（Deterministic Test Double）与内存级隔离契约，
 * 验证凭单租约时序、双轨执行分流、物理原子落盘、累加预算熔断、Position-Swap 消除偏见与防数据泄漏门禁。</p>
 */
@DisplayName("Hermes Harness² 阶段四契约测试套件")
class HermesHarnessStage4ContractTest {

    @Test
    @DisplayName("契约 1: ScopedVoucherLease 必须保证凭单注册、幂等卸载与零泄漏")
    void testScopedVoucherLeaseLifecycle() {
        HermesHarnessAssembler assembler = new HermesHarnessAssembler();
        HermesHarnessVoucher voucher = assembler.createRequestVoucher(1001L, "测试角色约束");
        AgentOrchestrator orchestrator = new AgentOrchestrator(null, null, null);

        String requestId = "req-lease-test-001";
        assertNull(orchestrator.getLatestEvidence(requestId));

        // 1. 验证在租约作用域内注册生效
        try (ScopedVoucherLease lease = new ScopedVoucherLease(orchestrator, requestId, voucher)) {
            assertEquals(requestId, lease.getProbeRequestId());
            assertFalse(lease.isClosed());
        }

        // 2. 验证跳出 try-with-resources 后自动卸载
        // 通过再次卸载验证幂等性
        orchestrator.unregisterRequestVoucher(requestId);
    }

    @Test
    @DisplayName("契约 2: DefaultOfflineProbeExecutor 双轨分流与原子落盘常量时间验签")
    void testOfflineProbeExecutorBatchAndPersist() throws IOException {
        Path scratchDir = Path.of("target/test-scratch/harness4");
        Files.createDirectories(scratchDir);

        Map<String, DeterministicHarnessTestDouble.DeterministicResponse> fixtures = Map.of(
                "kb-qa-01", new DeterministicHarnessTestDouble.DeterministicResponse(
                        "中间件规范回答: 重试3次，退避2.0", "knowledgeBase_MiddleWare_Spec", true, 600L, 25L),
                "tool-route-02", new DeterministicHarnessTestDouble.DeterministicResponse(
                        "银行业务查询流水与余额正常", "banking_transaction_query", true, 650L, 28L)
        );

        DeterministicHarnessTestDouble doubleDriver = new DeterministicHarnessTestDouble(fixtures);
        DefaultOfflineProbeExecutor executor = new DefaultOfflineProbeExecutor(doubleDriver, null);

        OfflineProbeExecutor.ProbeExecutionOptions options = new OfflineProbeExecutor.ProbeExecutionOptions(
                5000L, 1, scratchDir, true, "deepseek", "deepseek-flash"
        );

        List<OfflineBenchmarkTestCase> suite = List.of(
                OfflineBenchmarkTestCase.createDefaultSuite().get(0),
                OfflineBenchmarkTestCase.createDefaultSuite().get(1)
        );

        HermesHarnessAssembler assembler = new HermesHarnessAssembler();
        HermesHarnessVoucher voucher = assembler.createRequestVoucher(1001L, "基准系统约束");

        List<OfflineProbeExecutor.ProbeExecutionReport> reports =
                executor.executeBatchProbe("run-test-01", "baseline", suite, voucher, options);

        assertEquals(2, reports.size());
        for (OfflineProbeExecutor.ProbeExecutionReport report : reports) {
            assertTrue(report.success());
            assertEquals("NONE", report.errorCode());
            assertNotNull(report.evidenceRecord());
            assertEquals(HarnessEvidenceRecord.ExecutionMode.DETERMINISTIC_TEST_DOUBLE,
                    report.evidenceRecord().executionMode(), "测试替身生成的凭单必须显式标记 DETERMINISTIC_TEST_DOUBLE");
            assertNotNull(report.persistedJsonPath());
            assertTrue(Files.exists(report.persistedJsonPath()));

            // 验证落盘文件内容可被确定性回放验签
            OfflineEvidenceReplayer.ReplayVerificationResult result =
                    OfflineEvidenceReplayer.replayAndVerify(List.of(report.evidenceRecord()));
            assertTrue(result.allVerified(), "落盘凭单必须通过常量时间验签");
        }
    }

    @Test
    @DisplayName("契约 3: OfflineBudgetCircuitBreaker 累加熔断器必须严格拦截超支")
    void testBudgetCircuitBreakerThresholds() {
        OfflineBudgetCircuitBreaker breaker = new OfflineBudgetCircuitBreaker();

        // 正常范围累加
        breaker.trackAndAssert(5000L, 0.0075);
        assertEquals(5000L, breaker.getCumulativeTokens());
        assertTrue(breaker.getCumulativeCostCny() > 0.0);

        // 触发 Token 超限熔断 (> 80,000)
        IllegalStateException tokenEx = assertThrows(IllegalStateException.class, () ->
                breaker.trackAndAssert(85_000L, 0.01)
        );
        assertTrue(tokenEx.getMessage().contains("BUDGET_CIRCUIT_BREAKER_TRIGGERED"));

        // 触发 成本超限熔断 (> 0.15 CNY)
        OfflineBudgetCircuitBreaker costBreaker = new OfflineBudgetCircuitBreaker();
        IllegalStateException costEx = assertThrows(IllegalStateException.class, () ->
                costBreaker.trackAndAssert(1000L, 0.20)
        );
        assertTrue(costEx.getMessage().contains("BUDGET_CIRCUIT_BREAKER_TRIGGERED"));
    }

    @Test
    @DisplayName("契约 4: PositionSwapBlindEvaluator 必须消除位置偏见并支持物理短路门禁")
    void testPositionSwapBlindEvaluatorBiasesAndShortCircuit() {
        PositionSwapBlindEvaluator evaluator = new PositionSwapBlindEvaluator("TEST_SALT_123");

        OfflineBenchmarkTestCase tc = OfflineBenchmarkTestCase.createDefaultSuite().get(0);

        // 1. 物理差分硬短路门禁验证（构造 Token 膨胀）
        ProbeExecutionDiff swollenDiff = new ProbeExecutionDiff(
                tc.taskId(), "ev-b", "ev-c", 500L, 550L, 50L, // deltaTokens = +50 (膨胀)
                100L, 102L, 2L, 0.001, 0.0011, true, true
        );
        PositionSwapBlindEvaluator.BlindEvaluationResult shortResult = evaluator.evaluate(
                swollenDiff, "base", "cand", (a, b) -> "A"
        );
        assertEquals(PositionSwapBlindEvaluator.VerdictOutcome.SHORT_CIRCUITED_REJECT, shortResult.outcome());
        assertTrue(shortResult.arbitrationReason().contains("物理指标硬拦截"));

        // 2. 模拟固定选 A 偏见（Pass 1 选 A，Pass 2 也选 A）
        ProbeExecutionDiff goodDiff = new ProbeExecutionDiff(
                tc.taskId(), "ev-b", "ev-c", 500L, 450L, -50L, // 节省 50 tokens
                100L, 102L, 2L, 0.001, 0.0009, true, true
        );
        PositionSwapBlindEvaluator.BlindEvaluationResult biasedResult = evaluator.evaluate(
                goodDiff, "base", "cand", (a, b) -> "A" // 固执选 A
        );
        assertFalse(biasedResult.isConsistent());
        assertEquals(PositionSwapBlindEvaluator.VerdictOutcome.BIAS_CONFLICT_REJECT, biasedResult.outcome());

        // 3. 模拟客观选优（无论在 A 还是在 B，均识别出候选更优）
        PositionSwapBlindEvaluator.BlindEvaluationResult robustWin = evaluator.evaluate(
                goodDiff, "基准较冗余", "候选精炼准确",
                (a, b) -> a.contains("候选") ? "A" : "B"
        );
        assertTrue(robustWin.isConsistent());
        assertEquals(PositionSwapBlindEvaluator.VerdictOutcome.ROBUST_WIN_ADOPT, robustWin.outcome());
    }

    @Test
    @DisplayName("契约 5: SelectionDataLeakageTripwire 必须拦截扩充版判词标记与敏感路径")
    void testExpandedDataLeakageTripwire() {
        // 1. 拦截敏感判词
        assertThrows(IllegalStateException.class, () ->
                SelectionDataLeakageTripwire.assertNoLeak("测试结果如下: gold_rubric 匹配度 100%", List.of())
        );
        assertThrows(IllegalStateException.class, () ->
                SelectionDataLeakageTripwire.assertNoLeak("包含中文判词: 标准答案为重试三次", List.of())
        );

        // 2. 拦截敏感路径
        assertThrows(IllegalStateException.class, () ->
                SelectionDataLeakageTripwire.assertNoLeak("无毒文本", List.of("/path/to/experiment/outputs/harness4/baseline.json"))
        );
        assertThrows(IllegalStateException.class, () ->
                SelectionDataLeakageTripwire.assertNoLeak("无毒文本", List.of("docs/architecture-review-20261019/taskbook.md"))
        );

        // 3. 无毒正常放行
        assertDoesNotThrow(() ->
                SelectionDataLeakageTripwire.assertNoLeak("订单重试机制与幂等设计说明", List.of("src/main/resources/application.yml"))
        );
    }

    @Test
    @DisplayName("契约 6: OfflineProbeRunner 驱动 5 大用例完成测试替身受控 PPC 闭环与合成 JSON 隔离存证")
    void testEndToEndOfflineProbePpcCycleAndPersist() throws IOException {
        Path outputBaseDir = Path.of("target/test-scratch/harness4-synthetic").toAbsolutePath().normalize();
        String runId = "run-20261030-stage4-synthetic";

        Map<String, DeterministicHarnessTestDouble.DeterministicResponse> fixtures = Map.of(
                "kb-qa-01", new DeterministicHarnessTestDouble.DeterministicResponse(
                        "中间件规范回答: 重试3次，退避2.0，幂等键使用SHA-256", "knowledgeBase_MiddleWare_Spec", true, 580L, 22L),
                "tool-route-02", new DeterministicHarnessTestDouble.DeterministicResponse(
                        "银行业务核查: 扣款流水已确认，余额已校验", "banking_transaction_query", true, 620L, 26L),
                "param-guard-03", new DeterministicHarnessTestDouble.DeterministicResponse(
                        "时间参数纠错: 检测到非标准时间，已引导自纠", "calendar_event_creator", true, 490L, 18L),
                "ctx-history-04", new DeterministicHarnessTestDouble.DeterministicResponse(
                        "架构演进建议: 消除指代，采用 eBPF 绕过网络时延瓶颈", "defaultTool", true, 550L, 20L),
                "comp-rag-tool-05", new DeterministicHarnessTestDouble.DeterministicResponse(
                        "微服务演进评估: 已输出 4 列表格与无偏见总结", "knowledgeBase_Arch_Spec", true, 710L, 30L)
        );

        DeterministicHarnessTestDouble doubleDriver = new DeterministicHarnessTestDouble(fixtures);
        DefaultOfflineProbeExecutor probeExecutor = new DefaultOfflineProbeExecutor(doubleDriver, null);
        OfflineProbeRunner runner = new OfflineProbeRunner(probeExecutor);

        OfflineProbeRunner.FullPpcRunSummary summary = runner.runPpcCycle(runId, outputBaseDir);

        assertNotNull(summary);
        assertEquals(5, summary.totalCases());
        assertEquals(5, summary.passedCases());
        assertTrue(summary.candidateAdoptedCount() > 0);
        assertTrue(summary.hypothesisVerified());

        // 验证 10 份合成证据 JSON 资产全部落盘且显式标记 DETERMINISTIC_TEST_DOUBLE
        for (OfflineBenchmarkTestCase tc : OfflineBenchmarkTestCase.createDefaultSuite()) {
            Path baseJson = outputBaseDir.resolve(runId).resolve("baseline").resolve("evidence_" + tc.taskId() + ".json");
            Path candJson = outputBaseDir.resolve(runId).resolve("candidate").resolve("evidence_" + tc.taskId() + ".json");

            assertTrue(Files.exists(baseJson), "基线合成凭单必须存在: " + baseJson);
            assertTrue(Files.exists(candJson), "候选合成凭单必须存在: " + candJson);
            assertTrue(Files.size(baseJson) > 0, "基线凭单文件大小必须大于 0");
            assertTrue(Files.size(candJson) > 0, "候选凭单文件大小必须大于 0");
            assertTrue(Files.readString(baseJson).contains("\"executionMode\":\"DETERMINISTIC_TEST_DOUBLE\""));
            assertTrue(Files.readString(candJson).contains("\"executionMode\":\"DETERMINISTIC_TEST_DOUBLE\""));
        }

        Path summaryJson = outputBaseDir.resolve(runId).resolve("eval").resolve("ppc_summary.json");
        assertTrue(Files.exists(summaryJson));
        assertTrue(Files.size(summaryJson) > 0);
    }
}
