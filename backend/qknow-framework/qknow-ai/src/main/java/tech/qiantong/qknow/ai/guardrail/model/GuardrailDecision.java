package tech.qiantong.qknow.ai.guardrail.model;

/**
 * 护栏判定统一决策载荷
 *
 * @param permitted 是否允许主链路继续放行
 * @param action 采纳的处置策略动作
 * @param violationType 识别出的违规类型
 * @param processedText 处理后交付的文本（脱敏、清洗或拒绝模板）
 * @param reason 决策详细原因描述
 * @param latencyMicros 护栏判定耗时（微秒）
 *
 * @author qknow
 */
public record GuardrailDecision(
        boolean permitted,
        GuardrailPolicyAction action,
        GuardrailViolationType violationType,
        String processedText,
        String reason,
        long latencyMicros
) {
    public static GuardrailDecision permit(String text, long latencyMicros) {
        return new GuardrailDecision(true, GuardrailPolicyAction.PERMIT, GuardrailViolationType.NONE, text, "安全合规放行", latencyMicros);
    }

    public static GuardrailDecision redact(String redactedText, GuardrailViolationType violationType, String reason, long latencyMicros) {
        return new GuardrailDecision(true, GuardrailPolicyAction.REDACTED_REWRITE, violationType, redactedText, reason, latencyMicros);
    }

    public static GuardrailDecision refuse(String refusalText, GuardrailViolationType violationType, String reason, long latencyMicros) {
        return new GuardrailDecision(false, GuardrailPolicyAction.SAFE_REFUSAL, violationType, refusalText, reason, latencyMicros);
    }

    public static GuardrailDecision degrade(String degradedText, GuardrailViolationType violationType, String reason, long latencyMicros) {
        return new GuardrailDecision(true, GuardrailPolicyAction.ALERT_DEGRADATION, violationType, degradedText, reason, latencyMicros);
    }
}
