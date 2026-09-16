package tech.qiantong.qknow.hermes.synergy.dto;

/**
 * 涌现决策博弈收敛仲裁结果
 *
 * @param resolutionId  裁决唯一标识
 * @param agreedPlan    达成共识的执行方案
 * @param roundsTaken   博弈协商收敛轮次 (<= 3 轮)
 * @param converged     是否达成帕累托最优收敛
 * @param barrierMargin 控制屏障裕度值 h(x)
 * @param cbfIntervened 是否触发了 CBF 安全拦截或软投影修正
 * @param elapsedNanos  单步裁决耗时 (纳秒)
 */
public record EmergentDecisionResolution(
        String resolutionId,
        String agreedPlan,
        int roundsTaken,
        boolean converged,
        double barrierMargin,
        boolean cbfIntervened,
        long elapsedNanos
) {
    public EmergentDecisionResolution {
        if (resolutionId == null || resolutionId.isBlank()) {
            throw new IllegalArgumentException("resolutionId 不能为空");
        }
        if (agreedPlan == null || agreedPlan.isBlank()) {
            throw new IllegalArgumentException("agreedPlan 不能为空");
        }
    }
}
