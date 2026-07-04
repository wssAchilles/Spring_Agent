package tech.qiantong.qknow.hermes.tool.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 工具调用断路器 — 线程安全版本
 * [溯源] 算法优化指南 §4.4: "断路器线程安全 — P0"
 *
 * 三态状态机: CLOSED → OPEN → HALF_OPEN → CLOSED
 * 使用 AtomicInteger/AtomicReference/AtomicLock-free CAS 操作保证并发安全。
 */
public class ToolCircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int failureThreshold;
    private final long recoveryTimeout;
    private final AtomicInteger failureCount;
    private final AtomicReference<State> state;
    private final AtomicLong lastFailureTime;

    public ToolCircuitBreaker(int failureThreshold, long recoveryTimeout) {
        this.failureThreshold = failureThreshold;
        this.recoveryTimeout = recoveryTimeout;
        this.failureCount = new AtomicInteger(0);
        this.state = new AtomicReference<>(State.CLOSED);
        this.lastFailureTime = new AtomicLong(0);
    }

    public State getState() {
        State currentState = state.get();
        if (currentState == State.OPEN
                && System.currentTimeMillis() - lastFailureTime.get() > recoveryTimeout) {
            state.compareAndSet(State.OPEN, State.HALF_OPEN);
            return State.HALF_OPEN;
        }
        return currentState;
    }

    public boolean allowCall() {
        State currentState = getState();
        return currentState == State.CLOSED || currentState == State.HALF_OPEN;
    }

    public void recordSuccess() {
        failureCount.set(0);
        state.set(State.CLOSED);
    }

    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        int count = failureCount.incrementAndGet();
        if (count >= failureThreshold) {
            state.set(State.OPEN);
        }
    }

    public int getFailureCount() {
        return failureCount.get();
    }
}
