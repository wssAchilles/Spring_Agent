package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        when(candidateFusionService.fuse(anyList(), anyList())).thenReturn(List.of(candidate));
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
        assertTrue(result.getDebugInfo().containsKey("fallbacks"));
        assertTrue(((java.util.Map<?, ?>) result.getDebugInfo().get("fallbacks")).isEmpty());
        assertNull(result.getDebugInfo().get("semanticCacheHit"));
        assertEquals("not_queried",
                ((java.util.Map<?, ?>) result.getDebugInfo().get("semanticCache")).get("status"));
        verify(ragRerankService).rerank(eq("hi"), anyList(), eq(intent), eq(5), isNull(), isNull());
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
}
