package tech.qiantong.qknow.hermes.swarm.coalition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对数阻尼多智能体信誉动力学追踪器 (Log-Damped Reputation Dynamics Tracker)
 * 严格实现 Lemma 143.2：防女巫刷单、抗合谋刷分与三级冷备隔离可逆自愈环形缓冲区。
 *
 * @author Achilles
 * @since Phase 143
 */
public class LogDampedReputationTracker {

    public enum QuarantineLevel {
        LEVEL_0_NORMAL,      // 正常活跃状态 (Rep >= 0.50)
        LEVEL_1_WATCH,       // 观察预警状态 (0.30 <= Rep < 0.50)
        LEVEL_2_QUARANTINED  // 三级冷备隔离状态 (Rep < 0.30，软隔离自愈池)
    }

    /**
     * 智能体信誉档案不可变记录
     */
    public record ReputationProfile(
            String agentId,
            String tenantId,
            double currentReputation,
            int totalTasks,
            int successfulTasks,
            int violationCount,
            QuarantineLevel quarantineLevel,
            long lastUpdatedTimestamp,
            boolean isPinned
    ) {}

    // 参数配置：对数阻尼与非对称重惩超参数
    private final double etaUp;       // 成功奖励步长
    private final double betaSucc;    // 成功对数阻尼因子
    private final double etaDown;     // 违约惩罚步长
    private final double betaViol;    // 违约非对称强化因子
    private final int ringBufferSize; // 三级冷备环形缓冲区容量

    // 内存数据存储 (线程安全)
    private final ConcurrentHashMap<String, ReputationProfile> profiles = new ConcurrentHashMap<>();
    private final ArrayDeque<String> quarantineRingBuffer = new ArrayDeque<>();
    private final Object ringBufferLock = new Object();

    public LogDampedReputationTracker() {
        this(0.08, 0.50, 0.25, 0.80, 128);
    }

    public LogDampedReputationTracker(double etaUp, double betaSucc, double etaDown, double betaViol, int ringBufferSize) {
        this.etaUp = etaUp;
        this.betaSucc = betaSucc;
        this.etaDown = etaDown;
        this.betaViol = betaViol;
        this.ringBufferSize = ringBufferSize;
    }

    /**
     * 初始化或注册智能体档案
     */
    public ReputationProfile registerAgent(String agentId, String tenantId, double initialReputation, boolean isPinned) {
        double boundedRep = Math.max(0.0, Math.min(1.0, initialReputation));
        if (isPinned && boundedRep < 0.80) {
            boundedRep = 0.80;
        }
        QuarantineLevel level = evaluateLevel(boundedRep);
        ReputationProfile profile = new ReputationProfile(
                agentId, tenantId, boundedRep, 0, 0, 0, level, System.currentTimeMillis(), isPinned
        );
        profiles.put(buildKey(tenantId, agentId), profile);
        return profile;
    }

    /**
     * 获取智能体信誉档案
     */
    public ReputationProfile getProfile(String tenantId, String agentId) {
        return profiles.computeIfAbsent(buildKey(tenantId, agentId), k ->
                new ReputationProfile(agentId, tenantId, 0.50, 0, 0, 0, QuarantineLevel.LEVEL_0_NORMAL, System.currentTimeMillis(), false)
        );
    }

    /**
     * 依据夏普利边际贡献执行对数饱和阻尼奖励更新 (Success Reward)
     */
    public ReputationProfile recordSuccessContribution(String tenantId, String agentId, double shapleyPayoff, double coalitionValue) {
        return profiles.compute(buildKey(tenantId, agentId), (key, existing) -> {
            if (existing == null) {
                existing = new ReputationProfile(agentId, tenantId, 0.50, 0, 0, 0, QuarantineLevel.LEVEL_0_NORMAL, System.currentTimeMillis(), false);
            }
            int newSuccesses = existing.successfulTasks() + 1;
            int newTotals = existing.totalTasks() + 1;

            // 相对边际贡献率: phi_i / v(S)
            double contributionRatio = coalitionValue > 1e-9 ? Math.max(0.0, Math.min(1.0, shapleyPayoff / coalitionValue)) : 0.0;

            // 对数阻尼增量: etaUp * (phi_i / v(S)) / (1.0 + betaSucc * ln^2(1.0 + N_succ))
            double logTerm = Math.log(1.0 + newSuccesses);
            double damping = 1.0 + betaSucc * logTerm * logTerm;
            double deltaRep = (etaUp * contributionRatio) / damping;

            double updatedRep = Math.min(1.0, existing.currentReputation() + deltaRep);
            if (existing.isPinned() && updatedRep < 0.80) {
                updatedRep = 0.80;
            }

            QuarantineLevel level = evaluateLevel(updatedRep);
            updateRingBufferState(tenantId, agentId, level);

            return new ReputationProfile(
                    agentId, tenantId, updatedRep, newTotals, newSuccesses,
                    existing.violationCount(), level, System.currentTimeMillis(), existing.isPinned()
            );
        });
    }

