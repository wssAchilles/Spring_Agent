package tech.qiantong.qknow.hermes.causal.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 纯 Java 21 Record 格式不可变反事实决策审计凭单 (Counterfactual Decision Audit Receipt)
 * 严格遵循 Phase 146 密码学零时序泄漏工程铁律、Judea Pearl 结构因果反事实推演规范与 W3C 全链路追踪。
 *
 * @author Achilles
 * @since Phase 146
 */
public record CounterfactualDecisionAuditReceipt(
        String receiptId,
        String tenantId,
        String taskId,
        String traceId,
        List<String> bestActionPath,
        int counterfactualPrunedCount,
        int quarantinedCount,
        int maxTreeDepth,
        int totalVisitedNodes,
        double rootUctScore,
        double averageGeodesicDistance,
        int alignedTokens,
        boolean isCacheAligned,
        double inferenceLatencyMs,
        long timestamp,
        String sha256Signature
) {

    public CounterfactualDecisionAuditReceipt {
        // 保证内部路径列表不可变防篡改
        bestActionPath = bestActionPath != null ? List.copyOf(bestActionPath) : List.of();
    }

    /**
     * 工厂方法：自动规范化计算全字段防篡改 SHA-256 数字签名并签发不可变凭单
     */
    public static CounterfactualDecisionAuditReceipt create(
            String receiptId,
            String tenantId,
            String taskId,
            String traceId,
            List<String> bestActionPath,
            int counterfactualPrunedCount,
            int quarantinedCount,
            int maxTreeDepth,
            int totalVisitedNodes,
            double rootUctScore,
            double averageGeodesicDistance,
            int alignedTokens,
            boolean isCacheAligned,
            double inferenceLatencyMs,
            long timestamp
    ) {
        String payload = buildNormalizedPayload(
                receiptId, tenantId, taskId, traceId, bestActionPath,
                counterfactualPrunedCount, quarantinedCount, maxTreeDepth, totalVisitedNodes,
                rootUctScore, averageGeodesicDistance, alignedTokens, isCacheAligned,
                inferenceLatencyMs, timestamp
        );
        String signature = computeSha256Hex(payload);
        return new CounterfactualDecisionAuditReceipt(
                receiptId, tenantId, taskId, traceId, bestActionPath,
                counterfactualPrunedCount, quarantinedCount, maxTreeDepth, totalVisitedNodes,
                rootUctScore, averageGeodesicDistance, alignedTokens, isCacheAligned,
                inferenceLatencyMs, timestamp, signature
        );
    }

    /**
     * 校验数字签名完整性 (严格使用 MessageDigest.isEqual 常量时间比对防时序侧信道攻击)
     */
    public boolean verifySignature() {
        if (sha256Signature == null || sha256Signature.isBlank()) {
            return false;
        }
        String expectedPayload = buildNormalizedPayload(
                receiptId, tenantId, taskId, traceId, bestActionPath,
                counterfactualPrunedCount, quarantinedCount, maxTreeDepth, totalVisitedNodes,
                rootUctScore, averageGeodesicDistance, alignedTokens, isCacheAligned,
                inferenceLatencyMs, timestamp
        );
        String expectedSignature = computeSha256Hex(expectedPayload);
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    /**
     * 构建规范化全字段签名负载字符串
     */
    private static String buildNormalizedPayload(
            String receiptId,
            String tenantId,
            String taskId,
            String traceId,
            List<String> bestActionPath,
            int counterfactualPrunedCount,
            int quarantinedCount,
            int maxTreeDepth,
            int totalVisitedNodes,
            double rootUctScore,
            double averageGeodesicDistance,
            int alignedTokens,
            boolean isCacheAligned,
            double inferenceLatencyMs,
            long timestamp
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(receiptId).append("|")
                .append(tenantId).append("|")
                .append(taskId).append("|")
                .append(traceId).append("|")
                .append(bestActionPath != null ? String.join("->", bestActionPath) : "").append("|")
                .append(counterfactualPrunedCount).append("|")
                .append(quarantinedCount).append("|")
                .append(maxTreeDepth).append("|")
                .append(totalVisitedNodes).append("|")
                .append(String.format("%.6f", rootUctScore)).append("|")
                .append(String.format("%.6f", averageGeodesicDistance)).append("|")
                .append(alignedTokens).append("|")
                .append(isCacheAligned).append("|")
                .append(String.format("%.4f", inferenceLatencyMs)).append("|")
                .append(timestamp);
        return sb.toString();
    }

    private static String computeSha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
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
            throw new IllegalStateException("JVM 不支持 SHA-256 算法", e);
        }
    }
}
