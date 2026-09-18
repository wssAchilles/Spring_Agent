package tech.qiantong.qknow.hermes.flow.stategraph.enums;

/**
 * 状态图边类型枚举
 */
public enum StateGraphEdgeType {
    /**
     * 普通向前边：无条件沿拓扑单向流转
     */
    FORWARD,

    /**
     * 条件分支边：根据谓词条件动态求值选择目标分支
     */
    CONDITIONAL,

    /**
     * 回跳循环边：流转回上游节点，必须受收敛门禁控制
     */
    LOOP_BACK
}
