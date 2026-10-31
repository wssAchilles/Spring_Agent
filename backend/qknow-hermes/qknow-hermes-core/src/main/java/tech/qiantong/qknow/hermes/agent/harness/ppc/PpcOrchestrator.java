package tech.qiantong.qknow.hermes.agent.harness.ppc;

import com.alibaba.fastjson2.JSON;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;
import tech.qiantong.qknow.hermes.agent.harness.GuardrailsSpecificationRecord;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HarnessUsageSnapshot;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.InvariantSystemPromptRecord;
import tech.qiantong.qknow.hermes.agent.harness.RagAttributionSummary;
import tech.qiantong.qknow.hermes.agent.harness.UsageCapturingChatModel;
import tech.qiantong.qknow.hermes.agent.harness.UsageStatus;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;
import tech.qiantong.qknow.hermes.agent.harness.kernel.HarnessKernelBridge;
import tech.qiantong.qknow.hermes.agent.harness.kernel.HermesHarnessKernel;
import tech.qiantong.qknow.hermes.agent.harness.kernel.KernelBridgeMode;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.config.PlanSolveConfig;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.hermes.proto.RAGContext;

/** One bounded Propose-Probe-Compose round over the production chat path. */
public final class PpcOrchestrator {
    public static final PpcRun.Budget DEFAULT_BUDGET = new PpcRun.Budget(20, 60, 200_000L, 2_000, 10.0D);
    private static final String MODEL = "deepseek-flash";
    private static final long BOT_ID = 9L;

    private final AgentOrchestrator agent;
    private final HarnessKernelBridge bridge;
    private final PpcComposer composer = new PpcComposer();
    private final Path outputRoot;
    private final boolean deterministicTestDouble;
    private final PpcRun.Budget budget;

    public PpcOrchestrator(ChatModelFactory factory, Path outputRoot) {
        this(factory, outputRoot, false, DEFAULT_BUDGET);
    }

    // Explicit test mode cannot produce a REAL_PPC verdict.
    PpcOrchestrator(ChatModelFactory factory, Path outputRoot, boolean deterministicTestDouble, PpcRun.Budget budget) {
        this.outputRoot = outputRoot.toAbsolutePath().normalize();
        if (!this.outputRoot.endsWith(Path.of("experiment/outputs/harness9-live"))) {
            throw new IllegalArgumentException("Stage 9 output must be isolated under harness9-live");
        }
        this.deterministicTestDouble = deterministicTestDouble;
        this.budget = budget;
        Map<String, ToolCallback> tools = Map.of(
                "banking_transaction_query", FunctionToolCallback.builder("banking_transaction_query",
                        (TransactionInput input) -> "TX-20261029-8891 SUCCESS ACC-9901 5800.00")
                        .description("查询交易流水扣款状态和关联账户").inputType(TransactionInput.class).build(),
                "banking_account_balance", FunctionToolCallback.builder("banking_account_balance",
                        (AccountInput input) -> "ACC-9901 balance 5800.00")
                        .description("查询账户余额").inputType(AccountInput.class).build(),
                "arch_metrics_calculator", FunctionToolCallback.builder("arch_metrics_calculator",
                        (MetricsInput input) -> "Monolith P99 12ms CPU 25%; Sidecar Mesh P99 17ms CPU 38%; eBPF Mesh P99 13ms CPU 29%")
                        .description("查询当前集群架构延迟和 CPU 基线").inputType(MetricsInput.class).build());
        ToolCallbackResolver resolver = tools::get;
        PlanSolveConfig plan = new PlanSolveConfig();
        plan.setEnabled(false);
        plan.setRpReactEnabled(false);
        plan.setMaxReflectionRetries(0);
        this.agent = new AgentOrchestrator(factory, resolver, null, plan, null, null, null, null, null);
        this.bridge = new HarnessKernelBridge(new HermesHarnessKernel(8, 4, 8, 8, 3),
                new HermesHarnessAssembler(), true, KernelBridgeMode.ENFORCE);
        agent.setHarnessKernelBridge(bridge);
    }

    public PpcRun run(String apiKey) throws IOException {
        return execute(apiKey, false);
    }

    PpcRun runTestDouble() throws IOException {
        if (!deterministicTestDouble) throw new IllegalStateException("Test-double mode is not enabled");
        return execute(null, true);
    }

