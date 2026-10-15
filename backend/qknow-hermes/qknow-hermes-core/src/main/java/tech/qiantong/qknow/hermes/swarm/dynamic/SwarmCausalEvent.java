package tech.qiantong.qknow.hermes.swarm.dynamic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 智能体因果时序事件不可变凭单
 * 承载跨智能体通信事件载荷，绑定因果向量时钟并提供密码学零时序泄漏签名防护
 */
public record SwarmCausalEvent(
        String eventId,
        String senderAgentId,
        String targetAgentId,
        String eventType,
        String payload,
        SwarmVectorClock vectorClock,
        long timestamp,
        String signature
) {
    public SwarmCausalEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(senderAgentId, "senderAgentId cannot be null");
        Objects.requireNonNull(targetAgentId, "targetAgentId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
        Objects.requireNonNull(vectorClock, "vectorClock cannot be null");
    }

    public static String calculateSignature(String eventId, String sender, String target,
                                            String type, String payload, long timestamp, String secretKey) {
        try {
            String raw = String.format("%s|%s|%s|%s|%s|%d|%s",
                    eventId, sender, target, type, payload, timestamp,
                    secretKey == null ? "DEFAULT_SWARM_EVENT_KEY" : secretKey);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public boolean verifySignature(String secretKey) {
        if (signature == null || signature.isBlank()) return false;
        String expected = calculateSignature(eventId, senderAgentId, targetAgentId, eventType, payload, timestamp, secretKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
