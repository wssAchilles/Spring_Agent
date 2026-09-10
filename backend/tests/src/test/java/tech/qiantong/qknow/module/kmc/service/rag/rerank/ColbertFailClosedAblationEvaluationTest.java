package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A0 vs A1 ColBERT coarse-rank ablation on frozen golden queries.
 * A0: hash pseudo-vector coarse rank + topK*3 truncate.
 * A1: skip coarse rank when no real embedding is configured.
 *
 * Mechanism mode: -Dqknow.rag.colbert.ablation=true
 * Live ANN mode:  -Dqknow.rag.colbert.ablation.live=true
 *                 (+ -Dqknow.rag.colbert.ablation.api.key / model optional)
 */
class ColbertFailClosedAblationEvaluationTest {

    private static final int FINAL_TOP_K = 10;
    private static final int CANDIDATE_POOL = 80;
    private static final int LIVE_TOP_K = 40;

    @Test
    @EnabledIfSystemProperty(named = "qknow.rag.colbert.ablation", matches = "true")
    void ablatesHashCoarseTruncationVersusFailClosedSkip() throws Exception {
        List<GoldenCase> cases = loadGoldenCases();
        assertFalse(cases.isEmpty());

        String jdbcUrl = System.getProperty("qknow.rag.colbert.ablation.jdbc.url",
                "jdbc:postgresql://127.0.0.1:5432/ai_agent");
        String user = System.getProperty("qknow.rag.colbert.ablation.jdbc.user", "achilles");
        String password = System.getProperty("qknow.rag.colbert.ablation.jdbc.password",
                System.getenv().getOrDefault("POSTGRESQL_PASSWORD", ""));

        List<Map<String, Object>> rows = new ArrayList<>();
        Metrics aggA0 = new Metrics();
        Metrics aggA1 = new Metrics();
        int hashFallbackInA1 = 0;

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            for (GoldenCase item : cases) {
                List<RetrievalResult> pool = buildCandidatePool(connection, item);
                assertTrue(pool.size() >= FINAL_TOP_K, "pool too small for " + item.id());

                ArmResult a0 = runArm(pool, false, item);
                ArmResult a1 = runArm(pool, true, item);
                if (a1.hashInvolved()) {
                    hashFallbackInA1++;
                }

                aggA0.add(item, a0.ranking(), a0.elapsedMs());
                aggA1.add(item, a1.ranking(), a1.elapsedMs());

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.id());
                row.put("expectedSources", item.expectedSources());
                row.put("poolSize", pool.size());
                row.put("a0RetainedSources", a0.ranking());
                row.put("a1RetainedSources", a1.ranking());
                row.put("a0HitAt10", hitAt(item.expectedSources(), a0.ranking(), 10));
                row.put("a1HitAt10", hitAt(item.expectedSources(), a1.ranking(), 10));
                row.put("a1HashInvolved", a1.hashInvolved());
                rows.add(row);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("hypothesis", "H1");
        report.put("evidenceLevel", "MECHANISM_ABLATION");
        report.put("finalTopK", FINAL_TOP_K);
        report.put("coarseLimit", FINAL_TOP_K * 3);
        report.put("candidatePool", CANDIDATE_POOL);
        report.put("caseCount", cases.size());
        report.put("hashFallbackInA1", hashFallbackInA1);
        report.put("A0", aggA0.summary());
        report.put("A1", aggA1.summary());
        report.put("deltaRecallAt5", round(aggA1.recallAt5() - aggA0.recallAt5()));
        report.put("deltaRecallAt10", round(aggA1.recallAt10() - aggA0.recallAt10()));
        report.put("deltaMrrAt10", round(aggA1.mrrAt10() - aggA0.mrrAt10()));
        report.put("deltaNdcgAt10", round(aggA1.ndcgAt10() - aggA0.ndcgAt10()));
        report.put("cases", rows);
        report.put("notes", List.of(
                "Candidates are real kmc_document_segment rows matched by expected document names plus noise.",
                "Only ColBERT coarse stage differs; deterministic final rerank is identical.",
                "This is mechanism evidence, not full live end-to-end Recall@K with vector store."));

        Path outDir = resolveEvidenceDirectory();
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("a0-a1-ablation-report.json");
        Files.writeString(outFile,
                JSON.toJSONString(report, JSONWriter.Feature.PrettyFormat),
                StandardCharsets.UTF_8);

        assertEquals(0, hashFallbackInA1, "HASH_FALLBACK_IN_A1");
        assertTrue(Files.isRegularFile(outFile));
    }

