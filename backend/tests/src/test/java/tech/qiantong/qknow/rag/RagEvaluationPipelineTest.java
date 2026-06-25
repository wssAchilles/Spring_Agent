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
    void smokeDatasetLoadsAndMockGatePasses() throws Exception {
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
    void lowScoreFailsGate() {
        LlmAsAJudgeEvaluator.Judgement judgement = mockJudge(scores(0.7))
                .evaluate("query", "answer", List.of("context"), "expected");

        assertFalse(judgement.passed());
        assertTrue(judgement.scores().getFaithfulness() < judgement.threshold());
    }

    private List<GoldenItem> loadSmokeItems() throws Exception {
        List<GoldenItem> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/rag-golden-dataset.jsonl"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject json = JSON.parseObject(line);
                if (json.getJSONArray("tags").contains("smoke")) {
                    items.add(new GoldenItem(json.getString("id"), json.getString("query")));
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

    private record GoldenItem(String id, String query) {
    }
}
