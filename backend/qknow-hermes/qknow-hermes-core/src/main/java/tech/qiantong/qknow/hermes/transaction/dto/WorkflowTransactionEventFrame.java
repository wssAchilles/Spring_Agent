package tech.qiantong.qknow.hermes.transaction.dto;

/**
 * 1000Hz 事务无锁总线事件单帧 Record
 */
public record WorkflowTransactionEventFrame(
        long sequenceId,
        String transactionId,
        String eventType,
        String payloadSummary,
        long timestampEpochMs
) {}
