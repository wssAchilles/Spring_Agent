package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.ai.service.IEmbeddingService;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Versioned recovery contracts.  The class deliberately owns the recovery
 * namespace so the existing FreezeSupport and formal evidence entry points
 * remain untouched.
 */
class RagCandidate10DiagnosticRecoveryV2Test {

    private static final String MODE_PROPERTY =
            "rag.eval.candidate10.recovery-v2";
    private static final String MODE_CONTRACTS = "contracts";
    private static final String MODE_REVIEW = "review";
    private static final String MODE_PUBLISH = "publish";
    private static final String MODE_DIAGNOSTIC = "diagnostic";

    private static final String OLD_STAGE_SHA =
            "b70daf2a7b5d3162a2ad0157aa735a5d2e4b66b61301a9a828c67c5db75e5765";
    private static final String ORIGINAL_SOURCE_LOCK_SHA =
            "2faa4a7847c9c9adb54212d8158b1ebe36d3d1ccf1a86ae516e0dc2ac3440d66";
    private static final String ORIGINAL_DIAGNOSTIC_SHA =
            "844ba8b24cc28527d5b80ad7964549797db61e02e69ec1100fc0260e94ed4533";
    private static final String ORIGINAL_LEDGER_SHA =
            "f789dcea0a1b2609824ac2b1614b29f82546631eb678e4ec056c0690ab3d0431";
    private static final String SELECTION_MANIFEST_SHA =
            "2dcd028732aac132c98497e95958926fbd788d1c993033d25249bccbd181d32e";
    private static final String HOLDOUT_MANIFEST_SHA =
            "798e81b6845f2e6034d78affeb85f436b721feb745b5e6f4e8721a8c6ee4b334";
    private static final String CONFIG_HASH =
            "55a65f3ce58588587f6650cee44fc1c70e57007b0519b7d9853937a86566af15";
    private static final String SELECTION_DATASET_HASH =
            "d7799d2b5900104661cbc25a653772bbeb61c3999e2225b3ce88873df1998024";
    private static final String ATTRIBUTION_SOURCE_SHA =
            "890411497b17f23234a7e5b0ac62bc8b458dca48fb13138dd1e0eee6e551f671";
    private static final String ATTRIBUTION_XML_SHA =
            "6ee12b872a8825e7f96876950c374ce59fe9118810c42d0f604a32de4708a9ee";
    private static final String ATTRIBUTION_TXT_SHA =
            "9d86f523719d9efa80b5529263d64dd051af5cac2dad5090caf750d72e12ccf3";

    private static final String ALGORITHM =
            "bounded-exact-window-tail-admission-v1";
    private static final String CAUSAL_SCOPE =
            "post-fusion-content-only-colbert-snapshot";
    private static final String RECOVERY_SCHEMA =
            "candidate10-diagnostic-recovery-v2";
    private static final String RECOVERY_ERROR =
            "CANDIDATE10_FREEZE_INCOMPLETE";
    private static final String RECOVERY_INVALID =
            "CANDIDATE10_RECOVERY_INVALID";

