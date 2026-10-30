package tech.qiantong.qknow.hermes.agent.harness.kernel;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceCollector;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.RagAttributionStatus;
import tech.qiantong.qknow.hermes.agent.harness.RagAttributionSummary;
import tech.qiantong.qknow.hermes.agent.harness.UsageCapturingChatModel;
import tech.qiantong.qknow.hermes.agent.harness.UsageStatus;
import tech.qiantong.qknow.hermes.agent.harness.offline.DualBlindSelectionEvaluator;
import tech.qiantong.qknow.hermes.agent.harness.offline.EvidenceDiffExtractor;
import tech.qiantong.qknow.hermes.agent.harness.offline.OfflineBenchmarkTestCase;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.RAGContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HermesHarnessStage8PpcReadinessTest {
    private final HermesHarnessAssembler assembler = new HermesHarnessAssembler();

    private HarnessKernelBridge bridge() {
        return new HarnessKernelBridge(new HermesHarnessKernel(8, 4, 8, 8, 3), assembler,
                true, KernelBridgeMode.ENFORCE);
    }

    private ChatRequest request(String id, String capability) {
        return ChatRequest.newBuilder().setRequestId(id).setBotId(7L).setQuestion("question")
                .setInputParams("{\"capabilityTag\":\"" + capability + "\"}").build();
    }

    @Test
    void shouldCaptureRealUsageFromChatResponseAndMarkKnown() {
        ChatModel delegate = mock(ChatModel.class);
        Prompt prompt = new Prompt("much longer than 160 tokens");
        when(delegate.call(prompt)).thenReturn(new ChatResponse(List.of(),
                ChatResponseMetadata.builder().usage(new DefaultUsage(120, 40, 160)).build()));
        UsageCapturingChatModel metered = new UsageCapturingChatModel(delegate);
        metered.call(prompt);
        HarnessEvidenceCollector collector = new HarnessEvidenceCollector("metered", 7L, null);
        collector.setUsageSupplier(metered::snapshot);
        collector.recordCostAndTokens(999, 9.0);

        HarnessEvidenceRecord evidence = collector.finalizeEvidence();
        assertEquals(UsageStatus.KNOWN, evidence.usageStatus());
        assertEquals(160L, evidence.estimatedTokens());
    }

    @Test
    void shouldMarkUsageUnknownWhenModelOmitsUsageAndBlockPpcAdoption() {
        ChatModel delegate = mock(ChatModel.class);
        Prompt prompt = new Prompt("unmetered");
        when(delegate.call(prompt)).thenReturn(new ChatResponse(List.of()));
        UsageCapturingChatModel model = new UsageCapturingChatModel(delegate);
        model.call(prompt);
        HarnessEvidenceCollector candidate = new HarnessEvidenceCollector("candidate", 7L, null);
        candidate.setUsageSupplier(model::snapshot);
        candidate.recordCostAndTokens(999, 9.0);
        HarnessEvidenceRecord candidateEvidence = candidate.finalizeEvidence();
        HarnessEvidenceCollector baseline = new HarnessEvidenceCollector("baseline", 7L, null);
        baseline.setUsageSupplier(() -> new tech.qiantong.qknow.hermes.agent.harness.HarnessUsageSnapshot(
                UsageStatus.KNOWN, 120, 40, 160, 0, 0, 0.0, 1, 0));
        HarnessEvidenceRecord baselineEvidence = baseline.finalizeEvidence();
        var testCase = new OfflineBenchmarkTestCase("unknown-usage", OfflineBenchmarkTestCase.TaskCategory.KNOWLEDGE_QA,
                "question", Map.of(), List.of(), "", 0);
        var diff = EvidenceDiffExtractor.extract(testCase, baselineEvidence, candidateEvidence, true);

        assertEquals(UsageStatus.UNKNOWN, candidateEvidence.usageStatus());
        assertEquals(0L, candidateEvidence.estimatedTokens());
        assertFalse(diff.usageComparable());
        assertFalse(diff.satisfiesBudgetGate());
        assertFalse(diff.isEligibleForAdoption());
        AtomicInteger judgments = new AtomicInteger();
        var decision = new DualBlindSelectionEvaluator().evaluate(testCase, diff, "baseline", "candidate",
                (left, right) -> { judgments.incrementAndGet(); return "B"; });
        assertFalse(decision.candidateAdopted());
        assertEquals(DualBlindSelectionEvaluator.LABEL_BASELINE, decision.chosenCandidateId());
        assertEquals(0, judgments.get());
    }

    @Test
    void shouldInjectCapabilityContextIntoChatRequestAndDowngradeEmptyToPromptOnly() {
        var request = request("context", "COMPOSITE");
        var rag = RAGContext.newBuilder().setKnowledgeId("kb").setPreRetrievedContent("evidence").build();
        var history = ChatMessage.newBuilder().setRole("user").setContent("earlier").build();
        ToolCallback tool = tool(new AtomicInteger());
        var handle = bridge().bridgeExecution(request, assembler.createDefaultProductionVoucher(7L), "prompt",
                List.of(rag), List.of(tool), name -> tool, List.of(history));

        assertFalse(handle.failClosed());
        assertEquals(CapabilityTag.COMPOSITE, handle.effectiveCapabilityTag());
        assertEquals(List.of(rag), handle.effectiveRequest().getRagContextsList());
        assertEquals(List.of("lookup"), handle.effectiveRequest().getToolMethodIdsList());
        assertEquals(List.of(history), handle.effectiveRequest().getHistoryList());
        assertEquals(handle.effectiveRequest(), handle.capabilityContext().applyTo(handle.effectiveRequest().toBuilder()).build());

        var empty = bridge().bridgeExecution(request("empty", "PROMPT_ONLY"),
                assembler.createDefaultProductionVoucher(7L), "prompt",
                List.of(RAGContext.newBuilder().setPreRetrievedContent(" ").build()), List.of(), null, List.of());
        assertFalse(empty.failClosed());
        assertEquals(CapabilityTag.PROMPT_ONLY, empty.effectiveCapabilityTag());
        assertTrue(empty.effectiveRequest().getRagContextsList().isEmpty());
    }

    @Test
    void shouldUnifyGuardWithoutDoubleCountingAndMarkRejectedToolAsFailedAndBlocked() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallback tool = tool(calls);
        var request = request("guard", "TOOL");
        var collector = new HarnessEvidenceCollector(request.getRequestId(), 7L,
                assembler.createDefaultProductionVoucher(7L));
        var bridge = bridge();
        var handle = bridge.bridgeExecution(request, assembler.createDefaultProductionVoucher(7L), "prompt",
                List.of(), List.of(tool), name -> tool, List.of(), collector, null);

        assertFalse(handle.failClosed());
        assertNull(handle.effectiveResolver().resolve("unlisted"));
        assertTrue(handle.effectiveTools().getFirst().call("{}").startsWith("[HARNESS_GUARD_REJECTED:"));
        assertEquals(0, calls.get());
        bridge.completeExecution(request.getRequestId(), handle, 0.0);
        var receipt = bridge.getLatestReceipt(request.getRequestId()).orElseThrow();
        assertEquals(1, receipt.guardedToolInvocationCount());
        HarnessEvidenceRecord evidence = collector.finalizeEvidence();
        assertTrue(evidence.guardBlocked());
        assertEquals(2, evidence.guardBlockedCount());
        assertTrue(evidence.toolExecutions().stream().allMatch(entry -> !entry.success() && entry.guardBlocked()));
    }

    @Test
    void shouldPropagateRealRagAttributionAndDistinguishNoRagFromUsed() {
        var bridge = bridge();
        var voucher = assembler.createDefaultProductionVoucher(7L);
        var rag = RAGContext.newBuilder().setKnowledgeId("kb").setPreRetrievedContent("evidence").build();
        var noRag = bridge.bridgeExecution(request("no-rag", "PROMPT_ONLY"), voucher, "prompt",
                List.of(), List.of(), null, List.of());
        var unused = bridge.bridgeExecution(request("unused", "RAG"), voucher, "prompt",
                List.of(rag), List.of(), null, List.of());
        var used = bridge.bridgeExecution(request("used", "RAG"), voucher, "prompt",
                List.of(rag), List.of(), null, List.of());
        bridge.completeExecution("no-rag", noRag, 0, 0, 0, 0, 0.0);
        bridge.completeExecution("unused", unused, 0, 0, 1, 0, 0.8);
        bridge.completeExecution("used", used, 0, 0, 1, 1, 0.9);

        assertEquals(RagAttributionStatus.NO_RAG, bridge.getLatestReceipt("no-rag").orElseThrow().ragAttribution().status());
        assertEquals(RagAttributionStatus.UNUSED, bridge.getLatestReceipt("unused").orElseThrow().ragAttribution().status());
        assertEquals(1.0, bridge.getLatestReceipt("unused").orElseThrow().ragAttribution().ragUnderutilization());
        assertEquals(RagAttributionStatus.FULLY_USED, bridge.getLatestReceipt("used").orElseThrow().ragAttribution().status());
        assertEquals(0.0, bridge.getLatestReceipt("used").orElseThrow().ragAttribution().ragUnderutilization());
        assertEquals(0.9, bridge.getLatestReceipt("used").orElseThrow().ragAttribution().ragScore());
    }

    @Test
    void shouldProduceCompleteStage9PpcReadyEvidenceRecordWithValidDigest() {
        HarnessEvidenceCollector collector = new HarnessEvidenceCollector("ready", 7L, null);
        collector.recordCapabilityTag("RAG");
        collector.recordGuardBlockedToolExecution("lookup", 0, "PERMISSION", "{}", "rejected");
        collector.setRagAttributionSupplier(() -> RagAttributionSummary.of(2, 1, 0.7));
        HarnessEvidenceRecord record = collector.finalizeEvidence();
        String digest = record.computeDigest();

        assertEquals(UsageStatus.UNKNOWN, record.usageStatus());
        assertEquals("RAG", record.capabilityTag());
        assertTrue(record.guardBlocked());
        assertEquals(1, record.guardBlockedCount());
        assertEquals(RagAttributionStatus.PARTIALLY_USED, record.ragAttribution().status());
        assertTrue(record.verifyDigest(digest));
        assertFalse(record.verifyDigest("0".repeat(64)));
        var altered = new HarnessEvidenceRecord(record.evidenceId(), record.requestId(), record.botId(),
                record.voucherFingerprint(), record.voucherVersion(), record.timestamp(), record.userInputSummary(),
                record.ragDocumentCount(), record.ragTopScore(), record.toolExecutions(), record.estimatedTokens(),
                record.estimatedCostCny(), record.executionDurationMs(), record.finalOutputSummary(),
                record.executionMode(), record.usageStatus(), "PROMPT_ONLY", record.guardBlocked(),
                record.guardBlockedCount(), record.ragAttribution());
        assertFalse(altered.verifyDigest(digest));

        var legacy14 = new HarnessEvidenceRecord("old14", "ready", 7L, "NONE", "UNKNOWN", Instant.EPOCH,
                "question", 0, 0.0, record.toolExecutions(), 0, 0.0, 0, "answer");
        var legacy15 = new HarnessEvidenceRecord("old15", "ready", 7L, "NONE", "UNKNOWN", Instant.EPOCH,
                "question", 0, 0.0, record.toolExecutions(), 0, 0.0, 0, "answer",
                HarnessEvidenceRecord.ExecutionMode.DETERMINISTIC_TEST_DOUBLE);
        for (var legacy : List.of(legacy14, legacy15)) {
            assertEquals(UsageStatus.UNKNOWN, legacy.usageStatus());
            assertEquals("PROMPT_ONLY", legacy.capabilityTag());
            assertTrue(legacy.guardBlocked());
            assertEquals(1, legacy.guardBlockedCount());
            assertEquals(RagAttributionStatus.NO_RAG, legacy.ragAttribution().status());
            assertTrue(legacy.verifyDigest(legacy.computeDigest()));
        }
    }

    private ToolCallback tool(AtomicInteger calls) {
        return FunctionToolCallback.builder("lookup", (Input input) -> {
            calls.incrementAndGet();
            return "ok";
        }).inputType(Input.class).description("Lookup").build();
    }

    record Input(String value) {}
}
