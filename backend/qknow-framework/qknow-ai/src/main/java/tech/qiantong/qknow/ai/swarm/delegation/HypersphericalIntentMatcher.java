package tech.qiantong.qknow.ai.swarm.delegation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * 阿里千问 1536 维超球面高精度意图自适应委托匹配器
 * 施加单位超球面归一化校验与 tau_intent >= 0.82 严格门禁
 */
public class HypersphericalIntentMatcher {

    private static final Logger log = LoggerFactory.getLogger(HypersphericalIntentMatcher.class);

    public static final int EMBEDDING_DIMENSION = 1536;
    public static final double NORM_TOLERANCE = 1e-4;
    public static final double INTENT_CONFIDENCE_THRESHOLD = 0.82; // 严格门禁

    /**
     * 匹配最吻合能力的候选子智能体
     */
    public IntentMatchResult matchTargetAgent(float[] queryVector, List<AgentCard> candidateAgents) {
        Objects.requireNonNull(queryVector, "queryVector 不能为空");
        Objects.requireNonNull(candidateAgents, "candidateAgents 不能为空");

        // 1. 维度与超球面归一化几何约束校验
        validateHypersphericalConstraint(queryVector);

        if (candidateAgents.isEmpty()) {
            return IntentMatchResult.escalated("NO_CANDIDATE_AGENTS", "候选智能体集合为空，安全上浮至主控协调者");
        }

        AgentCard bestMatch = null;
        double maxAffinity = -1.0;

        for (AgentCard agent : candidateAgents) {
            validateHypersphericalConstraint(agent.capabilityVector());
            double affinity = computeCosineSimilarity(queryVector, agent.capabilityVector());
            if (affinity > maxAffinity) {
                maxAffinity = affinity;
                bestMatch = agent;
            }
        }

        // 2. 置信度门禁检查
        if (maxAffinity >= INTENT_CONFIDENCE_THRESHOLD && bestMatch != null) {
            log.info("[IntentMatcher] 命中目标智能体: {}, 亲和度得分: {}", bestMatch.agentId(), String.format("%.4f", maxAffinity));
            return IntentMatchResult.delegated(bestMatch.agentId(), maxAffinity);
        } else {
            log.warn("[IntentMatcher] 最高亲和度 {} 低于门禁阈值 {}, 触发安全上浮", String.format("%.4f", maxAffinity), INTENT_CONFIDENCE_THRESHOLD);
            return IntentMatchResult.escalated("LOW_AFFINITY_CONFIDENCE",
                    "最高语义亲和度 " + String.format("%.4f", maxAffinity) + " 低于门禁 0.82，拒绝盲目委派");
        }
    }

    /**
     * 严格校验向量维度为 1536 且位于超球面流形 S^1535 (模长 1.0 +- 1e-4)
     */
    public void validateHypersphericalConstraint(float[] vector) {
        if (vector == null || vector.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("向量维度必须严格等于 " + EMBEDDING_DIMENSION +
                    ", 实际传入: " + (vector == null ? "null" : vector.length));
        }
        double sumSq = 0.0;
        for (float v : vector) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        if (Math.abs(norm - 1.0) > NORM_TOLERANCE) {
            throw new IllegalStateException("向量未落在单位超球面流形 (hypersphere manifold S^1535) 上! 模长: " + norm);
        }
    }

    private double computeCosineSimilarity(float[] v1, float[] v2) {
        double dot = 0.0;
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            dot += v1[i] * v2[i];
        }
        return dot;
    }

    public record AgentCard(String agentId, String agentName, String roleDescription, float[] capabilityVector) {}

    public record IntentMatchResult(boolean delegated, String targetAgentId, double affinityScore, String escalationReason) {
        public static IntentMatchResult delegated(String targetAgentId, double score) {
            return new IntentMatchResult(true, targetAgentId, score, null);
        }
        public static IntentMatchResult escalated(String code, String reason) {
            return new IntentMatchResult(false, null, -1.0, code + ": " + reason);
        }
    }
}
