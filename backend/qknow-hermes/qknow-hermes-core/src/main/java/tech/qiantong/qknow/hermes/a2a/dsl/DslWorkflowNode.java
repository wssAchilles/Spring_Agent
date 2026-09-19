package tech.qiantong.qknow.hermes.a2a.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 增强型声明式工作流拓扑节点模型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DslWorkflowNode(
        String nodeId,
        String name,
        DslNodeType nodeType,
        String objective,
        String requiredCapability,
        String mcpToolName,
        int timeoutSeconds,
        Map<String, Object> config
) {
    public DslWorkflowNode {
        if (nodeType == null) {
            nodeType = DslNodeType.TASK;
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 30;
        }
        if (config == null) {
            config = Map.of();
        } else {
            config = Map.copyOf(config);
        }
    }
}
