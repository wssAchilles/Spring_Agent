package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.constant.WeaviateConstant;
import tech.qiantong.qknow.ai.service.IVectorStoreService;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeBaseDO;
import tech.qiantong.qknow.module.kmc.dal.mapper.knowledgeBase.KmcKnowledgeBaseMapper;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.sim.VecSimNative;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class VectorRetriever {

    @Resource
    private IAiModelApiService aiModelService;

    @Resource
    private IVectorStoreService vectorStoreService;

    @Resource
    private KmcKnowledgeBaseMapper kmcKnowledgeBaseMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private volatile Boolean vectorStoreIdUuid;

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
                Map<String, Object> metadata = doc.getMetadata() != null ? doc.getMetadata() : Map.of();
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
                        .metadata(new LinkedHashMap<>(metadata))
                        .build());
            }
            rescoreWithVecSim(knowledgeBaseId, query, embeddingModel, results);
            return results;
        } catch (Exception e) {
            RagFallbackMonitor.record("vector_store", "empty_results", "vector retrieval failed: " + e.getMessage());
            log.error("Vector retrieval failed for knowledgeBaseId={}", knowledgeBaseId, e);
            return new ArrayList<>();
        }
    }

    private void rescoreWithVecSim(Long knowledgeBaseId, String query, EmbeddingModel embeddingModel,
                                   List<RetrievalResult> results) {
        if (CollUtil.isEmpty(results) || query == null || query.isBlank() || embeddingModel == null) {
            return;
        }
        try {
            float[] queryEmbedding = embedQuery(query, embeddingModel);
            if (queryEmbedding.length == 0) {
                return;
            }

            List<CandidateVector> candidateVectors = loadCandidateVectors(knowledgeBaseId, results);
            if (candidateVectors.isEmpty()) {
                return;
            }

            Map<String, float[]> byVectorId = new HashMap<>();
            Map<Long, float[]> bySegmentId = new HashMap<>();
            for (CandidateVector candidate : candidateVectors) {
                if (candidate.embedding() == null || candidate.embedding().length != queryEmbedding.length) {
                    continue;
                }
                if (hasText(candidate.vectorId())) {
                    byVectorId.putIfAbsent(candidate.vectorId(), candidate.embedding());
                }
                if (candidate.segmentId() != null) {
                    bySegmentId.putIfAbsent(candidate.segmentId(), candidate.embedding());
                }
            }

            List<RetrievalResult> alignedResults = new ArrayList<>();
            List<float[]> alignedEmbeddings = new ArrayList<>();
            for (RetrievalResult result : results) {
                float[] embedding = findEmbedding(result, byVectorId, bySegmentId);
                if (embedding != null) {
                    alignedResults.add(result);
                    alignedEmbeddings.add(embedding);
                }
            }
            if (alignedResults.isEmpty()) {
                return;
            }

            float[] corpus = flatten(alignedEmbeddings, queryEmbedding.length);
            float[] scores = safeCosineBatch(queryEmbedding, corpus, queryEmbedding.length);
            if (scores == null || scores.length != alignedResults.size()) {
                return;
            }

            for (int i = 0; i < alignedResults.size(); i++) {
                RetrievalResult result = alignedResults.get(i);
                Map<String, Object> metadata = result.getMetadata();
                if (metadata == null) {
                    metadata = new LinkedHashMap<>();
                    result.setMetadata(metadata);
                }
                metadata.putIfAbsent("pgvector_score", result.getScore());
                metadata.put("vecsim_score", (double) scores[i]);
                result.setScore(scores[i]);
            }
            results.sort(Comparator.comparingDouble(RetrievalResult::getScore).reversed());
        } catch (Exception e) {
            RagFallbackMonitor.record("jni", "pgvector_score", "vecsim rescore failed: " + e.getMessage());
            log.debug("VecSim rescore skipped for knowledgeBaseId={}: {}", knowledgeBaseId, e.getMessage());
        }
    }

    private float[] embedQuery(String query, EmbeddingModel embeddingModel) {
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(query), null));
        if (response == null || response.getResults().isEmpty() || response.getResults().get(0) == null) {
            return new float[0];
        }
        float[] output = response.getResults().get(0).getOutput();
        return output != null ? output : new float[0];
    }

    List<CandidateVector> loadCandidateVectors(Long knowledgeBaseId, List<RetrievalResult> results) {
        if (jdbcTemplate == null || knowledgeBaseId == null || CollUtil.isEmpty(results)) {
            return List.of();
        }

        List<String> vectorIds = results.stream()
                .map(RetrievalResult::getQmSegmentId)
                .filter(this::hasText)
                .filter(id -> !"null".equalsIgnoreCase(id))
                .distinct()
                .toList();
        List<String> segmentIds = results.stream()
                .map(RetrievalResult::getSegmentId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .toList();

        List<CandidateVector> candidates = new ArrayList<>();
        Set<String> seenVectorIds = new LinkedHashSet<>();
        Set<Long> seenSegmentIds = new LinkedHashSet<>();

        if (!vectorIds.isEmpty()) {
            List<CandidateVector> byId = loadCandidateVectorsById(knowledgeBaseId, vectorIds);
            for (CandidateVector candidate : byId) {
                if (candidate.vectorId() != null) {
                    seenVectorIds.add(candidate.vectorId());
                }
                if (candidate.segmentId() != null) {
                    seenSegmentIds.add(candidate.segmentId());
                }
                candidates.add(candidate);
            }
        }

        List<String> missingSegmentIds = segmentIds.stream()
                .filter(segmentId -> {
                    try {
                        return !seenSegmentIds.contains(Long.parseLong(segmentId));
                    } catch (NumberFormatException e) {
                        return true;
                    }
                })
                .toList();
        if (!missingSegmentIds.isEmpty()) {
            for (CandidateVector candidate : loadCandidateVectorsBySegmentId(knowledgeBaseId, missingSegmentIds)) {
                if (candidate.vectorId() != null && !seenVectorIds.add(candidate.vectorId())) {
                    continue;
                }
                candidates.add(candidate);
            }
        }

        return candidates;
    }

    private List<CandidateVector> loadCandidateVectorsById(Long knowledgeBaseId, List<String> vectorIds) {
        boolean uuidId = isVectorStoreIdUuid();
        List<Object> params = new ArrayList<>();
        String idPredicate;
        if (uuidId) {
            List<UUID> uuids = vectorIds.stream()
                    .map(this::toUuid)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (uuids.isEmpty()) {
                return List.of();
            }
            idPredicate = "id IN (" + placeholders(uuids.size()) + ")";
            params.addAll(uuids);
        } else {
            idPredicate = "id IN (" + placeholders(vectorIds.size()) + ")";
            params.addAll(vectorIds);
        }
        String sql = """
                SELECT id,
                       metadata->>'%s' AS segment_id,
                       embedding::text AS embedding_text
                FROM vector_store
                WHERE %s
                  AND metadata->>'%s' = ?
                """.formatted(
                WeaviateConstant.METADATA_FIELD_SEGMENT_ID,
                idPredicate,
                WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID);
        params.add(String.valueOf(knowledgeBaseId));
        return queryCandidateVectors(sql, params);
    }

    private List<CandidateVector> loadCandidateVectorsBySegmentId(Long knowledgeBaseId, List<String> segmentIds) {
        List<Object> params = new ArrayList<>(segmentIds);
        String sql = """
                SELECT id,
                       metadata->>'%s' AS segment_id,
                       embedding::text AS embedding_text
                FROM vector_store
                WHERE metadata->>'%s' IN (%s)
                  AND metadata->>'%s' = ?
                """.formatted(
                WeaviateConstant.METADATA_FIELD_SEGMENT_ID,
                WeaviateConstant.METADATA_FIELD_SEGMENT_ID,
                placeholders(segmentIds.size()),
                WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID);
        params.add(String.valueOf(knowledgeBaseId));
        return queryCandidateVectors(sql, params);
    }

    private List<CandidateVector> queryCandidateVectors(String sql, List<Object> params) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CandidateVector(
                rs.getString("id"),
                toLong(rs.getString("segment_id")),
                parsePgVector(rs.getString("embedding_text"))
        ), params.toArray());
    }

    boolean isVectorStoreIdUuid() {
        Boolean cached = vectorStoreIdUuid;
        if (cached != null) {
            return cached;
        }
        try {
            String udtName = jdbcTemplate.queryForObject("""
                    SELECT udt_name
                    FROM information_schema.columns
                    WHERE table_name = 'vector_store'
                      AND column_name = 'id'
                    """, String.class);
            boolean detected = "uuid".equalsIgnoreCase(udtName);
            vectorStoreIdUuid = detected;
            return detected;
        } catch (Exception e) {
            return false;
        }
    }

    private UUID toUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    float[] safeCosineBatch(float[] queryEmbedding, float[] corpus, int dim) {
        return VecSimNative.safeCosineBatch(queryEmbedding, corpus, dim);
    }

    private float[] findEmbedding(RetrievalResult result, Map<String, float[]> byVectorId, Map<Long, float[]> bySegmentId) {
        if (result == null) {
            return null;
        }
        if (hasText(result.getQmSegmentId())) {
            float[] embedding = byVectorId.get(result.getQmSegmentId());
            if (embedding != null) {
                return embedding;
            }
        }
        return result.getSegmentId() != null ? bySegmentId.get(result.getSegmentId()) : null;
    }

    private float[] flatten(List<float[]> embeddings, int dim) {
        long size = (long) embeddings.size() * dim;
        if (dim <= 0 || size > Integer.MAX_VALUE) {
            return new float[0];
        }
        float[] corpus = new float[(int) size];
        for (int i = 0; i < embeddings.size(); i++) {
            System.arraycopy(embeddings.get(i), 0, corpus, i * dim, dim);
        }
        return corpus;
    }

    static float[] parsePgVector(String vectorText) {
        if (vectorText == null) {
            return new float[0];
        }
        String text = vectorText.trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        if (text.isBlank()) {
            return new float[0];
        }
        String[] parts = text.split(",");
        float[] vector = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i].trim());
            }
            return vector;
        } catch (NumberFormatException e) {
            return new float[0];
        }
    }

    private String placeholders(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> "?")
                .collect(Collectors.joining(","));
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record CandidateVector(String vectorId, Long segmentId, float[] embedding) {
    }
}
