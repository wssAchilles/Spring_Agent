package tech.qiantong.qknow.hermes.agent.harness;

import com.fasterxml.jackson.databind.JsonNode;
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

    private final ChatModel delegate;
    private final Totals totals;

    public UsageCapturingChatModel(ChatModel delegate) {
        this(delegate, new Totals());
    }

    private UsageCapturingChatModel(ChatModel delegate, Totals totals) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.totals = totals;
    }

    public UsageCapturingChatModel forDelegate(ChatModel delegate) {
        return new UsageCapturingChatModel(delegate, totals);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            ChatResponse response = delegate.call(prompt);
            record(usage(response));
            return response;
        } catch (RuntimeException | Error failure) {
            markIncomplete();
            throw failure;
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            AtomicReference<Usage> lastUsage = new AtomicReference<>();
            Flux<ChatResponse> stream;
            try {
                stream = delegate.stream(prompt);
            } catch (RuntimeException | Error failure) {
                markIncomplete();
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
                    .doOnCancel(this::markIncomplete);
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
                    totals.cacheHitTokens, totals.reasoningTokens, 0.0D,
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
            totals.promptTokens += positive(usage.getPromptTokens());
            totals.completionTokens += positive(usage.getCompletionTokens());
            long reportedTotal = positive(usage.getTotalTokens());
            totals.totalTokens += reportedTotal > 0 ? reportedTotal
                    : positive(usage.getPromptTokens()) + positive(usage.getCompletionTokens());
            Object nativeUsage = usage.getNativeUsage();
            totals.cacheHitTokens += nativeNumber(nativeUsage, "prompt_cache_hit_tokens");
            long nestedReasoning = nativeNumber(nativeField(nativeUsage, "completion_tokens_details"), "reasoning_tokens");
            totals.reasoningTokens += nestedReasoning > 0 ? nestedReasoning : nativeNumber(nativeUsage, "reasoning_tokens");
            totals.meteredCallCount++;
        }
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
        private int meteredCallCount;
        private int unmeteredCallCount;
    }
}
