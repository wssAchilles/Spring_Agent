package tech.qiantong.qknow.hermes.federation.dto;

/**
 * 主权合规裁决动作枚举
 */
public enum ComplianceVetoAction {
    /** 合规审查通过，无侵入直接放行 */
    APPROVED,
    /** 处于可自愈安全带内，经安全凸投影修正参数后放行 */
    MODIFIED_SAFE,
    /** 触碰绝对主权红线，一票否决熔断中止 */
    VETO_ABORTED
}
