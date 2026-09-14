package tech.qiantong.qknow.ai.audit.causal;

/**
 * 因果拓扑图有向边实体
 *
 * @param sourceId 前序因果来源节点 ID
 * @param targetId 后继被影响节点 ID
 * @param relationType 因果依赖关系类型 (如 DECOMPOSED_TO, RETRIEVED_FROM, INFERRED_BY, CONVERGED_AT)
 * @param edgeWeight 关联强度
 *
 * @author qknow
 */
public record CausalEdge(
        String sourceId,
        String targetId,
        String relationType,
        double edgeWeight
) {}
