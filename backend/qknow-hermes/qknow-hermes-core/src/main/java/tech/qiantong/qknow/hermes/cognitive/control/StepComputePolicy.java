package tech.qiantong.qknow.hermes.cognitive.control;

import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 零本地模型、分布无关 Conformal 步级异构算力分配策略引擎（Phase 9.1 ZCEA-SCR，定理 2 & 引理 2.2）。
 * <p>
 * 核心机理：
 * 1. 基于千问 {@code text-embedding-v4} 的 {@code S^1023} 单位超球面精确计算球面离散度
 *    {@code D_S = 1 - || (1/k) * sum d_j ||_2}、首命测地残差与 Top-1/2 余弦间距；
 * 2. 采用免微调单调阈值策略与 Bates UCB 有限样本风险校准（严禁引入本地 1.7B 小模型）；
 * 3. 双轨兼容协议：隔离上下文子图节点安全使用 {@code NONE..HIGH}，线性多轮复用思考模式默认以 {@code LOW} 保底以消除历史 tool_calls 协议冲突。
 * </p>
 */
public class StepComputePolicy {

    public static final int EMBEDDING_DIM = 1024;
    private static final double[] DEFAULT_THRESHOLDS = new double[]{0.25, 0.50, 0.78};

    // Phase 11.1 (ED-SCR) 认知阻尼默认标定常数
    public static final double DEFAULT_INNOVATION_THRESHOLD = 0.08;
    public static final double DEFAULT_GAMMA_MIN = 0.42;
    public static final int DEFAULT_MIN_OUTPUT_CAP = 384;
    public static final int DEFAULT_MAX_OUTPUT_CAP = 4096;

    /**
     * 轻量单步/轨迹校准样本（用于离线无参 Conformal UCB 阈值搜寻）。
     */
    public record CalibrationTrace(
            double difficultyScore,
            boolean isolatedContext,
            boolean highSucceeded,
            boolean noneSucceeded,
            boolean lowSucceeded,
            boolean mediumSucceeded,
            double noneTokens,
            double lowTokens,
            double mediumTokens,
            double highTokens
    ) {}

    /**
     * Conformal 风险校准结果。
     */
    public record ConformalCalibrationResult(
            double[] thresholds,
            double empiricalRisk,
            double ucbRiskUpperBound,
            double expectedThinkTokens,
            double targetEpsilon,
            double confidenceDelta,
            boolean constraintSatisfied
    ) {
        public ConformalCalibrationResult {
            thresholds = thresholds != null ? thresholds.clone() : DEFAULT_THRESHOLDS.clone();
        }

        @Override
        public double[] thresholds() {
            return thresholds.clone();
        }
    }

    private volatile double[] calibratedThresholds = DEFAULT_THRESHOLDS.clone();
    private volatile boolean conformalCalibrated = false;
    private final AtomicLong totalStepDecisions = new AtomicLong(0L);
    private final AtomicLong v5ManifoldDecisions = new AtomicLong(0L);
    private final AtomicLong v6DampedDecisions = new AtomicLong(0L);
    private final AtomicLong dampingDownshifts = new AtomicLong(0L);
    private final AtomicLong totalBudgetCapTokensSaved = new AtomicLong(0L);
    private final EnumMap<DeepSeekReasoningEffort, AtomicLong> effortCounters = new EnumMap<>(DeepSeekReasoningEffort.class);

    public StepComputePolicy() {
        for (DeepSeekReasoningEffort effort : DeepSeekReasoningEffort.values()) {
            effortCounters.put(effort, new AtomicLong(0L));
        }
    }

    /**
     * 计算 {@code S^1023} 候选向量的球面合向量模长 {@code R_bar = || (1/k) * sum d_j ||_2}。
     */
    public static double computeMeanResultantLength(List<float[]> candidateUnitVectors) {
        if (candidateUnitVectors == null || candidateUnitVectors.isEmpty()) {
            return 1.0;
        }
        int validCount = 0;
        double[] meanVec = null;
        for (float[] vec : candidateUnitVectors) {
            if (vec == null || vec.length == 0) {
                continue;
            }
            if (meanVec == null) {
                meanVec = new double[vec.length];
            }
            if (vec.length != meanVec.length) {
                continue;
            }
            double norm = l2Norm(vec);
            double invNorm = norm > 1e-12 ? (1.0 / norm) : 1.0;
            for (int i = 0; i < vec.length; i++) {
                meanVec[i] += vec[i] * invNorm;
            }
            validCount++;
        }
        if (validCount == 0 || meanVec == null) {
            return 1.0;
        }
        double normSq = 0.0;
        for (int i = 0; i < meanVec.length; i++) {
            double v = meanVec[i] / validCount;
            normSq += v * v;
        }
        return Math.max(0.0, Math.min(1.0, Math.sqrt(normSq)));
    }

