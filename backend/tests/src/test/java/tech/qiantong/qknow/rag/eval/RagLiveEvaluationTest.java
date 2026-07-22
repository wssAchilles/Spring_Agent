package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.eval.EvaluationDataset;
import tech.qiantong.qknow.hermes.eval.EvaluationReport;
import tech.qiantong.qknow.hermes.eval.EvaluationStatus;
import tech.qiantong.qknow.hermes.eval.RAGChecker;
import tech.qiantong.qknow.hermes.eval.RagasEvalConfig;
import tech.qiantong.qknow.hermes.eval.RagasEvaluator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit live generation/judge layer. Retrieval inputs are the contexts emitted by the
 * real shadow SUT; labels are loaded separately and are only passed to evaluators.
 */
@EnabledIfSystemProperty(named = "rag.eval.live", matches = "true")
class RagLiveEvaluationTest {

    @Test
    void evaluatesShadowContextsWithConfiguredLiveModel() throws IOException {
        RagLiveEvaluationSupport.LiveConfiguration live = loadConfigurationOrSkip();
        assertShadowProvenance();
        List<RagLiveEvaluationSupport.LiveCase> cases = loadCasesOrSkip();
        RagLiveEvaluationSupport.validateAgainstDataset(cases, RagEvaluationDatasetLoader.loadDefault());
        List<RagLiveEvaluationSupport.LiveCase> testCases = cases.stream()
                .filter(item -> "test".equals(item.split()))
                .toList();
        assertEquals(72, testCases.size(), "live evaluation must use the fixed test split");

        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setName("synthetic-rag-live-test");
        dataset.setItems(testCases.stream()
                .map(item -> new EvaluationDataset.EvalItem(
                        item.query(), item.referenceAnswer(), contextList(item.context())))
                .toList());

        RagasEvalConfig config = new RagasEvalConfig();
        config.setEnabled(true);
        config.setPlatform(live.platform());
        config.setBaseUrl(live.baseUrl());
        config.setApiKey(live.apiKey());
        config.setModelName(live.model());

        ChatModelFactory factory = new ChatModelFactory();
        RagasEvaluator ragas = new RagasEvaluator(factory, config);
        EvaluationReport ragasReport = ragas.evaluate(dataset);
        assertEquals(testCases.size(), ragasReport.getItemResults().size());

        RAGChecker checker = new RAGChecker(factory, config);
        List<RAGChecker.RAGCheckerReport> checkerReports = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            EvaluationReport.ItemResult generated = ragasReport.getItemResults().get(i);
            RagLiveEvaluationSupport.LiveCase input = testCases.get(i);
            if (isGenerationFailure(generated.getAnswer())) {
                checkerReports.add(generationFailureReport(input.query(), generated.getAnswer()));
            } else {
                checkerReports.add(checker.evaluate(
                        input.query(), generated.getAnswer(), contextList(input.context())));
            }
        }
        RAGChecker.RAGCheckerSummary checkerSummary = checker.summarize(checkerReports);

        Map<String, Object> report = new LinkedHashMap<>();
        boolean completeCoverage = completeJudgeCoverage(ragasReport, checkerSummary);
        report.put("status", completeCoverage ? "VALID" : "INVALID");
        report.put("evidenceLevel", "ENGINEERING_BASELINE");
        report.put("datasetHash", ShadowContractSupport.datasetHash());
        report.put("config", Map.of("platform", live.platform(), "model", live.model()));
        report.put("metrics", liveMetrics(ragasReport, checkerReports, testCases));
        report.put("coverage", Map.of(
                "ragasAttempted", ragasReport.getItemResults().size(),
                "ragasValid", validRagasItems(ragasReport),
                "checkerAttempted", checkerReports.size(),
                "checkerValid", checkerSummary.getValidSamples(),
                "checkerInvalid", checkerSummary.getInvalidSamples()));
        report.put("ragChecker", Map.of(
                "totalClaims", checkerSummary.getTotalClaims(),
                "entailed", checkerSummary.getTotalEntailed(),
                "contradicted", checkerSummary.getTotalContradicted(),
                "notFound", checkerSummary.getTotalNotFound(),
                "validSamples", checkerSummary.getValidSamples(),
                "invalidSamples", checkerSummary.getInvalidSamples()));
        report.put("errors", evaluationErrors(ragasReport, checkerReports, testCases));
        report.put("promotionGate", Map.of(
                "status", "NOT_EVALUATED",
                "reason", "LIVE_BASELINE_HAS_NO_CANDIDATE",
                "primaryMetric", "ClaimFaithfulness",
                "alpha", RagPromotionGate.ALPHA,
                "bootstrap", "paired-family-cluster",
                "resamples", 10_000));
        report.put("cost", costEstimate(live, testCases, ragasReport));

