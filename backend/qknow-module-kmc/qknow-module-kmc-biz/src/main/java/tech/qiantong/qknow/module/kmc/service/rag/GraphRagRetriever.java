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

@Slf4j
@Component
public class GraphRagRetriever {

    private static final double GRAPH_SCORE = 12.0;

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
