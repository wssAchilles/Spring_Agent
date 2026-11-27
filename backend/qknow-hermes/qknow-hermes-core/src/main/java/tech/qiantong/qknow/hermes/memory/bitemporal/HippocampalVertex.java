package tech.qiantong.qknow.hermes.memory.bitemporal;

import java.util.Arrays;
import java.util.Objects;

/**
 * 双时态海马体记忆超图异构顶点（纯 Java 21 不可变 Record，锁定千问 1024 维单位超球面 S^1023）。
 */
public record HippocampalVertex(
        String nodeId,
        String content,
        HippocampalVertexTier tier,
        float[] unitEmbedding1024
) {
    public static final int EMBEDDING_DIMENSION = 1024;

    public HippocampalVertex {
        Objects.requireNonNull(nodeId, "nodeId 不能为空");
        Objects.requireNonNull(content, "content 不能为空");
        Objects.requireNonNull(tier, "tier 不能为空");
        Objects.requireNonNull(unitEmbedding1024, "unitEmbedding1024 不能为空");
        if (unitEmbedding1024.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("海马体顶点向量维度必须严格为 1024 维，实际: " + unitEmbedding1024.length);
        }
        double normSq = 0.0;
        for (float v : unitEmbedding1024) {
            normSq += (double) v * v;
        }
        double norm = Math.sqrt(normSq);
        if (Math.abs(norm - 1.0) > 1e-3) {
            throw new IllegalArgumentException("海马体顶点向量必须位于单位超球面 S^1023 上，实际范数: " + norm);
        }
        unitEmbedding1024 = Arrays.copyOf(unitEmbedding1024, EMBEDDING_DIMENSION);
    }

    @Override
    public float[] unitEmbedding1024() {
        return Arrays.copyOf(unitEmbedding1024, EMBEDDING_DIMENSION);
    }
}
