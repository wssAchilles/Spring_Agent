package tech.qiantong.qknow.mcp.core.orchestration.dto;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 不可变幂等执行存证凭单 Java 21 Record
 * 记录工具幂等调用执行凭证与 SHA-256 密码学自签名，坚决杜绝重复写操作
 */
public record IdempotentExecutionReceipt(
        String receiptId,
        String idempotencyKey,
        String toolId,
        String status, // COMMITTED, REPLAYED, BLOCKED, COMPENSATING
        String payloadHash,
        String responsePayload,
        long latencyMicros,
        long timestamp,
        String signature
) implements Serializable {

    public static final String STATUS_COMMITTED = "COMMITTED";
    public static final String STATUS_REPLAYED = "REPLAYED";
    public static final String STATUS_BLOCKED = "BLOCKED";
    public static final String STATUS_COMPENSATING = "COMPENSATING";

    public IdempotentExecutionReceipt {
        if (receiptId == null || receiptId.isBlank()) {
            throw new IllegalArgumentException("receiptId 不能为空");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey 不能为空");
        }
        if (toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("toolId 不能为空");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status 不能为空");
        }
    }

    /**
     * 计算预期的 SHA-256 签名
     */
    public static String calculateSignature(String receiptId, String idempotencyKey, String toolId,
                                            String status, String payloadHash, long timestamp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = receiptId + "|" + idempotencyKey + "|" + toolId + "|" + status + "|" + payloadHash + "|" + timestamp;
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 摘要计算失败", e);
        }
    }

    /**
     * 验证自签名真实性
     */
    public boolean verifySignature() {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = calculateSignature(receiptId, idempotencyKey, toolId, status, payloadHash, timestamp);
        return signature.equalsIgnoreCase(expected);
    }
}
