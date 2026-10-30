package tech.qiantong.qknow.hermes.agent.harness;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.hermes.proto.RAGContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOrchestratorRagAttributionTest {
    @Test
    void recordsRagSourceUsedInFinalAnswer() {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("ResearchKB supports alpha beta"))))));
        AgentOrchestrator orchestrator = new AgentOrchestrator(factory, null, null);
        ChatRequest request = ChatRequest.newBuilder().setRequestId("rag-source-used")
                .setBotId(7L).setQuestion("What does the source say?")
                .setSystemPrompt("Answer from sources")
                .setModelConfig(ModelConfig.newBuilder().setPlatform("deepseek")
                        .setModelName("deepseek-flash").build())
                .addRagContexts(RAGContext.newBuilder().setKnowledgeId("kb-1")
                        .setKnowledgeName("ResearchKB").setPreRetrievedContent("alpha beta evidence").build())
                .build();

        orchestrator.chat(request).collectList().block();

        HarnessEvidenceRecord evidence = orchestrator.getLatestEvidence(request.getRequestId());
        assertNotNull(evidence);
        assertEquals(RagAttributionStatus.FULLY_USED, evidence.ragAttribution().status());
        assertEquals(1, evidence.ragAttribution().ragProvided());
        assertEquals(1, evidence.ragAttribution().ragUsed());
        assertEquals(UsageStatus.UNKNOWN, evidence.usageStatus());
    }
}
