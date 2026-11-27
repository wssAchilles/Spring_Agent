package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

import java.util.Objects;

/**
 * 因果演化链单步不可变节点契约（Java 21 Record）。
 * <p>
 * 按因果前驱与时间偏序拓扑排列，完整携带双时态边界、谓词基数分类、综合相关性分数与人类可读命题文本。
 * </p>
 */
public record CausalEvolutionStep(
        int stepRank,
        String edgeId,
        String sourceNodeId,
        String relationPredicate,
        String targetNodeId,
        PredicateCardinality cardinality,
        long validFromEpochMs,
        Long validToEpochMs,
        long txFromEpochMs,
        String causalPredecessorEdgeId,
        double compositeScore,
        String propositionText
) {
    public CausalEvolutionStep {
        Objects.requireNonNull(edgeId, "edgeId 不能为空");
        Objects.requireNonNull(sourceNodeId, "sourceNodeId 不能为空");
        Objects.requireNonNull(relationPredicate, "relationPredicate 不能为空");
        Objects.requireNonNull(targetNodeId, "targetNodeId 不能为空");
        Objects.requireNonNull(cardinality, "cardinality 不能为空");
        propositionText = propositionText != null ? propositionText : "";
    }
}
