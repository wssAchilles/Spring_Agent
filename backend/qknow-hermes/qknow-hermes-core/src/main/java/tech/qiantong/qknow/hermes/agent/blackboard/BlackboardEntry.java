package tech.qiantong.qknow.hermes.agent.blackboard;

import java.time.Instant;

/**
 * 黑板数据项（带版本号与来源标识）
 */
public record BlackboardEntry(
        String key,
        String value,
        String sourceTag,
        long version,
        Instant updatedAt
) {}
