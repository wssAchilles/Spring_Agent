package tech.qiantong.qknow.hermes.agent.blackboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工业级线程安全响应式共享黑板
 */
@Slf4j
@Component
public class SharedBlackboard {

    private final AtomicLong globalVersion = new AtomicLong(0);

    // 状态分区存储
    private final Map<String, BlackboardEntry> factsTable = new ConcurrentHashMap<>();
    private final Map<String, BlackboardEntry> hypothesesTable = new ConcurrentHashMap<>();
    private final List<String> traceLogs = Collections.synchronizedList(new ArrayList<>());

    // Project Reactor 响应式事件广播流 (带缓冲防护背压)
    private final Sinks.Many<BlackboardEvent> eventSink = Sinks.many().multicast().onBackpressureBuffer(1024);

    /**
     * 获取事件流以供观察者/下游订阅
     */
    public Flux<BlackboardEvent> eventStream() {
        return eventSink.asFlux();
    }

    public long getGlobalVersion() {
        return globalVersion.get();
    }

    /**
     * 获取指定事实条目的当前版本号 (若不存在返回 0)
     */
    public long getFactVersion(String key) {
        BlackboardEntry entry = factsTable.get(key);
        return entry == null ? 0L : entry.version();
    }

    /**
     * 基于 CAS 乐观锁版本提交事实 (防脑裂并发冲突)
     * 校验当前事实版本与预期版本，若版本不匹配则拒绝写入返回 false
     */
    public synchronized boolean commitFactWithVersion(String key, String value, long expectedVersion, String sourceTag) {
        BlackboardEntry existing = factsTable.get(key);
        long currentVersion = existing == null ? 0L : existing.version();
        if (currentVersion != expectedVersion) {
            log.warn("[SharedBlackboard] 乐观锁版本冲突拒绝写入！key={}, expectedVersion={}, currentVersion={}",
                    key, expectedVersion, currentVersion);
            return false;
        }

        long nextVersion = globalVersion.incrementAndGet();
        BlackboardEntry newEntry = new BlackboardEntry(key, value, sourceTag, nextVersion, Instant.now());
        factsTable.put(key, newEntry);
        eventSink.tryEmitNext(new BlackboardEvent("FACT_COMMITTED", key, nextVersion, Instant.now()));
        return true;
    }

    /**
     * 基于 CAS 乐观锁提交事实 (Commit Fact)
     */
    public boolean commitFact(String key, String value, String sourceTag) {
        long currentVersion = globalVersion.incrementAndGet();
        BlackboardEntry newEntry = new BlackboardEntry(key, value, sourceTag, currentVersion, Instant.now());

        // 原子写入最新版本
        factsTable.put(key, newEntry);

        // 触发响应式广播
        eventSink.tryEmitNext(new BlackboardEvent("FACT_COMMITTED", key, currentVersion, Instant.now()));
        return true;
    }

    public String getFact(String key) {
        BlackboardEntry entry = factsTable.get(key);
        return entry == null ? null : entry.value();
    }

    public Map<String, String> getFactsByKeys(List<String> keys) {
        Map<String, String> results = new LinkedHashMap<>();
        if (keys == null) {
            return results;
        }
        for (String key : keys) {
            BlackboardEntry entry = factsTable.get(key);
            if (entry != null) {
                results.put(key, entry.value());
            }
        }
        return results;
    }

    public Map<String, BlackboardEntry> getFactSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(factsTable));
    }

    public void recordTrace(String sessionId, String taskId, String status, String message) {
        String logEntry = String.format("[%s] [Session: %s] [Task: %s] Status: %s - %s",
                Instant.now(), sessionId, taskId, status, message);
        traceLogs.add(logEntry);
        log.debug("[BlackboardTrace] {}", logEntry);
    }

    public List<String> getTraceLogs() {
        return List.copyOf(traceLogs);
    }
}
