package tech.qiantong.qknow.hermes.agent.harness.ppc.controlled;

import com.alibaba.fastjson2.JSON;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.ai.deepseek.DeepSeekChatOptions;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HarnessUsageSnapshot;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.InvariantSystemPromptRecord;
import tech.qiantong.qknow.hermes.agent.harness.UsageCapturingChatModel;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.experience.DefaultExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.EvidenceReference;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDisposition;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry;
import tech.qiantong.qknow.hermes.agent.harness.experience.FileSystemExperienceStore;
import tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel.PpcRoundEvidence.Score;
import tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel.PpcStopPolicy;
import tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel.TaskLevelRecursivePpc;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.config.PlanSolveConfig;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.hermes.proto.RAGContext;

/** Fixed 30+20 controlled schedule over the real AgentOrchestrator; no production registration. */
public final class ControlledPpcRunner {
    public static final ExperienceDomain DOMAIN =
            new ExperienceDomain(110201, 110201, "controlled_harness11b2a");
    private static final String MODEL = "deepseek-flash";
    private static final String FIXTURE_SHA = "4d30d11b3fd73e8a647a6fb51ea6ec90dbd52bb5b59eae7fb4959b4546c263f4";
    private static final String VERIFY_SHA = "1b307ea0c51c1ee7ff4e5576fed0e608dc0477a482d14f5e4d99e90f4074dc77";
    private static final Map<String, String> FROZEN_VOUCHERS = Map.of(
            "baseline", "b0bef691ef7890671a96224d24dc60d245fb1ba068cdee287da58d5b11d0bfe5",
            "CONCISE_SYSTEM_PROMPT", "8f1adbcb1ace26ca138a09ef6b55c55a754c818c3ef95049d2496bba1b024b5c",
            "ADAPTIVE_MEMORY_WINDOW", "4b31f14a8d17ba1c3098be034e6accac902d10db85a97f282f1670b1ca276cd8");
    private static final List<Operator> BOOTSTRAP_OPERATORS = List.of(
            Operator.CONCISE_SYSTEM_PROMPT, Operator.ADAPTIVE_MEMORY_WINDOW);

    private final List<ControlledFixture> fixtures;
    private final Path outputRoot;
    private final boolean verifiedFixtureSource;

    public ControlledPpcRunner(List<ControlledFixture> fixtures, Path outputRoot) {
        this(fixtures, outputRoot, false);
    }

    public ControlledPpcRunner(JdbcControlledFixtureSource source, Path outputRoot) throws SQLException {
        this(Objects.requireNonNull(source, "source").load(), outputRoot, true);
    }

    private ControlledPpcRunner(List<ControlledFixture> fixtures, Path outputRoot, boolean verifiedFixtureSource) {
        this.fixtures = List.copyOf(fixtures);
        this.outputRoot = Objects.requireNonNull(outputRoot, "outputRoot").toAbsolutePath().normalize();
        this.verifiedFixtureSource = verifiedFixtureSource;
        validateFixtures(this.fixtures);
    }

    public RunResult runPreLive(ChatModel testDouble) throws IOException {
        Objects.requireNonNull(testDouble, "testDouble");
        if (testDouble instanceof DeepSeekCompatibleChatModel) {
            throw new IllegalArgumentException("pre-live must not use the real DeepSeek client");
        }
        return execute(testDouble, false);
    }

    public RunResult runLive(String apiKey) throws IOException {
        if (apiKey == null || apiKey.length() < 16 || apiKey.toLowerCase(Locale.ROOT).contains("placeholder")) {
            return new RunResult("", Status.BLOCKED, "DEEPSEEK_API_KEY is absent or invalid", null,
                    List.of(), 0, 0, 0.0D, null);
        }
        if (!verifiedFixtureSource) {
            return new RunResult("", Status.BLOCKED, "frozen PostgreSQL fixture has not been verified", null,
                    List.of(), 0, 0, 0.0D, null);
        }
        if (!outputRoot.endsWith(Path.of("experiment/outputs/harness11b2a"))) {
            throw new IllegalArgumentException("live evidence requires the isolated harness11b2a output root");
        }
        return execute(new DeepSeekCompatibleChatModel("https://api.deepseek.com", apiKey,
                MODEL, null, 2_000, false), true);
    }

