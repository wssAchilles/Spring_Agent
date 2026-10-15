package tech.qiantong.qknow.hermes.memory.hippocampus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 海马体离线“睡眠期”图谱沉淀不可变凭单
 * 记录睡眠整合处理的回合数、生成的不变量法则数量及超球面聚合质心向量
 */
public record SleepConsolidationReceipt(
        String consolidationId,
        String sessionId,
        int processedTurnsCount,
        int generatedInvariantsCount,
        List<String> invariantRules,
        float[] clusterCentroid,
        long durationMicros,
        long timestamp,
        String signature
) {
    public SleepConsolidationReceipt {
        Objects.requireNonNull(consolidationId, "consolidationId cannot be null");
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        invariantRules = invariantRules == null ? Collections.emptyList() : Collections.unmodifiableList(invariantRules);
    }

    public static String calculateSignature(String consolidationId, String sessionId,
                                            int processedTurnsCount, int generatedInvariantsCount,
                                            long durationMicros, long timestamp, String secretKey) {
        try {
            String raw = String.format("%s|%s|%d|%d|%d|%d|%s",
                    consolidationId, sessionId, processedTurnsCount, generatedInvariantsCount,
                    durationMicros, timestamp, secretKey == null ? "DEFAULT_CONSOLIDATION_SECRET" : secretKey);
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
        String expected = calculateSignature(consolidationId, sessionId,
                processedTurnsCount, generatedInvariantsCount, durationMicros, timestamp, secretKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
