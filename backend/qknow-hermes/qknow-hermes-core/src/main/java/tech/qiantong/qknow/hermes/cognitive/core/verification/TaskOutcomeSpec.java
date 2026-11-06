package tech.qiantong.qknow.hermes.cognitive.core.verification;

import java.util.List;
import java.util.Objects;

/** Expected result and evidence binding for one task execution. */
public record TaskOutcomeSpec(
        ExpectedOutcome expectedOutcome,
        List<String> evidenceRefs,
        String toolStatusStepId) {

    public TaskOutcomeSpec {
        Objects.requireNonNull(expectedOutcome, "expectedOutcome");
        evidenceRefs = evidenceRefs == null ? List.of()
                : evidenceRefs.stream().map(ref -> ref == null ? "" : ref).toList();
        if (OutcomeTaskFamily.TOOL_STATE_ASSERTION.equals(expectedOutcome.familyId())
                && (toolStatusStepId == null || toolStatusStepId.isBlank())) {
            throw new IllegalArgumentException("toolStatusStepId is required for tool-state outcomes");
        }
        if (toolStatusStepId != null && toolStatusStepId.isBlank()) {
            toolStatusStepId = null;
        }
    }
}
