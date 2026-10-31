package tech.qiantong.qknow.hermes.agent.harness.ppc;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.RagAttributionSummary;
import tech.qiantong.qknow.hermes.agent.harness.UsageStatus;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;

public record PpcProbeResult(
        String candidateId,
        String caseId,
        CapabilityTag effectiveCapabilityTag,
        Instant executedAt,
        boolean peakPricingPeriod,
        HarnessEvidenceRecord.ExecutionMode executionMode,
        UsageStatus usageStatus,
        boolean costKnown,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long cacheHitTokens,
        long reasoningTokens,
        double actualCostCny,
        int meteredCallCount,
        long durationMs,
        boolean qualityPass,
        boolean guardBlocked,
        boolean cycleDetected,
        List<String> injectedRagChunkIds,
        RagAttributionSummary ragAttribution,
        boolean ragScoreEnabled,
        HarnessEvidenceRecord evidence
) {
    public PpcProbeResult {
        if (candidateId == null || candidateId.isBlank() || caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("candidateId and caseId are required");
        }
        Objects.requireNonNull(effectiveCapabilityTag, "effectiveCapabilityTag");
        Objects.requireNonNull(executedAt, "executedAt");
        Objects.requireNonNull(executionMode, "executionMode");
        Objects.requireNonNull(usageStatus, "usageStatus");
        injectedRagChunkIds = List.copyOf(injectedRagChunkIds);
        Objects.requireNonNull(ragAttribution, "ragAttribution");
        Objects.requireNonNull(evidence, "evidence");
        if (ragScoreEnabled) {
            throw new IllegalArgumentException("ragScore is disabled in Stage 9");
        }
    }
}
