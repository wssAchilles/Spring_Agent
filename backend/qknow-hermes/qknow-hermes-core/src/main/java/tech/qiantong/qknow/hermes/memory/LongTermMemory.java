package tech.qiantong.qknow.hermes.memory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 向量化长期记忆。
 */
public class LongTermMemory {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    public LongTermMemory(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    public void store(String content, Map<String, Object> metadata) {
        Document doc = Document.builder()
                .text(content)
                .metadata(metadata)
                .build();
        vectorStore.add(List.of(doc));
    }

    public List<Document> recall(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return results != null ? results : Collections.emptyList();
    }
}