    private static final Path BACKEND = Path.of(
            "/Users/achilles/Documents/许子祺/Agent/backend")
            .toAbsolutePath().normalize();
    private static final Path TESTS = BACKEND.resolve("tests");
    private static final Path SELECTION = TESTS.resolve(
            "src/test/resources/rag-eval/candidate10-selection");
    private static final Path HOLDOUT = TESTS.resolve("candidate10-holdout");
    private static final Path RUNTIME = TESTS.resolve("target/rag-eval");
    private static final Path ORIGINAL_FREEZE = RUNTIME.resolve(
            "candidate10-freeze");
    private static final Path RECOVERY = RUNTIME.resolve(
            "candidate10-diagnostic-recovery-v2");
    private static final Path STAGING = RUNTIME.resolve(
            "candidate10-diagnostic-recovery-v2.staging");
    private static final Path CLAIM = RUNTIME.resolve(
            "candidate10-diagnostic-recovery-v2.claim");
    private static final Path INCOMPLETE = RUNTIME.resolve(
            "candidate10-diagnostic-recovery-v2.incomplete.json");
    private static final Path UNEXPECTED_HOLDOUT = RUNTIME.resolve(
            "candidate10-diagnostic-recovery-v2.unexpected-holdout");
    private static final Path UNEXPECTED_SELECTION = RUNTIME.resolve(
            "candidate10-diagnostic-recovery-v2.unexpected-selection");
    private static final Path ORIGINAL_UNEXPECTED_HOLDOUT = TESTS.resolve(
            "src/test/resources/rag-eval/candidate10-holdout");
    private static final Path ORIGINAL_UNEXPECTED_SELECTION = TESTS.resolve(
            "candidate10-selection");
    private static final Path REPORTS = TESTS.resolve("target/surefire-reports");
    private static final Path ORIGINAL_SOURCE_LOCK = ORIGINAL_FREEZE.resolve(
            "candidate10-source-lock.json");
    private static final Path ORIGINAL_LEDGER = ORIGINAL_FREEZE.resolve(
            "selection-ledger.json");
    private static final Path ORIGINAL_DIAGNOSTIC = RUNTIME.resolve(
            "candidate10-calibration-diagnostic.json");
    private static final Path SELECTION_MANIFEST = ORIGINAL_FREEZE.resolve(
            "selection-manifest.json");
    private static final Path HOLDOUT_MANIFEST = ORIGINAL_FREEZE.resolve(
            "holdout-manifest.json");
    private static final Path STAGE_SOURCE = BACKEND.resolve(
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10DiagnosticStageSupport.java");
    private static final Path RECOVERY_SOURCE = BACKEND.resolve(
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10DiagnosticRecoveryV2Test.java");
    private static final Path ATTRIBUTION_SOURCE = BACKEND.resolve(
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10RankingFailureAttributionTest.java");
    private static final Path ATTRIBUTION_XML = REPORTS.resolve(
            "TEST-tech.qiantong.qknow.rag.eval."
                    + "RagCandidate10RankingFailureAttributionTest-"
                    + "candidate10-ranking-attribution-attempt-001.xml");
    private static final Path ATTRIBUTION_TXT = REPORTS.resolve(
            "tech.qiantong.qknow.rag.eval."
                    + "RagCandidate10RankingFailureAttributionTest-"
                    + "candidate10-ranking-attribution-attempt-001.txt");
    private static final Path PUBLISH_REPORT = REPORTS.resolve(
            "TEST-tech.qiantong.qknow.rag.eval."
                    + "RagCandidate10DiagnosticRecoveryV2Test-"
                    + "candidate10-diagnostic-recovery-v2-source-lock-publish.xml");
    private static final Path DIAGNOSTIC_SELECTOR_REPORT = REPORTS.resolve(
            "TEST-tech.qiantong.qknow.rag.eval."
                    + "RagCandidate10DiagnosticRecoveryV2Test-"
                    + "candidate10-diagnostic-recovery-v2.xml");

    private static final Set<String> SOURCE_LOCK_KEYS = Set.of(
            "algorithm", "candidate10ProductionProperty", "causalScope",
            "config", "configHash", "files", "historicalArtifactLocalState",
            "historicalEvidence", "historicalEvidenceMode", "holdout", "jdk",
            "preflightReports", "selection", "version");
    private static final Set<String> BINDING_KEYS = Set.of("file", "sha256");
    private static final Set<String> SPLIT_KEYS = Set.of(
            "datasetHash", "fixtureSpecHash", "manifestSha256", "resources");
    private static final Set<String> JDK_KEYS = Set.of(
            "arch", "archiveSha256", "javaHome", "javaSha256", "javaVersion",
            "javacSha256", "libmanagementSha256", "releaseSha256",
            "runtimeVersion", "signingTeamIdentifier", "vendor");
    private static final Set<String> RECOVERY_KEYS = Set.of(
            "schemaVersion", "algorithm", "causalScope", "rootCauseClass",
            "algorithmConclusion", "originalEvidence", "sourceDelta",
            "attribution", "recoveryHarness", "compatibleSourceLock", "jdk",
            "configHash", "selectionDatasetHash", "selectionManifestSha256",
            "holdoutManifestSha256");
    private static final Set<String> ORIGINAL_EVIDENCE_KEYS = Set.of(
            "sourceLock", "diagnostic", "ledger");
    private static final Set<String> ATTRIBUTION_KEYS = Set.of(
            "status", "attempt", "queryOrdinal", "arm", "failureStage",
            "errorCode", "source", "reports");
    private static final Set<String> REPORT_KEYS = Set.of(
            "format", "file", "sha256");
    private static final Set<String> INCOMPLETE_KEYS = Set.of(
            "version", "status", "failedPhase", "errorCode");
    private static final Set<String> FAILED_PHASES = Set.of(
            "CLAIMING", "STAGING", "STAGED_VALIDATION", "PUBLISHING",
            "PUBLISHED_VERIFICATION", "CLAIM_RELEASE", "PUBLISHED_VALIDATION");

    @TempDir
    Path temp;

    @Test
    void recoveryContracts() throws Exception {
        requireMode(MODE_CONTRACTS);
        requireFormalCommand();
        requireFormalNamespaceAbsent();
        Map<String, String> before = snapshotAllowedEvidence();
        assertDocumentCaptureContract();
        assertRuntimePathContract();
        assertIncompleteContract();
        byte[] sourceLock = compatibleSourceLockBytes();
        byte[] envelope = recoveryEnvelopeBytes(sourceLock);
        validateCompatibleSourceLock(sourceLock);
        validateRecoveryEnvelope(envelope, sourceLock);
        assertEnvelopeRejections(envelope, sourceLock);
        assertOriginalEvidenceUnchanged(before);
    }

    @Test
    void reviewSourceLockExactDelta() throws Exception {
        requireMode(MODE_REVIEW);
        requireFormalCommand();
        requireFormalNamespaceAbsent();
        Map<String, String> before = snapshotAllowedEvidence();
        byte[] original = readRegular(ORIGINAL_SOURCE_LOCK);
        byte[] compatible = compatibleSourceLockBytes();
        byte[] envelope = recoveryEnvelopeBytes(compatible);
        validateCompatibleSourceLock(compatible);
        validateRecoveryEnvelope(envelope, compatible);
        assertArrayEquals(original, restoredOriginalSourceLock(compatible));
        assertNotEquals(OLD_STAGE_SHA, RagCandidate10FreezeSupport.sha256(
                STAGE_SOURCE));

        Path reviewRoot = temp.resolve("source-lock-review");
        Files.createDirectory(reviewRoot);
        Path reviewSourceLock = reviewRoot.resolve("candidate10-source-lock.json");
        writeCreateNew(reviewSourceLock, compatible);
        RagCandidate10FreezeSupport.RuntimePaths paths =
                temporaryStagedPaths(reviewRoot);
        RagCandidate10FreezeSupport.FrozenEvidence frozen =
                RagCandidate10FreezeSupport.openDiagnosticEvidence(paths);
        RagCandidate10FreezeSupport.requireSourceLockUnchanged(frozen);
        writeCreateNew(reviewRoot.resolve("recovery-manifest.json"), envelope);
        requireExactDirectory(reviewRoot,
                Set.of("candidate10-source-lock.json", "recovery-manifest.json"));
        assertOriginalEvidenceUnchanged(before);
    }

    @Test
    void publishRecoverySourceLock() throws Exception {
        requireMode(MODE_PUBLISH);
        requireFormalCommand();
        requireFormalNamespaceAbsent();
        Map<String, String> before = snapshotAllowedEvidence();
        byte[] compatible = compatibleSourceLockBytes();
        byte[] envelope = recoveryEnvelopeBytes(compatible);
        validateCompatibleSourceLock(compatible);
        validateRecoveryEnvelope(envelope, compatible);
        AtomicBoolean mutated = new AtomicBoolean();
        String failedPhase = "CLAIMING";
        try {
            requireRecoveryNamespaceAbsent();
            createClaim(CLAIM, mutated);
            requireRegular(CLAIM);

            failedPhase = "STAGING";
            Files.createDirectory(STAGING);
            writeCreateNew(STAGING.resolve("candidate10-source-lock.json"),
                    compatible);
            writeCreateNew(STAGING.resolve("recovery-manifest.json"), envelope);
            requireExactDirectory(STAGING,
                    Set.of("candidate10-source-lock.json", "recovery-manifest.json"));

            failedPhase = "STAGED_VALIDATION";
            RagCandidate10FreezeSupport.RuntimePaths staged =
                    stagedValidationPaths();
            RagCandidate10FreezeSupport.FrozenEvidence frozen =
                    RagCandidate10FreezeSupport.openDiagnosticEvidence(staged);
            RagCandidate10FreezeSupport.requireSourceLockUnchanged(frozen);

            failedPhase = "PUBLISHING";
            requireAbsent(RECOVERY);
            Files.move(STAGING, RECOVERY, StandardCopyOption.ATOMIC_MOVE);

            failedPhase = "PUBLISHED_VERIFICATION";
            requireExactDirectory(RECOVERY,
                    Set.of("candidate10-source-lock.json", "recovery-manifest.json"));
            assertArrayEquals(compatible,
                    readRegular(RECOVERY.resolve("candidate10-source-lock.json")));
            assertArrayEquals(envelope,
                    readRegular(RECOVERY.resolve("recovery-manifest.json")));
            assertPublishedPathsRejectClaim();

            failedPhase = "CLAIM_RELEASE";
            requireRegular(CLAIM);
            Files.delete(CLAIM);

            failedPhase = "PUBLISHED_VALIDATION";
            RagCandidate10FreezeSupport.RuntimePaths published =
                    publishedRuntimePaths();
            RagCandidate10FreezeSupport.FrozenEvidence publishedEvidence =
                    RagCandidate10FreezeSupport.openDiagnosticEvidence(published);
            RagCandidate10FreezeSupport.requireSourceLockUnchanged(
                    publishedEvidence);
            validateRecoveryEnvelope(
                    readRegular(RECOVERY.resolve("recovery-manifest.json")),
                    readRegular(RECOVERY.resolve("candidate10-source-lock.json")));
            assertOriginalEvidenceUnchanged(before);
            requireExactDirectory(RECOVERY,
                    Set.of("candidate10-source-lock.json", "recovery-manifest.json"));
            requireAbsent(STAGING);
            requireAbsent(CLAIM);
            requireAbsent(INCOMPLETE);
        } catch (Throwable failure) {
            if (mutated.get()) {
                try {
                    writeIncomplete(failedPhase);
                } catch (Throwable markerFailure) {
                    failure.addSuppressed(markerFailure);
                }
            }
            rethrow(failure);
        }
    }

    @Test
    void runVersionedSelectionDiagnostic() throws Exception {
        requireMode(MODE_DIAGNOSTIC);
        requireFormalCommand();
        requireRecoveryNamespacePublished();
        Map<String, String> before = snapshotAllowedEvidence();
        byte[] sourceLock = readRegular(RECOVERY.resolve(
                "candidate10-source-lock.json"));
        byte[] envelope = readRegular(RECOVERY.resolve("recovery-manifest.json"));
        validateCompatibleSourceLock(sourceLock);
        validateRecoveryEnvelope(envelope, sourceLock);
        assertOriginalEvidenceUnchanged(before);

        RagCandidate10FreezeSupport.RuntimePaths paths =
                publishedRuntimePaths();
        RagCandidate10FreezeSupport.FrozenEvidence frozen =
                RagCandidate10FreezeSupport.openDiagnosticEvidence(paths);
        RagCandidate10FreezeSupport.requireSourceLockUnchanged(frozen);
        requireExactDirectory(RECOVERY,
                Set.of("candidate10-source-lock.json", "recovery-manifest.json"));

        RagCandidate10DiagnosticStageSupport.DiagnosticResult result =
                RagCandidate10DiagnosticStageSupport
                        .runVersionedSelectionDiagnostic(paths);
        assertNotNull(result);
        verifyDiagnosticResult(result, sourceLock, envelope, before);
        assertOriginalEvidenceUnchanged(before);
        requireAbsent(STAGING);
        requireAbsent(CLAIM);
        requireAbsent(INCOMPLETE);
        requireAbsent(UNEXPECTED_HOLDOUT);
        requireAbsent(UNEXPECTED_SELECTION);
        requireExactDirectory(RECOVERY, Set.of(
                "candidate10-source-lock.json", "recovery-manifest.json",
                "selection-ledger.json", "candidate10-diagnostic.json"));
    }

    private void assertDocumentCaptureContract() throws Exception {
        RetrievalResult original = RetrievalResult.builder()
                .segmentId(9001L)
                .qmSegmentId("qm-9001")
                .parentSegmentId("parent-9001")
                .documentId(7001L)
                .documentName("recovery-contract")
                .content("candidate ten recovery contract document")
                .answer("answer")
                .score(0.75D)
                .source("source")
                .metadata(new LinkedHashMap<>(Map.of(
                        "segmentId", 9001L,
                        "score", 0.75D,
                        "source", "source")))
                .build();
        Document document = new Document(original.getContent());
        document.getMetadata().put("segmentId", original.getSegmentId());
        document.getMetadata().put("score", original.getScore());
        document.getMetadata().put("source", original.getSource());

        ColbertScorer.ColbertConfig config = new ColbertScorer.ColbertConfig();
        config.setEnabled(true);
        config.setNgramSize(3);
        config.setDimensions(64);
        config.setMaxTokensPerDoc(128);
        Class<?> captureClass = Class.forName(
                RagCandidate10DiagnosticStageSupport.class.getName()
                        + "$CapturingColbertScorer");
        Constructor<?> constructor = captureClass.getDeclaredConstructor(
                ColbertScorer.ColbertConfig.class, IEmbeddingService.class);
        constructor.setAccessible(true);
        Object capturing = constructor.newInstance(config, null);
        var rerank = captureClass.getDeclaredMethod(
                "rerank", String.class, List.class, int.class);
        rerank.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Document> returned = (List<Document>) rerank.invoke(
                capturing, "recovery query", List.of(document), 1);
        var field = captureClass.getDeclaredField("rerankedDocuments");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Document> captured = (List<Document>) field.get(capturing);
        assertEquals(returned.size(), captured.size());
        for (int index = 0; index < returned.size(); index++) {
            assertSame(returned.get(index), captured.get(index));
        }
        assertEquals(new ArrayList<>(returned.get(0).getMetadata().keySet()),
                new ArrayList<>(captured.get(0).getMetadata().keySet()));

        Document legacy = new Document(document.getId(), document.getText(),
                new LinkedHashMap<>(document.getMetadata()));
        assertEquals(document.getId(), legacy.getId());
        assertEquals(document.getText(), legacy.getText());
        assertEquals(new LinkedHashMap<>(document.getMetadata()),
                new LinkedHashMap<>(legacy.getMetadata()));
        assertNotEquals(new ArrayList<>(document.getMetadata().keySet()),
                new ArrayList<>(legacy.getMetadata().keySet()));

        RagRerankService service = new RagRerankService();
        Map<Long, RetrievalResult> originals = Map.of(original.getSegmentId(), original);
        RetrievalResult preserved = ReflectionTestUtils.invokeMethod(
                service, "toRetrievalResult", captured.get(0), originals);
        RetrievalResult rebuilt = ReflectionTestUtils.invokeMethod(
                service, "toRetrievalResult", legacy, originals);
        assertNotNull(preserved);
        assertNotNull(rebuilt);
        assertResultFieldsEqual(preserved, rebuilt);
        assertEquals(new LinkedHashMap<>(preserved.getMetadata()),
                new LinkedHashMap<>(rebuilt.getMetadata()));
        assertNotEquals(new ArrayList<>(preserved.getMetadata().keySet()),
                new ArrayList<>(rebuilt.getMetadata().keySet()));
        assertResultScalarsEqual(original, preserved);
        for (Map.Entry<String, Object> entry : original.getMetadata().entrySet()) {
            assertEquals(entry.getValue(), preserved.getMetadata().get(entry.getKey()));
        }
        assertTrue(preserved.getMetadata().get("colbert_score") instanceof Number);
        assertTrue(Double.isFinite(((Number) preserved.getMetadata()
                .get("colbert_score")).doubleValue()));
    }

    private static void assertResultFieldsEqual(
            RetrievalResult left, RetrievalResult right) {
        assertResultScalarsEqual(left, right);
        assertEquals(left.getMetadata(), right.getMetadata());
    }

    private static void assertResultScalarsEqual(
            RetrievalResult left, RetrievalResult right) {
        assertEquals(left.getSegmentId(), right.getSegmentId());
        assertEquals(left.getQmSegmentId(), right.getQmSegmentId());
        assertEquals(left.getParentSegmentId(), right.getParentSegmentId());
        assertEquals(left.getDocumentId(), right.getDocumentId());
        assertEquals(left.getDocumentName(), right.getDocumentName());
        assertEquals(left.getContent(), right.getContent());
        assertEquals(left.getAnswer(), right.getAnswer());
        assertEquals(left.getScore(), right.getScore());
        assertEquals(left.getSource(), right.getSource());
    }

    private void assertRuntimePathContract() throws Exception {
        RagCandidate10FreezeSupport.RuntimePaths staged =
                stagedValidationPaths();
        RagCandidate10FreezeSupport.RuntimePaths published =
                publishedRuntimePaths();
        for (RagCandidate10FreezeSupport.RuntimePaths paths
                : List.of(staged, published)) {
            assertEquals(BACKEND, paths.backendRoot());
            assertEquals(TESTS, paths.testsDirectory());
            assertEquals(SELECTION, paths.selectionDirectory());
            assertEquals(HOLDOUT, paths.holdoutDirectory());
            assertEquals(SELECTION_MANIFEST, paths.selectionManifest());
            assertEquals(HOLDOUT_MANIFEST, paths.holdoutManifest());
            assertEquals(INCOMPLETE, paths.incompleteMarker());
            assertEquals(UNEXPECTED_HOLDOUT,
                    paths.unexpectedHoldoutDirectory());
            assertEquals(UNEXPECTED_SELECTION,
                    paths.unexpectedSelectionDirectory());
            assertEquals(PUBLISH_REPORT, paths.freezeReport());
            assertEquals(DIAGNOSTIC_SELECTOR_REPORT,
                    paths.diagnosticReport());
        }
        assertEquals(STAGING, staged.freezeDirectory());
        assertEquals(STAGING.resolve("candidate10-source-lock.json"),
                staged.sourceLock());
        assertEquals(STAGING.resolve("selection-ledger.json"), staged.ledger());
        assertEquals(STAGING.resolve("candidate10-diagnostic.json"),
                staged.diagnostic());
        assertEquals(STAGING.resolve(".validation-selection.staging"),
                staged.selectionStaging());
        assertEquals(STAGING.resolve(".validation-selection.claim"),
                staged.selectionClaim());
        assertEquals(STAGING.resolve(".validation-holdout.staging"),
                staged.holdoutStaging());
        assertEquals(STAGING.resolve(".validation-holdout.claim"),
                staged.holdoutClaim());
        assertEquals(STAGING.resolve(".validation-freeze.staging"),
                staged.freezeStaging());
        assertEquals(STAGING.resolve(".validation-freeze.claim"),
                staged.freezeClaim());
        assertEquals(RECOVERY, published.freezeDirectory());
        assertEquals(RECOVERY.resolve("candidate10-source-lock.json"),
                published.sourceLock());
        assertEquals(RECOVERY.resolve("selection-ledger.json"), published.ledger());
        assertEquals(RECOVERY.resolve("candidate10-diagnostic.json"),
                published.diagnostic());
        assertEquals(RUNTIME.resolve(
                "candidate10-diagnostic-recovery-v2.selection.staging"),
                published.selectionStaging());
        assertEquals(RUNTIME.resolve(
                "candidate10-diagnostic-recovery-v2.selection.claim"),
                published.selectionClaim());
        assertEquals(RUNTIME.resolve(
                "candidate10-diagnostic-recovery-v2.holdout.staging"),
                published.holdoutStaging());
        assertEquals(RUNTIME.resolve(
                "candidate10-diagnostic-recovery-v2.holdout.claim"),
                published.holdoutClaim());
        assertEquals(STAGING, published.freezeStaging());
        assertEquals(CLAIM, published.freezeClaim());

        byte[] sourceLock = compatibleSourceLockBytes();
        Path stagedTemp = temp.resolve("staged-runtime");
        Files.createDirectory(stagedTemp);
        writeCreateNew(stagedTemp.resolve("candidate10-source-lock.json"),
                sourceLock);
        writeCreateNew(stagedTemp.resolve("recovery-manifest.json"),
                recoveryEnvelopeBytes(sourceLock));
        RagCandidate10FreezeSupport.RuntimePaths stagedTempPaths =
                temporaryStagedPaths(stagedTemp);
        RagCandidate10FreezeSupport.openDiagnosticEvidence(stagedTempPaths);
        Files.createFile(stagedTempPaths.selectionClaim());
        assertThrows(IllegalStateException.class, () ->
                RagCandidate10FreezeSupport.openDiagnosticEvidence(stagedTempPaths));
        Files.delete(stagedTempPaths.selectionClaim());

        Path publishedTemp = temp.resolve("published-runtime");
        Files.createDirectory(publishedTemp);
        writeCreateNew(publishedTemp.resolve("candidate10-source-lock.json"),
                sourceLock);
        writeCreateNew(publishedTemp.resolve("recovery-manifest.json"),
                recoveryEnvelopeBytes(sourceLock));
        Path residualStaging = temp.resolve("published-residual.staging");
        Path residualClaim = temp.resolve("published-residual.claim");
        Files.createFile(residualClaim);
        RagCandidate10FreezeSupport.RuntimePaths publishedTempPaths =
                temporaryPublishedPaths(publishedTemp, residualStaging, residualClaim);
        assertThrows(IllegalStateException.class, () ->
                RagCandidate10FreezeSupport.openDiagnosticEvidence(publishedTempPaths));
        Files.delete(residualClaim);
        RagCandidate10FreezeSupport.openDiagnosticEvidence(publishedTempPaths);
    }

    private void assertIncompleteContract() throws Exception {
        Path claim = temp.resolve("claim");
        AtomicBoolean mutated = new AtomicBoolean();
        createClaim(claim, mutated);
        assertTrue(mutated.get());
        assertEquals(0L, Files.size(claim));
        assertThrows(Exception.class,
                () -> createClaim(claim, new AtomicBoolean()));

        Path marker = temp.resolve("incomplete.json");
        writeIncompleteAt(marker, "STAGING");
        byte[] bytes = readRegular(marker);
        validateIncomplete(bytes);
        assertThrows(Exception.class, () -> writeIncompleteAt(marker, "CLAIMING"));
        JSONObject extra = JSON.parseObject(bytes);
        extra.put("message", "forbidden");
        assertThrows(IllegalStateException.class,
                () -> validateIncomplete(RagCandidate10FreezeSupport
                        .canonicalJsonBytes(extra)));
        JSONObject fractionalVersion = JSON.parseObject(bytes);
        fractionalVersion.put("version", 1.5D);
        assertThrows(IllegalStateException.class,
                () -> validateIncomplete(RagCandidate10FreezeSupport
                        .canonicalJsonBytes(fractionalVersion)));
    }

    private static RagCandidate10FreezeSupport.RuntimePaths
    stagedValidationPaths() {
        return runtimePaths(
                STAGING,
                STAGING.resolve("candidate10-source-lock.json"),
                STAGING.resolve("selection-ledger.json"),
                STAGING.resolve("candidate10-diagnostic.json"),
                INCOMPLETE,
                STAGING.resolve(".validation-selection.staging"),
                STAGING.resolve(".validation-selection.claim"),
                STAGING.resolve(".validation-holdout.staging"),
                STAGING.resolve(".validation-holdout.claim"),
                STAGING.resolve(".validation-freeze.staging"),
                STAGING.resolve(".validation-freeze.claim"),
                UNEXPECTED_HOLDOUT,
                UNEXPECTED_SELECTION,
                PUBLISH_REPORT,
                DIAGNOSTIC_SELECTOR_REPORT);
    }

    private static RagCandidate10FreezeSupport.RuntimePaths
    publishedRuntimePaths() {
        return runtimePaths(
                RECOVERY,
                RECOVERY.resolve("candidate10-source-lock.json"),
                RECOVERY.resolve("selection-ledger.json"),
                RECOVERY.resolve("candidate10-diagnostic.json"),
                INCOMPLETE,
                RUNTIME.resolve("candidate10-diagnostic-recovery-v2.selection.staging"),
                RUNTIME.resolve("candidate10-diagnostic-recovery-v2.selection.claim"),
                RUNTIME.resolve("candidate10-diagnostic-recovery-v2.holdout.staging"),
                RUNTIME.resolve("candidate10-diagnostic-recovery-v2.holdout.claim"),
                STAGING,
                CLAIM,
                UNEXPECTED_HOLDOUT,
                UNEXPECTED_SELECTION,
                PUBLISH_REPORT,
                DIAGNOSTIC_SELECTOR_REPORT);
    }

    private static RagCandidate10FreezeSupport.RuntimePaths
    temporaryStagedPaths(Path root) {
        return runtimePaths(
                root,
                root.resolve("candidate10-source-lock.json"),
                root.resolve("selection-ledger.json"),
                root.resolve("candidate10-diagnostic.json"),
                root.resolveSibling("temporary-staged.incomplete.json"),
                root.resolve(".validation-selection.staging"),
                root.resolve(".validation-selection.claim"),
                root.resolve(".validation-holdout.staging"),
                root.resolve(".validation-holdout.claim"),
                root.resolve(".validation-freeze.staging"),
                root.resolve(".validation-freeze.claim"),
                root.resolveSibling("temporary.unexpected-holdout"),
                root.resolveSibling("temporary.unexpected-selection"),
                root.resolveSibling("temporary-freeze.xml"),
                root.resolveSibling("temporary-diagnostic.xml"));
    }

    private static RagCandidate10FreezeSupport.RuntimePaths
    temporaryPublishedPaths(Path root, Path freezeStaging, Path freezeClaim) {
        return runtimePaths(
                root,
                root.resolve("candidate10-source-lock.json"),
                root.resolve("selection-ledger.json"),
                root.resolve("candidate10-diagnostic.json"),
                root.resolveSibling("temporary-published.incomplete.json"),
                root.resolveSibling("temporary-selection.staging"),
                root.resolveSibling("temporary-selection.claim"),
                root.resolveSibling("temporary-holdout.staging"),
                root.resolveSibling("temporary-holdout.claim"),
                freezeStaging,
                freezeClaim,
                root.resolveSibling("temporary.unexpected-holdout"),
                root.resolveSibling("temporary.unexpected-selection"),
                root.resolveSibling("temporary-freeze.xml"),
                root.resolveSibling("temporary-diagnostic.xml"));
    }

    private static RagCandidate10FreezeSupport.RuntimePaths runtimePaths(
            Path freezeDirectory,
            Path sourceLock,
            Path ledger,
            Path diagnostic,
            Path incompleteMarker,
            Path selectionStaging,
            Path selectionClaim,
            Path holdoutStaging,
            Path holdoutClaim,
            Path freezeStaging,
            Path freezeClaim,
            Path unexpectedHoldout,
            Path unexpectedSelection,
            Path freezeReport,
            Path diagnosticReport) {
        return new RagCandidate10FreezeSupport.RuntimePaths(
                BACKEND, TESTS, SELECTION, HOLDOUT, freezeDirectory,
                SELECTION_MANIFEST, HOLDOUT_MANIFEST, sourceLock, ledger,
                diagnostic, incompleteMarker, selectionStaging, selectionClaim,
                holdoutStaging, holdoutClaim, freezeStaging, freezeClaim,
                unexpectedHoldout, unexpectedSelection, freezeReport,
                diagnosticReport);
    }

    private static void requireMode(String expected) {
        if (!expected.equals(System.getProperty(MODE_PROPERTY))) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static void requireFormalCommand() {
        ReflectionTestUtils.invokeMethod(
                RagCandidate10DiagnosticStageSupport.class,
                "requireFormalCommand");
    }

    private static void requireFormalNamespaceAbsent() throws IOException {
        requireDirectory(RUNTIME);
        requireAbsent(RECOVERY);
        requireAbsent(STAGING);
        requireAbsent(CLAIM);
        requireAbsent(INCOMPLETE);
        requireAbsent(UNEXPECTED_HOLDOUT);
        requireAbsent(UNEXPECTED_SELECTION);
        requireAbsent(ORIGINAL_UNEXPECTED_HOLDOUT);
        requireAbsent(ORIGINAL_UNEXPECTED_SELECTION);
    }

    private static void requireRecoveryNamespaceAbsent() throws IOException {
        requireFormalNamespaceAbsent();
        requireAbsent(RUNTIME.resolve(
                "candidate10-diagnostic-recovery-v2.selection.staging"));
        requireAbsent(RUNTIME.resolve(
                "candidate10-diagnostic-recovery-v2.selection.claim"));
        requireAbsent(RUNTIME.resolve(
                "candidate10-diagnostic-recovery-v2.holdout.staging"));
        requireAbsent(RUNTIME.resolve(
                "candidate10-diagnostic-recovery-v2.holdout.claim"));
        requireAbsent(PUBLISH_REPORT);
    }

    private static void requireRecoveryNamespacePublished() throws IOException {
        requireDirectory(RUNTIME);
        requireDirectory(RECOVERY);
        requireExactDirectory(RECOVERY,
                Set.of("candidate10-source-lock.json", "recovery-manifest.json"));
        requireAbsent(STAGING);
        requireAbsent(CLAIM);
        requireAbsent(INCOMPLETE);
        for (Path guard : List.of(
                publishedRuntimePaths().selectionStaging(),
                publishedRuntimePaths().selectionClaim(),
                publishedRuntimePaths().holdoutStaging(),
                publishedRuntimePaths().holdoutClaim(),
                publishedRuntimePaths().freezeStaging(),
                publishedRuntimePaths().freezeClaim(),
                UNEXPECTED_HOLDOUT, UNEXPECTED_SELECTION)) {
            requireAbsent(guard);
        }
        requireAbsent(RECOVERY.resolve(".selection-ledger.json.tmp"));
        requireAbsent(RECOVERY.resolve(".candidate10-diagnostic.json.tmp"));
        requireAbsent(DIAGNOSTIC_SELECTOR_REPORT);
        requireAbsent(ORIGINAL_UNEXPECTED_HOLDOUT);
        requireAbsent(ORIGINAL_UNEXPECTED_SELECTION);
    }

    private static void requireAbsent(Path path) {
        if (path == null || Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static void requireRegular(Path path) {
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static void requireDirectory(Path path) {
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static void requireExactDirectory(Path directory,
                                              Set<String> expectedNames) {
        requireDirectory(directory);
        try (var entries = Files.list(directory)) {
            Set<String> actual = entries
                    .map(path -> {
                        if (Files.isSymbolicLink(path)
                                || (!Files.isRegularFile(path,
                                LinkOption.NOFOLLOW_LINKS))) {
                            throw new IllegalStateException(RECOVERY_INVALID);
                        }
                        return path.getFileName().toString();
                    })
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
            if (!actual.equals(new TreeSet<>(expectedNames))) {
                throw new IllegalStateException(RECOVERY_INVALID);
            }
        } catch (IOException failure) {
            throw new IllegalStateException(RECOVERY_INVALID, failure);
        }
    }

    private static void writeCreateNew(Path path, byte[] bytes) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null || Files.isSymbolicLink(parent)
                || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        assertArrayEquals(bytes, readRegular(path));
    }

    private static void createClaim(Path path, AtomicBoolean mutated)
            throws IOException {
        Objects.requireNonNull(mutated, "mutated");
        requireAbsent(path);
        requireDirectory(path.toAbsolutePath().normalize().getParent());
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            mutated.set(true);
            channel.force(true);
        }
        requireRegular(path);
        if (Files.size(path) != 0L) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static byte[] readRegular(Path path) {
        requireRegular(path);
        try {
            return Files.readAllBytes(path);
        } catch (IOException failure) {
            throw new IllegalStateException(RECOVERY_INVALID, failure);
        }
    }

    private static void writeIncomplete(String failedPhase) throws IOException {
        writeIncompleteAt(INCOMPLETE, failedPhase);
    }

    private static void writeIncompleteAt(Path markerPath, String failedPhase)
            throws IOException {
        if (!FAILED_PHASES.contains(failedPhase)) {
            throw new IllegalArgumentException(RECOVERY_INVALID);
        }
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("version", 1);
        marker.put("status", "INVALID");
        marker.put("failedPhase", failedPhase);
        marker.put("errorCode", "CANDIDATE10_FREEZE_INCOMPLETE");
        writeCreateNew(markerPath,
                RagCandidate10FreezeSupport.canonicalJsonBytes(marker));
        validateIncomplete(readRegular(markerPath));
    }

    private static void validateIncomplete(byte[] bytes) {
        JSONObject marker = parseCanonical(bytes);
        requireKeys(marker, INCOMPLETE_KEYS);
        if (!(marker.get("version") instanceof Integer version)
                || version != 1
                || !(marker.get("status") instanceof String status)
                || !"INVALID".equals(status)
                || !(marker.get("failedPhase") instanceof String phase)
                || !FAILED_PHASES.contains(phase)
                || !(marker.get("errorCode") instanceof String error)
                || !RECOVERY_ERROR.equals(error)) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static void assertPublishedPathsRejectClaim() {
        try {
            RagCandidate10FreezeSupport.openDiagnosticEvidence(
                    publishedRuntimePaths());
            throw new IllegalStateException(RECOVERY_INVALID);
        } catch (IllegalStateException failure) {
            assertEquals("CANDIDATE10_FREEZE_INCOMPLETE", failure.getMessage());
        }
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(RECOVERY_INVALID, failure);
    }

    private static byte[] compatibleSourceLockBytes() {
        byte[] originalBytes = readRegular(ORIGINAL_SOURCE_LOCK);
        if (!ORIGINAL_SOURCE_LOCK_SHA.equals(
                RagCandidate10FreezeSupport.sha256(originalBytes))) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        JSONObject original = parseCanonical(originalBytes);
        validateSourceLockSchema(original);
        JSONArray files = original.getJSONArray("files");
        int replacements = 0;
        String newSha = RagCandidate10FreezeSupport.sha256(STAGE_SOURCE);
        if (OLD_STAGE_SHA.equals(newSha)) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        for (Object raw : files) {
            JSONObject binding = (JSONObject) raw;
            if (STAGE_SOURCE_RELATIVE.equals(binding.getString("file"))) {
                binding.put("sha256", newSha);
                replacements++;
            }
        }
        if (replacements != 1) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        byte[] candidate = RagCandidate10FreezeSupport.canonicalJsonBytes(original);
        validateCompatibleSourceLock(candidate);
        return candidate;
    }

    private static final String STAGE_SOURCE_RELATIVE =
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10DiagnosticStageSupport.java";
    private static final String RECOVERY_SOURCE_RELATIVE =
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10DiagnosticRecoveryV2Test.java";
    private static final String ATTRIBUTION_SOURCE_RELATIVE =
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10RankingFailureAttributionTest.java";

    private static void validateCompatibleSourceLock(byte[] bytes) {
        JSONObject candidate = parseCanonical(bytes);
        validateSourceLockSchema(candidate);
        if (!"1".equals(String.valueOf(candidate.get("version")))
                || !ALGORITHM.equals(candidate.getString("algorithm"))
                || !CAUSAL_SCOPE.equals(candidate.getString("causalScope"))
                || !"ABSENT".equals(candidate.getString(
                "candidate10ProductionProperty"))
                || !CONFIG_HASH.equals(candidate.getString("configHash"))) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        JSONArray files = candidate.getJSONArray("files");
        int changed = 0;
        for (Object raw : files) {
            JSONObject binding = (JSONObject) raw;
            String file = binding.getString("file");
            String sha = binding.getString("sha256");
            String actual = RagCandidate10FreezeSupport.sha256(
                    BACKEND.resolve(file));
            if (STAGE_SOURCE_RELATIVE.equals(file)) {
                if (OLD_STAGE_SHA.equals(sha) || !actual.equals(sha)) {
                    throw new IllegalStateException(RECOVERY_INVALID);
                }
                changed++;
            } else if (!actual.equals(sha)) {
                throw new IllegalStateException(RECOVERY_INVALID);
            }
        }
        if (changed != 1) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        byte[] original = readRegular(ORIGINAL_SOURCE_LOCK);
        if (!Arrays.equals(original, restoredOriginalSourceLock(bytes))) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        JSONObject originalJson = parseCanonical(original);
        if (!canonicalEqual(candidate.get("config"), originalJson.get("config"))
                || !canonicalEqual(candidate.get("jdk"), originalJson.get("jdk"))
                || !canonicalEqual(candidate.get("selection"),
                originalJson.get("selection"))
                || !canonicalEqual(candidate.get("holdout"),
                originalJson.get("holdout"))
                || !canonicalEqual(candidate.get("historicalEvidence"),
                originalJson.get("historicalEvidence"))) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static byte[] restoredOriginalSourceLock(byte[] compatible) {
        JSONObject restored = parseCanonical(compatible);
        JSONArray files = restored.getJSONArray("files");
        for (Object raw : files) {
            JSONObject binding = (JSONObject) raw;
            if (STAGE_SOURCE_RELATIVE.equals(binding.getString("file"))) {
                binding.put("sha256", OLD_STAGE_SHA);
            }
        }
        return RagCandidate10FreezeSupport.canonicalJsonBytes(restored);
    }

    private static void validateSourceLockSchema(JSONObject lock) {
        requireKeys(lock, SOURCE_LOCK_KEYS);
        requireNumber(lock, "version", 1);
        requireString(lock, "algorithm");
        requireString(lock, "candidate10ProductionProperty");
        requireString(lock, "causalScope");
        requireObject(lock, "config");
        requireSha(lock, "configHash");
        requireString(lock, "historicalArtifactLocalState");
        requireString(lock, "historicalEvidenceMode");
        requireBindings(lock.getJSONArray("files"), 29, true);
        requireBindings(lock.getJSONArray("preflightReports"), 3, true);
        JSONArray history = requireArray(lock, "historicalEvidence", 2);
        for (Object raw : history) {
            JSONObject item = requireObject(raw);
            requireKeys(item, Set.of("id", "path", "sha256"));
            requireString(item, "id");
            requireString(item, "path");
            requireSha(item, "sha256");
        }
        validateSplit(lock.getJSONObject("selection"));
        validateSplit(lock.getJSONObject("holdout"));
        JSONObject jdk = requireObject(lock, "jdk");
        requireKeys(jdk, JDK_KEYS);
        for (String key : JDK_KEYS) {
            requireString(jdk, key);
        }
    }

    private static void validateSplit(JSONObject split) {
        requireKeys(split, SPLIT_KEYS);
        requireSha(split, "datasetHash");
        requireSha(split, "fixtureSpecHash");
        requireSha(split, "manifestSha256");
        JSONObject resources = requireObject(split, "resources");
        requireKeys(resources, Set.of("corpus", "pressure", "qrels", "queries"));
        for (String key : List.of("corpus", "pressure", "qrels", "queries")) {
            requireBinding(resources, key);
        }
    }

    private static void requireBindings(JSONArray values, int expected,
                                        boolean sorted) {
        if (values == null || values.size() != expected) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> order = new ArrayList<>();
        for (Object raw : values) {
            JSONObject item = requireObject(raw);
            requireKeys(item, BINDING_KEYS);
            String file = requireString(item, "file");
            requireSha(item, "sha256");
            if (!seen.add(file)) {
                throw new IllegalStateException(RECOVERY_INVALID);
            }
            order.add(file);
        }
        if (sorted && !order.equals(order.stream().sorted().toList())) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static byte[] recoveryEnvelopeBytes(byte[] compatibleSourceLock) {
        JSONObject originalLock = parseCanonical(readRegular(ORIGINAL_SOURCE_LOCK));
        String compatibleSha = RagCandidate10FreezeSupport.sha256(
                compatibleSourceLock);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", RECOVERY_SCHEMA);
        root.put("algorithm", ALGORITHM);
        root.put("causalScope", CAUSAL_SCOPE);
        root.put("rootCauseClass", "HARNESS_CONTRACT_BUG");
        root.put("algorithmConclusion", "NOT_REACHED");
        root.put("originalEvidence", Map.of(
                "sourceLock", binding(
                        "tests/target/rag-eval/candidate10-freeze/"
                                + "candidate10-source-lock.json",
                        ORIGINAL_SOURCE_LOCK_SHA),
                "diagnostic", binding(
                        "tests/target/rag-eval/"
                                + "candidate10-calibration-diagnostic.json",
                        ORIGINAL_DIAGNOSTIC_SHA),
                "ledger", binding(
                        "tests/target/rag-eval/candidate10-freeze/"
                                + "selection-ledger.json", ORIGINAL_LEDGER_SHA)));
        root.put("sourceDelta", Map.of(
                "file", STAGE_SOURCE_RELATIVE,
                "oldSha256", OLD_STAGE_SHA,
                "newSha256", RagCandidate10FreezeSupport.sha256(STAGE_SOURCE)));
        root.put("attribution", Map.of(
                "status", "COMPLETED",
                "attempt", 1,
                "queryOrdinal", 1,
                "arm", "BASELINE",
                "failureStage", "PRODUCTION_ORACLE_FIELD",
                "errorCode", "CANDIDATE10_RANKING_INVALID",
                "source", binding(ATTRIBUTION_SOURCE_RELATIVE,
                        ATTRIBUTION_SOURCE_SHA),
                "reports", List.of(
                        report("XML", attributionXmlRelative(), ATTRIBUTION_XML_SHA),
                        report("TXT", attributionTxtRelative(), ATTRIBUTION_TXT_SHA))));
        root.put("recoveryHarness", binding(
                RECOVERY_SOURCE_RELATIVE,
                RagCandidate10FreezeSupport.sha256(RECOVERY_SOURCE)));
        root.put("compatibleSourceLock", binding(
                "candidate10-source-lock.json", compatibleSha));
        root.put("jdk", new LinkedHashMap<>(originalLock.getJSONObject("jdk")));
        root.put("configHash", CONFIG_HASH);
        root.put("selectionDatasetHash", SELECTION_DATASET_HASH);
        root.put("selectionManifestSha256", SELECTION_MANIFEST_SHA);
        root.put("holdoutManifestSha256", HOLDOUT_MANIFEST_SHA);
        byte[] bytes = RagCandidate10FreezeSupport.canonicalJsonBytes(root);
        validateRecoveryEnvelope(bytes, compatibleSourceLock);
        return bytes;
    }

    private static Map<String, String> binding(String file, String sha) {
        Map<String, String> value = new LinkedHashMap<>();
        value.put("file", file);
        value.put("sha256", sha);
        return value;
    }

    private static Map<String, String> report(String format, String file,
                                              String sha) {
        Map<String, String> value = new LinkedHashMap<>();
        value.put("format", format);
        value.put("file", file);
        value.put("sha256", sha);
        return value;
    }

    private static String attributionXmlRelative() {
        return "tests/target/surefire-reports/TEST-tech.qiantong.qknow.rag.eval."
                + "RagCandidate10RankingFailureAttributionTest-"
                + "candidate10-ranking-attribution-attempt-001.xml";
    }

    private static String attributionTxtRelative() {
        return "tests/target/surefire-reports/tech.qiantong.qknow.rag.eval."
                + "RagCandidate10RankingFailureAttributionTest-"
                + "candidate10-ranking-attribution-attempt-001.txt";
    }

    private static void validateRecoveryEnvelope(byte[] bytes,
                                                  byte[] compatibleSourceLock) {
        JSONObject root = parseCanonical(bytes);
        requireKeys(root, RECOVERY_KEYS);
        requireExactString(root, "schemaVersion", RECOVERY_SCHEMA);
        requireExactString(root, "algorithm", ALGORITHM);
        requireExactString(root, "causalScope", CAUSAL_SCOPE);
        requireExactString(root, "rootCauseClass", "HARNESS_CONTRACT_BUG");
        requireExactString(root, "algorithmConclusion", "NOT_REACHED");

        JSONObject evidence = requireObject(root, "originalEvidence");
        requireKeys(evidence, ORIGINAL_EVIDENCE_KEYS);
        requireBinding(evidence, "sourceLock",
                "tests/target/rag-eval/candidate10-freeze/"
                        + "candidate10-source-lock.json", ORIGINAL_SOURCE_LOCK_SHA);
        requireBinding(evidence, "diagnostic",
                "tests/target/rag-eval/candidate10-calibration-diagnostic.json",
                ORIGINAL_DIAGNOSTIC_SHA);
        requireBinding(evidence, "ledger",
                "tests/target/rag-eval/candidate10-freeze/selection-ledger.json",
                ORIGINAL_LEDGER_SHA);

        JSONObject delta = requireObject(root, "sourceDelta");
        requireKeys(delta, Set.of("file", "oldSha256", "newSha256"));
        requireExactString(delta, "file", STAGE_SOURCE_RELATIVE);
        requireExactString(delta, "oldSha256", OLD_STAGE_SHA);
        requireExactString(delta, "newSha256",
                RagCandidate10FreezeSupport.sha256(STAGE_SOURCE));

        JSONObject attribution = requireObject(root, "attribution");
        requireKeys(attribution, ATTRIBUTION_KEYS);
        requireExactString(attribution, "status", "COMPLETED");
        requireNumber(attribution, "attempt", 1);
        requireNumber(attribution, "queryOrdinal", 1);
        requireExactString(attribution, "arm", "BASELINE");
        requireExactString(attribution, "failureStage", "PRODUCTION_ORACLE_FIELD");
        requireExactString(attribution, "errorCode", "CANDIDATE10_RANKING_INVALID");
        requireBinding(attribution, "source", ATTRIBUTION_SOURCE_RELATIVE,
                ATTRIBUTION_SOURCE_SHA);
        JSONArray reports = requireArray(attribution, "reports", 2);
        JSONObject xml = requireObject(reports.get(0));
        JSONObject txt = requireObject(reports.get(1));
        requireKeys(xml, REPORT_KEYS);
        requireKeys(txt, REPORT_KEYS);
        requireExactString(xml, "format", "XML");
        requireExactString(xml, "file", attributionXmlRelative());
        requireExactString(xml, "sha256", ATTRIBUTION_XML_SHA);
        requireExactString(txt, "format", "TXT");
        requireExactString(txt, "file", attributionTxtRelative());
        requireExactString(txt, "sha256", ATTRIBUTION_TXT_SHA);

        JSONObject harness = requireObject(root, "recoveryHarness");
        requireKeys(harness, BINDING_KEYS);
        requireExactString(harness, "file", RECOVERY_SOURCE_RELATIVE);
        requireExactString(harness, "sha256",
                RagCandidate10FreezeSupport.sha256(RECOVERY_SOURCE));
        requireBinding(root, "compatibleSourceLock",
                "candidate10-source-lock.json",
                RagCandidate10FreezeSupport.sha256(compatibleSourceLock));

        JSONObject originalLock = parseCanonical(readRegular(ORIGINAL_SOURCE_LOCK));
        JSONObject jdk = requireObject(root, "jdk");
        requireKeys(jdk, JDK_KEYS);
        if (!canonicalEqual(jdk, originalLock.get("jdk"))) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        requireExactString(root, "configHash", CONFIG_HASH);
        requireExactString(root, "selectionDatasetHash", SELECTION_DATASET_HASH);
        requireExactString(root, "selectionManifestSha256", SELECTION_MANIFEST_SHA);
        requireExactString(root, "holdoutManifestSha256", HOLDOUT_MANIFEST_SHA);

        if (!ORIGINAL_SOURCE_LOCK_SHA.equals(
                sha256Path(ORIGINAL_SOURCE_LOCK))
                || !ORIGINAL_DIAGNOSTIC_SHA.equals(
                sha256Path(ORIGINAL_DIAGNOSTIC))
                || !ORIGINAL_LEDGER_SHA.equals(sha256Path(ORIGINAL_LEDGER))
                || !SELECTION_MANIFEST_SHA.equals(
                sha256Path(SELECTION_MANIFEST))
                || !HOLDOUT_MANIFEST_SHA.equals(
                sha256Path(HOLDOUT_MANIFEST))
                || !ATTRIBUTION_SOURCE_SHA.equals(
                sha256Path(ATTRIBUTION_SOURCE))
                || !ATTRIBUTION_XML_SHA.equals(sha256Path(ATTRIBUTION_XML))
                || !ATTRIBUTION_TXT_SHA.equals(sha256Path(ATTRIBUTION_TXT))) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static void assertEnvelopeRejections(byte[] bytes,
                                                 byte[] sourceLock) {
        JSONObject extra = parseCanonical(bytes);
        extra.put("extra", true);
        assertThrows(IllegalStateException.class,
                () -> validateRecoveryEnvelope(
                        RagCandidate10FreezeSupport.canonicalJsonBytes(extra),
                        sourceLock));

        JSONObject missing = parseCanonical(bytes);
        missing.remove("configHash");
        assertThrows(IllegalStateException.class,
                () -> validateRecoveryEnvelope(
                        RagCandidate10FreezeSupport.canonicalJsonBytes(missing),
                        sourceLock));

        JSONObject wrongType = parseCanonical(bytes);
        wrongType.getJSONObject("attribution").put("attempt", "1");
        assertThrows(IllegalStateException.class,
                () -> validateRecoveryEnvelope(
                        RagCandidate10FreezeSupport.canonicalJsonBytes(wrongType),
                        sourceLock));

        JSONObject wrongSha = parseCanonical(bytes);
        wrongSha.getJSONObject("sourceDelta").put("newSha256", "x");
        assertThrows(IllegalStateException.class,
                () -> validateRecoveryEnvelope(
                        RagCandidate10FreezeSupport.canonicalJsonBytes(wrongSha),
                        sourceLock));

        JSONObject wrongOrder = parseCanonical(bytes);
        JSONArray reports = wrongOrder.getJSONObject("attribution")
                .getJSONArray("reports");
        Object first = reports.get(0);
        reports.set(0, reports.get(1));
        reports.set(1, first);
        assertThrows(IllegalStateException.class,
                () -> validateRecoveryEnvelope(
                        RagCandidate10FreezeSupport.canonicalJsonBytes(wrongOrder),
                        sourceLock));

        byte[] nonCanonical = JSON.toJSONString(
                parseCanonical(bytes)).getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class,
                () -> validateRecoveryEnvelope(nonCanonical, sourceLock));
    }

    private static JSONObject parseCanonical(byte[] bytes) {
        try {
            JSONObject value = JSON.parseObject(bytes);
            if (value == null || !Arrays.equals(bytes,
                    RagCandidate10FreezeSupport.canonicalJsonBytes(value))) {
                throw new IllegalStateException(RECOVERY_INVALID);
            }
            return value;
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IllegalStateException(RECOVERY_INVALID, failure);
        }
    }

    private static void requireKeys(JSONObject value, Set<String> expected) {
        if (value == null || !value.keySet().equals(expected)) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static JSONObject requireObject(JSONObject parent, String key) {
        Object value = parent == null ? null : parent.get(key);
        return requireObject(value);
    }

    private static JSONObject requireObject(Object value) {
        if (!(value instanceof JSONObject object)) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        return object;
    }

    private static JSONArray requireArray(JSONObject parent, String key,
                                          int expectedSize) {
        Object value = parent == null ? null : parent.get(key);
        if (!(value instanceof JSONArray array) || array.size() != expectedSize) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        return array;
    }

    private static String requireString(JSONObject parent, String key) {
        Object value = parent == null ? null : parent.get(key);
        if (!(value instanceof String string) || string.isEmpty()) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        return string;
    }

    private static void requireExactString(JSONObject parent, String key,
                                           String expected) {
        if (!expected.equals(requireString(parent, key))) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static void requireSha(JSONObject parent, String key) {
        String value = requireString(parent, key);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static void requireNumber(JSONObject parent, String key,
                                      int expected) {
        Object value = parent == null ? null : parent.get(key);
        if (!(value instanceof Number number)
                || number.doubleValue() != expected) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
    }

    private static void requireBinding(JSONObject parent, String key) {
        JSONObject binding = requireObject(parent, key);
        requireKeys(binding, BINDING_KEYS);
        requireString(binding, "file");
        requireSha(binding, "sha256");
    }

    private static void requireBinding(JSONObject parent, String key,
                                       String expectedFile, String expectedSha) {
        JSONObject binding = requireObject(parent, key);
        requireKeys(binding, BINDING_KEYS);
        requireExactString(binding, "file", expectedFile);
        requireExactString(binding, "sha256", expectedSha);
    }

    private static boolean canonicalEqual(Object left, Object right) {
        return Arrays.equals(RagCandidate10FreezeSupport.canonicalJsonBytes(left),
                RagCandidate10FreezeSupport.canonicalJsonBytes(right));
    }

    private static String sha256Path(Path path) {
        return RagCandidate10FreezeSupport.sha256(path);
    }

    private static Map<String, String> snapshotAllowedEvidence() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("diagnostic", sha256Path(ORIGINAL_DIAGNOSTIC));
        snapshot.put("ledger", sha256Path(ORIGINAL_LEDGER));
        snapshot.put("sourceLock", sha256Path(ORIGINAL_SOURCE_LOCK));
        snapshot.put("selectionManifest", sha256Path(SELECTION_MANIFEST));
        snapshot.put("holdoutManifest", sha256Path(HOLDOUT_MANIFEST));
        snapshot.put("selectionCorpus", sha256Path(SELECTION.resolve("corpus.jsonl")));
        snapshot.put("selectionQueries", sha256Path(SELECTION.resolve("queries.jsonl")));
        snapshot.put("selectionPressure", sha256Path(SELECTION.resolve("pressure.json")));
        return Map.copyOf(snapshot);
    }

    private static void assertOriginalEvidenceUnchanged(
            Map<String, String> expected) {
        assertEquals(expected, snapshotAllowedEvidence());
        assertEquals(ORIGINAL_SOURCE_LOCK_SHA,
                sha256Path(ORIGINAL_SOURCE_LOCK));
        assertEquals(ORIGINAL_DIAGNOSTIC_SHA,
                sha256Path(ORIGINAL_DIAGNOSTIC));
        assertEquals(ORIGINAL_LEDGER_SHA, sha256Path(ORIGINAL_LEDGER));
    }

    private static void verifyDiagnosticResult(
            RagCandidate10DiagnosticStageSupport.DiagnosticResult result,
            byte[] sourceLock,
            byte[] envelope,
            Map<String, String> before) {
        assertTrue(Files.isRegularFile(result.artifact(), LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.isRegularFile(result.ledger(), LinkOption.NOFOLLOW_LINKS));
        assertEquals(RECOVERY.resolve("candidate10-diagnostic.json"),
                result.artifact());
        assertEquals(RECOVERY.resolve("selection-ledger.json"), result.ledger());
        byte[] artifactBytes = readRegular(result.artifact());
        byte[] ledgerBytes = readRegular(result.ledger());
        assertEquals(result.artifactSha256(),
                RagCandidate10FreezeSupport.sha256(artifactBytes));
        assertEquals(result.ledgerSha256(),
                RagCandidate10FreezeSupport.sha256(ledgerBytes));
        JSONObject artifact = parseCanonical(artifactBytes);
        JSONObject ledger = parseCanonical(ledgerBytes);
        Map<String, Object> artifactMap = new LinkedHashMap<>(artifact);
        RagCandidate10DiagnosticStageSupport.validateSanitizedArtifact(artifactMap);
        assertEquals(result.valid() ? "VALID" : "INVALID",
                artifact.getString("validity"));
        assertEquals(result.decision(), artifact.get("decision"));
        assertEquals(result.errorCode(), artifact.get("errorCode"));
        assertEquals(result.artifactSha256(), ledger.getString("diagnosticSha256"));
        assertEquals("COMPLETED", ledger.getString("status"));
        assertEquals(artifact.getString("validity"), ledger.getString("validity"));
        assertEquals(result.decision(), ledger.get("decision"));
        assertEquals(result.errorCode(), ledger.get("errorCode"));
        if (result.valid()) {
            assertNull(result.errorCode());
            assertTrue(Set.of(
                    RagCandidate10DiagnosticStageSupport.PROCEED_DECISION,
                    RagCandidate10DiagnosticStageSupport.STOP_DECISION)
                    .contains(result.decision()));
            assertAccess(artifact, 0, 1, 0);
        } else {
            assertNull(result.decision());
            assertNotNull(result.errorCode());
            assertFalse(result.errorCode().isEmpty());
            JSONObject access = requireObject(artifact, "access");
            int beforeRanking = integer(access, "qrelResourceAccessBeforeRanking");
            int qrelCount = integer(access, "qrelResourceAccessCount");
            int holdoutCount = integer(access, "holdoutResourceAccessCount");
            assertEquals(0, beforeRanking);
            assertEquals(0, holdoutCount);
            if ("RUNNING".equals(artifact.getString("failedPhase"))) {
                assertEquals(0, qrelCount);
            }
        }
        assertArrayEquals(sourceLock,
                readRegular(RECOVERY.resolve("candidate10-source-lock.json")));
        assertArrayEquals(envelope,
                readRegular(RECOVERY.resolve("recovery-manifest.json")));
        validateRecoveryEnvelope(readRegular(
                RECOVERY.resolve("recovery-manifest.json")), sourceLock);
        validateCompatibleSourceLock(readRegular(
                RECOVERY.resolve("candidate10-source-lock.json")));
        RagCandidate10FreezeSupport.FrozenEvidence postRunEvidence =
                RagCandidate10FreezeSupport.openDiagnosticEvidence(
                        publishedRuntimePaths());
        RagCandidate10FreezeSupport.requireSourceLockUnchanged(postRunEvidence);
        assertOriginalEvidenceUnchanged(before);
    }

    private static void assertAccess(JSONObject artifact, int before,
                                     int qrel, int holdout) {
        JSONObject access = requireObject(artifact, "access");
        assertEquals(before, integer(access, "qrelResourceAccessBeforeRanking"));
        assertEquals(qrel, integer(access, "qrelResourceAccessCount"));
        assertEquals(holdout, integer(access, "holdoutResourceAccessCount"));
    }

    private static int integer(JSONObject object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException(RECOVERY_INVALID);
        }
        return number.intValue();
    }

}
