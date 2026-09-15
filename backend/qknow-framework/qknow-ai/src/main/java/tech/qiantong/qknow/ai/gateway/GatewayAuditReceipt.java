package tech.qiantong.qknow.ai.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 零信任网关不可变密码学存证凭单
 * <p>
 * 遵循 Java 21 Record 规范，记录路由路径、RTT 时延、防重放 Nonce 与签名等核心审计属性。
 *
 * @author Qknow AI Team
 * @since 2026-09-15
 */
public record GatewayAuditReceipt(
        String receiptId,
        String msgId,
        String senderId,
        String targetAgentId,
        String routingPath,
        long rttLatencyMs,
        String nonce,
        String clientSignature,
        String abacDecision,
        long timestamp,
        String receiptHash
) {

    public GatewayAuditReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(msgId, "msgId 不能为空");
        Objects.requireNonNull(senderId, "senderId 不能为空");
        Objects.requireNonNull(targetAgentId, "targetAgentId 不能为空");
        Objects.requireNonNull(receiptHash, "receiptHash 不能为空");
    }

    /**
     * 实例化并自动计算 SHA-256 存证哈希
     */
    public static GatewayAuditReceipt create(
            String receiptId,
            String msgId,
            String senderId,
            String targetAgentId,
            String routingPath,
            long rttLatencyMs,
            String nonce,
            String clientSignature,
            String abacDecision,
            long timestamp
    ) {
        String contentToHash = String.format("%s|%s|%s|%s|%s|%d|%s|%s|%s|%d",
                receiptId, msgId, senderId, targetAgentId, routingPath,
                rttLatencyMs, nonce, clientSignature, abacDecision, timestamp);
        String hash = computeSha256(contentToHash);
        return new GatewayAuditReceipt(
                receiptId, msgId, senderId, targetAgentId, routingPath,
                rttLatencyMs, nonce, clientSignature, abacDecision, timestamp, hash
        );
    }

    /**
     * 密码学防篡改自校验
     */
    public boolean verifyIntegrity() {
        String contentToHash = String.format("%s|%s|%s|%s|%s|%d|%s|%s|%s|%d",
                receiptId, msgId, senderId, targetAgentId, routingPath,
                rttLatencyMs, nonce, clientSignature, abacDecision, timestamp);
        String expectedHash = computeSha256(contentToHash);
        return expectedHash.equalsIgnoreCase(this.receiptHash);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }
}
