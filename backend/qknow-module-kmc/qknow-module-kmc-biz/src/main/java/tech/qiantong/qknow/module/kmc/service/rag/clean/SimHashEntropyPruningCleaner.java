package tech.qiantong.qknow.module.kmc.service.rag.clean;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 26: 纯 Java 内存 64 位 SimHash 倒排查重与香农信息熵噪声剪枝算子
 * 包含：字符级香农信息熵双阈值校验、64位 SimHash 指纹生成、4段16位内存倒排分桶与汉明距离 <= 3 近似去重。
 */
@Slf4j
@Component
public class SimHashEntropyPruningCleaner {

    public static final int HAMMING_THRESHOLD = 3;
    public static final double MIN_ENTROPY_THRESHOLD = 2.5; // 低于 2.5 为低熵模板/占位符
    public static final double MAX_ENTROPY_THRESHOLD = 6.8; // 高于 6.8 为随机密文/二进制乱码

    /**
     * 4 段 16 位分桶倒排索引 (Pigeonhole Principle: 若汉明距离 <= 3，则至少有 1 个 16 位片段完全相同)
     */
    @SuppressWarnings("unchecked")
    private final Map<Integer, List<Long>>[] buckets = new Map[4];

    public SimHashEntropyPruningCleaner() {
        for (int i = 0; i < 4; i++) {
            buckets[i] = new ConcurrentHashMap<>();
        }
    }

    /**
     * 1. 计算字符级香农信息熵 H(X) = -sum(p * log2(p))
     *
     * @param text 输入切片文本
     * @return 字符香农信息熵 (bits/char)
     */
    public double calculateShannonEntropy(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        Map<Character, Integer> freqMap = new HashMap<>();
        for (char c : text.toCharArray()) {
            freqMap.merge(c, 1, Integer::sum);
        }
        double totalLen = text.length();
        double entropy = 0.0;
        for (int count : freqMap.values()) {
            double p = count / totalLen;
            entropy -= p * (Math.log(p) / Math.log(2.0));
        }
        return entropy;
    }

