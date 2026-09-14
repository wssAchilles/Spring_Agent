package tech.qiantong.qknow.ai.gateway.circuitbreaker;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.ai.gateway.model.ChannelStatus;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 28: 基于无锁原子状态机与环形位图的自适应三态断路器
 * 管理 CLOSED -> OPEN -> HALF-OPEN -> CLOSED 全生命周期流转
 */
@Slf4j
@Getter
public class AdaptiveCircuitBreaker {

    private final String name;
    private final int minimumNumberOfCalls;
    private final double failureRateThreshold;
    private final long waitDurationInOpenStateMillis;
    private final int permittedNumberOfCallsInHalfOpenState;
    private final int consecutiveFailureThreshold;

    private final AtomicReference<ChannelStatus> state = new AtomicReference<>(ChannelStatus.CLOSED);
    private final RingBitMetrics metrics;
    private final AtomicLong lastStateChangeTimestamp = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger halfOpenProbeCounter = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccessCounter = new AtomicInteger(0);

    public AdaptiveCircuitBreaker(String name,
                                  int minimumNumberOfCalls,
                                  double failureRateThreshold,
                                  long waitDurationInOpenStateMillis,
                                  int permittedNumberOfCallsInHalfOpenState,
                                  int consecutiveFailureThreshold) {
        this.name = name;
        this.minimumNumberOfCalls = minimumNumberOfCalls;
        this.failureRateThreshold = failureRateThreshold;
        this.waitDurationInOpenStateMillis = waitDurationInOpenStateMillis;
        this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        this.consecutiveFailureThreshold = consecutiveFailureThreshold;
        this.metrics = new RingBitMetrics(100);
    }

    /**
     * 判断当前断路器是否允许执行请求
     */
    public boolean tryAcquirePermission() {
        ChannelStatus current = state.get();

        if (current == ChannelStatus.CLOSED || current == ChannelStatus.HEALTHY) {
            return true;
        }

        if (current == ChannelStatus.OPEN) {
            long now = System.currentTimeMillis();
            long openTime = lastStateChangeTimestamp.get();
            // 检查是否已过 OPEN 冷却期
            if (now - openTime >= waitDurationInOpenStateMillis) {
                if (state.compareAndSet(ChannelStatus.OPEN, ChannelStatus.HALF_OPEN)) {
                    lastStateChangeTimestamp.set(now);
                    halfOpenProbeCounter.set(0);
                    halfOpenSuccessCounter.set(0);
                    log.info("断路器 [{}] 冷却期结束，自适应切换至 HALF_OPEN 半开探活状态", name);
                    return true;
                }
            }
            return false;
        }

        if (current == ChannelStatus.HALF_OPEN) {
            int probes = halfOpenProbeCounter.incrementAndGet();
            return probes <= permittedNumberOfCallsInHalfOpenState;
        }

        return false;
    }

    /**
     * 记录调用成功
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        metrics.record(RingBitMetrics.SUCCESS);

        if (state.get() == ChannelStatus.HALF_OPEN) {
            int successes = halfOpenSuccessCounter.incrementAndGet();
            if (successes >= permittedNumberOfCallsInHalfOpenState) {
                // 半开探针全部验证通过，自愈复位至 CLOSED
                if (state.compareAndSet(ChannelStatus.HALF_OPEN, ChannelStatus.CLOSED)) {
                    lastStateChangeTimestamp.set(System.currentTimeMillis());
                    metrics.reset();
                    log.info("断路器 [{}] 半开探活连续成功，自适应复位至 CLOSED 健康状态", name);
                }
            }
        }
    }

    /**
     * 记录调用失败
     */
    public void recordFailure() {
        int fails = consecutiveFailures.incrementAndGet();
        metrics.record(RingBitMetrics.FAILURE);

        ChannelStatus current = state.get();
        if (current == ChannelStatus.HALF_OPEN) {
            // 半开状态下只要失败，立即惩罚回退至 OPEN 态
            if (state.compareAndSet(ChannelStatus.HALF_OPEN, ChannelStatus.OPEN)) {
                lastStateChangeTimestamp.set(System.currentTimeMillis());
                log.warn("断路器 [{}] 半开探活遭遇失败，惩罚性回退至 OPEN 熔断状态", name);
            }
            return;
        }

        if (current == ChannelStatus.CLOSED || current == ChannelStatus.HEALTHY) {
            // 检查连续失败阈值或滑动窗口失败率阈值
            boolean trip = false;
            if (fails >= consecutiveFailureThreshold) {
                trip = true;
                log.warn("断路器 [{}] 达到连续失败阈值 [{}], 触发熔断跳闸", name, fails);
            } else if (metrics.getRecordedSampleCount() >= minimumNumberOfCalls) {
                double rate = metrics.getFailureRate();
                if (rate >= failureRateThreshold) {
                    trip = true;
                    log.warn("断路器 [{}] 滑动窗口失败率 [{}] 超过阈值 [{}], 触发熔断跳闸", name, rate, failureRateThreshold);
                }
            }

            if (trip && state.compareAndSet(current, ChannelStatus.OPEN)) {
                lastStateChangeTimestamp.set(System.currentTimeMillis());
            }
        }
    }

    public ChannelStatus getCurrentState() {
        return state.get();
    }
}
