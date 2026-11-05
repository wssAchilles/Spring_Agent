package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.kernel.HarnessKernelBridge;
import tech.qiantong.qknow.hermes.agent.harness.kernel.HermesHarnessKernel;
import tech.qiantong.qknow.hermes.agent.harness.kernel.KernelBridgeMode;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolActionDfaKernelIntegrationTest {
    @Test
    void kernelPostconditionRejectionDoesNotAdvanceDfaButSuccessDoes() {
        String requestId = "kernel-dfa-postcondition";
        HermesHarnessAssembler assembler = new HermesHarnessAssembler();
        HermesHarnessVoucher voucher = assembler.createDefaultProductionVoucher(7L);
        HarnessKernelBridge bridge = new HarnessKernelBridge(
                new HermesHarnessKernel(8, 4, 8, 8, 3), assembler, true, KernelBridgeMode.ENFORCE);
        AtomicInteger handlerCalls = new AtomicInteger();
        ToolCallback callback = callback(handlerCalls);
        var handle = bridge.bridgeExecution(request(requestId), voucher, "prompt",
                List.of(), List.of(callback), null, List.of());
        assertFalse(handle.failClosed());
        assertEquals(KernelBridgeMode.ENFORCE, handle.effectiveMode());

        ToolCallDispatch dispatch = ToolActionDfaRuntime.defaults().openRequest(
                requestId, ToolActionDfaMode.ENFORCE, "fixture-read-then-write");
        ToolCallback kernelGuardedCallback = handle.effectiveTools().getFirst();

        ToolCallResponse rejected = dispatch.interceptToolCall(call("postcondition-reject", "fixture.read", "{}"),
                toolRequest -> ToolCallResponse.of(toolRequest.getToolCallId(), toolRequest.getToolName(),
                        kernelGuardedCallback.call(toolRequest.getArguments())));

        assertFalse(rejected.isError());
        assertEquals("[HARNESS_GUARD_REJECTED:RESULT_INTEGRITY]", rejected.getResult());
        assertEquals(1, handlerCalls.get());
        assertEquals(new ToolActionDfaState("START"), dispatch.sessionSnapshot());
        ToolActionDfaReceipt rejectionReceipt = dispatch.receiptsSnapshot().getFirst();
        assertEquals(ToolActionDfaReason.DFA_GUARD_REJECTED, rejectionReceipt.reason());
        assertTrue(rejectionReceipt.blocked());
        assertFalse(rejectionReceipt.advanced());

        ToolCallResponse successful = dispatch.interceptToolCall(
                call("successful-read", "fixture.read", "{\"value\":\"ok\"}"),
                toolRequest -> ToolCallResponse.of(toolRequest.getToolCallId(), toolRequest.getToolName(),
                        kernelGuardedCallback.call(toolRequest.getArguments())));

        assertEquals("read-result", successful.getResult());
        assertEquals(2, handlerCalls.get());
        assertEquals(new ToolActionDfaState("READ"), dispatch.sessionSnapshot());
        assertTrue(dispatch.receiptsSnapshot().getLast().advanced());

        bridge.completeExecution(requestId, handle, 0.0);
        HarnessKernelBridge.KernelBridgeReceipt kernelReceipt = bridge.getLatestReceipt(requestId).orElseThrow();
        assertEquals(2, kernelReceipt.guardedToolInvocationCount());
        assertEquals(1, kernelReceipt.blockedToolInvocationCount());
        assertTrue(kernelReceipt.guardViolations().contains("RESULT_INTEGRITY"));
        dispatch.close();
    }

    private static ToolCallback callback(AtomicInteger handlerCalls) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("fixture.read")
                        .description("Deterministic read fixture")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                handlerCalls.incrementAndGet();
                return "{}".equals(toolInput) ? "" : "read-result";
            }
        };
    }

    private static ChatRequest request(String requestId) {
        return ChatRequest.newBuilder().setRequestId(requestId).setBotId(7L).setQuestion("question")
                .setSystemPrompt("role").setInputParams("{\"capabilityTag\":\"TOOL\"}").build();
    }

    private static ToolCallRequest call(String callId, String toolName, String arguments) {
        return ToolCallRequest.builder().toolCallId(callId).toolName(toolName).arguments(arguments).build();
    }
}
