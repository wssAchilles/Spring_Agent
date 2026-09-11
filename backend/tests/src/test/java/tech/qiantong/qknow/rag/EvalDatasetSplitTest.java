package tech.qiantong.qknow.rag;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H0: freeze eval dataset shape — selection/holdout split and metric helper sanity.
 */
class EvalDatasetSplitTest {

    @Test
    @DisplayName("rag-eval-holdout-v1 具备 selection/holdout 分层且 holdout≥12")
    void datasetHasFrozenSplit() throws Exception {
        List<Map<String, Object>> rows = load();
        assertFalse(rows.isEmpty());
        long selection = rows.stream().filter(r -> "selection".equals(r.get("split"))).count();
        long holdout = rows.stream().filter(r -> "holdout".equals(r.get("split"))).count();
        assertTrue(selection >= 20, "selection too small: " + selection);
        assertTrue(holdout >= 12, "holdout too small: " + holdout);
        assertEquals(rows.size(), selection + holdout);
        for (Map<String, Object> row : rows) {
            assertNotNull(row.get("id"));
            assertNotNull(row.get("query"));
            assertNotNull(row.get("expectedSources"));
            assertNotNull(row.get("kbId"));
            assertNotNull(row.get("stratum"));
            assertTrue(row.get("expectedSources") instanceof List<?> list && !list.isEmpty());
        }
    }

    @Test
    @DisplayName("rag-eval-v2：holdout≥40、negative≥8、zh≥50%")
    void evalV2MeetsPhase01Gates() throws Exception {
        var cases = tech.qiantong.qknow.rag.eval.RagEvalV2Loader.loadFromClasspath();
        assertFalse(cases.isEmpty(), "rag-eval-v2.jsonl missing");
        long holdout = cases.stream().filter(c -> "holdout".equals(c.split())).count();
        long negHoldout = cases.stream()
                .filter(c -> "holdout".equals(c.split()) && "negative".equals(c.stratum()))
                .count();
        long zh = cases.stream().filter(c -> "zh".equals(c.lang())).count();
        assertTrue(holdout >= 40, "holdout too small: " + holdout);
        assertTrue(negHoldout >= 8, "holdout negative too small: " + negHoldout);
        assertTrue(zh * 2 >= cases.size(), "zh ratio < 50%: " + zh + "/" + cases.size());
    }

    @Test
    @DisplayName("LiveRetrievalMetrics 计算 Hit/MRR/NDCG 与人工期望一致")
    void metricsHelperMatchesManualScores() {
        LiveRetrievalMetrics.CaseScores perfect = LiveRetrievalMetrics.score(
                List.of("Day01", "Day16"),
                List.of("Day01.md", "Day16.md", "noise.md"));
        assertEquals(1.0, perfect.hitAt5());
        assertEquals(1.0, perfect.hitAt10());
        assertEquals(1.0, perfect.mrrAt10());
        assertEquals(1.0, perfect.ndcgAt10());

        LiveRetrievalMetrics.CaseScores miss = LiveRetrievalMetrics.score(
                List.of("Day01"),
                List.of("noise1", "noise2", "noise3"));
        assertEquals(0.0, miss.hitAt10());
        assertEquals(0.0, miss.mrrAt10());

        LiveRetrievalMetrics.CaseScores late = LiveRetrievalMetrics.score(
                List.of("Day01"),
                List.of("n1", "n2", "n3", "n4", "n5", "n6", "n7", "n8", "n9", "Day01", "n10"));
        assertEquals(0.0, late.hitAt5());
        assertEquals(1.0, late.hitAt10());
        assertEquals(0.1, late.mrrAt10(), 1e-6);

        LiveRetrievalMetrics.Aggregate agg = LiveRetrievalMetrics.aggregate(List.of(perfect, miss));
        assertEquals(2, agg.n());
        assertEquals(0.5, agg.hitAt10());
    }

    private static List<Map<String, Object>> load() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (InputStream in = EvalDatasetSplitTest.class
                .getResourceAsStream("/rag-eval-holdout-v1.jsonl")) {
            assertNotNull(in, "dataset missing");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> map = JSON.parseObject(line, Map.class);
                rows.add(map);
            }
        }
        return rows;
    }
}
