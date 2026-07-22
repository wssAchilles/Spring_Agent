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

final class RagCandidate6DiagnosticSupport {

    static final String FREEZE_PROPERTY = "rag.eval.candidate6.freeze";
    static final String DIAGNOSTIC_PROPERTY = "rag.eval.candidate6.diagnostic";
    static final String HOLDOUT_DIRECTORY_PROPERTY = "rag.eval.candidate6.holdout-dir";

    static final long SELECTION_KB_ID = 9_980_000L;
    static final long SELECTION_SEGMENT_ID_MIN = 9_980_001L;
    static final long SELECTION_SEGMENT_ID_MAX = 9_984_999L;
    static final long SELECTION_DOCUMENT_ID_MIN = 9_985_000L;
    static final long SELECTION_DOCUMENT_ID_MAX = 9_989_999L;

    static final long HOLDOUT_KB_ID = 9_990_000L;
    static final long HOLDOUT_SEGMENT_ID_MIN = 9_990_001L;
    static final long HOLDOUT_SEGMENT_ID_MAX = 9_994_999L;
    static final long HOLDOUT_DOCUMENT_ID_MIN = 9_995_000L;
    static final long HOLDOUT_DOCUMENT_ID_MAX = 9_999_999L;

    static final int BUSINESS_SQL_LIMIT = 500;
    static final int BUSINESS_JAVA_LIMIT = 50;
    static final String PROCEED_DECISION =
            "PROCEED_TO_IDENTIFIER_EVIDENCE_PRIORITY_RED";
    static final String STOP_DECISION =
            "STOP_IDENTIFIER_EVIDENCE_PRIORITY_UNSUPPORTED";

    private static final String GENERATOR = "candidate6-static-fixture-v1";
    private static final int GENERATOR_VERSION = 1;
    private static final long SELECTION_SEED = 20260717L;
    private static final long HOLDOUT_SEED = 20260718L;
    private static final int SELECTION_QUERY_COUNT = 28;
    private static final int HOLDOUT_QUERY_COUNT = 56;
    private static final int SELECTION_SEGMENT_COUNT = 604;
    private static final int HOLDOUT_SEGMENT_COUNT = 952;
    private static final int SELECTION_QREL_COUNT = 52;
    private static final int HOLDOUT_QREL_COUNT = 104;
    private static final int SELECTION_TARGET_FAMILIES = 8;
    private static final int HOLDOUT_TARGET_FAMILIES = 16;
    private static final int SELECTION_NO_ID_FAMILIES = 2;
    private static final int HOLDOUT_NO_ID_FAMILIES = 4;
    private static final int SELECTION_NO_EXACT_FAMILIES = 2;
    private static final int HOLDOUT_NO_EXACT_FAMILIES = 4;
    private static final int SELECTION_SAFETY_FAMILIES = 2;
    private static final int HOLDOUT_SAFETY_FAMILIES = 4;
    private static final int SELECTION_PRESSURE_COUNT = 512;
    private static final int HOLDOUT_PRESSURE_COUNT = 768;
    private static final int LEXICAL_PER_TARGET = 8;
    private static final Set<String> IDENTIFIER_SHAPES = Set.of(
            "numeric-token", "doc-prefix", "zero-padded", "han-adjacent");
    private static final Set<String> FORBIDDEN_ARTIFACT_FIELDS = Set.of(
            "query", "retrievalquery", "identifier", "documentname", "content",
            "reference", "referenceanswer", "referenceclaims", "qrels", "grade",
            "sqlparameters", "sqlparams", "exceptionmessage", "errormessage");
    private static final String OUTER_ORDER =
            "ORDER BY trgm_score DESC, document_id ASC, position ASC NULLS LAST";
    private static final String IDENTIFIER_PREDICATE =
            "document_name ~ ('(^|[^[:alnum:]])' || ? || '([^[:alnum:]]|$)')";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ASCII_ALPHANUMERIC = Pattern.compile("[A-Za-z0-9]");
    private static final Object LEDGER_LOCK = new Object();

    private RagCandidate6DiagnosticSupport() {
    }

    static RuntimePaths paths(Path ragEvalRuntime) {
        Path runtime = Objects.requireNonNull(ragEvalRuntime, "ragEvalRuntime")
                .toAbsolutePath().normalize();
        Path freeze = runtime.resolve("candidate6-freeze");
        return new RuntimePaths(
                freeze,
                freeze.resolve("selection-manifest.json"),
                freeze.resolve("holdout-manifest.json"),
                freeze.resolve("selection-ledger.json"),
                freeze.resolve("review-ledger.json"),
                runtime.resolve("candidate6-calibration-diagnostic.json"));
    }

    static int enabledDiagnosticCount(
            boolean identifierDiagnostic,
            boolean candidate2Diagnostic,
            boolean candidate3Diagnostic,
            boolean candidate4Diagnostic,
            boolean candidate5Diagnostic,
            boolean candidate6Diagnostic) {
        return (identifierDiagnostic ? 1 : 0)
                + (candidate2Diagnostic ? 1 : 0)
                + (candidate3Diagnostic ? 1 : 0)
                + (candidate4Diagnostic ? 1 : 0)
                + (candidate5Diagnostic ? 1 : 0)
                + (candidate6Diagnostic ? 1 : 0);
    }

