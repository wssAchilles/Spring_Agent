package tech.qiantong.qknow.hermes.synergy.dto;

/**
 * 1000Hz 实时认知协同总线单帧数据
 *
 * @param sequence      单调事件序列号
 * @param sessionId     会话标识
 * @param consensusPlan 共识决策方案
 * @param barrierMargin 控制屏障裕度
 * @param jitterFlag    时钟抖动标记
 * @param timestamp     时间戳 (毫秒)
 */
public record CognitiveSynergyEventFrame(
        long sequence,
        String sessionId,
        String consensusPlan,
        double barrierMargin,
        boolean jitterFlag,
        long timestamp
) {}
