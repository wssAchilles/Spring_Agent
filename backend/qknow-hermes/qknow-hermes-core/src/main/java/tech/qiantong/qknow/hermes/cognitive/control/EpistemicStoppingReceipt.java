package tech.qiantong.qknow.hermes.cognitive.control;

/**
 * 超球面预期信息增益感知与最优停机不可变凭单（Java 21 Record，对应 HEIG-BAS）。
 */
public record EpistemicStoppingReceipt(
        String requestId,
        int stepIndex,
        double geodesicInnovationDeltaI,
        double marginalVoi,
        double budgetPressureRho,
        int remainingToolCalls,
        StoppingDecision decision,
        String tailBudgetTag,
        long timestampMs
) {

    public enum StoppingDecision {
        /** 新息充足且预算宽松：继续当前检索/推演路径深挖 */
        CONTINUE_DEEPEN,
        /** 新息偏低但预算仍足够：建议切换检索维度或工具路径 */
        PIVOT_EXPLORE,
        /** 信息增益枯竭或预算压力触顶：执行确定性最优停机并直接归纳作答 */
        STOP_AND_SYNTHESIZE
    }
}
