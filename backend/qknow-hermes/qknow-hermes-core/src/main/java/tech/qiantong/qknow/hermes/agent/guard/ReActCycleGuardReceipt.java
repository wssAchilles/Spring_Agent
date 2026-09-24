package tech.qiantong.qknow.hermes.agent.guard;

import cn.hutool.crypto.digest.DigestUtil;

/**
 * ReAct 循环守卫不可变存证凭单 (Java 21 Record 格式)
 * 记录工具调用的指纹计算、窗口容量状态、熔断决策与 SHA-256 密码学自签名
 */
public record ReActCycleGuardReceipt(
        String receiptId,
        String sessionId,
        String toolName,
        String canonicalFingerprint,
        int windowCapacity,
        int windowSize,
        int currentFingerprintCount,
        boolean tripped,
        String trippedReason,
        long evaluatedAtNanos,
        String sha256Signature
) {

    /**
     * 生成带 SHA-256 防篡改自签名的存证凭单
     */
    public static ReActCycleGuardReceipt create(
            String receiptId,
            String sessionId,
            String toolName,
            String canonicalFingerprint,
            int windowCapacity,
            int windowSize,
            int currentFingerprintCount,
            boolean tripped,
            String trippedReason
    ) {
        long nowNanos = System.nanoTime();
        String payload = String.format("%s|%s|%s|%s|%d|%d|%d|%b|%s|%d",
                receiptId != null ? receiptId : "",
                sessionId != null ? sessionId : "",
                toolName != null ? toolName : "",
                canonicalFingerprint != null ? canonicalFingerprint : "",
                windowCapacity,
                windowSize,
                currentFingerprintCount,
                tripped,
                trippedReason != null ? trippedReason : "",
                nowNanos
        );
        String signature = DigestUtil.sha256Hex(payload);
        return new ReActCycleGuardReceipt(
                receiptId,
                sessionId,
                toolName,
                canonicalFingerprint,
                windowCapacity,
                windowSize,
                currentFingerprintCount,
                tripped,
                trippedReason,
                nowNanos,
                signature
        );
    }

    /**
     * 验证凭单的密码学自签名完整性
     */
    public boolean verifySignature() {
        String payload = String.format("%s|%s|%s|%s|%d|%d|%d|%b|%s|%d",
                receiptId != null ? receiptId : "",
                sessionId != null ? sessionId : "",
                toolName != null ? toolName : "",
                canonicalFingerprint != null ? canonicalFingerprint : "",
                windowCapacity,
                windowSize,
                currentFingerprintCount,
                tripped,
                trippedReason != null ? trippedReason : "",
                evaluatedAtNanos
        );
        String expected = DigestUtil.sha256Hex(payload);
        return expected.equals(sha256Signature);
    }
}
