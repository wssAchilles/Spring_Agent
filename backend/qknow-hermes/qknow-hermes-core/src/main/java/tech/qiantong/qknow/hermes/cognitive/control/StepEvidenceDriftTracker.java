package tech.qiantong.qknow.hermes.cognitive.control;

import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetRoutingReceipt;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetTopologyOperator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 推理步间超球面证据平方弦漂移追踪器（对应方向一 · Theorem 3.1 重规划与早退闭环 + Phase 9.1 步级努力度推送）。
 * <p>
 * 在每次工具调用返回后将新观测文本投影至 {@code S^1023}，计算与上一步证据质心的平方弦漂移
 * {@code Delta_drift(t) = 2 * (1 - <z_t, z_{t-1}>)}：
 * 当漂移超出 {@code 0.35} 时触发超网 NIG 非平稳折扣并动态升级拓扑/思考力度；
 * 当证据置信度达到 {@code >= 0.95} 且未发生剧烈漂移时，签发 {@code EARLY_EXIT} 早退提示阻断冗余工具循环。
 * </p>
 */
public class StepEvidenceDriftTracker {

    private static final int MAX_EVIDENCE_WINDOW = 6;

    private static final class RequestDriftState {
        float[] lastEvidenceVec1024;
        int stepCounter = 0;
        DeepSeekReasoningEffort latestRecommendedEffort;
        Integer latestRecommendedMaxTokens;
        boolean thinkingActivated;
        final Deque<float[]> evidenceWindow = new ArrayDeque<>();
        final List<StepDriftTransitionReceipt> transitions = new ArrayList<>();
        final List<StepComputeRoutingReceipt> computeReceipts = new ArrayList<>();
        final List<EpistemicDampedComputeReceipt> dampedReceipts = new ArrayList<>();
    }

    private final HypersphericalSupernetRouter supernetRouter;
    private final UnitSphereEmbeddingResolver embeddingResolver;
    private final Map<String, RequestDriftState> stateByRequest = new ConcurrentHashMap<>();

    public StepEvidenceDriftTracker(
            HypersphericalSupernetRouter supernetRouter,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        this.supernetRouter = supernetRouter != null ? supernetRouter : new HypersphericalSupernetRouter();
        this.embeddingResolver = embeddingResolver != null ? embeddingResolver : new UnitSphereEmbeddingResolver();
    }

    /**
     * 记录工具返回观测并评估平方弦漂移、重规划或早退触发条件。
     */
    public StepDriftTransitionReceipt observeStep(
            String requestId,
            String question,
            String toolName,
            String observationText,
            double evidenceConfidence,
            HnscdsAblationConfig ablationConfig
    ) {
        String safeReq = (requestId == null || requestId.isBlank()) ? "req-drift" : requestId;
        HnscdsAblationConfig cfg = ablationConfig == null ? HnscdsAblationConfig.defaults() : ablationConfig;
        RequestDriftState state = stateByRequest.computeIfAbsent(safeReq, k -> new RequestDriftState());

        float[] currVec = embeddingResolver.resolveUnit1024(observationText);
        float[] queryVec = embeddingResolver.resolveUnit1024(question);
        double clampedConf = Math.max(0.0, Math.min(1.0, evidenceConfidence));

        synchronized (state) {
            state.stepCounter++;
            int stepIdx = state.stepCounter;
            float[] prevVec = state.lastEvidenceVec1024;
            double driftSq = (prevVec == null)
                    ? 0.0
                    : HypersphericalSupernetRouter.computeSquaredChordDrift(prevVec, currVec);

            StepDriftTransitionReceipt receipt;
            if (!cfg.driftReplanEnabled()) {
                receipt = new StepDriftTransitionReceipt(
                        safeReq,
                        stepIdx,
                        toolName,
                        driftSq,
                        clampedConf,
                        false,
                        false,
                        SupernetTopologyOperator.SINGLE_REACT,
                        tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort.NONE,
                        System.currentTimeMillis()
                );
            } else {
                SupernetRoutingReceipt routing = supernetRouter.routeAndAdapt(
                        safeReq,
                        stepIdx,
                        queryVec,
                        prevVec,
                        currVec,
                        clampedConf
                );
                boolean earlyExit = routing.earlyExitTriggered()
                        || (clampedConf >= HypersphericalSupernetRouter.DEFAULT_EARLY_EXIT_CONFIDENCE
                        && driftSq <= HypersphericalSupernetRouter.DEFAULT_DRIFT_REPLAN_THRESHOLD);
                SupernetTopologyOperator nextOp = earlyExit
                        ? SupernetTopologyOperator.EARLY_EXIT
                        : routing.topologyOperator();
                receipt = new StepDriftTransitionReceipt(
                        safeReq,
                        stepIdx,
                        toolName,
                        driftSq,
                        clampedConf,
                        routing.replanTriggered(),
                        earlyExit,
                        nextOp,
                        routing.reasoningEffort(),
                        System.currentTimeMillis()
                );
            }

            state.lastEvidenceVec1024 = currVec;
            state.evidenceWindow.addLast(currVec);
            while (state.evidenceWindow.size() > MAX_EVIDENCE_WINDOW) {
                state.evidenceWindow.pollFirst();
            }
            state.latestRecommendedEffort = receipt.nextEffort();
            if (receipt.nextEffort() != null && receipt.nextEffort().thinkingEnabled()) {
                state.thinkingActivated = true;
            }
            state.transitions.add(receipt);
            return receipt;
        }
    }

