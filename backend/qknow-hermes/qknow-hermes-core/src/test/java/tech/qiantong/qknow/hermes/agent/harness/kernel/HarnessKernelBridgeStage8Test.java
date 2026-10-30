package tech.qiantong.qknow.hermes.agent.harness.kernel;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceCollector;
import tech.qiantong.qknow.hermes.agent.harness.HarnessUsageSnapshot;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.RagAttributionStatus;
import tech.qiantong.qknow.hermes.agent.harness.UsageStatus;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.RAGContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HarnessKernelBridgeStage8Test {
    private final HermesHarnessAssembler assembler = new HermesHarnessAssembler();

    private HarnessKernelBridge bridge() {
        return new HarnessKernelBridge(new HermesHarnessKernel(8, 4, 8, 8, 3), assembler,
                true, KernelBridgeMode.ENFORCE);
    }

    @Test
    void appliesPreparedContextAndPropagatesRagAndUsage() {
        var voucher = assembler.createDefaultProductionVoucher(7L);
        var request = ChatRequest.newBuilder().setRequestId("bridge-context").setBotId(7L)
                .setQuestion("question").setInputParams("{\"capabilityTag\":\"COMPOSITE\"}").build();
        var rag = RAGContext.newBuilder().setKnowledgeId("kb").setPreRetrievedContent("evidence").build();
        var history = ChatMessage.newBuilder().setRole("user").setContent("previous").build();
        var calls = new AtomicInteger();
        ToolCallback tool = tool(calls);
        var usage = new HarnessUsageSnapshot(UsageStatus.KNOWN, 120, 40, 160, 0, 0, 0.01, 1, 0);
        var collector = new HarnessEvidenceCollector(request.getRequestId(), 7L, voucher);
        var bridge = bridge();

        var handle = bridge.bridgeExecution(request, voucher, "prompt", List.of(rag), List.of(tool),
                name -> tool, List.of(history), collector, () -> usage);
        assertFalse(handle.failClosed());
        assertEquals(CapabilityTag.COMPOSITE, handle.effectiveCapabilityTag());
        assertEquals(handle.capabilityContext().ragContexts(), handle.effectiveRequest().getRagContextsList());
        assertEquals(List.of("lookup"), handle.effectiveRequest().getToolMethodIdsList());
        assertEquals(List.of(history), handle.effectiveRequest().getHistoryList());
        assertNull(handle.effectiveResolver().resolve("unlisted"));
        assertEquals("\"ok\"", handle.effectiveTools().getFirst().call("{\"value\":\"yes\"}"));
        assertEquals(1, calls.get());
        bridge.completeExecution(request.getRequestId(), handle, 4, 7, 2, 1, 0.8);
        var receipt = bridge.getLatestReceipt(request.getRequestId()).orElseThrow();
        assertEquals(UsageStatus.KNOWN, receipt.usageStatus());
        assertEquals(RagAttributionStatus.PARTIALLY_USED, receipt.ragAttribution().status());
        assertEquals(0.8, receipt.ragAttribution().ragScore());
        assertEquals(1, receipt.guardedToolInvocationCount());
        assertEquals(1, collector.toolExecutionsSnapshot().stream().filter(entry -> entry.success()).count());
    }

    @Test
    void rejectsWithoutCallingDelegateAndRecordsBlockedEvidence() {
        var voucher = assembler.createDefaultProductionVoucher(7L);
        var request = ChatRequest.newBuilder().setRequestId("bridge-block").setBotId(7L)
                .setInputParams("{\"capabilityTag\":\"TOOL\"}").build();
        var calls = new AtomicInteger();
        ToolCallback tool = tool(calls);
        var collector = new HarnessEvidenceCollector(request.getRequestId(), 7L, voucher);
        var bridge = bridge();

        var handle = bridge.bridgeExecution(request, voucher, "prompt", List.of(), List.of(tool),
                name -> tool, List.of(), collector, HarnessUsageSnapshot::unknown);
        assertTrue(handle.effectiveTools().getFirst().call("{}").startsWith("[HARNESS_GUARD_REJECTED:"));
        assertEquals(0, calls.get());
        assertFalse(collector.toolExecutionsSnapshot().getFirst().success());
        assertTrue(collector.toolExecutionsSnapshot().getFirst().guardBlocked());
        bridge.completeExecution(request.getRequestId(), handle, 0, 0, 0, 0, 0);
        var receipt = bridge.getLatestReceipt(request.getRequestId()).orElseThrow();
        assertEquals(UsageStatus.UNKNOWN, receipt.usageStatus());
        assertEquals(RagAttributionStatus.NO_RAG, receipt.ragAttribution().status());
        assertEquals(1, receipt.guardedToolInvocationCount());
        assertEquals(1, receipt.blockedToolInvocationCount());
    }

    private ToolCallback tool(AtomicInteger calls) {
        return FunctionToolCallback.builder("lookup", (Input input) -> {
            calls.incrementAndGet();
            return "ok";
        }).inputType(Input.class).description("Lookup").build();
    }

    record Input(String value) {}
}
