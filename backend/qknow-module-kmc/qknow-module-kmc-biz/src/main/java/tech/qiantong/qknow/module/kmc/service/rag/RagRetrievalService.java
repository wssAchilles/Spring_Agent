package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.common.core.utils.SecurityUtils;
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

    public RagResult retrieve(Long knowledgeBaseId, String query, int topK, boolean debug) {
        return retrieve(knowledgeBaseId, query, query, topK, debug);
    }

    public RagResult retrieve(Long knowledgeBaseId, String originalQuery, String query, int topK, boolean debug) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> debugInfo = debug ? new LinkedHashMap<>() : null;

        // 权限检查
        List<Long> accessibleKbIds = permissionFilter.getAccessibleKnowledgeBaseIds(SecurityUtils.getUserId());
        if (accessibleKbIds != null && !accessibleKbIds.contains(knowledgeBaseId)) {
            return RagResult.builder()
                    .context("")
                    .sources(Collections.emptyList())
                    .debugInfo(debugInfo != null ? debugInfo : Map.of())
                    .build();
        }

        RagResult first = retrieveOnce(knowledgeBaseId, originalQuery, query, topK, debug, debugInfo, "first");
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
            effective = retrieveOnce(knowledgeBaseId, originalQuery, rewrittenQuery, topK, debug, debugInfo, "second");
            rewriteApplied = true;
            secondRetrievalCount = effective.getSources().size();
        } else if (evaluation.isIncorrect()) {
            effective = RagResult.builder()
                    .context("")
                    .sources(Collections.emptyList())
                    .debugInfo(debugInfo != null ? debugInfo : Map.of())
                    .build();
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
            effective.setDebugInfo(debugInfo);
        }
        return effective;
    }

    private RagResult retrieveOnce(Long knowledgeBaseId, String originalQuery, String query, int topK, boolean debug,
                                    Map<String, Object> debugInfo, String phase) {
        QueryIntent queryIntent = queryIntentAnalyzer.analyze(originalQuery);
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

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            Future<List<String>> entityFuture = executor.submit(
                    () -> queryEntityExtractionService.extract(query, queryIntent.getKeywords()));
            Future<List<RetrievalResult>> vectorFuture = executor.submit(
                    () -> vectorRetriever.retrieve(knowledgeBaseId, query, candidateTopK, queryIntent.getDayNo()));
            Future<List<RetrievalResult>> keywordFuture = executor.submit(
                    () -> keywordRetriever.retrieve(knowledgeBaseId, query, candidateTopK));

            queryIntent.setEntities(getFuture(entityFuture, "query-entity"));
            Future<List<RetrievalResult>> metadataFuture = executor.submit(
                    () -> metadataRetriever.retrieve(knowledgeBaseId, queryIntent, candidateTopK));
            Future<List<RetrievalResult>> graphFuture = executor.submit(
                    () -> graphRagRetriever.retrieve(knowledgeBaseId, queryIntent.getEntities(), candidateTopK));

            vectorResults = getFuture(vectorFuture, "vector");
            keywordResults = getFuture(keywordFuture, "keyword");
            metadataResults = getFuture(metadataFuture, "metadata");
            graphResults = getFuture(graphFuture, "graph");
        } finally {
            executor.shutdown();
        }

        if (debug) {
            debugInfo.put(phase + "QueryEntities", queryIntent.getEntities());
            debugInfo.put(phase + "VectorResultCount", vectorResults.size());
            debugInfo.put(phase + "KeywordResultCount", keywordResults.size());
            debugInfo.put(phase + "MetadataResultCount", metadataResults.size());
            debugInfo.put(phase + "GraphResultCount", graphResults.size());
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
        List<RetrievalResult> fused = candidateFusionService.fuse(allResults, pathNames);
        if (debug) {
            debugInfo.put(phase + "FusedCount", fused.size());
        }

        List<RetrievalResult> reranked = ragRerankService.rerank(
                query, fused, queryIntent, topK, rerankingProviderName, rerankingModelName);
        if (debug) {
            debugInfo.put(phase + "RerankedCount", reranked.size());
            debugInfo.put("rerankerProvider", rerankingProviderName != null && rerankingModelName != null
                    ? "dashscope" : "deterministic");
        }

        String context = ragContextBuilder.buildContext(reranked, true);

        if (debug) {
            debugInfo.put("semanticCacheHit", false);
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

    private <T> List<T> getFuture(Future<List<T>> future, String name) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Future '{}' failed or timed out", name, e);
            return new ArrayList<>();
        }
    }
}
