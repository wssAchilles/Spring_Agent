package tech.qiantong.qknow.hermes.a2a.envelope;

/**
 * A2A (Agent-to-Agent) 通信协议信令动作枚举
 */
public enum A2AMessageType {
    CFP_SOLICIT("CFP_SOLICIT", "呼标征集"),
    BID_PROPOSE("BID_PROPOSE", "投标提议"),
    AWARD_ACCEPT("AWARD_ACCEPT", "授标接受"),
    TASK_EXECUTE("TASK_EXECUTE", "任务下发执行"),
    TASK_RESULT("TASK_RESULT", "任务结果响应"),
    CONSENSUS_VOTE("CONSENSUS_VOTE", "共识投票"),
    SAGA_COMPENSATE("SAGA_COMPENSATE", "SAGA逆向事务补偿"),
    BLACKBOARD_SYNC("BLACKBOARD_SYNC", "黑板事实同步");

    private final String code;
    private final String description;

    A2AMessageType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
