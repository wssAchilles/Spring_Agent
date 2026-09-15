package tech.qiantong.qknow.ai.speculative;

import java.util.Objects;

/**
 * Phase 63: 轻量确定性符号状态转移元数据 (Record)
 */
public record SymbolicStateTransition(
        String sourceState,
        String targetState,
        String triggerEvent,
        long dwellTimeThresholdMs,
        long timestamp
) {
    public SymbolicStateTransition {
        Objects.requireNonNull(sourceState, "源状态不能为空");
        Objects.requireNonNull(targetState, "目标状态不能为空");
        Objects.requireNonNull(triggerEvent, "触发事件不能为空");
    }
}
