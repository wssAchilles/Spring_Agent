package tech.qiantong.qknow.hermes.search.hybrid;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 无偏互惠排序融合 (Reciprocal Rank Fusion, RRF) 算法引擎
 * 针对稠密语义检索与稀疏关键词倒排检索进行无量纲分数的自适应加权融合
 */
public class ReciprocalRankFusionEngine {

    public static final int DEFAULT_SMOOTHING_K = 60;
    private static final Pattern CODE_IDENTIFIER_PATTERN = Pattern.compile(".*[A-Z0-9_]{3,}.*");

    private final int smoothingK;

    public ReciprocalRankFusionEngine() {
        this(DEFAULT_SMOOTHING_K);
    }

    public ReciprocalRankFusionEngine(int smoothingK) {
        if (smoothingK <= 0) {
            throw new IllegalArgumentException("平滑常数 smoothingK 必须大于 0");
        }
        this.smoothingK = smoothingK;
    }

    /**
     * 动态推断稠密与稀疏通道权重
     * 若查询包含大写错误码或标识符（如 ERR_TOKEN_401），自适应放大稀疏倒排权重
     */
    public double[] inferAdaptiveWeights(String query) {
        if (query == null || query.isBlank()) {
            return new double[]{0.5, 0.5};
        }
        if (CODE_IDENTIFIER_PATTERN.matcher(query).matches() || query.contains(".") || query.contains("_")) {
            // 标识符与错误码场景：稀疏权重 0.7，稠密权重 0.3
            return new double[]{0.3, 0.7};
        }
        // 纯自然语言语义场景：稠密权重 0.7，稀疏权重 0.3
        return new double[]{0.7, 0.3};
    }

    /**
     * 执行无偏两路 RRF 融合
     * @param denseCandidates 稠密通道排序文档列表 (按相关性降序)
     * @param sparseCandidates 稀疏通道排序文档列表 (按 BM25 得分降序)
     * @param denseWeight 稠密通道权重
     * @param sparseWeight 稀疏通道权重
     * @return 融合并重新排序后的完整候选列表
     */
    public List<HybridRetrievalCandidate> fuse(
            List<Map.Entry<String, Float>> denseCandidates,
            List<Map.Entry<String, Float>> sparseCandidates,
            Map<String, String> docTexts,
            double denseWeight,
            double sparseWeight
    ) {
        Objects.requireNonNull(denseCandidates, "denseCandidates 不能为空");
        Objects.requireNonNull(sparseCandidates, "sparseCandidates 不能为空");
        Objects.requireNonNull(docTexts, "docTexts 不能为空");

        Map<String, Integer> denseRankMap = new HashMap<>();
        Map<String, Float> denseScoreMap = new HashMap<>();
        for (int rank = 0; rank < denseCandidates.size(); rank++) {
            Map.Entry<String, Float> entry = denseCandidates.get(rank);
            denseRankMap.put(entry.getKey(), rank + 1);
            denseScoreMap.put(entry.getKey(), entry.getValue());
        }

        Map<String, Integer> sparseRankMap = new HashMap<>();
        Map<String, Float> sparseScoreMap = new HashMap<>();
        for (int rank = 0; rank < sparseCandidates.size(); rank++) {
            Map.Entry<String, Float> entry = sparseCandidates.get(rank);
            sparseRankMap.put(entry.getKey(), rank + 1);
            sparseScoreMap.put(entry.getKey(), entry.getValue());
        }

        Set<String> allDocIds = new HashSet<>(denseRankMap.keySet());
        allDocIds.addAll(sparseRankMap.keySet());

        List<HybridRetrievalCandidate> candidates = new ArrayList<>(allDocIds.size());
        for (String docId : allDocIds) {
            int dRank = denseRankMap.getOrDefault(docId, -1);
            float dScore = denseScoreMap.getOrDefault(docId, 0.0f);

            int sRank = sparseRankMap.getOrDefault(docId, -1);
            float sScore = sparseScoreMap.getOrDefault(docId, 0.0f);

            double rrfScore = 0.0;
            if (dRank > 0) {
                rrfScore += denseWeight / (smoothingK + dRank);
            }
            if (sRank > 0) {
                rrfScore += sparseWeight / (smoothingK + sRank);
            }

            String text = docTexts.getOrDefault(docId, "");
            candidates.add(HybridRetrievalCandidate.of(
                    docId, text, dScore, dRank, sScore, sRank, rrfScore
            ));
        }

        // 依据 RRF 得分降序排序
        candidates.sort(Comparator.comparingDouble(HybridRetrievalCandidate::rrfScore).reversed());
        return List.copyOf(candidates);
    }
}
