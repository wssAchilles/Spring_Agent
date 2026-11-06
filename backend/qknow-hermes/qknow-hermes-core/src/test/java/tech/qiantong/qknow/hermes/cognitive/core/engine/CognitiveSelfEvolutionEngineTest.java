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
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.FileSystemExperienceStore;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveExecutionResult;
import tech.qiantong.qknow.hermes.cognitive.core.model.PlanStep;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CognitiveSelfEvolutionEngineTest {
    @TempDir Path temp;

    @Test
    void failedStepIsRepairedOnceAndSuccessfulStrategyIsAdoptedThenReused() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(71, List.of("kb-alpha"));
        String lesson = "Before external action, validate prerequisites and preserve prior results.";
        ChatModel firstModel = model(
                "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"inspect\",\"dependencies\":[]},"
                        + "{\"stepId\":\"s2\",\"objective\":\"update\",\"dependencies\":[\"s1\"]}]}",
                "{\"accepted\":false,\"repair\":{\"stepId\":\"s2\",\"objective\":\"validate then update\"},"
                        + "\"strategy\":\"" + lesson + "\"}",
                "completed output",
                "{\"accepted\":true,\"repair\":null,\"strategy\":\"Keep a concise task checklist.\"}");
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicReference<String> repairPrompt = new AtomicReference<>();

        CognitiveExecutionResult first = engine.execute("request-1", "complete a two-step task", "base prompt",
                firstModel, domain, "a".repeat(64), 5, 2,
                (step, systemPrompt, previous) -> {
                    if (step.stepId().equals("s1")) {
                        assertTrue(previous.isEmpty());
                        firstCalls.incrementAndGet();
                        return CognitiveSelfEvolutionEngine.StepExecution.success("inspected");
                    }
                    if (step.objective().equals("update")) {
                        firstCalls.incrementAndGet();
                        return CognitiveSelfEvolutionEngine.StepExecution.failed("TOOL_FAILURE");
                    }
                    repairPrompt.set(systemPrompt);
                    firstCalls.incrementAndGet();
                    assertEquals("inspected", previous.get("s1"));
                    return CognitiveSelfEvolutionEngine.StepExecution.success("updated");
                });

        assertEquals(CognitiveExecutionResult.Status.COMPLETED, first.status());
        assertEquals(3, firstCalls.get());
        assertTrue(repairPrompt.get().contains(lesson));
        assertEquals(2, first.trace().reflections().size());
        assertTrue(first.trace().reflections().getLast().accepted());
        var adopted = library.recallForProposer(domain, "cognitive-evolution", 10).matchedEntries();
        assertEquals(1, adopted.size());
        assertEquals(ExperienceDisposition.ADOPTED, adopted.getFirst().disposition());
        assertEquals(lesson, adopted.getFirst().lesson());
        assertTrue(first.trace().experienceEvents().stream().anyMatch(e -> e.action().equals("AUTO_ADOPTED")));

        AtomicReference<String> reusedPrompt = new AtomicReference<>();
        ChatModel nextModel = model(
                "{\"steps\":[{\"stepId\":\"n1\",\"objective\":\"perform next task\",\"dependencies\":[]}]}",
                "next answer",
                "{\"accepted\":true,\"repair\":null,\"strategy\":\"Another candidate lesson that is not yet applied.\"}");
        CognitiveExecutionResult next = engine.execute("request-2", "perform a similar task", "base prompt",
                nextModel, domain, "b".repeat(64), 5, 2,
                (step, systemPrompt, previous) -> {
                    reusedPrompt.set(systemPrompt);
                    return CognitiveSelfEvolutionEngine.StepExecution.success("next result");
                });

        assertEquals(CognitiveExecutionResult.Status.COMPLETED, next.status());
        assertTrue(reusedPrompt.get().contains(lesson));
        ExperienceEntryView feedback = ExperienceEntryView.from(
                library.find(domain, adopted.getFirst().entryId()).orElseThrow());
        assertEquals(1, feedback.attempts());
        assertEquals(1, feedback.successes());
        assertTrue(library.recallForProposer(domain, "cognitive-evolution", 10).matchedEntries().stream()
                .anyMatch(entry -> entry.lesson().equals("Another candidate lesson that is not yet applied.")));
    }

    @Test
    void mismatchedRepairIsRejectedWithoutExecutingIt() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ChatModel model = model(
                "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"fail once\",\"dependencies\":[]}]}",
                "{\"accepted\":false,\"repair\":{\"stepId\":\"other\",\"objective\":\"invalid replacement\"},"
                        + "\"strategy\":\"Do not rely on an invalid repair proposal.\"}");
        AtomicInteger executions = new AtomicInteger();
        CognitiveExecutionResult result = engine.execute("request-3", "run task", "prompt", model,
                ExperienceDomain.cognitiveScope(5, List.of()), "c".repeat(64), 3, 2,
                (step, systemPrompt, previous) -> {
                    executions.incrementAndGet();
                    return CognitiveSelfEvolutionEngine.StepExecution.failed("FAILED");
                });

        assertEquals(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, result.status());
        assertEquals(1, executions.get());
        assertEquals("REPAIR_REJECTED", result.trace().terminationCode());
    }

    @Test
    void repairAttemptsAreBounded() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ChatModel model = model(
                "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"fail\",\"dependencies\":[]}]}",
                "{\"accepted\":false,\"repair\":{\"stepId\":\"s1\",\"objective\":\"retry once\"},"
                        + "\"strategy\":\"Check one prerequisite before attempting the operation.\"}",
                "{\"accepted\":false,\"repair\":{\"stepId\":\"s1\",\"objective\":\"retry again\"},"
                        + "\"strategy\":\"Check one prerequisite before attempting the operation.\"}");
        AtomicInteger executions = new AtomicInteger();
        CognitiveExecutionResult result = engine.execute("request-4", "run task", "prompt", model,
                null, "d".repeat(64), 3, 1,
                (step, systemPrompt, previous) -> {
                    executions.incrementAndGet();
                    return CognitiveSelfEvolutionEngine.StepExecution.failed("FAILED");
                });

        assertEquals(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, result.status());
        assertEquals(2, executions.get());
        assertEquals(2, result.trace().repairs().size());
        assertFalse(result.trace().repairs().getLast().proposalAccepted());
    }

    @Test
    void repeatedRuntimeFailuresRetireStrategyAndArchivedVersionCanBeReused() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(81, List.of("kb-learning"));
        ExperienceEntry initial = strategy("feedback-strategy", domain, 0, 0, 0.8);
        library.recordExperience(initial);

        CognitiveExecutionResult last = null;
        for (int i = 1; i <= 3; i++) {
            ChatModel model = model(
                    "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"execute task\",\"dependencies\":[]}]}",
                    "{\"accepted\":false,\"repair\":null,\"strategy\":\"\"}");
            last = engine.execute("feedback-request-" + i, "execute repeated task", "prompt", model,
                    domain, "e".repeat(64), 3, 1,
                    (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.failed("TOOL_FAILED"));
            assertEquals(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, last.status());
        }

        ExperienceEntry retired = library.find(domain, initial.entryId()).orElseThrow();
        assertTrue(retired.retired());
        assertEquals(3, retired.feedbackAttempts());
        assertEquals(0, retired.feedbackSuccesses());
        assertEquals(3, retired.consecutiveFailures());
        assertTrue(last.trace().experienceEvents().stream().anyMatch(event ->
                event.action().equals("FEEDBACK_AND_RETIRE") && event.retired()));
        assertFalse(library.recallForProposer(domain, "cognitive-evolution", 10).hasRelevantExperience());

        ExperienceEntry restored = engine.restoreStrategy(domain, initial.entryId(), 1);
        assertFalse(restored.retired());
        assertEquals(5, restored.version());
        assertEquals(0, restored.consecutiveFailures());
        assertTrue(library.recallForProposer(domain, "cognitive-evolution", 10).hasRelevantExperience());
    }

    @Test
    void ucbPrefersAnUnderexploredAdoptedStrategyAndReportsScoreParts() {
        ExperienceDomain domain = new ExperienceDomain(7, 8, "cognitive_task");
        ExperienceStrategySelector selector = new ExperienceStrategySelector();
        ExperienceEntry established = strategy("established", domain, 10, 10, 0.9);
        ExperienceEntry underexplored = strategy("underexplored", domain, 0, 0, 0.5);

        ExperienceStrategySelector.Selection selection = selector.select(List.of(established, underexplored));

        assertEquals("underexplored", selection.selected().entryId());
        assertEquals(2, selection.scores().size());
        assertTrue(selection.scores().getFirst().exploration() > 0.0);
        assertEquals(selection.scores().getFirst().exploitation() + selection.scores().getFirst().exploration(),
                selection.scores().getFirst().score(), 1e-9);
    }

    private ChatModel model(String... answers) {
        ChatModel model = mock(ChatModel.class);
        AtomicInteger index = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> response(answers[index.getAndIncrement()]));
        return model;
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private ExperienceEntry strategy(String id, ExperienceDomain domain, int attempts, int successes,
                                     double confidence) {
        return new ExperienceEntry(id, domain, "cognitive-evolution",
                "A reusable strategy for validating task progress before choosing actions.", "runtime evidence",
                new EvidenceReference("evidence-" + id, "request-" + id, "e".repeat(64), "COGNITIVE"),
                confidence, "request-" + id, "f".repeat(64), ExperienceDisposition.ADOPTED, false,
                AttributionStatus.UNRESOLVED, 1, false, null, Instant.now(), attempts, successes);
    }

    private record ExperienceEntryView(int attempts, int successes) {
        private static ExperienceEntryView from(
                tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry entry) {
            return new ExperienceEntryView(entry.feedbackAttempts(), entry.feedbackSuccesses());
        }
    }
}
