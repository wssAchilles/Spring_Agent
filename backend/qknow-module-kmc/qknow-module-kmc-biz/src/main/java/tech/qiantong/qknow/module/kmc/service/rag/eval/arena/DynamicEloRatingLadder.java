package tech.qiantong.qknow.module.kmc.service.rag.eval.arena;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaLeaderboardDO;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaPolicyDO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Phase 39: 动态 Elo 积分天梯 (Theorem 1.1: Martingale Convergence Invariant)
 * 基于 Bradley-Terry-Luce (BTL) 概率选择模型与自适应退火 K 因子，满足严格零和积分守恒。
 *
 * @author qknow
 */
@Service
public class DynamicEloRatingLadder {

    public static final double DEFAULT_RATING = 1200.0;
    public static final double BASE_K_FACTOR = 32.0;
    public static final double MIN_K_FACTOR = 12.0;
    public static final double K_ANNEALING_RATE = 0.95; // 退火速率
    public static final double SCALE_FACTOR = 400.0;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatingUpdateResult {
        private double newRatingA;
        private double newRatingB;
        private double deltaA;
        private double deltaB;
        private double expectedA;
        private double expectedB;
        private double effectiveK;
    }

    /**
     * 计算 BTL 期望胜率: E_A = 1 / (1 + 10^((R_B - R_A) / 400))
     */
    public double calculateExpectedScore(double ratingA, double ratingB) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / SCALE_FACTOR));
    }

    /**
     * 计算基于对战履历场次的自适应 K 因子退火值 (Theorem 1.1)
     */
    public double computeAdaptiveK(int matchCount) {
        double k = BASE_K_FACTOR * Math.pow(K_ANNEALING_RATE, matchCount);
        return Math.max(MIN_K_FACTOR, k);
    }

    /**
     * 执行零和守恒的动态 Elo 更新: Delta R_A + Delta R_B = 0
     * scoreA, scoreB: 胜 1.0, 平 0.5, 负 0.0 (满足 scoreA + scoreB = 1.0)
     */
    public RatingUpdateResult updateRatings(double ratingA, double ratingB, 
                                           int matchesA, int matchesB, 
                                           double scoreA, double scoreB) {
        double expectedA = calculateExpectedScore(ratingA, ratingB);
        double expectedB = 1.0 - expectedA; // 严格对称柯尔莫哥洛夫公理

        double kA = computeAdaptiveK(matchesA);
        double kB = computeAdaptiveK(matchesB);
        double effectiveK = (kA + kB) / 2.0; // 对称有效 K 因子保证对弈零和

        double deltaA = effectiveK * (scoreA - expectedA);
        double deltaB = effectiveK * (scoreB - expectedB);

        // 数学上: scoreA + scoreB = 1.0, expectedA + expectedB = 1.0 => deltaA + deltaB = 0
        return RatingUpdateResult.builder()
                .newRatingA(ratingA + deltaA)
                .newRatingB(ratingB + deltaB)
                .deltaA(deltaA)
                .deltaB(deltaB)
                .expectedA(expectedA)
                .expectedB(expectedB)
                .effectiveK(effectiveK)
                .build();
    }

    /**
     * 生成天梯排行榜与置信区间
     */
    public List<ArenaLeaderboardDO> generateLeaderboard(List<ArenaPolicyDO> policies) {
        List<ArenaPolicyDO> sorted = new ArrayList<>(policies);
        sorted.sort(Comparator.comparingDouble(ArenaPolicyDO::getEloRating).reversed());

        List<ArenaLeaderboardDO> leaderboard = new ArrayList<>();
        int rank = 1;
        for (ArenaPolicyDO p : sorted) {
            // 置信区间半径估计: 1.96 * K / sqrt(N + 1)
            double ci = 1.96 * computeAdaptiveK(p.getMatchCount()) / Math.sqrt(p.getMatchCount() + 1);
            String badge = getTierBadge(p.getEloRating());

            leaderboard.add(ArenaLeaderboardDO.builder()
                    .rank(rank++)
                    .policyId(p.getPolicyId())
                    .policyName(p.getName())
                    .version(p.getVersion())
                    .eloRating(Math.round(p.getEloRating() * 10.0) / 10.0)
                    .confidenceInterval(Math.round(ci * 10.0) / 10.0)
                    .totalMatches(p.getMatchCount())
                    .winRate(Math.round(p.getWinRate() * 1000.0) / 10.0)
                    .tierBadge(badge)
                    .status(p.getStatus())
                    .build());
        }
        return leaderboard;
    }

    private String getTierBadge(double rating) {
        if (rating >= 1400.0) return "宗师";
        if (rating >= 1300.0) return "大师";
        if (rating >= 1200.0) return "钻石";
        if (rating >= 1100.0) return "黄金";
        return "白银";
    }
}
