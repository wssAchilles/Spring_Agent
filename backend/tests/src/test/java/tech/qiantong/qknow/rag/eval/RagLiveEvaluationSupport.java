package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class RagLiveEvaluationSupport {

    static final List<String> REQUIRED_ENVIRONMENT = List.of(
            "RAG_EVAL_PLATFORM", "RAG_EVAL_BASE_URL", "RAG_EVAL_API_KEY", "RAG_EVAL_MODEL");

    private RagLiveEvaluationSupport() {
    }

    static LiveConfiguration fromEnvironment() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : REQUIRED_ENVIRONMENT) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Missing required live evaluation environment: " + name);
            }
            values.put(name, value.trim());
        }
        return new LiveConfiguration(
                values.get("RAG_EVAL_PLATFORM"),
                values.get("RAG_EVAL_BASE_URL"),
                values.get("RAG_EVAL_API_KEY"),
                values.get("RAG_EVAL_MODEL"),
                optionalPrice("RAG_EVAL_INPUT_USD_PER_MILLION"),
                optionalPrice("RAG_EVAL_OUTPUT_USD_PER_MILLION"));
    }

    static List<LiveCase> loadCases(Path directory) throws IOException {
        Path contextsPath = directory.resolve("shadow-contexts.jsonl");
        Path labelsPath = directory.resolve("shadow-labels.jsonl");
        if (!Files.isRegularFile(contextsPath) || !Files.isRegularFile(labelsPath)) {
            throw new IllegalStateException("Shadow live artifacts are missing; run the shadow baseline first");
        }

        Map<String, JSONObject> contexts = readJsonLines(contextsPath);
        Map<String, JSONObject> labels = readJsonLines(labelsPath);
        if (!contexts.keySet().equals(labels.keySet())) {
            throw new IllegalStateException("Shadow live artifacts do not cover the same query ids");
        }

        List<LiveCase> cases = new ArrayList<>();
        for (Map.Entry<String, JSONObject> entry : contexts.entrySet()) {
            String queryId = entry.getKey();
            JSONObject context = entry.getValue();
            JSONObject label = labels.get(queryId);
            if (!required(context, "familyId").equals(required(label, "familyId"))
                    || !required(context, "split").equals(required(label, "split"))
                    || context.getBooleanValue("answerable") != label.getBooleanValue("answerable")) {
                throw new IllegalStateException("Shadow artifact metadata mismatch for query id: " + queryId);
            }
            cases.add(new LiveCase(
                    queryId,
                    required(context, "familyId"),
                    required(context, "query"),
                    required(context, "split"),
                    context.getBooleanValue("answerable"),
                    context.getString("context"),
                    label.getString("referenceAnswer"),
                    strings(label.getJSONArray("referenceClaims"))));
        }
        return List.copyOf(cases);
    }

    static void validateAgainstDataset(List<LiveCase> cases, RagEvaluationDataset dataset) {
        Objects.requireNonNull(cases, "cases");
        Objects.requireNonNull(dataset, "dataset");
        if (cases.size() != dataset.queries().size() || cases.size() != 96) {
            throw new IllegalStateException("Shadow artifacts must contain exactly 96 dataset cases");
        }
        Map<String, RagEvaluationDataset.QueryCase> expected = new LinkedHashMap<>();
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            expected.put(query.id(), query);
        }
        for (LiveCase actual : cases) {
            RagEvaluationDataset.QueryCase query = expected.get(actual.queryId());
            if (query == null
                    || !query.familyId().equals(actual.familyId())
                    || !query.query().equals(actual.query())
                    || !query.split().equals(actual.split())
                    || query.answerable() != actual.answerable()
                    || !Objects.equals(query.referenceAnswer(), actual.referenceAnswer())
                    || !query.referenceClaims().equals(actual.referenceClaims())) {
                throw new IllegalStateException("Shadow artifact metadata does not match dataset: "
                        + actual.queryId());
            }
        }
    }

    static Path runtimeDirectory() {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        return workingDirectory.endsWith(Path.of("backend", "tests"))
                ? workingDirectory.resolve("target/rag-eval")
                : workingDirectory.resolve("backend/tests/target/rag-eval");
    }

    static CostEstimate cost(LiveConfiguration configuration, long promptTokens, long completionTokens) {
        Objects.requireNonNull(configuration, "configuration");
        if (configuration.inputPricePerMillion() == null
                || configuration.outputPricePerMillion() == null) {
            return CostEstimate.unavailable();
        }
        double value = promptTokens / 1_000_000.0 * configuration.inputPricePerMillion()
                + completionTokens / 1_000_000.0 * configuration.outputPricePerMillion();
        return new CostEstimate("AVAILABLE", value);
    }

    private static Map<String, JSONObject> readJsonLines(Path path) throws IOException {
        Map<String, JSONObject> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            JSONObject json = JSON.parseObject(line);
            String queryId = required(json, "queryId");
            if (values.putIfAbsent(queryId, json) != null) {
                throw new IllegalStateException("Duplicate live artifact query id: " + queryId);
            }
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("Live evaluation artifact is empty");
        }
        return values;
    }

    private static String required(JSONObject json, String field) {
        String value = json.getString(field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Live evaluation artifact is missing " + field);
        }
        return value;
    }

    private static List<String> strings(com.alibaba.fastjson2.JSONArray values) {
        return values == null ? List.of() : values.toJavaList(String.class);
    }

    private static Double optionalPrice(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed) || parsed < 0.0) {
                throw new IllegalStateException("Live evaluation price must be non-negative: " + name);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Live evaluation price is invalid: " + name, e);
        }
    }

    static final class LiveConfiguration {
        private final String platform;
        private final String baseUrl;
        private final String apiKey;
        private final String model;
        private final Double inputPricePerMillion;
        private final Double outputPricePerMillion;

        private LiveConfiguration(String platform, String baseUrl, String apiKey, String model,
                                  Double inputPricePerMillion, Double outputPricePerMillion) {
            this.platform = platform;
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.inputPricePerMillion = inputPricePerMillion;
            this.outputPricePerMillion = outputPricePerMillion;
        }

        static LiveConfiguration forTesting(String platform, String baseUrl, String apiKey,
                                            String model, Double inputPricePerMillion,
                                            Double outputPricePerMillion) {
            return new LiveConfiguration(platform, baseUrl, apiKey, model,
                    inputPricePerMillion, outputPricePerMillion);
        }

        String platform() {
            return platform;
        }

        String baseUrl() {
            return baseUrl;
        }

        String apiKey() {
            return apiKey;
        }

        String model() {
            return model;
        }

        Double inputPricePerMillion() {
            return inputPricePerMillion;
        }

        Double outputPricePerMillion() {
            return outputPricePerMillion;
        }
    }

    record LiveCase(String queryId, String familyId, String query, String split,
                    boolean answerable, String context, String referenceAnswer,
                    List<String> referenceClaims) {
        LiveCase {
            referenceClaims = referenceClaims == null ? List.of() : List.copyOf(referenceClaims);
        }
    }

    record CostEstimate(String status, Double value) {
        static CostEstimate unavailable() {
            return new CostEstimate("unavailable", null);
        }
    }
}
