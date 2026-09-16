package tech.qiantong.qknow.hermes.synergy.dto;

/**
 * 涌现决策提案
 *
 * @param proposalId      提案唯一标识
 * @param proposerAgentId 发起智能体标识
 * @param proposedPlan    建议的业务行动方案
 * @param utilityScore    发起方效用评分 (0.0 ~ 1.0)
 * @param riskScore       预测安全风险评分 (0.0 ~ 1.0)
 */
public record EmergentDecisionProposal(
        String proposalId,
        String proposerAgentId,
        String proposedPlan,
        double utilityScore,
        double riskScore
) {
    public EmergentDecisionProposal {
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("proposalId 不能为空");
        }
        if (proposedPlan == null || proposedPlan.isBlank()) {
            throw new IllegalArgumentException("proposedPlan 不能为空");
        }
    }
}
