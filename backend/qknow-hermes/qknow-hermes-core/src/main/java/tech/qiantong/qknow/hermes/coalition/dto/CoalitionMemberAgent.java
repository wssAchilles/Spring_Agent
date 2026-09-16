package tech.qiantong.qknow.hermes.coalition.dto;

import java.util.Arrays;

/**
 * 跨组织联盟成员智能体实体
 */
public record CoalitionMemberAgent(
    String agentId,
    OrganizationDomain domain,
    String capabilityTag,
    double initialCredit,
    double[] intentEmbedding,
    long registeredTimestamp
) {
    public CoalitionMemberAgent {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        if (domain == null) {
            throw new IllegalArgumentException("domain 不能为空");
        }
        if (intentEmbedding == null || intentEmbedding.length != 1536) {
            throw new IllegalArgumentException("intentEmbedding 必须严格为阿里千问 1536 维超球面向量");
        }
        // 严格超球面保模校验
        double normSq = 0.0;
        for (double v : intentEmbedding) {
            normSq += v * v;
        }
        double norm = Math.sqrt(normSq);
        if (Math.abs(norm - 1.0) > 1e-4) {
            throw new IllegalArgumentException("intentEmbedding 模长必须为 1.0±1e-4，当前为: " + norm);
        }
    }
}
