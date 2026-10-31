package tech.qiantong.qknow.hermes.agent.harness;

import java.util.Objects;

public record HarnessUsageSnapshot(
        UsageStatus usageStatus,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long cacheHitTokens,
        long reasoningTokens,
        double actualCostCny,
        int meteredCallCount,
        int unmeteredCallCount
) {
    public HarnessUsageSnapshot {
        usageStatus = Objects.requireNonNull(usageStatus, "usageStatus");
        if (meteredCallCount <= 0 || unmeteredCallCount > 0 || totalTokens <= 0L) {
            usageStatus = UsageStatus.UNKNOWN;
        }
    }

    public static HarnessUsageSnapshot unknown() {
        return new HarnessUsageSnapshot(UsageStatus.UNKNOWN, 0L, 0L, 0L, 0L, 0L, 0.0D, 0, 0);
    }

    public boolean isKnown() {
        return usageStatus == UsageStatus.KNOWN && meteredCallCount > 0 && unmeteredCallCount == 0 && totalTokens > 0L;
    }

    public boolean isCostKnown() {
        return usageStatus == UsageStatus.KNOWN && totalTokens > 0L
                && Double.isFinite(actualCostCny) && actualCostCny > 0.0D;
    }
}
