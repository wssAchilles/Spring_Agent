package tech.qiantong.qknow.module.kb.service.feedback;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kb.dal.dataobject.feedback.KbChatFeedbackDO;
import tech.qiantong.qknow.module.kb.dal.mapper.feedback.KbChatFeedbackMapper;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高并发反馈流削峰队列服务 (基于 Redis Streams / 内存阻塞队列缓冲与批量持久化)
 */
@Slf4j
@Service
public class FeedbackStreamQueueService {

    private static final int QUEUE_CAPACITY = 10_000;
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final BlockingQueue<KbChatFeedbackDO> bufferQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final List<KbChatFeedbackDO> inMemoryArchive = new CopyOnWriteArrayList<>();
    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalFlushed = new AtomicLong(0);

    @Autowired(required = false)
    private KbChatFeedbackMapper feedbackMapper;

    private ScheduledExecutorService scheduledExecutor;

    @PostConstruct
    public void init() {
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "feedback-flusher-thread");
            t.setDaemon(true);
            return t;
        });
        // 每 1000ms 触发一次批量落盘刷盘
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                flushBatch(DEFAULT_BATCH_SIZE);
            } catch (Exception e) {
                log.warn("反馈队列自动刷盘异常: {}", e.getMessage());
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
        flushBatch(QUEUE_CAPACITY);
    }

    /**
     * 将反馈事件推入削峰队列
     */
    public boolean enqueueFeedback(KbChatFeedbackDO feedback) {
        if (feedback == null) {
            return false;
        }
        boolean offered = bufferQueue.offer(feedback);
        if (offered) {
            totalEnqueued.incrementAndGet();
            inMemoryArchive.add(feedback);
        } else {
            log.error("反馈缓冲区已满 (capacity={}), 触发限流丢弃: msgId={}", QUEUE_CAPACITY, feedback.getMessageId());
        }
        return offered;
    }

    /**
     * 批量取出并落库持久化
     * @return 实际落库数量
     */
    public synchronized int flushBatch(int maxBatchSize) {
        if (bufferQueue.isEmpty()) {
            return 0;
        }
        List<KbChatFeedbackDO> batch = new ArrayList<>(maxBatchSize);
        bufferQueue.drainTo(batch, maxBatchSize);
        if (batch.isEmpty()) {
            return 0;
        }

        if (feedbackMapper != null) {
            try {
                for (KbChatFeedbackDO item : batch) {
                    feedbackMapper.insert(item);
                }
            } catch (Exception e) {
                log.error("批量持久化反馈记录失败: {}", e.getMessage(), e);
            }
        }
        totalFlushed.addAndGet(batch.size());
        return batch.size();
    }

    public int getPendingCount() {
        return bufferQueue.size();
    }

    public long getTotalEnqueued() {
        return totalEnqueued.get();
    }

    public long getTotalFlushed() {
        return totalFlushed.get();
    }

    public List<KbChatFeedbackDO> getFeedbackListByMessageId(String messageId) {
        if (messageId == null) {
            return Collections.emptyList();
        }
        List<KbChatFeedbackDO> result = new ArrayList<>();
        for (KbChatFeedbackDO fb : inMemoryArchive) {
            if (messageId.equals(fb.getMessageId())) {
                result.add(fb);
            }
        }
        return result;
    }

    public void clearForTest() {
        bufferQueue.clear();
        inMemoryArchive.clear();
        totalEnqueued.set(0);
        totalFlushed.set(0);
    }
}
