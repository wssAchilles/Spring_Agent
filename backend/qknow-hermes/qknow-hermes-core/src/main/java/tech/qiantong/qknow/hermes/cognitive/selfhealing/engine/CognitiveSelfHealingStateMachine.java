package tech.qiantong.qknow.hermes.cognitive.selfhealing.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.cognitive.selfhealing.dto.CognitiveState;

import java.util.ArrayList;
import java.util.List;

/**
 * Hermes 认知推理内核自愈状态机
 * 负责有限状态机生命周期调度、反思死锁检测与反事实变异自愈
 */
@Slf4j
@Component
public class CognitiveSelfHealingStateMachine {

    public static final double DEADLOCK_SIMILARITY_THRESHOLD = 0.90;
    public static final double PASS_SCORE_THRESHOLD = 0.80;
    public static final int MAX_ATTEMPTS = 4;

    private volatile CognitiveState currentState = CognitiveState.IDLE;
    private final List<double[]> attemptEmbeddings = new ArrayList<>();
    private int currentAttempt = 0;
    private boolean selfHealingTriggered = false;

    public synchronized boolean transitionTo(CognitiveState nextState) {
        if (!isValidTransition(currentState, nextState)) {
            log.warn("非法状态机跃迁请求: {} -> {}", currentState, nextState);
            return false;
        }
        log.info("Hermes 认知状态机跃迁: {} -> {}", currentState, nextState);
        this.currentState = nextState;
        return true;
    }

    private boolean isValidTransition(CognitiveState from, CognitiveState to) {
        if (from == to) return true;
        return switch (from) {
            case IDLE -> to == CognitiveState.PERCEIVING;
            case PERCEIVING -> to == CognitiveState.REASONING;
            case REASONING -> to == CognitiveState.JUDGING;
            case JUDGING -> to == CognitiveState.REFLECTING || to == CognitiveState.SELF_HEALING
                    || to == CognitiveState.TERMINATED_SUCCESS || to == CognitiveState.TERMINATED_DEGRADED_FALLBACK;
            case REFLECTING -> to == CognitiveState.REASONING || to == CognitiveState.SELF_HEALING
                    || to == CognitiveState.TERMINATED_DEGRADED_FALLBACK;
            case SELF_HEALING -> to == CognitiveState.REASONING || to == CognitiveState.TERMINATED_DEGRADED_FALLBACK;
            case TERMINATED_SUCCESS, TERMINATED_DEGRADED_FALLBACK -> to == CognitiveState.IDLE;
        };
    }

    /**
     * 检测反思中的语义固着死锁：未达标 + 连续两轮语义相似度 >= 0.90
     */
    public boolean checkDeadlock(double[] currEmbedding, double[] prevEmbedding, double passScore) {
        if (currEmbedding == null || prevEmbedding == null || currEmbedding.length != 1536 || prevEmbedding.length != 1536) {
            return false;
        }
        if (passScore >= PASS_SCORE_THRESHOLD) {
            return false;
        }
        double dot = 0.0;
        for (int i = 0; i < 1536; i++) {
            dot += currEmbedding[i] * prevEmbedding[i];
        }
        boolean deadlock = dot >= DEADLOCK_SIMILARITY_THRESHOLD;
        if (deadlock) {
            this.selfHealingTriggered = true;
            log.warn("检测到认知语义固着死锁! 相似度={}>={}, 得分={}, 触发自愈切入",
                    dot, DEADLOCK_SIMILARITY_THRESHOLD, passScore);
        }
        return deadlock;
    }

    /**
     * 自愈变异算子：注入反事实负向约束与思维链纠偏
     */
    public String applySelfHealingMutation(String currentPrompt, String failureReason, int attemptIndex) {
        this.selfHealingTriggered = true;
        StringBuilder sb = new StringBuilder(currentPrompt);
        sb.append("\n\n### [Hermes 认知自愈干涉指令 - 第 ").append(attemptIndex).append(" 轮自愈变异]\n");
        sb.append("- 失败根因诊断: ").append(failureReason != null ? failureReason : "连续回答出现语义固着死锁").append("\n");
        sb.append("- 反事实负向约束: 严禁沿用上一轮的论证路径与假设前提，必须颠覆既有假设，探索正交备选方案。\n");
        sb.append("- 思维链修正: 优先从边界条件、异常分支与安全防御策略切入。\n");
        return sb.toString();
    }

    public CognitiveState getCurrentState() {
        return currentState;
    }

    public boolean isTerminal() {
        return currentState == CognitiveState.TERMINATED_SUCCESS || currentState == CognitiveState.TERMINATED_DEGRADED_FALLBACK;
    }

    public boolean isSelfHealingTriggered() {
        return selfHealingTriggered;
    }

    public synchronized void recordAttemptEmbedding(double[] embedding) {
        if (embedding != null && embedding.length == 1536) {
            attemptEmbeddings.add(embedding);
            currentAttempt++;
        }
    }

    public int getCurrentAttempt() {
        return currentAttempt;
    }

    public double[] getPreviousEmbedding() {
        int sz = attemptEmbeddings.size();
        return sz >= 2 ? attemptEmbeddings.get(sz - 2) : null;
    }

    public double[] getLatestEmbedding() {
        int sz = attemptEmbeddings.size();
        return sz >= 1 ? attemptEmbeddings.get(sz - 1) : null;
    }

    public synchronized void reset() {
        this.currentState = CognitiveState.IDLE;
        this.attemptEmbeddings.clear();
        this.currentAttempt = 0;
        this.selfHealingTriggered = false;
    }
}
