package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RagCandidate5ReviewSupport {

    static final String REVIEW_ARM_PROPERTY = "rag.eval.candidate5.review-arm";
    static final String REVIEW_GATE_PROPERTY = "rag.eval.candidate5.review";
    static final String ALGORITHM = "document-name-boundary-predicate-v1";
    static final String SQL_POLICY = "postgres-posix-c-alnum-v1";
    static final int CASE_COUNT = 32;

    private RagCandidate5ReviewSupport() {
    }

    static Arm configuredArm() {
        String configured = System.getProperty(REVIEW_ARM_PROPERTY, "").trim();
        if (configured.isEmpty()) {
            return null;
        }
        if ("A".equals(configured) || "B".equals(configured)) {
            throw new IllegalStateException(
                    "CANDIDATE5_REVIEW_EXECUTION_DISABLED_AFTER_REJECTION");
        }
        throw new IllegalArgumentException("CANDIDATE5_REVIEW_ARM_INVALID");
    }

    static void requireArtifactsAbsent(Path runtime, Arm arm) {
        for (String fileName : arm.fileNames()) {
            if (Files.exists(runtime.resolve(fileName))) {
                throw new IllegalStateException("CANDIDATE5_REVIEW_ARTIFACT_EXISTS");
            }
        }
        if (Files.exists(arm.bundleDirectory(runtime))) {
            throw new IllegalStateException("CANDIDATE5_REVIEW_BUNDLE_EXISTS");
        }
    }

    static RagBenchmarkReport buildReport(
            RagCandidate5DiagnosticSupport.FrozenDataset frozen,
            Map<String, RagResult> results,
            Map<String, ?> config) {
        RagEvaluationDataset dataset = frozen.dataset();
        Map<String, Map<String, List<Double>>> scores = familyScores(dataset, results);
        Map<String, RagBenchmarkReport.MetricEstimate> metrics = new LinkedHashMap<>();
        scores.forEach((name, values) -> metrics.put(name, estimate(values)));
        List<RagBenchmarkReport.FailureSample> failures = new ArrayList<>();
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            if (query.answerable() && ranking(results.get(query.id())).isEmpty()) {
                failures.add(new RagBenchmarkReport.FailureSample(
                        query.id(), query.familyId(), "EMPTY_RETRIEVAL",
                        "answerable query returned no sources"));
            }
        }
        return new RagBenchmarkReport(
                frozen.datasetHash(),
                ShadowContractSupport.configHash(config),
                RagBenchmarkReport.DatasetEvidenceLevel.ENGINEERING_BASELINE,
                metrics,
                Map.of(),
                failures);
    }

    static Map<String, Map<String, List<Double>>> familyScores(
            RagEvaluationDataset dataset, Map<String, RagResult> results) {
        requireCompleteResults(dataset, results);
        Map<String, List<Double>> ap10 = new LinkedHashMap<>();
        Map<String, List<Double>> ndcg10 = new LinkedHashMap<>();
        Map<String, List<Double>> empty = new LinkedHashMap<>();
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            List<String> ranking = ranking(results.get(query.id()));
            if (!query.answerable()) {
                add(empty, query.familyId(), ranking.isEmpty() ? 1.0D : 0.0D);
                continue;
            }
            RagMetrics.Scores scores = RagMetrics.evaluate(dataset.qrelsFor(query.id()), ranking);
            add(ap10, query.familyId(), scores.retrievalApAt10());
            add(ndcg10, query.familyId(), scores.ndcgAt10());
        }
        Map<String, Map<String, List<Double>>> values = new LinkedHashMap<>();
        values.put("RetrievalAP@10", ap10);
        values.put("nDCG@10", ndcg10);
        values.put("EmptyRetrievalRate", empty);
        return values;
    }

    static void writeCompleteArm(
            Path runtime,
            Arm arm,
            RagCandidate5DiagnosticSupport.FrozenDataset frozen,
            Map<String, RagResult> results,
            RagBenchmarkReport report,
            Map<String, Object> config,
            List<String> sentinelEvidence,
            Map<String, Map<String, List<Double>>> familyScores,
            Map<String, Object> observedBudget,
            List<Map<String, Object>> dbCallTrace) throws IOException {
        requireArtifactsAbsent(runtime, arm);
        requireCompleteResults(frozen.dataset(), results);
        validateTrace(frozen.dataset(), dbCallTrace);
        if (!frozen.datasetHash().equals(report.datasetHash())
                || !ShadowContractSupport.configHash(config).equals(report.configHash())) {
            throw new IllegalArgumentException("CANDIDATE5_REVIEW_REPORT_HASH_MISMATCH");
        }

        Files.createDirectories(runtime);
        Path contexts = runtime.resolve(arm.contextsFile());
        Path labels = runtime.resolve(arm.labelsFile());
        Path trace = runtime.resolve(arm.traceFile());
        Path reportPath = runtime.resolve(arm.reportFile());
        writeLiveArtifacts(frozen.dataset(), results, contexts, labels);
        writeJson(trace, dbCallTrace);

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("contexts", artifact(contexts));
        artifacts.put("labels", artifact(labels));
        Map<String, Object> traceArtifact = new LinkedHashMap<>(artifact(trace));
        traceArtifact.put("count", CASE_COUNT);
        artifacts.put("dbCallTrace", traceArtifact);
        artifacts.put("count", CASE_COUNT);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("status", "VALID");
        root.put("sentinels", List.copyOf(sentinelEvidence));
        root.put("config", new LinkedHashMap<>(config));
        root.put("report", JSON.parseObject(JSON.toJSONString(report)));
        root.put("familyScores", familyScores);
        root.put("observedBudget", new LinkedHashMap<>(observedBudget));
        root.put("artifacts", artifacts);
        writeJson(reportPath, root);

        Path bundle = arm.bundleDirectory(runtime);
        Files.createDirectories(bundle.getParent());
        Files.createDirectory(bundle);
        for (Path source : List.of(reportPath, trace, contexts, labels)) {
            Files.copy(source, bundle.resolve(source.getFileName()),
                    StandardCopyOption.COPY_ATTRIBUTES);
        }
        Map<String, Object> files = new LinkedHashMap<>();
        for (String fileName : arm.fileNames()) {
            files.put(fileName, ShadowContractSupport.sha256(bundle.resolve(fileName)));
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("status", "COMPLETE");
        manifest.put("arm", arm.id());
        manifest.put("datasetHash", report.datasetHash());
        manifest.put("configHash", report.configHash());
        manifest.put("files", files);
        writeJson(bundle.resolve("manifest.json"), manifest);
    }

    private static void writeLiveArtifacts(
            RagEvaluationDataset dataset,
            Map<String, RagResult> results,
            Path contextsPath,
            Path labelsPath) throws IOException {
        List<String> contexts = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            RagResult result = results.get(query.id());
            String context = result.getContext() == null ? "" : result.getContext();
            Map<String, Object> contextRecord = commonCaseFields(query);
            contextRecord.put("query", query.query());
            contextRecord.put("context", context);
            contextRecord.put("sourceSegmentIds", sourceSegmentIds(result));
            contextRecord.put("sourceScores", sourceScores(result));
            contextRecord.put("contextSha256", ShadowContractSupport.sha256(
                    context.getBytes(StandardCharsets.UTF_8)));
            contextRecord.put("contextEmpty", context.isEmpty());
            contexts.add(JSON.toJSONString(contextRecord, JSONWriter.Feature.WriteNulls));

            Map<String, Object> labelRecord = commonCaseFields(query);
            labelRecord.put("referenceAnswer", query.referenceAnswer());
            labelRecord.put("referenceClaims", query.referenceClaims());
            labels.add(JSON.toJSONString(labelRecord, JSONWriter.Feature.WriteNulls));
        }
        Files.writeString(contextsPath, String.join("\n", contexts) + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(labelsPath, String.join("\n", labels) + "\n",
                StandardCharsets.UTF_8);
    }

    private static Map<String, Object> commonCaseFields(RagEvaluationDataset.QueryCase query) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("queryId", query.id());
        values.put("familyId", query.familyId());
        values.put("language", query.language());
        values.put("split", query.split());
        values.put("answerable", query.answerable());
        values.put("strata", query.strata().stream().sorted().toList());
        return values;
    }

    private static List<String> sourceSegmentIds(RagResult result) {
        if (result.getSources() == null) {
            return List.of();
        }
        return result.getSources().stream()
                .map(RetrievalResult::getSegmentId)
                .map(String::valueOf)
                .toList();
    }

    private static List<Double> sourceScores(RagResult result) {
        if (result.getSources() == null) {
            return List.of();
        }
        return result.getSources().stream()
                .map(RetrievalResult::getScore)
                .toList();
    }

    private static List<String> ranking(RagResult result) {
        if (result == null || result.getSources() == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (RetrievalResult source : result.getSources()) {
            if (source != null && source.getSegmentId() != null) {
                seen.add(String.valueOf(source.getSegmentId()));
            }
        }
        return List.copyOf(seen);
    }

    private static void validateTrace(
            RagEvaluationDataset dataset, List<Map<String, Object>> trace) {
        if (trace.size() != CASE_COUNT) {
            throw new IllegalArgumentException("CANDIDATE5_REVIEW_TRACE_COUNT_INVALID");
        }
        Set<String> expected = dataset.queries().stream()
                .map(RagEvaluationDataset.QueryCase::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> actual = new LinkedHashSet<>();
        for (Map<String, Object> item : trace) {
            Object queryId = item.get("queryId");
            if (!(queryId instanceof String value) || !actual.add(value)) {
                throw new IllegalArgumentException("CANDIDATE5_REVIEW_TRACE_QUERY_INVALID");
            }
        }
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("CANDIDATE5_REVIEW_TRACE_COVERAGE_INVALID");
        }
    }

    private static void requireCompleteResults(
            RagEvaluationDataset dataset, Map<String, RagResult> results) {
        if (dataset.queries().size() != CASE_COUNT || results.size() != CASE_COUNT) {
            throw new IllegalArgumentException("CANDIDATE5_REVIEW_CASE_COUNT_INVALID");
        }
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            if (!results.containsKey(query.id()) || results.get(query.id()) == null) {
                throw new IllegalArgumentException(
                        "CANDIDATE5_REVIEW_RESULT_MISSING:" + query.id());
            }
        }
    }

    private static RagBenchmarkReport.MetricEstimate estimate(
            Map<String, List<Double>> values) {
        FamilyClusterBootstrap.ConfidenceInterval interval =
                new FamilyClusterBootstrap().mean(values);
        return RagBenchmarkReport.MetricEstimate.valid(
                interval.estimate(), interval.ciLow(), interval.ciHigh(), interval.clusters());
    }

    private static void add(
            Map<String, List<Double>> values, String familyId, double value) {
        values.computeIfAbsent(familyId, ignored -> new ArrayList<>()).add(value);
    }

    private static Map<String, Object> artifact(Path path) {
        return Map.of(
                "file", path.getFileName().toString(),
                "sha256", ShadowContractSupport.sha256(path));
    }

    private static void writeJson(Path path, Object value) throws IOException {
        Files.writeString(path,
                JSON.toJSONString(value, JSONWriter.Feature.PrettyFormat,
                        JSONWriter.Feature.WriteNulls),
                StandardCharsets.UTF_8);
    }

    enum Arm {
        BASELINE("A", "candidate5-baseline.json", "candidate5-baseline-db-calls.json",
                "candidate5-baseline-contexts.jsonl", "candidate5-baseline-labels.jsonl",
                "candidate5-baseline"),
        CANDIDATE("B", "candidate5.json", "candidate5-db-calls.json",
                "candidate5-contexts.jsonl", "candidate5-labels.jsonl", "candidate5");

        private final String id;
        private final String reportFile;
        private final String traceFile;
        private final String contextsFile;
        private final String labelsFile;
        private final String bundleName;

        Arm(String id, String reportFile, String traceFile, String contextsFile,
            String labelsFile, String bundleName) {
            this.id = id;
            this.reportFile = reportFile;
            this.traceFile = traceFile;
            this.contextsFile = contextsFile;
            this.labelsFile = labelsFile;
            this.bundleName = bundleName;
        }

        String id() {
            return id;
        }

        boolean enabled() {
            return this == CANDIDATE;
        }

        String reportFile() {
            return reportFile;
        }

        String traceFile() {
            return traceFile;
        }

        String contextsFile() {
            return contextsFile;
        }

        String labelsFile() {
            return labelsFile;
        }

        Path bundleDirectory(Path runtime) {
            return runtime.resolve("bundles").resolve(bundleName);
        }

        List<String> fileNames() {
            return List.of(reportFile, traceFile, contextsFile, labelsFile);
        }
    }
}
