package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallDispatchConcurrencyTest {
    @Test
    void closeAndSnapshotWaitsForStartedHandlerAndRejectsLateDispatch() throws Exception {
        ToolActionDfaRuntime runtime = ToolActionDfaRuntime.defaults();
        ToolCallDispatch dispatch = runtime.openRequest("close-race", ToolActionDfaMode.ENFORCE,
                "fixture-read-then-write");
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        CountDownLatch closeAttempted = new CountDownLatch(1);
        CountDownLatch closeReturned = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ToolCallResponse> invocation = executor.submit(() -> dispatch.interceptToolCall(
                    call("in-flight", "fixture.read"), request -> {
                        executions.incrementAndGet();
                        handlerStarted.countDown();
                        await(releaseHandler);
                        return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "read-result");
                    }));
            assertTrue(handlerStarted.await(5, TimeUnit.SECONDS));

            Future<List<ToolActionDfaReceipt>> finalSnapshot = executor.submit(() -> {
                closeAttempted.countDown();
                List<ToolActionDfaReceipt> receipts = dispatch.closeAndSnapshot();
                closeReturned.countDown();
                return receipts;
            });
            assertTrue(closeAttempted.await(5, TimeUnit.SECONDS));
            assertFalse(closeReturned.await(200, TimeUnit.MILLISECONDS),
                    "closeAndSnapshot must drain an admitted handler before returning");
            releaseHandler.countDown();
            assertEquals("read-result", invocation.get(5, TimeUnit.SECONDS).getResult());
            List<ToolActionDfaReceipt> finalReceipts = finalSnapshot.get(5, TimeUnit.SECONDS);
            assertEquals(1, executions.get());
            assertEquals(1, finalReceipts.size());
            assertTrue(finalReceipts.getFirst().advanced());
            assertEquals(new ToolActionDfaState("READ"), dispatch.sessionSnapshot());

            ToolCallResponse late = dispatch.interceptToolCall(call("late", "fixture.write"), request -> {
                executions.incrementAndGet();
                return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "late-side-effect");
            });
            assertTrue(late.isError());
            assertTrue(late.getResult().contains("DFA_DISPATCH_CLOSED"));
            assertEquals(1, executions.get());
            assertEquals(finalReceipts, dispatch.receiptsSnapshot());
            assertEquals(0, runtime.activeSessions());
        } finally {
            releaseHandler.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shadowUnavailableSessionStillPassesThroughWhileRequestIsOpen() {
        ToolActionDfaRuntime runtime = new ToolActionDfaRuntime(ToolActionDfaRegistry.defaults(), 1);
        ToolCallDispatch occupying = runtime.openRequest("occupied", ToolActionDfaMode.ENFORCE,
                "fixture-read-then-write");
        ToolCallDispatch shadow = runtime.openRequest("shadow-unavailable", ToolActionDfaMode.SHADOW,
                "fixture-read-then-write");
        AtomicInteger executions = new AtomicInteger();

        try {
            ToolCallResponse response = shadow.interceptToolCall(call("shadow-call", "fixture.read"), request -> {
                executions.incrementAndGet();
                return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "passed-through");
            });

            assertEquals("passed-through", response.getResult());
            assertEquals(1, executions.get());
            assertEquals(ToolActionDfaReason.DFA_SESSION_UNAVAILABLE,
                    shadow.receiptsSnapshot().getFirst().reason());
            assertFalse(shadow.receiptsSnapshot().getFirst().blocked());
        } finally {
            shadow.close();
            occupying.close();
        }
    }

    private static ToolCallRequest call(String id, String tool) {
        return ToolCallRequest.builder().toolCallId(id).toolName(tool).arguments("{}").build();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test latch", exception);
        }
    }
}
