package tech.qiantong.qknow.ai.consensus.model;

/**
 * 经过拜占庭校验的提案实体包装
 */
public record InspectedProposal(
        WorkerProposal proposal,
        ByzantineFaultType faultType,
        String violationDetail,
        float[] embedding1536,
        double reputationWeight
) {
    public boolean isHonest() {
        return faultType == ByzantineFaultType.NONE;
    }
}
