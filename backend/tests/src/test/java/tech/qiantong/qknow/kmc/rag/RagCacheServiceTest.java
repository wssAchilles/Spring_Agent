package tech.qiantong.qknow.kmc.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.RetrieveResultRespVO;
import tech.qiantong.qknow.module.kmc.service.rag.RagCacheService;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RagCacheService.RagCacheConfig config;
    private RagCacheService cacheService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        config = new RagCacheService.RagCacheConfig();
        config.setEnabled(true);
        config.setTtl(300);

        cacheService = new RagCacheService(redisTemplate, objectMapper, config);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void firstCallInvokesRetrieverAndCaches() {
        String cacheKey = RagCacheService.buildCacheKey(1L, "test query", "hybrid");

        RetrieveResultRespVO vo = new RetrieveResultRespVO();
        vo.setId("1");
        vo.setContent("result content");
        vo.setScore(0.95);
        List<RetrieveResultRespVO> expected = List.of(vo);

        when(valueOperations.get(startsWith("rag:cache:"))).thenReturn(null);

        Supplier<List<RetrieveResultRespVO>> retriever = mock(Supplier.class);
        when(retriever.get()).thenReturn(expected);

        List<RetrieveResultRespVO> result = cacheService.getOrRetrieve(cacheKey, retriever);

        assertEquals(1, result.size());
        assertEquals("result content", result.get(0).getContent());
        verify(retriever).get();
        verify(valueOperations).set(eq("rag:cache:" + cacheKey), anyString(), eq(300L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void secondCallReturnsCachedWithoutInvokingRetriever() throws Exception {
        String cacheKey = RagCacheService.buildCacheKey(1L, "test query", "hybrid");

        RetrieveResultRespVO vo = new RetrieveResultRespVO();
        vo.setId("1");
        vo.setContent("cached content");
        vo.setScore(0.9);
        String cachedJson = objectMapper.writeValueAsString(List.of(vo));

        when(valueOperations.get("rag:cache:" + cacheKey)).thenReturn(cachedJson);

        Supplier<List<RetrieveResultRespVO>> retriever = mock(Supplier.class);

        List<RetrieveResultRespVO> result = cacheService.getOrRetrieve(cacheKey, retriever);

        assertEquals(1, result.size());
        assertEquals("cached content", result.get(0).getContent());
        verify(retriever, never()).get();
    }

    @Test
    void evictByKnowledgeBaseDeletesMatchingKeys() {
        String key1 = "rag:cache:1:abc:hybrid";
        String key2 = "rag:cache:1:def:semantic";
        when(redisTemplate.keys("rag:cache:1:*")).thenReturn(Set.of(key1, key2));

        cacheService.evictByKnowledgeBase(1L);

        verify(redisTemplate).delete(Set.of(key1, key2));
    }

    @Test
    void disabledCacheAlwaysInvokesRetriever() {
        config.setEnabled(false);

        String cacheKey = RagCacheService.buildCacheKey(1L, "query", "hybrid");

        RetrieveResultRespVO vo = new RetrieveResultRespVO();
        vo.setId("1");
        vo.setContent("direct result");
        List<RetrieveResultRespVO> expected = List.of(vo);

        Supplier<List<RetrieveResultRespVO>> retriever = mock(Supplier.class);
        when(retriever.get()).thenReturn(expected);

        List<RetrieveResultRespVO> result = cacheService.getOrRetrieve(cacheKey, retriever);

        assertEquals("direct result", result.get(0).getContent());
        verify(retriever).get();
        verifyNoInteractions(valueOperations);
    }
}
