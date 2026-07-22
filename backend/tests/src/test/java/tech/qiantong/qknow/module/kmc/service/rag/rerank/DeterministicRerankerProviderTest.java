package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeterministicRerankerProviderTest {

    @Test
    void equalScoresUseAscendingNonNullSegmentIdWithNullLast() {
        DeterministicRerankerProvider provider = new DeterministicRerankerProvider();
        List<RetrievalResult> candidates = new ArrayList<>(List.of(
                result(null, 1.0),
                result(7L, 1.0),
                result(2L, 1.0)
        ));

        List<Long> segmentIds = provider.rerank(
                        RerankRequestContext.builder().query("query").build(),
                        candidates,
                        QueryIntent.builder().build(),
                        candidates.size())
                .stream()
                .map(RetrievalResult::getSegmentId)
                .toList();

        assertEquals(Arrays.asList(2L, 7L, null), segmentIds);
    }

    private static RetrievalResult result(Long segmentId, double score) {
        return RetrievalResult.builder()
                .segmentId(segmentId)
                .score(score)
                .build();
    }
}