    /**
     * 执行非对称超线性违约惩罚更新 (Violation Penalty)
     */
    public ReputationProfile recordViolation(String tenantId, String agentId, double severity) {
        return profiles.compute(buildKey(tenantId, agentId), (key, existing) -> {
            if (existing == null) {
                existing = new ReputationProfile(agentId, tenantId, 0.50, 0, 0, 0, QuarantineLevel.LEVEL_0_NORMAL, System.currentTimeMillis(), false);
            }
            int newViolations = existing.violationCount() + 1;
            int newTotals = existing.totalTasks() + 1;
            double boundedSeverity = Math.max(0.1, Math.min(2.0, severity));

            // 非对称重罚方程: -etaDown * (1.0 + betaViol * ln(1.0 + N_viol)) * severity
            double penaltyScale = 1.0 + betaViol * Math.log(1.0 + newViolations);
            double deltaPenalty = etaDown * penaltyScale * boundedSeverity;

            double updatedRep = Math.max(0.0, existing.currentReputation() - deltaPenalty);
            if (existing.isPinned() && updatedRep < 0.80) {
                updatedRep = 0.80;
            }

            QuarantineLevel level = evaluateLevel(updatedRep);
            updateRingBufferState(tenantId, agentId, level);

            return new ReputationProfile(
                    agentId, tenantId, updatedRep, newTotals, existing.successfulTasks(),
                    newViolations, level, System.currentTimeMillis(), existing.isPinned()
            );
        });
    }

    /**
     * 沙盒闭环自愈恢复 (Self-Healing Recovery)
     */
    public ReputationProfile selfHeal(String tenantId, String agentId, double boostReputation) {
        return profiles.compute(buildKey(tenantId, agentId), (key, existing) -> {
            if (existing == null) {
                return null;
            }
            double updatedRep = Math.min(1.0, existing.currentReputation() + Math.max(0.0, boostReputation));
            QuarantineLevel level = evaluateLevel(updatedRep);
            updateRingBufferState(tenantId, agentId, level);
            return new ReputationProfile(
                    agentId, tenantId, updatedRep, existing.totalTasks(), existing.successfulTasks(),
                    existing.violationCount(), level, System.currentTimeMillis(), existing.isPinned()
            );
        });
    }

    /**
     * 获取处于冷备隔离环形缓冲区的所有智能体清单
     */
    public List<String> getQuarantinedAgents() {
        synchronized (ringBufferLock) {
            return Collections.unmodifiableList(new ArrayList<>(quarantineRingBuffer));
        }
    }

    /**
     * 判断当前智能体是否被隔离
     */
    public boolean isQuarantined(String tenantId, String agentId) {
        ReputationProfile profile = getProfile(tenantId, agentId);
        return profile.quarantineLevel() == QuarantineLevel.LEVEL_2_QUARANTINED;
    }

    private QuarantineLevel evaluateLevel(double rep) {
        if (rep >= 0.50) {
            return QuarantineLevel.LEVEL_0_NORMAL;
        } else if (rep >= 0.30) {
            return QuarantineLevel.LEVEL_1_WATCH;
        } else {
            return QuarantineLevel.LEVEL_2_QUARANTINED;
        }
    }

    private void updateRingBufferState(String tenantId, String agentId, QuarantineLevel level) {
        String key = buildKey(tenantId, agentId);
        synchronized (ringBufferLock) {
            if (level == QuarantineLevel.LEVEL_2_QUARANTINED) {
                if (!quarantineRingBuffer.contains(key)) {
                    if (quarantineRingBuffer.size() >= ringBufferSize) {
                        quarantineRingBuffer.pollFirst(); // 环形淘汰最老记录
                    }
                    quarantineRingBuffer.addLast(key);
                }
            } else {
                quarantineRingBuffer.remove(key); // 出池恢复正常
            }
        }
    }

    private String buildKey(String tenantId, String agentId) {
        return (tenantId != null ? tenantId : "default") + ":" + agentId;
    }
}
