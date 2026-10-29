package tech.qiantong.qknow.hermes.agent.harness.offline;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 离线受控实验成本与耗时硬件级累加熔断器 (Offline Budget Circuit Breaker)。
 *
 * <p>遵循阶段四安全契约，监控批次 Token 消耗（$\le 80,000$ tokens）、
 * 累计成本（$\le 0.15$ CNY）与累计耗时（$\le 120,000$ ms），
 * 一旦触碰天花板立即硬熔断，严防异常死循环导致账单或资源失控。</p>
 */
public final class OfflineBudgetCircuitBreaker {

    public static final long MAX_BATCH_TOKENS = 80_000L;
    public static final double MAX_BATCH_COST_CNY = 0.15;
    public static final long MAX_BATCH_DURATION_MS = 120_000L;

    private final AtomicLong cumulativeTokens = new AtomicLong(0L);
    private final AtomicReference<Double> cumulativeCostCny = new AtomicReference<>(0.0);
    private final long startTimestampMs;

    public OfflineBudgetCircuitBreaker() {
        this.startTimestampMs = System.currentTimeMillis();
    }

    /**
     * 累加单次调用消耗并执行硬性熔断断言。
     */
    public void trackAndAssert(long callTokens, double callCostCny) {
        long currentTokens = cumulativeTokens.addAndGet(Math.max(0L, callTokens));
        double currentCost = cumulativeCostCny.accumulateAndGet(Math.max(0.0, callCostCny), Double::sum);
        long elapsedMs = System.currentTimeMillis() - startTimestampMs;

        if (currentTokens > MAX_BATCH_TOKENS) {
            throw new IllegalStateException(String.format(
                    "BUDGET_CIRCUIT_BREAKER_TRIGGERED: 批次累计 Token (%d) 超出红线上限 (%d)",
                    currentTokens, MAX_BATCH_TOKENS));
        }

        if (currentCost > MAX_BATCH_COST_CNY) {
            throw new IllegalStateException(String.format(
                    "BUDGET_CIRCUIT_BREAKER_TRIGGERED: 批次累计成本 (%.4f CNY) 超出红线上限 (%.4f CNY)",
                    currentCost, MAX_BATCH_COST_CNY));
        }

        if (elapsedMs > MAX_BATCH_DURATION_MS) {
            throw new IllegalStateException(String.format(
                    "BUDGET_CIRCUIT_BREAKER_TRIGGERED: 批次总耗时 (%d ms) 超出超时熔断阈值 (%d ms)",
                    elapsedMs, MAX_BATCH_DURATION_MS));
        }
    }

    public long getCumulativeTokens() {
        return cumulativeTokens.get();
    }

    public double getCumulativeCostCny() {
        return cumulativeCostCny.get();
    }

    public long getElapsedDurationMs() {
        return System.currentTimeMillis() - startTimestampMs;
    }
}
