package tech.qiantong.qknow.ai.gateway.model;

import lombok.Builder;
import lombok.Data;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 28: 租户在途并发控制与金融级配额账本
 */
@Data
@Builder
public class TenantQuotaLedger {
    private String tenantId;

    /**
     * 单租户在途并发数硬上限 (例如普通租户 10，VIP 租户 50)
     */
    private int maxInFlightLimit;

    /**
     * 当前正在执行的在途并发请求数
     */
    @Builder.Default
    private AtomicInteger inFlightRequests = new AtomicInteger(0);

    /**
     * 账户纳元余额 (1 元 = 10^9 纳元)
     */
    @Builder.Default
    private AtomicLong balanceNanoYuan = new AtomicLong(0L);

    /**
     * 日度已消耗 Token 计数
     */
    @Builder.Default
    private AtomicLong dailyConsumedTokens = new AtomicLong(0L);

    /**
     * 日度 Token 硬配额
     */
    private long dailyTokenQuota;

    /**
     * 账户是否被冻结或欠费封禁
     */
    @Builder.Default
    private AtomicBoolean suspended = new AtomicBoolean(false);

    /**
     * 尝试获取在途并发名额 (无锁 CAS)
     */
    public boolean tryAcquireConcurrency() {
        if (suspended.get()) {
            return false;
        }
        int current = inFlightRequests.get();
        while (current < maxInFlightLimit) {
            if (inFlightRequests.compareAndSet(current, current + 1)) {
                return true;
            }
            current = inFlightRequests.get();
        }
        return false;
    }

    /**
     * 释放一个在途并发名额
     */
    public void releaseConcurrency() {
        inFlightRequests.decrementAndGet();
    }

    /**
     * 扣减纳元费用并检查余额
     */
    public long deductNano(long costNano) {
        long newBalance = balanceNanoYuan.addAndGet(-costNano);
        if (newBalance < 0) {
            suspended.set(true);
        }
        return newBalance;
    }
}
