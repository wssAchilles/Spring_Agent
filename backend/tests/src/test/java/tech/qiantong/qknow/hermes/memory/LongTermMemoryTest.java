package tech.qiantong.qknow.hermes.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LongTermMemory 测试")
class LongTermMemoryTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    private LongTermMemory longTermMemory;

    @BeforeEach
    void setUp() {
        longTermMemory = new LongTermMemory(vectorStore, embeddingModel);
    }

    private Document docWithScore(String text, Map<String, Object> meta, double score) {
        return Document.builder().text(text).metadata(meta).score(score).build();
    }

    // ========== 复合评分公式 ==========

    @Test
    @DisplayName("复合评分公式正确：0.5*similarity + 0.3*decay + 0.2*importance")
    void recall_compositeScoreFormula_correct() {
        // similarity=1.0, decay≈1.0(刚创建), importance=0.8
        // composite = 0.5*1.0 + 0.3*1.0 + 0.2*0.8 = 0.96
        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("created_at", System.currentTimeMillis());
        meta1.put("importance", 0.8);
        Document doc1 = docWithScore("doc1", meta1, 1.0);

        // similarity=0.5, decay≈1.0, importance=0.5
        // composite = 0.5*0.5 + 0.3*1.0 + 0.2*0.5 = 0.65
        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("created_at", System.currentTimeMillis());
        meta2.put("importance", 0.5);
        Document doc2 = docWithScore("doc2", meta2, 0.5);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc2, doc1)); // 故意逆序

        List<Document> results = longTermMemory.recall("query", 2);

        assertEquals(2, results.size());
        assertEquals("doc1", results.get(0).getText());
        assertEquals("doc2", results.get(1).getText());
    }

    // ========== 30 天半衰期衰减 ==========

    @Test
    @DisplayName("30 天半衰期衰减计算正确")
    void recall_decayHalfLife_correctDecay() {
        long now = System.currentTimeMillis();
        long thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000;

        Map<String, Object> meta = new HashMap<>();
        meta.put("created_at", thirtyDaysAgo);
        meta.put("importance", 1.0);
        Document doc = docWithScore("old-doc", meta, 1.0);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

        List<Document> results = longTermMemory.recall("query", 1);

        assertEquals(1, results.size());
        assertEquals("old-doc", results.get(0).getText());
    }

    // ========== 默认重要性 0.5 ==========

    @Test
    @DisplayName("默认重要性为 0.5")
    void recall_defaultImportance_is05() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("created_at", System.currentTimeMillis());
        Document doc = docWithScore("default-imp", meta, 1.0);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

        List<Document> results = longTermMemory.recall("query", 1);

        assertEquals(1, results.size());
    }

    // ========== store ==========

    @Test
    @DisplayName("store 调用 vectorStore.add 存储文档")
    void store_callsVectorStoreAdd() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", "sess-1");

        longTermMemory.store("测试内容", metadata);

        verify(vectorStore, times(1)).add(anyList());
    }

    @Test
    @DisplayName("store 空内容不调用 vectorStore")
    void store_blankContent_skips() {
        longTermMemory.store("", new HashMap<>());
        longTermMemory.store(null, new HashMap<>());
        longTermMemory.store("   ", new HashMap<>());

        verify(vectorStore, never()).add(anyList());
    }

    // ========== store consolidation ==========

    @Test
    @DisplayName("store 相似记忆(cosine>0.85)触发 consolidation 合并")
    void store_similarMemory_triggersConsolidation() {
        // cosine distance = 0.1 → similarity = 1.0 - 0.1/2.0 = 0.95 > 0.85
        Map<String, Object> existingMeta = new HashMap<>();
        existingMeta.put("sessionId", "sess-old");
        existingMeta.put("created_at", System.currentTimeMillis());
        Document existing = docWithScore("old content", existingMeta, 0.1);
        // 需要设置 id 用于 delete
        Document existingWithId = Document.builder()
                .id("existing-id").text("old content").metadata(existingMeta).score(0.1).build();

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(existingWithId));

        Map<String, Object> newMeta = new HashMap<>();
        newMeta.put("sessionId", "sess-new");

        longTermMemory.store("new content", newMeta);

        verify(vectorStore).delete(List.of("existing-id"));
        verify(vectorStore).add(anyList());

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        Document merged = captor.getValue().get(0);
        assertTrue(merged.getText().contains("old content"));
        assertTrue(merged.getText().contains("new content"));
        assertEquals(2, merged.getMetadata().get("consolidated_count"));
    }

    // ========== recall ==========

    @Test
    @DisplayName("recall 按复合评分重排序")
    void recall_sortedByCompositeScore() {
        long now = System.currentTimeMillis();
        long thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000;

        Map<String, Object> metaOld = new HashMap<>();
        metaOld.put("created_at", thirtyDaysAgo);
        metaOld.put("importance", 0.1);
        Document docOld = docWithScore("old-low-imp", metaOld, 0.9);

        Map<String, Object> metaNew = new HashMap<>();
        metaNew.put("created_at", now);
        metaNew.put("importance", 1.0);
        Document docNew = docWithScore("new-high-imp", metaNew, 0.9);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docOld, docNew));

        List<Document> results = longTermMemory.recall("query", 2);

        assertEquals(2, results.size());
        assertEquals("new-high-imp", results.get(0).getText());
        assertEquals("old-low-imp", results.get(1).getText());
    }

    // ========== recall scope 过滤 ==========

    @Test
    @DisplayName("recall 支持 scope 过滤")
    void recall_withScopeFilter_filtersByScope() {
        Map<String, Object> metaA = new HashMap<>();
        metaA.put("scope", "project-a");
        metaA.put("created_at", System.currentTimeMillis());
        Document docA = docWithScore("doc-a", metaA, 0.9);

        Map<String, Object> metaB = new HashMap<>();
        metaB.put("scope", "project-b");
        metaB.put("created_at", System.currentTimeMillis());
        Document docB = docWithScore("doc-b", metaB, 0.9);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docA, docB));

        List<Document> results = longTermMemory.recall("query", 5, "project-a");

        assertEquals(1, results.size());
        assertEquals("doc-a", results.get(0).getText());
    }

    // ========== recall over-fetch 截断 ==========

    @Test
    @DisplayName("recall over-fetch（topK*3）后截断到 topK")
    void recall_overFetch_truncatesToTopK() {
        List<Document> manyDocs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("created_at", System.currentTimeMillis());
            meta.put("importance", 0.5);
            manyDocs.add(docWithScore("doc-" + i, meta, 0.8));
        }

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(manyDocs);

        List<Document> results = longTermMemory.recall("query", 5);

        assertEquals(5, results.size());
    }

    @Test
    @DisplayName("recall 空结果返回空列表")
    void recall_emptyResults_returnsEmptyList() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Collections.emptyList());

        List<Document> results = longTermMemory.recall("query", 5);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("recall null 结果返回空列表")
    void recall_nullResults_returnsEmptyList() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(null);

        List<Document> results = longTermMemory.recall("query", 5);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("recall scope 前缀匹配（如 project-a:sub 匹配 scope=project-a）")
    void recall_scopePrefixMatch_included() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("scope", "project-a:sub-module");
        meta.put("created_at", System.currentTimeMillis());
        Document doc = docWithScore("sub-doc", meta, 0.9);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

        List<Document> results = longTermMemory.recall("query", 5, "project-a");

        assertEquals(1, results.size());
        assertEquals("sub-doc", results.get(0).getText());
    }
}
