package tech.qiantong.qknow.ai.superagent;

/**
 * Phase 60: 四维帕累托多目标进化适应度评估器
 * <p>
 * 基于定理 1.2，综合任务成功率、Token 经济性、SLA 延迟与安全合规，执行一票否决与强排序。
 */
public class EvolutionaryFitnessEvaluator {

    public static final double WEIGHT_SUCCESS_RATE = 0.35;
    public static final double WEIGHT_TOKEN_EFFICIENCY = 0.25;
    public static final double WEIGHT_LATENCY_MARGIN = 0.20;
    public static final double WEIGHT_SAFETY_COMPLIANCE = 0.20;

    public static final double PROMOTION_FITNESS_THRESHOLD = 0.80;
    public static final double STRICT_SAFETY_THRESHOLD = 1.0;

    public record FitnessEvaluationResult(
            double overallFitnessScore,
            double taskSuccessRate,
            double tokenEfficiency,
            double latencyMargin,
            double safetyCompliance,
            boolean promotionApproved,
            String decisionSummary
    ) {}

    /**
     * 执行四维帕累托多目标适应度评估
     */
    public FitnessEvaluationResult evaluate(
            double taskSuccessRate,
            double tokenEfficiency,
            double latencyMargin,
            double safetyCompliance
    ) {
        // 参数归一化安全截断 [0.0, 1.0]
        double s = clamp(taskSuccessRate);
        double e = clamp(tokenEfficiency);
        double l = clamp(latencyMargin);
        double c = clamp(safetyCompliance);

        // 加权线性加和
        double weightedScore = s * WEIGHT_SUCCESS_RATE
                + e * WEIGHT_TOKEN_EFFICIENCY
                + l * WEIGHT_LATENCY_MARGIN
                + c * WEIGHT_SAFETY_COMPLIANCE;

        // 安全合规一票否决机制 (Strict Compliance Redline)
        boolean safetyPassed = c >= (STRICT_SAFETY_THRESHOLD - 1e-6);
        boolean scorePassed = weightedScore >= PROMOTION_FITNESS_THRESHOLD;

        boolean approved;
        String summary;

        if (!safetyPassed) {
            approved = false;
            // 一票否决惩罚截断
            weightedScore = Math.min(weightedScore, 0.49);
            summary = String.format("安全合规得分 %.2f 低于绝对红线 1.00，触发一票否决拒绝晋级", c);
        } else if (!scorePassed) {
            approved = false;
            summary = String.format("综合适应度得分 %.4f 未达到准入门槛 %.2f，拒绝晋级", weightedScore, PROMOTION_FITNESS_THRESHOLD);
        } else {
            approved = true;
            summary = String.format("四维帕累托指标全量达标（得分 %.4f），获批晋级生态候选策略池", weightedScore);
        }

        return new FitnessEvaluationResult(
                weightedScore, s, e, l, c, approved, summary
        );
    }

    private static double clamp(double val) {
        return Math.max(0.0, Math.min(1.0, val));
    }
}
