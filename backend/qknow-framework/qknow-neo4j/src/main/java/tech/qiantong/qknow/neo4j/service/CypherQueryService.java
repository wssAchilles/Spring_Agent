package tech.qiantong.qknow.neo4j.service;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Neo4j Cypher 查询服务
 * 提供通用的图数据库操作接口，供上层业务模块调用。
 */
@Slf4j
@Service
@ConditionalOnBean(name = "neo4jDriver")
public class CypherQueryService {

    private final Neo4jClient neo4jClient;

    public CypherQueryService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    /**
     * 执行只读 Cypher 查询，返回 List of Map
     */
    public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
        log.debug("Cypher query: {}", cypher);
        return neo4jClient.query(cypher)
                .bindAll(params != null ? params : Map.of())
                .fetch()
                .all()
                .stream()
                .map(row -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    row.forEach((k, v) -> map.put(k, convertValue(v)));
                    return map;
                })
                .toList();
    }

    /**
     * 执行只读 Cypher 查询（无参数）
     */
    public List<Map<String, Object>> query(String cypher) {
        return query(cypher, null);
    }

    /**
     * 执行写入 Cypher（CREATE/MERGE/DELETE/SET）
     */
    public boolean execute(String cypher, Map<String, Object> params) {
        log.debug("Cypher execute: {}", cypher);
        try {
            neo4jClient.query(cypher)
                    .bindAll(params != null ? params : Map.of())
                    .run();
            return true;
        } catch (Exception e) {
            log.error("Cypher execute failed: {}", cypher, e);
            return false;
        }
    }

    /**
     * 获取节点数量
     */
    public long getNodeCount() {
        var result = query("MATCH (n) RETURN count(n) AS count");
        return result.isEmpty() ? 0 : ((Number) result.get(0).get("count")).longValue();
    }

    /**
     * 获取关系数量
     */
    public long getRelationshipCount() {
        var result = query("MATCH ()-[r]->() RETURN count(r) AS count");
        return result.isEmpty() ? 0 : ((Number) result.get(0).get("count")).longValue();
    }

    /**
     * 获取所有标签
     */
    public List<String> getLabels() {
        var result = query("CALL db.labels() YIELD label RETURN label");
        return result.stream()
                .map(r -> (String) r.get("label"))
                .toList();
    }

    /**
     * 健康检查
     */
    public boolean isHealthy() {
        try {
            var result = query("RETURN 1 AS ok");
            return !result.isEmpty();
        } catch (Exception e) {
            log.warn("Neo4j health check failed", e);
            return false;
        }
    }

    /**
     * Neo4j 值类型转换为 Java 类型
     */
    private Object convertValue(Object value) {
        if (value == null) return null;
        if (value instanceof Node node) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("_id", node.id());
            map.put("_labels", node.labels());
            node.keys().forEach(k -> map.put(k, convertValue(node.get(k))));
            return map;
        }
        if (value instanceof Relationship rel) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("_id", rel.id());
            map.put("_type", rel.type());
            rel.keys().forEach(k -> map.put(k, convertValue(rel.get(k))));
            return map;
        }
        if (value instanceof Path path) {
            List<Map<String, Object>> nodes = new ArrayList<>();
            path.nodes().forEach(n -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("_id", n.id());
                m.put("_labels", n.labels());
                n.keys().forEach(k -> m.put(k, convertValue(n.get(k))));
                nodes.add(m);
            });
            return nodes;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::convertValue).toList();
        }
        return value;
    }
}
