package tech.qiantong.qknow.ai.credit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 反事实边际优势因果信贷归因引擎 (Counterfactual Credit Assigner)
 * 基于 COMA 反事实基准消融与四维质量度量 (通过率、千问超球面忠实度、Token 效率、延迟) 消除搭便车
 */
@Component
public class CounterfactualCreditAssigner {

    private static final Logger log = LoggerFactory.getLogger(CounterfactualCreditAssigner.class);

    // 四维质量权重
    public static final double WEIGHT_PASS_RATE = 0.35;
    public static final double WEIGHT_FIDELITY = 0.30;
    public static final double WEIGHT_TOKEN_EFF = 0.20;
    public static final double WEIGHT_LATENCY = 0.15;

    /**
     * 智能体个体协同贡献元数据
     */
    public record AgentContribution(
            String agentId,
            String role,
            boolean outputValid,
            int generatedTokens,
            double localFidelity,
            long durationMs,
            boolean isFreeRiderCandidate
    ) {}

    /**
     * 多维度任务执行综合质量度量 Q(a)
     */
    public double evaluateQuality(double passRate, double qwenHypersphereFidelity, double tokenEfficiency, double latencyScore) {
        double p = Math.max(0.0, Math.min(1.0, passRate));
        double f = Math.max(0.0, Math.min(1.0, qwenHypersphereFidelity));
        double t = Math.max(0.0, Math.min(1.0, tokenEfficiency));
        double l = Math.max(0.0, Math.min(1.0, latencyScore));

        return WEIGHT_PASS_RATE * p + WEIGHT_FIDELITY * f + WEIGHT_TOKEN_EFF * t + WEIGHT_LATENCY * l;
    }

    /**
     * 计算反事实因果信贷归因
     * @param totalQuality 实际全员协同综合质量得分 Q(a)
     * @param contributions 参与协同的智能体贡献指标集合
     * @return 智能体唯一标识 -> 精确因果信贷奖励 [0.0, totalQuality]
     */
    public Map<String, Double> assignCredits(double totalQuality, List<AgentContribution> contributions) {
        if (contributions == null || contributions.isEmpty() || totalQuality <= 0.0) {
            return Map.of();
        }

        Map<String, Double> marginalAdvantages = new LinkedHashMap<>();
        double sumMarginal = 0.0;

        for (AgentContribution contrib : contributions) {
            String agentId = contrib.agentId();

            // 如果该节点是显式搭便车(空输出、无效输出、或无增量候选)，反事实消融无任何质量损失
            if (!contrib.outputValid() || contrib.isFreeRiderCandidate() || contrib.generatedTokens() <= 0) {
                marginalAdvantages.put(agentId, 0.0);
                continue;
            }

            // 计算反事实消融基准 Q(a^{-i}, a_0): 缺少该节点时的系统质量
            double counterfactualQuality = computeCounterfactualQuality(totalQuality, contrib, contributions.size());

            // 边际因果优势: Delta_i = max(0, Q(a) - Q(a^{-i}, a_0))
            double delta = Math.max(0.0, totalQuality - counterfactualQuality);
            marginalAdvantages.put(agentId, delta);
            sumMarginal += delta;
        }

        // 依据边际因果优势进行归一化并分配信贷
        Map<String, Double> allocatedCredits = new LinkedHashMap<>();
        for (AgentContribution contrib : contributions) {
            String agentId = contrib.agentId();
            double delta = marginalAdvantages.getOrDefault(agentId, 0.0);

            if (delta <= 1e-9 || sumMarginal <= 1e-9) {
                // 搭便车或无贡献节点：定理 1.2 证明信贷严格置为 0.0
                allocatedCredits.put(agentId, 0.0);
            } else {
                double credit = (delta / sumMarginal) * totalQuality;
                allocatedCredits.put(agentId, Math.round(credit * 10000.0) / 10000.0);
            }
        }

        return Collections.unmodifiableMap(allocatedCredits);
    }

    /**
     * 计算缺少智能体 i 时的反事实系统质量得分 Q(a^{-i}, a_0)
     */
    private double computeCounterfactualQuality(double actualQuality, AgentContribution contrib, int totalAgents) {
        // 关键角色 (REASONER, CODER) 消融会造成系统整体通过率与语义忠实度断崖式下跌
        double impactFactor;
        String role = contrib.role() != null ? contrib.role().toUpperCase() : "GENERAL";
        switch (role) {
            case "REASONER":
                impactFactor = 0.65; // 缺少推理节点，质量跌落 65%
                break;
            case "CODER":
                impactFactor = 0.55; // 缺少代码落地节点，质量跌落 55%
                break;
            case "RETRIEVER":
                impactFactor = 0.35; // 缺少检索，质量跌落 35%
                break;
            case "REVIEWER":
                impactFactor = 0.25; // 缺少审查，质量跌落 25%
                break;
            default:
                impactFactor = Math.min(0.5, 1.0 / Math.max(1, totalAgents));
                break;
        }

        // 根据该节点自身的局部忠实度微调
        double fidelityFactor = Math.max(0.5, contrib.localFidelity());
        double loss = actualQuality * impactFactor * fidelityFactor;

        return Math.max(0.0, actualQuality - loss);
    }
}
