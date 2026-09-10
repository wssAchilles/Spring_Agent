package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DeterministicRerankerProvider;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostFusionFilterH7Test {

    private static RagRerankService service(boolean postFusionFilterEnabled) {
        RagRerankService service = new RagRerankService();
        ReflectionTestUtils.setField(service, "postFusionFilterEnabled", postFusionFilterEnabled);
        ReflectionTestUtils.setField(service, "identifierConsistencyEnabled", false);
        ReflectionTestUtils.setField(service, "rerankerProviders", List.of());
        ReflectionTestUtils.setField(service, "deterministicRerankerProvider", new DeterministicRerankerProvider());
        var colbert = mock(tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer.class);
        var cfg = new tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer.ColbertConfig();
        when(colbert.getConfig()).thenReturn(cfg);
        when(colbert.isRealEmbeddingConfigured()).thenReturn(true);
        when(colbert.rerank(any(), any(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        ReflectionTestUtils.setField(service, "colbertScorer", colbert);
        return service;
    }

    @Test
    @DisplayName("H7 默认关闭：低分无关键词候选不被过滤")
    void defaultOffKeepsAllCandidates() {
        RagRerankService service = service(false);
        List<RetrievalResult> candidates = new ArrayList<>();
        candidates.add(RetrievalResult.builder().segmentId(1L).documentName("a")
                .content("完全无关内容").score(0.01).source("rrf").build());
        candidates.add(RetrievalResult.builder().segmentId(2L).documentName("b")
                .content("包含目标词 人工智能").score(0.02).source("rrf").build());

        List<RetrievalResult> out = service.rerank("人工智能", candidates, new QueryIntent(), 10, null, null);
        assertEquals(2, out.size());
    }

    @Test
    @DisplayName("filterIrrelevant 关闭时直接返回原列表")
    void filterIrrelevantDisabledReturnsOriginal() {
        RagRerankService service = service(false);
        List<RetrievalResult> candidates = List.of(
                RetrievalResult.builder().segmentId(1L).content("noise").score(0.0).build());
        @SuppressWarnings("unchecked")
        List<RetrievalResult> out = (List<RetrievalResult>) ReflectionTestUtils.invokeMethod(
                service, "filterIrrelevant", "q", candidates, new QueryIntent());
        assertEquals(1, out.size());
    }
}