    private RunResult execute(ChatModel model, boolean live) throws IOException {
        verifyFrozenResource("harness11b2a-controlled/fixture.sql", FIXTURE_SHA);
        verifyFrozenResource("harness11b2a-controlled/verify-retrieval.sql", VERIFY_SHA);
        HermesHarnessVoucher baseline = baseline();
        var mutator = new tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator();
        Map<String, HermesHarnessVoucher> vouchers = new LinkedHashMap<>();
        vouchers.put("baseline", baseline);
        for (Operator operator : BOOTSTRAP_OPERATORS) {
            vouchers.put(operator.name(), mutator.mutate(baseline, operator));
        }
        Map<String, Map<String, String>> canonicalVouchers = new LinkedHashMap<>();
        vouchers.forEach((name, voucher) -> {
            if (!voucher.computeFingerprint().equals(FROZEN_VOUCHERS.get(name))) {
                throw new IllegalStateException("controlled voucher differs from frozen candidate: " + name);
            }
            canonicalVouchers.put(name, Map.of("canonical", voucher.canonical(),
                    "sha256", voucher.computeFingerprint()));
        });
        Files.createDirectories(outputRoot);
        String runId = "crppc1-" + UUID.randomUUID();
        Path pending = outputRoot.resolve(runId + ".pending");
        Files.createDirectory(pending);
        List<ProbeRecord> probes = new ArrayList<>();
        Map<String, String> checksums = new LinkedHashMap<>();
        writeJson(pending, "vouchers.json", canonicalVouchers, checksums);
        BudgetWatchdog budget = new BudgetWatchdog();
        TaskLevelRecursivePpc engine = new TaskLevelRecursivePpc(
                new DefaultExperienceLibrary(new FileSystemExperienceStore(pending.resolve("experience"))),
                new PpcStopPolicy(1, 3));
        ChatModelFactory factory = new ChatModelFactory() {
            @Override
            public ChatModel getChatModel(String platform, String baseUrl, String ignoredKey, String modelName) {
                if (!"deepseek".equals(platform) || !MODEL.equals(modelName)) {
                    throw new IllegalArgumentException("controlled run permits only deepseek-flash");
                }
                return model;
            }
        };
        Status status = Status.PRELIVE_COMPLETE;
        String reason = "complete fixed-rubric evaluation";
        Operator selected = null;
        if (live && !connectivity(model, budget)) {
            status = budget.exhausted ? Status.BUDGET_EXHAUSTED : Status.BLOCKED;
            reason = "model connectivity or provider usage is unavailable";
        } else {
            Status bootstrap = schedule(runId, pending, checksums, probes, budget, engine,
                    factory, baseline, ControlledFixture.Split.BOOTSTRAP, BOOTSTRAP_OPERATORS);
            if (bootstrap != null) {
                status = bootstrap;
                reason = "bootstrap probe or evidence gate failed";
            } else {
                selected = selectBootstrap(probes);
                if (selected == null) {
                    status = live ? Status.NO_ADOPTED_EXPERIENCE : Status.PRELIVE_COMPLETE;
                    reason = "no pre-registered bootstrap candidate qualified";
                } else {
                    writeJson(pending, "selection.json", Map.of(
                            "operator", selected.name(),
                            "fingerprint", new tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator()
                                    .mutate(baseline, selected).computeFingerprint(),
                            "bootstrapProbes", probes.size(),
                            "stableImprovements", stableImprovements(probes, selected),
                            "bootstrapCostCny", candidateProbes(probes, selected,
                                    ControlledFixture.Split.BOOTSTRAP).stream().mapToDouble(ProbeRecord::costCny).sum(),
                            "bootstrapTokens", candidateProbes(probes, selected,
                                    ControlledFixture.Split.BOOTSTRAP).stream().mapToLong(ProbeRecord::tokens).sum()), checksums);
                    Status holdout = schedule(runId, pending, checksums, probes, budget, engine,
                            factory, baseline, ControlledFixture.Split.HOLDOUT, List.of(selected));
                    if (holdout != null) {
                        status = holdout;
                        reason = "holdout probe or evidence gate failed";
                    } else if (holdoutPasses(probes, selected)) {
                        status = live ? Status.ADOPTED : Status.PRELIVE_COMPLETE;
                        reason = live ? "one controlled candidate qualified" : "pre-live wiring qualified; no experience written";
                    } else {
                        status = live ? Status.NO_ADOPTED_EXPERIENCE : Status.PRELIVE_COMPLETE;
                        reason = "unique holdout candidate did not qualify";
                    }
                }
            }
        }
        if (budget.unknownUsage) {
            status = Status.BLOCKED;
            reason = "CONTROLLED_USAGE_UNKNOWN";
        }
        if (status == Status.ADOPTED) {
            adopt(runId, pending, checksums, probes, budget, baseline, selected);
        }
        RunResult result = new RunResult(runId, status, reason, selected, probes,
                budget.calls, budget.tokens, budget.unknownUsage ? null : budget.cost,
                outputRoot.resolve(runId));
        writeJson(pending, "run.json", result, checksums);
        StringBuilder manifest = new StringBuilder();
        checksums.forEach((file, hash) -> manifest.append(hash).append("  ").append(file).append('\n'));
        atomicWrite(pending.resolve("checksums.sha256"), manifest.toString());
        try {
            Files.move(pending, result.directory(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException("atomic controlled run publication is required; pending evidence is retained", unsupported);
        }
        return result;
    }

    private Status schedule(String runId, Path pending, Map<String, String> checksums,
                            List<ProbeRecord> probes, BudgetWatchdog budget,
                            TaskLevelRecursivePpc engine, ChatModelFactory factory,
                            HermesHarnessVoucher baseline, ControlledFixture.Split split,
                            List<Operator> operators) throws IOException {
        List<ControlledFixture> tasks = fixtures.stream().filter(task -> task.split() == split)
                .sorted(Comparator.comparing(ControlledFixture::id)).toList();
        for (int repetition = 1; repetition <= 2; repetition++) {
            for (ControlledFixture fixture : tasks) {
                final int currentRepeat = repetition;
                final Status[] abort = {null};
                int before = probes.size();
                try {
                    engine.runControlled(new TaskLevelRecursivePpc.Task(fixture.id(), DOMAIN,
                                    "controlled_harness11b2a"), baseline, operators, (task, voucher) -> {
                        if (abort[0] != null) throw new IllegalStateException("previous controlled probe failed");
                        if (probes.size() >= 50) {
                            abort[0] = Status.BUDGET_EXHAUSTED;
                            throw new IllegalStateException("50-probe ceiling");
                        }
                        String candidate = candidateId(voucher, baseline);
                        ProbeRecord probe = probe(runId, fixture, currentRepeat, candidate,
                                voucher, factory, budget);
                        probes.add(probe);
                        try {
                            writeJson(pending, "probes/" + probe.requestId() + ".json", probe, checksums);
                        } catch (IOException writeFailure) {
                            abort[0] = Status.BLOCKED;
                            throw new IllegalStateException("probe evidence write failed", writeFailure);
                        }
                        if (!probe.valid()) {
                            abort[0] = budget.exhausted ? Status.BUDGET_EXHAUSTED : Status.BLOCKED;
                            throw new IllegalStateException("incomplete controlled probe evidence");
                        }
                        return new Score(probe.qualityPass() ? 1.0D : 0.0D, probe.safe());
                    });
                } catch (RuntimeException failed) {
                    return abort[0] == null ? Status.BLOCKED : abort[0];
                }
                if (abort[0] != null) return abort[0];
                if (probes.size() - before != operators.size() + 1) return Status.BLOCKED;
            }
        }
        return null;
    }

    private ProbeRecord probe(String runId, ControlledFixture fixture, int repetition, String candidate,
                              HermesHarnessVoucher voucher, ChatModelFactory factory, BudgetWatchdog budget) {
        String requestId = runId + "-" + fixture.id() + "-r" + repetition + "-" + candidate;
        List<String> toolInputs = new ArrayList<>();
        ToolCallback callback = fixture.tool() == null ? null : tool(fixture.tool(), toolInputs);
        ToolCallbackResolver resolver = name -> callback != null
                && fixture.tool().name().equals(name) ? callback : null;
        PlanSolveConfig plan = new PlanSolveConfig();
        plan.setEnabled(false);
        plan.setRpReactEnabled(false);
        plan.setMaxReflectionRetries(0);
        AgentOrchestrator agent = new AgentOrchestrator(factory, resolver, null,
                plan, null, null, null, null, null);
        ProbeHook hook = new ProbeHook(fixture, budget);
        agent.registerRequestVoucher(requestId, voucher);
        agent.registerControlledFixtureRequest(requestId);
        agent.registerRequestBudgetHook(requestId, hook);
        ChatRequest.Builder request = ChatRequest.newBuilder().setRequestId(requestId).setBotId(DOMAIN.botId())
                .setQuestion(fixture.question()).setSystemPrompt("")
                .setModelConfig(ModelConfig.newBuilder().setPlatform("deepseek").setModelName(MODEL).build());
        if (fixture.retrieval() != null) {
            request.addRagContexts(RAGContext.newBuilder().setKnowledgeId(String.valueOf(DOMAIN.knowledgeBaseId()))
                    .setKnowledgeName("controlled fixture").setPreRetrievedContent(fixture.retrieval().body()).build());
        }
        if (fixture.tool() != null) request.addToolMethodIds(fixture.tool().name());
        for (ControlledFixture.HistoryTurn turn : fixture.history()) {
            request.addHistory(ChatMessage.newBuilder().setRole(turn.role()).setContent(turn.content()).build());
        }
        List<ChatEvent> events = null;
        try {
            events = agent.chat(request.build()).collectList().block(Duration.ofMinutes(3));
        } catch (RuntimeException failed) {
            // The partially metered attempt remains in the probe ledger.
        } finally {
            agent.unregisterRequestVoucher(requestId);
            agent.unregisterControlledFixtureRequest(requestId);
        }
        HarnessEvidenceRecord evidence = agent.getLatestEvidence(requestId);
        HarnessUsageSnapshot usage = hook.snapshot;
        String answer = events == null ? "" : events.stream().filter(ChatEvent::hasChunk)
                .map(event -> event.getChunk().getText()).reduce("", String::concat);
        if (answer.isBlank() && events != null) {
            answer = events.stream().filter(ChatEvent::hasFinished)
                    .map(event -> event.getFinished().getFullText()).reduce((left, right) -> right).orElse("");
        }
        boolean error = events == null || events.stream().anyMatch(ChatEvent::hasError);
        boolean valid = !error && !answer.isBlank() && evidence != null
                && evidence.executionMode() == HarnessEvidenceRecord.ExecutionMode.CONTROLLED_FIXTURE
                && voucher.matchesFingerprint(evidence.voucherFingerprint()) && usage.isCostKnown()
                && hook.firstPromptChecked && hook.ragConsumed && hook.historyConsumed
                && !budget.exhausted;
        boolean safe = evidence != null && !evidence.guardBlocked()
                && evidence.toolExecutions().stream().allMatch(tool -> tool.success() && !tool.guardBlocked()
                        && (fixture.tool() != null && fixture.tool().name().equals(tool.toolName())
                        || fixture.retrieval() != null
                        && (("knowledgeBase" + DOMAIN.knowledgeBaseId()).equals(tool.toolName())
                        || "knowledgeQuery".equals(tool.toolName()))));
        boolean quality = valid && safe && rubricPasses(fixture, answer)
                && (fixture.retrieval() == null || evidence.ragAttribution().ragUsed() > 0)
                && (fixture.tool() == null || toolInputs.size() == 1
                    && evidence.toolExecutions().stream().anyMatch(tool -> fixture.tool().name().equals(tool.toolName())));
        String actualInput = toolInputs.isEmpty() ? "" : toolInputs.getFirst();
        return new ProbeRecord(requestId, fixture.id(), fixture.split(), fixture.category(), candidate,
                repetition, voucher.computeFingerprint(), Instant.now(),
                fixture.retrievalQuery(), fixture.retrieval() == null ? null : fixture.retrieval().documentId(),
                fixture.retrieval() == null ? null : fixture.retrieval().segmentId(),
                fixture.retrieval() == null ? null : fixture.retrieval().bodySha256(),
                fixture.tool() == null ? null : fixture.tool().resultId(), actualInput,
                fixture.tool() == null ? null : ControlledFixture.sha256(fixture.tool().outputValue()),
                fixture.history().stream().map(ControlledFixture.HistoryTurn::sourceId).toList(),
                hook.ragConsumed, hook.historyConsumed, hook.firstPromptSha256,
                evidence == null ? null : evidence.computeDigest(), evidence,
                evidence == null ? 0 : evidence.ragAttribution().ragUsed(),
                usage.meteredCallCount() + usage.unmeteredCallCount(), usage.totalTokens(),
                usage.isCostKnown() ? usage.actualCostCny() : null, ControlledFixture.sha256(answer),
                answer.length() > 300 ? answer.substring(0, 300) : answer,
                valid, safe, quality);
    }

    private static ToolCallback tool(ControlledFixture.ToolFixture fixture, List<String> toolInputs) {
        ToolDefinition definition = ToolDefinition.builder().name(fixture.name())
                .description(fixture.description()).inputSchema(fixture.inputSchema()).build();
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() { return definition; }
            @Override public String call(String input) {
                toolInputs.add(input);
                if (!JSON.parseObject(fixture.expectedArgs()).equals(JSON.parseObject(input))) {
                    throw new IllegalArgumentException("controlled tool arguments differ from frozen fixture");
                }
                return fixture.outputValue();
            }
        };
    }

    private static boolean rubricPasses(ControlledFixture fixture, String answer) {
        String normalized = normalize(answer);
        return fixture.rubric().answerAllOf().stream().allMatch(term -> normalized.contains(normalize(term)))
                && fixture.rubric().forbid().stream().noneMatch(term -> normalized.contains(normalize(term)));
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ").trim();
    }

    private static Operator selectBootstrap(List<ProbeRecord> probes) {
        return BOOTSTRAP_OPERATORS.stream().filter(operator -> qualifies(probes, operator,
                        ControlledFixture.Split.BOOTSTRAP))
                .sorted(Comparator.comparingInt((Operator operator) -> stableImprovements(probes, operator)).reversed()
                        .thenComparingDouble(operator -> candidateProbes(probes, operator,
                                ControlledFixture.Split.BOOTSTRAP).stream().mapToDouble(ProbeRecord::costCny).sum())
                        .thenComparingLong(operator -> candidateProbes(probes, operator,
                                ControlledFixture.Split.BOOTSTRAP).stream().mapToLong(ProbeRecord::tokens).sum())
                        .thenComparingInt(Enum::ordinal))
                .findFirst().orElse(null);
    }

    private static boolean holdoutPasses(List<ProbeRecord> probes, Operator selected) {
        return qualifies(probes, selected, ControlledFixture.Split.HOLDOUT);
    }

    private static boolean qualifies(List<ProbeRecord> probes, Operator operator, ControlledFixture.Split split) {
        List<ProbeRecord> candidates = candidateProbes(probes, operator, split);
        if (candidates.size() != 10 || candidates.stream().anyMatch(probe -> !probe.valid()
                || !probe.safe() || !probe.qualityPass())) return false;
        for (ProbeRecord candidate : candidates) {
            ProbeRecord baseline = probes.stream().filter(probe -> probe.split() == split
                    && probe.taskId().equals(candidate.taskId()) && probe.repetition() == candidate.repetition()
                    && "baseline".equals(probe.candidateId())).findFirst().orElse(null);
            if (baseline == null || !baseline.valid() || !baseline.safe()
                    || baseline.qualityPass() && !candidate.qualityPass()) return false;
        }
        return split == ControlledFixture.Split.HOLDOUT || stableImprovements(probes, operator) > 0;
    }

    private static int stableImprovements(List<ProbeRecord> probes, Operator operator) {
        return (int) candidateProbes(probes, operator, ControlledFixture.Split.BOOTSTRAP).stream()
                .map(ProbeRecord::taskId).distinct().filter(id -> {
                    for (int repeat = 1; repeat <= 2; repeat++) {
                        final int current = repeat;
                        boolean baselineFailed = probes.stream().anyMatch(probe -> probe.taskId().equals(id)
                                && probe.repetition() == current && "baseline".equals(probe.candidateId())
                                && !probe.qualityPass());
                        boolean candidatePassed = probes.stream().anyMatch(probe -> probe.taskId().equals(id)
                                && probe.repetition() == current && operator.name().equals(probe.candidateId())
                                && probe.qualityPass());
                        if (!baselineFailed || !candidatePassed) return false;
                    }
                    return true;
                }).count();
    }

    private static List<ProbeRecord> candidateProbes(List<ProbeRecord> probes, Operator operator,
                                                      ControlledFixture.Split split) {
        return probes.stream().filter(probe -> probe.split() == split
                && operator.name().equals(probe.candidateId())).toList();
    }

    private void adopt(String runId, Path pending, Map<String, String> checksums,
                       List<ProbeRecord> probes, BudgetWatchdog budget,
                       HermesHarnessVoucher baseline, Operator selected) throws IOException {
        if (selected == null || probes.size() != 50 || budget.exhausted) {
            throw new IllegalStateException("adoption requires fifty complete bounded probes");
        }
        String entryId = "crppc1-" + runId;
        String fingerprint = new tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator()
                .mutate(baseline, selected).computeFingerprint();
        ControlledProvenanceRecord provenance = new ControlledProvenanceRecord(entryId, runId,
                "CONTROLLED_FIXTURE", "VERIFIED", selected, fingerprint, FIXTURE_SHA, VERIFY_SHA,
                probes.stream().map(probe -> ControlledFixture.sha256(JSON.toJSONString(probe))).toList(),
                budget.calls, budget.tokens, budget.cost);
        writeJson(pending, "provenance.json", provenance, checksums);
        String digest = checksums.get("provenance.json");
        ExperienceEntry entry = new ExperienceEntry(entryId, DOMAIN, "controlled_harness11b2a",
                "Accepted only for the frozen controlled fixture: " + selected.name(),
                "Fifty measured controlled probes passed the fixed rubric and safety gates",
                new EvidenceReference(entryId + "-evidence", probes.getFirst().requestId(), digest,
                        TaskLevelRecursivePpc.CONTROLLED_MODE), 1.0D, runId, fingerprint,
                ExperienceDisposition.ADOPTED, false, ExperienceEntry.AttributionStatus.NOT_APPLICABLE,
                1, false, null, Instant.now());
        FileSystemExperienceStore store = new FileSystemExperienceStore(pending.resolve("experience"));
        ExperienceEntry saved = new DefaultExperienceLibrary(store).recordExperience(entry);
        if (!store.find(DOMAIN, entryId).filter(saved::equals).isPresent()) {
            throw new IllegalStateException("controlled adoption could not be read back");
        }
    }

    static HermesHarnessVoucher baseline() {
        HermesHarnessVoucher source = new HermesHarnessAssembler().createDefaultProductionVoucher(DOMAIN.botId());
        return new HermesHarnessVoucher("11b2a-baseline-v1", DOMAIN.botId(), "11b2a-baseline-v1",
                Instant.EPOCH, new InvariantSystemPromptRecord("你是严谨的研究助手。",
                        "优先依据受控证据回答；\n证据不足时明确说明，不编造事实；\n输出结构清晰、可追溯。",
                        source.invariantSystemPrompt().safetyInstructions()), source.guardrails(),
                source.memoryContext(), source.toolGuidance(), source.subagents(), source.skillBank(),
                source.sandboxedExecutionPlan(), source.mcpMiddlewarePlugins());
    }

    private static String candidateId(HermesHarnessVoucher voucher, HermesHarnessVoucher baseline) {
        if (voucher.computeFingerprint().equals(baseline.computeFingerprint())) return "baseline";
        for (Operator operator : BOOTSTRAP_OPERATORS) {
            if (voucher.computeFingerprint().equals(new tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator()
                    .mutate(baseline, operator).computeFingerprint())) return operator.name();
        }
        throw new IllegalArgumentException("unregistered controlled candidate fingerprint");
    }

    private static void validateFixtures(List<ControlledFixture> fixtures) {
        if (fixtures.size() != 10 || fixtures.stream().map(ControlledFixture::id).distinct().count() != 10) {
            throw new IllegalArgumentException("exactly ten unique controlled fixtures are required");
        }
        for (ControlledFixture.Split split : ControlledFixture.Split.values()) {
            List<ControlledFixture> tasks = fixtures.stream().filter(task -> task.split() == split).toList();
            if (tasks.size() != 5 || tasks.stream().map(ControlledFixture::category).distinct().count() != 5) {
                throw new IllegalArgumentException("each split needs one fixture per category");
            }
        }
    }

    private static void verifyFrozenResource(String resource, String expected) throws IOException {
        try (InputStream stream = ControlledPpcRunner.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null || !ControlledFixture.sha256(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .equals(expected)) {
                throw new IOException("frozen controlled fixture checksum mismatch: " + resource);
            }
        }
    }

    private static boolean connectivity(ChatModel model, BudgetWatchdog budget) {
        ProbeHook hook = new ProbeHook(null, budget);
        UsageCapturingChatModel metered = new UsageCapturingChatModel(model, hook);
        try {
            ChatResponse response = metered.call(new Prompt("只回答 OK",
                    DeepSeekChatOptions.builder().model(MODEL).thinkingEnabled(false).maxTokens(16).build()));
            return response != null && response.getResult() != null
                    && !response.getResult().getOutput().getText().isBlank() && hook.snapshot.isCostKnown();
        } catch (RuntimeException failed) {
            return false;
        }
    }

    private static void writeJson(Path root, String relative, Object value,
                                  Map<String, String> checksums) throws IOException {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new IOException("controlled evidence path escaped run directory");
        String json = JSON.toJSONString(value);
        atomicWrite(target, json);
        checksums.put(relative, ControlledFixture.sha256(json));
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException("atomic evidence write is required", unsupported);
        }
    }

