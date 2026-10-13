package tech.qiantong.qknow.hermes.agent.governor.impl;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.agent.governor.ShapleyCreditAllocator;
import tech.qiantong.qknow.hermes.agent.governor.dto.ShapleyCreditAllocationReceipt;

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * 生产级蒙特卡洛抽样夏普利边际价值分配器实现 (MonteCarloShapleyCreditAllocator)
 * 采用多项式时间随机排列截断近似算法，公理化度量多智能体协作链中的边际收益贡献。
 */
@Slf4j
public class MonteCarloShapleyCreditAllocator implements ShapleyCreditAllocator {

    private final int defaultPermutations;

    public MonteCarloShapleyCreditAllocator() {
        this(25);
    }

    public MonteCarloShapleyCreditAllocator(int defaultPermutations) {
        this.defaultPermutations = Math.max(5, Math.min(100, defaultPermutations));
    }

    @Override
    public ShapleyCreditAllocationReceipt allocateCredits(
            String sessionId,
            List<String> agentIds,
            ToDoubleFunction<List<String>> characteristicFunction,
            int monteCarloPermutations
    ) {
        Objects.requireNonNull(sessionId, "sessionId 不可为空");
        Objects.requireNonNull(characteristicFunction, "characteristicFunction 不可为空");

        if (agentIds == null || agentIds.isEmpty()) {
            return ShapleyCreditAllocationReceipt.createSigned(
                    "shapley-rcpt-" + UUID.randomUUID().toString().substring(0, 8),
                    sessionId, 0, 0, 0.0, Map.of(), Map.of()
            );
        }

        List<String> uniqueAgents = new ArrayList<>(new LinkedHashSet<>(agentIds));
        int n = uniqueAgents.size();
        int M = monteCarloPermutations > 0 ? monteCarloPermutations : defaultPermutations;

        // 计算大全联盟收益 v(N) 与空集收益 v(Ø)
        double grandCoalitionValue = characteristicFunction.applyAsDouble(uniqueAgents);
        double emptyValue = characteristicFunction.applyAsDouble(List.of());

        // 累积各智能体的边际贡献
        Map<String, Double> marginalSums = new LinkedHashMap<>();
        uniqueAgents.forEach(id -> marginalSums.put(id, 0.0));

        Random random = new Random(sessionId.hashCode() ^ 20261013L);

        for (int m = 0; m < M; m++) {
            List<String> permutation = new ArrayList<>(uniqueAgents);
            Collections.shuffle(permutation, random);

            List<String> currentSubset = new ArrayList<>();
            double previousValue = emptyValue;

            for (String agentId : permutation) {
                currentSubset.add(agentId);
                double subsetValue = characteristicFunction.applyAsDouble(currentSubset);
                double marginal = subsetValue - previousValue;

                marginalSums.put(agentId, marginalSums.get(agentId) + marginal);
                previousValue = subsetValue;
            }
        }

        // 计算平均夏普利值
        Map<String, Double> shapleyValues = new LinkedHashMap<>();
        double totalShapley = 0.0;
        for (String agentId : uniqueAgents) {
            double phi = marginalSums.get(agentId) / M;
            shapleyValues.put(agentId, phi);
            totalShapley += Math.max(0.0, phi);
        }

        // 归一化分配比例 (Normalized Credit Weights)
        Map<String, Double> normalizedCredits = new LinkedHashMap<>();
        if (totalShapley > 1e-9) {
            for (String agentId : uniqueAgents) {
                double raw = Math.max(0.0, shapleyValues.get(agentId));
                normalizedCredits.put(agentId, raw / totalShapley);
            }
        } else {
            // 平权均分兜底
            double uniform = 1.0 / n;
            uniqueAgents.forEach(id -> normalizedCredits.put(id, uniform));
        }

        log.info("【夏普利分配器】会话 {} 完成公理化分配: agents={}, M={}, grandVal={}",
                sessionId, n, M, grandCoalitionValue);

        return ShapleyCreditAllocationReceipt.createSigned(
                "shapley-rcpt-" + UUID.randomUUID().toString().substring(0, 8),
                sessionId,
                n,
                M,
                grandCoalitionValue,
                shapleyValues,
                normalizedCredits
        );
    }
}
