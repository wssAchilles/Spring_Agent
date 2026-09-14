package tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 竞技场策略数据对象
 *
 * @author qknow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArenaPolicyDO {
    private String policyId;
    private String name;
    private String version;
    private String promptTemplate;
    @Builder.Default
    private double eloRating = 1200.0;
    @Builder.Default
    private int matchCount = 0;
    @Builder.Default
    private int winCount = 0;
    @Builder.Default
    private int lossCount = 0;
    @Builder.Default
    private int tieCount = 0;
    @Builder.Default
    private PolicyStatus status = PolicyStatus.ACTIVE;
    @Builder.Default
    private Map<String, Object> tags = new HashMap<>();
    @Builder.Default
    private long lastUpdated = System.currentTimeMillis();

    public enum PolicyStatus {
        ACTIVE,      // 活跃参战中
        CANDIDATE,   // 候选自进化策略
        PROMOTED,    // 满足门禁已晋级
        REJECTED     // 评测未达标淘汰
    }

    public double getWinRate() {
        if (matchCount == 0) {
            return 0.0;
        }
        return (double) winCount / matchCount;
    }

    public double getEffectiveScoreRate() {
        if (matchCount == 0) {
            return 0.0;
        }
        return (winCount * 1.0 + tieCount * 0.5) / matchCount;
    }
}
