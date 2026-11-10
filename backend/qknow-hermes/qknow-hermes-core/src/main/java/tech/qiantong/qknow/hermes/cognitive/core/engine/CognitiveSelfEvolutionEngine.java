package tech.qiantong.qknow.hermes.cognitive.core.engine;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.agent.harness.experience.EvidenceReference;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDisposition;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry.AttributionStatus;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyLifecycleStatus;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyTemplate;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyVariant;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyTaskType;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveExecutionResult;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitivePlan;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveTrace;
import tech.qiantong.qknow.hermes.cognitive.core.model.PlanStep;
import tech.qiantong.qknow.hermes.cognitive.core.model.PlanVerificationResult;
import tech.qiantong.qknow.hermes.cognitive.core.model.ReflectionResult;
import tech.qiantong.qknow.hermes.cognitive.core.model.RepairProposal;
import tech.qiantong.qknow.hermes.cognitive.core.verification.OutcomeSignal;
import tech.qiantong.qknow.hermes.cognitive.core.verification.OutcomeTaskFamily;
import tech.qiantong.qknow.hermes.cognitive.core.verification.OutcomeVerificationRequest;
import tech.qiantong.qknow.hermes.cognitive.core.verification.OutcomeVerifier;
import tech.qiantong.qknow.hermes.cognitive.core.verification.RuleBasedOutcomeVerifier;
import tech.qiantong.qknow.hermes.cognitive.core.verification.TaskOutcomeSpec;
import tech.qiantong.qknow.hermes.cognitive.core.verification.CognitiveToolOutcomeRegistry;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;

@Component
public final class CognitiveSelfEvolutionEngine {
    private static final String EXPERIENCE_CUE = "cognitive-evolution";
    private static final int MAX_RECALLED_STRATEGIES = 32;
    private static final int MAX_STRATEGY_LENGTH = 1000;

    private final ExperienceLibrary experienceLibrary;
    private final CognitiveToolOutcomeRegistry toolOutcomeRegistry;
    private final OutcomeVerifier outcomeVerifier = new RuleBasedOutcomeVerifier();
    private final PlanVerifier planVerifier = new PlanVerifier();
    private final ExperienceStrategySelector strategySelector = new ExperienceStrategySelector();

    public CognitiveSelfEvolutionEngine(ExperienceLibrary experienceLibrary) {
        this(experienceLibrary, CognitiveToolOutcomeRegistry.empty());
    }

    @Autowired
    public CognitiveSelfEvolutionEngine(ExperienceLibrary experienceLibrary,
                                        CognitiveToolOutcomeRegistry toolOutcomeRegistry) {
        this.experienceLibrary = experienceLibrary;
        this.toolOutcomeRegistry = toolOutcomeRegistry == null
                ? CognitiveToolOutcomeRegistry.empty() : toolOutcomeRegistry;
    }

    public CognitiveToolExecutionObserver newToolExecutionObserver(String stepId) {
        return new CognitiveToolExecutionObserver(stepId, toolOutcomeRegistry);
    }

    public CognitiveExecutionResult execute(String requestId, String question, String systemPrompt,
                                            ChatModel chatModel, ExperienceDomain domain,
                                            String harnessFingerprint, int maxTasks,
                                            int maxRepairAttempts, StepExecutor stepExecutor) {
        return execute(requestId, question, systemPrompt, chatModel, domain, harnessFingerprint, maxTasks,
                maxRepairAttempts, stepExecutor, null);
    }

    public CognitiveExecutionResult execute(String requestId, String question, String systemPrompt,
                                            ChatModel chatModel, ExperienceDomain domain,
                                            String harnessFingerprint, int maxTasks,
                                            int maxRepairAttempts, StepExecutor stepExecutor,
                                            TaskOutcomeSpec taskOutcomeSpec) {
        return execute(requestId, question, systemPrompt, chatModel, domain, harnessFingerprint, maxTasks,
                maxRepairAttempts, stepExecutor, taskOutcomeSpec, StrategyTaskType.GENERAL);
    }

