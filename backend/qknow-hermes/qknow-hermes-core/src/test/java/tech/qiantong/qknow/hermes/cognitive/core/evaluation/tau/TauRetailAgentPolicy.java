package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.harness.HarnessUsageSnapshot;
import tech.qiantong.qknow.hermes.agent.harness.UsageCapturingChatModel;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.hermes.proto.OrchestrationMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public final class TauRetailAgentPolicy implements TauRetailEvaluationRunner.AgentPolicy {
    private static final Pattern FOLLOW_UP = Pattern.compile("[?？]\\s*$");
    private static final int MAX_USER_TURNS = 12;
    private static final int MAX_MODEL_CALLS = 20;

    private final ChatModelFactory chatModelFactory;
    private final String platform;
    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private volatile RunResult lastRun;

    public TauRetailAgentPolicy(ChatModelFactory chatModelFactory, String platform, String baseUrl,
                                String apiKey, String modelName) {
        this.chatModelFactory = java.util.Objects.requireNonNull(chatModelFactory, "chatModelFactory");
        this.platform = require(platform, "platform");
        this.baseUrl = require(baseUrl, "baseUrl");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.modelName = require(modelName, "modelName");
    }

    public static TauRetailAgentPolicy fromEnvironment(ChatModelFactory factory) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY is required for tau live smoke");
        }
        String baseUrl = envOrDefault("HERMES_OPENAI_BASE_URL", "https://api.deepseek.com");
        String model = System.getenv("HERMES_DEFAULT_MODEL");
        if (model == null || model.isBlank()) {
            model = envOrDefault("DEEPSEEK_MODEL", "deepseek-flash");
        }
        return new TauRetailAgentPolicy(factory, "DeepSeek", baseUrl, apiKey, model);
    }

    @Override
    public void execute(String retailPolicy, String publicUserMessage, String strategyGuidance,
                        TauRetailTools tools, TauRetailUserSimulator.Session user) {
        TauRetailToolAdapter adapter = new TauRetailToolAdapter(tools);
        AgentOrchestrator orchestrator = new AgentOrchestrator(chatModelFactory, adapter, null);
        List<RoundResult> rounds = new ArrayList<>();
        List<ChatMessage> history = new ArrayList<>();
        ModelBudget budget = new ModelBudget(MAX_MODEL_CALLS);
        long promptTokens = 0;
        long completionTokens = 0;
        boolean tokenUsageKnown = true;
        String userMessage = publicUserMessage;
        String failureCode = null;
        long started = System.nanoTime();

        for (int turn = 1; turn <= MAX_USER_TURNS; turn++) {
            String requestId = "tau-retail-" + UUID.randomUUID();
            UsageHook hook = new UsageHook(budget);
            orchestrator.registerRequestBudgetHook(requestId, hook);
            int firstInvocation = adapter.invocations().size();
            ChatRequest request = request(requestId, userMessage, retailPolicy, strategyGuidance,
                    adapter.toolNames(), history);
            List<ChatEvent> events;
            try {
                events = orchestrator.chat(request).collectList().block();
            } catch (RuntimeException failure) {
                failureCode = budget.exhausted() ? "BUDGET_EXHAUSTED" : "AGENT_EXCEPTION";
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
            if (budget.exhausted()) {
                failureCode = "BUDGET_EXHAUSTED";
                break;
            }
            if (errorEvent != null) {
                failureCode = "AGENT_ERROR_" + errorEvent.getError().getCode();
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

        lastRun = new RunResult(rounds, budget.calls(), promptTokens, completionTokens,
                tokenUsageKnown && budget.calls() > 0,
                (System.nanoTime() - started) / 1_000_000.0, failureCode, adapter.invocations());
    }

    public RunResult lastRun() {
        return lastRun;
    }

    public String modelName() {
        return modelName;
    }

    private ChatRequest request(String requestId, String question, String retailPolicy, String strategyGuidance,
                                List<String> toolNames, List<ChatMessage> history) {
        StringBuilder system = new StringBuilder("You are a retail customer-service agent. Follow the retail policy, use the available tools for account and order actions, and inspect tool results before replying. Make one tool call at a time. Ask the customer only for information needed to complete the request.\n\n")
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

    public record RoundResult(int turn, String userMessage, String assistantMessage,
                              List<TauRetailToolAdapter.ToolInvocation> toolCalls,
                              long promptTokens, long completionTokens) {
        public RoundResult {
            toolCalls = List.copyOf(toolCalls);
        }
    }

    public record RunResult(List<RoundResult> rounds, int modelCalls, long promptTokens,
                            long completionTokens, boolean tokenUsageKnown, double latencyMs, String failureCode,
                            List<TauRetailToolAdapter.ToolInvocation> toolCalls) {
        public RunResult {
            rounds = List.copyOf(rounds);
            toolCalls = List.copyOf(toolCalls);
        }

        public long totalTokens() {
            return promptTokens + completionTokens;
        }
    }

    private static final class ModelBudget {
        private final int limit;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicBoolean exhausted = new AtomicBoolean();

        private ModelBudget(int limit) {
            this.limit = limit;
        }

        private boolean tryCall() {
            if (calls.get() >= limit) {
                exhausted.set(true);
                return false;
            }
            calls.incrementAndGet();
            return true;
        }

        private int calls() {
            return calls.get();
        }

        private boolean exhausted() {
            return exhausted.get();
        }
    }

    private static final class UsageHook implements UsageCapturingChatModel.ModelCallBudgetHook {
        private final ModelBudget budget;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicLong promptTokens = new AtomicLong();
        private final AtomicLong completionTokens = new AtomicLong();
        private final AtomicBoolean allCallsMetered = new AtomicBoolean(true);

        private UsageHook(ModelBudget budget) {
            this.budget = budget;
        }

        @Override
        public void beforeCall(org.springframework.ai.chat.prompt.Prompt prompt, HarnessUsageSnapshot snapshot) {
            if (!budget.tryCall()) {
                throw new IllegalStateException("BUDGET_EXHAUSTED");
            }
            calls.incrementAndGet();
        }

        @Override
        public void afterCall(HarnessUsageSnapshot snapshot) {
            promptTokens.set(Math.max(promptTokens.get(), snapshot.promptTokens()));
            completionTokens.set(Math.max(completionTokens.get(), snapshot.completionTokens()));
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
