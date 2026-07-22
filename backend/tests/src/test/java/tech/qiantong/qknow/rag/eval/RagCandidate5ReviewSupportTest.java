package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCandidate5ReviewSupportTest {

    @TempDir
    Path temp;

    private final String configuredArm = System.getProperty(
            RagCandidate5ReviewSupport.REVIEW_ARM_PROPERTY);

    @AfterEach
    void restoreReviewArm() {
        if (configuredArm == null) {
            System.clearProperty(RagCandidate5ReviewSupport.REVIEW_ARM_PROPERTY);
        } else {
            System.setProperty(RagCandidate5ReviewSupport.REVIEW_ARM_PROPERTY, configuredArm);
        }
    }

    @Test
    void rejectedCandidateCannotRunNewReviewArms() {
        System.clearProperty(RagCandidate5ReviewSupport.REVIEW_ARM_PROPERTY);
        assertEquals(null, RagCandidate5ReviewSupport.configuredArm());

        System.setProperty(RagCandidate5ReviewSupport.REVIEW_ARM_PROPERTY, " A ");
        assertEquals("CANDIDATE5_REVIEW_EXECUTION_DISABLED_AFTER_REJECTION",
                assertThrows(IllegalStateException.class,
                        RagCandidate5ReviewSupport::configuredArm).getMessage());
        System.setProperty(RagCandidate5ReviewSupport.REVIEW_ARM_PROPERTY, "B");
        assertEquals("CANDIDATE5_REVIEW_EXECUTION_DISABLED_AFTER_REJECTION",
                assertThrows(IllegalStateException.class,
                        RagCandidate5ReviewSupport::configuredArm).getMessage());
        System.setProperty(RagCandidate5ReviewSupport.REVIEW_ARM_PROPERTY, "baseline");
        assertEquals("CANDIDATE5_REVIEW_ARM_INVALID",
                assertThrows(IllegalArgumentException.class,
                        RagCandidate5ReviewSupport::configuredArm).getMessage());
    }

    @Test
    void reportUsesHoldoutDatasetHashInsteadOfAnySelectionHash() {
        ReviewFixture fixture = fixture("custom-holdout-dataset-hash");

        RagBenchmarkReport report = RagCandidate5ReviewSupport.buildReport(
                fixture.frozen(), fixture.results(), config(false));

        assertEquals("custom-holdout-dataset-hash", report.datasetHash());
        assertFalse("selection-dataset-hash".equals(report.datasetHash()));
    }

    @Test
    void rejectsDuplicateTraceQueryIdsBeforeWritingArtifacts() {
        ReviewFixture fixture = fixture("trace-dataset-hash");
        Map<String, Object> config = config(false);
        RagBenchmarkReport report = RagCandidate5ReviewSupport.buildReport(
                fixture.frozen(), fixture.results(), config);
        List<Map<String, Object>> trace = new ArrayList<>(fixture.trace());
        trace.set(trace.size() - 1, Map.of("queryId", fixture.queries().get(0).id()));

        assertEquals("CANDIDATE5_REVIEW_TRACE_QUERY_INVALID",
                assertThrows(IllegalArgumentException.class,
                        () -> RagCandidate5ReviewSupport.writeCompleteArm(
                                temp,
                                RagCandidate5ReviewSupport.Arm.BASELINE,
                                fixture.frozen(),
                                fixture.results(),
                                report,
                                config,
                                List.of("sentinel-ok"),
                                RagCandidate5ReviewSupport.familyScores(
                                        fixture.frozen().dataset(), fixture.results()),
                                budget(),
                                trace)).getMessage());
        assertFalse(Files.exists(temp.resolve("candidate5-baseline.json")));
    }

    @Test
    void fixesArtifactNamesAndWritesManifestLastWithCompleteHashes() throws Exception {
        RagCandidate5ReviewSupport.Arm arm = RagCandidate5ReviewSupport.Arm.BASELINE;
        assertEquals(List.of(
                "candidate5-baseline.json",
                "candidate5-baseline-db-calls.json",
                "candidate5-baseline-contexts.jsonl",
                "candidate5-baseline-labels.jsonl"), arm.fileNames());
        assertEquals(temp.resolve("bundles/candidate5-baseline"),
                arm.bundleDirectory(temp));

        ReviewFixture fixture = fixture("bundle-dataset-hash");
        Map<String, Object> config = config(false);
        RagBenchmarkReport report = RagCandidate5ReviewSupport.buildReport(
                fixture.frozen(), fixture.results(), config);
        RagCandidate5ReviewSupport.writeCompleteArm(
                temp,
                arm,
                fixture.frozen(),
                fixture.results(),
                report,
                config,
                List.of("sentinel-ok"),
                RagCandidate5ReviewSupport.familyScores(
                        fixture.frozen().dataset(), fixture.results()),
                budget(),
                fixture.trace());

        Path manifestPath = arm.bundleDirectory(temp).resolve("manifest.json");
        JSONObject manifest = JSON.parseObject(Files.readString(manifestPath));
        assertEquals("COMPLETE", manifest.getString("status"));
        assertEquals("A", manifest.getString("arm"));
        assertEquals(report.datasetHash(), manifest.getString("datasetHash"));
        assertEquals(report.configHash(), manifest.getString("configHash"));
        JSONObject hashes = manifest.getJSONObject("files");
        for (String fileName : arm.fileNames()) {
            Path rootArtifact = temp.resolve(fileName);
            Path bundledArtifact = arm.bundleDirectory(temp).resolve(fileName);
            assertTrue(Files.isRegularFile(rootArtifact));
            assertTrue(Files.isRegularFile(bundledArtifact));
            assertEquals(ShadowContractSupport.sha256(bundledArtifact),
                    hashes.getString(fileName));
            assertTrue(Files.getLastModifiedTime(bundledArtifact)
                    .compareTo(Files.getLastModifiedTime(manifestPath)) <= 0);
        }
    }

    @Test
    void existingRootOrBundleArtifactsFailClosed() throws Exception {
        Path rootRuntime = temp.resolve("root-artifact");
        Files.createDirectories(rootRuntime);
        Files.writeString(rootRuntime.resolve("candidate5.json"), "existing");
        assertEquals("CANDIDATE5_REVIEW_ARTIFACT_EXISTS",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5ReviewSupport.requireArtifactsAbsent(
                                rootRuntime,
                                RagCandidate5ReviewSupport.Arm.CANDIDATE)).getMessage());

        Path bundleRuntime = temp.resolve("bundle-artifact");
        Files.createDirectories(RagCandidate5ReviewSupport.Arm.CANDIDATE
                .bundleDirectory(bundleRuntime));
        assertEquals("CANDIDATE5_REVIEW_BUNDLE_EXISTS",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5ReviewSupport.requireArtifactsAbsent(
                                bundleRuntime,
                                RagCandidate5ReviewSupport.Arm.CANDIDATE)).getMessage());
    }

    private static ReviewFixture fixture(String datasetHash) {
        List<RagEvaluationDataset.QueryCase> queries = new ArrayList<>();
        Map<String, Map<String, Integer>> qrels = new LinkedHashMap<>();
        Map<String, RagResult> results = new LinkedHashMap<>();
        List<Map<String, Object>> trace = new ArrayList<>();
        for (int family = 0; family < 16; family++) {
            boolean answerable = family < 14;
            String familyId = "c5h-family-" + family;
            for (String language : List.of("zh", "en")) {
                String queryId = familyId + "-" + language;
                long segmentId = 9_970_001L + queries.size();
                queries.add(new RagEvaluationDataset.QueryCase(
                        queryId,
                        familyId,
                        "query " + queryId,
                        "query " + queryId,
                        List.of(),
                        language,
                        Set.of("candidate5", answerable ? "answerable" : "unanswerable"),
                        "holdout",
                        answerable,
                        answerable ? "reference" : null,
                        answerable ? List.of("claim") : List.of()));
                qrels.put(queryId, answerable
                        ? Map.of(String.valueOf(segmentId), 3)
                        : Map.of());
                List<RetrievalResult> sources = answerable
                        ? List.of(RetrievalResult.builder()
                                .segmentId(segmentId)
                                .documentId(9_975_000L + queries.size())
                                .documentName("Document " + queryId)
                                .content("Evidence " + queryId)
                                .score(1.0D)
                                .source("keyword")
                                .build())
                        : List.of();
                results.put(queryId, RagResult.builder()
                        .context(answerable ? "Context " + queryId : "")
                        .sources(sources)
                        .build());
                trace.add(Map.of("queryId", queryId, "dbCalls", 1));
            }
        }
        RagEvaluationDataset dataset = new RagEvaluationDataset(Map.of(), queries, qrels);
        RagCandidate5DiagnosticSupport.FrozenDataset frozen =
                new RagCandidate5DiagnosticSupport.FrozenDataset(
                        dataset, null, datasetHash, List.of());
        return new ReviewFixture(frozen, List.copyOf(queries), results, trace);
    }

    private static Map<String, Object> config(boolean candidate) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("identifierAware", false);
        config.put("identifierConsistencyEnabled", true);
        config.put("identifierRecallConsistencyEnabled", candidate);
        config.put("identifierRecallConsistencyAlgorithm", RagCandidate5ReviewSupport.ALGORITHM);
        config.put("identifierRecallConsistencySqlPolicy", RagCandidate5ReviewSupport.SQL_POLICY);
        return config;
    }

    private static Map<String, Object> budget() {
        return Map.of(
                "latencyMedianMs", 1L,
                "dbCalls", 32,
                "embeddingCalls", 32,
                "tokens", 0,
                "costUsd", 0.0D);
    }

    private record ReviewFixture(
            RagCandidate5DiagnosticSupport.FrozenDataset frozen,
            List<RagEvaluationDataset.QueryCase> queries,
            Map<String, RagResult> results,
            List<Map<String, Object>> trace) {
    }
}
