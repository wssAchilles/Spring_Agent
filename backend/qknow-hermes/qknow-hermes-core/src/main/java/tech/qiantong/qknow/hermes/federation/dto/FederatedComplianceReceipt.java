package tech.qiantong.qknow.hermes.federation.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 不可变跨域联邦与多租户合规存证凭单 Record
 */
public record FederatedComplianceReceipt(
        String receiptId,
        String transactionId,
        String tenantId,
        ComplianceVetoAction finalAction,
        int participantDomains,
        double cbfMargin,
        long totalDurationNanos,
        long timestampEpochMs,
        String signature
) {
    public boolean verifyIntegrity() {
        String expected = calculateSignature(
                receiptId, transactionId, tenantId, finalAction,
                participantDomains, cbfMargin, totalDurationNanos, timestampEpochMs
        );
        return expected.equalsIgnoreCase(signature);
    }

    public static String calculateSignature(
            String receiptId, String transactionId, String tenantId,
            ComplianceVetoAction finalAction, int participantDomains,
            double cbfMargin, long totalDurationNanos, long timestampEpochMs
    ) {
        String raw = String.format(
                "%s|%s|%s|%s|%d|%.6f|%d|%d",
                receiptId, transactionId, tenantId, finalAction.name(),
                participantDomains, cbfMargin, totalDurationNanos, timestampEpochMs
        );
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
