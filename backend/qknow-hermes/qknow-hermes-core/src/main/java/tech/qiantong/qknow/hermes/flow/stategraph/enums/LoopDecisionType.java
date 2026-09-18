package tech.qiantong.qknow.hermes.flow.stategraph.enums;

/**
 * 循环门禁决策结果枚举
 */
public enum LoopDecisionType {
    /**
     * 满足收敛判定条件，正常退出循环并继续向前
     */
    CONVERGED_EXIT,

    /**
     * 处于有效预算内，继续下一轮循环迭代
     */
    CONTINUE_LOOP,

    /**
     * 达到最大迭代步数或全局超步硬上限，触发熔断降级逃逸
     */
    DEGRADED_BREAK
}
