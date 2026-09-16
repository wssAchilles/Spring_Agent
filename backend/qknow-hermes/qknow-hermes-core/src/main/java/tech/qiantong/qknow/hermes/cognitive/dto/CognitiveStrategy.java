package tech.qiantong.qknow.hermes.cognitive.dto;

/**
 * 认知推理策略级别枚举
 * 遵循 Phase 84 定理 1.1 自适应决策流形
 */
public enum CognitiveStrategy {
    /** 极速直接回答：零思考链展开，单轮直出 (DeepSeek-V3) */
    DIRECT_ANSWER,
    /** 轻量短思维链：1-2步启发式聚焦推理 (DeepSeek-V3) */
    SHORT_COT,
    /** 深度多步推理树：复杂规划与工具编排 (DeepSeek-R1) */
    DEEP_REASONING
}
