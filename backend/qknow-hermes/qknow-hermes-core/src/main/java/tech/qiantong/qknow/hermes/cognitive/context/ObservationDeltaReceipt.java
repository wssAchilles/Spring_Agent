package tech.qiantong.qknow.hermes.cognitive.context;

/**
 * 前缀守恒增量观测去重不可变凭单（Phase 11.2 IDG-CO，引理 2.1 & 定理 2）。
 */
public record ObservationDeltaReceipt(
        String requestId,
        int stepIndex,
        String toolName,
        int rawChars,
        int emittedChars,
        int savedChars,
        int deduplicatedBlocks,
        int suppressedRepeatedMetaSections,
        boolean prefixMutationOccurred,
        long timestampMs
) {
    public ObservationDeltaReceipt {
        requestId = (requestId == null || requestId.isBlank()) ? "req-idg-co" : requestId;
        toolName = toolName != null ? toolName : "";
        rawChars = Math.max(0, rawChars);
        emittedChars = Math.max(0, emittedChars);
        savedChars = Math.max(0, savedChars);
        deduplicatedBlocks = Math.max(0, deduplicatedBlocks);
        suppressedRepeatedMetaSections = Math.max(0, suppressedRepeatedMetaSections);
    }
}
