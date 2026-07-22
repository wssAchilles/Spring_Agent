package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RagEvaluationDatasetLoader {

    private static final String CORPUS = "/rag-eval/corpus.jsonl";
    private static final String QUERIES = "/rag-eval/queries.jsonl";
    private static final String QRELS = "/rag-eval/qrels.tsv";

    private RagEvaluationDatasetLoader() {
    }

    public static RagEvaluationDataset loadDefault() {
        try {
            RagEvaluationDataset dataset = new RagEvaluationDataset(
                    loadCorpus(CORPUS), loadQueries(QUERIES), loadQrels(QRELS));
            validate(dataset);
            return dataset;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load RAG evaluation dataset", e);
        }
    }

    static void validate(RagEvaluationDataset dataset) {
        Map<String, RagEvaluationDataset.QueryCase> queriesById = dataset.queries().stream()
                .collect(Collectors.toMap(RagEvaluationDataset.QueryCase::id, query -> query));
        Set<String> answerableIds = dataset.queries().stream()
                .filter(RagEvaluationDataset.QueryCase::answerable)
                .map(RagEvaluationDataset.QueryCase::id)
                .collect(Collectors.toSet());
        if (!dataset.qrels().keySet().equals(answerableIds)) {
            throw new IllegalArgumentException("Qrels must exactly cover answerable queries");
        }
        dataset.qrels().forEach((queryId, grades) -> {
            if (!queriesById.containsKey(queryId) || grades.isEmpty()) {
                throw new IllegalArgumentException("Invalid qrels query: " + queryId);
            }
            grades.forEach((segmentId, grade) -> {
                if (!dataset.corpusById().containsKey(segmentId)) {
                    throw new IllegalArgumentException("Unknown qrels segment: " + segmentId);
                }
                if (grade == null || grade < 1 || grade > 3) {
                    throw new IllegalArgumentException("Invalid qrels grade: " + grade);
                }
            });
        });
    }

    private static Map<String, RagEvaluationDataset.CorpusSegment> loadCorpus(String resource) throws IOException {
        Map<String, RagEvaluationDataset.CorpusSegment> corpus = new LinkedHashMap<>();
        for (String line : lines(resource)) {
            JSONObject json = JSON.parseObject(line);
            JSONObject metadata = json.getJSONObject("metadata");
            RagEvaluationDataset.CorpusSegment segment = new RagEvaluationDataset.CorpusSegment(
                    required(json, "segmentId"),
                    required(json, "documentId"),
                    required(json, "content"),
                    json.getString("parentSegmentId"),
                    metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
            if (corpus.putIfAbsent(segment.segmentId(), segment) != null) {
                throw new IllegalArgumentException("Duplicate corpus segment: " + segment.segmentId());
            }
        }
        return corpus;
    }

    private static List<RagEvaluationDataset.QueryCase> loadQueries(String resource) throws IOException {
        List<RagEvaluationDataset.QueryCase> queries = new ArrayList<>();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String line : lines(resource)) {
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

    private static Map<String, Map<String, Integer>> loadQrels(String resource) throws IOException {
        Map<String, Map<String, Integer>> qrels = new LinkedHashMap<>();
        List<String> rows = lines(resource);
        for (int i = 0; i < rows.size(); i++) {
            String line = rows.get(i);
            if (i == 0 && line.equals("queryId\tsegmentId\tgrade")) {
                continue;
            }
            String[] columns = line.split("\\t", -1);
            if (columns.length != 3) {
                throw new IllegalArgumentException("Invalid qrels row: " + line);
            }
            int grade = Integer.parseInt(columns[2]);
            Map<String, Integer> queryQrels = qrels.computeIfAbsent(columns[0], ignored -> new LinkedHashMap<>());
            if (queryQrels.putIfAbsent(columns[1], grade) != null) {
                throw new IllegalArgumentException("Duplicate qrel: " + columns[0] + "/" + columns[1]);
            }
        }
        return qrels;
    }

    private static List<String> lines(String resource) throws IOException {
        InputStream stream = RagEvaluationDatasetLoader.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalArgumentException("Missing classpath resource: " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank()).toList();
        }
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
}
