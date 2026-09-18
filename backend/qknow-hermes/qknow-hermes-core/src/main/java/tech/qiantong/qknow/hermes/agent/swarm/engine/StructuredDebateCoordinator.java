package tech.qiantong.qknow.hermes.agent.swarm.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.agent.swarm.dto.MultiAgentDebateReceipt;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化对抗辩论控制器 (StructuredDebateCoordinator)
 * 严格管理“提案者 (Proponent) - 反对者 (Opponent) - 裁判仲裁 (Judge)”三方结构化会话协议
 * 刚性限制最大轮次 (2~3 轮) 与单轮 Token 配额，基于千问 1536 维超球面测地散度执行收敛截断
 */
@Slf4j
@Component
public class StructuredDebateCoordinator {

    public static final int DEFAULT_MAX_ROUNDS = 2;
    public static final int ABSOLUTE_MAX_ROUNDS = 3;
    public static final int MAX_TOKEN_QUOTA_PER_ROUND = 1500;
    public static final double GEODESIC_CONVERGENCE_MARGIN = 0.15; // 测地散度低于 0.15 判定为达成对齐

    /**
     * 执行三方对抗辩论
     */
    public MultiAgentDebateReceipt coordinateDebate(
            String debateId,
            String topic,
            AgentDebateParticipant proponent,
            AgentDebateParticipant opponent,
            AgentDebateJudge judge,
            int requestedMaxRounds) {

        long startTime = System.currentTimeMillis();
        int maxRounds = Math.min(requestedMaxRounds > 0 ? requestedMaxRounds : DEFAULT_MAX_ROUNDS, ABSOLUTE_MAX_ROUNDS);
        log.info("[Debate] 启动结构化对抗辩论: debateId={}, topic={}, maxRounds={}", debateId, topic, maxRounds);

        List<String> transcript = new ArrayList<>();
        List<String> participants = List.of(proponent.getAgentId(), opponent.getAgentId(), judge.getAgentId());
        boolean convergedEarly = false;
        int currentRound = 0;

        String lastProponentArgument = "";
        String lastOpponentArgument = "";

        while (currentRound < maxRounds) {
            currentRound++;
            log.debug("[Debate] 执行辩论第 [{}/{}] 轮", currentRound, maxRounds);

            // 1. 提案者立论 / 答辩
            String rawPropOutput = proponent.generateArgument(topic, transcript, lastOpponentArgument);
            lastProponentArgument = applyTokenQuota(rawPropOutput, MAX_TOKEN_QUOTA_PER_ROUND);
            transcript.add("Round " + currentRound + " [Proponent]: " + lastProponentArgument);

            // 2. 反对者质询 / 辩驳
            String rawOppOutput = opponent.generateRebuttal(topic, transcript, lastProponentArgument);
            lastOpponentArgument = applyTokenQuota(rawOppOutput, MAX_TOKEN_QUOTA_PER_ROUND);
            transcript.add("Round " + currentRound + " [Opponent]: " + lastOpponentArgument);

            // 3. 阿里千问 1536 维超球面测地散度收敛性研判
            float[] propVec = proponent.getArgumentEmbedding(lastProponentArgument);
            float[] oppVec = opponent.getArgumentEmbedding(lastOpponentArgument);

            double divergence = computeGeodesicDivergence(propVec, oppVec);
            log.debug("[Debate] 第 [{}] 轮测地散度评估: divergence={}", currentRound, divergence);

            if (divergence <= GEODESIC_CONVERGENCE_MARGIN) {
                convergedEarly = true;
                log.info("[Debate] 辩论在第 [{}] 轮达到纳什测地收敛 (散度 {} <= {})，提前触发退出截断",
                        currentRound, divergence, GEODESIC_CONVERGENCE_MARGIN);
                break;
            }
        }

        // 4. 移交中立仲裁裁判裁决
        log.info("[Debate] 辩论交锋完毕 (共 {} 轮, 提前收敛: {})，递交仲裁裁判处理...", currentRound, convergedEarly);
        JudgeVerdictBO verdict = judge.arbitrate(topic, transcript);

        long latencyMs = System.currentTimeMillis() - startTime;
        return MultiAgentDebateReceipt.createSigned(
                debateId,
                participants,
                currentRound,
                verdict.finalVerdict(),
                verdict.confidenceScore(),
                List.of(proponent.getAgentId() + "->" + opponent.getAgentId()),
                convergedEarly,
                verdict.factVerified(),
                latencyMs,
                System.currentTimeMillis()
        );
    }

    /**
     * 单轮 Token 配额安全截断（按 1 Token ≈ 3.5 字符进行保护性截断）
     */
    private String applyTokenQuota(String text, int maxTokenQuota) {
        if (text == null) return "";
        int maxCharsApprox = (int) (maxTokenQuota * 3.5);
        if (text.length() > maxCharsApprox) {
            log.warn("[Debate] 论述文本超出单轮配额 ({} > {} 字符)，执行刚性截断", text.length(), maxCharsApprox);
            return text.substring(0, maxCharsApprox) + "... [TOKEN_QUOTA_EXCEEDED_TRUNCATED]";
        }
        return text;
    }

    /**
     * 计算阿里千问 1536 维超球面测地大圆弧散度 [0.0, 1.0]
     * d_g = arccos(clamp(u · v, -1.0, 1.0)) / PI
     */
    private double computeGeodesicDivergence(float[] u, float[] v) {
        if (u == null || v == null || u.length != 1536 || v.length != 1536) {
            return 1.0;
        }
        double dot = 0.0;
        for (int i = 0; i < 1536; i++) {
            dot += (double) u[i] * v[i];
        }
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double geodAngle = Math.acos(dot); // 弧度 [0, pi]
        return geodAngle / Math.PI;
    }

    public interface AgentDebateParticipant {
        default String getAgentId() { return "debater"; }
        String generateArgument(String topic, List<String> history, String opponentLastArg);
        String generateRebuttal(String topic, List<String> history, String proponentLastArg);
        float[] getArgumentEmbedding(String text);
    }

    @FunctionalInterface
    public interface AgentDebateJudge {
        default String getAgentId() { return "judge-default"; }
        JudgeVerdictBO arbitrate(String topic, List<String> transcript);
    }

    public record JudgeVerdictBO(
            String finalVerdict,
            double confidenceScore,
            boolean factVerified
    ) {}
}
