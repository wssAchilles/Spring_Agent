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

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class SemanticCacheService {

    private static final double DEFAULT_THRESHOLD = 0.95D;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private SemanticCacheConfig config;

    public Optional<CacheHit> findAnswer(Long workspaceId, Long botId, Long knowledgeBaseId, String knowledgeIdsHash,
                                         String query, String modelName, EmbeddingModel embeddingModel) {
        return findAnswer(workspaceId, botId, List.of(knowledgeBaseId), knowledgeIdsHash, query, modelName, embeddingModel);
    }

    public Optional<CacheHit> findAnswer(Long workspaceId, Long botId, List<Long> knowledgeBaseIds, String knowledgeIdsHash,
                                         String query, String modelName, EmbeddingModel embeddingModel) {
        if (!config.isEnabled()) {
            return Optional.empty();
        }
        float[] embedding = embed(query, embeddingModel);
        if (embedding.length == 0) {
            return Optional.empty();
        }

        String sql = """
                SELECT id, answer, sources_json, 1 - (query_embedding <=> ?::vector) AS similarity
                FROM semantic_cache_store
                WHERE workspace_id = ?
                  AND bot_id = ?
                  AND knowledge_ids_hash = ?
                  AND model_name = ?
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                  AND 1 - (query_embedding <=> ?::vector) >= ?
                ORDER BY query_embedding <=> ?::vector
                LIMIT 1
                """;
        String vector = toVectorLiteral(embedding);
        try {
            List<CacheHit> hits = jdbcTemplate.query(sql, (rs, rowNum) -> CacheHit.builder()
                    .id(rs.getLong("id"))
                    .answer(rs.getString("answer"))
                    .sourcesJson(rs.getString("sources_json"))
                    .similarity(rs.getDouble("similarity"))
                    .build(), vector, workspaceId, botId, knowledgeIdsHash, modelName,
                    vector, config.getThreshold(), vector);
            if (CollUtil.isEmpty(hits)) {
                return Optional.empty();
            }
            jdbcTemplate.update("UPDATE semantic_cache_store SET hit_count = hit_count + 1, last_hit_at = CURRENT_TIMESTAMP WHERE id = ?",
                    hits.get(0).getId());
            return Optional.of(hits.get(0));
        } catch (Exception e) {
            log.warn("Semantic cache lookup failed, bypassing cache", e);
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
        if (embedding.length == 0) {
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
            }
        } catch (Exception e) {
            log.warn("Semantic cache write failed", e);
        }
    }

    public int evictByKnowledgeBase(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return 0;
        }
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
            log.debug("Evicted {} semantic cache entries for knowledgeBase {}", deleted, knowledgeBaseId);
            return deleted + deletedRel;
        } catch (Exception e) {
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
            return response.getResults().get(0).getOutput();
        } catch (Exception e) {
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheHit {
        private Long id;
        private String answer;
        private String sourcesJson;
        private double similarity;
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "qknow.rag.semantic-cache")
    public static class SemanticCacheConfig {
        private boolean enabled = true;
        private double threshold = DEFAULT_THRESHOLD;
        private Duration ttl = Duration.ofHours(24);
    }
}