    /**
     * 2. 计算 64 位 SimHash 指纹
     *
     * @param tokens 切片分词列表
     * @return 64 位二进制签名
     */
    public long computeSimHash(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return 0L;
        }
        int[] vector = new int[64];
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            long hash = fnv1a64(token.trim().toLowerCase(Locale.ROOT));
            int weight = Math.max(1, Math.min(5, token.length())); // 词长加权
            for (int i = 0; i < 64; i++) {
                if (((hash >> i) & 1L) == 1L) {
                    vector[i] += weight;
                } else {
                    vector[i] -= weight;
                }
            }
        }
        long fingerprint = 0L;
        for (int i = 0; i < 64; i++) {
            if (vector[i] > 0) {
                fingerprint |= (1L << i);
            }
        }
        return fingerprint;
    }

    /**
     * 重载：直接从文本提取简单 Token 计算 SimHash
     */
    public long computeSimHash(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        List<String> tokens = extractSimpleTokens(text);
        return computeSimHash(tokens);
    }

    /**
     * 计算两个 64 位指纹的汉明距离 (异或求 1 的个数)
     */
    public static int hammingDistance(long h1, long h2) {
        return Long.bitCount(h1 ^ h2);
    }

    /**
     * 3. 内存极速查重判断 (4 段 16 位分桶，汉明距离 <= 3)
     * 若存在近似重复则返回 true；否则注册该指纹至倒排桶并返回 false
     *
     * @param fingerprint 64 位指纹
     * @return 是否与已有指纹近似重复
     */
    public boolean isDuplicateAndRegister(long fingerprint) {
        int[] chunks = new int[4];
        for (int i = 0; i < 4; i++) {
            chunks[i] = (int) ((fingerprint >> (i * 16)) & 0xFFFF);
        }

        // 步骤 A: 优先在 4 个桶中查找可能碰撞的候选集合
        for (int i = 0; i < 4; i++) {
            List<Long> candidates = buckets[i].get(chunks[i]);
            if (candidates != null) {
                // 仅需检查候选集合
                synchronized (candidates) {
                    for (Long candidate : candidates) {
                        if (hammingDistance(fingerprint, candidate) <= HAMMING_THRESHOLD) {
                            return true; // 发现近似重复
                        }
                    }
                }
            }
        }

        // 步骤 B: 未发现近似重复，将指纹注册至 4 个对应分桶
        for (int i = 0; i < 4; i++) {
            buckets[i].computeIfAbsent(chunks[i], k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(fingerprint);
        }
        return false;
    }

    /**
     * 4. 切片综合清洗与去重预检总门禁
     *
     * @param text   切片原文
     * @param tokens 分词列表 (可选)
     * @return true: 保留入库; false: 剪枝过滤
     */
    public boolean shouldKeepSegment(String text, List<String> tokens) {
        if (text == null || text.trim().length() < 20) {
            return false;
        }
        // 香农熵双阈值校验
        double entropy = calculateShannonEntropy(text);
        if (entropy < MIN_ENTROPY_THRESHOLD || entropy > MAX_ENTROPY_THRESHOLD) {
            log.debug("切片被香农熵门禁剪枝: H={}", entropy);
            return false;
        }
        // SimHash 查重门禁
        List<String> tkList = (tokens != null && !tokens.isEmpty()) ? tokens : extractSimpleTokens(text);
        long fingerprint = computeSimHash(tkList);
        return !isDuplicateAndRegister(fingerprint);
    }

    /**
     * 清空内存倒排分桶 (供单测隔离使用)
     */
    public void resetBuckets() {
        for (int i = 0; i < 4; i++) {
            buckets[i].clear();
        }
    }

    /**
     * 校验文本香农信息熵是否处于合理语义区间 [MIN_ENTROPY_THRESHOLD, MAX_ENTROPY_THRESHOLD]
     */
    public boolean isValidTextByEntropy(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        double entropy = calculateShannonEntropy(text);
        return entropy >= MIN_ENTROPY_THRESHOLD && entropy <= MAX_ENTROPY_THRESHOLD;
    }

    /**
     * 计算两个指纹的汉明距离实例方法封装
     */
    public int calculateHammingDistance(long h1, long h2) {
        return hammingDistance(h1, h2);
    }

    /**
     * 将指纹注册至 4 个倒排桶中 (不查重)
     */
    public void indexSimHash(long fingerprint) {
        for (int i = 0; i < 4; i++) {
            int chunk = (int) ((fingerprint >> (i * 16)) & 0xFFFF);
            buckets[i].computeIfAbsent(chunk, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(fingerprint);
        }
    }

    /**
     * 将指纹从 4 个倒排桶中注销移除 (Machine Unlearning 级联反注册)
     *
     * @param fingerprint 待注销指纹
     * @return 是否成功注销至少一个桶中的记录
     */
    public boolean unindexSimHash(long fingerprint) {
        boolean removedAny = false;
        for (int i = 0; i < 4; i++) {
            int chunk = (int) ((fingerprint >> (i * 16)) & 0xFFFF);
            List<Long> candidates = buckets[i].get(chunk);
            if (candidates != null) {
                synchronized (candidates) {
                    boolean removed = candidates.remove(Long.valueOf(fingerprint));
                    removedAny |= removed;
                    if (candidates.isEmpty()) {
                        buckets[i].remove(chunk);
                    }
                }
            }
        }
        return removedAny;
    }

    /**
     * 查询倒排桶中是否存在近似重复指纹 (汉明距离 <= 3)
     */
    public boolean isNearDuplicate(long fingerprint) {
        for (int i = 0; i < 4; i++) {
            int chunk = (int) ((fingerprint >> (i * 16)) & 0xFFFF);
            List<Long> candidates = buckets[i].get(chunk);
            if (candidates != null) {
                synchronized (candidates) {
                    for (Long candidate : candidates) {
                        if (hammingDistance(fingerprint, candidate) <= HAMMING_THRESHOLD) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 简易中文与英文分词提取器 (先进行标点与空白归一化，再提取词元与 2-gram)
     */
    private List<String> extractSimpleTokens(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        // 1. 全面归一化标点符号与特殊空白字符
        String cleaned = text.replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s，。！？；：、“”‘’（）《》【】……—～·]+", " ").trim();
        String[] words = cleaned.split("\\s+");
        for (String w : words) {
            if (!w.isBlank()) {
                tokens.add(w.toLowerCase(Locale.ROOT));
            }
        }

        // 2. 在完全消除标点的纯净文本上提取连续 2-gram 字符特征，捕获抗扰动语义
        String pureText = cleaned.replaceAll("\\s+", "");
        for (int i = 0; i < pureText.length() - 1; i++) {
            tokens.add(pureText.substring(i, i + 2).toLowerCase(Locale.ROOT));
        }

        return tokens;
    }

    /**
     * 64 位 FNV-1a 哈希算法
     */
    private static long fnv1a64(String str) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < str.length(); i++) {
            hash ^= str.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
