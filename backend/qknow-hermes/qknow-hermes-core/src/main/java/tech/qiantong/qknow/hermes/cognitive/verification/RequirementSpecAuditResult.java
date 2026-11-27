package tech.qiantong.qknow.hermes.cognitive.verification;

import java.util.List;
import java.util.Objects;

/**
 * 六维形式化需求规格审计不可变结果（FRSV，Phase 9.1 定理 4）。
 */
public record RequirementSpecAuditResult(
        ReasoningConsistencyReceipt consistencyReceipt,
        QuestionRequirementSpec requirementSpec,
        double specSatisfactionScore,
        double multiplicativePenaltyFactor,
        List<SpecViolation> violations,
        boolean codeActWitnessAligned,
        String correctiveSpecHint
) {
    /**
     * 单条规格违约记录。
     */
    public record SpecViolation(
            String dimension,
            String code,
            String diagnostic,
            boolean hardViolation
    ) {
        public SpecViolation {
            dimension = dimension != null ? dimension : "GENERAL";
            code = code != null ? code : "SPEC_VIOLATION";
            diagnostic = diagnostic != null ? diagnostic : "";
        }
    }

    public RequirementSpecAuditResult {
        Objects.requireNonNull(consistencyReceipt, "consistencyReceipt 不能为空");
        Objects.requireNonNull(requirementSpec, "requirementSpec 不能为空");
        violations = List.copyOf(violations != null ? violations : List.of());
        correctiveSpecHint = correctiveSpecHint != null ? correctiveSpecHint : "";
    }

    public int hardViolationCount() {
        int count = 0;
        for (SpecViolation v : violations) {
            if (v.hardViolation()) {
                count++;
            }
        }
        return count;
    }

    public boolean hasHardViolations() {
        return hardViolationCount() > 0;
    }

    public boolean passed() {
        return consistencyReceipt.passed() && !hasHardViolations();
    }
}
