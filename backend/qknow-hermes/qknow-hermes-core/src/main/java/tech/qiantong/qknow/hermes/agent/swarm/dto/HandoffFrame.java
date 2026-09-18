package tech.qiantong.qknow.hermes.agent.swarm.dto;

/**
 * 单次交接记录帧
 */
public record HandoffFrame(
        String sourceAgentId,
        String targetAgentId,
        ContextSliceBO slice,
        long timestamp
) {
    public HandoffFrame {
        if (sourceAgentId == null || targetAgentId == null) {
            throw new IllegalArgumentException("sourceAgentId and targetAgentId must not be null");
        }
    }
}
