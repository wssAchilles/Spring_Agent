package tech.qiantong.qknow.hermes.streaming.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * DAG 执行拓扑全局状态快照 Java 21 Record
 * 聚合当前会话所有节点的即时状态、单步耗时与完成进度，支持前端断线无损重构
 */
public record DagExecutionSnapshot(
        String snapshotId,
        String sessionId,
        Map<String, String> nodeStates,
        Map<String, Long> nodeLatencies,
        int completedCount,
        int totalCount,
        long totalExecutionMicros,
        long timestamp
) implements Serializable {

    public DagExecutionSnapshot {
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId 不能为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (nodeStates == null) {
            nodeStates = Map.of();
        }
        if (nodeLatencies == null) {
            nodeLatencies = Map.of();
        }
    }

    public double progressPercentage() {
        if (totalCount <= 0) {
            return 100.0;
        }
        return Math.min(100.0, (completedCount * 100.0) / totalCount);
    }
}
