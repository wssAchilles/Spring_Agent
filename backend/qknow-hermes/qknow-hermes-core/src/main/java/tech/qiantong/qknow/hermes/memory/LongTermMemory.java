package tech.qiantong.qknow.hermes.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;

/**
 * 向量化长期记忆。
 * 参考：CrewAI 记忆公式（54.4k⭐）
 * composite = 0.5 × similarity + 0.3 × decay + 0.2 × importance
 * decay = 0.5 ^ (age_days / 30)  # 30 天半衰期
 *
 * 增强：Consolidation 机制（相似记忆合并/更新/删除）
 */
@Slf4j
public class LongTermMemory {

    private static final double WEIGHT_SIMILARITY = 0.5;
    private static final double WEIGHT_DECAY = 0.3;
    private static final double WEIGHT_IMPORTANCE = 0.2;
    private static final double DECAY_HALF_LIFE_DAYS = 30.0;
    private static final double CONSOLIDATION_THRESHOLD = 0.85;

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    public LongTermMemory(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 存储记忆，带 Consolidation 检测
     * 如果发现相似记忆（cosine > 0.85），则合并而非新增
     */
    public void store(String content, Map<String, Object> metadata) {
        List<Document> similar = findSimilar(content, 3);
        for (Document existing : similar) {
            double score = existing.getScore() != null ? existing.getScore() : 0.0;
            // PgVector 返回 cosine distance (0=相同, 2=相反)，转换为 similarity
            double similarity = 1.0 - Math.min(score, 2.0) / 2.0;
            if (similarity >= CONSOLIDATION_THRESHOLD) {
                log.debug("Consolidation: merging with existing memory (similarity={})", similarity);
                Map<String, Object> updatedMetadata = new HashMap<>(existing.getMetadata());
                updatedMetadata.putAll(metadata);
                updatedMetadata.put("updated_at", System.currentTimeMillis());
                int prevCount = updatedMetadata.containsKey("consolidated_count")
                        ? ((Number) updatedMetadata.get("consolidated_count")).intValue() : 1;
                updatedMetadata.put("consolidated_count", prevCount + 1);
                try {
                    vectorStore.delete(List.of(existing.getId()));
                } catch (Exception e) {
                    log.debug("Failed to delete old memory during consolidation", e);
                }
                Document merged = Document.builder()
                        .text(existing.getText() + "\n---\n" + content)
                        .metadata(updatedMetadata)
                        .build();
                vectorStore.add(List.of(merged));
                return;
            }
        }

        // 无相似记忆，直接存储
        if (!metadata.containsKey("created_at")) {
            metadata.put("created_at", System.currentTimeMillis());
        }
        Document doc = Document.builder()
                .text(content)
                .metadata(metadata)
                .build();
        vectorStore.add(List.of(doc));
    }

    /**
     * 召回并按复合评分重排序
     */
    public List<Document> recall(String query, int topK) {
        return recall(query, topK, null);
    }

    /**
     * 召回并按复合评分重排序，支持 Scope 过滤
     */
    public List<Document> recall(String query, int topK, String scope) {
        int fetchSize = Math.max(topK * 3, 20);
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(query)
                .topK(fetchSize);

        SearchRequest request = requestBuilder.build();
        List<Document> results = vectorStore.similaritySearch(request);
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        List<Document> mutable = new ArrayList<>(results);

        // Scope 过滤
        if (scope != null && !scope.isBlank()) {
            mutable.removeIf(doc -> {
                String docScope = String.valueOf(doc.getMetadata().getOrDefault("scope", ""));
                return !docScope.equals(scope) && !docScope.startsWith(scope + ":");
            });
        }

        // 按复合评分重排序
        mutable.sort((a, b) -> {
            double scoreA = computeCompositeScore(a);
            double scoreB = computeCompositeScore(b);
            return Double.compare(scoreB, scoreA);
        });

        return mutable.size() > topK ? mutable.subList(0, topK) : mutable;
    }

    /**
     * 查找与给定内容相似的记忆
     */
    private List<Document> findSimilar(String content, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(content)
                .topK(topK)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return results != null ? results : Collections.emptyList();
    }

    /**
     * 复合评分：similarity + decay + importance
     */
    private double computeCompositeScore(Document doc) {
        double similarity = doc.getScore() != null ? doc.getScore() : 0.0;
        double decay = computeDecay(doc);
        double importance = getImportance(doc);
        return WEIGHT_SIMILARITY * similarity + WEIGHT_DECAY * decay + WEIGHT_IMPORTANCE * importance;
    }

    /**
     * 时间衰减：30 天半衰期
     */
    private double computeDecay(Document doc) {
        Object timestamp = doc.getMetadata().get("created_at");
        if (timestamp == null) return 1.0;
        try {
            long createdMs = Long.parseLong(timestamp.toString());
            double ageDays = (System.currentTimeMillis() - createdMs) / 86400000.0;
            return Math.pow(0.5, ageDays / DECAY_HALF_LIFE_DAYS);
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    /**
     * 获取重要性分数（默认 0.5，可通过 metadata 设置）
     */
    private double getImportance(Document doc) {
        Object imp = doc.getMetadata().get("importance");
        if (imp == null) return 0.5;
        try {
            return Double.parseDouble(imp.toString());
        } catch (NumberFormatException e) {
            return 0.5;
        }
    }
}