    public enum Status { PRELIVE_COMPLETE, BLOCKED, BUDGET_EXHAUSTED, NO_ADOPTED_EXPERIENCE, ADOPTED }

    public record RunResult(String runId, Status status, String reason, Operator selectedOperator,
                            List<ProbeRecord> probes, int modelCalls, long totalTokens,
                            Double costCny, Path directory) {
        public RunResult { probes = List.copyOf(probes); }
    }

    public record ProbeRecord(String requestId, String taskId, ControlledFixture.Split split,
                              ControlledFixture.Category category, String candidateId, int repetition,
                              String voucherFingerprint, Instant timestamp, String retrievalQuery,
                              String documentId, String segmentId, String bodySha256,
                              String toolResultId, String actualToolArgs, String toolOutputSha256,
                              List<String> historySourceIds, boolean ragConsumed, boolean historyConsumed,
                              String firstPromptSha256, String agentEvidenceDigest,
                              HarnessEvidenceRecord agentEvidence, int ragUsed, int modelCalls, long tokens,
                              Double costCny, String answerSha256, String answerSummary,
                              boolean valid, boolean safe, boolean qualityPass) {
        public ProbeRecord { historySourceIds = List.copyOf(historySourceIds); }
    }

    private static final class BudgetWatchdog {
        private final DeepSeekCompatibleChatModel requestSizer = new DeepSeekCompatibleChatModel(
                "https://api.deepseek.com", "", MODEL, null, 2_000, false);
        private int calls;
        private long tokens;
        private double cost;
        private boolean exhausted;
        private boolean unknownUsage;

