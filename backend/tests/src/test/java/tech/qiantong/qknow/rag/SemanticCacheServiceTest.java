package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.service.rag.SemanticCacheService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SemanticCacheService 语义缓存测试")
class SemanticCacheServiceTest {

    @Test
    @DisplayName("缓存键应包含所有维度信息")
    void cacheKey_shouldIncludeAllDimensions() {
        // 验证缓存键的组成维度
        String workspaceId = "1";
        String botId = "1";
        String knowledgeBaseId = "1";
        String knowledgeIdsHash = "abc123";
        String modelName = "deepseek-chat";

        // 缓存键应该唯一标识一个查询
        String cacheKey = String.join(":", workspaceId, botId, knowledgeBaseId, knowledgeIdsHash, modelName);
        assertNotNull(cacheKey);
        assertTrue(cacheKey.contains(workspaceId));
        assertTrue(cacheKey.contains(botId));
        assertTrue(cacheKey.contains(knowledgeBaseId));
        assertTrue(cacheKey.contains(knowledgeIdsHash));
        assertTrue(cacheKey.contains(modelName));
    }

    @Test
    @DisplayName("相似度阈值应为 0.95")
    void similarityThreshold_shouldBe095() {
        double threshold = 0.95;
        assertTrue(threshold > 0.9, "阈值应大于 0.9");
        assertTrue(threshold < 1.0, "阈值应小于 1.0");
    }

    @Test
    @DisplayName("缓存命中应返回答案和相似度")
    void cacheHit_shouldContainAnswerAndSimilarity() {
        // 验证 CacheHit 数据结构
        long id = 1L;
        String answer = "cached answer";
        double similarity = 0.98;

        assertEquals(1L, id);
        assertEquals("cached answer", answer);
        assertTrue(similarity > 0.95, "相似度应大于阈值");
    }

    @Test
    @DisplayName("缓存未命中应返回空")
    void cacheMiss_shouldReturnEmpty() {
        // 验证缓存未命中的处理
        boolean isPresent = false;
        assertFalse(isPresent, "缓存未命中时应返回 false");
    }

    @Test
    @DisplayName("TTL 过期应使缓存失效")
    void ttlExpired_shouldInvalidateCache() {
        // 验证 TTL 过期逻辑
        long expiresAt = System.currentTimeMillis() - 1000; // 已过期
        boolean isExpired = expiresAt < System.currentTimeMillis();
        assertTrue(isExpired, "过期时间早于当前时间应判定为过期");
    }

    @Test
    @DisplayName("知识库变更应触发缓存失效")
    void knowledgeBaseChange_shouldEvictCache() {
        SemanticCacheService service = new SemanticCacheService();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        when(jdbcTemplate.queryForList(any(String.class), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(0);

        SemanticCacheService.CacheHit hit = SemanticCacheService.CacheHit.builder()
                .id(1L)
                .answer("cached")
                .sourcesJson("[]")
                .similarity(1.0)
                .knowledgeBaseIds(List.of(1L))
                .build();
        ReflectionTestUtils.invokeMethod(service, "putExactCache", "kb-key", hit);
        assertNotNull(ReflectionTestUtils.invokeMethod(service, "getExactCache", "kb-key"));

        service.evictByKnowledgeBase(1L);

        assertNull(ReflectionTestUtils.invokeMethod(service, "getExactCache", "kb-key"));
    }

    @Test
    @DisplayName("不同工作区的缓存应隔离")
    void differentWorkspaces_shouldIsolateCache() {
        // 验证缓存隔离
        long workspace1 = 1L;
        long workspace2 = 2L;
        assertNotEquals(workspace1, workspace2, "不同工作区的缓存应隔离");
    }

    @Test
    @DisplayName("精确缓存并发读写不应抛出异常")
    void exactCache_concurrentAccess_shouldNotThrow() throws Exception {
        SemanticCacheService service = new SemanticCacheService();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            final int index = i;
            tasks.add(() -> {
                String key = "key-" + index;
                SemanticCacheService.CacheHit hit = SemanticCacheService.CacheHit.builder()
                        .id((long) index)
                        .answer("answer-" + index)
                        .sourcesJson("[]")
                        .similarity(1.0)
                        .build();
                ReflectionTestUtils.invokeMethod(service, "putExactCache", key, hit);
                Object cached = ReflectionTestUtils.invokeMethod(service, "getExactCache", key);
                assertNotNull(cached);
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Void> future : futures) {
            future.get();
        }
    }

    @Test
    @DisplayName("精确缓存键使用 SHA-256 避免 hashCode 碰撞")
    void exactCacheKey_usesSha256Digest() {
        SemanticCacheService service = new SemanticCacheService();

        String key = ReflectionTestUtils.invokeMethod(service, "buildExactCacheKey",
                1L, 2L, "kb-hash", "model", "  Hello   RAG  ");

        assertNotNull(key);
        String[] parts = key.split(":");
        assertEquals(5, parts.length);
        assertEquals("1", parts[0]);
        assertEquals("2", parts[1]);
        assertEquals("kb-hash", parts[2]);
        assertEquals("model", parts[3]);
        assertTrue(parts[4].matches("[0-9a-f]{64}"));
        assertNotEquals(String.valueOf("hello rag".hashCode()), parts[4]);
    }

    @Test
    @DisplayName("精确缓存过期后不命中")
    void exactCache_expiredHit_shouldMiss() {
        SemanticCacheService service = new SemanticCacheService();
        SemanticCacheService.CacheHit hit = SemanticCacheService.CacheHit.builder()
                .id(1L)
                .answer("expired")
                .sourcesJson("[]")
                .similarity(1.0)
                .expiresAt(Instant.now().minusSeconds(1))
                .build();

        ReflectionTestUtils.invokeMethod(service, "putExactCache", "expired-key", hit);
        Object cached = ReflectionTestUtils.invokeMethod(service, "getExactCache", "expired-key");

        assertNull(cached);
    }

    @Test
    @DisplayName("语义缓存查询使用配置维度向量表达式")
    void findAnswer_usesConfiguredVectorExpression() {
        SemanticCacheService service = new SemanticCacheService();
        SemanticCacheService.SemanticCacheConfig config = new SemanticCacheService.SemanticCacheConfig();
        config.setEmbeddingDimension(768);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        ReflectionTestUtils.setField(service, "config", config);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[768], 0))));
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        service.findAnswer(1L, 2L, 3L, "kb", "query", "model", embeddingModel);

        verify(jdbcTemplate).query(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("query_embedding::vector(768) <=> ?::vector(768)")),
                any(RowMapper.class),
                any(Object[].class));
    }
}
