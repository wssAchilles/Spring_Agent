package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import tech.qiantong.qknow.ai.transformer.RecursiveSplitter;
import tech.qiantong.qknow.module.kmc.service.rag.EntityExtractionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("EntityExtractionService 实体抽取测试")
class EntityExtractionServiceTest {

    @Test
    @DisplayName("仅对 parent 分块执行实体抽取")
    void enrichParentChildMetadata_extractsOnlyFromParentChunks() {
        EntityExtractionService service = new EntityExtractionService();
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response("""
                {"entities":["qKnow","RAG"],"relations":[{"source":"qKnow","relation":"uses","target":"RAG","evidence":"qKnow RAG"}]}
                """));

        Document parent = Document.builder()
                .id("p1")
                .text("Day01 介绍 qKnow RAG 架构。")
                .metadata(new HashMap<>(Map.of(RecursiveSplitter.METADATA_CHUNK_LEVEL, RecursiveSplitter.CHUNK_LEVEL_PARENT)))
                .build();
        Document child = Document.builder()
                .id("c1")
                .text("RAG 架构细节")
                .metadata(new HashMap<>(Map.of(
                        RecursiveSplitter.METADATA_CHUNK_LEVEL, RecursiveSplitter.CHUNK_LEVEL_CHILD,
                        RecursiveSplitter.METADATA_PARENT_SEGMENT_ID, "p1")))
                .build();

        List<Document> enriched = service.enrichParentChildMetadata(List.of(parent, child), chatModel);

        assertTrue(enriched.get(0).getMetadata().containsKey("entities"));
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("child 分块应继承 parent 的实体 metadata")
    void enrichParentChildMetadata_childInheritsParentEntityMetadata() {
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

        assertEquals(enriched.get(0).getMetadata().get("entities"), enriched.get(1).getMetadata().get("entities"),
                "child 分块应继承 parent 的 entities");
        assertTrue(enriched.get(1).getMetadata().containsKey("relations"),
                "child 分块应继承 parent 的 relations");
    }

    @Test
    @DisplayName("LLM 失败时应回退到 regex 实体抽取")
    void enrichParentChildMetadata_llmFallsBackToRegex() {
        EntityExtractionService service = new EntityExtractionService();
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response("not valid json"));

        Document parent = Document.builder()
                .id("p1")
                .text("Day01 介绍 qKnow RAG 架构。")
                .metadata(new HashMap<>(Map.of(RecursiveSplitter.METADATA_CHUNK_LEVEL, RecursiveSplitter.CHUNK_LEVEL_PARENT)))
                .build();

        List<Document> enriched = service.enrichParentChildMetadata(List.of(parent), chatModel);

        @SuppressWarnings("unchecked")
        List<String> entities = (List<String>) enriched.get(0).getMetadata().get("entities");
        assertNotNull(entities, "regex 回退应产生实体列表");
        assertTrue(entities.contains("Day01"), "regex 应抽取 Day01 实体");
    }

    @Test
    @DisplayName("无 ChatModel 时使用 regex 策略")
    void enrichParentChildMetadata_noChatModel_usesRegex() {
        EntityExtractionService service = new EntityExtractionService();
        Document parent = Document.builder()
                .id("p1")
                .text("Day01 介绍 qKnow RAG 架构。")
                .metadata(new HashMap<>(Map.of(RecursiveSplitter.METADATA_CHUNK_LEVEL, RecursiveSplitter.CHUNK_LEVEL_PARENT)))
                .build();

        List<Document> enriched = service.enrichParentChildMetadata(List.of(parent));

        assertTrue(enriched.get(0).getMetadata().containsKey("entities"));
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
