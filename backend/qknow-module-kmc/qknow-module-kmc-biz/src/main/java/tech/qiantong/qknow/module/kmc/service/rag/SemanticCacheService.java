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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class SemanticCacheService {

    private static final double DEFAULT_THRESHOLD = 0.95D;

    @Resource
    private JdbcTemplate jdbcTemplate;

    public Optional<CacheHit> findAnswer(Long workspaceId, Long botId, Long knowledgeBaseId, String knowledgeIdsHash,
                                         String query, String modelName, EmbeddingModel embeddingModel) {
        float[] embedding = embed(query, embeddingModel);
        if (embedding.length == 0) {
            return Optional.empty();
        }

        String sql = """
                SELECT id, answer, 1 - (query_embedding <=> ?::vector) AS similarity
                FROM semantic_cache_store
                WHERE workspace_id = ?
                  AND bot_id = ?
                  AND knowledge_base_id = ?
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
                    .similarity(rs.getDouble("similarity"))
                    .build(), vector, workspaceId, botId, knowledgeBaseId, knowledgeIdsHash, modelName,
                    vector, DEFAULT_THRESHOLD, vector);
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
        float[] embedding = embed(query, embeddingModel);
        if (embedding.length == 0) {
            return;
        }

        String sql = """
                INSERT INTO semantic_cache_store (
                    workspace_id, bot_id, knowledge_base_id, knowledge_ids_hash, query,
                    query_embedding, answer, model_name, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?::vector, ?, ?, CURRENT_TIMESTAMP + (? || ' seconds')::interval)
                """;
        try {
            jdbcTemplate.update(sql, workspaceId, botId, knowledgeBaseId, knowledgeIdsHash, query,
                    toVectorLiteral(embedding), answer, modelName, ttl.toSeconds());
        } catch (Exception e) {
            log.warn("Semantic cache write failed", e);
        }
    }

    public int evictByKnowledgeBase(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return 0;
        }
        try {
            int deleted = jdbcTemplate.update("DELETE FROM semantic_cache_store WHERE knowledge_base_id = ?", knowledgeBaseId);
            log.debug("Evicted {} semantic cache entries for knowledgeBase {}", deleted, knowledgeBaseId);
            return deleted;
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
        private double similarity;
    }
}
