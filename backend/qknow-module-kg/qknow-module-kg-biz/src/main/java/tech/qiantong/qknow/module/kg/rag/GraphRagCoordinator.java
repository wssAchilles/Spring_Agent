package tech.qiantong.qknow.module.kg.rag;

import java.util.*;

/**
 * 神经符号知识图谱 3.0 统一检索门面 (GraphRAG 3.0)
 */
public interface GraphRagCoordinator {

    record GraphCausalEvidence(
            String sourceEntity,
            String relationType,
            String targetEntity,
            String pathDescription,
            double pprScore
    ) {}

    record CommunitySummaryEvidence(
            String communityId,
            String summaryText,
            int hierarchicalLevel,
            double relevanceScore
    ) {}

    /**
     * 执行基于 Banach 不动点 PPR 语义扩散的图因果多跳路径检索
     */
    List<GraphCausalEvidence> retrieveCausalPaths(Long workspaceId, List<String> seedEntities, int topK);

    /**
     * 执行 Leiden 多尺度分层社区宏观全局摘要检索
     */
    List<CommunitySummaryEvidence> retrieveCommunitySummaries(Long workspaceId, String query, int topK);

    /**
     * 异步切片入图构建回调
     */
    default void onSlicesIngestedAsync(Long workspaceId, Long docId, List<String> sliceIds, List<String> texts) {
        // 默认空实现，由具体图谱构建组件重写
    }

    /**
     * 定理 1.4: Banach 不动点个性化 PageRank (PPR) 收敛计算引擎
     * 转移方程: p^{(k+1)} = (1 - alpha) * s + alpha * p^{(k)} * M
     */
    static Map<String, Double> computePpr(
            Map<String, List<String>> adjacency,
            Map<String, Double> teleportDistribution,
            double dampingFactor,
            double tolerance,
            int maxIterations
    ) {
        if (adjacency == null || adjacency.isEmpty() || teleportDistribution == null || teleportDistribution.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<String> allNodes = new LinkedHashSet<>(adjacency.keySet());
        adjacency.values().forEach(allNodes::addAll);
        teleportDistribution.keySet().forEach(allNodes::add);

        int n = allNodes.size();
        if (n == 0) return Collections.emptyMap();

        // 归一化 teleport 向量
        Map<String, Double> s = new HashMap<>();
        double teleportSum = teleportDistribution.values().stream().mapToDouble(Double::doubleValue).sum();
        for (String node : allNodes) {
            s.put(node, teleportDistribution.getOrDefault(node, 0.0) / (teleportSum > 0 ? teleportSum : 1.0));
        }

        // 初始化分布向量 p^{(0)} = s
        Map<String, Double> p = new HashMap<>(s);

        for (int iter = 0; iter < maxIterations; iter++) {
            Map<String, Double> nextP = new HashMap<>();
            // 基础跳转分量: (1 - alpha) * s
            for (String node : allNodes) {
                nextP.put(node, (1.0 - dampingFactor) * s.getOrDefault(node, 0.0));
            }

            // 出度扩散分量
            double danglingSum = 0.0;
            for (String u : allNodes) {
                List<String> neighbors = adjacency.get(u);
                if (neighbors == null || neighbors.isEmpty()) {
                    danglingSum += p.getOrDefault(u, 0.0);
                } else {
                    double spread = (dampingFactor * p.getOrDefault(u, 0.0)) / neighbors.size();
                    for (String v : neighbors) {
                        nextP.put(v, nextP.getOrDefault(v, 0.0) + spread);
                    }
                }
            }

            // 悬挂节点补充分配
            if (danglingSum > 0) {
                double danglingShare = dampingFactor * danglingSum / n;
                for (String node : allNodes) {
                    nextP.put(node, nextP.getOrDefault(node, 0.0) + danglingShare);
                }
            }

            // 计算 L1 范数残差 ||nextP - p||_1
            double diff = 0.0;
            for (String node : allNodes) {
                diff += Math.abs(nextP.getOrDefault(node, 0.0) - p.getOrDefault(node, 0.0));
            }

            p = nextP;
            if (diff < tolerance) {
                break; // Banach 不动点收敛
            }
        }

        // 终末严格归一化保证
        double sum = p.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum > 0) {
            for (Map.Entry<String, Double> entry : p.entrySet()) {
                entry.setValue(entry.getValue() / sum);
            }
        }

        return p;
    }
}
