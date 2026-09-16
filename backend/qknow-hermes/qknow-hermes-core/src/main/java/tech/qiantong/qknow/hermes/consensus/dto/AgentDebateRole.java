package tech.qiantong.qknow.hermes.consensus.dto;

/**
 * 智能体博弈辩论角色枚举
 */
public enum AgentDebateRole {
    PROPOSER,          // 正方提案者 (积极提出主张与方案)
    OPPONENT,          // 反方质询者 (深入挖掘漏洞、风险与反例)
    FACT_VERIFIER,     // 独立事实核查者 (检索事实、校验数值与基准对齐)
    NEUTRAL_ARBITRATOR // 中立仲裁者 (统筹共识、权衡损益与仲裁终审)
}
