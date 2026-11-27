package tech.qiantong.qknow.hermes.cognitive.evolution;

import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetTopologyOperator;

/**
 * RHO 回顾性自我偏好样本对（对应论文 6.2 离线慢轨演化，严格无外部标注依赖）。
 */
public record SelfPreferencePair(
        String requestId,
        String queryText,
        SupernetTopologyOperator chosenOperator,
        DeepSeekReasoningEffort chosenEffort,
        double chosenUtility,
        String rejectedFailureReason,
        double rejectedUtility
) {
}
