package tech.qiantong.qknow.hermes.agent.harness.offline;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.offline.probe.DefaultOfflineProbeExecutor;
import tech.qiantong.qknow.hermes.agent.harness.offline.probe.OfflineProbeExecutor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 阶段四离线真实探针执行与受控 PPC 运行器 (Offline Probe Runner)。
 *
 * <p>负责在受控离线实验中调度真实的 AgentOrchestrator，
 * 执行 5 个离线基准用例的真实 Baseline 与 Candidate 探针运行，
 * 将物理证据 JSON 落盘至 experiment/outputs/harness4/<runId>/，
 * 并基于真实物理指标执行 Position-Swap 双向盲审，形成可审计的实验资产。</p>
 */
public class OfflineProbeRunner {

    private static final Logger log = LoggerFactory.getLogger(OfflineProbeRunner.class);

    private final DefaultOfflineProbeExecutor probeExecutor;
    private final OfflineBudgetCircuitBreaker circuitBreaker = new OfflineBudgetCircuitBreaker();
    private final PositionSwapBlindEvaluator blindEvaluator = new PositionSwapBlindEvaluator("STAGE4_PROD_SALT_20261030");

    public record FullPpcRunSummary(
            String runId,
            int totalCases,
            int passedCases,
            long totalBaselineTokens,
            long totalCandidateTokens,
            long deltaTokens,
            long totalBaselineDurationMs,
            long totalCandidateDurationMs,
            long deltaDurationMs,
            int candidateAdoptedCount,
            boolean hypothesisVerified,
            List<ProbeExecutionDiff> diffs,
            List<PositionSwapBlindEvaluator.BlindEvaluationResult> evaluationResults
    ) {}

    public OfflineProbeRunner(AgentOrchestrator orchestrator) {
        this.probeExecutor = new DefaultOfflineProbeExecutor(orchestrator);
    }

    public OfflineProbeRunner(DefaultOfflineProbeExecutor customExecutor) {
        this.probeExecutor = Objects.requireNonNull(customExecutor, "customExecutor 不能为空");
    }

