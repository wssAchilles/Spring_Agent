package tech.qiantong.qknow.hermes.agent.harness;

/**
 * 工具选择与执行治理策略契约。
 */
public record ToolGuidancePolicyRecord(
        boolean autoToolSelectionEnabled,
        boolean hoareVerificationEnabled,
        boolean twoPhaseApprovalEnabled
) {
    public String canonical() {
        return autoToolSelectionEnabled + "\u001f" + hoareVerificationEnabled + "\u001f" + twoPhaseApprovalEnabled;
    }

    public static ToolGuidancePolicyRecord defaults() {
        return new ToolGuidancePolicyRecord(true, false, false);
    }
}
