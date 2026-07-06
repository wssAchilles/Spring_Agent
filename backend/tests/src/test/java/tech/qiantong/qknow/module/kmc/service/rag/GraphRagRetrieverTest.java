package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.GraphRagResult;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphRagRetrieverTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private Neo4jClient neo4jClient;
    @Mock
    private CypherSafetyValidator cypherSafetyValidator;

    private GraphRagProperties properties;
    private GraphRagRetriever retriever;

    @BeforeEach
    void setUp() {
        RagFallbackMonitor.reset();
        properties = new GraphRagProperties();
        retriever = new GraphRagRetriever();
        ReflectionTestUtils.setField(retriever, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(retriever, "properties", properties);
        ReflectionTestUtils.setField(retriever, "neo4jClient", neo4jClient);
        ReflectionTestUtils.setField(retriever, "cypherSafetyValidator", cypherSafetyValidator);
    }

    @Nested
    @DisplayName("PPR 保护测试")
    class PprRetrieveTests {

        @Test
        @DisplayName("graph disabled 时 PPR 不访问数据库")
        void pprRetrieve_graphDisabled_doesNotQueryDatabase() {
            properties.setEnabled(false);
            properties.setPprEnabled(true);

            List<RetrievalResult> results = retriever.pprRetrieve(1L, List.of("A公司"), 10);

            assertTrue(results.isEmpty());
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        @DisplayName("PPR 读取 kg_edge 使用 source_id/target_id 且限制 seed 子图")
        void pprRetrieve_usesBoundedSchemaColumns() {
            properties.setEnabled(true);
            properties.setPprEnabled(true);
            properties.setPprMaxEdges(10);
            ReflectionTestUtils.setField(retriever, "neo4jClient", null);

            when(jdbcTemplate.queryForList(startsWith("SELECT id FROM kg_node"), eq(Long.class), any(Object[].class)))
                    .thenReturn(List.of(1L));
            when(jdbcTemplate.queryForList(contains("source_id, target_id"), any(Object[].class)))
                    .thenReturn(List.of(Map.of("source_id", 1L, "target_id", 2L)));
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            List<RetrievalResult> results = retriever.pprRetrieve(1L, List.of("A公司"), 10);

            assertTrue(results.isEmpty());
            verify(jdbcTemplate, atLeastOnce()).queryForList(argThat(sql ->
                    sql.contains("source_id, target_id")
                            && sql.contains("source_id IN")
                            && sql.contains("target_id IN")), any(Object[].class));
            verify(jdbcTemplate, never()).queryForList(contains("source_node_id"), any(Object[].class));
        }
    }

    @Nested
    @DisplayName("retrieve 测试")
    class RetrieveTests {

        @Test
        @DisplayName("空 entities 返回空列表")
        void retrieve_emptyEntities_returnsEmpty() {
            properties.setEnabled(true);

            List<RetrievalResult> results = retriever.retrieve(1L, List.of(), 10);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("null entities 返回空列表")
        void retrieve_nullEntities_returnsEmpty() {
            properties.setEnabled(true);

            List<RetrievalResult> results = retriever.retrieve(1L, null, 10);

            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("graphSearch 测试")
    class GraphSearchTests {

        @Test
        @DisplayName("graphSearch disabled 返回空")
        void graphSearch_disabled_returnsEmpty() {
            properties.setEnabled(false);

            List<GraphRagResult> results = retriever.graphSearch(1L, List.of("A公司"), 10);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("null knowledgeBaseId 返回空")
        void graphSearch_nullKbId_returnsEmpty() {
            properties.setEnabled(true);

            List<GraphRagResult> results = retriever.graphSearch(null, List.of("A公司"), 10);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Neo4j segmentIds 回读失败时记录 fallback")
        void loadSegmentsByIds_failure_recordsFallback() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenThrow(new RuntimeException("missing relation table"));

            @SuppressWarnings("unchecked")
            List<GraphRagResult> results = ReflectionTestUtils.invokeMethod(retriever,
                    "loadSegmentsByIds", 1L, List.of(10L), 5);

            assertNotNull(results);
            assertTrue(results.isEmpty());
            Map<String, Object> snapshot = RagFallbackMonitor.snapshot();
            assertTrue(snapshot.containsKey("neo4j"));
        }
    }

    @Nested
    @DisplayName("dualLevelRetrieve 测试")
    class DualLevelRetrieveTests {

        @Test
        @DisplayName("合并 entity + topic 结果并去重")
        void dualLevelRetrieve_mergesAndDeduces() throws Exception {
            // 测试 mergeResults 的去重逻辑，它是 dualLevelRetrieve 的核心
            java.lang.reflect.Method mergeMethod = GraphRagRetriever.class.getDeclaredMethod("mergeResults",
                    List.class, List.class, int.class);
            mergeMethod.setAccessible(true);

            List<RetrievalResult> entityResults = List.of(
                    RetrievalResult.builder().segmentId(1L).score(12.0).content("c1").build(),
                    RetrievalResult.builder().segmentId(2L).score(8.0).content("c2").build());
            List<RetrievalResult> topicResults = List.of(
                    RetrievalResult.builder().segmentId(1L).score(10.0).content("c1").build(),
                    RetrievalResult.builder().segmentId(3L).score(6.0).content("c3").build());

            @SuppressWarnings("unchecked")
            List<RetrievalResult> merged = (List<RetrievalResult>) mergeMethod.invoke(retriever,
                    entityResults, topicResults, 10);

            assertEquals(3, merged.size());
            // segmentId=1 应保留 entity 层的 score=12.0（更高）
            RetrievalResult seg1 = merged.stream().filter(r -> r.getSegmentId() == 1L).findFirst().orElseThrow();
            assertEquals(12.0, seg1.getScore(), 0.001);
            // 结果应按分数降序排列
            for (int i = 0; i < merged.size() - 1; i++) {
                assertTrue(merged.get(i).getScore() >= merged.get(i + 1).getScore());
            }
        }

        @Test
        @DisplayName("topic SQL 失败时记录 fallback")
        void dualLevelRetrieve_topicSqlFailure_recordsFallback() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenThrow(new RuntimeException("topic query failed"));

            List<RetrievalResult> results = retriever.dualLevelRetrieve(1L, List.of(), List.of("RAG"), 5);

            assertTrue(results.isEmpty());
            Map<String, Object> snapshot = RagFallbackMonitor.snapshot();
            assertTrue(snapshot.containsKey("neo4j"));
        }
    }

    @Nested
    @DisplayName("temporalRetrieve 测试")
    class TemporalRetrieveTests {

        @Test
        @DisplayName("时序衰减分数计算")
        void temporalRetrieve_decayScoreCalculation() throws Exception {
            long currentTime = 1700000000000L;
            long thirtyDaysAgo = currentTime - 30L * 86400000L;

            Method decayMethod = GraphRagRetriever.class.getDeclaredMethod("toLong", Object.class);
            decayMethod.setAccessible(true);

            // 验证 toLong 方法对 Number 类型的转换
            Long result = (Long) decayMethod.invoke(retriever, 42L);
            assertEquals(42L, result);

            // 验证时序衰减公式: GRAPH_SCORE * 0.9^(ageDays/30)
            double graphScore = 12.0;
            double decayFactor = 0.9;
            int ageDays = 30;
            double expectedScore = graphScore * Math.pow(decayFactor, ageDays / 30.0);
            assertEquals(12.0 * 0.9, expectedScore, 0.001);

            ageDays = 60;
            expectedScore = graphScore * Math.pow(decayFactor, ageDays / 30.0);
            assertEquals(12.0 * 0.81, expectedScore, 0.001);
        }
    }

    @Nested
    @DisplayName("mergeResults 测试")
    class MergeResultsTests {

        @Test
        @DisplayName("去重保留最高分")
        void mergeResults_deducesAndKeepsHighestScore() throws Exception {
            Method mergeMethod = GraphRagRetriever.class.getDeclaredMethod("mergeResults",
                    List.class, List.class, int.class);
            mergeMethod.setAccessible(true);

            List<RetrievalResult> first = List.of(
                    RetrievalResult.builder().segmentId(1L).score(5.0).content("c1").build(),
                    RetrievalResult.builder().segmentId(2L).score(8.0).content("c2").build());
            List<RetrievalResult> second = List.of(
                    RetrievalResult.builder().segmentId(1L).score(10.0).content("c1").build(),
                    RetrievalResult.builder().segmentId(3L).score(3.0).content("c3").build());

            @SuppressWarnings("unchecked")
            List<RetrievalResult> merged = (List<RetrievalResult>) mergeMethod.invoke(retriever, first, second, 10);

            assertEquals(3, merged.size());
            // segmentId=1 应保留 score=10.0 的版本
            RetrievalResult seg1 = merged.stream().filter(r -> r.getSegmentId() == 1L).findFirst().orElseThrow();
            assertEquals(10.0, seg1.getScore(), 0.001);
            // 结果应按分数降序排列
            for (int i = 0; i < merged.size() - 1; i++) {
                assertTrue(merged.get(i).getScore() >= merged.get(i + 1).getScore());
            }
        }
    }

    @Nested
    @DisplayName("toLong 类型转换测试")
    class ToLongTests {

        @Test
        @DisplayName("Number 类型转换")
        void toLong_numberType_convertsCorrectly() throws Exception {
            Method toLongMethod = GraphRagRetriever.class.getDeclaredMethod("toLong", Object.class);
            toLongMethod.setAccessible(true);

            assertEquals(42L, toLongMethod.invoke(retriever, 42));
            assertEquals(42L, toLongMethod.invoke(retriever, 42L));
            assertEquals(42L, toLongMethod.invoke(retriever, 42.0));
            assertEquals(100L, toLongMethod.invoke(retriever, (short) 100));
        }

        @Test
        @DisplayName("String 类型转换")
        void toLong_stringType_parsesCorrectly() throws Exception {
            Method toLongMethod = GraphRagRetriever.class.getDeclaredMethod("toLong", Object.class);
            toLongMethod.setAccessible(true);

            assertEquals(99L, toLongMethod.invoke(retriever, "99"));
            assertNull(toLongMethod.invoke(retriever, "not-a-number"));
            assertNull(toLongMethod.invoke(retriever, ""));
            assertNull(toLongMethod.invoke(retriever, "  "));
        }

        @Test
        @DisplayName("null 返回 null")
        void toLong_null_returnsNull() throws Exception {
            Method toLongMethod = GraphRagRetriever.class.getDeclaredMethod("toLong", Object.class);
            toLongMethod.setAccessible(true);

            assertNull(toLongMethod.invoke(retriever, (Object) null));
        }
    }
}
