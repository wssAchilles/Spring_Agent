package tech.qiantong.qknow.hermes.search.speculative;

import java.util.List;
import java.util.Objects;

/**
 * 推测性 RAG 候选子集并行草稿不可变条目
 * 遵循 Java 21 Record 纯不可变对象规范
 */
public record SpeculativeDraftRecord(
        String partitionId,
        List<String> documentIds,
        String draftHypothesis,
        String evidenceSummary,
        float localConfidence
) {
    public SpeculativeDraftRecord {
        Objects.requireNonNull(partitionId, "partitionId 不能为空");
        Objects.requireNonNull(documentIds, "documentIds 不能为空");
        documentIds = List.copyOf(documentIds);
        Objects.requireNonNull(draftHypothesis, "draftHypothesis 不能为空");
        Objects.requireNonNull(evidenceSummary, "evidenceSummary 不能为空");
    }

    public static SpeculativeDraftRecord of(
            String partitionId,
            List<String> documentIds,
            String draftHypothesis,
            String evidenceSummary,
            float localConfidence
    ) {
        return new SpeculativeDraftRecord(
                partitionId, documentIds, draftHypothesis, evidenceSummary, localConfidence
        );
    }
}
