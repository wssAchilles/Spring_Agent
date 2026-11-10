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
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyLifecycleStatus;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyTemplate;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyTaskType;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyVariant;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveExecutionResult;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveTrace;
import tech.qiantong.qknow.hermes.cognitive.core.model.PlanStep;
import tech.qiantong.qknow.hermes.cognitive.core.verification.ExpectedOutcome;
import tech.qiantong.qknow.hermes.cognitive.core.verification.OutcomeSignal;
import tech.qiantong.qknow.hermes.cognitive.core.verification.OutcomeTaskFamily;
import tech.qiantong.qknow.hermes.cognitive.core.verification.TaskOutcomeSpec;

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
                "{\"completed\":true}",
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
                }, structuredOutcome("{\"assertions\":{\"completed\":{\"type\":\"BOOLEAN\",\"equals\":true}}}"));

        assertEquals(CognitiveExecutionResult.Status.COMPLETED, first.status());
        assertEquals(OutcomeSignal.Status.PASS, first.trace().outcomeSignal().status());
        assertEquals(3, firstCalls.get());
        assertTrue(repairPrompt.get().contains(lesson));
        assertEquals(2, first.trace().reflections().size());
        assertTrue(first.trace().reflections().getLast().accepted());
        var adopted = library.recallForProposer(domain, "cognitive-evolution", 10).matchedEntries();
        assertEquals(1, adopted.size());
        assertEquals(ExperienceDisposition.ADOPTED, adopted.getFirst().disposition());
        assertEquals(StrategyLifecycleStatus.ADOPTED, adopted.getFirst().lifecycleStatus());
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
        assertEquals(OutcomeSignal.Status.UNKNOWN, next.trace().outcomeSignal().status());
        assertTrue(reusedPrompt.get().contains(lesson));
        ExperienceEntryView feedback = ExperienceEntryView.from(
                library.find(domain, adopted.getFirst().entryId()).orElseThrow());
        assertEquals(0, feedback.attempts());
        assertEquals(0, feedback.successes());
        assertFalse(library.recallForProposer(domain, "cognitive-evolution", 10).matchedEntries().stream()
                .anyMatch(entry -> entry.lesson().equals("Another candidate lesson that is not yet applied.")));
        ExperienceEntry probation = library.recallForStrategySelection(domain, "cognitive-evolution", 10)
                .matchedEntries().stream()
                .filter(entry -> entry.lesson().equals("Another candidate lesson that is not yet applied."))
                .findFirst().orElseThrow();
        assertEquals(StrategyLifecycleStatus.PROBATION, probation.lifecycleStatus());
        assertTrue(probation.strategyVariant() != null);
    }

    @Test
    void ucbFiltersToMatchingTaskTypeBeforeScoring() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(701, List.of("kb-task-type"));
        ExperienceEntry general = templateParent("general-entry", "general-template", domain,
                8, 8, 0.99);
        ExperienceEntry mutation = strategy("mutation-entry", domain, 0, 0, 0.4).withStrategy(
                StrategyTemplate.create("mutation-template", 1, null,
                        List.of("direct state mutation request"), List.of("inspect", "mutate", "verify"),
                        List.of("use task-provided mutation tools"), List.of("verify resulting state"),
                        List.of("requested state transition completed"), StrategyTaskType.CANCELLATION), null);
        ExperienceEntry refund = strategy("refund-entry", domain, 8, 8, 0.99).withStrategy(
                StrategyTemplate.create("refund-template", 1, null,
                        List.of("refund request"), List.of("inspect", "refund", "verify"),
                        List.of("use task-provided refund tools"), List.of("verify refund status"),
                        List.of("refund completed"), StrategyTaskType.RETURN_REFUND), null);
        library.recordExperience(general);
        library.recordExperience(mutation);
        library.recordExperience(refund);
        AtomicReference<String> usedPrompt = new AtomicReference<>();
        CognitiveExecutionResult result = engine.execute("request-mutation-type", "Please cancel my order",
                "base prompt", model(
                        "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"inspect\",\"dependencies\":[]}]}",
                        "answer", "{\"accepted\":true,\"repair\":null,"
                                + "\"strategy\":\"Persist the mutation result before completion.\"}"),
                domain, "d".repeat(64), 3, 0,
                (step, systemPrompt, previous) -> {
                    usedPrompt.set(systemPrompt);
                    return CognitiveSelfEvolutionEngine.StepExecution.success("cancelled",
                            List.of(successfulTool("cancel_pending_order")));
                }, null, StrategyTaskType.CANCELLATION);

        assertEquals(CognitiveExecutionResult.Status.COMPLETED, result.status());
        assertTrue(usedPrompt.get().contains(mutation.strategyTemplate().renderGuidance()));
        assertFalse(usedPrompt.get().contains(general.strategyTemplate().renderGuidance()));
        assertFalse(usedPrompt.get().contains(refund.strategyTemplate().renderGuidance()));
        assertEquals(8, library.find(domain, general.entryId()).orElseThrow().feedbackAttempts());
        assertEquals(8, library.find(domain, refund.entryId()).orElseThrow().feedbackAttempts());
        assertEquals(0, library.find(domain, mutation.entryId()).orElseThrow().feedbackAttempts());
        assertTrue(result.trace().states().contains("TASK_TYPE:CANCELLATION"));
        assertTrue(result.trace().states().contains("MUTATION_FEEDBACK_DEFERRED_OUTCOME_UNKNOWN"));
        assertEquals("cancel_pending_order", result.trace().toolExecutionEvents().getFirst().toolName());
    }

    @Test
    void identicalLessonsRemainSeparateAcrossTaskTypes() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(703, List.of("kb-task-identity"));
        String lesson = "Verify the resulting state after applying the requested change.";
        String plan = "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"inspect\",\"dependencies\":[]}]}";

        engine.execute("request-general-identity", "Analyze this request", "base prompt",
                model(plan, "complete", "{\"accepted\":true,\"repair\":null,\"strategy\":\""
                        + lesson + "\"}"), domain, "1".repeat(64), 3, 0,
                (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("done"),
                null, StrategyTaskType.GENERAL);
        engine.execute("request-mutation-identity", "Please modify my address", "base prompt",
                model(plan, "complete", "{\"accepted\":true,\"repair\":null,\"strategy\":\""
                        + lesson + "\"}"), domain, "2".repeat(64), 3, 0,
                (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("done",
                        List.of(successfulTool("modify_pending_order_address"))),
                null, StrategyTaskType.UPDATE);

        List<ExperienceEntry> strategies = library.recallForStrategySelection(
                domain, "cognitive-evolution", 10).matchedEntries();
        assertEquals(2, strategies.size());
        assertEquals(java.util.Set.of(StrategyTaskType.GENERAL, StrategyTaskType.UPDATE), strategies.stream()
                .map(entry -> entry.strategyTemplate().taskType()).collect(java.util.stream.Collectors.toSet()));
        assertFalse(strategies.get(0).entryId().equals(strategies.get(1).entryId()));
    }

    @Test
    void broadAdoptedStrategySeedsSpecificMutationVariantWithoutReceivingFeedback() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(702, List.of("kb-task-fallback"));
        ExperienceEntry general = templateParent("fallback-entry", "fallback-template", domain,
                2, 2, 0.8).withStrategy(StrategyTemplate.create("fallback-template", 1, null,
                List.of("broad state-changing request"), List.of("inspect", "act", "verify"),
                List.of("use tools for the requested action"), List.of("verify the final state"),
                List.of("requested transition completed"), StrategyTaskType.STATE_MUTATION), null);
        library.recordExperience(general);
        AtomicReference<String> usedPrompt = new AtomicReference<>();
        CognitiveExecutionResult result = engine.execute("request-mutation-fallback", "Please modify my address",
                "base prompt", model(
                        "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"inspect\",\"dependencies\":[]}]}",
                        "answer", "{\"accepted\":true,\"repair\":null,"
                                + "\"strategy\":\"Re-read the address after the requested update.\"}"),
                domain, "e".repeat(64), 3, 0,
                (step, systemPrompt, previous) -> {
                    usedPrompt.set(systemPrompt);
                    return CognitiveSelfEvolutionEngine.StepExecution.success("updated",
                            List.of(successfulTool("modify_pending_order_address")));
                }, null, StrategyTaskType.UPDATE);

        assertEquals(CognitiveExecutionResult.Status.COMPLETED, result.status());
        assertTrue(usedPrompt.get().contains(general.strategyTemplate().renderGuidance()));
        assertTrue(result.trace().states().contains("STRATEGY_CROSS_TASK_SEED_SELECTED"));
        assertEquals(2, library.find(domain, general.entryId()).orElseThrow().feedbackAttempts());
        ExperienceEntry mutation = library.recallForStrategySelection(domain, "cognitive-evolution", 10)
                .matchedEntries().stream().filter(entry -> entry.strategyTemplate() != null
                        && entry.strategyTemplate().taskType() == StrategyTaskType.UPDATE)
                .findFirst().orElseThrow();
        assertEquals(StrategyLifecycleStatus.PROBATION, mutation.lifecycleStatus());
        assertEquals("fallback-template", mutation.strategyTemplate().parentTemplateId());
        assertEquals("fallback-template", mutation.strategyVariant().parentTemplateId());
    }

    @Test
    void unknownMutationWithoutObservedWriteDoesNotCountAsProbationSuccess() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(704, List.of("kb-missing-write"));
        StrategyTemplate template = StrategyTemplate.create("cancel-template", 1, null,
                List.of("direct cancellation request"), List.of("inspect", "cancel", "verify"),
                List.of("use the cancellation tool"), List.of("check the resulting order state"),
                List.of("order is cancelled"), StrategyTaskType.CANCELLATION);
        ExperienceEntry candidate = new ExperienceEntry("cancel-template-entry", domain,
                "cognitive-evolution", "Verify cancellation through the order state.", "candidate",
                new EvidenceReference("seed-evidence", "seed-run", "a".repeat(64), "TEST"),
                0.7, "seed-run", "b".repeat(64), ExperienceDisposition.OBSERVED, false,
                AttributionStatus.UNRESOLVED, 1, false, null, Instant.now()).withStrategy(template, null);
        ExperienceEntry probation = library.createProbation(candidate);
        library.recordProbationFeedback(domain, probation.entryId(), true,
                new EvidenceReference("seed-success", "seed-run-1", "c".repeat(64), "TEST"),
                "seed-run-1", "d".repeat(64));
        library.recordProbationFeedback(domain, probation.entryId(), false,
                new EvidenceReference("seed-failure", "seed-run-2", "e".repeat(64), "TEST"),
                "seed-run-2", "f".repeat(64));

        CognitiveExecutionResult result = engine.execute("cancel-without-write", "Please cancel my order",
                "base prompt", model(
                        "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"cancel order\",\"dependencies\":[]}]}",
                        "The order is cancelled.",
                        "{\"accepted\":true,\"repair\":null,\"strategy\":\"Cancel only after checking eligibility.\"}"),
                domain, "e".repeat(64), 3, 0,
                (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("cancelled"),
                null, StrategyTaskType.CANCELLATION);

        ExperienceEntry updated = library.find(domain, probation.entryId()).orElseThrow();
        assertEquals(StrategyLifecycleStatus.PROBATION, updated.lifecycleStatus());
        assertEquals(2, updated.probationUses());
        assertEquals(1, updated.probationSuccesses());
        assertEquals(1, updated.consecutiveFailures());
        assertEquals(List.of(probation.entryId()), library.recallForStrategySelection(
                domain, "cognitive-evolution", 10).matchedEntries().stream()
                .map(ExperienceEntry::entryId).toList());
        assertTrue(result.trace().states().contains("MUTATION_FEEDBACK_DEFERRED_OUTCOME_UNKNOWN"));
        assertTrue(result.trace().toolExecutionEvents().isEmpty());
    }

    @Test
    void observedMutationFamilySeedsDedicatedVariantWithoutCreditingGeneralStrategy() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(705, List.of("kb-observed-family"));
        ExperienceEntry general = templateParent("general-observed-entry", "general-observed-template",
                domain, 0, 0, 0.8);
        library.recordExperience(general);

        CognitiveExecutionResult result = engine.execute("observed-cancellation", "Please cancel my order",
                "base prompt", model(
                        "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"complete the order request\",\"dependencies\":[]}]}",
                        "The order was cancelled.",
                        "{\"accepted\":true,\"repair\":null,"
                                + "\"strategy\":\"Verify cancellation through the order state.\"}"),
                domain, "f".repeat(64), 3, 0,
                (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("cancelled",
                        List.of(successfulTool("cancel_pending_order"))),
                null, StrategyTaskType.GENERAL);

        ExperienceEntry unchangedGeneral = library.find(domain, general.entryId()).orElseThrow();
        ExperienceEntry cancellation = library.recallForStrategySelection(domain, "cognitive-evolution", 10)
                .matchedEntries().stream()
                .filter(entry -> entry.strategyTemplate() != null
                        && entry.strategyTemplate().taskType() == StrategyTaskType.CANCELLATION)
                .findFirst().orElseThrow();
        assertEquals(0, unchangedGeneral.feedbackAttempts());
        assertEquals(StrategyLifecycleStatus.PROBATION, cancellation.lifecycleStatus());
        assertEquals("general-observed-template", cancellation.strategyVariant().parentTemplateId());
        assertTrue(result.trace().states().contains(
                "STRATEGY_TASK_FAMILY_MISMATCH:GENERAL->CANCELLATION:OBSERVED_CANCELLATION"));
        assertTrue(result.trace().states().contains("STRATEGY_FEEDBACK_SKIPPED_TASK_FAMILY_MISMATCH"));
    }

    @Test
    void unknownMutationResponseCreatesProbationWithoutClaimingBusinessSuccess() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(707, List.of("kb-unknown-mutation-result"));

        CognitiveExecutionResult result = engine.execute("unknown-cancel-result", "Please cancel my order",
                "base prompt", model(
                        "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"cancel order\",\"dependencies\":[]}]}",
                        "The cancellation request was processed.",
                        "{\"accepted\":true,\"repair\":null,"
                                + "\"strategy\":\"Check the order state after requesting cancellation.\"}"),
                domain, "2".repeat(64), 2, 0,
                (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("processed",
                        List.of(successfulTool("cancel_pending_order"))),
                null, StrategyTaskType.CANCELLATION);

        ExperienceEntry candidate = library.recallForStrategySelection(domain, "cognitive-evolution", 10)
                .matchedEntries().getFirst();
        assertEquals(OutcomeSignal.Status.UNKNOWN, result.trace().outcomeStatus());
        assertEquals(StrategyLifecycleStatus.PROBATION, candidate.lifecycleStatus());
        assertEquals(0, candidate.probationUses());
        assertEquals(0, candidate.probationSuccesses());
        assertTrue(result.trace().states().contains("MUTATION_FEEDBACK_DEFERRED_OUTCOME_UNKNOWN"));
    }

    @Test
    void unknownMutationWithExplicitToolErrorRecordsNegativeFeedback() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(708, List.of("kb-mutation-tool-error"));
        ExperienceEntry existing = strategy("cancel-error-strategy", domain, 0, 0, 0.8).withStrategy(
                StrategyTemplate.create("cancel-error-template", 1, null,
                        List.of("cancellation requested"), List.of("inspect", "cancel"),
                        List.of("use the cancellation tool"), List.of("verify the result"),
                        List.of("cancellation completes"), StrategyTaskType.CANCELLATION), null);
        library.recordExperience(existing);

        CognitiveExecutionResult result = engine.execute("cancel-tool-error", "Please cancel my order",
                "base prompt", model(
                        "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"cancel order\",\"dependencies\":[]}]}",
                        "The cancellation could not be completed.",
                        "{\"accepted\":true,\"repair\":null,\"strategy\":\"\"}"),
                domain, "3".repeat(64), 2, 0,
                (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("error",
                        List.of(failedTool("cancel_pending_order"))),
                null, StrategyTaskType.CANCELLATION);

        ExperienceEntry updated = library.find(domain, existing.entryId()).orElseThrow();
        assertEquals(OutcomeSignal.Status.UNKNOWN, result.trace().outcomeStatus());
        assertEquals(1, updated.feedbackAttempts());
        assertEquals(0, updated.feedbackSuccesses());
        assertTrue(result.trace().states().contains("MUTATION_FEEDBACK_RECORDED_TOOL_ERROR"));
    }

    @Test
    void structuredMutationOutcomeDrivesProbationFeedback() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(709, List.of("kb-verified-mutation-feedback"));
        StrategyTemplate template = StrategyTemplate.create("verified-cancel-template", 1, null,
                List.of("cancellation requested"), List.of("inspect", "cancel", "verify"),
                List.of("use cancellation tool"), List.of("check resulting order status"),
                List.of("order status is cancelled"), StrategyTaskType.CANCELLATION);
        ExperienceEntry probation = library.createProbation(new ExperienceEntry("verified-cancel-strategy",
                domain, "cognitive-evolution", "Verify cancellation after applying it.", "candidate",
                new EvidenceReference("verified-cancel-evidence", "seed", "4".repeat(64), "TEST"),
                0.5, "seed", "5".repeat(64), ExperienceDisposition.OBSERVED, false,
                AttributionStatus.UNRESOLVED, 1, false, null, Instant.now()).withStrategy(template, null));

        CognitiveExecutionResult result = engine.execute("verified-cancel-feedback", "Please cancel my order",
                "base prompt", model(
                        "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"cancel order\",\"dependencies\":[]}]}",
                        "The cancellation is complete.",
                        "{\"accepted\":true,\"repair\":null,\"strategy\":\"\"}"),
                domain, "6".repeat(64), 2, 0,
                (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("cancelled",
                        List.of(new CognitiveTrace.ToolExecutionEvent(0, "s1", "cancel_pending_order",
                                CognitiveTrace.ToolExecutionEvent.Status.RESPONSE_RECEIVED,
                                CognitiveTrace.ToolExecutionEvent.BusinessOutcome.APPLIED,
                                "ORDER_CANCELLED", "a".repeat(64)))),
                null, StrategyTaskType.CANCELLATION);

        ExperienceEntry updated = library.find(domain, probation.entryId()).orElseThrow();
        assertEquals(OutcomeSignal.Status.UNKNOWN, result.trace().outcomeStatus());
        assertEquals(1, updated.probationUses(), result.trace().states().toString());
        assertEquals(1, updated.probationSuccesses());
        assertTrue(result.trace().states().contains("MUTATION_FEEDBACK_RECORDED_OUTCOME_APPLIED"));
    }

    @Test
    void unrequestedMutationToolDoesNotCreateMutationStrategy() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(706, List.of("kb-unrequested-mutation"));
        ExperienceEntry general = templateParent("general-unrequested-entry", "general-unrequested-template",
                domain, 0, 0, 0.8);
        library.recordExperience(general);

        CognitiveExecutionResult result = engine.execute("unrequested-cancellation", "Handle this order request",
                "base prompt", model(
                        "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"handle the request\",\"dependencies\":[]}]}",
                        "The order was cancelled.",
                        "{\"accepted\":true,\"repair\":null,"
                                + "\"strategy\":\"Verify the order state after cancellation.\"}"),
                domain, "1".repeat(64), 3, 0,
                (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("cancelled",
                        List.of(successfulTool("cancel_pending_order"))),
                null, StrategyTaskType.GENERAL);

        assertEquals(List.of(general.entryId()), library.recallForStrategySelection(
                domain, "cognitive-evolution", 10).matchedEntries().stream()
                .map(ExperienceEntry::entryId).toList());
        assertEquals(0, library.find(domain, general.entryId()).orElseThrow().feedbackAttempts());
        assertTrue(result.trace().states().contains("MUTATION_TOOL_WITHOUT_EXPLICIT_INTENT"));
        assertTrue(result.trace().states().contains(
                "STRATEGY_TASK_FAMILY_MISMATCH:GENERAL->GENERAL:OBSERVED_CANCELLATION"));
    }

    @Test
    void rejectedMutationIsRepairedAndCreditsOnlyTheAppliedVariant() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(710, List.of("kb-mutation-repair"));
        StrategyTemplate template = StrategyTemplate.create("repair-cancel-template", 1, null,
                List.of("cancellation requested"), List.of("inspect", "cancel"),
                List.of("use cancellation tool"), List.of("check order status"),
                List.of("order cancelled"), StrategyTaskType.CANCELLATION);
        ExperienceEntry parent = library.createProbation(new ExperienceEntry("repair-cancel-parent",
                domain, "cognitive-evolution", "Cancel the selected order.", "candidate",
                new EvidenceReference("repair-cancel-evidence", "seed", "a".repeat(64), "TEST"),
                0.5, "seed", "b".repeat(64), ExperienceDisposition.OBSERVED, false,
                AttributionStatus.UNRESOLVED, 1, false, null, Instant.now()).withStrategy(template, null));
        String repairLesson = "Recheck order eligibility before retrying a rejected cancellation.";
        List<String> prompts = new ArrayList<>();
        List<String> answers = List.of(
                "{\"steps\":[{\"stepId\":\"s0\",\"objective\":\"inspect\",\"dependencies\":[]},"
                        + "{\"stepId\":\"s1\",\"objective\":\"cancel\",\"dependencies\":[\"s0\"]}]}",
                "{\"accepted\":false,\"repair\":{\"stepId\":\"s1\",\"objective\":\"recheck and cancel\"},"
                        + "\"strategy\":\"" + repairLesson + "\"}",
                "The order is cancelled.",
                "{\"accepted\":true,\"repair\":null,\"strategy\":\"\"}");
        ChatModel model = mock(ChatModel.class);
        AtomicInteger modelCalls = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            prompts.add(prompt.getInstructions().stream().map(message -> message.getText())
                    .collect(java.util.stream.Collectors.joining("\n")));
            return response(answers.get(modelCalls.getAndIncrement()));
        });
        AtomicInteger mutationCalls = new AtomicInteger();

        CognitiveExecutionResult result = engine.execute("rejected-cancel-repair", "Please cancel my order",
                "base prompt", model, domain, "c".repeat(64), 3, 2,
                (step, systemPrompt, previous) -> {
                    if (step.stepId().equals("s0")) {
                        return CognitiveSelfEvolutionEngine.StepExecution.success("inspected-order");
                    }
                    assertEquals("inspected-order", previous.get("s0"));
                    boolean repaired = mutationCalls.getAndIncrement() > 0;
                    if (repaired) {
                        assertTrue(systemPrompt.contains(repairLesson));
                    }
                    return CognitiveSelfEvolutionEngine.StepExecution.success(repaired ? "cancelled" : "rejected",
                            List.of(new CognitiveTrace.ToolExecutionEvent(0, "s1", "cancel_pending_order",
                                    CognitiveTrace.ToolExecutionEvent.Status.RESPONSE_RECEIVED,
                                    repaired ? CognitiveTrace.ToolExecutionEvent.BusinessOutcome.APPLIED
                                            : CognitiveTrace.ToolExecutionEvent.BusinessOutcome.REJECTED,
                                    repaired ? "ORDER_CANCELLED" : "ORDER_NOT_ELIGIBLE", "d".repeat(64))));
                }, null, StrategyTaskType.CANCELLATION);

        assertEquals(CognitiveExecutionResult.Status.COMPLETED, result.status());
        assertEquals(2, mutationCalls.get());
        assertTrue(prompts.stream().anyMatch(prompt -> prompt.contains("cancel_pending_order")
                && prompt.contains("ORDER_NOT_ELIGIBLE") && prompt.contains("d".repeat(64))
                && prompt.contains("inspected-order")));
        assertEquals(List.of(0, 1), result.trace().toolExecutionEvents().stream()
                .map(CognitiveTrace.ToolExecutionEvent::callIndex).toList());
        assertEquals(List.of(CognitiveTrace.ToolExecutionEvent.BusinessOutcome.REJECTED,
                        CognitiveTrace.ToolExecutionEvent.BusinessOutcome.APPLIED),
                result.trace().toolExecutionEvents().stream()
                        .map(CognitiveTrace.ToolExecutionEvent::businessOutcome).toList());
        ExperienceEntry updatedParent = library.find(domain, parent.entryId()).orElseThrow();
        assertEquals(1, updatedParent.probationUses());
        assertEquals(0, updatedParent.probationSuccesses());
        assertEquals(1, updatedParent.consecutiveFailures());
        ExperienceEntry variant = library.recallForStrategySelection(domain, "cognitive-evolution", 10)
                .matchedEntries().stream().filter(entry -> entry.lesson().equals(repairLesson))
                .findFirst().orElseThrow();
        assertEquals(template.templateId(), variant.strategyVariant().parentTemplateId());
        assertEquals(StrategyLifecycleStatus.PROBATION, variant.lifecycleStatus());
        assertEquals(1, variant.probationUses());
        assertEquals(1, variant.probationSuccesses());
    }

    @Test
    void repeatedBusinessRejectionStopsAtTheRepairBoundWithoutLearningSuccess() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(711, List.of("kb-rejected-repair-bound"));
        String reflection = "{\"accepted\":false,\"repair\":{\"stepId\":\"s1\",\"objective\":\"recheck eligibility\"},"
                + "\"strategy\":\"Check eligibility before cancellation.\"}";
        ChatModel model = model(
                "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"cancel\",\"dependencies\":[]}]}",
                reflection, reflection, reflection);
        AtomicInteger calls = new AtomicInteger();

        CognitiveExecutionResult result = engine.execute("rejected-repair-bound", "Please cancel my order",
                "base prompt", model, domain, "e".repeat(64), 3, 2,
                (step, systemPrompt, previous) -> {
                    calls.incrementAndGet();
                    return CognitiveSelfEvolutionEngine.StepExecution.success("rejected",
                            List.of(new CognitiveTrace.ToolExecutionEvent(0, "s1", "cancel_pending_order",
                                    CognitiveTrace.ToolExecutionEvent.Status.RESPONSE_RECEIVED,
                                    CognitiveTrace.ToolExecutionEvent.BusinessOutcome.REJECTED,
                                    "ORDER_NOT_ELIGIBLE", "f".repeat(64))));
                }, null, StrategyTaskType.CANCELLATION);

        assertEquals(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, result.status());
        assertEquals(3, calls.get());
        assertEquals(2, result.trace().repairs().stream().filter(CognitiveTrace.RepairEvent::proposalAccepted).count());
        assertEquals(List.of(0, 1, 2), result.trace().toolExecutionEvents().stream()
                .map(CognitiveTrace.ToolExecutionEvent::callIndex).toList());
        assertTrue(library.recallForStrategySelection(domain, "cognitive-evolution", 10).matchedEntries().isEmpty());
    }

    @Test
    void toolCallIndexesRemainUniqueWhenAPlanStepIsRepairedAndRetried() {
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(null);
        ChatModel model = model(
                "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"act\",\"dependencies\":[]}]}",
                "{\"accepted\":true,\"repair\":{\"stepId\":\"s1\",\"objective\":\"retry act\"},"
                        + "\"strategy\":\"Check the action response before continuing.\"}",
                "done",
                "{\"accepted\":true,\"repair\":null,\"strategy\":\"\"}");
        AtomicInteger calls = new AtomicInteger();

        CognitiveExecutionResult result = engine.execute("retry-trace", "perform an action", "prompt",
                model, null, "a".repeat(64), 2, 1,
                (step, systemPrompt, previous) -> calls.getAndIncrement() == 0
                        ? CognitiveSelfEvolutionEngine.StepExecution.failed("RETRYABLE",
                        List.of(successfulTool("example_action")))
                        : CognitiveSelfEvolutionEngine.StepExecution.success("done",
                        List.of(successfulTool("example_action"))));

        assertEquals(CognitiveExecutionResult.Status.COMPLETED, result.status());
        assertEquals(List.of(0, 1), result.trace().toolExecutionEvents().stream()
                .map(CognitiveTrace.ToolExecutionEvent::callIndex).toList());
    }

    @Test
    void failedIndependentOutcomeRejectsCandidateAndRecordsNegativeFeedback() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(72, List.of("kb-outcomes"));
        ExperienceEntry existing = strategy("existing-outcome-strategy", domain, 0, 0, 0.8);
        library.recordExperience(existing);
        ChatModel model = model(
                "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"inspect\",\"dependencies\":[]}]}",
                "{\"done\":false}",
                "{\"accepted\":true,\"repair\":null,\"strategy\":\"Do not adopt this failing candidate.\"}");

        CognitiveExecutionResult result = engine.execute("request-fail-outcome", "run task", "prompt", model,
                domain, "f".repeat(64), 3, 0,
                (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("checked"),
                structuredOutcome("{\"assertions\":{\"done\":{\"type\":\"BOOLEAN\",\"equals\":true}}}"));

        assertEquals(OutcomeSignal.Status.FAIL, result.trace().outcomeSignal().status());
        assertEquals("OUTCOME_VERIFIED_FAIL", result.trace().terminationCode());
        ExperienceEntry updated = library.find(domain, existing.entryId()).orElseThrow();
        assertEquals(1, updated.feedbackAttempts());
        assertEquals(0, updated.feedbackSuccesses());
        assertFalse(library.recallForProposer(domain, "cognitive-evolution", 10).matchedEntries().stream()
                .anyMatch(entry -> entry.lesson().equals("Do not adopt this failing candidate.")));
        assertEquals(1, library.recallForStrategySelection(domain, "cognitive-evolution", 10)
                .matchedEntries().size());
    }

    @Test
    void toolStateOutcomeUsesTheDeclaredStepOutput() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(73, List.of("kb-tool-outcome"));
        ChatModel model = model(
                "{\"steps\":[{\"stepId\":\"tool-step\",\"objective\":\"run tool\",\"dependencies\":[]}]}",
                "tool completed",
                "{\"accepted\":true,\"repair\":null,\"strategy\":\"Check tool completion before reporting success.\"}");
        TaskOutcomeSpec spec = new TaskOutcomeSpec(
                new ExpectedOutcome(OutcomeTaskFamily.TOOL_STATE_ASSERTION, "{\"status\":\"COMPLETED\"}"),
                List.of("fixture:tool-state"), "tool-step");

        CognitiveExecutionResult result = engine.execute("request-tool-outcome", "run tool", "prompt", model,
                domain, "9".repeat(64), 2, 0,
                (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("COMPLETED"),
                spec);

        assertEquals(OutcomeSignal.Status.PASS, result.trace().outcomeSignal().status());
        assertEquals("OUTCOME_VERIFIED_PASS", result.trace().terminationCode());
        ExperienceEntry adopted = library.recallForProposer(domain, "cognitive-evolution", 10)
                .matchedEntries().getFirst();
        assertEquals(StrategyLifecycleStatus.ADOPTED, adopted.lifecycleStatus());
    }

    @Test
    void reflectionCreatesVersionedVariantInProbationAndUcbCanSelectIt() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(74, List.of("kb-template-evolution"));
        String parentTemplateId = "template-parent";
        ExperienceEntry parent = strategy("parent-entry", domain, 0, 0, 0.9).withStrategy(
                StrategyTemplate.create(parentTemplateId, 1, null,
                        List.of("task has a declared outcome"), List.of("inspect", "act"),
                        List.of("use task-provided tools"), List.of("check each step result"),
                        List.of("all declared assertions hold")), null);
        library.recordExperience(parent);
        String mutation = "Check each tool result before reporting task completion.";
        ChatModel model = model(
                "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"inspect\",\"dependencies\":[]}]}",
                "{\"completed\":true}",
                "{\"accepted\":true,\"repair\":null,\"strategy\":\"" + mutation + "\"}");

        CognitiveExecutionResult result = engine.execute("request-template-variant", "inspect the task",
                "base prompt", model, domain, "9".repeat(64), 2, 0,
                (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("done"),
                structuredOutcome("{\"assertions\":{\"completed\":{\"type\":\"BOOLEAN\",\"equals\":true}}}"));

        ExperienceEntry variant = library.recallForStrategySelection(domain, "cognitive-evolution", 10)
                .matchedEntries().stream().filter(entry -> entry.strategyVariant() != null)
                .findFirst().orElseThrow();
        assertEquals(OutcomeSignal.Status.PASS, result.trace().outcomeSignal().status());
        assertEquals(StrategyLifecycleStatus.PROBATION, variant.lifecycleStatus());
        assertEquals(parentTemplateId, variant.strategyVariant().parentTemplateId());
        assertEquals(2, variant.strategyTemplate().version());
        assertEquals("reflectionRules", variant.strategyVariant().changedFields().getFirst());
        assertTrue(result.trace().experienceEvents().stream().anyMatch(event ->
                event.entryId().equals(variant.entryId()) && event.action().equals("VARIANT_PROBATION_CREATED")));
        assertTrue(result.trace().states().contains("STRATEGY_VARIANT_REMAINS_PROBATION"));
        assertEquals(variant.entryId(), new ExperienceStrategySelector()
                .select(library.recallForStrategySelection(domain, "cognitive-evolution", 10).matchedEntries())
                .selected().entryId());
    }

    @Test
    void threeVariantFailuresRetireItAndTraceRollbackToParent() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(75, List.of("kb-variant-rollback"));
        String parentTemplateId = "template-rollback-parent";
        ExperienceEntry parent = strategy("rollback-parent-entry", domain, 10, 0, 0.21).withStrategy(
                StrategyTemplate.create(parentTemplateId, 1, null,
                        List.of("task has a declared outcome"), List.of("inspect", "act"),
                        List.of("use task-provided tools"), List.of("check each step result"),
                        List.of("all declared assertions hold")), null);
        library.recordExperience(parent);
        String variantId = "variant-rollback-child";
        StrategyTemplate childTemplate = StrategyTemplate.create(variantId, 2, parentTemplateId,
                List.of("task has a declared outcome"), List.of("inspect", "act"),
                List.of("use task-provided tools"), List.of("check each tool result"),
                List.of("all declared assertions hold"));
        ExperienceEntry variant = new ExperienceEntry(variantId, domain, "cognitive-evolution",
                "Check each tool result before reporting task completion.", "failure-derived variant",
                new EvidenceReference("evidence-variant", "variant-run", "a".repeat(64), "COGNITIVE"),
                0.5, "variant-run", "b".repeat(64), ExperienceDisposition.OBSERVED, false,
                AttributionStatus.UNRESOLVED, 1, false, null, Instant.now())
                .withStrategy(childTemplate, new StrategyVariant(variantId, parentTemplateId,
                        "failure attribution: missing output verification", List.of("reflectionRules"),
                        "Prevent reporting completion before checking tool output."));
        library.createVariant(variant);

        CognitiveExecutionResult last = null;
        for (int i = 1; i <= 3; i++) {
            last = engine.execute("variant-failure-" + i, "inspect the task", "base prompt",
                    model("{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"inspect\",\"dependencies\":[]}]}",
                            "{\"accepted\":false,\"repair\":null,\"strategy\":\"\"}"),
                    domain, "c".repeat(64), 2, 0,
                    (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.failed("TOOL_FAILED"));
        }

        assertEquals(StrategyLifecycleStatus.RETIRED,
                library.find(domain, variantId).orElseThrow().lifecycleStatus());
        assertTrue(last.trace().states().contains("VARIANT_ROLLED_BACK_TO_PARENT"));
        assertTrue(last.trace().experienceEvents().stream().anyMatch(event ->
                event.entryId().equals("rollback-parent-entry")
                        && event.action().equals("VARIANT_ROLLBACK_TO_PARENT")));
    }

    @Test
    void verifierPassesAccumulateBeforeVariantPromotion() {
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(76, List.of("kb-variant-promotion"));
        ExperienceEntry parent = templateParent("promotion-parent-entry", "template-promotion-parent",
                domain, 10, 0, 0.21);
        library.recordExperience(parent);
        String variantId = "variant-promotion-child";
        library.createVariant(variantCandidate(parent, variantId,
                "Verify tool output before composing a final answer."));

        CognitiveExecutionResult last = null;
        for (int i = 1; i <= 3; i++) {
            last = engine.execute("variant-pass-" + i, "inspect the task", "base prompt",
                    model("{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"inspect\",\"dependencies\":[]}]}",
                            "{\"completed\":true}",
                            "{\"accepted\":true,\"repair\":null,\"strategy\":\"\"}"),
                    domain, "d".repeat(64), 2, 0,
                    (step, systemPrompt, previous) -> CognitiveSelfEvolutionEngine.StepExecution.success("done"),
                    structuredOutcome("{\"assertions\":{\"completed\":{\"type\":\"BOOLEAN\",\"equals\":true}}}"));
            ExperienceEntry current = library.find(domain, variantId).orElseThrow();
            if (i < 3) {
                assertEquals(StrategyLifecycleStatus.PROBATION, current.lifecycleStatus());
            }
        }

        ExperienceEntry promoted = library.find(domain, variantId).orElseThrow();
        assertEquals(OutcomeSignal.Status.PASS, last.trace().outcomeSignal().status());
        assertEquals(3, promoted.probationUses());
        assertEquals(3, promoted.probationSuccesses());
        assertEquals(StrategyLifecycleStatus.ADOPTED, promoted.lifecycleStatus());
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
        assertEquals(StrategyLifecycleStatus.PROBATION, restored.lifecycleStatus());
        assertEquals(0, restored.consecutiveFailures());
        assertTrue(library.recallForStrategySelection(domain, "cognitive-evolution", 10).hasRelevantExperience());
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

    private CognitiveTrace.ToolExecutionEvent successfulTool(String toolName) {
        return new CognitiveTrace.ToolExecutionEvent(0, "s1", toolName,
                CognitiveTrace.ToolExecutionEvent.Status.RESPONSE_RECEIVED, "");
    }

    private CognitiveTrace.ToolExecutionEvent failedTool(String toolName) {
        return new CognitiveTrace.ToolExecutionEvent(0, "s1", toolName,
                CognitiveTrace.ToolExecutionEvent.Status.ERROR, "TOOL_REPORTED_ERROR");
    }

    private ExperienceEntry strategy(String id, ExperienceDomain domain, int attempts, int successes,
                                     double confidence) {
        return new ExperienceEntry(id, domain, "cognitive-evolution",
                "A reusable strategy for validating task progress before choosing actions.", "runtime evidence",
                new EvidenceReference("evidence-" + id, "request-" + id, "e".repeat(64), "COGNITIVE"),
                confidence, "request-" + id, "f".repeat(64), ExperienceDisposition.ADOPTED, false,
                AttributionStatus.UNRESOLVED, 1, false, null, Instant.now(), attempts, successes);
    }

    private ExperienceEntry templateParent(String entryId, String templateId, ExperienceDomain domain,
                                           int attempts, int successes, double confidence) {
        return strategy(entryId, domain, attempts, successes, confidence).withStrategy(
                StrategyTemplate.create(templateId, 1, null,
                        List.of("task has a declared outcome"), List.of("inspect", "act"),
                        List.of("use task-provided tools"), List.of("check each step result"),
                        List.of("all declared assertions hold")), null);
    }

    private ExperienceEntry variantCandidate(ExperienceEntry parent, String variantId, String mutation) {
        StrategyTemplate parentTemplate = parent.strategyTemplate();
        List<String> reflectionRules = new ArrayList<>(parentTemplate.reflectionRules());
        reflectionRules.add(mutation);
        StrategyTemplate template = StrategyTemplate.create(variantId, parentTemplate.version() + 1,
                parentTemplate.templateId(), parentTemplate.preconditions(), parentTemplate.planSkeleton(),
                parentTemplate.toolPolicy(), reflectionRules, parentTemplate.successCriteria());
        return new ExperienceEntry(variantId, parent.domain(), "cognitive-evolution", mutation,
                "Failure-derived strategy variant awaiting execution feedback.",
                new EvidenceReference("evidence-" + variantId, "request-" + variantId, "a".repeat(64), "COGNITIVE"),
                0.5, "run-" + variantId, "b".repeat(64), ExperienceDisposition.OBSERVED, false,
                AttributionStatus.UNRESOLVED, 1, false, null, Instant.now())
                .withStrategy(template, new StrategyVariant(variantId, parentTemplate.templateId(),
                        "failure attribution: " + mutation, List.of("reflectionRules"),
                        "Reduce task errors by applying the learned check: " + mutation));
    }

    private TaskOutcomeSpec structuredOutcome(String expectedJson) {
        return new TaskOutcomeSpec(new ExpectedOutcome(OutcomeTaskFamily.STRUCTURED_OUTPUT_ASSERTION,
                expectedJson), List.of("fixture:expected-outcome"), null);
    }

    private record ExperienceEntryView(int attempts, int successes) {
        private static ExperienceEntryView from(
                tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry entry) {
            return new ExperienceEntryView(entry.feedbackAttempts(), entry.feedbackSuccesses());
        }
    }
}
