package tech.qiantong.qknow.hermes.flow.debug;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * 时间旅行历史单步快照 Record (不可变)
 * 记录工作流每一步执行时的节点状态、输入输出变量与全局上下文快照
 */
public record TimeTravelStepRecord(
        long stepSequence,
        String runtimeId,
        String nodeId,
        String nodeName,
        String status,
        Map<String, Object> inputVariablesSnapshot,
        Map<String, Object> outputVariablesSnapshot,
        Map<String, Object> fullContextSnapshot,
        long executionDurationMs,
        long timestampMs
) implements Serializable {

    public TimeTravelStepRecord {
        Objects.requireNonNull(runtimeId, "runtimeId 不能为空");
        Objects.requireNonNull(nodeId, "nodeId 不能为空");
        inputVariablesSnapshot = (inputVariablesSnapshot == null) ? Map.of() : Map.copyOf(inputVariablesSnapshot);
        outputVariablesSnapshot = (outputVariablesSnapshot == null) ? Map.of() : Map.copyOf(outputVariablesSnapshot);
        fullContextSnapshot = (fullContextSnapshot == null) ? Map.of() : Map.copyOf(fullContextSnapshot);
    }
}
