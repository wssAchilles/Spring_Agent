package tech.qiantong.qknow.hermes.a2a.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 声明式工作流拓扑节点模型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowNode(
        String nodeId,
        String name,
        String objective,
        String requiredCapability,
        int timeoutSeconds,
        boolean required
) {
    public WorkflowNode {
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 30;
        }
    }
}
