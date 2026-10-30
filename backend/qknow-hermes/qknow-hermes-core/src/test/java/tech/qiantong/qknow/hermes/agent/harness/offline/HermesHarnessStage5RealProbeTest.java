package tech.qiantong.qknow.hermes.agent.harness.offline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.offline.probe.DefaultOfflineProbeExecutor;
import tech.qiantong.qknow.hermes.agent.harness.offline.probe.DeterministicHarnessTestDouble;
import tech.qiantong.qknow.hermes.agent.harness.offline.probe.OfflineProbeExecutor;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.config.PlanSolveConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermes 阶段五真实生产轨道探针与受控 PPC 验证测试套件 (Stage 5 Real Probe Test)。
 *
 * <p>遵循《AGENTS.md》科研门禁与阶段五冻结任务书：
 * 1. 真实生产轨道：使用 ProductionAgentOrchestratorDriver 真实驱动 DeepSeek 官方 API；
 * 2. 证据真实性：落盘凭单必须包含真实网络 RTT 耗时、真实模型输出与 executionMode=REAL_PRODUCTION（或公网异常时的 FAIL_SAFE）；
 * 3. 严格隔离：测试替身隔离在 synthetic 目录，严禁伪造真实生产实验；
 * 4. 故障自愈：异常时安全合成 FAIL_SAFE 凭单并触发客观短路拦截。</p>
 */
@DisplayName("Hermes Harness² 阶段五真实生产轨道探针测试套件")
class HermesHarnessStage5RealProbeTest {

    @Test
    @DisplayName("阶段五核心验收: ProductionAgentOrchestratorDriver 驱动真实 DeepSeek API 探针执行并落盘资产")
    void testRealProductionTrackProbeExecutionAndPersist() throws IOException {
        String apiKey = DefaultOfflineProbeExecutor.resolveEffectiveApiKey();
        assertNotNull(apiKey, "DeepSeek API Key 必须能被解析");
        assertFalse(apiKey.isBlank(), "DeepSeek API Key 不能为空白");
        assertFalse(apiKey.contains("placeholder"), "DeepSeek API Key 不能是占位符");

        // 构造具备真实 DeepSeek 客户端的 AgentOrchestrator
        ChatModelFactory modelFactory = new ChatModelFactory();
        AgentOrchestrator realOrchestrator = new AgentOrchestrator(
                modelFactory,
                null,
                null,
                new PlanSolveConfig(),
                null,
                null,
                null,
                null,
                null
        );

        DefaultOfflineProbeExecutor probeExecutor = new DefaultOfflineProbeExecutor(realOrchestrator);
        OfflineProbeRunner runner = new OfflineProbeRunner(probeExecutor);

        Path projectRoot = OfflineProbeRunner.findProjectRoot();
        Path outputBaseDir = projectRoot.resolve("experiment/outputs/harness5").toAbsolutePath().normalize();
        String runId = "run-20261030-stage5-real";

        // 执行 5 大基准用例 × 2（Baseline 与 Candidate 各 1 次），共 10 次真实调用
        OfflineProbeRunner.FullPpcRunSummary summary = runner.runPpcCycle(runId, outputBaseDir);

        assertNotNull(summary);
        assertEquals(5, summary.totalCases());
        assertEquals(5, summary.passedCases());

        List<OfflineBenchmarkTestCase> suite = OfflineBenchmarkTestCase.createDefaultSuite();
        int realProductionCount = 0;

        for (OfflineBenchmarkTestCase tc : suite) {
            Path baseJson = outputBaseDir.resolve(runId).resolve("baseline").resolve("evidence_" + tc.taskId() + ".json");
            Path candJson = outputBaseDir.resolve(runId).resolve("candidate").resolve("evidence_" + tc.taskId() + ".json");

            assertTrue(Files.exists(baseJson), "基线真实物理凭单必须存在: " + baseJson);
            assertTrue(Files.exists(candJson), "候选真实物理凭单必须存在: " + candJson);

            String baseText = Files.readString(baseJson);
            String candText = Files.readString(candJson);

            // 核心防伪断言 1: 执行模式必须是 REAL_PRODUCTION 或公网瞬时超时的 FAIL_SAFE，严禁是 DETERMINISTIC_TEST_DOUBLE
            assertFalse(baseText.contains("\"DETERMINISTIC_TEST_DOUBLE\""), "严禁包含测试替身模式");
            assertFalse(candText.contains("\"DETERMINISTIC_TEST_DOUBLE\""), "严禁包含测试替身模式");

            if (baseText.contains("\"REAL_PRODUCTION\"")) {
                realProductionCount++;
            }
            if (candText.contains("\"REAL_PRODUCTION\"")) {
                realProductionCount++;
            }

            // 核心防伪断言 2: 严禁包含测试替身的特征前缀与投毒关键词
            assertFalse(baseText.contains("ev-test-probe-"), "严禁包含测试替身前缀 ev-test-");
            assertFalse(candText.contains("ev-test-probe-"), "严禁包含测试替身前缀 ev-test-");
            assertFalse(baseText.contains("v-base-fingerprint"), "严禁包含测试替身伪造指纹");
            assertFalse(candText.contains("v-cand-fingerprint"), "严禁包含测试替身伪造指纹");
            assertFalse(baseText.contains("in_hash_sample"), "严禁包含测试替身固定入参哈希");
            assertFalse(candText.contains(" (精炼客观)"), "严禁包含作弊拼接的 (精炼客观)");

            // 核心防伪断言 3: 必须包含真实的大模型输出与公网耗时
            assertTrue(baseText.contains("finalOutputSummary"), "必须包含真实模型回答摘要");
            assertTrue(candText.contains("finalOutputSummary"), "必须包含真实模型回答摘要");
        }

        // 真实生产凭单数量必须占绝大多数 (>= 6)
        assertTrue(realProductionCount >= 6, "至少 6 次以上调用必须成功达成 REAL_PRODUCTION，当前: " + realProductionCount);

        // 验证物理评估总账已落盘
        Path summaryJson = outputBaseDir.resolve(runId).resolve("eval").resolve("ppc_summary.json");
        assertTrue(Files.exists(summaryJson), "PPC 评估总账必须存在: " + summaryJson);
        assertTrue(Files.size(summaryJson) > 0);

        // 验证真实差分非人为固定 40 Tokens
        assertNotNull(summary.diffs());
        assertEquals(5, summary.diffs().size());
        boolean hasNonFortyDelta = summary.diffs().stream().anyMatch(d -> Math.abs(d.deltaTokens()) != 40L);
        assertTrue(hasNonFortyDelta, "真实调用生成的 Token 差分必须遵循大模型真实概率分布，绝非固定的 40 Tokens");
    }

