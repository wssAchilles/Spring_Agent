package tech.qiantong.qknow.ai.embodied.impedance.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 不可变多模态感知与阻抗协同存证凭单 (Java 21 Record)
 * 封装单步控制时空对齐残差、阻抗刚度/阻尼、储能罐余量、HOCBF 裕度与时延指标，并提供 SHA-256 数字签名。
 *
 * @author Achilles
 * @since 2026-09-16
 */
public record TactileVisualImpedanceReceipt(
        String receiptId,
        String sessionId,
        String robotArmId,
        String phaseName,
        double spatiotemporalResidualMm,
        double stiffnessNorm,
        double dampingNorm,
        double energyTankJoules,
        double hocbfMargin,
        long stepLatencyUs,
        String busState,
        long timestamp,
        String sha256Signature
) {
    public static TactileVisualImpedanceReceipt createAndSign(
            String receiptId,
            String sessionId,
            String robotArmId,
            String phaseName,
            double spatiotemporalResidualMm,
            double stiffnessNorm,
            double dampingNorm,
            double energyTankJoules,
            double hocbfMargin,
            long stepLatencyUs,
            String busState
    ) {
        long now = System.currentTimeMillis();
        String payload = String.format(Locale.ROOT,
                "%s|%s|%s|%s|%.6f|%.4f|%.4f|%.6f|%.6f|%d|%s|%d",
                receiptId, sessionId, robotArmId, phaseName,
                spatiotemporalResidualMm, stiffnessNorm, dampingNorm,
                energyTankJoules, hocbfMargin, stepLatencyUs, busState, now
        );
        String signature = computeSha256(payload);
        return new TactileVisualImpedanceReceipt(
                receiptId, sessionId, robotArmId, phaseName,
                spatiotemporalResidualMm, stiffnessNorm, dampingNorm,
                energyTankJoules, hocbfMargin, stepLatencyUs, busState, now, signature
        );
    }

    public boolean verifySignature() {
        String payload = String.format(Locale.ROOT,
                "%s|%s|%s|%s|%.6f|%.4f|%.4f|%.6f|%.6f|%d|%s|%d",
                receiptId, sessionId, robotArmId, phaseName,
                spatiotemporalResidualMm, stiffnessNorm, dampingNorm,
                energyTankJoules, hocbfMargin, stepLatencyUs, busState, timestamp
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
            throw new IllegalStateException("SHA-256 digest algorithm not available", e);
        }
    }
}
