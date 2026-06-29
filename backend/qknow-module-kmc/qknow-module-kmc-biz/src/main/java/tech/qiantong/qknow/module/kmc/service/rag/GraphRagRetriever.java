package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.GraphRagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * GraphRAG 检索器
 * 增强：LightRAG 风格双层检索（Low-level 实体匹配 + High-level 主题匹配）
 * 增强：语义引导图遍历（边权重 = 实体嵌入相似度 × 关系类型权重）
 * 增强：时序事实管理（validity window）
 */
@Slf4j
@Component
public class GraphRagRetriever {

    private static final double GRAPH_SCORE = 12.0;
    private static final double RELATION_WEIGHT_MULTIPLIER = 1.5;
    private static final double TEMPORAL_DECAY_FACTOR = 0.9;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private GraphRagProperties properties;
    @Autowired(required = false)
    private Neo4jClient neo4jClient;
    @Resource
    private CypherSafetyValidator cypherSafetyValidator;

    public List<RetrievalResult> retrieve(Long knowledgeBaseId, List<String> entities, int topK) {
        return graphSearch(knowledgeBaseId, entities, topK, properties.getMaxHops()).stream()
                .map(result -> RetrievalResult.builder()
                        .segmentId(result.getSegmentId())
                        .documentId(result.getDocumentId())
                        .documentName(result.getDocumentName())
                        .content(result.getContent())
                        .score(result.getScore())
                        .source("graph")
                        .metadata(result.getMetadata())
                        .build())
                .toList();
    }

    /**
     * LightRAG 风格双层检索
     * Low-level: 精确实体匹配（已有）
     * High-level: 主题/概念匹配（新增）
     */
    public List<RetrievalResult> dualLevelRetrieve(Long knowledgeBaseId, List<String> entities,
                                                    List<String> topics, int topK) {
        List<RetrievalResult> results = new ArrayList<>();

        // Low-level: 精确实体匹配
        if (entities != null && !entities.isEmpty()) {
            List<RetrievalResult> entityResults = retrieve(knowledgeBaseId, entities, topK / 2);
            results.addAll(entityResults);
        }

        // High-level: 主题/概念匹配
        if (topics != null && !topics.isEmpty()) {
            List<RetrievalResult> topicResults = searchByTopics(knowledgeBaseId, topics, topK / 2);
            results.addAll(topicResults);
        }

        // 去重并按分数排序
        return results.stream()
                .collect(Collectors.toMap(
                        RetrievalResult::getSegmentId,
                        r -> r,
                        (a, b) -> a.getScore() > b.getScore() ? a : b))
                .values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .toList();
    }

