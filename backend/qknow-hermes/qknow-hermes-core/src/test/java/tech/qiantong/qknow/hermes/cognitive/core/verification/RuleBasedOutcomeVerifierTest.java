package tech.qiantong.qknow.hermes.cognitive.core.verification;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedOutcomeVerifierTest {
    private final RuleBasedOutcomeVerifier verifier = new RuleBasedOutcomeVerifier();

    @Test
    void structuredOutputAssertionsPassForFieldsTypesAndRanges() {
        OutcomeSignal signal = verifier.verify(new OutcomeVerificationRequest("json-pass",
                OutcomeTaskFamily.STRUCTURED_OUTPUT_ASSERTION,
                "{\"assertions\":{\"name\":{\"type\":\"STRING\",\"equals\":\"Ada\"},"
                        + "\"score\":{\"type\":\"NUMBER\",\"min\":0.5,\"max\":1.0},"
                        + "\"active\":{\"type\":\"BOOLEAN\"}}}",
                null, Map.of(), "{\"name\":\"Ada\",\"score\":0.75,\"active\":true}",
                List.of("fixture:json:1")));

        assertEquals(OutcomeSignal.Status.PASS, signal.status());
        assertEquals("VERIFIED", signal.reasonCode());
        assertDigestsPresent(signal);
    }

    @Test
    void structuredOutputMismatchFailsWithStableReason() {
        OutcomeSignal signal = verifier.verify(new OutcomeVerificationRequest("json-fail",
                OutcomeTaskFamily.STRUCTURED_OUTPUT_ASSERTION,
                "{\"assertions\":{\"count\":{\"type\":\"NUMBER\",\"min\":1,\"max\":3}}}",
                null, Map.of(), "{\"count\":4}", List.of("fixture:json:2")));

        assertEquals(OutcomeSignal.Status.FAIL, signal.status());
        assertEquals("ASSERTION_MISMATCH", signal.reasonCode());
        assertNotEquals("VERIFIED", signal.reasonCode());
    }

    @Test
    void invalidStructuredContractReturnsUnknownInsteadOfFail() {
        OutcomeSignal signal = verifier.verify(new OutcomeVerificationRequest("json-invalid-contract",
                OutcomeTaskFamily.STRUCTURED_OUTPUT_ASSERTION,
                "{\"assertions\":{\"count\":{\"type\":\"UNSUPPORTED\"}}}",
                null, Map.of(), "{\"count\":4}", List.of("fixture:json:invalid")));

        assertEquals(OutcomeSignal.Status.UNKNOWN, signal.status());
        assertEquals("STRUCTURED_CONTRACT_INVALID", signal.reasonCode());
    }

    @Test
    void toolStatusAndSummaryPassWhenBothMatch() {
        OutcomeSignal signal = verifier.verify(new OutcomeVerificationRequest("tool-pass",
                OutcomeTaskFamily.TOOL_STATE_ASSERTION,
                "{\"status\":\"COMPLETED\",\"summaryDigest\":\"abc123\"}",
                "tool=write; state=completed", Map.of("status", "COMPLETED", "summaryDigest", "abc123"),
                null, List.of("fixture:tool:1")));

        assertEquals(OutcomeSignal.Status.PASS, signal.status());
        assertDigestsPresent(signal);
    }

    @Test
    void toolStatusMismatchFails() {
        OutcomeSignal signal = verifier.verify(new OutcomeVerificationRequest("tool-fail",
                OutcomeTaskFamily.TOOL_STATE_ASSERTION,
                "{\"status\":\"COMPLETED\",\"summaryDigest\":\"abc123\"}",
                "tool=write; state=failed", Map.of("status", "FAILED", "summaryDigest", "abc123"),
                null, List.of("fixture:tool:2")));

        assertEquals(OutcomeSignal.Status.FAIL, signal.status());
        assertEquals("TOOL_OUTPUT_MISMATCH", signal.reasonCode());
    }

    @Test
    void arithmeticExpressionPassesWhenAnswerMatchesComputedValue() {
        OutcomeSignal signal = verifier.verify(new OutcomeVerificationRequest("math-pass",
                OutcomeTaskFamily.DETERMINISTIC_LOGIC_ASSERTION, "(2 + 3) * 4", null,
                Map.of(), "20.0", List.of("fixture:math:1")));

        assertEquals(OutcomeSignal.Status.PASS, signal.status());
        assertDigestsPresent(signal);
    }

    @Test
    void arithmeticExpressionFailsWhenAnswerDiffers() {
        OutcomeSignal signal = verifier.verify(new OutcomeVerificationRequest("math-fail",
                OutcomeTaskFamily.DETERMINISTIC_LOGIC_ASSERTION, "2 + 3", null,
                Map.of(), "6", List.of("fixture:math:2")));

        assertEquals(OutcomeSignal.Status.FAIL, signal.status());
        assertEquals("OUTCOME_MISMATCH", signal.reasonCode());
    }

    private static void assertDigestsPresent(OutcomeSignal signal) {
        assertTrue(signal.expectedDigest().matches("[0-9a-f]{64}"));
        assertTrue(signal.observedDigest().matches("[0-9a-f]{64}"));
        assertTrue(signal.evidenceDigest().matches("[0-9a-f]{64}"));
    }
}
