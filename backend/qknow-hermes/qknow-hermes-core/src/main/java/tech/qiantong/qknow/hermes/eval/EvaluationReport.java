package tech.qiantong.qknow.hermes.eval;

import lombok.Data;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class EvaluationReport {
    private String runId;
    private String datasetName;
    private Map<String, Double> metricScores;
    private List<ItemResult> itemResults;
    private ReportSummary summary;

    @Data
    public static class ItemResult {
        private String query;
        private String answer;
        private Map<String, MetricResult> scores;
        private String feedback;

        public void setScores(Map<String, ?> scores) {
            if (scores == null) {
                this.scores = null;
                return;
            }
            Map<String, MetricResult> normalized = new LinkedHashMap<>();
            scores.forEach((name, value) -> normalized.put(name, normalizeMetric(value)));
            this.scores = normalized;
        }

        private MetricResult normalizeMetric(Object value) {
            if (value instanceof MetricResult metric) {
                return metric;
            }
            if (value instanceof Number number) {
                double score = number.doubleValue();
                if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
                    throw new IllegalArgumentException("metric score must be finite and between 0 and 1");
                }
                return new MetricResult(EvaluationStatus.VALID, score, null, null);
            }
            if (value instanceof Map<?, ?> fields) {
                Object rawStatus = fields.get("status");
                EvaluationStatus status = rawStatus instanceof EvaluationStatus evaluationStatus
                        ? evaluationStatus : EvaluationStatus.valueOf(String.valueOf(rawStatus));
                Object rawValue = fields.get("value");
                Double score = rawValue instanceof Number number ? number.doubleValue() : null;
                if (status == EvaluationStatus.VALID
                        && (score == null || !Double.isFinite(score) || score < 0.0 || score > 1.0)) {
                    throw new IllegalArgumentException("valid metric requires a score between 0 and 1");
                }
                return new MetricResult(status, status == EvaluationStatus.VALID ? score : null,
                        stringValue(fields.get("errorCode")), stringValue(fields.get("reason")));
            }
            throw new IllegalArgumentException("unsupported metric result");
        }

        private String stringValue(Object value) {
            return value != null ? String.valueOf(value) : null;
        }
    }

    public record MetricResult(EvaluationStatus status, Double value, String errorCode, String reason) {
        public MetricResult {
            if (status == null) {
                throw new IllegalArgumentException("metric status is required");
            }
            switch (status) {
                case VALID -> {
                    if (value == null || !Double.isFinite(value) || value < 0.0 || value > 1.0) {
                        throw new IllegalArgumentException("valid metric requires a score between 0 and 1");
                    }
                    if (errorCode != null || reason != null) {
                        throw new IllegalArgumentException("valid metric cannot contain an error");
                    }
                }
                case INVALID -> {
                    value = null;
                    if (errorCode == null || errorCode.isBlank() || reason == null || reason.isBlank()) {
                        throw new IllegalArgumentException("invalid metric requires an error code and reason");
                    }
                }
                case NOT_EVALUATED -> {
                    value = null;
                    errorCode = null;
                    reason = null;
                }
            }
        }
    }

    @Data
    public static class ReportSummary {
        private Map<String, Double> mean;
        private Map<String, Double> p50;
        private Map<String, Double> p90;

        public ReportSummary() {}

        public ReportSummary(Map<String, Double> mean, Map<String, Double> p50, Map<String, Double> p90) {
            this.mean = mean;
            this.p50 = p50;
            this.p90 = p90;
        }
    }

    public String toJson() {
        return JSON.toJSONString(this, JSONWriter.Feature.WriteMapNullValue);
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Evaluation Report: ").append(datasetName).append("\n\n");
        sb.append("Run ID: ").append(runId).append("\n\n");
        sb.append("## Metric Scores\n\n");
        if (metricScores != null) {
            metricScores.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(String.format("%.4f", v)).append("\n"));
        }
        sb.append("\n## Metric Summary\n\n");
        if (summary != null && summary.getMean() != null) {
            summary.getMean().forEach((k, v) -> sb.append("- ").append(k).append(" mean: ").append(String.format("%.4f", v)).append("\n"));
        } else {
            sb.append("N/A\n");
        }
        return sb.toString();
    }
}
