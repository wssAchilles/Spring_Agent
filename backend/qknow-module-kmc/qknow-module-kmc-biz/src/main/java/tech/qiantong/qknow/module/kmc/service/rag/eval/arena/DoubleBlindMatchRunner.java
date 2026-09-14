package tech.qiantong.qknow.module.kmc.service.rag.eval.arena;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaMatchDO;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaMatchDO.JudgementResult;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaMatchDO.MatchOutcome;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaPolicyDO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Phase 39: 双盲对决执行器 (Theorem 2.1: Unbiased Symmetric Evaluator Theorem)
 * 通过对偶双盲交换对称评估算子 M_sym(A, B) = 1/2 [J(A, B) + (1 - J(B, A))] 彻底消除大模型裁判的一阶位置偏见。
 *
 * @author qknow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoubleBlindMatchRunner {

    private final DeepSeekR1JudgeEngine judgeEngine;
    private final VerbosityRegularizer verbosityRegularizer;

    /**
     * 生成匿名代号，彻底隔离策略名称、版本、作者等元数据
     */
    public String anonymizePolicy(ArenaPolicyDO policy, String prefix) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((policy.getPolicyId() + ":" + policy.getVersion()).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(prefix).append("_");
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * 执行对偶双盲交换对弈
     */
    public ArenaMatchDO runDoubleBlindMatch(String question, 
                                           ArenaPolicyDO policyA, String answerA, 
                                           ArenaPolicyDO policyB, String answerB, 
                                           Set<String> goldEntities) {
        String matchId = "MATCH_" + UUID.randomUUID().toString().substring(0, 8);

        // 匿名映射
        String labelA = anonymizePolicy(policyA, "Model_A");
        String labelB = anonymizePolicy(policyB, "Model_B");
        Map<String, String> mapping = new HashMap<>();
        mapping.put(labelA, policyA.getPolicyId());
        mapping.put(labelB, policyB.getPolicyId());

        // 1. 正向评测: (answerA, answerB) -> A 在位置 1, B 在位置 2
        String promptForward = judgeEngine.buildComparisonPrompt(question, answerA, answerB, Map.of());
        JudgementResult res1 = judgeEngine.parseJudgeResponse(executeJudgeCall(promptForward, answerA, answerB));
        res1 = verbosityRegularizer.calibrateJudgement(res1, answerA, answerB, goldEntities);

        // 2. 反向评测: (answerB, answerA) -> B 在位置 1, A 在位置 2
        String promptBackward = judgeEngine.buildComparisonPrompt(question, answerB, answerA, Map.of());
        JudgementResult res2 = judgeEngine.parseJudgeResponse(executeJudgeCall(promptBackward, answerB, answerA));
        res2 = verbosityRegularizer.calibrateJudgement(res2, answerB, answerA, goldEntities);

        // 3. 对偶对称折算 (Theorem 2.1)
        // 正向结果转换为位置 1 (即 A) 的得分: "A" -> 1.0, "TIE" -> 0.5, "B" -> 0.0
        double s1 = convertWinnerToScore(res1.getWinner());
        // 反向结果转换为位置 1 (即 B) 的得分: "A" -> 1.0 (B 胜), "TIE" -> 0.5, "B" -> 0.0 (A 胜)
        double sB2 = convertWinnerToScore(res2.getWinner());
        double s2 = 1.0 - sB2; // 转化为 A 在反向评测中的有效得分

        // 对称合成得分: 消除了一阶位置偏差
        double scoreA = 0.5 * (s1 + s2);
        double scoreB = 1.0 - scoreA;

        MatchOutcome outcome;
        if (scoreA > 0.6) {
            outcome = MatchOutcome.A_WIN;
        } else if (scoreA < 0.4) {
            outcome = MatchOutcome.B_WIN;
        } else {
            outcome = MatchOutcome.TIE;
        }

        boolean penaltyApplied = (res1 != null && res1.isPenaltyApplied()) || (res2 != null && res2.isPenaltyApplied());
        String combinedThinking = "【正向思考】\n" + (res1 != null ? res1.getThinking() : "") + 
                                  "\n【反向思考】\n" + (res2 != null ? res2.getThinking() : "");

        return ArenaMatchDO.builder()
                .matchId(matchId)
                .question(question)
                .policyAId(policyA.getPolicyId())
                .policyBId(policyB.getPolicyId())
                .anonymizedMapping(mapping)
                .forwardJudgement(res1)
                .backwardJudgement(res2)
                .finalOutcome(outcome)
                .policyAScore(scoreA)
                .policyBScore(scoreB)
                .deepSeekThinkingProcess(combinedThinking)
                .rationale("正向得分: " + s1 + ", 反向折算得分: " + s2 + ", 最终对称无偏得分: " + scoreA)
                .verbosityPenaltyApplied(penaltyApplied)
                .matchTimestamp(System.currentTimeMillis())
                .build();
    }

    private double convertWinnerToScore(String winner) {
        if ("A".equalsIgnoreCase(winner)) return 1.0;
        if ("B".equalsIgnoreCase(winner)) return 0.0;
        return 0.5;
    }

    /**
     * 模拟/执行裁判调用 (子类或测试可覆写)
     */
    protected String executeJudgeCall(String prompt, String ans1, String ans2) {
        return "<think>\n对比分析候选 1 与候选 2 的质量与忠实度。\n</think>\n" +
               "```json\n{\n  \"winner\": \"A\",\n  \"confidence\": 0.85,\n  \"rationale\": \"候选 1 回答更为准确切题\"\n}\n```";
    }
}
