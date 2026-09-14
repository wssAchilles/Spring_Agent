package tech.qiantong.qknow.ai.consensus.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.consensus.model.ByzantineFaultType;
import tech.qiantong.qknow.ai.consensus.model.WorkerProfile;
import tech.qiantong.qknow.ai.consensus.model.WorkerProfile.WorkerStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工业级滑动窗口 EMA 智能体信誉账本
 */
@Slf4j
@Component
public class WorkerReputationLedger {

    private static final double INITIAL_REPUTATION = 0.85;
    private static final double EMA_ALPHA = 0.85;            // 历史记忆权重
    private static final double QUARANTINE_THRESHOLD = 0.40; // 入狱隔离线
    private static final double RECOVERY_TARGET = 0.70;      // 探活解冻线

    private final Map<String, WorkerProfile> profiles = new ConcurrentHashMap<>();

    public double getReputationWeight(String workerId) {
        WorkerProfile profile = profiles.computeIfAbsent(workerId, id ->
                new WorkerProfile(id, INITIAL_REPUTATION, WorkerStatus.ACTIVE, 0, 0, System.currentTimeMillis()));

        if (profile.status() == WorkerStatus.JAILED) {
            return 0.0; // 隔离节点权重归零
        }
        return Math.clamp(profile.reputationScore(), 0.05, 1.0);
    }

    public boolean isWorkerEligible(String workerId) {
        WorkerProfile profile = profiles.get(workerId);
        return profile == null || profile.status() != WorkerStatus.JAILED;
    }

    public WorkerProfile getProfile(String workerId) {
        return profiles.computeIfAbsent(workerId, id ->
                new WorkerProfile(id, INITIAL_REPUTATION, WorkerStatus.ACTIVE, 0, 0, System.currentTimeMillis()));
    }

    /**
     * 根据执行成效原子更新 EMA 信誉分
     */
    public synchronized void recordExecutionOutcome(String workerId, ByzantineFaultType faultType, double outcomeQuality) {
        WorkerProfile current = profiles.computeIfAbsent(workerId, id ->
                new WorkerProfile(id, INITIAL_REPUTATION, WorkerStatus.ACTIVE, 0, 0, System.currentTimeMillis()));

        double currentScore = current.reputationScore();
        double instantPerformance;
        int violations = current.violationCount();
        int successes = current.consecutiveSuccessCount();

        switch (faultType) {
            case NONE -> {
                instantPerformance = Math.clamp(outcomeQuality, 0.5, 1.0);
                successes++;
            }
            case CRASH_STOP -> {
                instantPerformance = 0.15;
                violations++;
                successes = 0;
            }
            case NOISY_OUTLIER -> {
                instantPerformance = 0.10;
                violations++;
                successes = 0;
            }
            case ADVERSARIAL_INJECTION -> {
                instantPerformance = 0.0;
                violations += 3;
                successes = 0;
            }
            default -> instantPerformance = 0.5;
        }

        // EMA 衰减公式：发生违规时有效历史权重降低至 0.60，加速惩罚以实现快降慢升
        double effectiveAlpha = (faultType == ByzantineFaultType.NONE) ? EMA_ALPHA : 0.60;
        double newScore = (effectiveAlpha * currentScore) + ((1.0 - effectiveAlpha) * instantPerformance);
        newScore = Math.clamp(newScore, 0.01, 1.0);

        WorkerStatus newStatus;
        if (faultType == ByzantineFaultType.ADVERSARIAL_INJECTION || newScore < QUARANTINE_THRESHOLD || violations >= 3) {
            newStatus = WorkerStatus.JAILED;
            log.warn("[ReputationLedger] Worker [{}] 触发拜占庭安全红线或信誉过低 ({})，立即执行静默入狱隔离！", workerId, newScore);
        } else if (newScore < 0.60) {
            newStatus = WorkerStatus.PROBATION;
        } else {
            newStatus = WorkerStatus.ACTIVE;
        }

        profiles.put(workerId, new WorkerProfile(workerId, newScore, newStatus, successes, violations, System.currentTimeMillis()));
    }

    /**
     * 周期性探活探针解冻自愈 (Slow Recovery)
     */
    public synchronized boolean probeAndRecover(String workerId, boolean probePassed) {
        WorkerProfile profile = profiles.get(workerId);
        if (profile == null || profile.status() != WorkerStatus.JAILED) {
            return false;
        }

        if (probePassed) {
            int newSuccesses = profile.consecutiveSuccessCount() + 1;
            double newScore = profile.reputationScore() + 0.15;
            WorkerStatus status = (newScore >= RECOVERY_TARGET && newSuccesses >= 3) ? WorkerStatus.PROBATION : WorkerStatus.JAILED;
            profiles.put(workerId, new WorkerProfile(workerId, Math.min(newScore, RECOVERY_TARGET), status, newSuccesses, profile.violationCount(), System.currentTimeMillis()));
            if (status != WorkerStatus.JAILED) {
                log.info("[ReputationLedger] Worker [{}] 连续探活通过，解除隔离进入观察期！", workerId);
                return true;
            }
        }
        return false;
    }
}
