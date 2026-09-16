package tech.qiantong.qknow.ai.embodied.continuum.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 不可变连续体软体臂伺服执行存证凭单。
 * <p>
 * 封装全周期曲率、力控残差、腔压向量、HOCBF 裕度、微秒级耗时与 SHA-256 防篡改签名。
 */
public record ContinuumServoingReceipt(
        String receiptId,
        String sessionId,
        String softArmId,
        double[] meanCurvature,
        double forceTrackingMarginN,
        double[] chamberPressuresKPa,
        double hocbfSafetyMargin,
        long forwardDynamicsLatencyMicros,
        long qpProjectionLatencyMicros,
        String busState,
        long timestamp,
        String signature
) {
    public ContinuumServoingReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(softArmId, "softArmId 不能为空");
        Objects.requireNonNull(meanCurvature, "meanCurvature 不能为空");
        Objects.requireNonNull(chamberPressuresKPa, "chamberPressuresKPa 不能为空");
        Objects.requireNonNull(busState, "busState 不能为空");
        Objects.requireNonNull(signature, "signature 不能为空");
    }

    /**
     * 构建并自动生成 SHA-256 签名的存证凭单。
     */
    public static ContinuumServoingReceipt createAndSign(
            String receiptId,
            String sessionId,
            String softArmId,
            double[] meanCurvature,
            double forceTrackingMarginN,
            double[] chamberPressuresKPa,
            double hocbfSafetyMargin,
            long forwardDynamicsLatencyMicros,
            long qpProjectionLatencyMicros,
            String busState,
            long timestamp
    ) {
        String payload = buildPayload(receiptId, sessionId, softArmId, meanCurvature,
                forceTrackingMarginN, chamberPressuresKPa, hocbfSafetyMargin,
                forwardDynamicsLatencyMicros, qpProjectionLatencyMicros, busState, timestamp);
        String signature = computeSha256(payload);
        return new ContinuumServoingReceipt(receiptId, sessionId, softArmId, meanCurvature,
                forceTrackingMarginN, chamberPressuresKPa, hocbfSafetyMargin,
                forwardDynamicsLatencyMicros, qpProjectionLatencyMicros, busState, timestamp, signature);
    }

    /**
     * 校验自身 SHA-256 签名完整性。
     */
    public boolean verifySignature() {
        String expectedPayload = buildPayload(receiptId, sessionId, softArmId, meanCurvature,
                forceTrackingMarginN, chamberPressuresKPa, hocbfSafetyMargin,
                forwardDynamicsLatencyMicros, qpProjectionLatencyMicros, busState, timestamp);
        String expectedSignature = computeSha256(expectedPayload);
        return expectedSignature.equalsIgnoreCase(this.signature);
    }

    private static String buildPayload(
            String receiptId,
            String sessionId,
            String softArmId,
            double[] meanCurvature,
            double forceTrackingMarginN,
            double[] chamberPressuresKPa,
            double hocbfSafetyMargin,
            long forwardDynamicsLatencyMicros,
            long qpProjectionLatencyMicros,
            String busState,
            long timestamp
    ) {
        return receiptId + "|" + sessionId + "|" + softArmId + "|" +
                Arrays.toString(meanCurvature) + "|" +
                String.format("%.4f", forceTrackingMarginN) + "|" +
                Arrays.toString(chamberPressuresKPa) + "|" +
                String.format("%.4f", hocbfSafetyMargin) + "|" +
                forwardDynamicsLatencyMicros + "|" +
                qpProjectionLatencyMicros + "|" +
                busState + "|" + timestamp;
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
