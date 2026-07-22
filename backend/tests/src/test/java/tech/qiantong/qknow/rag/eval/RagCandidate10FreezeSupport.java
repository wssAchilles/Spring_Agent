package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertNative;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankRequestContext;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankerProvider;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Candidate 10 single-publication freeze lifecycle. Test source only. */
final class RagCandidate10FreezeSupport {

    static final String FREEZE_PROPERTY = "rag.eval.candidate10.freeze";
    static final String DIAGNOSTIC_PROPERTY = "rag.eval.candidate10.diagnostic";
    static final String DIAGNOSTIC_ARM_PROPERTY =
            "rag.eval.candidate10.diagnostic-arm";
    static final String CAUSAL_SCOPE =
            "post-fusion-content-only-colbert-snapshot";
    static final String ALGORITHM =
            "bounded-exact-window-tail-admission-v1";

    private static final String GENERATOR = "candidate10-static-fixture-v1";
    private static final int GENERATOR_VERSION = 1;
    private static final long SELECTION_SEED = 20260725L;
    private static final long HOLDOUT_SEED = 20260726L;
    private static final String SOURCE_LOCK_FILE =
            "candidate10-source-lock.json";
    private static final String SELECTION_MANIFEST_FILE =
            "selection-manifest.json";
    private static final String HOLDOUT_MANIFEST_FILE =
            "holdout-manifest.json";
    private static final String INCOMPLETE_FILE =
            "candidate10-freeze.incomplete.json";
    private static final Set<String> RESOURCE_KEYS = Set.of(
            "corpus", "queries", "qrels", "pressure");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Object FREEZE_LOCK = new Object();

    private static final Path QUALIFIED_JAVA_HOME = Path.of(
            "/Users/achilles/.jdks/candidate10-temurin-17.0.19+10/Contents/Home");
    private static final Path NO_NATIVE_PATH = Path.of(
            "/Users/achilles/Documents/许子祺/Agent/backend/tests/target/"
                    + "rag-eval/no-native");
    private static final String JDK_ARCHIVE_SHA256 =
            "8fa1eff40bb637a33613b2ccb8b12c70dc3661cc22cf8e784943715769a05336";
    private static final String JDK_RELEASE_SHA256 =
            "d68c3995604bcac4e0386781fdfb8ee268ab7369cd167dca84edad3f88dd8c04";
    private static final String JDK_JAVA_SHA256 =
            "de7045580bdfc8281d64f5db2b32b8c2eb07c1327803cea08abed826f2661216";
    private static final String JDK_JAVAC_SHA256 =
            "9bf49c1393c540a4d8ae868aec5f7a2f4d438b89e78c4403db39af0130fb2c21";
    private static final String JDK_LIBMANAGEMENT_SHA256 =
            "5ea3b6c3d6b00bc6c435edd0803fadb0a5c4997d2a5476f7cc40d9aeb4e07ef7";

    private static final Map<String, String> LOCKED_CANDIDATE10_SOURCES = Map.of(
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10DiagnosticSupport.java",
            "802df58abd94b00b32c9c056bc119888eff72353070c3a7ff09168049805fa6e",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10NonDockerContractTest.java",
            "213d63841ae3ae4e734e8703c88d9f83ab6e96e811b156c19b459ec9659c5d20",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10CounterfactualContractTest.java",
            "7c34e501106e987d519776c9c2a756c1ecdc8e258f4763cffcb1a3de0e255a55");

    private static final List<String> SOURCE_LOCK_FILES = List.of(
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10DiagnosticSupport.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10NonDockerContractTest.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10CounterfactualContractTest.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10EnvironmentQualificationTest.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10FixtureGenerator.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10FreezeSupport.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10DiagnosticStageSupport.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10ThirdStageContractTest.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10FormalEvidenceTest.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/RagRerankService.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/KeywordRetriever.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/RagContextBuilder.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorer.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertNative.java",
            "qknow-module-kmc/qknow-module-kmc-api/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/api/rag/RagFallbackMonitor.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/rerank/"
                    + "DashScopeRerankerProvider.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/rerank/"
                    + "DeterministicRerankerProvider.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/rerank/"
                    + "LocalBgeRerankerProvider.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/rerank/"
                    + "LocalRerankerProvider.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/rerank/"
                    + "OnnxRerankerProvider.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/rerank/"
                    + "RerankRequestContext.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/model/"
                    + "RetrievalResult.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/model/RagResult.java",
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/model/QueryIntent.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagMetrics.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagEvaluationDataset.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagEvaluationDatasetLoader.java",
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "ShadowContractSupport.java",
            "tests/pom.xml");

    private static final List<String> FEATURE_SOURCE_FILES = List.of(
            SOURCE_LOCK_FILES.get(0),
            SOURCE_LOCK_FILES.get(9),
            SOURCE_LOCK_FILES.get(10),
            SOURCE_LOCK_FILES.get(11),
            SOURCE_LOCK_FILES.get(12),
            SOURCE_LOCK_FILES.get(13),
            SOURCE_LOCK_FILES.get(14),
            SOURCE_LOCK_FILES.get(16),
            SOURCE_LOCK_FILES.get(20),
            SOURCE_LOCK_FILES.get(21),
            SOURCE_LOCK_FILES.get(22),
            SOURCE_LOCK_FILES.get(23),
            SOURCE_LOCK_FILES.get(24));

    private static final List<String> PROVIDER_INVENTORY = List.of(
            "tech.qiantong.qknow.module.kmc.service.rag.rerank."
                    + "DashScopeRerankerProvider",
            "tech.qiantong.qknow.module.kmc.service.rag.rerank."
                    + "DeterministicRerankerProvider",
            "tech.qiantong.qknow.module.kmc.service.rag.rerank."
                    + "LocalBgeRerankerProvider",
            "tech.qiantong.qknow.module.kmc.service.rag.rerank."
                    + "LocalRerankerProvider",
            "tech.qiantong.qknow.module.kmc.service.rag.rerank."
                    + "OnnxRerankerProvider");

    private static final List<HistoricalLock> HISTORICAL_LOCKS = List.of(
            new HistoricalLock(
                    "candidate10-runtime-recovery-lock-table",
                    Path.of("/Users/achilles/.codex/attachments/"
                            + "1cc6b050-b5b4-4904-8303-22c0ec1c4323/"
                            + "pasted-text.txt"),
                    "be9979efdcc53b6c78ce1eab7fae1355bd6b0baac28a70565c94b72977a02d0e"),
            new HistoricalLock(
                    "candidate10-runtime-completion-lock-table",
                    Path.of("/Users/achilles/.codex/attachments/"
                            + "79bc8984-9513-4874-a724-b836b521ea52/"
                            + "pasted-text.txt"),
                    "178820c3af4a66e9f1af3bbdad7d7dd9c71f78236313f83ffb557805a6bfec53"));

    private static final List<ReportSpec> PREFLIGHT_REPORTS = List.of(
            new ReportSpec(
                    "tests/target/surefire-reports/TEST-tech.qiantong.qknow.rag.eval."
                            + "RagCandidate10ThirdStageContractTest-"
                            + "candidate10-third-stage.xml",
                    "tech.qiantong.qknow.rag.eval."
                            + "RagCandidate10ThirdStageContractTest",
                    "allContracts",
                    "candidate10-third-stage"),
            new ReportSpec(
                    "tests/target/surefire-reports/TEST-tech.qiantong.qknow.rag.eval."
                            + "RagCandidate10NonDockerContractTest-"
                            + "candidate10-support.xml",
                    "tech.qiantong.qknow.rag.eval."
                            + "RagCandidate10NonDockerContractTest",
                    "allContracts",
                    "candidate10-support"),
            new ReportSpec(
                    "tests/target/surefire-reports/TEST-tech.qiantong.qknow.rag.eval."
                            + "RagCandidate10CounterfactualContractTest-"
                            + "candidate10-counterfactual.xml",
                    "tech.qiantong.qknow.rag.eval."
                            + "RagCandidate10CounterfactualContractTest",
                    "boundedAdmissionReplacesOnlyTail",
                    "candidate10-counterfactual"));

