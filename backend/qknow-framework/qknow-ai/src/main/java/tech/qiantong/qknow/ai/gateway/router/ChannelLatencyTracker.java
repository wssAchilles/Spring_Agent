package tech.qiantong.qknow.ai.gateway.router;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Phase 28: 通道无锁环形滑动窗口时延分布追踪器
 * 统计实时 P50, P90, P99 延迟与平均首字延迟 (TTFT)
 */
public class ChannelLatencyTracker {

    private static final int BUCKET_COUNT = 60; // 60 秒环形滑动窗口

    @Data
    @Builder
    public static class LatencySnapshot {
        private long p50Millis;
        private long p90Millis;
        private long p99Millis;
        private long avgTtftMillis;
        private long totalSampleCount;
    }

    private static class TimeBucket {
        final AtomicLong timestampSeconds = new AtomicLong(0L);
        final List<Long> durations = Collections.synchronizedList(new ArrayList<>());
        final LongAdder ttftSum = new LongAdder();
        final LongAdder ttftCount = new LongAdder();

        void reset(long sec) {
            timestampSeconds.set(sec);
            durations.clear();
            ttftSum.reset();
            ttftCount.reset();
        }
    }

    private final TimeBucket[] buckets = new TimeBucket[BUCKET_COUNT];

    public ChannelLatencyTracker() {
        for (int i = 0; i < BUCKET_COUNT; i++) {
            buckets[i] = new TimeBucket();
        }
    }

    /**
     * 记录一次调用的时延采样数据
     *
     * @param ttftMillis 首 Token 延迟 (毫秒, 非流式可与总耗时相同)
     * @param totalDurationMillis 总往返耗时 (毫秒)
     */
    public void record(long ttftMillis, long totalDurationMillis) {
        long currentSec = System.currentTimeMillis() / 1000L;
        int slot = (int) (currentSec % BUCKET_COUNT);

        TimeBucket bucket = buckets[slot];
        if (bucket.timestampSeconds.get() != currentSec) {
            bucket.reset(currentSec);
        }

        bucket.durations.add(Math.max(1L, totalDurationMillis));
        if (ttftMillis > 0) {
            bucket.ttftSum.add(ttftMillis);
            bucket.ttftCount.increment();
        }
    }

    /**
     * 获取当前滑动窗口（最近 60 秒）的时延百分位数与 TTFT 统计快照
     */
    public LatencySnapshot getSnapshot() {
        long currentSec = System.currentTimeMillis() / 1000L;
        List<Long> allSamples = new ArrayList<>();
        long totalTtftSum = 0L;
        long totalTtftCount = 0L;

        for (int i = 0; i < BUCKET_COUNT; i++) {
            TimeBucket b = buckets[i];
            long bucketSec = b.timestampSeconds.get();
            // 只汇聚最近 60 秒内的数据
            if (currentSec - bucketSec < BUCKET_COUNT) {
                synchronized (b.durations) {
                    allSamples.addAll(b.durations);
                }
                totalTtftSum += b.ttftSum.sum();
                totalTtftCount += b.ttftCount.sum();
            }
        }

        if (allSamples.isEmpty()) {
            // 默认基准默认值
            return LatencySnapshot.builder()
                    .p50Millis(300L)
                    .p90Millis(600L)
                    .p99Millis(1000L)
                    .avgTtftMillis(300L)
                    .totalSampleCount(0L)
                    .build();
        }

        Collections.sort(allSamples);
        int n = allSamples.size();
        long p50 = allSamples.get((int) (n * 0.50));
        long p90 = allSamples.get(Math.min((int) (n * 0.90), n - 1));
        long p99 = allSamples.get(Math.min((int) (n * 0.99), n - 1));
        long avgTtft = totalTtftCount > 0 ? (totalTtftSum / totalTtftCount) : p50;

        return LatencySnapshot.builder()
                .p50Millis(p50)
                .p90Millis(p90)
                .p99Millis(p99)
                .avgTtftMillis(avgTtft)
                .totalSampleCount(n)
                .build();
    }
}
