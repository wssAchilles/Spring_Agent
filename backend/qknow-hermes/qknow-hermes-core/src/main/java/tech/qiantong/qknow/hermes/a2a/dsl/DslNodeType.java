package tech.qiantong.qknow.hermes.a2a.dsl;

/**
 * 声明式工作流节点多态类型枚举
 */
public enum DslNodeType {
    /**
     * 标准原子任务节点
     */
    TASK,

    /**
     * 状态图有界循环节点 (StateGraph Bounded Cycle)
     */
    STATE_GRAPH_LOOP,

    /**
     * Swarm 动态上下文交接节点
     */
    SWARM_HANDOFF,

    /**
     * 多智能体对抗辩论竞技场节点
     */
    DEBATE_ARENA,

    /**
     * 人机协同审批挂起节点 (Human-In-The-Loop)
     */
    HITL_APPROVAL,

    /**
     * 企业级 MCP 工具调用节点
     */
    MCP_TOOL_CALL
}
