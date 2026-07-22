package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.document.Document;
import tech.qiantong.qknow.module.kmc.service.rag.KeywordRetriever;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class RagCandidate5DiagnosticSupport {

    static final String FREEZE_PROPERTY = "rag.eval.candidate5.freeze";
    static final String DIAGNOSTIC_PROPERTY = "rag.eval.candidate5.diagnostic";
    static final String HOLDOUT_DIRECTORY_PROPERTY = "rag.eval.candidate5.holdout-dir";
    static final String SQL_BOUNDARY_PROPERTY = "rag.eval.candidate5.sql-boundary";
    static final String DOCUMENT_NAME_BOUNDARY_PREDICATE_SQL =
            "d.name ~ ('(^|[^[:alnum:]])' || ? || '([^[:alnum:]]|$)')";

    static final long SELECTION_KB_ID = 9_960_000L;
    static final long SELECTION_SEGMENT_ID_MIN = 9_960_001L;
    static final long SELECTION_SEGMENT_ID_MAX = 9_964_999L;
    static final long SELECTION_DOCUMENT_ID_MIN = 9_965_000L;
    static final long SELECTION_DOCUMENT_ID_MAX = 9_969_999L;

    static final long HOLDOUT_KB_ID = 9_970_000L;
    static final long HOLDOUT_SEGMENT_ID_MIN = 9_970_001L;
    static final long HOLDOUT_SEGMENT_ID_MAX = 9_974_999L;
    static final long HOLDOUT_DOCUMENT_ID_MIN = 9_975_000L;
    static final long HOLDOUT_DOCUMENT_ID_MAX = 9_979_999L;

    private static final String GENERATOR = "candidate5-static-fixture-v1";
    private static final int GENERATOR_VERSION = 1;
    private static final long GENERATOR_SEED = 20260716L;
    private static final int BASE_DISTRACTOR_COUNT = 64;
    private static final String MANIFEST_VERSION = "1";
    private static final String BASE_REMAP_POLICY =
            "source-segment-order-to-reserved-prefix-v1";
    private static final int SELECTION_CASE_COUNT = 20;
    private static final Set<String> IDENTIFIER_SHAPES = Set.of(
            "numeric-token", "doc-prefix", "han-zero-padded");
    private static final Set<String> MANIFEST_FIELDS = Set.of(
            "manifestVersion", "dataset", "freezeStatus", "generator", "version", "seed",
            "resources", "counts", "structure", "datasetHash");
    private static final Set<String> RESOURCE_FIELDS = Set.of("file", "sha256");
    private static final Set<String> RESOURCE_NAMES = Set.of(
            "corpus", "queries", "qrels", "baseDistractor");
    private static final Map<String, String> RESOURCE_FILES = Map.of(
            "corpus", "corpus.jsonl",
            "queries", "queries.jsonl",
            "qrels", "qrels.tsv",
            "baseDistractor", "corpus.jsonl");
    private static final Set<String> COUNT_FIELDS = Set.of(
            "familyCount", "queryCount", "documentCount", "segmentCount", "qrelCount");
    private static final Set<String> STRUCTURE_FIELDS = Set.of(
            "targetFamilyCount",
            "noIdentifierAnswerableFamilyCount",
            "identifierUnanswerableFamilyCount",
            "bilingualQueriesPerFamily",
            "relevantSegmentsPerTargetFamily",
            "relevantSegmentsPerNoIdentifierFamily",
            "targetEvidenceCount",
            "noIdentifierEvidenceCount",
            "lexicalDistractorCount",
            "baseDistractorCount",
            "bilingualFamilySharesRelevantSegments",
            "uniqueDocumentPerSegment",
            "identifierShapeFamilyCounts",
            "baseRemapRule",
            "mergedIdMappingHash");
    private static final Set<String> BASE_REMAP_FIELDS = Set.of(
            "policy", "count", "segmentIdStart", "documentIdStart");
    private static final Set<String> VALID_DECISIONS = Set.of(
            "PROCEED_TO_IDENTIFIER_RECALL_RED",
            "STOP_MULTILINGUAL_IDENTIFIER_UNSUPPORTED");
    private static final Set<String> BASE_ARTIFACT_FIELDS = Set.of(
            "datasetHash",
            "selectionManifestHash",
            "holdoutManifestHash",
            "holdoutFreezeStatus",
            "auditedReads",
            "config",
            "configHash",
            "status",
            "decision",
            "errorCode");
    private static final Set<String> VALID_ARTIFACT_FIELDS = joinedFields(
            BASE_ARTIFACT_FIELDS, Set.of("summary", "cases"));
    private static final Set<String> SUMMARY_FIELDS = Set.of(
            "caseCount",
            "targetCaseCount",
            "controlCaseCount",
            "classificationCounts",
            "qualifyingFamilyCount",
            "coveredIdentifierShapes");
    private static final Set<String> CASE_FIELDS = Set.of(
            "queryId",
            "familyId",
            "language",
            "split",
            "identifierShape",
            "target",
            "originalQuerySha256",
            "retrievalQuerySha256",
            "originalContainsIdentifier",
            "retrievalContainsIdentifier",
            "fullPathIdentifierPresence",
            "extractorContainsIdentifier",
            "extractedIdentifierCount",
            "extractedIdentifierHash",
            "searchTermCount",
            "searchTermsHash",
            "exactIdentifierTermEmitted",
            "exactIdentifierPredicate",
            "documentFieldsConsistent",
            "keywordReturnedAnchor",
            "finalReturnedAnchor",
            "behaviorUnchanged",
            "sqlShapeHash",
            "identifierPredicateCount",
            "classification",
            "paths",
            "fusedRanking",
            "filterRanking",
            "finalSources",
            "contextSha256",
            "contextEmpty",
            "callCounts",
            "actualRetrievalAP@10",
            "actualNDCG@10",
            "oracleRecoverableUpperBound");
    private static final Set<String> PATH_NAMES = Set.of(
            "keyword", "metadata", "vector", "graph");
    private static final Set<String> RANKED_SEGMENT_FIELDS = Set.of(
            "segmentId", "rank", "score");
    private static final Set<String> CALL_COUNT_FIELDS = Set.of(
            "dbCalls", "embeddingCalls", "networkCalls");
    private static final Set<String> ORACLE_SCORE_FIELDS = Set.of(
            "RetrievalAP@10", "nDCG@10");
    private static final Set<String> CONFIG_FIELDS = Set.of(
            "rrfK",
            "weakPathThreshold",
            "dynamicTopK",
            "colbert",
            "context",
            "routerEnabled",
            "queryEntityEnabled",
            "queryTransformEnabled",
            "cragEnabled",
            "cragWebEnabled",
            "graphEnabled",
            "rerankerMode",
            "rerankerProviders",
            "vecsimRescoreEnabled",
            "identifierAware",
            "identifierConsistencyEnabled",
            "identifierConsistencyAlgorithm",
            "identifierConsistencyScorePolicy",
            "identifierRecallConsistencyEnabled",
            "candidate5EvidenceAlgorithm",
            "identifierRecallConsistencyAlgorithm",
            "identifierRecallConsistencySqlPolicy",
            "topK",
            "executor",
            "nativeMode",
            "featureHash",
            "corpusInsertionOrder");
    private static final Set<String> COLBERT_CONFIG_FIELDS = Set.of(
            "enabled", "dimensions", "maxTokens");
    private static final Set<String> DYNAMIC_TOP_K_CONFIG_FIELDS = Set.of(
            "enabled",
            "defaultTopK",
            "minTopK",
            "maxTopK",
            "complexMinTopK",
            "mediumMultiplier",
            "complexMultiplier",
            "temporalMultiplier",
            "keywordMultiplierStep",
            "maxKeywordBonus");
    private static final Set<String> CONTEXT_CONFIG_FIELDS = Set.of(
            "maxBytes", "maxTokens");
    private static final Set<String> EXECUTOR_CONFIG_FIELDS = Set.of(
            "core", "max", "queue");
    private static final Set<String> FEATURE_HASH_CONFIG_FIELDS = Set.of(
            "version", "dimensions", "seed");
    private static final List<String> SELECTION_AUDITED_READS = List.of(
            "selection-manifest.json",
            "holdout-manifest.json",
            "candidate5-selection/corpus.jsonl",
            "candidate5-selection/queries.jsonl",
            "candidate5-selection/qrels.tsv",
            "candidate5-base-distractor/corpus.jsonl");
    private static final Set<String> INVALID_ERROR_CODES = Set.of(
            "CANDIDATE5_ARTIFACT_INVALID",
            "CANDIDATE5_CONFIG_INVALID",
            "CANDIDATE5_DATASET_INVALID",
            "CANDIDATE5_EXTERNAL_CALL_FAILED",
            "CANDIDATE5_KB_ISOLATION_FAILED",
            "CANDIDATE5_MANIFEST_INVALID",
            "CANDIDATE5_SELECTION_RESOURCE_HASH_MISMATCH",
            "CANDIDATE5_SQL_EXECUTION_FAILED",
            "INVALID_INCOMPLETE_PRIOR_RUN");
    private static final Set<String> FORBIDDEN_ARTIFACT_FIELDS = Set.of(
            "query",
            "retrievalquery",
            "identifier",
            "documentname",
            "content",
            "reference",
            "referenceanswer",
            "referenceclaims",
            "qrels",
            "grade",
            "sqlparameters",
            "sqlparams",
            "exceptionmessage",
            "errormessage");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ASCII_ALPHANUMERIC = Pattern.compile("[A-Za-z0-9]");
    private static final RagMetrics.Scores ZERO_SCORES =
            new RagMetrics.Scores(0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    private static final Object LEDGER_COMPLETION_LOCK = new Object();

    private RagCandidate5DiagnosticSupport() {
    }

    static RuntimePaths paths(Path ragEvalRuntime) {
        Path runtime = Objects.requireNonNull(ragEvalRuntime, "ragEvalRuntime")
                .toAbsolutePath().normalize();
        Path freeze = runtime.resolve("candidate5-freeze");
        return new RuntimePaths(
                freeze,
                freeze.resolve("selection-manifest.json"),
                freeze.resolve("holdout-manifest.json"),
                freeze.resolve("selection-ledger.json"),
                runtime.resolve("candidate5-calibration-diagnostic.json"));
    }

    static int enabledDiagnosticCount(
            boolean identifierDiagnostic,
            boolean candidate2Diagnostic,
            boolean candidate3Diagnostic,
            boolean candidate4Diagnostic,
            boolean candidate5Diagnostic) {
        return (identifierDiagnostic ? 1 : 0)
                + (candidate2Diagnostic ? 1 : 0)
                + (candidate3Diagnostic ? 1 : 0)
                + (candidate4Diagnostic ? 1 : 0)
                + (candidate5Diagnostic ? 1 : 0);
    }

    static void requireSelectionJobProperties() {
        if (System.getProperty(HOLDOUT_DIRECTORY_PROPERTY) != null) {
            throw new IllegalStateException("CANDIDATE5_HOLDOUT_ACCESS_FORBIDDEN");
        }
    }

    static void clearDiagnostic(RuntimePaths paths) {
        requireFixedRuntimePaths(paths);
        if (Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.deleteIfExists(paths.diagnostic());
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_CLEANUP_FAILED", failure);
        }
    }

    static FrozenManifests freezeDatasets(
            Path runtime,
            Path selectionDirectory,
            Path holdoutDirectory,
            Path baseCorpus) {
        RuntimePaths paths = paths(runtime);
        if (Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE5_SELECTION_ALREADY_STARTED");
        }
        DatasetFiles selection = readDatasetFiles(selectionDirectory, false);
        DatasetFiles holdout = readDatasetFiles(holdoutDirectory, true);
        BaseCorpus base = readBaseCorpus(baseCorpus);
        validateDatasetIsolation(selection.dataset(), holdout.dataset());
        RagEvaluationDataset mergedSelection = mergeBaseDistractors(
                selection.dataset(), base.segments(), false);
        RagEvaluationDataset mergedHoldout = mergeBaseDistractors(
                holdout.dataset(), base.segments(), true);
        Manifest selectionManifest = buildManifest(
                selection, base, mergedSelection, false);
        Manifest holdoutManifest = buildManifest(
                holdout, base, mergedHoldout, true);
        Map<String, Object> selectionValue = manifestMap(selectionManifest);
        Map<String, Object> holdoutValue = manifestMap(holdoutManifest);

        boolean selectionExists = Files.exists(
                paths.selectionManifest(), LinkOption.NOFOLLOW_LINKS);
        boolean holdoutExists = Files.exists(
                paths.holdoutManifest(), LinkOption.NOFOLLOW_LINKS);
        if (selectionExists || holdoutExists) {
            if (!selectionExists || !holdoutExists) {
                throw new IllegalStateException("CANDIDATE5_FREEZE_INCOMPLETE");
            }
            FrozenManifests existing = loadFrozenManifests(paths);
            if (existing.selection().equals(selectionManifest)
                    && existing.holdout().equals(holdoutManifest)
                    && atomicJson(selectionValue).equals(readUtf8(paths.selectionManifest()))
                    && atomicJson(holdoutValue).equals(readUtf8(paths.holdoutManifest()))) {
                return existing;
            }
            throw new IllegalStateException("CANDIDATE5_FREEZE_ALREADY_FROZEN");
        }
        writeAtomic(paths.selectionManifest(), selectionValue, false);
        writeAtomic(paths.holdoutManifest(), holdoutValue, false);
        return loadFrozenManifests(paths);
    }

    static FrozenManifests loadFrozenManifests(RuntimePaths paths) {
        Objects.requireNonNull(paths, "paths");
        requireFixedRuntimePaths(paths);
        if (Files.isSymbolicLink(paths.freezeDirectory())
                || !Files.isDirectory(paths.freezeDirectory(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE5_FREEZE_DIRECTORY_INVALID");
        }
        ManifestSnapshot selection = readManifest(
                paths.selectionManifest(), "candidate5-selection", "FROZEN");
        ManifestSnapshot holdout = readManifest(
                paths.holdoutManifest(), "candidate5-holdout", "FROZEN_NOT_BLIND");
        return new FrozenManifests(
                selection.manifest(),
                selection.sha256(),
                holdout.manifest(),
                holdout.sha256(),
                List.of("selection-manifest.json", "holdout-manifest.json"));
    }

    static FrozenDataset loadFrozenSelection(
            RuntimePaths paths, Path selectionDirectory, Path baseCorpus) {
        requireSelectionJobProperties();
        return loadFrozenDataset(paths, selectionDirectory, baseCorpus, false);
    }

    static FrozenDataset loadFrozenHoldout(
            RuntimePaths paths, Path holdoutDirectory, Path baseCorpus) {
        return loadFrozenDataset(paths, holdoutDirectory, baseCorpus, true);
    }

    static FrozenDataset loadFormalFrozenSelection(Path runtime) {
        Path tests = testsDirectory();
        return loadFrozenSelection(
                paths(runtime),
                tests.resolve("src/test/resources/rag-eval/candidate5-selection"),
                tests.resolve("src/test/resources/rag-eval/candidate5-base-distractor/corpus.jsonl"));
    }

    static FrozenDataset loadFormalFrozenHoldout(Path runtime, Path holdoutDirectory) {
        Path tests = testsDirectory();
        return loadFrozenHoldout(
                paths(runtime),
                holdoutDirectory,
                tests.resolve("src/test/resources/rag-eval/candidate5-base-distractor/corpus.jsonl"));
    }

    static FrozenDataset loadApprovedFormalFrozenHoldout(Path runtime) {
        RuntimePaths runtimePaths = paths(runtime);
        requireProceedToIdentifierRecallRed(runtimePaths);
        String configured = System.getProperty(HOLDOUT_DIRECTORY_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("CANDIDATE5_HOLDOUT_DIRECTORY_REQUIRED");
        }
        Path expected = testsDirectory().resolve("candidate5-holdout")
                .toAbsolutePath().normalize();
        Path actual = Path.of(configured).toAbsolutePath().normalize();
        if (!expected.equals(actual)) {
            throw new IllegalStateException("CANDIDATE5_HOLDOUT_DIRECTORY_MISMATCH");
        }
        return loadFormalFrozenHoldout(runtime, actual);
    }

    static void requireSelectionRunAvailable(RuntimePaths paths) {
        requireFixedRuntimePaths(paths);
        if (!Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        JSONObject ledger;
        try {
            ledger = readJson(paths.ledger(), "CANDIDATE5_LEDGER_INVALID");
        } catch (IllegalStateException failure) {
            throw new IllegalStateException("INVALID_INCOMPLETE_PRIOR_RUN", failure);
        }
        if ("RUNNING".equals(ledger.getString("status"))) {
            throw new IllegalStateException("INVALID_INCOMPLETE_PRIOR_RUN");
        }
        if ("COMPLETED".equals(ledger.getString("status"))) {
            throw new IllegalStateException("CANDIDATE5_SELECTION_ALREADY_COMPLETED");
        }
        throw new IllegalStateException("CANDIDATE5_LEDGER_INVALID");
    }

    static RunHandle beginSelectionRun(RuntimePaths paths, FrozenManifests manifests) {
        Objects.requireNonNull(manifests, "manifests");
        requireFixedRuntimePaths(paths);
        requireSelectionJobProperties();
        Map<String, Object> running = new LinkedHashMap<>();
        running.put("status", "RUNNING");
        running.put("selectionManifestSha256", manifests.selectionSha256());
        running.put("holdoutManifestSha256", manifests.holdoutSha256());
        running.put("startedAt", Instant.now().toString());
        running.put("artifactSha256", null);
        try {
            writeAtomicCreateNew(paths.ledger(), running);
        } catch (IllegalStateException failure) {
            if (Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)) {
                requireSelectionRunAvailable(paths);
            }
            throw failure;
        }
        return new RunHandle(
                paths,
                manifests.selectionSha256(),
                manifests.holdoutSha256(),
                running.get("startedAt").toString());
    }

    static String writeDiagnosticAndComplete(
            RuntimePaths paths, RunHandle handle, Map<String, ?> freshArtifact) {
        synchronized (LEDGER_COMPLETION_LOCK) {
            return writeDiagnosticAndCompleteLocked(paths, handle, freshArtifact);
        }
    }

    private static String writeDiagnosticAndCompleteLocked(
            RuntimePaths paths, RunHandle handle, Map<String, ?> freshArtifact) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(freshArtifact, "freshArtifact");
        if (!paths.equals(handle.paths())) {
            throw new IllegalArgumentException("CANDIDATE5_RUN_PATH_MISMATCH");
        }
        JSONObject ledger = readJson(paths.ledger(), "CANDIDATE5_LEDGER_INVALID");
        if (!"RUNNING".equals(ledger.getString("status"))) {
            throw new IllegalStateException("CANDIDATE5_LEDGER_NOT_RUNNING");
        }
        if (!handle.selectionManifestSha256().equals(
                ledger.getString("selectionManifestSha256"))
                || !handle.holdoutManifestSha256().equals(
                ledger.getString("holdoutManifestSha256"))) {
            throw new IllegalStateException("CANDIDATE5_LEDGER_MANIFEST_MISMATCH");
        }
        FrozenManifests manifests = loadFrozenManifests(paths);
        if (!handle.selectionManifestSha256().equals(manifests.selectionSha256())
                || !handle.holdoutManifestSha256().equals(manifests.holdoutSha256())) {
            throw new IllegalStateException("CANDIDATE5_RUN_MANIFEST_HASH_MISMATCH");
        }
        validateDiagnosticArtifact(freshArtifact, manifests);

        Map<String, Object> artifact = new LinkedHashMap<>();
        freshArtifact.forEach(artifact::put);
        writeAtomic(paths.diagnostic(), artifact, true);
        validateDiagnosticArtifact(
                readJson(paths.diagnostic(), "CANDIDATE5_DIAGNOSTIC_INVALID"), manifests);
        String artifactSha256 = ShadowContractSupport.sha256(paths.diagnostic());

        Map<String, Object> completed = new LinkedHashMap<>();
        completed.put("status", "COMPLETED");
        completed.put("selectionManifestSha256", handle.selectionManifestSha256());
        completed.put("holdoutManifestSha256", handle.holdoutManifestSha256());
        completed.put("startedAt", handle.startedAt());
        completed.put("completedAt", Instant.now().toString());
        completed.put("artifactSha256", artifactSha256);
        writeAtomic(paths.ledger(), completed, true);
        verifyCompletedRun(paths);
        return artifactSha256;
    }

    static void verifyCompletedRun(RuntimePaths paths) {
        JSONObject ledger = readJson(paths.ledger(), "CANDIDATE5_LEDGER_INVALID");
        if (!"COMPLETED".equals(ledger.getString("status"))) {
            throw new IllegalStateException("CANDIDATE5_LEDGER_NOT_COMPLETED");
        }
        FrozenManifests manifests = loadFrozenManifests(paths);
        if (!manifests.selectionSha256().equals(
                ledger.getString("selectionManifestSha256"))
                || !manifests.holdoutSha256().equals(
                ledger.getString("holdoutManifestSha256"))) {
            throw new IllegalStateException("CANDIDATE5_LEDGER_MANIFEST_MISMATCH");
        }
        if (!Files.isRegularFile(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(paths.diagnostic())) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_MISSING");
        }
        String expected = ledger.getString("artifactSha256");
        String actual = ShadowContractSupport.sha256(paths.diagnostic());
        if (!validSha256(expected) || !expected.equals(actual)) {
            throw new IllegalStateException("CANDIDATE5_ARTIFACT_HASH_MISMATCH");
        }
        validateDiagnosticArtifact(
                readJson(paths.diagnostic(), "CANDIDATE5_DIAGNOSTIC_INVALID"), manifests);
    }

    static void requireProceedToIdentifierRecallRed(RuntimePaths paths) {
        verifyCompletedRun(paths);
        JSONObject diagnostic = readJson(paths.diagnostic(), "CANDIDATE5_DIAGNOSTIC_INVALID");
        if (!"VALID".equals(diagnostic.getString("status"))
                || !"PROCEED_TO_IDENTIFIER_RECALL_RED".equals(
                diagnostic.getString("decision"))
                || diagnostic.get("errorCode") != null) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_NOT_APPROVED");
        }
    }

    static Map<String, Object> freshValidArtifact(
            FrozenDataset frozenSelection,
            Map<String, ?> config,
            String decision,
            Map<String, ?> summary,
            List<?> cases) {
        Map<String, Object> artifact = baseArtifact(frozenSelection, config);
        artifact.put("status", "VALID");
        artifact.put("decision", decision);
        artifact.put("errorCode", null);
        artifact.put("summary", new LinkedHashMap<>(summary));
        artifact.put("cases", List.copyOf(cases));
        return artifact;
    }

    static Map<String, Object> freshInvalidArtifact(
            FrozenDataset frozenSelection,
            Map<String, ?> config,
            String errorCode) {
        Map<String, Object> artifact = baseArtifact(frozenSelection, config);
        artifact.put("status", "INVALID");
        artifact.put("decision", null);
        artifact.put("errorCode", errorCode);
        return artifact;
    }

    static List<Document> copyDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<Document> copies = new ArrayList<>(documents.size());
        for (Document document : documents) {
            copies.add(Document.builder()
                    .id(document.getId())
                    .text(document.getText())
                    .metadata(new LinkedHashMap<>(document.getMetadata()))
                    .build());
        }
        return copies;
    }

    static CaseEvidence classify(CaseInput input) {
        Objects.requireNonNull(input, "input");
        RootCause classification;
        if (!input.target()) {
            classification = RootCause.NONE;
        } else if (!input.originalContainsIdentifier()
                || !input.retrievalContainsIdentifier()
                || !input.documentFieldsConsistent()) {
            classification = RootCause.RETRIEVAL_MISS_FIXTURE_INCONSISTENT;
        } else if (!input.extractorContainsIdentifier()) {
            classification = RootCause.IDENTIFIER_EXTRACTION_MISS;
        } else if ((!input.identifierTermEmitted() || !input.exactIdentifierPredicate())
                && !input.keywordReturnedAnchor()) {
            classification = RootCause.KEYWORD_TERM_NOT_EMITTED;
        } else if (input.exactIdentifierPredicate() && !input.keywordReturnedAnchor()) {
            classification = RootCause.KEYWORD_SQL_IDENTIFIER_MISS;
        } else if (input.keywordReturnedAnchor() && !input.finalReturnedAnchor()) {
            classification = RootCause.OTHER_RETRIEVAL_PATH_MISS;
        } else {
            classification = RootCause.NONE;
        }
        RagMetrics.Scores actual = input.qrels().isEmpty()
                ? ZERO_SCORES
                : RagMetrics.evaluate(input.qrels(), segmentIds(input.finalSources()));
        RagMetrics.Scores oracle = input.qrels().isEmpty()
                ? ZERO_SCORES
                : RagMetrics.evaluate(
                        input.qrels(), segmentIds(input.oracleRecoverableSources()));
        return new CaseEvidence(input, classification, actual, oracle);
    }

    static String decide(List<CaseEvidence> cases) {
        Objects.requireNonNull(cases, "cases");
        List<CaseEvidence> targets = cases.stream().filter(CaseEvidence::target).toList();
        List<CaseEvidence> controls = cases.stream().filter(item -> !item.target()).toList();
        if (targets.isEmpty()
                || controls.stream().anyMatch(item ->
                item.classification() != RootCause.NONE || !item.behaviorUnchanged())
                || targets.stream().anyMatch(item ->
                item.classification() == RootCause.RETRIEVAL_MISS_FIXTURE_INCONSISTENT
                        || item.classification() == RootCause.IDENTIFIER_EXTRACTION_MISS
                        || item.classification() == RootCause.KEYWORD_SQL_IDENTIFIER_MISS)) {
            return "STOP_MULTILINGUAL_IDENTIFIER_UNSUPPORTED";
        }
        Qualification qualification = qualification(targets);
        return qualification.familyCount() >= 4
                && qualification.identifierShapes().containsAll(IDENTIFIER_SHAPES)
                ? "PROCEED_TO_IDENTIFIER_RECALL_RED"
                : "STOP_MULTILINGUAL_IDENTIFIER_UNSUPPORTED";
    }

    static Map<String, Object> diagnosticSummary(List<CaseEvidence> cases) {
        Objects.requireNonNull(cases, "cases");
        Map<String, Integer> classifications = new LinkedHashMap<>();
        for (RootCause value : RootCause.values()) {
            classifications.put(value.name(), 0);
        }
        cases.forEach(item -> classifications.compute(
                item.classification().name(), (ignored, count) -> count + 1));
        List<CaseEvidence> targets = cases.stream().filter(CaseEvidence::target).toList();
        Qualification qualification = qualification(targets);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseCount", cases.size());
        summary.put("targetCaseCount", targets.size());
        summary.put("controlCaseCount", cases.size() - targets.size());
        summary.put("classificationCounts", Map.copyOf(classifications));
        summary.put("qualifyingFamilyCount", qualification.familyCount());
        summary.put("coveredIdentifierShapes", qualification.identifierShapes().stream()
                .sorted().toList());
        return Map.copyOf(summary);
    }

    private static Qualification qualification(List<CaseEvidence> targets) {
        Map<String, List<CaseEvidence>> families = targets.stream()
                .collect(Collectors.groupingBy(
                        item -> item.input().familyId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        Set<String> coveredShapes = new LinkedHashSet<>();
        int qualifyingFamilies = 0;
        for (List<CaseEvidence> family : families.values()) {
            CaseEvidence english = language(family, "en");
            CaseEvidence chinese = language(family, "zh");
            if (english != null
                    && chinese != null
                    && english.classification() == RootCause.KEYWORD_TERM_NOT_EMITTED
                    && chinese.input().keywordReturnedAnchor()) {
                qualifyingFamilies++;
                coveredShapes.add(english.input().identifierShape());
            }
        }
        return new Qualification(qualifyingFamilies, Set.copyOf(coveredShapes));
    }

    static void freezeFormalDatasets(Path runtime, Path holdoutDirectory) {
        Path tests = testsDirectory();
        freezeDatasets(
                runtime,
                tests.resolve("src/test/resources/rag-eval/candidate5-selection"),
                holdoutDirectory,
                tests.resolve("src/test/resources/rag-eval/candidate5-base-distractor/corpus.jsonl"));
    }

    private static FrozenDataset loadFrozenDataset(
            RuntimePaths paths,
            Path directory,
            Path baseCorpus,
            boolean holdout) {
        FrozenManifests manifests = loadFrozenManifests(paths);
        DatasetFiles source = readDatasetFiles(directory, holdout);
        BaseCorpus base = readBaseCorpus(baseCorpus);
        RagEvaluationDataset merged = mergeBaseDistractors(source.dataset(), base.segments(), holdout);
        Manifest current = buildManifest(source, base, merged, holdout);
        Manifest expected = holdout ? manifests.holdout() : manifests.selection();
        if (!current.equals(expected)) {
            throw new IllegalStateException(holdout
                    ? "CANDIDATE5_HOLDOUT_RESOURCE_HASH_MISMATCH"
                    : "CANDIDATE5_SELECTION_RESOURCE_HASH_MISMATCH");
        }
        String datasetName = holdout ? "candidate5-holdout" : "candidate5-selection";
        return new FrozenDataset(
                merged,
                manifests,
                current.datasetHash(),
                List.of(
                        "selection-manifest.json",
                        "holdout-manifest.json",
                        datasetName + "/corpus.jsonl",
                        datasetName + "/queries.jsonl",
                        datasetName + "/qrels.tsv",
                        "candidate5-base-distractor/corpus.jsonl"));
    }

    private static DatasetFiles readDatasetFiles(Path directory, boolean holdout) {
        Path normalized = requireDirectory(directory, "CANDIDATE5_DATASET_DIRECTORY_INVALID");
        Path corpusPath = requireRegular(
                normalized.resolve("corpus.jsonl"), "CANDIDATE5_DATASET_RESOURCE_INVALID");
        Path queriesPath = requireRegular(
                normalized.resolve("queries.jsonl"), "CANDIDATE5_DATASET_RESOURCE_INVALID");
        Path qrelsPath = requireRegular(
                normalized.resolve("qrels.tsv"), "CANDIDATE5_DATASET_RESOURCE_INVALID");
        try {
            Map<String, RagEvaluationDataset.CorpusSegment> corpus =
                    parseCorpus(Files.readString(corpusPath, StandardCharsets.UTF_8));
            List<RagEvaluationDataset.QueryCase> queries =
                    parseQueries(Files.readString(queriesPath, StandardCharsets.UTF_8));
            ParsedQrels qrels = parseQrels(
                    Files.readString(qrelsPath, StandardCharsets.UTF_8));
            RagEvaluationDataset dataset = new RagEvaluationDataset(
                    corpus, queries, qrels.qrels());
            RagEvaluationDatasetLoader.validate(dataset);
            validateCandidate5Shape(dataset, qrels.count(), holdout);
            Map<String, ResourceHash> resources = new LinkedHashMap<>();
            resources.put("corpus", new ResourceHash(
                    "corpus.jsonl", ShadowContractSupport.sha256(corpusPath)));
            resources.put("queries", new ResourceHash(
                    "queries.jsonl", ShadowContractSupport.sha256(queriesPath)));
            resources.put("qrels", new ResourceHash(
                    "qrels.tsv", ShadowContractSupport.sha256(qrelsPath)));
            return new DatasetFiles(dataset, qrels.count(), Map.copyOf(resources));
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE5_DATASET_READ_FAILED", failure);
        }
    }

    private static BaseCorpus readBaseCorpus(Path path) {
        Path source = requireRegular(path, "CANDIDATE5_BASE_CORPUS_INVALID");
        try {
            Map<String, RagEvaluationDataset.CorpusSegment> segments =
                    parseCorpus(Files.readString(source, StandardCharsets.UTF_8));
            if (segments.size() != BASE_DISTRACTOR_COUNT
                    || segments.values().stream().anyMatch(segment ->
                    segment.parentSegmentId() != null)
                    || segments.values().stream()
                    .map(RagEvaluationDataset.CorpusSegment::documentId)
                    .distinct().count() != BASE_DISTRACTOR_COUNT) {
                throw new IllegalArgumentException("CANDIDATE5_BASE_CORPUS_SHAPE_INVALID");
            }
            return new BaseCorpus(
                    Map.copyOf(segments),
                    new ResourceHash("corpus.jsonl", ShadowContractSupport.sha256(source)));
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE5_BASE_CORPUS_READ_FAILED", failure);
        }
    }

    private static void validateCandidate5Shape(
            RagEvaluationDataset dataset, int qrelCount, boolean holdout) {
        String prefix = holdout ? "c5h-" : "c5s-";
        String split = holdout ? "holdout" : "selection";
        int targetFamilies = holdout ? 12 : 6;
        int lexicalDistractors = holdout ? 24 : 12;
        int expectedQueries = holdout ? 32 : 20;
        int expectedQrels = holdout ? 76 : 40;
        int expectedSegments = targetFamilies * 3 + 2 + lexicalDistractors;
        long segmentMin = holdout ? HOLDOUT_SEGMENT_ID_MIN : SELECTION_SEGMENT_ID_MIN;
        long segmentMax = holdout ? HOLDOUT_SEGMENT_ID_MAX : SELECTION_SEGMENT_ID_MAX;
        long documentMin = holdout ? HOLDOUT_DOCUMENT_ID_MIN : SELECTION_DOCUMENT_ID_MIN;
        long documentMax = holdout ? HOLDOUT_DOCUMENT_ID_MAX : SELECTION_DOCUMENT_ID_MAX;
        if (dataset.queries().size() != expectedQueries
                || dataset.corpusById().size() != expectedSegments
                || qrelCount != expectedQrels) {
            throw new IllegalArgumentException("CANDIDATE5_DATASET_COUNT_MISMATCH");
        }
        if (dataset.queries().stream().anyMatch(query ->
                !query.id().startsWith(prefix)
                        || !split.equals(query.split())
                        || !Set.of("zh", "en").contains(query.language()))) {
            throw new IllegalArgumentException("CANDIDATE5_QUERY_NAMESPACE_MISMATCH");
        }
        Map<String, List<RagEvaluationDataset.QueryCase>> families = dataset.queries().stream()
                .collect(Collectors.groupingBy(
                        RagEvaluationDataset.QueryCase::familyId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        long targetCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "t")).count();
        long noIdentifierCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "n")).count();
        long unanswerableCount = families.keySet().stream()
                .filter(id -> id.startsWith(prefix + "u")).count();
        if (targetCount != targetFamilies
                || noIdentifierCount != 2
                || unanswerableCount != 2
                || families.size() != targetFamilies + 4) {
            throw new IllegalArgumentException("CANDIDATE5_FAMILY_SHAPE_MISMATCH");
        }

        Map<String, Integer> shapeCounts = new LinkedHashMap<>();
        for (Map.Entry<String, List<RagEvaluationDataset.QueryCase>> entry
                : families.entrySet()) {
            List<RagEvaluationDataset.QueryCase> family = entry.getValue();
            Set<String> languages = family.stream()
                    .map(RagEvaluationDataset.QueryCase::language).collect(Collectors.toSet());
            if (family.size() != 2 || !languages.equals(Set.of("zh", "en"))) {
                throw new IllegalArgumentException("CANDIDATE5_BILINGUAL_FAMILY_MISMATCH");
            }
            boolean target = entry.getKey().startsWith(prefix + "t");
            boolean noIdentifier = entry.getKey().startsWith(prefix + "n");
            int expectedRelevant = target ? 3 : noIdentifier ? 1 : 0;
            Set<String> sharedQrels = null;
            String familyShape = null;
            for (RagEvaluationDataset.QueryCase query : family) {
                Set<String> queryQrels = dataset.qrelsFor(query.id()).keySet();
                if (queryQrels.size() != expectedRelevant
                        || query.answerable() != (expectedRelevant > 0)) {
                    throw new IllegalArgumentException("CANDIDATE5_QREL_SHAPE_MISMATCH");
                }
                if (sharedQrels == null) {
                    sharedQrels = Set.copyOf(queryQrels);
                } else if (!sharedQrels.equals(queryQrels)) {
                    throw new IllegalArgumentException("CANDIDATE5_FAMILY_QRELS_MISMATCH");
                }
                List<String> queryIdentifiers = extractIdentifierTerms(query.query());
                List<String> retrievalIdentifiers =
                        extractIdentifierTerms(query.retrievalQuery());
                if (target) {
                    if (queryIdentifiers.size() != 1
                            || !queryIdentifiers.equals(retrievalIdentifiers)) {
                        throw new IllegalArgumentException(
                                "CANDIDATE5_TARGET_IDENTIFIER_INVALID");
                    }
                    String shape = query.strata().stream()
                            .filter(IDENTIFIER_SHAPES::contains)
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "CANDIDATE5_IDENTIFIER_SHAPE_INVALID"));
                    if (familyShape == null) {
                        familyShape = shape;
                    } else if (!familyShape.equals(shape)) {
                        throw new IllegalArgumentException(
                                "CANDIDATE5_IDENTIFIER_SHAPE_INVALID");
                    }
                    long exactMatches = queryQrels.stream().filter(segmentId -> {
                        RagEvaluationDataset.CorpusSegment segment =
                                dataset.corpusById().get(segmentId);
                        Object name = segment == null
                                ? null : segment.metadata().get("documentName");
                        return name instanceof String documentName
                                && asciiBoundaryMatches(
                                documentName, queryIdentifiers.get(0));
                    }).count();
                    if (exactMatches != 1) {
                        throw new IllegalArgumentException(
                                "CANDIDATE5_IDENTIFIER_ANCHOR_INVALID");
                    }
                } else if (noIdentifier
                        && (!queryIdentifiers.isEmpty() || !retrievalIdentifiers.isEmpty())) {
                    throw new IllegalArgumentException(
                            "CANDIDATE5_NO_IDENTIFIER_CONTROL_INVALID");
                } else if (!noIdentifier
                        && (queryIdentifiers.size() != 1
                        || !queryIdentifiers.equals(retrievalIdentifiers))) {
                    throw new IllegalArgumentException(
                            "CANDIDATE5_IDENTIFIER_CONTROL_INVALID");
                }
            }
            if (target) {
                shapeCounts.merge(familyShape, 1, Integer::sum);
            }
            for (String segmentId : Objects.requireNonNull(sharedQrels)) {
                RagEvaluationDataset.CorpusSegment segment =
                        dataset.corpusById().get(segmentId);
                String expectedRole = target
                        ? "target-evidence" : "no-identifier-evidence";
                if (segment == null
                        || !expectedRole.equals(
                        segment.metadata().get("candidate5Role"))
                        || !entry.getKey().equals(segment.metadata().get("familyId"))
                        || (target && !familyShape.equals(
                        segment.metadata().get("identifierShape")))) {
                    throw new IllegalArgumentException(
                        "CANDIDATE5_SHARED_EVIDENCE_METADATA_INVALID");
                }
            }
            if (target) {
                validateAnchorContentDoesNotLeakSearchTerms(
                        dataset, family, Objects.requireNonNull(sharedQrels));
            }
        }
        int expectedPerShape = holdout ? 4 : 2;
        if (!shapeCounts.equals(Map.of(
                "numeric-token", expectedPerShape,
                "doc-prefix", expectedPerShape,
                "han-zero-padded", expectedPerShape))) {
            throw new IllegalArgumentException("CANDIDATE5_IDENTIFIER_SHAPE_COUNT_INVALID");
        }

        Set<String> documents = new LinkedHashSet<>();
        Map<String, Integer> roleCounts = new LinkedHashMap<>();
        for (RagEvaluationDataset.CorpusSegment segment : dataset.corpusById().values()) {
            long segmentId = parseLong(segment.segmentId(), "CANDIDATE5_SEGMENT_ID_INVALID");
            long documentId = parseLong(segment.documentId(), "CANDIDATE5_DOCUMENT_ID_INVALID");
            if (segmentId < segmentMin || segmentId > segmentMax
                    || documentId < documentMin || documentId > documentMax
                    || segment.parentSegmentId() != null
                    || !documents.add(segment.documentId())) {
                throw new IllegalArgumentException("CANDIDATE5_CORPUS_ISOLATION_INVALID");
            }
            Object documentName = segment.metadata().get("documentName");
            Object role = segment.metadata().get("candidate5Role");
            if (!(documentName instanceof String name) || name.isBlank()
                    || !(role instanceof String)) {
                throw new IllegalArgumentException("CANDIDATE5_DOCUMENT_METADATA_INVALID");
            }
            roleCounts.merge((String) role, 1, Integer::sum);
        }
        if (!roleCounts.equals(Map.of(
                "target-evidence", targetFamilies * 3,
                "no-identifier-evidence", 2,
                "lexical-distractor", lexicalDistractors))) {
            throw new IllegalArgumentException("CANDIDATE5_EVIDENCE_ROLE_COUNT_INVALID");
        }
    }

    private static RagEvaluationDataset mergeBaseDistractors(
            RagEvaluationDataset source,
            Map<String, RagEvaluationDataset.CorpusSegment> base,
            boolean holdout) {
        long segmentMin = holdout ? HOLDOUT_SEGMENT_ID_MIN : SELECTION_SEGMENT_ID_MIN;
        long documentMin = holdout ? HOLDOUT_DOCUMENT_ID_MIN : SELECTION_DOCUMENT_ID_MIN;
        List<RagEvaluationDataset.CorpusSegment> sorted = base.values().stream()
                .sorted((left, right) -> left.segmentId().compareTo(right.segmentId()))
                .toList();
        Map<String, RagEvaluationDataset.CorpusSegment> merged =
                new LinkedHashMap<>(source.corpusById());
        for (int index = 0; index < sorted.size(); index++) {
            RagEvaluationDataset.CorpusSegment original = sorted.get(index);
            String segmentId = String.valueOf(segmentMin + index);
            String documentId = String.valueOf(documentMin + index);
            Map<String, Object> metadata = new LinkedHashMap<>(original.metadata());
            metadata.put("candidate5Role", "base-distractor");
            RagEvaluationDataset.CorpusSegment remapped =
                    new RagEvaluationDataset.CorpusSegment(
                            segmentId,
                            documentId,
                            original.content(),
                            null,
                            metadata);
            if (merged.putIfAbsent(segmentId, remapped) != null) {
                throw new IllegalStateException("CANDIDATE5_BASE_SEGMENT_ID_COLLISION");
            }
        }
        RagEvaluationDataset dataset = new RagEvaluationDataset(
                merged, source.queries(), source.qrels());
        RagEvaluationDatasetLoader.validate(dataset);
        return dataset;
    }

    private static Manifest buildManifest(
            DatasetFiles files,
            BaseCorpus base,
            RagEvaluationDataset merged,
            boolean holdout) {
        Map<String, ResourceHash> resources = new LinkedHashMap<>(files.resources());
        resources.put("baseDistractor", base.resource());
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("familyCount", (int) files.dataset().queries().stream()
                .map(RagEvaluationDataset.QueryCase::familyId).distinct().count());
        counts.put("queryCount", files.dataset().queries().size());
        counts.put("documentCount", (int) merged.corpusById().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId).distinct().count());
        counts.put("segmentCount", merged.corpusById().size());
        counts.put("qrelCount", files.qrelCount());
        Map<String, Object> structure = structure(holdout, merged);
        String datasetHash = manifestDatasetHash(
                GENERATOR,
                GENERATOR_VERSION,
                GENERATOR_SEED,
                resources,
                counts,
                structure);
        return new Manifest(
                holdout ? "candidate5-holdout" : "candidate5-selection",
                holdout ? "FROZEN_NOT_BLIND" : "FROZEN",
                GENERATOR,
                GENERATOR_VERSION,
                GENERATOR_SEED,
                Map.copyOf(resources),
                Map.copyOf(counts),
                Map.copyOf(structure),
                datasetHash);
    }

    private static Map<String, Object> structure(
            boolean holdout, RagEvaluationDataset merged) {
        int targetFamilies = holdout ? 12 : 6;
        int lexicalDistractors = holdout ? 24 : 12;
        int perShape = holdout ? 4 : 2;
        long segmentMin = holdout ? HOLDOUT_SEGMENT_ID_MIN : SELECTION_SEGMENT_ID_MIN;
        long documentMin = holdout ? HOLDOUT_DOCUMENT_ID_MIN : SELECTION_DOCUMENT_ID_MIN;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("targetFamilyCount", targetFamilies);
        value.put("noIdentifierAnswerableFamilyCount", 2);
        value.put("identifierUnanswerableFamilyCount", 2);
        value.put("bilingualQueriesPerFamily", 2);
        value.put("relevantSegmentsPerTargetFamily", 3);
        value.put("relevantSegmentsPerNoIdentifierFamily", 1);
        value.put("targetEvidenceCount", targetFamilies * 3);
        value.put("noIdentifierEvidenceCount", 2);
        value.put("lexicalDistractorCount", lexicalDistractors);
        value.put("baseDistractorCount", BASE_DISTRACTOR_COUNT);
        value.put("bilingualFamilySharesRelevantSegments", true);
        value.put("uniqueDocumentPerSegment", true);
        value.put("identifierShapeFamilyCounts", Map.of(
                "numeric-token", perShape,
                "doc-prefix", perShape,
                "han-zero-padded", perShape));
        value.put("baseRemapRule", Map.of(
                "policy", BASE_REMAP_POLICY,
                "count", BASE_DISTRACTOR_COUNT,
                "segmentIdStart", Math.toIntExact(segmentMin),
                "documentIdStart", Math.toIntExact(documentMin)));
        value.put("mergedIdMappingHash", mergedIdMappingHash(merged));
        return value;
    }

    private static String mergedIdMappingHash(RagEvaluationDataset dataset) {
        List<Map<String, String>> mapping = dataset.corpusById().values().stream()
                .sorted((left, right) -> Long.compare(
                        parseLong(left.segmentId(), "CANDIDATE5_SEGMENT_ID_INVALID"),
                        parseLong(right.segmentId(), "CANDIDATE5_SEGMENT_ID_INVALID")))
                .map(segment -> Map.of(
                        "segmentId", segment.segmentId(),
                        "documentId", segment.documentId()))
                .toList();
        return ShadowContractSupport.configHash(Map.of("mapping", mapping));
    }

    private static Map<String, Object> manifestMap(Manifest manifest) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("manifestVersion", 1);
        value.put("dataset", manifest.dataset());
        value.put("freezeStatus", manifest.freezeStatus());
        value.put("generator", manifest.generator());
        value.put("version", manifest.version());
        value.put("seed", manifest.seed());
        Map<String, Object> resources = new LinkedHashMap<>();
        manifest.resources().forEach((name, resource) -> resources.put(name, Map.of(
                "file", resource.file(), "sha256", resource.sha256())));
        value.put("resources", resources);
        value.put("counts", manifest.counts());
        value.put("structure", manifest.structure());
        value.put("datasetHash", manifest.datasetHash());
        return value;
    }

    private static ManifestSnapshot readManifest(
            Path path, String dataset, String freezeStatus) {
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE5_MANIFEST_NOT_REGULAR");
        }
        JSONObject json;
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
            json = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException("CANDIDATE5_MANIFEST_INVALID", failure);
        }
        if (!json.keySet().equals(MANIFEST_FIELDS)
                || !dataset.equals(json.getString("dataset"))
                || !freezeStatus.equals(json.getString("freezeStatus"))
                || !MANIFEST_VERSION.equals(String.valueOf(json.get("manifestVersion")))
                || !GENERATOR.equals(json.getString("generator"))
                || GENERATOR_VERSION != json.getIntValue("version")
                || GENERATOR_SEED != json.getLongValue("seed")) {
            throw new IllegalStateException("CANDIDATE5_MANIFEST_INVALID");
        }
        JSONObject resourceJson = json.getJSONObject("resources");
        JSONObject countJson = json.getJSONObject("counts");
        JSONObject structureJson = json.getJSONObject("structure");
        if (resourceJson == null || !resourceJson.keySet().equals(RESOURCE_NAMES)
                || countJson == null || !countJson.keySet().equals(COUNT_FIELDS)
                || structureJson == null || !structureJson.keySet().equals(STRUCTURE_FIELDS)) {
            throw new IllegalStateException("CANDIDATE5_MANIFEST_INVALID");
        }
        Map<String, ResourceHash> resources = new LinkedHashMap<>();
        for (String name : RESOURCE_NAMES) {
            JSONObject resource = resourceJson.getJSONObject(name);
            if (resource == null || !resource.keySet().equals(RESOURCE_FIELDS)
                    || !RESOURCE_FILES.get(name).equals(resource.getString("file"))
                    || !validSha256(resource.getString("sha256"))) {
                throw new IllegalStateException("CANDIDATE5_MANIFEST_INVALID");
            }
            resources.put(name, new ResourceHash(
                    resource.getString("file"), resource.getString("sha256")));
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        COUNT_FIELDS.forEach(name -> counts.put(name, countJson.getIntValue(name)));
        Map<String, Object> structure = new LinkedHashMap<>(structureJson);
        validateManifestStructure(dataset, structureJson);
        String datasetHash = json.getString("datasetHash");
        String expectedHash = manifestDatasetHash(
                json.getString("generator"),
                json.getIntValue("version"),
                json.getLongValue("seed"),
                resources,
                counts,
                structure);
        if (!validSha256(datasetHash) || !datasetHash.equals(expectedHash)) {
            throw new IllegalStateException("CANDIDATE5_MANIFEST_INVALID");
        }
        return new ManifestSnapshot(
                new Manifest(
                        json.getString("dataset"),
                        json.getString("freezeStatus"),
                        json.getString("generator"),
                        json.getIntValue("version"),
                        json.getLongValue("seed"),
                        Map.copyOf(resources),
                        Map.copyOf(counts),
                        Map.copyOf(structure),
                        datasetHash),
                ShadowContractSupport.sha256(bytes));
    }

    private static void validateManifestStructure(String dataset, JSONObject structure) {
        boolean holdout = "candidate5-holdout".equals(dataset);
        JSONObject remap = structure.getJSONObject("baseRemapRule");
        long expectedSegmentStart = holdout
                ? HOLDOUT_SEGMENT_ID_MIN : SELECTION_SEGMENT_ID_MIN;
        long expectedDocumentStart = holdout
                ? HOLDOUT_DOCUMENT_ID_MIN : SELECTION_DOCUMENT_ID_MIN;
        if (remap == null
                || !remap.keySet().equals(BASE_REMAP_FIELDS)
                || !BASE_REMAP_POLICY.equals(remap.getString("policy"))
                || remap.getIntValue("count") != BASE_DISTRACTOR_COUNT
                || remap.getLongValue("segmentIdStart") != expectedSegmentStart
                || remap.getLongValue("documentIdStart") != expectedDocumentStart
                || !validSha256(structure.getString("mergedIdMappingHash"))) {
            throw new IllegalStateException("CANDIDATE5_MANIFEST_INVALID");
        }
    }

    private static String manifestDatasetHash(
            String generator,
            int version,
            long seed,
            Map<String, ResourceHash> resources,
            Map<String, Integer> counts,
            Map<String, ?> structure) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("generator", generator);
        evidence.put("version", version);
        evidence.put("seed", seed);
        Map<String, Object> resourceEvidence = new LinkedHashMap<>();
        resources.forEach((name, resource) -> resourceEvidence.put(name, Map.of(
                "file", resource.file(), "sha256", resource.sha256())));
        evidence.put("resources", resourceEvidence);
        evidence.put("counts", counts);
        evidence.put("structure", structure);
        return ShadowContractSupport.configHash(evidence);
    }

    private static Map<String, Object> baseArtifact(
            FrozenDataset frozenSelection, Map<String, ?> config) {
        Objects.requireNonNull(frozenSelection, "frozenSelection");
        FrozenManifests manifests = frozenSelection.manifests();
        if (!SELECTION_AUDITED_READS.equals(frozenSelection.auditedReads())
                || !manifests.selection().datasetHash().equals(
                frozenSelection.datasetHash())) {
            throw new IllegalArgumentException("CANDIDATE5_SELECTION_AUDIT_INVALID");
        }
        Map<String, Object> configCopy = new LinkedHashMap<>();
        config.forEach(configCopy::put);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("datasetHash", frozenSelection.datasetHash());
        artifact.put("selectionManifestHash", manifests.selectionSha256());
        artifact.put("holdoutManifestHash", manifests.holdoutSha256());
        artifact.put("holdoutFreezeStatus", "FROZEN_NOT_BLIND");
        artifact.put("auditedReads", frozenSelection.auditedReads());
        artifact.put("config", configCopy);
        artifact.put("configHash", ShadowContractSupport.configHash(configCopy));
        return artifact;
    }

    private static void validateDiagnosticArtifact(
            Map<String, ?> artifact, FrozenManifests manifests) {
        rejectForbiddenFields(artifact);
        String status = stringValue(artifact.get("status"));
        Set<String> expectedFields = "VALID".equals(status)
                ? VALID_ARTIFACT_FIELDS : BASE_ARTIFACT_FIELDS;
        if (!artifact.keySet().equals(expectedFields)) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        Object configValue = artifact.get("config");
        String configHash = stringValue(artifact.get("configHash"));
        if (!(configValue instanceof Map<?, ?> config)) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_CONFIG_INVALID");
        }
        Map<String, Object> stringConfig = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : config.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_CONFIG_INVALID");
            }
            stringConfig.put(key, entry.getValue());
        }
        if (!validSha256(configHash)
                || !configHash.equals(ShadowContractSupport.configHash(stringConfig))) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_CONFIG_INVALID");
        }
        validateLockedConfig(stringConfig);
        if (!manifests.selection().datasetHash().equals(
                stringValue(artifact.get("datasetHash")))
                || !manifests.selectionSha256().equals(
                stringValue(artifact.get("selectionManifestHash")))
                || !manifests.holdoutSha256().equals(
                stringValue(artifact.get("holdoutManifestHash")))
                || !"FROZEN_NOT_BLIND".equals(
                stringValue(artifact.get("holdoutFreezeStatus")))
                || !SELECTION_AUDITED_READS.equals(artifact.get("auditedReads"))) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_MANIFEST_INVALID");
        }
        String decision = stringValue(artifact.get("decision"));
        String errorCode = stringValue(artifact.get("errorCode"));
        if ("VALID".equals(status)) {
            if (!VALID_DECISIONS.contains(decision)
                    || !artifact.containsKey("errorCode")
                    || errorCode != null
                    || !(artifact.get("summary") instanceof Map<?, ?>)
                    || !(artifact.get("cases") instanceof List<?>)) {
                throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
            }
            validateValidEvidence(
                    artifact.get("summary"), artifact.get("cases"), decision);
            return;
        }
        if (!"INVALID".equals(status)
                || decision != null
                || !INVALID_ERROR_CODES.contains(errorCode)
                || artifact.containsKey("summary")
                || artifact.containsKey("cases")) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
    }

    private static void validateLockedConfig(Map<String, Object> config) {
        if (!config.keySet().equals(CONFIG_FIELDS)
                || !Boolean.FALSE.equals(config.get("identifierAware"))
                || !Boolean.TRUE.equals(config.get("identifierConsistencyEnabled"))
                || !"document-name-exact-identifier-v1".equals(
                config.get("identifierConsistencyAlgorithm"))
                || !"deterministic-rank-score-preserving-v1".equals(
                config.get("identifierConsistencyScorePolicy"))
                || !Boolean.FALSE.equals(config.get("identifierRecallConsistencyEnabled"))
                || !"multilingual-identifier-recall-mechanistic-v1".equals(
                config.get("candidate5EvidenceAlgorithm"))
                || !"document-name-boundary-predicate-v1".equals(
                config.get("identifierRecallConsistencyAlgorithm"))
                || !"postgres-posix-c-alnum-v1".equals(
                config.get("identifierRecallConsistencySqlPolicy"))
                || !"java-fallback".equals(config.get("nativeMode"))
                || intValue(config.get("topK"), -1) != 10) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_CONFIG_INVALID");
        }
        Map<String, Object> colbert = stringMap(
                config.get("colbert"), "CANDIDATE5_DIAGNOSTIC_CONFIG_INVALID");
        Map<String, Object> dynamicTopK = stringMap(
                config.get("dynamicTopK"), "CANDIDATE5_DIAGNOSTIC_CONFIG_INVALID");
        Map<String, Object> context = stringMap(
                config.get("context"), "CANDIDATE5_DIAGNOSTIC_CONFIG_INVALID");
        Map<String, Object> executor = stringMap(
                config.get("executor"), "CANDIDATE5_DIAGNOSTIC_CONFIG_INVALID");
        Map<String, Object> featureHash = stringMap(
                config.get("featureHash"), "CANDIDATE5_DIAGNOSTIC_CONFIG_INVALID");
        if (intValue(config.get("rrfK"), -1) != 60
                || !numberEquals(config.get("weakPathThreshold"), 0.0D)
                || !Boolean.FALSE.equals(config.get("routerEnabled"))
                || !Boolean.FALSE.equals(config.get("queryEntityEnabled"))
                || !Boolean.FALSE.equals(config.get("queryTransformEnabled"))
                || !Boolean.FALSE.equals(config.get("cragEnabled"))
                || !Boolean.FALSE.equals(config.get("cragWebEnabled"))
                || !Boolean.FALSE.equals(config.get("graphEnabled"))
                || !"deterministic".equals(config.get("rerankerMode"))
                || !List.of("deterministic").equals(config.get("rerankerProviders"))
                || !Boolean.FALSE.equals(config.get("vecsimRescoreEnabled"))
                || !"segmentId-ascending".equals(config.get("corpusInsertionOrder"))
                || !dynamicTopK.keySet().equals(DYNAMIC_TOP_K_CONFIG_FIELDS)
                || !Boolean.FALSE.equals(dynamicTopK.get("enabled"))
                || intValue(dynamicTopK.get("defaultTopK"), -1) != 10
                || intValue(dynamicTopK.get("minTopK"), -1) != 3
                || intValue(dynamicTopK.get("maxTopK"), -1) != 80
                || intValue(dynamicTopK.get("complexMinTopK"), -1) != 12
                || !numberEquals(dynamicTopK.get("mediumMultiplier"), 1.0D)
                || !numberEquals(dynamicTopK.get("complexMultiplier"), 1.8D)
                || !numberEquals(dynamicTopK.get("temporalMultiplier"), 1.3D)
                || !numberEquals(dynamicTopK.get("keywordMultiplierStep"), 0.08D)
                || !numberEquals(dynamicTopK.get("maxKeywordBonus"), 0.5D)
                || !context.keySet().equals(CONTEXT_CONFIG_FIELDS)
                || intValue(context.get("maxBytes"), -1) != 20000
                || intValue(context.get("maxTokens"), -1) != 0
                || !colbert.keySet().equals(COLBERT_CONFIG_FIELDS)
                || !Boolean.TRUE.equals(colbert.get("enabled"))
                || intValue(colbert.get("dimensions"), -1) != 64
                || intValue(colbert.get("maxTokens"), -1) != 128
                || !executor.keySet().equals(EXECUTOR_CONFIG_FIELDS)
                || intValue(executor.get("core"), -1) != 4
                || intValue(executor.get("max"), -1) != 4
                || intValue(executor.get("queue"), -1) != 32
                || !featureHash.keySet().equals(FEATURE_HASH_CONFIG_FIELDS)
                || !FeatureHashEmbeddingModel.VERSION.equals(featureHash.get("version"))
                || intValue(featureHash.get("dimensions"), -1) != 256
                || longValue(featureHash.get("seed"), -1L) != 20260715L) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_CONFIG_INVALID");
        }
    }

    private static void validateValidEvidence(
            Object summaryValue, Object casesValue, String declaredDecision) {
        if (!(casesValue instanceof List<?> rawCases)
                || rawCases.size() != SELECTION_CASE_COUNT) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        List<CaseEvidence> cases = new ArrayList<>(rawCases.size());
        Set<String> queryIds = new LinkedHashSet<>();
        for (Object rawCase : rawCases) {
            CaseEvidence evidence = rebuildCaseEvidence(rawCase);
            if (!queryIds.add(evidence.input().queryId())) {
                throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
            }
            cases.add(evidence);
        }
        if (!queryIds.equals(selectionQueryIds())
                || !declaredDecision.equals(decide(cases))) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        validateCaseFamilyStructure(cases);
        Map<String, Object> summary = stringMap(
                summaryValue, "CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        if (!summary.keySet().equals(SUMMARY_FIELDS)) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        Map<String, Object> classifications = stringMap(
                summary.get("classificationCounts"),
                "CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        Set<String> classificationNames = java.util.Arrays.stream(RootCause.values())
                .map(Enum::name).collect(Collectors.toSet());
        if (!classifications.keySet().equals(classificationNames)
                || !(summary.get("coveredIdentifierShapes") instanceof List<?>)) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        String actualSummaryHash = ShadowContractSupport.configHash(
                Map.of("summary", summary));
        String expectedSummaryHash = ShadowContractSupport.configHash(
                Map.of("summary", diagnosticSummary(cases)));
        if (!actualSummaryHash.equals(expectedSummaryHash)) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
    }

    private static CaseEvidence rebuildCaseEvidence(Object rawValue) {
        Map<String, Object> value = stringMap(
                rawValue, "CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        if (!value.keySet().equals(CASE_FIELDS)) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        String queryId = requiredArtifactString(value, "queryId");
        String familyId = requiredArtifactString(value, "familyId");
        String language = requiredArtifactString(value, "language");
        String split = requiredArtifactString(value, "split");
        String identifierShape = requiredArtifactString(value, "identifierShape");
        boolean target = requiredArtifactBoolean(value, "target");
        boolean originalContainsIdentifier = requiredArtifactBoolean(
                value, "originalContainsIdentifier");
        boolean retrievalContainsIdentifier = requiredArtifactBoolean(
                value, "retrievalContainsIdentifier");
        boolean extractorContainsIdentifier = requiredArtifactBoolean(
                value, "extractorContainsIdentifier");
        boolean exactIdentifierTermEmitted = requiredArtifactBoolean(
                value, "exactIdentifierTermEmitted");
        boolean exactIdentifierPredicate = requiredArtifactBoolean(
                value, "exactIdentifierPredicate");
        boolean documentFieldsConsistent = requiredArtifactBoolean(
                value, "documentFieldsConsistent");
        boolean keywordReturnedAnchor = requiredArtifactBoolean(
                value, "keywordReturnedAnchor");
        boolean finalReturnedAnchor = requiredArtifactBoolean(
                value, "finalReturnedAnchor");
        boolean behaviorUnchanged = requiredArtifactBoolean(value, "behaviorUnchanged");
        boolean contextEmpty = requiredArtifactBoolean(value, "contextEmpty");

        requireSha256(value, "originalQuerySha256");
        requireSha256(value, "retrievalQuerySha256");
        requireSha256(value, "extractedIdentifierHash");
        requireSha256(value, "searchTermsHash");
        requireSha256(value, "sqlShapeHash");
        requireSha256(value, "contextSha256");

        int extractedIdentifierCount = requiredNonNegativeInt(
                value, "extractedIdentifierCount");
        requiredNonNegativeInt(value, "searchTermCount");
        int identifierPredicateCount = requiredNonNegativeInt(
                value, "identifierPredicateCount");
        if (extractorContainsIdentifier != (extractedIdentifierCount > 0)
                || exactIdentifierPredicate != (identifierPredicateCount > 0)) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }

        List<Boolean> fullPathPresence = booleanList(
                value.get("fullPathIdentifierPresence"));
        if (fullPathPresence.isEmpty()
                || fullPathPresence.get(0) != retrievalContainsIdentifier) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        Map<String, Object> rawPaths = stringMap(
                value.get("paths"), "CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        if (!rawPaths.keySet().equals(PATH_NAMES)) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        Map<String, List<RankedSegment>> paths = new LinkedHashMap<>();
        rawPaths.forEach((name, ranking) -> paths.put(name, rankedSegments(ranking)));
        List<RankedSegment> fusedRanking = rankedSegments(value.get("fusedRanking"));
        List<RankedSegment> filterRanking = rankedSegments(value.get("filterRanking"));
        List<RankedSegment> finalSources = rankedSegments(value.get("finalSources"));
        validateCallCounts(value.get("callCounts"));

        double actualAp = score(value.get("actualRetrievalAP@10"));
        double actualNdcg = score(value.get("actualNDCG@10"));
        Map<String, Object> oracle = stringMap(
                value.get("oracleRecoverableUpperBound"),
                "CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        if (!oracle.keySet().equals(ORACLE_SCORE_FIELDS)) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        double oracleAp = score(oracle.get("RetrievalAP@10"));
        double oracleNdcg = score(oracle.get("nDCG@10"));

        CaseInput input = new CaseInput(
                queryId,
                familyId,
                language,
                split,
                identifierShape,
                target,
                originalContainsIdentifier,
                retrievalContainsIdentifier,
                fullPathPresence,
                extractorContainsIdentifier,
                exactIdentifierTermEmitted,
                exactIdentifierPredicate,
                documentFieldsConsistent,
                keywordReturnedAnchor,
                finalReturnedAnchor,
                paths,
                fusedRanking,
                filterRanking,
                finalSources,
                Map.of(),
                List.of(),
                requiredArtifactString(value, "contextSha256"),
                contextEmpty,
                behaviorUnchanged);
        CaseEvidence recomputed = classify(input);
        RootCause declared;
        try {
            declared = RootCause.valueOf(requiredArtifactString(value, "classification"));
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID", failure);
        }
        if (declared != recomputed.classification()) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        return new CaseEvidence(
                input,
                declared,
                new RagMetrics.Scores(0.0D, 0.0D, 0.0D, actualNdcg, actualAp),
                new RagMetrics.Scores(0.0D, 0.0D, 0.0D, oracleNdcg, oracleAp));
    }

    private static void validateCaseFamilyStructure(List<CaseEvidence> cases) {
        Map<String, List<CaseEvidence>> families = cases.stream()
                .collect(Collectors.groupingBy(
                        item -> item.input().familyId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, Integer> targetShapes = new LinkedHashMap<>();
        for (Map.Entry<String, List<CaseEvidence>> entry : families.entrySet()) {
            List<CaseEvidence> family = entry.getValue();
            if (family.size() != 2
                    || !family.stream().map(item -> item.input().language())
                    .collect(Collectors.toSet()).equals(Set.of("zh", "en"))) {
                throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
            }
            boolean expectedTarget = entry.getKey().startsWith("c5s-t");
            boolean noIdentifierControl = entry.getKey().startsWith("c5s-n");
            boolean identifierControl = entry.getKey().startsWith("c5s-u");
            for (CaseEvidence evidence : family) {
                CaseInput input = evidence.input();
                if (!"selection".equals(input.split())
                        || !input.queryId().equals(
                        input.familyId() + "-" + input.language())
                        || input.target() != expectedTarget) {
                    throw new IllegalStateException(
                            "CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
                }
                if (noIdentifierControl
                        && (!"none".equals(input.identifierShape())
                        || input.originalContainsIdentifier()
                        || input.retrievalContainsIdentifier()
                        || input.extractorContainsIdentifier()
                        || input.fullPathIdentifierPresence().stream()
                        .anyMatch(Boolean::booleanValue))) {
                    throw new IllegalStateException(
                            "CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
                }
                if (identifierControl
                        && (!input.originalContainsIdentifier()
                        || !input.retrievalContainsIdentifier()
                        || !input.extractorContainsIdentifier()
                        || input.fullPathIdentifierPresence().isEmpty()
                        || !input.fullPathIdentifierPresence().get(0))) {
                    throw new IllegalStateException(
                            "CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
                }
            }
            if (expectedTarget) {
                Set<String> shapes = family.stream()
                        .map(item -> item.input().identifierShape())
                        .collect(Collectors.toSet());
                if (shapes.size() != 1 || !IDENTIFIER_SHAPES.containsAll(shapes)) {
                    throw new IllegalStateException(
                            "CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
                }
                targetShapes.merge(shapes.iterator().next(), 1, Integer::sum);
            }
        }
        if (!targetShapes.equals(Map.of(
                "numeric-token", 2,
                "doc-prefix", 2,
                "han-zero-padded", 2))) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
    }

    private static Set<String> selectionQueryIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (String type : List.of("t", "n", "u")) {
            int count = "t".equals(type) ? 6 : 2;
            for (int index = 1; index <= count; index++) {
                String family = "c5s-" + type + String.format("%02d", index);
                ids.add(family + "-zh");
                ids.add(family + "-en");
            }
        }
        return Set.copyOf(ids);
    }

    private static List<RankedSegment> rankedSegments(Object rawValue) {
        if (!(rawValue instanceof List<?> values)) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        List<RankedSegment> ranking = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Map<String, Object> value = stringMap(
                    values.get(index), "CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
            if (!value.keySet().equals(RANKED_SEGMENT_FIELDS)
                    || intValue(value.get("rank"), -1) != index + 1) {
                throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
            }
            double segmentScore = finiteDouble(value.get("score"));
            ranking.add(new RankedSegment(
                    requiredArtifactString(value, "segmentId"), index + 1, segmentScore));
        }
        return List.copyOf(ranking);
    }

    private static void validateCallCounts(Object rawValue) {
        Map<String, Object> counts = stringMap(
                rawValue, "CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        if (!counts.keySet().equals(CALL_COUNT_FIELDS)
                || counts.values().stream().anyMatch(value -> longValue(value, -1L) < 0L)) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
    }

    private static List<Boolean> booleanList(Object rawValue) {
        if (!(rawValue instanceof List<?> values) || values.stream()
                .anyMatch(value -> !(value instanceof Boolean))) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        return values.stream().map(Boolean.class::cast).toList();
    }

    private static void requireSha256(Map<String, Object> value, String field) {
        if (!validSha256(stringValue(value.get(field)))) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
    }

    private static String requiredArtifactString(Map<String, Object> value, String field) {
        String result = stringValue(value.get(field));
        if (result == null || result.isBlank()) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        return result;
    }

    private static boolean requiredArtifactBoolean(Map<String, Object> value, String field) {
        Object result = value.get(field);
        if (!(result instanceof Boolean booleanValue)) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        return booleanValue;
    }

    private static int requiredNonNegativeInt(Map<String, Object> value, String field) {
        int result = intValue(value.get(field), -1);
        if (result < 0) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        return result;
    }

    private static double score(Object value) {
        double result = finiteDouble(value);
        if (result < 0.0D || result > 1.0D) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        return result;
    }

    private static double finiteDouble(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_PAYLOAD_INVALID");
        }
        return number.doubleValue();
    }

    private static int intValue(Object value, int fallback) {
        if (!(value instanceof Number number)) {
            return fallback;
        }
        double candidate = number.doubleValue();
        int result = number.intValue();
        return Double.isFinite(candidate) && candidate == result ? result : fallback;
    }

    private static long longValue(Object value, long fallback) {
        if (!(value instanceof Number number)) {
            return fallback;
        }
        double candidate = number.doubleValue();
        long result = number.longValue();
        return Double.isFinite(candidate) && candidate == result ? result : fallback;
    }

    private static boolean numberEquals(Object value, double expected) {
        return value instanceof Number number
                && Double.compare(number.doubleValue(), expected) == 0;
    }

    private static Map<String, Object> stringMap(Object rawValue, String errorCode) {
        if (!(rawValue instanceof Map<?, ?> raw)) {
            throw new IllegalStateException(errorCode);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalStateException(errorCode);
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static Set<String> joinedFields(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.addAll(right);
        return Set.copyOf(result);
    }

    private static void rejectForbiddenFields(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_FORBIDDEN_FIELD");
                }
                if (FORBIDDEN_ARTIFACT_FIELDS.contains(key.toLowerCase(java.util.Locale.ROOT))) {
                    throw new IllegalStateException("CANDIDATE5_DIAGNOSTIC_FORBIDDEN_FIELD");
                }
                rejectForbiddenFields(entry.getValue());
            }
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(RagCandidate5DiagnosticSupport::rejectForbiddenFields);
        } else if (value != null && value.getClass().isArray()) {
            for (Object item : (Object[]) value) {
                rejectForbiddenFields(item);
            }
        }
    }

    private static Map<String, RagEvaluationDataset.CorpusSegment> parseCorpus(String content) {
        Map<String, RagEvaluationDataset.CorpusSegment> corpus = new LinkedHashMap<>();
        for (String line : dataLines(content)) {
            JSONObject json = JSON.parseObject(line);
            String segmentId = required(json, "segmentId");
            JSONObject metadata = json.getJSONObject("metadata");
            RagEvaluationDataset.CorpusSegment segment =
                    new RagEvaluationDataset.CorpusSegment(
                            segmentId,
                            required(json, "documentId"),
                            required(json, "content"),
                            json.getString("parentSegmentId"),
                            metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
            if (corpus.putIfAbsent(segmentId, segment) != null) {
                throw new IllegalArgumentException(
                        "CANDIDATE5_DUPLICATE_SEGMENT");
            }
        }
        return corpus;
    }

    private static List<RagEvaluationDataset.QueryCase> parseQueries(String content) {
        List<RagEvaluationDataset.QueryCase> queries = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (String line : dataLines(content)) {
            JSONObject json = JSON.parseObject(line);
            String id = required(json, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("CANDIDATE5_DUPLICATE_QUERY");
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
        return queries;
    }

    private static ParsedQrels parseQrels(String content) {
        Map<String, Map<String, Integer>> qrels = new LinkedHashMap<>();
        int count = 0;
        for (String line : dataLines(content)) {
            if (line.equals("queryId\tsegmentId\tgrade")) {
                continue;
            }
            String[] columns = line.split("\t", -1);
            if (columns.length != 3) {
                throw new IllegalArgumentException("CANDIDATE5_QREL_ROW_INVALID");
            }
            Map<String, Integer> queryQrels =
                    qrels.computeIfAbsent(columns[0], ignored -> new LinkedHashMap<>());
            if (queryQrels.putIfAbsent(
                    columns[1], Integer.parseInt(columns[2])) != null) {
                throw new IllegalArgumentException("CANDIDATE5_DUPLICATE_QREL");
            }
            count++;
        }
        Map<String, Map<String, Integer>> immutable = new LinkedHashMap<>();
        qrels.forEach((query, grades) -> immutable.put(query, Map.copyOf(grades)));
        return new ParsedQrels(Map.copyOf(immutable), count);
    }

    private static void validateDatasetIsolation(
            RagEvaluationDataset selection, RagEvaluationDataset holdout) {
        Set<String> selectionQueries = selection.queries().stream()
                .map(RagEvaluationDataset.QueryCase::id).collect(Collectors.toSet());
        Set<String> holdoutQueries = holdout.queries().stream()
                .map(RagEvaluationDataset.QueryCase::id).collect(Collectors.toSet());
        Set<String> selectionFamilies = selection.queries().stream()
                .map(RagEvaluationDataset.QueryCase::familyId).collect(Collectors.toSet());
        Set<String> holdoutFamilies = holdout.queries().stream()
                .map(RagEvaluationDataset.QueryCase::familyId).collect(Collectors.toSet());
        Set<String> selectionDocuments = selection.corpusById().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId)
                .collect(Collectors.toSet());
        Set<String> holdoutDocuments = holdout.corpusById().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId)
                .collect(Collectors.toSet());
        if (!Collections.disjoint(selectionQueries, holdoutQueries)
                || !Collections.disjoint(selectionFamilies, holdoutFamilies)
                || !Collections.disjoint(
                selection.corpusById().keySet(), holdout.corpusById().keySet())
                || !Collections.disjoint(selectionDocuments, holdoutDocuments)) {
            throw new IllegalArgumentException("CANDIDATE5_DATASET_ID_OVERLAP");
        }
    }

    private static List<String> dataLines(String content) {
        return content.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    private static String required(JSONObject json, String field) {
        String value = json.getString(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CANDIDATE5_MISSING_FIELD_" + field);
        }
        return value;
    }

    private static List<String> strings(JSONArray values) {
        return values == null ? List.of() : values.toJavaList(String.class);
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractIdentifierTerms(String query) {
        try {
            java.lang.reflect.Method method = KeywordRetriever.class
                    .getDeclaredMethod("extractIdentifierTerms", String.class);
            method.setAccessible(true);
            return List.copyOf((List<String>) method.invoke(null, query));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException(
                    "CANDIDATE5_IDENTIFIER_EXTRACTION_FAILED", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> buildSearchTerms(String query) {
        try {
            java.lang.reflect.Method method = KeywordRetriever.class
                    .getDeclaredMethod("buildSearchTerms", String.class, boolean.class);
            method.setAccessible(true);
            return List.copyOf((List<String>) method.invoke(null, query, false));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException(
                    "CANDIDATE5_SEARCH_TERM_EXTRACTION_FAILED", failure);
        }
    }

    private static void validateAnchorContentDoesNotLeakSearchTerms(
            RagEvaluationDataset dataset,
            List<RagEvaluationDataset.QueryCase> family,
            Set<String> relevantSegmentIds) {
        Set<String> identifiers = family.stream()
                .flatMap(query -> extractIdentifierTerms(query.retrievalQuery()).stream())
                .collect(Collectors.toSet());
        if (identifiers.size() != 1) {
            throw new IllegalArgumentException("CANDIDATE5_TARGET_IDENTIFIER_INVALID");
        }
        String identifier = identifiers.iterator().next();
        RagEvaluationDataset.CorpusSegment anchor = relevantSegmentIds.stream()
                .map(dataset.corpusById()::get)
                .filter(Objects::nonNull)
                .filter(segment -> {
                    Object name = segment.metadata().get("documentName");
                    return name instanceof String documentName
                            && asciiBoundaryMatches(documentName, identifier);
                })
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "CANDIDATE5_IDENTIFIER_ANCHOR_INVALID"));
        String content = anchor.content().toLowerCase(java.util.Locale.ROOT);
        boolean lexicalLeak = family.stream()
                .flatMap(query -> buildSearchTerms(query.retrievalQuery()).stream())
                .map(String::trim)
                .filter(term -> !term.isEmpty() && !identifiers.contains(term))
                .map(term -> term.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(content::contains);
        if (lexicalLeak) {
            throw new IllegalArgumentException(
                    "CANDIDATE5_ANCHOR_CONTENT_LEXICAL_LEAK");
        }
    }

    private static boolean asciiBoundaryMatches(String text, String identifier) {
        int offset = 0;
        while ((offset = text.indexOf(identifier, offset)) >= 0) {
            int before = offset - 1;
            int after = offset + identifier.length();
            boolean leftBoundary = before < 0
                    || !ASCII_ALPHANUMERIC.matcher(
                    text.substring(before, offset)).matches();
            boolean rightBoundary = after >= text.length()
                    || !ASCII_ALPHANUMERIC.matcher(
                    text.substring(after, after + 1)).matches();
            if (leftBoundary && rightBoundary) {
                return true;
            }
            offset++;
        }
        return false;
    }

    private static CaseEvidence language(List<CaseEvidence> family, String language) {
        return family.stream()
                .filter(item -> language.equals(item.input().language()))
                .findFirst()
                .orElse(null);
    }

    private static List<String> segmentIds(List<RankedSegment> ranking) {
        return ranking.stream().map(RankedSegment::segmentId).toList();
    }

    private static long parseLong(String value, String errorCode) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(errorCode, failure);
        }
    }

    private static Path testsDirectory() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return working.endsWith(Path.of("backend", "tests"))
                ? working
                : working.resolve("backend/tests");
    }

    private static Path requireDirectory(Path path, String errorCode) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(errorCode);
        }
        return normalized;
    }

    private static Path requireRegular(Path path, String errorCode) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(errorCode);
        }
        return normalized;
    }

    private static void requireFixedRuntimePaths(RuntimePaths candidate) {
        Objects.requireNonNull(candidate, "paths");
        Path freezeDirectory = candidate.freezeDirectory().toAbsolutePath().normalize();
        Path runtime = freezeDirectory.getParent();
        if (runtime == null || Files.isSymbolicLink(runtime)
                || !candidate.equals(paths(runtime))) {
            throw new IllegalStateException("CANDIDATE5_RUNTIME_PATHS_INVALID");
        }
    }

    private static String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE5_MANIFEST_READ_FAILED", failure);
        }
    }

    private static JSONObject readJson(Path path, String errorCode) {
        try {
            JSONObject value = JSON.parseObject(
                    Files.readString(path, StandardCharsets.UTF_8));
            if (value == null) {
                throw new IllegalStateException(errorCode);
            }
            return value;
        } catch (Exception failure) {
            if (failure instanceof IllegalStateException state
                    && errorCode.equals(state.getMessage())) {
                throw state;
            }
            throw new IllegalStateException(errorCode, failure);
        }
    }

    private static boolean validSha256(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    static String atomicJson(Map<String, ?> value) {
        return JSON.toJSONString(
                canonicalize(value),
                JSONWriter.Feature.PrettyFormat,
                JSONWriter.Feature.WriteNulls);
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, child) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException(
                            "CANDIDATE5_CANONICAL_JSON_KEY_INVALID");
                }
                sorted.put(stringKey, canonicalize(child));
            });
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> canonical = new ArrayList<>(list.size());
            list.forEach(child -> canonical.add(canonicalize(child)));
            return canonical;
        }
        return value;
    }

    private static void writeAtomic(Path target, Map<String, ?> value, boolean replace) {
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Path temporary = Files.createTempFile(
                    target.toAbsolutePath().getParent(),
                    target.getFileName().toString() + ".",
                    ".tmp");
            try {
                Files.writeString(temporary, atomicJson(value), StandardCharsets.UTF_8);
                if (replace) {
                    Files.move(
                            temporary,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (AtomicMoveNotSupportedException failure) {
                Files.deleteIfExists(temporary);
                throw new IllegalStateException(
                        "CANDIDATE5_ATOMIC_MOVE_UNSUPPORTED", failure);
            } catch (Exception failure) {
                Files.deleteIfExists(temporary);
                throw failure;
            }
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE5_ATOMIC_WRITE_FAILED", failure);
        }
    }

    private static void writeAtomicCreateNew(Path target, Map<String, ?> value) {
        Path temporary = null;
        boolean reserved = false;
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            temporary = Files.createTempFile(
                    target.toAbsolutePath().getParent(),
                    target.getFileName().toString() + ".",
                    ".tmp");
            Files.writeString(temporary, atomicJson(value), StandardCharsets.UTF_8);
            Files.createFile(target);
            reserved = true;
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (FileAlreadyExistsException failure) {
            deleteTemporary(temporary);
            throw new IllegalStateException("CANDIDATE5_LEDGER_ALREADY_EXISTS", failure);
        } catch (AtomicMoveNotSupportedException failure) {
            deleteTemporary(temporary);
            throw new IllegalStateException("CANDIDATE5_ATOMIC_MOVE_UNSUPPORTED", failure);
        } catch (IOException failure) {
            deleteTemporary(temporary);
            throw new IllegalStateException("CANDIDATE5_ATOMIC_WRITE_FAILED", failure);
        } finally {
            if (reserved && temporary != null && Files.exists(temporary)) {
                deleteTemporary(temporary);
            }
        }
    }

    private static void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE5_ATOMIC_WRITE_FAILED", failure);
        }
    }

    record RuntimePaths(
            Path freezeDirectory,
            Path selectionManifest,
            Path holdoutManifest,
            Path ledger,
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
            String holdoutSha256,
            List<String> auditedReads) {
    }

    record FrozenDataset(
            RagEvaluationDataset dataset,
            FrozenManifests manifests,
            String datasetHash,
            List<String> auditedReads) {
    }

    record RunHandle(
            RuntimePaths paths,
            String selectionManifestSha256,
            String holdoutManifestSha256,
            String startedAt) {
    }

    record RankedSegment(String segmentId, int rank, double score) {
        Map<String, Object> toMap() {
            return Map.of("segmentId", segmentId, "rank", rank, "score", score);
        }
    }

    record CaseInput(
            String queryId,
            String familyId,
            String language,
            String split,
            String identifierShape,
            boolean target,
            boolean originalContainsIdentifier,
            boolean retrievalContainsIdentifier,
            List<Boolean> fullPathIdentifierPresence,
            boolean extractorContainsIdentifier,
            boolean identifierTermEmitted,
            boolean exactIdentifierPredicate,
            boolean documentFieldsConsistent,
            boolean keywordReturnedAnchor,
            boolean finalReturnedAnchor,
            Map<String, List<RankedSegment>> paths,
            List<RankedSegment> fusedRanking,
            List<RankedSegment> filterRanking,
            List<RankedSegment> finalSources,
            Map<String, Integer> qrels,
            List<RankedSegment> oracleRecoverableSources,
            String contextSha256,
            boolean contextEmpty,
            boolean behaviorUnchanged) {

        CaseInput {
            fullPathIdentifierPresence = List.copyOf(fullPathIdentifierPresence);
            Map<String, List<RankedSegment>> copiedPaths = new LinkedHashMap<>();
            paths.forEach((name, ranking) ->
                    copiedPaths.put(name, List.copyOf(ranking)));
            paths = Map.copyOf(copiedPaths);
            fusedRanking = List.copyOf(fusedRanking);
            filterRanking = List.copyOf(filterRanking);
            finalSources = List.copyOf(finalSources);
            qrels = Map.copyOf(qrels);
            oracleRecoverableSources = List.copyOf(oracleRecoverableSources);
        }
    }

    record CaseEvidence(
            CaseInput input,
            RootCause classification,
            RagMetrics.Scores actualScores,
            RagMetrics.Scores oracleRecoverableUpperBound) {

        boolean target() {
            return input.target();
        }

        boolean behaviorUnchanged() {
            return input.behaviorUnchanged();
        }

        CaseEvidence withOracleRecoverableUpperBound(RagMetrics.Scores replacement) {
            return new CaseEvidence(input, classification, actualScores, replacement);
        }

        Map<String, Object> toMap(
                String originalQuerySha256,
                String retrievalQuerySha256,
                int extractedIdentifierCount,
                String extractedIdentifierHash,
                int searchTermCount,
                String searchTermsHash,
                String sqlShapeHash,
                int identifierPredicateCount,
                Map<String, Long> callCounts) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("queryId", input.queryId());
            value.put("familyId", input.familyId());
            value.put("language", input.language());
            value.put("split", input.split());
            value.put("identifierShape", input.identifierShape());
            value.put("target", input.target());
            value.put("originalQuerySha256", originalQuerySha256);
            value.put("retrievalQuerySha256", retrievalQuerySha256);
            value.put("originalContainsIdentifier", input.originalContainsIdentifier());
            value.put("retrievalContainsIdentifier", input.retrievalContainsIdentifier());
            value.put("fullPathIdentifierPresence", input.fullPathIdentifierPresence());
            value.put("extractorContainsIdentifier", input.extractorContainsIdentifier());
            value.put("extractedIdentifierCount", extractedIdentifierCount);
            value.put("extractedIdentifierHash", extractedIdentifierHash);
            value.put("searchTermCount", searchTermCount);
            value.put("searchTermsHash", searchTermsHash);
            value.put("exactIdentifierTermEmitted", input.identifierTermEmitted());
            value.put("exactIdentifierPredicate", input.exactIdentifierPredicate());
            value.put("documentFieldsConsistent", input.documentFieldsConsistent());
            value.put("keywordReturnedAnchor", input.keywordReturnedAnchor());
            value.put("finalReturnedAnchor", input.finalReturnedAnchor());
            value.put("behaviorUnchanged", input.behaviorUnchanged());
            value.put("sqlShapeHash", sqlShapeHash);
            value.put("identifierPredicateCount", identifierPredicateCount);
            value.put("classification", classification.name());
            Map<String, Object> pathValues = new LinkedHashMap<>();
            input.paths().forEach((name, ranking) ->
                    pathValues.put(name, ranking.stream()
                            .map(RankedSegment::toMap).toList()));
            value.put("paths", pathValues);
            value.put("fusedRanking", input.fusedRanking().stream()
                    .map(RankedSegment::toMap).toList());
            value.put("filterRanking", input.filterRanking().stream()
                    .map(RankedSegment::toMap).toList());
            value.put("finalSources", input.finalSources().stream()
                    .map(RankedSegment::toMap).toList());
            value.put("contextSha256", input.contextSha256());
            value.put("contextEmpty", input.contextEmpty());
            value.put("callCounts", Map.copyOf(callCounts));
            value.put("actualRetrievalAP@10", actualScores.retrievalApAt10());
            value.put("actualNDCG@10", actualScores.ndcgAt10());
            value.put("oracleRecoverableUpperBound", Map.of(
                    "RetrievalAP@10", oracleRecoverableUpperBound.retrievalApAt10(),
                    "nDCG@10", oracleRecoverableUpperBound.ndcgAt10()));
            return value;
        }
    }

    enum RootCause {
        RETRIEVAL_MISS_FIXTURE_INCONSISTENT,
        IDENTIFIER_EXTRACTION_MISS,
        KEYWORD_TERM_NOT_EMITTED,
        KEYWORD_SQL_IDENTIFIER_MISS,
        OTHER_RETRIEVAL_PATH_MISS,
        NONE
    }

    private record DatasetFiles(
            RagEvaluationDataset dataset,
            int qrelCount,
            Map<String, ResourceHash> resources) {
    }

    private record BaseCorpus(
            Map<String, RagEvaluationDataset.CorpusSegment> segments,
            ResourceHash resource) {
    }

    private record ParsedQrels(
            Map<String, Map<String, Integer>> qrels,
            int count) {
    }

    private record Qualification(int familyCount, Set<String> identifierShapes) {
    }

    private record ManifestSnapshot(Manifest manifest, String sha256) {
    }
}
