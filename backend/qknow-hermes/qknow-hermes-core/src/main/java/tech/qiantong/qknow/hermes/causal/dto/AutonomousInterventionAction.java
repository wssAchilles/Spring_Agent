package tech.qiantong.qknow.hermes.causal.dto;

/**
 * 自主干预决策动作枚举
 */
public enum AutonomousInterventionAction {
    /** 正常安全，原动作直接放行 */
    PASS_DIRECT,
    /** 存在越界风险，执行最小干预二次规划 (QP) 正交超平面软投影修补 */
    INTERVENE_SOFT_PROJECT,
    /** 原始方案高危不可修补，自主无缝切换至沙盘安全替代分支 */
    INTERVENE_ALTERNATIVE_BRANCH,
    /** 全部候选分支均严重越界违规，执行紧急一票熔断终止 */
    EMERGENCY_VETO_HALT
}
