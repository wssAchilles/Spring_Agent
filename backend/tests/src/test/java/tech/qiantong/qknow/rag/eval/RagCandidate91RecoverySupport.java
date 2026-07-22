package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.JSONObject;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/** Candidate 9.1 one-shot recovery lifecycle. Test source only. */
final class RagCandidate91RecoverySupport {

    static final String RECOVERY_PROPERTY = "rag.eval.candidate9.recovery";
    static final String PROBE_PROPERTY = "rag.eval.candidate9.recovery-probe";
    static final String SOURCE_LOCK_PROPERTY =
            "rag.eval.candidate9.recovery-source-lock";
    static final String PREFLIGHT_DATABASE = "candidate91_preflight";
    static final Path SOURCE_LOCK_STAGING =
            Path.of("/private/tmp/candidate91-source-lock.json");
    static final byte[] ARCHIVE_MARKER_BYTES =
            "{\"state\":\"ARCHIVE_PUBLISHED\",\"version\":1}"
                    .getBytes(StandardCharsets.UTF_8);

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Object WRITER_LOCK = new Object();
    private static final String SELECTION_MANIFEST_SHA =
            "440ff90e128b2110e305972f7a5918e0cd0652db73c863701d01d8ce85f9beab";
    private static final String HOLDOUT_MANIFEST_SHA =
            "9be60ae5001ae6e597a34591f15eb394a3207cc7c55e910d614b88330549433e";
    private static final String SELECTION_DATASET_HASH =
            "d9f6f87be1c1bcca34bae56b2906835ba809c696042e34d525d85e7513045f8f";
    private static final String HOLDOUT_DATASET_HASH =
            "be9c1a3016c481200b735c46f426de862be78a6e5dcbcbab7abf942fb99f1bc8";
    private static final String CONFIG_HASH =
            "a22dc5a7fb08fff64a6b2b0f6218ed9ce04318e726ac83fb1f20ba62fc44674b";
    private static final String SELECTION_LEDGER_SHA =
            "af693ad3a6f40ddb282b36863b772bd7f5aaf50c1c66d7dc0093cf4697146d7a";
    private static final String DIAGNOSTIC_SHA =
            "adb88ed3f6de0851315f82fe91250567880b533622d3fe34e152a4d3ece624d8";
    private static final String SHADOW_XML_SHA =
            "0889bec5c3d7fc5e03832d40ccc9b826ad77882284ea22bbc13fa518796af7ee";
    private static final String SHADOW_TXT_SHA =
            "28c3517423ae13530f77fa3f017de114abb2281907ce1ce1563c264f8cffc268";

    private static final List<String> SOURCE_LOCK_FILES = List.of(
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate9DiagnosticSupport.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate9DiagnosticSupportTest.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagShadowBaselineTest.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate91RecoverySupport.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate91RecoverySupportTest.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate91DockerAuthorizationProbeTest.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate91SourceLockTest.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate91NonDockerContractTest.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/ShadowContractSupport.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagEvaluationDataset.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagEvaluationDatasetLoader.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagMetrics.java",
            "tests/pom.xml");
    private static final Set<String> ARCHIVE_FILES = Set.of(
            "original-evidence-manifest.json",
            "recovery-source-lock.json",
            "archive-complete.marker");
    private static final Set<String> LEGACY_FILES = Set.of(
            "selection-manifest.json", "holdout-manifest.json",
            "selection-ledger.json", "candidate9-calibration-diagnostic.json");
    private static final Set<String> ROOT_FILES = Set.of(
            "docker-preflight.json", "preflight-failure.json",
            "recovery-ledger.json", "recovery-diagnostic.json");
    private static final Set<String> FORBIDDEN_ARTIFACT_KEYS = Set.of(
            "query", "retrievalquery", "identifier", "identifiers",
            "matchedidentifier", "matchedidentifiers", "visibleidentifier",
            "visibleidentifiers", "projectiontoken", "projectiontokens",
            "projectedtoken", "projectedtokens", "projectedtext", "term",
            "terms", "contentterm", "contentterms", "documentname",
            "documenttext", "originalcontent", "content", "answer",
            "reference", "referenceanswer", "referenceclaims", "qrel",
            "qrels", "qrelgrade", "grade", "apikey", "baseurl", "url",
            "model", "modelname", "embeddingapikey", "embeddingbaseurl",
            "embeddingmodel", "sqlparameter", "sqlparameters", "sqlparam",
            "sqlparams", "sqlexpressionvalue", "sqlexpressionvalues",
            "exceptionmessage", "errormessage");

    private RagCandidate91RecoverySupport() {
    }

    static RecoveryPaths paths(Path ragEvalRuntime) {
        Path runtime = Objects.requireNonNull(ragEvalRuntime, "ragEvalRuntime")
                .toAbsolutePath().normalize();
        Path root = runtime.resolve("candidate9-recovery");
        Path archive = root.resolve("archive");
        Path legacy = archive.resolve("legacy");
        return new RecoveryPaths(runtime, root, archive, legacy,
                archive.resolve("original-evidence-manifest.json"),
                archive.resolve("recovery-source-lock.json"),
                archive.resolve("archive-complete.marker"),
                legacy.resolve("selection-manifest.json"),
                legacy.resolve("holdout-manifest.json"),
                legacy.resolve("selection-ledger.json"),
                legacy.resolve("candidate9-calibration-diagnostic.json"),
                root.resolve("docker-preflight.json"),
                root.resolve("preflight-failure.json"),
                root.resolve("recovery-ledger.json"),
                root.resolve("recovery-diagnostic.json"));
    }

    static LegacyBinding lockedLegacyBinding() {
        return new LegacyBinding(SELECTION_MANIFEST_SHA, HOLDOUT_MANIFEST_SHA,
                SELECTION_DATASET_HASH, HOLDOUT_DATASET_HASH, CONFIG_HASH,
                SELECTION_LEDGER_SHA, DIAGNOSTIC_SHA, SHADOW_XML_SHA,
                SHADOW_TXT_SHA);
    }

    static void requireRecoveryProperties() {
        requireExactProperty(RECOVERY_PROPERTY, "true");
        if (System.getProperty(PROBE_PROPERTY) != null
                || System.getProperty(SOURCE_LOCK_PROPERTY) != null
                || System.getProperty(
                RagCandidate9DiagnosticSupport.HOLDOUT_DIRECTORY_PROPERTY) != null) {
            throw new IllegalStateException("CANDIDATE91_COMMAND_PROPERTY_INVALID");
        }
        RagCandidate9DiagnosticSupport.requireDiagnosticCommandProperties();
    }

    static void requireProbeProperty() {
        requireExactProperty(PROBE_PROPERTY, "true");
        requireAbsent(RECOVERY_PROPERTY);
        requireAbsent(SOURCE_LOCK_PROPERTY);
    }

