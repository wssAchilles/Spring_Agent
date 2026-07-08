package tech.qiantong.qknow.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.eval.LlmAsAJudgeEvaluator;
import tech.qiantong.qknow.hermes.eval.MetricScores;
import tech.qiantong.qknow.hermes.eval.RagasEvalConfig;
import tech.qiantong.qknow.hermes.eval.RagasEvaluator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagEvaluationPipelineTest {

    @Test
    void mockJudgeGate_passesHighFaithfulnessScores() throws Exception {
        List<GoldenItem> items = loadSmokeItems();
        assertEquals(10, items.size());

        LlmAsAJudgeEvaluator judge = mockJudge(scores(0.9));
        List<LlmAsAJudgeEvaluator.Judgement> judgements = items.stream()
                .map(item -> judge.evaluate(item.query(), "mock answer", List.of("mock context"), "mock expected"))
                .toList();

        double faithfulnessMean = judgements.stream()
                .mapToDouble(j -> j.scores().getFaithfulness())
                .average()
                .orElse(0.0);
        assertTrue(faithfulnessMean >= 0.85);
    }

    @Test
    void mockJudgeGate_failsLowFaithfulnessScores() {
        LlmAsAJudgeEvaluator.Judgement judgement = mockJudge(scores(0.7))
                .evaluate("query", "answer", List.of("context"), "expected");

        assertFalse(judgement.passed());
        assertTrue(judgement.scores().getFaithfulness() < judgement.threshold());
    }

    @Test
    void retrievalMetricsPipeline_failsWhenTopKMissesExpectedSource() throws Exception {
        GoldenItem item = loadSmokeItems().get(0);
        List<RetrievalMetrics.RetrievedContext> retrieved = List.of(
                new RetrievalMetrics.RetrievedContext("noise", "Day02.md", "不相关内容"),
                new RetrievalMetrics.RetrievedContext("noise", "Day03.md", "仍然不相关")
        );

        RetrievalMetrics.Scores scores = RetrievalMetrics.evaluate(item.expectedSources(), retrieved);
        RetrievalMetrics.GateResult gate = RetrievalMetrics.gate(
                scores,
                new RetrievalMetrics.Thresholds(1.0, 1.0, 1.0, 1.0, 0.20)
        );

        assertFalse(gate.passed());
        assertEquals(0.0, scores.recallAt5());
        assertTrue(gate.failures().contains("recallAt5"));
        assertTrue(gate.failures().contains("ndcgAt10"));
    }

    private List<GoldenItem> loadSmokeItems() throws Exception {
        List<GoldenItem> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/rag-golden-dataset.jsonl"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject json = JSON.parseObject(line);
                if (json.getJSONArray("tags").contains("smoke")) {
                    items.add(new GoldenItem(
                            json.getString("id"),
                            json.getString("query"),
                            json.getJSONArray("expectedSources").toJavaList(String.class)
                    ));
                }
            }
        }
        return items;
    }

    private MetricScores scores(double value) {
        MetricScores scores = new MetricScores();
        scores.setFaithfulness(value);
        scores.setAnswerRelevance(value);
        scores.setContextPrecision(value);
        scores.setContextRecall(value);
        scores.setPassed(value >= 0.85);
        return scores;
    }

    private LlmAsAJudgeEvaluator mockJudge(MetricScores scores) {
        RagasEvaluator ragasEvaluator = mock(RagasEvaluator.class);
        when(ragasEvaluator.evaluateSingle(anyString(), anyString(), anyList(), anyString())).thenReturn(scores);
        RagasEvalConfig config = new RagasEvalConfig();
        config.setThreshold(0.85);
        return new LlmAsAJudgeEvaluator(ragasEvaluator, config);
    }

    private record GoldenItem(String id, String query, List<String> expectedSources) {
    }
}
