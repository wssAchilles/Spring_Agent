package tech.qiantong.qknow.hermes.agent.bidding;

/**
 * Worker 投标单
 */
public record WorkerBid(
        String workerName,
        double bidScore,               // 综合评分 (0.0 ~ 1.0)
        double capabilityRelevance,     // 领域能力匹配度
        double currentLoadRate,         // 当前负载率 (0.0 ~ 1.0)
        double historicalReliability    // 历史可靠性得分
) {}
