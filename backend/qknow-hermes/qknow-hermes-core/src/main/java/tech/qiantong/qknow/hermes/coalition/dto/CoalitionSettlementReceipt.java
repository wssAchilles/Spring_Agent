package tech.qiantong.qknow.hermes.coalition.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 不可变密码学执行凭单 Record
 */
public record CoalitionSettlementReceipt(
    String receiptId,
    String coalitionId,
    String settlementId,
    int memberCount,
    double totalValue,
    double paretoUtility,
    double maxCredit,
    boolean isSovereigntyCompliant,
    long latencyUs,
    String sha256Signature,
    long timestamp
) {
    public CoalitionSettlementReceipt {
        if (receiptId == null || receiptId.isBlank()) {
            throw new IllegalArgumentException("receiptId 不能为空");
        }
    }

    /**
     * 自签名验真
     */
    public boolean verifyIntegrity() {
        String computed = computeSignature(receiptId, coalitionId, settlementId, totalValue, paretoUtility, timestamp);
        return computed.equalsIgnoreCase(sha256Signature);
    }

    /**
     * 计算 SHA-256 自签名
     */
    public static String computeSignature(
        String receiptId,
        String coalitionId,
        String settlementId,
        double totalValue,
        double paretoUtility,
        long timestamp
    ) {
        String payload = receiptId + "|" + coalitionId + "|" + settlementId + "|" + totalValue + "|" + paretoUtility + "|" + timestamp;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
