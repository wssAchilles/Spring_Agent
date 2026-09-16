package tech.qiantong.qknow.hermes.transaction.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 不可变分布式事务与工作流弹性伸缩执行凭单 Record
 */
public record WorkflowTransactionReceipt(
        String receiptId,
        String transactionId,
        SagaTransactionStatus finalStatus,
        int totalForwardNodes,
        int compensatedNodes,
        int allocatedSlots,
        double semanticDriftDistance,
        long totalExecutionDurationNanos,
        long timestampEpochMs,
        String signature
) {
    public boolean verifyIntegrity() {
        String expected = calculateSignature(
                receiptId, transactionId, finalStatus, totalForwardNodes,
                compensatedNodes, allocatedSlots, semanticDriftDistance,
                totalExecutionDurationNanos, timestampEpochMs
        );
        return expected.equalsIgnoreCase(signature);
    }

    public static String calculateSignature(
            String receiptId, String transactionId, SagaTransactionStatus finalStatus,
            int totalForwardNodes, int compensatedNodes, int allocatedSlots,
            double semanticDriftDistance, long totalExecutionDurationNanos, long timestampEpochMs
    ) {
        String raw = String.format(
                "%s|%s|%s|%d|%d|%d|%.6f|%d|%d",
                receiptId, transactionId, finalStatus.name(), totalForwardNodes,
                compensatedNodes, allocatedSlots, semanticDriftDistance,
                totalExecutionDurationNanos, timestampEpochMs
        );
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
