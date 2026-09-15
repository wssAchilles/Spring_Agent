package tech.qiantong.qknow.ai.embodied.fluid.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * 不可变流体-刚体动力学协同操作密码学存证凭单 (Java 21 Record)
 * <p>
 * 封装凭单唯一 ID、会话 ID、容器/工件 ID、分注容积、残余晃荡动能均值、最大晃荡倾角、
 * 开口防溢出安全裕度、表观剪切粘度指标、毛细拉丝截断率、降阶推演单步耗时、
 * 控制总线软着陆降级状态、时间戳与 SHA-256 密码学防篡改签名。
 */
public record FluidManipulationReceipt(
        String receiptId,
        String sessionId,
        String containerId,
        double dispensedVolumeMl,
        double averageKineticEnergy,
        double maxSloshAngleRad,
        double sloshAngleMarginRad,
        double apparentViscosity,
        double filamentCutoffRatio,
        long reducedModelInferenceTimeUs,
        boolean degradedSafeHover,
        long timestampMs,
        String sha256Signature
) {
    public FluidManipulationReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(containerId, "containerId 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
    }

    /**
     * 工厂方法：自动装配并签署 SHA-256 防篡改签名
     */
    public static FluidManipulationReceipt sign(
            String receiptId,
            String sessionId,
            String containerId,
            double dispensedVolumeMl,
            double averageKineticEnergy,
            double maxSloshAngleRad,
            double sloshAngleMarginRad,
            double apparentViscosity,
            double filamentCutoffRatio,
            long reducedModelInferenceTimeUs,
            boolean degradedSafeHover,
            long timestampMs
    ) {
        String raw = String.format(Locale.US, "%s|%s|%s|%.6f|%.6f|%.6f|%.6f|%.6f|%.6f|%d|%b|%d",
                receiptId, sessionId, containerId, dispensedVolumeMl, averageKineticEnergy,
                maxSloshAngleRad, sloshAngleMarginRad, apparentViscosity, filamentCutoffRatio,
                reducedModelInferenceTimeUs, degradedSafeHover, timestampMs);
        String signature = computeSha256(raw);
        return new FluidManipulationReceipt(
                receiptId, sessionId, containerId, dispensedVolumeMl, averageKineticEnergy,
                maxSloshAngleRad, sloshAngleMarginRad, apparentViscosity, filamentCutoffRatio,
                reducedModelInferenceTimeUs, degradedSafeHover, timestampMs, signature
        );
    }

    /**
     * 校验当前凭单的密码学签名自洽性与完整性
     */
    public boolean verifySignature() {
        String raw = String.format(Locale.US, "%s|%s|%s|%.6f|%.6f|%.6f|%.6f|%.6f|%.6f|%d|%b|%d",
                receiptId, sessionId, containerId, dispensedVolumeMl, averageKineticEnergy,
                maxSloshAngleRad, sloshAngleMarginRad, apparentViscosity, filamentCutoffRatio,
                reducedModelInferenceTimeUs, degradedSafeHover, timestampMs);
        String expected = computeSha256(raw);
        return Objects.equals(this.sha256Signature, expected);
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
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
