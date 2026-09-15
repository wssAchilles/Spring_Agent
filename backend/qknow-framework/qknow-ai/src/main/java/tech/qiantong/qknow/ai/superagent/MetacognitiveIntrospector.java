package tech.qiantong.qknow.ai.superagent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 60: 自省认知元框架
 * <p>
 * 基于定理 1.1，量化多智能体集群认知熵 H_cluster，监控自激振荡与环形调用死锁。
 */
public class MetacognitiveIntrospector {

    public static final double CRITICAL_ENTROPY_THRESHOLD = 2.0;
    public static final int OSCILLATION_CYCLE_LIMIT = 3;

    // 记录智能体信念分布
    private final Map<String, Map<String, Double>> agentBeliefs = new ConcurrentHashMap<>();
    // 记录智能体调用有向图边及频率
    private final Map<String, List<String>> callChains = new ConcurrentHashMap<>();
    private final Map<String, Integer> loopInvocationCounters = new ConcurrentHashMap<>();

    /**
     * 记录智能体信念概率分布
     */
    public void recordAgentBelief(String agentId, Map<String, Double> distribution) {
        if (agentId == null || distribution == null || distribution.isEmpty()) {
            return;
        }
        agentBeliefs.put(agentId, new HashMap<>(distribution));
    }

    /**
     * 记录调用边并检测调用环
     */
    public void recordCall(String callerAgentId, String calleeAgentId) {
        if (callerAgentId == null || calleeAgentId == null) {
            return;
        }
        callChains.computeIfAbsent(callerAgentId, k -> Collections.synchronizedList(new ArrayList<>())).add(calleeAgentId);

        // 跟踪二元环或自激调用
        String pairKey = callerAgentId + "->" + calleeAgentId;
        loopInvocationCounters.merge(pairKey, 1, Integer::sum);
    }

    /**
     * 计算集群香农认知熵：H_cluster = - \sum p_i * ln(p_i)
     */
    public double calculateClusterCognitiveEntropy() {
        if (agentBeliefs.isEmpty()) {
            return 0.0;
        }

        // 聚合所有状态的全局概率分布
        Map<String, Double> aggregatedProb = new HashMap<>();
        double totalMass = 0.0;

        for (Map<String, Double> dist : agentBeliefs.values()) {
            for (Map.Entry<String, Double> entry : dist.entrySet()) {
                double p = Math.max(0.0, entry.getValue());
                aggregatedProb.merge(entry.getKey(), p, Double::sum);
                totalMass += p;
            }
        }

        if (totalMass <= 1e-9) {
            return 0.0;
        }

        double entropy = 0.0;
        for (double mass : aggregatedProb.values()) {
            double prob = mass / totalMass;
            if (prob > 1e-9) {
                entropy -= prob * Math.log(prob);
            }
        }

        return Math.max(0.0, entropy);
    }

    /**
     * 是否超过认知熵临界警戒线
     */
    public boolean isEntropyCritical() {
        return calculateClusterCognitiveEntropy() >= CRITICAL_ENTROPY_THRESHOLD;
    }

    /**
     * 检测多智能体网络是否存在自激振荡或死锁环路
     */
    public boolean detectOscillationLoop() {
        // 1. 检查调用对频次
        for (int count : loopInvocationCounters.values()) {
            if (count >= OSCILLATION_CYCLE_LIMIT) {
                return true;
            }
        }

        // 2. 检查强连通环路（DFS 检测环路深度 >= 2）
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();

        for (String node : callChains.keySet()) {
            if (hasCycle(node, visited, recStack)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCycle(String curr, Set<String> visited, Set<String> recStack) {
        if (recStack.contains(curr)) {
            return true;
        }
        if (visited.contains(curr)) {
            return false;
        }

        visited.add(curr);
        recStack.add(curr);

        List<String> neighbors = callChains.get(curr);
        if (neighbors != null) {
            for (String next : neighbors) {
                if (hasCycle(next, visited, recStack)) {
                    return true;
                }
            }
        }

        recStack.remove(curr);
        return false;
    }

    /**
     * 重置自省上下文
     */
    public void reset() {
        agentBeliefs.clear();
        callChains.clear();
        loopInvocationCounters.clear();
    }
}
