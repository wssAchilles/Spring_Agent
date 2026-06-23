package tech.qiantong.qknow.hermes.tool.resilience;

public class ToolCircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int failureThreshold;
    private final long recoveryTimeout;
    private int failureCount;
    private State state;
    private long lastFailureTime;

    public ToolCircuitBreaker(int failureThreshold, long recoveryTimeout) {
        this.failureThreshold = failureThreshold;
        this.recoveryTimeout = recoveryTimeout;
        this.failureCount = 0;
        this.state = State.CLOSED;
        this.lastFailureTime = 0;
    }

    public State getState() {
        if (state == State.OPEN && System.currentTimeMillis() - lastFailureTime > recoveryTimeout) {
            state = State.HALF_OPEN;
        }
        return state;
    }

    public boolean allowCall() {
        State currentState = getState();
        if (currentState == State.CLOSED) return true;
        if (currentState == State.HALF_OPEN) return true;
        return false;
    }

    public void recordSuccess() {
        failureCount = 0;
        state = State.CLOSED;
    }

    public void recordFailure() {
        failureCount++;
        lastFailureTime = System.currentTimeMillis();
        if (failureCount >= failureThreshold) {
            state = State.OPEN;
        }
    }
}
