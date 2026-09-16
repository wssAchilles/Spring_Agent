package tech.qiantong.qknow.hermes.causal.dto;

/**
 * 时序反事实推演沙盘单分支推演节点
 *
 * @param branchId             分支唯一标识
 * @param proposedAction       假设执行动作
 * @param depth                推演时间步深度 (1 <= depth <= 5)
 * @param predictedLatentState 千问 1536 维预测潜态向量
 * @param riskScore            未来风险预估评分 (0.0 表示绝对安全, 1.0 表示极度危险)
 * @param expectedUtility      未来预期业务效用评分 (0.0 ~ 1.0)
 */
public record CounterfactualSandboxBranch(
        String branchId,
        String proposedAction,
        int depth,
        float[] predictedLatentState,
        double riskScore,
        double expectedUtility
) {
    public CounterfactualSandboxBranch {
        if (branchId == null || branchId.isBlank()) {
            throw new IllegalArgumentException("branchId 不能为空");
        }
        if (proposedAction == null || proposedAction.isBlank()) {
            throw new IllegalArgumentException("proposedAction 不能为空");
        }
    }
}
