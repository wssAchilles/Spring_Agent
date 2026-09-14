package tech.qiantong.qknow.hermes.a2a.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 声明式多智能体工作流规约根模型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowDefinition(
        String workflowId,
        String name,
        String version,
        String description,
        List<WorkflowNode> nodes,
        List<WorkflowEdge> edges
) {
    public WorkflowDefinition {
        if (nodes == null) nodes = List.of();
        if (edges == null) edges = List.of();
    }
}
