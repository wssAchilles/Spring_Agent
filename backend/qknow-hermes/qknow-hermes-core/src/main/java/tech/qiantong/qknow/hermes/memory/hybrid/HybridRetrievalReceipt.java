package tech.qiantong.qknow.hermes.memory.hybrid;

/**
 * BM25-超球面混合检索不可变审验凭单（Phase 11.3 BPR-LM）。
 */
public record HybridRetrievalReceipt(
        String requestId,
        String query,
        int totalIndexedSlices,
        int returnedSlices,
        double topBm25Score,
        double topCosineScore,
        long latencyUs,
        long timestampMs
) {
    public HybridRetrievalReceipt {
        requestId = (requestId == null || requestId.isBlank()) ? "req-hybrid" : requestId;
        query = query != null ? query : "";
        totalIndexedSlices = Math.max(0, totalIndexedSlices);
        returnedSlices = Math.max(0, returnedSlices);
        latencyUs = Math.max(0L, latencyUs);
    }
}
