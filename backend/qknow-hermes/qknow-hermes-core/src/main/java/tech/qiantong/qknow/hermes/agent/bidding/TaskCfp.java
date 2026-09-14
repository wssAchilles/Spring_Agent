package tech.qiantong.qknow.hermes.agent.bidding;

/**
 * 标书定义 (Call For Proposals)
 */
public record TaskCfp(
        String cfpId,
        String taskId,
        String objective,
        String requiredCapability,
        String contextPayload,
        int timeoutSeconds
) {}