    static void requireSourceLockProperty() {
        requireExactProperty(SOURCE_LOCK_PROPERTY, "true");
        requireAbsent(RECOVERY_PROPERTY);
        requireAbsent(PROBE_PROPERTY);
    }

    static void requireCurrentConfigHash(String actualConfigHash) {
        requireEquals(CONFIG_HASH, actualConfigHash,
                "CANDIDATE91_CONFIG_CHANGED");
    }

    static ProbeAudit probeDockerAuthorization() {
        requireProbeProperty();
        try {
            DockerClientFactory.instance().client().pingCmd().exec();
            return new ProbeAudit(0, 0, 0, 0, 1);
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "STOP_CANDIDATE91_DOCKER_AUTHORIZATION_REQUIRED", failure);
        }
    }

    static SourceLock publishSourceLockStaging(Path backendRoot, Path staging) {
        Path root = requireDirectory(backendRoot, "CANDIDATE91_SOURCE_ROOT_INVALID");
        Path target = Objects.requireNonNull(staging, "staging")
                .toAbsolutePath().normalize();
        requireNoSymlink(target.getParent(), target.getParent(),
                "CANDIDATE91_SOURCE_LOCK_PATH_INVALID");
        List<Map<String, Object>> files = new ArrayList<>();
        for (String logical : SOURCE_LOCK_FILES) {
            Path source = requireRegularFile(root.resolve(logical),
                    "CANDIDATE91_SOURCE_LOCK_INPUT_INVALID");
            files.add(Map.of("file", logical,
                    "sha256", ShadowContractSupport.sha256(source)));
        }
        Map<String, Object> lock = new LinkedHashMap<>();
        lock.put("version", 1);
        lock.put("generatedAt", Instant.now().toString());
        lock.put("files", files);
        byte[] bytes = canonicalJsonBytes(lock);
        atomicCreate(target, bytes);
        return new SourceLock(target, ShadowContractSupport.sha256(target),
                files.size(), bytes.clone());
    }

    static ArchiveHandle publishArchive(
            RecoveryPaths paths,
            Path backendRoot,
            Path sourceLockStaging,
            LegacyBinding binding) {
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(binding, "binding").validate();
        synchronized (WRITER_LOCK) {
            validateNamespace(paths, RecoveryPhase.EMPTY);
            RagCandidate9DiagnosticSupport.verifyLockedEvidence();
            Path backend = requireDirectory(backendRoot,
                    "CANDIDATE91_BACKEND_ROOT_INVALID");
            LegacyInputs inputs = legacyInputs(paths, backend);
            byte[] selectionManifest = readBoundBytes(inputs.selectionManifest(),
                    binding.selectionManifestSha256(),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");
            byte[] holdoutManifest = readBoundBytes(inputs.holdoutManifest(),
                    binding.holdoutManifestSha256(),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");
            byte[] selectionLedger = readBoundBytes(inputs.selectionLedger(),
                    binding.selectionLedgerSha256(),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");
            byte[] diagnostic = readBoundBytes(inputs.diagnostic(),
                    binding.diagnosticSha256(),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");
            requireHash(inputs.shadowXml(), binding.shadowXmlSha256(),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");
            requireHash(inputs.shadowTxt(), binding.shadowTxtSha256(),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");

            JSONObject selectionJson = JSON.parseObject(selectionManifest);
            JSONObject holdoutJson = JSON.parseObject(holdoutManifest);
            requireEquals(binding.selectionDatasetHash(),
                    selectionJson.getString("datasetHash"),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");
            requireEquals(binding.holdoutDatasetHash(),
                    holdoutJson.getString("datasetHash"),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");

            Map<String, String> selectionResourceSha = new TreeMap<>();
            JSONObject selectionResources = selectionJson.getJSONObject("resources");
            for (String logical : List.of("corpus", "pressure", "queries")) {
                JSONObject resource = selectionResources.getJSONObject(logical);
                String file = resource.getString("file");
                Path source = requireRegularFile(inputs.selectionDirectory().resolve(file),
                        "CANDIDATE91_SELECTION_RESOURCE_INVALID");
                String actual = ShadowContractSupport.sha256(source);
                requireEquals(resource.getString("sha256"), actual,
                        "CANDIDATE91_SELECTION_RESOURCE_CHANGED");
                selectionResourceSha.put(file, actual);
            }
            String selectionQrelSha = manifestResourceSha(selectionJson, "qrels");
            Map<String, String> holdoutResourceSha = new TreeMap<>();
            for (String logical : List.of("corpus", "pressure", "qrels", "queries")) {
                JSONObject resource = holdoutJson.getJSONObject("resources")
                        .getJSONObject(logical);
                holdoutResourceSha.put(resource.getString("file"),
                        resource.getString("sha256"));
            }

            byte[] sourceLock = readBoundBytes(sourceLockStaging, null,
                    "CANDIDATE91_SOURCE_LOCK_INVALID");
            requireSourceLock(sourceLock, backend);
            FilesystemPublication publication = new FilesystemPublication(paths);
            publication.copyExact(sourceLock, paths.sourceLock());
            publication.copyExact(selectionManifest, paths.legacySelectionManifest());
            publication.copyExact(holdoutManifest, paths.legacyHoldoutManifest());
            publication.copyExact(selectionLedger, paths.legacySelectionLedger());
            publication.copyExact(diagnostic, paths.legacyDiagnostic());

            String sourceLockSha = ShadowContractSupport.sha256(paths.sourceLock());
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("version", 1);
            manifest.put("selectionManifestSha256", binding.selectionManifestSha256());
            manifest.put("holdoutManifestSha256", binding.holdoutManifestSha256());
            manifest.put("selectionDatasetHash", binding.selectionDatasetHash());
            manifest.put("holdoutDatasetHash", binding.holdoutDatasetHash());
            manifest.put("configHash", binding.configHash());
            manifest.put("selectionLedgerSha256", binding.selectionLedgerSha256());
            manifest.put("diagnosticSha256", binding.diagnosticSha256());
            manifest.put("shadowXmlSha256", binding.shadowXmlSha256());
            manifest.put("shadowTxtSha256", binding.shadowTxtSha256());
            manifest.put("sourceLockSha256", sourceLockSha);
            manifest.put("selectionResourceSha256", selectionResourceSha);
            manifest.put("selectionQrelManifestSha256", selectionQrelSha);
            manifest.put("holdoutResourceManifestSha256", holdoutResourceSha);
            manifest.put("selectionResourceAccessCount", 3);
            manifest.put("selectionQrelResourceAccessCount", 0);
            manifest.put("holdoutResourceAccessCount", 0);
            publication.create(canonicalJsonBytes(manifest), paths.archiveManifest());
            publication.create(ARCHIVE_MARKER_BYTES, paths.archiveMarker());

            String archiveSha = ShadowContractSupport.sha256(paths.archiveManifest());
            String markerSha = ShadowContractSupport.sha256(paths.archiveMarker());
            validateNamespace(paths, RecoveryPhase.ARCHIVE_PUBLISHED);
            return new ArchiveHandle(paths, backend, binding,
                    Map.copyOf(selectionResourceSha), archiveSha, markerSha,
                    sourceLockSha, UUID.randomUUID().toString());
        }
    }

    static PreflightHandle preflight(
            ArchiveHandle archive,
            PostgreSQLContainer<?> container,
            DockerInfrastructureCounters dockerCounters) {
        return preflight(archive, new PostgreSqlPreflightEndpoint(container),
                dockerCounters);
    }

    static PreflightHandle preflight(
            ArchiveHandle archive,
            PreflightEndpoint endpoint,
            DockerInfrastructureCounters dockerCounters) {
        Objects.requireNonNull(archive, "archive").requireLive();
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(dockerCounters, "dockerCounters").validate();
        validateNamespace(archive.paths(), RecoveryPhase.ARCHIVE_PUBLISHED);
        if (!endpoint.running() || !"shadow".equals(endpoint.databaseName())) {
            throw new IllegalStateException("CANDIDATE91_DOCKER_PREFLIGHT_FAILED");
        }
        String identity = requireContainerIdentity(endpoint.containerIdentity());
        PreflightObservation observation;
        try {
            observation = endpoint.execute(PREFLIGHT_DATABASE);
        } catch (Exception failure) {
            throw new IllegalStateException("CANDIDATE91_DOCKER_PREFLIGHT_FAILED", failure);
        }
        if (observation == null || observation.preflightDbCalls() != 8
                || observation.serverVersion() == null
                || !observation.serverVersion().startsWith("16.")
                || !"UTF8".equals(observation.serverEncoding())
                || !"C".equals(observation.lcCollate())
                || !"C".equals(observation.lcCtype())
                || observation.selectionCorpusCount() != 421L) {
            throw new IllegalStateException("CANDIDATE91_DOCKER_PREFLIGHT_FAILED");
        }
        Map<String, Object> artifact = boundEvidence(archive);
        artifact.put("status", "VALID");
        artifact.put("preflightDatabase", PREFLIGHT_DATABASE);
        artifact.put("preflightDbCalls", observation.preflightDbCalls());
        artifact.put("dockerInfrastructureCalls",
                dockerCounters.dockerInfrastructureCalls());
        artifact.put("dockerImagePullCalls", dockerCounters.dockerImagePullCalls());
        artifact.put("holdoutResourceAccessCount", 0);
        artifact.put("addedNetworkCalls", 0);
        byte[] bytes = canonicalJsonBytes(artifact);
        atomicCreate(archive.paths().dockerPreflight(), bytes);
        validateNamespace(archive.paths(), RecoveryPhase.PREFLIGHT_VERIFIED);
        return new PreflightHandle(archive, endpoint.identityToken(), identity,
                ShadowContractSupport.sha256(archive.paths().dockerPreflight()),
                observation.preflightDbCalls(), dockerCounters,
                UUID.randomUUID().toString());
    }

    static void requireSameContainer(
            PreflightHandle handle, PostgreSQLContainer<?> container) {
        Objects.requireNonNull(container, "container");
        requireSameContainer(handle, container, container.getContainerId(),
                container.isRunning());
    }

    static void requireSameContainer(
            PreflightHandle handle,
            Object containerToken,
            String containerIdentity,
            boolean running) {
        Objects.requireNonNull(handle, "handle").requireLive();
        if (handle.containerToken() != containerToken
                || !handle.containerIdentity().equals(containerIdentity)
                || !running) {
            throw new IllegalStateException("CANDIDATE91_CONTAINER_IDENTITY_CHANGED");
        }
    }

    static void publishPreflightFailure(
            ArchiveHandle archive, String errorCode) {
        Objects.requireNonNull(archive, "archive").requireLive();
        validateNamespace(archive.paths(), RecoveryPhase.ARCHIVE_PUBLISHED);
        if (!"CANDIDATE91_DOCKER_PREFLIGHT_FAILED".equals(errorCode)) {
            throw new IllegalArgumentException("CANDIDATE91_ERROR_CODE_INVALID");
        }
        Map<String, Object> artifact = boundEvidence(archive);
        artifact.put("status", "INVALID");
        artifact.put("failureClass", "INFRASTRUCTURE");
        artifact.put("errorCode", errorCode);
        requireSanitized(artifact);
        atomicCreate(archive.paths().preflightFailure(), canonicalJsonBytes(artifact));
        validateNamespace(archive.paths(), RecoveryPhase.PREFLIGHT_FAILED);
    }

    static RecoveryRunHandle beginRecovery(PreflightHandle preflight) {
        Objects.requireNonNull(preflight, "preflight").requireLive();
        validateNamespace(preflight.archive().paths(), RecoveryPhase.PREFLIGHT_VERIFIED);
        Map<String, Object> ledger = recoveryLedger(preflight, RecoveryPhase.RUNNING,
                null, null);
        Path path = preflight.archive().paths().recoveryLedger();
        atomicCreate(path, canonicalJsonBytes(ledger));
        String runningLedgerSha = ShadowContractSupport.sha256(path);
        return new RecoveryRunHandle(preflight, new RunState(
                RecoveryPhase.RUNNING, runningLedgerSha, runningLedgerSha, null),
                UUID.randomUUID().toString());
    }

    static RagCandidate9DiagnosticSupport.RecoveryBinding bindFrozenSelection(
            RecoveryRunHandle handle,
            RagCandidate9DiagnosticSupport.RuntimePaths frozenPaths,
            RagCandidate9DiagnosticSupport.FrozenDataset frozen) {
        Objects.requireNonNull(handle, "handle").requireLive();
        synchronized (WRITER_LOCK) {
            handle.state().require(RecoveryPhase.RUNNING);
            validateCurrentLedger(handle, RecoveryPhase.RUNNING);
            return RagCandidate9DiagnosticSupport.bindRecoveryRun(
                    frozenPaths, frozen, new RecoveryAuthorization(handle));
        }
    }

    static RagCandidate9DiagnosticSupport.EvaluationView
    loadQrelsAfterRankingForRecovery(
            RecoveryRunHandle handle,
            RagCandidate9DiagnosticSupport.RecoveryBinding binding,
            RagCandidate9DiagnosticSupport.FrozenDataset frozen,
            List<? extends Map<String, ?>> rankingCases) {
        Objects.requireNonNull(handle, "handle").requireLive();
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(frozen, "frozen");
        Objects.requireNonNull(rankingCases, "rankingCases");
        synchronized (WRITER_LOCK) {
            handle.state().require(RecoveryPhase.RUNNING);
            validateCurrentLedger(handle, RecoveryPhase.RUNNING);
            requireEquals(handle.state().initialLedgerSha256,
                    binding.recoveryLedgerSha256(),
                    "CANDIDATE91_RECOVERY_BINDING_INVALID");
            String rankingSha = RagCandidate9DiagnosticSupport
                    .validateRankingForRecovery(binding, frozen, rankingCases);
            publishPhase(handle, RecoveryPhase.RUNNING,
                    RecoveryPhase.RANKING_FROZEN, rankingSha);
            RagCandidate9DiagnosticSupport.EvaluationView evaluation =
                    RagCandidate9DiagnosticSupport.loadQrelsAfterRanking(
                            binding, frozen, rankingCases);
            requireEquals(rankingSha, evaluation.rankingPhaseSha256(),
                    "CANDIDATE91_RANKING_SHA_INVALID");
            publishPhase(handle, RecoveryPhase.RANKING_FROZEN,
                    RecoveryPhase.QRELS_LOADED, rankingSha);
            return evaluation;
        }
    }

    private static void publishPhase(
            RecoveryRunHandle handle,
            RecoveryPhase expected,
            RecoveryPhase next,
            String rankingSha256) {
        Objects.requireNonNull(handle, "handle").requireLive();
        if (!validTransition(expected, next)) {
            throw new IllegalArgumentException("CANDIDATE91_PHASE_TRANSITION_INVALID");
        }
        synchronized (WRITER_LOCK) {
            handle.state().require(expected);
            validateCurrentLedger(handle, expected);
            if ((next == RecoveryPhase.RANKING_FROZEN
                    || next == RecoveryPhase.QRELS_LOADED)
                    && !validSha(rankingSha256)) {
                throw new IllegalArgumentException("CANDIDATE91_RANKING_SHA_INVALID");
            }
            Map<String, Object> ledger = recoveryLedger(
                    handle.preflight(), next, rankingSha256, null);
            atomicReplace(handle.preflight().archive().paths().recoveryLedger(),
                    canonicalJsonBytes(ledger));
            handle.state().phase = next;
            handle.state().rankingSha256 = rankingSha256;
            handle.state().ledgerSha256 = ShadowContractSupport.sha256(
                    handle.preflight().archive().paths().recoveryLedger());
        }
    }

    static void publishRecoveryDiagnosticAndComplete(
            RecoveryRunHandle handle,
            RagCandidate9DiagnosticSupport.RecoveryBinding binding,
            RagCandidate9DiagnosticSupport.FrozenDataset frozen,
            Map<String, Object> artifact) {
        Objects.requireNonNull(handle, "handle").requireLive();
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(frozen, "frozen");
        Objects.requireNonNull(artifact, "artifact");
        synchronized (WRITER_LOCK) {
            handle.state().require(RecoveryPhase.QRELS_LOADED);
            validateCurrentLedger(handle, RecoveryPhase.QRELS_LOADED);
            requireEquals(handle.state().initialLedgerSha256,
                    binding.recoveryLedgerSha256(),
                    "CANDIDATE91_RECOVERY_BINDING_INVALID");
            RagCandidate9DiagnosticSupport.validateRecoveryArtifact(
                    binding, frozen, artifact);
            requireEquals("VALID", artifact.get("status"),
                    "CANDIDATE91_ARTIFACT_SHAPE_INVALID");
            if (artifact.get("errorCode") != null
                    || artifact.get("decision") == null) {
                throw new IllegalArgumentException(
                        "CANDIDATE91_ARTIFACT_SHAPE_INVALID");
            }
            String artifactRankingSha = artifactRankingSha(artifact);
            if (!validSha(artifactRankingSha)
                    || !artifactRankingSha.equals(handle.state().rankingSha256)) {
                throw new IllegalArgumentException(
                        "CANDIDATE91_ARTIFACT_RANKING_SHA_INVALID");
            }
            publishAndComplete(handle, artifact);
        }
    }

    static void publishRuntimeInvalidAndComplete(
            RecoveryRunHandle handle, String errorCode) {
        if (errorCode == null || !errorCode.matches("CANDIDATE9_RECOVERY_[A-Z0-9_]+")) {
            throw new IllegalArgumentException("CANDIDATE91_ERROR_CODE_INVALID");
        }
        Map<String, Object> artifact = boundEvidence(handle.preflight().archive());
        artifact.put("status", "INVALID");
        artifact.put("decision", null);
        artifact.put("errorCode", errorCode);
        artifact.put("configHash", handle.preflight().archive().binding().configHash());
        synchronized (WRITER_LOCK) {
            if (handle.phase() != RecoveryPhase.RUNNING
                    && handle.phase() != RecoveryPhase.RANKING_FROZEN
                    && handle.phase() != RecoveryPhase.QRELS_LOADED) {
                throw new IllegalStateException("CANDIDATE91_PHASE_INVALID");
            }
            publishAndComplete(handle, artifact);
        }
    }

    private static void publishAndComplete(
            RecoveryRunHandle handle, Map<String, Object> artifact) {
        validateCurrentLedger(handle, handle.state().phase);
        requireArtifactBinding(handle, artifact);
        Path diagnostic = handle.preflight().archive().paths()
                .recoveryDiagnostic();
        atomicCreate(diagnostic, canonicalJsonBytes(artifact));
        String diagnosticSha = ShadowContractSupport.sha256(diagnostic);
        String rankingSha = artifactRankingSha(artifact);
        if (rankingSha == null) {
            rankingSha = handle.state().rankingSha256;
        }
        Map<String, Object> completed = recoveryLedger(handle.preflight(),
                RecoveryPhase.COMPLETED, rankingSha, diagnosticSha);
        byte[] completedBytes = canonicalJsonBytes(completed);
        atomicReplace(handle.preflight().archive().paths().recoveryLedger(),
                completedBytes);
        requireExactBytes(handle.preflight().archive().paths().recoveryLedger(),
                completedBytes, "CANDIDATE91_RECOVERY_LEDGER_CHANGED");
        handle.state().phase = RecoveryPhase.COMPLETED;
        handle.state().ledgerSha256 = ShadowContractSupport.sha256(
                handle.preflight().archive().paths().recoveryLedger());
        validateNamespace(handle.preflight().archive().paths(),
                RecoveryPhase.COMPLETED);
    }

    private static void validateCurrentLedger(
            RecoveryRunHandle handle, RecoveryPhase expectedPhase) {
        handle.preflight().archive().requireLive();
        validateNamespace(handle.preflight().archive().paths(), expectedPhase);
        Path ledgerPath = handle.preflight().archive().paths().recoveryLedger();
        requireHash(ledgerPath, handle.state().ledgerSha256,
                "CANDIDATE91_RECOVERY_LEDGER_CHANGED");
        JSONObject ledger;
        try {
            ledger = JSON.parseObject(Files.readString(
                    ledgerPath, StandardCharsets.UTF_8));
        } catch (RuntimeException | IOException failure) {
            throw new IllegalStateException(
                    "CANDIDATE91_RECOVERY_LEDGER_CHANGED", failure);
        }
        if (!expectedPhase.name().equals(ledger.getString("phase"))
                || !Objects.equals(handle.state().rankingSha256,
                ledger.getString("rankingPhaseSha256"))) {
            throw new IllegalStateException(
                    "CANDIDATE91_RECOVERY_LEDGER_CHANGED");
        }
    }

    private static void requireArtifactBinding(
            RecoveryRunHandle handle, Map<String, Object> artifact) {
        requireSanitized(artifact);
        LegacyBinding binding = handle.preflight().archive().binding();
        requireEquals(binding.selectionDatasetHash(), artifact.get("datasetHash"),
                "CANDIDATE91_ARTIFACT_BINDING_INVALID");
        requireEquals(binding.configHash(), artifact.get("configHash"),
                "CANDIDATE91_ARTIFACT_BINDING_INVALID");
        requireOneOf(binding.selectionManifestSha256(), artifact,
                "selectionManifestHash", "selectionManifestSha256");
        requireOneOf(binding.holdoutManifestSha256(), artifact,
                "holdoutManifestHash", "holdoutManifestSha256");
    }

    private static String artifactRankingSha(Map<String, Object> artifact) {
        Object summary = artifact.get("summary");
        if (summary instanceof Map<?, ?> values) {
            Object value = values.get("rankingPhaseSha256");
            return validSha(stringOrNull(value)) ? String.valueOf(value) : null;
        }
        return null;
    }

    static void validateNamespace(RecoveryPaths paths, RecoveryPhase phase) {
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(phase, "phase");
        Path root = paths.root();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            if (phase != RecoveryPhase.EMPTY) {
                throw incomplete();
            }
            return;
        }
        if (phase == RecoveryPhase.EMPTY) {
            throw incomplete();
        }
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw incomplete();
        }
        try (var walk = Files.walk(root)) {
            for (Path entry : walk.toList()) {
                if (entry.equals(root)) {
                    continue;
                }
                if (Files.isSymbolicLink(entry)) {
                    throw incomplete();
                }
                Path relative = root.relativize(entry);
                String name = entry.getFileName().toString();
                if (name.endsWith(".tmp") || name.contains(".tmp.")) {
                    throw incomplete();
                }
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    if (!relative.equals(Path.of("archive"))
                            && !relative.equals(Path.of("archive", "legacy"))) {
                        throw incomplete();
                    }
                } else if (!allowedFile(relative)) {
                    throw incomplete();
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("INVALID_INCOMPLETE_RECOVERY", failure);
        }
        requirePhaseFiles(paths, phase);
    }

    private static boolean allowedFile(Path relative) {
        if (relative.getNameCount() == 1) {
            return ROOT_FILES.contains(relative.getFileName().toString());
        }
        if (relative.getNameCount() == 2
                && "archive".equals(relative.getName(0).toString())) {
            return ARCHIVE_FILES.contains(relative.getFileName().toString());
        }
        return relative.getNameCount() == 3
                && "archive".equals(relative.getName(0).toString())
                && "legacy".equals(relative.getName(1).toString())
                && LEGACY_FILES.contains(relative.getFileName().toString());
    }

    private static void requirePhaseFiles(RecoveryPaths paths, RecoveryPhase phase) {
        boolean archive = allRegular(paths.archiveManifest(), paths.sourceLock(),
                paths.archiveMarker(), paths.legacySelectionManifest(),
                paths.legacyHoldoutManifest(), paths.legacySelectionLedger(),
                paths.legacyDiagnostic());
        boolean preflight = regular(paths.dockerPreflight());
        boolean preflightFailure = regular(paths.preflightFailure());
        boolean ledger = regular(paths.recoveryLedger());
        boolean diagnostic = regular(paths.recoveryDiagnostic());
        boolean valid = switch (phase) {
            case EMPTY -> !archive && !preflight && !preflightFailure
                    && !ledger && !diagnostic;
            case ARCHIVE_PUBLISHED -> archive && !preflight && !preflightFailure
                    && !ledger && !diagnostic;
            case PREFLIGHT_VERIFIED -> archive && preflight && !preflightFailure
                    && !ledger && !diagnostic;
            case PREFLIGHT_FAILED -> archive && !preflight && preflightFailure
                    && !ledger && !diagnostic;
            case RUNNING, RANKING_FROZEN, QRELS_LOADED -> archive && preflight
                    && !preflightFailure && ledger && !diagnostic;
            case COMPLETED -> archive && preflight && !preflightFailure
                    && ledger && diagnostic;
        };
        if (!valid) {
            throw incomplete();
        }
    }

    private static LegacyInputs legacyInputs(RecoveryPaths paths, Path backend) {
        Path runtime = paths.runtime();
        Path selectionDirectory = backend.resolve(
                "tests/src/test/resources/rag-eval/candidate9-selection");
        Path reports = backend.resolve("tests/target/surefire-reports");
        return new LegacyInputs(
                runtime.resolve("candidate9-freeze/selection-manifest.json"),
                runtime.resolve("candidate9-freeze/holdout-manifest.json"),
                runtime.resolve("candidate9-freeze/selection-ledger.json"),
                runtime.resolve("candidate9-calibration-diagnostic.json"),
                selectionDirectory,
                reports.resolve("TEST-tech.qiantong.qknow.rag.eval.RagShadowBaselineTest.xml"),
                reports.resolve("tech.qiantong.qknow.rag.eval.RagShadowBaselineTest.txt"));
    }

    private static Map<String, Object> boundEvidence(ArchiveHandle archive) {
        LegacyBinding binding = archive.binding();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("archiveManifestSha256", archive.archiveManifestSha256());
        evidence.put("archiveMarkerSha256", archive.archiveMarkerSha256());
        evidence.put("sourceLockSha256", archive.sourceLockSha256());
        evidence.put("selectionManifestSha256", binding.selectionManifestSha256());
        evidence.put("holdoutManifestSha256", binding.holdoutManifestSha256());
        evidence.put("datasetHash", binding.selectionDatasetHash());
        evidence.put("holdoutDatasetHash", binding.holdoutDatasetHash());
        evidence.put("configHash", binding.configHash());
        return evidence;
    }

    private static Map<String, Object> recoveryLedger(
            PreflightHandle preflight, RecoveryPhase phase,
            String rankingSha256, String artifactSha256) {
        Map<String, Object> ledger = boundEvidence(preflight.archive());
        ledger.put("phase", phase.name());
        ledger.put("dockerPreflightSha256", preflight.preflightSha256());
        ledger.put("containerIdentitySha256", sha256(
                preflight.containerIdentity().getBytes(StandardCharsets.UTF_8)));
        ledger.put("rankingPhaseSha256", rankingSha256);
        ledger.put("artifactSha256", artifactSha256);
        ledger.put("updatedAt", Instant.now().toString());
        return ledger;
    }

    private static boolean validTransition(
            RecoveryPhase expected, RecoveryPhase next) {
        return expected == RecoveryPhase.RUNNING
                && next == RecoveryPhase.RANKING_FROZEN
                || expected == RecoveryPhase.RANKING_FROZEN
                && next == RecoveryPhase.QRELS_LOADED;
    }

    private static String manifestResourceSha(JSONObject manifest, String key) {
        return manifest.getJSONObject("resources").getJSONObject(key)
                .getString("sha256");
    }

    private static void requireSourceLock(byte[] bytes, Path backendRoot) {
        try {
            JSONObject lock = JSON.parseObject(bytes);
            if (lock.getIntValue("version") != 1
                    || lock.getJSONArray("files") == null
                    || lock.getJSONArray("files").size() != SOURCE_LOCK_FILES.size()) {
                throw new IllegalStateException("CANDIDATE91_SOURCE_LOCK_INVALID");
            }
            Set<String> seen = new java.util.LinkedHashSet<>();
            for (int index = 0; index < SOURCE_LOCK_FILES.size(); index++) {
                JSONObject file = lock.getJSONArray("files").getJSONObject(index);
                String logical = file.getString("file");
                String expected = SOURCE_LOCK_FILES.get(index);
                if (!expected.equals(logical) || !seen.add(logical)
                        || !validSha(file.getString("sha256"))) {
                    throw new IllegalStateException("CANDIDATE91_SOURCE_LOCK_INVALID");
                }
                requireHash(backendRoot.resolve(logical), file.getString("sha256"),
                        "CANDIDATE91_SOURCE_LOCK_CHANGED");
            }
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalStateException state) {
                throw state;
            }
            throw new IllegalStateException("CANDIDATE91_SOURCE_LOCK_INVALID", failure);
        }
    }

    private static void requireSanitized(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey())
                        .toLowerCase(java.util.Locale.ROOT)
                        .replace("_", "")
                        .replace("-", "");
                if (FORBIDDEN_ARTIFACT_KEYS.contains(key)) {
                    throw new IllegalArgumentException(
                            "CANDIDATE91_ARTIFACT_SENSITIVE_FIELD");
                }
                requireSanitized(entry.getValue());
            }
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(RagCandidate91RecoverySupport::requireSanitized);
        } else if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                requireSanitized(java.lang.reflect.Array.get(value, index));
            }
        }
    }

    private static byte[] readBoundBytes(
            Path path, String expectedSha, String errorCode) {
        Path regular = requireRegularFile(path, errorCode);
        try {
            byte[] bytes = Files.readAllBytes(regular);
            if (expectedSha != null
                    && !expectedSha.equals(sha256(bytes))) {
                throw new IllegalStateException(errorCode);
            }
            return bytes;
        } catch (IOException failure) {
            throw new IllegalStateException(errorCode, failure);
        }
    }

    private static void requireHash(Path path, String sha, String errorCode) {
        if (!sha.equals(ShadowContractSupport.sha256(requireRegularFile(path, errorCode)))) {
            throw new IllegalStateException(errorCode);
        }
    }

    private static void requireExactBytes(
            Path path, byte[] expected, String errorCode) {
        try {
            Path regular = requireRegularFile(path, errorCode);
            if (!java.util.Arrays.equals(expected, Files.readAllBytes(regular))) {
                throw new IllegalStateException(errorCode);
            }
        } catch (IOException failure) {
            throw new IllegalStateException(errorCode, failure);
        }
    }

    private static Path requireRegularFile(Path path, String errorCode) {
        Path normalized = Objects.requireNonNull(path, "path")
                .toAbsolutePath().normalize();
        requireNoSymlink(normalized.getParent(), normalized, errorCode);
        if (Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(errorCode);
        }
        return normalized;
    }

    private static Path requireDirectory(Path path, String errorCode) {
        Path normalized = Objects.requireNonNull(path, "path")
                .toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(errorCode);
        }
        return normalized;
    }

    private static void requireNoSymlink(
            Path base, Path target, String errorCode) {
        if (base == null) {
            throw new IllegalStateException(errorCode);
        }
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedBase)) {
            throw new IllegalStateException(errorCode);
        }
        Path current = normalizedBase;
        if (Files.isSymbolicLink(current)) {
            throw new IllegalStateException(errorCode);
        }
        for (Path part : normalizedBase.relativize(normalizedTarget)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalStateException(errorCode);
            }
        }
    }

    private static void atomicCreate(Path target, byte[] bytes) {
        Path normalized = target.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalized.getParent());
            if (Files.isSymbolicLink(normalized.getParent())
                    || Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException(normalized.toString());
            }
            Path temp = Files.createTempFile(normalized.getParent(),
                    normalized.getFileName().toString() + ".", ".tmp");
            Files.write(temp, bytes);
            // Reserve the target with CREATE_NEW so concurrent publishers cannot
            // enter the provider-specific ATOMIC_MOVE replacement window.
            Files.createFile(normalized);
            Files.move(temp, normalized, StandardCopyOption.ATOMIC_MOVE);
            if (!java.util.Arrays.equals(bytes, Files.readAllBytes(normalized))) {
                throw new IllegalStateException("CANDIDATE91_ATOMIC_VERIFY_FAILED");
            }
        } catch (AtomicMoveNotSupportedException failure) {
            throw new IllegalStateException("CANDIDATE91_ATOMIC_MOVE_REQUIRED", failure);
        } catch (FileAlreadyExistsException failure) {
            throw new IllegalStateException("CANDIDATE91_ARTIFACT_ALREADY_EXISTS", failure);
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE91_ATOMIC_WRITE_FAILED", failure);
        }
    }

    private static void atomicReplace(Path target, byte[] bytes) {
        Path normalized = requireRegularFile(target,
                "CANDIDATE91_RECOVERY_LEDGER_INVALID");
        try {
            Path temp = Files.createTempFile(normalized.getParent(),
                    normalized.getFileName().toString() + ".", ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, normalized, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            requireExactBytes(normalized, bytes,
                    "CANDIDATE91_ATOMIC_VERIFY_FAILED");
        } catch (AtomicMoveNotSupportedException failure) {
            throw new IllegalStateException("CANDIDATE91_ATOMIC_MOVE_REQUIRED", failure);
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE91_ATOMIC_WRITE_FAILED", failure);
        }
    }

    private static byte[] canonicalJsonBytes(Object value) {
        return JSON.toJSONString(value, JSONWriter.Feature.WriteNulls,
                JSONWriter.Feature.MapSortField).getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void requireExactProperty(String key, String value) {
        if (!value.equals(System.getProperty(key))) {
            throw new IllegalStateException("CANDIDATE91_COMMAND_PROPERTY_INVALID");
        }
    }

    private static void requireAbsent(String key) {
        if (System.getProperty(key) != null) {
            throw new IllegalStateException("CANDIDATE91_COMMAND_PROPERTY_INVALID");
        }
    }

    private static void requireEquals(
            Object expected, Object actual, String errorCode) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException(errorCode);
        }
    }

    private static void requireOneOf(
            String expected, Map<String, Object> artifact, String... keys) {
        for (String key : keys) {
            if (expected.equals(artifact.get(key))) {
                return;
            }
        }
        throw new IllegalStateException("CANDIDATE91_ARTIFACT_BINDING_INVALID");
    }

    private static void requireSingleValue(
            Statement statement, String sql, String expected) throws Exception {
        if (!expected.equals(singleValue(statement, sql))) {
            throw new IllegalStateException("CANDIDATE91_DOCKER_PREFLIGHT_FAILED");
        }
    }

    private static String singleValue(Statement statement, String sql)
            throws Exception {
        try (ResultSet rows = statement.executeQuery(sql)) {
            if (!rows.next()) {
                throw new IllegalStateException("CANDIDATE91_DOCKER_PREFLIGHT_FAILED");
            }
            String value = rows.getString(1);
            if (value == null || value.isBlank() || rows.next()) {
                throw new IllegalStateException("CANDIDATE91_DOCKER_PREFLIGHT_FAILED");
            }
            return value;
        }
    }

    private static String requireContainerIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            throw new IllegalStateException("CANDIDATE91_DOCKER_PREFLIGHT_FAILED");
        }
        return identity;
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean validSha(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    private static boolean regular(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path);
    }

    private static boolean allRegular(Path... paths) {
        for (Path path : paths) {
            if (!regular(path)) {
                return false;
            }
        }
        return true;
    }

    private static IllegalStateException incomplete() {
        return new IllegalStateException("INVALID_INCOMPLETE_RECOVERY");
    }

    enum RecoveryPhase {
        EMPTY,
        ARCHIVE_PUBLISHED,
        PREFLIGHT_VERIFIED,
        PREFLIGHT_FAILED,
        RUNNING,
        RANKING_FROZEN,
        QRELS_LOADED,
        COMPLETED
    }

    record RecoveryPaths(
            Path runtime,
            Path root,
            Path archive,
            Path legacy,
            Path archiveManifest,
            Path sourceLock,
            Path archiveMarker,
            Path legacySelectionManifest,
            Path legacyHoldoutManifest,
            Path legacySelectionLedger,
            Path legacyDiagnostic,
            Path dockerPreflight,
            Path preflightFailure,
            Path recoveryLedger,
            Path recoveryDiagnostic) {
    }

    record LegacyBinding(
            String selectionManifestSha256,
            String holdoutManifestSha256,
            String selectionDatasetHash,
            String holdoutDatasetHash,
            String configHash,
            String selectionLedgerSha256,
            String diagnosticSha256,
            String shadowXmlSha256,
            String shadowTxtSha256) {

        void validate() {
            for (String value : List.of(selectionManifestSha256,
                    holdoutManifestSha256, selectionDatasetHash,
                    holdoutDatasetHash, configHash, selectionLedgerSha256,
                    diagnosticSha256, shadowXmlSha256, shadowTxtSha256)) {
                if (!validSha(value)) {
                    throw new IllegalArgumentException(
                            "CANDIDATE91_LEGACY_BINDING_INVALID");
                }
            }
        }
    }

    record SourceLock(Path path, String sha256, int fileCount, byte[] bytes) {
        SourceLock {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    record ProbeAudit(
            int probeResourceAccessCount,
            int recoveryNamespaceWriteCount,
            int containerStartCount,
            int databaseCreateDropCount,
            int dockerApiCalls) {
    }

    record DockerInfrastructureCounters(
            int dockerInfrastructureCalls,
            int dockerImagePullCalls) {
        void validate() {
            if (dockerInfrastructureCalls < 0 || dockerImagePullCalls < 0
                    || dockerImagePullCalls > dockerInfrastructureCalls) {
                throw new IllegalArgumentException(
                        "CANDIDATE91_DOCKER_COUNTER_INVALID");
            }
        }
    }

    interface PreflightEndpoint {
        Object identityToken();

        String containerIdentity();

        boolean running();

        String databaseName();

        PreflightObservation execute(String preflightDatabase) throws Exception;
    }

    record PreflightObservation(
            int preflightDbCalls,
            String serverVersion,
            String serverEncoding,
            String lcCollate,
            String lcCtype,
            long selectionCorpusCount) {
    }

    record ArchiveHandle(
            RecoveryPaths paths,
            Path backendRoot,
            LegacyBinding binding,
            Map<String, String> selectionResourceSha256,
            String archiveManifestSha256,
            String archiveMarkerSha256,
            String sourceLockSha256,
            String nonce) {
        void requireLive() {
            binding.validate();
            if (!selectionResourceSha256.keySet().equals(Set.of(
                    "corpus.jsonl", "pressure.json", "queries.jsonl"))) {
                throw new IllegalStateException("CANDIDATE91_ARCHIVE_CHANGED");
            }
            RagCandidate9DiagnosticSupport.verifyLockedEvidence();
            LegacyInputs inputs = legacyInputs(paths, backendRoot);
            requireHash(inputs.selectionManifest(),
                    binding.selectionManifestSha256(),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");
            requireHash(inputs.holdoutManifest(), binding.holdoutManifestSha256(),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");
            requireHash(inputs.selectionLedger(), binding.selectionLedgerSha256(),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");
            requireHash(inputs.diagnostic(), binding.diagnosticSha256(),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");
            requireHash(inputs.shadowXml(), binding.shadowXmlSha256(),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");
            requireHash(inputs.shadowTxt(), binding.shadowTxtSha256(),
                    "CANDIDATE91_LEGACY_EVIDENCE_CHANGED");
            selectionResourceSha256.forEach((file, expected) ->
                    requireHash(inputs.selectionDirectory().resolve(file), expected,
                            "CANDIDATE91_SELECTION_RESOURCE_CHANGED"));
            requireHash(paths.archiveManifest(), archiveManifestSha256,
                    "CANDIDATE91_ARCHIVE_CHANGED");
            requireHash(paths.archiveMarker(), archiveMarkerSha256,
                    "CANDIDATE91_ARCHIVE_CHANGED");
            requireHash(paths.sourceLock(), sourceLockSha256,
                    "CANDIDATE91_ARCHIVE_CHANGED");
            requireHash(paths.legacySelectionManifest(),
                    binding.selectionManifestSha256(),
                    "CANDIDATE91_ARCHIVE_CHANGED");
            requireHash(paths.legacyHoldoutManifest(),
                    binding.holdoutManifestSha256(),
                    "CANDIDATE91_ARCHIVE_CHANGED");
            requireHash(paths.legacySelectionLedger(),
                    binding.selectionLedgerSha256(),
                    "CANDIDATE91_ARCHIVE_CHANGED");
            requireHash(paths.legacyDiagnostic(), binding.diagnosticSha256(),
                    "CANDIDATE91_ARCHIVE_CHANGED");
            requireSourceLock(readBoundBytes(paths.sourceLock(), sourceLockSha256,
                    "CANDIDATE91_ARCHIVE_CHANGED"), backendRoot);
            if (nonce == null || nonce.isBlank()) {
                throw incomplete();
            }
        }
    }

    record PreflightHandle(
            ArchiveHandle archive,
            Object containerToken,
            String containerIdentity,
            String preflightSha256,
            int preflightDbCalls,
            DockerInfrastructureCounters dockerCounters,
            String nonce) {
        void requireLive() {
            archive.requireLive();
            requireHash(archive.paths().dockerPreflight(), preflightSha256,
                    "CANDIDATE91_PREFLIGHT_CHANGED");
            if (preflightDbCalls != 8 || nonce == null || nonce.isBlank()) {
                throw incomplete();
            }
        }
    }

    record RecoveryRunHandle(
            PreflightHandle preflight, RunState state, String nonce) {
        void requireLive() {
            preflight.requireLive();
            if (nonce == null || nonce.isBlank()) {
                throw incomplete();
            }
        }

        String ledgerSha256() {
            return state.ledgerSha256;
        }

        RecoveryPhase phase() {
            return state.phase;
        }
    }

    static final class RecoveryAuthorization {
        private final RecoveryRunHandle handle;

        private RecoveryAuthorization(RecoveryRunHandle handle) {
            this.handle = Objects.requireNonNull(handle, "handle");
        }

        synchronized String claimBinding(Object frozen) {
            handle.requireLive();
            if (handle.state().frozenBinding != null) {
                throw new IllegalStateException(
                        "CANDIDATE91_RECOVERY_BINDING_INVALID");
            }
            handle.state().frozenBinding = Objects.requireNonNull(frozen, "frozen");
            return handle.state().initialLedgerSha256;
        }

        synchronized void requireBound(
                String initialLedgerSha256, Object frozen) {
            handle.requireLive();
            if (!handle.state().initialLedgerSha256.equals(initialLedgerSha256)
                    || handle.state().frozenBinding != frozen
                    || handle.phase() != RecoveryPhase.RUNNING
                    && handle.phase() != RecoveryPhase.RANKING_FROZEN
                    && handle.phase() != RecoveryPhase.QRELS_LOADED) {
                throw new IllegalStateException(
                        "CANDIDATE91_RECOVERY_BINDING_INVALID");
            }
        }

        synchronized void requirePhase(
                String initialLedgerSha256, Object frozen,
                RecoveryPhase expectedPhase) {
            requireBound(initialLedgerSha256, frozen);
            if (handle.phase() != expectedPhase) {
                throw new IllegalStateException(
                        "CANDIDATE91_RECOVERY_BINDING_INVALID");
            }
        }
    }

    private static final class RunState {
        private RecoveryPhase phase;
        private final String initialLedgerSha256;
        private String ledgerSha256;
        private String rankingSha256;
        private Object frozenBinding;

        private RunState(
                RecoveryPhase phase, String initialLedgerSha256,
                String ledgerSha256, String rankingSha256) {
            this.phase = phase;
            this.initialLedgerSha256 = initialLedgerSha256;
            this.ledgerSha256 = ledgerSha256;
            this.rankingSha256 = rankingSha256;
        }

        private void require(RecoveryPhase expected) {
            if (phase != expected) {
                throw new IllegalStateException("CANDIDATE91_PHASE_INVALID");
            }
        }
    }

    private record LegacyInputs(
            Path selectionManifest,
            Path holdoutManifest,
            Path selectionLedger,
            Path diagnostic,
            Path selectionDirectory,
            Path shadowXml,
            Path shadowTxt) {
    }

    private static final class PostgreSqlPreflightEndpoint
            implements PreflightEndpoint {
        private final PostgreSQLContainer<?> container;

        private PostgreSqlPreflightEndpoint(PostgreSQLContainer<?> container) {
            this.container = Objects.requireNonNull(container, "container");
        }

        @Override
        public Object identityToken() {
            return container;
        }

        @Override
        public String containerIdentity() {
            return container.getContainerId();
        }

        @Override
        public boolean running() {
            return container.isRunning();
        }

        @Override
        public String databaseName() {
            return container.getDatabaseName();
        }

        @Override
        public PreflightObservation execute(String preflightDatabase)
                throws Exception {
            int dbCalls = 0;
            try (Connection connection = container.createConnection("")) {
                connection.setAutoCommit(true);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE " + preflightDatabase);
                    dbCalls++;
                    statement.execute("DROP DATABASE " + preflightDatabase);
                    dbCalls++;
                    requireSingleValue(statement, "SELECT 1", "1");
                    dbCalls++;
                    String serverVersion = singleValue(
                            statement, "SHOW server_version");
                    dbCalls++;
                    String serverEncoding = singleValue(
                            statement, "SHOW server_encoding");
                    dbCalls++;
                    String lcCollate = singleValue(statement, "SHOW lc_collate");
                    dbCalls++;
                    String lcCtype = singleValue(statement, "SHOW lc_ctype");
                    dbCalls++;
                    String corpusCount = singleValue(statement, """
                            SELECT count(*)
                            FROM kmc_document_segment s
                            JOIN kmc_document d ON d.id = s.document_id
                            WHERE d.knowledge_base_id = 10140000
                            """);
                    dbCalls++;
                    return new PreflightObservation(dbCalls, serverVersion,
                            serverEncoding, lcCollate, lcCtype,
                            Long.parseLong(corpusCount));
                }
            }
        }
    }

    private static final class FilesystemPublication {
        private final RecoveryPaths paths;

        private FilesystemPublication(RecoveryPaths paths) {
            this.paths = paths;
        }

        private void copyExact(byte[] sourceBytes, Path target) {
            create(sourceBytes, target);
        }

        private void create(byte[] bytes, Path target) {
            if (!target.startsWith(paths.root())) {
                throw new IllegalStateException("CANDIDATE91_ARCHIVE_PATH_INVALID");
            }
            atomicCreate(target, bytes);
            if (!sha256(bytes).equals(ShadowContractSupport.sha256(target))) {
                throw new IllegalStateException("CANDIDATE91_ARCHIVE_COPY_INVALID");
            }
        }
    }
}
