package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tech.qiantong.qknow.common.core.utils.SecurityUtils;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeBaseDO;
import tech.qiantong.qknow.module.kmc.dal.mapper.knowledgeBase.KmcKnowledgeBaseMapper;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
public class RagRetrievalService {

    private static final int DEFAULT_TOP_K = 50;
    private static final int CANDIDATE_MULTIPLIER = 3;

    @Resource
    private QueryIntentAnalyzer queryIntentAnalyzer;

    @Resource
    private VectorRetriever vectorRetriever;

    @Resource
    private KeywordRetriever keywordRetriever;

    @Resource
    private MetadataRetriever metadataRetriever;

    @Resource
    private GraphRagRetriever graphRagRetriever;

    @Resource
    private CandidateFusionService candidateFusionService;

    @Resource
    private RagRerankService ragRerankService;

    @Resource
    private RagContextBuilder ragContextBuilder;

    @Resource
    private KmcKnowledgeBaseMapper kmcKnowledgeBaseMapper;

    @Resource
    private PermissionFilter permissionFilter;

    @Resource
    private QueryEntityExtractionService queryEntityExtractionService;

    @Resource
    private CragRetrievalEvaluator cragRetrievalEvaluator;

    @Resource
    private QueryRouter queryRouter;

    @Resource
    private CragWebSearchClient cragWebSearchClient;

    @Resource
    private DynamicTopKConfig dynamicTopKConfig;

