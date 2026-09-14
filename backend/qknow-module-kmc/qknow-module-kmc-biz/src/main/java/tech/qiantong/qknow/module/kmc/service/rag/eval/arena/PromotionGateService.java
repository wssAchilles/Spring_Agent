package tech.qiantong.qknow.module.kmc.service.rag.eval.arena;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaPolicyDO;

/**
 * Phase 39: 策略自动化晋级双重门禁
 * 规则：
 * 1. 对战 Baseline 策略胜率 >= 60%
 * 2. 天梯 Elo 积分净增量 Delta Elo >= +30
 * 3. 最低有效对局数 >= 5
 *
 * @author qknow
 */
@Service
public class PromotionGateService {

    public static final double WIN_RATE_PROMOTION_THRESHOLD = 0.60;
    public static final double ELO_DELTA_PROMOTION_THRESHOLD = 30.0;
    public static final int MIN_MATCHES_FOR_PROMOTION = 5;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionDecision {
        private boolean isPromoted;
        private double winRateAgainstBase;
        private double eloDelta;
        private int totalMatches;
        private String decisionReason;
        private ArenaPolicyDO.PolicyStatus finalStatus;
    }

    public PromotionDecision evaluatePromotion(ArenaPolicyDO candidate, ArenaPolicyDO baseline) {
        if (candidate == null || baseline == null) {
            return PromotionDecision.builder()
                    .isPromoted(false)
                    .decisionReason("策略实体不能为空")
                    .finalStatus(ArenaPolicyDO.PolicyStatus.REJECTED)
                    .build();
        }

        double eloDelta = candidate.getEloRating() - baseline.getEloRating();
        double winRate = candidate.getEffectiveScoreRate();
        int matches = candidate.getMatchCount();

        boolean matchesOk = matches >= MIN_MATCHES_FOR_PROMOTION;
        boolean winRateOk = winRate >= WIN_RATE_PROMOTION_THRESHOLD;
        boolean eloDeltaOk = eloDelta >= ELO_DELTA_PROMOTION_THRESHOLD;

        boolean pass = matchesOk && winRateOk && eloDeltaOk;
        ArenaPolicyDO.PolicyStatus status = pass ? ArenaPolicyDO.PolicyStatus.PROMOTED : 
                (matches >= MIN_MATCHES_FOR_PROMOTION ? ArenaPolicyDO.PolicyStatus.REJECTED : ArenaPolicyDO.PolicyStatus.CANDIDATE);

        String reason = String.format("场次: %d (需>=%d), 得分胜率: %.1f%% (需>=%.0f%%), Elo增量: %.1f (需>=%.0f)",
                matches, MIN_MATCHES_FOR_PROMOTION, winRate * 100.0, WIN_RATE_PROMOTION_THRESHOLD * 100.0, eloDelta, ELO_DELTA_PROMOTION_THRESHOLD);

        return PromotionDecision.builder()
                .isPromoted(pass)
                .winRateAgainstBase(winRate)
                .eloDelta(eloDelta)
                .totalMatches(matches)
                .decisionReason(reason)
                .finalStatus(status)
                .build();
    }

    public void applyPromotionDecision(ArenaPolicyDO candidate, PromotionDecision decision) {
        if (candidate != null && decision != null) {
            candidate.setStatus(decision.getFinalStatus());
            candidate.getTags().put("promotionReason", decision.getDecisionReason());
            candidate.getTags().put("promotedTimestamp", System.currentTimeMillis());
        }
    }
}
