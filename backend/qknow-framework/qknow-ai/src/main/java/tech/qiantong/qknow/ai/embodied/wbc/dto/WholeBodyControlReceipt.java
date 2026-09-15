package tech.qiantong.qknow.ai.embodied.wbc.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 不可变全身执行存证凭单 (Java 21 Record)
 * 记录实时 ZMP 裕度、质心动量范数、分层 QP 耗时、摩擦锥裕度、总线状态与 SHA-256 密码学签名
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record WholeBodyControlReceipt(
        String receiptId,
        long timestampEpochMs,
        double zmpSecurityMarginMm,
        double centroidalMomentumNorm,
        double qpSolvingTimeMs,
        double frictionConeMargin,
        boolean antiSlipIntervened,
        boolean degradedMode,
        String degradationReason,
        String sha256Signature
) {
    public WholeBodyControlReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(degradationReason, "degradationReason 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
    }

    /**
     * 静态工厂方法，生成带有自签名的不可变凭单
     */
    public static WholeBodyControlReceipt createSigned(
            String receiptId,
            long timestampEpochMs,
            double zmpSecurityMarginMm,
            double centroidalMomentumNorm,
            double qpSolvingTimeMs,
            double frictionConeMargin,
            boolean antiSlipIntervened,
            boolean degradedMode,
            String degradationReason
    ) {
        String payload = buildPayload(receiptId, timestampEpochMs, zmpSecurityMarginMm,
                centroidalMomentumNorm, qpSolvingTimeMs, frictionConeMargin,
                antiSlipIntervened, degradedMode, degradationReason);
        String signature = calculateSha256(payload);
        return new WholeBodyControlReceipt(receiptId, timestampEpochMs, zmpSecurityMarginMm,
                centroidalMomentumNorm, qpSolvingTimeMs, frictionConeMargin,
                antiSlipIntervened, degradedMode, degradationReason, signature);
    }

    /**
     * 自验证签名完整性与抗篡改性
     */
    public boolean verifySignature() {
        String payload = buildPayload(receiptId, timestampEpochMs, zmpSecurityMarginMm,
                centroidalMomentumNorm, qpSolvingTimeMs, frictionConeMargin,
                antiSlipIntervened, degradedMode, degradationReason);
        return calculateSha256(payload).equalsIgnoreCase(sha256Signature);
    }

    private static String buildPayload(String id, long ts, double zmpMargin, double cmmNorm,
                                      double qpTime, double fcMargin, boolean antiSlip,
                                      boolean degraded, String reason) {
        return id + "|" + ts + "|" + String.format("%.4f", zmpMargin) + "|"
                + String.format("%.4f", cmmNorm) + "|" + String.format("%.4f", qpTime) + "|"
                + String.format("%.4f", fcMargin) + "|" + antiSlip + "|" + degraded + "|" + reason;
    }

    private static String calculateSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
