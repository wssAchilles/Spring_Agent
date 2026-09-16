package tech.qiantong.qknow.hermes.cognitive.dto;

/**
 * 1000Hz 实时定长无锁认知事件帧
 *
 * @param frameId          事件帧唯一标识
 * @param requestId        关联的业务请求 ID
 * @param sessionId        会话 ID
 * @param strategy         当前激活的认知策略
 * @param timestampNanos   纳秒级时间戳
 * @param qwenEmbedding1536 阿里千问 1536 维超球面单位归一化特征向量
 */
public record CognitiveEventFrame(
        String frameId,
        String requestId,
        String sessionId,
        CognitiveStrategy strategy,
        long timestampNanos,
        float[] qwenEmbedding1536
) {
    public CognitiveEventFrame {
        if (frameId == null || frameId.isBlank()) {
            throw new IllegalArgumentException("frameId 不能为空");
        }
        if (strategy == null) {
            strategy = CognitiveStrategy.DIRECT_ANSWER;
        }
        if (timestampNanos <= 0) {
            timestampNanos = System.nanoTime();
        }
    }
}
