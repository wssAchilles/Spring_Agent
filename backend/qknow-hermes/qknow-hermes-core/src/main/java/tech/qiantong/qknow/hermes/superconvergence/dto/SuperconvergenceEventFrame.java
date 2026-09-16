package tech.qiantong.qknow.hermes.superconvergence.dto;

/**
 * Disruptor 4096 事件单帧 Record
 */
public record SuperconvergenceEventFrame(
    long sequence,
    String eventId,
    String eventType,
    MetacognitiveContextFrame contextFrame,
    KernelAuditVerdict auditVerdict,
    long enqueueTimeNanos,
    long dequeueTimeNanos
) {
    public SuperconvergenceEventFrame {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 不能为空");
        }
    }
}
