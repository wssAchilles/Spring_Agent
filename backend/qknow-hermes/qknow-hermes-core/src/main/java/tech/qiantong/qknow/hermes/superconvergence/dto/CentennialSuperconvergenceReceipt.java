package tech.qiantong.qknow.hermes.superconvergence.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 不可变密码学世纪执行凭单 Record
 */
public record CentennialSuperconvergenceReceipt(
    String receiptId,
    String sessionId,
    AgentOsLifecycleState finalState,
    double residualEnergy,
    double metacognitiveDriftRate,
    boolean isBarrierCompliant,
    long busSequence,
    long processingLatencyNanos,
    String sha256Signature,
    long timestamp
) {
    public CentennialSuperconvergenceReceipt {
        if (receiptId == null || receiptId.isBlank()) {
            throw new IllegalArgumentException("receiptId 不能为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (finalState == null) {
            throw new IllegalArgumentException("finalState 不能为空");
        }
    }

    /**
     * 自签名验真
     */
    public boolean verifyIntegrity() {
        String computed = computeSignature(receiptId, sessionId, finalState, residualEnergy, metacognitiveDriftRate, busSequence, timestamp);
        return computed.equalsIgnoreCase(sha256Signature);
    }

    /**
     * 计算 SHA-256 自签名
     */
    public static String computeSignature(
        String receiptId,
        String sessionId,
        AgentOsLifecycleState finalState,
        double residualEnergy,
        double metacognitiveDriftRate,
        long busSequence,
        long timestamp
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.format(
                "%s|%s|%s|%.6f|%.6f|%d|%d",
                receiptId,
                sessionId,
                finalState.name(),
                residualEnergy,
                metacognitiveDriftRate,
                busSequence,
                timestamp
            );
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法获取 SHA-256 算法", e);
        }
    }
}
