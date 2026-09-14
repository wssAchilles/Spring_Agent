package tech.qiantong.qknow.ai.gateway.model;

import lombok.Builder;
import lombok.Data;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 28: API Key 轮询池单条密钥元数据
 */
@Data
@Builder
public class ApiKeyEntry {
    private String keyId;
    private String secretKey;
    private int weight;

    @Builder.Default
    private AtomicInteger currentWeight = new AtomicInteger(0);

    @Builder.Default
    private AtomicInteger effectiveWeight = new AtomicInteger(1);

    @Builder.Default
    private AtomicLong cooldownExpiryMillis = new AtomicLong(0L);

    @Builder.Default
    private AtomicBoolean active = new AtomicBoolean(true);

    /**
     * 判断该 Key 当前是否可用 (未冷却且处于激活状态)
     */
    public boolean isAvailable(long nowMillis) {
        return active.get() && nowMillis >= cooldownExpiryMillis.get();
    }

    /**
     * 标记该 Key 进入冷却隔离 (例如上游返回 429 或 401)
     */
    public void markCooldown(long cooldownDurationMillis) {
        this.cooldownExpiryMillis.set(System.currentTimeMillis() + cooldownDurationMillis);
    }
}
