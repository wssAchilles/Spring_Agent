package tech.qiantong.qknow.hermes.streaming.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 不可变流式交互与拓扑自愈存证凭单 Java 21 Record
 * 内置 SHA-256 密码学自签名与验真方法
 */
public record StreamingInteractionReceipt(
        String receiptId,
        String sessionId,
        int topologyNodeCount,
        int healedNodeCount,
        int routedSliceCount,
        long hitlResumeElapsedMicros,
        String busStatus,
        long timestamp,
        String signature
) {
    public static StreamingInteractionReceipt createAndSign(
            String receiptId,
            String sessionId,
            int topologyNodeCount,
            int healedNodeCount,
            int routedSliceCount,
            long hitlResumeElapsedMicros,
            String busStatus,
            long timestamp
    ) {
        String data = receiptId + "|" + sessionId + "|" + topologyNodeCount + "|"
                + healedNodeCount + "|" + routedSliceCount + "|" + hitlResumeElapsedMicros + "|"
                + busStatus + "|" + timestamp;
        String sig = calculateSha256(data);
        return new StreamingInteractionReceipt(
                receiptId, sessionId, topologyNodeCount, healedNodeCount,
                routedSliceCount, hitlResumeElapsedMicros, busStatus, timestamp, sig
        );
    }

    public boolean verifySignature() {
        String data = receiptId + "|" + sessionId + "|" + topologyNodeCount + "|"
                + healedNodeCount + "|" + routedSliceCount + "|" + hitlResumeElapsedMicros + "|"
                + busStatus + "|" + timestamp;
        return calculateSha256(data).equalsIgnoreCase(signature);
    }

    private static String calculateSha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }
}
