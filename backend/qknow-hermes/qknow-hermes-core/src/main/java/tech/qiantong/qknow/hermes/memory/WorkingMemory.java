package tech.qiantong.qknow.hermes.memory;

import tech.qiantong.qknow.redis.service.IRedisService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全的 key-value 工作记忆。
 */
public class WorkingMemory {

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();
    private final IRedisService redisService;

    public WorkingMemory() {
        this(null);
    }

    public WorkingMemory(IRedisService redisService) {
        this.redisService = redisService;
    }

    public void set(String key, Object value) {
        store.put(key, value);
    }

    public void set(String sessionId, String key, Object value) {
        if (redisService == null || sessionId == null || sessionId.isBlank()) {
            set(key, value);
            return;
        }
        redisService.hashPut(redisKey(sessionId), key, String.valueOf(value));
    }

    public Object get(String key) {
        return store.get(key);
    }

    public Object get(String sessionId, String key) {
        if (redisService == null || sessionId == null || sessionId.isBlank()) {
            return get(key);
        }
        return redisService.hashGet(redisKey(sessionId), key);
    }

    public Object getOrDefault(String key, Object defaultValue) {
        return store.getOrDefault(key, defaultValue);
    }

    public void remove(String key) {
        store.remove(key);
    }

    public void remove(String sessionId, String key) {
        if (redisService == null || sessionId == null || sessionId.isBlank()) {
            remove(key);
            return;
        }
        redisService.hashDelete(redisKey(sessionId), key);
    }

    public void clear() {
        store.clear();
    }

    public void clear(String sessionId) {
        if (redisService == null || sessionId == null || sessionId.isBlank()) {
            clear();
            return;
        }
        redisService.delete(redisKey(sessionId));
    }

    public int size() {
        return store.size();
    }

    public int size(String sessionId) {
        if (redisService == null || sessionId == null || sessionId.isBlank()) {
            return size();
        }
        return redisService.hashGetAll(redisKey(sessionId)).size();
    }

    public boolean containsKey(String key) {
        return store.containsKey(key);
    }

    /**
     * 返回当前所有条目的不可变快照副本。
     */
    public Map<String, Object> snapshot() {
        return new java.util.HashMap<>(store);
    }

    public Map<String, Object> snapshot(String sessionId) {
        if (redisService == null || sessionId == null || sessionId.isBlank()) {
            return snapshot();
        }
        return redisService.hashGetAll(redisKey(sessionId));
    }

    private String redisKey(String sessionId) {
        return "memory:working:" + sessionId;
    }
}
