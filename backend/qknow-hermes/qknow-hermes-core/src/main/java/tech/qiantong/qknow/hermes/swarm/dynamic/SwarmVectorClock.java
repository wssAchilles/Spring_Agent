package tech.qiantong.qknow.hermes.swarm.dynamic;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 智能体因果时序向量时钟 (Swarm Vector Clock)
 * 用于在多智能体异步推演与事件分发中维护因果偏序关系 (Partial Order)，杜绝并发竞争与消息失序
 */
public record SwarmVectorClock(Map<String, Long> timestamps) {

    public SwarmVectorClock {
        timestamps = timestamps == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(timestamps));
    }

    public static SwarmVectorClock initial() {
        return new SwarmVectorClock(Collections.emptyMap());
    }

    public long getTime(String agentId) {
        return timestamps.getOrDefault(agentId, 0L);
    }

    /**
     * 当前节点本地时钟自增一步
     */
    public SwarmVectorClock tick(String agentId) {
        Objects.requireNonNull(agentId, "agentId cannot be null");
        Map<String, Long> next = new HashMap<>(timestamps);
        next.put(agentId, next.getOrDefault(agentId, 0L) + 1L);
        return new SwarmVectorClock(next);
    }

    /**
     * 合并两个向量时钟（按键取最大值）
     */
    public SwarmVectorClock merge(SwarmVectorClock other) {
        if (other == null) return this;
        Map<String, Long> merged = new HashMap<>(timestamps);
        for (Map.Entry<String, Long> entry : other.timestamps().entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), (v1, v2) -> Math.max(v1, v2));
        }
        return new SwarmVectorClock(merged);
    }

    /**
     * 判断当前时钟是否严格因果早于 (happens-before) 另一时钟:
     * this <= other 且 exists k, this[k] < other[k]
     */
    public boolean happensBefore(SwarmVectorClock other) {
        if (other == null) return false;
        boolean strictlySmallerInAtLeastOne = false;

        // 检查 this 中所有键
        for (Map.Entry<String, Long> entry : timestamps.entrySet()) {
            long otherVal = other.getTime(entry.getKey());
            if (entry.getValue() > otherVal) {
                return false;
            }
            if (entry.getValue() < otherVal) {
                strictlySmallerInAtLeastOne = true;
            }
        }

        // 检查 other 中独有的键
        for (Map.Entry<String, Long> entry : other.timestamps().entrySet()) {
            if (!timestamps.containsKey(entry.getKey()) && entry.getValue() > 0L) {
                strictlySmallerInAtLeastOne = true;
            }
        }

        return strictlySmallerInAtLeastOne;
    }

    /**
     * 判断两个时钟是否因果并发 (Concurrent)
     */
    public boolean isConcurrentWith(SwarmVectorClock other) {
        if (other == null) return false;
        if (this.equals(other)) return false;
        return !this.happensBefore(other) && !other.happensBefore(this);
    }
}
