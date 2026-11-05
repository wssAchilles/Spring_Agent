package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.kernel.HarnessKernelBridge;
import tech.qiantong.qknow.hermes.agent.harness.kernel.HermesHarnessKernel;
import tech.qiantong.qknow.hermes.agent.harness.kernel.KernelBridgeMode;
import tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction.ToolActionDfaMode;
import tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction.ToolActionDfaRuntime;
import tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction.ToolCallDispatch;
import tech.qiantong.qknow.hermes.proto.ChatRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOrchestratorPlanResolverTest {
    @Test
    void offKeepsOriginalResolver() {
        ToolCallbackResolver original = name -> null;
        ToolCallbackResolver bridge = name -> null;

        assertSame(original, AgentOrchestrator.selectPlanResolver(
                ToolActionDfaMode.OFF, Set.of("fixture.read"), original, bridge));
    }

    @Test
    void shadowSelectsKernelProtectedResolverAndPreservesPostconditionRejection() {
        AtomicInteger handlerCalls = new AtomicInteger();
        ToolCallback callback = callback(handlerCalls);
        ToolCallbackResolver original = name -> callback;
        HarnessKernelBridge kernelBridge = new HarnessKernelBridge(
                new HermesHarnessKernel(8, 4, 8, 8, 3), new HermesHarnessAssembler(), true,
                KernelBridgeMode.ENFORCE);
        var handle = kernelBridge.bridgeExecution(request(),
                new HermesHarnessAssembler().createDefaultProductionVoucher(7L), "prompt",
                List.of(), List.of(callback), original, List.of());
        assertTrue(!handle.failClosed());

        ToolCallbackResolver selected = AgentOrchestrator.selectPlanResolver(
                ToolActionDfaMode.SHADOW, Set.of(), original, handle.effectiveResolver());
        ToolCallback guardedCallback = selected.resolve("fixture.read");

        assertTrue(guardedCallback.call("{}").startsWith("[HARNESS_GUARD_REJECTED:RESULT_INTEGRITY]"));
        assertEquals(1, handlerCalls.get());
        kernelBridge.completeExecution(request().getRequestId(), handle, 0.0);
        assertEquals(1, kernelBridge.getLatestReceipt(request().getRequestId()).orElseThrow()
                .blockedToolInvocationCount());
    }

    @Test
    void enforceUsesKernelResolverOnlyForAllowedToolNames() {
        ToolCallback callback = callback(new AtomicInteger());
        ToolCallbackResolver original = name -> callback;
        HarnessKernelBridge kernelBridge = new HarnessKernelBridge(
                new HermesHarnessKernel(8, 4, 8, 8, 3), new HermesHarnessAssembler(), true,
                KernelBridgeMode.ENFORCE);
        var handle = kernelBridge.bridgeExecution(request(),
                new HermesHarnessAssembler().createDefaultProductionVoucher(7L), "prompt",
                List.of(), List.of(callback), original, List.of());

        ToolCallbackResolver selected = AgentOrchestrator.selectPlanResolver(
                ToolActionDfaMode.ENFORCE, Set.of("fixture.read"), original, handle.effectiveResolver());

        assertNotNull(selected.resolve("fixture.read"));
        assertNull(selected.resolve("not-enabled"));
    }

    @Test
    void planWorkerDispatchesToolCallsThroughRequestInterceptor() {
        AtomicInteger executions = new AtomicInteger();
        ToolCallback tool = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("fixture.read")
                        .description("Deterministic fixture callback")
                        .inputSchema("{\"type\":\"object\"}").build();
            }

            @Override
            public String call(String toolInput) {
                executions.incrementAndGet();
                return "read-result";
            }
        };
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            boolean toolReturned = prompt.getInstructions().stream()
                    .anyMatch(ToolResponseMessage.class::isInstance);
            AssistantMessage output = toolReturned ? new AssistantMessage("plan complete")
                    : AssistantMessage.builder().content("")
                    .toolCalls(List.of(new ToolCall("plan-call-1", "function", "fixture.read", "{}")))
                    .build();
            return new ChatResponse(List.of(new Generation(output)));
        });

        ToolCallDispatch dispatch = ToolActionDfaRuntime.defaults().openRequest(
                "plan-worker-dispatch", ToolActionDfaMode.ENFORCE, "fixture-read-then-write");
        WorkerAgent worker = new WorkerAgent("plan-worker", "fixture worker", "prompt",
                List.of(tool), model, name -> tool).withToolInterceptors(List.of(dispatch))
                .withSerialToolExecution(true);

        assertEquals("plan complete", worker.chat("read fixture", Map.of()));
        assertEquals(1, executions.get());
        assertEquals(1, dispatch.receiptsSnapshot().size());
        assertEquals("tool:plan-call-1", dispatch.receiptsSnapshot().getFirst().toolCallId());
        assertTrue(dispatch.receiptsSnapshot().getFirst().advanced());
        dispatch.close();
    }

    private static ToolCallback callback(AtomicInteger calls) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("fixture.read")
                        .description("Deterministic guarded callback")
                        .inputSchema("{\"type\":\"object\"}").build();
            }

            @Override
            public String call(String toolInput) {
                calls.incrementAndGet();
                return "{}".equals(toolInput) ? "" : "read-result";
            }
        };
    }

    private static ChatRequest request() {
        return ChatRequest.newBuilder().setRequestId("plan-resolver-kernel-enforce")
                .setBotId(7L).setQuestion("question").setSystemPrompt("role")
                .setInputParams("{\"capabilityTag\":\"TOOL\"}").build();
    }
}
