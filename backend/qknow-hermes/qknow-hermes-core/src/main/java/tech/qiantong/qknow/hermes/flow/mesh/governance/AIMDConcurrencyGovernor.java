package tech.qiantong.qknow.hermes.flow.mesh.governance;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Envoy 延迟梯度与李雅普诺夫稳态理论的 AIMD 并发调谐控制器
 * 用于智能体调度网格在面对下游 DeepSeek API 429 限流及延迟抖动时的自适应背压
 */
public class AIMDConcurrencyGovernor {

    private static final Logger log = LoggerFactory.getLogger(AIMDConcurrencyGovernor.class);

    private final int minConcurrency;
    private final int maxConcurrency;
    private final double backoffFactor; // 乘性减衰减系数，默认 0.5
    private final long targetP99LatencyMs;

    private final AtomicInteger currentConcurrencyLimit;
    private final AtomicLong lastAdjustmentTimeMs = new AtomicLong(0L);
    private final AtomicInteger consecutiveMinLimitHits = new AtomicInteger(0);

    public AIMDConcurrencyGovernor(int initialLimit, int minConcurrency, int maxConcurrency,
                                   double backoffFactor, long targetP99LatencyMs) {
        this.minConcurrency = Math.max(1, minConcurrency);
        this.maxConcurrency = Math.max(this.minConcurrency, maxConcurrency);
        this.backoffFactor = (backoffFactor > 0.0 && backoffFactor < 1.0) ? backoffFactor : 0.5;
        this.targetP99LatencyMs = targetP99LatencyMs > 0 ? targetP99LatencyMs : 1500L;
        this.currentConcurrencyLimit = new AtomicInteger(
                Math.min(Math.max(initialLimit, this.minConcurrency), this.maxConcurrency)
        );
    }

    /**
     * 根据滑动窗口遥测指标进行周期性动态调谐 (AIMD 结合 Envoy 延迟梯度)
     */
    public synchronized int adjustOnFeedback(MeshBackpressureTelemetry telemetry) {
        long now = System.currentTimeMillis();
        int current = currentConcurrencyLimit.get();

        // 1. 遭遇 429 限流或高延迟/高错误率：触发乘性减 (Multiplicative Decrease)
        if (telemetry.http429Count() > 0 || telemetry.p99LatencyMs() > targetP99LatencyMs * 1.5 || telemetry.errorRate() > 0.05) {
            int reduced = (int) Math.floor(current * backoffFactor);
            int nextLimit = Math.max(minConcurrency, reduced);
            currentConcurrencyLimit.set(nextLimit);
            lastAdjustmentTimeMs.set(now);

            if (nextLimit == minConcurrency) {
                int hits = consecutiveMinLimitHits.incrementAndGet();
                if (hits >= 5) {
                    // 自愈探针：连续 5 次触底，重置探测并尝试微幅恢复避免永久卡死
                    log.warn("[AIMD 背压] 连续 5 次触底最低并发 {}，激活自愈探针允许一次加性探测", minConcurrency);
                    consecutiveMinLimitHits.set(0);
                    currentConcurrencyLimit.set(Math.min(maxConcurrency, minConcurrency + 1));
                    return currentConcurrencyLimit.get();
                }
            } else {
                consecutiveMinLimitHits.set(0);
            }

            log.warn("[AIMD 背压] 触发乘性减: 限制由 {} 降至 {}, 原因: 429次数={}, P99={}ms, 错误率={}",
                    current, nextLimit, telemetry.http429Count(), telemetry.p99LatencyMs(), telemetry.errorRate());
            return nextLimit;
        }

        consecutiveMinLimitHits.set(0);

        // 2. 指标良好且未过载：触发加性增 (Additive Increase)
        if (telemetry.p99LatencyMs() <= targetP99LatencyMs && telemetry.errorRate() <= 0.01) {
            // 控制加性增频率，至少间隔 1000ms 调整一次
            if (current < maxConcurrency && (now - lastAdjustmentTimeMs.get() >= 1000L)) {
                int nextLimit = Math.min(maxConcurrency, current + 1);
                currentConcurrencyLimit.set(nextLimit);
                lastAdjustmentTimeMs.set(now);
                log.info("[AIMD 背压] 触发加性增: 限制由 {} 增至 {}, P99={}ms", current, nextLimit, telemetry.p99LatencyMs());
                return nextLimit;
            }
        }

        return current;
    }

    public int getCurrentLimit() {
        return currentConcurrencyLimit.get();
    }

    public int getMinConcurrency() {
        return minConcurrency;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }
}
