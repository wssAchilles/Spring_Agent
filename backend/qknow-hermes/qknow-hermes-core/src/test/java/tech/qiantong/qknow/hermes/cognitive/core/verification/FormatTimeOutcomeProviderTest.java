package tech.qiantong.qknow.hermes.cognitive.core.verification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatTimeOutcomeProviderTest {
    private final FormatTimeOutcomeProvider provider = new FormatTimeOutcomeProvider();

    @Test
    void verifiesExplicitFormatAndRejectsMismatchedResult() {
        String arguments = "{\"timeStr\":\"2026-11-10 12:34:56\","
                + "\"formatPattern\":\"yyyy-MM-dd\"}";

        assertEquals(CognitiveToolOutcomeEvidence.Status.APPLIED,
                provider.evaluate(arguments, "2026-11-10").status());
        assertEquals(CognitiveToolOutcomeEvidence.Status.REJECTED,
                provider.evaluate(arguments, "2026/11/10").status());
    }

    @Test
    void unknownWhenFormatIsImplicitOrResultIsMalformed() {
        assertEquals(CognitiveToolOutcomeEvidence.Status.UNKNOWN,
                provider.evaluate("{\"timeStr\":\"2026-11-10 12:34:56\"}", "2026-11-10 12:34:56").status());
        assertEquals(CognitiveToolOutcomeEvidence.Status.UNKNOWN,
                provider.evaluate("not-json", "2026-11-10").status());
    }
}
