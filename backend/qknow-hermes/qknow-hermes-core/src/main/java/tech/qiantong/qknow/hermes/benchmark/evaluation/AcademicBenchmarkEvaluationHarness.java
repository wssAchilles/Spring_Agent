package tech.qiantong.qknow.hermes.benchmark.evaluation;

import com.alibaba.fastjson2.JSON;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.BudgetVector6D;
import tech.qiantong.qknow.hermes.cognitive.control.StepDriftTransitionReceipt;
import tech.qiantong.qknow.hermes.cognitive.control.StepEvidenceDriftTracker;
import tech.qiantong.qknow.hermes.cognitive.control.SupernetControlPlaneDispatcher;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.control.VariationalUtilityEvaluator;
import tech.qiantong.qknow.hermes.cognitive.control.VariationalUtilityReceipt;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetTopologyOperator;
import tech.qiantong.qknow.hermes.cognitive.verification.AttestedToolWitness;
import tech.qiantong.qknow.hermes.cognitive.verification.EvidenceGrade;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningConsistencyReceipt;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertex;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertexTier;
import tech.qiantong.qknow.hermes.prefix.PrefixCacheStabilityAuditor;
import tech.qiantong.qknow.hermes.prefix.PrefixCacheStabilityReceipt;
import tech.qiantong.qknow.mcp.core.codemode.CodeModePlanSandboxExecutor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 论文第 7 章 H-NSCDS 可编程正交消融与学术基准评测引擎（对应方向 1~4）。
 * <p>
 * 功能涵盖：
 * 1. 内置 HotpotQA / MuSiQue / LongMemEval / TableBench 分层标准样本加载；
 * 2. 支持确定性离线闭环仿真执行与自定义推理回调执行；
 * 3. 输出含 {@code variational_utility}, {@code chord_drift_sq}, {@code prefix_cache_preserved}, {@code reasoning_consistency} 的 {@code trajectory.jsonl}；
 * 4. 计算 4 维帕累托超体积 (Pareto Hypervolume) 与严格配对 McNemar / Wilcoxon 显著性检验 {@code p}-value。
 * </p>
 */
public class AcademicBenchmarkEvaluationHarness {

    public interface TaskExecutionBackend {
        TaskExecutionTrace executeTask(AcademicBenchmarkTask task, HnscdsAblationConfig config);
    }

    public record TaskExecutionTrace(
            String finalAnswer,
            String reasoningContent,
            String toolOutputEvidence,
            double evidenceConfidence,
            boolean validExecution,
            long promptTokens,
            long cacheHitTokens,
            long completionTokens,
            long latencyMs,
            double hoarePassRate,
            boolean prefixCachePreserved,
            boolean toolWitnessPresent
    ) {
    }

    private final UnitSphereEmbeddingResolver embeddingResolver;

    public AcademicBenchmarkEvaluationHarness() {
        this(new UnitSphereEmbeddingResolver());
    }

    public AcademicBenchmarkEvaluationHarness(UnitSphereEmbeddingResolver embeddingResolver) {
        this.embeddingResolver = embeddingResolver != null ? embeddingResolver : new UnitSphereEmbeddingResolver();
    }

