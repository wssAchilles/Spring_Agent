package tech.qiantong.qknow.hermes.cognitive.core.verification;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.hermes.cognitive.core.engine.CognitiveSelfEvolutionEngine;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveExecutionResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OutcomeVerifierTest {
    private final OutcomeVerifier verifier = new RuleBasedOutcomeVerifier();

    @Test
    void missingEvidenceReturnsUnknown() {
        OutcomeSignal signal = verifier.verify(new OutcomeVerificationRequest("task-1",
                OutcomeTaskFamily.STRUCTURED_OUTPUT_ASSERTION,
                "{\"assertions\":{\"ok\":{\"type\":\"BOOLEAN\",\"equals\":true}}}",
                null, Map.of(), "{\"ok\":true}", List.of()));

        assertEquals(OutcomeSignal.Status.UNKNOWN, signal.status());
        assertEquals("REQUIRED_EVIDENCE_MISSING", signal.reasonCode());
        assertFalse(signal.isPass());
    }

    @Test
    void unregisteredFamilyReturnsUnknown() {
        OutcomeSignal signal = verifier.verify(new OutcomeVerificationRequest("task-2", "future-family",
                "expected", null, Map.of(), "observed", List.of("fixture:1")));

        assertEquals(OutcomeSignal.Status.UNKNOWN, signal.status());
        assertEquals("UNREGISTERED_FAMILY", signal.reasonCode());
        assertFalse(signal.isPass());
    }

    @Test
    void unknownSignalIsNeverTreatedAsPass() {
        OutcomeSignal signal = verifier.verify(new OutcomeVerificationRequest("task-3",
                OutcomeTaskFamily.TOOL_STATE_ASSERTION, "{\"status\":\"DONE\"}", null,
                Map.of(), null, List.of("fixture:tool")));

        assertEquals(OutcomeSignal.Status.UNKNOWN, signal.status());
        assertFalse(signal.isPass());
    }

    @Test
    void identicalRequestProducesIdenticalSignalAndStableEvidenceDigest() {
        OutcomeVerificationRequest first = new OutcomeVerificationRequest("task-4",
                OutcomeTaskFamily.DETERMINISTIC_LOGIC_ASSERTION, "(2 + 3) * 4", null,
                Map.of(), "20", List.of("fixture:b", "fixture:a"));
        OutcomeVerificationRequest second = new OutcomeVerificationRequest("task-4",
                OutcomeTaskFamily.DETERMINISTIC_LOGIC_ASSERTION, "(2 + 3) * 4", null,
                Map.of(), "20", List.of("fixture:a", "fixture:b"));

        OutcomeSignal firstSignal = verifier.verify(first);
        OutcomeSignal secondSignal = verifier.verify(second);

        assertEquals(OutcomeSignal.Status.PASS, firstSignal.status());
        assertEquals(firstSignal, secondSignal);
        assertEquals(firstSignal.evidenceDigest(), secondSignal.evidenceDigest());
    }

    @Test
    void verifierDoesNotInteractWithModelAndLegacyCognitivePathStillRuns() {
        ChatModel model = mock(ChatModel.class);
        assertEquals(OutcomeSignal.Status.PASS, verifier.verify(new OutcomeVerificationRequest("task-5",
                OutcomeTaskFamily.DETERMINISTIC_LOGIC_ASSERTION, "6 / 2", null,
                Map.of(), "3", List.of("fixture:math"))).status());
        verifyNoInteractions(model);

        AtomicInteger call = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> switch (call.getAndIncrement()) {
            case 0 -> response("{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"inspect\",\"dependencies\":[]}]}");
            case 1 -> response("answer");
            case 2 -> response("{\"accepted\":true,\"repair\":null,\"strategy\":\"\"}");
            default -> throw new AssertionError("unexpected legacy model call");
        });

        CognitiveExecutionResult oldPath = new CognitiveSelfEvolutionEngine(null).execute(
                "legacy-request", "inspect the fixture", "prompt", model, null,
                "a".repeat(64), 2, 0,
                (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("done"));

        assertEquals(CognitiveExecutionResult.Status.COMPLETED, oldPath.status());
        assertEquals(3, call.get());
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
