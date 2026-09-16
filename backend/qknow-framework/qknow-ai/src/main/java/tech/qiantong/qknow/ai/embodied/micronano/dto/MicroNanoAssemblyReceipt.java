package tech.qiantong.qknow.ai.embodied.micronano.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 不可变微纳装配操作存证凭单 (Java 21 Record)
 * 封装装配会话 ID、工件 ID、脱粘成功标志、伴生飞溅微位移残差、最大微接触力、压溃率、千问超球面偏角、HOCBF 前向不变性标志、计算耗时与 SHA-256 密码学签名。
 *
 * @author Achilles
 * @since 2026-09-16
 */
public record MicroNanoAssemblyReceipt(
        String receiptId,
        String assemblySessionId,
        String workpieceId,
        double releaseSplashMicron,
        boolean detachmentSuccess,
        double maxContactForceMicroN,
        double crushRate,
        double alignmentErrorMicron,
        double hocbfSafetyMargin,
        double qwenGeodesicDistance,
        String busStatus,
        long computeLatencyMicros,
        String signature
) {
    public MicroNanoAssemblyReceipt {
        Objects.requireNonNull(receiptId, "receiptId cannot be null");
        Objects.requireNonNull(assemblySessionId, "assemblySessionId cannot be null");
        Objects.requireNonNull(workpieceId, "workpieceId cannot be null");
        Objects.requireNonNull(busStatus, "busStatus cannot be null");
        Objects.requireNonNull(signature, "signature cannot be null");
    }

    /**
     * 生成 SHA-256 密码学防伪自签名
     */
    public static String generateSignature(
            String receiptId,
            String assemblySessionId,
            String workpieceId,
            double releaseSplashMicron,
            boolean detachmentSuccess,
            double maxContactForceMicroN,
            double crushRate,
            double alignmentErrorMicron,
            double hocbfSafetyMargin,
            double qwenGeodesicDistance,
            String busStatus,
            long computeLatencyMicros
    ) {
        String payload = String.format(
                "%s|%s|%s|%.4f|%b|%.4f|%.4f|%.4f|%.4f|%.6f|%s|%d",
                receiptId, assemblySessionId, workpieceId, releaseSplashMicron, detachmentSuccess,
                maxContactForceMicroN, crushRate, alignmentErrorMicron, hocbfSafetyMargin,
                qwenGeodesicDistance, busStatus, computeLatencyMicros
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /**
     * 自验证签名是否防篡改且有效
     */
    public boolean verifySignature() {
        String expectedSignature = generateSignature(
                receiptId, assemblySessionId, workpieceId, releaseSplashMicron, detachmentSuccess,
                maxContactForceMicroN, crushRate, alignmentErrorMicron, hocbfSafetyMargin,
                qwenGeodesicDistance, busStatus, computeLatencyMicros
        );
        return expectedSignature.equalsIgnoreCase(this.signature);
    }
}
