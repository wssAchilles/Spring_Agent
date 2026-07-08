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
    private QueryTransformService queryTransformService;

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

        QueryEnhancement firstEnhancement = buildQueryEnhancement(query);
        RagResult first = retrieveOnce(knowledgeBaseId, queryIntent, firstEnhancement, dynamicTopK,
                debug, debugInfo, "first", route);
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
            QueryEnhancement secondEnhancement = buildQueryEnhancement(rewrittenQuery);
            effective = retrieveOnce(knowledgeBaseId, queryIntent, secondEnhancement, topK,
                    debug, debugInfo, "second", route);
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
            Map<String, Object> queryEnhance = firstEnhancement.toDebugMap();
            queryEnhance.put("cragRewriteApplied", rewriteApplied);
            queryEnhance.put("cragRewrittenQuery", rewrittenQuery);
            debugInfo.put("queryEnhance", queryEnhance);
            debugInfo.putIfAbsent("excludedPaths", List.of());
            debugInfo.put("fallbacks", RagFallbackMonitor.currentScopeSnapshot());
            effective.setDebugInfo(debugInfo);
        }
        return effective;
    }

    private RagResult retrieveOnce(Long knowledgeBaseId, QueryIntent queryIntent, QueryEnhancement queryEnhancement,
                                    int topK, boolean debug,
                                    Map<String, Object> debugInfo, String phase, QueryRouter.QueryRoute route) {
        long phaseStart = System.currentTimeMillis();
        String query = queryEnhancement.primaryQuery();
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
                        () -> retrieveVectorVariants(knowledgeBaseId, queryEnhancement.vectorQueries(),
                                candidateTopK, queryIntent.getDayNo())));
        Future<List<RetrievalResult>> keywordFuture = submitRetrieval(
                () -> timed(phase + "KeywordMs", timings,
                        () -> retrieveKeywordVariants(knowledgeBaseId, queryEnhancement.fullPathQueries(), candidateTopK)));

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
            debugInfo.put(phase + "QueryVariants", queryEnhancement.fullPathQueries());
            debugInfo.put(phase + "VectorQueries", queryEnhancement.vectorQueries());
            debugInfo.put(phase + "VectorResultCount", vectorResults.size());
            debugInfo.put(phase + "KeywordResultCount", keywordResults.size());
            debugInfo.put(phase + "MetadataResultCount", metadataResults.size());
            debugInfo.put(phase + "GraphResultCount", graphResults.size());
            List<Map<String, Object>> graphProvenance = summarizeGraphProvenance(graphResults);
            debugInfo.put(phase + "GraphProvenance", graphProvenance);
            debugInfo.put(phase + "GraphShadowProvenance", graphProvenance);
            putTimings(debugInfo, timings,
                    phase + "QueryEntityMs", phase + "VectorMs", phase + "KeywordMs",
                    phase + "MetadataMs", phase + "GraphMs");
        }

        List<List<RetrievalResult>> allResults = new ArrayList<>();
        List<String> pathNames = new ArrayList<>();
        if (CollUtil.isNotEmpty(vectorResults)) {
            allResults.add(vectorResults);
            pathNames.add("vector");
        }
        if (CollUtil.isNotEmpty(keywordResults)) {
            allResults.add(keywordResults);
            pathNames.add("keyword");
        }
        if (CollUtil.isNotEmpty(metadataResults)) {
            allResults.add(metadataResults);
            pathNames.add("metadata");
        }
        if (CollUtil.isNotEmpty(graphResults)) {
            allResults.add(graphResults);
            pathNames.add("graph");
        }

        long fusionStart = System.currentTimeMillis();
        CandidateFusionService.FusionResult fusionResult = candidateFusionService.fuseWithDiagnostics(allResults, pathNames);
        List<RetrievalResult> fused = fusionResult.getResults();
        if (debug) {
            debugInfo.put(phase + "FusedCount", fused.size());
            debugInfo.put(phase + "FusionMs", System.currentTimeMillis() - fusionStart);
            debugInfo.put(phase + "PathScores", fusionResult.getPathScores());
            debugInfo.put(phase + "ExcludedPaths", fusionResult.getExcludedPaths());
            mergeExcludedPaths(debugInfo, fusionResult.getExcludedPaths());
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

    private QueryEnhancement buildQueryEnhancement(String query) {
        List<String> originalOnly = normalizeVariants(query, List.of(query));
        if (queryTransformService == null || !queryTransformService.isEnabled()) {
            return new QueryEnhancement(query, "none", originalOnly, originalOnly, false);
        }

        String strategy = Optional.ofNullable(queryTransformService.getStrategy())
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .orElse("none");
        if ("multi_query".equals(strategy)) {
            List<String> variants;
            try {
                variants = queryTransformService.expandQueries(query, Math.max(0, queryTransformService.getVariantCount()));
            } catch (Exception e) {
                RagFallbackMonitor.record("query_transform", "original_query", "multi-query failed: " + e.getMessage());
                log.warn("Multi-query expansion failed, using original query", e);
                variants = originalOnly;
            }
            List<String> normalized = normalizeVariants(query, variants);
            return new QueryEnhancement(query, "multi_query", normalized, normalized, false);
        }

        if ("hyde".equals(strategy)) {
            List<String> vectorQueries = new ArrayList<>(originalOnly);
            try {
                String hypothetical = queryTransformService.generateHypotheticalDocument(query);
                if (hypothetical != null
                        && !hypothetical.isBlank()
                        && !hypothetical.trim().equalsIgnoreCase(query != null ? query.trim() : "")) {
                    vectorQueries.add(hypothetical.trim());
                }
            } catch (Exception e) {
                RagFallbackMonitor.record("query_transform", "original_query", "hyde failed: " + e.getMessage());
                log.warn("HyDE generation failed, using original query", e);
            }
            return new QueryEnhancement(query, "hyde", originalOnly,
                    normalizeVariants(query, vectorQueries), true);
        }

        return new QueryEnhancement(query, strategy, originalOnly, originalOnly, false);
    }

    private List<String> normalizeVariants(String originalQuery, List<String> variants) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (originalQuery != null && !originalQuery.isBlank()) {
            normalized.add(originalQuery.trim());
        }
        if (variants != null) {
            for (String variant : variants) {
                if (variant != null && !variant.isBlank()) {
                    normalized.add(variant.trim());
                }
            }
        }
        return normalized.isEmpty() ? List.of("") : new ArrayList<>(normalized);
    }

    private List<RetrievalResult> retrieveVectorVariants(Long knowledgeBaseId, List<String> queries,
                                                         int candidateTopK, Integer dayNo) {
        return retrieveVariants(queries, query -> vectorRetriever.retrieve(knowledgeBaseId, query, candidateTopK, dayNo));
    }

    private List<RetrievalResult> retrieveKeywordVariants(Long knowledgeBaseId, List<String> queries, int candidateTopK) {
        return retrieveVariants(queries, query -> keywordRetriever.retrieve(knowledgeBaseId, query, candidateTopK));
    }

    private List<RetrievalResult> retrieveVariants(List<String> queries, VariantRetriever retriever) {
        Map<String, RetrievalResult> bestByKey = new LinkedHashMap<>();
        List<String> effectiveQueries = queries == null || queries.isEmpty() ? List.of("") : queries;
        for (String variant : effectiveQueries) {
            try {
                List<RetrievalResult> results = retriever.retrieve(variant);
                if (results == null) {
                    continue;
                }
                for (RetrievalResult result : results) {
                    if (result == null) {
                        continue;
                    }
                    String key = variantResultKey(result);
                    RetrievalResult existing = bestByKey.get(key);
                    if (existing == null || result.getScore() > existing.getScore()) {
                        bestByKey.put(key, result);
                    }
                }
            } catch (Exception e) {
                RagFallbackMonitor.record("query_transform", "partial_variant_skip",
                        "variant retrieval failed: " + e.getMessage());
                log.warn("Variant retrieval failed for query '{}'", variant, e);
            }
        }
        return bestByKey.values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .toList();
    }

    private String variantResultKey(RetrievalResult result) {
        if (result == null) {
            return "null";
        }
        if (result.getSegmentId() != null) {
            return "seg:" + result.getSegmentId();
        }
        if (result.getDocumentId() != null && result.getContent() != null) {
            return "doc:" + result.getDocumentId() + ":" + result.getContent().hashCode();
        }
        return "content:" + Objects.toString(result.getContent(), "");
    }

    @FunctionalInterface
    private interface VariantRetriever {
        List<RetrievalResult> retrieve(String query);
    }

    private record QueryEnhancement(String primaryQuery,
                                    String strategy,
                                    List<String> fullPathQueries,
                                    List<String> vectorQueries,
                                    boolean hydeVectorOnly) {
        Map<String, Object> toDebugMap() {
            Map<String, Object> debug = new LinkedHashMap<>();
            debug.put("strategy", strategy);
            debug.put("originalQuery", primaryQuery);
            debug.put("variants", fullPathQueries);
            debug.put("vectorVariants", vectorQueries);
            debug.put("hydeVectorOnly", hydeVectorOnly);
            return debug;
        }
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

    private void mergeExcludedPaths(Map<String, Object> debugInfo, List<String> excludedPaths) {
        if (debugInfo == null || excludedPaths == null || excludedPaths.isEmpty()) {
            return;
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        Object existing = debugInfo.get("excludedPaths");
        if (existing instanceof Collection<?> existingPaths) {
            for (Object path : existingPaths) {
                if (path instanceof String value) {
                    merged.add(value);
                }
            }
        }
        merged.addAll(excludedPaths);
        debugInfo.put("excludedPaths", new ArrayList<>(merged));
    }

    private List<Map<String, Object>> summarizeGraphProvenance(List<RetrievalResult> graphResults) {
        if (graphResults == null || graphResults.isEmpty()) {
            return List.of();
        }
        return graphResults.stream()
                .limit(20)
                .map(result -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("source", result.getSource());
                    row.put("segmentId", result.getSegmentId());
                    row.put("documentId", result.getDocumentId());
                    row.put("documentName", result.getDocumentName());
                    row.put("score", result.getScore());
                    row.put("metadata", result.getMetadata() != null ? result.getMetadata() : Map.of());
                    return row;
                })
                .toList();
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