        private synchronized void before(Prompt prompt) {
            if (unknownUsage) {
                throw new IllegalStateException("CONTROLLED_USAGE_UNKNOWN");
            }
            long inputReserve = JSON.toJSONBytes(requestSizer.buildRequestBody(prompt, true)).length + 4_096L;
            long tokenReserve = inputReserve + 2_000L;
            double costReserve = (inputReserve * 2_000D + 2_000D * 8_000D) / 1_000_000_000D;
            if (exhausted || calls >= 100 || tokens + tokenReserve > 200_000L
                    || cost + costReserve > 10.0D) {
                exhausted = true;
                throw new IllegalStateException("CONTROLLED_BUDGET_EXHAUSTED");
            }
            calls++;
        }

        private synchronized void after(HarnessUsageSnapshot current, HarnessUsageSnapshot previous) {
            if (!current.isCostKnown() || current.unmeteredCallCount() > 0
                    || current.meteredCallCount() != previous.meteredCallCount() + 1) {
                unknownUsage = true;
                return;
            }
            tokens += current.totalTokens() - previous.totalTokens();
            cost += current.actualCostCny() - previous.actualCostCny();
            if (current.completionTokens() - previous.completionTokens() > 2_000L
                    || tokens > 200_000L || cost > 10.0D) exhausted = true;
        }
    }

