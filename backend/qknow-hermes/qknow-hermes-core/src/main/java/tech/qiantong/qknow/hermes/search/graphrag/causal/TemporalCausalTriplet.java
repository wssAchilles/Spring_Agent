package tech.qiantong.qknow.hermes.search.graphrag.causal;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * 时间感知语义三元组实体 (Temporal Causal Triplet)
 * 遵循 Java 21 Record 纯不可变对象规范与阿里千问 1536 维超球面单位球几何约束：
 * 1. 包含主语、谓词、宾语符号级语义与物理生成时间戳；
 * 2. 显式挂载有效时间窗口 (ValidTimeWindow) 与半衰期指数衰减因子；
 * 3. 显式挂载因果前驱偏序指针 (causalPredecessorId)，破除时序倒错；
 * 4. 挂载阿里千问 1536 维超球面单位向量并执行深度防御性拷贝。
 */
public record TemporalCausalTriplet(
        String tripletId,
        String subject,
        String predicate,
        String object,
        long timestampEpochMs,
        String causalPredecessorId,
        ValidTimeWindow validTimeWindow,
        double confidence,
        float[] tripletVector
) implements Serializable {

    public static final int EMBEDDING_DIM = 1536;

    /**
     * 事实有效性时间窗口不可变定义
     */
    public record ValidTimeWindow(
            long validStartEpochMs,
            long validEndEpochMs,
            long halfLifeMs
    ) implements Serializable {

        public ValidTimeWindow {
            if (halfLifeMs <= 0) {
                halfLifeMs = 86_400_000L; // 默认 24 小时半衰期衰减基线
            }
        }

        public boolean isFuture(long queryTimestampEpochMs) {
            return validStartEpochMs > 0 && queryTimestampEpochMs < validStartEpochMs;
        }

        public boolean isExpired(long queryTimestampEpochMs) {
            return validEndEpochMs > 0 && queryTimestampEpochMs > validEndEpochMs;
        }

        /**
         * 计算时空半衰期衰减因子
         */
        public double computeDecayFactor(long queryTimestampEpochMs) {
            if (isFuture(queryTimestampEpochMs)) {
                return 0.0; // 尚未生效的事实直接硬截断
            }
            if (isExpired(queryTimestampEpochMs)) {
                long delta = queryTimestampEpochMs - validEndEpochMs;
                return Math.exp(-(Math.log(2.0) / (double) halfLifeMs) * (double) delta);
            }
            return 1.0; // 窗口期内完全有效
        }
    }

    public TemporalCausalTriplet {
        Objects.requireNonNull(tripletId, "tripletId cannot be null");
        Objects.requireNonNull(subject, "subject cannot be null");
        Objects.requireNonNull(predicate, "predicate cannot be null");
        Objects.requireNonNull(object, "object cannot be null");
        Objects.requireNonNull(validTimeWindow, "validTimeWindow cannot be null");

        if (tripletVector != null && tripletVector.length != EMBEDDING_DIM) {
            throw new IllegalArgumentException("千问三元组嵌入向量维度必须严格为 " + EMBEDDING_DIM + "，实际: " + tripletVector.length);
        }
        tripletVector = tripletVector != null ? tripletVector.clone() : null;
    }

    @Override
    public float[] tripletVector() {
        return tripletVector != null ? tripletVector.clone() : null;
    }

    public static TemporalCausalTriplet of(
            String tripletId,
            String subject,
            String predicate,
            String object,
            long timestampEpochMs,
            String causalPredecessorId,
            long validStartEpochMs,
            long validEndEpochMs,
            long halfLifeMs,
            double confidence,
            float[] tripletVector
    ) {
        return new TemporalCausalTriplet(
                tripletId,
                subject,
                predicate,
                object,
                timestampEpochMs,
                causalPredecessorId,
                new ValidTimeWindow(validStartEpochMs, validEndEpochMs, halfLifeMs),
                confidence,
                tripletVector
        );
    }
}
