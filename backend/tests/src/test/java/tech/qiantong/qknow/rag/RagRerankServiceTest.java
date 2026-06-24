package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DeterministicRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankRequestContext;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankerProvider;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RagRerankService 重排序测试")
class RagRerankServiceTest {

    @Test
    @DisplayName("DashScope provider 失败时应降级到 deterministic")
    void rerank_dashscopeFails_fallsBackToDeterministic() {
        RerankerProvider failingProvider = new RerankerProvider() {
            @Override
            public String name() { return "dashscope"; }

            @Override
            public boolean supports(RerankRequestContext context) { return true; }

            @Override
            public List<RetrievalResult> rerank(RerankRequestContext context, List<RetrievalResult> candidates,
                                                QueryIntent queryIntent, int topK) {
                throw new RuntimeException("DashScope API unavailable");
            }
        };

        DeterministicRerankerProvider deterministicProvider = new DeterministicRerankerProvider();

        // Use reflection to set providers
        tech.qiantong.qknow.module.kmc.service.rag.RagRerankService service =
                new tech.qiantong.qknow.module.kmc.service.rag.RagRerankService();
        try {
            var providersField = tech.qiantong.qknow.module.kmc.service.rag.RagRerankService.class.getDeclaredField("rerankerProviders");
            providersField.setAccessible(true);
            providersField.set(service, List.of(failingProvider));

            var detField = tech.qiantong.qknow.module.kmc.service.rag.RagRerankService.class.getDeclaredField("deterministicRerankerProvider");
            detField.setAccessible(true);
            detField.set(service, deterministicProvider);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<RetrievalResult> candidates = List.of(
                RetrievalResult.builder().segmentId(1L).documentName("Day01.txt").content("test").score(0.5).build()
        );
        QueryIntent intent = QueryIntent.builder().build();

        List<RetrievalResult> result = service.rerank("query", candidates, intent, 5, null, null);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("deterministic 评分: dayNo 匹配 +3")
    void deterministicRerank_dayNoMatch_bonus3() {
        DeterministicRerankerProvider provider = new DeterministicRerankerProvider();

        List<RetrievalResult> candidates = new ArrayList<>(List.of(
                RetrievalResult.builder().segmentId(1L).documentName("Day05_工作日志.txt").content("内容").score(1.0).build(),
                RetrievalResult.builder().segmentId(2L).documentName("Day10_总结.txt").content("内容").score(1.0).build()
        ));

        QueryIntent intent = QueryIntent.builder().dayNo(5).build();
        RerankRequestContext context = RerankRequestContext.builder().query("Day05 相关").build();

        List<RetrievalResult> result = provider.rerank(context, candidates, intent, 10);

        assertEquals(1L, result.get(0).getSegmentId(), "Day05 文档应排第一 (1.0 + 3.0 = 4.0)");
        assertEquals(4.0, result.get(0).getScore(), 0.01);
    }

    @Test
    @DisplayName("deterministic 评分: docName 匹配 +2")
    void deterministicRerank_docNameMatch_bonus2() {
        DeterministicRerankerProvider provider = new DeterministicRerankerProvider();

        List<RetrievalResult> candidates = new ArrayList<>(List.of(
                RetrievalResult.builder().segmentId(1L).documentName("架构设计文档.txt").content("内容").score(1.0).build(),
                RetrievalResult.builder().segmentId(2L).documentName("其他文档.txt").content("内容").score(1.0).build()
        ));

        QueryIntent intent = QueryIntent.builder().docName("架构设计").build();
        RerankRequestContext context = RerankRequestContext.builder().query("架构").build();

        List<RetrievalResult> result = provider.rerank(context, candidates, intent, 10);

        assertEquals(1L, result.get(0).getSegmentId());
        assertEquals(3.0, result.get(0).getScore(), 0.01);
    }

    @Test
    @DisplayName("deterministic 评分: keyword 匹配 +1 per keyword")
    void deterministicRerank_keywordMatch_bonus1PerKeyword() {
        DeterministicRerankerProvider provider = new DeterministicRerankerProvider();

        List<RetrievalResult> candidates = new ArrayList<>(List.of(
                RetrievalResult.builder().segmentId(1L).documentName("doc.txt").content("包含 RAG 和 Agent 的内容").score(1.0).build(),
                RetrievalResult.builder().segmentId(2L).documentName("doc.txt").content("普通内容").score(1.0).build()
        ));

        QueryIntent intent = QueryIntent.builder().keywords(List.of("RAG", "Agent")).build();
        RerankRequestContext context = RerankRequestContext.builder().query("RAG Agent").build();

        List<RetrievalResult> result = provider.rerank(context, candidates, intent, 10);

        assertEquals(1L, result.get(0).getSegmentId());
        assertEquals(3.0, result.get(0).getScore(), 0.01);
    }

    @Test
    @DisplayName("空候选列表应返回空结果")
    void rerank_emptyCandidates_returnsEmpty() {
        DeterministicRerankerProvider provider = new DeterministicRerankerProvider();
        RerankRequestContext context = RerankRequestContext.builder().query("query").build();
        QueryIntent intent = QueryIntent.builder().build();

        List<RetrievalResult> result = provider.rerank(context, new ArrayList<>(), intent, 10);

        assertTrue(result.isEmpty());
    }
}
