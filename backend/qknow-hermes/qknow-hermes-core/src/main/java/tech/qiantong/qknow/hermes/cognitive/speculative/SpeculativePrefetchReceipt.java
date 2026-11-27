package tech.qiantong.qknow.hermes.cognitive.speculative;

/**
 * 安全格约束投机工具预取不可变凭单（Java 21 Record，对应 LG-STP）。
 */
public record SpeculativePrefetchReceipt(
        String requestId,
        String targetToolName,
        String normalizedArgsKey,
        boolean cacheHit,
        boolean rejectedBySafetyLattice,
        long savedLatencyMs,
        long timestampMs
) {
}
