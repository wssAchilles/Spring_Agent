package tech.qiantong.qknow.ai.policy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * 离线策略评估与安全治理不可变存证凭单 (Java 21 Record)
 */
public record OfflineTrajectoryReceipt(
        String receiptId,
        String evaluationBatchId,
        String targetPolicyId,
        String baselinePolicyId,
        double doublyRobustValue,
        double directMethodValue,
        double importanceSamplingValue,
        double cqlPessimisticPenalty,
        double conservativeValue,
        Map<String, Double> lagrangianMultipliers,
        boolean constraintViolated,
        boolean policyAccepted,
        long timestamp,
        String sha256Signature
) {

    public static OfflineTrajectoryReceipt create(
            String receiptId,
            String evaluationBatchId,
            String targetPolicyId,
            String baselinePolicyId,
            double doublyRobustValue,
            double directMethodValue,
            double importanceSamplingValue,
            double cqlPessimisticPenalty,
            double conservativeValue,
            Map<String, Double> lagrangianMultipliers,
            boolean constraintViolated,
            boolean policyAccepted,
            long timestamp
    ) {
        Map<String, Double> sortedMultipliers = new TreeMap<>(lagrangianMultipliers != null ? lagrangianMultipliers : Map.of());
        String payload = String.format("%s|%s|%s|%s|%.6f|%.6f|%.6f|%.6f|%.6f|%s|%b|%b|%d",
                receiptId, evaluationBatchId, targetPolicyId, baselinePolicyId,
                doublyRobustValue, directMethodValue, importanceSamplingValue,
                cqlPessimisticPenalty, conservativeValue,
                sortedMultipliers.toString(), constraintViolated, policyAccepted, timestamp);
        String signature = computeSha256(payload);
        return new OfflineTrajectoryReceipt(
                receiptId, evaluationBatchId, targetPolicyId, baselinePolicyId,
                doublyRobustValue, directMethodValue, importanceSamplingValue,
                cqlPessimisticPenalty, conservativeValue,
                Collections.unmodifiableMap(sortedMultipliers), constraintViolated, policyAccepted, timestamp, signature
        );
    }

    public boolean verifySignature() {
        Map<String, Double> sortedMultipliers = new TreeMap<>(lagrangianMultipliers != null ? lagrangianMultipliers : Map.of());
        String payload = String.format("%s|%s|%s|%s|%.6f|%.6f|%.6f|%.6f|%.6f|%s|%b|%b|%d",
                receiptId, evaluationBatchId, targetPolicyId, baselinePolicyId,
                doublyRobustValue, directMethodValue, importanceSamplingValue,
                cqlPessimisticPenalty, conservativeValue,
                sortedMultipliers.toString(), constraintViolated, policyAccepted, timestamp);
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
