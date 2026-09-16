package tech.qiantong.qknow.ai.embodied.hybrid.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 不可变轮腿越障执行存证凭单。
 * <p>
 * 封装凭单 ID、会话 ID、地形分类、构型拓扑、力封闭安全裕度、姿态残差、冲量功、微秒级耗时与 SHA-256 密码学防篡改自签名。
 */
public record LegWheelLocomotionReceipt(
        String receiptId,
        String sessionId,
        String robotId,
        String terrainType,
        String topologyMode,
        double forceClosureMargin,
        double meanPitchRollDeviationDeg,
        double impulseDissipationWorkJ,
        double hocbfSafetyMargin,
        long kinematicsLatencyMicros,
        long qpLatencyMicros,
        String busState,
        long timestamp,
        String signature
) {
    public LegWheelLocomotionReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(robotId, "robotId 不能为空");
        Objects.requireNonNull(terrainType, "terrainType 不能为空");
        Objects.requireNonNull(topologyMode, "topologyMode 不能为空");
        Objects.requireNonNull(busState, "busState 不能为空");
        Objects.requireNonNull(signature, "signature 不能为空");
    }

    /**
     * 静态工厂方法，自动计算 SHA-256 签名并构建不可变凭单实例。
     */
    public static LegWheelLocomotionReceipt createAndSign(
            String receiptId,
            String sessionId,
            String robotId,
            String terrainType,
            String topologyMode,
            double forceClosureMargin,
            double meanPitchRollDeviationDeg,
            double impulseDissipationWorkJ,
            double hocbfSafetyMargin,
            long kinematicsLatencyMicros,
            long qpLatencyMicros,
            String busState,
            long timestamp
    ) {
        String payload = buildPayload(receiptId, sessionId, robotId, terrainType, topologyMode,
                forceClosureMargin, meanPitchRollDeviationDeg, impulseDissipationWorkJ,
                hocbfSafetyMargin, kinematicsLatencyMicros, qpLatencyMicros, busState, timestamp);
        String signature = computeSha256(payload);
        return new LegWheelLocomotionReceipt(
                receiptId, sessionId, robotId, terrainType, topologyMode,
                forceClosureMargin, meanPitchRollDeviationDeg, impulseDissipationWorkJ,
                hocbfSafetyMargin, kinematicsLatencyMicros, qpLatencyMicros,
                busState, timestamp, signature
        );
    }

    /**
     * 校验当前凭单密码学防篡改自签名有效性。
     */
    public boolean verifySignature() {
        String expectedPayload = buildPayload(receiptId, sessionId, robotId, terrainType, topologyMode,
                forceClosureMargin, meanPitchRollDeviationDeg, impulseDissipationWorkJ,
                hocbfSafetyMargin, kinematicsLatencyMicros, qpLatencyMicros, busState, timestamp);
        String expectedSignature = computeSha256(expectedPayload);
        return expectedSignature.equalsIgnoreCase(this.signature);
    }

    private static String buildPayload(
            String receiptId,
            String sessionId,
            String robotId,
            String terrainType,
            String topologyMode,
            double forceClosureMargin,
            double meanPitchRollDeviationDeg,
            double impulseDissipationWorkJ,
            double hocbfSafetyMargin,
            long kinematicsLatencyMicros,
            long qpLatencyMicros,
            String busState,
            long timestamp
    ) {
        return receiptId + "|" + sessionId + "|" + robotId + "|" + terrainType + "|" + topologyMode + "|" +
                String.format("%.4f", forceClosureMargin) + "|" +
                String.format("%.4f", meanPitchRollDeviationDeg) + "|" +
                String.format("%.4f", impulseDissipationWorkJ) + "|" +
                String.format("%.4f", hocbfSafetyMargin) + "|" +
                kinematicsLatencyMicros + "|" +
                qpLatencyMicros + "|" +
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
