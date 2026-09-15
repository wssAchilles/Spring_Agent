package tech.qiantong.qknow.ai.embodied.dexterous.dto;

import java.util.Arrays;

/**
 * 手眼视触 1000Hz 实时高频控制帧状态 (Java 21 Record)
 * 封装视觉位姿观测、遮挡因子、触觉微剪切阵列、千问 1536 维超球面同胚特征与力封闭测度。
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record HandEyeCoordinationState(
        long sequenceNumber,
        double[] visualPoseSe3,
        double visualOcclusionRatio,
        double[][] tactileShearStrainArray,
        double[] fusedQwenEmbedding1536,
        double forceClosureMetric,
        double hocbfSafetyMargin,
        long timestampNs,
        String busStatus
) {
    public HandEyeCoordinationState {
        if (visualPoseSe3 == null || visualPoseSe3.length != 7) {
            throw new IllegalArgumentException("Visual pose in SE(3) must have 7 elements [x, y, z, qx, qy, qz, qw]");
        }
        if (fusedQwenEmbedding1536 == null || fusedQwenEmbedding1536.length != 1536) {
            throw new IllegalArgumentException("Fused Qwen embedding must have exact dimension 1536");
        }
        visualOcclusionRatio = Math.clamp(visualOcclusionRatio, 0.0, 1.0);
    }

    /**
     * 判断是否处于全遮挡极限工况 (Occlusion >= 95%)
     */
    public boolean isSeverelyOccluded() {
        return visualOcclusionRatio >= 0.95;
    }
}
