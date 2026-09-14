package tech.qiantong.qknow.hermes.a2a.card;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 智能体语义能力名片（封装阿里千问 1536 维超球面嵌入向量与历史信誉得分）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentCard(
        String agentId,
        String agentName,
        String description,
        List<String> capabilities,
        float[] embedding1536,
        double reputationScore,
        boolean online
) {
    public AgentCard {
        capabilities = capabilities != null ? List.copyOf(capabilities) : List.of();
        if (reputationScore < 0.0 || reputationScore > 1.0) {
            throw new IllegalArgumentException("信誉得分必须落在 [0.0, 1.0] 闭区间，当前值: " + reputationScore);
        }
        if (embedding1536 != null) {
            if (embedding1536.length != 1536) {
                throw new IllegalArgumentException("阿里千问超球面向量维度必须严格为 1536 维，实际传入: " + embedding1536.length);
            }
            double norm = 0.0;
            for (float v : embedding1536) {
                norm += v * v;
            }
            norm = Math.sqrt(norm);
            if (norm <= 1e-6) {
                throw new IllegalArgumentException("全零向量无法进行超球面单位投影 (norm <= 1e-6)");
            }
            if (Math.abs(norm - 1.0) > 1e-4) {
                for (int i = 0; i < embedding1536.length; i++) {
                    embedding1536[i] = (float) (embedding1536[i] / norm);
                }
            }
        }
    }
}
