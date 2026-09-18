package tech.qiantong.qknow.hermes.agent.swarm.dto;

import java.util.Map;

/**
 * 增量上下文切片 Record
 * 仅包含核心意图、增量键值状态与前序处理结论，实现 >= 80% 体积压缩
 */
public record ContextSliceBO(
        String userGoalSummary,
        Map<String, Object> stateDeltaMap,
        String handoffInstruction,
        long creationTimestamp
) {
    public ContextSliceBO {
        if (userGoalSummary == null) {
            throw new IllegalArgumentException("userGoalSummary must not be null");
        }
        stateDeltaMap = stateDeltaMap != null ? Map.copyOf(stateDeltaMap) : Map.of();
    }
}
