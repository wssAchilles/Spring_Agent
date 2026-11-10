package tech.qiantong.qknow.hermes.cognitive.core.verification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitiveToolOutcomeRegistryTest {
    @Test
    void canonicalBusinessStateProducesStableRedactedEvidence() {
        CognitiveToolOutcomeEvidence first = CognitiveToolOutcomeEvidence.fromCanonicalState(
                CognitiveToolOutcomeEvidence.Status.APPLIED, "ORDER_CANCELLED", "order-1:cancelled");
        CognitiveToolOutcomeEvidence second = CognitiveToolOutcomeEvidence.fromCanonicalState(
                CognitiveToolOutcomeEvidence.Status.APPLIED, "ORDER_CANCELLED", "order-1:cancelled");

        assertEquals(first, second);
        assertEquals(64, first.evidenceDigest().length());
        assertFalse(first.toString().contains("order-1"));
        assertEquals("BUSINESS_STATE_UNAVAILABLE", CognitiveToolOutcomeEvidence.fromCanonicalState(
                CognitiveToolOutcomeEvidence.Status.APPLIED, "ORDER_CANCELLED", " ").reasonCode());
    }

    @Test
    void missingOrFailingProviderCannotBecomePassOrApplied() {
        CognitiveToolOutcomeRegistry registry = new CognitiveToolOutcomeRegistry(List.of(
                new CognitiveToolOutcomeProvider() {
                    @Override
                    public String toolName() { return "broken_tool"; }

                    @Override
                    public CognitiveToolOutcomeEvidence evaluate(String argumentsJson, String toolResult) {
                        throw new IllegalStateException("sensitive failure detail");
                    }
                }));

        assertEquals(CognitiveToolOutcomeEvidence.Status.UNKNOWN,
                registry.evaluate("unknown_tool", "{}", "{}").status());
        CognitiveToolOutcomeEvidence failed = registry.evaluate("broken_tool", "{}", "{}");
        assertEquals(CognitiveToolOutcomeEvidence.Status.UNKNOWN, failed.status());
        assertEquals("OUTCOME_PROVIDER_FAILED", failed.reasonCode());
        assertFalse(failed.toString().contains("sensitive failure detail"));
    }

    @Test
    void knownBusinessOutcomeRequiresEvidenceDigest() {
        assertThrows(IllegalArgumentException.class, () -> new CognitiveToolOutcomeEvidence(
                CognitiveToolOutcomeEvidence.Status.REJECTED, "ORDER_NOT_CANCELLABLE", ""));
    }
}
