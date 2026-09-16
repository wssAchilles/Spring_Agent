package tech.qiantong.qknow.hermes.intent.dto;

import java.util.List;
import java.util.Map;

/**
 * 分层多模态意图状态 Java 21 Record
 * 封装宏观业务意图、子意图路径、槽位映射与阿里千问 1536 维超球面单位向量
 */
public record HierarchicalIntentState(
        String intentId,
        String sessionId,
        String macroIntent, // QUERY, MUTATE, EXPORT, SYSTEM_ADMIN, FINANCE, etc.
        List<String> subIntentPath,
        Map<String, String> slots,
        double confidenceScore,
        float[] sphericalEmbedding,
        long timestamp
) {
    public static final int EXPECTED_DIMENSION = 1536;
    public static final double NORM_TOLERANCE = 1e-4;

    /**
     * 强校验阿里千问 1536 维超球面单位向量模长范数 (||v||_2 = 1.0 +- 1e-4)
     */
    public boolean isValidEmbedding() {
        if (sphericalEmbedding == null || sphericalEmbedding.length != EXPECTED_DIMENSION) {
            return false;
        }
        double sumSq = 0.0;
        for (float v : sphericalEmbedding) {
            sumSq += (double) v * v;
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= NORM_TOLERANCE;
    }

    public boolean hasSlot(String slotKey) {
        return slots != null && slots.containsKey(slotKey) && slots.get(slotKey) != null && !slots.get(slotKey).isBlank();
    }

    public String getSlot(String slotKey) {
        return slots != null ? slots.get(slotKey) : null;
    }

    // 便捷 Accessor 适配契约测试与上层认知调用
    public String macroDomain() {
        return macroIntent;
    }

    public String taskGoal() {
        if (subIntentPath != null && !subIntentPath.isEmpty()) {
            return subIntentPath.get(subIntentPath.size() - 1);
        }
        return macroIntent;
    }

    public List<String> subActionSequence() {
        return subIntentPath != null ? subIntentPath : List.of();
    }

    public double semanticConfidence() {
        return confidenceScore;
    }

    public float[] qwenEmbedding() {
        return sphericalEmbedding;
    }
}
