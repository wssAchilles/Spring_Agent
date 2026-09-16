package tech.qiantong.qknow.hermes.synergy.dto;

/**
 * 智能体动态分层层级枚举
 */
public enum AgentHierarchyLayer {
    /** 战略决策层：宏观意图把控、目标分解与主权裁决 */
    STRATEGIC_DIRECTOR,
    /** 战术协调层：局部拓扑路由、资源竞标与冲突预消解 */
    TACTICAL_COORDINATOR,
    /** 业务执行层：专业工具调用、微服务交互与数据吞吐 */
    OPERATIONAL_EXECUTOR,
    /** 质检验真层：事实核验、合规屏障审计与收据存证 */
    QUALITY_VERIFIER
}
