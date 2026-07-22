package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.io.IOException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCandidate6DiagnosticSupportTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.1-pg16@sha256:33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337")
            .asCompatibleSubstituteFor("postgres");

    @TempDir
    Path temp;

    @Test
    void fixesRuntimePathsAndUsesExplicitSixWayDiagnosticAddition() {
        RagCandidate6DiagnosticSupport.RuntimePaths paths =
                RagCandidate6DiagnosticSupport.paths(temp);

        assertEquals(temp.resolve("candidate6-freeze").toAbsolutePath().normalize(),
                paths.freezeDirectory());
        assertEquals(paths.freezeDirectory().resolve("selection-manifest.json"),
                paths.selectionManifest());
        assertEquals(paths.freezeDirectory().resolve("holdout-manifest.json"),
                paths.holdoutManifest());
        assertEquals(paths.freezeDirectory().resolve("selection-ledger.json"),
                paths.ledger());
        assertEquals(paths.freezeDirectory().resolve("review-ledger.json"),
                paths.reviewLedger());
        assertEquals(temp.resolve("candidate6-calibration-diagnostic.json")
                        .toAbsolutePath().normalize(),
                paths.diagnostic());
        assertEquals(2, RagCandidate6DiagnosticSupport.enabledDiagnosticCount(
                false, false, false, false, true, true));
        assertEquals(1, RagCandidate6DiagnosticSupport.enabledDiagnosticCount(
                false, false, false, false, false, true));
    }

    @Test
    void freezesExactExpandedShapeAndLoadsSelectionWithoutHoldoutResources()
            throws Exception {
        Fixture fixture = copyFixture();
        RagCandidate6DiagnosticSupport.FrozenManifests frozen =
                RagCandidate6DiagnosticSupport.freezeDatasets(
                        temp.resolve("runtime"), fixture.selection(), fixture.holdout());

        assertEquals("FROZEN", frozen.selection().freezeStatus());
        assertEquals("FROZEN_NOT_BLIND", frozen.holdout().freezeStatus());
        assertEquals(28, frozen.selection().counts().get("queryCount"));
        assertEquals(604, frozen.selection().counts().get("segmentCount"));
        assertEquals(604, frozen.selection().counts().get("documentCount"));
        assertEquals(52, frozen.selection().counts().get("qrelCount"));
        assertEquals(56, frozen.holdout().counts().get("queryCount"));
        assertEquals(952, frozen.holdout().counts().get("segmentCount"));
        assertEquals(104, frozen.holdout().counts().get("qrelCount"));
        assertEquals(512, frozen.selection().structure().get("pressureDistractorCount"));
        assertEquals(768, frozen.holdout().structure().get("pressureDistractorCount"));
        assertEquals(4, ((Map<?, ?>) frozen.selection().structure()
                .get("identifierShapeFamilyCounts")).size());
        assertEquals(64, ((String) frozen.selection().structure()
                .get("expandedDatasetHash")).length());

        byte[] selectionManifest = Files.readAllBytes(
                RagCandidate6DiagnosticSupport.paths(temp.resolve("runtime"))
                        .selectionManifest());
        byte[] holdoutManifest = Files.readAllBytes(
                RagCandidate6DiagnosticSupport.paths(temp.resolve("runtime"))
                        .holdoutManifest());
        RagCandidate6DiagnosticSupport.freezeDatasets(
                temp.resolve("runtime"), fixture.selection(), fixture.holdout());
        assertTrue(java.util.Arrays.equals(selectionManifest, Files.readAllBytes(
                RagCandidate6DiagnosticSupport.paths(temp.resolve("runtime"))
                        .selectionManifest())));
        assertTrue(java.util.Arrays.equals(holdoutManifest, Files.readAllBytes(
                RagCandidate6DiagnosticSupport.paths(temp.resolve("runtime"))
                        .holdoutManifest())));
        String holdoutProperty = System.getProperty(
                RagCandidate6DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY);
        RagCandidate6DiagnosticSupport.FrozenDataset loaded;
        try {
            System.clearProperty(
                    RagCandidate6DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY);
            loaded = RagCandidate6DiagnosticSupport.loadFormalFrozenSelection(
                    temp.resolve("runtime"));
        } finally {
            restoreProperty(
                    RagCandidate6DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
                    holdoutProperty);
        }
        assertEquals(604, loaded.dataset().corpusById().size());
        assertEquals(frozen.selection().datasetHash(), loaded.datasetHash());
    }

    @Test
    void rejectsResourceMutationAfterFreezeAndNeverCreatesLedger() throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("runtime");
        RagCandidate6DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        Files.writeString(fixture.selection().resolve("pressure.json"), "\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RagCandidate6DiagnosticSupport.freezeDatasets(
                        runtime, fixture.selection(), fixture.holdout()));

        assertEquals("CANDIDATE6_FREEZE_ALREADY_FROZEN", failure.getMessage());
        assertFalse(Files.exists(RagCandidate6DiagnosticSupport.paths(runtime).ledger()));
    }

    @Test
    void rejectsNoExactControlWithExactCorpusMatchBeforeFreeze() throws Exception {
        Fixture fixture = copyFixture();
        Path corpus = fixture.selection().resolve("corpus.jsonl");
        String original = Files.readString(corpus, StandardCharsets.UTF_8);
        String mutated = original.replace(
                "\"documentName\":\"selection-neutral-handoff\"",
                "\"documentName\":\"Topic 8891 selection-neutral-handoff\"");
        assertNotEquals(original, mutated);
        Files.writeString(corpus, mutated, StandardCharsets.UTF_8);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> RagCandidate6DiagnosticSupport.freezeDatasets(
                        temp.resolve("runtime"), fixture.selection(), fixture.holdout()));

        assertEquals("CANDIDATE6_NO_EXACT_CONTROL_INVALID", failure.getMessage());
        assertFalse(Files.exists(RagCandidate6DiagnosticSupport.paths(
                temp.resolve("runtime")).selectionManifest()));
    }

    @Test
    void preRunValidationFailureWritesFreshInvalidWithoutLedger() throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("runtime");
        RagCandidate6DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        RagCandidate6DiagnosticSupport.RuntimePaths paths =
                RagCandidate6DiagnosticSupport.paths(runtime);

        RagCandidate6DiagnosticSupport.writePreRunInvalidDiagnostic(
                paths, config(), "CANDIDATE6_FROZEN_INPUT_INVALID");

        JSONObject diagnostic = JSON.parseObject(Files.readString(paths.diagnostic()));
        assertEquals("INVALID", diagnostic.getString("status"));
        assertEquals("CANDIDATE6_FROZEN_INPUT_INVALID",
                diagnostic.getString("errorCode"));
        assertEquals(64, diagnostic.getString("selectionManifestHash").length());
        assertEquals(64, diagnostic.getString("holdoutManifestHash").length());
        assertFalse(diagnostic.containsKey("cases"));
        assertFalse(diagnostic.containsKey("summary"));
        assertFalse(Files.exists(paths.ledger()));
        assertThrows(IllegalStateException.class,
                () -> RagCandidate6DiagnosticSupport.writePreRunInvalidDiagnostic(
                        paths, config(), "CANDIDATE6_FROZEN_INPUT_INVALID"));
    }

    @Test
    void selectionJobRejectsHoldoutProperty() {
        String previous = System.getProperty(
                RagCandidate6DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY);
        try {
            System.setProperty(RagCandidate6DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
                    temp.resolve("holdout").toString());
            assertThrows(IllegalStateException.class,
                    RagCandidate6DiagnosticSupport::requireSelectionJobProperties);
        } finally {
            restoreProperty(
                    RagCandidate6DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY, previous);
        }
    }

    @Test
    void ledgerIsFailClosedAcrossRunningCompletedAndArtifactHashMismatch()
            throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("runtime");
        RagCandidate6DiagnosticSupport.FrozenManifests manifests =
                RagCandidate6DiagnosticSupport.freezeDatasets(
                        runtime, fixture.selection(), fixture.holdout());
        RagCandidate6DiagnosticSupport.RuntimePaths paths =
                RagCandidate6DiagnosticSupport.paths(runtime);
        RagCandidate6DiagnosticSupport.RunHandle handle =
                RagCandidate6DiagnosticSupport.beginSelectionRun(paths, manifests);

        assertThrows(IllegalStateException.class,
                () -> RagCandidate6DiagnosticSupport.requireSelectionRunAvailable(paths));
        Map<String, Object> artifact = validStopArtifact(
                frozenDataset(fixture, manifests), selectionCases());
        RagCandidate6DiagnosticSupport.writeDiagnosticAndComplete(
                paths, handle, artifact);

        JSONObject ledger = JSON.parseObject(Files.readString(paths.ledger()));
        assertEquals("COMPLETED", ledger.getString("status"));
        assertEquals(ShadowContractSupport.sha256(paths.diagnostic()),
                ledger.getString("artifactSha256"));
        assertThrows(IllegalStateException.class,
                () -> RagCandidate6DiagnosticSupport.requireSelectionRunAvailable(paths));
        assertThrows(IllegalStateException.class,
                () -> RagCandidate6DiagnosticSupport.beginSelectionRun(paths, manifests));

        Files.writeString(paths.diagnostic(), "{}", StandardCharsets.UTF_8);
        assertNotEquals(ledger.getString("artifactSha256"),
                ShadowContractSupport.sha256(paths.diagnostic()));
    }

    @Test
    void invalidArtifactIsFreshWhileValidStopRetainsCurrentSanitizedCases()
            throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("runtime");
        RagCandidate6DiagnosticSupport.FrozenManifests manifests =
                RagCandidate6DiagnosticSupport.freezeDatasets(
                        runtime, fixture.selection(), fixture.holdout());
        RagCandidate6DiagnosticSupport.FrozenDataset frozen =
                frozenDataset(fixture, manifests);

        Map<String, Object> invalid =
                RagCandidate6DiagnosticSupport.freshInvalidArtifact(
                        frozen, config(), "CANDIDATE6_DATASET_INVALID");
        Map<String, Object> stop = validStopArtifact(frozen, selectionCases());

        assertEquals("INVALID", invalid.get("status"));
        assertFalse(invalid.containsKey("cases"));
        assertFalse(invalid.containsKey("summary"));
        assertEquals("VALID", stop.get("status"));
        assertEquals(RagCandidate6DiagnosticSupport.STOP_DECISION,
                stop.get("decision"));
        assertEquals(28, ((List<?>) stop.get("cases")).size());
        assertTrue(stop.containsKey("summary"));
    }

    @Test
    void immutableSnapshotsDoNotShareBusinessObjectsAndPreserveStableTies() {
        RetrievalResult nonExact = result(2L, "general archive", 0.8D);
        RetrievalResult exact = result(1L, "DOC-7611 archive", 0.8D);
        List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> snapshots =
                RagCandidate6DiagnosticSupport.snapshotResults(
                        List.of(nonExact, exact), List.of("7611"));
        nonExact.setScore(42.0D);
        exact.setDocumentName("mutated");

        assertEquals(0.8D, snapshots.get(0).score());
        assertEquals("DOC-7611 archive", snapshots.get(1).documentName());
        List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> exactFirst =
                RagCandidate6DiagnosticSupport.stableExactFirst(snapshots);
        assertEquals(List.of(1L, 2L), exactFirst.stream()
                .map(RagCandidate6DiagnosticSupport.RetrievalSnapshot::segmentId)
                .toList());
        List<RetrievalResult> rebuilt =
                RagCandidate6DiagnosticSupport.rebuildResults(exactFirst);
        rebuilt.get(0).setScore(9.0D);
        assertEquals(0.8D, exactFirst.get(0).score());
    }

    @Test
    void javaPriorityAndVariantMergeMatchBusinessStabilityContracts() {
        List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> rows = List.of(
                snapshot(1, 1L, 0.9D, false),
                snapshot(2, 2L, 0.7D, true),
                snapshot(3, 3L, 0.7D, true));

        assertEquals(List.of(1L, 2L, 3L),
                RagCandidate6DiagnosticSupport.originalJavaTop50(rows).stream()
                        .map(RagCandidate6DiagnosticSupport.RetrievalSnapshot::segmentId)
                        .toList());
        assertEquals(List.of(2L, 3L, 1L),
                RagCandidate6DiagnosticSupport.exactFirstJavaTop50(rows).stream()
                        .map(RagCandidate6DiagnosticSupport.RetrievalSnapshot::segmentId)
                        .toList());
        List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> merged =
                RagCandidate6DiagnosticSupport.mergeVariants(List.of(
                        List.of(snapshot(0, 9L, 0.2D, false)),
                        List.of(snapshot(0, 9L, 0.8D, false),
                                snapshot(1, 8L, 0.5D, false))));
        assertEquals(List.of(9L, 8L), merged.stream()
                .map(RagCandidate6DiagnosticSupport.RetrievalSnapshot::segmentId)
                .toList());
        assertEquals(0.8D, merged.get(0).score());
    }

    @Test
    void classifiesMultiVariantAtEarliestCommonRecoverableStage() {
        Set<Long> exact = Set.of(11L);
        RagCandidate6DiagnosticSupport.VariantStage sqlSuppressed = variant(
                List.of(11L, 12L), List.of(12L), List.of(12L));
        RagCandidate6DiagnosticSupport.VariantStage javaSuppressed = variant(
                List.of(11L), List.of(11L), List.of());
        RagCandidate6DiagnosticSupport.VariantStage downstream = variant(
                List.of(11L), List.of(11L), List.of(11L));

        assertEquals(RagCandidate6DiagnosticSupport.RootCause.SQL_PRELIMIT_RANK_SUPPRESSION,
                RagCandidate6DiagnosticSupport.classifyStages(
                        exact, List.of(sqlSuppressed), List.of(), List.of()));
        assertEquals(RagCandidate6DiagnosticSupport.RootCause.JAVA_KEYWORD_RANK_SUPPRESSION,
                RagCandidate6DiagnosticSupport.classifyStages(
                        exact, List.of(javaSuppressed), List.of(), List.of()));
        assertEquals(RagCandidate6DiagnosticSupport.RootCause.KEYWORD_TOPK_DOWNSTREAM_SUPPRESSION,
                RagCandidate6DiagnosticSupport.classifyStages(
                        exact, List.of(downstream), List.of(11L), List.of()));
        assertEquals(RagCandidate6DiagnosticSupport.RootCause.BASELINE_ALREADY_PRESENT,
                RagCandidate6DiagnosticSupport.classifyStages(
                        exact, List.of(downstream), List.of(11L), List.of(11L)));
        assertEquals(RagCandidate6DiagnosticSupport.RootCause.JAVA_KEYWORD_RANK_SUPPRESSION,
                RagCandidate6DiagnosticSupport.classifyStages(
                        exact, List.of(sqlSuppressed, javaSuppressed), List.of(), List.of()));
    }

    @Test
    void familyDecisionRequiresOneRootFourShapesImprovementsAndSafeControls() {
        List<RagCandidate6DiagnosticSupport.CaseEvidence> proceed = selectionCases();
        assertEquals(RagCandidate6DiagnosticSupport.PROCEED_DECISION,
                RagCandidate6DiagnosticSupport.decide(proceed));

        List<RagCandidate6DiagnosticSupport.CaseEvidence> unsafe =
                new ArrayList<>(proceed);
        int safetyIndex = 24;
        RagCandidate6DiagnosticSupport.CaseEvidence original = unsafe.get(safetyIndex);
        unsafe.set(safetyIndex, copyWithSafety(original, false));
        assertEquals(RagCandidate6DiagnosticSupport.STOP_DECISION,
                RagCandidate6DiagnosticSupport.decide(unsafe));

        List<RagCandidate6DiagnosticSupport.CaseEvidence> mixed =
                new ArrayList<>(proceed);
        RagCandidate6DiagnosticSupport.CaseEvidence english = mixed.get(1);
        mixed.set(1, copyWithRoot(english,
                RagCandidate6DiagnosticSupport.RootCause.JAVA_KEYWORD_RANK_SUPPRESSION));
        assertEquals(RagCandidate6DiagnosticSupport.STOP_DECISION,
                RagCandidate6DiagnosticSupport.decide(mixed));
    }

    @Test
    @EnabledIfSystemProperty(named = RagCandidate6DiagnosticSupport.FREEZE_PROPERTY,
            matches = "true")
    void freezesFormalDatasetsOnlyWhenExplicitlyEnabled() {
        String holdout = System.getProperty(
                RagCandidate6DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY);
        if (holdout == null || holdout.isBlank()) {
            throw new IllegalStateException("CANDIDATE6_HOLDOUT_DIRECTORY_REQUIRED");
        }
        verifyPostgresBoundarySemantics();
        RagCandidate6DiagnosticSupport.freezeFormalDatasets(
                Path.of("target/rag-eval"), Path.of(holdout));
    }

    private static void verifyPostgresBoundarySemantics() {
        try (PostgreSQLContainer<?> container = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName("candidate6")
                .withUsername("candidate6")
                .withPassword("candidate6")
                .withEnv("TZ", "UTC")
                .withEnv("LANG", "C")
                .withEnv("LC_ALL", "C")
                .withEnv("POSTGRES_INITDB_ARGS", "--encoding=UTF8 --locale=C")
                .withReuse(false)) {
            container.start();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    container.getJdbcUrl(), container.getUsername(),
                    container.getPassword()));
            Map<String, Object> locale = jdbc.queryForMap(
                    "SELECT datcollate, datctype FROM pg_database "
                            + "WHERE datname = current_database()");
            assertEquals("C", locale.get("datcollate"));
            assertEquals("C", locale.get("datctype"));
            assertEquals("UTF8", jdbc.queryForObject(
                    "SHOW server_encoding", String.class));
            String sql = "SELECT ? ~ ('(^|[^[:alnum:]])' || ? "
                    + "|| '([^[:alnum:]]|$)')";
            for (String value : List.of(
                    "DOC-7611", "主题7611说明", "Topic 7611 archive")) {
                assertEquals(
                        RagCandidate6DiagnosticSupport.boundaryMatches(value, "7611"),
                        Boolean.TRUE.equals(jdbc.queryForObject(
                                sql, Boolean.class, value, "7611")));
            }
            for (String value : List.of("ABC7611", "17611", "76110")) {
                assertEquals(
                        RagCandidate6DiagnosticSupport.boundaryMatches(value, "7611"),
                        Boolean.TRUE.equals(jdbc.queryForObject(
                                sql, Boolean.class, value, "7611")));
            }
            assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
                    sql, Boolean.class, "DOC-07621", "07621")));
            assertFalse(Boolean.TRUE.equals(jdbc.queryForObject(
                    sql, Boolean.class, "DOC-07621", "7621")));

            String businessSql = """
                    SELECT *
                    FROM (VALUES
                        (1::bigint, ?::text, 0.9::double precision,
                            1::bigint, 1::int),
                        (2::bigint, ?::text, 0.1::double precision,
                            2::bigint, 2::int)
                    ) AS deduped(id, document_name, trgm_score, document_id, position)
                    ORDER BY trgm_score DESC, document_id ASC, position ASC NULLS LAST
                    LIMIT ?
                    """;
            RagCandidate6DiagnosticSupport.ExactFirstSql priority =
                    RagCandidate6DiagnosticSupport.exactFirstSql(
                            businessSql,
                            new Object[]{
                                    "general archive",
                                    "DOC-7611 archive",
                                    RagCandidate6DiagnosticSupport.BUSINESS_SQL_LIMIT},
                            List.of("7611"));
            assertFalse(priority.sql().contains("7611"));
            assertEquals(List.of(
                    "general archive", "DOC-7611 archive", "7611", 500),
                    priority.parameters());
            assertEquals(List.of(2L, 1L), jdbc.query(
                    priority.sql(),
                    (rs, rowNum) -> rs.getLong("id"),
                    priority.parameters().toArray()));
        }
    }

    private Fixture copyFixture() throws IOException {
        Path tests = testsDirectory();
        Path selection = temp.resolve("selection");
        Path holdout = temp.resolve("holdout");
        copyDataset(tests.resolve(
                "src/test/resources/rag-eval/candidate6-selection"), selection);
        copyDataset(tests.resolve("candidate6-holdout"), holdout);
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

    private RagCandidate6DiagnosticSupport.FrozenDataset frozenDataset(
            Fixture fixture,
            RagCandidate6DiagnosticSupport.FrozenManifests manifests) {
        return new RagCandidate6DiagnosticSupport.FrozenDataset(
                datasetFromFormalSelection(),
                manifests,
                manifests.selection().datasetHash(),
                List.of(
                        "selection-manifest.json", "holdout-manifest.json",
                        "candidate6-selection/corpus.jsonl",
                        "candidate6-selection/queries.jsonl",
                        "candidate6-selection/qrels.tsv",
                        "candidate6-selection/pressure.json"));
    }

    private static RagEvaluationDataset datasetFromFormalSelection() {
        RagEvaluationDataset defaultDataset = RagEvaluationDatasetLoader.loadDefault();
        return new RagEvaluationDataset(
                defaultDataset.corpusById(),
                defaultDataset.queries(),
                defaultDataset.qrels());
    }

    private static Map<String, Object> validStopArtifact(
            RagCandidate6DiagnosticSupport.FrozenDataset frozen,
            List<RagCandidate6DiagnosticSupport.CaseEvidence> evidence) {
        List<Map<String, Object>> cases = evidence.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("queryId", item.queryId());
            value.put("familyId", item.familyId());
            value.put("language", item.language());
            value.put("classification", item.classification().name());
            return value;
        }).toList();
        return RagCandidate6DiagnosticSupport.freshValidArtifact(
                frozen,
                config(),
                RagCandidate6DiagnosticSupport.STOP_DECISION,
                RagCandidate6DiagnosticSupport.diagnosticSummary(evidence),
                cases);
    }

    private static Map<String, Object> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("identifierAware", false);
        config.put("identifierConsistencyEnabled", true);
        config.put("identifierEvidencePriorityEnabled", false);
        config.put("identifierEvidencePriorityAlgorithm",
                "document-name-exact-priority-diagnostic-v1");
        config.put("topK", 10);
        config.put("keywordCandidateTopK", 50);
        config.put("keywordSqlLimit", 500);
        return config;
    }

    private static RetrievalResult result(
            long segmentId, String documentName, double score) {
        return RetrievalResult.builder()
                .segmentId(segmentId)
                .documentId(segmentId + 100L)
                .qmSegmentId(String.valueOf(segmentId))
                .documentName(documentName)
                .content("immutable content")
                .score(score)
                .source("keyword")
                .build();
    }

    private static RagCandidate6DiagnosticSupport.RetrievalSnapshot snapshot(
            int ordinal, long segmentId, double score, boolean exact) {
        return new RagCandidate6DiagnosticSupport.RetrievalSnapshot(
                ordinal, segmentId, segmentId + 100L, String.valueOf(segmentId),
                null, "name-" + segmentId, "content-" + segmentId,
                null, score, "keyword", exact);
    }

    private static RagCandidate6DiagnosticSupport.VariantStage variant(
            List<Long> full, List<Long> business, List<Long> javaTop50) {
        return new RagCandidate6DiagnosticSupport.VariantStage(
                full, business, javaTop50);
    }

    private static List<RagCandidate6DiagnosticSupport.CaseEvidence>
    selectionCases() {
        List<RagCandidate6DiagnosticSupport.CaseEvidence> cases = new ArrayList<>();
        List<String> shapes = List.of(
                "numeric-token", "numeric-token", "doc-prefix", "doc-prefix",
                "zero-padded", "zero-padded", "han-adjacent", "han-adjacent");
        for (int index = 0; index < 8; index++) {
            String family = String.format("c6s-t%02d", index + 1);
            cases.add(evidence(
                    family + "-zh", family, "zh", shapes.get(index), true,
                    false, false,
                    RagCandidate6DiagnosticSupport.RootCause.BASELINE_ALREADY_PRESENT,
                    RagCandidate6DiagnosticSupport.CounterfactualMode.NONE,
                    0.8D, 0.8D, 0.8D, 0.8D, true, true));
            cases.add(evidence(
                    family + "-en", family, "en", shapes.get(index), true,
                    false, false,
                    RagCandidate6DiagnosticSupport.RootCause.SQL_PRELIMIT_RANK_SUPPRESSION,
                    RagCandidate6DiagnosticSupport.CounterfactualMode.DUAL,
                    0.2D, 0.2D, 0.8D, 0.8D, false, true));
        }
        for (int index = 1; index <= 2; index++) {
            String family = String.format("c6s-n%02d", index);
            cases.add(evidence(family + "-zh", family, "zh", "none", false,
                    false, false, RagCandidate6DiagnosticSupport.RootCause.NONE,
                    RagCandidate6DiagnosticSupport.CounterfactualMode.NONE,
                    1.0D, 1.0D, 1.0D, 1.0D, true, true));
            cases.add(evidence(family + "-en", family, "en", "none", false,
                    false, false, RagCandidate6DiagnosticSupport.RootCause.NONE,
                    RagCandidate6DiagnosticSupport.CounterfactualMode.NONE,
                    1.0D, 1.0D, 1.0D, 1.0D, true, true));
        }
        for (int index = 1; index <= 2; index++) {
            String family = String.format("c6s-x%02d", index);
            cases.add(evidence(family + "-zh", family, "zh", "none", false,
                    true, false, RagCandidate6DiagnosticSupport.RootCause.NONE,
                    RagCandidate6DiagnosticSupport.CounterfactualMode.NONE,
                    0.0D, 0.0D, 0.0D, 0.0D, true, true));
            cases.add(evidence(family + "-en", family, "en", "none", false,
                    true, false, RagCandidate6DiagnosticSupport.RootCause.NONE,
                    RagCandidate6DiagnosticSupport.CounterfactualMode.NONE,
                    0.0D, 0.0D, 0.0D, 0.0D, true, true));
        }
        for (int index = 1; index <= 2; index++) {
            String family = String.format("c6s-s%02d", index);
            cases.add(evidence(family + "-zh", family, "zh", "numeric-token", false,
                    false, true, RagCandidate6DiagnosticSupport.RootCause.NONE,
                    RagCandidate6DiagnosticSupport.CounterfactualMode.NONE,
                    0.0D, 0.0D, 0.0D, 0.0D, true, true));
            cases.add(evidence(family + "-en", family, "en", "numeric-token", false,
                    false, true, RagCandidate6DiagnosticSupport.RootCause.NONE,
                    RagCandidate6DiagnosticSupport.CounterfactualMode.NONE,
                    0.0D, 0.0D, 0.0D, 0.0D, true, true));
        }
        return List.copyOf(cases);
    }

    private static RagCandidate6DiagnosticSupport.CaseEvidence evidence(
            String queryId,
            String familyId,
            String language,
            String shape,
            boolean target,
            boolean noExact,
            boolean safety,
            RagCandidate6DiagnosticSupport.RootCause root,
            RagCandidate6DiagnosticSupport.CounterfactualMode mode,
            double baselineAp,
            double baselineNdcg,
            double counterfactualAp,
            double counterfactualNdcg,
            boolean unchanged,
            boolean safetyValid) {
        return new RagCandidate6DiagnosticSupport.CaseEvidence(
                queryId, familyId, language, shape, target, noExact, safety,
                root, mode, baselineAp, baselineNdcg,
                counterfactualAp, counterfactualNdcg, unchanged, safetyValid,
                4L, 2L, 1L, 3L, 0L, 0L, 0L, 0L, 0L);
    }

    private static RagCandidate6DiagnosticSupport.CaseEvidence copyWithSafety(
            RagCandidate6DiagnosticSupport.CaseEvidence value, boolean safe) {
        return new RagCandidate6DiagnosticSupport.CaseEvidence(
                value.queryId(), value.familyId(), value.language(),
                value.identifierShape(), value.target(), value.noExactMatchControl(),
                value.safetyControl(), value.classification(), value.counterfactualMode(),
                value.baselineAp(), value.baselineNdcg(), value.counterfactualAp(),
                value.counterfactualNdcg(), value.behaviorUnchanged(), safe,
                value.sutDbCalls(), value.diagnosticSqlCalls(),
                value.diagnosticContextDbCalls(), value.diagnosticTotalDbCalls(),
                value.addedEmbeddingCalls(), value.addedVectorCalls(),
                value.addedMetadataCalls(), value.addedGraphCalls(),
                value.addedNetworkCalls());
    }

    private static RagCandidate6DiagnosticSupport.CaseEvidence copyWithRoot(
            RagCandidate6DiagnosticSupport.CaseEvidence value,
            RagCandidate6DiagnosticSupport.RootCause root) {
        return new RagCandidate6DiagnosticSupport.CaseEvidence(
                value.queryId(), value.familyId(), value.language(),
                value.identifierShape(), value.target(), value.noExactMatchControl(),
                value.safetyControl(), root, value.counterfactualMode(),
                value.baselineAp(), value.baselineNdcg(), value.counterfactualAp(),
                value.counterfactualNdcg(), value.behaviorUnchanged(), value.safetyValid(),
                value.sutDbCalls(), value.diagnosticSqlCalls(),
                value.diagnosticContextDbCalls(), value.diagnosticTotalDbCalls(),
                value.addedEmbeddingCalls(), value.addedVectorCalls(),
                value.addedMetadataCalls(), value.addedGraphCalls(),
                value.addedNetworkCalls());
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private record Fixture(Path selection, Path holdout) {
    }
}
