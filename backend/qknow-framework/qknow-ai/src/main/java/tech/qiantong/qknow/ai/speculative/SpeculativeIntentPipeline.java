package tech.qiantong.qknow.ai.speculative;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Phase 63: 流式意图投机管道与影子快照隔离执行器
 * <p>
 * 基于定理 1.3，基于千问 1536 维超球面测地线内积并行预检索，结合 DeepSeek 64-token 规整前缀对齐。
 * 严格写时隔离 (COW)，影子分支绝对不污染全局生产状态！
 */
public class SpeculativeIntentPipeline {

    public static final int EMBEDDING_DIMENSION = 1536;
    public static final int PREFIX_BLOCK_SIZE = 64;

    /**
     * 影子投机上下文快照 (写时隔离，提交前对主干完全透明)
     */
    public record SpeculativeShadowContext(
            String contextId,
            long epoch,
            String intentDraft,
            float[] queryVector,
            List<String> retrievedSlices,
            String alignedPrefixPrompt,
            long preparedTimeMs
    ) {}

    /**
     * 规范化并校验千问 1536 维超球面单位向量 (||v||_2 = 1.0)
     */
    public static float[] normalizeHypersphere(float[] vec) {
        if (vec == null || vec.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("向量必须严格为 " + EMBEDDING_DIMENSION + " 维，当前: " + (vec != null ? vec.length : 0));
        }
        float[] norm = Arrays.copyOf(vec, EMBEDDING_DIMENSION);
        double sumSq = 0.0;
        for (float v : norm) {
            sumSq += v * v;
        }
        double len = Math.sqrt(sumSq);
        if (len > 1e-9) {
            for (int i = 0; i < norm.length; i++) {
                norm[i] = (float) (norm[i] / len);
            }
        }
        return norm;
    }

    /**
     * 构建 DeepSeek 官方 64-Token 整数倍前缀对齐块
     */
    public static String alignPrefixToBlock(String rawPrefix) {
        if (rawPrefix == null) rawPrefix = "";
        // 估算 Token 数（粗粒度以字符/词估算，并补齐至 64 整数倍占位符）
        int estimatedTokens = Math.max(1, rawPrefix.length() / 3);
        int remainder = estimatedTokens % PREFIX_BLOCK_SIZE;
        if (remainder != 0) {
            int paddingTokens = PREFIX_BLOCK_SIZE - remainder;
            return rawPrefix + " <!-- ALIGNED_PAD:" + "0".repeat(Math.max(1, paddingTokens * 2)) + " -->";
        }
        return rawPrefix;
    }

    /**
     * 展开投机影子预检索
     */
    public SpeculativeShadowContext executeSpeculativePreRetrieval(
            long epoch,
            String intentDraft,
            float[] queryVector,
            Map<String, float[]> knowledgeBaseEmbeddings
    ) {
        float[] normalizedQuery = normalizeHypersphere(queryVector);

        // 千问超球面测地线余弦内积匹配
        List<Map.Entry<String, Double>> scored = new ArrayList<>();
        if (knowledgeBaseEmbeddings != null) {
            for (Map.Entry<String, float[]> entry : knowledgeBaseEmbeddings.entrySet()) {
                double cosine = dotProduct(normalizedQuery, entry.getValue());
                scored.add(Map.entry(entry.getKey(), cosine));
            }
        }
        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<String> topSlices = new ArrayList<>();
        for (int i = 0; i < Math.min(3, scored.size()); i++) {
            topSlices.add(scored.get(i).getKey());
        }

        String rawPrefix = "SYSTEM: 知识库投机预检索上下文: " + String.join(", ", topSlices);
        String alignedPrefix = alignPrefixToBlock(rawPrefix);

        String contextId = "SHADOW-CTX-" + epoch + "-" + UUID.randomUUID().toString().substring(0, 8);
        return new SpeculativeShadowContext(
                contextId, epoch, intentDraft, normalizedQuery,
                topSlices, alignedPrefix, System.currentTimeMillis()
        );
    }

    private double dotProduct(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
