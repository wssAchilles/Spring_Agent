package tech.qiantong.qknow.ai.embodied.dexterous.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 不可变灵巧手操作存证凭单 (Java 21 Record)
 * 封装重抓取全生命周期指标并提供 SHA-256 密码学签名验真与防篡改自检能力。
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record DexterousManipulationReceipt(
        String receiptId,
        String sessionId,
        String workpieceId,
        String regraspPhase,
        double forceClosureMetric,
        double hocbfSafetyMargin,
        double poseTrackingErrorMm,
        long stepLatencyUs,
        String executionStatus,
        long timestamp,
        String sha256Signature
) {
    public static DexterousManipulationReceipt createAndSign(
            String receiptId,
            String sessionId,
            String workpieceId,
            String regraspPhase,
            double forceClosureMetric,
            double hocbfSafetyMargin,
            double poseTrackingErrorMm,
            long stepLatencyUs,
            String executionStatus
    ) {
        long now = System.currentTimeMillis();
        String payload = String.format(Locale.ROOT,
                "%s|%s|%s|%s|%.6f|%.6f|%.4f|%d|%s|%d",
                receiptId, sessionId, workpieceId, regraspPhase,
                forceClosureMetric, hocbfSafetyMargin, poseTrackingErrorMm,
                stepLatencyUs, executionStatus, now
        );
        String signature = computeSha256(payload);
        return new DexterousManipulationReceipt(
                receiptId, sessionId, workpieceId, regraspPhase,
                forceClosureMetric, hocbfSafetyMargin, poseTrackingErrorMm,
                stepLatencyUs, executionStatus, now, signature
        );
    }

    public boolean verifySignature() {
        String payload = String.format(Locale.ROOT,
                "%s|%s|%s|%s|%.6f|%.6f|%.4f|%d|%s|%d",
                receiptId, sessionId, workpieceId, regraspPhase,
                forceClosureMetric, hocbfSafetyMargin, poseTrackingErrorMm,
                stepLatencyUs, executionStatus, timestamp
        );
        return computeSha256(payload).equalsIgnoreCase(sha256Signature);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }
}
