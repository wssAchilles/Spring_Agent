package tech.qiantong.qknow.mcp.core.orchestration.dto;

/**
 * 1000Hz 流式工具分块事件帧 Record (Java 21 原生 Record)
 * 用于长耗时工具分块推送、分块保活看门狗监控与阿里千问 1536 维超球面意图对齐
 */
public record StreamingToolChunkEventFrame(
        String frameId,
        String executionId,
        String toolName,
        int chunkIndex,
        boolean isLastChunk,
        String chunkPayload,
        double[] sphericalEmbedding,
        long epochMicros,
        long sequenceNumber
) {
    /**
     * 校验阿里千问 1536 维超球面单位向量范数：||v||_2 = 1.0 ± 1e-4
     */
    public boolean isValidEmbedding() {
        if (sphericalEmbedding == null || sphericalEmbedding.length != 1536) {
            return false;
        }
        double normSq = 0.0;
        for (double v : sphericalEmbedding) {
            normSq += v * v;
        }
        return Math.abs(Math.sqrt(normSq) - 1.0) <= 1e-4;
    }
}
