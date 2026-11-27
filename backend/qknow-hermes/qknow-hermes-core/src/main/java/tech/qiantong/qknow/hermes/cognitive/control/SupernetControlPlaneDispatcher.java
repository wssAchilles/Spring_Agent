package tech.qiantong.qknow.hermes.cognitive.control;

import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetRoutingReceipt;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetTopologyOperator;

import java.util.Objects;

/**
 * 超球面超网控制面核心调度器（对应论文第 3 章真接管调度总线）。
 * <p>
 * 串联 {@link UnitSphereEmbeddingResolver}、{@link HypersphericalSupernetRouter} 与 {@link BudgetVector6D}，
 * 并受 {@link HnscdsAblationConfig} 正交消融开关严格控制：
 * 当 {@code supernetEnabled() == true} 时，输出决定实际执行拓扑算子与 DeepSeek 思考深度的不可变控制指令；
 * 当消融关闭超网时，确定性退化为 {@code SINGLE_REACT + NONE} 基线，以支持零混杂消融对比。
 * </p>
 */
public class SupernetControlPlaneDispatcher {

    public record ControlPlaneDirective(
            String requestId,
            float[] queryUnit1024,
            UnitSphereEmbeddingResolver.ResolutionSource embeddingSource,
            SupernetRoutingReceipt routingReceipt,
            SupernetTopologyOperator effectiveOperator,
            DeepSeekReasoningEffort effectiveEffort,
            boolean budgetDowngraded,
            boolean supernetControlled
    ) {
    }

    private final HypersphericalSupernetRouter router;
    private final UnitSphereEmbeddingResolver embeddingResolver;

    public SupernetControlPlaneDispatcher(
            HypersphericalSupernetRouter router,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        this.router = Objects.requireNonNull(router, "HypersphericalSupernetRouter 不能为空");
        this.embeddingResolver = embeddingResolver != null
                ? embeddingResolver
                : new UnitSphereEmbeddingResolver();
    }

    /**
     * 为当前请求签发超网控制面执行指令（含静态 6 维预算校验与消融分支判定）。
     */
    public ControlPlaneDirective dispatch(
            String requestId,
            String question,
            long estimatedPromptTokens,
            int plannedToolCalls,
            BudgetVector6D budget,
            HnscdsAblationConfig ablationConfig
    ) {
        String safeReqId = (requestId == null || requestId.isBlank()) ? "req-ctrl" : requestId;
        HnscdsAblationConfig cfg = ablationConfig == null ? HnscdsAblationConfig.defaults() : ablationConfig;
        BudgetVector6D safeBudget = budget == null ? BudgetVector6D.defaults() : budget;

        var embResult = embeddingResolver.resolveWithProvenance(question);
        float[] queryVec = embResult.unitVector1024();

        if (!cfg.supernetEnabled()) {
            // 消融基线分支（G0/G1）：确定性退化为 Vanilla SINGLE_REACT + 关闭深度思考
            SupernetRoutingReceipt baselineReceipt = SupernetRoutingReceipt.createSigned(
                    safeReqId, 0, SupernetTopologyOperator.SINGLE_REACT, DeepSeekReasoningEffort.NONE,
                    0.0, false, false, 0.50, 0.0, System.currentTimeMillis(), "baseline-ablation"
            );
            return new ControlPlaneDirective(
                    safeReqId,
                    queryVec,
                    embResult.source(),
                    baselineReceipt,
                    SupernetTopologyOperator.SINGLE_REACT,
                    DeepSeekReasoningEffort.NONE,
                    false,
                    false
            );
        }

        SupernetRoutingReceipt receipt = router.routeAndAdapt(
                safeReqId, 0, queryVec, null, null, 0.50
        );
        // Phase 8 & Phase 9 正交消融门禁：未显式启用对应能力时，禁止误分发至高阶算子
        SupernetTopologyOperator gatedOperator = receipt.topologyOperator();
        if (gatedOperator == SupernetTopologyOperator.CODE_ACT_SANDBOX && !cfg.verifiedCodeActEnabled()) {
            gatedOperator = SupernetTopologyOperator.PLAN_SOLVE;
        } else if (gatedOperator == SupernetTopologyOperator.CAUSAL_TREE_SEARCH && !cfg.causalReflexiveTreeSearchEnabled()) {
            gatedOperator = SupernetTopologyOperator.PLAN_SOLVE;
        } else if (gatedOperator == SupernetTopologyOperator.TYPED_SUBGRAPH_MAP_REDUCE && !cfg.typedParallelSubgraphEnabled()) {
            gatedOperator = SupernetTopologyOperator.PLAN_SOLVE;
        }
        var budgetOutcome = safeBudget.enforceStaticBudget(
                gatedOperator,
                receipt.reasoningEffort(),
                estimatedPromptTokens,
                plannedToolCalls
        );
        return new ControlPlaneDirective(
                safeReqId,
                queryVec,
                embResult.source(),
                receipt,
                budgetOutcome.resolvedOperator(),
                budgetOutcome.resolvedEffort(),
                budgetOutcome.budgetDowngraded(),
                true
        );
    }

    public UnitSphereEmbeddingResolver getEmbeddingResolver() {
        return embeddingResolver;
    }

    public HypersphericalSupernetRouter getRouter() {
        return router;
    }
}
