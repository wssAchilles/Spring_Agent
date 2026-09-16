package tech.qiantong.qknow.hermes.synergy.dto;

import java.util.List;
import java.util.Map;

/**
 * 分层动态重组拓扑方案
 *
 * @param planId                重组方案唯一标识
 * @param generation            拓扑重组代际号
 * @param nodeLayers            各智能体所处分层映射
 * @param activeTopology        活跃通信邻接拓扑
 * @param algebraicConnectivity 图拉普拉斯代数连通度 (Fiedler 特征值 lambda_2)
 * @param elapsedNanos          单步重组计算耗时 (纳秒)
 */
public record TopologyRecombinationPlan(
        String planId,
        int generation,
        Map<String, AgentHierarchyLayer> nodeLayers,
        Map<String, List<String>> activeTopology,
        double algebraicConnectivity,
        long elapsedNanos
) {
    public TopologyRecombinationPlan {
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("planId 不能为空");
        }
        if (nodeLayers == null || activeTopology == null) {
            throw new IllegalArgumentException("拓扑映射不能为空");
        }
    }
}
