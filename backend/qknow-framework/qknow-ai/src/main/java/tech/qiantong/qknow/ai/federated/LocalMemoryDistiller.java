package tech.qiantong.qknow.ai.federated;

import org.springframework.ai.embedding.EmbeddingModel;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;

import java.util.*;

/**
 * 本地私有记忆提炼器
 * <p>
 * 智能体在本地针对公开无害的公共参考锚点查询（Public Anchors），
 * 结合私有长程记忆流提炼紧凑条件响应向量（千问 1024 维超球面单位向量 S^1023），私有数据零明文出域。
 *
 * @author Qknow AI Team
 * @since 2026-09-15
 */
public class LocalMemoryDistiller {

    private final EmbeddingModel embeddingModel;

    public LocalMemoryDistiller() {
        this(null);
    }

    public LocalMemoryDistiller(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

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
        float[] vector = new float[1024];

        if (baseEmbedding != null && baseEmbedding.length == 1024) {
            System.arraycopy(baseEmbedding, 0, vector, 0, 1024);
        } else if (embeddingModel != null) {
            float[] embedded = embeddingModel.embed(anchorQuery);
            if (embedded != null && embedded.length == 1024) {
                System.arraycopy(embedded, 0, vector, 0, 1024);
            } else {
                vector = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(agentId + ":" + anchorQuery, 1024);
            }
        } else {
            vector = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(agentId + ":" + anchorQuery, 1024);
        }

        // 融合本地私有记忆的归一化语义方向分量（不暴露原始文本）
        if (privateMemories != null && !privateMemories.isEmpty()) {
            float modulationWeight = (float) Math.min(0.25, memoryCount * 0.04);
            for (String mem : privateMemories) {
                if (mem != null && !mem.isBlank()) {
                    float[] memVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(mem, 1024);
                    for (int i = 0; i < 1024; i++) {
                        vector[i] += modulationWeight * memVec[i];
                    }
                }
            }
        }

        // 重新执行千问 1024 维超球面保模归一化 (||v||_2 = 1.0)
        LocalitySensitiveFeatureHasher.normalizeInPlace(vector);

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