    /**
     * Phase 9.1 ZCEA-SCR 步级观测与异构思考努力度实时路由：
     * 结合平方弦漂移、{@code S^1023} 球面离散度、规格风险与节点阶段签发 {@link StepComputeRoutingReceipt}。
     */
    public StepComputeRoutingReceipt observeStepWithPolicy(
            String requestId,
            String question,
            String toolName,
            String observationText,
            List<float[]> candidateVectors,
            CognitiveStepPhase phase,
            double evidenceConfidence,
            double specRiskScore,
            int priorRejectCount,
            boolean isolatedContext,
            StepComputePolicy policy,
            HnscdsAblationConfig ablationConfig
    ) {
        StepDriftTransitionReceipt driftReceipt = observeStep(
                requestId, question, toolName, observationText, evidenceConfidence, ablationConfig
        );
        String safeReq = (requestId == null || requestId.isBlank()) ? "req-drift" : requestId;
        RequestDriftState state = stateByRequest.computeIfAbsent(safeReq, k -> new RequestDriftState());
        StepComputePolicy safePolicy = policy != null ? policy : new StepComputePolicy();
        float[] queryVec = embeddingResolver.resolveUnit1024(question);
        List<float[]> cands = (candidateVectors != null && !candidateVectors.isEmpty())
                ? candidateVectors
                : List.of(embeddingResolver.resolveUnit1024(observationText));

        synchronized (state) {
            StepComputeRoutingReceipt computeReceipt = safePolicy.decideStepEffort(
                    safeReq,
                    driftReceipt.stepIndex(),
                    phase,
                    isolatedContext,
                    state.thinkingActivated,
                    queryVec,
                    cands,
                    driftReceipt.chordDriftSquared(),
                    specRiskScore,
                    priorRejectCount,
                    phase == CognitiveStepPhase.DECOMPOSE || phase == CognitiveStepPhase.VERIFY_SYNTHESIZE ? 2.5 : 1.0
            );
            state.latestRecommendedEffort = computeReceipt.chosenEffort();
            if (computeReceipt.chosenEffort().thinkingEnabled()) {
                state.thinkingActivated = true;
            }
            state.computeReceipts.add(computeReceipt);
            return computeReceipt;
        }
    }

    /**
     * Phase 10.1 (MS-SHSCR) 新增：基于多候选流形散度与滑动历史证据窗执行步级异构算力平滑升档路由。
     */
    public StepComputeRoutingReceipt observeStepWithManifoldSignal(
            String requestId,
            String question,
            String toolName,
            String observationText,
            StepManifoldDifficultySignal signal,
            StepComputePolicy policy,
            HnscdsAblationConfig ablationConfig
    ) {
        double stepConf = (signal != null && signal.priorRejectCount() > 0) ? 0.40 : 0.85;
        StepDriftTransitionReceipt driftReceipt = observeStep(
                requestId, question, toolName, observationText, stepConf, ablationConfig
        );
        String safeReq = (requestId == null || requestId.isBlank()) ? "req-drift" : requestId;
        RequestDriftState state = stateByRequest.computeIfAbsent(safeReq, k -> new RequestDriftState());
        StepComputePolicy safePolicy = policy != null ? policy : new StepComputePolicy();
        float[] queryVec = embeddingResolver.resolveUnit1024(question);
        StepManifoldDifficultySignal enrichedSignal = (signal != null ? signal : new StepManifoldDifficultySignal(
                CognitiveStepPhase.EXPLORE_RETRIEVE, List.of(), driftReceipt.chordDriftSquared(),
                0.10, 1, 0, false, 1.0, null, false
        )).withChordDriftSq(Math.max(driftReceipt.chordDriftSquared(), signal != null ? signal.chordDriftSq() : 0.0));

        synchronized (state) {
            StepComputeRoutingReceipt computeReceipt = safePolicy.decideStepEffortV5(
                    safeReq,
                    driftReceipt.stepIndex(),
                    state.thinkingActivated,
                    queryVec,
                    enrichedSignal,
                    List.copyOf(state.evidenceWindow)
            );
            state.latestRecommendedEffort = computeReceipt.chosenEffort();
            if (computeReceipt.chosenEffort().thinkingEnabled()) {
                state.thinkingActivated = true;
            }
            state.computeReceipts.add(computeReceipt);
            return computeReceipt;
        }
    }