        Path directory = RagLiveEvaluationSupport.runtimeDirectory();
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("live-report.json"),
                JSON.toJSONString(report, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNulls),
                StandardCharsets.UTF_8);

        assertFalse(ragasReport.getItemResults().isEmpty());
        assertTrue(Files.isRegularFile(directory.resolve("live-report.json")));
        assertTrue(completeCoverage,
                "live evaluation is INVALID because generation or judge coverage is incomplete");
    }

    private RagLiveEvaluationSupport.LiveConfiguration loadConfigurationOrSkip() {
        try {
            return RagLiveEvaluationSupport.fromEnvironment();
        } catch (IllegalStateException e) {
            Assumptions.abort("Live evaluation is gated: " + e.getMessage());
            throw new AssertionError("unreachable");
        }
    }

    private List<RagLiveEvaluationSupport.LiveCase> loadCasesOrSkip() throws IOException {
        try {
            return RagLiveEvaluationSupport.loadCases(RagLiveEvaluationSupport.runtimeDirectory());
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("artifacts are missing")) {
                Assumptions.abort("Live evaluation is gated: " + e.getMessage());
            }
            throw e;
        }
    }

    private void assertShadowProvenance() throws IOException {
        Path reportPath = RagLiveEvaluationSupport.runtimeDirectory().resolve("shadow-report.json");
        Assumptions.assumeTrue(Files.isRegularFile(reportPath),
                "Live evaluation requires a shadow report produced by the real SUT");
        JSONObject report = JSON.parseObject(Files.readString(reportPath, StandardCharsets.UTF_8));
        assertEquals("VALID", report.getString("status"));
        assertEquals(Boolean.FALSE, report.getBoolean("vecsimRescore"));
        JSONObject nested = report.getJSONObject("report");
        assertEquals(ShadowContractSupport.datasetHash(), nested.getString("datasetHash"));
        assertTrue(nested.getString("configHash") != null && !nested.getString("configHash").isBlank());
        JSONObject shadowConfig = report.getJSONObject("config");
        assertNotNull(shadowConfig, "shadow report must contain the evaluated configuration");
        assertEquals(ShadowContractSupport.configHash(new LinkedHashMap<>(shadowConfig)),
                nested.getString("configHash"));
        try (InputStream input = RagLiveEvaluationTest.class
                .getResourceAsStream("/rag-eval/baseline.json")) {
            assertNotNull(input, "stable shadow baseline is required for live provenance");
            JSONObject baseline = JSON.parseObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(baseline.getString("configHash"), nested.getString("configHash"));
        }
        JSONObject artifacts = report.getJSONObject("artifacts");
        assertEquals(96, artifacts.getIntValue("count"));
        Path directory = RagLiveEvaluationSupport.runtimeDirectory();
        assertArtifactHash(directory.resolve("shadow-contexts.jsonl"), artifacts,
                "contexts");
        assertArtifactHash(directory.resolve("shadow-labels.jsonl"), artifacts, "labels");
    }

    private static void assertArtifactHash(Path path, JSONObject artifacts, String name) throws IOException {
        assertTrue(Files.isRegularFile(path), "missing shadow artifact: " + name);
        JSONObject artifact = artifacts.getJSONObject(name);
        assertTrue(artifact != null, "missing artifact provenance: " + name);
        assertEquals(ShadowContractSupport.sha256(path), artifact.getString("sha256"));
    }

    private static List<String> contextList(String context) {
        return context == null || context.isBlank() ? List.of() : List.of(context);
    }

    static int validRagasItems(EvaluationReport report) {
        int valid = 0;
        for (EvaluationReport.ItemResult item : report.getItemResults()) {
            if (!isGenerationFailure(item.getAnswer())
                    && item.getScores() != null && item.getScores().values().stream()
                    .allMatch(score -> score.status() == EvaluationStatus.VALID)) {
                valid++;
            }
        }
        return valid;
    }

    private static boolean completeJudgeCoverage(EvaluationReport ragas,
                                                  RAGChecker.RAGCheckerSummary checker) {
        return validRagasItems(ragas) == ragas.getItemResults().size()
                && checker.getValidSamples() == checker.getTotalSamples();
    }

    private static List<Map<String, Object>> evaluationErrors(
            EvaluationReport ragas,
            List<RAGChecker.RAGCheckerReport> checkerReports,
            List<RagLiveEvaluationSupport.LiveCase> cases) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            EvaluationReport.ItemResult item = i < ragas.getItemResults().size()
                    ? ragas.getItemResults().get(i) : null;
            RAGChecker.RAGCheckerReport checker = i < checkerReports.size()
                    ? checkerReports.get(i) : null;
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("queryId", cases.get(i).queryId());
            if (item != null && (isGenerationFailure(item.getAnswer())
                    || item.getScores() == null
                    || item.getScores().values().stream()
                    .anyMatch(score -> score.status() != EvaluationStatus.VALID))) {
                Map<String, Object> metricErrors = new LinkedHashMap<>();
                if (item.getScores() != null) {
                    item.getScores().forEach((name, score) -> {
                        if (score.status() != EvaluationStatus.VALID) {
                            Map<String, Object> detail = new LinkedHashMap<>();
                            detail.put("status", score.status().name());
                            detail.put("errorCode", score.errorCode());
                            detail.put("reason", score.reason());
                            metricErrors.put(name, detail);
                        }
                    });
                }
                if (isGenerationFailure(item.getAnswer())) {
                    metricErrors.put("generation", Map.of(
                            "status", "INVALID",
                            "errorCode", "GENERATION_ERROR",
                            "reason", "model generation failed"));
                }
                failure.put("ragas", metricErrors);
            }
            if (checker != null && checker.getStatus() != EvaluationStatus.VALID) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("status", checker.getStatus().name());
                detail.put("errorCode", checker.getErrorCode());
                detail.put("reason", checker.getReason());
                detail.put("totalClaims", checker.getTotalClaims());
                detail.put("entailed", checker.getEntailedClaims());
                detail.put("contradicted", checker.getContradictedClaims());
                detail.put("notFound", checker.getNotFoundClaims());
                failure.put("ragChecker", detail);
            }
            if (failure.size() > 1) {
                errors.add(failure);
            }
        }
        return List.copyOf(errors);
    }

    static boolean isGenerationFailure(String answer) {
        return "[GENERATION_ERROR]".equals(answer) || "[EVALUATION_ERROR]".equals(answer);
    }

    static RAGChecker.RAGCheckerReport generationFailureReport(String query, String answer) {
        RAGChecker.RAGCheckerReport report = new RAGChecker.RAGCheckerReport();
        report.setQuery(query);
        report.setAnswer(answer);
        report.setStatus(EvaluationStatus.INVALID);
        report.setErrorCode("GENERATION_ERROR");
        report.setReason("model generation failed");
        return report;
    }

    private static Map<String, Object> liveMetrics(
            EvaluationReport ragas,
            List<RAGChecker.RAGCheckerReport> checkerReports,
            List<RagLiveEvaluationSupport.LiveCase> cases) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("JudgeContextPrecision", metric(clusteredRagasMetric(
                ragas, cases, "context_precision", false)));
        metrics.put("ClaimFaithfulness", metric(clusteredCheckerMetric(checkerReports, cases)));
        metrics.put("CorrectRejectionRate", metric(clusteredRagasMetric(
                ragas, cases, "negative_rejection", true)));
        return metrics;
    }

    private static Map<String, Object> metric(ClusteredMetric value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value == null) {
            result.put("status", "NOT_EVALUATED");
            result.put("value", null);
            result.put("errorCode", "JUDGE_NO_VALID_SAMPLES");
            return result;
        }
        result.put("status", "VALID");
        result.put("value", value.interval().estimate());
        result.put("ciLow", value.interval().ciLow());
        result.put("ciHigh", value.interval().ciHigh());
        result.put("n", value.caseCount());
        result.put("clusters", value.interval().clusters());
        result.put("resamples", value.interval().resamples());
        return result;
    }

    private static ClusteredMetric clusteredRagasMetric(
            EvaluationReport report,
            List<RagLiveEvaluationSupport.LiveCase> cases,
            String metricName,
            boolean unanswerableOnly) {
        Map<String, List<Double>> valuesByFamily = new LinkedHashMap<>();
        for (int i = 0; i < cases.size(); i++) {
            if (unanswerableOnly != !cases.get(i).answerable()) {
                continue;
            }
            EvaluationReport.ItemResult item = report.getItemResults().get(i);
            EvaluationReport.MetricResult score = item.getScores() == null
                    ? null : item.getScores().get(metricName);
            if (score != null && score.status() == EvaluationStatus.VALID && score.value() != null) {
                valuesByFamily.computeIfAbsent(cases.get(i).familyId(), ignored -> new ArrayList<>())
                        .add(score.value());
            }
        }
        return clustered(valuesByFamily);
    }

    private static ClusteredMetric clusteredCheckerMetric(
            List<RAGChecker.RAGCheckerReport> reports,
            List<RagLiveEvaluationSupport.LiveCase> cases) {
        Map<String, List<Double>> valuesByFamily = new LinkedHashMap<>();
        for (int i = 0; i < reports.size(); i++) {
            RAGChecker.RAGCheckerReport report = reports.get(i);
            if (report.getStatus() == EvaluationStatus.VALID) {
                valuesByFamily.computeIfAbsent(cases.get(i).familyId(), ignored -> new ArrayList<>())
                        .add(report.getEntailedRate());
            }
        }
        return clustered(valuesByFamily);
    }

    private static ClusteredMetric clustered(Map<String, List<Double>> valuesByFamily) {
        if (valuesByFamily.isEmpty()) {
            return null;
        }
        int caseCount = valuesByFamily.values().stream().mapToInt(List::size).sum();
        return new ClusteredMetric(new FamilyClusterBootstrap().mean(valuesByFamily), caseCount);
    }

    private static RagLiveEvaluationSupport.CostEstimate cost(
            RagLiveEvaluationSupport.LiveConfiguration live,
            List<RagLiveEvaluationSupport.LiveCase> cases,
            EvaluationReport report) {
        long promptTokens = cases.stream()
                .mapToLong(item -> estimateTokens(item.query()) + estimateTokens(item.context()))
                .sum();
        long completionTokens = report.getItemResults().stream()
                .mapToLong(item -> estimateTokens(item.getAnswer()))
                .sum();
        return RagLiveEvaluationSupport.cost(live, promptTokens, completionTokens);
    }

    private static Map<String, Object> costEstimate(
            RagLiveEvaluationSupport.LiveConfiguration live,
            List<RagLiveEvaluationSupport.LiveCase> cases,
            EvaluationReport report) {
        RagLiveEvaluationSupport.CostEstimate estimate = cost(live, cases, report);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", estimate.status());
        result.put("value", estimate.value());
        return result;
    }

    private static long estimateTokens(String value) {
        return value == null || value.isBlank() ? 0L : Math.max(1L, value.codePointCount(0, value.length()) / 4L);
    }

    private record ClusteredMetric(FamilyClusterBootstrap.ConfidenceInterval interval, int caseCount) {
    }
}
