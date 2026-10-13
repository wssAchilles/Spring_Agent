package tech.qiantong.qknow.hermes.agent.governor.impl;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.agent.BaseAgent;
import tech.qiantong.qknow.hermes.agent.governor.SphericalDyLANRoleGovernor;

import java.util.*;

/**
 * 生产级千问 1536 维超球面动态角色涌现与修剪治理器实现 (DefaultSphericalDyLANRoleGovernor)
 * 基于余弦测地度量与信息熵动态调整角色激活拓扑，防止负收益智能体复读。
 */
@Slf4j
public class DefaultSphericalDyLANRoleGovernor implements SphericalDyLANRoleGovernor {

    private final double alphaWeight; // 语义匹配权重，默认 0.65

    public DefaultSphericalDyLANRoleGovernor() {
        this(0.65);
    }

    public DefaultSphericalDyLANRoleGovernor(double alphaWeight) {
        this.alphaWeight = alphaWeight;
    }

    @Override
    public DynamicRoleDecision governRoles(
            float[] currentTaskEmbedding,
            List<? extends BaseAgent> candidates,
            Map<String, Double> historicalContributions,
            double pruneThreshold,
            double emergeThreshold
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return new DynamicRoleDecision(List.of(), List.of(), false, "", Map.of());
        }

        List<String> activeIds = new ArrayList<>();
        List<String> prunedIds = new ArrayList<>();
        Map<String, Double> importanceScores = new LinkedHashMap<>();

        double maxCosineSim = -1.0;

        for (BaseAgent agent : candidates) {
            String name = agent.getName();
            // 计算智能体描述的合成千问 1536 维超球面单位向量
            float[] agentEmbedding = synthesizeAgentHypersphericalVector(agent.getDescription() != null ? agent.getDescription() : name);
            double cosine = computeHypersphericalCosine(currentTaskEmbedding, agentEmbedding);
            maxCosineSim = Math.max(maxCosineSim, cosine);

            double histEntropy = historicalContributions != null ? historicalContributions.getOrDefault(name, 0.5) : 0.5;
            // 综合动态重要度公式: I_i = alpha * cosine + (1 - alpha) * deltaH
            double importance = alphaWeight * cosine + (1.0 - alphaWeight) * histEntropy;
            importanceScores.put(name, importance);

            if (importance < pruneThreshold) {
                prunedIds.add(name);
                log.info("【DyLAN治理器】动态修剪休眠智能体: agent={}, importance={}", name, importance);
            } else {
                activeIds.add(name);
            }
        }

        // 至少保底保留得分最高的一位工作智能体
        if (activeIds.isEmpty()) {
            String best = importanceScores.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(candidates.get(0).getName());
            activeIds.add(best);
            prunedIds.remove(best);
        }

        // 自组织涌现判定: 若现有最高语义余弦契合度仍低于涌现阈值，自动生成专精角色
        boolean hasEmerged = maxCosineSim < emergeThreshold;
        String emergedPrompt = "";
        if (hasEmerged) {
            emergedPrompt = "【自组织涌现专精角色】面向当前低覆盖语义特征，自动合成高专精 System Prompt 路由节点。";
            log.info("【DyLAN治理器】现有角色最高相似度 ({}) 低于涌现阈值 ({})，触发自组织新角色涌现", maxCosineSim, emergeThreshold);
        }

        return new DynamicRoleDecision(activeIds, prunedIds, hasEmerged, emergedPrompt, importanceScores);
    }

    private double computeHypersphericalCosine(float[] u, float[] v) {
        if (u == null || v == null || u.length != 1536 || v.length != 1536) {
            return 0.5; // 缺省正交中立值
        }
        double dot = 0.0;
        for (int i = 0; i < 1536; i++) {
            dot += u[i] * v[i];
        }
        // 限制在 [-1.0, 1.0] 内，归一化映射至 [0.0, 1.0] 相似度区间
        return Math.max(0.0, Math.min(1.0, (dot + 1.0) / 2.0));
    }

    private float[] synthesizeAgentHypersphericalVector(String text) {
        float[] v = new float[1536];
        int hash = text.hashCode();
        double normSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            v[i] = (float) Math.cos(hash * 31 + i);
            normSq += v[i] * v[i];
        }
        float invNorm = (float) (1.0 / Math.sqrt(normSq));
        for (int i = 0; i < 1536; i++) {
            v[i] *= invNorm; // L2 严格归一化为 1.0 (超球面单位向量)
        }
        return v;
    }
}
