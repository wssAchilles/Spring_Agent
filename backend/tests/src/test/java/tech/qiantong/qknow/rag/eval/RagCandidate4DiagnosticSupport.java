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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class RagCandidate4DiagnosticSupport {

    static final String FREEZE_PROPERTY = "rag.eval.candidate4.freeze";
    static final String DIAGNOSTIC_PROPERTY = "rag.eval.candidate4.diagnostic";
    static final String HOLDOUT_DIRECTORY_PROPERTY = "rag.eval.candidate4.holdout-dir";
    static final long SELECTION_KB_ID = 9_940_000L;
    static final long SELECTION_SEGMENT_ID_MIN = 9_940_001L;
    static final long SELECTION_SEGMENT_ID_MAX = 9_944_999L;
    static final long SELECTION_DOCUMENT_ID_MIN = 9_945_000L;
    static final long SELECTION_DOCUMENT_ID_MAX = 9_949_999L;

    private static final String GENERATOR = "candidate4-static-fixture-v1";
    private static final int GENERATOR_VERSION = 1;
    private static final long GENERATOR_SEED = 20260716L;
    static final long HOLDOUT_SEGMENT_ID_MIN = 9_950_001L;
    static final long HOLDOUT_SEGMENT_ID_MAX = 9_954_999L;
    private static final Set<String> SENTINEL_SEGMENT_IDS = Set.of(
            "990001", "990002", "990003", "990004", "990005");
    private static final String MANIFEST_VERSION = "1";
    private static final Set<String> MANIFEST_FIELDS = Set.of(
            "manifestVersion", "dataset", "freezeStatus", "generator", "version", "seed",
            "resources", "counts", "datasetHash");
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
    private static final Set<String> DIAGNOSTIC_DECISIONS = Set.of(
            "PROCEED_TO_IDENTIFIER_ANCHOR_DESIGN",
            "STOP_IDENTIFIER_ANCHOR_UNSUPPORTED");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final RagMetrics.Scores ZERO_SCORES =
            new RagMetrics.Scores(0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

    private RagCandidate4DiagnosticSupport() {
    }

    static RuntimePaths paths(Path ragEvalRuntime) {
        Path runtime = Objects.requireNonNull(ragEvalRuntime, "ragEvalRuntime")
                .toAbsolutePath().normalize();
        Path freeze = runtime.resolve("candidate4-freeze");
        return new RuntimePaths(
                freeze,
                freeze.resolve("selection-manifest.json"),
                freeze.resolve("holdout-manifest.json"),
                freeze.resolve("selection-ledger.json"),
                runtime.resolve("candidate4-calibration-diagnostic.json"));
    }

    static void requireSelectionJobProperties() {
        if (System.getProperty(HOLDOUT_DIRECTORY_PROPERTY) != null) {
            throw new IllegalStateException("CANDIDATE4_HOLDOUT_ACCESS_FORBIDDEN");
        }
    }

    static int enabledDiagnosticCount(
            boolean identifierDiagnostic,
            boolean candidate2Diagnostic,
            boolean candidate3Diagnostic,
            boolean candidate4Diagnostic) {
        return (identifierDiagnostic ? 1 : 0)
                + (candidate2Diagnostic ? 1 : 0)
                + (candidate3Diagnostic ? 1 : 0)
                + (candidate4Diagnostic ? 1 : 0);
    }

    static void clearDiagnostic(RuntimePaths paths) {
        requireFixedRuntimePaths(paths);
        try {
            Files.deleteIfExists(paths.diagnostic());
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE4_DIAGNOSTIC_CLEANUP_FAILED", failure);
        }
    }

    static void requireSelectionRunAvailable(RuntimePaths paths) {
        requireFixedRuntimePaths(paths);
        if (!Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        JSONObject existing = readJson(paths.ledger(), "CANDIDATE4_LEDGER_INVALID");
        String status = existing.getString("status");
        if ("RUNNING".equals(status)) {
            throw new IllegalStateException("INVALID_INCOMPLETE_PRIOR_RUN");
        }
        if ("COMPLETED".equals(status)) {
            throw new IllegalStateException("CANDIDATE4_SELECTION_ALREADY_COMPLETED");
        }
        throw new IllegalStateException("CANDIDATE4_LEDGER_INVALID");
    }

    static FrozenManifests loadFrozenManifests(RuntimePaths paths) {
        Objects.requireNonNull(paths, "paths");
        requireFixedRuntimePaths(paths);
        Path freezeDirectory = paths.freezeDirectory();
        if (Files.isSymbolicLink(freezeDirectory)
                || !Files.isDirectory(freezeDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE4_FREEZE_DIRECTORY_INVALID");
        }
        ManifestSnapshot selection = readManifest(
                paths.selectionManifest(), "candidate4-selection", "FROZEN");
        ManifestSnapshot holdout = readManifest(
                paths.holdoutManifest(), "candidate4-holdout", "FROZEN_NOT_BLIND");
        return new FrozenManifests(
                selection.manifest(),
                selection.sha256(),
                holdout.manifest(),
                holdout.sha256(),
                List.of("selection-manifest.json", "holdout-manifest.json"));
    }

    static FrozenManifests freezeDatasets(
            Path runtime,
            Path selectionDirectory,
            Path holdoutDirectory,
            Path baseCorpus) {
        RuntimePaths paths = paths(runtime);
        if (Files.exists(paths.ledger(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE4_SELECTION_ALREADY_STARTED");
        }
        DatasetFiles selection = readDatasetFiles(selectionDirectory, false);
        DatasetFiles holdout = readDatasetFiles(holdoutDirectory, true);
        BaseCorpus base = readBaseCorpus(baseCorpus);
        validateDatasetIsolation(selection.dataset(), holdout.dataset());
        RagEvaluationDataset mergedSelection = mergeBaseDistractors(
                selection.dataset(), base.segments());
        long selectionDocumentCapacity = SELECTION_DOCUMENT_ID_MAX
                - SELECTION_DOCUMENT_ID_MIN + 1;
        long mergedDocumentCount = mergedSelection.corpusById().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId).distinct().count();
        if (mergedDocumentCount > selectionDocumentCapacity) {
            throw new IllegalArgumentException("CANDIDATE4_DOCUMENT_RANGE_EXHAUSTED");
        }
        Manifest selectionManifest = buildManifest(selection, base, false);
        Manifest holdoutManifest = buildManifest(holdout, base, true);
        Map<String, Object> selectionValue = manifestMap(selectionManifest);
        Map<String, Object> holdoutValue = manifestMap(holdoutManifest);
        boolean selectionExists = Files.exists(
                paths.selectionManifest(), LinkOption.NOFOLLOW_LINKS);
        boolean holdoutExists = Files.exists(
                paths.holdoutManifest(), LinkOption.NOFOLLOW_LINKS);
        if (selectionExists || holdoutExists) {
            if (!selectionExists || !holdoutExists) {
                throw new IllegalStateException("CANDIDATE4_FREEZE_INCOMPLETE");
            }
            FrozenManifests existing = loadFrozenManifests(paths);
            if (existing.selection().equals(selectionManifest)
                    && existing.holdout().equals(holdoutManifest)
                    && atomicJson(selectionValue).equals(readUtf8(paths.selectionManifest()))
                    && atomicJson(holdoutValue).equals(readUtf8(paths.holdoutManifest()))) {
                return existing;
            }
            throw new IllegalStateException("CANDIDATE4_FREEZE_ALREADY_FROZEN");
        }
        writeAtomic(paths.selectionManifest(), selectionValue, false);
        writeAtomic(paths.holdoutManifest(), holdoutValue, false);
        return loadFrozenManifests(paths);
    }

    static FrozenSelection loadFrozenSelection(
            RuntimePaths paths, Path selectionDirectory, Path baseCorpus) {
        requireSelectionJobProperties();
        FrozenManifests manifests = loadFrozenManifests(paths);
        DatasetFiles selection = readDatasetFiles(selectionDirectory, false);
        BaseCorpus base = readBaseCorpus(baseCorpus);
        Manifest current = buildManifest(selection, base, false);
        if (!current.equals(manifests.selection())) {
            throw new IllegalStateException("CANDIDATE4_SELECTION_RESOURCE_HASH_MISMATCH");
        }
        RagEvaluationDataset merged = mergeBaseDistractors(selection.dataset(), base.segments());
        return new FrozenSelection(
                merged,
                manifests,
                current.datasetHash(),
                List.of(
                        "selection-manifest.json", "holdout-manifest.json",
                        "candidate4-selection/corpus.jsonl",
                        "candidate4-selection/queries.jsonl",
                        "candidate4-selection/qrels.tsv",
                        "base-distractor/corpus.jsonl"));
    }

    static FrozenSelection loadFormalFrozenSelection(Path runtime) {
        Path testsDirectory = testsDirectory();
        return loadFrozenSelection(
                paths(runtime),
                testsDirectory.resolve("src/test/resources/rag-eval/candidate4-selection"),
                testsDirectory.resolve("src/test/resources/rag-eval/corpus.jsonl"));
    }

    static RunHandle beginSelectionRun(RuntimePaths paths, FrozenManifests manifests) {
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(manifests, "manifests");
        requireFixedRuntimePaths(paths);
        requireSelectionJobProperties();
        requireSelectionRunAvailable(paths);
        Map<String, Object> running = new LinkedHashMap<>();
        running.put("status", "RUNNING");
        running.put("selectionManifestSha256", manifests.selectionSha256());
        running.put("holdoutManifestSha256", manifests.holdoutSha256());
        running.put("startedAt", Instant.now().toString());
        running.put("artifactSha256", null);
        writeAtomic(paths.ledger(), running, false);
        return new RunHandle(
                paths,
                manifests.selectionSha256(),
                manifests.holdoutSha256(),
                running.get("startedAt").toString());
    }

    static String writeDiagnosticAndComplete(
            RuntimePaths paths, RunHandle handle, Map<String, ?> freshArtifact) {
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(freshArtifact, "freshArtifact");
        if (!paths.equals(handle.paths())) {
            throw new IllegalArgumentException("CANDIDATE4_RUN_PATH_MISMATCH");
        }
        JSONObject ledger = readJson(paths.ledger(), "CANDIDATE4_LEDGER_INVALID");
        if (!"RUNNING".equals(ledger.getString("status"))) {
            throw new IllegalStateException("CANDIDATE4_LEDGER_NOT_RUNNING");
        }
        if (!handle.selectionManifestSha256().equals(
                ledger.getString("selectionManifestSha256"))
                || !handle.holdoutManifestSha256().equals(
                ledger.getString("holdoutManifestSha256"))) {
            throw new IllegalStateException("CANDIDATE4_LEDGER_MANIFEST_MISMATCH");
        }
        FrozenManifests manifests = loadFrozenManifests(paths);
        if (!handle.selectionManifestSha256().equals(manifests.selectionSha256())
                || !handle.holdoutManifestSha256().equals(manifests.holdoutSha256())) {
            throw new IllegalStateException("CANDIDATE4_RUN_MANIFEST_HASH_MISMATCH");
        }
        validateDiagnosticArtifact(freshArtifact, manifests);

        Map<String, Object> artifact = new LinkedHashMap<>();
        freshArtifact.forEach(artifact::put);
        writeAtomic(paths.diagnostic(), artifact, true);
        validateDiagnosticArtifact(
                readJson(paths.diagnostic(), "CANDIDATE4_DIAGNOSTIC_INVALID"), manifests);
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
        JSONObject ledger = readJson(paths.ledger(), "CANDIDATE4_LEDGER_INVALID");
        if (!"COMPLETED".equals(ledger.getString("status"))) {
            throw new IllegalStateException("CANDIDATE4_LEDGER_NOT_COMPLETED");
        }
        FrozenManifests manifests = loadFrozenManifests(paths);
        if (!manifests.selectionSha256().equals(
                ledger.getString("selectionManifestSha256"))
                || !manifests.holdoutSha256().equals(
                ledger.getString("holdoutManifestSha256"))) {
            throw new IllegalStateException("CANDIDATE4_LEDGER_MANIFEST_MISMATCH");
        }
        if (!Files.isRegularFile(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(paths.diagnostic())) {
            throw new IllegalStateException("CANDIDATE4_DIAGNOSTIC_MISSING");
        }
        String expected = ledger.getString("artifactSha256");
        String actual = ShadowContractSupport.sha256(paths.diagnostic());
        if (!validSha256(expected) || !expected.equals(actual)) {
            throw new IllegalStateException("CANDIDATE4_ARTIFACT_HASH_MISMATCH");
        }
        validateDiagnosticArtifact(
                readJson(paths.diagnostic(), "CANDIDATE4_DIAGNOSTIC_INVALID"), manifests);
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
        if (input.exactIdentifierRelevantIds().isEmpty()) {
            classification = RootCause.NONE;
        } else if (!containsAny(input.fused(), input.exactIdentifierRelevantIds())) {
            classification = RootCause.RETRIEVAL_MISS;
        } else if (!containsAny(input.filterOutput(), input.exactIdentifierRelevantIds())) {
            classification = RootCause.FILTER_DELETION;
        } else if (!containsAny(input.colbertOutput(), input.exactIdentifierRelevantIds())
                && containsAny(input.fullColbert(), input.exactIdentifierRelevantIds())) {
            classification = RootCause.COLBERT_IDENTIFIER_SUPPRESSION;
        } else if (containsAny(input.colbertOutput(), input.exactIdentifierRelevantIds())
                && !containsAny(input.finalSources(), input.exactIdentifierRelevantIds())) {
            classification = RootCause.FINAL_REDUNDANCY;
        } else {
            classification = RootCause.NONE;
        }
        RagMetrics.Scores actual = input.qrels().isEmpty()
                ? ZERO_SCORES
                : RagMetrics.evaluate(input.qrels(), segmentIds(input.finalSources()));
        RagMetrics.Scores counterfactual = input.qrels().isEmpty()
                ? ZERO_SCORES
                : RagMetrics.evaluate(input.qrels(), segmentIds(input.counterfactualSources()));
        return new CaseEvidence(input, classification, actual, counterfactual);
    }

    static String decide(List<CaseEvidence> cases) {
        List<CaseEvidence> targets = cases.stream().filter(CaseEvidence::target).toList();
        List<CaseEvidence> controls = cases.stream().filter(item -> !item.target()).toList();
        boolean proceed = targets.size() == 12
                && controls.size() == 4
                && targets.stream().allMatch(item ->
                item.classification() == RootCause.COLBERT_IDENTIFIER_SUPPRESSION
                        && containsAny(
                        item.input().counterfactualSources(),
                        item.input().exactIdentifierRelevantIds())
                        && item.counterfactualScores().retrievalApAt10()
                        > item.actualScores().retrievalApAt10()
                        && item.counterfactualScores().ndcgAt10()
                        > item.actualScores().ndcgAt10())
                && controls.stream().allMatch(CaseEvidence::behaviorUnchanged);
        return proceed
                ? "PROCEED_TO_IDENTIFIER_ANCHOR_DESIGN"
                : "STOP_IDENTIFIER_ANCHOR_UNSUPPORTED";
    }

    static void freezeFormalDatasets(Path runtime, Path holdoutDirectory) {
        Path testsDirectory = testsDirectory();
        freezeDatasets(
                runtime,
                testsDirectory.resolve("src/test/resources/rag-eval/candidate4-selection"),
                holdoutDirectory,
                testsDirectory.resolve("src/test/resources/rag-eval/corpus.jsonl"));
    }

    private static Path testsDirectory() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return working.endsWith(Path.of("backend", "tests"))
                ? working
                : working.resolve("backend/tests");
    }

    private static DatasetFiles readDatasetFiles(Path directory, boolean holdout) {
        Path normalized = requireDirectory(directory, "CANDIDATE4_DATASET_DIRECTORY_INVALID");
        Path corpusPath = requireRegular(
                normalized.resolve("corpus.jsonl"), "CANDIDATE4_DATASET_RESOURCE_INVALID");
        Path queriesPath = requireRegular(
                normalized.resolve("queries.jsonl"), "CANDIDATE4_DATASET_RESOURCE_INVALID");
        Path qrelsPath = requireRegular(
                normalized.resolve("qrels.tsv"), "CANDIDATE4_DATASET_RESOURCE_INVALID");
        try {
            Map<String, RagEvaluationDataset.CorpusSegment> corpus =
                    parseCorpus(Files.readString(corpusPath, StandardCharsets.UTF_8));
            List<RagEvaluationDataset.QueryCase> queries =
                    parseQueries(Files.readString(queriesPath, StandardCharsets.UTF_8));
            ParsedQrels parsedQrels = parseQrels(
                    Files.readString(qrelsPath, StandardCharsets.UTF_8));
            RagEvaluationDataset dataset = new RagEvaluationDataset(
                    corpus, queries, parsedQrels.qrels());
            RagEvaluationDatasetLoader.validate(dataset);
            validateCandidate4Shape(dataset, parsedQrels.count(), holdout);
            Map<String, ResourceHash> resources = new LinkedHashMap<>();
            resources.put("corpus", new ResourceHash(
                    "corpus.jsonl", ShadowContractSupport.sha256(corpusPath)));
            resources.put("queries", new ResourceHash(
                    "queries.jsonl", ShadowContractSupport.sha256(queriesPath)));
            resources.put("qrels", new ResourceHash(
                    "qrels.tsv", ShadowContractSupport.sha256(qrelsPath)));
            return new DatasetFiles(dataset, parsedQrels.count(), Map.copyOf(resources));
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE4_DATASET_READ_FAILED", failure);
        }
    }

    private static BaseCorpus readBaseCorpus(Path path) {
        Path source = requireRegular(path, "CANDIDATE4_BASE_CORPUS_INVALID");
        try {
            Map<String, RagEvaluationDataset.CorpusSegment> parsed =
                    parseCorpus(Files.readString(source, StandardCharsets.UTF_8));
            Map<String, RagEvaluationDataset.CorpusSegment> active = new LinkedHashMap<>();
            parsed.forEach((id, segment) -> {
                if (!SENTINEL_SEGMENT_IDS.contains(id)) {
                    active.put(id, segment);
                }
            });
            for (RagEvaluationDataset.CorpusSegment segment : active.values()) {
                if (segment.parentSegmentId() != null
                        && !active.containsKey(segment.parentSegmentId())) {
                    throw new IllegalArgumentException(
                            "CANDIDATE4_BASE_PARENT_OUTSIDE_CORPUS");
                }
            }
            return new BaseCorpus(
                    Map.copyOf(active),
                    new ResourceHash("corpus.jsonl", ShadowContractSupport.sha256(source)));
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE4_BASE_CORPUS_READ_FAILED", failure);
        }
    }

    private static Manifest buildManifest(
            DatasetFiles files, BaseCorpus base, boolean holdout) {
        Map<String, ResourceHash> resources = new LinkedHashMap<>(files.resources());
        resources.put("baseDistractor", base.resource());
        Set<String> documents = files.dataset().corpusById().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> baseDocuments = base.segments().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("familyCount", (int) files.dataset().queries().stream()
                .map(RagEvaluationDataset.QueryCase::familyId).distinct().count());
        counts.put("queryCount", files.dataset().queries().size());
        counts.put("documentCount", documents.size() + baseDocuments.size());
        counts.put("segmentCount", files.dataset().corpusById().size() + base.segments().size());
        counts.put("qrelCount", files.qrelCount());
        String datasetHash = manifestDatasetHash(
                GENERATOR, GENERATOR_VERSION, GENERATOR_SEED, resources, counts);
        return new Manifest(
                holdout ? "candidate4-holdout" : "candidate4-selection",
                holdout ? "FROZEN_NOT_BLIND" : "FROZEN",
                GENERATOR,
                GENERATOR_VERSION,
                GENERATOR_SEED,
                Map.copyOf(resources),
                Map.copyOf(counts),
                datasetHash);
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
        value.put("datasetHash", manifest.datasetHash());
        return value;
    }

    private static Map<String, RagEvaluationDataset.CorpusSegment> parseCorpus(String content) {
        Map<String, RagEvaluationDataset.CorpusSegment> corpus = new LinkedHashMap<>();
        for (String line : dataLines(content)) {
            JSONObject json = JSON.parseObject(line);
            String segmentId = required(json, "segmentId");
            JSONObject metadata = json.getJSONObject("metadata");
            RagEvaluationDataset.CorpusSegment segment = new RagEvaluationDataset.CorpusSegment(
                    segmentId,
                    required(json, "documentId"),
                    required(json, "content"),
                    json.getString("parentSegmentId"),
                    metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
            if (corpus.putIfAbsent(segmentId, segment) != null) {
                throw new IllegalArgumentException("Duplicate corpus segment: " + segmentId);
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
                throw new IllegalArgumentException("Duplicate query: " + id);
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
                throw new IllegalArgumentException("Invalid qrels row");
            }
            Map<String, Integer> queryQrels = qrels.computeIfAbsent(
                    columns[0], ignored -> new LinkedHashMap<>());
            if (queryQrels.putIfAbsent(columns[1], Integer.parseInt(columns[2])) != null) {
                throw new IllegalArgumentException("Duplicate qrel: " + columns[0] + "/" + columns[1]);
            }
            count++;
        }
        Map<String, Map<String, Integer>> immutable = new LinkedHashMap<>();
        qrels.forEach((query, grades) -> immutable.put(query, Map.copyOf(grades)));
        return new ParsedQrels(Map.copyOf(immutable), count);
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
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return value;
    }

    private static List<String> strings(JSONArray values) {
        return values == null ? List.of() : values.toJavaList(String.class);
    }

    private static void validateCandidate4Shape(
            RagEvaluationDataset dataset, int qrelCount, boolean holdout) {
        String prefix = holdout ? "c4h-" : "c4s-";
        String split = holdout ? "holdout" : "selection";
        int targetFamilies = holdout ? 12 : 6;
        int unanswerableFamilies = holdout ? 2 : 1;
        int noIdentifierFamilies = holdout ? 2 : 1;
        int expectedQueries = holdout ? 32 : 16;
        int expectedQrels = holdout ? 76 : 38;
        long minimumSegmentId = holdout ? HOLDOUT_SEGMENT_ID_MIN : SELECTION_SEGMENT_ID_MIN;
        long maximumSegmentId = holdout ? HOLDOUT_SEGMENT_ID_MAX : SELECTION_SEGMENT_ID_MAX;
        if (dataset.queries().size() != expectedQueries || qrelCount != expectedQrels) {
            throw new IllegalArgumentException("Candidate 4 dataset count mismatch");
        }
        if (dataset.queries().stream().anyMatch(query ->
                !query.id().startsWith(prefix) || !split.equals(query.split()))) {
            throw new IllegalArgumentException("Candidate 4 query namespace mismatch");
        }
        Map<String, List<RagEvaluationDataset.QueryCase>> families = dataset.queries().stream()
                .collect(Collectors.groupingBy(
                        RagEvaluationDataset.QueryCase::familyId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        long targets = families.keySet().stream().filter(id -> id.startsWith(prefix + "t")).count();
        long unanswerable = families.keySet().stream().filter(id -> id.startsWith(prefix + "u")).count();
        long noIdentifier = families.keySet().stream().filter(id -> id.startsWith(prefix + "n")).count();
        if (targets != targetFamilies || unanswerable != unanswerableFamilies
                || noIdentifier != noIdentifierFamilies
                || families.size() != targetFamilies + unanswerableFamilies + noIdentifierFamilies) {
            throw new IllegalArgumentException("Candidate 4 family shape mismatch");
        }
        for (Map.Entry<String, List<RagEvaluationDataset.QueryCase>> family : families.entrySet()) {
            Set<String> languages = family.getValue().stream()
                    .map(RagEvaluationDataset.QueryCase::language).collect(Collectors.toSet());
            if (family.getValue().size() != 2 || !languages.equals(Set.of("zh", "en"))) {
                throw new IllegalArgumentException("Candidate 4 bilingual family mismatch");
            }
            for (RagEvaluationDataset.QueryCase query : family.getValue()) {
                int expected = family.getKey().startsWith(prefix + "t") ? 3
                        : family.getKey().startsWith(prefix + "n") ? 1 : 0;
                if (dataset.qrelsFor(query.id()).size() != expected
                        || query.answerable() != (expected > 0)) {
                    throw new IllegalArgumentException("Candidate 4 qrel shape mismatch");
                }
            }
            boolean target = family.getKey().startsWith(prefix + "t");
            boolean unanswerableIdentifier = family.getKey().startsWith(prefix + "u");
            Set<String> familyQrels = null;
            for (RagEvaluationDataset.QueryCase query : family.getValue()) {
                List<String> identifiers = extractIdentifierTerms(query.query());
                if (target) {
                    Set<String> queryQrels = dataset.qrelsFor(query.id()).keySet();
                    if (familyQrels == null) {
                        familyQrels = Set.copyOf(queryQrels);
                    } else if (!familyQrels.equals(queryQrels)) {
                        throw new IllegalArgumentException(
                                "CANDIDATE4_FAMILY_QRELS_MISMATCH");
                    }
                    List<Pattern> patterns = identifiers.stream().map(identifier ->
                            Pattern.compile("(?<![\\p{L}\\p{N}])"
                                    + Pattern.quote(identifier)
                                    + "(?![\\p{L}\\p{N}])")).toList();
                    long exactMatches = queryQrels.stream().filter(segmentId -> {
                        RagEvaluationDataset.CorpusSegment segment =
                                dataset.corpusById().get(segmentId);
                        return segment != null && patterns.stream().anyMatch(pattern ->
                                pattern.matcher(segment.documentId()).find());
                    }).count();
                    if (identifiers.isEmpty() || exactMatches != 1) {
                        throw new IllegalArgumentException(
                                "CANDIDATE4_IDENTIFIER_ANCHOR_INVALID");
                    }
                } else if (unanswerableIdentifier && identifiers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "CANDIDATE4_IDENTIFIER_CONTROL_INVALID");
                } else if (!unanswerableIdentifier && !identifiers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "CANDIDATE4_NO_IDENTIFIER_CONTROL_INVALID");
                }
            }
        }
        for (RagEvaluationDataset.CorpusSegment segment : dataset.corpusById().values()) {
            long value = Long.parseLong(segment.segmentId());
            if (value < minimumSegmentId || value > maximumSegmentId) {
                throw new IllegalArgumentException("Candidate 4 segment ID outside reserved range");
            }
            if (segment.parentSegmentId() != null) {
                long parent = Long.parseLong(segment.parentSegmentId());
                if (parent < minimumSegmentId || parent > maximumSegmentId
                        || !dataset.corpusById().containsKey(segment.parentSegmentId())) {
                    throw new IllegalArgumentException(
                            "CANDIDATE4_PARENT_OUTSIDE_DATASET");
                }
            }
        }
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
                    "CANDIDATE4_IDENTIFIER_EXTRACTION_FAILED", failure);
        }
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
                .map(RagEvaluationDataset.CorpusSegment::documentId).collect(Collectors.toSet());
        Set<String> holdoutDocuments = holdout.corpusById().values().stream()
                .map(RagEvaluationDataset.CorpusSegment::documentId).collect(Collectors.toSet());
        if (!java.util.Collections.disjoint(selectionQueries, holdoutQueries)
                || !java.util.Collections.disjoint(selectionFamilies, holdoutFamilies)
                || !java.util.Collections.disjoint(
                selection.corpusById().keySet(), holdout.corpusById().keySet())
                || !java.util.Collections.disjoint(selectionDocuments, holdoutDocuments)) {
            throw new IllegalArgumentException("CANDIDATE4_DATASET_ID_OVERLAP");
        }
    }

    private static RagEvaluationDataset mergeBaseDistractors(
            RagEvaluationDataset selection,
            Map<String, RagEvaluationDataset.CorpusSegment> baseSegments) {
        Map<String, RagEvaluationDataset.CorpusSegment> merged =
                new LinkedHashMap<>(selection.corpusById());
        List<RagEvaluationDataset.CorpusSegment> sortedBase = baseSegments.values().stream()
                .sorted((left, right) -> Long.compare(
                        Long.parseLong(left.segmentId()), Long.parseLong(right.segmentId())))
                .toList();
        Map<String, String> remappedIds = new LinkedHashMap<>();
        for (int index = 0; index < sortedBase.size(); index++) {
            remappedIds.put(sortedBase.get(index).segmentId(),
                    String.valueOf(SELECTION_SEGMENT_ID_MIN + index));
        }
        Map<String, String> documents = new LinkedHashMap<>();
        for (RagEvaluationDataset.CorpusSegment segment : sortedBase) {
            documents.computeIfAbsent(segment.documentId(), ignored ->
                    "c4s-base-" + String.format("%03d", documents.size() + 1));
            String remappedId = remappedIds.get(segment.segmentId());
            RagEvaluationDataset.CorpusSegment copy = new RagEvaluationDataset.CorpusSegment(
                    remappedId,
                    documents.get(segment.documentId()),
                    segment.content(),
                    segment.parentSegmentId() == null
                            ? null : remappedIds.get(segment.parentSegmentId()),
                    segment.metadata());
            if (merged.putIfAbsent(remappedId, copy) != null) {
                throw new IllegalStateException("CANDIDATE4_BASE_SEGMENT_ID_COLLISION");
            }
        }
        RagEvaluationDataset dataset = new RagEvaluationDataset(
                merged, selection.queries(), selection.qrels());
        RagEvaluationDatasetLoader.validate(dataset);
        return dataset;
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

    private static boolean containsAny(List<RankedSegment> ranking, Set<String> ids) {
        return ranking.stream().map(RankedSegment::segmentId).anyMatch(ids::contains);
    }

    private static List<String> segmentIds(List<RankedSegment> ranking) {
        return ranking.stream().map(RankedSegment::segmentId).toList();
    }

    private static ManifestSnapshot readManifest(
            Path path, String dataset, String freezeStatus) {
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CANDIDATE4_MANIFEST_NOT_REGULAR");
        }
        byte[] bytes;
        JSONObject json;
        try {
            bytes = Files.readAllBytes(path);
            json = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException("CANDIDATE4_MANIFEST_INVALID", failure);
        }
        if (!json.keySet().equals(MANIFEST_FIELDS)
                || !dataset.equals(json.getString("dataset"))
                || !freezeStatus.equals(json.getString("freezeStatus"))
                || !MANIFEST_VERSION.equals(String.valueOf(json.get("manifestVersion")))
                || !GENERATOR.equals(json.getString("generator"))
                || GENERATOR_VERSION != json.getIntValue("version")
                || GENERATOR_SEED != json.getLongValue("seed")) {
            throw new IllegalStateException("CANDIDATE4_MANIFEST_INVALID");
        }
        JSONObject resources = json.getJSONObject("resources");
        JSONObject counts = json.getJSONObject("counts");
        if (resources == null || !resources.keySet().equals(RESOURCE_NAMES)
                || counts == null || !counts.keySet().equals(COUNT_FIELDS)) {
            throw new IllegalStateException("CANDIDATE4_MANIFEST_INVALID");
        }
        Map<String, ResourceHash> resourceHashes = new LinkedHashMap<>();
        for (String name : RESOURCE_NAMES) {
            JSONObject resource = resources.getJSONObject(name);
            if (resource == null || !resource.keySet().equals(RESOURCE_FIELDS)
                    || !RESOURCE_FILES.get(name).equals(resource.getString("file"))
                    || !validSha256(resource.getString("sha256"))) {
                throw new IllegalStateException("CANDIDATE4_MANIFEST_INVALID");
            }
            resourceHashes.put(name,
                    new ResourceHash(resource.getString("file"), resource.getString("sha256")));
        }
        Map<String, Integer> countValues = new LinkedHashMap<>();
        COUNT_FIELDS.forEach(name -> countValues.put(name, counts.getIntValue(name)));
        String datasetHash = json.getString("datasetHash");
        String expectedDatasetHash = manifestDatasetHash(
                json.getString("generator"),
                json.getIntValue("version"),
                json.getLongValue("seed"),
                resourceHashes,
                countValues);
        if (!validSha256(datasetHash) || !datasetHash.equals(expectedDatasetHash)) {
            throw new IllegalStateException("CANDIDATE4_MANIFEST_INVALID");
        }
        return new ManifestSnapshot(
                new Manifest(
                        json.getString("dataset"),
                        json.getString("freezeStatus"),
                        json.getString("generator"),
                        json.getIntValue("version"),
                        json.getLongValue("seed"),
                        Map.copyOf(resourceHashes),
                        Map.copyOf(countValues),
                        datasetHash),
                ShadowContractSupport.sha256(bytes));
    }

    private static void requireFixedRuntimePaths(RuntimePaths candidate) {
        Objects.requireNonNull(candidate, "paths");
        Path freezeDirectory = candidate.freezeDirectory().toAbsolutePath().normalize();
        Path runtime = freezeDirectory.getParent();
        if (runtime == null || Files.isSymbolicLink(runtime)
                || !candidate.equals(paths(runtime))) {
            throw new IllegalStateException("CANDIDATE4_RUNTIME_PATHS_INVALID");
        }
    }

    private static String manifestDatasetHash(
            String generator,
            int version,
            long seed,
            Map<String, ResourceHash> resources,
            Map<String, Integer> counts) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("generator", generator);
        evidence.put("version", version);
        evidence.put("seed", seed);
        Map<String, Object> resourceEvidence = new LinkedHashMap<>();
        resources.forEach((name, resource) -> resourceEvidence.put(name, Map.of(
                "file", resource.file(), "sha256", resource.sha256())));
        evidence.put("resources", resourceEvidence);
        evidence.put("counts", counts);
        return ShadowContractSupport.configHash(evidence);
    }

    private static void validateDiagnosticArtifact(
            Map<String, ?> artifact, FrozenManifests manifests) {
        Object configValue = artifact.get("config");
        String configHash = stringValue(artifact.get("configHash"));
        if (!(configValue instanceof Map<?, ?> config)) {
            throw new IllegalStateException("CANDIDATE4_DIAGNOSTIC_CONFIG_INVALID");
        }
        Map<String, Object> stringConfig = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : config.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalStateException("CANDIDATE4_DIAGNOSTIC_CONFIG_INVALID");
            }
            stringConfig.put(key, entry.getValue());
        }
        if (!validSha256(configHash)
                || !configHash.equals(ShadowContractSupport.configHash(stringConfig))) {
            throw new IllegalStateException("CANDIDATE4_DIAGNOSTIC_CONFIG_INVALID");
        }
        if (!manifests.selection().datasetHash().equals(
                stringValue(artifact.get("datasetHash")))
                || !manifests.selectionSha256().equals(
                stringValue(artifact.get("selectionManifestHash")))
                || !manifests.holdoutSha256().equals(
                stringValue(artifact.get("holdoutManifestHash")))
                || !"FROZEN_NOT_BLIND".equals(
                stringValue(artifact.get("holdoutFreezeStatus")))) {
            throw new IllegalStateException("CANDIDATE4_DIAGNOSTIC_MANIFEST_INVALID");
        }
        String decision = stringValue(artifact.get("decision"));
        if (!DIAGNOSTIC_DECISIONS.contains(decision)) {
            throw new IllegalStateException("CANDIDATE4_DIAGNOSTIC_DECISION_INVALID");
        }
        String status = stringValue(artifact.get("status"));
        String errorCode = stringValue(artifact.get("errorCode"));
        if ("VALID".equals(status)) {
            if (!artifact.containsKey("errorCode") || errorCode != null
                    || !(artifact.get("summary") instanceof Map<?, ?>)
                    || !(artifact.get("cases") instanceof List<?>)) {
                throw new IllegalStateException("CANDIDATE4_DIAGNOSTIC_PAYLOAD_INVALID");
            }
            return;
        }
        if (!"INVALID".equals(status)
                || errorCode == null || errorCode.isBlank()
                || artifact.containsKey("summary") || artifact.containsKey("cases")) {
            throw new IllegalStateException("CANDIDATE4_DIAGNOSTIC_PAYLOAD_INVALID");
        }
    }

    private static boolean validSha256(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private static JSONObject readJson(Path path, String errorCode) {
        try {
            return JSON.parseObject(Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException(errorCode, failure);
        }
    }

    private static String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE4_MANIFEST_READ_FAILED", failure);
        }
    }

    private static String atomicJson(Map<String, ?> value) {
        return JSON.toJSONString(value, JSONWriter.Feature.PrettyFormat,
                JSONWriter.Feature.WriteNulls);
    }

    private static void writeAtomic(Path target, Map<String, ?> value, boolean replace) {
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Path temporary = Files.createTempFile(
                    target.toAbsolutePath().getParent(), target.getFileName().toString() + ".", ".tmp");
            try {
                Files.writeString(temporary, atomicJson(value), StandardCharsets.UTF_8);
                if (replace) {
                    Files.move(temporary, target,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (AtomicMoveNotSupportedException failure) {
                Files.deleteIfExists(temporary);
                throw new IllegalStateException("CANDIDATE4_ATOMIC_MOVE_UNSUPPORTED", failure);
            } catch (Exception failure) {
                Files.deleteIfExists(temporary);
                throw failure;
            }
        } catch (IOException failure) {
            throw new IllegalStateException("CANDIDATE4_ATOMIC_WRITE_FAILED", failure);
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
            String datasetHash) {
    }

    record FrozenManifests(
            Manifest selection,
            String selectionSha256,
            Manifest holdout,
            String holdoutSha256,
            List<String> auditedReads) {
    }

    record FrozenSelection(
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
            String split,
            boolean target,
            Map<String, List<RankedSegment>> paths,
            List<RankedSegment> fused,
            List<RankedSegment> filterOutput,
            List<RankedSegment> colbertOutput,
            List<RankedSegment> fullColbert,
            List<RankedSegment> deterministicInput,
            List<RankedSegment> deterministicOutput,
            List<RankedSegment> finalSources,
            List<RankedSegment> counterfactualSources,
            Map<String, Integer> qrels,
            Set<String> exactIdentifierRelevantIds,
            String actualContextSha256,
            String counterfactualContextSha256,
            boolean actualContextEmpty,
            boolean counterfactualContextEmpty) {

        CaseInput {
            paths = Map.copyOf(paths);
            fused = List.copyOf(fused);
            filterOutput = List.copyOf(filterOutput);
            colbertOutput = List.copyOf(colbertOutput);
            fullColbert = List.copyOf(fullColbert);
            deterministicInput = List.copyOf(deterministicInput);
            deterministicOutput = List.copyOf(deterministicOutput);
            finalSources = List.copyOf(finalSources);
            counterfactualSources = List.copyOf(counterfactualSources);
            qrels = Map.copyOf(qrels);
            exactIdentifierRelevantIds = Set.copyOf(exactIdentifierRelevantIds);
        }
    }

    record CaseEvidence(
            CaseInput input,
            RootCause classification,
            RagMetrics.Scores actualScores,
            RagMetrics.Scores counterfactualScores) {

        boolean target() {
            return input.target();
        }

        boolean behaviorUnchanged() {
            return input.finalSources().equals(input.counterfactualSources())
                    && Objects.equals(input.actualContextSha256(), input.counterfactualContextSha256())
                    && input.actualContextEmpty() == input.counterfactualContextEmpty();
        }

        Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("queryId", input.queryId());
            value.put("familyId", input.familyId());
            value.put("split", input.split());
            value.put("classification", classification.name());
            Map<String, Object> pathValues = new LinkedHashMap<>();
            input.paths().forEach((name, ranking) ->
                    pathValues.put(name, ranking.stream().map(RankedSegment::toMap).toList()));
            value.put("paths", pathValues);
            value.put("fused", maps(input.fused()));
            value.put("filterOutput", maps(input.filterOutput()));
            value.put("colbertOutput", maps(input.colbertOutput()));
            value.put("detachedFullColbert", maps(input.fullColbert()));
            value.put("deterministicInput", maps(input.deterministicInput()));
            value.put("deterministicOutput", maps(input.deterministicOutput()));
            value.put("finalSources", maps(input.finalSources()));
            value.put("counterfactualSources", maps(input.counterfactualSources()));
            value.put("contextSha256", input.actualContextSha256());
            value.put("contextEmpty", input.actualContextEmpty());
            value.put("counterfactualContextSha256", input.counterfactualContextSha256());
            value.put("counterfactualContextEmpty", input.counterfactualContextEmpty());
            value.put("actualRetrievalAP@10", actualScores.retrievalApAt10());
            value.put("actualNDCG@10", actualScores.ndcgAt10());
            value.put("recoverableRetrievalAP@10", counterfactualScores.retrievalApAt10());
            value.put("recoverableNDCG@10", counterfactualScores.ndcgAt10());
            return value;
        }

        private static List<Map<String, Object>> maps(List<RankedSegment> ranking) {
            return ranking.stream().map(RankedSegment::toMap).toList();
        }
    }

    enum RootCause {
        RETRIEVAL_MISS,
        FILTER_DELETION,
        COLBERT_IDENTIFIER_SUPPRESSION,
        FINAL_REDUNDANCY,
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

    private record ManifestSnapshot(Manifest manifest, String sha256) {
    }
}
