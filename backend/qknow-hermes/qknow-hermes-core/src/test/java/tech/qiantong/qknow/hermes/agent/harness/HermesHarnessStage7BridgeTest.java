package tech.qiantong.qknow.hermes.agent.harness;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;
import tech.qiantong.qknow.hermes.agent.harness.kernel.HarnessKernelBridge;
import tech.qiantong.qknow.hermes.agent.harness.kernel.HermesHarnessKernel;
import tech.qiantong.qknow.hermes.agent.harness.kernel.KernelBridgeMode;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.hermes.proto.RAGContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HermesHarnessStage7BridgeTest {
    private final HermesHarnessAssembler assembler = new HermesHarnessAssembler();
    private final HermesHarnessVoucher voucher = assembler.createDefaultProductionVoucher(7L);

    private HarnessKernelBridge bridge(KernelBridgeMode mode) {
        return new HarnessKernelBridge(new HermesHarnessKernel(8, 4, 8, 8, 3), assembler, true, mode);
    }

    private ChatRequest request(String id, String capability) {
        return ChatRequest.newBuilder().setRequestId(id).setBotId(7L).setQuestion("question")
                .setSystemPrompt("role").setInputParams("{\"capabilityTag\":\"" + capability + "\"}")
                .setModelConfig(ModelConfig.newBuilder().setPlatform("deepseek")
                        .setModelName("deepseek-flash").build()).build();
    }

    @Test
    void shouldBeCompletelyNoOpWhenBridgeModeIsOff() {
        HarnessKernelBridge disabled = HarnessKernelBridge.disabledInstance();
        ToolCallback tool = tool(new AtomicInteger());
        var handle = disabled.bridgeExecution(request("off", "TOOL"), voucher, "original",
                List.of(), List.of(tool), null, List.of());

        assertTrue(disabled.isOff());
        assertEquals(KernelBridgeMode.OFF, handle.effectiveMode());
        assertEquals("original", handle.effectiveSystemPrompt());
        assertSame(tool, handle.effectiveTools().getFirst());
        assertNull(handle.session());
        disabled.completeExecution("off", handle, 0.0);
        assertTrue(disabled.getLatestReceipt("off").isEmpty());
        assertTrue(new AgentOrchestrator(null, null, null).getHarnessKernelBridge().isOff());
    }

    @Test
    void shouldRecordShadowReceiptAndFailOpenWhenCapabilityMissingInShadowMode() {
        HarnessKernelBridge shadow = bridge(KernelBridgeMode.SHADOW);
        var handle = shadow.bridgeExecution(request("shadow", "RAG"), voucher, "original",
                List.of(), List.of(), null, List.of());

        assertFalse(handle.failClosed());
        assertEquals("original", handle.effectiveSystemPrompt());
        assertFalse(shadow.getLatestReceipt("shadow").orElseThrow().preparedSuccess());
        assertFalse(shadow.getLatestReceipt("shadow").orElseThrow().failClosed());
    }

    @Test
    void offAndShadowChatContinueToModelWithoutNetwork() {
        for (KernelBridgeMode mode : List.of(KernelBridgeMode.OFF, KernelBridgeMode.SHADOW)) {
            ChatModelFactory factory = mock(ChatModelFactory.class);
            ChatModel model = mock(ChatModel.class);
            when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);
            when(model.stream(any(Prompt.class))).thenReturn(Flux.just(
                    new ChatResponse(List.of(new Generation(new AssistantMessage("answer"))))));
            AgentOrchestrator orchestrator = new AgentOrchestrator(factory, null, null);
            HarnessKernelBridge active = mode == KernelBridgeMode.OFF
                    ? HarnessKernelBridge.disabledInstance() : bridge(mode);
            orchestrator.setHarnessKernelBridge(active);
            String id = "chat-" + mode;

            var events = orchestrator.chat(request(id, "RAG")).collectList().block();

            assertNotNull(events);
            assertTrue(events.stream().noneMatch(event -> event.hasError()), mode.name());
            verify(model).stream(any(Prompt.class));
            if (mode == KernelBridgeMode.OFF) {
                assertTrue(active.getLatestReceipt(id).isEmpty());
            } else {
                var receipt = active.getLatestReceipt(id).orElseThrow();
                assertFalse(receipt.preparedSuccess());
                assertFalse(receipt.failClosed());
            }
        }
    }

    @Test
    void shouldFailCloseBeforeLlmInvocationWhenCapabilityMissingInEnforceMode() {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);
        AgentOrchestrator orchestrator = new AgentOrchestrator(factory, null, null);
        HarnessKernelBridge enforced = bridge(KernelBridgeMode.ENFORCE);
        orchestrator.setHarnessKernelBridge(enforced);
        ChatRequest request = request("enforce-rag", "RAG");

        var events = orchestrator.chat(request).collectList().block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(event -> event.hasError() && event.getError().getCode() == 403));
        verifyNoInteractions(model);
        assertEquals(HarnessEvidenceRecord.ExecutionMode.FAIL_SAFE,
                orchestrator.getLatestEvidence(request.getRequestId()).executionMode());
        assertTrue(enforced.getLatestReceipt(request.getRequestId()).orElseThrow().failClosed());
    }

    @Test
    void shouldFailCloseForEachMissingDeclaredCapability() {
        HarnessKernelBridge enforced = bridge(KernelBridgeMode.ENFORCE);
        for (String capability : List.of("RAG", "TOOL", "HISTORY", "COMPOSITE")) {
            String id = "missing-" + capability;
            var handle = enforced.bridgeExecution(request(id, capability), voucher, "original",
                    List.of(), List.of(), null, List.of());
            assertTrue(handle.failClosed(), capability);
            assertFalse(enforced.getLatestReceipt(id).orElseThrow().preparedSuccess(), capability);
        }
        var malformed = request("malformed", "RAG").toBuilder()
                .setInputParams("{\"capabilityTag\":\"RAG\",\"secret\":\"private-token\"")
                .build();
        var rejected = enforced.bridgeExecution(malformed, voucher, "original",
                List.of(), List.of(), null, List.of());
        assertTrue(rejected.failClosed());
        assertFalse(rejected.failCloseReason().contains("private-token"));
    }

    @Test
    void shouldEnforceHoareToolGuardAndLocallyDenseCycleAutomatonInEnforceMode() {
        HarnessKernelBridge enforced = bridge(KernelBridgeMode.ENFORCE);
        AtomicInteger calls = new AtomicInteger();
        ToolCallback original = tool(calls);
        var handle = enforced.bridgeExecution(request("guard", "TOOL"), voucher, "original",
                List.of(), List.of(original), name -> original, List.of());
        assertFalse(handle.failClosed());
        assertNotSame(original, handle.effectiveTools().getFirst());
        assertNull(handle.effectiveResolver().resolve("unlisted"));
        assertNotNull(handle.effectiveResolver().resolve("lookup"));
        ToolCallback guarded = handle.effectiveTools().getFirst();

        assertEquals("\"ok\"", guarded.call("{\"value\":\"valid\"}"));
        assertEquals(1, calls.get());
        assertTrue(guarded.call("{}").startsWith("[HARNESS_GUARD_REJECTED:"));
        assertEquals(1, calls.get(), "missing required argument must not reach the callback");
        for (int i = 0; i < voucher.guardrails().loopThreshold(); i++) {
            guarded.call("{\"value\":\"valid\"}");
        }
        assertTrue(calls.get() < voucher.guardrails().loopThreshold() + 1);
        enforced.completeExecution("guard", handle, 0.0);
        var receipt = enforced.getLatestReceipt("guard").orElseThrow();
        assertTrue(receipt.blockedToolInvocationCount() >= 2);
        assertTrue(receipt.trajectoryEvaluation().isPresent());
    }

    @Test
    void shouldIsolateCycleAutomatonAndColdBufferAcrossRequestsInEnforceMode() {
        HarnessKernelBridge enforced = bridge(KernelBridgeMode.ENFORCE);
        AtomicInteger calls = new AtomicInteger();
        ToolCallback original = tool(calls);
        ChatMessage history = ChatMessage.newBuilder().setRole("user").setContent("previous").build();
        RAGContext rag = RAGContext.newBuilder().setKnowledgeId("kb")
                .setPreRetrievedContent("evidence").build();
        var first = enforced.bridgeExecution(request("a", "COMPOSITE"), voucher, "prompt",
                List.of(rag), List.of(original), null, List.of(history));
        for (int i = 0; i <= voucher.guardrails().loopThreshold(); i++) {
            first.effectiveTools().getFirst().call("{\"value\":\"valid\"}");
        }
        var second = enforced.bridgeExecution(request("b", "COMPOSITE"), voucher, "prompt",
                List.of(rag), List.of(original), null, List.of(history));

        assertNotSame(first.session(), second.session());
        assertEquals(0, second.session().cycleState().frequency());
        assertEquals("\"ok\"", second.effectiveTools().getFirst().call("{\"value\":\"valid\"}"));
        assertEquals(List.of(1), List.copyOf(second.session().cycleState().counts().values()));
        assertEquals(first.session().coldBufferSnapshot(), second.session().coldBufferSnapshot());
        assertEquals(CapabilityTag.COMPOSITE, second.capabilityTag());
    }

    @Test
    void shouldResolveHistoryFromMemoryManagerWhenRequestHistoryListIsEmpty() {
        HarnessKernelBridge enforced = bridge(KernelBridgeMode.ENFORCE);
        ChatRequest request = request("memory-history", "HISTORY");
        assertTrue(request.getHistoryList().isEmpty());
        List<ChatMessage> history = enforced.resolveEffectiveHistory(request,
                List.of(new UserMessage("previous question")));

        assertEquals("previous question", history.getFirst().getContent());
        var handle = enforced.bridgeExecution(request, voucher, "prompt",
                List.of(), List.of(), null, history);
        assertFalse(handle.failClosed());
        assertEquals(CapabilityTag.HISTORY, handle.capabilityTag());
    }

    @Test
    void ragAndHistoryWithoutToolsDoesNotImplyCompositeCapability() {
        HarnessKernelBridge enforced = bridge(KernelBridgeMode.ENFORCE);
        ChatRequest request = ChatRequest.newBuilder().setRequestId("inferred-rag")
                .setBotId(7L).setQuestion("question").build();
        RAGContext rag = RAGContext.newBuilder().setKnowledgeId("kb")
                .setPreRetrievedContent("evidence").build();
        ChatMessage history = ChatMessage.newBuilder().setRole("user")
                .setContent("previous question").build();

        assertEquals(CapabilityTag.RAG,
                enforced.resolveCapabilityTag(request, List.of(rag), List.of(), null, List.of(history)));
    }

    private ToolCallback tool(AtomicInteger calls) {
        return FunctionToolCallback.builder("lookup", (Input input) -> {
            calls.incrementAndGet();
            return "ok";
        }).inputType(Input.class).description("Lookup").build();
    }

    record Input(String value) {}
}
