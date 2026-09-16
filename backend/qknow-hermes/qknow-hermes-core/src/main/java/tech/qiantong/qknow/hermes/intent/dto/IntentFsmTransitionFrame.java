package tech.qiantong.qknow.hermes.intent.dto;

/**
 * 确定性意图交互有限状态机跃迁事件 Java 21 Record
 * 约束 5 态状态机确定性有序转移
 */
public record IntentFsmTransitionFrame(
        String sessionId,
        IntentFsmState sourceState,
        IntentFsmState targetState,
        String triggerEvent,
        int roundCounter,
        String payload,
        long timestamp
) {
    public static final String STATE_INITIAL_PARSING = "INITIAL_PARSING";
    public static final String STATE_AMBIGUITY_DETECTED = "AMBIGUITY_DETECTED";
    public static final String STATE_ACTIVE_CLARIFYING = "ACTIVE_CLARIFYING";
    public static final String STATE_SLOT_CONVERGED = "SLOT_CONVERGED";
    public static final String STATE_CONFIRMED_EXECUTION = "CONFIRMED_EXECUTION";

    public static final String EVENT_AMBIGUITY_FOUND = "EVENT_AMBIGUITY_FOUND";
    public static final String EVENT_NO_AMBIGUITY = "EVENT_NO_AMBIGUITY";
    public static final String EVENT_START_CLARIFICATION = "EVENT_START_CLARIFICATION";
    public static final String EVENT_USER_RESPONSE = "EVENT_USER_RESPONSE";
    public static final String EVENT_SLOTS_COMPLETED = "EVENT_SLOTS_COMPLETED";
    public static final String EVENT_USER_CONFIRMED = "EVENT_USER_CONFIRMED";
}