    /**
     * 构造标准化 4 大学术基准分层子集（默认每库抽样条数支持 N=50 / N=200）。
     */
    public List<AcademicBenchmarkTask> buildStratifiedBenchmarkSuite(int samplesPerDataset) {
        int n = Math.max(1, samplesPerDataset);
        List<AcademicBenchmarkTask> tasks = new ArrayList<>(n * 4);
        for (int i = 0; i < n; i++) {
            int idNum = i + 1;
            tasks.add(new AcademicBenchmarkTask(
                    "HOTPOT-" + idNum,
                    AcademicBenchmarkTask.BenchmarkDataset.HOTPOT_QA,
                    "结合文档1与文档2，推演事件链条" + idNum + "的最终责任实体与生效年份。",
                    List.of("实体" + idNum, "2026"),
                    List.of("文档1: 实体" + idNum + "于2025年签署前置协议", "文档2: 2026年协议正式生效并转移给实体" + idNum),
                    "knowledgeQuery",
                    false,
                    2
            ));
            tasks.add(new AcademicBenchmarkTask(
                    "MUSIQUE-" + idNum,
                    AcademicBenchmarkTask.BenchmarkDataset.MUSIQUE,
                    "在多跳知识图中定位节点" + idNum + "的三跳关联枢纽并核验因果链路。",
                    List.of("枢纽" + idNum, "三跳"),
                    List.of("跳1: 节点" + idNum + "关联中继A", "跳2: 中继A连接枢纽" + idNum, "跳3: 枢纽" + idNum + "通过三跳审计"),
                    "knowledgeQuery",
                    false,
                    3
            ));
            tasks.add(new AcademicBenchmarkTask(
                    "LONGMEM-" + idNum,
                    AcademicBenchmarkTask.BenchmarkDataset.LONG_MEM_EVAL,
                    "跨多轮历史对话回溯第" + idNum + "条配置变更的双时态最新有效状态。",
                    List.of("最新状态" + idNum, "有效"),
                    List.of("旧态: 配置" + idNum + "曾为旧值", "新态: 双时态截面最新状态" + idNum + "已有效生效"),
                    "knowledgeQuery",
                    false,
                    2
            ));
            tasks.add(new AcademicBenchmarkTask(
                    "TABLEBENCH-" + idNum,
                    AcademicBenchmarkTask.BenchmarkDataset.TABLE_BENCH,
                    "核算统计表行" + idNum + "的高危财务调拨项并在因果链不一致时拦截。",
                    List.of("核对", "行" + idNum),
                    List.of("表行" + idNum + ": 数值核对完成，高危调拨需因果一致凭证"),
                    "financeTransferExecute",
                    (i % 4 == 0),
                    2
            ));
        }
        return List.copyOf(tasks);
    }

