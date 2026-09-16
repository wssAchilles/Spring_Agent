package tech.qiantong.qknow.hermes.consensus.dto;

/**
 * BFT 拜占庭多阶段共识状态枚举
 */
public enum BftConsensusPhase {
    PRE_PREPARE,
    PREPARE,
    COMMIT,
    COMMITTED,
    ABORTED
}
