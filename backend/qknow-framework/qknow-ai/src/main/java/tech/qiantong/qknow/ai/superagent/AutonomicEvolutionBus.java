package tech.qiantong.qknow.ai.superagent;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Phase 60: 自主进化生态总线
 * <p>
 * 提供背压流控、有界队列容错与主权准入挂载。
 */
public class AutonomicEvolutionBus {

    public static final int MAX_QUEUE_CAPACITY = 1000;

    public record BusEvent(
            String eventId,
            String senderAgentId,
            String topic,
            Object payload,
            long timestamp
    ) {}

    private final BlockingQueue<BusEvent> eventQueue = new ArrayBlockingQueue<>(MAX_QUEUE_CAPACITY);
    private final Map<String, List<Consumer<BusEvent>>> subscribers = new ConcurrentHashMap<>();
    private final AtomicBoolean isDrained = new AtomicBoolean(false);

    /**
     * 订阅主题
     */
    public void subscribe(String topic, Consumer<BusEvent> subscriber) {
        if (topic == null || subscriber == null) {
            return;
        }
        subscribers.computeIfAbsent(topic, k -> Collections.synchronizedList(new ArrayList<>())).add(subscriber);
    }

    /**
     * 发布事件（带背压与容量判定）
     *
     * @return true 若成功投递，false 若被流控拒绝
     */
    public boolean publishEvent(String senderAgentId, String topic, Object payload) {
        if (isDrained.get()) {
            return false;
        }
        if (senderAgentId == null || topic == null) {
            throw new IllegalArgumentException("发送方与主题不能为空");
        }

        BusEvent event = new BusEvent(
                UUID.randomUUID().toString(),
                senderAgentId,
                topic,
                payload,
                System.currentTimeMillis()
        );

        boolean offered = eventQueue.offer(event);
        if (offered) {
            // 派发给订阅者
            List<Consumer<BusEvent>> topicSubscribers = subscribers.get(topic);
            if (topicSubscribers != null) {
                synchronized (topicSubscribers) {
                    for (Consumer<BusEvent> consumer : topicSubscribers) {
                        try {
                            consumer.accept(event);
                        } catch (Exception ignored) {
                            // 订阅异常隔离
                        }
                    }
                }
            }
        }
        return offered;
    }

    /**
     * 紧急排空与清空总线
     */
    public int purgeAndDrain() {
        isDrained.set(true);
        List<BusEvent> drained = new ArrayList<>();
        eventQueue.drainTo(drained);
        return drained.size();
    }

    /**
     * 获取队列当前积压长度
     */
    public int getQueueSize() {
        return eventQueue.size();
    }

    /**
     * 重置总线状态
     */
    public void reset() {
        eventQueue.clear();
        isDrained.set(false);
    }
}