    /**
     * 计算 {@code S^1023} 超球面球面离散度 {@code D_S = 1 - R_bar in [0, 1]}。
     */
    public static double computeSphericalVariance(List<float[]> candidateUnitVectors) {
        return Math.max(0.0, Math.min(1.0, 1.0 - computeMeanResultantLength(candidateUnitVectors)));
    }

    /**
     * 校验恒等式 {@code R_bar^2 = 1 - (1 / (2 k^2)) sum ||d_a - d_b||_2^2} 的展开形式（用于测试与审计）。
     */
    public static double computeMeanResultantLengthSquaredByPairwiseChord(List<float[]> candidateUnitVectors) {
        if (candidateUnitVectors == null || candidateUnitVectors.isEmpty()) {
            return 1.0;
        }
        List<double[]> normalized = candidateUnitVectors.stream()
                .filter(v -> v != null && v.length > 0)
                .map(StepComputePolicy::toDoubleUnit)
                .toList();
        int k = normalized.size();
        if (k == 0) {
            return 1.0;
        }
        double sumEucSq = 0.0;
        for (int a = 0; a < k; a++) {
            double[] va = normalized.get(a);
            for (int b = 0; b < k; b++) {
                double[] vb = normalized.get(b);
                if (va.length != vb.length) {
                    continue;
                }
                double d2 = 0.0;
                for (int i = 0; i < va.length; i++) {
                    double diff = va[i] - vb[i];
                    d2 += diff * diff;
                }
                sumEucSq += d2;
            }
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - sumEucSq / (2.0 * k * k)));
    }

    /**
     * 计算归一化步级认知难度泛函 {@code s_i in [0, 1]}。
     */
    public double computeDifficultyScore(
            CognitiveStepPhase phase,
            float[] queryUnit1024,
            List<float[]> candidateUnitVectors,
            double chordDriftSq,
            double specRiskScore,
            int priorRejectCount,
            double topologicalErrorWeight
    ) {
        CognitiveStepPhase safePhase = Objects.requireNonNullElse(phase, CognitiveStepPhase.EXPLORE_RETRIEVE);
        double sphericalVar = computeSphericalVariance(candidateUnitVectors);
        TopCosineStats stats = computeTopCosineStats(queryUnit1024, candidateUnitVectors);
        double residual = stats.firstHitResidual();
        double ambiguity = 1.0 - Math.max(0.0, Math.min(1.0, stats.top2Margin() * 2.5));
        double normDrift = clamp01(chordDriftSq / 1.0);
        double normTopo = clamp01((Math.max(1.0, topologicalErrorWeight) - 1.0) / 3.0);
        double rejectBoost = priorRejectCount > 0 ? Math.min(0.45, 0.25 * priorRejectCount) : 0.0;

        double raw = 0.30 * safePhase.phasePrior()
                + 0.20 * residual
                + 0.15 * sphericalVar
                + 0.12 * ambiguity
                + 0.13 * normDrift
                + 0.10 * clamp01(specRiskScore)
                + 0.08 * normTopo
                + rejectBoost;
        return clamp01(raw);
    }

    /**
     * 为当前认知节点或推理步分配最优 DeepSeek 思考努力度并签发不可变凭单。
     */
    public StepComputeRoutingReceipt decideStepEffort(
            String requestId,
            int stepIndex,
            CognitiveStepPhase phase,
            boolean isolatedContext,
            boolean sessionThinkingAlreadyActive,
            float[] queryUnit1024,
            List<float[]> candidateUnitVectors,
            double chordDriftSq,
            double specRiskScore,
            int priorRejectCount,
            double topologicalErrorWeight
    ) {
        CognitiveStepPhase safePhase = Objects.requireNonNullElse(phase, CognitiveStepPhase.EXPLORE_RETRIEVE);
        double sphericalVar = computeSphericalVariance(candidateUnitVectors);
        TopCosineStats stats = computeTopCosineStats(queryUnit1024, candidateUnitVectors);
        double difficulty = computeDifficultyScore(
                safePhase, queryUnit1024, candidateUnitVectors,
                chordDriftSq, specRiskScore, priorRejectCount, topologicalErrorWeight
        );

        double[] th = calibratedThresholds;
        DeepSeekReasoningEffort effort;
        StringBuilder reason = new StringBuilder();

        if (priorRejectCount >= 1 || safePhase == CognitiveStepPhase.COUNTERFACTUAL_REPAIR && specRiskScore >= 0.40) {
            effort = DeepSeekReasoningEffort.HIGH;
            reason.append("priorReject/repairEscalation");
        } else if (difficulty < th[0] && safePhase.noneEffortEligible() && isolatedContext) {
            effort = DeepSeekReasoningEffort.NONE;
            reason.append("isolatedLowDifficulty");
        } else if (difficulty < th[1]) {
            effort = DeepSeekReasoningEffort.LOW;
            reason.append(isolatedContext ? "lowDifficultyBand" : "linearChainSafeLowFloor");
        } else if (difficulty < th[2]) {
            effort = DeepSeekReasoningEffort.MEDIUM;
            reason.append("mediumAnalyticalBand");
        } else {
            effort = DeepSeekReasoningEffort.HIGH;
            reason.append("highComplexityOrUncertainty");
        }

        // 线性多轮协议防抖动兜底：只要本会话已开启思考链且非隔离叶子，严禁回退至 NONE 造成历史 tool_calls 协议冲突
        if (!isolatedContext && sessionThinkingAlreadyActive && effort == DeepSeekReasoningEffort.NONE) {
            effort = DeepSeekReasoningEffort.LOW;
            reason.append("+linearThinkingFloor");
        }

        totalStepDecisions.incrementAndGet();
        effortCounters.get(effort).incrementAndGet();

        return new StepComputeRoutingReceipt(
                requestId,
                stepIndex,
                safePhase,
                isolatedContext,
                sphericalVar,
                stats.firstHitResidual(),
                stats.top2Margin(),
                difficulty,
                Math.max(1.0, topologicalErrorWeight),
                effort,
                th,
                conformalCalibrated,
                reason.toString(),
                System.currentTimeMillis()
        );
    }

    /**
     * Phase 10.1 (MS-SHSCR 定理 1)：多候选流形散度与跳深感知步级异构算力分配。
     * <p>
     * 消除单向量坍缩导致的 {@code D_S ≡ 0} 与 {@code MEDIUM = 0} 死区：当单步仅提供单一候选向量时，
     * 自动融合滑动证据轨迹窗 {@code historicalEvidenceWindow} 计算累积流形分散度；并结合多跳深度、
     * PA-TSG 桥接分叉歧义、Supernet 先验成本与 Conformal UCB 校准阈值平滑分级（首次异常阶梯升 {@code MEDIUM}）。
     * </p>
     */
    public StepComputeRoutingReceipt decideStepEffortV5(
            String requestId,
            int stepIndex,
            boolean sessionThinkingAlreadyActive,
            float[] queryUnit1024,
            StepManifoldDifficultySignal signal,
            List<float[]> historicalEvidenceWindow
    ) {
        StepManifoldDifficultySignal safeSignal = Objects.requireNonNull(signal, "StepManifoldDifficultySignal 不能为空");
        CognitiveStepPhase safePhase = safeSignal.phase();

        // 1. 构造多候选流形集合：优先真实 Top-K 候选，若不足 2 条则融合历史轨迹滑动证据窗防坍缩
        List<float[]> effectiveCandidates = new ArrayList<>(safeSignal.topKCandidateVectors());
        if (effectiveCandidates.size() < 2 && historicalEvidenceWindow != null && !historicalEvidenceWindow.isEmpty()) {
            for (float[] histVec : historicalEvidenceWindow) {
                if (histVec != null && histVec.length > 0) {
                    effectiveCandidates.add(histVec);
                }
            }
        }
        double sphericalVar = computeSphericalVariance(effectiveCandidates);
        TopCosineStats stats = computeTopCosineStats(queryUnit1024, effectiveCandidates);
        double effectiveMargin = safeSignal.bridgeForkAmbiguity()
                ? Math.min(stats.top2Margin(), safeSignal.bridgeTop2Margin())
                : stats.top2Margin();
        double ambiguity = 1.0 - Math.max(0.0, Math.min(1.0, effectiveMargin * 2.5));
        double normDrift = clamp01(safeSignal.chordDriftSq());
        double hopFactor = clamp01(Math.max(0, safeSignal.hopDepth() - 1) / 3.0);
        double bridgeForkTerm = safeSignal.bridgeForkAmbiguity() ? 0.14 : 0.0;
        double supernetPriorCost = safeSignal.supernetPriorEffort().normalizedCost();

        double topoWeight = 1.0 + 1.5 * hopFactor + (safePhase == CognitiveStepPhase.CODEACT_PROGRAM ? 0.6 : 0.0)
                + (safePhase == CognitiveStepPhase.VERIFY_SYNTHESIZE || safePhase == CognitiveStepPhase.DECOMPOSE ? 1.0 : 0.0);

        double difficulty = clamp01(
                0.24 * safePhase.phasePrior()
                        + 0.16 * stats.firstHitResidual()
                        + 0.14 * sphericalVar
                        + 0.10 * ambiguity
                        + 0.10 * normDrift
                        + 0.10 * safeSignal.specRiskScore()
                        + 0.14 * hopFactor
                        + 0.10 * supernetPriorCost
                        + bridgeForkTerm
                        + (safeSignal.priorRejectCount() > 0 ? Math.min(0.40, 0.18 * safeSignal.priorRejectCount()) : 0.0)
        );

        double[] th = calibratedThresholds;
        DeepSeekReasoningEffort effort;
        StringBuilder reason = new StringBuilder("v5Manifold:");

        if (safeSignal.priorRejectCount() >= 2
                || (safePhase == CognitiveStepPhase.COUNTERFACTUAL_REPAIR && safeSignal.specRiskScore() >= 0.55)) {
            effort = DeepSeekReasoningEffort.HIGH;
            reason.append("multiRejectOrRepairHigh");
        } else if (safeSignal.priorRejectCount() == 1) {
            effort = difficulty >= th[2] ? DeepSeekReasoningEffort.HIGH : DeepSeekReasoningEffort.MEDIUM;
            reason.append("stepwiseSingleRejectEscalation");
        } else if (difficulty < th[0] && safePhase.noneEffortEligible() && safeSignal.isolatedContext()) {
            effort = DeepSeekReasoningEffort.NONE;
            reason.append("isolatedLowDifficultyNone");
        } else if (difficulty < th[1]) {
            effort = DeepSeekReasoningEffort.LOW;
            reason.append(safeSignal.isolatedContext() ? "lowBand" : "linearSafeLowFloor");
        } else if (difficulty < th[2]) {
            effort = DeepSeekReasoningEffort.MEDIUM;
            reason.append("manifoldMediumAnalyticalBand");
        } else {
            effort = DeepSeekReasoningEffort.HIGH;
            reason.append("manifoldHighComplexity");
        }

        // 线性多轮协议防抖动铁律：非隔离分支已启用思考态时禁止退回 NONE
        if (!safeSignal.isolatedContext() && sessionThinkingAlreadyActive && effort == DeepSeekReasoningEffort.NONE) {
            effort = DeepSeekReasoningEffort.LOW;
            reason.append("+linearThinkingFloor");
        }

        totalStepDecisions.incrementAndGet();
        v5ManifoldDecisions.incrementAndGet();
        effortCounters.get(effort).incrementAndGet();

        return new StepComputeRoutingReceipt(
                requestId,
                stepIndex,
                safePhase,
                safeSignal.isolatedContext(),
                sphericalVar,
                stats.firstHitResidual(),
                effectiveMargin,
                difficulty,
                topoWeight,
                effort,
                th,
                conformalCalibrated,
                reason.toString(),
                System.currentTimeMillis()
        );
    }

    /**
     * Phase 11.1 (ED-SCR 定理 1)：超球面测地新息耦合认知阻尼控算与步级动态输出预算帽。
     * <p>
     * 在保持 hard error / spec reject 时重置 {@code gamma = 1.0} 保障纠偏算力的前提下，
     * 对连续低测地新息探索步或高累积思考预算占比施加单调指数阻尼并纠偏纯检索阶段误映射，
     * 动态签发 {@code max_completion_tokens} 预算帽彻底打破 {@code MEDIUM x 10} 自锁。
     * </p>
     */
    public EpistemicDampedComputeReceipt decideStepEffortV6Damped(
            String requestId,
            int stepIndex,
            boolean sessionThinkingAlreadyActive,
            float[] queryUnit1024,
            EpistemicDampingSignal dampingSignal,
            List<float[]> historicalEvidenceWindow
    ) {
        EpistemicDampingSignal safeDamping = Objects.requireNonNull(dampingSignal, "EpistemicDampingSignal 不能为空");
        StepManifoldDifficultySignal rawSignal = safeDamping.manifoldSignal();

        double deltaI = safeDamping.geodesicInnovationDeltaI();
        int lowInnovStreak = safeDamping.consecutiveLowInnovationSteps();
        double psi = clamp01((double) safeDamping.cumulativeCompletionTokens() / safeDamping.maxThinkTokenBudget());
        // Phase 11b 早期多跳桥接保护窗：前 3 步未发生连续低新息空转且预算充足时，保留 MS-SHSCR 先验自由升档能力，
        // 仅自第 4 步起或出现低新息连击/高预算占用时开启认知阻尼与纯检索降档。
        boolean dampingWindowActive = (stepIndex >= 4) || (lowInnovStreak >= 2) || (psi >= 0.55);

        // 1. 阶段纠偏：仅在阻尼窗口激活后，对无硬驳回的纯只读检索步压回 EXPLORE_RETRIEVE 打破 MEDIUMx10 自锁
        StepManifoldDifficultySignal correctedSignal = rawSignal;
        boolean phaseCorrected = false;
        if (dampingWindowActive
                && safeDamping.pureRetrievalStep()
                && rawSignal.priorRejectCount() == 0
                && (rawSignal.phase() == CognitiveStepPhase.CODEACT_PROGRAM
                || rawSignal.phase() == CognitiveStepPhase.VERIFY_SYNTHESIZE)) {
            correctedSignal = new StepManifoldDifficultySignal(
                    CognitiveStepPhase.EXPLORE_RETRIEVE,
                    rawSignal.topKCandidateVectors(),
                    rawSignal.chordDriftSq(),
                    rawSignal.specRiskScore(),
                    rawSignal.hopDepth(),
                    rawSignal.priorRejectCount(),
                    rawSignal.bridgeForkAmbiguity(),
                    rawSignal.bridgeTop2Margin(),
                    DeepSeekReasoningEffort.LOW,
                    rawSignal.isolatedContext()
            );
            phaseCorrected = true;
        }

        CognitiveStepPhase safePhase = correctedSignal.phase();
        List<float[]> effectiveCandidates = new ArrayList<>(correctedSignal.topKCandidateVectors());
        if (effectiveCandidates.size() < 2 && historicalEvidenceWindow != null && !historicalEvidenceWindow.isEmpty()) {
            for (float[] histVec : historicalEvidenceWindow) {
                if (histVec != null && histVec.length > 0) {
                    effectiveCandidates.add(histVec);
                }
            }
        }
        double sphericalVar = computeSphericalVariance(effectiveCandidates);
        TopCosineStats stats = computeTopCosineStats(queryUnit1024, effectiveCandidates);
        double effectiveMargin = correctedSignal.bridgeForkAmbiguity()
                ? Math.min(stats.top2Margin(), correctedSignal.bridgeTop2Margin())
                : stats.top2Margin();
        double ambiguity = 1.0 - Math.max(0.0, Math.min(1.0, effectiveMargin * 2.5));
        double normDrift = clamp01(correctedSignal.chordDriftSq());
        double hopFactor = clamp01(Math.max(0, correctedSignal.hopDepth() - 1) / 3.0);
        double bridgeForkTerm = correctedSignal.bridgeForkAmbiguity() ? 0.14 : 0.0;
        double supernetPriorCost = correctedSignal.supernetPriorEffort().normalizedCost();

        double topoWeight = 1.0 + 1.5 * hopFactor + (safePhase == CognitiveStepPhase.CODEACT_PROGRAM ? 0.6 : 0.0)
                + (safePhase == CognitiveStepPhase.VERIFY_SYNTHESIZE || safePhase == CognitiveStepPhase.DECOMPOSE ? 1.0 : 0.0);

        double rawDifficulty = clamp01(
                0.24 * safePhase.phasePrior()
                        + 0.16 * stats.firstHitResidual()
                        + 0.14 * sphericalVar
                        + 0.10 * ambiguity
                        + 0.10 * normDrift
                        + 0.10 * correctedSignal.specRiskScore()
                        + 0.14 * hopFactor
                        + 0.10 * supernetPriorCost
                        + bridgeForkTerm
                        + (correctedSignal.priorRejectCount() > 0 ? Math.min(0.40, 0.18 * correctedSignal.priorRejectCount()) : 0.0)
        );

        // 2. 计算指数认知阻尼因子 gamma_t（硬错误或早期多跳保护窗内重置为 1.0）
        double gamma;
        StringBuilder dampReason = new StringBuilder("v6Damped:");
        if (phaseCorrected) {
            dampReason.append("pureRetrievalPhaseCorrected+");
        }
        if (correctedSignal.priorRejectCount() >= 1) {
            gamma = 1.0;
            dampReason.append("rejectResetGamma1.0");
        } else if (!dampingWindowActive) {
            gamma = 1.0;
            dampReason.append("earlyHopGuardGamma1.0");
        } else {
            double innovPenalty = 3.5 * Math.max(0.0, DEFAULT_INNOVATION_THRESHOLD - deltaI);
            double streakPenalty = 0.30 * Math.max(0, lowInnovStreak - 1);
            double budgetPenalty = 1.15 * Math.max(0.0, psi - 0.45);
            gamma = Math.max(DEFAULT_GAMMA_MIN, Math.exp(-(innovPenalty + streakPenalty + budgetPenalty)));
            dampReason.append(String.format(java.util.Locale.ROOT, "gamma=%.3f(dI=%.3f,streak=%d,psi=%.2f)",
                    gamma, deltaI, lowInnovStreak, psi));
        }

        double dampedDifficulty = clamp01(rawDifficulty * gamma);
        double[] th = calibratedThresholds;

        // 先计算无阻尼基线 effort（用于观测是否真实发生了阻尼降档）
        DeepSeekReasoningEffort rawEffort = classifyEffortWithSignals(
                rawDifficulty, safePhase, correctedSignal, th, sessionThinkingAlreadyActive);
        DeepSeekReasoningEffort dampedEffort = classifyEffortWithSignals(
                dampedDifficulty, safePhase, correctedSignal, th, sessionThinkingAlreadyActive);

        boolean downshifted = dampedEffort.ordinal() < rawEffort.ordinal();
        if (correctedSignal.priorRejectCount() == 0 && (lowInnovStreak >= 2 || psi >= 0.65)) {
            if (dampedEffort == DeepSeekReasoningEffort.HIGH) {
                dampedEffort = DeepSeekReasoningEffort.MEDIUM;
                downshifted = true;
                dampReason.append("+stepwiseClampToMedium");
            } else if (dampedEffort == DeepSeekReasoningEffort.MEDIUM) {
                dampedEffort = DeepSeekReasoningEffort.LOW;
                downshifted = true;
                dampReason.append("+stepwiseClampToLow");
            }
        }

        // 线性多轮协议防抖动铁律：非隔离分支已启用思考态时禁止退回 NONE
        if (!correctedSignal.isolatedContext() && sessionThinkingAlreadyActive && dampedEffort == DeepSeekReasoningEffort.NONE) {
            dampedEffort = DeepSeekReasoningEffort.LOW;
            dampReason.append("+linearThinkingFloor");
        }

        int baseCap = switch (dampedEffort) {
            case NONE -> 512;
            case LOW -> 1024;
            case MEDIUM -> 1792;
            case HIGH -> 2816;
        };
        int recommendedMaxTokens = Math.max(DEFAULT_MIN_OUTPUT_CAP,
                Math.min(DEFAULT_MAX_OUTPUT_CAP, (int) Math.floor(baseCap * gamma)));

        totalStepDecisions.incrementAndGet();
        v6DampedDecisions.incrementAndGet();
        if (downshifted) {
            dampingDownshifts.incrementAndGet();
        }
        totalBudgetCapTokensSaved.addAndGet(Math.max(0, DEFAULT_MAX_OUTPUT_CAP - recommendedMaxTokens));
        effortCounters.get(dampedEffort).incrementAndGet();

        StepComputeRoutingReceipt baseReceipt = new StepComputeRoutingReceipt(
                requestId,
                stepIndex,
                safePhase,
                correctedSignal.isolatedContext(),
                sphericalVar,
                stats.firstHitResidual(),
                effectiveMargin,
                dampedDifficulty,
                topoWeight,
                dampedEffort,
                th,
                conformalCalibrated,
                dampReason.toString(),
                System.currentTimeMillis()
        );

        return new EpistemicDampedComputeReceipt(
                baseReceipt,
                rawDifficulty,
                gamma,
                dampedDifficulty,
                deltaI,
                lowInnovStreak,
                psi,
                recommendedMaxTokens,
                downshifted,
                dampReason.toString()
        );
    }

    private static DeepSeekReasoningEffort classifyEffortWithSignals(
            double score,
            CognitiveStepPhase safePhase,
            StepManifoldDifficultySignal signal,
            double[] th,
            boolean sessionThinkingAlreadyActive
    ) {
        DeepSeekReasoningEffort effort;
        if (signal.priorRejectCount() >= 2
                || (safePhase == CognitiveStepPhase.COUNTERFACTUAL_REPAIR && signal.specRiskScore() >= 0.55)) {
            effort = DeepSeekReasoningEffort.HIGH;
        } else if (signal.priorRejectCount() == 1) {
            effort = score >= th[2] ? DeepSeekReasoningEffort.HIGH : DeepSeekReasoningEffort.MEDIUM;
        } else if (score < th[0] && safePhase.noneEffortEligible() && signal.isolatedContext()) {
            effort = DeepSeekReasoningEffort.NONE;
        } else if (score < th[1]) {
            effort = DeepSeekReasoningEffort.LOW;
        } else if (score < th[2]) {
            effort = DeepSeekReasoningEffort.MEDIUM;
        } else {
            effort = DeepSeekReasoningEffort.HIGH;
        }
        if (!signal.isolatedContext() && sessionThinkingAlreadyActive && effort == DeepSeekReasoningEffort.NONE) {
            return DeepSeekReasoningEffort.LOW;
        }
        return effort;
    }

    /**
     * 基于多跳先验基准轨迹集运行 Bates-Hoeffding Conformal UCB 阈值校准，开放可达的 {@code MEDIUM} 推理带。
     */
    public synchronized ConformalCalibrationResult calibrateFromDefaultMultiHopPrior() {
        List<CalibrationTrace> priorTraces = new ArrayList<>();
        // Phase 10b 校准：构造 64 条覆盖低难(0.08..0.46)、多跳中难(0.47..0.75)与高难修复(0.76..0.96)的无偏先验样本，
        // 降低 UCB 余量并将 LOW->MEDIUM 阈值精准收紧至 ~0.45，避免普通单/双跳步过度升档产生无效思考 Token 税。
        for (int i = 0; i < 64; i++) {
            double s = 0.08 + i * 0.014;
            boolean iso = s < 0.22;
            boolean noneOk = s < 0.22;
            boolean lowOk = s < 0.48;
            boolean medOk = s < 0.76;
            priorTraces.add(new CalibrationTrace(
                    Math.min(0.98, s), iso, true, noneOk, lowOk, medOk,
                    60.0, 240.0, 560.0, 1250.0
            ));
        }
        return calibrateConformalThresholds(priorTraces, 0.20, 0.05);
    }

    public double[] getCalibratedThresholds() {
        return calibratedThresholds.clone();
    }

    /**
     * 分布无关有限样本 Conformal UCB 阈值校准（定理 2）：
     * 在保证降级退化风险上置信界 <= epsilon 的前提下最小化期望思考 Token。
     */
    public synchronized ConformalCalibrationResult calibrateConformalThresholds(
            List<CalibrationTrace> traces,
            double epsilon,
            double delta
    ) {
        if (traces == null || traces.isEmpty()) {
            return new ConformalCalibrationResult(
                    calibratedThresholds, 0.0, 1.0, 0.0, epsilon, delta, false
            );
        }
        double safeEps = Math.max(0.01, Math.min(0.99, epsilon));
        double safeDelta = Math.max(1e-6, Math.min(0.5, delta));
        int n = traces.size();
        double ucbMargin = Math.sqrt(Math.log(1.0 / safeDelta) / (2.0 * n));

        double[] bestThresholds = new double[]{0.0, 0.0, 0.0};
        double bestTokens = Double.POSITIVE_INFINITY;
        double bestEmpRisk = 0.0;
        double bestUcbRisk = Math.min(1.0, ucbMargin);
        boolean anyFeasible = false;

        double[] grid = new double[]{0.0, 0.15, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.85, 0.95};
        for (double l1 : grid) {
            for (double l2 : grid) {
                if (l2 < l1) {
                    continue;
                }
                for (double l3 : grid) {
                    if (l3 < l2) {
                        continue;
                    }
                    double lossSum = 0.0;
                    double tokSum = 0.0;
                    for (CalibrationTrace tr : traces) {
                        DeepSeekReasoningEffort e = mapScoreWithThresholds(tr.difficultyScore(), tr.isolatedContext(), l1, l2, l3);
                        boolean ok = switch (e) {
                            case NONE -> tr.noneSucceeded();
                            case LOW -> tr.lowSucceeded();
                            case MEDIUM -> tr.mediumSucceeded();
                            case HIGH -> tr.highSucceeded();
                        };
                        double toks = switch (e) {
                            case NONE -> tr.noneTokens();
                            case LOW -> tr.lowTokens();
                            case MEDIUM -> tr.mediumTokens();
                            case HIGH -> tr.highTokens();
                        };
                        if (tr.highSucceeded() && !ok) {
                            lossSum += 1.0;
                        }
                        tokSum += toks;
                    }
                    double empRisk = lossSum / n;
                    double ucbRisk = Math.min(1.0, empRisk + ucbMargin);
                    double avgTok = tokSum / n;
                    if (ucbRisk <= safeEps && avgTok < bestTokens) {
                        anyFeasible = true;
                        bestTokens = avgTok;
                        bestEmpRisk = empRisk;
                        bestUcbRisk = ucbRisk;
                        bestThresholds = new double[]{l1, l2, l3};
                    }
                }
            }
        }

        if (!anyFeasible) {
            // 若校准集过小导致 UCB 边界无法在任意降级下满足 epsilon，退回最保守全 HIGH (0, 0, 0)
            double highTok = traces.stream().mapToDouble(CalibrationTrace::highTokens).average().orElse(0.0);
            this.calibratedThresholds = new double[]{0.0, 0.0, 0.0};
            this.conformalCalibrated = true;
            return new ConformalCalibrationResult(
                    this.calibratedThresholds, 0.0, Math.min(1.0, ucbMargin), highTok, safeEps, safeDelta, false
            );
        }

        this.calibratedThresholds = bestThresholds.clone();
        this.conformalCalibrated = true;
        return new ConformalCalibrationResult(
                bestThresholds, bestEmpRisk, bestUcbRisk, bestTokens, safeEps, safeDelta, true
        );
    }

    public Map<String, Object> getTelemetryStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        double[] th = calibratedThresholds;
        map.put("totalStepDecisions", totalStepDecisions.get());
        map.put("v5ManifoldDecisions", v5ManifoldDecisions.get());
        map.put("v6DampedDecisions", v6DampedDecisions.get());
        map.put("dampingDownshifts", dampingDownshifts.get());
        map.put("totalBudgetCapTokensSaved", totalBudgetCapTokensSaved.get());
        map.put("conformalCalibrated", conformalCalibrated);
        map.put("calibratedThresholds", List.of(th[0], th[1], th[2]));
        map.put("noneSteps", effortCounters.get(DeepSeekReasoningEffort.NONE).get());
        map.put("lowSteps", effortCounters.get(DeepSeekReasoningEffort.LOW).get());
        map.put("mediumSteps", effortCounters.get(DeepSeekReasoningEffort.MEDIUM).get());
        map.put("highSteps", effortCounters.get(DeepSeekReasoningEffort.HIGH).get());
        return Collections.unmodifiableMap(map);
    }

    /**
     * Phase 11.1 (ED-SCR) 新增：暴露认知阻尼步级控算专项遥测指标（供 PhdStudioObservabilityController 展示）。
     */
    public Map<String, Object> getDampingTelemetryStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        double[] th = calibratedThresholds;
        map.put("v6DampedDecisions", v6DampedDecisions.get());
        map.put("dampingDownshifts", dampingDownshifts.get());
        map.put("totalBudgetCapTokensSaved", totalBudgetCapTokensSaved.get());
        map.put("innovationThreshold", DEFAULT_INNOVATION_THRESHOLD);
        map.put("gammaMin", DEFAULT_GAMMA_MIN);
        map.put("calibratedThresholds", List.of(th[0], th[1], th[2]));
        return Collections.unmodifiableMap(map);
    }

    private static DeepSeekReasoningEffort mapScoreWithThresholds(
            double s, boolean isolated, double l1, double l2, double l3) {
        if (s < l1 && isolated) {
            return DeepSeekReasoningEffort.NONE;
        }
        if (s < l2) {
            return DeepSeekReasoningEffort.LOW;
        }
        if (s < l3) {
            return DeepSeekReasoningEffort.MEDIUM;
        }
        return DeepSeekReasoningEffort.HIGH;
    }

    private record TopCosineStats(double firstHitResidual, double top2Margin) {}

    private static TopCosineStats computeTopCosineStats(float[] query, List<float[]> candidates) {
        if (query == null || query.length == 0 || candidates == null || candidates.isEmpty()) {
            return new TopCosineStats(0.35, 0.20);
        }
        double top1 = -1.0;
        double top2 = -1.0;
        for (float[] cand : candidates) {
            if (cand == null || cand.length != query.length) {
                continue;
            }
            double cos = cosine(query, cand);
            if (cos > top1) {
                top2 = top1;
                top1 = cos;
            } else if (cos > top2) {
                top2 = cos;
            }
        }
        if (top1 < -0.999) {
            return new TopCosineStats(0.35, 0.20);
        }
        double residual = clamp01(0.5 * (1.0 - top1));
        double margin = top2 < -0.999 ? 0.35 : Math.max(0.0, top1 - top2);
        return new TopCosineStats(residual, margin);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        if (denom <= 1e-12) {
            return 0.0;
        }
        return Math.max(-1.0, Math.min(1.0, dot / denom));
    }

    private static double l2Norm(float[] vec) {
        double sum = 0.0;
        for (float v : vec) {
            sum += (double) v * v;
        }
        return Math.sqrt(sum);
    }

    private static double[] toDoubleUnit(float[] vec) {
        double norm = l2Norm(vec);
        double inv = norm > 1e-12 ? (1.0 / norm) : 1.0;
        double[] out = new double[vec.length];
        for (int i = 0; i < vec.length; i++) {
            out[i] = vec[i] * inv;
        }
        return out;
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0.0 : Math.max(0.0, Math.min(1.0, v));
    }
}
