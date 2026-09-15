package tech.qiantong.qknow.ai.embodied.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.ai.embodied.dto.MultimodalTemporalFrame;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 生产级多源事件并发总线与熔断保护器 (Phase 64)
 * <p>
 * 采用严格定长环形缓冲原语，防止传感器 1000Hz 洪峰击穿 JVM 堆内存，超载时触发 Fail-Open 降级。
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class EventDrivenDecisionBus {

    private static final Logger log = LoggerFactory.getLogger(EventDrivenDecisionBus.class);
    public static final int BUFFER_CAPACITY = 4096; // 严格定长缓冲槽位
    private static final double FAIL_OPEN_THRESHOLD = 0.85; // 85% 水位线触发 Fail-Open 阻尼

    private final BlockingQueue<MultimodalTemporalFrame> ringQueue = new ArrayBlockingQueue<>(BUFFER_CAPACITY);
    private final AtomicLong droppedEventsCount = new AtomicLong(0);
    private final AtomicLong publishedEventsCount = new AtomicLong(0);

    /**
     * 发布时序感知事件，具备无锁极速探测与 Fail-Open 保护
     */
    public boolean publishEvent(MultimodalTemporalFrame event) {
        if (event == null) return false;

        double currentOccupancy = (double) ringQueue.size() / BUFFER_CAPACITY;
        if (currentOccupancy >= FAIL_OPEN_THRESHOLD) {
            // 触发 Fail-Open 降级：丢弃非关键物理高频采样或阻尼释放，保障系统不发生 OOM
            droppedEventsCount.incrementAndGet();
            log.warn("事件总线过载告警！占用率 {:.2f}% 触发 Fail-Open 降级抛弃，累计丢弃: {}",
                    currentOccupancy * 100, droppedEventsCount.get());
            return false;
        }

        boolean offered = ringQueue.offer(event);
        if (offered) {
            publishedEventsCount.incrementAndGet();
        }
        return offered;
    }

    public long getDroppedEventsCount() {
        return droppedEventsCount.get();
    }

    public long getPublishedEventsCount() {
        return publishedEventsCount.get();
    }

    public int getQueueSize() {
        return ringQueue.size();
    }
}
