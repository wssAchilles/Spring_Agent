package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import com.alibaba.fastjson2.JSON;
import tech.qiantong.qknow.hermes.agent.harness.experience.DefaultExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.EvidenceReference;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDisposition;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceStore;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyLifecycleStatus;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyTemplate;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyVariant;
import tech.qiantong.qknow.hermes.cognitive.core.engine.ExperienceStrategySelector;

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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class TauRetailEvaluationRunner {
    private static final int EPISODES = 3;
    private static final String CUE = "tau retail tool execution and state verification";
    private static final String FINGERPRINT = sha256("tau-retail-offline-eval-v1");
    private final ExperienceStrategySelector selector = new ExperienceStrategySelector();

    public EvaluationReport run(AgentPolicy policy) throws IOException {
        Objects.requireNonNull(policy, "policy");
        TauRetailTaskLoader loader = new TauRetailTaskLoader();
        TauRetailTaskLoader.Dataset dataset = loader.load();
        String policyText = loader.policy();
        com.alibaba.fastjson2.JSONObject database = TauRetailEnvironment.fromResource().snapshot();
        EnumMap<Arm, ArmState> states = seedArms();
        List<TaskResult> results = new ArrayList<>();
        for (int episode = 1; episode <= EPISODES; episode++) {
            for (Arm arm : Arm.values()) {
                ArmState state = states.get(arm);
                for (TauRetailTaskLoader.TaskCase task : dataset.bootstrap()) {
                    results.add(runTask(policy, policyText, database, task, episode, arm, state, true));
                }
            }
        }

        EnumMap<Arm, InMemoryExperienceStore> holdoutStores = new EnumMap<>(Arm.class);
        EnumMap<Arm, ExperienceLibrary> holdoutLibraries = new EnumMap<>(Arm.class);
        for (Arm arm : Arm.values()) {
            ArmState state = states.get(arm);
            if (state != null) {
                InMemoryExperienceStore copy = new InMemoryExperienceStore(state.store().list(state.domain()));
                holdoutStores.put(arm, copy);
                holdoutLibraries.put(arm, new DefaultExperienceLibrary(copy));
            }
        }
        for (int episode = 1; episode <= EPISODES; episode++) {
            for (Arm arm : Arm.values()) {
                ArmState original = states.get(arm);
                ArmState holdout = original == null ? null : new ArmState(original.domain(),
                        holdoutLibraries.get(arm), holdoutStores.get(arm));
                for (TauRetailTaskLoader.TaskCase task : dataset.holdout()) {
                    results.add(runTask(policy, policyText, database, task, episode, arm, holdout, false));
                }
            }
        }
        return summarize(results, dataset);
    }

    public EvaluationReport runAndWrite(AgentPolicy policy, Path path) throws IOException {
        EvaluationReport report = run(policy);
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, JSON.toJSONString(report), StandardCharsets.UTF_8);
        return report;
    }

    private EnumMap<Arm, ArmState> seedArms() {
        EnumMap<Arm, ArmState> states = new EnumMap<>(Arm.class);
        states.put(Arm.BASELINE, null);
        for (Arm arm : List.of(Arm.LESSON_ONLY, Arm.STRATEGY_TEMPLATE, Arm.TEMPLATE_VARIANT_PROBATION)) {
            ExperienceDomain domain = ExperienceDomain.cognitiveScope(9200L + arm.ordinal(), List.of("tau_retail"));
            InMemoryExperienceStore store = new InMemoryExperienceStore();
            ExperienceLibrary library = new DefaultExperienceLibrary(store);
            if (arm == Arm.LESSON_ONLY) {
                library.recordExperience(entry("tau-lesson", domain,
                        "把用户要求拆成独立操作，完成后逐项核对结果。", null, null));
            } else {
                ExperienceEntry parent = entry("tau-template", domain,
                        "先读取相关订单和商品，再按要求逐项执行并复核状态。",
                        StrategyTemplate.create("tau-template", 1, null,
                                List.of("仅使用当前任务和同域工具数据"),
                                List.of("inspect order", "execute requested operation", "verify state"),
                                List.of("use registered retail tools"),
                                List.of("检查多项请求是否全部完成"),
                                List.of("reference tool actions and resulting state match")), null);
                library.recordExperience(parent);
                if (arm == Arm.TEMPLATE_VARIANT_PROBATION) {
                    StrategyTemplate template = parent.strategyTemplate();
                    StrategyTemplate variantTemplate = StrategyTemplate.create("tau-template-v2", 2,
                            template.templateId(), template.preconditions(), template.planSkeleton(),
                            template.toolPolicy(), List.of("每次变更后重新读取相关订单字段"),
                            template.successCriteria());
                    library.createVariant(candidateEntry("tau-template-v2", domain,
                            "每次变更后重新读取相关订单字段。", variantTemplate,
                            new StrategyVariant("tau-template-v2", "tau-template", "verify each mutation",
                                    List.of("reflectionRules"), "detect incomplete multi-step state changes")));
                }
            }
            states.put(arm, new ArmState(domain, library, store));
        }
        return states;
    }

    private ExperienceEntry entry(String id, ExperienceDomain domain, String lesson,
                                  StrategyTemplate template, StrategyVariant variant) {
        ExperienceEntry entry = new ExperienceEntry(id, domain, CUE, lesson,
                "Offline retail evaluation strategy fixture.",
                new EvidenceReference("tau-evidence-" + id, "tau-seed-" + id,
                        "a".repeat(64), "COGNITIVE"),
                0.8, "tau-seed-" + id, FINGERPRINT,
                ExperienceDisposition.ADOPTED, false, ExperienceEntry.AttributionStatus.UNRESOLVED,
                1, false, null, Instant.parse("2026-01-01T00:00:00Z"), 8, 6);
        return template == null ? entry : entry.withStrategy(template, variant);
    }

    private ExperienceEntry candidateEntry(String id, ExperienceDomain domain, String lesson,
                                           StrategyTemplate template, StrategyVariant variant) {
        ExperienceEntry entry = new ExperienceEntry(id, domain, CUE, lesson,
                "Offline retail evaluation strategy variant.",
                new EvidenceReference("tau-evidence-" + id, "tau-seed-" + id,
                        "c".repeat(64), "COGNITIVE"),
                0.5, "tau-seed-" + id, FINGERPRINT,
                ExperienceDisposition.OBSERVED, false, ExperienceEntry.AttributionStatus.UNRESOLVED,
                1, false, null, Instant.parse("2026-01-01T00:00:00Z"));
        return entry.withStrategy(template, variant);
    }

    private TaskResult runTask(AgentPolicy policy, String policyText,
                               com.alibaba.fastjson2.JSONObject sourceDatabase,
                               TauRetailTaskLoader.TaskCase task, int episode, Arm arm,
                               ArmState state, boolean updateFeedback) {
        TauRetailEnvironment environment = new TauRetailEnvironment(sourceDatabase);
        TauRetailTools tools = new TauRetailTools(environment);
        TauRetailUserSimulator.Session user = new TauRetailUserSimulator().start(task.scenario());
        String failureCode = null;
        long start = System.nanoTime();
        try {
            policy.execute(policyText, user.initialMessage(), selectGuidance(state), tools, user);
        } catch (RuntimeException failure) {
            failureCode = "AGENT_POLICY_" + failure.getClass().getSimpleName();
        }
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
        TauRetailStateEvaluator.Evaluation evaluation = new TauRetailStateEvaluator()
                .evaluate(task, sourceDatabase, environment);
        if (failureCode != null) {
            evaluation = new TauRetailStateEvaluator.Evaluation(TauRetailStateEvaluator.Status.FAIL,
                    failureCode, evaluation.expectedDigest(), evaluation.observedDigest(), evaluation.evidenceDigest());
        }
        String selectedId = selectedId(state);
        boolean promoted = false;
        boolean retired = false;
        StrategyLifecycleStatus lifecycle = variantStatus(state);
        StrategyLifecycleStatus variantBefore = variantStatus(state);
        if (updateFeedback && state != null && selectedId != null) {
            StrategyLifecycleStatus before = state.library().find(state.domain(), selectedId)
                    .map(ExperienceEntry::lifecycleStatus).orElse(null);
            boolean success = evaluation.status() == TauRetailStateEvaluator.Status.PASS;
            if (before == StrategyLifecycleStatus.PROBATION) {
                state.library().recordProbationFeedback(state.domain(), selectedId, success,
                        evidence(evaluation), "tau-" + arm + "-" + episode + "-" + task.scenario().taskId(),
                        FINGERPRINT);
                state.library().find(state.domain(), selectedId).filter(entry ->
                        entry.lifecycleStatus() == StrategyLifecycleStatus.PROBATION
                                && entry.probationUses() >= 3 && entry.probationSuccesses() >= 2)
                        .ifPresent(entry -> state.library().promote(state.domain(), selectedId));
            } else if (before == StrategyLifecycleStatus.ADOPTED) {
                state.library().recordFeedback(state.domain(), selectedId, success,
                        evidence(evaluation), "tau-" + arm + "-" + episode + "-" + task.scenario().taskId(),
                        FINGERPRINT);
            }
            StrategyLifecycleStatus variantAfter = variantStatus(state);
            promoted = variantBefore == StrategyLifecycleStatus.PROBATION
                    && variantAfter == StrategyLifecycleStatus.ADOPTED;
            retired = variantBefore == StrategyLifecycleStatus.PROBATION
                    && variantAfter == StrategyLifecycleStatus.RETIRED;
            lifecycle = variantStatus(state);
        }
        return new TaskResult(arm, episode, task.scenario().taskId(), task.scenario().split(),
                evaluation.status(), evaluation.reasonCode(), selectedId, lifecycle,
                environment.calls().size(), environment.errors().size(), promoted, retired,
                latencyMs, evaluation.expectedDigest(), evaluation.observedDigest(), evaluation.evidenceDigest());
    }

    private String selectGuidance(ArmState state) {
        ExperienceEntry selected = selected(state);
        return selected == null ? "" : selected.strategyTemplate() == null
                ? selected.lesson() : selected.strategyTemplate().renderGuidance();
    }

    private String selectedId(ArmState state) {
        ExperienceEntry selected = selected(state);
        return selected == null ? null : selected.entryId();
    }

    private ExperienceEntry selected(ArmState state) {
        if (state == null) {
            return null;
        }
        return selector.select(state.library().recallForStrategySelection(state.domain(), CUE, 8)
                .matchedEntries()).selected();
    }

    private StrategyLifecycleStatus variantStatus(ArmState state) {
        return state == null ? null : state.library().find(state.domain(), "tau-template-v2")
                .map(ExperienceEntry::lifecycleStatus).orElse(null);
    }

    private EvidenceReference evidence(TauRetailStateEvaluator.Evaluation evaluation) {
        String digest = evaluation.evidenceDigest().isBlank() ? sha256("no tool evidence") : evaluation.evidenceDigest();
        return new EvidenceReference("tau-outcome-" + digest.substring(0, 16),
                "tau-eval-run", digest, "COGNITIVE");
    }

    private EvaluationReport summarize(List<TaskResult> results, TauRetailTaskLoader.Dataset dataset) {
        List<ArmMetrics> arms = new ArrayList<>();
        for (Arm arm : Arm.values()) {
            List<TaskResult> selected = results.stream().filter(result -> result.arm() == arm).toList();
            arms.add(new ArmMetrics(arm, selected.size(), rate(selected, TauRetailTaskLoader.Split.BOOTSTRAP),
                    rate(selected, TauRetailTaskLoader.Split.HOLDOUT),
                    (int) selected.stream().filter(result -> result.status() == TauRetailStateEvaluator.Status.PASS).count(),
                    (int) selected.stream().filter(result -> result.status() == TauRetailStateEvaluator.Status.FAIL).count(),
                    (int) selected.stream().filter(result -> result.status() == TauRetailStateEvaluator.Status.UNKNOWN).count(),
                    selected.stream().filter(result -> result.selectedStrategyId() != null).count(),
                    selected.stream().filter(TaskResult::variantPromoted).count(),
                    selected.stream().filter(TaskResult::variantRetired).count(),
                    selected.stream().mapToDouble(TaskResult::localLatencyMs).average().orElse(0.0)));
        }
        ArmMetrics baseline = arms.stream().filter(metrics -> metrics.arm() == Arm.BASELINE).findFirst().orElseThrow();
        PairedComparison comparison = compare(Arm.STRATEGY_TEMPLATE, Arm.TEMPLATE_VARIANT_PROBATION, results);
        return new EvaluationReport("DETERMINISTIC_OFFLINE_POLICY",
                "Policy receives only public user text, tools, simulator and selected strategy guidance; gold actions remain evaluator-only. "
                        + "Results characterize this deterministic policy and adapter, not real-model gains.",
                dataset.bootstrap().size(), dataset.holdout().size(), EPISODES, 0,
                baseline.bootstrapSuccessRate() == 1.0 && baseline.holdoutSuccessRate() == 1.0,
                comparison, arms, results);
    }

    private PairedComparison compare(Arm left, Arm right, List<TaskResult> results) {
        Map<String, TaskResult> leftResults = results.stream().filter(result -> result.arm() == left)
                .collect(java.util.stream.Collectors.toMap(this::pairKey, result -> result));
        int wins = 0;
        int ties = 0;
        int losses = 0;
        for (TaskResult rightResult : results.stream().filter(result -> result.arm() == right).toList()) {
            TaskResult leftResult = leftResults.get(pairKey(rightResult));
            if (leftResult == null) {
                continue;
            }
            boolean leftPassed = leftResult.status() == TauRetailStateEvaluator.Status.PASS;
            boolean rightPassed = rightResult.status() == TauRetailStateEvaluator.Status.PASS;
            if (rightPassed && !leftPassed) {
                wins++;
            } else if (leftPassed && !rightPassed) {
                losses++;
            } else {
                ties++;
            }
        }
        return new PairedComparison(wins + ties + losses, wins, ties, losses);
    }

    private String pairKey(TaskResult result) {
        return result.episode() + "|" + result.taskId() + "|" + result.split();
    }

    private double rate(List<TaskResult> results, TauRetailTaskLoader.Split split) {
        List<TaskResult> selected = results.stream().filter(result -> result.split() == split).toList();
        return selected.isEmpty() ? 0.0 : selected.stream()
                .filter(result -> result.status() == TauRetailStateEvaluator.Status.PASS).count() / (double) selected.size();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    @FunctionalInterface
    public interface AgentPolicy {
        void execute(String retailPolicy, String publicUserMessage, String strategyGuidance, TauRetailTools tools,
                     TauRetailUserSimulator.Session user);
    }

    public enum Arm {
        BASELINE,
        LESSON_ONLY,
        STRATEGY_TEMPLATE,
        TEMPLATE_VARIANT_PROBATION
    }

    private record ArmState(ExperienceDomain domain, ExperienceLibrary library, InMemoryExperienceStore store) { }

    public record EvaluationReport(String mode, String interpretation, int bootstrapTasks,
                                   int holdoutTasks, int episodes, int realApiCalls,
                                   boolean baselineSaturated, PairedComparison strategyTemplateVsVariantProbation,
                                   List<ArmMetrics> arms, List<TaskResult> taskResults) {
        public EvaluationReport {
            arms = List.copyOf(arms);
            taskResults = List.copyOf(taskResults);
        }
    }

    public record ArmMetrics(Arm arm, int runs, double bootstrapSuccessRate,
                             double holdoutSuccessRate, int pass, int fail, int unknown,
                             long strategyReuseCount, long variantPromotions, long variantRetirements,
                             double averageLocalLatencyMs) { }

    public record PairedComparison(int taskEpisodePairs, int variantWins, int ties, int variantLosses) { }

    public record TaskResult(Arm arm, int episode, String taskId, TauRetailTaskLoader.Split split,
                             TauRetailStateEvaluator.Status status, String reasonCode,
                             String selectedStrategyId, StrategyLifecycleStatus variantLifecycleStatus,
                             int toolCalls, int toolErrors, boolean variantPromoted,
                             boolean variantRetired, double localLatencyMs,
                             String expectedDigest, String observedDigest, String evidenceDigest) { }

    private static final class InMemoryExperienceStore implements ExperienceStore {
        private final ConcurrentHashMap<String, ExperienceEntry> entries = new ConcurrentHashMap<>();

        private InMemoryExperienceStore() { }

        private InMemoryExperienceStore(List<ExperienceEntry> source) {
            source.forEach(entry -> entries.put(key(entry.domain(), entry.entryId()), entry));
        }

        @Override
        public ExperienceEntry save(ExperienceEntry entry) {
            return entries.compute(key(entry.domain(), entry.entryId()), (ignored, current) -> {
                if (current != null && entry.version() < current.version()) {
                    throw new IllegalArgumentException("experience versions must not move backwards");
                }
                return entry;
            });
        }

        @Override
        public Optional<ExperienceEntry> find(ExperienceDomain domain, String entryId) {
            return Optional.ofNullable(entries.get(key(domain, entryId)));
        }

        @Override
        public Optional<ExperienceEntry> findVersion(ExperienceDomain domain, String entryId, int version) {
            return find(domain, entryId).filter(entry -> entry.version() == version);
        }

        @Override
        public List<ExperienceEntry> list(ExperienceDomain domain) {
            return entries.values().stream().filter(entry -> entry.domain().equals(domain))
                    .sorted(Comparator.comparing(ExperienceEntry::entryId)).toList();
        }

        private static String key(ExperienceDomain domain, String id) {
            return domain.toSlug() + "::" + id;
        }
    }
}
