package tech.qiantong.qknow.hermes.cognitive.core.verification;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable declaration of a deterministic, offline-verifiable task family. */
public record OutcomeTaskFamily(
        String familyId,
        int version,
        Set<String> supportedTaskTypes,
        Set<String> requiredEvidence,
        boolean deterministic,
        boolean offlineOnly) {

    public static final String STRUCTURED_OUTPUT_ASSERTION = "STRUCTURED_OUTPUT_ASSERTION";
    public static final String TOOL_STATE_ASSERTION = "TOOL_STATE_ASSERTION";
    public static final String DETERMINISTIC_LOGIC_ASSERTION = "DETERMINISTIC_LOGIC_ASSERTION";

    public OutcomeTaskFamily {
        if (familyId == null || familyId.isBlank()) {
            throw new IllegalArgumentException("familyId is required");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        supportedTaskTypes = immutableNonBlankSet(supportedTaskTypes, "supportedTaskTypes");
        requiredEvidence = immutableNonBlankSet(requiredEvidence, "requiredEvidence");
    }

    public static OutcomeTaskFamily structuredOutput() {
        return new OutcomeTaskFamily(STRUCTURED_OUTPUT_ASSERTION, 1,
                Set.of("json_field_assertion"),
                Set.of("expectedOutcome", "finalAnswer", "evidenceRefs"), true, true);
    }

    public static OutcomeTaskFamily toolState() {
        return new OutcomeTaskFamily(TOOL_STATE_ASSERTION, 1,
                Set.of("tool_status_assertion"),
                Set.of("expectedOutcome", "toolOutputs", "evidenceRefs"), true, true);
    }

    public static OutcomeTaskFamily deterministicLogic() {
        return new OutcomeTaskFamily(DETERMINISTIC_LOGIC_ASSERTION, 1,
                Set.of("arithmetic_expression_assertion"),
                Set.of("expectedOutcome", "finalAnswer", "evidenceRefs"), true, true);
    }

    private static Set<String> immutableNonBlankSet(Set<String> values, String name) {
        Objects.requireNonNull(values, name);
        var copy = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " cannot contain blank values");
            }
            copy.add(value);
        }
        return Set.copyOf(copy);
    }
}
