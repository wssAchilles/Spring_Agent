package tech.qiantong.qknow.hermes.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 向量化长期记忆。
 * 遵循 Phase 21 理论与架构体系：
 * 1. 动态艾宾浩斯强化衰减模型 (Theorem 1.1 & 1.2)
 * 2. 实体关系图 2-Hop 激活扩散 (Theorem 2.1 ~ 2.3)
 * 3. 多目标 Pareto 排序与温度 Sigmoid 余弦标定 (Theorem 3.1 & 3.2)
 *    Score = 0.40 * Sim_cal + 0.25 * R(t) + 0.15 * Importance + 0.20 * C_graph
 * 4. SHA-256 内容签名幂等防重
 */
@Slf4j
public class LongTermMemory {

    private static final double WEIGHT_SIMILARITY = 0.40;
    private static final double WEIGHT_DECAY = 0.25;
    private static final double WEIGHT_IMPORTANCE = 0.15;
    private static final double WEIGHT_GRAPH = 0.20;

    private static final double SIGMOID_TAU = 0.65;
    private static final double SIGMOID_TEMPERATURE = 0.15;
    private static final double CONSOLIDATION_THRESHOLD = 0.85;

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final org.springframework.ai.chat.model.ChatModel chatModel;
    private final UserMemoryGraphService graphService;

    public LongTermMemory(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this(vectorStore, embeddingModel, null, null);
    }

    public LongTermMemory(VectorStore vectorStore, EmbeddingModel embeddingModel,
                          org.springframework.ai.chat.model.ChatModel chatModel) {
        this(vectorStore, embeddingModel, chatModel, null);
    }

