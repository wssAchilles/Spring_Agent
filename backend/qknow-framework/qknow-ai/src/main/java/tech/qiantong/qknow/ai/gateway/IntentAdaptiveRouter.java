package tech.qiantong.qknow.ai.gateway;

import java.util.*;

/**
 * 千问 1536 维超球面动态意图路由器
 * <p>
 * 基于单位超球面测地线余弦相似度与节点健康度、实时负载的多目标帕累托加权选拔。
 *
 * @author Qknow AI Team
 * @since 2026-09-15
 */
public class IntentAdaptiveRouter {

    private final AgentGatewayRegistry registry;

    public IntentAdaptiveRouter(AgentGatewayRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "注册中心不能为空");
    }

    /**
     * 根据意图特征与负载选拔最优目标智能体
     *
     * @param intentQuery      自然语言意图描述
     * @param queryEmbedding   千问 1536 维超球面嵌入向量（已归一化，模长为 1.0）
     * @param currentLoads     各智能体当前并发负载因子 [0.0, 1.0]
     * @return 最优智能体元数据
     */
    public AgentGatewayRegistry.AgentMetadata route(
            String intentQuery,
            float[] queryEmbedding,
            Map<String, Double> currentLoads
    ) {
        List<AgentGatewayRegistry.AgentMetadata> candidates = registry.getHealthyAgents();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("无任何健康活跃智能体可供路由");
        }

        AgentGatewayRegistry.AgentMetadata bestAgent = null;
        double highestScore = Double.NEGATIVE_INFINITY;

        for (AgentGatewayRegistry.AgentMetadata agent : candidates) {
            double sim = 0.0;
            if (queryEmbedding != null && agent.embeddingVector() != null &&
                    queryEmbedding.length == agent.embeddingVector().length) {
                // 计算超球面测地线余弦内积（因为模长均为 1.0）
                sim = computeCosineSimilarity(queryEmbedding, agent.embeddingVector());
            } else {
                // 关键词能力匹配保底
                sim = computeLexicalMatch(intentQuery, agent.capabilities());
            }

            double health = agent.healthScore();
            double load = currentLoads.getOrDefault(agent.agentId(), 0.0);

            // 综合路由效用：0.5 * 意图相关度 + 0.3 * 健康分 - 0.2 * 负载
            double utilityScore = 0.5 * sim + 0.3 * health - 0.2 * load;

            if (utilityScore > highestScore) {
                highestScore = utilityScore;
                bestAgent = agent;
            }
        }

        if (bestAgent == null) {
            throw new IllegalStateException("未能选拔出满足效用门禁的智能体");
        }

        return bestAgent;
    }

    private double computeCosineSimilarity(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    private double computeLexicalMatch(String query, List<String> capabilities) {
        if (query == null || capabilities == null || capabilities.isEmpty()) {
            return 0.1;
        }
        String lowerQuery = query.toLowerCase();
        for (String cap : capabilities) {
            if (lowerQuery.contains(cap.toLowerCase())) {
                return 0.95;
            }
        }
        return 0.2;
    }
}
