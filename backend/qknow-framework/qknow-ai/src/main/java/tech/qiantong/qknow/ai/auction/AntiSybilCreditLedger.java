package tech.qiantong.qknow.ai.auction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抗女巫攻击信用图谱与账本 (定理 1.3: 拓扑瓶颈传导阻抗过滤)
 */
@Component
public class AntiSybilCreditLedger {

    private static final Logger log = LoggerFactory.getLogger(AntiSybilCreditLedger.class);

    public record AgentCreditProfile(
            String agentId,
            double reputationScore,
            boolean isQuarantined,
            int completedTasks,
            int timeoutFailures,
            long lastActiveMs
    ) {
        public AgentCreditProfile withDelta(double repDelta, boolean success) {
            double nextRep = Math.max(0.0, Math.min(1.0, this.reputationScore + repDelta));
            return new AgentCreditProfile(
                    agentId,
                    nextRep,
                    nextRep < 0.40,
                    success ? completedTasks + 1 : completedTasks,
                    success ? timeoutFailures : timeoutFailures + 1,
                    System.currentTimeMillis()
            );
        }

        public AgentCreditProfile withQuarantine(boolean quarantined) {
            return new AgentCreditProfile(agentId, reputationScore, quarantined, completedTasks, timeoutFailures, System.currentTimeMillis());
        }
    }

    private final Set<String> trustedSeeds = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Set<String>> trustGraph = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentCreditProfile> creditProfiles = new ConcurrentHashMap<>();

    public AntiSybilCreditLedger() {
        // 注册系统级不可伪造绝对可信种子节点
        trustedSeeds.add("agent_coordinator");
        trustedSeeds.add("agent_retriever");
        trustedSeeds.add("agent_reasoner");

        for (String seed : trustedSeeds) {
            creditProfiles.put(seed, new AgentCreditProfile(seed, 0.99, false, 100, 0, System.currentTimeMillis()));
            trustGraph.put(seed, ConcurrentHashMap.newKeySet());
        }
    }

    public void registerTrustedSeed(String agentId) {
        trustedSeeds.add(agentId);
        creditProfiles.putIfAbsent(agentId, new AgentCreditProfile(agentId, 0.95, false, 50, 0, System.currentTimeMillis()));
    }

    public void addTrustEdge(String source, String target) {
        trustGraph.computeIfAbsent(source, k -> ConcurrentHashMap.newKeySet()).add(target);
        trustGraph.computeIfAbsent(target, k -> ConcurrentHashMap.newKeySet()).add(source);
    }

    /**
     * 判断是否属于女巫伪造身份 (定理 1.3: 传导阻抗低于门限且未与可信种子连通)
     */
    public boolean isSybil(String agentId) {
        if (trustedSeeds.contains(agentId)) {
            return false;
        }

        AgentCreditProfile profile = creditProfiles.get(agentId);
        if (profile != null && profile.isQuarantined()) {
            return true;
        }

        // BFS 检查从可信种子到达该节点的距离与割边连通性
        boolean connectedToSeed = false;
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        for (String seed : trustedSeeds) {
            queue.offer(seed);
            visited.add(seed);
        }

        int maxDepth = 4;
        int depth = 0;
        while (!queue.isEmpty() && depth < maxDepth) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                String curr = queue.poll();
                if (curr.equals(agentId)) {
                    connectedToSeed = true;
                    break;
                }
                Set<String> neighbors = trustGraph.getOrDefault(curr, Collections.emptySet());
                for (String nb : neighbors) {
                    if (visited.add(nb)) {
                        queue.offer(nb);
                    }
                }
            }
            if (connectedToSeed) break;
            depth++;
        }

        // 若无法从种子连通，或信誉过低，判定为女巫节点
        if (!connectedToSeed) {
            return true;
        }

        return profile != null && profile.reputationScore() < 0.50;
    }

    public void recordExecutionSuccess(String agentId, double reputationGain) {
        creditProfiles.compute(agentId, (k, v) -> {
            if (v == null) {
                return new AgentCreditProfile(agentId, Math.min(1.0, 0.70 + reputationGain), false, 1, 0, System.currentTimeMillis());
            }
            return v.withDelta(reputationGain, true);
        });
    }

    public void recordExecutionTimeout(String agentId) {
        creditProfiles.compute(agentId, (k, v) -> {
            if (v == null) {
                return new AgentCreditProfile(agentId, 0.35, true, 0, 1, System.currentTimeMillis());
            }
            return v.withDelta(-0.25, false);
        });
        log.warn("Agent {} penalised for execution timeout, profile updated.", agentId);
    }

    public Optional<AgentCreditProfile> getProfile(String agentId) {
        return Optional.ofNullable(creditProfiles.get(agentId));
    }
}
