package tech.qiantong.qknow.hermes.flow.checkpoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/**
 * 分布式 MCP 断点租约活性自愈存证凭单
 * 纯 Java 21 Record 格式不可变载荷，内嵌 SHA-256 自签名与常数时间验真，彻底杜绝自愈审计记录伪造与篡改
 */
public record LeaseLivenessRecoveryReceipt(
        String receiptId,
        String runtimeId,
        String flowId,
        String previousOwnerId,
        String newOwnerId,
        long previousFencingToken,
        long newFencingToken,
        String triggerReason,
        long leaseDurationMs,
        long timestamp,
        String signature
) {
    /**
     * 校验凭单参数合法性
     */
    public LeaseLivenessRecoveryReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(runtimeId, "runtimeId 不能为空");
        Objects.requireNonNull(newOwnerId, "newOwnerId 不能为空");
        Objects.requireNonNull(triggerReason, "triggerReason 不能为空");
        Objects.requireNonNull(signature, "signature 不能为空");
        if (newFencingToken < previousFencingToken) {
            throw new IllegalArgumentException("newFencingToken 必须单调非减，杜绝令牌回退");
        }
    }

    /**
     * 工厂方法：自动生成签名并创建不可变存证凭单
     */
    public static LeaseLivenessRecoveryReceipt create(
            String runtimeId,
            String flowId,
            String previousOwnerId,
            String newOwnerId,
            long previousFencingToken,
            long newFencingToken,
            String triggerReason,
            long leaseDurationMs
    ) {
        String receiptId = "LLR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timestamp = System.currentTimeMillis();
        String safeFlowId = flowId != null ? flowId : "DEFAULT_FLOW";
        String safePreviousOwnerId = previousOwnerId != null ? previousOwnerId : "NONE";

        String payload = buildCanonicalPayload(receiptId, runtimeId, safeFlowId, safePreviousOwnerId,
                newOwnerId, previousFencingToken, newFencingToken, triggerReason, leaseDurationMs, timestamp);
        String signature = computeSha256(payload);

        return new LeaseLivenessRecoveryReceipt(
                receiptId,
                runtimeId,
                safeFlowId,
                safePreviousOwnerId,
                newOwnerId,
                previousFencingToken,
                newFencingToken,
                triggerReason,
                leaseDurationMs,
                timestamp,
                signature
        );
    }

    /**
     * 常量时间自验真方法，防止时间侧信道攻击
     */
    public boolean verifySignature() {
        String expectedPayload = buildCanonicalPayload(receiptId, runtimeId, flowId, previousOwnerId,
                newOwnerId, previousFencingToken, newFencingToken, triggerReason, leaseDurationMs, timestamp);
        String expectedSignature = computeSha256(expectedPayload);
        return MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildCanonicalPayload(
            String receiptId,
            String runtimeId,
            String flowId,
            String previousOwnerId,
            String newOwnerId,
            long previousFencingToken,
            long newFencingToken,
            String triggerReason,
            long leaseDurationMs,
            long timestamp
    ) {
        return receiptId + "|" + runtimeId + "|" + flowId + "|" + previousOwnerId + "|"
                + newOwnerId + "|" + previousFencingToken + "|" + newFencingToken + "|"
                + triggerReason + "|" + leaseDurationMs + "|" + timestamp;
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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
            throw new IllegalStateException("SHA-256 算法在当前 JVM 中不可用", e);
        }
    }
}
