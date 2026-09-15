package tech.qiantong.qknow.ai.embodied.suction.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 不可变多相吸附与流体控制存证凭单 (Java 21 Record)
 * 封装多相流体吸附全生命周期状态指标并提供 SHA-256 密码学防篡改签名自验能力。
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record SuctionFluidReceipt(
        String receiptId,
        String sessionId,
        String dexterityHandId,
        String workpieceId,
        String manipulationPhase,
        double avgSealIntegrity,
        double maxWallShearStressKPa,
        double cavitationMargin,
        double trackingErrorMm,
        long stepLatencyUs,
        boolean degradedModeActive,
        long timestamp,
        String sha256Signature
) {
    public static SuctionFluidReceipt createAndSign(
            String receiptId,
            String sessionId,
            String dexterityHandId,
            String workpieceId,
            String manipulationPhase,
            double avgSealIntegrity,
            double maxWallShearStressKPa,
            double cavitationMargin,
            double trackingErrorMm,
            long stepLatencyUs,
            boolean degradedModeActive
    ) {
        long now = System.currentTimeMillis();
        String payload = String.format(Locale.ROOT,
                "%s|%s|%s|%s|%s|%.6f|%.6f|%.6f|%.4f|%d|%b|%d",
                receiptId, sessionId, dexterityHandId, workpieceId, manipulationPhase,
                avgSealIntegrity, maxWallShearStressKPa, cavitationMargin,
                trackingErrorMm, stepLatencyUs, degradedModeActive, now
        );
        String signature = computeSha256(payload);
        return new SuctionFluidReceipt(
                receiptId, sessionId, dexterityHandId, workpieceId, manipulationPhase,
                avgSealIntegrity, maxWallShearStressKPa, cavitationMargin,
                trackingErrorMm, stepLatencyUs, degradedModeActive, now, signature
        );
    }

    public boolean verifySignature() {
        String payload = String.format(Locale.ROOT,
                "%s|%s|%s|%s|%s|%.6f|%.6f|%.6f|%.4f|%d|%b|%d",
                receiptId, sessionId, dexterityHandId, workpieceId, manipulationPhase,
                avgSealIntegrity, maxWallShearStressKPa, cavitationMargin,
                trackingErrorMm, stepLatencyUs, degradedModeActive, timestamp
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
