package tech.qiantong.qknow.ai.speculative;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Phase 63: 投机执行与端云认知同步不可变存证凭单 (Record)
 * <p>
 * 具备密码学防篡改特性，内置 SHA-256 自签名与验真逻辑。
 */
public record SpeculativeExecutionReceipt(
        String receiptId,
        long epoch,
        String intentDraft,
        String fsmState,
        boolean speculativeHit,
        boolean speculativeAborted,
        long latencySavedMs,
        String contextHash,
        String decisionSummary,
        long timestamp,
        String sha256Signature
) {

    /**
     * 工厂方法：构建并自动生成 SHA-256 防篡改签名
     */
    public static SpeculativeExecutionReceipt create(
            String receiptId,
            long epoch,
            String intentDraft,
            String fsmState,
            boolean speculativeHit,
            boolean speculativeAborted,
            long latencySavedMs,
            String contextHash,
            String decisionSummary,
            long timestamp
    ) {
        String signature = computeHash(
                receiptId, epoch, intentDraft, fsmState,
                speculativeHit, speculativeAborted, latencySavedMs,
                contextHash, decisionSummary, timestamp
        );
        return new SpeculativeExecutionReceipt(
                receiptId, epoch, intentDraft, fsmState,
                speculativeHit, speculativeAborted, latencySavedMs,
                contextHash, decisionSummary, timestamp, signature
        );
    }

    /**
     * 自校验签名防篡改
     */
    public boolean verifySignature() {
        String expectedHash = computeHash(
                receiptId, epoch, intentDraft, fsmState,
                speculativeHit, speculativeAborted, latencySavedMs,
                contextHash, decisionSummary, timestamp
        );
        return Objects.equals(this.sha256Signature, expectedHash);
    }

    private static String computeHash(
            String receiptId,
            long epoch,
            String intentDraft,
            String fsmState,
            boolean speculativeHit,
            boolean speculativeAborted,
            long latencySavedMs,
            String contextHash,
            String decisionSummary,
            long timestamp
    ) {
        String raw = receiptId + "|" + epoch + "|" + intentDraft + "|" + fsmState + "|" +
                speculativeHit + "|" + speculativeAborted + "|" + latencySavedMs + "|" +
                contextHash + "|" + decisionSummary + "|" + timestamp;
        try {
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
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
