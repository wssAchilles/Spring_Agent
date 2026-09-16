package tech.qiantong.qknow.hermes.cognitive.selfhealing.dto;

/**
 * 1000Hz 自愈认知事件帧 Record
 * 封装状态机跃迁、反思轮次、一致性评分、死锁标记与千问 1536 维超球面单位向量
 */
public record CognitiveSelfHealingEventFrame(
        String eventId,
        String sessionId,
        CognitiveState state,
        int attemptIndex,
        double consistencyScore,
        boolean deadlockDetected,
        boolean selfHealingTriggered,
        double[] sphericalEmbedding,
        long epochMicros,
        long sequenceNumber
) {
    /**
     * 严格校验阿里千问 1536 维超球面单位向量模长：||v||_2 = 1.0 ± 1e-4
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
