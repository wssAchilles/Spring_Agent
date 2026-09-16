package tech.qiantong.qknow.hermes.consensus.dto;

/**
 * BFT 拜占庭共识投票报文 Java 21 Record
 */
public record BftVoteMessage(
        String voteId,
        String sessionId,
        String agentId,
        BftConsensusPhase phase,
        String proposalHash,
        double weight,
        boolean approve,
        String signature,
        long timestamp
) {}
