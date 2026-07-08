package tech.qiantong.qknow.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG Golden Question Set - deterministic retrieval gate.
 */
@DisplayName("RAG 黄金检索 gate")
class RagGoldenTest {

    private static final RetrievalMetrics.Thresholds GOLDEN_THRESHOLDS =
            new RetrievalMetrics.Thresholds(1.0, 1.0, 1.0, 1.0, 0.20);

    @Test
    @DisplayName("golden JSONL 数据集应驱动固定 fixture retrieval gate")
    void goldenDatasets_passFixtureRetrievalGate() throws Exception {
        List<GoldenItem> items = loadGoldenItems();

        assertEquals(20, items.size());
        for (GoldenItem item : items) {
            RetrievalMetrics.Scores scores = RetrievalMetrics.evaluate(
                    item.expectedSources(),
                    deterministicFixture(item.id()));

            RetrievalMetrics.GateResult gate = RetrievalMetrics.gate(scores, GOLDEN_THRESHOLDS);

            assertTrue(gate.passed(), () -> item.id() + " failed gate: " + gate.failures());
            assertAll(
                    () -> assertEquals(1.0, scores.recallAt5(), 0.0001),
                    () -> assertEquals(1.0, scores.recallAt10(), 0.0001),
                    () -> assertEquals(1.0, scores.mrr(), 0.0001),
                    () -> assertEquals(1.0, scores.ndcgAt10(), 0.0001),
                    () -> assertTrue(scores.contextPrecisionAt5() >= 0.20)
            );
        }
    }

    @Test
    @DisplayName("golden gate 缺失 expectedSources 命中时必须失败")
    void goldenGate_failsWhenExpectedSourceIsMissing() {
        RetrievalMetrics.Scores scores = RetrievalMetrics.evaluate(
                List.of("Day01"),
                List.of(
                        new RetrievalMetrics.RetrievedContext("noise", "Day02.md", "unrelated"),
                        new RetrievalMetrics.RetrievedContext("noise", "Day03.md", "unrelated")
                ));

        RetrievalMetrics.GateResult gate = RetrievalMetrics.gate(scores, GOLDEN_THRESHOLDS);

        assertFalse(gate.passed());
        assertTrue(gate.failures().contains("recallAt5"));
        assertTrue(gate.failures().contains("mrr"));
    }

    @Test
    @DisplayName("RetrievalMetrics 应计算排序敏感指标")
    void retrievalMetrics_calculatesRankSensitiveScores() {
        RetrievalMetrics.Scores scores = RetrievalMetrics.evaluate(
                List.of("Day01", "Day16"),
                List.of(
                        new RetrievalMetrics.RetrievedContext("noise", "Day02.md", "unrelated"),
                        new RetrievalMetrics.RetrievedContext("source", "Day01.md", "matched"),
                        new RetrievalMetrics.RetrievedContext("noise", "Day03.md", "unrelated"),
                        new RetrievalMetrics.RetrievedContext("noise", "Day04.md", "unrelated"),
                        new RetrievalMetrics.RetrievedContext("noise", "Day05.md", "unrelated"),
                        new RetrievalMetrics.RetrievedContext("source", "Day16.md", "matched")
                ));

        assertAll(
                () -> assertEquals(0.5, scores.recallAt5(), 0.0001),
                () -> assertEquals(1.0, scores.recallAt10(), 0.0001),
                () -> assertEquals(0.5, scores.mrr(), 0.0001),
                () -> assertTrue(scores.ndcgAt10() > 0.60 && scores.ndcgAt10() < 0.61),
                () -> assertEquals(0.20, scores.contextPrecisionAt5(), 0.0001)
        );
    }

    @Test
    @DisplayName("RetrievalMetrics source 指标不应被 content 关键词误命中")
    void retrievalMetrics_doesNotMatchExpectedSourceFromContent() {
        RetrievalMetrics.Scores scores = RetrievalMetrics.evaluate(
                List.of("RAG"),
                List.of(new RetrievalMetrics.RetrievedContext("noise", "unrelated.md", "RAG appears in body")));

        assertAll(
                () -> assertEquals(0.0, scores.recallAt5(), 0.0001),
                () -> assertEquals(0.0, scores.mrr(), 0.0001),
                () -> assertEquals(0.0, scores.ndcgAt10(), 0.0001)
        );
    }

