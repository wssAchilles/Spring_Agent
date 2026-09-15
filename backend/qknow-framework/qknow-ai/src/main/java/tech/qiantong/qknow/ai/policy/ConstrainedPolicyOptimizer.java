package tech.qiantong.qknow.ai.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全约束策略优化器 (基于受约束 MDP 拉格朗日对偶更新与控制屏障 CBF, 定理 1.3)
 */
@Component
public class ConstrainedPolicyOptimizer {

    private static final Logger log = LoggerFactory.getLogger(ConstrainedPolicyOptimizer.class);

    // 三大硬性约束限额 (SLA 上限)
    public static final String COST_TOKEN_BUDGET = "token_cost";
    public static final String COST_LATENCY_MS = "latency_ms";
    public static final String COST_SECURITY_VIOLATION = "security_violation";

    public static final double LIMIT_TOKEN_BUDGET = 2000.0;
    public static final double LIMIT_LATENCY_MS = 1500.0;
    public static final double LIMIT_SECURITY_VIOLATION = 0.0;

    // 安全回退保底动作
    public static final String SAFE_FALLBACK_ACTION = "SAFE_FALLBACK";

    // 拉格朗日乘子阻尼上限与更新步长
    private static final double MAX_LAMBDA = 100.0;
    private static final double DUAL_STEP_SIZE = 0.1;

    // 线程安全的乘子状态
    private final ConcurrentHashMap<String, Double> lambdas = new ConcurrentHashMap<>();

    public ConstrainedPolicyOptimizer() {
        lambdas.put(COST_TOKEN_BUDGET, 0.0);
        lambdas.put(COST_LATENCY_MS, 0.0);
        lambdas.put(COST_SECURITY_VIOLATION, 0.0);
    }

    /**
     * 拉格朗日乘子对偶更新 (定理 1.3)
     * lambda_k^(t+1) = [lambda_k^(t) + eta * (c_k - d_k)]_+
     */
    public synchronized void updateMultipliers(Map<String, Double> observedCosts) {
        if (observedCosts == null) return;

        updateSingleMultiplier(COST_TOKEN_BUDGET, observedCosts.getOrDefault(COST_TOKEN_BUDGET, 0.0), LIMIT_TOKEN_BUDGET);
        updateSingleMultiplier(COST_LATENCY_MS, observedCosts.getOrDefault(COST_LATENCY_MS, 0.0), LIMIT_LATENCY_MS);
        updateSingleMultiplier(COST_SECURITY_VIOLATION, observedCosts.getOrDefault(COST_SECURITY_VIOLATION, 0.0), LIMIT_SECURITY_VIOLATION);
    }

    private void updateSingleMultiplier(String costKey, double observed, double limit) {
        double current = lambdas.getOrDefault(costKey, 0.0);
        double diff = observed - limit;
        double next = Math.max(0.0, current + DUAL_STEP_SIZE * diff);
        // 阻尼截断防死锁
        next = Math.min(MAX_LAMBDA, next);
        lambdas.put(costKey, next);
    }

    /**
     * 控制屏障函数 (CBF) 过滤算子：高危动作 100% 物理硬拦截
     */
    public String filterSafeAction(String candidateAction, Map<String, Double> estimatedCosts) {
        if (estimatedCosts != null) {
            double secViol = estimatedCosts.getOrDefault(COST_SECURITY_VIOLATION, 0.0);
            if (secViol > LIMIT_SECURITY_VIOLATION) {
                log.warn("安全控制屏障硬拦截违规动作: {}, 违规指标: {}", candidateAction, secViol);
                return SAFE_FALLBACK_ACTION;
            }
        }
        return candidateAction;
    }

    /**
     * 判定整体策略是否满足所有安全约束
     */
    public boolean isPolicySafe(Map<String, Double> averageCosts) {
        if (averageCosts == null) return true;
        if (averageCosts.getOrDefault(COST_SECURITY_VIOLATION, 0.0) > LIMIT_SECURITY_VIOLATION) return false;
        if (averageCosts.getOrDefault(COST_TOKEN_BUDGET, 0.0) > LIMIT_TOKEN_BUDGET) return false;
        if (averageCosts.getOrDefault(COST_LATENCY_MS, 0.0) > LIMIT_LATENCY_MS) return false;
        return true;
    }

    public Map<String, Double> getMultipliers() {
        return Collections.unmodifiableMap(new HashMap<>(lambdas));
    }

    public void reset() {
        lambdas.put(COST_TOKEN_BUDGET, 0.0);
        lambdas.put(COST_LATENCY_MS, 0.0);
        lambdas.put(COST_SECURITY_VIOLATION, 0.0);
    }
}
