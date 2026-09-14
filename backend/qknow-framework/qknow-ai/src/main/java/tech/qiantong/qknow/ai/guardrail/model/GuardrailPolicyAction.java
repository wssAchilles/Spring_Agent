package tech.qiantong.qknow.ai.guardrail.model;

/**
 * 护栏处置动作枚举
 *
 * @author qknow
 */
public enum GuardrailPolicyAction {
    /** 正常放行 */
    PERMIT,
    /** 脱敏重写 (如打码 PII 或清洗幽灵引用) */
    REDACTED_REWRITE,
    /** 安全拒绝 (Fail-Close 阻断或越狱拒答) */
    SAFE_REFUSAL,
    /** 违规预警降级 (注入免责警示横幅) */
    ALERT_DEGRADATION
}
