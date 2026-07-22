package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCandidate8DiagnosticSupportTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.1-pg16@sha256:33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337")
            .asCompatibleSubstituteFor("postgres");

    @TempDir
    Path temp;

    @Test
    @EnabledIfSystemProperty(named = RagCandidate8DiagnosticSupport.FREEZE_PROPERTY,
            matches = "true")
    @DisabledIfSystemProperty(
            named = RagCandidate8DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void freezesExpandedDatasetsAndLoadsSelectionWithoutHoldoutAccess()
            throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("runtime");

        RagCandidate8DiagnosticSupport.FrozenManifests frozen =
                RagCandidate8DiagnosticSupport.freezeDatasets(
                        runtime, fixture.selection(), fixture.holdout());

        assertEquals("FROZEN", frozen.selection().freezeStatus());
        assertEquals("FROZEN_NOT_BLIND", frozen.holdout().freezeStatus());
        assertEquals(32, frozen.selection().counts().get("queryCount"));
        assertEquals(607, frozen.selection().counts().get("segmentCount"));
        assertEquals(607, frozen.selection().counts().get("documentCount"));
        assertEquals(56, frozen.selection().counts().get("qrelPairCount"));
        assertEquals(64, frozen.holdout().counts().get("queryCount"));
        assertEquals(958, frozen.holdout().counts().get("segmentCount"));
        assertEquals(112, frozen.holdout().counts().get("qrelPairCount"));
        assertEquals(16, frozen.selection().counts().get("familyCount"));
        assertEquals(32, frozen.holdout().counts().get("familyCount"));
        assertEquals(64, frozen.selection().datasetHash().length());

        JSONObject selectionManifest = JSON.parseObject(Files.readString(
                RagCandidate8DiagnosticSupport.paths(runtime).selectionManifest()));
        assertFalse(selectionManifest.toJSONString().contains(
                fixture.holdout().toString()));
        assertFalse(selectionManifest.toJSONString().contains("holdout-dir"));
        JSONObject structure = selectionManifest.getJSONObject("structure");
        assertFalse(structure.getBooleanValue("crossFamilySharing"));
        assertEquals(31, structure.getIntValue("evidenceControlUniqueSegmentCount"));
        assertEquals(24, structure.getJSONObject("roleTable")
                .getJSONObject("target").getIntValue("uniqueSegmentCount"));
        assertEquals(48, structure.getJSONObject("roleTable")
                .getJSONObject("target").getIntValue("qrelPairCount"));
        assertTrue(structure.getJSONArray("englishStopWords").contains("evidence"));
        assertTrue(structure.getJSONArray("identifierCues").contains("topic"));
        assertEquals("postgres16-c-space-punctuation-v1",
                structure.getString("boundaryPolicy"));
        assertEquals(10_124_999, structure.getJSONObject("idMappingRule")
                .getIntValue("segmentIdMax"));

        RagCandidate8DiagnosticSupport.FrozenDataset loaded =
                RagCandidate8DiagnosticSupport.loadFrozenSelection(
                        runtime, fixture.selection());
        assertEquals(607, loaded.dataset().corpusById().size());
        assertEquals(4, loaded.auditedReads().selectionResourceAccessCount());
        assertEquals(2, loaded.auditedReads().manifestAccessCount());
        assertEquals(0, loaded.auditedReads().holdoutResourceAccessCount());
        assertEquals(frozen.selection().datasetHash(), loaded.datasetHash());

        byte[] original = Files.readAllBytes(
                RagCandidate8DiagnosticSupport.paths(runtime).selectionManifest());
        RagCandidate8DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(
                RagCandidate8DiagnosticSupport.paths(runtime).selectionManifest())));
    }

    @Test
    @EnabledIfSystemProperty(named = RagCandidate8DiagnosticSupport.FREEZE_PROPERTY,
            matches = "true")
    @DisabledIfSystemProperty(
            named = RagCandidate8DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void rejectsManifestResourcePathInjection() throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("runtime");
        RagCandidate8DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        Path manifestPath = RagCandidate8DiagnosticSupport.paths(runtime)
                .selectionManifest();
        JSONObject manifest = JSON.parseObject(Files.readString(manifestPath));
        manifest.getJSONObject("resources").getJSONObject("corpus")
                .put("file", "../corpus.jsonl");
        Files.writeString(manifestPath, manifest.toJSONString(), StandardCharsets.UTF_8);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RagCandidate8DiagnosticSupport.loadFrozenManifests(
                        RagCandidate8DiagnosticSupport.paths(runtime)));
        assertEquals("CANDIDATE8_MANIFEST_INVALID", failure.getMessage());
    }

    @Test
    void rejectsCrashTemporaryArtifactsWithoutDeletingThem() throws Exception {
        Path runtime = temp.resolve("runtime");
        RagCandidate8DiagnosticSupport.RuntimePaths paths =
                RagCandidate8DiagnosticSupport.paths(runtime);
        Files.createDirectories(paths.freezeDirectory());
        Path partial = paths.freezeDirectory().resolve(
                "selection-ledger.json-orphan.tmp");
        Files.writeString(partial, "partial", StandardCharsets.UTF_8);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RagCandidate8DiagnosticSupport.requireSelectionRunAvailable(paths));
        assertEquals("INVALID_INCOMPLETE_PRIOR_RUN", failure.getMessage());
        assertTrue(Files.exists(partial));
    }

    @Test
    @EnabledIfSystemProperty(named = RagCandidate8DiagnosticSupport.FREEZE_PROPERTY,
            matches = "true")
    @DisabledIfSystemProperty(
            named = RagCandidate8DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void rejectsMutationSymlinksAndHoldoutSelectionProperty() throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("runtime");
        RagCandidate8DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        Files.writeString(fixture.selection().resolve("pressure.json"), "\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        IllegalStateException mutation = assertThrows(IllegalStateException.class,
                () -> RagCandidate8DiagnosticSupport.freezeDatasets(
                        runtime, fixture.selection(), fixture.holdout()));
        assertEquals("CANDIDATE8_FREEZE_ALREADY_FROZEN", mutation.getMessage());
        assertFalse(Files.exists(
                RagCandidate8DiagnosticSupport.paths(runtime).ledger()));

        String previous = System.getProperty(
                RagCandidate8DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY);
        try {
            System.setProperty(
                    RagCandidate8DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY, "");
            IllegalStateException forbidden = assertThrows(
                    IllegalStateException.class,
                    RagCandidate8DiagnosticSupport::requireSelectionJobProperties);
            assertEquals("CANDIDATE8_HOLDOUT_ACCESS_FORBIDDEN",
                    forbidden.getMessage());
        } finally {
            restoreProperty(
                    RagCandidate8DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
                    previous);
        }

        Path symlink = temp.resolve("selection-link");
        try {
            Files.createSymbolicLink(symlink, fixture.selection());
            IllegalArgumentException linkFailure = assertThrows(
                    IllegalArgumentException.class,
                    () -> RagCandidate8DiagnosticSupport.freezeDatasets(
                            temp.resolve("link-runtime"), symlink, fixture.holdout()));
            assertEquals("CANDIDATE8_DATASET_DIRECTORY_INVALID",
                    linkFailure.getMessage());
        } catch (UnsupportedOperationException ignored) {
            // The contract is exercised on filesystems that support symbolic links.
        }
    }

    @Test
    void extractsImmutableOrderedSignalsAndUsesFrozenBoundaries() {
        RagCandidate8DiagnosticSupport.QuerySignals signals =
                RagCandidate8DiagnosticSupport.querySignals(
                        "主题 07621 的 Alpha lattice 核验证据，以及文档 7622 的 Beta archive");

        assertEquals(List.of("07621", "7622"), signals.identifiers());
        assertEquals("alpha", signals.contentTerms().get(0));
        assertTrue(signals.contentTerms().indexOf("核验证据")
                < signals.contentTerms().indexOf("核验证"));
        assertTrue(signals.contentTerms().indexOf("核验证")
                < signals.contentTerms().indexOf("核验"));
        assertThrows(UnsupportedOperationException.class,
                () -> signals.identifiers().add("9999"));
        assertThrows(UnsupportedOperationException.class,
                () -> signals.contentTerms().add("mutated"));

        for (String value : List.of("7611", "DOC-7611", "主题-7611-证据")) {
            assertTrue(RagCandidate8DiagnosticSupport.boundaryMatches(value, "7611"));
        }
        for (String value : List.of(
                "ABC7611", "17611", "76110", "主题7611证据")) {
            assertFalse(RagCandidate8DiagnosticSupport.boundaryMatches(value, "7611"));
        }
        assertTrue(RagCandidate8DiagnosticSupport.boundaryMatches(
                "DOC-07621", "07621"));
        assertFalse(RagCandidate8DiagnosticSupport.boundaryMatches(
                "DOC-07621", "7621"));
        assertTrue(RagCandidate8DiagnosticSupport.contentCorroborated(
                "Alpha lattice custody evidence", signals));
    }

    @Test
    void buildsSqlAndJavaAdmissionWithoutChangingLimitsOrScores() {
        String sql = """
                SELECT *
                FROM (
                    SELECT ?::bigint id, ?::text document_name,
                           ?::text content,
                           0.9::double precision trgm_score,
                           1::bigint document_id, 1::int position
                ) s
                ORDER BY trgm_score DESC, document_id ASC, position ASC NULLS LAST
                LIMIT ?
                """;
        RagCandidate8DiagnosticSupport.QuerySignals signals =
                RagCandidate8DiagnosticSupport.querySignals(
                        "Which alpha custody records apply to Topic 7611?");
                RagCandidate8DiagnosticSupport.CorroboratedFirstSql priority =
                RagCandidate8DiagnosticSupport.corroboratedFirstSql(
                        sql, new Object[]{
                                1L, "DOC-7611 alpha archive",
                                "alpha custody record", 500}, signals);

        assertTrue(priority.sql().contains("s.document_name ~"));
        assertTrue(priority.sql().contains(
                "strpos(lower(coalesce(s.content, '')), ?) > 0"));
        assertFalse(priority.sql().contains("7611"));
        for (String term : signals.contentTerms()) {
            assertFalse(priority.sql().contains(term));
        }
        assertEquals(List.of(
                        1L, "DOC-7611 alpha archive", "alpha custody record",
                        "7611", "alpha", "custody", "records", "apply", 500),
                priority.parameters());

        RetrievalResult ordinary = result(
                1L, "general archive", "alpha custody record", 0.9D, 0.5D);
        RetrievalResult anchor = result(
                2L, "DOC-7611 archive", "alpha custody record", 0.7D, 0.5D);
        List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> snapshots =
                RagCandidate8DiagnosticSupport.snapshotResults(
                        List.of(ordinary, anchor), signals);
        anchor.setScore(99D);
        anchor.setContent("mutated");

        assertEquals(0.7D, snapshots.get(1).score());
        assertEquals("alpha custody record", snapshots.get(1).content());
        List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> ordered =
                RagCandidate8DiagnosticSupport
                        .stableCorroboratedFirstJavaTop50(snapshots);
        assertEquals(List.of(2L, 1L), ordered.stream()
                .map(RagCandidate8DiagnosticSupport.RetrievalSnapshot::segmentId)
                .toList());
        assertEquals(0.7D, ordered.get(0).score());
    }

    @Test
    void tieReplacementUsesIdentifierFilterAndTailSlotOrderWithoutDedupingPrefix() {
        RagCandidate8DiagnosticSupport.QuerySignals signals =
                RagCandidate8DiagnosticSupport.querySignals(
                        "Which violet grid custody records apply to Topic 7611 and Topic 7622?");
        RagCandidate8DiagnosticSupport.RetrievalSnapshot slotA = snapshot(
                0, 1L, "ordinary-a", "violet grid custody", 0.9D, 0.5D,
                false, true);
        RagCandidate8DiagnosticSupport.RetrievalSnapshot duplicate = snapshot(
                1, 1L, "ordinary-a", "violet grid custody", 0.8D, 0.5D,
                false, true);
        RagCandidate8DiagnosticSupport.RetrievalSnapshot slotB = snapshot(
                2, 3L, "ordinary-b", "violet grid custody", 0.7D, 0.5D,
                false, true);
        RagCandidate8DiagnosticSupport.RetrievalSnapshot anchorFirst = snapshot(
                3, 11L, "Topic 7611 archive", "violet grid custody", 0.6D, 0.5D,
                true, true);
        RagCandidate8DiagnosticSupport.RetrievalSnapshot anchorSecond = snapshot(
                4, 22L, "Topic 7622 archive", "violet grid custody", 0.6D, 0.5D,
                true, true);
        List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> prefix =
                List.of(slotA, duplicate, slotB);
        List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> full =
                List.of(slotA, duplicate, slotB, anchorSecond, anchorFirst);
        List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> filter =
                List.of(anchorFirst, anchorSecond, slotA, duplicate, slotB);

        List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> replaced =
                RagCandidate8DiagnosticSupport.tieReplace(
                        prefix, full, filter, signals);

        assertEquals(List.of(1L, 11L, 22L), replaced.stream()
                .map(RagCandidate8DiagnosticSupport.RetrievalSnapshot::segmentId)
                .toList());
        assertEquals(0.6D, replaced.get(1).score());
        assertEquals(prefix, RagCandidate8DiagnosticSupport.tieReplace(
                prefix, prefix, filter, signals));
        assertEquals(List.of(1L, 1L, 3L), prefix.stream()
                .map(RagCandidate8DiagnosticSupport.RetrievalSnapshot::segmentId)
                .toList());
    }

    @Test
    void tieReplacementFailsClosedOutsideExactCutoffCapacity() {
        RagCandidate8DiagnosticSupport.QuerySignals signals =
                RagCandidate8DiagnosticSupport.querySignals(
                        "Which violet custody record applies to Topic 7611 and Topic 7622?");
        RagCandidate8DiagnosticSupport.RetrievalSnapshot ordinary = snapshot(
                0, 1L, "ordinary", "violet custody", 0.9D, 0.5D,
                false, true);
        RagCandidate8DiagnosticSupport.RetrievalSnapshot tiedSlot = snapshot(
                1, 2L, "ordinary-two", "violet custody", 0.8D, 0.5D,
                false, true);
        RagCandidate8DiagnosticSupport.RetrievalSnapshot below = snapshot(
                2, 11L, "Topic 7611 archive", "violet custody", 0.7D, 0.4D,
                true, true);
        List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> prefix =
                List.of(ordinary, tiedSlot);
        assertEquals(prefix, RagCandidate8DiagnosticSupport.tieReplace(
                prefix, List.of(ordinary, tiedSlot, below), List.of(below), signals));

        RagCandidate8DiagnosticSupport.RetrievalSnapshot nonFinite = snapshot(
                1, 3L, "ordinary-three", "violet custody", 0.8D, Double.NaN,
                false, true);
        List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> nonFinitePrefix =
                List.of(ordinary, nonFinite);
        assertEquals(nonFinitePrefix, RagCandidate8DiagnosticSupport.tieReplace(
                nonFinitePrefix, List.of(ordinary, nonFinite, below),
                List.of(below), signals));

        RagCandidate8DiagnosticSupport.RetrievalSnapshot existing = snapshot(
                1, 21L, "Topic 7611 and 7622 archive", "violet custody",
                0.8D, 0.5D, true, true);
        List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> existingPrefix =
                List.of(ordinary, existing);
        assertEquals(existingPrefix, RagCandidate8DiagnosticSupport.tieReplace(
                existingPrefix, List.of(ordinary, existing, below),
                List.of(existing, below), signals));

        RagCandidate8DiagnosticSupport.RetrievalSnapshot noSlot = snapshot(
                0, 4L, "ordinary-four", "violet custody", 0.9D, 0.6D,
                false, true);
        RagCandidate8DiagnosticSupport.RetrievalSnapshot anchor = snapshot(
                2, 22L, "Topic 7611 and 7622 archive", "violet custody",
                0.7D, 0.5D, true, true);
        List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> noSlotPrefix =
                List.of(noSlot, existing);
        assertEquals(noSlotPrefix, RagCandidate8DiagnosticSupport.tieReplace(
                noSlotPrefix, List.of(noSlot, existing, anchor),
                List.of(anchor), signals));

        List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> restored =
                RagCandidate8DiagnosticSupport.tieReplace(
                        prefix, List.of(ordinary, tiedSlot, anchor),
                        List.of(anchor), signals);
        assertEquals(1L, restored.stream()
                .filter(item -> Long.valueOf(22L).equals(item.segmentId())).count());
    }

    @Test
    @EnabledIfSystemProperty(named = RagCandidate8DiagnosticSupport.FREEZE_PROPERTY,
            matches = "true")
    @DisabledIfSystemProperty(
            named = RagCandidate8DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void ledgerPublishesOnceAndRejectsForbiddenArtifactFields() throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("runtime");
        RagCandidate8DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        RagCandidate8DiagnosticSupport.FrozenDataset frozen =
                RagCandidate8DiagnosticSupport.loadFrozenSelection(
                        runtime, fixture.selection());
        RagCandidate8DiagnosticSupport.RuntimePaths paths =
                RagCandidate8DiagnosticSupport.paths(runtime);
        RagCandidate8DiagnosticSupport.RunHandle handle =
                RagCandidate8DiagnosticSupport.beginSelectionRun(paths, frozen);

        Map<String, Object> forbidden = new LinkedHashMap<>(
                RagCandidate8DiagnosticSupport.freshInvalidArtifact(
                        frozen, config(), "CANDIDATE8_TEST_INVALID"));
        for (String field : List.of(
                "query", "identifiers", "contentTerms", "qrelGrade",
                "sqlParameter", "exceptionMessage")) {
            forbidden.put("nested", Map.of(field, "must not persist"));
            assertThrows(IllegalStateException.class,
                    () -> RagCandidate8DiagnosticSupport.writeDiagnosticAndComplete(
                            paths, handle, forbidden));
        }
        forbidden.put("nested", new Object[]{Map.of(
                "content", "must not persist")});
        assertThrows(IllegalStateException.class,
                () -> RagCandidate8DiagnosticSupport.writeDiagnosticAndComplete(
                        paths, handle, forbidden));
        assertFalse(Files.exists(paths.diagnostic()));

        Map<String, Object> artifact =
                RagCandidate8DiagnosticSupport.freshInvalidArtifact(
                        frozen, config(), "CANDIDATE8_TEST_INVALID");
        RagCandidate8DiagnosticSupport.writeDiagnosticAndComplete(
                paths, handle, artifact);
        JSONObject ledger = JSON.parseObject(Files.readString(paths.ledger()));
        assertEquals("COMPLETED", ledger.getString("status"));
        assertEquals(ShadowContractSupport.sha256(paths.diagnostic()),
                ledger.getString("artifactSha256"));
        assertEquals(ShadowContractSupport.configHash(config()),
                ledger.getString("configHash"));
        assertEquals(0, JSON.parseObject(Files.readString(paths.diagnostic()))
                .getJSONObject("auditedReads")
                .getIntValue("holdoutResourceAccessCount"));
        assertThrows(IllegalStateException.class,
                () -> RagCandidate8DiagnosticSupport.writeDiagnosticAndComplete(
                        paths, handle, artifact));
    }

    @Test
    @EnabledIfSystemProperty(named = RagCandidate8DiagnosticSupport.FREEZE_PROPERTY,
            matches = "true")
    @DisabledIfSystemProperty(
            named = RagCandidate8DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void rejectsFabricatedProceedEvenWhenClaimsAreInternallyConsistent() {
        Fixture fixture;
        try {
            fixture = copyFixture();
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        Path runtime = temp.resolve("runtime");
        RagCandidate8DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        RagCandidate8DiagnosticSupport.FrozenDataset frozen =
                RagCandidate8DiagnosticSupport.loadFrozenSelection(
                        runtime, fixture.selection());
        RagCandidate8DiagnosticSupport.beginSelectionRun(
                RagCandidate8DiagnosticSupport.paths(runtime), frozen);
        List<RagCandidate8DiagnosticSupport.CaseEvidence> evidence = validCases();
        List<Map<String, Object>> cases = evidence.stream()
                .map(RagCandidate8DiagnosticSupportTest::sanitizedCase)
                .toList();

        assertThrows(IllegalArgumentException.class,
                () -> RagCandidate8DiagnosticSupport.freshValidArtifact(
                        frozen, config(), sha256("ranking-phase"), cases));
    }

    @Test
    @EnabledIfSystemProperty(named = RagCandidate8DiagnosticSupport.FREEZE_PROPERTY,
            matches = "true")
    @DisabledIfSystemProperty(
            named = RagCandidate8DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".*")
    void derivesValidStopFromFrozenDataAndFourArmRankings() throws Exception {
        Fixture fixture = copyFixture();
        Path runtime = temp.resolve("runtime");
        RagCandidate8DiagnosticSupport.freezeDatasets(
                runtime, fixture.selection(), fixture.holdout());
        RagCandidate8DiagnosticSupport.FrozenDataset frozen =
                RagCandidate8DiagnosticSupport.loadFrozenSelection(
                        runtime, fixture.selection());
        RagCandidate8DiagnosticSupport.beginSelectionRun(
                RagCandidate8DiagnosticSupport.paths(runtime), frozen);
        List<Map<String, Object>> cases = frozen.dataset().queries().stream()
                .map(RagCandidate8DiagnosticSupportTest::sanitizedStopCase)
                .toList();
        String rankingPhaseSha256 = sha256(JSON.toJSONString(
                cases.stream().map(item -> item.get("rankingSha256")).toList(),
                JSONWriter.Feature.MapSortField));

        Map<String, Object> artifact =
                RagCandidate8DiagnosticSupport.freshValidArtifact(
                        frozen, config(), rankingPhaseSha256, cases);
        assertEquals("VALID", artifact.get("status"));
        assertEquals(RagCandidate8DiagnosticSupport.STOP_DECISION,
                artifact.get("decision"));
    }

    @Test
    void fourArmDecisionRequiresJointEnglishImprovementAndStableControls() {
        List<RagCandidate8DiagnosticSupport.CaseEvidence> evidence = validCases();
        assertEquals(RagCandidate8DiagnosticSupport.PROCEED_DECISION,
                RagCandidate8DiagnosticSupport.decide(evidence));

        List<RagCandidate8DiagnosticSupport.CaseEvidence> admissionChanged =
                new ArrayList<>(evidence);
        RagCandidate8DiagnosticSupport.CaseEvidence original =
                admissionChanged.get(1);
        admissionChanged.set(1, original.withAdmissionOnlyUnchanged(false));
        assertEquals(RagCandidate8DiagnosticSupport.STOP_DECISION,
                RagCandidate8DiagnosticSupport.decide(admissionChanged));

        assertEquals(2, RagCandidate8DiagnosticSupport.enabledDiagnosticCount(
                false, false, false, false, false, true, true));
        assertEquals(1, RagCandidate8DiagnosticSupport.enabledDiagnosticCount(
                false, false, false, false, false, false, true));

        List<RagCandidate8DiagnosticSupport.CaseEvidence> duplicateQuery =
                new ArrayList<>(evidence);
        duplicateQuery.set(1, evidence.get(0));
        assertEquals(RagCandidate8DiagnosticSupport.STOP_DECISION,
                RagCandidate8DiagnosticSupport.decide(duplicateQuery));
    }

    @Test
    @EnabledIfSystemProperty(named = RagCandidate8DiagnosticSupport.FREEZE_PROPERTY,
            matches = "true")
    @EnabledIfSystemProperty(
            named = RagCandidate8DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY,
            matches = ".+")
    void freezesFormalDatasetsOnlyWhenExplicitlyEnabled() {
        String holdout = System.getProperty(
                RagCandidate8DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY);
        if (holdout == null || holdout.isBlank()) {
            throw new IllegalStateException("CANDIDATE8_HOLDOUT_DIRECTORY_REQUIRED");
        }
        verifyPostgresBoundarySemantics();
        RagCandidate8DiagnosticSupport.freezeFormalDatasets(
                Path.of("target/rag-eval"), Path.of(holdout));
    }

    private static void verifyPostgresBoundarySemantics() {
        try (PostgreSQLContainer<?> container = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName("candidate8")
                .withUsername("candidate8")
                .withPassword("candidate8")
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
            String exactSql = "SELECT ? ~ ("
                    + "'(^|[[:space:][:punct:]])' || ? || "
                    + "'([[:space:][:punct:]]|$)')";
            for (String value : List.of(
                    "7611", "DOC-7611", "主题-7611-证据")) {
                assertEquals(
                        RagCandidate8DiagnosticSupport.boundaryMatches(value, "7611"),
                        Boolean.TRUE.equals(jdbc.queryForObject(
                                exactSql, Boolean.class, value, "7611")));
            }
            for (String value : List.of(
                    "ABC7611", "17611", "76110", "主题7611证据")) {
                assertEquals(
                        RagCandidate8DiagnosticSupport.boundaryMatches(value, "7611"),
                        Boolean.TRUE.equals(jdbc.queryForObject(
                                exactSql, Boolean.class, value, "7611")));
            }
            assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
                    exactSql, Boolean.class, "DOC-07621", "07621")));
            assertFalse(Boolean.TRUE.equals(jdbc.queryForObject(
                    exactSql, Boolean.class, "DOC-07621", "7621")));
        }
    }

    private Fixture copyFixture() throws IOException {
        Path tests = testsDirectory();
        Path selection = temp.resolve("selection");
        copyDataset(tests.resolve(
                "src/test/resources/rag-eval/candidate8-selection"), selection);
        return new Fixture(selection, tests.resolve("candidate8-holdout"));
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

    private static Map<String, Object> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("candidate3Enabled", true);
        config.put("candidate8Enabled", false);
        config.put("identifierAware", false);
        config.put("identifierConsistencyEnabled", true);
        config.put("identifierEvidenceCorroboratedTieEnabled", false);
        config.put("localRerankerEnabled", false);
        config.put("onnxRerankerEnabled", false);
        config.put("topK", 10);
        config.put("keywordCandidateTopK", 50);
        config.put("keywordSqlLimit", 500);
        config.put("businessColbertLimit", 30);
        config.put("nativeMode", "java-fallback");
        config.put("colbert", Map.of("enabled", true));
        config.put("executor", Map.of("core", 4, "max", 4, "queue", 32));
        config.put("featureHash", Map.of(
                "version", "test", "dimensions", 256, "seed", 20260715L));
        config.put("candidate8EvidenceAlgorithm",
                "corroborated-identifier-cutoff-tie-diagnostic-v1");
        config.put("identifierEvidenceEligibilityPolicy",
                "deterministic-config-fail-closed-v2");
        config.put("identifierEvidenceAdmissionPolicy",
                "document-name-content-corroborated-exact-first-v1");
        config.put("identifierEvidenceSurvivalPolicy",
                "colbert-cutoff-tie-replacement-v1");
        config.put("identifierEvidenceContentPolicy",
                "ascii-han-non-identifier-content-v1");
        config.put("identifierEvidenceColbertFailurePolicy",
                "request-fail-closed-v1");
        config.put("kbRemoteReranking", Map.of(
                "enabled", false, "providerName", "", "modelName", ""));
        String providerPackage =
                "tech.qiantong.qknow.module.kmc.service.rag.rerank.";
        config.put("providerInventory", List.of(
                providerPackage + "DeterministicRerankerProvider"));
        config.put("eligibleProviderClasses", List.of(
                providerPackage + "DashScopeRerankerProvider",
                providerPackage + "DeterministicRerankerProvider",
                providerPackage + "LocalBgeRerankerProvider",
                providerPackage + "LocalRerankerProvider",
                providerPackage + "OnnxRerankerProvider"));
        return config;
    }

    private static RetrievalResult result(
            long segmentId,
            String documentName,
            String content,
            double score,
            double colbertScore) {
        return RetrievalResult.builder()
                .segmentId(segmentId)
                .documentId(segmentId + 100L)
                .qmSegmentId(String.valueOf(segmentId))
                .documentName(documentName)
                .content(content)
                .score(score)
                .source("keyword")
                .metadata(Map.of("colbert_score", colbertScore))
                .build();
    }

    private static RagCandidate8DiagnosticSupport.RetrievalSnapshot snapshot(
            int ordinal,
            long segmentId,
            String documentName,
            String content,
            double score,
            double colbertScore,
            boolean exact,
            boolean corroborated) {
        return new RagCandidate8DiagnosticSupport.RetrievalSnapshot(
                ordinal, segmentId, segmentId + 100L, String.valueOf(segmentId),
                null, documentName, content, null, score, "keyword",
                colbertScore, exact, corroborated, exact && corroborated);
    }

    private static List<RagCandidate8DiagnosticSupport.CaseEvidence> validCases() {
        List<RagCandidate8DiagnosticSupport.CaseEvidence> cases = new ArrayList<>();
        for (int family = 1; family <= 8; family++) {
            String familyId = String.format("c8s-t%02d", family);
            cases.add(RagCandidate8DiagnosticSupport.CaseEvidence.target(
                    familyId + "-zh", familyId, "zh", true,
                    0.8D, 0.8D, 0.8D, 0.8D));
            cases.add(RagCandidate8DiagnosticSupport.CaseEvidence.target(
                    familyId + "-en", familyId, "en", true,
                    0.2D, 0.2D, 0.8D, 0.8D));
        }
        for (String role : List.of(
                "no-identifier", "no-exact-match", "exact-only-safety",
                "lexical-lure-below-cutoff", "existing-survivor",
                "multi-id-collision")) {
            int families = List.of("no-identifier", "no-exact-match")
                    .contains(role) ? 2 : 1;
            for (int family = 1; family <= families; family++) {
                String familyId = "c8s-" + role + "-" + family;
                cases.add(RagCandidate8DiagnosticSupport.CaseEvidence.control(
                        familyId + "-zh", familyId, "zh", role, true));
                cases.add(RagCandidate8DiagnosticSupport.CaseEvidence.control(
                        familyId + "-en", familyId, "en", role, true));
            }
        }
        assertEquals(32, cases.size());
        return List.copyOf(cases);
    }

    private static Map<String, Object> sanitizedCase(
            RagCandidate8DiagnosticSupport.CaseEvidence evidence) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("queryId", evidence.queryId());
        value.put("familyId", evidence.familyId());
        value.put("language", evidence.language());
        value.put("role", evidence.role());
        value.put("target", evidence.target());
        value.put("mechanismValid", evidence.mechanismValid());
        value.put("baselineAP@10", evidence.baselineAp());
        value.put("baselineNDCG@10", evidence.baselineNdcg());
        value.put("jointAP@10", evidence.jointAp());
        value.put("jointNDCG@10", evidence.jointNdcg());
        value.put("admissionOnlyUnchanged", evidence.admissionOnlyUnchanged());
        value.put("survivalOnlyUnchanged", evidence.survivalOnlyUnchanged());
        value.put("controlUnchanged", evidence.controlUnchanged());
        value.put("classification", "KEYWORD_TOPK_DOWNSTREAM_SUPPRESSION");
        value.put("rankingSha256", sha256(evidence.queryId()));
        value.put("variants", List.of(Map.of(
                "variantSha256", sha256(evidence.queryId() + "-variant"))));
        value.put("arms", Map.of(
                "BASELINE", Map.of("finalSources", List.of()),
                "ADMISSION_ONLY", Map.of("finalSources", List.of()),
                "SURVIVAL_ONLY", Map.of("finalSources", List.of()),
                "JOINT", Map.of("finalSources", List.of())));
        value.put("callCounts", Map.ofEntries(
                Map.entry("sutDbCalls", 1L),
                Map.entry("diagnosticSqlCalls", 2L),
                Map.entry("diagnosticContextDbCalls", 3L),
                Map.entry("diagnosticTotalDbCalls", 5L),
                Map.entry("diagnosticBusinessColbertCalls", 4L),
                Map.entry("diagnosticFullColbertCalls", 4L),
                Map.entry("sutEmbeddingCalls", 1L),
                Map.entry("diagnosticBusinessEmbeddingCalls", 0L),
                Map.entry("diagnosticFullEmbeddingCalls", 0L),
                Map.entry("diagnosticEmbeddingCalls", 0L),
                Map.entry("addedVectorCalls", 0L),
                Map.entry("addedMetadataCalls", 0L),
                Map.entry("addedGraphCalls", 0L),
                Map.entry("addedNetworkCalls", 0L)));
        return value;
    }

    private static Map<String, Object> sanitizedStopCase(
            RagEvaluationDataset.QueryCase query) {
        RagCandidate8DiagnosticSupport.QuerySignals signals =
                RagCandidate8DiagnosticSupport.querySignals(query.retrievalQuery());
        String role = query.strata().contains("candidate8-target")
                ? "target"
                : List.of(
                        "no-identifier", "no-exact-match", "exact-only-safety",
                        "lexical-lure-below-cutoff", "existing-survivor",
                        "multi-id-collision").stream()
                .filter(query.strata()::contains).findFirst().orElseThrow();
        Map<String, Object> baseline = emptyArm();
        Map<String, Object> admission = emptyArm();
        Map<String, Object> survival = emptyArm();
        Map<String, Object> joint = emptyArm();
        Map<String, Object> ranking = new LinkedHashMap<>();
        ranking.put("queryId", query.id());
        ranking.put("familyId", query.familyId());
        ranking.put("BASELINE", baseline);
        ranking.put("ADMISSION_ONLY", admission);
        ranking.put("SURVIVAL_ONLY", survival);
        ranking.put("JOINT", joint);
        boolean target = "target".equals(role);
        boolean mechanismValid = target
                ? "zh".equals(query.language())
                : !"existing-survivor".equals(role);
        Map<String, Object> value = sanitizedCase(new
                RagCandidate8DiagnosticSupport.CaseEvidence(
                query.id(), query.familyId(), query.language(), role, target,
                mechanismValid, 0.0D, 0.0D, 0.0D, 0.0D,
                true, true, true));
        value.put("split", query.split());
        value.put("originalQuerySha256", sha256(query.query()));
        value.put("retrievalQuerySha256", sha256(query.retrievalQuery()));
        value.put("extractedIdentifierCount", signals.identifiers().size());
        value.put("extractedIdentifierHash",
                sha256(JSON.toJSONString(signals.identifiers())));
        value.put("extractedContentTermCount", signals.contentTerms().size());
        value.put("extractedContentTermHash",
                sha256(JSON.toJSONString(signals.contentTerms())));
        value.put("classification", "NONE");
        value.put("rankingSha256", sha256(JSON.toJSONString(
                ranking, JSONWriter.Feature.MapSortField)));
        value.put("arms", Map.of(
                "BASELINE", baseline,
                "ADMISSION_ONLY", admission,
                "SURVIVAL_ONLY", survival,
                "JOINT", joint));
        return value;
    }

    private static Map<String, Object> emptyArm() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("filterOutput", List.of());
        value.put("businessColbert", List.of());
        value.put("fullColbert", List.of());
        value.put("tieOutput", List.of());
        value.put("finalSources", List.of());
        value.put("contextSha256", sha256(""));
        value.put("contextEmpty", true);
        value.put("tieChanged", false);
        value.put("cutoffMechanismValid", false);
        return value;
    }

    private static String sha256(String value) {
        return ShadowContractSupport.sha256(
                value.getBytes(StandardCharsets.UTF_8));
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
