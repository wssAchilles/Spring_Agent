package tech.qiantong.qknow.ai.embodied.actuation.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 不可变连续执行存证凭单 (Java 21 Record)
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record ContinuousActuationReceipt(
        String receiptId,
        String commandId,
        long timestampNs,
        String commandHash,
        double maxVelocity,
        double maxAcceleration,
        double maxJerk,
        double minBarrierMargin,
        double meanContactForce,
        String actuationStatus,
        String signatureSha256
) {
    public static ContinuousActuationReceipt generate(
            String commandId,
            String commandHash,
            double maxVelocity,
            double maxAcceleration,
            double maxJerk,
            double minBarrierMargin,
            double meanContactForce,
            String actuationStatus
    ) {
        String receiptId = "ACT-RCPT-" + UUID.randomUUID();
        long timestampNs = System.nanoTime();
        String payload = String.format("%s|%s|%d|%s|%.4f|%.4f|%.4f|%.4f|%.4f|%s",
                receiptId, commandId, timestampNs, commandHash,
                maxVelocity, maxAcceleration, maxJerk,
                minBarrierMargin, meanContactForce, actuationStatus);
        String signature = sha256(payload);
        return new ContinuousActuationReceipt(
                receiptId, commandId, timestampNs, commandHash,
                maxVelocity, maxAcceleration, maxJerk,
                minBarrierMargin, meanContactForce, actuationStatus,
                signature
        );
    }

    public boolean verifyIntegrity() {
        String payload = String.format("%s|%s|%d|%s|%.4f|%.4f|%.4f|%.4f|%.4f|%s",
                receiptId, commandId, timestampNs, commandHash,
                maxVelocity, maxAcceleration, maxJerk,
                minBarrierMargin, meanContactForce, actuationStatus);
        return sha256(payload).equals(signatureSha256);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }
}
