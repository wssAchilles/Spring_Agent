package tech.qiantong.qknow.ai.dataagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.ai.dataagent.model.SchemaCard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态模式索引与智能剪枝服务
 * 遵从定理 1.1（模式覆盖完备性引理）与外键传递闭包恢复算法
 */
@Service
public class SchemaCatalogService {

    private static final Logger log = LoggerFactory.getLogger(SchemaCatalogService.class);
    public static final int MAX_SCHEMA_PROMPT_BYTES = 4096;

    /** 本地多租户/数据源模式卡片缓存: datasourceId -> (tableName -> SchemaCard) */
    private final Map<String, Map<String, SchemaCard>> catalogMap = new ConcurrentHashMap<>();

    /**
     * 注册或更新数据源表元数据卡片
     */
    public void registerSchemaCard(String datasourceId, SchemaCard card) {
        if (datasourceId == null || card == null || card.getTableName() == null) return;
        catalogMap.computeIfAbsent(datasourceId, k -> new ConcurrentHashMap<>())
                .put(card.getTableName().toLowerCase(), card);
    }

    /**
     * 批量导入数据源模式
     */
    public void registerBatch(String datasourceId, Collection<SchemaCard> cards) {
        if (cards == null) return;
        for (SchemaCard card : cards) {
            registerSchemaCard(datasourceId, card);
        }
    }

    /**
     * 获取指定数据源全量表卡片
     */
    public Map<String, SchemaCard> getSchemaCards(String datasourceId) {
        return catalogMap.getOrDefault(datasourceId, Collections.emptyMap());
    }

    /**
     * 基于用户提问与嵌入向量，执行动态模式检索与外键传递闭包剪枝
     *
     * @param datasourceId 数据源标识
     * @param queryText    用户自然语言查询文本
     * @param queryVector  用户自然语言提问的 1536 维超球面嵌入 (若为 null 则降级为关键词与拓扑检索)
     * @param topK         核心候选表数量上限 (默认 3~5)
     * @return 剪枝后的紧凑 DDL / Schema 描述字符串，体积严格 <= 4096 字节
     */
    public String retrievePrunedSchema(String datasourceId, String queryText, float[] queryVector, int topK) {
        Map<String, SchemaCard> allCards = getSchemaCards(datasourceId);
        if (allCards.isEmpty()) {
            return "-- No schema available for datasource: " + datasourceId;
        }

        // 阶段 1: 表级相似度粗筛
        List<Map.Entry<String, Double>> scoredTables = new ArrayList<>();
        String normalizedQuery = queryText != null ? queryText.toLowerCase() : "";

        for (Map.Entry<String, SchemaCard> entry : allCards.entrySet()) {
            SchemaCard card = entry.getValue();
            double score = 0.0;

            if (queryVector != null && card.getEmbedding() != null) {
                score = computeCosineSimilarity(queryVector, card.getEmbedding());
            } else {
                // 降级: 基于名称与注释的关键词重合度打分
                score = computeLexicalOverlapScore(normalizedQuery, card);
            }
            scoredTables.add(new AbstractMap.SimpleEntry<>(entry.getKey(), score));
        }

        scoredTables.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        Set<String> coreTableNames = new LinkedHashSet<>();
        int k = Math.min(topK <= 0 ? 3 : topK, scoredTables.size());
        for (int i = 0; i < k; i++) {
            coreTableNames.add(scoredTables.get(i).getKey());
        }

        // 阶段 2: 外键拓扑因果闭包补全 (Steiner Minimal Tree Closure)
        Set<String> enrichedTableNames = expandForeignKeyTransitiveClosure(allCards, coreTableNames);

        // 阶段 3: 构造紧凑 Schema 文本并严格限制在 4KB 预算内
        StringBuilder sb = new StringBuilder();
        sb.append("-- DATABASE SCHEMA (PRUNED TO RELEVANT SUBSET):\n");

        for (String tbl : enrichedTableNames) {
            SchemaCard card = allCards.get(tbl);
            if (card != null) {
                String desc = card.toCompactDescription();
                if (sb.length() + desc.length() > MAX_SCHEMA_PROMPT_BYTES - 100) {
                    log.warn("[SchemaCatalogService] 触碰 4KB 上下文预算上限，停止追加后续表: {}", tbl);
                    break;
                }
                sb.append(desc).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 外键拓扑闭包恢复算法
     * 若已选表中包含 A 和 B，但在全库拓扑中 A 与 B 需通过中间表 M 桥接，则将 M 自动纳入子集
     */
    private Set<String> expandForeignKeyTransitiveClosure(Map<String, SchemaCard> allCards, Set<String> coreTables) {
        Set<String> result = new LinkedHashSet<>(coreTables);

        // 构建外键邻接表: table -> set of foreign connected tables
        Map<String, Set<String>> adj = new HashMap<>();
        for (Map.Entry<String, SchemaCard> entry : allCards.entrySet()) {
            String fromTable = entry.getKey();
            SchemaCard card = entry.getValue();
            for (String target : card.getForeignKeys().values()) {
                String toTable = target.split("\\.")[0].toLowerCase();
                adj.computeIfAbsent(fromTable, x -> new HashSet<>()).add(toTable);
                adj.computeIfAbsent(toTable, x -> new HashSet<>()).add(fromTable);
            }
        }

        // 对核心表中任意两两节点寻找最短外键路径 (BFS)
        List<String> coreList = new ArrayList<>(coreTables);
        for (int i = 0; i < coreList.size(); i++) {
            for (int j = i + 1; j < coreList.size(); j++) {
                String src = coreList.get(i);
                String dst = coreList.get(j);
                List<String> bridgePath = findShortestPath(adj, src, dst);
                if (bridgePath != null && bridgePath.size() <= 4) { // 限制最多 2 跳桥接
                    result.addAll(bridgePath);
                }
            }
        }

        return result;
    }

    private List<String> findShortestPath(Map<String, Set<String>> adj, String start, String end) {
        if (!adj.containsKey(start) || !adj.containsKey(end)) return null;
        Queue<List<String>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.add(Collections.singletonList(start));
        visited.add(start);

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String current = path.get(path.size() - 1);

            if (current.equals(end)) {
                return path;
            }

            Set<String> neighbors = adj.getOrDefault(current, Collections.emptySet());
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.add(newPath);
                }
            }
        }
        return null;
    }

    private double computeCosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) return 0.0;
        double dot = 0.0;
        double n1 = 0.0;
        double n2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            n1 += v1[i] * v1[i];
            n2 += v2[i] * v2[i];
        }
        if (n1 == 0.0 || n2 == 0.0) return 0.0;
        return dot / (Math.sqrt(n1) * Math.sqrt(n2));
    }

    private double computeLexicalOverlapScore(String query, SchemaCard card) {
        double score = 0.0;
        if (card.getTableName() != null && query.contains(card.getTableName().toLowerCase())) {
            score += 2.0;
        }
        if (card.getTableComment() != null && query.contains(card.getTableComment().toLowerCase())) {
            score += 3.0;
        }
        for (String col : card.getColumns().keySet()) {
            if (query.contains(col.toLowerCase())) {
                score += 1.0;
            }
        }
        return score;
    }
}
