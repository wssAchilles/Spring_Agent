package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallDispatchTest {
    private final ToolActionDfaRuntime runtime = ToolActionDfaRuntime.defaults();
    private static final String SPEC = "fixture-read-then-write";

    @Test
    void offIsTransparentAndDoesNotCreateReceipt() {
        ToolCallDispatch dispatch = runtime.openRequest("off", ToolActionDfaMode.OFF, SPEC);
        dispatch.recordUncovered("OFF_PATH");
        AtomicInteger executions = new AtomicInteger();
        ToolCallResponse response = dispatch.interceptToolCall(call("c1", "fixture.read"), request -> {
            executions.incrementAndGet();
            return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "ok");
        });

        assertEquals("ok", response.getResult());
        assertEquals(1, executions.get());
        assertTrue(dispatch.receiptsSnapshot().isEmpty());
        assertEquals(0, runtime.activeSessions());
        dispatch.close();
        ToolCallResponse late = dispatch.interceptToolCall(call("late", "fixture.read"), request -> {
            executions.incrementAndGet();
            return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "late");
        });
        assertTrue(late.isError());
        assertTrue(late.getResult().contains("DFA_DISPATCH_CLOSED"));
        assertEquals(1, executions.get());
        assertTrue(dispatch.receiptsSnapshot().isEmpty());
    }

    @Test
    void shadowRunsIllegalTransitionAndRecordsWouldBlock() {
        ToolCallDispatch dispatch = runtime.openRequest("shadow", ToolActionDfaMode.SHADOW, SPEC);
        AtomicInteger executions = new AtomicInteger();
        ToolCallResponse response = dispatch.interceptToolCall(call("c1", "fixture.write"), request -> {
            executions.incrementAndGet();
            return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "ran");
        });

        ToolActionDfaReceipt receipt = dispatch.receiptsSnapshot().getFirst();
        assertEquals("ran", response.getResult());
        assertEquals(1, executions.get());
        assertFalse(receipt.allowed());
        assertFalse(receipt.blocked());
        assertFalse(receipt.advanced());
        assertEquals(ToolActionDfaReason.DFA_TRANSITION_NOT_FOUND, receipt.reason());
        assertEquals(new ToolActionDfaState("START"), dispatch.sessionSnapshot());
        dispatch.close();
    }

    @Test
    void enforceRejectsBeforeHandlerAndDoesNotAdvance() {
        ToolCallDispatch dispatch = runtime.openRequest("enforce", ToolActionDfaMode.ENFORCE, SPEC);
        AtomicInteger executions = new AtomicInteger();
        ToolCallResponse response = dispatch.interceptToolCall(call("c1", "fixture.write"), request -> {
            executions.incrementAndGet();
            return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "should not run");
        });

        assertTrue(response.isError());
        assertEquals(0, executions.get());
        assertEquals(new ToolActionDfaState("START"), dispatch.sessionSnapshot());
        assertTrue(dispatch.receiptsSnapshot().getFirst().blocked());
        dispatch.close();
    }

    @Test
    void successfulCallAdvancesAndRepeatedCallIdDoesNotExecuteAgain() {
        ToolCallDispatch dispatch = runtime.openRequest("replay", ToolActionDfaMode.ENFORCE, SPEC);
        AtomicInteger executions = new AtomicInteger();
        var request = call("stable-call-id", "fixture.read");
        var handler = (com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler) current -> {
            executions.incrementAndGet();
            return ToolCallResponse.of(current.getToolCallId(), current.getToolName(), "read-result");
        };

        ToolCallResponse first = dispatch.interceptToolCall(request, handler);
        ToolCallResponse replay = dispatch.interceptToolCall(request, handler);

        assertEquals("read-result", first.getResult());
        assertEquals(first.getResult(), replay.getResult());
        assertEquals(1, executions.get());
        assertEquals(new ToolActionDfaState("READ"), dispatch.sessionSnapshot());
        assertTrue(dispatch.receiptsSnapshot().getFirst().advanced());
        assertTrue(dispatch.receiptsSnapshot().getLast().allowed());
        assertFalse(dispatch.receiptsSnapshot().getLast().advanced());
        assertEquals(ToolActionDfaReason.DFA_DUPLICATE_REPLAY,
                dispatch.receiptsSnapshot().getLast().reason());
        dispatch.close();
    }

    @Test
    void replayOfRejectedCallPreservesOriginalDecisionWithoutExecutingAgain() {
        ToolCallDispatch dispatch = runtime.openRequest("rejected-replay", ToolActionDfaMode.ENFORCE, SPEC);
        AtomicInteger executions = new AtomicInteger();
        var request = call("rejected-call-id", "fixture.write");
        var handler = (com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler) current -> {
            executions.incrementAndGet();
            return ToolCallResponse.of(current.getToolCallId(), current.getToolName(), "should not run");
        };

        ToolCallResponse first = dispatch.interceptToolCall(request, handler);
        ToolCallResponse replay = dispatch.interceptToolCall(request, handler);

        assertTrue(first.isError());
        assertTrue(replay.isError());
        assertEquals(0, executions.get());
        ToolActionDfaReceipt replayReceipt = dispatch.receiptsSnapshot().getLast();
        assertEquals(ToolActionDfaReason.DFA_DUPLICATE_REPLAY, replayReceipt.reason());
        assertFalse(replayReceipt.allowed());
        assertTrue(replayReceipt.blocked());
        assertFalse(replayReceipt.advanced());
        dispatch.close();
    }

    @Test
    void toolErrorAndExistingGuardRejectionDoNotAdvance() {
        ToolCallDispatch dispatch = runtime.openRequest("failure", ToolActionDfaMode.ENFORCE, SPEC);
        ToolCallResponse rejected = dispatch.interceptToolCall(call("c1", "fixture.read"), request ->
                ToolCallResponse.of(request.getToolCallId(), request.getToolName(),
                        "[HARNESS_GUARD_REJECTED:POST]"));
        assertFalse(rejected.isError());
        assertEquals(new ToolActionDfaState("START"), dispatch.sessionSnapshot());
        assertEquals(ToolActionDfaReason.DFA_GUARD_REJECTED, dispatch.receiptsSnapshot().getFirst().reason());

        ToolCallDispatch failed = runtime.openRequest("tool-error", ToolActionDfaMode.ENFORCE, SPEC);
        ToolCallResponse error = failed.interceptToolCall(call("c2", "fixture.read"), request ->
                ToolCallResponse.error(request.getToolCallId(), request.getToolName(), "tool failed"));
        assertTrue(error.isError());
        assertEquals(new ToolActionDfaState("START"), failed.sessionSnapshot());
        assertFalse(failed.receiptsSnapshot().getFirst().advanced());
        dispatch.close();
        failed.close();
    }

    @Test
    void permissionDenialJsonDoesNotAdvanceButOrdinaryJsonDoes() {
        ToolCallDispatch denied = runtime.openRequest("permission-denied", ToolActionDfaMode.ENFORCE, SPEC);
        ToolCallResponse denial = denied.interceptToolCall(call("denied-call", "fixture.read"), request ->
                ToolCallResponse.of(request.getToolCallId(), request.getToolName(),
                        "{\"error\": \"permission_denied\", \"tool\": \"fixture.read\"}"));
        assertEquals(new ToolActionDfaState("START"), denied.sessionSnapshot());
        assertEquals(ToolActionDfaReason.DFA_GUARD_REJECTED, denied.receiptsSnapshot().getFirst().reason());
        assertEquals("{\"error\": \"permission_denied\", \"tool\": \"fixture.read\"}", denial.getResult());

        ToolCallDispatch ordinary = runtime.openRequest("ordinary-json", ToolActionDfaMode.ENFORCE, SPEC);
        ordinary.interceptToolCall(call("ordinary-call", "fixture.read"), request ->
                ToolCallResponse.of(request.getToolCallId(), request.getToolName(),
                        "{\"message\": \"permission_denied is only an example\"}"));
        assertEquals(new ToolActionDfaState("READ"), ordinary.sessionSnapshot());
        assertTrue(ordinary.receiptsSnapshot().getFirst().advanced());
        denied.close();
        ordinary.close();
    }

    @Test
    void shadowUnavailableSessionPreservesHandlerException() {
        ToolCallDispatch dispatch = runtime.openRequest("missing-shadow-throws", ToolActionDfaMode.SHADOW, "missing");
        IllegalStateException expected = new IllegalStateException("handler failure");

        IllegalStateException actual = assertThrows(IllegalStateException.class, () ->
                dispatch.interceptToolCall(call("shadow-failure", "fixture.read"), request -> {
                    throw expected;
                }));

        assertTrue(actual == expected);
        assertFalse(dispatch.receiptsSnapshot().getFirst().blocked());
        dispatch.close();
    }

    private static ToolCallRequest call(String id, String tool) {
        return ToolCallRequest.builder().toolCallId(id).toolName(tool).arguments("{}").build();
    }
}
