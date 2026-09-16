package tech.qiantong.qknow.hermes.synergy.dto;

/**
 * 协同智能体节点模型
 *
 * @param agentId          智能体唯一标识
 * @param layer            所属动态分层层级
 * @param qwenEmbedding    阿里千问 1536 维超球面单位向量 (||v||_2 = 1.0 +- 1e-4)
 * @param loadScore        实时负载评分 (0.0 空闲 ~ 1.0 满载)
 * @param reputationWeight 协同信誉权重 (0.1 ~ 1.0)
 */
public record SynergyAgentNode(
        String agentId,
        AgentHierarchyLayer layer,
        float[] qwenEmbedding,
        double loadScore,
        double reputationWeight
) {
    public static final int EXPECTED_DIMENSION = 1536;

    public SynergyAgentNode {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        if (layer == null) {
            throw new IllegalArgumentException("layer 不能为空");
        }
        if (qwenEmbedding == null || qwenEmbedding.length != EXPECTED_DIMENSION) {
            throw new IllegalArgumentException("千问嵌入向量必须严格为 1536 维，当前为: " + (qwenEmbedding == null ? 0 : qwenEmbedding.length));
        }
        double sumSq = 0.0;
        for (float v : qwenEmbedding) {
            sumSq += (double) v * v;
        }
        double norm = Math.sqrt(sumSq);
        if (Math.abs(norm - 1.0) > 1e-4) {
            throw new IllegalArgumentException("千问嵌入向量模长必须严格为 1.0 +- 1e-4，当前为: " + norm);
        }
    }
}
