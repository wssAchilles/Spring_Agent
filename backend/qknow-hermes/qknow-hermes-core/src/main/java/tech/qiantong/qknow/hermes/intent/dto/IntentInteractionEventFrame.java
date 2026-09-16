package tech.qiantong.qknow.hermes.intent.dto;

/**
 * 1000Hz 意图交互事件单帧 Java 21 Record
 * 用于 Disruptor 无锁队列高频流转
 */
public record IntentInteractionEventFrame(
        long sequenceId,
        String sessionId,
        IntentFsmState fsmState,
        String eventType,
        String payload,
        double latencyJitterMs,
        long timestamp
) {
    public static final String TYPE_INTENT_RESOLVE = "INTENT_RESOLVE";
    public static final String TYPE_AMBIGUITY_DETECTED = "AMBIGUITY_DETECTED";
    public static final String TYPE_CLARIFICATION_PROMPT = "CLARIFICATION_PROMPT";
    public static final String TYPE_SLOT_FILLED = "SLOT_FILLED";
    public static final String TYPE_EXECUTION_CONFIRMED = "EXECUTION_CONFIRMED";
}
