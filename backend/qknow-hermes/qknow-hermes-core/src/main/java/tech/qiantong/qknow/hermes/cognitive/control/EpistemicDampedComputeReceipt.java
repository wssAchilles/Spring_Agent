package tech.qiantong.qknow.hermes.cognitive.control;

import java.util.Objects;

/**
 * 认知阻尼步级控算不可变凭单（Phase 11.1 ED-SCR，定理 1）。
 */
public record EpistemicDampedComputeReceipt(
        StepComputeRoutingReceipt baseReceipt,
        double rawDifficultyScore,
        double dampingFactorGamma,
        double dampedDifficultyScore,
        double geodesicInnovationDeltaI,
        int consecutiveLowInnovationSteps,
        double thinkBudgetUtilizationPsi,
        int recommendedMaxCompletionTokens,
        boolean downshiftedByDamping,
        String dampingRationale
) {
    public EpistemicDampedComputeReceipt {
        baseReceipt = Objects.requireNonNull(baseReceipt, "baseReceipt 不能为空");
        rawDifficultyScore = clamp01(rawDifficultyScore);
        dampingFactorGamma = Math.max(0.0, Math.min(1.0, Double.isNaN(dampingFactorGamma) ? 1.0 : dampingFactorGamma));
        dampedDifficultyScore = clamp01(dampedDifficultyScore);
        geodesicInnovationDeltaI = Double.isNaN(geodesicInnovationDeltaI)
                ? 0.0
                : Math.max(0.0, Math.min(2.0, geodesicInnovationDeltaI));
        consecutiveLowInnovationSteps = Math.max(0, consecutiveLowInnovationSteps);
        thinkBudgetUtilizationPsi = clamp01(thinkBudgetUtilizationPsi);
        recommendedMaxCompletionTokens = Math.max(64, recommendedMaxCompletionTokens);
        dampingRationale = dampingRationale != null ? dampingRationale : "";
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0.0 : Math.max(0.0, Math.min(1.0, v));
    }
}
