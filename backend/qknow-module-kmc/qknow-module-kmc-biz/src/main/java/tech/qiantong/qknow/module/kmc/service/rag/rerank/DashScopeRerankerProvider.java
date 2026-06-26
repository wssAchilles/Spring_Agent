package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import cn.hutool.core.util.StrUtil;
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

/**
 * DashScope 重排序提供者
 * 使用自定义 HTTP 客户端调用 DashScope Rerank API，消除对 Alibaba SDK 的直接依赖
 */
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
        // 获取 API Key（providerName 是 keyId）
        String apiKey = aiModelService.getApiKeyById(context.getProviderName());
        if (StrUtil.isBlank(apiKey)) {
            throw new RuntimeException("DashScope API Key not found for provider: " + context.getProviderName());
        }

        // 构建 Document 列表
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

        // 调用 HTTP Rerank 客户端
        DashScopeRerankClient client = new DashScopeRerankClient(apiKey, context.getModelName());
        List<DashScopeRerankClient.RerankResult> rerankedResults = client.rerank(
                context.getQuery(), documents, topK);

        // 转换为 RetrievalResult
        List<RetrievalResult> results = new ArrayList<>(rerankedResults.size());
        for (DashScopeRerankClient.RerankResult rerankResult : rerankedResults) {
            Document output = rerankResult.document();
            Map<String, Object> metadata = output.getMetadata();
            results.add(RetrievalResult.builder()
                    .segmentId(toLong(metadata.get("segmentId")))
                    .qmSegmentId(String.valueOf(metadata.getOrDefault("qmSegmentId", "")))
                    .parentSegmentId(blankToNull(String.valueOf(metadata.getOrDefault("parentSegmentId", ""))))
                    .documentId(toLong(metadata.get("documentId")))
                    .documentName(String.valueOf(metadata.getOrDefault("documentName", "")))
                    .content(output.getText())
                    .answer(String.valueOf(metadata.getOrDefault("answer", "")))
                    .score(rerankResult.score())
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
        if (value == null) return null;
        if (value instanceof Long l) return l;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException e) { return null; }
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value;
    }
}
