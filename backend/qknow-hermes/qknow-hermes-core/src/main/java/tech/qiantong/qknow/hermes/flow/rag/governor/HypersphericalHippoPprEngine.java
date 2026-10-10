package tech.qiantong.qknow.hermes.flow.rag.governor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 千问 1536 维超球面个性化 PageRank (Hippo-PPR) 拓扑实体扩散引擎
 * 模拟人脑海马体记忆整合机制，基于超球面余弦亲和力转移概率执行带阻尼的多跳拓扑扩散，
 * 解决复杂多跳关系检索中传统平面向量检索的语义断层问题。
 */
public class HypersphericalHippoPprEngine {

    private static final Logger log = LoggerFactory.getLogger(HypersphericalHippoPprEngine.class);

    public static final int EXPECTED_DIMENSION = 1536;
    public static final double NORM_TOLERANCE = 1e-3;

    public record GraphEntity(
            String entityId,
            String entityName,
            String entityType,
            float[] embedding1536,
            int estimatedTokens
    ) {
        public GraphEntity {
            if (embedding1536 != null) {
                if (embedding1536.length != EXPECTED_DIMENSION) {
                    throw new IllegalArgumentException("向量维度必须精确为 1536 维: actual=" + embedding1536.length);
                }
                double norm = computeNorm(embedding1536);
                if (Math.abs(norm - 1.0) > NORM_TOLERANCE) {
                    throw new IllegalArgumentException("向量必须严格满足超球面单位范数 ||v||_2 = 1.0: norm=" + norm);
                }
            }
        }

        private static double computeNorm(float[] vec) {
            double sum = 0.0;
            for (float f : vec) {
                sum += (double) f * f;
            }
            return Math.sqrt(sum);
        }
    }

    public record PprSpreadResult(
            List<GraphEntity> activatedEntities,
            Map<String, Double> pprScores,
            int totalIterations,
            double convergenceDelta,
            long elapsedMillis
    ) {}

    /**
     * 执行超球面图谱个性化 PageRank 拓扑扩散
     *
     * @param entities       实体节点候选集合
     * @param seedEntityIds  Query 识别出的种子实体 ID
     * @param queryVector    Query 1536 维单位向量
     * @param dampingFactor  阻尼系数 (默认 0.85)
     * @param maxIterations  最大迭代轮次 (推荐 5~10 轮)
     * @param topKEntities   激活保留的顶层实体数量
     * @return 扩散激活结果
     */
    public PprSpreadResult spreadOverHypersphericalGraph(
            List<GraphEntity> entities,
            List<String> seedEntityIds,
            float[] queryVector,
            double dampingFactor,
            int maxIterations,
            int topKEntities
    ) {
        long startTime = System.currentTimeMillis();
        int n = entities.size();
        if (n == 0) {
            return new PprSpreadResult(Collections.emptyList(), Map.of(), 0, 0.0, 0);
        }

        // 1. 构建初始个性化偏置向量 p0
        double[] p0 = new double[n];
        Set<String> seedSet = new HashSet<>(seedEntityIds != null ? seedEntityIds : Collections.emptyList());
        int seedCount = 0;
        for (int i = 0; i < n; i++) {
            if (seedSet.contains(entities.get(i).entityId())) {
                p0[i] = 1.0;
                seedCount++;
            }
        }
        if (seedCount > 0) {
            for (int i = 0; i < n; i++) {
                p0[i] /= seedCount;
            }
        } else {
            // 若无显式种子，按与 queryVector 的余弦相似度初始化
            double sumCos = 0.0;
            for (int i = 0; i < n; i++) {
                double cos = Math.max(0.0, dotProduct(entities.get(i).embedding1536(), queryVector));
                p0[i] = cos;
                sumCos += cos;
            }
            if (sumCos > 1e-6) {
                for (int i = 0; i < n; i++) {
                    p0[i] /= sumCos;
                }
            } else {
                Arrays.fill(p0, 1.0 / n);
            }
        }

        // 2. 构建超球面余弦转移概率矩阵 M (行归一化)
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++) {
            double rowSum = 0.0;
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    double cos = Math.max(0.0, dotProduct(entities.get(i).embedding1536(), entities.get(j).embedding1536()));
                    m[i][j] = cos;
                    rowSum += cos;
                }
            }
            if (rowSum > 1e-6) {
                for (int j = 0; j < n; j++) {
                    m[i][j] /= rowSum;
                }
            } else {
                Arrays.fill(m[i], 1.0 / n);
            }
        }

        // 3. 幂迭代求解 p = (1 - alpha) * p0 + alpha * M^T * p
        double[] p = Arrays.copyOf(p0, n);
        double delta = 1.0;
        int iter = 0;
        while (iter < maxIterations && delta > 1e-4) {
            iter++;
            double[] nextP = new double[n];
            for (int j = 0; j < n; j++) {
                double transfer = 0.0;
                for (int i = 0; i < n; i++) {
                    transfer += p[i] * m[i][j];
                }
                nextP[j] = (1.0 - dampingFactor) * p0[j] + dampingFactor * transfer;
            }

            delta = 0.0;
            for (int i = 0; i < n; i++) {
                delta += Math.abs(nextP[i] - p[i]);
            }
            p = nextP;
        }

        // 4. 按 PPR 得分排序并截取 Top-K
        record ScoredNode(GraphEntity entity, double score) {}
        List<ScoredNode> scoredList = new ArrayList<>();
        Map<String, Double> scoreMap = new HashMap<>();
        for (int i = 0; i < n; i++) {
            scoredList.add(new ScoredNode(entities.get(i), p[i]));
            scoreMap.put(entities.get(i).entityId(), p[i]);
        }
        scoredList.sort((a, b) -> Double.compare(b.score(), a.score()));

        int limit = Math.min(topKEntities, scoredList.size());
        List<GraphEntity> topEntities = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            topEntities.add(scoredList.get(i).entity());
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[Hippo-PPR] 拓扑扩散完成: 节点数={}, 迭代轮次={}, 收敛残差={}, 激活实体数={}, 耗时={}ms",
                n, iter, String.format("%.6f", delta), topEntities.size(), elapsed);

        return new PprSpreadResult(
                Collections.unmodifiableList(topEntities),
                Collections.unmodifiableMap(scoreMap),
                iter,
                delta,
                elapsed
        );
    }

    private static double dotProduct(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < v1.length; i++) {
            sum += (double) v1[i] * v2[i];
        }
        return sum;
    }
}
