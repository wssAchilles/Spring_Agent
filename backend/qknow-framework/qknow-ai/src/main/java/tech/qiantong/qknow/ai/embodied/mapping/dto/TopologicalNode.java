package tech.qiantong.qknow.ai.embodied.mapping.dto;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 分层拓扑场景图节点数据模型 (Hierarchical Topological Node)
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class TopologicalNode {

    public enum LayerLevel {
        VOXEL,      // 底层几何度量体素节点 (L0)
        CORRIDOR,   // 中层 Voronoi 通道骨架节点 (L1)
        ROOM        // 顶层语义区域/房间节点 (L2)
    }

    private final String nodeId;
    private final double[] position;
    private final LayerLevel layerLevel;
    private final double[] qwenSemanticEmbedding; // 阿里千问 1536 维超球面单位特征向量
    private final Map<String, Double> neighborDistances;

    public TopologicalNode(String nodeId, double[] position, LayerLevel layerLevel, double[] qwenSemanticEmbedding) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        this.position = position != null ? position.clone() : new double[]{0.0, 0.0, 0.0};
        this.layerLevel = layerLevel != null ? layerLevel : LayerLevel.CORRIDOR;
        this.qwenSemanticEmbedding = qwenSemanticEmbedding != null ? qwenSemanticEmbedding.clone() : null;
        this.neighborDistances = new HashMap<>();
    }

    public void addNeighbor(String targetNodeId, double distance) {
        if (targetNodeId != null && !targetNodeId.equals(this.nodeId)) {
            this.neighborDistances.put(targetNodeId, Math.max(0.0, distance));
        }
    }

    public String nodeId() {
        return nodeId;
    }

    public double[] position() {
        return position.clone();
    }

    public LayerLevel layerLevel() {
        return layerLevel;
    }

    public double[] qwenSemanticEmbedding() {
        return qwenSemanticEmbedding != null ? qwenSemanticEmbedding.clone() : null;
    }

    public Map<String, Double> neighborDistances() {
        return Collections.unmodifiableMap(neighborDistances);
    }

    public double distanceTo(TopologicalNode other) {
        if (other == null) return Double.MAX_VALUE;
        double dx = this.position[0] - other.position[0];
        double dy = this.position[1] - other.position[1];
        double dz = this.position[2] - other.position[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