    private RagCandidate10FreezeSupport() {
    }

    static RuntimePaths formalPaths() {
        Path current = Path.of(System.getProperty("user.dir", "."))
                .toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml"))
                && Files.isDirectory(current.resolve("tests"))) {
            return paths(current);
        }
        if ("tests".equals(Objects.toString(current.getFileName(), ""))
                && Files.isRegularFile(current.getParent().resolve("pom.xml"))) {
            return paths(current.getParent());
        }
        Path nested = current.resolve("backend");
        if (Files.isRegularFile(nested.resolve("pom.xml"))
                && Files.isDirectory(nested.resolve("tests"))) {
            return paths(nested);
        }
        throw new IllegalStateException("CANDIDATE10_BACKEND_ROOT_NOT_FOUND");
    }

    static RuntimePaths paths(Path backendRoot) {
        Path backend = Objects.requireNonNull(backendRoot, "backendRoot")
                .toAbsolutePath().normalize();
        Path tests = backend.resolve("tests");
        Path selection = tests.resolve(
                "src/test/resources/rag-eval/candidate10-selection");
        Path holdout = tests.resolve("candidate10-holdout");
        Path runtime = tests.resolve("target/rag-eval");
        Path freeze = runtime.resolve("candidate10-freeze");
        Path reports = tests.resolve("target/surefire-reports");
        return new RuntimePaths(
                backend,
                tests,
                selection,
                holdout,
                freeze,
                freeze.resolve(SELECTION_MANIFEST_FILE),
                freeze.resolve(HOLDOUT_MANIFEST_FILE),
                freeze.resolve(SOURCE_LOCK_FILE),
                freeze.resolve("selection-ledger.json"),
                runtime.resolve("candidate10-calibration-diagnostic.json"),
                runtime.resolve(INCOMPLETE_FILE),
                selection.resolveSibling("candidate10-selection.staging"),
                selection.resolveSibling("candidate10-selection.claim"),
                holdout.resolveSibling("candidate10-holdout.staging"),
                holdout.resolveSibling("candidate10-holdout.claim"),
                freeze.resolveSibling("candidate10-freeze.staging"),
                freeze.resolveSibling("candidate10-freeze.claim"),
                tests.resolve("src/test/resources/rag-eval/candidate10-holdout"),
                tests.resolve("candidate10-selection"),
                reports.resolve("TEST-tech.qiantong.qknow.rag.eval."
                        + "RagCandidate10FormalEvidenceTest-candidate10-freeze.xml"),
                reports.resolve("TEST-tech.qiantong.qknow.rag.eval."
                        + "RagCandidate10FormalEvidenceTest-candidate10-diagnostic.xml"));
    }

    static ConfiguredRuntime configureRuntime(EntryPoint entryPoint) {
        Objects.requireNonNull(entryPoint, "entryPoint");
        Map<String, String> properties = requireEligibilityProperties(entryPoint);
        JdkIdentity jdk = requireJdkIdentity();

        ColbertScorer.ColbertConfig colbert = new ColbertScorer.ColbertConfig();
        colbert.setEnabled(Boolean.parseBoolean(
                properties.get("hermes.rag.colbert.enabled")));
        colbert.setNgramSize(Integer.parseInt(
                properties.get("hermes.rag.colbert.ngram-size")));
        colbert.setDimensions(Integer.parseInt(
                properties.get("hermes.rag.colbert.dimensions")));
        colbert.setMaxTokensPerDoc(Integer.parseInt(
                properties.get("hermes.rag.colbert.max-tokens-per-doc")));
        colbert.setEmbeddingPlatform(
                properties.get("hermes.rag.colbert.embedding-platform"));
        colbert.setEmbeddingBaseUrl(
                properties.get("hermes.rag.colbert.embedding-base-url"));
        colbert.setEmbeddingApiKey(
                properties.get("hermes.rag.colbert.embedding-api-key"));
        colbert.setEmbeddingModel(
                properties.get("hermes.rag.colbert.embedding-model"));

        RagRerankService rerankService = new RagRerankService();
        ReflectionTestUtils.setField(
                rerankService,
                "identifierConsistencyEnabled",
                Boolean.parseBoolean(properties.get(
                        "qknow.rag.rerank.identifier-consistency-enabled")));
        RerankRequestContext context = RerankRequestContext.builder()
                .query("candidate10-config-probe")
                .providerName(null)
                .modelName(null)
                .build();
        requireObjectConfiguration(rerankService, colbert, context);

        if (ColbertNative.isAvailable()) {
            throw new IllegalStateException("CANDIDATE10_CONFIG_INVALID");
        }
        PROVIDER_INVENTORY.forEach(RagCandidate10FreezeSupport::requireProviderClass);

        RuntimePaths paths = formalPaths();
        Map<String, String> featureSourceHashes = hashFiles(
                paths.backendRoot(), FEATURE_SOURCE_FILES,
                "CANDIDATE10_SOURCE_LOCK_INVALID");
        Map<String, Object> config = buildConfig(
                properties, colbert, context, featureSourceHashes, jdk);
        String configHash = sha256(canonicalJsonBytes(config));
        return new ConfiguredRuntime(
                entryPoint, rerankService, colbert, context,
                Map.copyOf(config), configHash, jdk);
    }

    static FreezePlan prepareFreeze(
            RuntimePaths paths,
            RagCandidate10FixtureGenerator.GeneratedSplit selection,
            RagCandidate10FixtureGenerator.GeneratedSplit holdout,
            ConfiguredRuntime runtime) {
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(holdout, "holdout");
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.entryPoint() != EntryPoint.FREEZE) {
            throw new IllegalArgumentException("CANDIDATE10_CONFIG_INVALID");
        }
        requireFormalPreflight(paths);
        DatasetManifest selectionManifest = manifest(selection, false);
        DatasetManifest holdoutManifest = manifest(holdout, true);
        byte[] selectionManifestBytes = canonicalJsonBytes(
                manifestMap(selectionManifest));
        byte[] holdoutManifestBytes = canonicalJsonBytes(
                manifestMap(holdoutManifest));

        Map<String, String> lockedFiles = hashFiles(
                paths.backendRoot(), SOURCE_LOCK_FILES,
                "CANDIDATE10_SOURCE_LOCK_INVALID");
        Map<String, String> reports = verifyPreflightReports(paths.backendRoot());
        Map<String, Object> sourceLock = sourceLockMap(
                runtime,
                lockedFiles,
                reports,
                selectionManifest,
                selectionManifestBytes,
                holdoutManifest,
                holdoutManifestBytes);
        byte[] sourceLockBytes = canonicalJsonBytes(sourceLock);

        Map<String, byte[]> selectionFiles = resourceFiles(selection);
        Map<String, byte[]> holdoutFiles = resourceFiles(holdout);
        Map<String, byte[]> freezeFiles = new LinkedHashMap<>();
        freezeFiles.put(SELECTION_MANIFEST_FILE, selectionManifestBytes);
        freezeFiles.put(HOLDOUT_MANIFEST_FILE, holdoutManifestBytes);
        freezeFiles.put(SOURCE_LOCK_FILE, sourceLockBytes);
        return new FreezePlan(
                paths,
                selectionFiles,
                holdoutFiles,
                freezeFiles,
                selectionManifest,
                holdoutManifest,
                runtime.configHash(),
                sha256(sourceLockBytes),
                runtime);
    }

    static FrozenEvidence publishFreeze(FreezePlan plan) {
        Objects.requireNonNull(plan, "plan");
        synchronized (FREEZE_LOCK) {
            RuntimePaths paths = plan.paths();
            requireFormalPreflight(paths);
            requirePlanStillBound(plan);
            boolean mutated = false;
            String phase = "CLAIMING";
            try {
                createClaim(paths.selectionClaim());
                mutated = true;
                createClaim(paths.holdoutClaim());
                createClaim(paths.freezeClaim());

                phase = "STAGING";
                stageDirectory(paths.selectionStaging(), plan.selectionFiles());
                stageDirectory(paths.holdoutStaging(), plan.holdoutFiles());
                stageDirectory(paths.freezeStaging(), plan.freezeFiles());

                phase = "SELECTION_PUBLISHED";
                atomicPublishDirectory(
                        paths.selectionStaging(), paths.selectionDirectory());
                phase = "HOLDOUT_PUBLISHED";
                atomicPublishDirectory(
                        paths.holdoutStaging(), paths.holdoutDirectory());
                phase = "FREEZE_COMMITTED";
                atomicPublishDirectory(
                        paths.freezeStaging(), paths.freezeDirectory());

                verifyPublishedFreezeWhileClaimed(plan);
                phase = "CLAIMS_RELEASED";
                Files.delete(paths.selectionClaim());
                Files.delete(paths.holdoutClaim());
                Files.delete(paths.freezeClaim());
                return openDiagnosticEvidence(paths, plan.runtime());
            } catch (RuntimeException | IOException failure) {
                if (mutated) {
                    throw markIncomplete(paths, phase, failure);
                }
                if (failure instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                throw new IllegalStateException(
                        "CANDIDATE10_FREEZE_WRITE_FAILED", failure);
            }
        }
    }

    static FrozenEvidence verifyPublishedFreeze(FreezePlan plan) {
        Objects.requireNonNull(plan, "plan");
        RuntimePaths paths = plan.paths();
        requireDirectoryBytes(
                paths.selectionDirectory(), plan.selectionFiles());
        requireDirectoryBytes(
                paths.holdoutDirectory(), plan.holdoutFiles());
        requireDirectoryBytes(paths.freezeDirectory(), plan.freezeFiles());
        if (Files.exists(paths.incompleteMarker(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE10_FREEZE_INCOMPLETE");
        }
        return openDiagnosticEvidence(paths, plan.runtime());
    }

    private static FrozenEvidence verifyPublishedFreezeWhileClaimed(
            FreezePlan plan) {
        RuntimePaths paths = plan.paths();
        requireDirectoryBytes(
                paths.selectionDirectory(), plan.selectionFiles());
        requireDirectoryBytes(
                paths.holdoutDirectory(), plan.holdoutFiles());
        requireDirectoryBytes(paths.freezeDirectory(), plan.freezeFiles());
        return openDiagnosticEvidence(paths, plan.runtime(), true);
    }

    /**
     * Validates only manifests, source/config locks, source files and reports.
     * It deliberately does not open either split's raw resources.
     */
    static FrozenEvidence openDiagnosticEvidence(RuntimePaths paths) {
        return openDiagnosticEvidence(
                paths, configureRuntime(EntryPoint.DIAGNOSTIC));
    }

    static FrozenEvidence openDiagnosticEvidence(
            RuntimePaths paths, ConfiguredRuntime runtime) {
        return openDiagnosticEvidence(paths, runtime, false);
    }

    private static FrozenEvidence openDiagnosticEvidence(
            RuntimePaths paths,
            ConfiguredRuntime runtime,
            boolean publicationClaimsAllowed) {
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(runtime, "runtime");
        if (Files.exists(paths.incompleteMarker(), LinkOption.NOFOLLOW_LINKS)
                || !publicationClaimsAllowed && hasStagingOrClaim(paths)) {
            throw new IllegalStateException("CANDIDATE10_FREEZE_INCOMPLETE");
        }
        byte[] selectionBytes = readCanonicalJson(
                paths.selectionManifest(), "CANDIDATE10_FIXTURE_INVALID");
        byte[] holdoutBytes = readCanonicalJson(
                paths.holdoutManifest(), "CANDIDATE10_FIXTURE_INVALID");
        byte[] sourceLockBytes = readCanonicalJson(
                paths.sourceLock(), "CANDIDATE10_SOURCE_LOCK_INVALID");
        DatasetManifest selection = parseManifest(selectionBytes, false);
        DatasetManifest holdout = parseManifest(holdoutBytes, true);
        SourceLockSnapshot lock = requireSourceLock(
                sourceLockBytes,
                paths,
                runtime,
                selection,
                sha256(selectionBytes),
                holdout,
                sha256(holdoutBytes));
        return new FrozenEvidence(
                paths,
                selection,
                holdout,
                sha256(selectionBytes),
                sha256(holdoutBytes),
                selection.datasetHash(),
                holdout.datasetHash(),
                runtime.configHash(),
                sha256(sourceLockBytes),
                selection.resources(),
                lock.lockedFiles(),
                lock.reportHashes(),
                sourceLockBytes);
    }

    static void requireSourceLockUnchanged(FrozenEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        requireHash(evidence.paths().selectionManifest(),
                evidence.selectionManifestSha256(),
                "CANDIDATE10_SOURCE_LOCK_INVALID");
        requireHash(evidence.paths().holdoutManifest(),
                evidence.holdoutManifestSha256(),
                "CANDIDATE10_SOURCE_LOCK_INVALID");
        requireHash(evidence.paths().sourceLock(),
                evidence.sourceLockSha256(),
                "CANDIDATE10_SOURCE_LOCK_INVALID");
        requireFileHashes(evidence.paths().backendRoot(),
                evidence.lockedFiles(), "CANDIDATE10_SOURCE_LOCK_INVALID");
        requireFileHashes(evidence.paths().backendRoot(),
                evidence.reportHashes(), "CANDIDATE10_SOURCE_LOCK_INVALID");
        verifyHistoricalLocks();
        requireJdkIdentity();
    }

    static byte[] canonicalJsonBytes(Object value) {
        String json = JSON.toJSONString(
                canonicalJsonValue(Objects.requireNonNull(value, "value")),
                JSONWriter.Feature.WriteNulls,
                JSONWriter.Feature.MapSortField);
        return (json + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static Object canonicalJsonValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nested) -> sorted.put(
                    (String) key, canonicalJsonValue(nested)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(RagCandidate10FreezeSupport::canonicalJsonValue)
                    .toList();
        }
        return value;
    }

    static String sha256(byte[] bytes) {
        return ShadowContractSupport.sha256(
                Objects.requireNonNull(bytes, "bytes"));
    }

    static String sha256(Path path) {
        return ShadowContractSupport.sha256(
                Objects.requireNonNull(path, "path"));
    }

    private static Map<String, String> requireEligibilityProperties(
            EntryPoint entryPoint) {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put(FREEZE_PROPERTY,
                entryPoint == EntryPoint.FREEZE ? "true" : "false");
        expected.put(DIAGNOSTIC_PROPERTY,
                entryPoint == EntryPoint.DIAGNOSTIC ? "true" : "false");
        expected.put(DIAGNOSTIC_ARM_PROPERTY, "true");
        expected.put("rag.eval.candidate10.environment-qualification", "false");
        expected.put("rag.eval.shadow", "false");
        expected.put("rag.eval.shadow.compare-stable", "false");
        expected.put("rag.eval.identifier.diagnostic", "false");
        expected.put("rag.eval.candidate2.diagnostic", "false");
        expected.put("rag.eval.candidate3.diagnostic", "false");
        expected.put("rag.eval.candidate4.diagnostic", "false");
        expected.put("rag.eval.candidate5.diagnostic", "false");
        expected.put("rag.eval.candidate6.diagnostic", "false");
        expected.put("rag.eval.candidate8.diagnostic", "false");
        expected.put("rag.eval.candidate9.diagnostic", "false");
        expected.put("rag.eval.candidate9.recovery", "false");
        expected.put("rag.eval.promotion", "false");
        expected.put("rag.eval.live", "false");
        expected.put("qknow.rag.keyword.identifier-aware", "false");
        expected.put(
                "qknow.rag.rerank.identifier-consistency-enabled", "true");
        expected.put("qknow.rag.local-reranker.enabled", "false");
        expected.put("qknow.rag.onnx-reranker.enabled", "false");
        expected.put("hermes.rag.colbert.enabled", "true");
        expected.put("hermes.rag.colbert.ngram-size", "3");
        expected.put("hermes.rag.colbert.dimensions", "64");
        expected.put("hermes.rag.colbert.max-tokens-per-doc", "128");
        expected.put("hermes.rag.colbert.embedding-platform", "");
        expected.put("hermes.rag.colbert.embedding-base-url", "");
        expected.put("hermes.rag.colbert.embedding-api-key", "");
        expected.put("hermes.rag.colbert.embedding-model", "");
        expected.put("hermes.rag.context.max-bytes", "20000");
        expected.put("hermes.rag.context.max-tokens", "0");
        expected.put("forkCount", "1");
        expected.put("reuseForks", "false");
        expected.put("file.encoding", "UTF-8");
        expected.put("user.timezone", "UTC");
        expected.put("user.language", "en");
        expected.put("user.country", "US");
        expected.put("user.script", "");
        expected.put("user.variant", "");
        expected.put("qknow.native.lib.dir", "");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!entry.getValue().equals(System.getProperty(entry.getKey()))) {
                throw new IllegalStateException(
                        "CANDIDATE10_CONFIG_INVALID");
            }
        }
        String libraryPath = System.getProperty("java.library.path");
        if (libraryPath == null
                || !NO_NATIVE_PATH.equals(Path.of(libraryPath).normalize())) {
            throw new IllegalStateException("CANDIDATE10_CONFIG_INVALID");
        }
        expected.put("java.library.path", libraryPath);
        String javaHome = System.getenv("JAVA_HOME");
        String path = System.getenv("PATH");
        String expectedHome = QUALIFIED_JAVA_HOME.toString();
        if (!expectedHome.equals(javaHome)
                || path == null
                || !path.startsWith(expectedHome + "/bin:")) {
            throw new IllegalStateException("CANDIDATE10_CONFIG_INVALID");
        }
        if (!"en-US".equals(Locale.getDefault().toLanguageTag())
                || !"UTC".equals(ZoneId.systemDefault().getId())) {
            throw new IllegalStateException("CANDIDATE10_CONFIG_INVALID");
        }
        return Map.copyOf(expected);
    }

    private static void requireObjectConfiguration(
            RagRerankService service,
            ColbertScorer.ColbertConfig colbert,
            RerankRequestContext context) {
        if (!Boolean.TRUE.equals(ReflectionTestUtils.getField(
                service, "identifierConsistencyEnabled"))
                || !colbert.isEnabled()
                || colbert.getNgramSize() != 3
                || colbert.getDimensions() != 64
                || colbert.getMaxTokensPerDoc() != 128
                || !isEmpty(colbert.getEmbeddingPlatform())
                || !isEmpty(colbert.getEmbeddingBaseUrl())
                || !isEmpty(colbert.getEmbeddingApiKey())
                || !isEmpty(colbert.getEmbeddingModel())
                || context.getProviderName() != null
                || context.getModelName() != null) {
            throw new IllegalStateException("CANDIDATE10_CONFIG_INVALID");
        }
    }

    private static Map<String, Object> buildConfig(
            Map<String, String> properties,
            ColbertScorer.ColbertConfig colbert,
            RerankRequestContext context,
            Map<String, String> featureSourceHashes,
            JdkIdentity jdk) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("version", 1);
        config.put("causalScope", CAUSAL_SCOPE);
        config.put("algorithm", ALGORITHM);
        config.put("candidate10IdentifierMatchPolicy",
                "all-visible-identifiers-v1");
        config.put("candidate3IdentifierMatchPolicy",
                "any-visible-identifier-v1");
        config.put("candidate10ProductionProperty", "ABSENT");
        config.put("topK", 10);
        config.put("prefix", 30);
        config.put("windowStartInclusive", 31);
        config.put("windowEndInclusive", 60);
        config.put("keywordSqlLimit", 500);
        config.put("keywordJavaLimit", 50);
        config.put("candidate3", "identifier-consistency-real-path-v1");
        config.put("eligibility", Map.ofEntries(
                Map.entry("candidate10DiagnosticArm", true),
                Map.entry("identifierConsistencyEnabled", true),
                Map.entry("keywordIdentifierAware", false),
                Map.entry("localRerankerEnabled", false),
                Map.entry("onnxRerankerEnabled", false),
                Map.entry("remoteRerankerEnabled", false),
                Map.entry("legacyDiagnosticsEnabled", false),
                Map.entry("promotionEnabled", false),
                Map.entry("liveEvaluationEnabled", false)));
        config.put("colbert", Map.of(
                "enabled", colbert.isEnabled(),
                "dimensions", colbert.getDimensions(),
                "ngramSize", colbert.getNgramSize(),
                "maxTokensPerDocument", colbert.getMaxTokensPerDoc(),
                "implementation", "hash-vector-deterministic-v1",
                "embeddingPlatformPresent", false,
                "embeddingBaseUrlPresent", false,
                "embeddingApiKeyPresent", false,
                "embeddingModelPresent", false));
        config.put("kbRemoteReranking", Map.of(
                "enabled", false,
                "providerPresent", context.getProviderName() != null,
                "modelPresent", context.getModelName() != null));
        config.put("providerInventory", PROVIDER_INVENTORY.stream()
                .sorted().toList());
        config.put("context", Map.of(
                "maxBytes", Integer.parseInt(
                        properties.get("hermes.rag.context.max-bytes")),
                "maxTokens", Integer.parseInt(
                        properties.get("hermes.rag.context.max-tokens")),
                "includeMetadata", true));
        config.put("fork", Map.of("count", 1, "reuse", false));
        config.put("locale", "en-US");
        config.put("timezone", "UTC");
        config.put("native", Map.of(
                "available", false,
                "nativeDirectoryPresent", false,
                "javaLibraryPath", NO_NATIVE_PATH.toString()));
        config.put("jdk", jdk.asMap());
        config.put("featureSourceSha256", new TreeMap<>(featureSourceHashes));
        config.put("historicalEvidenceMode", "LOCK_TABLE_ONLY");
        config.put("historicalArtifactLocalState", "CLEANED");
        config.put("failurePolicy", Map.of(
                "ranking", "CANDIDATE10_RANKING_INVALID",
                "sourceLock", "CANDIDATE10_SOURCE_LOCK_INVALID",
                "config", "CANDIDATE10_CONFIG_INVALID",
                "fixture", "CANDIDATE10_FIXTURE_INVALID",
                "qrelAccess", "CANDIDATE10_QREL_ACCESS_INVALID",
                "runtime", "CANDIDATE10_RUNTIME_INVALID"));
        config.put("evaluationPolicy", "qrel-after-ranking-single-read-v1");
        return Map.copyOf(config);
    }

    private static JdkIdentity requireJdkIdentity() {
        Path home = Path.of(System.getProperty("java.home", ""))
                .toAbsolutePath().normalize();
        if (!QUALIFIED_JAVA_HOME.equals(home)
                || !"17.0.19".equals(System.getProperty("java.version"))
                || !"17.0.19+10".equals(
                System.getProperty("java.runtime.version"))
                || !"Eclipse Adoptium".equals(System.getProperty("java.vendor"))
                || !"aarch64".equals(System.getProperty("os.arch"))) {
            throw new IllegalStateException("CANDIDATE10_CONFIG_INVALID");
        }
        requireHash(home.resolve("release"), JDK_RELEASE_SHA256,
                "CANDIDATE10_CONFIG_INVALID");
        requireHash(home.resolve("bin/java"), JDK_JAVA_SHA256,
                "CANDIDATE10_CONFIG_INVALID");
        requireHash(home.resolve("bin/javac"), JDK_JAVAC_SHA256,
                "CANDIDATE10_CONFIG_INVALID");
        requireHash(home.resolve("lib/libmanagement.dylib"),
                JDK_LIBMANAGEMENT_SHA256, "CANDIDATE10_CONFIG_INVALID");
        return new JdkIdentity(
                home.toString(),
                "17.0.19",
                "17.0.19+10",
                "Eclipse Adoptium",
                "aarch64",
                JDK_ARCHIVE_SHA256,
                JDK_RELEASE_SHA256,
                JDK_JAVA_SHA256,
                JDK_JAVAC_SHA256,
                JDK_LIBMANAGEMENT_SHA256,
                "JCDTMS22B4");
    }

    private static DatasetManifest manifest(
            RagCandidate10FixtureGenerator.GeneratedSplit split,
            boolean holdout) {
        long expectedSeed = holdout ? HOLDOUT_SEED : SELECTION_SEED;
        RagCandidate10FixtureGenerator.Split expectedSplit = holdout
                ? RagCandidate10FixtureGenerator.Split.HOLDOUT
                : RagCandidate10FixtureGenerator.Split.SELECTION;
        if (split.split() != expectedSplit
                || split.seed() != expectedSeed
                || !validSha(split.fixtureSpecHash())
                || !validSha(split.datasetHash())
                || !RESOURCE_KEYS.equals(split.resources().keySet())) {
            throw new IllegalArgumentException("CANDIDATE10_FIXTURE_INVALID");
        }
        Map<String, ResourceBinding> resources = new TreeMap<>();
        split.resources().forEach((key, resource) -> {
            if (!validResourceName(key, resource.fileName())
                    || !resource.sha256().equals(sha256(resource.bytes()))) {
                throw new IllegalArgumentException(
                        "CANDIDATE10_FIXTURE_INVALID");
            }
            resources.put(key, new ResourceBinding(
                    resource.fileName(), resource.sha256()));
        });
        Map<String, Object> counts = new LinkedHashMap<>();
        split.counts().forEach(counts::put);
        return new DatasetManifest(
                holdout ? "candidate10-holdout" : "candidate10-selection",
                holdout ? "FROZEN_NOT_BLIND" : "FROZEN",
                GENERATOR,
                GENERATOR_VERSION,
                expectedSeed,
                Map.copyOf(resources),
                Map.copyOf(counts),
                Map.copyOf(split.structure()),
                split.fixtureSpecHash(),
                split.datasetHash());
    }

    private static boolean validResourceName(String key, String file) {
        return switch (key) {
            case "corpus" -> "corpus.jsonl".equals(file);
            case "queries" -> "queries.jsonl".equals(file);
            case "qrels" -> "qrels.tsv".equals(file);
            case "pressure" -> "pressure.json".equals(file);
            default -> false;
        };
    }

    private static Map<String, Object> manifestMap(DatasetManifest manifest) {
        Map<String, Object> resources = new TreeMap<>();
        manifest.resources().forEach((key, value) -> resources.put(key, Map.of(
                "file", value.file(), "sha256", value.sha256())));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("version", 1);
        map.put("dataset", manifest.dataset());
        map.put("freezeStatus", manifest.freezeStatus());
        map.put("generator", manifest.generator());
        map.put("generatorVersion", manifest.generatorVersion());
        map.put("seed", manifest.seed());
        map.put("causalScope", CAUSAL_SCOPE);
        map.put("resources", resources);
        map.put("counts", manifest.counts());
        map.put("structure", manifest.structure());
        map.put("fixtureSpecHash", manifest.fixtureSpecHash());
        map.put("datasetHash", manifest.datasetHash());
        return map;
    }

    private static Map<String, byte[]> resourceFiles(
            RagCandidate10FixtureGenerator.GeneratedSplit split) {
        Map<String, byte[]> files = new TreeMap<>();
        split.resources().values().forEach(resource ->
                files.put(resource.fileName(), resource.bytes()));
        if (files.size() != RESOURCE_KEYS.size()) {
            throw new IllegalArgumentException("CANDIDATE10_FIXTURE_INVALID");
        }
        return immutableBytes(files);
    }

    private static Map<String, Object> sourceLockMap(
            ConfiguredRuntime runtime,
            Map<String, String> lockedFiles,
            Map<String, String> reportHashes,
            DatasetManifest selection,
            byte[] selectionManifestBytes,
            DatasetManifest holdout,
            byte[] holdoutManifestBytes) {
        Map<String, Object> lock = new LinkedHashMap<>();
        lock.put("version", 1);
        lock.put("algorithm", ALGORITHM);
        lock.put("causalScope", CAUSAL_SCOPE);
        lock.put("candidate10ProductionProperty", "ABSENT");
        lock.put("historicalEvidenceMode", "LOCK_TABLE_ONLY");
        lock.put("historicalArtifactLocalState", "CLEANED");
        lock.put("historicalEvidence", historicalEvidenceMaps());
        lock.put("jdk", runtime.jdk().asMap());
        lock.put("config", runtime.config());
        lock.put("configHash", runtime.configHash());
        lock.put("files", boundFileMaps(lockedFiles));
        lock.put("preflightReports", boundFileMaps(reportHashes));
        lock.put("selection", splitBinding(
                selection, sha256(selectionManifestBytes)));
        lock.put("holdout", splitBinding(
                holdout, sha256(holdoutManifestBytes)));
        return lock;
    }

    private static Map<String, Object> splitBinding(
            DatasetManifest manifest, String manifestSha) {
        Map<String, Object> resources = new TreeMap<>();
        manifest.resources().forEach((key, value) -> resources.put(key, Map.of(
                "file", value.file(), "sha256", value.sha256())));
        return Map.of(
                "datasetHash", manifest.datasetHash(),
                "fixtureSpecHash", manifest.fixtureSpecHash(),
                "manifestSha256", manifestSha,
                "resources", resources);
    }

    private static List<Map<String, Object>> boundFileMaps(
            Map<String, String> bindings) {
        return bindings.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.<String, Object>of(
                        "file", entry.getKey(), "sha256", entry.getValue()))
                .toList();
    }

    private static List<Map<String, Object>> historicalEvidenceMaps() {
        return HISTORICAL_LOCKS.stream().map(lock -> Map.<String, Object>of(
                "id", lock.id(),
                "path", lock.path().toString(),
                "sha256", lock.sha256())).toList();
    }

    private static void requireFormalPreflight(RuntimePaths paths) {
        requireFixedFormalPaths(paths);
        verifyLockedCandidate10Sources(paths.backendRoot());
        verifyHistoricalLocks();
        requireJdkIdentity();
        ensureRuntimeRoot(paths);
        requireFormalArtifactsAbsent(paths);
        requireRegularDirectory(paths.selectionDirectory().getParent(),
                "CANDIDATE10_FREEZE_PATH_INVALID");
        requireRegularDirectory(paths.holdoutDirectory().getParent(),
                "CANDIDATE10_FREEZE_PATH_INVALID");
    }

    private static void requireFormalArtifactsAbsent(RuntimePaths paths) {
        requireAbsent(paths.selectionDirectory());
        requireAbsent(paths.holdoutDirectory());
        requireAbsent(paths.freezeDirectory());
        requireAbsent(paths.ledger());
        requireAbsent(paths.diagnostic());
        requireAbsent(paths.incompleteMarker());
        requireAbsent(paths.selectionStaging());
        requireAbsent(paths.selectionClaim());
        requireAbsent(paths.holdoutStaging());
        requireAbsent(paths.holdoutClaim());
        requireAbsent(paths.freezeStaging());
        requireAbsent(paths.freezeClaim());
        requireAbsent(paths.unexpectedHoldoutDirectory());
        requireAbsent(paths.unexpectedSelectionDirectory());
        requireAbsent(paths.diagnosticReport());
    }

    private static Path ensureRuntimeRoot(RuntimePaths paths) {
        Path runtime = Objects.requireNonNull(paths, "paths")
                .freezeDirectory().getParent().toAbsolutePath().normalize();
        requireRegularDirectory(runtime.getParent(),
                "CANDIDATE10_FREEZE_PATH_INVALID");
        if (!Files.exists(runtime, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(runtime)) {
            try {
                Files.createDirectory(runtime);
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "CANDIDATE10_FREEZE_PATH_INVALID", failure);
            }
        }
        return requireRegularDirectory(
                runtime, "CANDIDATE10_FREEZE_PATH_INVALID");
    }

    private static void requireFixedFormalPaths(RuntimePaths paths) {
        RuntimePaths expected = formalPaths();
        if (!expected.equals(paths)) {
            throw new IllegalArgumentException("CANDIDATE10_FREEZE_PATH_INVALID");
        }
        requireNoSymlink(paths.backendRoot(), paths.testsDirectory());
        requireNoSymlink(paths.backendRoot(), paths.selectionDirectory());
        requireNoSymlink(paths.backendRoot(), paths.holdoutDirectory());
        requireNoSymlink(paths.backendRoot(), paths.freezeDirectory());
        requireNoSymlink(paths.backendRoot(), paths.diagnostic());
    }

    private static void verifyLockedCandidate10Sources(Path backendRoot) {
        LOCKED_CANDIDATE10_SOURCES.forEach((file, expected) ->
                requireHash(backendRoot.resolve(file), expected,
                        "CANDIDATE10_SOURCE_LOCK_INVALID"));
    }

    private static void verifyHistoricalLocks() {
        for (HistoricalLock lock : HISTORICAL_LOCKS) {
            requireHash(lock.path(), lock.sha256(),
                    "CANDIDATE10_SOURCE_LOCK_INVALID");
        }
    }

    private static Map<String, String> verifyPreflightReports(Path backendRoot) {
        Map<String, String> result = new TreeMap<>();
        for (ReportSpec report : PREFLIGHT_REPORTS) {
            Path path = backendRoot.resolve(report.file()).normalize();
            result.put(report.file(), requireSurefireSuccess(path, report));
        }
        return Map.copyOf(result);
    }

    private static String requireSurefireSuccess(Path path, ReportSpec spec) {
        Path regular = requireRegularFile(path,
                "CANDIDATE10_SOURCE_LOCK_INVALID");
        try {
            byte[] bytes = Files.readAllBytes(regular);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(
                    javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities", false);
            var suite = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bytes))
                    .getDocumentElement();
            String expectedClassName = spec.className()
                    + "(" + spec.reportNameSuffix() + ")";
            if (!"testsuite".equals(suite.getTagName())
                    || !"1".equals(suite.getAttribute("tests"))
                    || !"0".equals(suite.getAttribute("failures"))
                    || !"0".equals(suite.getAttribute("errors"))
                    || !"0".equals(suite.getAttribute("skipped"))
                    || !expectedClassName.equals(suite.getAttribute("name"))) {
                throw new IllegalStateException(
                        "CANDIDATE10_SOURCE_LOCK_INVALID");
            }
            var cases = suite.getElementsByTagName("testcase");
            if (cases.getLength() != 1) {
                throw new IllegalStateException(
                        "CANDIDATE10_SOURCE_LOCK_INVALID");
            }
            var testCase = (org.w3c.dom.Element) cases.item(0);
            if (!expectedClassName.equals(testCase.getAttribute("classname"))
                    || !spec.methodName().equals(testCase.getAttribute("name"))) {
                throw new IllegalStateException(
                        "CANDIDATE10_SOURCE_LOCK_INVALID");
            }
            return sha256(bytes);
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "CANDIDATE10_SOURCE_LOCK_INVALID", failure);
        }
    }

    private static void requirePlanStillBound(FreezePlan plan) {
        verifyLockedCandidate10Sources(plan.paths().backendRoot());
        verifyHistoricalLocks();
        requireJdkIdentity();
        Map<String, String> currentFiles = hashFiles(
                plan.paths().backendRoot(), SOURCE_LOCK_FILES,
                "CANDIDATE10_SOURCE_LOCK_INVALID");
        Map<String, String> currentReports = verifyPreflightReports(
                plan.paths().backendRoot());
        JSONObject lock = parseCanonicalObject(
                plan.freezeFiles().get(SOURCE_LOCK_FILE),
                "CANDIDATE10_SOURCE_LOCK_INVALID");
        if (!currentFiles.equals(parseBindings(lock.getJSONArray("files")))
                || !currentReports.equals(parseBindings(
                lock.getJSONArray("preflightReports")))) {
            throw new IllegalStateException("CANDIDATE10_SOURCE_LOCK_INVALID");
        }
    }

    private static void createClaim(Path claim) throws IOException {
        requireAbsent(claim);
        Files.write(claim, new byte[0],
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void stageDirectory(
            Path staging, Map<String, byte[]> files) throws IOException {
        requireAbsent(staging);
        Files.createDirectory(staging);
        if (Files.isSymbolicLink(staging)) {
            throw new IllegalStateException("CANDIDATE10_FREEZE_PATH_INVALID");
        }
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            Path target = staging.resolve(entry.getKey()).normalize();
            if (!staging.equals(target.getParent())) {
                throw new IllegalStateException(
                        "CANDIDATE10_FREEZE_PATH_INVALID");
            }
            Files.write(target, entry.getValue(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            requireExactBytes(target, entry.getValue(),
                    "CANDIDATE10_FREEZE_VERIFY_FAILED");
        }
    }

    private static void atomicPublishDirectory(
            Path staging, Path target) throws IOException {
        requireAbsent(target);
        if (!staging.getParent().equals(target.getParent())) {
            throw new IllegalStateException("CANDIDATE10_FREEZE_PATH_INVALID");
        }
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException failure) {
            throw new IllegalStateException(
                    "CANDIDATE10_ATOMIC_MOVE_REQUIRED", failure);
        } catch (FileAlreadyExistsException failure) {
            throw new IllegalStateException(
                    "CANDIDATE10_FREEZE_ALREADY_EXISTS", failure);
        }
    }

    private static IllegalStateException markIncomplete(
            RuntimePaths paths, String phase, Throwable failure) {
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("version", 1);
        marker.put("status", "INVALID");
        marker.put("failedPhase", phase);
        marker.put("errorCode", "CANDIDATE10_FREEZE_INCOMPLETE");
        try {
            Files.write(paths.incompleteMarker(), canonicalJsonBytes(marker),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception markerFailure) {
            failure.addSuppressed(markerFailure);
        }
        return new IllegalStateException(
                "CANDIDATE10_FREEZE_INCOMPLETE", failure);
    }

    private static void requireDirectoryBytes(
            Path directory, Map<String, byte[]> expected) {
        Path regular = requireRegularDirectory(
                directory, "CANDIDATE10_FREEZE_VERIFY_FAILED");
        try (var entries = Files.list(regular)) {
            List<String> actualNames = entries
                    .map(path -> path.getFileName().toString())
                    .sorted().toList();
            List<String> expectedNames = expected.keySet().stream()
                    .sorted().toList();
            if (!expectedNames.equals(actualNames)) {
                throw new IllegalStateException(
                        "CANDIDATE10_FREEZE_VERIFY_FAILED");
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "CANDIDATE10_FREEZE_VERIFY_FAILED", failure);
        }
        expected.forEach((name, bytes) -> requireExactBytes(
                regular.resolve(name), bytes,
                "CANDIDATE10_FREEZE_VERIFY_FAILED"));
    }

    private static DatasetManifest parseManifest(
            byte[] bytes, boolean holdout) {
        JSONObject json = parseCanonicalObject(
                bytes, "CANDIDATE10_FIXTURE_INVALID");
        String expectedDataset = holdout
                ? "candidate10-holdout" : "candidate10-selection";
        String expectedStatus = holdout ? "FROZEN_NOT_BLIND" : "FROZEN";
        long expectedSeed = holdout ? HOLDOUT_SEED : SELECTION_SEED;
        if (json.getIntValue("version") != 1
                || !expectedDataset.equals(json.getString("dataset"))
                || !expectedStatus.equals(json.getString("freezeStatus"))
                || !GENERATOR.equals(json.getString("generator"))
                || json.getIntValue("generatorVersion") != GENERATOR_VERSION
                || json.getLongValue("seed") != expectedSeed
                || !CAUSAL_SCOPE.equals(json.getString("causalScope"))
                || !validSha(json.getString("fixtureSpecHash"))
                || !validSha(json.getString("datasetHash"))) {
            throw new IllegalStateException("CANDIDATE10_FIXTURE_INVALID");
        }
        JSONObject resourcesJson = json.getJSONObject("resources");
        if (resourcesJson == null
                || !RESOURCE_KEYS.equals(resourcesJson.keySet())) {
            throw new IllegalStateException("CANDIDATE10_FIXTURE_INVALID");
        }
        Map<String, ResourceBinding> resources = new TreeMap<>();
        resourcesJson.forEach((key, raw) -> {
            JSONObject resource = (JSONObject) raw;
            String file = resource.getString("file");
            String sha = resource.getString("sha256");
            if (!validResourceName(key, file) || !validSha(sha)) {
                throw new IllegalStateException(
                        "CANDIDATE10_FIXTURE_INVALID");
            }
            resources.put(key, new ResourceBinding(file, sha));
        });
        JSONObject counts = json.getJSONObject("counts");
        JSONObject structure = json.getJSONObject("structure");
        if (counts == null || structure == null) {
            throw new IllegalStateException("CANDIDATE10_FIXTURE_INVALID");
        }
        return new DatasetManifest(
                expectedDataset,
                expectedStatus,
                GENERATOR,
                GENERATOR_VERSION,
                expectedSeed,
                Map.copyOf(resources),
                Map.copyOf(counts),
                Map.copyOf(structure),
                json.getString("fixtureSpecHash"),
                json.getString("datasetHash"));
    }

    private static SourceLockSnapshot requireSourceLock(
            byte[] bytes,
            RuntimePaths paths,
            ConfiguredRuntime runtime,
            DatasetManifest selection,
            String selectionManifestSha,
            DatasetManifest holdout,
            String holdoutManifestSha) {
        JSONObject lock = parseCanonicalObject(
                bytes, "CANDIDATE10_SOURCE_LOCK_INVALID");
        if (lock.getIntValue("version") != 1
                || !ALGORITHM.equals(lock.getString("algorithm"))
                || !CAUSAL_SCOPE.equals(lock.getString("causalScope"))
                || !"ABSENT".equals(
                lock.getString("candidate10ProductionProperty"))
                || !"LOCK_TABLE_ONLY".equals(
                lock.getString("historicalEvidenceMode"))
                || !"CLEANED".equals(
                lock.getString("historicalArtifactLocalState"))
                || !runtime.configHash().equals(lock.getString("configHash"))) {
            throw new IllegalStateException("CANDIDATE10_SOURCE_LOCK_INVALID");
        }
        JSONObject config = lock.getJSONObject("config");
        if (config == null
                || !runtime.configHash().equals(
                sha256(canonicalJsonBytes(config)))) {
            throw new IllegalStateException("CANDIDATE10_CONFIG_INVALID");
        }
        Map<String, String> lockedFiles = parseBindings(
                lock.getJSONArray("files"));
        Map<String, String> reportHashes = parseBindings(
                lock.getJSONArray("preflightReports"));
        if (!lockedFiles.keySet().equals(new LinkedHashSet<>(SOURCE_LOCK_FILES))
                || !reportHashes.keySet().equals(PREFLIGHT_REPORTS.stream()
                .map(ReportSpec::file).collect(
                        java.util.stream.Collectors.toSet()))) {
            throw new IllegalStateException("CANDIDATE10_SOURCE_LOCK_INVALID");
        }
        requireFileHashes(paths.backendRoot(), lockedFiles,
                "CANDIDATE10_SOURCE_LOCK_INVALID");
        requireFileHashes(paths.backendRoot(), reportHashes,
                "CANDIDATE10_SOURCE_LOCK_INVALID");
        requireHistoricalBindings(lock.getJSONArray("historicalEvidence"));
        requireJdkBinding(lock.getJSONObject("jdk"), runtime.jdk());
        requireSplitBinding(lock.getJSONObject("selection"), selection,
                selectionManifestSha);
        requireSplitBinding(lock.getJSONObject("holdout"), holdout,
                holdoutManifestSha);
        return new SourceLockSnapshot(
                Map.copyOf(lockedFiles), Map.copyOf(reportHashes));
    }

    private static void requireSplitBinding(
            JSONObject binding,
            DatasetManifest manifest,
            String manifestSha) {
        if (binding == null
                || !manifest.datasetHash().equals(
                binding.getString("datasetHash"))
                || !manifest.fixtureSpecHash().equals(
                binding.getString("fixtureSpecHash"))
                || !manifestSha.equals(binding.getString("manifestSha256"))) {
            throw new IllegalStateException("CANDIDATE10_SOURCE_LOCK_INVALID");
        }
        JSONObject resources = binding.getJSONObject("resources");
        if (resources == null
                || !canonicalJsonEqual(resources,
                splitBinding(manifest, manifestSha).get("resources"))) {
            throw new IllegalStateException("CANDIDATE10_SOURCE_LOCK_INVALID");
        }
    }

    private static void requireHistoricalBindings(JSONArray bindings) {
        if (bindings == null || bindings.size() != HISTORICAL_LOCKS.size()) {
            throw new IllegalStateException("CANDIDATE10_SOURCE_LOCK_INVALID");
        }
        for (int index = 0; index < HISTORICAL_LOCKS.size(); index++) {
            HistoricalLock expected = HISTORICAL_LOCKS.get(index);
            JSONObject actual = bindings.getJSONObject(index);
            if (!expected.id().equals(actual.getString("id"))
                    || !expected.path().toString().equals(
                    actual.getString("path"))
                    || !expected.sha256().equals(actual.getString("sha256"))) {
                throw new IllegalStateException(
                        "CANDIDATE10_SOURCE_LOCK_INVALID");
            }
        }
        verifyHistoricalLocks();
    }

    private static void requireJdkBinding(
            JSONObject actual, JdkIdentity expected) {
        if (actual == null
                || !canonicalJsonEqual(actual, expected.asMap())) {
            throw new IllegalStateException("CANDIDATE10_CONFIG_INVALID");
        }
        requireJdkIdentity();
    }

    private static Map<String, String> parseBindings(JSONArray bindings) {
        if (bindings == null) {
            throw new IllegalStateException("CANDIDATE10_SOURCE_LOCK_INVALID");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Object raw : bindings) {
            JSONObject binding = (JSONObject) raw;
            String file = binding.getString("file");
            String sha = binding.getString("sha256");
            if (file == null || file.isBlank() || !validSha(sha)
                    || result.putIfAbsent(file, sha) != null) {
                throw new IllegalStateException(
                        "CANDIDATE10_SOURCE_LOCK_INVALID");
            }
        }
        return Map.copyOf(result);
    }

    private static JSONObject parseCanonicalObject(
            byte[] bytes, String errorCode) {
        try {
            JSONObject json = JSON.parseObject(bytes);
            if (json == null
                    || !Arrays.equals(bytes, canonicalJsonBytes(json))) {
                throw new IllegalStateException(errorCode);
            }
            return json;
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IllegalStateException(errorCode, failure);
        }
    }

    private static byte[] readCanonicalJson(Path path, String errorCode) {
        try {
            return readBoundBytes(path, null, errorCode);
        } catch (RuntimeException failure) {
            throw failure;
        }
    }

    private static Map<String, String> hashFiles(
            Path root, List<String> files, String errorCode) {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String file : files) {
            Path path = root.resolve(file).normalize();
            if (!path.startsWith(root.toAbsolutePath().normalize())) {
                throw new IllegalStateException(errorCode);
            }
            hashes.put(file, sha256(requireRegularFile(path, errorCode)));
        }
        return Map.copyOf(hashes);
    }

    private static void requireFileHashes(
            Path root, Map<String, String> hashes, String errorCode) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        hashes.forEach((file, expected) -> {
            Path path = normalizedRoot.resolve(file).normalize();
            if (!path.startsWith(normalizedRoot)) {
                throw new IllegalStateException(errorCode);
            }
            requireHash(path, expected, errorCode);
        });
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

    private static void requireHash(
            Path path, String expected, String errorCode) {
        if (!validSha(expected)
                || !expected.equals(sha256(requireRegularFile(path, errorCode)))) {
            throw new IllegalStateException(errorCode);
        }
    }

    private static void requireExactBytes(
            Path path, byte[] expected, String errorCode) {
        try {
            if (!Arrays.equals(expected,
                    Files.readAllBytes(requireRegularFile(path, errorCode)))) {
                throw new IllegalStateException(errorCode);
            }
        } catch (IOException failure) {
            throw new IllegalStateException(errorCode, failure);
        }
    }

    private static Path requireRegularFile(Path path, String errorCode) {
        Path normalized = Objects.requireNonNull(path, "path")
                .toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(errorCode);
        }
        return normalized;
    }

    private static Path requireRegularDirectory(Path path, String errorCode) {
        Path normalized = Objects.requireNonNull(path, "path")
                .toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(errorCode);
        }
        return normalized;
    }

    private static void requireNoSymlink(Path base, Path target) {
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedBase)
                || Files.isSymbolicLink(normalizedBase)) {
            throw new IllegalStateException("CANDIDATE10_FREEZE_PATH_INVALID");
        }
        Path current = normalizedBase;
        boolean missing = false;
        for (Path part : normalizedBase.relativize(normalizedTarget)) {
            current = current.resolve(part);
            boolean exists = Files.exists(current, LinkOption.NOFOLLOW_LINKS);
            if (exists && (missing || Files.isSymbolicLink(current))) {
                throw new IllegalStateException(
                        "CANDIDATE10_FREEZE_PATH_INVALID");
            }
            missing |= !exists;
        }
    }

    private static void requireAbsent(Path path) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IllegalStateException("CANDIDATE10_FREEZE_ALREADY_EXISTS");
        }
    }

    private static boolean hasStagingOrClaim(RuntimePaths paths) {
        return List.of(
                        paths.selectionStaging(), paths.selectionClaim(),
                        paths.holdoutStaging(), paths.holdoutClaim(),
                        paths.freezeStaging(), paths.freezeClaim())
                .stream().anyMatch(path ->
                        Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                                || Files.isSymbolicLink(path));
    }

    private static void requireProviderClass(String className) {
        try {
            Class<?> type = Class.forName(className, false,
                    RagCandidate10FreezeSupport.class.getClassLoader());
            if (!RerankerProvider.class.isAssignableFrom(type)) {
                throw new IllegalStateException("CANDIDATE10_CONFIG_INVALID");
            }
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException(
                    "CANDIDATE10_CONFIG_INVALID", failure);
        }
    }

    private static boolean canonicalJsonEqual(Object left, Object right) {
        return Arrays.equals(canonicalJsonBytes(left), canonicalJsonBytes(right));
    }

    private static boolean validSha(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    private static boolean isEmpty(String value) {
        return value != null && value.isEmpty();
    }

    private static Map<String, byte[]> immutableBytes(
            Map<String, byte[]> source) {
        Map<String, byte[]> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "file"),
                Objects.requireNonNull(value, "bytes").clone()));
        return java.util.Collections.unmodifiableMap(copy);
    }

    enum EntryPoint {
        FREEZE,
        DIAGNOSTIC
    }

    record RuntimePaths(
            Path backendRoot,
            Path testsDirectory,
            Path selectionDirectory,
            Path holdoutDirectory,
            Path freezeDirectory,
            Path selectionManifest,
            Path holdoutManifest,
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
            Path unexpectedHoldoutDirectory,
            Path unexpectedSelectionDirectory,
            Path freezeReport,
            Path diagnosticReport) {
    }

    record ConfiguredRuntime(
            EntryPoint entryPoint,
            RagRerankService rerankService,
            ColbertScorer.ColbertConfig colbertConfig,
            RerankRequestContext requestContext,
            Map<String, Object> config,
            String configHash,
            JdkIdentity jdk) {

        ConfiguredRuntime {
            config = Map.copyOf(config);
            if (!validSha(configHash)) {
                throw new IllegalArgumentException("CANDIDATE10_CONFIG_INVALID");
            }
        }
    }

    record JdkIdentity(
            String javaHome,
            String javaVersion,
            String runtimeVersion,
            String vendor,
            String arch,
            String archiveSha256,
            String releaseSha256,
            String javaSha256,
            String javacSha256,
            String libmanagementSha256,
            String signingTeamIdentifier) {

        Map<String, Object> asMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("javaHome", javaHome);
            value.put("javaVersion", javaVersion);
            value.put("runtimeVersion", runtimeVersion);
            value.put("vendor", vendor);
            value.put("arch", arch);
            value.put("archiveSha256", archiveSha256);
            value.put("releaseSha256", releaseSha256);
            value.put("javaSha256", javaSha256);
            value.put("javacSha256", javacSha256);
            value.put("libmanagementSha256", libmanagementSha256);
            value.put("signingTeamIdentifier", signingTeamIdentifier);
            return Map.copyOf(value);
        }
    }

    record ResourceBinding(String file, String sha256) {
        ResourceBinding {
            Objects.requireNonNull(file, "file");
            if (!validSha(sha256)) {
                throw new IllegalArgumentException("CANDIDATE10_FIXTURE_INVALID");
            }
        }
    }

    record DatasetManifest(
            String dataset,
            String freezeStatus,
            String generator,
            int generatorVersion,
            long seed,
            Map<String, ResourceBinding> resources,
            Map<String, Object> counts,
            Map<String, Object> structure,
            String fixtureSpecHash,
            String datasetHash) {

        DatasetManifest {
            resources = Map.copyOf(resources);
            counts = Map.copyOf(counts);
            structure = Map.copyOf(structure);
        }
    }

    record FreezePlan(
            RuntimePaths paths,
            Map<String, byte[]> selectionFiles,
            Map<String, byte[]> holdoutFiles,
            Map<String, byte[]> freezeFiles,
            DatasetManifest selectionManifest,
            DatasetManifest holdoutManifest,
            String configHash,
            String sourceLockSha256,
            ConfiguredRuntime runtime) {

        FreezePlan {
            selectionFiles = immutableBytes(selectionFiles);
            holdoutFiles = immutableBytes(holdoutFiles);
            freezeFiles = immutableBytes(freezeFiles);
            if (!validSha(configHash) || !validSha(sourceLockSha256)) {
                throw new IllegalArgumentException(
                        "CANDIDATE10_SOURCE_LOCK_INVALID");
            }
        }
    }

    record FrozenEvidence(
            RuntimePaths paths,
            DatasetManifest selectionManifest,
            DatasetManifest holdoutManifest,
            String selectionManifestSha256,
            String holdoutManifestSha256,
            String selectionDatasetHash,
            String holdoutDatasetHash,
            String configHash,
            String sourceLockSha256,
            Map<String, ResourceBinding> selectionResources,
            Map<String, String> lockedFiles,
            Map<String, String> reportHashes,
            byte[] sourceLockBytes) {

        FrozenEvidence {
            selectionResources = Map.copyOf(selectionResources);
            lockedFiles = Map.copyOf(lockedFiles);
            reportHashes = Map.copyOf(reportHashes);
            sourceLockBytes = sourceLockBytes.clone();
        }

        @Override
        public byte[] sourceLockBytes() {
            return sourceLockBytes.clone();
        }
    }

    private record HistoricalLock(String id, Path path, String sha256) {
    }

    private record ReportSpec(
            String file,
            String className,
            String methodName,
            String reportNameSuffix) {
    }

    private record SourceLockSnapshot(
            Map<String, String> lockedFiles,
            Map<String, String> reportHashes) {
    }
}