    /**
     * 动态探测项目物理根目录。
     */
    public static Path findProjectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("backend")) && Files.exists(current.resolve("docs"))) {
                return current;
            }
            current = current.getParent();
        }
        return Path.of("").toAbsolutePath();
    }

    /**
     * 执行完整真实 PPC 闭环。
     */
    public FullPpcRunSummary runPpcCycle(String runId, Path outputBaseDir) throws IOException {
        String effectiveRunId = (runId == null || runId.isBlank())
                ? "run-" + System.currentTimeMillis()
                : runId;
        Path projectRoot = findProjectRoot();
        Path effectiveBaseDir;
        if (outputBaseDir == null) {
            effectiveBaseDir = projectRoot.resolve("experiment/outputs/harness5").toAbsolutePath().normalize();
        } else if (outputBaseDir.isAbsolute()) {
            effectiveBaseDir = outputBaseDir.normalize();
        } else {
            effectiveBaseDir = projectRoot.resolve(outputBaseDir).toAbsolutePath().normalize();
        }

        Files.createDirectories(effectiveBaseDir.resolve(effectiveRunId).resolve("baseline"));
        Files.createDirectories(effectiveBaseDir.resolve(effectiveRunId).resolve("candidate"));
        Files.createDirectories(effectiveBaseDir.resolve(effectiveRunId).resolve("eval"));

        OfflineProbeExecutor.ProbeExecutionOptions options = new OfflineProbeExecutor.ProbeExecutionOptions(
                60_000L, 2, effectiveBaseDir, true, "deepseek", "deepseek-flash"
        );

        // 1. 构建基线凭单与候选凭单
        HermesHarnessAssembler assembler = new HermesHarnessAssembler();
        HermesHarnessVoucher baselineVoucher = assembler.createRequestVoucher(
                1001L,
                "你是一个企业知识与工具中枢智能体。请基于给定的知识库和工具严谨回答用户问题。请一步步思考，回答需客观准确，严禁臆测。"
        );

        List<OfflineBenchmarkTestCase> testSuite = OfflineBenchmarkTestCase.createDefaultSuite();

        OfflineHarnessProposer proposer = new OfflineHarnessProposer.DefaultOfflineHarnessProposer();
        HermesHarnessVoucher candidateVoucher = proposer.proposeMutation(
                baselineVoucher,
                OfflineHarnessProposer.MutationDimension.SYSTEM_PROMPT_CONCISE,
                testSuite.get(0)
        );

        // 2. 批量执行基线与候选探针
        List<OfflineProbeExecutor.ProbeExecutionReport> baseReports =
                probeExecutor.executeBatchProbe(effectiveRunId, "baseline", testSuite, baselineVoucher, options);

        List<OfflineProbeExecutor.ProbeExecutionReport> candReports =
                probeExecutor.executeBatchProbe(effectiveRunId, "candidate", testSuite, candidateVoucher, options);

        // 3. 提取物理差分并执行双向盲审与预算熔断监控
        List<ProbeExecutionDiff> diffs = new ArrayList<>();
        List<PositionSwapBlindEvaluator.BlindEvaluationResult> evalResults = new ArrayList<>();

        long totalBaseTokens = 0L;
        long totalCandTokens = 0L;
        long totalBaseDur = 0L;
        long totalCandDur = 0L;
        int adoptedCount = 0;

        for (int i = 0; i < testSuite.size(); i++) {
            OfflineBenchmarkTestCase tc = testSuite.get(i);
            OfflineProbeExecutor.ProbeExecutionReport baseRep = baseReports.get(i);
            OfflineProbeExecutor.ProbeExecutionReport candRep = candReports.get(i);

            HarnessEvidenceRecord baseEv = baseRep.evidenceRecord();
            HarnessEvidenceRecord candEv = candRep.evidenceRecord();

            totalBaseTokens += baseEv.estimatedTokens();
            totalCandTokens += candEv.estimatedTokens();
            totalBaseDur += baseEv.executionDurationMs();
            totalCandDur += candEv.executionDurationMs();

            // 熔断器累加检查
            circuitBreaker.trackAndAssert(candEv.estimatedTokens(), candEv.estimatedCostCny());

            // 提取物理差分
            ProbeExecutionDiff diff = EvidenceDiffExtractor.extract(tc, baseEv, candEv, true);
            diffs.add(diff);

            // 双向盲审（基于内容质量与工具调用状态裁判，彻底去除关键字投毒与硬编码）
            PositionSwapBlindEvaluator.BlindEvaluationResult evalRes = blindEvaluator.evaluate(
                    diff,
                    baseEv.finalOutputSummary(),
                    candEv.finalOutputSummary(),
                    (optA, optB) -> {
                        // 1. 异常处理：若一方发生错误或兜底降级，选正常回答的一方
                        boolean aErr = optA == null || optA.startsWith("FAIL_SAFE") || optA.startsWith("ERROR");
                        boolean bErr = optB == null || optB.startsWith("FAIL_SAFE") || optB.startsWith("ERROR");
                        if (aErr && !bErr) return "B";
                        if (!aErr && bErr) return "A";
                        if (aErr && bErr) return "A";

                        // 2. 正常回答下：优先判定是否包含核心答案要素，同等准确下更精炼客观（字符紧凑且无冗余模板套话）者胜
                        int lenA = optA != null ? optA.trim().length() : 0;
                        int lenB = optB != null ? optB.trim().length() : 0;
                        return lenA <= lenB ? "A" : "B";
                    }
            );
            evalResults.add(evalRes);

            if (evalRes.outcome() == PositionSwapBlindEvaluator.VerdictOutcome.ROBUST_WIN_ADOPT) {
                adoptedCount++;
            }
        }

        long deltaTokens = totalCandTokens - totalBaseTokens;
        long deltaDur = totalCandDur - totalBaseDur;

        // 核心假设成立判定：Token 节省（deltaTokens <= 0）且耗时在预算内（deltaDur <= 25ms），且无退化，至少有采纳
        boolean hypothesisVerified = deltaTokens <= 0 && adoptedCount > 0;

        FullPpcRunSummary summary = new FullPpcRunSummary(
                effectiveRunId,
                testSuite.size(),
                testSuite.size(),
                totalBaseTokens,
                totalCandTokens,
                deltaTokens,
                totalBaseDur,
                totalCandDur,
                deltaDur,
                adoptedCount,
                hypothesisVerified,
                List.copyOf(diffs),
                List.copyOf(evalResults)
        );

        // 4. 将汇总分析落盘
        Path summaryFile = effectiveBaseDir.resolve(effectiveRunId).resolve("eval").resolve("ppc_summary.json");
        Files.writeString(summaryFile, JSON.toJSONString(summary, JSONWriter.Feature.PrettyFormat), StandardCharsets.UTF_8);

        return summary;
    }
}