    static void requireSelectionJobProperties() {
        if (System.getProperty(HOLDOUT_DIRECTORY_PROPERTY) != null) {
            throw new IllegalStateException("CANDIDATE6_HOLDOUT_ACCESS_FORBIDDEN");
        }
    }

    static void requireSelectionRunAvailable(RuntimePaths paths) {
        requireFixedRuntimePaths(paths);
        if (Files.exists(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE6_DIAGNOSTIC_ALREADY_EXISTS");
        }
        if (!Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        JSONObject ledger;
        try {
            ledger = readJson(paths.ledger(), "CANDIDATE6_LEDGER_INVALID");
        } catch (IllegalStateException failure) {
            throw new IllegalStateException("INVALID_INCOMPLETE_PRIOR_RUN", failure);
        }
        if ("RUNNING".equals(ledger.getString("status"))) {
            throw new IllegalStateException("INVALID_INCOMPLETE_PRIOR_RUN");
        }
        if ("COMPLETED".equals(ledger.getString("status"))) {
            throw new IllegalStateException("CANDIDATE6_SELECTION_ALREADY_COMPLETED");
        }
        throw new IllegalStateException("CANDIDATE6_LEDGER_INVALID");
    }

    static FrozenManifests freezeDatasets(
            Path runtime,
            Path selectionDirectory,
            Path holdoutDirectory) {
        RuntimePaths paths = paths(runtime);
        if (Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE6_SELECTION_ALREADY_STARTED");
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
                throw new IllegalStateException("CANDIDATE6_FREEZE_INCOMPLETE");
            }
            requireBytes(paths.selectionManifest(), selectionBytes,
                    "CANDIDATE6_FREEZE_ALREADY_FROZEN");
            requireBytes(paths.holdoutManifest(), holdoutBytes,
                    "CANDIDATE6_FREEZE_ALREADY_FROZEN");
            return loadFrozenManifests(paths);
        }
        atomicCreate(paths.selectionManifest(), selectionBytes);
        atomicCreate(paths.holdoutManifest(), holdoutBytes);
        return loadFrozenManifests(paths);
    }

    static void freezeFormalDatasets(Path runtime, Path holdoutDirectory) {
        Path tests = testsDirectory();
        Path expectedHoldout = tests.resolve("candidate6-holdout")
                .toAbsolutePath().normalize();
        Path actualHoldout = holdoutDirectory.toAbsolutePath().normalize();
        if (!expectedHoldout.equals(actualHoldout)) {
            throw new IllegalStateException("CANDIDATE6_HOLDOUT_DIRECTORY_MISMATCH");
        }
        freezeDatasets(
                runtime,
                tests.resolve("src/test/resources/rag-eval/candidate6-selection"),
                actualHoldout);
    }

    static FrozenManifests loadFrozenManifests(RuntimePaths paths) {
        requireFixedRuntimePaths(paths);
        if (Files.isSymbolicLink(paths.freezeDirectory())
                || !Files.isDirectory(paths.freezeDirectory(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE6_FREEZE_DIRECTORY_INVALID");
        }
        ManifestSnapshot selection = readManifest(
                paths.selectionManifest(), "candidate6-selection", "FROZEN",
                SELECTION_SEED, SELECTION_QUERY_COUNT, SELECTION_SEGMENT_COUNT,
                SELECTION_QREL_COUNT);
        ManifestSnapshot holdout = readManifest(
                paths.holdoutManifest(), "candidate6-holdout", "FROZEN_NOT_BLIND",
                HOLDOUT_SEED, HOLDOUT_QUERY_COUNT, HOLDOUT_SEGMENT_COUNT,
                HOLDOUT_QREL_COUNT);
        return new FrozenManifests(
                selection.manifest(), selection.sha256(),
                holdout.manifest(), holdout.sha256());
    }

    static FrozenDataset loadFormalFrozenSelection(Path runtime) {
        requireSelectionJobProperties();
        RuntimePaths paths = paths(runtime);
        FrozenManifests manifests = loadFrozenManifests(paths);
        DatasetFiles current = readDatasetFiles(
                testsDirectory().resolve(
                        "src/test/resources/rag-eval/candidate6-selection"), false);
        Manifest currentManifest = buildManifest(current, false);
        if (!currentManifest.equals(manifests.selection())) {
            throw new IllegalStateException(
                    "CANDIDATE6_SELECTION_RESOURCE_HASH_MISMATCH");
        }
        return new FrozenDataset(
                current.dataset(),
                manifests,
                currentManifest.datasetHash(),
                List.of(
                        "selection-manifest.json",
                        "holdout-manifest.json",
                        "candidate6-selection/corpus.jsonl",
                        "candidate6-selection/queries.jsonl",
                        "candidate6-selection/qrels.tsv",
                        "candidate6-selection/pressure.json"));
    }

    static RunHandle beginSelectionRun(
            RuntimePaths paths, FrozenManifests manifests) {
        synchronized (LEDGER_LOCK) {
            requireSelectionRunAvailable(paths);
            if (!manifests.equals(loadFrozenManifests(paths))) {
                throw new IllegalStateException("CANDIDATE6_MANIFEST_CHANGED");
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
                    ShadowContractSupport.sha256(bytes));
        }
    }

    static void writeDiagnosticAndComplete(
            RuntimePaths paths,
            RunHandle handle,
            Map<String, ?> artifact) {
        synchronized (LEDGER_LOCK) {
            requireFixedRuntimePaths(paths);
            JSONObject ledger = readJson(paths.ledger(), "CANDIDATE6_LEDGER_INVALID");
            if (!"RUNNING".equals(ledger.getString("status"))
                    || !handle.datasetHash().equals(ledger.getString("datasetHash"))
                    || !handle.selectionManifestSha256().equals(
                    ledger.getString("selectionManifestSha256"))
                    || !handle.holdoutManifestSha256().equals(
                    ledger.getString("holdoutManifestSha256"))
                    || !handle.runningLedgerSha256().equals(
                    ShadowContractSupport.sha256(paths.ledger()))) {
                throw new IllegalStateException("CANDIDATE6_LEDGER_INVALID");
            }
            validateArtifact(artifact, handle);
            byte[] artifactBytes = canonicalJsonBytes(artifact);
            atomicCreate(paths.diagnostic(), artifactBytes);
            String artifactSha256 = ShadowContractSupport.sha256(paths.diagnostic());
            if (!artifactSha256.equals(ShadowContractSupport.sha256(artifactBytes))) {
                throw new IllegalStateException("CANDIDATE6_ARTIFACT_HASH_MISMATCH");
            }
            Map<String, Object> completed = new LinkedHashMap<>();
            completed.put("status", "COMPLETED");
            completed.put("datasetHash", handle.datasetHash());
            completed.put("selectionManifestSha256", handle.selectionManifestSha256());
            completed.put("holdoutManifestSha256", handle.holdoutManifestSha256());
            completed.put("artifactSha256", artifactSha256);
            completed.put("completedAt", Instant.now().toString());
            atomicReplace(paths.ledger(), canonicalJsonBytes(completed));
            JSONObject verified = readJson(paths.ledger(), "CANDIDATE6_LEDGER_INVALID");
            if (!"COMPLETED".equals(verified.getString("status"))
                    || !artifactSha256.equals(verified.getString("artifactSha256"))) {
                throw new IllegalStateException("CANDIDATE6_LEDGER_INVALID");
            }
        }
    }

    static void writePreRunInvalidDiagnostic(
            RuntimePaths paths,
            Map<String, ?> config,
            String errorCode) {
        synchronized (LEDGER_LOCK) {
            requireFixedRuntimePaths(paths);
            if (Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("CANDIDATE6_SELECTION_ALREADY_STARTED");
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
            String decision,
            Map<String, ?> summary,
            List<? extends Map<String, ?>> cases) {
        if (!Set.of(PROCEED_DECISION, STOP_DECISION).contains(decision)) {
            throw new IllegalArgumentException("CANDIDATE6_DECISION_INVALID");
        }
        Map<String, Object> artifact = baseArtifact(frozen, config);
        artifact.put("status", "VALID");
        artifact.put("decision", decision);
        artifact.put("errorCode", null);
        artifact.put("summary", new LinkedHashMap<>(summary));
        artifact.put("cases", cases.stream()
                .map(LinkedHashMap<String, Object>::new)
                .toList());
        rejectForbiddenFields(artifact);
        return artifact;
    }

    static String decide(List<CaseEvidence> cases) {
        if (cases.size() != SELECTION_QUERY_COUNT) {
            return STOP_DECISION;
        }
        List<CaseEvidence> englishTargets = cases.stream()
                .filter(CaseEvidence::target)
                .filter(item -> "en".equals(item.language()))
                .toList();
        Map<RootCause, Long> modifiableCounts = englishTargets.stream()
                .collect(Collectors.groupingBy(
                        CaseEvidence::classification,
                        LinkedHashMap::new,
                        Collectors.counting()));
        List<Map.Entry<RootCause, Long>> qualifyingRoots = modifiableCounts.entrySet()
                .stream()
                .filter(entry -> entry.getKey().modifiable())
                .filter(entry -> entry.getValue() >= 4L)
                .toList();
        if (qualifyingRoots.size() != 1) {
            return STOP_DECISION;
        }
        RootCause selectedRoot = qualifyingRoots.get(0).getKey();
        List<CaseEvidence> qualifying = englishTargets.stream()
                .filter(item -> item.classification() == selectedRoot)
                .toList();
        Set<String> shapes = qualifying.stream()
                .map(CaseEvidence::identifierShape)
                .collect(Collectors.toSet());
        if (!shapes.equals(IDENTIFIER_SHAPES)
                || englishTargets.stream().anyMatch(item ->
                item.classification().modifiable()
                        && item.classification() != selectedRoot)
                || qualifying.stream().anyMatch(item ->
                !(item.counterfactualAp() > item.baselineAp())
                        || !(item.counterfactualNdcg() > item.baselineNdcg()))) {
            return STOP_DECISION;
        }
        Map<String, CaseEvidence> byQuery = cases.stream().collect(Collectors.toMap(
                CaseEvidence::queryId, item -> item));
        for (CaseEvidence english : qualifying) {
            CaseEvidence chinese = byQuery.get(
                    english.queryId().substring(0, english.queryId().length() - 2) + "zh");
            if (chinese == null
                    || chinese.classification() != RootCause.BASELINE_ALREADY_PRESENT) {
                return STOP_DECISION;
            }
        }
        if (cases.stream().filter(CaseEvidence::noExactMatchControl)
                .anyMatch(item -> !item.behaviorUnchanged())
                || cases.stream().filter(CaseEvidence::safetyControl)
                .anyMatch(item -> !item.safetyValid())
                || cases.stream().filter(CaseEvidence::target)
                .anyMatch(item -> item.counterfactualAp() < item.baselineAp()
                        || item.counterfactualNdcg() < item.baselineNdcg())
                || cases.stream().anyMatch(item ->
                item.diagnosticTotalDbCalls()
                        != item.diagnosticSqlCalls() + item.diagnosticContextDbCalls())
                || cases.stream().anyMatch(item ->
                item.addedEmbeddingCalls() != 0L
                        || item.addedVectorCalls() != 0L
                        || item.addedMetadataCalls() != 0L
                        || item.addedGraphCalls() != 0L
                        || item.addedNetworkCalls() != 0L)) {
            return STOP_DECISION;
        }
        double baselineAp = qualifying.stream().mapToDouble(
                CaseEvidence::baselineAp).average().orElse(0.0D);
        double counterfactualAp = qualifying.stream().mapToDouble(
                CaseEvidence::counterfactualAp).average().orElse(0.0D);
        double baselineNdcg = qualifying.stream().mapToDouble(
                CaseEvidence::baselineNdcg).average().orElse(0.0D);
        double counterfactualNdcg = qualifying.stream().mapToDouble(
                CaseEvidence::counterfactualNdcg).average().orElse(0.0D);
        return counterfactualAp > baselineAp && counterfactualNdcg > baselineNdcg
                ? PROCEED_DECISION : STOP_DECISION;
    }

    static Map<String, Object> diagnosticSummary(List<CaseEvidence> cases) {
        Map<String, Long> counts = new TreeMap<>();
        for (RootCause value : RootCause.values()) {
            counts.put(value.name(), cases.stream()
                    .filter(item -> item.classification() == value).count());
        }
        Set<String> modes = cases.stream().filter(CaseEvidence::target)
                .filter(item -> "en".equals(item.language()))
                .map(CaseEvidence::counterfactualMode)
                .filter(mode -> mode != CounterfactualMode.NONE)
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseCount", cases.size());
        summary.put("classificationCounts", counts);
        summary.put("counterfactualModes", List.copyOf(modes));
        summary.put("baselineAP@10", cases.stream().filter(CaseEvidence::target)
                .mapToDouble(CaseEvidence::baselineAp).average().orElse(0.0D));
        summary.put("counterfactualAP@10", cases.stream().filter(CaseEvidence::target)
                .mapToDouble(CaseEvidence::counterfactualAp).average().orElse(0.0D));
        summary.put("baselineNDCG@10", cases.stream().filter(CaseEvidence::target)
                .mapToDouble(CaseEvidence::baselineNdcg).average().orElse(0.0D));
        summary.put("counterfactualNDCG@10", cases.stream().filter(CaseEvidence::target)
                .mapToDouble(CaseEvidence::counterfactualNdcg).average().orElse(0.0D));
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
            throw new IllegalStateException("CANDIDATE6_IDENTIFIER_EXTRACTOR_UNAVAILABLE", failure);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException(
                    "CANDIDATE6_IDENTIFIER_EXTRACTOR_FAILED", failure.getCause());
        }
    }

    static boolean boundaryMatches(String documentName, String identifier) {
        if (documentName == null || identifier == null || identifier.isEmpty()) {
            return false;
        }
        return Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(identifier)
                + "(?![A-Za-z0-9])").matcher(documentName).find();
    }

    static boolean matchesAnyIdentifier(String documentName, List<String> identifiers) {
        return identifiers.stream().anyMatch(identifier ->
                boundaryMatches(documentName, identifier));
    }

    static ExactFirstSql exactFirstSql(
            String originalSql,
            Object[] originalParameters,
            List<String> identifiers) {
        Objects.requireNonNull(originalSql, "originalSql");
        Objects.requireNonNull(originalParameters, "originalParameters");
        List<String> copiedIdentifiers = List.copyOf(identifiers);
        if (copiedIdentifiers.isEmpty()
                || originalParameters.length == 0
                || !(originalParameters[originalParameters.length - 1] instanceof Number limit)
                || limit.intValue() != BUSINESS_SQL_LIMIT) {
            throw new IllegalStateException("CANDIDATE6_SQL_SHAPE_INVALID");
        }
        int orderOffset = originalSql.lastIndexOf(OUTER_ORDER);
        if (orderOffset < 0) {
            throw new IllegalStateException("CANDIDATE6_SQL_SHAPE_INVALID");
        }
        String exactOrder = copiedIdentifiers.stream()
                .map(ignored -> IDENTIFIER_PREDICATE)
                .collect(Collectors.joining(" OR "));
        String prioritySql = originalSql.substring(0, orderOffset)
                + "ORDER BY (" + exactOrder + ") DESC, "
                + "trgm_score DESC, document_id ASC, position ASC NULLS LAST"
                + originalSql.substring(orderOffset + OUTER_ORDER.length());
        List<Object> priorityParameters = new ArrayList<>(
                java.util.Arrays.asList(originalParameters)
                        .subList(0, originalParameters.length - 1));
        priorityParameters.addAll(copiedIdentifiers);
        priorityParameters.add(BUSINESS_SQL_LIMIT);
        return new ExactFirstSql(prioritySql, List.copyOf(priorityParameters));
    }

    static List<RetrievalSnapshot> snapshotResults(
            List<RetrievalResult> results, List<String> identifiers) {
        List<RetrievalSnapshot> snapshots = new ArrayList<>();
        if (results == null) {
            return List.of();
        }
        int ordinal = 0;
        for (RetrievalResult result : results) {
            if (result == null) {
                continue;
            }
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
                    matchesAnyIdentifier(result.getDocumentName(), identifiers)));
        }
        return List.copyOf(snapshots);
    }

    static List<RetrievalSnapshot> stableExactFirst(
            List<RetrievalSnapshot> values) {
        List<RetrievalSnapshot> exact = values.stream()
                .filter(RetrievalSnapshot::exactMatch).toList();
        List<RetrievalSnapshot> other = values.stream()
                .filter(value -> !value.exactMatch()).toList();
        List<RetrievalSnapshot> reordered = new ArrayList<>(values.size());
        reordered.addAll(exact);
        reordered.addAll(other);
        return List.copyOf(reordered);
    }

    static List<RetrievalSnapshot> originalJavaTop50(
            List<RetrievalSnapshot> rows) {
        return rows.stream()
                .sorted(Comparator.comparingDouble(RetrievalSnapshot::score).reversed())
                .limit(BUSINESS_JAVA_LIMIT)
                .toList();
    }

    static List<RetrievalSnapshot> exactFirstJavaTop50(
            List<RetrievalSnapshot> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(RetrievalSnapshot::exactMatch).reversed()
                        .thenComparing(
                                Comparator.comparingDouble(
                                        RetrievalSnapshot::score).reversed()))
                .limit(BUSINESS_JAVA_LIMIT)
                .toList();
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
        if (!handle.datasetHash().equals(artifact.get("datasetHash"))
                || !handle.selectionManifestSha256().equals(
                artifact.get("selectionManifestHash"))
                || !handle.holdoutManifestSha256().equals(
                artifact.get("holdoutManifestHash"))) {
            throw new IllegalStateException("CANDIDATE6_ARTIFACT_MANIFEST_INVALID");
        }
        String status = Objects.toString(artifact.get("status"), null);
        String decision = Objects.toString(artifact.get("decision"), null);
        Object errorCode = artifact.get("errorCode");
        if ("INVALID".equals(status)) {
            if (decision != null || !(errorCode instanceof String code) || code.isBlank()
                    || artifact.containsKey("cases") || artifact.containsKey("summary")) {
                throw new IllegalStateException("CANDIDATE6_ARTIFACT_INVALID");
            }
            return;
        }
        if (!"VALID".equals(status)
                || !Set.of(PROCEED_DECISION, STOP_DECISION).contains(decision)
                || errorCode != null
                || !(artifact.get("cases") instanceof List<?> cases)
                || cases.size() != SELECTION_QUERY_COUNT
                || !(artifact.get("summary") instanceof Map<?, ?>)) {
            throw new IllegalStateException("CANDIDATE6_ARTIFACT_INVALID");
        }
    }

