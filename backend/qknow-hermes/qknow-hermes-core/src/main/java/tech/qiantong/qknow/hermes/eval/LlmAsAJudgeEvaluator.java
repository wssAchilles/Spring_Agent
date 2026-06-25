package tech.qiantong.qknow.hermes.eval;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LlmAsAJudgeEvaluator {

    private final RagasEvaluator ragasEvaluator;
    private final RagasEvalConfig config;

    public LlmAsAJudgeEvaluator(RagasEvaluator ragasEvaluator, RagasEvalConfig config) {
        this.ragasEvaluator = ragasEvaluator;
        this.config = config;
    }

    public Judgement evaluate(String query, String answer, List<String> contexts, String expectedAnswer) {
        MetricScores scores = ragasEvaluator.evaluateSingle(query, answer, contexts, expectedAnswer);
        boolean passed = scores.isAboveThreshold(config.getThreshold());
        return new Judgement(scores, passed, config.getThreshold());
    }

    public record Judgement(MetricScores scores, boolean passed, double threshold) {
    }
}
