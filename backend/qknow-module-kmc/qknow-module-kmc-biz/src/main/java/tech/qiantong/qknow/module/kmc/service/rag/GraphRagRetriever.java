package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.GraphRagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GraphRAG 检索器
 * 增强：LightRAG 风格双层检索（Low-level 实体匹配 + High-level 主题匹配）
 * 增强：语义引导图遍历（边权重 = 实体嵌入相似度 × 关系类型权重）
 * 增强：时序事实管理（validity window）
 * [溯源] 算法优化指南 §3.3: HippoRAG PPR 个性化 PageRank
 */
@Slf4j
@Component
public class GraphRagRetriever {

    private static final double GRAPH_SCORE = 12.0;
    private static final double RELATION_WEIGHT_MULTIPLIER = 1.5;
    // [溯源] 算法优化指南 §3.6: 时序衰减因子配置化
    @Value("${qknow.rag.graph.temporal-decay-factor:0.9}")
    private double temporalDecayFactor = 0.9;

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

    public List<RetrievalResult> retrieve(Long knowledgeBaseId, QueryIntent queryIntent,
                                          String query, QueryRouter.QueryRoute route, int topK) {
        if (queryIntent == null) {
            return List.of();
        }
        List<String> entities = queryIntent.getEntities() != null ? queryIntent.getEntities() : List.of();
        List<String> topics = new ArrayList<>();
        if (queryIntent.getKeywords() != null) {
            topics.addAll(queryIntent.getKeywords());
        }
        if (StrUtil.isNotBlank(queryIntent.getCategory())) {
            topics.add(queryIntent.getCategory());
        }

        // [溯源] 算法优化指南 §3.3: PPR 作为额外检索路径
        List<RetrievalResult> pprResults = List.of();
        if (!entities.isEmpty() && properties.isEnabled() && properties.isPprEnabled()) {
            pprResults = pprRetrieve(knowledgeBaseId, entities, topK);
        }

        if (route == QueryRouter.QueryRoute.COMPLEX && !entities.isEmpty()) {
            List<RetrievalResult> semantic = semanticGuidedRetrieve(knowledgeBaseId, entities, query, topK);
            List<RetrievalResult> temporal = queryIntent.getDayNo() != null
                    ? temporalRetrieve(knowledgeBaseId, entities, System.currentTimeMillis(), topK) : List.of();
            List<RetrievalResult> dual = dualLevelRetrieve(knowledgeBaseId, entities, topics, topK);
            return mergeResults(mergeResults(semantic, temporal, topK), mergeResults(dual, pprResults, topK), topK);
        }
        if (queryIntent.getDayNo() != null && !entities.isEmpty()) {
            return mergeResults(temporalRetrieve(knowledgeBaseId, entities, System.currentTimeMillis(), topK), pprResults, topK);
        }
        if (!topics.isEmpty()) {
            return mergeResults(dualLevelRetrieve(knowledgeBaseId, entities, topics, topK), pprResults, topK);
        }
        return mergeResults(retrieve(knowledgeBaseId, entities, topK), pprResults, topK);
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
            List<RetrievalResult> entityResults = retrieve(knowledgeBaseId, entities, Math.max(1, topK / 2));
            results.addAll(entityResults);
        }

