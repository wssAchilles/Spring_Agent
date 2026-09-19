package tech.qiantong.qknow.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 阿里千问 1536 维超球面测地距离与毫秒级时钟指数半衰期衰减对齐器 (定理 1.1)
 * 结合超球面流形角距离与动态时序生命周期，计算复合时空对齐得分，彻底杜绝陈旧知识倒挂
 */
@Component
public class SpatiotemporalDecayAligner {

    private static final Logger log = LoggerFactory.getLogger(SpatiotemporalDecayAligner.class);

    public static final int EMBEDDING_DIM = 1536;
    public static final double DEFAULT_HALF_LIFE_DAYS = 180.0; // 默认半衰期 180 天
    public static final double EXPIRED_PENALTY = 0.01; // 过期失效事实惩罚因子
    public static final double ALPHA_GEO_WEIGHT = 0.65; // 几何测地相似度权重

    /**
     * 计算阿里千问 1536 维超球面测地余弦相似度
     * 在单位超球面 S^1535 (||v||_2 = 1.0) 上，内积等价于测地余弦值
     *
     * @param queryEmb  查询向量 (1536 维归一化向量)
     * @param entityEmb 实体向量 (1536 维归一化向量)
     * @return 几何测地相似度 [0.0, 1.0]
     */
    public double calculateGeodesicSimilarity(float[] queryEmb, float[] entityEmb) {
        if (queryEmb == null || entityEmb == null) {
            return 0.0;
        }
        int len = Math.min(queryEmb.length, entityEmb.length);
        if (len < EMBEDDING_DIM) {
            return 0.0;
        }

        double dotProduct = 0.0;
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            dotProduct += queryEmb[i] * entityEmb[i];
        }

        // 测地余弦投影归一化到 [0.0, 1.0]
        return Math.max(0.0, Math.min(1.0, (dotProduct + 1.0) * 0.5));
    }

    /**
     * 计算毫秒级时序指数半衰期衰减系数 D(Δt) = exp(-λ * Δt)
     *
     * @param factTimestampMs 事实生效或更新时间戳 (毫秒)
     * @param currentEpochMs  当前查询时间戳 (毫秒)
     * @param halfLifeDays    半衰期天数 (如 180 天)
     * @param isExplicitlyExpired 是否已显式过期作废
     * @return 时序衰减因子 [0.0, 1.0]
     */
    public double calculateTemporalDecay(
            long factTimestampMs,
            long currentEpochMs,
            double halfLifeDays,
            boolean isExplicitlyExpired
    ) {
        if (isExplicitlyExpired) {
            return EXPIRED_PENALTY;
        }

        long deltaMs = Math.max(0L, currentEpochMs - factTimestampMs);
        double tauDays = halfLifeDays > 0.0 ? halfLifeDays : DEFAULT_HALF_LIFE_DAYS;

        // λ = ln(2) / (tau * 86,400,000 ms)
        double lambda = Math.log(2.0) / (tauDays * 86_400_000.0);
        double decay = Math.exp(-lambda * deltaMs);

        return Math.max(0.0, Math.min(1.0, decay));
    }

    /**
     * 计算复合时空对齐得分 S_st = (α * S_geo + (1 - α) * S_ppr) * D(Δt)
     *
     * @param queryEmb            查询 1536 维超球面向量
     * @param entityEmb           实体 1536 维超球面向量
     * @param pprScore            局部子图 PPR 拓扑得分 [0.0, 1.0]
     * @param factTimestampMs     事实有效时间戳 (毫秒)
     * @param currentEpochMs      当前查询时间戳 (毫秒)
     * @param isExplicitlyExpired 是否已显式失效
     * @return 复合时空对齐得分
     */
    public double alignSpatiotemporalScore(
            float[] queryEmb,
            float[] entityEmb,
            double pprScore,
            long factTimestampMs,
            long currentEpochMs,
            boolean isExplicitlyExpired
    ) {
        double sGeo = calculateGeodesicSimilarity(queryEmb, entityEmb);
        double decay = calculateTemporalDecay(factTimestampMs, currentEpochMs, DEFAULT_HALF_LIFE_DAYS, isExplicitlyExpired);

        double compositeSpatial = ALPHA_GEO_WEIGHT * sGeo + (1.0 - ALPHA_GEO_WEIGHT) * Math.max(0.0, pprScore);
        double finalScore = compositeSpatial * decay;

        if (log.isTraceEnabled()) {
            log.trace("[SpatiotemporalAligner] sGeo={:.4f}, ppr={:.4f}, decay={:.4f}, final={:.4f}",
                    sGeo, pprScore, decay, finalScore);
        }

        return Math.max(0.0, Math.min(1.0, finalScore));
    }
}
