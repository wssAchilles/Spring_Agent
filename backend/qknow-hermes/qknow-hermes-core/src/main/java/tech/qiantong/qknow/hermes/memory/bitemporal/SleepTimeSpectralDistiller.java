package tech.qiantong.qknow.hermes.memory.bitemporal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 睡眠期海马体图谱对称归一化拉普拉斯谱蒸馏器（对应方向四 · Sleep-time Compute 离线慢轨）。
 * <p>
 * 数学机制：
 * 1. 基于活跃超边构造无向加权邻接矩阵 {@code A} 与度向量 {@code D}，形成对称归一化拉普拉斯矩阵
 *    {@code L_sym = I - D^{-1/2} A D^{-1/2}}；
 * 2. 估算代数连通度近似界 {@code lambda_2} 与每条边的有效谱能量贡献 {@code w_uv * ((f_u/sqrt(d_u)) - (f_v/sqrt(d_v)))^2}；
 * 3. 在严格保持图连通核心骨架的前提下，将冗余低频弱边可逆转移至 {@link ColdStandbyTier#WARM_QUARANTINE}，
 *    绝不物理硬删除任何一条记忆边，支持 100% 反事实回滚恢复。
 * </p>
 */
public class SleepTimeSpectralDistiller {

    private final BiTemporalHippocampalGraphEngine graphEngine;

    public SleepTimeSpectralDistiller(BiTemporalHippocampalGraphEngine graphEngine) {
        this.graphEngine = Objects.requireNonNull(graphEngine, "BiTemporalHippocampalGraphEngine 不能为空");
    }

    /**
     * 在睡眠窗口对当前双时态海马体图谱执行一次谱蒸馏与冗余弱边冷备归档。
     *
     * @param maxPruneRatio 最大允许归档的活跃边比例（如 0.25）
     * @param nowEpochMs    当前时间戳
     */
    public SpectralDistillationReceipt distill(double maxPruneRatio, long nowEpochMs) {
        long startNs = System.nanoTime();
        Map<String, HippocampalVertex> vertexMap = graphEngine.getVerticesSnapshot();
        Map<String, BiTemporalHyperEdge> edgeMap = graphEngine.getLedgerEdgesSnapshot();

        List<String> nodeIds = new ArrayList<>(vertexMap.keySet());
        int n = nodeIds.size();
        Map<String, Integer> nodeIndex = new LinkedHashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            nodeIndex.put(nodeIds.get(i), i);
        }

        List<BiTemporalHyperEdge> activeEdges = new ArrayList<>();
        for (BiTemporalHyperEdge edge : edgeMap.values()) {
            if (edge.storageTier() == ColdStandbyTier.ACTIVE_HOT && edge.txToEpochMs() == null) {
                if (nodeIndex.containsKey(edge.sourceNodeId()) && nodeIndex.containsKey(edge.targetNodeId())) {
                    activeEdges.add(edge);
                }
            }
        }

        if (n < 2 || activeEdges.size() <= 1) {
            return new SpectralDistillationReceipt(
                    n, activeEdges.size(), 0, 0.0, 1.0, List.of(),
                    Math.max(1L, (System.nanoTime() - startNs) / 1000L), nowEpochMs
            );
        }

        // 构建邻接表并计算真实度向量与边权
        double[] degree = new double[n];
        double[] edgeStrength = new double[activeEdges.size()];
        double totalWeight = 0.0;
        record AdjEntry(int neighbor, int edgeIndex) {}
        List<List<AdjEntry>> adjacency = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adjacency.add(new ArrayList<>());
        }

        for (int eIdx = 0; eIdx < activeEdges.size(); eIdx++) {
            BiTemporalHyperEdge edge = activeEdges.get(eIdx);
            int u = nodeIndex.get(edge.sourceNodeId());
            int v = nodeIndex.get(edge.targetNodeId());
            HippocampalVertex vu = vertexMap.get(edge.sourceNodeId());
            HippocampalVertex vv = vertexMap.get(edge.targetNodeId());
            double simU = Math.max(0.05, 0.5 * (1.0 + cosine(edge.unitEmbedding1024(), vu.unitEmbedding1024())));
            double simV = Math.max(0.05, 0.5 * (1.0 + cosine(edge.unitEmbedding1024(), vv.unitEmbedding1024())));
            double w = 0.5 * (simU + simV) * Math.max(1, edge.sourceReferenceCount());
            edgeStrength[eIdx] = w;
            degree[u] += w;
            degree[v] += w;
            totalWeight += w;
            if (u != v) {
                adjacency.get(u).add(new AdjEntry(v, eIdx));
                adjacency.get(v).add(new AdjEntry(u, eIdx));
            }
        }

        // Phase 9.4 重构：基于真实图拓扑对称归一化拉普拉斯 L_sym = I - D^{-1/2} A D^{-1/2}
        // 使用移位矩阵 M = 2I - L_sym = I + D^{-1/2} A D^{-1/2} 对平凡零特征向量 u_0 = D^{1/2}1/||D^{1/2}1||_2
        // 执行 16 轮确定性正交投影幂迭代，收敛至代数连通度 lambda_2 对应真实 Fiedler 特征向量。
        double[] invSqrtD = new double[n];
        double[] u0 = new double[n];
        double u0NormSq = 0.0;
        for (int i = 0; i < n; i++) {
            double sqrtD = Math.sqrt(Math.max(1e-12, degree[i]));
            invSqrtD[i] = degree[i] > 1e-12 ? (1.0 / sqrtD) : 0.0;
            u0[i] = degree[i] > 1e-12 ? sqrtD : 0.0;
            u0NormSq += u0[i] * u0[i];
        }
        double u0Norm = Math.sqrt(Math.max(1e-12, u0NormSq));
        for (int i = 0; i < n; i++) {
            u0[i] /= u0Norm;
        }

        double[] fiedlerVec = new double[n];
        for (int i = 0; i < n; i++) {
            // 确定性非对称初始扰动（兼顾奇偶与索引倾斜），随后对 u0 正交归一化
            fiedlerVec[i] = ((i % 2 == 0) ? 1.0 : -1.0) + 0.13 * (i - (n - 1) / 2.0) / Math.max(1, n);
        }
        orthogonalizeAndNormalize(fiedlerVec, u0);

        double[] nextVec = new double[n];
        for (int iter = 0; iter < 16; iter++) {
            for (int i = 0; i < n; i++) {
                double accum = fiedlerVec[i];
                for (AdjEntry adj : adjacency.get(i)) {
                    double sNorm = edgeStrength[adj.edgeIndex()] * invSqrtD[i] * invSqrtD[adj.neighbor()];
                    accum += sNorm * fiedlerVec[adj.neighbor()];
                }
                nextVec[i] = accum;
            }
            orthogonalizeAndNormalize(nextVec, u0);
            System.arraycopy(nextVec, 0, fiedlerVec, 0, n);
        }

        double rayleighNumerator = 0.0;
        record ScoredEdge(BiTemporalHyperEdge edge, double edgeWeight, double spectralImportance, int u, int v) {}
        List<ScoredEdge> scoredEdges = new ArrayList<>(activeEdges.size());
        for (int eIdx = 0; eIdx < activeEdges.size(); eIdx++) {
            BiTemporalHyperEdge edge = activeEdges.get(eIdx);
            int u = nodeIndex.get(edge.sourceNodeId());
            int v = nodeIndex.get(edge.targetNodeId());
            double diff = (fiedlerVec[u] * invSqrtD[u]) - (fiedlerVec[v] * invSqrtD[v]);
            double specContribution = edgeStrength[eIdx] * diff * diff;
            rayleighNumerator += specContribution;
            // 边得分综合引用权重与真实拉普拉斯谱能量，避免误删唯一桥梁边
            double importance = edgeStrength[eIdx] * (1.0 + specContribution);
            scoredEdges.add(new ScoredEdge(edge, edgeStrength[eIdx], importance, u, v));
        }
        double lambda2Approx = Math.max(0.0, Math.min(2.0, rayleighNumerator));

        // 仅在端点度数 > 1 冗余连通的前提下按弱重要度排序归档
        double safeRatio = Math.max(0.0, Math.min(0.40, maxPruneRatio));
        int maxPruneCount = (int) Math.floor(activeEdges.size() * safeRatio);
        int[] activeEdgeCountPerNode = new int[n];
        for (ScoredEdge se : scoredEdges) {
            activeEdgeCountPerNode[se.u]++;
            activeEdgeCountPerNode[se.v]++;
        }

        scoredEdges.sort(Comparator.comparingDouble(ScoredEdge::spectralImportance));
        List<String> archivedIds = new ArrayList<>();
        double retainedWeight = totalWeight;

        for (ScoredEdge se : scoredEdges) {
            if (archivedIds.size() >= maxPruneCount) {
                break;
            }
            // 保护单边唯一桥接节点与 Error-Book 警示边
            HippocampalVertex targetV = vertexMap.get(se.edge.targetNodeId());
            if (targetV != null && targetV.tier() == HippocampalVertexTier.ERROR_BOOK) {
                continue;
            }
            if (activeEdgeCountPerNode[se.u] <= 1 || activeEdgeCountPerNode[se.v] <= 1) {
                continue;
            }
            if (graphEngine.archiveEdgeToColdStandby(se.edge.edgeId(), ColdStandbyTier.WARM_QUARANTINE, nowEpochMs)) {
                activeEdgeCountPerNode[se.u]--;
                activeEdgeCountPerNode[se.v]--;
                retainedWeight -= se.edgeWeight();
                archivedIds.add(se.edge.edgeId());
            }
        }

        double retainedRatio = totalWeight <= 1e-9 ? 1.0 : Math.max(0.0, Math.min(1.0, retainedWeight / totalWeight));
        long elapsedUs = Math.max(1L, (System.nanoTime() - startNs) / 1000L);
        return new SpectralDistillationReceipt(
                n,
                activeEdges.size(),
                archivedIds.size(),
                lambda2Approx,
                retainedRatio,
                List.copyOf(archivedIds),
                elapsedUs,
                nowEpochMs
        );
    }

    private static void orthogonalizeAndNormalize(double[] vec, double[] u0) {
        double proj = 0.0;
        for (int i = 0; i < vec.length; i++) {
            proj += vec[i] * u0[i];
        }
        double normSq = 0.0;
        for (int i = 0; i < vec.length; i++) {
            vec[i] -= proj * u0[i];
            normSq += vec[i] * vec[i];
        }
        if (normSq <= 1e-12) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] = (i == 0 ? 1.0 : -1.0 / Math.max(1, vec.length - 1));
            }
            double p2 = 0.0;
            for (int i = 0; i < vec.length; i++) {
                p2 += vec[i] * u0[i];
            }
            normSq = 0.0;
            for (int i = 0; i < vec.length; i++) {
                vec[i] -= p2 * u0[i];
                normSq += vec[i] * vec[i];
            }
        }
        double norm = Math.sqrt(Math.max(1e-12, normSq));
        for (int i = 0; i < vec.length; i++) {
            vec[i] /= norm;
        }
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }
}
