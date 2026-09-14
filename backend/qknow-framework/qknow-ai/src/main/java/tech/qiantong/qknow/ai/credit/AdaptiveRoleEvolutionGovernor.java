package tech.qiantong.qknow.ai.credit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自适应角色分化复制子动力学演化器 (Adaptive Role Evolution Governor)
 * 管理 RETRIEVER / REASONER / CODER / REVIEWER 四大生态位配额，
 * 基于离散化复制子方程自组织更新，引入 [0.10, 0.50] 有界单纯形投影杜绝死锁并收敛至 ESS
 */
@Component
public class AdaptiveRoleEvolutionGovernor {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveRoleEvolutionGovernor.class);

    // 四大生态位标准角色标识
    public static final String ROLE_RETRIEVER = "RETRIEVER";
    public static final String ROLE_REASONER = "REASONER";
    public static final String ROLE_CODER = "CODER";
    public static final String ROLE_REVIEWER = "REVIEWER";

    public static final List<String> STANDARD_ROLES = List.of(
            ROLE_RETRIEVER, ROLE_REASONER, ROLE_CODER, ROLE_REVIEWER
    );

    /** 学习率 / 动力学更新步长 eta */
    public static final double DEFAULT_LEARNING_RATE = 0.20;

    /** 生态位保底最小配额 x_min = 10% (杜绝角色灭绝) */
    public static final double MIN_ROLE_QUOTA = 0.10;

    /** 生态位最大单一垄断配额 x_max = 50% */
    public static final double MAX_ROLE_QUOTA = 0.50;

    private final double learningRate;
    private final ConcurrentHashMap<String, Double> roleQuotas = new ConcurrentHashMap<>();

    public AdaptiveRoleEvolutionGovernor() {
        this(DEFAULT_LEARNING_RATE);
    }

    public AdaptiveRoleEvolutionGovernor(double learningRate) {
        this.learningRate = learningRate;
        resetToEqualQuotas();
    }

    /**
     * 重置为初始均等分配状态 (各 25%)
     */
    public synchronized void resetToEqualQuotas() {
        double initialShare = 1.0 / STANDARD_ROLES.size();
        for (String role : STANDARD_ROLES) {
            roleQuotas.put(role, initialShare);
        }
    }

    /**
     * 根据角色适应度向量执行一轮离散化复制子动力学演化:
     * x_k^{(t+1)} = x_k^{(t)} + \eta * x_k^{(t)} * (f_k - \bar{f})
     * 并应用 [0.10, 0.50] 有界单纯形投影
     * @param roleFitness 各角色当前适应度得分 (f_k >= 0)
     * @return 演化后的下一期生态位配额字典
     */
    public synchronized Map<String, Double> evolve(Map<String, Double> roleFitness) {
        if (roleFitness == null || roleFitness.isEmpty()) {
            return getRoleQuotas();
        }

        // 1. 确保所有标准角色都有适应度
        Map<String, Double> safeFitness = new LinkedHashMap<>();
        for (String role : STANDARD_ROLES) {
            safeFitness.put(role, Math.max(0.0, roleFitness.getOrDefault(role, 0.25)));
        }

        // 2. 计算种群加权平均适应度 \bar{f} = \sum x_k * f_k
        double averageFitness = 0.0;
        for (String role : STANDARD_ROLES) {
            double x_k = roleQuotas.getOrDefault(role, 0.25);
            double f_k = safeFitness.get(role);
            averageFitness += x_k * f_k;
        }

        // 3. 欧拉离散化复制子更新增量
        Map<String, Double> tentativeQuotas = new LinkedHashMap<>();
        for (String role : STANDARD_ROLES) {
            double x_k = roleQuotas.getOrDefault(role, 0.25);
            double f_k = safeFitness.get(role);

            double dx = learningRate * x_k * (f_k - averageFitness);
            double updated = Math.max(0.01, x_k + dx);
            tentativeQuotas.put(role, updated);
        }

        // 4. 有界单纯形投影: 严格满足 [0.10, 0.50] 且 \sum x_k = 1.0
        Map<String, Double> projected = projectOntoBoundedSimplex(tentativeQuotas);

        for (Map.Entry<String, Double> entry : projected.entrySet()) {
            roleQuotas.put(entry.getKey(), entry.getValue());
        }

        return Collections.unmodifiableMap(projected);
    }

    /**
     * 将非负权重向量投影至有界单纯形: x_k \in [0.10, 0.50] 且 \sum x_k = 1.0
     */
    private Map<String, Double> projectOntoBoundedSimplex(Map<String, Double> rawWeights) {
        Map<String, Double> current = new LinkedHashMap<>(rawWeights);
        Set<String> fixed = new HashSet<>();

        // 最多循环 4 轮锁定触及边界的角色
        for (int iter = 0; iter < STANDARD_ROLES.size(); iter++) {
            double fixedSum = 0.0;
            for (String r : fixed) {
                fixedSum += current.get(r);
            }
            double remainingSum = 1.0 - fixedSum;

            double freeRawSum = 0.0;
            for (String r : STANDARD_ROLES) {
                if (!fixed.contains(r)) {
                    freeRawSum += current.get(r);
                }
            }

            boolean newlyFixed = false;
            for (String r : STANDARD_ROLES) {
                if (!fixed.contains(r)) {
                    double allocated = freeRawSum > 1e-9 ? (current.get(r) / freeRawSum) * remainingSum : remainingSum / (STANDARD_ROLES.size() - fixed.size());
                    if (allocated > MAX_ROLE_QUOTA) {
                        current.put(r, MAX_ROLE_QUOTA);
                        fixed.add(r);
                        newlyFixed = true;
                    } else if (allocated < MIN_ROLE_QUOTA) {
                        current.put(r, MIN_ROLE_QUOTA);
                        fixed.add(r);
                        newlyFixed = true;
                    } else {
                        current.put(r, allocated);
                    }
                }
            }

            if (!newlyFixed) {
                break;
            }
        }

        // 精度规整四位小数
        double sum = 0.0;
        Map<String, Double> rounded = new LinkedHashMap<>();
        for (String role : STANDARD_ROLES) {
            double val = Math.min(MAX_ROLE_QUOTA, Math.max(MIN_ROLE_QUOTA, current.get(role)));
            double r = Math.round(val * 10000.0) / 10000.0;
            rounded.put(role, r);
            sum += r;
        }

        double diff = 1.0 - sum;
        if (Math.abs(diff) > 1e-6) {
            // 挑选一个未触碰极限边界的角色吸收微小精度舍入
            for (String role : STANDARD_ROLES) {
                double v = rounded.get(role);
                if (v + diff >= MIN_ROLE_QUOTA && v + diff <= MAX_ROLE_QUOTA) {
                    rounded.put(role, Math.round((v + diff) * 10000.0) / 10000.0);
                    break;
                }
            }
        }

        return rounded;
    }

    public Map<String, Double> getRoleQuotas() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(roleQuotas));
    }

    public double getQuota(String role) {
        return roleQuotas.getOrDefault(role, 0.25);
    }
}
