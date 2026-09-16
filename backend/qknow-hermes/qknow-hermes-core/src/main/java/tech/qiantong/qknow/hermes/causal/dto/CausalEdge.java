package tech.qiantong.qknow.hermes.causal.dto;

/**
 * 因果拓扑有向边模型
 *
 * @param sourceId         源节点标识
 * @param targetId         目标节点标识
 * @param weight           因果传递权重
 * @param isConfounderEdge 是否为混淆后门边
 */
public record CausalEdge(
        String sourceId,
        String targetId,
        double weight,
        boolean isConfounderEdge
) {
    public CausalEdge {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId 不能为空");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId 不能为空");
        }
    }
}
