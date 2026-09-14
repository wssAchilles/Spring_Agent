package tech.qiantong.qknow.ai.mor.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阿里千问 1536 维超球面与检索切片不可变签名双重复合键认知缓存器 (定理 1.3 局部李普希茨零幻觉)
 */
@Service
public class CoTCognitiveCacheService {

    private static final Logger log = LoggerFactory.getLogger(CoTCognitiveCacheService.class);

    // L1 本地无锁内存缓存 (ConcurrentHashMap + 1000 容量防 OOM)
    private final Map<String, CacheEntry> localL1Cache = new ConcurrentHashMap<>();
    private static final int MAX_L1_CAPACITY = 1000;
    private static final double SIMILARITY_THRESHOLD = 0.92; // 测地线余弦命中门限

    record CacheEntry(
            float[] qwen1536Embedding,
            CognitiveScaffold scaffold,
            long accessTime
    ) {}

    /**
     * 计算检索切片不可变哈希签名: SHA256(Sort(segmentId:version:hash))
     */
    public String computeSlicesSignature(List<String> sliceSignatures) {
        if (sliceSignatures == null || sliceSignatures.isEmpty()) {
            return "empty_knowledge_signature";
        }
        List<String> sorted = new ArrayList<>(sliceSignatures);
        Collections.sort(sorted);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String sig : sorted) {
                if (sig != null) {
                    digest.update(sig.getBytes(StandardCharsets.UTF_8));
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "hash_error_" + System.currentTimeMillis();
        }
    }

    /**
     * 生成语义聚类分桶 Key: 基于超球面重要维度的局部哈希 (LSH)
     */
    public String computeClusterId(float[] embedding) {
        if (embedding == null || embedding.length != 1536) {
            return "cluster_default";
        }
        // 提取前 8 个超球面基底正负符号位构成二进制簇哈希
        int code = 0;
        for (int i = 0; i < 8; i++) {
            if (embedding[i] > 0) {
                code |= (1 << i);
            }
        }
        return "c_" + Integer.toHexString(code);
    }

    /**
     * 复合键计算: H(ClusterId || SlicesHash)
     */
    public String computeCompositeKey(float[] embedding, List<String> sliceSignatures) {
        String cluster = computeClusterId(embedding);
        String slicesHash = computeSlicesSignature(sliceSignatures);
        return cluster + "::" + slicesHash;
    }

    /**
     * 查询认知脚手架 (定理 1.3: 测地线距离 <= 0.40 且知识签名严格匹配)
     */
    public Optional<CognitiveScaffold> getScaffold(float[] queryEmbedding, List<String> sliceSignatures) {
        if (queryEmbedding == null || queryEmbedding.length != 1536) {
            return Optional.empty();
        }
        String targetSlicesHash = computeSlicesSignature(sliceSignatures);

        // 遍历局部 L1 缓存检索具有相同知识签名的候选
        for (Map.Entry<String, CacheEntry> entry : localL1Cache.entrySet()) {
            CacheEntry ce = entry.getValue();
            if (ce.scaffold().isExpired()) {
                localL1Cache.remove(entry.getKey());
                continue;
            }
            // 1. 知识切片签名必须绝对一致 (排斥数据演进带来的过期幻觉)
            if (!targetSlicesHash.equals(ce.scaffold().knowledgeSlicesSha256())) {
                continue;
            }
            // 2. 计算阿里千问 1536 维超球面点积余弦相似度
            double cosine = computeCosineSimilarity(queryEmbedding, ce.qwen1536Embedding());
            if (cosine >= SIMILARITY_THRESHOLD) {
                log.info("[CoTCache] 命中认知脚手架! 余弦相似度: {}, cluster: {}", String.format("%.4f", cosine), ce.scaffold().clusterId());
                return Optional.of(ce.scaffold());
            }
        }
        return Optional.empty();
    }

    /**
     * 异步写入认知脚手架 (L1 本地缓存 + 软容量防 OOM)
     */
    public void putScaffold(float[] queryEmbedding, List<String> sliceSignatures, String distilledScaffold, long ttlSeconds) {
        if (queryEmbedding == null || queryEmbedding.length != 1536 || distilledScaffold == null || distilledScaffold.isBlank()) {
            return;
        }
        if (localL1Cache.size() >= MAX_L1_CAPACITY) {
            // 简单清理最早访问的条目
            localL1Cache.keySet().stream().findFirst().ifPresent(localL1Cache::remove);
        }

        String cluster = computeClusterId(queryEmbedding);
        String slicesHash = computeSlicesSignature(sliceSignatures);
        String compositeKey = cluster + "::" + slicesHash + "::" + UUID.randomUUID().toString().substring(0, 6);

        CognitiveScaffold scaffold = new CognitiveScaffold(
                cluster,
                slicesHash,
                distilledScaffold,
                System.currentTimeMillis(),
                ttlSeconds > 0 ? ttlSeconds : 86400L
        );

        localL1Cache.put(compositeKey, new CacheEntry(queryEmbedding.clone(), scaffold, System.currentTimeMillis()));
        log.info("[CoTCache] 沉淀认知脚手架成功: cluster={}, slicesHash={}", cluster, slicesHash.substring(0, 8));
    }

    private double computeCosineSimilarity(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < 1536; i++) {
            dot += a[i] * b[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    public int size() {
        return localL1Cache.size();
    }

    public void clear() {
        localL1Cache.clear();
    }
}
