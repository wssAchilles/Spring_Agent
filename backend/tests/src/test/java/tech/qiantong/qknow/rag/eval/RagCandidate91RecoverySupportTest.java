package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCandidate91RecoverySupportTest {

    @TempDir
    Path temp;

    static RagCandidate91RecoverySupport.RecoveryRunHandle newRecoveryRun(
            Path root) throws Exception {
        RagCandidate91RecoverySupportTest support =
                new RagCandidate91RecoverySupportTest();
        support.temp = root;
        Files.createDirectories(root);
        RecoveryFixture fixture = support.archivedFixture(
                root.resolve("recovery-state"));
        return RagCandidate91RecoverySupport.beginRecovery(
                support.mockedPreflight(fixture.archive()));
    }

    @Test
    void freezesPathsMarkerPropertiesAndLegacyBinding() {
        RagCandidate91RecoverySupport.RecoveryPaths paths =
                RagCandidate91RecoverySupport.paths(temp.resolve("runtime"));
        assertEquals("candidate9-recovery", paths.root().getFileName().toString());
        assertEquals("archive-complete.marker",
                paths.archiveMarker().getFileName().toString());
        assertArrayEquals(
                "{\"state\":\"ARCHIVE_PUBLISHED\",\"version\":1}"
                        .getBytes(StandardCharsets.UTF_8),
                RagCandidate91RecoverySupport.ARCHIVE_MARKER_BYTES);
        RagCandidate91RecoverySupport.lockedLegacyBinding().validate();

        withProperty(RagCandidate91RecoverySupport.PROBE_PROPERTY, "true", () -> {
            RagCandidate91RecoverySupport.requireProbeProperty();
            assertThrows(IllegalStateException.class,
                    RagCandidate91RecoverySupport::requireRecoveryProperties);
        });
    }

    @Test
    void sourceLockAndArchiveAreCreateOnlyAndDoNotReadQrelsOrHoldout()
            throws Exception {
        Path backend = backendRoot();
        Path runtime = temp.resolve("runtime");
        copyLegacyEvidence(runtime, backend);
        Path staging = temp.resolve("candidate91-source-lock.json");
        RagCandidate91RecoverySupport.SourceLock sourceLock =
                RagCandidate91RecoverySupport.publishSourceLockStaging(
                        backend, staging);
        assertTrue(sourceLock.fileCount() >= 10);
        assertThrows(IllegalStateException.class, () ->
                RagCandidate91RecoverySupport.publishSourceLockStaging(
                        backend, staging));

        RagCandidate91RecoverySupport.RecoveryPaths paths =
                RagCandidate91RecoverySupport.paths(runtime);
        RagCandidate91RecoverySupport.ArchiveHandle archive =
                RagCandidate91RecoverySupport.publishArchive(
                        paths, backend, staging,
                        RagCandidate91RecoverySupport.lockedLegacyBinding());
        archive.requireLive();
        assertArrayEquals(sourceLock.bytes(), Files.readAllBytes(paths.sourceLock()));
        assertArrayEquals(RagCandidate91RecoverySupport.ARCHIVE_MARKER_BYTES,
                Files.readAllBytes(paths.archiveMarker()));

        JSONObject manifest = JSON.parseObject(Files.readString(
                paths.archiveManifest(), StandardCharsets.UTF_8));
        assertEquals(3, manifest.getIntValue("selectionResourceAccessCount"));
        assertEquals(0,
                manifest.getIntValue("selectionQrelResourceAccessCount"));
        assertEquals(0, manifest.getIntValue("holdoutResourceAccessCount"));
        assertFalse(manifest.toJSONString().contains("candidate9-holdout/"));
        assertThrows(IllegalStateException.class, () ->
                RagCandidate91RecoverySupport.publishArchive(
                        paths, backend, staging,
                        RagCandidate91RecoverySupport.lockedLegacyBinding()));
    }

    @Test
    void namespaceRejectsUnknownTemporaryAndSymlinkEntries() throws Exception {
        RagCandidate91RecoverySupport.RecoveryPaths paths =
                RagCandidate91RecoverySupport.paths(temp.resolve("runtime"));
        Files.createDirectories(paths.root());
        assertIncomplete(paths);

        Files.writeString(paths.root().resolve("unknown.json"), "{}");
        assertIncomplete(paths);

        Files.delete(paths.root().resolve("unknown.json"));
        Files.writeString(paths.root().resolve("orphan.tmp"), "x");
        assertIncomplete(paths);

        Files.delete(paths.root().resolve("orphan.tmp"));
        try {
            Files.createSymbolicLink(paths.root().resolve("archive"), temp);
            assertIncomplete(paths);
        } catch (UnsupportedOperationException ignored) {
            // Platform does not support symbolic links; other namespace cases apply.
        }
    }

    @Test
    void preflightUsesFixedDatabaseAndBindsTheSameContainer() throws Exception {
        RecoveryFixture fixture = archivedFixture();
        Object container = new Object();
        RecordingEndpoint endpoint = new RecordingEndpoint(container);

        RagCandidate91RecoverySupport.PreflightHandle preflight =
                RagCandidate91RecoverySupport.preflight(
                        fixture.archive(), endpoint,
                        new RagCandidate91RecoverySupport
                                .DockerInfrastructureCounters(3, 1));
        assertEquals(8, preflight.preflightDbCalls());
        assertEquals("candidate91_preflight", endpoint.database);
        RagCandidate91RecoverySupport.requireSameContainer(
                preflight, container, "container-identity", true);
        assertThrows(IllegalStateException.class, () ->
                RagCandidate91RecoverySupport.requireSameContainer(
                        preflight, new Object(), "container-identity", true));
        assertEquals(1, endpoint.executeCalls);
        assertThrows(IllegalStateException.class, () ->
                RagCandidate91RecoverySupport.preflight(
                        fixture.archive(), endpoint,
                        new RagCandidate91RecoverySupport
                                .DockerInfrastructureCounters(3, 1)));
        assertEquals(1, endpoint.executeCalls,
                "stale namespace must fail before database operations");

        RecoveryFixture badVersionFixture = archivedFixture(
                temp.resolve("bad-version"));
        RecordingEndpoint badVersion = new RecordingEndpoint(new Object()) {
            @Override
            public RagCandidate91RecoverySupport.PreflightObservation execute(
                    String preflightDatabase) {
                return new RagCandidate91RecoverySupport.PreflightObservation(
                        8, "15.8", "UTF8", "C", "C", 421L);
            }
        };
        assertThrows(IllegalStateException.class, () ->
                RagCandidate91RecoverySupport.preflight(
                        badVersionFixture.archive(), badVersion,
                        new RagCandidate91RecoverySupport
                                .DockerInfrastructureCounters(1, 0)));
    }

    @Test
    void preflightFailureIsCreateOnlyAndNeverCreatesLedger() throws Exception {
        RecoveryFixture fixture = archivedFixture();
        RagCandidate91RecoverySupport.publishPreflightFailure(
                fixture.archive(), "CANDIDATE91_DOCKER_PREFLIGHT_FAILED");
        assertTrue(Files.isRegularFile(fixture.paths().preflightFailure()));
        assertFalse(Files.exists(fixture.paths().dockerPreflight()));
        assertFalse(Files.exists(fixture.paths().recoveryLedger()));
        assertThrows(IllegalStateException.class, () ->
                RagCandidate91RecoverySupport.publishPreflightFailure(
                        fixture.archive(),
                        "CANDIDATE91_DOCKER_PREFLIGHT_FAILED"));
    }

    @Test
    void recoveryLedgerTransitionsAndSanitizedCompletionAreAtomic()
            throws Exception {
        RecoveryFixture fixture = archivedFixture();
        RagCandidate91RecoverySupport.PreflightHandle preflight =
                mockedPreflight(fixture.archive());
        RagCandidate91RecoverySupport.RecoveryRunHandle run =
                RagCandidate91RecoverySupport.beginRecovery(preflight);
        assertEquals(RagCandidate91RecoverySupport.RecoveryPhase.RUNNING,
                run.phase());
        String rankingSha = "1".repeat(64);
        publishPhase(run,
                RagCandidate91RecoverySupport.RecoveryPhase.RUNNING,
                RagCandidate91RecoverySupport.RecoveryPhase.RANKING_FROZEN,
                rankingSha);
        publishPhase(run,
                RagCandidate91RecoverySupport.RecoveryPhase.RANKING_FROZEN,
                RagCandidate91RecoverySupport.RecoveryPhase.QRELS_LOADED,
                rankingSha);

        Files.writeString(fixture.paths().recoveryLedger(), "tampered");
        assertThrows(IllegalStateException.class, () ->
                RagCandidate91RecoverySupport.publishRuntimeInvalidAndComplete(
                        run, "CANDIDATE9_RECOVERY_RUNTIME_INVALID"));
        assertFalse(Files.exists(fixture.paths().recoveryDiagnostic()));

        RecoveryFixture second = archivedFixture(temp.resolve("second"));
        RagCandidate91RecoverySupport.RecoveryRunHandle invalidRun =
                RagCandidate91RecoverySupport.beginRecovery(
                        mockedPreflight(second.archive()));
        publishPhase(invalidRun,
                RagCandidate91RecoverySupport.RecoveryPhase.RUNNING,
                RagCandidate91RecoverySupport.RecoveryPhase.RANKING_FROZEN,
                rankingSha);
        publishPhase(invalidRun,
                RagCandidate91RecoverySupport.RecoveryPhase.RANKING_FROZEN,
                RagCandidate91RecoverySupport.RecoveryPhase.QRELS_LOADED,
                rankingSha);
        assertThrows(IllegalArgumentException.class, () ->
                RagCandidate91RecoverySupport.publishRuntimeInvalidAndComplete(
                        invalidRun, "CANDIDATE91_BAD"));
        RagCandidate91RecoverySupport.publishRuntimeInvalidAndComplete(
                invalidRun, "CANDIDATE9_RECOVERY_RUNTIME_INVALID");
        JSONObject invalidLedger = JSON.parseObject(Files.readString(
                second.paths().recoveryLedger()));
        assertEquals(rankingSha,
                invalidLedger.getString("rankingPhaseSha256"));
        assertEquals("COMPLETED", invalidLedger.getString("phase"));

        RecoveryFixture third = archivedFixture(temp.resolve("third"));
        RagCandidate91RecoverySupport.RecoveryRunHandle runningFailure =
                RagCandidate91RecoverySupport.beginRecovery(
                        mockedPreflight(third.archive()));
        RagCandidate91RecoverySupport.publishRuntimeInvalidAndComplete(
                runningFailure, "CANDIDATE9_RECOVERY_RUNTIME_INVALID");
        assertEquals(RagCandidate91RecoverySupport.RecoveryPhase.COMPLETED,
                runningFailure.phase());

        RecoveryFixture sensitive = archivedFixture(temp.resolve("sensitive"));
        RagCandidate91RecoverySupport.RecoveryRunHandle sensitiveRun =
                RagCandidate91RecoverySupport.beginRecovery(
                        mockedPreflight(sensitive.archive()));
        Map<String, Object> forbidden = new LinkedHashMap<>();
        forbidden.put("exception_message", "hidden");
        assertThrows(IllegalArgumentException.class, () ->
                ReflectionTestUtils.invokeMethod(
                        RagCandidate91RecoverySupport.class,
                        "requireSanitized", forbidden));
        Object[] nested = {Map.of("document-name", "hidden")};
        assertThrows(IllegalArgumentException.class, () ->
                ReflectionTestUtils.invokeMethod(
                        RagCandidate91RecoverySupport.class,
                        "requireSanitized", (Object) nested));
    }

    @Test
    void archiveAndOriginalEvidenceDriftFailBeforeNextPhase() throws Exception {
        RecoveryFixture archiveDrift = archivedFixture(temp.resolve("archive-drift"));
        Files.writeString(archiveDrift.paths().legacyDiagnostic(), "changed");
        assertThrows(IllegalStateException.class,
                archiveDrift.archive()::requireLive);

        RecoveryFixture originalDrift = archivedFixture(temp.resolve("original-drift"));
        Path originalDiagnostic = originalDrift.paths().runtime()
                .resolve("candidate9-calibration-diagnostic.json");
        Files.writeString(originalDiagnostic, "changed");
        assertThrows(IllegalStateException.class,
                originalDrift.archive()::requireLive);
    }

    private RagCandidate91RecoverySupport.PreflightHandle mockedPreflight(
            RagCandidate91RecoverySupport.ArchiveHandle archive) throws Exception {
        return RagCandidate91RecoverySupport.preflight(archive,
                new RecordingEndpoint(new Object()),
                new RagCandidate91RecoverySupport.DockerInfrastructureCounters(1, 0));
    }

    private static void publishPhase(
            RagCandidate91RecoverySupport.RecoveryRunHandle handle,
            RagCandidate91RecoverySupport.RecoveryPhase expected,
            RagCandidate91RecoverySupport.RecoveryPhase next,
            String rankingSha) {
        ReflectionTestUtils.invokeMethod(
                RagCandidate91RecoverySupport.class, "publishPhase",
                handle, expected, next, rankingSha);
    }

    private RecoveryFixture archivedFixture() throws Exception {
        return archivedFixture(temp.resolve("fixture-" + System.nanoTime()));
    }

    private RecoveryFixture archivedFixture(Path root) throws Exception {
        Path backend = backendRoot();
        Path runtime = root.resolve("runtime");
        Files.createDirectories(root);
        copyLegacyEvidence(runtime, backend);
        Path staging = root.resolve("source-lock.json");
        RagCandidate91RecoverySupport.publishSourceLockStaging(backend, staging);
        RagCandidate91RecoverySupport.RecoveryPaths paths =
                RagCandidate91RecoverySupport.paths(runtime);
        RagCandidate91RecoverySupport.ArchiveHandle archive =
                RagCandidate91RecoverySupport.publishArchive(
                        paths, backend, staging,
                        RagCandidate91RecoverySupport.lockedLegacyBinding());
        return new RecoveryFixture(paths, archive);
    }

    private static void copyLegacyEvidence(Path runtime, Path backend)
            throws Exception {
        Path actual = backend.resolve("tests/target/rag-eval");
        Files.createDirectories(runtime.resolve("candidate9-freeze"));
        Files.copy(actual.resolve("candidate9-freeze/selection-manifest.json"),
                runtime.resolve("candidate9-freeze/selection-manifest.json"));
        Files.copy(actual.resolve("candidate9-freeze/holdout-manifest.json"),
                runtime.resolve("candidate9-freeze/holdout-manifest.json"));
        Files.copy(actual.resolve("candidate9-freeze/selection-ledger.json"),
                runtime.resolve("candidate9-freeze/selection-ledger.json"));
        Files.copy(actual.resolve("candidate9-calibration-diagnostic.json"),
                runtime.resolve("candidate9-calibration-diagnostic.json"));
    }

    static Path backendRoot() {
        Path current = Path.of(System.getProperty("user.dir", "."))
                .toAbsolutePath().normalize();
        if ("tests".equals(String.valueOf(current.getFileName()))) {
            return current.getParent();
        }
        if (Files.isDirectory(current.resolve("tests"))) {
            return current;
        }
        return current.resolve("backend");
    }

    private static void withProperty(String key, String value, Runnable action) {
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, value);
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    private static void assertIncomplete(
            RagCandidate91RecoverySupport.RecoveryPaths paths) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RagCandidate91RecoverySupport.validateNamespace(paths,
                        RagCandidate91RecoverySupport.RecoveryPhase.EMPTY));
        assertEquals("INVALID_INCOMPLETE_RECOVERY", failure.getMessage());
    }

    private record RecoveryFixture(
            RagCandidate91RecoverySupport.RecoveryPaths paths,
            RagCandidate91RecoverySupport.ArchiveHandle archive) {
    }

    private static class RecordingEndpoint
            implements RagCandidate91RecoverySupport.PreflightEndpoint {
        private final Object token;
        private String database;
        private int executeCalls;

        private RecordingEndpoint(Object token) {
            this.token = token;
        }

        @Override
        public Object identityToken() {
            return token;
        }

        @Override
        public String containerIdentity() {
            return "container-identity";
        }

        @Override
        public boolean running() {
            return true;
        }

        @Override
        public String databaseName() {
            return "shadow";
        }

        @Override
        public RagCandidate91RecoverySupport.PreflightObservation execute(
                String preflightDatabase) {
            executeCalls++;
            database = preflightDatabase;
            return new RagCandidate91RecoverySupport.PreflightObservation(
                    8, "16.4", "UTF8", "C", "C", 421L);
        }
    }
}
