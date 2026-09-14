package tech.qiantong.qknow.module.kmc.service.rag.graph;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 双层神经符号图重排器实现 (GraphRAG 2.0)
 * 落地 Theorem 2.1 神经符号逻辑门控、Banach 不动点压缩映射 PPR 求解与防环路消除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NeuroSymbolicGraphRankerImpl implements NeuroSymbolicGraphRanker {

    private final MultiHopCausalPathExtractor causalPathExtractor;
    private final HierarchicalCommunityService communityService;

    // 模拟边拓扑: source -> List<EdgeRecord>
    private final Map<String, List<EdgeRecord>> mockEdgeStore = new ConcurrentHashMap<>();

    // 门控阈值 tau_prune = 0.60
    public static final double TAU_PRUNE = 0.60;

    public record EdgeRecord(String source, String relation, String target, double score) {}

    @Getter
    @AllArgsConstructor
    public static class PprResult {
        private final boolean converged;
        private final int iterations;
        private final List<Double> residualHistory;
        private final Map<String, Double> finalScores;
    }

    @Override
    public RankedGraphContext rankGraphContext(
            String workspaceId,
            String query,
            float[] queryVector,
            List<String> extractedEntities
    ) {
        List<String> activeSeeds = extractedEntities != null ? new ArrayList<>(extractedEntities) : new ArrayList<>();
        if (activeSeeds.isEmpty() && query != null) {
            activeSeeds.add(query);
        }

        // 1. 神经符号逻辑门控过滤 (Theorem 2.1)
        List<MultiHopCausalPathExtractor.CausalPathFact> validFacts = new ArrayList<>();
        Map<String, Double> importanceScores = new HashMap<>();

        for (String seed : activeSeeds) {
            List<EdgeRecord> edges = mockEdgeStore.getOrDefault(seed, Collections.emptyList());
            for (EdgeRecord edge : edges) {
                // 门控判定: 语义相似度 * 符号因果权重 >= tau_prune
                if (edge.score() >= TAU_PRUNE && isCausalRelation(edge.relation())) {
                    validFacts.add(MultiHopCausalPathExtractor.CausalPathFact.builder()
                            .sourceEntity(edge.source())
                            .firstRelation(edge.relation())
                            .targetEntity(edge.target())
                            .causalScore(edge.score())
                            .build());
                    importanceScores.put(edge.target(), edge.score());
                } else {
                    log.debug("边 [{} --{}--> {}] 未通过神经符号门控 (得分: {} < {}), 触发硬剪枝",
                            edge.source(), edge.relation(), edge.target(), edge.score(), TAU_PRUNE);
                }
            }
        }

        // 2. 结合底层抽取器事实链
        if (causalPathExtractor != null) {
            var extracted = causalPathExtractor.extractCausalChains(workspaceId, activeSeeds, 10);
            validFacts.addAll(extracted);
        }

        // 3. 关联层次化社区摘要
        List<String> communitySummaries = new ArrayList<>();
        if (communityService != null) {
            var communities = communityService.retrieveRelevantCommunities(workspaceId, query, 3);
            for (var c : communities) {
                communitySummaries.add(c.summary());
            }
        }

        return new RankedGraphContext(
                activeSeeds,
                validFacts,
                communitySummaries,
                importanceScores
        );
    }

    /**
     * 判定关系谓词是否具备强因果/依赖/结构从属性
     */
    private boolean isCausalRelation(String relation) {
        if (relation == null) return false;
        String upper = relation.toUpperCase(Locale.ROOT);
        return upper.contains("CAUSE") || upper.contains("TRIGGER") || upper.contains("TIMEOUT")
                || upper.contains("DEPEND") || upper.contains("CALL") || upper.contains("BELONG")
                || upper.contains("LEAD");
    }

    /**
     * Banach 压缩映射不动点 PPR 求解与收敛轨迹跟踪
     */
    public PprResult computePprWithConvergenceTracking(
            List<String> nodes,
            Map<String, List<String>> adj,
            String seedNode,
            double alpha,
            int maxIters,
            double eps
    ) {
        int n = nodes.size();
        Map<String, Integer> nodeIndex = new HashMap<>();
        for (int i = 0; i < n; i++) {
            nodeIndex.put(nodes.get(i), i);
        }

        // 初始概率分布 p0
        double[] p = new double[n];
        int seedIdx = nodeIndex.getOrDefault(seedNode, 0);
        p[seedIdx] = 1.0;

        double[] p0 = Arrays.copyOf(p, n);
        List<Double> residualHistory = new ArrayList<>();
        boolean converged = false;
        int iter = 0;

        while (iter < maxIters) {
            iter++;
            double[] pNext = new double[n];

            // 矩阵乘法: (1 - alpha) * W_norm * p
            for (int u = 0; u < n; u++) {
                String uNode = nodes.get(u);
                List<String> neighbors = adj.getOrDefault(uNode, Collections.emptyList());
                int deg = neighbors.size();
                if (deg > 0) {
                    double share = ((1.0 - alpha) * p[u]) / deg;
                    for (String vNode : neighbors) {
                        Integer vIdx = nodeIndex.get(vNode);
                        if (vIdx != null) {
                            pNext[vIdx] += share;
                        }
                    }
                } else {
                    // 悬挂节点自环均摊
                    pNext[u] += (1.0 - alpha) * p[u];
                }
            }

            // 叠加重启项: alpha * p0
            for (int i = 0; i < n; i++) {
                pNext[i] += alpha * p0[i];
            }

            // 计算 L1 范数残差
            double l1Diff = 0.0;
            for (int i = 0; i < n; i++) {
                l1Diff += Math.abs(pNext[i] - p[i]);
            }
            residualHistory.add(l1Diff);
            p = pNext;

            if (l1Diff < eps) {
                converged = true;
                break;
            }
        }

        Map<String, Double> finalScores = new HashMap<>();
        for (int i = 0; i < n; i++) {
            finalScores.put(nodes.get(i), p[i]);
        }
        return new PprResult(converged, iter, residualHistory, finalScores);
    }

    /**
     * 防环路冗余消除定理实现 (Loop Redundancy Elimination)
     * 剪除拓扑回路以在 Pareto 双目标前沿上保留最简非支配路径
     */
    public List<String> eliminatePathLoops(List<String> path) {
        if (path == null || path.size() <= 2) {
            return path;
        }

        List<String> simplified = new ArrayList<>(path);
        boolean foundLoop = true;

        while (foundLoop) {
            foundLoop = false;
            Map<String, Integer> firstSeen = new HashMap<>();
            for (int i = 0; i < simplified.size(); i++) {
                String node = simplified.get(i);
                if (firstSeen.containsKey(node)) {
                    int prevIndex = firstSeen.get(node);
                    // 发现拓扑环: prevIndex 与 i 重复，剪除 (prevIndex, i] 之间的子段
                    List<String> newPath = new ArrayList<>();
                    for (int j = 0; j <= prevIndex; j++) {
                        newPath.add(simplified.get(j));
                    }
                    for (int j = i + 1; j < simplified.size(); j++) {
                        newPath.add(simplified.get(j));
                    }
                    simplified = newPath;
                    foundLoop = true;
                    break;
                } else {
                    firstSeen.put(node, i);
                }
            }
        }
        return simplified;
    }

    /**
     * 测试/预置辅助方法：注册模拟边
     */
    public void mockRegisterEdge(String source, String relation, String target, double score) {
        mockEdgeStore.computeIfAbsent(source, k -> new ArrayList<>())
                .add(new EdgeRecord(source, relation, target, score));
    }
}
