package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.config.PlanSolveConfig;
import tech.qiantong.qknow.hermes.memory.MemoryManager;
import tech.qiantong.qknow.hermes.memory.ShortTermMemory;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentOrchestratorIdentityTest {

    private MemoryManager memoryManager;
    private ShortTermMemory shortTermMemory;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        memoryManager = mock(MemoryManager.class);
        shortTermMemory = mock(ShortTermMemory.class);
        when(memoryManager.getShortTerm()).thenReturn(shortTermMemory);
        orchestrator = new AgentOrchestrator(null, null, null, new PlanSolveConfig(),
                null, null, null, memoryManager, null);
    }

    @Test
    void conversationIdDefinesSessionWhileRequestIdRemainsPerRequest() {
        ChatRequest first = completeRequest("request-1");
        ChatRequest second = completeRequest("request-2");

        assertEquals("30", ReflectionTestUtils.invokeMethod(orchestrator, "memorySessionId", first));
        assertEquals("30", ReflectionTestUtils.invokeMethod(orchestrator, "memorySessionId", second));
        assertEquals("40", ReflectionTestUtils.invokeMethod(orchestrator, "memoryUserId", first));
        assertEquals("workspace:10:bot:20", ReflectionTestUtils.invokeMethod(orchestrator, "memoryScope", first));
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(orchestrator, "hasCompleteIdentity", first));
    }

    @Test
    void completeIdentityWritesConversationScopedMemory() {
        ChatRequest request = completeRequest("request-1");

        ReflectionTestUtils.invokeMethod(orchestrator, "recordUserMemory", request);
        ReflectionTestUtils.invokeMethod(orchestrator, "recordAssistantMemory", request, "answer");

        verify(shortTermMemory).addMessage(eq("30"), any(UserMessage.class));
        verify(shortTermMemory).addMessage(eq("30"), any(AssistantMessage.class));
        verify(shortTermMemory, times(2)).touchSession("30", "40", "workspace:10:bot:20");
    }

    @Test
    void shortTermHistoryIsReadBeforeCurrentQuestionIsRecorded() {
        ChatModelFactory chatModelFactory = mock(ChatModelFactory.class);
        RetrievalEvaluator retrievalEvaluator = mock(RetrievalEvaluator.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());
        when(chatModelFactory.getChatModel(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(chatModel);
        when(retrievalEvaluator.evaluate(anyString(), anyList(), any(), any()))
                .thenReturn(RetrievalEvaluation.correct("ok"));
        when(shortTermMemory.getContext("30", 20)).thenReturn(List.of());
        AgentOrchestrator executingOrchestrator = new AgentOrchestrator(
                chatModelFactory, mock(ToolCallbackResolver.class), retrievalEvaluator,
                new PlanSolveConfig(), null, null, null, memoryManager, null);
        ChatRequest request = completeRequest("request-order").toBuilder()
                .setModelConfig(ModelConfig.newBuilder()
                        .setPlatform("Ollama")
                        .setBaseUrl("http://localhost")
                        .setModelName("test-model")
                        .build())
                .build();

        executingOrchestrator.chat(request).collectList().block();

        InOrder order = inOrder(shortTermMemory);
        order.verify(shortTermMemory).getContext("30", 20);
        order.verify(shortTermMemory).addMessage(eq("30"), any(UserMessage.class));
    }

    @Test
    void incompleteIdentitySkipsMemoryReadsAndWrites() {
        ChatRequest complete = completeRequest("request-1");
        List<ChatRequest> incompleteRequests = List.of(
                complete.toBuilder().clearRequestId().build(),
                complete.toBuilder().clearWorkspaceId().build(),
                complete.toBuilder().clearBotId().build(),
                complete.toBuilder().clearUserId().build(),
                complete.toBuilder().clearConversationId().build());

        for (ChatRequest request : incompleteRequests) {
            assertFalse((Boolean) ReflectionTestUtils.invokeMethod(orchestrator, "hasCompleteIdentity", request));
            ReflectionTestUtils.invokeMethod(orchestrator, "recordUserMemory", request);
            ReflectionTestUtils.invokeMethod(orchestrator, "recordAssistantMemory", request, "answer");
        }

        verifyNoInteractions(shortTermMemory);
    }

    private ChatRequest completeRequest(String requestId) {
        return ChatRequest.newBuilder()
                .setRequestId(requestId)
                .setWorkspaceId(10L)
                .setBotId(20L)
                .setUserId(40L)
                .setConversationId(30L)
                .setQuestion("question")
                .build();
    }
}
