package tech.qiantong.qknow.hermes.cognitive.treesearch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单调保守原假设保护的反谄媚非对称竞争仲裁器（Phase 10.2 MA-BCBT，定理 2）。
 * <p>
 * 对照 ICLR 2024 Self-Correction 退化边界与 DSPy 严格高水位不变量：
 * 1. 若初答存在客观硬缺陷且新分支消除全部硬缺陷 → 接受纠偏；
 * 2. 若初答无客观硬缺陷，**严禁纯 LLM 主观疑心翻转正确初答**：新分支必须同时满足
 *    (a) 无硬缺陷、(b) 带来了客观新增工具见证或通过的 CodeAct 见证、(c) 综合得分严格超越初答加安全裕度 {@code delta >= 0.08}；
 *    平局与无新证据分支恒等锁定保留初答。
 * </p>
 */
public class MonotonicBranchAcceptanceArbiter {

    public static final double DEFAULT_ACCEPTANCE_MARGIN_DELTA = 0.08;

    private final AtomicLong totalArbitrations = new AtomicLong(0L);
    private final AtomicLong acceptedRefinements = new AtomicLong(0L);
    private final AtomicLong rejectedFlipProtections = new AtomicLong(0L);

    /**
     * 执行非对称证据支配仲裁裁决。
     */
    public MonotonicBranchAcceptanceReceipt arbitrate(
            String executionId,
            NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome primaryEval,
            boolean primarySpecHardViolation,
            boolean primaryMissingRequiredTool,
            NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome candidateEval,
            boolean candidateSpecHardViolation,
            int newObjectiveWitnessCount,
            boolean newCodeActPassed,
            double acceptanceMarginDelta
    ) {
        totalArbitrations.incrementAndGet();
        double safeMargin = acceptanceMarginDelta > 0.0 ? acceptanceMarginDelta : DEFAULT_ACCEPTANCE_MARGIN_DELTA;

        boolean primaryHardDefect = primarySpecHardViolation
                || primaryMissingRequiredTool
                || (primaryEval != null && primaryEval.hardFailureDetected());
        boolean candidateHardDefect = candidateSpecHardViolation
                || (candidateEval == null)
                || candidateEval.hardFailureDetected();
        boolean hasNewWitness = newObjectiveWitnessCount > 0 || newCodeActPassed;

        double primaryVal = primaryEval != null ? primaryEval.compositeValue() : 0.0;
        double candidateVal = candidateEval != null ? candidateEval.compositeValue() : 0.0;

        if (candidateHardDefect) {
            rejectedFlipProtections.incrementAndGet();
            return new MonotonicBranchAcceptanceReceipt(
                    executionId, primaryHardDefect, true, hasNewWitness,
                    primaryVal, candidateVal, safeMargin, false,
                    "CANDIDATE_HAS_OBJECTIVE_HARD_DEFECT"
            );
        }

        if (primaryHardDefect) {
            acceptedRefinements.incrementAndGet();
            return new MonotonicBranchAcceptanceReceipt(
                    executionId, true, false, hasNewWitness,
                    primaryVal, candidateVal, safeMargin, true,
                    "REPAIRED_PRIMARY_OBJECTIVE_DEFECT"
            );
        }

        if (!hasNewWitness) {
            rejectedFlipProtections.incrementAndGet();
            return new MonotonicBranchAcceptanceReceipt(
                    executionId, false, false, false,
                    primaryVal, candidateVal, safeMargin, false,
                    "REJECTED_SYCOPHANTIC_NO_NEW_OBJECTIVE_WITNESS"
            );
        }

        if (candidateVal >= primaryVal + safeMargin) {
            acceptedRefinements.incrementAndGet();
            return new MonotonicBranchAcceptanceReceipt(
                    executionId, false, false, true,
                    primaryVal, candidateVal, safeMargin, true,
                    "ACCEPTED_DOMINANT_NEW_WITNESS_WITH_MARGIN"
            );
        }

        rejectedFlipProtections.incrementAndGet();
        return new MonotonicBranchAcceptanceReceipt(
                executionId, false, false, true,
                primaryVal, candidateVal, safeMargin, false,
                "REJECTED_INSUFFICIENT_VALUE_MARGIN"
        );
    }

    public Map<String, Object> getTelemetryStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalArbitrations", totalArbitrations.get());
        map.put("acceptedRefinements", acceptedRefinements.get());
        map.put("rejectedFlipProtections", rejectedFlipProtections.get());
        return Collections.unmodifiableMap(map);
    }
}
