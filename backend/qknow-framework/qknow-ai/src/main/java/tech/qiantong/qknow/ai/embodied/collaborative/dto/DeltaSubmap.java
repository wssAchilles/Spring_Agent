package tech.qiantong.qknow.ai.embodied.collaborative.dto;

import tech.qiantong.qknow.ai.embodied.mapping.dto.TopologicalNode;

import java.util.List;
import java.util.Map;

/**
 * 具身智能体轻量增量拓扑子图 (Delta-Submap)
 * 包含稀疏哈希体素增量、拓扑骨架节点与阿里千问 1536 维超球面特征指纹
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record DeltaSubmap(
        String submapId,
        String agentId,
        double[][] se3Pose,
        Map<Long, Byte> voxelDeltas,
        List<TopologicalNode> topologicalNodes,
        double[] hypersphericalFingerprint,
        long timestamp
) {
    public DeltaSubmap {
        if (submapId == null || agentId == null) {
            throw new IllegalArgumentException("submapId and agentId must not be null");
        }
        if (hypersphericalFingerprint != null && hypersphericalFingerprint.length != 1536) {
            throw new IllegalArgumentException("hypersphericalFingerprint must be 1536-dimensional");
        }
    }
}
