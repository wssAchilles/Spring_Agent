package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.List;

public interface RerankerProvider {

    String name();

    boolean supports(RerankRequestContext context);

    List<RetrievalResult> rerank(RerankRequestContext context, List<RetrievalResult> candidates,
                                 QueryIntent queryIntent, int topK);
}
