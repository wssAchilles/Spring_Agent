package tech.qiantong.qknow.ai.consensus.model;

/**
 * 多智能体共识裁决最终输出
 */
public record ConsensusResult(
        String taskId,
        String consensusContent,
        boolean isConsensusAchieved,
        double consensusConfidence,
        String selectedMedoidWorkerId,
        int totalWorkers,
        int honestWorkers,
        int debateRounds,
        String resolutionType // FAST_QUORUM_MEDOID, FAST_QUORUM_DISCRETE, DEBATE_CONVERGED, DEBATE_STAGNATION_FORCED, MAX_ROUNDS_REACHED, FALLBACK_FAIL_OPEN, INSUFFICIENT_QUORUM
) {
    public static ConsensusResult failOpen(String taskId, String fallbackContent, int totalWorkers) {
        return new ConsensusResult(
                taskId,
                fallbackContent,
                false,
                0.0,
                null,
                totalWorkers,
                0,
                0,
                "FALLBACK_FAIL_OPEN"
        );
    }
}
