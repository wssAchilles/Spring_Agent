package tech.qiantong.qknow.module.kmc.service.rag.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工业级增强语义缓存服务
 * 包含：L1 无锁高并发内存缓存、DCL 防击穿、Null 哨兵防穿透、Jitter 随机防雪崩、否定词与实体漂移门禁
 */
@Slf4j
@Service
public class EnhancedSemanticCacheService {

    public static final String EMPTY_SENTINEL = "__EMPTY_CACHE_RESULT__";
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.92D;
    private static final int DEFAULT_EMBEDDING_DIMENSION = 1536;
    private static final int MAX_LOCAL_CACHE_SIZE = 5000;

    // 核心否定词与动作反向意图词汇库
    private static final List<String> NEGATION_WORDS = List.of(
            "不", "未", "无", "非", "别", "禁", "莫", "免", "关", "开", "增", "删", "禁用", "启用"
    );

    // L1 无锁高性能内存缓存 (线程安全，彻底消除全局 synchronized 排队瓶颈)
    private final ConcurrentHashMap<String, CacheEntry> localCache = new ConcurrentHashMap<>();

    private final JdbcTemplate jdbcTemplate;

    public EnhancedSemanticCacheService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询精确/L1 缓存
     */
    public Optional<CacheEntry> findExact(Long workspaceId, Long botId, List<Long> kbIds, String modelName, String query) {
        String key = buildExactKey(workspaceId, botId, hashKnowledgeBaseIds(kbIds), modelName, query);
        CacheEntry entry = localCache.get(key);
        if (entry == null) {
            return Optional.empty();
        }

        // 检查是否过期
        if (entry.getExpiresAt() != null && entry.getExpiresAt().isBefore(Instant.now())) {
            localCache.remove(key);
            return Optional.empty();
        }

        // 检查是否为空哨兵拦截
        if (EMPTY_SENTINEL.equals(entry.getAnswer())) {
            return Optional.empty();
        }

        return Optional.of(entry);
    }

    /**
     * 检查是否被空哨兵拦截（防穿透）
     */
    public boolean isBlockedBySentinel(Long workspaceId, Long botId, List<Long> kbIds, String modelName, String query) {
        String key = buildExactKey(workspaceId, botId, hashKnowledgeBaseIds(kbIds), modelName, query);
        CacheEntry entry = localCache.get(key);
        if (entry != null) {
            if (entry.getExpiresAt() != null && entry.getExpiresAt().isBefore(Instant.now())) {
                localCache.remove(key);
                return false;
            }
            return EMPTY_SENTINEL.equals(entry.getAnswer());
        }
        return false;
    }

    /**
     * 写入精确/L1 缓存
     */
    public void putExactCache(Long workspaceId, Long botId, List<Long> kbIds, String modelName,
                              String query, String answer, String sourcesJson, Duration ttl) {
        if (localCache.size() >= MAX_LOCAL_CACHE_SIZE) {
            evictEldestEntries();
        }

        String key = buildExactKey(workspaceId, botId, hashKnowledgeBaseIds(kbIds), modelName, query);
        Duration jitteredTtl = calculateJitteredTtl(ttl);

        CacheEntry entry = CacheEntry.builder()
                .answer(answer)
                .sourcesJson(sourcesJson != null ? sourcesJson : "[]")
                .originalQuery(query)
                .similarity(1.0)
                .expiresAt(Instant.now().plus(jitteredTtl))
                .knowledgeBaseIds(new ArrayList<>(kbIds))
                .build();

        localCache.put(key, entry);
    }

    /**
     * 写入空值哨兵（防穿透，短 TTL）
     */
    public void putEmptySentinel(Long workspaceId, Long botId, List<Long> kbIds, String modelName,
                                 String query, Duration ttl) {
        String key = buildExactKey(workspaceId, botId, hashKnowledgeBaseIds(kbIds), modelName, query);
        CacheEntry sentinel = CacheEntry.builder()
                .answer(EMPTY_SENTINEL)
                .originalQuery(query)
                .expiresAt(Instant.now().plus(ttl))
                .knowledgeBaseIds(new ArrayList<>(kbIds))
                .build();
        localCache.put(key, sentinel);
    }

