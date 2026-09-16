package tech.qiantong.qknow.hermes.coalition.dto;

/**
 * 1000Hz 跨域协同事件单帧 Record
 */
public record CoalitionEventFrame(
    long sequenceNumber,
    String eventType,
    String payloadHash,
    boolean isJitterDetected,
    long timestampNs
) {}
