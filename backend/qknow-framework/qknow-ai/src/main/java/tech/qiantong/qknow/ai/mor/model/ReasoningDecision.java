package tech.qiantong.qknow.ai.mor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import tech.qiantong.qknow.ai.deepseek.DeepSeekChatOptions;

/**
 * 自适应思考调控仲裁决策结果载体 (Java 21 不可变 Record)
 * 纯单一主干 deepseek-flash 参数化思考调控，彻底弃用双物理模型路由
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReasoningDecision(
        RoutingBranch branch,
        double compositeScore,       // 综合得分 Φ(Q)
        double semanticComplexity,   // 语义复杂度 C_semantic
        double contextConfidence,    // 上下文置信度 Conf_rag
        double conflictFactor,       // 冲突度 Δ_conflict
        String decisionScaffold,     // 命中的认知脚手架内容 (若未命中则为 null)
        String routingReason,        // 选路原因决策摘要
        String reasoningEffort       // 思考深度级别: null / "low" / "medium" / "high"
) {
    public enum RoutingBranch {
        FAST_FLASH,           // deepseek-flash 毫秒级直出 (thinking: disabled)
        FLASH_WITH_SCAFFOLD,  // deepseek-flash 挂载认知脚手架极速复用 (thinking: disabled)
        DEEP_THINKING;        // deepseek-flash 激活长思考链推演 (thinking: enabled)

        // 历史兼容别名 (防止旧反射或代码中断)
        public static final RoutingBranch FAST_V3 = FAST_FLASH;
        public static final RoutingBranch V3_WITH_SCAFFOLD = FLASH_WITH_SCAFFOLD;
        public static final RoutingBranch DEEP_R1 = DEEP_THINKING;
    }

    public ReasoningDecision(
            RoutingBranch branch,
            double compositeScore,
            double semanticComplexity,
            double contextConfidence,
            double conflictFactor,
            String decisionScaffold,
            String routingReason
    ) {
        this(branch, compositeScore, semanticComplexity, contextConfidence, conflictFactor, decisionScaffold, routingReason, null);
    }

    public ReasoningDecision {
        if (routingReason == null || routingReason.isBlank()) {
            routingReason = "Auto-evaluated by Adaptive Thinking Governor";
        }
    }

    /**
     * 将仲裁结果快速转换为针对 deepseek-flash 的请求参数对象
     */
    public DeepSeekChatOptions toChatOptions() {
        DeepSeekChatOptions options = new DeepSeekChatOptions();
        options.setModel("deepseek-flash");
        if (branch == RoutingBranch.DEEP_THINKING) {
            options.setThinkingEnabled(true);
            options.setReasoningEffort(reasoningEffort != null ? reasoningEffort : "medium");
        } else {
            options.setThinkingEnabled(false);
            options.setReasoningEffort(null);
        }
        return options;
    }
}
