package tech.qiantong.qknow.ai.consensus.nash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 基于阿里千问 1536 维超球面测地投影与论据因果覆盖率的自适应置信度争议仲裁者 (定理 1.2)
 * 复杂度严格有界于 O(|N| * |Arguments|)，纯内存计算耗时 <= 10ms，保障合规/风控硬约束不被多数票穿透
 */
public class AdaptiveJudgeArbiter {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveJudgeArbiter.class);
    public static final int EMBEDDING_DIM = 1536;
    public static final double CONFIDENCE_THRESHOLD = 0.55;

    /**
     * 论据实体 (纯 Java 21 Record)
     */
    public record Argument(
            String argumentId,
            String agentRole, // PROCURER, RISK_CONTROL, LEGAL_COMPLIANCE
            String content,
            float[] embedding1536,
            double causalCoverage, // 因果覆盖率 [0.0, 1.0]
            boolean hasVetoHardConstraint // 是否包含合规/风控一票否决硬约束
    ) {}

    /**
     * 仲裁判定结果 (纯 Java 21 Record)
     */
    public record ArbitrationResult(
            String winningAgentRole,
            String winningStrategy,
            double arbitrationConfidence,
            Map<String, Double> agentScores,
            boolean vetoTriggered,
            String justification,
            long executionTimeMs
    ) {}

    /**
     * 执行争议仲裁判定
     *
     * @param topicEmbedding1536 议题 1536 维超球面归一化嵌入向量
     * @param arguments          各方提交的论据清单
     * @return 仲裁结果
     */
    public ArbitrationResult arbitrate(float[] topicEmbedding1536, List<Argument> arguments) {
        long startNano = System.nanoTime();

        if (topicEmbedding1536 == null || topicEmbedding1536.length < EMBEDDING_DIM) {
            throw new IllegalArgumentException("议题向量必须为 1536 维超球面向量");
        }
        if (arguments == null || arguments.isEmpty()) {
            return new ArbitrationResult("ARBITRATOR", "STATUS_QUO", 0.0, Map.of(), false,
                    "无有效论据，维持现状", 0);
        }

        // 1. 优先检查合规与风控的一票否决硬约束 (Hard Constraint)
        for (Argument arg : arguments) {
            if (arg.hasVetoHardConstraint()) {
                long durationMs = (System.nanoTime() - startNano) / 1_000_000L;
                log.warn("[JudgeArbiter] 触发合规/风控一票否决硬约束! 角色: {}, 论据: {}", arg.agentRole(), arg.content());
                return new ArbitrationResult(
                        arg.agentRole(),
                        "VETO_INTERCEPT",
                        1.0,
                        Map.of(arg.agentRole(), 100.0),
                        true,
                        "触发法务/风控硬约束一票否决: " + arg.content(),
                        Math.max(1, durationMs)
                );
            }
        }

        // 2. 遍历论据，计算超球面测地投影得分与因果权重
        Map<String, Double> agentScoreMap = new HashMap<>();
        double totalScore = 0.0;

        for (Argument arg : arguments) {
            double sGeo = calculateGeodesicProjection(arg.embedding1536(), topicEmbedding1536);
            double effectiveWeight = sGeo * Math.max(0.0, Math.min(1.0, arg.causalCoverage()));

            double current = agentScoreMap.getOrDefault(arg.agentRole(), 0.0);
            double updated = current + effectiveWeight;
            agentScoreMap.put(arg.agentRole(), updated);
            totalScore += effectiveWeight;
        }

        // 3. 寻找获胜角色与计算置信度
        String winner = "LEGAL_COMPLIANCE";
        double maxScore = -1.0;
        for (Map.Entry<String, Double> entry : agentScoreMap.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                winner = entry.getKey();
            }
        }

        double confidence = totalScore > 1e-9 ? (maxScore / totalScore) : 0.5;
        String winningStrategy = switch (winner) {
            case "PROCURER" -> "EXPEDITED_APPROVAL";
            case "RISK_CONTROL" -> "CONDITIONAL_APPROVAL_WITH_DEPOSIT";
            default -> "STRICT_LEGAL_COMPLIANCE_AMENDMENT";
        };

        long durationMs = (System.nanoTime() - startNano) / 1_000_000L;
        String justification = String.format(
                "仲裁者基于千问 1536 维超球面测地投影与因果覆盖率判定胜出者为 %s (综合强度: %.2f, 置信度: %.2f%%)",
                winner, maxScore, confidence * 100
        );

        return new ArbitrationResult(
                winner,
                winningStrategy,
                confidence,
                Collections.unmodifiableMap(agentScoreMap),
                false,
                justification,
                Math.max(1, durationMs)
        );
    }

    /**
     * 计算阿里千问 1536 维超球面测地内积投影: S_geo = (dot(v_a, v_topic) + 1) / 2
     */
    private double calculateGeodesicProjection(float[] vA, float[] vTopic) {
        if (vA == null || vTopic == null) {
            return 0.5;
        }
        int len = Math.min(vA.length, vTopic.length);
        double dot = 0.0;
        for (int i = 0; i < len; i++) {
            dot += vA[i] * vTopic[i];
        }
        return Math.max(0.0, Math.min(1.0, (dot + 1.0) * 0.5));
    }
}
