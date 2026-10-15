package tech.qiantong.qknow.hermes.agent.speculative;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 阿里千问 1536 维超球面置信度门禁推测预取过滤器 (Confidence-Gated Prefetch Filter)
 * 遵循超球面余弦几何律与拒绝采样防注意力污染工程铁律：
 * 1. 严格校验意图与草稿向量满足 S^{1535} 单位超球面范数约束 (||v||_2 = 1.0)；
 * 2. 计算余弦内积 cos theta = u · v，执行硬门禁 cos theta >= 0.85 判定；
 * 3. 拒绝采样的假阳性草稿平滑拦截并隔离入容量为 32 的 L3 冷备环形仓，绝不污染大模型上下文；
 * 4. 签发纯 Java 21 Record 密码学自验真凭单。
 */
public class ConfidenceGatedPrefetchFilter {

    public static final int VECTOR_DIMENSION = 1536;
    public static final double DEFAULT_ACCEPTANCE_THRESHOLD = 0.85;
    public static final int COLD_BUFFER_CAPACITY = 32;

    public record RejectedDraftRecord(
            String targetKey,
            String draftKey,
            double cosineSimilarity,
            double threshold,
            String rejectionReason,
            long timestamp
    ) {}

    public record GatedPrefetchDecisionReceipt(
            String receiptId,
            String targetKey,
            String draftKey,
            double cosineSimilarity,
            double threshold,
            boolean accepted,
            int coldBufferSize,
            long timestamp,
            String signature
    ) {
        public static String calculateSignature(
                String receiptId,
                String targetKey,
                String draftKey,
                double cosineSimilarity,
                double threshold,
                boolean accepted,
                long timestamp,
                String secretKey
        ) {
            String payload = String.join("|",
                    receiptId,
                    targetKey,
                    draftKey,
                    String.format(Locale.ROOT, "%.4f", cosineSimilarity),
                    String.format(Locale.ROOT, "%.4f", threshold),
                    String.valueOf(accepted),
                    String.valueOf(timestamp),
                    secretKey == null ? "GATE_SECRET" : secretKey
            );
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    hex.append(String.format("%02x", b));
                }
                return hex.toString();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to calculate SHA-256 for gate receipt", e);
            }
        }

        public boolean verifySignature(String secretKey) {
            String expected = calculateSignature(receiptId, targetKey, draftKey, cosineSimilarity, threshold, accepted, timestamp, secretKey);
            byte[] a = this.signature.getBytes(StandardCharsets.UTF_8);
            byte[] b = expected.getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(a, b);
        }
    }

    public record GateEvaluationResult(
            boolean accepted,
            double cosineSimilarity,
            GatedPrefetchDecisionReceipt receipt
    ) {}

    private final double threshold;
    private final String secretKey;
    private final Deque<RejectedDraftRecord> coldBuffer = new ConcurrentLinkedDeque<>();

    private int totalEvaluated = 0;
    private int totalAccepted = 0;
    private int totalRejected = 0;

    public ConfidenceGatedPrefetchFilter() {
        this(DEFAULT_ACCEPTANCE_THRESHOLD, "DEFAULT_GATE_SECRET_" + System.currentTimeMillis());
    }

    public ConfidenceGatedPrefetchFilter(double threshold, String secretKey) {
        this.threshold = threshold;
        this.secretKey = secretKey == null ? "DEFAULT_GATE_SECRET" : secretKey;
    }

    /**
     * 校验向量维度并进行单位归一化校准
     */
    public static float[] validateAndNormalize(float[] vector) {
        Objects.requireNonNull(vector, "vector cannot be null");
        if (vector.length != VECTOR_DIMENSION) {
            throw new IllegalArgumentException("Vector dimension mismatch: expected " + VECTOR_DIMENSION + ", got " + vector.length);
        }
        double sumSq = 0.0;
        for (float v : vector) {
            sumSq += (double) v * v;
        }
        double norm = Math.sqrt(sumSq);
        if (norm < 1e-12) {
            throw new IllegalArgumentException("Vector norm is zero or degenerate");
        }
        if (Math.abs(norm - 1.0) <= 1e-4) {
            return vector;
        }
        float[] normalized = new float[VECTOR_DIMENSION];
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    /**
     * 计算千问 1536 维超球面单位向量内积 (余弦相似度)
     */
    public static double computeCosineSimilarity(float[] u, float[] v) {
        float[] normU = validateAndNormalize(u);
        float[] normV = validateAndNormalize(v);
        double dot = 0.0;
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            dot += (double) normU[i] * normV[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    /**
     * 基于超球面施密特正交分解构造与目标向量余弦相似度恒等为 targetCosine 的单位向量
     */
    public static float[] createCorrelatedUnitVector(float[] u, double targetCosine, long seed) {
        float[] normU = validateAndNormalize(u);
        Random rnd = new Random(seed);
        float[] r = new float[VECTOR_DIMENSION];
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            r[i] = (float) rnd.nextGaussian();
        }
        double dot = 0.0;
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            dot += (double) r[i] * normU[i];
        }
        float[] w = new float[VECTOR_DIMENSION];
        double sumSqW = 0.0;
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            w[i] = (float) (r[i] - dot * normU[i]);
            sumSqW += (double) w[i] * w[i];
        }
        double normW = Math.sqrt(sumSqW);
        double cosVal = Math.max(-1.0, Math.min(1.0, targetCosine));
        double sinVal = Math.sqrt(1.0 - cosVal * cosVal);

        float[] result = new float[VECTOR_DIMENSION];
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            result[i] = (float) (cosVal * normU[i] + sinVal * (w[i] / normW));
        }
        return validateAndNormalize(result);
    }

    /**
     * 执行推测预取置信度门禁评估
     *
     * @param targetKey       目标意图键 (如 "doc:manual_user_guide")
     * @param draftKey        候选草稿键 (如 "doc:manual_user_guide_v2")
     * @param targetEmbedding 目标意图超球面嵌入向量 (1536 维)
     * @param draftEmbedding  推测草稿超球面嵌入向量 (1536 维)
     * @return GateEvaluationResult 包含接受判定与不可变凭单
     */
    public synchronized GateEvaluationResult evaluateDraft(
            String targetKey,
            String draftKey,
            float[] targetEmbedding,
            float[] draftEmbedding
    ) {
        Objects.requireNonNull(targetKey, "targetKey cannot be null");
        Objects.requireNonNull(draftKey, "draftKey cannot be null");

        totalEvaluated++;
        long timestamp = System.currentTimeMillis();
        String receiptId = "gate_rcpt_" + UUID.randomUUID().toString().replace("-", "");

        double cosSim = computeCosineSimilarity(targetEmbedding, draftEmbedding);
        boolean accepted = cosSim >= this.threshold;

        if (accepted) {
            totalAccepted++;
        } else {
            totalRejected++;
            // 拒绝采样：隔离至三级冷备环形仓，绝不污染注意力
            while (coldBuffer.size() >= COLD_BUFFER_CAPACITY) {
                coldBuffer.pollFirst();
            }
            coldBuffer.addLast(new RejectedDraftRecord(
                    targetKey, draftKey, cosSim, threshold,
                    "Cosine similarity " + String.format(Locale.ROOT, "%.4f", cosSim) + " < threshold " + threshold,
                    timestamp
            ));
        }

        String sig = GatedPrefetchDecisionReceipt.calculateSignature(
                receiptId, targetKey, draftKey, cosSim, threshold, accepted, timestamp, secretKey);

        GatedPrefetchDecisionReceipt receipt = new GatedPrefetchDecisionReceipt(
                receiptId, targetKey, draftKey, cosSim, threshold, accepted,
                coldBuffer.size(), timestamp, sig);

        return new GateEvaluationResult(accepted, cosSim, receipt);
    }

    public List<RejectedDraftRecord> getColdBufferSnapshot() {
        return List.copyOf(coldBuffer);
    }

    public double getThreshold() {
        return threshold;
    }

    public int getTotalEvaluated() {
        return totalEvaluated;
    }

    public int getTotalAccepted() {
        return totalAccepted;
    }

    public int getTotalRejected() {
        return totalRejected;
    }

    public synchronized void reset() {
        coldBuffer.clear();
        totalEvaluated = 0;
        totalAccepted = 0;
        totalRejected = 0;
    }
}
