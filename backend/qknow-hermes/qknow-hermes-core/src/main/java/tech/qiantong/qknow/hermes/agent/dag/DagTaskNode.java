package tech.qiantong.qknow.hermes.agent.dag;

import java.util.List;
import java.util.Map;

/**
 * 强类型 DAG 任务节点
 */
public record DagTaskNode(
        String taskId,
        String objective,
        String requiredCapability,
        List<String> dependencies,
        int timeoutSeconds,
        Map<String, Object> metadata
) {
    public DagTaskNode {
        dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
    }
}
