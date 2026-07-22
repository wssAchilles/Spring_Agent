package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.service.IEmbeddingService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ColBERT 风格粗排层
 * 使用 token-level 延迟交互（MaxSim）进行粗排
 * [溯源] 算法优化指南 Phase 4: ColBERT MaxSim batch native kernel
 */
@Slf4j
@Component
public class ColbertScorer {

    private final ColbertConfig config;
    private final IEmbeddingService embeddingService;

    public ColbertScorer(ColbertConfig config, @Autowired(required = false) IEmbeddingService embeddingService) {
        this.config = config;
        this.embeddingService = embeddingService;
    }

    /**
     * 对文档进行 ColBERT 风格粗排
     * 使用 token-level 向量延迟交互 MaxSim
     */
    public List<Document> rerank(String query, List<Document> documents, int topK) {
        if (!config.isEnabled() || documents == null || documents.isEmpty()) {
            return documents;
        }

        List<double[]> queryVectors = encodeTokens(tokenize(query));

        List<List<double[]>> docVectorSets = new ArrayList<>(documents.size());
        List<Document> alignedDocs = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            List<String> docTokens = tokenize(doc.getText());
            if (config.getMaxTokensPerDoc() > 0 && docTokens.size() > config.getMaxTokensPerDoc()) {
                docTokens = docTokens.subList(0, config.getMaxTokensPerDoc());
            }
            docVectorSets.add(encodeTokens(docTokens));
            alignedDocs.add(doc);
        }
        double[] scores = computeMaxSimBatch(queryVectors, docVectorSets);

        List<ScoredDocument> scored = new ArrayList<>();
        for (int i = 0; i < alignedDocs.size(); i++) {
            scored.add(new ScoredDocument(alignedDocs.get(i), scores[i]));
        }

        scored.sort(Comparator.comparingDouble(ScoredDocument::getScore).reversed()
                .thenComparing(ColbertScorer::compareStableDocumentIds));

