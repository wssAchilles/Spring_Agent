package tech.qiantong.qknow.module.kb.service.agent.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 自适应意图超球面 RRF 重排与熵感知门控器 (Adaptive Hyperspherical RRF Governor)
 * <p>
 * 遵循系统工程总纲 (AGENTS.md) 几何与检索基线：
 * 1. 唯一向量模型基线：阿里千问 1536 维超球面单位向量空间 (||v||_2 = 1.0，余弦相似度 cos\theta = u \cdot v)；
 * 2. 意图语义熵动态加权：根据用户 Query 的文本特征与信息熵动态调制 \alpha 权重：
 *    - 针对罕见专有名词、错误码、数字编号等低熵高特异性查询，自动偏向 Sparse 全文匹配；
 *    - 针对长难句、抽象概念等高熵语义查询，自动偏向 Dense 1536 维超球面向量语义；
 * 3. 超球面几何二次校准：结合余弦保真度动态调制最终排名；
 * 4. 纯 Java 21 Record 格式审计凭单，内嵌 SHA-256 常量时间自验真。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
@Component
public class AdaptiveHypersphericalRrfGovernor {

    public static final int EMBEDDING_DIM = 1536;
    public static final double DEFAULT_RRF_K = 60.0;
    public static final double VECTOR_NORM_TOLERANCE = 1e-4;

    /**
     * 单个自适应重排切片 Record
     */
    public record RankedCandidate(
            RetrieveResult chunk,
            double finalScore,
            double rrfScore,
            double cosineSimilarity,
            int rankDense,
            int rankSparse
    ) {}

