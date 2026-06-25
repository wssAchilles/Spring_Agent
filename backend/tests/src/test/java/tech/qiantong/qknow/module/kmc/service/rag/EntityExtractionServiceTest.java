package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import tech.qiantong.qknow.ai.transformer.RecursiveSplitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EntityExtractionServiceTest {

    @Test
    void extractsLlmJsonOnlyFromParentAndCopiesToChildren() {
        EntityExtractionService service = new EntityExtractionService();
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response("""
                {"entities":["qKnow","RAG"],"relations":[{"source":"qKnow","relation":"uses","target":"RAG","evidence":"qKnow RAG 架构"}]}
                """));
        Document parent = Document.builder()
                .id("p1")
                .text("Day01 介绍 qKnow RAG 架构和 Agent 编排。")
                .metadata(new HashMap<>(Map.of(RecursiveSplitter.METADATA_CHUNK_LEVEL, RecursiveSplitter.CHUNK_LEVEL_PARENT)))
                .build();
        Document child = Document.builder()
                .id("c1")
                .text("RAG 架构")
                .metadata(new HashMap<>(Map.of(
                        RecursiveSplitter.METADATA_CHUNK_LEVEL, RecursiveSplitter.CHUNK_LEVEL_CHILD,
                        RecursiveSplitter.METADATA_PARENT_SEGMENT_ID, "p1")))
                .build();

        List<Document> enriched = service.enrichParentChildMetadata(List.of(parent, child), chatModel);

        assertTrue(enriched.get(0).getMetadata().containsKey("entities"));
        assertEquals(List.of("qKnow", "RAG"), enriched.get(0).getMetadata().get("entities"));
        assertEquals(enriched.get(0).getMetadata().get("entities"), enriched.get(1).getMetadata().get("entities"));
        assertTrue(enriched.get(1).getMetadata().containsKey("relations"));
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void parsesFencedLlmJson() {
        EntityExtractionService service = new EntityExtractionService();
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response("""
                ```json
                {"entities":["DeepSeek","CRAG"],"relations":[{"source":"CRAG","relation":"evaluates","target":"retrieval","evidence":"CRAG evaluator"}]}
                ```
                """));

        Document parent = parent("p1", "CRAG evaluator uses DeepSeek.");

        service.enrichParentChildMetadata(List.of(parent), chatModel);

        assertEquals(List.of("DeepSeek", "CRAG"), parent.getMetadata().get("entities"));
    }

    @Test
    void fallsBackToRegexWhenLlmJsonInvalid() {
        EntityExtractionService service = new EntityExtractionService();
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response("not json"));

        Document parent = parent("p1", "Day01 介绍 qKnow RAG 架构。");

        service.enrichParentChildMetadata(List.of(parent), chatModel);

        @SuppressWarnings("unchecked")
        List<String> entities = (List<String>) parent.getMetadata().get("entities");
        assertTrue(entities.contains("Day01"));
        assertTrue(entities.contains("qKnow"));
    }

    @Test
    void usesRegexWhenNoChatModelProvided() {
        EntityExtractionService service = new EntityExtractionService();
        Document parent = parent("p1", "Day01 介绍 qKnow RAG 架构。");

        service.enrichParentChildMetadata(List.of(parent));

        assertTrue(parent.getMetadata().containsKey("entities"));
    }

    private Document parent(String id, String text) {
        return Document.builder()
                .id(id)
                .text(text)
                .metadata(new HashMap<>(Map.of(RecursiveSplitter.METADATA_CHUNK_LEVEL, RecursiveSplitter.CHUNK_LEVEL_PARENT)))
                .build();
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
