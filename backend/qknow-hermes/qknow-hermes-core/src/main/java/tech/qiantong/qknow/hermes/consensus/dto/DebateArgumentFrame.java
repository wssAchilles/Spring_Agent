package tech.qiantong.qknow.hermes.consensus.dto;

import java.util.Arrays;

/**
 * 单轮博弈论据事件 Java 21 Record
 * 封装辩论轮次、角色主张、阿里千问 1536 维超球面单位向量与逻辑得分
 */
public record DebateArgumentFrame(
        String argumentId,
        String sessionId,
        int roundNumber,
        AgentDebateRole role,
        String claimText,
        float[] argumentEmbedding,
        double logicScore,
        long timestamp
) {
    public static final int EXPECTED_DIMENSION = 1536;
    public static final double NORM_TOLERANCE = 1e-4;

    /**
     * 强校验阿里千问 1536 维超球面单位向量模长范数 (||v||_2 = 1.0 +- 1e-4)
     */
    public boolean isValidEmbedding() {
        if (argumentEmbedding == null || argumentEmbedding.length != EXPECTED_DIMENSION) {
            return false;
        }
        double sumSq = 0.0;
        for (float v : argumentEmbedding) {
            sumSq += (double) v * v;
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= NORM_TOLERANCE;
    }
}
