package tech.qiantong.qknow.module.kmc.service.rag.temporal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.conflict.DocumentConflictResolutionService.ConflictStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Phase 26: 时态感知检索门禁与带半衰期指数时间衰减算子
 * 包含：
 * 1. 查询时间戳 t_q 生效区间硬门禁过滤 (有效性判定) 与状态机硬阻断；
 * 2. 基于半衰期 tau_{1/2} 的严格单调下凸指数时间衰减算子；
 * 3. 候选切片时态加权重排与过滤流水线。
 */
@Slf4j
@Component
public class TimeAwareRetrievalFilter {

    public static final double DEFAULT_HALF_LIFE_DAYS = 180.0; // 默认业务半衰期 180 天
    public static final double DEFAULT_ALPHA = 0.4;             // 默认时序调制强度 alpha (底线保底权重 1 - alpha = 0.6)

    /**
     * 候选切片时序元数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateSegment {
        private Long segmentId;
        private Long documentId;
        private String content;
        private double baseScore;
        private double finalScore;
        private Instant effectiveStart;
        private Instant effectiveEnd;
        private Instant publishTime;
        private ConflictStatus conflictStatus;
        private Long supersededById;
    }

    /**
     * 1. 检查切片在指定查询时间戳 t_q 处是否处于有效时限内，且未被废弃或取代
     *
     * @param segment 候选切片
     * @param queryTime 查询基准时间戳 (若为 null 则使用当前系统时间)
     * @return true 表示有效可召回，false 表示已过期或处于阻断状态
     */
    public boolean isEffectiveAt(CandidateSegment segment, Instant queryTime) {
        if (segment == null) {
            return false;
        }

        // 状态机硬阻断：被取代(SUPERSEDED)或已废弃(DEPRECATED)切片坚决阻断召回
        if (segment.getConflictStatus() == ConflictStatus.SUPERSEDED
                || segment.getConflictStatus() == ConflictStatus.DEPRECATED
                || segment.getConflictStatus() == ConflictStatus.CONFLICTED) {
            log.debug("切片 [{}] 因冲突/废弃状态 [{}] 被时序门禁直接阻断", segment.getSegmentId(), segment.getConflictStatus());
            return false;
        }

        Instant tq = queryTime != null ? queryTime : Instant.now();

        // 判定生效起始时间：effective_start <= t_q
        if (segment.getEffectiveStart() != null && segment.getEffectiveStart().isAfter(tq)) {
            log.debug("切片 [{}] 生效起始时间 [{}] 晚于查询时间 [{}]，尚未生效", segment.getSegmentId(), segment.getEffectiveStart(), tq);
            return false;
        }

        // 判定失效截止时间：effective_end > t_q
        if (segment.getEffectiveEnd() != null && !segment.getEffectiveEnd().isAfter(tq)) {
            log.debug("切片 [{}] 失效截止时间 [{}] 早于或等于查询时间 [{}]，已过期", segment.getSegmentId(), segment.getEffectiveEnd(), tq);
            return false;
        }

        return true;
    }

    /**
     * 2. 计算带半衰期 tau_{1/2} 的指数时间衰减加权因子
     * 公式：
     * Delta_t = max(0, t_q - t_{publish}) (以天为单位)
     * lambda = ln(2) / tau_{1/2}
     * W_temporal = (1 - alpha) + alpha * exp(-lambda * Delta_t)
     *
     * 证明特性：
     * 当 Delta_t = 0 时，W = 1.0；
     * 当 Delta_t = tau_{1/2} 时，W = 1 - 0.5 * alpha；
     * 当 Delta_t -> 无穷大 时，W -> 1 - alpha (保底置信度，不归零)；
     * 全区间严格单调递减且下凸：dW/dt <= 0, d^2W/dt^2 >= 0。
     *
     * @param publishTime 切片发布或最后更新时间戳
     * @param queryTime 查询基准时间戳
     * @param halfLifeDays 衰减半衰期 (天)
     * @param alpha 衰减调节权重 (0.0 <= alpha <= 1.0)
     * @return 衰减因子 [1-alpha, 1.0]
     */
    public double calculateTemporalDecayWeight(Instant publishTime, Instant queryTime, double halfLifeDays, double alpha) {
        if (publishTime == null) {
            return 1.0; // 若无发布时间，不衰减
        }
        Instant tq = queryTime != null ? queryTime : Instant.now();
        if (publishTime.isAfter(tq)) {
            return 1.0; // 若发布时间晚于查询基准，不作惩罚
        }

        double halfLife = halfLifeDays > 0 ? halfLifeDays : DEFAULT_HALF_LIFE_DAYS;
        double a = Math.max(0.0, Math.min(1.0, alpha));

        // 计算时间差 (天数)
        long millisDiff = Duration.between(publishTime, tq).toMillis();
        double deltaDays = millisDiff / (1000.0 * 60.0 * 60.0 * 24.0);

        // 衰减常数 lambda = ln(2) / tau_{1/2}
        double lambda = Math.log(2.0) / halfLife;

        // W_temporal = (1 - alpha) + alpha * exp(-lambda * deltaDays)
        double decayFactor = Math.exp(-lambda * deltaDays);
        return (1.0 - a) + (a * decayFactor);
    }

    /**
     * 3. 候选切片时态感知过滤与重排流水线
     *
     * @param candidates 原始候选切片列表
     * @param queryTime 查询基准时间戳
     * @param halfLifeDays 半衰期天数
     * @param alpha 衰减系数
     * @return 经过门禁清洗并完成时间衰减加权的候选列表，按 finalScore 降序排列
     */
    public List<CandidateSegment> filterAndRerank(List<CandidateSegment> candidates,
                                                   Instant queryTime,
                                                   double halfLifeDays,
                                                   double alpha) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Instant tq = queryTime != null ? queryTime : Instant.now();
        List<CandidateSegment> passed = new ArrayList<>();

        for (CandidateSegment seg : candidates) {
            if (isEffectiveAt(seg, tq)) {
                double weight = calculateTemporalDecayWeight(
                        seg.getPublishTime() != null ? seg.getPublishTime() : seg.getEffectiveStart(),
                        tq,
                        halfLifeDays,
                        alpha
                );
                seg.setFinalScore(seg.getBaseScore() * weight);
                passed.add(seg);
            }
        }

        // 按时态加权后最终得分降序重排
        passed.sort(Comparator.comparingDouble(CandidateSegment::getFinalScore).reversed());
        return passed;
    }
}
