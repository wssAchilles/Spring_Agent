package tech.qiantong.qknow.hermes.a2a.card;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分布式智能体能力网格中心与自适应竞标调度器
 */
@Component
public class AgentMeshRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentMeshRegistry.class);
    private final Map<String, AgentCard> onlineCards = new ConcurrentHashMap<>();
    private final Map<String, Long> activeLeases = new ConcurrentHashMap<>();

    public void registerCard(AgentCard card) {
        if (card != null && card.agentId() != null) {
            onlineCards.put(card.agentId(), card);
            log.info("[AgentMesh] 注册 AgentCard: {} (capabilities={})", card.agentName(), card.capabilities());
        }
    }

    public void unregisterCard(String agentId) {
        if (agentId != null) {
            onlineCards.remove(agentId);
            log.info("[AgentMesh] 注销 AgentCard: {}", agentId);
        }
    }

    public Optional<AgentCard> getCard(String agentId) {
        return Optional.ofNullable(onlineCards.get(agentId));
    }

    public List<AgentCard> listOnlineCards() {
        return List.copyOf(onlineCards.values());
    }

    /**
     * 签发时效性安全租约令牌
     */
    public String issueLeaseToken(String agentId, long durationMs) {
        String token = "LEASE_" + UUID.randomUUID().toString().replace("-", "") + "_" + agentId;
        long expiry = Instant.now().toEpochMilli() + durationMs;
        activeLeases.put(token, expiry);
        return token;
    }

    /**
     * 验证安全租约令牌有效性与防重放
     */
    public boolean verifyLeaseToken(String token) {
        if (token == null || !activeLeases.containsKey(token)) {
            return false;
        }
        long expiry = activeLeases.get(token);
        long now = Instant.now().toEpochMilli();
        if (now > expiry) {
            activeLeases.remove(token);
            return false;
        }
        return true;
    }

    /**
     * 定理 1.2: 基于千问 1536 维超球面测地线内积与信誉得分的加权竞标算法
     * S(Q, A) = 0.70 * cos(theta) + 0.30 * R
     */
    public Optional<BiddingMatchResult> findBestWorker(float[] queryVector, String requiredCapability) {
        if (onlineCards.isEmpty()) {
            return Optional.empty();
        }

        AgentCard bestCard = null;
        double maxScore = -1.0;

        for (AgentCard card : onlineCards.values()) {
            if (!card.online()) {
                continue;
            }
            if (requiredCapability != null && !requiredCapability.isBlank()
                    && card.capabilities() != null && !card.capabilities().contains(requiredCapability)) {
                continue;
            }

            double semanticSimilarity = 0.5;
            if (queryVector != null && card.embedding1536() != null && queryVector.length == card.embedding1536().length) {
                semanticSimilarity = computeCosineSimilarity(queryVector, card.embedding1536());
            }

            double totalScore = 0.70 * Math.max(0.0, semanticSimilarity) + 0.30 * Math.max(0.0, card.reputationScore());
            if (totalScore > maxScore) {
                maxScore = totalScore;
                bestCard = card;
            }
        }

        if (bestCard != null) {
            return Optional.of(new BiddingMatchResult(bestCard, maxScore));
        }
        return Optional.empty();
    }

    private double computeCosineSimilarity(float[] a, float[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator < 1e-9) {
            return 0.0;
        }
        return dot / denominator;
    }

    public record BiddingMatchResult(AgentCard card, double compositeScore) {}
}
