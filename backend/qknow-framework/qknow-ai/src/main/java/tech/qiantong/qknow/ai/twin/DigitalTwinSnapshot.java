package tech.qiantong.qknow.ai.twin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 智能体数字孪生不可变快照 (Java 21 Record, 引理 1.4: 不可变结构共享 COW 内存有界)
 */
public record DigitalTwinSnapshot(
        long snapshotVersion,
        long timestampMs,
        Map<String, AgentTwinState> agentStates,
        Map<String, Double> systemMetrics,
        String snapshotHash
) {

    /**
     * 单个智能体在虚拟孪生空间中的状态镜像
     */
    public record AgentTwinState(
            String agentId,
            String role,
            String status,
            double reputationScore,
            double loadFactor,
            Map<String, Object> attributes
    ) {
        public AgentTwinState withLoadAndReputation(double newLoad, double newReputation) {
            return new AgentTwinState(agentId, role, status, newReputation, newLoad, attributes);
        }

        public AgentTwinState withAttributes(Map<String, Object> newAttrs) {
            Map<String, Object> merged = new HashMap<>(attributes != null ? attributes : Map.of());
            if (newAttrs != null) {
                merged.putAll(newAttrs);
            }
            return new AgentTwinState(agentId, role, status, reputationScore, loadFactor, Collections.unmodifiableMap(merged));
        }
    }

    /**
     * 创建初始快照工厂方法
     */
    public static DigitalTwinSnapshot initialSnapshot(
            Map<String, AgentTwinState> initialAgents,
            Map<String, Double> initialMetrics
    ) {
        Map<String, AgentTwinState> agents = Collections.unmodifiableMap(new HashMap<>(initialAgents != null ? initialAgents : Map.of()));
        Map<String, Double> metrics = Collections.unmodifiableMap(new HashMap<>(initialMetrics != null ? initialMetrics : Map.of()));
        long now = System.currentTimeMillis();
        String hash = computeHash(1L, now, agents, metrics);
        return new DigitalTwinSnapshot(1L, now, agents, metrics, hash);
    }

    /**
     * 写时复制 (COW) 结构共享干预产生新快照 (定理 1.1: 物理隔离不变量)
     */
    public DigitalTwinSnapshot withIntervention(
            String targetAgentId,
            String newStatus,
            Map<String, Object> deltaAttrs
    ) {
        Map<String, AgentTwinState> newAgents = new HashMap<>(this.agentStates);
        AgentTwinState current = newAgents.get(targetAgentId);
        if (current != null) {
            Map<String, Object> updatedAttrs = new HashMap<>(current.attributes() != null ? current.attributes() : Map.of());
            if (deltaAttrs != null) {
                updatedAttrs.putAll(deltaAttrs);
            }
            AgentTwinState mutated = new AgentTwinState(
                    current.agentId(),
                    current.role(),
                    newStatus != null ? newStatus : current.status(),
                    current.reputationScore(),
                    current.loadFactor(),
                    Collections.unmodifiableMap(updatedAttrs)
            );
            newAgents.put(targetAgentId, mutated);
        } else if (targetAgentId != null) {
            AgentTwinState created = new AgentTwinState(
                    targetAgentId,
                    "VIRTUAL_AGENT",
                    newStatus != null ? newStatus : "ACTIVE",
                    1.0,
                    0.0,
                    deltaAttrs != null ? Collections.unmodifiableMap(new HashMap<>(deltaAttrs)) : Map.of()
            );
            newAgents.put(targetAgentId, created);
        }

        long nextVersion = this.snapshotVersion + 1;
        long now = System.currentTimeMillis();
        Map<String, Double> newMetrics = new HashMap<>(this.systemMetrics);
        String nextHash = computeHash(nextVersion, now, newAgents, newMetrics);

        return new DigitalTwinSnapshot(
                nextVersion,
                now,
                Collections.unmodifiableMap(newAgents),
                Collections.unmodifiableMap(newMetrics),
                nextHash
        );
    }

    /**
     * 安全获取智能体状态
     */
    public Optional<AgentTwinState> getAgent(String agentId) {
        return Optional.ofNullable(agentStates.get(agentId));
    }

    /**
     * 计算快照密码学 SHA-256 指纹
     */
    private static String computeHash(
            long version,
            long timestamp,
            Map<String, AgentTwinState> agents,
            Map<String, Double> metrics
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = version + ":" + timestamp + ":" + agents.size() + ":" + metrics.hashCode();
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "HASH_FALLBACK_" + version + "_" + timestamp;
        }
    }
}
