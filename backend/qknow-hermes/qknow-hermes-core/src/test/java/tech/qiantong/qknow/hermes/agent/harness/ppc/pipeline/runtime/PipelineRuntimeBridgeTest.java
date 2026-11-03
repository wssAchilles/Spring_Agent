package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;
import tech.qiantong.qknow.hermes.agent.harness.kernel.HarnessKernelBridge;
import tech.qiantong.qknow.hermes.agent.harness.kernel.HermesHarnessKernel;
import tech.qiantong.qknow.hermes.agent.harness.kernel.KernelBridgeMode;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.BoundedOperatorPipeline;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.hermes.proto.OrchestrationMode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PipelineRuntimeBridgeTest {
    private static final BoundedOperatorPipeline HISTORY_PIPELINE = new BoundedOperatorPipeline(
            List.of(Operator.ADAPTIVE_MEMORY_WINDOW));

    @Test
    void defaultSingleAndShadowUseTheSameModelInputWhileEnforceUsesThePipeline() {
        Observation off = execute("off", null);
        PipelineRuntimeBridge shadowBridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.SHADOW);
        Observation shadow = execute("shadow", shadowBridge);
        PipelineRuntimeBridge enforceBridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE);
        Observation enforce = execute("enforce", enforceBridge);

        assertEquals(off.systemPrompt(), shadow.systemPrompt());
        assertEquals(off.historyCount(), shadow.historyCount());
        assertEquals(20, off.historyCount());
        assertEquals(off.executedFingerprint(), shadow.executedFingerprint());
        assertTrue(off.receipt().isEmpty());
        var shadowReceipt = shadow.receipt().orElseThrow();
        assertEquals(PipelineRuntimeMode.SHADOW, shadowReceipt.mode());
        assertTrue(shadowReceipt.preparedSuccess());
        assertEquals("history-profile", shadowReceipt.profileId());
        assertEquals("1", shadowReceipt.profileVersion());
        assertEquals("MATCHED_CAPABILITY", shadowReceipt.routeReason());
        assertFalse(shadowReceipt.fallback());
        assertEquals(HISTORY_PIPELINE.fingerprint(), shadowReceipt.pipelineFingerprint());
        assertEquals(shadowReceipt.baselineFingerprint(), shadowReceipt.executedFingerprint());
        assertNotEquals(shadowReceipt.candidateFingerprint(), shadowReceipt.executedFingerprint());

        var enforceReceipt = enforce.receipt().orElseThrow();
        assertEquals(PipelineRuntimeMode.ENFORCE, enforceReceipt.mode());
        assertTrue(enforceReceipt.preparedSuccess());
        assertEquals(enforceReceipt.candidateFingerprint(), enforce.executedFingerprint());
        assertEquals(shadowReceipt.candidateFingerprint(), enforceReceipt.candidateFingerprint());
        assertEquals(shadowReceipt.profileFingerprint(), enforceReceipt.profileFingerprint());
        assertEquals(shadowReceipt.routeFingerprint(), enforceReceipt.routeFingerprint());
        assertEquals(off.systemPrompt(), enforce.systemPrompt());
        assertEquals(19, enforce.historyCount());
        assertFalse(enforce.promptText().contains("history-01"));
        assertTrue(enforce.promptText().contains("history-20"));
    }

    @Test
    void promptOnlyEnforceChangesSystemPromptWithoutAddingHistory() {
        Observation off = execute("prompt-off", null, false);
        Observation enforce = execute("prompt-enforce", new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE), false);

        assertEquals(0, enforce.historyCount());
        assertNotEquals(off.systemPrompt(), enforce.systemPrompt());
        assertEquals("prompt-profile", enforce.receipt().orElseThrow().profileId());
    }

    @Test
    void disabledAndUnknownModesStayOff() {
        assertTrue(PipelineRuntimeBridge.disabledInstance().isOff());
        assertTrue(new PipelineRuntimeBridge(false, "ENFORCE").isOff());
        assertTrue(new PipelineRuntimeBridge(true, "unknown").isOff());
        var disabled = PipelineRuntimeBridge.disabledInstance();
        var baseline = new HermesHarnessAssembler().createRequestVoucher(7L, "role");
        var selection = disabled.prepare(
                new HarnessRoutingContext("off-direct", "DEFAULT", CapabilityTag.PROMPT_ONLY), baseline, false);
        assertSame(baseline, selection.effectiveVoucher());
        assertTrue(disabled.getLatestReceipt("off-direct").isEmpty());
    }

    @Test
    void unknownCapabilityFallsBackWithoutMutationInEnforce() {
        var bridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE);
        var baseline = new HermesHarnessAssembler().createRequestVoucher(7L, "role");
        var selection = bridge.prepare(new HarnessRoutingContext("unknown", "DEFAULT", null), baseline, false);

        assertSame(baseline, selection.effectiveVoucher());
        assertFalse(selection.failClosed());
        var receipt = bridge.getLatestReceipt("unknown").orElseThrow();
        assertEquals("baseline", receipt.profileId());
        assertTrue(receipt.fallback());
        assertEquals("UNKNOWN_CAPABILITY", receipt.routeReason());
        assertEquals(baseline.computeFingerprint(), receipt.executedFingerprint());
        assertFalse(receipt.routeFingerprint().isBlank());
    }

    @Test
    void toolProfileEnforceMutatesToolGuidanceAndRecordsRouteIdentity() {
        var bridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE);
        var baseline = new HermesHarnessAssembler().createRequestVoucher(7L, "role");
        var context = new HarnessRoutingContext("tool-enforce", "DEFAULT", CapabilityTag.TOOL);
        var selection = bridge.prepare(context, baseline, false);

        assertFalse(selection.failClosed());
        assertNotEquals(baseline.toolGuidance(), selection.effectiveVoucher().toolGuidance());
        var receipt = bridge.getLatestReceipt("tool-enforce").orElseThrow();
        assertEquals("tool-profile", receipt.profileId());
        assertEquals("MATCHED_CAPABILITY", receipt.routeReason());
        assertEquals(HarnessRouter.defaults().route(context).routeFingerprint(), receipt.routeFingerprint());
        assertEquals(selection.effectiveVoucher().computeFingerprint(), receipt.executedFingerprint());
    }

    @Test
    void shadowFallsBackAndEnforceFailsClosedBeforeModelCall() {
        var concise = new AlgebraicHarnessMutator().mutate(
                new HermesHarnessAssembler().createRequestVoucher(7L, "role"),
                Operator.CONCISE_SYSTEM_PROMPT);
        PipelineRuntimeBridge shadow = new PipelineRuntimeBridge(true, PipelineRuntimeMode.SHADOW);
        var shadowSelection = shadow.prepare(
                new HarnessRoutingContext("shadow-failure", "DEFAULT", CapabilityTag.PROMPT_ONLY), concise, false);
        assertSame(concise, shadowSelection.effectiveVoucher());
        assertFalse(shadowSelection.failClosed());
        assertFalse(shadow.getLatestReceipt("shadow-failure").orElseThrow().preparedSuccess());
        assertEquals("prompt-profile", shadow.getLatestReceipt("shadow-failure").orElseThrow().profileId());

        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);
        AgentOrchestrator orchestrator = new AgentOrchestrator(factory, null, null);
        PipelineRuntimeBridge enforce = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE);
        orchestrator.setPipelineRuntimeBridge(enforce);
        orchestrator.registerRequestVoucher("enforce-failure", concise);

        var events = orchestrator.chat(request("enforce-failure", false)).collectList().block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(event -> event.hasError() && event.getError().getCode() == 403));
        verifyNoInteractions(model);
        var receipt = enforce.getLatestReceipt("enforce-failure").orElseThrow();
        assertTrue(receipt.failClosed());
        assertFalse(receipt.preparedSuccess());
        assertEquals("PIPELINE_PREPARE_FAILED:IllegalStateException", receipt.failureCode());
        assertEquals(HarnessEvidenceRecord.ExecutionMode.FAIL_SAFE,
                orchestrator.getLatestEvidence("enforce-failure").executionMode());
    }

    @Test
    void activeKernelBridgeCannotOverridePipelineExecution() {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);
        AgentOrchestrator orchestrator = new AgentOrchestrator(factory, null, null);
        orchestrator.setPipelineRuntimeBridge(new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE));
        orchestrator.setHarnessKernelBridge(new HarnessKernelBridge(
                new HermesHarnessKernel(8, 4, 8, 8, 3), new HermesHarnessAssembler(),
                true, KernelBridgeMode.SHADOW));

        var events = orchestrator.chat(request("bridge-conflict")).collectList().block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(event -> event.hasError() && event.getError().getCode() == 403));
        verifyNoInteractions(model);
        var receipt = orchestrator.getPipelineRuntimeBridge().getLatestReceipt("bridge-conflict").orElseThrow();
        assertTrue(receipt.failClosed());
        assertEquals("PIPELINE_PREPARE_FAILED:IllegalStateException", receipt.failureCode());
    }

    private static Observation execute(String id, PipelineRuntimeBridge bridge) {
        return execute(id, bridge, true);
    }

    private static Observation execute(String id, PipelineRuntimeBridge bridge, boolean withHistory) {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("answer"))))));
        AgentOrchestrator orchestrator = new AgentOrchestrator(factory, null, null);
        if (bridge != null) {
            orchestrator.setPipelineRuntimeBridge(bridge);
        }
        ChatRequest request = request(id, withHistory);
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE, request.getOrchestrationMode());

        var events = orchestrator.chat(request).collectList().block();

        assertNotNull(events);
        assertTrue(events.stream().noneMatch(event -> event.hasError()));
        ArgumentCaptor<Prompt> captured = ArgumentCaptor.forClass(Prompt.class);
        verify(model, atLeastOnce()).stream(captured.capture());
        Prompt prompt = captured.getAllValues().getFirst();
        String systemPrompt = prompt.getInstructions().stream()
                .filter(SystemMessage.class::isInstance).map(message -> message.getText()).findFirst().orElseThrow();
        String promptText = prompt.getInstructions().stream()
                .map(message -> message.getText() == null ? "" : message.getText())
                .collect(java.util.stream.Collectors.joining("\n"));
        long historyCount = prompt.getInstructions().stream()
                .filter(message -> message.getText() != null && message.getText().startsWith("history-"))
                .count();
        var evidence = orchestrator.getLatestEvidence(id);
        assertNotNull(evidence);
        return new Observation(systemPrompt, promptText, historyCount, evidence.voucherFingerprint(),
                bridge == null ? java.util.Optional.empty() : bridge.getLatestReceipt(id));
    }

    private static ChatRequest request(String id) {
        return request(id, true);
    }

    private static ChatRequest request(String id, boolean withHistory) {
        ChatRequest.Builder builder = ChatRequest.newBuilder().setRequestId(id).setBotId(7L)
                .setQuestion("question").setSystemPrompt("role")
                .setModelConfig(ModelConfig.newBuilder().setPlatform("deepseek")
                        .setModelName("deepseek-flash").build());
        if (withHistory) {
            for (int i = 1; i <= 20; i++) {
                builder.addHistory(ChatMessage.newBuilder().setRole("user")
                        .setContent("history-%02d".formatted(i)).build());
            }
        }
        return builder.build();
    }

    private record Observation(String systemPrompt, String promptText, long historyCount,
                               String executedFingerprint, java.util.Optional<PipelineRuntimeBridge.Receipt> receipt) {
    }
}
