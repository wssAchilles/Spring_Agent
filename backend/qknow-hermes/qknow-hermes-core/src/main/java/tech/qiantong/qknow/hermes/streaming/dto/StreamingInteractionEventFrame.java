package tech.qiantong.qknow.hermes.streaming.dto;

/**
 * 1000Hz 流式交互事件单帧 Java 21 Record
 * 用于 Disruptor 无锁环形总线中纳秒级推帧流转
 */
public record StreamingInteractionEventFrame(
        String eventId,
        String sessionId,
        String eventType, // TOKEN_CHUNK, TOPOLOGY_HEAL, HITL_SUSPEND, HITL_RESUME, SLICE_ROUTED
        String payload,
        boolean healthy,
        long timestamp
) {
    public static final String TYPE_TOKEN_CHUNK = "TOKEN_CHUNK";
    public static final String TYPE_TOPOLOGY_HEAL = "TOPOLOGY_HEAL";
    public static final String TYPE_HITL_SUSPEND = "HITL_SUSPEND";
    public static final String TYPE_HITL_RESUME = "HITL_RESUME";
    public static final String TYPE_SLICE_ROUTED = "SLICE_ROUTED";
}
