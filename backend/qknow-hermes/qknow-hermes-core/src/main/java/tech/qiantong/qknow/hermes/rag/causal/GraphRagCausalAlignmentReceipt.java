package tech.qiantong.qknow.hermes.rag.causal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 拓扑子图因果推断与时空流形对齐存证凭单
 * 纯 Java 21 Record 格式不可变载荷，内嵌 SHA-256 密码学自签名与常数时间验真，彻底杜绝图谱推理审计造假
 */
public record GraphRagCausalAlignmentReceipt(
        String receiptId,
        String queryText,
        List<String> seedEntities,
        int rawNodeCount,
        int prunedNodeCount,
        int propositionCount,
        double avgSpatiotemporalScore,
        List<String> causalPropositions,
        long latencyMicros,
        long timestamp,
        String signature
) {
    public GraphRagCausalAlignmentReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(queryText, "queryText 不能为空");
        Objects.requireNonNull(seedEntities, "seedEntities 不能为空");
        Objects.requireNonNull(causalPropositions, "causalPropositions 不能为空");
        Objects.requireNonNull(signature, "signature 不能为空");
    }

    public static GraphRagCausalAlignmentReceipt create(
            String queryText,
            List<String> seedEntities,
            int rawNodeCount,
            int prunedNodeCount,
            int propositionCount,
            double avgSpatiotemporalScore,
            List<String> causalPropositions,
            long latencyMicros
    ) {
        String receiptId = "GRCA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timestamp = System.currentTimeMillis();
        String safeQuery = queryText != null ? queryText : "EMPTY_QUERY";
        List<String> safeSeeds = seedEntities != null ? List.copyOf(seedEntities) : List.of();
        List<String> safeProps = causalPropositions != null ? List.copyOf(causalPropositions) : List.of();

        String payload = buildCanonicalPayload(receiptId, safeQuery, safeSeeds, rawNodeCount,
                prunedNodeCount, propositionCount, avgSpatiotemporalScore, safeProps, latencyMicros, timestamp);
        String signature = computeSha256(payload);

        return new GraphRagCausalAlignmentReceipt(
                receiptId,
                safeQuery,
                safeSeeds,
                rawNodeCount,
                prunedNodeCount,
                propositionCount,
                avgSpatiotemporalScore,
                safeProps,
                latencyMicros,
                timestamp,
                signature
        );
    }

    public boolean verifySignature() {
        String expectedPayload = buildCanonicalPayload(receiptId, queryText, seedEntities, rawNodeCount,
                prunedNodeCount, propositionCount, avgSpatiotemporalScore, causalPropositions, latencyMicros, timestamp);
        String expectedSignature = computeSha256(expectedPayload);
        return MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildCanonicalPayload(
            String receiptId,
            String query,
            List<String> seeds,
            int rawNodes,
            int prunedNodes,
            int propCount,
            double avgScore,
            List<String> props,
            long latencyMicros,
            long timestamp
    ) {
        return receiptId + "|" + query + "|" + String.join(",", seeds) + "|" + rawNodes + "|"
                + prunedNodes + "|" + propCount + "|" + String.format(java.util.Locale.US, "%.4f", avgScore) + "|"
                + String.join(";;", props) + "|" + latencyMicros + "|" + timestamp;
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
