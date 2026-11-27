package tech.qiantong.qknow.hermes.cognitive.control;

import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetTopologyOperator;

/**
 * 单步证据平方弦漂移状态转移不可变凭单（Java 21 Record）。
 * <p>
 * 对应公式：{@code Delta_drift(t) = 2 * (1 - <z_t, z_{t-1}>)}。
 * </p>
 */
public record StepDriftTransitionReceipt(
        String requestId,
        int stepIndex,
        String toolName,
        double chordDriftSquared,
        double evidenceConfidence,
        boolean replanTriggered,
        boolean earlyExitTriggered,
        SupernetTopologyOperator nextOperator,
        DeepSeekReasoningEffort nextEffort,
        long timestampMs
) {
}
