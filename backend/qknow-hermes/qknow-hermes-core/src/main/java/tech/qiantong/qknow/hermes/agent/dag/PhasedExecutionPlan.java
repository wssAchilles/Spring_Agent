package tech.qiantong.qknow.hermes.agent.dag;

import java.util.List;

/**
 * 经拓扑排序后的分层阶段任务图
 */
public record PhasedExecutionPlan(
        List<List<DagTaskNode>> phases,
        int totalTasks
) {
    public boolean isEmpty() {
        return phases == null || phases.isEmpty();
    }
}
