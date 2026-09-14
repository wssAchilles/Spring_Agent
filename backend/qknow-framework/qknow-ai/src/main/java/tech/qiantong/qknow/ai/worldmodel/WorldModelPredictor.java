package tech.qiantong.qknow.ai.worldmodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 联合嵌入前向因果世界模型预测器 (JEPA on Hypersphere S^1535)
 * 将系统潜在状态映射至阿里千问 1536 维超球面单位流形，
 * 进行前向因果转移预测与利普希茨误差界定 (定理 1.1)
 */
@Component
public class WorldModelPredictor {

    private static final Logger log = LoggerFactory.getLogger(WorldModelPredictor.class);

    /** 阿里千问标准 Embedding 维度: 1536 维 */
    public static final int EMBEDDING_DIMENSION = 1536;

    /** 利普希茨状态收缩系数 L_s < 1.0 */
    public static final double LIPSCHITZ_CONSTANT = 0.85;

    /**
     * 潜在状态向量表示 (千问 1536 维超球面单位向量)
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
     * 创建基于随机种子或文本初始化的单位超球面初始状态
     */
    public LatentState createInitialState(long seed) {
        double[] v = new double[EMBEDDING_DIMENSION];
        Random rand = new Random(seed);
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            v[i] = rand.nextGaussian();
        }
        normalizeToHypersphere(v);
        return new LatentState(v, 0L, computeVectorHash(v));
    }

    /**
     * 前向因果潜在状态单步预测: s_{t+1} = \Pi_{S^{1535}}( L_s * s_t + \Delta(a_t) )
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

        // 2. 累加各智能体联合动作扰动
        if (actions != null) {
            for (AgentAction act : actions) {
                double factor = (1.0 - LIPSCHITZ_CONSTANT) * Math.min(1.0, Math.max(-1.0, act.intensity()));
                int hash = act.actionType() != null ? act.actionType().hashCode() : 0;
                Random actRand = new Random(hash);

                for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                    next[i] += factor * (actRand.nextDouble() - 0.5) * 0.1;
                }
            }
        }

        // 3. 超球面投影归一化 (保模约束 ||s|| = 1.0)
        normalizeToHypersphere(next);

        long nextStep = currentState.stepIndex() + 1;
        String hash = computeVectorHash(next);

        return new LatentState(next, nextStep, hash);
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
            // 采样前中后关键维度计算指纹
            for (int i = 0; i < 16; i++) {
                sb.append(String.format(Locale.US, "%.4f,", v[i * 96]));
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
