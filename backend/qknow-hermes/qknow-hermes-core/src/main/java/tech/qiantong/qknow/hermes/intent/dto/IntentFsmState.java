package tech.qiantong.qknow.hermes.intent.dto;

/**
 * 确定性意图有限状态机 5 态收敛拓扑枚举
 * 严格遵照定理 1.3 确定性有限状态机李雅普诺夫收敛与有限步意图消除不变量
 */
public enum IntentFsmState {
    INITIAL_PARSING,
    AMBIGUITY_DETECTED,
    ACTIVE_CLARIFYING,
    SLOT_CONVERGED,
    CONFIRMED_EXECUTION
}