    @Test
    @DisplayName("防伪隔离验证: DeterministicHarnessTestDouble 必须打标 DETERMINISTIC_TEST_DOUBLE 并隔离在合成目录")
    void testDeterministicDoubleIsolationInSyntheticDir() throws IOException {
        Path scratchDir = Path.of("target/test-scratch/harness5-double");
        Files.createDirectories(scratchDir);

        Map<String, DeterministicHarnessTestDouble.DeterministicResponse> fixtures = Map.of(
                "kb-qa-01", new DeterministicHarnessTestDouble.DeterministicResponse(
                        "测试替身回答", "mockTool", true, 500L, 20L)
        );
        DeterministicHarnessTestDouble doubleDriver = new DeterministicHarnessTestDouble(fixtures);
        DefaultOfflineProbeExecutor executor = new DefaultOfflineProbeExecutor(doubleDriver, null);

        OfflineProbeExecutor.ProbeExecutionOptions options = new OfflineProbeExecutor.ProbeExecutionOptions(
                5000L, 1, scratchDir, true, "deepseek", "deepseek-flash"
        );

        HermesHarnessAssembler assembler = new HermesHarnessAssembler();
        HermesHarnessVoucher voucher = assembler.createRequestVoucher(1001L, "基准约束");
        OfflineBenchmarkTestCase tc = OfflineBenchmarkTestCase.createDefaultSuite().get(0);

        OfflineProbeExecutor.ProbeExecutionReport report =
                executor.executeSingleProbe("run-double-test", "baseline", tc, voucher, options);

        assertNotNull(report);
        assertNotNull(report.evidenceRecord());
        assertEquals(HarnessEvidenceRecord.ExecutionMode.DETERMINISTIC_TEST_DOUBLE,
                report.evidenceRecord().executionMode(),
                "测试替身生成的凭单必须显式打标 DETERMINISTIC_TEST_DOUBLE");
        assertTrue(report.evidenceRecord().evidenceId().startsWith("ev-test-"),
                "测试替身证据 ID 必须保留测试标识前缀，杜绝冒充生产");
    }

    @Test
    @DisplayName("异常自愈门禁: 探针遇到不可恢复中断时必须生成 FAIL_SAFE 凭单并触发熔断")
    void testFailSafeSynthesisOnInterruption() {
        OfflineBenchmarkTestCase tc = OfflineBenchmarkTestCase.createDefaultSuite().get(0);
        HermesHarnessAssembler assembler = new HermesHarnessAssembler();
        HermesHarnessVoucher voucher = assembler.createRequestVoucher(1001L, "测试角色约束");

        // 构造在执行时抛出中断异常的驱动器
        tech.qiantong.qknow.hermes.agent.harness.offline.probe.HarnessExecutionDriver failingDriver =
                new tech.qiantong.qknow.hermes.agent.harness.offline.probe.HarnessExecutionDriver() {
                    @Override
                    public reactor.core.publisher.Flux<tech.qiantong.qknow.hermes.proto.ChatEvent> execute(tech.qiantong.qknow.hermes.proto.ChatRequest request) {
                        return reactor.core.publisher.Flux.error(new java.net.ConnectException("模拟外部网络连接失败"));
                    }

                    @Override
                    public HarnessEvidenceRecord getLatestEvidence(String requestId) {
                        return null; // 无证据产出
                    }

                    @Override
                    public DriverMode getMode() {
                        return DriverMode.REAL_PRODUCTION;
                    }
                };

        DefaultOfflineProbeExecutor executor = new DefaultOfflineProbeExecutor(failingDriver, null);
        Path scratchDir = Path.of("target/test-scratch/harness5-failsafe");
        OfflineProbeExecutor.ProbeExecutionOptions options = new OfflineProbeExecutor.ProbeExecutionOptions(
                1000L, 1, scratchDir, true, "deepseek", "deepseek-flash"
        );

        OfflineProbeExecutor.ProbeExecutionReport report =
                executor.executeSingleProbe("run-failsafe-test", "baseline", tc, voucher, options);

        assertNotNull(report);
        assertFalse(report.success(), "异常驱动执行应当报告失败");
        assertEquals("ConnectException", report.errorCode(), "异常错误码应当正确解包为底层 ConnectException");
        assertNotNull(report.evidenceRecord());
        assertEquals(HarnessEvidenceRecord.ExecutionMode.FAIL_SAFE,
                report.evidenceRecord().executionMode(),
                "异常探针必须兜底生成 FAIL_SAFE 执行模式凭单");
        assertTrue(report.evidenceRecord().finalOutputSummary().contains("FAIL_SAFE [ConnectException]"));
    }
}
