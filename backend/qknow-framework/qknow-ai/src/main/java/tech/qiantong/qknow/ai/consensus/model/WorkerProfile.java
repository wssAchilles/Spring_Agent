package tech.qiantong.qknow.ai.consensus.model;

/**
 * Worker 智能体历史信誉档案
 */
public record WorkerProfile(
        String workerId,
        double reputationScore,
        WorkerStatus status,
        int consecutiveSuccessCount,
        int violationCount,
        long lastActiveTime
) {
    public enum WorkerStatus {
        ACTIVE,      // 正常活跃
        PROBATION,   // 观察期 (适度降权)
        JAILED       // 静默入狱隔离 (剥夺派单与投票权)
    }
}
