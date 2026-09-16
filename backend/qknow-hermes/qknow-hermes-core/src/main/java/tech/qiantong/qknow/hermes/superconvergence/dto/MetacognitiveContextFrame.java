package tech.qiantong.qknow.hermes.superconvergence.dto;

import java.util.Arrays;

/**
 * 元认知上下文单帧 Record（包含阿里千问 1536 维超球面归一化向量）
 */
public record MetacognitiveContextFrame(
    String frameId,
    String sessionId,
    double[] embeddingVector,
    double semanticEntropy,
    double confidenceScore,
    String intentCategory,
    boolean isFrechetNormalized,
    long timestamp
) {
    public static final int EXPECTED_DIMENSION = 1536;
    private static final double EPSILON = 1e-3;

    public MetacognitiveContextFrame {
        if (frameId == null || frameId.isBlank()) {
            throw new IllegalArgumentException("frameId 不能为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (embeddingVector == null || embeddingVector.length != EXPECTED_DIMENSION) {
            throw new IllegalArgumentException("embeddingVector 必须为严格 " + EXPECTED_DIMENSION + " 维阿里千问超球面向量");
        }
        // 模长归一化校验
        double normSq = 0.0;
        for (double val : embeddingVector) {
            normSq += val * val;
        }
        double norm = Math.sqrt(normSq);
        if (Math.abs(norm - 1.0) > EPSILON) {
            throw new IllegalArgumentException("embeddingVector 必须位于单位超球面流形上 (||v||_2 = 1.0, 当前=" + norm + ")");
        }
    }

    /**
     * 获取特征维度
     */
    public int dimension() {
        return embeddingVector.length;
    }

    /**
     * 计算当前向量模长
     */
    public double norm() {
        double sum = 0.0;
        for (double v : embeddingVector) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }
}
