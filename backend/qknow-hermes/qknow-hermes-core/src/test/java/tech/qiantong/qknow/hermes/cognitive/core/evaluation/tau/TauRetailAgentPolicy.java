package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import com.alibaba.fastjson2.JSON;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.harness.HarnessUsageSnapshot;
import tech.qiantong.qknow.hermes.agent.harness.UsageCapturingChatModel;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.hermes.proto.OrchestrationMode;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public final class TauRetailAgentPolicy implements TauRetailEvaluationRunner.AgentPolicy {
    private static final Pattern FOLLOW_UP = Pattern.compile(
            "(?is)(?:[?？]\\s*$|\\b(?:could you|please provide|provide your|tell me your|what is your|what's your|share your|to proceed.{0,80}need)\\b.*$)");
    private static final int MAX_USER_TURNS = 12;
    private static final BudgetLimits LIVE_BUDGET = new BudgetLimits(8, 18, 60_000, 120_000, 1_024);
    private static final DeepSeekCompatibleChatModel REQUEST_SIZER = new DeepSeekCompatibleChatModel(
            "https://api.deepseek.com", "", "deepseek-flash", null, 1_024);

    private final ChatModelFactory chatModelFactory;
    private final String platform;
    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final SharedBudget budget;
    private volatile RunResult lastRun;

    public TauRetailAgentPolicy(ChatModelFactory chatModelFactory, String platform, String baseUrl,
                                String apiKey, String modelName) {
        this(chatModelFactory, platform, baseUrl, apiKey, modelName, LIVE_BUDGET, false);
    }

    private TauRetailAgentPolicy(ChatModelFactory chatModelFactory, String platform, String baseUrl,
                                 String apiKey, String modelName, BudgetLimits budgetLimits,
                                 boolean requireMeteredUsage) {
        this.chatModelFactory = java.util.Objects.requireNonNull(chatModelFactory, "chatModelFactory");
        this.platform = require(platform, "platform");
        this.baseUrl = require(baseUrl, "baseUrl");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.modelName = require(modelName, "modelName");
        this.budget = new SharedBudget(budgetLimits, requireMeteredUsage);
    }

    public static TauRetailAgentPolicy fromEnvironment(ChatModelFactory factory) {
        return fromEnvironment(factory, LIVE_BUDGET);
    }

    static TauRetailAgentPolicy fromEnvironment(BudgetLimits budgetLimits) {
        ChatModelFactory factory = new ChatModelFactory() {
            @Override
            public org.springframework.ai.chat.model.ChatModel getChatModel(
                    String platform, String baseUrl, String apiKey, String modelName) {
                return new DeepSeekCompatibleChatModel(baseUrl, apiKey, modelName, null,
                        budgetLimits.maxOutputTokensPerCall());
            }
        };
        return fromEnvironment(factory, budgetLimits);
    }

    private static TauRetailAgentPolicy fromEnvironment(ChatModelFactory factory, BudgetLimits budgetLimits) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY is required for tau live smoke");
        }
        String baseUrl = envOrDefault("HERMES_OPENAI_BASE_URL", "https://api.deepseek.com");
        String model = System.getenv("HERMES_DEFAULT_MODEL");
        if (model == null || model.isBlank()) {
            model = envOrDefault("DEEPSEEK_MODEL", "deepseek-flash");
        }
        return new TauRetailAgentPolicy(factory, "DeepSeek", baseUrl, apiKey, model,
                budgetLimits, true);
    }

    public static TauRetailAgentPolicy fromEnvironment() {
        return fromEnvironment(LIVE_BUDGET);
    }

    @Override
    public void execute(String retailPolicy, String publicUserMessage, String strategyGuidance,
                        TauRetailTools tools, TauRetailUserSimulator.Session user) {
        TauRetailToolAdapter adapter = new TauRetailToolAdapter(tools);
        AgentOrchestrator orchestrator = new AgentOrchestrator(chatModelFactory, adapter, null);
        List<RoundResult> rounds = new ArrayList<>();
        List<ChatMessage> history = new ArrayList<>();
        ModelBudget taskBudget = new ModelBudget(budget);
        long promptTokens = 0;
        long completionTokens = 0;
        boolean tokenUsageKnown = true;
        String userMessage = publicUserMessage;
        String failureCode = null;
        FailureDetail failureDetail = null;
        long started = System.nanoTime();

        for (int turn = 1; turn <= MAX_USER_TURNS; turn++) {
            if (budget.exhaustionReason() != null) {
                failureCode = budget.exhaustionReason();
                break;
            }
            if (taskBudget.calls() >= budget.maxCallsPerTask()) {
                failureCode = "MAX_CALLS_PER_TASK";
                break;
            }
            String requestId = "tau-retail-" + UUID.randomUUID();
            UsageHook hook = new UsageHook(taskBudget, budget);
            orchestrator.registerRequestBudgetHook(requestId, hook);
            int firstInvocation = adapter.invocations().size();
            ChatRequest request = request(requestId, userMessage, retailPolicy, strategyGuidance,
                    adapter.toolNames(), history);
            List<ChatEvent> events;
            try {
                events = orchestrator.chat(request).collectList()
                        .block(Duration.ofMillis(budget.remainingMillis()));
            } catch (RuntimeException failure) {
                failureDetail = new FailureDetail("AGENT_CHAT", requestId,
                        failure.getClass().getName(), safeMessage(failure.getMessage()));
                if (budget.exhaustionReason() == null && taskBudget.failureCode() == null) {
                    budget.stop("AGENT_EXCEPTION");
                }
                failureCode = budget.exhaustionReason() == null
                        ? taskBudget.failureCode() : budget.exhaustionReason();
                promptTokens += hook.promptTokens();
                completionTokens += hook.completionTokens();
                tokenUsageKnown &= hook.usageKnown();
                List<TauRetailToolAdapter.ToolInvocation> allCalls = adapter.invocations();
                rounds.add(new RoundResult(turn, userMessage, "",
                        allCalls.subList(firstInvocation, allCalls.size()), hook.promptTokens(),
                        hook.completionTokens()));
                break;
            }
            promptTokens += hook.promptTokens();
            completionTokens += hook.completionTokens();
            tokenUsageKnown &= hook.usageKnown();
            if (events == null) {
                failureCode = "EMPTY_AGENT_EVENTS";
                break;
            }

            ChatEvent errorEvent = events.stream().filter(ChatEvent::hasError).findFirst().orElse(null);
            String answer = events.stream().filter(ChatEvent::hasFinished)
                    .map(event -> event.getFinished().getFullText()).reduce((first, next) -> next).orElse("");
            List<TauRetailToolAdapter.ToolInvocation> allCalls = adapter.invocations();
            List<TauRetailToolAdapter.ToolInvocation> calls = allCalls.subList(firstInvocation, allCalls.size());
            rounds.add(new RoundResult(turn, userMessage, answer, calls, hook.promptTokens(),
                    hook.completionTokens()));
            if (budget.exhaustionReason() != null) {
                failureCode = budget.exhaustionReason();
                break;
            }
            if (errorEvent != null) {
                failureCode = "AGENT_ERROR_" + errorEvent.getError().getCode();
                failureDetail = new FailureDetail("AGENT_EVENT", requestId,
                        "AgentError", safeMessage(errorEvent.getError().getMessage()));
                break;
            }
            if (answer.isBlank()) {
                failureCode = "EMPTY_AGENT_RESPONSE";
                break;
            }
            if (!FOLLOW_UP.matcher(answer).find()) {
                break;
            }
            if (turn == MAX_USER_TURNS) {
                failureCode = "MAX_USER_TURNS_EXCEEDED";
                break;
            }
            history.add(ChatMessage.newBuilder().setRole("user").setContent(userMessage).build());
            history.add(ChatMessage.newBuilder().setRole("assistant").setContent(answer).build());
            userMessage = user.answer(answer);
        }

        lastRun = new RunResult(rounds, taskBudget.calls(), promptTokens, completionTokens,
                tokenUsageKnown && taskBudget.calls() > 0,
                (System.nanoTime() - started) / 1_000_000.0,
                failureCode == null ? budget.exhaustionReason() : failureCode, adapter.toolNames(),
                adapter.invocations(), failureDetail);
    }

    public RunResult lastRun() {
        return lastRun;
    }

    public String modelName() {
        return modelName;
    }

    public BudgetSnapshot budgetSnapshot() {
        return budget.snapshot();
    }

    public static BudgetLimits liveBudgetLimits() {
        return LIVE_BUDGET;
    }

    private ChatRequest request(String requestId, String question, String retailPolicy, String strategyGuidance,
                                List<String> toolNames, List<ChatMessage> history) {
        StringBuilder system = new StringBuilder("You are a retail customer-service agent. The customer's request is authorization to perform the requested action. Do not ask for confirmation again for a requested cancellation, exchange, return, or account update. Ask only for information strictly required to complete the request. Use the available tools, inspect their results, and after every successful mutation read the affected state back and verify it before replying. If a required detail is missing or a tool fails, explain what is needed or what failed. Make one tool call at a time.\n\n")
                .append(retailPolicy == null ? "" : retailPolicy);
        if (strategyGuidance != null && !strategyGuidance.isBlank()) {
            system.append("\n\nStrategy guidance:\n").append(strategyGuidance);
        }
        return ChatRequest.newBuilder().setRequestId(requestId).setBotId(1L)
                .setQuestion(question).setSystemPrompt(system.toString())
                .addAllToolMethodIds(toolNames).addAllHistory(history)
                .setOrchestrationMode(OrchestrationMode.ORCHESTRATION_MODE_SINGLE)
                .setModelConfig(ModelConfig.newBuilder().setPlatform(platform).setBaseUrl(baseUrl)
                        .setApiKey(apiKey).setModelName(modelName).build())
                .build();
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) return "";
        return apiKey.isBlank() ? message : message.replace(apiKey, "[REDACTED]");
    }

    public record RoundResult(int turn, String userMessage, String assistantMessage,
                              List<TauRetailToolAdapter.ToolInvocation> toolCalls,
                              long promptTokens, long completionTokens) {
        public RoundResult {
            toolCalls = List.copyOf(toolCalls);
        }
    }

    public record RunResult(List<RoundResult> rounds, int modelCalls, long promptTokens,
                            long completionTokens, boolean tokenUsageKnown, double latencyMs, String failureCode,
                            List<String> agentVisibleTools,
                            List<TauRetailToolAdapter.ToolInvocation> toolCalls,
                            FailureDetail failureDetail) {
        public RunResult {
            rounds = List.copyOf(rounds);
            agentVisibleTools = List.copyOf(agentVisibleTools);
            toolCalls = List.copyOf(toolCalls);
        }

        public long totalTokens() {
            return promptTokens + completionTokens;
        }
    }

    public record FailureDetail(String stage, String requestId, String exceptionType, String message) { }

    public record BudgetLimits(int maxCallsPerTask, int maxTotalCalls, long maxTotalTokens,
                               long maxWallClockMs, int maxOutputTokensPerCall) {
        public BudgetLimits {
            if (maxCallsPerTask < 1 || maxTotalCalls < 1 || maxTotalTokens < 1
                    || maxWallClockMs < 1 || maxOutputTokensPerCall < 1) {
                throw new IllegalArgumentException("tau smoke budgets must be positive");
            }
        }
    }

    public record BudgetSnapshot(int modelCalls, long observedTokens, boolean tokenUsageKnown,
                                 long elapsedMs, String stoppedReason, BudgetLimits limits) { }

    static final class SharedBudget {
        private final BudgetLimits limits;
        private final boolean requireMeteredUsage;
        private final long startedNanos = System.nanoTime();
        private int modelCalls;
        private long observedTokens;
        private long reservedTokens;
        private boolean tokenUsageKnown = true;
        private String stoppedReason;

        SharedBudget(BudgetLimits limits, boolean requireMeteredUsage) {
            this.limits = java.util.Objects.requireNonNull(limits, "limits");
            this.requireMeteredUsage = requireMeteredUsage;
        }

        synchronized boolean reserveCall(int taskCalls, long tokenReservation) {
            refreshWallClock();
            if (stoppedReason != null) {
                return false;
            }
            if (taskCalls >= limits.maxCallsPerTask()) {
                return false;
            } else if (modelCalls >= limits.maxTotalCalls()) {
                stop("MAX_TOTAL_CALLS");
            } else if (observedTokens + reservedTokens + tokenReservation > limits.maxTotalTokens()) {
                stop("MAX_TOTAL_TOKENS");
            } else {
                modelCalls++;
                reservedTokens += tokenReservation;
                return true;
            }
            return false;
        }

        synchronized void recordUsage(long tokenDelta, boolean known, long tokenReservation) {
            refreshWallClock();
            reservedTokens = Math.max(0L, reservedTokens - tokenReservation);
            if (!known) {
                tokenUsageKnown = false;
                if (requireMeteredUsage) {
                    stop("TOKEN_USAGE_UNKNOWN");
                }
                return;
            }
            observedTokens += Math.max(0L, tokenDelta);
            if (observedTokens + reservedTokens >= limits.maxTotalTokens()) {
                stop("MAX_TOTAL_TOKENS");
            }
        }

        synchronized String exhaustionReason() {
            refreshWallClock();
            return stoppedReason;
        }

        synchronized long remainingMillis() {
            refreshWallClock();
            return Math.max(1L, limits.maxWallClockMs() - elapsedMs());
        }

        synchronized void stop(String reason) {
            if (stoppedReason == null) {
                stoppedReason = reason;
            }
        }

        int maxCallsPerTask() {
            return limits.maxCallsPerTask();
        }

        BudgetLimits limits() {
            return limits;
        }

        synchronized BudgetSnapshot snapshot() {
            refreshWallClock();
            return new BudgetSnapshot(modelCalls, observedTokens, tokenUsageKnown, elapsedMs(),
                    stoppedReason, limits);
        }

        private void refreshWallClock() {
            if (stoppedReason == null && elapsedMs() >= limits.maxWallClockMs()) {
                stop("MAX_WALL_CLOCK");
            }
        }

        private long elapsedMs() {
            return (System.nanoTime() - startedNanos) / 1_000_000L;
        }
    }

    private static final class ModelBudget {
        private final SharedBudget shared;
        private final AtomicInteger calls = new AtomicInteger();
        private volatile String failureCode;

        private ModelBudget(SharedBudget shared) {
            this.shared = shared;
        }

        private boolean tryCall(long tokenReservation) {
            if (!shared.reserveCall(calls.get(), tokenReservation)) {
                if (calls.get() >= shared.maxCallsPerTask()) {
                    failureCode = "MAX_CALLS_PER_TASK";
                }
                return false;
            }
            calls.incrementAndGet();
            return true;
        }

        private int calls() {
            return calls.get();
        }

        private String failureCode() {
            return failureCode;
        }

    }

    private static final class UsageHook implements UsageCapturingChatModel.ModelCallBudgetHook {
        private final ModelBudget budget;
        private final SharedBudget sharedBudget;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicLong promptTokens = new AtomicLong();
        private final AtomicLong completionTokens = new AtomicLong();
        private final AtomicLong observedTotalTokens = new AtomicLong();
        private final AtomicLong reservedTokens = new AtomicLong();
        private final AtomicBoolean allCallsMetered = new AtomicBoolean(true);

        private UsageHook(ModelBudget budget, SharedBudget sharedBudget) {
            this.budget = budget;
            this.sharedBudget = sharedBudget;
        }

        @Override
        public void beforeCall(org.springframework.ai.chat.prompt.Prompt prompt, HarnessUsageSnapshot snapshot) {
            long reservation = JSON.toJSONBytes(REQUEST_SIZER.buildRequestBody(prompt, true)).length
                    + 4_096L + sharedBudget.limits().maxOutputTokensPerCall();
            if (!budget.tryCall(reservation)) {
                throw new IllegalStateException("BUDGET_EXHAUSTED");
            }
            reservedTokens.set(reservation);
            calls.incrementAndGet();
        }

        @Override
        public void afterCall(HarnessUsageSnapshot snapshot) {
            promptTokens.set(Math.max(promptTokens.get(), snapshot.promptTokens()));
            completionTokens.set(Math.max(completionTokens.get(), snapshot.completionTokens()));
            long total = Math.max(observedTotalTokens.get(), snapshot.totalTokens());
            long previous = observedTotalTokens.getAndSet(total);
            sharedBudget.recordUsage(total - previous, snapshot.isKnown(), reservedTokens.getAndSet(0L));
            if (!snapshot.isKnown()) {
                allCallsMetered.set(false);
            }
        }

        private boolean usageKnown() {
            return calls.get() > 0 && allCallsMetered.get();
        }

        private long promptTokens() {
            return promptTokens.get();
        }

        private long completionTokens() {
            return completionTokens.get();
        }
    }
}
