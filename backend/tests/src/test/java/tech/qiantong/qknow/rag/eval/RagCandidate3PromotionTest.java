package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "rag.eval.promotion", matches = "true")
class RagCandidate3PromotionTest {

    private static final String ALGORITHM = "document-name-exact-identifier-v1";
    private static final String SCORE_POLICY =
            "deterministic-rank-score-preserving-v1";

    @Test
    void candidateMustPassCalibrationIntegrityAndPairedPromotionGates() throws IOException {
        Path runtime = RagLiveEvaluationSupport.runtimeDirectory();
        JSONObject diagnostic = validateDiagnostic(
                runtime.resolve("candidate3-calibration-diagnostic.json"));
        EvaluatedRun baseline = validateRun(
                runtime.resolve("bundles/candidate3-baseline-run1"),
                "candidate3-baseline.json", "candidate3-baseline-db-calls.json", "A");
        EvaluatedRun candidate = validateRun(
                runtime.resolve("bundles/candidate3-run1"),
                "candidate3.json", "candidate3-db-calls.json", "C");

        String datasetHash = diagnostic.getString("datasetHash");
        assertEquals(datasetHash, baseline.report().getString("datasetHash"));
        assertEquals(datasetHash, candidate.report().getString("datasetHash"));
        assertEquals(Boolean.FALSE, baseline.config().getBoolean("identifierAware"));
        assertEquals(Boolean.FALSE, candidate.config().getBoolean("identifierAware"));
        assertEquals(Boolean.FALSE,
                baseline.config().getBoolean("identifierConsistencyEnabled"));
        assertEquals(Boolean.TRUE,
                candidate.config().getBoolean("identifierConsistencyEnabled"));
        assertEquals(without(baseline.config(), "identifierConsistencyEnabled"),
                without(candidate.config(), "identifierConsistencyEnabled"));
        assertEquals(baseline.report().getJSONObject("metrics").keySet(),
                candidate.report().getJSONObject("metrics").keySet());
        assertEquals(baseline.traceByQuery().keySet(), candidate.traceByQuery().keySet());

        Map<String, Map<String, List<Double>>> baselineScores = familyScores(baseline.root());
        Map<String, Map<String, List<Double>>> candidateScores = familyScores(candidate.root());
        assertSameScoreStructure(baselineScores, candidateScores);

        RagPromotionGate.PrimaryEvidence primary = RagPromotionGate.primary(
                "RetrievalAP@10", baselineScores.get("RetrievalAP@10"),
                candidateScores.get("RetrievalAP@10"));
        RagPromotionGate.SecondaryEvidence ndcg = RagPromotionGate.secondary(
                "nDCG@10", baselineScores.get("nDCG@10"),
                candidateScores.get("nDCG@10"), 0.02D);
        RagPromotionGate.SecondaryEvidence emptyDelta = RagPromotionGate.secondary(
                "EmptyRetrievalRate", baselineScores.get("EmptyRetrievalRate"),
                candidateScores.get("EmptyRetrievalRate"), 1.0D);
        assertTrue(emptyDelta.delta().ciHigh() <= 0.0D,
                "EmptyRetrievalRate delta CI upper bound must be <= 0");

        JSONObject baselineBudget = baseline.budget();
        JSONObject candidateBudget = candidate.budget();
        assertTrue(baselineBudget.containsKey("costUsd")
                && candidateBudget.containsKey("costUsd"));
        assertEquals(0L, baselineBudget.getLongValue("tokens"));
        assertEquals(0L, candidateBudget.getLongValue("tokens"));
        assertEquals(0.0D, baselineBudget.getDoubleValue("costUsd"));
        assertEquals(0.0D, candidateBudget.getDoubleValue("costUsd"));
        assertTrue(candidateBudget.getLongValue("embeddingCalls")
                        <= baselineBudget.getLongValue("embeddingCalls"),
                "embedding calls must not increase");

        RagPromotionGate.Budget budget = new RagPromotionGate.Budget(
                (long) Math.ceil(baselineBudget.getLongValue("latencyMs") * 1.10D),
                baselineBudget.getLongValue("dbCalls"), 0L, 0.0D);
        RagPromotionGate.ObservedBudget observed = new RagPromotionGate.ObservedBudget(
                candidateBudget.getLongValue("latencyMs"),
                candidateBudget.getLongValue("dbCalls"),
                candidateBudget.getLongValue("tokens"),
                OptionalDouble.of(candidateBudget.getDoubleValue("costUsd")));
        RagPromotionGate.Decision decision = RagPromotionGate.evaluate(
                List.of(primary), List.of(ndcg), budget, observed, true);
        assertTrue(decision.passed(), () -> "promotion gate failed: " + decision.failures());
    }

