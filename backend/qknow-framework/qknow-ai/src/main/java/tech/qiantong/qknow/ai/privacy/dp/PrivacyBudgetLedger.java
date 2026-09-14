package tech.qiantong.qknow.ai.privacy.dp;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 租户级差分隐私预算账本与断路熔断器 (PrivacyBudgetLedger)
 *
 * 基于 Advanced Composition Theorem 累积隐私预算消耗，
 * 采用无锁 CAS 原子状态更新；预算耗尽时自动触发 Circuit Breaker 阻断高频探针攻击。
 *
 * @author qknow
 */
@Slf4j
@Component
public class PrivacyBudgetLedger {

    @Value("${qknow.privacy.budget.default-tenant-quota:500.0}")
    private double defaultTenantQuota = 500.0;

    @Value("${qknow.privacy.budget.delta-prime:1e-5}")
    private double deltaPrime = 1e-5;

    private final Map<String, AtomicReference<TenantBudgetState>> tenantLedgers = new ConcurrentHashMap<>();

    public enum CircuitState {
        CLOSED,     // 熔断器关闭：正常放行并消耗预算
        OPEN,       // 熔断器打开：预算耗尽，全量拦截
        HALF_OPEN   // 半开试探：周期重置期
    }

    @Getter
    @ToString
    public static class TenantBudgetState {
        private final String tenantId;
        private final int queryCount;              // 已执行查询次数 k
        private final double totalEpsilonSpent;    // 高级组合定理核算的总消耗 epsilon_g
        private final double maxBudgetQuota;       // 最大预算配额
        private final CircuitState circuitState;   // 熔断器状态
        private final long lastUpdatedTime;        // 最后核销时间

        public TenantBudgetState(String tenantId, int queryCount, double totalEpsilonSpent,
                                 double maxBudgetQuota, CircuitState circuitState, long lastUpdatedTime) {
            this.tenantId = tenantId;
            this.queryCount = queryCount;
            this.totalEpsilonSpent = totalEpsilonSpent;
            this.maxBudgetQuota = maxBudgetQuota;
            this.circuitState = circuitState;
            this.lastUpdatedTime = lastUpdatedTime;
        }
    }

    /**
     * 校验并核销租户单次差分隐私预算 (CAS 无锁保证)
     *
     * @param tenantId 租户标识
     * @param level    单次查询请求的隐私等级
     * @throws IllegalStateException 若预算耗尽熔断器开启
     */
    public void recordAndVerifyBudgetConsumption(String tenantId, DifferentialPrivacyScorer.PrivacyLevel level) {
        if (level == DifferentialPrivacyScorer.PrivacyLevel.PUBLIC) {
            return;
        }
        recordAndVerifyBudgetConsumption(tenantId, level.getEpsilon());
    }

    /**
     * 针对指定 epsilon 进行核销
     */
    public void recordAndVerifyBudgetConsumption(String tenantId, double singleQueryEpsilon) {
        AtomicReference<TenantBudgetState> ref = tenantLedgers.computeIfAbsent(
                tenantId,
                k -> new AtomicReference<>(new TenantBudgetState(
                        tenantId, 0, 0.0, defaultTenantQuota, CircuitState.CLOSED, System.currentTimeMillis()))
        );

        while (true) {
            TenantBudgetState current = ref.get();
            if (current.getCircuitState() == CircuitState.OPEN) {
                log.warn("[PrivacyBudget] 租户 {} 差分隐私预算已耗尽，断路熔断器 OPEN 拦截请求", tenantId);
                throw new IllegalStateException("租户 " + tenantId + " 差分隐私预算已耗尽，已触发熔断保护 (Circuit Breaker OPEN)");
            }

            int newCount = current.getQueryCount() + 1;
            // 高级组合定理累积核算公式:
            // epsilon_g = sqrt(2 * k * ln(1 / delta')) * epsilon_0 + k * epsilon_0 * (exp(epsilon_0) - 1)
            double newTotalEpsilon = calculateAdvancedComposition(newCount, singleQueryEpsilon, deltaPrime);

            CircuitState nextState = (newTotalEpsilon >= current.getMaxBudgetQuota())
                    ? CircuitState.OPEN
                    : CircuitState.CLOSED;

            TenantBudgetState updated = new TenantBudgetState(
                    tenantId, newCount, newTotalEpsilon, current.getMaxBudgetQuota(), nextState, System.currentTimeMillis()
            );

            if (ref.compareAndSet(current, updated)) {
                if (nextState == CircuitState.OPEN) {
                    log.warn("[PrivacyBudget] 租户 {} 消耗累积达到 {}，超过阈值 {}，熔断器自动触发 OPEN",
                            tenantId, newTotalEpsilon, current.getMaxBudgetQuota());
                }
                break;
            }
        }
    }

    /**
     * 高级组合定理计算累计消耗 epsilon_g (融合基础组合定理以保证数值紧致与稳定性)
     */
    public double calculateAdvancedComposition(int k, double epsilon0, double deltaP) {
        if (k <= 0) {
            return 0.0;
        }
        double basicEpsilon = k * epsilon0;
        double term1 = Math.sqrt(2.0 * k * Math.log(1.0 / deltaP)) * epsilon0;
        double term2 = k * epsilon0 * (Math.exp(epsilon0) - 1.0);
        double advancedEpsilon = term1 + term2;
        return Math.min(basicEpsilon, advancedEpsilon);
    }

    /**
     * 查询租户当前预算状态
     */
    public TenantBudgetState getTenantBudgetState(String tenantId) {
        AtomicReference<TenantBudgetState> ref = tenantLedgers.get(tenantId);
        if (ref == null) {
            return new TenantBudgetState(tenantId, 0, 0.0, defaultTenantQuota, CircuitState.CLOSED, System.currentTimeMillis());
        }
        return ref.get();
    }

    /**
     * 设置租户最大配额
     */
    public void setTenantQuota(String tenantId, double quota) {
        AtomicReference<TenantBudgetState> ref = tenantLedgers.computeIfAbsent(
                tenantId,
                k -> new AtomicReference<>(new TenantBudgetState(
                        tenantId, 0, 0.0, quota, CircuitState.CLOSED, System.currentTimeMillis()))
        );
        ref.updateAndGet(curr -> new TenantBudgetState(
                curr.getTenantId(), curr.getQueryCount(), curr.getTotalEpsilonSpent(), quota,
                curr.getTotalEpsilonSpent() >= quota ? CircuitState.OPEN : CircuitState.CLOSED,
                System.currentTimeMillis()
        ));
    }

    /**
     * 重置租户预算状态（管理员或滑动窗口周期）
     */
    public void resetTenantBudget(String tenantId) {
        AtomicReference<TenantBudgetState> ref = tenantLedgers.get(tenantId);
        if (ref != null) {
            ref.set(new TenantBudgetState(
                    tenantId, 0, 0.0, defaultTenantQuota, CircuitState.CLOSED, System.currentTimeMillis()
            ));
        }
    }
}
