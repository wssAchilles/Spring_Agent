package tech.qiantong.qknow.kmc.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
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
class FullTextSearchTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private KmcKnowledgeBaseServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new KmcKnowledgeBaseServiceImpl();
        java.lang.reflect.Field field = KmcKnowledgeBaseServiceImpl.class.getDeclaredField("jdbcTemplate");
        field.setAccessible(true);
        field.set(service, jdbcTemplate);
    }

    private RetrieveResultReqVO buildReqVO(String query, Long knowledgeBaseId,
                                            boolean scoreThresholdEnabled,
                                            BigDecimal scoreThreshold,
                                            boolean rerankingEnable) {
        RetrieveResultReqVO reqVO = new RetrieveResultReqVO();
        reqVO.setQuery(query);
        reqVO.setId(knowledgeBaseId);
        reqVO.setTopK(BigDecimal.valueOf(10));
        reqVO.setScoreThresholdEnabled(scoreThresholdEnabled);
        reqVO.setScoreThreshold(scoreThreshold);
        reqVO.setRerankingEnable(rerankingEnable);
        return reqVO;
    }

    private Document buildDocument(String content, float score, String segmentId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("score", score);
        metadata.put("kmc_knowledgeBase_id", "1");
        metadata.put("kmc_document_id", "10");
        metadata.put("kmc_segment_id", segmentId);
        metadata.put("kmc_document_name", "test-doc");
        return Document.builder()
                .text(content)
                .score(BigDecimal.valueOf(score).doubleValue())
                .metadata(metadata)
                .build();
    }

    @Test
    void chineseFulltextSearchReturnsRelevantResults() throws Exception {
        RetrieveResultReqVO reqVO = buildReqVO("人工智能", 1L, false, null, false);

        Document doc = buildDocument("人工智能是计算机科学的一个分支", 0.85f, "100");
        doReturn(List.of(doc))
                .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

        java.lang.reflect.Method method = KmcKnowledgeBaseServiceImpl.class
                .getDeclaredMethod("fullTextSearch", RetrieveResultReqVO.class);
        method.setAccessible(true);

        try {
            @SuppressWarnings("unchecked")
            List<Document> result = (List<Document>) method.invoke(service, reqVO);
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.get(0).getText().contains("人工智能"));
        } catch (Exception e) {
            fail("Method invocation failed: " + e.getMessage());
        }
    }

    @Test
    void englishFulltextSearchReturnsRelevantResults() throws Exception {
        RetrieveResultReqVO reqVO = buildReqVO("machine learning", 1L, false, null, false);

        Document doc = buildDocument("Machine learning is a subset of artificial intelligence", 0.92f, "101");
        doReturn(List.of(doc))
                .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

        java.lang.reflect.Method method = KmcKnowledgeBaseServiceImpl.class
                .getDeclaredMethod("fullTextSearch", RetrieveResultReqVO.class);
        method.setAccessible(true);

        try {
            @SuppressWarnings("unchecked")
            List<Document> result = (List<Document>) method.invoke(service, reqVO);
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.get(0).getText().contains("Machine learning"));
        } catch (Exception e) {
            fail("Method invocation failed: " + e.getMessage());
        }
    }

    @Test
    void knowledgeBaseIdFilterWorks() throws Exception {
        RetrieveResultReqVO reqVO = buildReqVO("test", 42L, false, null, false);

        doReturn(Collections.emptyList())
                .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

        java.lang.reflect.Method method = KmcKnowledgeBaseServiceImpl.class
                .getDeclaredMethod("fullTextSearch", RetrieveResultReqVO.class);
        method.setAccessible(true);

        try {
            @SuppressWarnings("unchecked")
            List<Document> result = (List<Document>) method.invoke(service, reqVO);
            assertNotNull(result);
            assertTrue(result.isEmpty());

            verify(jdbcTemplate, atLeastOnce()).query((String) any(), any(RowMapper.class), any(Object[].class));
        } catch (Exception e) {
            fail("Method invocation failed: " + e.getMessage());
        }
    }

    @Test
    void scoreThresholdFilteringWorks() throws Exception {
        RetrieveResultReqVO reqVO = buildReqVO("deep learning", 1L, true, BigDecimal.valueOf(0.5), false);

        Document doc = buildDocument("Deep learning neural networks", 0.75f, "102");
        doReturn(List.of(doc))
                .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

        java.lang.reflect.Method method = KmcKnowledgeBaseServiceImpl.class
                .getDeclaredMethod("fullTextSearch", RetrieveResultReqVO.class);
        method.setAccessible(true);

        try {
            @SuppressWarnings("unchecked")
            List<Document> result = (List<Document>) method.invoke(service, reqVO);
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.get(0).getScore() > 0.5);

            verify(jdbcTemplate, atLeastOnce()).query((String) any(), any(RowMapper.class), any(Object[].class));
        } catch (Exception e) {
            fail("Method invocation failed: " + e.getMessage());
        }
    }
}
