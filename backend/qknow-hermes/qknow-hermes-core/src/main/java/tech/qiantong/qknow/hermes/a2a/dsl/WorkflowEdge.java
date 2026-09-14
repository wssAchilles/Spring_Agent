package tech.qiantong.qknow.hermes.a2a.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 声明式工作流拓扑依赖有向边模型 (fromNodeId -> toNodeId)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowEdge(
        String fromNodeId,
        String toNodeId
) {
}
