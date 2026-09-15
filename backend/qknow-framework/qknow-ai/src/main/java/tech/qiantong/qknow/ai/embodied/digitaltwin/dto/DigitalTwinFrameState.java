package tech.qiantong.qknow.ai.embodied.digitaltwin.dto;

import java.util.Objects;

/**
 * 具身多智能体柔性装配线数字孪生高频状态帧 (Java 21 Record)
 * <p>
 * 封装产线 ID、当前帧全局序号、当前工艺步骤、各工位末端位姿矩阵、各工位六维接触力矩矩阵、
 * 当前仿真保真度级别、瞬时物理-孪生残差范数、阿里千问 1536 维超球面单位特征向量与时间戳。
 */
public record DigitalTwinFrameState(
        String lineId,
        long frameSequence,
        int currentCycleStep,
        double[][] stationPoses,
        double[][] contactWrenches,
        String fidelityLevel,
        double residualNorm,
        double[] hypersphericalEmbedding,
        long timestampMs
) {
    public static final int EMBEDDING_DIM = 1536;
    public static final double NORM_TOLERANCE = 1e-4;

    public DigitalTwinFrameState {
        Objects.requireNonNull(lineId, "lineId 不能为空");
        Objects.requireNonNull(stationPoses, "stationPoses 不能为空");
        Objects.requireNonNull(contactWrenches, "contactWrenches 不能为空");
        Objects.requireNonNull(fidelityLevel, "fidelityLevel 不能为空");
        Objects.requireNonNull(hypersphericalEmbedding, "hypersphericalEmbedding 不能为空");
        if (hypersphericalEmbedding.length != EMBEDDING_DIM) {
            throw new IllegalArgumentException("千问特征向量维度必须严格为 1536，当前为: " + hypersphericalEmbedding.length);
        }
    }

    /**
     * 计算阿里千问 1536 维超球面向量的欧氏 L2 范数
     */
    public double computeEmbeddingNorm() {
        double sumSq = 0.0;
        for (double v : hypersphericalEmbedding) {
            sumSq += v * v;
        }
        return Math.sqrt(sumSq);
    }

    /**
     * 校验向量是否严格位于单位超球面流形 S^1535 上 (||v||_2 = 1.0)
     */
    public boolean isHypersphericalNormalized() {
        double norm = computeEmbeddingNorm();
        return Math.abs(norm - 1.0) <= NORM_TOLERANCE;
    }
}
