package tech.qiantong.qknow.hermes.intent.dto;

import java.util.List;

/**
 * 意图歧义反思探测结果 Java 21 Record
 * 封装条件信息熵、测地角裕度、竞争意图、缺失必填槽位与高危动作拦截标记
 */
public record AmbiguityDetectionResult(
        boolean isAmbiguous,
        double conditionalEntropy,
        double geodesicMargin,
        List<String> competingIntents,
        List<String> missingRequiredSlots,
        boolean isDestructiveAction,
        String clarificationPrompt
) {
    /**
     * 严格安全判定：若存在歧义，或属于高危破坏动作且参数不完整，必须强制拦截执行
     */
    public boolean shouldInterceptExecution() {
        if (isAmbiguous) {
            return true;
        }
        if (isDestructiveAction && missingRequiredSlots != null && !missingRequiredSlots.isEmpty()) {
            return true;
        }
        return false;
    }

    // 便捷 Accessor 适配契约测试
    public boolean hasAmbiguity() {
        return isAmbiguous;
    }

    public boolean requiresClarification() {
        return shouldInterceptExecution();
    }

    public boolean highRiskAction() {
        return isDestructiveAction;
    }
}
