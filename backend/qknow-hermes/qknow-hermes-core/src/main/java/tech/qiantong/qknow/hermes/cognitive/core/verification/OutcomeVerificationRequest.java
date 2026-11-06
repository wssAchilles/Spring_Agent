package tech.qiantong.qknow.hermes.cognitive.core.verification;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Data-only input to an offline outcome verifier. */
public record OutcomeVerificationRequest(
        String taskId,
        String familyId,
        String expectedOutcome,
        String observedTrace,
        Map<String, String> toolOutputs,
        String finalAnswer,
        List<String> evidenceRefs) {

    public OutcomeVerificationRequest {
        toolOutputs = immutableMap(toolOutputs);
        evidenceRefs = evidenceRefs == null ? List.of()
                : evidenceRefs.stream().map(ref -> ref == null ? "" : ref).toList();
    }

    private static Map<String, String> immutableMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, String>();
        values.forEach((key, value) -> copy.put(key == null ? "" : key, value == null ? "" : value));
        return Map.copyOf(copy);
    }
}
