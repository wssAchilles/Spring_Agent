package tech.qiantong.qknow.ai.embodied.jumping.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 具身智能体高动态弹跳越障操作不可变密码学存证凭单。
 * 内置 SHA-256 密码学自签名与验真方法。
 *
 * @param receiptId 凭单唯一 ID
 * @param sessionId 会话 ID
 * @param robotId 机器人物理 ID
 * @param obstacleHeightMeters 越障高度 (m)
 * @param takeoffImpulseNs 起跳净推力冲量 (N*s)
 * @param apexHeightMeters 飞行弹道顶点高度 (m)
 * @param touchAttitudeErrorDeg 触地瞬态机身姿态残差 (度)
 * @param energyAbsorptionRatio 落地冲击动能吸收率
 * @param hocbfMargin HOCBF 安全裕度
 * @param solveLatencyMicros 单步闭式求解耗时微秒
 * @param degradedModeActivated 是否激活柔顺趴地降级软着陆
 * @param degradationReason 降级触发原因
 * @param digitalSignature 密码学数字签名
 * @param timestamp 时间戳毫秒
 */
public record ExtremeJumpingReceipt(
        String receiptId,
        String sessionId,
        String robotId,
        double obstacleHeightMeters,
        double takeoffImpulseNs,
        double apexHeightMeters,
        double touchAttitudeErrorDeg,
        double energyAbsorptionRatio,
        double hocbfMargin,
        long solveLatencyMicros,
        boolean degradedModeActivated,
        String degradationReason,
        String digitalSignature,
        long timestamp
) {
    public ExtremeJumpingReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(robotId, "robotId 不能为空");
        Objects.requireNonNull(digitalSignature, "digitalSignature 不能为空");
    }

    /**
     * 计算内容摘要哈希字符串。
     */
    public static String computeDigest(String receiptId, String sessionId, String robotId,
                                      double obstacleHeightMeters, double takeoffImpulseNs,
                                      double apexHeightMeters, double touchAttitudeErrorDeg,
                                      double energyAbsorptionRatio, double hocbfMargin,
                                      long solveLatencyMicros, boolean degradedModeActivated,
                                      String degradationReason, long timestamp) {
        String payload = String.format("%s|%s|%s|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f|%d|%b|%s|%d",
                receiptId, sessionId, robotId, obstacleHeightMeters, takeoffImpulseNs,
                apexHeightMeters, touchAttitudeErrorDeg, energyAbsorptionRatio, hocbfMargin,
                solveLatencyMicros, degradedModeActivated, degradationReason == null ? "NONE" : degradationReason,
                timestamp);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * 工厂方法：构建并自动生成 SHA-256 签名凭单。
     */
    public static ExtremeJumpingReceipt createAndSign(String receiptId, String sessionId, String robotId,
                                                      double obstacleHeightMeters, double takeoffImpulseNs,
                                                      double apexHeightMeters, double touchAttitudeErrorDeg,
                                                      double energyAbsorptionRatio, double hocbfMargin,
                                                      long solveLatencyMicros, boolean degradedModeActivated,
                                                      String degradationReason, long timestamp) {
        String signature = computeDigest(receiptId, sessionId, robotId, obstacleHeightMeters, takeoffImpulseNs,
                apexHeightMeters, touchAttitudeErrorDeg, energyAbsorptionRatio, hocbfMargin,
                solveLatencyMicros, degradedModeActivated, degradationReason, timestamp);
        return new ExtremeJumpingReceipt(receiptId, sessionId, robotId, obstacleHeightMeters, takeoffImpulseNs,
                apexHeightMeters, touchAttitudeErrorDeg, energyAbsorptionRatio, hocbfMargin,
                solveLatencyMicros, degradedModeActivated, degradationReason, signature, timestamp);
    }

    /**
     * 验证凭单签名防篡改完整性。
     */
    public boolean verifySignature() {
        String expected = computeDigest(receiptId, sessionId, robotId, obstacleHeightMeters, takeoffImpulseNs,
                apexHeightMeters, touchAttitudeErrorDeg, energyAbsorptionRatio, hocbfMargin,
                solveLatencyMicros, degradedModeActivated, degradationReason, timestamp);
        return expected.equalsIgnoreCase(digitalSignature);
    }
}