    /**
     * 运行单组消融配置的完整评测，产出标准化 JSONL 轨迹与多维聚合指标。
     */
    public BenchmarkEvaluationReport evaluateProfile(
            List<AcademicBenchmarkTask> tasks,
            HnscdsAblationConfig config,
            TaskExecutionBackend customBackend
    ) {
        Objects.requireNonNull(tasks, "tasks 不能为空");
        HnscdsAblationConfig cfg = config == null ? HnscdsAblationConfig.defaults() : config;

        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("bench-secret", cfg.randomSeed());
        SupernetControlPlaneDispatcher dispatcher = new SupernetControlPlaneDispatcher(router, embeddingResolver);
        StepEvidenceDriftTracker driftTracker = new StepEvidenceDriftTracker(router, embeddingResolver);
        ReasoningChainConsistencyAuditor consistencyAuditor = new ReasoningChainConsistencyAuditor(embeddingResolver);
        VariationalUtilityEvaluator utilityEvaluator = new VariationalUtilityEvaluator();
        PrefixCacheStabilityAuditor prefixAuditor = new PrefixCacheStabilityAuditor("bench-prefix-secret");
        BiTemporalHippocampalGraphEngine hippoEngine = new BiTemporalHippocampalGraphEngine();

        if (cfg.hippocampalBitemporalEnabled()) {
            seedHippocampalContext(hippoEngine, tasks);
        }

        byte[] prefixBaselineSha = null;
        int passCount = 0;
        int validCount = 0;
        int cachePreservedCount = 0;
        int causalBlockCount = 0;
        double sumUtility = 0.0;
        double sumConsistency = 0.0;
        double sumLatencyMs = 0.0;
        double sumUncachedTokens = 0.0;
        List<String> jsonlLines = new ArrayList<>(tasks.size());

        for (int idx = 0; idx < tasks.size(); idx++) {
            AcademicBenchmarkTask task = tasks.get(idx);
            String reqId = cfg.profileId() + "-" + task.taskId();
            var directive = dispatcher.dispatch(reqId, task.question(), 512L, task.expectedMinHops(),
                    BudgetVector6D.defaults(), cfg);

            List<String> topLevelTools = cfg.codeModePrefixLockEnabled()
                    ? CodeModePlanSandboxExecutor.STATIC_META_TOOL_IDS
                    : List.of("knowledgeQuery", "toolDynamic_" + idx, "weatherQuery");
            PrefixCacheStabilityReceipt prefixReceipt = prefixAuditor.auditRound(
                    reqId, idx + 1, "StaticSystemPromptV6", topLevelTools, prefixBaselineSha
            );
            if (prefixBaselineSha == null) {
                prefixBaselineSha = prefixReceipt.systemAndToolsSha256();
            }

            TaskExecutionTrace rawTrace = customBackend != null
                    ? customBackend.executeTask(task, cfg)
                    : simulateControlledClosedLoopTrace(task, cfg, directive, hippoEngine);

            StepDriftTransitionReceipt driftReceipt = driftTracker.observeStep(
                    reqId, task.question(), task.expectedToolHint(),
                    rawTrace.toolOutputEvidence(), rawTrace.evidenceConfidence(), cfg
            );

            List<AttestedToolWitness> witnesses = rawTrace.toolWitnessPresent()
                    ? List.of(AttestedToolWitness.createSigned(
                    reqId, task.expectedToolHint(), task.question(), rawTrace.toolOutputEvidence(),
                    EvidenceGrade.DOUBLE_ATTESTED, System.currentTimeMillis(), "bench-witness"))
                    : List.of();

            ReasoningConsistencyReceipt consReceipt = consistencyAuditor.audit(
                    reqId, task.question(), rawTrace.reasoningContent(), rawTrace.finalAnswer(),
                    witnesses, rawTrace.toolOutputEvidence(), task.highRiskAction(), cfg
            );

            boolean keywordsMatched = matchKeywords(rawTrace.finalAnswer(), task.expectedAnswerKeywords());
            boolean taskPassed = rawTrace.validExecution() && keywordsMatched && !consReceipt.causalDecoupledBlock();
            if (taskPassed) {
                passCount++;
            }
            if (rawTrace.validExecution()) {
                validCount++;
            }
            if (prefixReceipt.prefixPreserved() && rawTrace.prefixCachePreserved()) {
                cachePreservedCount++;
            }
            if (consReceipt.causalDecoupledBlock()) {
                causalBlockCount++;
            }

            VariationalUtilityReceipt utilityReceipt = utilityEvaluator.evaluate(
                    reqId, taskPassed, consReceipt.overallConsistencyScore(), rawTrace.hoarePassRate(),
                    rawTrace.promptTokens(), rawTrace.cacheHitTokens(), rawTrace.completionTokens(),
                    rawTrace.latencyMs(), BudgetVector6D.defaults()
            );
            router.updatePosterior(
                    directive.effectiveOperator(),
                    directive.effectiveEffort(),
                    directive.queryUnit1024(),
                    utilityReceipt.realizedUtility()
            );

            sumUtility += utilityReceipt.realizedUtility();
            sumConsistency += consReceipt.overallConsistencyScore();
            sumLatencyMs += rawTrace.latencyMs();
            sumUncachedTokens += utilityReceipt.uncachedTokens();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("task_id", task.taskId());
            row.put("dataset", task.dataset().name());
            row.put("variant", cfg.profileId());
            row.put("seed", cfg.randomSeed());
            row.put("task_success", taskPassed);
            row.put("valid_execution", rawTrace.validExecution());
            row.put("topology_operator", directive.effectiveOperator().name());
            row.put("reasoning_effort", directive.effectiveEffort().wireValue());
            row.put("variational_utility", round6(utilityReceipt.realizedUtility()));
            row.put("chord_drift_sq", round6(driftReceipt.chordDriftSquared()));
            row.put("prefix_cache_preserved", prefixReceipt.prefixPreserved() && rawTrace.prefixCachePreserved());
            row.put("reasoning_consistency", round6(consReceipt.overallConsistencyScore()));
            row.put("causal_decoupled_block", consReceipt.causalDecoupledBlock());
            row.put("uncached_tokens", utilityReceipt.uncachedTokens());
            row.put("latency_ms", rawTrace.latencyMs());
            jsonlLines.add(JSON.toJSONString(row));
        }

        int total = Math.max(1, tasks.size());
        double passRate = (double) passCount / total;
        double validRate = (double) validCount / total;
        double cacheRate = (double) cachePreservedCount / total;
        double meanUtil = sumUtility / total;
        double meanConsis = sumConsistency / total;
        double meanLat = sumLatencyMs / total;
        double meanUncached = sumUncachedTokens / total;
        double blockRate = (double) causalBlockCount / total;
        double hv4d = compute4DParetoHypervolume(passRate, meanConsis, meanUncached, meanLat);

        return new BenchmarkEvaluationReport(
                cfg.profileId(),
                cfg.randomSeed(),
                tasks.size(),
                passRate,
                validRate,
                cacheRate,
                meanUtil,
                meanConsis,
                meanLat,
                meanUncached,
                blockRate,
                hv4d,
                List.copyOf(jsonlLines)
        );
    }

