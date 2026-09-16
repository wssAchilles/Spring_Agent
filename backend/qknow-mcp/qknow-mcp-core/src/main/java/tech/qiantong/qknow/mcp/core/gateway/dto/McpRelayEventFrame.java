package tech.qiantong.qknow.mcp.core.gateway.dto;

import java.util.Arrays;

/**
 * 1000Hz 高频 MCP 中继事件帧 (Java 21 原生 Record)
 * 封装阿里千问 1536 维超球面单位向量与微秒级时间戳
 */
public record McpRelayEventFrame(
        String frameId,
        String serverId,
        String toolName,
        String eventType,
        double[] embeddingVector,
        long timestampMs,
        long sequenceNo
) {
    public static final int EXPECTED_EMBEDDING_DIM = 1536;
    public static final double NORM_EPSILON = 1e-4;

    /**
     * 校验阿里千问 1536 维超球面流形单位向量合法性 (||v||_2 = 1.0 +- 1e-4)
     *
     * @return 是否满足超球面约束
     */
    public boolean isValidEmbedding() {
        if (embeddingVector == null || embeddingVector.length != EXPECTED_EMBEDDING_DIM) {
            return false;
        }
        double normSq = 0.0;
        for (double v : embeddingVector) {
            normSq += v * v;
        }
        double norm = Math.sqrt(normSq);
        return Math.abs(norm - 1.0) <= NORM_EPSILON;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof McpRelayEventFrame that)) return false;
        return timestampMs == that.timestampMs &&
                sequenceNo == that.sequenceNo &&
                java.util.Objects.equals(frameId, that.frameId) &&
                java.util.Objects.equals(serverId, that.serverId) &&
                java.util.Objects.equals(toolName, that.toolName) &&
                java.util.Objects.equals(eventType, that.eventType) &&
                Arrays.equals(embeddingVector, that.embeddingVector);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(frameId, serverId, toolName, eventType, timestampMs, sequenceNo);
        result = 31 * result + Arrays.hashCode(embeddingVector);
        return result;
    }
}
