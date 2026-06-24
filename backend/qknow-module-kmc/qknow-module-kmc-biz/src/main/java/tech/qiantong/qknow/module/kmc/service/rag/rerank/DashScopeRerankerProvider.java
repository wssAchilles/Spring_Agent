package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DashScopeRerankerProvider implements RerankerProvider {

    @Resource
    private IAiModelApiService aiModelService;

    @Override
    public String name() {
        return "dashscope";
    }

    @Override
    public boolean supports(RerankRequestContext context) {
        return context != null && context.getProviderName() != null && StrUtil.isNotBlank(context.getModelName());
    }

    @Override
    public List<RetrievalResult> rerank(RerankRequestContext context, List<RetrievalResult> candidates,
                                        QueryIntent queryIntent, int topK) {
        DashScopeRerankModel rerankModel = aiModelService.getRerankModel(context.getProviderName(), context.getModelName());

        List<Document> documents = candidates.stream()
                .map(r -> Document.builder()
                        .id(String.valueOf(r.getSegmentId()))
                        .text(r.getContent())
                        .metadata(Map.of(
                                "segmentId", r.getSegmentId() != null ? r.getSegmentId() : 0L,
                                "qmSegmentId", r.getQmSegmentId() != null ? r.getQmSegmentId() : "",
                                "parentSegmentId", r.getParentSegmentId() != null ? r.getParentSegmentId() : "",
                                "documentId", r.getDocumentId() != null ? r.getDocumentId() : 0L,
                                "documentName", r.getDocumentName() != null ? r.getDocumentName() : "",
                                "answer", r.getAnswer() != null ? r.getAnswer() : "",
                                "source", r.getSource() != null ? r.getSource() : ""
                        ))
                        .build())
                .collect(Collectors.toList());

        RerankResponse rerankResponse = rerankModel.call(new RerankRequest(context.getQuery(), documents));
        List<DocumentWithScore> rerankedResults = rerankResponse.getResults();

        List<RetrievalResult> results = new ArrayList<>(rerankedResults.size());
        for (DocumentWithScore docWithScore : rerankedResults) {
            Document output = docWithScore.getOutput();
            Map<String, Object> metadata = output.getMetadata();
            results.add(RetrievalResult.builder()
                    .segmentId(toLong(metadata.get("segmentId")))
                    .qmSegmentId(String.valueOf(metadata.getOrDefault("qmSegmentId", "")))
                    .parentSegmentId(blankToNull(String.valueOf(metadata.getOrDefault("parentSegmentId", ""))))
                    .documentId(toLong(metadata.get("documentId")))
                    .documentName(String.valueOf(metadata.getOrDefault("documentName", "")))
                    .content(output.getText())
                    .answer(String.valueOf(metadata.getOrDefault("answer", "")))
                    .score(docWithScore.getScore())
                    .source(String.valueOf(metadata.getOrDefault("source", "")))
                    .metadata(metadata)
                    .build());
        }

        return results.stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long l) {
            return l;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value;
    }
}
