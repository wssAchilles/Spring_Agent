package tech.qiantong.qknow.hermes.cognitive.core.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.hermes.agent.harness.experience.DefaultExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.EvidenceReference;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDisposition;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry.AttributionStatus;
import tech.qiantong.qknow.hermes.agent.harness.experience.FileSystemExperienceStore;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyLifecycleStatus;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveExecutionResult;
import tech.qiantong.qknow.hermes.cognitive.core.model.PlanStep;
import tech.qiantong.qknow.hermes.cognitive.core.verification.OutcomeSignal;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProbationaryStrategyTest {
    @TempDir Path temp;

    @Test
    void unknownOutcomeUsesExecutionFeedbackToPromoteProbationaryStrategy() {
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(91, List.of("kb-probation"));
        DefaultExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        ExperienceEntry candidate = new ExperienceEntry("probationary-checklist", domain,
                "cognitive-evolution", "A reusable strategy for validating task progress before choosing actions.",
                "offline candidate", new EvidenceReference("evidence-probation", "source-run",
                "a".repeat(64), "COGNITIVE"), 0.5, "source-run", "b".repeat(64),
                ExperienceDisposition.OBSERVED, false, AttributionStatus.UNRESOLVED,
                1, false, null, Instant.now());
        ExperienceEntry probation = library.createProbation(candidate);
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);

        CognitiveExecutionResult last = null;
        for (int run = 1; run <= 3; run++) {
            ChatModel model = successfulUnknownModel();
            last = engine.execute("probation-run-" + run, "complete a short task", "base prompt",
                    model, domain, "c".repeat(64), 2, 0,
                    (step, prompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("done"));
            assertEquals(CognitiveExecutionResult.Status.COMPLETED, last.status());
            assertEquals(OutcomeSignal.Status.UNKNOWN, last.trace().outcomeStatus());
            verify(model, times(3)).call(any(Prompt.class));
        }

        ExperienceEntry promoted = library.find(domain, probation.entryId()).orElseThrow();
        assertEquals(3, promoted.probationUses());
        assertEquals(3, promoted.probationSuccesses());
        assertEquals(StrategyLifecycleStatus.ADOPTED, promoted.lifecycleStatus());
        assertTrue(last.trace().experienceEvents().stream()
                .anyMatch(event -> event.action().equals("PROBATION_PROMOTED")
                        && event.outcomeStatus() == OutcomeSignal.Status.UNKNOWN));
    }

    private ChatModel successfulUnknownModel() {
        ChatModel model = mock(ChatModel.class);
        AtomicInteger calls = new AtomicInteger();
        List<String> outputs = List.of(
                "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"perform task\",\"dependencies\":[]}]}",
                "task completed",
                "{\"accepted\":true,\"repair\":null,\"strategy\":\"\"}");
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(
                new Generation(new AssistantMessage(outputs.get(calls.getAndIncrement()))))));
        return model;
    }
}
