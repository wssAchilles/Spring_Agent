package tech.qiantong.qknow.module.kg.service;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 知识图谱社区检测服务
 * 参考：Microsoft GraphRAG Leiden 社区检测（34k⭐）
 * 参考：LightRAG 轻量级图构建（37k⭐）
 *
 * 使用 Neo4j GDS 库的 Leiden 算法检测实体社区，
 * 为每个社区生成摘要，支持 GraphRAG 式全局问答。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.neo4j.uri", havingValue = "", matchIfMissing = false)
public class GraphCommunityService {

    private final Driver driver;

    public GraphCommunityService(org.neo4j.driver.Driver driver) {
        this.driver = driver;
    }

    /**
     * 执行 Leiden 社区检测
     * 使用 Neo4j GDS 库的 leiden 算法
     */
    public List<Community> detectCommunities(String workspaceId) {
        try (Session session = driver.session()) {
            // 1. 创建投影图（仅包含有效实体和关系）
            session.run("""
                CALL gds.graph.project.cypher(
                    'community-graph-%s',
                    'MATCH (n:KgNode) WHERE n.workspace_id = %s AND n.del_flag = 0 RETURN id(n) AS id, labels(n) AS labels, n.name AS name',
                    'MATCH (a:KgNode)-[r:KgEdge]->(b:KgNode) WHERE a.workspace_id = %s AND b.workspace_id = %s RETURN id(a) AS source, id(b) AS target, type(r) AS type'
                )
                """.formatted(workspaceId, workspaceId, workspaceId, workspaceId));

            // 2. 执行 Leiden 算法
            var result = session.run("""
                CALL gds.leiden.stream('community-graph-%s')
                YIELD nodeId, communityId
                RETURN gds.util.asNode(nodeId).name AS entityName,
                       gds.util.asNode(nodeId).label AS entityLabel,
                       communityId
                ORDER BY communityId, entityName
                """.formatted(workspaceId));

            // 3. 按社区分组
            Map<Long, List<String>> communityEntities = new HashMap<>();
            Map<Long, List<String>> communityLabels = new HashMap<>();
            while (result.hasNext()) {
                var record = result.next();
                long communityId = record.get("communityId").asLong();
                String entityName = record.get("entityName").asString();
                String entityLabel = record.get("entityLabel").asString();
                communityEntities.computeIfAbsent(communityId, k -> new ArrayList<>()).add(entityName);
                communityLabels.computeIfAbsent(communityId, k -> new ArrayList<>()).add(entityLabel);
            }

            // 4. 清理投影图
            session.run("CALL gds.graph.drop('community-graph-%s')".formatted(workspaceId));

            // 5. 构建社区列表
            List<Community> communities = new ArrayList<>();
            for (var entry : communityEntities.entrySet()) {
                Community community = new Community();
                community.setId(entry.getKey());
                community.setEntities(entry.getValue());
                community.setLabels(communityLabels.getOrDefault(entry.getKey(), List.of()));
                community.setSize(entry.getValue().size());
                communities.add(community);
            }

            log.info("社区检测完成: workspaceId={}, communities={}", workspaceId, communities.size());
            return communities;
        }
    }

    /**
     * 保存社区到数据库
     */
    public void saveCommunities(String workspaceId, List<Community> communities) {
        try (Session session = driver.session()) {
            for (Community community : communities) {
                session.run("""
                    MERGE (c:Community {workspace_id: $workspaceId, community_id: $communityId})
                    SET c.size = $size,
                        c.entities = $entities,
                        c.labels = $labels,
                        c.updated_at = datetime()
                    """,
                    Values.parameters(
                        "workspaceId", workspaceId,
                        "communityId", community.getId(),
                        "size", community.getSize(),
                        "entities", community.getEntities(),
                        "labels", community.getLabels()
                    ));
            }
            log.info("社区保存完成: workspaceId={}, count={}", workspaceId, communities.size());
        }
    }

    /**
     * 社区数据模型
     */
    public static class Community {
        private long id;
        private List<String> entities;
        private List<String> labels;
        private int size;
        private String summary;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public List<String> getEntities() { return entities; }
        public void setEntities(List<String> entities) { this.entities = entities; }
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }
}