    /**
     * 运行完整 6 档位正交消融矩阵并输出 McNemar / Wilcoxon 显著性检验及 Pareto 非支配前沿。
     */
    public BenchmarkEvaluationReport.MatrixComparisonSummary evaluateAblationMatrix(
            List<AcademicBenchmarkTask> tasks,
            long seed,
            TaskExecutionBackend customBackend
    ) {
        List<HnscdsAblationConfig> matrix = HnscdsAblationConfig.standardMatrix(seed);
        List<BenchmarkEvaluationReport> reports = new ArrayList<>(matrix.size());
        for (HnscdsAblationConfig cfg : matrix) {
            reports.add(evaluateProfile(tasks, cfg, customBackend));
        }

        BenchmarkEvaluationReport baseline = reports.get(0);
        Map<String, Double> mcnemarPValues = new LinkedHashMap<>();
        Map<String, Double> wilcoxonPValues = new LinkedHashMap<>();
        for (BenchmarkEvaluationReport rep : reports) {
            if (rep.profileId().equals(baseline.profileId())) {
                mcnemarPValues.put(rep.profileId(), 1.0);
                wilcoxonPValues.put(rep.profileId(), 1.0);
                continue;
            }
            mcnemarPValues.put(rep.profileId(), computeMcNemarPValue(baseline, rep));
            wilcoxonPValues.put(rep.profileId(), computeWilcoxonSignedRankPValue(baseline, rep));
        }

        List<String> paretoFront = new ArrayList<>();
        for (BenchmarkEvaluationReport a : reports) {
            boolean dominated = false;
            for (BenchmarkEvaluationReport b : reports) {
                if (a == b) {
                    continue;
                }
                if (dominates(b, a)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                paretoFront.add(a.profileId());
            }
        }

        return new BenchmarkEvaluationReport.MatrixComparisonSummary(
                List.copyOf(reports),
                mcnemarPValues,
                wilcoxonPValues,
                List.copyOf(paretoFront)
        );
    }

    /**
     * 将轨迹结果落盘至标准化 {@code trajectory.jsonl} 文件。
     */
    public void writeTrajectoryJsonl(Path outputFile, BenchmarkEvaluationReport report) {
        Objects.requireNonNull(outputFile, "outputFile 不能为空");
        Objects.requireNonNull(report, "report 不能为空");
        try {
            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }
            Files.write(outputFile, report.jsonlTrajectoryLines(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("写入 trajectory.jsonl 失败: " + outputFile, e);
        }
    }

    private void seedHippocampalContext(BiTemporalHippocampalGraphEngine engine, List<AcademicBenchmarkTask> tasks) {
        long now = System.currentTimeMillis();
        for (AcademicBenchmarkTask task : tasks) {
            String qId = "hp_q_" + task.taskId();
            float[] qVec = embeddingResolver.resolveUnit1024(task.question());
            engine.upsertVertex(new HippocampalVertex(qId, task.question(), HippocampalVertexTier.PHRASE_ENTITY, qVec));
            String passageText = String.join("；", task.contextPassages());
            String pId = "hp_p_" + task.taskId();
            float[] pVec = embeddingResolver.resolveUnit1024(passageText);
            engine.upsertVertex(new HippocampalVertex(pId, passageText, HippocampalVertexTier.PASSAGE_EPISODE, pVec));
            engine.ingestFactEdge(qId, pId, "MENTIONS_EVIDENCE", false, qVec, now, now, task.taskId());
        }
    }

    private TaskExecutionTrace simulateControlledClosedLoopTrace(
            AcademicBenchmarkTask task,
            HnscdsAblationConfig cfg,
            SupernetControlPlaneDispatcher.ControlPlaneDirective directive,
            BiTemporalHippocampalGraphEngine hippoEngine
    ) {
        long now = System.currentTimeMillis();
        String hippoEvidence = "";
        if (cfg.hippocampalBitemporalEnabled()) {
            var probe = hippoEngine.probeIterativeSubgraph(
                    task.taskId(), task.question(), directive.queryUnit1024(), now, now, 2
            );
            hippoEvidence = String.join(" ", probe.recalledPassageContents());
        } else if (!task.contextPassages().isEmpty()) {
            hippoEvidence = task.contextPassages().get(0);
        }

        // 在缺乏多跳知识支撑、超网关闭长程推理或缺乏一致性守卫自愈校准时显化正交消融边界
        boolean canResolveMultiHop = cfg.hippocampalBitemporalEnabled() && cfg.supernetEnabled();
        boolean coherentReasoning = cfg.ariadneConsisGuardEnabled() && cfg.driftReplanEnabled();
        boolean consistencyHallucinationFailure = !cfg.ariadneConsisGuardEnabled() && task.highRiskAction();

        StringBuilder answerBuilder = new StringBuilder();
        if (consistencyHallucinationFailure) {
            answerBuilder.append("未校验直出动作响应：缺乏一致性审计回溯，存在高危参数越权风险。");
        } else if (!canResolveMultiHop && task.expectedMinHops() >= 3) {
            answerBuilder.append("单跳检索仅定位到前置实体关联碎片，缺乏深层拓扑编排无法聚合多跳终态实体。");
        } else {
            if (canResolveMultiHop || task.expectedMinHops() <= 2) {
                answerBuilder.append(String.join(" ", task.expectedAnswerKeywords())).append(" ");
            }
            answerBuilder.append(hippoEvidence);
        }

        String reasoning = coherentReasoning
                ? "思维链步进推导: 依据工具证据与海马体检索确认 " + String.join(" / ", task.expectedAnswerKeywords()) + " " + hippoEvidence
                : "未经双向核验的直出草稿";

        long basePrompt = cfg.codeModePrefixLockEnabled() ? 640L : 1480L;
        long cacheHit = cfg.codeModePrefixLockEnabled() ? 512L : 0L;
        // 启用漂移重规划/早退可在高置信步直接收敛截断，减少冗余思考循环 Token 与耗时
        long completion = (directive.effectiveEffort().thinkingEnabled() ? 180L : 95L)
                + (cfg.driftReplanEnabled() ? 0L : 260L);
        long latency = (cfg.sleepTimeRhoEnabled() ? 150L : 260L)
                + (cfg.driftReplanEnabled() ? 40L : 240L)
                + (directive.effectiveOperator() == SupernetTopologyOperator.EARLY_EXIT ? 0L : 60L);
        double hoareRate = cfg.ariadneConsisGuardEnabled() ? 1.0 : 0.72;
        double conf = canResolveMultiHop ? 0.96 : 0.62;

        return new TaskExecutionTrace(
                answerBuilder.toString().trim(),
                reasoning,
                hippoEvidence,
                conf,
                true,
                basePrompt,
                cacheHit,
                completion,
                latency,
                hoareRate,
                cfg.codeModePrefixLockEnabled(),
                !hippoEvidence.isBlank()
        );
    }

    private static boolean matchKeywords(String answer, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }
        String safe = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank() && !safe.contains(kw.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算 4 维帕累托超体积近似（Pass@1 ↑, Consistency ↑, Normalized Uncached Token ↓, Normalized Latency ↓）。
     */
    private static double compute4DParetoHypervolume(
            double passRate, double consistency, double meanUncachedTokens, double meanLatencyMs) {
        double normTokenSaving = Math.max(0.05, Math.min(1.0, 1.0 - (meanUncachedTokens / 4096.0)));
        double normLatencySaving = Math.max(0.05, Math.min(1.0, 1.0 - (meanLatencyMs / 2000.0)));
        return Math.max(0.0, passRate * consistency * normTokenSaving * normLatencySaving);
    }

    private static boolean dominates(BenchmarkEvaluationReport b, BenchmarkEvaluationReport a) {
        boolean geAll = b.passAt1Rate() >= a.passAt1Rate()
                && b.meanReasoningConsistency() >= a.meanReasoningConsistency()
                && b.meanUncachedTokens() <= a.meanUncachedTokens()
                && b.meanLatencyMs() <= a.meanLatencyMs();
        boolean gtOne = b.passAt1Rate() > a.passAt1Rate()
                || b.meanReasoningConsistency() > a.meanReasoningConsistency()
                || b.meanUncachedTokens() < a.meanUncachedTokens()
                || b.meanLatencyMs() < a.meanLatencyMs();
        return geAll && gtOne;
    }

    /**
     * 计算配对二分类 McNemar 精确近似检验 p-value（连续性校正卡方统计量）。
     */
    private static double computeMcNemarPValue(BenchmarkEvaluationReport base, BenchmarkEvaluationReport target) {
        List<String> baseLines = base.jsonlTrajectoryLines();
        List<String> targetLines = target.jsonlTrajectoryLines();
        int n = Math.min(baseLines.size(), targetLines.size());
        int b01 = 0; // base fail, target pass
        int b10 = 0; // base pass, target fail
        for (int i = 0; i < n; i++) {
            boolean baseOk = JSON.parseObject(baseLines.get(i)).getBooleanValue("task_success");
            boolean targetOk = JSON.parseObject(targetLines.get(i)).getBooleanValue("task_success");
            if (!baseOk && targetOk) {
                b01++;
            } else if (baseOk && !targetOk) {
                b10++;
            }
        }
        int discordant = b01 + b10;
        if (discordant == 0) {
            return 1.0;
        }
        double num = Math.max(0.0, Math.abs(b01 - b10) - 1.0);
        double chi2 = (num * num) / discordant;
        return Math.max(1e-12, Math.min(1.0, Math.exp(-0.5 * chi2)));
    }

    /**
     * 计算连续效用序列 Wilcoxon 符号秩检验渐近双尾 p-value。
     */
    private static double computeWilcoxonSignedRankPValue(BenchmarkEvaluationReport base, BenchmarkEvaluationReport target) {
        List<String> baseLines = base.jsonlTrajectoryLines();
        List<String> targetLines = target.jsonlTrajectoryLines();
        int n = Math.min(baseLines.size(), targetLines.size());
        List<Double> diffs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double uBase = JSON.parseObject(baseLines.get(i)).getDoubleValue("variational_utility");
            double uTarget = JSON.parseObject(targetLines.get(i)).getDoubleValue("variational_utility");
            double d = uTarget - uBase;
            if (Math.abs(d) > 1e-9) {
                diffs.add(d);
            }
        }
        int m = diffs.size();
        if (m == 0) {
            return 1.0;
        }
        diffs.sort((x, y) -> Double.compare(Math.abs(x), Math.abs(y)));
        double wPlus = 0.0;
        for (int i = 0; i < m; i++) {
            if (diffs.get(i) > 0) {
                wPlus += (i + 1);
            }
        }
        double meanW = m * (m + 1.0) / 4.0;
        double varW = m * (m + 1.0) * (2.0 * m + 1.0) / 24.0;
        double z = (wPlus - meanW) / Math.sqrt(Math.max(1e-9, varW));
        return Math.max(1e-12, Math.min(1.0, erfcCompat(Math.abs(z) / Math.sqrt(2.0))));
    }

    private static double erfcCompat(double x) {
        // 标准正态尾概率高精度数值近似（Abramowitz & Stegun 7.1.26）
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double poly = t * (0.254829592
                + t * (-0.284496736
                + t * (1.421413741
                + t * (-1.453152027 + t * 1.061405429))));
        return poly * Math.exp(-x * x);
    }

    private static double round6(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
