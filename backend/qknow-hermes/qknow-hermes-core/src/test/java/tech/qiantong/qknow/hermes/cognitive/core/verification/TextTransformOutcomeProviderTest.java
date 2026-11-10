package tech.qiantong.qknow.hermes.cognitive.core.verification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TextTransformOutcomeProviderTest {
    private final TextTransformOutcomeProvider provider = new TextTransformOutcomeProvider();

    @Test
    void verifiesAppliedAndRejectedStructuredResults() {
        CognitiveToolOutcomeEvidence applied = provider.evaluate(
                "{\"text\":\" hello \",\"operation\":\"trim\"}",
                "{\"result\":\"hello\"}");
        CognitiveToolOutcomeEvidence rejected = provider.evaluate(
                "{\"text\":\"hello\",\"operation\":\"trim\"}",
                "{\"result\":\"HELLO\"}");

        assertEquals(CognitiveToolOutcomeEvidence.Status.APPLIED, applied.status());
        assertEquals("TEXT_TRANSFORM_APPLIED", applied.reasonCode());
        assertFalse(applied.evidenceDigest().isBlank());
        assertEquals(CognitiveToolOutcomeEvidence.Status.REJECTED, rejected.status());
        assertEquals("TEXT_TRANSFORM_RESULT_MISMATCH", rejected.reasonCode());
    }

    @Test
    void refusesToInferOutcomeFromMalformedOrToolErrorResults() {
        CognitiveToolOutcomeEvidence malformed = provider.evaluate("{}", "not-json");
        CognitiveToolOutcomeEvidence error = provider.evaluate(
                "{\"text\":\"hello\",\"operation\":\"trim\"}",
                "{\"error\":\"tool failed\"}");

        assertEquals(CognitiveToolOutcomeEvidence.Status.UNKNOWN, malformed.status());
        assertEquals(CognitiveToolOutcomeEvidence.Status.REJECTED, error.status());
        assertEquals("TEXT_TRANSFORM_REJECTED", error.reasonCode());
    }
}
