package tech.qiantong.qknow.hermes.search.graphrag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * GraphRAG 分层社区认知聚合引擎 (Hierarchical GraphRAG Engine)
 * 对标 Microsoft Research GraphRAG (2024) 架构标准：
 * 1. Leiden 层次化社区树建模 (宏观 Level 2, 中观 Level 1, 微观 Level 0)
 * 2. 宏观全局全景总结 (Global Summarization) 与微观精准事实下钻 (Local Factual) 双层认知覆盖
 * 3. 挂载阿里千问 1536 维超球面单位向量进行超球面几何路由
 */
public class HierarchicalGraphRagEngine {

    private static final Logger log = LoggerFactory.getLogger(HierarchicalGraphRagEngine.class);
    private static final Pattern GLOBAL_INTENT_PATTERN = Pattern.compile(".*(总结|综述|脉络|全景|概括|架构演进|发展历史|演进趋势|整体方案).*");

    // 全量社区索引: communityId -> HierarchicalCommunityNode
    private final Map<String, HierarchicalCommunityNode> communityIndex = new ConcurrentHashMap<>();
    // 按层级分层索引: level -> List<HierarchicalCommunityNode>
    private final Map<Integer, List<HierarchicalCommunityNode>> levelIndex = new ConcurrentHashMap<>();

    public synchronized void registerCommunity(HierarchicalCommunityNode node) {
        Objects.requireNonNull(node, "node 不能为空");
        communityIndex.put(node.communityId(), node);
        levelIndex.computeIfAbsent(node.level(), k -> new ArrayList<>()).add(node);
    }

    public synchronized void registerCommunities(List<HierarchicalCommunityNode> nodes) {
        Objects.requireNonNull(nodes, "nodes 不能为空");
        for (HierarchicalCommunityNode node : nodes) {
            registerCommunity(node);
        }
    }

    /**
     * 智能推断查询意图：区分全局全景综述 (GLOBAL_SUMMARIZATION) 与微观精准事实 (LOCAL_FACTUAL)
     */
    public String classifyIntent(String query) {
        if (query == null || query.isBlank()) {
            return "LOCAL_FACTUAL";
        }
        if (GLOBAL_INTENT_PATTERN.matcher(query).matches()) {
            return "GLOBAL_SUMMARIZATION";
        }
        return "LOCAL_FACTUAL";
    }

    /**
     * 执行 GraphRAG 社区折叠树遍历与语义路由
     */
    public GraphRagCommunityReceipt retrieveCommunityKnowledge(String query, float[] queryEmbedding) {
        Objects.requireNonNull(query, "query 不能为空");
        Objects.requireNonNull(queryEmbedding, "queryEmbedding 不能为空");
        long startNanos = System.nanoTime();

        String intent = classifyIntent(query);
        int targetLevel = intent.equals("GLOBAL_SUMMARIZATION") ? 2 : 0;

        List<HierarchicalCommunityNode> candidateNodes = levelIndex.getOrDefault(targetLevel, Collections.emptyList());
        if (candidateNodes.isEmpty()) {
            // 若指定层级不存在，兜底退化至所有社区节点
            candidateNodes = new ArrayList<>(communityIndex.values());
        }

        if (candidateNodes.isEmpty()) {
            long duration = System.nanoTime() - startNanos;
            return GraphRagCommunityReceipt.create(
                    query, intent, "none", -1, "无可用社区知识图谱", Collections.emptyList(), 0.0f, duration
            );
        }

        // 依据千问 1536 维超球面单位向量余弦相似度，寻找最佳匹配社区
        HierarchicalCommunityNode bestNode = null;
        float maxSimilarity = Float.NEGATIVE_INFINITY;

        for (HierarchicalCommunityNode node : candidateNodes) {
            float sim = node.calculateCosine(queryEmbedding);
            if (sim > maxSimilarity) {
                maxSimilarity = sim;
                bestNode = node;
            }
        }

        if (bestNode == null) {
            bestNode = candidateNodes.get(0);
            maxSimilarity = 0.0f;
        }

        long duration = System.nanoTime() - startNanos;
        return GraphRagCommunityReceipt.create(
                query,
                intent,
                bestNode.communityId(),
                bestNode.level(),
                bestNode.summaryReport(),
                bestNode.entityIds(),
                maxSimilarity,
                duration
        );
    }

    public int getTotalCommunityCount() {
        return communityIndex.size();
    }

    public HierarchicalCommunityNode getCommunity(String communityId) {
        return communityIndex.get(communityId);
    }
}
