package tech.qiantong.qknow.ai.consensus.nash;

import java.util.Arrays;

/**
 * 纯 Java 21 堆内轻量级纳什均衡求解算子 (定理 1.1)
 * 提供概率单纯形策略投影、Jensen-Shannon 散度度量与双矩阵博弈纳什均衡求解，单次计算耗时 <= 100μs
 */
public class NashEquilibriumCalculator {

    public static final double DEFAULT_EPSILON = 0.05; // ε-Nash 均衡收敛阈值

    /**
     * 计算两个策略概率分布之间的 Jensen-Shannon (JS) 散度作为 ε-Nash 收敛距离
     *
     * @param p 前一轮策略概率分布 (满足 sum(p) = 1.0, p_i >= 0)
     * @param q 当前轮策略概率分布 (满足 sum(q) = 1.0, q_i >= 0)
     * @return JS 散度 [0.0, 1.0]
     */
    public double calculateJensenShannonDivergence(double[] p, double[] q) {
        if (p == null || q == null || p.length != q.length || p.length == 0) {
            throw new IllegalArgumentException("策略概率分布向量必须非空且具有相同维度");
        }

        int n = p.length;
        double[] m = new double[n];
        for (int i = 0; i < n; i++) {
            m[i] = 0.5 * (p[i] + q[i]);
        }

        double klPm = calculateKLDivergence(p, m);
        double klQm = calculateKLDivergence(q, m);

        double js = 0.5 * (klPm + klQm);
        return Math.max(0.0, Math.min(1.0, js));
    }

    /**
     * 计算 Kullback-Leibler (KL) 散度: D_KL(P || Q) = sum(P(i) * log2(P(i) / Q(i)))
     */
    private double calculateKLDivergence(double[] p, double[] q) {
        double kl = 0.0;
        for (int i = 0; i < p.length; i++) {
            if (p[i] > 1e-12) {
                if (q[i] <= 1e-12) {
                    // 平滑避免无穷大
                    kl += p[i] * (Math.log(p[i] / 1e-12) / Math.log(2.0));
                } else {
                    kl += p[i] * (Math.log(p[i] / q[i]) / Math.log(2.0));
                }
            }
        }
        return Math.max(0.0, kl);
    }

    /**
     * 求解 2x2 双矩阵博弈 (Bimatrix Game) 的混合策略纳什均衡
     * 设 Proposer 收益矩阵为 U1, Opponent 收益矩阵为 U2
     *
     * @param u1 Proposer 2x2 收益矩阵
     * @param u2 Opponent 2x2 收益矩阵
     * @return 最优策略分布 [p_proposer, p_opponent]
     */
    public double[] solve2x2MixedNashEquilibrium(double[][] u1, double[][] u2) {
        if (u1 == null || u2 == null || u1.length != 2 || u2.length != 2 ||
                u1[0].length != 2 || u2[0].length != 2) {
            throw new IllegalArgumentException("收益矩阵必须为 2x2 维度");
        }

        // Opponent 使 Proposer 无差异:
        // q * u1[0][0] + (1-q) * u1[0][1] = q * u1[1][0] + (1-q) * u1[1][1]
        // q * (u1[0][0] - u1[0][1] - u1[1][0] + u1[1][1]) = u1[1][1] - u1[0][1]
        double denomQ = (u1[0][0] - u1[0][1] - u1[1][0] + u1[1][1]);
        double numQ = u1[1][1] - u1[0][1];
        double q;
        if (Math.abs(denomQ) < 1e-9) {
            q = 0.5; // 退化情况均分
        } else {
            q = numQ / denomQ;
            q = Math.max(0.0, Math.min(1.0, q)); // 截断至 [0, 1]
        }

        // Proposer 使 Opponent 无差异:
        // p * u2[0][0] + (1-p) * u2[1][0] = p * u2[0][1] + (1-p) * u2[1][1]
        // p * (u2[0][0] - u2[1][0] - u2[0][1] + u2[1][1]) = u2[1][1] - u2[1][0]
        double denomP = (u2[0][0] - u2[1][0] - u2[0][1] + u2[1][1]);
        double numP = u2[1][1] - u2[1][0];
        double p;
        if (Math.abs(denomP) < 1e-9) {
            p = 0.5;
        } else {
            p = numP / denomP;
            p = Math.max(0.0, Math.min(1.0, p));
        }

        return new double[]{p, q};
    }

    /**
     * 将任意得分向量投影到标准概率单纯形 Delta^K 上 (Euclidean Projection on Simplex)
     */
    public double[] projectToSimplex(double[] scores) {
        if (scores == null || scores.length == 0) {
            return new double[0];
        }
        int n = scores.length;
        double[] sorted = scores.clone();
        Arrays.sort(sorted);
        // 逆序
        for (int i = 0; i < n / 2; i++) {
            double temp = sorted[i];
            sorted[i] = sorted[n - 1 - i];
            sorted[n - 1 - i] = temp;
        }

        double cumSum = 0.0;
        double rho = 0.0;
        for (int i = 0; i < n; i++) {
            cumSum += sorted[i];
            double t = (cumSum - 1.0) / (i + 1);
            if (sorted[i] > t) {
                rho = t;
            }
        }

        double[] p = new double[n];
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            p[i] = Math.max(0.0, scores[i] - rho);
            sum += p[i];
        }
        if (sum > 1e-12) {
            for (int i = 0; i < n; i++) {
                p[i] /= sum;
            }
        } else {
            Arrays.fill(p, 1.0 / n);
        }
        return p;
    }
}
