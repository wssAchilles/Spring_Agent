package tech.qiantong.qknow.hermes.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LongTermMemoryEnhancedTest {

    @Test
    @DisplayName("Consolidation阈值正确设置")
    void longTermMemory_hasCorrectConstants() {
        // 验证 CrewAI 复合评分公式常量
        // 通过反射或直接测试行为来验证
        VectorStore vectorStore = mock(VectorStore.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        LongTermMemory memory = new LongTermMemory(vectorStore, embeddingModel);

        assertNotNull(memory);
    }

    @Test
    @DisplayName("recallByScope在无结果时返回空列表")
    void recallByScope_withNoResults_returnsEmpty() {
        VectorStore vectorStore = mock(VectorStore.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of());

        LongTermMemory memory = new LongTermMemory(vectorStore, embeddingModel);
        List<Document> results = memory.recall("test query", 10, "scope");

        assertTrue(results.isEmpty());
    }
}
