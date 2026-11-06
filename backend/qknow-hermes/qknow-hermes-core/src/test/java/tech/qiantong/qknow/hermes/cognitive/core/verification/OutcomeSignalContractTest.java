package tech.qiantong.qknow.hermes.cognitive.core.verification;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutcomeSignalContractTest {
    private final RuleBasedOutcomeVerifier verifier = new RuleBasedOutcomeVerifier();

    @Test
    void passRequiresAllThreeSha256Digests() {
        assertThrows(IllegalArgumentException.class, () -> new OutcomeSignal("task", "family",
                OutcomeSignal.Status.PASS, "VERIFIED", "", "b".repeat(64), "c".repeat(64)));
    }

    @Test
    void signalContainsNoRawExpectedOrObservedContent() {
        String secret = "sensitive-expected-and-answer-value";
        OutcomeSignal signal = verifier.verify(new OutcomeVerificationRequest("redacted-task",
                OutcomeTaskFamily.STRUCTURED_OUTPUT_ASSERTION,
                "{\"assertions\":{\"secret\":{\"type\":\"STRING\",\"equals\":\"" + secret + "\"}}}",
                null, Map.of(), "{\"secret\":\"" + secret + "\"}", List.of("fixture:redacted")));

        assertEquals(OutcomeSignal.Status.PASS, signal.status());
        assertFalse(signal.toString().contains(secret));
        assertFalse(signal.reasonCode().contains(secret));
    }

    @Test
    void failureReasonCodeIsStableAndSignalDoesNotCollapseToBoolean() {
        OutcomeVerificationRequest request = new OutcomeVerificationRequest("stable-reason",
                OutcomeTaskFamily.TOOL_STATE_ASSERTION, "{\"status\":\"COMPLETED\"}", null,
                Map.of("status", "FAILED"), null, List.of("fixture:state"));

        OutcomeSignal first = verifier.verify(request);
        OutcomeSignal second = verifier.verify(request);

        assertEquals(OutcomeSignal.Status.FAIL, first.status());
        assertEquals("TOOL_OUTPUT_MISMATCH", first.reasonCode());
        assertEquals(first.reasonCode(), second.reasonCode());
        assertFalse(first.isPass());
        assertTrue(first.observedDigest().matches("[0-9a-f]{64}"));
    }
}
