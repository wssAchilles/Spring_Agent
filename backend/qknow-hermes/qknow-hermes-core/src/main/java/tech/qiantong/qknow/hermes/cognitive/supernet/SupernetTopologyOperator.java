package tech.qiantong.qknow.hermes.cognitive.supernet;

import tech.qiantong.qknow.hermes.proto.OrchestrationMode;

/**
 * Meta-MDP 认知超网拓扑算子集合（对应博士论文第 3 章定义 3.1 候选算子池 {@code O_pool}）。
 */
public enum SupernetTopologyOperator {

    /** 单轮直答算子（零多余工具跳数，最低延迟） */
    DIRECT_FAST(1, 0.05, OrchestrationMode.ORCHESTRATION_MODE_SINGLE),

    /** 单智能体 ReAct 工具迭代算子 */
    SINGLE_REACT(2, 0.25, OrchestrationMode.ORCHESTRATION_MODE_SINGLE),

    /** 分解-求解两阶段规划算子 */
    PLAN_SOLVE(3, 0.55, OrchestrationMode.ORCHESTRATION_MODE_SINGLE),

    /** 多角色正反辩论裁决算子 */
    ADVERSARIAL_DEBATE(4, 0.85, OrchestrationMode.ORCHESTRATION_MODE_DEBATE),

    /** 蜂群并行黑板协作算子 */
    SWARM_PARALLEL(4, 0.90, OrchestrationMode.ORCHESTRATION_MODE_SWARM),

    /** 安全格验证型 CodeAct 程序化求解算子（Phase 8.2） */
    CODE_ACT_SANDBOX(2, 0.35, OrchestrationMode.ORCHESTRATION_MODE_SINGLE),

    /** 前缀锚定隔离上下文类型化并行子图 Map-Reduce 算子（Phase 9.2 PA-TSG） */
    TYPED_SUBGRAPH_MAP_REDUCE(3, 0.45, OrchestrationMode.ORCHESTRATION_MODE_SINGLE),

    /** 超球面漂移有界因果反思树搜索算子（Phase 8.3 BCBT） */
    CAUSAL_TREE_SEARCH(4, 0.75, OrchestrationMode.ORCHESTRATION_MODE_SINGLE),

    /** 认知置信度饱和早退算子（Theorem 3.1 期望深度截断算子） */
    EARLY_EXIT(1, 0.02, OrchestrationMode.ORCHESTRATION_MODE_SINGLE);

    private final int maxLayerDepth;
    private final double normalizedComputeCost;
    private final OrchestrationMode orchestrationMode;

    SupernetTopologyOperator(int maxLayerDepth, double normalizedComputeCost, OrchestrationMode orchestrationMode) {
        this.maxLayerDepth = maxLayerDepth;
        this.normalizedComputeCost = normalizedComputeCost;
        this.orchestrationMode = orchestrationMode;
    }

    public int maxLayerDepth() {
        return maxLayerDepth;
    }

    public double normalizedComputeCost() {
        return normalizedComputeCost;
    }

    public OrchestrationMode toOrchestrationMode() {
        return orchestrationMode;
    }
}
