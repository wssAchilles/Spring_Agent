package tech.qiantong.qknow.hermes.agent.orchestration;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 线程安全的有界 LRU + TTL 缓存映射容器 (P0-15 内存治理)
 *
 * <p>用于替代多智能体编排器、蜂群主管与辩论协调器中的无界 {@code ConcurrentHashMap}，
 * 严格限制内存中保留的审计凭单与黑板快照数量上限及存活周期，消除高并发长稳运行下的内存泄漏与隐私滞留风险。</p>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class BoundedTtlMap<K, V> extends AbstractMap<K, V> {

    public static final int DEFAULT_MAX_ENTRIES = 256;
    public static final long DEFAULT_TTL_MS = 30 * 60 * 1000L;

    private final int maxEntries;
    private final long ttlMs;
    private final LinkedHashMap<K, TimedValue<V>> backingMap;

    private record TimedValue<V>(V value, long expireAtMs) {
        boolean isExpired(long nowMs) {
            return nowMs >= expireAtMs;
        }
    }

    public BoundedTtlMap() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_TTL_MS);
    }

    public BoundedTtlMap(int maxEntries, long ttlMs) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries 必须大于 0");
        }
        if (ttlMs <= 0) {
            throw new IllegalArgumentException("ttlMs 必须大于 0");
        }
        this.maxEntries = maxEntries;
        this.ttlMs = ttlMs;
        this.backingMap = new LinkedHashMap<>(Math.min(maxEntries, 64), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, TimedValue<V>> eldest) {
                return size() > BoundedTtlMap.this.maxEntries;
            }
        };
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public long getTtlMs() {
        return ttlMs;
    }

    @Override
    public synchronized V put(K key, V value) {
        if (key == null || value == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        evictExpiredInternal(now);
        TimedValue<V> previous = backingMap.put(key, new TimedValue<>(value, now + ttlMs));
        return (previous != null && !previous.isExpired(now)) ? previous.value() : null;
    }

    @Override
    public synchronized V get(Object key) {
        if (key == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        TimedValue<V> timed = backingMap.get(key);
        if (timed == null) {
            return null;
        }
        if (timed.isExpired(now)) {
            backingMap.remove(key);
            return null;
        }
        return timed.value();
    }

    @Override
    public synchronized V getOrDefault(Object key, V defaultValue) {
        V val = get(key);
        return val != null ? val : defaultValue;
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public synchronized V remove(Object key) {
        if (key == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        TimedValue<V> removed = backingMap.remove(key);
        return (removed != null && !removed.isExpired(now)) ? removed.value() : null;
    }

    @Override
    public synchronized void clear() {
        backingMap.clear();
    }

    @Override
    public synchronized int size() {
        evictExpiredInternal(System.currentTimeMillis());
        return backingMap.size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public synchronized Set<Entry<K, V>> entrySet() {
        long now = System.currentTimeMillis();
        evictExpiredInternal(now);
        Set<Entry<K, V>> result = new LinkedHashSet<>(backingMap.size());
        for (Entry<K, TimedValue<V>> entry : backingMap.entrySet()) {
            result.add(new SimpleImmutableEntry<>(entry.getKey(), entry.getValue().value()));
        }
        return result;
    }

    @Override
    public synchronized Collection<V> values() {
        long now = System.currentTimeMillis();
        evictExpiredInternal(now);
        List<V> list = new ArrayList<>(backingMap.size());
        for (TimedValue<V> tv : backingMap.values()) {
            list.add(tv.value());
        }
        return list;
    }

    private void evictExpiredInternal(long nowMs) {
        backingMap.entrySet().removeIf(entry -> entry.getValue().isExpired(nowMs));
    }
}
