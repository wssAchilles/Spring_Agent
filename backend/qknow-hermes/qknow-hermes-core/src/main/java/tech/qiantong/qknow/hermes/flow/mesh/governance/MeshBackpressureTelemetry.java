package tech.qiantong.qknow.hermes.flow.mesh.governance;

import java.io.Serializable;

/**
 * 网格自适应遥测指标凭单 (不可变 Record)
 * 记录滑动窗口内的排队任务数、P99 延迟、429 速率与并发限制
 */
public record MeshBackpressureTelemetry(
        long windowTimestampMs,
        int activeSlots,
        int queuedTasks,
        double p99LatencyMs,
        double errorRate,
        int http429Count,
        int currentLimit
) implements Serializable {}
