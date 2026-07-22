package tech.qiantong.qknow.rag.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record RagEvaluationDataset(
        Map<String, CorpusSegment> corpusById,
        List<QueryCase> queries,
        Map<String, Map<String, Integer>> qrels
) {

    public RagEvaluationDataset {
        corpusById = Map.copyOf(corpusById);
        queries = List.copyOf(queries);
        Map<String, Map<String, Integer>> copiedQrels = new LinkedHashMap<>();
        qrels.forEach((queryId, grades) -> copiedQrels.put(queryId, Map.copyOf(grades)));
        qrels = Map.copyOf(copiedQrels);
    }

    public Map<String, Integer> qrelsFor(String queryId) {
        return qrels.getOrDefault(queryId, Map.of());
    }

    public record CorpusSegment(
            String segmentId,
            String documentId,
            String content,
            String parentSegmentId,
            Map<String, Object> metadata
    ) {
        public CorpusSegment {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record QueryCase(
            String id,
            String familyId,
            String query,
            String retrievalQuery,
            List<String> history,
            String language,
            Set<String> strata,
            String split,
            boolean answerable,
            String referenceAnswer,
            List<String> referenceClaims
    ) {
        public QueryCase {
            history = history == null ? List.of() : List.copyOf(history);
            strata = strata == null ? Set.of() : Set.copyOf(strata);
            referenceClaims = referenceClaims == null ? List.of() : List.copyOf(referenceClaims);
        }
    }
}
