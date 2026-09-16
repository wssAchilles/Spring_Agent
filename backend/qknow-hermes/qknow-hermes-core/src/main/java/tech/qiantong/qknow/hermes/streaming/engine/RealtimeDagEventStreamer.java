package tech.qiantong.qknow.hermes.streaming.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.streaming.dto.DagExecutionSnapshot;
import tech.qiantong.qknow.hermes.streaming.dto.DagNodeExecutionEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 实时 DAG 事件推流器 (RealtimeDagEventStreamer)
 * 基于反应式架构与微秒级分发管道，将内核工作流执行事件流式推送到前端画布
 */
public class RealtimeDagEventStreamer {

    private static final Logger log = LoggerFactory.getLogger(RealtimeDagEventStreamer.class);
    private static final int MAX_HISTORY_PER_SESSION = 500;

    // 会话订阅者注册表: sessionId -> 监听回调列表
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<DagNodeExecutionEvent>>> subscriberMap = new ConcurrentHashMap<>();

    // 会话事件环形历史缓存: sessionId -> 事件列表
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<DagNodeExecutionEvent>> historyMap = new ConcurrentHashMap<>();

    // 会话节点最新状态维护: sessionId -> (nodeId -> status)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> sessionNodeStates = new ConcurrentHashMap<>();

    // 会话节点耗时维护: sessionId -> (nodeId -> latencyMicros)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> sessionNodeLatencies = new ConcurrentHashMap<>();

    // 统计指标
    private final AtomicLong totalPublishedEvents = new AtomicLong(0);

    /**
     * 订阅指定会话的 DAG 节点执行事件
     */
    public AutoCloseable subscribe(String sessionId, Consumer<DagNodeExecutionEvent> listener) {
        if (sessionId == null || listener == null) {
            return () -> {};
        }
        CopyOnWriteArrayList<Consumer<DagNodeExecutionEvent>> listeners =
                subscriberMap.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        listeners.add(listener);

        return () -> listeners.remove(listener);
    }

    /**
     * 发布单步 DAG 节点执行事件并广播至前端订阅者
     * 包含微秒级纳秒时钟计时
     */
    public long publishEvent(DagNodeExecutionEvent event) {
        long startNano = System.nanoTime();
        if (event == null) {
            return 0;
        }

        String sessionId = event.sessionId();

        // 1. 维护会话节点状态与耗时
        ConcurrentHashMap<String, String> states =
                sessionNodeStates.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        states.put(event.nodeId(), event.status());

        ConcurrentHashMap<String, Long> latencies =
                sessionNodeLatencies.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        latencies.put(event.nodeId(), event.latencyMicros());

        // 2. 有界环形历史维护 (防 OOM)
        CopyOnWriteArrayList<DagNodeExecutionEvent> history =
                historyMap.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        history.add(event);
        if (history.size() > MAX_HISTORY_PER_SESSION) {
            history.remove(0);
        }

        // 3. 广播到活跃订阅者
        CopyOnWriteArrayList<Consumer<DagNodeExecutionEvent>> listeners = subscriberMap.get(sessionId);
        if (listeners != null && !listeners.isEmpty()) {
            for (Consumer<DagNodeExecutionEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (Exception ex) {
                    log.warn("向订阅者推送 DAG 事件失败: sessionId={}, eventId={}, err={}",
                            sessionId, event.eventId(), ex.getMessage());
                }
            }
        }

        totalPublishedEvents.incrementAndGet();
        return (System.nanoTime() - startNano) / 1000L;
    }

    /**
     * 构建指定会话的即时 DAG 执行状态图快照
     */
    public DagExecutionSnapshot buildSnapshot(String sessionId, int totalNodes) {
        Map<String, String> states = sessionNodeStates.getOrDefault(sessionId, new ConcurrentHashMap<>());
        Map<String, Long> latencies = sessionNodeLatencies.getOrDefault(sessionId, new ConcurrentHashMap<>());

        int completed = 0;
        long totalMicros = 0;
        for (Map.Entry<String, String> entry : states.entrySet()) {
            if (DagNodeExecutionEvent.STATUS_SUCCEEDED.equals(entry.getValue())
                    || DagNodeExecutionEvent.STATUS_SKIPPED.equals(entry.getValue())) {
                completed++;
            }
        }
        for (Long lat : latencies.values()) {
            if (lat != null) {
                totalMicros += lat;
            }
        }

        return new DagExecutionSnapshot(
                "snap_" + UUID.randomUUID().toString().substring(0, 8),
                sessionId,
                Collections.unmodifiableMap(states),
                Collections.unmodifiableMap(latencies),
                completed,
                Math.max(totalNodes, states.size()),
                totalMicros,
                System.currentTimeMillis()
        );
    }

    /**
     * 获取指定会话的历史事件回放列表
     */
    public List<DagNodeExecutionEvent> getHistory(String sessionId) {
        CopyOnWriteArrayList<DagNodeExecutionEvent> history = historyMap.get(sessionId);
        if (history == null) {
            return List.of();
        }
        return Collections.unmodifiableList(history);
    }

    /**
     * 清理已完成会话资源
     */
    public void cleanupSession(String sessionId) {
        subscriberMap.remove(sessionId);
        historyMap.remove(sessionId);
        sessionNodeStates.remove(sessionId);
        sessionNodeLatencies.remove(sessionId);
    }

    public long totalPublishedEvents() {
        return totalPublishedEvents.get();
    }

    public int activeSessionCount() {
        return sessionNodeStates.size();
    }
}
