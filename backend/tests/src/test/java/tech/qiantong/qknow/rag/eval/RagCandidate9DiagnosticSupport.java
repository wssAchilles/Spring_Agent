package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.JSONObject;
import tech.qiantong.qknow.module.kmc.service.rag.KeywordRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class RagCandidate9DiagnosticSupport {

    static final String FREEZE_PROPERTY = "rag.eval.candidate9.freeze";
    static final String DIAGNOSTIC_PROPERTY = "rag.eval.candidate9.diagnostic";
    static final String HOLDOUT_DIRECTORY_PROPERTY = "rag.eval.candidate9.holdout-dir";
    static final String PRODUCTION_PROPERTY =
            "qknow.rag.rerank.identifier-visible-colbert-enabled";

    static final long SELECTION_KB_ID = 10_140_000L;
    static final long SELECTION_SEGMENT_ID_MIN = 10_140_001L;
    static final long SELECTION_SEGMENT_ID_MAX = 10_144_999L;
    static final long SELECTION_DOCUMENT_ID_MIN = 10_145_000L;
    static final long SELECTION_DOCUMENT_ID_MAX = 10_149_999L;

    static final long HOLDOUT_KB_ID = 10_150_000L;
    static final long HOLDOUT_SEGMENT_ID_MIN = 10_150_001L;
    static final long HOLDOUT_SEGMENT_ID_MAX = 10_154_999L;
    static final long HOLDOUT_DOCUMENT_ID_MIN = 10_155_000L;
    static final long HOLDOUT_DOCUMENT_ID_MAX = 10_159_999L;

    static final int BUSINESS_SQL_LIMIT = 500;
    static final int BUSINESS_JAVA_LIMIT = 50;
    static final int BUSINESS_COLBERT_LIMIT = 30;
    static final int MAX_TOKENS_PER_DOCUMENT = 128;
    static final String PROCEED_DECISION =
            "PROCEED_TO_IDENTIFIER_VISIBLE_COLBERT_RED";
    static final String STOP_DECISION =
            "STOP_IDENTIFIER_VISIBLE_COLBERT_UNSUPPORTED";

    private static final String GENERATOR = "candidate9-static-fixture-v1";
    private static final int GENERATOR_VERSION = 1;
    private static final long SELECTION_SEED = 20260723L;
    private static final long HOLDOUT_SEED = 20260724L;
    private static final int SELECTION_QUERY_COUNT = 34;
    private static final int HOLDOUT_QUERY_COUNT = 68;
    private static final int SELECTION_SEGMENT_COUNT = 421;
    private static final int HOLDOUT_SEGMENT_COUNT = 842;
    private static final int SELECTION_QREL_COUNT = 58;
    private static final int HOLDOUT_QREL_COUNT = 116;
    private static final int SELECTION_TARGET_FAMILIES = 8;
    private static final int HOLDOUT_TARGET_FAMILIES = 16;
    private static final int SELECTION_NO_ID_FAMILIES = 1;
    private static final int HOLDOUT_NO_ID_FAMILIES = 2;
    private static final int SELECTION_NO_EXACT_FAMILIES = 1;
    private static final int HOLDOUT_NO_EXACT_FAMILIES = 2;
    private static final int SELECTION_EXACT_ONLY_FAMILIES = 1;
    private static final int HOLDOUT_EXACT_ONLY_FAMILIES = 2;
    private static final int SELECTION_LURE_FAMILIES = 1;
    private static final int HOLDOUT_LURE_FAMILIES = 2;
    private static final int SELECTION_SURVIVOR_FAMILIES = 1;
    private static final int HOLDOUT_SURVIVOR_FAMILIES = 2;
    private static final int SELECTION_MULTI_ID_FAMILIES = 1;
    private static final int HOLDOUT_MULTI_ID_FAMILIES = 2;
    private static final int SELECTION_RELEVANT_NONEXACT_FAMILIES = 1;
    private static final int HOLDOUT_RELEVANT_NONEXACT_FAMILIES = 2;
    private static final int SELECTION_LONG_TOKEN_FAMILIES = 1;
    private static final int HOLDOUT_LONG_TOKEN_FAMILIES = 2;
    private static final int SELECTION_BOUNDARY_NEGATIVE_FAMILIES = 1;
    private static final int HOLDOUT_BOUNDARY_NEGATIVE_FAMILIES = 2;
    private static final int SELECTION_PRESSURE_COUNT = 320;
    private static final int HOLDOUT_PRESSURE_COUNT = 640;
    private static final int LEXICAL_PER_TARGET = 8;
    private static final int PRESSURE_PER_TARGET = 40;
    private static final Map<String, String> RESOURCE_FILES = Map.of(
            "corpus", "corpus.jsonl",
            "queries", "queries.jsonl",
            "qrels", "qrels.tsv",
            "pressure", "pressure.json");
    private static final Set<String> IDENTIFIER_SHAPES = Set.of(
            "numeric-token", "doc-prefix", "zero-padded", "han-punctuation");
    private static final Set<String> FORBIDDEN_ARTIFACT_FIELDS = Set.of(
            "query", "retrievalquery", "identifier", "identifiers",
            "matchedidentifier", "matchedidentifiers",
            "visibleidentifier", "visibleidentifiers",
            "projectiontoken", "projectiontokens",
            "projectedtoken", "projectedtokens", "projectedtext",
            "term", "terms", "contentterm", "contentterms",
            "documentname", "documenttext", "originalcontent", "content", "answer",
            "reference", "referenceanswer", "referenceclaims",
            "qrel", "qrels", "qrelgrade", "grade",
            "apikey", "baseurl", "url", "model", "modelname",
            "embeddingapikey", "embeddingbaseurl", "embeddingmodel",
            "sqlparameter", "sqlparameters", "sqlparam", "sqlparams",
            "sqlexpressionvalue", "sqlexpressionvalues",
            "exceptionmessage", "errormessage");
    private static final Set<String> RANKING_CASE_KEYS = Set.of(
            "queryId", "familyId", "split", "originalQuerySha256",
            "retrievalQuerySha256", "extractedIdentifierCount",
            "extractedIdentifierHash", "rankingSha256", "arms",
            "callCounts", "baselinePrefixMatchesSut",
            "originalContentRestored", "businessColbertCalls",
            "baselineFullColbertCalls", "projectionFullColbertCalls",
            "sutEmbeddingCalls", "businessColbertEmbeddingCalls",
            "baselineFullColbertEmbeddingCalls",
            "projectionFullColbertEmbeddingCalls");
    private static final Set<String> FINALIZED_CASE_KEYS;
    private static final String JAVA_LIBRARY_PATH_PROPERTY = "java.library.path";
    private static final Path NO_NATIVE_PATH_SUFFIX = Path.of(
            "backend", "tests", "target", "rag-eval", "no-native");
    private static final Map<String, String> REQUIRED_DIAGNOSTIC_PROPERTIES =
            Map.ofEntries(
                    Map.entry("rag.eval.shadow", "true"),
                    Map.entry("rag.eval.shadow.compare-stable", "false"),
                    Map.entry("rag.eval.identifier.diagnostic", "false"),
                    Map.entry("rag.eval.candidate2.diagnostic", "false"),
                    Map.entry("rag.eval.candidate3.diagnostic", "false"),
                    Map.entry("rag.eval.candidate4.diagnostic", "false"),
                    Map.entry("rag.eval.candidate5.diagnostic", "false"),
                    Map.entry("rag.eval.candidate6.diagnostic", "false"),
                    Map.entry("rag.eval.candidate8.diagnostic", "false"),
                    Map.entry(DIAGNOSTIC_PROPERTY, "true"),
                    Map.entry("rag.eval.promotion", "false"),
                    Map.entry("qknow.rag.keyword.identifier-aware", "false"),
                    Map.entry(
                            "qknow.rag.rerank.identifier-consistency-enabled",
                            "true"),
                    Map.entry(PRODUCTION_PROPERTY, "false"),
                    Map.entry("qknow.rag.local-reranker.enabled", "false"),
                    Map.entry("qknow.rag.onnx-reranker.enabled", "false"),
                    Map.entry("hermes.rag.colbert.enabled", "true"),
                    Map.entry("hermes.rag.colbert.max-tokens-per-doc", "128"),
                    Map.entry("hermes.rag.colbert.embedding-platform", ""),
                    Map.entry("hermes.rag.colbert.embedding-base-url", ""),
                    Map.entry("hermes.rag.colbert.embedding-api-key", ""),
                    Map.entry("hermes.rag.colbert.embedding-model", ""),
                    Map.entry("forkCount", "1"),
                    Map.entry("reuseForks", "false"),
                    Map.entry("file.encoding", "UTF-8"),
                    Map.entry("user.timezone", "UTC"),
                    Map.entry("user.language", "en"),
                    Map.entry("user.country", "US"),
                    Map.entry("qknow.native.lib.dir", ""));

    static {
        Set<String> keys = new LinkedHashSet<>(RANKING_CASE_KEYS);
        keys.addAll(List.of(
                "language", "role", "identifierShape", "target", "qualifying",
                "mechanismValid", "baselineAP@10", "baselineNDCG@10",
                "projectionAP@10", "projectionNDCG@10", "unchanged",
                "safetyValid", "classification"));
        FINALIZED_CASE_KEYS = Set.copyOf(keys);
    }
    private static final String QUERY_VISIBILITY_TEMPLATE =
            "(?:^| )\" + Pattern.quote(identifier) + \"(?: |$)";
    private static final String JAVA_IDENTIFIER_BOUNDARY_TEMPLATE =
            "(?<![\\p{L}\\p{N}])\" + Pattern.quote(identifier) "
                    + "+ \"(?![\\p{L}\\p{N}])";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ERROR_CODE = Pattern.compile(
            "CANDIDATE9_[A-Z0-9_]+");
    private static final String RERANKER_PACKAGE =
            "tech.qiantong.qknow.module.kmc.service.rag.rerank.";
    private static final List<String> ELIGIBLE_PROVIDER_CLASSES = List.of(
            RERANKER_PACKAGE + "DashScopeRerankerProvider",
            RERANKER_PACKAGE + "DeterministicRerankerProvider",
            RERANKER_PACKAGE + "LocalBgeRerankerProvider",
            RERANKER_PACKAGE + "LocalRerankerProvider",
            RERANKER_PACKAGE + "OnnxRerankerProvider");
    private static final Map<String, String> LOCKED_EVIDENCE_SHA256 = Map.ofEntries(
            Map.entry("candidate5-freeze/selection-manifest.json",
                    "97f4dea785d3427e24265b9944c8f643584cb09c71e3c32dab6db64b0c4d18cb"),
            Map.entry("candidate5-freeze/holdout-manifest.json",
                    "0bb5b3fb38e5f592fc78abdd6dcd7e8639e97a0255a644ddd567ca8b632fe594"),
            Map.entry("candidate5-freeze/selection-ledger.json",
                    "5c8a4180ac587945eecc941ce3d259859a9d840ea3e97a03921acb29fa5e71e7"),
            Map.entry("candidate5-calibration-diagnostic.json",
                    "443071b522c9cd4d5bfcc6b38b45ff66a6f21710ec0e36ba7745432bb7eed1a9"),
            Map.entry("bundles/candidate5-baseline/manifest.json",
                    "d9e3e564e37cb7753d9b7e01419e909bea4cc514a0c2328881558bd1cf9ebb6a"),
            Map.entry("bundles/candidate5/manifest.json",
                    "5c6d56e01e063dd00c9c921396e0bac72b7d26c9dbe5f402902b382b8872529a"),
            Map.entry("candidate5-review-gate-failure/manifest.json",
                    "cc1cbcfd446c623e5332e6f7eaa5b9d50debac7124c60dddc22e616afea60c75"),
            Map.entry("candidate6-freeze/selection-manifest.json",
                    "aa545a7931f2a426ae8691e2c0f6990eeb436b9886808691b8226607c0ac0ddb"),
            Map.entry("candidate6-freeze/holdout-manifest.json",
                    "6560b9f95f117b4704e508a91d11de0c0e51ffe69f04b99c691e3ac97fef7bf3"),
            Map.entry("candidate6-freeze/selection-ledger.json",
                    "0b50a51bcd8bd896eef234f7a12066629d8d0c9df40d9e698c188d1308851d57"),
            Map.entry("candidate6-calibration-diagnostic.json",
                    "359ef072906124982a69ea288c2c1b87030dd46eb2f25656c2ff249a85ff2856"),
            Map.entry("candidate8-freeze/selection-manifest.json",
                    "85bf6c7419f55a9a877f90976e80b85103f1836496a3c5c2e598f2cfac3d02e3"),
            Map.entry("candidate8-freeze/holdout-manifest.json",
                    "c761bcda9b6b582352e95b95ef630a2a494fedbacb47e075ebd53efcebec0c2e"),
            Map.entry("candidate8-freeze/selection-ledger.json",
                    "1f35c40b10f9f28a722717500f83760468204abd30fe09cab138cf724fd8dcb8"),
            Map.entry("candidate8-calibration-diagnostic.json",
                    "025e938d1fb9fd2c41def1a55c517846afa46536e9eff7889db225c6c4115953"));
    private static final Object LEDGER_LOCK = new Object();

    private RagCandidate9DiagnosticSupport() {
    }

    static RuntimePaths paths(Path ragEvalRuntime) {
        Path runtime = Objects.requireNonNull(ragEvalRuntime, "ragEvalRuntime")
                .toAbsolutePath().normalize();
        Path freeze = runtime.resolve("candidate9-freeze");
        return new RuntimePaths(
                freeze,
                freeze.resolve("selection-manifest.json"),
                freeze.resolve("holdout-manifest.json"),
                freeze.resolve("selection-ledger.json"),
                freeze.resolve("review-ledger.json"),
                runtime.resolve("candidate9-calibration-diagnostic.json"));
    }

    static int enabledDiagnosticCount(
            boolean identifierDiagnostic,
            boolean candidate2Diagnostic,
            boolean candidate3Diagnostic,
            boolean candidate4Diagnostic,
            boolean candidate5Diagnostic,
            boolean candidate6Diagnostic,
            boolean candidate8Diagnostic,
            boolean candidate9Diagnostic) {
        return (identifierDiagnostic ? 1 : 0)
                + (candidate2Diagnostic ? 1 : 0)
                + (candidate3Diagnostic ? 1 : 0)
                + (candidate4Diagnostic ? 1 : 0)
                + (candidate5Diagnostic ? 1 : 0)
                + (candidate6Diagnostic ? 1 : 0)
                + (candidate8Diagnostic ? 1 : 0)
                + (candidate9Diagnostic ? 1 : 0);
    }

    static void requireSelectionJobProperties() {
        if (System.getProperty(HOLDOUT_DIRECTORY_PROPERTY) != null) {
            throw new IllegalStateException("CANDIDATE9_HOLDOUT_ACCESS_FORBIDDEN");
        }
    }

    static Map<String, String> requireDiagnosticCommandProperties() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String property : REQUIRED_DIAGNOSTIC_PROPERTIES.keySet()) {
            String value = System.getProperty(property);
            if (value == null) {
                throw new IllegalStateException(
                        "CANDIDATE9_COMMAND_PROPERTY_INVALID");
            }
            values.put(property, value);
        }
        String libraryPath = System.getProperty(JAVA_LIBRARY_PATH_PROPERTY);
        if (libraryPath == null) {
            throw new IllegalStateException(
                    "CANDIDATE9_COMMAND_PROPERTY_INVALID");
        }
        values.put(JAVA_LIBRARY_PATH_PROPERTY, libraryPath);
        return validateDiagnosticCommandProperties(values);
    }

    static Map<String, String> validateDiagnosticCommandProperties(
            Map<String, String> properties) {
        Objects.requireNonNull(properties, "properties");
        Set<String> expectedKeys = new LinkedHashSet<>(
                REQUIRED_DIAGNOSTIC_PROPERTIES.keySet());
        expectedKeys.add(JAVA_LIBRARY_PATH_PROPERTY);
        if (!properties.keySet().equals(expectedKeys)
                || REQUIRED_DIAGNOSTIC_PROPERTIES.entrySet().stream()
                .anyMatch(entry -> !entry.getValue().equals(
                        properties.get(entry.getKey())))
                || !containsFixedNoNativePath(
                        properties.get(JAVA_LIBRARY_PATH_PROPERTY))) {
            throw new IllegalStateException(
                    "CANDIDATE9_COMMAND_PROPERTY_INVALID");
        }
        return Map.copyOf(properties);
    }

    private static boolean containsFixedNoNativePath(String libraryPath) {
        if (libraryPath == null || libraryPath.isBlank()) {
            return false;
        }
        try {
            return java.util.Arrays.stream(libraryPath.split(
                            Pattern.quote(java.io.File.pathSeparator)))
                    .map(Path::of)
                    .map(Path::normalize)
                    .anyMatch(path -> path.endsWith(NO_NATIVE_PATH_SUFFIX));
        } catch (RuntimeException failure) {
            return false;
        }
    }

    static void requireSelectionRunAvailable(RuntimePaths paths) {
        requireFixedRuntimePaths(paths);
        if (hasCandidate9TemporaryArtifacts(paths)) {
            throw new IllegalStateException("INVALID_INCOMPLETE_PRIOR_RUN");
        }
        if (Files.exists(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE9_DIAGNOSTIC_ALREADY_EXISTS");
        }
        if (!Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        JSONObject ledger;
        try {
            ledger = readJson(paths.ledger(), "CANDIDATE9_LEDGER_INVALID");
        } catch (IllegalStateException failure) {
            throw new IllegalStateException("INVALID_INCOMPLETE_PRIOR_RUN", failure);
        }
        if ("RUNNING".equals(ledger.getString("status"))) {
            throw new IllegalStateException("INVALID_INCOMPLETE_PRIOR_RUN");
        }
        if ("COMPLETED".equals(ledger.getString("status"))) {
            throw new IllegalStateException("CANDIDATE9_SELECTION_ALREADY_COMPLETED");
        }
        throw new IllegalStateException("CANDIDATE9_LEDGER_INVALID");
    }

    static FrozenManifests freezeDatasets(
            Path runtime,
            Path selectionDirectory,
            Path holdoutDirectory) {
        RuntimePaths paths = paths(runtime);
        if (hasCandidate9TemporaryArtifacts(paths)
                || Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE9_SELECTION_ALREADY_STARTED");
        }
        DatasetFiles selection = readDatasetFiles(selectionDirectory, false);
        DatasetFiles holdout = readDatasetFiles(holdoutDirectory, true);
        validateDatasetIsolation(selection.dataset(), holdout.dataset());
        Manifest selectionManifest = buildManifest(selection, false);
        Manifest holdoutManifest = buildManifest(holdout, true);
        byte[] selectionBytes = canonicalJsonBytes(manifestMap(selectionManifest));
        byte[] holdoutBytes = canonicalJsonBytes(manifestMap(holdoutManifest));

        boolean selectionExists = Files.exists(
                paths.selectionManifest(), LinkOption.NOFOLLOW_LINKS);
        boolean holdoutExists = Files.exists(
                paths.holdoutManifest(), LinkOption.NOFOLLOW_LINKS);
        if (selectionExists || holdoutExists) {
            if (!selectionExists || !holdoutExists) {
                throw new IllegalStateException("CANDIDATE9_FREEZE_INCOMPLETE");
            }
            requireBytes(paths.selectionManifest(), selectionBytes,
                    "CANDIDATE9_FREEZE_ALREADY_FROZEN");
            requireBytes(paths.holdoutManifest(), holdoutBytes,
                    "CANDIDATE9_FREEZE_ALREADY_FROZEN");
            return loadFrozenManifests(paths);
        }
        atomicCreate(paths.selectionManifest(), selectionBytes);
        atomicCreate(paths.holdoutManifest(), holdoutBytes);
        return loadFrozenManifests(paths);
    }

    static void freezeFormalDatasets(Path runtime, Path holdoutDirectory) {
        verifyLockedEvidence();
        Path tests = testsDirectory();
        requireNoSymbolicLinksBelow(
                tests,
                paths(runtime).freezeDirectory(),
                "CANDIDATE9_RUNTIME_PATH_INVALID");
        Path selection = tests.resolve(
                "src/test/resources/rag-eval/candidate9-selection")
                .toAbsolutePath().normalize();
        Path expectedHoldout = tests.resolve("candidate9-holdout")
                .toAbsolutePath().normalize();
        Path actualHoldout = holdoutDirectory.toAbsolutePath().normalize();
        if (!expectedHoldout.equals(actualHoldout)) {
            throw new IllegalStateException("CANDIDATE9_HOLDOUT_DIRECTORY_MISMATCH");
        }
        requireNoSymbolicLinksBelow(tests, selection,
                "CANDIDATE9_DATASET_DIRECTORY_INVALID");
        requireNoSymbolicLinksBelow(tests, actualHoldout,
                "CANDIDATE9_DATASET_DIRECTORY_INVALID");
        freezeDatasets(
                runtime,
                selection,
                actualHoldout);
        verifyLockedEvidence();
    }

    static void verifyLockedEvidence() {
        Path ragEval = testsDirectory().resolve("target/rag-eval")
                .toAbsolutePath().normalize();
        LOCKED_EVIDENCE_SHA256.forEach((relative, expected) -> {
            Path path = ragEval.resolve(relative).normalize();
            if (!path.startsWith(ragEval)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(path)
                    || !expected.equals(ShadowContractSupport.sha256(path))) {
                throw new IllegalStateException(
                        "CANDIDATE9_LOCKED_EVIDENCE_HASH_MISMATCH");
            }
        });
    }

    static FrozenManifests loadFrozenManifests(RuntimePaths paths) {
        return loadFrozenManifests(paths, null);
    }

    private static FrozenManifests loadFrozenManifests(
            RuntimePaths paths, AccessCounter accessCounter) {
        requireFixedRuntimePaths(paths);
        if (Files.isSymbolicLink(paths.freezeDirectory())
                || !Files.isDirectory(paths.freezeDirectory(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE9_FREEZE_DIRECTORY_INVALID");
        }
        ManifestSnapshot selection = readManifest(
                paths.selectionManifest(), "candidate9-selection", "FROZEN",
                SELECTION_SEED, SELECTION_QUERY_COUNT, SELECTION_SEGMENT_COUNT,
                SELECTION_QREL_COUNT, accessCounter);
        ManifestSnapshot holdout = readManifest(
                paths.holdoutManifest(), "candidate9-holdout", "FROZEN_NOT_BLIND",
                HOLDOUT_SEED, HOLDOUT_QUERY_COUNT, HOLDOUT_SEGMENT_COUNT,
                HOLDOUT_QREL_COUNT, accessCounter);
        return new FrozenManifests(
                selection.manifest(), selection.sha256(),
                holdout.manifest(), holdout.sha256());
    }

    static FrozenDataset loadFormalFrozenSelection(Path runtime) {
        verifyLockedEvidence();
        Path selection = testsDirectory().resolve(
                "src/test/resources/rag-eval/candidate9-selection")
                .toAbsolutePath().normalize();
        requireNoSymbolicLinksBelow(testsDirectory(), selection,
                "CANDIDATE9_DATASET_DIRECTORY_INVALID");
        FrozenDataset frozen = loadFrozenSelection(runtime, selection);
        verifyLockedEvidence();
        return frozen;
    }

    static FrozenDataset loadFrozenSelection(Path runtime, Path selectionDirectory) {
        requireSelectionJobProperties();
        RuntimePaths paths = paths(runtime);
        AccessCounter accessCounter = new AccessCounter();
        FrozenManifests manifests = loadFrozenManifests(paths, accessCounter);
        RankingDatasetFiles current = readRankingDatasetFiles(
                selectionDirectory, accessCounter);
        Manifest manifest = manifests.selection();
        current.resources().forEach((name, resource) -> {
            if (!resource.equals(manifest.resources().get(name))) {
                throw new IllegalStateException(
                        "CANDIDATE9_SELECTION_RESOURCE_HASH_MISMATCH");
            }
        });
        return new FrozenDataset(
                current.dataset(),
                manifests,
                manifest.datasetHash(),
                current.directory(),
                accessCounter);
    }

    static EvaluationView loadQrelsAfterRanking(
            RuntimePaths paths,
            RunHandle handle,
            FrozenDataset frozen,
            List<? extends Map<String, ?>> rankingOnlyCases) {
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(frozen, "frozen");
        Objects.requireNonNull(rankingOnlyCases, "rankingOnlyCases");
        synchronized (LEDGER_LOCK) {
            validateRunningLedger(paths, handle);
            if (handle.accessCounter() != frozen.accessCounter()
                    || handle.dataset() != frozen.dataset()
                    || !handle.datasetHash().equals(frozen.datasetHash())
                    || !handle.selectionManifestSha256().equals(
                    frozen.manifests().selectionSha256())
                    || !handle.holdoutManifestSha256().equals(
                    frozen.manifests().holdoutSha256())) {
                throw new IllegalStateException("CANDIDATE9_RUN_HANDLE_MISMATCH");
            }
            return loadQrelsAfterRankingCore(frozen, rankingOnlyCases);
        }
    }

    static RecoveryBinding bindRecoveryRun(
            RuntimePaths frozenPaths,
            FrozenDataset frozen,
            RagCandidate91RecoverySupport.RecoveryAuthorization authorization) {
        Objects.requireNonNull(frozenPaths, "frozenPaths");
        Objects.requireNonNull(frozen, "frozen");
        Objects.requireNonNull(authorization, "authorization");
        String recoveryLedgerSha256 = authorization.claimBinding(frozen);
        if (!validSha(recoveryLedgerSha256)) {
            throw new IllegalArgumentException(
                    "CANDIDATE9_RECOVERY_BINDING_INVALID");
        }
        synchronized (LEDGER_LOCK) {
            synchronized (frozen.accessCounter()) {
                if (frozen.accessCounter().phase
                        != DiagnosticPhase.MANIFEST_VERIFIED) {
                    throw new IllegalStateException(
                            "CANDIDATE9_RUN_STATE_INVALID");
                }
            }
            FrozenManifests current = loadFrozenManifests(
                    frozenPaths, frozen.accessCounter());
            if (!frozen.manifests().equals(current)) {
                throw new FrozenInputChangedException(
                        "CANDIDATE9_MANIFEST_CHANGED");
            }
            synchronized (frozen.accessCounter()) {
                frozen.accessCounter().phase = DiagnosticPhase.RUNNING;
            }
            return new RecoveryBinding(
                    frozenPaths,
                    authorization,
                    frozen.datasetHash(),
                    frozen.manifests().selectionSha256(),
                    frozen.manifests().holdoutSha256(),
                    recoveryLedgerSha256,
                    frozen.dataset(),
                    frozen.accessCounter());
        }
    }

    static EvaluationView loadQrelsAfterRanking(
            RecoveryBinding binding,
            FrozenDataset frozen,
            List<? extends Map<String, ?>> rankingOnlyCases) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(frozen, "frozen");
        Objects.requireNonNull(rankingOnlyCases, "rankingOnlyCases");
        synchronized (LEDGER_LOCK) {
            requireRecoveryBinding(binding, frozen);
            binding.authorization().requirePhase(
                    binding.recoveryLedgerSha256(), frozen,
                    RagCandidate91RecoverySupport.RecoveryPhase.RANKING_FROZEN);
            return loadQrelsAfterRankingCore(frozen, rankingOnlyCases);
        }
    }

    static String validateRankingForRecovery(
            RecoveryBinding binding,
            FrozenDataset frozen,
            List<? extends Map<String, ?>> rankingOnlyCases) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(frozen, "frozen");
        Objects.requireNonNull(rankingOnlyCases, "rankingOnlyCases");
        synchronized (LEDGER_LOCK) {
            requireRecoveryBinding(binding, frozen);
            binding.authorization().requirePhase(
                    binding.recoveryLedgerSha256(), frozen,
                    RagCandidate91RecoverySupport.RecoveryPhase.RUNNING);
            List<Map<String, Object>> rankingCases = rankingOnlyCases.stream()
                    .map(RagCandidate9DiagnosticSupport::mutableCaseCopy)
                    .toList();
            validateRankingCasesBeforeQrels(frozen.dataset(), rankingCases);
            return rankingPhaseHash(rankingCases);
        }
    }

    private static EvaluationView loadQrelsAfterRankingCore(
            FrozenDataset frozen,
            List<? extends Map<String, ?>> rankingOnlyCases) {
        AccessCounter counter = frozen.accessCounter();
        synchronized (counter) {
            if (counter.phase != DiagnosticPhase.RUNNING
                    || counter.qrelResourceAccessCount != 0
                    || counter.evaluationDataset != null) {
                throw new IllegalStateException(
                        "CANDIDATE9_QREL_STATE_INVALID");
            }
            List<Map<String, Object>> rankingCases = rankingOnlyCases.stream()
                    .map(RagCandidate9DiagnosticSupport::mutableCaseCopy)
                    .toList();
            validateRankingCasesBeforeQrels(frozen.dataset(), rankingCases);
            String rankingPhaseSha256 = rankingPhaseHash(rankingCases);
            counter.rankingCasesSha256 = rankingCasesSha256(rankingCases);
            counter.rankingPhaseSha256 = rankingPhaseSha256;
            counter.phase = DiagnosticPhase.RANKING_FROZEN;
            counter.qrelResourceAccessBeforeRanking =
                    counter.qrelResourceAccessCount;
            Path qrelsPath = requireRegular(
                    frozen.selectionDirectory().resolve("qrels.tsv"),
                    "CANDIDATE9_DATASET_RESOURCE_INVALID");
            ResourceBytes resource;
            try {
                resource = readDatasetResource(
                        qrelsPath, false, counter, true);
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "CANDIDATE9_DATASET_RESOURCE_READ_FAILED", failure);
            }
            if (!resource.resourceHash().equals(
                    frozen.manifests().selection().resources().get("qrels"))) {
                throw new IllegalStateException(
                        "CANDIDATE9_SELECTION_QREL_HASH_MISMATCH");
            }
            ParsedQrels qrels = parseQrels(resource.text());
            RagEvaluationDataset evaluationDataset = new RagEvaluationDataset(
                    frozen.dataset().corpusById(), frozen.dataset().queries(),
                    qrels.qrels());
            RagEvaluationDatasetLoader.validate(evaluationDataset);
            validateShape(evaluationDataset, qrels.count(),
                    counter.pressure, false);
            validateFrozenStructure(
                    evaluationDataset,
                    qrels.count(),
                    counter.pressure,
                    frozen.manifests().selection(),
                    false);
            counter.evaluationDataset = evaluationDataset;
            counter.phase = DiagnosticPhase.QRELS_LOADED;
            return new EvaluationView(
                    evaluationDataset, rankingPhaseSha256, counter.snapshot());
        }
    }

    static RunHandle beginSelectionRun(
            RuntimePaths paths, FrozenDataset frozen) {
        synchronized (LEDGER_LOCK) {
            synchronized (frozen.accessCounter()) {
                if (frozen.accessCounter().phase
                        != DiagnosticPhase.MANIFEST_VERIFIED) {
                    throw new IllegalStateException(
                            "CANDIDATE9_RUN_STATE_INVALID");
                }
            }
            requireSelectionRunAvailable(paths);
            FrozenManifests manifests = frozen.manifests();
            if (!manifests.equals(loadFrozenManifests(
                    paths, frozen.accessCounter()))) {
                throw new IllegalStateException("CANDIDATE9_MANIFEST_CHANGED");
            }
            Map<String, Object> ledger = new LinkedHashMap<>();
            ledger.put("status", "RUNNING");
            ledger.put("datasetHash", manifests.selection().datasetHash());
            ledger.put("selectionManifestSha256", manifests.selectionSha256());
            ledger.put("holdoutManifestSha256", manifests.holdoutSha256());
            ledger.put("startedAt", Instant.now().toString());
            byte[] bytes = canonicalJsonBytes(ledger);
            atomicCreate(paths.ledger(), bytes);
            synchronized (frozen.accessCounter()) {
                frozen.accessCounter().phase = DiagnosticPhase.RUNNING;
            }
            return new RunHandle(
                    manifests.selection().datasetHash(),
                    manifests.selectionSha256(),
                    manifests.holdoutSha256(),
                    ShadowContractSupport.sha256(bytes),
                    frozen.dataset(),
                    frozen.accessCounter());
        }
    }

    static void writeDiagnosticAndComplete(
            RuntimePaths paths,
            RunHandle handle,
            FrozenDataset frozen,
            Map<String, ?> artifact) {
        synchronized (LEDGER_LOCK) {
            boolean formalRuntime = isFormalRuntime(paths);
            if (formalRuntime) {
                verifyLockedEvidence();
            }
            requireFixedRuntimePaths(paths);
            validateRunningLedger(paths, handle);
            requireRunBinding(handle, frozen);
            validateArtifact(artifact, handle);
            if ("VALID".equals(artifact.get("status"))) {
                verifyFrozenInputsUnchanged(paths, handle, frozen);
            }
            byte[] artifactBytes = canonicalJsonBytes(artifact);
            atomicCreate(paths.diagnostic(), artifactBytes);
            String artifactSha256 = ShadowContractSupport.sha256(paths.diagnostic());
            if (!artifactSha256.equals(ShadowContractSupport.sha256(artifactBytes))) {
                throw new IllegalStateException("CANDIDATE9_ARTIFACT_HASH_MISMATCH");
            }
            Map<String, Object> completed = new LinkedHashMap<>();
            completed.put("status", "COMPLETED");
            completed.put("datasetHash", handle.datasetHash());
            completed.put("selectionManifestSha256", handle.selectionManifestSha256());
            completed.put("holdoutManifestSha256", handle.holdoutManifestSha256());
            completed.put("artifactSha256", artifactSha256);
            completed.put("configHash", artifact.get("configHash"));
            completed.put("completedAt", Instant.now().toString());
            atomicReplace(paths.ledger(), canonicalJsonBytes(completed));
            JSONObject verified = readJson(paths.ledger(), "CANDIDATE9_LEDGER_INVALID");
            if (!"COMPLETED".equals(verified.getString("status"))
                    || !artifactSha256.equals(verified.getString("artifactSha256"))
                    || !Objects.equals(artifact.get("configHash"),
                    verified.getString("configHash"))) {
                throw new IllegalStateException("CANDIDATE9_LEDGER_INVALID");
            }
            if (formalRuntime) {
                verifyLockedEvidence();
            }
        }
    }

    private static void validateRunningLedger(
            RuntimePaths paths, RunHandle handle) {
        requireFixedRuntimePaths(paths);
        JSONObject ledger = readJson(paths.ledger(), "CANDIDATE9_LEDGER_INVALID");
        if (!"RUNNING".equals(ledger.getString("status"))
                || !handle.datasetHash().equals(ledger.getString("datasetHash"))
                || !handle.selectionManifestSha256().equals(
                ledger.getString("selectionManifestSha256"))
                || !handle.holdoutManifestSha256().equals(
                ledger.getString("holdoutManifestSha256"))
                || !handle.runningLedgerSha256().equals(
                ShadowContractSupport.sha256(paths.ledger()))) {
            throw new IllegalStateException("CANDIDATE9_LEDGER_INVALID");
        }
    }

    private static void requireRunBinding(
            RunHandle handle, FrozenDataset frozen) {
        if (frozen == null
                || handle.accessCounter() != frozen.accessCounter()
                || handle.dataset() != frozen.dataset()
                || !handle.datasetHash().equals(frozen.datasetHash())
                || !handle.selectionManifestSha256().equals(
                frozen.manifests().selectionSha256())
                || !handle.holdoutManifestSha256().equals(
                frozen.manifests().holdoutSha256())) {
            throw new IllegalStateException("CANDIDATE9_RUN_HANDLE_MISMATCH");
        }
    }

    private static void requireRecoveryBinding(
            RecoveryBinding binding, FrozenDataset frozen) {
        if (binding == null
                || frozen == null
                || binding.frozenPaths() == null
                || binding.authorization() == null
                || binding.accessCounter() != frozen.accessCounter()
                || binding.dataset() != frozen.dataset()
                || !binding.datasetHash().equals(frozen.datasetHash())
                || !binding.selectionManifestSha256().equals(
                frozen.manifests().selectionSha256())
                || !binding.holdoutManifestSha256().equals(
                frozen.manifests().holdoutSha256())
                || !validSha(binding.recoveryLedgerSha256())) {
            throw new IllegalStateException(
                    "CANDIDATE9_RECOVERY_BINDING_INVALID");
        }
        binding.authorization().requireBound(
                binding.recoveryLedgerSha256(), frozen);
    }

    private static void verifyFrozenInputsUnchanged(
            RuntimePaths paths,
            RunHandle handle,
            FrozenDataset frozen) {
        verifyFrozenInputsUnchanged(paths, handle.selectionManifestSha256(),
                handle.holdoutManifestSha256(), frozen, true);
    }

    private static void verifyFrozenInputsUnchanged(
            RuntimePaths paths,
            String selectionManifestSha256,
            String holdoutManifestSha256,
            FrozenDataset frozen,
            boolean recheckQrelBytes) {
        FrozenManifests current;
        try {
            current = loadFrozenManifests(paths);
        } catch (RuntimeException failure) {
            throw new FrozenInputChangedException(
                    "CANDIDATE9_MANIFEST_CHANGED", failure);
        }
        if (!selectionManifestSha256.equals(
                current.selectionSha256())
                || !holdoutManifestSha256.equals(
                current.holdoutSha256())
                || !frozen.manifests().equals(current)) {
            throw new FrozenInputChangedException(
                    "CANDIDATE9_MANIFEST_CHANGED");
        }
        Path tests = testsDirectory().toAbsolutePath().normalize();
        Path selectionBase = frozen.selectionDirectory().startsWith(tests)
                ? tests : frozen.selectionDirectory().getParent();
        requireNoSymbolicLinksBelow(
                selectionBase, frozen.selectionDirectory(),
                "CANDIDATE9_SELECTION_RESOURCE_HASH_MISMATCH");
        List<String> resources = new ArrayList<>(
                List.of("queries", "corpus", "pressure"));
        if (recheckQrelBytes) {
            resources.add("qrels");
        }
        for (String name : resources) {
            ResourceHash expected = frozen.manifests().selection()
                    .resources().get(name);
            Path resource = frozen.selectionDirectory().resolve(expected.file());
            if (Files.isSymbolicLink(resource)
                    || !Files.isRegularFile(
                    resource, LinkOption.NOFOLLOW_LINKS)
                    || !expected.sha256().equals(
                    ShadowContractSupport.sha256(resource))) {
                throw new FrozenInputChangedException(
                        "CANDIDATE9_SELECTION_RESOURCE_HASH_MISMATCH");
            }
        }
    }

    private static boolean isFormalRuntime(RuntimePaths paths) {
        return paths(testsDirectory().resolve("target/rag-eval"))
                .equals(paths);
    }

    static void writePreRunInvalidDiagnostic(
            RuntimePaths paths,
            Map<String, ?> config,
            String errorCode) {
        synchronized (LEDGER_LOCK) {
            requireFixedRuntimePaths(paths);
            if (hasCandidate9TemporaryArtifacts(paths)
                    || Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("CANDIDATE9_SELECTION_ALREADY_STARTED");
            }
            Map<String, Object> configCopy = new LinkedHashMap<>();
            config.forEach(configCopy::put);
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("selectionManifestHash",
                    safeFileHash(paths.selectionManifest()));
            artifact.put("holdoutManifestHash",
                    safeFileHash(paths.holdoutManifest()));
            artifact.put("config", configCopy);
            artifact.put("configHash", ShadowContractSupport.configHash(configCopy));
            artifact.put("status", "INVALID");
            artifact.put("decision", null);
            artifact.put("errorCode", requireErrorCode(errorCode));
            rejectForbiddenFields(artifact);
            atomicCreate(paths.diagnostic(), canonicalJsonBytes(artifact));
        }
    }

    static void writePreRunCommandInvalidDiagnostic(RuntimePaths paths) {
        synchronized (LEDGER_LOCK) {
            requireFixedRuntimePaths(paths);
            if (hasCandidate9TemporaryArtifacts(paths)
                    || Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException(
                        "CANDIDATE9_SELECTION_ALREADY_STARTED");
            }
            Map<String, Object> presence = new TreeMap<>();
            for (Map.Entry<String, String> entry
                    : REQUIRED_DIAGNOSTIC_PROPERTIES.entrySet()) {
                String actual = System.getProperty(entry.getKey());
                presence.put(entry.getKey(), Map.of(
                        "present", actual != null,
                        "empty", actual != null && actual.isEmpty(),
                        "matchesContract", entry.getValue().equals(actual)));
            }
            String libraryPath = System.getProperty(JAVA_LIBRARY_PATH_PROPERTY);
            presence.put(JAVA_LIBRARY_PATH_PROPERTY, Map.of(
                    "present", libraryPath != null,
                    "empty", libraryPath != null && libraryPath.isEmpty(),
                    "matchesContract", containsFixedNoNativePath(libraryPath)));
            presence.put(HOLDOUT_DIRECTORY_PROPERTY, Map.of(
                    "present", System.getProperty(
                            HOLDOUT_DIRECTORY_PROPERTY) != null,
                    "matchesContract", System.getProperty(
                            HOLDOUT_DIRECTORY_PROPERTY) == null));
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("selectionManifestHash",
                    safeFileHash(paths.selectionManifest()));
            artifact.put("holdoutManifestHash",
                    safeFileHash(paths.holdoutManifest()));
            artifact.put("commandContractHash",
                    ShadowContractSupport.configHash(Map.of(
                            "required", REQUIRED_DIAGNOSTIC_PROPERTIES,
                            "javaLibraryPathPolicy", "fixed-no-native-v1",
                            "holdoutDirectoryPolicy", "property-absent-v1")));
            artifact.put("commandPresenceHash",
                    ShadowContractSupport.configHash(presence));
            artifact.put("status", "INVALID");
            artifact.put("decision", null);
            artifact.put("errorCode",
                    "CANDIDATE9_DIAGNOSTIC_COMMAND_INVALID");
            rejectForbiddenFields(artifact);
            atomicCreate(paths.diagnostic(), canonicalJsonBytes(artifact));
        }
    }

    static Map<String, Object> freshInvalidArtifact(
            FrozenDataset frozen,
            Map<String, ?> config,
            String errorCode) {
        Map<String, Object> artifact = baseArtifact(frozen, config);
        artifact.put("status", "INVALID");
        artifact.put("decision", null);
        artifact.put("errorCode", requireErrorCode(errorCode));
        return artifact;
    }

    static Map<String, Object> freshInvalidArtifact(
            RecoveryBinding binding,
            FrozenDataset frozen,
            Map<String, ?> config,
            String errorCode) {
        Map<String, Object> artifact = freshInvalidArtifact(
                frozen, config, errorCode);
        validateRecoveryArtifact(binding, frozen, artifact);
        return artifact;
    }

    static Map<String, Object> freshValidArtifact(
            FrozenDataset frozen,
            Map<String, ?> config,
            List<? extends Map<String, ?>> cases) {
        if (cases.size() != SELECTION_QUERY_COUNT) {
            throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
        }
        List<Map<String, Object>> rankingCases = cases.stream()
                .map(RagCandidate9DiagnosticSupport::mutableCaseCopy)
                .toList();
        validateRankingCasesBeforeQrels(frozen.dataset(), rankingCases);
        String rankingPhaseSha256 = rankingPhaseHash(rankingCases);
        String rankingCasesSha256 = rankingCasesSha256(rankingCases);
        RagEvaluationDataset evaluationDataset = frozen.evaluationDataset(
                rankingPhaseSha256, rankingCasesSha256);
        FinalizedCases finalized = finalizeCasesAfterQrels(
                evaluationDataset, rankingCases);
        List<Map<String, Object>> caseCopies = finalized.cases();
        List<CaseEvidence> evidence = finalized.evidence();
        long encodedDocumentTokenDelta = caseCopies.stream()
                .map(item -> requiredMap(item, "callCounts"))
                .mapToLong(counts -> ((Number) counts.get(
                        "colbertEncodedDocumentTokens")).longValue())
                .sum();
        if (encodedDocumentTokenDelta > 26L) {
            throw new IllegalArgumentException("CANDIDATE9_BUDGET_INVALID");
        }
        String recomputedRankingPhase = rankingPhaseHash(caseCopies);
        if (!rankingPhaseSha256.equals(recomputedRankingPhase)) {
            throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
        }
        String decision = decide(evidence);
        Map<String, Object> summary = new LinkedHashMap<>(
                diagnosticSummary(evidence));
        summary.put("rankingPhaseSha256", rankingPhaseSha256);
        summary.put("selectionResourceAccessCount",
                frozen.auditedReads().selectionResourceAccessCount());
        summary.put("manifestAccessCount",
                frozen.auditedReads().manifestAccessCount());
        summary.put("holdoutResourceAccessCount",
                frozen.auditedReads().holdoutResourceAccessCount());
        summary.put("qrelResourceAccessBeforeRanking",
                frozen.auditedReads().qrelResourceAccessBeforeRanking());
        summary.put("qrelResourceAccessCount",
                frozen.auditedReads().qrelResourceAccessCount());
        summary.put("diagnosticPhase",
                frozen.auditedReads().diagnosticPhase());
        summary.put("colbertEncodedDocumentTokenDelta",
                encodedDocumentTokenDelta);
        Map<String, Object> artifact = baseArtifact(frozen, config);
        artifact.put("status", "VALID");
        artifact.put("decision", decision);
        artifact.put("errorCode", null);
        artifact.put("summary", summary);
        artifact.put("cases", caseCopies);
        rejectForbiddenFields(artifact);
        return artifact;
    }

    static Map<String, Object> freshValidArtifact(
            RecoveryBinding binding,
            FrozenDataset frozen,
            Map<String, ?> config,
            List<? extends Map<String, ?>> cases) {
        Map<String, Object> artifact = freshValidArtifact(
                frozen, config, cases);
        validateRecoveryArtifact(binding, frozen, artifact);
        return artifact;
    }

    static void validateRecoveryArtifact(
            RecoveryBinding binding,
            FrozenDataset frozen,
            Map<String, ?> artifact) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(frozen, "frozen");
        Objects.requireNonNull(artifact, "artifact");
        synchronized (LEDGER_LOCK) {
            requireRecoveryBinding(binding, frozen);
            binding.authorization().requirePhase(
                    binding.recoveryLedgerSha256(), frozen,
                    RagCandidate91RecoverySupport.RecoveryPhase.QRELS_LOADED);
            validateArtifact(artifact, binding);
            if ("VALID".equals(artifact.get("status"))) {
                verifyFrozenInputsUnchanged(
                        binding.frozenPaths(),
                        binding.selectionManifestSha256(),
                        binding.holdoutManifestSha256(),
                        frozen, false);
            }
        }
    }

    static List<Map<String, Object>> finalizeCasesAfterQrels(
            FrozenDataset frozen,
            List<? extends Map<String, ?>> rankingOnlyCases) {
        List<Map<String, Object>> copies = rankingOnlyCases.stream()
                .map(RagCandidate9DiagnosticSupport::mutableCaseCopy)
                .toList();
        validateRankingCasesBeforeQrels(frozen.dataset(), copies);
        String rankingPhaseSha256 = rankingPhaseHash(copies);
        String rankingCasesSha256 = rankingCasesSha256(copies);
        return finalizeCasesAfterQrels(
                frozen.evaluationDataset(
                        rankingPhaseSha256, rankingCasesSha256), copies).cases();
    }

    static String decide(List<CaseEvidence> cases) {
        Map<String, Integer> expectedFamiliesByRole = Map.of(
                "target", 8,
                "no-identifier", 1,
                "no-exact", 1,
                "existing-survivor", 1,
                "irrelevant-exact-safety", 1,
                "semantic-near-exact-lure", 1,
                "relevant-nonexact", 1,
                "multi-id-collision", 1,
                "long-token-boundary", 1,
                "boundary-negative", 1);
        if (cases.size() != SELECTION_QUERY_COUNT
                || cases.stream().map(CaseEvidence::queryId).distinct().count()
                != SELECTION_QUERY_COUNT
                || cases.stream().anyMatch(item -> item.queryId() == null
                || item.queryId().isBlank() || item.familyId() == null
                || item.familyId().isBlank()
                || item.target() != "target".equals(item.role())
                || !Set.of("zh", "en").contains(item.language())
                || !expectedFamiliesByRole.containsKey(item.role()))) {
            return STOP_DECISION;
        }
        Map<String, List<CaseEvidence>> families = cases.stream().collect(
                Collectors.groupingBy(CaseEvidence::familyId));
        if (families.size() != 17 || families.values().stream().anyMatch(family ->
                family.size() != 2
                        || family.stream().map(CaseEvidence::language)
                        .collect(Collectors.toSet()).size() != 2
                        || family.stream().map(CaseEvidence::role)
                        .distinct().count() != 1)) {
            return STOP_DECISION;
        }
        Map<String, Long> actualFamiliesByRole = families.values().stream()
                .collect(Collectors.groupingBy(
                        family -> family.get(0).role(), Collectors.counting()));
        if (expectedFamiliesByRole.entrySet().stream().anyMatch(entry ->
                actualFamiliesByRole.getOrDefault(entry.getKey(), 0L)
                        != entry.getValue().longValue())) {
            return STOP_DECISION;
        }
        List<CaseEvidence> englishTargets = cases.stream()
                .filter(CaseEvidence::target)
                .filter(item -> "en".equals(item.language()))
                .toList();
        List<CaseEvidence> chineseTargets = cases.stream()
                .filter(CaseEvidence::target)
                .filter(item -> "zh".equals(item.language()))
                .toList();
        List<CaseEvidence> qualifyingTargets = englishTargets.stream()
                .filter(CaseEvidence::qualifying)
                .toList();
        if (englishTargets.size() != SELECTION_TARGET_FAMILIES
                || chineseTargets.size() != SELECTION_TARGET_FAMILIES
                || qualifyingTargets.size() < 4
                || !qualifyingTargets.stream().map(CaseEvidence::identifierShape)
                .collect(Collectors.toSet()).containsAll(IDENTIFIER_SHAPES)
                || cases.stream().anyMatch(item ->
                !item.mechanismValid() || !item.safetyValid())
                || qualifyingTargets.stream().anyMatch(item ->
                !(item.projectionAp() > item.baselineAp())
                        || !(item.projectionNdcg() > item.baselineNdcg()))
                || englishTargets.stream().filter(item -> !item.qualifying())
                .anyMatch(item -> !item.unchanged())
                || chineseTargets.stream().anyMatch(item ->
                !item.unchanged()
                        || Double.compare(item.projectionAp(), item.baselineAp()) != 0
                        || Double.compare(item.projectionNdcg(), item.baselineNdcg()) != 0)
                || cases.stream().filter(item -> !item.target())
                .filter(item -> !"multi-id-collision".equals(item.role()))
                .anyMatch(item -> !item.unchanged())) {
            return STOP_DECISION;
        }
        if (families.values().stream().anyMatch(family ->
                family.stream().mapToDouble(CaseEvidence::projectionAp).average()
                        .orElse(0.0D)
                        < family.stream().mapToDouble(CaseEvidence::baselineAp)
                        .average().orElse(0.0D)
                        || family.stream().mapToDouble(CaseEvidence::projectionNdcg)
                        .average().orElse(0.0D)
                        < family.stream().mapToDouble(CaseEvidence::baselineNdcg)
                        .average().orElse(0.0D))) {
            return STOP_DECISION;
        }
        double baselineAp = cases.stream().mapToDouble(
                CaseEvidence::baselineAp).average().orElse(0.0D);
        double projectionAp = cases.stream().mapToDouble(
                CaseEvidence::projectionAp).average().orElse(0.0D);
        double baselineNdcg = cases.stream().mapToDouble(
                CaseEvidence::baselineNdcg).average().orElse(0.0D);
        double projectionNdcg = cases.stream().mapToDouble(
                CaseEvidence::projectionNdcg).average().orElse(0.0D);
        return projectionAp > baselineAp && projectionNdcg > baselineNdcg
                ? PROCEED_DECISION : STOP_DECISION;
    }

    static Map<String, Object> diagnosticSummary(List<CaseEvidence> cases) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseCount", cases.size());
        summary.put("targetCaseCount", cases.stream().filter(CaseEvidence::target).count());
        summary.put("controlCaseCount", cases.stream().filter(item -> !item.target()).count());
        summary.put("baselineAP@10", cases.stream().filter(CaseEvidence::target)
                .mapToDouble(CaseEvidence::baselineAp).average().orElse(0.0D));
        summary.put("projectionAP@10", cases.stream().filter(CaseEvidence::target)
                .mapToDouble(CaseEvidence::projectionAp).average().orElse(0.0D));
        summary.put("baselineNDCG@10", cases.stream().filter(CaseEvidence::target)
                .mapToDouble(CaseEvidence::baselineNdcg).average().orElse(0.0D));
        summary.put("projectionNDCG@10", cases.stream().filter(CaseEvidence::target)
                .mapToDouble(CaseEvidence::projectionNdcg).average().orElse(0.0D));
        summary.put("qualifyingEnglishTargetCount", cases.stream()
                .filter(CaseEvidence::target)
                .filter(item -> "en".equals(item.language()))
                .filter(CaseEvidence::qualifying).count());
        return summary;
    }

    static List<String> identifierTerms(String query) {
        try {
            Method method = KeywordRetriever.class.getDeclaredMethod(
                    "extractIdentifierTerms", String.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) method.invoke(null, query);
            return List.copyOf(values);
        } catch (NoSuchMethodException | IllegalAccessException failure) {
            throw new IllegalStateException("CANDIDATE9_IDENTIFIER_EXTRACTOR_UNAVAILABLE", failure);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException(
                    "CANDIDATE9_IDENTIFIER_EXTRACTOR_FAILED", failure.getCause());
        }
    }

    static List<String> visibleIdentifierTerms(String effectiveRerankQuery) {
        String query = Objects.toString(effectiveRerankQuery, "");
        LinkedHashSet<String> visible = new LinkedHashSet<>();
        for (String identifier : identifierTerms(query)) {
            if (Pattern.compile(
                    "(?:^| )" + Pattern.quote(identifier) + "(?: |$)")
                    .matcher(query).find()) {
                visible.add(identifier);
            }
        }
        return List.copyOf(visible);
    }

    static boolean boundaryMatches(String documentName, String identifier) {
        if (documentName == null || identifier == null || identifier.isEmpty()) {
            return false;
        }
        return Pattern.compile("(?<![\\p{L}\\p{N}])"
                + Pattern.quote(identifier)
                + "(?![\\p{L}\\p{N}])")
                .matcher(documentName).find();
    }

    static boolean matchesDocumentName(String documentName, String identifier) {
        return boundaryMatches(documentName, identifier);
    }

    static boolean matchesAnyIdentifier(String documentName, List<String> identifiers) {
        return identifiers.stream().anyMatch(identifier ->
                boundaryMatches(documentName, identifier));
    }

    static List<String> matchedVisibleIdentifiers(
            String effectiveRerankQuery, String documentName) {
        List<String> visible = visibleIdentifierTerms(effectiveRerankQuery);
        if (visible.isEmpty() || visible.size() > 2) {
            return List.of();
        }
        return visible.stream()
                .filter(identifier -> matchesDocumentName(documentName, identifier))
                .toList();
    }

    static String projectedText(
            String effectiveRerankQuery,
            String documentName,
            String originalContent) {
        return project(effectiveRerankQuery, documentName, originalContent).text();
    }

    static Projection project(
            String effectiveRerankQuery,
            String documentName,
            String originalContent) {
        String content = Objects.toString(originalContent, "");
        List<String> matched = matchedVisibleIdentifiers(
                effectiveRerankQuery, documentName);
        int originalTokens = tokenCount(content);
        int projectedTokens = matched.size();
        boolean applied = !matched.isEmpty()
                && matched.size() <= 2
                && originalTokens >= 1
                && projectedTokens < MAX_TOKENS_PER_DOCUMENT;
        if (!applied) {
            return new Projection(
                    content, 0, originalTokens, 0,
                    Math.min(originalTokens, MAX_TOKENS_PER_DOCUMENT), false);
        }
        return new Projection(
                String.join(" ", matched) + " " + content,
                matched.size(), originalTokens, projectedTokens,
                Math.min(originalTokens,
                        MAX_TOKENS_PER_DOCUMENT - projectedTokens), true);
    }

    static boolean projectionApplied(
            String effectiveRerankQuery,
            String documentName,
            String originalContent) {
        return !projectedText(effectiveRerankQuery, documentName, originalContent)
                .equals(Objects.toString(originalContent, ""));
    }

    static int tokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\w\\s\\u4e00-\\u9fff\\u3400-\\u4dbf]", "")
                .replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String part : normalized.split(" ")) {
            boolean hasHan = part.chars().anyMatch(codePoint ->
                    Character.UnicodeScript.of(codePoint)
                            == Character.UnicodeScript.HAN);
            count += hasHan && part.length() > 1 ? part.length() : 1;
        }
        return count;
    }

    static List<RetrievalSnapshot> snapshotResults(
            List<RetrievalResult> results, String effectiveRerankQuery) {
        List<String> identifiers = visibleIdentifierTerms(effectiveRerankQuery);
        List<RetrievalSnapshot> snapshots = new ArrayList<>();
        if (results == null) {
            return List.of();
        }
        int ordinal = 0;
        for (RetrievalResult result : results) {
            if (result == null) {
                continue;
            }
            Object rawColbertScore = result.getMetadata() == null
                    ? null : result.getMetadata().get("colbert_score");
            double colbertScore = rawColbertScore instanceof Number number
                    ? number.doubleValue() : Double.NaN;
            boolean exact = matchesAnyIdentifier(
                    result.getDocumentName(), identifiers);
            snapshots.add(new RetrievalSnapshot(
                    ordinal++, result.getSegmentId(), result.getDocumentId(),
                    result.getQmSegmentId(), result.getParentSegmentId(),
                    result.getDocumentName(), result.getContent(), result.getAnswer(),
                    result.getScore(), result.getSource(), colbertScore,
                    exact));
        }
        return List.copyOf(snapshots);
    }

    static List<RetrievalResult> copyResults(
            List<RetrievalSnapshot> snapshots) {
        return snapshots.stream()
                .map(RetrievalSnapshot::toRetrievalResult)
                .toList();
    }

    private static Map<String, Object> baseArtifact(
            FrozenDataset frozen, Map<String, ?> config) {
        Objects.requireNonNull(frozen, "frozen");
        Map<String, Object> configCopy = new LinkedHashMap<>();
        config.forEach(configCopy::put);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("datasetHash", frozen.datasetHash());
        artifact.put("selectionManifestHash", frozen.manifests().selectionSha256());
        artifact.put("holdoutManifestHash", frozen.manifests().holdoutSha256());
        artifact.put("holdoutFreezeStatus", "FROZEN_NOT_BLIND");
        artifact.put("auditedReads", frozen.auditedReads());
        artifact.put("config", configCopy);
        artifact.put("configHash", ShadowContractSupport.configHash(configCopy));
        return artifact;
    }

    private static void validateArtifact(
            Map<String, ?> artifact, ArtifactBinding handle) {
        rejectForbiddenFields(artifact);
        Object config = artifact.get("config");
        if (!(config instanceof Map<?, ?> rawConfig)) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_CONFIG_INVALID");
        }
        Map<String, Object> configMap = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawConfig.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalStateException("CANDIDATE9_ARTIFACT_CONFIG_INVALID");
            }
            configMap.put(key, entry.getValue());
        }
        if (!Objects.equals(artifact.get("configHash"),
                ShadowContractSupport.configHash(configMap))) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_CONFIG_INVALID");
        }
        if (!(artifact.get("auditedReads") instanceof AccessAudit audit)
                || audit.manifestAccessCount() != 4
                || audit.holdoutResourceAccessCount() != 0
                || audit.qrelResourceAccessBeforeRanking() != 0
                || audit.qrelResourceAccessCount() < 0
                || audit.qrelResourceAccessCount() > 1
                || audit.selectionResourceAccessCount()
                != 3 + audit.qrelResourceAccessCount()) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_ACCESS_AUDIT_INVALID");
        }
        if (!handle.datasetHash().equals(artifact.get("datasetHash"))
                || !handle.selectionManifestSha256().equals(
                artifact.get("selectionManifestHash"))
                || !handle.holdoutManifestSha256().equals(
                artifact.get("holdoutManifestHash"))) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_MANIFEST_INVALID");
        }
        String status = Objects.toString(artifact.get("status"), null);
        String decision = Objects.toString(artifact.get("decision"), null);
        Object errorCode = artifact.get("errorCode");
        if ("INVALID".equals(status)) {
            if (decision != null || !(errorCode instanceof String code)
                    || !ERROR_CODE.matcher(code).matches()
                    || artifact.containsKey("cases") || artifact.containsKey("summary")) {
                throw new IllegalStateException("CANDIDATE9_ARTIFACT_INVALID");
            }
            return;
        }
        validateConfig(configMap);
        if (!"VALID".equals(status)
                || !Set.of(PROCEED_DECISION, STOP_DECISION).contains(decision)
                || errorCode != null
                || !(artifact.get("cases") instanceof List<?> cases)
                || cases.size() != SELECTION_QUERY_COUNT
                || !(artifact.get("summary") instanceof Map<?, ?>)) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_INVALID");
        }
        Map<?, ?> summary = (Map<?, ?>) artifact.get("summary");
        String rankingPhaseSha256 = Objects.toString(
                summary.get("rankingPhaseSha256"), null);
        if (audit.qrelResourceAccessCount() != 1
                || !DiagnosticPhase.QRELS_LOADED.name().equals(
                audit.diagnosticPhase())
                || !validSha(rankingPhaseSha256)) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_INVALID");
        }
        FinalizedCases finalized;
        try {
            List<Map<String, Object>> rankingCases = ((List<?>) artifact.get("cases"))
                    .stream()
                    .map(item -> rankingCaseCopy((Map<?, ?>) item))
                    .toList();
            finalized = finalizeCasesAfterQrels(
                    handle.evaluationDataset(
                            rankingPhaseSha256,
                            rankingCasesSha256(rankingCases)),
                    (List<?>) artifact.get("cases"));
        } catch (RuntimeException failure) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_INVALID", failure);
        }
        if (!java.util.Arrays.equals(
                canonicalJsonBytes(finalized.cases()),
                canonicalJsonBytes(artifact.get("cases")))) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_INVALID");
        }
        List<CaseEvidence> evidence = finalized.evidence();
        if (!decision.equals(decide(evidence))) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_INVALID");
        }
        Map<String, Object> expectedSummary = diagnosticSummary(evidence);
        if (expectedSummary.entrySet().stream().anyMatch(entry ->
                !Objects.equals(entry.getValue(), summary.get(entry.getKey())))
                || !validSha(Objects.toString(
                summary.get("rankingPhaseSha256"), null))
                || !Objects.equals(summary.get("rankingPhaseSha256"),
                rankingPhaseHash((List<?>) artifact.get("cases")))
                || !Integer.valueOf(audit.selectionResourceAccessCount()).equals(
                summary.get("selectionResourceAccessCount"))
                || !Integer.valueOf(audit.manifestAccessCount()).equals(
                summary.get("manifestAccessCount"))
                || !Integer.valueOf(audit.holdoutResourceAccessCount()).equals(
                summary.get("holdoutResourceAccessCount"))
                || !Integer.valueOf(audit.qrelResourceAccessBeforeRanking()).equals(
                summary.get("qrelResourceAccessBeforeRanking"))
                || !Integer.valueOf(audit.qrelResourceAccessCount()).equals(
                summary.get("qrelResourceAccessCount"))
                || !audit.diagnosticPhase().equals(
                summary.get("diagnosticPhase"))) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_INVALID");
        }
    }

    private static Map<String, Object> mutableCaseCopy(Map<String, ?> item) {
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.putAll(item);
        return copy;
    }

    private static void validateRankingCasesBeforeQrels(
            RagEvaluationDataset dataset,
            List<? extends Map<String, ?>> cases) {
        if (cases.size() != dataset.queries().size()) {
            throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
        }
        for (int index = 0; index < cases.size(); index++) {
            Map<String, ?> map = cases.get(index);
            if (!map.keySet().equals(RANKING_CASE_KEYS)) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            validateRankingCaseShape(map);
            RagEvaluationDataset.QueryCase query = dataset.queries().get(index);
            if (!query.id().equals(map.get("queryId"))
                    || !query.familyId().equals(map.get("familyId"))
                    || !query.split().equals(map.get("split"))
                    || !sha256Utf8(query.query()).equals(
                    map.get("originalQuerySha256"))
                    || !sha256Utf8(query.retrievalQuery()).equals(
                    map.get("retrievalQuerySha256"))) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            List<String> identifiers = visibleIdentifierTerms(
                    query.retrievalQuery());
            if (!Integer.valueOf(identifiers.size()).equals(
                    map.get("extractedIdentifierCount"))
                    || !sha256Utf8(JSON.toJSONString(identifiers)).equals(
                    map.get("extractedIdentifierHash"))) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            Map<?, ?> arms = requiredMap(map, "arms");
            Map<?, ?> baseline = requiredMap(arms, "BASELINE");
            Map<?, ?> projection = requiredMap(arms, "FIELD_PROJECTION");
            validateArm(baseline);
            validateArm(projection);
            validateStageExactness(dataset, identifiers, baseline);
            validateStageExactness(dataset, identifiers, projection);
            if (!Boolean.TRUE.equals(map.get("baselinePrefixMatchesSut"))
                    || !Boolean.TRUE.equals(map.get("originalContentRestored"))
                    || !Integer.valueOf(1).equals(
                    map.get("businessColbertCalls"))
                    || !Integer.valueOf(1).equals(
                    map.get("baselineFullColbertCalls"))
                    || !Integer.valueOf(1).equals(
                    map.get("projectionFullColbertCalls"))
                    || !Integer.valueOf(1).equals(map.get("sutEmbeddingCalls"))
                    || !Integer.valueOf(0).equals(
                    map.get("businessColbertEmbeddingCalls"))
                    || !Integer.valueOf(0).equals(
                    map.get("baselineFullColbertEmbeddingCalls"))
                    || !Integer.valueOf(0).equals(
                    map.get("projectionFullColbertEmbeddingCalls"))
                    || !rankingCaseHash(
                    query.id(), query.familyId(),
                    castStringMap(baseline), castStringMap(projection))
                    .equals(map.get("rankingSha256"))) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
        }
    }

    private static Map<String, Object> castStringMap(Map<?, ?> value) {
        Map<String, Object> copy = new LinkedHashMap<>();
        value.forEach((key, child) -> {
            if (!(key instanceof String text)) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            copy.put(text, child);
        });
        return copy;
    }

    private static FinalizedCases finalizeCasesAfterQrels(
            RagEvaluationDataset dataset, List<?> cases) {
        if (cases.size() != dataset.queries().size()) {
            throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
        }
        List<CaseEvidence> evidence = new ArrayList<>();
        List<Map<String, Object>> finalizedCases = new ArrayList<>();
        for (int index = 0; index < cases.size(); index++) {
            if (!(cases.get(index) instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            validateRankingCaseShape(map);
            RagEvaluationDataset.QueryCase query = dataset.queries().get(index);
            String role = frozenRole(query);
            if (!query.id().equals(map.get("queryId"))
                    || !query.familyId().equals(map.get("familyId"))
                    || !query.split().equals(map.get("split"))
                    || !sha256Utf8(query.query()).equals(
                    map.get("originalQuerySha256"))
                    || !sha256Utf8(query.retrievalQuery()).equals(
                    map.get("retrievalQuerySha256"))) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            List<String> identifiers = visibleIdentifierTerms(query.retrievalQuery());
            if (!Integer.valueOf(identifiers.size()).equals(
                    map.get("extractedIdentifierCount"))
                    || !sha256Utf8(JSON.toJSONString(identifiers)).equals(
                    map.get("extractedIdentifierHash"))) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            Map<?, ?> arms = requiredMap(map, "arms");
            if (!Boolean.TRUE.equals(map.get("baselinePrefixMatchesSut"))
                    || !Boolean.TRUE.equals(map.get("originalContentRestored"))
                    || !Integer.valueOf(1).equals(
                    map.get("businessColbertCalls"))
                    || !Integer.valueOf(1).equals(
                    map.get("baselineFullColbertCalls"))
                    || !Integer.valueOf(1).equals(
                    map.get("projectionFullColbertCalls"))
                    || !Integer.valueOf(1).equals(map.get("sutEmbeddingCalls"))
                    || !Integer.valueOf(0).equals(
                    map.get("businessColbertEmbeddingCalls"))
                    || !Integer.valueOf(0).equals(
                    map.get("baselineFullColbertEmbeddingCalls"))
                    || !Integer.valueOf(0).equals(
                    map.get("projectionFullColbertEmbeddingCalls"))) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            Map<?, ?> baseline = requiredMap(arms, "BASELINE");
            Map<?, ?> projection = requiredMap(arms, "FIELD_PROJECTION");
            validateArm(baseline);
            validateArm(projection);
            validateStageExactness(dataset, identifiers, baseline);
            validateStageExactness(dataset, identifiers, projection);
            Map<String, Object> rankingEvidence = new LinkedHashMap<>();
            rankingEvidence.put("queryId", query.id());
            rankingEvidence.put("familyId", query.familyId());
            rankingEvidence.put("BASELINE", baseline);
            rankingEvidence.put("FIELD_PROJECTION", projection);
            if (!sha256Utf8(JSON.toJSONString(
                    rankingEvidence, JSONWriter.Feature.MapSortField)).equals(
                    map.get("rankingSha256"))) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            boolean unchanged = sameArmBehavior(baseline, projection);
            RagMetrics.Scores baselineMetrics = metrics(dataset, query, baseline);
            RagMetrics.Scores projectionMetrics = metrics(dataset, query, projection);
            boolean target = "target".equals(role);
            Set<Long> relevant = dataset.qrelsFor(query.id()).keySet().stream()
                    .map(Long::valueOf).collect(Collectors.toSet());
            Set<Long> exactRelevant = relevant.stream().filter(segmentId -> {
                RagEvaluationDataset.CorpusSegment segment =
                        dataset.corpusById().get(String.valueOf(segmentId));
                return segment != null && matchesAnyIdentifier(
                        Objects.toString(segment.metadata().get("documentName"), null),
                        identifiers);
            }).collect(Collectors.toCollection(LinkedHashSet::new));
            boolean qualifying = target && "en".equals(query.language())
                    && exactRelevant.size() == 1
                    && containsAnyStage(baseline, "filterOutput", exactRelevant)
                    && containsAnyStage(baseline, "fullColbert", exactRelevant)
                    && !containsAnyStage(baseline, "businessPrefix", exactRelevant)
                    && containsAnyStage(projection, "businessPrefix", exactRelevant)
                    && containsAnySource(projection, exactRelevant)
                    && containsAnyStage(projection, "contextSegments", exactRelevant);
            String computedClassification = classification(
                    target, exactRelevant, baseline, projection, unchanged,
                    qualifying);
            if (map.containsKey("classification")
                    && !computedClassification.equals(map.get("classification"))) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            boolean safetyValid = safetyValid(
                    role, identifiers, dataset, query, baseline, projection,
                    relevant, unchanged);
            boolean mechanismValid = target && "en".equals(query.language())
                    ? qualifying || unchanged
                    : safetyValid;
            String identifierShape = query.strata().stream()
                    .filter(IDENTIFIER_SHAPES::contains)
                    .findFirst().orElse("none");
            CaseEvidence computed = new CaseEvidence(
                    query.id(), query.familyId(), query.language(), role,
                    identifierShape, target, qualifying, mechanismValid,
                    baselineMetrics.retrievalApAt10(), baselineMetrics.ndcgAt10(),
                    projectionMetrics.retrievalApAt10(),
                    projectionMetrics.ndcgAt10(), unchanged, safetyValid);
            evidence.add(computed);
            Map<String, Object> finalized = rankingCaseCopy(map);
            finalized.put("language", computed.language());
            finalized.put("role", computed.role());
            finalized.put("identifierShape", computed.identifierShape());
            finalized.put("target", computed.target());
            finalized.put("qualifying", computed.qualifying());
            finalized.put("mechanismValid", computed.mechanismValid());
            finalized.put("baselineAP@10", computed.baselineAp());
            finalized.put("baselineNDCG@10", computed.baselineNdcg());
            finalized.put("projectionAP@10", computed.projectionAp());
            finalized.put("projectionNDCG@10", computed.projectionNdcg());
            finalized.put("unchanged", computed.unchanged());
            finalized.put("safetyValid", computed.safetyValid());
            finalized.put("classification", computedClassification);
            finalizedCases.add(finalized);
        }
        return new FinalizedCases(
                List.copyOf(finalizedCases), List.copyOf(evidence));
    }

    private static Map<String, Object> rankingCaseCopy(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (String key : List.of(
                "queryId", "familyId", "split", "originalQuerySha256",
                "retrievalQuerySha256", "extractedIdentifierCount",
                "extractedIdentifierHash", "rankingSha256", "arms",
                "callCounts", "baselinePrefixMatchesSut",
                "originalContentRestored", "businessColbertCalls",
                "baselineFullColbertCalls", "projectionFullColbertCalls",
                "sutEmbeddingCalls", "businessColbertEmbeddingCalls",
                "baselineFullColbertEmbeddingCalls",
                "projectionFullColbertEmbeddingCalls")) {
            copy.put(key, map.get(key));
        }
        return copy;
    }

    private static String frozenRole(RagEvaluationDataset.QueryCase query) {
        if (query.strata().contains("candidate9-target")) {
            return "target";
        }
        return List.of(
                        "no-identifier", "no-exact", "existing-survivor",
                        "irrelevant-exact-safety", "semantic-near-exact-lure",
                        "relevant-nonexact", "multi-id-collision",
                        "long-token-boundary", "boundary-negative").stream()
                .filter(query.strata()::contains)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "CANDIDATE9_EVIDENCE_INVALID"));
    }

    private static Map<?, ?> requiredMap(Map<?, ?> owner, String key) {
        Object value = owner.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
        }
        return map;
    }

    private static List<Map<?, ?>> stage(Map<?, ?> arm, String key) {
        Object value = arm.get(key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
        }
        List<Map<?, ?>> stage = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            stage.add(map);
        }
        return List.copyOf(stage);
    }

    private static void validateArm(Map<?, ?> arm) {
        Set<String> required = new LinkedHashSet<>(List.of(
                "filterOutput", "fullColbert", "businessPrefix",
                "finalSources", "contextSegments", "contextSha256",
                "contextEmpty", "tailDisplacementVerified"));
        if (!arm.keySet().equals(required)
                || !(arm.get("contextSha256") instanceof String contextSha)
                || !validSha(contextSha)
                || !(arm.get("contextEmpty") instanceof Boolean)
                || !(arm.get("tailDisplacementVerified") instanceof Boolean)) {
            throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
        }
        for (String stageName : List.of(
                "filterOutput", "fullColbert", "businessPrefix",
                "finalSources", "contextSegments")) {
            List<Map<?, ?>> values = stage(arm, stageName);
            for (int index = 0; index < values.size(); index++) {
                Map<?, ?> item = values.get(index);
                if (!item.keySet().equals(Set.of(
                        "segmentId", "rank", "score", "exactMatch"))
                        || !(item.get("segmentId") instanceof Number)
                        || !Integer.valueOf(index + 1).equals(item.get("rank"))
                        || !(item.get("score") instanceof Number score)
                        || !Double.isFinite(score.doubleValue())
                        || !(item.get("exactMatch") instanceof Boolean)) {
                    throw new IllegalArgumentException(
                            "CANDIDATE9_EVIDENCE_INVALID");
                }
            }
        }
        if (stage(arm, "businessPrefix").size() > BUSINESS_COLBERT_LIMIT) {
            throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
        }
    }

    private static void validateStageExactness(
            RagEvaluationDataset dataset,
            List<String> identifiers,
            Map<?, ?> arm) {
        for (String stageName : List.of(
                "filterOutput", "fullColbert", "businessPrefix",
                "finalSources", "contextSegments")) {
            for (Map<?, ?> item : stage(arm, stageName)) {
                long segmentId = ((Number) item.get("segmentId")).longValue();
                RagEvaluationDataset.CorpusSegment segment =
                        dataset.corpusById().get(String.valueOf(segmentId));
                if (segment == null
                        || Boolean.TRUE.equals(item.get("exactMatch"))
                        != matchesAnyIdentifier(Objects.toString(
                        segment.metadata().get("documentName"), null), identifiers)) {
                    throw new IllegalArgumentException(
                            "CANDIDATE9_EVIDENCE_INVALID");
                }
            }
        }
    }

    private static boolean sameArmBehavior(Map<?, ?> left, Map<?, ?> right) {
        List<Map<?, ?>> leftSources = stage(left, "finalSources");
        List<Map<?, ?>> rightSources = stage(right, "finalSources");
        return sourceIds(leftSources).equals(sourceIds(rightSources))
                && sourceScores(leftSources).equals(sourceScores(rightSources))
                && Objects.equals(left.get("contextSha256"), right.get("contextSha256"))
                && Objects.equals(left.get("contextEmpty"), right.get("contextEmpty"));
    }

    private static List<Long> sourceIds(List<Map<?, ?>> sources) {
        return sources.stream().map(source -> {
            Object id = source.get("segmentId");
            if (!(id instanceof Number number)) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            return number.longValue();
        }).toList();
    }

    private static List<Double> sourceScores(List<Map<?, ?>> sources) {
        return sources.stream().map(source -> {
            Object score = source.get("score");
            if (!(score instanceof Number number)
                    || !Double.isFinite(number.doubleValue())) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            return number.doubleValue();
        }).toList();
    }

    private static RagMetrics.Scores metrics(
            RagEvaluationDataset dataset,
            RagEvaluationDataset.QueryCase query,
            Map<?, ?> arm) {
        if (!query.answerable()) {
            return new RagMetrics.Scores(0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        return RagMetrics.evaluate(
                dataset.qrelsFor(query.id()),
                sourceIds(stage(arm, "finalSources")).stream()
                        .map(String::valueOf).toList());
    }

    private static boolean containsAnyStage(
            Map<?, ?> arm, String stageName, Set<Long> expected) {
        return sourceIds(stage(arm, stageName)).stream().anyMatch(expected::contains);
    }

    private static boolean containsAnySource(Map<?, ?> arm, Set<Long> expected) {
        return containsAnyStage(arm, "finalSources", expected);
    }

    private static String classification(
            boolean target,
            Set<Long> exactRelevant,
            Map<?, ?> baseline,
            Map<?, ?> projection,
            boolean unchanged,
            boolean qualifying) {
        if (!target) {
            return unchanged ? "NONE" : "SAFETY_CONTROL_CHANGED";
        }
        if (exactRelevant.isEmpty()) {
            return "DOCUMENT_NAME_BOUNDARY_INVALID";
        }
        if (!containsAnyStage(baseline, "filterOutput", exactRelevant)) {
            return "PRE_COLBERT_FILTER_MISS";
        }
        if (containsAnyStage(baseline, "businessPrefix", exactRelevant)) {
            return "BASELINE_ALREADY_PRESENT";
        }
        if (qualifying) {
            return "CONTENT_ONLY_COLBERT_SUPPRESSION_RECOVERED";
        }
        if (!containsAnyStage(projection, "businessPrefix", exactRelevant)) {
            return "PROJECTION_COLBERT_NO_RECOVERY";
        }
        return "POST_COLBERT_DOWNSTREAM_SUPPRESSION";
    }

    private static boolean safetyValid(
            String role,
            List<String> identifiers,
            RagEvaluationDataset dataset,
            RagEvaluationDataset.QueryCase query,
            Map<?, ?> baseline,
            Map<?, ?> projection,
            Set<Long> relevant,
            boolean unchanged) {
        Set<Long> exact = dataset.corpusById().values().stream()
                .filter(segment -> matchesAnyIdentifier(
                        Objects.toString(segment.metadata().get("documentName"), null),
                        identifiers))
                .map(segment -> Long.valueOf(segment.segmentId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return switch (role) {
            case "target" -> true;
            case "no-identifier" -> identifiers.isEmpty() && unchanged;
            case "no-exact", "boundary-negative" -> exact.isEmpty() && unchanged;
            case "existing-survivor" -> containsAnySource(
                    baseline, relevant) && unchanged;
            case "irrelevant-exact-safety", "semantic-near-exact-lure" ->
                    !exact.isEmpty()
                            && containsAnyStage(projection, "filterOutput", exact)
                            && !containsAnyStage(projection, "businessPrefix", exact)
                            && !containsAnySource(projection, exact)
                            && !containsAnyStage(
                            projection, "contextSegments", exact)
                            && unchanged;
            case "relevant-nonexact" -> exact.isEmpty() && unchanged;
            case "long-token-boundary" -> unchanged
                    && Boolean.TRUE.equals(projection.get("tailDisplacementVerified"));
            case "multi-id-collision" -> identifiers.size() == 2
                    && multiIdentifierControlValid(
                    relevant, exact, baseline, projection);
            default -> false;
        };
    }

    private static boolean multiIdentifierControlValid(
            Set<Long> relevant,
            Set<Long> exact,
            Map<?, ?> baseline,
            Map<?, ?> projection) {
        Set<Long> relevantExact = new LinkedHashSet<>(exact);
        relevantExact.retainAll(relevant);
        if (relevantExact.size() != 1) {
            return false;
        }
        Set<Long> irrelevantExact = new LinkedHashSet<>(exact);
        irrelevantExact.removeAll(relevant);
        List<Map<?, ?>> baselineSources = stage(baseline, "finalSources");
        List<Map<?, ?>> projectionSources = stage(projection, "finalSources");
        List<Map<?, ?>> baselineContext = stage(baseline, "contextSegments");
        List<Map<?, ?>> projectionContext = stage(projection, "contextSegments");
        return containsAtMostOne(
                baselineSources, relevantExact)
                && containsAtMostOne(projectionSources, relevantExact)
                && containsAtMostOne(baselineContext, relevantExact)
                && containsAtMostOne(projectionContext, relevantExact)
                && sameRankingExcept(
                baselineSources, projectionSources, relevantExact)
                && sameRankingExcept(
                baselineContext, projectionContext, relevantExact)
                && Boolean.valueOf(baselineContext.isEmpty()).equals(
                baseline.get("contextEmpty"))
                && Boolean.valueOf(projectionContext.isEmpty()).equals(
                projection.get("contextEmpty"))
                && !containsAnySource(baseline, irrelevantExact)
                && !containsAnySource(projection, irrelevantExact)
                && !containsAnyStage(
                baseline, "contextSegments", irrelevantExact)
                && !containsAnyStage(
                projection, "contextSegments", irrelevantExact);
    }

    private static boolean containsAtMostOne(
            List<Map<?, ?>> ranking, Set<Long> allowedToChange) {
        return ranking.stream().filter(item -> allowedToChange.contains(
                        ((Number) item.get("segmentId")).longValue()))
                .count() <= 1L;
    }

    private static boolean sameRankingExcept(
            List<Map<?, ?>> baseline,
            List<Map<?, ?>> projection,
            Set<Long> allowedToChange) {
        List<Map<?, ?>> baselineStable = baseline.stream()
                .filter(item -> !allowedToChange.contains(
                        ((Number) item.get("segmentId")).longValue()))
                .toList();
        List<Map<?, ?>> projectionStable = projection.stream()
                .filter(item -> !allowedToChange.contains(
                        ((Number) item.get("segmentId")).longValue()))
                .toList();
        if (baselineStable.size() != projectionStable.size()) {
            return false;
        }
        for (int index = 0; index < baselineStable.size(); index++) {
            Map<?, ?> left = baselineStable.get(index);
            Map<?, ?> right = projectionStable.get(index);
            if (((Number) left.get("segmentId")).longValue()
                    != ((Number) right.get("segmentId")).longValue()
                    || Double.compare(
                    ((Number) left.get("score")).doubleValue(),
                    ((Number) right.get("score")).doubleValue()) != 0) {
                return false;
            }
        }
        return true;
    }

    static String rankingPhaseHash(List<?> cases) {
        List<String> hashes = cases.stream().map(item -> {
            if (!(item instanceof Map<?, ?> map)
                    || !validSha(Objects.toString(map.get("rankingSha256"), null))) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            return String.valueOf(map.get("rankingSha256"));
        }).toList();
        return sha256Utf8(JSON.toJSONString(
                hashes, JSONWriter.Feature.MapSortField));
    }

    private static String rankingCasesSha256(List<?> cases) {
        List<Map<String, Object>> normalized = cases.stream().map(item -> {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
            }
            return rankingCaseCopy(map);
        }).toList();
        return ShadowContractSupport.sha256(canonicalJsonBytes(normalized));
    }

    static String rankingCaseHash(
            String queryId,
            String familyId,
            Map<String, ?> baseline,
            Map<String, ?> fieldProjection) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("queryId", queryId);
        evidence.put("familyId", familyId);
        evidence.put("BASELINE", baseline);
        evidence.put("FIELD_PROJECTION", fieldProjection);
        return sha256Utf8(JSON.toJSONString(
                evidence, JSONWriter.Feature.MapSortField));
    }

    private static String sha256Utf8(String value) {
        return ShadowContractSupport.sha256(
                Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
    }

    private static void validateRankingCaseShape(Map<?, ?> map) {
        Object armsValue = map.get("arms");
        Object callCountsValue = map.get("callCounts");
        if (!map.keySet().equals(RANKING_CASE_KEYS)
                && !map.keySet().equals(FINALIZED_CASE_KEYS)
                || !(map.get("queryId") instanceof String)
                || !(map.get("familyId") instanceof String)
                || !(map.get("split") instanceof String)
                || !validSha(Objects.toString(
                map.get("originalQuerySha256"), null))
                || !validSha(Objects.toString(
                map.get("retrievalQuerySha256"), null))
                || !(map.get("extractedIdentifierCount") instanceof Number count)
                || count.intValue() < 0 || count.intValue() > 2
                || !validSha(Objects.toString(
                map.get("extractedIdentifierHash"), null))
                || !validSha(Objects.toString(map.get("rankingSha256"), null))
                || !(armsValue instanceof Map<?, ?> arms)
                || !arms.keySet().equals(Set.of("BASELINE", "FIELD_PROJECTION"))
                || !(callCountsValue instanceof Map<?, ?> callCounts)) {
            throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
        }
        List<String> requiredCounts = List.of(
                "externalEmbeddingBatchCalls", "colbertEncodedQueryTokens",
                "colbertEncodedDocumentTokens", "llmPromptTokens",
                "llmCompletionTokens", "cost", "sutDbCalls",
                "diagnosticContextDbCalls",
                "addedVectorCalls", "addedMetadataCalls", "addedGraphCalls",
                "addedNetworkCalls");
        if (!callCounts.keySet().equals(new LinkedHashSet<>(requiredCounts))
                || requiredCounts.stream().anyMatch(key ->
                !(callCounts.get(key) instanceof Number number)
                        || !Double.isFinite(number.doubleValue())
                        || number.doubleValue() < 0.0D)) {
            throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
        }
        if (((Number) callCounts.get("externalEmbeddingBatchCalls")).longValue() != 0L
                || ((Number) callCounts.get("colbertEncodedQueryTokens")).longValue() != 0L
                || ((Number) callCounts.get("llmPromptTokens")).longValue() != 0L
                || ((Number) callCounts.get("llmCompletionTokens")).longValue() != 0L
                || Double.compare(((Number) callCounts.get("cost")).doubleValue(),
                0.0D) != 0
                || ((Number) callCounts.get("addedVectorCalls")).longValue() != 0L
                || ((Number) callCounts.get("addedMetadataCalls")).longValue() != 0L
                || ((Number) callCounts.get("addedGraphCalls")).longValue() != 0L
                || ((Number) callCounts.get("addedNetworkCalls")).longValue() != 0L) {
            throw new IllegalArgumentException("CANDIDATE9_EVIDENCE_INVALID");
        }
    }

    static void validateConfig(Map<?, ?> config) {
        Object remoteValue = config.get("kbRemoteReranking");
        Object colbertValue = config.get("colbert");
        Object executorValue = config.get("executor");
        Object forkValue = config.get("fork");
        Object nativeValue = config.get("native");
        Object featureHashValue = config.get("featureSourceHash");
        Object inventoryValue = config.get("providerInventory");
        if (!Boolean.TRUE.equals(config.get("candidate3Enabled"))
                || !Boolean.FALSE.equals(config.get("candidate9Enabled"))
                || !Boolean.TRUE.equals(config.get("candidate9DiagnosticEnabled"))
                || !Boolean.FALSE.equals(config.get("identifierAware"))
                || !Boolean.FALSE.equals(config.get("localRerankerEnabled"))
                || !Boolean.FALSE.equals(config.get("onnxRerankerEnabled"))
                || !Boolean.FALSE.equals(config.get("promotionEnabled"))
                || !Boolean.TRUE.equals(config.get("shadowEnabled"))
                || !Boolean.FALSE.equals(config.get("compareStable"))
                || !"UTF-8".equals(config.get("fileEncoding"))
                || !Integer.valueOf(10).equals(config.get("topK"))
                || !Integer.valueOf(BUSINESS_JAVA_LIMIT).equals(
                config.get("keywordCandidateTopK"))
                || !Integer.valueOf(BUSINESS_SQL_LIMIT).equals(
                config.get("keywordSqlLimit"))
                || !Integer.valueOf(BUSINESS_COLBERT_LIMIT).equals(
                config.get("businessColbertLimit"))
                || !"en-US".equals(config.get("locale"))
                || !"UTC".equals(config.get("timezone"))
                || !"matched-visible-identifiers-prefix-v1".equals(
                config.get("candidate9ProjectionPolicy"))
                || !"ascii-space-visible-identifier-v1".equals(
                config.get("candidate9QueryVisibilityPolicy"))
                || !"unicode-letter-number-boundary-v1".equals(
                config.get("candidate9BoundaryPolicy"))
                || !"prefix-head-128-v1".equals(
                config.get("candidate9TokenPolicy"))
                || !"hash-colbert-deterministic-fail-closed-v1".equals(
                config.get("candidate9EligibilityPolicy"))
                || !"active-request-propagation-v1".equals(
                config.get("candidate9FailurePolicy"))
                || !"qrel-after-ranking-v1".equals(
                config.get("candidate9EvaluationPolicy"))
                || !(remoteValue instanceof Map<?, ?> remote)
                || !Boolean.FALSE.equals(remote.get("enabled"))
                || !Boolean.FALSE.equals(remote.get("providerPresent"))
                || !Boolean.FALSE.equals(remote.get("modelPresent"))
                || !(colbertValue instanceof Map<?, ?> colbert)
                || !Boolean.TRUE.equals(colbert.get("enabled"))
                || !Integer.valueOf(MAX_TOKENS_PER_DOCUMENT).equals(
                colbert.get("resolvedMaxTokensPerDoc"))
                || !Boolean.FALSE.equals(colbert.get("embeddingPlatformPresent"))
                || !Boolean.FALSE.equals(colbert.get("embeddingBaseUrlPresent"))
                || !Boolean.FALSE.equals(colbert.get("embeddingApiKeyPresent"))
                || !Boolean.FALSE.equals(colbert.get("embeddingModelPresent"))
                || !(executorValue instanceof Map<?, ?>)
                || !(forkValue instanceof Map<?, ?> fork)
                || !Integer.valueOf(1).equals(fork.get("count"))
                || !Boolean.FALSE.equals(fork.get("reuse"))
                || !(nativeValue instanceof Map<?, ?> nativeConfig)
                || !(nativeConfig.get("available") instanceof Boolean)
                || !Boolean.FALSE.equals(nativeConfig.get("pathPresent"))
                || !Boolean.TRUE.equals(
                nativeConfig.get("noNativeLibraryPathPresent"))
                || !(featureHashValue instanceof Map<?, ?> featureHashes)
                || featureHashes.size() != 6
                || featureHashes.values().stream().map(String::valueOf)
                .anyMatch(value -> !validSha(value))
                || !(inventoryValue instanceof List<?> inventory)
                || inventory.isEmpty()
                || !inventory.equals(inventory.stream().map(String::valueOf)
                .sorted().toList())
                || inventory.stream().map(String::valueOf)
                .anyMatch(item -> !ELIGIBLE_PROVIDER_CLASSES.contains(item))
                || !inventory.contains(
                RERANKER_PACKAGE + "DeterministicRerankerProvider")) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_CONFIG_INVALID");
        }
    }

    private static void rejectForbiddenFields(Object value) {
        Object jsonTree;
        try {
            jsonTree = JSON.parse(JSON.toJSONString(value));
        } catch (RuntimeException failure) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_INVALID", failure);
        }
        rejectForbiddenJsonTree(jsonTree);
    }

    private static void rejectForbiddenJsonTree(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = Objects.toString(entry.getKey(), "")
                        .replace("_", "").replace("-", "")
                        .toLowerCase(Locale.ROOT);
                if (FORBIDDEN_ARTIFACT_FIELDS.contains(key)) {
                    throw new IllegalStateException(
                            "CANDIDATE9_FORBIDDEN_ARTIFACT_FIELD");
                }
                rejectForbiddenJsonTree(entry.getValue());
            }
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(RagCandidate9DiagnosticSupport::rejectForbiddenJsonTree);
        }
    }

    private static DatasetFiles readDatasetFiles(Path directory, boolean holdout) {
        return readDatasetFiles(directory, holdout, null);
    }

    private static DatasetFiles readDatasetFiles(
            Path directory, boolean holdout, AccessCounter accessCounter) {
        Path normalized = requireDirectory(directory,
                "CANDIDATE9_DATASET_DIRECTORY_INVALID");
        Path corpusPath = requireRegular(normalized.resolve("corpus.jsonl"),
                "CANDIDATE9_DATASET_RESOURCE_INVALID");
        Path queriesPath = requireRegular(normalized.resolve("queries.jsonl"),
                "CANDIDATE9_DATASET_RESOURCE_INVALID");
        Path qrelsPath = requireRegular(normalized.resolve("qrels.tsv"),
                "CANDIDATE9_DATASET_RESOURCE_INVALID");
        Path pressurePath = requireRegular(normalized.resolve("pressure.json"),
                "CANDIDATE9_DATASET_RESOURCE_INVALID");
        try {
            ResourceBytes corpusResource = readDatasetResource(
                    corpusPath, holdout, accessCounter);
            ResourceBytes queriesResource = readDatasetResource(
                    queriesPath, holdout, accessCounter);
            ResourceBytes qrelsResource = readDatasetResource(
                    qrelsPath, holdout, accessCounter);
            ResourceBytes pressureResource = readDatasetResource(
                    pressurePath, holdout, accessCounter);
            Map<String, RagEvaluationDataset.CorpusSegment> core = parseCorpus(
                    corpusResource.text());
            List<RagEvaluationDataset.QueryCase> queries = parseQueries(
                    queriesResource.text());
            ParsedQrels qrels = parseQrels(
                    qrelsResource.text());
            PressureSpec pressure = parsePressure(
                    pressureResource.text());
            Map<String, RagEvaluationDataset.CorpusSegment> expanded =
                    expandCorpus(core, queries, pressure, holdout);
            RagEvaluationDataset dataset = new RagEvaluationDataset(
                    expanded, queries, qrels.qrels());
            RagEvaluationDatasetLoader.validate(dataset);
            validateShape(dataset, qrels.count(), pressure, holdout);
            Map<String, ResourceHash> resources = new LinkedHashMap<>();
            resources.put("corpus", corpusResource.resourceHash());
            resources.put("queries", queriesResource.resourceHash());
            resources.put("qrels", qrelsResource.resourceHash());
            resources.put("pressure", pressureResource.resourceHash());
            return new DatasetFiles(
                    dataset, qrels.count(), pressure, Map.copyOf(resources));
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "CANDIDATE9_DATASET_RESOURCE_READ_FAILED", failure);
        }
    }

    private static RankingDatasetFiles readRankingDatasetFiles(
            Path directory, AccessCounter accessCounter) {
        Path normalized = requireDirectory(directory,
                "CANDIDATE9_DATASET_DIRECTORY_INVALID");
        Path corpusPath = requireRegular(normalized.resolve("corpus.jsonl"),
                "CANDIDATE9_DATASET_RESOURCE_INVALID");
        Path queriesPath = requireRegular(normalized.resolve("queries.jsonl"),
                "CANDIDATE9_DATASET_RESOURCE_INVALID");
        Path pressurePath = requireRegular(normalized.resolve("pressure.json"),
                "CANDIDATE9_DATASET_RESOURCE_INVALID");
        try {
            ResourceBytes corpusResource = readDatasetResource(
                    corpusPath, false, accessCounter);
            ResourceBytes queriesResource = readDatasetResource(
                    queriesPath, false, accessCounter);
            ResourceBytes pressureResource = readDatasetResource(
                    pressurePath, false, accessCounter);
            List<RagEvaluationDataset.QueryCase> queries = parseQueries(
                    queriesResource.text());
            PressureSpec pressure = parsePressure(pressureResource.text());
            Map<String, RagEvaluationDataset.CorpusSegment> expanded =
                    expandCorpus(parseCorpus(corpusResource.text()), queries,
                            pressure, false);
            RagEvaluationDataset dataset = new RagEvaluationDataset(
                    expanded, queries, Map.of());
            validateRankingDataset(dataset, pressure);
            accessCounter.pressure = pressure;
            Map<String, ResourceHash> resources = new LinkedHashMap<>();
            resources.put("corpus", corpusResource.resourceHash());
            resources.put("queries", queriesResource.resourceHash());
            resources.put("pressure", pressureResource.resourceHash());
            return new RankingDatasetFiles(
                    dataset, pressure, Map.copyOf(resources), normalized);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "CANDIDATE9_DATASET_RESOURCE_READ_FAILED", failure);
        }
    }

    private static void validateRankingDataset(
            RagEvaluationDataset dataset, PressureSpec pressure) {
        if (dataset.queries().size() != SELECTION_QUERY_COUNT
                || dataset.corpusById().size() != SELECTION_SEGMENT_COUNT
                || pressure.seed() != SELECTION_SEED
                || pressure.pressureCount() != SELECTION_PRESSURE_COUNT
                || pressure.lexicalPerTargetFamily() != LEXICAL_PER_TARGET) {
            throw new IllegalArgumentException(
                    "CANDIDATE9_DATASET_COUNT_MISMATCH");
        }
        Map<String, List<RagEvaluationDataset.QueryCase>> families =
                dataset.queries().stream().collect(Collectors.groupingBy(
                        RagEvaluationDataset.QueryCase::familyId,
                        LinkedHashMap::new, Collectors.toList()));
        if (families.size() != 17 || families.values().stream().anyMatch(family ->
                family.size() != 2
                        || !family.stream().map(RagEvaluationDataset.QueryCase::language)
                        .collect(Collectors.toSet()).equals(Set.of("zh", "en")))) {
            throw new IllegalArgumentException(
                    "CANDIDATE9_BILINGUAL_FAMILY_INVALID");
        }
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            if (!"selection".equals(query.split())
                    || !query.id().startsWith("c9s-")
                    || !identifierTerms(query.query()).equals(
                    identifierTerms(query.retrievalQuery()))) {
                throw new IllegalArgumentException(
                        "CANDIDATE9_IDENTIFIER_FIXTURE_INVALID");
            }
            List<String> identifiers = identifierTerms(query.retrievalQuery());
            if (!identifiers.isEmpty()
                    && !visibleIdentifierTerms(query.retrievalQuery())
                    .equals(identifiers)) {
                throw new IllegalArgumentException(
                        "CANDIDATE9_QUERY_VISIBILITY_INVALID");
            }
        }
        Set<String> documents = new LinkedHashSet<>();
        for (RagEvaluationDataset.CorpusSegment segment
                : dataset.corpusById().values()) {
            long segmentId = Long.parseLong(segment.segmentId());
            long documentId = Long.parseLong(segment.documentId());
            if (segmentId < SELECTION_SEGMENT_ID_MIN
                    || segmentId > SELECTION_SEGMENT_ID_MAX
                    || documentId < SELECTION_DOCUMENT_ID_MIN
                    || documentId > SELECTION_DOCUMENT_ID_MAX
                    || segment.parentSegmentId() != null
                    || !documents.add(segment.documentId())
                    || !(segment.metadata().get("documentName")
                    instanceof String documentName)
                    || documentName.isBlank()) {
                throw new IllegalArgumentException(
                        "CANDIDATE9_ID_ISOLATION_INVALID");
            }
        }
    }

    private static ResourceBytes readDatasetResource(
            Path path, boolean holdout, AccessCounter accessCounter) throws IOException {
        return readDatasetResource(path, holdout, accessCounter, false);
    }

    private static ResourceBytes readDatasetResource(
            Path path,
            boolean holdout,
            AccessCounter accessCounter,
            boolean qrelResource) throws IOException {
        if (accessCounter != null) {
            accessCounter.openDatasetResource(holdout, qrelResource);
        }
        byte[] bytes = Files.readAllBytes(path);
        return new ResourceBytes(
                new String(bytes, StandardCharsets.UTF_8),
                new ResourceHash(path.getFileName().toString(),
                        ShadowContractSupport.sha256(bytes)));
    }

    private static Map<String, RagEvaluationDataset.CorpusSegment> parseCorpus(
            String value) {
        Map<String, RagEvaluationDataset.CorpusSegment> corpus = new LinkedHashMap<>();
        for (String line : dataLines(value)) {
            JSONObject json = JSON.parseObject(line);
            JSONObject metadata = json.getJSONObject("metadata");
            RagEvaluationDataset.CorpusSegment segment =
                    new RagEvaluationDataset.CorpusSegment(
                            required(json, "segmentId"),
                            required(json, "documentId"),
                            required(json, "content"),
                            json.getString("parentSegmentId"),
                            metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
            if (corpus.putIfAbsent(segment.segmentId(), segment) != null) {
                throw new IllegalArgumentException("CANDIDATE9_DUPLICATE_SEGMENT");
            }
        }
        return corpus;
    }

    private static List<RagEvaluationDataset.QueryCase> parseQueries(String value) {
        List<RagEvaluationDataset.QueryCase> queries = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (String line : dataLines(value)) {
            JSONObject json = JSON.parseObject(line);
            String id = required(json, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("CANDIDATE9_DUPLICATE_QUERY");
            }
            queries.add(new RagEvaluationDataset.QueryCase(
                    id,
                    required(json, "familyId"),
                    required(json, "query"),
                    required(json, "retrievalQuery"),
                    strings(json.getJSONArray("history")),
                    required(json, "language"),
                    new LinkedHashSet<>(strings(json.getJSONArray("strata"))),
                    required(json, "split"),
                    json.getBooleanValue("answerable"),
                    json.getString("referenceAnswer"),
                    strings(json.getJSONArray("referenceClaims"))));
        }
        return List.copyOf(queries);
    }

    private static ParsedQrels parseQrels(String value) {
        Map<String, Map<String, Integer>> qrels = new LinkedHashMap<>();
        int count = 0;
        for (String line : dataLines(value)) {
            if (line.equals("queryId\tsegmentId\tgrade")) {
                continue;
            }
            String[] columns = line.split("\t", -1);
            if (columns.length != 3) {
                throw new IllegalArgumentException("CANDIDATE9_QREL_INVALID");
            }
            Map<String, Integer> queryQrels = qrels.computeIfAbsent(
                    columns[0], ignored -> new LinkedHashMap<>());
            if (queryQrels.putIfAbsent(
                    columns[1], Integer.parseInt(columns[2])) != null) {
                throw new IllegalArgumentException("CANDIDATE9_DUPLICATE_QREL");
            }
            count++;
        }
        Map<String, Map<String, Integer>> immutable = new LinkedHashMap<>();
        qrels.forEach((queryId, grades) -> immutable.put(queryId, Map.copyOf(grades)));
        return new ParsedQrels(Map.copyOf(immutable), count);
    }

    private static PressureSpec parsePressure(String value) {
        JSONObject json = JSON.parseObject(value);
        Set<String> expected = Set.of(
                "generator", "version", "seed", "lexicalPerTargetFamily",
                "pressureCount", "neutralColbertCue", "lexicalSegmentIdStart",
                "pressureSegmentIdStart", "lexicalDocumentIdStart",
                "pressureDocumentIdStart");
        if (!json.keySet().equals(expected)
                || !"candidate9-colbert-pressure-v1".equals(json.getString("generator"))
                || json.getIntValue("version") != 1) {
            throw new IllegalArgumentException("CANDIDATE9_PRESSURE_SPEC_INVALID");
        }
        return new PressureSpec(
                json.getString("generator"),
                json.getIntValue("version"),
                json.getLongValue("seed"),
                json.getIntValue("lexicalPerTargetFamily"),
                json.getIntValue("pressureCount"),
                required(json, "neutralColbertCue"),
                json.getLongValue("lexicalSegmentIdStart"),
                json.getLongValue("pressureSegmentIdStart"),
                json.getLongValue("lexicalDocumentIdStart"),
                json.getLongValue("pressureDocumentIdStart"));
    }

    private static Map<String, RagEvaluationDataset.CorpusSegment> expandCorpus(
            Map<String, RagEvaluationDataset.CorpusSegment> core,
            List<RagEvaluationDataset.QueryCase> queries,
            PressureSpec pressure,
            boolean holdout) {
        Map<String, RagEvaluationDataset.CorpusSegment> expanded =
                new LinkedHashMap<>(core);
        Map<String, RagEvaluationDataset.QueryCase> targets = queries.stream()
                .filter(query -> query.familyId().matches("c9[sh]-t\\d+"))
                .collect(Collectors.toMap(
                        RagEvaluationDataset.QueryCase::familyId,
                        query -> query,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<String> families = targets.keySet().stream().sorted().toList();
        long segmentId = pressure.lexicalSegmentIdStart();
        long documentId = pressure.lexicalDocumentIdStart();
        for (String familyId : families) {
            String shape = targets.get(familyId).strata().stream()
                    .filter(IDENTIFIER_SHAPES::contains).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "CANDIDATE9_IDENTIFIER_SHAPE_INVALID"));
            for (int index = 1; index <= pressure.lexicalPerTargetFamily(); index++) {
                putGenerated(expanded, segmentId++, documentId++,
                        pressure.neutralColbertCue()
                                + " Routine lexical admission note " + index + ".",
                        Map.of(
                                "candidate9Role", "lexical-distractor",
                                "familyId", familyId,
                                "identifierShape", shape,
                                "documentName", (holdout ? "holdout" : "selection")
                                        + "-lexical-" + familyId + "-" + index));
            }
        }
        if (segmentId != pressure.pressureSegmentIdStart()
                || documentId != pressure.pressureDocumentIdStart()) {
            throw new IllegalArgumentException("CANDIDATE9_PRESSURE_ID_RULE_INVALID");
        }
        if (pressure.pressureCount() != families.size() * PRESSURE_PER_TARGET) {
            throw new IllegalArgumentException(
                    "CANDIDATE9_PRESSURE_FAMILY_COUNT_INVALID");
        }
        int globalIndex = 1;
        for (String familyId : families) {
            for (int index = 1; index <= PRESSURE_PER_TARGET; index++) {
                putGenerated(expanded, segmentId++, documentId++,
                        pressure.neutralColbertCue()
                                + " Pressure admission record.",
                        Map.of(
                                "candidate9Role", "pressure-distractor",
                                "familyId", familyId,
                                "identifierShape", "none",
                                "documentName", (holdout ? "holdout" : "selection")
                                        + "-pressure-" + familyId + "-" + index
                                        + "-" + globalIndex++));
            }
        }
        if (segmentId != pressure.pressureSegmentIdStart()
                + pressure.pressureCount()
                || documentId != pressure.pressureDocumentIdStart()
                + pressure.pressureCount()) {
            throw new IllegalArgumentException(
                    "CANDIDATE9_PRESSURE_ID_RULE_INVALID");
        }
        return Map.copyOf(expanded);
    }

    private static void putGenerated(
            Map<String, RagEvaluationDataset.CorpusSegment> corpus,
            long segmentId,
            long documentId,
            String content,
            Map<String, Object> metadata) {
        String key = String.valueOf(segmentId);
        RagEvaluationDataset.CorpusSegment segment =
                new RagEvaluationDataset.CorpusSegment(
                        key, String.valueOf(documentId), content, null, metadata);
        if (corpus.putIfAbsent(key, segment) != null) {
            throw new IllegalArgumentException("CANDIDATE9_GENERATED_ID_COLLISION");
        }
    }

    private static void validateShape(
            RagEvaluationDataset dataset,
            int qrelCount,
            PressureSpec pressure,
            boolean holdout) {
        int expectedQueries = holdout ? HOLDOUT_QUERY_COUNT : SELECTION_QUERY_COUNT;
        int expectedSegments = holdout ? HOLDOUT_SEGMENT_COUNT : SELECTION_SEGMENT_COUNT;
        int expectedQrels = holdout ? HOLDOUT_QREL_COUNT : SELECTION_QREL_COUNT;
        int targetFamilies = holdout ? HOLDOUT_TARGET_FAMILIES : SELECTION_TARGET_FAMILIES;
        int noIdFamilies = holdout ? HOLDOUT_NO_ID_FAMILIES : SELECTION_NO_ID_FAMILIES;
        int noExactFamilies = holdout ? HOLDOUT_NO_EXACT_FAMILIES : SELECTION_NO_EXACT_FAMILIES;
        int exactOnlyFamilies = holdout
                ? HOLDOUT_EXACT_ONLY_FAMILIES : SELECTION_EXACT_ONLY_FAMILIES;
        int lureFamilies = holdout ? HOLDOUT_LURE_FAMILIES : SELECTION_LURE_FAMILIES;
        int survivorFamilies = holdout
                ? HOLDOUT_SURVIVOR_FAMILIES : SELECTION_SURVIVOR_FAMILIES;
        int multiIdFamilies = holdout
                ? HOLDOUT_MULTI_ID_FAMILIES : SELECTION_MULTI_ID_FAMILIES;
        int relevantNonexactFamilies = holdout
                ? HOLDOUT_RELEVANT_NONEXACT_FAMILIES
                : SELECTION_RELEVANT_NONEXACT_FAMILIES;
        int longTokenFamilies = holdout
                ? HOLDOUT_LONG_TOKEN_FAMILIES : SELECTION_LONG_TOKEN_FAMILIES;
        int boundaryNegativeFamilies = holdout
                ? HOLDOUT_BOUNDARY_NEGATIVE_FAMILIES
                : SELECTION_BOUNDARY_NEGATIVE_FAMILIES;
        int pressureCount = holdout ? HOLDOUT_PRESSURE_COUNT : SELECTION_PRESSURE_COUNT;
        long segmentMin = holdout ? HOLDOUT_SEGMENT_ID_MIN : SELECTION_SEGMENT_ID_MIN;
        long segmentMax = holdout ? HOLDOUT_SEGMENT_ID_MAX : SELECTION_SEGMENT_ID_MAX;
        long documentMin = holdout ? HOLDOUT_DOCUMENT_ID_MIN : SELECTION_DOCUMENT_ID_MIN;
        long documentMax = holdout ? HOLDOUT_DOCUMENT_ID_MAX : SELECTION_DOCUMENT_ID_MAX;
        String prefix = holdout ? "c9h-" : "c9s-";
        String split = holdout ? "holdout" : "selection";
        if (dataset.queries().size() != expectedQueries
                || dataset.corpusById().size() != expectedSegments
                || qrelCount != expectedQrels
                || pressure.pressureCount() != pressureCount
                || pressure.lexicalPerTargetFamily() != LEXICAL_PER_TARGET
                || pressure.seed() != (holdout ? HOLDOUT_SEED : SELECTION_SEED)) {
            throw new IllegalArgumentException("CANDIDATE9_DATASET_COUNT_MISMATCH");
        }
        Map<String, List<RagEvaluationDataset.QueryCase>> families =
                dataset.queries().stream().collect(Collectors.groupingBy(
                        RagEvaluationDataset.QueryCase::familyId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        long targetCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "t")).count();
        long noIdCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "n")).count();
        long noExactCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "x")).count();
        long exactOnlyCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "e")).count();
        long lureCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "s")).count();
        long survivorCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "u")).count();
        long multiIdCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "m")).count();
        long relevantNonexactCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "r")).count();
        long longTokenCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "l")).count();
        long boundaryNegativeCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "b")).count();
        if (targetCount != targetFamilies || noIdCount != noIdFamilies
                || noExactCount != noExactFamilies
                || exactOnlyCount != exactOnlyFamilies || lureCount != lureFamilies
                || survivorCount != survivorFamilies || multiIdCount != multiIdFamilies
                || relevantNonexactCount != relevantNonexactFamilies
                || longTokenCount != longTokenFamilies
                || boundaryNegativeCount != boundaryNegativeFamilies
                || families.size() != targetFamilies + noIdFamilies
                + noExactFamilies + exactOnlyFamilies + lureFamilies
                + survivorFamilies + multiIdFamilies + relevantNonexactFamilies
                + longTokenFamilies + boundaryNegativeFamilies) {
            throw new IllegalArgumentException("CANDIDATE9_FAMILY_COUNT_MISMATCH");
        }
        Map<String, String> relevantSegmentFamilies = new LinkedHashMap<>();
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            for (String segmentId : dataset.qrelsFor(query.id()).keySet()) {
                String existingFamily = relevantSegmentFamilies.putIfAbsent(
                        segmentId, query.familyId());
                if (existingFamily != null
                        && !existingFamily.equals(query.familyId())) {
                    throw new IllegalArgumentException(
                            "CANDIDATE9_CROSS_FAMILY_SHARING_INVALID");
                }
            }
        }
        List<String> targetFamilyIds = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "t"))
                .sorted()
                .toList();
        validateCoreRoleTable(
                dataset, families, pressure, segmentMin,
                relevantSegmentFamilies);
        validateGeneratedFamilyAttribution(
                dataset, pressure, targetFamilyIds, relevantSegmentFamilies);
        Map<String, Integer> shapeCounts = new LinkedHashMap<>();
        for (Map.Entry<String, List<RagEvaluationDataset.QueryCase>> entry
                : families.entrySet()) {
            List<RagEvaluationDataset.QueryCase> family = entry.getValue();
            Set<String> languages = family.stream()
                    .map(RagEvaluationDataset.QueryCase::language)
                    .collect(Collectors.toSet());
            if (family.size() != 2 || !languages.equals(Set.of("zh", "en"))
                    || family.stream().anyMatch(query ->
                    !split.equals(query.split()) || !query.id().startsWith(prefix))) {
                throw new IllegalArgumentException(
                        "CANDIDATE9_BILINGUAL_FAMILY_INVALID");
            }
            boolean target = entry.getKey().startsWith(prefix + "t");
            boolean noId = entry.getKey().startsWith(prefix + "n");
            boolean noExact = entry.getKey().startsWith(prefix + "x");
            boolean exactOnly = entry.getKey().startsWith(prefix + "e");
            boolean lure = entry.getKey().startsWith(prefix + "s");
            boolean survivor = entry.getKey().startsWith(prefix + "u");
            boolean multiId = entry.getKey().startsWith(prefix + "m");
            boolean relevantNonexact = entry.getKey().startsWith(prefix + "r");
            boolean longToken = entry.getKey().startsWith(prefix + "l");
            boolean boundaryNegative = entry.getKey().startsWith(prefix + "b");
            int expectedRelevant = target ? 3
                    : noId || survivor || multiId || relevantNonexact || longToken ? 1 : 0;
            Set<String> shared = null;
            String shape = null;
            for (RagEvaluationDataset.QueryCase query : family) {
                Set<String> qrels = dataset.qrelsFor(query.id()).keySet();
                if (qrels.size() != expectedRelevant
                        || query.answerable() != (expectedRelevant > 0)) {
                    throw new IllegalArgumentException("CANDIDATE9_QREL_SHAPE_INVALID");
                }
                if (shared == null) {
                    shared = Set.copyOf(qrels);
                } else if (!shared.equals(qrels)) {
                    throw new IllegalArgumentException(
                            "CANDIDATE9_FAMILY_QREL_SHARING_INVALID");
                }
                List<String> identifiers = identifierTerms(
                        query.retrievalQuery());
                if (!noId) {
                    int expectedIdentifiers = multiId ? 2 : 1;
                    if (identifiers.size() != expectedIdentifiers
                            || !visibleIdentifierTerms(query.retrievalQuery())
                            .equals(identifiers)
                            || !identifiers.equals(identifierTerms(query.query()))) {
                        throw new IllegalArgumentException(
                                "CANDIDATE9_IDENTIFIER_FIXTURE_INVALID");
                    }
                } else if (!identifiers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "CANDIDATE9_NO_IDENTIFIER_CONTROL_INVALID");
                }
                List<RagEvaluationDataset.CorpusSegment> exactSegments =
                        dataset.corpusById().values().stream()
                                .filter(segment -> matchesAnyIdentifier(
                                        Objects.toString(segment.metadata()
                                                .get("documentName"), null), identifiers))
                                .toList();
                if (target) {
                    String currentShape = query.strata().stream()
                            .filter(IDENTIFIER_SHAPES::contains).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "CANDIDATE9_IDENTIFIER_SHAPE_INVALID"));
                    if (shape == null) {
                        shape = currentShape;
                    } else if (!shape.equals(currentShape)) {
                        throw new IllegalArgumentException(
                                "CANDIDATE9_IDENTIFIER_SHAPE_INVALID");
                    }
                    long exactRelevant = qrels.stream().filter(segmentId -> {
                        RagEvaluationDataset.CorpusSegment segment =
                                dataset.corpusById().get(segmentId);
                        return segment != null && matchesAnyIdentifier(
                                Objects.toString(
                                        segment.metadata().get("documentName"), null),
                                identifiers);
                    }).count();
                    if (exactRelevant != 1L || exactSegments.size() != 1
                            || !entry.getKey().equals(Objects.toString(
                            exactSegments.get(0).metadata().get("familyId"), ""))
                            || !"target-evidence".equals(Objects.toString(
                            exactSegments.get(0).metadata().get("candidate9Role"), ""))) {
                        throw new IllegalArgumentException(
                            "CANDIDATE9_EXACT_EVIDENCE_INVALID");
                    }
                    RagEvaluationDataset.CorpusSegment anchor = exactSegments.get(0);
                    if (tokenCount(anchor.content()) < 1) {
                        throw new IllegalArgumentException(
                                "CANDIDATE9_CONTENT_TOKEN_INVALID");
                    }
                } else if (noExact && !exactSegments.isEmpty()) {
                    throw new IllegalArgumentException(
                            "CANDIDATE9_NO_EXACT_CONTROL_INVALID");
                } else if (exactOnly || lure || survivor || longToken) {
                    String expectedRole = exactOnly ? "irrelevant-exact-safety"
                            : lure ? "semantic-near-exact-lure"
                            : survivor ? "existing-survivor" : "long-token-boundary";
                    if (exactSegments.size() != 1
                            || !entry.getKey().equals(Objects.toString(
                            exactSegments.get(0).metadata().get("familyId"), ""))
                            || !expectedRole.equals(Objects.toString(
                            exactSegments.get(0).metadata().get("candidate9Role"), ""))
                            || (longToken && tokenCount(exactSegments.get(0).content()) != 128)) {
                        throw new IllegalArgumentException(
                                "CANDIDATE9_SAFETY_CONTROL_INVALID");
                    }
                } else if (multiId) {
                    long eligible = exactSegments.stream().filter(segment ->
                                    "multi-id-relevant".equals(Objects.toString(
                                            segment.metadata().get("candidate9Role"), ""))
                                            && dataset.qrelsFor(query.id())
                                            .containsKey(segment.segmentId()))
                            .count();
                    long collision = exactSegments.stream().filter(segment ->
                                    "multi-id-collision".equals(Objects.toString(
                                            segment.metadata().get("candidate9Role"), ""))
                                            && !dataset.qrelsFor(query.id())
                                            .containsKey(segment.segmentId()))
                            .count();
                    if (exactSegments.size() != 2 || eligible != 1L || collision != 1L) {
                        throw new IllegalArgumentException(
                                "CANDIDATE9_MULTI_IDENTIFIER_CONTROL_INVALID");
                    }
                } else if (relevantNonexact && !exactSegments.isEmpty()) {
                    throw new IllegalArgumentException(
                            "CANDIDATE9_RELEVANT_NONEXACT_CONTROL_INVALID");
                } else if (boundaryNegative && !exactSegments.isEmpty()) {
                    throw new IllegalArgumentException(
                            "CANDIDATE9_BOUNDARY_NEGATIVE_CONTROL_INVALID");
                }
            }
            if (target) {
                shapeCounts.merge(shape, 1, Integer::sum);
            }
        }
        int perShape = targetFamilies / IDENTIFIER_SHAPES.size();
        if (!shapeCounts.equals(Map.of(
                "numeric-token", perShape,
                "doc-prefix", perShape,
                "zero-padded", perShape,
                "han-punctuation", perShape))) {
            throw new IllegalArgumentException(
                    "CANDIDATE9_IDENTIFIER_SHAPE_COUNT_INVALID");
        }
        Set<String> documents = new LinkedHashSet<>();
        for (RagEvaluationDataset.CorpusSegment segment
                : dataset.corpusById().values()) {
            long segmentId = Long.parseLong(segment.segmentId());
            long documentId = Long.parseLong(segment.documentId());
            if (segmentId < segmentMin || segmentId > segmentMax
                    || documentId < documentMin || documentId > documentMax
                    || documentId != documentMin + (segmentId - segmentMin)
                    || segment.parentSegmentId() != null
                    || !documents.add(segment.documentId())
                    || !(segment.metadata().get("documentName") instanceof String name)
                    || name.isBlank()) {
                throw new IllegalArgumentException("CANDIDATE9_ID_ISOLATION_INVALID");
            }
        }
        ProjectionBudget projectionBudget = projectionBudget(dataset);
        long expectedInsertions = holdout ? 56L : 28L;
        long expectedEncodedDelta = holdout ? 52L : 26L;
        if (projectionBudget.projectionInsertions() != expectedInsertions
                || projectionBudget.encodedDocumentTokenDelta()
                != expectedEncodedDelta) {
            throw new IllegalArgumentException(
                    "CANDIDATE9_PROJECTION_BUDGET_INVALID");
        }
    }

    private static void validateGeneratedFamilyAttribution(
            RagEvaluationDataset dataset,
            PressureSpec pressure,
            List<String> targetFamilyIds,
            Map<String, String> relevantSegmentFamilies) {
        Set<String> expectedFamilies = new LinkedHashSet<>(targetFamilyIds);
        Map<String, Integer> lexicalCounts = new LinkedHashMap<>();
        Map<String, Integer> pressureCounts = new LinkedHashMap<>();
        long lexicalStart = pressure.lexicalSegmentIdStart();
        long pressureStart = pressure.pressureSegmentIdStart();
        long pressureEnd = pressureStart + pressure.pressureCount();
        for (RagEvaluationDataset.CorpusSegment segment
                : dataset.corpusById().values()) {
            long segmentId = Long.parseLong(segment.segmentId());
            boolean lexicalGenerated = segmentId >= lexicalStart
                    && segmentId < pressureStart;
            boolean pressureGenerated = segmentId >= pressureStart
                    && segmentId < pressureEnd;
            String role = Objects.toString(
                    segment.metadata().get("candidate9Role"), "");
            if (!lexicalGenerated && !pressureGenerated) {
                if ("lexical-distractor".equals(role)
                        || "pressure-distractor".equals(role)) {
                    throw new IllegalArgumentException(
                            "CANDIDATE9_GENERATED_FAMILY_INVALID");
                }
                continue;
            }
            String expectedRole = lexicalGenerated
                    ? "lexical-distractor" : "pressure-distractor";
            String familyId = Objects.toString(
                    segment.metadata().get("familyId"), "");
            if (!expectedRole.equals(role)
                    || !expectedFamilies.contains(familyId)
                    || relevantSegmentFamilies.containsKey(segment.segmentId())) {
                throw new IllegalArgumentException(
                        "CANDIDATE9_GENERATED_FAMILY_INVALID");
            }
            (lexicalGenerated ? lexicalCounts : pressureCounts)
                    .merge(familyId, 1, Integer::sum);
        }
        if (!lexicalCounts.keySet().equals(expectedFamilies)
                || !pressureCounts.keySet().equals(expectedFamilies)
                || targetFamilyIds.stream().anyMatch(familyId ->
                lexicalCounts.getOrDefault(familyId, 0) != LEXICAL_PER_TARGET
                        || pressureCounts.getOrDefault(familyId, 0)
                        != PRESSURE_PER_TARGET)) {
            throw new IllegalArgumentException(
                    "CANDIDATE9_GENERATED_FAMILY_COUNT_INVALID");
        }
    }

    private static void validateCoreRoleTable(
            RagEvaluationDataset dataset,
            Map<String, List<RagEvaluationDataset.QueryCase>> families,
            PressureSpec pressure,
            long segmentMin,
            Map<String, String> relevantSegmentFamilies) {
        Map<String, List<RagEvaluationDataset.CorpusSegment>> byFamily =
                new LinkedHashMap<>();
        long coreEnd = pressure.lexicalSegmentIdStart();
        for (RagEvaluationDataset.CorpusSegment segment
                : dataset.corpusById().values()) {
            long segmentId = Long.parseLong(segment.segmentId());
            if (segmentId < segmentMin || segmentId >= coreEnd) {
                continue;
            }
            String familyId = Objects.toString(
                    segment.metadata().get("familyId"), "");
            if (!families.containsKey(familyId)) {
                throw new IllegalArgumentException(
                        "CANDIDATE9_CORE_ROLE_TABLE_INVALID");
            }
            byFamily.computeIfAbsent(familyId, ignored -> new ArrayList<>())
                    .add(segment);
        }
        if (byFamily.values().stream().mapToInt(List::size).sum()
                != coreEnd - segmentMin
                || !byFamily.keySet().equals(families.keySet())) {
            throw new IllegalArgumentException(
                    "CANDIDATE9_CORE_ROLE_TABLE_INVALID");
        }
        for (Map.Entry<String, List<RagEvaluationDataset.QueryCase>> entry
                : families.entrySet()) {
            String familyId = entry.getKey();
            String familyCode = familyId.replaceFirst("^c9[sh]-", "")
                    .substring(0, 1);
            Map<String, Integer> expectedRoles = switch (familyCode) {
                case "t" -> Map.of("target-evidence", 3);
                case "n" -> Map.of("no-identifier-evidence", 1);
                case "x" -> Map.of("no-exact-control", 1);
                case "e" -> Map.of("irrelevant-exact-safety", 1);
                case "s" -> Map.of("semantic-near-exact-lure", 1);
                case "u" -> Map.of("existing-survivor", 1);
                case "r" -> Map.of("relevant-nonexact", 1);
                case "m" -> Map.of(
                        "multi-id-relevant", 1,
                        "multi-id-collision", 1);
                case "l" -> Map.of("long-token-boundary", 1);
                case "b" -> Map.of("boundary-negative", 4);
                default -> throw new IllegalArgumentException(
                        "CANDIDATE9_CORE_ROLE_TABLE_INVALID");
            };
            List<RagEvaluationDataset.CorpusSegment> segments =
                    byFamily.getOrDefault(familyId, List.of());
            Map<String, Long> actualRoles = segments.stream().collect(
                    Collectors.groupingBy(
                            segment -> Objects.toString(segment.metadata()
                                    .get("candidate9Role"), ""),
                            LinkedHashMap::new,
                            Collectors.counting()));
            Map<String, Long> expectedRoleCounts = new LinkedHashMap<>();
            expectedRoles.forEach((role, count) ->
                    expectedRoleCounts.put(role, count.longValue()));
            if (!actualRoles.equals(expectedRoleCounts)
                    || segments.stream().anyMatch(segment ->
                    !familyId.equals(Objects.toString(
                            segment.metadata().get("familyId"), "")))) {
                throw new IllegalArgumentException(
                        "CANDIDATE9_CORE_ROLE_TABLE_INVALID");
            }
            Set<String> relevantRoles = switch (familyCode) {
                case "t" -> Set.of("target-evidence");
                case "n" -> Set.of("no-identifier-evidence");
                case "u" -> Set.of("existing-survivor");
                case "r" -> Set.of("relevant-nonexact");
                case "m" -> Set.of("multi-id-relevant");
                case "l" -> Set.of("long-token-boundary");
                default -> Set.of();
            };
            Set<String> expectedRelevant = segments.stream()
                    .filter(segment -> relevantRoles.contains(Objects.toString(
                            segment.metadata().get("candidate9Role"), "")))
                    .map(RagEvaluationDataset.CorpusSegment::segmentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> actualRelevant = entry.getValue().stream()
                    .flatMap(query -> dataset.qrelsFor(query.id()).keySet().stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!expectedRelevant.equals(actualRelevant)
                    || expectedRelevant.stream().anyMatch(segmentId ->
                    !familyId.equals(relevantSegmentFamilies.get(segmentId)))) {
                throw new IllegalArgumentException(
                        "CANDIDATE9_CORE_ROLE_TABLE_INVALID");
            }
        }
    }

    private static ProjectionBudget projectionBudget(
            RagEvaluationDataset dataset) {
        long insertions = 0L;
        long encodedDelta = 0L;
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            List<String> visible = visibleIdentifierTerms(query.retrievalQuery());
            if (visible.isEmpty() || visible.size() > 2) {
                continue;
            }
            for (RagEvaluationDataset.CorpusSegment segment
                    : dataset.corpusById().values()) {
                String documentName = Objects.toString(
                        segment.metadata().get("documentName"), null);
                int projectedTokens = (int) visible.stream()
                        .filter(identifier -> matchesDocumentName(
                                documentName, identifier))
                        .count();
                int originalTokens = tokenCount(segment.content());
                if (projectedTokens == 0 || originalTokens < 1) {
                    continue;
                }
                insertions += projectedTokens;
                encodedDelta += Math.min(projectedTokens,
                        Math.max(MAX_TOKENS_PER_DOCUMENT - originalTokens, 0));
            }
        }
        return new ProjectionBudget(insertions, encodedDelta);
    }

    private static void validateDatasetIsolation(
            RagEvaluationDataset selection,
            RagEvaluationDataset holdout) {
        Set<String> selectionQueries = selection.queries().stream()
                .map(RagEvaluationDataset.QueryCase::id).collect(Collectors.toSet());
        Set<String> selectionFamilies = selection.queries().stream()
                .map(RagEvaluationDataset.QueryCase::familyId).collect(Collectors.toSet());
        Set<String> selectionSegments = selection.corpusById().keySet();
        Set<String> selectionDocuments = selection.corpusById().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId)
                .collect(Collectors.toSet());
        if (holdout.queries().stream().map(RagEvaluationDataset.QueryCase::id)
                .anyMatch(selectionQueries::contains)
                || holdout.queries().stream().map(RagEvaluationDataset.QueryCase::familyId)
                .anyMatch(selectionFamilies::contains)
                || holdout.corpusById().keySet().stream().anyMatch(selectionSegments::contains)
                || holdout.corpusById().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId)
                .anyMatch(selectionDocuments::contains)) {
            throw new IllegalArgumentException("CANDIDATE9_DATASET_ISOLATION_INVALID");
        }
    }

    private static Manifest buildManifest(DatasetFiles files, boolean holdout) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("familyCount", (int) files.dataset().queries().stream()
                .map(RagEvaluationDataset.QueryCase::familyId).distinct().count());
        counts.put("queryCount", files.dataset().queries().size());
        counts.put("documentCount", (int) files.dataset().corpusById().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId).distinct().count());
        counts.put("segmentCount", files.dataset().corpusById().size());
        counts.put("qrelPairCount", files.qrelCount());
        Map<String, Object> structure = structure(files, holdout);
        long seed = holdout ? HOLDOUT_SEED : SELECTION_SEED;
        String datasetHash = manifestDatasetHash(
                GENERATOR, GENERATOR_VERSION, seed,
                files.resources(), counts, structure);
        return new Manifest(
                holdout ? "candidate9-holdout" : "candidate9-selection",
                holdout ? "FROZEN_NOT_BLIND" : "FROZEN",
                GENERATOR,
                GENERATOR_VERSION,
                seed,
                files.resources(),
                Map.copyOf(counts),
                Map.copyOf(structure),
                datasetHash);
    }

    private static Map<String, Object> structure(
            DatasetFiles files, boolean holdout) {
        int targetFamilies = holdout ? HOLDOUT_TARGET_FAMILIES : SELECTION_TARGET_FAMILIES;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("targetFamilyCount", targetFamilies);
        value.put("noIdentifierAnswerableFamilyCount",
                holdout ? HOLDOUT_NO_ID_FAMILIES : SELECTION_NO_ID_FAMILIES);
        value.put("noExactMatchControlFamilyCount",
                holdout ? HOLDOUT_NO_EXACT_FAMILIES : SELECTION_NO_EXACT_FAMILIES);
        value.put("irrelevantExactSafetyFamilyCount",
                holdout ? HOLDOUT_EXACT_ONLY_FAMILIES : SELECTION_EXACT_ONLY_FAMILIES);
        value.put("semanticNearExactLureFamilyCount",
                holdout ? HOLDOUT_LURE_FAMILIES : SELECTION_LURE_FAMILIES);
        value.put("existingSurvivorFamilyCount",
                holdout ? HOLDOUT_SURVIVOR_FAMILIES : SELECTION_SURVIVOR_FAMILIES);
        value.put("multiIdentifierCollisionFamilyCount",
                holdout ? HOLDOUT_MULTI_ID_FAMILIES : SELECTION_MULTI_ID_FAMILIES);
        value.put("relevantNonexactFamilyCount", holdout
                ? HOLDOUT_RELEVANT_NONEXACT_FAMILIES
                : SELECTION_RELEVANT_NONEXACT_FAMILIES);
        value.put("longTokenBoundaryFamilyCount", holdout
                ? HOLDOUT_LONG_TOKEN_FAMILIES : SELECTION_LONG_TOKEN_FAMILIES);
        value.put("boundaryNegativeFamilyCount", holdout
                ? HOLDOUT_BOUNDARY_NEGATIVE_FAMILIES
                : SELECTION_BOUNDARY_NEGATIVE_FAMILIES);
        value.put("bilingualQueriesPerFamily", 2);
        value.put("crossFamilySharing", false);
        value.put("relevantSegmentsPerTargetFamily", 3);
        value.put("relevantSegmentsPerNoIdentifierFamily", 1);
        value.put("roleTable", roleTable(holdout));
        value.put("evidenceControlUniqueSegmentCount", holdout ? 74 : 37);
        value.put("qrelPairCount", holdout ? HOLDOUT_QREL_COUNT : SELECTION_QREL_COUNT);
        value.put("lexicalDistractorCount", targetFamilies * LEXICAL_PER_TARGET);
        value.put("pressureDistractorCount", files.pressure().pressureCount());
        value.put("lexicalDistractorsPerTargetFamily", LEXICAL_PER_TARGET);
        value.put("pressureDistractorsPerTargetFamily", PRESSURE_PER_TARGET);
        value.put("generatedFamilyAttributionPolicy",
                "sorted-target-family-8-40-v1");
        value.put("segmentDocumentIdMappingPolicy", "same-ordinal-v1");
        value.put("pressureGenerator", files.pressure().generator());
        value.put("pressureVersion", files.pressure().version());
        value.put("pressureSeed", Math.toIntExact(files.pressure().seed()));
        value.put("neutralColbertCueSha256", ShadowContractSupport.sha256(
                files.pressure().neutralColbertCue().getBytes(StandardCharsets.UTF_8)));
        value.put("identifierShapeFamilyCounts", Map.of(
                "numeric-token", targetFamilies / 4,
                "doc-prefix", targetFamilies / 4,
                "zero-padded", targetFamilies / 4,
                "han-punctuation", targetFamilies / 4));
        value.put("identifierExtractorPolicy", "keyword-identifier-terms-v1");
        value.put("identifierExtractionOutputSha256",
                identifierExtractionOutputHash(files.dataset()));
        value.put("queryVisibilityTemplate", QUERY_VISIBILITY_TEMPLATE);
        value.put("queryVisibilityPolicy", "ascii-space-visible-identifier-v1");
        value.put("queryVisibilityExamples", Map.of(
                "active", List.of("topic 7611", "topic  7611 evidence", "主题 7611 证据"),
                "inactive", List.of("topic DOC-7611", "主题-7611-证据",
                        "topic\t7611", "topic\u00a07611")));
        value.put("documentNameJavaBoundaryTemplate", JAVA_IDENTIFIER_BOUNDARY_TEMPLATE);
        value.put("boundaryPolicy", "unicode-letter-number-boundary-v1");
        value.put("documentNameBoundaryExamples", Map.of(
                "matched", List.of("7611", "DOC-7611", "主题-7611-证据"),
                "rejected", List.of(
                        "ABC7611", "17611", "76110", "主题7611证据"),
                "zeroPadding", Map.of("07621As07621", true,
                        "07621As7621", false)));
        value.put("projectionTemplate",
                "String.join(\" \", matchedIdentifiers) + \" \" + originalContent");
        value.put("identifierDedupPolicy", "extractor-order-first-occurrence-v1");
        value.put("unicodeNormalization", "NONE");
        value.put("maxTokensPerDocument", MAX_TOKENS_PER_DOCUMENT);
        value.put("maxProjectedIdentifiersPerDocument", 2);
        value.put("minOriginalContentTokens", 1);
        value.put("tokenPolicy", "prefix-head-128-v1");
        value.put("encodedDeltaFormula", "min(p,max(128-b,0))");
        value.put("retainedContentTokensFormula", "min(b,128-p)");
        value.put("projectionInsertionUpperBound", holdout ? 56 : 28);
        value.put("encodedDocumentTokenDeltaUpperBound", holdout ? 52 : 26);
        ProjectionBudget projectionBudget = projectionBudget(files.dataset());
        value.put("projectionInsertionCount",
                projectionBudget.projectionInsertions());
        value.put("encodedDocumentTokenDelta",
                projectionBudget.encodedDocumentTokenDelta());
        value.put("longContentDisplacementCaseCount", holdout ? 4 : 2);
        value.put("idMappingRule", Map.of(
                "knowledgeBaseId", Math.toIntExact(
                        holdout ? HOLDOUT_KB_ID : SELECTION_KB_ID),
                "segmentIdMin", Math.toIntExact(
                        holdout ? HOLDOUT_SEGMENT_ID_MIN : SELECTION_SEGMENT_ID_MIN),
                "segmentIdMax", Math.toIntExact(
                        holdout ? HOLDOUT_SEGMENT_ID_MAX : SELECTION_SEGMENT_ID_MAX),
                "documentIdMin", Math.toIntExact(
                        holdout ? HOLDOUT_DOCUMENT_ID_MIN : SELECTION_DOCUMENT_ID_MIN),
                "documentIdMax", Math.toIntExact(
                        holdout ? HOLDOUT_DOCUMENT_ID_MAX : SELECTION_DOCUMENT_ID_MAX),
                "lexicalSegmentIdStart", Math.toIntExact(
                        files.pressure().lexicalSegmentIdStart()),
                "pressureSegmentIdStart", Math.toIntExact(
                        files.pressure().pressureSegmentIdStart()),
                "lexicalDocumentIdStart", Math.toIntExact(
                        files.pressure().lexicalDocumentIdStart()),
                "pressureDocumentIdStart", Math.toIntExact(
                        files.pressure().pressureDocumentIdStart())));
        value.put("expandedDatasetHash", expandedDatasetHash(files.dataset()));
        value.put("policyIdentifiers", Map.of(
                "candidate9ProjectionPolicy",
                "matched-visible-identifiers-prefix-v1",
                "candidate9QueryVisibilityPolicy",
                "ascii-space-visible-identifier-v1",
                "candidate9BoundaryPolicy",
                "unicode-letter-number-boundary-v1",
                "candidate9TokenPolicy", "prefix-head-128-v1",
                "candidate9EligibilityPolicy",
                "hash-colbert-deterministic-fail-closed-v1",
                "candidate9FailurePolicy", "active-request-propagation-v1",
                "candidate9EvaluationPolicy", "qrel-after-ranking-v1"));
        return value;
    }

    private static void validateFrozenStructure(
            RagEvaluationDataset dataset,
            int qrelCount,
            PressureSpec pressure,
            Manifest manifest,
            boolean holdout) {
        DatasetFiles files = new DatasetFiles(
                dataset, qrelCount, pressure, manifest.resources());
        String actualHash = ShadowContractSupport.configHash(
                structure(files, holdout));
        String expectedHash = ShadowContractSupport.configHash(
                manifest.structure());
        if (!actualHash.equals(expectedHash)) {
            throw new FrozenInputChangedException(
                    "CANDIDATE9_FROZEN_STRUCTURE_MISMATCH");
        }
    }

    private static String identifierExtractionOutputHash(
            RagEvaluationDataset dataset) {
        Map<String, Object> evidence = new TreeMap<>();
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            evidence.put(query.id(), Map.of(
                    "original", identifierTerms(query.query()),
                    "retrieval", identifierTerms(query.retrievalQuery())));
        }
        return ShadowContractSupport.configHash(evidence);
    }

    private static Map<String, Object> roleTable(boolean holdout) {
        int scale = holdout ? 2 : 1;
        Map<String, Object> roles = new LinkedHashMap<>();
        roles.put("target", roleContract(8 * scale, 3, 0, 24 * scale, 48 * scale));
        roles.put("noIdentifierAnswerable",
                roleContract(1 * scale, 1, 0, scale, 2 * scale));
        roles.put("noExactMatch", roleContract(1 * scale, 0, 1, scale, 0));
        roles.put("irrelevantExactSafety", roleContract(1 * scale, 0, 1, scale, 0));
        roles.put("semanticNearExactLure",
                roleContract(1 * scale, 0, 1, scale, 0));
        roles.put("existingSurvivor", roleContract(1 * scale, 1, 0, scale, 2 * scale));
        roles.put("relevantNonexact", roleContract(1 * scale, 1, 0, scale, 2 * scale));
        roles.put("multiIdentifierCollision",
                roleContract(1 * scale, 1, 1, 2 * scale, 2 * scale));
        roles.put("longTokenBoundary", roleContract(1 * scale, 1, 0, scale, 2 * scale));
        roles.put("boundaryNegative", roleContract(1 * scale, 0, 4, 4 * scale, 0));
        return Map.copyOf(roles);
    }

    private static Map<String, Object> roleContract(
            int familyCount,
            int sharedRelevantSegments,
            int irrelevantControlSegments,
            int uniqueSegmentCount,
            int qrelPairCount) {
        return Map.of(
                "familyCount", familyCount,
                "queriesPerFamily", 2,
                "sharedRelevantSegmentsPerBilingualFamily", sharedRelevantSegments,
                "irrelevantControlSegmentsPerBilingualFamily", irrelevantControlSegments,
                "crossFamilySharing", false,
                "uniqueSegmentCount", uniqueSegmentCount,
                "qrelPairCount", qrelPairCount);
    }

    private static String expandedDatasetHash(RagEvaluationDataset dataset) {
        List<Map<String, Object>> corpus = dataset.corpusById().values().stream()
                .sorted(Comparator.comparingLong(segment ->
                        Long.parseLong(segment.segmentId())))
                .map(segment -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("segmentId", segment.segmentId());
                    item.put("documentId", segment.documentId());
                    item.put("contentSha256", ShadowContractSupport.sha256(
                            segment.content().getBytes(StandardCharsets.UTF_8)));
                    item.put("documentNameSha256", ShadowContractSupport.sha256(
                            Objects.toString(segment.metadata().get("documentName"), "")
                                    .getBytes(StandardCharsets.UTF_8)));
                    item.put("metadata", new TreeMap<>(segment.metadata()));
                    return item;
                }).toList();
        List<Map<String, Object>> queries = dataset.queries().stream()
                .sorted(Comparator.comparing(RagEvaluationDataset.QueryCase::id))
                .map(query -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", query.id());
            item.put("familyId", query.familyId());
            item.put("querySha256", ShadowContractSupport.sha256(
                    query.query().getBytes(StandardCharsets.UTF_8)));
            item.put("retrievalQuerySha256", ShadowContractSupport.sha256(
                    query.retrievalQuery().getBytes(StandardCharsets.UTF_8)));
            item.put("language", query.language());
            item.put("strata", query.strata().stream().sorted().toList());
            item.put("split", query.split());
            item.put("answerable", query.answerable());
            return item;
        }).toList();
        Map<String, Object> qrels = new TreeMap<>();
        dataset.qrels().forEach((queryId, grades) ->
                qrels.put(queryId, new TreeMap<>(grades)));
        return ShadowContractSupport.configHash(Map.of(
                "corpus", corpus, "queries", queries, "qrels", qrels));
    }

    private static Map<String, Object> manifestMap(Manifest manifest) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("manifestVersion", 1);
        value.put("dataset", manifest.dataset());
        value.put("freezeStatus", manifest.freezeStatus());
        value.put("generator", manifest.generator());
        value.put("version", manifest.version());
        value.put("seed", manifest.seed());
        Map<String, Object> resources = new TreeMap<>();
        manifest.resources().forEach((name, resource) -> resources.put(name, Map.of(
                "file", resource.file(), "sha256", resource.sha256())));
        value.put("resources", resources);
        value.put("counts", manifest.counts());
        value.put("structure", manifest.structure());
        value.put("datasetHash", manifest.datasetHash());
        return value;
    }

    private static ManifestSnapshot readManifest(
            Path path,
            String expectedDataset,
            String expectedFreezeStatus,
            long expectedSeed,
            int expectedQueries,
            int expectedSegments,
            int expectedQrels,
            AccessCounter accessCounter) {
        if (accessCounter != null) {
            accessCounter.openManifest();
        }
        byte[] manifestBytes;
        JSONObject json;
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE9_MANIFEST_INVALID");
        }
        try {
            manifestBytes = Files.readAllBytes(path);
            json = JSON.parseObject(new String(manifestBytes, StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException("CANDIDATE9_MANIFEST_INVALID", failure);
        }
        if (!json.keySet().equals(Set.of(
                "manifestVersion", "dataset", "freezeStatus", "generator",
                "version", "seed", "resources", "counts", "structure",
                "datasetHash"))
                || !expectedDataset.equals(json.getString("dataset"))
                || !expectedFreezeStatus.equals(json.getString("freezeStatus"))
                || !GENERATOR.equals(json.getString("generator"))
                || GENERATOR_VERSION != json.getIntValue("version")
                || expectedSeed != json.getLongValue("seed")
                || json.getIntValue("manifestVersion") != 1) {
            throw new IllegalStateException("CANDIDATE9_MANIFEST_INVALID");
        }
        JSONObject resourceJson = json.getJSONObject("resources");
        JSONObject countsJson = json.getJSONObject("counts");
        JSONObject structureJson = json.getJSONObject("structure");
        if (resourceJson == null || countsJson == null || structureJson == null
                || countsJson.getIntValue("queryCount") != expectedQueries
                || countsJson.getIntValue("segmentCount") != expectedSegments
                || countsJson.getIntValue("documentCount") != expectedSegments
                || !resourceJson.keySet().equals(RESOURCE_FILES.keySet())
                || !countsJson.keySet().equals(Set.of(
                "familyCount", "queryCount", "documentCount",
                "segmentCount", "qrelPairCount"))
                || countsJson.getIntValue("qrelPairCount") != expectedQrels) {
            throw new IllegalStateException("CANDIDATE9_MANIFEST_INVALID");
        }
        Map<String, ResourceHash> resources = new LinkedHashMap<>();
        for (String name : List.of("corpus", "queries", "qrels", "pressure")) {
            JSONObject resource = resourceJson.getJSONObject(name);
            if (resource == null
                    || !resource.keySet().equals(Set.of("file", "sha256"))
                    || !RESOURCE_FILES.get(name).equals(resource.getString("file"))
                    || !validSha(resource.getString("sha256"))) {
                throw new IllegalStateException("CANDIDATE9_MANIFEST_INVALID");
            }
            resources.put(name, new ResourceHash(
                    resource.getString("file"), resource.getString("sha256")));
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : List.of(
                "familyCount", "queryCount", "documentCount",
                "segmentCount", "qrelPairCount")) {
            counts.put(name, countsJson.getIntValue(name));
        }
        Map<String, Object> structure = new LinkedHashMap<>(structureJson);
        String datasetHash = json.getString("datasetHash");
        String recomputed = manifestDatasetHash(
                json.getString("generator"),
                json.getIntValue("version"),
                json.getLongValue("seed"),
                resources, counts, structure);
        if (!validSha(datasetHash) || !datasetHash.equals(recomputed)) {
            throw new IllegalStateException("CANDIDATE9_MANIFEST_INVALID");
        }
        Manifest manifest = new Manifest(
                expectedDataset, expectedFreezeStatus, GENERATOR,
                GENERATOR_VERSION, expectedSeed, Map.copyOf(resources),
                Map.copyOf(counts), Map.copyOf(structure), datasetHash);
        return new ManifestSnapshot(
                manifest, ShadowContractSupport.sha256(manifestBytes));
    }

    private static String manifestDatasetHash(
            String generator,
            int version,
            long seed,
            Map<String, ResourceHash> resources,
            Map<String, Integer> counts,
            Map<String, ?> structure) {
        Map<String, Object> resourceEvidence = new TreeMap<>();
        resources.forEach((name, resource) -> resourceEvidence.put(name, Map.of(
                "file", resource.file(), "sha256", resource.sha256())));
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("generator", generator);
        evidence.put("version", version);
        evidence.put("seed", seed);
        evidence.put("resources", resourceEvidence);
        evidence.put("counts", counts);
        evidence.put("structure", structure);
        return ShadowContractSupport.configHash(evidence);
    }

    private static Path testsDirectory() {
        Path current = Path.of(System.getProperty("user.dir", "."))
                .toAbsolutePath().normalize();
        if ("tests".equals(Objects.toString(current.getFileName(), ""))) {
            return current;
        }
        Path nested = current.resolve("tests");
        if (Files.isDirectory(nested)) {
            return nested;
        }
        Path backendTests = current.resolve("backend/tests");
        if (Files.isDirectory(backendTests)) {
            return backendTests;
        }
        throw new IllegalStateException("CANDIDATE9_TESTS_DIRECTORY_NOT_FOUND");
    }

    private static void requireFixedRuntimePaths(RuntimePaths paths) {
        RuntimePaths expected = paths(paths.freezeDirectory().getParent());
        if (!expected.equals(paths)) {
            throw new IllegalArgumentException("CANDIDATE9_RUNTIME_PATH_INVALID");
        }
        Path tests = testsDirectory().toAbsolutePath().normalize();
        Path runtime = paths.freezeDirectory().getParent();
        Path base = paths.freezeDirectory().startsWith(tests)
                ? tests : runtime.getParent();
        requireNoSymbolicLinksBelow(
                base, paths.freezeDirectory(), "CANDIDATE9_RUNTIME_PATH_INVALID");
        requireNoSymbolicLinksBelow(
                base, paths.diagnostic(), "CANDIDATE9_RUNTIME_PATH_INVALID");
    }

    private static Path requireDirectory(Path path, String errorCode) {
        if (containsParentTraversal(path)) {
            throw new IllegalArgumentException(errorCode);
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }

    private static Path requireRegular(Path path, String errorCode) {
        if (containsParentTraversal(path)) {
            throw new IllegalArgumentException(errorCode);
        }
        Path parent = requireDirectory(path.getParent(), errorCode);
        Path normalized = path.toAbsolutePath().normalize();
        if (!parent.equals(normalized.getParent())
                || Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }

    private static boolean containsParentTraversal(Path path) {
        for (Path component : path) {
            if ("..".equals(component.toString())) {
                return true;
            }
        }
        return false;
    }

    private static void requireNoSymbolicLinksBelow(
            Path base, Path target, String errorCode) {
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedBase)
                || Files.isSymbolicLink(normalizedBase)
                || !Files.isDirectory(
                normalizedBase, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(errorCode);
        }
        Path current = normalizedBase;
        boolean missingTail = false;
        for (Path component : normalizedBase.relativize(normalizedTarget)) {
            current = current.resolve(component);
            boolean exists = Files.exists(current, LinkOption.NOFOLLOW_LINKS);
            if (exists && (missingTail || Files.isSymbolicLink(current)
                    || !current.equals(normalizedTarget)
                    && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))) {
                throw new IllegalArgumentException(errorCode);
            }
            missingTail |= !exists;
        }
    }

    private static void requireAtomicTargetPath(Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        Path tests = testsDirectory().toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        Path runtime = parent.getFileName() != null
                && "candidate9-freeze".equals(parent.getFileName().toString())
                ? parent.getParent() : parent;
        Path base = normalized.startsWith(tests) ? tests : runtime.getParent();
        requireNoSymbolicLinksBelow(
                base, normalized, "CANDIDATE9_ATOMIC_PARENT_INVALID");
    }

    private static void requireBytes(Path path, byte[] expected, String errorCode) {
        try {
            if (Files.isSymbolicLink(path)
                    || !java.util.Arrays.equals(Files.readAllBytes(path), expected)) {
                throw new IllegalStateException(errorCode);
            }
        } catch (IOException failure) {
            throw new IllegalStateException(errorCode, failure);
        }
    }

    private static String safeFileHash(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path)
                ? ShadowContractSupport.sha256(path)
                : null;
    }

    private static boolean hasCandidate9TemporaryArtifacts(RuntimePaths paths) {
        return hasTemporaryArtifact(paths.freezeDirectory(), List.of(
                paths.selectionManifest().getFileName().toString(),
                paths.holdoutManifest().getFileName().toString(),
                paths.ledger().getFileName().toString(),
                paths.reviewLedger().getFileName().toString()))
                || hasTemporaryArtifact(paths.diagnostic().getParent(), List.of(
                paths.diagnostic().getFileName().toString()));
    }

    private static boolean hasTemporaryArtifact(
            Path directory, List<String> targetNames) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) {
            return false;
        }
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).anyMatch(name ->
                    name.endsWith(".tmp")
                            && targetNames.stream().anyMatch(name::startsWith));
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "CANDIDATE9_TEMPORARY_ARTIFACT_AUDIT_FAILED", failure);
        }
    }

    private static void atomicCreate(Path target, byte[] bytes) {
        Path normalized = target.toAbsolutePath().normalize();
        try {
            requireAtomicTargetPath(normalized);
            Files.createDirectories(normalized.getParent());
            if (Files.isSymbolicLink(normalized.getParent())) {
                throw new IllegalStateException("CANDIDATE9_ATOMIC_PARENT_INVALID");
            }
            Path temp = Files.createTempFile(
                    normalized.getParent(), normalized.getFileName().toString(), ".tmp");
            Files.write(temp, bytes);
            Files.createFile(normalized);
            Files.move(temp, normalized,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException failure) {
            throw new IllegalStateException("CANDIDATE9_ATOMIC_MOVE_REQUIRED", failure);
        } catch (FileAlreadyExistsException failure) {
            throw new IllegalStateException("CANDIDATE9_ARTIFACT_ALREADY_EXISTS", failure);
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE9_ATOMIC_WRITE_FAILED", failure);
        }
    }

    private static void atomicReplace(Path target, byte[] bytes) {
        Path normalized = target.toAbsolutePath().normalize();
        try {
            requireAtomicTargetPath(normalized);
            if (Files.isSymbolicLink(normalized.getParent())
                    || Files.isSymbolicLink(normalized)
                    || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("CANDIDATE9_ATOMIC_PARENT_INVALID");
            }
            Path temp = Files.createTempFile(
                    normalized.getParent(), normalized.getFileName().toString(), ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, normalized,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException failure) {
            throw new IllegalStateException("CANDIDATE9_ATOMIC_MOVE_REQUIRED", failure);
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE9_ATOMIC_WRITE_FAILED", failure);
        }
    }

    private static JSONObject readJson(Path path, String errorCode) {
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(errorCode);
        }
        try {
            return JSON.parseObject(Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException(errorCode, failure);
        }
    }

    private static byte[] canonicalJsonBytes(Object value) {
        return JSON.toJSONString(
                        value,
                        JSONWriter.Feature.WriteNulls,
                        JSONWriter.Feature.MapSortField)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> dataLines(String value) {
        return value.lines().map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    private static String required(JSONObject json, String field) {
        String value = json.getString(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CANDIDATE9_REQUIRED_FIELD_MISSING");
        }
        return value;
    }

    private static List<String> strings(JSONArray values) {
        return values == null ? List.of() : values.toJavaList(String.class);
    }

    private static boolean validSha(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    private static String requireErrorCode(String value) {
        if (value == null || !ERROR_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("CANDIDATE9_ERROR_CODE_INVALID");
        }
        return value;
    }

    record RuntimePaths(
            Path freezeDirectory,
            Path selectionManifest,
            Path holdoutManifest,
            Path ledger,
            Path reviewLedger,
            Path diagnostic) {
    }

    record ResourceHash(String file, String sha256) {
    }

    record Manifest(
            String dataset,
            String freezeStatus,
            String generator,
            int version,
            long seed,
            Map<String, ResourceHash> resources,
            Map<String, Integer> counts,
            Map<String, Object> structure,
            String datasetHash) {
    }

    record FrozenManifests(
            Manifest selection,
            String selectionSha256,
            Manifest holdout,
            String holdoutSha256) {
    }

    record FrozenDataset(
            RagEvaluationDataset dataset,
            FrozenManifests manifests,
            String datasetHash,
            Path selectionDirectory,
            AccessCounter accessCounter) {

        AccessAudit auditedReads() {
            return accessCounter.snapshot();
        }

        RagEvaluationDataset evaluationDataset(
                String rankingPhaseSha256, String rankingCasesSha256) {
            return accessCounter.evaluationDataset(
                    rankingPhaseSha256, rankingCasesSha256);
        }
    }

    record AccessAudit(
            int selectionResourceAccessCount,
            int manifestAccessCount,
            int holdoutResourceAccessCount,
            int qrelResourceAccessBeforeRanking,
            int qrelResourceAccessCount,
            String diagnosticPhase) {
    }

    private enum DiagnosticPhase {
        MANIFEST_VERIFIED,
        RUNNING,
        RANKING_FROZEN,
        QRELS_LOADED
    }

    private static final class AccessCounter {
        private int selectionResourceAccessCount;
        private int manifestAccessCount;
        private int holdoutResourceAccessCount;
        private int qrelResourceAccessBeforeRanking;
        private int qrelResourceAccessCount;
        private PressureSpec pressure;
        private RagEvaluationDataset evaluationDataset;
        private String rankingPhaseSha256;
        private String rankingCasesSha256;
        private DiagnosticPhase phase = DiagnosticPhase.MANIFEST_VERIFIED;

        private void openDatasetResource(boolean holdout, boolean qrelResource) {
            if (holdout) {
                holdoutResourceAccessCount++;
            } else {
                selectionResourceAccessCount++;
            }
            if (qrelResource) {
                qrelResourceAccessCount++;
            }
        }

        private void openManifest() {
            manifestAccessCount++;
        }

        private AccessAudit snapshot() {
            return new AccessAudit(
                    selectionResourceAccessCount,
                    manifestAccessCount,
                    holdoutResourceAccessCount,
                    qrelResourceAccessBeforeRanking,
                    qrelResourceAccessCount,
                    phase.name());
        }

        private synchronized RagEvaluationDataset evaluationDataset(
                String expectedRankingSha256,
                String expectedRankingCasesSha256) {
            if (phase != DiagnosticPhase.QRELS_LOADED
                    || evaluationDataset == null
                    || !Objects.equals(rankingPhaseSha256,
                    expectedRankingSha256)
                    || !Objects.equals(rankingCasesSha256,
                    expectedRankingCasesSha256)) {
                throw new IllegalStateException(
                        "CANDIDATE9_QRELS_NOT_LOADED_AFTER_RANKING");
            }
            return evaluationDataset;
        }
    }

    private interface ArtifactBinding {
        String datasetHash();

        String selectionManifestSha256();

        String holdoutManifestSha256();

        RagEvaluationDataset evaluationDataset(
                String rankingPhaseSha256, String rankingCasesSha256);
    }

    record RunHandle(
            String datasetHash,
            String selectionManifestSha256,
            String holdoutManifestSha256,
            String runningLedgerSha256,
            RagEvaluationDataset dataset,
            AccessCounter accessCounter) implements ArtifactBinding {

        @Override
        public RagEvaluationDataset evaluationDataset(
                String rankingPhaseSha256, String rankingCasesSha256) {
            return accessCounter.evaluationDataset(
                    rankingPhaseSha256, rankingCasesSha256);
        }
    }

    record RecoveryBinding(
            RuntimePaths frozenPaths,
            RagCandidate91RecoverySupport.RecoveryAuthorization authorization,
            String datasetHash,
            String selectionManifestSha256,
            String holdoutManifestSha256,
            String recoveryLedgerSha256,
            RagEvaluationDataset dataset,
            AccessCounter accessCounter) implements ArtifactBinding {

        @Override
        public RagEvaluationDataset evaluationDataset(
                String rankingPhaseSha256, String rankingCasesSha256) {
            return accessCounter.evaluationDataset(
                    rankingPhaseSha256, rankingCasesSha256);
        }
    }

    record EvaluationView(
            RagEvaluationDataset dataset,
            String rankingPhaseSha256,
            AccessAudit auditedReads) {
    }

    static final class FrozenInputChangedException extends IllegalStateException {
        private final String errorCode;

        FrozenInputChangedException(String errorCode) {
            super(errorCode);
            this.errorCode = errorCode;
        }

        FrozenInputChangedException(String errorCode, Throwable cause) {
            super(errorCode, cause);
            this.errorCode = errorCode;
        }

        String errorCode() {
            return errorCode;
        }
    }

    record Projection(
            String text,
            int matchedIdentifierCount,
            int originalTokenCount,
            int projectedTokenCount,
            int retainedContentTokens,
            boolean applied) {
    }

    record RetrievalSnapshot(
            int ordinal,
            Long segmentId,
            Long documentId,
            String qmSegmentId,
            String parentSegmentId,
            String documentName,
            String content,
            String answer,
            double score,
            String source,
            double colbertScore,
            boolean exactMatch) {

        RetrievalResult toRetrievalResult() {
            return RetrievalResult.builder()
                    .segmentId(segmentId)
                    .documentId(documentId)
                    .qmSegmentId(qmSegmentId)
                    .parentSegmentId(parentSegmentId)
                    .documentName(documentName)
                    .content(content)
                    .answer(answer)
                    .score(score)
                    .source(source)
                    .metadata(Double.isFinite(colbertScore)
                            ? Map.of("colbert_score", colbertScore) : Map.of())
                    .build();
        }

    }

    record CaseEvidence(
            String queryId,
            String familyId,
            String language,
            String role,
            String identifierShape,
            boolean target,
            boolean qualifying,
            boolean mechanismValid,
            double baselineAp,
            double baselineNdcg,
            double projectionAp,
            double projectionNdcg,
            boolean unchanged,
            boolean safetyValid) {

        static CaseEvidence target(
                String queryId, String familyId, String language,
                String identifierShape, boolean qualifying,
                boolean mechanismValid,
                double baselineAp, double baselineNdcg,
                double projectionAp, double projectionNdcg,
                boolean unchanged) {
            return new CaseEvidence(
                    queryId, familyId, language, "target", identifierShape,
                    true, qualifying, mechanismValid,
                    baselineAp, baselineNdcg, projectionAp, projectionNdcg,
                    unchanged, true);
        }

        static CaseEvidence control(
                String queryId, String familyId, String language,
                String role, boolean unchanged) {
            return new CaseEvidence(
                    queryId, familyId, language, role, "none", false,
                    false, true,
                    0.0D, 0.0D, 0.0D, 0.0D,
                    unchanged, true);
        }
    }

    private record PressureSpec(
            String generator,
            int version,
            long seed,
            int lexicalPerTargetFamily,
            int pressureCount,
            String neutralColbertCue,
            long lexicalSegmentIdStart,
            long pressureSegmentIdStart,
            long lexicalDocumentIdStart,
            long pressureDocumentIdStart) {
    }

    private record DatasetFiles(
            RagEvaluationDataset dataset,
            int qrelCount,
            PressureSpec pressure,
            Map<String, ResourceHash> resources) {
    }

    private record RankingDatasetFiles(
            RagEvaluationDataset dataset,
            PressureSpec pressure,
            Map<String, ResourceHash> resources,
            Path directory) {
    }

    private record FinalizedCases(
            List<Map<String, Object>> cases,
            List<CaseEvidence> evidence) {
    }

    private record ProjectionBudget(
            long projectionInsertions,
            long encodedDocumentTokenDelta) {
    }

    private record ParsedQrels(
            Map<String, Map<String, Integer>> qrels,
            int count) {
    }

    private record ResourceBytes(String text, ResourceHash resourceHash) {
    }

    private record ManifestSnapshot(Manifest manifest, String sha256) {
    }
}
