package tech.qiantong.qknow.ai.guardrail.model;

/**
 * 护栏违规类型枚举
 *
 * @author qknow
 */
public enum GuardrailViolationType {
    /** 无违规，合规放行 */
    NONE,
    /** 敏感个人信息泄露 (PII) */
    PII_EXPOSURE,
    /** 提示词越狱与对抗注入 */
    PROMPT_INJECTION,
    /** 输出触碰政治红线/涉暴/涉黄敏感词 (Fail-Close) */
    REDLINE_SENSITIVE,
    /** 幽灵引用 (Phantom Citations，引用不存在的切片) */
    PHANTOM_CITATION,
    /** 事实忠实度过低 (Faithfulness Low，凭空捏造事实) */
    FAITHFULNESS_LOW
}
