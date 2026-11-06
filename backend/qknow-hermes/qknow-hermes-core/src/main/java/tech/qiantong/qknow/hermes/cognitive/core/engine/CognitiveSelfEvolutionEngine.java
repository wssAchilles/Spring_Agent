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
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveExecutionResult;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitivePlan;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveTrace;
import tech.qiantong.qknow.hermes.cognitive.core.model.PlanStep;
import tech.qiantong.qknow.hermes.cognitive.core.model.PlanVerificationResult;
import tech.qiantong.qknow.hermes.cognitive.core.model.ReflectionResult;
import tech.qiantong.qknow.hermes.cognitive.core.model.RepairProposal;

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
    private final PlanVerifier planVerifier = new PlanVerifier();
    private final ExperienceStrategySelector strategySelector = new ExperienceStrategySelector();

    public CognitiveSelfEvolutionEngine(ExperienceLibrary experienceLibrary) {
        this.experienceLibrary = experienceLibrary;
    }

    public CognitiveExecutionResult execute(String requestId, String question, String systemPrompt,
                                            ChatModel chatModel, ExperienceDomain domain,
                                            String harnessFingerprint, int maxTasks,
                                            int maxRepairAttempts, StepExecutor stepExecutor) {
        var states = new ArrayList<String>();
        var reflections = new ArrayList<CognitiveTrace.ReflectionEvent>();
        var repairs = new ArrayList<CognitiveTrace.RepairEvent>();
        var experienceEvents = new ArrayList<CognitiveTrace.ExperienceEvent>();
        var strategyScores = new ArrayList<CognitiveTrace.StrategyScore>();
        states.add("ANALYZING");
        if (chatModel == null || stepExecutor == null || question == null || question.isBlank()
                || maxTasks < 1 || maxRepairAttempts < 0) {
            return result(CognitiveExecutionResult.Status.FALLBACK_BEFORE_EXECUTION,
                    null, states, strategyScores, repairs, experienceEvents, "INVALID_ENGINE_INPUT");
        }

        ExperienceEntry selectedStrategy = null;
        String selectedLesson = "";
        if (domain != null && experienceLibrary != null) {
            states.add("RECALLING");
            try {
                var recall = experienceLibrary.recallForProposer(domain, EXPERIENCE_CUE, MAX_RECALLED_STRATEGIES);
                var selection = strategySelector.select(recall.matchedEntries());
                selectedStrategy = selection.selected();
                strategyScores.addAll(selection.scores());
                if (selectedStrategy != null) {
                    selectedLesson = selectedStrategy.lesson();
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
                if (execution != null && execution.succeeded()) {
                    outputs.put(step.stepId(), execution.output() == null ? "" : execution.output());
                    completed.add(step.stepId());
                    index++;
                    continue;
                }

                states.add("REFLECTING");
                ReflectionResult reflection;
                try {
                    reflection = reflect(question, step, outputs, execution,
                            systemPrompt, selectedLesson, chatModel);
                } catch (RuntimeException error) {
                    recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                            states, strategyScores, repairs, experienceEvents);
                    return result(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, null,
                            states, strategyScores, reflections, repairs, experienceEvents, "REFLECTION_FAILED");
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
                    recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                            states, strategyScores, repairs, experienceEvents);
                    return result(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, null,
                            states, strategyScores, reflections, repairs, experienceEvents, "REPAIR_REJECTED");
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
                recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                        states, strategyScores, repairs, experienceEvents);
                return result(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, null,
                        states, strategyScores, reflections, repairs, experienceEvents, "COMPOSITION_FAILED");
            }
            states.add("REFLECTING");
            ReflectionResult finalReflection;
            try {
                finalReflection = reflectAnswer(question, answer, outputs, systemPrompt,
                        selectedLesson, chatModel);
            } catch (RuntimeException error) {
                recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                        states, strategyScores, repairs, experienceEvents);
                return result(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, null,
                        states, strategyScores, reflections, repairs, experienceEvents, "FINAL_REFLECTION_FAILED");
            }
            reflections.add(new CognitiveTrace.ReflectionEvent("FINAL", finalReflection.accepted(),
                    finalReflection.accepted() ? "ANSWER_ACCEPTED" : "ANSWER_REPAIR_REQUIRED"));
            if (finalReflection.accepted()) {
                recordFeedback(selectedStrategy, true, requestId, harnessFingerprint,
                        states, strategyScores, repairs, experienceEvents);
                String strategyToAdopt = !appliedRepairStrategy.isBlank() ? appliedRepairStrategy
                        : finalReflection.reusableStrategy();
                if (!strategyToAdopt.isBlank() && (selectedStrategy == null
                        || !entryIdForStrategy(strategyToAdopt).equals(selectedStrategy.entryId()))) {
                    adoptStrategy(strategyToAdopt, requestId, harnessFingerprint, domain,
                            states, strategyScores, repairs, experienceEvents);
                }
                states.add("COMPLETED");
                return result(CognitiveExecutionResult.Status.COMPLETED, answer,
                        states, strategyScores, reflections, repairs, experienceEvents, "REFLECTION_ACCEPTED");
            }

            RepairProposal proposal = finalReflection.repairProposal();
            if (repairAttempts >= maxRepairAttempts || proposal == null) {
                recordFeedback(selectedStrategy, false, requestId, harnessFingerprint,
                        states, strategyScores, repairs, experienceEvents);
                return result(CognitiveExecutionResult.Status.FAILED_AFTER_EXECUTION, null,
                        states, strategyScores, reflections, repairs, experienceEvents, "FINAL_REFLECTION_REJECTED");
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
                        states, strategyScores, reflections, repairs, experienceEvents, "FINAL_REPAIR_REJECTED");
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
        return experienceLibrary.restore(domain, entryId, version);
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
                """.formatted(failedStep.stepId(), failedStep.stepId(), question,
                summarizeOutputs(outputs), failedStep.stepId(), failedStep.objective(),
                failedExecution == null ? "UNKNOWN" : failedExecution.failureCode());
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
        if (selected == null || experienceLibrary == null || requestId == null || requestId.isBlank()
                || harnessFingerprint == null || !harnessFingerprint.matches("[0-9a-f]{64}")) {
            return;
        }
        EvidenceReference evidence = evidenceReference(requestId, harnessFingerprint, states, scores, repairs);
        try {
            ExperienceEntry updated = experienceLibrary.recordFeedback(selected.domain(), selected.entryId(),
                    success, evidence, requestId, harnessFingerprint);
            events.add(new CognitiveTrace.ExperienceEvent(updated.entryId(), updated.version(),
                    updated.retired() ? "FEEDBACK_AND_RETIRE" : "FEEDBACK", updated.feedbackAttempts(),
                    updated.feedbackSuccesses(), updated.retired()));
            if (updated.retired()) {
                states.add("STRATEGY_RETIRED");
            }
        } catch (RuntimeException error) {
            states.add("EXPERIENCE_FEEDBACK_FAILED");
        }
    }

    private void adoptStrategy(String strategy, String requestId, String harnessFingerprint,
                               ExperienceDomain domain, List<String> states,
                               List<CognitiveTrace.StrategyScore> scores,
                               List<CognitiveTrace.RepairEvent> repairs,
                               List<CognitiveTrace.ExperienceEvent> events) {
        if (strategy == null || strategy.isBlank() || experienceLibrary == null || domain == null
                || requestId == null || requestId.isBlank()
                || harnessFingerprint == null || !harnessFingerprint.matches("[0-9a-f]{64}")) {
            return;
        }
        String lesson = boundedStrategy(strategy);
        String entryId = entryIdForStrategy(lesson);
        try {
            EvidenceReference evidence = evidenceReference(requestId, harnessFingerprint, states, scores, repairs);
            ExperienceEntry candidate = new ExperienceEntry(entryId, domain, EXPERIENCE_CUE, lesson,
                    "Model-reflected successful runtime execution; not an external correctness label.", evidence,
                    0.5, requestId, harnessFingerprint, ExperienceDisposition.ADOPTED, false,
                    AttributionStatus.UNRESOLVED, 1, false, null, Instant.now());
            ExperienceEntry saved = experienceLibrary.adoptRuntimeStrategy(candidate);
            String action = saved.version() == 1 ? "AUTO_ADOPTED" : "RECONFIRMED";
            events.add(new CognitiveTrace.ExperienceEvent(saved.entryId(), saved.version(), action,
                    saved.feedbackAttempts(), saved.feedbackSuccesses(), saved.retired()));
            if (saved.version() == 1) {
                states.add("STRATEGY_ADOPTED");
            }
        } catch (IllegalStateException error) {
            states.add("STRATEGY_ALREADY_RETIRED");
        } catch (RuntimeException error) {
            states.add("EXPERIENCE_ADOPTION_FAILED");
        }
    }

    private String entryIdForStrategy(String strategy) {
        return "cog-" + sha256(boundedStrategy(strategy)).substring(0, 24);
    }

    private EvidenceReference evidenceReference(String requestId, String fingerprint,
                                                List<String> states,
                                                List<CognitiveTrace.StrategyScore> scores,
                                                List<CognitiveTrace.RepairEvent> repairs) {
        String evidenceSha = sha256(String.join("|", states) + scores + repairs);
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
        return new CognitiveExecutionResult(status, answer,
                new CognitiveTrace(states, scores, reflections, repairs, events, reason));
    }

    @FunctionalInterface
    public interface StepExecutor {
        StepExecution execute(PlanStep step, String systemPrompt, Map<String, String> previousResults) throws Exception;
    }

    public record StepExecution(boolean succeeded, String output, String failureCode) {
        public static StepExecution success(String output) {
            return new StepExecution(true, output, null);
        }

        public static StepExecution failed(String failureCode) {
            return new StepExecution(false, null, failureCode == null ? "STEP_FAILED" : failureCode);
        }
    }
}
