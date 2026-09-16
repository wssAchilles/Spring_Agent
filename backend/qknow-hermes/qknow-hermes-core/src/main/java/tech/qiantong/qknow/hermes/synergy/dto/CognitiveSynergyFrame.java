package tech.qiantong.qknow.hermes.synergy.dto;

/**
 * 超球面认知协同网络状态帧
 *
 * @param sessionId        协同会话标识
 * @param participantCount 参与协同智能体总数
 * @param frechetMeanVector 千问 1536 维切空间 Fréchet 均值向量
 * @param informationGain  协同互信息增益 Delta I (必须严格 > 0)
 * @param semanticDriftRate 语义漂移率 (必须 <= 0.8%)
 * @param elapsedNanos     单步协同聚合耗时 (纳秒)
 */
public record CognitiveSynergyFrame(
        String sessionId,
        int participantCount,
        float[] frechetMeanVector,
        double informationGain,
        double semanticDriftRate,
        long elapsedNanos
) {
    public CognitiveSynergyFrame {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (frechetMeanVector == null || frechetMeanVector.length != 1536) {
            throw new IllegalArgumentException("Fréchet 均值向量必须严格为 1536 维");
        }
    }
}
