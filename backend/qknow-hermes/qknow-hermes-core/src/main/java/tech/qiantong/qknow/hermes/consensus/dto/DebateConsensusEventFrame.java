package tech.qiantong.qknow.hermes.consensus.dto;

/**
 * 1000Hz 总线高频事件单帧 Java 21 Record
 */
public record DebateConsensusEventFrame(
        long sequenceId,
        String sessionId,
        String eventType,
        String payload,
        double latencyJitterMs,
        long timestamp
) {}
