package tech.qiantong.qknow.module.kmc.service.rag.evolution.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 不可变知识图谱演化密码学存证凭单 (命题 2.1)
 */
public record KnowledgeEvolutionReceipt(
        String receiptId,
        String sessionId,
        int alignedEntityCount,
        int resolvedConflictCount,
        double ontologyContractionRatio,
        long elapsedMicros,
        String busStatus,
        String signature,
        long timestamp
) {
    public static KnowledgeEvolutionReceipt createAndSign(
            String receiptId,
            String sessionId,
            int alignedEntityCount,
            int resolvedConflictCount,
            double ontologyContractionRatio,
            long elapsedMicros,
            String busStatus,
            long timestamp
    ) {
        String rawData = receiptId + "|" + sessionId + "|" + alignedEntityCount + "|" +
                resolvedConflictCount + "|" + ontologyContractionRatio + "|" + elapsedMicros + "|" +
                busStatus + "|" + timestamp;
        String sig = computeSha256(rawData);
        return new KnowledgeEvolutionReceipt(
                receiptId, sessionId, alignedEntityCount, resolvedConflictCount,
                ontologyContractionRatio, elapsedMicros, busStatus, sig, timestamp
        );
    }

    public boolean verifySignature() {
        String rawData = receiptId + "|" + sessionId + "|" + alignedEntityCount + "|" +
                resolvedConflictCount + "|" + ontologyContractionRatio + "|" + elapsedMicros + "|" +
                busStatus + "|" + timestamp;
        return computeSha256(rawData).equalsIgnoreCase(signature);
    }

    private static String computeSha256(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
