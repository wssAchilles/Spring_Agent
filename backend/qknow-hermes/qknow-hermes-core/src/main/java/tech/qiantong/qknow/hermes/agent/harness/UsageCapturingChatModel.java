package tech.qiantong.qknow.hermes.agent.harness;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/** Request-scoped model wrapper; only provider-reported usage is counted. */
public final class UsageCapturingChatModel implements ChatModel {

    public interface ModelCallBudgetHook {
        void beforeCall(Prompt prompt, HarnessUsageSnapshot snapshot);

        void afterCall(HarnessUsageSnapshot snapshot);
    }

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private final ChatModel delegate;
    private final Totals totals;
    private final ModelCallBudgetHook budgetHook;
    private final Clock clock;

    public UsageCapturingChatModel(ChatModel delegate) {
        this(delegate, null);
    }

    public UsageCapturingChatModel(ChatModel delegate, ModelCallBudgetHook budgetHook) {
        this(delegate, budgetHook, Clock.systemUTC());
    }

    UsageCapturingChatModel(ChatModel delegate, ModelCallBudgetHook budgetHook, Clock clock) {
        this(delegate, new Totals(), budgetHook, clock);
    }

    private UsageCapturingChatModel(ChatModel delegate, Totals totals, ModelCallBudgetHook budgetHook, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.totals = totals;
        this.budgetHook = budgetHook;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public UsageCapturingChatModel forDelegate(ChatModel delegate) {
        return new UsageCapturingChatModel(delegate, totals, budgetHook, clock);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        beforeCall(prompt);
        ChatResponse response;
        try {
            response = delegate.call(prompt);
        } catch (RuntimeException | Error failure) {
            markIncomplete();
            afterCall();
            throw failure;
        }
        record(usage(response));
        afterCall();
        return response;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            beforeCall(prompt);
            AtomicReference<Usage> lastUsage = new AtomicReference<>();
            Flux<ChatResponse> stream;
            try {
                stream = delegate.stream(prompt);
            } catch (RuntimeException | Error failure) {
                markIncomplete();
                afterCall();
                return Flux.error(failure);
            }
            return stream
                    .doOnNext(response -> {
                        Usage current = usage(response);
                        if (current != null) {
                            lastUsage.set(current);
                        }
                    })
                    .doOnComplete(() -> record(lastUsage.get()))
                    .doOnError(failure -> markIncomplete())
                    .doOnCancel(this::markIncomplete)
                    .doFinally(signal -> afterCall());
        });
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    public HarnessUsageSnapshot snapshot() {
        synchronized (totals) {
            return new HarnessUsageSnapshot(
                    UsageStatus.KNOWN, totals.promptTokens, totals.completionTokens, totals.totalTokens,
                    totals.cacheHitTokens, totals.reasoningTokens, totals.costNano / 1_000_000_000.0D,
                    totals.meteredCallCount, totals.unmeteredCallCount);
        }
    }

    public void markIncomplete() {
        synchronized (totals) {
            totals.unmeteredCallCount++;
        }
    }

    private void record(Usage usage) {
        synchronized (totals) {
            if (usage == null) {
                totals.unmeteredCallCount++;
                return;
            }
            long promptTokens = positive(usage.getPromptTokens());
            long completionTokens = positive(usage.getCompletionTokens());
            long cacheHitTokens = nativeNumber(usage.getNativeUsage(), "prompt_cache_hit_tokens");
            totals.promptTokens += promptTokens;
            totals.completionTokens += completionTokens;
            long reportedTotal = positive(usage.getTotalTokens());
            totals.totalTokens += reportedTotal > 0 ? reportedTotal
                    : promptTokens + completionTokens;
            Object nativeUsage = usage.getNativeUsage();
            totals.cacheHitTokens += cacheHitTokens;
            long nestedReasoning = nativeNumber(nativeField(nativeUsage, "completion_tokens_details"), "reasoning_tokens");
            totals.reasoningTokens += nestedReasoning > 0 ? nestedReasoning : nativeNumber(nativeUsage, "reasoning_tokens");
            totals.costNano += computeOfficialCostNano(promptTokens, completionTokens, cacheHitTokens, clock.instant());
            totals.meteredCallCount++;
        }
    }

    private void beforeCall(Prompt prompt) {
        if (budgetHook != null) {
            budgetHook.beforeCall(prompt, snapshot());
        }
    }

    private void afterCall() {
        if (budgetHook != null) {
            budgetHook.afterCall(snapshot());
        }
    }

    public static boolean isPeakBeijingTime(Instant timestamp) {
        // shortcut: statutory holidays need an official calendar; add one before pricing holiday calls.
        var beijing = Objects.requireNonNull(timestamp, "timestamp").atZone(BEIJING);
        DayOfWeek day = beijing.getDayOfWeek();
        LocalTime time = beijing.toLocalTime();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
                && ((!time.isBefore(LocalTime.of(9, 0)) && time.isBefore(LocalTime.NOON))
                || (!time.isBefore(LocalTime.of(14, 0)) && time.isBefore(LocalTime.of(18, 0))));
    }

    public static long computeOfficialCostNano(long promptTokens, long completionTokens,
                                                long cacheHitTokens, Instant timestamp) {
        long hit = Math.min(Math.max(0L, promptTokens), Math.max(0L, cacheHitTokens));
        long miss = Math.max(0L, promptTokens - hit);
        long peakMultiplier = isPeakBeijingTime(timestamp) ? 2L : 1L;
        return peakMultiplier * (hit * 20L + miss * 1_000L + Math.max(0L, completionTokens) * 4_000L);
    }

    public static double computeOfficialCostCny(long promptTokens, long completionTokens,
                                                 long cacheHitTokens, Instant timestamp) {
        return computeOfficialCostNano(promptTokens, completionTokens, cacheHitTokens, timestamp) / 1_000_000_000.0D;
    }

    private static Usage usage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        return usage != null && (positive(usage.getPromptTokens()) > 0
                || positive(usage.getCompletionTokens()) > 0
                || positive(usage.getTotalTokens()) > 0) ? usage : null;
    }

    private static long positive(Number value) {
        return value == null ? 0L : Math.max(0L, value.longValue());
    }

    private static Object nativeField(Object source, String key) {
        if (source instanceof JsonNode node) {
            return node.get(key);
        }
        return source instanceof Map<?, ?> map ? map.get(key) : null;
    }

    private static long nativeNumber(Object source, String key) {
        Object value = nativeField(source, key);
        if (value instanceof JsonNode node) {
            return node.isNumber() ? Math.max(0L, node.longValue()) : 0L;
        }
        return value instanceof Number number ? positive(number) : 0L;
    }

    private static final class Totals {
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private long cacheHitTokens;
        private long reasoningTokens;
        private long costNano;
        private int meteredCallCount;
        private int unmeteredCallCount;
    }
}