    @Test
    @DisplayName("RetrievalMetrics NDCG 不应因重复同一 source 超过 1")
    void retrievalMetrics_ndcgDeduplicatesExpectedSources() {
        RetrievalMetrics.Scores scores = RetrievalMetrics.evaluate(
                List.of("Day01", "Day16"),
                List.of(
                        new RetrievalMetrics.RetrievedContext("source", "Day01.md", "first"),
                        new RetrievalMetrics.RetrievedContext("source", "Day01.md", "duplicate"),
                        new RetrievalMetrics.RetrievedContext("source", "Day16.md", "second")
                ));

        assertTrue(scores.ndcgAt10() <= 1.0);
        assertEquals(1.0, scores.recallAt5(), 0.0001);
    }

    private List<GoldenItem> loadGoldenItems() throws Exception {
        List<GoldenItem> items = new ArrayList<>();
        loadGoldenItems("/rag-golden-dataset.jsonl", items);
        loadGoldenItems("/rag-golden-dataset-v2.jsonl", items);
        return items;
    }

    private void loadGoldenItems(String resource, List<GoldenItem> items) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream(resource)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject json = JSON.parseObject(line);
                JSONArray expectedSources = json.getJSONArray("expectedSources");
                items.add(new GoldenItem(
                        json.getString("id"),
                        json.getString("query"),
                        expectedSources.toJavaList(String.class)
                ));
            }
        }
    }

    private List<RetrievalMetrics.RetrievedContext> deterministicFixture(String id) {
        return switch (id) {
            case "smoke-001" -> List.of(ctx("Day01", "Day01.md"));
            case "smoke-002" -> List.of(ctx("Day15", "Day15.md"), ctx("Day16", "Day16.md"), ctx("KMC", "KMC-RAG.md"));
            case "smoke-003" -> List.of(ctx("知识库", "知识库.md"), ctx("切分", "切分.md"));
            case "smoke-004" -> List.of(ctx("semantic_cache", "semantic_cache.md"), ctx("语义缓存", "语义缓存.md"));
            case "smoke-005" -> List.of(ctx("CRAG", "CRAG.md"));
            case "smoke-006" -> List.of(ctx("GraphRAG", "GraphRAG.md"), ctx("entities", "entities.md"),
                    ctx("relations", "relations.md"));
            case "smoke-007" -> List.of(ctx("Hermes", "Hermes.md"), ctx("Agent", "Agent.md"));
            case "smoke-008" -> List.of(ctx("KMC", "KMC.md"), ctx("RAG", "RAG.md"));
            case "smoke-009" -> List.of(ctx("父块", "父块.md"), ctx("parent", "parent.md"));
            case "smoke-010" -> List.of(ctx("评测", "评测.md"), ctx("RAG", "RAG.md"));
            case "eval-001", "eval-002", "eval-004", "eval-006", "eval-009" ->
                    List.of(ctx("人工智能.pdf", "人工智能.pdf"));
            case "eval-003" -> List.of(ctx("分布式.pdf", "分布式.pdf"));
            case "eval-005" -> List.of(ctx("关于印发《常州工学院学生综合素质评价办法（修订）》的通知.pdf",
                    "关于印发《常州工学院学生综合素质评价办法（修订）》的通知.pdf"));
            case "eval-007", "eval-010" -> List.of(ctx("大数据.pdf", "大数据.pdf"));
            case "eval-008" -> List.of(ctx("移动应用开发.pdf", "移动应用开发.pdf"));
            default -> throw new IllegalArgumentException("No deterministic fixture for golden id: " + id);
        };
    }

    private RetrievalMetrics.RetrievedContext ctx(String source, String documentName) {
        return new RetrievalMetrics.RetrievedContext(source, documentName, "fixture context for " + documentName);
    }

    private record GoldenItem(String id, String query, List<String> expectedSources) {
    }
}
