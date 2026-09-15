package tech.qiantong.qknow.ai.geosync;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 61: 严格因果偏序向量时钟 (Vector Clock)
 * <p>
 * 基于定理 1.2，在纳秒级判定分布式事件的 Happened-Before 因果关系与并发冲突。
 */
public class CausalVectorClock {

    public enum Ordering {
        BEFORE,
        AFTER,
        EQUAL,
        CONCURRENT
    }

    private final Map<String, Long> clockMap;

    public CausalVectorClock() {
        this.clockMap = new ConcurrentHashMap<>();
    }

    public CausalVectorClock(Map<String, Long> initialMap) {
        this.clockMap = new ConcurrentHashMap<>(initialMap);
    }

    /**
     * 本地事件触发时钟递增
     */
    public synchronized void tick(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("节点 ID 不能为空");
        }
        clockMap.merge(nodeId, 1L, Long::sum);
    }

    /**
     * 接收远程向量时钟并执行逐分量最大值合并与本地自增
     */
    public synchronized void update(CausalVectorClock remoteClock, String localNodeId) {
        if (remoteClock != null) {
            remoteClock.clockMap.forEach((node, val) ->
                    clockMap.merge(node, val, Math::max)
            );
        }
        if (localNodeId != null && !localNodeId.isBlank()) {
            tick(localNodeId);
        }
    }

    /**
     * 比较两个向量时钟的因果偏序
     */
    public Ordering compare(CausalVectorClock other) {
        if (other == null) {
            return Ordering.AFTER;
        }

        Set<String> allKeys = new HashSet<>(this.clockMap.keySet());
        allKeys.addAll(other.clockMap.keySet());

        boolean hasLess = false;
        boolean hasGreater = false;

        for (String key : allKeys) {
            long v1 = this.clockMap.getOrDefault(key, 0L);
            long v2 = other.clockMap.getOrDefault(key, 0L);

            if (v1 < v2) {
                hasLess = true;
            } else if (v1 > v2) {
                hasGreater = true;
            }
        }

        if (!hasLess && !hasGreater) {
            return Ordering.EQUAL;
        } else if (hasLess && !hasGreater) {
            return Ordering.BEFORE;
        } else if (!hasLess && hasGreater) {
            return Ordering.AFTER;
        } else {
            return Ordering.CONCURRENT;
        }
    }

    /**
     * 判断是否与目标时钟处于因果并发（无先后关系）
     */
    public boolean isConcurrentWith(CausalVectorClock other) {
        return compare(other) == Ordering.CONCURRENT;
    }

    public long getClock(String nodeId) {
        return clockMap.getOrDefault(nodeId, 0L);
    }

    public Map<String, Long> asMap() {
        return Collections.unmodifiableMap(new HashMap<>(clockMap));
    }

    public CausalVectorClock clone() {
        return new CausalVectorClock(this.clockMap);
    }

    @Override
    public String toString() {
        return new TreeMap<>(clockMap).toString();
    }
}
