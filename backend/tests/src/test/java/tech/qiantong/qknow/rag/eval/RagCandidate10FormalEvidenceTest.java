package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCandidate10FormalEvidenceTest {

    @Test
    @EnabledIfSystemProperty(
            named = RagCandidate10FreezeSupport.FREEZE_PROPERTY,
            matches = "true")
    void freezeFixtureAndSourceLock() {
        RagCandidate10FreezeSupport.RuntimePaths paths =
                RagCandidate10FreezeSupport.formalPaths();
        RagCandidate10FreezeSupport.ConfiguredRuntime runtime =
                RagCandidate10FreezeSupport.configureRuntime(
                        RagCandidate10FreezeSupport.EntryPoint.FREEZE);
        RagCandidate10FreezeSupport.FreezePlan plan =
                RagCandidate10FreezeSupport.prepareFreeze(
                        paths,
                        RagCandidate10FixtureGenerator.selection(),
                        RagCandidate10FixtureGenerator.holdout(),
                        runtime);

        RagCandidate10FreezeSupport.FrozenEvidence published =
                RagCandidate10FreezeSupport.publishFreeze(plan);
        RagCandidate10FreezeSupport.FrozenEvidence verified =
                RagCandidate10FreezeSupport.verifyPublishedFreeze(plan);

        assertEquals(plan.sourceLockSha256(), published.sourceLockSha256());
        assertEquals(published.sourceLockSha256(), verified.sourceLockSha256());
        assertEquals(plan.configHash(), verified.configHash());
        assertEquals(plan.selectionManifest().datasetHash(),
                verified.selectionDatasetHash());
        assertEquals(plan.holdoutManifest().datasetHash(),
                verified.holdoutDatasetHash());
        assertTrue(Files.isRegularFile(paths.sourceLock()));
        assertFalse(Files.exists(paths.incompleteMarker()));
        assertFalse(Files.exists(paths.ledger()));
        assertFalse(Files.exists(paths.diagnostic()));
    }

    @Test
    @EnabledIfSystemProperty(
            named = RagCandidate10FreezeSupport.DIAGNOSTIC_PROPERTY,
            matches = "true")
    void runSelectionDiagnostic() throws IOException {
        RagCandidate10DiagnosticStageSupport.DiagnosticResult result =
                RagCandidate10DiagnosticStageSupport
                        .runFormalSelectionDiagnostic();

        assertNotNull(result);
        assertTrue(Files.isRegularFile(result.artifact()));
        assertTrue(Files.isRegularFile(result.ledger()));
        assertEquals(64, result.artifactSha256().length());
        assertEquals(64, result.ledgerSha256().length());
        RagCandidate10DiagnosticStageSupport.validateSanitizedArtifact(
                result.artifactData());
        assertEquals(result.valid() ? "VALID" : "INVALID",
                result.artifactData().get("validity"));
        assertTrue(result.artifactData().containsKey("decision"));
        assertEquals(result.decision(), result.artifactData().get("decision"));
        assertEquals(result.errorCode(), result.artifactData().get("errorCode"));

        JSONObject artifact = JSON.parseObject(
                Files.readAllBytes(result.artifact()));
        JSONObject ledger = JSON.parseObject(
                Files.readAllBytes(result.ledger()));
        assertEquals(result.artifactSha256(),
                ledger.getString("diagnosticSha256"));
        assertEquals("COMPLETED", ledger.getString("status"));
        assertEquals(result.valid() ? "VALID" : "INVALID",
                ledger.getString("validity"));
        assertTrue(artifact.containsKey("decision"));
        assertTrue(ledger.containsKey("decision"));
        assertEquals(result.decision(), artifact.get("decision"));
        assertEquals(result.decision(), ledger.get("decision"));
        assertEquals(result.errorCode(), artifact.get("errorCode"));
        assertEquals(result.errorCode(), ledger.get("errorCode"));
        if (result.valid()) {
            assertNull(result.errorCode());
            assertTrue(Set.of(
                    RagCandidate10DiagnosticStageSupport.PROCEED_DECISION,
                    RagCandidate10DiagnosticStageSupport.STOP_DECISION)
                    .contains(result.decision()));
        } else {
            assertNotNull(result.errorCode());
            assertNull(result.decision());
        }
    }
}
