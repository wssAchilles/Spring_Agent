package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.collection.CollUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;

import tech.qiantong.qknow.module.kmc.service.rag.cache.EnhancedSemanticCacheService;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

// [溯源] 算法优化指南 §2.7 P2-4: 精确缓存层前置 + Phase 11 增强语义漂移防护与无锁高并发

@Slf4j
@Component
public class SemanticCacheService {

    private static final double DEFAULT_THRESHOLD = 0.92D;
    private static final int DEFAULT_EMBEDDING_DIMENSION = 1536;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private SemanticCacheConfig config;

    @Resource
    private EnhancedSemanticCacheService enhancedCacheService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService ragMetricsService;

    // L1 本地无锁精确缓存（使用 ConcurrentHashMap，彻底消除全局互斥排队锁）
    private final java.util.concurrent.ConcurrentHashMap<String, CacheHit> exactCache = new java.util.concurrent.ConcurrentHashMap<>();

    public Optional<CacheHit> findAnswer(Long workspaceId, Long botId, Long knowledgeBaseId, String knowledgeIdsHash,
                                         String query, String modelName, EmbeddingModel embeddingModel) {
        return findAnswer(workspaceId, botId, List.of(knowledgeBaseId), knowledgeIdsHash, query, modelName, embeddingModel);
    }

    public Optional<CacheHit> findAnswer(Long workspaceId, Long botId, List<Long> knowledgeBaseIds, String knowledgeIdsHash,
                                          String query, String modelName, EmbeddingModel embeddingModel) {
        long startNs = System.nanoTime();
        if (!config.isEnabled()) {
            if (ragMetricsService != null) {
                ragMetricsService.recordCacheBypass();
            }
            return Optional.empty();
        }

        // 防穿透检查：是否已被空结果哨兵短期拦截
        if (enhancedCacheService != null && enhancedCacheService.isBlockedBySentinel(workspaceId, botId, knowledgeBaseIds, modelName, query)) {
            log.debug("Semantic cache blocked by empty sentinel for query: {}", query);
            return Optional.empty();
        }

        // L1: 精确缓存层 — 无锁高并发检索
        if (enhancedCacheService != null) {
            var exactOpt = enhancedCacheService.findExact(workspaceId, botId, knowledgeBaseIds, modelName, query);
            if (exactOpt.isPresent()) {
                var e = exactOpt.get();
                log.debug("Exact cache hit for query: {}", query.substring(0, Math.min(50, query.length())));
                if (ragMetricsService != null) {
                    ragMetricsService.recordCacheHitExact();
                    ragMetricsService.recordStage(tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService.RagStage.CACHE,
                            tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService.StageOutcome.OK, System.nanoTime() - startNs);
                }
                return Optional.of(CacheHit.builder()
                        .id(e.getId())
                        .answer(e.getAnswer())
                        .sourcesJson(e.getSourcesJson())
                        .similarity(e.getSimilarity())
                        .knowledgeBaseIds(knowledgeBaseIds)
                        .query(e.getOriginalQuery())
                        .build());
            }
        }

        // L2: 语义缓存层 (PGVector)
        float[] embedding = embed(query, embeddingModel);
        int embeddingDimension = embeddingDimension();
        if (embedding.length != embeddingDimension) {
            RagFallbackMonitor.record("semantic_cache", "bypass",
                    "embedding dimension mismatch: expected " + embeddingDimension + ", got " + embedding.length);
            log.debug("Semantic cache skipped due to embedding dimension mismatch: expected={}, actual={}",
                    embeddingDimension, embedding.length);
            if (ragMetricsService != null) {
                ragMetricsService.recordCacheBypass();
                ragMetricsService.recordStage(tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService.RagStage.CACHE,
                        tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService.StageOutcome.BYPASS, System.nanoTime() - startNs);
            }
            return Optional.empty();
        }
        String vectorCast = "vector(" + embeddingDimension + ")";

        String sql = """
                SELECT id, query, answer, sources_json, 1 - (query_embedding::%1$s <=> ?::%1$s) AS similarity
                FROM semantic_cache_store
                WHERE workspace_id = ?
                  AND bot_id = ?
                  AND knowledge_ids_hash = ?
                  AND model_name = ?
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                  AND 1 - (query_embedding::%1$s <=> ?::%1$s) >= ?
                ORDER BY query_embedding::%1$s <=> ?::%1$s
                LIMIT 1
                """.formatted(vectorCast);
        String vector = toVectorLiteral(embedding);
        try {
            List<CacheHit> hits = jdbcTemplate.query(sql, (rs, rowNum) -> CacheHit.builder()
                    .id(rs.getLong("id"))
                    .query(rs.getString("query"))
                    .answer(rs.getString("answer"))
                    .sourcesJson(rs.getString("sources_json"))
                    .similarity(rs.getDouble("similarity"))
                    .knowledgeBaseIds(knowledgeBaseIds)
                    .build(), vector, workspaceId, botId, knowledgeIdsHash, modelName,
                    vector, config.getThreshold(), vector);
            if (CollUtil.isEmpty(hits)) {
                if (ragMetricsService != null) {
                    ragMetricsService.recordCacheMiss();
                    ragMetricsService.recordStage(tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService.RagStage.CACHE,
                            tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService.StageOutcome.OK, System.nanoTime() - startNs);
                }
                return Optional.empty();
            }

            CacheHit hit = hits.get(0);
            // Phase 11 核心：语义漂移防御门禁校验（否定词与核心词元）
            if (enhancedCacheService != null && !enhancedCacheService.passSemanticGating(query, hit.getQuery())) {
                log.warn("[SemanticCache] 命中条目未通过语义漂移防御门禁，拦截反向/实体漂移误命中: query='{}', hitQuery='{}'",
                        query, hit.getQuery());
                if (ragMetricsService != null) {
                    ragMetricsService.recordCacheMiss();
                    ragMetricsService.recordStage(tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService.RagStage.CACHE,
                            tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService.StageOutcome.OK, System.nanoTime() - startNs);
                }
                return Optional.empty();
            }

            jdbcTemplate.update("UPDATE semantic_cache_store SET hit_count = hit_count + 1, last_hit_at = CURRENT_TIMESTAMP WHERE id = ?",
                    hit.getId());

            // 门禁通过后回填 L1 增强缓存
            if (enhancedCacheService != null) {
                enhancedCacheService.putExactCache(workspaceId, botId, knowledgeBaseIds, modelName, query,
                        hit.getAnswer(), hit.getSourcesJson(), config.getTtl());
            }

            if (ragMetricsService != null) {
                ragMetricsService.recordCacheHitSemantic();
                ragMetricsService.recordStage(tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService.RagStage.CACHE,
                        tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService.StageOutcome.OK, System.nanoTime() - startNs);
            }
            return Optional.of(hit);
        } catch (Exception e) {
            RagFallbackMonitor.record("semantic_cache", "bypass", "lookup failed: " + e.getMessage());
            log.warn("Semantic cache lookup failed, bypassing cache", e);
            if (ragMetricsService != null) {
                ragMetricsService.recordCacheBypass();
                ragMetricsService.recordStage(tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService.RagStage.CACHE,
                        tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService.StageOutcome.ERROR, System.nanoTime() - startNs);
            }
            return Optional.empty();
        }
    }

