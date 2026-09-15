package tech.qiantong.qknow.ai.gateway;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 自组织多智能体动态微服务网关注册中心
 * <p>
 * 支持智能体动态注册、下线、自适应心跳探活与故障剔除。
 *
 * @author Qknow AI Team
 * @since 2026-09-15
 */
public class AgentGatewayRegistry {

    public enum AgentStatus {
        ONLINE,
        DEGRADED,
        DEAD
    }

    public record AgentMetadata(
            String agentId,
            String name,
            String endpoint,
            List<String> capabilities,
            float[] embeddingVector,
            double healthScore,
            AgentStatus status,
            long lastHeartbeatTime
    ) {
        public AgentMetadata withStatusAndHealth(AgentStatus newStatus, double newHealth, long newHeartbeat) {
            return new AgentMetadata(
                    agentId, name, endpoint, capabilities, embeddingVector, newHealth, newStatus, newHeartbeat
            );
        }
    }

    private final Map<String, AgentMetadata> agentStore = new ConcurrentHashMap<>();

    /**
     * 注册智能体节点
     */
    public void register(AgentMetadata metadata) {
        Objects.requireNonNull(metadata, "智能体元数据不能为空");
        Objects.requireNonNull(metadata.agentId(), "agentId 不能为空");
        agentStore.put(metadata.agentId(), metadata);
    }

    /**
     * 注销智能体节点
     */
    public void deregister(String agentId) {
        agentStore.remove(agentId);
    }

    /**
     * 心跳上报与健康评分刷新
     */
    public boolean heartbeat(String agentId, double currentHealthScore) {
        AgentMetadata existing = agentStore.get(agentId);
        if (existing == null) {
            return false;
        }
        AgentStatus newStatus = currentHealthScore >= 0.5 ? AgentStatus.ONLINE : AgentStatus.DEGRADED;
        AgentMetadata updated = existing.withStatusAndHealth(newStatus, currentHealthScore, System.currentTimeMillis());
        agentStore.put(agentId, updated);
        return true;
    }

    /**
     * 获取单个智能体
     */
    public Optional<AgentMetadata> getAgent(String agentId) {
        return Optional.ofNullable(agentStore.get(agentId));
    }

    /**
     * 获取所有健康可用智能体（排除 DEAD 与超时节点）
     */
    public List<AgentMetadata> getHealthyAgents() {
        long now = System.currentTimeMillis();
        return agentStore.values().stream()
                .filter(a -> a.status() != AgentStatus.DEAD)
                .filter(a -> (now - a.lastHeartbeatTime()) <= 15000L) // 15秒内有心跳
                .collect(Collectors.toList());
    }

    /**
     * 周期性检查并剔除/软隔离假死节点
     */
    public int checkAndEvictStaleNodes(long timeoutMs) {
        long now = System.currentTimeMillis();
        int evictedCount = 0;
        for (Map.Entry<String, AgentMetadata> entry : agentStore.entrySet()) {
            AgentMetadata node = entry.getValue();
            if ((now - node.lastHeartbeatTime()) > timeoutMs && node.status() != AgentStatus.DEAD) {
                AgentMetadata deadNode = node.withStatusAndHealth(AgentStatus.DEAD, 0.0, node.lastHeartbeatTime());
                agentStore.put(entry.getKey(), deadNode);
                evictedCount++;
            }
        }
        return evictedCount;
    }

    public int totalRegisteredCount() {
        return agentStore.size();
    }

    public void clear() {
        agentStore.clear();
    }
}
