package tech.qiantong.qknow.hermes.eval;

import lombok.Data;
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
        private Map<String, Double> scores;
        private String feedback;
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
        return com.alibaba.fastjson2.JSON.toJSONString(this);
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
