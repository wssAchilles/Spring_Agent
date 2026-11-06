package tech.qiantong.qknow.hermes.cognitive.core.engine;

import tech.qiantong.qknow.hermes.cognitive.core.model.CognitivePlan;
import tech.qiantong.qknow.hermes.cognitive.core.model.PlanStep;
import tech.qiantong.qknow.hermes.cognitive.core.model.PlanVerificationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class PlanVerifier {
    public PlanVerificationResult verify(CognitivePlan plan, int maxSteps) {
        if (plan == null || plan.steps().isEmpty()) {
            return new PlanVerificationResult(false, java.util.List.of("EMPTY_PLAN"));
        }
        if (maxSteps < 1 || plan.steps().size() > maxSteps) {
            return new PlanVerificationResult(false, java.util.List.of("STEP_LIMIT_EXCEEDED"));
        }
        Set<String> previous = new HashSet<>();
        var errors = new ArrayList<String>();
        for (PlanStep step : plan.steps()) {
            if (step == null || step.stepId() == null || step.stepId().isBlank()) {
                errors.add("MISSING_STEP_ID");
                continue;
            }
            if (!previous.add(step.stepId())) {
                errors.add("DUPLICATE_STEP_ID");
            }
            if (step.objective() == null || step.objective().isBlank()) {
                errors.add("MISSING_OBJECTIVE");
            }
            for (String dependency : step.dependencies()) {
                if (dependency == null || !previous.contains(dependency) || dependency.equals(step.stepId())) {
                    errors.add("DEPENDENCY_NOT_PREVIOUS");
                }
            }
        }
        return new PlanVerificationResult(errors.isEmpty(), errors);
    }

    public PlanVerificationResult verifyReplacement(PlanStep original, PlanStep replacement,
                                                    Set<String> completedStepIds) {
        if (original == null || replacement == null || !original.stepId().equals(replacement.stepId())) {
            return new PlanVerificationResult(false, java.util.List.of("REPAIR_STEP_ID_MISMATCH"));
        }
        if (replacement.objective() == null || replacement.objective().isBlank()
                || replacement.objective().length() > 2000) {
            return new PlanVerificationResult(false, java.util.List.of("INVALID_REPAIR_OBJECTIVE"));
        }
        if (replacement.dependencies().stream().anyMatch(id -> !completedStepIds.contains(id))) {
            return new PlanVerificationResult(false, java.util.List.of("REPAIR_DEPENDENCY_NOT_COMPLETED"));
        }
        return new PlanVerificationResult(true, java.util.List.of());
    }

    public PlanVerificationResult verifyAppend(CognitivePlan plan, PlanStep appended, int maxSteps) {
        if (plan.steps().size() >= maxSteps) {
            return new PlanVerificationResult(false, java.util.List.of("STEP_LIMIT_EXCEEDED"));
        }
        Set<String> completed = new HashSet<>();
        plan.steps().forEach(step -> completed.add(step.stepId()));
        if (appended == null || appended.stepId() == null || appended.stepId().isBlank()
                || completed.contains(appended.stepId()) || appended.objective() == null
                || appended.objective().isBlank() || appended.objective().length() > 2000) {
            return new PlanVerificationResult(false, java.util.List.of("INVALID_APPENDED_REPAIR"));
        }
        if (appended.dependencies().stream().anyMatch(id -> !completed.contains(id))) {
            return new PlanVerificationResult(false, java.util.List.of("REPAIR_DEPENDENCY_NOT_COMPLETED"));
        }
        return new PlanVerificationResult(true, java.util.List.of());
    }
}
