package tech.qiantong.qknow.ai.mor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 双核混合推理判定决策载体 (Java 21 不可变 Record)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReasoningDecision(
        RoutingBranch branch,
        double compositeScore,       // 综合得分 Φ(Q)
        double semanticComplexity,   // 语义复杂度 C_semantic
        double contextConfidence,    // 上下文置信度 Conf_rag
        double conflictFactor,       // 冲突度 Δ_conflict
        String decisionScaffold,     // 命中的认知脚手架内容 (若未命中则为 null)
        String routingReason         // 选路原因决策摘要
) {
    public enum RoutingBranch {
        FAST_V3,            // DeepSeek-V3 毫秒级直出
        V3_WITH_SCAFFOLD,   // DeepSeek-V3 挂载认知脚手架极速复用
        DEEP_R1             // DeepSeek-R1 深度推演
    }

    public ReasoningDecision {
        if (routingReason == null || routingReason.isBlank()) {
            routingReason = "Auto-evaluated by MoR Governor";
        }
    }
}
