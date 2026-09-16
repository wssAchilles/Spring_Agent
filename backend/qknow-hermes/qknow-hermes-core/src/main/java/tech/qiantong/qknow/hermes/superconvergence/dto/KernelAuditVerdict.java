package tech.qiantong.qknow.hermes.superconvergence.dto;

/**
 * 内核安全审计判定与修补 Record
 */
public record KernelAuditVerdict(
    String verdictId,
    String proposalId,
    boolean isPermitted,
    boolean isRepaired,
    double[] finalActionVector,
    double barrierMargin,
    String rejectionReason,
    long auditLatencyUs,
    long timestamp
) {
    public KernelAuditVerdict {
        if (verdictId == null || verdictId.isBlank()) {
            throw new IllegalArgumentException("verdictId 不能为空");
        }
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("proposalId 不能为空");
        }
    }
}
