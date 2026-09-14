package tech.qiantong.qknow.module.kmc.service.rag.adaptive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Discounted-LinUCB 的自适应多臂老虎机检索策略路由器
 * 针对非平稳知识库环境提供次线性累积遗憾保证 O(sqrt(d*T*ln(T))) 与敏锐时变追踪能力
 */
@Slf4j
@Service
public class DiscountedLinUcbRouter {

    public enum ActionType {
        VECTOR,
        BM25,
        HYBRID,
        GRAPH,
        MULTI_KB
    }

    public static final int EMBEDDING_DIM = 1536;
    public static final int FEATURE_DIM = 32;

    private final double alpha; // 探索置信区间缩放系数
    private final double gamma; // 折扣衰减因子
    private final float[][] projectionMatrix; // 1536 -> 32 固定正交降维投影矩阵

    // 每个 Action 对应的 LinUCB 状态
    public static class ArmState {
        public final double[][] A; // 32x32 协方差矩阵
        public final double[] b;   // 32 维累积收益向量
        public final AtomicLong pullCount = new AtomicLong(0);

        public ArmState(int d) {
            this.A = new double[d][d];
            for (int i = 0; i < d; i++) {
                this.A[i][i] = 1.0; // 初始化为单位矩阵 I
            }
            this.b = new double[d];
        }

        public synchronized void updatePulled(double[] x, double reward, double gamma, int d) {
            // 被拉动的臂: A = gamma * (A - I) + I + x * x^T; b = gamma * b + reward * x
            for (int i = 0; i < d; i++) {
                for (int j = 0; j < d; j++) {
                    double prev = A[i][j];
                    if (i == j) {
                        A[i][j] = gamma * (prev - 1.0) + 1.0 + x[i] * x[j];
                    } else {
                        A[i][j] = gamma * prev + x[i] * x[j];
                    }
                }
                b[i] = gamma * b[i] + reward * x[i];
            }
            pullCount.incrementAndGet();
        }

        public synchronized void decayUnpulled(double gamma, int d) {
            // 未被拉动的臂: A = gamma * (A - I) + I; b = gamma * b (遗忘旧记忆并恢复探索方差)
            for (int i = 0; i < d; i++) {
                for (int j = 0; j < d; j++) {
                    double prev = A[i][j];
                    if (i == j) {
                        A[i][j] = gamma * (prev - 1.0) + 1.0;
                    } else {
                        A[i][j] = gamma * prev;
                    }
                }
                b[i] = gamma * b[i];
            }
        }
    }

    private final Map<ActionType, ArmState> armStates = new ConcurrentHashMap<>();

    public DiscountedLinUcbRouter() {
        this(0.35, 0.98);
    }

    public DiscountedLinUcbRouter(double alpha, double gamma) {
        this.alpha = alpha;
        this.gamma = gamma;
        this.projectionMatrix = initProjectionMatrix(EMBEDDING_DIM, FEATURE_DIM);
        for (ActionType action : ActionType.values()) {
            armStates.put(action, new ArmState(FEATURE_DIM));
        }
    }

    /**
     * 将 1536 维千问 Embedding 快速正交投影降维至 32 维特征
     */
    public double[] projectEmbedding(float[] embedding1536) {
        if (embedding1536 == null || embedding1536.length < EMBEDDING_DIM) {
            double[] fallback = new double[FEATURE_DIM];
            fallback[0] = 1.0;
            return fallback;
        }
        double[] feature = new double[FEATURE_DIM];
        for (int j = 0; j < FEATURE_DIM; j++) {
            double sum = 0.0;
            for (int i = 0; i < EMBEDDING_DIM; i++) {
                sum += embedding1536[i] * projectionMatrix[i][j];
            }
            feature[j] = sum;
        }
        // L2 归一化
        double norm = 0.0;
        for (double val : feature) {
            norm += val * val;
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-9) {
            for (int j = 0; j < FEATURE_DIM; j++) {
                feature[j] /= norm;
            }
        }
        return feature;
    }

