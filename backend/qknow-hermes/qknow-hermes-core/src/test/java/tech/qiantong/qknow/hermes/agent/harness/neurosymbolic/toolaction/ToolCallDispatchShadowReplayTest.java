package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ToolCallDispatchShadowReplayTest {
    @Test
    void shadowReusesResponseForSameLogicalCallIdWithoutRepeatingSideEffect() {
        ToolCallDispatch dispatch = ToolActionDfaRuntime.defaults().openRequest(
                "shadow-replay", ToolActionDfaMode.SHADOW, "fixture-read-then-write");
        AtomicInteger executions = new AtomicInteger();
        ToolCallRequest request = ToolCallRequest.builder()
                .toolCallId("stable-call-id")
                .toolName("fixture.read")
                .arguments("{}")
                .build();
        var handler = (com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler) call -> {
            executions.incrementAndGet();
            return ToolCallResponse.of(call.getToolCallId(), call.getToolName(), "result");
        };

        ToolCallResponse first = dispatch.interceptToolCall(request, handler);
        ToolCallResponse replay = dispatch.interceptToolCall(request, handler);

        assertEquals("result", first.getResult());
        assertEquals(first.getResult(), replay.getResult());
        assertEquals(1, executions.get());
        assertFalse(dispatch.receiptsSnapshot().getLast().blocked());
        assertEquals(ToolActionDfaReason.DFA_DUPLICATE_REPLAY,
                dispatch.receiptsSnapshot().getLast().reason());
        dispatch.close();
    }
}
