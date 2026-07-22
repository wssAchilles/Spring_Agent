package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCandidate91SourceLockTest {

    @Test
    @EnabledIfSystemProperty(
            named = RagCandidate91RecoverySupport.SOURCE_LOCK_PROPERTY,
            matches = "true")
    void publishSourceLock() {
        RagCandidate91RecoverySupport.requireSourceLockProperty();
        Path backend = RagCandidate91RecoverySupportTest.backendRoot();
        Path runtime = backend.resolve("tests/target/rag-eval");
        RagCandidate91RecoverySupport.RecoveryPaths paths =
                RagCandidate91RecoverySupport.paths(runtime);
        assertFalse(Files.exists(paths.root()),
                "Recovery namespace must be absent before source lock");
        RagCandidate91RecoverySupport.SourceLock lock =
                RagCandidate91RecoverySupport.publishSourceLockStaging(
                        backend, RagCandidate91RecoverySupport.SOURCE_LOCK_STAGING);
        assertTrue(Files.isRegularFile(lock.path()));
        assertTrue(lock.fileCount() >= 10);
        assertFalse(Files.exists(paths.root()),
                "Source lock must not write the Recovery namespace");
    }
}
