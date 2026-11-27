package tech.qiantong.qknow.hermes.cognitive.control;

import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;

import java.util.Arrays;
import java.util.Objects;

/**
 * 步级异构算力路由不可变凭单（Phase 9.1 ZCEA-SCR，定理 2）。
 */
public record StepComputeRoutingReceipt(
        String requestId,
        int stepIndex,
        CognitiveStepPhase phase,
        boolean isolatedContext,
        double sphericalVariance,
        double firstHitGeodesicResidual,
        double top2CosineMargin,
        double difficultyScore,
        double topologicalErrorWeight,
        DeepSeekReasoningEffort chosenEffort,
        double[] calibratedThresholds,
        boolean conformalCalibrated,
        String rationale,
        long timestampMs
) {
    public StepComputeRoutingReceipt {
        requestId = (requestId == null || requestId.isBlank()) ? "req-step-compute" : requestId;
        phase = Objects.requireNonNullElse(phase, CognitiveStepPhase.EXPLORE_RETRIEVE);
        chosenEffort = Objects.requireNonNullElse(chosenEffort, DeepSeekReasoningEffort.LOW);
        calibratedThresholds = calibratedThresholds != null
                ? Arrays.copyOf(calibratedThresholds, calibratedThresholds.length)
                : new double[]{0.25, 0.50, 0.78};
        rationale = rationale != null ? rationale : "";
    }

    @Override
    public double[] calibratedThresholds() {
        return Arrays.copyOf(calibratedThresholds, calibratedThresholds.length);
    }
}
