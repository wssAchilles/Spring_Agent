package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    @Test
    void offAndShadowDoNotCreateSessionBindings() {
        HarnessSessionBinder binder = new HarnessSessionBinder();
        var baseline = new HermesHarnessAssembler().createRequestVoucher(7L, "role");
        var off = new PipelineRuntimeBridge(false, PipelineRuntimeMode.ENFORCE,
                HarnessRouter.defaults(), binder);
        assertSame(baseline, off.prepare(null, baseline, false).effectiveVoucher());
        assertTrue(off.getLatestReceipt("off").isEmpty());

        var shadow = new PipelineRuntimeBridge(true, PipelineRuntimeMode.SHADOW,
                HarnessRouter.defaults(), binder);
        var context = sessionContext("shadow", "session-a", CapabilityTag.HISTORY);
        assertSame(baseline, shadow.prepare(context, baseline, false).effectiveVoucher());
        assertEquals(0, binder.size());
        var receipt = shadow.getLatestReceipt("shadow").orElseThrow();
        assertEquals("session-a", receipt.sessionId());
        assertEquals(CapabilityTag.HISTORY, receipt.capabilityTag());
        assertEquals("DEFAULT", receipt.domain());
        assertEquals(HarnessProfileStatus.ACTIVE, receipt.profileStatus());
        assertEquals(HarnessBindingAction.NONE, receipt.bindingAction());
        assertEquals(baseline.computeFingerprint(), receipt.executedFingerprint());
    }

    @Test
    void enforcePinsAndReusesWithinSessionCapabilityAndSeparatesOtherKeys() {
        HarnessSessionBinder binder = new HarnessSessionBinder();
        var bridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                HarnessRouter.defaults(), binder);
        var baseline = new HermesHarnessAssembler().createRequestVoucher(7L, "role");
        var historyKey = new HarnessSessionKey("session-a", CapabilityTag.HISTORY, "DEFAULT");

        bridge.prepare(sessionContext("h1", "session-a", CapabilityTag.HISTORY), baseline, false);
        var first = bridge.getLatestReceipt("h1").orElseThrow();
        assertEquals("history-profile", first.profileId());
        assertEquals(HarnessBindingAction.PINNED, first.bindingAction());
        assertEquals(first.profileFingerprint(), binder.lookup(historyKey).binding().orElseThrow().profileFingerprint());

        bridge.prepare(sessionContext("h2", "session-a", CapabilityTag.HISTORY), baseline, false);
        var reused = bridge.getLatestReceipt("h2").orElseThrow();
        assertEquals(HarnessBindingAction.REUSED, reused.bindingAction());
        assertEquals(first.profileFingerprint(), reused.profileFingerprint());
        assertEquals("SESSION_BINDING_REUSED", reused.routeReason());
        assertEquals(1, binder.size());

        bridge.prepare(sessionContext("tool", "session-a", CapabilityTag.TOOL), baseline, false);
        assertEquals("tool-profile", bridge.getLatestReceipt("tool").orElseThrow().profileId());
        assertEquals(HarnessBindingAction.PINNED, bridge.getLatestReceipt("tool").orElseThrow().bindingAction());
        bridge.prepare(sessionContext("other", "session-b", CapabilityTag.HISTORY), baseline, false);
        assertEquals(HarnessBindingAction.PINNED, bridge.getLatestReceipt("other").orElseThrow().bindingAction());
        assertEquals(3, binder.size());
    }

    @Test
    void absentSessionUsesRequestRouteWithoutBinding() {
        HarnessSessionBinder binder = new HarnessSessionBinder();
        var bridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                HarnessRouter.defaults(), binder);
        var baseline = new HermesHarnessAssembler().createRequestVoucher(7L, "role");

        bridge.prepare(new HarnessRoutingContext("request-only", null, "DEFAULT", CapabilityTag.TOOL),
                baseline, false);

        var receipt = bridge.getLatestReceipt("request-only").orElseThrow();
        assertNull(receipt.sessionId());
        assertEquals("tool-profile", receipt.profileId());
        assertEquals(HarnessBindingAction.NONE, receipt.bindingAction());
        assertEquals(0, binder.size());
    }

    @Test
    void unsupportedCapabilityWithSessionKeepsStage18FallbackReason() {
        HarnessSessionBinder binder = new HarnessSessionBinder();
        var bridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                HarnessRouter.defaults(), binder);
        var baseline = new HermesHarnessAssembler().createRequestVoucher(7L, "role");

        bridge.prepare(sessionContext("rag-fallback", "session", CapabilityTag.RAG), baseline, false);

        var receipt = bridge.getLatestReceipt("rag-fallback").orElseThrow();
        assertEquals("baseline", receipt.profileId());
        assertEquals("NO_APPROVED_ROUTE", receipt.routeReason());
        assertEquals(HarnessBindingAction.FALLBACK, receipt.bindingAction());
        assertEquals(0, binder.size());
    }

    @Test
    void withdrawnRouteCannotReuseAnExistingSessionBinding() {
        HarnessSessionBinder binder = new HarnessSessionBinder();
        var baseline = new HermesHarnessAssembler().createRequestVoucher(7L, "role");
        var initial = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                HarnessRouter.defaults(), binder);
        initial.prepare(sessionContext("initial", "session", CapabilityTag.HISTORY), baseline, false);

        var withdrawn = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                new HarnessRouter(HarnessProfileRegistry.defaults(), Map.of()), binder);
        var selected = withdrawn.prepare(sessionContext("withdrawn", "session", CapabilityTag.HISTORY),
                baseline, false);

        assertSame(baseline, selected.effectiveVoucher());
        var receipt = withdrawn.getLatestReceipt("withdrawn").orElseThrow();
        assertEquals("NO_APPROVED_ROUTE", receipt.routeReason());
        assertEquals(HarnessBindingAction.FALLBACK, receipt.bindingAction());
        assertTrue(receipt.fallback());
        assertTrue(binder.lookup(new HarnessSessionKey("session", CapabilityTag.HISTORY, "DEFAULT"))
                .binding().isEmpty());
    }

    @Test
    void deprecatedProfileServesExistingBindingUntilTtlButNotNewSessions() {
        var clock = new HarnessSessionBinderTest.MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HarnessSessionBinder binder = new HarnessSessionBinder(clock, Duration.ofMinutes(30), 8);
        var baseline = new HermesHarnessAssembler().createRequestVoucher(7L, "role");
        var active = historyProfile("1", HarnessProfileStatus.ACTIVE);
        var deprecated = historyProfile("1", HarnessProfileStatus.DEPRECATED);
        var activeBridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                historyRouter(active), binder);
        activeBridge.prepare(sessionContext("initial", "session", CapabilityTag.HISTORY), baseline, false);
        var original = activeBridge.getLatestReceipt("initial").orElseThrow();
        assertEquals(HarnessBindingAction.PINNED, original.bindingAction());

        var deprecatedBridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                historyRouter(deprecated), binder);
        var reused = deprecatedBridge.prepare(
                sessionContext("existing", "session", CapabilityTag.HISTORY), baseline, false);
        var receipt = deprecatedBridge.getLatestReceipt("existing").orElseThrow();
        assertFalse(reused.failClosed());
        assertEquals("history-profile", receipt.profileId());
        assertEquals(HarnessProfileStatus.DEPRECATED, receipt.profileStatus());
        assertEquals(HarnessBindingAction.REUSED, receipt.bindingAction());
        assertEquals("DEPRECATED_EXISTING_ONLY", receipt.routeReason());
        assertEquals(1, binder.size());

        deprecatedBridge.prepare(sessionContext("new", "new-session", CapabilityTag.HISTORY), baseline, false);
        var fresh = deprecatedBridge.getLatestReceipt("new").orElseThrow();
        assertEquals("baseline", fresh.profileId());
        assertEquals("PROFILE_DEPRECATED", fresh.routeReason());
        assertTrue(fresh.fallback());
        assertEquals(1, binder.size());

        clock.advance(Duration.ofMinutes(30));
        deprecatedBridge.prepare(sessionContext("expired", "session", CapabilityTag.HISTORY), baseline, false);
        var expired = deprecatedBridge.getLatestReceipt("expired").orElseThrow();
        assertEquals("baseline", expired.profileId());
        assertTrue(expired.fallback());
        assertTrue(binder.lookup(new HarnessSessionKey("session", CapabilityTag.HISTORY, "DEFAULT"))
                .binding().isEmpty());
    }

    @Test
    void retiredBindingImmediatelyRebindsToActiveReplacement() {
        HarnessSessionBinder binder = new HarnessSessionBinder();
        var baseline = new HermesHarnessAssembler().createRequestVoucher(7L, "role");
        HarnessProfile original = historyProfile("1", HarnessProfileStatus.ACTIVE);
        HarnessProfile replacement = new HarnessProfile("history-v2", "2", "DEFAULT", CapabilityTag.HISTORY,
                HISTORY_PIPELINE, true, "TEST", HarnessProfileStatus.ACTIVE);
        var initialBridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                historyRouter(original), binder);
        initialBridge.prepare(sessionContext("initial", "session", CapabilityTag.HISTORY), baseline, false);

        HarnessRouter nextRouter = new HarnessRouter(new HarnessProfileRegistry(List.of(
                HarnessProfileRegistry.defaults().defaultProfile(),
                historyProfile("1", HarnessProfileStatus.RETIRED), replacement)),
                Map.of(CapabilityTag.HISTORY, replacement.profileId()));
        var nextBridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE, nextRouter, binder);
        nextBridge.prepare(sessionContext("next", "session", CapabilityTag.HISTORY), baseline, false);

        var receipt = nextBridge.getLatestReceipt("next").orElseThrow();
        assertEquals("history-v2", receipt.profileId());
        assertEquals("2", receipt.profileVersion());
        assertEquals(HarnessBindingAction.REBOUND, receipt.bindingAction());
        assertEquals(replacement.fingerprint(), binder.lookup(
                new HarnessSessionKey("session", CapabilityTag.HISTORY, "DEFAULT"))
                .binding().orElseThrow().profileFingerprint());
    }

    @Test
    void retiredBindingWithoutReplacementFallsBackBaseline() {
        HarnessSessionBinder binder = new HarnessSessionBinder();
        var baseline = new HermesHarnessAssembler().createRequestVoucher(7L, "role");
        var initialBridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                historyRouter(historyProfile("1", HarnessProfileStatus.ACTIVE)), binder);
        initialBridge.prepare(sessionContext("initial", "session", CapabilityTag.HISTORY), baseline, false);

        var retiredBridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                historyRouter(historyProfile("1", HarnessProfileStatus.RETIRED)), binder);
        var selected = retiredBridge.prepare(sessionContext("retired", "session", CapabilityTag.HISTORY), baseline,
                false);

        assertSame(baseline, selected.effectiveVoucher());
        assertFalse(selected.failClosed());
        var receipt = retiredBridge.getLatestReceipt("retired").orElseThrow();
        assertEquals("baseline", receipt.profileId());
        assertTrue(receipt.fallback());
        assertEquals(HarnessBindingAction.FALLBACK, receipt.bindingAction());
        assertTrue(binder.lookup(new HarnessSessionKey("session", CapabilityTag.HISTORY, "DEFAULT"))
                .binding().isEmpty());
    }

    @Test
    void expiredBindingSelectsNewActiveVersion() {
        var clock = new HarnessSessionBinderTest.MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HarnessSessionBinder binder = new HarnessSessionBinder(clock, Duration.ofMinutes(30), 8);
        var baseline = new HermesHarnessAssembler().createRequestVoucher(7L, "role");
        var initialBridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                historyRouter(historyProfile("1", HarnessProfileStatus.ACTIVE)), binder);
        initialBridge.prepare(sessionContext("initial", "session", CapabilityTag.HISTORY), baseline, false);
        clock.advance(Duration.ofMinutes(30));

        var versionTwo = historyProfile("2", HarnessProfileStatus.ACTIVE);
        var nextBridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                historyRouter(versionTwo), binder);
        nextBridge.prepare(sessionContext("expired", "session", CapabilityTag.HISTORY), baseline, false);

        var receipt = nextBridge.getLatestReceipt("expired").orElseThrow();
        assertEquals(HarnessBindingAction.EXPIRED, receipt.bindingAction());
        assertEquals("2", receipt.profileVersion());
        assertEquals(versionTwo.fingerprint(), receipt.profileFingerprint());
        assertEquals("BINDING_EXPIRED", receipt.routeReason());
    }

    @Test
    void failedEnforcePreparationDoesNotLeaveAReusableBinding() {
        HarnessSessionBinder binder = new HarnessSessionBinder();
        var bridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                HarnessRouter.defaults(), binder);
        var alreadyConcise = new AlgebraicHarnessMutator().mutate(
                new HermesHarnessAssembler().createRequestVoucher(7L, "role"),
                Operator.CONCISE_SYSTEM_PROMPT);
        var context = sessionContext("failed", "session", CapabilityTag.PROMPT_ONLY);

        var selection = bridge.prepare(context, alreadyConcise, false);

        assertTrue(selection.failClosed());
        assertFalse(bridge.getLatestReceipt("failed").orElseThrow().preparedSuccess());
        assertTrue(binder.lookup(new HarnessSessionKey("session", CapabilityTag.PROMPT_ONLY, "DEFAULT"))
                .binding().isEmpty());
    }

    @Test
    void orchestratorCarriesConversationIdIntoSessionRouting() {
        HarnessSessionBinder binder = new HarnessSessionBinder();
        var bridge = new PipelineRuntimeBridge(true, PipelineRuntimeMode.ENFORCE,
                HarnessRouter.defaults(), binder);

        Observation first = execute("conversation-first", bridge, true, 72L);
        Observation second = execute("conversation-second", bridge, true, 72L);

        assertEquals(19, first.historyCount());
        assertEquals(19, second.historyCount());
        assertEquals("72", first.receipt().orElseThrow().sessionId());
        assertEquals(HarnessBindingAction.PINNED, first.receipt().orElseThrow().bindingAction());
        assertEquals(HarnessBindingAction.REUSED, second.receipt().orElseThrow().bindingAction());
        assertEquals(1, binder.size());
    }

    private static HarnessRoutingContext sessionContext(String requestId, String sessionId, CapabilityTag tag) {
        return new HarnessRoutingContext(requestId, sessionId, "DEFAULT", tag);
    }

    private static HarnessProfile historyProfile(String version, HarnessProfileStatus status) {
        return new HarnessProfile("history-profile", version, "DEFAULT", CapabilityTag.HISTORY,
                HISTORY_PIPELINE, true, "TEST", status);
    }

    private static HarnessRouter historyRouter(HarnessProfile history) {
        return new HarnessRouter(new HarnessProfileRegistry(List.of(
                HarnessProfileRegistry.defaults().defaultProfile(), history)),
                Map.of(CapabilityTag.HISTORY, history.profileId()));
    }

    private static Observation execute(String id, PipelineRuntimeBridge bridge) {
        return execute(id, bridge, true);
    }

    private static Observation execute(String id, PipelineRuntimeBridge bridge, boolean withHistory) {
        return execute(id, bridge, withHistory, 0L);
    }

    private static Observation execute(String id, PipelineRuntimeBridge bridge,
                                       boolean withHistory, long conversationId) {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("answer"))))));
        AgentOrchestrator orchestrator = new AgentOrchestrator(factory, null, null);
        if (bridge != null) {
            orchestrator.setPipelineRuntimeBridge(bridge);
        }
        ChatRequest request = request(id, withHistory).toBuilder()
                .setConversationId(conversationId).build();
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
