package tech.qiantong.qknow.hermes.agent.harness;

import java.util.List;
import java.util.Objects;

/**
 * 子智能体拓扑契约。默认 SINGLE；SWARM/DEBATE/SUPERVISOR 仅作为显式配置，
 * 不因该 Record 存在而自动启用。
 */
public record SubagentsTopologyRecord(
        HarnessOrchestrationMode orchestrationMode,
        List<String> workerRoles,
        boolean isolatedContextEnabled
) {
    public SubagentsTopologyRecord {
        orchestrationMode = orchestrationMode == null ? HarnessOrchestrationMode.SINGLE : orchestrationMode;
        workerRoles = workerRoles == null ? List.of() : List.copyOf(workerRoles);
        for (String role : workerRoles) {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("workerRoles 不能包含空角色");
            }
        }
    }

    public String canonical() {
        return orchestrationMode.name() + "\u001f" + String.join(",", workerRoles)
                + "\u001f" + isolatedContextEnabled;
    }

    public static SubagentsTopologyRecord singleAgent() {
        return new SubagentsTopologyRecord(HarnessOrchestrationMode.SINGLE, List.of(), true);
    }

    /**
     * 显式枚举：不再让字符串模式在 Agent 执行链中漂移。
     */
    public enum HarnessOrchestrationMode {
        SINGLE,
        SWARM,
        DEBATE,
        SUPERVISOR
    }
}
