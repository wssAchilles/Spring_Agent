package tech.qiantong.qknow.hermes.agent.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class UsageCapturingChatModelTest {

    private final Prompt prompt = new Prompt("test");

    @Test
    void countsOnlyReportedUsageIncludingNativeDetails() {
        ChatModel delegate = mock(ChatModel.class);
        when(delegate.call(prompt)).thenReturn(response(new DefaultUsage(7, 3, 10,
                Map.of("prompt_cache_hit_tokens", 2,
                        "completion_tokens_details", Map.of("reasoning_tokens", 1)))));
        UsageCapturingChatModel model = new UsageCapturingChatModel(delegate);

        model.call(prompt);

        HarnessUsageSnapshot snapshot = model.snapshot();
        assertTrue(snapshot.isKnown());
        assertEquals(10, snapshot.totalTokens());
        assertEquals(2, snapshot.cacheHitTokens());
        assertEquals(1, snapshot.reasoningTokens());
        assertEquals(0.0D, snapshot.actualCostCny());
    }

    @Test
    void missingUsageMakesEntireRequestUnknown() {
        ChatModel delegate = mock(ChatModel.class);
        when(delegate.call(prompt)).thenReturn(response(new DefaultUsage(4, 2)))
                .thenReturn(new ChatResponse(List.of()));
        UsageCapturingChatModel model = new UsageCapturingChatModel(delegate);

        model.call(prompt);
        model.call(prompt);

        assertFalse(model.snapshot().isKnown());
        assertEquals(1, model.snapshot().meteredCallCount());
        assertEquals(1, model.snapshot().unmeteredCallCount());
    }

    @Test
    void alternateDelegateSharesRequestTotals() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel toolRouter = mock(ChatModel.class);
        when(primary.call(prompt)).thenReturn(response(new DefaultUsage(4, 2, 6)));
        when(toolRouter.call(prompt)).thenReturn(response(new DefaultUsage(3, 1, 4)));
        UsageCapturingChatModel model = new UsageCapturingChatModel(primary);
        UsageCapturingChatModel router = model.forDelegate(toolRouter);

        model.call(prompt);
        router.call(prompt);

        assertEquals(10, model.snapshot().totalTokens());
        assertEquals(2, router.snapshot().meteredCallCount());
        router.markIncomplete();
        assertFalse(model.snapshot().isKnown());
    }

    @Test
    void streamingCountsOnceAtCompletionButNotOnErrorOrCancel() {
        ChatModel delegate = mock(ChatModel.class);
        ChatResponse delta = new ChatResponse(List.of());
        ChatResponse finalChunk = response(new DefaultUsage(5, 2, 7));
        when(delegate.stream(prompt)).thenReturn(Flux.just(delta, finalChunk),
                Flux.concat(Flux.just(finalChunk), Flux.error(new IllegalStateException("lost"))),
                Flux.just(finalChunk).concatWith(Flux.never()));
        UsageCapturingChatModel model = new UsageCapturingChatModel(delegate);

        model.stream(prompt).blockLast();
        assertTrue(model.snapshot().isKnown());
        assertEquals(1, model.snapshot().meteredCallCount());
        assertThrows(IllegalStateException.class, () -> model.stream(prompt).blockLast());
        model.stream(prompt).take(1).blockLast();
        assertFalse(model.snapshot().isKnown());
        assertEquals(2, model.snapshot().unmeteredCallCount());
        assertEquals(7, model.snapshot().totalTokens());
    }

    private static ChatResponse response(DefaultUsage usage) {
        return new ChatResponse(List.of(), ChatResponseMetadata.builder().usage(usage).build());
    }
}
