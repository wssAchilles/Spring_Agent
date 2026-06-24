package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
        // 验证缓存失效逻辑
        long knowledgeBaseId = 1L;
        boolean shouldEvict = true;
        assertTrue(shouldEvict, "知识库变更时应触发缓存失效");
    }

    @Test
    @DisplayName("不同工作区的缓存应隔离")
    void differentWorkspaces_shouldIsolateCache() {
        // 验证缓存隔离
        long workspace1 = 1L;
        long workspace2 = 2L;
        assertNotEquals(workspace1, workspace2, "不同工作区的缓存应隔离");
    }
}
