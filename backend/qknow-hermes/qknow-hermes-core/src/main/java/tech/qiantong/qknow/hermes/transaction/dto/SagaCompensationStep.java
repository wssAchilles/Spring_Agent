package tech.qiantong.qknow.hermes.transaction.dto;

/**
 * Saga 逆拓扑补偿单步记录 Record
 */
public record SagaCompensationStep(
        String nodeId,
        String nodeName,
        boolean success,
        String compensationAction,
        long durationNanos,
        String errorMessage
) {}
