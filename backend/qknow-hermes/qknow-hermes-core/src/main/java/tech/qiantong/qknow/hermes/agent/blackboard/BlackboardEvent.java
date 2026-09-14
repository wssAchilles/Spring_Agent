package tech.qiantong.qknow.hermes.agent.blackboard;

import java.time.Instant;

/**
 * 响应式黑板广播事件
 */
public record BlackboardEvent(
        String eventType,
        String key,
        long version,
        Instant timestamp
) {}
