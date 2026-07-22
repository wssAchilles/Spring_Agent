package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DeterministicRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.LocalRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankRequestContext;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankerProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RagRerankService 重排序测试")
class RagRerankServiceTest {

    @Test
    @DisplayName("identifier consistency 默认关闭")
    void identifierConsistency_defaultsToDisabled() {
        RagRerankService service = new RagRerankService();

        assertEquals(Boolean.FALSE,
                ReflectionTestUtils.getField(service, "identifierConsistencyEnabled"));
    }

    @Test
    @DisplayName("开启后按标识符稳定前置并精确保留 deterministic 分数前缀")
    void identifierConsistency_enabled_promotesMatchesAndPreservesScoresAndDuplicates() {
        DeterministicRerankerProvider deterministic = spy(new DeterministicRerankerProvider());
        RagRerankService service = service(List.of(deterministic), deterministic, true);
        List<RetrievalResult> candidates = new ArrayList<>(List.of(
                candidate(1L, "archive-999.txt", 7.0D),
                candidate(2L, "policy-034.txt", 6.0D),
                candidate(3L, "文034.txt", 5.0D),
                candidate(2L, "topic_044.txt", 4.0D),
                candidate(4L, "topic-044.txt", 3.0D),
                candidate(5L, "policy-34.txt", 2.0D),
                candidate(6L, null, 1.0D)));

        List<RetrievalResult> result = service.rerank(
                "document id 034 and topic 044", candidates,
                QueryIntent.builder().build(), 4, null, null);

        assertEquals(List.of(2L, 2L, 4L, 1L),
                result.stream().map(RetrievalResult::getSegmentId).toList());
        assertEquals(List.of(7.0D, 6.0D, 5.0D, 4.0D),
                result.stream().map(RetrievalResult::getScore).toList());
        assertEquals("qm-2", result.get(0).getQmSegmentId());
        assertEquals("parent-2", result.get(0).getParentSegmentId());
        assertEquals(102L, result.get(0).getDocumentId());
        assertEquals("answer-2", result.get(0).getAnswer());
        assertEquals("keyword", result.get(0).getSource());
        assertEquals(Map.of("segmentId", 2L, "score", 6.0D, "source", "keyword"),
                result.get(0).getMetadata());
        assertNotSame(candidates.get(1).getMetadata(), result.get(0).getMetadata());
        verify(deterministic, times(1)).rerank(
                any(RerankRequestContext.class), anyList(), any(QueryIntent.class), eq(7));
    }

    @Test
    @DisplayName("关闭、无标识符或无匹配时保持业务 topK deterministic 路径")
    void identifierConsistency_inactivePathsPreserveBusinessTopK() {
        for (InactiveCase testCase : List.of(
                new InactiveCase(false, "document id 034", "policy-034.txt"),
                new InactiveCase(true, "2024-03-04 10% Day07", "policy-034.txt"),
                new InactiveCase(true, "document id 034", "policy-035.txt"))) {
            DeterministicRerankerProvider deterministic = spy(new DeterministicRerankerProvider());
            RagRerankService service = service(
                    List.of(deterministic), deterministic, testCase.enabled());

            List<RetrievalResult> result = service.rerank(
                    testCase.query(), new ArrayList<>(List.of(
                            candidate(1L, testCase.documentName(), 2.0D),
                            candidate(2L, "other.txt", 1.0D))),
                    QueryIntent.builder().build(), 1, null, null);

            assertEquals(List.of(1L),
                    result.stream().map(RetrievalResult::getSegmentId).toList());
            verify(deterministic, times(1)).rerank(
                    any(RerankRequestContext.class), anyList(), any(QueryIntent.class), eq(1));
        }
    }

    @Test
    @DisplayName("remote 与 local 成功路径不执行 identifier consistency")
    void identifierConsistency_remoteAndLocalSuccessBypassDeterministic() {
        RetrievalResult remoteResult = candidate(90L, "remote.txt", 9.0D);
        RerankerProvider remote = mock(RerankerProvider.class);
        when(remote.supports(any())).thenReturn(true);
        when(remote.name()).thenReturn("remote");
        when(remote.rerank(any(), anyList(), any(), anyInt())).thenReturn(List.of(remoteResult));
        DeterministicRerankerProvider remoteDeterministic = mock(DeterministicRerankerProvider.class);
        RagRerankService remoteService = service(
                List.of(remote), remoteDeterministic, true);

        assertSame(remoteResult, remoteService.rerank(
                "document id 034", List.of(candidate(1L, "policy-034.txt", 1.0D)),
                QueryIntent.builder().build(), 1, null, null).get(0));
        verifyNoInteractions(remoteDeterministic);

        RetrievalResult localResult = candidate(91L, "local.txt", 8.0D);
        LocalRerankerProvider local = mock(LocalRerankerProvider.class);
        when(local.supports(any())).thenReturn(true);
        when(local.rerank(any(), anyList(), any(), anyInt())).thenReturn(List.of(localResult));
        DeterministicRerankerProvider localDeterministic = mock(DeterministicRerankerProvider.class);
        RagRerankService localService = service(
                List.of(local), localDeterministic, true);

        assertSame(localResult, localService.rerank(
                "document id 034", List.of(candidate(1L, "policy-034.txt", 1.0D)),
                QueryIntent.builder().build(), 1, null, null).get(0));
        verifyNoInteractions(localDeterministic);
    }

