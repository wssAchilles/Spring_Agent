package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.common.core.domain.model.LoginUser;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.dal.mapper.knowledgeBase.KmcKnowledgeBaseMapper;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceDebugTest {

    @Mock
    private QueryIntentAnalyzer queryIntentAnalyzer;
    @Mock
    private VectorRetriever vectorRetriever;
    @Mock
    private KeywordRetriever keywordRetriever;
    @Mock
    private MetadataRetriever metadataRetriever;
    @Mock
    private GraphRagRetriever graphRagRetriever;
    @Mock
    private CandidateFusionService candidateFusionService;
    @Mock
    private RagRerankService ragRerankService;
    @Mock
    private RagContextBuilder ragContextBuilder;
    @Mock
    private KmcKnowledgeBaseMapper kmcKnowledgeBaseMapper;
    @Mock
    private PermissionFilter permissionFilter;
    @Mock
    private QueryEntityExtractionService queryEntityExtractionService;
    @Mock
    private CragRetrievalEvaluator cragRetrievalEvaluator;
    @Mock
    private QueryRouter queryRouter;
    @Mock
    private QueryTransformService queryTransformService;
    @Mock
    private CragWebSearchClient cragWebSearchClient;

    private RagRetrievalService service;
    private ThreadPoolTaskExecutor retrievalExecutor;

    @BeforeEach
    void setUp() {
        RagFallbackMonitor.reset();
        service = new RagRetrievalService();
        retrievalExecutor = new ThreadPoolTaskExecutor();
        retrievalExecutor.setCorePoolSize(2);
        retrievalExecutor.setMaxPoolSize(2);
        retrievalExecutor.setQueueCapacity(10);
        retrievalExecutor.setThreadNamePrefix("rag-retrieval-test-");
        retrievalExecutor.initialize();
        ReflectionTestUtils.setField(service, "queryIntentAnalyzer", queryIntentAnalyzer);
        ReflectionTestUtils.setField(service, "vectorRetriever", vectorRetriever);
        ReflectionTestUtils.setField(service, "keywordRetriever", keywordRetriever);
        ReflectionTestUtils.setField(service, "metadataRetriever", metadataRetriever);
        ReflectionTestUtils.setField(service, "graphRagRetriever", graphRagRetriever);
        ReflectionTestUtils.setField(service, "candidateFusionService", candidateFusionService);
        ReflectionTestUtils.setField(service, "ragRerankService", ragRerankService);
        ReflectionTestUtils.setField(service, "ragContextBuilder", ragContextBuilder);
        ReflectionTestUtils.setField(service, "kmcKnowledgeBaseMapper", kmcKnowledgeBaseMapper);
        ReflectionTestUtils.setField(service, "permissionFilter", permissionFilter);
        ReflectionTestUtils.setField(service, "queryEntityExtractionService", queryEntityExtractionService);
        ReflectionTestUtils.setField(service, "cragRetrievalEvaluator", cragRetrievalEvaluator);
        ReflectionTestUtils.setField(service, "queryRouter", queryRouter);
        ReflectionTestUtils.setField(service, "queryTransformService", queryTransformService);
        ReflectionTestUtils.setField(service, "cragWebSearchClient", cragWebSearchClient);
        ReflectionTestUtils.setField(service, "retrievalExecutor", retrievalExecutor);
        DynamicTopKConfig dynamicTopKConfig = new DynamicTopKConfig();
        dynamicTopKConfig.setEnabled(false);
        ReflectionTestUtils.setField(service, "dynamicTopKConfig", dynamicTopKConfig);

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(42L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        RagFallbackMonitor.reset();
        if (retrievalExecutor != null) {
            retrievalExecutor.shutdown();
        }
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("非调试 SIMPLE 路由仍跳过检索")
    void retrieve_simpleRouteWithoutDebug_skipsRetrieval() {
        when(queryRouter.classify("hi")).thenReturn(QueryRouter.QueryRoute.SIMPLE);

        RagResult result = service.retrieve(1L, "hi", 5, false);

        assertEquals("", result.getContext());
        assertTrue(result.getSources().isEmpty());
        verifyNoInteractions(ragRerankService, vectorRetriever, keywordRetriever, metadataRetriever, graphRagRetriever);
    }

    @Test
    @DisplayName("recallDebug SIMPLE 路由强制进入 rerank 链路")
    void retrieve_simpleRouteWithDebug_invokesRerank() {
        RagFallbackMonitor.record("jni", "historical", "before recallDebug");
        when(queryRouter.classify("hi")).thenReturn(QueryRouter.QueryRoute.SIMPLE);
        when(permissionFilter.getAccessibleKnowledgeBaseIds(any())).thenReturn(null);
        QueryIntent intent = QueryIntent.builder().build();
        when(queryIntentAnalyzer.analyze("hi")).thenReturn(intent);
        when(queryEntityExtractionService.extract(eq("hi"), anyList())).thenReturn(List.of());

        RetrievalResult candidate = RetrievalResult.builder()
                .segmentId(11L)
                .content("debug recall candidate")
                .score(0.8)
                .source("vector")
                .build();
        when(vectorRetriever.retrieve(eq(1L), eq("hi"), anyInt(), isNull())).thenReturn(List.of(candidate));
        when(keywordRetriever.retrieve(eq(1L), eq("hi"), anyInt())).thenReturn(List.of());
        when(metadataRetriever.retrieve(eq(1L), eq(intent), anyInt())).thenReturn(List.of());
        when(graphRagRetriever.retrieve(eq(1L), eq(intent), eq("hi"), eq(QueryRouter.QueryRoute.SIMPLE), anyInt()))
                .thenReturn(List.of());
        when(candidateFusionService.fuseWithDiagnostics(anyList(), anyList())).thenReturn(
                new CandidateFusionService.FusionResult(List.of(candidate), List.of(), List.of()));
        when(ragRerankService.rerank(eq("hi"), anyList(), eq(intent), eq(5), isNull(), isNull()))
                .thenReturn(List.of(candidate));
        when(ragContextBuilder.buildContext(anyList(), eq(true))).thenReturn("ctx");
        when(ragContextBuilder.getMaxContextBytes()).thenReturn(1024);
        when(cragRetrievalEvaluator.evaluate(eq("hi"), any(RagResult.class))).thenReturn(
                CragRetrievalEvaluation.builder()
                        .label(CragRetrievalEvaluation.Label.CORRECT)
                        .confidence(1.0)
                        .reason("test")
                        .rewrittenQuery("hi")
                        .build()
        );

        RagResult result = service.retrieve(1L, "hi", "hi", 5, true);

        assertEquals("ctx", result.getContext());
        assertEquals(Boolean.TRUE, result.getDebugInfo().get("forcedRetrievalForDebug"));
        assertTrue(result.getDebugInfo().containsKey("firstVectorMs"));
        assertTrue(result.getDebugInfo().containsKey("firstKeywordMs"));
        assertTrue(result.getDebugInfo().containsKey("firstRerankMs"));
        assertTrue(result.getDebugInfo().containsKey("firstContextMs"));
        assertTrue(result.getDebugInfo().containsKey("firstTotalMs"));
        assertTrue(result.getDebugInfo().containsKey("firstExcludedPaths"));
        assertTrue(result.getDebugInfo().containsKey("firstGraphProvenance"));
        assertEquals(List.of(), result.getDebugInfo().get("firstGraphProvenance"));
        assertTrue(result.getDebugInfo().containsKey("fallbacks"));
        assertTrue(((java.util.Map<?, ?>) result.getDebugInfo().get("fallbacks")).isEmpty());
        assertNull(result.getDebugInfo().get("semanticCacheHit"));
        assertEquals("not_queried",
                ((java.util.Map<?, ?>) result.getDebugInfo().get("semanticCache")).get("status"));
        verify(ragRerankService).rerank(eq("hi"), anyList(), eq(intent), eq(5), isNull(), isNull());
    }

    @Test
    @DisplayName("融合诊断 pathNames 与非空结果列表保持对齐")
    void retrieve_debugFusionDiagnostics_alignsPathNamesWithNonEmptyResultLists() {
        when(queryRouter.classify("graph metadata query")).thenReturn(QueryRouter.QueryRoute.MEDIUM);
        when(permissionFilter.getAccessibleKnowledgeBaseIds(any())).thenReturn(null);
        QueryIntent intent = QueryIntent.builder().build();
        when(queryIntentAnalyzer.analyze("graph metadata query")).thenReturn(intent);
        when(queryEntityExtractionService.extract(eq("graph metadata query"), anyList())).thenReturn(List.of());

        RetrievalResult metadata = RetrievalResult.builder()
                .segmentId(21L)
                .content("metadata candidate")
                .score(4.0)
                .source("metadata")
                .build();
        RetrievalResult graph = RetrievalResult.builder()
                .segmentId(22L)
                .content("graph candidate")
                .score(8.0)
                .source("graph")
                .build();
        when(vectorRetriever.retrieve(eq(1L), eq("graph metadata query"), anyInt(), isNull())).thenReturn(List.of());
        when(keywordRetriever.retrieve(eq(1L), eq("graph metadata query"), anyInt())).thenReturn(List.of());
        when(metadataRetriever.retrieve(eq(1L), eq(intent), anyInt())).thenReturn(List.of(metadata));
        when(graphRagRetriever.retrieve(eq(1L), eq(intent), eq("graph metadata query"), eq(QueryRouter.QueryRoute.MEDIUM), anyInt()))
                .thenReturn(List.of(graph));
        when(candidateFusionService.fuseWithDiagnostics(anyList(), anyList())).thenReturn(
                new CandidateFusionService.FusionResult(List.of(metadata, graph), List.of(), List.of("graph")));
        when(ragRerankService.rerank(eq("graph metadata query"), anyList(), eq(intent), eq(5), isNull(), isNull()))
                .thenReturn(List.of(metadata, graph));
        when(ragContextBuilder.buildContext(anyList(), eq(true))).thenReturn("ctx");
        when(ragContextBuilder.getMaxContextBytes()).thenReturn(1024);
        when(cragRetrievalEvaluator.evaluate(eq("graph metadata query"), any(RagResult.class))).thenReturn(
                CragRetrievalEvaluation.builder()
                        .label(CragRetrievalEvaluation.Label.CORRECT)
                        .confidence(1.0)
                        .reason("test")
                        .rewrittenQuery("graph metadata query")
                        .build()
        );

        RagResult result = service.retrieve(1L, "graph metadata query", "graph metadata query", 5, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<RetrievalResult>>> resultListsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> pathNamesCaptor = ArgumentCaptor.forClass(List.class);
        verify(candidateFusionService).fuseWithDiagnostics(resultListsCaptor.capture(), pathNamesCaptor.capture());
        assertEquals(2, resultListsCaptor.getValue().size());
        assertEquals(List.of("metadata", "graph"), pathNamesCaptor.getValue());
        assertEquals(List.of("graph"), result.getDebugInfo().get("firstExcludedPaths"));
        assertEquals(List.of("graph"), result.getDebugInfo().get("excludedPaths"));
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> provenance =
                (List<java.util.Map<String, Object>>) result.getDebugInfo().get("firstGraphProvenance");
        assertEquals(1, provenance.size());
        assertEquals("graph", provenance.get(0).get("source"));
        assertEquals(22L, provenance.get(0).get("segmentId"));
    }

    @Test
    @DisplayName("multi_query strategy 只在显式开启时展开并保留原始查询")
    void retrieve_multiQueryStrategy_expandsVectorAndKeywordVariants() {
        when(queryTransformService.isEnabled()).thenReturn(true);
        when(queryTransformService.getStrategy()).thenReturn("multi_query");
        when(queryTransformService.getVariantCount()).thenReturn(2);
        when(queryTransformService.expandQueries("multi query", 2))
                .thenReturn(List.of("multi query", "variant one", "variant two"));
        stubSuccessfulRetrieval("multi query", QueryRouter.QueryRoute.MEDIUM, List.of(), List.of());
        when(vectorRetriever.retrieve(eq(1L), anyString(), anyInt(), isNull())).thenAnswer(invocation -> {
            String query = invocation.getArgument(1);
            return List.of(resultFor(query.hashCode() & 0xffffL, query, "vector"));
        });
        when(keywordRetriever.retrieve(eq(1L), anyString(), anyInt())).thenAnswer(invocation -> {
            String query = invocation.getArgument(1);
            return List.of(resultFor((query.hashCode() & 0xffffL) + 100000L, query, "keyword"));
        });
        stubFusionPassthrough();

        RagResult result = service.retrieve(1L, "multi query", "multi query", 5, true);

        verify(vectorRetriever).retrieve(eq(1L), eq("multi query"), anyInt(), isNull());
        verify(vectorRetriever).retrieve(eq(1L), eq("variant one"), anyInt(), isNull());
        verify(vectorRetriever).retrieve(eq(1L), eq("variant two"), anyInt(), isNull());
        verify(keywordRetriever).retrieve(eq(1L), eq("multi query"), anyInt());
        verify(keywordRetriever).retrieve(eq(1L), eq("variant one"), anyInt());
        verify(keywordRetriever).retrieve(eq(1L), eq("variant two"), anyInt());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> queryEnhance =
                (java.util.Map<String, Object>) result.getDebugInfo().get("queryEnhance");
        assertEquals("multi_query", queryEnhance.get("strategy"));
        assertEquals(List.of("multi query", "variant one", "variant two"), queryEnhance.get("variants"));
    }

    @Test
    @DisplayName("HyDE strategy 只把假设文档加入 vector 路径")
    void retrieve_hydeStrategy_addsHypotheticalDocumentToVectorOnly() {
        when(queryTransformService.isEnabled()).thenReturn(true);
        when(queryTransformService.getStrategy()).thenReturn("hyde");
        when(queryTransformService.generateHypotheticalDocument("why graph rag"))
                .thenReturn("GraphRAG uses entities and relations to answer multi-hop questions.");
        stubSuccessfulRetrieval("why graph rag", QueryRouter.QueryRoute.MEDIUM, List.of(), List.of());
        when(vectorRetriever.retrieve(eq(1L), anyString(), anyInt(), isNull())).thenAnswer(invocation -> {
            String query = invocation.getArgument(1);
            return List.of(resultFor(query.hashCode() & 0xffffL, query, "vector"));
        });
        when(keywordRetriever.retrieve(eq(1L), anyString(), anyInt()))
                .thenReturn(List.of(resultFor(41L, "keyword", "keyword")));
        stubFusionPassthrough();

        RagResult result = service.retrieve(1L, "why graph rag", "why graph rag", 5, true);

        verify(vectorRetriever).retrieve(eq(1L), eq("why graph rag"), anyInt(), isNull());
        verify(vectorRetriever).retrieve(eq(1L),
                eq("GraphRAG uses entities and relations to answer multi-hop questions."), anyInt(), isNull());
        verify(keywordRetriever, times(1)).retrieve(eq(1L), eq("why graph rag"), anyInt());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> queryEnhance =
                (java.util.Map<String, Object>) result.getDebugInfo().get("queryEnhance");
        assertEquals("hyde", queryEnhance.get("strategy"));
        assertEquals(Boolean.TRUE, queryEnhance.get("hydeVectorOnly"));
        assertEquals(List.of("why graph rag"), queryEnhance.get("variants"));
        assertEquals(List.of("why graph rag",
                        "GraphRAG uses entities and relations to answer multi-hop questions."),
                queryEnhance.get("vectorVariants"));
    }

    @Test
    @DisplayName("召回任务超时时取消 Future")
    void getFuture_timeoutCancelsFuture() {
        TimeoutFuture future = new TimeoutFuture();

        @SuppressWarnings("unchecked")
        List<String> result = ReflectionTestUtils.invokeMethod(service, "getFuture", future, "slow");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertTrue(future.cancelled);
    }

    private static class TimeoutFuture implements Future<List<String>> {
        private boolean cancelled;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = mayInterruptIfRunning;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public List<String> get() {
            return List.of();
        }

        @Override
        public List<String> get(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException {
            throw new TimeoutException("slow");
        }
    }

    private void stubSuccessfulRetrieval(String query, QueryRouter.QueryRoute route,
                                         List<RetrievalResult> metadataResults,
                                         List<RetrievalResult> graphResults) {
        when(queryRouter.classify(query)).thenReturn(route);
        when(permissionFilter.getAccessibleKnowledgeBaseIds(any())).thenReturn(null);
        QueryIntent intent = QueryIntent.builder().build();
        when(queryIntentAnalyzer.analyze(query)).thenReturn(intent);
        when(queryEntityExtractionService.extract(eq(query), anyList())).thenReturn(List.of());
        when(metadataRetriever.retrieve(eq(1L), eq(intent), anyInt())).thenReturn(metadataResults);
        when(graphRagRetriever.retrieve(eq(1L), eq(intent), eq(query), eq(route), anyInt()))
                .thenReturn(graphResults);
        when(ragRerankService.rerank(eq(query), anyList(), eq(intent), eq(5), isNull(), isNull()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(ragContextBuilder.buildContext(anyList(), eq(true))).thenReturn("ctx");
        when(ragContextBuilder.getMaxContextBytes()).thenReturn(1024);
        when(cragRetrievalEvaluator.evaluate(eq(query), any(RagResult.class))).thenReturn(
                CragRetrievalEvaluation.builder()
                        .label(CragRetrievalEvaluation.Label.CORRECT)
                        .confidence(1.0)
                        .reason("test")
                        .rewrittenQuery(query)
                        .build()
        );
    }

    private void stubFusionPassthrough() {
        when(candidateFusionService.fuseWithDiagnostics(anyList(), anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<List<RetrievalResult>> lists = invocation.getArgument(0);
            List<RetrievalResult> merged = lists.stream()
                    .flatMap(List::stream)
                    .toList();
            return new CandidateFusionService.FusionResult(merged, List.of(), List.of());
        });
    }

    private RetrievalResult resultFor(Long segmentId, String content, String source) {
        return RetrievalResult.builder()
                .segmentId(segmentId)
                .content(content)
                .score(0.8)
                .source(source)
                .build();
    }
}
