package tech.qiantong.qknow.hermes.coalition.dto;

import java.util.Arrays;

/**
 * 跨域审计裁决结果 Record
 */
public record CrossDomainAuditResolution(
    String proposalId,
    boolean allowed,
    boolean softProjected,
    double[] safeActionVector,
    double cbfMargin,
    String auditReason,
    long evaluatedTimestamp
) {
    public CrossDomainAuditResolution {
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("proposalId 不能为空");
        }
    }
}
