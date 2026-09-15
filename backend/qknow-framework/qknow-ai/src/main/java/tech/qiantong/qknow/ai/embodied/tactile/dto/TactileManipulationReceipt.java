package tech.qiantong.qknow.ai.embodied.tactile.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * 不可变触觉操作存证凭单 (Java 21 Record)
 * <p>
 * 封装凭单唯一 ID、会话 ID、工件类型、基元类型、瞬时滑脱比、冲量平衡残差、
 * HOCBF 安全裕度、末态跟踪误差、单步耗时、执行状态、时间戳与 SHA-256 密码学自签名。
 */
public record TactileManipulationReceipt(
        String receiptId,
        String sessionId,
        String workpieceType,
        String primitiveType,
        double slipRatio,
        double momentumResidual,
        double hocbfSafetyMargin,
        double poseTrackingErrorMm,
        long stepLatencyUs,
        String executionStatus,
        long timestamp,
        String sha256Signature
) {
    public TactileManipulationReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为 null");
        Objects.requireNonNull(sessionId, "sessionId 不能为 null");
        Objects.requireNonNull(workpieceType, "workpieceType 不能为 null");
        Objects.requireNonNull(primitiveType, "primitiveType 不能为 null");
        Objects.requireNonNull(executionStatus, "executionStatus 不能为 null");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为 null");
    }

    public static TactileManipulationReceipt createAndSign(
            String receiptId,
            String sessionId,
            String workpieceType,
            String primitiveType,
            double slipRatio,
            double momentumResidual,
            double hocbfSafetyMargin,
            double poseTrackingErrorMm,
            long stepLatencyUs,
            String executionStatus
    ) {
        long now = System.currentTimeMillis();
        String payload = String.format(Locale.ROOT,
                "%s|%s|%s|%s|%.6f|%.6f|%.6f|%.4f|%d|%s|%d",
                receiptId, sessionId, workpieceType, primitiveType,
                slipRatio, momentumResidual, hocbfSafetyMargin, poseTrackingErrorMm,
                stepLatencyUs, executionStatus, now
        );
        String signature = computeSha256(payload);
        return new TactileManipulationReceipt(
                receiptId, sessionId, workpieceType, primitiveType,
                slipRatio, momentumResidual, hocbfSafetyMargin, poseTrackingErrorMm,
                stepLatencyUs, executionStatus, now, signature
        );
    }

    public boolean verifySignature() {
        String payload = String.format(Locale.ROOT,
                "%s|%s|%s|%s|%.6f|%.6f|%.6f|%.4f|%d|%s|%d",
                receiptId, sessionId, workpieceType, primitiveType,
                slipRatio, momentumResidual, hocbfSafetyMargin, poseTrackingErrorMm,
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
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing in JVM runtime", e);
        }
    }
}
