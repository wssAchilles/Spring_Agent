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

@EnabledIfSystemProperty(named = "rag.eval.candidate5.review", matches = "true")
class RagCandidate5ReviewGateTest {

    private static final Set<String> SCORE_METRICS = Set.of(
            "RetrievalAP@10", "nDCG@10", "EmptyRetrievalRate");

    @Test
    void candidateMustPassCompletedHoldoutIntegrityAndReviewGates() throws IOException {
        assertFalse(Boolean.parseBoolean(System.getProperty("rag.eval.promotion", "false")),
                "Candidate 5 review must not run as promotion");

        Path runtime = RagLiveEvaluationSupport.runtimeDirectory();
        RagCandidate5DiagnosticSupport.RuntimePaths diagnosticPaths =
                RagCandidate5DiagnosticSupport.paths(runtime);
        RagCandidate5DiagnosticSupport.verifyCompletedRun(diagnosticPaths);
        RagCandidate5DiagnosticSupport.FrozenManifests manifests =
                RagCandidate5DiagnosticSupport.loadFrozenManifests(diagnosticPaths);
        validateDiagnostic(diagnosticPaths.diagnostic());

        String holdoutDatasetHash = manifests.holdout().datasetHash();
        EvaluatedRun baseline = validateRun(
                runtime, RagCandidate5ReviewSupport.Arm.BASELINE, holdoutDatasetHash);
        EvaluatedRun candidate = validateRun(
                runtime, RagCandidate5ReviewSupport.Arm.CANDIDATE, holdoutDatasetHash);

        assertCandidate5Config(baseline.config(), false);
        assertCandidate5Config(candidate.config(), true);
        assertEquals(without(baseline.config(), "identifierRecallConsistencyEnabled"),
                without(candidate.config(), "identifierRecallConsistencyEnabled"));
        assertEquals(baseline.report().getJSONObject("metrics").keySet(),
                candidate.report().getJSONObject("metrics").keySet());
        assertEquals(baseline.traceByQuery().keySet(), candidate.traceByQuery().keySet());
        assertEquals(baseline.cases().keySet(), candidate.cases().keySet());
        assertSameCaseStructure(baseline.cases(), candidate.cases());
        assertSameScoreStructure(baseline.familyScores(), candidate.familyScores());
        assertControlCasesUnchanged(baseline.cases(), candidate.cases());

        RagPromotionGate.PrimaryEvidence primary = RagPromotionGate.primary(
                "RetrievalAP@10",
                baseline.familyScores().get("RetrievalAP@10"),
                candidate.familyScores().get("RetrievalAP@10"));
        RagPromotionGate.SecondaryEvidence ndcg = RagPromotionGate.secondary(
                "nDCG@10",
                baseline.familyScores().get("nDCG@10"),
                candidate.familyScores().get("nDCG@10"),
                0.02D);
        RagPromotionGate.SecondaryEvidence empty = RagPromotionGate.secondary(
                "EmptyRetrievalRate",
                baseline.familyScores().get("EmptyRetrievalRate"),
                candidate.familyScores().get("EmptyRetrievalRate"),
                1.0D);
        assertTrue(empty.delta().ciHigh() <= 0.0D,
                "EmptyRetrievalRate delta CI upper bound must be <= 0");

        JSONObject baselineBudget = baseline.budget();
        JSONObject candidateBudget = candidate.budget();
        assertZeroCost(baselineBudget, "baseline");
        assertZeroCost(candidateBudget, "candidate");
        assertTrue(candidateBudget.getLongValue("embeddingCalls")
                        <= baselineBudget.getLongValue("embeddingCalls"),
                "embedding calls must not increase");

        RagPromotionGate.Budget budget = new RagPromotionGate.Budget(
                (long) Math.floor(baselineBudget.getLongValue("latencyMs") * 1.10D),
                baselineBudget.getLongValue("dbCalls"),
                0L,
                0.0D);
        RagPromotionGate.ObservedBudget observed = new RagPromotionGate.ObservedBudget(
                candidateBudget.getLongValue("latencyMs"),
                candidateBudget.getLongValue("dbCalls"),
                candidateBudget.getLongValue("tokens"),
                OptionalDouble.of(candidateBudget.getDoubleValue("costUsd")));
        RagPromotionGate.Decision decision = RagPromotionGate.evaluate(
                List.of(primary), List.of(ndcg), budget, observed, true);
        assertTrue(decision.passed(), () -> "Candidate 5 review gate failed: "
                + decision.failures());
    }

