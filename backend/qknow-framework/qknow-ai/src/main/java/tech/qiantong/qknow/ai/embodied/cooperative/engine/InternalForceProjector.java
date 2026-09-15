package tech.qiantong.qknow.ai.embodied.cooperative.engine;

/**
 * 抓取内力零空间正交解耦与凸约束硬截断投影器 (定理 1.1)
 * 实现抓取矩阵加权伪逆分解与内力歼灭: G * F_int == 0
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class InternalForceProjector {

    private final double maxSafeInternalForce;

    public InternalForceProjector(double maxSafeInternalForce) {
        if (maxSafeInternalForce <= 0) {
            throw new IllegalArgumentException("Max safe internal force must be positive");
        }
        this.maxSafeInternalForce = maxSafeInternalForce;
    }

    /**
     * 抓取矩阵正交投影分解结果 record
     */
    public record DecompositionResult(
            double[] externalWrench,
            double[] internalForces
    ) {
    }

    /**
     * 将联合力向量分解为任务合外力 (6维) 与内力零空间向量 (6m维)
     * 满足定理 1.1: F_net = G * F_combined, G * F_int == 0
     *
     * @param G          抓取矩阵 (6 x 6m)
     * @param F_combined 联合多臂力/力矩向量 (6m维)
     * @return 分解结果 (合外力与内力向量)
     */
    public DecompositionResult decompose(double[][] G, double[] F_combined) {
        if (G == null || G.length != 6) {
            throw new IllegalArgumentException("Grasp matrix G must have 6 rows");
        }
        int cols = G[0].length;
        if (F_combined == null || F_combined.length != cols) {
            throw new IllegalArgumentException("Combined force dimension must match grasp matrix column dimension: " + cols);
        }

        // 1. 计算合外力 F_ext = G * F_combined (6 维)
        double[] externalWrench = new double[6];
        for (int r = 0; r < 6; r++) {
            double sum = 0.0;
            for (int c = 0; c < cols; c++) {
                sum += G[r][c] * F_combined[c];
            }
            externalWrench[r] = sum;
        }

        // 2. 计算 A = G * G^T (6 x 6 矩阵)
        double[][] A = new double[6][6];
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                double sum = 0.0;
                for (int k = 0; k < cols; k++) {
                    sum += G[r][k] * G[c][k];
                }
                A[r][c] = sum;
            }
        }

        // 3. 对 A 求逆: A_inv = (G * G^T)^{-1}
        double[][] A_inv = invert6x6(A);

        // 4. 计算中间向量 v = A_inv * F_ext (6 维)
        double[] v = new double[6];
        for (int r = 0; r < 6; r++) {
            double sum = 0.0;
            for (int c = 0; c < 6; c++) {
                sum += A_inv[r][c] * externalWrench[c];
            }
            v[r] = sum;
        }

        // 5. 计算外力对应的最小范数运动力 F_motion = G^T * v (cols 维)
        // 保证 G * F_motion = G * G^T * A_inv * F_ext = F_ext
        double[] F_motion = new double[cols];
        for (int c = 0; c < cols; c++) {
            double sum = 0.0;
            for (int r = 0; r < 6; r++) {
                sum += G[r][c] * v[r];
            }
            F_motion[c] = sum;
        }

        // 6. 内力向量 F_int = F_combined - F_motion
        // 必然有 G * F_int = G * F_combined - G * F_motion = F_ext - F_ext == 0
        double[] internalForces = new double[cols];
        for (int i = 0; i < cols; i++) {
            internalForces[i] = F_combined[i] - F_motion[i];
        }

        return new DecompositionResult(externalWrench, internalForces);
    }

    /**
     * 对多智能体内力向量执行凸约束硬截断，防止搬运挤压撕裂工件
     *
     * @param internalForces 输入内力向量 (6m 维)
     * @return 截断后的安全内力向量
     */
    public double[] clampInternalForces(double[] internalForces) {
        if (internalForces == null) {
            return new double[0];
        }
        double[] clamped = internalForces.clone();
        int stride = (clamped.length % 6 == 0) ? 6 : 3;

        for (int i = 0; i < clamped.length; i += stride) {
            double fx = clamped[i];
            double fy = (i + 1 < clamped.length) ? clamped[i + 1] : 0.0;
            double fz = (i + 2 < clamped.length) ? clamped[i + 2] : 0.0;
            double norm = Math.sqrt(fx * fx + fy * fy + fz * fz);

            if (norm > maxSafeInternalForce && norm > 1e-9) {
                double scale = maxSafeInternalForce / norm;
                clamped[i] = fx * scale;
                if (i + 1 < clamped.length) clamped[i + 1] = fy * scale;
                if (i + 2 < clamped.length) clamped[i + 2] = fz * scale;
            }
        }
        return clamped;
    }

    /**
     * 高斯-约旦消元法求 6x6 矩阵逆
     */
    private static double[][] invert6x6(double[][] A) {
        int n = 6;
        double[][] aug = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                aug[i][j] = A[i][j];
            }
            aug[i][i + n] = 1.0;
        }

        for (int i = 0; i < n; i++) {
            int maxRow = i;
            double maxVal = Math.abs(aug[i][i]);
            for (int k = i + 1; k < n; k++) {
                if (Math.abs(aug[k][i]) > maxVal) {
                    maxVal = Math.abs(aug[k][i]);
                    maxRow = k;
                }
            }

            if (maxVal < 1e-12) {
                aug[i][i] += 1e-6;
            } else if (maxRow != i) {
                double[] temp = aug[i];
                aug[i] = aug[maxRow];
                aug[maxRow] = temp;
            }

            double pivot = aug[i][i];
            for (int j = 0; j < 2 * n; j++) {
                aug[i][j] /= pivot;
            }

            for (int k = 0; k < n; k++) {
                if (k != i) {
                    double factor = aug[k][i];
                    for (int j = 0; j < 2 * n; j++) {
                        aug[k][j] -= factor * aug[i][j];
                    }
                }
            }
        }

        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(aug[i], n, inv[i], 0, n);
        }
        return inv;
    }
}
