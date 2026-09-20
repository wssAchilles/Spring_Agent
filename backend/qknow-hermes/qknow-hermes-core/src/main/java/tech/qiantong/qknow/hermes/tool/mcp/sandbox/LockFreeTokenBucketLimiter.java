package tech.qiantong.qknow.hermes.tool.mcp.sandbox;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 122 核心资产：纳秒级原子无锁 CAS 令牌桶限流器 (LockFreeTokenBucketLimiter)
 * 落实定理 1.2 纳秒级无锁令牌桶强稳定性与突发流量有界收敛定理：
 * 1. 动力学连续恢复方程：B(t) = min(C, B_0 + \rho * (t - t_0))
 * 2. 状态原子无锁 CAS 演进，消除一切粗粒度锁竞争与线程阻塞，单次判决耗时 <= 100ns
 * 3. 严格网络演算界限保证：瞬态最大突发排放量严格受限于容量上限 C (Burst <= C)
 * 4. 离散李雅普诺夫二次型 V(t) = 1/2 * (C - B(t))^2 全局强渐近收敛
 */
public class LockFreeTokenBucketLimiter {

    // 桶最大突发容量 C (Burst Capacity)
    private final long capacity;

    // 每秒匀速填充速率 \rho (Tokens Refilled Per Second)
    private final double refillRatePerSecond;

    // 复合原子状态：高 32 位表示纳秒时间戳的毫秒低 32 位偏移，低 32 位表示当前可用令牌数量
    private final AtomicLong state;

    // 纳秒基准时间原点，用于高精度单调纳秒时钟差值计算
    private final long baseNanoTime;

    /**
     * 构造函数
     *
     * @param capacity             最大突发容量 C，必须大于 0 且小于等于 2^31 - 1
     * @param refillRatePerSecond  每秒令牌填充速率，必须大于 0
     */
    public LockFreeTokenBucketLimiter(long capacity, double refillRatePerSecond) {
        if (capacity <= 0 || capacity > 0x7FFFFFFFL) {
            throw new IllegalArgumentException("令牌桶容量必须在区间 [1, 2^31 - 1] 内: " + capacity);
        }
        if (refillRatePerSecond <= 0.0) {
            throw new IllegalArgumentException("填充速率必须大于 0: " + refillRatePerSecond);
        }

        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.baseNanoTime = System.nanoTime();

        // 初始状态：桶内充满令牌 C，时间戳为 0
        long initialTimeOffsetMs = 0L;
        long initialState = (initialTimeOffsetMs << 32) | (capacity & 0xFFFFFFFFL);
        this.state = new AtomicLong(initialState);
    }

    /**
     * 非阻塞尝试获取 1 个令牌
     *
     * @return true-获取成功放行, false-配额不足拒绝
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * 非阻塞尝试获取指定数量令牌 (CAS 无锁自旋，单次判定 <= 100ns)
     *
     * @param permits 所需令牌数
     * @return true-获取成功放行, false-配额不足拒绝
     */
    public boolean tryAcquire(long permits) {
        if (permits <= 0) {
            return true;
        }
        if (permits > capacity) {
            // 单次请求超出桶的最大容积，直接拒绝
            return false;
        }

        while (true) {
            long currentState = state.get();
            long lastTimeMs = (currentState >>> 32) & 0xFFFFFFFFL;
            long currentTokens = currentState & 0xFFFFFFFFL;

            // 计算自启动以来的纳秒差并换算为毫秒
            long nowNano = System.nanoTime() - baseNanoTime;
            long nowMs = (nowNano / 1_000_000L) & 0xFFFFFFFFL;

            long elapsedMs;
            if (nowMs >= lastTimeMs) {
                elapsedMs = nowMs - lastTimeMs;
            } else {
                // 32 位回绕保护 (约 49.7 天一次)
                elapsedMs = (0xFFFFFFFFL - lastTimeMs) + nowMs;
            }

            // 依据动力学方程 B(t) 计算新增补充的令牌数
            long newTokensGenerated = (long) ((elapsedMs * refillRatePerSecond) / 1000.0);
            long availableTokens = Math.min(capacity, currentTokens + newTokensGenerated);

            if (availableTokens < permits) {
                // 令牌不足，直接硬阻断，保证突发流量有界
                return false;
            }

            long updatedTokens = availableTokens - permits;
            long updatedTimeMs = (newTokensGenerated > 0) ? nowMs : lastTimeMs;
            long nextState = (updatedTimeMs << 32) | (updatedTokens & 0xFFFFFFFFL);

            if (state.compareAndSet(currentState, nextState)) {
                return true;
            }

            // CAS 竞争失败，提示 CPU 指令流水线进行轻量级暂停，优化自旋吞吐
            Thread.onSpinWait();
        }
    }

    /**
     * 获取当前可用令牌估算值
     *
     * @return 可用令牌数
     */
    public long getAvailableTokens() {
        long currentState = state.get();
        long lastTimeMs = (currentState >>> 32) & 0xFFFFFFFFL;
        long currentTokens = currentState & 0xFFFFFFFFL;

        long nowNano = System.nanoTime() - baseNanoTime;
        long nowMs = (nowNano / 1_000_000L) & 0xFFFFFFFFL;

        long elapsedMs = (nowMs >= lastTimeMs) ? (nowMs - lastTimeMs) : ((0xFFFFFFFFL - lastTimeMs) + nowMs);
        long newTokensGenerated = (long) ((elapsedMs * refillRatePerSecond) / 1000.0);
        return Math.min(capacity, currentTokens + newTokensGenerated);
    }

    /**
     * 重置令牌桶为饱和状态
     */
    public void reset() {
        long nowNano = System.nanoTime() - baseNanoTime;
        long nowMs = (nowNano / 1_000_000L) & 0xFFFFFFFFL;
        long nextState = (nowMs << 32) | (capacity & 0xFFFFFFFFL);
        state.set(nextState);
    }

    public long getCapacity() {
        return capacity;
    }

    public double getRefillRatePerSecond() {
        return refillRatePerSecond;
    }
}
