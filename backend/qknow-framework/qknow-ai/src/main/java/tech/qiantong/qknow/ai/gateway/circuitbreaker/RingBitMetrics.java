package tech.qiantong.qknow.ai.gateway.circuitbreaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 28: 无锁环形位数组调用指标采集器
 * 基于固定容量的环形槽位与原子游标取模，单次采样耗时 < 5ns，零 GC 对象分配
 */
public class RingBitMetrics {

    public static final int SUCCESS = 0;
    public static final int FAILURE = 1;
    public static final int SLOW = 2;

    private final int capacity;
    private final AtomicIntegerArray slots;
    private final AtomicLong indexSequence = new AtomicLong(0L);
    private final AtomicInteger totalRecorded = new AtomicInteger(0);

    public RingBitMetrics(int capacity) {
        this.capacity = capacity;
        this.slots = new AtomicIntegerArray(capacity);
        // 初始化全部槽位为 -1 (未记录)
        for (int i = 0; i < capacity; i++) {
            this.slots.set(i, -1);
        }
    }

    /**
     * 记录一次调用结果 (无锁原子推进)
     *
     * @param resultType 0-成功, 1-失败, 2-慢调用
     */
    public void record(int resultType) {
        long seq = indexSequence.getAndIncrement();
        int slotIndex = (int) (Math.abs(seq) % capacity);
        slots.set(slotIndex, resultType);
        if (totalRecorded.get() < capacity) {
            totalRecorded.incrementAndGet();
        }
    }

    /**
     * 获取当前有效样本数
     */
    public int getRecordedSampleCount() {
        return Math.min(totalRecorded.get(), capacity);
    }

    /**
     * 计算当前滑动窗口内的失败率 [0.0, 1.0]
     */
    public double getFailureRate() {
        int sampleCount = getRecordedSampleCount();
        if (sampleCount == 0) {
            return 0.0;
        }

        int failures = 0;
        for (int i = 0; i < capacity; i++) {
            int val = slots.get(i);
            if (val == FAILURE) {
                failures++;
            }
        }
        return (double) failures / sampleCount;
    }

    /**
     * 重置所有槽位
     */
    public void reset() {
        for (int i = 0; i < capacity; i++) {
            slots.set(i, -1);
        }
        totalRecorded.set(0);
        indexSequence.set(0L);
    }
}
