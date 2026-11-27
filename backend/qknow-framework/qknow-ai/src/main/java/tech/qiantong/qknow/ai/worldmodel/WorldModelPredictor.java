package tech.qiantong.qknow.ai.worldmodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 探索性超球面状态转移插值预测器（Experimental Manifold State Predictor on S^1023）。
 * <p>
 * 架构定位声明：本组件为独立探索性基线模块（不假冒已训练神经网络世界模型），博士论文主线认知路由由第 3 章
 * {@code HypersphericalSupernetRouter} 承接。当注入真实千问 {@link EmbeddingModel} 时采用真实动作语义向量执行球面收缩插值；
 * 离线单测下采用确定性 SplitMix64 基底与局部敏感特征哈希保证利普希茨连续性。
 * </p>
 */
@Component
public class WorldModelPredictor {

    private static final Logger log = LoggerFactory.getLogger(WorldModelPredictor.class);

    /** 阿里千问标准 Embedding 维度: 1024 维 */
    public static final int EMBEDDING_DIMENSION = 1024;

    /** 利普希茨状态收缩系数 L_s < 1.0 */
    public static final double LIPSCHITZ_CONSTANT = 0.85;

    private final EmbeddingModel embeddingModel;

    public WorldModelPredictor() {
        this(null);
    }

    @Autowired
    public WorldModelPredictor(@Autowired(required = false) EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 潜在状态向量表示 (千问 1024 维超球面单位向量)
     */
    public record LatentState(double[] vector, long stepIndex, String stateHash) {
        public LatentState {
            if (vector == null || vector.length != EMBEDDING_DIMENSION) {
                throw new IllegalArgumentException("潜在状态向量必须严格为 " + EMBEDDING_DIMENSION + " 维");
            }
        }
    }

    /**
     * 智能体执行动作向量模型
     */
    public record AgentAction(String agentId, String actionType, double intensity, Map<String, Object> parameters) {}

    /**
     * 创建基于确定性拟蒙特卡洛基底的单位超球面 S^1023 初始状态
     */
    public LatentState createInitialState(long seed) {
        double[] v = LocalitySensitiveFeatureHasher.deterministicBasisVectorDouble(seed, EMBEDDING_DIMENSION);
        return new LatentState(v, 0L, computeVectorHash(v));
    }

    /**
     * 前向潜在状态单步球面收缩预测: s_{t+1} = \Pi_{S^{1023}}( L_s * s_t + \Delta(a_t) )
     */
    public LatentState predictNextState(LatentState currentState, List<AgentAction> actions) {
        if (currentState == null) {
            throw new IllegalArgumentException("当前潜在状态不能为空");
        }

        double[] next = new double[EMBEDDING_DIMENSION];
        double[] curr = currentState.vector();

        // 1. 状态自身衰减项: L_s * s_t
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            next[i] = LIPSCHITZ_CONSTANT * curr[i];
        }

        // 2. 累加各智能体联合动作的局部敏感语义方向分量（消除 Random(hashCode) 白噪声）
        if (actions != null) {
            for (AgentAction act : actions) {
                double factor = (1.0 - LIPSCHITZ_CONSTANT) * Math.min(1.0, Math.max(-1.0, act.intensity()));
                String actionDescriptor = (act.actionType() != null ? act.actionType() : "NOOP")
                        + ":" + (act.parameters() != null ? act.parameters().keySet() : "");
                double[] actionDir = resolveActionDirection(actionDescriptor);
                for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                    next[i] += factor * actionDir[i] * 0.25;
                }
            }
        }

        // 3. 超球面投影归一化 (保模约束 ||s|| = 1.0)
        normalizeToHypersphere(next);

        long nextStep = currentState.stepIndex() + 1;
        String hash = computeVectorHash(next);

        return new LatentState(next, nextStep, hash);
    }

    private double[] resolveActionDirection(String actionDescriptor) {
        if (embeddingModel != null) {
            float[] raw = embeddingModel.embed(actionDescriptor);
            if (raw != null && raw.length == EMBEDDING_DIMENSION) {
                double[] dir = new double[EMBEDDING_DIMENSION];
                for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                    dir[i] = raw[i];
                }
                normalizeToHypersphere(dir);
                return dir;
            }
        }
        return LocalitySensitiveFeatureHasher.projectToUnitSphereDouble(actionDescriptor, EMBEDDING_DIMENSION);
    }

    /**
     * 计算两潜在状态在超球面上的测地线夹角距离 (Geodesic Distance / Angular Distance):
     * \theta = \arccos( \langle s_1, s_2 \rangle ) \in [0, \pi]
     */
    public double computeGeodesicDistance(LatentState s1, LatentState s2) {
        double dot = 0.0;
        double[] v1 = s1.vector();
        double[] v2 = s2.vector();
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            dot += v1[i] * v2[i];
        }
        // 防止浮点精度微弱越界 [-1.0, 1.0]
        double clamped = Math.max(-1.0, Math.min(1.0, dot));
        return Math.acos(clamped);
    }

    private void normalizeToHypersphere(double[] v) {
        double sumSq = 0.0;
        for (double val : v) {
            sumSq += val * val;
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 1e-12) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= norm;
            }
        } else {
            v[0] = 1.0;
        }
    }

    private String computeVectorHash(double[] v) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            // 采样前中后关键维度计算指纹（自适应向量长度步长，1024 维下步长为 64）
            int stride = Math.max(1, v.length / 16);
            int sampleCount = Math.min(16, v.length);
            for (int i = 0; i < sampleCount; i++) {
                sb.append(String.format(Locale.US, "%.4f,", v[i * stride]));
            }
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte b : digest) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException e) {
            return "VEC_HASH_" + Arrays.hashCode(v);
        }
    }
}
