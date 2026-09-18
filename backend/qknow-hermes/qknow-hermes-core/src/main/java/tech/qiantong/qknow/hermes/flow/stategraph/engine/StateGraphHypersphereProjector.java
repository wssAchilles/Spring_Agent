package tech.qiantong.qknow.hermes.flow.stategraph.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphContext;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 阿里千问 1536 维超球面流形投影算子 (StateGraphHypersphereProjector)
 * 严格实施保模归一化 ||v|| = 1.0 +/- 10^-4 与测地几何距离计算
 */
@Slf4j
@Component
public class StateGraphHypersphereProjector {

    public static final int QWEN_EMBEDDING_DIM = 1536;
    private final int dimension;

    public StateGraphHypersphereProjector() {
        this(QWEN_EMBEDDING_DIM);
    }

    public StateGraphHypersphereProjector(int dimension) {
        this.dimension = dimension > 0 ? dimension : QWEN_EMBEDDING_DIM;
    }

    /**
     * 将任意连续向量投影到超球面流形 S^{D-1}
     *
     * @param rawVector 原始输入向量
     * @return 保模归一化向量
     */
    public double[] projectToHypersphere(double[] rawVector) {
        if (rawVector == null || rawVector.length == 0) {
            return createDefaultUnitVector();
        }

        double[] aligned = new double[dimension];
        int copyLen = Math.min(rawVector.length, dimension);
        System.arraycopy(rawVector, 0, aligned, 0, copyLen);

        double norm = computeL2Norm(aligned);
        // 若向量接近于零，添加微小高斯扰动进行正则化防除以零
        if (norm < 1e-12) {
            aligned[0] = 1.0;
            norm = 1.0;
        }

        double invNorm = 1.0 / norm;
        for (int i = 0; i < dimension; i++) {
            aligned[i] *= invNorm;
        }
        return aligned;
    }

    /**
     * 计算向量的 L2 欧几里得范数
     */
    public double computeL2Norm(double[] vector) {
        if (vector == null || vector.length == 0) return 0.0;
        double sumSq = 0.0;
        for (double v : vector) {
            sumSq += v * v;
        }
        return Math.sqrt(sumSq);
    }

    /**
     * 计算两个单位向量在超球面流形上的测地距离 (Geodesic Distance)
     * 映射归一化至 [0, 1] 区间
     * d_S(u, v) = arccos(clamp(u · v, -1.0, 1.0)) / PI
     */
    public double computeGeodesicDistance(double[] u, double[] v) {
        if (u == null || v == null || u.length != dimension || v.length != dimension) {
            return 1.0;
        }

        double dotProduct = 0.0;
        for (int i = 0; i < dimension; i++) {
            dotProduct += u[i] * v[i];
        }

        // 数值截断保证 arccos 参数合法性
        double clamped = Math.max(-1.0, Math.min(1.0, dotProduct));
        return Math.acos(clamped) / Math.PI;
    }

    /**
     * 生成随机单位向量（用于测试与基准）
     */
    public double[] createRandomUnitVector() {
        double[] vec = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            vec[i] = ThreadLocalRandom.current().nextGaussian();
        }
        return projectToHypersphere(vec);
    }

    /**
     * 默认单位基底向量
     */
    private double[] createDefaultUnitVector() {
        double[] vec = new double[dimension];
        vec[0] = 1.0;
        return vec;
    }

    /**
     * 根据上下文特征生成超球面状态快照
     */
    public double[] projectState(StateGraphContext context) {
        double[] feature = new double[dimension];
        if (context != null) {
            feature[0] = context.getCurrentSuperstep().get();
            feature[1] = context.getRemainingRetryBudget().get();
            feature[2] = context.getDegradedBreakOccurred().get() ? 1.0 : 0.0;
            feature[3] = context.getSelfHealedOccurred().get() ? 1.0 : 0.0;
            feature[4] = context.getNodeResults().size();
        }
        return projectToHypersphere(feature);
    }
}
