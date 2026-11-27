package tech.qiantong.qknow.hermes.memory.hybrid;

import java.util.Objects;

/**
 * BM25-超球面-PPR 混合检出分片得分明细（保留精确分片坐标，绝不退化整篇扫描）。
 */
public record HybridScoredChunk(
        HybridChunkEntry entry,
        double bm25Score,
        double cosineScore,
        double pprScore,
        double fusedRrfScore,
        int bm25Rank,
        int cosineRank
) {
    public HybridScoredChunk {
        entry = Objects.requireNonNull(entry, "HybridChunkEntry 不能为空");
    }
}
