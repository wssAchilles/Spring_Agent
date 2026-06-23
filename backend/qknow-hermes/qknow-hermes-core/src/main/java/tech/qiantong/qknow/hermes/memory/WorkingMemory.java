package tech.qiantong.qknow.hermes.memory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全的 key-value 工作记忆。
 */
public class WorkingMemory {

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    public WorkingMemory() {
    }

    public void set(String key, Object value) {
        store.put(key, value);
    }

    public Object get(String key) {
        return store.get(key);
    }

    public Object getOrDefault(String key, Object defaultValue) {
        return store.getOrDefault(key, defaultValue);
    }

    public void remove(String key) {
        store.remove(key);
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
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
}
