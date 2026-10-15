package tech.qiantong.qknow.hermes.flow.hitl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 人机协同 (HITL) 中断挂起与恢复不可变凭单
 * 严格遵循 Java 21 Record 规范，提供密码学常量时间安全比对验签
 */
public record HitlInterruptReceipt(
        String executionId,
        String nodeId,
        String interruptToken,
        String status, // SUSPENDED_WAITING_HUMAN, RESUMED_APPROVED, RESUMED_REJECTED, EXPIRED_REVOKED
        long suspendedEpochMillis,
        long ttlMillis,
        String requiredRole,
        String actionDescription,
        String sha256Signature
) {
    public static HitlInterruptReceipt createSuspended(
            String executionId,
            String nodeId,
            String interruptToken,
            long suspendedEpochMillis,
            long ttlMillis,
            String requiredRole,
            String actionDescription
    ) {
        String status = "SUSPENDED_WAITING_HUMAN";
        String signature = generateSignature(executionId, nodeId, interruptToken, status, suspendedEpochMillis, requiredRole);
        return new HitlInterruptReceipt(
                executionId,
                nodeId,
                interruptToken,
                status,
                suspendedEpochMillis,
                ttlMillis,
                requiredRole,
                actionDescription,
                signature
        );
    }

    public static HitlInterruptReceipt createResolved(
            String executionId,
            String nodeId,
            String interruptToken,
            String resolvedStatus,
            long suspendedEpochMillis,
            long ttlMillis,
            String requiredRole,
            String actionDescription
    ) {
        String signature = generateSignature(executionId, nodeId, interruptToken, resolvedStatus, suspendedEpochMillis, requiredRole);
        return new HitlInterruptReceipt(
                executionId,
                nodeId,
                interruptToken,
                resolvedStatus,
                suspendedEpochMillis,
                ttlMillis,
                requiredRole,
                actionDescription,
                signature
        );
    }

    private static String generateSignature(
            String executionId,
            String nodeId,
            String interruptToken,
            String status,
            long suspendedEpochMillis,
            String requiredRole
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String payload = String.format("%s|%s|%s|%s|%d|%s",
                    executionId, nodeId, interruptToken, status, suspendedEpochMillis, requiredRole);
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256 算法实现", e);
        }
    }

    /**
     * 常量时间比对防时序侧信道攻击验签
     */
    public boolean verifySignature() {
        String expected = generateSignature(executionId, nodeId, interruptToken, status, suspendedEpochMillis, requiredRole);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
