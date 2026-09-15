package tech.qiantong.qknow.ai.speculative;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 63: 端侧/边缘轻量确定性符号有限状态机 (Symbolic FSM)
 * <p>
 * 基于定理 1.1，管理 IDLE -> TYPING -> DWELLING -> SPECULATING -> COMMITTED / ABORTED 五态流转。
 * 纯确定性符号推理，纳秒级状态判定，全系统绝无本地大模型！
 */
public class LightweightSymbolicFsm {

    public enum State {
        IDLE,
        TYPING,
        DWELLING,
        SPECULATING,
        COMMITTED,
        ABORTED
    }

    public static final long DEFAULT_DWELL_THRESHOLD_MS = 300L;

    private final AtomicReference<State> currentState = new AtomicReference<>(State.IDLE);
    private volatile long lastKeystrokeTime = 0L;
    private final List<SymbolicStateTransition> transitionHistory = new ArrayList<>();

    public State getCurrentState() {
        return currentState.get();
    }

    /**
     * 收到击键事件：流转至 TYPING
     */
    public synchronized State onKeystroke(long now) {
        State oldState = currentState.get();
        lastKeystrokeTime = now;
        if (oldState == State.IDLE || oldState == State.DWELLING || oldState == State.ABORTED) {
            transition(oldState, State.TYPING, "KEYSTROKE_RECEIVED", 0L, now);
        }
        return currentState.get();
    }

    /**
     * 检查击键停顿状态：若停顿达到最优停止阈值 tau* = 300ms，流转至 DWELLING
     */
    public synchronized State checkDwellTime(long now) {
        State oldState = currentState.get();
        if (oldState == State.TYPING) {
            long dwell = now - lastKeystrokeTime;
            if (dwell >= DEFAULT_DWELL_THRESHOLD_MS) {
                transition(oldState, State.DWELLING, "DWELL_TIME_EXCEEDED", dwell, now);
            }
        }
        return currentState.get();
    }

    /**
     * 触发投机执行：流转至 SPECULATING
     */
    public synchronized boolean triggerSpeculation(long now) {
        State oldState = currentState.get();
        if (oldState == State.DWELLING) {
            transition(oldState, State.SPECULATING, "SPECULATION_TRIGGERED", 0L, now);
            return true;
        }
        return false;
    }

    /**
     * 用户提交意图：流转至 COMMITTED
     */
    public synchronized void onCommit(long now) {
        State oldState = currentState.get();
        transition(oldState, State.COMMITTED, "USER_COMMITTED", 0L, now);
    }

    /**
     * 用户取消或输入漂移撤销：流转至 ABORTED
     */
    public synchronized void onAbort(long now) {
        State oldState = currentState.get();
        transition(oldState, State.ABORTED, "USER_ABORTED_OR_DRIFTED", 0L, now);
    }

    /**
     * 重置回空闲状态
     */
    public synchronized void reset(long now) {
        State oldState = currentState.get();
        transition(oldState, State.IDLE, "RESET_IDLE", 0L, now);
    }

    private void transition(State from, State to, String event, long dwellMs, long now) {
        currentState.set(to);
        transitionHistory.add(new SymbolicStateTransition(from.name(), to.name(), event, dwellMs, now));
    }

    public synchronized List<SymbolicStateTransition> getHistory() {
        return List.copyOf(transitionHistory);
    }
}
