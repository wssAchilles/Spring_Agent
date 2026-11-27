package tech.qiantong.qknow.hermes.cognitive.control;

import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetTopologyOperator;

/**
 * 6 维认知预算守恒向量（对标 RGAO O(|V|+|E|) 调用前静态校验原则）。
 * <p>
 * 定义：6 个预算维度分别限制总 Token、Prompt Token、工具调用次数、延迟毫秒、估算花费（元）与多跳子图深度。
 * 当预测或累计开销超过配额时，执行确定性降档以保证预算守恒与可控终止。
 * </p>
 */
public record BudgetVector6D(
        long maxTotalTokens,
        long maxPromptTokens,
        int maxToolCalls,
        long maxLatencyMs,
        double maxEstimatedCostCny,
        int maxSubgraphDepth
) {

    public BudgetVector6D {
        if (maxTotalTokens <= 0 || maxPromptTokens <= 0 || maxToolCalls <= 0
                || maxLatencyMs <= 0 || maxEstimatedCostCny <= 0.0 || maxSubgraphDepth <= 0) {
            throw new IllegalArgumentException("BudgetVector6D 所有配额维度必须严格大于 0");
        }
    }

    public static BudgetVector6D defaults() {
        return new BudgetVector6D(16_384L, 12_288L, 8, 30_000L, 0.15D, 4);
    }

    /**
     * O(|V|+|E|) 静态预算守恒检查：校验拓扑算子与思考深度组合是否超出当前请求配额；
     * 若超限则自动确定性降档至满足约束的最优配置，并返回静态校验结果。
     */
    public BudgetConservationOutcome enforceStaticBudget(
            SupernetTopologyOperator candidateOp,
            DeepSeekReasoningEffort candidateEffort,
            long estimatedPromptTokens,
            int plannedToolCalls
    ) {
        SupernetTopologyOperator safeOp = candidateOp == null ? SupernetTopologyOperator.SINGLE_REACT : candidateOp;
        DeepSeekReasoningEffort safeEffort = candidateEffort == null ? DeepSeekReasoningEffort.MEDIUM : candidateEffort;

        // 依据拓扑算子估算节点与边线性复杂度 O(|V|+|E|) 对应 Token 乘数
        double opFactor = 1.0 + safeOp.normalizedComputeCost() * 1.5;
        double effortFactor = 1.0 + safeEffort.normalizedCost() * 1.2;
        long projectedTotalTokens = Math.round(Math.max(1L, estimatedPromptTokens) * opFactor * effortFactor);
        double projectedCostCny = (estimatedPromptTokens * 1.0 + (projectedTotalTokens - estimatedPromptTokens) * 2.0) / 1_000_000.0;

        boolean downgraded = false;
        SupernetTopologyOperator finalOp = safeOp;
        DeepSeekReasoningEffort finalEffort = safeEffort;

        if (estimatedPromptTokens > maxPromptTokens
                || projectedTotalTokens > maxTotalTokens
                || plannedToolCalls > maxToolCalls
                || safeOp.maxLayerDepth() > maxSubgraphDepth
                || projectedCostCny > maxEstimatedCostCny) {
            downgraded = true;
            // 确定性逐级降档 DeepSeek 思考努力度与拓扑深度，优先保障硬预算不被击穿
            if (safeEffort == DeepSeekReasoningEffort.HIGH) {
                finalEffort = DeepSeekReasoningEffort.MEDIUM;
            } else if (safeEffort == DeepSeekReasoningEffort.MEDIUM) {
                finalEffort = DeepSeekReasoningEffort.LOW;
            } else if (safeEffort == DeepSeekReasoningEffort.LOW) {
                finalEffort = DeepSeekReasoningEffort.NONE;
            }
            if (safeOp == SupernetTopologyOperator.SWARM_PARALLEL || safeOp == SupernetTopologyOperator.ADVERSARIAL_DEBATE) {
                finalOp = SupernetTopologyOperator.PLAN_SOLVE;
            } else if (safeOp == SupernetTopologyOperator.CAUSAL_TREE_SEARCH) {
                if (projectedCostCny > maxEstimatedCostCny * 1.2 || estimatedPromptTokens > maxPromptTokens) {
                    finalOp = SupernetTopologyOperator.DIRECT_FAST;
                    finalEffort = DeepSeekReasoningEffort.NONE;
                } else if (maxSubgraphDepth >= 3) {
                    finalOp = SupernetTopologyOperator.PLAN_SOLVE;
                } else if (maxSubgraphDepth >= 2) {
                    finalOp = SupernetTopologyOperator.CODE_ACT_SANDBOX;
                } else {
                    finalOp = SupernetTopologyOperator.DIRECT_FAST;
                    finalEffort = DeepSeekReasoningEffort.NONE;
                }
            } else if (safeOp == SupernetTopologyOperator.TYPED_SUBGRAPH_MAP_REDUCE) {
                if (projectedCostCny > maxEstimatedCostCny * 1.2 || estimatedPromptTokens > maxPromptTokens) {
                    finalOp = SupernetTopologyOperator.DIRECT_FAST;
                    finalEffort = DeepSeekReasoningEffort.NONE;
                } else {
                    finalOp = maxSubgraphDepth >= 2 ? SupernetTopologyOperator.SINGLE_REACT : SupernetTopologyOperator.DIRECT_FAST;
                }
            } else if (safeOp == SupernetTopologyOperator.CODE_ACT_SANDBOX && safeOp.maxLayerDepth() > maxSubgraphDepth) {
                finalOp = SupernetTopologyOperator.DIRECT_FAST;
            } else if (safeOp == SupernetTopologyOperator.PLAN_SOLVE && safeOp.maxLayerDepth() > maxSubgraphDepth) {
                finalOp = SupernetTopologyOperator.SINGLE_REACT;
            } else if (projectedCostCny > maxEstimatedCostCny * 1.2 || estimatedPromptTokens > maxPromptTokens) {
                finalOp = SupernetTopologyOperator.DIRECT_FAST;
                finalEffort = DeepSeekReasoningEffort.NONE;
            }
        }

        return new BudgetConservationOutcome(
                finalOp,
                finalEffort,
                downgraded,
                projectedTotalTokens,
                projectedCostCny
        );
    }

    public record BudgetConservationOutcome(
            SupernetTopologyOperator resolvedOperator,
            DeepSeekReasoningEffort resolvedEffort,
            boolean budgetDowngraded,
            long projectedTotalTokens,
            double projectedCostCny
    ) {
    }
}
