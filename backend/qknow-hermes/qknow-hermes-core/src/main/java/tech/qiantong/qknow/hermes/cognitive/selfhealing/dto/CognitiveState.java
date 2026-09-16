package tech.qiantong.qknow.hermes.cognitive.selfhealing.dto;

/**
 * 认知推理内核自愈状态机生命周期枚举
 */
public enum CognitiveState {
    IDLE,
    PERCEIVING,
    REASONING,
    JUDGING,
    REFLECTING,
    SELF_HEALING,
    TERMINATED_SUCCESS,
    TERMINATED_DEGRADED_FALLBACK
}