    private static final class ProbeHook implements UsageCapturingChatModel.ModelCallBudgetHook {
        private final ControlledFixture fixture;
        private final BudgetWatchdog budget;
        private HarnessUsageSnapshot snapshot = HarnessUsageSnapshot.unknown();
        private boolean firstPromptChecked;
        private String firstPromptSha256;
        private boolean ragConsumed;
        private boolean historyConsumed;

        private ProbeHook(ControlledFixture fixture, BudgetWatchdog budget) {
            this.fixture = fixture;
            this.budget = budget;
        }

        @Override
        public void beforeCall(Prompt prompt, HarnessUsageSnapshot current) {
            if (!firstPromptChecked && fixture != null) {
                String joined = prompt.getInstructions().stream()
                        .map(message -> message.getText() == null ? "" : message.getText())
                        .reduce("", (left, right) -> left + "\n" + right);
                firstPromptSha256 = ControlledFixture.sha256(joined);
                ragConsumed = fixture.retrieval() == null || joined.contains(fixture.retrieval().body());
                int position = -1;
                historyConsumed = true;
                for (ControlledFixture.HistoryTurn turn : fixture.history()) {
                    position = joined.indexOf(turn.content(), position + 1);
                    if (position < 0) { historyConsumed = false; break; }
                }
                firstPromptChecked = true;
                if (!ragConsumed || !historyConsumed) {
                    throw new IllegalStateException("controlled context did not reach Agent model input");
                }
            }
            budget.before(prompt);
        }

        @Override
        public void afterCall(HarnessUsageSnapshot current) {
            HarnessUsageSnapshot previous = snapshot;
            snapshot = current;
            budget.after(current, previous);
        }
    }
}
