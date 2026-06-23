package tech.qiantong.qknow.hermes.eval;

import lombok.Data;

@Data
public class MetricScores {
    private double faithfulness;
    private double answerRelevance;
    private double contextPrecision;
    private double contextRecall;
    private boolean passed;

    public boolean isAboveThreshold(double threshold) {
        return faithfulness >= threshold && answerRelevance >= threshold
               && contextPrecision >= threshold && contextRecall >= threshold;
    }

    public double getScore(String metricName) {
        switch (metricName) {
            case "faithfulness": return getFaithfulness();
            case "answer_relevance": return getAnswerRelevance();
            case "context_precision": return getContextPrecision();
            case "context_recall": return getContextRecall();
            default: return 0.0;
        }
    }

    public boolean isAllAboveThreshold() {
        return isAboveThreshold(0.5);
    }
}
