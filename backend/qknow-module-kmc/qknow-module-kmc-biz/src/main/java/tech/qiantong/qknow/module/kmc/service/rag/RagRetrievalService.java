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
    private CandidateFusionService candidateFusionService;

    @Resource
    private RagRerankService ragRerankService;

    @Resource
    private RagContextBuilder ragContextBuilder;

    @Resource
    private KmcKnowledgeBaseMapper kmcKnowledgeBaseMapper;

    @Resource
    private PermissionFilter permissionFilter;

    public RagResult retrieve(Long knowledgeBaseId, String query, int topK, boolean debug) {
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

        QueryIntent queryIntent = queryIntentAnalyzer.analyze(query);
        if (debug) {
            debugInfo.put("queryIntent", queryIntent);
            debugInfo.put("searchMethod", "RAG v2 混合检索");
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

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<List<RetrievalResult>> vectorFuture = executor.submit(
                    () -> vectorRetriever.retrieve(knowledgeBaseId, query, candidateTopK));
            Future<List<RetrievalResult>> keywordFuture = executor.submit(
                    () -> keywordRetriever.retrieve(knowledgeBaseId, query, candidateTopK));
            Future<List<RetrievalResult>> metadataFuture = executor.submit(
                    () -> metadataRetriever.retrieve(knowledgeBaseId, queryIntent, candidateTopK));

            vectorResults = getFuture(vectorFuture, "vector");
            keywordResults = getFuture(keywordFuture, "keyword");
            metadataResults = getFuture(metadataFuture, "metadata");
        } finally {
            executor.shutdown();
        }

        if (debug) {
            debugInfo.put("vectorResultCount", vectorResults.size());
            debugInfo.put("keywordResultCount", keywordResults.size());
            debugInfo.put("metadataResultCount", metadataResults.size());
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

        List<RetrievalResult> fused = candidateFusionService.fuse(allResults);
        if (debug) {
            debugInfo.put("fusedCount", fused.size());
        }

        List<RetrievalResult> reranked = ragRerankService.rerank(
                query, fused, queryIntent, topK, rerankingProviderName, rerankingModelName);
        if (debug) {
            debugInfo.put("rerankedCount", reranked.size());
            debugInfo.put("rerankerProvider", rerankingProviderName != null && rerankingModelName != null
                    ? "dashscope" : "deterministic");
        }

        String context = ragContextBuilder.buildContext(reranked, true);

        long elapsed = System.currentTimeMillis() - startTime;
        if (debug) {
            debugInfo.put("elapsedMs", elapsed);
            debugInfo.put("semanticCacheHit", false);
            debugInfo.put("parentExpansionCount", reranked.stream()
                    .filter(result -> result.getParentSegmentId() != null && !result.getParentSegmentId().isBlank())
                    .count());
        }

        return RagResult.builder()
                .context(context)
                .sources(reranked)
                .debugInfo(debugInfo != null ? debugInfo : Map.of())
                .build();
    }

    private List<RetrievalResult> getFuture(Future<List<RetrievalResult>> future, String name) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Retriever '{}' failed or timed out", name, e);
            return new ArrayList<>();
        }
    }
}
