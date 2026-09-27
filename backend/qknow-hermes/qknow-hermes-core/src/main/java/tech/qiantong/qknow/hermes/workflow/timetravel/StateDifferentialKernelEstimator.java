package tech.qiantong.qknow.hermes.workflow.timetravel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * 阿里千问 1536 维超球面状态差异测地线核与置信度门控投机估算器
 * <p>
 * 严格遵循系统架构模型与超球面几何基线 (Hyperspherical Geometry Baseline)：
 * 1. 向量空间维度锁定为 1536 维，向量严格满足 L2 范数单位归一化约束 (||v||_2 = 1.0)；
 * 2. 状态差异度量基于超球面测地线核：K_diff(S_i, S_j) = 1.0 - arccos(u · v) / π；
 * 3. 严格遵循欧氏与余弦几何度规恒等式：d_Euc^2 = 2(1 - cos θ)；
 * 4. 置信度门控投机执行阈值锁定为 γ_spec = 0.88，投机保真度损失严格满足 Lemma 148.2：L_fid <= 4 * sin^2(π * (1 - γ) / 2)。
 *
 * @author Achilles
 * @since Phase 148
 */
public class StateDifferentialKernelEstimator {

    /**
     * 阿里千问 (Qwen) Embedding 官方固定向量维度
     */
    public static final int EMBEDDING_DIMENSION = 1536;

    /**
     * 投机执行置信度准入门控临界阈值 (γ_spec = 0.88)
     */
    public static final double SPECULATION_CONFIDENCE_THRESHOLD = 0.88;

    /**
     * 理论投机保真度损失上限 (Lemma 148.2: 4 * sin^2(0.06π) ≈ 0.1416 < 0.15)
     */
    public static final double THEORETICAL_MAX_FIDELITY_LOSS = 0.15;

    /**
     * 计算两个 1536 维超球面单位向量的内积 (Cosine Similarity)
     *
     * @param u 单位向量 u
     * @param v 单位向量 v
     * @return 严格截断在 [-1.0, 1.0] 的点积
     */
    public static double computeCosineSimilarity(double[] u, double[] v) {
        validateVector(u, "向量 u");
        validateVector(v, "向量 v");

        double dotProduct = 0.0;
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            dotProduct += u[i] * v[i];
        }

