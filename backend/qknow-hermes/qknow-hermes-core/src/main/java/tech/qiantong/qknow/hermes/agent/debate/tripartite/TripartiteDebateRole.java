package tech.qiantong.qknow.hermes.agent.debate.tripartite;

/**
 * 三方结构化博弈辩论角色定义
 */
public enum TripartiteDebateRole {
    /**
     * 提议架构师：提出初始方案、技术选型与实现主干
     */
    PROPOSER_ARCHITECT,

    /**
     * 红队对抗审计员：专职挖掘事实性漏洞、逻辑矛盾、安全风险与边界反事实极端场景
     */
    RED_TEAM_AUDITOR,

    /**
     * 中立仲裁法官：评估双方论据充分性，量化夏普利边际贡献，执行终局收敛裁决
     */
    NEUTRAL_ARBITER
}
