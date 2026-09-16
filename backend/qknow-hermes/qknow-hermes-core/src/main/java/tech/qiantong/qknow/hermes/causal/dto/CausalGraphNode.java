package tech.qiantong.qknow.hermes.causal.dto;

import java.util.Map;

/**
 * 因果拓扑图节点模型
 *
 * @param nodeId            节点唯一标识
 * @param nodeName          节点名称
 * @param nodeType          节点类型 (OBSERVATION, CONFOUNDER, INTENT, ACTION, STATE)
 * @param exogenousVariance 外生方差扰动
 * @param attributes        扩展属性映射
 */
public record CausalGraphNode(
        String nodeId,
        String nodeName,
        String nodeType,
        double exogenousVariance,
        Map<String, Object> attributes
) {
    public CausalGraphNode {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId 不能为空");
        }
        if (nodeName == null || nodeName.isBlank()) {
            throw new IllegalArgumentException("nodeName 不能为空");
        }
        if (nodeType == null || nodeType.isBlank()) {
            throw new IllegalArgumentException("nodeType 不能为空");
        }
    }
}