        // 浮点数截断，防止由于数值精度微小抖动超出反余弦定义域
        if (dotProduct > 1.0) {
            dotProduct = 1.0;
        } else if (dotProduct < -1.0) {
            dotProduct = -1.0;
        }
        return dotProduct;
    }

    /**
     * 计算两个超球面状态节点之间的测地线角度距离 (Geodesic Distance on S^1535)
     *
     * @param u 节点状态向量 u
     * @param v 节点状态向量 v
     * @return 测地线角度 θ ∈ [0, π]
     */
    public static double computeGeodesicAngle(double[] u, double[] v) {
        if (u == v) {
            return 0.0;
        }
        double cosTheta = computeCosineSimilarity(u, v);
        if (cosTheta >= 1.0 - 1e-12) {
            return 0.0;
        }
        if (cosTheta <= -1.0 + 1e-12) {
            return Math.PI;
        }
        return Math.acos(cosTheta);
    }

    /**
     * 阿里千问 1536 维超球面状态差异测地线核函数 (State Differential Geodesic Kernel)
     * K_diff(S_i, S_j) = 1.0 - θ / π ∈ [0.0, 1.0]
     * <p>
     * 性质：
     * - 当 u 与 v 重合 (完全相同状态) 时，θ = 0，K_diff = 1.0；
     * - 当 u 与 v 正交 (完全独立状态) 时，θ = π/2，K_diff = 0.5；
     * - 当 u 与 v 反向 (完全对立状态) 时，θ = π，K_diff = 0.0。
     *
     * @param u 状态节点 i 的特征向量
     * @param v 状态节点 j 的特征向量
     * @return 测地线正定核相似度
     */
    public static double computeDifferentialKernel(double[] u, double[] v) {
        double theta = computeGeodesicAngle(u, v);
        return 1.0 - (theta / Math.PI);
    }

    /**
     * 验证欧氏空间距离与超球面余弦度规的几何恒等式：d_Euc^2 = 2 * (1 - cos θ)
     *
     * @param u 状态节点向量 u
     * @param v 状态节点向量 v
     * @return 恒等式相对误差，理论上应接近 0.0
     */
    public static double verifyEuclideanCosineIdentityError(double[] u, double[] v) {
        double cosTheta = computeCosineSimilarity(u, v);
        double theoreticalEucSq = 2.0 * (1.0 - cosTheta);

        double actualEucSq = 0.0;
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            double diff = u[i] - v[i];
            actualEucSq += diff * diff;
        }
        return Math.abs(theoreticalEucSq - actualEucSq);
    }

    /**
     * 判定当前分支置信度是否满足投机执行门控条件 (γ >= 0.88)
     *
     * @param confidence 预测分支置信度
     * @return 若符合投机执行条件则返回 true，否则返回 false
     */
    public static boolean isSpeculationEligible(double confidence) {
        return confidence >= SPECULATION_CONFIDENCE_THRESHOLD;
    }

    /**
     * 根据 Lemma 148.2 估算分支投机执行的保真度损失理论上界
     * L_fid <= 4 * sin^2(π * (1 - confidence) / 2)
     *
     * @param confidence 分支置信度
     * @return 投机保真度损失理论上界
     */
    public static double estimateSpeculativeFidelityLoss(double confidence) {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("置信度必须在 [0.0, 1.0] 区间内: " + confidence);
        }
        double halfAngle = Math.PI * (1.0 - confidence) / 2.0;
        double sinVal = Math.sin(halfAngle);
        return 4.0 * sinVal * sinVal;
    }

    /**
     * 面向 DeepSeek 官方 1M Context Caching 规约，校验状态前缀对齐因子
     *
     * @param promptPrefixA 前一状态 Prompt 规范化前缀
     * @param promptPrefixB 回退重播 Prompt 规范化前缀
     * @return 前缀公共重叠度比率 ∈ [0.0, 1.0]
     */
    public static double calculatePrefixCacheAlignment(String promptPrefixA, String promptPrefixB) {
        if (promptPrefixA == null || promptPrefixB == null || promptPrefixA.isEmpty() || promptPrefixB.isEmpty()) {
            return 0.0;
        }
        int minLen = Math.min(promptPrefixA.length(), promptPrefixB.length());
        int commonLen = 0;
        while (commonLen < minLen && promptPrefixA.charAt(commonLen) == promptPrefixB.charAt(commonLen)) {
            commonLen++;
        }
        return (double) commonLen / Math.max(promptPrefixA.length(), promptPrefixB.length());
    }

    /**
     * 对任意原始向量进行 L2 范数超球面单位归一化 (||v||_2 = 1.0)
     *
     * @param rawVector 原始输入特征向量
     * @return 严格满足超球面单位范数的归一化向量
     */
    public static double[] normalizeToUnitSphere(double[] rawVector) {
        Objects.requireNonNull(rawVector, "输入向量不能为空");
        if (rawVector.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("向量维度必须精确为 " + EMBEDDING_DIMENSION + "，当前为: " + rawVector.length);
        }

        double normSq = 0.0;
        for (double val : rawVector) {
            normSq += val * val;
        }

        double norm = Math.sqrt(normSq);
        if (norm < 1e-12) {
            // 退化零向量赋予均匀单位球面初值
            double uniformVal = 1.0 / Math.sqrt(EMBEDDING_DIMENSION);
            double[] result = new double[EMBEDDING_DIMENSION];
            Arrays.fill(result, uniformVal);
            return result;
        }

        double[] normalized = new double[EMBEDDING_DIMENSION];
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            normalized[i] = rawVector[i] / norm;
        }
        return normalized;
    }

    /**
     * 基于种子字符串确定性生成阿里千问 1536 维超球面单位向量 (用于基准测试与模拟)
     *
     * @param seed 确定性种子文本
     * @return 1536 维超球面单位向量
     */
    public static double[] generateDeterministicSphericalEmbedding(String seed) {
        Objects.requireNonNull(seed, "种子文本不能为空");
        double[] raw = new double[EMBEDDING_DIMENSION];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] currentHash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));

            int bytesRead = 0;
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                if (bytesRead >= currentHash.length - 2) {
                    currentHash = digest.digest(currentHash);
                    bytesRead = 0;
                }
                int byteVal = ((currentHash[bytesRead] & 0xFF) << 8) | (currentHash[bytesRead + 1] & 0xFF);
                raw[i] = (byteVal / 32767.5) - 1.0;
                bytesRead += 2;
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("缺失 SHA-256 算法", e);
        }
        return normalizeToUnitSphere(raw);
    }

    /**
     * 校验向量维度与超球面单位模长约束
     */
    private static void validateVector(double[] v, String name) {
        Objects.requireNonNull(v, name + " 不能为空");
        if (v.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException(name + " 维度不合法，期望 " + EMBEDDING_DIMENSION + "，实际: " + v.length);
        }
    }
}