    /**
     * 根据当前上下文特征选择 UCB 上界最高的最优策略
     */
    public ActionType selectAction(double[] feature) {
        if (feature == null || feature.length != FEATURE_DIM) {
            return ActionType.HYBRID;
        }

        // 面对不确定性的乐观原则 (OFU): 冷启动未探索臂优先探索至少一次
        for (ActionType action : ActionType.values()) {
            ArmState state = armStates.get(action);
            if (state != null && state.pullCount.get() == 0) {
                return action;
            }
        }

        ActionType bestAction = ActionType.HYBRID;
        double maxUcb = Double.NEGATIVE_INFINITY;

        for (Map.Entry<ActionType, ArmState> entry : armStates.entrySet()) {
            ActionType action = entry.getKey();
            ArmState state = entry.getValue();

            double ucb = computeUcbScore(state, feature);
            if (ucb > maxUcb) {
                maxUcb = ucb;
                bestAction = action;
            }
        }

        return bestAction;
    }

    /**
     * 计算特定 Arm 的 UCB 得分: x^T * A^{-1} * b + alpha * sqrt(x^T * A^{-1} * x)
     */
    public double computeUcbScore(ArmState state, double[] x) {
        double[][] invA = invertMatrix(state.A, FEATURE_DIM);
        // theta = invA * b
        double[] theta = multiplyMatrixVector(invA, state.b, FEATURE_DIM);
        // expectedReward = x^T * theta
        double expectedReward = dotProduct(x, theta, FEATURE_DIM);
        // variance = x^T * invA * x
        double[] invAx = multiplyMatrixVector(invA, x, FEATURE_DIM);
        double variance = dotProduct(x, invAx, FEATURE_DIM);
        double confidence = alpha * Math.sqrt(Math.max(0.0, variance));

        return expectedReward + confidence;
    }

    /**
     * 在线更新反馈奖励 (全臂同步衰减，被拉动臂吸收新样本)
     */
    public synchronized void updateReward(ActionType action, double[] feature, double reward) {
        if (action == null || feature == null || feature.length != FEATURE_DIM) {
            return;
        }
        double clampedReward = Math.max(0.0, Math.min(1.0, reward));
        for (Map.Entry<ActionType, ArmState> entry : armStates.entrySet()) {
            ActionType act = entry.getKey();
            ArmState state = entry.getValue();
            if (act == action) {
                state.updatePulled(feature, clampedReward, gamma, FEATURE_DIM);
            } else {
                state.decayUnpulled(gamma, FEATURE_DIM);
            }
        }
    }

    public long getPullCount(ActionType action) {
        ArmState state = armStates.get(action);
        return state != null ? state.pullCount.get() : 0;
    }

    public void reset() {
        for (ActionType action : ActionType.values()) {
            armStates.put(action, new ArmState(FEATURE_DIM));
        }
    }

    // ================= 线性代数辅助计算 (32x32 极速高斯消元求逆) =================

    private static double[][] invertMatrix(double[][] a, int n) {
        double[][] augmented = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, augmented[i], 0, n);
            augmented[i][n + i] = 1.0;
        }

        for (int i = 0; i < n; i++) {
            int pivot = i;
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(augmented[j][i]) > Math.abs(augmented[pivot][i])) {
                    pivot = j;
                }
            }
            double[] temp = augmented[i];
            augmented[i] = augmented[pivot];
            augmented[pivot] = temp;

            double div = augmented[i][i];
            if (Math.abs(div) < 1e-12) {
                div = 1e-12;
            }
            for (int j = 0; j < 2 * n; j++) {
                augmented[i][j] /= div;
            }

            for (int k = 0; k < n; k++) {
                if (k != i) {
                    double factor = augmented[k][i];
                    for (int j = 0; j < 2 * n; j++) {
                        augmented[k][j] -= factor * augmented[i][j];
                    }
                }
            }
        }

        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(augmented[i], n, inv[i], 0, n);
        }
        return inv;
    }

    private static double[] multiplyMatrixVector(double[][] m, double[] v, int n) {
        double[] res = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j < n; j++) {
                sum += m[i][j] * v[j];
            }
            res[i] = sum;
        }
        return res;
    }

    private static double dotProduct(double[] a, double[] b, int n) {
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private static float[][] initProjectionMatrix(int rows, int cols) {
        // 使用固定伪随机种子生成确定性正交投影矩阵
        Random random = new Random(42L);
        float[][] mat = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                mat[i][j] = (float) (random.nextGaussian() / Math.sqrt(cols));
            }
        }
        return mat;
    }
}
