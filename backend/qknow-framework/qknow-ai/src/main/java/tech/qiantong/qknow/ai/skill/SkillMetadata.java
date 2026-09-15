package tech.qiantong.qknow.ai.skill;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Phase 62: 技能语义卡片与超球面嵌入元数据 (Record)
 * <p>
 * 严格锁定阿里千问 1536 维超球面归一化向量 (||v||_2 = 1.0)。
 */
public record SkillMetadata(
        String skillId,
        String name,
        String description,
        int version,
        float[] embeddingVector,
        String inputSchema,
        String outputSchema,
        List<String> dependencies,
        double successRate,
        boolean isDeprecated
) {

    public static final int EMBEDDING_DIMENSION = 1536;

    public SkillMetadata {
        Objects.requireNonNull(skillId, "技能 ID 不能为空");
        Objects.requireNonNull(name, "技能名称不能为空");
        if (embeddingVector != null && embeddingVector.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("技能向量维度必须严格等于 " + EMBEDDING_DIMENSION + "，当前: " + embeddingVector.length);
        }
        dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
    }

    /**
     * 工厂辅助方法：生成归一化的示例技能卡片
     */
    public static SkillMetadata create(
            String skillId,
            String name,
            String description,
            int version,
            float[] rawEmbedding,
            List<String> dependencies,
            double successRate
    ) {
        float[] normalized = normalize(rawEmbedding);
        return new SkillMetadata(
                skillId, name, description, version, normalized,
                "{}", "{}", dependencies, successRate, false
        );
    }

    private static float[] normalize(float[] vec) {
        if (vec == null) {
            return new float[EMBEDDING_DIMENSION];
        }
        float[] norm = Arrays.copyOf(vec, EMBEDDING_DIMENSION);
        double sumSq = 0.0;
        for (float f : norm) {
            sumSq += f * f;
        }
        double mag = Math.sqrt(sumSq);
        if (mag > 1e-9) {
            for (int i = 0; i < norm.length; i++) {
                norm[i] = (float) (norm[i] / mag);
            }
        }
        return norm;
    }
}
