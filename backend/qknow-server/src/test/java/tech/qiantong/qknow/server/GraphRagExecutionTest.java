package tech.qiantong.qknow.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tech.qiantong.qknow.module.kg.service.GraphCommunityService;
import tech.qiantong.qknow.module.kmc.service.rag.GraphRagSyncService;
import org.springframework.ai.chat.model.ChatModel;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.service.rag.EntityExtractionService;
import org.springframework.ai.document.Document;
import tech.qiantong.qknow.ai.transformer.RecursiveSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.alibaba.fastjson2.JSON;

@SpringBootTest(classes = QKnowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GraphRagExecutionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GraphRagSyncService graphRagSyncService;

    @Autowired
    private GraphCommunityService graphCommunityService;

    @Autowired(required = false)
    private IAiModelApiService aiModelApiService;

    @Autowired
    private EntityExtractionService entityExtractionService;

    @Test
    public void executeExtractionForDoc25() {
        System.out.println("========== STARTING GRAPH RAG EXTRACTION FOR DOC 25 ==========");
        
        List<Map<String, Object>> segments = jdbcTemplate.queryForList(
                "SELECT s.id, s.document_id, s.qm_segment_id, s.content, d.workspace_id " +
                "FROM kmc_document_segment s " +
                "JOIN kmc_document d ON s.document_id = d.id " +
                "WHERE s.document_id = 25");
                
        System.out.println("Loaded " + segments.size() + " segments from Document 25.");
        
        ChatModel chatModel = null;
        if (aiModelApiService != null) {
            try {
                chatModel = aiModelApiService.getChatModel(1L, "deepseek-chat");
                System.out.println("Loaded DeepSeek Chat Model: " + chatModel);
            } catch(Exception e) {
                System.out.println("Error loading Chat Model: " + e.getMessage());
            }
        }
        
        List<Document> documents = new ArrayList<>();
        String workspaceId = "1001";
        for (Map<String, Object> segment : segments) {
            String content = (String) segment.get("content");
            if (content == null) continue;
            if (segment.get("workspace_id") != null) {
                workspaceId = String.valueOf(segment.get("workspace_id"));
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("db_id", segment.get("id"));
            metadata.put("document_id", segment.get("document_id"));
            metadata.put("qm_segment_id", segment.get("qm_segment_id"));
            metadata.put(RecursiveSplitter.METADATA_CHUNK_LEVEL, RecursiveSplitter.CHUNK_LEVEL_PARENT);
            documents.add(new Document(content, metadata));
        }
        
        System.out.println("Running Entity Extraction...");
        List<Document> enrichedDocs = entityExtractionService.enrichParentChildMetadata(documents, chatModel);
        
        List<Object[]> batchArgs = new ArrayList<>();
        for (Document doc : enrichedDocs) {
            Map<String, Object> meta = doc.getMetadata();
            batchArgs.add(new Object[]{
                    meta.get("document_id"),
                    meta.get("db_id"),
                    meta.get("qm_segment_id"),
                    JSON.toJSONString(meta.getOrDefault("entities", List.of())),
                    JSON.toJSONString(meta.getOrDefault("relations", List.of()))
            });
        }
        
        System.out.println("Saving extracted entities to kmc_segment_entity_metadata...");
        jdbcTemplate.update("DELETE FROM kmc_segment_entity_metadata WHERE document_id = 25");
        jdbcTemplate.batchUpdate(
                "INSERT INTO kmc_segment_entity_metadata(document_id, segment_id, qm_segment_id, entities, relations) " +
                "VALUES (?, ?, ?, ?::jsonb, ?::jsonb)", batchArgs);
                
        System.out.println("Syncing to Neo4j...");
        int migrated = graphRagSyncService.migrateExistingMetadata();
        System.out.println("Migrated " + migrated + " relations to Neo4j.");
        
        System.out.println("Running Leiden Community Detection...");
        try {
            List<GraphCommunityService.Community> communities = graphCommunityService.detectCommunities(workspaceId);
            System.out.println("Generated " + communities.size() + " communities.");
            for(GraphCommunityService.Community c : communities) {
                System.out.println("Community " + c.getId() + ": " + c.getSummary());
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        System.out.println("========== GRAPH RAG EXTRACTION FINISHED ==========");
    }
}
