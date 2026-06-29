package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DeterministicRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankRequestContext;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankerProvider;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RagRerankService {

    @Resource
    private List<RerankerProvider> rerankerProviders;

    @Resource
    private DeterministicRerankerProvider deterministicRerankerProvider;

    @Resource
    private ColbertScorer colbertScorer;

    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates,
                                         QueryIntent queryIntent, int topK,
                                         Long rerankingProviderName, String rerankingModelName) {
        if (CollUtil.isEmpty(candidates)) {
            return new ArrayList<>();
        }

        // ColBERT 粗排层：在主 reranker 之前进行 n-gram 粗排
        candidates = colbertCoarseRerank(query, candidates, topK * 3);

        RerankRequestContext context = RerankRequestContext.builder()
                .query(query)
                .providerName(rerankingProviderName)
                .modelName(rerankingModelName)
                .build();

        for (RerankerProvider provider : rerankerProviders) {
            if (provider instanceof DeterministicRerankerProvider || !provider.supports(context)) {
                continue;
            }
            long start = System.currentTimeMillis();
            try {
                List<RetrievalResult> results = provider.rerank(context, candidates, queryIntent, topK);
                log.debug("Reranker provider '{}' finished in {}ms", provider.name(), System.currentTimeMillis() - start);
                return results;
            } catch (Exception e) {
                log.warn("Reranker provider '{}' failed, falling back to deterministic rerank", provider.name(), e);
            }
        }

        return deterministicRerankerProvider.rerank(context, candidates, queryIntent, topK);
    }

    /**
     * ColBERT 粗排：使用 n-gram 重叠度进行快速粗排
     */
    private List<RetrievalResult> colbertCoarseRerank(String query, List<RetrievalResult> candidates, int limit) {
        try {
            List<org.springframework.ai.document.Document> docs = candidates.stream()
                    .map(r -> {
                        org.springframework.ai.document.Document doc = new org.springframework.ai.document.Document(r.getContent());
                        doc.getMetadata().put("segmentId", r.getSegmentId());
                        doc.getMetadata().put("score", r.getScore());
                        doc.getMetadata().put("source", r.getSource());
                        return doc;
                    })
                    .collect(Collectors.toList());

            List<org.springframework.ai.document.Document> reranked = colbertScorer.rerank(query, docs, limit);

            return reranked.stream()
                    .map(doc -> {
                        Map<String, Object> meta = doc.getMetadata();
                        return RetrievalResult.builder()
                                .segmentId((Long) meta.get("segmentId"))
                                .content(doc.getText())
                                .score((Double) meta.getOrDefault("score", 0.0))
                                .source((String) meta.getOrDefault("source", "colbert"))
                                .metadata(meta)
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("ColBERT coarse rerank failed, returning original candidates", e);
            return candidates;
        }
    }
}
