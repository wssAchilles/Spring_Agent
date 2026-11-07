package tech.qiantong.qknow.hermes.cognitive.core.evaluation;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
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
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceStore;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyLifecycleStatus;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyTemplate;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyVariant;
import tech.qiantong.qknow.hermes.cognitive.core.engine.CognitiveSelfEvolutionEngine;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveExecutionResult;
import tech.qiantong.qknow.hermes.cognitive.core.verification.OutcomeSignal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class SelfEvolutionEvaluationRunner {
    private static final int EPISODES = 3;
    private static final String CUE = "cognitive-evolution";
    private static final String VARIANT_MUTATION = "对边界和多步骤结果进行二次核验。";
    private static final Pattern STRUCTURED_INPUT = Pattern.compile(
            "caseId=(S\\d{2}), score=(\\d+), accepted=(true|false)");
    private static final Pattern LOGIC_INPUT = Pattern.compile("Compute expression (-?\\d+) ([+*]) (-?\\d+)");

    public EvaluationReport runAndWrite(Path reportPath) throws IOException {
        EvaluationReport report = run();
        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        Files.writeString(reportPath, JSON.toJSONString(report, JSONWriter.Feature.PrettyFormat),
                StandardCharsets.UTF_8);
        return report;
    }

    public EvaluationReport run() throws IOException {
        EvaluationTaskLoader.Dataset dataset = new EvaluationTaskLoader().load();
        EnumMap<EvaluationArm, ArmState> states = createArms(dataset.tasks().getFirst().domain());
        List<EvaluationReport.TaskResult> results = new ArrayList<>();
        List<String> prompts = new ArrayList<>();
        Map<EvaluationArm, List<InMemoryExperienceStore>> storesByArm = new EnumMap<>(EvaluationArm.class);

        for (EvaluationArm arm : EvaluationArm.values()) {
            ArmState state = states.get(arm);
            storesByArm.put(arm, new ArrayList<>());
            if (state != null && state.store() != null) {
                storesByArm.get(arm).add(state.store());
            }
        }

        for (int episode = 1; episode <= EPISODES; episode++) {
            for (EvaluationTaskLoader.EvaluationTask task : dataset.tasks()) {
                if (task.split() == EvaluationTaskLoader.Split.BOOTSTRAP) {
                    results.add(runTask(dataset, task, episode, EvaluationArm.BASELINE,
                            null, null, prompts, null));
                    for (EvaluationArm arm : List.of(EvaluationArm.LESSON_ONLY,
                            EvaluationArm.STRATEGY_TEMPLATE, EvaluationArm.TEMPLATE_VARIANT_PROBATION)) {
                        ArmState state = states.get(arm);
                        results.add(runTask(dataset, task, episode, arm, state.library(), state.domain(),
                                prompts, state.store()));
                    }
                }
            }
            for (EvaluationArm arm : EvaluationArm.values()) {
                ArmState state = states.get(arm);
                if (state == null) {
                    for (EvaluationTaskLoader.EvaluationTask task : dataset.tasks()) {
                        if (task.split() == EvaluationTaskLoader.Split.HOLDOUT) {
                            results.add(runTask(dataset, task, episode, arm, null, null, prompts, null));
                        }
                    }
                } else {
                    InMemoryExperienceStore holdoutStore = copy(state.store(), state.domain());
                    storesByArm.get(arm).add(holdoutStore);
                    ExperienceLibrary holdoutLibrary = new DefaultExperienceLibrary(holdoutStore);
                    for (EvaluationTaskLoader.EvaluationTask task : dataset.tasks()) {
                        if (task.split() == EvaluationTaskLoader.Split.HOLDOUT) {
                            results.add(runTask(dataset, task, episode, arm, holdoutLibrary, state.domain(),
                                    prompts, holdoutStore));
                        }
                    }
                }
            }
        }

        int promptLeaks = countOutcomePayloadLeaks(dataset, prompts);
        int evidenceLeaks = countEvidenceLeaks(dataset, prompts);
        int armLabelLeaks = countArmLabelLeaks(prompts);
        int splitLabelLeaks = countSplitLabelLeaks(prompts);
        int experienceLeaks = countExperienceLeaks(dataset, storesByArm);
        int evidenceExperienceLeaks = countEvidenceExperienceLeaks(dataset, storesByArm);
        List<EvaluationReport.ArmMetrics> armMetrics = java.util.Arrays.stream(EvaluationArm.values())
                .map(arm -> summarize(arm, results)).toList();
        EvaluationReport.PairedComparison comparison = compareVariantToTemplate(results);
        List<EvaluationReport.ThresholdReplay> thresholdReplay = replayThresholds(results);
        return new EvaluationReport("BLINDED_FAKE_MODEL",
                "Deterministic prompt-only fake; it receives no runner-side arm, episode, split, or expected-outcome metadata. "
                        + "Its answers are derived from user-task inputs and do not vary with strategy guidance; "
                        + "this cannot establish real-model gains "
                        + "or API token/latency costs.",
                dataset.tasks().size(), EPISODES, 0, promptLeaks, evidenceLeaks, armLabelLeaks, splitLabelLeaks,
                experienceLeaks, evidenceExperienceLeaks,
                armMetrics, comparison, thresholdReplay, results);
    }

    private EnumMap<EvaluationArm, ArmState> createArms(String taskDomain) {
        EnumMap<EvaluationArm, ArmState> arms = new EnumMap<>(EvaluationArm.class);
        arms.put(EvaluationArm.BASELINE, null);
        for (EvaluationArm arm : List.of(EvaluationArm.LESSON_ONLY,
                EvaluationArm.STRATEGY_TEMPLATE, EvaluationArm.TEMPLATE_VARIANT_PROBATION)) {
            long id = 9100L + arm.ordinal();
            ExperienceDomain domain = ExperienceDomain.cognitiveScope(id, List.of(taskDomain));
            InMemoryExperienceStore store = new InMemoryExperienceStore();
            ExperienceLibrary library = new DefaultExperienceLibrary(store);
            if (arm == EvaluationArm.LESSON_ONLY) {
                library.recordExperience(entry("eval-lesson", domain,
                        "完成任务后，按任务步骤逐项复核最终结果。", ExperienceDisposition.ADOPTED, null, null));
            } else {
                ExperienceEntry parent = entry("eval-template", domain,
                        "输出前检查每个请求字段与步骤结果。", ExperienceDisposition.ADOPTED,
                        StrategyTemplate.create("eval-template", 1, null,
                                List.of("Apply only to the current task family"),
                                List.of("inspect", "execute", "verify"),
                                List.of("use the task-provided operation"),
                                List.of("输出前检查每个请求字段与步骤结果。"),
                                List.of("all requested assertions hold")), null);
                library.recordExperience(parent);
                if (arm == EvaluationArm.TEMPLATE_VARIANT_PROBATION) {
                    library.createVariant(variantCandidate(parent, VARIANT_MUTATION));
                }
            }
            arms.put(arm, new ArmState(domain, library, store));
        }
        return arms;
    }

    private ExperienceEntry entry(String id, ExperienceDomain domain, String lesson,
                                  ExperienceDisposition disposition, StrategyTemplate template,
                                  StrategyVariant variant) {
        ExperienceEntry entry = new ExperienceEntry(id, domain, CUE, lesson,
                "Deterministic evaluation fixture strategy.",
                new EvidenceReference("evidence-" + id, "seed-" + id, "a".repeat(64), "COGNITIVE"),
                disposition == ExperienceDisposition.ADOPTED ? 0.8 : 0.5,
                "seed-" + id, "b".repeat(64), disposition, false, AttributionStatus.UNRESOLVED,
                1, false, null, Instant.parse("2026-01-01T00:00:00Z"),
                disposition == ExperienceDisposition.ADOPTED ? 8 : 0,
                disposition == ExperienceDisposition.ADOPTED ? 6 : 0);
        return template == null ? entry : entry.withStrategy(template, variant);
    }

    private ExperienceEntry variantCandidate(ExperienceEntry parent, String mutation) {
        StrategyTemplate parentTemplate = parent.strategyTemplate();
        String id = variantId(parentTemplate.templateId(), mutation);
        List<String> reflectionRules = new ArrayList<>(parentTemplate.reflectionRules());
        reflectionRules.add(mutation);
        StrategyTemplate template = StrategyTemplate.create(id, parentTemplate.version() + 1,
                parentTemplate.templateId(), parentTemplate.preconditions(), parentTemplate.planSkeleton(),
                parentTemplate.toolPolicy(), reflectionRules, parentTemplate.successCriteria());
        return entry(id, parent.domain(), mutation, ExperienceDisposition.OBSERVED, template,
                new StrategyVariant(id, parentTemplate.templateId(), "controlled evaluation variant",
                        List.of("reflectionRules"), "Apply an additional check to boundary and multi-step results"));
    }

    private EvaluationReport.TaskResult runTask(EvaluationTaskLoader.Dataset dataset,
                                                 EvaluationTaskLoader.EvaluationTask task,
                                                 int episode, EvaluationArm arm,
                                                 ExperienceLibrary library, ExperienceDomain domain,
                                                 List<String> allPrompts, InMemoryExperienceStore store) {
        EvaluationTaskLoader.ExpectedOutcomeFixture expected = dataset.expectedOutcomes().get(task.taskId());
        BlindedFakeChatModel fake = new BlindedFakeChatModel(allPrompts::add);
        CognitiveSelfEvolutionEngine engine = new CognitiveSelfEvolutionEngine(library);
        Map<String, StrategyLifecycleStatus> before = variantStatuses(store, domain);
        long started = System.nanoTime();
        CognitiveExecutionResult execution = engine.execute("eval-" + arm.name().toLowerCase()
                        + "-" + episode + "-" + task.taskId(), task.question(), "offline evaluation system",
                fake.chatModel(), domain, fingerprint(arm, episode, task.taskId()), 4, 1,
                (step, systemPrompt, previous) -> {
                    if (Objects.equals(task.toolStatusStepId(), step.stepId())) {
                        return CognitiveSelfEvolutionEngine.StepExecution.success("COMPLETED");
                    }
                    return CognitiveSelfEvolutionEngine.StepExecution.success("fixture observation available");
                }, expected.evaluatorSpec());
        double latencyMs = (System.nanoTime() - started) / 1_000_000.0;
        OutcomeSignal signal = execution.trace() == null ? null : execution.trace().outcomeSignal();
        EvaluationReport.OutcomeStatus status = signal == null ? EvaluationReport.OutcomeStatus.UNKNOWN
                : EvaluationReport.OutcomeStatus.valueOf(signal.status().name());
        String selectedId = execution.trace() == null || execution.trace().strategyScores().isEmpty()
                ? null : execution.trace().strategyScores().getFirst().entryId();
        boolean modelAccepted = execution.trace() != null && !execution.trace().reflections().isEmpty()
                && execution.trace().reflections().getLast().accepted();
        Map<String, StrategyLifecycleStatus> after = variantStatuses(store, domain);
        boolean promoted = before.entrySet().stream().anyMatch(entry ->
                entry.getValue() == StrategyLifecycleStatus.PROBATION
                        && after.get(entry.getKey()) == StrategyLifecycleStatus.ADOPTED);
        boolean retired = before.entrySet().stream().anyMatch(entry ->
                entry.getValue() != StrategyLifecycleStatus.RETIRED
                        && after.get(entry.getKey()) == StrategyLifecycleStatus.RETIRED);
        var variantEvent = execution.trace() == null ? null : execution.trace().experienceEvents().stream()
                .filter(event -> variantId("eval-template", VARIANT_MUTATION).equals(event.entryId()))
                .reduce((first, second) -> second).orElse(null);
        return new EvaluationReport.TaskResult(arm, episode, task.taskId(), task.familyId(), task.split(),
                status, signal == null ? "OUTCOME_SIGNAL_MISSING" : signal.reasonCode(), selectedId,
                modelAccepted, fake.tokenEstimate(), latencyMs, promoted,
                variantEvent == null ? null : variantEvent.lifecycleStatus(),
                variantEvent == null ? 0 : variantEvent.probationUses(),
                variantEvent == null ? 0 : variantEvent.probationSuccesses(), retired,
                signal == null ? "" : signal.expectedDigest(), signal == null ? "" : signal.observedDigest(),
                signal == null ? "" : signal.evidenceDigest());
    }

    private InMemoryExperienceStore copy(InMemoryExperienceStore source, ExperienceDomain domain) {
        InMemoryExperienceStore copy = new InMemoryExperienceStore();
        source.list(domain).forEach(copy::save);
        return copy;
    }

    private Map<String, StrategyLifecycleStatus> variantStatuses(InMemoryExperienceStore store,
                                                                  ExperienceDomain domain) {
        if (store == null || domain == null) {
            return Map.of();
        }
        Map<String, StrategyLifecycleStatus> statuses = new HashMap<>();
        store.list(domain).stream().filter(entry -> entry.strategyVariant() != null)
                .forEach(entry -> statuses.put(entry.entryId(), entry.lifecycleStatus()));
        return statuses;
    }

    private EvaluationReport.ArmMetrics summarize(EvaluationArm arm,
                                                  List<EvaluationReport.TaskResult> allResults) {
        List<EvaluationReport.TaskResult> results = allResults.stream().filter(r -> r.arm() == arm).toList();
        int successes = countPass(results);
        List<EvaluationReport.TaskResult> bootstrap = results.stream()
                .filter(r -> r.split() == EvaluationTaskLoader.Split.BOOTSTRAP).toList();
        List<EvaluationReport.TaskResult> holdout = results.stream()
                .filter(r -> r.split() == EvaluationTaskLoader.Split.HOLDOUT).toList();
        long reused = results.stream().filter(r -> r.selectedStrategyId() != null).count();
        int regressions = (int) results.stream().collect(java.util.stream.Collectors.groupingBy(
                        EvaluationReport.TaskResult::taskId)).values().stream()
                .mapToLong(taskRuns -> {
                    List<EvaluationReport.TaskResult> ordered = taskRuns.stream()
                            .sorted(Comparator.comparingInt(EvaluationReport.TaskResult::episode)).toList();
                    int count = 0;
                    boolean passed = false;
                    for (EvaluationReport.TaskResult result : ordered) {
                        if (passed && result.outcomeStatus() != EvaluationReport.OutcomeStatus.PASS) {
                            count++;
                        }
                        passed = result.outcomeStatus() == EvaluationReport.OutcomeStatus.PASS;
                    }
                    return count;
                }).sum();
        List<EvaluationReport.EpisodeMetrics> curve = new ArrayList<>();
        for (int episode = 1; episode <= EPISODES; episode++) {
            int currentEpisode = episode;
            List<EvaluationReport.TaskResult> round = results.stream()
                    .filter(result -> result.episode() == currentEpisode).toList();
            List<EvaluationReport.TaskResult> roundBootstrap = round.stream()
                    .filter(result -> result.split() == EvaluationTaskLoader.Split.BOOTSTRAP).toList();
            List<EvaluationReport.TaskResult> roundHoldout = round.stream()
                    .filter(result -> result.split() == EvaluationTaskLoader.Split.HOLDOUT).toList();
            curve.add(new EvaluationReport.EpisodeMetrics(episode, rate(countPass(round), round.size()),
                    rate(countPass(roundBootstrap), roundBootstrap.size()),
                    rate(countPass(roundHoldout), roundHoldout.size())));
        }
        return new EvaluationReport.ArmMetrics(arm, results.size(), successes, rate(successes, results.size()),
                rate(countPass(bootstrap), bootstrap.size()), rate(countPass(holdout), holdout.size()),
                rate((int) reused, results.size()),
                (int) results.stream().filter(EvaluationReport.TaskResult::variantPromoted).count(),
                (int) results.stream().filter(EvaluationReport.TaskResult::variantRetired).count(), regressions,
                results.stream().mapToLong(EvaluationReport.TaskResult::promptTokenEstimate).average().orElse(0.0),
                results.stream().mapToDouble(EvaluationReport.TaskResult::localLatencyMs).average().orElse(0.0), curve);
    }

    private EvaluationReport.PairedComparison compareVariantToTemplate(
            List<EvaluationReport.TaskResult> results) {
        Map<String, EvaluationReport.TaskResult> template = new HashMap<>();
        Map<String, EvaluationReport.TaskResult> variant = new HashMap<>();
        for (EvaluationReport.TaskResult result : results) {
            if (result.split() != EvaluationTaskLoader.Split.HOLDOUT) {
                continue;
            }
            String key = result.episode() + ":" + result.taskId();
            if (result.arm() == EvaluationArm.STRATEGY_TEMPLATE) {
                template.put(key, result);
            } else if (result.arm() == EvaluationArm.TEMPLATE_VARIANT_PROBATION) {
                variant.put(key, result);
            }
        }
        int wins = 0;
        int losses = 0;
        int ties = 0;
        for (String key : template.keySet()) {
            boolean templatePass = template.get(key).outcomeStatus() == EvaluationReport.OutcomeStatus.PASS;
            boolean variantPass = variant.get(key).outcomeStatus() == EvaluationReport.OutcomeStatus.PASS;
            if (variantPass && !templatePass) {
                wins++;
            } else if (templatePass && !variantPass) {
                losses++;
            } else {
                ties++;
            }
        }
        return new EvaluationReport.PairedComparison(template.size(), wins, ties, losses, losses);
    }

    private List<EvaluationReport.ThresholdReplay> replayThresholds(
            List<EvaluationReport.TaskResult> results) {
        List<Boolean> outcomes = results.stream()
                .filter(result -> result.arm() == EvaluationArm.TEMPLATE_VARIANT_PROBATION
                        && result.split() == EvaluationTaskLoader.Split.BOOTSTRAP
                        && variantId("eval-template", VARIANT_MUTATION).equals(result.selectedStrategyId()))
                .sorted(Comparator.comparingInt(EvaluationReport.TaskResult::episode)
                        .thenComparing(EvaluationReport.TaskResult::taskId))
                .map(result -> result.outcomeStatus() == EvaluationReport.OutcomeStatus.PASS).toList();
        return List.of(new int[]{2, 2}, new int[]{3, 2}, new int[]{3, 3}, new int[]{4, 3}).stream()
                .map(threshold -> thresholdReplay(outcomes, threshold[0], threshold[1])).toList();
    }

    private EvaluationReport.ThresholdReplay thresholdReplay(List<Boolean> outcomes,
                                                              int minUses, int minSuccesses) {
        int successes = 0;
        for (int i = 0; i < outcomes.size(); i++) {
            if (outcomes.get(i)) {
                successes++;
            }
            if (i + 1 >= minUses && successes >= minSuccesses) {
                return new EvaluationReport.ThresholdReplay(minUses, minSuccesses, i + 1, successes, true);
            }
        }
        return new EvaluationReport.ThresholdReplay(minUses, minSuccesses, null, successes, false);
    }

    private int countOutcomePayloadLeaks(EvaluationTaskLoader.Dataset dataset, List<String> prompts) {
        return (int) dataset.expectedOutcomes().values().stream()
                .filter(outcome -> prompts.stream().anyMatch(prompt -> prompt.contains(outcome.value()))).count();
    }

    private int countEvidenceLeaks(EvaluationTaskLoader.Dataset dataset, List<String> prompts) {
        return (int) dataset.expectedOutcomes().values().stream()
                .flatMap(outcome -> outcome.evidenceRefs().stream())
                .filter(evidence -> prompts.stream().anyMatch(prompt -> prompt.contains(evidence))).count();
    }

    private int countArmLabelLeaks(List<String> prompts) {
        return (int) prompts.stream().filter(prompt -> {
            String normalized = prompt.toUpperCase(Locale.ROOT);
            return java.util.Arrays.stream(EvaluationArm.values())
                    .anyMatch(arm -> normalized.contains(arm.name()));
        }).count();
    }

    private int countSplitLabelLeaks(List<String> prompts) {
        return (int) prompts.stream().filter(prompt -> {
            String normalized = prompt.toUpperCase(Locale.ROOT);
            return java.util.Arrays.stream(EvaluationTaskLoader.Split.values())
                    .anyMatch(split -> normalized.contains(split.name()));
        }).count();
    }

    private int countExperienceLeaks(EvaluationTaskLoader.Dataset dataset,
                                     Map<EvaluationArm, List<InMemoryExperienceStore>> storesByArm) {
        List<String> experienceContent = storesByArm.values().stream().flatMap(List::stream)
                .flatMap(store -> store.allEntries().stream())
                .map(entry -> entry.cue() + entry.lesson() + entry.evidenceSummary()
                        + JSON.toJSONString(entry.evidenceRef())).toList();
        return (int) dataset.expectedOutcomes().values().stream()
                .filter(outcome -> experienceContent.stream().anyMatch(content -> content.contains(outcome.value())))
                .count();
    }

    private int countEvidenceExperienceLeaks(EvaluationTaskLoader.Dataset dataset,
                                             Map<EvaluationArm, List<InMemoryExperienceStore>> storesByArm) {
        List<String> experienceContent = storesByArm.values().stream().flatMap(List::stream)
                .flatMap(store -> store.allEntries().stream())
                .map(entry -> entry.cue() + entry.lesson() + entry.evidenceSummary()
                        + JSON.toJSONString(entry.evidenceRef())).toList();
        return (int) dataset.expectedOutcomes().values().stream()
                .flatMap(outcome -> outcome.evidenceRefs().stream())
                .filter(evidence -> experienceContent.stream().anyMatch(content -> content.contains(evidence)))
                .count();
    }

    private int countPass(List<EvaluationReport.TaskResult> results) {
        return (int) results.stream()
                .filter(result -> result.outcomeStatus() == EvaluationReport.OutcomeStatus.PASS).count();
    }

    private double rate(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private String fingerprint(EvaluationArm arm, int episode, String taskId) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((arm + ":" + episode + ":" + taskId).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String variantId(String parentTemplateId, String strategy) {
        return "var-" + sha256(parentTemplateId + "|" + strategy).substring(0, 24);
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record ArmState(ExperienceDomain domain, ExperienceLibrary library,
                            InMemoryExperienceStore store) { }

    private static final class BlindedFakeChatModel {
        private final Consumer<String> recordPrompt;
        private final AtomicLong tokens = new AtomicLong();

        private BlindedFakeChatModel(Consumer<String> recordPrompt) {
            this.recordPrompt = recordPrompt;
        }

        private ChatModel chatModel() {
            ChatModel model = mock(ChatModel.class);
            when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
                Prompt prompt = invocation.getArgument(0);
                String system = text(prompt, SystemMessage.class);
                String user = text(prompt, UserMessage.class);
                String combined = system + "\n" + user;
                recordPrompt.accept(combined);
                tokens.addAndGet(Math.max(1, combined.length() / 4));
                return response(responseText(user));
            });
            return model;
        }

        private long tokenEstimate() {
            return tokens.get();
        }

        private String responseText(String user) {
            if (user.startsWith("将用户任务拆解")) {
                return "{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"inspect task input\",\"dependencies\":[]},"
                        + "{\"stepId\":\"s2\",\"objective\":\"derive and verify result\",\"dependencies\":[\"s1\"]}]}";
            }
            if (user.startsWith("根据已完成的步骤结果回答用户任务")) {
                return answer(user);
            }
            if (user.startsWith("根据原任务和执行结果评估")) {
                return "{\"accepted\":true,\"repair\":null,\"strategy\":\"\"}";
            }
            throw new IllegalStateException("unexpected cognitive prompt type");
        }

        private String answer(String user) {
            Matcher structured = STRUCTURED_INPUT.matcher(user);
            if (structured.find()) {
                int score = Integer.parseInt(structured.group(2));
                return "{\"caseId\":\"" + structured.group(1) + "\",\"score\":" + score
                        + ",\"accepted\":" + structured.group(3) + "}";
            }
            if (user.contains("Execute operation")) {
                Matcher status = Pattern.compile("s1: ([A-Z_]+)").matcher(user);
                return status.find() ? status.group(1) : "UNKNOWN";
            }
            Matcher matcher = LOGIC_INPUT.matcher(user);
            if (matcher.find()) {
                long left = Long.parseLong(matcher.group(1));
                long right = Long.parseLong(matcher.group(3));
                long value = "+".equals(matcher.group(2)) ? left + right : left * right;
                return Long.toString(value);
            }
            throw new IllegalArgumentException("task prompt is not parseable");
        }

        private static String text(Prompt prompt, Class<? extends Message> messageType) {
            return prompt.getInstructions().stream().filter(messageType::isInstance)
                    .map(Message::getText).filter(Objects::nonNull).findFirst().orElse("");
        }

        private static ChatResponse response(String text) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }
    }

    private static final class InMemoryExperienceStore implements ExperienceStore {
        private final Map<String, ExperienceEntry> entries = new LinkedHashMap<>();

        @Override
        public synchronized ExperienceEntry save(ExperienceEntry entry) {
            entries.put(key(entry.domain(), entry.entryId()), entry);
            return entry;
        }

        @Override
        public synchronized java.util.Optional<ExperienceEntry> find(ExperienceDomain domain, String entryId) {
            return java.util.Optional.ofNullable(entries.get(key(domain, entryId)));
        }

        @Override
        public synchronized java.util.Optional<ExperienceEntry> findVersion(ExperienceDomain domain,
                                                                             String entryId, int version) {
            return find(domain, entryId).filter(entry -> entry.version() == version);
        }

        @Override
        public synchronized List<ExperienceEntry> list(ExperienceDomain domain) {
            return entries.values().stream().filter(entry -> entry.domain().equals(domain))
                    .sorted(Comparator.comparing(ExperienceEntry::entryId)).toList();
        }

        private synchronized List<ExperienceEntry> allEntries() {
            return List.copyOf(entries.values());
        }

        private String key(ExperienceDomain domain, String entryId) {
            return domain.toSlug() + ":" + entryId;
        }
    }
}
