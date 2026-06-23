package tech.qiantong.qknow.module.kmc.service.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.RetrieveResultRespVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
public class RagCacheService {

    private static final String KEY_PREFIX = "rag:cache:";
    private static final TypeReference<List<RetrieveResultRespVO>> RESULT_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RagCacheConfig config;

    public RagCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, RagCacheConfig config) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    public List<RetrieveResultRespVO> getOrRetrieve(String cacheKey, Supplier<List<RetrieveResultRespVO>> retriever) {
        if (!config.isEnabled()) {
            return retriever.get();
        }

        String redisKey = buildRedisKey(cacheKey);

        try {
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                log.debug("RAG cache hit: {}", redisKey);
                return objectMapper.readValue(cached, RESULT_TYPE);
            }
        } catch (Exception e) {
            log.warn("RAG cache read failed, falling back to retriever: {}", redisKey, e);
        }

        log.debug("RAG cache miss: {}", redisKey);
        List<RetrieveResultRespVO> results = retriever.get();

        try {
            String json = objectMapper.writeValueAsString(results);
            redisTemplate.opsForValue().set(redisKey, json, config.getTtl(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("RAG cache write failed: {}", redisKey, e);
        }

        return results;
    }

    public void evictByKnowledgeBase(Long knowledgeBaseId) {
        String pattern = KEY_PREFIX + knowledgeBaseId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("Evicted {} RAG cache entries for knowledgeBase {}", keys.size(), knowledgeBaseId);
        }
    }

    public static String buildCacheKey(Long knowledgeBaseId, String query, String searchMethod) {
        return knowledgeBaseId + ":" + sha256(query) + ":" + searchMethod;
    }

    private String buildRedisKey(String cacheKey) {
        return KEY_PREFIX + cacheKey;
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "hermes.rag.cache")
    public static class RagCacheConfig {
        private boolean enabled = true;
        private long ttl = 300;
    }
}
