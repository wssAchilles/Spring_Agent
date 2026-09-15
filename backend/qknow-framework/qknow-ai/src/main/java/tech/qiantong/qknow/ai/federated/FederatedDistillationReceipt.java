package tech.qiantong.qknow.ai.federated;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * 联邦记忆蒸馏不可变密码学存证凭单
 * <p>
 * 记录联邦轮次、参与智能体签名、拓扑收缩比、语义压缩比与全局元记忆哈希。
 *
 * @author Qknow AI Team
 * @since 2026-09-15
 */
public record FederatedDistillationReceipt(
        String receiptId,
        String roundId,
        List<String> participatingAgentIds,
        int originalEdgesCount,
        int contractedEdgesCount,
        double compressionRatio,
        String globalMetaMemoryHash,
        long timestamp,
        String receiptHash
) {

    public FederatedDistillationReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(roundId, "roundId 不能为空");
        Objects.requireNonNull(participatingAgentIds, "participatingAgentIds 不能为空");
        Objects.requireNonNull(globalMetaMemoryHash, "globalMetaMemoryHash 不能为空");
        Objects.requireNonNull(receiptHash, "receiptHash 不能为空");
    }

    public static FederatedDistillationReceipt create(
            String receiptId,
            String roundId,
            List<String> participatingAgentIds,
            int originalEdgesCount,
            int contractedEdgesCount,
            double compressionRatio,
            String globalMetaMemoryHash,
            long timestamp
    ) {
        String participantsStr = String.join(",", participatingAgentIds != null ? participatingAgentIds : List.of());
        String contentToHash = String.format("%s|%s|%s|%d|%d|%.4f|%s|%d",
                receiptId, roundId, participantsStr, originalEdgesCount,
                contractedEdgesCount, compressionRatio, globalMetaMemoryHash, timestamp);
        String hash = computeSha256(contentToHash);
        return new FederatedDistillationReceipt(
                receiptId, roundId, participatingAgentIds, originalEdgesCount,
                contractedEdgesCount, compressionRatio, globalMetaMemoryHash, timestamp, hash
        );
    }

    public boolean verifyIntegrity() {
        String participantsStr = String.join(",", participatingAgentIds != null ? participatingAgentIds : List.of());
        String contentToHash = String.format("%s|%s|%s|%d|%d|%.4f|%s|%d",
                receiptId, roundId, participantsStr, originalEdgesCount,
                contractedEdgesCount, compressionRatio, globalMetaMemoryHash, timestamp);
        String expected = computeSha256(contentToHash);
        return expected.equalsIgnoreCase(this.receiptHash);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }
}
