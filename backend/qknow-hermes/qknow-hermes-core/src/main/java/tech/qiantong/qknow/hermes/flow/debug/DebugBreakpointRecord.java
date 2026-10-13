package tech.qiantong.qknow.hermes.flow.debug;

import java.io.Serializable;
import java.util.Objects;

/**
 * 调试断点定义 Record (不可变)
 * 封装断点目标节点、SpEL 命中条件、触发计数与生效状态
 */
public record DebugBreakpointRecord(
        String breakpointId,
        String flowId,
        String targetNodeId,
        String spelCondition, // 可选 SpEL 条件表达式
        int hitCountLimit,    // 命中次数上限 (0 表示无限制)
        boolean enabled,
        long createdAtMs
) implements Serializable {

    public DebugBreakpointRecord {
        Objects.requireNonNull(breakpointId, "breakpointId 不能为空");
        Objects.requireNonNull(flowId, "flowId 不能为空");
        Objects.requireNonNull(targetNodeId, "targetNodeId 不能为空");
    }
}