        List<Document> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            Document doc = scored.get(i).document;
            doc.getMetadata().put("colbert_score", scored.get(i).score);
            result.add(doc);
        }

        log.debug("ColBERT rerank: {} -> {} documents", documents.size(), result.size());
        return result;
    }

    private static int compareStableDocumentIds(ScoredDocument left, ScoredDocument right) {
        Long leftSegmentId = segmentId(left.document);
        Long rightSegmentId = segmentId(right.document);
        if (leftSegmentId != null || rightSegmentId != null) {
            if (leftSegmentId == null) {
                return 1;
            }
            if (rightSegmentId == null) {
                return -1;
            }
            int comparison = Long.compare(leftSegmentId, rightSegmentId);
            if (comparison != 0) {
                return comparison;
            }
        }
        return String.valueOf(left.document.getId()).compareTo(String.valueOf(right.document.getId()));
    }

    private static Long segmentId(Document document) {
        Object value = document.getMetadata() == null ? null : document.getMetadata().get("segmentId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.valueOf(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // Fall through to the stable document id.
            }
        }
        return null;
    }

    /**
     * MaxSim：每个查询 token 与所有文档 token 叉乘取最大相似度，再做平均。
     * [溯源] Phase 4: 优先使用 Rust batch JNI，降级到 Java 实现
     */
    private double[] computeMaxSimBatch(List<double[]> queryVectors, List<List<double[]>> docVectorSets) {
        double[] javaFallback = new double[docVectorSets.size()];
        if (queryVectors.isEmpty()) {
            return javaFallback;
        }
        if (ColbertNative.isAvailable()) {
            try {
                int dim = commonDimension(queryVectors, docVectorSets);
                if (dim > 0) {
                    float[] qFlat = flattenToFloat(queryVectors, dim);
                    BatchDocVectors batch = flattenDocsToFloat(docVectorSets, dim);
                    float[] nativeScores = ColbertNative.safeMaxsimBatch(
                            qFlat, batch.docTokens(), batch.offsets(), batch.lens(), queryVectors.size(), dim);
                    if (nativeScores != null && nativeScores.length == docVectorSets.size()) {
                        log.debug("[JNI] ColbertNative.maxsimBatch called, {} documents scored", nativeScores.length);
                        double[] scores = new double[nativeScores.length];
                        for (int i = 0; i < nativeScores.length; i++) {
                            scores[i] = nativeScores[i];
                        }
                        return scores;
                    }
                }
            } catch (LinkageError | RuntimeException e) {
                log.debug("ColbertNative batch JNI failed, falling back to Java: {}", e.getMessage());
            }
        } else {
            log.debug("[JNI] ColbertNative not available, using Java fallback");
        }

        for (int i = 0; i < docVectorSets.size(); i++) {
            javaFallback[i] = computeMaxSimJava(queryVectors, docVectorSets.get(i));
        }
        return javaFallback;
    }

    private double computeMaxSimJava(List<double[]> queryVectors, List<double[]> docVectors) {
        if (queryVectors.isEmpty() || docVectors.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double[] queryVector : queryVectors) {
            double best = -Double.MAX_VALUE;
            for (double[] docVector : docVectors) {
                double score = dot(queryVector, docVector);
                if (score > best) best = score;
            }
            sum += best;
        }
        return sum / queryVectors.size();
    }

    private int commonDimension(List<double[]> queryVectors, List<List<double[]>> docVectorSets) {
        int dim = queryVectors.get(0) != null ? queryVectors.get(0).length : 0;
        if (dim <= 0) return -1;
        for (double[] vector : queryVectors) {
            if (vector == null || vector.length != dim) return -1;
        }
        for (List<double[]> docVectors : docVectorSets) {
            for (double[] vector : docVectors) {
                if (vector == null || vector.length != dim) return -1;
            }
        }
        return dim;
    }

    private float[] flattenToFloat(List<double[]> vectors, int dim) {
        long size = (long) vectors.size() * dim;
        if (dim <= 0 || size > Integer.MAX_VALUE) return null;
        float[] result = new float[(int) size];
        for (int i = 0; i < vectors.size(); i++) {
            double[] v = vectors.get(i);
            for (int j = 0; j < dim; j++) {
                result[i * dim + j] = (float) v[j];
            }
        }
        return result;
    }

    private BatchDocVectors flattenDocsToFloat(List<List<double[]>> docVectorSets, int dim) {
        long tokenCount = 0;
        for (List<double[]> docVectors : docVectorSets) {
            tokenCount += docVectors.size();
        }
        long valueCount = tokenCount * dim;
        if (dim <= 0 || valueCount > Integer.MAX_VALUE) {
            return new BatchDocVectors(new float[0], new int[docVectorSets.size()], new int[docVectorSets.size()]);
        }
        float[] docTokens = new float[(int) valueCount];
        int[] offsets = new int[docVectorSets.size()];
        int[] lens = new int[docVectorSets.size()];
        int offset = 0;
        for (int i = 0; i < docVectorSets.size(); i++) {
            List<double[]> docVectors = docVectorSets.get(i);
            offsets[i] = offset;
            lens[i] = docVectors.size();
            for (double[] vector : docVectors) {
                for (int j = 0; j < dim; j++) {
                    docTokens[offset + j] = (float) vector[j];
                }
                offset += dim;
            }
        }
        return new BatchDocVectors(docTokens, offsets, lens);
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        // 保留 CJK 字符 + ASCII 单词 + 空格
        String normalized = text.toLowerCase().replaceAll("[^\\w\\s\\u4e00-\\u9fff\\u3400-\\u4dbf]", "")
                .replaceAll("\\s+", " ").trim();
        // 对 CJK 字符按字分割为 unigram，英文按空格分词
        List<String> tokens = new ArrayList<>();
        for (String part : normalized.split(" ")) {
            boolean hasCjk = part.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
            if (hasCjk && part.length() > 1) {
                for (int i = 0; i < part.length(); i++) {
                    tokens.add(String.valueOf(part.charAt(i)));
                }
            } else {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private List<double[]> encodeTokens(List<String> tokens) {
        if (tokens.isEmpty()) {
            return List.of();
        }
        if (embeddingService != null
                && config.getEmbeddingPlatform() != null && !config.getEmbeddingPlatform().isBlank()
                && config.getEmbeddingBaseUrl() != null && !config.getEmbeddingBaseUrl().isBlank()
                && config.getEmbeddingApiKey() != null && !config.getEmbeddingApiKey().isBlank()
                && config.getEmbeddingModel() != null && !config.getEmbeddingModel().isBlank()) {
            try {
                EmbeddingModel model = embeddingService.getEmbeddingModel(
                        config.getEmbeddingPlatform(),
                        config.getEmbeddingBaseUrl(),
                        config.getEmbeddingApiKey(),
                        config.getEmbeddingModel());
                EmbeddingResponse response = model.call(new EmbeddingRequest(tokens, null));
                if (response == null || response.getResults().isEmpty()) {
                    log.warn("Embedding API returned empty response, falling back to hash-based vectors");
                } else {
                    List<double[]> vectors = new ArrayList<>(response.getResults().size());
                    for (var output : response.getResults()) {
                        float[] emb = output.getOutput();
                        double[] vec = new double[emb.length];
                        for (int i = 0; i < emb.length; i++) {
                            vec[i] = emb[i];
                        }
                        normalize(vec);
                        vectors.add(vec);
                    }
                    return vectors;
                }
            } catch (Exception e) {
                log.warn("Embedding API call failed, falling back to hash-based vectors: {}", e.getMessage());
            }
        }
        List<double[]> vectors = new ArrayList<>();
        for (String token : tokens) {
            vectors.add(tokenVectorHash(token));
        }
        return vectors;
    }

    private double[] tokenVectorHash(String token) {
        int dimensions = Math.max(16, config.getDimensions());
        double[] vector = new double[dimensions];
        int hash = token.hashCode();
        for (int i = 0; i < dimensions; i++) {
            hash = 31 * hash + i;
            vector[i] = ((hash & 1) == 0 ? 1.0 : -1.0) * (1.0 + ((hash >>> 1) & 0x0F) / 16.0);
        }
        normalize(vector);
        return vector;
    }

    private void normalize(double[] vector) {
        double norm = 0.0;
        for (double value : vector) {
            norm += value * value;
        }
        if (norm == 0.0) {
            return;
        }
        double scale = 1.0 / Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
    }

    private double dot(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    @Data
    private static class ScoredDocument {
        private final Document document;
        private final double score;
    }

    private record BatchDocVectors(float[] docTokens, int[] offsets, int[] lens) {
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "hermes.rag.colbert")
    public static class ColbertConfig {
        private boolean enabled = false;
        private int ngramSize = 3;
        private int dimensions = 64;
        private String embeddingPlatform;
        private String embeddingBaseUrl;
        private String embeddingApiKey;
        private String embeddingModel;
        private int maxTokensPerDoc = 128;
    }
}
