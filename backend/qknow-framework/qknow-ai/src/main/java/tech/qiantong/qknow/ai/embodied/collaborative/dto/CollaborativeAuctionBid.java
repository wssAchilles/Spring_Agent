package tech.qiantong.qknow.ai.embodied.collaborative.dto;

/**
 * 协同视点竞价标书
 * 包含竞标智能体 ID、候选视点坐标、次模香农互信息增益、到达代价与最终出价
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record CollaborativeAuctionBid(
        String agentId,
        double[] viewpoint,
        double submodularGain,
        double energyCost,
        double remainingBattery,
        double finalBid,
        double voronoiDistance,
        long timestamp
) {
}