    private PpcRun execute(String apiKey, boolean testDouble) throws IOException {
        String runId = UUID.randomUUID().toString();
        Path directory = outputRoot.resolve(runId);
        Files.createDirectories(directory.resolve("probes"));
        List<PpcCandidate> candidates = new ArrayList<>(composer.createCandidates(baseline()));
        List<PpcProbeResult> probes = new ArrayList<>();
        Map<String, String> checksums = new LinkedHashMap<>();
        BudgetWatchdog watchdog = new BudgetWatchdog();
        PpcMode mode = PpcMode.EMPIRICAL_BLOCKED;
        PpcComposer.SelectionResult selection = null;
        if (testDouble || validKey(apiKey)) {
            mode = PpcMode.REAL_PPC;
            outer: for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                PpcCandidate candidate = candidates.get(candidateIndex);
                for (BenchmarkCase fixture : fixtures()) {
                    if (watchdog.exhausted || probes.size() >= budget.maxProbes()) {
                        mode = PpcMode.BUDGET_EXHAUSTED;
                        break outer;
                    }
                    PpcProbeResult probe = probe(runId, candidate, fixture, watchdog, testDouble);
                    if (probe == null || probe.usageStatus() != UsageStatus.KNOWN || !probe.costKnown()) {
                        mode = watchdog.exhausted ? PpcMode.BUDGET_EXHAUSTED : PpcMode.EMPIRICAL_BLOCKED;
                        break outer;
                    }
                    probes.add(probe);
                    writeJson(directory, "probes/" + candidate.candidateId() + "-" + fixture.id + ".json",
                            probe, checksums);
                    if (watchdog.exhausted) {
                        mode = PpcMode.BUDGET_EXHAUSTED;
                        break outer;
                    }
                }
                // Project five extra calls at the largest observed per-probe cost before unlocking C.
                double projectedCost = watchdog.cost + 5 * probes.stream()
                        .mapToDouble(PpcProbeResult::actualCostCny).max().orElse(0.0D);
                if ("candidate-b".equals(candidate.candidateId()) && probes.size() == 15
                        && projectedCost < 5.0D && noQualityCollapse(probes)) {
                    candidates.add(composer.createCombinedCandidate(candidates.get(1), candidates.get(2)));
                }
            }
            if (mode == PpcMode.REAL_PPC && !testDouble) {
                selection = composer.select(runId, candidates, probes, allQrels());
            }
        }
        if (testDouble && mode == PpcMode.REAL_PPC) mode = PpcMode.EMPIRICAL_BLOCKED;
        List<PpcCandidate> measured = candidates.stream().map(candidate -> candidate.withResults(probes)).toList();
        String selected = selection == null ? "baseline" : selection.selectedCandidateId();
        PpcRun run = new PpcRun(runId, mode, budget, probes.size(), watchdog.calls, watchdog.tokens,
                watchdog.cost, (int) probes.stream().filter(p -> p.effectiveCapabilityTag() == CapabilityTag.PROMPT_ONLY
                        && !"param-guard-03".equals(p.caseId())).count(), measured, probes,
                selection == null ? List.of() : selection.audits(), selected,
                selection == null ? mode.name() : selection.finalVerdict(), checksums);
        writeJson(directory, "ppc_summary.json", run, checksums);
        writeJson(directory, "selected_candidate.json", Map.of("selectedCandidateId", selected,
                "reason", run.finalVerdict(), "voucher", measured.stream()
                        .filter(c -> c.candidateId().equals(selected)).findFirst().orElseThrow().voucher()), checksums);
        StringBuilder manifest = new StringBuilder();
        checksums.forEach((file, hash) -> manifest.append(hash).append("  ").append(file).append('\n'));
        atomicWrite(directory.resolve("checksums.sha256"), manifest.toString());
        return new PpcRun(run.runId(), run.mode(), run.budget(), run.totalProbesExecuted(),
                run.totalModelCallsExecuted(), run.totalTokensConsumed(), run.totalCostCnyConsumed(),
                run.promptOnlyDowngradeCount(), run.candidates(), run.probes(), run.positionSwapAudits(),
                run.selectedCandidateId(), run.finalVerdict(), checksums);
    }

    private PpcProbeResult probe(String runId, PpcCandidate candidate, BenchmarkCase fixture,
                                 BudgetWatchdog watchdog, boolean testDouble) {
        String requestId = runId + "-" + candidate.candidateId() + "-" + fixture.id;
        ChatRequest.Builder request = ChatRequest.newBuilder().setRequestId(requestId).setBotId(BOT_ID)
                .setQuestion(fixture.question).setSystemPrompt("")
                .setInputParams("{\"harness.capabilityTag\":\"" + fixture.tag + "\"}")
                .setModelConfig(ModelConfig.newBuilder().setPlatform("deepseek").setModelName(MODEL).build())
                .addAllRagContexts(fixture.ragContexts).addAllToolMethodIds(fixture.toolIds)
                .addAllHistory(fixture.history);
        agent.registerRequestVoucher(requestId, candidate.voucher());
        ProbeHook hook = new ProbeHook(watchdog);
        agent.registerRequestBudgetHook(requestId, hook);
        long started = System.nanoTime();
        List<ChatEvent> events;
        try {
            events = agent.chat(request.build()).collectList().block(Duration.ofMinutes(3));
        } catch (RuntimeException failure) {
            return null;
        } finally {
            agent.unregisterRequestVoucher(requestId);
        }
        HarnessEvidenceRecord evidence = agent.getLatestEvidence(requestId);
        if (evidence == null) return null;
        HarnessUsageSnapshot usage = hook.snapshot;
        var receipt = awaitReceipt(requestId);
        String chunks = events.stream().filter(ChatEvent::hasChunk)
                .map(e -> e.getChunk().getText()).reduce("", String::concat);
        String answer = chunks.isBlank() ? events.stream().filter(ChatEvent::hasFinished)
                .map(e -> e.getFinished().getFullText()).reduce((left, right) -> right).orElse("") : chunks;
        boolean error = events.stream().anyMatch(ChatEvent::hasError);
        boolean guarded = receipt != null && receipt.blockedToolInvocationCount() > 0 || evidence.guardBlocked();
        boolean cycle = receipt != null && receipt.guardViolations().contains("CYCLE");
        boolean quality = !error && !answer.isBlank() && quality(fixture, answer, evidence, receipt);
        return new PpcProbeResult(candidate.candidateId(), fixture.id,
                receipt == null ? CapabilityTag.PROMPT_ONLY : receipt.capabilityTag(), Instant.now(),
                UsageCapturingChatModel.isPeakBeijingTime(Instant.now()),
                testDouble ? HarnessEvidenceRecord.ExecutionMode.DETERMINISTIC_TEST_DOUBLE : evidence.executionMode(),
                usage.usageStatus(), usage.isCostKnown(), usage.promptTokens(), usage.completionTokens(),
                usage.totalTokens(), usage.cacheHitTokens(), usage.reasoningTokens(), usage.actualCostCny(),
                usage.meteredCallCount(), Math.max(1L, (System.nanoTime() - started) / 1_000_000L),
                quality, guarded, cycle, fixture.chunkIds, evidence.ragAttribution(), false,
                testDouble ? testEvidence(evidence) : evidence);
    }

    private boolean quality(BenchmarkCase fixture, String answer, HarnessEvidenceRecord evidence,
                            HarnessKernelBridge.KernelBridgeReceipt receipt) {
        if (receipt == null || receipt.failClosed() || receipt.capabilityTag() != fixture.tag) return false;
        String lower = answer.toLowerCase(java.util.Locale.ROOT);
        return switch (fixture.tag) {
            case RAG -> evidence.ragAttribution().ragUsed() > 0
                    && lower.contains("3") && (lower.contains("幂等") || lower.contains("sha-256"));
            case TOOL -> toolExecuted(evidence, "banking_transaction_query")
                    && !evidence.guardBlocked() && (lower.contains("acc-9901") || lower.contains("5800"));
            case PROMPT_ONLY -> lower.contains("25:99:99") || lower.contains("越界")
                    || lower.contains("iso-8601") || lower.contains("无效");
            case HISTORY -> receipt.coldBufferSnapshot() != null && receipt.coldBufferSnapshot().hot() >= 4
                    && (lower.contains("上下文切换") || lower.contains("延迟"))
                    && (lower.contains("ebpf") || lower.contains("sockmap")
                    || lower.contains("ambient") || lower.contains("旁路"));
            case COMPOSITE -> evidence.ragAttribution().ragUsed() > 0
                    && toolExecuted(evidence, "arch_metrics_calculator")
                    && lower.contains("sidecar") && lower.contains("ebpf")
                    && (lower.contains("单体") || lower.contains("monolith"));
        };
    }

    private static boolean toolExecuted(HarnessEvidenceRecord evidence, String name) {
        return evidence.toolExecutions().stream().anyMatch(t -> name.equals(t.toolName()) && t.success());
    }

    private HarnessKernelBridge.KernelBridgeReceipt awaitReceipt(String requestId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (System.nanoTime() < deadline) {
            var receipt = bridge.getLatestReceipt(requestId);
            if (receipt.isPresent()) return receipt.get();
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static HarnessEvidenceRecord testEvidence(HarnessEvidenceRecord evidence) {
        return new HarnessEvidenceRecord(evidence.evidenceId(), evidence.requestId(), evidence.botId(),
                evidence.voucherFingerprint(), evidence.voucherVersion(), evidence.timestamp(),
                evidence.userInputSummary(), evidence.ragDocumentCount(), evidence.ragTopScore(),
                evidence.toolExecutions(), evidence.estimatedTokens(), evidence.estimatedCostCny(),
                evidence.executionDurationMs(), evidence.finalOutputSummary(),
                HarnessEvidenceRecord.ExecutionMode.DETERMINISTIC_TEST_DOUBLE, evidence.usageStatus(),
                evidence.capabilityTag(), evidence.guardBlocked(), evidence.guardBlockedCount(),
                evidence.ragAttribution());
    }

    private static boolean noQualityCollapse(List<PpcProbeResult> probes) {
        return probes.stream().filter(p -> !"baseline".equals(p.candidateId()))
                .allMatch(p -> p.qualityPass() && !p.guardBlocked() && !p.cycleDetected());
    }

    private static boolean validKey(String key) {
        if (key == null || key.length() < 16) return false;
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        return !lower.contains("placeholder") && !lower.contains("your_key") && !lower.contains("dummy");
    }

    private static HermesHarnessVoucher baseline() {
        HermesHarnessVoucher source = new HermesHarnessAssembler().createDefaultProductionVoucher(BOT_ID);
        return new HermesHarnessVoucher("stage9-baseline", BOT_ID, "stage9", Instant.now(),
                new InvariantSystemPromptRecord("你是严谨的研究助手，请逐步分析，并给出清晰的结论。",
                        "优先依据授权上下文和工具输出，详细解释关键依据。   避免空泛铺垫，且请勿重复总结。",
                        source.invariantSystemPrompt().safetyInstructions()),
                new GuardrailsSpecificationRecord(3, 2, 200_000L, 10.0D), source.memoryContext(),
                source.toolGuidance(), source.subagents(), source.skillBank(), source.sandboxedExecutionPlan(),
                source.mcpMiddlewarePlugins());
    }

    private static List<BenchmarkCase> fixtures() {
        return List.of(
                new BenchmarkCase("kb-qa-01", CapabilityTag.RAG,
                        "请根据《企业订单支付系统中间件技术规范》，说明订单支付网关超时重试的最大次数、退避间隔，以及幂等键（Idempotency-Key）的标准生成规则。",
                        List.of(RAGContext.newBuilder().setKnowledgeId("_MiddleWare_Spec")
                                .setKnowledgeName("企业订单支付系统中间件技术规范")
                                .setPreRetrievedContent("网关超时最多重试 3 次，退避间隔依次为 1000、2000、4000ms。幂等键 Idempotency-Key 由订单号、商户号与支付请求流水拼接后生成 SHA-256 摘要。")
                                .build()), List.of(), List.of(), List.of("chunk-pay-idem-01")),
                new BenchmarkCase("tool-route-02", CapabilityTag.TOOL,
                        "客户反馈交易流水 TX-20261029-8891 已扣款但未生成凭证，请调用合适的金融查询工具核查该流水的扣款状态与关联账户号，并给出核查结论。",
                        List.of(), List.of("banking_transaction_query", "banking_account_balance"), List.of(), List.of()),
                new BenchmarkCase("param-guard-03", CapabilityTag.PROMPT_ONLY,
                        "用户请求创建日程：{\"title\":\"架构评审会\",\"startTime\":\"2026/10/29 25:99:99\"}。请依据时间格式公理检查参数合法性，指出具体错误并给出修正建议。",
                        List.of(), List.of(), List.of(), List.of()),
                new BenchmarkCase("ctx-history-04", CapabilityTag.HISTORY,
                        "针对前文讨论的方案 A（Envoy Sidecar 服务网格）最担忧的性能瓶颈，网络数据面有哪些绕过优化方案？",
                        List.of(), List.of(), List.of(
                                message("user", "方案 A 使用 Envoy Sidecar 服务网格，P99 性能如何？"),
                                message("assistant", "主要瓶颈是内核态/用户态上下文切换和两次 socket 转发，P99 增加约 3.5~5ms。"),
                                message("user", "这会影响高频交易吗？"),
                                message("assistant", "会，额外上下文切换和双 socket 转发增加尾延迟。")), List.of()),
                new BenchmarkCase("comp-rag-tool-05", CapabilityTag.COMPOSITE,
                        "请结合《微服务架构演进评估标准》并调用 arch_metrics_calculator 查询集群基线指标，对比单体架构、Sidecar Mesh 与 eBPF 加速网格的延迟和资源开销。",
                        List.of(RAGContext.newBuilder().setKnowledgeId("arch-standard")
                                .setKnowledgeName("微服务架构演进评估标准")
                                .setPreRetrievedContent("比较单体架构、Sidecar Mesh、eBPF 加速网格时，须同时报告 P99 延迟和 CPU 占用；eBPF 可旁路 Sidecar 用户态转发。")
                                .build()), List.of("arch_metrics_calculator"), List.of(),
                        List.of("chunk-arch-ebpf-01")));
    }

    private static ChatMessage message(String role, String content) {
        return ChatMessage.newBuilder().setRole(role).setContent(content).build();
    }

    private static List<String> allQrels() {
        return List.of("25:99:99", "ACC-9901", "5800.00", "chunk-pay-idem-01", "chunk-arch-ebpf-01");
    }

    private static void writeJson(Path directory, String relative, Object value,
                                  Map<String, String> checksums) throws IOException {
        Path target = directory.resolve(relative).normalize();
        if (!target.startsWith(directory)) throw new IOException("Evidence path escaped run directory");
        String json = JSON.toJSONString(value);
        atomicWrite(target, json);
        checksums.put(relative, sha256(json));
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException("Atomic evidence move is required", unsupported);
        }
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record BenchmarkCase(String id, CapabilityTag tag, String question, List<RAGContext> ragContexts,
                                 List<String> toolIds, List<ChatMessage> history, List<String> chunkIds) {}
    private record TransactionInput(String transactionId) {}
    private record AccountInput(String accountId) {}
    private record MetricsInput(String clusterId) {}

    private final class BudgetWatchdog {
        private final DeepSeekCompatibleChatModel requestSizer = new DeepSeekCompatibleChatModel(
                "https://api.deepseek.com", "", MODEL, null, 1024);
        private int calls;
        private long tokens;
        private double cost;
        private boolean exhausted;

        private synchronized void before(Prompt prompt) {
            long reservedInput = JSON.toJSONBytes(requestSizer.buildRequestBody(prompt, true)).length + 4_096L;
            long reservedTokens = reservedInput + budget.maxOutputTokensPerCall();
            double reservedCost = (reservedInput * 2_000D + budget.maxOutputTokensPerCall() * 8_000D)
                    / 1_000_000_000D;
            if (exhausted || calls >= budget.maxModelCalls() || tokens >= budget.maxTotalTokens()
                    || cost >= budget.maxCostCny() || tokens + reservedTokens > budget.maxTotalTokens()
                    || cost + reservedCost > budget.maxCostCny()) {
                exhausted = true;
                throw new BudgetReached();
            }
            calls++;
        }

        private synchronized void after(HarnessUsageSnapshot current, HarnessUsageSnapshot previous) {
            tokens += Math.max(0L, current.totalTokens() - previous.totalTokens());
            cost += Math.max(0.0D, current.actualCostCny() - previous.actualCostCny());
            if (current.completionTokens() - previous.completionTokens() >= budget.maxOutputTokensPerCall()
                    || calls >= budget.maxModelCalls() || tokens >= budget.maxTotalTokens()
                    || cost >= budget.maxCostCny()) exhausted = true;
        }
    }

    private final class ProbeHook implements UsageCapturingChatModel.ModelCallBudgetHook {
        private final BudgetWatchdog watchdog;
        private HarnessUsageSnapshot snapshot = HarnessUsageSnapshot.unknown();

        private ProbeHook(BudgetWatchdog watchdog) { this.watchdog = watchdog; }

        @Override
        public void beforeCall(Prompt prompt, HarnessUsageSnapshot current) {
            watchdog.before(prompt);
        }

        @Override
        public void afterCall(HarnessUsageSnapshot current) {
            HarnessUsageSnapshot previous = snapshot;
            snapshot = current;
            watchdog.after(current, previous);
        }
    }

    private static final class BudgetReached extends RuntimeException {
        private BudgetReached() { super("STAGE9_BUDGET_EXHAUSTED"); }
    }
}