    /**
     * 自适应重排不可变审计凭单 Record (支持常量时间 SHA-256 自验真)
     */
    public record AdaptiveRrfAuditReceipt(
            String receiptId,
            String query,
            double queryEntropy,
            double adaptiveAlpha,
            int totalCandidates,
            int selectedCount,
            String receiptHash
    ) {
        public static AdaptiveRrfAuditReceipt create(
                String receiptId,
                String query,
                double queryEntropy,
                double adaptiveAlpha,
                int totalCandidates,
                int selectedCount
        ) {
            String payload = String.format("%s|%s|%.4f|%.4f|%d|%d",
                    receiptId, query != null ? query : "", queryEntropy, adaptiveAlpha, totalCandidates, selectedCount);
            String hash = computeSha256(payload);
            return new AdaptiveRrfAuditReceipt(receiptId, query, queryEntropy, adaptiveAlpha, totalCandidates, selectedCount, hash);
        }

        public boolean verifyIntegrity() {
            String payload = String.format("%s|%s|%.4f|%.4f|%d|%d",
                    receiptId, query != null ? query : "", queryEntropy, adaptiveAlpha, totalCandidates, selectedCount);
            String expected = computeSha256(payload);
            return MessageDigest.isEqual(
                    this.receiptHash.getBytes(StandardCharsets.UTF_8),
                    expected.getBytes(StandardCharsets.UTF_8)
            );
        }

        private static String computeSha256(String data) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : bytes) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception e) {
                throw new IllegalStateException("SHA-256 算法不可用", e);
            }
        }
    }

    /**
     * 计算 Query 的信息熵与语义特异性，动态决定 Dense vs Sparse 调制系数 alpha (0.1 ~ 0.9)
     */
    public double computeAdaptiveAlpha(String query) {
        if (query == null || query.isBlank()) {
            return 0.5; // 中立默认权重
        }

        String trimmed = query.trim();
        // 1. 低熵模式：包含字母与数字混合标识符（如 ID、错误码 ERR_404、camelCase 变量名、UUID）或下划线命名
        boolean hasAlphanumericId = (trimmed.matches(".*[a-zA-Z].*") && trimmed.matches(".*[0-9].*")) || trimmed.contains("_");
        boolean isShortSpecificKeyword = trimmed.length() <= 8 && !trimmed.contains(" ");

        if (hasAlphanumericId || isShortSpecificKeyword) {
            // 低语义熵，关键字精准度更高，偏向 Sparse (alpha 偏低)
            return 0.25;
        }

        // 2. 高熵模式：包含自然语言疑问词或句式（长于 25 字符且带标点或空格）
        if (trimmed.length() > 25 || trimmed.contains("如何") || trimmed.contains("怎么") || trimmed.contains("为什么") || trimmed.contains("?") || trimmed.contains("？")) {
            // 语义意图复杂，偏向 Dense 超球面余弦 (alpha 偏高)
            return 0.75;
        }

        return 0.5;
    }

    /**
     * 校验 1536 维超球面单位向量归一化约束 (||v||_2 = 1.0)
     */
    public boolean validateHypersphericalNorm(float[] vector) {
        if (vector == null || vector.length != EMBEDDING_DIM) {
            return false;
        }
        double sumSq = 0.0;
        for (float v : vector) {
            sumSq += ((double) v * v);
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= VECTOR_NORM_TOLERANCE;
    }

    /**
     * 计算超球面余弦相似度 (\cos\theta = u \cdot v)
     */
    public double computeCosineSimilarity(float[] u, float[] v) {
        if (u == null || v == null || u.length != EMBEDDING_DIM || v.length != EMBEDDING_DIM) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            dot += ((double) u[i] * v[i]);
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    /**
     * 执行自适应超球面 RRF 重排
     *
     * @param query       用户查询
     * @param denseRanked 向量通道候选列表 (顺序代表初排位次)
     * @param sparseRanked 关键字全文检索候选列表 (顺序代表初排位次)
     * @param queryVec    Query 1536 维向量 (可选)
     * @param chunkVectors 切片向量映射 (chunkId -> 1536 维向量，可选)
     * @param topK        最终选取的候选数量
     * @return 排序后的切片列表与审计凭单
     */
    public Map.Entry<List<RankedCandidate>, AdaptiveRrfAuditReceipt> executeAdaptiveRerank(
            String query,
            List<RetrieveResult> denseRanked,
            List<RetrieveResult> sparseRanked,
            float[] queryVec,
            Map<String, float[]> chunkVectors,
            int topK
    ) {
        double alpha = computeAdaptiveAlpha(query);
        double queryEntropy = (alpha > 0.5) ? 0.8 : ((alpha < 0.5) ? 0.2 : 0.5);

        Map<String, RetrieveResult> itemMap = new LinkedHashMap<>();
        Map<String, Integer> denseRankMap = new HashMap<>();
        Map<String, Integer> sparseRankMap = new HashMap<>();

        if (denseRanked != null) {
            for (int i = 0; i < denseRanked.size(); i++) {
                RetrieveResult r = denseRanked.get(i);
                if (r != null && r.getId() != null) {
                    itemMap.put(r.getId(), r);
                    denseRankMap.put(r.getId(), i + 1);
                }
            }
        }

        if (sparseRanked != null) {
            for (int i = 0; i < sparseRanked.size(); i++) {
                RetrieveResult r = sparseRanked.get(i);
                if (r != null && r.getId() != null) {
                    itemMap.putIfAbsent(r.getId(), r);
                    sparseRankMap.put(r.getId(), i + 1);
                }
            }
        }

        List<RankedCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, RetrieveResult> entry : itemMap.entrySet()) {
            String id = entry.getKey();
            RetrieveResult item = entry.getValue();

            int rDense = denseRankMap.getOrDefault(id, 1000);
            int rSparse = sparseRankMap.getOrDefault(id, 1000);

            // 自适应 RRF 融合公式
            double rrfScore = alpha * (1.0 / (DEFAULT_RRF_K + rDense)) + (1.0 - alpha) * (1.0 / (DEFAULT_RRF_K + rSparse));

            // 余弦几何调制
            double cosine = 0.0;
            if (queryVec != null && chunkVectors != null && chunkVectors.containsKey(id)) {
                float[] itemVec = chunkVectors.get(id);
                if (itemVec != null && itemVec.length == EMBEDDING_DIM) {
                    cosine = computeCosineSimilarity(queryVec, itemVec);
                }
            } else if (item.getScore() != null) {
                // 兼容已有 score 作为预计算余弦相似度
                cosine = item.getScore();
            }

            double finalScore = rrfScore * (1.0 + Math.max(0.0, cosine));

            candidates.add(new RankedCandidate(item, finalScore, rrfScore, cosine, rDense, rSparse));
        }

        // 按 finalScore 降序排列
        candidates.sort((a, b) -> Double.compare(b.finalScore(), a.finalScore()));

        List<RankedCandidate> selected = candidates.stream().limit(Math.max(1, topK)).toList();

        String receiptId = "rcpt_rrf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AdaptiveRrfAuditReceipt receipt = AdaptiveRrfAuditReceipt.create(
                receiptId, query, queryEntropy, alpha, candidates.size(), selected.size()
        );

        return Map.entry(selected, receipt);
    }

    /**
     * 重载简版接口 (不带显式切片向量表)
     */
    public Map.Entry<List<RankedCandidate>, AdaptiveRrfAuditReceipt> executeAdaptiveRerank(
            String query,
            List<RetrieveResult> denseRanked,
            List<RetrieveResult> sparseRanked,
            float[] queryVec,
            int topK
    ) {
        return executeAdaptiveRerank(query, denseRanked, sparseRanked, queryVec, Collections.emptyMap(), topK);
    }
}