    private static void validateDiagnostic(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "missing Candidate 5 diagnostic: " + path);
        JSONObject diagnostic = read(path);
        assertEquals("VALID", diagnostic.getString("status"));
        assertEquals("PROCEED_TO_IDENTIFIER_RECALL_RED", diagnostic.getString("decision"));
        assertNull(diagnostic.getString("errorCode"));
    }

    private static EvaluatedRun validateRun(
            Path runtime,
            RagCandidate5ReviewSupport.Arm arm,
            String expectedDatasetHash) throws IOException {
        Path directory = arm.bundleDirectory(runtime).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(directory), "missing Candidate 5 bundle: " + directory);
        assertFalse(Files.isSymbolicLink(directory), "bundle must not be a symlink: " + directory);
        Path reportPath = inside(directory, arm.reportFile());
        assertTrue(Files.isRegularFile(reportPath), "missing Candidate 5 report: " + reportPath);

        JSONObject root = read(reportPath);
        assertEquals("VALID", root.getString("status"), arm.reportFile());
        JSONObject config = requiredObject(root, "config", arm.reportFile());
        JSONObject report = requiredObject(root, "report", arm.reportFile());
        JSONObject budget = requiredObject(root, "observedBudget", arm.reportFile());
        JSONObject artifacts = requiredObject(root, "artifacts", arm.reportFile());
        requiredObject(root, "familyScores", arm.reportFile());
        requiredObject(report, "metrics", arm.reportFile());

        String configHash = ShadowContractSupport.configHash(new LinkedHashMap<>(config));
        assertEquals(configHash, report.getString("configHash"), arm.reportFile());
        assertEquals(expectedDatasetHash, report.getString("datasetHash"), arm.reportFile());
        validateBudget(budget, arm.reportFile());

        assertEquals(RagCandidate5ReviewSupport.CASE_COUNT,
                artifacts.getIntValue("count"), arm.reportFile());
        Path contextsPath = validateArtifact(
                directory, artifacts, "contexts", arm.contextsFile(), arm.reportFile());
        Path labelsPath = validateArtifact(
                directory, artifacts, "labels", arm.labelsFile(), arm.reportFile());
        Path tracePath = validateTraceArtifact(
                directory, artifacts, arm.traceFile(), arm.reportFile());

        Map<String, JSONObject> contexts = readJsonLines(contextsPath, arm.reportFile());
        Map<String, JSONObject> labels = readJsonLines(labelsPath, arm.reportFile());
        assertEquals(RagCandidate5ReviewSupport.CASE_COUNT, contexts.size(), arm.reportFile());
        assertEquals(contexts.keySet(), labels.keySet(), arm.reportFile());
        Map<String, CaseEvidence> cases = validateCases(contexts, labels, arm.reportFile());

        Map<String, JSONObject> traceByQuery = validateTrace(
                tracePath, budget, arm.reportFile());
        assertEquals(cases.keySet(), traceByQuery.keySet(), arm.reportFile());

        Map<String, Map<String, List<Double>>> scores = familyScores(root, arm.reportFile());
        validateManifest(
                directory, arm, expectedDatasetHash, configHash,
                List.of(reportPath, tracePath, contextsPath, labelsPath));
        return new EvaluatedRun(report, config, budget, traceByQuery, cases, scores);
    }

    private static JSONObject requiredObject(JSONObject root, String key, String label) {
        JSONObject value = root.getJSONObject(key);
        assertNotNull(value, label + ":" + key);
        return value;
    }

    private static Path validateArtifact(
            Path directory,
            JSONObject artifacts,
            String key,
            String expectedName,
            String reportName) {
        JSONObject artifact = requiredObject(artifacts, key, reportName);
        assertEquals(Set.of("file", "sha256"), artifact.keySet(), reportName + ":" + key);
        assertEquals(expectedName, artifact.getString("file"), reportName + ":" + key);
        Path path = inside(directory, expectedName);
        assertTrue(Files.isRegularFile(path), "missing Candidate 5 artifact: " + path);
        assertFalse(Files.isSymbolicLink(path), "artifact must not be a symlink: " + path);
        assertEquals(ShadowContractSupport.sha256(path), artifact.getString("sha256"),
                reportName + ":" + key);
        return path;
    }

    private static Path validateTraceArtifact(
            Path directory, JSONObject artifacts, String expectedName, String reportName) {
        JSONObject artifact = requiredObject(artifacts, "dbCallTrace", reportName);
        assertEquals(Set.of("file", "sha256", "count"), artifact.keySet(),
                reportName + ":dbCallTrace");
        assertEquals(expectedName, artifact.getString("file"), reportName);
        assertEquals(RagCandidate5ReviewSupport.CASE_COUNT,
                artifact.getIntValue("count"), reportName);
        Path path = inside(directory, expectedName);
        assertTrue(Files.isRegularFile(path), "missing Candidate 5 trace: " + path);
        assertFalse(Files.isSymbolicLink(path), "trace must not be a symlink: " + path);
        assertEquals(ShadowContractSupport.sha256(path), artifact.getString("sha256"), reportName);
        return path;
    }

    private static Map<String, JSONObject> validateTrace(
            Path path, JSONObject budget, String reportName) throws IOException {
        JSONArray serialized = JSON.parseArray(Files.readString(path, StandardCharsets.UTF_8));
        assertEquals(RagCandidate5ReviewSupport.CASE_COUNT, serialized.size(), reportName);
        Map<String, JSONObject> byQuery = new LinkedHashMap<>();
        long calls = 0L;
        for (int index = 0; index < serialized.size(); index++) {
            JSONObject item = serialized.getJSONObject(index);
            assertNotNull(item, reportName + ":trace[" + index + "]");
            String queryId = requiredString(item, "queryId", reportName);
            assertNull(byQuery.put(queryId, item), "duplicate trace queryId: " + queryId);
            assertTrue(item.containsKey("total"), reportName + ":trace total");
            long total = item.getLongValue("total");
            assertTrue(total >= 0L, reportName + ":trace total");
            calls += total;
        }
        assertEquals(RagCandidate5ReviewSupport.CASE_COUNT, byQuery.size(), reportName);
        assertEquals(budget.getLongValue("dbCalls"), calls, reportName);
        return byQuery;
    }

    private static Map<String, CaseEvidence> validateCases(
            Map<String, JSONObject> contexts,
            Map<String, JSONObject> labels,
            String reportName) {
        Map<String, CaseEvidence> cases = new LinkedHashMap<>();
        for (Map.Entry<String, JSONObject> entry : contexts.entrySet()) {
            String queryId = entry.getKey();
            JSONObject context = entry.getValue();
            JSONObject label = labels.get(queryId);
            CaseMetadata contextMetadata = metadata(context, reportName + ":contexts:" + queryId);
            CaseMetadata labelMetadata = metadata(label, reportName + ":labels:" + queryId);
            assertEquals(contextMetadata, labelMetadata, reportName + ":" + queryId);

            String text = context.getString("context");
            assertNotNull(text, reportName + ":context:" + queryId);
            assertTrue(context.containsKey("contextEmpty"), reportName + ":" + queryId);
            boolean contextEmpty = context.getBooleanValue("contextEmpty");
            assertEquals(text.isEmpty(), contextEmpty, reportName + ":" + queryId);
            String contextSha256 = requiredString(
                    context, "contextSha256", reportName + ":" + queryId);
            assertEquals(ShadowContractSupport.sha256(text.getBytes(StandardCharsets.UTF_8)),
                    contextSha256, reportName + ":" + queryId);

            List<String> sourceIds = strings(
                    context.getJSONArray("sourceSegmentIds"), reportName + ":" + queryId);
            List<Double> sourceScores = numbers(
                    context.getJSONArray("sourceScores"), reportName + ":" + queryId);
            assertEquals(sourceIds.size(), sourceScores.size(), reportName + ":" + queryId);
            cases.put(queryId, new CaseEvidence(
                    contextMetadata, sourceIds, sourceScores, contextSha256, contextEmpty));
        }
        assertEquals(RagCandidate5ReviewSupport.CASE_COUNT, cases.size(), reportName);
        return cases;
    }

    private static CaseMetadata metadata(JSONObject value, String label) {
        String familyId = requiredString(value, "familyId", label);
        String language = requiredString(value, "language", label);
        String split = requiredString(value, "split", label);
        assertTrue(value.containsKey("answerable"), label + ":answerable");
        Boolean answerable = value.getBoolean("answerable");
        assertNotNull(answerable, label + ":answerable");
        List<String> strata = strings(value.getJSONArray("strata"), label + ":strata");
        assertEquals(strata.stream().distinct().sorted().toList(), strata, label + ":strata");
        return new CaseMetadata(familyId, language, split, answerable, strata);
    }

    private static List<String> strings(JSONArray values, String label) {
        assertNotNull(values, label);
        List<String> result = new ArrayList<>();
        for (Object raw : values) {
            assertTrue(raw instanceof String && !((String) raw).isBlank(), label);
            assertFalse("null".equals(raw), label);
            result.add((String) raw);
        }
        return List.copyOf(result);
    }

    private static List<Double> numbers(JSONArray values, String label) {
        assertNotNull(values, label);
        List<Double> result = new ArrayList<>();
        for (Object raw : values) {
            assertTrue(raw instanceof Number, label);
            double value = ((Number) raw).doubleValue();
            assertTrue(Double.isFinite(value), label);
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static void validateManifest(
            Path directory,
            RagCandidate5ReviewSupport.Arm arm,
            String expectedDatasetHash,
            String expectedConfigHash,
            List<Path> artifacts) throws IOException {
        Path path = inside(directory, "manifest.json");
        assertTrue(Files.isRegularFile(path), "missing Candidate 5 manifest: " + path);
        assertFalse(Files.isSymbolicLink(path), "manifest must not be a symlink: " + path);
        JSONObject manifest = read(path);
        assertEquals("COMPLETE", manifest.getString("status"), arm.reportFile());
        assertEquals(arm.id(), manifest.getString("arm"), arm.reportFile());
        assertEquals(expectedDatasetHash, manifest.getString("datasetHash"), arm.reportFile());
        assertEquals(expectedConfigHash, manifest.getString("configHash"), arm.reportFile());
        JSONObject files = requiredObject(manifest, "files", arm.reportFile());
        assertEquals(new LinkedHashSet<>(arm.fileNames()), files.keySet(), arm.reportFile());
        for (Path artifact : artifacts) {
            assertEquals(ShadowContractSupport.sha256(artifact),
                    files.getString(artifact.getFileName().toString()),
                    arm.reportFile() + ":" + artifact.getFileName());
        }
    }

    private static Map<String, Map<String, List<Double>>> familyScores(
            JSONObject root, String reportName) {
        JSONObject serialized = requiredObject(root, "familyScores", reportName);
        assertEquals(SCORE_METRICS, serialized.keySet(), reportName);
        Map<String, Map<String, List<Double>>> scores = new LinkedHashMap<>();
        for (String metric : List.of(
                "RetrievalAP@10", "nDCG@10", "EmptyRetrievalRate")) {
            JSONObject byFamily = requiredObject(serialized, metric, reportName);
            Map<String, List<Double>> values = new LinkedHashMap<>();
            byFamily.forEach((family, raw) -> {
                assertTrue(family != null && !family.isBlank(), reportName + ":" + metric);
                assertTrue(raw instanceof List<?>, reportName + ":" + metric + ":" + family);
                List<Double> observations = new ArrayList<>();
                for (Object value : (List<?>) raw) {
                    assertTrue(value instanceof Number,
                            reportName + ":" + metric + ":" + family);
                    double observation = ((Number) value).doubleValue();
                    assertTrue(Double.isFinite(observation),
                            reportName + ":" + metric + ":" + family);
                    observations.add(observation);
                }
                assertFalse(observations.isEmpty(), reportName + ":" + metric + ":" + family);
                values.put(family, List.copyOf(observations));
            });
            assertFalse(values.isEmpty(), reportName + ":" + metric);
            scores.put(metric, values);
        }
        return scores;
    }

    private static void assertCandidate5Config(JSONObject config, boolean enabled) {
        assertEquals(Boolean.FALSE, config.getBoolean("identifierAware"));
        assertEquals(Boolean.TRUE, config.getBoolean("identifierConsistencyEnabled"));
        assertEquals(Boolean.valueOf(enabled),
                config.getBoolean("identifierRecallConsistencyEnabled"));
        assertEquals(RagCandidate5ReviewSupport.ALGORITHM,
                config.getString("identifierRecallConsistencyAlgorithm"));
        assertEquals(RagCandidate5ReviewSupport.SQL_POLICY,
                config.getString("identifierRecallConsistencySqlPolicy"));
    }

    private static void assertSameCaseStructure(
            Map<String, CaseEvidence> baseline, Map<String, CaseEvidence> candidate) {
        for (String queryId : baseline.keySet()) {
            assertEquals(baseline.get(queryId).metadata(), candidate.get(queryId).metadata(),
                    queryId);
        }
    }

    private static void assertControlCasesUnchanged(
            Map<String, CaseEvidence> baseline, Map<String, CaseEvidence> candidate) {
        int controls = 0;
        for (Map.Entry<String, CaseEvidence> entry : baseline.entrySet()) {
            if (!entry.getValue().metadata().strata().contains("candidate5-control")) {
                continue;
            }
            controls++;
            CaseEvidence actual = candidate.get(entry.getKey());
            assertEquals(entry.getValue().sourceSegmentIds(), actual.sourceSegmentIds(),
                    entry.getKey() + ":sourceSegmentIds");
            assertEquals(entry.getValue().sourceScores(), actual.sourceScores(),
                    entry.getKey() + ":sourceScores");
            assertEquals(entry.getValue().contextSha256(), actual.contextSha256(),
                    entry.getKey() + ":contextSha256");
            assertEquals(entry.getValue().contextEmpty(), actual.contextEmpty(),
                    entry.getKey() + ":contextEmpty");
        }
        assertEquals(8, controls, "Candidate 5 holdout must contain eight control cases");
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

    private static void assertZeroCost(JSONObject budget, String label) {
        assertEquals(0L, budget.getLongValue("tokens"), label);
        assertEquals(0.0D, budget.getDoubleValue("costUsd"), label);
    }

    private static void validateBudget(JSONObject budget, String label) {
        for (String field : List.of(
                "latencyMs", "dbCalls", "embeddingCalls", "tokens", "costUsd")) {
            assertTrue(budget.containsKey(field), label + ":" + field);
            assertTrue(budget.getDoubleValue(field) >= 0.0D, label + ":" + field);
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

    private static Map<String, JSONObject> readJsonLines(Path path, String label)
            throws IOException {
        Map<String, JSONObject> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            JSONObject value = JSON.parseObject(line);
            String queryId = requiredString(value, "queryId", label);
            assertNull(values.put(queryId, value), "duplicate artifact queryId: " + queryId);
        }
        return values;
    }

    private static String requiredString(JSONObject value, String key, String label) {
        String result = value.getString(key);
        assertTrue(result != null && !result.isBlank(), label + ":" + key);
        return result;
    }

    private static Path inside(Path directory, String fileName) {
        Path path = directory.resolve(fileName).toAbsolutePath().normalize();
        assertEquals(directory, path.getParent(), "artifact path escapes bundle");
        return path;
    }

    private static JSONObject read(Path path) throws IOException {
        return JSON.parseObject(Files.readString(path, StandardCharsets.UTF_8));
    }

    private record CaseMetadata(
            String familyId,
            String language,
            String split,
            boolean answerable,
            List<String> strata) {
    }

    private record CaseEvidence(
            CaseMetadata metadata,
            List<String> sourceSegmentIds,
            List<Double> sourceScores,
            String contextSha256,
            boolean contextEmpty) {
    }

    private record EvaluatedRun(
            JSONObject report,
            JSONObject config,
            JSONObject budget,
            Map<String, JSONObject> traceByQuery,
            Map<String, CaseEvidence> cases,
            Map<String, Map<String, List<Double>>> familyScores) {
    }
}
