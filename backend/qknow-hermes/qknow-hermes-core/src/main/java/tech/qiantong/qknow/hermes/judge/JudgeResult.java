package tech.qiantong.qknow.hermes.judge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI Judge 评分结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JudgeResult {
    private Double factualityScore;
    private Double relevanceScore;
    private Double instructionScore;
    private Double overallScore;
    private boolean passed;
    private String feedback;

    public static JudgeResult passed(double factuality, double relevance, double instruction, String feedback) {
        JudgeResult r = new JudgeResult();
        r.factualityScore = factuality;
        r.relevanceScore = relevance;
        r.instructionScore = instruction;
        r.overallScore = (factuality + relevance + instruction) / 3.0;
        r.passed = true;
        r.feedback = feedback;
        return r;
    }

    public static JudgeResult failed(double factuality, double relevance, double instruction, String feedback) {
        JudgeResult r = new JudgeResult();
        r.factualityScore = factuality;
        r.relevanceScore = relevance;
        r.instructionScore = instruction;
        r.overallScore = (factuality + relevance + instruction) / 3.0;
        r.passed = false;
        r.feedback = feedback;
        return r;
    }
}