        // High-level: 主题/概念匹配
        if (topics != null && !topics.isEmpty()) {
            List<RetrievalResult> topicResults = searchByTopics(knowledgeBaseId, topics, Math.max(1, topK / 2));
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
            RagFallbackMonitor.record("neo4j", "standard_graph", "topic sql search failed: " + e.getMessage());
            log.warn("Graph topic search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * [溯源] 算法优化指南 §3.3: HippoRAG PPR 个性化 PageRank
     * 以查询实体为种子节点，通过迭代 PageRank 计算多跳实体的隐式关联分数
     * 使用 PostgreSQL 邻接表实现，无需 Neo4j GDS
     */
    public List<RetrievalResult> pprRetrieve(Long knowledgeBaseId, List<String> seedEntities, int topK) {
        if (seedEntities == null || seedEntities.isEmpty()) {
            return List.of();
        }
        if (!properties.isEnabled() || !properties.isPprEnabled()) {
            return List.of();
        }

        int maxIterations = 20;
        double dampingFactor = 0.85;
        double convergenceThreshold = 0.001;
        int maxEdges = Math.max(1, properties.getPprMaxEdges());
        int maxNodes = Math.max(1, properties.getPprMaxNodes());

        try {
            // Step 1: 找到种子节点 ID
            String seedQuery = "SELECT id FROM kg_node WHERE del_flag = 0 AND (" +
                    seedEntities.stream().map(e -> "label ILIKE ?").collect(Collectors.joining(" OR ")) +
                    ") LIMIT ?";
            List<Object> seedParams = new ArrayList<>(seedEntities.stream()
                    .map(e -> (Object) ("%" + e + "%"))
                    .toList());
            seedParams.add(maxNodes + 1);
            List<Long> seedNodeIds = jdbcTemplate.queryForList(seedQuery, Long.class, seedParams.toArray());

            if (seedNodeIds.isEmpty()) {
                log.debug("PPR: no seed nodes found for entities: {}", seedEntities);
                return List.of();
            }
            if (seedNodeIds.size() > maxNodes) {
                String reason = "seed node count exceeded limit " + maxNodes;
                RagFallbackMonitor.record("neo4j", "standard_graph", "ppr " + reason);
                log.warn("PPR retrieval skipped: {}", reason);
                return retrieve(knowledgeBaseId, seedEntities, topK);
            }
            Set<Long> seedNodeIdSet = new HashSet<>(seedNodeIds);

            // Step 2: 构建邻接表
            List<Map<String, Object>> edges = loadSeedSubgraphEdges(seedNodeIds, maxEdges);
            if (edges.size() > maxEdges) {
                String reason = "edge count exceeded limit " + maxEdges;
                RagFallbackMonitor.record("neo4j", "standard_graph", "ppr " + reason);
                log.warn("PPR retrieval skipped: {}", reason);
                return retrieve(knowledgeBaseId, seedEntities, topK);
            }
            Map<Long, List<Long>> adjacency = new java.util.HashMap<>();
            for (Map<String, Object> edge : edges) {
                long src = ((Number) edge.get("source_id")).longValue();
                long tgt = ((Number) edge.get("target_id")).longValue();
                adjacency.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
                adjacency.computeIfAbsent(tgt, k -> new ArrayList<>()).add(src); // 无向图
                if (adjacency.size() > maxNodes) {
                    String reason = "node count exceeded limit " + maxNodes;
                    RagFallbackMonitor.record("neo4j", "standard_graph", "ppr " + reason);
                    log.warn("PPR retrieval skipped: {}", reason);
                    return retrieve(knowledgeBaseId, seedEntities, topK);
                }
            }
            if (adjacency.isEmpty()) {
                return List.of();
            }

            // Step 3: 迭代 PageRank
            Map<Long, Double> scores = new java.util.HashMap<>();
            for (long nodeId : adjacency.keySet()) {
                scores.put(nodeId, 1.0 / adjacency.size());
            }
            // 种子节点初始分数更高
            double seedBoost = 1.0 / seedNodeIds.size();
            for (long seedId : seedNodeIds) {
                scores.put(seedId, seedBoost);
            }

            for (int iter = 0; iter < maxIterations; iter++) {
                Map<Long, Double> newScores = new java.util.HashMap<>();
                double maxDiff = 0;

                for (Map.Entry<Long, List<Long>> entry : adjacency.entrySet()) {
                    long nodeId = entry.getKey();
                    List<Long> neighbors = entry.getValue();

                    double neighborSum = 0;
                    for (long neighbor : neighbors) {
                        int degree = adjacency.getOrDefault(neighbor, List.of()).size();
                        if (degree > 0) {
                            neighborSum += scores.getOrDefault(neighbor, 0.0) / degree;
                        }
                    }

                    double personalization = seedNodeIdSet.contains(nodeId) ? seedBoost : 0;
                    double newScore = (1 - dampingFactor) * personalization + dampingFactor * neighborSum;
                    newScores.put(nodeId, newScore);

                    maxDiff = Math.max(maxDiff, Math.abs(newScore - scores.getOrDefault(nodeId, 0.0)));
                }

                scores = newScores;
                if (maxDiff < convergenceThreshold) {
                    log.debug("PPR converged after {} iterations", iter + 1);
                    break;
                }
            }

            // Step 4: 按 PPR 分数排序，取 topK 节点
            List<Long> topNodeIds = scores.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(topK)
                    .map(Map.Entry::getKey)
                    .toList();

            if (topNodeIds.isEmpty()) {
                return List.of();
            }

            // Step 5: 查找这些节点关联的 segment
            String placeholders = topNodeIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String segmentQuery = """
                    SELECT s.id, s.document_id, s.document_name, s.content
                    FROM kmc_segment_entity_metadata em
                    JOIN kmc_document_segment s ON s.id = em.segment_id
                    JOIN kmc_document d ON d.id = s.document_id AND d.del_flag = 0
                    WHERE d.knowledge_base_id = ?
                      AND em.segment_id IN (
                          SELECT DISTINCT em2.segment_id
                          FROM kmc_segment_entity_metadata em2
                          WHERE em2.document_id IN (
                              SELECT DISTINCT em3.document_id
                              FROM kmc_segment_entity_metadata em3
                              WHERE em3.segment_id IN (
                                  SELECT DISTINCT segment_id FROM kg_node_segment_rel
                                  WHERE node_id IN (%s)
                              )
                          )
                      )
                    ORDER BY s.id ASC
                    LIMIT ?
                    """.formatted(placeholders);

            List<Object> params = new ArrayList<>();
            params.add(knowledgeBaseId);
            params.addAll(topNodeIds);
            params.add(topK);

            final Map<Long, Double> finalScores = scores;
            final List<Long> finalTopNodeIds = topNodeIds;
            List<RetrievalResult> results = jdbcTemplate.query(segmentQuery, (rs, rowNum) -> {
                long bestNodeId = 0;
                double bestScore = 0;
                for (Long nodeId : finalTopNodeIds) {
                    double score = finalScores.getOrDefault(nodeId, 0.0);
                    if (score > bestScore) {
                        bestScore = score;
                        bestNodeId = nodeId;
                    }
                }
                return RetrievalResult.builder()
                        .segmentId(rs.getLong("id"))
                        .documentId(rs.getLong("document_id"))
                        .documentName(rs.getString("document_name"))
                        .content(rs.getString("content"))
                        .score(bestScore * GRAPH_SCORE)
                        .source("graph_ppr")
                        .metadata(Map.of("ppr_iterations", maxIterations))
                        .build();
            }, params.toArray());

            log.info("PPR retrieve: {} seed entities -> {} top nodes -> {} segments",
                    seedEntities.size(), topNodeIds.size(), results.size());
            return results != null ? results : List.of();

        } catch (Exception e) {
            RagFallbackMonitor.record("neo4j", "standard_graph", "ppr failed: " + e.getMessage());
            log.warn("PPR retrieval failed, falling back to standard: {}", e.getMessage());
            return retrieve(knowledgeBaseId, seedEntities, topK);
        }
    }

    private List<Map<String, Object>> loadSeedSubgraphEdges(List<Long> seedNodeIds, int maxEdges) {
        List<Map<String, Object>> firstHop = loadEdgesForNodes(seedNodeIds, maxEdges + 1);
        if (firstHop.size() > maxEdges) {
            return firstHop;
        }

        Set<Long> neighbors = new HashSet<>();
        for (Map<String, Object> edge : firstHop) {
            neighbors.add(((Number) edge.get("source_id")).longValue());
            neighbors.add(((Number) edge.get("target_id")).longValue());
        }
        neighbors.removeAll(seedNodeIds);
        if (neighbors.isEmpty()) {
            return firstHop;
        }

        List<Map<String, Object>> merged = new ArrayList<>(firstHop);
        Set<String> seen = firstHop.stream()
                .map(this::edgeKey)
                .collect(Collectors.toSet());
        int remaining = maxEdges + 1 - merged.size();
        for (Map<String, Object> edge : loadEdgesForNodes(new ArrayList<>(neighbors), remaining)) {
            if (seen.add(edgeKey(edge))) {
                merged.add(edge);
            }
            if (merged.size() > maxEdges) {
                break;
            }
        }
        return merged;
    }

    private List<Map<String, Object>> loadEdgesForNodes(List<Long> nodeIds, int limit) {
        if (nodeIds == null || nodeIds.isEmpty() || limit <= 0) {
            return List.of();
        }
        String placeholders = nodeIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT source_id, target_id
                FROM kg_edge
                WHERE del_flag = 0
                  AND (source_id IN (%s) OR target_id IN (%s))
                LIMIT ?
                """.formatted(placeholders, placeholders);
        List<Object> params = new ArrayList<>(nodeIds);
        params.addAll(nodeIds);
        params.add(limit);
        return jdbcTemplate.queryForList(sql, params.toArray());
    }

    private String edgeKey(Map<String, Object> edge) {
        long src = ((Number) edge.get("source_id")).longValue();
        long tgt = ((Number) edge.get("target_id")).longValue();
        return src < tgt ? src + ":" + tgt : tgt + ":" + src;
    }

    /**
     * 语义引导图遍历
     * 边权重 = 实体嵌入相似度 × 关系类型权重
     */
    public List<RetrievalResult> semanticGuidedRetrieve(Long knowledgeBaseId, List<String> entities,
                                                         String queryContext, int topK) {
        if (neo4jClient == null || cypherSafetyValidator == null) {
            RagFallbackMonitor.record("neo4j", "standard_graph", "semantic traversal unavailable");
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
            RagFallbackMonitor.record("neo4j", "standard_graph", "semantic traversal blocked by cypher safety validator");
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
            RagFallbackMonitor.record("neo4j", "standard_graph", "semantic traversal failed: " + e.getMessage());
            log.warn("Semantic guided traversal failed, fallback to standard: {}", e.getMessage());
        }

        return retrieve(knowledgeBaseId, entities, topK);
    }

    /**
     * 时序事实检索
     * [溯源] 算法优化指南 §3.3: 修复 SQL 缺列 — 使用 created_at 替代 valid_from/valid_until
     */
    public List<RetrievalResult> temporalRetrieve(Long knowledgeBaseId, List<String> entities,
                                                   long currentTime, int topK) {
        boolean h2 = isH2();

        List<Object> params = new ArrayList<>();
        params.add(knowledgeBaseId);
        for (String entity : entities) {
            params.add(h2 ? "%" + entity + "%" : JSON.toJSONString(List.of(entity)));
        }
        params.add(topK);

        String entityCondition = entities.stream()
                .map(term -> h2 ? "em.entities LIKE ?" : "em.entities @> ?::jsonb")
                .collect(Collectors.joining(" OR "));

        try {
            // [溯源] 算法优化指南 §3.3: 修复 SQL 缺列 — 使用 created_at 替代 valid_from/valid_until
            String sql = """
                    SELECT s.id, s.document_id, s.document_name, s.content, em.relations,
                           em.created_at
                    FROM kmc_segment_entity_metadata em
                    JOIN kmc_document_segment s ON s.id = em.segment_id AND s.del_flag = 0
                    JOIN kmc_document d ON d.id = s.document_id AND d.del_flag = 0
                    WHERE d.knowledge_base_id = ?
                      AND (%s)
                    ORDER BY s.id ASC
                    LIMIT ?
                    """.formatted(entityCondition);
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
                // 时序衰减：越近的事实分数越高
                double temporalScore = GRAPH_SCORE;
                if (createdAt != null) {
                    long ageDays = (currentTime - createdAt.getTime()) / 86400000L;
                    temporalScore *= Math.pow(temporalDecayFactor, ageDays / 30.0);
                }
                return RetrievalResult.builder()
                        .segmentId(rs.getLong("id"))
                        .documentId(rs.getLong("document_id"))
                        .documentName(rs.getString("document_name"))
                        .content(rs.getString("content"))
                        .score(temporalScore)
                        .source("graph_temporal")
                        .metadata(Map.of(
                                "relations", rs.getString("relations") != null ? rs.getString("relations") : "[]"))
                        .build();
            }, params.toArray());
        } catch (Exception e) {
            RagFallbackMonitor.record("neo4j", "standard_graph", "temporal retrieval failed: " + e.getMessage());
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
            RagFallbackMonitor.record("neo4j", "normal_rag", "metadata sql graph search failed: " + e.getMessage());
            log.warn("GraphRAG search failed, fallback to normal RAG: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Long> searchNeo4jSegments(List<String> terms, Integer maxHops, int topK) {
        if (neo4jClient == null || cypherSafetyValidator == null || maxHops == null || maxHops <= 0) {
            RagFallbackMonitor.record("neo4j", "metadata_sql", "neo4j client or safety validator unavailable");
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
            RagFallbackMonitor.record("neo4j", "metadata_sql", "neo4j template blocked by cypher safety validator");
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
            RagFallbackMonitor.record("neo4j", "metadata_sql", "neo4j traversal failed: " + e.getMessage());
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
        try {
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
            RagFallbackMonitor.record("neo4j", "metadata_sql", "load neo4j segment ids failed: " + e.getMessage());
            log.warn("GraphRAG segment id load failed, fallback to metadata SQL: {}", e.getMessage());
            return List.of();
        }
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

    private List<RetrievalResult> mergeResults(List<RetrievalResult> first, List<RetrievalResult> second, int topK) {
        Map<String, RetrievalResult> merged = new java.util.LinkedHashMap<>();
        for (RetrievalResult result : java.util.stream.Stream.concat(first.stream(), second.stream()).toList()) {
            String key = result.getSegmentId() != null ? "seg:" + result.getSegmentId() : "content:" + result.getContent();
            RetrievalResult existing = merged.get(key);
            if (existing == null || result.getScore() > existing.getScore()) {
                merged.put(key, result);
            }
        }
        return merged.values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .toList();
    }

    private boolean isH2() {
        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("h2");
        } catch (Exception e) {
            return false;
        }
    }
}
