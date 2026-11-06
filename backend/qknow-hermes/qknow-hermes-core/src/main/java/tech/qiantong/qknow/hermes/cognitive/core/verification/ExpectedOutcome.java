package tech.qiantong.qknow.hermes.cognitive.core.verification;

/** Explicit task result contract; it must come from a trusted task source, not model reflection. */
public record ExpectedOutcome(String familyId, String value) {
    public ExpectedOutcome {
        if (familyId == null || familyId.isBlank()) {
            throw new IllegalArgumentException("familyId is required");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("expected outcome value is required");
        }
    }
}
