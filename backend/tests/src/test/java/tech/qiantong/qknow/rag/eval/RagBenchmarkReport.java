package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RagBenchmarkReport(
        String datasetHash,
        String configHash,
        DatasetEvidenceLevel datasetEvidenceLevel,
        Map<String, MetricEstimate> metrics,
        Map<String, Map<String, MetricEstimate>> strata,
        List<FailureSample> failures
) {

    public RagBenchmarkReport {
        Objects.requireNonNull(datasetEvidenceLevel, "datasetEvidenceLevel");
        metrics = Map.copyOf(metrics);
        Map<String, Map<String, MetricEstimate>> copiedStrata = new LinkedHashMap<>();
        strata.forEach((name, values) -> copiedStrata.put(name, Map.copyOf(values)));
        strata = Map.copyOf(copiedStrata);
        failures = List.copyOf(failures);
    }

    public enum MetricStatus {
        VALID,
        INVALID,
        NOT_EVALUATED
    }

    public enum MetricErrorCode {
        SUT_SENTINEL_FAILED,
        EVALUATOR_TIMEOUT,
        EVALUATOR_MODEL_ERROR,
        EVALUATOR_PARSE_ERROR,
        MISSING_LIVE_CONFIGURATION
    }

    public enum DatasetEvidenceLevel {
        ENGINEERING_BASELINE
    }

    public record MetricEstimate(
            @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
            Double value,
            @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
            Double ciLow,
            @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
            Double ciHigh,
            int n,
            MetricStatus status,
            MetricErrorCode errorCode
    ) {
        public MetricEstimate {
            Objects.requireNonNull(status, "status");
            if (status == MetricStatus.VALID) {
                Objects.requireNonNull(value, "value");
                Objects.requireNonNull(ciLow, "ciLow");
                Objects.requireNonNull(ciHigh, "ciHigh");
                if (!Double.isFinite(value) || !Double.isFinite(ciLow) || !Double.isFinite(ciHigh)
                        || ciLow > value || value > ciHigh) {
                    throw new IllegalArgumentException("A valid metric requires a finite ordered interval");
                }
                if (n <= 0) {
                    throw new IllegalArgumentException("A valid metric requires n > 0");
                }
                if (errorCode != null) {
                    throw new IllegalArgumentException("A valid metric cannot have an error code");
                }
            } else {
                value = null;
                ciLow = null;
                ciHigh = null;
                if (status == MetricStatus.INVALID) {
                    Objects.requireNonNull(errorCode, "errorCode");
                } else if (errorCode != null) {
                    throw new IllegalArgumentException("A metric that was not evaluated cannot have an error code");
                }
            }
        }

        public static MetricEstimate valid(double value, double ciLow, double ciHigh, int n) {
            return new MetricEstimate(value, ciLow, ciHigh, n, MetricStatus.VALID, null);
        }

        public static MetricEstimate invalid(MetricErrorCode errorCode) {
            return new MetricEstimate(null, null, null, 0, MetricStatus.INVALID, errorCode);
        }

        public static MetricEstimate notEvaluated() {
            return new MetricEstimate(null, null, null, 0, MetricStatus.NOT_EVALUATED, null);
        }
    }

    public record FailureSample(String queryId, String familyId, String errorCode, String detail) {
    }
}