    private static JSONObject validateDiagnostic(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "missing calibration diagnostic: " + path);
        JSONObject root = read(path);
        assertEquals("VALID", root.getString("status"));
        assertEquals("PROCEED_IDENTIFIER_CONSISTENCY", root.getString("decision"));
        assertNull(root.getString("errorCode"));
        JSONObject config = root.getJSONObject("config");
        assertNotNull(config);
        assertEquals(ShadowContractSupport.configHash(new LinkedHashMap<>(config)),
                root.getString("configHash"));
        assertEquals(ShadowContractSupport.datasetHash(), root.getString("datasetHash"));
        assertCandidate3Config(config);
        assertEquals(Boolean.FALSE, config.getBoolean("identifierAware"));
        return root;
    }

    private static EvaluatedRun validateRun(
            Path directory, String reportName, String traceName, String arm) throws IOException {
        Path reportPath = directory.resolve(reportName);
        assertTrue(Files.isRegularFile(reportPath), "missing promotion report: " + reportPath);
        JSONObject root = read(reportPath);
        assertEquals("VALID", root.getString("status"), reportName);
        JSONObject config = root.getJSONObject("config");
        JSONObject report = root.getJSONObject("report");
        JSONObject budget = root.getJSONObject("observedBudget");
        assertNotNull(config, reportName);
        assertNotNull(report, reportName);
        assertNotNull(budget, reportName);
        assertEquals(ShadowContractSupport.configHash(new LinkedHashMap<>(config)),
                report.getString("configHash"), reportName);
        assertEquals(ShadowContractSupport.datasetHash(),
                report.getString("datasetHash"), reportName);
        assertFalse(config.containsKey("compare-stable"));
        assertFalse(config.containsKey("report-file"));
        assertCandidate3Config(config);

        JSONObject artifacts = root.getJSONObject("artifacts");
        assertEquals(96, artifacts.getIntValue("count"), reportName);
        Path contexts = validateArtifact(directory, artifacts, "contexts",
                "shadow-contexts.jsonl", reportName);
        Path labels = validateArtifact(directory, artifacts, "labels",
                "shadow-labels.jsonl", reportName);
        JSONObject traceArtifact = artifacts.getJSONObject("dbCallTrace");
        assertEquals(traceName, traceArtifact.getString("file"), reportName);
        assertEquals(96, traceArtifact.getIntValue("count"), reportName);
        Path trace = directory.resolve(traceName).normalize();
        assertInside(directory, trace);
        assertTrue(Files.isRegularFile(trace), "missing trace: " + trace);
        assertEquals(traceArtifact.getString("sha256"),
                ShadowContractSupport.sha256(trace), reportName);

        JSONArray serializedTrace = JSON.parseArray(Files.readString(
                trace, StandardCharsets.UTF_8));
        assertEquals(96, serializedTrace.size(), reportName);
        Map<String, JSONObject> traceByQuery = new LinkedHashMap<>();
        long tracedCalls = 0L;
        for (Object raw : serializedTrace) {
            JSONObject item = (JSONObject) raw;
            String queryId = item.getString("queryId");
            assertNotNull(queryId, reportName);
            assertNull(traceByQuery.put(queryId, item), "duplicate queryId: " + queryId);
            tracedCalls += item.getLongValue("total");
        }
        assertEquals(96, traceByQuery.size(), reportName);
        assertEquals(budget.getLongValue("dbCalls"), tracedCalls, reportName);

        List<RagLiveEvaluationSupport.LiveCase> cases =
                RagLiveEvaluationSupport.loadCases(directory);
        RagLiveEvaluationSupport.validateAgainstDataset(
                cases, RagEvaluationDatasetLoader.loadDefault());
        assertEquals(96, cases.stream().map(RagLiveEvaluationSupport.LiveCase::queryId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)).size());

        Path manifestPath = directory.resolve("manifest.json");
        assertTrue(Files.isRegularFile(manifestPath), "missing manifest: " + manifestPath);
        JSONObject manifest = read(manifestPath);
        assertEquals("COMPLETE", manifest.getString("status"), reportName);
        assertEquals(arm, manifest.getString("arm"), reportName);
        JSONObject files = manifest.getJSONObject("files");
        Set<String> expectedFiles = Set.of(
                reportName, traceName, "shadow-contexts.jsonl", "shadow-labels.jsonl");
        assertEquals(expectedFiles, files.keySet(), reportName);
        for (Path artifact : List.of(reportPath, trace, contexts, labels)) {
            assertEquals(ShadowContractSupport.sha256(artifact),
                    files.getString(artifact.getFileName().toString()),
                    reportName + ":" + artifact.getFileName());
        }
        return new EvaluatedRun(root, report, config, budget, traceByQuery);
    }

    private static Path validateArtifact(
            Path directory, JSONObject artifacts, String key,
            String expectedName, String reportName) {
        JSONObject artifact = artifacts.getJSONObject(key);
        assertNotNull(artifact, reportName + ":" + key);
        assertEquals(expectedName, artifact.getString("file"), reportName + ":" + key);
        Path path = directory.resolve(expectedName).normalize();
        assertInside(directory, path);
        assertTrue(Files.isRegularFile(path), "missing artifact: " + path);
        assertEquals(artifact.getString("sha256"),
                ShadowContractSupport.sha256(path), reportName + ":" + key);
        return path;
    }

    private static void assertInside(Path directory, Path path) {
        assertEquals(directory.toAbsolutePath().normalize(),
                path.toAbsolutePath().getParent());
    }

    private static void assertCandidate3Config(JSONObject config) {
        assertEquals(ALGORITHM, config.getString("identifierConsistencyAlgorithm"));
        assertEquals(SCORE_POLICY, config.getString("identifierConsistencyScorePolicy"));
    }

    private static void assertSameScoreStructure(
            Map<String, Map<String, List<Double>>> baseline,
            Map<String, Map<String, List<Double>>> candidate) {
        assertEquals(baseline.keySet(), candidate.keySet());
        for (String metric : baseline.keySet()) {
            assertEquals(baseline.get(metric).keySet(), candidate.get(metric).keySet(), metric);
            for (String family : baseline.get(metric).keySet()) {
                assertEquals(baseline.get(metric).get(family).size(),
                        candidate.get(metric).get(family).size(), metric + ":" + family);
            }
        }
    }

    private static Map<String, Object> without(JSONObject config, String excludedKey) {
        Map<String, Object> values = new LinkedHashMap<>();
        config.forEach((key, value) -> {
            if (!excludedKey.equals(key)) {
                values.put(key, value);
            }
        });
        return values;
    }

    private static Map<String, Map<String, List<Double>>> familyScores(JSONObject root) {
        JSONObject serialized = root.getJSONObject("familyScores");
        Map<String, Map<String, List<Double>>> scores = new LinkedHashMap<>();
        for (String metric : List.of(
                "RetrievalAP@10", "nDCG@10", "EmptyRetrievalRate")) {
            JSONObject byFamily = serialized.getJSONObject(metric);
            assertNotNull(byFamily, metric);
            Map<String, List<Double>> values = new LinkedHashMap<>();
            byFamily.forEach((family, raw) -> {
                List<Double> observations = new ArrayList<>();
                for (Object value : (List<?>) raw) {
                    observations.add(((Number) value).doubleValue());
                }
                values.put(family, observations);
            });
            scores.put(metric, values);
        }
        return scores;
    }

    private static JSONObject read(Path path) throws IOException {
        return JSON.parseObject(Files.readString(path, StandardCharsets.UTF_8));
    }

    private record EvaluatedRun(
            JSONObject root,
            JSONObject report,
            JSONObject config,
            JSONObject budget,
            Map<String, JSONObject> traceByQuery) {
    }
}
