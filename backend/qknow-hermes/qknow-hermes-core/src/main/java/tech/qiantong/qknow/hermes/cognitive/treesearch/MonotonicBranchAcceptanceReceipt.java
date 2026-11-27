package tech.qiantong.qknow.hermes.cognitive.treesearch;

/**
 * 单调保守反事实树分支竞赛仲裁不可变凭单（Phase 10.2 MA-BCBT，定理 2）。
 */
public record MonotonicBranchAcceptanceReceipt(
        String executionId,
        boolean primaryHadObjectiveHardDefect,
        boolean candidateHadObjectiveHardDefect,
        boolean candidateAddedNewObjectiveWitness,
        double primaryCompositeValue,
        double candidateCompositeValue,
        double acceptanceMarginDelta,
        boolean candidateAccepted,
        String decisionRationale
) {
    public MonotonicBranchAcceptanceReceipt {
        executionId = executionId != null ? executionId : "";
        decisionRationale = decisionRationale != null ? decisionRationale : "";
    }
}
