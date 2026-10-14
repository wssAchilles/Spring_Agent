package tech.qiantong.qknow.hermes.search.hybrid;

import java.util.Objects;

/**
 * 混合检索多路召回与重排候选条目
 * 采用 Java 21 Record 纯不可变结构构建
 */
public record HybridRetrievalCandidate(
        String docId,
        String textContent,
        float denseScore,
        int denseRank,
        float sparseScore,
        int sparseRank,
        double rrfScore,
        float finalRerankScore
) implements Comparable<HybridRetrievalCandidate> {

    public HybridRetrievalCandidate {
        Objects.requireNonNull(docId, "docId 不能为空");
        Objects.requireNonNull(textContent, "textContent 不能为空");
    }

    public static HybridRetrievalCandidate of(
            String docId,
            String textContent,
            float denseScore,
            int denseRank,
            float sparseScore,
            int sparseRank,
            double rrfScore
    ) {
        return new HybridRetrievalCandidate(
                docId, textContent, denseScore, denseRank, sparseScore, sparseRank, rrfScore, 0.0f
        );
    }

    public HybridRetrievalCandidate withFinalRerankScore(float rerankScore) {
        return new HybridRetrievalCandidate(
                docId, textContent, denseScore, denseRank, sparseScore, sparseRank, rrfScore, rerankScore
        );
    }

    @Override
    public int compareTo(HybridRetrievalCandidate other) {
        if (Float.compare(this.finalRerankScore, other.finalRerankScore) != 0) {
            return Float.compare(other.finalRerankScore, this.finalRerankScore);
        }
        return Double.compare(other.rrfScore, this.rrfScore);
    }
}