    @Resource(name = "threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor retrievalExecutor;

    public RagResult retrieve(Long knowledgeBaseId, String query, int topK, boolean debug) {
        return retrieve(knowledgeBaseId, query, query, topK, debug);
    }

    public RagResult retrieve(Long knowledgeBaseId, String originalQuery, String query, int topK, boolean debug) {
        RagFallbackMonitor.Scope fallbackScope = debug ? RagFallbackMonitor.openScope() : null;
        try {
            return retrieveScoped(knowledgeBaseId, originalQuery, query, topK, debug);
        } finally {
            if (fallbackScope != null) {
                fallbackScope.close();
            }
        }
    }

    private RagResult retrieveScoped(Long knowledgeBaseId, String originalQuery, String query, int topK, boolean debug) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> debugInfo = debug ? new LinkedHashMap<>() : null;

        // Query routing
        QueryRouter.QueryRoute route = queryRouter.classify(query);
        if (debug) {
            debugInfo.put("queryRoute", route.name());
        }

        // Simple queries: skip retrieval in normal serving, but keep recallDebug on the full retrieval path.
        if (route == QueryRouter.QueryRoute.SIMPLE && !debug) {
            return RagResult.builder()
                    .context("")
                    .sources(Collections.emptyList())
                    .debugInfo(debugInfo != null ? debugInfo : Map.of())
                    .build();
        }
        if (debug && route == QueryRouter.QueryRoute.SIMPLE) {
            debugInfo.put("forcedRetrievalForDebug", true);
            debugInfo.put("reason", "SIMPLE route - recallDebug forces retrieval");
        }

        // 权限检查
        List<Long> accessibleKbIds = permissionFilter.getAccessibleKnowledgeBaseIds(SecurityUtils.getUserId());
        if (accessibleKbIds != null && !accessibleKbIds.contains(knowledgeBaseId)) {
            return RagResult.builder()
                    .context("")
                    .sources(Collections.emptyList())
                    .debugInfo(debugInfo != null ? debugInfo : Map.of())
                    .build();
        }

        // Dynamic topK: 提前解析意图，通过 resolveTopK 动态调整
        QueryIntent queryIntent = queryIntentAnalyzer.analyze(originalQuery);
        int dynamicTopK = resolveTopK(topK, route, queryIntent);
        if (debug) {
            debugInfo.put("dynamicTopK", dynamicTopK);
            debugInfo.put("dynamicTopKRoute", route.name());
        }
        log.debug("Dynamic topK: route={}, topK {} -> {}", route, topK, dynamicTopK);

        RagResult first = retrieveOnce(knowledgeBaseId, queryIntent, query, dynamicTopK, debug, debugInfo, "first", route);
        CragRetrievalEvaluation evaluation = cragRetrievalEvaluator.evaluate(query, first);
        if (debug) {
            debugInfo.put("cragLabel", evaluation.getLabel() != null ? evaluation.getLabel().name() : null);
            debugInfo.put("cragConfidence", evaluation.getConfidence());
            debugInfo.put("cragReason", evaluation.getReason());
            debugInfo.put("rewrittenQuery", evaluation.getRewrittenQuery());
        }

        RagResult effective = first;
        boolean rewriteApplied = false;
        int secondRetrievalCount = 0;
        String rewrittenQuery = evaluation.getRewrittenQuery();
        if (evaluation.isIncorrect()
                && rewrittenQuery != null
                && !rewrittenQuery.isBlank()
                && !rewrittenQuery.trim().equalsIgnoreCase(query.trim())) {
            effective = retrieveOnce(knowledgeBaseId, queryIntent, rewrittenQuery, topK, debug, debugInfo, "second", route);
            rewriteApplied = true;
            secondRetrievalCount = effective.getSources().size();
        } else if (evaluation.isIncorrect()) {
            effective = RagResult.builder()
                    .context("")
                    .sources(Collections.emptyList())
                    .debugInfo(debugInfo != null ? debugInfo : Map.of())
                    .build();
        }
        if (evaluation.isIncorrect()) {
            effective = applyWebFallback(query, rewrittenQuery, effective, topK, debug, debugInfo);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (debug) {
            debugInfo.put("rewriteApplied", rewriteApplied);
            debugInfo.put("secondRetrievalCount", secondRetrievalCount);
            debugInfo.put("elapsedMs", elapsed);
            Map<String, Object> queryEnhance = new java.util.LinkedHashMap<>();
            queryEnhance.put("strategy", rewriteApplied ? "rewrite" : "none");
            queryEnhance.put("originalQuery", query);
            queryEnhance.put("variants", List.of());
            debugInfo.put("queryEnhance", queryEnhance);
            debugInfo.put("excludedPaths", List.of());
            debugInfo.put("fallbacks", RagFallbackMonitor.currentScopeSnapshot());
            effective.setDebugInfo(debugInfo);
        }
        return effective;
    }

    private RagResult retrieveOnce(Long knowledgeBaseId, QueryIntent queryIntent, String query, int topK, boolean debug,
                                    Map<String, Object> debugInfo, String phase, QueryRouter.QueryRoute route) {
        long phaseStart = System.currentTimeMillis();
        if (debug && "first".equals(phase)) {
            debugInfo.put("queryIntent", queryIntent);
            debugInfo.put("searchMethod", "RAG v2 混合检索 + CRAG");
        }

        int candidateTopK = Math.max(topK * CANDIDATE_MULTIPLIER, DEFAULT_TOP_K);

        KmcKnowledgeBaseDO kb = kmcKnowledgeBaseMapper.selectById(knowledgeBaseId);
        Long rerankingProviderName = null;
        String rerankingModelName = null;
        if (kb != null) {
            if (kb.getRerankingEnable() != null && kb.getRerankingEnable()) {
                rerankingProviderName = kb.getRerankingProviderName() != null
                        ? Long.valueOf(kb.getRerankingProviderName()) : null;
                rerankingModelName = kb.getRerankingModelName();
            }
        }

        List<RetrievalResult> vectorResults;
        List<RetrievalResult> keywordResults;
        List<RetrievalResult> metadataResults;
        List<RetrievalResult> graphResults;
        Map<String, Long> timings = debug ? new ConcurrentHashMap<>() : null;

        Future<List<String>> entityFuture = submitRetrieval(
                () -> timed(phase + "QueryEntityMs", timings,
                        () -> queryEntityExtractionService.extract(query, queryIntent.getKeywords())));
        Future<List<RetrievalResult>> vectorFuture = submitRetrieval(
                () -> timed(phase + "VectorMs", timings,
                        () -> vectorRetriever.retrieve(knowledgeBaseId, query, candidateTopK, queryIntent.getDayNo())));
        Future<List<RetrievalResult>> keywordFuture = submitRetrieval(
                () -> timed(phase + "KeywordMs", timings,
                        () -> keywordRetriever.retrieve(knowledgeBaseId, query, candidateTopK)));

        queryIntent.setEntities(getFuture(entityFuture, "query-entity"));
        Future<List<RetrievalResult>> metadataFuture = submitRetrieval(
                () -> timed(phase + "MetadataMs", timings,
                        () -> metadataRetriever.retrieve(knowledgeBaseId, queryIntent, candidateTopK)));
        Future<List<RetrievalResult>> graphFuture = submitRetrieval(
                () -> timed(phase + "GraphMs", timings,
                        () -> graphRagRetriever.retrieve(knowledgeBaseId, queryIntent, query, route, candidateTopK)));

        vectorResults = getFuture(vectorFuture, "vector");
        keywordResults = getFuture(keywordFuture, "keyword");
        metadataResults = getFuture(metadataFuture, "metadata");
        graphResults = getFuture(graphFuture, "graph");

        if (debug) {
            debugInfo.put(phase + "QueryEntities", queryIntent.getEntities());
            debugInfo.put(phase + "VectorResultCount", vectorResults.size());
            debugInfo.put(phase + "KeywordResultCount", keywordResults.size());
            debugInfo.put(phase + "MetadataResultCount", metadataResults.size());
            debugInfo.put(phase + "GraphResultCount", graphResults.size());
            putTimings(debugInfo, timings,
                    phase + "QueryEntityMs", phase + "VectorMs", phase + "KeywordMs",
                    phase + "MetadataMs", phase + "GraphMs");
        }

        List<List<RetrievalResult>> allResults = new ArrayList<>();
        if (CollUtil.isNotEmpty(vectorResults)) {
            allResults.add(vectorResults);
        }
        if (CollUtil.isNotEmpty(keywordResults)) {
            allResults.add(keywordResults);
        }
        if (CollUtil.isNotEmpty(metadataResults)) {
            allResults.add(metadataResults);
        }
        if (CollUtil.isNotEmpty(graphResults)) {
            allResults.add(graphResults);
        }

        List<String> pathNames = List.of("vector", "keyword", "metadata", "graph");
        long fusionStart = System.currentTimeMillis();
        List<RetrievalResult> fused = candidateFusionService.fuse(allResults, pathNames);
        if (debug) {
            debugInfo.put(phase + "FusedCount", fused.size());
            debugInfo.put(phase + "FusionMs", System.currentTimeMillis() - fusionStart);
        }

        long rerankStart = System.currentTimeMillis();
        List<RetrievalResult> reranked = ragRerankService.rerank(
                query, fused, queryIntent, topK, rerankingProviderName, rerankingModelName);
        if (debug) {
            debugInfo.put(phase + "RerankedCount", reranked.size());
            debugInfo.put(phase + "RerankMs", System.currentTimeMillis() - rerankStart);
            debugInfo.put("rerankerProvider", rerankingProviderName != null && rerankingModelName != null
                    ? "dashscope" : "deterministic");
        }

        long contextStart = System.currentTimeMillis();
        String context = ragContextBuilder.buildContext(reranked, true);

        if (debug) {
            debugInfo.put("semanticCacheHit", null);
            debugInfo.put("semanticCache", Map.of(
                    "status", "not_queried",
                    "reason", "recallDebug path does not invoke semantic cache"));
            debugInfo.put(phase + "ContextMs", System.currentTimeMillis() - contextStart);
            debugInfo.put(phase + "TotalMs", System.currentTimeMillis() - phaseStart);
            debugInfo.put(phase + "ParentExpansionCount", reranked.stream()
                    .filter(result -> result.getParentSegmentId() != null && !result.getParentSegmentId().isBlank())
                    .count());
            debugInfo.put("contextBytes", context.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            debugInfo.put("maxContextBytes", ragContextBuilder.getMaxContextBytes());
        }

        return RagResult.builder()
                .context(context)
                .sources(reranked)
                .debugInfo(debugInfo != null ? debugInfo : Map.of())
                .build();
    }

    private <T> T timed(String key, Map<String, Long> timings, Callable<T> callable) throws Exception {
        long start = System.currentTimeMillis();
        try {
            return callable.call();
        } finally {
            if (timings != null) {
                timings.put(key, System.currentTimeMillis() - start);
            }
        }
    }

    private <T> Future<T> submitRetrieval(Callable<T> callable) {
        if (retrievalExecutor == null) {
            throw new IllegalStateException("threadPoolTaskExecutor is not configured");
        }
        RagFallbackMonitor.Scope fallbackScope = RagFallbackMonitor.currentScope();
        return retrievalExecutor.submit(() -> {
            try (RagFallbackMonitor.ScopeBinding ignored = RagFallbackMonitor.bindScope(fallbackScope)) {
                return callable.call();
            }
        });
    }

    private void putTimings(Map<String, Object> debugInfo, Map<String, Long> timings, String... keys) {
        if (debugInfo == null || timings == null) {
            return;
        }
        for (String key : keys) {
            Long value = timings.get(key);
            if (value != null) {
                debugInfo.put(key, value);
            }
        }
    }

    private <T> List<T> getFuture(Future<List<T>> future, String name) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Future '{}' timed out and was cancelled", name, e);
            return new ArrayList<>();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            log.warn("Future '{}' interrupted and was cancelled", name, e);
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("Future '{}' failed or timed out", name, e);
            return new ArrayList<>();
        }
    }