    /**
     * Live ANN A0/A1: query embedding (text-embedding-v4) + pgvector HNSW recall,
     * then only ColBERT coarse stage differs. Does NOT re-embed documents.
     */
    @Test
    @EnabledIfSystemProperty(named = "qknow.rag.colbert.ablation.live", matches = "true")
    void liveAnnAblationHashCoarseVersusFailClosedSkip() throws Exception {
        List<GoldenCase> cases = loadGoldenCases();
        assertFalse(cases.isEmpty());

        String jdbcUrl = System.getProperty("qknow.rag.colbert.ablation.jdbc.url",
                "jdbc:postgresql://127.0.0.1:5432/ai_agent");
        String user = System.getProperty("qknow.rag.colbert.ablation.jdbc.user", "achilles");
        String password = System.getProperty("qknow.rag.colbert.ablation.jdbc.password",
                System.getenv().getOrDefault("POSTGRESQL_PASSWORD", ""));
        String apiKey = System.getProperty("qknow.rag.colbert.ablation.api.key", "");
        if (apiKey.isBlank()) {
            try (Connection c = DriverManager.getConnection(jdbcUrl, user, password);
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT api_key FROM ai_api_key WHERE id = 103 AND del_flag = 0")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        apiKey = rs.getString(1);
                    }
                }
            }
        }
        assertFalse(apiKey == null || apiKey.isBlank(), "missing embedding api key");

        String model = System.getProperty("qknow.rag.colbert.ablation.api.model", "text-embedding-v4");
        String baseUrl = System.getProperty("qknow.rag.colbert.ablation.api.base-url",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings");

        List<Map<String, Object>> rows = new ArrayList<>();
        Metrics aggA0 = new Metrics();
        Metrics aggA1 = new Metrics();
        int hashFallbackInA1 = 0;
        int emptyPools = 0;
        long queryEmbedMs = 0;

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            for (GoldenCase item : cases) {
                long t0 = System.currentTimeMillis();
                float[] qvec = embedQuery(http, baseUrl, apiKey, model, item.query());
                queryEmbedMs += System.currentTimeMillis() - t0;

                List<RetrievalResult> pool = annRetrieve(connection, qvec, LIVE_TOP_K);
                if (pool.size() < FINAL_TOP_K) {
                    emptyPools++;
                }
                if (pool.isEmpty()) {
                    continue;
                }

                ArmResult a0 = runArm(pool, false, item);
                ArmResult a1 = runArm(pool, true, item);
                if (a1.hashInvolved()) {
                    hashFallbackInA1++;
                }
                aggA0.add(item, a0.ranking(), a0.elapsedMs());
                aggA1.add(item, a1.ranking(), a1.elapsedMs());

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.id());
                row.put("query", item.query());
                row.put("expectedSources", item.expectedSources());
                row.put("annPoolSize", pool.size());
                row.put("annTopSources", pool.stream().map(RetrievalResult::getDocumentName).distinct().limit(5).toList());
                row.put("a0RetainedSources", a0.ranking());
                row.put("a1RetainedSources", a1.ranking());
                row.put("a0HitAt10", hitAt(item.expectedSources(), a0.ranking(), 10));
                row.put("a1HitAt10", hitAt(item.expectedSources(), a1.ranking(), 10));
                row.put("a1HashInvolved", a1.hashInvolved());
                rows.add(row);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("hypothesis", "H1");
        report.put("evidenceLevel", "LIVE_ANN_ABLATION");
        report.put("queryEmbeddingModel", model);
        report.put("annTopK", LIVE_TOP_K);
        report.put("finalTopK", FINAL_TOP_K);
        report.put("coarseLimit", FINAL_TOP_K * 3);
        report.put("caseCount", cases.size());
        report.put("evaluatedCases", rows.size());
        report.put("emptyOrSmallPools", emptyPools);
        report.put("hashFallbackInA1", hashFallbackInA1);
        report.put("totalQueryEmbedMs", queryEmbedMs);
        report.put("A0", aggA0.summary());
        report.put("A1", aggA1.summary());
        report.put("deltaRecallAt5", round(aggA1.recallAt5() - aggA0.recallAt5()));
        report.put("deltaRecallAt10", round(aggA1.recallAt10() - aggA0.recallAt10()));
        report.put("deltaMrrAt10", round(aggA1.mrrAt10() - aggA0.mrrAt10()));
        report.put("deltaNdcgAt10", round(aggA1.ndcgAt10() - aggA0.ndcgAt10()));
        report.put("cases", rows);
        report.put("notes", List.of(
                "Candidates come from live pgvector HNSW ANN on restored vector_store embeddings (text-embedding-v4, 1024d).",
                "Documents were NOT re-embedded in this experiment.",
                "Only ColBERT coarse stage differs between A0 and A1; final deterministic rerank is identical."));

        Path outDir = resolveEvidenceDirectory();
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("a0-a1-live-ann-report.json");
        Files.writeString(outFile,
                JSON.toJSONString(report, JSONWriter.Feature.PrettyFormat),
                StandardCharsets.UTF_8);

        assertEquals(0, hashFallbackInA1, "HASH_FALLBACK_IN_A1");
        assertTrue(Files.isRegularFile(outFile));
        assertTrue(rows.size() >= 5, "live ANN produced too few evaluated cases");
    }

    private static float[] embedQuery(HttpClient http, String baseUrl, String apiKey,
                                      String model, String query) throws Exception {
        String body = JSON.toJSONString(Map.of("model", model, "input", List.of(query)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("embed HTTP " + response.statusCode() + ": "
                    + response.body().substring(0, Math.min(200, response.body().length())));
        }
        var json = JSON.parseObject(response.body());
        var emb = json.getJSONArray("data").getJSONObject(0).getJSONArray("embedding");
        float[] vec = new float[emb.size()];
        for (int i = 0; i < emb.size(); i++) {
            vec[i] = emb.getFloatValue(i);
        }
        return vec;
    }

    private static List<RetrievalResult> annRetrieve(Connection connection, float[] queryVec, int topK)
            throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < queryVec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(java.util.Locale.ROOT, "%.6f", queryVec[i]));
        }
        sb.append(']');
        String sql = """
                SELECT content,
                       coalesce(metadata->>'kmc_document_name', '') AS doc_name,
                       1 - (embedding <=> ?::vector) AS score
                FROM vector_store
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        List<RetrievalResult> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String lit = sb.toString();
            ps.setString(1, lit);
            ps.setString(2, lit);
            ps.setInt(3, topK);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String content = rs.getString("content");
                    if (content != null && content.length() > 800) {
                        content = content.substring(0, 800);
                    }
                    results.add(RetrievalResult.builder()
                            .segmentId((long) results.size())
                            .documentName(rs.getString("doc_name"))
                            .content(content)
                            .score(rs.getDouble("score"))
                            .source("ann")
                            .build());
                }
            }
        }
        return results;
    }

    private static Path resolveEvidenceDirectory() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path name = cwd.getFileName();
        String leaf = name == null ? "" : name.toString();
        if (leaf.equals("tests") && cwd.getParent() != null
                && "backend".equals(cwd.getParent().getFileName() == null
                        ? null : cwd.getParent().getFileName().toString())) {
            return cwd.resolve("evidence/a1-colbert-fail-closed");
        }
        if (leaf.equals("backend")) {
            return cwd.resolve("tests/evidence/a1-colbert-fail-closed");
        }
        return cwd.resolve("backend/tests/evidence/a1-colbert-fail-closed");
    }

    private static List<GoldenCase> loadGoldenCases() throws Exception {
        List<GoldenCase> cases = new ArrayList<>();
        try (InputStream input = ColbertFailClosedAblationEvaluationTest.class
                .getResourceAsStream("/rag-golden-dataset-v2.jsonl")) {
            if (input == null) {
                return cases;
            }
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                var json = JSON.parseObject(line);
                List<String> sources = json.getJSONArray("expectedSources").toList(String.class);
                cases.add(new GoldenCase(
                        json.getString("id"),
                        json.getString("query"),
                        sources));
            }
        }
        return cases;
    }

    private static List<RetrievalResult> buildCandidatePool(Connection connection, GoldenCase item)
            throws Exception {
        List<RetrievalResult> pool = new ArrayList<>();
        Set<String> expected = new LinkedHashSet<>(item.expectedSources());
        for (String doc : expected) {
            pool.addAll(loadSegments(connection, doc, 30, 1.0));
        }
        pool.addAll(loadSegments(connection, null, Math.max(0, CANDIDATE_POOL - pool.size()), 0.2));
        // Cap pool while keeping expected docs first in score order.
        return pool.stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(CANDIDATE_POOL)
                .collect(Collectors.toList());
    }

    private static List<RetrievalResult> loadSegments(Connection connection, String documentName,
                                                      int limit, double baseScore) throws Exception {
        String sql = documentName == null
                ? "select id, document_name, content from kmc_document_segment "
                + "where document_name is not null and content is not null "
                + "and document_name not in ('人工智能.pdf','分布式.pdf','大数据.pdf','移动应用开发.pdf',"
                + "'关于印发《常州工学院学生综合素质评价办法（修订）》的通知.pdf') "
                + "order by id limit ?"
                : "select id, document_name, content from kmc_document_segment "
                + "where document_name = ? and content is not null order by length(content) desc limit ?";
        List<RetrievalResult> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (documentName == null) {
                ps.setInt(1, limit);
            } else {
                ps.setString(1, documentName);
                ps.setInt(2, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String content = rs.getString("content");
                    if (content != null && content.length() > 800) {
                        content = content.substring(0, 800);
                    }
                    results.add(RetrievalResult.builder()
                            .segmentId(id)
                            .documentName(rs.getString("document_name"))
                            .content(content)
                            .score(baseScore)
                            .source("abl")
                            .build());
                }
            }
        }
        return results;
    }

    private static ArmResult runArm(List<RetrievalResult> pool, boolean skipWhenNoEmbedding,
                                    GoldenCase item) {
        ColbertScorer.ColbertConfig config = new ColbertScorer.ColbertConfig();
        config.setEnabled(true);
        config.setSkipWhenNoEmbedding(skipWhenNoEmbedding);
        config.setDimensions(64);
        config.setMaxTokensPerDoc(128);
        ColbertScorer scorer = new ColbertScorer(config, null);

        DeterministicRerankerProvider deterministic = new DeterministicRerankerProvider();
        RagRerankService service = new RagRerankService();
        ReflectionTestUtils.setField(service, "colbertScorer", scorer);
        ReflectionTestUtils.setField(service, "deterministicRerankerProvider", deterministic);
        ReflectionTestUtils.setField(service, "rerankerProviders", List.of(deterministic));
        ReflectionTestUtils.setField(service, "identifierConsistencyEnabled", false);

        QueryIntent intent = new QueryIntent();
        List<RetrievalResult> copy = pool.stream()
                .map(r -> RetrievalResult.builder()
                        .segmentId(r.getSegmentId())
                        .documentName(r.getDocumentName())
                        .content(r.getContent())
                        .score(r.getScore())
                        .source(r.getSource())
                        .build())
                .collect(Collectors.toList());

        long startNs = System.nanoTime();
        List<RetrievalResult> ranked = service.rerank(
                item.query(), copy, intent, FINAL_TOP_K, null, null);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        List<String> sources = ranked.stream()
                .map(RetrievalResult::getDocumentName)
                .collect(Collectors.toList());
        boolean hashInvolved = !skipWhenNoEmbedding
                || ranked.stream().anyMatch(r -> r.getMetadata() != null
                && r.getMetadata().containsKey("colbert_score"));
        return new ArmResult(sources, skipWhenNoEmbedding && hashInvolved, elapsedMs);
    }

    private static boolean hitAt(List<String> expected, List<String> ranking, int k) {
        List<String> top = ranking.subList(0, Math.min(k, ranking.size()));
        return top.stream().anyMatch(expected::contains);
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record GoldenCase(String id, String query, List<String> expectedSources) {
    }

    private record ArmResult(List<String> ranking, boolean hashInvolved, long elapsedMs) {
    }

    private static final class Metrics {
        private final List<Double> hitsAt5 = new ArrayList<>();
        private final List<Double> hitsAt10 = new ArrayList<>();
        private final List<Double> rrs = new ArrayList<>();
        private final List<Double> ndcgs = new ArrayList<>();
        private final List<Long> latencies = new ArrayList<>();

        void add(GoldenCase item, List<String> ranking, long elapsedMs) {
            hitsAt5.add(hitAt(item.expectedSources(), ranking, 5) ? 1.0 : 0.0);
            hitsAt10.add(hitAt(item.expectedSources(), ranking, 10) ? 1.0 : 0.0);
            rrs.add(reciprocalRank(item.expectedSources(), ranking, 10));
            ndcgs.add(ndcgAt10(item.expectedSources(), ranking));
            latencies.add(elapsedMs);
        }

        private static double reciprocalRank(List<String> expected, List<String> ranking, int k) {
            for (int i = 0; i < Math.min(k, ranking.size()); i++) {
                if (expected.contains(ranking.get(i))) {
                    return 1.0 / (i + 1);
                }
            }
            return 0.0;
        }

        private static double ndcgAt10(List<String> expected, List<String> ranking) {
            double dcg = 0.0;
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < Math.min(10, ranking.size()); i++) {
                String source = ranking.get(i);
                if (expected.contains(source) && seen.add(source)) {
                    dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
                }
            }
            double idcg = 0.0;
            int ideal = Math.min(expected.size(), 10);
            for (int i = 0; i < ideal; i++) {
                idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
            return idcg == 0.0 ? 0.0 : dcg / idcg;
        }

        private static double average(List<Double> values) {
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        double recallAt5() {
            return average(hitsAt5);
        }

        double recallAt10() {
            return average(hitsAt10);
        }

        double mrrAt10() {
            return average(rrs);
        }

        double ndcgAt10() {
            return average(ndcgs);
        }

        long p50() {
            List<Long> sorted = latencies.stream().sorted().collect(Collectors.toList());
            return sorted.get(sorted.size() / 2);
        }

        long p95() {
            List<Long> sorted = latencies.stream().sorted().collect(Collectors.toList());
            return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1));
        }

        Map<String, Object> summary() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("recallAt5", round(recallAt5()));
            map.put("recallAt10", round(recallAt10()));
            map.put("mrrAt10", round(mrrAt10()));
            map.put("ndcgAt10", round(ndcgAt10()));
            map.put("p50Ms", p50());
            map.put("p95Ms", p95());
            map.put("n", hitsAt10.size());
            return map;
        }
    }
}
