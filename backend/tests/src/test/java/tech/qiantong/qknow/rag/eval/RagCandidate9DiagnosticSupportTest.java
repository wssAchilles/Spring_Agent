package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCandidate9DiagnosticSupportTest {

    @TempDir
    Path temp;

    @Test
    void freezesQueryVisibilityBoundaryAndProjectionTokenContract() {
        assertEquals(List.of("7611"),
                RagCandidate9DiagnosticSupport.visibleIdentifierTerms(
                        "topic 7611"));
        assertEquals(List.of("7611"),
                RagCandidate9DiagnosticSupport.visibleIdentifierTerms(
                        "topic  7611 evidence"));
        assertEquals(List.of("7611"),
                RagCandidate9DiagnosticSupport.visibleIdentifierTerms(
                        "主题 7611 证据"));
        for (String query : List.of(
                "topic DOC-7611", "主题-7611-证据",
                "topic\t7611", "topic\u00a07611")) {
            assertEquals(List.of(),
                    RagCandidate9DiagnosticSupport.visibleIdentifierTerms(query));
        }

        for (String name : List.of("7611", "DOC-7611", "主题-7611-证据")) {
            assertTrue(RagCandidate9DiagnosticSupport.matchesDocumentName(
                    name, "7611"));
        }
        for (String name : List.of(
                "ABC7611", "17611", "76110", "主题7611证据")) {
            assertFalse(RagCandidate9DiagnosticSupport.matchesDocumentName(
                    name, "7611"));
        }
        assertTrue(RagCandidate9DiagnosticSupport.matchesDocumentName(
                "DOC-07621", "07621"));
        assertFalse(RagCandidate9DiagnosticSupport.matchesDocumentName(
                "DOC-07621", "7621"));

        RagCandidate9DiagnosticSupport.Projection projection =
                RagCandidate9DiagnosticSupport.project(
                        "topic 7611", "DOC-7611 archive", "alpha evidence");
        assertTrue(projection.applied());
        assertEquals("7611 alpha evidence", projection.text());
        assertEquals(1, projection.matchedIdentifierCount());
        assertEquals(2, projection.originalTokenCount());
        assertEquals(1, projection.projectedTokenCount());
        assertEquals(2, projection.retainedContentTokens());

        RagCandidate9DiagnosticSupport.Projection inactive =
                RagCandidate9DiagnosticSupport.project(
                        "topic DOC-7611", "DOC-7611 archive", "alpha evidence");
        assertFalse(inactive.applied());
        assertEquals("alpha evidence", inactive.text());

        String longContent = java.util.stream.IntStream.range(0, 128)
                .mapToObj(index -> "token" + index)
                .collect(java.util.stream.Collectors.joining(" "));
        RagCandidate9DiagnosticSupport.Projection truncated =
                RagCandidate9DiagnosticSupport.project(
                        "topic 7611", "DOC-7611 archive", longContent);
        assertEquals(128, truncated.originalTokenCount());
        assertEquals(127, truncated.retainedContentTokens());
    }

    @Test
    void snapshotsInputsAndKeepsDiagnosticModesMutuallyExclusive() {
        RetrievalResult result = RetrievalResult.builder()
                .segmentId(10_140_001L)
                .documentId(10_145_000L)
                .qmSegmentId("10140001")
                .documentName("DOC-7611 archive")
                .content("alpha evidence")
                .score(0.7D)
                .source("keyword")
                .metadata(Map.of("colbert_score", 0.4D))
                .build();
        List<RagCandidate9DiagnosticSupport.RetrievalSnapshot> snapshots =
                RagCandidate9DiagnosticSupport.snapshotResults(
                        List.of(result), "topic 7611");
        result.setContent("mutated");
        result.setScore(9.0D);

        assertEquals("alpha evidence", snapshots.get(0).content());
        assertEquals(0.7D, snapshots.get(0).score());
        assertTrue(snapshots.get(0).exactMatch());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshots.add(snapshots.get(0)));
        List<RetrievalResult> copies =
                RagCandidate9DiagnosticSupport.copyResults(snapshots);
        copies.get(0).setContent("copy-mutated");
        assertEquals("alpha evidence", snapshots.get(0).content());

        assertEquals(2, RagCandidate9DiagnosticSupport.enabledDiagnosticCount(
                false, false, false, false, false, false, true, true));
        assertEquals(1, RagCandidate9DiagnosticSupport.enabledDiagnosticCount(
                false, false, false, false, false, false, false, true));
    }

    @Test
    void tokenBudgetMatchesTheRealColbertTokenizerForFrozenDatasets()
            throws Exception {
        Fixture fixture = copyFixture();
        ColbertScorer scorer = new ColbertScorer(
                new ColbertScorer.ColbertConfig(), null);
        for (DatasetFixture datasetFixture : List.of(
                new DatasetFixture(fixture.selection(), false),
                new DatasetFixture(fixture.holdout(), true))) {
            Object files = ReflectionTestUtils.invokeMethod(
                    RagCandidate9DiagnosticSupport.class,
                    "readDatasetFiles",
                    datasetFixture.directory(),
                    datasetFixture.holdout());
            RagEvaluationDataset dataset = ReflectionTestUtils.invokeMethod(
                    files, "dataset");
            LinkedHashSet<String> texts = new LinkedHashSet<>();
            dataset.corpusById().values().forEach(segment ->
                    texts.add(segment.content()));
            for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
                for (RagEvaluationDataset.CorpusSegment segment
                        : dataset.corpusById().values()) {
                    RagCandidate9DiagnosticSupport.Projection projection =
                            RagCandidate9DiagnosticSupport.project(
                                    query.retrievalQuery(),
                                    String.valueOf(segment.metadata().get(
                                            "documentName")),
                                    segment.content());
                    if (projection.applied()) {
                        texts.add(projection.text());
                    }
                }
            }
            for (String text : texts) {
                @SuppressWarnings("unchecked")
                List<String> actual = ReflectionTestUtils.invokeMethod(
                        scorer, "tokenize", text);
                assertEquals(actual.size(),
                        RagCandidate9DiagnosticSupport.tokenCount(text));
            }
        }
    }

    @Test
    void rejectsGeneratedCoreAndDocumentMappingDrift() throws Exception {
        Fixture fixture = copyFixture();
        Object files = readDatasetFiles(fixture.selection(), false);
        RagEvaluationDataset dataset = dataset(files);
        Object pressure = ReflectionTestUtils.invokeMethod(files, "pressure");

        RagEvaluationDataset.CorpusSegment generated = dataset.corpusById()
                .values().stream()
                .filter(segment -> "pressure-distractor".equals(
                        segment.metadata().get("candidate9Role")))
                .findFirst().orElseThrow();
        Map<String, Object> wrongFamily = new LinkedHashMap<>(
                generated.metadata());
        wrongFamily.put("familyId", "c9s-t08".equals(
                generated.metadata().get("familyId"))
                ? "c9s-t01" : "c9s-t08");
        assertShapeRejected(dataset, pressure,
                replaceSegment(dataset, generated,
                        generated.documentId(), wrongFamily));

        RagEvaluationDataset.CorpusSegment targetNonexact =
                dataset.corpusById().values().stream()
                        .filter(segment -> "c9s-t01".equals(
                                segment.metadata().get("familyId")))
                        .filter(segment -> !RagCandidate9DiagnosticSupport
                                .matchesDocumentName(
                                        String.valueOf(segment.metadata().get(
                                                "documentName")), "7611"))
                        .findFirst().orElseThrow();
        Map<String, Object> wrongRole = new LinkedHashMap<>(
                targetNonexact.metadata());
        wrongRole.put("candidate9Role", "no-exact-control");
        assertShapeRejected(dataset, pressure,
                replaceSegment(dataset, targetNonexact,
                        targetNonexact.documentId(), wrongRole));

        RagEvaluationDataset.CorpusSegment noExact = dataset.corpusById()
                .values().stream()
                .filter(segment -> "no-exact-control".equals(
                        segment.metadata().get("candidate9Role")))
                .findFirst().orElseThrow();
        Map<String, Object> noExactWrongFamily = new LinkedHashMap<>(
                noExact.metadata());
        noExactWrongFamily.put("familyId", "c9s-n01");
        assertShapeRejected(dataset, pressure,
                replaceSegment(dataset, noExact,
                        noExact.documentId(), noExactWrongFamily));

        RagEvaluationDataset.CorpusSegment first = dataset.corpusById()
                .values().stream().findFirst().orElseThrow();
        assertShapeRejected(dataset, pressure,
                replaceSegment(dataset, first, "10149999", first.metadata()));
    }

    @Test
    void rejectsAncestorSymlinkInRuntimePath() throws Exception {
        Path trusted = temp.resolve("trusted");
        Path actual = temp.resolve("actual");
        Files.createDirectories(trusted);
        Files.createDirectories(actual);
        Path link = trusted.resolve("linked-target");
        Files.createSymbolicLink(link, actual);
        RagCandidate9DiagnosticSupport.RuntimePaths paths =
                RagCandidate9DiagnosticSupport.paths(
                        link.resolve("rag-eval"));
        assertThrows(IllegalArgumentException.class,
                () -> RagCandidate9DiagnosticSupport
                        .requireSelectionRunAvailable(paths));
    }

    @Test
    void multiIdentifierSafetyRejectsNonAnchorDrift() {
        Set<Long> relevant = Set.of(101L);
        Set<Long> exact = Set.of(101L, 202L);
        Map<String, Object> baseline = multiIdArm(
                List.of(stageItem(301L, 1, 0.8D),
                        stageItem(302L, 2, 0.7D)),
                List.of(stageItem(301L, 1, 0.0D)));
        Map<String, Object> legal = multiIdArm(
                List.of(stageItem(101L, 1, 0.9D),
                        stageItem(301L, 2, 0.8D),
                        stageItem(302L, 3, 0.7D)),
                List.of(stageItem(101L, 1, 0.0D),
                        stageItem(301L, 2, 0.0D)));
        assertTrue(multiIdentifierControlValid(
                relevant, exact, baseline, legal));

        for (Map<String, Object> invalid : List.of(
                multiIdArm(List.of(
                        stageItem(101L, 1, 0.9D),
                        stageItem(302L, 2, 0.7D),
                        stageItem(301L, 3, 0.8D)),
                        List.of(stageItem(101L, 1, 0.0D),
                                stageItem(301L, 2, 0.0D))),
                multiIdArm(List.of(
                        stageItem(101L, 1, 0.9D),
                        stageItem(301L, 2, 0.8D)),
                        List.of(stageItem(101L, 1, 0.0D),
                                stageItem(301L, 2, 0.0D))),
                multiIdArm(List.of(
                        stageItem(101L, 1, 0.9D),
                        stageItem(301L, 2, 0.81D),
                        stageItem(302L, 3, 0.7D)),
                        List.of(stageItem(101L, 1, 0.0D),
                                stageItem(301L, 2, 0.0D))))) {
            assertFalse(multiIdentifierControlValid(
                    relevant, exact, baseline, invalid));
        }

        List<RagCandidate9DiagnosticSupport.CaseEvidence> evidence =
                new ArrayList<>(validEvidence());
        for (int index = 0; index < evidence.size(); index++) {
            RagCandidate9DiagnosticSupport.CaseEvidence item = evidence.get(index);
            if ("multi-id-collision".equals(item.role())) {
                evidence.set(index, new RagCandidate9DiagnosticSupport.CaseEvidence(
                        item.queryId(), item.familyId(), item.language(), item.role(),
                        item.identifierShape(), item.target(), item.qualifying(),
                        item.mechanismValid(), item.baselineAp(), item.baselineNdcg(),
                        item.projectionAp(), item.projectionNdcg(), item.unchanged(),
                        false));
                break;
            }
        }
        assertEquals(RagCandidate9DiagnosticSupport.STOP_DECISION,
                RagCandidate9DiagnosticSupport.decide(evidence));
    }

    @Test
    void decisionRequiresFourShapesAndNoFamilyRegression() {
        List<RagCandidate9DiagnosticSupport.CaseEvidence> cases = validEvidence();
        assertEquals(RagCandidate9DiagnosticSupport.PROCEED_DECISION,
                RagCandidate9DiagnosticSupport.decide(cases));

        List<RagCandidate9DiagnosticSupport.CaseEvidence> missingShape =
                new ArrayList<>(cases);
        RagCandidate9DiagnosticSupport.CaseEvidence target = missingShape.get(0);
        missingShape.set(0, new RagCandidate9DiagnosticSupport.CaseEvidence(
                target.queryId(), target.familyId(), target.language(),
                target.role(), "numeric-token", target.target(),
                target.qualifying(), target.mechanismValid(),
                target.baselineAp(), target.baselineNdcg(),
                target.projectionAp(), target.projectionNdcg(),
                target.unchanged(), target.safetyValid()));
        for (int index = 0; index < missingShape.size(); index++) {
            RagCandidate9DiagnosticSupport.CaseEvidence item = missingShape.get(index);
            if ("han-punctuation".equals(item.identifierShape())) {
                missingShape.set(index, new RagCandidate9DiagnosticSupport.CaseEvidence(
                        item.queryId(), item.familyId(), item.language(), item.role(),
                        "numeric-token", item.target(), item.qualifying(),
                        item.mechanismValid(), item.baselineAp(), item.baselineNdcg(),
                        item.projectionAp(), item.projectionNdcg(), item.unchanged(),
                        item.safetyValid()));
            }
        }
        assertEquals(RagCandidate9DiagnosticSupport.STOP_DECISION,
                RagCandidate9DiagnosticSupport.decide(missingShape));
    }

    @Test
    void acceptsTheActualShadowCandidate9ConfigShape() {
        Map<String, String> previous = installSystemProperties(
                validDiagnosticCommandProperties());
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> actual = ReflectionTestUtils.invokeMethod(
                    RagShadowBaselineTest.class, "candidate9Config");
            RagCandidate9DiagnosticSupport.validateConfig(actual);
        } finally {
            restoreSystemProperties(previous);
        }
    }

    @Test
    void diagnosticCommandPropertiesFailClosed() {
        Map<String, String> valid = validDiagnosticCommandProperties();
        RagCandidate9DiagnosticSupport.validateDiagnosticCommandProperties(valid);

        Map<String, String> promotion = new LinkedHashMap<>(valid);
        promotion.put("rag.eval.promotion", "true");
        assertThrows(IllegalStateException.class,
                () -> RagCandidate9DiagnosticSupport
                        .validateDiagnosticCommandProperties(promotion));

        Map<String, String> embedding = new LinkedHashMap<>(valid);
        embedding.put("hermes.rag.colbert.embedding-model", "forbidden");
        assertThrows(IllegalStateException.class,
                () -> RagCandidate9DiagnosticSupport
                        .validateDiagnosticCommandProperties(embedding));

        Map<String, String> missing = new LinkedHashMap<>(valid);
        missing.remove("hermes.rag.colbert.enabled");
        assertThrows(IllegalStateException.class,
                () -> RagCandidate9DiagnosticSupport
                        .validateDiagnosticCommandProperties(missing));
    }

    @Test
    void commandMismatchPublishesOneSanitizedPreRunInvalidArtifact()
            throws Exception {
        Fixture fixture = copyFixture();
        List<Map<String, String>> invalidCommands = new ArrayList<>();
        Map<String, String> promotion = new LinkedHashMap<>(
                validDiagnosticCommandProperties());
        promotion.put("rag.eval.promotion", "true");
        invalidCommands.add(promotion);
        Map<String, String> embedding = new LinkedHashMap<>(
                validDiagnosticCommandProperties());
        embedding.put("hermes.rag.colbert.embedding-api-key", "forbidden-secret");
        invalidCommands.add(embedding);

        for (int index = 0; index < invalidCommands.size(); index++) {
            Path runtime = temp.resolve("command-invalid-" + index);
            RagCandidate9DiagnosticSupport.freezeDatasets(
                    runtime, fixture.selection(), fixture.holdout());
            RagCandidate9DiagnosticSupport.RuntimePaths paths =
                    RagCandidate9DiagnosticSupport.paths(runtime);
            Map<String, String> previous = installSystemProperties(
                    invalidCommands.get(index));
            try {
                assertThrows(IllegalStateException.class,
                        RagCandidate9DiagnosticSupport
                                ::requireDiagnosticCommandProperties);
                RagCandidate9DiagnosticSupport
                        .writePreRunCommandInvalidDiagnostic(paths);
                JSONObject artifact = JSON.parseObject(
                        Files.readString(paths.diagnostic()));
                assertEquals("INVALID", artifact.getString("status"));
                assertEquals("CANDIDATE9_DIAGNOSTIC_COMMAND_INVALID",
                        artifact.getString("errorCode"));
                assertFalse(artifact.containsKey("cases"));
                assertFalse(artifact.containsKey("config"));
                assertFalse(Files.exists(paths.ledger()));
                assertFalse(Files.readString(paths.diagnostic())
                        .contains("forbidden-secret"));
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate9DiagnosticSupport
                                .writePreRunCommandInvalidDiagnostic(paths));
            } finally {
                restoreSystemProperties(previous);
            }
        }
    }

    @Test
    void recursivelyRejectsSensitiveArtifactAliases() {
        for (String field : List.of(
                "modelName", "projectionToken", "projectionTokens",
                "matchedIdentifiers", "projectedText", "originalContent")) {
            Map<String, Object> nested = Map.of(
                    "safe", List.of(Map.of(
                            "deeper", new Object[]{Map.of(field, "secret")})));
            assertThrows(RuntimeException.class,
                    () -> ReflectionTestUtils.invokeMethod(
                            RagCandidate9DiagnosticSupport.class,
                            "rejectForbiddenFields", nested));
        }
        ReflectionTestUtils.invokeMethod(
                RagCandidate9DiagnosticSupport.class,
                "rejectForbiddenFields",
                Map.of("colbertEncodedDocumentTokens", 26L,
                        "embeddingModelPresent", false));
    }

    @Test
    @DisabledIfSystemProperty(
            named = RagCandidate9DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void freezesAndLoadsQrelsOnlyAfterRanking() throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("runtime");
        RagCandidate9DiagnosticSupport.FrozenManifests manifests =
                RagCandidate9DiagnosticSupport.freezeDatasets(
                        runtime, fixture.selection(), fixture.holdout());
        RagCandidate9DiagnosticSupport.FrozenDataset frozen =
                RagCandidate9DiagnosticSupport.loadFrozenSelection(
                        runtime, fixture.selection());

        assertEquals(34, manifests.selection().counts().get("queryCount"));
        assertEquals(421, manifests.selection().counts().get("segmentCount"));
        assertEquals(58, manifests.selection().counts().get("qrelPairCount"));
        assertEquals(Map.of(), frozen.dataset().qrels());
        assertEquals(3, frozen.auditedReads().selectionResourceAccessCount());
        assertEquals(0, frozen.auditedReads().qrelResourceAccessCount());
        assertEquals(0, frozen.auditedReads().holdoutResourceAccessCount());

        RagCandidate9DiagnosticSupport.RuntimePaths paths =
                RagCandidate9DiagnosticSupport.paths(runtime);
        List<Map<String, Object>> rankingCases = frozen.dataset().queries().stream()
                .map(RagCandidate9DiagnosticSupportTest::emptyRankingCase)
                .toList();
        assertThrows(NullPointerException.class,
                () -> RagCandidate9DiagnosticSupport.loadQrelsAfterRanking(
                        paths, null, frozen, rankingCases));
        assertEquals(0, frozen.auditedReads().qrelResourceAccessCount());
        RagCandidate9DiagnosticSupport.RunHandle handle =
                RagCandidate9DiagnosticSupport.beginSelectionRun(paths, frozen);
        List<Map<String, Object>> tampered = new ArrayList<>(rankingCases);
        Map<String, Object> tamperedFirst = new LinkedHashMap<>(tampered.get(0));
        tamperedFirst.put("rankingSha256", sha256("fabricated"));
        tampered.set(0, tamperedFirst);
        assertThrows(IllegalArgumentException.class,
                () -> RagCandidate9DiagnosticSupport.loadQrelsAfterRanking(
                        paths, handle, frozen, tampered));
        assertEquals(0, frozen.auditedReads().qrelResourceAccessCount());
        assertEquals("RUNNING", frozen.auditedReads().diagnosticPhase());
        Map<String, Integer> invalidExecutionCounts = Map.of(
                "businessColbertCalls", 2,
                "baselineFullColbertCalls", 2,
                "projectionFullColbertCalls", 2,
                "sutEmbeddingCalls", 0,
                "businessColbertEmbeddingCalls", 1,
                "baselineFullColbertEmbeddingCalls", 1,
                "projectionFullColbertEmbeddingCalls", 1);
        for (Map.Entry<String, Integer> entry
                : invalidExecutionCounts.entrySet()) {
            List<Map<String, Object>> tamperedCounts =
                    new ArrayList<>(rankingCases);
            Map<String, Object> first = new LinkedHashMap<>(
                    tamperedCounts.get(0));
            first.put(entry.getKey(), entry.getValue());
            tamperedCounts.set(0, first);
            assertThrows(IllegalArgumentException.class,
                    () -> RagCandidate9DiagnosticSupport.loadQrelsAfterRanking(
                            paths, handle, frozen, tamperedCounts));
            assertEquals(0, frozen.auditedReads().qrelResourceAccessCount());
            assertEquals("RUNNING", frozen.auditedReads().diagnosticPhase());
        }
        RagCandidate9DiagnosticSupport.EvaluationView evaluation =
                RagCandidate9DiagnosticSupport.loadQrelsAfterRanking(
                        paths, handle, frozen, rankingCases);
        assertEquals(58, evaluation.dataset().qrels().values().stream()
                .mapToInt(Map::size).sum());
        assertEquals(1, evaluation.auditedReads().qrelResourceAccessCount());
        assertEquals(0,
                evaluation.auditedReads().qrelResourceAccessBeforeRanking());
        assertEquals("QRELS_LOADED",
                evaluation.auditedReads().diagnosticPhase());
        assertThrows(IllegalStateException.class,
                () -> RagCandidate9DiagnosticSupport.loadQrelsAfterRanking(
                        paths, handle, frozen, rankingCases));

        JSONObject manifest = JSON.parseObject(Files.readString(
                RagCandidate9DiagnosticSupport.paths(runtime)
                        .selectionManifest()));
        assertEquals("ascii-space-visible-identifier-v1",
                manifest.getJSONObject("structure")
                        .getString("queryVisibilityPolicy"));
        assertEquals(28, manifest.getJSONObject("structure")
                .getLongValue("projectionInsertionCount"));
        assertEquals(26, manifest.getJSONObject("structure")
                .getLongValue("encodedDocumentTokenDelta"));
        String manifestText = Files.readString(
                RagCandidate9DiagnosticSupport.paths(runtime)
                        .selectionManifest());
        assertFalse(manifestText.contains("\n"));
        assertFalse(manifestText.contains("\r"));
    }

    @Test
    @DisabledIfSystemProperty(
            named = RagCandidate9DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void rejectsExpandedDatasetDriftBeforeQrelsBecomeAvailable() throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("expanded-drift-runtime");
        RagCandidate9DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        RagCandidate9DiagnosticSupport.FrozenDataset original =
                RagCandidate9DiagnosticSupport.loadFrozenSelection(
                        runtime, fixture.selection());
        RagEvaluationDataset dataset = original.dataset();
        RagEvaluationDataset.CorpusSegment pressure = dataset.corpusById()
                .values().stream()
                .filter(segment -> "pressure-distractor".equals(
                        segment.metadata().get("candidate9Role")))
                .findFirst().orElseThrow();
        Map<String, RagEvaluationDataset.CorpusSegment> corpus =
                new LinkedHashMap<>(dataset.corpusById());
        corpus.put(pressure.segmentId(), new RagEvaluationDataset.CorpusSegment(
                pressure.segmentId(), pressure.documentId(),
                pressure.content() + " drift", pressure.parentSegmentId(),
                pressure.metadata()));
        RagEvaluationDataset drifted = new RagEvaluationDataset(
                corpus, dataset.queries(), dataset.qrels());
        RagCandidate9DiagnosticSupport.FrozenDataset frozen =
                replaceFrozenDataset(original, drifted);

        assertFrozenStructureMismatchPublishesInvalid(runtime, frozen);
    }

    @Test
    @DisabledIfSystemProperty(
            named = RagCandidate9DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void rejectsSelfConsistentManifestStructureDrift() throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("manifest-structure-drift-runtime");
        RagCandidate9DiagnosticSupport.FrozenManifests manifests =
                RagCandidate9DiagnosticSupport.freezeDatasets(
                        runtime, fixture.selection(), fixture.holdout());
        RagCandidate9DiagnosticSupport.Manifest selection = manifests.selection();
        Map<String, Object> structure = new LinkedHashMap<>(
                selection.structure());
        structure.put("expandedDatasetHash", sha256("stale-expanded-dataset"));
        String datasetHash = ReflectionTestUtils.invokeMethod(
                RagCandidate9DiagnosticSupport.class,
                "manifestDatasetHash",
                selection.generator(), selection.version(), selection.seed(),
                selection.resources(), selection.counts(), structure);
        RagCandidate9DiagnosticSupport.Manifest tampered =
                new RagCandidate9DiagnosticSupport.Manifest(
                        selection.dataset(), selection.freezeStatus(),
                        selection.generator(), selection.version(), selection.seed(),
                        selection.resources(), selection.counts(),
                        Map.copyOf(structure), datasetHash);
        Map<String, Object> manifest = ReflectionTestUtils.invokeMethod(
                RagCandidate9DiagnosticSupport.class, "manifestMap", tampered);
        Files.writeString(
                RagCandidate9DiagnosticSupport.paths(runtime).selectionManifest(),
                JSON.toJSONString(manifest), StandardCharsets.UTF_8);
        RagCandidate9DiagnosticSupport.FrozenDataset frozen =
                RagCandidate9DiagnosticSupport.loadFrozenSelection(
                        runtime, fixture.selection());

        assertFrozenStructureMismatchPublishesInvalid(runtime, frozen);
    }

    @Test
    @DisabledIfSystemProperty(
            named = RagCandidate9DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void detachedRecoveryBindingLoadsQrelsAndFinalizesWithoutLegacyLedger()
            throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("detached-recovery-runtime");
        RagCandidate9DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        RagCandidate9DiagnosticSupport.FrozenDataset frozen =
                RagCandidate9DiagnosticSupport.loadFrozenSelection(
                        runtime, fixture.selection());
        RagCandidate9DiagnosticSupport.RuntimePaths paths =
                RagCandidate9DiagnosticSupport.paths(runtime);
        RagCandidate91RecoverySupport.RecoveryRunHandle recoveryRun =
                RagCandidate91RecoverySupportTest.newRecoveryRun(
                        temp.resolve("detached-recovery-state"));
        RagCandidate9DiagnosticSupport.RecoveryBinding binding =
                RagCandidate91RecoverySupport.bindFrozenSelection(
                        recoveryRun, paths, frozen);

        assertEquals(recoveryRun.ledgerSha256(),
                binding.recoveryLedgerSha256());
        assertEquals(4, frozen.auditedReads().manifestAccessCount());
        assertEquals("RUNNING", frozen.auditedReads().diagnosticPhase());
        assertFalse(Files.exists(paths.ledger()));
        assertFalse(Files.exists(paths.diagnostic()));

        List<Map<String, Object>> rankingCases = frozen.dataset().queries().stream()
                .map(RagCandidate9DiagnosticSupportTest::emptyRankingCase)
                .toList();
        assertThrows(IllegalStateException.class, () ->
                RagCandidate9DiagnosticSupport.loadQrelsAfterRanking(
                        binding, frozen, rankingCases));
        assertEquals(0, frozen.auditedReads().qrelResourceAccessCount());
        assertEquals("RUNNING", frozen.auditedReads().diagnosticPhase());
        RagCandidate9DiagnosticSupport.EvaluationView evaluation =
                RagCandidate91RecoverySupport.loadQrelsAfterRankingForRecovery(
                        recoveryRun, binding, frozen, rankingCases);
        assertEquals(RagCandidate9DiagnosticSupport.rankingPhaseHash(
                        rankingCases),
                evaluation.rankingPhaseSha256());
        assertEquals(1, evaluation.auditedReads().qrelResourceAccessCount());
        assertEquals("QRELS_LOADED",
                evaluation.auditedReads().diagnosticPhase());

        Map<String, Object> artifact =
                RagCandidate9DiagnosticSupport.freshValidArtifact(
                        binding, frozen, config(), rankingCases);
        assertEquals("VALID", artifact.get("status"));
        assertEquals(RagCandidate9DiagnosticSupport.STOP_DECISION,
                artifact.get("decision"));
        RagCandidate9DiagnosticSupport.validateRecoveryArtifact(
                binding, frozen, artifact);
        assertThrows(IllegalStateException.class,
                () -> RagCandidate91RecoverySupport
                        .loadQrelsAfterRankingForRecovery(
                                recoveryRun, binding, frozen, rankingCases));
        assertEquals(1, frozen.auditedReads().qrelResourceAccessCount());
        assertFalse(Files.exists(paths.ledger()));
        assertFalse(Files.exists(paths.diagnostic()));

        Map<String, Object> forbiddenConfig = new LinkedHashMap<>(config());
        forbiddenConfig.put("exceptionMessage", "must-not-be-persisted");
        assertThrows(IllegalStateException.class,
                () -> RagCandidate9DiagnosticSupport.freshInvalidArtifact(
                        binding, frozen, forbiddenConfig,
                        "CANDIDATE9_RECOVERY_RUNTIME_INVALID"));
    }

    @Test
    @DisabledIfSystemProperty(
            named = RagCandidate9DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void detachedRecoveryRejectsRankingAndBindingMismatchBeforeQrels()
            throws Exception {
        Fixture fixture = copyFixture();
        Path firstRuntime = temp.resolve("recovery-binding-first");
        Path secondRuntime = temp.resolve("recovery-binding-second");
        RagCandidate9DiagnosticSupport.freezeDatasets(
                firstRuntime, fixture.selection(), fixture.holdout());
        RagCandidate9DiagnosticSupport.freezeDatasets(
                secondRuntime, fixture.selection(), fixture.holdout());
        RagCandidate9DiagnosticSupport.FrozenDataset first =
                RagCandidate9DiagnosticSupport.loadFrozenSelection(
                        firstRuntime, fixture.selection());
        RagCandidate9DiagnosticSupport.FrozenDataset second =
                RagCandidate9DiagnosticSupport.loadFrozenSelection(
                        secondRuntime, fixture.selection());
        RagCandidate91RecoverySupport.RecoveryRunHandle recoveryRun =
                RagCandidate91RecoverySupportTest.newRecoveryRun(
                        temp.resolve("recovery-binding-state"));
        RagCandidate9DiagnosticSupport.RecoveryBinding binding =
                RagCandidate91RecoverySupport.bindFrozenSelection(
                        recoveryRun,
                        RagCandidate9DiagnosticSupport.paths(firstRuntime), first);
        List<Map<String, Object>> firstCases = first.dataset().queries().stream()
                .map(RagCandidate9DiagnosticSupportTest::emptyRankingCase)
                .toList();
        List<Map<String, Object>> tampered = new ArrayList<>(firstCases);
        Map<String, Object> firstCase = new LinkedHashMap<>(tampered.get(0));
        firstCase.put("rankingSha256", sha256("tampered-ranking"));
        tampered.set(0, firstCase);

        assertThrows(IllegalArgumentException.class,
                () -> RagCandidate91RecoverySupport
                        .loadQrelsAfterRankingForRecovery(
                                recoveryRun, binding, first, tampered));
        assertEquals(0, first.auditedReads().qrelResourceAccessCount());
        assertEquals("RUNNING", first.auditedReads().diagnosticPhase());
        assertThrows(IllegalStateException.class,
                () -> RagCandidate91RecoverySupport
                        .loadQrelsAfterRankingForRecovery(
                                recoveryRun, binding, second, firstCases));
        assertEquals(0, second.auditedReads().qrelResourceAccessCount());
        assertEquals("MANIFEST_VERIFIED",
                second.auditedReads().diagnosticPhase());
        assertThrows(IllegalStateException.class,
                () -> RagCandidate91RecoverySupport.bindFrozenSelection(
                        recoveryRun,
                        RagCandidate9DiagnosticSupport.paths(secondRuntime), second));
        assertEquals(2, second.auditedReads().manifestAccessCount());
    }

    @Test
    @DisabledIfSystemProperty(
            named = RagCandidate9DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void detachedRecoveryBypassesCompletedLegacyLifecycleWithoutMutatingIt()
            throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("completed-legacy-recovery");
        RagCandidate9DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        RagCandidate9DiagnosticSupport.FrozenDataset frozen =
                RagCandidate9DiagnosticSupport.loadFrozenSelection(
                        runtime, fixture.selection());
        RagCandidate9DiagnosticSupport.RuntimePaths paths =
                RagCandidate9DiagnosticSupport.paths(runtime);
        Files.writeString(paths.ledger(),
                "{\"status\":\"COMPLETED\"}", StandardCharsets.UTF_8);
        Files.writeString(paths.diagnostic(),
                "{\"status\":\"INVALID\"}", StandardCharsets.UTF_8);
        byte[] ledgerBefore = Files.readAllBytes(paths.ledger());
        byte[] diagnosticBefore = Files.readAllBytes(paths.diagnostic());

        assertThrows(IllegalStateException.class, () ->
                RagCandidate9DiagnosticSupport.beginSelectionRun(paths, frozen));
        RagCandidate91RecoverySupport.RecoveryRunHandle recoveryRun =
                RagCandidate91RecoverySupportTest.newRecoveryRun(
                        temp.resolve("completed-legacy-state"));
        RagCandidate9DiagnosticSupport.RecoveryBinding binding =
                RagCandidate91RecoverySupport.bindFrozenSelection(
                        recoveryRun, paths, frozen);
        List<Map<String, Object>> rankingCases = frozen.dataset().queries().stream()
                .map(RagCandidate9DiagnosticSupportTest::emptyRankingCase)
                .toList();
        RagCandidate91RecoverySupport.loadQrelsAfterRankingForRecovery(
                recoveryRun, binding, frozen, rankingCases);
        Map<String, Object> artifact =
                RagCandidate9DiagnosticSupport.freshValidArtifact(
                        binding, frozen, config(), rankingCases);
        RagCandidate9DiagnosticSupport.validateRecoveryArtifact(
                binding, frozen, artifact);

        assertArrayEquals(ledgerBefore, Files.readAllBytes(paths.ledger()));
        assertArrayEquals(diagnosticBefore, Files.readAllBytes(paths.diagnostic()));
    }

    @Test
    void preservesFailedAtomicCreateTempAndRejectsAutomaticRecovery()
            throws Exception {
        Path runtime = temp.resolve("runtime");
        RagCandidate9DiagnosticSupport.RuntimePaths paths =
                RagCandidate9DiagnosticSupport.paths(runtime);
        Files.createDirectories(paths.freezeDirectory());
        Files.writeString(paths.ledger(), "occupied", StandardCharsets.UTF_8);

        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                RagCandidate9DiagnosticSupport.class, "atomicCreate",
                paths.ledger(), "replacement".getBytes(StandardCharsets.UTF_8)));
        assertEquals("occupied", Files.readString(paths.ledger()));
        Files.delete(paths.ledger());
        try (var files = Files.list(paths.freezeDirectory())) {
            assertTrue(files.map(path -> path.getFileName().toString())
                    .anyMatch(name -> name.startsWith("selection-ledger.json")
                            && name.endsWith(".tmp")));
        }
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> RagCandidate9DiagnosticSupport
                        .requireSelectionRunAvailable(paths));
        assertEquals("INVALID_INCOMPLETE_PRIOR_RUN", failure.getMessage());
    }

    @Test
    @DisabledIfSystemProperty(
            named = RagCandidate9DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void enrichesRankingOnlyCasesAfterQrelsAndPublishesLedger() throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("runtime");
        RagCandidate9DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        RagCandidate9DiagnosticSupport.FrozenDataset frozen =
                RagCandidate9DiagnosticSupport.loadFrozenSelection(
                        runtime, fixture.selection());
        RagCandidate9DiagnosticSupport.RuntimePaths paths =
                RagCandidate9DiagnosticSupport.paths(runtime);
        RagCandidate9DiagnosticSupport.RunHandle handle =
                RagCandidate9DiagnosticSupport.beginSelectionRun(paths, frozen);
        List<Map<String, Object>> rankingCases = frozen.dataset().queries().stream()
                .map(RagCandidate9DiagnosticSupportTest::emptyRankingCase)
                .toList();
        RagCandidate9DiagnosticSupport.loadQrelsAfterRanking(
                paths, handle, frozen, rankingCases);

        Map<String, Object> artifact =
                RagCandidate9DiagnosticSupport.freshValidArtifact(
                        frozen, config(), rankingCases);
        assertEquals("VALID", artifact.get("status"));
        assertEquals(RagCandidate9DiagnosticSupport.STOP_DECISION,
                artifact.get("decision"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> finalized =
                (List<Map<String, Object>>) artifact.get("cases");
        assertTrue(finalized.get(0).containsKey("classification"));
        assertTrue(finalized.get(0).containsKey("baselineAP@10"));

        RagCandidate9DiagnosticSupport.writeDiagnosticAndComplete(
                paths, handle, frozen, artifact);
        JSONObject ledger = JSON.parseObject(Files.readString(paths.ledger()));
        assertEquals("COMPLETED", ledger.getString("status"));
        assertEquals(ShadowContractSupport.sha256(paths.diagnostic()),
                ledger.getString("artifactSha256"));
    }

    @Test
    @DisabledIfSystemProperty(
            named = RagCandidate9DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void resourceDriftPublishesInvalidInsteadOfValid() throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("resource-drift-runtime");
        RagCandidate9DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        RagCandidate9DiagnosticSupport.FrozenDataset frozen =
                RagCandidate9DiagnosticSupport.loadFrozenSelection(
                        runtime, fixture.selection());
        RagCandidate9DiagnosticSupport.RuntimePaths paths =
                RagCandidate9DiagnosticSupport.paths(runtime);
        RagCandidate9DiagnosticSupport.RunHandle handle =
                RagCandidate9DiagnosticSupport.beginSelectionRun(paths, frozen);
        List<Map<String, Object>> rankingCases = frozen.dataset().queries().stream()
                .map(RagCandidate9DiagnosticSupportTest::emptyRankingCase)
                .toList();
        RagCandidate9DiagnosticSupport.loadQrelsAfterRanking(
                paths, handle, frozen, rankingCases);
        Map<String, Object> valid =
                RagCandidate9DiagnosticSupport.freshValidArtifact(
                        frozen, config(), rankingCases);

        Files.writeString(
                fixture.selection().resolve("corpus.jsonl"),
                "\n",
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        RagCandidate9DiagnosticSupport.FrozenInputChangedException failure =
                assertThrows(
                        RagCandidate9DiagnosticSupport
                                .FrozenInputChangedException.class,
                        () -> RagCandidate9DiagnosticSupport
                                .writeDiagnosticAndComplete(
                                        paths, handle, frozen, valid));
        assertEquals("CANDIDATE9_SELECTION_RESOURCE_HASH_MISMATCH",
                failure.errorCode());
        assertFalse(Files.exists(paths.diagnostic()));
        assertEquals("RUNNING", JSON.parseObject(
                Files.readString(paths.ledger())).getString("status"));

        Map<String, Object> invalid =
                RagCandidate9DiagnosticSupport.freshInvalidArtifact(
                        frozen, config(), failure.errorCode());
        RagCandidate9DiagnosticSupport.writeDiagnosticAndComplete(
                paths, handle, frozen, invalid);
        JSONObject artifact = JSON.parseObject(
                Files.readString(paths.diagnostic()));
        assertEquals("INVALID", artifact.getString("status"));
        assertFalse(artifact.containsKey("cases"));
        assertEquals("COMPLETED", JSON.parseObject(
                Files.readString(paths.ledger())).getString("status"));
    }

    @Test
    @EnabledIfSystemProperty(
            named = RagCandidate9DiagnosticSupport.FREEZE_PROPERTY,
            matches = "true")
    @EnabledIfSystemProperty(
            named = RagCandidate9DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".+")
    void freezesFormalDatasetsOnlyWhenExplicitlyEnabled() {
        RagCandidate9DiagnosticSupport.freezeFormalDatasets(
                Path.of("target/rag-eval"),
                Path.of(System.getProperty(
                        RagCandidate9DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY)));
    }

    private Fixture copyFixture() throws IOException {
        Path tests = testsDirectory();
        Path selection = temp.resolve("selection");
        Path holdout = temp.resolve("holdout");
        copyDataset(tests.resolve(
                "src/test/resources/rag-eval/candidate9-selection"), selection);
        copyDataset(tests.resolve("candidate9-holdout"), holdout);
        return new Fixture(selection, holdout);
    }

    private static void copyDataset(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        for (String file : List.of(
                "corpus.jsonl", "queries.jsonl", "qrels.tsv", "pressure.json")) {
            Files.copy(source.resolve(file), target.resolve(file));
        }
    }

    private static Path testsDirectory() {
        Path current = Path.of(System.getProperty("user.dir", "."))
                .toAbsolutePath().normalize();
        if ("tests".equals(String.valueOf(current.getFileName()))) {
            return current;
        }
        if (Files.isDirectory(current.resolve("tests"))) {
            return current.resolve("tests");
        }
        return current.resolve("backend/tests");
    }

    private static List<RagCandidate9DiagnosticSupport.CaseEvidence> validEvidence() {
        List<RagCandidate9DiagnosticSupport.CaseEvidence> cases = new ArrayList<>();
        List<String> shapes = List.of(
                "numeric-token", "numeric-token", "doc-prefix", "doc-prefix",
                "zero-padded", "zero-padded", "han-punctuation",
                "han-punctuation");
        for (int family = 1; family <= 8; family++) {
            String familyId = String.format("c9s-t%02d", family);
            cases.add(RagCandidate9DiagnosticSupport.CaseEvidence.target(
                    familyId + "-zh", familyId, "zh", shapes.get(family - 1),
                    false, true, 0.8D, 0.8D, 0.8D, 0.8D, true));
            cases.add(RagCandidate9DiagnosticSupport.CaseEvidence.target(
                    familyId + "-en", familyId, "en", shapes.get(family - 1),
                    true, true, 0.1D, 0.1D, 0.8D, 0.8D, false));
        }
        for (String role : List.of(
                "no-identifier", "no-exact", "existing-survivor",
                "irrelevant-exact-safety", "semantic-near-exact-lure",
                "relevant-nonexact", "multi-id-collision",
                "long-token-boundary", "boundary-negative")) {
            String familyId = "c9s-" + role;
            cases.add(RagCandidate9DiagnosticSupport.CaseEvidence.control(
                    familyId + "-zh", familyId, "zh", role, true));
            cases.add(RagCandidate9DiagnosticSupport.CaseEvidence.control(
                    familyId + "-en", familyId, "en", role, true));
        }
        assertEquals(34, cases.size());
        return List.copyOf(cases);
    }

    private static Map<String, Object> emptyRankingCase(
            RagEvaluationDataset.QueryCase query) {
        Map<String, Object> baseline = emptyArm(false);
        Map<String, Object> projection = emptyArm(
                query.strata().contains("long-token-boundary"));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("queryId", query.id());
        value.put("familyId", query.familyId());
        value.put("split", query.split());
        value.put("originalQuerySha256", sha256(query.query()));
        value.put("retrievalQuerySha256", sha256(query.retrievalQuery()));
        List<String> identifiers =
                RagCandidate9DiagnosticSupport.visibleIdentifierTerms(
                        query.retrievalQuery());
        value.put("extractedIdentifierCount", identifiers.size());
        value.put("extractedIdentifierHash",
                sha256(JSON.toJSONString(identifiers)));
        value.put("arms", Map.of(
                "BASELINE", baseline,
                "FIELD_PROJECTION", projection));
        value.put("rankingSha256",
                RagCandidate9DiagnosticSupport.rankingCaseHash(
                        query.id(), query.familyId(), baseline, projection));
        value.put("baselinePrefixMatchesSut", true);
        value.put("originalContentRestored", true);
        value.put("businessColbertCalls", 1);
        value.put("baselineFullColbertCalls", 1);
        value.put("projectionFullColbertCalls", 1);
        value.put("sutEmbeddingCalls", 1);
        value.put("businessColbertEmbeddingCalls", 0);
        value.put("baselineFullColbertEmbeddingCalls", 0);
        value.put("projectionFullColbertEmbeddingCalls", 0);
        value.put("callCounts", zeroCallCounts());
        return value;
    }

    private static Map<String, Object> emptyArm(boolean tailDisplacementVerified) {
        Map<String, Object> arm = new LinkedHashMap<>();
        arm.put("filterOutput", List.of());
        arm.put("fullColbert", List.of());
        arm.put("businessPrefix", List.of());
        arm.put("finalSources", List.of());
        arm.put("contextSegments", List.of());
        arm.put("contextSha256", sha256(""));
        arm.put("contextEmpty", true);
        arm.put("tailDisplacementVerified", tailDisplacementVerified);
        return arm;
    }

    private static Map<String, Object> zeroCallCounts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String field : List.of(
                "externalEmbeddingBatchCalls", "colbertEncodedQueryTokens",
                "colbertEncodedDocumentTokens", "llmPromptTokens",
                "llmCompletionTokens", "cost", "sutDbCalls",
                "diagnosticContextDbCalls", "addedVectorCalls",
                "addedMetadataCalls", "addedGraphCalls", "addedNetworkCalls")) {
            counts.put(field, 0L);
        }
        return counts;
    }

    private static Map<String, Object> config() {
        String providerPackage =
                "tech.qiantong.qknow.module.kmc.service.rag.rerank.";
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("candidate3Enabled", true);
        config.put("candidate9Enabled", false);
        config.put("candidate9DiagnosticEnabled", true);
        config.put("identifierAware", false);
        config.put("localRerankerEnabled", false);
        config.put("onnxRerankerEnabled", false);
        config.put("promotionEnabled", false);
        config.put("shadowEnabled", true);
        config.put("compareStable", false);
        config.put("fileEncoding", "UTF-8");
        config.put("topK", 10);
        config.put("keywordCandidateTopK", 50);
        config.put("keywordSqlLimit", 500);
        config.put("businessColbertLimit", 30);
        config.put("locale", "en-US");
        config.put("timezone", "UTC");
        config.put("colbert", Map.of(
                "enabled", true,
                "resolvedMaxTokensPerDoc", 128,
                "embeddingPlatformPresent", false,
                "embeddingBaseUrlPresent", false,
                "embeddingApiKeyPresent", false,
                "embeddingModelPresent", false));
        config.put("kbRemoteReranking", Map.of(
                "enabled", false,
                "providerPresent", false,
                "modelPresent", false));
        config.put("providerInventory", List.of(
                providerPackage + "DeterministicRerankerProvider"));
        config.put("fork", Map.of("count", 1, "reuse", false));
        config.put("executor", Map.of("core", 4, "max", 4, "queue", 32));
        config.put("native", Map.of(
                "available", false,
                "pathPresent", false,
                "noNativeLibraryPathPresent", true));
        config.put("featureSourceHash", Map.of(
                "RagRetrievalService", sha256("1"),
                "RagRerankService", sha256("2"),
                "KeywordRetriever", sha256("3"),
                "ColbertScorer", sha256("4"),
                "DeterministicRerankerProvider", sha256("5"),
                "RagContextBuilder", sha256("6")));
        config.put("candidate9ProjectionPolicy",
                "matched-visible-identifiers-prefix-v1");
        config.put("candidate9QueryVisibilityPolicy",
                "ascii-space-visible-identifier-v1");
        config.put("candidate9BoundaryPolicy",
                "unicode-letter-number-boundary-v1");
        config.put("candidate9TokenPolicy", "prefix-head-128-v1");
        config.put("candidate9EligibilityPolicy",
                "hash-colbert-deterministic-fail-closed-v1");
        config.put("candidate9FailurePolicy",
                "active-request-propagation-v1");
        config.put("candidate9EvaluationPolicy", "qrel-after-ranking-v1");
        return config;
    }

    private static Map<String, String> validDiagnosticCommandProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("rag.eval.shadow", "true");
        properties.put("rag.eval.shadow.compare-stable", "false");
        properties.put("rag.eval.identifier.diagnostic", "false");
        properties.put("rag.eval.candidate2.diagnostic", "false");
        properties.put("rag.eval.candidate3.diagnostic", "false");
        properties.put("rag.eval.candidate4.diagnostic", "false");
        properties.put("rag.eval.candidate5.diagnostic", "false");
        properties.put("rag.eval.candidate6.diagnostic", "false");
        properties.put("rag.eval.candidate8.diagnostic", "false");
        properties.put(RagCandidate9DiagnosticSupport.DIAGNOSTIC_PROPERTY, "true");
        properties.put("rag.eval.promotion", "false");
        properties.put("qknow.rag.keyword.identifier-aware", "false");
        properties.put("qknow.rag.rerank.identifier-consistency-enabled", "true");
        properties.put(RagCandidate9DiagnosticSupport.PRODUCTION_PROPERTY, "false");
        properties.put("qknow.rag.local-reranker.enabled", "false");
        properties.put("qknow.rag.onnx-reranker.enabled", "false");
        properties.put("hermes.rag.colbert.enabled", "true");
        properties.put("hermes.rag.colbert.max-tokens-per-doc", "128");
        properties.put("hermes.rag.colbert.embedding-platform", "");
        properties.put("hermes.rag.colbert.embedding-base-url", "");
        properties.put("hermes.rag.colbert.embedding-api-key", "");
        properties.put("hermes.rag.colbert.embedding-model", "");
        properties.put("forkCount", "1");
        properties.put("reuseForks", "false");
        properties.put("file.encoding", "UTF-8");
        properties.put("user.timezone", "UTC");
        properties.put("user.language", "en");
        properties.put("user.country", "US");
        properties.put("qknow.native.lib.dir", "");
        properties.put("java.library.path",
                "backend/tests/target/rag-eval/no-native");
        return Map.copyOf(properties);
    }

    private static Map<String, String> installSystemProperties(
            Map<String, String> values) {
        Map<String, String> previous = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            previous.put(key, System.getProperty(key));
            System.setProperty(key, value);
        });
        return previous;
    }

    private static void restoreSystemProperties(Map<String, String> previous) {
        previous.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
    }

    private static RagCandidate9DiagnosticSupport.FrozenDataset
            replaceFrozenDataset(
            RagCandidate9DiagnosticSupport.FrozenDataset source,
            RagEvaluationDataset dataset) throws Exception {
        Object accessCounter = ReflectionTestUtils.invokeMethod(
                source, "accessCounter");
        var constructor = RagCandidate9DiagnosticSupport.FrozenDataset.class
                .getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (RagCandidate9DiagnosticSupport.FrozenDataset)
                constructor.newInstance(
                        dataset, source.manifests(), source.datasetHash(),
                        source.selectionDirectory(), accessCounter);
    }

    private static void assertFrozenStructureMismatchPublishesInvalid(
            Path runtime,
            RagCandidate9DiagnosticSupport.FrozenDataset frozen) {
        RagCandidate9DiagnosticSupport.RuntimePaths paths =
                RagCandidate9DiagnosticSupport.paths(runtime);
        RagCandidate9DiagnosticSupport.RunHandle handle =
                RagCandidate9DiagnosticSupport.beginSelectionRun(paths, frozen);
        List<Map<String, Object>> rankingCases = frozen.dataset().queries().stream()
                .map(RagCandidate9DiagnosticSupportTest::emptyRankingCase)
                .toList();
        RagCandidate9DiagnosticSupport.FrozenInputChangedException failure =
                assertThrows(
                        RagCandidate9DiagnosticSupport
                                .FrozenInputChangedException.class,
                        () -> RagCandidate9DiagnosticSupport
                                .loadQrelsAfterRanking(
                                        paths, handle, frozen, rankingCases));
        assertEquals("CANDIDATE9_FROZEN_STRUCTURE_MISMATCH",
                failure.errorCode());
        assertEquals("RANKING_FROZEN",
                frozen.auditedReads().diagnosticPhase());
        assertEquals(1, frozen.auditedReads().qrelResourceAccessCount());
        assertFalse(Files.exists(paths.diagnostic()));

        Map<String, Object> invalid =
                RagCandidate9DiagnosticSupport.freshInvalidArtifact(
                        frozen, config(), failure.errorCode());
        RagCandidate9DiagnosticSupport.writeDiagnosticAndComplete(
                paths, handle, frozen, invalid);
        JSONObject artifact;
        JSONObject ledger;
        try {
            artifact = JSON.parseObject(Files.readString(paths.diagnostic()));
            ledger = JSON.parseObject(Files.readString(paths.ledger()));
        } catch (IOException readFailure) {
            throw new IllegalStateException(readFailure);
        }
        assertEquals("INVALID", artifact.getString("status"));
        assertEquals("CANDIDATE9_FROZEN_STRUCTURE_MISMATCH",
                artifact.getString("errorCode"));
        assertFalse(artifact.containsKey("cases"));
        assertEquals("COMPLETED", ledger.getString("status"));
    }

    private static Object readDatasetFiles(Path directory, boolean holdout) {
        return ReflectionTestUtils.invokeMethod(
                RagCandidate9DiagnosticSupport.class,
                "readDatasetFiles", directory, holdout);
    }

    private static RagEvaluationDataset dataset(Object files) {
        return ReflectionTestUtils.invokeMethod(files, "dataset");
    }

    private static RagEvaluationDataset replaceSegment(
            RagEvaluationDataset dataset,
            RagEvaluationDataset.CorpusSegment original,
            String documentId,
            Map<String, Object> metadata) {
        Map<String, RagEvaluationDataset.CorpusSegment> corpus =
                new LinkedHashMap<>(dataset.corpusById());
        corpus.put(original.segmentId(),
                new RagEvaluationDataset.CorpusSegment(
                        original.segmentId(), documentId, original.content(),
                        original.parentSegmentId(), Map.copyOf(metadata)));
        return new RagEvaluationDataset(
                Map.copyOf(corpus), dataset.queries(), dataset.qrels());
    }

    private static void assertShapeRejected(
            RagEvaluationDataset original,
            Object pressure,
            RagEvaluationDataset mutated) {
        int qrelCount = original.qrels().values().stream()
                .mapToInt(Map::size).sum();
        assertThrows(RuntimeException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        RagCandidate9DiagnosticSupport.class,
                        "validateShape", mutated, qrelCount, pressure, false));
    }

    private static boolean multiIdentifierControlValid(
            Set<Long> relevant,
            Set<Long> exact,
            Map<String, Object> baseline,
            Map<String, Object> projection) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(
                RagCandidate9DiagnosticSupport.class,
                "multiIdentifierControlValid",
                relevant, exact, baseline, projection));
    }

    private static Map<String, Object> multiIdArm(
            List<Map<String, Object>> finalSources,
            List<Map<String, Object>> contextSegments) {
        Map<String, Object> arm = new LinkedHashMap<>();
        arm.put("finalSources", finalSources);
        arm.put("contextSegments", contextSegments);
        arm.put("contextEmpty", contextSegments.isEmpty());
        return arm;
    }

    private static Map<String, Object> stageItem(
            long segmentId, int rank, double score) {
        return Map.of(
                "segmentId", segmentId,
                "rank", rank,
                "score", score,
                "exactMatch", false);
    }

    private static String sha256(String value) {
        return ShadowContractSupport.sha256(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private record Fixture(Path selection, Path holdout) {
    }

    private record DatasetFixture(Path directory, boolean holdout) {
    }
}
