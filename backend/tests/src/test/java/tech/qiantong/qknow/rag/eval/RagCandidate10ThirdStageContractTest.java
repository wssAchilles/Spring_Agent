package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCandidate10ThirdStageContractTest {

    private static final String ZERO_SHA = "0".repeat(64);
    private static final String ONE_SHA = "1".repeat(64);

    @TempDir
    Path temp;

    @Test
    void allContracts() throws Exception {
        RagCandidate10FixtureGenerator.GeneratedSplit selection =
                RagCandidate10FixtureGenerator.selection();
        RagCandidate10FixtureGenerator.GeneratedSplit selectionAgain =
                RagCandidate10FixtureGenerator.selection();
        RagCandidate10FixtureGenerator.GeneratedSplit holdout =
                RagCandidate10FixtureGenerator.holdout();

        assertSplit(selection, 20, 40, 1_120, 46,
                10_160_001L, 10_161_120L,
                10_165_000L, 10_166_119L);
        assertSplit(holdout, 40, 80, 2_240, 92,
                10_170_001L, 10_172_240L,
                10_175_000L, 10_177_239L);
        assertRepeatable(selection, selectionAgain);
        assertRoleAndShapeArithmetic(selection, 1);
        assertRoleAndShapeArithmetic(holdout, 2);
        assertIdentifierExtraction(selection);
        assertLongTokenBoundary(selection);
        assertQrelPostRankingBoundary();
        assertCanonicalJsonOrdering();
        assertCanonicalHashCorruptionFailsClosed(selection);
        assertSourceLockDriftFailsClosed();
        assertFormalHarnessClosureContracts();
        assertSurefireReportParserContracts();
        assertPublicationGuards();
        assertDiagnosticContracts();
        assertEarlySourceLockInvalidCompletes();
        assertFallbackIsObservableAndRejected();
    }

    private static void assertSplit(
            RagCandidate10FixtureGenerator.GeneratedSplit split,
            int families,
            int queries,
            int segments,
            int qrels,
            long firstSegment,
            long lastSegment,
            long firstDocument,
            long lastDocument) {
        assertEquals(families, split.families().size());
        assertEquals(queries, split.dataset().queries().size());
        assertEquals(segments, split.dataset().corpusById().size());
        assertEquals(qrels, split.dataset().qrels().values().stream()
                .mapToInt(Map::size).sum());
        assertEquals(families, split.counts().get("familyCount"));
        assertEquals(queries, split.counts().get("queryCount"));
        assertEquals(segments, split.counts().get("segmentCount"));
        assertEquals(qrels, split.counts().get("qrelPairCount"));
        assertEquals(Set.of("corpus", "queries", "qrels", "pressure"),
                split.resources().keySet());
        assertEquals(64, split.datasetHash().length());
        assertEquals(split.datasetHash(),
                RagCandidate10FixtureGenerator.sha256(
                        split.datasetHashPreimage()));

        List<Long> segmentIds = split.dataset().corpusById().keySet().stream()
                .map(Long::valueOf).sorted().toList();
        List<Long> documentIds = split.dataset().corpusById().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId)
                .map(Long::valueOf).sorted().toList();
        assertEquals(firstSegment, segmentIds.get(0));
        assertEquals(lastSegment, segmentIds.get(segmentIds.size() - 1));
        assertEquals(firstDocument, documentIds.get(0));
        assertEquals(lastDocument, documentIds.get(documentIds.size() - 1));
        assertEquals(segments, new LinkedHashSet<>(documentIds).size());

        long segmentBase = split.split().segmentBase();
        long documentBase = split.split().documentBase();
        split.dataset().corpusById().values().forEach(segment -> {
            int ordinal = ((Number) segment.metadata().get("ordinal")).intValue();
            assertEquals(segmentBase + ordinal,
                    Long.parseLong(segment.segmentId()));
            assertEquals(documentBase + ordinal - 1L,
                    Long.parseLong(segment.documentId()));
            assertNull(segment.parentSegmentId());
        });

        split.resources().values().forEach(resource -> {
            byte[] bytes = resource.bytes();
            assertTrue(bytes.length > 1);
            assertEquals('\n', bytes[bytes.length - 1]);
            assertNotEquals('\n', bytes[bytes.length - 2]);
            assertEquals(resource.sha256(),
                    RagCandidate10FixtureGenerator.sha256(bytes));
        });
    }

    private static void assertRepeatable(
            RagCandidate10FixtureGenerator.GeneratedSplit first,
            RagCandidate10FixtureGenerator.GeneratedSplit second) {
        assertEquals(first.datasetHash(), second.datasetHash());
        assertEquals(first.fixtureSpecHash(), second.fixtureSpecHash());
        assertArrayEquals(first.datasetHashPreimage(),
                second.datasetHashPreimage());
        for (String resource : List.of(
                "corpus", "queries", "qrels", "pressure")) {
            assertArrayEquals(first.resource(resource).bytes(),
                    second.resource(resource).bytes());
            assertEquals(first.resource(resource).sha256(),
                    second.resource(resource).sha256());
        }

        Map<String, Object> left = new LinkedHashMap<>();
        left.put("z", 2);
        left.put("a", 1);
        Map<String, Object> right = new LinkedHashMap<>();
        right.put("a", 1);
        right.put("z", 2);
        assertArrayEquals(RagCandidate10FixtureGenerator.canonicalJsonBytes(left),
                RagCandidate10FixtureGenerator.canonicalJsonBytes(right));
    }

    private static void assertRoleAndShapeArithmetic(
            RagCandidate10FixtureGenerator.GeneratedSplit split, int scale) {
        Map<RagCandidate10FixtureGenerator.FamilyRole, Long> roles =
                split.families().stream().collect(Collectors.groupingBy(
                        RagCandidate10FixtureGenerator.FamilySpec::role,
                        () -> new EnumMap<>(
                                RagCandidate10FixtureGenerator.FamilyRole.class),
                        Collectors.counting()));
        assertEquals(8L * scale,
                roles.get(RagCandidate10FixtureGenerator.FamilyRole.TARGET));
        assertEquals(2L * scale,
                roles.get(RagCandidate10FixtureGenerator.FamilyRole.BASELINE_PRESENT));
        for (RagCandidate10FixtureGenerator.FamilyRole role
                : RagCandidate10FixtureGenerator.FamilyRole.values()) {
            if (role != RagCandidate10FixtureGenerator.FamilyRole.TARGET
                    && role != RagCandidate10FixtureGenerator.FamilyRole.BASELINE_PRESENT
                    && role != RagCandidate10FixtureGenerator.FamilyRole.BOUNDARY) {
                assertEquals(1L * scale, roles.get(role));
            }
        }
        assertEquals(2L * scale,
                roles.get(RagCandidate10FixtureGenerator.FamilyRole.BOUNDARY));

        Map<RagCandidate10FixtureGenerator.TargetShape, Long> shapes =
                split.families().stream()
                        .filter(family -> family.targetShape() != null)
                        .collect(Collectors.groupingBy(
                                RagCandidate10FixtureGenerator.FamilySpec::targetShape,
                                () -> new EnumMap<>(
                                        RagCandidate10FixtureGenerator.TargetShape.class),
                                Collectors.counting()));
        for (RagCandidate10FixtureGenerator.TargetShape shape
                : RagCandidate10FixtureGenerator.TargetShape.values()) {
            assertEquals(2L * scale, shapes.get(shape));
        }

        List<RagEvaluationDataset.QueryCase> queries = split.dataset().queries();
        for (int ordinal = 1; ordinal <= split.families().size(); ordinal++) {
            int offset = (ordinal - 1) * 2;
            assertEquals(split.families().get(ordinal - 1).familyId(),
                    queries.get(offset).familyId());
            assertEquals("zh", queries.get(offset).language());
            assertEquals("en", queries.get(offset + 1).language());
            assertEquals(queries.get(offset).query(),
                    queries.get(offset).retrievalQuery());
            assertEquals(queries.get(offset + 1).query(),
                    queries.get(offset + 1).retrievalQuery());
        }

        split.dataset().qrels().forEach((queryId, grades) -> {
            String familyId = queryId.substring(0, queryId.length() - 3);
            grades.forEach((segmentId, grade) -> {
                assertEquals(1, grade);
                assertEquals(familyId, split.dataset().corpusById()
                        .get(segmentId).metadata().get("familyId"));
            });
        });
    }

    private static void assertIdentifierExtraction(
            RagCandidate10FixtureGenerator.GeneratedSplit split) {
        Map<String, RagCandidate10FixtureGenerator.FamilySpec> families =
                split.families().stream().collect(Collectors.toMap(
                        RagCandidate10FixtureGenerator.FamilySpec::familyId,
                        Function.identity()));
        for (RagEvaluationDataset.QueryCase query : split.dataset().queries()) {
            RagCandidate10FixtureGenerator.FamilySpec family =
                    families.get(query.familyId());
            List<String> expected = switch (family.role().identifierCount()) {
                case 0 -> List.of();
                case 1 -> List.of(family.identifier1());
                case 2 -> List.of(family.identifier1(), family.identifier2());
                default -> throw new AssertionError("unexpected identifier count");
            };
            assertEquals(expected,
                    RagCandidate10DiagnosticSupport.identifierTerms(query.query()));
        }
    }

    private static void assertLongTokenBoundary(
            RagCandidate10FixtureGenerator.GeneratedSplit split) {
        RagCandidate10FixtureGenerator.FamilySpec family = split.families()
                .stream()
                .filter(value -> value.role()
                        == RagCandidate10FixtureGenerator.FamilyRole.LONG_TOKEN)
                .findFirst().orElseThrow();
        RagEvaluationDataset.CorpusSegment segment = split.dataset().corpusById()
                .values().stream()
                .filter(value -> family.familyId().equals(
                        value.metadata().get("familyId")))
                .filter(value -> "long-token-core".equals(
                        value.metadata().get("candidate10Role")))
                .filter(value -> ((Number) value.metadata().get("ordinal"))
                        .intValue() == 4 * (family.ordinal() - 1) + 1)
                .findFirst().orElseThrow();
        ColbertScorer scorer = new ColbertScorer(
                new ColbertScorer.ColbertConfig(), null);
        @SuppressWarnings("unchecked")
        List<String> tokens = ReflectionTestUtils.invokeMethod(
                scorer, "tokenize", segment.content());
        assertNotNull(tokens);
        assertEquals(160, tokens.size());
        assertEquals(family.marker2(), tokens.get(128));
        assertFalse(tokens.subList(0, 128).contains(family.marker2()));
    }

    private static void assertQrelPostRankingBoundary() {
        RagCandidate10FixtureGenerator.RankingFixture ranking =
                RagCandidate10FixtureGenerator.rankingView(
                        RagCandidate10FixtureGenerator.Split.SELECTION);
        assertEquals(Set.of("corpus", "queries", "pressure"),
                ranking.resources().keySet());
        assertTrue(ranking.dataset().qrels().isEmpty());
        RagCandidate10FixtureGenerator.QrelFixture qrels =
                RagCandidate10FixtureGenerator.qrels(
                        RagCandidate10FixtureGenerator.Split.SELECTION);
        assertEquals(46, qrels.pairCount());
        assertEquals("qrels.tsv", qrels.resource().fileName());
    }

    private static void assertCanonicalJsonOrdering() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("z", List.of(Map.of("b", 2, "a", 1)));
        nested.put("a", true);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("nested", nested);
        root.put("algorithm", "candidate10");

        byte[] bytes = RagCandidate10FreezeSupport.canonicalJsonBytes(root);
        assertEquals("{\"algorithm\":\"candidate10\",\"nested\":{"
                        + "\"a\":true,\"z\":[{\"a\":1,\"b\":2}]},"
                        + "\"version\":1}\n",
                new String(bytes, StandardCharsets.UTF_8));
        JSONObject parsed = JSON.parseObject(bytes);
        assertArrayEquals(bytes,
                RagCandidate10FreezeSupport.canonicalJsonBytes(parsed));
        Object verified = ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class,
                "parseCanonicalObject", bytes,
                "CANDIDATE10_SOURCE_LOCK_INVALID");
        assertNotNull(verified);
    }

    private static void assertCanonicalHashCorruptionFailsClosed(
            RagCandidate10FixtureGenerator.GeneratedSplit original) {
        Map<String, RagCandidate10FixtureGenerator.Resource> resources =
                new LinkedHashMap<>(original.resources());
        RagCandidate10FixtureGenerator.Resource corpus =
                resources.get("corpus");
        byte[] corruptedBytes = corpus.bytes();
        corruptedBytes[0] ^= 1;
        resources.put("corpus", new RagCandidate10FixtureGenerator.Resource(
                corpus.fileName(), corruptedBytes, corpus.sha256()));
        RagCandidate10FixtureGenerator.GeneratedSplit corrupted =
                new RagCandidate10FixtureGenerator.GeneratedSplit(
                        original.split(), original.seed(), original.dataset(),
                        original.families(), resources, original.counts(),
                        original.structure(), original.fixtureSpecHash(),
                        original.datasetHashPreimage(), original.datasetHash());
        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class,
                "manifest", corrupted, false));
    }

    private void assertSourceLockDriftFailsClosed() throws IOException {
        Path backend = temp.resolve("source-lock-backend");
        RagCandidate10FreezeSupport.RuntimePaths paths =
                RagCandidate10FreezeSupport.paths(backend);
        Files.createDirectories(paths.freezeDirectory());
        Files.createDirectories(backend);
        byte[] selectionManifest = "selection\n".getBytes(StandardCharsets.UTF_8);
        byte[] holdoutManifest = "holdout\n".getBytes(StandardCharsets.UTF_8);
        byte[] sourceLock = "source-lock\n".getBytes(StandardCharsets.UTF_8);
        Files.write(paths.selectionManifest(), selectionManifest);
        Files.write(paths.holdoutManifest(), holdoutManifest);
        Files.write(paths.sourceLock(), sourceLock);
        Path locked = backend.resolve("locked.txt");
        Files.writeString(locked, "locked\n", StandardCharsets.UTF_8);

        RagCandidate10FreezeSupport.DatasetManifest manifest = dummyManifest();
        RagCandidate10FreezeSupport.FrozenEvidence evidence =
                new RagCandidate10FreezeSupport.FrozenEvidence(
                        paths, manifest, manifest,
                        RagCandidate10FreezeSupport.sha256(selectionManifest),
                        RagCandidate10FreezeSupport.sha256(holdoutManifest),
                        ZERO_SHA, ONE_SHA, ZERO_SHA,
                        RagCandidate10FreezeSupport.sha256(sourceLock),
                        Map.of(),
                        Map.of("locked.txt",
                                RagCandidate10FreezeSupport.sha256(locked)),
                        Map.of(), sourceLock);
        RagCandidate10FreezeSupport.requireSourceLockUnchanged(evidence);
        Files.writeString(locked, "drifted\n", StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () ->
                RagCandidate10FreezeSupport.requireSourceLockUnchanged(evidence));
    }

    private static RagCandidate10FreezeSupport.DatasetManifest dummyManifest() {
        return new RagCandidate10FreezeSupport.DatasetManifest(
                "candidate10-selection", "FROZEN",
                RagCandidate10FixtureGenerator.GENERATOR,
                RagCandidate10FixtureGenerator.GENERATOR_VERSION,
                20260725L, Map.of(), Map.of(), Map.of(), ZERO_SHA, ONE_SHA);
    }

    private void assertPublicationGuards() throws Exception {
        Path base = temp.resolve("symlink-base");
        Path actual = temp.resolve("symlink-actual");
        Files.createDirectories(base);
        Files.createDirectories(actual);
        Path link = base.resolve("linked");
        Files.createSymbolicLink(link, actual);
        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class,
                "requireNoSymlink", base, link.resolve("child")));

        Path claim = temp.resolve("claim/entry.claim");
        Files.createDirectories(claim.getParent());
        ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class, "createClaim", claim);
        assertTrue(Files.isRegularFile(claim, LinkOption.NOFOLLOW_LINKS));
        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class, "createClaim", claim));

        Path publishParent = temp.resolve("atomic");
        Files.createDirectories(publishParent);
        Path staging = publishParent.resolve("staging");
        Path target = publishParent.resolve("target");
        Files.createDirectory(staging);
        Files.writeString(staging.resolve("evidence"), "first");
        ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class,
                "atomicPublishDirectory", staging, target);
        assertEquals("first", Files.readString(target.resolve("evidence")));
        Path second = publishParent.resolve("second-staging");
        Files.createDirectory(second);
        Files.writeString(second.resolve("evidence"), "second");
        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class,
                "atomicPublishDirectory", second, target));
        assertEquals("first", Files.readString(target.resolve("evidence")));
        assertTrue(Files.exists(second));

        Path partialBackend = temp.resolve("partial-backend");
        RagCandidate10FreezeSupport.RuntimePaths partial =
                RagCandidate10FreezeSupport.paths(partialBackend);
        Files.createDirectories(partial.selectionDirectory());
        Files.createDirectories(partial.incompleteMarker().getParent());
        Files.writeString(partial.selectionDirectory().resolve("published"),
                "must-remain");
        IllegalStateException marked = ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class,
                "markIncomplete", partial, "SELECTION_PUBLISHED",
                new IOException("sensitive failure detail"));
        assertNotNull(marked);
        assertTrue(Files.exists(
                partial.selectionDirectory().resolve("published")));
        JSONObject marker = JSON.parseObject(
                Files.readAllBytes(partial.incompleteMarker()));
        assertEquals("INVALID", marker.getString("status"));
        assertEquals("SELECTION_PUBLISHED", marker.getString("failedPhase"));
        assertFalse(marker.toJSONString().contains("sensitive"));
        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class,
                "requireFormalArtifactsAbsent", partial));
    }

    private void assertFormalHarnessClosureContracts() throws Exception {
        Path missingBackend = temp.resolve("harness-missing-runtime");
        Files.createDirectories(missingBackend.resolve("tests/target"));
        RagCandidate10FreezeSupport.RuntimePaths missing =
                RagCandidate10FreezeSupport.paths(missingBackend);
        Path runtime = missing.freezeDirectory().getParent();
        assertFalse(Files.exists(runtime, LinkOption.NOFOLLOW_LINKS));
        Path created = ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class,
                "ensureRuntimeRoot", missing);
        assertEquals(runtime.toAbsolutePath().normalize(), created);
        assertTrue(Files.isDirectory(runtime, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.isSymbolicLink(runtime));
        try (var entries = Files.list(runtime)) {
            assertEquals(0L, entries.count());
        }
        Path existingEntry = runtime.resolve("existing-runtime-entry");
        Files.writeString(existingEntry, "preserve");
        assertEquals(created, ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class,
                "ensureRuntimeRoot", missing));
        assertEquals("preserve", Files.readString(existingEntry));

        Path runtimeFileBackend = temp.resolve("harness-runtime-file");
        Files.createDirectories(runtimeFileBackend.resolve("tests/target"));
        RagCandidate10FreezeSupport.RuntimePaths runtimeFile =
                RagCandidate10FreezeSupport.paths(runtimeFileBackend);
        Files.writeString(runtimeFile.freezeDirectory().getParent(), "not-a-dir");
        assertFreezePathInvalid(runtimeFile);

        Path runtimeLinkBackend = temp.resolve("harness-runtime-link");
        Files.createDirectories(runtimeLinkBackend.resolve("tests/target"));
        Path runtimeLinkTarget = temp.resolve("harness-runtime-link-target");
        Files.createDirectory(runtimeLinkTarget);
        RagCandidate10FreezeSupport.RuntimePaths runtimeLink =
                RagCandidate10FreezeSupport.paths(runtimeLinkBackend);
        Files.createSymbolicLink(
                runtimeLink.freezeDirectory().getParent(), runtimeLinkTarget);
        assertFreezePathInvalid(runtimeLink);

        Path missingParentBackend = temp.resolve("harness-parent-missing");
        Files.createDirectories(missingParentBackend.resolve("tests"));
        RagCandidate10FreezeSupport.RuntimePaths missingParent =
                RagCandidate10FreezeSupport.paths(missingParentBackend);
        assertFreezePathInvalid(missingParent);
        assertFalse(Files.exists(missingParent.freezeDirectory().getParent(),
                LinkOption.NOFOLLOW_LINKS));

        Path parentFileBackend = temp.resolve("harness-parent-file");
        Files.createDirectories(parentFileBackend.resolve("tests"));
        Files.writeString(parentFileBackend.resolve("tests/target"), "not-a-dir");
        assertFreezePathInvalid(RagCandidate10FreezeSupport.paths(
                parentFileBackend));

        Path parentLinkBackend = temp.resolve("harness-parent-link");
        Files.createDirectories(parentLinkBackend.resolve("tests"));
        Path parentLinkTarget = temp.resolve("harness-parent-link-target");
        Files.createDirectory(parentLinkTarget);
        Files.createSymbolicLink(
                parentLinkBackend.resolve("tests/target"), parentLinkTarget);
        assertFreezePathInvalid(RagCandidate10FreezeSupport.paths(
                parentLinkBackend));

        Path reportBackend = temp.resolve("harness-old-report");
        RagCandidate10FreezeSupport.RuntimePaths reports =
                RagCandidate10FreezeSupport.paths(reportBackend);
        Files.createDirectories(reports.freezeReport().getParent());
        byte[] oldXml = "old-freeze-xml\n".getBytes(StandardCharsets.UTF_8);
        byte[] oldTxt = "old-freeze-txt\n".getBytes(StandardCharsets.UTF_8);
        Path oldTxtPath = reports.freezeReport().resolveSibling(
                "tech.qiantong.qknow.rag.eval."
                        + "RagCandidate10FormalEvidenceTest-candidate10-freeze.txt");
        Files.write(reports.freezeReport(), oldXml);
        Files.write(oldTxtPath, oldTxt);
        ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class,
                "requireFormalArtifactsAbsent", reports);
        assertArrayEquals(oldXml, Files.readAllBytes(reports.freezeReport()));
        assertArrayEquals(oldTxt, Files.readAllBytes(oldTxtPath));

        List<Function<RagCandidate10FreezeSupport.RuntimePaths, Path>> blocked =
                List.of(
                        RagCandidate10FreezeSupport.RuntimePaths::selectionDirectory,
                        RagCandidate10FreezeSupport.RuntimePaths::holdoutDirectory,
                        RagCandidate10FreezeSupport.RuntimePaths::freezeDirectory,
                        RagCandidate10FreezeSupport.RuntimePaths::sourceLock,
                        RagCandidate10FreezeSupport.RuntimePaths::ledger,
                        RagCandidate10FreezeSupport.RuntimePaths::diagnostic,
                        RagCandidate10FreezeSupport.RuntimePaths::incompleteMarker,
                        RagCandidate10FreezeSupport.RuntimePaths::selectionStaging,
                        RagCandidate10FreezeSupport.RuntimePaths::selectionClaim,
                        RagCandidate10FreezeSupport.RuntimePaths::holdoutStaging,
                        RagCandidate10FreezeSupport.RuntimePaths::holdoutClaim,
                        RagCandidate10FreezeSupport.RuntimePaths::freezeStaging,
                        RagCandidate10FreezeSupport.RuntimePaths::freezeClaim,
                        RagCandidate10FreezeSupport.RuntimePaths::unexpectedHoldoutDirectory,
                        RagCandidate10FreezeSupport.RuntimePaths::unexpectedSelectionDirectory,
                        RagCandidate10FreezeSupport.RuntimePaths::diagnosticReport);
        for (int index = 0; index < blocked.size(); index++) {
            Path blockedBackend = temp.resolve("harness-blocked-" + index);
            RagCandidate10FreezeSupport.RuntimePaths blockedPaths =
                    RagCandidate10FreezeSupport.paths(blockedBackend);
            Path occupied = blocked.get(index).apply(blockedPaths);
            Files.createDirectories(occupied.getParent());
            Files.writeString(occupied, "occupied");
            assertThrows(RuntimeException.class, () ->
                    ReflectionTestUtils.invokeMethod(
                            RagCandidate10FreezeSupport.class,
                            "requireFormalArtifactsAbsent", blockedPaths));
        }
    }

    private static void assertFreezePathInvalid(
            RagCandidate10FreezeSupport.RuntimePaths paths) {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(
                        RagCandidate10FreezeSupport.class,
                        "ensureRuntimeRoot", paths));
        assertEquals("CANDIDATE10_FREEZE_PATH_INVALID", failure.getMessage());
    }

    private void assertSurefireReportParserContracts() throws Exception {
        Path backend = temp.resolve("surefire-parser");
        List<String> reportFiles = List.of(
                "RagCandidate10ThirdStageContractTest-candidate10-third-stage.xml",
                "RagCandidate10NonDockerContractTest-candidate10-support.xml",
                "RagCandidate10CounterfactualContractTest-"
                        + "candidate10-counterfactual.xml");
        List<String> classNames = List.of(
                "RagCandidate10ThirdStageContractTest",
                "RagCandidate10NonDockerContractTest",
                "RagCandidate10CounterfactualContractTest");
        List<String> methodNames = List.of(
                "allContracts", "allContracts",
                "boundedAdmissionReplacesOnlyTail");
        List<String> suffixes = List.of(
                "candidate10-third-stage", "candidate10-support",
                "candidate10-counterfactual");
        Path reports = backend.resolve("tests/target/surefire-reports");
        Files.createDirectories(reports);
        for (int index = 0; index < reportFiles.size(); index++) {
            Files.writeString(
                    reports.resolve("TEST-tech.qiantong.qknow.rag.eval."
                            + reportFiles.get(index)),
                    surefireXml(classNames.get(index), methodNames.get(index),
                            suffixes.get(index)));
        }

        Map<String, String> hashes = ReflectionTestUtils.invokeMethod(
                RagCandidate10FreezeSupport.class,
                "verifyPreflightReports", backend);
        assertNotNull(hashes);
        assertEquals(3, hashes.size());
        hashes.forEach((file, sha) -> assertEquals(
                RagCandidate10FreezeSupport.sha256(backend.resolve(file)), sha));

        Files.writeString(
                reports.resolve("TEST-tech.qiantong.qknow.rag.eval."
                        + reportFiles.get(0)),
                surefireXml(classNames.get(0), methodNames.get(0),
                        "wrong-suffix"));
        assertThrows(IllegalStateException.class, () ->
                ReflectionTestUtils.invokeMethod(
                        RagCandidate10FreezeSupport.class,
                        "verifyPreflightReports", backend));

        Files.writeString(
                reports.resolve("TEST-tech.qiantong.qknow.rag.eval."
                        + reportFiles.get(0)),
                "<?xml version=\"1.0\"?>\n"
                        + "<!DOCTYPE testsuite [<!ENTITY xxe SYSTEM "
                        + "\"file:///etc/passwd\">]>\n"
                        + surefireXml(classNames.get(0), methodNames.get(0),
                                suffixes.get(0)));
        assertThrows(IllegalStateException.class, () ->
                ReflectionTestUtils.invokeMethod(
                        RagCandidate10FreezeSupport.class,
                        "verifyPreflightReports", backend));
    }

    private static String surefireXml(
            String className, String methodName, String reportNameSuffix) {
        String qualifiedName = "tech.qiantong.qknow.rag.eval." + className
                + "(" + reportNameSuffix + ")";
        return "<testsuite name=\"" + qualifiedName
                + "\" tests=\"1\" failures=\"0\" errors=\"0\" "
                + "skipped=\"0\"><testcase classname=\"" + qualifiedName
                + "\" name=\"" + methodName
                + "\"/></testsuite>\n";
    }

    private static void assertDiagnosticContracts() {
        usingLinearStateMachine();
        RagCandidate10DiagnosticStageSupport.DecisionEvidence proceed =
                new RagCandidate10DiagnosticStageSupport.DecisionEvidence(
                        true, true, true, true, true, true, true);
        assertEquals(RagCandidate10DiagnosticStageSupport.PROCEED_DECISION,
                RagCandidate10DiagnosticStageSupport.decide(proceed));
        assertEquals(RagCandidate10DiagnosticStageSupport.STOP_DECISION,
                RagCandidate10DiagnosticStageSupport.decide(
                        new RagCandidate10DiagnosticStageSupport.DecisionEvidence(
                                true, true, true, true, true, false, true)));

        Map<String, Object> valid = new LinkedHashMap<>();
        valid.put("schemaVersion", "candidate10-diagnostic-v1");
        valid.put("causalScope", RagCandidate10DiagnosticStageSupport.CAUSAL_SCOPE);
        valid.put("validity", "VALID");
        valid.put("metrics", Map.of("baselineApAt10", 0.0D));
        valid.put("decision", RagCandidate10DiagnosticStageSupport.STOP_DECISION);
        valid.put("errorCode", null);
        RagCandidate10DiagnosticStageSupport.validateSanitizedArtifact(valid);

        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("schemaVersion", "candidate10-diagnostic-v1");
        invalid.put("causalScope", RagCandidate10DiagnosticStageSupport.CAUSAL_SCOPE);
        invalid.put("validity", "INVALID");
        invalid.put("failedPhase", "RUNNING");
        invalid.put("decision", null);
        invalid.put("errorCode", RagCandidate10DiagnosticStageSupport.RUNTIME_ERROR);
        RagCandidate10DiagnosticStageSupport.validateSanitizedArtifact(invalid);

        Map<String, Object> missingDecision = new LinkedHashMap<>(invalid);
        missingDecision.remove("decision");
        assertThrows(IllegalStateException.class, () ->
                RagCandidate10DiagnosticStageSupport
                        .validateSanitizedArtifact(missingDecision));
        Map<String, Object> nonNullDecision = new LinkedHashMap<>(invalid);
        nonNullDecision.put("decision", RagCandidate10DiagnosticStageSupport.STOP_DECISION);
        assertThrows(IllegalStateException.class, () ->
                RagCandidate10DiagnosticStageSupport
                        .validateSanitizedArtifact(nonNullDecision));
        for (Object invalidError : Arrays.asList(null, "", "UNKNOWN_ERROR")) {
            Map<String, Object> invalidErrorCode = new LinkedHashMap<>(invalid);
            invalidErrorCode.put("errorCode", invalidError);
            assertThrows(IllegalStateException.class, () ->
                    RagCandidate10DiagnosticStageSupport
                            .validateSanitizedArtifact(invalidErrorCode));
        }
        invalid.put("metrics", Map.of());
        assertThrows(IllegalStateException.class, () ->
                RagCandidate10DiagnosticStageSupport
                        .validateSanitizedArtifact(invalid));
        invalid.remove("metrics");
        invalid.put("nested", List.of(Map.of("query", "forbidden")));
        assertThrows(IllegalStateException.class, () ->
                RagCandidate10DiagnosticStageSupport
                        .validateSanitizedArtifact(invalid));
    }

    private void assertEarlySourceLockInvalidCompletes() throws IOException {
        Path backend = temp.resolve("early-source-lock-invalid");
        RagCandidate10FreezeSupport.RuntimePaths paths =
                RagCandidate10FreezeSupport.paths(backend);
        Files.createDirectories(paths.freezeDirectory());
        Files.writeString(paths.sourceLock(), "{}\n", StandardCharsets.UTF_8);

        RagCandidate10DiagnosticStageSupport.DiagnosticResult result =
                RagCandidate10DiagnosticStageSupport
                        .runSelectionDiagnostic(paths);

        assertFalse(result.valid());
        assertNull(result.decision());
        assertEquals(RagCandidate10DiagnosticStageSupport.SOURCE_LOCK_ERROR,
                result.errorCode());
        assertTrue(Files.isRegularFile(result.artifact(),
                LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.isRegularFile(result.ledger(),
                LinkOption.NOFOLLOW_LINKS));
        assertEquals(result.artifactSha256(),
                RagCandidate10FreezeSupport.sha256(result.artifact()));
        assertEquals(result.ledgerSha256(),
                RagCandidate10FreezeSupport.sha256(result.ledger()));

        JSONObject artifact = JSON.parseObject(
                Files.readAllBytes(result.artifact()));
        assertEquals("INVALID", artifact.getString("validity"));
        assertTrue(artifact.containsKey("decision"));
        assertNull(artifact.get("decision"));
        assertEquals(result.errorCode(), artifact.getString("errorCode"));

        JSONObject ledger = JSON.parseObject(
                Files.readAllBytes(result.ledger()));
        assertEquals("COMPLETED", ledger.getString("status"));
        assertEquals("INVALID", ledger.getString("validity"));
        assertTrue(ledger.containsKey("decision"));
        assertNull(ledger.get("decision"));
        assertEquals(result.errorCode(), ledger.getString("errorCode"));
        assertEquals(result.artifactSha256(),
                ledger.getString("diagnosticSha256"));
        assertEquals(result.artifactData(), new LinkedHashMap<>(artifact));
    }

    private static void usingLinearStateMachine() {
        RagCandidate10DiagnosticStageSupport.Phase running =
                RagCandidate10DiagnosticStageSupport.Phase.RUNNING;
        RagCandidate10DiagnosticStageSupport.Phase ranked =
                RagCandidate10DiagnosticStageSupport.Phase.RANKING_FROZEN;
        RagCandidate10DiagnosticStageSupport.Phase qrels =
                RagCandidate10DiagnosticStageSupport.Phase.QRELS_LOADED;
        RagCandidate10DiagnosticStageSupport.Phase completed =
                RagCandidate10DiagnosticStageSupport.Phase.COMPLETED;
        assertTrue(RagCandidate10DiagnosticStageSupport.validTransition(
                running, ranked));
        assertTrue(RagCandidate10DiagnosticStageSupport.validTransition(
                ranked, qrels));
        assertTrue(RagCandidate10DiagnosticStageSupport.validTransition(
                qrels, completed));
        assertTrue(RagCandidate10DiagnosticStageSupport.validTransition(
                running, completed));
        assertFalse(RagCandidate10DiagnosticStageSupport.validTransition(
                running, qrels));
        assertFalse(RagCandidate10DiagnosticStageSupport.validTransition(
                completed, running));
    }

    private static void assertFallbackIsObservableAndRejected() {
        ColbertScorer.ColbertConfig config = new ColbertScorer.ColbertConfig();
        config.setEnabled(true);
        ColbertScorer failing = new ColbertScorer(config, null) {
            @Override
            public List<Document> rerank(
                    String query, List<Document> documents, int topK) {
                throw new IllegalStateException("expected contract failure");
            }
        };
        RagRerankService service = new RagRerankService();
        ReflectionTestUtils.setField(service, "colbertScorer", failing);
        List<RetrievalResult> originals = new ArrayList<>(List.of(
                result(1L), result(2L)));
        Map<String, Object> fallback;
        try (RagFallbackMonitor.Scope ignored = RagFallbackMonitor.openScope()) {
            @SuppressWarnings("unchecked")
            List<RetrievalResult> returned = ReflectionTestUtils.invokeMethod(
                    service, "colbertCoarseRerank",
                    "document 7600000", originals, originals.size());
            assertNotNull(returned);
            assertEquals(List.of(1L, 2L), returned.stream()
                    .map(RetrievalResult::getSegmentId).toList());
            fallback = RagFallbackMonitor.currentScopeSnapshot();
            assertFalse(fallback.isEmpty());
        }
        assertNull(RagFallbackMonitor.currentScope());
        Map<String, Object> capturedFallback = fallback;
        assertThrows(IllegalStateException.class, () ->
                RagCandidate10DiagnosticStageSupport
                        .requireNoScopedFallback(capturedFallback));
        RagCandidate10DiagnosticStageSupport.requireNoScopedFallback(Map.of());

        Document malformed = Document.builder()
                .text("content")
                .metadata(Map.of("segmentId", 1L,
                        "colbert_score", Double.NaN))
                .build();
        assertThrows(IllegalStateException.class, () ->
                RagCandidate10DiagnosticSupport.snapshotFullRanking(
                        List.of(result(1L)), List.of(malformed)));
    }

    private static RetrievalResult result(long segmentId) {
        return RetrievalResult.builder()
                .segmentId(segmentId)
                .documentId(100L + segmentId)
                .documentName("DOC-7600000-EVIDENCE.txt")
                .content("document 7600000 evidence")
                .score(0.0D)
                .source("candidate10-contract")
                .metadata(new LinkedHashMap<>())
                .build();
    }
}
