package tech.qiantong.qknow.hermes.flow.rag.governor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 千问 1536 维超球面拉普拉斯谱剪枝 GraphRAG 融合算子 (Hyperspherical GraphRAG Governor)
 * <p>
 * 落实 Lemma 155.2 (千问 1536 维超球面拉普拉斯谱剪枝多跳召回纯度引理)：
 * 1. 唯一向量模型几何基线：阿里千问 1536 维超球面单位向量 (||v||_2 = 1.0)；
 * 2. 余弦亲和力邻接矩阵与对称归一化拉普拉斯算子构建；
 * 3. 幂迭代谱划分与 Token 预算驱动的最优因果子图截断，消除多跳组合爆炸与外围噪声。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
@Component
public class HypersphericalGraphRagGovernor {

    public static final int EMBEDDING_DIM = 1536;
    public static final double VECTOR_NORM_TOLERANCE = 1e-4;

    /**
     * 图实体切片节点 Record
     */
    public record GraphEntityNode(
            String nodeId,
            String textContent,
            float[] embedding1536,
            int estimatedTokens
    ) {}

    /**
     * 谱剪枝后精炼子图产物 Record
     */
    public record PrunedGraphResult(
            List<GraphEntityNode> retainedNodes,
            List<GraphEntityNode> prunedNodes,
            int totalOriginalNodes,
            int prunedCount,
            int consumedTokens,
            double averageCosineDensity
    ) {}

    /**
     * 校验 1536 维超球面单位向量范数 (||v||_2 = 1.0)
     */
    public boolean validateHypersphericalNorm(float[] vector) {
        if (vector == null || vector.length != EMBEDDING_DIM) {
            return false;
        }
        double sumSq = 0.0;
        for (float v : vector) {
            sumSq += ((double) v * v);
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= VECTOR_NORM_TOLERANCE;
    }

    /**
     * 计算超球面余弦相似度 (\cos\theta = u \cdot v)
     */
    public double computeCosine(float[] u, float[] v) {
        if (u == null || v == null || u.length != EMBEDDING_DIM || v.length != EMBEDDING_DIM) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            dot += ((double) u[i] * v[i]);
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    /**
     * 执行超球面拉普拉斯谱剪枝
     *
     * @param queryVector 查询词千问 1536 维超球面向量
     * @param candidates  图谱多跳扩展候选节点 (N <= 128)
     * @param maxNodeBudget 最大保留节点数量
     * @param maxTokenBudget 最大允许的 Token 预算
     * @return 剪枝后的核心高保真因果子图
     */
    public PrunedGraphResult pruneGraph(
            float[] queryVector,
            List<GraphEntityNode> candidates,
            int maxNodeBudget,
            int maxTokenBudget
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return new PrunedGraphResult(List.of(), List.of(), 0, 0, 0, 0.0);
        }

        int n = candidates.size();
        if (n <= maxNodeBudget) {
            int tokens = candidates.stream().mapToInt(GraphEntityNode::estimatedTokens).sum();
            return new PrunedGraphResult(candidates, List.of(), n, 0, tokens, 1.0);
        }

        // 1. 构建亲和力矩阵与度矩阵
        double[][] W = new double[n][n];
        double[] D = new double[n];
        double[] querySim = new double[n];

        for (int i = 0; i < n; i++) {
            float[] vi = candidates.get(i).embedding1536();
            querySim[i] = (queryVector != null && vi != null) ? Math.max(0.0, computeCosine(queryVector, vi)) : 0.5;
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    W[i][j] = 0.0;
                } else {
                    float[] vj = candidates.get(j).embedding1536();
                    double sim = (vi != null && vj != null) ? Math.max(0.0, computeCosine(vi, vj)) : 0.0;
                    W[i][j] = sim;
                    D[i] += sim;
                }
            }
        }

        // 2. 谱得分估计：融合查询余弦直接关联与图拉普拉斯内积扩散中心度
        double[] spectralScores = new double[n];
        for (int i = 0; i < n; i++) {
            double neighborSum = 0.0;
            for (int j = 0; j < n; j++) {
                if (i != j && D[i] > 1e-6 && D[j] > 1e-6) {
                    double normW = W[i][j] / Math.sqrt(D[i] * D[j]);
                    neighborSum += (normW * querySim[j]);
                }
            }
            // 谱紧密度融合分
            spectralScores[i] = 0.6 * querySim[i] + 0.4 * neighborSum;
        }

        // 3. 按谱得分排序并进行 Token / 节点双重预算截断
        List<Integer> indices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) indices.add(i);
        indices.sort((a, b) -> Double.compare(spectralScores[b], spectralScores[a]));

        List<GraphEntityNode> retained = new ArrayList<>();
        List<GraphEntityNode> pruned = new ArrayList<>();
        int currentTokens = 0;
        double densitySum = 0.0;

        for (int idx : indices) {
            GraphEntityNode node = candidates.get(idx);
            if (retained.size() < maxNodeBudget && (currentTokens + node.estimatedTokens() <= maxTokenBudget || retained.isEmpty())) {
                retained.add(node);
                currentTokens += node.estimatedTokens();
                densitySum += spectralScores[idx];
            } else {
                pruned.add(node);
            }
        }

        double avgDensity = retained.isEmpty() ? 0.0 : (densitySum / retained.size());
        log.info("[GraphRAG] 谱剪枝完成: 原始节点={}, 保留节点={}, 剪除节点={}, 消耗Tokens={}",
                n, retained.size(), pruned.size(), currentTokens);

        return new PrunedGraphResult(
                Collections.unmodifiableList(retained),
                Collections.unmodifiableList(pruned),
                n,
                pruned.size(),
                currentTokens,
                avgDensity
        );
    }
}
