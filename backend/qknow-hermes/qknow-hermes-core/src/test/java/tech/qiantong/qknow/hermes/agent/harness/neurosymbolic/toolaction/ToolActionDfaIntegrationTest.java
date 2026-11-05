package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import org.junit.jupiter.api.Test;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolActionDfaIntegrationTest {
    @Test
    void uncoveredPathIsRecordedWithoutAdvancingAndCloseReleasesRequestKey() {
        ToolActionDfaRuntime runtime = new ToolActionDfaRuntime(ToolActionDfaRegistry.defaults(), 1);
        ToolCallDispatch first = runtime.openRequest("request-1", ToolActionDfaMode.ENFORCE,
                "fixture-read-then-write");
        assertTrue(first.isAvailable());
        assertTrue(first.isEnforce());
        first.recordUncovered("MultiAgent");
        assertEquals(ToolActionDfaReason.DFA_PATH_UNCOVERED, first.receiptsSnapshot().getFirst().reason());
        assertFalse(first.receiptsSnapshot().getFirst().advanced());

        ToolCallDispatch duplicate = runtime.openRequest("request-1", ToolActionDfaMode.ENFORCE,
                "fixture-read-then-write");
        assertFalse(duplicate.isAvailable());
        assertEquals("REQUEST_ALREADY_ACTIVE", duplicate.failureCode());
        first.close();
        assertEquals(0, runtime.activeSessions());
        ToolCallDispatch reopened = runtime.openRequest("request-1", ToolActionDfaMode.ENFORCE,
                "fixture-read-then-write");
        assertTrue(reopened.isAvailable());
        reopened.close();
    }

    @Test
    void shadowUncoveredPathIsObservedButNotReportedAsBlocked() {
        ToolCallDispatch shadow = ToolActionDfaRuntime.defaults().openRequest(
                "shadow-uncovered", ToolActionDfaMode.SHADOW, "fixture-read-then-write");

        shadow.recordUncovered("MultiAgent");

        ToolActionDfaReceipt receipt = shadow.receiptsSnapshot().getFirst();
        assertEquals(ToolActionDfaReason.DFA_PATH_UNCOVERED, receipt.reason());
        assertFalse(receipt.blocked());
        assertFalse(receipt.advanced());
        shadow.close();
    }

    @Test
    void closedShadowDispatcherRejectsLateHandlerWithoutChangingFinalSnapshot() {
        ToolCallDispatch shadow = ToolActionDfaRuntime.defaults().openRequest(
                "shadow-closed", ToolActionDfaMode.SHADOW, "fixture-read-then-write");
        shadow.close();
        AtomicInteger executions = new AtomicInteger();

        ToolCallResponse response = shadow.interceptToolCall(
                ToolCallRequest.builder().toolCallId("after-close").toolName("fixture.read").arguments("{}").build(),
                request -> {
                    executions.incrementAndGet();
                    return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "ran");
                });

        assertTrue(response.isError());
        assertTrue(response.getResult().contains("DFA_DISPATCH_CLOSED"));
        assertEquals(0, executions.get());
        assertTrue(shadow.receiptsSnapshot().isEmpty());
    }

    @Test
    void missingSpecFailsClosedOnlyInEnforceAndRemainsVisibleInShadow() {
        ToolActionDfaRuntime runtime = ToolActionDfaRuntime.defaults();
        ToolCallDispatch enforce = runtime.openRequest("missing-enforce", ToolActionDfaMode.ENFORCE, "missing");
        assertFalse(enforce.isAvailable());
        assertTrue(enforce.isEnforce());
        assertTrue(enforce.receiptsSnapshot().getFirst().blocked());
        assertEquals(ToolActionDfaReason.DFA_SESSION_UNAVAILABLE,
                enforce.receiptsSnapshot().getFirst().reason());

        ToolCallDispatch shadow = runtime.openRequest("missing-shadow", ToolActionDfaMode.SHADOW, "missing");
        assertTrue(shadow.isAvailable());
        assertFalse(shadow.isEnforce());
        assertEquals("DFA_SPEC_NOT_FOUND", shadow.failureCode());
        AtomicInteger executions = new AtomicInteger();
        ToolCallResponse response = shadow.interceptToolCall(
                ToolCallRequest.builder().toolCallId("shadow-call").toolName("fixture.read").arguments("{}").build(),
                request -> {
                    executions.incrementAndGet();
                    return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "ran");
                });
        assertEquals("ran", response.getResult());
        assertEquals(1, executions.get());
        assertFalse(shadow.receiptsSnapshot().getFirst().blocked());
        assertEquals(ToolActionDfaReason.DFA_SESSION_UNAVAILABLE,
                shadow.receiptsSnapshot().getFirst().reason());
        shadow.close();
    }

    @Test
    void shadowCapacityFailureStillPassesThroughAndRecordsUnavailability() {
        ToolActionDfaRuntime runtime = new ToolActionDfaRuntime(ToolActionDfaRegistry.defaults(), 1);
        ToolCallDispatch occupying = runtime.openRequest("occupying", ToolActionDfaMode.ENFORCE,
                "fixture-read-then-write");
        ToolCallDispatch shadow = runtime.openRequest("shadow-capacity", ToolActionDfaMode.SHADOW,
                "fixture-read-then-write");

        assertTrue(shadow.isAvailable());
        assertEquals("SESSION_CAPACITY_EXCEEDED", shadow.failureCode());
        AtomicInteger executions = new AtomicInteger();
        ToolCallResponse response = shadow.interceptToolCall(
                ToolCallRequest.builder().toolCallId("shadow-call").toolName("fixture.read").arguments("{}").build(),
                request -> {
                    executions.incrementAndGet();
                    return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), "ran");
                });

        assertEquals("ran", response.getResult());
        assertEquals(1, executions.get());
        assertFalse(shadow.receiptsSnapshot().getFirst().blocked());
        assertEquals("SESSION_CAPACITY_EXCEEDED", shadow.receiptsSnapshot().getFirst().failureCode());
        shadow.close();
        occupying.close();
    }
}
