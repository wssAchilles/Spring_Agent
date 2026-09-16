package tech.qiantong.qknow.hermes.superconvergence.dto;

/**
 * 内核动作执行提案 Record
 */
public record KernelExecutionProposal(
    String proposalId,
    String sourceDomain,
    String actionType,
    double[] actionVector,
    double nominalRiskScore,
    long nonce,
    long timestamp
) {
    public KernelExecutionProposal {
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("proposalId 不能为空");
        }
        if (sourceDomain == null || sourceDomain.isBlank()) {
            throw new IllegalArgumentException("sourceDomain 不能为空");
        }
        if (actionType == null || actionType.isBlank()) {
            throw new IllegalArgumentException("actionType 不能为空");
        }
        if (actionVector == null || actionVector.length == 0) {
            throw new IllegalArgumentException("actionVector 不能为空");
        }
    }
}
