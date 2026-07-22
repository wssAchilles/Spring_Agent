package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RagCandidate5DiagnosticSupportTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.1-pg16@sha256:33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337")
            .asCompatibleSubstituteFor("postgres");

    @TempDir
    Path temp;

    private String configuredHoldoutDirectory;
    private String configuredUserDirectory;

    @BeforeEach
    void isolateSelectionJobProperties() {
        configuredHoldoutDirectory = System.getProperty(
                RagCandidate5DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY);
        configuredUserDirectory = System.getProperty("user.dir");
        System.clearProperty(RagCandidate5DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY);
    }

    @AfterEach
    void restoreSelectionJobProperties() {
        restoreProperty(RagCandidate5DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
                configuredHoldoutDirectory);
        restoreProperty("user.dir", configuredUserDirectory);
    }

    @Test
    void fixesPathsIdsAndFiveWayDiagnosticExclusion() {
        RagCandidate5DiagnosticSupport.RuntimePaths paths =
                RagCandidate5DiagnosticSupport.paths(temp);

        assertEquals(temp.resolve("candidate5-freeze"), paths.freezeDirectory());
        assertEquals(paths.freezeDirectory().resolve("selection-manifest.json"),
                paths.selectionManifest());
        assertEquals(paths.freezeDirectory().resolve("holdout-manifest.json"),
                paths.holdoutManifest());
        assertEquals(paths.freezeDirectory().resolve("selection-ledger.json"), paths.ledger());
        assertEquals(temp.resolve("candidate5-calibration-diagnostic.json"), paths.diagnostic());
        assertEquals(9_960_000L, RagCandidate5DiagnosticSupport.SELECTION_KB_ID);
        assertEquals(9_970_000L, RagCandidate5DiagnosticSupport.HOLDOUT_KB_ID);
        assertEquals(0, RagCandidate5DiagnosticSupport.enabledDiagnosticCount(
                false, false, false, false, false));
        assertEquals(2, RagCandidate5DiagnosticSupport.enabledDiagnosticCount(
                true, false, false, true, false));
    }

    @Test
    void selectionRejectsAnyHoldoutPropertyAndNeverClearsStartedEvidence() throws Exception {
        System.setProperty(RagCandidate5DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY, "");
        assertEquals("CANDIDATE5_HOLDOUT_ACCESS_FORBIDDEN",
                assertThrows(IllegalStateException.class,
                        RagCandidate5DiagnosticSupport::requireSelectionJobProperties).getMessage());
        System.clearProperty(RagCandidate5DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY);

        RagCandidate5DiagnosticSupport.RuntimePaths paths =
                RagCandidate5DiagnosticSupport.paths(temp);
        Files.createDirectories(paths.freezeDirectory());
        Files.writeString(paths.selectionManifest(), "selection", StandardCharsets.UTF_8);
        Files.writeString(paths.holdoutManifest(), "holdout", StandardCharsets.UTF_8);
        Files.writeString(paths.diagnostic(), "diagnostic", StandardCharsets.UTF_8);

        RagCandidate5DiagnosticSupport.clearDiagnostic(paths);

        assertFalse(Files.exists(paths.diagnostic()));
        Files.writeString(paths.ledger(), "{\"status\":\"RUNNING\"}", StandardCharsets.UTF_8);
        Files.writeString(paths.diagnostic(), "running evidence", StandardCharsets.UTF_8);
        RagCandidate5DiagnosticSupport.clearDiagnostic(paths);
        assertEquals("running evidence", Files.readString(paths.diagnostic()));

        Files.writeString(paths.ledger(), "{\"status\":\"COMPLETED\"}", StandardCharsets.UTF_8);
        Files.writeString(paths.diagnostic(), "completed evidence", StandardCharsets.UTF_8);
        RagCandidate5DiagnosticSupport.clearDiagnostic(paths);
        assertEquals("completed evidence", Files.readString(paths.diagnostic()));
        assertTrue(Files.exists(paths.selectionManifest()));
        assertTrue(Files.exists(paths.holdoutManifest()));
        assertTrue(Files.exists(paths.ledger()));
    }

    @Test
    void freezesExactSharedDatasetShapeAndRemapsBasePerArm() throws Exception {
        Fixture fixture = writeFixtureTree(temp.resolve("fixture"));
        Path runtime = temp.resolve("runtime");

        RagCandidate5DiagnosticSupport.FrozenManifests frozen =
                RagCandidate5DiagnosticSupport.freezeDatasets(
                        runtime, fixture.selection(), fixture.holdout(), fixture.baseCorpus());

        assertEquals(Map.of(
                "familyCount", 10,
                "queryCount", 20,
                "documentCount", 96,
                "segmentCount", 96,
                "qrelCount", 40), frozen.selection().counts());
        assertEquals(Map.of(
                "familyCount", 16,
                "queryCount", 32,
                "documentCount", 126,
                "segmentCount", 126,
                "qrelCount", 76), frozen.holdout().counts());
        assertEquals(6, frozen.selection().structure().get("targetFamilyCount"));
        assertEquals(18, frozen.selection().structure().get("targetEvidenceCount"));
        assertEquals(64, frozen.selection().structure().get("baseDistractorCount"));
        assertEquals(Boolean.TRUE,
                frozen.selection().structure().get("bilingualFamilySharesRelevantSegments"));
        assertEquals("source-segment-order-to-reserved-prefix-v1",
                ((Map<?, ?>) frozen.selection().structure().get("baseRemapRule"))
                        .get("policy"));
        assertTrue(frozen.selection().structure().get("mergedIdMappingHash") instanceof String);
        assertEquals(64, ((Map<?, ?>) frozen.selection().structure().get("baseRemapRule"))
                .get("count"));
        assertEquals("FROZEN_NOT_BLIND", frozen.holdout().freezeStatus());

        RagCandidate5DiagnosticSupport.FrozenDataset selection =
                RagCandidate5DiagnosticSupport.loadFrozenSelection(
                        RagCandidate5DiagnosticSupport.paths(runtime),
                        fixture.selection(), fixture.baseCorpus());
        RagCandidate5DiagnosticSupport.FrozenDataset holdout =
                RagCandidate5DiagnosticSupport.loadFrozenHoldout(
                        RagCandidate5DiagnosticSupport.paths(runtime),
                        fixture.holdout(), fixture.baseCorpus());

        assertEquals(96, selection.dataset().corpusById().size());
        assertEquals(126, holdout.dataset().corpusById().size());
        assertTrue(selection.dataset().corpusById().keySet().stream()
                .mapToLong(Long::parseLong)
                .allMatch(value -> value >= 9_960_001L && value <= 9_964_999L));
        assertTrue(selection.dataset().corpusById().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId)
                .mapToLong(Long::parseLong)
                .allMatch(value -> value >= 9_965_000L && value <= 9_969_999L));
        assertTrue(holdout.dataset().corpusById().keySet().stream()
                .mapToLong(Long::parseLong)
                .allMatch(value -> value >= 9_970_001L && value <= 9_974_999L));
        assertTrue(holdout.dataset().corpusById().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId)
                .mapToLong(Long::parseLong)
                .allMatch(value -> value >= 9_975_000L && value <= 9_979_999L));
        assertEquals(List.of(
                "selection-manifest.json", "holdout-manifest.json",
                "candidate5-selection/corpus.jsonl", "candidate5-selection/queries.jsonl",
                "candidate5-selection/qrels.tsv",
                "candidate5-base-distractor/corpus.jsonl"), selection.auditedReads());
        assertFalse(JSON.toJSONString(frozen.holdout()).contains("candidate5-holdout/"));
    }

    @Test
    void rejectsResourceMutationDuplicateQrelAndOverlappingIds() throws Exception {
        Fixture fixture = writeFixtureTree(temp.resolve("mutable"));
        Path runtime = temp.resolve("mutable-runtime");
        RagCandidate5DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout(), fixture.baseCorpus());

        Files.writeString(fixture.selection().resolve("queries.jsonl"), "\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        assertEquals("CANDIDATE5_SELECTION_RESOURCE_HASH_MISMATCH",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.loadFrozenSelection(
                                RagCandidate5DiagnosticSupport.paths(runtime),
                                fixture.selection(), fixture.baseCorpus())).getMessage());

        Fixture duplicate = writeFixtureTree(temp.resolve("duplicate"));
        String firstQrel = Files.readAllLines(
                duplicate.selection().resolve("qrels.tsv"), StandardCharsets.UTF_8).get(1);
        Files.writeString(duplicate.selection().resolve("qrels.tsv"),
                firstQrel + "\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        assertEquals("CANDIDATE5_DUPLICATE_QREL",
                assertThrows(IllegalArgumentException.class,
                        () -> RagCandidate5DiagnosticSupport.freezeDatasets(
                                temp.resolve("duplicate-runtime"), duplicate.selection(),
                                duplicate.holdout(), duplicate.baseCorpus())).getMessage());

        Fixture overlap = writeFixtureTree(temp.resolve("overlap"));
        String holdoutCorpus = Files.readString(
                overlap.holdout().resolve("corpus.jsonl"), StandardCharsets.UTF_8)
                .replaceFirst("9970065", "9960065");
        Files.writeString(overlap.holdout().resolve("corpus.jsonl"),
                holdoutCorpus, StandardCharsets.UTF_8);
        Files.writeString(overlap.holdout().resolve("qrels.tsv"),
                Files.readString(overlap.holdout().resolve("qrels.tsv"), StandardCharsets.UTF_8)
                        .replace("9970065", "9960065"),
                StandardCharsets.UTF_8);
        assertEquals("CANDIDATE5_CORPUS_ISOLATION_INVALID",
                assertThrows(IllegalArgumentException.class,
                        () -> RagCandidate5DiagnosticSupport.freezeDatasets(
                                temp.resolve("overlap-runtime"), overlap.selection(),
                                overlap.holdout(), overlap.baseCorpus())).getMessage());

        Fixture badControl = writeFixtureTree(temp.resolve("bad-control"));
        Path queries = badControl.selection().resolve("queries.jsonl");
        List<String> changed = Files.readAllLines(queries, StandardCharsets.UTF_8).stream()
                .map(line -> {
                    JSONObject query = JSON.parseObject(line);
                    if ("c5s-n01-en".equals(query.getString("id"))) {
                        query.put("retrievalQuery", "Topic 7611 compass protocol evidence");
                    }
                    return JSON.toJSONString(query);
                }).toList();
        Files.writeString(queries, String.join("\n", changed) + "\n", StandardCharsets.UTF_8);
        assertEquals("CANDIDATE5_NO_IDENTIFIER_CONTROL_INVALID",
                assertThrows(IllegalArgumentException.class,
                        () -> RagCandidate5DiagnosticSupport.freezeDatasets(
                                temp.resolve("bad-control-runtime"), badControl.selection(),
                                badControl.holdout(), badControl.baseCorpus())).getMessage());

        Fixture lexicalLeak = writeFixtureTree(temp.resolve("lexical-leak"));
        Path corpus = lexicalLeak.selection().resolve("corpus.jsonl");
        List<String> leakyCorpus = Files.readAllLines(corpus, StandardCharsets.UTF_8).stream()
                .map(line -> {
                    JSONObject segment = JSON.parseObject(line);
                    if ("9960065".equals(segment.getString("segmentId"))) {
                        segment.put("content", "topic evidence family slug");
                    }
                    return JSON.toJSONString(segment);
                }).toList();
        Files.writeString(corpus, String.join("\n", leakyCorpus) + "\n",
                StandardCharsets.UTF_8);
        assertEquals("CANDIDATE5_ANCHOR_CONTENT_LEXICAL_LEAK",
                assertThrows(IllegalArgumentException.class,
                        () -> RagCandidate5DiagnosticSupport.freezeDatasets(
                                temp.resolve("lexical-leak-runtime"), lexicalLeak.selection(),
                                lexicalLeak.holdout(), lexicalLeak.baseCorpus())).getMessage());
    }

    @Test
    void ledgerCompletesAtomicallyAndRejectsStaleOrTamperedEvidence() throws Exception {
        Fixture fixture = writeFixtureTree(temp.resolve("ledger-fixture"));
        Path runtime = temp.resolve("ledger-runtime");
        RagCandidate5DiagnosticSupport.FrozenManifests manifests =
                RagCandidate5DiagnosticSupport.freezeDatasets(
                        runtime, fixture.selection(), fixture.holdout(), fixture.baseCorpus());
        RagCandidate5DiagnosticSupport.RuntimePaths paths =
                RagCandidate5DiagnosticSupport.paths(runtime);
        RagCandidate5DiagnosticSupport.FrozenDataset frozenSelection =
                RagCandidate5DiagnosticSupport.loadFrozenSelection(
                        paths, fixture.selection(), fixture.baseCorpus());
        RagCandidate5DiagnosticSupport.RunHandle handle =
                RagCandidate5DiagnosticSupport.beginSelectionRun(paths, manifests);

        assertEquals("RUNNING",
                JSON.parseObject(Files.readString(paths.ledger())).getString("status"));
        byte[] runningBytes = Files.readAllBytes(paths.ledger());
        assertEquals("INVALID_INCOMPLETE_PRIOR_RUN",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.requireSelectionRunAvailable(paths))
                        .getMessage());
        assertEquals("INVALID_INCOMPLETE_PRIOR_RUN",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.beginSelectionRun(paths, manifests))
                        .getMessage());
        assertArrayEquals(runningBytes, Files.readAllBytes(paths.ledger()));

        Map<String, Object> artifact = RagCandidate5DiagnosticSupport.freshValidArtifact(
                frozenSelection,
                lockedConfig(),
                "STOP_MULTILINGUAL_IDENTIFIER_UNSUPPORTED",
                diagnosticSummary(selectionEvidence(false)),
                caseMaps(selectionEvidence(false)));
        String artifactHash = RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(
                paths, handle, artifact);

        JSONObject completed = JSON.parseObject(Files.readString(paths.ledger()));
        assertEquals("COMPLETED", completed.getString("status"));
        assertEquals(artifactHash, completed.getString("artifactSha256"));
        RagCandidate5DiagnosticSupport.verifyCompletedRun(paths);
        byte[] completedBytes = Files.readAllBytes(paths.ledger());
        assertEquals("CANDIDATE5_SELECTION_ALREADY_COMPLETED",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.beginSelectionRun(paths, manifests))
                        .getMessage());
        assertArrayEquals(completedBytes, Files.readAllBytes(paths.ledger()));

        Files.writeString(paths.diagnostic(), "{}", StandardCharsets.UTF_8);
        assertEquals("CANDIDATE5_ARTIFACT_HASH_MISMATCH",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.verifyCompletedRun(paths)).getMessage());
    }

    @Test
    void concurrentLedgerBeginHasExactlyOneWinner() throws Exception {
        Fixture fixture = writeFixtureTree(temp.resolve("concurrent-ledger-fixture"));
        Path runtime = temp.resolve("concurrent-ledger-runtime");
        RagCandidate5DiagnosticSupport.FrozenManifests manifests =
                RagCandidate5DiagnosticSupport.freezeDatasets(
                        runtime, fixture.selection(), fixture.holdout(), fixture.baseCorpus());
        RagCandidate5DiagnosticSupport.RuntimePaths paths =
                RagCandidate5DiagnosticSupport.paths(runtime);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> begin = () -> {
                ready.countDown();
                start.await();
                try {
                    RagCandidate5DiagnosticSupport.beginSelectionRun(paths, manifests);
                    return true;
                } catch (IllegalStateException expected) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(begin);
            Future<Boolean> second = executor.submit(begin);
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            JSONObject ledger = JSON.parseObject(Files.readString(paths.ledger()));
            assertEquals("RUNNING", ledger.getString("status"));
            assertEquals(manifests.selectionSha256(),
                    ledger.getString("selectionManifestSha256"));
            assertEquals(manifests.holdoutSha256(),
                    ledger.getString("holdoutManifestSha256"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void canonicalJsonIsIndependentOfMapInsertionOrder() {
        Map<String, Object> firstNested = new LinkedHashMap<>();
        firstNested.put("zeta", 2);
        firstNested.put("alpha", 1);
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("resources", firstNested);
        first.put("status", "RUNNING");

        Map<String, Object> secondNested = new LinkedHashMap<>();
        secondNested.put("alpha", 1);
        secondNested.put("zeta", 2);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("status", "RUNNING");
        second.put("resources", secondNested);

        assertEquals(
                RagCandidate5DiagnosticSupport.atomicJson(first),
                RagCandidate5DiagnosticSupport.atomicJson(second));
    }

    @Test
    void concurrentCompletionHasExactlyOneWriterAndCoherentEvidence() throws Exception {
        Fixture fixture = writeFixtureTree(temp.resolve("completion-fixture"));
        Path runtime = temp.resolve("completion-runtime");
        RagCandidate5DiagnosticSupport.FrozenManifests manifests =
                RagCandidate5DiagnosticSupport.freezeDatasets(
                        runtime, fixture.selection(), fixture.holdout(), fixture.baseCorpus());
        RagCandidate5DiagnosticSupport.RuntimePaths paths =
                RagCandidate5DiagnosticSupport.paths(runtime);
        RagCandidate5DiagnosticSupport.FrozenDataset frozenSelection =
                RagCandidate5DiagnosticSupport.loadFrozenSelection(
                        paths, fixture.selection(), fixture.baseCorpus());
        RagCandidate5DiagnosticSupport.RunHandle handle =
                RagCandidate5DiagnosticSupport.beginSelectionRun(paths, manifests);
        List<RagCandidate5DiagnosticSupport.CaseEvidence> evidence = selectionEvidence(false);
        Map<String, Object> artifact = RagCandidate5DiagnosticSupport.freshValidArtifact(
                frozenSelection,
                lockedConfig(),
                "STOP_MULTILINGUAL_IDENTIFIER_UNSUPPORTED",
                diagnosticSummary(evidence),
                caseMaps(evidence));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> complete = () -> {
                ready.countDown();
                start.await();
                try {
                    RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(
                            paths, handle, artifact);
                    return true;
                } catch (IllegalStateException expected) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(complete);
            Future<Boolean> second = executor.submit(complete);
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            RagCandidate5DiagnosticSupport.verifyCompletedRun(paths);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void artifactFactoriesAreFreshAndRejectPlaintextEvidence() throws Exception {
        Fixture fixture = writeFixtureTree(temp.resolve("artifact-fixture"));
        RagCandidate5DiagnosticSupport.FrozenManifests manifests =
                RagCandidate5DiagnosticSupport.freezeDatasets(
                        temp.resolve("artifact-runtime"), fixture.selection(),
                        fixture.holdout(), fixture.baseCorpus());
        RagCandidate5DiagnosticSupport.RuntimePaths paths =
                RagCandidate5DiagnosticSupport.paths(temp.resolve("artifact-runtime"));
        RagCandidate5DiagnosticSupport.FrozenDataset frozenSelection =
                RagCandidate5DiagnosticSupport.loadFrozenSelection(
                        paths, fixture.selection(), fixture.baseCorpus());
        Map<String, Object> config = lockedConfig();
        List<RagCandidate5DiagnosticSupport.CaseEvidence> evidence = selectionEvidence(true);
        List<Map<String, Object>> cases = caseMaps(evidence);
        Map<String, Object> summary = diagnosticSummary(evidence);

        Map<String, Object> valid = RagCandidate5DiagnosticSupport.freshValidArtifact(
                frozenSelection, config,
                "PROCEED_TO_IDENTIFIER_RECALL_RED",
                summary, cases);
        assertEquals("VALID", valid.get("status"));
        assertEquals(null, valid.get("errorCode"));
        assertTrue(cases.get(0).keySet().containsAll(Set.of(
                "target", "extractorContainsIdentifier", "documentFieldsConsistent",
                "keywordReturnedAnchor", "finalReturnedAnchor", "behaviorUnchanged")));

        Map<String, Object> invalid = RagCandidate5DiagnosticSupport.freshInvalidArtifact(
                frozenSelection, config,
                "CANDIDATE5_SQL_EXECUTION_FAILED");
        assertEquals("INVALID", invalid.get("status"));
        assertFalse(invalid.containsKey("cases"));
        assertFalse(invalid.containsKey("summary"));
        assertEquals(null, invalid.get("decision"));

        Map<String, Object> leaked = new LinkedHashMap<>(valid);
        leaked.put("cases", List.of(Map.of("query", "secret query")));
        RagCandidate5DiagnosticSupport.RunHandle handle =
                RagCandidate5DiagnosticSupport.beginSelectionRun(paths, manifests);
        Map<String, Object> missingLockedConfig =
                RagCandidate5DiagnosticSupport.freshValidArtifact(
                        frozenSelection,
                        Map.of("identifierAware", false),
                        "PROCEED_TO_IDENTIFIER_RECALL_RED", summary, cases);
        assertEquals("CANDIDATE5_DIAGNOSTIC_CONFIG_INVALID",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(
                                paths, handle, missingLockedConfig)).getMessage());

        Map<String, Object> configWithPlaintext = new LinkedHashMap<>(config);
        configWithPlaintext.put("queryText", "secret query");
        Map<String, Object> unexpectedConfig =
                RagCandidate5DiagnosticSupport.freshValidArtifact(
                        frozenSelection, configWithPlaintext,
                        "PROCEED_TO_IDENTIFIER_RECALL_RED", summary, cases);
        assertEquals("CANDIDATE5_DIAGNOSTIC_CONFIG_INVALID",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(
                                paths, handle, unexpectedConfig)).getMessage());

        Map<String, Object> mismatchedDecision = new LinkedHashMap<>(valid);
        mismatchedDecision.put("decision", "STOP_MULTILINGUAL_IDENTIFIER_UNSUPPORTED");
        assertEquals("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(
                                paths, handle, mismatchedDecision)).getMessage());

        Map<String, Object> unexpectedSummary = new LinkedHashMap<>(valid);
        Map<String, Object> summaryWithExtra = new LinkedHashMap<>(summary);
        summaryWithExtra.put("notes", "not allowlisted");
        unexpectedSummary.put("summary", summaryWithExtra);
        assertEquals("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(
                                paths, handle, unexpectedSummary)).getMessage());

        Map<String, Object> unexpectedCase = new LinkedHashMap<>(valid);
        List<Map<String, Object>> casesWithExtra = new ArrayList<>(cases);
        Map<String, Object> firstWithExtra = new LinkedHashMap<>(casesWithExtra.get(0));
        firstWithExtra.put("debug", true);
        casesWithExtra.set(0, firstWithExtra);
        unexpectedCase.put("cases", casesWithExtra);
        assertEquals("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(
                                paths, handle, unexpectedCase)).getMessage());

        Map<String, Object> forgedControl = new LinkedHashMap<>(valid);
        List<Map<String, Object>> forgedCases = new ArrayList<>(cases);
        int controlIndex = java.util.stream.IntStream.range(0, forgedCases.size())
                .filter(index -> "c5s-n01-en".equals(
                        forgedCases.get(index).get("queryId")))
                .findFirst().orElseThrow();
        Map<String, Object> control = new LinkedHashMap<>(forgedCases.get(controlIndex));
        control.put("retrievalContainsIdentifier", true);
        control.put("fullPathIdentifierPresence", List.of(true));
        forgedCases.set(controlIndex, control);
        forgedControl.put("cases", forgedCases);
        assertEquals("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(
                                paths, handle, forgedControl)).getMessage());

        Map<String, Object> stale = new LinkedHashMap<>(valid);
        stale.put("oldDatasetHash", "c".repeat(64));
        assertEquals("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(
                                paths, handle, stale)).getMessage());
        assertEquals("CANDIDATE5_DIAGNOSTIC_FORBIDDEN_FIELD",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(
                                paths, handle, leaked)).getMessage());
        assertFalse(Files.exists(paths.diagnostic()));
        RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(paths, handle, valid);
    }

    @Test
    void classificationsAreOrderedAndDecisionUsesFamiliesNotOracleScores() {
        assertEquals(RagCandidate5DiagnosticSupport.RootCause
                        .RETRIEVAL_MISS_FIXTURE_INCONSISTENT,
                evidence("fixture", "f0", "en", "numeric-token", true,
                        false, true, false, false, false, false).classification());
        assertEquals(RagCandidate5DiagnosticSupport.RootCause.IDENTIFIER_EXTRACTION_MISS,
                evidence("extract", "f0", "en", "numeric-token", true,
                        true, false, false, false, false, false).classification());
        assertEquals(RagCandidate5DiagnosticSupport.RootCause.KEYWORD_TERM_NOT_EMITTED,
                evidence("term", "f0", "en", "numeric-token", true,
                        true, true, false, false, false, false).classification());
        assertEquals(RagCandidate5DiagnosticSupport.RootCause.KEYWORD_SQL_IDENTIFIER_MISS,
                evidence("sql", "f0", "en", "numeric-token", true,
                        true, true, true, true, false, false).classification());
        assertEquals(RagCandidate5DiagnosticSupport.RootCause.OTHER_RETRIEVAL_PATH_MISS,
                evidence("other", "f0", "en", "numeric-token", true,
                        true, true, true, true, true, false).classification());
        assertEquals(RagCandidate5DiagnosticSupport.RootCause.NONE,
                evidence("none", "f0", "en", "numeric-token", true,
                        true, true, true, true, true, true).classification());

        List<RagCandidate5DiagnosticSupport.CaseEvidence> cases = new ArrayList<>();
        String[] shapes = {"numeric-token", "doc-prefix", "han-zero-padded", "numeric-token"};
        for (int index = 0; index < shapes.length; index++) {
            String family = "target-" + index;
            cases.add(evidence(family + "-en", family, "en", shapes[index], true,
                    true, true, false, false, false, false));
            cases.add(evidence(family + "-zh", family, "zh", shapes[index], true,
                    true, true, true, true, true, false));
        }
        cases.add(evidence("control-en", "control", "en", "none", false,
                true, true, true, true, false, false));
        cases.add(evidence("control-zh", "control", "zh", "none", false,
                true, true, true, true, false, false));

        assertEquals("PROCEED_TO_IDENTIFIER_RECALL_RED",
                RagCandidate5DiagnosticSupport.decide(cases));

        List<RagCandidate5DiagnosticSupport.CaseEvidence> differentOracle =
                cases.stream().map(item -> item.withOracleRecoverableUpperBound(
                        new RagMetrics.Scores(0.0D, 0.0D, 0.0D, 0.0D, 0.0D))).toList();
        assertEquals("PROCEED_TO_IDENTIFIER_RECALL_RED",
                RagCandidate5DiagnosticSupport.decide(differentOracle));

        cases.set(0, evidence("bad-en", "target-0", "en", "numeric-token", true,
                true, false, false, false, false, false));
        assertEquals("STOP_MULTILINGUAL_IDENTIFIER_UNSUPPORTED",
                RagCandidate5DiagnosticSupport.decide(cases));
    }

    @Test
    void documentCopiesDoNotShareMetadata() {
        Document original = Document.builder().id("9960001").text("evidence")
                .metadata(new LinkedHashMap<>(Map.of("score", 0.4D))).build();

        List<Document> detached =
                RagCandidate5DiagnosticSupport.copyDocuments(List.of(original));
        detached.get(0).getMetadata().put("score", 1.0D);

        assertEquals(0.4D, ((Number) original.getMetadata().get("score")).doubleValue());
        assertNotEquals(original.getMetadata(), detached.get(0).getMetadata());
    }

    @Test
    void postgres16CLocaleLocksDocumentNameBoundarySemantics() {
        assumeTrue(Boolean.getBoolean(RagCandidate5DiagnosticSupport.SQL_BOUNDARY_PROPERTY));
        verifyPostgresBoundarySemantics();
    }

    @Test
    void unitTestsDoNotConsumeFormalLedger() throws Exception {
        Path formalLedger = RagCandidate5DiagnosticSupport.paths(
                RagLiveEvaluationSupport.runtimeDirectory()).ledger();
        byte[] before = Files.isRegularFile(formalLedger) ? Files.readAllBytes(formalLedger) : null;

        Fixture fixture = writeFixtureTree(temp.resolve("formal-isolation"));
        Path runtime = temp.resolve("isolated-runtime");
        RagCandidate5DiagnosticSupport.FrozenManifests manifests =
                RagCandidate5DiagnosticSupport.freezeDatasets(
                        runtime, fixture.selection(), fixture.holdout(), fixture.baseCorpus());
        RagCandidate5DiagnosticSupport.beginSelectionRun(
                RagCandidate5DiagnosticSupport.paths(runtime), manifests);

        byte[] after = Files.isRegularFile(formalLedger) ? Files.readAllBytes(formalLedger) : null;
        assertTrue(java.util.Arrays.equals(before, after));
    }

    @Test
    void approvedFormalHoldoutRejectsCompletedStopDiagnostic() throws Exception {
        FormalFixture fixture = completeFormalRun(
                temp.resolve("formal-stop"), "STOP_MULTILINGUAL_IDENTIFIER_UNSUPPORTED", false);
        System.setProperty(RagCandidate5DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
                fixture.holdout().toString());

        assertEquals("CANDIDATE5_DIAGNOSTIC_NOT_APPROVED",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.loadApprovedFormalFrozenHoldout(
                                fixture.runtime())).getMessage());
    }

    @Test
    void approvedFormalHoldoutAcceptsCompletedProceedDiagnostic() throws Exception {
        FormalFixture fixture = completeFormalRun(
                temp.resolve("formal-proceed"), "PROCEED_TO_IDENTIFIER_RECALL_RED", true);
        System.setProperty(RagCandidate5DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
                fixture.holdout().toString());

        RagCandidate5DiagnosticSupport.FrozenDataset loaded =
                RagCandidate5DiagnosticSupport.loadApprovedFormalFrozenHoldout(
                        fixture.runtime());

        assertEquals(32, loaded.dataset().queries().size());
        assertEquals(126, loaded.dataset().corpusById().size());
        assertEquals(loaded.manifests().holdout().datasetHash(), loaded.datasetHash());
    }

    @Test
    void approvedFormalHoldoutRejectsMissingAndWrongConfiguredPath() throws Exception {
        FormalFixture fixture = completeFormalRun(
                temp.resolve("formal-path"), "PROCEED_TO_IDENTIFIER_RECALL_RED", true);

        assertEquals("CANDIDATE5_HOLDOUT_DIRECTORY_REQUIRED",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.loadApprovedFormalFrozenHoldout(
                                fixture.runtime())).getMessage());

        System.setProperty(RagCandidate5DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
                fixture.tests().resolve("wrong-holdout").toString());
        assertEquals("CANDIDATE5_HOLDOUT_DIRECTORY_MISMATCH",
                assertThrows(IllegalStateException.class,
                        () -> RagCandidate5DiagnosticSupport.loadApprovedFormalFrozenHoldout(
                                fixture.runtime())).getMessage());
    }

    @Test
    @EnabledIfSystemProperty(
            named = RagCandidate5DiagnosticSupport.FREEZE_PROPERTY, matches = "true")
    void freezeCandidate5Datasets() {
        assertTrue(configuredHoldoutDirectory != null
                && !configuredHoldoutDirectory.isBlank(), "holdout directory is required");
        verifyPostgresBoundarySemantics();
        RagCandidate5DiagnosticSupport.freezeFormalDatasets(
                RagLiveEvaluationSupport.runtimeDirectory(),
                Path.of(configuredHoldoutDirectory));
    }

    @Test
    @EnabledIfSystemProperty(named = "rag.eval.candidate5.validate-formal", matches = "true")
    void validatesFormalFixturesInTemporaryRuntime() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path tests = working.endsWith(Path.of("backend", "tests"))
                ? working : working.resolve("backend/tests");
        Path selection = tests.resolve("src/test/resources/rag-eval/candidate5-selection");
        Path holdout = tests.resolve("candidate5-holdout");
        Path base = tests.resolve(
                "src/test/resources/rag-eval/candidate5-base-distractor/corpus.jsonl");
        RagCandidate5DiagnosticSupport.FrozenManifests manifests =
                RagCandidate5DiagnosticSupport.freezeDatasets(
                        temp.resolve("formal-runtime"), selection, holdout, base);
        RagCandidate5DiagnosticSupport.FrozenDataset frozen =
                RagCandidate5DiagnosticSupport.loadFrozenSelection(
                        RagCandidate5DiagnosticSupport.paths(temp.resolve("formal-runtime")),
                        selection,
                        base);
        assertEquals(manifests.selection().datasetHash(), frozen.datasetHash());
    }

    private static void verifyPostgresBoundarySemantics() {
        try (PostgreSQLContainer<?> container = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName("candidate5")
                .withUsername("candidate5")
                .withPassword("candidate5")
                .withEnv("TZ", "UTC")
                .withEnv("LANG", "C")
                .withEnv("LC_ALL", "C")
                .withEnv("POSTGRES_INITDB_ARGS", "--encoding=UTF8 --locale=C")
                .withReuse(false)) {
            container.start();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    container.getJdbcUrl(), container.getUsername(), container.getPassword()));
            Map<String, Object> locale = jdbc.queryForMap(
                    "SELECT datcollate, datctype FROM pg_database "
                            + "WHERE datname = current_database()");
            assertEquals("C", locale.get("datcollate"));
            assertEquals("C", locale.get("datctype"));
            assertEquals("UTF8", jdbc.queryForObject("SHOW server_encoding", String.class));
            assertEquals(
                    "d.name ~ ('(^|[^[:alnum:]])' || ? || '([^[:alnum:]]|$)')",
                    RagCandidate5DiagnosticSupport.DOCUMENT_NAME_BOUNDARY_PREDICATE_SQL);
            String sql = "SELECT ? ~ ('(^|[^[:alnum:]])' || ? || '([^[:alnum:]]|$)')";
            assertTrue(boundaryMatches(jdbc, sql, "DOC-7611", "7611"));
            assertTrue(boundaryMatches(jdbc, sql, "主题7611说明", "7611"));
            assertTrue(boundaryMatches(jdbc, sql, "DOC-07621", "07621"));
            assertFalse(boundaryMatches(jdbc, sql, "DOC-07621", "7621"));
            assertFalse(boundaryMatches(jdbc, sql, "ABC7611", "7611"));
            assertFalse(boundaryMatches(jdbc, sql, "17611", "7611"));
            assertFalse(boundaryMatches(jdbc, sql, "76110", "7611"));
        }
    }

    private static boolean boundaryMatches(
            JdbcTemplate jdbc, String sql, String documentName, String identifier) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                sql, Boolean.class, documentName, identifier));
    }

    private static Map<String, Object> lockedConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rrfK", 60);
        config.put("weakPathThreshold", 0.0D);
        config.put("dynamicTopK", Map.of(
                "enabled", false,
                "defaultTopK", 10,
                "minTopK", 3,
                "maxTopK", 80,
                "complexMinTopK", 12,
                "mediumMultiplier", 1.0D,
                "complexMultiplier", 1.8D,
                "temporalMultiplier", 1.3D,
                "keywordMultiplierStep", 0.08D,
                "maxKeywordBonus", 0.5D));
        config.put("context", Map.of("maxBytes", 20000, "maxTokens", 0));
        config.put("routerEnabled", false);
        config.put("queryEntityEnabled", false);
        config.put("queryTransformEnabled", false);
        config.put("cragEnabled", false);
        config.put("cragWebEnabled", false);
        config.put("graphEnabled", false);
        config.put("rerankerMode", "deterministic");
        config.put("rerankerProviders", List.of("deterministic"));
        config.put("vecsimRescoreEnabled", false);
        config.put("identifierAware", false);
        config.put("identifierConsistencyEnabled", true);
        config.put("identifierConsistencyAlgorithm", "document-name-exact-identifier-v1");
        config.put("identifierConsistencyScorePolicy",
                "deterministic-rank-score-preserving-v1");
        config.put("identifierRecallConsistencyEnabled", false);
        config.put("candidate5EvidenceAlgorithm",
                "multilingual-identifier-recall-mechanistic-v1");
        config.put("identifierRecallConsistencyAlgorithm",
                "document-name-boundary-predicate-v1");
        config.put("identifierRecallConsistencySqlPolicy",
                "postgres-posix-c-alnum-v1");
        config.put("colbert", Map.of("enabled", true, "dimensions", 64, "maxTokens", 128));
        config.put("topK", 10);
        config.put("executor", Map.of("core", 4, "max", 4, "queue", 32));
        config.put("nativeMode", "java-fallback");
        config.put("featureHash", Map.of(
                "version", FeatureHashEmbeddingModel.VERSION,
                "dimensions", 256,
                "seed", 20260715L));
        config.put("corpusInsertionOrder", "segmentId-ascending");
        return config;
    }

    private static List<RagCandidate5DiagnosticSupport.CaseEvidence> selectionEvidence(
            boolean proceed) {
        List<RagCandidate5DiagnosticSupport.CaseEvidence> cases = new ArrayList<>();
        String[] shapes = {
                "numeric-token", "doc-prefix", "han-zero-padded",
                "numeric-token", "doc-prefix", "han-zero-padded"};
        for (int index = 0; index < shapes.length; index++) {
            String family = "c5s-t" + String.format("%02d", index + 1);
            cases.add(selectionCase(
                    family + "-en", family, "en", shapes[index], true,
                    true, true, !proceed, !proceed, !proceed, !proceed));
            cases.add(selectionCase(
                    family + "-zh", family, "zh", shapes[index], true,
                    true, true, true, true, true, !proceed));
        }
        for (int index = 1; index <= 2; index++) {
            String family = "c5s-n" + String.format("%02d", index);
            for (String language : List.of("zh", "en")) {
                cases.add(selectionCase(
                        family + "-" + language, family, language, "none", false,
                        false, false, false, false, false, false));
            }
        }
        for (int index = 1; index <= 2; index++) {
            String family = "c5s-u" + String.format("%02d", index);
            for (String language : List.of("zh", "en")) {
                cases.add(selectionCase(
                        family + "-" + language, family, language, "numeric-token", false,
                        true, true, true, true, false, false));
            }
        }
        return List.copyOf(cases);
    }

    private static RagCandidate5DiagnosticSupport.CaseEvidence selectionCase(
            String queryId,
            String familyId,
            String language,
            String identifierShape,
            boolean target,
            boolean retrievalContainsIdentifier,
            boolean extractorContainsIdentifier,
            boolean identifierTermEmitted,
            boolean exactIdentifierPredicate,
            boolean keywordReturnedAnchor,
            boolean finalReturnedAnchor) {
        List<RagCandidate5DiagnosticSupport.RankedSegment> actual =
                finalReturnedAnchor ? ranked("anchor") : ranked("other");
        RagCandidate5DiagnosticSupport.CaseInput input =
                new RagCandidate5DiagnosticSupport.CaseInput(
                        queryId,
                        familyId,
                        language,
                        "selection",
                        identifierShape,
                        target,
                        retrievalContainsIdentifier,
                        retrievalContainsIdentifier,
                        List.of(retrievalContainsIdentifier),
                        extractorContainsIdentifier,
                        identifierTermEmitted,
                        exactIdentifierPredicate,
                        true,
                        keywordReturnedAnchor,
                        finalReturnedAnchor,
                        Map.of(
                                "keyword", keywordReturnedAnchor
                                        ? ranked("anchor") : ranked("other"),
                                "metadata", List.of(),
                                "vector", List.of(),
                                "graph", List.of()),
                        actual,
                        actual,
                        actual,
                        target ? Map.of("anchor", 3) : Map.of(),
                        target ? ranked("anchor") : actual,
                        "a".repeat(64),
                        false,
                        true);
        return RagCandidate5DiagnosticSupport.classify(input);
    }

    private static List<Map<String, Object>> caseMaps(
            List<RagCandidate5DiagnosticSupport.CaseEvidence> cases) {
        return cases.stream().map(item -> {
            RagCandidate5DiagnosticSupport.CaseInput input = item.input();
            Map<String, Object> value = new LinkedHashMap<>(item.toMap(
                    "a".repeat(64),
                    "b".repeat(64),
                    input.extractorContainsIdentifier() ? 1 : 0,
                    "c".repeat(64),
                    2,
                    "d".repeat(64),
                    "e".repeat(64),
                    input.exactIdentifierPredicate() ? 1 : 0,
                    Map.of("dbCalls", 1L, "embeddingCalls", 1L, "networkCalls", 0L)));
            value.put("target", input.target());
            value.put("extractorContainsIdentifier", input.extractorContainsIdentifier());
            value.put("documentFieldsConsistent", input.documentFieldsConsistent());
            value.put("keywordReturnedAnchor", input.keywordReturnedAnchor());
            value.put("finalReturnedAnchor", input.finalReturnedAnchor());
            value.put("behaviorUnchanged", input.behaviorUnchanged());
            return Map.copyOf(value);
        }).toList();
    }

    private static Map<String, Object> diagnosticSummary(
            List<RagCandidate5DiagnosticSupport.CaseEvidence> cases) {
        Map<String, Integer> classificationCounts = new LinkedHashMap<>();
        for (RagCandidate5DiagnosticSupport.RootCause value
                : RagCandidate5DiagnosticSupport.RootCause.values()) {
            classificationCounts.put(value.name(), 0);
        }
        cases.forEach(item -> classificationCounts.compute(
                item.classification().name(), (ignored, count) -> count + 1));
        Map<String, List<RagCandidate5DiagnosticSupport.CaseEvidence>> families =
                cases.stream().filter(RagCandidate5DiagnosticSupport.CaseEvidence::target)
                        .collect(java.util.stream.Collectors.groupingBy(
                                item -> item.input().familyId(),
                                LinkedHashMap::new,
                                java.util.stream.Collectors.toList()));
        Set<String> shapes = new java.util.TreeSet<>();
        int qualifying = 0;
        for (List<RagCandidate5DiagnosticSupport.CaseEvidence> family : families.values()) {
            RagCandidate5DiagnosticSupport.CaseEvidence english = family.stream()
                    .filter(item -> "en".equals(item.input().language())).findFirst().orElseThrow();
            RagCandidate5DiagnosticSupport.CaseEvidence chinese = family.stream()
                    .filter(item -> "zh".equals(item.input().language())).findFirst().orElseThrow();
            if (english.classification()
                    == RagCandidate5DiagnosticSupport.RootCause.KEYWORD_TERM_NOT_EMITTED
                    && chinese.input().keywordReturnedAnchor()) {
                qualifying++;
                shapes.add(english.input().identifierShape());
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseCount", cases.size());
        summary.put("targetCaseCount", (int) cases.stream()
                .filter(RagCandidate5DiagnosticSupport.CaseEvidence::target).count());
        summary.put("controlCaseCount", (int) cases.stream()
                .filter(item -> !item.target()).count());
        summary.put("classificationCounts", classificationCounts);
        summary.put("qualifyingFamilyCount", qualifying);
        summary.put("coveredIdentifierShapes", List.copyOf(shapes));
        return summary;
    }

    private static RagCandidate5DiagnosticSupport.CaseEvidence evidence(
            String queryId,
            String familyId,
            String language,
            String identifierShape,
            boolean target,
            boolean retrievalContainsIdentifier,
            boolean extractorContainsIdentifier,
            boolean identifierTermEmitted,
            boolean exactIdentifierPredicate,
            boolean keywordReturnedAnchor,
            boolean finalReturnedAnchor) {
        List<RagCandidate5DiagnosticSupport.RankedSegment> actual =
                finalReturnedAnchor ? ranked("anchor") : ranked("other");
        List<RagCandidate5DiagnosticSupport.RankedSegment> oracle = target
                ? List.of(new RagCandidate5DiagnosticSupport.RankedSegment(
                "anchor", 1, 1.0D)) : actual;
        RagCandidate5DiagnosticSupport.CaseInput input =
                new RagCandidate5DiagnosticSupport.CaseInput(
                        queryId,
                        familyId,
                        language,
                        "selection",
                        identifierShape,
                        target,
                        true,
                        retrievalContainsIdentifier,
                        List.of(retrievalContainsIdentifier),
                        extractorContainsIdentifier,
                        identifierTermEmitted,
                        exactIdentifierPredicate,
                        true,
                        keywordReturnedAnchor,
                        finalReturnedAnchor,
                        Map.of("keyword", keywordReturnedAnchor ? ranked("anchor") : ranked("other")),
                        actual,
                        actual,
                        actual,
                        target ? Map.of("anchor", 3) : Map.of(),
                        oracle,
                        "a".repeat(64),
                        false,
                        true);
        return RagCandidate5DiagnosticSupport.classify(input);
    }

    private static List<RagCandidate5DiagnosticSupport.RankedSegment> ranked(String id) {
        return List.of(new RagCandidate5DiagnosticSupport.RankedSegment(id, 1, 1.0D));
    }

    private static Fixture writeFixtureTree(Path root) throws Exception {
        Path selection = root.resolve("selection");
        Path holdout = root.resolve("holdout");
        Path base = root.resolve("base/corpus.jsonl");
        writeCandidate5Fixture(selection, false);
        writeCandidate5Fixture(holdout, true);
        Files.createDirectories(base.getParent());
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            lines.add(corpusLine(
                    String.valueOf(100_001L + index),
                    String.valueOf(200_001L + index),
                    "base distractor " + index,
                    Map.of("candidate5Role", "base-distractor",
                            "documentName", "Base " + index)));
        }
        Files.writeString(base, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return new Fixture(selection, holdout, base);
    }

    private FormalFixture completeFormalRun(
            Path root, String decision, boolean proceed) throws Exception {
        Path tests = root.resolve("backend/tests").toAbsolutePath().normalize();
        Path selection = tests.resolve("src/test/resources/rag-eval/candidate5-selection");
        Path holdout = tests.resolve("candidate5-holdout");
        Path base = tests.resolve(
                "src/test/resources/rag-eval/candidate5-base-distractor/corpus.jsonl");
        writeCandidate5Fixture(selection, false);
        writeCandidate5Fixture(holdout, true);
        Files.createDirectories(base.getParent());
        List<String> baseLines = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            baseLines.add(corpusLine(
                    String.valueOf(100_001L + index),
                    String.valueOf(200_001L + index),
                    "base distractor " + index,
                    Map.of("candidate5Role", "base-distractor",
                            "documentName", "Base " + index)));
        }
        Files.writeString(base, String.join("\n", baseLines) + "\n",
                StandardCharsets.UTF_8);
        System.setProperty("user.dir", tests.toString());

        Path runtime = root.resolve("runtime");
        RagCandidate5DiagnosticSupport.FrozenManifests manifests =
                RagCandidate5DiagnosticSupport.freezeDatasets(
                        runtime, selection, holdout, base);
        RagCandidate5DiagnosticSupport.RuntimePaths paths =
                RagCandidate5DiagnosticSupport.paths(runtime);
        RagCandidate5DiagnosticSupport.FrozenDataset frozenSelection =
                RagCandidate5DiagnosticSupport.loadFrozenSelection(
                        paths, selection, base);
        List<RagCandidate5DiagnosticSupport.CaseEvidence> evidence =
                selectionEvidence(proceed);
        Map<String, Object> artifact = RagCandidate5DiagnosticSupport.freshValidArtifact(
                frozenSelection,
                lockedConfig(),
                decision,
                diagnosticSummary(evidence),
                caseMaps(evidence));
        RagCandidate5DiagnosticSupport.RunHandle handle =
                RagCandidate5DiagnosticSupport.beginSelectionRun(paths, manifests);
        RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(paths, handle, artifact);
        return new FormalFixture(tests, runtime, holdout);
    }

    private static void writeCandidate5Fixture(Path directory, boolean holdout) throws Exception {
        Files.createDirectories(directory);
        String prefix = holdout ? "c5h" : "c5s";
        String split = holdout ? "holdout" : "selection";
        int targetFamilies = holdout ? 12 : 6;
        int lexicalDistractors = holdout ? 24 : 12;
        long segment = (holdout ? 9_970_001L : 9_960_001L) + 64L;
        long document = holdout ? 9_975_064L : 9_965_064L;
        List<String> corpus = new ArrayList<>();
        List<String> queries = new ArrayList<>();
        List<String> qrels = new ArrayList<>(List.of("queryId\tsegmentId\tgrade"));

        for (int family = 1; family <= targetFamilies; family++) {
            String familyId = prefix + "-t" + String.format("%02d", family);
            String identifier = family % 3 == 0
                    ? "0" + (7600 + family)
                    : String.valueOf(7600 + family);
            String shape = switch ((family - 1) % 3) {
                case 0 -> "numeric-token";
                case 1 -> "doc-prefix";
                default -> "han-zero-padded";
            };
            long first = segment;
            for (int evidence = 0; evidence < 3; evidence++) {
                String documentName = evidence == 0
                        ? switch (shape) {
                            case "doc-prefix" -> "DOC-" + identifier;
                            case "han-zero-padded" -> "主题" + identifier + "说明";
                            default -> identifier;
                        }
                        : prefix + "-semantic-" + family + "-" + evidence;
                corpus.add(corpusLine(
                        String.valueOf(segment++),
                        String.valueOf(document++),
                        evidence == 0
                                ? "sealed archive entry " + family
                                : "target evidence " + family + " " + evidence,
                        Map.of(
                                "candidate5Role", "target-evidence",
                                "familyId", familyId,
                                "identifierShape", shape,
                                "documentName", documentName)));
            }
            for (String language : List.of("zh", "en")) {
                String queryId = familyId + "-" + language;
                String query = language.equals("zh")
                        ? "主题 " + identifier + " 的证据"
                        : "topic " + identifier + " evidence";
                queries.add(queryLine(queryId, familyId, query, language, split, true,
                        "candidate5-target", shape));
                qrels.add(queryId + "\t" + first + "\t3");
                qrels.add(queryId + "\t" + (first + 1) + "\t2");
                qrels.add(queryId + "\t" + (first + 2) + "\t1");
            }
        }

        for (int family = 1; family <= 2; family++) {
            String familyId = prefix + "-n" + String.format("%02d", family);
            long evidence = segment++;
            corpus.add(corpusLine(
                    String.valueOf(evidence),
                    String.valueOf(document++),
                    "no identifier evidence " + family,
                    Map.of("candidate5Role", "no-identifier-evidence",
                            "familyId", familyId,
                            "identifierShape", "none",
                            "documentName", "Compass " + family)));
            for (String language : List.of("zh", "en")) {
                String queryId = familyId + "-" + language;
                String query = language.equals("zh") ? "罗盘协议证据" : "compass protocol evidence";
                queries.add(queryLine(queryId, familyId, query, language, split, true,
                        "candidate5-no-identifier-control", "none"));
                qrels.add(queryId + "\t" + evidence + "\t3");
            }
        }

        for (int family = 1; family <= 2; family++) {
            String familyId = prefix + "-u" + String.format("%02d", family);
            String identifier = String.valueOf(8900 + family);
            for (String language : List.of("zh", "en")) {
                String queryId = familyId + "-" + language;
                String query = language.equals("zh")
                        ? "主题 " + identifier + " 的缺失证据"
                        : "topic " + identifier + " missing evidence";
                queries.add(queryLine(queryId, familyId, query, language, split, false,
                        "candidate5-identifier-unanswerable-control", "numeric-token"));
            }
        }

        for (int index = 0; index < lexicalDistractors; index++) {
            corpus.add(corpusLine(
                    String.valueOf(segment++),
                    String.valueOf(document++),
                    "lexical distractor " + index,
                    Map.of("candidate5Role", "lexical-distractor",
                            "familyId", "none",
                            "identifierShape", "none",
                            "documentName", "Distractor " + index)));
        }

        Files.writeString(directory.resolve("corpus.jsonl"),
                String.join("\n", corpus) + "\n", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("queries.jsonl"),
                String.join("\n", queries) + "\n", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("qrels.tsv"),
                String.join("\n", qrels) + "\n", StandardCharsets.UTF_8);
    }

    private static String corpusLine(
            String segmentId, String documentId, String content, Map<String, Object> metadata) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("segmentId", segmentId);
        value.put("documentId", documentId);
        value.put("content", content);
        value.put("parentSegmentId", null);
        value.put("metadata", metadata);
        return JSON.toJSONString(value);
    }

    private static String queryLine(
            String id,
            String familyId,
            String query,
            String language,
            String split,
            boolean answerable,
            String stratum,
            String shape) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("familyId", familyId);
        value.put("query", query);
        value.put("retrievalQuery", query);
        value.put("history", List.of());
        value.put("language", language);
        value.put("strata", List.of("candidate5", stratum, shape));
        value.put("split", split);
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

    private record Fixture(Path selection, Path holdout, Path baseCorpus) {
    }

    private record FormalFixture(Path tests, Path runtime, Path holdout) {
    }
}
