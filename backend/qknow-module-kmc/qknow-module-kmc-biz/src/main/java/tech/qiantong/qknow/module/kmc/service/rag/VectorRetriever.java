package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.constant.WeaviateConstant;
import tech.qiantong.qknow.ai.service.IVectorStoreService;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeBaseDO;
import tech.qiantong.qknow.module.kmc.dal.mapper.knowledgeBase.KmcKnowledgeBaseMapper;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class VectorRetriever {

    @Resource
    private IAiModelApiService aiModelService;

    @Resource
    private IVectorStoreService vectorStoreService;

    @Resource
    private KmcKnowledgeBaseMapper kmcKnowledgeBaseMapper;

    public List<RetrievalResult> retrieve(Long knowledgeBaseId, String query, int topK) {
        return retrieve(knowledgeBaseId, query, topK, null);
    }

    public List<RetrievalResult> retrieve(Long knowledgeBaseId, String query, int topK, Integer dayNo) {
        KmcKnowledgeBaseDO kb = kmcKnowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            log.warn("Knowledge base not found: {}", knowledgeBaseId);
            return new ArrayList<>();
        }

        try {
            String providerStr = kb.getEmbeddingModelProvider();
            if (providerStr == null || providerStr.isBlank()) {
                log.warn("embeddingModelProvider is empty for knowledgeBaseId={}, falling back to default", knowledgeBaseId);
                providerStr = "1";
            }
            EmbeddingModel embeddingModel = aiModelService.getEmbeddingModel(
                    Long.valueOf(providerStr), kb.getEmbeddingModel());
            VectorStore vectorStore = vectorStoreService.getVectorStore(embeddingModel);

            FilterExpressionBuilder b = new FilterExpressionBuilder();
            Filter.Expression kbFilter = b.eq(WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID, knowledgeBaseId).build();
            Filter.Expression expression = kbFilter;

            if (dayNo != null) {
                Filter.Expression dayFilter = b.eq(WeaviateConstant.METADATA_FIELD_DAY_NO, dayNo).build();
                expression = new Filter.Expression(Filter.ExpressionType.AND, kbFilter, dayFilter);
                log.info("Vector 检索添加 day_no 过滤: dayNo={}", dayNo);
            }

            SearchRequest searchRequest = SearchRequest.builder()
                    .filterExpression(expression)
                    .topK(topK)
                    .query(query)
                    .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            if (CollUtil.isEmpty(documents)) {
                return new ArrayList<>();
            }

            List<RetrievalResult> results = new ArrayList<>(documents.size());
            for (Document doc : documents) {
                Map<String, Object> metadata = doc.getMetadata();
                results.add(RetrievalResult.builder()
                        .segmentId(toLong(metadata.get(WeaviateConstant.METADATA_FIELD_SEGMENT_ID)))
                        .qmSegmentId(String.valueOf(doc.getId()))
                        .parentSegmentId(stringValue(metadata.get("parent_segment_id")))
                        .documentId(toLong(metadata.get(WeaviateConstant.METADATA_FIELD_DOCUMENT_ID)))
                        .documentName(stringValue(metadata.get(WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME)))
                        .content(doc.getText())
                        .answer(String.valueOf(metadata.getOrDefault("answer", "")))
                        .score(doc.getScore() != null ? doc.getScore() : 0.0)
                        .source("vector")
                        .metadata(metadata)
                        .build());
            }
            return results;
        } catch (Exception e) {
            log.error("Vector retrieval failed for knowledgeBaseId={}", knowledgeBaseId, e);
            return new ArrayList<>();
        }
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

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
