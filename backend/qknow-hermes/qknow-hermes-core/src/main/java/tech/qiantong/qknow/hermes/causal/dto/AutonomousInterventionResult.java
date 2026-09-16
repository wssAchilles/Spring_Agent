package tech.qiantong.qknow.hermes.causal.dto;

/**
 * 自主干预决策裁决结果
 *
 * @param action               干预裁决动作
 * @param intervened           是否发生了干预修正 (非 PASS_DIRECT 时为 true)
 * @param selectedBranchId     选中的执行分支 ID
 * @param modifiedActionVector 经 QP 软投影修补后的动作参数向量
 * @param barrierMargin        控制屏障函数裕度值 h(x)
 * @param rationale            干预裁决理由说明
 * @param elapsedNanos         单步裁决耗时 (纳秒)
 */
public record AutonomousInterventionResult(
        AutonomousInterventionAction action,
        boolean intervened,
        String selectedBranchId,
        float[] modifiedActionVector,
        double barrierMargin,
        String rationale,
        long elapsedNanos
) {
    public AutonomousInterventionResult {
        if (action == null) {
            throw new IllegalArgumentException("action 不能为空");
        }
    }
}
