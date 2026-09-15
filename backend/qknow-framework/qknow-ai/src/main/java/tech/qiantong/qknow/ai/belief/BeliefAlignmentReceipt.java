package tech.qiantong.qknow.ai.belief;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 跨层级信念状态对齐与自反博弈不可变存证凭单 (Java 21 Record)
 */
public record BeliefAlignmentReceipt(
        String receiptId,
        String coordinationRoundId,
        String macroAgentId,
        String microAgentId,
        String inferredIntent,
        double intentConfidence,
        double beliefEntropy,
        double jeffreysDivergence,
        int cognitiveLevel,
        String selectedAction,
        boolean barrierTriggered,
        boolean alignmentSuccessful,
        long timestamp,
        String sha256Signature
) {

    public static BeliefAlignmentReceipt create(
            String receiptId,
            String coordinationRoundId,
            String macroAgentId,
            String microAgentId,
            String inferredIntent,
            double intentConfidence,
            double beliefEntropy,
            double jeffreysDivergence,
            int cognitiveLevel,
            String selectedAction,
            boolean barrierTriggered,
            boolean alignmentSuccessful,
            long timestamp
    ) {
        String payload = String.format("%s|%s|%s|%s|%s|%.6f|%.6f|%.6f|%d|%s|%b|%b|%d",
                receiptId, coordinationRoundId, macroAgentId, microAgentId,
                inferredIntent, intentConfidence, beliefEntropy, jeffreysDivergence,
                cognitiveLevel, selectedAction, barrierTriggered, alignmentSuccessful, timestamp);
        String signature = computeSha256(payload);
        return new BeliefAlignmentReceipt(
                receiptId, coordinationRoundId, macroAgentId, microAgentId,
                inferredIntent, intentConfidence, beliefEntropy, jeffreysDivergence,
                cognitiveLevel, selectedAction, barrierTriggered, alignmentSuccessful,
                timestamp, signature
        );
    }

    public boolean verifySignature() {
        String payload = String.format("%s|%s|%s|%s|%s|%.6f|%.6f|%.6f|%d|%s|%b|%b|%d",
                receiptId, coordinationRoundId, macroAgentId, microAgentId,
                inferredIntent, intentConfidence, beliefEntropy, jeffreysDivergence,
                cognitiveLevel, selectedAction, barrierTriggered, alignmentSuccessful, timestamp);
        return computeSha256(payload).equalsIgnoreCase(sha256Signature);
    }

    private static String computeSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
