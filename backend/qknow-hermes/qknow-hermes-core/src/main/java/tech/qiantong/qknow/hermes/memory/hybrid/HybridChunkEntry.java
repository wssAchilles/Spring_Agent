package tech.qiantong.qknow.hermes.memory.hybrid;

/**
 * BM25-超球面混合分片索引不可变分片实体（Phase 11.3 BPR-LM，定理 3）。
 */
public record HybridChunkEntry(
        String chunkId,
        String docTitle,
        int sliceIndex,
        int startOffset,
        int endOffset,
        String content,
        String timestampStr,
        float[] unitVector1024
) {
    public HybridChunkEntry {
        chunkId = (chunkId == null || chunkId.isBlank()) ? "chunk-0" : chunkId;
        docTitle = docTitle != null ? docTitle : "";
        sliceIndex = Math.max(0, sliceIndex);
        startOffset = Math.max(0, startOffset);
        endOffset = Math.max(startOffset, endOffset);
        content = content != null ? content : "";
        timestampStr = timestampStr != null ? timestampStr : "";
        unitVector1024 = unitVector1024 != null ? unitVector1024.clone() : new float[0];
    }

    @Override
    public float[] unitVector1024() {
        return unitVector1024.clone();
    }
}
