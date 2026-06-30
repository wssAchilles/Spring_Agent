package tech.qiantong.qknow.hermes.eval;

import lombok.Data;

@Data
public class MetricScores {
    public static final double DEFAULT_GATE_THRESHOLD = 0.85;

    private double faithfulness;
    private double answerRelevance;
    private double contextPrecision;
    private double contextRecall;
    private double factualCorrectness;
    private double noiseSensitivity;
    private double negativeRejection;
    private boolean passed;

    public boolean isAboveThreshold(double threshold) {
        return faithfulness >= threshold && answerRelevance >= threshold
               && contextPrecision >= threshold && contextRecall >= threshold
               && factualCorrectness >= threshold;
    }

    public double getScore(String metricName) {
        switch (metricName) {
            case "faithfulness": return getFaithfulness();
            case "answer_relevance": return getAnswerRelevance();
            case "context_precision": return getContextPrecision();
            case "context_recall": return getContextRecall();
            case "factual_correctness": return getFactualCorrectness();
            case "noise_sensitivity": return getNoiseSensitivity();
            case "negative_rejection": return getNegativeRejection();
            default: return 0.0;
        }
    }

    public boolean isAllAboveThreshold() {
        return isAboveThreshold(DEFAULT_GATE_THRESHOLD);
    }
}
