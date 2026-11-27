package tech.qiantong.qknow.hermes.cognitive.control;

import java.util.Objects;

/**
 * 认知阻尼步级控算输入信号（Phase 11.1 ED-SCR，定理 1 不可变载体）。
 * <p>
 * 耦合 {@code HEIG-BAS} 单位超球面 {@code S^1023} 测地新息 {@code Delta I_t}、连续低新息步数与累积思考输出消耗，
 * 用于打破多轮纯检索探索触发 {@code MEDIUM x 10} 自锁并抑制单题 Overthinking Spiral。
 * </p>
 */
public record EpistemicDampingSignal(
        StepManifoldDifficultySignal manifoldSignal,
        double geodesicInnovationDeltaI,
        int consecutiveLowInnovationSteps,
        long cumulativeCompletionTokens,
        long maxThinkTokenBudget,
        boolean pureRetrievalStep
) {
    public static final long DEFAULT_MAX_THINK_TOKEN_BUDGET = 6000L;

    public EpistemicDampingSignal {
        manifoldSignal = Objects.requireNonNull(manifoldSignal, "StepManifoldDifficultySignal 不能为空");
        geodesicInnovationDeltaI = Double.isNaN(geodesicInnovationDeltaI)
                ? 0.0
                : Math.max(0.0, Math.min(2.0, geodesicInnovationDeltaI));
        consecutiveLowInnovationSteps = Math.max(0, consecutiveLowInnovationSteps);
        cumulativeCompletionTokens = Math.max(0L, cumulativeCompletionTokens);
        maxThinkTokenBudget = maxThinkTokenBudget <= 0L ? DEFAULT_MAX_THINK_TOKEN_BUDGET : maxThinkTokenBudget;
    }
}
