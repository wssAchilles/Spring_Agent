package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tech.qiantong.qknow.ai.constant.WeaviateConstant;
import tech.qiantong.qknow.ai.service.IVectorStoreService;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeBaseDO;
import tech.qiantong.qknow.module.kmc.dal.mapper.knowledgeBase.KmcKnowledgeBaseMapper;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorRetrieverTest {

    @Mock
    private IAiModelApiService aiModelService;
    @Mock
    private IVectorStoreService vectorStoreService;
    @Mock
    private KmcKnowledgeBaseMapper kmcKnowledgeBaseMapper;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private TestVectorRetriever retriever;

    @BeforeEach
    void setUp() throws Exception {
        retriever = new TestVectorRetriever();
        setField("aiModelService", aiModelService);
        setField("vectorStoreService", vectorStoreService);
        setField("kmcKnowledgeBaseMapper", kmcKnowledgeBaseMapper);
    }

    @Test
    @DisplayName("PgVector 返回候选后使用 VecSimNative 路径重算 score")
    void retrieve_rescoresWithVecSimNativePath() {
        KmcKnowledgeBaseDO kb = KmcKnowledgeBaseDO.builder()
                .id(7L)
                .embeddingModelProvider("1")
                .embeddingModel("text-embedding")
                .build();
        when(kmcKnowledgeBaseMapper.selectById(7L)).thenReturn(kb);
        when(aiModelService.getEmbeddingModel(eq(1L), eq("text-embedding"))).thenReturn(embeddingModel);
        when(vectorStoreService.getVectorStore(embeddingModel)).thenReturn(vectorStore);
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[]{1.0f, 0.0f}, 0))));

        Document first = vectorDoc("vec-11", 11L, 101L, "alpha", 0.8);
        Document second = vectorDoc("vec-22", 22L, 102L, "beta", 0.1);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(first, second));

        retriever.candidateVectors = List.of(
                new VectorRetriever.CandidateVector("vec-11", 11L, new float[]{1.0f, 0.0f}),
                new VectorRetriever.CandidateVector("vec-22", 22L, new float[]{0.0f, 1.0f})
        );
        retriever.nativeScores = new float[]{0.2f, 0.9f};

        List<RetrievalResult> results = retriever.retrieve(7L, "query", 2);

        assertTrue(retriever.vecSimCalled);
        assertEquals(2, retriever.lastDim);
        assertArrayEquals(new float[]{1.0f, 0.0f}, retriever.lastQueryEmbedding, 0.000001f);
        assertArrayEquals(new float[]{1.0f, 0.0f, 0.0f, 1.0f}, retriever.lastCorpus, 0.000001f);
        assertEquals(22L, results.get(0).getSegmentId());
        assertEquals(0.9d, results.get(0).getScore(), 0.000001d);
        assertEquals(0.1d, (Double) results.get(0).getMetadata().get("pgvector_score"), 0.000001d);
        assertEquals(0.9d, (Double) results.get(0).getMetadata().get("vecsim_score"), 0.000001d);
    }

    @Test
    @DisplayName("VecSim 重评分开关默认开启并绑定固定配置项")
    void vecSimRescoreFlag_usesExpectedPropertyAndDefaultsTrue() throws Exception {
        Field field = requiredField("vecSimRescoreEnabled");
        Value value = field.getAnnotation(Value.class);

        assertNotNull(value);
        assertEquals("${qknow.rag.vector.vecsim-rescore-enabled:true}", value.value());
        assertTrue(field.getBoolean(new VectorRetriever()));
    }

    @Test
    @DisplayName("关闭 VecSim 重评分时保留 pgvector 顺序和分数")
    void retrieve_keepsPgVectorResultsWhenVecSimRescoreDisabled() throws Exception {
        KmcKnowledgeBaseDO kb = KmcKnowledgeBaseDO.builder()
                .id(7L)
                .embeddingModelProvider("1")
                .embeddingModel("text-embedding")
                .build();
        when(kmcKnowledgeBaseMapper.selectById(7L)).thenReturn(kb);
        when(aiModelService.getEmbeddingModel(eq(1L), eq("text-embedding"))).thenReturn(embeddingModel);
        when(vectorStoreService.getVectorStore(embeddingModel)).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorDoc("vec-11", 11L, 101L, "alpha", 0.8),
                vectorDoc("vec-22", 22L, 102L, "beta", 0.1)
        ));
        setField("jdbcTemplate", jdbcTemplate);
        requiredField("vecSimRescoreEnabled").set(retriever, false);

        List<RetrievalResult> results = retriever.retrieve(7L, "query", 2);

        verify(embeddingModel, never()).call(any(EmbeddingRequest.class));
        verifyNoInteractions(jdbcTemplate);
        assertFalse(retriever.candidateLoadCalled);
        assertFalse(retriever.vecSimCalled);
        assertEquals(List.of(11L, 22L), results.stream().map(RetrievalResult::getSegmentId).toList());
        assertEquals(0.8d, results.get(0).getScore(), 0.000001d);
        assertEquals(0.1d, results.get(1).getScore(), 0.000001d);
        assertFalse(results.get(0).getMetadata().containsKey("pgvector_score"));
        assertFalse(results.get(0).getMetadata().containsKey("vecsim_score"));
    }

    @Test
    @DisplayName("从 vector_store.embedding 读取候选向量")
    @SuppressWarnings("unchecked")
    void loadCandidateVectors_readsEmbeddingFromVectorStore() throws Exception {
        VectorRetriever jdbcRetriever = new VectorRetriever();
        Field field = VectorRetriever.class.getDeclaredField("jdbcTemplate");
        field.setAccessible(true);
        field.set(jdbcRetriever, jdbcTemplate);

        String[] capturedSql = new String[1];
        Object[][] capturedParams = new Object[1][];
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    capturedSql[0] = invocation.getArgument(0);
                    capturedParams[0] = Arrays.copyOfRange(invocation.getArguments(), 2,
                            invocation.getArguments().length);
                    RowMapper<VectorRetriever.CandidateVector> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("id")).thenReturn("vec-11");
                    when(rs.getString("segment_id")).thenReturn("11");
                    when(rs.getString("embedding_text")).thenReturn("[1,0]");
                    return List.of(mapper.mapRow(rs, 0));
                });

        List<VectorRetriever.CandidateVector> vectors = jdbcRetriever.loadCandidateVectors(7L, List.of(
                RetrievalResult.builder().qmSegmentId("vec-11").segmentId(11L).build()
        ));

        assertEquals(1, vectors.size());
        assertEquals("vec-11", vectors.get(0).vectorId());
        assertEquals(11L, vectors.get(0).segmentId());
        assertArrayEquals(new float[]{1.0f, 0.0f}, vectors.get(0).embedding(), 0.000001f);
        assertTrue(capturedSql[0].contains("FROM vector_store"));
        assertTrue(capturedSql[0].contains("id IN (?)"));
        assertTrue(capturedSql[0].contains("embedding::text AS embedding_text"));
        assertTrue(capturedSql[0].contains("metadata->>'" + WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID + "'"));
        assertArrayEquals(new Object[]{"vec-11", "7"}, capturedParams[0]);
    }

    @Test
    @DisplayName("解析 pgvector 文本格式")
    void parsePgVector_parsesTextVector() {
        assertArrayEquals(new float[]{1.0f, -0.5f, 0.0012f},
                VectorRetriever.parsePgVector("[1.0, -0.5, 1.2E-3]"), 0.000001f);
        assertArrayEquals(new float[0], VectorRetriever.parsePgVector("[]"), 0.000001f);
        assertArrayEquals(new float[0], VectorRetriever.parsePgVector("[bad]"), 0.000001f);
    }

    @Test
    @DisplayName("vector_store.id 类型探测应缓存")
    void isVectorStoreIdUuid_cachesSchemaLookup() throws Exception {
        VectorRetriever jdbcRetriever = new VectorRetriever();
        Field field = VectorRetriever.class.getDeclaredField("jdbcTemplate");
        field.setAccessible(true);
        field.set(jdbcRetriever, jdbcTemplate);
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class))).thenReturn("uuid");

        assertTrue(jdbcRetriever.isVectorStoreIdUuid());
        assertTrue(jdbcRetriever.isVectorStoreIdUuid());

        verify(jdbcTemplate, times(1)).queryForObject(any(String.class), eq(String.class));
    }

    private Document vectorDoc(String id, Long segmentId, Long documentId, String text, double score) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(WeaviateConstant.METADATA_FIELD_SEGMENT_ID, segmentId);
        metadata.put(WeaviateConstant.METADATA_FIELD_DOCUMENT_ID, documentId);
        metadata.put(WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME, "doc-" + documentId + ".txt");
        return Document.builder()
                .id(id)
                .text(text)
                .metadata(metadata)
                .score(score)
                .build();
    }

    private void setField(String name, Object value) throws Exception {
        Field field = VectorRetriever.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(retriever, value);
    }

    private Field requiredField(String name) {
        Field field = Arrays.stream(VectorRetriever.class.getDeclaredFields())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElse(null);
        assertNotNull(field, "VectorRetriever 缺少字段 " + name);
        field.setAccessible(true);
        return field;
    }

    private static class TestVectorRetriever extends VectorRetriever {
        private List<VectorRetriever.CandidateVector> candidateVectors = List.of();
        private float[] nativeScores = new float[0];
        private boolean candidateLoadCalled;
        private boolean vecSimCalled;
        private float[] lastQueryEmbedding;
        private float[] lastCorpus;
        private int lastDim;

        @Override
        List<VectorRetriever.CandidateVector> loadCandidateVectors(Long knowledgeBaseId, List<RetrievalResult> results) {
            candidateLoadCalled = true;
            assertEquals(7L, knowledgeBaseId);
            assertEquals(2, results.size());
            return candidateVectors;
        }

        @Override
        float[] safeCosineBatch(float[] queryEmbedding, float[] corpus, int dim) {
            vecSimCalled = true;
            lastQueryEmbedding = queryEmbedding;
            lastCorpus = corpus;
            lastDim = dim;
            return nativeScores;
        }
    }
}
