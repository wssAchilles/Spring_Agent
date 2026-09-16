package tech.qiantong.qknow.hermes.agent.debate.dto;

/**
 * 争辩共识收敛状态枚举类 (Debate Consensus Status)
 * <p>
 * 定义多智能体黑板争辩网络中的收敛及终止状态。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public enum DebateConsensusStatus {

    /**
     * 正在争辩推进中 (Converging)
     */
    CONVERGING("正在收敛", false),

    /**
     * 多方达成完全共识 (Consensus Reached)
     */
    CONSENSUS_REACHED("完全共识达成", true),

    /**
     * 沙普利值加权多数派通过 (Majority Approved)
     */
    MAJORITY_APPROVED("多数派通过", true),

    /**
     * 熵减停滞，仲裁专家强制介入裁决 (Degraded Arbitration)
     */
    DEGRADED_ARBITRATION("仲裁专家终审裁决", true),

    /**
     * 超出最大轮次或看门狗硬超时终止 (Terminated by Watchdog)
     */
    TERMINATED_BY_WATCHDOG("看门狗硬超时终止", false);

    private final String description;
    private final boolean resolved;

    DebateConsensusStatus(String description, boolean resolved) {
        this.description = description;
        this.resolved = resolved;
    }

    public String getDescription() {
        return description;
    }

    public boolean isResolved() {
        return resolved;
    }
}
