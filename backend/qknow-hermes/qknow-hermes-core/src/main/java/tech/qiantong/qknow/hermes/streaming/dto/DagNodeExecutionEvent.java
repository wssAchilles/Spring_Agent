package tech.qiantong.qknow.hermes.streaming.dto;

import java.io.Serializable;

/**
 * DAG 节点执行事件单帧 Java 21 Record
 * 承载工作流图谱中各微状态的微秒级流转事件，用于实时推流至前端画布投影
 */
public record DagNodeExecutionEvent(
        String eventId,
        String sessionId,
        String nodeId,
        String nodeType,
        String status,
        long latencyMicros,
        int stepIndex,
        String payloadSummary,
        long timestamp
) implements Serializable {

    public static final String TYPE_INTENT = "INTENT";
    public static final String TYPE_REASONING = "REASONING";
    public static final String TYPE_TOOL = "TOOL";
    public static final String TYPE_KNOWLEDGE = "KNOWLEDGE";
    public static final String TYPE_GATE = "GATE";

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    public DagNodeExecutionEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 不能为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId 不能为空");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status 不能为空");
        }
    }
}
