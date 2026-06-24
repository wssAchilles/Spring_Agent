package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RagRerankService {

    @Resource
    private IAiModelApiService aiModelService;

    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates,
                                         QueryIntent queryIntent, int topK,
                                         Long rerankingProviderName, String rerankingModelName) {
        if (CollUtil.isEmpty(candidates)) {
            return new ArrayList<>();
        }

        if (rerankingProviderName != null && StrUtil.isNotBlank(rerankingModelName)) {
            try {
                return rerankWithModel(query, candidates, topK, rerankingProviderName, rerankingModelName);
            } catch (Exception e) {
                log.warn("DashScope rerank failed, falling back to deterministic rerank", e);
            }
        }

        return deterministicRerank(candidates, queryIntent, topK);
    }

    private List<RetrievalResult> rerankWithModel(String query, List<RetrievalResult> candidates,
                                                    int topK, Long providerName, String modelName) {
        DashScopeRerankModel rerankModel = aiModelService.getRerankModel(providerName, modelName);

        List<Document> documents = candidates.stream()
                .map(r -> Document.builder()
                        .id(String.valueOf(r.getSegmentId()))
                        .text(r.getContent())
                        .metadata(Map.of(
                                "segmentId", r.getSegmentId() != null ? r.getSegmentId() : 0L,
                                "documentId", r.getDocumentId() != null ? r.getDocumentId() : 0L,
                                "documentName", r.getDocumentName() != null ? r.getDocumentName() : "",
                                "answer", r.getAnswer() != null ? r.getAnswer() : "",
                                "source", r.getSource() != null ? r.getSource() : ""
                        ))
                        .build())
                .collect(Collectors.toList());

        RerankRequest rerankRequest = new RerankRequest(query, documents);
        RerankResponse rerankResponse = rerankModel.call(rerankRequest);
        List<DocumentWithScore> rerankedResults = rerankResponse.getResults();

        List<RetrievalResult> results = new ArrayList<>(rerankedResults.size());
        for (DocumentWithScore docWithScore : rerankedResults) {
            Document output = docWithScore.getOutput();
            Map<String, Object> metadata = output.getMetadata();
            results.add(RetrievalResult.builder()
                    .segmentId(toLong(metadata.get("segmentId")))
                    .documentId(toLong(metadata.get("documentId")))
                    .documentName(String.valueOf(metadata.getOrDefault("documentName", "")))
                    .content(output.getText())
                    .answer(String.valueOf(metadata.getOrDefault("answer", "")))
                    .score(docWithScore.getScore())
                    .source(String.valueOf(metadata.getOrDefault("source", "")))
                    .build());
        }

        return results.stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    private List<RetrievalResult> deterministicRerank(List<RetrievalResult> candidates,
                                                       QueryIntent queryIntent, int topK) {
        for (RetrievalResult candidate : candidates) {
            double bonus = 0.0;
            String docName = candidate.getDocumentName();
            String content = candidate.getContent();

            if (queryIntent.getDayNo() != null && StrUtil.isNotBlank(docName)) {
                String dayPattern = String.format("Day%02d", queryIntent.getDayNo());
                if (docName.contains(dayPattern) || docName.contains("Day" + queryIntent.getDayNo())) {
                    bonus += 3.0;
                }
            }

            if (StrUtil.isNotBlank(queryIntent.getDocName()) && StrUtil.isNotBlank(docName)) {
                if (docName.contains(queryIntent.getDocName())) {
                    bonus += 2.0;
                }
            }

            if (CollUtil.isNotEmpty(queryIntent.getKeywords()) && StrUtil.isNotBlank(content)) {
                for (String keyword : queryIntent.getKeywords()) {
                    if (content.contains(keyword)) {
                        bonus += 1.0;
                    }
                }
            }

            candidate.setScore(candidate.getScore() + bonus);
        }

        return candidates.stream()
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
}
