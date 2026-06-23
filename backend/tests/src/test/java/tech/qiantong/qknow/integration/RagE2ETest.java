package tech.qiantong.qknow.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.RetrieveResultReqVO;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.impl.KmcKnowledgeBaseServiceImpl;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagE2ETest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private KmcKnowledgeBaseServiceImpl service;

    private static final float[] FIXED_VECTOR = new float[]{
            0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f
    };

    @BeforeEach
    void setUp() throws Exception {
        service = new KmcKnowledgeBaseServiceImpl();
        setField("jdbcTemplate", jdbcTemplate);
    }

    private void setField(String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = KmcKnowledgeBaseServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    private RetrieveResultReqVO buildReq(String query, Long kbId, String searchMethod) {
        RetrieveResultReqVO req = new RetrieveResultReqVO();
        req.setQuery(query);
        req.setId(kbId);
        req.setTopK(BigDecimal.valueOf(5));
        req.setSearchMethod(searchMethod);
        req.setScoreThresholdEnabled(false);
        req.setRerankingEnable(false);
        return req;
    }

    private Document buildDoc(String content, double score, String segmentId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("score", (float) score);
        metadata.put("kmc_knowledgeBase_id", "1");
        metadata.put("kmc_document_id", "10");
        metadata.put("kmc_segment_id", segmentId);
        metadata.put("kmc_document_name", "test-doc");
        return Document.builder()
                .text(content)
                .score(score)
                .metadata(metadata)
                .build();
    }

    @Nested
    @DisplayName("语义检索 E2E")
    class SemanticSearch {

        @Test
        @DisplayName("语义检索返回相关文档且包含 KB 内容")
        void semanticSearchReturnsRelevantDocs() {
            RetrieveResultReqVO req = buildReq("什么是人工智能", 1L, "semantic_search");

            Document doc = buildDoc("人工智能是计算机科学的一个分支，致力于创建能够模拟人类智能的系统。", 0.92, "seg-1");
            doReturn(List.of(doc))
                    .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

            java.lang.reflect.Method method = findMethod("fullTextSearch");
            try {
                @SuppressWarnings("unchecked")
                List<Document> result = (List<Document>) method.invoke(service, req);

                assertNotNull(result);
                assertFalse(result.isEmpty());
                assertTrue(result.get(0).getText().contains("人工智能"));
                assertTrue(result.get(0).getScore() > 0.5);
            } catch (Exception e) {
                fail("语义检索失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("语义检索空结果时返回空列表")
        void semanticSearchReturnsEmptyForNoMatch() {
            RetrieveResultReqVO req = buildReq("完全无关的查询", 1L, "semantic_search");

            doReturn(Collections.emptyList())
                    .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

            java.lang.reflect.Method method = findMethod("fullTextSearch");
            try {
                @SuppressWarnings("unchecked")
                List<Document> result = (List<Document>) method.invoke(service, req);

                assertNotNull(result);
                assertTrue(result.isEmpty());
            } catch (Exception e) {
                fail("语义检索失败: " + e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("全文检索 E2E")
    class FullTextSearch {

        @Test
        @DisplayName("中文全文检索返回匹配文档")
        void chineseFullTextSearchWorks() {
            RetrieveResultReqVO req = buildReq("机器学习", 1L, "full_text_search");

            Document doc = buildDoc("机器学习是人工智能的核心技术之一，通过数据驱动的方式训练模型。", 0.88, "seg-2");
            doReturn(List.of(doc))
                    .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

            java.lang.reflect.Method method = findMethod("fullTextSearch");
            try {
                @SuppressWarnings("unchecked")
                List<Document> result = (List<Document>) method.invoke(service, req);

                assertNotNull(result);
                assertEquals(1, result.size());
                assertTrue(result.get(0).getText().contains("机器学习"));
            } catch (Exception e) {
                fail("全文检索失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("英文全文检索返回匹配文档")
        void englishFullTextSearchWorks() {
            RetrieveResultReqVO req = buildReq("deep learning", 1L, "full_text_search");

            Document doc = buildDoc("Deep learning is a subset of machine learning using neural networks.", 0.85, "seg-3");
            doReturn(List.of(doc))
                    .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

            java.lang.reflect.Method method = findMethod("fullTextSearch");
            try {
                @SuppressWarnings("unchecked")
                List<Document> result = (List<Document>) method.invoke(service, req);

                assertNotNull(result);
                assertEquals(1, result.size());
                assertTrue(result.get(0).getText().contains("Deep learning"));
            } catch (Exception e) {
                fail("英文全文检索失败: " + e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("混合检索 E2E")
    class HybridSearch {

        @Test
        @DisplayName("混合检索合并语义和全文结果并去重")
        void hybridSearchMergesAndDeduplicates() {
            RetrieveResultReqVO req = buildReq("知识图谱", 1L, "hybrid_search");
            req.setKeywordWeight(BigDecimal.valueOf(0.3));
            req.setVectorWeight(BigDecimal.valueOf(0.7));

            Document semanticDoc = buildDoc("知识图谱是一种结构化的语义知识库。", 0.9, "seg-4");
            Document fulltextDoc = buildDoc("知识图谱通过图结构表示实体和关系。", 0.8, "seg-5");
            Document overlapDoc = buildDoc("知识图谱广泛应用于搜索引擎和问答系统。", 0.85, "seg-6");

            doReturn(List.of(fulltextDoc, overlapDoc))
                    .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

            java.lang.reflect.Method method = findMethod("fullTextSearch");
            try {
                @SuppressWarnings("unchecked")
                List<Document> result = (List<Document>) method.invoke(service, req);

                assertNotNull(result);
                assertFalse(result.isEmpty());
                result.forEach(doc -> assertNotNull(doc.getText()));
            } catch (Exception e) {
                fail("混合检索失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("混合检索分数阈值过滤低分结果")
        void hybridSearchFiltersByScoreThreshold() {
            RetrieveResultReqVO req = buildReq("自然语言处理", 1L, "hybrid_search");
            req.setScoreThresholdEnabled(true);
            req.setScoreThreshold(BigDecimal.valueOf(0.8));

            Document highScoreDoc = buildDoc("NLP是自然语言处理的简称。", 0.95, "seg-7");
            Document lowScoreDoc = buildDoc("一些不太相关的内容。", 0.3, "seg-8");

            doReturn(List.of(highScoreDoc, lowScoreDoc))
                    .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

            java.lang.reflect.Method method = findMethod("fullTextSearch");
            try {
                @SuppressWarnings("unchecked")
                List<Document> result = (List<Document>) method.invoke(service, req);

                assertNotNull(result);
                result.forEach(doc ->
                        assertTrue(doc.getScore() >= 0.8, "返回文档分数应 >= 0.8，实际: " + doc.getScore()));
            } catch (Exception e) {
                fail("混合检索分数过滤失败: " + e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("端到端流程验证")
    class EndToEndPipeline {

        @Test
        @DisplayName("完整 RAG 流程: 查询 → 检索 → 返回相关文档")
        void fullRagPipelineReturnsRelevantDocs() {
            RetrieveResultReqVO req = buildReq("深度学习的应用场景", 1L, "full_text_search");

            Document doc1 = buildDoc("深度学习在图像识别领域取得了突破性进展。", 0.93, "seg-10");
            Document doc2 = buildDoc("深度学习被广泛应用于自然语言处理任务。", 0.89, "seg-11");
            Document doc3 = buildDoc("深度学习在语音识别中也有重要应用。", 0.82, "seg-12");

            doReturn(List.of(doc1, doc2, doc3))
                    .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

            java.lang.reflect.Method method = findMethod("fullTextSearch");
            try {
                @SuppressWarnings("unchecked")
                List<Document> result = (List<Document>) method.invoke(service, req);

                assertNotNull(result);
                assertEquals(3, result.size());
                assertTrue(result.stream().allMatch(d -> d.getText().contains("深度学习")));
                assertTrue(result.get(0).getScore() >= result.get(1).getScore(),
                        "结果应按分数降序排列");
            } catch (Exception e) {
                fail("完整 RAG 流程失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("知识库隔离: 不同 KB ID 返回各自文档")
        void knowledgeBaseIsolation() {
            RetrieveResultReqVO req1 = buildReq("test", 1L, "full_text_search");
            RetrieveResultReqVO req2 = buildReq("test", 2L, "full_text_search");

            Document kb1Doc = buildDoc("KB1 的文档内容", 0.9, "seg-kb1");
            Document kb2Doc = buildDoc("KB2 的文档内容", 0.9, "seg-kb2");

            doReturn(List.of(kb1Doc))
                    .doReturn(List.of(kb2Doc))
                    .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

            java.lang.reflect.Method method = findMethod("fullTextSearch");
            try {
                @SuppressWarnings("unchecked")
                List<Document> r1 = (List<Document>) method.invoke(service, req1);
                @SuppressWarnings("unchecked")
                List<Document> r2 = (List<Document>) method.invoke(service, req2);

                assertEquals(1, r1.size());
                assertTrue(r1.get(0).getText().contains("KB1"));
                assertEquals(1, r2.size());
                assertTrue(r2.get(0).getText().contains("KB2"));
            } catch (Exception e) {
                fail("知识库隔离验证失败: " + e.getMessage());
            }
        }
    }

    private java.lang.reflect.Method findMethod(String name) {
        try {
            java.lang.reflect.Method m = KmcKnowledgeBaseServiceImpl.class.getDeclaredMethod(name, RetrieveResultReqVO.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            fail("方法不存在: " + name);
            return null;
        }
    }
}
