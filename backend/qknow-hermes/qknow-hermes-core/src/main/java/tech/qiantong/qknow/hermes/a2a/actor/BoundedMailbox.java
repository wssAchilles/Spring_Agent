package tech.qiantong.qknow.hermes.a2a.actor;

import tech.qiantong.qknow.hermes.a2a.envelope.A2AMessageEnvelope;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 严格有界容量的线程安全异步消息信箱 (Bounded Mailbox)
 * 落实定理 3：非阻塞入队与主动反压，破坏死锁 Hold-and-Wait 条件，确保等待图严格无环
 */
public class BoundedMailbox {

    public static final int DEFAULT_CAPACITY = 1024;
    private final int capacity;
    private final BlockingQueue<A2AMessageEnvelope> queue;

    public BoundedMailbox() {
        this(DEFAULT_CAPACITY);
    }

    public BoundedMailbox(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Mailbox 容量必须大于 0");
        }
        this.capacity = capacity;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * 非阻塞尝试投递消息
     *
     * @param envelope 消息信封
     * @return 成功入队返回 true；若队列已满立即返回 false（触发主动反压，绝不挂起当前线程）
     */
    public boolean offer(A2AMessageEnvelope envelope) {
        if (envelope == null) {
            return false;
        }
        return queue.offer(envelope);
    }

    /**
     * 非阻塞拉取消息
     */
    public A2AMessageEnvelope poll() {
        return queue.poll();
    }

    /**
     * 带超时的阻塞拉取（仅供 Actor 专用虚拟线程消费事件循环内部使用）
     */
    public A2AMessageEnvelope poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /**
     * 判定是否达到 80% 高水位阈值
     */
    public boolean isHighWatermark() {
        return queue.size() >= (int) (capacity * 0.80);
    }

    public int capacity() {
        return capacity;
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    public void clear() {
        queue.clear();
    }
}
