package tech.qiantong.qknow.ai.embodied.mapping.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * 不可变语义自主探索存证凭单 (Java 21 Record)
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record SemanticExplorationReceipt(
        String sessionId,
        int explorationRound,
        double[] frontierCoordinate,
        double informationGain,
        double travelCost,
        double targetSemanticSimilarity,
        double coverageRatio,
        int topologicalNodeCount,
        long timestampNs,
        String signature
) {

    public SemanticExplorationReceipt {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        frontierCoordinate = frontierCoordinate != null ? frontierCoordinate.clone() : new double[3];
    }

    /**
     * 自动生成带签名的构造器
     */
    public SemanticExplorationReceipt(
            String sessionId,
            int explorationRound,
            double[] frontierCoordinate,
            double informationGain,
            double travelCost,
            double targetSemanticSimilarity,
            double coverageRatio,
            int topologicalNodeCount,
            long timestampNs
    ) {
        this(
                sessionId,
                explorationRound,
                frontierCoordinate,
                informationGain,
                travelCost,
                targetSemanticSimilarity,
                coverageRatio,
                topologicalNodeCount,
                timestampNs,
                computeSha256(sessionId, explorationRound, frontierCoordinate, informationGain, travelCost, targetSemanticSimilarity, coverageRatio, topologicalNodeCount, timestampNs)
        );
    }

    /**
     * 零信任密码学自验
     */
    public boolean verifyIntegrity() {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = computeSha256(
                sessionId,
                explorationRound,
                frontierCoordinate,
                informationGain,
                travelCost,
                targetSemanticSimilarity,
                coverageRatio,
                topologicalNodeCount,
                timestampNs
        );
        return signature.equalsIgnoreCase(expected);
    }

    private static String computeSha256(
            String sessionId,
            int explorationRound,
            double[] frontierCoordinate,
            double informationGain,
            double travelCost,
            double targetSemanticSimilarity,
            double coverageRatio,
            int topologicalNodeCount,
            long timestampNs
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            sb.append(sessionId).append("|")
                    .append(explorationRound).append("|")
                    .append(Arrays.toString(frontierCoordinate)).append("|")
                    .append(String.format("%.6f", informationGain)).append("|")
                    .append(String.format("%.6f", travelCost)).append("|")
                    .append(String.format("%.6f", targetSemanticSimilarity)).append("|")
                    .append(String.format("%.6f", coverageRatio)).append("|")
                    .append(topologicalNodeCount).append("|")
                    .append(timestampNs);

            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
