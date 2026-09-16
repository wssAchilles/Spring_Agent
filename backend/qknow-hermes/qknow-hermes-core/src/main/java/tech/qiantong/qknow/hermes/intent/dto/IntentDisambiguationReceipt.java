package tech.qiantong.qknow.hermes.intent.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 不可变意图消歧与状态机交互存证凭单 Java 21 Record
 * 内置 SHA-256 密码学防篡改自签名与验真方法
 */
public record IntentDisambiguationReceipt(
        String receiptId,
        String sessionId,
        String resolvedIntent,
        IntentFsmState finalState,
        double residualEntropy,
        double geodesicMargin,
        int clarificationRounds,
        double elapsedMicros,
        String busStatus,
        long timestamp,
        String signature
) {
    public IntentDisambiguationReceipt(
            String receiptId,
            String sessionId,
            String resolvedIntent,
            IntentFsmState finalState,
            double residualEntropy,
            double geodesicMargin,
            int clarificationRounds,
            double elapsedMicros,
            String busStatus,
            long timestamp
    ) {
        this(
                receiptId, sessionId, resolvedIntent, finalState,
                residualEntropy, geodesicMargin, clarificationRounds,
                elapsedMicros, busStatus, timestamp,
                calculateSha256(receiptId + "|" + sessionId + "|" + resolvedIntent + "|"
                        + finalState + "|" + residualEntropy + "|" + geodesicMargin + "|"
                        + clarificationRounds + "|" + elapsedMicros + "|" + busStatus + "|" + timestamp)
        );
    }

    public boolean verifySignature() {
        String data = receiptId + "|" + sessionId + "|" + resolvedIntent + "|"
                + finalState + "|" + residualEntropy + "|" + geodesicMargin + "|"
                + clarificationRounds + "|" + elapsedMicros + "|" + busStatus + "|" + timestamp;
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
