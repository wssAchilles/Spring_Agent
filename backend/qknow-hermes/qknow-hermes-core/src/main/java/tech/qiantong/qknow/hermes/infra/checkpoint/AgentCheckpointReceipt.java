package tech.qiantong.qknow.hermes.infra.checkpoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 智能体持久化快照凭单
 * 严格基于 Java 21 Record 规范，提供常量时间 MessageDigest.isEqual 防侧信道验签
 */
public record AgentCheckpointReceipt(
        String taskId,
        String checkpointId,
        long version,
        String parentCheckpointId,
        String activeNodeId,
        long saveLatencyMicros,
        String sha256Signature
) {
    public static AgentCheckpointReceipt create(
            String taskId,
            String checkpointId,
            long version,
            String parentCheckpointId,
            String activeNodeId,
            long saveLatencyMicros
    ) {
        String signature = generateSignature(taskId, checkpointId, version, parentCheckpointId, activeNodeId);
        return new AgentCheckpointReceipt(
                taskId,
                checkpointId,
                version,
                parentCheckpointId,
                activeNodeId,
                saveLatencyMicros,
                signature
        );
    }

    private static String generateSignature(
            String taskId,
            String checkpointId,
            long version,
            String parentCheckpointId,
            String activeNodeId
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String payload = String.format("%s|%s|%d|%s|%s",
                    taskId, checkpointId, version,
                    parentCheckpointId != null ? parentCheckpointId : "ROOT",
                    activeNodeId);
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }

    /**
     * 常量时间比对防时序侧信道攻击验签
     */
    public boolean verifySignature() {
        String expected = generateSignature(taskId, checkpointId, version, parentCheckpointId, activeNodeId);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
