package tech.qiantong.qknow.hermes.agent.harness.ppc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PpcOrchestratorOfflineTest {
    @TempDir Path temp;

    @Test
    void missingOrPlaceholderKeyBlocksWithoutCallingModel() throws Exception {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        PpcOrchestrator orchestrator = new PpcOrchestrator(factory, output());
        PpcRun noKey = orchestrator.run(null);
        PpcRun placeholder = orchestrator.run("your_key_placeholder_12345");

        assertEquals(PpcMode.EMPIRICAL_BLOCKED, noKey.mode());
        assertEquals(0, noKey.totalProbesExecuted());
        assertEquals(0, noKey.totalModelCallsExecuted());
        assertEquals(PpcMode.EMPIRICAL_BLOCKED, placeholder.mode());
        verifyNoInteractions(factory);
        assertTrue(Files.isRegularFile(output().resolve(noKey.runId()).resolve("checksums.sha256")));
    }

    @Test
    void testDoubleNeverBecomesRealPpcAndKeepsFixtureIdsSeparate() throws Exception {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), anyString(), anyString(), anyString())).thenReturn(model);
        when(model.stream(any(Prompt.class))).thenAnswer(invocation -> Flux.just(new ChatResponse(
                List.of(new Generation(new AssistantMessage("25:99:99 is invalid; eBPF reduces Sidecar latency; ACC-9901 5800; SHA-256 幂等键最多重试 3 次；单体架构对比 Sidecar 和 eBPF。"))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(100, 40, 140)).build())));
        PpcOrchestrator orchestrator = new PpcOrchestrator(factory, output(), true,
                PpcOrchestrator.DEFAULT_BUDGET);

        PpcRun run = orchestrator.runTestDouble();

        assertEquals(PpcMode.EMPIRICAL_BLOCKED, run.mode());
        assertEquals(15, run.totalProbesExecuted());
        assertEquals(5, run.probes().stream().filter(p -> p.candidateId().equals("baseline")).count());
        assertEquals(List.of("RAG", "TOOL", "PROMPT_ONLY", "HISTORY", "COMPOSITE"),
                run.probes().stream().filter(p -> p.candidateId().equals("baseline"))
                        .map(p -> p.effectiveCapabilityTag().name()).toList(),
                run.probes().stream().filter(p -> p.candidateId().equals("baseline"))
                        .map(p -> p.caseId() + ":" + p.evidence().finalOutputSummary()).toList().toString());
        assertTrue(run.probes().stream().allMatch(p -> p.executionMode()
                == HarnessEvidenceRecord.ExecutionMode.DETERMINISTIC_TEST_DOUBLE));
        assertTrue(run.probes().stream().allMatch(p -> p.evidence().executionMode()
                == HarnessEvidenceRecord.ExecutionMode.DETERMINISTIC_TEST_DOUBLE));
        assertEquals(List.of("chunk-pay-idem-01"), run.probes().getFirst().injectedRagChunkIds());
        assertTrue(run.probes().get(1).injectedRagChunkIds().isEmpty());
        assertTrue(Files.readString(output().resolve(run.runId()).resolve("checksums.sha256"))
                .contains("probes/baseline-kb-qa-01.json"));
    }

    @Test
    void unknownProviderUsageBlocksWithoutInventingCost() throws Exception {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), anyString(), anyString(), anyString())).thenReturn(model);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(
                List.of(new Generation(new AssistantMessage("unmetered answer"))))));

        PpcRun run = new PpcOrchestrator(factory, output(), true,
                PpcOrchestrator.DEFAULT_BUDGET).runTestDouble();

        assertEquals(PpcMode.EMPIRICAL_BLOCKED, run.mode());
        assertEquals(0, run.totalProbesExecuted());
        assertEquals(1, run.totalModelCallsExecuted());
        assertEquals(0.0D, run.totalCostCnyConsumed());
    }

    @Test
    void modelCallLimitKeepsCompletedProbeAndStopsNextNetworkCall() throws Exception {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), anyString(), anyString(), anyString())).thenReturn(model);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(
                List.of(new Generation(new AssistantMessage("幂等 SHA-256 最多重试 3 次"))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(100, 40, 140)).build())));
        PpcRun.Budget oneCall = new PpcRun.Budget(20, 1, 200_000L, 2_000, 10.0D);

        PpcRun run = new PpcOrchestrator(factory, output(), true, oneCall).runTestDouble();

        assertEquals(PpcMode.BUDGET_EXHAUSTED, run.mode());
        assertEquals(1, run.totalModelCallsExecuted());
        assertEquals(1, run.totalProbesExecuted());
        assertTrue(Files.isRegularFile(output().resolve(run.runId())
                .resolve("probes/baseline-kb-qa-01.json")));
        verify(model, times(1)).stream(any(Prompt.class));
    }

    @Test
    void rejectsOutputOutsideDedicatedLiveDirectory() {
        assertThrows(IllegalArgumentException.class,
                () -> new PpcOrchestrator(mock(ChatModelFactory.class), temp.resolve("harness5")));
    }

    private Path output() {
        return temp.resolve("experiment/outputs/harness9-live");
    }
}
