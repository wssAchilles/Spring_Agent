package tech.qiantong.qknow.hermes.federation.dto;

/**
 * 1000Hz 联邦与合规总线事件单帧 Record
 */
public record FederatedComplianceEventFrame(
        long sequenceId,
        String transactionId,
        String eventType,
        String payloadSummary,
        long timestampEpochMs
) {}