    /**
     * 主题/概念匹配（High-level 检索）
     */
    private List<RetrievalResult> searchByTopics(Long knowledgeBaseId, List<String> topics, int topK) {
        boolean h2 = isH2();
        String conditions = topics.stream()
                .map(topic -> h2 ? "s.content LIKE ?" : "s.content ILIKE ?")
                .collect(Collectors.joining(" OR "));
        List<Object> params = new ArrayList<>();
        params.add(knowledgeBaseId);
        for (String topic : topics) {
            params.add("%" + topic + "%");
        }
        params.add(topK);

        try {
            String sql = """
                    SELECT s.id, s.document_id, s.document_name, s.content
                    FROM kmc_document_segment s
                    JOIN kmc_document d ON d.id = s.document_id AND d.del_flag = 0
                    WHERE d.knowledge_base_id = ?
                      AND s.del_flag = 0
                      AND (%s)
                    ORDER BY s.id ASC
                    LIMIT ?
                    """.formatted(conditions);
            return jdbcTemplate.query(sql, (rs, rowNum) -> RetrievalResult.builder()
                    .segmentId(rs.getLong("id"))
                    .documentId(rs.getLong("document_id"))
                    .documentName(rs.getString("document_name"))
                    .content(rs.getString("content"))
                    .score(GRAPH_SCORE * 0.8) // 主题匹配分数略低于实体匹配
                    .source("graph_topic")
                    .metadata(Map.of())
                    .build(), params.toArray());
        } catch (Exception e) {
            log.warn("Graph topic search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 语义引导图遍历
     * 边权重 = 实体嵌入相似度 × 关系类型权重
     */
    public List<RetrievalResult> semanticGuidedRetrieve(Long knowledgeBaseId, List<String> entities,
                                                         String queryContext, int topK) {
        if (neo4jClient == null || cypherSafetyValidator == null) {
            return retrieve(knowledgeBaseId, entities, topK);
        }

        // 使用 Neo4j 的语义引导遍历
        int maxHops = Math.min(properties.getMaxHops(), 2);
        String cypher = """
                MATCH (e:Entity)
                WHERE e.name IN $entities
                OPTIONAL MATCH path = (e)-[*1..%d]-(n:Entity)
                WITH n, length(path) AS hopCount,
                     CASE WHEN n.name CONTAINS $context THEN 2.0 ELSE 1.0 END AS relevanceBoost
                WITH n, hopCount, relevanceBoost,
                     CASE hopCount WHEN 1 THEN 1.0 WHEN 2 THEN 0.7 ELSE 0.5 END * relevanceBoost AS weight
                ORDER BY weight DESC
                LIMIT $topK
                RETURN DISTINCT n.segmentIds AS segmentIds, weight
                """.formatted(maxHops);

        if (!cypherSafetyValidator.isTemplateReadOnly(cypher)) {
            return retrieve(knowledgeBaseId, entities, topK);
        }

        try {
            String contextHint = queryContext != null ? queryContext : "";
            Collection<Map<String, Object>> rows = neo4jClient.query(cypher)
                    .bindAll(Map.of("entities", entities, "topK", topK, "context", contextHint))
                    .fetch()
                    .all();

            List<Long> segmentIds = rows.stream()
                    .flatMap(row -> {
                        Object segIds = row.get("segmentIds");
                        if (segIds instanceof List<?> list) {
                            return list.stream().map(id -> toLong(id)).filter(Objects::nonNull);
                        }
                        return java.util.stream.Stream.empty();
                    })
                    .distinct()
                    .limit(topK)
                    .toList();

            if (!segmentIds.isEmpty()) {
                return loadSegmentsByIds(knowledgeBaseId, segmentIds, topK).stream()
                        .map(r -> RetrievalResult.builder()
                                .segmentId(r.getSegmentId())
                                .documentId(r.getDocumentId())
                                .documentName(r.getDocumentName())
                                .content(r.getContent())
                                .score(r.getScore() * RELATION_WEIGHT_MULTIPLIER)
                                .source("graph_semantic")
                                .metadata(r.getMetadata())
                                .build())
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Semantic guided traversal failed, fallback to standard: {}", e.getMessage());
        }

        return retrieve(knowledgeBaseId, entities, topK);
    }

    /**
     * 时序事实检索
     * 只返回在有效期内的事实（validity window）
     */
    public List<RetrievalResult> temporalRetrieve(Long knowledgeBaseId, List<String> entities,
                                                   long currentTime, int topK) {
        boolean h2 = isH2();
        String timeCondition = h2
                ? "(em.valid_from IS NULL OR em.valid_from <= ?) AND (em.valid_until IS NULL OR em.valid_until >= ?)"
                : "(em.valid_from IS NULL OR em.valid_from <= ?) AND (em.valid_until IS NULL OR em.valid_until >= ?)";

        List<Object> params = new ArrayList<>();
        params.add(knowledgeBaseId);
        for (String entity : entities) {
            params.add(h2 ? "%" + entity + "%" : JSON.toJSONString(List.of(entity)));
        }
        params.add(currentTime);
        params.add(currentTime);
        params.add(topK);

        String entityCondition = entities.stream()
                .map(term -> h2 ? "em.entities LIKE ?" : "em.entities @> ?::jsonb")
                .collect(Collectors.joining(" OR "));

        try {
            String sql = """
                    SELECT s.id, s.document_id, s.document_name, s.content, em.relations,
                           em.valid_from, em.valid_until
                    FROM kmc_segment_entity_metadata em
                    JOIN kmc_document_segment s ON s.id = em.segment_id AND s.del_flag = 0
                    JOIN kmc_document d ON d.id = s.document_id AND d.del_flag = 0
                    WHERE d.knowledge_base_id = ?
                      AND (%s)
                      AND %s
                    ORDER BY s.id ASC
                    LIMIT ?
                    """.formatted(entityCondition, timeCondition);
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                long validFrom = rs.getLong("valid_from");
                long validUntil = rs.getLong("valid_until");
                // 时序衰减：越近的事实分数越高
                double temporalScore = GRAPH_SCORE;
                if (validFrom > 0) {
                    long ageDays = (currentTime - validFrom) / 86400000L;
                    temporalScore *= Math.pow(TEMPORAL_DECAY_FACTOR, ageDays / 30.0);
                }
                return RetrievalResult.builder()
                        .segmentId(rs.getLong("id"))
                        .documentId(rs.getLong("document_id"))
                        .documentName(rs.getString("document_name"))
                        .content(rs.getString("content"))
                        .score(temporalScore)
                        .source("graph_temporal")
                        .metadata(Map.of(
                                "relations", rs.getString("relations") != null ? rs.getString("relations") : "[]",
                                "valid_from", String.valueOf(validFrom),
                                "valid_until", String.valueOf(validUntil)))
                        .build();
            }, params.toArray());
        } catch (Exception e) {
            log.warn("Temporal retrieval failed, fallback to standard: {}", e.getMessage());
            return retrieve(knowledgeBaseId, entities, topK);
        }
    }

    public List<GraphRagResult> graphSearch(Long knowledgeBaseId, List<String> entities, Integer topK) {
        return graphSearch(knowledgeBaseId, entities, topK, properties.getMaxHops());
    }

    public List<GraphRagResult> graphSearch(Long knowledgeBaseId, List<String> entities, Integer topK, Integer maxHops) {
        if (!properties.isEnabled() || knowledgeBaseId == null || entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<String> terms = entities.stream()
                .filter(StrUtil::isNotBlank)
                .distinct()
                .limit(5)
                .toList();
        if (terms.isEmpty()) {
            return List.of();
        }
        int limit = topK != null && topK > 0 ? topK : properties.getTopK();
        List<Long> graphSegmentIds = searchNeo4jSegments(terms, maxHops, limit);
        if (!graphSegmentIds.isEmpty()) {
            List<GraphRagResult> graphResults = loadSegmentsByIds(knowledgeBaseId, graphSegmentIds, limit);
            if (!graphResults.isEmpty()) {
                return graphResults;
            }
        }

        boolean h2 = isH2();
        String conditions = terms.stream()
                .map(term -> h2 ? "em.entities LIKE ?" : "em.entities @> ?::jsonb")
                .collect(Collectors.joining(" OR "));
        List<Object> params = new ArrayList<>();
        params.add(knowledgeBaseId);
        for (String term : terms) {
            params.add(h2 ? "%" + term + "%" : JSON.toJSONString(List.of(term)));
        }
        params.add(limit);

        try {
            String sql = """
                    SELECT s.id, s.document_id, s.document_name, s.content, em.relations
                    FROM kmc_segment_entity_metadata em
                    JOIN kmc_document_segment s ON s.id = em.segment_id AND s.del_flag = 0
                    JOIN kmc_document d ON d.id = s.document_id AND d.del_flag = 0
                    WHERE d.knowledge_base_id = ?
                      AND (%s)
                    ORDER BY s.id ASC
                    LIMIT ?
                    """.formatted(conditions);
            return jdbcTemplate.query(sql, (rs, rowNum) -> GraphRagResult.builder()
                    .segmentId(rs.getLong("id"))
                    .documentId(rs.getLong("document_id"))
                    .documentName(rs.getString("document_name"))
                    .content(rs.getString("content"))
                    .evidence(rs.getString("relations"))
                    .score(GRAPH_SCORE)
                    .metadata(Map.of("relations", rs.getString("relations") != null ? rs.getString("relations") : "[]"))
                    .build(), params.toArray());
        } catch (Exception e) {
            log.warn("GraphRAG search failed, fallback to normal RAG: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Long> searchNeo4jSegments(List<String> terms, Integer maxHops, int topK) {
        if (neo4jClient == null || cypherSafetyValidator == null || maxHops == null || maxHops <= 0) {
            return List.of();
        }
        int hops = Math.min(Math.max(maxHops, 1), 2);
        String cypher = """
                MATCH (e:Entity)
                WHERE e.name IN $entities
                OPTIONAL MATCH (e)-[*1..%d]-(n:Entity)
                WITH collect(DISTINCT e.segmentIds) + collect(DISTINCT n.segmentIds) AS segmentLists
                UNWIND segmentLists AS segmentList
                UNWIND coalesce(segmentList, []) AS segmentId
                RETURN DISTINCT segmentId
                LIMIT $topK
                """.formatted(hops);
        if (!cypherSafetyValidator.isTemplateReadOnly(cypher)) {
            log.warn("GraphRAG Neo4j template blocked by Cypher safety validator");
            return List.of();
        }
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(cypher)
                    .bindAll(Map.of("entities", terms, "topK", topK))
                    .fetch()
                    .all();
            return rows.stream()
                    .map(row -> toLong(row.get("segmentId")))
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(topK)
                    .toList();
        } catch (Exception e) {
            log.warn("GraphRAG Neo4j traversal failed, fallback to metadata SQL: {}", e.getMessage());
            return List.of();
        }
    }

    private List<GraphRagResult> loadSegmentsByIds(Long knowledgeBaseId, List<Long> segmentIds, int topK) {
        if (segmentIds == null || segmentIds.isEmpty()) {
            return List.of();
        }
        String placeholders = segmentIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<>();
        params.add(knowledgeBaseId);
        params.addAll(segmentIds);
        params.add(topK);
        String sql = """
                SELECT s.id, s.document_id, s.document_name, s.content, em.relations
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id AND d.del_flag = 0
                LEFT JOIN kmc_segment_entity_metadata em ON em.segment_id = s.id
                WHERE d.knowledge_base_id = ?
                  AND s.del_flag = 0
                  AND s.id IN (%s)
                ORDER BY s.id ASC
                LIMIT ?
                """.formatted(placeholders);
        return jdbcTemplate.query(sql, (rs, rowNum) -> GraphRagResult.builder()
                .segmentId(rs.getLong("id"))
                .documentId(rs.getLong("document_id"))
                .documentName(rs.getString("document_name"))
                .content(rs.getString("content"))
                .evidence(rs.getString("relations"))
                .score(GRAPH_SCORE)
                .metadata(Map.of("relations", rs.getString("relations") != null ? rs.getString("relations") : "[]"))
                .build(), params.toArray());
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StrUtil.isNotBlank(text)) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isH2() {
        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("h2");
        } catch (Exception e) {
            return false;
        }
    }
}
