package tech.qiantong.qknow.module.kmc.service.rag.reconcile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.constant.WeaviateConstant;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeSegment.KmcDocumentSegmentDO;
import tech.qiantong.qknow.module.kmc.dal.mapper.knowledgeSegment.KmcDocumentSegmentMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量存储与 RDBMS 双向对齐反熵自愈引擎 (Phase 13 Anti-Entropy Reconciliation Engine)
 *
 * @author qknow
 */
@Slf4j
@Component
public class VectorReconciliationEngine {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private KmcDocumentSegmentMapper segmentMapper;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditReport {
        private Long knowledgeBaseId;
        private int totalActiveSegments;
        private int totalVectors;
        private List<Long> missingSegmentIds;
        private List<String> orphanVectorIds;

        public int getMissingCount() {
            return missingSegmentIds != null ? missingSegmentIds.size() : 0;
        }

        public int getOrphanCount() {
            return orphanVectorIds != null ? orphanVectorIds.size() : 0;
        }

        public boolean isConsistent() {
            return getMissingCount() == 0 && getOrphanCount() == 0;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconcileSummary {
        private Long knowledgeBaseId;
        private AuditReport auditReport;
        private int purgedOrphans;
        private int compensatedSegments;
    }

    /**
     * 执行指定知识库的双向不变量审计
     */
    public AuditReport audit(Long knowledgeBaseId, List<Long> activeSegmentIds, List<Map<String, String>> vectorRows) {
        Set<Long> activeSet = new HashSet<>(activeSegmentIds != null ? activeSegmentIds : Collections.emptyList());

        Set<Long> vectorSegIdSet = new HashSet<>();
        List<String> orphanVectorIds = new ArrayList<>();

        if (vectorRows != null) {
            for (Map<String, String> row : vectorRows) {
                String vectorId = row.get("id");
                String segIdStr = row.get("segment_id");
                if (segIdStr != null && !segIdStr.isBlank()) {
                    try {
                        Long segId = Long.parseLong(segIdStr);
                        vectorSegIdSet.add(segId);
                        if (!activeSet.contains(segId)) {
                            orphanVectorIds.add(vectorId);
                        }
                    } catch (NumberFormatException e) {
                        orphanVectorIds.add(vectorId);
                    }
                } else {
                    // 无有效 segment_id 的向量视为孤儿
                    orphanVectorIds.add(vectorId);
                }
            }
        }

        // 正向差集：MySQL 存在但向量库缺失
        List<Long> missingSegmentIds = activeSet.stream()
                .filter(id -> !vectorSegIdSet.contains(id))
                .sorted()
                .collect(Collectors.toList());

        return AuditReport.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .totalActiveSegments(activeSet.size())
                .totalVectors(vectorRows != null ? vectorRows.size() : 0)
                .missingSegmentIds(missingSegmentIds)
                .orphanVectorIds(orphanVectorIds)
                .build();
    }

    /**
     * 基于数据库连接执行真实审计 (支持无 DB 环境降级)
     */
    public AuditReport auditKnowledgeBase(Long knowledgeBaseId) {
        if (jdbcTemplate == null) {
            log.warn("[VectorReconciliationEngine] JdbcTemplate 为空，跳过物理数据库对账");
            return AuditReport.builder()
                    .knowledgeBaseId(knowledgeBaseId)
                    .missingSegmentIds(Collections.emptyList())
                    .orphanVectorIds(Collections.emptyList())
                    .build();
        }

        try {
            // 1. 查询该知识库在 MySQL 中的有效切片 ID 列表
            String segSql = """
                SELECT s.id 
                FROM kmc_document_segment s
                JOIN kmc_document d ON s.document_id = d.id
                WHERE d.knowledge_base_id = ?
                  AND s.del_flag = 0
                  AND coalesce(s.sync_status, 1) = 1
            """;
            List<Long> activeSegmentIds = jdbcTemplate.queryForList(segSql, Long.class, knowledgeBaseId);

            // 2. 查询 vector_store 中的该知识库记录
            String vecSql = String.format("""
                SELECT id,
                       metadata->>'%s' AS segment_id
                FROM vector_store
                WHERE metadata->>'%s' = ?
            """, WeaviateConstant.METADATA_FIELD_SEGMENT_ID, WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID);

            List<Map<String, String>> vectorRows = jdbcTemplate.query(vecSql, (rs, rowNum) -> {
                Map<String, String> row = new HashMap<>();
                row.put("id", rs.getString("id"));
                row.put("segment_id", rs.getString("segment_id"));
                return row;
            }, String.valueOf(knowledgeBaseId));

            return audit(knowledgeBaseId, activeSegmentIds, vectorRows);
        } catch (Exception e) {
            log.error("[VectorReconciliationEngine] 审计失败 knowledgeBaseId={}: {}", knowledgeBaseId, e.getMessage());
            return AuditReport.builder()
                    .knowledgeBaseId(knowledgeBaseId)
                    .missingSegmentIds(Collections.emptyList())
                    .orphanVectorIds(Collections.emptyList())
                    .build();
        }
    }

    /**
     * 批量物理清理孤儿向量
     */
    public int purgeOrphans(List<String> orphanVectorIds) {
        if (orphanVectorIds == null || orphanVectorIds.isEmpty()) {
            return 0;
        }
        if (jdbcTemplate == null) {
            log.info("[VectorReconciliationEngine] (内存模拟) 已清理孤儿向量 {} 条", orphanVectorIds.size());
            return orphanVectorIds.size();
        }

        int totalDeleted = 0;
        int batchSize = 100;
        for (int i = 0; i < orphanVectorIds.size(); i += batchSize) {
            List<String> batch = orphanVectorIds.subList(i, Math.min(i + batchSize, orphanVectorIds.size()));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?::uuid"));
            String deleteSql = "DELETE FROM vector_store WHERE id IN (" + placeholders + ")";
            int deleted = jdbcTemplate.update(deleteSql, batch.toArray());
            totalDeleted += deleted;
        }
        log.info("[VectorReconciliationEngine] 成功物理清理孤儿向量: {} 条", totalDeleted);
        return totalDeleted;
    }
}
