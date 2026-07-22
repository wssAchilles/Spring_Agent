package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.JSONObject;
import tech.qiantong.qknow.module.kmc.service.rag.KeywordRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.io.IOException;
import java.lang.reflect.Field;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class RagCandidate8DiagnosticSupport {

    static final String FREEZE_PROPERTY = "rag.eval.candidate8.freeze";
    static final String DIAGNOSTIC_PROPERTY = "rag.eval.candidate8.diagnostic";
    static final String HOLDOUT_DIRECTORY_PROPERTY = "rag.eval.candidate8.holdout-dir";

    static final long SELECTION_KB_ID = 10_120_000L;
    static final long SELECTION_SEGMENT_ID_MIN = 10_120_001L;
    static final long SELECTION_SEGMENT_ID_MAX = 10_124_999L;
    static final long SELECTION_DOCUMENT_ID_MIN = 10_125_000L;
    static final long SELECTION_DOCUMENT_ID_MAX = 10_129_999L;

    static final long HOLDOUT_KB_ID = 10_130_000L;
    static final long HOLDOUT_SEGMENT_ID_MIN = 10_130_001L;
    static final long HOLDOUT_SEGMENT_ID_MAX = 10_134_999L;
    static final long HOLDOUT_DOCUMENT_ID_MIN = 10_135_000L;
    static final long HOLDOUT_DOCUMENT_ID_MAX = 10_139_999L;

    static final int BUSINESS_SQL_LIMIT = 500;
    static final int BUSINESS_JAVA_LIMIT = 50;
    static final String PROCEED_DECISION =
            "PROCEED_TO_CORROBORATED_IDENTIFIER_TIE_RED";
    static final String STOP_DECISION =
            "STOP_CORROBORATED_IDENTIFIER_TIE_UNSUPPORTED";

    private static final String GENERATOR = "candidate8-static-fixture-v1";
    private static final int GENERATOR_VERSION = 1;
    private static final long SELECTION_SEED = 20260721L;
    private static final long HOLDOUT_SEED = 20260722L;
    private static final int SELECTION_QUERY_COUNT = 32;
    private static final int HOLDOUT_QUERY_COUNT = 64;
    private static final int SELECTION_SEGMENT_COUNT = 607;
    private static final int HOLDOUT_SEGMENT_COUNT = 958;
    private static final int SELECTION_QREL_COUNT = 56;
    private static final int HOLDOUT_QREL_COUNT = 112;
    private static final int SELECTION_TARGET_FAMILIES = 8;
    private static final int HOLDOUT_TARGET_FAMILIES = 16;
    private static final int SELECTION_NO_ID_FAMILIES = 2;
    private static final int HOLDOUT_NO_ID_FAMILIES = 4;
    private static final int SELECTION_NO_EXACT_FAMILIES = 2;
    private static final int HOLDOUT_NO_EXACT_FAMILIES = 4;
    private static final int SELECTION_EXACT_ONLY_FAMILIES = 1;
    private static final int HOLDOUT_EXACT_ONLY_FAMILIES = 2;
    private static final int SELECTION_LURE_FAMILIES = 1;
    private static final int HOLDOUT_LURE_FAMILIES = 2;
    private static final int SELECTION_SURVIVOR_FAMILIES = 1;
    private static final int HOLDOUT_SURVIVOR_FAMILIES = 2;
    private static final int SELECTION_MULTI_ID_FAMILIES = 1;
    private static final int HOLDOUT_MULTI_ID_FAMILIES = 2;
    private static final int SELECTION_PRESSURE_COUNT = 512;
    private static final int HOLDOUT_PRESSURE_COUNT = 768;
    private static final int LEXICAL_PER_TARGET = 8;
    private static final Map<String, String> RESOURCE_FILES = Map.of(
            "corpus", "corpus.jsonl",
            "queries", "queries.jsonl",
            "qrels", "qrels.tsv",
            "pressure", "pressure.json");
    private static final Set<String> IDENTIFIER_SHAPES = Set.of(
            "numeric-token", "doc-prefix", "zero-padded", "han-adjacent");
    private static final Set<String> FORBIDDEN_ARTIFACT_FIELDS = Set.of(
            "query", "retrievalquery", "identifier", "identifiers",
            "term", "terms", "contentterm", "contentterms",
            "documentname", "content", "answer",
            "reference", "referenceanswer", "referenceclaims",
            "qrel", "qrels", "qrelgrade", "grade",
            "sqlparameter", "sqlparameters", "sqlparam", "sqlparams",
            "sqlexpressionvalue", "sqlexpressionvalues",
            "exceptionmessage", "errormessage");
    private static final String OUTER_ORDER =
            "ORDER BY trgm_score DESC, document_id ASC, position ASC NULLS LAST";
    private static final String IDENTIFIER_PREDICATE = "s.document_name ~ ("
            + "'(^|[[:space:][:punct:]])' || ? || "
            + "'([[:space:][:punct:]]|$)')";
    private static final String CONTENT_PREDICATE =
            "strpos(lower(coalesce(s.content, '')), ?) > 0";
    private static final String JAVA_IDENTIFIER_BOUNDARY_TEMPLATE =
            "(^|[\\x09-\\x0D\\x20\\p{Punct}])<quoted-identifier>"
                    + "([\\x09-\\x0D\\x20\\p{Punct}]|$)";
    private static final String JAVA_CONTENT_TEMPLATE =
            "content.toLowerCase(Locale.ROOT).contains(term)";
    private static final Pattern JAVA_IDENTIFIER_BOUNDARY_PREFIX = Pattern.compile(
            "[\\x09-\\x0D\\x20\\p{Punct}]");
    private static final Pattern QUERY_TOKEN_PATTERN = Pattern.compile(
            "[A-Za-z]{2,}|\\p{IsHan}+");
    private static final Set<String> ENGLISH_STOP_WORDS = Set.of(
            "an", "the", "and", "or", "of", "to", "for", "in", "on", "at",
            "by", "with", "from", "about", "what", "which", "who", "whom",
            "whose", "where", "when", "why", "how", "is", "are", "was", "were",
            "be", "been", "being", "do", "does", "did", "can", "could", "may",
            "might", "must", "shall", "should", "will", "would", "this", "that",
            "these", "those", "me", "please", "tell", "show", "find", "give",
            "provide", "explain", "information", "info", "detail", "details",
            "answer", "content", "evidence");
    private static final Set<String> IDENTIFIER_CUES = Set.of(
            "topic", "id", "document", "doc", "segment", "policy",
            "主题", "编号", "文档", "段落");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ASCII_ALPHANUMERIC = Pattern.compile("[A-Za-z0-9]");
    private static final String RERANKER_PACKAGE =
            "tech.qiantong.qknow.module.kmc.service.rag.rerank.";
    private static final List<String> ELIGIBLE_PROVIDER_CLASSES = List.of(
            RERANKER_PACKAGE + "DashScopeRerankerProvider",
            RERANKER_PACKAGE + "DeterministicRerankerProvider",
            RERANKER_PACKAGE + "LocalBgeRerankerProvider",
            RERANKER_PACKAGE + "LocalRerankerProvider",
            RERANKER_PACKAGE + "OnnxRerankerProvider");
    private static final Object LEDGER_LOCK = new Object();

    private RagCandidate8DiagnosticSupport() {
    }

    static RuntimePaths paths(Path ragEvalRuntime) {
        Path runtime = Objects.requireNonNull(ragEvalRuntime, "ragEvalRuntime")
                .toAbsolutePath().normalize();
        Path freeze = runtime.resolve("candidate8-freeze");
        return new RuntimePaths(
                freeze,
                freeze.resolve("selection-manifest.json"),
                freeze.resolve("holdout-manifest.json"),
                freeze.resolve("selection-ledger.json"),
                freeze.resolve("review-ledger.json"),
                runtime.resolve("candidate8-calibration-diagnostic.json"));
    }

    static int enabledDiagnosticCount(
            boolean identifierDiagnostic,
            boolean candidate2Diagnostic,
            boolean candidate3Diagnostic,
            boolean candidate4Diagnostic,
            boolean candidate5Diagnostic,
            boolean candidate6Diagnostic,
            boolean candidate8Diagnostic) {
        return (identifierDiagnostic ? 1 : 0)
                + (candidate2Diagnostic ? 1 : 0)
                + (candidate3Diagnostic ? 1 : 0)
                + (candidate4Diagnostic ? 1 : 0)
                + (candidate5Diagnostic ? 1 : 0)
                + (candidate6Diagnostic ? 1 : 0)
                + (candidate8Diagnostic ? 1 : 0);
    }

    static void requireSelectionJobProperties() {
        if (System.getProperty(HOLDOUT_DIRECTORY_PROPERTY) != null) {
            throw new IllegalStateException("CANDIDATE8_HOLDOUT_ACCESS_FORBIDDEN");
        }
    }

    static void requireSelectionRunAvailable(RuntimePaths paths) {
        requireFixedRuntimePaths(paths);
        if (hasCandidate8TemporaryArtifacts(paths)) {
            throw new IllegalStateException("INVALID_INCOMPLETE_PRIOR_RUN");
        }
        if (Files.exists(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE8_DIAGNOSTIC_ALREADY_EXISTS");
        }
        if (!Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        JSONObject ledger;
        try {
            ledger = readJson(paths.ledger(), "CANDIDATE8_LEDGER_INVALID");
        } catch (IllegalStateException failure) {
            throw new IllegalStateException("INVALID_INCOMPLETE_PRIOR_RUN", failure);
        }
        if ("RUNNING".equals(ledger.getString("status"))) {
            throw new IllegalStateException("INVALID_INCOMPLETE_PRIOR_RUN");
        }
        if ("COMPLETED".equals(ledger.getString("status"))) {
            throw new IllegalStateException("CANDIDATE8_SELECTION_ALREADY_COMPLETED");
        }
        throw new IllegalStateException("CANDIDATE8_LEDGER_INVALID");
    }

    static FrozenManifests freezeDatasets(
            Path runtime,
            Path selectionDirectory,
            Path holdoutDirectory) {
        RuntimePaths paths = paths(runtime);
        if (hasCandidate8TemporaryArtifacts(paths)
                || Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE8_SELECTION_ALREADY_STARTED");
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
                throw new IllegalStateException("CANDIDATE8_FREEZE_INCOMPLETE");
            }
            requireBytes(paths.selectionManifest(), selectionBytes,
                    "CANDIDATE8_FREEZE_ALREADY_FROZEN");
            requireBytes(paths.holdoutManifest(), holdoutBytes,
                    "CANDIDATE8_FREEZE_ALREADY_FROZEN");
            return loadFrozenManifests(paths);
        }
        atomicCreate(paths.selectionManifest(), selectionBytes);
        atomicCreate(paths.holdoutManifest(), holdoutBytes);
        return loadFrozenManifests(paths);
    }

    static void freezeFormalDatasets(Path runtime, Path holdoutDirectory) {
        Path tests = testsDirectory();
        Path selection = tests.resolve(
                "src/test/resources/rag-eval/candidate8-selection")
                .toAbsolutePath().normalize();
        Path expectedHoldout = tests.resolve("candidate8-holdout")
                .toAbsolutePath().normalize();
        Path actualHoldout = holdoutDirectory.toAbsolutePath().normalize();
        if (!expectedHoldout.equals(actualHoldout)) {
            throw new IllegalStateException("CANDIDATE8_HOLDOUT_DIRECTORY_MISMATCH");
        }
        requireNoSymbolicLinksBelow(tests, selection,
                "CANDIDATE8_DATASET_DIRECTORY_INVALID");
        requireNoSymbolicLinksBelow(tests, actualHoldout,
                "CANDIDATE8_DATASET_DIRECTORY_INVALID");
        freezeDatasets(
                runtime,
                selection,
                actualHoldout);
    }

    static FrozenManifests loadFrozenManifests(RuntimePaths paths) {
        return loadFrozenManifests(paths, null);
    }

    private static FrozenManifests loadFrozenManifests(
            RuntimePaths paths, AccessCounter accessCounter) {
        requireFixedRuntimePaths(paths);
        if (Files.isSymbolicLink(paths.freezeDirectory())
                || !Files.isDirectory(paths.freezeDirectory(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE8_FREEZE_DIRECTORY_INVALID");
        }
        ManifestSnapshot selection = readManifest(
                paths.selectionManifest(), "candidate8-selection", "FROZEN",
                SELECTION_SEED, SELECTION_QUERY_COUNT, SELECTION_SEGMENT_COUNT,
                SELECTION_QREL_COUNT, accessCounter);
        ManifestSnapshot holdout = readManifest(
                paths.holdoutManifest(), "candidate8-holdout", "FROZEN_NOT_BLIND",
                HOLDOUT_SEED, HOLDOUT_QUERY_COUNT, HOLDOUT_SEGMENT_COUNT,
                HOLDOUT_QREL_COUNT, accessCounter);
        return new FrozenManifests(
                selection.manifest(), selection.sha256(),
                holdout.manifest(), holdout.sha256());
    }

    static FrozenDataset loadFormalFrozenSelection(Path runtime) {
        Path selection = testsDirectory().resolve(
                "src/test/resources/rag-eval/candidate8-selection")
                .toAbsolutePath().normalize();
        requireNoSymbolicLinksBelow(testsDirectory(), selection,
                "CANDIDATE8_DATASET_DIRECTORY_INVALID");
        return loadFrozenSelection(runtime, selection);
    }

    static FrozenDataset loadFrozenSelection(Path runtime, Path selectionDirectory) {
        requireSelectionJobProperties();
        RuntimePaths paths = paths(runtime);
        AccessCounter accessCounter = new AccessCounter();
        FrozenManifests manifests = loadFrozenManifests(paths, accessCounter);
        DatasetFiles current = readDatasetFiles(
                selectionDirectory, false, accessCounter);
        Manifest currentManifest = buildManifest(current, false);
        if (!currentManifest.equals(manifests.selection())) {
            throw new IllegalStateException(
                    "CANDIDATE8_SELECTION_RESOURCE_HASH_MISMATCH");
        }
        return new FrozenDataset(
                current.dataset(),
                manifests,
                currentManifest.datasetHash(),
                accessCounter);
    }

    static RunHandle beginSelectionRun(
            RuntimePaths paths, FrozenDataset frozen) {
        synchronized (LEDGER_LOCK) {
            requireSelectionRunAvailable(paths);
            FrozenManifests manifests = frozen.manifests();
            if (!manifests.equals(loadFrozenManifests(
                    paths, frozen.accessCounter()))) {
                throw new IllegalStateException("CANDIDATE8_MANIFEST_CHANGED");
            }
            Map<String, Object> ledger = new LinkedHashMap<>();
            ledger.put("status", "RUNNING");
            ledger.put("datasetHash", manifests.selection().datasetHash());
            ledger.put("selectionManifestSha256", manifests.selectionSha256());
            ledger.put("holdoutManifestSha256", manifests.holdoutSha256());
            ledger.put("startedAt", Instant.now().toString());
            byte[] bytes = canonicalJsonBytes(ledger);
            atomicCreate(paths.ledger(), bytes);
            return new RunHandle(
                    manifests.selection().datasetHash(),
                    manifests.selectionSha256(),
                    manifests.holdoutSha256(),
                    ShadowContractSupport.sha256(bytes),
                    frozen.dataset());
        }
    }

    static void writeDiagnosticAndComplete(
            RuntimePaths paths,
            RunHandle handle,
            Map<String, ?> artifact) {
        synchronized (LEDGER_LOCK) {
            requireFixedRuntimePaths(paths);
            JSONObject ledger = readJson(paths.ledger(), "CANDIDATE8_LEDGER_INVALID");
            if (!"RUNNING".equals(ledger.getString("status"))
                    || !handle.datasetHash().equals(ledger.getString("datasetHash"))
                    || !handle.selectionManifestSha256().equals(
                    ledger.getString("selectionManifestSha256"))
                    || !handle.holdoutManifestSha256().equals(
                    ledger.getString("holdoutManifestSha256"))
                    || !handle.runningLedgerSha256().equals(
                    ShadowContractSupport.sha256(paths.ledger()))) {
                throw new IllegalStateException("CANDIDATE8_LEDGER_INVALID");
            }
            validateArtifact(artifact, handle);
            byte[] artifactBytes = canonicalJsonBytes(artifact);
            atomicCreate(paths.diagnostic(), artifactBytes);
            String artifactSha256 = ShadowContractSupport.sha256(paths.diagnostic());
            if (!artifactSha256.equals(ShadowContractSupport.sha256(artifactBytes))) {
                throw new IllegalStateException("CANDIDATE8_ARTIFACT_HASH_MISMATCH");
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
            JSONObject verified = readJson(paths.ledger(), "CANDIDATE8_LEDGER_INVALID");
            if (!"COMPLETED".equals(verified.getString("status"))
                    || !artifactSha256.equals(verified.getString("artifactSha256"))
                    || !Objects.equals(artifact.get("configHash"),
                    verified.getString("configHash"))) {
                throw new IllegalStateException("CANDIDATE8_LEDGER_INVALID");
            }
        }
    }

    static void writePreRunInvalidDiagnostic(
            RuntimePaths paths,
            Map<String, ?> config,
            String errorCode) {
        synchronized (LEDGER_LOCK) {
            requireFixedRuntimePaths(paths);
            if (hasCandidate8TemporaryArtifacts(paths)
                    || Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("CANDIDATE8_SELECTION_ALREADY_STARTED");
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
            artifact.put("errorCode", Objects.requireNonNull(errorCode, "errorCode"));
            rejectForbiddenFields(artifact);
            atomicCreate(paths.diagnostic(), canonicalJsonBytes(artifact));
        }
    }

    static Map<String, Object> freshInvalidArtifact(
            FrozenDataset frozen,
            Map<String, ?> config,
            String errorCode) {
        Objects.requireNonNull(errorCode, "errorCode");
        Map<String, Object> artifact = baseArtifact(frozen, config);
        artifact.put("status", "INVALID");
        artifact.put("decision", null);
        artifact.put("errorCode", errorCode);
        return artifact;
    }

    static Map<String, Object> freshValidArtifact(
            FrozenDataset frozen,
            Map<String, ?> config,
            String rankingPhaseSha256,
            List<? extends Map<String, ?>> cases) {
        if (!validSha(rankingPhaseSha256)
                || cases.size() != SELECTION_QUERY_COUNT) {
            throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
        }
        List<Map<String, Object>> caseCopies = cases.stream().map(item -> {
            Map<String, Object> copy = new LinkedHashMap<>();
            copy.putAll(item);
            return copy;
        }).toList();
        List<CaseEvidence> evidence = recomputeEvidence(
                frozen.dataset(), caseCopies);
        String recomputedRankingPhase = rankingPhaseHash(caseCopies);
        if (!rankingPhaseSha256.equals(recomputedRankingPhase)) {
            throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
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
        Map<String, Object> artifact = baseArtifact(frozen, config);
        artifact.put("status", "VALID");
        artifact.put("decision", decision);
        artifact.put("errorCode", null);
        artifact.put("summary", summary);
        artifact.put("cases", caseCopies);
        rejectForbiddenFields(artifact);
        return artifact;
    }

    static String decide(List<CaseEvidence> cases) {
        Map<String, Integer> expectedFamiliesByRole = Map.of(
                "target", 8,
                "no-identifier", 2,
                "no-exact-match", 2,
                "exact-only-safety", 1,
                "lexical-lure-below-cutoff", 1,
                "existing-survivor", 1,
                "multi-id-collision", 1);
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
        if (families.size() != 16 || families.values().stream().anyMatch(family ->
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
        if (englishTargets.size() != SELECTION_TARGET_FAMILIES
                || chineseTargets.size() != SELECTION_TARGET_FAMILIES
                || cases.stream().anyMatch(item -> !item.mechanismValid())
                || englishTargets.stream().anyMatch(item ->
                !item.admissionOnlyUnchanged() || !item.survivalOnlyUnchanged()
                        || !(item.jointAp() > item.baselineAp())
                        || !(item.jointNdcg() > item.baselineNdcg()))
                || chineseTargets.stream().anyMatch(item ->
                !item.admissionOnlyUnchanged() || !item.survivalOnlyUnchanged()
                        || Double.compare(item.jointAp(), item.baselineAp()) != 0
                        || Double.compare(item.jointNdcg(), item.baselineNdcg()) != 0)
                || cases.stream().filter(item -> !item.target())
                .anyMatch(item -> !item.controlUnchanged())) {
            return STOP_DECISION;
        }
        double baselineAp = englishTargets.stream().mapToDouble(
                CaseEvidence::baselineAp).average().orElse(0.0D);
        double jointAp = englishTargets.stream().mapToDouble(
                CaseEvidence::jointAp).average().orElse(0.0D);
        double baselineNdcg = englishTargets.stream().mapToDouble(
                CaseEvidence::baselineNdcg).average().orElse(0.0D);
        double jointNdcg = englishTargets.stream().mapToDouble(
                CaseEvidence::jointNdcg).average().orElse(0.0D);
        return jointAp > baselineAp && jointNdcg > baselineNdcg
                ? PROCEED_DECISION : STOP_DECISION;
    }

    static Map<String, Object> diagnosticSummary(List<CaseEvidence> cases) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseCount", cases.size());
        summary.put("targetCaseCount", cases.stream().filter(CaseEvidence::target).count());
        summary.put("controlCaseCount", cases.stream().filter(item -> !item.target()).count());
        summary.put("baselineAP@10", cases.stream().filter(CaseEvidence::target)
                .mapToDouble(CaseEvidence::baselineAp).average().orElse(0.0D));
        summary.put("jointAP@10", cases.stream().filter(CaseEvidence::target)
                .mapToDouble(CaseEvidence::jointAp).average().orElse(0.0D));
        summary.put("baselineNDCG@10", cases.stream().filter(CaseEvidence::target)
                .mapToDouble(CaseEvidence::baselineNdcg).average().orElse(0.0D));
        summary.put("jointNDCG@10", cases.stream().filter(CaseEvidence::target)
                .mapToDouble(CaseEvidence::jointNdcg).average().orElse(0.0D));
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
            throw new IllegalStateException("CANDIDATE8_IDENTIFIER_EXTRACTOR_UNAVAILABLE", failure);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException(
                    "CANDIDATE8_IDENTIFIER_EXTRACTOR_FAILED", failure.getCause());
        }
    }

    static QuerySignals querySignals(String query) {
        String value = Objects.toString(query, "");
        List<String> identifiers = identifierTerms(value);
        Set<String> chineseStopWords = chineseStopWords();
        LinkedHashSet<String> contentTerms = new LinkedHashSet<>();
        Matcher matcher = QUERY_TOKEN_PATTERN.matcher(value);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.codePoints().allMatch(Character::isLetter)
                    && token.codePoints().allMatch(codePoint -> codePoint < 128)) {
                addContentTerm(contentTerms, token.toLowerCase(Locale.ROOT),
                        chineseStopWords);
                continue;
            }
            int codePoints = token.codePointCount(0, token.length());
            for (int start = 0; start < codePoints; start++) {
                for (int length : List.of(4, 3, 2)) {
                    if (start + length > codePoints) {
                        continue;
                    }
                    int startOffset = token.offsetByCodePoints(0, start);
                    int endOffset = token.offsetByCodePoints(startOffset, length);
                    addContentTerm(contentTerms,
                            token.substring(startOffset, endOffset), chineseStopWords);
                }
            }
        }
        contentTerms.removeAll(identifiers);
        return new QuerySignals(identifiers, List.copyOf(contentTerms));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> chineseStopWords() {
        try {
            Field field = KeywordRetriever.class.getDeclaredField("STOP_WORDS");
            field.setAccessible(true);
            return Set.copyOf((Set<String>) field.get(null));
        } catch (NoSuchFieldException | IllegalAccessException failure) {
            throw new IllegalStateException(
                    "CANDIDATE8_STOP_WORDS_UNAVAILABLE", failure);
        }
    }

    private static void addContentTerm(
            Set<String> terms, String term, Set<String> chineseStopWords) {
        if (term.length() < 2 || ENGLISH_STOP_WORDS.contains(term)
                || chineseStopWords.contains(term)
                || IDENTIFIER_CUES.stream().anyMatch(cue ->
                term.equals(cue) || term.contains(cue))
                || term.codePoints().anyMatch(Character::isDigit)) {
            return;
        }
        terms.add(term);
    }

    static boolean boundaryMatches(String documentName, String identifier) {
        if (documentName == null || identifier == null || identifier.isEmpty()) {
            return false;
        }
        return Pattern.compile("(^|[\\x09-\\x0D\\x20\\p{Punct}])"
                + Pattern.quote(identifier)
                + "([\\x09-\\x0D\\x20\\p{Punct}]|$)")
                .matcher(documentName).find();
    }

    static boolean matchesAnyIdentifier(String documentName, List<String> identifiers) {
        return identifiers.stream().anyMatch(identifier ->
                boundaryMatches(documentName, identifier));
    }

    static boolean contentCorroborated(String content, QuerySignals signals) {
        if (content == null || signals.contentTerms().isEmpty()) {
            return false;
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        return signals.contentTerms().stream().anyMatch(normalized::contains);
    }

    static CorroboratedFirstSql corroboratedFirstSql(
            String originalSql,
            Object[] originalParameters,
            QuerySignals signals) {
        Objects.requireNonNull(originalSql, "originalSql");
        Objects.requireNonNull(originalParameters, "originalParameters");
        if (!signals.active()
                || originalParameters.length == 0
                || !(originalParameters[originalParameters.length - 1] instanceof Number limit)
                || limit.intValue() != BUSINESS_SQL_LIMIT) {
            throw new IllegalStateException("CANDIDATE8_SQL_SHAPE_INVALID");
        }
        int orderOffset = originalSql.lastIndexOf(OUTER_ORDER);
        if (orderOffset < 0) {
            throw new IllegalStateException("CANDIDATE8_SQL_SHAPE_INVALID");
        }
        String exactOrder = signals.identifiers().stream()
                .map(ignored -> IDENTIFIER_PREDICATE)
                .collect(Collectors.joining(" OR "));
        String contentOrder = signals.contentTerms().stream()
                .map(ignored -> CONTENT_PREDICATE)
                .collect(Collectors.joining(" OR "));
        String prioritySql = "SELECT base.* FROM (\n"
                + originalSql.substring(0, orderOffset)
                + ") base\nJOIN kmc_document_segment s ON s.id = base.id\n"
                + "ORDER BY ((" + exactOrder + ") AND (" + contentOrder + ")) DESC, "
                + "base.trgm_score DESC, base.document_id ASC, "
                + "base.position ASC NULLS LAST"
                + originalSql.substring(orderOffset + OUTER_ORDER.length());
        List<Object> priorityParameters = new ArrayList<>(
                java.util.Arrays.asList(originalParameters)
                        .subList(0, originalParameters.length - 1));
        priorityParameters.addAll(signals.identifiers());
        priorityParameters.addAll(signals.contentTerms());
        priorityParameters.add(BUSINESS_SQL_LIMIT);
        return new CorroboratedFirstSql(prioritySql, List.copyOf(priorityParameters));
    }

    static List<RetrievalSnapshot> snapshotResults(
            List<RetrievalResult> results, QuerySignals signals) {
        List<RetrievalSnapshot> snapshots = new ArrayList<>();
        if (results == null) {
            return List.of();
        }
        int ordinal = 0;
        for (RetrievalResult result : results) {
            if (result == null) {
                continue;
            }
            boolean exact = matchesAnyIdentifier(
                    result.getDocumentName(), signals.identifiers());
            boolean corroborated = contentCorroborated(result.getContent(), signals);
            Object rawColbertScore = result.getMetadata() == null
                    ? null : result.getMetadata().get("colbert_score");
            double colbertScore = rawColbertScore instanceof Number number
                    ? number.doubleValue() : Double.NaN;
            snapshots.add(new RetrievalSnapshot(
                    ordinal++,
                    result.getSegmentId(),
                    result.getDocumentId(),
                    result.getQmSegmentId(),
                    result.getParentSegmentId(),
                    result.getDocumentName(),
                    result.getContent(),
                    result.getAnswer(),
                    result.getScore(),
                    result.getSource(),
                    colbertScore,
                    exact,
                    corroborated,
                    exact && corroborated));
        }
        return List.copyOf(snapshots);
    }

    static List<RetrievalSnapshot> originalJavaTop50(
            List<RetrievalSnapshot> rows) {
        return rows.stream()
                .sorted(Comparator.comparingDouble(RetrievalSnapshot::score).reversed())
                .limit(BUSINESS_JAVA_LIMIT)
                .toList();
    }

    static List<RetrievalSnapshot> stableCorroboratedFirstJavaTop50(
            List<RetrievalSnapshot> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(
                                RetrievalSnapshot::corroboratedExact).reversed()
                        .thenComparing(
                                Comparator.comparingDouble(
                                        RetrievalSnapshot::score).reversed()))
                .limit(BUSINESS_JAVA_LIMIT)
                .toList();
    }

    static List<RetrievalSnapshot> tieReplace(
            List<RetrievalSnapshot> prefix,
            List<RetrievalSnapshot> full,
            List<RetrievalSnapshot> filterOrder,
            QuerySignals signals) {
        List<RetrievalSnapshot> businessPrefix = List.copyOf(prefix);
        if (!signals.active() || businessPrefix.isEmpty()
                || full.size() <= businessPrefix.size()) {
            return businessPrefix;
        }
        double cutoff = businessPrefix.get(businessPrefix.size() - 1).colbertScore();
        if (!Double.isFinite(cutoff)) {
            return businessPrefix;
        }
        Map<Long, Double> fullScores = new LinkedHashMap<>();
        for (RetrievalSnapshot item : full) {
            if (item.segmentId() != null) {
                fullScores.putIfAbsent(item.segmentId(), item.colbertScore());
            }
        }
        Set<Long> seen = businessPrefix.stream()
                .map(RetrievalSnapshot::segmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<RetrievalSnapshot> anchors = new ArrayList<>();
        for (String identifier : signals.identifiers()) {
            for (RetrievalSnapshot candidate : filterOrder) {
                Long segmentId = candidate.segmentId();
                Double fullScore = segmentId == null ? null : fullScores.get(segmentId);
                if (segmentId == null || seen.contains(segmentId)
                        || !boundaryMatches(candidate.documentName(), identifier)
                        || !candidate.contentCorroborated()
                        || fullScore == null || !Double.isFinite(fullScore)
                        || Double.compare(fullScore, cutoff) < 0) {
                    continue;
                }
                anchors.add(candidate.withColbertScore(fullScore));
                seen.add(segmentId);
                break;
            }
        }
        List<Integer> slots = new ArrayList<>();
        for (int index = businessPrefix.size() - 1; index >= 0; index--) {
            RetrievalSnapshot candidate = businessPrefix.get(index);
            if (!candidate.corroboratedExact()
                    && Double.compare(candidate.colbertScore(), cutoff) == 0) {
                slots.add(index);
            }
        }
        int restoreCount = Math.min(anchors.size(), slots.size());
        if (restoreCount == 0) {
            return businessPrefix;
        }
        List<Integer> selectedSlots = slots.subList(0, restoreCount).stream()
                .sorted().toList();
        List<RetrievalSnapshot> replaced = new ArrayList<>(businessPrefix);
        for (int index = 0; index < restoreCount; index++) {
            replaced.set(selectedSlots.get(index), anchors.get(index));
        }
        return List.copyOf(replaced);
    }

    static List<RetrievalResult> rebuildResults(List<RetrievalSnapshot> snapshots) {
        return snapshots.stream().map(RetrievalSnapshot::toRetrievalResult).toList();
    }

    static List<RetrievalSnapshot> mergeVariants(
            List<? extends List<RetrievalSnapshot>> variants) {
        Map<String, RetrievalSnapshot> bestByKey = new LinkedHashMap<>();
        for (List<RetrievalSnapshot> variant : variants) {
            for (RetrievalSnapshot result : variant) {
                String key = result.segmentId() != null
                        ? "seg:" + result.segmentId()
                        : result.documentId() != null && result.content() != null
                        ? "doc:" + result.documentId() + ":" + result.content().hashCode()
                        : "content:" + Objects.toString(result.content(), "");
                RetrievalSnapshot existing = bestByKey.get(key);
                if (existing == null || result.score() > existing.score()) {
                    bestByKey.put(key, result);
                }
            }
        }
        return bestByKey.values().stream()
                .sorted(Comparator.comparingDouble(RetrievalSnapshot::score).reversed())
                .toList();
    }

    static RootCause classifyStages(
            Set<Long> exactEvidence,
            List<VariantStage> variants,
            List<Long> mergedKeyword,
            List<Long> finalSources) {
        if (exactEvidence.isEmpty()) {
            return RootCause.NONE;
        }
        boolean fullAdmission = variants.stream().anyMatch(variant ->
                containsAny(variant.fullAdmission(), exactEvidence));
        boolean anyBusinessRows = variants.stream().anyMatch(variant ->
                containsAny(variant.businessRows(), exactEvidence));
        boolean anyJavaTop50 = variants.stream().anyMatch(variant ->
                containsAny(variant.javaTop50(), exactEvidence));
        boolean merged = containsAny(mergedKeyword, exactEvidence);
        boolean finalPresent = containsAny(finalSources, exactEvidence);
        if (fullAdmission && !anyBusinessRows) {
            return RootCause.SQL_PRELIMIT_RANK_SUPPRESSION;
        }
        if (anyBusinessRows && !anyJavaTop50) {
            return RootCause.JAVA_KEYWORD_RANK_SUPPRESSION;
        }
        if (anyJavaTop50 && merged && !finalPresent) {
            return RootCause.KEYWORD_TOPK_DOWNSTREAM_SUPPRESSION;
        }
        if (merged && finalPresent) {
            return RootCause.BASELINE_ALREADY_PRESENT;
        }
        return RootCause.ADMISSION_OR_VARIANT_INCONSISTENT;
    }

    private static boolean containsAny(List<Long> values, Set<Long> targets) {
        return values.stream().anyMatch(targets::contains);
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

    private static void validateArtifact(Map<String, ?> artifact, RunHandle handle) {
        rejectForbiddenFields(artifact);
        Object config = artifact.get("config");
        if (!(config instanceof Map<?, ?> rawConfig)) {
            throw new IllegalStateException("CANDIDATE8_ARTIFACT_CONFIG_INVALID");
        }
        Map<String, Object> configMap = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawConfig.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalStateException("CANDIDATE8_ARTIFACT_CONFIG_INVALID");
            }
            configMap.put(key, entry.getValue());
        }
        if (!Objects.equals(artifact.get("configHash"),
                ShadowContractSupport.configHash(configMap))) {
            throw new IllegalStateException("CANDIDATE8_ARTIFACT_CONFIG_INVALID");
        }
        validateConfig(configMap);
        if (!(artifact.get("auditedReads") instanceof AccessAudit audit)
                || audit.selectionResourceAccessCount() != 4
                || audit.manifestAccessCount() != 4
                || audit.holdoutResourceAccessCount() != 0) {
            throw new IllegalStateException("CANDIDATE8_ARTIFACT_ACCESS_AUDIT_INVALID");
        }
        if (!handle.datasetHash().equals(artifact.get("datasetHash"))
                || !handle.selectionManifestSha256().equals(
                artifact.get("selectionManifestHash"))
                || !handle.holdoutManifestSha256().equals(
                artifact.get("holdoutManifestHash"))) {
            throw new IllegalStateException("CANDIDATE8_ARTIFACT_MANIFEST_INVALID");
        }
        String status = Objects.toString(artifact.get("status"), null);
        String decision = Objects.toString(artifact.get("decision"), null);
        Object errorCode = artifact.get("errorCode");
        if ("INVALID".equals(status)) {
            if (decision != null || !(errorCode instanceof String code) || code.isBlank()
                    || artifact.containsKey("cases") || artifact.containsKey("summary")) {
                throw new IllegalStateException("CANDIDATE8_ARTIFACT_INVALID");
            }
            return;
        }
        if (!"VALID".equals(status)
                || !Set.of(PROCEED_DECISION, STOP_DECISION).contains(decision)
                || errorCode != null
                || !(artifact.get("cases") instanceof List<?> cases)
                || cases.size() != SELECTION_QUERY_COUNT
                || !(artifact.get("summary") instanceof Map<?, ?>)) {
            throw new IllegalStateException("CANDIDATE8_ARTIFACT_INVALID");
        }
        List<CaseEvidence> evidence;
        try {
            evidence = recomputeEvidence(
                    handle.dataset(), (List<?>) artifact.get("cases"));
        } catch (RuntimeException failure) {
            throw new IllegalStateException("CANDIDATE8_ARTIFACT_INVALID", failure);
        }
        if (!decision.equals(decide(evidence))) {
            throw new IllegalStateException("CANDIDATE8_ARTIFACT_INVALID");
        }
        Map<?, ?> summary = (Map<?, ?>) artifact.get("summary");
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
                summary.get("holdoutResourceAccessCount"))) {
            throw new IllegalStateException("CANDIDATE8_ARTIFACT_INVALID");
        }
    }

    private static List<CaseEvidence> recomputeEvidence(
            RagEvaluationDataset dataset, List<?> cases) {
        if (cases.size() != dataset.queries().size()) {
            throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
        }
        List<CaseEvidence> evidence = new ArrayList<>();
        for (int index = 0; index < cases.size(); index++) {
            if (!(cases.get(index) instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
            }
            RagEvaluationDataset.QueryCase query = dataset.queries().get(index);
            CaseEvidence claimed = caseEvidence(map);
            String role = frozenRole(query);
            if (!query.id().equals(claimed.queryId())
                    || !query.familyId().equals(claimed.familyId())
                    || !query.language().equals(claimed.language())
                    || !role.equals(claimed.role())
                    || claimed.target() != "target".equals(role)
                    || !query.split().equals(map.get("split"))
                    || !sha256Utf8(query.query()).equals(
                    map.get("originalQuerySha256"))
                    || !sha256Utf8(query.retrievalQuery()).equals(
                    map.get("retrievalQuerySha256"))) {
                throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
            }
            QuerySignals signals = querySignals(query.retrievalQuery());
            if (!Integer.valueOf(signals.identifiers().size()).equals(
                    map.get("extractedIdentifierCount"))
                    || !Integer.valueOf(signals.contentTerms().size()).equals(
                    map.get("extractedContentTermCount"))
                    || !sha256Utf8(JSON.toJSONString(signals.identifiers())).equals(
                    map.get("extractedIdentifierHash"))
                    || !sha256Utf8(JSON.toJSONString(signals.contentTerms())).equals(
                    map.get("extractedContentTermHash"))) {
                throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
            }
            Map<?, ?> arms = requiredMap(map, "arms");
            Map<?, ?> baseline = requiredMap(arms, "BASELINE");
            Map<?, ?> admissionOnly = requiredMap(arms, "ADMISSION_ONLY");
            Map<?, ?> survivalOnly = requiredMap(arms, "SURVIVAL_ONLY");
            Map<?, ?> joint = requiredMap(arms, "JOINT");
            Map<String, Object> rankingEvidence = new LinkedHashMap<>();
            rankingEvidence.put("queryId", query.id());
            rankingEvidence.put("familyId", query.familyId());
            rankingEvidence.put("BASELINE", baseline);
            rankingEvidence.put("ADMISSION_ONLY", admissionOnly);
            rankingEvidence.put("SURVIVAL_ONLY", survivalOnly);
            rankingEvidence.put("JOINT", joint);
            if (!sha256Utf8(JSON.toJSONString(
                    rankingEvidence, JSONWriter.Feature.MapSortField)).equals(
                    map.get("rankingSha256"))) {
                throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
            }
            boolean admissionUnchanged = sameArmBehavior(baseline, admissionOnly);
            boolean survivalUnchanged = sameArmBehavior(baseline, survivalOnly);
            boolean jointUnchanged = sameArmBehavior(baseline, joint);
            RagMetrics.Scores baselineMetrics = metrics(dataset, query, baseline);
            RagMetrics.Scores jointMetrics = metrics(dataset, query, joint);
            boolean target = "target".equals(role);
            boolean mechanismValid;
            boolean controlUnchanged;
            if (target && "en".equals(query.language())) {
                Set<Long> relevant = dataset.qrelsFor(query.id()).keySet().stream()
                        .map(Long::valueOf).collect(Collectors.toSet());
                Set<Long> admitted = corroboratedRelevantIds(
                        stage(admissionOnly, "filterOutput"), relevant, false);
                mechanismValid = signals.active()
                        && admitted.size() == 1
                        && !containsAnySource(baseline, admitted)
                        && !containsAnyStage(admissionOnly, "businessColbert", admitted)
                        && !containsAnyStage(joint, "businessColbert", admitted)
                        && containsAnyStage(joint, "fullColbert", admitted)
                        && corroboratedRelevantIds(
                        stage(joint, "tieOutput"), admitted, true).equals(admitted)
                        && containsAnySource(joint, admitted)
                        && Boolean.TRUE.equals(joint.get("tieChanged"))
                        && Boolean.TRUE.equals(joint.get("cutoffMechanismValid"))
                        && Set.of(
                        RootCause.SQL_PRELIMIT_RANK_SUPPRESSION.name(),
                        RootCause.JAVA_KEYWORD_RANK_SUPPRESSION.name())
                        .contains(map.get("classification"));
                controlUnchanged = true;
            } else if (target) {
                mechanismValid = admissionUnchanged && survivalUnchanged
                        && jointUnchanged
                        && !Boolean.TRUE.equals(joint.get("tieChanged"));
                controlUnchanged = true;
            } else {
                mechanismValid = controlMechanismValid(
                        role, signals, dataset, query, baseline, joint);
                controlUnchanged = "multi-id-collision".equals(role)
                        ? multiIdentifierControlValid(
                        dataset, query, baseline, admissionOnly, survivalOnly, joint)
                        : admissionUnchanged && survivalUnchanged && jointUnchanged;
            }
            CaseEvidence computed = new CaseEvidence(
                    query.id(), query.familyId(), query.language(), role, target,
                    mechanismValid,
                    baselineMetrics.retrievalApAt10(), baselineMetrics.ndcgAt10(),
                    jointMetrics.retrievalApAt10(), jointMetrics.ndcgAt10(),
                    admissionUnchanged, survivalUnchanged, controlUnchanged);
            if (!computed.equals(claimed)) {
                throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
            }
            evidence.add(computed);
        }
        return List.copyOf(evidence);
    }

    private static CaseEvidence caseEvidence(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
        }
        validateCaseEvidenceShape(map);
        return new CaseEvidence(
                requiredArtifactString(map, "queryId"),
                requiredArtifactString(map, "familyId"),
                requiredArtifactString(map, "language"),
                requiredArtifactString(map, "role"),
                requiredArtifactBoolean(map, "target"),
                requiredArtifactBoolean(map, "mechanismValid"),
                requiredArtifactDouble(map, "baselineAP@10"),
                requiredArtifactDouble(map, "baselineNDCG@10"),
                requiredArtifactDouble(map, "jointAP@10"),
                requiredArtifactDouble(map, "jointNDCG@10"),
                requiredArtifactBoolean(map, "admissionOnlyUnchanged"),
                requiredArtifactBoolean(map, "survivalOnlyUnchanged"),
                requiredArtifactBoolean(map, "controlUnchanged"));
    }

    private static String frozenRole(RagEvaluationDataset.QueryCase query) {
        if (query.strata().contains("candidate8-target")) {
            return "target";
        }
        return List.of(
                        "no-identifier", "no-exact-match", "exact-only-safety",
                        "lexical-lure-below-cutoff", "existing-survivor",
                        "multi-id-collision").stream()
                .filter(query.strata()::contains)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "CANDIDATE8_EVIDENCE_INVALID"));
    }

    private static Map<?, ?> requiredMap(Map<?, ?> owner, String key) {
        Object value = owner.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
        }
        return map;
    }

    private static List<Map<?, ?>> stage(Map<?, ?> arm, String key) {
        Object value = arm.get(key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
        }
        List<Map<?, ?>> stage = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
            }
            stage.add(map);
        }
        return List.copyOf(stage);
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
                throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
            }
            return number.longValue();
        }).toList();
    }

    private static List<Double> sourceScores(List<Map<?, ?>> sources) {
        return sources.stream().map(source -> {
            Object score = source.get("score");
            if (!(score instanceof Number number)
                    || !Double.isFinite(number.doubleValue())) {
                throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
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

    private static Set<Long> corroboratedRelevantIds(
            List<Map<?, ?>> stage,
            Set<Long> relevant,
            boolean requireCutoffTie) {
        return stage.stream().filter(item ->
                        Boolean.TRUE.equals(item.get("exactMatch"))
                                && Boolean.TRUE.equals(item.get("contentCorroborated"))
                                && (!requireCutoffTie
                                || Boolean.TRUE.equals(item.get("cutoffEligible"))))
                .map(item -> item.get("segmentId"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .filter(relevant::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean containsAnyStage(
            Map<?, ?> arm, String stageName, Set<Long> expected) {
        return sourceIds(stage(arm, stageName)).stream().anyMatch(expected::contains);
    }

    private static boolean containsAnySource(Map<?, ?> arm, Set<Long> expected) {
        return containsAnyStage(arm, "finalSources", expected);
    }

    private static boolean controlMechanismValid(
            String role,
            QuerySignals signals,
            RagEvaluationDataset dataset,
            RagEvaluationDataset.QueryCase query,
            Map<?, ?> baseline,
            Map<?, ?> joint) {
        List<Map<?, ?>> full = stage(joint, "fullColbert");
        return switch (role) {
            case "no-identifier" -> signals.identifiers().isEmpty();
            case "no-exact-match" -> full.stream().noneMatch(item ->
                    Boolean.TRUE.equals(item.get("exactMatch")));
            case "exact-only-safety" -> full.stream().noneMatch(item ->
                    Boolean.TRUE.equals(item.get("exactMatch"))
                            && Boolean.TRUE.equals(item.get("contentCorroborated")));
            case "lexical-lure-below-cutoff" ->
                    !Boolean.TRUE.equals(joint.get("tieChanged"));
            case "existing-survivor" -> containsAnySource(
                    baseline, dataset.qrelsFor(query.id()).keySet().stream()
                            .map(Long::valueOf).collect(Collectors.toSet()));
            case "multi-id-collision" -> signals.identifiers().size() == 2;
            default -> false;
        };
    }

    private static boolean multiIdentifierControlValid(
            RagEvaluationDataset dataset,
            RagEvaluationDataset.QueryCase query,
            Map<?, ?> baseline,
            Map<?, ?> admissionOnly,
            Map<?, ?> survivalOnly,
            Map<?, ?> joint) {
        if (!sameArmBehavior(baseline, admissionOnly)
                || !sameArmBehavior(baseline, survivalOnly)) {
            return false;
        }
        Set<Long> baselineIds = new LinkedHashSet<>(
                sourceIds(stage(baseline, "finalSources")));
        Set<Long> added = new LinkedHashSet<>(
                sourceIds(stage(joint, "finalSources")));
        added.removeAll(baselineIds);
        Set<Long> relevant = dataset.qrelsFor(query.id()).keySet().stream()
                .map(Long::valueOf).collect(Collectors.toSet());
        List<Double> baselineScores = sourceScores(
                stage(baseline, "finalSources")).stream().sorted().toList();
        List<Double> jointScores = sourceScores(
                stage(joint, "finalSources")).stream().sorted().toList();
        return relevant.containsAll(added) && baselineScores.equals(jointScores);
    }

    private static String rankingPhaseHash(List<?> cases) {
        List<String> hashes = cases.stream().map(item -> {
            if (!(item instanceof Map<?, ?> map)
                    || !validSha(Objects.toString(map.get("rankingSha256"), null))) {
                throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
            }
            return String.valueOf(map.get("rankingSha256"));
        }).toList();
        return sha256Utf8(JSON.toJSONString(
                hashes, JSONWriter.Feature.MapSortField));
    }

    private static String sha256Utf8(String value) {
        return ShadowContractSupport.sha256(
                Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
    }

    private static void validateCaseEvidenceShape(Map<?, ?> map) {
        Object variantsValue = map.get("variants");
        Object armsValue = map.get("arms");
        Object callCountsValue = map.get("callCounts");
        if (!(map.get("classification") instanceof String classification)
                || classification.isBlank()
                || !validSha(Objects.toString(map.get("rankingSha256"), null))
                || !(variantsValue instanceof List<?> variants) || variants.isEmpty()
                || !(armsValue instanceof Map<?, ?> arms)
                || !arms.keySet().equals(Set.of(
                "BASELINE", "ADMISSION_ONLY", "SURVIVAL_ONLY", "JOINT"))
                || !(callCountsValue instanceof Map<?, ?> callCounts)) {
            throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
        }
        List<String> requiredCounts = List.of(
                "sutDbCalls", "diagnosticSqlCalls", "diagnosticContextDbCalls",
                "diagnosticTotalDbCalls", "diagnosticBusinessColbertCalls",
                "diagnosticFullColbertCalls", "sutEmbeddingCalls",
                "diagnosticBusinessEmbeddingCalls",
                "diagnosticFullEmbeddingCalls", "diagnosticEmbeddingCalls",
                "addedVectorCalls", "addedMetadataCalls", "addedGraphCalls",
                "addedNetworkCalls");
        if (requiredCounts.stream().anyMatch(key ->
                !(callCounts.get(key) instanceof Number number)
                        || number.longValue() < 0L)) {
            throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
        }
        long diagnosticSql = ((Number) callCounts.get("diagnosticSqlCalls")).longValue();
        long diagnosticContext = ((Number) callCounts.get(
                "diagnosticContextDbCalls")).longValue();
        long diagnosticTotal = ((Number) callCounts.get(
                "diagnosticTotalDbCalls")).longValue();
        long diagnosticBusinessEmbedding = ((Number) callCounts.get(
                "diagnosticBusinessEmbeddingCalls")).longValue();
        long diagnosticFullEmbedding = ((Number) callCounts.get(
                "diagnosticFullEmbeddingCalls")).longValue();
        long diagnosticEmbedding = ((Number) callCounts.get(
                "diagnosticEmbeddingCalls")).longValue();
        if (diagnosticTotal != diagnosticSql + diagnosticContext
                || diagnosticEmbedding
                != diagnosticBusinessEmbedding + diagnosticFullEmbedding
                || ((Number) callCounts.get("addedVectorCalls")).longValue() != 0L
                || ((Number) callCounts.get("addedMetadataCalls")).longValue() != 0L
                || ((Number) callCounts.get("addedGraphCalls")).longValue() != 0L
                || ((Number) callCounts.get("addedNetworkCalls")).longValue() != 0L) {
            throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
        }
    }

    private static String requiredArtifactString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
        }
        return text;
    }

    private static boolean requiredArtifactBoolean(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
        }
        return flag;
    }

    private static double requiredArtifactDouble(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("CANDIDATE8_EVIDENCE_INVALID");
        }
        return number.doubleValue();
    }

    private static void validateConfig(Map<?, ?> config) {
        Object remoteValue = config.get("kbRemoteReranking");
        Object colbertValue = config.get("colbert");
        Object executorValue = config.get("executor");
        Object featureHashValue = config.get("featureHash");
        Object inventoryValue = config.get("providerInventory");
        Object eligibleValue = config.get("eligibleProviderClasses");
        if (!Boolean.TRUE.equals(config.get("candidate3Enabled"))
                || !Boolean.FALSE.equals(config.get("candidate8Enabled"))
                || !Boolean.FALSE.equals(config.get("identifierAware"))
                || !Boolean.TRUE.equals(config.get("identifierConsistencyEnabled"))
                || !Boolean.FALSE.equals(
                config.get("identifierEvidenceCorroboratedTieEnabled"))
                || !Boolean.FALSE.equals(config.get("localRerankerEnabled"))
                || !Boolean.FALSE.equals(config.get("onnxRerankerEnabled"))
                || !Integer.valueOf(10).equals(config.get("topK"))
                || !Integer.valueOf(BUSINESS_JAVA_LIMIT).equals(
                config.get("keywordCandidateTopK"))
                || !Integer.valueOf(BUSINESS_SQL_LIMIT).equals(
                config.get("keywordSqlLimit"))
                || !Integer.valueOf(30).equals(config.get("businessColbertLimit"))
                || !"java-fallback".equals(config.get("nativeMode"))
                || !"corroborated-identifier-cutoff-tie-diagnostic-v1".equals(
                config.get("candidate8EvidenceAlgorithm"))
                || !"deterministic-config-fail-closed-v2".equals(
                config.get("identifierEvidenceEligibilityPolicy"))
                || !"document-name-content-corroborated-exact-first-v1".equals(
                config.get("identifierEvidenceAdmissionPolicy"))
                || !"colbert-cutoff-tie-replacement-v1".equals(
                config.get("identifierEvidenceSurvivalPolicy"))
                || !"ascii-han-non-identifier-content-v1".equals(
                config.get("identifierEvidenceContentPolicy"))
                || !"request-fail-closed-v1".equals(
                config.get("identifierEvidenceColbertFailurePolicy"))
                || !(remoteValue instanceof Map<?, ?> remote)
                || !Boolean.FALSE.equals(remote.get("enabled"))
                || !"".equals(remote.get("providerName"))
                || !"".equals(remote.get("modelName"))
                || !(colbertValue instanceof Map<?, ?> colbert)
                || !Boolean.TRUE.equals(colbert.get("enabled"))
                || !(executorValue instanceof Map<?, ?>)
                || !(featureHashValue instanceof Map<?, ?>)
                || !(inventoryValue instanceof List<?> inventory)
                || inventory.isEmpty()
                || !inventory.equals(inventory.stream().map(String::valueOf)
                .sorted().toList())
                || inventory.stream().map(String::valueOf)
                .anyMatch(item -> !ELIGIBLE_PROVIDER_CLASSES.contains(item))
                || !inventory.contains(
                RERANKER_PACKAGE + "DeterministicRerankerProvider")
                || !(eligibleValue instanceof List<?> eligible)
                || !eligible.equals(ELIGIBLE_PROVIDER_CLASSES)) {
            throw new IllegalStateException("CANDIDATE8_ARTIFACT_CONFIG_INVALID");
        }
    }

    private static void rejectForbiddenFields(Object value) {
        Object jsonTree;
        try {
            jsonTree = JSON.parse(JSON.toJSONString(value));
        } catch (RuntimeException failure) {
            throw new IllegalStateException("CANDIDATE8_ARTIFACT_INVALID", failure);
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
                            "CANDIDATE8_FORBIDDEN_ARTIFACT_FIELD");
                }
                rejectForbiddenJsonTree(entry.getValue());
            }
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(RagCandidate8DiagnosticSupport::rejectForbiddenJsonTree);
        }
    }

    private static DatasetFiles readDatasetFiles(Path directory, boolean holdout) {
        return readDatasetFiles(directory, holdout, null);
    }

    private static DatasetFiles readDatasetFiles(
            Path directory, boolean holdout, AccessCounter accessCounter) {
        Path normalized = requireDirectory(directory,
                "CANDIDATE8_DATASET_DIRECTORY_INVALID");
        Path corpusPath = requireRegular(normalized.resolve("corpus.jsonl"),
                "CANDIDATE8_DATASET_RESOURCE_INVALID");
        Path queriesPath = requireRegular(normalized.resolve("queries.jsonl"),
                "CANDIDATE8_DATASET_RESOURCE_INVALID");
        Path qrelsPath = requireRegular(normalized.resolve("qrels.tsv"),
                "CANDIDATE8_DATASET_RESOURCE_INVALID");
        Path pressurePath = requireRegular(normalized.resolve("pressure.json"),
                "CANDIDATE8_DATASET_RESOURCE_INVALID");
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
                    "CANDIDATE8_DATASET_RESOURCE_READ_FAILED", failure);
        }
    }

    private static ResourceBytes readDatasetResource(
            Path path, boolean holdout, AccessCounter accessCounter) throws IOException {
        if (accessCounter != null) {
            accessCounter.openDatasetResource(holdout);
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
                throw new IllegalArgumentException("CANDIDATE8_DUPLICATE_SEGMENT");
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
                throw new IllegalArgumentException("CANDIDATE8_DUPLICATE_QUERY");
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
                throw new IllegalArgumentException("CANDIDATE8_QREL_INVALID");
            }
            Map<String, Integer> queryQrels = qrels.computeIfAbsent(
                    columns[0], ignored -> new LinkedHashMap<>());
            if (queryQrels.putIfAbsent(
                    columns[1], Integer.parseInt(columns[2])) != null) {
                throw new IllegalArgumentException("CANDIDATE8_DUPLICATE_QREL");
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
                "pressureCount", "neutralAdmissionCue", "lexicalSegmentIdStart",
                "pressureSegmentIdStart", "lexicalDocumentIdStart",
                "pressureDocumentIdStart");
        if (!json.keySet().equals(expected)
                || !"candidate8-pressure-v1".equals(json.getString("generator"))
                || json.getIntValue("version") != 1) {
            throw new IllegalArgumentException("CANDIDATE8_PRESSURE_SPEC_INVALID");
        }
        return new PressureSpec(
                json.getString("generator"),
                json.getIntValue("version"),
                json.getLongValue("seed"),
                json.getIntValue("lexicalPerTargetFamily"),
                json.getIntValue("pressureCount"),
                required(json, "neutralAdmissionCue"),
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
                .filter(query -> query.familyId().matches("c8[sh]-t\\d+"))
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
                            "CANDIDATE8_IDENTIFIER_SHAPE_INVALID"));
            for (int index = 1; index <= pressure.lexicalPerTargetFamily(); index++) {
                putGenerated(expanded, segmentId++, documentId++,
                        pressure.neutralAdmissionCue()
                                + " Routine lexical admission note " + index + ".",
                        Map.of(
                                "candidate8Role", "lexical-distractor",
                                "familyId", familyId,
                                "identifierShape", shape,
                                "documentName", (holdout ? "holdout" : "selection")
                                        + "-lexical-" + familyId + "-" + index));
            }
        }
        if (segmentId != pressure.pressureSegmentIdStart()
                || documentId != pressure.pressureDocumentIdStart()) {
            throw new IllegalArgumentException("CANDIDATE8_PRESSURE_ID_RULE_INVALID");
        }
        for (int index = 1; index <= pressure.pressureCount(); index++) {
            putGenerated(expanded, segmentId++, documentId++,
                    pressure.neutralAdmissionCue() + " Pressure admission record.",
                    Map.of(
                            "candidate8Role", "pressure-distractor",
                            "familyId", "candidate8-pressure",
                            "identifierShape", "none",
                            "documentName", (holdout ? "holdout" : "selection")
                                    + "-pressure-record-" + index));
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
            throw new IllegalArgumentException("CANDIDATE8_GENERATED_ID_COLLISION");
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
        int pressureCount = holdout ? HOLDOUT_PRESSURE_COUNT : SELECTION_PRESSURE_COUNT;
        long segmentMin = holdout ? HOLDOUT_SEGMENT_ID_MIN : SELECTION_SEGMENT_ID_MIN;
        long segmentMax = holdout ? HOLDOUT_SEGMENT_ID_MAX : SELECTION_SEGMENT_ID_MAX;
        long documentMin = holdout ? HOLDOUT_DOCUMENT_ID_MIN : SELECTION_DOCUMENT_ID_MIN;
        long documentMax = holdout ? HOLDOUT_DOCUMENT_ID_MAX : SELECTION_DOCUMENT_ID_MAX;
        String prefix = holdout ? "c8h-" : "c8s-";
        String split = holdout ? "holdout" : "selection";
        if (dataset.queries().size() != expectedQueries
                || dataset.corpusById().size() != expectedSegments
                || qrelCount != expectedQrels
                || pressure.pressureCount() != pressureCount
                || pressure.lexicalPerTargetFamily() != LEXICAL_PER_TARGET
                || pressure.seed() != (holdout ? HOLDOUT_SEED : SELECTION_SEED)) {
            throw new IllegalArgumentException("CANDIDATE8_DATASET_COUNT_MISMATCH");
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
                .filter(id -> id.startsWith(prefix + "l")).count();
        long survivorCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "u")).count();
        long multiIdCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "m")).count();
        if (targetCount != targetFamilies || noIdCount != noIdFamilies
                || noExactCount != noExactFamilies
                || exactOnlyCount != exactOnlyFamilies || lureCount != lureFamilies
                || survivorCount != survivorFamilies || multiIdCount != multiIdFamilies
                || families.size() != targetFamilies + noIdFamilies
                + noExactFamilies + exactOnlyFamilies + lureFamilies
                + survivorFamilies + multiIdFamilies) {
            throw new IllegalArgumentException("CANDIDATE8_FAMILY_COUNT_MISMATCH");
        }
        Map<String, String> relevantSegmentFamilies = new LinkedHashMap<>();
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            for (String segmentId : dataset.qrelsFor(query.id()).keySet()) {
                String existingFamily = relevantSegmentFamilies.putIfAbsent(
                        segmentId, query.familyId());
                if (existingFamily != null
                        && !existingFamily.equals(query.familyId())) {
                    throw new IllegalArgumentException(
                            "CANDIDATE8_CROSS_FAMILY_SHARING_INVALID");
                }
            }
        }
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
                        "CANDIDATE8_BILINGUAL_FAMILY_INVALID");
            }
            boolean target = entry.getKey().startsWith(prefix + "t");
            boolean noId = entry.getKey().startsWith(prefix + "n");
            boolean noExact = entry.getKey().startsWith(prefix + "x");
            boolean exactOnly = entry.getKey().startsWith(prefix + "e");
            boolean lure = entry.getKey().startsWith(prefix + "l");
            boolean survivor = entry.getKey().startsWith(prefix + "u");
            boolean multiId = entry.getKey().startsWith(prefix + "m");
            int expectedRelevant = target ? 3 : noId || survivor || multiId ? 1 : 0;
            Set<String> shared = null;
            String shape = null;
            for (RagEvaluationDataset.QueryCase query : family) {
                Set<String> qrels = dataset.qrelsFor(query.id()).keySet();
                if (qrels.size() != expectedRelevant
                        || query.answerable() != (expectedRelevant > 0)) {
                    throw new IllegalArgumentException("CANDIDATE8_QREL_SHAPE_INVALID");
                }
                if (shared == null) {
                    shared = Set.copyOf(qrels);
                } else if (!shared.equals(qrels)) {
                    throw new IllegalArgumentException(
                            "CANDIDATE8_FAMILY_QREL_SHARING_INVALID");
                }
                QuerySignals signals = querySignals(query.retrievalQuery());
                List<String> identifiers = signals.identifiers();
                if (!noId) {
                    int expectedIdentifiers = multiId ? 2 : 1;
                    if (identifiers.size() != expectedIdentifiers
                            || !identifiers.equals(identifierTerms(query.query()))) {
                        throw new IllegalArgumentException(
                                "CANDIDATE8_IDENTIFIER_FIXTURE_INVALID");
                    }
                } else if (!identifiers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "CANDIDATE8_NO_IDENTIFIER_CONTROL_INVALID");
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
                                    "CANDIDATE8_IDENTIFIER_SHAPE_INVALID"));
                    if (shape == null) {
                        shape = currentShape;
                    } else if (!shape.equals(currentShape)) {
                        throw new IllegalArgumentException(
                                "CANDIDATE8_IDENTIFIER_SHAPE_INVALID");
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
                            exactSegments.get(0).metadata().get("candidate8Role"), ""))) {
                        throw new IllegalArgumentException(
                            "CANDIDATE8_EXACT_EVIDENCE_INVALID");
                    }
                    RagEvaluationDataset.CorpusSegment anchor = exactSegments.get(0);
                    if (!contentCorroborated(anchor.content(), signals)) {
                        throw new IllegalArgumentException(
                                "CANDIDATE8_CONTENT_CORROBORATION_INVALID");
                    }
                } else if (noExact && !exactSegments.isEmpty()) {
                    throw new IllegalArgumentException(
                            "CANDIDATE8_NO_EXACT_CONTROL_INVALID");
                } else if (exactOnly || lure || survivor) {
                    String expectedRole = exactOnly ? "exact-only-safety"
                            : lure ? "lexical-lure-below-cutoff" : "existing-survivor";
                    if (exactSegments.size() != 1
                            || !entry.getKey().equals(Objects.toString(
                            exactSegments.get(0).metadata().get("familyId"), ""))
                            || !expectedRole.equals(Objects.toString(
                            exactSegments.get(0).metadata().get("candidate8Role"), ""))
                            || contentCorroborated(exactSegments.get(0).content(), signals)
                            != (lure || survivor)) {
                        throw new IllegalArgumentException(
                                "CANDIDATE8_SAFETY_CONTROL_INVALID");
                    }
                } else if (multiId) {
                    long eligible = exactSegments.stream().filter(segment ->
                                    "multi-id-eligible".equals(Objects.toString(
                                            segment.metadata().get("candidate8Role"), ""))
                                            && contentCorroborated(segment.content(), signals))
                            .count();
                    long collision = exactSegments.stream().filter(segment ->
                                    "multi-id-collision".equals(Objects.toString(
                                            segment.metadata().get("candidate8Role"), ""))
                                            && !contentCorroborated(segment.content(), signals))
                            .count();
                    if (exactSegments.size() != 2 || eligible != 1L || collision != 1L) {
                        throw new IllegalArgumentException(
                                "CANDIDATE8_MULTI_IDENTIFIER_CONTROL_INVALID");
                    }
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
                "han-adjacent", perShape))) {
            throw new IllegalArgumentException(
                    "CANDIDATE8_IDENTIFIER_SHAPE_COUNT_INVALID");
        }
        Set<String> documents = new LinkedHashSet<>();
        for (RagEvaluationDataset.CorpusSegment segment
                : dataset.corpusById().values()) {
            long segmentId = Long.parseLong(segment.segmentId());
            long documentId = Long.parseLong(segment.documentId());
            if (segmentId < segmentMin || segmentId > segmentMax
                    || documentId < documentMin || documentId > documentMax
                    || segment.parentSegmentId() != null
                    || !documents.add(segment.documentId())
                    || !(segment.metadata().get("documentName") instanceof String name)
                    || name.isBlank()) {
                throw new IllegalArgumentException("CANDIDATE8_ID_ISOLATION_INVALID");
            }
        }
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
            throw new IllegalArgumentException("CANDIDATE8_DATASET_ISOLATION_INVALID");
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
                holdout ? "candidate8-holdout" : "candidate8-selection",
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
        value.put("exactOnlySafetyFamilyCount",
                holdout ? HOLDOUT_EXACT_ONLY_FAMILIES : SELECTION_EXACT_ONLY_FAMILIES);
        value.put("lexicalLureBelowCutoffFamilyCount",
                holdout ? HOLDOUT_LURE_FAMILIES : SELECTION_LURE_FAMILIES);
        value.put("existingSurvivorFamilyCount",
                holdout ? HOLDOUT_SURVIVOR_FAMILIES : SELECTION_SURVIVOR_FAMILIES);
        value.put("multiIdentifierCollisionFamilyCount",
                holdout ? HOLDOUT_MULTI_ID_FAMILIES : SELECTION_MULTI_ID_FAMILIES);
        value.put("bilingualQueriesPerFamily", 2);
        value.put("crossFamilySharing", false);
        value.put("relevantSegmentsPerTargetFamily", 3);
        value.put("relevantSegmentsPerNoIdentifierFamily", 1);
        value.put("roleTable", roleTable(holdout));
        value.put("evidenceControlUniqueSegmentCount", holdout ? 62 : 31);
        value.put("qrelPairCount", holdout ? HOLDOUT_QREL_COUNT : SELECTION_QREL_COUNT);
        value.put("lexicalDistractorCount", targetFamilies * LEXICAL_PER_TARGET);
        value.put("pressureDistractorCount", files.pressure().pressureCount());
        value.put("pressureGenerator", files.pressure().generator());
        value.put("pressureVersion", files.pressure().version());
        value.put("pressureSeed", Math.toIntExact(files.pressure().seed()));
        value.put("neutralAdmissionCueSha256", ShadowContractSupport.sha256(
                files.pressure().neutralAdmissionCue().getBytes(StandardCharsets.UTF_8)));
        value.put("identifierShapeFamilyCounts", Map.of(
                "numeric-token", targetFamilies / 4,
                "doc-prefix", targetFamilies / 4,
                "zero-padded", targetFamilies / 4,
                "han-adjacent", targetFamilies / 4));
        value.put("identifierExtractorPolicy", "keyword-identifier-terms-v1");
        value.put("identifierExtractionOutputSha256",
                identifierExtractionOutputHash(files.dataset()));
        value.put("asciiContentTermRegex", "[A-Za-z]{2,}");
        value.put("hanContentTermPolicy",
                "unicode-code-point-position-ascending-window-4-3-2-v1");
        value.put("contentTermOrderingPolicy",
                "source-code-point-position-ascii-and-han-window-v1");
        value.put("contentTermDedupPolicy", "linked-hash-set-first-occurrence-v1");
        value.put("identifierCueExclusionPolicy", "equals-or-contains-v1");
        value.put("englishStopWords", ENGLISH_STOP_WORDS.stream().sorted().toList());
        value.put("identifierCues", IDENTIFIER_CUES.stream().sorted().toList());
        value.put("chineseStopWordsSha256", ShadowContractSupport.configHash(
                Map.of("stopWords", chineseStopWords().stream().sorted().toList())));
        value.put("documentNameSqlBoundaryTemplate", IDENTIFIER_PREDICATE);
        value.put("documentNameJavaBoundaryTemplate", JAVA_IDENTIFIER_BOUNDARY_TEMPLATE);
        value.put("contentSqlTemplate", CONTENT_PREDICATE);
        value.put("contentJavaTemplate", JAVA_CONTENT_TEMPLATE);
        value.put("boundaryPolicy", "postgres16-c-space-punctuation-v1");
        value.put("tieReplacementPolicy", "capacity-neutral-cutoff-exact-v1");
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
        return value;
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
                roleContract(2 * scale, 1, 0, 2 * scale, 4 * scale));
        roles.put("noExactMatch", roleContract(2 * scale, 0, 0, 0, 0));
        roles.put("exactOnlySafety", roleContract(1 * scale, 0, 1, scale, 0));
        roles.put("lexicalLureBelowCutoff",
                roleContract(1 * scale, 0, 1, scale, 0));
        roles.put("existingSurvivor", roleContract(1 * scale, 1, 0, scale, 2 * scale));
        roles.put("multiIdentifierCollision",
                roleContract(1 * scale, 1, 1, 2 * scale, 2 * scale));
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
        List<Map<String, Object>> queries = dataset.queries().stream().map(query -> {
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
            throw new IllegalStateException("CANDIDATE8_MANIFEST_INVALID");
        }
        try {
            manifestBytes = Files.readAllBytes(path);
            json = JSON.parseObject(new String(manifestBytes, StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException("CANDIDATE8_MANIFEST_INVALID", failure);
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
            throw new IllegalStateException("CANDIDATE8_MANIFEST_INVALID");
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
            throw new IllegalStateException("CANDIDATE8_MANIFEST_INVALID");
        }
        Map<String, ResourceHash> resources = new LinkedHashMap<>();
        for (String name : List.of("corpus", "queries", "qrels", "pressure")) {
            JSONObject resource = resourceJson.getJSONObject(name);
            if (resource == null
                    || !resource.keySet().equals(Set.of("file", "sha256"))
                    || !RESOURCE_FILES.get(name).equals(resource.getString("file"))
                    || !validSha(resource.getString("sha256"))) {
                throw new IllegalStateException("CANDIDATE8_MANIFEST_INVALID");
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
            throw new IllegalStateException("CANDIDATE8_MANIFEST_INVALID");
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
        throw new IllegalStateException("CANDIDATE8_TESTS_DIRECTORY_NOT_FOUND");
    }

    private static void requireFixedRuntimePaths(RuntimePaths paths) {
        RuntimePaths expected = paths(paths.freezeDirectory().getParent());
        if (!expected.equals(paths)
                || Files.isSymbolicLink(paths.freezeDirectory())) {
            throw new IllegalArgumentException("CANDIDATE8_RUNTIME_PATH_INVALID");
        }
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
        if (!normalizedTarget.startsWith(normalizedBase)) {
            throw new IllegalArgumentException(errorCode);
        }
        Path current = normalizedBase;
        if (Files.isSymbolicLink(current)) {
            throw new IllegalArgumentException(errorCode);
        }
        for (Path component : normalizedBase.relativize(normalizedTarget)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(errorCode);
            }
        }
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

    private static boolean hasCandidate8TemporaryArtifacts(RuntimePaths paths) {
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
                    "CANDIDATE8_TEMPORARY_ARTIFACT_AUDIT_FAILED", failure);
        }
    }

    private static void atomicCreate(Path target, byte[] bytes) {
        atomicWrite(target, bytes, false);
    }

    private static void atomicReplace(Path target, byte[] bytes) {
        atomicWrite(target, bytes, true);
    }

    private static void atomicWrite(Path target, byte[] bytes, boolean replace) {
        Path normalized = target.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalized.getParent());
            if (Files.isSymbolicLink(normalized.getParent())) {
                throw new IllegalStateException("CANDIDATE8_ATOMIC_PARENT_INVALID");
            }
            Path temp = Files.createTempFile(
                    normalized.getParent(), normalized.getFileName().toString(), ".tmp");
            try {
                Files.write(temp, bytes);
                if (replace) {
                    Files.move(temp, normalized,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temp, normalized, StandardCopyOption.ATOMIC_MOVE);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (AtomicMoveNotSupportedException failure) {
            throw new IllegalStateException("CANDIDATE8_ATOMIC_MOVE_REQUIRED", failure);
        } catch (FileAlreadyExistsException failure) {
            throw new IllegalStateException("CANDIDATE8_ARTIFACT_ALREADY_EXISTS", failure);
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE8_ATOMIC_WRITE_FAILED", failure);
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
                        JSONWriter.Feature.PrettyFormat,
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
            throw new IllegalArgumentException("CANDIDATE8_REQUIRED_FIELD_MISSING");
        }
        return value;
    }

    private static List<String> strings(JSONArray values) {
        return values == null ? List.of() : values.toJavaList(String.class);
    }

    private static boolean validSha(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    enum RootCause {
        SQL_PRELIMIT_RANK_SUPPRESSION(true),
        JAVA_KEYWORD_RANK_SUPPRESSION(true),
        KEYWORD_TOPK_DOWNSTREAM_SUPPRESSION(false),
        BASELINE_ALREADY_PRESENT(false),
        ADMISSION_OR_VARIANT_INCONSISTENT(false),
        FIXTURE_OR_BOUNDARY_INVALID(false),
        NONE(false);

        private final boolean modifiable;

        RootCause(boolean modifiable) {
            this.modifiable = modifiable;
        }

        boolean modifiable() {
            return modifiable;
        }
    }

    enum CounterfactualMode {
        SQL,
        JAVA,
        DUAL,
        NONE
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
            AccessCounter accessCounter) {

        AccessAudit auditedReads() {
            return accessCounter.snapshot();
        }
    }

    record AccessAudit(
            int selectionResourceAccessCount,
            int manifestAccessCount,
            int holdoutResourceAccessCount) {
    }

    private static final class AccessCounter {
        private int selectionResourceAccessCount;
        private int manifestAccessCount;
        private int holdoutResourceAccessCount;

        private void openDatasetResource(boolean holdout) {
            if (holdout) {
                holdoutResourceAccessCount++;
            } else {
                selectionResourceAccessCount++;
            }
        }

        private void openManifest() {
            manifestAccessCount++;
        }

        private AccessAudit snapshot() {
            return new AccessAudit(
                    selectionResourceAccessCount,
                    manifestAccessCount,
                    holdoutResourceAccessCount);
        }
    }

    record RunHandle(
            String datasetHash,
            String selectionManifestSha256,
            String holdoutManifestSha256,
            String runningLedgerSha256,
            RagEvaluationDataset dataset) {
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
            boolean exactMatch,
            boolean contentCorroborated,
            boolean corroboratedExact) {

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

        RetrievalSnapshot withColbertScore(double value) {
            return new RetrievalSnapshot(
                    ordinal, segmentId, documentId, qmSegmentId, parentSegmentId,
                    documentName, content, answer, score, source, value,
                    exactMatch, contentCorroborated, corroboratedExact);
        }

        Map<String, Object> sanitized(int rank) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("ordinal", ordinal);
            value.put("segmentId", segmentId);
            value.put("documentId", documentId);
            value.put("rank", rank);
            value.put("score", score);
            value.put("exactMatch", exactMatch);
            value.put("contentCorroborated", contentCorroborated);
            value.put("fullColbertScore", colbertScore);
            value.put("corroboratedExactFiniteScore",
                    corroboratedExact && Double.isFinite(colbertScore));
            return value;
        }
    }

    record QuerySignals(List<String> identifiers, List<String> contentTerms) {
        QuerySignals {
            identifiers = List.copyOf(identifiers);
            contentTerms = List.copyOf(contentTerms);
        }

        boolean active() {
            return !identifiers.isEmpty() && !contentTerms.isEmpty();
        }
    }

    record CorroboratedFirstSql(String sql, List<Object> parameters) {
        CorroboratedFirstSql {
            parameters = List.copyOf(parameters);
        }
    }

    record VariantStage(
            List<Long> fullAdmission,
            List<Long> businessRows,
            List<Long> javaTop50) {
    }

    record CaseEvidence(
            String queryId,
            String familyId,
            String language,
            String role,
            boolean target,
            boolean mechanismValid,
            double baselineAp,
            double baselineNdcg,
            double jointAp,
            double jointNdcg,
            boolean admissionOnlyUnchanged,
            boolean survivalOnlyUnchanged,
            boolean controlUnchanged) {

        static CaseEvidence target(
                String queryId, String familyId, String language,
                boolean mechanismValid,
                double baselineAp, double baselineNdcg,
                double jointAp, double jointNdcg) {
            return new CaseEvidence(
                    queryId, familyId, language, "target", true, mechanismValid,
                    baselineAp, baselineNdcg, jointAp, jointNdcg,
                    true, true, true);
        }

        static CaseEvidence control(
                String queryId, String familyId, String language,
                String role, boolean unchanged) {
            return new CaseEvidence(
                    queryId, familyId, language, role, false, true,
                    0.0D, 0.0D, 0.0D, 0.0D,
                    unchanged, unchanged, unchanged);
        }

        CaseEvidence withAdmissionOnlyUnchanged(boolean value) {
            return new CaseEvidence(
                    queryId, familyId, language, role, target, mechanismValid,
                    baselineAp, baselineNdcg, jointAp, jointNdcg,
                    value, survivalOnlyUnchanged, controlUnchanged);
        }
    }

    private record PressureSpec(
            String generator,
            int version,
            long seed,
            int lexicalPerTargetFamily,
            int pressureCount,
            String neutralAdmissionCue,
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

    private record ParsedQrels(
            Map<String, Map<String, Integer>> qrels,
            int count) {
    }

    private record ResourceBytes(String text, ResourceHash resourceHash) {
    }

    private record ManifestSnapshot(Manifest manifest, String sha256) {
    }
}
