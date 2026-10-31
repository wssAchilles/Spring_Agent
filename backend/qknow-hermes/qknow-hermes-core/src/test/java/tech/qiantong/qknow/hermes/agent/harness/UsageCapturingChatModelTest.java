package tech.qiantong.qknow.hermes.agent.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
        assertTrue(snapshot.isCostKnown());
        assertTrue(snapshot.actualCostCny() > 0.0D);
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
        assertFalse(model.snapshot().isCostKnown());
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

    @Test
    void officialPricingUsesBeijingTimeAndClampsCacheHits() {
        Instant beforePeak = Instant.parse("2026-11-02T00:59:59Z");
        Instant morningPeak = Instant.parse("2026-11-02T01:00:00Z");
        Instant lunch = Instant.parse("2026-11-02T04:00:00Z");
        Instant afternoonPeak = Instant.parse("2026-11-02T06:00:00Z");
        Instant afterPeak = Instant.parse("2026-11-02T10:00:00Z");
        Instant weekend = Instant.parse("2026-11-01T02:00:00Z");

        assertFalse(UsageCapturingChatModel.isPeakBeijingTime(beforePeak));
        assertTrue(UsageCapturingChatModel.isPeakBeijingTime(morningPeak));
        assertFalse(UsageCapturingChatModel.isPeakBeijingTime(lunch));
        assertTrue(UsageCapturingChatModel.isPeakBeijingTime(afternoonPeak));
        assertFalse(UsageCapturingChatModel.isPeakBeijingTime(afterPeak));
        assertFalse(UsageCapturingChatModel.isPeakBeijingTime(weekend));
        assertEquals(28_040L, UsageCapturingChatModel.computeOfficialCostNano(10, 5, 2, lunch));
        assertEquals(56_080L, UsageCapturingChatModel.computeOfficialCostNano(10, 5, 2, morningPeak));
        assertEquals(20_200L, UsageCapturingChatModel.computeOfficialCostNano(10, 5, 20, lunch));
        assertEquals(0.00005608D,
                UsageCapturingChatModel.computeOfficialCostCny(10, 5, 2, morningPeak));
    }

    @Test
    void accumulatesEachCallAtItsOwnPriceAndHooksSeeFinalSnapshot() {
        ChatModel delegate = mock(ChatModel.class);
        when(delegate.call(prompt)).thenReturn(response(new DefaultUsage(10, 5, 15,
                Map.of("prompt_cache_hit_tokens", 2))));
        AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-11-02T04:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return time.get(); }
        };
        List<HarnessUsageSnapshot> after = new ArrayList<>();
        UsageCapturingChatModel.ModelCallBudgetHook hook = new UsageCapturingChatModel.ModelCallBudgetHook() {
            @Override public void beforeCall(Prompt candidate, HarnessUsageSnapshot snapshot) {
                assertEquals(prompt, candidate);
                assertEquals(after.size(), snapshot.meteredCallCount());
            }
            @Override public void afterCall(HarnessUsageSnapshot snapshot) { after.add(snapshot); }
        };
        UsageCapturingChatModel model = new UsageCapturingChatModel(delegate, hook, clock);

        model.call(prompt);
        time.set(Instant.parse("2026-11-02T06:00:00Z"));
        model.forDelegate(delegate).call(prompt);

        assertEquals(2, after.size());
        assertEquals(28_040D / 1_000_000_000D, after.get(0).actualCostCny());
        assertEquals(84_120D / 1_000_000_000D, model.snapshot().actualCostCny());
    }

    @Test
    void budgetHookCanBlockBeforeDelegateAndObservesStreamFailure() {
        ChatModel delegate = mock(ChatModel.class);
        List<HarnessUsageSnapshot> after = new ArrayList<>();
        UsageCapturingChatModel.ModelCallBudgetHook hook = new UsageCapturingChatModel.ModelCallBudgetHook() {
            @Override public void beforeCall(Prompt candidate, HarnessUsageSnapshot snapshot) {
                throw new IllegalStateException("BUDGET_EXHAUSTED");
            }
            @Override public void afterCall(HarnessUsageSnapshot snapshot) { after.add(snapshot); }
        };
        UsageCapturingChatModel blocked = new UsageCapturingChatModel(delegate, hook);
        assertThrows(IllegalStateException.class, () -> blocked.call(prompt));
        assertThrows(IllegalStateException.class, () -> blocked.stream(prompt).blockLast());
        verify(delegate, never()).call(prompt);
        verify(delegate, never()).stream(prompt);
        assertTrue(after.isEmpty());
        assertEquals(0, blocked.snapshot().unmeteredCallCount());

        when(delegate.stream(prompt)).thenReturn(Flux.error(new IllegalStateException("lost")));
        UsageCapturingChatModel observed = new UsageCapturingChatModel(delegate,
                new UsageCapturingChatModel.ModelCallBudgetHook() {
                    @Override public void beforeCall(Prompt candidate, HarnessUsageSnapshot snapshot) { }
                    @Override public void afterCall(HarnessUsageSnapshot snapshot) { after.add(snapshot); }
                });
        assertThrows(IllegalStateException.class, () -> observed.stream(prompt).blockLast());
        assertEquals(1, after.size());
        assertEquals(1, after.get(0).unmeteredCallCount());

        when(delegate.call(prompt)).thenThrow(new IllegalStateException("lost"));
        assertThrows(IllegalStateException.class, () -> observed.call(prompt));
        assertEquals(2, after.size());
        assertEquals(2, after.get(1).unmeteredCallCount());

        when(delegate.stream(prompt)).thenReturn(Flux.just(response(new DefaultUsage(5, 2, 7)))
                .concatWith(Flux.never()));
        observed.stream(prompt).take(1).blockLast();
        assertEquals(3, after.size());
        assertEquals(3, after.get(2).unmeteredCallCount());
    }

    private static ChatResponse response(DefaultUsage usage) {
        return new ChatResponse(List.of(), ChatResponseMetadata.builder().usage(usage).build());
    }
}
