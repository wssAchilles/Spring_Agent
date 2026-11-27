package tech.qiantong.qknow.hermes.consensus.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/**
 * 不可变零信任决策存证凭单 Java 21 Record
 * 内置 SHA-256 密码学防篡改自签名与验真方法
 */
public record ZeroTrustDecisionVoucher(
        String voucherId,
        String sessionId,
        String topic,
        String winningProposal,
        double quorumPercentage,
        int totalRounds,
        double nashResidualMargin,
        List<String> participantSignatures,
        double elapsedMicros,
        String busStatus,
        long timestamp,
        String signature
) {
    public ZeroTrustDecisionVoucher(
            String voucherId,
            String sessionId,
            String topic,
            String winningProposal,
            double quorumPercentage,
            int totalRounds,
            double nashResidualMargin,
            List<String> participantSignatures,
            double elapsedMicros,
            String busStatus,
            long timestamp
    ) {
        this(
                voucherId, sessionId, topic, winningProposal,
                quorumPercentage, totalRounds, nashResidualMargin,
                participantSignatures, elapsedMicros, busStatus, timestamp,
                calculateSha256(voucherId + "|" + sessionId + "|" + topic + "|" + winningProposal + "|"
                        + quorumPercentage + "|" + totalRounds + "|" + nashResidualMargin + "|"
                        + (participantSignatures != null ? String.join(",", participantSignatures) : "") + "|"
                        + elapsedMicros + "|" + busStatus + "|" + timestamp)
        );
    }

    public boolean verifySignature() {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        String data = voucherId + "|" + sessionId + "|" + topic + "|" + winningProposal + "|"
                + quorumPercentage + "|" + totalRounds + "|" + nashResidualMargin + "|"
                + (participantSignatures != null ? String.join(",", participantSignatures) : "") + "|"
                + elapsedMicros + "|" + busStatus + "|" + timestamp;
        String expected = calculateSha256(data);
        // 使用常量时间比对校验零信任存证签名
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
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
