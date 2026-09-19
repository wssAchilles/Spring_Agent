package tech.qiantong.qknow.ai.mor;

import org.springframework.stereotype.Service;
import tech.qiantong.qknow.ai.deepseek.DeepSeekChatOptions;
import tech.qiantong.qknow.ai.mor.model.ReasoningDecision;

import java.util.List;

/**
 * 自适应思考调控统一门面 (Adaptive Thinking Governor Facade)
 * 面向 Spring AI 与编排引擎提供极简的调控与 Options 转换契约
 */
@Service
public class AdaptiveThinkingGovernor {

    private final MixtureOfReasoningGovernor underlyingGovernor;

    public AdaptiveThinkingGovernor(MixtureOfReasoningGovernor underlyingGovernor) {
        this.underlyingGovernor = underlyingGovernor;
    }

    /**
     * 评估自适应思考选路
     */
    public ReasoningDecision evaluate(
            String userQuery,
            float[] queryEmbedding1536,
            List<String> recalledSliceSignatures,
            double meanRecallScore,
            boolean hasTemporalConflict,
            boolean isCragAmbiguous
    ) {
        return underlyingGovernor.evaluateRoute(
                userQuery,
                queryEmbedding1536,
                recalledSliceSignatures,
                meanRecallScore,
                hasTemporalConflict,
                isCragAmbiguous
        );
    }

    /**
     * 快速将决策转换为 DeepSeekChatOptions
     */
    public DeepSeekChatOptions toChatOptions(ReasoningDecision decision) {
        if (decision == null) {
            DeepSeekChatOptions fallback = new DeepSeekChatOptions();
            fallback.setModel("deepseek-flash");
            fallback.setThinkingEnabled(false);
            return fallback;
        }
        return decision.toChatOptions();
    }
}
