package tech.qiantong.qknow.ai.gateway.router;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.gateway.model.ChannelStatus;
import tech.qiantong.qknow.ai.gateway.model.ProviderChannel;
import tech.qiantong.qknow.ai.gateway.model.RoutingScenario;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Phase 28: SLA 延迟感知与动态成本-时延双目标 Pareto 最优选路器
 * 结合实时 TTFT / P99 延迟分布与单位 Token 成本，通过 Softmax 负熵正则化防止羊群振荡 (Theorem 2.1)
 */
@Slf4j
@Component
public class LatencyAwareSlaRouter {

    private final Map<String, ChannelLatencyTracker> latencyTrackers = new ConcurrentHashMap<>();

    public ChannelLatencyTracker getOrCreateTracker(String channelId) {
        return latencyTrackers.computeIfAbsent(channelId, k -> new ChannelLatencyTracker());
    }

    /**
     * 在一组候选可用通道中，依据业务场景选择 Pareto 最优通道
     *
     * @param candidates 候选通道集合
     * @param scenario 业务场景 (交互式对话 / 推理 / 离线批处理)
     * @return 最优被选中的通道
     */
    public ProviderChannel selectOptimalChannel(List<ProviderChannel> candidates, RoutingScenario scenario) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("无任何可用的大模型上游候选通道");
        }
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }

        RoutingScenario effectiveScenario = scenario != null ? scenario : RoutingScenario.INTERACTIVE_CHAT;

        // 1. 过滤健康可用的通道 (HEALTHY 或 DEGRADED)
        List<ProviderChannel> healthyList = candidates.stream()
                .filter(c -> c.getStatus() == ChannelStatus.HEALTHY || c.getStatus() == ChannelStatus.DEGRADED)
                .sorted(Comparator.comparingInt(ProviderChannel::getPriority))
                .toList();

        if (healthyList.isEmpty()) {
            // 所有通道均非绝对健康，按优先级选取最高者强制降级尝试
            return candidates.stream()
                    .min(Comparator.comparingInt(ProviderChannel::getPriority))
                    .orElse(candidates.getFirst());
        }

        // 2. 找到最高优先级 (例如优先考虑 Priority 0 主通道)
        int minPriority = healthyList.getFirst().getPriority();
        List<ProviderChannel> samePriorityCandidates = healthyList.stream()
                .filter(c -> c.getPriority() == minPriority)
                .toList();

        if (samePriorityCandidates.size() == 1) {
            return samePriorityCandidates.getFirst();
        }

        // 3. 同优先级下计算综合成本评分 Psi = w_lat * (TTFT/1000) + w_cost * (Cost/20)
        double[] scores = new double[samePriorityCandidates.size()];
        double minScore = Double.MAX_VALUE;
        int bestIdx = 0;

        for (int i = 0; i < samePriorityCandidates.size(); i++) {
            ProviderChannel ch = samePriorityCandidates.get(i);
            ChannelLatencyTracker tracker = getOrCreateTracker(ch.getChannelId());
            ChannelLatencyTracker.LatencySnapshot snapshot = tracker.getSnapshot();

            double latencyMetric = snapshot.getAvgTtftMillis() / 1000.0;
            double costMetric = ch.getCostPerMillionTokens() / 20.0;
            double degradationPenalty = ch.getStatus() == ChannelStatus.DEGRADED ? 1.5 : 0.0;

            double psi = effectiveScenario.getLatencyWeight() * latencyMetric
                    + effectiveScenario.getCostWeight() * costMetric
                    + effectiveScenario.getErrorPenaltyWeight() * degradationPenalty;

            scores[i] = psi;
            if (psi < minScore) {
                minScore = psi;
                bestIdx = i;
            }
        }

        // 4. 引入基于 Softmax 的轻量概率阻尼 (温度 tau = 0.20)，兼顾探索与最优性收敛，避免羊群踩踏 (Theorem 2.1)
        double tau = 0.20;
        double expSum = 0.0;
        double[] probabilities = new double[scores.length];
        for (int i = 0; i < scores.length; i++) {
            // exp(-(score - minScore)/tau)
            probabilities[i] = Math.exp(-(scores[i] - minScore) / tau);
            expSum += probabilities[i];
        }

        double rand = ThreadLocalRandom.current().nextDouble() * expSum;
        double runningSum = 0.0;
        for (int i = 0; i < probabilities.length; i++) {
            runningSum += probabilities[i];
            if (rand <= runningSum) {
                return samePriorityCandidates.get(i);
            }
        }

        return samePriorityCandidates.get(bestIdx);
    }
}
