package tech.qiantong.qknow.module.kmc.service.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GraphRagSyncService {

    @Resource
    private GraphRagProperties properties;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private Neo4jClient neo4jClient;

    public void syncRows(List<Object[]> rows) {
        if (!properties.isEnabled() || neo4jClient == null || rows == null || rows.isEmpty()) {
            return;
        }
        try {
            for (Object[] row : rows) {
                Long documentId = ((Number) row[0]).longValue();
                Long segmentId = ((Number) row[1]).longValue();
                JSONArray entities = JSON.parseArray(String.valueOf(row[3]));
                JSONArray relations = JSON.parseArray(String.valueOf(row[4]));
                for (Object entity : entities) {
                    upsertEntity(String.valueOf(entity), documentId, segmentId);
                }
                for (Object relationObject : relations) {
                    if (relationObject instanceof JSONObject relation) {
                        upsertRelation(relation, documentId, segmentId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("GraphRAG Neo4j sync failed, continuing document sync", e);
        }
    }

    public int migrateExistingMetadata() {
        List<Object[]> rows = jdbcTemplate.query("""
                SELECT document_id, segment_id, qm_segment_id, entities, relations
                FROM kmc_segment_entity_metadata
                ORDER BY id ASC
                """, (rs, rowNum) -> new Object[]{
                rs.getLong("document_id"),
                rs.getLong("segment_id"),
                rs.getString("qm_segment_id"),
                rs.getString("entities"),
                rs.getString("relations")
        });
        if (rows.isEmpty()) {
            log.info("GraphRAG migration skipped: kmc_segment_entity_metadata is empty");
            return 0;
        }
        syncRows(rows);
        return rows.size();
    }

    private void upsertEntity(String name, Long documentId, Long segmentId) {
        if (name == null || name.isBlank()) {
            return;
        }
        neo4jClient.query("""
                MERGE (e:Entity {name: $name})
                SET e.documentIds = CASE
                    WHEN e.documentIds IS NULL THEN [$documentId]
                    WHEN NOT ($documentId IN e.documentIds) THEN e.documentIds + $documentId
                    ELSE e.documentIds
                END,
                e.segmentIds = CASE
                    WHEN e.segmentIds IS NULL THEN [$segmentId]
                    WHEN NOT ($segmentId IN e.segmentIds) THEN e.segmentIds + $segmentId
                    ELSE e.segmentIds
                END
                """)
                .bindAll(Map.of("name", name, "documentId", documentId, "segmentId", segmentId))
                .run();
    }

    private void upsertRelation(JSONObject relation, Long documentId, Long segmentId) {
        String source = relation.getString("source");
        String target = relation.getString("target");
        String type = relation.getString("relation");
        if (source == null || target == null || type == null) {
            return;
        }
        neo4jClient.query("""
                MERGE (s:Entity {name: $source})
                MERGE (t:Entity {name: $target})
                MERGE (s)-[r:RELATED_TO {type: $type}]->(t)
                SET r.evidence = $evidence,
                r.documentIds = CASE
                    WHEN r.documentIds IS NULL THEN [$documentId]
                    WHEN NOT ($documentId IN r.documentIds) THEN r.documentIds + $documentId
                    ELSE r.documentIds
                END,
                r.segmentIds = CASE
                    WHEN r.segmentIds IS NULL THEN [$segmentId]
                    WHEN NOT ($segmentId IN r.segmentIds) THEN r.segmentIds + $segmentId
                    ELSE r.segmentIds
                END
                """)
                .bindAll(Map.of(
                        "source", source,
                        "target", target,
                        "type", type,
                        "evidence", relation.getString("evidence") != null ? relation.getString("evidence") : "",
                        "documentId", documentId,
                        "segmentId", segmentId
                ))
                .run();
    }
}
