package tech.qiantong.qknow.hermes.flow.hitl;

/**
 * 人机审批裁决枚举
 */
public enum HitlApprovalDecision {
    /**
     * 批准通过：工作流继续沿主干推进
     */
    APPROVED,

    /**
     * 明确拒绝：工作流终止或执行拒绝分支
     */
    REJECTED,

    /**
     * 修正批准：人工携带修正后的状态变量推进
     */
    MODIFIED,

    /**
     * 超时作废：审批超过 TTL 自动熔断撤销
     */
    EXPIRED
}
