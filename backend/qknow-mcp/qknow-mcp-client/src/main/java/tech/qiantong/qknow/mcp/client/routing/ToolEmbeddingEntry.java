package tech.qiantong.qknow.mcp.client.routing;

import tech.qiantong.qknow.mcp.client.safety.HighRiskToolSafetyGovernor.RiskLevel;

/**
 * 工具元数据超球面嵌入项 (ToolEmbeddingEntry)
 * 封装工具标识、功能描述、Schema 与阿里千问 1536 维超球面单位向量
 */
public record ToolEmbeddingEntry(
        String toolName,
        String serverId,
        String description,
        String inputSchemaJson,
        float[] embedding1536,
        RiskLevel riskLevel
) {

    public ToolEmbeddingEntry {
        if (toolName == null || serverId == null) {
            throw new IllegalArgumentException("toolName and serverId must not be null");
        }
        if (embedding1536 != null && embedding1536.length != 1536) {
            throw new IllegalArgumentException("Embedding vector must be 1536 dimensions");
        }
    }

    /**
     * 计算当前工具向量与意图查询向量之间的千问 1536 维超球面测地大圆弧散度 [0.0, 1.0]
     * d_g = arccos(clamp(u · v, -1.0, 1.0)) / PI
     */
    public double geodesicDivergence(float[] queryEmbedding) {
        if (queryEmbedding == null || embedding1536 == null || queryEmbedding.length != 1536) {
            return 1.0;
        }
        double dot = 0.0;
        for (int i = 0; i < 1536; i++) {
            dot += (double) embedding1536[i] * queryEmbedding[i];
        }
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.acos(dot) / Math.PI;
    }
}
