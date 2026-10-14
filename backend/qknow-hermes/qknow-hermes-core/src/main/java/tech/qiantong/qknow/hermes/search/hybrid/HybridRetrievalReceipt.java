package tech.qiantong.qknow.hermes.search.hybrid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * 混合检索全链路不可变加密防伪凭单
 * 遵循 Java 21 Record 规范，提供常量时间 SHA-256 签名校验
 */
public record HybridRetrievalReceipt(
        String query,
        int denseHitCount,
        int sparseHitCount,
        int totalCandidatesEvaluated,
        List<HybridRetrievalCandidate> finalTopKCandidates,
        long executionDurationNanos,
        String rerankMode,
        String signature
) {
    public HybridRetrievalReceipt {
        Objects.requireNonNull(query, "query 不能为空");
        Objects.requireNonNull(finalTopKCandidates, "finalTopKCandidates 不能为空");
        finalTopKCandidates = List.copyOf(finalTopKCandidates);
        Objects.requireNonNull(rerankMode, "rerankMode 不能为空");
        Objects.requireNonNull(signature, "signature 不能为空");
    }

    public static HybridRetrievalReceipt create(
            String query,
            int denseHitCount,
            int sparseHitCount,
            int totalCandidatesEvaluated,
            List<HybridRetrievalCandidate> finalTopKCandidates,
            long executionDurationNanos,
            String rerankMode
    ) {
        Objects.requireNonNull(finalTopKCandidates, "finalTopKCandidates 不能为空");
        List<HybridRetrievalCandidate> immutableCandidates = List.copyOf(finalTopKCandidates);

        String rawPayload = String.format("%s:%d:%d:%d:%d:%d:%s",
                query, denseHitCount, sparseHitCount, totalCandidatesEvaluated,
                immutableCandidates.size(), executionDurationNanos, rerankMode);
        String signature = generateSha256(rawPayload);

        return new HybridRetrievalReceipt(
                query,
                denseHitCount,
                sparseHitCount,
                totalCandidatesEvaluated,
                immutableCandidates,
                executionDurationNanos,
                rerankMode,
                signature
        );
    }

    /**
     * 使用 MessageDigest.isEqual 进行常量时间比对验签，防范时序侧信道攻击
     */
    public boolean verifySignature() {
        String rawPayload = String.format("%s:%d:%d:%d:%d:%d:%s",
                query, denseHitCount, sparseHitCount, totalCandidatesEvaluated,
                finalTopKCandidates.size(), executionDurationNanos, rerankMode);
        String expectedSignature = generateSha256(rawPayload);
        return MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String generateSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 环境缺少 SHA-256 算法实现", e);
        }
    }
}
