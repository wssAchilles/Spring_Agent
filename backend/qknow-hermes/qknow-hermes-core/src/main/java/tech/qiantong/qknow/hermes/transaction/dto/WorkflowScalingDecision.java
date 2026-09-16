package tech.qiantong.qknow.hermes.transaction.dto;

/**
 * 李雅普诺夫工作流自适应弹性伸缩决策 Record
 */
public record WorkflowScalingDecision(
        int previousSlots,
        int targetSlots,
        int allocatedSlots,
        double currentQueueBacklog,
        double arrivalRate,
        double latencyDerivative,
        String scalingAction,
        long decisionDurationNanos
) {}
