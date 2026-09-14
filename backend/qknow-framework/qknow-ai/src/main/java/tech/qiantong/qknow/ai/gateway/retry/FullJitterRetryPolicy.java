package tech.qiantong.qknow.ai.gateway.retry;

import lombok.Getter;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Phase 28: 遵循 AWS Marc Brooker 经典论文的 Full Jitter 指数抖动重试退避策略
 * 消除高并发网络瞬态故障下的惊群雪崩效应 (Theorem 1.1)
 */
@Getter
public class FullJitterRetryPolicy {

    private final long baseIntervalMillis;
    private final long maxIntervalMillis;
    private final int maxAttempts;

    public FullJitterRetryPolicy(long baseIntervalMillis, long maxIntervalMillis, int maxAttempts) {
        this.baseIntervalMillis = baseIntervalMillis;
        this.maxIntervalMillis = maxIntervalMillis;
        this.maxAttempts = maxAttempts;
    }

    public FullJitterRetryPolicy() {
        this(200L, 3000L, 3);
    }

    /**
     * 计算第 attempt 次重试的随机睡眠退避时长 (毫秒)
     * 公式: V_ceiling = min(maxInterval, baseInterval * 2^attempt)
     *       T_sleep ~ Uniform(0, V_ceiling)
     *
     * @param attempt 当前重试次数 (0, 1, 2, ...)
     * @return 严格全随机抖动等待毫秒数
     */
    public long calculateSleepMillis(int attempt) {
        if (attempt <= 0) {
            return 0L;
        }
        // 防止移位过大发生 long 溢出
        int safeShift = Math.min(attempt, 30);
        long exponentialFactor = 1L << safeShift;
        long ceiling = Math.min(maxIntervalMillis, baseIntervalMillis * exponentialFactor);

        if (ceiling <= 0L) {
            return 0L;
        }
        return ThreadLocalRandom.current().nextLong(0, ceiling + 1);
    }

    /**
     * 判断给定异常是否属于可重试/可倒换的瞬态可用性异常
     */
    public static boolean isRetryableException(Throwable t) {
        if (t == null) {
            return false;
        }
        String msg = t.getMessage() != null ? t.getMessage() : "";
        String exName = t.getClass().getSimpleName();

        // 429 限流, 502, 503, 504 服务端不可用, 网络超时
        return msg.contains("429") || msg.contains("502") || msg.contains("503") || msg.contains("504")
                || msg.contains("Too Many Requests") || msg.contains("Service Unavailable")
                || msg.contains("Timeout") || msg.contains("timeout")
                || exName.contains("Timeout") || exName.contains("ConnectException");
    }
}
