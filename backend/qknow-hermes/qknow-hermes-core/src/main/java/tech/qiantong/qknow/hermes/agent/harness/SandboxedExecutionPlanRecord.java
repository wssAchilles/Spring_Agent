package tech.qiantong.qknow.hermes.agent.harness;

import java.util.List;

/**
 * 沙箱执行计划契约。只允许声明式参数槽位，永久禁止任意脚本热加载。
 */
public record SandboxedExecutionPlanRecord(
        List<String> declaredParameterSlots,
        boolean allowArbitraryScripts
) {
    public SandboxedExecutionPlanRecord {
        declaredParameterSlots = declaredParameterSlots == null ? List.of() : List.copyOf(declaredParameterSlots);
        for (String slot : declaredParameterSlots) {
            if (slot == null || slot.isBlank()) {
                throw new IllegalArgumentException("declaredParameterSlots 不能包含空槽位");
            }
        }
        if (allowArbitraryScripts) {
            throw new IllegalArgumentException("allowArbitraryScripts 必须为 false：禁止未受控脚本执行");
        }
    }

    public String canonical() {
        return String.join(",", declaredParameterSlots) + "\u001f" + false;
    }

    public static SandboxedExecutionPlanRecord declarative() {
        return new SandboxedExecutionPlanRecord(List.of(), false);
    }
}
