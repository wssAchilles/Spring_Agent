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
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "rag.eval.promotion", matches = "true")
class RagKeywordIdentifierPromotionTest {

    @Test
    void candidateMustPassPairedPromotionGate() throws IOException {
        Path bundles = RagLiveEvaluationSupport.runtimeDirectory().resolve("bundles");
        EvaluatedRun baseline = validateRun(
                bundles.resolve("identifier-current-baseline-run1"),
                "identifier-current-baseline.json", "identifier-current-baseline-db-calls.json");
        EvaluatedRun candidate = validateRun(
                bundles.resolve("identifier-current-candidate-run1"),
                "identifier-current-candidate.json", "identifier-current-candidate-db-calls.json");

        assertEquals(baseline.report().getString("datasetHash"), candidate.report().getString("datasetHash"));
        assertEquals(Boolean.FALSE, baseline.config().getBoolean("identifierAware"));
        assertEquals(Boolean.TRUE, candidate.config().getBoolean("identifierAware"));
        assertEquals(without(baseline.config(), "identifierAware"),
                without(candidate.config(), "identifierAware"));

        Map<String, Map<String, List<Double>>> baselineScores = familyScores(baseline.root());
        Map<String, Map<String, List<Double>>> candidateScores = familyScores(candidate.root());
        assertEquals(baselineScores.keySet(), candidateScores.keySet());
        for (String metric : baselineScores.keySet()) {
            assertEquals(baselineScores.get(metric).keySet(), candidateScores.get(metric).keySet(), metric);
            for (String family : baselineScores.get(metric).keySet()) {
                assertEquals(baselineScores.get(metric).get(family).size(),
                        candidateScores.get(metric).get(family).size(), metric + ":" + family);
            }
        }

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
        assertTrue(baselineBudget.containsKey("costUsd") && candidateBudget.containsKey("costUsd"));
        assertEquals(0L, baselineBudget.getLongValue("tokens"));
        assertEquals(0L, candidateBudget.getLongValue("tokens"));
        long baselineLatency = baselineBudget.getLongValue("latencyMs");
        long maxLatency = (long) Math.ceil(baselineLatency * 1.10D);
        RagPromotionGate.Budget budget = new RagPromotionGate.Budget(
                maxLatency, baselineBudget.getLongValue("dbCalls"), 0L, 0.0D);
        RagPromotionGate.ObservedBudget observed = new RagPromotionGate.ObservedBudget(
                candidateBudget.getLongValue("latencyMs"),
                candidateBudget.getLongValue("dbCalls"),
                candidateBudget.getLongValue("tokens"),
                OptionalDouble.of(candidateBudget.getDoubleValue("costUsd")));
        RagPromotionGate.Decision decision = RagPromotionGate.evaluate(
                List.of(primary), List.of(ndcg), budget, observed, true);

        assertTrue(candidateBudget.getLongValue("embeddingCalls")
                        <= baselineBudget.getLongValue("embeddingCalls"),
                "embedding calls must not increase");
        assertEquals(0.0D, baselineBudget.getDoubleValue("costUsd"));
        assertEquals(0.0D, candidateBudget.getDoubleValue("costUsd"));
        assertTrue(decision.passed(), () -> "promotion gate failed: " + decision.failures());
    }

    private static JSONObject read(Path path) throws IOException {
        return JSON.parseObject(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static EvaluatedRun validateRun(Path directory, String reportName,
                                            String expectedTraceName) throws IOException {
        Path reportPath = directory.resolve(reportName);
        assertTrue(Files.isRegularFile(reportPath), "missing promotion report: " + reportName);
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
        assertFalse(config.containsKey("compare-stable"));
        assertFalse(config.containsKey("report-file"));

        JSONObject artifacts = root.getJSONObject("artifacts");
        assertEquals(96, artifacts.getIntValue("count"), reportName);
        Path contextsPath = validateLineArtifact(directory, artifacts, "contexts", reportName);
        Path labelsPath = validateLineArtifact(directory, artifacts, "labels", reportName);
        JSONObject traceArtifact = artifacts.getJSONObject("dbCallTrace");
        assertEquals(expectedTraceName, traceArtifact.getString("file"), reportName);
        assertEquals(96, traceArtifact.getIntValue("count"), reportName);
        Path tracePath = directory.resolve(traceArtifact.getString("file")).normalize();
        assertEquals(directory.toAbsolutePath().normalize(), tracePath.toAbsolutePath().getParent());
        assertTrue(Files.isRegularFile(tracePath), "missing trace: " + tracePath);
        assertEquals(traceArtifact.getString("sha256"), ShadowContractSupport.sha256(tracePath));

        JSONArray serializedTrace = JSON.parseArray(Files.readString(tracePath, StandardCharsets.UTF_8));
        assertEquals(96, serializedTrace.size(), reportName);
        Map<String, JSONObject> traceByQuery = new LinkedHashMap<>();
        long tracedCalls = 0L;
        for (Object raw : serializedTrace) {
            JSONObject trace = (JSONObject) raw;
            String queryId = trace.getString("queryId");
            assertNotNull(queryId, reportName);
            assertNull(traceByQuery.put(queryId, trace), "duplicate queryId: " + queryId);
            tracedCalls += trace.getLongValue("total");
        }
        assertEquals(96, traceByQuery.size(), reportName);
        assertEquals(budget.getLongValue("dbCalls"), tracedCalls, reportName);

        JSONObject manifest = read(directory.resolve("manifest.json"));
        assertEquals("COMPLETE", manifest.getString("status"), reportName);
        JSONObject manifestFiles = manifest.getJSONObject("files");
        for (Path artifact : List.of(reportPath, tracePath, contextsPath, labelsPath)) {
            assertEquals(ShadowContractSupport.sha256(artifact),
                    manifestFiles.getString(artifact.getFileName().toString()),
                    reportName + ":" + artifact.getFileName());
        }
        return new EvaluatedRun(root, report, config, budget, traceByQuery);
    }

    private static Path validateLineArtifact(Path directory, JSONObject artifacts,
                                             String name, String reportName) throws IOException {
        JSONObject artifact = artifacts.getJSONObject(name);
        assertNotNull(artifact, reportName + ":" + name);
        Path path = directory.resolve(artifact.getString("file")).normalize();
        assertEquals(directory.toAbsolutePath().normalize(), path.toAbsolutePath().getParent());
        assertTrue(Files.isRegularFile(path), "missing artifact: " + path);
        assertEquals(artifact.getString("sha256"), ShadowContractSupport.sha256(path));
        assertEquals(96L, Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank()).count(), reportName + ":" + name);
        return path;
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
        for (String metric : List.of("RetrievalAP@10", "nDCG@10", "EmptyRetrievalRate")) {
            JSONObject byFamily = serialized.getJSONObject(metric);
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

    private record EvaluatedRun(JSONObject root, JSONObject report, JSONObject config,
                                JSONObject budget, Map<String, JSONObject> traceByQuery) {
    }
}
