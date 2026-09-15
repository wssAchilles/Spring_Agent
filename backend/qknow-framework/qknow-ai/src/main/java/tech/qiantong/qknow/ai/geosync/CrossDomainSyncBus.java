package tech.qiantong.qknow.ai.geosync;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Phase 61: 跨数据中心异步同步总线
 * <p>
 * 具备有界队列流控、离线暂存 (Hinted Handoff) 队列与网络分区恢复自愈回放。
 */
public class CrossDomainSyncBus {

    public static final int MAX_QUEUE_CAPACITY = 1000;

    public record SyncMessage(
            String messageId,
            String originRegion,
            String targetRegion,
            Object payload,
            long timestamp
    ) {}

    private final BlockingQueue<SyncMessage> liveQueue = new ArrayBlockingQueue<>(MAX_QUEUE_CAPACITY);
    // 离线 Hinted Handoff 暂存字典: targetRegion -> 待回放队列
    private final Map<String, Queue<SyncMessage>> hintedHandoffBuffers = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<SyncMessage>>> regionSubscribers = new ConcurrentHashMap<>();
    private final AtomicBoolean networkPartitioned = new AtomicBoolean(false);

    /**
     * 注册目标地域订阅监听
     */
    public void subscribe(String region, Consumer<SyncMessage> consumer) {
        if (region != null && consumer != null) {
            regionSubscribers.computeIfAbsent(region, k -> Collections.synchronizedList(new ArrayList<>())).add(consumer);
        }
    }

    /**
     * 发送跨域同步消息
     *
     * @return true 若成功投递（即时投递或暂存 Hinted Handoff），false 若队列饱和拒绝
     */
    public boolean publishSyncMessage(String originRegion, String targetRegion, Object payload) {
        if (originRegion == null || targetRegion == null) {
            throw new IllegalArgumentException("源地域与目标地域不能为空");
        }

        SyncMessage message = new SyncMessage(
                UUID.randomUUID().toString(),
                originRegion,
                targetRegion,
                payload,
                System.currentTimeMillis()
        );

        if (networkPartitioned.get()) {
            // 网络分区期间，自动存入目标地域的 Hinted Handoff 队列
            return storeHintedHandoff(targetRegion, message);
        }

        boolean offered = liveQueue.offer(message);
        if (offered) {
            dispatchToSubscribers(targetRegion, message);
        }
        return offered;
    }

    private boolean storeHintedHandoff(String targetRegion, SyncMessage message) {
        Queue<SyncMessage> buffer = hintedHandoffBuffers.computeIfAbsent(targetRegion, k -> new ConcurrentLinkedQueue<>());
        if (buffer.size() >= MAX_QUEUE_CAPACITY) {
            return false; // 离线缓存溢出保护
        }
        return buffer.offer(message);
    }

    private void dispatchToSubscribers(String targetRegion, SyncMessage message) {
        List<Consumer<SyncMessage>> subscribers = regionSubscribers.get(targetRegion);
        if (subscribers != null) {
            synchronized (subscribers) {
                for (Consumer<SyncMessage> consumer : subscribers) {
                    try {
                        consumer.accept(message);
                    } catch (Exception ignored) {
                        // 订阅异常隔离
                    }
                }
            }
        }
    }

    /**
     * 设置网络分区状态
     */
    public void setNetworkPartitioned(boolean partitioned) {
        this.networkPartitioned.set(partitioned);
    }

    public boolean isNetworkPartitioned() {
        return networkPartitioned.get();
    }

    /**
     * 网络恢复时回放目标地域的 Hinted Handoff 离线包
     *
     * @return 成功回放并派发的消息数量
     */
    public int replayHintedHandoff(String targetRegion) {
        Queue<SyncMessage> buffer = hintedHandoffBuffers.get(targetRegion);
        if (buffer == null || buffer.isEmpty()) {
            return 0;
        }

        int count = 0;
        SyncMessage msg;
        while ((msg = buffer.poll()) != null) {
            dispatchToSubscribers(targetRegion, msg);
            count++;
        }
        return count;
    }

    public int getHintedHandoffCount(String targetRegion) {
        Queue<SyncMessage> buffer = hintedHandoffBuffers.get(targetRegion);
        return buffer != null ? buffer.size() : 0;
    }

    public int getLiveQueueSize() {
        return liveQueue.size();
    }

    public void reset() {
        liveQueue.clear();
        hintedHandoffBuffers.clear();
        networkPartitioned.set(false);
    }
}
