package tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 竞技场对决数据记录
 *
 * @author qknow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArenaMatchDO {
    private String matchId;
    private String question;
    private String policyAId;
    private String policyBId;
    private Map<String, String> anonymizedMapping; // 如 {"Model_X": policyAId, "Model_Y": policyBId}
    private JudgementResult forwardJudgement;      // 正向评测 (A, B)
    private JudgementResult backwardJudgement;     // 反向评测 (B, A)
    private MatchOutcome finalOutcome;             // 最终判定: A_WIN, B_WIN, TIE
    private double policyAScore;                   // 1.0, 0.5, 0.0
    private double policyBScore;                   // 0.0, 0.5, 1.0
    private double ratingDeltaA;                   // A 积分增量
    private double ratingDeltaB;                   // B 积分增量
    private String deepSeekThinkingProcess;        // R1 链式深度思考过程
    private String rationale;                      // 最终裁决判据解释
    private boolean verbosityPenaltyApplied;       // 是否触发长度惩罚
    @Builder.Default
    private long matchTimestamp = System.currentTimeMillis();

    public enum MatchOutcome {
        A_WIN,
        B_WIN,
        TIE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JudgementResult {
        private String winner;          // "A", "B", "TIE"
        private double confidence;      // 置信度 [0.0, 1.0]
        private String rationale;       // 裁决理由
        private String thinking;        // 链式思考过程
        private double lengthRatio;     // 长度比率
        private boolean penaltyApplied; // 是否受到长度惩罚
    }
}
