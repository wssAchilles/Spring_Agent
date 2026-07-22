package tech.qiantong.qknow.hermes.eval;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class MetricScores {
    public static final double DEFAULT_GATE_THRESHOLD = 0.85;
    public static final List<String> METRIC_NAMES = List.of(
            "faithfulness", "answer_relevance", "context_precision", "context_recall",
            "factual_correctness", "noise_sensitivity", "negative_rejection");

    private double faithfulness;
    private double answerRelevance;
    private double contextPrecision;
    private double contextRecall;
    private double factualCorrectness;
    private double noiseSensitivity;
    private double negativeRejection;
    private boolean passed;
    @lombok.Getter(lombok.AccessLevel.NONE)
    private final Map<String, EvaluationStatus> statuses = new LinkedHashMap<>();
    @lombok.Getter(lombok.AccessLevel.NONE)
    private final Map<String, EvaluationError> errors = new LinkedHashMap<>();

    public boolean isAboveThreshold(double threshold) {
        return isValid("faithfulness") && isValid("answer_relevance")
               && isValid("context_precision") && isValid("context_recall")
               && isValid("factual_correctness")
               && faithfulness >= threshold && answerRelevance >= threshold
               && contextPrecision >= threshold && contextRecall >= threshold
               && factualCorrectness >= threshold;
    }

    public EvaluationStatus getStatus(String metricName) {
        return statuses.getOrDefault(metricName, EvaluationStatus.NOT_EVALUATED);
    }

    public String getErrorCode(String metricName) {
        EvaluationError error = errors.get(metricName);
        return error != null ? error.getCode() : null;
    }

    public String getReason(String metricName) {
        EvaluationError error = errors.get(metricName);
        return error != null ? error.getReason() : null;
    }

    public boolean isValid(String metricName) {
        return getStatus(metricName) == EvaluationStatus.VALID;
    }

    public void markInvalid(String metricName, EvaluationError error) {
        requireKnownMetric(metricName);
        if (error == null) {
            throw new IllegalArgumentException("error is required");
        }
        statuses.put(metricName, EvaluationStatus.INVALID);
        errors.put(metricName, error);
    }

    public void markAllInvalid(EvaluationError error) {
        METRIC_NAMES.forEach(name -> markInvalid(name, error));
    }

    public Map<String, EvaluationReport.MetricResult> toReportMetrics() {
        Map<String, EvaluationReport.MetricResult> result = new LinkedHashMap<>();
        for (String name : METRIC_NAMES) {
            EvaluationStatus status = getStatus(name);
            result.put(name, new EvaluationReport.MetricResult(
                    status,
                    status == EvaluationStatus.VALID ? getScore(name) : null,
                    getErrorCode(name),
                    getReason(name)));
        }
        return result;
    }

    private void markValid(String metricName) {
        statuses.put(metricName, EvaluationStatus.VALID);
        errors.remove(metricName);
    }

    public void setFaithfulness(double value) {
        validateScore(value);
        faithfulness = value;
        markValid("faithfulness");
    }

    public void setAnswerRelevance(double value) {
        validateScore(value);
        answerRelevance = value;
        markValid("answer_relevance");
    }

    public void setContextPrecision(double value) {
        validateScore(value);
        contextPrecision = value;
        markValid("context_precision");
    }

    public void setContextRecall(double value) {
        validateScore(value);
        contextRecall = value;
        markValid("context_recall");
    }

    public void setFactualCorrectness(double value) {
        validateScore(value);
        factualCorrectness = value;
        markValid("factual_correctness");
    }

    public void setNoiseSensitivity(double value) {
        validateScore(value);
        noiseSensitivity = value;
        markValid("noise_sensitivity");
    }

    public void setNegativeRejection(double value) {
        validateScore(value);
        negativeRejection = value;
        markValid("negative_rejection");
    }

    private void requireKnownMetric(String metricName) {
        if (!METRIC_NAMES.contains(metricName)) {
            throw new IllegalArgumentException("unknown metric");
        }
    }

    private void validateScore(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("metric score must be finite and between 0 and 1");
        }
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