    public void saveAnswer(Long workspaceId, Long botId, Long knowledgeBaseId, String knowledgeIdsHash, String query,
                           String answer, String modelName, EmbeddingModel embeddingModel, Duration ttl) {
        saveAnswer(workspaceId, botId, List.of(knowledgeBaseId), knowledgeIdsHash, query, answer,
                modelName, null, embeddingModel, ttl);
    }

    public void saveAnswer(Long workspaceId, Long botId, List<Long> knowledgeBaseIds, String knowledgeIdsHash,
                           String query, String answer, String modelName, String sourcesJson,
                           EmbeddingModel embeddingModel, Duration ttl) {
        if (!config.isEnabled() || CollUtil.isEmpty(knowledgeBaseIds)) {
            return;
        }
        float[] embedding = embed(query, embeddingModel);
        int embeddingDimension = embeddingDimension();
        if (embedding.length != embeddingDimension) {
            RagFallbackMonitor.record("semantic_cache", "skip_write",
                    "embedding dimension mismatch: expected " + embeddingDimension + ", got " + embedding.length);
            log.debug("Semantic cache write skipped due to embedding dimension mismatch: expected={}, actual={}",
                    embeddingDimension, embedding.length);
            return;
        }

        String sql = """
                INSERT INTO semantic_cache_store (
                    workspace_id, bot_id, knowledge_base_id, knowledge_ids_hash, query,
                    query_embedding, answer, sources_json, model_name, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?::vector, ?, ?::jsonb, ?, CURRENT_TIMESTAMP + (? || ' seconds')::interval)
                RETURNING id
                """;
        try {
            Long cacheId = jdbcTemplate.queryForObject(sql, Long.class, workspaceId, botId, knowledgeBaseIds.get(0),
                    knowledgeIdsHash, query, toVectorLiteral(embedding), answer,
                    sourcesJson != null ? sourcesJson : "[]", modelName, ttl.toSeconds());
            if (cacheId != null) {
                List<Object[]> rows = new ArrayList<>();
                for (Long knowledgeBaseId : knowledgeBaseIds) {
                    rows.add(new Object[]{cacheId, knowledgeBaseId});
                }
                jdbcTemplate.batchUpdate("""
                        INSERT INTO semantic_cache_knowledge_rel(cache_id, knowledge_base_id)
                        VALUES (?, ?)
                        ON CONFLICT DO NOTHING
                        """, rows);

                // Phase 11: 委托写入无锁高性能增强精确缓存（自带 Jitter 防雪崩）
                if (enhancedCacheService != null) {
                    enhancedCacheService.putExactCache(workspaceId, botId, knowledgeBaseIds, modelName,
                            query, answer, sourcesJson != null ? sourcesJson : "[]", ttl);
                }
            }
        } catch (Exception e) {
            RagFallbackMonitor.record("semantic_cache", "skip_write", "write failed: " + e.getMessage());
            log.warn("Semantic cache write failed", e);
        }
    }

