package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DeterministicRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankRequestContext;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankerProvider;

import jakarta.annotation.Resource;
import java.util.*;

@Slf4j
@Component
public class RagRerankService {

    @Resource
    private List<RerankerProvider> rerankerProviders;

    @Resource
    private DeterministicRerankerProvider deterministicRerankerProvider;

    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates,
                                         QueryIntent queryIntent, int topK,
                                         Long rerankingProviderName, String rerankingModelName) {
        if (CollUtil.isEmpty(candidates)) {
            return new ArrayList<>();
        }

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
}
