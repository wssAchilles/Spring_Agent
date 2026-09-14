package tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 竞技场天梯排行榜条目
 *
 * @author qknow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArenaLeaderboardDO {
    private int rank;
    private String policyId;
    private String policyName;
    private String version;
    private double eloRating;
    private double confidenceInterval; // 置信区间半径 (+/- CI)
    private int totalMatches;
    private double winRate;
    private String tierBadge;          // 分段徽章 (宗师/大师/钻石/黄金/白银)
    private ArenaPolicyDO.PolicyStatus status;
}
