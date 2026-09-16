package tech.qiantong.qknow.hermes.causal.dto;

/**
 * 1000Hz 实时因果推演与干预总线单帧数据
 *
 * @param sequence      单调事件序列号
 * @param sessionId     会话标识
 * @param causalIntent  识别出的核心因果意图
 * @param action        最终干预动作
 * @param barrierMargin 控制屏障裕度
 * @param jitterFlag    时钟抖动标记
 * @param timestamp     时间戳 (毫秒)
 */
public record CausalInterventionEventFrame(
        long sequence,
        String sessionId,
        String causalIntent,
        AutonomousInterventionAction action,
        double barrierMargin,
        boolean jitterFlag,
        long timestamp
) {}
