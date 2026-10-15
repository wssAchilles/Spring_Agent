package tech.qiantong.qknow.hermes.agent.speculative;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 推测执行与前向预取不可变审计凭单
 * 记录 System 1 草稿候选数、System 2 思考流触发的预取数、缓存命中数与耗时
 */
public record SpeculativeDraftPrefetchReceipt(
        String receiptId,
        String requestId,
        int draftCandidatesCount,
        int triggeredPrefetchCount,
        int cacheHitCount,
        long speculativeLatencyMicros,
        long timestamp,
        String signature
) {
    public SpeculativeDraftPrefetchReceipt {
        Objects.requireNonNull(receiptId, "receiptId cannot be null");
        Objects.requireNonNull(requestId, "requestId cannot be null");
    }

    public static String calculateSignature(String receiptId, String requestId, int draftCount,
                                            int prefetchCount, int hitCount, long latencyMicros,
                                            long timestamp, String secretKey) {
        try {
            String raw = String.format("%s|%s|%d|%d|%d|%d|%d|%s",
                    receiptId, requestId, draftCount, prefetchCount, hitCount,
                    latencyMicros, timestamp, secretKey == null ? "DEFAULT_SPECULATIVE_SECRET" : secretKey);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public boolean verifySignature(String secretKey) {
        if (signature == null || signature.isBlank()) return false;
        String expected = calculateSignature(receiptId, requestId, draftCandidatesCount,
                triggeredPrefetchCount, cacheHitCount, speculativeLatencyMicros, timestamp, secretKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
