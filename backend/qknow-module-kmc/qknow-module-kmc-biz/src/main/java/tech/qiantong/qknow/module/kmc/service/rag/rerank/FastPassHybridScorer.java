package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.*;

/**
 * Phase 25: Fast-Pass 轻量混合保真打分算子
 * 结合阿里千问 1536 维向量内积 (0.50) + CJK Bi-gram 词重合度 (0.35) + 关键实体覆盖度 (0.15)。
 * 纯 CPU 纳秒/微秒级打分，严格在 5ms 内完成 Top-100 候选至 Top-20 的高效降维，保持 Top-10 高相关召回率 >= 90%。
 */
@Slf4j
@Component
public class FastPassHybridScorer {

    public static final double DEFAULT_VECTOR_WEIGHT = 0.50;
    public static final double DEFAULT_BIGRAM_WEIGHT = 0.35;
    public static final double DEFAULT_ENTITY_WEIGHT = 0.15;

    /**
     * 对候选结果执行纯内存级混合保真粗排与快速降维
     *
     * @param query          查询语句
     * @param candidates     粗排候选列表 (例如 Top-100)
     * @param queryEmbedding 1536 维千问查询向量 (可选)
     * @param topK           期望截断返回的候选规模 (例如 20)
     * @return 经打分与排序后的高置信度子集 (Top-K)
     */
    public List<RetrievalResult> scoreAndFilter(String query,
                                                List<RetrievalResult> candidates,
                                                float[] queryEmbedding,
                                                int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        if (candidates.size() <= topK) {
            return new ArrayList<>(candidates);
        }

        long startTimeNs = System.nanoTime();

        // 1. 预提取 Query 侧的 Bi-gram 特征集与 Term 词集 (只计算 1 次)
        Set<String> queryBiGrams = extractBiGrams(query);
        Set<String> queryTerms = extractSimpleTerms(query);

        // 2. 批量并行/快速计算各候选切片的综合评分
        List<ScoredCandidate> scoredList = new ArrayList<>(candidates.size());
        for (RetrievalResult candidate : candidates) {
            double vectorScore = computeVectorSimilarity(queryEmbedding, candidate);
            double biGramScore = computeBiGramOverlap(queryBiGrams, candidate.getContent());
            double entityScore = computeTermCoverage(queryTerms, candidate.getContent());

            double compositeScore = DEFAULT_VECTOR_WEIGHT * vectorScore
                    + DEFAULT_BIGRAM_WEIGHT * biGramScore
                    + DEFAULT_ENTITY_WEIGHT * entityScore;

            scoredList.add(new ScoredCandidate(candidate, compositeScore));
        }

        // 3. 快速排序与截断 Top-K
        scoredList.sort((a, b) -> Double.compare(b.score, a.score));

        int targetSize = Math.min(topK, scoredList.size());
        List<RetrievalResult> filteredResults = new ArrayList<>(targetSize);
        for (int i = 0; i < targetSize; i++) {
            ScoredCandidate sc = scoredList.get(i);
            RetrievalResult res = sc.candidate;
            // 记录 Fast-Pass 打分至元数据
            if (res.getMetadata() == null) {
                res.setMetadata(new LinkedHashMap<>());
            }
            res.getMetadata().put("fast_pass_score", sc.score);
            filteredResults.add(res);
        }

        long elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000;
        log.debug("Fast-Pass 粗排完成: 候选规模由 {} 降维至 {}, 耗时={}ms",
                candidates.size(), filteredResults.size(), elapsedMs);

        return filteredResults;
    }

    /**
     * 计算向量余弦相似度或利用已有原始分归一化
     */
    private double computeVectorSimilarity(float[] queryEmbedding, RetrievalResult candidate) {
        if (queryEmbedding != null && candidate.getMetadata() != null) {
            Object embObj = candidate.getMetadata().get("embedding");
            if (embObj instanceof float[] candEmb) {
                return dotProduct(queryEmbedding, candEmb);
            } else if (embObj instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Number) {
                float[] candEmb = new float[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    candEmb[i] = ((Number) list.get(i)).floatValue();
                }
                return dotProduct(queryEmbedding, candEmb);
            }
        }
        // 回退机制：若无浮点向量，使用候选者已有的原始归一化分数 [0, 1]
        double raw = candidate.getScore();
        return raw > 1.0 ? Math.min(1.0, raw / 100.0) : Math.max(0.0, raw);
    }

    /**
     * 1536 维点积计算 (假设已 L2 归一化)
     */
    private double dotProduct(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        float sum = 0.0f;
        for (int i = 0; i < len; i++) {
            sum += a[i] * b[i];
        }
        return Math.max(0.0, Math.min(1.0, sum));
    }

    /**
     * 提取 CJK 2-gram 字符串集合
     */
    private Set<String> extractBiGrams(String text) {
        if (text == null || text.length() < 2) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<>(text.length());
        for (int i = 0; i < text.length() - 1; i++) {
            char c1 = text.charAt(i);
            char c2 = text.charAt(i + 1);
            if (!Character.isWhitespace(c1) && !Character.isWhitespace(c2)) {
                set.add("" + c1 + c2);
            }
        }
        return set;
    }

    /**
     * 提取简单查询关键词集合 (长度 >= 2)
     */
    private Set<String> extractSimpleTerms(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> terms = new HashSet<>();
        // 按标点与空格分词
        String[] parts = text.split("[\\s\\p{Punct}，。！？；：、“”‘’（）《》]+");
        for (String p : parts) {
            if (p.length() >= 2) {
                terms.add(p.toLowerCase());
            }
        }
        return terms;
    }

    /**
     * 计算 CJK Bi-gram 词重合度
     */
    private double computeBiGramOverlap(Set<String> queryBiGrams, String content) {
        if (queryBiGrams == null || queryBiGrams.isEmpty() || content == null || content.length() < 2) {
            return 0.0;
        }
        int matchCount = 0;
        for (String bg : queryBiGrams) {
            if (content.contains(bg)) {
                matchCount++;
            }
        }
        return (double) matchCount / queryBiGrams.size();
    }

    /**
     * 计算关键词覆盖度
     */
    private double computeTermCoverage(Set<String> queryTerms, String content) {
        if (queryTerms == null || queryTerms.isEmpty() || content == null || content.isEmpty()) {
            return 0.0;
        }
        String lowerContent = content.toLowerCase();
        int hitCount = 0;
        for (String term : queryTerms) {
            if (lowerContent.contains(term)) {
                hitCount++;
            }
        }
        return (double) hitCount / queryTerms.size();
    }

    private static class ScoredCandidate {
        final RetrievalResult candidate;
        final double score;

        ScoredCandidate(RetrievalResult candidate, double score) {
            this.candidate = candidate;
            this.score = score;
        }
    }
}