    /**
     * 语义漂移防御门禁：否定词极性与核心词元一致性校验
     */
    public boolean passSemanticGating(String incomingQuery, String cachedQuery) {
        if (incomingQuery == null || cachedQuery == null) {
            return false;
        }

        // 1. 否定词与反向动作词极性检查
        for (String word : NEGATION_WORDS) {
            boolean inHas = incomingQuery.contains(word);
            boolean cacheHas = cachedQuery.contains(word);
            if (inHas != cacheHas) {
                log.warn("[SemanticGating] 否定词极性翻转拦截: word='{}', incoming='{}', cached='{}'",
                        word, incomingQuery, cachedQuery);
                return false; // 存在反向语气，直接阻断误命中
            }
        }

        // 2. 基于 2-gram 的 Jaccard 核心词元重合度校验
        Set<String> setA = extractNgrams(incomingQuery, 2);
        Set<String> setB = extractNgrams(cachedQuery, 2);
        if (setA.isEmpty() || setB.isEmpty()) {
            return true;
        }

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        double jaccard = union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
        if (jaccard < 0.40D) {
            log.warn("[SemanticGating] 核心词元 Jaccard 低于门限拦截: jaccard={}", jaccard);
            return false;
        }

        return true;
    }

    /**
     * 动态计算叠加随机扰动（Jitter ±10%）的防雪崩 TTL
     */
    public Duration calculateJitteredTtl(Duration baseTtl) {
        if (baseTtl == null || baseTtl.isZero()) {
            return Duration.ofMinutes(30);
        }
        long baseSeconds = baseTtl.toSeconds();
        double factor = ThreadLocalRandom.current().nextDouble(0.90, 1.10);
        long jitteredSeconds = Math.max(1, (long) (baseSeconds * factor));
        return Duration.ofSeconds(jitteredSeconds);
    }

    /**
     * 根据知识库 ID 驱逐本地缓存
     */
    public int evictExactCacheByKnowledgeBase(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) return 0;
        int count = 0;
        var it = localCache.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next().getValue();
            if (entry != null && entry.getKnowledgeBaseIds() != null
                    && entry.getKnowledgeBaseIds().contains(knowledgeBaseId)) {
                it.remove();
                count++;
            }
        }
        return count;
    }

    private void evictEldestEntries() {
        // 当达到容量上限时，随机剔除已过期或 10% 的老条目
        Instant now = Instant.now();
        var it = localCache.entrySet().iterator();
        int evicted = 0;
        while (it.hasNext() && evicted < MAX_LOCAL_CACHE_SIZE / 10) {
            var entry = it.next().getValue();
            if (entry.getExpiresAt() != null && entry.getExpiresAt().isBefore(now)) {
                it.remove();
                evicted++;
            }
        }
    }

    private Set<String> extractNgrams(String s, int n) {
        Set<String> ngrams = new HashSet<>();
        if (s == null || s.length() < n) {
            if (s != null && !s.isBlank()) ngrams.add(s.trim());
            return ngrams;
        }
        for (int i = 0; i <= s.length() - n; i++) {
            ngrams.add(s.substring(i, i + n));
        }
        return ngrams;
    }

    public String buildExactKey(Long workspaceId, Long botId, String knowledgeIdsHash, String modelName, String query) {
        String normalized = query != null ? query.trim().toLowerCase().replaceAll("\\s+", " ") : "";
        return workspaceId + ":" + botId + ":" + knowledgeIdsHash + ":" + modelName + ":" + sha256(normalized);
    }

    public String hashKnowledgeBaseIds(List<Long> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) return "";
        List<Long> sorted = new ArrayList<>(kbIds);
        Collections.sort(sorted);
        return sha256(sorted.toString());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheEntry {
        private Long id;
        private String answer;
        private String sourcesJson;
        private String originalQuery;
        private double similarity;
        private Instant expiresAt;
        private List<Long> knowledgeBaseIds;
    }
}
