package tech.qiantong.qknow.hermes.memory;

/**
 * 动态自适应艾宾浩斯强化衰减模型计算器。
 * 遵循 Phase 21 学术推导 (Theorem 1.1 & Theorem 1.2)：
 * 1. 记忆强度自适应强化递推：S_{k+1} = S_k * (1 + alpha * ln(1 + k))
 * 2. 半衰期单调递增：T_{1/2}(k) = S_k * ln(2)
 * 3. 重要性初始强度极值调制：S_0(I) = S_{base} * exp(gamma * I / (1 - I + epsilon))
 * 4. 留存率计算：R(t) = exp(-delta_t / S_k)
 */
public final class DynamicEbbinghausDecay {

    public static final double DEFAULT_BASE_STRENGTH_DAYS = 30.0;
    public static final double DEFAULT_PLASTICITY_ALPHA = 0.20;
    public static final double MAX_STRENGTH_DAYS = 3650.0; // 10年上限，防止溢出

    private static final double INITIAL_BASE_DAYS = 7.0;
    private static final double IMPORTANCE_GAMMA = 1.8;
    private static final double IMPORTANCE_EPSILON = 0.05;

    private DynamicEbbinghausDecay() {
        // 工具类禁止实例化
    }

    /**
     * 计算第 k 次唤醒后的动态记忆强度
     *
     * @param currentStrength 当前记忆强度 (天)
     * @param recallCount     历史被唤醒/检索总次数 k
     * @param alpha           神经塑性系数 (默认约 0.2)
     * @return 强化后的记忆强度 (天)
     */
    public static double computeNextStrength(double currentStrength, int recallCount, double alpha) {
        if (Double.isNaN(currentStrength) || currentStrength <= 0.0) {
            currentStrength = DEFAULT_BASE_STRENGTH_DAYS;
        }
        if (recallCount <= 0) {
            return currentStrength;
        }
        if (alpha <= 0.0 || Double.isNaN(alpha)) {
            alpha = DEFAULT_PLASTICITY_ALPHA;
        }

        double factor = 1.0 + alpha * Math.log(1.0 + recallCount);
        double nextStrength = currentStrength * factor;

        return Math.min(MAX_STRENGTH_DAYS, Math.max(1.0, nextStrength));
    }

    /**
     * 根据记忆强度计算等效半衰期 (天)
     */
    public static double computeHalfLife(double strength) {
        if (Double.isNaN(strength) || strength <= 0.0) {
            strength = DEFAULT_BASE_STRENGTH_DAYS;
        }
        return strength * Math.log(2.0);
    }

    /**
     * 根据固有重要性评分调制初始记忆强度 S_0(I)
     * 具备渐进不遗忘性 (Theorem 1.2)
     *
     * @param importance 重要性 (0.0 ~ 1.0)
     * @return 调制的初始记忆强度 (天)
     */
    public static double computeInitialStrength(double importance) {
        double imp = Math.min(1.0, Math.max(0.0, importance));
        double exponent = (IMPORTANCE_GAMMA * imp) / (1.0 - imp + IMPORTANCE_EPSILON);
        double strength = INITIAL_BASE_DAYS * Math.exp(exponent);
        return Math.min(MAX_STRENGTH_DAYS, Math.max(INITIAL_BASE_DAYS, strength));
    }

    /**
     * 计算第 k 次唤醒后的动态记忆强度 (使用默认神经塑性系数)
     */
    public static double calculateNextStrength(double currentStrength, int recallCount) {
        return computeNextStrength(currentStrength, recallCount, DEFAULT_PLASTICITY_ALPHA);
    }

    public static double computeNextStrength(double currentStrength, int recallCount) {
        return computeNextStrength(currentStrength, recallCount, DEFAULT_PLASTICITY_ALPHA);
    }

    /**
     * 计算当前时间点的动态留存率 R(t)
     *
     * @param lastRetrievedAt 上次唤醒时间戳 (毫秒)
     * @param strengthDays    当前强度 (天)
     * @param nowMs           当前物理时间戳 (毫秒)
     * @return 留存率 R \in (0, 1]
     */
    public static double computeRetention(long lastRetrievedAt, double strengthDays, long nowMs) {
        if (nowMs <= lastRetrievedAt || lastRetrievedAt <= 0) {
            return 1.0;
        }
        if (Double.isNaN(strengthDays) || strengthDays <= 0.0) {
            strengthDays = DEFAULT_BASE_STRENGTH_DAYS;
        }

        double ageDays = (nowMs - lastRetrievedAt) / 86400000.0;
        double r = Math.exp(-ageDays / strengthDays);
        return Math.min(1.0, Math.max(0.0, r));
    }

    /**
     * 综合留存率计算重载，结合重要性与唤醒强度
     */
    public static double computeRetention(long createdAt, int recallCount, double strength, double importance) {
        long now = System.currentTimeMillis();
        double effectiveStrength = strength > 0.0 ? strength : computeInitialStrength(importance);
        double impProtection = 1.0 + 0.5 * (Math.min(1.0, Math.max(0.0, importance)) - 0.5);
        return computeRetention(createdAt, effectiveStrength * impProtection, now);
    }
}
