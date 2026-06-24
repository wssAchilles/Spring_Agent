package tech.qiantong.qknow.module.kmc.service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.*;

@Slf4j
@Component
public class CandidateFusionService {

    private static final int RRF_K = 60;

    public List<RetrievalResult> fuse(List<List<RetrievalResult>> resultLists) {
        if (resultLists == null || resultLists.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, RetrievalResult> bestBySegment = new LinkedHashMap<>();
        Map<Long, Double> rrfScores = new HashMap<>();

        for (List<RetrievalResult> results : resultLists) {
            if (results == null || results.isEmpty()) {
                continue;
            }
            for (int rank = 0; rank < results.size(); rank++) {
                RetrievalResult result = results.get(rank);
                Long segmentId = result.getSegmentId();
                if (segmentId == null) {
                    continue;
                }

                double rrfIncrement = 1.0 / (RRF_K + rank + 1);
                rrfScores.merge(segmentId, rrfIncrement, Double::sum);

                RetrievalResult existing = bestBySegment.get(segmentId);
                if (existing == null || result.getScore() > existing.getScore()) {
                    bestBySegment.put(segmentId, result);
                }
            }
        }

        List<RetrievalResult> fused = new ArrayList<>(bestBySegment.size());
        for (Map.Entry<Long, RetrievalResult> entry : bestBySegment.entrySet()) {
            RetrievalResult copy = RetrievalResult.builder()
                    .segmentId(entry.getValue().getSegmentId())
                    .qmSegmentId(entry.getValue().getQmSegmentId())
                    .parentSegmentId(entry.getValue().getParentSegmentId())
                    .documentId(entry.getValue().getDocumentId())
                    .documentName(entry.getValue().getDocumentName())
                    .content(entry.getValue().getContent())
                    .answer(entry.getValue().getAnswer())
                    .score(rrfScores.getOrDefault(entry.getKey(), 0.0))
                    .source(entry.getValue().getSource())
                    .metadata(entry.getValue().getMetadata())
                    .build();
            fused.add(copy);
        }

        fused.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return fused;
    }
}
