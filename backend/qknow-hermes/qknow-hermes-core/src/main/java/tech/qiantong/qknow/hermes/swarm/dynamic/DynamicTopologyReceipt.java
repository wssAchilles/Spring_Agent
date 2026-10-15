package tech.qiantong.qknow.hermes.swarm.dynamic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 动态拓扑激活与剪枝不可变审计凭单
 * 记录基于超球面测地散度激活的专家智能体列表、夏普利动态剪枝结果与耗时
 */
public record DynamicTopologyReceipt(
        String receiptId,
        String taskId,
        List<String> activatedAgents,
        List<String> prunedAgents,
        Map<String, Float> geodesicScores,
        long durationMicros,
        long timestamp,
        String signature
) {
    public DynamicTopologyReceipt {
        Objects.requireNonNull(receiptId, "receiptId cannot be null");
        Objects.requireNonNull(taskId, "taskId cannot be null");
        activatedAgents = activatedAgents == null ? Collections.emptyList() : Collections.unmodifiableList(activatedAgents);
        prunedAgents = prunedAgents == null ? Collections.emptyList() : Collections.unmodifiableList(prunedAgents);
        geodesicScores = geodesicScores == null ? Collections.emptyMap() : Collections.unmodifiableMap(geodesicScores);
    }

    public static String calculateSignature(String receiptId, String taskId, int activatedCount,
                                            int prunedCount, long durationMicros, long timestamp, String secretKey) {
        try {
            String raw = String.format("%s|%s|%d|%d|%d|%d|%s",
                    receiptId, taskId, activatedCount, prunedCount, durationMicros, timestamp,
                    secretKey == null ? "DEFAULT_TOPOLOGY_SECRET" : secretKey);
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
        String expected = calculateSignature(receiptId, taskId, activatedAgents.size(),
                prunedAgents.size(), durationMicros, timestamp, secretKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
