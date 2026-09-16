package tech.qiantong.qknow.hermes.superconvergence.engine;

import tech.qiantong.qknow.hermes.superconvergence.dto.MetacognitiveContextFrame;

import java.util.Arrays;
import java.util.List;

/**
 * 超球面元认知对齐中枢（阿里千问 1536 维流形保模投影与测地无偏聚合）
 */
public class HypersphericalMetacognitiveAligner {

    public static final int DIMENSION = 1536;
    private static final double EPSILON = 1e-12;

    /**
     * 8 路循环展开高性能向量点积（纳秒级）
     */
    public double dotProduct(double[] u, double[] v) {
        if (u == null || v == null || u.length != v.length) {
            throw new IllegalArgumentException("向量必须非空且长度一致");
        }
        int n = u.length;
        int limit = n - (n % 8);
        double s0 = 0, s1 = 0, s2 = 0, s3 = 0, s4 = 0, s5 = 0, s6 = 0, s7 = 0;
        for (int i = 0; i < limit; i += 8) {
            s0 += u[i] * v[i];
            s1 += u[i + 1] * v[i + 1];
            s2 += u[i + 2] * v[i + 2];
            s3 += u[i + 3] * v[i + 3];
            s4 += u[i + 4] * v[i + 4];
            s5 += u[i + 5] * v[i + 5];
            s6 += u[i + 6] * v[i + 6];
            s7 += u[i + 7] * v[i + 7];
        }
        double sum = (s0 + s1) + (s2 + s3) + (s4 + s5) + (s6 + s7);
        for (int i = limit; i < n; i++) {
            sum += u[i] * v[i];
        }
        return sum;
    }

    /**
     * 将高维原始特征严格投影并归一化至单位超球面 S^1535 (||v||_2 = 1.0)
     */
    public double[] projectToHypersphere(double[] rawVector) {
        if (rawVector == null || rawVector.length != DIMENSION) {
            throw new IllegalArgumentException("rawVector 必须严格为 1536 维");
        }
        double normSq = dotProduct(rawVector, rawVector);
        double norm = Math.sqrt(normSq);
        double[] normalized = new double[DIMENSION];
        if (norm < EPSILON) {
            normalized[0] = 1.0; // 零向量默认投影到基准北极点
            return normalized;
        }
        double invNorm = 1.0 / norm;
        for (int i = 0; i < DIMENSION; i++) {
            normalized[i] = rawVector[i] * invNorm;
        }
        return normalized;
    }

    /**
     * 计算超球面黎曼测地距离: d_g(u, v) = arccos(clamp(u^T v, -1.0, 1.0))
     */
    public double geodesicDistance(double[] u, double[] v) {
        double dot = dotProduct(u, v);
        double clamped = Math.max(-1.0, Math.min(1.0, dot));
        return Math.acos(clamped);
    }

    /**
     * 切空间加权聚合与 Fréchet 均值保模投影
     */
    public double[] aggregateFrechetMean(List<double[]> vectors, double[] weights) {
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("vectors 不能为空");
        }
        int count = vectors.size();
        double[] finalWeights = new double[count];
        double totalWeight = 0.0;
        for (int i = 0; i < count; i++) {
            double w = (weights != null && i < weights.length) ? weights[i] : 1.0;
            finalWeights[i] = w;
            totalWeight += w;
        }
        if (totalWeight < EPSILON) {
            totalWeight = 1.0;
        }

        double[] meanVector = new double[DIMENSION];
        for (int i = 0; i < count; i++) {
            double[] v = vectors.get(i);
            double normalizedW = finalWeights[i] / totalWeight;
            for (int d = 0; d < DIMENSION; d++) {
                meanVector[d] += v[d] * normalizedW;
            }
        }
        return projectToHypersphere(meanVector);
    }

    /**
     * 构建对齐的元认知单帧 Record
     */
    public MetacognitiveContextFrame alignContext(
        String frameId,
        String sessionId,
        double[] rawVector,
        String intentCategory,
        double confidenceScore
    ) {
        double[] normalized = projectToHypersphere(rawVector);
        // 香农熵估计（基于特征分布平方和）
        double entropy = 0.0;
        for (double v : normalized) {
            double p = v * v;
            if (p > 1e-12) {
                entropy -= p * Math.log(p);
            }
        }

        return new MetacognitiveContextFrame(
            frameId,
            sessionId,
            normalized,
            entropy,
            confidenceScore,
            intentCategory != null ? intentCategory : "GENERAL_REASONING",
            true,
            System.currentTimeMillis()
        );
    }

    /**
     * 计算元认知语义漂移率: drift = 1.0 - (u^T basis)
     */
    public double computeMetacognitiveDrift(MetacognitiveContextFrame frame, double[] targetBasis) {
        if (frame == null || targetBasis == null) {
            return 1.0;
        }
        double dot = dotProduct(frame.embeddingVector(), targetBasis);
        return Math.max(0.0, 1.0 - Math.abs(dot));
    }
}