    private RagResult applyWebFallback(String query, String rewrittenQuery, RagResult current, int topK,
                                       boolean debug, Map<String, Object> debugInfo) {
        String webQuery = rewrittenQuery != null && !rewrittenQuery.isBlank() ? rewrittenQuery : query;
        List<RetrievalResult> webResults = cragWebSearchClient.search(webQuery, Math.min(Math.max(topK, 3), 8));
        if (debug) {
            debugInfo.put("webFallbackApplied", !webResults.isEmpty());
            debugInfo.put("webFallbackCount", webResults.size());
        }
        if (webResults.isEmpty()) {
            return current;
        }
        List<RetrievalResult> merged = new ArrayList<>();
        if (current != null && current.getSources() != null) {
            merged.addAll(current.getSources());
        }
        merged.addAll(webResults);
        String context = ragContextBuilder.buildContext(merged, true);
        RagResult result = RagResult.builder()
                .context(context)
                .sources(merged)
                .debugInfo(debugInfo != null ? debugInfo : Map.of())
                .build();
        return result;
    }

    private int resolveTopK(int requestedTopK, QueryRouter.QueryRoute route, QueryIntent intent) {
        if (dynamicTopKConfig == null || !dynamicTopKConfig.isEnabled()) {
            return requestedTopK;
        }
        double multiplier = switch (route) {
            case COMPLEX -> dynamicTopKConfig.getComplexMultiplier();
            case MEDIUM -> dynamicTopKConfig.getMediumMultiplier();
            default -> 1.0;
        };
        int keywordCount = intent != null && intent.getKeywords() != null ? intent.getKeywords().size() : 0;
        double keywordBonus = Math.min(keywordCount * dynamicTopKConfig.getKeywordMultiplierStep(),
                dynamicTopKConfig.getMaxKeywordBonus());
        multiplier += keywordBonus;
        if (intent != null && intent.getDayNo() != null) {
            multiplier *= dynamicTopKConfig.getTemporalMultiplier();
        }
        int result = (int) Math.round(requestedTopK * multiplier);
        if (route == QueryRouter.QueryRoute.COMPLEX) {
            result = Math.max(result, dynamicTopKConfig.getComplexMinTopK());
        }
        return Math.max(dynamicTopKConfig.getMinTopK(),
                Math.min(result, dynamicTopKConfig.getMaxTopK()));
    }
}
