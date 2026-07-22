package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RagCandidate91NonDockerContractTest {

    @TempDir
    Path temp;

    @Test
    void allContracts() throws Exception {
        RagCandidate91RecoverySupport.RecoveryPaths paths =
                RagCandidate91RecoverySupport.paths(temp.resolve("runtime"));
        RagCandidate91RecoverySupport.validateNamespace(paths,
                RagCandidate91RecoverySupport.RecoveryPhase.EMPTY);
        assertArrayEquals(
                "{\"state\":\"ARCHIVE_PUBLISHED\",\"version\":1}"
                        .getBytes(StandardCharsets.UTF_8),
                RagCandidate91RecoverySupport.ARCHIVE_MARKER_BYTES);
        assertEquals("candidate91_preflight",
                RagCandidate91RecoverySupport.PREFLIGHT_DATABASE);
        RagCandidate91RecoverySupport.lockedLegacyBinding().validate();
        RagCandidate91RecoverySupport.requireCurrentConfigHash(
                RagCandidate91RecoverySupport.lockedLegacyBinding().configHash());
        assertThrows(IllegalStateException.class, () ->
                RagCandidate91RecoverySupport.requireCurrentConfigHash(
                        "0".repeat(64)));

        Files.createDirectories(paths.root());
        Files.writeString(paths.root().resolve("unexpected.tmp"), "partial");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RagCandidate91RecoverySupport.validateNamespace(paths,
                        RagCandidate91RecoverySupport.RecoveryPhase.EMPTY));
        assertEquals("INVALID_INCOMPLETE_RECOVERY", failure.getMessage());

        RagCandidate91RecoverySupportTest recoveryContracts =
                new RagCandidate91RecoverySupportTest();
        recoveryContracts.temp = temp.resolve("recovery-contracts");
        Files.createDirectories(recoveryContracts.temp);
        recoveryContracts
                .sourceLockAndArchiveAreCreateOnlyAndDoNotReadQrelsOrHoldout();
        recoveryContracts.preflightUsesFixedDatabaseAndBindsTheSameContainer();
        recoveryContracts.preflightFailureIsCreateOnlyAndNeverCreatesLedger();
        recoveryContracts.recoveryLedgerTransitionsAndSanitizedCompletionAreAtomic();

        RagCandidate91RecoverySupportTest driftContracts =
                new RagCandidate91RecoverySupportTest();
        driftContracts.temp = temp.resolve("drift-contracts");
        Files.createDirectories(driftContracts.temp);
        driftContracts.archiveAndOriginalEvidenceDriftFailBeforeNextPhase();

        RagCandidate9DiagnosticSupportTest detachedContracts =
                new RagCandidate9DiagnosticSupportTest();
        detachedContracts.temp = temp.resolve("detached-qrel-contracts");
        Files.createDirectories(detachedContracts.temp);
        detachedContracts
                .detachedRecoveryBindingLoadsQrelsAndFinalizesWithoutLegacyLedger();

        RagCandidate9DiagnosticSupportTest completedLegacyContracts =
                new RagCandidate9DiagnosticSupportTest();
        completedLegacyContracts.temp = temp.resolve("completed-legacy-contracts");
        Files.createDirectories(completedLegacyContracts.temp);
        completedLegacyContracts
                .detachedRecoveryBypassesCompletedLegacyLifecycleWithoutMutatingIt();
    }
}
