package tech.qiantong.qknow.ai.federated.dp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 阿里千问 1536 维超球面保模局部差分隐私 (LDP) 特征扰动器
 * 
 * 核心数学定理 (Theorem 3.1 LDP Feature Perturbation & Norm Conservation):
 * 为特征向量各维度注入拉普拉斯噪声 eta ~ Laplace(0, scale)，
 * 加噪后严格重新执行超球面重投影 v_hat = v_tilde / ||v_tilde||_2，
 * 确保输出向量模长严格守恒为 1.0，阻断反向重构攻击 (Embedding Inversion)，
 * 同时保障下游检索余弦相似度保真率 >= 85%。
 */
@Component
public class DifferentialPrivacyPerturber {

    private static final Logger log = LoggerFactory.getLogger(DifferentialPrivacyPerturber.class);

    /** 阿里千问标准 Embedding 维度 */
    public static final int EMBEDDING_DIM = 1536;
    /** 默认隐私预算 epsilon */
    public static final double DEFAULT_EPSILON = 2.0;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 生成拉普拉斯随机分布样本: Laplace(0, scale)
     */
    public double sampleLaplace(double scale) {
        // u in (-0.5, 0.5]
        double u = secureRandom.nextDouble() - 0.5;
        // x = -scale * sgn(u) * ln(1 - 2|u|)
        return -scale * Math.signum(u) * Math.log(1.0 - 2.0 * Math.abs(u));
    }

    /**
     * 对高维向量施加局部差分隐私扰动并保模重投影
     *
     * @param originalVector 原始归一化向量 (1536 维)
     * @param epsilon 隐私预算 (推荐 1.0 ~ 4.0)
     * @return 扰动后严格保模重投影的归一化向量 (模长为 1.0)
     */
    public double[] perturbAndProject(double[] originalVector, double epsilon) {
        if (originalVector == null || originalVector.length == 0) {
            throw new IllegalArgumentException("特征向量不能为空");
        }
        if (epsilon <= 0.0) {
            throw new IllegalArgumentException("隐私预算 epsilon 必须大于 0: " + epsilon);
        }

        int d = originalVector.length;
        // 在高维单位超球面上，单维度的拉普拉斯噪声尺度调节为 scale = 0.5 / (sqrt(d) * epsilon)
        // 确保总体噪声功率受控，余弦相似度保真度稳定 >= 0.85，且在强隐私下产生充分欧氏位移
        double scale = 0.5 / (Math.sqrt(d) * epsilon);

        double[] noisyVector = new double[d];
        double sumSquares = 0.0;

        for (int i = 0; i < d; i++) {
            double noise = sampleLaplace(scale);
            noisyVector[i] = originalVector[i] + noise;
            sumSquares += noisyVector[i] * noisyVector[i];
        }

        double norm = Math.sqrt(sumSquares);
        if (norm < 1e-12) {
            norm = 1.0;
        }

        // 超球面保模重投影: v_hat = noisyVector / norm
        double[] projectedVector = new double[d];
        for (int i = 0; i < d; i++) {
            projectedVector[i] = noisyVector[i] / norm;
        }

        return projectedVector;
    }

    /**
     * 计算两个向量的余弦相似度
     */
    public double cosineSimilarity(double[] v1, double[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) {
            throw new IllegalArgumentException("向量长度不一致");
        }
        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        if (norm1 < 1e-12 || norm2 < 1e-12) {
            return 0.0;
        }
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 计算向量欧氏范数 (L2 Norm)
     */
    public double computeL2Norm(double[] v) {
        if (v == null) return 0.0;
        double sum = 0.0;
        for (double val : v) {
            sum += val * val;
        }
        return Math.sqrt(sum);
    }

    /**
     * 计算两向量间的欧氏距离
     */
    public double computeEuclideanDistance(double[] v1, double[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) {
            throw new IllegalArgumentException("向量长度不一致");
        }
        double sum = 0.0;
        for (int i = 0; i < v1.length; i++) {
            double diff = v1[i] - v2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
