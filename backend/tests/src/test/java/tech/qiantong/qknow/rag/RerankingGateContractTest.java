package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.service.rag.CandidateFusionService;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DeterministicRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankRequestContext;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankerProvider;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Phase 07: RRF 消融与重排动态门控契约测试")
public class RerankingGateContractTest {

    @Test
    @DisplayName("契约 1: 多路首位共识 (Top Consensus) 触发门控短路，完全跳过下游精排 Provider")
    void testGateTriggeredOnTopConsensus() {
        CandidateFusionService fusionService = new CandidateFusionService();

        // 向量路第一名是 101L，第二名是 102L
        List<RetrievalResult> vectorResults = List.of(
                createResult(101L, "向量高分文档", 0.92, "vector"),
                createResult(102L, "向量次高分文档", 0.81, "vector")
        );
        // 关键词路第一名同样是 101L，达成 Rank 1 共识
        List<RetrievalResult> keywordResults = List.of(
                createResult(101L, "关键词匹配文档", 10.0, "keyword"),
                createResult(103L, "关键词次匹配文档", 5.0, "keyword")
        );

        CandidateFusionService.FusionResult fusionResult = fusionService.fuseWithDiagnostics(
                List.of(vectorResults, keywordResults),
                List.of("vector", "keyword")
        );

        assertTrue(fusionResult.isTopConsensus(), "多路检索 Rank 1 均为 101L，应达成首位共识");
        assertEquals(101L, fusionResult.getResults().get(0).getSegmentId());

        // 构造 RagRerankService 并挂载 Mock 远程重排器
        RerankerProvider mockProvider = mock(RerankerProvider.class);
        when(mockProvider.supports(any(RerankRequestContext.class))).thenReturn(true);
        when(mockProvider.name()).thenReturn("mock-remote-reranker");

        RagRerankService rerankService = createRerankService(List.of(mockProvider), true, 0.88, 0.15);

        List<RetrievalResult> reranked = rerankService.rerank(
                "测试查询", fusionResult.getResults(), QueryIntent.builder().build(), 5, 1L, "mock-model"
        );

        assertNotNull(reranked);
        assertFalse(reranked.isEmpty());
        assertEquals(101L, reranked.get(0).getSegmentId());
        assertEquals(Boolean.TRUE, reranked.get(0).getMetadata().get("rerankGateSkipped"),
                "门控应成功标记 rerankGateSkipped=true");
        assertEquals("top_consensus", reranked.get(0).getMetadata().get("rerankGateReason"),
                "门控原因应为 top_consensus");

        // 关键断言：远程精排算子绝对未被调用（100% 旁路降本降时延）
        verify(mockProvider, never()).rerank(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("契约 2: 高置信度与高分差 (Score Margin) 触发门控短路")
    void testGateTriggeredOnHighConfidenceAndMargin() {
        RerankerProvider mockProvider = mock(RerankerProvider.class);
        when(mockProvider.supports(any(RerankRequestContext.class))).thenReturn(true);
        when(mockProvider.name()).thenReturn("mock-remote-reranker");

        RagRerankService rerankService = createRerankService(List.of(mockProvider), true, 0.88, 0.15);

        // 候选 1 分数 0.95 (>= 0.88)，候选 2 分数 0.70，分差 0.25 (>= 0.15)
        RetrievalResult top1 = createResult(201L, "高置信第一名", 0.95, "vector");
        top1.getMetadata().put("maxNormalizedScore", 0.95);
        top1.getMetadata().put("topMargin", 0.25);
        top1.getMetadata().put("topConsensus", false);

        RetrievalResult top2 = createResult(202L, "普通第二名", 0.70, "vector");

        List<RetrievalResult> candidates = List.of(top1, top2);

        List<RetrievalResult> reranked = rerankService.rerank(
                "高置信查询", candidates, QueryIntent.builder().build(), 2, 1L, "mock-model"
        );

        assertEquals(201L, reranked.get(0).getSegmentId());
        assertEquals(Boolean.TRUE, reranked.get(0).getMetadata().get("rerankGateSkipped"));
        assertEquals("high_confidence_margin", reranked.get(0).getMetadata().get("rerankGateReason"));
        verify(mockProvider, never()).rerank(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("契约 3: 候选集激烈竞争时门控放行，正常调用下游精排算子")
    void testGatePassThroughOnCompetitiveCandidates() {
        RerankerProvider mockProvider = mock(RerankerProvider.class);
        when(mockProvider.supports(any(RerankRequestContext.class))).thenReturn(true);
        when(mockProvider.name()).thenReturn("mock-remote-reranker");

        RetrievalResult expectedRerankedDoc = createResult(302L, "精排逆袭第二名", 0.99, "remote");
        when(mockProvider.rerank(any(), any(), any(), anyInt()))
                .thenReturn(List.of(expectedRerankedDoc));

        RagRerankService rerankService = createRerankService(List.of(mockProvider), true, 0.88, 0.15);

        // 候选 1 分数 0.85 (< 0.88)，与候选 2 (0.83) 分差仅 0.02 (< 0.15)，无共识
        RetrievalResult top1 = createResult(301L, "弱优势第一名", 0.85, "vector");
        top1.getMetadata().put("maxNormalizedScore", 0.85);
        top1.getMetadata().put("topMargin", 0.02);
        top1.getMetadata().put("topConsensus", false);

        RetrievalResult top2 = createResult(302L, "紧随其后第二名", 0.83, "vector");

        List<RetrievalResult> candidates = List.of(top1, top2);

        List<RetrievalResult> reranked = rerankService.rerank(
                "模糊竞争查询", candidates, QueryIntent.builder().build(), 2, 1L, "mock-model"
        );

        // 验证精排算子被正常调用 1 次，且返回了精排模型重排后的结果
        verify(mockProvider, times(1)).rerank(any(), any(), any(), anyInt());
        assertEquals(302L, reranked.get(0).getSegmentId());
    }

    @Test
    @DisplayName("契约 4: RRF k 参数消融与平滑性单调性验证")
    void testRrfKAblation() {
        CandidateFusionService service = new CandidateFusionService();

        List<RetrievalResult> list1 = List.of(
                createResult(1L, "Doc1", 0.9, "vector"),
                createResult(2L, "Doc2", 0.8, "vector")
        );

        // 消融 k = 10
        service.setRrfK(10);
        CandidateFusionService.FusionResult resK10 = service.fuseWithDiagnostics(List.of(list1), List.of("vector"));
        double diffK10 = resK10.getResults().get(0).getScore() - resK10.getResults().get(1).getScore();
        // 1/(10+1) - 1/(10+2) = 1/11 - 1/12 = 1/132 ≈ 0.007575
        assertEquals(1.0 / 132.0, diffK10, 1e-6);

        // 消融 k = 60
        service.setRrfK(60);
        CandidateFusionService.FusionResult resK60 = service.fuseWithDiagnostics(List.of(list1), List.of("vector"));
        double diffK60 = resK60.getResults().get(0).getScore() - resK60.getResults().get(1).getScore();
        // 1/(60+1) - 1/(60+2) = 1/61 - 1/62 = 1/3782 ≈ 0.0002644
        assertEquals(1.0 / 3782.0, diffK60, 1e-6);

        // 消融 k = 100
        service.setRrfK(100);
        CandidateFusionService.FusionResult resK100 = service.fuseWithDiagnostics(List.of(list1), List.of("vector"));
        double diffK100 = resK100.getResults().get(0).getScore() - resK100.getResults().get(1).getScore();
        // 1/(100+1) - 1/(100+2) = 1/101 - 1/102 = 1/10302 ≈ 0.00009706
        assertEquals(1.0 / 10302.0, diffK100, 1e-6);

        // 验证平滑性：随着 k 增大，高排名的极端分差单调递减
        assertTrue(diffK10 > diffK60);
        assertTrue(diffK60 > diffK100);
    }

    private static RetrievalResult createResult(Long segmentId, String docName, double score, String source) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("segmentId", segmentId);
        meta.put("score", score);
        meta.put("source", source);
        return RetrievalResult.builder()
                .segmentId(segmentId)
                .documentId(segmentId)
                .documentName(docName)
                .content(docName + " 详细内容正文")
                .score(score)
                .source(source)
                .metadata(meta)
                .build();
    }

    private static RagRerankService createRerankService(List<RerankerProvider> providers,
                                                        boolean gateEnabled,
                                                        double confThreshold,
                                                        double marginThreshold) {
        RagRerankService service = new RagRerankService();
        ColbertScorer colbert = mock(ColbertScorer.class);
        when(colbert.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        ReflectionTestUtils.setField(service, "rerankerProviders", providers);
        ReflectionTestUtils.setField(service, "deterministicRerankerProvider", new DeterministicRerankerProvider());
        ReflectionTestUtils.setField(service, "colbertScorer", colbert);
        ReflectionTestUtils.setField(service, "identifierConsistencyEnabled", false);
        ReflectionTestUtils.setField(service, "postFusionFilterEnabled", false);
        ReflectionTestUtils.setField(service, "rerankGateEnabled", gateEnabled);
        ReflectionTestUtils.setField(service, "gateConfidenceThreshold", confThreshold);
        ReflectionTestUtils.setField(service, "gateMarginThreshold", marginThreshold);
        return service;
    }
}
