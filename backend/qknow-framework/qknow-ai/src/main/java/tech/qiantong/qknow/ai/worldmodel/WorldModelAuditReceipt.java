package tech.qiantong.qknow.ai.worldmodel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 世界模型推演、意图协商与控制屏障不可变存证收据 (Java 21 Record, SHA-256 存证留痕)
 */
public record WorldModelAuditReceipt(
        String receiptId,
        String sessionId,
        Map<String, String> jointIntents,
        String predictedStateHash,
        double barrierMargin,
        Set<String> quarantinedActions,
        long executionDurationMs,
        String tamperProofHash,
        long timestampMs
) {

    public static WorldModelAuditReceipt createReceipt(
            String sessionId,
            Map<String, String> jointIntents,
            String predictedStateHash,
            double barrierMargin,
            Set<String> quarantinedActions,
            long executionDurationMs
    ) {
        long now = System.currentTimeMillis();
        String receiptId = "WM-RCPT-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> intents = Collections.unmodifiableMap(new LinkedHashMap<>(jointIntents != null ? jointIntents : Map.of()));
        Set<String> quarantined = Collections.unmodifiableSet(new TreeSet<>(quarantinedActions != null ? quarantinedActions : Set.of()));
        String stateHash = predictedStateHash != null ? predictedStateHash : "HASH_EMPTY_STATE";

        String hash = computeHash(receiptId, sessionId, intents, stateHash, barrierMargin, quarantined, executionDurationMs, now);

        return new WorldModelAuditReceipt(
                receiptId, sessionId, intents, stateHash, barrierMargin, quarantined, executionDurationMs, hash, now
        );
    }

    /**
     * 验证存证收据 SHA-256 哈希防篡改完整性
     */
    public boolean verifyIntegrity() {
        String expected = computeHash(receiptId, sessionId, jointIntents, predictedStateHash, barrierMargin, quarantinedActions, executionDurationMs, timestampMs);
        return expected.equalsIgnoreCase(tamperProofHash);
    }

    private static String computeHash(
            String receiptId, String sessionId, Map<String, String> intents,
            String stateHash, double margin, Set<String> quarantined,
            long duration, long ts
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = receiptId + ":" + sessionId + ":" + intents.hashCode() + ":"
                    + stateHash + ":" + String.format(Locale.US, "%.4f", margin) + ":"
                    + quarantined.hashCode() + ":" + duration + ":" + ts;
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "WM_HASH_" + receiptId + "_" + ts;
        }
    }
}
