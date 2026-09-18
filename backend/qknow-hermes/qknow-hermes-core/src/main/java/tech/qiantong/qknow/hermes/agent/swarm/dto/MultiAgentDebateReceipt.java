package tech.qiantong.qknow.hermes.agent.swarm.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * 不可变多智能体协同与辩论决策凭单 Record
 * 具备自签名防伪与端到端审计存证能力
 */
public record MultiAgentDebateReceipt(
        String debateId,
        List<String> participants,
        int totalRounds,
        String finalVerdict,
        double confidenceScore,
        List<String> handoffChain,
        boolean convergedEarly,
        boolean factVerified,
        long latencyMs,
        String sha256Signature,
        long timestamp
) {
    public MultiAgentDebateReceipt {
        if (debateId == null || finalVerdict == null) {
            throw new IllegalArgumentException("debateId and finalVerdict must not be null");
        }
        if (totalRounds < 0 || totalRounds > 3) {
            throw new IllegalArgumentException("totalRounds must be between 0 and 3");
        }
        if (confidenceScore < 0.0 || confidenceScore > 1.0) {
            throw new IllegalArgumentException("confidenceScore must be within [0.0, 1.0]");
        }
    }

    /**
     * 创建并签署防篡改决策凭单
     */
    public static MultiAgentDebateReceipt createSigned(
            String debateId,
            List<String> participants,
            int totalRounds,
            String finalVerdict,
            double confidenceScore,
            List<String> handoffChain,
            boolean convergedEarly,
            boolean factVerified,
            long latencyMs,
            long timestamp) {

        String rawData = computeRawData(
                debateId,
                participants != null ? participants.size() : 0,
                totalRounds,
                confidenceScore,
                convergedEarly,
                factVerified,
                latencyMs,
                timestamp);

        String signature = computeSha256(rawData);
        return new MultiAgentDebateReceipt(
                debateId,
                participants != null ? List.copyOf(participants) : List.of(),
                totalRounds,
                finalVerdict,
                confidenceScore,
                handoffChain != null ? List.copyOf(handoffChain) : List.of(),
                convergedEarly,
                factVerified,
                latencyMs,
                signature,
                timestamp
        );
    }

    /**
     * 校验自签名完整性
     */
    public boolean verifyIntegrity() {
        String rawData = computeRawData(
                debateId,
                participants.size(),
                totalRounds,
                confidenceScore,
                convergedEarly,
                factVerified,
                latencyMs,
                timestamp);
        return computeSha256(rawData).equalsIgnoreCase(sha256Signature);
    }

    private static String computeRawData(
            String debateId,
            int participantCount,
            int rounds,
            double confidence,
            boolean earlyExit,
            boolean verified,
            long latency,
            long ts) {
        return String.format("%s|%d|%d|%.4f|%b|%b|%d|%d",
                debateId, participantCount, rounds, confidence, earlyExit, verified, latency, ts);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
