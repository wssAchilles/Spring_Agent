package tech.qiantong.qknow.hermes.coalition.dto;

import java.util.Arrays;

/**
 * 跨域事务提案 Record
 */
public record CrossDomainTransactionProposal(
    String proposalId,
    String sourceAgentId,
    OrganizationDomain sourceDomain,
    OrganizationDomain targetDomain,
    String actionType,
    double requestedQuota,
    boolean isDestructiveWrite,
    double[] actionVector,
    long timestamp,
    String nonce
) {
    public CrossDomainTransactionProposal {
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("proposalId 不能为空");
        }
        if (sourceAgentId == null || sourceAgentId.isBlank()) {
            throw new IllegalArgumentException("sourceAgentId 不能为空");
        }
        if (sourceDomain == null || targetDomain == null) {
            throw new IllegalArgumentException("sourceDomain 与 targetDomain 不能为空");
        }
        if (nonce == null || nonce.isBlank()) {
            throw new IllegalArgumentException("nonce 不能为空");
        }
    }
}
