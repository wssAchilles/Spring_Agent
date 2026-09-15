package tech.qiantong.qknow.ai.embodied.deformable.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * 不可变可形变软体流形操作存证凭单
 * <p>
 * 封装会话 ID、物体 ID、平均弹性势能、最大 von Mises 等效应力、材料屈服安全裕度、
 * 触觉微滑脱指标、PINO 前向推理耗时、总线降级标志与 SHA-256 密码学防篡改签名。
 *
 * @param receiptId            凭单唯一存证标识
 * @param sessionId            操作会话 ID
 * @param objectId             可形变物体 ID
 * @param averageStrainEnergy  平均弹性应变能 (J)
 * @param maxVonMisesStress    内部最大等效应力 (Pa)
 * @param stressMargin         材料抗拉伸/屈服极限安全裕度 (Pa)
 * @param tactileSlipMetric    触觉局部微滑脱风险指标 [0.0, 1.0]
 * @param pinoInferenceTimeUs  PINO 算子前向推演单步耗时 (微秒)
 * @param degradedBusMode      是否处于 DEGRADED_COMPLIANT_HOLD 柔顺持握降级模式
 * @param timestampMs          凭单签发物理时间戳 (ms)
 * @param sha256Signature      SHA-256 密码学防篡改签名
 */
public record DeformableManipulationReceipt(
        String receiptId,
        String sessionId,
        String objectId,
        double averageStrainEnergy,
        double maxVonMisesStress,
        double stressMargin,
        double tactileSlipMetric,
        long pinoInferenceTimeUs,
        boolean degradedBusMode,
        long timestampMs,
        String sha256Signature
) {
    public DeformableManipulationReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(objectId, "objectId 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
    }

    /**
     * 工厂方法：计算并自动签署 SHA-256 防篡改签名
     */
    public static DeformableManipulationReceipt sign(
            String receiptId,
            String sessionId,
            String objectId,
            double averageStrainEnergy,
            double maxVonMisesStress,
            double stressMargin,
            double tactileSlipMetric,
            long pinoInferenceTimeUs,
            boolean degradedBusMode,
            long timestampMs
    ) {
        String raw = String.format(Locale.US, "%s|%s|%s|%.6f|%.6f|%.6f|%.6f|%d|%b|%d",
                receiptId, sessionId, objectId, averageStrainEnergy, maxVonMisesStress,
                stressMargin, tactileSlipMetric, pinoInferenceTimeUs, degradedBusMode, timestampMs);
        String signature = computeSha256(raw);
        return new DeformableManipulationReceipt(
                receiptId, sessionId, objectId, averageStrainEnergy, maxVonMisesStress,
                stressMargin, tactileSlipMetric, pinoInferenceTimeUs, degradedBusMode,
                timestampMs, signature
        );
    }

    /**
     * 校验当前凭单的密码学签名自洽性
     *
     * @return 签名有效返回 true，否则返回 false
     */
    public boolean verifySignature() {
        String raw = String.format(Locale.US, "%s|%s|%s|%.6f|%.6f|%.6f|%.6f|%d|%b|%d",
                receiptId, sessionId, objectId, averageStrainEnergy, maxVonMisesStress,
                stressMargin, tactileSlipMetric, pinoInferenceTimeUs, degradedBusMode, timestampMs);
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
