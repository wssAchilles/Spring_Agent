package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCandidate4DiagnosticSupportTest {

    @TempDir
    Path temp;

    private String configuredHoldoutDirectory;

    @BeforeEach
    void isolateSelectionJobProperties() {
        configuredHoldoutDirectory = System.getProperty(
                RagCandidate4DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY);
        System.clearProperty(RagCandidate4DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY);
    }

    @AfterEach
    void restoreSelectionJobProperties() {
        restoreProperty(RagCandidate4DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
                configuredHoldoutDirectory);
    }

    @Test
    void pathsAreFixedUnderRagEvalRuntime() {
        RagCandidate4DiagnosticSupport.RuntimePaths paths =
                RagCandidate4DiagnosticSupport.paths(temp);

        assertEquals(temp.resolve("candidate4-freeze"), paths.freezeDirectory());
        assertEquals(paths.freezeDirectory().resolve("selection-manifest.json"),
                paths.selectionManifest());
        assertEquals(paths.freezeDirectory().resolve("holdout-manifest.json"),
                paths.holdoutManifest());
        assertEquals(paths.freezeDirectory().resolve("selection-ledger.json"), paths.ledger());
        assertEquals(temp.resolve("candidate4-calibration-diagnostic.json"), paths.diagnostic());
    }

    @Test
    void selectionJobRejectsHoldoutDirectoryEvenWhenBlank() {
        String property = RagCandidate4DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY;
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "");
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    RagCandidate4DiagnosticSupport::requireSelectionJobProperties);
            assertEquals("CANDIDATE4_HOLDOUT_ACCESS_FORBIDDEN", failure.getMessage());
        } finally {
            restoreProperty(property, previous);
        }
    }

    @Test
    void lifecycleHelpersCountFourModesAndOnlyClearCandidate4Diagnostic() throws Exception {
        assertEquals(0, RagCandidate4DiagnosticSupport.enabledDiagnosticCount(
                false, false, false, false));
        assertEquals(2, RagCandidate4DiagnosticSupport.enabledDiagnosticCount(
                true, false, true, false));

        RagCandidate4DiagnosticSupport.RuntimePaths paths =
                RagCandidate4DiagnosticSupport.paths(temp);
        Files.createDirectories(paths.freezeDirectory());
        Files.writeString(paths.selectionManifest(), "selection", StandardCharsets.UTF_8);
        Files.writeString(paths.holdoutManifest(), "holdout", StandardCharsets.UTF_8);
        Files.writeString(paths.ledger(), "ledger", StandardCharsets.UTF_8);
        Files.writeString(paths.diagnostic(), "old diagnostic", StandardCharsets.UTF_8);

        RagCandidate4DiagnosticSupport.clearDiagnostic(paths);

        assertFalse(Files.exists(paths.diagnostic()));
        assertTrue(Files.exists(paths.selectionManifest()));
        assertTrue(Files.exists(paths.holdoutManifest()));
        assertTrue(Files.exists(paths.ledger()));
    }

    @Test
    void frozenManifestLoaderReadsOnlyFixedRegularFiles() throws Exception {
        RagCandidate4DiagnosticSupport.RuntimePaths paths =
                RagCandidate4DiagnosticSupport.paths(temp);
        Files.createDirectories(paths.freezeDirectory());
        writeManifest(paths.selectionManifest(), "FROZEN");
        writeManifest(paths.holdoutManifest(), "FROZEN_NOT_BLIND");

        RagCandidate4DiagnosticSupport.FrozenManifests manifests =
                RagCandidate4DiagnosticSupport.loadFrozenManifests(paths);

        assertEquals("FROZEN", manifests.selection().freezeStatus());
        assertEquals("FROZEN_NOT_BLIND", manifests.holdout().freezeStatus());
        assertEquals(List.of("selection-manifest.json", "holdout-manifest.json"),
                manifests.auditedReads());
        String serializedHoldout = JSON.toJSONString(manifests.holdout());
        assertFalse(serializedHoldout.contains("holdout-dir"));
        assertFalse(serializedHoldout.contains("candidate4-holdout/"));

        Path realManifest = temp.resolve("real-holdout.json");
        writeManifest(realManifest, "FROZEN_NOT_BLIND");
        RagCandidate4DiagnosticSupport.RuntimePaths substitutedPaths =
                new RagCandidate4DiagnosticSupport.RuntimePaths(
                        paths.freezeDirectory(), paths.selectionManifest(), realManifest,
                        paths.ledger(), paths.diagnostic());
        assertEquals("CANDIDATE4_RUNTIME_PATHS_INVALID",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate4DiagnosticSupport.loadFrozenManifests(
                                substitutedPaths)).getMessage());

        Files.delete(paths.holdoutManifest());
        Files.createSymbolicLink(paths.holdoutManifest(), realManifest);
        assertEquals("CANDIDATE4_MANIFEST_NOT_REGULAR",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate4DiagnosticSupport.loadFrozenManifests(paths)).getMessage());
    }

    @Test
    void freezeValidatesFixedDatasetShapeAndRejectsSelectionMutation() throws Exception {
        Path selection = temp.resolve("selection-source");
        Path holdout = temp.resolve("holdout-source");
        Path baseCorpus = temp.resolve("base-corpus.jsonl");
        writeCandidate4Fixture(selection, false);
        writeCandidate4Fixture(holdout, true);
        Files.writeString(baseCorpus, corpusLine(100005L, "base-document", "base evidence"),
                StandardCharsets.UTF_8);
        Path runtime = temp.resolve("runtime");

        RagCandidate4DiagnosticSupport.freezeDatasets(runtime, selection, holdout, baseCorpus);

        RagCandidate4DiagnosticSupport.RuntimePaths paths =
                RagCandidate4DiagnosticSupport.paths(runtime);
        byte[] selectionManifest = Files.readAllBytes(paths.selectionManifest());
        byte[] holdoutManifest = Files.readAllBytes(paths.holdoutManifest());
        RagCandidate4DiagnosticSupport.freezeDatasets(runtime, selection, holdout, baseCorpus);
        assertTrue(java.util.Arrays.equals(
                selectionManifest, Files.readAllBytes(paths.selectionManifest())));
        assertTrue(java.util.Arrays.equals(
                holdoutManifest, Files.readAllBytes(paths.holdoutManifest())));
        RagCandidate4DiagnosticSupport.FrozenManifests manifests =
                RagCandidate4DiagnosticSupport.loadFrozenManifests(paths);
        assertEquals(8, manifests.selection().counts().get("familyCount"));
        assertEquals(16, manifests.selection().counts().get("queryCount"));
        assertEquals(38, manifests.selection().counts().get("qrelCount"));
        assertEquals(16, manifests.holdout().counts().get("familyCount"));
        assertEquals(32, manifests.holdout().counts().get("queryCount"));
        assertEquals(76, manifests.holdout().counts().get("qrelCount"));

        RagCandidate4DiagnosticSupport.FrozenSelection frozen =
                RagCandidate4DiagnosticSupport.loadFrozenSelection(
                        paths, selection, baseCorpus);
        assertEquals(16, frozen.dataset().queries().size());
        assertEquals(20, frozen.dataset().corpusById().size());
        assertEquals(manifests.selection().datasetHash(), frozen.datasetHash());
        assertEquals(List.of(
                "selection-manifest.json", "holdout-manifest.json",
                "candidate4-selection/corpus.jsonl", "candidate4-selection/queries.jsonl",
                "candidate4-selection/qrels.tsv", "base-distractor/corpus.jsonl"),
                frozen.auditedReads());

        Files.writeString(selection.resolve("queries.jsonl"), "\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        assertEquals("CANDIDATE4_FREEZE_ALREADY_FROZEN",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate4DiagnosticSupport.freezeDatasets(
                                runtime, selection, holdout, baseCorpus)).getMessage());
        assertTrue(java.util.Arrays.equals(
                selectionManifest, Files.readAllBytes(paths.selectionManifest())));
        assertTrue(java.util.Arrays.equals(
                holdoutManifest, Files.readAllBytes(paths.holdoutManifest())));
        assertEquals("CANDIDATE4_SELECTION_RESOURCE_HASH_MISMATCH",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate4DiagnosticSupport.loadFrozenSelection(
                                paths, selection, baseCorpus)).getMessage());
    }

    @Test
    void incompleteFreezeIsPreservedAndCannotBeRetried() throws Exception {
        Path selection = temp.resolve("selection");
        Path holdout = temp.resolve("holdout");
        Path baseCorpus = temp.resolve("base-corpus.jsonl");
        writeCandidate4Fixture(selection, false);
        writeCandidate4Fixture(holdout, true);
        Files.writeString(baseCorpus, corpusLine(100005L, "base-document", "base evidence"),
                StandardCharsets.UTF_8);
        Path runtime = temp.resolve("runtime-partial");
        RagCandidate4DiagnosticSupport.freezeDatasets(
                runtime, selection, holdout, baseCorpus);
        RagCandidate4DiagnosticSupport.RuntimePaths paths =
                RagCandidate4DiagnosticSupport.paths(runtime);
        byte[] selectionManifest = Files.readAllBytes(paths.selectionManifest());
        Files.delete(paths.holdoutManifest());

        assertEquals("CANDIDATE4_FREEZE_INCOMPLETE",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate4DiagnosticSupport.freezeDatasets(
                                runtime, selection, holdout, baseCorpus)).getMessage());
        assertTrue(java.util.Arrays.equals(
                selectionManifest, Files.readAllBytes(paths.selectionManifest())));
        assertFalse(Files.exists(paths.holdoutManifest()));
    }

    @Test
    void freezeRejectsTargetWithoutExactIdentifierAnchor() throws Exception {
        Path selection = temp.resolve("selection-without-anchor");
        Path holdout = temp.resolve("holdout-with-anchor");
        Path baseCorpus = temp.resolve("base-corpus-anchor.jsonl");
        writeCandidate4Fixture(selection, false);
        writeCandidate4Fixture(holdout, true);
        Path selectionCorpus = selection.resolve("corpus.jsonl");
        Files.writeString(selectionCorpus,
                Files.readString(selectionCorpus, StandardCharsets.UTF_8)
                        .replace("\"documentId\":\"Topic 7101\"",
                                "\"documentId\":\"missing anchor\""),
                StandardCharsets.UTF_8);
        Files.writeString(baseCorpus, corpusLine(100005L, "base-document", "base evidence"),
                StandardCharsets.UTF_8);

        assertEquals("CANDIDATE4_IDENTIFIER_ANCHOR_INVALID",
                assertThrows(IllegalArgumentException.class,
                        () -> RagCandidate4DiagnosticSupport.freezeDatasets(
                                temp.resolve("runtime-anchor"), selection, holdout, baseCorpus))
                        .getMessage());
    }

    @Test
    void ledgerTransitionsAtomicallyAndDetectsArtifactTampering() throws Exception {
        RagCandidate4DiagnosticSupport.RuntimePaths paths =
                RagCandidate4DiagnosticSupport.paths(temp);
        Files.createDirectories(paths.freezeDirectory());
        writeManifest(paths.selectionManifest(), "FROZEN");
        writeManifest(paths.holdoutManifest(), "FROZEN_NOT_BLIND");
        RagCandidate4DiagnosticSupport.FrozenManifests manifests =
                RagCandidate4DiagnosticSupport.loadFrozenManifests(paths);

        RagCandidate4DiagnosticSupport.RunHandle handle =
                RagCandidate4DiagnosticSupport.beginSelectionRun(paths, manifests);
        JSONObject running = JSON.parseObject(Files.readString(paths.ledger()));
        assertEquals("RUNNING", running.getString("status"));

        Map<String, Object> artifact = validArtifact(manifests);
        String artifactHash = RagCandidate4DiagnosticSupport.writeDiagnosticAndComplete(
                paths, handle, artifact);

        JSONObject completed = JSON.parseObject(Files.readString(paths.ledger()));
        assertEquals("COMPLETED", completed.getString("status"));
        assertEquals(artifactHash, completed.getString("artifactSha256"));
        RagCandidate4DiagnosticSupport.verifyCompletedRun(paths);

        Files.writeString(paths.diagnostic(), "{}", StandardCharsets.UTF_8);
        assertEquals("CANDIDATE4_ARTIFACT_HASH_MISMATCH",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate4DiagnosticSupport.verifyCompletedRun(paths)).getMessage());
    }

    @Test
    void staleRunningAndCompletedLedgersArePreserved() throws Exception {
        RagCandidate4DiagnosticSupport.RuntimePaths paths =
                RagCandidate4DiagnosticSupport.paths(temp);
        Files.createDirectories(paths.freezeDirectory());
        writeManifest(paths.selectionManifest(), "FROZEN");
        writeManifest(paths.holdoutManifest(), "FROZEN_NOT_BLIND");
        RagCandidate4DiagnosticSupport.FrozenManifests manifests =
                RagCandidate4DiagnosticSupport.loadFrozenManifests(paths);
        RagCandidate4DiagnosticSupport.RunHandle handle =
                RagCandidate4DiagnosticSupport.beginSelectionRun(paths, manifests);
        byte[] running = Files.readAllBytes(paths.ledger());

        assertEquals("INVALID_INCOMPLETE_PRIOR_RUN",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate4DiagnosticSupport.requireSelectionRunAvailable(paths))
                        .getMessage());
        assertEquals("INVALID_INCOMPLETE_PRIOR_RUN",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate4DiagnosticSupport.beginSelectionRun(paths, manifests))
                        .getMessage());
        assertTrue(java.util.Arrays.equals(running, Files.readAllBytes(paths.ledger())));

        Map<String, Object> invalid = validArtifact(manifests);
        invalid.put("status", "INVALID");
        invalid.put("errorCode", "FIXTURE_FAILURE");
        invalid.remove("summary");
        invalid.remove("cases");
        RagCandidate4DiagnosticSupport.writeDiagnosticAndComplete(paths, handle, invalid);
        byte[] completed = Files.readAllBytes(paths.ledger());
        assertEquals("CANDIDATE4_SELECTION_ALREADY_COMPLETED",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate4DiagnosticSupport.beginSelectionRun(paths, manifests))
                        .getMessage());
        assertTrue(java.util.Arrays.equals(completed, Files.readAllBytes(paths.ledger())));
    }

    @Test
    void documentCopiesDoNotShareMetadataWithSutObjects() {
        Document original = Document.builder().id("9940001").text("evidence")
                .metadata(new LinkedHashMap<>(Map.of("segmentId", 9_940_001L, "score", 0.4D)))
                .build();

        List<Document> detached = RagCandidate4DiagnosticSupport.copyDocuments(List.of(original));
        detached.get(0).getMetadata().put("colbert_score", 9.0D);
        detached.get(0).getMetadata().put("score", 1.0D);

        assertFalse(original.getMetadata().containsKey("colbert_score"));
        assertEquals(0.4D, ((Number) original.getMetadata().get("score")).doubleValue());
        assertNotEquals(original.getMetadata(), detached.get(0).getMetadata());
    }

    @Test
    void detachedFullColbertRerankDoesNotMutateBusinessDocuments() {
        List<Document> business = List.of(
                Document.builder().id("first").text("alpha protocol evidence")
                        .metadata(new LinkedHashMap<>(Map.of(
                                "segmentId", 9_940_001L, "score", 0.4D))).build(),
                Document.builder().id("second").text("unrelated evidence")
                        .metadata(new LinkedHashMap<>(Map.of(
                                "segmentId", 9_940_002L, "score", 0.2D))).build());
        List<String> businessOrder = business.stream().map(Document::getId).toList();
        List<Map<String, Object>> businessMetadata = business.stream()
                .<Map<String, Object>>map(document ->
                        new LinkedHashMap<>(document.getMetadata())).toList();
        ColbertScorer.ColbertConfig config = new ColbertScorer.ColbertConfig();
        config.setEnabled(true);
        config.setDimensions(16);
        config.setMaxTokensPerDoc(32);

        List<Document> detached = RagCandidate4DiagnosticSupport.copyDocuments(business);
        new ColbertScorer(config, null).rerank(
                "alpha protocol", detached, detached.size());

        assertEquals(businessOrder, business.stream().map(Document::getId).toList());
        assertEquals(businessMetadata, business.stream().map(Document::getMetadata).toList());
        assertTrue(business.stream().noneMatch(document ->
                document.getMetadata().containsKey("colbert_score")));
        assertTrue(detached.stream().allMatch(document ->
                document.getMetadata().containsKey("colbert_score")));
    }

    @Test
    void counterfactualAppendsOnlyMissingIdentifierMatchesWithFusionScores() {
        RetrievalResult first = RetrievalResult.builder()
                .segmentId(9_940_101L)
                .documentId(9_945_001L)
                .documentName("Topic 7101")
                .content("first")
                .score(0.25D)
                .metadata(new LinkedHashMap<>(Map.of("score", 0.25D)))
                .build();
        RetrievalResult second = RetrievalResult.builder()
                .segmentId(9_940_102L)
                .documentId(9_945_002L)
                .documentName("other")
                .content("second")
                .score(0.75D)
                .metadata(new LinkedHashMap<>(Map.of("score", 0.75D)))
                .build();
        RetrievalResult anchor = RetrievalResult.builder()
                .segmentId(9_940_103L)
                .documentId(9_945_003L)
                .documentName("Topic 7101")
                .content("anchor")
                .score(0.5D)
                .metadata(new LinkedHashMap<>(Map.of("score", 0.5D)))
                .build();
        RetrievalResult unrelatedFullResult = RetrievalResult.builder()
                .segmentId(9_940_104L)
                .documentId(9_945_004L)
                .documentName("unrelated")
                .content("unrelated")
                .score(0.9D)
                .metadata(new LinkedHashMap<>(Map.of("score", 0.9D)))
                .build();
        List<RetrievalResult> detached =
                RagShadowBaselineTest.buildCandidate4CounterfactualInput(
                        List.of("9940101", "9940102"),
                        List.of("9940104", "9940103", "9940101", "9940102"),
                        Map.of(
                                "9940101", first,
                                "9940102", second,
                                "9940103", anchor,
                                "9940104", unrelatedFullResult),
                        Set.of("9940103"));

        assertEquals(List.of(9_940_101L, 9_940_102L, 9_940_103L), detached.stream()
                .map(RetrievalResult::getSegmentId).toList());
        assertEquals(List.of(0.25D, 0.75D, 0.5D), detached.stream()
                .map(RetrievalResult::getScore).toList());
        assertEquals(List.of(0.25D, 0.75D, 0.5D, 0.9D),
                List.of(first.getScore(), second.getScore(), anchor.getScore(),
                        unrelatedFullResult.getScore()));
        assertNotSame(first.getMetadata(), detached.get(0).getMetadata());
    }

    @Test
    void classifiesAllFiveStagesAndOnlyUniformTargetsProceedToDesign() {
        assertEquals(RagCandidate4DiagnosticSupport.RootCause.RETRIEVAL_MISS,
                evidence("miss", true, List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), Set.of("1")).classification());
        assertEquals(RagCandidate4DiagnosticSupport.RootCause.FILTER_DELETION,
                evidence("filter", true, List.of("1"), List.of(), List.of(), List.of(),
                        List.of(), List.of(), Set.of("1")).classification());
        RagCandidate4DiagnosticSupport.CaseEvidence suppression = evidence(
                "suppression", true, List.of("1"), List.of("1"), List.of("2"),
                List.of("2", "1"), List.of("2"), List.of("1", "2"), Set.of("1"));
        assertEquals(RagCandidate4DiagnosticSupport.RootCause.COLBERT_IDENTIFIER_SUPPRESSION,
                suppression.classification());
        assertEquals(RagCandidate4DiagnosticSupport.RootCause.FINAL_REDUNDANCY,
                evidence("final", true, List.of("1"), List.of("1"), List.of("1"),
                        List.of("1"), List.of("2"), List.of("1", "2"), Set.of("1"))
                        .classification());
        RagCandidate4DiagnosticSupport.CaseEvidence unchanged = evidence(
                "control", false, List.of("2"), List.of("2"), List.of("2"),
                List.of("2"), List.of("2"), List.of("2"), Set.of());
        assertEquals(RagCandidate4DiagnosticSupport.RootCause.NONE, unchanged.classification());

        List<RagCandidate4DiagnosticSupport.CaseEvidence> cases = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            cases.add(evidence("target-" + index, true, List.of("1"), List.of("1"),
                    List.of("2"), List.of("2", "1"), List.of("2"),
                    List.of("1", "2"), Set.of("1")));
        }
        for (int index = 0; index < 4; index++) {
            cases.add(evidence("control-" + index, false, List.of("2"), List.of("2"),
                    List.of("2"), List.of("2"), List.of("2"), List.of("2"), Set.of()));
        }
        assertEquals("PROCEED_TO_IDENTIFIER_ANCHOR_DESIGN",
                RagCandidate4DiagnosticSupport.decide(cases));

        cases.set(0, unchanged);
        assertEquals("STOP_IDENTIFIER_ANCHOR_UNSUPPORTED",
                RagCandidate4DiagnosticSupport.decide(cases));
    }

    @Test
    void unitTestsDoNotConsumeFormalLedger() throws Exception {
        Path formalLedger = RagCandidate4DiagnosticSupport.paths(
                RagLiveEvaluationSupport.runtimeDirectory()).ledger();
        byte[] before = Files.isRegularFile(formalLedger) ? Files.readAllBytes(formalLedger) : null;

        RagCandidate4DiagnosticSupport.RuntimePaths tempPaths =
                RagCandidate4DiagnosticSupport.paths(temp);
        Files.createDirectories(tempPaths.freezeDirectory());
        writeManifest(tempPaths.selectionManifest(), "FROZEN");
        writeManifest(tempPaths.holdoutManifest(), "FROZEN_NOT_BLIND");
        RagCandidate4DiagnosticSupport.beginSelectionRun(tempPaths,
                RagCandidate4DiagnosticSupport.loadFrozenManifests(tempPaths));

        byte[] after = Files.isRegularFile(formalLedger) ? Files.readAllBytes(formalLedger) : null;
        assertTrue(java.util.Arrays.equals(before, after));
    }

    @Test
    @EnabledIfSystemProperty(named = RagCandidate4DiagnosticSupport.FREEZE_PROPERTY, matches = "true")
    void freezeCandidate4Datasets() throws Exception {
        String holdout = configuredHoldoutDirectory;
        assertTrue(holdout != null && !holdout.isBlank(), "holdout directory is required");
        RagCandidate4DiagnosticSupport.freezeFormalDatasets(
                RagLiveEvaluationSupport.runtimeDirectory(), Path.of(holdout));
    }

    private static RagCandidate4DiagnosticSupport.CaseEvidence evidence(
            String id,
            boolean target,
            List<String> fused,
            List<String> filtered,
            List<String> colbert,
            List<String> fullColbert,
            List<String> actual,
            List<String> counterfactual,
            Set<String> exactRelevant) {
        Map<String, Integer> qrels = target ? Map.of("1", 3, "2", 1) : Map.of();
        RagCandidate4DiagnosticSupport.CaseInput input =
                new RagCandidate4DiagnosticSupport.CaseInput(
                        id, "family-" + id, "selection", target,
                        Map.of("keyword", ranked(fused)),
                        ranked(fused), ranked(filtered), ranked(colbert), ranked(fullColbert),
                        ranked(colbert), ranked(actual), ranked(actual), ranked(counterfactual),
                        qrels, exactRelevant,
                        "context-a", target ? "context-b" : "context-a", false, false);
        return RagCandidate4DiagnosticSupport.classify(input);
    }

    private static List<RagCandidate4DiagnosticSupport.RankedSegment> ranked(List<String> ids) {
        List<RagCandidate4DiagnosticSupport.RankedSegment> ranking = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            ranking.add(new RagCandidate4DiagnosticSupport.RankedSegment(
                    ids.get(index), index + 1, 1.0D / (index + 1)));
        }
        return List.copyOf(ranking);
    }

    private static void writeManifest(Path path, String status) throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("manifestVersion", 1);
        manifest.put("dataset", path.getFileName().toString().startsWith("selection")
                ? "candidate4-selection" : "candidate4-holdout");
        manifest.put("freezeStatus", status);
        manifest.put("generator", "candidate4-static-fixture-v1");
        manifest.put("version", 1);
        manifest.put("seed", 20260716L);
        Map<String, Object> resources = Map.of(
                "corpus", Map.of("file", "corpus.jsonl", "sha256", "a".repeat(64)),
                "queries", Map.of("file", "queries.jsonl", "sha256", "b".repeat(64)),
                "qrels", Map.of("file", "qrels.tsv", "sha256", "c".repeat(64)),
                "baseDistractor", Map.of("file", "corpus.jsonl", "sha256", "d".repeat(64)));
        Map<String, Object> counts = Map.of(
                "familyCount", 1, "queryCount", 2, "documentCount", 1,
                "segmentCount", 1, "qrelCount", 1);
        manifest.put("resources", resources);
        manifest.put("counts", counts);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("generator", manifest.get("generator"));
        evidence.put("version", manifest.get("version"));
        evidence.put("seed", manifest.get("seed"));
        evidence.put("resources", resources);
        evidence.put("counts", counts);
        manifest.put("datasetHash", ShadowContractSupport.configHash(evidence));
        Files.writeString(path, JSON.toJSONString(manifest), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> validArtifact(
            RagCandidate4DiagnosticSupport.FrozenManifests manifests) {
        Map<String, Object> config = Map.of("identifierAware", false);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("status", "VALID");
        artifact.put("decision", "STOP_IDENTIFIER_ANCHOR_UNSUPPORTED");
        artifact.put("errorCode", null);
        artifact.put("datasetHash", manifests.selection().datasetHash());
        artifact.put("selectionManifestHash", manifests.selectionSha256());
        artifact.put("holdoutManifestHash", manifests.holdoutSha256());
        artifact.put("holdoutFreezeStatus", "FROZEN_NOT_BLIND");
        artifact.put("auditedReads", manifests.auditedReads());
        artifact.put("config", config);
        artifact.put("configHash", ShadowContractSupport.configHash(config));
        artifact.put("summary", Map.of());
        artifact.put("cases", List.of());
        return artifact;
    }

    private static void writeCandidate4Fixture(Path directory, boolean holdout) throws Exception {
        Files.createDirectories(directory);
        int targetFamilies = holdout ? 12 : 6;
        int unanswerableFamilies = holdout ? 2 : 1;
        int noIdentifierFamilies = holdout ? 2 : 1;
        String prefix = holdout ? "c4h" : "c4s";
        long segmentBase = holdout ? 9_951_000L : 9_941_000L;
        List<String> corpus = new ArrayList<>();
        List<String> queries = new ArrayList<>();
        List<String> qrels = new ArrayList<>();
        qrels.add("queryId\tsegmentId\tgrade");

        for (int family = 1; family <= targetFamilies; family++) {
            String familyId = prefix + "-t" + String.format("%02d", family);
            long identifier = (holdout ? 8100L : 7100L) + family;
            long firstSegment = segmentBase + (family - 1L) * 3L;
            corpus.add(corpusLine(firstSegment, "Topic " + identifier,
                    "archived anchor evidence outside the semantic wording"));
            corpus.add(corpusLine(firstSegment + 1, prefix + "-evidence-a-" + family,
                    "semantic evidence alpha for protocol " + family));
            corpus.add(corpusLine(firstSegment + 2, prefix + "-evidence-b-" + family,
                    "semantic evidence beta for protocol " + family));
            for (String language : List.of("zh", "en")) {
                String queryId = familyId + "-" + language;
                queries.add(queryLine(queryId, familyId,
                        language.equals("zh")
                                ? "主题 " + identifier + " 的 protocol " + family + " 证据"
                                : "topic " + identifier + " protocol " + family + " evidence",
                        language, true));
                qrels.add(queryId + "\t" + firstSegment + "\t3");
                qrels.add(queryId + "\t" + (firstSegment + 1) + "\t2");
                qrels.add(queryId + "\t" + (firstSegment + 2) + "\t1");
            }
        }

        for (int family = 1; family <= unanswerableFamilies; family++) {
            String familyId = prefix + "-u" + String.format("%02d", family);
            long identifier = (holdout ? 8990L : 7990L) + family;
            queries.add(queryLine(familyId + "-zh", familyId,
                    "主题 " + identifier + " 的缺失证据", "zh", false));
            queries.add(queryLine(familyId + "-en", familyId,
                    "topic " + identifier + " missing evidence", "en", false));
        }

        for (int family = 1; family <= noIdentifierFamilies; family++) {
            String familyId = prefix + "-n" + String.format("%02d", family);
            long segmentId = segmentBase + targetFamilies * 3L + family - 1L;
            corpus.add(corpusLine(segmentId, prefix + "-no-id-" + family,
                    "compass protocol evidence without a numeric identifier"));
            for (String language : List.of("zh", "en")) {
                String queryId = familyId + "-" + language;
                queries.add(queryLine(queryId, familyId,
                        language.equals("zh") ? "无编号的 compass protocol 证据" : "compass protocol evidence",
                        language, true));
                qrels.add(queryId + "\t" + segmentId + "\t3");
            }
        }

        Files.writeString(directory.resolve("corpus.jsonl"),
                String.join("\n", corpus) + "\n", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("queries.jsonl"),
                String.join("\n", queries) + "\n", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("qrels.tsv"),
                String.join("\n", qrels) + "\n", StandardCharsets.UTF_8);
    }

    private static String corpusLine(long segmentId, String documentId, String content) {
        return JSON.toJSONString(Map.of(
                "segmentId", String.valueOf(segmentId),
                "documentId", documentId,
                "content", content,
                "metadata", Map.of()));
    }

    private static String queryLine(
            String id, String familyId, String query, String language, boolean answerable) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("familyId", familyId);
        value.put("query", query);
        value.put("retrievalQuery", query);
        value.put("history", List.of());
        value.put("language", language);
        value.put("strata", List.of("candidate4", answerable ? "answerable" : "unanswerable"));
        value.put("split", id.startsWith("c4s-") ? "selection" : "holdout");
        value.put("answerable", answerable);
        value.put("referenceAnswer", answerable ? "frozen synthetic evidence" : null);
        value.put("referenceClaims", answerable ? List.of("frozen synthetic claim") : List.of());
        return JSON.toJSONString(value);
    }

    private static void restoreProperty(String name, String previous) {
        if (previous == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previous);
        }
    }
}