    /**
     * Phase 11.1 (ED-SCR) 新增：结合 HEIG-BAS 超球面测地新息与累积思考预算执行认知阻尼步级控算。
     */
    public EpistemicDampedComputeReceipt observeStepWithEpistemicDamping(
            String requestId,
            String question,
            String toolName,
            String observationText,
            EpistemicDampingSignal dampingSignal,
            StepComputePolicy policy,
            HnscdsAblationConfig ablationConfig
    ) {
        EpistemicDampingSignal safeDamping = Objects.requireNonNull(dampingSignal, "EpistemicDampingSignal 不能为空");
        StepManifoldDifficultySignal signal = safeDamping.manifoldSignal();
        double stepConf = (signal != null && signal.priorRejectCount() > 0) ? 0.40 : 0.85;
        StepDriftTransitionReceipt driftReceipt = observeStep(
                requestId, question, toolName, observationText, stepConf, ablationConfig
        );
        String safeReq = (requestId == null || requestId.isBlank()) ? "req-drift" : requestId;
        RequestDriftState state = stateByRequest.computeIfAbsent(safeReq, k -> new RequestDriftState());
        StepComputePolicy safePolicy = policy != null ? policy : new StepComputePolicy();
        float[] queryVec = embeddingResolver.resolveUnit1024(question);
        StepManifoldDifficultySignal enrichedManifold = signal.withChordDriftSq(
                Math.max(driftReceipt.chordDriftSquared(), signal.chordDriftSq())
        );
        EpistemicDampingSignal enrichedDamping = new EpistemicDampingSignal(
                enrichedManifold,
                safeDamping.geodesicInnovationDeltaI(),
                safeDamping.consecutiveLowInnovationSteps(),
                safeDamping.cumulativeCompletionTokens(),
                safeDamping.maxThinkTokenBudget(),
                safeDamping.pureRetrievalStep()
        );

        synchronized (state) {
            EpistemicDampedComputeReceipt dampedReceipt = safePolicy.decideStepEffortV6Damped(
                    safeReq,
                    driftReceipt.stepIndex(),
                    state.thinkingActivated,
                    queryVec,
                    enrichedDamping,
                    List.copyOf(state.evidenceWindow)
            );
            StepComputeRoutingReceipt base = dampedReceipt.baseReceipt();
            state.latestRecommendedEffort = base.chosenEffort();
            state.latestRecommendedMaxTokens = dampedReceipt.recommendedMaxCompletionTokens();
            if (base.chosenEffort().thinkingEnabled()) {
                state.thinkingActivated = true;
            }
            state.computeReceipts.add(base);
            state.dampedReceipts.add(dampedReceipt);
            return dampedReceipt;
        }
    }

    public Optional<Integer> getLatestRecommendedMaxTokens(String requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        RequestDriftState state = stateByRequest.get(requestId);
        if (state == null) {
            return Optional.empty();
        }
        synchronized (state) {
            return Optional.ofNullable(state.latestRecommendedMaxTokens);
        }
    }

    public List<EpistemicDampedComputeReceipt> getDampedComputeReceipts(String requestId) {
        if (requestId == null) {
            return List.of();
        }
        RequestDriftState state = stateByRequest.get(requestId);
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            return Collections.unmodifiableList(new ArrayList<>(state.dampedReceipts));
        }
    }

    public Optional<DeepSeekReasoningEffort> getLatestRecommendedEffort(String requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        RequestDriftState state = stateByRequest.get(requestId);
        if (state == null) {
            return Optional.empty();
        }
        synchronized (state) {
            return Optional.ofNullable(state.latestRecommendedEffort);
        }
    }

    public List<StepComputeRoutingReceipt> getComputeReceipts(String requestId) {
        if (requestId == null) {
            return List.of();
        }
        RequestDriftState state = stateByRequest.get(requestId);
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            return Collections.unmodifiableList(new ArrayList<>(state.computeReceipts));
        }
    }

    public List<StepDriftTransitionReceipt> getTransitions(String requestId) {
        if (requestId == null) {
            return List.of();
        }
        RequestDriftState state = stateByRequest.get(requestId);
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            return Collections.unmodifiableList(new ArrayList<>(state.transitions));
        }
    }

    public void clearRequest(String requestId) {
        if (requestId != null) {
            stateByRequest.remove(requestId);
        }
    }
}