    private static void rejectForbiddenFields(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = Objects.toString(entry.getKey(), "")
                        .replace("_", "").replace("-", "")
                        .toLowerCase(Locale.ROOT);
                if (FORBIDDEN_ARTIFACT_FIELDS.contains(key)) {
                    throw new IllegalStateException(
                            "CANDIDATE6_FORBIDDEN_ARTIFACT_FIELD");
                }
                rejectForbiddenFields(entry.getValue());
            }
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(RagCandidate6DiagnosticSupport::rejectForbiddenFields);
        }
    }

    private static DatasetFiles readDatasetFiles(Path directory, boolean holdout) {
        Path normalized = requireDirectory(directory,
                "CANDIDATE6_DATASET_DIRECTORY_INVALID");
        Path corpusPath = requireRegular(normalized.resolve("corpus.jsonl"),
                "CANDIDATE6_DATASET_RESOURCE_INVALID");
        Path queriesPath = requireRegular(normalized.resolve("queries.jsonl"),
                "CANDIDATE6_DATASET_RESOURCE_INVALID");
        Path qrelsPath = requireRegular(normalized.resolve("qrels.tsv"),
                "CANDIDATE6_DATASET_RESOURCE_INVALID");
        Path pressurePath = requireRegular(normalized.resolve("pressure.json"),
                "CANDIDATE6_DATASET_RESOURCE_INVALID");
        try {
            Map<String, RagEvaluationDataset.CorpusSegment> core = parseCorpus(
                    Files.readString(corpusPath, StandardCharsets.UTF_8));
            List<RagEvaluationDataset.QueryCase> queries = parseQueries(
                    Files.readString(queriesPath, StandardCharsets.UTF_8));
            ParsedQrels qrels = parseQrels(
                    Files.readString(qrelsPath, StandardCharsets.UTF_8));
            PressureSpec pressure = parsePressure(
                    Files.readString(pressurePath, StandardCharsets.UTF_8));
            Map<String, RagEvaluationDataset.CorpusSegment> expanded =
                    expandCorpus(core, queries, pressure, holdout);
            RagEvaluationDataset dataset = new RagEvaluationDataset(
                    expanded, queries, qrels.qrels());
            RagEvaluationDatasetLoader.validate(dataset);
            validateShape(dataset, qrels.count(), pressure, holdout);
            Map<String, ResourceHash> resources = new LinkedHashMap<>();
            resources.put("corpus", resourceHash(corpusPath));
            resources.put("queries", resourceHash(queriesPath));
            resources.put("qrels", resourceHash(qrelsPath));
            resources.put("pressure", resourceHash(pressurePath));
            return new DatasetFiles(
                    dataset, qrels.count(), pressure, Map.copyOf(resources));
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "CANDIDATE6_DATASET_RESOURCE_READ_FAILED", failure);
        }
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
                throw new IllegalArgumentException("CANDIDATE6_DUPLICATE_SEGMENT");
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
                throw new IllegalArgumentException("CANDIDATE6_DUPLICATE_QUERY");
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
                throw new IllegalArgumentException("CANDIDATE6_QREL_INVALID");
            }
            Map<String, Integer> queryQrels = qrels.computeIfAbsent(
                    columns[0], ignored -> new LinkedHashMap<>());
            if (queryQrels.putIfAbsent(
                    columns[1], Integer.parseInt(columns[2])) != null) {
                throw new IllegalArgumentException("CANDIDATE6_DUPLICATE_QREL");
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
                || !"candidate6-pressure-v1".equals(json.getString("generator"))
                || json.getIntValue("version") != 1) {
            throw new IllegalArgumentException("CANDIDATE6_PRESSURE_SPEC_INVALID");
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
                .filter(query -> query.familyId().matches("c6[sh]-t\\d+"))
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
                            "CANDIDATE6_IDENTIFIER_SHAPE_INVALID"));
            for (int index = 1; index <= pressure.lexicalPerTargetFamily(); index++) {
                putGenerated(expanded, segmentId++, documentId++,
                        pressure.neutralAdmissionCue()
                                + " Routine lexical admission note " + index + ".",
                        Map.of(
                                "candidate6Role", "lexical-distractor",
                                "familyId", familyId,
                                "identifierShape", shape,
                                "documentName", (holdout ? "holdout" : "selection")
                                        + "-lexical-" + familyId + "-" + index));
            }
        }
        if (segmentId != pressure.pressureSegmentIdStart()
                || documentId != pressure.pressureDocumentIdStart()) {
            throw new IllegalArgumentException("CANDIDATE6_PRESSURE_ID_RULE_INVALID");
        }
        for (int index = 1; index <= pressure.pressureCount(); index++) {
            putGenerated(expanded, segmentId++, documentId++,
                    pressure.neutralAdmissionCue() + " Pressure admission record.",
                    Map.of(
                            "candidate6Role", "pressure-distractor",
                            "familyId", "candidate6-pressure",
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
            throw new IllegalArgumentException("CANDIDATE6_GENERATED_ID_COLLISION");
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
        int safetyFamilies = holdout ? HOLDOUT_SAFETY_FAMILIES : SELECTION_SAFETY_FAMILIES;
        int pressureCount = holdout ? HOLDOUT_PRESSURE_COUNT : SELECTION_PRESSURE_COUNT;
        long segmentMin = holdout ? HOLDOUT_SEGMENT_ID_MIN : SELECTION_SEGMENT_ID_MIN;
        long segmentMax = holdout ? HOLDOUT_SEGMENT_ID_MAX : SELECTION_SEGMENT_ID_MAX;
        long documentMin = holdout ? HOLDOUT_DOCUMENT_ID_MIN : SELECTION_DOCUMENT_ID_MIN;
        long documentMax = holdout ? HOLDOUT_DOCUMENT_ID_MAX : SELECTION_DOCUMENT_ID_MAX;
        String prefix = holdout ? "c6h-" : "c6s-";
        String split = holdout ? "holdout" : "selection";
        if (dataset.queries().size() != expectedQueries
                || dataset.corpusById().size() != expectedSegments
                || qrelCount != expectedQrels
                || pressure.pressureCount() != pressureCount
                || pressure.lexicalPerTargetFamily() != LEXICAL_PER_TARGET
                || pressure.seed() != (holdout ? HOLDOUT_SEED : SELECTION_SEED)) {
            throw new IllegalArgumentException("CANDIDATE6_DATASET_COUNT_MISMATCH");
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
        long safetyCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "s")).count();
        if (targetCount != targetFamilies || noIdCount != noIdFamilies
                || noExactCount != noExactFamilies || safetyCount != safetyFamilies
                || families.size() != targetFamilies + noIdFamilies
                + noExactFamilies + safetyFamilies) {
            throw new IllegalArgumentException("CANDIDATE6_FAMILY_COUNT_MISMATCH");
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
                        "CANDIDATE6_BILINGUAL_FAMILY_INVALID");
            }
            boolean target = entry.getKey().startsWith(prefix + "t");
            boolean noId = entry.getKey().startsWith(prefix + "n");
            boolean noExact = entry.getKey().startsWith(prefix + "x");
            boolean safety = entry.getKey().startsWith(prefix + "s");
            int expectedRelevant = target ? 3 : noId ? 1 : 0;
            Set<String> shared = null;
            String shape = null;
            for (RagEvaluationDataset.QueryCase query : family) {
                Set<String> qrels = dataset.qrelsFor(query.id()).keySet();
                if (qrels.size() != expectedRelevant
                        || query.answerable() != (expectedRelevant > 0)) {
                    throw new IllegalArgumentException("CANDIDATE6_QREL_SHAPE_INVALID");
                }
                if (shared == null) {
                    shared = Set.copyOf(qrels);
                } else if (!shared.equals(qrels)) {
                    throw new IllegalArgumentException(
                            "CANDIDATE6_FAMILY_QREL_SHARING_INVALID");
                }
                List<String> identifiers = identifierTerms(query.retrievalQuery());
                if (target || safety || entry.getKey().startsWith(prefix + "x")) {
                    if (identifiers.size() != 1
                            || !identifiers.equals(identifierTerms(query.query()))) {
                        throw new IllegalArgumentException(
                                "CANDIDATE6_IDENTIFIER_FIXTURE_INVALID");
                    }
                } else if (!identifiers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "CANDIDATE6_NO_IDENTIFIER_CONTROL_INVALID");
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
                                    "CANDIDATE6_IDENTIFIER_SHAPE_INVALID"));
                    if (shape == null) {
                        shape = currentShape;
                    } else if (!shape.equals(currentShape)) {
                        throw new IllegalArgumentException(
                                "CANDIDATE6_IDENTIFIER_SHAPE_INVALID");
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
                            exactSegments.get(0).metadata().get("candidate6Role"), ""))) {
                        throw new IllegalArgumentException(
                                "CANDIDATE6_EXACT_EVIDENCE_INVALID");
                    }
                } else if (noExact && !exactSegments.isEmpty()) {
                    throw new IllegalArgumentException(
                            "CANDIDATE6_NO_EXACT_CONTROL_INVALID");
                } else if (safety && (exactSegments.size() != 1
                        || !entry.getKey().equals(Objects.toString(
                        exactSegments.get(0).metadata().get("familyId"), ""))
                        || !"irrelevant-exact-evidence".equals(Objects.toString(
                        exactSegments.get(0).metadata().get("candidate6Role"), "")))) {
                    throw new IllegalArgumentException(
                            "CANDIDATE6_SAFETY_CONTROL_INVALID");
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
                    "CANDIDATE6_IDENTIFIER_SHAPE_COUNT_INVALID");
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
                throw new IllegalArgumentException("CANDIDATE6_ID_ISOLATION_INVALID");
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
            throw new IllegalArgumentException("CANDIDATE6_DATASET_ISOLATION_INVALID");
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
        counts.put("qrelCount", files.qrelCount());
        Map<String, Object> structure = structure(files, holdout);
        long seed = holdout ? HOLDOUT_SEED : SELECTION_SEED;
        String datasetHash = manifestDatasetHash(
                GENERATOR, GENERATOR_VERSION, seed,
                files.resources(), counts, structure);
        return new Manifest(
                holdout ? "candidate6-holdout" : "candidate6-selection",
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
        value.put("irrelevantExactMatchSafetyFamilyCount",
                holdout ? HOLDOUT_SAFETY_FAMILIES : SELECTION_SAFETY_FAMILIES);
        value.put("bilingualQueriesPerFamily", 2);
        value.put("relevantSegmentsPerTargetFamily", 3);
        value.put("relevantSegmentsPerNoIdentifierFamily", 1);
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
        value.put("idMappingRule", Map.of(
                "knowledgeBaseId", Math.toIntExact(
                        holdout ? HOLDOUT_KB_ID : SELECTION_KB_ID),
                "segmentIdMin", Math.toIntExact(
                        holdout ? HOLDOUT_SEGMENT_ID_MIN : SELECTION_SEGMENT_ID_MIN),
                "documentIdMin", Math.toIntExact(
                        holdout ? HOLDOUT_DOCUMENT_ID_MIN : SELECTION_DOCUMENT_ID_MIN),
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
            int expectedQrels) {
        JSONObject json = readJson(path, "CANDIDATE6_MANIFEST_INVALID");
        if (!expectedDataset.equals(json.getString("dataset"))
                || !expectedFreezeStatus.equals(json.getString("freezeStatus"))
                || !GENERATOR.equals(json.getString("generator"))
                || GENERATOR_VERSION != json.getIntValue("version")
                || expectedSeed != json.getLongValue("seed")
                || json.getIntValue("manifestVersion") != 1) {
            throw new IllegalStateException("CANDIDATE6_MANIFEST_INVALID");
        }
        JSONObject resourceJson = json.getJSONObject("resources");
        JSONObject countsJson = json.getJSONObject("counts");
        JSONObject structureJson = json.getJSONObject("structure");
        if (resourceJson == null || countsJson == null || structureJson == null
                || countsJson.getIntValue("queryCount") != expectedQueries
                || countsJson.getIntValue("segmentCount") != expectedSegments
                || countsJson.getIntValue("documentCount") != expectedSegments
                || countsJson.getIntValue("qrelCount") != expectedQrels) {
            throw new IllegalStateException("CANDIDATE6_MANIFEST_INVALID");
        }
        Map<String, ResourceHash> resources = new LinkedHashMap<>();
        for (String name : List.of("corpus", "queries", "qrels", "pressure")) {
            JSONObject resource = resourceJson.getJSONObject(name);
            if (resource == null || !validSha(resource.getString("sha256"))) {
                throw new IllegalStateException("CANDIDATE6_MANIFEST_INVALID");
            }
            resources.put(name, new ResourceHash(
                    resource.getString("file"), resource.getString("sha256")));
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : List.of(
                "familyCount", "queryCount", "documentCount",
                "segmentCount", "qrelCount")) {
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
            throw new IllegalStateException("CANDIDATE6_MANIFEST_INVALID");
        }
        Manifest manifest = new Manifest(
                expectedDataset, expectedFreezeStatus, GENERATOR,
                GENERATOR_VERSION, expectedSeed, Map.copyOf(resources),
                Map.copyOf(counts), Map.copyOf(structure), datasetHash);
        return new ManifestSnapshot(manifest, ShadowContractSupport.sha256(path));
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

    private static ResourceHash resourceHash(Path path) {
        return new ResourceHash(path.getFileName().toString(),
                ShadowContractSupport.sha256(path));
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
        throw new IllegalStateException("CANDIDATE6_TESTS_DIRECTORY_NOT_FOUND");
    }

    private static void requireFixedRuntimePaths(RuntimePaths paths) {
        RuntimePaths expected = paths(paths.freezeDirectory().getParent());
        if (!expected.equals(paths)) {
            throw new IllegalArgumentException("CANDIDATE6_RUNTIME_PATH_INVALID");
        }
    }

    private static Path requireDirectory(Path path, String errorCode) {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }

    private static Path requireRegular(Path path, String errorCode) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(path.getParent().toAbsolutePath().normalize())
                || Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
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
                throw new IllegalStateException("CANDIDATE6_ATOMIC_PARENT_INVALID");
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
            throw new IllegalStateException("CANDIDATE6_ATOMIC_MOVE_REQUIRED", failure);
        } catch (FileAlreadyExistsException failure) {
            throw new IllegalStateException("CANDIDATE6_ARTIFACT_ALREADY_EXISTS", failure);
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE6_ATOMIC_WRITE_FAILED", failure);
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
            throw new IllegalArgumentException("CANDIDATE6_REQUIRED_FIELD_MISSING");
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
            List<String> auditedReads) {
    }

    record RunHandle(
            String datasetHash,
            String selectionManifestSha256,
            String holdoutManifestSha256,
            String runningLedgerSha256) {
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
                    .build();
        }

        Map<String, Object> sanitized(int rank) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("ordinal", ordinal);
            value.put("segmentId", segmentId);
            value.put("documentId", documentId);
            value.put("rank", rank);
            value.put("score", score);
            value.put("exactMatch", exactMatch);
            return value;
        }
    }

    record ExactFirstSql(String sql, List<Object> parameters) {
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
            String identifierShape,
            boolean target,
            boolean noExactMatchControl,
            boolean safetyControl,
            RootCause classification,
            CounterfactualMode counterfactualMode,
            double baselineAp,
            double baselineNdcg,
            double counterfactualAp,
            double counterfactualNdcg,
            boolean behaviorUnchanged,
            boolean safetyValid,
            long sutDbCalls,
            long diagnosticSqlCalls,
            long diagnosticContextDbCalls,
            long diagnosticTotalDbCalls,
            long addedEmbeddingCalls,
            long addedVectorCalls,
            long addedMetadataCalls,
            long addedGraphCalls,
            long addedNetworkCalls) {
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

    private record ManifestSnapshot(Manifest manifest, String sha256) {
    }
}
