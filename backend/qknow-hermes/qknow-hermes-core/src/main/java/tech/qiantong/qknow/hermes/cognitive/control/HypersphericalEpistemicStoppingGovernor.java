package tech.qiantong.qknow.hermes.cognitive.control;

import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 超球面预期信息增益感知的动态预算与最优停机治理器（HEIG-BAS，对应 Phase 7 原创架构机制二）。
 * <p>
 * 理论机制：
 * 1. 将每步工具观察向量化到 {@code S^1023}，计算相对历史已获取证据集的最小正交余弦新息：
 *    {@code Delta_I_t = min_{j < t} (1 - <z_t, z_j>) in [0, 2]}；
 * 2. 结合 6 维预算压力 {@code rho_t} 与边际信息价值比 {@code VOI_t = Delta_I_t / max(0.2, costRatio)}；
 * 3. 在连续多步边际信息增益枯竭（新息低于阈值）或预算压力超过 {@code 0.90} 时，触发确定性最优停机 {@code STOP_AND_SYNTHESIZE}，
 *    并将极简 {@code <budget_state .../>} 标签挂载至 Zone-1 尾部以保证 Zone-0 前缀缓存零污染。
 * </p>
 */
public class HypersphericalEpistemicStoppingGovernor {

    public static final double DEFAULT_LOW_INNOVATION_THRESHOLD = 0.08;
    public static final double DEFAULT_HIGH_BUDGET_PRESSURE = 0.90;

    private static final class RequestEpistemicState {
        final List<float[]> historicalEvidenceVecs = new ArrayList<>();
        final List<EpistemicStoppingReceipt> receipts = new ArrayList<>();
        int consecutiveLowInnovationSteps = 0;
        long cumulativeTokens = 0L;
    }

    private final UnitSphereEmbeddingResolver embeddingResolver;
    private final double lowInnovationThreshold;
    private final Map<String, RequestEpistemicState> stateByRequest = new ConcurrentHashMap<>();

    public HypersphericalEpistemicStoppingGovernor() {
        this(new UnitSphereEmbeddingResolver(), DEFAULT_LOW_INNOVATION_THRESHOLD);
    }

    public HypersphericalEpistemicStoppingGovernor(UnitSphereEmbeddingResolver embeddingResolver) {
        this(embeddingResolver, DEFAULT_LOW_INNOVATION_THRESHOLD);
    }

    public HypersphericalEpistemicStoppingGovernor(
            UnitSphereEmbeddingResolver embeddingResolver,
            double lowInnovationThreshold
    ) {
        this.embeddingResolver = embeddingResolver != null ? embeddingResolver : new UnitSphereEmbeddingResolver();
        this.lowInnovationThreshold = Math.max(0.01, lowInnovationThreshold);
    }

    /**
     * 评估单步工具观察的测地新息、剩余预算压力并裁决继续深挖、转向还是最优停机。
     */
    public EpistemicStoppingReceipt evaluateStep(
            String requestId,
            int stepIndex,
            String observationText,
            long stepTokensDelta,
            BudgetVector6D budget,
            HnscdsAblationConfig ablationConfig
    ) {
        String safeReq = (requestId == null || requestId.isBlank()) ? "req-heig" : requestId;
        BudgetVector6D safeBudget = budget == null ? BudgetVector6D.defaults() : budget;
        HnscdsAblationConfig cfg = ablationConfig == null ? HnscdsAblationConfig.defaults() : ablationConfig;
        RequestEpistemicState state = stateByRequest.computeIfAbsent(safeReq, k -> new RequestEpistemicState());

        float[] currVec = embeddingResolver.resolveUnit1024(observationText);
        synchronized (state) {
            state.cumulativeTokens += Math.max(1L, stepTokensDelta);
            double minGeodesicInnovation = 1.0;
            if (!state.historicalEvidenceVecs.isEmpty()) {
                minGeodesicInnovation = Double.MAX_VALUE;
                for (float[] histVec : state.historicalEvidenceVecs) {
                    double d = 1.0 - cosine(currVec, histVec);
                    if (d < minGeodesicInnovation) {
                        minGeodesicInnovation = d;
                    }
                }
                minGeodesicInnovation = Math.max(0.0, Math.min(2.0, minGeodesicInnovation));
            }
            state.historicalEvidenceVecs.add(currVec);

            int remainingCalls = Math.max(0, safeBudget.maxToolCalls() - Math.max(1, stepIndex));
            double callUsage = (double) Math.max(1, stepIndex) / Math.max(1, safeBudget.maxToolCalls());
            double tokenUsage = (double) state.cumulativeTokens / Math.max(1L, safeBudget.maxTotalTokens());
            double budgetPressureRho = Math.max(0.0, Math.min(1.0, Math.max(callUsage, tokenUsage)));

            double normalizedStepCost = Math.max(0.20, (double) Math.max(64L, stepTokensDelta) / 512.0);
            double marginalVoi = minGeodesicInnovation / normalizedStepCost;

            if (minGeodesicInnovation < lowInnovationThreshold) {
                state.consecutiveLowInnovationSteps++;
            } else {
                state.consecutiveLowInnovationSteps = 0;
            }

            EpistemicStoppingReceipt.StoppingDecision decision;
            if (!cfg.epistemicStoppingEnabled()) {
                decision = EpistemicStoppingReceipt.StoppingDecision.CONTINUE_DEEPEN;
            } else if (budgetPressureRho >= DEFAULT_HIGH_BUDGET_PRESSURE
                    || remainingCalls <= 0
                    || state.consecutiveLowInnovationSteps >= 2) {
                decision = EpistemicStoppingReceipt.StoppingDecision.STOP_AND_SYNTHESIZE;
            } else if (minGeodesicInnovation < lowInnovationThreshold) {
                decision = EpistemicStoppingReceipt.StoppingDecision.PIVOT_EXPLORE;
            } else {
                decision = EpistemicStoppingReceipt.StoppingDecision.CONTINUE_DEEPEN;
            }

            String tailBudgetTag = String.format(
                    Locale.ROOT,
                    "<budget_state step=\"%d\" remaining_calls=\"%d\" pressure=\"%.2f\" delta_I=\"%.3f\" decision=\"%s\"/>",
                    stepIndex, remainingCalls, budgetPressureRho, minGeodesicInnovation, decision.name()
            );

            EpistemicStoppingReceipt receipt = new EpistemicStoppingReceipt(
                    safeReq,
                    stepIndex,
                    minGeodesicInnovation,
                    marginalVoi,
                    budgetPressureRho,
                    remainingCalls,
                    decision,
                    tailBudgetTag,
                    System.currentTimeMillis()
            );
            state.receipts.add(receipt);
            return receipt;
        }
    }

    public int getLatestConsecutiveLowInnovationSteps(String requestId) {
        if (requestId == null) {
            return 0;
        }
        RequestEpistemicState state = stateByRequest.get(requestId);
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return state.consecutiveLowInnovationSteps;
        }
    }

    public List<EpistemicStoppingReceipt> getReceipts(String requestId) {
        if (requestId == null) {
            return List.of();
        }
        RequestEpistemicState state = stateByRequest.get(requestId);
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            return Collections.unmodifiableList(new ArrayList<>(state.receipts));
        }
    }

    public void clearRequest(String requestId) {
        if (requestId != null) {
            stateByRequest.remove(requestId);
        }
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }
}
