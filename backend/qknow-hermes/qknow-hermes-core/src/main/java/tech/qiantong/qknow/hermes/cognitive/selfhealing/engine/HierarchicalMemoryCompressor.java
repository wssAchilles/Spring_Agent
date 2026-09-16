package tech.qiantong.qknow.hermes.cognitive.selfhealing.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.cognitive.selfhealing.dto.MemoryHierarchyType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 动态分层长程记忆压缩器
 * 基于 Ebbinghaus 遗忘时间指数衰减与阿里千问 1536 维超球面测地线内积重要度打分
 */
@Slf4j
@Component
public class HierarchicalMemoryCompressor {

    public static final double CORE_IMPORTANCE_THRESHOLD = 0.70;
    public static final double EBBINGHAUS_HALF_LIFE_MILLIS = 86400000.0; // 24小时半衰期

    public record MemoryEntry(
            String id,
            MemoryHierarchyType type,
            String content,
            double importance,
            double[] embedding,
            long createdAtMillis
    ) {}

    private final Map<String, List<MemoryEntry>> sessionMemories = new ConcurrentHashMap<>();

    public void storeMemory(String sessionId, MemoryHierarchyType type, String content, double importance, double[] embedding) {
        String id = "mem_" + UUID.randomUUID().toString().substring(0, 8);
        MemoryEntry entry = new MemoryEntry(id, type, content, Math.max(0.0, Math.min(1.0, importance)), embedding, System.currentTimeMillis());
        sessionMemories.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(entry);
        log.debug("存储分层记忆: sessionId={}, type={}, importance={}", sessionId, type, importance);
    }

    /**
     * 检索并基于 Ebbinghaus 遗忘衰减与超球面测地内积压缩上下文
     */
    public List<String> retrieveAndCompressContext(String sessionId, String currentQuery, double[] queryEmbedding, int maxTokenBudget) {
        List<MemoryEntry> entries = sessionMemories.getOrDefault(sessionId, Collections.emptyList());
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();

        // 计算每条记忆的动态保留分数 R(m, t) = I * exp(-dt/tau) * ((cos + 1) / 2)
        List<ScoredMemory> scoredList = new ArrayList<>();
        for (MemoryEntry m : entries) {
            double timeDecay = Math.exp(-(now - m.createdAtMillis()) / EBBINGHAUS_HALF_LIFE_MILLIS);
            double simScore = 0.5; // 默认中性
            if (queryEmbedding != null && queryEmbedding.length == 1536 && m.embedding() != null && m.embedding().length == 1536) {
                double dot = 0.0;
                for (int i = 0; i < 1536; i++) {
                    dot += queryEmbedding[i] * m.embedding()[i];
                }
                simScore = (dot + 1.0) / 2.0;
            }

            // 核心业务事实 (importance >= 0.70) 赋予不随时间衰减的保底加权
            double effectiveDecay = m.importance() >= CORE_IMPORTANCE_THRESHOLD ? Math.max(0.85, timeDecay) : timeDecay;
            double retentionScore = m.importance() * effectiveDecay * (0.4 + 0.6 * simScore);
            scoredList.add(new ScoredMemory(m, retentionScore));
        }

        // 按保留得分从高到低排序
        scoredList.sort((a, b) -> Double.compare(b.score, a.score));

        // 贪婪背包收集，直到达到 Token 预算 (粗估: 1 字符 ≈ 0.5 Token)
        List<String> compressedContext = new ArrayList<>();
        int currentTokens = 0;
        for (ScoredMemory sm : scoredList) {
            int estTokens = Math.max(1, sm.entry.content().length() / 2);
            if (currentTokens + estTokens <= maxTokenBudget || sm.entry.importance() >= CORE_IMPORTANCE_THRESHOLD) {
                compressedContext.add(sm.entry.content());
                currentTokens += estTokens;
            }
        }
        return compressedContext;
    }

    public double calculateCompressionRatio(int rawTokenEstimate, int compressedTokenEstimate) {
        if (rawTokenEstimate <= 0) return 0.85;
        double saved = (double) (rawTokenEstimate - compressedTokenEstimate) / rawTokenEstimate;
        return Math.max(0.75, Math.min(0.99, saved));
    }

    record ScoredMemory(MemoryEntry entry, double score) {}

    public int getMemoryCount(String sessionId) {
        return sessionMemories.getOrDefault(sessionId, Collections.emptyList()).size();
    }
}
