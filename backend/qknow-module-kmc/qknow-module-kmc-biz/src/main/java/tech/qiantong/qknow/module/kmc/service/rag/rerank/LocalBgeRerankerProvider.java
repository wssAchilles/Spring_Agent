package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.List;

@Component
public class LocalBgeRerankerProvider implements RerankerProvider {

    @Override
    public String name() {
        return "local-bge";
    }

    @Override
    public boolean supports(RerankRequestContext context) {
        return false;
    }

    @Override
    public List<RetrievalResult> rerank(RerankRequestContext context, List<RetrievalResult> candidates,
                                        QueryIntent queryIntent, int topK) {
        throw new UnsupportedOperationException("Local BGE reranker is reserved but not enabled in phase 1");
    }
}
