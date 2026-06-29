package tech.qiantong.qknow.module.kmc.service.rag;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 时序事实管理服务（Graphiti 风格）
 * 每个事实有 validity window（生效/失效时间）
 * 信息变更时旧事实自动标记失效（非删除）
 */
@Slf4j
@Component
public class TemporalFactService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        ensureTableExists();
    }

    /**
     * 存储时序事实
     */
    public void storeFact(Long segmentId, Long documentId, String entityName,
                          String factContent, long validFrom, long validUntil) {
        try {
            ensureTableExists();
            jdbcTemplate.update("""
                    INSERT INTO kmc_temporal_facts(segment_id, document_id, entity_name, fact_content, valid_from, valid_until, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (segment_id, entity_name) DO UPDATE SET
                        fact_content = EXCLUDED.fact_content,
                        valid_from = EXCLUDED.valid_from,
                        valid_until = EXCLUDED.valid_until,
                        updated_at = CURRENT_TIMESTAMP
                    """, segmentId, documentId, entityName, factContent, validFrom, validUntil, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("Failed to store temporal fact: {}", e.getMessage());
        }
    }

    /**
     * 标记旧事实失效（当新信息到达时）
     */
    public void invalidateFact(Long segmentId, String entityName) {
        try {
            ensureTableExists();
            jdbcTemplate.update("""
                    UPDATE kmc_temporal_facts
                    SET valid_until = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE segment_id = ? AND entity_name = ? AND (valid_until IS NULL OR valid_until > ?)
                    """, System.currentTimeMillis(), segmentId, entityName, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("Failed to invalidate fact: {}", e.getMessage());
        }
    }

    /**
     * 检索有效期内的事实
     */
    public List<TemporalFact> retrieveValidFacts(String entityName, long currentTime) {
        try {
            ensureTableExists();
            return jdbcTemplate.query("""
                    SELECT id, segment_id, document_id, entity_name, fact_content, valid_from, valid_until
                    FROM kmc_temporal_facts
                    WHERE entity_name = ?
                      AND (valid_from IS NULL OR valid_from <= ?)
                      AND (valid_until IS NULL OR valid_until >= ?)
                    ORDER BY valid_from DESC
                    """, (rs, rowNum) -> {
                TemporalFact fact = new TemporalFact();
                fact.setId(rs.getLong("id"));
                fact.setSegmentId(rs.getLong("segment_id"));
                fact.setDocumentId(rs.getLong("document_id"));
                fact.setEntityName(rs.getString("entity_name"));
                fact.setFactContent(rs.getString("fact_content"));
                fact.setValidFrom(rs.getLong("valid_from"));
                fact.setValidUntil(rs.getLong("valid_until"));
                return fact;
            }, entityName, currentTime, currentTime);
        } catch (Exception e) {
            log.warn("Failed to retrieve temporal facts: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 创建时序事实表（如果不存在）
     */
    public void ensureTableExists() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS kmc_temporal_facts (
                        id BIGSERIAL PRIMARY KEY,
                        segment_id BIGINT NOT NULL,
                        document_id BIGINT NOT NULL,
                        entity_name VARCHAR(255) NOT NULL,
                        fact_content TEXT,
                        valid_from BIGINT,
                        valid_until BIGINT,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT,
                        UNIQUE(segment_id, entity_name)
                    )
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_temporal_facts_entity ON kmc_temporal_facts(entity_name)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_temporal_facts_validity ON kmc_temporal_facts(valid_from, valid_until)");
        } catch (Exception e) {
            log.warn("Failed to create temporal facts table: {}", e.getMessage());
        }
    }

    @Data
    public static class TemporalFact {
        private Long id;
        private Long segmentId;
        private Long documentId;
        private String entityName;
        private String factContent;
        private long validFrom;
        private long validUntil;
    }
}