    public CognitiveExecutionResult execute(String requestId, String question, String systemPrompt,
                                            ChatModel chatModel, ExperienceDomain domain,
                                            String harnessFingerprint, int maxTasks,
                                            int maxRepairAttempts, StepExecutor stepExecutor,
                                            TaskOutcomeSpec taskOutcomeSpec, StrategyTaskType taskType) {
        StrategyTaskType effectiveTaskType = taskType == null ? StrategyTaskType.GENERAL : taskType;
        var states = new ArrayList<String>();
        var reflections = new ArrayList<CognitiveTrace.ReflectionEvent>();
        var repairs = new ArrayList<CognitiveTrace.RepairEvent>();
        var experienceEvents = new ArrayList<CognitiveTrace.ExperienceEvent>();
        var strategyScores = new ArrayList<CognitiveTrace.StrategyScore>();
        var toolExecutionEvents = new ArrayList<CognitiveTrace.ToolExecutionEvent>();
        var latestToolExecutionEventsByStep = new LinkedHashMap<String, List<CognitiveTrace.ToolExecutionEvent>>();
        boolean selectedStrategyFailureRecorded = false;
        states.add("ANALYZING");
        if (chatModel == null || stepExecutor == null || question == null || question.isBlank()
                || maxTasks < 1 || maxRepairAttempts < 0) {
            return result(CognitiveExecutionResult.Status.FALLBACK_BEFORE_EXECUTION,
                    null, states, strategyScores, repairs, experienceEvents, "INVALID_ENGINE_INPUT");
        }

        ExperienceEntry selectedStrategy = null;
        ExperienceEntry lineageParent = null;
        String selectedLesson = "";
        if (domain != null && experienceLibrary != null) {
            states.add("RECALLING");
            states.add("TASK_TYPE:" + effectiveTaskType);
            try {
                var recall = experienceLibrary.recallForStrategySelection(
                        domain, EXPERIENCE_CUE, MAX_RECALLED_STRATEGIES);
                List<ExperienceEntry> applicable = recall.matchedEntries().stream()
                        .filter(entry -> strategyTaskType(entry).appliesTo(effectiveTaskType)).toList();
                var selection = strategySelector.select(applicable);
                selectedStrategy = selection.selected();
                lineageParent = selectedStrategy;
                strategyScores.addAll(selection.scores());
                if (selectedStrategy == null && effectiveTaskType.isMutationFamily()) {
                    List<ExperienceEntry> fallback = recall.matchedEntries().stream()
                            .filter(entry -> entry.lifecycleStatus() == StrategyLifecycleStatus.ADOPTED)
                            .filter(entry -> entry.strategyTemplate() != null)
                            .filter(entry -> strategyTaskType(entry).canSeedCrossTaskVariant()).toList();
                    var fallbackSelection = strategySelector.select(fallback);
                    strategyScores.addAll(fallbackSelection.scores());
                    if (fallbackSelection.selected() != null) {
                        lineageParent = fallbackSelection.selected();
                        selectedLesson = strategyGuidance(lineageParent);
                        states.add("STRATEGY_CROSS_TASK_SEED_SELECTED");
                    }
                }
                if (selectedStrategy != null) {
                    selectedLesson = strategyGuidance(selectedStrategy);
                }
            } catch (RuntimeException error) {
                states.add("EXPERIENCE_RECALL_FAILED");
            }
        }

        states.add("PLANNING");
        CognitivePlan plan;
        try {
            plan = generatePlan(question, systemPrompt, selectedLesson, chatModel, maxTasks);
        } catch (RuntimeException error) {
            return result(CognitiveExecutionResult.Status.FALLBACK_BEFORE_EXECUTION,
                    null, states, strategyScores, repairs, experienceEvents, "PLAN_GENERATION_FAILED");
        }
        states.add("VERIFYING");
        PlanVerificationResult verification = planVerifier.verify(plan, maxTasks);
        if (!verification.valid()) {
            return result(CognitiveExecutionResult.Status.FALLBACK_BEFORE_EXECUTION,
                    null, states, strategyScores, repairs, experienceEvents, verification.reasonCodes().getFirst());
        }

        var steps = new ArrayList<>(plan.steps());
        var outputs = new LinkedHashMap<String, String>();
        var completed = new LinkedHashSet<String>();
        int repairAttempts = 0;
        int nextToolCallIndex = 0;
        String appliedRepairStrategy = "";
        int index = 0;
        while (true) {
            while (index < steps.size()) {
                PlanStep step = steps.get(index);
                states.add("EXECUTING:" + step.stepId());
                StepExecution execution;
                try {
                    execution = stepExecutor.execute(step, composeSystemPrompt(systemPrompt, selectedLesson,
                            appliedRepairStrategy), Map.copyOf(outputs));
                } catch (Exception error) {
                    execution = StepExecution.failed(error.getClass().getSimpleName());
                }
                List<CognitiveTrace.ToolExecutionEvent> executionToolEvents = execution == null
                        ? List.of() : execution.toolExecutionEvents();
                latestToolExecutionEventsByStep.put(step.stepId(), executionToolEvents);
                if (execution != null) {
                    for (CognitiveTrace.ToolExecutionEvent event : executionToolEvents) {
                        CognitiveTrace.ToolExecutionEvent observed = new CognitiveTrace.ToolExecutionEvent(
                                nextToolCallIndex++, event.stepId(), event.toolName(), event.status(),
                                event.businessOutcome(), event.reasonCode(), event.outcomeEvidenceDigest());
                        toolExecutionEvents.add(observed);
                        states.add("TOOL_EXECUTION:" + observed.stepId() + ':' + observed.callIndex() + ':'
                                + observed.toolName() + ':' + observed.status() + ':' + observed.reasonCode());
                    }
                }
                boolean knownToolFailure = executionToolEvents.stream().anyMatch(event ->
                        event.status() == CognitiveTrace.ToolExecutionEvent.Status.ERROR
                                || event.businessOutcome()
                                == CognitiveTrace.ToolExecutionEvent.BusinessOutcome.REJECTED);
                boolean stepSucceeded = execution != null && execution.succeeded() && !knownToolFailure;
                if (stepSucceeded) {
                    outputs.put(step.stepId(), execution.output() == null ? "" : execution.output());
                    completed.add(step.stepId());
                    index++;
                    continue;
                }

                states.add("REFLECTING");
                StepExecution reflectionExecution = execution;
                if (knownToolFailure && execution != null && execution.succeeded()) {
                    reflectionExecution = StepExecution.failed(
                            toolFailureCode(executionToolEvents), executionToolEvents);
                    states.add("TOOL_OUTCOME_REQUIRES_REPAIR");
                    if (!selectedStrategyFailureRecorded && selectedStrategy != null) {
                        recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                                states, strategyScores, repairs, experienceEvents);
                        selectedStrategyFailureRecorded = true;
                        states.add("STRATEGY_FEEDBACK_RECORDED_STEP_FAILURE");
                    }
                    if (executionToolEvents.stream().anyMatch(event ->
                            event.status() == CognitiveTrace.ToolExecutionEvent.Status.ERROR)) {
                        states.add("MUTATION_FEEDBACK_RECORDED_TOOL_ERROR");
                    }
                }
                ReflectionResult reflection;
                try {
                    reflection = reflect(question, step, outputs, reflectionExecution,
                            systemPrompt, selectedLesson, chatModel);
                } catch (RuntimeException error) {
                    if (!selectedStrategyFailureRecorded) {
                        recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                                states, strategyScores, repairs, experienceEvents);
                        selectedStrategyFailureRecorded = true;
                    }
                    return result(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, null,
                            states, strategyScores, reflections, repairs, experienceEvents,
                            "REFLECTION_FAILED", null, toolExecutionEvents);
                }
                reflections.add(new CognitiveTrace.ReflectionEvent(step.stepId(), reflection.accepted(),
                        "STEP_FAILURE_REFLECTION"));
                RepairProposal proposal = reflection.repairProposal();
                boolean canRepair = repairAttempts < maxRepairAttempts && proposal != null
                        && step.stepId().equals(proposal.stepId());
                PlanStep replacement = canRepair
                        ? new PlanStep(step.stepId(), proposal.objective(), step.dependencies()) : null;
                PlanVerificationResult repairVerification = canRepair
                        ? planVerifier.verifyReplacement(step, replacement, completed)
                        : new PlanVerificationResult(false, List.of("REPAIR_BUDGET_OR_PROPOSAL_MISSING"));
                if (!repairVerification.valid()) {
                    repairs.add(new CognitiveTrace.RepairEvent(step.stepId(), repairAttempts,
                            false, repairVerification.reasonCodes().getFirst()));
                    if (!selectedStrategyFailureRecorded) {
                        recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                                states, strategyScores, repairs, experienceEvents);
                        selectedStrategyFailureRecorded = true;
                    }
                    return result(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, null,
                            states, strategyScores, reflections, repairs, experienceEvents,
                            "REPAIR_REJECTED", null, toolExecutionEvents);
                }
                repairAttempts++;
                repairs.add(new CognitiveTrace.RepairEvent(step.stepId(), repairAttempts, true, "REPAIR_ACCEPTED"));
                steps.set(index, replacement);
                if (!reflection.reusableStrategy().isBlank()) {
                    appliedRepairStrategy = boundedStrategy(reflection.reusableStrategy());
                }
            }

            states.add("COMPOSING");
            String answer;
            try {
                answer = composeAnswer(question, outputs, systemPrompt, selectedLesson, chatModel);
            } catch (RuntimeException error) {
                if (!selectedStrategyFailureRecorded) {
                    recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                            states, strategyScores, repairs, experienceEvents);
                }
                return result(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, null,
                        states, strategyScores, reflections, repairs, experienceEvents,
                        "COMPOSITION_FAILED", null, toolExecutionEvents);
            }
            states.add("REFLECTING");
            ReflectionResult finalReflection;
            try {
                finalReflection = reflectAnswer(question, answer, outputs, systemPrompt,
                        selectedLesson, chatModel);
            } catch (RuntimeException error) {
                if (!selectedStrategyFailureRecorded) {
                    recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                            states, strategyScores, repairs, experienceEvents);
                }
                return result(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, null,
                        states, strategyScores, reflections, repairs, experienceEvents,
                        "FINAL_REFLECTION_FAILED", null, toolExecutionEvents);
            }
            reflections.add(new CognitiveTrace.ReflectionEvent("FINAL", finalReflection.accepted(),
                    finalReflection.accepted() ? "ANSWER_ACCEPTED" : "ANSWER_REPAIR_REQUIRED"));
            if (finalReflection.accepted()) {
                OutcomeSignal outcome = verifyOutcome(requestId, taskOutcomeSpec, outputs, answer);
                states.add("OUTCOME_" + outcome.status() + ":" + outcome.reasonCode());
                List<CognitiveTrace.ToolExecutionEvent> finalToolExecutionEvents = latestToolExecutionEventsByStep
                        .values().stream().flatMap(List::stream).toList();
                List<String> returnedToolNames = toolExecutionEvents.stream()
                        .filter(event -> event.status() == CognitiveTrace.ToolExecutionEvent.Status.RESPONSE_RECEIVED)
                        .map(CognitiveTrace.ToolExecutionEvent::toolName).toList();
                StrategyTaskType observedTaskType = CognitiveTaskClassifier.classifyObservedTools(returnedToolNames);
                StrategyTaskType intentObservedTaskType = CognitiveTaskClassifier.classify(question, returnedToolNames);
                StrategyTaskType feedbackTaskType = intentObservedTaskType.isMutationFamily()
                        ? intentObservedTaskType : effectiveTaskType;
                boolean taskFamilyMismatch = effectiveTaskType.isMutationFamily()
                        ? observedTaskType.isMutationFamily() && observedTaskType != effectiveTaskType
                        : intentObservedTaskType.isMutationFamily() || observedTaskType.isMutationFamily();
                boolean candidateFamilyVerified = !observedTaskType.isMutationFamily()
                        || intentObservedTaskType.isMutationFamily();
                states.add("OBSERVED_TOOL_TASK_TYPE:" + observedTaskType);
                states.add("INTENT_OBSERVED_TASK_TYPE:" + intentObservedTaskType);
                if (taskFamilyMismatch) {
                    states.add("STRATEGY_TASK_FAMILY_MISMATCH:" + effectiveTaskType + "->"
                            + feedbackTaskType + ":OBSERVED_" + observedTaskType);
                }
                if (!candidateFamilyVerified) {
                    states.add("MUTATION_TOOL_WITHOUT_EXPLICIT_INTENT");
                }
                String terminationCode;
                if (outcome.status() == OutcomeSignal.Status.PASS) {
                    if (taskFamilyMismatch) {
                        states.add("STRATEGY_FEEDBACK_SKIPPED_TASK_FAMILY_MISMATCH");
                    } else if (!selectedStrategyFailureRecorded) {
                        recordFeedback(selectedStrategy, true, requestId, harnessFingerprint,
                                states, strategyScores, repairs, experienceEvents, outcome);
                        promoteProbationAfterVerifiedPass(selectedStrategy, states, experienceEvents, outcome);
                    }
                    String strategyToAdopt = !appliedRepairStrategy.isBlank() ? appliedRepairStrategy
                            : finalReflection.reusableStrategy();
                    ExperienceEntry candidateParent = strategyParentForTaskType(lineageParent, feedbackTaskType);
                    if (candidateFamilyVerified && !strategyToAdopt.isBlank() && (selectedStrategy == null
                            || taskFamilyMismatch
                            || !strategyCandidateId(strategyToAdopt, candidateParent, feedbackTaskType)
                            .equals(selectedStrategy.entryId()))) {
                        ExperienceEntry learned = createStrategy(strategyToAdopt, candidateParent,
                                requestId, harnessFingerprint, domain, true, states, strategyScores,
                                repairs, experienceEvents, outcome, feedbackTaskType);
                        if (learned != null && learned.lifecycleStatus() == StrategyLifecycleStatus.PROBATION) {
                            states.add("STRATEGY_VARIANT_REMAINS_PROBATION");
                        }
                    }
                    terminationCode = "OUTCOME_VERIFIED_PASS";
                } else if (outcome.status() == OutcomeSignal.Status.FAIL) {
                    if (taskFamilyMismatch) {
                        states.add("STRATEGY_FEEDBACK_SKIPPED_TASK_FAMILY_MISMATCH");
                    } else if (!selectedStrategyFailureRecorded) {
                        recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                                states, strategyScores, repairs, experienceEvents, outcome);
                    }
                    states.add("STRATEGY_ADOPTION_REJECTED_OUTCOME_FAIL");
                    terminationCode = "OUTCOME_VERIFIED_FAIL";
                } else {
                    boolean hasToolExecution = !finalToolExecutionEvents.isEmpty();
                    boolean noToolFailures = finalToolExecutionEvents.stream().noneMatch(event ->
                            event.status() == CognitiveTrace.ToolExecutionEvent.Status.ERROR);
                    boolean operationalSuccess = noToolFailures
                            && !effectiveTaskType.isMutationFamily()
                            && !intentObservedTaskType.isMutationFamily()
                            && !observedTaskType.isMutationFamily();
                    List<CognitiveTrace.ToolExecutionEvent> mutationEvents = finalToolExecutionEvents.stream()
                            .filter(event -> CognitiveTaskClassifier.classifyObservedTools(List.of(event.toolName()))
                                    .isMutationFamily())
                            .toList();
                    boolean mutationApplied = !mutationEvents.isEmpty() && mutationEvents.stream().allMatch(event ->
                            event.businessOutcome() == CognitiveTrace.ToolExecutionEvent.BusinessOutcome.APPLIED);
                    boolean mutationRejected = mutationEvents.stream().anyMatch(event ->
                            event.businessOutcome() == CognitiveTrace.ToolExecutionEvent.BusinessOutcome.REJECTED);
                    boolean observedMutationReturned = taskFamilyMismatch && candidateFamilyVerified
                            && observedTaskType.isMutationFamily() && mutationApplied;
                    if (taskFamilyMismatch) {
                        states.add("STRATEGY_FEEDBACK_SKIPPED_TASK_FAMILY_MISMATCH");
                    } else if (effectiveTaskType.isMutationFamily()) {
                        if (hasToolExecution && !noToolFailures) {
                            if (!selectedStrategyFailureRecorded) {
                                recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                                        states, strategyScores, repairs, experienceEvents, outcome);
                            }
                            states.add("MUTATION_FEEDBACK_RECORDED_TOOL_ERROR");
                        } else if (mutationRejected) {
                            if (!selectedStrategyFailureRecorded) {
                                recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                                        states, strategyScores, repairs, experienceEvents, outcome);
                            }
                            states.add("MUTATION_FEEDBACK_RECORDED_OUTCOME_REJECTED");
                        } else if (mutationApplied) {
                            if (!selectedStrategyFailureRecorded) {
                                recordFeedback(selectedStrategy, true, requestId, harnessFingerprint,
                                        states, strategyScores, repairs, experienceEvents, outcome);
                            }
                            states.add("MUTATION_FEEDBACK_RECORDED_OUTCOME_APPLIED");
                        } else {
                            states.add("MUTATION_FEEDBACK_DEFERRED_OUTCOME_UNKNOWN");
                        }
                    } else if (operationalSuccess || hasToolExecution) {
                        boolean selectedCanUseOperationalFeedback = hasToolExecution
                                || selectedStrategy != null
                                && selectedStrategy.lifecycleStatus() == StrategyLifecycleStatus.PROBATION;
                        if (selectedCanUseOperationalFeedback && !selectedStrategyFailureRecorded) {
                            recordFeedback(selectedStrategy, operationalSuccess, requestId, harnessFingerprint,
                                    states, strategyScores, repairs, experienceEvents, outcome);
                        }
                        if (operationalSuccess && selectedCanUseOperationalFeedback) {
                            states.add("STRATEGY_FEEDBACK_RECORDED_OPERATIONAL_SUCCESS");
                        }
                    } else {
                        states.add("STRATEGY_FEEDBACK_SKIPPED_NO_TOOL_EXECUTION");
                    }
                    String strategyToTry = !appliedRepairStrategy.isBlank() ? appliedRepairStrategy
                            : finalReflection.reusableStrategy();
                    boolean enteredProbation = false;
                    ExperienceEntry candidateParent = strategyParentForTaskType(lineageParent, feedbackTaskType);
                    boolean candidateCanEnterProbation = noToolFailures && !mutationRejected
                            && (!effectiveTaskType.isMutationFamily()
                            || observedTaskType == effectiveTaskType || observedMutationReturned
                            || mutationEvents.stream().anyMatch(event -> event.businessOutcome()
                            == CognitiveTrace.ToolExecutionEvent.BusinessOutcome.UNKNOWN));
                    if (candidateCanEnterProbation && candidateFamilyVerified
                            && !strategyToTry.isBlank()
                            && (selectedStrategy == null || taskFamilyMismatch
                            || !strategyCandidateId(strategyToTry, candidateParent, feedbackTaskType)
                            .equals(selectedStrategy.entryId()))) {
                        ExperienceEntry probation = createStrategy(strategyToTry, candidateParent,
                                requestId, harnessFingerprint, domain, false, states, strategyScores,
                                repairs, experienceEvents, outcome, feedbackTaskType);
                        enteredProbation = probation != null
                                && probation.lifecycleStatus() == StrategyLifecycleStatus.PROBATION;
                        if (enteredProbation && (operationalSuccess || mutationApplied)) {
                            recordFeedback(probation, true, requestId, harnessFingerprint, states,
                                    strategyScores, repairs, experienceEvents, outcome);
                        }
                    }
                    if (returnedToolNames.isEmpty() && !taskFamilyMismatch && effectiveTaskType.isMutationFamily()) {
                        states.add("MUTATION_FEEDBACK_REJECTED_NO_MATCHING_TOOL_EXECUTION");
                    }
                    states.add(enteredProbation ? "STRATEGY_ENTERED_PROBATION_OUTCOME_UNKNOWN"
                            : "STRATEGY_NOT_ENTERED_PROBATION_OUTCOME_UNKNOWN");
                    terminationCode = "OUTCOME_VERIFIED_UNKNOWN";
                }
                states.add("COMPLETED");
                return result(CognitiveExecutionResult.Status.COMPLETED, answer,
                        states, strategyScores, reflections, repairs, experienceEvents,
                        terminationCode, outcome, toolExecutionEvents);
            }

            RepairProposal proposal = finalReflection.repairProposal();
            if (repairAttempts >= maxRepairAttempts || proposal == null) {
                recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                        states, strategyScores, repairs, experienceEvents);
                return result(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, null,
                        states, strategyScores, reflections, repairs, experienceEvents,
                        "FINAL_REFLECTION_REJECTED", null, toolExecutionEvents);
            }
            PlanStep appended = new PlanStep(proposal.stepId(), proposal.objective(), List.copyOf(completed));
            PlanVerificationResult appendVerification = planVerifier.verifyAppend(
                    new CognitivePlan(steps), appended, maxTasks);
            if (!appendVerification.valid()) {
                repairs.add(new CognitiveTrace.RepairEvent(proposal.stepId(), repairAttempts,
                        false, appendVerification.reasonCodes().getFirst()));
                recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                        states, strategyScores, repairs, experienceEvents);
                return result(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, null,
                        states, strategyScores, reflections, repairs, experienceEvents,
                        "FINAL_REPAIR_REJECTED", null, toolExecutionEvents);
            }
            repairAttempts++;
            repairs.add(new CognitiveTrace.RepairEvent(proposal.stepId(), repairAttempts,
                    true, "FINAL_REPAIR_ACCEPTED"));
            steps.add(appended);
            if (!finalReflection.reusableStrategy().isBlank()) {
                appliedRepairStrategy = boundedStrategy(finalReflection.reusableStrategy());
            }
        }
    }

    public ExperienceEntry restoreStrategy(ExperienceDomain domain, String entryId, int version) {
        if (experienceLibrary == null) {
            throw new IllegalStateException("experience library is unavailable");
        }
        return experienceLibrary.restoreToProbation(domain, entryId, version);
    }

    private OutcomeSignal verifyOutcome(String requestId, TaskOutcomeSpec spec,
                                        Map<String, String> outputs, String answer) {
        String familyId = spec == null ? OutcomeTaskFamily.STRUCTURED_OUTPUT_ASSERTION
                : spec.expectedOutcome().familyId();
        String expected = spec == null ? null : spec.expectedOutcome().value();
        List<String> evidenceRefs = spec == null ? List.of() : spec.evidenceRefs();
        Map<String, String> toolOutputs = Map.of();
        if (spec != null && spec.toolStatusStepId() != null
                && outputs.containsKey(spec.toolStatusStepId())) {
            toolOutputs = Map.of("status", outputs.get(spec.toolStatusStepId()));
        }
        return outcomeVerifier.verify(new OutcomeVerificationRequest(requestId, familyId,
                expected, summarizeOutputs(outputs), toolOutputs, answer, evidenceRefs));
    }

    private CognitivePlan generatePlan(String question, String systemPrompt, String lesson,
                                      ChatModel chatModel, int maxTasks) {
        String prompt = """
                将用户任务拆解为最多 %d 个严格顺序执行的步骤。只返回 JSON 对象：
                {"steps":[{"stepId":"s1","objective":"...","dependencies":[]}]}
                stepId 唯一；objective 必须具体；dependencies 只能引用更早步骤。
                可参考已采纳经验，但不得复述或臆造用户未提供的事实。
                用户任务：%s
                """.formatted(maxTasks, question);
        String output = call(chatModel, systemPromptWithLesson(systemPrompt, lesson), prompt);
        JSONObject object = JSON.parseObject(jsonPayload(output));
        JSONArray array = object == null ? null : object.getJSONArray("steps");
        if (array == null) {
            throw new IllegalArgumentException("plan steps missing");
        }
        var steps = new ArrayList<PlanStep>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (item == null) {
                throw new IllegalArgumentException("invalid plan step");
            }
            JSONArray dependencyArray = item.getJSONArray("dependencies");
            var dependencies = new ArrayList<String>();
            if (dependencyArray != null) {
                for (int j = 0; j < dependencyArray.size(); j++) {
                    dependencies.add(dependencyArray.getString(j));
                }
            }
            steps.add(new PlanStep(item.getString("stepId"), item.getString("objective"), dependencies));
        }
        return new CognitivePlan(steps);
    }

    private ReflectionResult reflect(String question, PlanStep failedStep, Map<String, String> outputs,
                                     StepExecution failedExecution,
                                     String systemPrompt, String lesson, ChatModel chatModel) {
        String prompt = """
                反思本次步骤失败，判断能否通过替代该步骤修复。只返回 JSON：
                {"accepted":false,"repair":{"stepId":"%s","objective":"..."},"strategy":"可复用策略"}
                repair.stepId 必须保持为 %s；没有可验证修复时 repair 设为 null。
                strategy 写成简洁、可复用的行为规则；不要包含原始问题中的隐私或答案。
                原任务：%s
                已完成步骤摘要：%s
                失败步骤：%s / %s
                失败码：%s
                工具结果摘要：%s
                """.formatted(failedStep.stepId(), failedStep.stepId(), question,
                summarizeOutputs(outputs), failedStep.stepId(), failedStep.objective(),
                failedExecution == null ? "UNKNOWN" : failedExecution.failureCode(),
                failedExecution == null ? "NONE" : summarizeToolExecutionEvents(
                        failedExecution.toolExecutionEvents()));
        return parseReflection(call(chatModel, systemPromptWithLesson(systemPrompt, lesson), prompt));
    }

    private ReflectionResult reflectAnswer(String question, String answer, Map<String, String> outputs,
                                           String systemPrompt, String lesson, ChatModel chatModel) {
        String prompt = """
                根据原任务和执行结果评估当前答案是否完成任务。你不是外部真值裁判，必须保守判断。
                只返回 JSON：
                {"accepted":true,"repair":null,"strategy":"可复用策略"}
                若未完成且可用一个补充步骤修复，返回 {"accepted":false,"repair":{"stepId":"repair1","objective":"..."},"strategy":"..."}。
                strategy 只写可复用行为规则，不得写对答案正确性的绝对保证。
                原任务：%s
                步骤结果摘要：%s
                当前答案：%s
                """.formatted(question, summarizeOutputs(outputs), answer);
        return parseReflection(call(chatModel, systemPromptWithLesson(systemPrompt, lesson), prompt));
    }

    private ReflectionResult parseReflection(String output) {
        JSONObject object = JSON.parseObject(jsonPayload(output));
        if (object == null || !object.containsKey("accepted")) {
            throw new IllegalArgumentException("reflection accepted field missing");
        }
        boolean accepted = object.getBooleanValue("accepted");
        JSONObject repairObject = object.getJSONObject("repair");
        RepairProposal proposal = repairObject == null ? null
                : new RepairProposal(repairObject.getString("stepId"), repairObject.getString("objective"));
        String strategy = object.getString("strategy");
        if (strategy != null && strategy.length() > MAX_STRATEGY_LENGTH) {
            throw new IllegalArgumentException("reflection strategy exceeds limit");
        }
        return new ReflectionResult(accepted, proposal, strategy);
    }

    private String composeAnswer(String question, Map<String, String> outputs, String systemPrompt,
                                String lesson, ChatModel chatModel) {
        String prompt = """
                根据已完成的步骤结果回答用户任务。若材料不足，明确说明，不要补造事实。
                用户任务：%s
                步骤结果：%s
                """.formatted(question, summarizeOutputs(outputs));
        return call(chatModel, systemPromptWithLesson(systemPrompt, lesson), prompt);
    }

    private String call(ChatModel chatModel, String systemPrompt, String userPrompt) {
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt == null ? "" : systemPrompt),
                new UserMessage(userPrompt)
        )));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null) {
            throw new IllegalStateException("model returned no text");
        }
        return response.getResult().getOutput().getText();
    }

    private String systemPromptWithLesson(String systemPrompt, String lesson) {
        return lesson == null || lesson.isBlank() ? systemPrompt
                : (systemPrompt == null ? "" : systemPrompt)
                + "\n\n仅供本任务参考的已采纳策略：\n- " + lesson;
    }

    private String composeSystemPrompt(String systemPrompt, String selectedLesson, String repairStrategy) {
        String prompt = systemPromptWithLesson(systemPrompt, selectedLesson);
        return repairStrategy == null || repairStrategy.isBlank() ? prompt
                : prompt + "\n\n本次修复新增策略：\n- " + repairStrategy;
    }

    private String summarizeOutputs(Map<String, String> outputs) {
        var lines = new ArrayList<String>();
        outputs.forEach((id, value) -> lines.add(id + ": " + value));
        return String.join("\n", lines);
    }

    private String jsonPayload(String output) {
        if (output == null) {
            throw new IllegalArgumentException("empty model output");
        }
        String text = output.trim().replaceFirst("(?s)^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "").trim();
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        int start = objectStart < 0 ? arrayStart : arrayStart < 0 ? objectStart : Math.min(objectStart, arrayStart);
        int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("model output is not JSON");
        }
        return text.substring(start, end + 1);
    }

    private void recordFeedback(ExperienceEntry selected, boolean success, String requestId,
                                String harnessFingerprint, List<String> states,
                                List<CognitiveTrace.StrategyScore> scores,
                                List<CognitiveTrace.RepairEvent> repairs,
                                List<CognitiveTrace.ExperienceEvent> events) {
        recordFeedback(selected, success, requestId, harnessFingerprint, states, scores, repairs, events, null);
    }

    private void recordFeedback(ExperienceEntry selected, boolean success, String requestId,
                                String harnessFingerprint, List<String> states,
                                List<CognitiveTrace.StrategyScore> scores,
                                List<CognitiveTrace.RepairEvent> repairs,
                                List<CognitiveTrace.ExperienceEvent> events,
                                OutcomeSignal outcomeSignal) {
        if (selected == null || experienceLibrary == null || requestId == null || requestId.isBlank()
                || harnessFingerprint == null || !harnessFingerprint.matches("[0-9a-f]{64}")) {
            return;
        }
        EvidenceReference evidence = evidenceReference(requestId, harnessFingerprint, states, scores, repairs,
                outcomeSignal);
        try {
            ExperienceEntry updated = selected.lifecycleStatus() == StrategyLifecycleStatus.PROBATION
                    ? experienceLibrary.recordProbationFeedback(selected.domain(), selected.entryId(),
                    success, evidence, requestId, harnessFingerprint)
                    : experienceLibrary.recordFeedback(selected.domain(), selected.entryId(), success,
                    evidence, requestId, harnessFingerprint);
            String action = updated.lifecycleStatus() == StrategyLifecycleStatus.RETIRED
                    ? selected.lifecycleStatus() == StrategyLifecycleStatus.PROBATION
                    ? "PROBATION_RETIRED" : "FEEDBACK_AND_RETIRE"
                    : updated.lifecycleStatus() == StrategyLifecycleStatus.ADOPTED
                    && selected.lifecycleStatus() == StrategyLifecycleStatus.PROBATION
                    ? "PROBATION_PROMOTED" : selected.lifecycleStatus() == StrategyLifecycleStatus.PROBATION
                    ? "PROBATION_FEEDBACK" : "FEEDBACK";
            events.add(experienceEvent(updated, action, outcomeSignal));
            if (action.equals("PROBATION_FEEDBACK")) {
                states.add("PROBATION_FEEDBACK_RECORDED");
            } else if (action.equals("PROBATION_PROMOTED")) {
                states.add("STRATEGY_PROMOTED_AFTER_PROBATION");
            }
            if (updated.retired()) {
                states.add("STRATEGY_RETIRED");
                if (selected.strategyVariant() != null) {
                    try {
                        var parent = experienceLibrary.rollbackToParent(selected.domain(), selected.entryId());
                        if (parent.isPresent()) {
                            events.add(experienceEvent(parent.get(), "VARIANT_ROLLBACK_TO_PARENT", outcomeSignal));
                            states.add("VARIANT_ROLLED_BACK_TO_PARENT");
                        } else {
                            states.add("VARIANT_PARENT_UNAVAILABLE");
                        }
                    } catch (RuntimeException error) {
                        states.add("VARIANT_ROLLBACK_FAILED");
                    }
                }
            }
        } catch (RuntimeException error) {
            states.add("EXPERIENCE_FEEDBACK_FAILED");
        }
    }

    private void promoteProbationAfterVerifiedPass(ExperienceEntry selected, List<String> states,
                                                   List<CognitiveTrace.ExperienceEvent> events,
                                                   OutcomeSignal outcomeSignal) {
        if (selected == null || selected.lifecycleStatus() != StrategyLifecycleStatus.PROBATION
                || selected.strategyVariant() != null || experienceLibrary == null) {
            return;
        }
        try {
            ExperienceEntry latest = experienceLibrary.find(selected.domain(), selected.entryId()).orElse(null);
            if (latest == null || latest.lifecycleStatus() != StrategyLifecycleStatus.PROBATION) {
                return;
            }
            ExperienceEntry promoted = experienceLibrary.promote(selected.domain(), selected.entryId());
            events.add(experienceEvent(promoted, "VERIFIER_PASS_PROMOTION", outcomeSignal));
            states.add("STRATEGY_PROMOTED_BY_OUTCOME_VERIFIER");
        } catch (RuntimeException error) {
            states.add("EXPERIENCE_PROMOTION_FAILED");
        }
    }

    private ExperienceEntry createStrategy(String strategy, ExperienceEntry selected,
                                           String requestId, String harnessFingerprint,
                                           ExperienceDomain domain, boolean verifierPass,
                                           List<String> states,
                                           List<CognitiveTrace.StrategyScore> scores,
                                           List<CognitiveTrace.RepairEvent> repairs,
                                           List<CognitiveTrace.ExperienceEvent> events,
                                           OutcomeSignal outcomeSignal, StrategyTaskType taskType) {
        if (strategy == null || strategy.isBlank() || experienceLibrary == null || domain == null
                || requestId == null || requestId.isBlank()
                || harnessFingerprint == null || !harnessFingerprint.matches("[0-9a-f]{64}")) {
            return null;
        }
        String lesson = boundedStrategy(strategy);
        ExperienceEntry parent = selected != null && selected.strategyTemplate() != null ? selected : null;
        boolean variant = parent != null;
        String entryId = variant ? variantId(parent.strategyTemplate().templateId(), lesson, taskType)
                : templateIdForStrategy(lesson, taskType);
        try {
            EvidenceReference evidence = evidenceReference(requestId, harnessFingerprint, states, scores, repairs,
                    outcomeSignal);
            ExperienceEntry existing = experienceLibrary.find(domain, entryId).orElse(null);
            if (existing != null) {
                return existing;
            }
            StrategyTemplate template = strategyTemplate(entryId, parent, lesson, taskType);
            List<String> changedFields = parent != null && parent.strategyTemplate() != null
                    && parent.strategyTemplate().taskType() != taskType
                    ? List.of("reflectionRules", "taskType") : List.of("reflectionRules");
            StrategyVariant strategyVariant = variant
                    ? new StrategyVariant(entryId, parent.strategyTemplate().templateId(),
                    "reflection_or_failure_attribution:" + lesson,
                    changedFields, "Improve behavior for the observed task without changing tool policy")
                    : null;
            ExperienceDisposition initialDisposition = verifierPass && !variant
                    ? ExperienceDisposition.ADOPTED : ExperienceDisposition.OBSERVED;
            ExperienceEntry candidate = new ExperienceEntry(entryId, domain, EXPERIENCE_CUE, lesson,
                    verifierPass
                            ? "OutcomeVerifier PASS; structured strategy template with verifiable lineage."
                            : "OutcomeVerifier UNKNOWN; template promotion requires empirical probation feedback.",
                    evidence, 0.5, requestId, harnessFingerprint, initialDisposition, false,
                    AttributionStatus.UNRESOLVED, 1, false, null, Instant.now())
                    .withStrategy(template, strategyVariant);
            if (!verifierPass) {
                events.add(experienceEvent(candidate, "CANDIDATE_CREATED", outcomeSignal));
            }
            ExperienceEntry saved;
            if (variant) {
                saved = experienceLibrary.createVariant(candidate);
            } else if (verifierPass) {
                saved = experienceLibrary.adoptRuntimeStrategy(candidate);
            } else {
                saved = experienceLibrary.createProbation(candidate);
            }
            String action = variant ? "VARIANT_PROBATION_CREATED"
                    : saved.lifecycleStatus() == StrategyLifecycleStatus.ADOPTED
                    ? "AUTO_ADOPTED" : "TEMPLATE_PROBATION_CREATED";
            events.add(experienceEvent(saved, action, outcomeSignal));
            states.add(saved.lifecycleStatus() == StrategyLifecycleStatus.PROBATION
                    ? variant ? "STRATEGY_VARIANT_PROBATION" : "STRATEGY_TEMPLATE_PROBATION"
                    : "STRATEGY_TEMPLATE_" + saved.lifecycleStatus());
            return saved;
        } catch (IllegalStateException error) {
            states.add(error.getMessage() != null && error.getMessage().contains("variant limit")
                    ? "STRATEGY_VARIANT_LIMIT_REACHED" : "STRATEGY_LINEAGE_REJECTED");
            return null;
        } catch (RuntimeException error) {
            states.add("STRATEGY_TEMPLATE_CREATION_FAILED");
            return null;
        }
    }

    private StrategyTemplate strategyTemplate(String templateId, ExperienceEntry parent, String lesson,
                                              StrategyTaskType taskType) {
        StrategyTemplate.Content content = parent == null || parent.strategyTemplate() == null
                ? StrategyTemplate.Content.fromLesson(lesson) : parent.strategyTemplate().content();
        String parentId = parent == null || parent.strategyTemplate() == null
                ? null : parent.strategyTemplate().templateId();
        int version = parent == null || parent.strategyTemplate() == null
                ? 1 : parent.strategyTemplate().version() + 1;
        if (parent != null && parent.strategyTemplate() != null) {
            List<String> reflectionRules = new ArrayList<>(content.reflectionRules());
            reflectionRules.add(lesson);
            while (reflectionRules.size() > 12) {
                reflectionRules.removeFirst();
            }
            content = new StrategyTemplate.Content(content.preconditions(), content.planSkeleton(),
                    content.toolPolicy(), reflectionRules.stream().distinct().toList(),
                    content.successCriteria());
        }
        return StrategyTemplate.create(templateId, version, parentId, content.preconditions(),
                content.planSkeleton(), content.toolPolicy(), content.reflectionRules(), content.successCriteria(),
                taskType);
    }

    private StrategyTaskType strategyTaskType(ExperienceEntry entry) {
        return entry.strategyTemplate() == null ? StrategyTaskType.UNCLASSIFIED
                : entry.strategyTemplate().taskType();
    }

    private ExperienceEntry strategyParentForTaskType(ExperienceEntry candidate, StrategyTaskType taskType) {
        if (candidate == null || candidate.strategyTemplate() == null) {
            return null;
        }
        StrategyTaskType parentType = strategyTaskType(candidate);
        return parentType == taskType || parentType.canSeedCrossTaskVariant() ? candidate : null;
    }

    private boolean hasParentTemplate(ExperienceEntry selected) {
        return selected != null && selected.strategyTemplate() != null;
    }

    private String strategyGuidance(ExperienceEntry entry) {
        return entry.strategyTemplate() == null ? entry.lesson() : entry.strategyTemplate().renderGuidance();
    }

    private CognitiveTrace.ExperienceEvent experienceEvent(ExperienceEntry entry, String action,
                                                           OutcomeSignal outcomeSignal) {
        return new CognitiveTrace.ExperienceEvent(entry.entryId(), entry.version(), action,
                entry.feedbackAttempts(), entry.feedbackSuccesses(), entry.retired(),
                entry.lifecycleStatus(), entry.probationUses(), entry.probationSuccesses(),
                entry.consecutiveFailures(), outcomeSignal == null
                ? OutcomeSignal.Status.UNKNOWN : outcomeSignal.status());
    }

    private String entryIdForStrategy(String strategy) {
        return "cog-" + sha256(boundedStrategy(strategy)).substring(0, 24);
    }

    private String strategyCandidateId(String strategy, ExperienceEntry parent, StrategyTaskType taskType) {
        return parent != null && parent.strategyTemplate() != null
                ? variantId(parent.strategyTemplate().templateId(), strategy, taskType)
                : templateIdForStrategy(strategy, taskType);
    }

    private String templateIdForStrategy(String strategy, StrategyTaskType taskType) {
        return "tpl-" + sha256(strategyIdentity(strategy, taskType)).substring(0, 24);
    }

    private String variantId(String parentTemplateId, String strategy, StrategyTaskType taskType) {
        return "var-" + sha256(parentTemplateId + "|" + strategyIdentity(strategy, taskType)).substring(0, 24);
    }

    private String strategyIdentity(String strategy, StrategyTaskType taskType) {
        String bounded = boundedStrategy(strategy);
        return taskType == StrategyTaskType.GENERAL || taskType == StrategyTaskType.UNCLASSIFIED
                ? bounded : taskType.name() + "|" + bounded;
    }

    private EvidenceReference evidenceReference(String requestId, String fingerprint,
                                                List<String> states,
                                                List<CognitiveTrace.StrategyScore> scores,
                                                List<CognitiveTrace.RepairEvent> repairs) {
        return evidenceReference(requestId, fingerprint, states, scores, repairs, null);
    }

    private EvidenceReference evidenceReference(String requestId, String fingerprint,
                                                List<String> states,
                                                List<CognitiveTrace.StrategyScore> scores,
                                                List<CognitiveTrace.RepairEvent> repairs,
                                                OutcomeSignal outcomeSignal) {
        String outcomeEvidence = outcomeSignal == null ? "" : outcomeSignal.status() + "|"
                + outcomeSignal.reasonCode() + "|" + outcomeSignal.expectedDigest() + "|"
                + outcomeSignal.observedDigest() + "|" + outcomeSignal.evidenceDigest();
        String evidenceSha = sha256(String.join("|", states) + scores + repairs + outcomeEvidence);
        return new EvidenceReference("cognitive-" + sha256(requestId).substring(0, 24),
                requestId, evidenceSha, "COGNITIVE_EVOLUTION_RUNTIME");
    }

    private String boundedStrategy(String strategy) {
        String normalized = strategy == null ? "" : strategy.trim();
        if (normalized.length() > MAX_STRATEGY_LENGTH) {
            throw new IllegalArgumentException("strategy exceeds limit");
        }
        return normalized;
    }

    private String toolFailureCode(List<CognitiveTrace.ToolExecutionEvent> events) {
        return events.stream()
                .filter(event -> event.status() == CognitiveTrace.ToolExecutionEvent.Status.ERROR
                        || event.businessOutcome() == CognitiveTrace.ToolExecutionEvent.BusinessOutcome.REJECTED)
                .map(event -> event.status() == CognitiveTrace.ToolExecutionEvent.Status.ERROR
                        ? "TOOL_EXECUTION_ERROR:" + event.reasonCode()
                        : "TOOL_OUTCOME_REJECTED:" + event.reasonCode())
                .findFirst().orElse("TOOL_EXECUTION_FAILED");
    }

    private String summarizeToolExecutionEvents(List<CognitiveTrace.ToolExecutionEvent> events) {
        if (events == null || events.isEmpty()) {
            return "NONE";
        }
        return events.stream()
                .map(event -> event.toolName() + "|" + event.status() + "|"
                        + event.businessOutcome() + "|" + event.reasonCode() + "|"
                        + event.outcomeEvidenceDigest())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private CognitiveExecutionResult result(CognitiveExecutionResult.Status status, String answer,
                                            List<String> states,
                                            List<CognitiveTrace.StrategyScore> scores,
                                            List<CognitiveTrace.RepairEvent> repairs,
                                            List<CognitiveTrace.ExperienceEvent> events,
                                            String reason) {
        return result(status, answer, states, scores, List.of(), repairs, events, reason);
    }

    private CognitiveExecutionResult result(CognitiveExecutionResult.Status status, String answer,
                                            List<String> states,
                                            List<CognitiveTrace.StrategyScore> scores,
                                            List<CognitiveTrace.ReflectionEvent> reflections,
                                            List<CognitiveTrace.RepairEvent> repairs,
                                            List<CognitiveTrace.ExperienceEvent> events,
                                            String reason) {
        return result(status, answer, states, scores, reflections, repairs, events, reason, null);
    }

    private CognitiveExecutionResult result(CognitiveExecutionResult.Status status, String answer,
                                            List<String> states,
                                            List<CognitiveTrace.StrategyScore> scores,
                                            List<CognitiveTrace.ReflectionEvent> reflections,
                                            List<CognitiveTrace.RepairEvent> repairs,
                                            List<CognitiveTrace.ExperienceEvent> events,
                                            String reason,
                                            OutcomeSignal outcomeSignal) {
        return result(status, answer, states, scores, reflections, repairs, events,
                reason, outcomeSignal, List.of());
    }

    private CognitiveExecutionResult result(CognitiveExecutionResult.Status status, String answer,
                                            List<String> states,
                                            List<CognitiveTrace.StrategyScore> scores,
                                            List<CognitiveTrace.ReflectionEvent> reflections,
                                            List<CognitiveTrace.RepairEvent> repairs,
                                            List<CognitiveTrace.ExperienceEvent> events,
                                            String reason,
                                            OutcomeSignal outcomeSignal,
                                            List<CognitiveTrace.ToolExecutionEvent> toolExecutionEvents) {
        return new CognitiveExecutionResult(status, answer,
                new CognitiveTrace(states, scores, reflections, repairs, events, reason,
                        outcomeSignal, toolExecutionEvents));
    }

    @FunctionalInterface
    public interface StepExecutor {
        StepExecution execute(PlanStep step, String systemPrompt, Map<String, String> previousResults) throws Exception;
    }

    public record StepExecution(boolean succeeded, String output, String failureCode,
                                List<CognitiveTrace.ToolExecutionEvent> toolExecutionEvents) {
        public StepExecution(boolean succeeded, String output, String failureCode) {
            this(succeeded, output, failureCode, List.of());
        }

        public StepExecution {
            toolExecutionEvents = toolExecutionEvents == null ? List.of() : List.copyOf(toolExecutionEvents);
        }

        public static StepExecution success(String output) {
            return new StepExecution(true, output, null);
        }

        public static StepExecution success(String output,
                                            List<CognitiveTrace.ToolExecutionEvent> toolExecutionEvents) {
            return new StepExecution(true, output, null, toolExecutionEvents);
        }

        public static StepExecution failed(String failureCode) {
            return new StepExecution(false, null, failureCode == null ? "STEP_FAILED" : failureCode);
        }

        public static StepExecution failed(String failureCode,
                                           List<CognitiveTrace.ToolExecutionEvent> toolExecutionEvents) {
            return new StepExecution(false, null,
                    failureCode == null ? "STEP_FAILED" : failureCode, toolExecutionEvents);
        }
    }
}
