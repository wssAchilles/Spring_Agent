package tech.qiantong.qknow.hermes.causal.dto;

import java.util.Map;

/**
 * 跨模态时序因果输入状态帧
 *
 * @param sessionId        会话唯一标识
 * @param multimodalInputs 多模态输入流映射 (例如 natural_language, ui_events, system_metrics 等)
 * @param qwenEmbedding    阿里千问 1536 维超球面归一化向量 (||v||_2 = 1.0 +- 1e-4)
 * @param timestampNano    纳秒时间戳
 */
public record MultimodalCausalState(
        String sessionId,
        Map<String, String> multimodalInputs,
        float[] qwenEmbedding,
        long timestampNano
) {
    public static final int EXPECTED_DIMENSION = 1536;

    public MultimodalCausalState {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (qwenEmbedding == null || qwenEmbedding.length != EXPECTED_DIMENSION) {
            throw new IllegalArgumentException("千问嵌入向量必须严格为 1536 维，当前为: " + (qwenEmbedding == null ? 0 : qwenEmbedding.length));
        }
        double sumSq = 0.0;
        for (float v : qwenEmbedding) {
            sumSq += (double) v * v;
        }
        double norm = Math.sqrt(sumSq);
        if (Math.abs(norm - 1.0) > 1e-4) {
            throw new IllegalArgumentException("千问嵌入向量模长必须严格为 1.0 +- 1e-4，当前为: " + norm);
        }
    }
}
