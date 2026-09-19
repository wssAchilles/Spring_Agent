package tech.qiantong.qknow.hermes.a2a.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 增强型声明式工作流有向拓扑边模型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DslWorkflowEdge(
        String edgeId,
        String fromNodeId,
        String toNodeId,
        String condition,
        boolean isLoopEdge,
        int maxIterations,
        String exitCondition
) {
    public DslWorkflowEdge {
        if (edgeId == null || edgeId.isBlank()) {
            edgeId = fromNodeId + "->" + toNodeId;
        }
    }
}