    @Test
    @DisplayName("服务空输入保持空结果且不调用任何 reranker")
    void identifierConsistency_emptyInputReturnsEmpty() {
        DeterministicRerankerProvider deterministic = mock(DeterministicRerankerProvider.class);
        RagRerankService service = service(List.of(deterministic), deterministic, true);

        assertTrue(service.rerank("document id 034", List.of(),
                QueryIntent.builder().build(), 10, null, null).isEmpty());
        verifyNoInteractions(deterministic);
    }

    @Test
    @DisplayName("非空候选先经过 ColBERT 粗排")
    void rerank_nonEmptyCandidates_callsColbertScorer() {
        tech.qiantong.qknow.module.kmc.service.rag.RagRerankService service =
                new tech.qiantong.qknow.module.kmc.service.rag.RagRerankService();
        ColbertScorer colbertScorer = mock(ColbertScorer.class);
        when(colbertScorer.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        try {
            var providersField = tech.qiantong.qknow.module.kmc.service.rag.RagRerankService.class.getDeclaredField("rerankerProviders");
            providersField.setAccessible(true);
            providersField.set(service, List.of());

            var detField = tech.qiantong.qknow.module.kmc.service.rag.RagRerankService.class.getDeclaredField("deterministicRerankerProvider");
            detField.setAccessible(true);
            detField.set(service, new DeterministicRerankerProvider());

            var colbertField = tech.qiantong.qknow.module.kmc.service.rag.RagRerankService.class.getDeclaredField("colbertScorer");
            colbertField.setAccessible(true);
            colbertField.set(service, colbertScorer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        RetrievalResult candidate = RetrievalResult.builder()
                        .segmentId(1L)
                        .qmSegmentId("vec-1")
                        .parentSegmentId("parent-1")
                        .documentId(100L)
                        .documentName("knowledge.txt")
                        .content("knowledge graph entities relationships")
                        .answer("answer")
                        .score(0.5)
                        .build();
        List<RetrievalResult> candidates = List.of(candidate);

        List<RetrievalResult> result = service.rerank(
                "knowledge graph", candidates, QueryIntent.builder().build(), 2, null, null);

        verify(colbertScorer).rerank(eq("knowledge graph"), anyList(), eq(6));
        assertEquals("vec-1", result.get(0).getQmSegmentId());
        assertEquals("parent-1", result.get(0).getParentSegmentId());
        assertEquals(100L, result.get(0).getDocumentId());
        assertEquals("knowledge.txt", result.get(0).getDocumentName());
        assertEquals("answer", result.get(0).getAnswer());
    }

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

    private static RagRerankService service(
            List<RerankerProvider> providers,
            DeterministicRerankerProvider deterministic,
            boolean identifierConsistencyEnabled) {
        RagRerankService service = new RagRerankService();
        ColbertScorer colbert = mock(ColbertScorer.class);
        when(colbert.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        ReflectionTestUtils.setField(service, "rerankerProviders", providers);
        ReflectionTestUtils.setField(service, "deterministicRerankerProvider", deterministic);
        ReflectionTestUtils.setField(service, "colbertScorer", colbert);
        ReflectionTestUtils.setField(service, "identifierConsistencyEnabled",
                identifierConsistencyEnabled);
        return service;
    }

    private static RetrievalResult candidate(Long segmentId, String documentName, double score) {
        return RetrievalResult.builder()
                .segmentId(segmentId)
                .qmSegmentId("qm-" + segmentId)
                .parentSegmentId("parent-" + segmentId)
                .documentId(100L + segmentId)
                .documentName(documentName)
                .content("document topic content")
                .answer("answer-" + segmentId)
                .score(score)
                .source("keyword")
                .metadata(Map.of("ordinal", segmentId))
                .build();
    }

    private record InactiveCase(boolean enabled, String query, String documentName) {
    }

}
