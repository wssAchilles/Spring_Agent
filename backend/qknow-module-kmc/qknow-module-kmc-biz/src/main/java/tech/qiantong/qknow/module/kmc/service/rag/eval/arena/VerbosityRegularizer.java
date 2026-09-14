package tech.qiantong.qknow.module.kmc.service.rag.eval.arena;

import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaMatchDO.JudgementResult;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Phase 39: 长度偏差下凸阻尼惩罚器 (Theorem 2.2: Convex Verbosity Penalty Invariant)
 * 防范候选模型单纯通过长篇大论文本注水骗取裁判高分的欺骗行为。
 *
 * @author qknow
 */
@Component
public class VerbosityRegularizer {

    public static final double LENGTH_RATIO_UPPER_BOUND = 1.25; // 超过 125% 长度触发怀疑门禁
    public static final double MIN_PENALTY_FACTOR = 0.70;        // 最低阻尼系数
    private static final Pattern WORD_SPLIT_PATTERN = Pattern.compile("[,，.。!！?？\\s;；]+");

    /**
     * 计算两个文本的字符长度比 (cand / base)
     */
    public double calculateLengthRatio(String candidateText, String baselineText) {
        if (candidateText == null || baselineText == null) {
            return 1.0;
        }
        int lenCand = Math.max(1, candidateText.trim().length());
        int lenBase = Math.max(1, baselineText.trim().length());
        return (double) lenCand / lenBase;
    }

    /**
     * 计算下凸长度惩罚系数 (Theorem 2.2)
     * 当长度比 > 1.25 且有效信息覆盖度没有成比例提升时，施加对数下凸惩罚
     */
    public double computeConvexPenalty(double lengthRatio, double entityCoverage) {
        if (lengthRatio <= LENGTH_RATIO_UPPER_BOUND) {
            return 1.0; // 无惩罚
        }
        // 若实体覆盖率伴随长度线性显著增加，说明确实信息量大，免予重罚
        if (entityCoverage >= lengthRatio * 0.9) {
            return 1.0;
        }
        // 下凸对数衰减惩罚: Phi(R_L) = max(0.70, 1.0 - 0.5 * ln(R_L))
        double penalty = 1.0 - 0.5 * Math.log(lengthRatio);
        return Math.max(MIN_PENALTY_FACTOR, Math.min(1.0, penalty));
    }

    /**
     * 抽取文本中的关键词元集合
     */
    public Set<String> extractKeyEntities(String text) {
        Set<String> entities = new HashSet<>();
        if (text == null || text.isBlank()) {
            return entities;
        }
        String[] tokens = WORD_SPLIT_PATTERN.split(text);
        for (String token : tokens) {
            if (token.length() >= 2) {
                entities.add(token.toLowerCase());
            }
        }
        return entities;
    }

    /**
     * 计算候选文本相对于黄金/基线实体的覆盖度
     */
    public double computeEntityCoverage(String candidateText, Set<String> goldEntities) {
        if (goldEntities == null || goldEntities.isEmpty()) {
            return 1.0;
        }
        Set<String> candTokens = extractKeyEntities(candidateText);
        long matched = goldEntities.stream().filter(candTokens::contains).count();
        return (double) matched / goldEntities.size();
    }

    /**
     * 综合校准裁判初评：若存在明显注水且初评判其获胜，实施下凸阻尼惩罚（胜局强制降级为平局）
     */
    public JudgementResult calibrateJudgement(JudgementResult initialJudgement, 
                                             String candAnswer, 
                                             String baseAnswer, 
                                             Set<String> goldEntities) {
        if (initialJudgement == null) {
            return null;
        }
        double lengthRatio = calculateLengthRatio(candAnswer, baseAnswer);
        double entityCoverage = computeEntityCoverage(candAnswer, goldEntities);
        double penalty = computeConvexPenalty(lengthRatio, entityCoverage);

        JudgementResult calibrated = JudgementResult.builder()
                .winner(initialJudgement.getWinner())
                .confidence(initialJudgement.getConfidence() * penalty)
                .rationale(initialJudgement.getRationale())
                .thinking(initialJudgement.getThinking())
                .lengthRatio(lengthRatio)
                .penaltyApplied(false)
                .build();

        // 若因注水导致惩罚生效，且原判定为 A 获胜（假设 A 为候选方），降级为 TIE
        if (penalty < 1.0 && "A".equalsIgnoreCase(initialJudgement.getWinner())) {
            calibrated.setWinner("TIE");
            calibrated.setPenaltyApplied(true);
            calibrated.setRationale(initialJudgement.getRationale() + " [注水下凸阻尼降级: 长度比 " + String.format("%.2f", lengthRatio) + " 判定降为平局]");
        }
        return calibrated;
    }
}
