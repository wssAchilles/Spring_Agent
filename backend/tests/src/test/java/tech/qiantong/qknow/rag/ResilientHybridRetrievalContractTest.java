package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.qiantong.qknow.module.kmc.service.rag.CandidateFusionService;
import tech.qiantong.qknow.module.kmc.service.rag.GraphRagRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.KeywordRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.QueryEntityExtractionService;
import tech.qiantong.qknow.module.kmc.service.rag.VectorRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.orchestration.ResilientHybridRetrievalCoordinator;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 11: 向量与图谱混合检索弹性编排与软超时熔断契约测试
 */
@ExtendWith(MockitoExtension.class)
class ResilientHybridRetrievalContractTest {

    @Mock
    private VectorRetriever vectorRetriever;

    @Mock
    private KeywordRetriever keywordRetriever;

    @Mock
    private GraphRagRetriever graphRagRetriever;

    @Mock
    private QueryEntityExtractionService entityExtractionService;

    @Mock
    private CandidateFusionService fusionService;

    private ExecutorService coreExecutor;
    private ResilientHybridRetrievalCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coreExecutor = Executors.newFixedThreadPool(4);
        coordinator = new ResilientHybridRetrievalCoordinator(
                vectorRetriever, keywordRetriever, graphRagRetriever,
                entityExtractionService, fusionService, coreExecutor
        );
    }

    @Test
    @DisplayName("契约验证：图谱检索慢查 2 秒时，软超时在 250ms 内准时降级并成功输出向量与全文结果")
    void testGraphTimeoutFailOpenFallback() {
        // 模拟向量检索正常且迅速 (10ms)
        RetrievalResult vecItem = RetrievalResult.builder().content("向量检索结果").score(0.85).build();
        when(vectorRetriever.retrieve(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(vecItem));

        // 模拟全文检索正常 (10ms)
        RetrievalResult kwItem = RetrievalResult.builder().content("全文检索结果").score(0.75).build();
        when(keywordRetriever.retrieve(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(kwItem));

        // 模拟实体抽取正常 (20ms)
        when(entityExtractionService.extract(anyString(), anyList()))
                .thenReturn(List.of("Spring"));

        // 模拟 Neo4j 慢查 2000ms
        when(graphRagRetriever.retrieve(anyLong(), anyList(), anyInt())).thenAnswer(invocation -> {
            Thread.sleep(2000);
            return List.of(RetrievalResult.builder().content("慢查结果").score(0.9).build());
        });

        // 模拟融合服务直接返回合并项
        when(fusionService.fuseWithDiagnostics(anyList(), anyList())).thenAnswer(invocation -> {
            List<List<RetrievalResult>> candidates = invocation.getArgument(0);
            List<RetrievalResult> flat = candidates.stream().flatMap(List::stream).toList();
            return new CandidateFusionService.FusionResult(flat, List.of(), List.of());
        });

        QueryIntent intent = new QueryIntent();
        intent.setKeywords(List.of("Spring"));

        long start = System.currentTimeMillis();
        List<RetrievalResult> results = coordinator.parallelRetrieve(1L, "Spring 架构", intent, 5);
        long duration = System.currentTimeMillis() - start;

        // 断言耗时必须在 600ms 以内（250ms 软超时 + 线程调度缓冲），绝不能被 2000ms 拖垮
        assertTrue(duration < 600, "端到端检索耗时必须受控在软超时以内，实际耗时=" + duration + "ms");
        assertNotNull(results);
        // 断言包含了向量与全文的返回，图谱慢查被丢弃未影响主流程
        assertTrue(results.stream().anyMatch(r -> "向量检索结果".equals(r.getContent())), "必须包含向量检索结果");
        assertTrue(results.stream().anyMatch(r -> "全文检索结果".equals(r.getContent())), "必须包含全文检索结果");
    }

    @Test
    @DisplayName("契约验证：图谱通道出现异常时 100% Fail-Open 降级，主流程不抛出异常")
    void testGraphExceptionFailOpen() {
        RetrievalResult vecItem = RetrievalResult.builder().content("正常向量结果").score(0.88).build();
        when(vectorRetriever.retrieve(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(vecItem));
        when(keywordRetriever.retrieve(anyLong(), anyString(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(entityExtractionService.extract(anyString(), anyList()))
                .thenReturn(List.of("Entity"));

        // 模拟 Neo4j 连接失败抛出异常
        when(graphRagRetriever.retrieve(anyLong(), anyList(), anyInt()))
                .thenThrow(new RuntimeException("Neo4j connection refused"));

        when(fusionService.fuseWithDiagnostics(anyList(), anyList())).thenAnswer(invocation -> {
            List<List<RetrievalResult>> candidates = invocation.getArgument(0);
            List<RetrievalResult> flat = candidates.stream().flatMap(List::stream).toList();
            return new CandidateFusionService.FusionResult(flat, List.of(), List.of());
        });

        QueryIntent intent = new QueryIntent();
        List<RetrievalResult> results = assertDoesNotThrow(() ->
                coordinator.parallelRetrieve(1L, "测试问题", intent, 5), "图谱异常必须被捕获降级");

        assertEquals(1, results.size());
        assertEquals("正常向量结果", results.get(0).getContent());
    }
}
