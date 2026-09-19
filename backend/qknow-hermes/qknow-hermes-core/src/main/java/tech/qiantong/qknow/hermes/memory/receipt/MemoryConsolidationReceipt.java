package tech.qiantong.qknow.hermes.memory.receipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 不可变记忆巩固存证凭单（纯 Java 21 Record）。
 * 遵循 Phase 111 契约，具备密码学自签名与防篡改验真能力。
 */
public record MemoryConsolidationReceipt(
        String receiptId,
        String sessionId,
        String userId,
        long consolidatedAt,
        int sourceMessageCount,
        int retainedMessageCount,
        String extractedProfileHash,
        String status, // SUCCESS, SKIPPED_LOW_CONFIDENCE, FAILED
        String signature
) {
    private static final String SIGNATURE_SALT = "QKnow-Phase111-Memory-Consolidation-Salt-v1";

    public static MemoryConsolidationReceipt create(
            String receiptId,
            String sessionId,
            String userId,
            long consolidatedAt,
            int sourceMessageCount,
            int retainedMessageCount,
            String extractedProfileHash,
            String status) {
        String calculatedSig = calculateSignature(receiptId, sessionId, userId, consolidatedAt,
                sourceMessageCount, retainedMessageCount, extractedProfileHash, status);
        return new MemoryConsolidationReceipt(receiptId, sessionId, userId, consolidatedAt,
                sourceMessageCount, retainedMessageCount, extractedProfileHash, status, calculatedSig);
    }

    public boolean verifySignature() {
        String expectedSig = calculateSignature(receiptId, sessionId, userId, consolidatedAt,
                sourceMessageCount, retainedMessageCount, extractedProfileHash, status);
        return expectedSig.equals(this.signature);
    }

    private static String calculateSignature(
            String receiptId, String sessionId, String userId, long consolidatedAt,
            int sourceMessageCount, int retainedMessageCount, String extractedProfileHash, String status) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.format("%s|%s|%s|%d|%d|%d|%s|%s|%s",
                    receiptId, sessionId, userId, consolidatedAt,
                    sourceMessageCount, retainedMessageCount, extractedProfileHash, status, SIGNATURE_SALT);
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