    public int evictByKnowledgeBase(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return 0;
        }
        int evictedExact = enhancedCacheService != null ? enhancedCacheService.evictExactCacheByKnowledgeBase(knowledgeBaseId) : 0;
        evictedExact += evictExactCacheByKnowledgeBase(knowledgeBaseId);
        try {
            List<Long> cacheIds = jdbcTemplate.queryForList("""
                    SELECT cache_id FROM semantic_cache_knowledge_rel WHERE knowledge_base_id = ?
                    """, Long.class, knowledgeBaseId);
            int deletedRel = jdbcTemplate.update("""
                    DELETE FROM semantic_cache_knowledge_rel
                    WHERE knowledge_base_id = ?
                    """, knowledgeBaseId);
            int deleted = jdbcTemplate.update("DELETE FROM semantic_cache_store WHERE knowledge_base_id = ?", knowledgeBaseId);
            for (Long cacheId : cacheIds) {
                deleted += jdbcTemplate.update("DELETE FROM semantic_cache_store WHERE id = ?", cacheId);
            }
            log.debug("Evicted {} semantic cache entries and {} exact cache entries for knowledgeBase {}",
                    deleted, evictedExact, knowledgeBaseId);
            return deleted + deletedRel;
        } catch (Exception e) {
            RagFallbackMonitor.record("semantic_cache", "skip_evict", "eviction failed: " + e.getMessage());
            log.warn("Semantic cache eviction failed for knowledgeBase={}", knowledgeBaseId, e);
            return 0;
        }
    }

    private float[] embed(String query, EmbeddingModel embeddingModel) {
        try {
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(query), null));
            if (response == null || response.getResults().isEmpty()) {
                return new float[0];
            }
            float[] output = response.getResults().get(0).getOutput();
            return output != null ? output : new float[0];
        } catch (Exception e) {
            RagFallbackMonitor.record("semantic_cache", "bypass", "embedding failed: " + e.getMessage());
            log.warn("Semantic cache embedding failed", e);
            return new float[0];
        }
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }

    private int embeddingDimension() {
        int dimension = config.getEmbeddingDimension();
        return dimension > 0 ? dimension : DEFAULT_EMBEDDING_DIMENSION;
    }

    private String buildExactCacheKey(Long workspaceId, Long botId, String knowledgeIdsHash, String modelName, String query) {
        String normalized = query.trim().toLowerCase().replaceAll("\\s+", " ");
        return workspaceId + ":" + botId + ":" + knowledgeIdsHash + ":" + modelName + ":" + sha256(normalized);
    }

    private void putExactCache(String exactKey, CacheHit hit) {
        if (exactKey != null && hit != null) {
            exactCache.put(exactKey, hit);
        }
    }

    private CacheHit getExactCache(String exactKey) {
        if (exactKey == null) {
            return null;
        }
        CacheHit hit = exactCache.get(exactKey);
        if (hit != null && hit.getExpiresAt() != null && hit.getExpiresAt().isBefore(Instant.now())) {
            exactCache.remove(exactKey);
            return null;
        }
        return hit;
    }

    private int evictExactCacheByKnowledgeBase(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return 0;
        }
        int removed = 0;
        Iterator<java.util.Map.Entry<String, CacheHit>> iterator = exactCache.entrySet().iterator();
        while (iterator.hasNext()) {
            CacheHit hit = iterator.next().getValue();
            if (hit != null && hit.getKnowledgeBaseIds() != null
                    && hit.getKnowledgeBaseIds().contains(knowledgeBaseId)) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    private String sha256(String input) {
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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheHit {
        private Long id;
        private String query;
        private String answer;
        private String sourcesJson;
        private double similarity;
        private Instant expiresAt;
        private List<Long> knowledgeBaseIds;
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "qknow.rag.semantic-cache")
    public static class SemanticCacheConfig {
        private boolean enabled = true;
        private double threshold = DEFAULT_THRESHOLD;
        private Duration ttl = Duration.ofHours(24);
        private int embeddingDimension = DEFAULT_EMBEDDING_DIMENSION;
    }
}
