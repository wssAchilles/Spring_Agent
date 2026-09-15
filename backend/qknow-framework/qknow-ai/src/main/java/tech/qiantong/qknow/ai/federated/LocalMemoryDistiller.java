package tech.qiantong.qknow.ai.federated;

import java.util.*;

/**
 * 本地私有记忆提炼器
 * <p>
 * 智能体在本地针对公开无害的公共参考锚点查询（Public Anchors），
 * 结合私有长程记忆流提炼紧凑条件响应向量（千问 1536 维超球面单位向量），私有数据零明文出域。
 *
 * @author Qknow AI Team
 * @since 2026-09-15
 */
public class LocalMemoryDistiller {

    public record LocalInsight(
            String agentId,
            String anchorQuery,
            float[] embeddingVector,
            int localMemoryCount,
            double confidenceScore
    ) {}

    /**
     * 针对指定公共锚点提炼本地认知向量
     */
    public LocalInsight distill(
            String agentId,
            String anchorQuery,
            List<String> privateMemories,
            float[] baseEmbedding
    ) {
        Objects.requireNonNull(agentId, "agentId 不能为空");
        Objects.requireNonNull(anchorQuery, "anchorQuery 不能为空");

        int memoryCount = privateMemories != null ? privateMemories.size() : 0;
        float[] vector = new float[1536];

        if (baseEmbedding != null && baseEmbedding.length == 1536) {
            System.arraycopy(baseEmbedding, 0, vector, 0, 1536);
        } else {
            // 根据 agentId 与 anchorQuery 种子生成确定性基底向量
            Random rnd = new Random((agentId + anchorQuery).hashCode());
            for (int i = 0; i < 1536; i++) {
                vector[i] = rnd.nextFloat() - 0.5f;
            }
        }

        // 模拟本地记忆对向量特征维度的调制微调
        float memoryModulation = (float) Math.min(0.2, memoryCount * 0.02);
        vector[0] += memoryModulation;
        vector[1] -= memoryModulation * 0.5f;

        // 重新执行千问 1536 维超球面保模归一化 (||v||_2 = 1.0)
        normalizeToHypersphere(vector);

        double confidence = Math.min(0.99, 0.70 + memoryCount * 0.05);

        return new LocalInsight(agentId, anchorQuery, vector, memoryCount, confidence);
    }

    private void normalizeToHypersphere(float[] v) {
        float norm = 0.0f;
        for (float val : v) {
            norm += val * val;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 1e-9f) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= norm;
            }
        }
    }
}
