package tech.qiantong.qknow.module.kmc.service.rag.orchestration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kmc.service.rag.CandidateFusionService;
import tech.qiantong.qknow.module.kmc.service.rag.GraphRagRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.KeywordRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.QueryEntityExtractionService;
import tech.qiantong.qknow.module.kmc.service.rag.VectorRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.*;
import java.util.concurrent.*;

/**
 * 工业级混合检索弹性编排协调器
 * 核心技术：CompletableFuture 响应式非阻塞编排、Neo4j 独立仓壁隔离 (Bulkhead)、250ms 软超时降级与无偏 RRF 融合
 */
@Slf4j
@Service
public class ResilientHybridRetrievalCoordinator {

    private final VectorRetriever vectorRetriever;
    private final KeywordRetriever keywordRetriever;
    private final GraphRagRetriever graphRagRetriever;
    private final QueryEntityExtractionService entityExtractionService;
    private final CandidateFusionService fusionService;

    // 核心线程池（向量、全文检索等轻量快通道）
    private final ExecutorService coreRetrievalExecutor;

    // 仓壁隔离线程池（专供 Neo4j 图谱慢通道，物理阻断对核心线程池的拖拽）
    private final ExecutorService graphBulkheadExecutor;

    @Autowired
    public ResilientHybridRetrievalCoordinator(
            VectorRetriever vectorRetriever,
            KeywordRetriever keywordRetriever,
            GraphRagRetriever graphRagRetriever,
            QueryEntityExtractionService entityExtractionService,
            CandidateFusionService fusionService,
            @Autowired(required = false) @Qualifier("threadPoolTaskExecutor") Executor coreExecutor) {
        this.vectorRetriever = vectorRetriever;
        this.keywordRetriever = keywordRetriever;
        this.graphRagRetriever = graphRagRetriever;
        this.entityExtractionService = entityExtractionService;
        this.fusionService = fusionService;

        if (coreExecutor instanceof ExecutorService es) {
            this.coreRetrievalExecutor = es;
        } else if (coreExecutor != null) {
            this.coreRetrievalExecutor = Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r, "core-retrieval-worker");
                t.setDaemon(true);
                return t;
            });
        } else {
            this.coreRetrievalExecutor = Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r, "core-retrieval-worker");
                t.setDaemon(true);
                return t;
            });
        }

        // 独立构建 Neo4j 仓壁隔离线程池 (核心4, 最大8, 有界队列50, 饱和丢弃降级)
        this.graphBulkheadExecutor = new ThreadPoolExecutor(
                4, 8, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                r -> {
                    Thread t = new Thread(r, "bulkhead-neo4j-worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardPolicy() // 队列满直接丢弃，不阻塞主流程
        );
    }

    /**
     * 高并发并行多路检索协调，并在规定预算内完成融合
     */
    public List<RetrievalResult> parallelRetrieve(Long kbId, String query, QueryIntent intent, int topK) {
        long deadlineBudgetMs = 500L;

        // 1. 向量检索异步分支 (软超时 200ms)
        CompletableFuture<List<RetrievalResult>> vectorFuture = CompletableFuture.<List<RetrievalResult>>supplyAsync(
                () -> {
                    if (vectorRetriever == null) return Collections.<RetrievalResult>emptyList();
                    return vectorRetriever.retrieve(kbId, query, topK);
                }, coreRetrievalExecutor)
                .completeOnTimeout(Collections.<RetrievalResult>emptyList(), 200, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("[HybridCoordinator] 向量检索异常降级: {}", ex.getMessage());
                    return Collections.<RetrievalResult>emptyList();
                });

        // 2. 全文检索异步分支 (软超时 150ms)
        CompletableFuture<List<RetrievalResult>> keywordFuture = CompletableFuture.<List<RetrievalResult>>supplyAsync(
                () -> {
                    if (keywordRetriever == null) return Collections.<RetrievalResult>emptyList();
                    return keywordRetriever.retrieve(kbId, query, topK);
                }, coreRetrievalExecutor)
                .completeOnTimeout(Collections.<RetrievalResult>emptyList(), 150, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("[HybridCoordinator] 全文检索异常降级: {}", ex.getMessage());
                    return Collections.<RetrievalResult>emptyList();
                });

        // 3. 实体抽取接力 Neo4j 图检索异步流水线 (软超时 250ms)
        CompletableFuture<List<RetrievalResult>> graphFuture = CompletableFuture.<List<String>>supplyAsync(
                () -> {
                    if (entityExtractionService == null || intent == null) return Collections.<String>emptyList();
                    return entityExtractionService.extract(query, intent.getKeywords());
                }, coreRetrievalExecutor)
                .completeOnTimeout(Collections.<String>emptyList(), 80, TimeUnit.MILLISECONDS)
                .thenComposeAsync(entities -> {
                    if (entities == null || entities.isEmpty() || graphRagRetriever == null) {
                        return CompletableFuture.completedFuture(Collections.<RetrievalResult>emptyList());
                    }
                    return CompletableFuture.supplyAsync(
                            () -> graphRagRetriever.retrieve(kbId, entities, topK),
                            graphBulkheadExecutor
                    );
                }, graphBulkheadExecutor)
                .completeOnTimeout(Collections.<RetrievalResult>emptyList(), 250, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("[HybridCoordinator] Neo4j 图检索异常或超时降级: {}", ex.getMessage());
                    return Collections.<RetrievalResult>emptyList();
                });

        // 4. 等待多路结果受控汇聚 (全局硬预算 500ms)
        try {
            CompletableFuture.allOf(vectorFuture, keywordFuture, graphFuture)
                    .get(deadlineBudgetMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("[HybridCoordinator] 全局检索预算已耗尽 (500ms)，执行部分召回截断融合");
        } catch (Exception e) {
            log.error("[HybridCoordinator] 检索流水线等待异常", e);
        }

        List<RetrievalResult> vectorRes = vectorFuture.getNow(Collections.emptyList());
        List<RetrievalResult> keywordRes = keywordFuture.getNow(Collections.emptyList());
        List<RetrievalResult> graphRes = graphFuture.getNow(Collections.emptyList());

        // 5. 组合有效候选集，由 CandidateFusionService 执行无偏 RRF (k=60) 融合
        List<List<RetrievalResult>> candidates = new ArrayList<>();
        List<String> pathNames = new ArrayList<>();
        if (vectorRes != null && !vectorRes.isEmpty()) {
            candidates.add(vectorRes);
            pathNames.add("vector");
        }
        if (keywordRes != null && !keywordRes.isEmpty()) {
            candidates.add(keywordRes);
            pathNames.add("keyword");
        }
        if (graphRes != null && !graphRes.isEmpty()) {
            candidates.add(graphRes);
            pathNames.add("graph");
        }

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        if (fusionService != null) {
            var diag = fusionService.fuseWithDiagnostics(candidates, pathNames);
            return diag != null && diag.getResults() != null ? diag.getResults() : Collections.emptyList();
        }

        // 若无融合服务，回退到按顺序合并
        List<RetrievalResult> fallback = new ArrayList<>();
        candidates.forEach(fallback::addAll);
        return fallback;
    }
}
