package tech.qiantong.qknow.ai.credit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 多智能体因果信贷归因与反共谋审计不可变存证收据 (Java 21 Record, SHA-256 存证留痕)
 */
public record AttributionAuditReceipt(
        String receiptId,
        String auctionId,
        String taskId,
        Map<String, Double> agentCredits,
        double collusionRiskScore,
        Set<String> quarantinedCartelAgents,
        Map<String, Double> roleFitnessVector,
        Map<String, Double> nextEpochRoleQuotas,
        double qualityScore,
        long executionDurationMs,
        String tamperProofHash,
        long timestampMs
) {

    public static AttributionAuditReceipt createReceipt(
            String auctionId,
            String taskId,
            Map<String, Double> agentCredits,
            double collusionRiskScore,
            Set<String> quarantinedCartelAgents,
            Map<String, Double> roleFitnessVector,
            Map<String, Double> nextEpochRoleQuotas,
            double qualityScore,
            long executionDurationMs
    ) {
        long now = System.currentTimeMillis();
        String receiptId = "RCPT-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Double> credits = Collections.unmodifiableMap(new LinkedHashMap<>(agentCredits != null ? agentCredits : Map.of()));
        Set<String> quarantined = Collections.unmodifiableSet(new TreeSet<>(quarantinedCartelAgents != null ? quarantinedCartelAgents : Set.of()));
        Map<String, Double> fitness = Collections.unmodifiableMap(new LinkedHashMap<>(roleFitnessVector != null ? roleFitnessVector : Map.of()));
        Map<String, Double> quotas = Collections.unmodifiableMap(new LinkedHashMap<>(nextEpochRoleQuotas != null ? nextEpochRoleQuotas : Map.of()));

        String hash = computeHash(receiptId, auctionId, taskId, credits, collusionRiskScore, quarantined, fitness, quotas, qualityScore, executionDurationMs, now);

        return new AttributionAuditReceipt(
                receiptId, auctionId, taskId, credits, collusionRiskScore, quarantined, fitness, quotas, qualityScore, executionDurationMs, hash, now
        );
    }

    /**
     * 验证存证收据密码学哈希完整性 (防篡改检测)
     */
    public boolean verifyIntegrity() {
        String expected = computeHash(receiptId, auctionId, taskId, agentCredits, collusionRiskScore, quarantinedCartelAgents, roleFitnessVector, nextEpochRoleQuotas, qualityScore, executionDurationMs, timestampMs);
        return expected.equalsIgnoreCase(tamperProofHash);
    }

    private static String computeHash(
            String receiptId, String auctionId, String taskId,
            Map<String, Double> credits, double collusionRisk,
            Set<String> quarantined, Map<String, Double> fitness,
            Map<String, Double> quotas, double quality, long duration, long ts
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = receiptId + ":" + auctionId + ":" + taskId + ":" + credits.hashCode() + ":"
                    + String.format(Locale.US, "%.4f", collusionRisk) + ":" + quarantined.hashCode() + ":"
                    + fitness.hashCode() + ":" + quotas.hashCode() + ":"
                    + String.format(Locale.US, "%.4f", quality) + ":" + duration + ":" + ts;
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "RECEIPT_HASH_" + receiptId + "_" + ts;
        }
    }
}
