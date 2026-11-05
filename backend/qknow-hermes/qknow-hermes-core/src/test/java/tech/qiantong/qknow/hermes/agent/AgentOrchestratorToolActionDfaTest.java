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
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction.ToolActionDfaMode;
import tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction.ToolActionDfaReason;
import tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction.ToolActionDfaRuntime;
import tech.qiantong.qknow.hermes.agent.orchestration.MultiAgentOrchestrationService;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.config.PlanSolveConfig;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.hermes.proto.OrchestrationMode;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorToolActionDfaTest {
    @Test
    void multiAgentEnforceFailsBeforePlannerOrWorkerExecution() {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);

        PlanSolveConfig planConfig = new PlanSolveConfig();
        planConfig.setEnabled(true);
        planConfig.setComplexKeywords(java.util.List.of("force-plan"));

        MultiAgentOrchestrationService orchestration = mock(MultiAgentOrchestrationService.class);
        when(orchestration.resolveOrchestrationMode(any(ChatRequest.class))).thenReturn(
                new MultiAgentOrchestrationService.ModeResolution(
                        OrchestrationMode.ORCHESTRATION_MODE_SWARM,
                        OrchestrationMode.ORCHESTRATION_MODE_SWARM,
                        false,
                        AgentOrchestrator.ERR_NONE));

        AgentOrchestrator orchestrator = new AgentOrchestrator(factory, null, null, planConfig,
                null, null, null, null, null, orchestration);
        orchestrator.setToolActionDfaRuntime(ToolActionDfaRuntime.defaults());
        orchestrator.setToolActionDfaMode(ToolActionDfaMode.ENFORCE, "fixture-read-then-write");
        ChatRequest request = ChatRequest.newBuilder().setRequestId("multiagent-enforce")
                .setBotId(1L).setQuestion("force-plan: compare the two reports")
                .setSystemPrompt("Answer carefully")
                .setModelConfig(ModelConfig.newBuilder().setPlatform("test")
                        .setModelName("deterministic-test").build())
                .setOrchestrationMode(OrchestrationMode.ORCHESTRATION_MODE_SWARM)
                .build();

        var events = orchestrator.chat(request).collectList().block();

        assertEquals(403, events.getFirst().getError().getCode());
        assertEquals("DFA_PATH_UNCOVERED:MULTIAGENT", events.getFirst().getError().getMessage());
        assertEquals(ToolActionDfaReason.DFA_PATH_UNCOVERED,
                orchestrator.getLatestToolActionDfaReceipts("multiagent-enforce").getFirst().reason());
        verify(model, never()).call(any(Prompt.class));
        verify(model, never()).stream(any(Prompt.class));
        verify(orchestration, never()).executeIfEnabled(any(), anyString(), anyString(), any(), any(),
                any(), any(), anyBoolean());
    }

    @Test
    void reactDispatchesToolCallsThroughDfaInterceptor() {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);
        AtomicBoolean emitToolCall = new AtomicBoolean(true);
        when(model.stream(any(Prompt.class))).thenAnswer(invocation -> {
            if (!emitToolCall.get()) {
                return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("off complete")))));
            }
            Prompt prompt = invocation.getArgument(0);
            boolean toolReturned = prompt.getInstructions().stream()
                    .anyMatch(ToolResponseMessage.class::isInstance);
            AssistantMessage output = toolReturned ? new AssistantMessage("react complete")
                    : AssistantMessage.builder().content("")
                    .toolCalls(List.of(new ToolCall("react-call-1", "function", "fixture.read", "{}")))
                    .build();
            return Flux.just(new ChatResponse(List.of(new Generation(output))));
        });
        AtomicInteger executions = new AtomicInteger();
        ToolCallback tool = callback(executions);
        ToolCallbackResolver resolver = name -> "fixture.read".equals(name) ? tool : null;
        AgentOrchestrator orchestrator = new AgentOrchestrator(factory, resolver, null);
        orchestrator.setToolActionDfaMode(ToolActionDfaMode.ENFORCE, "fixture-read-then-write");
        ChatRequest request = ChatRequest.newBuilder().setRequestId("react-dfa-dispatch")
                .setBotId(1L).setQuestion("read fixture")
                .setSystemPrompt("Answer from the fixture tool")
                .addToolMethodIds("fixture.read")
                .setModelConfig(ModelConfig.newBuilder().setPlatform("test")
                        .setModelName("deterministic-test").build())
                .setOrchestrationMode(OrchestrationMode.ORCHESTRATION_MODE_SINGLE)
                .build();

        var events = orchestrator.chat(request).collectList().block();

        assertFalse(events.isEmpty());
        assertEquals(1, executions.get());
        var receipts = orchestrator.getLatestToolActionDfaReceipts(request.getRequestId());
        assertEquals(1, receipts.size());
        assertEquals("tool:react-call-1", receipts.getFirst().toolCallId());
        assertTrue(receipts.getFirst().advanced());

        emitToolCall.set(false);
        orchestrator.setToolActionDfaMode(ToolActionDfaMode.OFF, "");
        orchestrator.chat(request).collectList().block();

        assertTrue(orchestrator.getLatestToolActionDfaReceipts(request.getRequestId()).isEmpty());
    }

    private static ToolCallback callback(AtomicInteger executions) {
        return new ToolCallback() {
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
    }
}
