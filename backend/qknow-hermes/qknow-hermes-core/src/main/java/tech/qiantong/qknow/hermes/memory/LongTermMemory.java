package tech.qiantong.qknow.hermes.memory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 向量化长期记忆。
 * 参考：CrewAI 记忆公式（54.4k⭐）
 * composite = 0.5 × similarity + 0.3 × decay + 0.2 × importance
 * decay = 0.5 ^ (age_days / 30)  # 30 天半衰期
 */
public class LongTermMemory {

    private static final double WEIGHT_SIMILARITY = 0.5;
    private static final double WEIGHT_DECAY = 0.3;
    private static final double WEIGHT_IMPORTANCE = 0.2;
    private static final double DECAY_HALF_LIFE_DAYS = 30.0;

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    public LongTermMemory(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    public void store(String content, Map<String, Object> metadata) {
        Document doc = Document.builder()
                .text(content)
                .metadata(metadata)
                .build();
        vectorStore.add(List.of(doc));
    }

    /**
     * 召回并按复合评分重排序
     * 参考 CrewAI 记忆系统：similarity + decay + importance
     */
    public List<Document> recall(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK * 2)  // 多取一些，重排后截断
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        // 创建可变副本（vectorStore 可能返回不可变列表）
        List<Document> mutable = new java.util.ArrayList<>(results);

        // 按复合评分重排序
        mutable.sort((a, b) -> {
            double scoreA = computeCompositeScore(a);
            double scoreB = computeCompositeScore(b);
            return Double.compare(scoreB, scoreA);
        });

        return mutable.size() > topK ? mutable.subList(0, topK) : mutable;
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
