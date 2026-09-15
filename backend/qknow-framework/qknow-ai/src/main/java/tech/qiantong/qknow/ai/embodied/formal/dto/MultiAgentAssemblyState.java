package tech.qiantong.qknow.ai.embodied.formal.dto;

import java.util.Objects;

/**
 * 具身多智能体精密装配时空状态与形式化时序流转数据载体 (Java 21 Record)
 * <p>
 * 封装工位 ID、当前 DFA 自动机状态、瞬时激活原子命题位图、各臂末端位姿矩阵、
 * 各臂六维接触力矩矩阵、瞬时连续平滑 STL 鲁棒度、阿里千问 1536 维超球面单位特征向量与时间戳。
 */
public record MultiAgentAssemblyState(
        String stationId,
        int currentDfaState,
        int activePropsBitmask,
        double[][] endEffectorPoses,
        double[][] contactWrenches,
        double smoothStlRobustness,
        double[] hypersphericalEmbedding,
        long timestampMs
) {
    public static final int EMBEDDING_DIM = 1536;
    public static final double NORM_TOLERANCE = 1e-4;

    public MultiAgentAssemblyState {
        Objects.requireNonNull(stationId, "stationId 不能为空");
        Objects.requireNonNull(endEffectorPoses, "endEffectorPoses 不能为空");
        Objects.requireNonNull(contactWrenches, "contactWrenches 不能为空");
        Objects.requireNonNull(hypersphericalEmbedding, "hypersphericalEmbedding 不能为空");
        if (hypersphericalEmbedding.length != EMBEDDING_DIM) {
            throw new IllegalArgumentException("千问嵌入向量维度必须严格为 1536，实际为: " + hypersphericalEmbedding.length);
        }
    }

    /**
     * 计算阿里千问 1536 维超球面特征向量的欧氏 L2 范数
     */
    public double computeEmbeddingNorm() {
        double sumSq = 0.0;
        for (double v : hypersphericalEmbedding) {
            sumSq += v * v;
        }
        return Math.sqrt(sumSq);
    }

    /**
     * 校验向量是否严格满足单位超球面流形约束 (||v||_2 = 1.0)
     */
    public boolean isHypersphericalNormalized() {
        double norm = computeEmbeddingNorm();
        return Math.abs(norm - 1.0) <= NORM_TOLERANCE;
    }
}