    public LongTermMemory(VectorStore vectorStore, EmbeddingModel embeddingModel,
                          org.springframework.ai.chat.model.ChatModel chatModel,
                          UserMemoryGraphService graphService) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.graphService = graphService;
    }

    /**
     * 计算文本内容的 SHA-256 摘要
     */
    public String computeSha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.warn("Failed to compute SHA-256 for memory content, fallback to hashCode", e);
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * 存储记忆，带 SHA-256 幂等拦截与 Consolidation 检测
     */
    public void store(String content, Map<String, Object> metadata) {
        if (vectorStore == null || content == null || content.isBlank()) {
            log.debug("LongTermMemory skipped: vectorStore missing or content blank");
            return;
        }
        if (!hasCompleteIdentity(metadata)) {
            log.warn("LongTermMemory skipped: incomplete identity metadata");
            return;
        }

        String contentHash = computeSha256(content);
        metadata.put("content_hash", contentHash);

        long now = System.currentTimeMillis();
        if (!metadata.containsKey("created_at")) {
            metadata.put("created_at", now);
        }
        if (!metadata.containsKey("last_retrieved_at")) {
            metadata.put("last_retrieved_at", now);
        }
        if (!metadata.containsKey("recall_count")) {
            metadata.put("recall_count", 0);
        }
        if (!metadata.containsKey("memory_strength")) {
            metadata.put("memory_strength", 30.0);
        }

        List<Document> similar = findSimilar(content, 3);
        for (Document existing : similar) {
            if (!hasSameIdentity(existing.getMetadata(), metadata)) {
                continue;
            }

            // 1. 完全相同内容或相同 SHA-256 签名，幂等阻断不再重复写入
            String existingHash = String.valueOf(existing.getMetadata().getOrDefault("content_hash", ""));
            String existingText = existing.getText() != null ? existing.getText().trim() : "";
            if (contentHash.equals(existingHash) || content.trim().equals(existingText)) {
                log.debug("LongTermMemory store skipped: identical content or SHA-256 hash already exists: hash={}", contentHash);
                return;
            }

            // 2. 相似度较高时执行 Consolidation 合并
            double score = existing.getScore() != null ? existing.getScore() : 0.0;
            // PgVector 返回 cosine distance (0=相同, 2=相反)，转换为 similarity
            double similarity = 1.0 - Math.min(score, 2.0) / 2.0;
            if (similarity >= CONSOLIDATION_THRESHOLD) {
                if (chatModel != null && !isSemanticallyEquivalent(existing.getText(), content)) {
                    log.debug("Consolidation skipped: LLM judged not equivalent (similarity={})", similarity);
                    continue;
                }
                log.debug("Consolidation: merging with existing memory (similarity={})", similarity);
                Map<String, Object> updatedMetadata = new HashMap<>(existing.getMetadata());
                updatedMetadata.putAll(metadata);
                updatedMetadata.put("updated_at", now);
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
        Document doc = Document.builder()
                .text(content)
                .metadata(metadata)
                .build();
        vectorStore.add(List.of(doc));
    }

    private boolean hasCompleteIdentity(Map<String, Object> metadata) {
        return metadata != null
                && isPositiveId(metadata.get("sessionId"))
                && isPositiveId(metadata.get("userId"))
                && metadata.get("scope") != null
                && metadata.get("scope").toString().matches("workspace:[1-9]\\d*:bot:[1-9]\\d*");
    }

    private boolean hasSameIdentity(Map<String, Object> existing, Map<String, Object> incoming) {
        return existing != null
                && Objects.equals(existing.get("userId"), incoming.get("userId"))
                && Objects.equals(existing.get("scope"), incoming.get("scope"));
    }

    private boolean isPositiveId(Object value) {
        return value != null && value.toString().matches("[1-9]\\d*");
    }

    /**
     * 召回并按多目标 Pareto 综合评分重排序
     */
    public List<Document> recall(String query, int topK) {
        return recall(query, topK, null);
    }

    /**
     * 召回并按多目标 Pareto 综合评分重排序，支持 Scope 过滤与图谱 2-Hop 激活扩散
     */
    public List<Document> recall(String query, int topK, String scope) {
        if (vectorStore == null) {
            return Collections.emptyList();
        }
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

        if (mutable.isEmpty()) {
            return Collections.emptyList();
        }

        // 图谱激活扩散检索
        Map<String, Double> graphActivations = Collections.emptyMap();
        if (graphService != null) {
            String userId = extractUserId(mutable);
            List<String> seedNames = extractSeedEntities(query, mutable);
            graphActivations = graphService.spreadActivation(
                    userId != null ? userId : "default_user",
                    scope != null ? scope : "default_scope",
                    seedNames,
                    0.4, 10, 5, 0.02
            );
        }

        final Map<String, Double> finalGraphActivations = graphActivations;

        // 按多目标 Pareto 综合评分重排序
        mutable.sort((a, b) -> {
            double scoreA = computeCompositeScore(a, finalGraphActivations);
            double scoreB = computeCompositeScore(b, finalGraphActivations);
            return Double.compare(scoreB, scoreA);
        });

        List<Document> topDocs = mutable.size() > topK ? mutable.subList(0, topK) : mutable;

        // 异步/内存唤醒强化更新
        reinforceRetrievedMemories(topDocs);

        return topDocs;
    }

    private String extractUserId(List<Document> docs) {
        for (Document doc : docs) {
            Object uId = doc.getMetadata().get("userId");
            if (uId != null && !uId.toString().isBlank()) {
                return uId.toString();
            }
        }
        return null;
    }

    private List<String> extractSeedEntities(String query, List<Document> docs) {
        Set<String> seeds = new LinkedHashSet<>();
        if (query != null && !query.isBlank()) {
            seeds.add(query.trim());
            for (String part : query.split("[\\s,;，；]+")) {
                if (!part.isBlank() && part.length() > 1) {
                    seeds.add(part.trim());
                }
            }
        }
        for (Document doc : docs) {
            Object entitiesObj = doc.getMetadata().get("entities");
            if (entitiesObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        seeds.add(item.toString().trim());
                    }
                }
            }
        }
        return new ArrayList<>(seeds);
    }

    /**
     * 查找与给定内容相似的记忆
     */
    private List<Document> findSimilar(String content, int topK) {
        if (vectorStore == null) {
            return Collections.emptyList();
        }
        SearchRequest request = SearchRequest.builder()
                .query(content)
                .topK(topK)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return results != null ? results : Collections.emptyList();
    }

    /**
     * 多目标 Pareto 综合评分函数 (Theorem 3.1 & 3.2):
     * Score = 0.40 * Sim_cal + 0.25 * R(t) + 0.15 * Importance + 0.20 * C_graph
     */
    private double computeCompositeScore(Document doc, Map<String, Double> graphActivations) {
        double rawScore = doc.getScore() != null ? doc.getScore() : 0.0;
        // 归一化余弦相似度并做温度 Sigmoid 标定
        double similarity = rawScore <= 1.0 && rawScore >= 0.0 ? rawScore : Math.max(0.0, 1.0 - Math.min(rawScore, 2.0) / 2.0);
        double simCalibrated = 1.0 / (1.0 + Math.exp(-(similarity - SIGMOID_TAU) / SIGMOID_TEMPERATURE));

        // 动态艾宾浩斯强化留存率 R(t)
        double retention = computeRetention(doc);

        // 重要度
        double importance = getImportance(doc);

        // 实体图谱 2-Hop 激活关联度
        double cGraph = computeGraphRelevance(doc, graphActivations);

        return WEIGHT_SIMILARITY * simCalibrated
                + WEIGHT_DECAY * retention
                + WEIGHT_IMPORTANCE * importance
                + WEIGHT_GRAPH * cGraph;
    }

    private double computeRetention(Document doc) {
        Object timestamp = doc.getMetadata().get("created_at");
        if (timestamp == null) return 1.0;
        try {
            long createdMs = Long.parseLong(timestamp.toString());
            int recallCount = doc.getMetadata().containsKey("recall_count")
                    ? ((Number) doc.getMetadata().get("recall_count")).intValue() : 0;
            double strength = doc.getMetadata().containsKey("memory_strength")
                    ? ((Number) doc.getMetadata().get("memory_strength")).doubleValue() : 30.0;
            double importance = getImportance(doc);
            return DynamicEbbinghausDecay.computeRetention(createdMs, recallCount, strength, importance);
        } catch (Exception e) {
            return 1.0;
        }
    }

    private double computeGraphRelevance(Document doc, Map<String, Double> graphActivations) {
        if (graphActivations == null || graphActivations.isEmpty()) {
            return 0.0;
        }
        double score = 0.0;
        Object entitiesObj = doc.getMetadata().get("entities");
        if (entitiesObj instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    Double act = graphActivations.get(item.toString());
                    if (act != null) {
                        score += act;
                    }
                }
            }
        }
        return Math.min(1.0, score);
    }

    private double getImportance(Document doc) {
        Object imp = doc.getMetadata().get("importance");
        if (imp == null) return 0.5;
        try {
            return Math.min(1.0, Math.max(0.0, Double.parseDouble(imp.toString())));
        } catch (NumberFormatException e) {
            return 0.5;
        }
    }

    private void reinforceRetrievedMemories(List<Document> docs) {
        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata();
            int currentRecall = meta.containsKey("recall_count") ? ((Number) meta.get("recall_count")).intValue() : 0;
            double currentStrength = meta.containsKey("memory_strength") ? ((Number) meta.get("memory_strength")).doubleValue() : 30.0;
            int nextRecall = currentRecall + 1;
            double nextStrength = DynamicEbbinghausDecay.calculateNextStrength(currentStrength, nextRecall);
            meta.put("recall_count", nextRecall);
            meta.put("memory_strength", nextStrength);
            meta.put("last_retrieved_at", System.currentTimeMillis());
        }
    }

    /**
     * LLM 语义等价验证
     */
    private boolean isSemanticallyEquivalent(String text1, String text2) {
        try {
            String prompt = "判断以下两段文本是否表达相同或高度相似的含义。只回答 YES 或 NO。\n\n"
                    + "文本1: " + text1.substring(0, Math.min(200, text1.length())) + "\n\n"
                    + "文本2: " + text2.substring(0, Math.min(200, text2.length()));
            var response = chatModel.call(new org.springframework.ai.chat.prompt.Prompt(
                    List.of(new org.springframework.ai.chat.messages.UserMessage(prompt))));
            String answer = response.getResult().getOutput().getText().trim().toUpperCase();
            return answer.startsWith("YES");
        } catch (Exception e) {
            log.debug("LLM equivalence check failed, assuming equivalent", e);
            return true;
        }
    }
}
